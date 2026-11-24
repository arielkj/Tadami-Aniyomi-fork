package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
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
import tachiyomi.core.common.util.system.getDisplayMaxHeightInPx
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * v7.9.47: strips taller than this height:width ratio are split into
 * ~2-screen pieces when reading online, like downloads do. The 7.9.46
 * diagnostic proved the freeze follows height (r23 strips freeze, r9.7
 * does not), so only the very tall ones are split.
 */
private const val SPLIT_MIN_RATIO = 12F

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

    /**
     * v7.9.47: a very tall strip is shown as a stack of small pieces
     * (see [showSplitPieces]). The container and its piece views, so a
     * rebind/recycle can remove them.
     */
    private var splitContainer: LinearLayout? = null
    private var splitPieceViews: List<ReaderPageImageView> = emptyList()

    /** v7.9.47: true once the first split piece has reported loaded. */
    private var splitDecoded = false

    /**
     * v7.9.49 (diagnostic): black overlay box (top-left) showing the page
     * line + live frame/freeze lines, so a screenshot tells decoder vs
     * page vs layout. The text is refreshed on bind and on every freeze.
     */
    private var diagOverlay: TextView? = null
    private var diagPageLine: String = ""
    private var diagJankListener: (() -> Unit)? = null
    private var currentRebindCount: Int = 0

    /** Rebuilds the overlay text from the current page line + live diag stats. */
    private fun refreshDiagOverlay() {
        val overlay = diagOverlay ?: return
        overlay.text = ReaderDiag.overlayText(diagPageLine)
        overlay.bringToFront()
    }

    init {
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

        // v7.9.49 (diagnostic): overlay box for the page + frame/freeze lines.
        diagOverlay = TextView(frame.context).apply {
            setBackgroundColor(Color.argb(190, 0, 0, 0))
            setTextColor(Color.WHITE)
            textSize = 11f
            maxLines = 5
            isClickable = false
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            )
        }.also { frame.addView(it) }
    }

    /**
     * Binds the given [page] with this view holder, subscribing to its state.
     */
    fun bind(page: ReaderPage) {
        this.page = page
        layoutSettledForPage = null
        // A rebind drops the previous page's split pieces (if any) before
        // the new page's image is set.
        clearSplitViews()
        // v7.9.49 (diagnostic): start frame tracking + the UI-thread
        // sampler (idempotent) and show this page's line. The line is
        // refined in trySetImageFromFile once format/size are known.
        ReaderDiag.start(viewer.activity)
        currentRebindCount = ReaderDiag.rebindCount(page.imageUrl)
        diagPageLine = "p${page.number} · binding… · rb:$currentRebindCount · heap:${ReaderDiag.heapMb()}MB"
        refreshDiagOverlay()
        diagJankListener?.let { ReaderDiag.removeJankListener(it) }
        diagJankListener = { refreshDiagOverlay() }.also { ReaderDiag.addJankListener(it) }
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

        // v7.9.49 (diagnostic): stop refreshing this holder's overlay.
        diagJankListener?.let { ReaderDiag.removeJankListener(it) }
        diagJankListener = null

        clearSplitViews()
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
                removeErrorLayout()
                // v7.9.49 (diagnostic): page line for the Buffer path too.
                sniffedDims?.let { dims ->
                    diagPageLine = "p${currentPage.number} · buffer ${dims.width}x${dims.height} r${ReaderDiag.ratio1(dims.width, dims.height)} · rb:$currentRebindCount · heap:${ReaderDiag.heapMb()}MB"
                    ReaderDiag.logPage(diagPageLine)
                    refreshDiagOverlay()
                }
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
     * v7.9.47: strips taller than [SPLIT_MIN_RATIO] are split into
     * ~2-screen pieces first (like downloads do) and shown as a stack,
     * because the 7.9.46 diagnostic proved the freeze follows height
     * (r23 strips freeze, r9.7 does not): one 18700px image forces the
     * reader to decode many big tiles at once at the page boundary.
     */
    private suspend fun trySetImageFromFile(page: ReaderPage): Boolean {
        val imageUrl = page.imageUrl ?: return false
        if (page.isAnimatedImage) return false
        val dims = page.imageDimensions ?: return false
        val ratio = dims.height.toFloat() / dims.width.toFloat()
        val isTall = ratio > 3F
        if (!isTall) return false
        if (viewer.config.dualPageSplit || viewer.config.dualPageRotateToFit) return false
        if (viewer.config.webtoonSmartFit || viewer.config.imageCropBorders) return false
        val file = Injekt.get<ChapterCache>().getImageFile(imageUrl).takeIf { it.exists() } ?: return false
        // v7.9.49 (diagnostic): image format (decoder behaviour depends on
        // it) + the display file, both read off the UI thread.
        val (displayFile, format) = withIOContext {
            val fmt = runCatching { file.inputStream().use { ImageUtil.findImageType(it)?.name } }.getOrNull() ?: "?"
            (resolveDisplayFile(file) ?: file) to fmt
        }
        val canUseHardware = ImageUtil.canUseHardwareBitmap(dims.width, dims.height)
        val config = ReaderPageImageView.Config(
            zoomDuration = viewer.config.doubleTapAnimDuration,
            minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
            cropBorders = viewer.config.imageCropBorders,
            webtoonSmartFit = viewer.config.webtoonSmartFit,
            isTallImage = isTall,
            canUseHardwareBitmap = canUseHardware,
        )
        // A very tall strip is split and shown as pieces; anything else
        // (or a failed split) keeps the single-image path.
        if (ratio > SPLIT_MIN_RATIO) {
            val pieces = withIOContext { splitDisplayFile(displayFile, dims) }
            if (pieces != null && pieces.size >= 2 && this.page === page) {
                val setImageMs = withUIContext {
                    val t0 = System.nanoTime()
                    page.chapter.notePageRatio(ratio)
                    viewer.activity.viewModel.noteSessionPageRatio(ratio)
                    refreshLayoutParams()
                    refreshPlaceholderHeight()
                    showSplitPieces(pieces, dims, config)
                    removeErrorLayout()
                    (System.nanoTime() - t0) / 1_000_000
                }
                // v7.9.49 (diagnostic): full page line + overlay refresh.
                diagPageLine = "p${page.number} · $format ${dims.width}x${dims.height} r${ReaderDiag.ratio1(dims.width, dims.height)} · split:${pieces.size} · setImage:${setImageMs}ms · rb:$currentRebindCount · heap:${ReaderDiag.heapMb()}MB · itemH:${frame.height}px"
                ReaderDiag.logPage(diagPageLine)
                withUIContext { refreshDiagOverlay() }
                return true
            }
        }
        val setImageMs = withUIContext {
            val t0 = System.nanoTime()
            page.chapter.notePageRatio(ratio)
            viewer.activity.viewModel.noteSessionPageRatio(ratio)
            refreshLayoutParams()
            refreshPlaceholderHeight()
            frame.setImage(displayFile, config)
            removeErrorLayout()
            (System.nanoTime() - t0) / 1_000_000
        }
        // v7.9.49 (diagnostic): full page line + overlay refresh.
        diagPageLine = "p${page.number} · $format ${dims.width}x${dims.height} r${ReaderDiag.ratio1(dims.width, dims.height)} · file · setImage:${setImageMs}ms · rb:$currentRebindCount · heap:${ReaderDiag.heapMb()}MB · itemH:${frame.height}px"
        ReaderDiag.logPage(diagPageLine)
        withUIContext { refreshDiagOverlay() }
        return true
    }

    /**
     * v7.9.45: returns the JPEG version of a tall WebP/AVIF strip, converting
     * it once and caching the result, so the reader decodes fast JPEG regions
     * instead of slow WebP/AVIF ones. Returns null to keep the original file
     * (already a fast format, or anything failed). Runs on the IO dispatcher;
     * the one-time decode + encode takes a few seconds per page and is
     * cached, so later scrolls are smooth like a downloaded chapter.
     */
    private fun resolveDisplayFile(file: File): File? {
        return try {
            val type = file.inputStream().use { ImageUtil.findImageType(it) } ?: return null
            if (type != ImageUtil.ImageType.WEBP && type != ImageUtil.ImageType.AVIF) return null
            val dir = File(viewer.activity.cacheDir, "webtoon_converted").apply { mkdirs() }
            val converted = File(dir, file.name + ".jpg")
            if (converted.exists() && converted.length() > 0) return converted
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            try {
                val tmp = File.createTempFile(file.name + "_", ".tmp.jpg", dir)
                try {
                    FileOutputStream(tmp).use { out ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) return null
                    }
                    if (converted.exists() && converted.length() > 0) {
                        tmp.delete()
                        return converted
                    }
                    if (!tmp.renameTo(converted)) return null
                    converted
                } catch (e: Exception) {
                    tmp.delete()
                    throw e
                }
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to convert tall WebP/AVIF to JPEG, using original" }
            null
        } catch (e: OutOfMemoryError) {
            logcat(LogPriority.WARN) { "OOM converting tall WebP/AVIF to JPEG, using original" }
            null
        }
    }

    /** v7.9.47: one piece of a split tall strip. */
    private data class SplitPiece(val file: File, val height: Int)

    /**
     * v7.9.47: how many ~2-screen pieces a strip of [imageHeight] splits
     * into. Same cut as the downloader ([ImageUtil.splitTallImage]):
     * one piece is twice the display's longest side.
     */
    private fun splitPartCount(imageHeight: Int): Int {
        val optimalImageHeight = getDisplayMaxHeightInPx * 2
        return (imageHeight - 1) / optimalImageHeight + 1
    }

    /**
     * v7.9.47: height of piece [index] of a strip of [imageHeight] split
     * into [partCount] pieces. The last piece takes the remainder, so the
     * pieces cover the whole strip with no gaps.
     */
    private fun splitPieceHeight(index: Int, partCount: Int, imageHeight: Int): Int {
        val optimalSplitHeight = imageHeight / partCount
        val topOffset = index * optimalSplitHeight
        var splitHeight = minOf(optimalSplitHeight, imageHeight - topOffset)
        if (index == partCount - 1) {
            splitHeight += imageHeight - (topOffset + splitHeight)
        }
        return splitHeight
    }

    /**
     * v7.9.47: splits a very tall display file into ~2-screen JPEG pieces
     * (same cut as downloads) under cacheDir/webtoon_split/<file name>/,
     * reusing them when they already exist. Returns null when the strip
     * does not need splitting or anything fails; the caller then shows
     * the whole image. Runs on the IO dispatcher; the region decoder
     * reads one piece at a time, so peak RAM is one piece, not the strip.
     * The original file is never deleted (it is the chapter cache).
     */
    private fun splitDisplayFile(file: File, dims: ImageUtil.ImageDimensions): List<SplitPiece>? {
        val imageHeight = dims.height
        val imageWidth = dims.width
        val partCount = splitPartCount(imageHeight)
        if (partCount < 2) return null
        val dir = File(File(viewer.activity.cacheDir, "webtoon_split"), file.name).apply { mkdirs() }
        val names = (1..partCount).map { "part_%03d.jpg".format(Locale.ENGLISH, it) }
        val existing = names.map { File(dir, it) }
        if (existing.all { it.exists() && it.length() > 0 }) {
            return existing.mapIndexed { index, f ->
                SplitPiece(f, splitPieceHeight(index, partCount, imageHeight))
            }
        }
        // (Re)split: drop any stale or partial pieces first.
        dir.listFiles()?.forEach { it.delete() }
        val input = try {
            file.inputStream()
        } catch (e: Exception) {
            return null
        }
        val decoder = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(input)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(input, false)
            }
        } catch (e: Exception) {
            try {
                input.close()
            } catch (_: Exception) {
            }
            logcat(LogPriority.WARN, e) { "Failed to open region decoder for tall strip" }
            return null
        } ?: run {
            try {
                input.close()
            } catch (_: Exception) {
            }
            return null
        }
        try {
            val pieces = mutableListOf<SplitPiece>()
            val optimalSplitHeight = imageHeight / partCount
            for (index in 0 until partCount) {
                val height = splitPieceHeight(index, partCount, imageHeight)
                val topOffset = index * optimalSplitHeight
                val region = Rect(0, topOffset, imageWidth, topOffset + height)
                val bitmap = decoder.decodeRegion(region, BitmapFactory.Options()) ?: run {
                    dir.listFiles()?.forEach { it.delete() }
                    return null
                }
                val pieceFile = File(dir, names[index])
                try {
                    FileOutputStream(pieceFile).use { out ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                            dir.listFiles()?.forEach { it.delete() }
                            return null
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
                pieces.add(SplitPiece(pieceFile, height))
            }
            return pieces
        } catch (e: Exception) {
            dir.listFiles()?.forEach { it.delete() }
            logcat(LogPriority.WARN, e) { "Failed to split tall strip for online reading, using whole image" }
            return null
        } finally {
            decoder.recycle()
            try {
                input.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * v7.9.47: shows a split strip as a vertical stack of small images
     * inside this holder's frame, one [ReaderPageImageView] per piece.
     * Each piece is ~2 screens tall, so its tiles decode fast and the
     * page boundary no longer freezes. Runs on the UI thread. The first
     * piece to load hides the progress indicator (the page is readable);
     * pinch-zoom is off per piece because the webtoon pinch scales the
     * whole list, and a single piece zooming alone would break the strip.
     */
    private fun showSplitPieces(
        pieces: List<SplitPiece>,
        dims: ImageUtil.ImageDimensions,
        config: ReaderPageImageView.Config,
    ) {
        clearSplitViews()
        // The frame's own image view is unused for a split page; hide it
        // (a rebind without an intervening recycle could otherwise leave
        // the previous page's image showing underneath the pieces).
        frame.recycle()
        val margins = (frame.layoutParams as? ViewGroup.MarginLayoutParams)
            ?.let { it.marginStart + it.marginEnd } ?: 0
        val contentWidth = (viewer.recycler.width - margins).coerceAtLeast(1)
        val container = LinearLayout(frame.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        val pieceViews = pieces.map { piece ->
            ReaderPageImageView(frame.context, isWebtoon = true).apply {
                val pieceHeightPx = (contentWidth.toLong() * piece.height / dims.width)
                    .toInt()
                    .coerceAtLeast(1)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, pieceHeightPx)
                onImageLoaded = {
                    if (!splitDecoded) {
                        splitDecoded = true
                        onImageDecoded()
                    }
                }
                onImageLoadError = { markDecodeError() }
                onScaleChanged = { viewer.activity.hideMenu() }
            }
        }
        pieceViews.forEach { container.addView(it) }
        frame.addView(container)
        splitContainer = container
        splitPieceViews = pieceViews
        pieceViews.forEachIndexed { index, view ->
            view.setImage(
                pieces[index].file,
                // v7.9.48: no eager tile decoding on split pieces — all
                // pieces decoding at once on (re)bind froze the screen in
                // both scroll directions; each piece now decodes only its
                // visible tiles, when visible.
                config.copy(enablePinchToZoom = false, isTallImage = true, eagerLoading = false),
            )
        }
    }

    /**
     * v7.9.47: removes the split pieces (if any) from this holder and
     * recycles their image views. Called on rebind and recycle.
     */
    private fun clearSplitViews() {
        splitContainer?.let { container ->
            splitPieceViews.forEach { it.recycle() }
            frame.removeView(container)
        }
        splitContainer = null
        splitPieceViews = emptyList()
        splitDecoded = false
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
