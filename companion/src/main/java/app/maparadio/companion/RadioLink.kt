package app.maparadio.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Enlace con el radio (v1.6): el telefono encuentra el radio en la
 * red del hotspot y le manda el destino.
 *
 * Protocolo (igual que PhoneLinkServer en el radio):
 * - Descubrimiento UDP, puerto 53535: broadcast "MAPARADIO_DISCOVER",
 *   el radio responde "MAPARADIO_HERE|Mapa Radio" al remitente.
 * - Destino TCP, puerto 8080: POST /destination con JSON
 *   {"name": "...", "lat": 18.48, "lng": -69.93}.
 * - GET /ping para comprobar que el radio contesta.
 *
 * Sin librerias nuevas: OkHttp para el HTTP y DatagramSocket para
 * el descubrimiento. Todo en Dispatchers.IO.
 */
object RadioLink {

    const val TCP_PORT = 8080
    const val UDP_PORT = 53535
    private const val DISCOVER_MSG = "MAPARADIO_DISCOVER"
    private const val HERE_PREFIX = "MAPARADIO_HERE"

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * Busca el radio por broadcast en la red local. Devuelve su IP o
     * null si no contesto en [timeoutMs].
     */
    suspend fun discover(timeoutMs: Int = 4000): String? = withContext(Dispatchers.IO) {
        try {
            val socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = timeoutMs
            val msg = DISCOVER_MSG.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(
                msg, msg.size,
                InetAddress.getByName("255.255.255.255"), UDP_PORT
            )
            socket.send(packet)
            val buf = ByteArray(256)
            val reply = DatagramPacket(buf, buf.size)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                try {
                    socket.receive(reply)
                } catch (_: java.net.SocketTimeoutException) {
                    break
                }
                val text = String(reply.data, 0, reply.length, Charsets.UTF_8).trim()
                if (text.startsWith(HERE_PREFIX)) {
                    val ip = reply.address.hostAddress ?: continue
                    socket.close()
                    return@withContext ip
                }
            }
            socket.close()
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Comprueba que el radio contesta en su puerto TCP. */
    suspend fun ping(ip: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("http://$ip:$TCP_PORT/ping").get().build()
            http.newCall(req).execute().use { resp -> resp.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** Manda el destino al radio. True si el radio lo acepto (200). */
    suspend fun sendDestination(ip: String, name: String, lat: Double, lng: Double): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("name", name)
                    put("lat", lat)
                    put("lng", lng)
                }.toString().toRequestBody(JSON)
                val req = Request.Builder()
                    .url("http://$ip:$TCP_PORT/destination")
                    .post(body)
                    .build()
                http.newCall(req).execute().use { resp -> resp.isSuccessful }
            } catch (_: Exception) {
                false
            }
        }

    /** Resultado de mandar una ruta completa (v1.0.3-test). */
    enum class SendRouteResult {
        /** El radio acepto la ruta (200). */
        SENT,
        /** El radio contesto 404: version vieja sin POST /route; el
         *  llamador puede caer al destino solo (/destination). */
        NOT_FOUND,
        /** Sin respuesta (radio apagado, IP vieja, hotspot caido...). */
        UNREACHABLE,
    }

    /** Manda la ruta completa al radio (POST /route, JSON de
     *  PhoneRouteJson). No lanza: devuelve el motivo del fallo. */
    suspend fun sendRoute(ip: String, routeJson: String): SendRouteResult =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("http://$ip:$TCP_PORT/route")
                    .post(routeJson.toRequestBody(JSON))
                    .build()
                http.newCall(req).execute().use { resp ->
                    when {
                        resp.isSuccessful -> SendRouteResult.SENT
                        resp.code == 404 -> SendRouteResult.NOT_FOUND
                        else -> SendRouteResult.UNREACHABLE
                    }
                }
            } catch (_: Exception) {
                SendRouteResult.UNREACHABLE
            }
        }
}
