package eu.kanade.presentation.entries.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.data.cover.AlternativeCover
import eu.kanade.tachiyomi.data.cover.AlternativeCoverCandidate
import eu.kanade.tachiyomi.data.cover.AlternativeCoverRepository
import eu.kanade.tachiyomi.data.cover.AlternativeCoverSearchResult
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Pick an alternative cover for one entry, from public sources
 * (MangaDex / Jikan / Kitsu for manga; Kitsu / Jikan / TMDB for anime).
 * One at a time, chosen by the user. Tapping a cover applies it as that
 * entry's custom cover, always at full original size.
 *
 * The search box is editable: the first search uses the entry title, but
 * the user can type any name. If a search finds nothing, the entry's
 * alternative titles (other languages/synonyms) are tried too — a title
 * often lives under another name in another source (e.g. "Bulpyeonhan
 * Jinsil" in the library is "An Uncomfortable Truth" in MangaDex), so
 * candidates are NEVER filtered by name equality: they are shown as
 * they come and the user picks.
 */
@Composable
fun AlternativeCoverPickerDialog(
    title: String,
    alternativeTitles: List<String> = emptyList(),
    isManga: Boolean,
    onCoverSelected: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var queryText by remember { mutableStateOf(title) }
    var candidates by remember { mutableStateOf<List<AlternativeCoverCandidate>?>(null) }
    var selected by remember { mutableStateOf<AlternativeCoverCandidate?>(null) }
    var covers by remember { mutableStateOf<List<AlternativeCover>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var searchedAs by remember { mutableStateOf<String?>(null) }
    var emptySources by remember { mutableStateOf<List<String>>(emptyList()) }
    var tmdbNeedsKey by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun selectCandidate(candidate: AlternativeCoverCandidate) {
        selected = candidate
        if (candidate.source != AlternativeCoverRepository.SOURCE_MANGADEX) {
            // Single-poster source: no second request needed.
            covers = listOfNotNull(candidate.cover)
            return
        }
        covers = null
        scope.launch {
            try {
                covers = AlternativeCoverRepository.loadMangaDexCovers(
                    candidate.sourceId ?: return@launch,
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                error = true
            }
        }
    }

    fun searchWith(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        loading = true
        error = false
        selected = null
        covers = null
        candidates = null
        searchedAs = null
        emptySources = emptyList()
        tmdbNeedsKey = false
        scope.launch {
            try {
                // The typed query first; if it finds nothing, the
                // entry's alternative titles (other languages/synonyms).
                val queries = (listOf(trimmed) + alternativeTitles)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                var found: AlternativeCoverSearchResult? = null
                var usedQuery: String? = null
                for (q in queries) {
                    val result = AlternativeCoverRepository.searchAll(q, isManga)
                    if (result.candidates.isNotEmpty()) {
                        found = result
                        usedQuery = q
                        break
                    }
                    found = result
                }
                val result = found
                candidates = result?.candidates.orEmpty()
                emptySources = result?.emptySources.orEmpty()
                tmdbNeedsKey = result?.tmdbNeedsKey ?: false
                searchedAs = usedQuery
                loading = false
                val list = result?.candidates.orEmpty()
                // Single result, or the first result is the same title:
                // skip the candidate list and show its covers directly.
                // NEVER require the name to match: a candidate under
                // another name is still the right one to show.
                if (list.size == 1 ||
                    (list.isNotEmpty() && isExactMatch(trimmed, list.first().title))
                ) {
                    selectCandidate(list.first())
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                loading = false
                error = true
            }
        }
    }

    fun reload() {
        if (selected != null) {
            selected?.let { selectCandidate(it) }
        } else {
            searchWith(queryText)
        }
    }

    // First open: search with the entry title.
    LaunchedEffect(title, isManga) {
        searchWith(title)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected != null) {
                    IconButton(onClick = {
                        selected = null
                        covers = null
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                        )
                    }
                }
                Text(text = stringResource(MR.strings.alternative_covers))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 560.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        placeholder = { Text(text = stringResource(MR.strings.alternative_covers_search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { searchWith(queryText) },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { searchWith(queryText) }) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(MR.strings.action_search),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 8.dp),
                ) {
                    when {
                        loading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        error -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(text = stringResource(MR.strings.covers_search_failed))
                                TextButton(onClick = { reload() }) {
                                    Text(text = stringResource(MR.strings.action_retry))
                                }
                            }
                        }
                        selected != null -> {
                            val list = covers
                            if (list == null) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            } else if (list.isEmpty()) {
                                Text(
                                    text = stringResource(MR.strings.no_covers_found),
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(list) { cover ->
                                        Column(
                                            modifier = Modifier.clickable {
                                                onCoverSelected(cover.url)
                                            },
                                        ) {
                                            AsyncImage(
                                                model = cover.thumbnailUrl,
                                                contentDescription = cover.label,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .aspectRatio(2f / 3f)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                            )
                                            cover.label?.let { label ->
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(top = 4.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            val list = candidates
                            if (list.isNullOrEmpty()) {
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(text = stringResource(MR.strings.no_covers_found))
                                    if (tmdbNeedsKey) {
                                        Text(
                                            text = stringResource(MR.strings.alternative_covers_tmdb_key_missing),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                LazyColumn {
                                    val order = if (isManga) {
                                        listOf(
                                            AlternativeCoverRepository.SOURCE_MANGADEX,
                                            AlternativeCoverRepository.SOURCE_JIKAN,
                                            AlternativeCoverRepository.SOURCE_KITSU,
                                        )
                                    } else {
                                        listOf(
                                            AlternativeCoverRepository.SOURCE_KITSU,
                                            AlternativeCoverRepository.SOURCE_JIKAN,
                                            AlternativeCoverRepository.SOURCE_TMDB,
                                        )
                                    }
                                    val grouped = list.groupBy { it.source }
                                    if (searchedAs != null && searchedAs != queryText.trim()) {
                                        item {
                                            Text(
                                                text = stringResource(
                                                    MR.strings.alternative_covers_searched_as,
                                                    searchedAs!!,
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(bottom = 8.dp),
                                            )
                                        }
                                    }
                                    for (source in order) {
                                        val group = grouped[source].orEmpty()
                                        if (group.isEmpty()) continue
                                        item {
                                            Text(
                                                text = source,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                            )
                                        }
                                        items(group) { candidate ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectCandidate(candidate) }
                                                    .padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                AsyncImage(
                                                    model = candidate.cover?.thumbnailUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(48.dp, 72.dp)
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
                                                    candidate.subtitle?.let { subtitle ->
                                                        Text(
                                                            text = subtitle,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                                    contentDescription = null,
                                                )
                                            }
                                        }
                                    }
                                    if (emptySources.isNotEmpty() || tmdbNeedsKey) {
                                        item {
                                            val notes = buildList {
                                                if (emptySources.isNotEmpty()) {
                                                    add(
                                                        stringResource(
                                                            MR.strings.alternative_covers_no_results_in,
                                                            emptySources.joinToString(", "),
                                                        ),
                                                    )
                                                }
                                                if (tmdbNeedsKey) {
                                                    add(stringResource(MR.strings.alternative_covers_tmdb_key_missing))
                                                }
                                            }
                                            Text(
                                                text = notes.joinToString("\n"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 12.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_close))
            }
        },
    )
}

private fun isExactMatch(query: String, title: String): Boolean {
    fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    val normalizedQuery = norm(query)
    return normalizedQuery.isNotEmpty() && normalizedQuery == norm(title)
}
