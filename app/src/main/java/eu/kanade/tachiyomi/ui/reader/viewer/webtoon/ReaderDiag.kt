package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.Context
import android.os.Debug
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * v7.9.49 — DIAGNOSTIC ONLY. It fixes nothing. It measures what freezes
 * the webtoon reader so the next fix targets the real cause, not a guess.
 *
 * Two instruments, both best-effort and wrapped so diagnostics never
 * break reading:
 *
 * 1. Frame times ([Choreographer]): the interval between vsync callbacks.
 *    A frame longer than [JANK_THRESHOLD_MS] is a freeze — the screen did
 *    not draw for that long. Tracked: last frame, max frame, freeze count.
 *
 * 2. A sampler: a background thread records the main (UI) thread's stack
 *    every [SAMPLE_INTERVAL_MS] into a ring buffer. When a freeze is
 *    detected, the samples from inside the freeze window are dumped: the
 *    most common top frame names what the UI thread was doing while the
 *    screen was frozen (decoder? layout? draw? GC? idle?).
 *
 * The overlay (one per visible page) shows the page line plus the live
 * frame stats and the top frame of the last freeze, so a screenshot is
 * enough to tell decoder vs page vs layout. The full dumps also go to
 * <external-files>/reader-diag.txt and logcat (tag ReaderDiag).
 */
object ReaderDiag {

    private const val TAG = "ReaderDiag"

    /** A frame longer than this is a freeze (at 120Hz a normal frame is ~8ms). */
    private const val JANK_THRESHOLD_MS = 50L

    /** Gaps longer than this mean the app was backgrounded / first frame — not a freeze. */
    private const val IDLE_GAP_MS = 1000L

    private const val SAMPLE_INTERVAL_MS = 16L

    /** Ring of main-thread samples (~768ms of history). */
    private const val RING_SIZE = 48

    /** Frames kept per sample in the dump (top of the stack). */
    private const val STACK_DEPTH = 8

    // ---- live stats (written on the UI thread, read on the UI thread) ----
    @Volatile var lastFrameMs: Long = 0
    @Volatile var maxFrameMs: Long = 0
    @Volatile var jankCount: Int = 0
    @Volatile var lastJankMs: Long = 0
    @Volatile var lastJankTop: String = "-"
    @Volatile var lastJankAt: String = "-"

    private data class Sample(val uptimeMs: Long, val top: String, val stack: String)

    private val samples = ArrayDeque<Sample>(RING_SIZE)

    private val fileWriter = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ReaderDiag-writer").apply { isDaemon = true }
    }

    private var logFile: File? = null
    private val started = AtomicBoolean(false)

    private var lastFrameNanos: Long = 0L
    private var tracking = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            try {
                if (lastFrameNanos != 0L) {
                    val deltaMs = (frameTimeNanos - lastFrameNanos) / 1_000_000
                    if (deltaMs in 1 until IDLE_GAP_MS) {
                        lastFrameMs = deltaMs
                        if (deltaMs > maxFrameMs) maxFrameMs = deltaMs
                        if (deltaMs >= JANK_THRESHOLD_MS) {
                            jankCount++
                            onJank(deltaMs, SystemClock.uptimeMillis())
                        }
                    }
                }
                lastFrameNanos = frameTimeNanos
            } catch (_: Throwable) {
            } finally {
                if (tracking) {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }
    }

    private val samplerRunning = AtomicBoolean(false)
    private var samplerThread: Thread? = null

    /** Overlay refresh callbacks, one per visible holder. */
    private val jankListeners = mutableListOf<() -> Unit>()

    fun addJankListener(listener: () -> Unit) {
        synchronized(jankListeners) { jankListeners.add(listener) }
    }

    fun removeJankListener(listener: () -> Unit) {
        synchronized(jankListeners) { jankListeners.remove(listener) }
    }

    /**
     * Starts frame tracking + the UI-thread sampler. Idempotent: safe to
     * call from every viewer creation.
     */
    fun start(context: Context) {
        try {
            if (logFile == null) {
                val dir = context.getExternalFilesDir(null)
                logFile = dir?.let { File(it, "reader-diag.txt") }?.also { file ->
                    file.appendText("\n===== ReaderDiag v7.9.49 session ${System.currentTimeMillis()} =====\n")
                }
            }
        } catch (_: Throwable) {
        }
        if (started.compareAndSet(false, true)) {
            tracking = true
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(frameCallback)
            startSampler()
            log("diag started (jank >= ${JANK_THRESHOLD_MS}ms, sample every ${SAMPLE_INTERVAL_MS}ms)")
        }
    }

    fun stop() {
        if (started.compareAndSet(true, false)) {
            tracking = false
            try {
                Choreographer.getInstance().removeFrameCallback(frameCallback)
            } catch (_: Throwable) {
            }
            stopSampler()
            log("diag stopped (frames: max=${maxFrameMs}ms, janks=$jankCount)")
        }
    }

    private fun startSampler() {
        if (!samplerRunning.compareAndSet(false, true)) return
        val mainThread = Looper.getMainLooper().thread
        samplerThread = Thread({
            while (samplerRunning.get()) {
                try {
                    val stack = mainThread.stackTrace
                    val top = topFrame(stack)
                    val short = stack
                        .mapNotNull { shortFrame(it) }
                        .take(STACK_DEPTH)
                        .joinToString(" <- ")
                    synchronized(samples) {
                        if (samples.size >= RING_SIZE) samples.removeFirst()
                        samples.addLast(Sample(SystemClock.uptimeMillis(), top, short))
                    }
                } catch (_: Throwable) {
                }
                try {
                    Thread.sleep(SAMPLE_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "ReaderDiag-sampler").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopSampler() {
        samplerRunning.set(false)
        samplerThread?.interrupt()
        samplerThread = null
        synchronized(samples) { samples.clear() }
    }

    /**
     * A freeze just happened. Finds what the UI thread was doing inside
     * the freeze window (samples from now-frameMs .. now), names the most
     * common top frame, logs the full dump, and refreshes the overlays.
     */
    private fun onJank(frameMs: Long, nowMs: Long) {
        lastJankMs = frameMs
        lastJankAt = "${(nowMs % 100_000)}ms"
        val window = synchronized(samples) {
            samples.filter { it.uptimeMs >= nowMs - frameMs }.toList()
        }
        val top = window
            .groupingBy { it.top }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: "?"
        lastJankTop = top
        val gc = gcInfo()
        val dump = buildString {
            append("JANK ${frameMs}ms top=$top samples=${window.size} gc=$gc\n")
            window.forEach { append("  ${it.uptimeMs % 100_000}ms ${it.top} :: ${it.stack}\n") }
        }
        log(dump.trimEnd())
        val listeners = synchronized(jankListeners) { jankListeners.toList() }
        listeners.forEach { listener ->
            try {
                listener()
            } catch (_: Throwable) {
            }
        }
    }

    /** First stack frame that is not idle/dispatch plumbing, as ShortClass.method. */
    private fun topFrame(stack: Array<StackTraceElement>): String {
        for (el in stack) {
            shortFrame(el)?.let { return it }
        }
        return "idle"
    }

    /** Null for plumbing frames (idle loop, handler dispatch, vsync pump). */
    private fun shortFrame(el: StackTraceElement): String? {
        val cls = el.className
        val m = el.methodName
        if (cls == "android.os.MessageQueue" && m == "nativePollOnce") return null
        if (cls == "android.os.Looper" && (m == "loop" || m == "loopOnce")) return null
        if (cls == "android.os.Handler" && (m == "dispatchMessage" || m == "handleCallback")) return null
        if (cls == "android.view.Choreographer" && (m == "doFrame" || m == "doCallbacks" || m == "doScheduleCallback")) return null
        if (cls == "java.lang.Thread" && (m == "run" || m == "getStackTrace")) return null
        val simple = cls.substringAfterLast('.')
        return "$simple.$m"
    }

    /** GC count + total GC time (ms), when the runtime reports them. */
    fun gcInfo(): String {
        return try {
            val count = Debug.getRuntimeStat("art.gc.gc-count") ?: "?"
            val time = Debug.getRuntimeStat("art.gc.time") ?: "?"
            "gc=$count/${time}ms"
        } catch (_: Throwable) {
            "gc=?"
        }
    }

    fun heapMb(): Long {
        return try {
            val rt = Runtime.getRuntime()
            (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        } catch (_: Throwable) {
            -1
        }
    }

    /** Per-image-URL bind counter (how many times a page was (re)bound). */
    private val rebindCounts = mutableMapOf<String, Int>()

    fun rebindCount(imageUrl: String?): Int {
        if (imageUrl == null) return 0
        synchronized(rebindCounts) {
            val next = (rebindCounts[imageUrl] ?: 0) + 1
            rebindCounts[imageUrl] = next
            return next
        }
    }

    /** Full overlay text for a page: its line plus the live frame/freeze lines. */
    fun overlayText(pageLine: String): String {
        return buildString {
            append(pageLine)
            append("\nframe: ${lastFrameMs}ms max: ${maxFrameMs}ms jank: $jankCount")
            append("\nUI en el trabón: $lastJankTop (${lastJankMs}ms) ${gcInfo()}")
        }
    }

    fun logPage(line: String) = log(line)

    fun log(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: Throwable) {
        }
        val file = logFile ?: return
        val text = msg
        fileWriter.execute {
            try {
                file.appendText(text + "\n")
            } catch (_: Throwable) {
            }
        }
    }

    /** Approximate ratio with one decimal, for the page line. */
    fun ratio1(w: Int, h: Int): String {
        if (w <= 0) return "?"
        return ((h.toFloat() / w * 10).roundToInt() / 10f).toString()
    }
}
