package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.network.toAsciiUrl
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

object CoverRequestPolicy {

    const val HEADER_COVER_REQUEST = "X-Aurora-Cover-Request"
    const val HEADER_COVER_FALLBACK_URL = "X-Aurora-Cover-Fallback-Url"
    const val HEADER_COVER_ATTEMPT = "X-Aurora-Cover-Attempt"

    private const val FAILURE_THRESHOLD = 2

    private data class HostState(
        var failures: Int = 0,
    )

    private val hostStates = ConcurrentHashMap<String, HostState>()

    fun isCoverRequest(request: Request): Boolean {
        return request.header(HEADER_COVER_REQUEST) == "1"
    }

    fun coverFallbackUrl(request: Request): String? {
        return request.header(HEADER_COVER_FALLBACK_URL)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun coverAttempt(request: Request): Int {
        return request.header(HEADER_COVER_ATTEMPT)
            ?.trim()
            ?.toIntOrNull()
            ?: 0
    }

    fun markCoverRequest(
        builder: Request.Builder,
        fallbackUrl: String? = null,
        attempt: Int = 0,
    ): Request.Builder {
        builder.header(HEADER_COVER_REQUEST, "1")
        builder.header(HEADER_COVER_ATTEMPT, attempt.toString())
        if (!fallbackUrl.isNullOrBlank()) {
            builder.header(HEADER_COVER_FALLBACK_URL, fallbackUrl.toAsciiUrl())
        }
        return builder
    }

    fun isRecoverableResponseCode(code: Int): Boolean {
        return code in setOf(403, 404, 429, 500, 502, 503)
    }

    fun recordFailure(host: String) {
        val state = hostStates.getOrPut(host) { HostState() }
        synchronized(state) {
            state.failures += 1
        }
    }

    fun clear(host: String) {
        hostStates.remove(host)
    }

    /**
     * Drops every recorded failure (e.g. when connectivity is restored): hosts
     * blacklisted by a broken network (bad VPN exit, captive portal) get one
     * honest retry instead of staying pinned to the placeholder until the
     * process restarts.
     */
    fun clearAll() {
        hostStates.clear()
    }

    fun isBlacklisted(host: String): Boolean {
        val state = hostStates[host] ?: return false
        synchronized(state) {
            return state.failures >= FAILURE_THRESHOLD
        }
    }

    // V7.8: caché negativa por URL con TTL corto. El interceptor no registra
    // IOExceptions (son transitorias) y Coil no cachea fallos, así que una
    // portada con el host caído o la URL muerta repetía 2 intentos de red
    // (hasta 25 s de timeout cada uno + 500 ms de espera) CADA VEZ que su
    // tarjeta entraba en pantalla al hacer scroll, ocupando los hilos
    // fetcher y retrasando las portadas buenas. Con esto, una URL que falla
    // no vuelve a tocar la red durante 60 s; pasado el TTL se reintenta
    // sola, y un éxito la desbloquea al instante. No afecta a las portadas
    // que ya están en caché (archivo o disco): esas nunca llegan aquí.
    private const val URL_FAILURE_TTL_MS = 60_000L
    private const val URL_FAILURE_MAX_ENTRIES = 512

    private val urlFailures = ConcurrentHashMap<String, Long>()

    fun isUrlRecentlyFailed(url: String): Boolean {
        val at = urlFailures[url] ?: return false
        if (System.currentTimeMillis() - at > URL_FAILURE_TTL_MS) {
            urlFailures.remove(url, at)
            return false
        }
        return true
    }

    fun recordUrlFailure(url: String) {
        if (urlFailures.size >= URL_FAILURE_MAX_ENTRIES) {
            val now = System.currentTimeMillis()
            urlFailures.entries.removeIf { now - it.value > URL_FAILURE_TTL_MS }
            if (urlFailures.size >= URL_FAILURE_MAX_ENTRIES) urlFailures.clear()
        }
        urlFailures[url] = System.currentTimeMillis()
    }

    fun clearUrlFailure(url: String) {
        urlFailures.remove(url)
    }

    fun resetForTests() {
        clearAll()
        urlFailures.clear()
    }
}
