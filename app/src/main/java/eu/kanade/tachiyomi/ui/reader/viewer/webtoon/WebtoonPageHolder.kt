package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.recyclerview.widget.RecyclerView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.tadami.aurora.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ProcessedPageImage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderScaleUtil
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream

/**
 * Holder of the webtoon reader for a single page of a chapter.
 *
 * @param frame the root view for this holder.
 * @param viewer the webtoon viewer.
 * @constructor creates a new webtoon holder.
 */
class WebtoonPageHolder(
    private val frame: ReaderPageImageView,
    viewer: WebtoonViewer,
) : WebtoonBaseHolder(frame, viewer) {

    /**
     * Loading progress bar to indicate the current progress.
     */
    private val progressIndicator = createProgressIndicator()

    /**
     * Progress bar container. Needed to keep a minimum height size of the holder, otherwise the
     * adapter would create more views to fill the screen, which is not wanted.
     */
    private lateinit var progressContainer: ViewGroup

    /**
     * Error layout to show when the image fails to load.
     */
    private var errorLayout: ReaderErrorBinding? = null

    /**
     * Getter to retrieve the height of the recycler view.
     */
    private val parentHeight
        get() = viewer.recycler.height

    /**
     * Page of a chapter.
     */
    private var page: ReaderPage? = null

    /**
     * The page whose layout has settled after the last [bind]. A height change during the bind
     * itself is the RecyclerView positioning a freshly bound item, not a reflow of the page the
     * reader is looking at, so it must not be compensated.
     */
    private var layoutSettledForPage: ReaderPage? = null

    private val scope = MainScope()

    /**
     * Job for loading the page.
     */
    private var loadJob: Job? = null

    // v7.9.46 DIAGNOSTIC: one-line overlay on the page (top-left) carrying
    // the facts for this page, so a screenshot shows them. Remove with
    // ReaderDiag before a normal release.
    private val diagOverlay = TextView(frame.context).apply {
        setBackgroundColor(Color.argb(190, 0, 0, 0))
        setTextColor(Color.WHITE)
        textSize = 11f
        setPadding(8, 4, 8, 4)
        maxLines = 4
        isClickable = false
        isFocusable = false
    }

    /** v7.9.46 DIAGNOSTIC: rebind count and heap captured at bind time. */
    private var currentRebindCount = 0
    private var currentHeapMb = 0L

    private fun diag(line: String) {
        ReaderDiag.log(line)
        diagOverlay.text = line
        diagOverlay.bringToFront()
    }

    init {
        frame.addView(
            diagOverlay,
            FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START),
        )
        refreshLayoutParams()

        // The item keeps a placeholder height until the page image is decoded. RecyclerView pins
        // the top of the first visible item, so when THAT item changes height every page below it
        // moves by the difference; let the viewer keep the reading position instead.
        frame.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val newHeight = bottom - top
            val oldHeight = oldBottom - oldTop
            if (newHeight == oldHeight) return@addOnLayoutChangeListener
            val currentPage = page
            // Only the placeholder -> real image transition of a page that is already loaded is a
            // reflow of what the reader is looking at; layout changes while the page is still
            // loading (and during a bind) belong to the RecyclerView positioning the item.
            if (currentPage == null || currentPage.status != Page.State.READY) return@addOnLayoutChangeListener
            if (layoutSettledForPage !== currentPage) return@addOnLayoutChangeListener
            viewer.schedulePositionPreservation(frame, oldHeight, newHeight)
        }

        frame.onImageLoaded = { onImageDecoded() }
        frame.onImageLoadError = { markDecodeError() }
        frame.onScaleChanged = { viewer.activity.hideMenu() }
    }

    /**
     * Binds the given [page] with this view holder, subscribing to its state.
     */
    fun bind(page: ReaderPage) {
        this.page = page
        layoutSettledForPage = null
        // v7.9.46 DIAGNOSTIC: rebind count + heap for this page, and the
        // overlay line while the image loads.
        ReaderDiag.init(viewer.activity)
        currentRebindCount = page.imageUrl?.let { ReaderDiag.rebindCount(it) } ?: 0
        currentHeapMb = ReaderDiag.heapMb()
        diagOverlay.text = "p${page.number} · binding… · rb:$currentRebindCount · heap:${currentHeapMb}MB"
        diagOverlay.bringToFront()
        // The bind-induced layout happens in the traversal this call belongs to, so anything the
        // view reports after it has settled is a genuine reflow of this page.
        frame.post {
            if (this.page === page) layoutSettledForPage = page
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            // A preloaded or downloaded page can be measured right here: knowing the page size
            // before the image is ready keeps the item height stable while scrolling.
            launchIO { cacheImageDimensions(page) }
            loadPageAndProcessStatus()
        }
        refreshLayoutParams()
        refreshPlaceholderHeight()
    }

    private fun refreshLayoutParams() {
        val displayMetrics = Resources.getSystem().displayMetrics
        val totalWidth = if (viewer.recycler.width > 0) viewer.recycler.width else displayMetrics.widthPixels
        val totalHeight = if (viewer.recycler.height > 0) viewer.recycler.height else displayMetrics.heightPixels

        val dims = page?.imageDimensions
        val margin = ReaderScaleUtil.computeWebtoonAdaptiveSideMargin(
            viewportWidth = totalWidth,
            viewportHeight = totalHeight,
            imageWidth = dims?.width,
            imageHeight = dims?.height,
            basePaddingPercent = viewer.config.sidePadding,
            webtoonSmartFit = viewer.config.webtoonSmartFit,
        )
        val bottomMargin = if (viewer.hasPageGaps) 15.dpToPx else 0

        // Avoid layout thrash: rebinds while scrolling must not trigger a requestLayout
        // when nothing about the layout params actually changed.
        val current = frame.layoutParams as? ViewGroup.MarginLayoutParams
        if (current != null) {
            if (current.marginStart == margin &&
                current.marginEnd == margin &&
                current.bottomMargin == bottomMargin
            ) {
                return
            }
            current.width = MATCH_PARENT
            current.height = WRAP_CONTENT
            current.bottomMargin = bottomMargin
            current.marginStart = margin
            current.marginEnd = margin
            frame.layoutParams = current
            return
        }

        frame.layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            this.bottomMargin = bottomMargin
            marginEnd = margin
            marginStart = margin
        }
    }

    /**
     * Keeps the loading placeholder in sync with the size the item will actually have. The
     * container is sized once at holder creation, when the recycler may not be measured yet
     * (height 0) or may have been resized since (rotation, split screen), and again when the
     * page's pixel size becomes known. A zero or stale placeholder height makes the layout
     * manager create and bind far more holders than needed and makes content jump when the real
     * image height arrives.
     */
    private fun refreshPlaceholderHeight() {
        val height = resolvePlaceholderHeight()
        val params = progressContainer.layoutParams ?: return
        if (height > 0 && params.height != height) {
            progressContainer.updateLayoutParams { this.height = height }
            progressIndicator.updateLayoutParams<FrameLayout.LayoutParams> {
                updateMargins(top = height / 4)
            }
        }
    }

    /**
     * Height reserved for the item while the image is not decoded yet. When the page's pixel size
     * is known, the reserved height is its final rendered height (fit width), so the RecyclerView
     * does not relayout the page when the image becomes ready; otherwise the viewport height is
     * used, as before.
     */
    private fun resolvePlaceholderHeight(): Int {
        val viewportHeight = parentHeight
        val currentPage = page ?: return viewportHeight
        if (currentPage.isAnimatedImage ||
            viewer.config.webtoonSmartFit ||
            viewer.config.imageCropBorders ||
            viewer.config.dualPageSplit ||
            viewer.config.dualPageRotateToFit
        ) {
            return viewportHeight
        }

        val ratio = currentPage.imageDimensions
            ?.let { it.height.toFloat() / it.width.toFloat() }
            ?: currentPage.chapter.typicalPageRatio
            ?: viewer.activity.viewModel.sessionTypicalPageRatio
            ?: return viewportHeight
        val margins = (frame.layoutParams as? ViewGroup.MarginLayoutParams)
            ?.let { it.marginStart + it.marginEnd }
            ?: 0
        val contentWidth = viewer.recycler.width - margins
        if (contentWidth <= 0) return viewportHeight

        return (contentWidth * ratio).toInt().coerceAtLeast(1)
    }

    /**
     * Sniffs the page's pixel size (and whether it is animated) once on IO and stores it on the
     * page. Pages that are not downloaded yet have no [ReaderPage.stream] and are measured later,
     * when the image bytes are already available.
     */
    private suspend fun cacheImageDimensions(page: ReaderPage) {
        if (page.imageDimensions != null || page.isAnimatedImage) return
        val streamFn = page.stream ?: return

        val sniffed = withIOContext {
            runCatching {
                streamFn().use { stream ->
                    val source = Buffer().readFrom(stream)
                    ImageUtil.isAnimatedAndSupported(source) to ImageUtil.getImageDimensions(source)
                }
            }.getOrNull()
        } ?: return

        val (isAnimated, dimensions) = sniffed
        page.isAnimatedImage = isAnimated
        if (!isAnimated && dimensions != null && dimensions.width > 0 && dimensions.height > 0) {
            page.imageDimensions = dimensions
        }

        val boundPage = this.page
        withUIContext {
            dimensions
                ?.takeIf { it.width > 0 && it.height > 0 }
                ?.let {
                    val ratio = it.height.toFloat() / it.width.toFloat()
                    page.chapter.notePageRatio(ratio)
                    viewer.activity.viewModel.noteSessionPageRatio(ratio)
                }
            if (boundPage === page) refreshPlaceholderHeight()
        }
    }

    /**
     * Called when the view is recycled and added to the view pool.
     */
    override fun recycle() {
        loadJob?.cancel()
        loadJob = null

        removeErrorLayout()
        frame.recycle()
        progressIndicator.setProgress(0)
        progressContainer.isVisible = true
    }

    /**
     * Loads the page and processes changes to the page's status.
     *
     * Returns immediately if there is no page or the page has no PageLoader.
     * Otherwise, this function does not return. It will continue to process status changes until
     * the Job is cancelled.
     */
    private suspend fun loadPageAndProcessStatus() {
        val page = page ?: return
        val loader = page.chapter.pageLoader ?: return
        supervisorScope {
            launchIO {
                loader.loadPage(page)
            }
            page.statusFlow.collectLatest { state ->
                when (state) {
                    Page.State.QUEUE -> setQueued()
                    Page.State.LOAD_PAGE -> setLoading()
                    Page.State.DOWNLOAD_IMAGE -> {
                        setDownloading()
                        page.progressFlow.collectLatest { value ->
                            progressIndicator.setProgress(value)
                        }
                    }
                    Page.State.READY -> setImage()
                    Page.State.ERROR -> setError()
                }
            }
        }
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is downloading
     */
    private fun setDownloading() {
        progressContainer.isVisible = true
        progressIndicator.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is ready.
     */
    private suspend fun setImage() {
        progressIndicator.setProgress(0)

        val currentPage = page ?: return
        val streamFn = currentPage.stream ?: return

        // v7.9.44: tall online pages are shown straight from the cache file
        // (no whole-file copy into RAM). If it applies, it returns early.
        if (trySetImageFromFile(currentPage)) return

        try {
            var sniffedDims: ImageUtil.ImageDimensions? = null
            val prepared = withIOContext {
                when (val processed = streamFn().use { process(Buffer().readFrom(it)) }) {
                    is ProcessedPageImage.Decoded -> {
                        sniffedDims = ImageUtil.ImageDimensions(processed.bitmap.width, processed.bitmap.height)
                        PageImageData.Decoded(processed.bitmap)
                    }
                    is ProcessedPageImage.Encoded -> {
                        val source = processed.source
                        // Sizes sniffed at bind time are reused here (unless the image was
                        // transformed by the dual-page options, which changes its geometry);
                        // otherwise sniff image headers here so the UI thread does not have to
                        // instantiate native decoders per page while the user is scrolling.
                        val reusable = currentPage
                            .takeIf { !viewer.config.dualPageSplit && !viewer.config.dualPageRotateToFit }
                            ?.imageDimensions
                        val isAnimated = if (reusable != null) false else ImageUtil.isAnimatedAndSupported(source)
                        val dims = if (isAnimated) null else (reusable ?: ImageUtil.getImageDimensions(source))
                        sniffedDims = dims
                        if (reusable == null && dims != null) {
                            currentPage.imageDimensions = dims
                        }
                        val isTall = dims?.let { it.height.toFloat() / it.width.toFloat() > 3F } ?: false
                        val canUseHardware = dims?.let { ImageUtil.canUseHardwareBitmap(it.width, it.height) } ?: false
                        PageImageData.Encoded(source, isAnimated, isTall, canUseHardware)
                    }
                }
            }
            withUIContext {
                sniffedDims?.let {
                    val ratio = it.height.toFloat() / it.width.toFloat()
                    currentPage.chapter.notePageRatio(ratio)
                    viewer.activity.viewModel.noteSessionPageRatio(ratio)
                    refreshLayoutParams()
                    refreshPlaceholderHeight()
                }
                when (prepared) {
                    is PageImageData.Encoded -> {
                        frame.setImage(
                            prepared.source,
                            prepared.isAnimated,
                            ReaderPageImageView.Config(
                                zoomDuration = viewer.config.doubleTapAnimDuration,
                                minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                                cropBorders = viewer.config.imageCropBorders,
                                webtoonSmartFit = viewer.config.webtoonSmartFit,
                                isTallImage = prepared.isTall,
                                canUseHardwareBitmap = prepared.canUseHardware,
                            ),
                        )
                    }
                    is PageImageData.Decoded -> {
                        // Split-and-merge/rotate results arrive pre-decoded: hand the bitmap
                        // straight to the image view instead of a JPEG q=100 re-encode plus
                        // second decode on the scroll path.
                        val bitmap = prepared.bitmap
                        frame.setImage(
                            BitmapDrawable(frame.resources, bitmap),
                            ReaderPageImageView.Config(
                                zoomDuration = viewer.config.doubleTapAnimDuration,
                                minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                                cropBorders = viewer.config.imageCropBorders,
                                webtoonSmartFit = viewer.config.webtoonSmartFit,
                                isTallImage = bitmap.width > 0 &&
                                    bitmap.height.toFloat() / bitmap.width.toFloat() > 3F,
                            ),
                        )
                    }
                }
                // v7.9.46 DIAGNOSTIC: keep the overlay above the image view.
                diagOverlay.bringToFront()
                removeErrorLayout()
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            withUIContext {
                markDecodeError()
            }
        }
    }

    /**
     * v7.9.44: shows a tall online page straight from the chapter-cache
     * file instead of copying the whole file into RAM (Buffer) and letting
     * the region decoder copy it again. A giant strip copied twice per
     * rebind (10-30MB) is what stalls the screen while re-scrolling over
     * it. Returns true when the file path was used. Falls back to the
     * Buffer path (false) when the file is missing, the page is animated,
     * its size is unknown, or a transform needs the bytes.
     * v7.9.46 DIAGNOSTIC: every decision and timing is logged (ReaderDiag)
     * and shown on the page overlay.
     */
    private suspend fun trySetImageFromFile(page: ReaderPage): Boolean {
        val imageUrl = page.imageUrl ?: run {
            diag("p${page.number} · file:SKIP no-url · rb:$currentRebindCount · heap:${currentHeapMb}MB")
            return false
        }
        if (page.isAnimatedImage) {
            diag("p${page.number} · file:SKIP animated · rb:$currentRebindCount · heap:${currentHeapMb}MB")
            return false
        }
        val dims = page.imageDimensions ?: run {
            diag("p${page.number} · file:SKIP no-dims · rb:$currentRebindCount · heap:${currentHeapMb}MB")
            return false
        }
        val ratio = dims.height.toFloat() / dims.width.toFloat()
        val isTall = ratio > 3F
        if (!isTall) {
            diag("p${page.number} · ${dims.width}x${dims.height} r${"%.1f".format(ratio)} · file:SKIP not-tall · rb:$currentRebindCount · heap:${currentHeapMb}MB")
            return false
        }
        if (viewer.config.dualPageSplit || viewer.config.dualPageRotateToFit) {
            diag("p${page.number} · file:SKIP dual-page · rb:$currentRebindCount · heap:${currentHeapMb}MB")
            return false
        }
        if (viewer.config.webtoonSmartFit || viewer.config.imageCropBorders) {
            diag("p${page.number} · file:SKIP smartfit/crop · rb:$currentRebindCount · heap:${currentHeapMb}MB")
            return false
        }
        val file = Injekt.get<ChapterCache>().getImageFile(imageUrl).takeIf { it.exists() } ?: run {
            diag("p${page.number} · file:SKIP no-cache-file · rb:$currentRebindCount · heap:${currentHeapMb}MB")
            return false
        }
        val outcome = withIOContext { resolveDisplayFile(file) }
        val displayFile = outcome.file ?: file
        val canUseHardware = ImageUtil.canUseHardwareBitmap(dims.width, dims.height)
        var setImageMs = -1L
        withUIContext {
            page.chapter.notePageRatio(ratio)
            viewer.activity.viewModel.noteSessionPageRatio(ratio)
            refreshLayoutParams()
            refreshPlaceholderHeight()
            val t0 = System.nanoTime()
            frame.setImage(
                displayFile,
                ReaderPageImageView.Config(
                    zoomDuration = viewer.config.doubleTapAnimDuration,
                    minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                    cropBorders = viewer.config.imageCropBorders,
                    webtoonSmartFit = viewer.config.webtoonSmartFit,
                    isTallImage = isTall,
                    canUseHardwareBitmap = canUseHardware,
                ),
            )
            setImageMs = (System.nanoTime() - t0) / 1_000_000L
            removeErrorLayout()
        }
        val itemH = (viewer.recycler.width * ratio).toInt()
        diag(
            "p${page.number} · ${outcome.format} ${dims.width}x${dims.height} r${"%.1f".format(ratio)}" +
                " · file · conv:${outcome.status} ${outcome.timeMs}ms" +
                " · setImage:${setImageMs}ms · rb:$currentRebindCount" +
                " · heap:${ReaderDiag.heapMb()}MB · itemH:${itemH}px",
        )
        return true
    }

    /** v7.9.46 DIAGNOSTIC: result of the WebP/AVIF → JPEG conversion. */
    private data class ConvertOutcome(
        val file: File?,
        val format: String,
        val status: String,
        val timeMs: Long,
    )

    private fun elapsedMs(startNs: Long) = (System.nanoTime() - startNs) / 1_000_000L

    /**
     * v7.9.45: returns the JPEG version of a tall WebP/AVIF strip, converting
     * it once and caching the result, so the reader decodes fast JPEG regions
     * instead of slow WebP/AVIF ones. Returns null to keep the original file
     * (already a fast format, or anything failed). Runs on the IO dispatcher;
     * the one-time decode + encode takes a few seconds per page and is
     * cached, so later scrolls are smooth like a downloaded chapter.
     * v7.9.46 DIAGNOSTIC: returns the outcome (format, status, time) so the
     * holder can log it; a failed conversion is retried on every rebind
     * because nothing records the failure — that retry is itself a suspect.
     */
    private fun resolveDisplayFile(file: File): ConvertOutcome {
        val start = System.nanoTime()
        return try {
            val type = file.inputStream().use { ImageUtil.findImageType(it) }
            val format = type?.name ?: "UNKNOWN"
            if (type == null) return ConvertOutcome(null, format, "FAIL:no-type", elapsedMs(start))
            if (type != ImageUtil.ImageType.WEBP && type != ImageUtil.ImageType.AVIF) {
                return ConvertOutcome(null, format, "SKIP:not-webp-avif", elapsedMs(start))
            }
            val dir = File(viewer.activity.cacheDir, "webtoon_converted").apply { mkdirs() }
            val converted = File(dir, file.name + ".jpg")
            if (converted.exists() && converted.length() > 0) {
                return ConvertOutcome(converted, format, "CACHED", elapsedMs(start))
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ?: return ConvertOutcome(null, format, "FAIL:decode-null", elapsedMs(start))
            try {
                val tmp = File.createTempFile(file.name + "_", ".tmp.jpg", dir)
                try {
                    FileOutputStream(tmp).use { out ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                            return ConvertOutcome(null, format, "FAIL:compress-false", elapsedMs(start))
                        }
                    }
                    if (converted.exists() && converted.length() > 0) {
                        tmp.delete()
                        return ConvertOutcome(converted, format, "CACHED", elapsedMs(start))
                    }
                    if (!tmp.renameTo(converted)) {
                        return ConvertOutcome(null, format, "FAIL:rename-false", elapsedMs(start))
                    }
                    ConvertOutcome(converted, format, "OK", elapsedMs(start))
                } catch (e: Exception) {
                    tmp.delete()
                    throw e
                }
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to convert tall WebP/AVIF to JPEG, using original" }
            ConvertOutcome(null, "?", "FAIL:exc:${e.javaClass.simpleName}", elapsedMs(start))
        } catch (e: OutOfMemoryError) {
            logcat(LogPriority.WARN) { "OOM converting tall WebP/AVIF to JPEG, using original" }
            ConvertOutcome(null, "?", "FAIL:oom", elapsedMs(start))
        }
    }

    private fun process(imageSource: BufferedSource): ProcessedPageImage {
        if (viewer.config.dualPageRotateToFit) {
            return rotateDualPage(imageSource)
        }

        if (viewer.config.dualPageSplit) {
            val isDoublePage = ImageUtil.isWideImage(imageSource)
            if (isDoublePage) {
                val upperSide = if (viewer.config.dualPageInvert) ImageUtil.Side.LEFT else ImageUtil.Side.RIGHT
                // peek() keeps the source intact so the stream variant remains a viable fallback.
                ImageUtil.splitAndMergeBitmap(imageSource.peek(), upperSide)?.let {
                    return ProcessedPageImage.Decoded(it)
                }
                return ProcessedPageImage.Encoded(ImageUtil.splitAndMerge(imageSource, upperSide))
            }
        }

        return ProcessedPageImage.Encoded(imageSource)
    }

    private fun rotateDualPage(imageSource: BufferedSource): ProcessedPageImage {
        val isDoublePage = ImageUtil.isWideImage(imageSource)
        if (!isDoublePage) {
            return ProcessedPageImage.Encoded(imageSource)
        }
        val rotation = if (viewer.config.dualPageRotateToFitInvert) -90f else 90f
        return ImageUtil.rotateImageBitmap(imageSource.peek(), rotation)
            ?.let { ProcessedPageImage.Decoded(it) }
            ?: ProcessedPageImage.Encoded(ImageUtil.rotateImage(imageSource, rotation))
    }

    /**
     * Called when the page has an error.
     */
    private fun setError() {
        progressContainer.isVisible = false
        initErrorLayout()
    }

    /**
     * Decode/render failure while the page status is READY: move the status to ERROR so the
     * Retry button can re-queue it through the page loader. Showing the error layout alone left
     * the status at READY and retryPage() became a no-op (the loader skips non-QUEUE pages).
     */
    private fun markDecodeError() {
        val currentPage = page
        if (currentPage != null && currentPage.status == Page.State.READY) {
            currentPage.status = Page.State.ERROR
        }
        setError()
    }

    /**
     * Called when the image is decoded and going to be displayed.
     */
    private fun onImageDecoded() {
        progressContainer.isVisible = false
        removeErrorLayout()
        page?.let(viewer::onPageImageReady)
    }

    /**
     * Creates a new progress bar.
     */
    private fun createProgressIndicator(): ReaderProgressIndicator {
        progressContainer = FrameLayout(context)
        frame.addView(progressContainer, MATCH_PARENT, parentHeight)

        val progress = ReaderProgressIndicator(context).apply {
            updateLayoutParams<FrameLayout.LayoutParams> {
                updateMargins(top = parentHeight / 4)
            }
        }
        progressContainer.addView(progress)
        return progress
    }

    /**
     * Initializes a button to retry pages.
     */
    private fun initErrorLayout(): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), frame, true)
            errorLayout?.root?.layoutParams = FrameLayout.LayoutParams(
                MATCH_PARENT,
                (parentHeight * 0.8).toInt(),
            )
            errorLayout?.actionRetry?.setOnClickListener {
                page?.let { it.chapter.pageLoader?.retryPage(it) }
            }
        }

        val imageUrl = page?.imageUrl
        errorLayout?.actionOpenInWebView?.isVisible = imageUrl != null
        if (imageUrl != null) {
            if (imageUrl.startsWith("http", true)) {
                errorLayout?.actionOpenInWebView?.setOnClickListener {
                    val intent = WebViewActivity.newIntent(context, imageUrl)
                    context.startActivity(intent)
                }
            }
        }

        return errorLayout!!
    }

    /**
     * Removes the decode error layout from the holder, if found.
     */
    private fun removeErrorLayout() {
        errorLayout?.let {
            frame.removeView(it.root)
            errorLayout = null
        }
    }
}

private sealed interface PageImageData {
    data class Encoded(
        val source: BufferedSource,
        val isAnimated: Boolean,
        val isTall: Boolean,
        val canUseHardware: Boolean,
    ) : PageImageData

    data class Decoded(val bitmap: android.graphics.Bitmap) : PageImageData
}
