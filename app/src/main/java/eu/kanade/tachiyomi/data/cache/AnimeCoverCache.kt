package eu.kanade.tachiyomi.data.cache

import android.content.Context
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.domain.entries.anime.model.Anime
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Class used to create cover cache.
 * It is used to store the covers of the library.
 * Names of files are created with the md5 of the thumbnail URL.
 *
 * @param context the application context.
 * @constructor creates an instance of the cover cache.
 */
class AnimeCoverCache(private val context: Context) {

    companion object {
        private const val COVERS_DIR = "animecovers"
        private const val CUSTOM_COVERS_DIR = "animecovers/custom"

        /** v7.9.20 Fase 2: portadas mejoradas con IA (JPEG, una por título). */
        private const val ENHANCED_COVERS_DIR = "animecovers/enhanced"
    }

    /**
     * Cache directory used for cache management.
     */
    private val cacheDir = getCacheDir(COVERS_DIR)

    private val customCoverCacheDir = getCacheDir(CUSTOM_COVERS_DIR)

    /** v7.9.20 Fase 2: carpeta de portadas mejoradas con IA. */
    private val enhancedCoverCacheDir = getCacheDir(ENHANCED_COVERS_DIR)

    /**
     * Returns the cover from cache.
     *
     * @param animeThumbnailUrl the anime.
     * @return cover image.
     */
    fun getCoverFile(animeThumbnailUrl: String?): File? {
        return animeThumbnailUrl?.let {
            File(cacheDir, DiskUtil.hashKeyForDisk(it))
        }
    }

    /**
     * Returns the custom cover from cache.
     *
     * @param animeId the anime id.
     * @return cover image.
     */
    fun getCustomCoverFile(animeId: Long?): File {
        return File(customCoverCacheDir, DiskUtil.hashKeyForDisk(animeId.toString()))
    }

    /**
     * v7.9.20 Fase 2: la portada mejorada con IA (Real-ESRGAN x2) de un
     * título, guardada como JPEG en app-private storage. Gana a la
     * portada de red en todo (detalle, cuadrícula, Seguir viendo), pero
     * nunca a la personalizada del usuario (esa se comprueba antes).
     */
    fun getEnhancedCoverFile(animeId: Long?): File {
        return File(enhancedCoverCacheDir, DiskUtil.hashKeyForDisk(animeId.toString()))
    }

    fun deleteEnhancedCover(animeId: Long?): Boolean {
        return getEnhancedCoverFile(animeId).let {
            it.exists() && it.delete()
        }
    }

    /**
     * Saves the given stream as the anime's custom cover to cache.
     *
     * @param anime the anime.
     * @param inputStream the stream to copy.
     * @throws IOException if there's any error.
     */
    @Throws(IOException::class)
    fun setCustomCoverToCache(anime: Anime, inputStream: InputStream) {
        getCustomCoverFile(anime.id).outputStream().use {
            inputStream.copyTo(it)
        }
    }

    /**
     * Delete the cover files of the anime from the cache.
     *
     * @param anime the anime.
     * @param deleteCustomCover whether the custom cover should be deleted.
     * @return number of files that were deleted.
     */
    fun deleteFromCache(anime: Anime, deleteCustomCover: Boolean = false): Int {
        var deleted = 0

        getCoverFile(anime.thumbnailUrl)?.let {
            if (it.exists() && it.delete()) ++deleted
        }

        if (deleteCustomCover) {
            if (deleteCustomCover(anime.id)) ++deleted
        }

        return deleted
    }

    /**
     * Delete custom cover of the anime from the cache
     *
     * @param animeId the anime id.
     * @return whether the cover was deleted.
     */
    fun deleteCustomCover(animeId: Long?): Boolean {
        return getCustomCoverFile(animeId).let {
            it.exists() && it.delete()
        }
    }

    private fun getCacheDir(dir: String): File {
        return context.getExternalFilesDir(dir)
            ?: File(context.filesDir, dir).also { it.mkdirs() }
    }
}
