package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * MangaDex challenges browser-like User-Agents (the app's device WebView
 * agent, set by [UserAgentInterceptor]) with a JS proof-of-work page
 * ("Browser verification", POWBlock) instead of JSON/images; OkHttp
 * cannot solve it, so the cover picker found nothing on MangaDex.
 * Plain API-style agents are served normally (verified: "Tadami/..."
 * and no UA get JSON 200 + real JPEGs, while the WebView UA gets a 302
 * to the challenge page on api.mangadex.org, /cover and
 * uploads.mangadex.org).
 *
 * This interceptor runs after [UserAgentInterceptor] and, only for
 * *.mangadex.org hosts, swaps the agent for a plain one and strips the
 * browser client-hint headers. Every other host is untouched. It fixes
 * the cover picker (API + thumbnails via Coil) and setCoverFromUrl in
 * one place, because all of them use the shared NetworkHelper client.
 */
class MangaDexUserAgentInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        if (host != "mangadex.org" && !host.endsWith(".mangadex.org")) {
            return chain.proceed(request)
        }
        val builder = request.newBuilder()
            .header("User-Agent", MANGADEX_USER_AGENT)
            .removeHeader("sec-ch-ua")
            .removeHeader("sec-ch-ua-mobile")
            .removeHeader("sec-ch-ua-platform")
        return chain.proceed(builder.build())
    }

    companion object {
        const val MANGADEX_USER_AGENT = "Tadami"
    }
}
