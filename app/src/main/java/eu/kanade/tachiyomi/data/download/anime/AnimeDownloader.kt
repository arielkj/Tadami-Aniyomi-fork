package eu.kanade.tachiyomi.data.download.anime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.LogRedirectionStrategy
import com.arthenica.ffmpegkit.StatisticsCallback
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.DownloadNetworkStatus
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.engine.DownloadCompletionTracker
import eu.kanade.tachiyomi.data.download.engine.DownloadSection
import eu.kanade.tachiyomi.data.download.engine.DownloadTelemetryEmitter
import eu.kanade.tachiyomi.data.download.isNetworkRelatedFailure
import eu.kanade.tachiyomi.data.download.toDownloadNetworkStatus
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateNotifier
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.ui.player.torrent.TorrentPlaybackResolver
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.toFFmpegString
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.R
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.renameToOrCopy
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * This class is the one in charge of downloading episodes.
 *
 * Its queue contains the list of episodes to download. In order to download them, the downloader
 * subscription must be running and the list of episodes must be sent to them by [downloaderJob].
 *
 * The queue manipulation must be done in one thread (currently the main thread) to avoid unexpected
 * behavior, but it's safe to read it from multiple threads.
 */
class AnimeDownloader(
    private val context: Context,
    private val provider: AnimeDownloadProvider,
    private val cache: AnimeDownloadCache,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    var telemetryEmitter: DownloadTelemetryEmitter = DownloadTelemetryEmitter.NOOP,
    var completionTracker: DownloadCompletionTracker = DownloadCompletionTracker(),
) {
    /**
     * Store for persisting downloads across restarts.
     */
    private val store = AnimeDownloadStore(context)

    /**
     * Queue where active downloads are kept.
     */
    private val _queueState = MutableStateFlow<List<AnimeDownload>>(emptyList())
    val queueState = _queueState.asStateFlow()

    /**
     * Notifier for the downloader state and progress.
     */
    private val notifier by lazy { AnimeDownloadNotifier(context) }

    /**
     * Coroutine scope used for download job scheduling
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Job object for download queue management
     */
    private var downloaderJob: Job? = null

    // v7.9.27: freno por fuente. Si un hoster responde 429, la descarga de esa
    // fuente afloja sola (el usuario advirtió que las fuentes pueden bloquear
    // el acceso si se hacen muchas descargas a la vez). Mapa fuente -> instante
    // (ms) en el que se puede volver a intentar.
    private val rateLimitedUntilBySource = ConcurrentHashMap<Long, Long>()

    // v7.9.27: episodios re-encolados automáticamente UNA vez tras un fallo de
    // red (clave = id del episodio, valor = intentos ya usados).
    private val networkAutoRetries = ConcurrentHashMap<Long, Int>()

    // v7.9.27: URL del vídeo al que pertenece el .tmp parcial de cada episodio
    // (clave = id del episodio). Solo se reanuda si la URL coincide: si el
    // hoster resolvió otro vídeo, el parcial viejo se borra y se baja de cero.
    // En memoria: al reiniciar la app se pierde y el primer intento es fresco.
    private val partialVideoUrlByEpisode = ConcurrentHashMap<Long, String>()

    // v7.9.27: tamaño total conocido de la descarga directa de cada episodio
    // (clave = id del episodio). Si el parcial ya llegó a ese tamaño, la
    // descarga está completa y un reintento pasa directo al remux sin bajar nada.
    private val directTotalBytesByEpisode = ConcurrentHashMap<Long, Long>()

    private fun isSourceRateLimited(sourceId: Long): Boolean =
        (rateLimitedUntilBySource[sourceId] ?: 0L) > System.currentTimeMillis()

    private fun rateLimitWaitMs(sourceId: Long): Long =
        ((rateLimitedUntilBySource[sourceId] ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    private fun markSourceRateLimited(sourceId: Long, windowSeconds: Long = RATE_LIMIT_WINDOW_SECONDS) {
        rateLimitedUntilBySource[sourceId] =
            System.currentTimeMillis() + windowSeconds * 1000L
    }

    /**
     * Reintenta una operación de red (resolución del hoster) hasta 3 veces con
     * backoff 2/4/8 s. Un 429 marca la fuente como limitada y espera su ventana
     * (máx. 60 s) antes de seguir.
     */
    private suspend fun <T> retryNetworkCall(sourceId: Long, block: suspend () -> T): T {
        var lastError: Throwable? = null
        repeat(DOWNLOAD_MAX_RETRIES.toInt()) { attempt ->
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
     * Re-encola UNA vez un episodio caído a ERROR por fallo de red, con espera.
     * Solo fallos de red: un error de otro tipo no se reintenta. Devuelve true
     * si se programó el re-encolado.
     */
    private fun requeueEpisodeAfterNetworkFailure(
        download: AnimeDownload,
        error: Throwable?,
    ): Boolean {
        if (!isNetworkRelatedFailure(error)) return false
        val episodeId = download.episode.id ?: return false
        val retries = networkAutoRetries.getOrDefault(episodeId, 0)
        if (retries >= MAX_NETWORK_AUTO_RETRIES) return false
        networkAutoRetries[episodeId] = retries + 1
        // La espera corre en el scope del descargador, NO en el job del episodio:
        // el planificador cancela ese job en cuanto el episodio pasa a ERROR, así
        // que un delay dentro de downloadEpisode moriría cancelado y el
        // re-encolado no llegaría nunca.
        val waitMs = rateLimitWaitMs(download.source.id)
            .coerceAtMost(MAX_RATE_LIMIT_WAIT_MS)
            .coerceAtLeast(NETWORK_AUTO_RETRY_DELAY_MS)
        scope.launch {
            delay(waitMs)
            // Si el usuario quitó el episodio de la cola o lo reintentó a mano
            // mientras tanto, no se toca nada.
            val current = queueState.value.firstOrNull { it.episode.id == episodeId }
            if (current?.status != AnimeDownload.State.ERROR) return@launch
            removeFromQueue(download)
            addAllToQueue(listOf(download))
            // Acotado a un reintento por episodio: no puede girar en bucle
            // stop() -> requeue -> start() -> stop() (ver el comentario de stop()).
            // Si el usuario lo pausó, no se arranca: queda en cola.
            if (!isPaused && !isRunning) {
                AnimeDownloadJob.start(context)
                start(notifyOnNetworkBlock = false)
            }
        }
        return true
    }

    /**
     * Preference for user's choice of external downloader
     */
    private val preferences: DownloadPreferences by injectLazy()
    private val torrentPlaybackResolver by lazy { TorrentPlaybackResolver(context.contentResolver) }

    /**
     * Whether the downloader is running.
     */
    val isRunning: Boolean
        get() = downloaderJob?.isActive ?: false

    /**
     * Whether the downloader is paused (v7.9.27: espejo de MangaDownloader, para
     * que el re-encolado automático no rearranque tras una pausa del usuario).
     */
    @Volatile
    var isPaused: Boolean = false

    init {
        scope.launch {
            val episodes = async { store.restore() }
            addAllToQueue(episodes.await())
            // v7.9.27: la cola restaurada se reanuda sola al abrir la app (antes
            // los ítems quedaban en QUEUE parados hasta que el usuario volviera
            // a tocar «Descargar»). Solo arranca si hay algo pendiente; el aviso
            // de red se omite aquí porque el arranque es automático.
            if (queueState.value.any { it.status == AnimeDownload.State.QUEUE }) {
                AnimeDownloadJob.start(context)
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
        // Same network gate as MangaDownloader.start() (C-H1, fork-shared pattern): the
        // in-process startDownloads() path bypasses AnimeDownloadJob whose WIFI/CONNECTED
        // constraints are the only network policy on the worker path, and the worker's
        // pauseForNetwork never runs while it stays ENQUEUED. The worker calls start() again
        // once its constraints are met, so gating here loses nothing.
        val networkStatus = context.activeNetworkState()
            .toDownloadNetworkStatus(preferences.downloadOnlyOverWifi().get())
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

        val pending = queueState.value.filter { it.status != AnimeDownload.State.DOWNLOADED }
        if (pending.isEmpty()) {
            return false
        }
        pending.forEach { if (it.status != AnimeDownload.State.QUEUE) it.status = AnimeDownload.State.QUEUE }

        isPaused = false

        launchDownloaderJob()

        return true
    }

    /**
     * Stops the downloader.
     */
    fun stop(reason: String? = null) {
        cancelDownloaderJob()
        // IMPORTANT: interrupted downloads must end in a terminal state (ERROR) here.
        // Putting them back in QUEUE makes areAllAnimeDownloadsFinished() never return true,
        // so stop() -> requeue -> start() -> stop() spins forever and keeps cancelling and
        // re-enqueueing the worker. Resumable parking belongs to pause()/pauseForNetwork(),
        // which do not cancel the unique work.
        queueState.value
            .filter { it.status == AnimeDownload.State.DOWNLOADING }
            .forEach {
                it.status = AnimeDownload.State.ERROR
                it.currentSpeedBytesPerSecond = 0L
            }

        if (reason != null) {
            notifier.onWarning(reason)
            return
        }

        if (queueState.value.isNotEmpty()) {
            notifier.onPaused()
        } else {
            notifier.onComplete()
        }

        AnimeDownloadJob.stop(context)
    }

    /**
     * Pauses the downloader
     */
    fun pause() {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == AnimeDownload.State.DOWNLOADING }
            .forEach {
                it.status = AnimeDownload.State.QUEUE
                it.currentSpeedBytesPerSecond = 0L
            }
        isPaused = true
    }

    /**
     * Pauses active downloads while the worker waits for network recovery.
     */
    fun pauseForNetwork(reason: String) {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == AnimeDownload.State.DOWNLOADING }
            .forEach {
                it.status = AnimeDownload.State.QUEUE
                it.currentSpeedBytesPerSecond = 0L
            }
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
            it.status == AnimeDownload.State.DOWNLOADED
        }
    }

    /**
     * Prepares the jobs to start downloading.
     */
    private fun launchDownloaderJob() {
        if (isRunning) return

        downloaderJob = scope.launch {
            val activeDownloadsFlow = queueState.transformLatest { queue ->
                while (true) {
                    val activeDownloads = queue.asSequence()
                        .filter {
                            it.status.value <= AnimeDownload.State.DOWNLOADING.value
                        } // Ignore completed downloads, leave them in the queue
                        .groupBy { it.source }
                        .toList().take(MAX_CONCURRENT_SOURCES) // Concurrently download from N sources
                        .map { (_, downloads) -> downloads.first() }
                    emit(activeDownloads)

                    if (activeDownloads.isEmpty()) break

                    // Suspend until one of the active downloads leaves the runnable states,
                    // so the scheduler can immediately pick the next queued episode.
                    val activeDownloadsSettledFlow =
                        combine(activeDownloads.map(AnimeDownload::statusFlow)) { states ->
                            states.any { it.value > AnimeDownload.State.DOWNLOADING.value }
                        }.filter { it }
                    activeDownloadsSettledFlow.first()
                }

                if (areAllAnimeDownloadsFinished()) stop()
            }.distinctUntilChanged()

            // Use supervisorScope to cancel child jobs when the downloader job is cancelled
            supervisorScope {
                val downloadJobs = mutableMapOf<AnimeDownload, Job>()

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

    /**
     * Launch the job responsible for download a single video
     */
    private fun CoroutineScope.launchDownloadJob(download: AnimeDownload) = launchIO {
        // This try-catch manages the job cancellation
        try {
            downloadEpisode(download)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e)
            // Only fail this episode. Previously a single failure called stop(), which cancelled
            // the WorkManager job and killed every other in-flight download.
            download.status = AnimeDownload.State.ERROR
            download.currentSpeedBytesPerSecond = 0L
            notifier.onError(
                e.message,
                download.episode.name,
                download.anime.title,
                download.anime.id,
            )
            if (areAllAnimeDownloadsFinished()) stop()
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
     * Creates a download object for every episode and adds them to the downloads queue.
     *
     * @param anime the anime of the episodes to download.
     * @param episodes the list of episodes to download.
     * @param autoStart whether to start the downloader after enqueing the episodes.
     */
    fun queueEpisodes(
        anime: Anime,
        episodes: List<Episode>,
        autoStart: Boolean,
        changeDownloader: Boolean = false,
        video: Video? = null,
    ) {
        if (episodes.isEmpty()) return

        val source = sourceManager.get(anime.source) as? AnimeHttpSource ?: return
        val wasEmpty = queueState.value.isEmpty()

        val episodesToQueue = episodes.asSequence()
            // Filter out those already downloaded.
            .filter { provider.findEpisodeDir(it.name, it.scanlator, anime.title, source) == null }
            // Add episodes to queue from the start.
            .sortedByDescending { it.sourceOrder }
            // Filter out those already enqueued.
            .filter { episode -> queueState.value.none { it.episode.id == episode.id } }
            // Create a download for each one.
            .map { AnimeDownload(source, anime, it, changeDownloader, video) }
            .toList()

        if (episodesToQueue.isNotEmpty()) {
            addAllToQueue(episodesToQueue)

            // Start downloader if needed
            if (autoStart) {
                val queuedDownloads =
                    queueState.value.count { it: AnimeDownload -> it.source !is UnmeteredSource }
                val maxDownloadsFromSource = queueState.value
                    .groupBy { it.source }
                    .filterKeys { it !is UnmeteredSource }
                    .maxOfOrNull { it.value.size }
                    ?: 0
                // TODO: show warnings in stable
                // v7.9.27: la advertencia de cola grande solo se muestra cuando la
                // cola estaba vacía (era el único caso en el que antes se arrancaba);
                // el arranque en sí ya no depende de wasEmpty: los episodios nuevos
                // no se quedan en «En cola» parados cuando la cola ya tenía algo.
                if (wasEmpty && (
                    queuedDownloads > DOWNLOADS_QUEUED_WARNING_THRESHOLD ||
                    maxDownloadsFromSource > EPISODES_PER_SOURCE_QUEUE_WARNING_THRESHOLD
                )) {
                    notifier.onWarning(
                        context.stringResource(AYMR.strings.download_queue_size_warning),
                        WARNING_NOTIF_TIMEOUT_MS,
                        NotificationHandler.openUrl(
                            context,
                            AnimeLibraryUpdateNotifier.HELP_WARNING_URL,
                        ),
                    )
                }
                // Adding episodes to the queue must never cancel the worker that is currently
                // downloading, otherwise the running episode restarts from 0%.
                AnimeDownloadJob.start(context, keepExistingWork = isRunning)
                start()
            }
        }
    }

    /**
     * Download the video associated with download object
     *
     * @param download the episode to be downloaded.
     */
    private suspend fun downloadEpisode(download: AnimeDownload) {
        val animeDir = provider.getAnimeDir(download.anime.title, download.source)

        val availSpace = DiskUtil.getAvailableStorageSpace(context, animeDir)
        if (availSpace != -1L && availSpace < MIN_DISK_SPACE) {
            download.status = AnimeDownload.State.ERROR
            notifier.onError(
                context.stringResource(AYMR.strings.download_insufficient_space),
                download.episode.name,
                download.anime.title,
                download.anime.id,
            )
            return
        }

        val episodeDirname = provider.getEpisodeDirName(download.episode.name, download.episode.scanlator)
        val tmpDir = animeDir.createDirectory(episodeDirname + TMP_DIR_SUFFIX)!!
        // Keep the media scanner out of the partial download directory, otherwise it keeps trying
        // to index the growing/short-lived .tmp file and floods the log with NoSuchFileException.
        DiskUtil.createNoMediaFile(tmpDir, context)

        try {
            // v7.9.27: si la fuente está en ventana de 429, se espera a que pase
            // (máx. 60 s) antes de pedir nada más a esa fuente.
            val rateLimitWait = rateLimitWaitMs(download.source.id).coerceAtMost(MAX_RATE_LIMIT_WAIT_MS)
            if (rateLimitWait > 0L) delay(rateLimitWait)

            if (download.video == null) {
                // Pull video from network and add them to download object
                // v7.9.27: la resolución del hoster se reintenta 3x con backoff
                // (antes un solo fallo dejaba el episodio en ERROR sin bajar nada).
                val hosters = retryNetworkCall(download.source.id) {
                    EpisodeLoader.getHosters(download.episode, download.anime, download.source)
                }
                if (hosters.isEmpty()) {
                    throw Exception(context.stringResource(AYMR.strings.video_list_empty_error))
                }
                val bestVideo = retryNetworkCall(download.source.id) {
                    HosterLoader.getBestVideo(download.source, hosters)
                } ?: throw Exception(context.stringResource(AYMR.strings.video_list_empty_error))
                download.video = bestVideo
            }

            withIOContext {
                getOrDownloadVideoFile(download, tmpDir)
            }

            if (!isDownloadSuccessful(download, tmpDir)) {
                download.status = AnimeDownload.State.ERROR
                return
            }

            val filename = DiskUtil.buildValidFilename("${download.anime.title} - ${download.episode.name}")
            tmpDir.findFile("${filename}_tmp.mkv")?.delete()
            val episodeDir = tmpDir.renameToOrCopy(episodeDirname)

            cache.addEpisode(episodeDirname, animeDir, download.anime)

            DiskUtil.createNoMediaFile(episodeDir, context)

            download.status = AnimeDownload.State.DOWNLOADED
            download.currentSpeedBytesPerSecond = 0L
            removeFromQueue(download)
            completionTracker.recordCompletion(DownloadSection.ANIME)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // If the video threw, it will resume here
            logcat(LogPriority.ERROR, error)
            download.status = AnimeDownload.State.ERROR
            notifier.onError(
                error.message,
                download.episode.name,
                download.anime.title,
                download.anime.id,
            )
            // v7.9.27: fallo de red -> re-encolar una vez (con espera) en vez de
            // dejar el episodio en ERROR para siempre. Errores de otro tipo no.
            requeueEpisodeAfterNetworkFailure(download, error)
        }
    }

    /**
     * Gets the video file if already downloaded, otherwise downloads it
     *
     * @param download the download of the video.
     * @param tmpDir the temporary directory of the download.
     */
    private suspend fun getOrDownloadVideoFile(
        download: AnimeDownload,
        tmpDir: UniFile,
    ) {
        val video = download.video!!

        video.status = Video.State.LOAD_VIDEO

        if (torrentPlaybackResolver.isTorrentLikeUrl(video.videoUrl)) {
            video.videoUrl = torrentPlaybackResolver.resolve(
                videoUrl = video.videoUrl,
                title = "${download.anime.title} - ${download.episode.name}",
            )
        }

        var progressJob: Job? = null

        // Get filename from download info
        val filename = DiskUtil.buildValidFilename(download.episode.name)

        // v7.9.27: ya NO se borra el .tmp parcial aquí. Para MP4/WebM directos el
        // parcial se reanuda con Range (downloadVideo decide si vale o se borra
        // porque el vídeo cambió); borrarlo aquí reiniciaba cada reintento de 0%.
        // La ruta HLS/FFmpeg sigue borrando su parcial al empezar (legacy).

        // Try to find the video file
        val videoFile = tmpDir.listFiles()?.firstOrNull { it.name!!.startsWith("$filename.mkv") }

        try {
            // If the video is already downloaded, do nothing. Otherwise download from network
            val file = when {
                videoFile != null -> videoFile
                else -> {
                    notifier.onProgressChange(download)

                    download.status = AnimeDownload.State.DOWNLOADING
                    download.progress = 0
                    download.downloadedBytes = 0L
                    download.estimatedTotalBytes = 0L
                    download.currentSpeedBytesPerSecond = 0L

                    // If videoFile is not existing then download it
                    if (preferences.useExternalDownloader().get() == download.changeDownloader) {
                        progressJob = scope.launch {
                            while (download.status == AnimeDownload.State.DOWNLOADING) {
                                delay(PROGRESS_NOTIFICATION_INTERVAL_MS)
                                notifier.onProgressChange(download)
                            }
                        }

                        downloadVideo(download, tmpDir, filename)
                    } else {
                        val betterFileName = DiskUtil.buildValidFilename(
                            "${download.anime.title} - ${download.episode.name}",
                        )
                        downloadVideoExternal(download.video!!, download.source, tmpDir, betterFileName)
                    }
                }
            }

            video.videoUrl = file.uri.path ?: ""
            download.progress = 100
            video.status = Video.State.READY
            progressJob?.cancel()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            video.status = Video.State.ERROR
            progressJob?.cancel()
            // Propagate so downloadEpisode() marks the download as failed instead of silently
            // continuing and then failing the opaque "Unable to finalize download" check.
            throw e
        }
    }

    /**
     * Downloads the video, resuming a partial direct file when possible.
     *
     * @param download the download reference
     * @param tmpDir the directory where placing the file
     * @param filename the name to give to download file
     */
    private suspend fun downloadVideo(
        download: AnimeDownload,
        tmpDir: UniFile,
        filename: String,
    ): UniFile {
        // v7.9.27: un reintento ya NO reinicia siempre de 0%. Para MP4/WebM
        // directos el parcial se reanuda con HTTP Range; solo HLS (que no se
        // puede reanudar por segmentos) sigue empezando de cero. Corrupt input
        // sigue fallando en la misma posición, así que ese reintento sigue
        // limitado a un solo pase tolerante.
        var tolerantMode = false
        return flow {
            val video = download.video!!
            val file = if (isDirectFileDownloadEligible(video, tmpDir)) {
                try {
                    downloadDirectFile(download, tmpDir, filename)
                    remuxLocalFile(download, tmpDir, filename, tolerantMode)
                } catch (e: NotDirectFileException) {
                    // El enlace resultó ser una lista HLS pese a la URL: ruta clásica.
                    downloadLegacyVideo(download, tmpDir, filename, tolerantMode)
                }
            } else {
                downloadLegacyVideo(download, tmpDir, filename, tolerantMode)
            }

            emit(file)
        }
            .retryWhen { cause, attempt ->
                if (cause is CancellationException) {
                    return@retryWhen false
                }
                download.currentSpeedBytesPerSecond = 0L

                val isCorruptInput = (cause as? FFmpegException)?.returnCode == AVERROR_INVALID_DATA
                when {
                    // Corrupt/garbage input: give it exactly one more pass with error tolerance
                    // enabled, then give up instead of restarting from 0% over and over.
                    isCorruptInput && !tolerantMode -> {
                        logcat(LogPriority.WARN, cause) {
                            "Corrupt input for ${download.episode.name}, retrying once while skipping bad packets"
                        }
                        tolerantMode = true
                        delay(1000)
                        true
                    }
                    isCorruptInput -> {
                        logcat(LogPriority.ERROR, cause) {
                            "Corrupt input for ${download.episode.name} persists, not restarting again"
                        }
                        false
                    }
                    // v7.9.27: un 429 del hoster marca la fuente como limitada y
                    // espera su ventana (15/30/45 s) en vez de insistir.
                    cause is HttpException && cause.code == 429 -> {
                        markSourceRateLimited(download.source.id)
                        delay((15L * (attempt + 1)) * 1000L)
                        attempt < DOWNLOAD_MAX_RETRIES
                    }
                    attempt >= DOWNLOAD_MAX_RETRIES -> false
                    else -> {
                        val backoffMs = minOf(
                            (2L shl attempt.toInt()) * 1000L,
                            DOWNLOAD_MAX_BACKOFF_MS,
                        )
                        logcat(LogPriority.WARN, cause) {
                            "Download attempt ${attempt + 1} failed, retrying in ${backoffMs}ms"
                        }
                        delay(backoffMs)
                        true
                    }
                }
            }
            .flowOn(Dispatchers.IO)
            .first()
    }

    /**
     * Ruta clásica (HLS o cualquier vídeo no directo): FFmpeg descarga y remuxea
     * en un solo paso. Cada reintento borra el parcial y empieza de cero (HLS no
     * se puede reanudar por segmentos); la re-conexión por segmento la hace el
     * propio FFmpeg (-reconnect / -max_reload en networkResilienceOptions).
     */
    private suspend fun downloadLegacyVideo(
        download: AnimeDownload,
        tmpDir: UniFile,
        filename: String,
        tolerant: Boolean,
    ): UniFile {
        tmpDir.findFile("$filename.tmp")?.delete()
        val videoFile = tmpDir.createFile("$filename.tmp")!!
        try {
            ffmpegDownload(download, tmpDir, videoFile, filename, tolerant = tolerant)
        } catch (e: Exception) {
            videoFile.delete()
            throw e
        }
        return videoFile
    }

    /**
     * ¿El vídeo se puede bajar como archivo directo con OkHttp (con reanudación)?
     * Solo MP4/WebM sin pistas extra y con ruta de archivo local: la reanudación
     * necesita append sobre el archivo real (UniFile no expone append), así que
     * en almacenamiento SAF se usa la ruta clásica. HLS (.m3u8) y los proxies
     * locales (torrent) quedan fuera.
     */
    private fun isDirectFileDownloadEligible(video: Video, tmpDir: UniFile): Boolean {
        val url = video.videoUrl
        if (url.isBlank() || url == "null") return false
        if (url.contains(".m3u8", ignoreCase = true)) return false
        if (video.subtitleTracks.isNotEmpty() || video.audioTracks.isNotEmpty()) return false
        if (url.contains("127.0.0.1") || url.contains("localhost")) return false
        if (tmpDir.filePath.isNullOrBlank()) return false
        return true
    }

    /**
     * Baja un archivo directo (MP4/WebM) con OkHttp, REANUDANDO el parcial con
     * HTTP Range en vez de borrarlo. Si el servidor no acepta Range (200) se
     * baja completo de cero; si el parcial es mayor que el archivo (416) también.
     * Si el Content-Type resulta ser una lista HLS, lanza [NotDirectFileException]
     * y el llamador usa la ruta clásica.
     */
    private suspend fun downloadDirectFile(
        download: AnimeDownload,
        tmpDir: UniFile,
        filename: String,
    ) {
        val video = download.video!!
        val url = video.videoUrl
        val tmpPath = tmpDir.filePath ?: throw NotDirectFileException()
        val tmpFile = File(tmpPath, "$filename.tmp")
        val episodeId = download.episode.id

        // Si el archivo ya está completo (la descarga anterior lo bajó entero pero
        // el remux falló), no se baja nada: se pasa directo al remux.
        val knownTotal = episodeId?.let { directTotalBytesByEpisode[it] }
        if (knownTotal != null && knownTotal > 0L && tmpFile.exists() && tmpFile.length() >= knownTotal) {
            download.downloadedBytes = tmpFile.length()
            download.estimatedTotalBytes = knownTotal
            download.progress = 100
            return
        }

        // ¿El parcial pertenece a este mismo vídeo?
        val resumeFrom = if (episodeId != null && partialVideoUrlByEpisode[episodeId] == url && tmpFile.exists()) {
            tmpFile.length()
        } else {
            // Parcial de otro vídeo (o de una sesión anterior sin registro): fuera.
            if (tmpFile.exists()) tmpFile.delete()
            if (episodeId != null) partialVideoUrlByEpisode[episodeId] = url
            0L
        }
        download.downloadedBytes = resumeFrom
        if (resumeFrom > 0L) download.progress = -1

        // Offset mutable: si el servidor ignora el Range (200) o el parcial no
        // vale (416), se reinicia a 0 antes de leer el cuerpo.
        var resumeOffset = resumeFrom
        var lastUpdateMs = 0L
        val listener = object : ProgressListener {
            override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                val now = System.currentTimeMillis()
                if (done || now - lastUpdateMs >= 200L) {
                    lastUpdateMs = now
                    val downloaded = resumeOffset + bytesRead
                    download.downloadedBytes = downloaded
                    val total = if (resumeOffset > 0L) resumeOffset + contentLength else contentLength
                    download.estimatedTotalBytes = if (total > 0L) total else 0L
                    download.progress = if (total > 0L) (100 * downloaded / total).toInt() else -1
                }
            }
        }

        val source = download.source
        val execute: suspend (okhttp3.Request) -> okhttp3.Response = { request ->
            source.client.newCachelessCallWithProgress(request, listener).awaitSuccess()
        }

        var response = try {
            val request = if (resumeFrom > 0L) {
                source.videoRequest(video, resumeFrom, resumeFrom)
            } else {
                source.safeVideoRequest(video)
            }
            execute(request)
        } catch (e: HttpException) {
            if (e.code == 416 && resumeOffset > 0L) {
                // El parcial es mayor que el archivo (el vídeo cambió en el
                // servidor): se borra y se baja de cero.
                tmpFile.delete()
                resumeOffset = 0L
                execute(source.safeVideoRequest(video))
            } else {
                throw e
            }
        }

        val contentType = response.header("Content-Type") ?: ""
        if (contentType.contains("mpegurl", ignoreCase = true)) {
            response.close()
            throw NotDirectFileException()
        }
        if (response.code == 200 && resumeOffset > 0L) {
            // El servidor ignoró el Range: el cuerpo es el archivo completo.
            tmpFile.delete()
            resumeOffset = 0L
        }
        val remainingLength = response.body.contentLength()
        val expectedTotal = if (remainingLength > 0L) resumeOffset + remainingLength else -1L

        try {
            response.body.byteStream().use { input ->
                FileOutputStream(tmpFile, resumeOffset > 0L).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            response.close()
        }

        if (expectedTotal > 0L && tmpFile.length() < expectedTotal) {
            // La conexión se cortó antes de tiempo: el reintento reanuda desde aquí.
            throw java.io.IOException(
                "incomplete direct download: ${tmpFile.length()} of $expectedTotal bytes",
            )
        }
        if (expectedTotal > 0L) {
            episodeId?.let { directTotalBytesByEpisode[it] = expectedTotal }
            download.estimatedTotalBytes = expectedTotal
            download.progress = 100
        }
    }

    /**
     * Remuxea el archivo directo ya completo a .mkv con `-c copy` (sin
     * re-codificar: es una copia local rápida y además valida el archivo).
     * Devuelve el .mkv. Con `tolerant` se ignoran los paquetes corruptos
     * (segundo pase tras un AVERROR_INVALID_DATA).
     */
    private suspend fun remuxLocalFile(
        download: AnimeDownload,
        tmpDir: UniFile,
        filename: String,
        tolerant: Boolean,
    ): UniFile {
        val tmpPath = tmpDir.filePath ?: throw NotDirectFileException()
        val inputFile = File(tmpPath, "$filename.tmp")
        val outputFile = File(tmpPath, "$filename.mkv")
        if (outputFile.exists()) outputFile.delete()
        val tolerantOptions = if (tolerant) "-err_detect ignore_err -fflags +discardcorrupt " else ""
        val args = FFmpegKitConfig.parseArguments(
            "-nostdin -hide_banner $tolerantOptions-i \"${inputFile.absolutePath}\" " +
                "-map 0 -c copy \"${outputFile.absolutePath}\" -y",
        )
        FFmpegKitConfig.setLogRedirectionStrategy(LogRedirectionStrategy.NEVER_PRINT_LOGS)
        val session = suspendCancellableCoroutine { continuation ->
            val running = FFmpegKit.executeWithArgumentsAsync(
                args,
                { continuation.resume(it) },
                { log ->
                    if (log.level <= Level.AV_LOG_WARNING) {
                        log.message?.let { logcat(LogPriority.WARN) { it } }
                    }
                },
                // El remux no necesita estadísticas de progreso.
                { _ -> },
            )
            continuation.invokeOnCancellation { running.cancel() }
        }
        if (session.returnCode.isValueSuccess) {
            inputFile.delete()
            return tmpDir.findFile("$filename.mkv")!!
        }
        val reason = session.failStackTrace?.takeIf(String::isNotBlank)
            ?: session.allLogsAsString?.trim()?.takeLast(FFMPEG_ERROR_LOG_CHARS)
            ?: "unknown error"
        val exception = FFmpegException(
            session.returnCode?.value ?: 0,
            "ffmpeg remux failed (${session.returnCode}): $reason",
        )
        if (tolerant) {
            // Segundo fallo (ya con tolerancia): el archivo está corrupto de origen.
            // Se borra el parcial para que el próximo intento manual baje de cero.
            inputFile.delete()
            download.episode.id?.let {
                partialVideoUrlByEpisode.remove(it)
                directTotalBytesByEpisode.remove(it)
            }
        }
        throw exception
    }

    // ffmpeg is always on safe mode
    private suspend fun ffmpegDownload(
        download: AnimeDownload,
        tmpDir: UniFile,
        videoFile: UniFile,
        filename: String,
        tolerant: Boolean = false,
    ) {
        val video = download.video!!

        val ffmpegFilename = { videoFile.uri.toFFmpegString(context) }

        val headers = video.headers ?: download.source.headers
        val headerOptions = headers.joinToString("", "-headers '", "'") {
            "${it.first}: ${it.second}\r\n"
        }

        // Only our (de-duplicated) callback should write ffmpeg output to logcat, otherwise
        // ffmpeg-kit prints every single line itself and the throttling above has no effect.
        FFmpegKitConfig.setLogRedirectionStrategy(LogRedirectionStrategy.NEVER_PRINT_LOGS)
        val ffmpegOptions = getFFmpegOptions(video, headerOptions, ffmpegFilename(), tolerant)
        val ffprobeCommand = { file: String, ffprobeHeaders: String? ->
            FFmpegKitConfig.parseArguments(
                "${ffprobeHeaders?.plus(" ") ?: ""}-v quiet -show_entries " +
                    "format=duration -of default=noprint_wrappers=1:nokey=1 \"$file\"",
            )
        }

        var duration = 0L
        var lastStatBytes = 0L
        var lastStatTimestampMs = 0L

        // ffmpeg can emit the same warning many times per second (reconnects, HLS reloads, ...).
        // Printing each one floods logcat, so collapse consecutive duplicates into a counter.
        var lastLogMessage: String? = null
        var repeatedLogCount = 0
        val logCallback = LogCallback { log ->
            if (log.level <= Level.AV_LOG_WARNING) {
                val message = log.message
                if (message != null) {
                    if (message == lastLogMessage) {
                        repeatedLogCount++
                    } else {
                        if (repeatedLogCount > 0) {
                            val skipped = repeatedLogCount
                            logcat(LogPriority.WARN) { "(previous ffmpeg message repeated $skipped more times)" }
                        }
                        lastLogMessage = message
                        repeatedLogCount = 0
                        logcat(LogPriority.WARN) { message }
                    }
                }
            }
        }

        val statCallback = StatisticsCallback { s ->
            val outTime = (s.time / 1000.0).toLong()
            val bytesDownloaded = s.size
            val now = System.currentTimeMillis()

            download.downloadedBytes = bytesDownloaded
            if (lastStatTimestampMs > 0L && bytesDownloaded >= lastStatBytes) {
                val elapsedMs = now - lastStatTimestampMs
                val deltaBytes = bytesDownloaded - lastStatBytes
                if (elapsedMs > 0L && deltaBytes >= 0L) {
                    download.currentSpeedBytesPerSecond = (deltaBytes * 1000L) / elapsedMs
                }
            }
            lastStatBytes = bytesDownloaded
            lastStatTimestampMs = now

            if (duration != 0L && outTime > 0) {
                download.progress = (100 * outTime / duration).toInt()

                // HLS never declares a total size, so extrapolate it from the muxed timeline:
                // bytes so far scaled by (total duration / processed duration). Costs no extra
                // network request and converges on the real size as the download progresses.
                if (bytesDownloaded > 0L) {
                    download.estimatedTotalBytes = bytesDownloaded * duration / outTime
                }

                telemetryEmitter.record(
                    section = DownloadSection.ANIME,
                    downloadKey = download.episode.id.toString(),
                    bytesDownloaded = bytesDownloaded,
                    bytesTotal = download.estimatedTotalBytes,
                    timestampMs = now,
                )
            }
        }

        // A failing ffprobe only means we cannot show a percentage; it must never abort the
        // download itself (common for HLS playlists and streams without a declared duration).
        duration = try {
            getDuration(ffprobeCommand(video.videoUrl, headerOptions))?.toLong() ?: 0L
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Could not probe duration for ${download.episode.name}" }
            0L
        }

        suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(
                ffmpegOptions,
                {
                    if (it.returnCode.isValueSuccess) {
                        tmpDir.findFile("$filename.tmp")?.apply {
                            renameToOrCopy("$filename.mkv")
                        }
                        continuation.resume(it)
                    } else {
                        // Surface the real ffmpeg failure instead of an opaque "Error in ffmpeg!",
                        // and keep the return code so the retry logic can classify it.
                        val reason = it.failStackTrace?.takeIf(String::isNotBlank)
                            ?: it.allLogsAsString?.trim()?.takeLast(FFMPEG_ERROR_LOG_CHARS)
                            ?: "unknown error"
                        continuation.resumeWithException(
                            FFmpegException(
                                it.returnCode?.value ?: 0,
                                "ffmpeg failed (${it.returnCode}): $reason",
                            ),
                        )
                    }
                },
                logCallback,
                statCallback,
            )
            continuation.invokeOnCancellation {
                session.cancel()
            }
        }
    }

    /**
     * Options that make ffmpeg survive flaky networks instead of aborting the whole download on the
     * first dropped connection, stalled socket or 5xx answer.
     */
    private fun networkResilienceOptions(url: String): String {
        val options = mutableListOf<String>()

        // Some sources are streamed through the app's own loopback proxy. HTTP reconnect logic is
        // pointless there (the socket never leaves the device) and only hides real proxy errors.
        val isLoopback = url.contains("127.0.0.1") || url.contains("localhost")
        if (!isLoopback) {
            options += listOf(
                // Recover from dropped connections and transient server errors.
                //
                // WARNING: never add -reconnect_at_eof here. Downloads are finite, so EOF is the
                // normal end of the stream. With that flag ffmpeg retries the same byte offset
                // forever with a zero second delay ("Will reconnect at N in 0 second(s),
                // error=End of file"), which floods the log and means the download never finishes.
                "-reconnect", "1",
                "-reconnect_streamed", "1",
                "-reconnect_on_network_error", "1",
                // Only retryable statuses. 4xx answers are permanent, so retrying them loops.
                "-reconnect_on_http_error", "429,5xx",
                "-reconnect_delay_max", "10",
                // Reuse connections but never hang forever on a dead socket.
                "-multiple_requests", "1",
                "-rw_timeout", SOCKET_TIMEOUT_MICROS.toString(),
            )
        }

        if (url.contains(".m3u8", ignoreCase = true)) {
            options += listOf(
                // Keep reloading the playlist rather than giving up mid-episode.
                "-max_reload",
                "1000",
                "-m3u8_hold_counters",
                "10",
                // Segments are frequently disguised (.jpeg, .png, ...) by the source.
                "-allowed_extensions",
                "ALL",
            )
        }
        return options.joinToString(" ")
    }

    private fun getFFmpegOptions(
        video: Video,
        headerOptions: String,
        ffmpegFilename: String,
        tolerant: Boolean = false,
    ): Array<String> {
        fun formatInputs(tracks: List<Track>) = tracks.joinToString(" ", postfix = " ") {
            buildList {
                if (it.url.startsWith("http")) {
                    add(headerOptions)
                    networkResilienceOptions(it.url).takeIf(String::isNotBlank)?.let(::add)
                }
                add("-i")
                add("\"${it.url}\"")
            }.joinToString(" ")
        }

        fun formatMaps(tracks: List<Track>, type: String, offset: Int = 0) = tracks.indices.joinToString(" ") {
            "-map ${it + 1 + offset}:$type"
        }

        fun formatMetadata(tracks: List<Track>, type: String) = tracks.mapIndexed { i, track ->
            "-metadata:s:$type:$i \"title=${track.lang}\""
        }.joinToString(" ")

        val subtitleInputs = formatInputs(video.subtitleTracks)
        val subtitleMaps = formatMaps(video.subtitleTracks, "s")
        val subtitleMetadata = formatMetadata(video.subtitleTracks, "s")

        val audioInputs = formatInputs(video.audioTracks)
        val audioMaps = formatMaps(video.audioTracks, "a", video.subtitleTracks.size)
        val audioMetadata = formatMetadata(video.audioTracks, "a")

        val sourceStreamOptions = video.ffmpegStreamArgs.joinToString(" ") { (key, value) ->
            "-$key \"$value\""
        }
        val sourceVideoOptions = video.ffmpegVideoArgs.joinToString(" ") { (key, value) ->
            "-$key \"$value\""
        }

        val videoInput = buildList {
            if (video.videoUrl.startsWith("http")) {
                add(headerOptions)
                networkResilienceOptions(video.videoUrl).takeIf(String::isNotBlank)?.let(::add)
            }
            if (tolerant) {
                // Second-chance pass: drop corrupt packets instead of aborting the whole download
                // with AVERROR_INVALIDDATA ("Invalid data found when processing input").
                add("-err_detect ignore_err")
                add("-fflags +discardcorrupt")
            }
            add(sourceStreamOptions)
            add("-i")
            add("\"${video.videoUrl}\"")
        }.joinToString(" ")

        val command = listOf(
            // Never block on stdin and keep the log readable.
            "-nostdin -hide_banner",
            videoInput, subtitleInputs, audioInputs,
            "-map 0:v", audioMaps, "-map 0:a?", subtitleMaps, "-map 0:s? -map 0:t?",
            "-f matroska -c:a copy -c:v copy -c:s copy",
            // Avoid "Too many packets buffered" aborts on streams with sparse audio/subtitles.
            "-max_muxing_queue_size 4096",
            subtitleMetadata, audioMetadata, sourceVideoOptions,
            "\"$ffmpegFilename\" -y",
        )
            .filter(String::isNotBlank)
            .joinToString(" ")

        return FFmpegKitConfig.parseArguments(command)
    }

    private suspend fun getDuration(ffprobeCommand: Array<String>): Float? {
        return suspendCancellableCoroutine { continuation ->
            val session = FFprobeKit.executeWithArgumentsAsync(ffprobeCommand) {
                if (it.returnCode.isValueSuccess) {
                    continuation.resume(it)
                } else {
                    continuation.resumeWithException(Exception(it.output))
                }
            }
            continuation.invokeOnCancellation { session.cancel() }
        }.output.toFloatOrNull()
    }

    /**
     * Returns the observable which downloads the video with an external downloader.
     *
     * @param video the video to download.
     * @param source the source of the video.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the video.
     */
    private suspend fun downloadVideoExternal(
        video: Video,
        source: AnimeHttpSource,
        tmpDir: UniFile,
        filename: String,
    ): UniFile {
        try {
            val file = tmpDir.createFile("${filename}_tmp.mkv")!!
            withUIContext {
                context.copyToClipboard("Episode download location", tmpDir.filePath!!.substringBeforeLast("_tmp"))
            }

            // TODO: support other file formats!!
            // start download with intent
            val pm = context.packageManager
            val pkgName = preferences.externalDownloaderSelection().get()
            val intent: Intent
            if (pkgName.isNotEmpty()) {
                intent = pm.getLaunchIntentForPackage(pkgName) ?: throw Exception(
                    "Launch intent not found",
                )
                when {
                    // 1DM
                    pkgName.startsWith("idm.internet.download.manager") -> {
                        val headers = (video.headers ?: source.headers).toMap()
                        val bundle = Bundle()
                        for ((key, value) in headers) {
                            bundle.putString(key, value)
                        }

                        intent.apply {
                            component = ComponentName(
                                pkgName,
                                "idm.internet.download.manager.Downloader",
                            )
                            action = Intent.ACTION_VIEW
                            data = video.videoUrl.toUri()

                            putExtra("extra_filename", "$filename.mkv")
                            putExtra("extra_headers", bundle)
                        }
                    }
                    // ADM
                    pkgName.startsWith("com.dv.adm") -> {
                        val headers = (video.headers ?: source.headers).toList()
                        val bundle = Bundle()
                        headers.forEach { a ->
                            bundle.putString(
                                a.first,
                                a.second.replace("http", "h_ttp"),
                            )
                        }

                        intent.apply {
                            component = ComponentName(pkgName, "$pkgName.AEditor")
                            action = Intent.ACTION_VIEW
                            putExtra(
                                "com.dv.get.ACTION_LIST_ADD",
                                "${video.videoUrl.toUri()}<info>$filename.mkv",
                            )
                            putExtra(
                                "com.dv.get.ACTION_LIST_PATH",
                                tmpDir.filePath!!.substringBeforeLast("_"),
                            )
                            putExtra("android.media.intent.extra.HTTP_HEADERS", bundle)
                        }
                        file.delete()
                        tmpDir.delete()
                        queueState.value.find { anime -> anime.video == video }?.let { download ->
                            download.status = AnimeDownload.State.DOWNLOADED
                            removeFromQueue(download)
                            if (areAllAnimeDownloadsFinished()) {
                                stop()
                            }
                        }
                    }
                }
            } else {
                intent = Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setDataAndType(video.videoUrl.toUri(), "video/*")
                    putExtra("extra_filename", filename)
                }
            }
            context.startActivity(intent)
            return file
        } catch (e: Exception) {
            tmpDir.findFile("${filename}_tmp.mkv")?.delete()
            throw e
        }
    }

    /**
     * Checks if the download was successful.
     *
     * @param download the download to check.
     * @param tmpDir the directory where the download is currently stored.
     */
    private fun isDownloadSuccessful(
        download: AnimeDownload,
        tmpDir: UniFile,
    ): Boolean {
        // `extension` never contains the leading dot, so the previous `== ".tmp"` check never
        // matched: unfinished .tmp files were counted as finished downloads (and the real video
        // file plus a leftover .tmp made a valid download look broken).
        val downloadedVideo = tmpDir.listFiles().orEmpty()
            .filterNot { it.isDirectory || it.name.equals(NOMEDIA_FILE, ignoreCase = true) }
            .filterNot { it.extension.equals("tmp", ignoreCase = true) }
        return downloadedVideo.size == 1 && downloadedVideo.first().length() > 0L
    }

    /**
     * Checks if the download was successful.
     *
     * @param download the download to check.
     * @param animeDir the anime directory of the download.
     * @param tmpDir the directory where the download is currently stored.
     * @param dirname the real (non temporary) directory name of the download.
     */
    private suspend fun ensureSuccessfulAnimeDownload(
        download: AnimeDownload,
        animeDir: UniFile,
        tmpDir: UniFile,
        dirname: String,
    ) {
        // Ensure that the episode folder has the full video
        val downloadedVideo = tmpDir.listFiles().orEmpty()
            .filterNot { it.isDirectory || it.name.equals(NOMEDIA_FILE, ignoreCase = true) }
            .filterNot { it.extension.equals("tmp", ignoreCase = true) }

        download.status = if (downloadedVideo.size == 1) {
            // Only rename the directory if it's downloaded
            val filename = DiskUtil.buildValidFilename("${download.anime.title} - ${download.episode.name}")
            tmpDir.findFile("${filename}_tmp.mkv")?.delete()
            val episodeDir = tmpDir.renameToOrCopy(dirname)

            cache.addEpisode(dirname, animeDir, download.anime)

            DiskUtil.createNoMediaFile(episodeDir, context)
            AnimeDownload.State.DOWNLOADED
        } else {
            throw Exception("Unable to finalize download")
        }
    }

    /**
     * Returns true if all the queued downloads are in DOWNLOADED or ERROR state.
     */
    private fun areAllAnimeDownloadsFinished(): Boolean {
        return queueState.value.none { it.status.value <= AnimeDownload.State.DOWNLOADING.value }
    }

    private fun addAllToQueue(downloads: List<AnimeDownload>) {
        _queueState.update {
            downloads.forEach { download ->
                download.status = AnimeDownload.State.QUEUE
            }
            store.addAll(downloads)
            it + downloads
        }
    }

    private fun removeFromQueue(download: AnimeDownload) {
        _queueState.update {
            store.remove(download)
            if (download.status == AnimeDownload.State.DOWNLOADING || download.status == AnimeDownload.State.QUEUE) {
                download.status = AnimeDownload.State.NOT_DOWNLOADED
            }
            it - download
        }
    }

    private inline fun removeFromQueueIf(predicate: (AnimeDownload) -> Boolean) {
        _queueState.update { queue ->
            val downloads = queue.filter { predicate(it) }
            store.removeAll(downloads)
            downloads.forEach { download ->
                if (download.status == AnimeDownload.State.DOWNLOADING ||
                    download.status == AnimeDownload.State.QUEUE
                ) {
                    download.status = AnimeDownload.State.NOT_DOWNLOADED
                }
            }
            queue - downloads.toSet()
        }
    }

    fun removeFromQueue(episodes: List<Episode>) {
        val episodeIds = episodes.map { it.id }
        removeFromQueueIf { it.episode.id in episodeIds }
    }

    fun removeFromQueue(anime: Anime) {
        removeFromQueueIf { it.anime.id == anime.id }
    }

    private fun internalClearQueue() {
        _queueState.update {
            it.forEach { download ->
                if (download.status == AnimeDownload.State.DOWNLOADING ||
                    download.status == AnimeDownload.State.QUEUE
                ) {
                    download.status = AnimeDownload.State.NOT_DOWNLOADED
                }
            }
            store.clear()
            emptyList()
        }
    }

    fun updateQueue(downloads: List<AnimeDownload>) {
        if (queueState == downloads) return

        if (downloads.isEmpty()) {
            clearQueue()
            stop()
            return
        }

        val wasRunning = isRunning

        pause()
        internalClearQueue()
        addAllToQueue(downloads)

        if (wasRunning) {
            start()
        }
    }

    /** ffmpeg failure carrying the raw return code so retries can tell corrupt input from I/O. */
    private class FFmpegException(val returnCode: Int, message: String) : Exception(message)

    // v7.9.27: marcador interno — el enlace resultó ser una lista HLS, no un
    // archivo directo. El llamador usa la ruta clásica de FFmpeg.
    private class NotDirectFileException : Exception("not a direct file")

    companion object {
        const val TMP_DIR_SUFFIX = "_tmp"
        const val WARNING_NOTIF_TIMEOUT_MS = 30_000L
        const val EPISODES_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 10
        private const val DOWNLOADS_QUEUED_WARNING_THRESHOLD = 20

        /** Number of different sources downloaded from concurrently. */
        private const val MAX_CONCURRENT_SOURCES = 3

        /** How often the progress notification is refreshed while downloading. */
        private const val PROGRESS_NOTIFICATION_INTERVAL_MS = 500L

        /**
         * Retry attempts for transient failures before an episode is marked as failed. Kept low on
         * purpose: every retry restarts the episode from 0%.
         */
        private const val DOWNLOAD_MAX_RETRIES = 3L

        /** ffmpeg's AVERROR_INVALIDDATA: "Invalid data found when processing input". */
        private const val AVERROR_INVALID_DATA = -1094995529

        /** Upper bound for the exponential retry backoff. */
        private const val DOWNLOAD_MAX_BACKOFF_MS = 60_000L

        // v7.9.27: constantes de la mejora de descarga.
        private const val MAX_NETWORK_AUTO_RETRIES = 1
        private const val NETWORK_AUTO_RETRY_DELAY_MS = 10_000L
        private const val RATE_LIMIT_WINDOW_SECONDS = 60L
        private const val MAX_RATE_LIMIT_WAIT_MS = 60_000L

        /** Socket read/write timeout handed to ffmpeg, in microseconds (30s). */
        private const val SOCKET_TIMEOUT_MICROS = 30_000_000L

        /** How much of the ffmpeg log is kept when reporting a failure. */
        private const val FFMPEG_ERROR_LOG_CHARS = 500

        private const val NOMEDIA_FILE = ".nomedia"
    }
}

// Arbitrary minimum required space to start a download: 200 MB
private const val MIN_DISK_SPACE = 200L * 1024 * 1024
