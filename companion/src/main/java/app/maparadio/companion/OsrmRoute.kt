package app.maparadio.companion

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Ruta calculada EN EL TELEFONO con OSRM (v1.0.3-test).
 *
 * El telefono tiene internet y GPS; el radio no tiene internet
 * fiable y su GraphHopper offline tarda minutos en cargar. Asi que
 * el telefono pide la ruta a OSRM (misma API y mismos dos servidores
 * espejo que usa el radio en RouteEngine: router.project-osrm.org y
 * routing.openstreetmap.de, misma User-Agent) y se queda solo con la
 * PRIMERA ruta (la mas rapida). Luego la manda entera al radio con
 * PhoneRouteJson; el radio solo la dibuja y la guia.
 *
 * Antes de mandar, la polilinea se afina con Douglas-Peucker
 * (tolerancia 10 m, referencia: libs/geometry/simplification.hpp
 * de CoMaps — SimplifyDP — reimplementado aqui en Kotlin; CoMaps
 * en si NO se usa: su nucleo es C++ con formato .mwm propio y un
 * render que exige OpenGL ES 3.0, que el Mali-450 del radio no
 * tiene). Afinar no toca los pasos (sus distancias y startMeters
 * son las de OSRM) ni el primer/ultimo punto: solo quita puntos
 * intermedios que estan a menos de 10 m de la linea recta entre sus
 * vecinos, asi el JSON baja de ~140 KB a ~8 KB (medido SD ->
 * Santiago: 1.958 puntos, 143.615 bytes de OSRM, 307 puntos y 7.803
 * bytes de JSON) y por Bluetooth va mucho mas rapido. La guia del
 * radio (snap-to-road) trabaja igual sobre segmentos mas largos.
 *
 * Llamar fuera del hilo principal (hace red). Errores con motivo
 * concreto para mostrarlos en la UI, no un generico.
 */
object OsrmRoute {

    enum class FailKind { NO_INTERNET, HTTP, NO_ROUTE }

    sealed class Result {
        data class Ok(val route: RouteData) : Result()
        data class Fail(val kind: FailKind, val detail: String = "") : Result()
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val HOSTS = listOf(
        "https://router.project-osrm.org",
        "https://routing.openstreetmap.de/routed-car",
    )

    /** Primera (mas rapida) ruta OSRM de origen a destino, o el fallo. */
    fun fetchRoute(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double,
        name: String,
    ): Result {
        var lastKind = FailKind.NO_INTERNET
        var lastDetail = ""
        for (host in HOSTS) {
            val url = "$host/route/v1/driving/" +
                "$fromLng,$fromLat;$toLng,$toLat" +
                "?overview=full&geometries=geojson&steps=true&alternatives=false"
            val req = Request.Builder().url(url)
                .header("User-Agent", "MapaRadio/1.0-radio")
                .build()
            try {
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        lastKind = FailKind.HTTP
                        lastDetail = "HTTP ${resp.code}"
                        return@use
                    }
                    val body = resp.body?.string()
                    if (body.isNullOrBlank()) {
                        lastKind = FailKind.NO_INTERNET
                        lastDetail = "respuesta vacia"
                        return@use
                    }
                    val route = parseOsrm(JSONObject(body), name)
                    if (route != null) return Result.Ok(route)
                    lastKind = FailKind.NO_ROUTE
                    lastDetail = "OSRM sin ruta"
                }
            } catch (e: IOException) {
                lastKind = FailKind.NO_INTERNET
                lastDetail = e.message ?: e.javaClass.simpleName
            } catch (e: Exception) {
                lastKind = FailKind.NO_INTERNET
                lastDetail = e.message ?: e.javaClass.simpleName
            }
            // Si el primer servidor contesto pero sin ruta, no vale la
            // pena probar el espejo: la respuesta seria la misma.
            if (lastKind == FailKind.NO_ROUTE) break
        }
        return Result.Fail(lastKind, lastDetail)
    }

    /**
     * Primera ruta del JSON de OSRM -> RouteData, con la polilinea
     * afinada. Espejo de RouteEngine.parseOneRoute del radio: mismas
     * coordenadas [lng, lat], mismos pasos (tipo/modificador/nombre/
     * distancia, startMeters acumulado) y mismos totales.
     */
    fun parseOsrm(json: JSONObject, name: String): RouteData? {
        if (json.optString("code") != "Ok") return null
        val routesJson = json.optJSONArray("routes") ?: return null
        if (routesJson.length() == 0) return null
        val routeJson = routesJson.optJSONObject(0) ?: return null
        val coords = routeJson.optJSONObject("geometry")
            ?.optJSONArray("coordinates") ?: return null
        val rawPoints = ArrayList<Pair<Double, Double>>(coords.length())
        for (i in 0 until coords.length()) {
            val c = coords.optJSONArray(i) ?: continue
            rawPoints.add(c.optDouble(1) to c.optDouble(0))
        }
        if (rawPoints.size < 2) return null
        val steps = ArrayList<RouteStepData>()
        val legs = routeJson.optJSONArray("legs")
        var startMeters = 0.0
        if (legs != null) {
            for (li in 0 until legs.length()) {
                val legSteps = legs.optJSONObject(li)?.optJSONArray("steps") ?: continue
                for (si in 0 until legSteps.length()) {
                    val s = legSteps.optJSONObject(si) ?: continue
                    val man = s.optJSONObject("maneuver")
                    steps.add(
                        RouteStepData(
                            type = man?.optString("type") ?: "continue",
                            modifier = man?.optString("modifier")
                                ?.takeIf { it.isNotEmpty() },
                            name = s.optString("name")
                                .takeIf { it.isNotEmpty() } ?: "",
                            distance = s.optDouble("distance"),
                            startMeters = startMeters,
                        )
                    )
                    startMeters += s.optDouble("distance")
                }
            }
        }
        return RouteData(
            name = name,
            distance = routeJson.optDouble("distance"),
            duration = routeJson.optDouble("duration"),
            points = simplify(rawPoints, SIMPLIFY_TOLERANCE_M),
            steps = steps,
        )
    }

    /** Tolerancia del afinado: 10 m (la guia del radio tolera mas). */
    const val SIMPLIFY_TOLERANCE_M = 10.0

    /**
     * Douglas-Peucker iterativo (sin recursion: una ruta larga no
     * debe desbordar la pila). Devuelve los puntos que quedan, en
     * orden, siempre con el primero y el ultimo. Distancias en
     * metros con la misma aproximacion equirectangular que usa el
     * radio (RouteEngine.metersBetween).
     */
    fun simplify(
        points: List<Pair<Double, Double>>,
        toleranceMeters: Double = SIMPLIFY_TOLERANCE_M,
    ): List<Pair<Double, Double>> {
        if (points.size <= 2) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(0 to points.size - 1)
        while (stack.isNotEmpty()) {
            val (first, last) = stack.removeLast()
            if (last - first < 2) continue
            var maxDist = 0.0
            var maxIndex = -1
            for (i in first + 1 until last) {
                val d = pointToSegmentMeters(points[i], points[first], points[last])
                if (d > maxDist) {
                    maxDist = d
                    maxIndex = i
                }
            }
            if (maxIndex >= 0 && maxDist > toleranceMeters) {
                keep[maxIndex] = true
                stack.add(first to maxIndex)
                stack.add(maxIndex to last)
            }
        }
        val out = ArrayList<Pair<Double, Double>>(points.size / 2 + 2)
        for (i in points.indices) if (keep[i]) out.add(points[i])
        return out
    }

    /** Distancia (m) de un punto al segmento a-b (equirectangular). */
    fun pointToSegmentMeters(
        p: Pair<Double, Double>,
        a: Pair<Double, Double>,
        b: Pair<Double, Double>,
    ): Double {
        val latRad = Math.toRadians((a.first + b.first) / 2.0)
        val kx = 111_320.0 * cos(latRad)
        val ky = 110_540.0
        val ax = a.second * kx
        val ay = a.first * ky
        val bx = b.second * kx
        val by = b.first * ky
        val px = p.second * kx
        val py = p.first * ky
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        var t = if (len2 == 0.0) 0.0 else ((px - ax) * dx + (py - ay) * dy) / len2
        t = t.coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }
}
