package app.maparadio.companion

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Lee una ubicacion compartida desde Google Maps (o un geo:).
 *
 * Formas que llegan al compartir desde Google Maps (todas se intentan):
 * - geo:18.4861,-69.9312?q=Nombre
 * - https://www.google.com/maps/place/Nombre/@18.4861,-69.9312,17z/...
 * - https://www.google.com/maps/@18.4861,-69.9312,17z
 * - https://www.google.com/maps/search/?api=1&query=18.4861,-69.9312
 * - https://www.google.com/maps/search/?api=1&query=Nombre
 * - https://www.google.com/maps/dir/?api=1&destination=18.4861,-69.9312
 * - URLs con data=...!3d18.4861!4d-69.9312... (las coordenadas que
 *   Google esconde en el parametro data=, a veces en orden inverso
 *   !4d<lng>!3d<lat>)
 * - https://maps.app.goo.gl/XXXX (enlace corto: se resuelve el
 *   redirect con una peticion y se lee la URL final; si la respuesta
 *   es una pagina HTML, se buscan enlaces a google.com/maps dentro)
 * - Texto con el enlace dentro ("Nombre\nhttps://maps.app.goo.gl/...")
 *
 * Si no hay coordenadas en ningun sitio pero el texto trae un nombre
 * de lugar, se busca ese nombre en Photon (solo RD) y se usa el
 * primer resultado. Solo Republica Dominicana: fuera del cuadro se
 * rechaza.
 *
 * parse() hace red (acortador + Photon) y debe llamarse fuera del
 * hilo principal.
 */
object ShareParser {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Sin seguir redirects: para inspeccionar cada Location del acortador.
    private val httpNoRedirect = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val COORD = Pattern.compile("(-?\\d{1,2}\\.\\d+),(-?\\d{1,3}\\.\\d+)")
    private val DATA_3D4D = Pattern.compile("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)")
    private val DATA_4D3D = Pattern.compile("!4d(-?\\d+(?:\\.\\d+)?)!3d(-?\\d+(?:\\.\\d+)?)")
    private val MAPS_HREF = Pattern.compile(
        "https?://(?:www\\.)?(?:google\\.com/maps|maps\\.google\\.com)[^\"'\\s<>]+",
        Pattern.CASE_INSENSITIVE,
    )

    data class SharedPlace(val name: String, val lat: Double, val lng: Double)

    sealed class ParseResult {
        data class Found(val place: SharedPlace) : ParseResult()
        data class NotFound(val received: String) : ParseResult()
    }

    fun parse(sharedText: String?): ParseResult {
        if (sharedText.isNullOrBlank()) return ParseResult.NotFound("")
        val text = sharedText.trim()
        // 1) geo: directo.
        if (text.startsWith("geo:", ignoreCase = true)) {
            val place = parseGeo(text)
            if (place != null) return ParseResult.Found(place)
        }
        // 2) Sacar todos los enlaces del texto y probar uno por uno.
        val urls = extractUrls(text)
        for (url in urls) {
            val place = parseUrl(url) ?: continue
            return ParseResult.Found(place)
        }
        // 3) Coordenadas sueltas en el texto ("18.4861, -69.9312").
        val m = COORD.matcher(text)
        if (m.find()) {
            val lat = m.group(1)!!.toDoubleOrNull()
            val lng = m.group(2)!!.toDoubleOrNull()
            if (lat != null && lng != null) {
                val place = validPlace("Destino", lat, lng)
                if (place != null) return ParseResult.Found(place)
            }
        }
        // 4) Sin coordenadas: el texto trae un nombre -> buscarlo en
        //    Photon (solo RD) y usar el primer resultado.
        val name = extractCandidateName(text, urls)
        if (!name.isNullOrBlank()) {
            val place = resolveByName(name)
            if (place != null) return ParseResult.Found(place)
        }
        // 5) Nada funciono: devolver lo recibido para diagnosticar.
        return ParseResult.NotFound(text.take(300))
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
        // Enlace corto de Google Maps: resolver y probar cada URL de
        // la cadena de redirects + la final + enlaces dentro del HTML.
        val candidates = if (url.contains("maps.app.goo.gl")) {
            resolveShortLink(url)
        } else {
            listOf(url)
        }
        for (target in candidates) {
            parseMapsUrl(target)?.let { return it }
        }
        return null
    }

    /** Una URL de google.com/maps (ya resuelta) -> lugar o null. */
    private fun parseMapsUrl(url: String): SharedPlace? {
        if (!url.contains("google.com/maps") && !url.contains("maps.google.com")) {
            return null
        }
        val decoded = urlDecodeAll(url)
        // 1) data=...!3d<lat>!4d<lng>... (o en orden inverso).
        DATA_3D4D.matcher(decoded).let { m ->
            if (m.find()) {
                val lat = m.group(1)!!.toDoubleOrNull()
                val lng = m.group(2)!!.toDoubleOrNull()
                if (lat != null && lng != null) {
                    validPlace(nameFromPlaceUrl(decoded), lat, lng)?.let { return it }
                }
            }
        }
        DATA_4D3D.matcher(decoded).let { m ->
            if (m.find()) {
                val lng = m.group(1)!!.toDoubleOrNull()
                val lat = m.group(2)!!.toDoubleOrNull()
                if (lat != null && lng != null) {
                    validPlace(nameFromPlaceUrl(decoded), lat, lng)?.let { return it }
                }
            }
        }
        // 2) /place/Nombre/@lat,lng o /@lat,lng
        val atIndex = decoded.indexOf("/@")
        if (atIndex >= 0) {
            val after = decoded.substring(atIndex + 2)
            val m = COORD.matcher(after)
            if (m.find()) {
                val lat = m.group(1)!!.toDoubleOrNull()
                val lng = m.group(2)!!.toDoubleOrNull()
                if (lat != null && lng != null) {
                    validPlace(nameFromPlaceUrl(decoded), lat, lng)?.let { return it }
                }
            }
        }
        // 3) ?q=, ?query=, ?destination= (coordenadas o nombre).
        //    Con coordenadas -> lugar. Con nombre -> null aqui; el
        //    fallback por nombre lo hace parse() con extractCandidateName.
        for (key in listOf("destination", "query", "q")) {
            val value = queryParam(decoded, key) ?: continue
            val m = COORD.matcher(value)
            if (m.find()) {
                val lat = m.group(1)!!.toDoubleOrNull()
                val lng = m.group(2)!!.toDoubleOrNull()
                if (lat != null && lng != null) {
                    validPlace(nameFromQ(value), lat, lng)?.let { return it }
                }
            }
        }
        // 4) Ultimo recurso: cualquier par lat,lng en la URL.
        val m = COORD.matcher(decoded)
        if (m.find()) {
            val lat = m.group(1)!!.toDoubleOrNull()
            val lng = m.group(2)!!.toDoubleOrNull()
            if (lat != null && lng != null) {
                validPlace(nameFromPlaceUrl(decoded), lat, lng)?.let { return it }
            }
        }
        return null
    }

    /**
     * Sigue el redirect del enlace corto a mano (max 5 saltos) y
     * devuelve todas las URLs candidatas: cada Location intermedio,
     * la URL final, y cualquier enlace a google.com/maps encontrado
     * en el HTML de la respuesta final (pagina de consentimiento o
     * intermedia). Una sola peticion por intento.
     */
    private fun resolveShortLink(url: String): List<String> {
        val candidates = ArrayList<String>()
        var current = url
        try {
            for (hop in 0 until 5) {
                val req = Request.Builder().url(current)
                    .header("User-Agent", "MapaRadioMovil/1.0.1-test")
                    .build()
                httpNoRedirect.newCall(req).execute().use { resp ->
                    val location = resp.header("Location")
                    val body = if (resp.code in 200..299) {
                        resp.body?.string()
                    } else {
                        null
                    }
                    if (location != null) {
                        val next = absoluteUrl(current, location)
                        candidates.add(next)
                        current = next
                        return@use
                    }
                    // Respuesta final: la URL + enlaces dentro del HTML.
                    candidates.add(resp.request.url.toString())
                    if (body != null) {
                        val hm = MAPS_HREF.matcher(body)
                        while (hm.find()) {
                            candidates.add(urlDecodeAll(hm.group()!!.trimEnd('.', ',', ')', ']')))
                        }
                    }
                    return candidates
                }
            }
            // Demasiados saltos: probar la ultima URL igual.
            candidates.add(current)
        } catch (_: Exception) {
            // Sin red o error: probar la URL original como ultimo recurso.
            if (candidates.isEmpty()) candidates.add(url)
        }
        return candidates
    }

    private fun absoluteUrl(base: String, location: String): String {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location
        }
        return try {
            URI(base).resolve(location).toString()
        } catch (_: Exception) {
            location
        }
    }

    /** Decodifica %XX repetidas veces (data= viene a veces doble). */
    private fun urlDecodeAll(s: String): String {
        var out = s
        repeat(3) {
            try {
                val decoded = URLDecoder.decode(out, "UTF-8")
                if (decoded == out) return out
                out = decoded
            } catch (_: Exception) {
                return out
            }
        }
        return out
    }

    /**
     * Nombre candidato para el fallback por Photon: la primera linea
     * del texto que no sea un enlace, o el /place/Nombre/ de la URL,
     * o el valor de q/query/destination si es un nombre.
     */
    private fun extractCandidateName(text: String, urls: List<String>): String? {
        for (line in text.lines()) {
            val clean = line.trim()
            if (clean.isEmpty()) continue
            if (clean.startsWith("http://") || clean.startsWith("https://")
                || clean.startsWith("geo:", ignoreCase = true)
            ) {
                continue
            }
            // Quitar coordenadas sueltas y dejar el nombre.
            val name = clean.replace(COORD.toRegex(), "").trim(' ', ',', '+', '-', '–')
            if (name.length >= 3) return name.take(80)
        }
        for (url in urls) {
            val decoded = urlDecodeAll(url)
            val placeName = nameFromPlaceUrl(decoded)
            if (placeName != "Destino") return placeName
            for (key in listOf("destination", "query", "q")) {
                val value = queryParam(decoded, key) ?: continue
                val clean = nameFromQ(value)
                if (clean != "Destino" && clean.length >= 3) return clean.take(80)
            }
        }
        return null
    }

    /** Busca el nombre en Photon (solo RD) y usa el primer resultado. */
    private fun resolveByName(name: String): SharedPlace? {
        return try {
            val results = PhotonSearch.search(name, limit = 3)
            val first = results.firstOrNull() ?: return null
            SharedPlace("Destino por nombre: $name", first.lat, first.lon)
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
        val m = Regex("/place/([^/]+)").find(url) ?: return "Destino"
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
