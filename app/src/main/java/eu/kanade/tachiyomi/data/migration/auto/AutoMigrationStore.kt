package eu.kanade.tachiyomi.data.migration.auto

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Persists the automatic-migration run state and the review list.
 *
 * The worker and the UI each create their own store instance, so the
 * live state lives in companion-object flows shared process-wide, and
 * every mutation is also written to SharedPreferences so the screens
 * can restore it after a process restart.
 */
class AutoMigrationStore(
    context: Context,
    private val json: Json = Injekt.get(),
) {

    companion object {
        private const val PREFS_NAME = "auto_migration"
        private const val KEY_RUN_STATE = "run_state"
        private const val KEY_REVIEW_ITEMS = "review_items"

        private val runStateFlowInternal = MutableStateFlow<AutoMigrationRunState?>(null)
        val runStateFlow: StateFlow<AutoMigrationRunState?> = runStateFlowInternal.asStateFlow()

        private val reviewItemsFlowInternal = MutableStateFlow<List<AutoMigrationReviewItem>>(emptyList())
        val reviewItemsFlow: StateFlow<List<AutoMigrationReviewItem>> = reviewItemsFlowInternal.asStateFlow()

        private val lock = Any()
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Restore persisted state into the shared flows (e.g. after the
        // process was killed while a run was active).
        if (runStateFlowInternal.value == null) {
            runCatching {
                preferences.getString(KEY_RUN_STATE, null)?.let {
                    runStateFlowInternal.value = json.decodeFromString<AutoMigrationRunState>(it)
                }
            }
        }
        if (reviewItemsFlowInternal.value.isEmpty()) {
            runCatching {
                preferences.getString(KEY_REVIEW_ITEMS, null)?.let {
                    reviewItemsFlowInternal.value = json.decodeFromString<List<AutoMigrationReviewItem>>(it)
                }
            }
        }
    }

    fun isRunActive(): Boolean = runStateFlowInternal.value?.isRunning == true

    fun currentRun(): AutoMigrationRunState? = runStateFlowInternal.value

    fun startRun(isManga: Boolean, sourceId: Long, sourceName: String, total: Int) {
        val state = AutoMigrationRunState(
            isManga = isManga,
            sourceId = sourceId,
            sourceName = sourceName,
            total = total,
            processed = 0,
            autoMigrated = 0,
            awaitingReview = 0,
            noCandidates = 0,
            failed = 0,
            currentTitle = null,
            status = AutoMigrationRunState.STATUS_RUNNING,
            startedAt = System.currentTimeMillis(),
        )
        synchronized(lock) {
            runStateFlowInternal.value = state
            persistRunState(state)
        }
    }

    fun updateRun(transform: (AutoMigrationRunState) -> AutoMigrationRunState) {
        synchronized(lock) {
            val current = runStateFlowInternal.value ?: return
            val updated = transform(current)
            runStateFlowInternal.value = updated
            persistRunState(updated)
        }
    }

    fun finishRun(status: String) {
        synchronized(lock) {
            val current = runStateFlowInternal.value ?: return
            val updated = current.copy(
                status = status,
                currentTitle = null,
                finishedAt = System.currentTimeMillis(),
            )
            runStateFlowInternal.value = updated
            persistRunState(updated)
        }
    }

    fun addReviewItem(item: AutoMigrationReviewItem) {
        synchronized(lock) {
            val current = reviewItemsFlowInternal.value
            // A restarted worker re-processes entries it already reviewed;
            // never duplicate an item for the same entry.
            if (current.any { it.entryId == item.entryId && it.isManga == item.isManga }) return
            val updated = current + item
            reviewItemsFlowInternal.value = updated
            persistReviewItems(updated)
        }
    }

    fun updateReviewItem(entryId: Long, isManga: Boolean, transform: (AutoMigrationReviewItem) -> AutoMigrationReviewItem) {
        synchronized(lock) {
            val current = reviewItemsFlowInternal.value
            val updated = current.map {
                if (it.entryId == entryId && it.isManga == isManga) transform(it) else it
            }
            if (updated != current) {
                reviewItemsFlowInternal.value = updated
                persistReviewItems(updated)
            }
        }
    }

    fun removeReviewItem(entryId: Long, isManga: Boolean) {
        synchronized(lock) {
            val current = reviewItemsFlowInternal.value
            val updated = current.filterNot { it.entryId == entryId && it.isManga == isManga }
            if (updated.size != current.size) {
                reviewItemsFlowInternal.value = updated
                persistReviewItems(updated)
            }
        }
    }

    fun reviewItemsFor(isManga: Boolean, sourceId: Long): List<AutoMigrationReviewItem> {
        return reviewItemsFlowInternal.value.filter { it.isManga == isManga && it.sourceId == sourceId }
    }

    private fun persistRunState(state: AutoMigrationRunState) {
        preferences.edit { putString(KEY_RUN_STATE, json.encodeToString(state)) }
    }

    private fun persistReviewItems(items: List<AutoMigrationReviewItem>) {
        preferences.edit { putString(KEY_REVIEW_ITEMS, json.encodeToString(items)) }
    }
}
