package app.maparadio.android

import com.mapbox.mapboxsdk.geometry.LatLng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pegado a la calle reforzado (v1.3): filtro de Kalman + matcher
 * probabilistico estilo HMM + dead reckoning.
 *
 * NO es una red neuronal ni lleva ninguna libreria de ML: es matematica
 * determinista, que es lo que usan Waze y Google para el snap-to-road y
 * corre bien en hardware debil. El matcher arregla la posicion SOBRE la
 * calle; la calidad cruda de la senal GPS (que se ve en Ajustes) la pone
 * el telefono y el cielo, no la app.
 *
 * Como funciona, en orden por cada fix:
 *  1. Kalman: suaviza el fix crudo (posicion + velocidad) segun su
 *     precision declarada; descarta saltos fisicamente imposibles.
 *  2. HMM: cada segmento de calle cercano es un candidato y se puntua
 *     por distancia + alineacion de rumbo + continuidad con el segmento
 *     anterior; gana el mejor, no simplemente el mas cercano.
 *  3. Sin senal (tunel): dead reckoning — se avanza por la calle con la
 *     ultima velocidad y rumbo hasta que vuelve el GPS.
 */
object MapMatcher {

    // ------------------------------------------------------------ Kalman

    /** Filtro de Kalman 1D (posicion + velocidad) para un eje. */
    private class AxisKF {
        private var p = 0.0 // posicion
        private var v = 0.0 // velocidad
        private var p00 = 1000.0
        private var p01 = 0.0
        private var p11 = 100.0
        private var initialized = false

        fun reset() {
            initialized = false
        }

        /**
         * Devuelve la posicion filtrada. [q] es el ruido de proceso
         * (aceleracion, m^2/s^4); [r] es la varianza de la medida.
         */
        fun update(measured: Double, r: Double, dt: Double, q: Double): Double {
            if (!initialized) {
                p = measured
                v = 0.0
                p00 = r.coerceAtLeast(9.0)
                p11 = 25.0
                p01 = 0.0
                initialized = true
                return p
            }
            val dt2 = dt * dt
            val dt3 = dt2 * dt
            val dt4 = dt2 * dt2
            // Prediccion (movimiento rectilineo uniforme).
            val pPred = p + v * dt
            val vPred = v
            val q00 = q * dt4 / 4.0
            val q01 = q * dt3 / 2.0
            val q11 = q * dt2
            val pp00 = p00 + 2 * dt * p01 + dt2 * p11 + q00
            val pp01 = p01 + dt * p11 + q01
            val pp11 = p11 + q11
            // Correccion.
            val s = pp00 + r
            val k0 = pp00 / s
            val k1 = pp01 / s
            val residual = measured - pPred
            p = pPred + k0 * residual
            v = vPred + k1 * residual
            p00 = (1 - k0) * pp00
            p01 = (1 - k0) * pp01
            p11 = -k1 * pp00 + pp11
            // La velocidad no puede ser absurda para un carro.
            val maxV = 60.0 // ~216 km/h
            if (abs(v) > maxV) v = if (v > 0) maxV else -maxV
            return p
        }

        fun velocity(): Double = v
    }

    /** Kalman 2D (un filtro por eje) en metros, plano equirectangular. */
    class Kalman2D {
        private val kx = AxisKF()
        private val ky = AxisKF()
        private var lastTimeMs = 0L
        private var refLat = 18.4861

        fun reset() {
            kx.reset()
            ky.reset()
            lastTimeMs = 0L
        }

        fun update(lat: Double, lon: Double, accuracyM: Float, timeMs: Long): LatLng {
            refLat = lat
            val mx = lon * 111_320.0 * cos(Math.toRadians(lat))
            val my = lat * 110_540.0
            var dt = if (lastTimeMs == 0L) 1.0 else (timeMs - lastTimeMs) / 1000.0
            lastTimeMs = timeMs
            if (dt <= 0) dt = 0.1
            if (dt > 10) dt = 10.0
            val r = (accuracyM * accuracyM).toDouble().coerceAtLeast(9.0)
            val fx = kx.update(mx, r, dt, 3.0)
            val fy = ky.update(my, r, dt, 3.0)
            return LatLng(fy / 110_540.0, fx / (111_320.0 * cos(Math.toRadians(refLat))))
        }

        fun speedMps(): Double {
            val vx = kx.velocity()
            val vy = ky.velocity()
            return sqrt(vx * vx + vy * vy)
        }
    }

    // ------------------------------------------------------------ HMM

    data class MatchResult(
        val snap: SnapResult,
        /** 0..1: confianza del segmento elegido. */
        val score: Double,
    )

    /**
     * Matcher probabilistico sobre la polilinea de la ruta.
     * Cada segmento en la ventana es un candidato y se puntua:
     *  - distancia (55%): cerca del fix, con sigma segun la precision
     *  - rumbo (30%): el segmento va en la misma direccion que el carro
     *  - continuidad (15%): no saltar atras ni a una calle paralela
     * Devuelve el mejor candidato; si ni el mejor convence (score < 0.15
     * o distancia > [offRouteMeters]), el llamador lo cuenta como desvio.
     */
    fun matchToRoute(
        route: Route,
        pos: LatLng,
        gpsBearingDeg: Double,
        speedMps: Double,
        fromIndex: Int,
        prevIndex: Int,
        accuracyM: Float,
        offRouteMeters: Double = 60.0,
    ): MatchResult {
        val pts = route.points
        val lo = maxOf(0, fromIndex - 5)
        val hi = minOf(pts.size - 2, fromIndex + 400)
        val sigma = maxOf(accuracyM.toDouble(), 12.0)
        val latRad = Math.toRadians(pos.latitude)
        val kx = 111_320.0 * cos(latRad)
        val ky = 110_540.0
        val px = pos.longitude * kx
        val py = pos.latitude * ky
        val hasBearing = speedMps > 2.0
        var bestScore = -1.0
        var bestSnap: SnapResult? = null
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
            // (a) Distancia: gaussiana con sigma = precision del fix.
            val distScore = exp(-(d / sigma) * (d / sigma))
            // (b) Rumbo: el segmento debe apuntar como el carro.
            val segBearing = RouteEngine.bearingBetween(a, b)
            val headingScore = if (hasBearing) {
                var diff = abs(segBearing - gpsBearingDeg) % 360.0
                if (diff > 180) diff = 360 - diff
                (cos(Math.toRadians(diff)) + 1.0) / 2.0
            } else {
                0.5
            }
            // (c) Continuidad: penalizar volver atras o saltar de calle.
            val continuityScore = when {
                i < prevIndex - 2 -> exp(-(prevIndex - i) / 20.0)
                i > prevIndex + 200 -> 0.7
                else -> 1.0
            }
            val score = 0.55 * distScore + 0.30 * headingScore + 0.15 * continuityScore
            if (score > bestScore) {
                bestScore = score
                val segLen = RouteEngine.metersBetween(a, b)
                val progress = route.cumulative[i] + t * segLen
                bestSnap = SnapResult(
                    index = i,
                    snapped = LatLng(cy / ky, cx / kx),
                    distance = d,
                    progressMeters = progress,
                    bearing = segBearing,
                )
            }
        }
        val fallback = bestSnap ?: SnapResult(
            index = fromIndex.coerceIn(0, pts.size - 1),
            snapped = pts[fromIndex.coerceIn(0, pts.size - 1)],
            distance = Double.MAX_VALUE,
            progressMeters = route.cumulative[fromIndex.coerceIn(0, pts.size - 1)],
            bearing = 0.0,
        )
        return MatchResult(fallback, bestScore.coerceIn(0.0, 1.0))
    }

    // ------------------------------------------------------------ dead reckoning

    /** Punto de la ruta a [progressMeters] desde el inicio. */
    fun pointAlongRoute(route: Route, progressMeters: Double): LatLng {
        val target = progressMeters.coerceIn(0.0, route.totalDistance)
        val cum = route.cumulative
        var i = 0
        while (i < cum.size - 2 && cum[i + 1] < target) i++
        val segStart = cum[i]
        val segEnd = cum[i + 1]
        val t = if (segEnd > segStart) (target - segStart) / (segEnd - segStart) else 0.0
        val a = route.points[i]
        val b = route.points[i + 1]
        return LatLng(
            a.latitude + (b.latitude - a.latitude) * t,
            a.longitude + (b.longitude - a.longitude) * t,
        )
    }

    /** Rumbo de la ruta a [progressMeters] desde el inicio. */
    fun bearingAlongRoute(route: Route, progressMeters: Double): Double {
        val target = progressMeters.coerceIn(0.0, route.totalDistance)
        val cum = route.cumulative
        var i = 0
        while (i < cum.size - 2 && cum[i + 1] < target) i++
        return RouteEngine.bearingBetween(route.points[i], route.points[i + 1])
    }

    /** Avanzar [distanceM] en linea recta desde [from] con rumbo [bearingDeg]. */
    fun advanceFree(from: LatLng, bearingDeg: Double, distanceM: Double): LatLng {
        val br = Math.toRadians(bearingDeg)
        val dLat = distanceM * cos(br) / 110_540.0
        val dLon = distanceM * sin(br) / (111_320.0 * cos(Math.toRadians(from.latitude)))
        return LatLng(from.latitude + dLat, from.longitude + dLon)
    }
}
