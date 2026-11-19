package app.maparadio.android

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.mapbox.mapboxsdk.geometry.LatLng
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Ruta OSRM + matematicas de pegado a la calle (snap-to-road).
 *
 * v1 usa el servidor demo de OSRM (en linea). La version del radio usara
 * GraphHopper offline; la interfaz aqui es la misma idea: una ruta es una
 * polilinea con pasos (maniobras) y distancias acumuladas.
 */
data class RouteStep(
    val type: String,
    val modifier: String?,
    val name: String,
    /** Metros que dura este paso. */
    val distance: Double,
    /** Metros desde el inicio de la ruta hasta que empieza este paso. */
    val startMeters: Double,
)

data class Route(
    val points: List<LatLng>,
    val steps: List<RouteStep>,
    val totalDistance: Double,
    val totalDuration: Double,
    /** Distancia acumulada (m) en cada punto de la polilinea. */
    val cumulative: DoubleArray,
    /** Calle principal de la ruta (el paso con nombre mas largo), para
        mostrarla en la previsualizacion. Vacio si no hay nombre. */
    val viaName: String = "",
)

data class SnapResult(
    /** Indice del punto de la ruta mas cercano. */
    val index: Int,
    /** Punto proyectado sobre la ruta. */
    val snapped: LatLng,
    /** Distancia (m) del GPS crudo a la ruta. */
    val distance: Double,
    /** Distancia (m) recorrida desde el inicio de la ruta. */
    val progressMeters: Double,
    /** Rumbo de la ruta en ese punto, en grados 0-360. */
    val bearing: Double,
)

object RouteEngine {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun fetchRoute(from: LatLng, to: LatLng): Route? {
        return fetchRoutes(from, to)?.firstOrNull()
    }

    /**
     * Rutas ALTERNATIVAS (v1.7): OSRM devuelve hasta 3 rutas para el
     * mismo destino (alternatives=true). La primera es la mas rapida
     * (la que devolvia fetchRoute). Si no hay internet o OSRM falla,
     * devuelve null y el llamador muestra el aviso sin romper nada.
     */
    fun fetchRoutes(from: LatLng, to: LatLng): List<Route>? {
        val url = "https://router.project-osrm.org/route/v1/driving/" +
            "${from.longitude},${from.latitude};${to.longitude},${to.latitude}" +
            "?overview=full&geometries=geojson&steps=true&alternatives=true"
        val req = Request.Builder().url(url)
            .header("User-Agent", "MapaRadio/1.7-test")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            return parseRoutes(JSONObject(body))
        }
    }

    /**
     * Pegado a la calle SIN ruta (v1.3): OSRM Match devuelve la traza
     * pegada a la calle mas cercana para una lista de puntos GPS.
     * Se llama como mucho cada 5 s y solo en movimiento; si falla o no
     * hay internet, el llamador usa el punto filtrado por Kalman.
     * [radiuses] es la precision (m) de cada punto.
     */
    fun matchPoints(points: List<LatLng>, radiuses: List<Float>): List<LatLng>? {
        if (points.isEmpty() || points.size != radiuses.size) return null
        val coords = points.joinToString(";") { "${it.longitude},${it.latitude}" }
        val rad = radiuses.joinToString(";") { it.coerceIn(5f, 100f).toInt().toString() }
        val url = "https://router.project-osrm.org/match/v1/driving/$coords" +
            "?radiuses=$rad&overview=full&geometries=geojson"
        val req = Request.Builder().url(url)
            .header("User-Agent", "MapaRadio/1.3-test")
            .build()
        return try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                if (json.optString("code") != "Ok") return null
                val matchings = json.optJSONArray("matchings") ?: return null
                if (matchings.length() == 0) return null
                val coordsArr = matchings.optJSONObject(0)
                    ?.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
                val out = ArrayList<LatLng>(coordsArr.length())
                for (i in 0 until coordsArr.length()) {
                    val c = coordsArr.optJSONArray(i) ?: continue
                    out.add(LatLng(c.optDouble(1), c.optDouble(0)))
                }
                if (out.isEmpty()) null else out
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Todas las rutas del JSON de OSRM (hasta 3 con alternatives=true). */
    private fun parseRoutes(json: JSONObject): List<Route>? {
        if (json.optString("code") != "Ok") return null
        val routesJson = json.optJSONArray("routes") ?: return null
        val out = ArrayList<Route>()
        for (ri in 0 until minOf(routesJson.length(), 3)) {
            parseOneRoute(routesJson.optJSONObject(ri))?.let { out.add(it) }
        }
        return if (out.isEmpty()) null else out
    }

    private fun parseOneRoute(routeJson: JSONObject?): Route? {
        if (routeJson == null) return null
        val coords = routeJson.optJSONObject("geometry")?.optJSONArray("coordinates")
            ?: return null
        val points = ArrayList<LatLng>(coords.length())
        for (i in 0 until coords.length()) {
            val c = coords.optJSONArray(i) ?: continue
            points.add(LatLng(c.optDouble(1), c.optDouble(0)))
        }
        if (points.size < 2) return null

        val steps = ArrayList<RouteStep>()
        val legs = routeJson.optJSONArray("legs")
        var startMeters = 0.0
        if (legs != null) {
            for (li in 0 until legs.length()) {
                val legSteps = legs.optJSONObject(li)?.optJSONArray("steps") ?: continue
                for (si in 0 until legSteps.length()) {
                    val s = legSteps.optJSONObject(si) ?: continue
                    val man = s.optJSONObject("maneuver")
                    steps.add(
                        RouteStep(
                            type = man?.optString("type") ?: "continue",
                            modifier = man?.optString("modifier")?.takeIf { it.isNotEmpty() },
                            name = s.optString("name").takeIf { it.isNotEmpty() } ?: "",
                            distance = s.optDouble("distance"),
                            startMeters = startMeters,
                        )
                    )
                    startMeters += s.optDouble("distance")
                }
            }
        }

        val cumulative = DoubleArray(points.size)
        var total = 0.0
        for (i in 1 until points.size) {
            total += metersBetween(points[i - 1], points[i])
            cumulative[i] = total
        }
        // Calle principal: el paso con nombre mas largo (sin contar la salida).
        val viaName = steps
            .filter { it.name.isNotEmpty() && it.type != "depart" }
            .maxByOrNull { it.distance }?.name ?: ""
        return Route(
            points = points,
            steps = steps,
            totalDistance = routeJson.optDouble("distance"),
            totalDuration = routeJson.optDouble("duration"),
            cumulative = cumulative,
            viaName = viaName,
        )
    }

    /** Metros entre dos puntos (aproximacion equirectangular, suficiente para RD). */
    fun metersBetween(a: LatLng, b: LatLng): Double {
        val latRad = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val x = (b.longitude - a.longitude) * 111_320.0 * cos(latRad)
        val y = (b.latitude - a.latitude) * 110_540.0
        return sqrt(x * x + y * y)
    }

    /** Rumbo de a hacia b, en grados 0-360 (0 = norte). */
    fun bearingBetween(a: LatLng, b: LatLng): Double {
        val latRad = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val x = (b.longitude - a.longitude) * cos(latRad)
        val y = (b.latitude - a.latitude)
        var deg = Math.toDegrees(kotlin.math.atan2(x, y))
        if (deg < 0) deg += 360.0
        return deg
    }

    /**
     * Proyecta el punto GPS sobre la polilinea de la ruta.
     * Busca solo alrededor del ultimo indice (ventana hacia adelante), asi
     * el carro no salta a una calle paralela ni vuelve atras.
     */
    fun snapToRoute(route: Route, pos: LatLng, fromIndex: Int): SnapResult {
        val pts = route.points
        val lo = maxOf(0, fromIndex - 5)
        val hi = minOf(pts.size - 2, fromIndex + 400)
        var bestDist = Double.MAX_VALUE
        var bestIndex = fromIndex.coerceIn(0, pts.size - 1)
        var bestPoint = pts[bestIndex]
        var bestT = 0.0
        val latRad = Math.toRadians(pos.latitude)
        val kx = 111_320.0 * cos(latRad)
        val ky = 110_540.0
        val px = pos.longitude * kx
        val py = pos.latitude * ky
        for (i in lo..hi) {
            val a = pts[i]
            val b = pts[i + 1]
            val ax = a.longitude * kx
            val ay = a.latitude * ky
            val bx = b.longitude * kx
            val by = b.latitude * ky
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            var t = if (len2 == 0.0) 0.0 else ((px - ax) * dx + (py - ay) * dy) / len2
            t = t.coerceIn(0.0, 1.0)
            val cx = ax + t * dx
            val cy = ay + t * dy
            val d = sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
            if (d < bestDist) {
                bestDist = d
                bestIndex = i
                bestT = t
                bestPoint = LatLng(cy / ky, cx / kx)
            }
        }
        val segLen = metersBetween(pts[bestIndex], pts[bestIndex + 1])
        val progress = route.cumulative[bestIndex] + bestT * segLen
        return SnapResult(
            index = bestIndex,
            snapped = bestPoint,
            distance = bestDist,
            progressMeters = progress,
            bearing = bearingBetween(pts[bestIndex], pts[bestIndex + 1]),
        )
    }

    /** Siguiente maniobra despues de haber recorrido [progressMeters]. */
    fun nextStep(route: Route, progressMeters: Double): Pair<RouteStep, Double>? {
        // El paso 0 es "depart" (salida): no es una maniobra que mostrar.
        for (step in route.steps) {
            if (step.type == "depart") continue
            val remaining = step.startMeters - progressMeters
            if (remaining > -10.0) return step to remaining
        }
        return null
    }

    /** Texto en espanol para la maniobra. */
    fun maneuverText(context: android.content.Context, step: RouteStep): String {
        val res = context.resources
        return when (step.type) {
            "turn" -> when (step.modifier) {
                "left" -> res.getString(R.string.turn_left)
                "right" -> res.getString(R.string.turn_right)
                "slight left" -> res.getString(R.string.slight_left)
                "slight right" -> res.getString(R.string.slight_right)
                "sharp left" -> res.getString(R.string.sharp_left)
                "sharp right" -> res.getString(R.string.sharp_right)
                "uturn" -> res.getString(R.string.uturn)
                else -> res.getString(R.string.continue_straight)
            }
            "roundabout", "rotary" -> res.getString(R.string.roundabout)
            "fork" -> when (step.modifier) {
                "left", "slight left", "sharp left" -> res.getString(R.string.fork_left)
                else -> res.getString(R.string.fork_right)
            }
            "merge" -> res.getString(R.string.merge)
            "end of road" -> res.getString(R.string.end_of_road)
            "depart" -> res.getString(R.string.depart)
            "arrive" -> res.getString(R.string.arrived)
            else -> res.getString(R.string.continue_straight)
        }
    }

    /** Rotacion de la flecha (grados): 0 = recto, -90 izquierda, 90 derecha. */
    fun maneuverArrowRotation(step: RouteStep): Float {
        return when (step.type) {
            "turn" -> when (step.modifier) {
                "left" -> -90f
                "right" -> 90f
                "slight left" -> -45f
                "slight right" -> 45f
                "sharp left" -> -135f
                "sharp right" -> 135f
                "uturn" -> 180f
                else -> 0f
            }
            "fork" -> when (step.modifier) {
                "left", "slight left", "sharp left" -> -45f
                else -> 45f
            }
            "roundabout", "rotary" -> 90f
            else -> 0f
        }
    }

    fun formatDistance(meters: Double, context: android.content.Context): String {
        return if (meters < 1000) {
            context.getString(R.string.in_meters, meters.toInt())
        } else {
            context.getString(R.string.in_km, meters / 1000.0)
        }
    }

    /** Verdadero si el tramo que viene es recto por lo menos [aheadMeters]. */
    fun isStraightAhead(route: Route, progressMeters: Double, aheadMeters: Double): Boolean {
        val next = nextStep(route, progressMeters) ?: return true
        return next.second > aheadMeters
    }
}
