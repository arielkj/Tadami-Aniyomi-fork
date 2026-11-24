package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.Context
import android.util.Log
import java.io.File

/**
 * v7.9.46 DIAGNOSTIC ONLY — detects why webtoon pages stutter.
 *
 * Every bind / display decision in WebtoonPageHolder is logged here:
 * - logcat, tag "ReaderDiag" (filter: `adb logcat -s ReaderDiag`)
 * - file <external-files>/reader-diag.txt (one line per event)
 * - a one-line overlay on each page, so a screenshot carries the facts
 *
 * What it records per page: format (WEBP/AVIF/JPEG/PNG/…), pixel size
 * and ratio, whether it is animated, which display path was used (file
 * vs Buffer) and why, whether the WebP→JPEG conversion ran / was cached
 * / failed and why, how long the conversion and the UI-thread setImage
 * took, how many times the page was rebound, and the used Java heap.
 *
 * The overlay and the file are only for diagnosis. Remove this object
 * (and its calls) before a normal release.
 */
object ReaderDiag {
    const val TAG = "ReaderDiag"

    private val rebindCounts = mutableMapOf<String, Int>()
    private var diagFile: File? = null

    fun init(context: Context) {
        if (diagFile == null) {
            diagFile = File(context.getExternalFilesDir(null), "reader-diag.txt").also {
                try {
                    it.appendText("\n=== ReaderDiag session ${System.currentTimeMillis()} ===\n")
                } catch (_: Exception) {
                    // diagnostics must never break reading
                }
            }
        }
    }

    fun diagFilePath(context: Context): String {
        init(context)
        return diagFile?.absolutePath ?: "(sin archivo)"
    }

    /** How many times this imageUrl has been bound (1 = first time). */
    fun rebindCount(imageUrl: String): Int {
        val n = (rebindCounts[imageUrl] ?: 0) + 1
        rebindCounts[imageUrl] = n
        return n
    }

    /** Used Java heap, in MB. */
    fun heapMb(): Long {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)
    }

    fun log(msg: String) {
        Log.i(TAG, msg)
        try {
            diagFile?.appendText(msg + "\n")
        } catch (_: Exception) {
            // diagnostics must never break reading
        }
    }
}
