package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.interceptor.CoverRequestPolicy
import eu.kanade.tachiyomi.network.toAsciiUrl
import eu.kanade.tachiyomi.network.withCoverTimeouts
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException

data class AuroraPosterRequest(
    val primaryUrl: String?,
    val fallbackUrl: String? = null,
    val refererUrl: String? = null,
    /**
     * Extra request headers (e.g. the source's User-Agent) forwarded verbatim.
     * Some cover proxies (anilist via wp.youtube-anime.com) answer 403 when the
     * request does not carry the source's headers, while the same URL succeeds
     * through the source's own client in [eu.kanade.tachiyomi.data.coil.AnimeImageFetcher].
     */
    val headers: Map<String, String>? = null,
    /**
     * Source's own OkHttp client, when available. It carries the source's
     * interceptors (Cloudflare/WebView bypass, User-Agent) that the generic
     * poster client does not, so viewer-hosted covers (e.g. animepahe behind
     * Cloudflare) are fetched the same way the entry thumbnail is.
     */
    val client: Call.Factory? = null,
    /**
     * User-set custom cover file for the entry, if any. When the file exists
     * it always wins over [primaryUrl]/[fallbackUrl] (issue #154).
     */
    val customCoverFile: File? = null,
    /** Entry cover timestamp so cache keys change after cover edits. */
    val coverLastModified: Long = 0L,
)

class AuroraPosterRequestKeyer : Keyer<AuroraPosterRequest> {
    override fun key(data: AuroraPosterRequest, options: Options): String {
        return buildString {
            append("aurora-poster;")
            append(data.primaryUrl.orEmpty())
            append(';')
            append(data.fallbackUrl.orEmpty())
            append(';')
            append(data.coverLastModified)
            if (data.customCoverFile?.exists() == true) {
                append(";custom")
            }
            // v7.9.20: el nivel del filtro forma parte de la clave, como
            // en la clave de disco — si no, cambiar el ajuste serviría
            // el bitmap viejo de la caché de memoria.
            append(CoverFilter.cacheKeyFragment())
        }
    }
}

class AuroraPosterRequestFetcher(
    private val data: AuroraPosterRequest,
    private val options: Options,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val imageLoader: ImageLoader,
) : Fetcher {

    private val diskCacheKey: String
        get() = buildString {
            append("aurora-poster;")
            append(data.primaryUrl.orEmpty())
            append(';')
            append(data.fallbackUrl.orEmpty())
            // v7.9.19 Fase 1 (Capa 2): el nivel del filtro forma parte de
            // la clave — al cambiar el ajuste, la portada se vuelve a
            // bajar y a filtrar en vez de servir la versión vieja.
            append(CoverFilter.cacheKeyFragment())
        }

    override suspend fun fetch(): FetchResult {
        // Issue #154: a user-set custom cover must always win over URL-resolved
        // posters and over the URL-keyed poster disk cache.
        data.customCoverFile?.takeIf { it.exists() }?.let { file ->
            return customCoverLoader(file)
        }
        readFromDiskCache()?.let { return it }

        val fetched = loadAuroraPosterSource(
            callFactory = data.client ?: callFactoryLazy.value,
            fileSystem = options.fileSystem,
            request = data,
        )
        if (fetched !is SourceFetchResult) return fetched
        return writeToDiskCache(fetched) ?: fetched
    }

    private fun customCoverLoader(file: File): FetchResult {
        return SourceFetchResult(
            source = ImageSource(
                file = file.toOkioPath(),
                fileSystem = FileSystem.SYSTEM,
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private fun readFromDiskCache(): FetchResult? {
        if (!options.diskCachePolicy.readEnabled) return null
        val diskCache = imageLoader.diskCache ?: return null
        val snapshot = runCatching { diskCache.openSnapshot(diskCacheKey) }.getOrNull() ?: return null
        return SourceFetchResult(
            source = ImageSource(
                file = snapshot.data,
                fileSystem = diskCache.fileSystem,
                diskCacheKey = diskCacheKey,
                closeable = snapshot,
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    /**
     * Persists the fetched poster bytes into Coil's disk cache so posters stay
     * available on offline starts. Returns null only before the source has
     * been consumed (caching disabled), so the caller can fall back to the
     * original result safely.
     *
     * v7.9.19 Fase 1 (Capa 2): antes de guardar, los bytes pasan por el
     * filtro suave de portadas del detalle. Se aplica UNA vez aquí (hilo
     * de IO de Coil) y el resultado filtrado es lo que queda en caché,
     * así que al hacer scroll o al reabrir el detalle no se recalcula
     * nada. Si el filtro está apagado o falla, se guardan los bytes
     * originales sin tocar.
     */
    private fun writeToDiskCache(fetched: SourceFetchResult): FetchResult? {
        if (!options.diskCachePolicy.writeEnabled) return null
        val diskCache = imageLoader.diskCache ?: return null
        val originalBytes = fetched.source.use { it.source().readByteArray() }
        val filteredBytes = CoverFilter.filter(originalBytes)
        val bytes = filteredBytes ?: originalBytes
        // Los bytes filtrados se re-codifican como JPEG: el tipo MIME
        // tiene que decirlo, o el decodificador recibiría una pista falsa.
        val mimeType = if (filteredBytes != null) "image/jpeg" else fetched.mimeType
        val snapshot = runCatching {
            val editor = diskCache.openEditor(diskCacheKey) ?: return@runCatching null
            try {
                diskCache.fileSystem.write(editor.data) { write(bytes) }
                editor.commitAndOpenSnapshot()
            } catch (e: Exception) {
                runCatching { editor.abort() }
                null
            }
        }.getOrNull()
        val source = if (snapshot != null) {
            ImageSource(
                file = snapshot.data,
                fileSystem = diskCache.fileSystem,
                diskCacheKey = diskCacheKey,
                closeable = snapshot,
            )
        } else {
            ImageSource(source = Buffer().write(bytes), fileSystem = options.fileSystem)
        }
        return SourceFetchResult(
            source = source,
            mimeType = mimeType,
            dataSource = fetched.dataSource,
        )
    }

    class Factory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<AuroraPosterRequest> {
        override fun create(
            data: AuroraPosterRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            return AuroraPosterRequestFetcher(
                data = data,
                options = options,
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }
}

internal suspend fun loadAuroraPosterSource(
    callFactory: Call.Factory,
    fileSystem: FileSystem,
    request: AuroraPosterRequest,
): FetchResult {
    // v7.9.19 Fase 1 (Capa 1): si la dirección de la portada permite
    // pedir una variante más grande, se prueba esa primero y la original
    // queda como respaldo inmediato — si la grande da 404 o falla, cae
    // a la que había sin que se note. Cuando no hay variante mayor, la
    // lista queda igual que antes ([primary, fallback]).
    //
    // v7.9.20: la variante mejorada es una SONDA (isProbe = true): su
    // 404 no ensucia la lista negra del host (ver CoverRequestPolicy).
    // Antes, cada póster de anime sumaba un fallo del host por la sonda
    // y con dos fallos el CDN entero quedaba bloqueado para el proceso.
    val candidates: List<Pair<String, Boolean>> = buildList {
        request.primaryUrl?.trim()?.takeIf { it.isNotBlank() }?.let { url ->
            upgradePosterUrl(url)?.let { add(it to true) }
            add(url to false)
        }
        request.fallbackUrl?.trim()?.takeIf { it.isNotBlank() }?.let { url ->
            upgradePosterUrl(url)?.let { add(it to true) }
            add(url to false)
        }
    }.distinct()

    // v7.9.20: timeouts de portada (25 s por intento) como AnimeImageFetcher.
    // El fetcher usaba el cliente tal cual (hasta 2 min por intento): con
    // 4 candidatos, un póster atascado retenía el detalle minutos.
    val effectiveCallFactory = if (callFactory is OkHttpClient) {
        callFactory.withCoverTimeouts()
    } else {
        callFactory
    }

    var lastError: IOException? = null

    for ((attemptIndex, candidate) in candidates.withIndex()) {
        val (candidateUrl, isProbe) = candidate
        val httpUrl = candidateUrl.toHttpUrlOrNull()
        if (httpUrl == null) {
            lastError = IOException("Invalid poster url: $candidateUrl")
            continue
        }
        if (CoverRequestPolicy.isBlacklisted(httpUrl.host)) {
            lastError = IOException("Skipped blacklisted cover host: ${httpUrl.host}")
            continue
        }

        val requestBuilder = CoverRequestPolicy.markCoverRequest(
            Request.Builder().url(httpUrl),
            fallbackUrl = request.fallbackUrl,
            attempt = attemptIndex,
        )
        if (isProbe) {
            CoverRequestPolicy.markCoverProbe(requestBuilder)
        }

        request.headers?.forEach { (name, value) ->
            requestBuilder.header(name, value)
        }
        request.refererUrl?.trim()?.takeIf { it.isNotBlank() }?.let { referer ->
            requestBuilder.header("Referer", referer.toAsciiUrl().trimEnd('/') + "/")
        }

        val response = try {
            effectiveCallFactory.newCall(requestBuilder.build()).await()
        } catch (e: IOException) {
            lastError = e
            continue
        }

        val body = response.body
        if (response.isSuccessful) {
            CoverRequestPolicy.clear(httpUrl.host)
            return SourceFetchResult(
                source = ImageSource(
                    source = body.source(),
                    fileSystem = fileSystem,
                ),
                mimeType = body.contentType()?.toString() ?: "image/*",
                dataSource = if (response.cacheResponse != null) DataSource.DISK else DataSource.NETWORK,
            )
        }

        lastError = IOException("HTTP ${response.code} for $candidateUrl")
        response.close()
    }

    throw (lastError ?: IOException("Failed to load poster"))
}

/**
 * v7.9.19 Fase 1 (Capa 1): devuelve la variante más grande de una
 * dirección de portada cuando el CDN la ofrece cambiando un segmento
 * de la ruta, o null si no hay variante mayor que pedir. La variante
 * se prueba primero y la original queda como respaldo (ver
 * [loadAuroraPosterSource]), así que un 404 no rompe nada: solo cuesta
 * una petición fallida antes de cargar la de siempre.
 *
 * Variantes que se conocen:
 * - AniList CDN: /medium/ -> /large/ (la extraLarge vive bajo /large/).
 * - Shikimori: /preview/ -> /original/.
 * - MangaDex: cover.256.jpg / cover.512.jpg -> cover original.
 * - MyAnimeList CDN: ...123t.jpg -> ...123.jpg (la `t` marca miniatura).
 * - Genérico: /small/ -> /large/, /thumb(s)/ -> /original/,
 *   /thumbnail/ -> /large/.
 *
 * Nota: los títulos con metadatos (AniList/Shikimori) ya traen la
 * extraLarge o la original desde la propia API, así que esto solo
 * ayuda a las portadas que vienen de la fuente sin metadatos. El
 * bannerImage de AniList (~1900 px panorámico) NO se usa aquí: el
 * fondo del detalle es vertical y con recorte solo se vería una
 * franja del banner; la portada vertical grande es la imagen correcta.
 */
internal fun upgradePosterUrl(url: String): String? {
    val value = url.trim()
    if (value.isEmpty()) return null
    val upgraded = when {
        "/medium/" in value -> value.replace("/medium/", "/large/")
        "/preview/" in value -> value.replace("/preview/", "/original/")
        MANGADEX_SIZED_COVER_REGEX.containsMatchIn(value) ->
            value.replace(MANGADEX_SIZED_COVER_REGEX, "$2")
        value.contains("cdn.myanimelist.net") &&
            MAL_THUMBNAIL_REGEX.containsMatchIn(value) ->
            value.replace(MAL_THUMBNAIL_REGEX, "$1$2")
        "/small/" in value -> value.replace("/small/", "/large/")
        "/thumb/" in value -> value.replace("/thumb/", "/original/")
        "/thumbs/" in value -> value.replace("/thumbs/", "/original/")
        "/thumbnail/" in value -> value.replace("/thumbnail/", "/large/")
        else -> return null
    }
    return upgraded.takeIf { it != value }
}

private val MANGADEX_SIZED_COVER_REGEX =
    Regex("""\.(256|512)(\.(jpe?g|png|webp))$""", RegexOption.IGNORE_CASE)

private val MAL_THUMBNAIL_REGEX =
    Regex("""(\d)t(\.(jpe?g|png|webp))$""", RegexOption.IGNORE_CASE)
