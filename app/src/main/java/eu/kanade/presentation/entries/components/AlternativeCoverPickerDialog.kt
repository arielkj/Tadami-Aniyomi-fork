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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.data.cover.AlternativeCover
import eu.kanade.tachiyomi.data.cover.AlternativeCoverCandidate
import eu.kanade.tachiyomi.data.cover.AlternativeCoverRepository
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Pick an alternative cover for one entry, from a public source:
 * MangaDex for manga, Kitsu for anime. One at a time, chosen by the
 * user. Tapping a cover applies it as that entry's custom cover.
 */
@Composable
fun AlternativeCoverPickerDialog(
    title: String,
    isManga: Boolean,
    onCoverSelected: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var candidates by remember { mutableStateOf<List<AlternativeCoverCandidate>?>(null) }
    var selected by remember { mutableStateOf<AlternativeCoverCandidate?>(null) }
    var covers by remember { mutableStateOf<List<AlternativeCover>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    fun selectCandidate(candidate: AlternativeCoverCandidate) {
        selected = candidate
        if (!isManga) {
            // Kitsu: one poster per candidate, no second request needed.
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

    fun loadCandidates() {
        loading = true
        error = false
        scope.launch {
            try {
                val found = if (isManga) {
                    AlternativeCoverRepository.searchMangaDex(title)
                } else {
                    AlternativeCoverRepository.searchKitsu(title)
                }
                candidates = found
                loading = false
                // Single result, or the first result is the same title:
                // skip the candidate list and show its covers directly.
                if (found.size == 1 ||
                    (found.isNotEmpty() && isExactMatch(title, found.first().title))
                ) {
                    selectCandidate(found.first())
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                loading = false
                error = true
            }
        }
    }

    fun reload() {
        val current = selected
        if (current == null) {
            loadCandidates()
        } else {
            selectCandidate(current)
        }
    }

    LaunchedEffect(title, isManga, retry) {
        reload()
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 520.dp),
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
                            TextButton(onClick = { retry++ }) {
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
                            Text(
                                text = stringResource(MR.strings.no_covers_found),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            LazyColumn {
                                items(list) { candidate ->
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
