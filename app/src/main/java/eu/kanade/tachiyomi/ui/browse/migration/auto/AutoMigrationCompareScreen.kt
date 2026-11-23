package eu.kanade.tachiyomi.ui.browse.migration.auto

import android.app.Application
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.metadata.interactor.parseOriginalTitle
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.migration.auto.AutoMigrationCandidate
import eu.kanade.tachiyomi.data.migration.auto.AutoMigrationReviewItem
import eu.kanade.tachiyomi.data.migration.auto.AutoMigrationSearcher
import eu.kanade.tachiyomi.data.migration.auto.AutoMigrationStagedPair
import eu.kanade.tachiyomi.data.migration.auto.AutoMigrationStore
import eu.kanade.tachiyomi.data.migration.auto.isExactTitleMatch
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Old-vs-new comparison for one summary section of an automatic
 * migration run.
 *
 * - "staged": pairs waiting for "Confirmar" — old cover + title +
 *   source on the left, substitute on the right. Tapping a cover (or
 *   title) opens that title's detail screen so the user can check the
 *   match is really right before confirming.
 * - "review": entries still waiting for a pick — the old entry on the
 *   left, its candidates on the right. Tapping a candidate stages it.
 * - "no_candidates": entries with no substitute found — old entry only,
 *   with a note and a shortcut to the review search.
 */
data class AutoMigrationCompareScreen(
    val isManga: Boolean,
    val sourceId: Long,
    val section: String,
) : Screen() {

    companion object {
        const val SECTION_STAGED = "staged"
        const val SECTION_REVIEW = "review"
        const val SECTION_NO_CANDIDATES = "no_candidates"
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { AutoMigrationCompareScreenModel(isManga, sourceId, section) }
        val state by screenModel.state.collectAsStateWithLifecycle()

        val titleRes = when (section) {
            SECTION_STAGED -> MR.strings.auto_migration_ready_to_confirm
            SECTION_REVIEW -> MR.strings.auto_migration_awaiting_review
            else -> MR.strings.auto_migration_no_candidates
        }
        val openDetail: (Long) -> Unit = { entryId ->
            if (isManga) {
                navigator.push(MangaScreen(entryId))
            } else {
                navigator.push(AnimeScreen(entryId))
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(titleRes),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when (section) {
                SECTION_STAGED -> {
                    if (state.pairs.isEmpty()) {
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
                        items(state.pairs, key = { it.oldEntryId }) { pair ->
                            StagedPairCard(
                                pair = pair,
                                isManga = isManga,
                                counts = state.counts,
                                entries = state.entries,
                                onOpenDetail = openDetail,
                                onRemove = { screenModel.unstage(pair) },
                            )
                        }
                    }
                }
                SECTION_REVIEW -> {
                    val items = state.reviewItems.filter { it.candidates.isNotEmpty() }
                    if (items.isEmpty()) {
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
                        items(items, key = { it.entryId }) { item ->
                            ReviewCompareCard(
                                item = item,
                                isManga = isManga,
                                counts = state.counts,
                                entries = state.entries,
                                isStaging = item.entryId in state.stagingIds,
                                isSearching = item.entryId in state.searchingIds,
                                onOpenDetail = openDetail,
                                onStage = { candidate -> screenModel.stageCandidate(item, candidate) },
                                onExclude = { screenModel.exclude(item) },
                                onResearch = { query -> screenModel.research(item, query) },
                            )
                        }
                    }
                }
                else -> {
                    val items = state.reviewItems.filter { it.candidates.isEmpty() }
                    if (items.isEmpty()) {
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
                        items(items, key = { it.entryId }) { item ->
                            NoCandidateCard(
                                item = item,
                                isManga = isManga,
                                counts = state.counts,
                                entries = state.entries,
                                isSearching = item.entryId in state.searchingIds,
                                onOpenDetail = openDetail,
                                onGoToReview = { navigator.push(AutoMigrationReviewScreen(isManga, sourceId)) },
                                onExclude = { screenModel.exclude(item) },
                                onResearch = { query -> screenModel.research(item, query) },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun EntrySide(
        title: String,
        sourceName: String,
        // v7.9.35: the live domain entry when loaded, else the snapshot
        // URL. Routing through ItemCover (instead of a raw AsyncImage
        // with a plain URL) is what makes the cover load: the entry's
        // fetcher uses the source's own client + headers + Referer,
        // which hosts like Hitomi require — a bare URL request 403s
        // and stays blank in the list while the detail loads fine.
        coverData: Any?,
        count: Int?,
        countLabel: StringResource,
        onOpenDetail: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Column(
            modifier = modifier.clickable(onClick = onOpenDetail),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ItemCover.Book(
                data = coverData,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
            Text(
                text = sourceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (count != null && count > 0) {
                Text(
                    text = stringResource(countLabel, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    @Composable
    private fun StagedPairCard(
        pair: AutoMigrationStagedPair,
        isManga: Boolean,
        counts: Map<Long, Int>,
        entries: Map<Long, Any>,
        onOpenDetail: (Long) -> Unit,
        onRemove: () -> Unit,
    ) {
        val countLabel = if (isManga) {
            MR.strings.auto_migration_chapters_count
        } else {
            MR.strings.auto_migration_episodes_count
        }
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(MR.strings.auto_migration_original),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        EntrySide(
                            title = pair.oldTitle,
                            sourceName = pair.oldSourceName,
                            coverData = entries[pair.oldEntryId] ?: pair.oldThumbnailUrl,
                            count = counts[pair.oldEntryId],
                            countLabel = countLabel,
                            onOpenDetail = { onOpenDetail(pair.oldEntryId) },
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 72.dp)
                            .size(24.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(MR.strings.auto_migration_substitute),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        EntrySide(
                            title = pair.newTitle,
                            sourceName = pair.newSourceName,
                            coverData = entries[pair.newEntryId] ?: pair.newThumbnailUrl,
                            count = counts[pair.newEntryId],
                            countLabel = countLabel,
                            onOpenDetail = { onOpenDetail(pair.newEntryId) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onRemove) {
                        Text(text = stringResource(MR.strings.auto_migration_remove_from_staged))
                    }
                }
            }
        }
    }

    @Composable
    private fun ReviewCompareCard(
        item: AutoMigrationReviewItem,
        isManga: Boolean,
        counts: Map<Long, Int>,
        entries: Map<Long, Any>,
        isStaging: Boolean,
        isSearching: Boolean,
        onOpenDetail: (Long) -> Unit,
        onStage: (AutoMigrationCandidate) -> Unit,
        onExclude: () -> Unit,
        onResearch: (String) -> Unit,
    ) {
        val countLabel = if (isManga) {
            MR.strings.auto_migration_chapters_count
        } else {
            MR.strings.auto_migration_episodes_count
        }
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                ) {
                    // v7.9.35: cover through the live entry (source-aware
                    // fetcher) so it loads in the list like everywhere
                    // else, not through a bare URL.
                    ItemCover.Book(
                        data = entries[item.entryId] ?: item.thumbnailUrl,
                        modifier = Modifier.size(48.dp, 72.dp),
                        shape = RoundedCornerShape(4.dp),
                        onClick = { onOpenDetail(item.entryId) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            modifier = Modifier.clickable { onOpenDetail(item.entryId) },
                        )
                        Text(
                            text = item.originSourceName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        counts[item.entryId]?.takeIf { it > 0 }?.let { count ->
                            Text(
                                text = stringResource(countLabel, count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (isStaging) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                    // X: excludes this entry from the migration — it stays
                    // in the old source and Confirmar never touches it.
                    IconButton(
                        onClick = onExclude,
                        enabled = !isStaging,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.migration_skip_entry),
                        )
                    }
                }
                // v7.9.35: edit the name and retry the search for THIS
                // entry only — other sources often list the same title
                // shorter or without symbols, so the original noisy title
                // finds nothing there.
                ResearchRow(
                    item = item,
                    isSearching = isSearching,
                    onResearch = onResearch,
                )
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
                                .clickable(enabled = !isStaging) { onStage(candidate) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // v7.9.35: cover through the live entry (source-aware
                            // fetcher) so it loads in the list like everywhere else.
                            ItemCover.Book(
                                data = entries[candidate.entryId] ?: candidate.thumbnailUrl,
                                modifier = Modifier.size(40.dp, 60.dp),
                                shape = RoundedCornerShape(4.dp),
                                onClick = { onOpenDetail(candidate.entryId) },
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
                                counts[candidate.entryId]?.takeIf { it > 0 }?.let { count ->
                                    Text(
                                        text = stringResource(countLabel, count),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun NoCandidateCard(
        item: AutoMigrationReviewItem,
        isManga: Boolean,
        counts: Map<Long, Int>,
        entries: Map<Long, Any>,
        isSearching: Boolean,
        onOpenDetail: (Long) -> Unit,
        onGoToReview: () -> Unit,
        onExclude: () -> Unit,
        onResearch: (String) -> Unit,
    ) {
        val countLabel = if (isManga) {
            MR.strings.auto_migration_chapters_count
        } else {
            MR.strings.auto_migration_episodes_count
        }
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                ) {
                    // v7.9.35: cover through the live entry (source-aware
                    // fetcher) so it loads in the list like everywhere
                    // else, not through a bare URL.
                    ItemCover.Book(
                        data = entries[item.entryId] ?: item.thumbnailUrl,
                        modifier = Modifier.size(48.dp, 72.dp),
                        shape = RoundedCornerShape(4.dp),
                        onClick = { onOpenDetail(item.entryId) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            modifier = Modifier.clickable { onOpenDetail(item.entryId) },
                        )
                        Text(
                            text = item.originSourceName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        counts[item.entryId]?.takeIf { it > 0 }?.let { count ->
                            Text(
                                text = stringResource(countLabel, count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // X: excludes this entry from the migration — it stays
                    // in the old source and Confirmar never touches it.
                    IconButton(onClick = onExclude) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.migration_skip_entry),
                        )
                    }
                }
                Text(
                    text = stringResource(MR.strings.auto_migration_no_substitute_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // v7.9.35: same per-entry retry as in "Para revisar" —
                // edit the name (shorter, without symbols) and search
                // again for this entry only.
                ResearchRow(
                    item = item,
                    isSearching = isSearching,
                    onResearch = onResearch,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onGoToReview) {
                        Text(text = stringResource(MR.strings.auto_migration_go_to_review))
                    }
                }
            }
        }
    }
    /**
     * Per-entry retry search: the user edits the name (shorter, without
     * the original's symbols) and re-searches ONLY this entry — never
     * the whole list or run.
     */
    @Composable
    private fun ResearchRow(
        item: AutoMigrationReviewItem,
        isSearching: Boolean,
        onResearch: (String) -> Unit,
    ) {
        var queryText by remember(item.entryId) {
            mutableStateOf(item.lastQuery ?: item.title)
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
        if (isSearching) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

class AutoMigrationCompareScreenModel(
    private val isManga: Boolean,
    private val sourceId: Long,
    /** Which summary section opened this screen — only that section's
     * data is subscribed to and counted (load only what the user
     * is actually looking at). */
    private val section: String,
    private val store: AutoMigrationStore = AutoMigrationStore(Injekt.get<Application>()),
    private val searcher: AutoMigrationSearcher = AutoMigrationSearcher(),
    private val getManga: GetManga = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
) : StateScreenModel<AutoMigrationCompareScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            // Load only what this section shows: staged pairs for the
            // staged section, review items otherwise. The other flow
            // is never subscribed to, so opening one section loads
            // nothing for the sections the user has not opened.
            val pairsFlow = if (section == AutoMigrationCompareScreen.SECTION_STAGED) {
                AutoMigrationStore.stagedPairsFlow
            } else {
                flowOf(emptyList())
            }
            val itemsFlow = if (section == AutoMigrationCompareScreen.SECTION_STAGED) {
                flowOf(emptyList())
            } else {
                AutoMigrationStore.reviewItemsFlow
            }
            combine(
                pairsFlow,
                itemsFlow,
            ) { pairs, items -> pairs to items }.collect { (pairs, items) ->
                val myPairs = pairs
                    .filter { it.isManga == isManga && it.sourceId == sourceId }
                    .toImmutableList()
                val myItems = items
                    .filter { it.isManga == isManga && it.sourceId == sourceId }
                    .toImmutableList()
                mutableState.update { it.copy(pairs = myPairs, reviewItems = myItems) }
                loadEntryData(myPairs, myItems)
            }
        }
    }

    /**
     * Chapter/episode counts from the local DB, only where available —
     * and the live domain entries for the same ids, so covers load
     * through the source-aware fetcher (see EntrySide) instead of a
     * bare URL, and with the freshest thumbnail (the detail fetch may
     * have updated it since the pair was staged).
     */
    private suspend fun loadEntryData(
        pairs: ImmutableList<AutoMigrationStagedPair>,
        items: ImmutableList<AutoMigrationReviewItem>,
    ) {
        // Load only what this section shows: staged pairs for the
        // staged section, review items otherwise. Counts and entries
        // for the other sections' entries are never fetched.
        val ids = mutableSetOf<Long>()
        when (section) {
            AutoMigrationCompareScreen.SECTION_STAGED -> {
                pairs.forEach { ids += it.oldEntryId; ids += it.newEntryId }
            }
            AutoMigrationCompareScreen.SECTION_REVIEW -> {
                items.filter { it.candidates.isNotEmpty() }.forEach { item ->
                    ids += item.entryId
                    item.candidates.forEach { ids += it.entryId }
                }
            }
            else -> {
                items.filter { it.candidates.isEmpty() }.forEach { ids += it.entryId }
            }
        }
        val missing = ids.filter { it !in mutableState.value.counts }
        if (missing.isEmpty()) return
        val loadedCounts = mutableMapOf<Long, Int>()
        val loadedEntries = mutableMapOf<Long, Any>()
        for (id in missing) {
            val count = runCatching {
                if (isManga) {
                    getChaptersByMangaId.await(id).size
                } else {
                    getEpisodesByAnimeId.await(id).size
                }
            }.getOrNull() ?: 0
            loadedCounts[id] = count
            runCatching {
                if (isManga) getManga.await(id) else getAnime.await(id)
            }.getOrNull()?.let { loadedEntries[id] = it }
        }
        mutableState.update { it.copy(counts = it.counts + loadedCounts, entries = it.entries + loadedEntries) }
    }

    /** Takes a pair out of the staged set — the entry stays in the old source. */
    fun unstage(pair: AutoMigrationStagedPair) {
        store.removeStagedPair(pair.oldEntryId, isManga)
    }

    /**
     * Per-entry retry search: re-searches ONLY this entry with the
     * user-edited name (the typed query first, then the original title
     * parsed from the description). Same logic as the review screen's
     * research — the review item's candidates update in the store, so
     * this screen (which subscribes to the same flow) refreshes.
     */
    fun research(item: AutoMigrationReviewItem, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        screenModelScope.launchIO {
            mutableState.update { it.copy(searchingIds = it.searchingIds + item.entryId) }
            try {
                val candidates = if (isManga) {
                    val oldManga = getManga.await(item.entryId) ?: return@launchIO
                    val queries = listOfNotNull(
                        trimmed,
                        parseOriginalTitle(oldManga.description)?.trim()?.takeIf { it.isNotEmpty() },
                    ).distinct()
                    searcher.searchManga(oldManga, queries, sourceId).candidates
                } else {
                    val oldAnime = getAnime.await(item.entryId) ?: return@launchIO
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
                logcat(LogPriority.ERROR, e) { "Auto-migration compare: search failed for ${item.entryId}" }
            } finally {
                mutableState.update { it.copy(searchingIds = it.searchingIds - item.entryId) }
            }
        }
    }

    /** Excludes a non-exact entry from the migration (X button): it is
     * removed from the review list, stays in the old source, and
     * Confirmar never touches it. */
    fun exclude(item: AutoMigrationReviewItem) {
        store.removeReviewItem(item.entryId, isManga)
    }

    /** Stages a review pick (old -> candidate) so Confirmar applies it. */
    fun stageCandidate(item: AutoMigrationReviewItem, candidate: AutoMigrationCandidate) {
        screenModelScope.launchIO {
            mutableState.update { it.copy(stagingIds = it.stagingIds + item.entryId) }
            try {
                val oldTitle: String
                val oldThumbnail: String?
                if (isManga) {
                    val oldManga = getManga.await(item.entryId) ?: return@launchIO
                    oldTitle = oldManga.title
                    oldThumbnail = oldManga.thumbnailUrl
                } else {
                    val oldAnime = getAnime.await(item.entryId) ?: return@launchIO
                    oldTitle = oldAnime.title
                    oldThumbnail = oldAnime.thumbnailUrl
                }
                store.addStagedPair(
                    AutoMigrationStagedPair(
                        isManga = isManga,
                        sourceId = sourceId,
                        oldEntryId = item.entryId,
                        oldTitle = oldTitle,
                        oldThumbnailUrl = oldThumbnail,
                        oldSourceName = item.originSourceName,
                        newEntryId = candidate.entryId,
                        newSourceId = candidate.sourceId,
                        newSourceName = candidate.sourceName,
                        newTitle = candidate.title,
                        newThumbnailUrl = candidate.thumbnailUrl,
                        auto = false,
                        isSeasonType = candidate.isSeasonType,
                    ),
                )
                store.removeReviewItem(item.entryId, isManga)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Auto-migration compare: failed to stage ${item.entryId}" }
            } finally {
                mutableState.update { it.copy(stagingIds = it.stagingIds - item.entryId) }
            }
        }
    }

    data class State(
        val pairs: ImmutableList<AutoMigrationStagedPair> = persistentListOf(),
        val reviewItems: ImmutableList<AutoMigrationReviewItem> = persistentListOf(),
        val counts: Map<Long, Int> = emptyMap(),
        /** Live domain entries for the visible section's ids, so covers
         * load through the source-aware fetcher. */
        val entries: Map<Long, Any> = emptyMap(),
        val stagingIds: Set<Long> = emptySet(),
        val searchingIds: Set<Long> = emptySet(),
    )
}
