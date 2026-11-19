package app.maparadio.companion

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mapa Radio Movil (v1.0.1-test): la app del telefono que manda destinos
 * al mapa del radio por el hotspot WiFi.
 *
 * Flujo: el usuario busca en Google Maps -> Compartir -> "Mapa Radio
 * Movil" (las coordenadas llegan solas, sin copiar nada), o busca aqui
 * mismo con Photon (solo RD). Luego toca "Enviar al radio" y el radio
 * muestra el pin y traza la ruta solo.
 *
 * Sin mapa, sin librerias pesadas: solo OkHttp + corrutinas. El radio
 * se encuentra solo por broadcast UDP; si no, se escribe su IP a mano
 * (la muestra en Ajustes -> Telefono).
 */
class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var searchJob: Job? = null

    private lateinit var statusText: TextView
    private lateinit var destName: TextView
    private lateinit var destCoords: TextView
    private lateinit var searchInput: EditText
    private lateinit var searchResults: ListView
    private lateinit var ipInput: EditText
    private lateinit var btStatus: TextView

    // Bluetooth (v1.0.2-test): accion pendiente de conceder el permiso.
    private var pendingBtAction: (() -> Unit)? = null

    private var radioIp: String? = null
    private var destination: ShareParser.SharedPlace? = null

    private companion object {
        private const val REQ_BT_CONNECT = 41
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        destName = findViewById(R.id.destName)
        destCoords = findViewById(R.id.destCoords)
        searchInput = findViewById(R.id.searchInput)
        searchResults = findViewById(R.id.searchResults)
        ipInput = findViewById(R.id.ipInput)

        findViewById<TextView>(R.id.btnDiscover).setOnClickListener { discoverRadio() }
        findViewById<TextView>(R.id.btnSend).setOnClickListener { sendToRadio() }
        btStatus = findViewById(R.id.btStatus)
        findViewById<TextView>(R.id.btnBluetooth).setOnClickListener { pickBluetoothRadio() }
        refreshBtStatus()
        ipInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                connectManualIp()
                true
            } else false
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

        handleIntent(intent)
        discoverRadio()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Compartido desde Google Maps (SEND) o enlace abierto (VIEW). */
    private fun handleIntent(intent: Intent) {
        val shared: String? = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        if (shared.isNullOrBlank()) return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    ShareParser.parse(shared)
                } catch (_: Exception) {
                    ShareParser.ParseResult.NotFound(shared.take(300))
                }
            }
            when (result) {
                is ShareParser.ParseResult.Found -> setDestination(result.place)
                is ShareParser.ParseResult.NotFound -> {
                    // Mostrar lo recibido en la tarjeta de Destino para
                    // diagnosticar que mando Google Maps.
                    destination = null
                    destName.setText(R.string.share_unreadable_title)
                    destCoords.text = if (result.received.isBlank()) {
                        getString(R.string.share_unreadable)
                    } else {
                        getString(R.string.share_received, result.received)
                    }
                    Toast.makeText(
                        this@MainActivity, R.string.share_unreadable, Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setDestination(place: ShareParser.SharedPlace) {
        destination = place
        destName.text = place.name
        destCoords.text = "%.5f, %.5f".format(place.lat, place.lng)
    }

    /** Busca el radio por broadcast en la red del hotspot. */
    private fun discoverRadio() {
        statusText.setText(R.string.searching_radio)
        scope.launch {
            val ip = RadioLink.discover()
            if (ip != null && RadioLink.ping(ip)) {
                radioIp = ip
                statusText.text = getString(R.string.radio_found, ip)
            } else {
                radioIp = null
                statusText.setText(R.string.radio_not_found)
            }
        }
    }

    /** IP escrita a mano (la del radio en Ajustes -> Telefono). */
    private fun connectManualIp() {
        val ip = ipInput.text.toString().trim()
        if (ip.isEmpty()) return
        hideKeyboard()
        statusText.setText(R.string.searching_radio)
        scope.launch {
            if (RadioLink.ping(ip)) {
                radioIp = ip
                statusText.text = getString(R.string.radio_manual_ok, ip)
            } else {
                Toast.makeText(
                    this@MainActivity, R.string.radio_manual_fail, Toast.LENGTH_LONG
                ).show()
                statusText.setText(R.string.radio_not_found)
            }
        }
    }

    /** Elige el radio de la lista de dispositivos emparejados (una vez;
     *  se recuerda). Sin escanear: evita el permiso BLUETOOTH_SCAN. */
    private fun pickBluetoothRadio() {
        if (!BluetoothLink.hasPermission(this)) {
            pendingBtAction = { pickBluetoothRadio() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    REQ_BT_CONNECT
                )
            }
            return
        }
        val devices = BluetoothLink.pairedDevices(this)
        if (devices.isEmpty()) {
            Toast.makeText(this, R.string.bt_no_devices, Toast.LENGTH_LONG).show()
            return
        }
        val names = devices.map { it.name ?: it.address }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.bt_pick_title)
            .setItems(names) { _, which ->
                val device: BluetoothDevice = devices[which]
                BluetoothLink.rememberDevice(this, device)
                refreshBtStatus()
                Toast.makeText(
                    this,
                    getString(R.string.bt_chosen, device.name ?: device.address),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshBtStatus() {
        val name = BluetoothLink.rememberedName(this)
        btStatus.text = if (name != null) {
            getString(R.string.bt_chosen, name)
        } else {
            getString(R.string.bt_none)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BT_CONNECT) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                pendingBtAction?.invoke()
            } else {
                Toast.makeText(
                    this, R.string.bt_permission_needed, Toast.LENGTH_LONG
                ).show()
            }
            pendingBtAction = null
        }
    }

    private fun sendToRadio() {
        val dest = destination
        if (dest == null) {
            Toast.makeText(this, R.string.no_dest_yet, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            // 1) WiFi primero: el camino principal (hotspot).
            val ip = radioIp
            if (ip != null) {
                if (RadioLink.sendDestination(ip, dest.name, dest.lat, dest.lng)) {
                    Toast.makeText(
                        this@MainActivity, R.string.sent_wifi, Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
            }
            // 2) Bluetooth de respaldo, cuando no hay hotspot.
            if (!BluetoothLink.hasPermission(this@MainActivity)) {
                pendingBtAction = { sendToRadio() }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                        REQ_BT_CONNECT
                    )
                }
                return@launch
            }
            val device = BluetoothLink.rememberedDevice(this@MainActivity)
            if (device == null) {
                Toast.makeText(
                    this@MainActivity, R.string.no_radio_bt, Toast.LENGTH_LONG
                ).show()
                pickBluetoothRadio()
                return@launch
            }
            val ok = BluetoothLink.sendDestination(
                device, dest.name, dest.lat, dest.lng
            )
            Toast.makeText(
                this@MainActivity,
                if (ok) R.string.sent_bt else R.string.send_failed,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun onSearchText(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            searchResults.visibility = View.GONE
            return
        }
        searchJob = scope.launch {
            delay(400)
            val results = withContext(Dispatchers.IO) {
                try {
                    PhotonSearch.search(query)
                } catch (_: Exception) {
                    emptyList()
                }
            }
            if (results.isEmpty()) {
                searchResults.visibility = View.VISIBLE
                searchResults.adapter = SimpleRows(
                    listOf(getString(R.string.no_results) to "")
                ) { }
                return@launch
            }
            searchResults.visibility = View.VISIBLE
            searchResults.adapter = SimpleRows(
                results.map { it.name to it.detail }
            ) { position ->
                val r = results[position]
                setDestination(ShareParser.SharedPlace(r.name, r.lat, r.lon))
                searchResults.visibility = View.GONE
                hideKeyboard()
                searchInput.clearFocus()
            }
        }
    }

    private inner class SimpleRows(
        private val rows: List<Pair<String, String>>,
        private val onPick: (Int) -> Unit,
    ) : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: layoutInflater.inflate(
                    android.R.layout.simple_list_item_2, parent, false
                )
            view.findViewById<TextView>(android.R.id.text1).text = rows[position].first
            view.findViewById<TextView>(android.R.id.text2).text = rows[position].second
            view.setOnClickListener { onPick(position) }
            return view
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }
}
