package eu.kanade.tachiyomi.data.coil.ai

import android.graphics.Bitmap
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * v7.9.20 — Kotlin side of the on-device Real-ESRGAN cover upscaler
 * (RealESRGAN_x4plus_anime_6B, ncnn + Vulkan). The native library is optional:
 * every call is guarded so a missing .so, a missing Vulkan device or any
 * native failure degrades to «AI not available» instead of a crash.
 *
 * JNI names must match app/src/main/cpp/realesrgan/realesrgan_jni.cpp.
 *
 * Scale: the model itself is x4, but the cover only needs x2 (guide): the
 * x4 output is downscaled to 2x the input here, which is also cheaper to
 * store. A 460x650 cover becomes 920x1300.
 */
object RealEsrganBridge {

    /** x4 model: the native output is 4x the input on each side. */
    const val SCALE = 4

    /** Output scale actually delivered to the caller (x4 downscaled to x2). */
    const val OUTPUT_SCALE = 2

    /** Longest input edge allowed (bounds output memory: 768*4 = 3072 px). */
    const val MAX_INPUT_EDGE = 768

    @JvmStatic
    private external fun nativeGpuCount(): Int

    @JvmStatic
    private external fun nativeLoadModel(paramPath: String, binPath: String): Int

    @JvmStatic
    private external fun nativeEnhance(argb: IntArray, w: Int, h: Int): IntArray?

    private var libraryLoaded: Boolean? = null

    private fun loadLibrary(): Boolean {
        libraryLoaded?.let { return it }
        val ok = runCatching {
            System.loadLibrary("realesrgan-jni")
            true
        }.onFailure {
            logcat(LogPriority.WARN) { "realesrgan-jni not available: ${it.message}" }
        }.getOrDefault(false)
        libraryLoaded = ok
        return ok
    }

    /**
     * True when this device can run the AI enhancer (native lib present and
     * at least one Vulkan GPU visible to ncnn).
     */
    fun isSupported(): Boolean {
        if (!loadLibrary()) return false
        return runCatching { nativeGpuCount() > 0 }
            .onFailure { logcat(LogPriority.WARN, it) { "nativeGpuCount failed" } }
            .getOrDefault(false)
    }

    /**
     * Loads the model files into the native engine (once per process).
     * Returns true on success.
     */
    fun loadModel(paramPath: String, binPath: String): Boolean {
        if (!loadLibrary()) return false
        return runCatching { nativeLoadModel(paramPath, binPath) == 0 }
            .onFailure { logcat(LogPriority.WARN, it) { "nativeLoadModel failed" } }
            .getOrDefault(false)
    }

    /**
     * Upscales [source] to 2x its size (the x4 model output downscaled by
     * half). The input is downscaled first when its longest edge exceeds
     * [MAX_INPUT_EDGE], to bound output memory. Returns the upscaled bitmap,
     * or null on any failure. The input bitmap is untouched.
     */
    fun enhance(source: Bitmap): Bitmap? {
        if (!loadLibrary()) return null
        return runCatching {
            val scaled = if (maxOf(source.width, source.height) > MAX_INPUT_EDGE) {
                val ratio = MAX_INPUT_EDGE.toFloat() / maxOf(source.width, source.height)
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * ratio).toInt().coerceAtLeast(1),
                    (source.height * ratio).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                source
            }
            try {
                val w = scaled.width
                val h = scaled.height
                val argb = IntArray(w * h)
                scaled.getPixels(argb, 0, w, 0, 0, w, h)
                val outArgb = nativeEnhance(argb, w, h) ?: return null
                val outW = w * SCALE
                val outH = h * SCALE
                if (outArgb.size != outW * outH) return null
                val out4x = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                out4x.setPixels(outArgb, 0, outW, 0, 0, outW, outH)
                try {
                    // x4 -> x2: half the model output. A clean 0.5 box/bilinear
                    // downscale also smooths any model ringing.
                    Bitmap.createScaledBitmap(
                        out4x,
                        w * OUTPUT_SCALE,
                        h * OUTPUT_SCALE,
                        true,
                    )
                } finally {
                    out4x.recycle()
                }
            } finally {
                if (scaled !== source) scaled.recycle()
            }
        }.onFailure {
            logcat(LogPriority.WARN, it) { "Real-ESRGAN enhance failed" }
        }.getOrNull()
    }
}
