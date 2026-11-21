package eu.kanade.tachiyomi.data.coil.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * v7.9.20 — downloads the Real-ESRGAN anime model (param + bin) on first use.
 * The model is NOT bundled in the APK (the app stays light): it lives in
 * app-private storage after the first download, verified by SHA-256.
 *
 * Source: xinntao/Real-ESRGAN-ncnn-vulkan model converted to ncnn
 * (realesrgan-x4plus-anime), mirrored by Tohrusky/realesrgan-ncnn-py.
 * Total ~8.6 MB. Hashes verified 21 Nov 2026 (direct download + sha256sum).
 */
object RealEsrganModelDownloader {

    private const val MODEL_DIR = "ai-models"

    private const val PARAM_URL =
        "https://raw.githubusercontent.com/Tohrusky/realesrgan-ncnn-py/main/" +
            "src/realesrgan_ncnn_py/models/realesrgan-x4plus-anime.param"
    private const val BIN_URL =
        "https://raw.githubusercontent.com/Tohrusky/realesrgan-ncnn-py/main/" +
            "src/realesrgan_ncnn_py/models/realesrgan-x4plus-anime.bin"

    // Verified 21 Nov 2026 (direct download + sha256sum).
    private const val PARAM_SHA256 =
        "2b8fb6e0ae4d2d85704ca08c119a2f5ea40add4f2ecd512eb7f4cd44b6127ed4"
    private const val BIN_SHA256 =
        "fe01c269cfd10cdef8e018ab66ebe750cf79c7af4d1f9c16c737e1295229bacc"

    /** Approximate download size, shown to the user before the first use. */
    const val MODEL_SIZE_MB = 8.6

    fun paramFile(context: Context): File = File(modelDir(context), "realesrgan-x4plus-anime.param")

    fun binFile(context: Context): File = File(modelDir(context), "realesrgan-x4plus-anime.bin")

    private fun modelDir(context: Context): File =
        File(context.filesDir, MODEL_DIR).apply { mkdirs() }

    fun isDownloaded(context: Context): Boolean {
        val param = paramFile(context)
        val bin = binFile(context)
        return param.exists() && param.length() > 0 &&
            bin.exists() && bin.length() > 0 &&
            sha256(param) == PARAM_SHA256 &&
            sha256(bin) == BIN_SHA256
    }

    /**
     * The model is only downloaded over WiFi (user decision, 21 Nov): the
     * first tap of «Mejorar portada» on mobile data asks the user to
     * connect to WiFi instead of spending their data.
     */
    fun isWifiConnected(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }.getOrDefault(false)

    /**
     * Downloads the model when missing (or corrupt) and verifies it.
     * Returns true when the model is ready to use. Runs on IO.
     */
    suspend fun ensureDownloaded(context: Context, client: OkHttpClient): Boolean {
        if (isDownloaded(context)) return true
        return withContext(Dispatchers.IO) {
            runCatching {
                download(client, PARAM_URL, paramFile(context), PARAM_SHA256)
                download(client, BIN_URL, binFile(context), BIN_SHA256)
                true
            }.onFailure {
                logcat(LogPriority.WARN, it) { "AI model download failed" }
            }.getOrDefault(false)
        }
    }

    private fun download(client: OkHttpClient, url: String, dest: File, expectedSha256: String) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} for $url")
            }
            val body = response.body ?: throw IllegalStateException("Empty body for $url")
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            body.byteStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (sha256(tmp) != expectedSha256) {
                tmp.delete()
                throw IllegalStateException("SHA-256 mismatch for $url")
            }
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                throw IllegalStateException("Could not store model file ${dest.name}")
            }
        }
    }

    private fun sha256(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
