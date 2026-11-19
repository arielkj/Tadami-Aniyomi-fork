package app.maparadio.android

import android.content.Context
import android.util.Log
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.ResponsePath
import com.graphhopper.config.CHProfile
import com.graphhopper.config.Profile
import com.graphhopper.json.Statement
import com.graphhopper.util.CustomModel
import com.graphhopper.util.Instruction
import com.graphhopper.util.Parameters
import com.mapbox.mapboxsdk.geometry.LatLng
import java.io.File

/**
 * Rutas 100% sin internet (GraphHopper 10.0) para la version final
 * del radio. El grafo de RD va empaquetado en el APK (assets/
 * graphhopper) y en el primer arranque se copia al almacenamiento
 * interno, porque GraphHopper lo lee con memory-mapping y no puede
 * leerlo comprimido desde assets.
 *
 * Si el grafo no esta listo (primer arranque, o versiones de prueba
 * que no lo traen), devuelve null y el llamador usa OSRM con
 * internet como antes. La guia, la previsualizacion y el pegado a
 * la calle no cambian: una ruta es una ruta, venga de donde venga.
 *
 * Nota: sin costos de giro (turn costs). GraphHopper puede rutear
 * un giro que en la calle esta prohibido; OSRM (con internet) si
 * los respeta. Mejora pendiente, no un bloqueo para la v1.0.
 */
object GraphHopperRouter {
    private const val TAG = "MapaRadioGH"
    /** Cambiar cuando se reconstruya el grafo: fuerza volver a copiarlo. */
    private const val GRAPH_VERSION = "gh10-dr-v1"
    private const val GRAPH_ASSET_DIR = "graphhopper"
    private const val PROFILE = "car"

    @Volatile
    private var hopper: GraphHopper? = null

    @Volatile
    var isReady = false
        private set

    /** Copia el grafo (si hace falta) y lo abre, en segundo plano. */
    fun initAsync(context: Context) {
        if (isReady || hopper != null) return
        Thread({
            try {
                val appContext = context.applicationContext
                val dir = File(appContext.filesDir, GRAPH_ASSET_DIR)
                if (!isGraphCopied(dir)) {
                    copyGraphFromAssets(appContext, dir)
                }
                val gh = GraphHopper()
                gh.setGraphHopperLocation(dir.absolutePath)
                gh.setEncodedValuesString("car_access, car_average_speed")
                gh.setProfiles(carProfile())
                gh.chPreparationHandler.setCHProfiles(CHProfile(PROFILE))
                if (!gh.load()) {
                    Log.w(TAG, "GraphHopper.load() devolvio false")
                    return@Thread
                }
                hopper = gh
                isReady = true
                Log.i(TAG, "GraphHopper listo (offline)")
            } catch (e: Exception) {
                // Sin grafo (versiones de prueba) o grafo danado: se
                // sigue con OSRM cuando hay internet. No se rompe nada.
                Log.w(TAG, "GraphHopper no disponible: ${e.message}")
            }
        }, "gh-init").start()
    }

    /** Perfil "car" de GraphHopper 10.0 (igual que car.json del jar). */
    private fun carProfile(): Profile {
        val model = CustomModel()
        model.setDistanceInfluence(90.0)
        model.addToPriority(Statement.If("!car_access", Statement.Op.MULTIPLY, "0"))
        model.addToSpeed(Statement.If("true", Statement.Op.LIMIT, "car_average_speed"))
        return Profile(PROFILE).setWeighting("custom").setCustomModel(model)
    }

    /** Rutas sin internet primero; si no hay grafo, OSRM con internet. */
    fun fetchRoutes(from: LatLng, to: LatLng): List<Route>? {
        fetchOfflineRoutes(from, to)?.let { if (it.isNotEmpty()) return it }
        return RouteEngine.fetchRoutes(from, to)
    }

    fun fetchRoute(from: LatLng, to: LatLng): Route? {
        return fetchRoutes(from, to)?.firstOrNull()
    }

    private fun fetchOfflineRoutes(from: LatLng, to: LatLng): List<Route>? {
        val gh = hopper ?: return null
        if (!isReady) return null
        return try {
            // 1) Alternativas (hasta 3) sin CH: es el algoritmo que
            // mas alternativas da. Si no sale ninguna, 2) ruta unica
            // con CH (la mas rapida).
            val altReq = GHRequest(from.latitude, from.longitude, to.latitude, to.longitude)
                .setProfile(PROFILE)
                .setAlgorithm(Parameters.Algorithms.ALT_ROUTE)
            altReq.putHint(Parameters.Algorithms.AltRoute.MAX_PATHS, 3)
            altReq.putHint(Parameters.CH.DISABLE, true)
            val altResp = gh.route(altReq)
            val paths = if (!altResp.hasErrors() && altResp.all.isNotEmpty()) {
                altResp.all
            } else {
                val req = GHRequest(from.latitude, from.longitude, to.latitude, to.longitude)
                    .setProfile(PROFILE)
                val resp = gh.route(req)
                if (resp.hasErrors()) return null
                resp.all
            }
            val out = ArrayList<Route>()
            for (p in paths) {
                if (p.errors.isNotEmpty()) continue
                toRoute(p)?.let { out.add(it) }
            }
            if (out.isEmpty()) null else out
        } catch (_: Exception) {
            null
        }
    }

    /** Convierte una ruta de GraphHopper a la Route que usa la app. */
    private fun toRoute(path: ResponsePath): Route? {
        val points = ArrayList<LatLng>(path.points.size)
        for (p in path.points) {
            points.add(LatLng(p.lat, p.lon))
        }
        if (points.size < 2) return null
        val steps = ArrayList<RouteStep>()
        var startMeters = 0.0
        val instructions = path.instructions
        for (i in 0 until instructions.size) {
            val ins = instructions[i]
            val (type, modifier) = when (ins.sign) {
                Instruction.TURN_LEFT -> "turn" to "left"
                Instruction.TURN_RIGHT -> "turn" to "right"
                Instruction.TURN_SLIGHT_LEFT -> "turn" to "slight left"
                Instruction.TURN_SLIGHT_RIGHT -> "turn" to "slight right"
                Instruction.TURN_SHARP_LEFT -> "turn" to "sharp left"
                Instruction.TURN_SHARP_RIGHT -> "turn" to "sharp right"
                Instruction.U_TURN_LEFT, Instruction.U_TURN_RIGHT -> "turn" to "uturn"
                Instruction.USE_ROUNDABOUT -> "roundabout" to null
                Instruction.FINISH -> "arrive" to null
                Instruction.REACHED_VIA -> "continue" to null
                else -> "continue" to null
            }
            steps.add(
                RouteStep(
                    type = type,
                    modifier = modifier,
                    name = ins.name ?: "",
                    distance = ins.distance,
                    startMeters = startMeters,
                )
            )
            startMeters += ins.distance
        }
        val cumulative = DoubleArray(points.size)
        var total = 0.0
        for (i in 1 until points.size) {
            total += RouteEngine.metersBetween(points[i - 1], points[i])
            cumulative[i] = total
        }
        val viaName = steps
            .filter { it.name.isNotEmpty() && it.type != "depart" }
            .maxByOrNull { it.distance }?.name ?: ""
        return Route(
            points = points,
            steps = steps,
            totalDistance = path.distance,
            totalDuration = path.time / 1000.0,
            cumulative = cumulative,
            viaName = viaName,
        )
    }

    /** El grafo ya esta copiado y marcado con la version actual. */
    private fun isGraphCopied(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val marker = File(dir, "graph-version.txt")
        // "properties" es un archivo que todo grafo de GraphHopper
        // tiene: si falta, la copia esta incompleta.
        return marker.exists() &&
            marker.readText().trim() == GRAPH_VERSION &&
            File(dir, "properties").exists()
    }

    private fun copyGraphFromAssets(context: Context, dir: File) {
        dir.deleteRecursively()
        dir.mkdirs()
        val copied = copyAssetDir(context, GRAPH_ASSET_DIR, dir)
        if (copied == 0) {
            // Versiones de prueba sin grafo empaquetado: no marcar
            // nada, para no fingir que hay grafo.
            dir.deleteRecursively()
            throw IllegalStateException("sin grafo en assets")
        }
        File(dir, "graph-version.txt").writeText(GRAPH_VERSION)
        Log.i(TAG, "Grafo copiado a ${dir.absolutePath} ($copied archivos)")
    }

    /** Copia un directorio de assets (recursivo). Devuelve archivos. */
    private fun copyAssetDir(context: Context, assetPath: String, outDir: File): Int {
        val assets = context.assets
        val entries = assets.list(assetPath) ?: return 0
        var count = 0
        for (name in entries) {
            val childAsset = "$assetPath/$name"
            val childOut = File(outDir, name)
            val sub = assets.list(childAsset)
            if (sub != null && sub.isNotEmpty()) {
                childOut.mkdirs()
                count += copyAssetDir(context, childAsset, childOut)
            } else {
                assets.open(childAsset).use { input ->
                    childOut.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                count++
            }
        }
        return count
    }
}
