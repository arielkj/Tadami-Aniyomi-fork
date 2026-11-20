package app.maparadio.android

import android.content.Context
import android.net.wifi.WifiManager
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enlace con el telefono (v1.6): el radio escucha en la red local (el
 * hotspot del telefono) para que la app "Mapa Radio Movil" le mande
 * destinos. Sin internet, sin cuentas, sin librerias nuevas: un
 * ServerSocket plano y un socket UDP para descubrirse.
 *
 * Protocolo (documentado; la app del telefono usa exactamente esto):
 * - Descubrimiento UDP, puerto 53535: el telefono manda por broadcast
 *   "MAPARADIO_DISCOVER" y el radio responde al remitente
 *   "MAPARADIO_HERE|Mapa Radio".
 * - Destino TCP, puerto 8080: POST /destination con cuerpo JSON
 *   {"name": "...", "lat": 18.48, "lng": -69.93} -> 200 {"ok": true}.
 *   GET /ping -> 200 {"ok": true, "name": "Mapa Radio"}.
 * - Ruta completa TCP, puerto 8080 (v1.0.8): POST /route con el
 *   cuerpo JSON de PhoneRouteJson ({"type":"route","name":...,
 *   "points":[[lat,lng],...],"steps":[...],...}) -> 200 {"ok": true}
 *   si la ruta vale, 400 si no. El telefono calcula la ruta con
 *   OSRM y el radio solo la dibuja y la guia (su GraphHopper
 *   offline tarda minutos en cargar). El destino solo sigue
 *   funcionando igual que antes.
 *
 * Todo corre en hilos propios, nunca en el hilo principal. El servidor
 * solo escucha en la red local (hotspot); no se expone a internet.
 * Se arranca en onResume y se para en onPause: solo escucha mientras
 * la app esta abierta.
 */
object PhoneLinkServer {

    const val TCP_PORT = 8080
    const val UDP_PORT = 53535
    const val DISCOVER_MSG = "MAPARADIO_DISCOVER"
    const val HERE_MSG = "MAPARADIO_HERE|Mapa Radio"

    interface Listener {
        /** Un destino valido llego del telefono (hilo de red, no el principal). */
        fun onDestination(name: String, lat: Double, lng: Double)
        /** Una ruta completa valida llego del telefono (v1.0.8). */
        fun onRoute(data: RouteData)
        /** El servidor no pudo arrancar (puerto ocupado, sin WiFi...). */
        fun onStartFailed()
    }

    private val running = AtomicBoolean(false)
    private var tcpSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    private var listener: Listener? = null

    fun isRunning(): Boolean = running.get()

    fun start(listener: Listener) {
        if (running.get()) return
        this.listener = listener
        running.set(true)
        Thread({ runTcp() }, "phonelink-tcp").apply { isDaemon = true }.start()
        Thread({ runUdp() }, "phonelink-udp").apply { isDaemon = true }.start()
    }

    fun stop() {
        running.set(false)
        try { tcpSocket?.close() } catch (_: Exception) { }
        try { udpSocket?.close() } catch (_: Exception) { }
        tcpSocket = null
        udpSocket = null
        listener = null
    }

    /** IP actual del radio en la red WiFi (hotspot del telefono). */
    fun localIp(context: Context): String? {
        return try {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifi.connectionInfo?.ipAddress ?: 0
            if (ip == 0) return null
            // ipAddress viene en little-endian.
            "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
        } catch (_: Exception) {
            null
        }
    }

    private fun runTcp() {
        try {
            val server = ServerSocket(TCP_PORT)
            tcpSocket = server
            while (running.get()) {
                val client: Socket = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                Thread({ handleTcp(client) }, "phonelink-client").apply {
                    isDaemon = true
                }.start()
            }
        } catch (_: Exception) {
            if (running.get()) {
                running.set(false)
                listener?.onStartFailed()
            }
        }
    }

    private fun handleTcp(client: Socket) {
        try {
            client.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]
            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                val header = line!!
                if (header.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }
            val bodyChars = CharArray(contentLength.coerceAtLeast(0))
            var read = 0
            while (read < bodyChars.size) {
                val n = reader.read(bodyChars, read, bodyChars.size - read)
                if (n <= 0) break
                read += n
            }
            val body = String(bodyChars, 0, read)
            val out = client.getOutputStream()
            when {
                method == "GET" && path == "/ping" -> {
                    sendJson(out, 200, JSONObject().put("ok", true).put("name", "Mapa Radio"))
                }
                method == "POST" && path == "/destination" -> {
                    val ok = handleDestination(body)
                    val code = if (ok) 200 else 400
                    sendJson(out, code, JSONObject().put("ok", ok))
                }
                method == "POST" && path == "/route" -> {
                    val ok = handleRoute(body)
                    val code = if (ok) 200 else 400
                    sendJson(out, code, JSONObject().put("ok", ok))
                }
                else -> sendJson(out, 404, JSONObject().put("ok", false))
            }
            out.flush()
        } catch (_: Exception) {
            // Conexion mala: se ignora, el servidor sigue.
        } finally {
            try { client.close() } catch (_: Exception) { }
        }
    }

    private fun sendJson(out: java.io.OutputStream, code: Int, json: JSONObject) {
        val body = json.toString().toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code OK\r\nContent-Type: application/json\r\n" +
            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(body)
    }

    /** Valida el destino (numeros y dentro de RD). Null si no vale.
     *  Lo usa tambien BluetoothLinkServer: un destino vale igual por
     *  WiFi que por Bluetooth. */
    data class Destination(val name: String, val lat: Double, val lng: Double)

    fun parseDestination(body: String): Destination? {
        return try {
            val json = JSONObject(body)
            val name = json.optString("name").takeIf { it.isNotEmpty() } ?: "Destino"
            val lat = json.optDouble("lat", Double.NaN)
            val lng = json.optDouble("lng", Double.NaN)
            if (lat.isNaN() || lng.isNaN()) return null
            // Solo Republica Dominicana: fuera del cuadro se ignora.
            if (lat !in 17.4..20.0 || lng !in -72.1..-68.5) return null
            Destination(name, lat, lng)
        } catch (_: Exception) {
            null
        }
    }

    /** Valida el destino (numeros y dentro de RD) y lo pasa al listener. */
    private fun handleDestination(body: String): Boolean {
        val dest = parseDestination(body) ?: return false
        listener?.onDestination(dest.name, dest.lat, dest.lng)
        return true
    }

    /** Valida la ruta completa (PhoneRouteJson) y la pasa al listener.
     *  El destino final (ultimo punto) se valida con el cuadro
     *  estricto de RD, igual que el destino solo. */
    private fun handleRoute(body: String): Boolean {
        val data = PhoneRouteJson.decode(body) ?: return false
        val last = data.points.lastOrNull() ?: return false
        if (last.first !in 17.4..20.0 || last.second !in -72.1..-68.5) return false
        listener?.onRoute(data)
        return true
    }

    private fun runUdp() {
        try {
            val socket = DatagramSocket(UDP_PORT)
            socket.broadcast = true
            udpSocket = socket
            val buf = ByteArray(256)
            while (running.get()) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (_: Exception) {
                    break
                }
                val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                if (msg == DISCOVER_MSG) {
                    val reply = HERE_MSG.toByteArray(Charsets.UTF_8)
                    val answer = DatagramPacket(
                        reply, reply.size, packet.address, packet.port
                    )
                    try { socket.send(answer) } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) {
            // Sin red local: el descubrimiento no funciona, pero el
            // servidor TCP puede seguir sirviendo con la IP manual.
        }
    }
}
