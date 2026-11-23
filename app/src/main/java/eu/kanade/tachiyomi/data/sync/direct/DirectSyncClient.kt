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
 * v7.9.39 — a sync failure with a stable [code] the screen can translate
 * ("wrong_code", "server_error", "timeout"…), plus a human-readable
 * [detail] (the server's error text, the HTTP status, the exception).
 * Replaces the old pattern where every unmapped failure collapsed into
 * the generic "sync_failed".
 */
class DirectSyncFailure(val code: String, val detail: String = "") : Exception(code)

/**
 * v7.9.36 — HTTP client for direct device-to-device sync (local WiFi only).
 */
class DirectSyncClient(
    private val json: Json,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // v7.9.39: a first sync sends the whole library and the peer
        // restores it inside the request — on a big library that takes
        // minutes, and the old 200 s call timeout fired as the
        // unclassified InterruptedIOException ("sync_failed").
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .callTimeout(600, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    private fun url(address: String, path: String): String {
        // v7.9.38: never build a URL from a raw address — a port-less
        // address silently became port 80 ("Failed to connect to
        // /192.168.0.141:80"). Normalize first (missing port -> 27801);
        // an unusable address fails here with a specific error instead
        // of a connection to the wrong port.
        val normalized = DirectSyncAddress.normalize(address)
            ?: throw IllegalArgumentException("invalid_address")
        return "http://$normalized$path"
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

    /**
     * One-time pairing: sends this device's id/name + the code typed by the user.
     * v7.9.38 — one-sided pairing: also sends this device's own WiFi address
     * and its own pairing code, so the other device links itself automatically.
     */
    fun pair(
        address: String,
        code: String,
        ownId: String,
        ownName: String,
        ownAddress: String = "",
        ownCode: String = "",
    ): DirectSyncPairResponse {
        val payload = DirectSyncPairRequest(
            deviceId = ownId,
            deviceName = ownName,
            code = code,
            ownAddress = ownAddress,
            ownCode = ownCode,
        )
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

    /**
     * Full exchange: sends our payload, receives the merged payload back.
     *
     * v7.9.39: every failure is a [DirectSyncFailure] with a code, never a
     * bare exception the caller cannot classify. The old
     * `check(response.isSuccessful)` turned ANY non-2xx status (400/500…)
     * into an IllegalStateException that fell through to the generic
     * "sync_failed" — now the status (and the server's error detail, when
     * it sent one) travels with the failure.
     */
    fun exchange(address: String, code: String, ownId: String, payload: DirectSyncPayload): DirectSyncPayload {
        val bodyJson = try {
            json.encodeToString(payload)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw DirectSyncFailure("bad_payload")
        } catch (e: IllegalArgumentException) {
            // JSON cannot represent a non-finite Float (a NaN rating or
            // chapter number): encode throws IllegalArgumentException.
            throw DirectSyncFailure("bad_payload", e.message.orEmpty())
        }
        val request = Request.Builder()
            .url(url(address, "/sync/exchange"))
            .header("X-Sync-Device", ownId)
            .header("X-Sync-Code", code)
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsed = runCatching { json.decodeFromString<DirectSyncErrorBody>(body) }.getOrNull()
                val serverError = parsed?.error?.takeIf { it.isNotBlank() }
                val detail = parsed?.detail?.takeIf { it.isNotBlank() } ?: body.take(200)
                when (response.code) {
                    401 -> throw DirectSyncFailure("wrong_code")
                    400 -> throw DirectSyncFailure("bad_payload", detail)
                    else -> {
                        val where = serverError?.let { "$it: " }.orEmpty()
                        throw DirectSyncFailure("server_error", "$where$detail")
                    }
                }
            }
            return try {
                json.decodeFromString(body)
            } catch (e: kotlinx.serialization.SerializationException) {
                throw DirectSyncFailure("bad_response")
            }
        }
    }
}
