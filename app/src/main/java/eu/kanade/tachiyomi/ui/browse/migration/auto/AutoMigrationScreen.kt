package eu.kanade.tachiyomi.ui.browse.migration.auto

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.migration.auto.AutoMigrationJob
import eu.kanade.tachiyomi.data.migration.auto.AutoMigrationRunState
import eu.kanade.tachiyomi.data.migration.auto.AutoMigrationStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Progress + summary screen for one automatic migration run.
 *
 * The run itself lives in [AutoMigrationJob] (WorkManager), so leaving
 * this screen — or even the app — does not stop it; this screen only
 * observes the run state from [AutoMigrationStore].
 */
data class AutoMigrationScreen(
    val isManga: Boolean,
    val sourceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { AutoMigrationScreenModel(isManga, sourceId) }
        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.auto_migration),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                Text(
                    text = state.sourceName ?: "",
                    style = MaterialTheme.typography.titleLarge,
                )

                val run = state.run
                when {
                    run == null -> {
                        Text(text = stringResource(MR.strings.auto_migration_start_description))
                        Text(
                            text = stringResource(
                                MR.strings.auto_migration_start_count,
                                state.entryCount ?: 0,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.otherRunActive) {
                            Text(
                                text = stringResource(MR.strings.auto_migration_already_running),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Button(
                                onClick = { screenModel.startRun(context) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = stringResource(MR.strings.auto_migration_start))
                            }
                        }
                    }
                    run.isRunning -> {
                        Text(text = stringResource(MR.strings.auto_migration_running))
                        LinearProgressIndicator(
                            progress = { (run.processed.toFloat() / run.total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(
                                MR.strings.auto_migration_processed,
                                run.processed,
                                run.total,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        run.currentTitle?.let { current ->
                            Text(
                                text = stringResource(MR.strings.auto_migration_searching_title, current),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        CountRows(run = run)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                onClick = { screenModel.cancelRun(context) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = stringResource(MR.strings.action_cancel))
                            }
                            Button(
                                onClick = { navigator.push(AutoMigrationReviewScreen(isManga, sourceId)) },
                                enabled = state.reviewCount > 0,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(
                                        MR.strings.auto_migration_review_count,
                                        state.reviewCount,
                                    ),
                                )
                            }
                        }
                    }
                    else -> {
                        Text(text = stringResource(MR.strings.auto_migration_finished))
                        CountRows(run = run)
                        Button(
                            onClick = { navigator.push(AutoMigrationReviewScreen(isManga, sourceId)) },
                            enabled = state.reviewCount > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(
                                    MR.strings.auto_migration_review_count,
                                    state.reviewCount,
                                ),
                            )
                        }
                        OutlinedButton(
                            onClick = navigator::pop,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(MR.strings.action_close))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CountRows(run: AutoMigrationRunState) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
            CountRow(
                label = stringResource(MR.strings.auto_migration_auto_migrated),
                count = run.autoMigrated,
            )
            CountRow(
                label = stringResource(MR.strings.auto_migration_awaiting_review),
                count = run.awaitingReview,
            )
            CountRow(
                label = stringResource(MR.strings.auto_migration_no_candidates),
                count = run.noCandidates,
            )
            if (run.failed > 0) {
                CountRow(
                    label = stringResource(MR.strings.auto_migration_failed),
                    count = run.failed,
                )
            }
        }
    }

    @Composable
    private fun CountRow(label: String, count: Int) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(text = count.toString(), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

class AutoMigrationScreenModel(
    private val isManga: Boolean,
    private val sourceId: Long,
    private val store: AutoMigrationStore = AutoMigrationStore(Injekt.get<Application>()),
    private val mangaSourceManager: MangaSourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val getMangaFavorites: GetMangaFavorites = Injekt.get(),
    private val getAnimeFavorites: GetAnimeFavorites = Injekt.get(),
) : StateScreenModel<AutoMigrationScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            val sourceName = if (isManga) {
                mangaSourceManager.getOrStub(sourceId).name
            } else {
                animeSourceManager.getOrStub(sourceId).name
            }
            val entryCount = if (isManga) {
                getMangaFavorites.subscribe(sourceId).first().size
            } else {
                getAnimeFavorites.subscribe(sourceId).first().size
            }
            mutableState.update {
                it.copy(sourceName = sourceName, entryCount = entryCount, isLoading = false)
            }
        }
        screenModelScope.launchIO {
            AutoMigrationStore.runStateFlow.collect { run ->
                val matching = run?.takeIf { it.isManga == isManga && it.sourceId == sourceId }
                val otherActive = run != null && run.isRunning && matching == null
                mutableState.update {
                    it.copy(run = matching, otherRunActive = otherActive)
                }
            }
        }
        screenModelScope.launchIO {
            AutoMigrationStore.reviewItemsFlow.collect { items ->
                mutableState.update {
                    it.copy(reviewCount = items.count { item -> item.isManga == isManga && item.sourceId == sourceId })
                }
            }
        }
    }

    fun startRun(context: android.content.Context) {
        if (store.isRunActive()) return
        AutoMigrationJob.start(context, isManga, sourceId)
    }

    fun cancelRun(context: android.content.Context) {
        AutoMigrationJob.cancel(context)
    }

    data class State(
        val sourceName: String? = null,
        val entryCount: Int? = null,
        val run: AutoMigrationRunState? = null,
        val reviewCount: Int = 0,
        val otherRunActive: Boolean = false,
        val isLoading: Boolean = true,
    )
}
