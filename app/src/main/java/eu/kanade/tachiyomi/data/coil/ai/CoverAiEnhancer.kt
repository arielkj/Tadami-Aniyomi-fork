package eu.kanade.tachiyomi.data.coil.ai

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil3.BitmapImage
import coil3.DrawableImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AiCoverMode
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.coil.CoverFilter
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream

/**
 * v7.9.20 — on-demand AI cover enhancement (Real-ESRGAN x2, on-device).
 *
 * Flow: load the current cover (original, never the filtered one) →
 * download the model once (first use, ~8.6 MB over WiFi only, SHA-256
 * verified) → upscale x2 on the Vulkan GPU → apply the user's Fase 1
 * colour level (vivid but natural by default; no extra sharpening, the
 * AI output is already sharp) → the dialog shows before/after and the
 * user decides: keep it or discard it. Nothing is saved until the user
 * taps «Guardar».
 *
 * The enhanced file beats the network cover everywhere (grid, detail,
 * backdrop) but never a user-set custom cover. It is per-entry and
 * survives restarts; tapping «Mejorar portada» again regenerates it, and
 * «Volver a la original» deletes it. Every failure returns a [Result]
 * instead of throwing, so the button can report it.
 */
object CoverAiEnhancer {

    enum class Kind {
        ANIME,
        MANGA,
    }

    sealed interface Result {
        /**
         * The enhanced cover is ready to compare. [original] is the
         * current cover as it comes from the source (Coil-owned: do NOT
         * recycle it); [enhanced] is the AI result (caller-owned: recycle
         * it when the dialog closes or the user discards it).
         */
        data class Success(val original: Bitmap, val enhanced: Bitmap) : Result

        /** The AI mode is OFF (the button should be hidden in this case). */
        data object Disabled : Result

        /** No Vulkan GPU or no native library on this device. */
        data object UnsupportedDevice : Result

        /** The model is not downloaded yet and there is no WiFi. */
        data object ModelDownloadNeedsWifi : Result

        /** The model could not be downloaded (no network, bad hash, ...). */
        data object ModelDownloadFailed : Result

        /** The current cover could not be loaded. */
        data object CoverLoadFailed : Result

        /** The upscale itself failed. */
        data object EnhanceFailed : Result
    }

    fun currentMode(): AiCoverMode = runCatching {
        Injekt.get<UiPreferences>().auroraAiCoverMode().get()
    }.getOrDefault(AiCoverMode.MANUAL)

    /**
     * Runs the enhancement and returns both bitmaps for the before/after
     * comparison. The caller owns the enhanced bitmap and must recycle it
     * when the dialog closes or the user discards it (the original is
     * Coil-owned and must NOT be recycled). Nothing is saved here — call
     * [saveEnhanced] only if the user keeps it.
     */
    suspend fun enhance(
        context: Context,
        kind: Kind,
        entryId: Long,
        coverUrl: String?,
        fallbackCoverUrl: String?,
    ): Result = withContext(Dispatchers.IO) {
        if (currentMode() == AiCoverMode.OFF) return@withContext Result.Disabled
        if (!RealEsrganBridge.isSupported()) {
            logcat(LogPriority.WARN) { "AI enhance unsupported on this device" }
            return@withContext Result.UnsupportedDevice
        }

        val url = coverUrl?.takeIf { it.isNotBlank() }
            ?: fallbackCoverUrl?.takeIf { it.isNotBlank() }
        val source = url?.let { loadCoverBitmap(context, it) }
        if (source == null) {
            logcat(LogPriority.WARN) { "AI enhance: could not load cover $url" }
            return@withContext Result.CoverLoadFailed
        }
        // NOTE: `source` is Coil-owned (it may live in Coil's bitmap pool),
        // so it must NOT be recycled; the upscaled/finished bitmaps below
        // are ours and are recycled in their finally blocks.
        val networkHelper: NetworkHelper = Injekt.get()
        if (!RealEsrganModelDownloader.isDownloaded(context) &&
            !RealEsrganModelDownloader.isWifiConnected(context)
        ) {
            return@withContext Result.ModelDownloadNeedsWifi
        }
        if (!RealEsrganModelDownloader.ensureDownloaded(context, networkHelper.client)) {
            return@withContext Result.ModelDownloadFailed
        }
        if (!RealEsrganBridge.loadModel(
                RealEsrganModelDownloader.paramFile(context).absolutePath,
                RealEsrganModelDownloader.binFile(context).absolutePath,
            )
        ) {
            return@withContext Result.ModelDownloadFailed
        }
        val upscaled = RealEsrganBridge.enhance(source)
            ?: return@withContext Result.EnhanceFailed
        try {
            // Colour only (no sharpening): the AI output is already sharp.
            // The level comes from the user's Fase 1 filter setting
            // (Suave by default: vivid but natural, never oversaturated).
            val finished = CoverFilter.applyColorOnly(upscaled)
            if (finished !== upscaled) upscaled.recycle()
            return@withContext Result.Success(original = source, enhanced = finished)
        } catch (e: Exception) {
            upscaled.recycle()
            logcat(LogPriority.WARN, e) { "AI enhance: colour step failed" }
            return@withContext Result.EnhanceFailed
        }
    }

    /**
     * Saves an enhanced bitmap (from [enhance]) as this entry's enhanced
     * cover and invalidates the cover caches so it shows immediately
     * everywhere (grid, detail, backdrop). Returns false on failure.
     */
    suspend fun saveEnhanced(
        context: Context,
        kind: Kind,
        entryId: Long,
        bitmap: Bitmap,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = enhancedFile(kind, entryId)
            target.parentFile?.mkdirs()
            ByteArrayOutputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) return@runCatching false
                target.outputStream().use { it.write(out.toByteArray()) }
            }
            bumpCoverLastModified(kind, entryId)
            // The new file must win over the memory-cached old cover.
            runCatching { context.imageLoader.memoryCache?.clear() }
            true
        }.onFailure {
            logcat(LogPriority.WARN, it) { "AI enhance: could not save enhanced cover" }
        }.getOrDefault(false)
    }

    /**
     * Deletes this entry's enhanced cover («Volver a la original») and
     * invalidates the cover caches so the original shows again.
     */
    suspend fun deleteEnhanced(
        context: Context,
        kind: Kind,
        entryId: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = enhancedFile(kind, entryId)
            val deleted = !target.exists() || target.delete()
            bumpCoverLastModified(kind, entryId)
            runCatching { context.imageLoader.memoryCache?.clear() }
            deleted
        }.onFailure {
            logcat(LogPriority.WARN, it) { "AI enhance: could not delete enhanced cover" }
        }.getOrDefault(false)
    }

    /** True when this entry already has a saved AI-enhanced cover. */
    fun hasEnhancedCover(context: Context, kind: Kind, entryId: Long): Boolean {
        return runCatching { enhancedFile(kind, entryId).exists() }.getOrDefault(false)
    }

    private fun enhancedFile(kind: Kind, entryId: Long) = when (kind) {
        Kind.ANIME -> Injekt.get<AnimeCoverCache>().getEnhancedCoverFile(entryId)
        Kind.MANGA -> Injekt.get<MangaCoverCache>().getEnhancedCoverFile(entryId)
    }

    /**
     * Same invalidation the custom-cover flow uses: bumping
     * coverLastModified gives every cover request a new key, so the
     * detail, the grid and «Seguir viendo» reload from the enhanced
     * file (or back to the original after a delete).
     */
    private suspend fun bumpCoverLastModified(kind: Kind, entryId: Long) {
        runCatching {
            when (kind) {
                Kind.ANIME -> Injekt.get<UpdateAnime>().awaitUpdateCoverLastModified(entryId)
                Kind.MANGA -> Injekt.get<UpdateManga>().awaitUpdateCoverLastModified(entryId)
            }
        }.onFailure {
            logcat(LogPriority.WARN, it) { "AI enhance: could not bump coverLastModified" }
        }
    }

    private suspend fun loadCoverBitmap(context: Context, url: String): Bitmap? {
        return runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                // Coil 3 no tiene allowHardware: el decodificador devuelve
                // un bitmap de software (ARGB_8888) legible con getPixels.
                .size(Size.ORIGINAL)
                .build()
            val result = context.imageLoader.execute(request)
            val image = (result as? SuccessResult)?.image ?: return null
            when (image) {
                is BitmapImage -> image.bitmap
                is DrawableImage -> image.drawable.toBitmap()
                else -> null
            }
        }.onFailure {
            logcat(LogPriority.WARN, it) { "AI enhance: cover load failed for $url" }
        }.getOrNull()
    }
}
