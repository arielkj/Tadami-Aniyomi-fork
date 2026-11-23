package eu.kanade.tachiyomi.ui.browse.migration.auto

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.entries.anime.interactor.MigrateAnimeUseCase
import eu.kanade.domain.entries.manga.interactor.MigrateMangaUseCase
import eu.kanade.domain.metadata.interactor.parseOriginalTitle
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorMedia
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorStore
import eu.kanade.tachiyomi.data.migration.auto.AutoMigrationCandidate
import eu.kanade.tachiyomi.data.migration.auto.AutoMigrationReviewItem
import eu.kanade.tachiyomi.data.migration.auto.AutoMigrationSearcher
import eu.kanade.tachiyomi.data.migration.auto.AutoMigrationStore
import eu.kanade.tachiyomi.data.migration.auto.autoMigrationAnimeFlags
import eu.kanade.tachiyomi.data.migration.auto.autoMigrationMangaFlags
import eu.kanade.tachiyomi.data.migration.auto.isExactTitleMatch
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeSearchScreen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.updateimport logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Review list for an automatic migration run — the same idea as the
 * alternative-covers picker: one card per entry that had no exact title
 * match, with its candidate substitutes grouped BY SOURCE (cover +
 * title + source name). The user taps the one it resembles most, types
 * another name to search again when titles differ across languages, or
 * skips the entry to keep it in the old source.
 */
data class AutoMigrationReviewScreen(
    val isManga: Boolean,
    val sourceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { AutoMigrationReviewScreenModel(isManga, sourceId) }
        val state by screenModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.auto_migration_review),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            if (state.items.isEmpty()) {
                EmptyScreen(
                    stringRes = MR.strings.auto_migration_no_review_items,
                    modifier = Modifier.padding(contentPadding),
                )
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                items(
                    items = state.items,
                    key = { item -> item.entryId },
                ) { item ->
                    ReviewItemCard(
                        item = item,
                        isManga = isManga,
                        isMigrating = item.entryId in state.migratingIds,
                        isSearching = item.entryId in state.searchingIds,
                        onMigrate = { candidate -> screenModel.migrateNow(item, candidate) },
                        onSeasonCandidate = { navigator.push(MigrateAnimeSearchScreen(item.entryId)) },
                        onResearch = { query -> screenModel.research(item, query) },
                        onSkip = { screenModel.skip(item) },
                    )
                }
            }
        }
    }

    @Composable
    private fun ReviewItemCard(
        item: AutoMigrationReviewItem,
        isManga: Boolean,
        isMigrating: Boolean,
        isSearching: Boolean,
        onMigrate: (AutoMigrationCandidate) -> Unit,
        onSeasonCandidate: () -> Unit,
        onResearch: (String) -> Unit,
        onSkip: () -> Unit,
    ) {
        var queryText by remember(item.entryId) {
            mutableStateOf(item.lastQuery ?: item.title)
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                ) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp, 72.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                        )
                        Text(
                            text = item.originSourceName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (item.reason == AutoMigrationReviewItem.REASON_SEASON) {
                            Text(
                                text = stringResource(MR.strings.auto_migration_season_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (isMigrating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        placeholder = { Text(text = stringResource(MR.strings.auto_migration_search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { onResearch(queryText) },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onResearch(queryText) },
                        enabled = !isSearching,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(MR.strings.action_search),
                        )
                    }
                }

                when {
                    isSearching -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                    item.candidates.isEmpty() -> {
                        Text(
                            text = stringResource(MR.strings.auto_migration_no_candidates_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        val grouped = item.candidates.groupBy { it.sourceName }
                        for ((sourceName, group) in grouped) {
                            Text(
                                text = sourceName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                            for (candidate in group) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isMigrating) {
                                            if (!isManga && candidate.isSeasonType) {
                                                onSeasonCandidate()
                                            } else {
                                                onMigrate(candidate)
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(
                                        model = candidate.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp, 60.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 12.dp),
                                    ) {
                                        Text(
                                            text = candidate.title,
                                            maxLines = 2,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        if (!isManga && candidate.isSeasonType) {
                                            Text(
                                                text = stringResource(MR.strings.auto_migration_season_note),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onSkip,
                        enabled = !isMigrating,
                    ) {
                        Text(text = stringResource(MR.strings.migration_skip_entry))
                    }
                }
            }
        }
    }
}

class AutoMigrationReviewScreenModel(
    private val isManga: Boolean,
    private val sourceId: Long,
    private val store: AutoMigrationStore = AutoMigrationStore(Injekt.get<Application>()),
    private val searcher: AutoMigrationSearcher = AutoMigrationSearcher(),
    private val migrateManga: MigrateMangaUseCase = MigrateMangaUseCase(),
    private val migrateAnime: MigrateAnimeUseCase = MigrateAnimeUseCase(),
    private val getManga: GetManga = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
) : StateScreenModel<AutoMigrationReviewScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            AutoMigrationStore.reviewItemsFlow.collect { items ->
                mutableState.update {
                    it.copy(
                        items = items
                            .filter { item -> item.isManga == isManga && item.sourceId == sourceId }
                            .toImmutableList(),
                    )
                }
            }
        }
    }

    fun migrateNow(item: AutoMigrationReviewItem, candidate: AutoMigrationCandidate) {
        screenModelScope.launchIO {
            mutableState.update { it.copy(migratingIds = it.migratingIds + item.entryId) }
            try {
                if (isManga) {
                    val oldManga = getManga.await(item.entryId) ?: return@launchIO
                    val target = getManga.await(candidate.entryId) ?: return@launchIO
                    migrateManga.migrateManga(oldManga, target, replace = true, flags = autoMigrationMangaFlags(oldManga))
                    LibraryUpdateErrorStore.markResolved(
                        media = LibraryUpdateErrorMedia.Manga,
                        entryId = oldManga.id,
                    )
                } else {
                    val oldAnime = getAnime.await(item.entryId) ?: return@launchIO
                    val target = getAnime.await(candidate.entryId) ?: return@launchIO
                    migrateAnime.migrateAnime(oldAnime, target, replace = true, flags = autoMigrationAnimeFlags(oldAnime))
                    LibraryUpdateErrorStore.markResolved(
                        media = LibraryUpdateErrorMedia.Anime,
                        entryId = oldAnime.id,
                    )
                }
                store.removeReviewItem(item.entryId, isManga)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Auto-migration review: failed to migrate ${item.entryId}" }
            } finally {
                mutableState.update { it.copy(migratingIds = it.migratingIds - item.entryId) }
            }
        }
    }

    fun research(item: AutoMigrationReviewItem, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        // IO: the search hits the network and the local DB.
        screenModelScope.launchIO {
            mutableState.update { it.copy(searchingIds = it.searchingIds + item.entryId) }
            try {
                val candidates = if (isManga) {
                    val oldManga = getManga.await(item.entryId) ?: return@launch
                    val queries = listOfNotNull(
                        trimmed,
                        parseOriginalTitle(oldManga.description)?.trim()?.takeIf { it.isNotEmpty() },
                    ).distinct()
                    searcher.searchManga(oldManga, queries, sourceId).candidates
                } else {
                    val oldAnime = getAnime.await(item.entryId) ?: return@launch
                    val queries = listOfNotNull(
                        trimmed,
                        parseOriginalTitle(oldAnime.description)?.trim()?.takeIf { it.isNotEmpty() },
                    ).distinct()
                    searcher.searchAnime(oldAnime, queries, sourceId).candidates
                }
                // Exact matches of the typed query first, like the covers picker.
                val sorted = candidates.sortedByDescending {
                    isExactTitleMatch(trimmed, it.title)
                }
                store.updateReviewItem(item.entryId, isManga) {
                    it.copy(candidates = sorted, lastQuery = trimmed)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Auto-migration review: search failed for ${item.entryId}" }
            } finally {
                mutableState.update { it.copy(searchingIds = it.searchingIds - item.entryId) }
            }
        }
    }

    fun skip(item: AutoMigrationReviewItem) {
        store.removeReviewItem(item.entryId, isManga)
    }

    data class State(
        val items: ImmutableList<AutoMigrationReviewItem> = persistentListOf(),
        val migratingIds: Set<Long> = emptySet(),
        val searchingIds: Set<Long> = emptySet(),
    )
}
