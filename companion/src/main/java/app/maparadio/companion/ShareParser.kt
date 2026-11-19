package app.maparadio.companion

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Lee una ubicacion compartida desde Google Maps (o un geo:).
 *
 * Formas que llegan al compartir desde Google Maps:
 * - geo:18.4861,-69.9312?q=Nombre
 * - https://www.google.com/maps/place/Nombre/@18.4861,-69.9312,17z/...
 * - https://www.google.com/maps/@18.4861,-69.9312,17z
 * - https://www.google.com/maps/search/?api=1&query=18.4861,-69.9312
 * - https://maps.app.goo.gl/XXXX (enlace corto: se resuelve el
 *   redirect con una peticion y se lee la URL final)
 * - Texto con el enlace dentro ("Nombre\nhttps://maps.app.goo.gl/...")
 *
 * Devuelve (nombre, lat, lng) o null si no se pudo leer nada. Solo
 * Republica Dominicana: fuera del cuadro se rechaza.
 */
object ShareParser {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val COORD = Pattern.compile("(-?\\d{1,2}\\.\\d+),(-?\\d{1,3}\\.\\d+)")

    data class SharedPlace(val name: String, val lat: Double, val lng: Double)

    fun parse(sharedText: String?): SharedPlace? {
        if (sharedText.isNullOrBlank()) return null
        val text = sharedText.trim()
        // 1) geo: directo.
        if (text.startsWith("geo:", ignoreCase = true)) {
            return parseGeo(text)
        }
        // 2) Sacar todos los enlaces del texto y probar uno por uno.
        val urls = extractUrls(text)
        for (url in urls) {
            val place = parseUrl(url) ?: continue
            return place
        }
        // 3) Coordenadas sueltas en el texto ("18.4861, -69.9312").
        val m = COORD.matcher(text)
        if (m.find()) {
            val lat = m.group(1)!!.toDoubleOrNull() ?: return null
            val lng = m.group(2)!!.toDoubleOrNull() ?: return null
            return validPlace("Destino", lat, lng)
        }
        return null
    }

    private fun extractUrls(text: String): List<String> {
        val out = ArrayList<String>()
        val m = Pattern.compile("(geo:[^\\s]+|https?://[^\\s]+)").matcher(text)
        while (m.find()) {
            // Quitar puntuacion final que el chat/app pega al enlace.
            out.add(m.group(1)!!.trimEnd('.', ',', ')', ']'))
        }
        return out
    }

    private fun parseGeo(uri: String): SharedPlace? {
        val after = uri.substringAfter("geo:", "")
        val coordsPart = after.substringBefore("?")
        val m = COORD.matcher(coordsPart)
        if (!m.find()) {
            // geo:0,0?q=18.4861,-69.9312(Nombre)
            val q = queryParam(uri, "q") ?: return null
            val qm = COORD.matcher(q)
            if (!qm.find()) return null
            val lat = qm.group(1)!!.toDoubleOrNull() ?: return null
            val lng = qm.group(2)!!.toDoubleOrNull() ?: return null
            return validPlace(nameFromQ(q), lat, lng)
        }
        val lat = m.group(1)!!.toDoubleOrNull() ?: return null
        val lng = m.group(2)!!.toDoubleOrNull() ?: return null
        val name = queryParam(uri, "q")?.let { nameFromQ(it) } ?: "Destino"
        return validPlace(name, lat, lng)
    }

    private fun parseUrl(url: String): SharedPlace? {
        var target = url
        // Enlace corto de Google Maps: resolver el redirect y leer la final.
        if (target.contains("maps.app.goo.gl")) {
            target = resolveShortLink(target) ?: return null
        }
        if (!target.contains("google.com/maps") && !target.contains("maps.google.com")) {
            return null
        }
        // /place/Nombre/@lat,lng o /@lat,lng
        val atIndex = target.indexOf("/@")
        if (atIndex >= 0) {
            val after = target.substring(atIndex + 2)
            val m = COORD.matcher(after)
            if (m.find()) {
                val lat = m.group(1)!!.toDoubleOrNull() ?: return null
                val lng = m.group(2)!!.toDoubleOrNull() ?: return null
                return validPlace(nameFromPlaceUrl(target), lat, lng)
            }
        }
        // ?q=lat,lng, ?query=lat,lng o ?api=1&query=Nombre
        val q = queryParam(target, "q") ?: queryParam(target, "query")
        if (q != null) {
            val m = COORD.matcher(q)
            if (m.find()) {
                val lat = m.group(1)!!.toDoubleOrNull() ?: return null
                val lng = m.group(2)!!.toDoubleOrNull() ?: return null
                return validPlace(nameFromQ(q), lat, lng)
            }
            // query con nombre pero sin coordenadas: no sirve.
            return null
        }
        return null
    }

    /** Sigue el redirect del enlace corto y devuelve la URL final. */
    private fun resolveShortLink(url: String): String? {
        return try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "MapaRadioMovil/1.0-test")
                .build()
            http.newCall(req).execute().use { resp ->
                resp.request.url.toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryParam(url: String, key: String): String? {
        val query = url.substringAfter("?", "").substringBefore("#")
        for (pair in query.split("&")) {
            val kv = pair.split("=", limit = 2)
            if (kv.size == 2 && kv[0] == key) {
                return try {
                    URLDecoder.decode(kv[1], "UTF-8")
                } catch (_: Exception) {
                    kv[1]
                }
            }
        }
        return null
    }

    /** "18.4861,-69.9312(Nombre)" o "Nombre" -> nombre legible. */
    private fun nameFromQ(q: String): String {
        val paren = Regex("\\(([^)]+)\\)").find(q)
        if (paren != null) return paren.groupValues[1].trim()
        val clean = q.replace(COORD.toRegex(), "").trim(' ', ',', '+')
        return clean.ifEmpty { "Destino" }
    }

    /** "/place/Nombre/@lat,lng" -> "Nombre" (o "Destino"). */
    private fun nameFromPlaceUrl(url: String): String {
        val m = Regex("/place/([^/]+)/").find(url) ?: return "Destino"
        return try {
            URLDecoder.decode(m.groupValues[1], "UTF-8")
                .replace('+', ' ').trim()
        } catch (_: Exception) {
            "Destino"
        }.ifEmpty { "Destino" }
    }

    private fun validPlace(name: String, lat: Double, lng: Double): SharedPlace? {
        if (lat !in 17.4..20.0 || lng !in -72.1..-68.5) return null
        return SharedPlace(name.ifEmpty { "Destino" }, lat, lng)
    }
}
