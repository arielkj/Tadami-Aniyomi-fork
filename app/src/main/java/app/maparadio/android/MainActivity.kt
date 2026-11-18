package app.maparadio.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mapbox.mapboxsdk.geometry.LatLng
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mapa Radio v1 (prueba en el telefono).
 *
 * Diseno del mockup v2: mapa oscuro estilo Tesla, ruta azul, carro blanco
 * centrado, velocidad arriba a la izquierda con la busqueda ENCIMA de ella,
 * panel de guia a un lado que crece cerca del giro, controles a la derecha
 * (brujula, dia/noche, zoom +/-), barra inferior con lugares guardados.
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
    }

    private lateinit var mapView: MapView
    private var map: MapboxMap? = null
    private var style: Style? = null

    private lateinit var searchInput: EditText
    private lateinit var searchResults: ListView
    private lateinit var speedValue: TextView
    private lateinit var guidanceCard: View
    private lateinit var maneuverArrow: ImageView
    private lateinit var maneuverTitle: TextView
    private lateinit var maneuverStreet: TextView
    private lateinit var maneuverEta: TextView
    private lateinit var statusText: TextView
    private lateinit var btnCancelRoute: TextView
    private lateinit var pointCard: View
    private lateinit var pointTitle: TextView
    private lateinit var pointSub: TextView
    private lateinit var btnDayNight: ImageButton

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var searchJob: Job? = null

    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var lastCarPos: LatLng? = null
    private var lastBearing = 0.0
    private var lastSpeedMps = 0.0

    private var followMode = true
    private var headingUp = true
    private var isNight = true
    private var simplified = false
    private var manualZoomUntil = 0L
    private var lastZoomBand = -1
    private var guidanceExpanded = false

    private var route: Route? = null
    private var routeIndex = 0
    private var destination: LatLng? = null
    private var destinationName: String? = null
    private var offRouteCount = 0

    private var routeSource: GeoJsonSource? = null
    private var carSource: GeoJsonSource? = null
    private var carLayer: SymbolLayer? = null
    private var destSource: GeoJsonSource? = null

    private var pendingPoint: LatLng? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onNewFix(location)
        }
    }

    // ------------------------------------------------------------------ ciclo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        searchInput = findViewById(R.id.searchInput)
        searchResults = findViewById(R.id.searchResults)
        speedValue = findViewById(R.id.speedValue)
        guidanceCard = findViewById(R.id.guidanceCard)
        maneuverArrow = findViewById(R.id.maneuverArrow)
        maneuverTitle = findViewById(R.id.maneuverTitle)
        maneuverStreet = findViewById(R.id.maneuverStreet)
        maneuverEta = findViewById(R.id.maneuverEta)
        statusText = findViewById(R.id.statusText)
        btnCancelRoute = findViewById(R.id.btnCancelRoute)
        pointCard = findViewById(R.id.pointCard)
        pointTitle = findViewById(R.id.pointTitle)
        pointSub = findViewById(R.id.pointSub)
        btnDayNight = findViewById(R.id.btnDayNight)

        findViewById<ImageButton>(R.id.btnCompass).setOnClickListener { onCompass() }
        btnDayNight.setOnClickListener { onDayNight() }
        findViewById<ImageButton>(R.id.btnZoomIn).setOnClickListener { onZoomButton(1.0) }
        findViewById<ImageButton>(R.id.btnZoomOut).setOnClickListener { onZoomButton(-1.0) }
        findViewById<TextView>(R.id.btnSaved).setOnClickListener { showFavorites() }
        btnCancelRoute.setOnClickListener { cancelRoute() }
        findViewById<TextView>(R.id.btnGo).setOnClickListener { onGoToPoint() }
        findViewById<TextView>(R.id.btnSavePoint).setOnClickListener { onSavePoint() }

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
            mapLibreMap.addOnMapClickListener { hideOverlays(); true }
            mapLibreMap.addOnMapLongClickListener { latLng -> onMapLongPress(latLng); true }
            mapLibreMap.addOnCameraMoveStartedListener { reason ->
                if (reason == MapboxMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    followMode = false
                }
            }
            loadStyle(if (isNight) STYLE_DARK else STYLE_LIGHT)
            mapLibreMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    com.mapbox.mapboxsdk.camera.CameraPosition.Builder()
                        .target(SANTO_DOMINGO).zoom(15.0).tilt(45.0).bearing(0.0).build()
                ),
                1,
            )
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        ensureLocationPermission()
    }

    private fun loadStyle(url: String) {
        map?.setStyle(Style.Builder().fromUri(url)) { loaded ->
            style = loaded
            setupLayers(loaded)
            redrawRoute()
            updateCarMarker()
            setSimplified(simplified)
        }
    }

    private fun setupLayers(style: Style) {
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
        val w = 110
        val h = 180
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
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

    // ------------------------------------------------------------- ubicacion

    private fun ensureLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED) {
            startLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
                REQ_LOCATION,
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startLocation()
        } else {
            Toast.makeText(this, R.string.location_denied, Toast.LENGTH_LONG).show()
        }
    }

    private fun startLocation() {
        val lm = locationManager ?: return
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) return
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper()
            )
            lm.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 2000L, 0f, locationListener, Looper.getMainLooper()
            )
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onNewFix(it) }
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { onNewFix(it) }
        } catch (_: SecurityException) {
            // Sin permiso: el mapa sigue usable centrado en Santo Domingo.
        } catch (_: IllegalArgumentException) {
            // Proveedor no disponible en este aparato.
        }
    }

    private fun onNewFix(location: Location) {
        if (location.accuracy > 60f) return // GPS muy impreciso: ignorar.
        val pos = LatLng(location.latitude, location.longitude)
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
            val snap = RouteEngine.snapToRoute(r, pos, routeIndex)
            if (snap.distance > OFF_ROUTE_METERS) {
                offRouteCount++
                if (offRouteCount >= 3) {
                    offRouteCount = 0
                    statusText.setText(R.string.recalculating)
                    routeTo(destination!!, destinationName, fromRaw = pos)
                    return
                }
            } else {
                offRouteCount = 0
                routeIndex = snap.index
                lastCarPos = snap.snapped
                lastBearing = snap.bearing
                updateCarMarker()
                updateGuidance(snap.progressMeters)
                if (snap.progressMeters > r.totalDistance - 20) {
                    // Llegada.
                    Toast.makeText(this, R.string.arrived, Toast.LENGTH_LONG).show()
                    cancelRoute()
                    return
                }
            }
            if (followMode) applyAutoZoom(snap.progressMeters)
            if (followMode) moveCamera(lastCarPos!!, lastBearing)
        } else {
            if (lastCarPos != null) {
                lastBearing = RouteEngine.bearingBetween(lastCarPos!!, pos)
            }
            lastCarPos = pos
            updateCarMarker()
            if (followMode) {
                map?.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        com.mapbox.mapboxsdk.camera.CameraPosition.Builder()
                            .target(pos).zoom(15.0).tilt(45.0)
                            .bearing(if (headingUp) lastBearing else 0.0).build()
                    ),
                    700,
                )
            }
        }
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
        maneuverTitle.textSize = if (expanded) 24f else 18f
        maneuverStreet.textSize = if (expanded) 17f else 15f
        val arrowLp = maneuverArrow.layoutParams
        val size = (if (expanded) 56 else 40) * resources.displayMetrics.density
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
            ?: lastCarPos
            ?: lastLocation?.let { LatLng(it.latitude, it.longitude) }
            ?: SANTO_DOMINGO
        destination = dest
        destinationName = name
        statusText.text = getString(R.string.route_to, name ?: "")
        scope.launch {
            val newRoute = withContext(Dispatchers.IO) {
                try {
                    RouteEngine.fetchRoute(origin, dest)
                } catch (_: Exception) {
                    null
                }
            }
            if (newRoute == null) {
                Toast.makeText(this@MainActivity, R.string.no_route, Toast.LENGTH_LONG).show()
                statusText.setText(R.string.offline_status)
                return@launch
            }
            route = newRoute
            routeIndex = 0
            offRouteCount = 0
            lastZoomBand = -1
            followMode = true
            btnCancelRoute.visibility = View.VISIBLE
            guidanceCard.visibility = View.VISIBLE
            destSource?.setGeoJson(
                Feature.fromGeometry(Point.fromLngLat(dest.longitude, dest.latitude))
            )
            redrawRoute()
            statusText.text = getString(R.string.route_to, name ?: "")
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
        route = null
        destination = null
        destinationName = null
        routeIndex = 0
        btnCancelRoute.visibility = View.GONE
        guidanceCard.visibility = View.GONE
        setSimplified(false)
        destSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        redrawRoute()
        statusText.setText(R.string.offline_status)
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
        isNight = !isNight
        btnDayNight.setImageResource(if (isNight) R.drawable.ic_day else R.drawable.ic_night)
        loadStyle(if (isNight) STYLE_DARK else STYLE_LIGHT)
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
        pointCard.visibility = View.GONE
        hideKeyboard()
        routeTo(p, pointTitle.text.toString())
    }

    private fun onSavePoint() {
        val p = pendingPoint ?: return
        val name = pointTitle.text.toString()
        scope.launch {
            FavoritesStore.add(
                this@MainActivity,
                Favorite(name, pointSub.text.toString(), p.latitude, p.longitude),
            )
            Toast.makeText(this@MainActivity, R.string.saved, Toast.LENGTH_SHORT).show()
        }
        pointCard.visibility = View.GONE
    }

    // ------------------------------------------------------------- busqueda

    private data class RowItem(
        val title: String,
        val sub: String,
        val lat: Double,
        val lon: Double,
        val favorite: Favorite?,
        val deletable: Boolean,
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
                RowItem(it.name, it.detail, it.lat, it.lon, null, false)
            }
            showRows(
                if (rows.isEmpty()) {
                    listOf(RowItem(getString(R.string.no_results), "", 0.0, 0.0, null, false))
                } else rows
            ) { row ->
                if (row.lat == 0.0 && row.lon == 0.0) return@showRows
                hideOverlays()
                hideKeyboard()
                searchInput.clearFocus()
                routeTo(LatLng(row.lat, row.lon), row.title)
            }
        }
    }

    private fun showFavorites() {
        scope.launch {
            val favs = FavoritesStore.load(this@MainActivity)
            val rows = favs.map {
                RowItem(it.name, it.address, it.lat, it.lon, it, true)
            }
            showRows(rows) { row ->
                hideOverlays()
                routeTo(LatLng(row.lat, row.lon), row.title)
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
                val del = view.findViewById<ImageView>(R.id.rowDelete)
                del.visibility = if (row.deletable) View.VISIBLE else View.GONE
                del.setOnClickListener {
                    val fav = row.favorite ?: return@setOnClickListener
                    scope.launch {
                        FavoritesStore.remove(this@MainActivity, fav)
                        Toast.makeText(this@MainActivity, R.string.removed, Toast.LENGTH_SHORT).show()
                        showFavorites()
                    }
                }
                return view
            }
        }
        searchResults.setOnItemClickListener { _, _, position, _ -> onPick(rows[position]) }
    }

    private fun hideOverlays() {
        searchResults.visibility = View.GONE
        pointCard.visibility = View.GONE
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
    }

    override fun onPause() {
        super.onPause()
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
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {
        }
        scope.coroutineContext[Job]?.cancel()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
