package app.maparadio.companion

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * Enlace con el radio por Bluetooth (v1.0.2-test): segundo camino
 * para mandar destinos, cuando no hay hotspot WiFi. El hotspot sigue
 * siendo el principal (RadioLink); el Bluetooth es el respaldo.
 *
 * Protocolo: RFCOMM clasico (perfil SPP), mismo UUID que en el radio
 * (BluetoothLinkServer). Se conecta al radio emparejado, manda UNA
 * linea JSON {"name": "...", "lat": 18.48, "lng": -69.93} y espera la
 * respuesta del radio: "OK" (destino aceptado) o "BAD" (fuera de RD
 * o JSON invalido), igual que el 200/400 del POST /destination.
 *
 * Sin librerias nuevas, todo en Dispatchers.IO. El radio se elige UNA
 * vez de la lista de dispositivos emparejados y se recuerda
 * (SharedPreferences); no se escanea (evita el permiso
 * BLUETOOTH_SCAN). En Android 12+ hace falta BLUETOOTH_CONNECT en
 * tiempo de ejecucion para ver los emparejados y conectar.
 */
object BluetoothLink {

    /** UUID SPP estandar: identico al del radio. */
    val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private const val PREFS = "mapa_radio_movil"
    private const val KEY_BT_ADDRESS = "bt_device_address"
    private const val KEY_BT_NAME = "bt_device_name"

    fun adapter(context: Context): BluetoothAdapter? = try {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    } catch (_: Exception) {
        null
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Dispositivos ya emparejados, ordenados por nombre. */
    fun pairedDevices(context: Context): List<BluetoothDevice> {
        return try {
            val ad = adapter(context) ?: return emptyList()
            ad.bondedDevices.sortedBy { it.name ?: it.address }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun rememberedDevice(context: Context): BluetoothDevice? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val address = prefs.getString(KEY_BT_ADDRESS, null) ?: return null
        return try {
            adapter(context)?.getRemoteDevice(address)
        } catch (_: Exception) {
            null
        }
    }

    fun rememberedName(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BT_NAME, null)
    }

    fun rememberDevice(context: Context, device: BluetoothDevice) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_BT_ADDRESS, device.address)
            .putString(KEY_BT_NAME, device.name ?: device.address)
            .apply()
    }

    /** Manda el destino por RFCOMM. True solo si el radio contesto "OK". */
    suspend fun sendDestination(
        device: BluetoothDevice, name: String, lat: Double, lng: Double
    ): Boolean = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
            socket.connect()
            val json = JSONObject().apply {
                put("name", name)
                put("lat", lat)
                put("lng", lng)
            }.toString()
            val out = socket.outputStream
            out.write((json + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
            // La respuesta ("OK"/"BAD") llega por el mismo socket.
            // Se espera hasta 5 s sin bloquear para siempre.
            val input = socket.inputStream
            val deadline = System.currentTimeMillis() + 5000
            while (input.available() == 0 && System.currentTimeMillis() < deadline) {
                if (!socket.isConnected) return@withContext false
                Thread.sleep(100)
            }
            if (input.available() == 0) return@withContext false
            val reply = input.bufferedReader(Charsets.UTF_8).readLine()?.trim()
            reply == "OK"
        } catch (_: Exception) {
            false
        } finally {
            try { socket?.close() } catch (_: Exception) { }
        }
    }
}
