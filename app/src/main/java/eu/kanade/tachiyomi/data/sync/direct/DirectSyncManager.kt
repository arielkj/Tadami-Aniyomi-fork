package eu.kanade.tachiyomi.data.sync.direct

import android.content.Context
import android.net.Uri
import android.util.Base64
import eu.kanade.domain.sync.DeviceSyncPreferences
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeCategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeSourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaCategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaSourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.NovelBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.NovelCategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.NovelSourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.restore.BackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import eu.kanade.tachiyomi.data.sync.SyncData
import eu.kanade.tachiyomi.data.sync.service.SyncService
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.novel.interactor.GetNovelFavorites
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.random.Random

/**
 * v7.9.36 — Direct device-to-device library sync (no account, no server).
 *
 * Transport: one device hosts [DirectSyncServer] on the local WiFi, the other connects
 * with [DirectSyncClient]. Payload: the same [SyncData]/[Backup] the Google Drive sync
 * uses, so the merge engine in [SyncService] (newest version wins per entry, chapters
 * and episodes merged, furthest progress kept) is reused as-is. Custom covers travel
 * as base64 in the payload because the backup format does not carry them.
 *
 * - First sync sends the whole selected library; later syncs send only entries whose
 *   fingerprint changed since the last successful sync (deltas).
 * - Conflict rule: newest change wins per entry; reading progress takes the furthest
 *   chapter/episode read (handled by the merge engine).
 * - Downloads are NOT synced (too heavy); each device downloads its own.
 * - Manual mode: nothing runs until the user taps "Sincronizar ahora".
 *   Auto mode: the server starts on app launch and a sync to the paired device is
 *   attempted when both are on the same WiFi. No other background work.
 */
sealed interface DirectSyncState {
    data object Idle : DirectSyncState
    data object Running : DirectSyncState
    data class Success(val message: String) : DirectSyncState
    data class Error(val message: String) : DirectSyncState
}

class DirectSyncService(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
    private val client: DirectSyncClient,
    private val peerAddress: () -> String?,
    private val peerCode: () -> String,
) : SyncService(context, json, syncPreferences) {

    override suspend fun doSync(syncData: SyncData): Backup? {
        val address = peerAddress() ?: return null
        val payload = DirectSyncPayload(
            deviceId = syncPreferences.uniqueDeviceID(),
            deviceName = android.os.Build.MODEL,
            sentAt = System.currentTimeMillis(),
            syncData = syncData,
        )
        val merged = client.exchange(address, peerCode(), syncPreferences.uniqueDeviceID(), payload)
        return merged.syncData.backup
    }

    fun merge(local: SyncData, remote: SyncData): SyncData = mergeSyncData(local, remote)
}

class DirectSyncManager(
    private val context: Context,
    private val prefs: DeviceSyncPreferences = Injekt.get(),
    private val syncPrefs: SyncPreferences = Injekt.get(),
    private val protoBuf: ProtoBuf = Injekt.get(),
    private val getAnimeFavorites: GetAnimeFavorites = Injekt.get(),
    private val getMangaFavorites: GetMangaFavorites = Injekt.get(),
    private val getNovelFavorites: GetNovelFavorites = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val getMangaCategories: GetMangaCategories = Injekt.get(),
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
    private val animeCoverCache: AnimeCoverCache = Injekt.get(),
    private val mangaCoverCache: MangaCoverCache = Injekt.get(),
    private val novelCoverCache: NovelCoverCache = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
) {
    // v7.9.39: no shared BackupRestorer field — it carries mutable progress
    // state that interleaves when the server handler and the client apply
    // run at the same time. restoreBackup() builds a fresh one per call
    // (same construction as BackupRestoreJob/SyncManager, isSync = true).
    // (v7.9.37: BackupRestorer is NOT registered in Injekt anywhere, so it
    // is built by hand here instead of Injekt.get(), which used to crash
    // the sync screen on open.)

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val client = DirectSyncClient(json)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<DirectSyncState>(DirectSyncState.Idle)
    val state: StateFlow<DirectSyncState> = _state.asStateFlow()

    private var server: DirectSyncServer? = null

    val deviceId: String get() = syncPrefs.uniqueDeviceID()
    val deviceName: String get() = prefs.deviceName().get()

    fun ensurePairCode(): String {
        val existing = prefs.pairCode().get()
        if (existing.isNotBlank()) return existing
        val code = (100000 + Random.nextInt(900000)).toString()
        prefs.pairCode().set(code)
        return code
    }

    fun regeneratePairCode(): String {
        val code = (100000 + Random.nextInt(900000)).toString()
        prefs.pairCode().set(code)
        return code
    }

    fun isServerRunning(): Boolean = server?.isRunning == true

    fun localAddressText(): String? {
        val server = server ?: return null
        if (!server.isRunning) return null
        val address = server.localAddress() ?: return null
        return "$address:${server.port}"
    }

    fun startServer() {
        ensurePairCode()
        if (server?.isRunning == true) return
        val srv = DirectSyncServer(prefs, json, deviceId, ::handleExchange)
        srv.start()
        server = srv
    }

    fun stopServer() {
        server?.stop()
        server = null
    }

    /** Called once on app launch (from MainActivity). Auto mode only. */
    fun onAppLaunch() {
        if (prefs.mode().get() != DeviceSyncPreferences.MODE_AUTO) return
        scope.launch {
            runCatching { startServer() }
            val address = prefs.pairedDeviceAddress().get()
            val code = prefs.pairedDeviceCode().get()
            if (address.isNotBlank() && code.isNotBlank()) {
                runCatching { syncTo(address, code, silent = true) }
            }
        }
    }

    /**
     * One-time pairing from this device to the peer at [address] using the code shown on the peer.
     * v7.9.38 — pairing is ONE-SIDED: only this device types anything. The
     * handshake sends this device's own WiFi address and its own pairing
     * code, so the peer links itself automatically and can sync back
     * without typing anything. Returns "ok", "invalid_address" (the typed
     * address cannot be used — e.g. a port above 65535) or "failed".
     */
    suspend fun pair(address: String, code: String): String = withContext(Dispatchers.IO) {
        val normalized = DirectSyncAddress.normalize(address) ?: return@withContext "invalid_address"
        // The peer needs OUR address to link back: start our server first.
        startServer()
        val ownAddress = localAddressText().orEmpty()
        val ownCode = ensurePairCode()
        val info = runCatching { client.info(normalized) }.getOrNull() ?: return@withContext "failed"
        val response = runCatching {
            client.pair(normalized, code.trim(), deviceId, deviceName, ownAddress, ownCode)
        }.getOrNull() ?: return@withContext "failed"
        if (!response.ok) return@withContext "failed"
        prefs.pairedDeviceId().set(response.deviceId.ifBlank { info.deviceId })
        prefs.pairedDeviceName().set(response.deviceName.ifBlank { info.deviceName })
        prefs.pairedDeviceAddress().set(normalized)
        prefs.pairedDeviceCode().set(code.trim())
        "ok"
    }

    /** Manual "Sincronizar ahora": syncs with the paired device. */
    suspend fun syncNow(): DirectSyncState {
        // v7.9.38: always sync to the STORED linked-device address (the
        // address field on screen is only for pairing). Normalize it here
        // too: an address stored by v7.9.36/37 without a port (bare IP)
        // gets the default port instead of silently becoming port 80.
        val address = DirectSyncAddress.normalize(prefs.pairedDeviceAddress().get())
            ?: return DirectSyncState.Error("invalid_address")
        val code = prefs.pairedDeviceCode().get()
        if (code.isBlank()) {
            return DirectSyncState.Error("no_paired_device")
        }
        return syncTo(address, code, silent = false)
    }

    suspend fun syncTo(address: String, code: String, silent: Boolean): DirectSyncState {
        val normalized = DirectSyncAddress.normalize(address)
            ?: return DirectSyncState.Error("invalid_address").also {
                if (!silent) _state.value = it
            }
        if (!silent) _state.value = DirectSyncState.Running
        // Build phase: prepare our payload. A failure here is ours, not the
        // peer's — report it as "build_failed" instead of letting it fall
        // through to the generic "sync_failed" (v7.9.39).
        val payload: DirectSyncPayload
        try {
            payload = buildPayload()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "DirectSync: cannot build payload for $normalized" }
            val state = DirectSyncState.Error("build_failed|${e::class.simpleName}")
            if (!silent) _state.value = state
            return state
        }
        // Exchange phase: send it and get the merged payload back. Any
        // failure here is classified so the screen shows the real cause
        // instead of a generic "sync_failed".
        val merged: DirectSyncPayload
        try {
            merged = client.exchange(normalized, code, deviceId, payload)
            markSent(payload)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "DirectSync: sync with $normalized failed" }
            val state = DirectSyncState.Error(classifySyncError(e))
            if (!silent) _state.value = state
            return state
        }
        // Apply phase: merge the reply into our library. A failure here
        // means the exchange worked but the data could not be applied.
        return try {
            val counts = applyMerged(merged)
            prefs.lastSyncAt().set(System.currentTimeMillis())
            val message = "synced:${counts.manga + counts.anime + counts.novel}"
            if (!silent) _state.value = DirectSyncState.Success(message)
            logcat { "DirectSync: sync with $normalized OK ($message)" }
            DirectSyncState.Success(message)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "DirectSync: sync with $normalized failed applying the reply" }
            val state = DirectSyncState.Error("apply_failed")
            if (!silent) _state.value = state
            state
        }
    }

    /**
     * v7.9.38/39 — turns a sync failure into a specific, diagnosable code
     * (the screen shows a sentence for each one). The error state carries
     * "code" or "code|detail"; the screen splits on "|".
     *
     * v7.9.39 diagnosis (why the generic "sync_failed" survived v7.9.38):
     * the old classifier only covered failures the CLIENT could see. Two
     * holes swallowed everything else: (1) the server closed the socket
     * with NO response whenever its exchange handler threw, so the client
     * got an EOF/IOException that mapped to nothing; (2) the client's
     * `check(response.isSuccessful)` turned any non-401 HTTP status into an
     * IllegalStateException that also mapped to nothing. And the old 200 s
     * call timeout fired as InterruptedIOException — also unmapped. All
     * three now have codes (server_error / timeout), and the server
     * answers 500 with the step + exception instead of hanging up.
     * "sync_failed" is only a last resort now, and it carries the
     * exception name with it.
     */
    private fun classifySyncError(e: Exception): String {
        return when {
            e is DirectSyncFailure -> if (e.detail.isBlank()) e.code else "${e.code}|${e.detail}"
            e is IllegalArgumentException && e.message == "invalid_address" -> "invalid_address"
            e is java.net.UnknownHostException -> "unreachable_host"
            e is java.net.ConnectException || e is java.net.SocketTimeoutException -> "cannot_connect"
            e is java.io.InterruptedIOException -> "timeout"
            e is java.io.EOFException -> "server_error|connection_closed"
            e is java.io.IOException && (e.message?.contains("unexpected end of stream") == true) -> "server_error|connection_closed"
            e is IllegalStateException && (e.message?.contains("401") == true) -> "wrong_code"
            e is kotlinx.serialization.SerializationException -> "bad_response"
            e is IllegalArgumentException -> "bad_payload"
            else -> "sync_failed|${e::class.simpleName}"
        }
    }

    /** Server side: merge the incoming payload with our library, apply it, return the merged payload. */
    private suspend fun handleExchange(incoming: DirectSyncPayload): DirectSyncPayload {
        val local = step("build") { buildPayload() }
        val service = DirectSyncService(
            context,
            json,
            syncPrefs,
            client,
            peerAddress = { null },
            peerCode = { "" },
        )
        val mergedSyncData = step("merge") { service.merge(local.syncData, incoming.syncData) }
        val mergedBackup = mergedSyncData.backup
        if (mergedBackup != null) {
            step("restore") { restoreBackup(mergedBackup) }
        }
        step("covers") { applyCovers(incoming.covers) }
        markSent(local)
        prefs.lastSyncAt().set(System.currentTimeMillis())
        val mergedPayload = DirectSyncPayload(
            deviceId = deviceId,
            deviceName = deviceName,
            sentAt = System.currentTimeMillis(),
            syncData = mergedSyncData,
            covers = local.covers,
        )
        logcat { "DirectSync: exchange from ${incoming.deviceName} applied (${mergedBackup?.backupManga?.size} manga, ${mergedBackup?.backupAnime?.size} anime, ${mergedBackup?.backupNovel?.size} novel)" }
        return mergedPayload
    }

    /**
     * v7.9.39 — tags a failure inside the server-side exchange with the
     * step that threw ("build"/"merge"/"restore"/"covers"). The server
     * sends the message back as HTTP 500 detail, so the phone shows what
     * actually failed instead of the generic "sync_failed".
     */
    private suspend fun <T> step(name: String, block: suspend () -> T): T {
        try {
            return block()
        } catch (e: Exception) {
            throw IllegalStateException("$name: ${e::class.simpleName}: ${e.message}", e)
        }
    }

    private data class AppliedCounts(val manga: Int, val anime: Int, val novel: Int)

    /** Client side: apply the merged payload received from the peer. */
    private suspend fun applyMerged(merged: DirectSyncPayload): AppliedCounts {
        val backup = merged.syncData.backup
        if (backup != null) restoreBackup(backup)
        applyCovers(merged.covers)
        return AppliedCounts(
            manga = backup?.backupManga?.size ?: 0,
            anime = backup?.backupAnime?.size ?: 0,
            novel = backup?.backupNovel?.size ?: 0,
        )
    }

    private suspend fun restoreBackup(backup: Backup) {
        val bytes = protoBuf.encodeToByteArray(Backup.serializer(), backup)
        // v7.9.39: unique file per call — the old fixed name was shared by
        // the server handler and the client apply, so two overlapping
        // exchanges clobbered each other's file.
        val file = File(context.cacheDir, "direct-sync-backup-${System.nanoTime()}.tachibk")
        withContext(Dispatchers.IO) {
            GZIPOutputStream(file.outputStream()).use { it.write(bytes) }
        }
        // Fresh restorer per call (see the field comment above): the shared
        // one carried mutable progress state across concurrent restores.
        val restorer = BackupRestorer(
            context = context,
            notifier = BackupNotifier(context),
            isSync = true,
        )
        restorer.restore(Uri.fromFile(file), RestoreOptions())
        runCatching { file.delete() }
    }

    // ---- Payload building (selected folders + delta fingerprints + custom covers) ----

    private val mangaBackupCreator = MangaBackupCreator()
    private val animeBackupCreator = AnimeBackupCreator()
    private val novelBackupCreator = NovelBackupCreator()
    private val mangaCategoriesBackupCreator = MangaCategoriesBackupCreator()
    private val animeCategoriesBackupCreator = AnimeCategoriesBackupCreator()
    private val novelCategoriesBackupCreator = NovelCategoriesBackupCreator()
    private val mangaSourcesBackupCreator = MangaSourcesBackupCreator()
    private val animeSourcesBackupCreator = AnimeSourcesBackupCreator()
    private val novelSourcesBackupCreator = NovelSourcesBackupCreator()

    private fun loadFingerprints(): MutableMap<String, String> {
        return runCatching {
            json.decodeFromString<Map<String, String>>(prefs.fingerprintsJson().get())
        }.getOrNull()?.toMutableMap() ?: mutableMapOf()
    }

    private fun saveFingerprints(fingerprints: Map<String, String>) {
        prefs.fingerprintsJson().set(json.encodeToString(fingerprints))
    }

    private fun fingerprintManga(manga: BackupManga): String {
        val chaptersHash = manga.chapters.joinToString(";") {
            "${it.url}:${it.read}:${it.lastPageRead}:${it.bookmark}"
        }.hashCode()
        return "${manga.version}|${manga.lastModifiedAt}|${manga.favoriteModifiedAt}|$chaptersHash"
    }

    private fun fingerprintAnime(anime: BackupAnime): String {
        val episodesHash = anime.episodes.joinToString(";") {
            "${it.url}:${it.seen}:${it.lastSecondSeen}:${it.bookmark}"
        }.hashCode()
        return "${anime.version}|${anime.lastModifiedAt}|${anime.favoriteModifiedAt}|$episodesHash"
    }

    private fun fingerprintNovel(novel: BackupNovel): String {
        val chaptersHash = novel.chapters.joinToString(";") {
            "${it.url}:${it.read}:${it.lastPageRead}:${it.bookmark}"
        }.hashCode()
        return "${novel.version}|${novel.lastModifiedAt}|${novel.favoriteModifiedAt}|$chaptersHash"
    }

    private suspend fun buildPayload(): DirectSyncPayload {
        val options = BackupOptions()
        val fingerprints = loadFingerprints()
        val lastSync = prefs.lastSyncAt().get()

        val animeCategories = getAnimeCategories.await()
        val mangaCategories = getMangaCategories.await()
        val novelCategories = getNovelCategories.await()
        val animeOrderToId = animeCategories.associate { it.order to it.id }
        val mangaOrderToId = mangaCategories.associate { it.order to it.id }
        val novelOrderToId = novelCategories.associate { it.order to it.id }
        val excludedAnime = prefs.excludedAnimeCategories().get().mapNotNull { it.toLongOrNull() }.toSet()
        val excludedManga = prefs.excludedMangaCategories().get().mapNotNull { it.toLongOrNull() }.toSet()
        val excludedNovel = prefs.excludedNovelCategories().get().mapNotNull { it.toLongOrNull() }.toSet()

        // An entry with no categories (uncategorized) always syncs; otherwise it syncs
        // when at least one of its folders is selected.
        fun syncsManga(manga: BackupManga): Boolean {
            if (manga.categories.isEmpty()) return true
            return manga.categories.any { order -> mangaOrderToId[order]?.let { it !in excludedManga } == true }
        }
        fun syncsAnime(anime: BackupAnime): Boolean {
            if (anime.categories.isEmpty()) return true
            return anime.categories.any { order -> animeOrderToId[order]?.let { it !in excludedAnime } == true }
        }
        fun syncsNovel(novel: BackupNovel): Boolean {
            if (novel.categories.isEmpty()) return true
            return novel.categories.any { order -> novelOrderToId[order]?.let { it !in excludedNovel } == true }
        }

        val allManga = mangaBackupCreator(getMangaFavorites.await(), options)
        val allAnime = animeBackupCreator(getAnimeFavorites.await(), options)
        val allNovel = novelBackupCreator(getNovelFavorites.await(), options)

        val manga = allManga.filter { syncsManga(it) && fingerprintManga(it) != fingerprints["manga|${it.source}|${it.url}"] }
        val anime = allAnime.filter { syncsAnime(it) && fingerprintAnime(it) != fingerprints["anime|${it.source}|${it.url}"] }
        val novel = allNovel.filter { syncsNovel(it) && fingerprintNovel(it) != fingerprints["novel|${it.source}|${it.url}"] }

        val backup = Backup(
            backupManga = manga,
            backupAnime = anime,
            backupNovel = novel,
            backupCategories = mangaCategoriesBackupCreator(),
            backupAnimeCategories = animeCategoriesBackupCreator(),
            backupNovelCategories = novelCategoriesBackupCreator(),
            backupSources = mangaSourcesBackupCreator(manga),
            backupAnimeSources = animeSourcesBackupCreator(anime),
            backupNovelSources = novelSourcesBackupCreator(novel),
            // v7.9.39: native backups are not legacy — the default (true)
            // used to travel in our payload and confuse origin detection.
            isLegacy = false,
        )

        // Custom covers: send the file bytes when the cover changed since the last sync
        // (or on the first sync, when there is no last sync yet).
        val covers = ArrayList<DirectSyncCover>()
        val mangaByKey = getMangaFavorites.await().associateBy { "${it.source}|${it.url}" }
        val animeByKey = getAnimeFavorites.await().associateBy { "${it.source}|${it.url}" }
        val novelByKey = getNovelFavorites.await().associateBy { "${it.source}|${it.url}" }
        for (entry in manga) {
            val local = mangaByKey["${entry.source}|${entry.url}"] ?: continue
            val file = mangaCoverCache.getCustomCoverFile(local.id)
            if (file.exists() && (lastSync == 0L || file.lastModified() > lastSync)) {
                readCoverBytes(file)?.let {
                    covers.add(DirectSyncCover("manga", entry.source, entry.url, it, file.lastModified()))
                }
            }
        }
        for (entry in anime) {
            val local = animeByKey["${entry.source}|${entry.url}"] ?: continue
            val file = animeCoverCache.getCustomCoverFile(local.id)
            if (file.exists() && (lastSync == 0L || file.lastModified() > lastSync)) {
                readCoverBytes(file)?.let {
                    covers.add(DirectSyncCover("anime", entry.source, entry.url, it, file.lastModified()))
                }
            }
        }
        for (entry in novel) {
            val local = novelByKey["${entry.source}|${entry.url}"] ?: continue
            val file = novelCoverCache.getCustomCoverFile(local.id)
            if (file.exists() && (lastSync == 0L || file.lastModified() > lastSync)) {
                readCoverBytes(file)?.let {
                    covers.add(DirectSyncCover("novel", entry.source, entry.url, it, file.lastModified()))
                }
            }
        }

        return DirectSyncPayload(
            deviceId = deviceId,
            deviceName = deviceName,
            sentAt = System.currentTimeMillis(),
            syncData = SyncData(deviceId, backup),
            covers = covers,
        )
    }

    private fun readCoverBytes(file: File): String? {
        return try {
            if (file.length() > 2_000_000) return null
            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "DirectSync: cannot read cover ${file.name}" }
            null
        }
    }

    /** After a successful exchange, remember the fingerprints of everything we just sent. */
    private fun markSent(payload: DirectSyncPayload) {
        val backup = payload.syncData.backup ?: return
        val fingerprints = loadFingerprints()
        for (manga in backup.backupManga) fingerprints["manga|${manga.source}|${manga.url}"] = fingerprintManga(manga)
        for (anime in backup.backupAnime) fingerprints["anime|${anime.source}|${anime.url}"] = fingerprintAnime(anime)
        for (novel in backup.backupNovel) fingerprints["novel|${novel.source}|${novel.url}"] = fingerprintNovel(novel)
        saveFingerprints(fingerprints)
    }

    private suspend fun applyCovers(covers: List<DirectSyncCover>) {
        if (covers.isEmpty()) return
        val mangaByKey = getMangaFavorites.await().associateBy { "${it.source}|${it.url}" }
        val animeByKey = getAnimeFavorites.await().associateBy { "${it.source}|${it.url}" }
        val novelByKey = getNovelFavorites.await().associateBy { "${it.source}|${it.url}" }
        for (cover in covers) {
            try {
                val bytes = Base64.decode(cover.base64, Base64.DEFAULT)
                when (cover.kind) {
                    "manga" -> {
                        val local = mangaByKey["${cover.source}|${cover.url}"] ?: continue
                        mangaCoverCache.setCustomCoverToCache(local, ByteArrayInputStream(bytes))
                        updateManga.awaitUpdateCoverLastModified(local.id)
                    }
                    "anime" -> {
                        val local = animeByKey["${cover.source}|${cover.url}"] ?: continue
                        animeCoverCache.setCustomCoverToCache(local, ByteArrayInputStream(bytes))
                        updateAnime.awaitUpdateCoverLastModified(local.id)
                    }
                    "novel" -> {
                        val local = novelByKey["${cover.source}|${cover.url}"] ?: continue
                        novelCoverCache.setCustomCoverToCache(local, ByteArrayInputStream(bytes))
                        updateNovel.awaitUpdateCoverLastModified(local.id)
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "DirectSync: cannot apply cover ${cover.kind} ${cover.url}" }
            }
        }
    }
}
