package app.maparadio.android

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Ruta completa calculada en el telefono (v1.0.3-test): el telefono
 * calcula la ruta con OSRM (tiene internet) y manda TODA la ruta al
 * radio, que solo la dibuja y la guia. El radio ya no tiene que
 * calcularla (su GraphHopper offline tarda minutos en cargar).
 *
 * Formato JSON compacto (el mismo en el telefono y en el radio;
 * este archivo existe IDENTICO en los dos modulos, solo cambia el
 * paquete — verificar con diff antes de entregar):
 *   {"type":"route","name":"...","distance":150875.0,"duration":7066.0,
 *    "points":[[18.48610,-69.93120],...],
 *    "steps":[{"t":"turn","m":"right","n":"Av. San Martín",
 *              "d":330.0,"s":367.0},...]}
 * - points: [lat, lng] redondeados a 5 decimales (~1 m, suficiente
 *   para guiar; medido SD -> Santiago: 1.958 puntos OSRM, 307 tras
 *   afinar, JSON de 7.803 bytes).
 * - steps: t = tipo de maniobra OSRM, m = modificador (o null),
 *   n = nombre de la calle, d = metros del paso, s = metros desde el
 *   inicio hasta que empieza el paso (igual que RouteStep del radio).
 * - El mensaje de destino solo ({"name","lat","lng"}, sin "type")
 *   sigue existiendo y se distingue por no traer "type":"route".
 */
data class RouteStepData(
    val type: String,
    val modifier: String?,
    val name: String,
    val distance: Double,
    val startMeters: Double,
)

data class RouteData(
    val name: String,
    val distance: Double,
    val duration: Double,
    /** Puntos [lat, lng] en orden, del origen al destino. */
    val points: List<Pair<Double, Double>>,
    val steps: List<RouteStepData>,
)

object PhoneRouteJson {

    const val TYPE_ROUTE = "route"

    /** True si el texto es un mensaje de ruta (no un destino solo). */
    fun isRouteMessage(json: String): Boolean {
        return try {
            JSONObject(json).optString("type") == TYPE_ROUTE
        } catch (_: Exception) {
            false
        }
    }

    /** Codifica la ruta a JSON compacto (coordenadas a 5 decimales). */
    fun encode(data: RouteData): String {
        val json = JSONObject()
        json.put("type", TYPE_ROUTE)
        json.put("name", data.name)
        json.put("distance", data.distance)
        json.put("duration", data.duration)
        val pts = JSONArray()
        for ((lat, lng) in data.points) {
            val p = JSONArray()
            p.put(round5(lat))
            p.put(round5(lng))
            pts.put(p)
        }
        json.put("points", pts)
        val st = JSONArray()
        for (s in data.steps) {
            val o = JSONObject()
            o.put("t", s.type)
            if (s.modifier != null) o.put("m", s.modifier)
            o.put("n", s.name)
            o.put("d", s.distance)
            o.put("s", s.startMeters)
            st.put(o)
        }
        json.put("steps", st)
        return json.toString()
    }

    /** Decodifica y valida una ruta. Null si no vale (JSON roto,
     *  menos de 2 puntos, numeros no finitos o puntos fuera de RD). */
    fun decode(json: String): RouteData? {
        return try {
            val obj = JSONObject(json)
            if (obj.optString("type") != TYPE_ROUTE) return null
            val name = obj.optString("name").takeIf { it.isNotEmpty() } ?: "Destino"
            val distance = obj.optDouble("distance", Double.NaN)
            val duration = obj.optDouble("duration", Double.NaN)
            if (!distance.isFinite() || distance < 0) return null
            if (!duration.isFinite() || duration < 0) return null
            val ptsJson = obj.optJSONArray("points") ?: return null
            if (ptsJson.length() < 2) return null
            val points = ArrayList<Pair<Double, Double>>(ptsJson.length())
            for (i in 0 until ptsJson.length()) {
                val p = ptsJson.optJSONArray(i) ?: return null
                val lat = p.optDouble(0, Double.NaN)
                val lng = p.optDouble(1, Double.NaN)
                if (!lat.isFinite() || !lng.isFinite()) return null
                // Cuadro amplio de RD (la ruta puede rozar la costa o
                // la frontera); el destino final se valida aparte con
                // el cuadro estricto, igual que el destino solo.
                if (lat !in 17.0..20.5 || lng !in -72.5..-68.0) return null
                points.add(lat to lng)
            }
            val stepsJson = obj.optJSONArray("steps") ?: JSONArray()
            val steps = ArrayList<RouteStepData>(stepsJson.length())
            for (i in 0 until stepsJson.length()) {
                val s = stepsJson.optJSONObject(i) ?: return null
                val type = s.optString("t").takeIf { it.isNotEmpty() } ?: return null
                val d = s.optDouble("d", Double.NaN)
                val start = s.optDouble("s", Double.NaN)
                if (!d.isFinite() || d < 0 || !start.isFinite() || start < 0) return null
                steps.add(
                    RouteStepData(
                        type = type,
                        modifier = s.optString("m").takeIf { it.isNotEmpty() },
                        name = s.optString("n"),
                        distance = d,
                        startMeters = start,
                    )
                )
            }
            RouteData(name, distance, duration, points, steps)
        } catch (_: Exception) {
            null
        }
    }

    private fun round5(v: Double): Double =
        (v * 100000.0).roundToInt() / 100000.0
}
