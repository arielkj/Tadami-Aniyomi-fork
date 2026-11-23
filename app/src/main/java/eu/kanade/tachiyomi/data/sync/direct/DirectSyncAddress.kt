package eu.kanade.tachiyomi.data.sync.direct

import eu.kanade.domain.sync.DeviceSyncPreferences

/**
 * v7.9.38 — Address parsing for direct device sync.
 *
 * v7.9.37 bug (user photo, phone sync screen): the sync failed with
 * "Failed to connect to /192.168.0.141:80" — port 80, not 27801. The
 * client itself builds the URL with the port it is given; the port was
 * lost BEFORE that: the server-side /sync/pair handler stored the peer's
 * bare socket IP (`socket.inetAddress.hostAddress`, no port) into
 * pairedDeviceAddress, so the next sync used "http://192.168.0.141/…"
 * (port 80). The address field on screen was a red herring — sync uses
 * the STORED address, not the field.
 *
 * Rule from here on: an address is ALWAYS stored and used as
 * "host:port". This normalizer is the single place that enforces it:
 * it accepts "192.168.0.141", "192.168.0.141:27801", "http://…:27801/"
 * and returns "host:port" (default port [DeviceSyncPreferences.DEFAULT_PORT]
 * when missing). Anything unparsable — a port above 65535 (the user's
 * "278012" typo), letters in the port, an empty host — returns null so
 * the caller can show a specific error instead of failing silently.
 */
object DirectSyncAddress {

    /** Returns "host:port", or null when the address cannot be used. */
    fun normalize(raw: String?): String? {
        var text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (text.startsWith("http://", ignoreCase = true)) {
            text = text.substring("http://".length)
        } else if (text.startsWith("https://", ignoreCase = true)) {
            text = text.substring("https://".length)
        }
        // Drop any path/query after the host:port.
        text = text.substringBefore('/').trim()
        if (text.isEmpty()) return null

        val host: String
        val port: Int
        if (text.startsWith("[")) {
            // Bracketed IPv6: [::1]:27801
            val close = text.indexOf(']')
            if (close < 0) return null
            host = text.substring(0, close + 1)
            val rest = text.substring(close + 1)
            port = when {
                rest.isEmpty() -> DeviceSyncPreferences.DEFAULT_PORT
                rest.startsWith(":") -> parsePort(rest.substring(1)) ?: return null
                else -> return null
            }
        } else {
            val lastColon = text.lastIndexOf(':')
            if (lastColon < 0) {
                host = text
                port = DeviceSyncPreferences.DEFAULT_PORT
            } else {
                host = text.substring(0, lastColon)
                port = parsePort(text.substring(lastColon + 1)) ?: return null
            }
        }
        if (host.isBlank() || host.contains(' ') || host.contains('/')) return null
        return "$host:$port"
    }

    fun isValid(raw: String?): Boolean = normalize(raw) != null

    private fun parsePort(text: String): Int? {
        if (text.isEmpty() || text.any { !it.isDigit() }) return null
        val port = text.toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        return port
    }
}
