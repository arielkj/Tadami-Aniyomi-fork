package app.maparadio.android

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Busqueda de lugares con Photon (OpenStreetMap, tolerante a errores de
 * escritura, sin clave). La busqueda es en linea en v1; la version del radio
 * usara un indice offline de RD con la misma forma de resultados.
 */
data class SearchResult(
    val name: String,
    val detail: String,
    val lat: Double,
    val lon: Double,
    val inDominicanRepublic: Boolean,
)

object PhotonApi {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val BASE = "https://photon.komoot.io"

    fun search(query: String, lat: Double, lon: Double, limit: Int = 8): List<SearchResult> {
        val q = URLEncoder.encode(query, "UTF-8")
        // countrycode=DO: el buscador es SOLO Republica Dominicana (v1.2).
        val url = "$BASE/api/?q=$q&lat=$lat&lon=$lon&limit=$limit&countrycode=DO"
        val req = Request.Builder().url(url)
            .header("User-Agent", "MapaRadio/1.2-test")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string() ?: return emptyList()
            return parseResults(JSONObject(body))
        }
    }

    /** Nombre de calle o lugar en una coordenada (para el long-press). */
    fun reverse(lat: Double, lon: Double): String? {
        val url = "$BASE/reverse?lat=$lat&lon=$lon"
        val req = Request.Builder().url(url)
            .header("User-Agent", "MapaRadio/1.0-test")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val features = JSONObject(body).optJSONArray("features") ?: return null
            if (features.length() == 0) return null
            val props = features.optJSONObject(0)?.optJSONObject("properties") ?: return null
            return props.optString("name").takeIf { it.isNotEmpty() }
                ?: props.optString("street").takeIf { it.isNotEmpty() }
        }
    }

    private fun parseResults(json: JSONObject): List<SearchResult> {
        val features = json.optJSONArray("features") ?: return emptyList()
        val out = ArrayList<SearchResult>()
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val props = f.optJSONObject("properties") ?: continue
            val name = props.optString("name").takeIf { it.isNotEmpty() } ?: continue
            val geom = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            val lon = geom.optDouble(0)
            val lat = geom.optDouble(1)
            // Red de seguridad (v1.2): aunque Photon ya filtra con
            // countrycode=DO, aqui se descarta todo lo que caiga fuera
            // del cuadro de RD o cuyo pais no sea DO.
            val country = props.optString("countrycode")
            if (country.isNotEmpty() && country != "DO") continue
            if (lat !in 17.5..19.9 || lon !in -72.0..-68.6) continue
            val street = props.optString("street").takeIf { it.isNotEmpty() }
            val city = props.optString("city").takeIf { it.isNotEmpty() }
                ?: props.optString("locality").takeIf { it.isNotEmpty() }
            val type = props.optString("osm_value").takeIf { it.isNotEmpty() }
            val detail = listOfNotNull(street, city, type).joinToString(" • ")
            out.add(
                SearchResult(
                    name = name,
                    detail = detail,
                    lat = lat,
                    lon = lon,
                    inDominicanRepublic = props.optString("countrycode") == "DO",
                )
            )
        }
        // Primero los de Republica Dominicana.
        return out.sortedByDescending { it.inDominicanRepublic }
    }
}
