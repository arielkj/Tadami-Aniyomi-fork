package eu.kanade.tachiyomi.data.migration.auto

import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Paced, sequential substitute search for automatic migration.
 *
 * Searches run one request at a time with a delay between them and a
 * per-source throttle window after a 429, so a 300-title run never
 * hammers a source. Only the exact title match (after normalization)
 * counts as an automatic migration; everything else is returned as
 * review candidates grouped by source.
 */
class AutoMigrationSearcher(
    private val mangaSourceManager: MangaSourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
) {

    companion object {
        /** Delay between two search requests. */
        const val REQUEST_DELAY_MS = 600L

        /** How long a source that answered 429 stays skipped. */
        const val THROTTLE_WINDOW_MS = 60_000L

        /** Candidates kept per source for the review list. */
        const val MAX_CANDIDATES_PER_SOURCE = 4
    }

    /** Source id -> timestamp (ms) until which the source stays skipped. */
    private val throttledUntil = mutableMapOf<Long, Long>()

    data class SearchResult(
        val exactMatch: AutoMigrationCandidate?,
        val candidates: List<AutoMigrationCandidate>,
    )

    /**
     * Searches every enabled source for [queries] (first the entry title,
     * then its alternative/original title when available). Returns the
     * first exact title match plus every candidate found, grouped by
     * source order (pinned sources first).
     */
    suspend fun searchManga(
        entry: Manga,
        queries: List<String>,
        originSourceId: Long,
    ): SearchResult {
        val sources = enabledMangaSources(originSourceId)
        val candidates = mutableListOf<AutoMigrationCandidate>()
        val normalizedTargets = queries
            .map { normalizeMigrationTitle(it) }
            .filter { it.isNotEmpty() }

        for (query in queries) {
            for (source in sources) {
                currentCoroutineContext().ensureActive()
                if (isThrottled(source.id)) continue
                delay(REQUEST_DELAY_MS)
                val results = runCatching {
                    source.getSearchManga(1, query, source.getFilterList()).mangas
                }.onFailure { error -> handleSearchError(source.id, source.name, error) }
                    .getOrNull() ?: continue

                for (smanga in results.take(MAX_CANDIDATES_PER_SOURCE)) {
                    currentCoroutineContext().ensureActive()
                    if (smanga.url == entry.url && source.id == entry.source) continue
                    val domain = runCatching {
                        smanga.toDomainManga(source.id).let { networkToLocalManga.await(it) }
                    }.getOrNull() ?: continue
                    val candidate = AutoMigrationCandidate(
                        entryId = domain.id,
                        sourceId = source.id,
                        sourceName = source.name,
                        title = domain.title,
                        thumbnailUrl = domain.thumbnailUrl,
                    )
                    if (candidates.none { it.entryId == candidate.entryId }) {
                        candidates += candidate
                    }
                    if (normalizedTargets.any { it == normalizeMigrationTitle(domain.title) }) {
                        return SearchResult(exactMatch = candidate, candidates = candidates)
                    }
                }
            }
        }
        return SearchResult(exactMatch = null, candidates = candidates)
    }

    suspend fun searchAnime(
        entry: Anime,
        queries: List<String>,
        originSourceId: Long,
    ): SearchResult {
        val sources = enabledAnimeSources(originSourceId)
        val candidates = mutableListOf<AutoMigrationCandidate>()
        val normalizedTargets = queries
            .map { normalizeMigrationTitle(it) }
            .filter { it.isNotEmpty() }

        for (query in queries) {
            for (source in sources) {
                currentCoroutineContext().ensureActive()
                if (isThrottled(source.id)) continue
                delay(REQUEST_DELAY_MS)
                val results = runCatching {
                    source.getSearchAnime(1, query, source.getFilterList()).animes
                }.onFailure { error -> handleSearchError(source.id, source.name, error) }
                    .getOrNull() ?: continue

                for (sanime in results.take(MAX_CANDIDATES_PER_SOURCE)) {
                    currentCoroutineContext().ensureActive()
                    if (sanime.url == entry.url && source.id == entry.source) continue
                    val domain = runCatching {
                        sanime.toDomainAnime(source.id).let { networkToLocalAnime.await(it) }
                    }.getOrNull() ?: continue
                    val candidate = AutoMigrationCandidate(
                        entryId = domain.id,
                        sourceId = source.id,
                        sourceName = source.name,
                        title = domain.title,
                        thumbnailUrl = domain.thumbnailUrl,
                        isSeasonType = domain.fetchType != FetchType.Episodes,
                    )
                    if (candidates.none { it.entryId == candidate.entryId }) {
                        candidates += candidate
                    }
                    if (normalizedTargets.any { it == normalizeMigrationTitle(domain.title) }) {
                        return SearchResult(exactMatch = candidate, candidates = candidates)
                    }
                }
            }
        }
        return SearchResult(exactMatch = null, candidates = candidates)
    }

    private fun isThrottled(sourceId: Long): Boolean {
        val until = throttledUntil[sourceId] ?: return false
        return System.currentTimeMillis() < until
    }

    private fun handleSearchError(sourceId: Long, sourceName: String, error: Throwable) {
        if (error is HttpException && error.code == 429) {
            // Rate-limited: skip this source for a while instead of
            // retrying immediately and getting blocked.
            throttledUntil[sourceId] = System.currentTimeMillis() + THROTTLE_WINDOW_MS
            logcat(LogPriority.WARN) { "Auto-migration: source $sourceName throttled (429)" }
        } else {
            logcat(LogPriority.ERROR, error) { "Auto-migration: search failed in source $sourceName" }
        }
    }

    private fun enabledMangaSources(originSourceId: Long): List<CatalogueSource> {
        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledMangaSources().get()
        val pinnedSources = sourcePreferences.pinnedMangaSources().get()

        return mangaSourceManager.getCatalogueSources()
            .filter {
                it.id != originSourceId &&
                    it.lang in enabledLanguages &&
                    "${it.id}" !in disabledSources
            }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    private fun enabledAnimeSources(originSourceId: Long): List<AnimeCatalogueSource> {
        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledAnimeSources().get()
        val pinnedSources = sourcePreferences.pinnedAnimeSources().get()

        return animeSourceManager.getCatalogueSources()
            .filter {
                it.id != originSourceId &&
                    it.lang in enabledLanguages &&
                    "${it.id}" !in disabledSources
            }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }
}
