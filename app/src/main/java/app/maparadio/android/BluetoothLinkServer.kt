package app.maparadio.android

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enlace con el telefono por Bluetooth (v1.0.4): segundo camino para
 * mandar destinos, cuando no hay hotspot WiFi. El hotspot sigue siendo
 * el principal; el Bluetooth es el respaldo.
 *
 * Protocolo: RFCOMM clasico (perfil SPP), mismo UUID en la app del
 * telefono (BluetoothLink). El telefono se conecta y manda UNA linea
 * JSON {"name": "...", "lat": 18.48, "lng": -69.93}; el radio contesta
 * "OK" si el destino vale (dentro de RD) o "BAD" si no, y lo maneja
 * EXACTAMENTE igual que el POST /destination de PhoneLinkServer
 * (misma validacion en PhoneLinkServer.parseDestination, mismo pin,
 * misma ruta, mismo Toast "Ruta recibida del teléfono").
 *
 * Sin librerias nuevas, hilos propios, nunca el principal. Se arranca
 * en onResume y se para en onPause, igual que PhoneLinkServer.
 * En API 27 BLUETOOTH / BLUETOOTH_ADMIN son permisos de instalacion:
 * no hace falta pedir nada en tiempo de ejecucion en el radio.
 *
 * Si el Bluetooth esta apagado o el stack del radio no deja abrir el
 * servidor, NO se cierra la app: queda registrado (CrashLog.logEvent)
 * y el estado se ve en Ajustes -> Telefono; el camino WiFi sigue
 * funcionando.
 */
object BluetoothLinkServer {

    /** UUID SPP estandar: el mismo en el radio y en el telefono. */
    val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    const val SERVICE_NAME = "MapaRadio"

    enum class Status { LISTENING, OFF, UNAVAILABLE, FAILED }

    @Volatile
    private var status: Status = Status.OFF
    private val running = AtomicBoolean(false)
    private var serverSocket: BluetoothServerSocket? = null
    private var appContext: Context? = null
    private var onDestination: ((String, Double, Double) -> Unit)? = null

    fun status(): Status = status

    fun start(context: Context, onDestination: (String, Double, Double) -> Unit) {
        if (running.get()) return
        appContext = context.applicationContext
        this.onDestination = onDestination
        running.set(true)
        Thread({ runAccept() }, "btlink-accept").apply { isDaemon = true }.start()
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) { }
        serverSocket = null
        onDestination = null
    }

    private fun runAccept() {
        val ctx = appContext ?: return
        val adapter: BluetoothAdapter? = try {
            (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        } catch (_: Exception) {
            null
        }
        if (adapter == null) {
            status = Status.UNAVAILABLE
            CrashLog.logEvent(ctx, "BT: sin adaptador Bluetooth en este radio")
            return
        }
        if (!adapter.isEnabled) {
            status = Status.OFF
            return
        }
        val server: BluetoothServerSocket = try {
            adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
        } catch (e: Exception) {
            status = Status.FAILED
            CrashLog.logEvent(
                ctx,
                "BT: no se pudo abrir el servidor RFCOMM: ${e.javaClass.simpleName}"
            )
            return
        }
        serverSocket = server
        status = Status.LISTENING
        while (running.get()) {
            val socket: BluetoothSocket = try {
                server.accept()
            } catch (_: Exception) {
                break
            }
            Thread({ handleClient(socket) }, "btlink-client").apply {
                isDaemon = true
            }.start()
        }
        try { server.close() } catch (_: Exception) { }
    }

    private fun handleClient(socket: BluetoothSocket) {
        try {
            val reader = socket.inputStream.bufferedReader(Charsets.UTF_8)
            val text = reader.readLine()?.trim()
            val dest = text?.let { PhoneLinkServer.parseDestination(it) }
            val out = socket.outputStream
            if (dest != null) {
                out.write("OK\n".toByteArray(Charsets.UTF_8))
                out.flush()
                onDestination?.invoke(dest.name, dest.lat, dest.lng)
            } else if (text != null) {
                out.write("BAD\n".toByteArray(Charsets.UTF_8))
                out.flush()
                appContext?.let {
                    CrashLog.logEvent(it, "BT: destino ignorado (fuera de RD o JSON invalido)")
                }
            }
        } catch (_: Exception) {
            // Conexion mala: se ignora, el servidor sigue.
        } finally {
            try { socket.close() } catch (_: IOException) { }
        }
    }
}
