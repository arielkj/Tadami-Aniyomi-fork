package eu.kanade.tachiyomi.data.sync.direct

import eu.kanade.domain.sync.DeviceSyncPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * v7.9.36 — Tiny local HTTP server for direct device-to-device sync.
 *
 * One device hosts it (port 27801 by default), the other connects as a client.
 * Nothing leaves the local network. Every request must carry the pairing code
 * in the `X-Sync-Code` header (or in the JSON body for /sync/pair).
 *
 * Endpoints:
 * - GET  /sync/info     → [DirectSyncInfo] (no auth, discovery only)
 * - POST /sync/pair    → [DirectSyncPairRequest] → [DirectSyncPairResponse]
 * - POST /sync/exchange → [DirectSyncPayload] → merged [DirectSyncPayload]
 */
class DirectSyncServer(
    private val prefs: DeviceSyncPreferences,
    private val json: Json,
    private val deviceId: String,
    private val exchangeHandler: suspend (DirectSyncPayload) -> DirectSyncPayload,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    @Volatile
    var port: Int = prefs.serverPort().get()
        private set

    val isRunning: Boolean
        get() = serverSocket?.isClosed == false

    fun start() {
        if (isRunning) return
        val socket = try {
            ServerSocket(port)
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "DirectSync: port $port busy, using ephemeral port" }
            ServerSocket(0)
        }
        serverSocket = socket
        port = socket.localPort
        val thread = Thread(
            {
                while (!socket.isClosed) {
                    try {
                        val client = socket.accept()
                        scope.launch { handleConnection(client) }
                    } catch (e: Exception) {
                        if (!socket.isClosed) logcat(LogPriority.WARN, e) { "DirectSync: accept failed" }
                    }
                }
            },
            "DirectSyncServer",
        )
        thread.isDaemon = true
        thread.start()
        acceptThread = thread
        logcat { "DirectSync: server listening on port $port" }
    }

    fun stop() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        acceptThread = null
    }

    /** First non-loopback IPv4 site-local address (WiFi), or null if none. */
    fun localAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
                .firstOrNull()
                ?.hostAddress
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "DirectSync: cannot determine local address" }
            null
        }
    }

    private fun handleConnection(socket: Socket) {
        try {
            socket.soTimeout = 120_000
            val input = BufferedInputStream(socket.getInputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                writeResponse(socket, 400, """{"ok":false,"error":"bad request"}""")
                return
            }
            val method = parts[0]
            val path = parts[1].substringBefore("?")
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val bodyBytes = ByteArrayOutputStream()
            var remaining = contentLength
            val buffer = ByteArray(8192)
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read < 0) break
                bodyBytes.write(buffer, 0, read)
                remaining -= read
            }
            val body = String(bodyBytes.toByteArray(), Charsets.UTF_8)
            val codeHeader = headers["x-sync-code"].orEmpty()

            when {
                method == "GET" && path == "/sync/info" -> {
                    val info = DirectSyncInfo(deviceId = deviceId, deviceName = prefs.deviceName().get())
                    writeResponse(socket, 200, json.encodeToString(info))
                }
                method == "POST" && path == "/sync/pair" -> {
                    val request = runCatching { json.decodeFromString<DirectSyncPairRequest>(body) }.getOrNull()
                    val expected = prefs.pairCode().get()
                    if (request == null || request.code != expected || expected.isEmpty()) {
                        writeResponse(socket, 401, json.encodeToString(DirectSyncPairResponse(ok = false, error = "wrong_code")))
                    } else {
                        prefs.pairedDeviceId().set(request.deviceId)
                        prefs.pairedDeviceName().set(request.deviceName)
                        prefs.pairedDeviceAddress().set(socket.inetAddress.hostAddress)
                        val response = DirectSyncPairResponse(
                            ok = true,
                            deviceId = deviceId,
                            deviceName = prefs.deviceName().get(),
                        )
                        writeResponse(socket, 200, json.encodeToString(response))
                    }
                }
                method == "POST" && path == "/sync/exchange" -> {
                    val expected = prefs.pairCode().get()
                    if (expected.isEmpty() || codeHeader != expected) {
                        writeResponse(socket, 401, json.encodeToString(DirectSyncPairResponse(ok = false, error = "wrong_code")))
                    } else {
                        val incoming = runCatching { json.decodeFromString<DirectSyncPayload>(body) }.getOrNull()
                        if (incoming == null) {
                            writeResponse(socket, 400, json.encodeToString(DirectSyncPairResponse(ok = false, error = "bad_payload")))
                        } else {
                            val merged = exchangeHandler(incoming)
                            writeResponse(socket, 200, json.encodeToString(merged))
                        }
                    }
                }
                else -> writeResponse(socket, 404, """{"ok":false,"error":"not_found"}""")
            }
        } catch (e: SocketTimeoutException) {
            logcat(LogPriority.WARN) { "DirectSync: connection timed out" }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "DirectSync: connection failed" }
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        var prevCr = false
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b == '\r'.code) {
                prevCr = true
                continue
            }
            if (prevCr) {
                // stray CR inside the line; keep it simple
                prevCr = false
            }
            sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun writeResponse(socket: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        socket.getOutputStream().write(header.toByteArray(Charsets.UTF_8))
        socket.getOutputStream().write(bytes)
        socket.getOutputStream().flush()
    }
}
