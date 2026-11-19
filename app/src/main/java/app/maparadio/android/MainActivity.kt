package app.maparadio.android

import android.Manifest
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.WellKnownTileServer
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.CircleLayer
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import android.media.session.MediaSessionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Mapa Radio v1.2 (prueba en el telefono).
 *
 * Diseno del mockup v2: mapa oscuro estilo Tesla, ruta azul, carro blanco
 * centrado, velocidad arriba a la izquierda con la busqueda ENCIMA de ella,
 * panel de guia a un lado que crece cerca del giro, controles a la derecha
 * (ajustes, brujula, centrar, dia/noche, zoom +/-), barra inferior con
 * lugares guardados.
 *
 * v1.2 anade: Ajustes (modo oscuro/claro/automatico + descarga y
 * actualizacion del mapa offline de RD), boton de centrar, iconos mas
 * pequenos y favoritos minimalistas, mini-reproductor de musica que lee
 * TODOS los reproductores con sesion activa, ubicacion precisa con
 * FusedLocationProvider y buscador solo de Republica Dominicana.
 *
 * v1.4 anade: pantalla partida estilo Android Auto (el mapa se
 * achica a una capsula y el reproductor ocupa todo el lado derecho),
 * controles en una capsula, y se quita el texto de estado del mapa.
 * Optimizacion ante todo: animacion barata, sin blur ni sombras.
 *
 * v1.6 anade: lugares guardados por categorias (Locales, Restaurantes,
 * Favoritos) con una capsula de tres chips debajo del buscador, y el
 * enlace con el telefono: el radio escucha en la red local (hotspot)
 * y la app "Mapa Radio Movil" le manda destinos (pin + ruta solos).
 *
 * v1.7 anade: previsualizacion de rutas — al elegir un destino no
 * arranca la guia de inmediato: OSRM devuelve hasta 3 rutas, se
 * dibujan todas (la mas rapida en azul, las demas en gris) y el
 * usuario toca una para ir (o su linea en el mapa). Sin internet se
 * avisa y el pin queda para guardar el lugar. Tambien se puede
 * guardar en favoritos cualquier lugar buscado: estrella en cada
 * resultado y boton "Guardar en favoritos" en el panel de vista previa.
 *
 * Pegado a la calle (snap-to-road): con ruta activa el carro se dibuja
 * SOBRE la polilinea de la ruta (el GPS solo mide el avance); sin ruta se
 * usa el GPS suavizado. Estacionado (velocidad ~0) el carro se congela para
 * que el ruido del GPS no lo mueva.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private val SANTO_DOMINGO = LatLng(18.4861, -69.9312)
        private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
        private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
        private const val REQ_LOCATION = 1
        private const val OFF_ROUTE_METERS = 60.0
        private const val MANUAL_ZOOM_MS = 20_000L
        // Umbrales de precision de ubicacion (v1.2).
        private const val GOOD_ACCURACY_M = 25f
        private const val MAX_ACCURACY_M = 60f
        private const val CENTER_ACCURACY_M = 30f
        private const val GOOD_FIX_GRACE_MS = 10_000L
        private const val STALE_FIX_MS = 30_000L
        private const val LAST_KNOWN_MAX_AGE_MS = 120_000L
        private const val MAX_JUMP_M = 150.0
        private const val CENTER_TIMEOUT_MS = 8_000L
        // Solo Republica Dominicana (v1.3): la camara no puede salir de
        // este cuadro ni alejarse tanto que se vean otros paises.
        private val DR_BOUNDS = com.mapbox.mapboxsdk.geometry.LatLngBounds.Builder()
            .include(LatLng(20.0, -68.5)) // noreste
            .include(LatLng(17.4, -72.1)) // suroeste
            .build()
        private const val DR_MIN_ZOOM = 8.5
        // Pegado a la calle (v1.3): OSRM Match como mucho cada 5 s.
        private const val OSRM_MATCH_MIN_INTERVAL_MS = 5_000L
        // Pantalla partida (v1.4, v1.5): peso del reproductor en el split y
        // duracion de la animacion barata (un solo ValueAnimator).
        // v1.5: 0.39f -> la capsula de musica ocupa ~28% del ancho
        // (0.39/1.39) y el mapa ~72%, antes 0.55f (~35% / ~65%).
        private const val MUSIC_WEIGHT = 0.39f
        private const val SPLIT_ANIM_MS = 250L
    }

    private lateinit var mapView: MapView
    private var map: MapboxMap? = null
    private var style: Style? = null
    // Pantalla partida (v1.4): capsula del mapa + estado del split.
    private lateinit var mapCapsule: FrameLayout
    private var splitAnimator: ValueAnimator? = null
    private var musicSplitOn = false

    private lateinit var searchInput: EditText
    private lateinit var searchResults: ListView
    private lateinit var speedValue: TextView
    private lateinit var guidanceCard: View
    private lateinit var maneuverArrow: ImageView
    private lateinit var maneuverTitle: TextView
    private lateinit var maneuverStreet: TextView
    private lateinit var maneuverEta: TextView
    private lateinit var btnCancelRoute: TextView
    private lateinit var pointCard: View
    private lateinit var pointTitle: TextView
    private lateinit var pointSub: TextView
    private lateinit var btnDayNight: ImageButton

    // Ajustes (v1.2)
    private lateinit var settingsPanel: ScrollView
    private lateinit var modeDark: TextView
    private lateinit var modeLight: TextView
    private lateinit var modeAuto: TextView
    private lateinit var offlineStatus: TextView
    private lateinit var offlineProgress: ProgressBar
    private lateinit var btnDownloadMap: TextView
    private lateinit var btnUpdateMap: TextView
    private lateinit var btnDeleteMap: TextView
    private lateinit var musicPermStatus: TextView
    private lateinit var btnMusicPerm: TextView

    // Musica (v1.3): tarjeta lateral estilo Android Auto con la
    // portada de fondo, titulo, artista, progreso y controles.
    private lateinit var musicCard: FrameLayout
    private lateinit var musicArt: ImageView
    private lateinit var musicPlayerChip: TextView
    private lateinit var musicTitle: TextView
    private lateinit var musicArtist: TextView
    private lateinit var musicProgress: ProgressBar
    private lateinit var musicTimeCur: TextView
    private lateinit var musicTimeTotal: TextView
    private lateinit var musicPlayPause: ImageButton
    private lateinit var btnShowMusic: ImageButton
    private var musicHiddenByUser = false
    private var currentPlayer: PlayerInfo? = null
    private var selectedPlayerPkg: String? = null
    private var musicJob: Job? = null
    private var lastMusicArtKey: String? = null

    // Favoritos en tira bajo el buscador (v1.3), filtrada por
    // categoria en v1.6: la capsula de categorias (Locales,
    // Restaurantes, Favoritos) esta directamente debajo del buscador.
    private lateinit var favChips: HorizontalScrollView
    private lateinit var favChipsRow: LinearLayout
    private lateinit var catLocales: ImageView
    private lateinit var catRestaurantes: ImageView
    private lateinit var catFavoritos: ImageView
    private var selectedCategory: String? = null

    // Enlace con el telefono (v1.6): servidor local en la red WiFi.
    private lateinit var phoneIp: TextView
    private lateinit var phoneStatus: TextView
    private var phoneLastReceived: String? = null
    private val phoneLinkListener = object : PhoneLinkServer.Listener {
        override fun onDestination(name: String, lat: Double, lng: Double) {
            runOnUiThread { onPhoneDestination(name, lat, lng) }
        }

        override fun onStartFailed() {
            runOnUiThread {
                phoneLastReceived = null
                refreshPhoneStatus()
                Toast.makeText(
                    this@MainActivity, R.string.phone_failed, Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Diagnostico GPS en vivo (v1.3).
    private lateinit var gpsStatus: TextView
    private lateinit var gpsPerm: TextView

    // Registro de errores (v1.0.1): ultimo cierre + compartirlo.
    private lateinit var crashLogStatus: TextView
    private lateinit var btnShareCrashLog: TextView
    private var lastFixAccuracy = -1f
    private var lastFixTimeMs = 0L
    private var lastFixProvider = ""
    private var gpsJob: Job? = null

    // Matcher reforzado (v1.3): Kalman + HMM + dead reckoning.
    private val kalman = MapMatcher.Kalman2D()
    private var lastMatchedIndex = 0
    private var lastProgressMeters = 0.0
    private var lastFixElapsedMs = 0L
    private var deadReckonJob: Job? = null
    private var recentPoints = ArrayDeque<Pair<LatLng, Float>>()
    private var lastOsrmMatchAt = 0L
    private var lastOsrmMatched: LatLng? = null
    private var lastOsrmMatchedAt = 0L
    private var deadReckonMeters = 0.0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var searchJob: Job? = null

    private var locationManager: LocationManager? = null
    private var fusedClient: FusedLocationProviderClient? = null
    private var fusedCallback: LocationCallback? = null
    private var locationStarted = false
    private var lastLocation: Location? = null
    private var lastCarPos: LatLng? = null
    private var lastBearing = 0.0
    private var lastSpeedMps = 0.0
    private var hasGoodFix = false
    private var firstFixRequestAt = 0L
    private var centering = false
    private var centerTimeoutJob: Job? = null

    private var mode = AppPrefs.MODE_DARK
    private var followMode = true
    private var headingUp = true
    private var simplified = false
    private var manualZoomUntil = 0L
    private var lastZoomBand = -1
    private var guidanceExpanded = false
    private var offlineDownloading = false

    private var route: Route? = null
    private var routeIndex = 0
    private var destination: LatLng? = null
    private var destinationName: String? = null
    private var offRouteCount = 0

    private var routeSource: GeoJsonSource? = null
    private var carSource: GeoJsonSource? = null
    private var carLayer: SymbolLayer? = null
    private var destSource: GeoJsonSource? = null

    // Previsualizacion de rutas (v1.7): al elegir un destino no se
    // arranca la guia de inmediato; se muestran las rutas posibles
    // (la mas rapida en azul, las demas en gris) y el usuario toca
    // una para ir. altRouteSource dibuja las grises.
    private var altRouteSource: GeoJsonSource? = null
    private var previewRoutes: List<Route>? = null
    private var previewDest: LatLng? = null
    private var previewDestName: String? = null
    private var previewSelected = 0
    private var previewJob: Job? = null
    private var followBeforePreview = true
    private lateinit var routePreviewPanel: View
    private lateinit var previewTitle: TextView
    private lateinit var previewRows: LinearLayout
    private lateinit var previewSave: TextView
    private lateinit var previewCancel: TextView

    private var pendingPoint: LatLng? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onNewFix(location)
        }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { refreshMusic() }

    // ------------------------------------------------------------------ ciclo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Registro de errores (v1.0.1): instalar ANTES de inicializar
        // el mapa, para que un cierre al cargar el mapa tambien quede
        // guardado en filesDir/crash.log.
        CrashLog.install(this)
        // MapLibre 10.x exige el singleton inicializado ANTES de inflar
        // el MapView: su constructor llama a validateMapbox() y sin esto
        // la app se cierra al abrir (MapboxConfigurationException).
        Mapbox.getInstance(this, "maparadio-local", WellKnownTileServer.MapLibre)
        setContentView(R.layout.activity_main)

        // Rutas sin internet (version final del radio): copia el grafo
        // empaquetado y lo abre en segundo plano. Si no hay grafo (o
        // todavia no esta listo), las rutas usan OSRM con internet.
        GraphHopperRouter.initAsync(this)

        mapView = findViewById(R.id.mapView)
        mapCapsule = findViewById(R.id.mapCapsule)
        mapView.onCreate(savedInstanceState)
        searchInput = findViewById(R.id.searchInput)
        searchResults = findViewById(R.id.searchResults)
        speedValue = findViewById(R.id.speedValue)
        guidanceCard = findViewById(R.id.guidanceCard)
        maneuverArrow = findViewById(R.id.maneuverArrow)
        maneuverTitle = findViewById(R.id.maneuverTitle)
        maneuverStreet = findViewById(R.id.maneuverStreet)
        maneuverEta = findViewById(R.id.maneuverEta)
        btnCancelRoute = findViewById(R.id.btnCancelRoute)
        pointCard = findViewById(R.id.pointCard)
        pointTitle = findViewById(R.id.pointTitle)
        pointSub = findViewById(R.id.pointSub)
        routePreviewPanel = findViewById(R.id.routePreviewPanel)
        previewTitle = findViewById(R.id.previewTitle)
        previewRows = findViewById(R.id.previewRows)
        previewSave = findViewById(R.id.previewSave)
        previewCancel = findViewById(R.id.previewCancel)
        btnDayNight = findViewById(R.id.btnDayNight)

        settingsPanel = findViewById(R.id.settingsPanel)
        modeDark = findViewById(R.id.modeDark)
        modeLight = findViewById(R.id.modeLight)
        modeAuto = findViewById(R.id.modeAuto)
        offlineStatus = findViewById(R.id.offlineStatus)
        offlineProgress = findViewById(R.id.offlineProgress)
        btnDownloadMap = findViewById(R.id.btnDownloadMap)
        btnUpdateMap = findViewById(R.id.btnUpdateMap)
        btnDeleteMap = findViewById(R.id.btnDeleteMap)
        musicPermStatus = findViewById(R.id.musicPermStatus)
        btnMusicPerm = findViewById(R.id.btnMusicPerm)

        musicCard = findViewById(R.id.musicCard)
        musicArt = findViewById(R.id.musicArt)
        musicPlayerChip = findViewById(R.id.musicPlayerChip)
        musicTitle = findViewById(R.id.musicTitle)
        musicArtist = findViewById(R.id.musicArtist)
        musicProgress = findViewById(R.id.musicProgress)
        musicTimeCur = findViewById(R.id.musicTimeCur)
        musicTimeTotal = findViewById(R.id.musicTimeTotal)
        musicPlayPause = findViewById(R.id.musicPlayPause)
        btnShowMusic = findViewById(R.id.btnShowMusic)

        favChips = findViewById(R.id.favChips)
        favChipsRow = findViewById(R.id.favChipsRow)
        catLocales = findViewById(R.id.catLocales)
        catRestaurantes = findViewById(R.id.catRestaurantes)
        catFavoritos = findViewById(R.id.catFavoritos)
        catLocales.setOnClickListener { onCategoryChip(FavoriteCategory.LOCALES) }
        catRestaurantes.setOnClickListener { onCategoryChip(FavoriteCategory.RESTAURANTES) }
        catFavoritos.setOnClickListener { onCategoryChip(FavoriteCategory.FAVORITOS) }
        phoneIp = findViewById(R.id.phoneIp)
        phoneStatus = findViewById(R.id.phoneStatus)
        gpsStatus = findViewById(R.id.gpsStatus)
        gpsPerm = findViewById(R.id.gpsPerm)
        crashLogStatus = findViewById(R.id.crashLogStatus)
        btnShareCrashLog = findViewById(R.id.btnShareCrashLog)
        btnShareCrashLog.setOnClickListener { shareCrashLog() }

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { toggleSettings() }
        findViewById<ImageButton>(R.id.btnCloseSettings).setOnClickListener { hideSettings() }
        findViewById<ImageButton>(R.id.btnCompass).setOnClickListener { onCompass() }
        findViewById<ImageButton>(R.id.btnCenter).setOnClickListener { onCenter() }
        btnDayNight.setOnClickListener { onDayNight() }
        findViewById<ImageButton>(R.id.btnZoomIn).setOnClickListener { onZoomButton(1.0) }
        findViewById<ImageButton>(R.id.btnZoomOut).setOnClickListener { onZoomButton(-1.0) }
        btnCancelRoute.setOnClickListener { cancelRoute() }
        findViewById<TextView>(R.id.btnGo).setOnClickListener { onGoToPoint() }
        findViewById<TextView>(R.id.btnSavePoint).setOnClickListener { onSavePoint() }
        previewSave.setOnClickListener { onPreviewSave() }
        previewCancel.setOnClickListener { onPreviewCancel() }

        modeDark.setOnClickListener { setMode(AppPrefs.MODE_DARK) }
        modeLight.setOnClickListener { setMode(AppPrefs.MODE_LIGHT) }
        modeAuto.setOnClickListener { setMode(AppPrefs.MODE_AUTO) }
        btnDownloadMap.setOnClickListener { startOfflineDownload() }
        btnUpdateMap.setOnClickListener { startOfflineDownload() }
        btnDeleteMap.setOnClickListener { deleteOfflineMap() }
        btnMusicPerm.setOnClickListener { MusicController.openAccessSettings(this) }

        musicPlayerChip.setOnClickListener { showPlayerSwitcher() }
        musicPlayPause.setOnClickListener { currentPlayer?.let { MusicController.togglePlayPause(it) }; refreshMusic() }
        findViewById<ImageButton>(R.id.musicPrev).setOnClickListener { currentPlayer?.let { MusicController.skipPrevious(it) }; refreshMusic() }
        findViewById<ImageButton>(R.id.musicNext).setOnClickListener { currentPlayer?.let { MusicController.skipNext(it) }; refreshMusic() }
        findViewById<ImageButton>(R.id.btnCloseMusic).setOnClickListener {
            musicHiddenByUser = true
            refreshMusic()
        }
        btnShowMusic.setOnClickListener {
            musicHiddenByUser = false
            btnShowMusic.visibility = View.GONE
            refreshMusic()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onSearchText(s?.toString().orEmpty())
            }
        })
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                searchInput.clearFocus()
                true
            } else false
        }
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && searchInput.text.isEmpty()) showFavorites()
        }

        mapView.getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.uiSettings.apply {
                isAttributionEnabled = true
                isLogoEnabled = false
                isCompassEnabled = false
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = false
            }
            // Solo Republica Dominicana (v1.3): la camara no puede salir
            // del cuadro del pais ni alejarse tanto que se vean otros.
            mapLibreMap.setLatLngBoundsForCameraTarget(DR_BOUNDS)
            mapLibreMap.setMinZoomPreference(DR_MIN_ZOOM)
            mapLibreMap.addOnMapClickListener { latLng ->
                // Con la previsualizacion abierta, tocar una linea gris
                // arranca la guia con esa ruta; tocar fuera la cancela.
                if (previewRoutes != null && tryPreviewMapTap(latLng)) {
                    true
                } else {
                    hideOverlays()
                    true
                }
            }
            mapLibreMap.addOnMapLongClickListener { latLng -> onMapLongPress(latLng); true }
            mapLibreMap.addOnCameraMoveStartedListener { reason ->
                if (reason == MapboxMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    followMode = false
                }
            }
            loadStyle(styleUrlForMode())
            mapLibreMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    com.mapbox.mapboxsdk.camera.CameraPosition.Builder()
                        .target(SANTO_DOMINGO).zoom(15.0).tilt(45.0).bearing(0.0).build()
                ),
                1,
            )
        }

        // Modo guardado (oscuro por defecto). Si cambia respecto al
        // supuesto inicial, se recarga el estilo.
        scope.launch {
            val saved = AppPrefs.loadMode(this@MainActivity)
            if (saved != mode) {
                mode = saved
                applyModeToUi()
                map?.let { loadStyle(styleUrlForMode()) }
            } else {
                applyModeToUi()
            }
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        ensureLocationPermission()
        refreshFavoriteChips()
    }

    private fun loadStyle(url: String) {
        map?.setStyle(Style.Builder().fromUri(url)) { loaded ->
            style = loaded
            setupLayers(loaded)
            redrawRoute()
            // Si habia una previsualizacion abierta al cambiar de estilo,
            // las capas se recrearon: volver a dibujar sus lineas.
            if (previewRoutes != null) drawPreviewLines()
            updateCarMarker()
            setSimplified(simplified)
        }
    }

    private fun setupLayers(style: Style) {
        // Rutas alternativas (v1.7): las grises van DEBAJO de la azul.
        altRouteSource = GeoJsonSource("alt-src", FeatureCollection.fromFeatures(emptyList()))
        style.addSource(altRouteSource!!)
        style.addLayer(
            LineLayer("alt-casing", "alt-src").withProperties(
                PropertyFactory.lineColor("#424242"),
                PropertyFactory.lineWidth(10f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        style.addLayer(
            LineLayer("alt-line", "alt-src").withProperties(
                PropertyFactory.lineColor("#8A8A8A"),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        routeSource = GeoJsonSource("route-src", FeatureCollection.fromFeatures(emptyList()))
        style.addSource(routeSource!!)
        style.addLayer(
            LineLayer("route-casing", "route-src").withProperties(
                PropertyFactory.lineColor("#1565C0"),
                PropertyFactory.lineWidth(10f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        style.addLayer(
            LineLayer("route-line", "route-src").withProperties(
                PropertyFactory.lineColor("#2196F3"),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        destSource = GeoJsonSource("dest-src", FeatureCollection.fromFeatures(emptyList()))
        style.addSource(destSource!!)
        style.addLayer(
            CircleLayer("dest-layer", "dest-src").withProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor("#2196F3"),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
            )
        )
        carSource = GeoJsonSource("car-src", FeatureCollection.fromFeatures(emptyList()))
        style.addSource(carSource!!)
        style.addImage("car-icon", drawCarBitmap())
        carLayer = SymbolLayer("car-layer", "car-src").withProperties(
            PropertyFactory.iconImage("car-icon"),
            PropertyFactory.iconRotate(0f),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
        )
        style.addLayer(carLayer!!)
    }

    private fun drawCarBitmap(): Bitmap {
        // Tamano de marcador de navegacion (v1.0.3): ~48dp de ancho,
        // como Waze/Tesla. Antes era 110x180 px fijos: en el radio
        // (160 dpi, DPR 1.0) eso son 110x180 dp, casi un tercio de la
        // pantalla de 600 px — el carro se veia enorme (foto del
        // usuario, 19 nov). Se dibuja a 48dp x densidad para que sea
        // nitido en cualquier pantalla, con el mismo dibujo escalado
        // por el canvas. La rotacion y el pegado a la calle no cambian.
        val density = resources.displayMetrics.density
        val w = (48f * density).roundToInt().coerceAtLeast(1)
        val h = (78f * density).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.density = (DisplayMetrics.DENSITY_DEFAULT * density).toInt()
        val c = Canvas(bmp)
        c.scale(w / 110f, h / 180f)
        val body = Paint().apply { color = Color.WHITE; isAntiAlias = true }
        val glass = Paint().apply { color = Color.parseColor("#1A2530"); isAntiAlias = true }
        val light = Paint().apply { color = Color.parseColor("#FFE082"); isAntiAlias = true }
        val tail = Paint().apply { color = Color.parseColor("#E53935"); isAntiAlias = true }
        c.drawRoundRect(RectF(18f, 6f, 92f, 174f), 26f, 26f, body)
        c.drawRoundRect(RectF(26f, 34f, 84f, 66f), 10f, 10f, glass) // parabrisas
        c.drawRoundRect(RectF(26f, 112f, 84f, 138f), 10f, 10f, glass) // luneta trasera
        c.drawRoundRect(RectF(20f, 8f, 46f, 16f), 4f, 4f, light) // faro
        c.drawRoundRect(RectF(64f, 8f, 90f, 16f), 4f, 4f, light)
        c.drawRoundRect(RectF(20f, 164f, 46f, 172f), 4f, 4f, tail) // traseras
        c.drawRoundRect(RectF(64f, 164f, 90f, 172f), 4f, 4f, tail)
        return bmp
    }

    // ------------------------------------------------------------- modo

    private fun isNightNow(): Boolean {
        return when (mode) {
            AppPrefs.MODE_DARK -> true
            AppPrefs.MODE_LIGHT -> false
            else -> {
                val night = resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                night == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun styleUrlForMode(): String =
        if (isNightNow()) STYLE_DARK else STYLE_LIGHT

    private fun applyModeToUi() {
        val night = isNightNow()
        btnDayNight.setImageResource(if (night) R.drawable.ic_day else R.drawable.ic_night)
        modeDark.setBackgroundResource(
            if (mode == AppPrefs.MODE_DARK) R.drawable.bg_chip_selected else R.drawable.bg_chip
        )
        modeLight.setBackgroundResource(
            if (mode == AppPrefs.MODE_LIGHT) R.drawable.bg_chip_selected else R.drawable.bg_chip
        )
        modeAuto.setBackgroundResource(
            if (mode == AppPrefs.MODE_AUTO) R.drawable.bg_chip_selected else R.drawable.bg_chip
        )
    }

    private fun setMode(newMode: String) {
        if (newMode == mode) return
        mode = newMode
        applyModeToUi()
        scope.launch { AppPrefs.saveMode(this@MainActivity, newMode) }
        loadStyle(styleUrlForMode())
    }

    // ------------------------------------------------------------- ubicacion

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun fusedAvailable(): Boolean {
        return try {
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureLocationPermission() {
        if (hasLocationPermission()) {
            startLocation()
            return
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            Toast.makeText(this, R.string.location_perm_needed, Toast.LENGTH_LONG).show()
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            REQ_LOCATION,
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_LOCATION) return
        if (grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startLocation()
        } else if (!ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            // Negado para siempre: no quedarse en silencio; mandar a Ajustes.
            Toast.makeText(this, R.string.location_perm_settings, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null),
                )
            )
        } else {
            Toast.makeText(this, R.string.location_denied, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Fuente principal: FusedLocationProvider (GPS+WiFi+celda, fix mas
     * rapido y preciso). LocationManager queda solo de fallback si Play
     * Services no esta disponible.
     */
    private fun startLocation() {
        if (locationStarted) return
        if (!hasLocationPermission()) return
        firstFixRequestAt = SystemClock.elapsedRealtime()
        hasGoodFix = false
        if (fusedAvailable()) {
            val client = LocationServices.getFusedLocationProviderClient(this)
            fusedClient = client
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { onNewFix(it) }
                }
            }
            fusedCallback = callback
            try {
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                client.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        val age = System.currentTimeMillis() - loc.time
                        if (age < LAST_KNOWN_MAX_AGE_MS &&
                            loc.accuracy > 0f && loc.accuracy <= GOOD_ACCURACY_M
                        ) {
                            onNewFix(loc)
                        }
                    }
                }
                locationStarted = true
            } catch (_: SecurityException) {
                // Sin permiso: el mapa sigue usable centrado en Santo Domingo.
            }
        } else {
            startLocationManagerFallback()
        }
    }

    private fun startLocationManagerFallback() {
        val lm = locationManager ?: return
        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper()
            )
            lm.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 2000L, 0f, locationListener, Looper.getMainLooper()
            )
            val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (last != null) {
                val age = System.currentTimeMillis() - last.time
                if (age < LAST_KNOWN_MAX_AGE_MS &&
                    last.accuracy > 0f && last.accuracy <= GOOD_ACCURACY_M
                ) {
                    onNewFix(last)
                }
            }
            locationStarted = true
        } catch (_: SecurityException) {
            // Sin permiso: el mapa sigue usable centrado en Santo Domingo.
        } catch (_: IllegalArgumentException) {
            // Proveedor no disponible en este aparato.
        }
    }

    private fun onNewFix(location: Location) {
        // --- Filtro de calidad (v1.2): el carro NUNCA se mueve con un
        // fix malo, viejo o impreciso.
        val ageMs = System.currentTimeMillis() - location.time
        if (ageMs > STALE_FIX_MS) return // fix viejo: ignorar
        val acc = location.accuracy
        if (acc <= 0f || acc > MAX_ACCURACY_M) return // sin precision o peor de 60 m
        if (acc > GOOD_ACCURACY_M) {
            // Fix mediocre (25-60 m): solo se acepta si no teletransporta
            // el carro, y si todavia no hubo un fix bueno, solo despues
            // de 10 s de gracia esperando uno mejor.
            if (!hasGoodFix &&
                SystemClock.elapsedRealtime() - firstFixRequestAt < GOOD_FIX_GRACE_MS
            ) {
                return
            }
            val last = lastCarPos
            if (last != null) {
                val jump = RouteEngine.metersBetween(
                    last, LatLng(location.latitude, location.longitude)
                )
                if (jump > MAX_JUMP_M) return
            }
        } else {
            hasGoodFix = true
        }

        // Diagnostico en vivo (v1.3): se muestra en Ajustes.
        lastFixAccuracy = acc
        lastFixTimeMs = location.time
        lastFixProvider = location.provider ?: ""
        lastFixElapsedMs = SystemClock.elapsedRealtime()
        deadReckonMeters = 0.0 // volvio la senal: se acabo la estimacion

        // Si se esta centrando, este fix bueno es el que manda.
        if (centering && acc <= CENTER_ACCURACY_M) {
            centerTimeoutJob?.cancel()
            centering = false
            centerOn(location)
        }

        // 1) Kalman: suaviza el fix crudo segun su precision.
        val pos = kalman.update(location.latitude, location.longitude, acc, location.time)
        recentPoints.addLast(pos to acc)
        while (recentPoints.size > 5) recentPoints.removeFirst()

        val prev = lastLocation
        val speedMps = when {
            location.hasSpeed() -> location.speed.toDouble()
            prev != null && location.time > prev.time -> {
                val dt = (location.time - prev.time) / 1000.0
                if (dt > 0) RouteEngine.metersBetween(
                    LatLng(prev.latitude, prev.longitude), pos,
                ) / dt else 0.0
            }
            else -> 0.0
        }
        lastLocation = location
        lastSpeedMps = speedMps
        speedValue.text = (speedMps * 2.23694).toInt().toString()

        // ESTACIONADO: velocidad ~0 -> congelar el carro, el ruido del GPS
        // no lo mueve ni lo saca de la calle.
        if (speedMps < 0.5 && lastCarPos != null) return

        val r = route
        if (r != null) {
            // 2) HMM: el mejor segmento por distancia + rumbo +
            // continuidad, no simplemente el mas cercano.
            val gpsBearing = if (location.hasBearing()) {
                location.bearing.toDouble()
            } else {
                lastBearing
            }
            val match = MapMatcher.matchToRoute(
                r, pos, gpsBearing, speedMps, routeIndex, lastMatchedIndex, acc
            )
            lastMatchedIndex = match.snap.index
            if (match.snap.distance > OFF_ROUTE_METERS || match.score < 0.15) {
                offRouteCount++
                if (offRouteCount >= 3) {
                    offRouteCount = 0
                    routeTo(destination!!, destinationName, fromRaw = pos)
                    return
                }
            } else {
                offRouteCount = 0
                routeIndex = match.snap.index
                lastCarPos = match.snap.snapped
                lastBearing = match.snap.bearing
                lastProgressMeters = match.snap.progressMeters
                updateCarMarker()
                updateGuidance(match.snap.progressMeters)
                if (match.snap.progressMeters > r.totalDistance - 20) {
                    // Llegada.
                    Toast.makeText(this, R.string.arrived, Toast.LENGTH_LONG).show()
                    cancelRoute()
                    return
                }
            }
            if (followMode) applyAutoZoom(lastProgressMeters)
            lastCarPos?.let { if (followMode) moveCamera(it, lastBearing) }
        } else {
            // Sin ruta: OSRM Match (cada 5 s, solo en movimiento) pega
            // el carro a la calle mas cercana; si no hay internet o
            // falla, se usa el punto filtrado por Kalman.
            maybeOsrmMatch()
            val osrm = lastOsrmMatched
            val useOsrm = osrm != null &&
                SystemClock.elapsedRealtime() - lastOsrmMatchedAt < 6_000 &&
                RouteEngine.metersBetween(pos, osrm) < 100.0
            val displayPos = if (useOsrm) osrm else pos
            if (lastCarPos != null) {
                lastBearing = RouteEngine.bearingBetween(lastCarPos!!, displayPos)
            }
            lastCarPos = displayPos
            updateCarMarker()
            if (followMode) {
                map?.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        com.mapbox.mapboxsdk.camera.CameraPosition.Builder()
                            .target(displayPos).zoom(15.0).tilt(45.0)
                            .bearing(if (headingUp) lastBearing else 0.0).build()
                    ),
                    700,
                )
            }
        }
    }

    /**
     * OSRM Match throttled (v1.3): como mucho una llamada cada 5 s y
     * solo en movimiento, con los ultimos 5 puntos filtrados. El
     * resultado se guarda y lo usa el siguiente fix sin ruta.
     */
    private fun maybeOsrmMatch() {
        if (route != null) return
        if (lastSpeedMps < 3.0) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastOsrmMatchAt < OSRM_MATCH_MIN_INTERVAL_MS) return
        if (recentPoints.size < 3) return
        lastOsrmMatchAt = now
        val pts = recentPoints.map { it.first }
        val rads = recentPoints.map { it.second }
        scope.launch {
            val matched = withContext(Dispatchers.IO) {
                RouteEngine.matchPoints(pts, rads)
            }
            if (!matched.isNullOrEmpty()) {
                val last = matched.last()
                val cur = lastCarPos
                if (cur == null || RouteEngine.metersBetween(cur, last) < 100.0) {
                    lastOsrmMatched = last
                    lastOsrmMatchedAt = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    /**
     * Dead reckoning (v1.3): si el GPS se pierde mas de 3 s (tunel,
     * parqueo techado), el carro sigue avanzando por la calle con la
     * ultima velocidad y rumbo. Se detiene a los 30 s sin senal o en
     * cuanto vuelve un fix.
     */
    private fun startDeadReckoning() {
        deadReckonJob?.cancel()
        deadReckonJob = scope.launch {
            while (isActive) {
                delay(1000)
                tickDeadReckoning()
            }
        }
    }

    private fun tickDeadReckoning() {
        if (lastFixElapsedMs == 0L) return
        val sinceFix = SystemClock.elapsedRealtime() - lastFixElapsedMs
        if (sinceFix < 3_000 || sinceFix > 30_000) return
        if (lastSpeedMps < 1.0 || lastCarPos == null) return
        // Sin senal el carro no puede avanzar para siempre: maximo 500 m
        // estimados (tuneles cortos). Mas alla, se queda donde estaba.
        if (deadReckonMeters >= 500.0) return
        deadReckonMeters += lastSpeedMps
        val r = route
        if (r != null) {
            val newProgress = (lastProgressMeters + lastSpeedMps)
                .coerceAtMost(r.totalDistance)
            val point = MapMatcher.pointAlongRoute(r, newProgress)
            lastProgressMeters = newProgress
            lastCarPos = point
            lastBearing = MapMatcher.bearingAlongRoute(r, newProgress)
            updateCarMarker()
            updateGuidance(newProgress)
            if (followMode) {
                applyAutoZoom(newProgress)
                moveCamera(point, lastBearing)
            }
        } else {
            val next = MapMatcher.advanceFree(lastCarPos!!, lastBearing, lastSpeedMps)
            lastCarPos = next
            updateCarMarker()
            if (followMode) moveCamera(next, lastBearing)
        }
    }

    // ------------------------------------------------------------- centrar

    private fun onCenter() {
        if (!hasLocationPermission()) {
            ensureLocationPermission()
            return
        }
        centering = true
        followMode = true
        // Pedir un fix fresco y preciso; si no llega, lo cubren las
        // actualizaciones continuas y el timeout avisa.
        if (fusedAvailable()) {
            val client = fusedClient ?: LocationServices.getFusedLocationProviderClient(this)
            fusedClient = client
            try {
                val source = CancellationTokenSource()
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, source.token)
                    .addOnSuccessListener { loc ->
                        if (loc != null && centering) {
                            val age = System.currentTimeMillis() - loc.time
                            if (age <= STALE_FIX_MS && loc.accuracy > 0f &&
                                loc.accuracy <= CENTER_ACCURACY_M
                            ) {
                                centerTimeoutJob?.cancel()
                                centering = false
                                centerOn(loc)
                            }
                        }
                    }
            } catch (_: SecurityException) {
            }
        }
        centerTimeoutJob?.cancel()
        centerTimeoutJob = scope.launch {
            delay(CENTER_TIMEOUT_MS)
            if (centering) {
                centering = false
                Toast.makeText(this@MainActivity, R.string.center_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun centerOn(location: Location) {
        val pos = LatLng(location.latitude, location.longitude)
        lastCarPos = pos
        lastLocation = location
        updateCarMarker()
        map?.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                com.mapbox.mapboxsdk.camera.CameraPosition.Builder()
                    .target(pos).zoom(16.0).tilt(45.0)
                    .bearing(if (headingUp) lastBearing else 0.0).build()
            ),
            700,
        )
        Toast.makeText(this, R.string.centered, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------- guia

    private fun updateGuidance(progressMeters: Double) {
        val r = route ?: return
        val next = RouteEngine.nextStep(r, progressMeters)
        if (next == null) {
            guidanceCard.visibility = View.GONE
            return
        }
        val (step, remaining) = next
        guidanceCard.visibility = View.VISIBLE
        maneuverArrow.rotation = RouteEngine.maneuverArrowRotation(step)
        maneuverTitle.text = RouteEngine.maneuverText(this, step) +
            " " + RouteEngine.formatDistance(remaining, this)
        maneuverStreet.text = step.name.ifEmpty { "" }
        val remainDist = (r.totalDistance - progressMeters).coerceAtLeast(0.0)
        val remainDurMin = (r.totalDuration * (remainDist / r.totalDistance.coerceAtLeast(1.0))).toLong()
        val eta = Date(System.currentTimeMillis() + remainDurMin * 1000)
        maneuverEta.text = getString(
            R.string.eta_format,
            SimpleDateFormat("h:mm a", Locale("es", "DO")).format(eta),
            "${(remainDurMin / 60).toInt()} min",
        )
        // El panel CRECE cuando el giro esta cerca (mockup v2).
        setGuidanceExpanded(remaining < 300)
    }

    private fun setGuidanceExpanded(expanded: Boolean) {
        if (expanded == guidanceExpanded) return
        guidanceExpanded = expanded
        maneuverTitle.textSize = if (expanded) 22f else 17f
        maneuverStreet.textSize = if (expanded) 16f else 14f
        val arrowLp = maneuverArrow.layoutParams
        val size = (if (expanded) 44 else 32) * resources.displayMetrics.density
        arrowLp.width = size.toInt()
        arrowLp.height = size.toInt()
        maneuverArrow.layoutParams = arrowLp
    }

    /** Zoom automatico segun lo que falta para la maniobra. */
    private fun applyAutoZoom(progressMeters: Double) {
        val r = route ?: return
        if (SystemClock.uptimeMillis() < manualZoomUntil) return
        val next = RouteEngine.nextStep(r, progressMeters)
        val remaining = next?.second ?: Double.MAX_VALUE
        val band = when {
            remaining < 200 -> 3 // giro encima: bien cerca
            remaining < 600 -> 2 // acercandose
            remaining > 1500 && RouteEngine.isStraightAhead(r, progressMeters, 1500.0) -> 0
            else -> 1 // recto normal
        }
        if (band == lastZoomBand) return
        lastZoomBand = band
        val (zoom, tilt, simple) = when (band) {
            3 -> Triple(17.5, 45.0, false)
            2 -> Triple(16.5, 45.0, false)
            0 -> Triple(14.0, 0.0, true) // trayecto largo recto: de arriba, simple
            else -> Triple(15.5, 45.0, false)
        }
        setSimplified(simple)
        moveCamera(lastCarPos ?: return, lastBearing, zoom, tilt)
    }

    /**
     * Modo simplificado (trayecto largo recto): se esconden edificios y
     * zonas para que el radio no tenga que dibujar tanto; solo quedan
     * calles, ruta y nombres.
     */
    private fun setSimplified(on: Boolean) {
        if (on == simplified && style != null) return
        simplified = on
        val st = style ?: return
        for (layer in st.layers) {
            val id = layer.id
            if (id.startsWith("building") || id.startsWith("landuse") ||
                id.startsWith("landcover") || id.startsWith("aeroway")
            ) {
                layer.setProperties(
                    PropertyFactory.visibility(
                        if (on) Property.NONE else Property.VISIBLE
                    )
                )
            }
        }
    }

    private fun moveCamera(target: LatLng, bearing: Double, zoom: Double? = null, tilt: Double? = null) {
        val m = map ?: return
        val cur = m.cameraPosition
        m.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                com.mapbox.mapboxsdk.camera.CameraPosition.Builder()
                    .target(target)
                    .zoom(zoom ?: cur.zoom)
                    .tilt(tilt ?: cur.tilt)
                    .bearing(if (headingUp) bearing else 0.0)
                    .build()
            ),
            700,
        )
    }

    private fun updateCarMarker() {
        val pos = lastCarPos ?: return
        carSource?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(pos.longitude, pos.latitude)))
        carLayer?.setProperties(
            PropertyFactory.iconRotate(
                if (headingUp) 0f else lastBearing.toFloat()
            )
        )
    }

    // ------------------------------------------------------------- ruta

    private fun routeTo(dest: LatLng, name: String?, fromRaw: LatLng? = null) {
        val origin = fromRaw
            ?: currentOrigin()
            ?: SANTO_DOMINGO
        destination = dest
        destinationName = name
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    // GraphHopper offline primero, OSRM con internet si no hay grafo.
                    GraphHopperRouter.fetchRoutesResult(origin, dest)
                } catch (t: Throwable) {
                    RouteResult(
                        null, RouteFailure.GH_ERROR,
                        t.message ?: t.javaClass.simpleName,
                    )
                }
            }
            val newRoute = result.routes?.firstOrNull()
            if (newRoute == null) {
                // v1.0.3: motivo exacto en vez de un generico, y en el registro.
                val reason = routeFailureText(result)
                CrashLog.logEvent(
                    this@MainActivity,
                    "Ruta a ${name ?: dest}: ${result.failure} — ${result.detail}",
                )
                Toast.makeText(this@MainActivity, reason, Toast.LENGTH_LONG).show()
                return@launch
            }
            route = newRoute
            routeIndex = 0
            lastMatchedIndex = 0
            lastProgressMeters = 0.0
            offRouteCount = 0
            lastZoomBand = -1
            followMode = true
            btnCancelRoute.visibility = View.VISIBLE
            guidanceCard.visibility = View.VISIBLE
            destSource?.setGeoJson(
                Feature.fromGeometry(Point.fromLngLat(dest.longitude, dest.latitude))
            )
            redrawRoute()
        }
    }

    /**
     * Previsualizacion de rutas (v1.7): entrada UNICA para cualquier
     * destino que elige el usuario (busqueda, lugar guardado, punto
     * del mapa o telefono). NO arranca la guia de inmediato: pide las
     * rutas posibles a OSRM y las muestra para elegir. Si habia una
     * ruta activa, se cancela primero (el nuevo destino la reemplaza).
     *
     * v1.0.3: si el grafo offline aun se esta cargando (copia de 82 MB
     * en el primer arranque) o no hay fix GPS todavia, NO falla de
     * inmediato: espera un poco y reintenta. Y si la ruta no se puede
     * calcular, el panel dice el motivo exacto (no un generico) y el
     * motivo queda en el registro de errores.
     */
    private fun previewRoutes(dest: LatLng, name: String?) {
        cancelRoute()
        hideOverlays()
        hideKeyboard()
        searchInput.clearFocus()
        previewDest = dest
        previewDestName = name
        destination = dest
        destinationName = name
        previewJob?.cancel()
        previewJob = scope.launch {
            // (1) Sin fix GPS todavia: esperar hasta 20 s a que llegue
            // uno. Si no llega, se sigue con el centro de Santo
            // Domingo, como siempre (y queda en el registro).
            var origin = currentOrigin()
            if (origin == null) {
                showPreviewMessage(getString(R.string.route_waiting_gps))
                waitForFix(20_000)
                origin = currentOrigin()
                if (origin == null) {
                    CrashLog.logEvent(
                        this@MainActivity,
                        "Ruta a ${name ?: dest}: sin fix GPS, origen Santo Domingo",
                    )
                    origin = SANTO_DOMINGO
                }
            }
            // (2) GraphHopper offline primero, OSRM con internet si no
            // hay grafo (con espejo y motivo concreto, v1.0.3).
            var result = withContext(Dispatchers.IO) {
                try {
                    GraphHopperRouter.fetchRoutesResult(origin, dest)
                } catch (t: Throwable) {
                    RouteResult(
                        null, RouteFailure.GH_ERROR,
                        t.message ?: t.javaClass.simpleName,
                    )
                }
            }
            // (3) Si fallo y el grafo aun se esta cargando, esperar a
            // que este listo (hasta 45 s) y reintentar solo offline:
            // en el primer arranque la copia tarda decenas de segundos
            // en el radio y la ruta no debe fallar por eso.
            if (result.routes.isNullOrEmpty() &&
                GraphHopperRouter.loadState == GhStatus.LOADING
            ) {
                showPreviewMessage(getString(R.string.route_preparing))
                if (waitForGraph(45_000)) {
                    result = withContext(Dispatchers.IO) {
                        try {
                            GraphHopperRouter.fetchOfflineResult(origin, dest)
                        } catch (t: Throwable) {
                            RouteResult(
                                null, RouteFailure.GH_ERROR,
                                t.message ?: t.javaClass.simpleName,
                            )
                        }
                    }
                }
            }
            val routes = result.routes
            if (!routes.isNullOrEmpty()) {
                showRoutePreview(routes)
                return@launch
            }
            // (4) Motivo exacto en el panel y en el registro, no un
            // generico. El pin queda puesto y el panel permite
            // guardar el lugar.
            val reason = routeFailureText(result)
            CrashLog.logEvent(
                this@MainActivity,
                "Ruta a ${name ?: dest}: ${result.failure} — ${result.detail}",
            )
            Toast.makeText(this@MainActivity, reason, Toast.LENGTH_LONG).show()
            showPreviewMessage("$reason\n${getString(R.string.route_fail_hint)}")
        }
    }

    /** Origen de la ruta: posicion del carro, ultimo fix, o null si no
        hay ningun fix todavia. */
    private fun currentOrigin(): LatLng? =
        lastCarPos ?: lastLocation?.let { LatLng(it.latitude, it.longitude) }

    /** Espera hasta [timeoutMs] a que llegue un fix GPS (v1.0.3). */
    private suspend fun waitForFix(timeoutMs: Long): Boolean {
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            if (currentOrigin() != null) return true
            delay(500)
        }
        return currentOrigin() != null
    }

    /** Espera hasta [timeoutMs] a que el grafo offline este listo. */
    private suspend fun waitForGraph(timeoutMs: Long): Boolean {
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            if (GraphHopperRouter.isReady) return true
            if (GraphHopperRouter.loadState == GhStatus.FAILED) return false
            delay(500)
        }
        return GraphHopperRouter.isReady
    }

    /** Texto corto en espanol para el motivo del fallo (v1.0.3). */
    private fun routeFailureText(result: RouteResult): String {
        return when (result.failure) {
            RouteFailure.GH_NOT_READY -> getString(R.string.route_fail_gh_loading)
            RouteFailure.GH_ERROR -> getString(R.string.route_fail_gh_error)
            RouteFailure.OSRM_NETWORK -> getString(R.string.route_fail_osrm_network)
            RouteFailure.OSRM_HTTP -> getString(R.string.route_fail_osrm_http)
            RouteFailure.OSRM_NO_ROUTE -> getString(R.string.route_fail_osrm_noroute)
            RouteFailure.NONE -> getString(R.string.no_route)
        }
    }

    /** Dibuja las rutas de la previsualizacion: la elegida en azul,
        las demas en gris (cada una con su indice para tocarla). */
    private fun drawPreviewLines() {
        val routes = previewRoutes ?: return
        val sel = routes.getOrNull(previewSelected) ?: return
        val pts = sel.points.map { Point.fromLngLat(it.longitude, it.latitude) }
        routeSource?.setGeoJson(
            FeatureCollection.fromFeatures(
                listOf(Feature.fromGeometry(LineString.fromLngLats(pts)))
            )
        )
        val feats = ArrayList<Feature>()
        for ((i, r) in routes.withIndex()) {
            if (i == previewSelected) continue
            val f = Feature.fromGeometry(
                LineString.fromLngLats(
                    r.points.map { Point.fromLngLat(it.longitude, it.latitude) }
                )
            )
            f.addNumberProperty("idx", i)
            feats.add(f)
        }
        altRouteSource?.setGeoJson(FeatureCollection.fromFeatures(feats))
    }

    /** Muestra el panel con las rutas: nombre (calle principal o
        "Ruta N"), distancia y tiempo. Tocar una fila arranca la guia
        con ella. Si solo hay una, igual se muestra para confirmar. */
    private fun showRoutePreview(routes: List<Route>) {
        previewRoutes = routes
        previewSelected = 0
        val dest = previewDest ?: return
        destSource?.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(dest.longitude, dest.latitude))
        )
        drawPreviewLines()
        fitCameraToRoutes(routes)
        // Durante la vista previa el mapa no sigue al carro, para que
        // el encuadre de las rutas se quede quieto.
        followBeforePreview = followMode
        followMode = false
        previewTitle.text = previewDestName ?: getString(R.string.choose_route)
        previewRows.removeAllViews()
        val density = resources.displayMetrics.density
        for ((i, r) in routes.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_chip)
                setPadding(
                    (12 * density).toInt(), (10 * density).toInt(),
                    (12 * density).toInt(), (10 * density).toInt(),
                )
            }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            val title = TextView(this).apply {
                text = if (r.viaName.isNotEmpty()) r.viaName
                    else getString(R.string.route_n, i + 1)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(
                    ContextCompat.getColor(this@MainActivity, R.color.text_primary)
                )
            }
            val sub = TextView(this).apply {
                text = getString(
                    R.string.route_summary,
                    RouteEngine.formatDistance(r.totalDistance, this@MainActivity),
                    (r.totalDuration / 60).roundToInt().coerceAtLeast(1),
                )
                textSize = 13f
                maxLines = 1
                setTextColor(
                    ContextCompat.getColor(this@MainActivity, R.color.text_secondary)
                )
            }
            texts.addView(title)
            texts.addView(sub)
            val go = TextView(this).apply {
                text = getString(R.string.go)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(
                    ContextCompat.getColor(this@MainActivity, R.color.accent_blue)
                )
                setPadding((10 * density).toInt(), 0, 0, 0)
            }
            row.addView(texts)
            row.addView(go)
            row.setOnClickListener { startPreviewRoute(i) }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = (6 * density).toInt()
            previewRows.addView(row, lp)
        }
        routePreviewPanel.visibility = View.VISIBLE
    }

    /** Panel de previsualizacion con un mensaje (espera o motivo del
        fallo, v1.0.3): titulo del destino, pin puesto, y el texto.
        Antes solo habia un aviso generico sin internet. */
    private fun showPreviewMessage(message: String) {
        previewRoutes = null
        val dest = previewDest ?: return
        destSource?.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(dest.longitude, dest.latitude))
        )
        previewTitle.text = previewDestName ?: getString(R.string.choose_route)
        previewRows.removeAllViews()
        val info = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(
                ContextCompat.getColor(this@MainActivity, R.color.text_secondary)
            )
            setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, 0)
        }
        previewRows.addView(info)
        routePreviewPanel.visibility = View.VISIBLE
    }

    /** Encuadra todas las rutas en pantalla, con margen para la
        columna izquierda y el panel de abajo. */
    private fun fitCameraToRoutes(routes: List<Route>) {
        val m = map ?: return
        val w = mapView.width
        val h = mapView.height
        if (w <= 0 || h <= 0) return
        var minLat = 90.0
        var maxLat = -90.0
        var minLng = 180.0
        var maxLng = -180.0
        for (r in routes) {
            for (p in r.points) {
                if (p.latitude < minLat) minLat = p.latitude
                if (p.latitude > maxLat) maxLat = p.latitude
                if (p.longitude < minLng) minLng = p.longitude
                if (p.longitude > maxLng) maxLng = p.longitude
            }
        }
        if (maxLat - minLat < 0.002 && maxLng - minLng < 0.002) {
            // Ruta muy corta: centrar en el destino con zoom normal.
            val dest = previewDest ?: return
            m.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    com.mapbox.mapboxsdk.camera.CameraPosition.Builder()
                        .target(dest).zoom(15.0).tilt(0.0).bearing(0.0).build()
                ),
                700,
            )
            return
        }
        val density = resources.displayMetrics.density
        val left = minOf((360 * density).toInt(), (w * 0.4).toInt())
        val right = minOf((60 * density).toInt(), (w * 0.1).toInt())
        val top = minOf((40 * density).toInt(), (h * 0.15).toInt())
        val bottom = minOf((220 * density).toInt(), (h * 0.5).toInt())
        val bounds = LatLngBounds.Builder()
            .include(LatLng(maxLat, maxLng))
            .include(LatLng(minLat, minLng))
            .build()
        try {
            m.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, left, top, right, bottom),
                700,
            )
        } catch (_: Exception) {
            // Si el encuadre no cabe (pantalla muy chica), no romper.
        }
    }

    /** Tocar una linea gris en el mapa arranca la guia con esa ruta. */
    private fun tryPreviewMapTap(latLng: LatLng): Boolean {
        val m = map ?: return false
        val screen = m.projection.toScreenLocation(latLng)
        val tol = 20 * resources.displayMetrics.density
        val rect = RectF(
            screen.x - tol, screen.y - tol, screen.x + tol, screen.y + tol
        )
        val feats = try {
            m.queryRenderedFeatures(rect, "alt-line")
        } catch (_: Exception) {
            emptyList()
        }
        val first = feats.firstOrNull() ?: return false
        val idx = first.getNumberProperty("idx")?.toInt() ?: return false
        startPreviewRoute(idx)
        return true
    }

    /** El usuario eligio una ruta de la previsualizacion: arranca la guia. */
    private fun startPreviewRoute(index: Int) {
        val routes = previewRoutes ?: return
        if (index !in routes.indices) return
        activateRoute(routes[index])
    }

    /** Activa una ruta ya calculada (lo mismo que hacia routeTo al
        recibirla): pin, guia, seguimiento y camara. */
    private fun activateRoute(selected: Route) {
        val dest = previewDest ?: destination ?: return
        val name = previewDestName ?: destinationName
        cancelPreview()
        route = selected
        routeIndex = 0
        lastMatchedIndex = 0
        lastProgressMeters = 0.0
        offRouteCount = 0
        lastZoomBand = -1
        followMode = true
        btnCancelRoute.visibility = View.VISIBLE
        guidanceCard.visibility = View.VISIBLE
        destination = dest
        destinationName = name
        destSource?.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(dest.longitude, dest.latitude))
        )
        redrawRoute()
    }

    /** Cierra la previsualizacion sin arrancar ninguna ruta. */
    private fun cancelPreview() {
        previewJob?.cancel()
        previewJob = null
        previewRoutes = null
        previewDest = null
        previewDestName = null
        previewSelected = 0
        routePreviewPanel.visibility = View.GONE
        previewRows.removeAllViews()
        altRouteSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }

    /** "Cancelar" del panel: cierra la vista previa y quita el pin. */
    private fun onPreviewCancel() {
        cancelPreview()
        destSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        destination = null
        destinationName = null
        followMode = followBeforePreview
    }

    /** "Guardar en favoritos" del panel: el destino de la vista previa,
        con el mismo selector de categoria que el long-press. */
    private fun onPreviewSave() {
        val dest = previewDest ?: return
        val name = previewDestName ?: getString(R.string.point_on_map)
        showCategoryPicker(previewSave) { category ->
            scope.launch {
                FavoritesStore.add(
                    this@MainActivity,
                    Favorite(name, "", dest.latitude, dest.longitude, category),
                )
                Toast.makeText(this@MainActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                selectedCategory = category
                refreshFavoriteChips()
            }
        }
    }

    private fun redrawRoute() {
        val r = route
        if (r == null) {
            routeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val points = r.points.map { Point.fromLngLat(it.longitude, it.latitude) }
        routeSource?.setGeoJson(
            FeatureCollection.fromFeatures(
                listOf(Feature.fromGeometry(LineString.fromLngLats(points)))
            )
        )
    }

    private fun cancelRoute() {
        cancelPreview()
        route = null
        destination = null
        destinationName = null
        routeIndex = 0
        lastMatchedIndex = 0
        lastProgressMeters = 0.0
        btnCancelRoute.visibility = View.GONE
        guidanceCard.visibility = View.GONE
        setSimplified(false)
        destSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        redrawRoute()
    }

    // ------------------------------------------------------------- controles

    private fun onCompass() {
        headingUp = !headingUp
        followMode = true
        lastCarPos?.let { moveCamera(it, lastBearing) }
        Toast.makeText(
            this,
            if (headingUp) "Hacia donde voy" else "Norte arriba",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun onDayNight() {
        // El boton rapido fija el modo explicito (el selector de Ajustes
        // sigue siendo el control principal).
        setMode(if (isNightNow()) AppPrefs.MODE_LIGHT else AppPrefs.MODE_DARK)
    }

    private fun onZoomButton(delta: Double) {
        val m = map ?: return
        manualZoomUntil = SystemClock.uptimeMillis() + MANUAL_ZOOM_MS
        lastZoomBand = -1
        val target = (m.cameraPosition.zoom + delta).coerceIn(5.0, 19.0)
        m.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                com.mapbox.mapboxsdk.camera.CameraPosition.Builder()
                    .target(m.cameraPosition.target)
                    .zoom(target)
                    .tilt(m.cameraPosition.tilt)
                    .bearing(m.cameraPosition.bearing)
                    .build()
            ),
            400,
        )
    }

    private fun onMapLongPress(latLng: LatLng) {
        pendingPoint = latLng
        pointTitle.setText(R.string.searching)
        pointSub.text = "%.5f, %.5f".format(latLng.latitude, latLng.longitude)
        pointCard.visibility = View.VISIBLE
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                try {
                    PhotonApi.reverse(latLng.latitude, latLng.longitude)
                } catch (_: Exception) {
                    null
                }
            }
            pointTitle.text = name ?: getString(R.string.point_on_map)
        }
    }

    private fun onGoToPoint() {
        val p = pendingPoint ?: return
        // v1.7: no arranca la guia directo; muestra las rutas posibles.
        previewRoutes(p, pointTitle.text.toString())
    }

    /** Selector de categoria (v1.6, reutilizado en v1.7): Locales,
        Restaurantes o Favoritos. Lo usan el long-press, la estrella
        de los resultados y el panel de vista previa. */
    private fun showCategoryPicker(anchor: View, onPick: (String) -> Unit) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, R.string.cat_locales)
        popup.menu.add(0, 1, 0, R.string.cat_restaurantes)
        popup.menu.add(0, 2, 0, R.string.cat_favoritos)
        popup.setOnMenuItemClickListener { item ->
            val category = when (item.itemId) {
                0 -> FavoriteCategory.LOCALES
                1 -> FavoriteCategory.RESTAURANTES
                else -> FavoriteCategory.FAVORITOS
            }
            onPick(category)
            true
        }
        popup.show()
    }

    private fun onSavePoint() {
        val p = pendingPoint ?: return
        val name = pointTitle.text.toString()
        val address = pointSub.text.toString()
        // v1.6: el usuario elige en que categoria lo guarda.
        showCategoryPicker(findViewById<TextView>(R.id.btnSavePoint)) { category ->
            scope.launch {
                FavoritesStore.add(
                    this@MainActivity,
                    Favorite(name, address, p.latitude, p.longitude, category),
                )
                Toast.makeText(this@MainActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                selectedCategory = category
                refreshFavoriteChips()
            }
        }
        pointCard.visibility = View.GONE
    }

    // ------------------------------------------------------------- ajustes

    private fun toggleSettings() {
        if (settingsPanel.visibility == View.VISIBLE) {
            hideSettings()
        } else {
            hideOverlays()
            settingsPanel.visibility = View.VISIBLE
            refreshSettingsPanel()
        }
    }

    private fun hideSettings() {
        settingsPanel.visibility = View.GONE
    }

    private fun refreshSettingsPanel() {
        applyModeToUi()
        musicPermStatus.text = getString(
            if (MusicController.hasAccess(this)) R.string.music_perm_on
            else R.string.music_perm_off
        )
        btnMusicPerm.text = getString(
            if (MusicController.hasAccess(this)) R.string.music_perm_on
            else R.string.music_perm_allow
        )
        refreshOfflineStatus()
        refreshPhoneStatus()
        refreshCrashLogStatus()
    }

    /** Registro de errores (v1.0.1): ultimo cierre y boton para compartirlo. */
    private fun refreshCrashLogStatus() {
        if (!::crashLogStatus.isInitialized) return
        val info = CrashLog.lastCrashInfo(this)
        if (info == null) {
            crashLogStatus.setText(R.string.crash_none)
            btnShareCrashLog.visibility = View.GONE
        } else {
            crashLogStatus.text = getString(R.string.crash_last, info.first, info.second)
            btnShareCrashLog.visibility = View.VISIBLE
        }
    }

    private fun shareCrashLog() {
        val intent = CrashLog.shareIntent(this) ?: run {
            Toast.makeText(this, R.string.crash_none, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent.createChooser(intent, getString(R.string.crash_share_title)))
    }

    private fun refreshOfflineStatus() {
        if (offlineDownloading) return
        OfflineMapManager.queryStatus(this) { infos ->
            runOnUiThread {
                if (infos.isEmpty()) {
                    offlineStatus.setText(R.string.offline_not_downloaded)
                } else {
                    val size = infos.sumOf { it.sizeBytes }
                    val complete = infos.size == 2 && infos.all { it.complete }
                    offlineStatus.text = if (complete) {
                        getString(
                            R.string.offline_downloaded,
                            OfflineMapManager.formatSize(size),
                        )
                    } else {
                        getString(R.string.offline_incomplete)
                    }
                }
            }
        }
    }

    private fun startOfflineDownload() {
        if (offlineDownloading) return
        offlineDownloading = true
        offlineStatus.setText(R.string.offline_needs_wifi)
        offlineProgress.visibility = View.VISIBLE
        offlineProgress.progress = 0
        setOfflineButtonsEnabled(false)
        OfflineMapManager.downloadAll(
            this, STYLE_DARK, STYLE_LIGHT,
            onProgress = { frac, bytes ->
                runOnUiThread {
                    offlineProgress.progress = (frac * 100).toInt()
                    offlineStatus.text = getString(
                        R.string.offline_downloading,
                        (frac * 100).toInt(),
                        OfflineMapManager.formatSize(bytes),
                    )
                }
            },
            onDone = { ok, error ->
                runOnUiThread {
                    offlineDownloading = false
                    offlineProgress.visibility = View.GONE
                    setOfflineButtonsEnabled(true)
                    if (ok) {
                        Toast.makeText(this, R.string.offline_ready, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.offline_error) +
                                (error?.let { ": $it" } ?: ""),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    refreshOfflineStatus()
                }
            },
        )
    }

    private fun deleteOfflineMap() {
        if (offlineDownloading) return
        OfflineMapManager.deleteAll(this) {
            runOnUiThread {
                Toast.makeText(this, R.string.offline_deleted, Toast.LENGTH_SHORT).show()
                refreshOfflineStatus()
            }
        }
    }

    private fun setOfflineButtonsEnabled(enabled: Boolean) {
        btnDownloadMap.isEnabled = enabled
        btnUpdateMap.isEnabled = enabled
        btnDeleteMap.isEnabled = enabled
        val alpha = if (enabled) 1f else 0.4f
        btnDownloadMap.alpha = alpha
        btnUpdateMap.alpha = alpha
        btnDeleteMap.alpha = alpha
    }

    // ------------------------------------------------------------- musica

    /**
     * Pantalla partida (v1.4): con musica, el mapa se achica a una
     * capsula a la izquierda y el reproductor ocupa todo el lado
     * derecho; sin musica, el mapa vuelve a pantalla completa.
     *
     * Animacion BARATA, como manda la regla de optimizacion: un solo
     * ValueAnimator de 250 ms sobre el peso del reproductor, una sola
     * pasada de layout por frame, sin opacidad, sin blur, sin sombras
     * ni gradientes. Si algun dia tironea, se quita la animacion y el
     * cambio es instantaneo.
     */
    private fun applySplitLayout(musicOn: Boolean) {
        if (musicOn == musicSplitOn) return
        musicSplitOn = musicOn
        splitAnimator?.cancel()
        val mapLp = mapCapsule.layoutParams as LinearLayout.LayoutParams
        val musicLp = musicCard.layoutParams as LinearLayout.LayoutParams
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        if (musicOn) {
            // Capsula del mapa: margenes + esquinas redondeadas. El
            // MapView va en modo TextureView para que clipToOutline
            // recorte de verdad sus esquinas.
            mapCapsule.setBackgroundResource(R.drawable.bg_capsule)
            mapCapsule.clipToOutline = true
            mapLp.setMargins(dp(12), dp(12), dp(6), dp(12))
            mapCapsule.layoutParams = mapLp
            musicLp.setMargins(dp(6), dp(12), dp(12), dp(12))
            musicLp.weight = 0f
            musicCard.layoutParams = musicLp
            musicCard.visibility = View.VISIBLE
            val anim = ValueAnimator.ofFloat(0f, MUSIC_WEIGHT)
            anim.duration = SPLIT_ANIM_MS
            anim.addUpdateListener {
                musicLp.weight = it.animatedValue as Float
                musicCard.layoutParams = musicLp
            }
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (splitAnimator == anim) splitAnimator = null
                }
            })
            splitAnimator = anim
            anim.start()
        } else {
            val anim = ValueAnimator.ofFloat(musicLp.weight, 0f)
            anim.duration = SPLIT_ANIM_MS
            anim.addUpdateListener {
                musicLp.weight = it.animatedValue as Float
                musicCard.layoutParams = musicLp
            }
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (splitAnimator != anim) return
                    splitAnimator = null
                    musicCard.visibility = View.GONE
                    // Mapa a pantalla completa: sin fondo ni recorte.
                    mapCapsule.background = null
                    mapCapsule.clipToOutline = false
                    mapLp.setMargins(0, 0, 0, 0)
                    mapCapsule.layoutParams = mapLp
                }
            })
            splitAnimator = anim
            anim.start()
        }
    }

    private fun refreshMusic() {
        val players = MusicController.activePlayers(this)
        if (players.isEmpty()) {
            currentPlayer = null
            lastMusicArtKey = null
            applySplitLayout(false)
            btnShowMusic.visibility = View.GONE
            return
        }
        val current = players.find { it.packageName == selectedPlayerPkg } ?: players.first()
        if (players.none { it.packageName == selectedPlayerPkg }) {
            selectedPlayerPkg = current.packageName
        }
        currentPlayer = current
        if (musicHiddenByUser) {
            applySplitLayout(false)
            btnShowMusic.visibility = View.VISIBLE
            return
        }
        applySplitLayout(true)
        btnShowMusic.visibility = View.GONE
        musicPlayerChip.text = current.appName + " ▾"
        musicTitle.text = current.title
        musicArtist.text = current.artist
        // La portada solo se recarga si cambio la cancion o el reproductor.
        val artKey = current.packageName + "|" + current.title
        if (artKey != lastMusicArtKey) {
            lastMusicArtKey = artKey
            val art = current.albumArt
            if (art != null) {
                musicArt.setImageBitmap(art)
                musicArt.visibility = View.VISIBLE
            } else {
                musicArt.setImageBitmap(null)
                musicArt.visibility = View.GONE
            }
        }
        // Progreso de la cancion.
        val dur = current.durationMs
        if (dur > 0) {
            val pos = current.positionMs.coerceIn(0L, dur)
            musicProgress.max = 1000
            musicProgress.progress = ((pos * 1000L) / dur).toInt()
            musicTimeCur.text = formatMusicTime(pos)
            musicTimeTotal.text = formatMusicTime(dur)
        } else {
            musicProgress.progress = 0
            musicTimeCur.text = ""
            musicTimeTotal.text = ""
        }
        musicPlayPause.setImageResource(
            if (current.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun formatMusicTime(ms: Long): String {
        val totalSec = (ms / 1000).toInt()
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    private fun showPlayerSwitcher() {
        val players = MusicController.activePlayers(this)
        if (players.isEmpty()) return
        val popup = PopupMenu(this, musicPlayerChip)
        for ((index, player) in players.withIndex()) {
            popup.menu.add(0, index, 0, player.appName)
        }
        popup.setOnMenuItemClickListener { item ->
            selectedPlayerPkg = players[item.itemId].packageName
            refreshMusic()
            true
        }
        popup.show()
    }

    private fun startMusicUpdates() {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.addOnActiveSessionsChangedListener(
                sessionsListener,
                android.content.ComponentName(this, MusicListenerService::class.java),
            )
        } catch (_: Exception) {
        }
        musicJob?.cancel()
        musicJob = scope.launch {
            while (isActive) {
                refreshMusic()
                delay(1000) // progreso de la cancion en vivo
            }
        }
    }

    private fun stopMusicUpdates() {
        musicJob?.cancel()
        musicJob = null
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------- busqueda

    private data class RowItem(
        val title: String,
        val sub: String,
        val lat: Double,
        val lon: Double,
        val favorite: Favorite?,
        val deletable: Boolean,
        /** v1.7: solo los resultados de busqueda se pueden guardar
            (estrella); los favoritos y "Sin resultados", no. */
        val savable: Boolean,
    )

    private fun onSearchText(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            showFavorites()
            return
        }
        searchJob = scope.launch {
            delay(400)
            val results = withContext(Dispatchers.IO) {
                try {
                    PhotonApi.search(query, SANTO_DOMINGO.latitude, SANTO_DOMINGO.longitude)
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val rows = results.map {
                RowItem(it.name, it.detail, it.lat, it.lon, null, false, true)
            }
            showRows(
                if (rows.isEmpty()) {
                    listOf(RowItem(getString(R.string.no_results), "", 0.0, 0.0, null, false, false))
                } else rows
            ) { row ->
                if (row.lat == 0.0 && row.lon == 0.0) return@showRows
                hideOverlays()
                hideKeyboard()
                searchInput.clearFocus()
                previewRoutes(LatLng(row.lat, row.lon), row.title)
            }
        }
    }

    private fun showFavorites() {
        scope.launch {
            val favs = FavoritesStore.load(this@MainActivity)
            val rows = favs.map {
                RowItem(it.name, it.address, it.lat, it.lon, it, true, false)
            }
            showRows(rows) { row ->
                hideOverlays()
                previewRoutes(LatLng(row.lat, row.lon), row.title)
            }
        }
    }

    private fun showRows(rows: List<RowItem>, onPick: (RowItem) -> Unit) {
        if (rows.isEmpty()) {
            searchResults.visibility = View.GONE
            return
        }
        searchResults.visibility = View.VISIBLE
        searchResults.adapter = object : BaseAdapter() {
            override fun getCount() = rows.size
            override fun getItem(position: Int) = rows[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView
                    ?: layoutInflater.inflate(R.layout.row_search_result, parent, false)
                val row = rows[position]
                view.findViewById<TextView>(R.id.rowTitle).text = row.title
                view.findViewById<TextView>(R.id.rowSub).text = row.sub
                // Estrella para guardar el lugar buscado (v1.7).
                val save = view.findViewById<ImageView>(R.id.rowSave)
                save.visibility = if (row.savable) View.VISIBLE else View.GONE
                save.setOnClickListener {
                    showCategoryPicker(save) { category ->
                        scope.launch {
                            FavoritesStore.add(
                                this@MainActivity,
                                Favorite(
                                    row.title, row.sub,
                                    row.lat, row.lon, category,
                                ),
                            )
                            Toast.makeText(
                                this@MainActivity, R.string.saved, Toast.LENGTH_SHORT
                            ).show()
                            selectedCategory = category
                            refreshFavoriteChips()
                        }
                    }
                }
                val del = view.findViewById<ImageView>(R.id.rowDelete)
                del.visibility = if (row.deletable) View.VISIBLE else View.GONE
                del.setOnClickListener {
                    val fav = row.favorite ?: return@setOnClickListener
                    scope.launch {
                        FavoritesStore.remove(this@MainActivity, fav)
                        Toast.makeText(this@MainActivity, R.string.removed, Toast.LENGTH_SHORT).show()
                        refreshFavoriteChips()
                        showFavorites()
                    }
                }
                return view
            }
        }
        searchResults.setOnItemClickListener { _, _, position, _ -> onPick(rows[position]) }
    }

    // ------------------------------------------------- favoritos (tira)

    /**
     * Capsula de categorias (v1.6) directamente debajo del buscador:
     * Locales, Restaurantes y Favoritos. Tocar un chip muestra los
     * lugares guardados de esa categoria en la tira de abajo; tocarlo
     * otra vez la esconde.
     */
    private fun onCategoryChip(category: String) {
        selectedCategory = if (selectedCategory == category) null else category
        applyCategorySelection()
        refreshFavoriteChips()
        if (selectedCategory != null) {
            scope.launch {
                val count = FavoritesStore.loadByCategory(this@MainActivity, category).size
                if (count == 0) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(
                            R.string.cat_empty,
                            FavoriteCategory.label(category, this@MainActivity),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun applyCategorySelection() {
        val selected = selectedCategory
        fun paint(view: ImageView, category: String) {
            view.setBackgroundResource(
                if (selected == category) R.drawable.bg_chip_selected
                else R.drawable.bg_chip
            )
        }
        paint(catLocales, FavoriteCategory.LOCALES)
        paint(catRestaurantes, FavoriteCategory.RESTAURANTES)
        paint(catFavoritos, FavoriteCategory.FAVORITOS)
    }

    /**
     * Tira compacta de lugares guardados DEBAJO de la capsula de
     * categorias (v1.3, filtrada por categoria en v1.6). Tocar un chip
     * traza la ruta; mantenerlo presionado lo elimina. Solo visible
     * con una categoria elegida y con lugares en ella.
     */
    private fun refreshFavoriteChips() {
        val category = selectedCategory
        if (category == null) {
            favChips.visibility = View.GONE
            favChipsRow.removeAllViews()
            return
        }
        scope.launch {
            val favs = FavoritesStore.loadByCategory(this@MainActivity, category)
            favChipsRow.removeAllViews()
            if (favs.isEmpty()) {
                favChips.visibility = View.GONE
                return@launch
            }
            favChips.visibility = View.VISIBLE
            val density = resources.displayMetrics.density
            for (fav in favs) {
                val chip = TextView(this@MainActivity).apply {
                    text = fav.name
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                    setBackgroundResource(R.drawable.bg_chip)
                    setPadding(
                        (10 * density).toInt(), (5 * density).toInt(),
                        (10 * density).toInt(), (5 * density).toInt(),
                    )
                }
                chip.setOnClickListener {
                    hideOverlays()
                    hideKeyboard()
                    searchInput.clearFocus()
                    previewRoutes(LatLng(fav.lat, fav.lon), fav.name)
                }
                chip.setOnLongClickListener {
                    scope.launch {
                        FavoritesStore.remove(this@MainActivity, fav)
                        Toast.makeText(
                            this@MainActivity, R.string.removed, Toast.LENGTH_SHORT
                        ).show()
                        refreshFavoriteChips()
                    }
                    true
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.marginEnd = (6 * density).toInt()
                favChipsRow.addView(chip, lp)
            }
        }
    }

    // --------------------------------------- enlace con el telefono (v1.6)

    /** Un destino llego desde la app del telefono: pin + ruta, solo. */
    private fun onPhoneDestination(name: String, lat: Double, lng: Double) {
        if (lat !in 17.4..20.0 || lng !in -72.1..-68.5) {
            Toast.makeText(this, R.string.outside_dr, Toast.LENGTH_LONG).show()
            return
        }
        phoneLastReceived = name
        refreshPhoneStatus()
        hideOverlays()
        hideKeyboard()
        searchInput.clearFocus()
        // v1.7: el destino del telefono tambien pasa por la vista previa.
        previewRoutes(LatLng(lat, lng), name)
        Toast.makeText(this, R.string.route_from_phone, Toast.LENGTH_LONG).show()
    }

    /** IP y estado del enlace, en Ajustes -> Telefono. */
    private fun refreshPhoneStatus() {
        if (!::phoneIp.isInitialized) return
        val ip = PhoneLinkServer.localIp(this)
        val received = phoneLastReceived
        phoneIp.text = when {
            received != null -> getString(R.string.phone_received, received)
            ip != null && PhoneLinkServer.isRunning() ->
                getString(R.string.phone_listening, ip, PhoneLinkServer.TCP_PORT)
            else -> getString(R.string.phone_waiting)
        }
        val btLine = when (BluetoothLinkServer.status()) {
            BluetoothLinkServer.Status.LISTENING ->
                getString(R.string.phone_bt_listening)
            BluetoothLinkServer.Status.OFF -> getString(R.string.phone_bt_off)
            BluetoothLinkServer.Status.UNAVAILABLE ->
                getString(R.string.phone_bt_unavailable)
            BluetoothLinkServer.Status.FAILED -> getString(R.string.phone_bt_failed)
        }
        phoneStatus.text = getString(R.string.phone_hint) + "\n" + btLine
    }

    // --------------------------------------------- diagnostico GPS (v1.3)

    /** Texto en vivo de la calidad de la senal, en Ajustes. */
    private fun updateGpsStatusText() {
        if (!::gpsStatus.isInitialized) return
        if (settingsPanel.visibility != View.VISIBLE) return
        gpsPerm.text = getString(
            if (hasLocationPermission()) R.string.gps_perm_precise
            else R.string.gps_perm_missing
        )
        if (lastFixTimeMs == 0L) {
            gpsStatus.setText(R.string.gps_no_fix)
            return
        }
        val ageSec = ((System.currentTimeMillis() - lastFixTimeMs) / 1000).toInt()
        val acc = if (lastFixAccuracy > 0) "±${lastFixAccuracy.toInt()} m" else "sin precisión"
        gpsStatus.text = getString(
            R.string.gps_fix_format, acc, ageSec,
            lastFixProvider.ifEmpty { "?" },
        )
    }

    private fun startGpsStatusUpdates() {
        gpsJob?.cancel()
        gpsJob = scope.launch {
            while (isActive) {
                updateGpsStatusText()
                delay(2000)
            }
        }
    }

    private fun hideOverlays() {
        searchResults.visibility = View.GONE
        pointCard.visibility = View.GONE
        // Tocar fuera con la previsualizacion abierta la cancela.
        cancelPreview()
        hideSettings()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    // ------------------------------------------------------------- ciclo mapa

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // Al volver de Ajustes del sistema (permiso), arrancar si ya hay permiso.
        if (hasLocationPermission() && !locationStarted) {
            startLocation()
        }
        refreshFavoriteChips()
        startMusicUpdates()
        startGpsStatusUpdates()
        startDeadReckoning()
        // Enlace con el telefono (v1.6): escucha en la red local
        // mientras la app esta abierta.
        PhoneLinkServer.start(phoneLinkListener)
        // Enlace por Bluetooth (v1.0.4): respaldo cuando no hay hotspot.
        BluetoothLinkServer.start(this) { name, lat, lng ->
            runOnUiThread { onPhoneDestination(name, lat, lng) }
        }
        refreshPhoneStatus()
    }

    override fun onPause() {
        super.onPause()
        stopMusicUpdates()
        gpsJob?.cancel()
        gpsJob = null
        deadReckonJob?.cancel()
        deadReckonJob = null
        PhoneLinkServer.stop()
        BluetoothLinkServer.stop()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        PhoneLinkServer.stop()
        BluetoothLinkServer.stop()
        try {
            fusedCallback?.let { fusedClient?.removeLocationUpdates(it) }
        } catch (_: Exception) {
        }
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {
        }
        centerTimeoutJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
