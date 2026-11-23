package eu.kanade.tachiyomi.data.sync.direct

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * v7.9.36 — HTTP client for direct device-to-device sync (local WiFi only).
 */
class DirectSyncClient(
    private val json: Json,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .callTimeout(200, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    private fun url(address: String, path: String): String {
        val host = address.trim()
        return if (host.startsWith("http://") || host.startsWith("https://")) {
            host.trimEnd('/') + path
        } else {
            "http://$host$path"
        }
    }

    /** Reachability + identity check. Throws on failure. */
    fun info(address: String): DirectSyncInfo {
        val request = Request.Builder().url(url(address, "/sync/info")).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { "info failed: ${response.code}" }
            return json.decodeFromString(body)
        }
    }

    /** One-time pairing: sends this device's id/name + the code typed by the user. */
    fun pair(address: String, code: String, ownId: String, ownName: String): DirectSyncPairResponse {
        val payload = DirectSyncPairRequest(deviceId = ownId, deviceName = ownName, code = code)
        val request = Request.Builder()
            .url(url(address, "/sync/pair"))
            .post(json.encodeToString(payload).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val parsed = runCatching { json.decodeFromString<DirectSyncPairResponse>(body) }.getOrNull()
            if (!response.isSuccessful) {
                return parsed ?: DirectSyncPairResponse(ok = false, error = "http_${response.code}")
            }
            return parsed ?: DirectSyncPairResponse(ok = false, error = "bad_response")
        }
    }

    /** Full exchange: sends our payload, receives the merged payload back. */
    fun exchange(address: String, code: String, ownId: String, payload: DirectSyncPayload): DirectSyncPayload {
        val request = Request.Builder()
            .url(url(address, "/sync/exchange"))
            .header("X-Sync-Device", ownId)
            .header("X-Sync-Code", code)
            .post(json.encodeToString(payload).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { "exchange failed: ${response.code} $body" }
            return json.decodeFromString(body)
        }
    }
}
