package eu.kanade.presentation.components

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import com.tadami.aurora.R
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.theme.LocalIsAuroraTheme
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Collections

enum class AuroraCoverPlaceholderVariant {
    Portrait,
    Wide,
}

@DrawableRes
fun auroraCoverPlaceholderResId(variant: AuroraCoverPlaceholderVariant): Int {
    return when (variant) {
        AuroraCoverPlaceholderVariant.Portrait -> R.drawable.aurora_cover_placeholder_portrait
        AuroraCoverPlaceholderVariant.Wide -> R.drawable.aurora_cover_placeholder_wide
    }
}

@Composable
fun rememberAuroraCoverPlaceholderPainter(
    variant: AuroraCoverPlaceholderVariant = AuroraCoverPlaceholderVariant.Portrait,
): Painter {
    return rememberResourceBitmapPainter(auroraCoverPlaceholderResId(variant))
}

@Composable
fun rememberThemeAwareCoverErrorPainter(
    variant: AuroraCoverPlaceholderVariant = AuroraCoverPlaceholderVariant.Portrait,
): Painter {
    val isAuroraTheme = LocalIsAuroraTheme.current
    return rememberResourceBitmapPainter(
        themeAwareCoverFallbackResId(
            isAuroraTheme = isAuroraTheme,
            variant = variant,
        ),
    )
}

fun resolveAuroraCoverPlaceholderMemoryCacheKey(data: Any?): String? {
    return when (val candidate = resolveAuroraCoverModelCandidate(data)) {
        null -> null
        is String -> candidate
        is Anime -> "anime;${candidate.id};${candidate.thumbnailUrl};${candidate.coverLastModified}"
        is AnimeCover -> "anime;${candidate.animeId};${candidate.url};${candidate.lastModified}"
        is Manga -> "manga;${candidate.id};${candidate.thumbnailUrl};${candidate.coverLastModified}"
        is MangaCover -> "manga;${candidate.mangaId};${candidate.url};${candidate.lastModified}"
        is Novel -> "novel;${candidate.id};${candidate.thumbnailUrl};${candidate.coverLastModified}"
        is NovelCover -> "novel;${candidate.novelId};${candidate.url};${candidate.lastModified}"
        else -> candidate.toString()
    }
}

fun buildAuroraCoverImageRequest(
    context: Context,
    data: Any?,
    configure: ImageRequest.Builder.() -> Unit = {},
): ImageRequest {
    // V6: miniatura progresiva. Se pide primero una versión pequeña (160x240)
    // que pinta al instante como placeholder de la petición completa; la
    // petición principal no cambia (misma URL, misma caché de disco), así que
    // si la miniatura falla no se pierde nada: el placeholder simplemente no
    // aparece, como antes. No se toca el resto del pipeline de portadas.
    val baseKey = resolveAuroraCoverPlaceholderMemoryCacheKey(data)
    val thumbKey = baseKey?.let { "$it;thumb160" }
    if (thumbKey != null) {
        enqueueAuroraCoverThumb(context, data, thumbKey)
    }
    val ultraLightMode = runCatching { Injekt.get<UiPreferences>().ultraLightMode().get() }.getOrDefault(false)
    return ImageRequest.Builder(context)
        .data(resolveAuroraCoverModel(data))
        .placeholderMemoryCacheKey(thumbKey ?: baseKey)
        .apply(configure)
        .apply {
            // V6 ultra ligero: fuerza miniaturas en baja resolución en toda la app.
            if (ultraLightMode) size(Size(320, 480))
        }
        .build()
}

// Claves de miniatura ya pedidas en este proceso (con tope, para no pedir la
// misma dos veces en cada recomposición). La miniatura queda en la caché de
// memoria de Coil, así que el set solo evita duplicar el trabajo.
private val auroraCoverThumbRequested = Collections.synchronizedSet(LinkedHashSet<String>())

private fun enqueueAuroraCoverThumb(context: Context, data: Any?, thumbKey: String) {
    runCatching {
        if (!auroraCoverThumbRequested.add(thumbKey)) return
        if (auroraCoverThumbRequested.size > 1000) auroraCoverThumbRequested.clear()
        val imageLoader = SingletonImageLoader.get(context)
        if (imageLoader.memoryCache[thumbKey] != null) return
        val thumbRequest = ImageRequest.Builder(context)
            .data(resolveAuroraCoverModel(data))
            .memoryCacheKey(thumbKey)
            .size(Size(160, 240))
            .build()
        imageLoader.enqueue(thumbRequest)
    }
}

@DrawableRes
fun themeAwareCoverFallbackResId(
    isAuroraTheme: Boolean,
    variant: AuroraCoverPlaceholderVariant = AuroraCoverPlaceholderVariant.Portrait,
): Int {
    return if (isAuroraTheme) {
        auroraCoverPlaceholderResId(variant)
    } else {
        R.drawable.cover_empty
    }
}

fun resolveAuroraCoverModel(data: Any?): Any? {
    return resolveAuroraCoverModelCandidate(data)
}

internal data class AuroraPosterModelPair(
    val primary: Any?,
    val fallback: Any?,
)

internal fun resolveAuroraPosterModelPair(
    primary: Any?,
    fallback: Any? = null,
): AuroraPosterModelPair {
    return AuroraPosterModelPair(
        primary = resolveAuroraCoverModelCandidate(primary),
        fallback = resolveAuroraCoverModelCandidate(fallback),
    )
}

internal fun shouldApplyAuroraPosterTrim(url: String?): Boolean {
    if (url.isNullOrBlank()) {
        return false
    }
    val normalized = url.substringBefore('?').substringBefore('#').lowercase()
    val extension = normalized.substringAfterLast('.', missingDelimiterValue = "")
    return extension !in setOf("gif", "webp", "apng", "avif")
}

private fun resolveAuroraCoverModelCandidate(data: Any?): Any? {
    return when (data) {
        null -> null
        is String -> data.takeIf { it.isNotBlank() }
        is AnimeCover -> data.takeIf { !it.url.isNullOrBlank() || it.isAnimeFavorite }
        is MangaCover -> data.takeIf { !it.url.isNullOrBlank() || it.isMangaFavorite }
        is NovelCover -> data.takeIf { !it.url.isNullOrBlank() || it.isNovelFavorite }
        is Novel -> data.takeIf { !it.thumbnailUrl.isNullOrBlank() || it.favorite }
        else -> data
    }
}
