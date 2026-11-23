package eu.kanade.tachiyomi.data.migration.auto

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.entries.anime.interactor.MigrateAnimeUseCase
import eu.kanade.domain.entries.manga.interactor.MigrateMangaUseCase
import eu.kanade.domain.metadata.interactor.parseOriginalTitle
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorMedia
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorStore
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.coroutines.coroutineContext

/**
 * Background worker for automatic per-source migration.
 *
 * Two phases, never automatic: the SEARCH phase (one run covers ONE
 * origin source, manga or anime) only looks for substitutes — exact
 * title matches are STAGED, everything else lands in the review list.
 * Nothing is migrated until the user taps "Confirmar", which enqueues
 * the APPLY phase: it migrates every staged pair at once. Only runs
 * while the user started it — no always-on background work.
 */
class AutoMigrationJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val store = AutoMigrationStore(applicationContext)
    private val searcher = AutoMigrationSearcher()
    private val migrateManga: MigrateMangaUseCase = MigrateMangaUseCase()
    private val migrateAnime: MigrateAnimeUseCase = MigrateAnimeUseCase()
    private val mangaSourceManager: MangaSourceManager = Injekt.get()
    private val animeSourceManager: AnimeSourceManager = Injekt.get()
    private val getMangaFavorites: GetMangaFavorites = Injekt.get()
    private val getAnimeFavorites: GetAnimeFavorites = Injekt.get()
    private val getManga: GetManga = Injekt.get()
    private val getAnime: GetAnime = Injekt.get()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_AUTO_MIGRATION_PROGRESS) {
            setContentTitle(applicationContext.stringResource(MR.strings.auto_migration_notification_title))
            setSmallIcon(android.R.drawable.stat_sys_download)
        }.build()
        return ForegroundInfo(
            Notifications.ID_AUTO_MIGRATION_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result {
        val isManga = inputData.getBoolean(KEY_IS_MANGA, true)
        val sourceId = inputData.getLong(KEY_SOURCE_ID, 0L)
        val mode = inputData.getString(KEY_MODE) ?: MODE_SEARCH
        if (sourceId == 0L) return Result.failure()

        setForegroundSafely()

        return try {
            if (mode == MODE_APPLY) {
                if (isManga) applyManga(sourceId) else applyAnime(sourceId)
            } else {
                if (isManga) runManga(sourceId) else runAnime(sourceId)
            }
            Result.success()
        } catch (e: CancellationException) {
            store.finishRun(AutoMigrationRunState.STATUS_CANCELLED)
            // Cancel discards the staged run: nothing staged or pending
            // review survives it.
            store.clearRunData()
            postFinishedNotification(cancelled = true)
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Auto-migration failed" }
            store.finishRun(AutoMigrationRunState.STATUS_CANCELLED)
            Result.failure()
        }
    }

    /**
     * APPLY phase (after the user taps "Confirmar"): migrates every
     * staged pair at once with the existing use cases (replace=true,
     * standard flags, old downloads kept). Progress is reported in the
     * same run state so the screen shows "Aplicando… X de Y".
     */
    private suspend fun applyManga(sourceId: Long) {
        val pairs = store.stagedPairsFor(isManga = true, sourceId = sourceId)
        if (pairs.isEmpty()) {
            store.finishRun(AutoMigrationRunState.STATUS_FINISHED)
            return
        }
        store.updateRun {
            it.copy(status = AutoMigrationRunState.STATUS_APPLYING, total = pairs.size, processed = 0, autoMigrated = 0, failed = 0, currentTitle = null)
        }
        postProgressNotification()

        var applied = 0
        var failed = 0
        for ((index, pair) in pairs.withIndex()) {
            coroutineContext.ensureActive()
            store.updateRun { it.copy(processed = index, currentTitle = pair.oldTitle) }
            postProgressNotification()
            try {
                val oldManga = getManga.await(pair.oldEntryId)
                val target = getManga.await(pair.newEntryId)
                if (oldManga == null || target == null) {
                    failed++
                } else {
                    migrateManga.migrateManga(oldManga, target, replace = true, flags = autoMigrationMangaFlags(oldManga))
                    LibraryUpdateErrorStore.markResolved(
                        media = LibraryUpdateErrorMedia.Manga,
                        entryId = oldManga.id,
                    )
                    applied++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Auto-migration apply failed for manga ${pair.oldEntryId}" }
                failed++
            }
            store.removeStagedPair(pair.oldEntryId, isManga = true)
            store.updateRun {
                it.copy(processed = index + 1, autoMigrated = applied, failed = failed, currentTitle = null)
            }
            postProgressNotification()
        }

        store.finishRun(AutoMigrationRunState.STATUS_FINISHED)
        postFinishedNotification(cancelled = false)
    }

    private suspend fun applyAnime(sourceId: Long) {
        val pairs = store.stagedPairsFor(isManga = false, sourceId = sourceId)
        if (pairs.isEmpty()) {
            store.finishRun(AutoMigrationRunState.STATUS_FINISHED)
            return
        }
        store.updateRun {
            it.copy(status = AutoMigrationRunState.STATUS_APPLYING, total = pairs.size, processed = 0, autoMigrated = 0, failed = 0, currentTitle = null)
        }
        postProgressNotification()

        var applied = 0
        var failed = 0
        for ((index, pair) in pairs.withIndex()) {
            coroutineContext.ensureActive()
            store.updateRun { it.copy(processed = index, currentTitle = pair.oldTitle) }
            postProgressNotification()
            try {
                val oldAnime = getAnime.await(pair.oldEntryId)
                val target = getAnime.await(pair.newEntryId)
                if (oldAnime == null || target == null) {
                    failed++
                } else {
                    migrateAnime.migrateAnime(oldAnime, target, replace = true, flags = autoMigrationAnimeFlags(oldAnime))
                    LibraryUpdateErrorStore.markResolved(
                        media = LibraryUpdateErrorMedia.Anime,
                        entryId = oldAnime.id,
                    )
                    applied++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Auto-migration apply failed for anime ${pair.oldEntryId}" }
                failed++
            }
            store.removeStagedPair(pair.oldEntryId, isManga = false)
            store.updateRun {
                it.copy(processed = index + 1, autoMigrated = applied, failed = failed, currentTitle = null)
            }
            postProgressNotification()
        }

        store.finishRun(AutoMigrationRunState.STATUS_FINISHED)
        postFinishedNotification(cancelled = false)
    }

    private suspend fun runManga(sourceId: Long) {
        val source = mangaSourceManager.getOrStub(sourceId)
        val entries = getMangaFavorites.subscribe(sourceId).first()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

        store.startRun(isManga = true, sourceId = sourceId, sourceName = source.name, total = entries.size)
        postProgressNotification()

        var staged = 0
        var awaitingReview = 0
        var noCandidates = 0
        var failed = 0

        for ((index, entry) in entries.withIndex()) {
            coroutineContext.ensureActive()
            store.updateRun { it.copy(processed = index, currentTitle = entry.title) }
            postProgressNotification()

            try {
                val queries = migrationQueries(entry.title, entry.description)
                val result = searcher.searchManga(entry, queries, sourceId)
                val exact = result.exactMatch
                if (exact != null) {
                    // Two-phase: stage the pair, do NOT migrate yet —
                    // nothing moves until the user taps "Confirmar".
                    store.addStagedPair(
                        AutoMigrationStagedPair(
                            isManga = true,
                            sourceId = sourceId,
                            oldEntryId = entry.id,
                            oldTitle = entry.title,
                            oldThumbnailUrl = entry.thumbnailUrl,
                            oldSourceName = source.name,
                            newEntryId = exact.entryId,
                            newSourceId = exact.sourceId,
                            newSourceName = exact.sourceName,
                            newTitle = exact.title,
                            newThumbnailUrl = exact.thumbnailUrl,
                            auto = true,
                        ),
                    )
                    staged++
                } else {
                    store.addReviewItem(
                        AutoMigrationReviewItem(
                            isManga = true,
                            sourceId = sourceId,
                            entryId = entry.id,
                            title = entry.title,
                            thumbnailUrl = entry.thumbnailUrl,
                            originSourceName = source.name,
                            reason = AutoMigrationReviewItem.REASON_NO_EXACT,
                            candidates = result.candidates,
                        ),
                    )
                    awaitingReview++
                    if (result.candidates.isEmpty()) noCandidates++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Auto-migration failed for manga ${entry.id}" }
                failed++
            }

            store.updateRun {
                it.copy(
                    processed = index + 1,
                    awaitingReview = awaitingReview,
                    noCandidates = noCandidates,
                    failed = failed,
                    currentTitle = null,
                )
            }
            postProgressNotification()
        }

        finishSearch(staged, awaitingReview)
    }

    private suspend fun runAnime(sourceId: Long) {
        val source = animeSourceManager.getOrStub(sourceId)
        val entries = getAnimeFavorites.subscribe(sourceId).first()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

        store.startRun(isManga = false, sourceId = sourceId, sourceName = source.name, total = entries.size)
        postProgressNotification()

        var staged = 0
        var awaitingReview = 0
        var noCandidates = 0
        var failed = 0

        for ((index, entry) in entries.withIndex()) {
            coroutineContext.ensureActive()
            store.updateRun { it.copy(processed = index, currentTitle = entry.title) }
            postProgressNotification()

            try {
                // Season-type entries are never auto-migrated: the
                // substitute may split seasons differently, so the user
                // picks it by hand in the review list.
                if (entry.fetchType != FetchType.Episodes) {
                    store.addReviewItem(
                        AutoMigrationReviewItem(
                            isManga = false,
                            sourceId = sourceId,
                            entryId = entry.id,
                            title = entry.title,
                            thumbnailUrl = entry.thumbnailUrl,
                            originSourceName = source.name,
                            reason = AutoMigrationReviewItem.REASON_SEASON,
                            candidates = emptyList(),
                        ),
                    )
                    awaitingReview++
                    noCandidates++
                } else {
                    val queries = migrationQueries(entry.title, entry.description)
                    val result = searcher.searchAnime(entry, queries, sourceId)
                    val exact = result.exactMatch
                    if (exact != null && !exact.isSeasonType) {
                        // Two-phase: stage the pair, do NOT migrate yet.
                        store.addStagedPair(
                            AutoMigrationStagedPair(
                                isManga = false,
                                sourceId = sourceId,
                                oldEntryId = entry.id,
                                oldTitle = entry.title,
                                oldThumbnailUrl = entry.thumbnailUrl,
                                oldSourceName = source.name,
                                newEntryId = exact.entryId,
                                newSourceId = exact.sourceId,
                                newSourceName = exact.sourceName,
                                newTitle = exact.title,
                                newThumbnailUrl = exact.thumbnailUrl,
                                auto = true,
                                isSeasonType = false,
                            ),
                        )
                        staged++
                    } else {
                        // No exact match, or the exact match is a
                        // season-type entry: review it by hand.
                        val reason = if (exact?.isSeasonType == true) {
                            AutoMigrationReviewItem.REASON_SEASON
                        } else {
                            AutoMigrationReviewItem.REASON_NO_EXACT
                        }
                        store.addReviewItem(
                            AutoMigrationReviewItem(
                                isManga = false,
                                sourceId = sourceId,
                                entryId = entry.id,
                                title = entry.title,
                                thumbnailUrl = entry.thumbnailUrl,
                                originSourceName = source.name,
                                reason = reason,
                                candidates = result.candidates,
                            ),
                        )
                        awaitingReview++
                        if (result.candidates.isEmpty()) noCandidates++
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Auto-migration failed for anime ${entry.id}" }
                failed++
            }

            store.updateRun {
                it.copy(
                    processed = index + 1,
                    awaitingReview = awaitingReview,
                    noCandidates = noCandidates,
                    failed = failed,
                    currentTitle = null,
                )
            }
            postProgressNotification()
        }

        finishSearch(staged, awaitingReview)
    }

    /**
     * The search is done — but nothing migrated yet. If there is
     * anything to confirm (staged pairs or review items), the run waits
     * for the user to tap "Confirmar"; otherwise it is simply finished.
     */
    private fun finishSearch(staged: Int, awaitingReview: Int) {
        val state = store.currentRun()
        val status = if (staged > 0 || awaitingReview > 0) {
            AutoMigrationRunState.STATUS_AWAITING_CONFIRMATION
        } else {
            AutoMigrationRunState.STATUS_FINISHED
        }
        if (state != null) {
            store.updateRun { it.copy(status = status, currentTitle = null, finishedAt = System.currentTimeMillis()) }
        }
        postFinishedNotification(cancelled = false)
    }

    /**
     * Search queries for an entry: its own title first, then the original
     * title parsed from its description when it differs (a title often
     * lives under another name in another source, e.g. "Bulpyeonhan
     * Jinsil" is "An Uncomfortable Truth" on MangaDex).
     */
    private fun migrationQueries(title: String, description: String?): List<String> {
        return listOfNotNull(
            title.trim().takeIf { it.isNotEmpty() },
            parseOriginalTitle(description)?.trim()?.takeIf { it.isNotEmpty() },
        ).distinct()
    }

    private fun postProgressNotification() {
        val state = store.currentRun() ?: return
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_AUTO_MIGRATION_PROGRESS) {
            setContentTitle(applicationContext.stringResource(MR.strings.auto_migration_notification_title))
            setContentText(
                applicationContext.stringResource(
                    MR.strings.auto_migration_processed,
                    state.processed,
                    state.total,
                ),
            )
            setSmallIcon(android.R.drawable.stat_sys_download)
            setProgress(state.total, state.processed, false)
            setOngoing(true)
        }.build()
        NotificationManagerCompat.from(applicationContext)
            .notify(Notifications.ID_AUTO_MIGRATION_PROGRESS, notification)
    }

    private fun postFinishedNotification(cancelled: Boolean) {
        val state = store.currentRun()
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_AUTO_MIGRATION_PROGRESS) {
            setContentTitle(applicationContext.stringResource(MR.strings.auto_migration_notification_title))
            setContentText(
                if (cancelled || state == null) {
                    applicationContext.stringResource(MR.strings.auto_migration_cancelled)
                } else if (state.isAwaitingConfirmation) {
                    val staged = store.stagedPairsFor(state.isManga, state.sourceId).size
                    applicationContext.stringResource(
                        MR.strings.auto_migration_notification_ready,
                        staged,
                        state.awaitingReview,
                    )
                } else {
                    applicationContext.stringResource(
                        MR.strings.auto_migration_notification_finished,
                        state.autoMigrated,
                        state.awaitingReview,
                    )
                },
            )
            setSmallIcon(android.R.drawable.stat_sys_download)
            setOngoing(false)
            setAutoCancel(true)
            setContentIntent(openAppIntent())
        }.build()
        NotificationManagerCompat.from(applicationContext)
            .notify(Notifications.ID_AUTO_MIGRATION_PROGRESS, notification)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val KEY_IS_MANGA = "is_manga"
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_MODE = "mode"
        const val MODE_SEARCH = "search"
        const val MODE_APPLY = "apply"

        /** One automatic migration run at a time, like the downloaders. */
        private const val UNIQUE_WORK_NAME = "AutoMigration"

        fun start(context: Context, isManga: Boolean, sourceId: Long) {
            val request = OneTimeWorkRequestBuilder<AutoMigrationJob>()
                .setConstraints(
                    Constraints(requiredNetworkType = NetworkType.CONNECTED),
                )
                .setInputData(
                    workDataOf(
                        KEY_IS_MANGA to isManga,
                        KEY_SOURCE_ID to sourceId,
                        KEY_MODE to MODE_SEARCH,
                    ),
                )
                .addTag(UNIQUE_WORK_NAME)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        /**
         * Enqueues the APPLY phase (the user tapped "Confirmar"): migrates
         * every staged pair at once. KEEP so a double tap cannot apply
         * the same run twice.
         */
        fun startApply(context: Context, isManga: Boolean, sourceId: Long) {
            val request = OneTimeWorkRequestBuilder<AutoMigrationJob>()
                .setConstraints(
                    Constraints(requiredNetworkType = NetworkType.CONNECTED),
                )
                .setInputData(
                    workDataOf(
                        KEY_IS_MANGA to isManga,
                        KEY_SOURCE_ID to sourceId,
                        KEY_MODE to MODE_APPLY,
                    ),
                )
                .addTag(UNIQUE_WORK_NAME)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
