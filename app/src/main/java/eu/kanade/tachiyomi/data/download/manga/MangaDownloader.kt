package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import aniyomi.util.DataSaver
import aniyomi.util.DataSaver.Companion.getImage
import com.hippo.unifile.UniFile
import eu.kanade.domain.entries.manga.model.getComicInfo
import eu.kanade.domain.items.chapter.model.toSChapter
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadNetworkStatus
import eu.kanade.tachiyomi.data.download.engine.DownloadCompletionTracker
import eu.kanade.tachiyomi.data.download.engine.DownloadSection
import eu.kanade.tachiyomi.data.download.engine.DownloadTelemetryEmitter
import eu.kanade.tachiyomi.data.download.isNetworkRelatedFailure
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.download.toDownloadNetworkStatus
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateNotifier
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.DiskUtil.NOMEDIA_FILE
import eu.kanade.tachiyomi.util.storage.saveTo
import eu.kanade.tachiyomi.util.system.activeNetworkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import mihon.core.archive.ZipWriter
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.Response
import okio.Throttler
import okio.buffer
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.R
import java.util.concurrent.ConcurrentHashMap
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.renameToOrCopy
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNow
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Locale

/**
 * This class is the one in charge of downloading chapters.
 *
 * Its queue contains the list of chapters to download. In order to download them, the downloader
 * subscription must be running and the list of chapters must be sent to them by [downloadsRelay].
 *
 * The queue manipulation must be done in one thread (currently the main thread) to avoid unexpected
 * behavior, but it's safe to read it from multiple threads.
 */
class MangaDownloader(
    private val context: Context,
    private val provider: MangaDownloadProvider,
    private val cache: MangaDownloadCache,
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val chapterCache: ChapterCache = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val xml: XML = Injekt.get(),
    private val getCategories: GetMangaCategories = Injekt.get(),
    private val getMangaTracks: GetMangaTracks = Injekt.get(),
    // SY -->
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // SY <--
    var telemetryEmitter: DownloadTelemetryEmitter = DownloadTelemetryEmitter.NOOP,
    var completionTracker: DownloadCompletionTracker = DownloadCompletionTracker(),
) {

    /**
     * Store for persisting downloads across restarts.
     */
    private val store = MangaDownloadStore(context)

    /**
     * Queue where active downloads are kept.
     */
    private val _queueState = MutableStateFlow<List<MangaDownload>>(emptyList())

    // v7.9.54: bloqueo para que el filtro ("ya descargado / ya en cola")
    // y el agregado a la cola sean una sola operación atómica — sin él,
    // dos llamadas a queueChapters a la vez (descarga por adelantado al
    // ir para atrás y bajar de nuevo, o manual + automática) podían ver
    // el mismo capítulo como "no encolado" y encolarlo dos veces.
    private val queueLock = Any()
    val queueState = _queueState.asStateFlow()

    /**
     * Notifier for the downloader state and progress.
     */
    private val notifier by lazy { MangaDownloadNotifier(context) }

    /**
     * The throttler used to control the download speed.
     */
    private val throttler = Throttler()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloaderJob: Job? = null

    // v7.9.27: freno por fuente. Si una fuente responde 429, la descarga de esa
    // fuente afloja sola y la concurrencia baja temporalmente a 1 en vez de
    // insistir (el usuario advirtió que las fuentes pueden bloquear el acceso
    // si se hacen muchas descargas a la vez). Mapa fuente -> instante (ms) en
    // el que se puede volver a intentar.
    private val rateLimitedUntilBySource = ConcurrentHashMap<Long, Long>()

    // v7.9.27: capítulos re-encolados automáticamente UNA vez tras un fallo de
    // red (clave = id del capítulo, valor = intentos ya usados).
    private val networkAutoRetries = ConcurrentHashMap<Long, Int>()

    // v7.9.27: capítulos cuya imagen falló por red mientras la lista de páginas
    // sí se obtuvo (el capítulo puede acabar en ERROR por isDownloadSuccessful
    // sin lanzar excepción: ahí se consulta este registro).
    private val networkFailedChapterIds = ConcurrentHashMap.newKeySet<Long>()

    private fun isSourceRateLimited(sourceId: Long): Boolean =
        (rateLimitedUntilBySource[sourceId] ?: 0L) > System.currentTimeMillis()

    private fun rateLimitWaitMs(sourceId: Long): Long =
        ((rateLimitedUntilBySource[sourceId] ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    private fun markSourceRateLimited(sourceId: Long, windowSeconds: Long = RATE_LIMIT_WINDOW_SECONDS) {
        rateLimitedUntilBySource[sourceId] =
            System.currentTimeMillis() + windowSeconds * 1000L
    }

    /**
     * Reintenta una operación de red (lista de páginas, URL de imagen) hasta 3
     * veces con backoff 2/4/8 s, igual que los bytes de la imagen. Un 429 marca
     * la fuente como limitada y espera su ventana (máx. 60 s) antes de seguir.
     */
    private suspend fun <T> retryNetworkCall(sourceId: Long, block: suspend () -> T): T {
        var lastError: Throwable? = null
        repeat(DOWNLOAD_MAX_RETRIES) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastError = e
                if (e is HttpException && e.code == 429) {
                    markSourceRateLimited(sourceId)
                    delay(rateLimitWaitMs(sourceId).coerceAtMost(MAX_RATE_LIMIT_WAIT_MS))
                } else {
                    delay((2L shl attempt) * 1000L)
                }
            }
        }
        throw lastError!!
    }

    /**
     * Re-encola UNA vez un capítulo caído a ERROR por fallo de red, con espera.
     * Solo fallos de red: un error de otro tipo no se reintenta. Devuelve true
     * si se programó el re-encolado.
     */
    private fun requeueChapterAfterNetworkFailure(
        download: MangaDownload,
        error: Throwable?,
    ): Boolean {
        if (!isNetworkRelatedFailure(error)) return false
        return requeueChapterOnce(download)
    }

    /** Programa UN re-encolado del capítulo tras una espera. Devuelve true si se programó. */
    private fun requeueChapterOnce(download: MangaDownload): Boolean {
        val chapterId = download.chapter.id ?: return false
        val retries = networkAutoRetries.getOrDefault(chapterId, 0)
        if (retries >= MAX_NETWORK_AUTO_RETRIES) return false
        networkAutoRetries[chapterId] = retries + 1
        // La espera corre en el scope del descargador, NO en el job del capítulo:
        // el planificador cancela ese job en cuanto el capítulo pasa a ERROR, así
        // que un delay dentro de downloadChapter moriría cancelado y el
        // re-encolado no llegaría nunca.
        val waitMs = rateLimitWaitMs(download.source.id)
            .coerceAtMost(MAX_RATE_LIMIT_WAIT_MS)
            .coerceAtLeast(NETWORK_AUTO_RETRY_DELAY_MS)
        scope.launch {
            delay(waitMs)
            // Si el usuario quitó el capítulo de la cola o lo reintentó a mano
            // mientras tanto, no se toca nada.
            val current = queueState.value.firstOrNull { it.chapter.id == chapterId }
            if (current?.status != MangaDownload.State.ERROR) return@launch
            removeFromQueue(download)
            addAllToQueue(listOf(download))
            // Si el descargador se paró mientras tanto (p. ej. era el último
            // capítulo pendiente), se vuelve a arrancar para que el reintento
            // corra. Si el usuario lo pausó, no se arranca: queda en cola.
            if (!isPaused && !isRunning) {
                MangaDownloadJob.start(context)
                start(notifyOnNetworkBlock = false)
            }
        }
        return true
    }

    /**
     * Whether the downloader is running.
     */
    val isRunning: Boolean
        get() = downloaderJob?.isActive ?: false

    /**
     * Whether the downloader is paused
     */
    @Volatile
    var isPaused: Boolean = false

    init {
        // v7.9.28: migración única del 2 -> 5 de la concurrencia de páginas
        // (ver DownloadPreferences.migratePageDownloadConcurrency): sin esto
        // las instalaciones existentes seguían en 2 aunque la 7.9.27 cambió
        // el default, y el manga bajaba «a la misma velocidad».
        downloadPreferences.migratePageDownloadConcurrency()
        launchNow {
            // restore() blocks on per-item DB lookups (runBlocking inside); keep that off the
            // main thread - the downloader is constructed eagerly at app start.
            val chapters = async(Dispatchers.IO) { store.restore() }
            addAllToQueue(chapters.await())
            // v7.9.27: la cola restaurada se reanuda sola al abrir la app (antes los
            // ítems quedaban en QUEUE parados hasta que el usuario volviera a tocar
            // «Descargar»). Solo arranca si hay algo pendiente; el aviso de red se
            // omite aquí porque el arranque es automático, no una acción del usuario.
            if (queueState.value.any { it.status == MangaDownload.State.QUEUE }) {
                MangaDownloadJob.start(context)
                start(notifyOnNetworkBlock = false)
            }
        }
    }

    /**
     * Starts the downloader. It doesn't do anything if it's already running or there isn't anything
     * to download.
     *
     * @return true if the downloader is started, false otherwise.
     */
    @Synchronized
    fun start(notifyOnNetworkBlock: Boolean = true): Boolean {
        // Network gate: startDownloads() and its callers (queue UI, entry/updates screens, the
        // engine facade) kick the downloader in-process, bypassing MangaDownloadJob whose
        // WIFI/CONNECTED constraints are the only network policy on the worker path. With
        // "download only over Wi-Fi" on a metered network the job stays ENQUEUED - so its
        // pauseForNetwork never runs - while an ungated start() consumed mobile data; offline it
        // produced a burst of per-chapter ERRORs. The worker calls start() again once its
        // constraints are met, so gating here loses nothing.
        val networkStatus = context.activeNetworkState()
            .toDownloadNetworkStatus(downloadPreferences.downloadOnlyOverWifi().get())
        if (networkStatus != DownloadNetworkStatus.Available) {
            // v7.9.27: antes devolvía false en silencio y el usuario solo veía la
            // descarga parada. Ahora se avisa con la notificación que ya existía
            // para este caso («solo WiFi» / «sin red»).
            if (notifyOnNetworkBlock) {
                val reason = when (networkStatus) {
                    DownloadNetworkStatus.NoWifi -> context.getString(R.string.download_notifier_text_only_wifi)
                    DownloadNetworkStatus.NoNetwork -> context.getString(R.string.download_notifier_no_network)
                    else -> return false
                }
                notifier.onWarning(reason, WARNING_NOTIF_TIMEOUT_MS)
            }
            return false
        }

        clearCompletedDownloads()
        if (isRunning || queueState.value.isEmpty()) {
            return false
        }

        val pending = queueState.value.filter { it.status != MangaDownload.State.DOWNLOADED }
        if (pending.isEmpty()) {
            return false
        }
        pending.forEach { if (it.status != MangaDownload.State.QUEUE) it.status = MangaDownload.State.QUEUE }

        isPaused = false

        launchDownloaderJob()

        return true
    }

    /**
     * Stops the downloader.
     */
    fun stop(reason: String? = null) {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == MangaDownload.State.DOWNLOADING }
            .forEach { it.status = MangaDownload.State.ERROR }

        if (reason != null) {
            notifier.onWarning(reason)
            return
        }

        if (isPaused && queueState.value.isNotEmpty()) {
            notifier.onPaused()
        } else {
            notifier.onComplete()
        }

        isPaused = false

        MangaDownloadJob.stop(context)
    }

    /**
     * Pauses the downloader
     */
    fun pause() {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == MangaDownload.State.DOWNLOADING }
            .forEach { it.status = MangaDownload.State.QUEUE }
        isPaused = true
    }

    /**
     * Pauses active downloads while the worker waits for network recovery.
     */
    fun pauseForNetwork(reason: String) {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == MangaDownload.State.DOWNLOADING }
            .forEach { it.status = MangaDownload.State.QUEUE }
        notifier.onWarning(reason)
    }

    /**
     * Removes everything from the queue.
     */
    fun clearQueue() {
        cancelDownloaderJob()

        internalClearQueue()
        notifier.dismissProgress()
    }

    fun clearCompletedDownloads() {
        removeFromQueueIf {
            it.status == MangaDownload.State.DOWNLOADED
        }
    }

    /**
     * Prepares the subscriptions to start downloading.
     */
    private fun launchDownloaderJob() {
        if (isRunning) return

        downloaderJob = scope.launch {
            val activeDownloadsFlow = queueState.transformLatest { queue ->
                while (true) {
                    // "Maximum downloads" preference (download_slots): chapters per source running
                    // at once. Wired for real here (issue #210) — it used to be UI-only dead.
                    val chaptersPerSource = downloadPreferences.numberOfDownloads().get().coerceIn(1, 5)
                    val activeDownloads = queue.asSequence()
                        .filter {
                            it.status.value <= MangaDownload.State.DOWNLOADING.value
                        } // Ignore completed downloads, leave them in the queue
                        .groupBy { it.source }
                        .toList().take(5) // Concurrently download from 5 different sources
                        .flatMap { (_, downloads) -> downloads.take(chaptersPerSource) }
                    emit(activeDownloads)

                    if (activeDownloads.isEmpty()) break
                    // Suspend until one of the active downloads leaves the runnable states,
                    // so the scheduler can immediately pick the next queued chapter.
                    val activeDownloadsSettledFlow =
                        combine(activeDownloads.map(MangaDownload::statusFlow)) { states ->
                            states.any { it.value > MangaDownload.State.DOWNLOADING.value }
                        }.filter { it }
                    activeDownloadsSettledFlow.first()
                }

                if (areAllDownloadsFinished()) stop()
            }.distinctUntilChanged()

            // Use supervisorScope to cancel child jobs when the downloader job is cancelled
            supervisorScope {
                val downloadJobs = mutableMapOf<MangaDownload, Job>()

                activeDownloadsFlow.collectLatest { activeDownloads ->
                    val downloadJobsToStop = downloadJobs.filter { it.key !in activeDownloads }
                    downloadJobsToStop.forEach { (download, job) ->
                        job.cancel()
                        downloadJobs.remove(download)
                    }

                    val downloadsToStart = activeDownloads.filter { it !in downloadJobs }
                    downloadsToStart.forEach { download ->
                        downloadJobs[download] = launchDownloadJob(download)
                    }
                }
            }
        }
    }

    private fun CoroutineScope.launchDownloadJob(download: MangaDownload) = launchIO {
        try {
            downloadChapter(download)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e)
            notifier.onError(e.message)
            stop()
        }
    }

    /**
     * Destroys the downloader subscriptions.
     */
    private fun cancelDownloaderJob() {
        downloaderJob?.cancel()
        downloaderJob = null
    }

    /**
     * Creates a download object for every chapter and adds them to the downloads queue.
     *
     * @param manga the manga of the chapters to download.
     * @param chapters the list of chapters to download.
     * @param autoStart whether to start the downloader after enqueing the chapters.
     */
    fun queueChapters(manga: Manga, chapters: List<Chapter>, autoStart: Boolean) {
        if (chapters.isEmpty()) return

        val source = sourceManager.get(manga.source) as? HttpSource ?: return
        val wasEmpty = queueState.value.isEmpty()
        // v7.9.54: filtrar y agregar bajo el mismo bloqueo — ver queueLock.
        val chaptersToQueue: List<MangaDownload>
        synchronized(queueLock) {
            chaptersToQueue = chapters.asSequence()
                // C-L: deduplicate the input batch itself - queueState is only updated after the
                // whole list is mapped, so duplicates within one call both passed the enqueue filter.
                .distinctBy { it.id }
                // Filter out those already downloaded.
                .filter { provider.findChapterDir(it.name, it.scanlator, manga.title, manga.id, it.id, source) == null }
                // Add chapters to queue from the start.
                .sortedByDescending { it.sourceOrder }
                // Filter out those already enqueued.
                .filter { chapter -> queueState.value.none { it.chapter.id == chapter.id } }
                // Create a download for each one.
                .map { MangaDownload(source, manga, it) }
                .toList()

            if (chaptersToQueue.isNotEmpty()) {
                addAllToQueue(chaptersToQueue)
            }
        }

        if (chaptersToQueue.isNotEmpty()) {
            // Start downloader if needed
            if (autoStart) {
                val queuedDownloads = queueState.value.count { it: MangaDownload -> it.source !is UnmeteredSource }
                val maxDownloadsFromSource = queueState.value
                    .groupBy { it.source }
                    .filterKeys { it !is UnmeteredSource }
                    .maxOfOrNull { it.value.size }
                    ?: 0
                // v7.9.27: la advertencia de cola grande solo se muestra cuando la
                // cola estaba vacía (era el único caso en el que antes se arrancaba);
                // el arranque en sí ya no depende de wasEmpty: los capítulos nuevos
                // no se quedan en «En cola» parados cuando la cola ya tenía algo.
                if (wasEmpty && (
                    queuedDownloads > DOWNLOADS_QUEUED_WARNING_THRESHOLD ||
                    maxDownloadsFromSource > CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD
                )) {
                    notifier.onWarning(
                        context.stringResource(AYMR.strings.download_queue_size_warning),
                        WARNING_NOTIF_TIMEOUT_MS,
                        NotificationHandler.openUrl(context, MangaLibraryUpdateNotifier.HELP_WARNING_URL),
                    )
                }
                MangaDownloadJob.start(context)
                start()
            }
        }
    }

    /**
     * Downloads a chapter.
     *
     * @param download the chapter to be downloaded.
     */
    private suspend fun downloadChapter(download: MangaDownload) {
        val mangaDir: UniFile
        val chapterDirname: String
        val tmpDir: UniFile
        try {
            mangaDir = provider.getMangaDir(download.manga.title, download.manga.id, download.source)

            val availSpace = DiskUtil.getAvailableStorageSpace(context, mangaDir)
            if (availSpace != -1L && availSpace < MIN_DISK_SPACE) {
                download.status = MangaDownload.State.ERROR
                notifier.onError(
                    context.stringResource(AYMR.strings.download_insufficient_space),
                    download.chapter.name,
                    download.manga.title,
                    download.manga.id,
                )
                return
            }

            chapterDirname = provider.getChapterDirName(
                download.chapter.name,
                download.chapter.scanlator,
                download.chapter.id,
            )
            tmpDir = mangaDir.createDirectory(chapterDirname + TMP_DIR_SUFFIX)!!
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // SAF failures in the preamble (unwritable root, getMangaDir throw, null
            // createDirectory behind the !!) used to escape to the scheduler's catch, which
            // stop()s the ENTIRE queue. Isolate them to this chapter instead.
            logcat(LogPriority.ERROR, error)
            download.status = MangaDownload.State.ERROR
            notifier.onError(error.message, download.chapter.name, download.manga.title, download.manga.id)
            return
        }

        try {
            // v7.9.27: si la fuente está en ventana de 429, se espera a que pase
            // (máx. 60 s) antes de pedir nada más a esa fuente.
            val rateLimitWait = rateLimitWaitMs(download.source.id).coerceAtMost(MAX_RATE_LIMIT_WAIT_MS)
            if (rateLimitWait > 0L) delay(rateLimitWait)

            // If the page list already exists, start from the file
            val pageList = download.pages ?: run {
                // Otherwise, pull page list from network and add them to download object
                // v7.9.27: con reintento 3x (antes un solo tropiezo de red tumbaba
                // el capítulo entero a ERROR).
                val pages = retryNetworkCall(download.source.id) {
                    download.source.getPageList(download.chapter.toSChapter())
                }

                if (pages.isEmpty()) {
                    throw Exception(context.stringResource(MR.strings.page_list_empty_error))
                }
                // Don't trust index from source
                val reIndexedPages = pages.mapIndexed { index, page ->
                    Page(
                        index,
                        page.url,
                        page.imageUrl,
                        page.uri,
                    )
                }
                download.pages = reIndexedPages
                reIndexedPages
            }

            val dataSaver = if (sourcePreferences.dataSaverDownloader().get()) {
                DataSaver(download.source, sourcePreferences)
            } else {
                DataSaver.NoOp
            }
            // v7.9.27: con la fuente en 429 la concurrencia baja temporalmente a 1
            // (freno por fuente); en condiciones normales es el valor de la
            // preferencia (por defecto 5 desde esta versión, antes 2).
            val pageDownloadConcurrency = if (isSourceRateLimited(download.source.id)) {
                1
            } else {
                downloadPreferences.pageDownloadConcurrency().get().coerceIn(1, 10)
            }
            val telemetryLock = Any()
            var chapterDownloadedBytes = 0L
            var lastStatBytes = 0L
            var lastStatTimestampMs = 0L

            // Delete all temporary (unfinished) files
            tmpDir.listFiles()
                ?.filter { it.extension == "tmp" }
                ?.forEach { it.delete() }

            download.status = MangaDownload.State.DOWNLOADING
            download.downloadedBytes = 0L
            download.currentSpeedBytesPerSecond = 0L

            // Start downloading images, consider we can have downloaded images already
            // Concurrently download a configurable number of pages at a time
            pageList.asFlow()
                .flatMapMerge(concurrency = pageDownloadConcurrency) { page ->
                    flow {
                        // Fetch image URL if necessary
                        if (page.imageUrl.isNullOrEmpty()) {
                            page.status = Page.State.LOAD_PAGE
                            try {
                                // v7.9.27: con reintento 3x (antes un fallo aquí
                                // marcaba la página en ERROR sin más).
                                page.imageUrl = retryNetworkCall(download.source.id) {
                                    download.source.getImageUrl(page)
                                }
                            } catch (e: Throwable) {
                                page.status = Page.State.ERROR
                            }
                        }

                        withIOContext {
                            getOrDownloadImage(
                                page = page,
                                download = download,
                                tmpDir = tmpDir,
                                dataSaver = dataSaver,
                            ) { fileSize ->
                                synchronized(telemetryLock) {
                                    chapterDownloadedBytes += fileSize
                                    download.downloadedBytes = chapterDownloadedBytes
                                    val now = System.currentTimeMillis()
                                    if (lastStatTimestampMs > 0L && chapterDownloadedBytes >= lastStatBytes) {
                                        val elapsedMs = now - lastStatTimestampMs
                                        val deltaBytes = chapterDownloadedBytes - lastStatBytes
                                        if (elapsedMs > 0L && deltaBytes >= 0L) {
                                            download.currentSpeedBytesPerSecond = (deltaBytes * 1000L) / elapsedMs
                                        }
                                    }
                                    lastStatBytes = chapterDownloadedBytes
                                    lastStatTimestampMs = now
                                    telemetryEmitter.record(
                                        section = DownloadSection.MANGA,
                                        downloadKey = download.chapter.id.toString(),
                                        bytesDownloaded = chapterDownloadedBytes,
                                        bytesTotal = 0L,
                                        timestampMs = now,
                                    )
                                }
                            }
                        }
                        emit(page)
                    }.flowOn(Dispatchers.IO)
                }
                .collect {
                    // Do when page is downloaded.
                    notifier.onProgressChange(download)
                }

            // Do after download completes
            if (!isDownloadSuccessful(download, tmpDir)) {
                download.status = MangaDownload.State.ERROR
                // v7.9.27: el capítulo puede acabar aquí sin excepción (alguna
                // imagen en ERROR). Si el fallo fue de red, se re-encola una vez
                // en vez de dejarlo en ERROR para siempre.
                val chapterId = download.chapter.id
                if (chapterId != null && networkFailedChapterIds.remove(chapterId)) {
                    requeueChapterOnce(download)
                }
                return
            }

            createComicInfoFile(
                tmpDir,
                download.manga,
                download.chapter,
                download.source,
            )

            // Only rename the directory if it's downloaded
            if (downloadPreferences.saveChaptersAsCBZ().get()) {
                archiveChapter(mangaDir, chapterDirname, tmpDir)
            } else {
                val chapterDir = tmpDir.renameToOrCopy(chapterDirname)
                DiskUtil.createNoMediaFile(chapterDir, context)
            }
            cache.addChapter(chapterDirname, mangaDir, download.manga)

            download.status = MangaDownload.State.DOWNLOADED
            download.currentSpeedBytesPerSecond = 0L
            removeFromQueue(download)
            completionTracker.recordCompletion(DownloadSection.MANGA)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // If the page list threw, it will resume here
            logcat(LogPriority.ERROR, error)
            download.status = MangaDownload.State.ERROR
            notifier.onError(error.message, download.chapter.name, download.manga.title, download.manga.id)
            // v7.9.27: fallo de red -> re-encolar una vez (con espera) en vez de
            // dejar el capítulo en ERROR para siempre. Errores de otro tipo no.
            requeueChapterAfterNetworkFailure(download, error)
        }
    }

    /**
     * Gets the image from the filesystem if it exists or downloads it otherwise.
     *
     * @param page the page to download.
     * @param download the download of the page.
     * @param tmpDir the temporary directory of the download.
     */
    private suspend fun getOrDownloadImage(
        page: Page,
        download: MangaDownload,
        tmpDir: UniFile,
        dataSaver: DataSaver,
        onBytesDownloaded: (Long) -> Unit,
    ) {
        // If the image URL is empty, do nothing
        if (page.imageUrl == null) {
            return
        }

        val digitCount = (download.pages?.size ?: 0).toString().length.coerceAtLeast(3)
        val filename = "%0${digitCount}d".format(Locale.ENGLISH, page.number)
        val tmpFile = tmpDir.findFile("$filename.tmp")

        // Delete temp file if it exists
        tmpFile?.delete()

        // Try to find the image file
        val imageFile = tmpDir.listFiles()?.firstOrNull {
            it.name!!.startsWith("$filename.") ||
                it.name!!.startsWith(
                    "${filename}__001",
                )
        }

        try {
            // If the image is already downloaded, do nothing. Otherwise download from network
            val file = when {
                imageFile != null -> imageFile
                chapterCache.isImageInCache(page.imageUrl!!) -> copyImageFromCache(
                    chapterCache.getImageFile(page.imageUrl!!),
                    tmpDir,
                    filename,
                )
                else -> downloadImage(page, download.source, tmpDir, filename, dataSaver)
            }

            // When the page is ready, set page path, progress (just in case) and status
            splitTallImageIfNeeded(page, tmpDir, digitCount)
            // v7.9.53: las páginas que no se dividieron se guardan como WebP
            // q=80 (mucho más livianas que el JPEG original); las altas ya
            // quedaron como piezas WebP en el paso anterior.
            val finalFile = compressDownloadedPageIfNeeded(file, tmpDir, filename)
            page.uri = finalFile.uri
            page.progress = 100
            page.status = Page.State.READY

            // Emit byte-level telemetry after page download
            val fileSize = finalFile.length()
            if (fileSize > 0L) {
                onBytesDownloaded(fileSize)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            // Mark this page as error and allow to download the remaining
            page.progress = 0
            page.status = Page.State.ERROR
            // v7.9.27: si el fallo fue de red, el capítulo puede acabar en ERROR
            // por isDownloadSuccessful sin lanzar excepción; se registra para
            // re-encolarlo una vez en vez de dejarlo en ERROR para siempre.
            if (isNetworkRelatedFailure(e)) {
                download.chapter.id?.let { networkFailedChapterIds.add(it) }
            }
            notifier.onError(e.message, download.chapter.name, download.manga.title, download.manga.id)
        }
    }

    /**
     * Downloads the image from network to a file in tmpDir.
     *
     * @param page the page to download.
     * @param source the source of the page.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the image.
     */
    private suspend fun downloadImage(
        page: Page,
        source: HttpSource,
        tmpDir: UniFile,
        filename: String,
        dataSaver: DataSaver,
    ): UniFile {
        page.status = Page.State.DOWNLOAD_IMAGE
        page.progress = 0
        return flow {
            val response = source.getImage(page, dataSaver)
            val file = tmpDir.createFile("$filename.tmp")!!
            val completedFile = try {
                throttler.apply {
                    bytesPerSecond(downloadPreferences.downloadSpeedLimit().get().toLong() * 1024)
                }
                val throttledSource = throttler.source(response.body.source()).buffer()
                throttledSource.saveTo(file.openOutputStream())
                throttledSource.close()
                val extension = getImageExtension(response, file)
                file.renameToOrCopy("$filename.$extension")
            } catch (e: Exception) {
                response.close()
                file.delete()
                throw e
            }
            emit(completedFile)
        }
            // Retry 3 times, waiting 2, 4 and 8 seconds between attempts.
            // v7.9.27: un 429 marca la fuente como limitada y espera su ventana
            // (15/30/45 s) en vez de insistir: el nuevo paralelismo (5 imágenes)
            // nunca martillea una fuente que pide aflojar.
            .retryWhen { cause, attempt ->
                if (attempt < 3) {
                    if (cause is HttpException && cause.code == 429) {
                        markSourceRateLimited(source.id)
                        delay((15L * (attempt + 1)) * 1000L)
                    } else {
                        delay((2L shl attempt.toInt()) * 1000)
                    }
                    true
                } else {
                    false
                }
            }
            .flowOn(Dispatchers.IO)
            .first()
    }

    /**
     * Copies the image from cache to file in tmpDir.
     *
     * @param cacheFile the file from cache.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the image.
     */
    private fun copyImageFromCache(cacheFile: File, tmpDir: UniFile, filename: String): UniFile {
        val tmpFile = tmpDir.createFile("$filename.tmp")!!
        cacheFile.inputStream().use { input ->
            tmpFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }
        val extension = ImageUtil.findImageType(cacheFile.inputStream()) ?: return tmpFile
        val imageFile = tmpFile.renameToOrCopy("$filename.${extension.extension}")
        cacheFile.delete()
        return imageFile
    }

    /**
     * Returns the extension of the downloaded image from the network response, or if it's null,
     * analyze the file. If everything fails, assume it's a jpg.
     *
     * @param response the network response of the image.
     * @param file the file where the image is already downloaded.
     */
    private fun getImageExtension(response: Response, file: UniFile): String {
        val mime = response.body.contentType()?.run { if (type == "image") "image/$subtype" else null }
        return ImageUtil.getExtensionFromMimeType(mime) { file.openInputStream() }
    }

    private fun splitTallImageIfNeeded(page: Page, tmpDir: UniFile, digitCount: Int) {
        if (!downloadPreferences.splitTallImages().get()) return

        try {
            // NEW-14: the page prefix MUST use the same digit count as saveTo ("%03d" hardcoded
            // here vs digitCount>=4 for >999-page chapters): the prefix of page 5 ("005") did not
            // match its own file ("0005.jpg") but DID match page 50's ("0050.jpg") - the split
            // ran on a foreign page, produced "005__*" files and desynced isDownloadSuccessful
            // into a permanent chapter ERROR.
            val filenamePrefix = "%0${digitCount}d".format(Locale.ENGLISH, page.number)
            val imageFile = tmpDir.listFiles()?.firstOrNull { it.name.orEmpty().startsWith(filenamePrefix) }
                ?: error(context.stringResource(MR.strings.download_notifier_split_page_not_found, page.number))

            // If the original page was previously split, then skip
            if (imageFile.name.orEmpty().startsWith("${filenamePrefix}__")) return

            ImageUtil.splitTallImage(tmpDir, imageFile, filenamePrefix)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to split downloaded image" }
        }
    }

    /**
     * v7.9.53: re-encodes a downloaded page to WebP q=80 so downloads weigh
     * much less on the phone. Only for pages that were NOT split into pieces
     * (tall pages are already WebP pieces); skips WebP originals, animated
     * images, non-JPEG/PNG formats, and images too large to decode safely.
     * The original is only replaced when the WebP is actually smaller.
     * Returns the file to use (the new .webp, or the original when skipped).
     * Runs on the IO dispatcher (the downloader already runs there).
     */
    private fun compressDownloadedPageIfNeeded(file: UniFile, tmpDir: UniFile, filename: String): UniFile {
        // The original was deleted by the split (tall page) — nothing to compress.
        if (!file.exists()) return file
        return try {
            val type = file.openInputStream().use { ImageUtil.findImageType(it) } ?: return file
            // Only JPEG/PNG are worth re-encoding; WebP is already compact and
            // GIF/AVIF/etc. are either animated or already efficient.
            if (type != ImageUtil.ImageType.JPEG && type != ImageUtil.ImageType.PNG) return file
            // Read dimensions without loading pixels; skip images too large to
            // decode safely (a very tall strip is handled by the split above).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            file.openInputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
            val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || pixels > MAX_COMPRESS_PIXELS) return file
            val bitmap = file.openInputStream().use { BitmapFactory.decodeStream(it) } ?: return file
            val webpFile = tmpDir.createFile("$filename.webp") ?: run { bitmap.recycle(); return file }
            try {
                webpFile.openOutputStream().use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.WEBP, DOWNLOAD_WEBP_QUALITY, out)) {
                        webpFile.delete()
                        return file
                    }
                }
            } finally {
                bitmap.recycle()
            }
            // Only replace the original when the WebP is actually smaller.
            if (webpFile.length() >= file.length()) {
                webpFile.delete()
                return file
            }
            file.delete()
            webpFile
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to compress downloaded page, keeping original" }
            file
        }
    }

    /**
     * Checks if the download was successful.
     *
     * @param download the download to check.
     * @param tmpDir the directory where the download is currently stored.
     */
    private fun isDownloadSuccessful(
        download: MangaDownload,
        tmpDir: UniFile,
    ): Boolean {
        // Page list hasn't been initialized
        val downloadPageCount = download.pages?.size ?: return false
        // Ensure that all pages have been downloaded
        if (download.downloadedImages != downloadPageCount) {
            return false
        }
        // Ensure that the chapter folder has all the pages
        val countedFiles = tmpDir.listFiles().orEmpty().filter {
            val fileName = it.name.orEmpty()
            when {
                fileName in listOf(COMIC_INFO_FILE, NOMEDIA_FILE) -> false
                fileName.endsWith(".tmp") -> false
                // Only count the first split page and not the others
                // (v7.9.45: split pieces are now .webp; keep .jpg for chapters
                // downloaded before this change)
                fileName.contains("__") && !fileName.endsWith("__001.jpg") && !fileName.endsWith("__001.webp") -> false
                else -> true
            }
        }
        if (countedFiles.size != downloadPageCount) {
            return false
        }
        // C-M10: the count is not the content - an HTML error page served with HTTP 200 used to
        // be saved as "001.jpg", pass the name-based count and keep the chapter "downloaded"
        // forever. Validate the magic bytes of every counted file instead.
        return countedFiles.all { file ->
            runCatching {
                file.openInputStream().use { ImageUtil.findImageType(it) != null }
            }.getOrDefault(false)
        }
    }

    /**
     * Archive the chapter pages as a CBZ.
     */
    private fun archiveChapter(
        mangaDir: UniFile,
        dirname: String,
        tmpDir: UniFile,
    ) {
        val zip = mangaDir.createFile("$dirname.cbz$TMP_DIR_SUFFIX")!!
        ZipWriter(context, zip).use { writer ->
            tmpDir.listFiles()?.forEach { file ->
                writer.write(file)
            }
        }
        // C-M2: raw/SAF rename silently REPLACES an existing file (POSIX semantics): a colliding
        // dirname destroyed an already-downloaded chapter. With id-suffixed names (DECISION-6)
        // collisions are practically gone; refuse instead of clobbering if the target exists.
        if (mangaDir.findFile("$dirname.cbz") != null) {
            zip.delete()
            error("Target archive already exists: $dirname.cbz")
        }
        zip.renameToOrCopy("$dirname.cbz")
        tmpDir.delete()
    }

    /**
     * Creates a ComicInfo.xml file inside the given directory.
     */
    private suspend fun createComicInfoFile(
        dir: UniFile,
        manga: Manga,
        chapter: Chapter,
        source: HttpSource,
    ) {
        val categories = getCategories.await(manga.id).map { it.name.trim() }.takeUnless { it.isEmpty() }
        val urls = getMangaTracks.await(manga.id)
            .mapNotNull { track ->
                track.remoteUrl.takeUnless { url -> url.isBlank() }?.trim()
            }
            .plus(source.getChapterUrl(chapter.toSChapter()).trim())
            .distinct()

        val comicInfo = getComicInfo(
            manga,
            chapter,
            urls,
            categories,
            source.name,
        )

        // Remove the old file
        dir.findFile(COMIC_INFO_FILE)?.delete()
        dir.createFile(COMIC_INFO_FILE)!!.openOutputStream().use {
            val comicInfoString = xml.encodeToString(ComicInfo.serializer(), comicInfo)
            it.write(comicInfoString.toByteArray())
        }
    }

    /**
     * Returns true if all the queued downloads are in DOWNLOADED or ERROR state.
     */
    private fun areAllDownloadsFinished(): Boolean {
        return queueState.value.none { it.status.value <= MangaDownload.State.DOWNLOADING.value }
    }

    private fun addAllToQueue(downloads: List<MangaDownload>) {
        _queueState.update {
            downloads.forEach { download ->
                download.status = MangaDownload.State.QUEUE
            }
            store.addAll(downloads)
            it + downloads
        }
    }

    private fun removeFromQueue(download: MangaDownload) {
        _queueState.update {
            store.remove(download)
            if (download.status == MangaDownload.State.DOWNLOADING || download.status == MangaDownload.State.QUEUE) {
                download.status = MangaDownload.State.NOT_DOWNLOADED
            }
            it - download
        }
    }

    private inline fun removeFromQueueIf(predicate: (MangaDownload) -> Boolean) {
        _queueState.update { queue ->
            val downloads = queue.filter { predicate(it) }
            store.removeAll(downloads)
            downloads.forEach { download ->
                if (download.status == MangaDownload.State.DOWNLOADING ||
                    download.status == MangaDownload.State.QUEUE
                ) {
                    download.status = MangaDownload.State.NOT_DOWNLOADED
                }
            }
            queue - downloads
        }
    }

    fun removeFromQueue(chapters: List<Chapter>) {
        val chapterIds = chapters.map { it.id }
        removeFromQueueIf { it.chapter.id in chapterIds }
    }

    fun removeFromQueue(manga: Manga) {
        removeFromQueueIf { it.manga.id == manga.id }
    }

    private fun internalClearQueue() {
        _queueState.update {
            it.forEach { download ->
                if (download.status == MangaDownload.State.DOWNLOADING ||
                    download.status == MangaDownload.State.QUEUE
                ) {
                    download.status = MangaDownload.State.NOT_DOWNLOADED
                }
            }
            store.clear()
            emptyList()
        }
    }

    @Synchronized
    fun updateQueue(downloads: List<MangaDownload>) {
        val wasRunning = isRunning

        if (downloads.isEmpty()) {
            clearQueue()
            stop()
            return
        }

        pause()
        internalClearQueue()
        addAllToQueue(downloads)

        if (wasRunning) {
            start()
        }
    }

    companion object {
        const val TMP_DIR_SUFFIX = "_tmp"
        const val WARNING_NOTIF_TIMEOUT_MS = 30_000L
        const val CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 15
        private const val DOWNLOADS_QUEUED_WARNING_THRESHOLD = 30

        // v7.9.27: constantes de la mejora de descarga.
        private const val DOWNLOAD_MAX_RETRIES = 3
        private const val MAX_NETWORK_AUTO_RETRIES = 1
        private const val NETWORK_AUTO_RETRY_DELAY_MS = 10_000L
        private const val RATE_LIMIT_WINDOW_SECONDS = 60L
        private const val MAX_RATE_LIMIT_WAIT_MS = 60_000L

        // v7.9.53: calidad WebP para las páginas descargadas (mucho más
        // livianas que el JPEG original, sin diferencia visible en el teléfono).
        private const val DOWNLOAD_WEBP_QUALITY = 80

        // v7.9.53: tope de píxeles para re-codificar una página descargada
        // (12 MP ~ 48 MB como ARGB); las tiras muy altas ya van por el split.
        private const val MAX_COMPRESS_PIXELS = 12_000_000L
    }
}

// Arbitrary minimum required space to start a download: 200 MB
private const val MIN_DISK_SPACE = 200L * 1024 * 1024
