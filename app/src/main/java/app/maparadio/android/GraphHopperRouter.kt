package app.maparadio.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.GraphHopperConfig
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
 * Rutas 100% sin internet (GraphHopper 9.1) para la version final
 * del radio. El grafo de RD va empaquetado en el APK (assets/
 * graphhopper) y en el primer arranque se copia al almacenamiento
 * interno, porque GraphHopper lo lee y no puede leerlo comprimido
 * desde assets.
 *
 * Si el grafo no esta listo (primer arranque, o versiones de prueba
 * que no lo traen), devuelve null y el llamador usa OSRM con
 * internet como antes. La guia, la previsualizacion y el pegado a
 * la calle no cambian: una ruta es una ruta, venga de donde venga.
 *
 * Nota: sin costos de giro (turn costs). GraphHopper puede rutear
 * un giro que en la calle esta prohibido; OSRM (con internet) si
 * los respeta. Mejora pendiente, no un bloqueo para la v1.0.
 *
 * IMPORTANTE (v1.0.5): GraphHopper 10.0 NO sirve en este radio,
 * por DOS metodos que le faltan a su framework ALPS (dice API 27
 * pero su core-oj.jar esta recortado):
 *  1. RAMDataAccess usa VarHandle (MethodHandles
 *     .byteArrayViewVarHandle) y el verificador DEX rechaza ese
 *     bytecode en API 27 (VerifyError, cierre de la v1.0.1, registro
 *     del usuario 19 nov 10:35). La v1.0.2 lo esquivo con DAType
 *     MMAP (RAMDataAccess es la UNICA clase de GH 10.0 que
 *     referencia VarHandle).
 *  2. MMapDataAccess usa ByteBuffer.get(int, byte[], int, int)
 *     (bulk absoluto) y ese metodo NO EXISTE en el framework del
 *     radio: el registro del usuario (19 nov ~14:59, eventos de ruta
 *     13:42-13:47) dice "No virtual method get(I[BII)
 *     Ljava/nio/ByteBuffer" y "grafo offline no cargó" — con MMAP
 *     el grafo nunca llego a cargar y la app caia al OSRM en linea,
 *     que ese dia tampoco tenia internet real (DNS sin respuesta).
 * Arreglo: bajar a GraphHopper 9.1 y abrir el grafo con DAType
 * RAM_STORE. Verificado en los jars (19 nov): en 9.1 NINGUNA clase
 * referencia VarHandle; RAMDataAccess es Java puro sobre byte[]
 * (sin Unsafe, sin VarHandle, sin ByteBuffer en todo el jar) y el
 * unico que usa el bulk ByteBuffer.get es MMapDataAccess, que con
 * RAM_STORE nunca se carga. La API que usa la app (GHRequest,
 * ResponsePath, Instruction, Parameters, CustomModel, CHProfile,
 * Profile, load()/init()/route()) es identica en 9.1. El grafo se
 * reconstruye en CI con la misma version 9.1 (:tools) y el id del
 * grafo cambia a gh91-dr-v1 para que el radio vuelva a copiarlo en
 * el primer arranque de la v1.0.5 (el formato del grafo depende de
 * la version: el grafo gh10 NO lo abre GH 9.1). RAM_STORE mete el
 * grafo de 82 MB en el heap (4 GB de RAM en el radio, cabe), en
 * vez de paginarlo como MMAP.
 */
/** Estado de la carga del grafo offline (v1.0.3): la UI lo usa para
    saber si vale la pena esperar a que este listo en vez de fallar. */
enum class GhStatus {
    NOT_STARTED,
    LOADING,
    READY,
    FAILED,
}

object GraphHopperRouter {
    private const val TAG = "MapaRadioGH"
    /** Cambiar cuando se reconstruya el grafo: fuerza volver a copiarlo. */
    private const val GRAPH_VERSION = "gh91-dr-v1"
    private const val GRAPH_ASSET_DIR = "graphhopper"
    private const val PROFILE = "car"

    @Volatile
    private var hopper: GraphHopper? = null

    @Volatile
    var isReady = false
        private set

    /** Estado de la carga (v1.0.3) y motivo si fallo. */
    @Volatile
    var loadState = GhStatus.NOT_STARTED
        private set

    @Volatile
    var loadError: String? = null
        private set

    /** Copia el grafo (si hace falta) y lo abre, en segundo plano. */
    fun initAsync(context: Context) {
        if (isReady || hopper != null) return
        loadState = GhStatus.LOADING
        loadError = null
        Thread({
            try {
                val appContext = context.applicationContext
                val dir = File(appContext.filesDir, GRAPH_ASSET_DIR)
                if (!isGraphCopied(dir)) {
                    copyGraphFromAssets(appContext, dir)
                }
                val gh = GraphHopper()
                // RAM_STORE en vez de MMAP (v1.0.5): en GH 9.1 el
                // RAMDataAccess es Java puro sobre byte[] y carga en
                // API 27; MMAP quedo descartado porque su
                // MMapDataAccess usa ByteBuffer.get(int, byte[], int,
                // int), metodo que NO EXISTE en el framework de este
                // radio ALPS (registro 19 nov: "No virtual method
                // get(I[BII)"). init() ANTES que los setters, como
                // recomienda el javadoc de GraphHopper; graph.location
                // va en el config porque init() lo sobrescribe, y
                // import.osm.ignored_highways es obligatorio en
                // init() aunque solo se cargue el grafo.
                val config = GraphHopperConfig()
                config.putObject("graph.location", dir.absolutePath)
                config.putObject("graph.dataaccess", "RAM_STORE")
                config.putObject("import.osm.ignored_highways", "")
                gh.init(config)
                gh.setEncodedValuesString("car_access, car_average_speed")
                gh.setProfiles(carProfile())
                gh.chPreparationHandler.setCHProfiles(CHProfile(PROFILE))
                try {
                    if (!gh.load()) {
                        loadState = GhStatus.FAILED
                        loadError = "load() devolvió false"
                        Log.w(TAG, "GraphHopper.load() devolvio false")
                        return@Thread
                    }
                } catch (t: Throwable) {
                    // Un fallo del router NUNCA debe cerrar la app:
                    // sin rutas offline esta sesion, OSRM con internet
                    // de respaldo. VerifyError es un Error, no una
                    // Exception: por eso se atrapa Throwable.
                    loadState = GhStatus.FAILED
                    loadError = t.message ?: t.javaClass.simpleName
                    Log.e(TAG, "GraphHopper.load() fallo", t)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            appContext,
                            "Rutas sin conexión no disponibles",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    return@Thread
                }
                hopper = gh
                isReady = true
                loadState = GhStatus.READY
                Log.i(TAG, "GraphHopper listo (offline, RAM_STORE)")
            } catch (t: Throwable) {
                // Sin grafo (versiones de prueba) o grafo danado: se
                // sigue con OSRM cuando hay internet. No se rompe nada.
                loadState = GhStatus.FAILED
                loadError = t.message ?: t.javaClass.simpleName
                Log.w(TAG, "GraphHopper no disponible: ${t.message}")
            }
        }, "gh-init").start()
    }

    /** Perfil "car" de GraphHopper 9.1 (igual que car.json del jar). */
    private fun carProfile(): Profile {
        val model = CustomModel()
        model.setDistanceInfluence(90.0)
        model.addToPriority(Statement.If("!car_access", Statement.Op.MULTIPLY, "0"))
        model.addToSpeed(Statement.If("true", Statement.Op.LIMIT, "car_average_speed"))
        return Profile(PROFILE).setWeighting("custom").setCustomModel(model)
    }

    /** Rutas sin internet primero; si no hay grafo, OSRM con internet. */
    fun fetchRoutes(from: LatLng, to: LatLng): List<Route>? {
        return fetchRoutesResult(from, to).routes
    }

    fun fetchRoute(from: LatLng, to: LatLng): Route? {
        return fetchRoutes(from, to)?.firstOrNull()
    }

    /**
     * Lo mismo que fetchRoutes, pero con el motivo del fallo (v1.0.3):
     * si el grafo offline no pudo, el motivo que se muestra es el del
     * intento en vivo (OSRM) y el estado del grafo va en el detalle
     * para el registro de errores.
     */
    fun fetchRoutesResult(from: LatLng, to: LatLng): RouteResult {
        val offline = fetchOfflineResult(from, to)
        if (offline.routes != null) return offline
        val osrm = RouteEngine.fetchRoutesResult(from, to)
        if (osrm.routes != null) return osrm
        val ghDetail = when (loadState) {
            GhStatus.FAILED -> "grafo offline no cargó (${loadError ?: "?"})"
            GhStatus.LOADING -> "grafo offline aún cargando"
            else -> "grafo offline no listo"
        }
        val detail = if (osrm.detail.isNotEmpty()) "${osrm.detail}; $ghDetail" else ghDetail
        return RouteResult(null, osrm.failure, detail)
    }

    /** Solo el grafo offline, con motivo (v1.0.3). */
    fun fetchOfflineResult(from: LatLng, to: LatLng): RouteResult {
        val gh = hopper ?: return RouteResult(
            null,
            if (loadState == GhStatus.FAILED) RouteFailure.GH_ERROR
            else RouteFailure.GH_NOT_READY,
            loadError ?: "",
        )
        if (!isReady) return RouteResult(null, RouteFailure.GH_NOT_READY, "")
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
                if (resp.hasErrors()) {
                    return RouteResult(
                        null, RouteFailure.GH_ERROR, "ruta CH con errores",
                    )
                }
                resp.all
            }
            val out = ArrayList<Route>()
            for (p in paths) {
                if (p.errors.isNotEmpty()) continue
                toRoute(p)?.let { out.add(it) }
            }
            if (out.isEmpty()) {
                RouteResult(null, RouteFailure.GH_ERROR, "sin rutas")
            } else {
                RouteResult(out, RouteFailure.NONE)
            }
        } catch (t: Throwable) {
            // Un fallo del router (incluido un Error como VerifyError)
            // nunca cierra la app: se devuelve el motivo y el llamador
            // usa OSRM con internet.
            RouteResult(
                null, RouteFailure.GH_ERROR,
                t.message ?: t.javaClass.simpleName,
            )
        }
    }

    /** Convierte una ruta de GraphHopper a la Route que usa la app. */
    private fun toRoute(path: ResponsePath): Route? {
        val points = ArrayList<LatLng>(path.points.size())
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
