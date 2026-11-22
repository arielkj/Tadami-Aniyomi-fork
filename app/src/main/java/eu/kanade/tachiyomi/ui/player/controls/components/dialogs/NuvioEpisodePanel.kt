package eu.kanade.tachiyomi.ui.player.controls.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.data.database.models.anime.Episode
import eu.kanade.tachiyomi.util.lang.toRelativeString
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Nuvio-style episodes panel: a dark panel sliding in from the right with
 * the "Episodios" title, a close pill, the season pill and one card per
 * episode (anime cover thumbnail with a "T1 E8" badge, title and air
 * date, checkmark on the episode currently playing). Tapping a card
 * plays that episode.
 */
@Composable
fun NuvioEpisodePanel(
    displayMode: Long?,
    currentEpisodeIndex: Int,
    episodeList: List<Episode>,
    dateRelativeTime: Boolean,
    dateFormat: DateTimeFormatter,
    anime: Anime,
    onEpisodeClicked: (Long?) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isLandscape =
        LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val panelWidthFraction = if (isLandscape) 0.62f else 0.88f
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentEpisodeIndex - 1).coerceAtLeast(0),
    )
    val seasonNumber = formatEpisodeNumber(anime.seasonNumber)

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim: tap outside to close.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismissRequest),
        )
        // Panel.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(panelWidthFraction)
                .widthIn(max = 640.dp)
                .background(Color.Black),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MaterialTheme.padding.medium),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.episodes),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF2A2A2A),
                        modifier = Modifier.clickable(onClick = onDismissRequest),
                    ) {
                        Text(
                            text = stringResource(MR.strings.action_close),
                            fontSize = 14.sp,
                            color = Color.White,
                            modifier = Modifier.padding(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.small,
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                ) {
                    Text(
                        text = stringResource(
                            AYMR.strings.display_mode_season,
                            seasonNumber,
                        ),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = episodeList,
                        key = { _, episode -> "episode-${episode.id}" },
                        contentType = { _, _ -> "episode" },
                    ) { index, episode ->
                        val isCurrentEpisode = index == currentEpisodeIndex
                        val title = if (displayMode == Anime.EPISODE_DISPLAY_NUMBER) {
                            stringResource(
                                AYMR.strings.display_mode_episode,
                                formatEpisodeNumber(episode.episode_number.toDouble()),
                            )
                        } else {
                            episode.name
                        }
                        val date = episode.date_upload
                            .takeIf { it > 0L }
                            ?.let {
                                LocalDate.ofInstant(
                                    Instant.ofEpochMilli(it),
                                    ZoneId.systemDefault(),
                                ).toRelativeString(
                                    context = context,
                                    relative = dateRelativeTime,
                                    dateFormat = dateFormat,
                                )
                            } ?: ""

                        NuvioEpisodeCard(
                            title = title,
                            date = date,
                            badge = "T$seasonNumber E${formatEpisodeNumber(episode.episode_number.toDouble())}",
                            anime = anime,
                            isCurrentEpisode = isCurrentEpisode,
                            onClick = { onEpisodeClicked(episode.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NuvioEpisodeCard(
    title: String,
    date: String,
    badge: String,
    anime: Anime,
    isCurrentEpisode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrentEpisode) Color(0xFF1E1E1E) else Color(0xFF161616))
            .then(
                if (isCurrentEpisode) {
                    Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 68.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            AsyncImage(
                model = anime.asAnimeCover(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
            ) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (isCurrentEpisode) {
                Surface(
                    color = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(MaterialTheme.padding.small))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = if (isCurrentEpisode) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
            )
            if (date.isNotBlank()) {
                Text(
                    text = date,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
    }
}
