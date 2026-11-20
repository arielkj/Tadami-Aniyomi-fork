package eu.kanade.tachiyomi.ui.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.updates.anime.TmdbDetailDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.tmdb.TmdbUpcomingItem
import eu.kanade.tachiyomi.data.upcoming.UpcomingReleasesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

/**
 * v7.9.2 — "Ver todo" de películas o series (TMDB): la lista completa de
 * próximos estrenos y, arriba, la lista local "Por ver" guardada.
 */
class TmdbUpcomingScreen(
    private val mediaType: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val colors = AuroraTheme.colors
        val model = rememberScreenModel { Model(mediaType) }
        val state by model.state.collectAsStateWithLifecycle()
        var detail by remember { mutableStateOf<TmdbUpcomingItem?>(null) }

        val title = if (mediaType == "tv") {
            stringResource(AYMR.strings.aurora_upcoming_series)
        } else {
            stringResource(AYMR.strings.aurora_upcoming_movies)
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = title,
                    navigateUp = navigator::pop,
                )
            },
        ) { paddingValues ->
            if (!state.keySet) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.aurora_tmdb_key_needed),
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                    )
                }
                return@Scaffold
            }
            if (state.loading && state.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.accent)
                }
                return@Scaffold
            }
            LazyColumn(
                contentPadding = paddingValues,
                modifier = Modifier.fillMaxSize(),
            ) {
                val saved = state.watchlist.values
                    .filter { it.mediaType == mediaType }
                    .sortedBy { it.date ?: LocalDate.MAX }
                if (saved.isNotEmpty()) {
                    item(key = "watchlist-header") {
                        Text(
                            text = stringResource(AYMR.strings.aurora_watchlist),
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    items(items = saved, key = { it.key }) { entry ->
                        WatchlistRow(
                            entry = entry,
                            onToggleWatched = { model.setWatched(entry.key, !entry.watched) },
                            onRemove = { model.removeFromWatchlist(entry.key) },
                        )
                    }
                    item(key = "watchlist-spacer") {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                if (state.items.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(AYMR.strings.aurora_no_upcoming_tmdb),
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    items(items = state.items, key = { it.watchlistKey }) { item ->
                        UpcomingRow(
                            item = item,
                            inWatchlist = state.watchlist.containsKey(item.watchlistKey),
                            onClick = { detail = item },
                            onToggleWatchlist = { model.toggleWatchlist(item) },
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }

        val detailItem = detail
        if (detailItem != null) {
            val inWatchlist = state.watchlist[detailItem.watchlistKey]
            TmdbDetailDialog(
                item = detailItem,
                inWatchlist = inWatchlist != null,
                watched = inWatchlist?.watched == true,
                onToggleWatchlist = { model.toggleWatchlist(detailItem) },
                onToggleWatched = { watched ->
                    state.watchlist[detailItem.watchlistKey]?.let {
                        model.setWatched(it.key, watched)
                    }
                },
                onDismiss = { detail = null },
            )
        }
    }

    private class Model(
        private val mediaType: String,
        private val uiPreferences: UiPreferences = Injekt.get(),
        private val repository: UpcomingReleasesRepository = UpcomingReleasesRepository(),
    ) : ScreenModel {

        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state.asStateFlow()

        init {
            // La clave manda: cargar al arrancar y cuando cambia. La lista
            // Por ver se actualiza sola sin volver a pedir nada a TMDB.
            screenModelScope.launch {
                uiPreferences.tmdbApiKey().changes().collect { key ->
                    val keySet = key.trim().isNotEmpty()
                    _state.update { it.copy(keySet = keySet) }
                    if (keySet) load(key.trim())
                }
            }
            screenModelScope.launch {
                uiPreferences.tmdbWatchlist().changes().collect { json ->
                    _state.update {
                        it.copy(watchlist = UpdatesTopSectionScreenModel.parseWatchlist(json))
                    }
                }
            }
        }

        private fun load(apiKey: String) {
            screenModelScope.launch {
                _state.update { it.copy(loading = true) }
                val items = try {
                    if (mediaType == "tv") {
                        repository.fetchUpcomingSeries(apiKey)
                    } else {
                        repository.fetchUpcomingMovies(apiKey)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "TmdbUpcoming: fetch failed" }
                    emptyList()
                }
                _state.update { it.copy(items = items, loading = false) }
            }
        }

        fun toggleWatchlist(item: TmdbUpcomingItem) {
            val current = UpdatesTopSectionScreenModel.parseWatchlist(
                uiPreferences.tmdbWatchlist().get(),
            ).toMutableMap()
            val key = item.watchlistKey
            if (current.remove(key) == null) {
                current[key] = WatchlistEntry(
                    key = key,
                    title = item.title,
                    posterUrl = item.posterUrl,
                    date = item.date?.let { LocalDate.parse(it) },
                    mediaType = item.mediaType,
                    watched = false,
                )
            }
            uiPreferences.tmdbWatchlist().set(
                UpdatesTopSectionScreenModel.serializeWatchlist(current),
            )
        }

        fun setWatched(key: String, watched: Boolean) {
            val current = UpdatesTopSectionScreenModel.parseWatchlist(
                uiPreferences.tmdbWatchlist().get(),
            ).toMutableMap()
            current[key]?.let { current[key] = it.copy(watched = watched) }
            uiPreferences.tmdbWatchlist().set(
                UpdatesTopSectionScreenModel.serializeWatchlist(current),
            )
        }

        fun removeFromWatchlist(key: String) {
            val current = UpdatesTopSectionScreenModel.parseWatchlist(
                uiPreferences.tmdbWatchlist().get(),
            ).toMutableMap()
            if (current.remove(key) != null) {
                uiPreferences.tmdbWatchlist().set(
                    UpdatesTopSectionScreenModel.serializeWatchlist(current),
                )
            }
        }

        data class State(
            val items: List<TmdbUpcomingItem> = emptyList(),
            val loading: Boolean = true,
            val keySet: Boolean = false,
            val watchlist: Map<String, WatchlistEntry> = emptyMap(),
        )
    }
}

@Composable
private fun UpcomingRow(
    item: TmdbUpcomingItem,
    inWatchlist: Boolean,
    onClick: () -> Unit,
    onToggleWatchlist: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val date = item.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            error = placeholderPainter,
            fallback = placeholderPainter,
            modifier = Modifier
                .size(48.dp, 72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.cardBackground),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (date != null) {
                Text(
                    text = date.toString(),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
            if (item.overview.isNotBlank()) {
                Text(
                    text = item.overview,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onToggleWatchlist,
            modifier = Modifier
                .size(36.dp)
                .background(colors.accent, CircleShape),
        ) {
            Icon(
                imageVector = if (inWatchlist) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = null,
                tint = colors.textOnAccent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun WatchlistRow(
    entry: WatchlistEntry,
    onToggleWatched: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = entry.posterUrl,
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            error = placeholderPainter,
            fallback = placeholderPainter,
            modifier = Modifier
                .size(48.dp, 72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.cardBackground),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.date != null) {
                    Text(
                        text = entry.date.toString(),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (entry.watched) {
                        stringResource(AYMR.strings.aurora_watched)
                    } else {
                        stringResource(AYMR.strings.aurora_watchlist)
                    },
                    color = if (entry.watched) colors.accent else colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (entry.watched) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        TextButton(onClick = onToggleWatched) {
            Text(
                text = if (entry.watched) {
                    stringResource(AYMR.strings.aurora_mark_unwatched)
                } else {
                    stringResource(AYMR.strings.aurora_mark_watched)
                },
                color = colors.accent,
                fontSize = 12.sp,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(AYMR.strings.aurora_watchlist_removed),
                tint = colors.textSecondary,
            )
        }
    }
}
