package eu.kanade.tachiyomi.ui.browse.migration.auto

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.tachiyomi.data.migration.auto.AutoMigrationRunState
import eu.kanade.tachiyomi.data.migration.auto.AutoMigrationStore
import kotlinx.coroutines.flow.sample
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Persistent progress banner for an automatic-migration run, shown
 * OUTSIDE the run's own screen (sources list, per-source entry list)
 * so the user sees at a glance which entry is being migrated without
 * entering sections one by one.
 *
 * The run state lives in [AutoMigrationStore] (process-wide flow +
 * SharedPreferences), so this banner re-subscribes on entry and shows
 * the live run even after the user left to read and came back. It
 * subscribes to the run flow only — nothing else loads for it — and
 * tapping it opens that run's own screen. Renders nothing when no run
 * is active or waiting for confirmation.
 *
 * [isManga]/[sourceId] optionally restrict the banner to one source's
 * list; with both null it shows whichever run is active.
 */
@Composable
fun AutoMigrationProgressBanner(
    isManga: Boolean? = null,
    sourceId: Long? = null,
    onOpenRun: (isManga: Boolean, sourceId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Fluidity: same 300 ms sampling as the run screen — worker bursts
    // coalesce into at most one recomposition per window.
    val runFlow = remember { AutoMigrationStore.runStateFlow.sample(300) }
    val run by runFlow.collectAsStateWithLifecycle(initialValue = AutoMigrationStore.runStateFlow.value)
    val stagedPairs by AutoMigrationStore.stagedPairsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val shown = run?.takeIf {
        (isManga == null || it.isManga == isManga) &&
            (sourceId == null || it.sourceId == sourceId) &&
            (it.isRunning || it.isApplying || it.isAwaitingConfirmation)
    } ?: return

    val stagedCount = stagedPairs.count { it.isManga == shown.isManga && it.sourceId == shown.sourceId }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenRun(shown.isManga, shown.sourceId) },
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Text(
                text = shown.sourceName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                shown.isRunning -> {
                    Text(
                        text = stringResource(MR.strings.auto_migration_running),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { (shown.processed.toFloat() / shown.total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            MR.strings.auto_migration_processed,
                            shown.processed,
                            shown.total,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    shown.currentTitle?.let { current ->
                        Text(
                            text = stringResource(MR.strings.auto_migration_searching_title, current),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                shown.isApplying -> {
                    Text(
                        text = stringResource(MR.strings.auto_migration_applying),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { (shown.processed.toFloat() / shown.total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            MR.strings.auto_migration_processed,
                            shown.processed,
                            shown.total,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    shown.currentTitle?.let { current ->
                        Text(
                            text = stringResource(MR.strings.auto_migration_applying_title, current),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                shown.isAwaitingConfirmation -> {
                    Text(
                        text = stringResource(MR.strings.auto_migration_ready_to_confirm),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            MR.strings.auto_migration_notification_ready,
                            stagedCount,
                            shown.awaitingReview,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
