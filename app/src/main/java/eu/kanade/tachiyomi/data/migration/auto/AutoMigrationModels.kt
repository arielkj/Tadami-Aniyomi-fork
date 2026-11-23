package eu.kanade.tachiyomi.data.migration.auto

import eu.kanade.tachiyomi.ui.browse.anime.migration.AnimeMigrationFlags
import eu.kanade.tachiyomi.ui.browse.manga.migration.MangaMigrationFlags
import kotlinx.serialization.Serializable
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import java.text.Normalizer
import java.util.Locale

/**
 * Automatic per-source migration ("Migración automática").
 *
 * One run covers ONE origin source (manga or anime). The background
 * worker only SEARCHES: exact title matches are staged, everything
 * else lands in a review list where the user picks the substitute,
 * like the alternative-covers picker. NOTHING is migrated until the
 * user taps "Confirmar" — the apply step then migrates every staged
 * pair at once.
 */

/** One candidate substitute found in another source. */
@Serializable
data class AutoMigrationCandidate(
    /** Local DB id of the candidate entry (resolved via NetworkToLocal). */
    val entryId: Long,
    val sourceId: Long,
    val sourceName: String,
    val title: String,
    val thumbnailUrl: String?,
    /** Anime only: the candidate uses seasons instead of plain episodes. */
    val isSeasonType: Boolean = false,
)

/**
 * One old -> new pair waiting for the user to tap "Confirmar".
 * [auto] is true when the worker found it by exact title match, false
 * when the user picked it from the review list.
 */
@Serializable
data class AutoMigrationStagedPair(
    val isManga: Boolean,
    val sourceId: Long,
    val oldEntryId: Long,
    val oldTitle: String,
    val oldThumbnailUrl: String?,
    val oldSourceName: String,
    val newEntryId: Long,
    val newSourceId: Long,
    val newSourceName: String,
    val newTitle: String,
    val newThumbnailUrl: String?,
    val auto: Boolean = true,
    val isSeasonType: Boolean = false,
)

/** One library entry that could not be auto-migrated, waiting for review. */
@Serializable
data class AutoMigrationReviewItem(
    val isManga: Boolean,
    val sourceId: Long,
    val entryId: Long,
    val title: String,
    val thumbnailUrl: String?,
    val originSourceName: String,
    /** "no_exact": no exact title match found. "season": seasons differ. */
    val reason: String,
    val candidates: List<AutoMigrationCandidate> = emptyList(),
    val lastQuery: String? = null,
) {
    companion object {
        const val REASON_NO_EXACT = "no_exact"
        const val REASON_SEASON = "season"
    }
}

/** Persisted state of the current (or last) automatic migration run. */
@Serializable
data class AutoMigrationRunState(
    val isManga: Boolean,
    val sourceId: Long,
    val sourceName: String,
    val total: Int,
    val processed: Int,
    val autoMigrated: Int,
    val awaitingReview: Int,
    val noCandidates: Int,
    val failed: Int,
    val currentTitle: String?,
    /**
     * "running" (searching) | "awaiting_confirmation" (search done,
     * staged pairs + review items wait for the Confirmar button) |
     * "applying" (the Confirmar step is migrating the staged pairs) |
     * "finished" | "cancelled".
     */
    val status: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
) {
    companion object {
        const val STATUS_RUNNING = "running"
        const val STATUS_AWAITING_CONFIRMATION = "awaiting_confirmation"
        const val STATUS_APPLYING = "applying"
        const val STATUS_FINISHED = "finished"
        const val STATUS_CANCELLED = "cancelled"
    }

    val isRunning: Boolean get() = status == STATUS_RUNNING
    val isAwaitingConfirmation: Boolean get() = status == STATUS_AWAITING_CONFIRMATION
    val isApplying: Boolean get() = status == STATUS_APPLYING
    val isFinished: Boolean get() = status == STATUS_FINISHED
}

/**
 * Normalizes a title for comparison: lowercase, accents stripped,
 * punctuation/whitespace removed. "One Piece", "ONE PIECE!" and
 * "One-Piece" all normalize to the same string.
 */
fun normalizeMigrationTitle(raw: String): String {
    val noAccents = Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return noAccents.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
}

/** True when two titles are the same title after normalization. */
fun isExactTitleMatch(a: String, b: String): Boolean {
    val normalizedA = normalizeMigrationTitle(a)
    val normalizedB = normalizeMigrationTitle(b)
    return normalizedA.isNotEmpty() && normalizedA == normalizedB
}

/**
 * Standard migration flags for an automatic migration: everything the
 * one-by-one migration includes by default (chapters/episodes,
 * categories, tracking, custom cover/background, notes, extra) EXCEPT
 * deleting the old downloads — those files belong to the old source and
 * deleting them is destructive; the user re-downloads in the new source.
 */
fun autoMigrationMangaFlags(manga: Manga): Int {
    val flags = MangaMigrationFlags.getFlags(manga, Int.MAX_VALUE)
        .filter { !MangaMigrationFlags.hasDeleteDownloaded(it.flag) }
    return MangaMigrationFlags.getSelectedFlagsBitMap(flags.map { true }, flags)
}

fun autoMigrationAnimeFlags(anime: Anime): Int {
    val flags = AnimeMigrationFlags.getFlags(anime, Int.MAX_VALUE)
        .filter { !AnimeMigrationFlags.hasDeleteDownloaded(it.flag) }
    return AnimeMigrationFlags.getSelectedFlagsBitMap(flags.map { true }, flags)
}
