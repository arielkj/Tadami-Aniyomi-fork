package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class CoverRecoveryInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!CoverRequestPolicy.isCoverRequest(request)) {
            return chain.proceed(request)
        }

        val host = request.url.host
        if (CoverRequestPolicy.isBlacklisted(host)) {
            throw IOException("Skipped blacklisted cover host: $host")
        }

        return try {
            val response = chain.proceed(request)
            if (response.isSuccessful) {
                CoverRequestPolicy.clear(host)
            } else if (CoverRequestPolicy.isRecoverableResponseCode(response.code)) {
                // v7.9.20: un 404/403 de una sonda de variante mejorada no
                // es un fallo del host — la variante simplemente no existe.
                // Antes, cada póster de anime probaba la variante primero y
                // su 404 sumaba un fallo; con dos fallos el host entero
                // (p. ej. el CDN de AniList) quedaba en lista negra para
                // todo el proceso y ninguna portada de ese host volvía a
                // cargar. Solo las URLs reales cuentan para la lista negra.
                if (!CoverRequestPolicy.isCoverProbe(request)) {
                    CoverRequestPolicy.recordFailure(host)
                }
            }
            response
        } catch (e: IOException) {
            // Keep transient network failures from poisoning the host blacklist.
            throw e
        }
    }
}
