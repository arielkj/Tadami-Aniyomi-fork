package eu.kanade.tachiyomi.data.download.anime

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.cancellation.CancellationException

/**
 * v7.9.28: convierte lo que devuelve la extensión en un enlace descargable.
 *
 * El camino viejo confiaba en que `Video.videoUrl` fuera ya un archivo o una
 * lista HLS válida. En la práctica (TioAnime) los hosters devuelven enlaces
 * que son páginas con reproductores embebidos, listas maestras HLS con
 * variantes anidadas, redirecciones, o enlaces que exigen cabeceras Referer.
 * Este resolver prueba los candidatos en orden hasta que uno dé un flujo
 * descargable y nunca falla por el primer enlace que no se pueda leer: si
 * nada se puede verificar, devuelve el vídeo original tal cual (ffmpeg puede
 * con él igualmente) y el bucle de servidores del descargador prueba el
 * siguiente servidor.
 *
 * Acepta y resuelve: directos MP4/WebM/MKV/MOV/AVI, HLS `.m3u8` (maestra y
 * de medios, URLs relativas, variantes anidadas, mejor calidad con fallback),
 * DASH `.mpd` (ffmpeg lo descarga directo), iframes embebidos, URLs de vídeo
 * incrustadas en JSON/HTML, redirecciones, y enlaces que exigen Referer o
 * User-Agent de la fuente (se pasan las cabeceras de la fuente).
 */
class VideoLinkResolver(private val source: AnimeHttpSource) {

    /**
     * Devuelve el vídeo con una URL final verificada (o la original si no se
     * pudo verificar nada). Devuelve null solo si no hay URL en absoluto.
     */
    suspend fun resolve(video: Video): Video? {
        var working = video
        var url = video.videoUrl.trim()
        if (url.isBlank() || url == "null") {
            val resolved = try {
                source.resolveVideo(video)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG, e) { "VideoLinkResolver: resolveVideo failed for ${video.videoTitle}" }
                null
            }
            if (resolved == null || resolved.videoUrl.isBlank() || resolved.videoUrl == "null") {
                return null
            }
            working = resolved
            url = resolved.videoUrl.trim()
        }

        val headers = effectiveHeaders(working)
        val finalUrl = try {
            resolveUrl(url, headers, depth = 0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "VideoLinkResolver: resolve failed for $url" }
            null
        }
        return withUrl(working, finalUrl ?: url, headers)
    }

    private fun withUrl(video: Video, url: String, headers: Headers): Video {
        return Video(
            videoUrl = url,
            videoTitle = video.videoTitle,
            resolution = video.resolution,
            bitrate = video.bitrate,
            headers = headers,
            preferred = video.preferred,
            subtitleTracks = video.subtitleTracks,
            audioTracks = video.audioTracks,
            timestamps = video.timestamps,
            mpvArgs = video.mpvArgs,
            ffmpegStreamArgs = video.ffmpegStreamArgs,
            ffmpegVideoArgs = video.ffmpegVideoArgs,
            internalData = video.internalData,
            initialized = true,
        ).also {
            it.pageUrl = video.pageUrl
            it.status = video.status
        }
    }

    /**
     * Cabeceras efectivas: las del vídeo (o las de la fuente) y, si no traen
     * Referer, el de la fuente. Muchos hosters rechazan la petición sin el
     * Referer del sitio que embebe el reproductor.
     */
    private fun effectiveHeaders(video: Video): Headers {
        val base = video.headers ?: source.headers
        if (base["Referer"] != null) return base
        return Headers.Builder()
            .addAll(base)
            .add("Referer", source.baseUrl.trimEnd('/') + "/")
            .build()
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val withScheme = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> source.baseUrl.trimEnd('/') + trimmed
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> return null
        }
        return withScheme.toHttpUrlOrNull()?.toString() ?: return null
    }

    private fun resolveRelative(baseUrl: String, ref: String): String {
        val trimmed = ref.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return baseUrl.toHttpUrlOrNull()?.resolve(trimmed)?.toString() ?: trimmed
    }

    private suspend fun resolveUrl(rawUrl: String, headers: Headers, depth: Int): String? {
        if (depth > MAX_DEPTH) return null
        val url = normalizeUrl(rawUrl) ?: return null
        val lower = url.lowercase().substringBefore('?')
        return when {
            lower.endsWith(".m3u8") -> resolvePlaylist(url, headers, depth)
            lower.endsWith(".mpd") -> if (reachable(url, headers)) url else null
            isDirectMediaExtension(lower) -> if (reachable(url, headers)) url else url
            else -> resolveUnknown(url, headers, depth)
        }
    }

    /**
     * La URL no dice qué es: se sondea con HEAD (sin bajar el cuerpo) y se
     * clasifica por Content-Type y por la URL final tras redirecciones.
     */
    private suspend fun resolveUnknown(url: String, headers: Headers, depth: Int): String? {
        val probe = headProbe(url, headers) ?: rangeProbe(url, headers) ?: return null
        val finalUrl = probe.finalUrl
        val contentType = probe.contentType.lowercase()
        val lower = finalUrl.lowercase().substringBefore('?')
        return when {
            contentType.startsWith("video/") -> finalUrl
            contentType.contains("mpegurl") || lower.endsWith(".m3u8") ->
                resolvePlaylist(finalUrl, headers, depth)
            contentType.contains("dash+xml") || lower.endsWith(".mpd") -> finalUrl
            isDirectMediaExtension(lower) -> finalUrl
            contentType.contains("html") || contentType.startsWith("text/") -> {
                val body = fetchText(finalUrl, headers) ?: return null
                for (candidate in extractVideoCandidates(body, finalUrl)) {
                    resolveUrl(candidate, headers, depth + 1)?.let { return it }
                }
                null
            }
            looksLikeMediaUrl(finalUrl) -> finalUrl
            else -> null
        }
    }

    private data class Probe(val finalUrl: String, val contentType: String)

    private suspend fun headProbe(url: String, headers: Headers): Probe? {
        return withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            try {
                val request = Request.Builder().url(url).head().headers(headers).build()
                source.client.newCall(request).awaitSuccess().use { response ->
                    Probe(
                        response.request.url.toString(),
                        response.header("Content-Type").orEmpty(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Algunos servidores rechazan HEAD: se prueba un GET con Range de 1 byte. */
    private suspend fun rangeProbe(url: String, headers: Headers): Probe? {
        return withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            try {
                val requestHeaders = Headers.Builder()
                    .addAll(headers)
                    .add("Range", "bytes=0-1")
                    .build()
                val request = Request.Builder().url(url).headers(requestHeaders).build()
                source.client.newCall(request).awaitSuccess().use { response ->
                    // No se lee el cuerpo: solo importan la URL final y el tipo.
                    Probe(
                        response.request.url.toString(),
                        response.header("Content-Type").orEmpty(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun reachable(url: String, headers: Headers): Boolean {
        return headProbe(url, headers) != null || rangeProbe(url, headers) != null
    }

    private suspend fun fetchText(url: String, headers: Headers): String? {
        return withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            try {
                val request = Request.Builder().url(url).headers(headers).build()
                source.client.newCall(request).awaitSuccess().use { response ->
                    response.body.string().take(MAX_BODY_CHARS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Baja la lista HLS y, si es maestra, elige la mejor variante (mayor
     * BANDWIDTH, desempate por resolución) y resuelve recursivamente (las
     * variantes pueden ser a su vez maestras anidadas). Si ya es de medios,
     * devuelve su propia URL.
     */
    private suspend fun resolvePlaylist(url: String, headers: Headers, depth: Int): String? {
        val body = fetchText(url, headers) ?: return null
        return parsePlaylist(body, url, headers, depth)
    }

    private suspend fun parsePlaylist(
        body: String,
        playlistUrl: String,
        headers: Headers,
        depth: Int,
    ): String? {
        val lines = body.lines()
        var bestUrl: String? = null
        var bestBandwidth = -1L
        var bestPixels = -1L
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val bandwidth = BANDWIDTH_REGEX.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: -1L
                val res = RESOLUTION_REGEX.find(line)
                val pixels = if (res != null) {
                    (res.groupValues[1].toLongOrNull() ?: 0L) * (res.groupValues[2].toLongOrNull() ?: 0L)
                } else {
                    -1L
                }
                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || lines[j].trimStart().startsWith("#"))) j++
                val variant = lines.getOrNull(j)?.trim()
                if (!variant.isNullOrBlank() &&
                    (bandwidth > bestBandwidth || (bandwidth == bestBandwidth && pixels > bestPixels))
                ) {
                    bestBandwidth = bandwidth
                    bestPixels = pixels
                    bestUrl = resolveRelative(playlistUrl, variant)
                }
                i = j
            }
            i++
        }
        if (bestUrl != null) {
            // Variante anidada: puede ser otra maestra o la lista de medios.
            return resolveUrl(bestUrl, headers, depth + 1) ?: bestUrl
        }
        // Sin variantes: ya es una lista de medios.
        return playlistUrl
    }

    /**
     * Extrae candidatos de vídeo de una página HTML: primero los iframes
     * embebidos (suelen contener el reproductor real), luego URLs en JSON
     * (`"file": "..."`, `source src=...`) y por último URLs sueltas.
     */
    private fun extractVideoCandidates(html: String, pageUrl: String): List<String> {
        val found = linkedSetOf<String>()
        fun addCandidate(raw: String?) {
            val cleaned = raw
                ?.trim()
                ?.replace("\\/", "/")
                ?.replace("\\u0026", "&")
                ?.replace("&amp;", "&")
                ?.trim()
                ?: return
            if (cleaned.isBlank()) return
            val resolved = resolveRelative(pageUrl, cleaned)
            val lower = resolved.lowercase().substringBefore('?')
            if (isDirectMediaExtension(lower) || lower.endsWith(".m3u8") || lower.endsWith(".mpd")) {
                found.add(resolved)
            }
        }

        IFRAME_SRC_REGEX.findAll(html).forEach { addCandidate(it.groupValues[1]) }
        JSON_FILE_REGEX.findAll(html).forEach { addCandidate(it.groupValues[1]) }
        SOURCE_SRC_REGEX.findAll(html).forEach { addCandidate(it.groupValues[1]) }
        BARE_URL_REGEX.findAll(html).forEach { addCandidate(it.groupValues[0]) }
        return found.toList()
    }

    private fun isDirectMediaExtension(lowerUrl: String): Boolean {
        return lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".webm") ||
            lowerUrl.endsWith(".mkv") || lowerUrl.endsWith(".mov") ||
            lowerUrl.endsWith(".avi") || lowerUrl.endsWith(".flv") ||
            lowerUrl.endsWith(".ts")
    }

    private fun looksLikeMediaUrl(url: String): Boolean {
        val lower = url.lowercase().substringBefore('?')
        return isDirectMediaExtension(lower) || lower.endsWith(".m3u8") || lower.endsWith(".mpd")
    }

    companion object {
        private const val MAX_DEPTH = 3
        private const val PROBE_TIMEOUT_MS = 20_000L
        private const val FETCH_TIMEOUT_MS = 30_000L
        private const val MAX_BODY_CHARS = 2_000_000

        private val BANDWIDTH_REGEX = Regex("BANDWIDTH=(\\d+)")
        private val RESOLUTION_REGEX = Regex("RESOLUTION=(\\d+)x(\\d+)")
        private val IFRAME_SRC_REGEX =
            Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        private val JSON_FILE_REGEX = Regex(
            "[\"'](?:file|source|url|src)[\"']\\s*:\\s*[\"']([^\"']+)[\"']",
            RegexOption.IGNORE_CASE,
        )
        private val SOURCE_SRC_REGEX = Regex(
            "<source[^>]+src=[\"']([^\"']+)[\"']",
            RegexOption.IGNORE_CASE,
        )
        private val BARE_URL_REGEX = Regex(
            "https?://[^\\s\"'<>]+\\.(?:m3u8|mp4|webm|mkv|mov)(?:\\?[^\\s\"'<>]*)?",
            RegexOption.IGNORE_CASE,
        )
    }
}
