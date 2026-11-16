package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.entries.components.aurora.PassiveDownloadStatus
import eu.kanade.presentation.entries.manga.components.ChapterDownloadAction
import eu.kanade.presentation.entries.manga.components.ChapterDownloadIndicator
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences

/**
 * Two-column chapter grid from the approved design. Chapters have no cover of
 * their own, so each card is a fixed aurora gradient (deterministic per
 * chapter id, so neighbours differ) with the chapter name, upload date and a
 * read check in the corner. Click, long-press selection, swipe actions,
 * download and in-progress bar behave exactly like [MangaChapterCardCompact].
 */

/** One LazyColumn row of the chapter grid: up to two chapters, or a gap. */
internal sealed interface MangaChapterGridRow {
    data class ChapterRow(val first: ChapterList.Item, val second: ChapterList.Item?) : MangaChapterGridRow
    data class MissingRow(val missing: ChapterList.MissingCount) : MangaChapterGridRow
}

/** Groups consecutive chapters into rows of two, keeping missing-count gaps in place. */
internal fun buildMangaChapterGridRows(chapters: List<ChapterList>): List<MangaChapterGridRow> {
    val rows = mutableListOf<MangaChapterGridRow>()
    var pending: ChapterList.Item? = null
    fun flushPending() {
        val first = pending
        if (first != null) {
            rows += MangaChapterGridRow.ChapterRow(first = first, second = null)
            pending = null
        }
    }
    for (entry in chapters) {
        when (entry) {
            is ChapterList.Item -> {
                val first = pending
                if (first == null) {
                    pending = entry
                } else {
                    rows += MangaChapterGridRow.ChapterRow(first = first, second = entry)
                    pending = null
                }
            }
            is ChapterList.MissingCount -> {
                flushPending()
                rows += MangaChapterGridRow.MissingRow(missing = entry)
            }
        }
    }
    flushPending()
    return rows
}

/**
 * Maps an auto-jump target chapter index (index among the flat chapter items)
 * to the LazyColumn item index of the grid row that contains it.
 */
internal fun resolveMangaAuroraGridTargetItemIndex(
    targetChapterIndex: Int,
    rows: List<MangaChapterGridRow>,
    preChapterItemCount: Int,
): Int {
    var seenChapters = 0
    rows.forEachIndexed { rowIndex, row ->
        if (row is MangaChapterGridRow.ChapterRow) {
            val rowSize = if (row.second != null) 2 else 1
            if (targetChapterIndex in seenChapters until seenChapters + rowSize) {
                return preChapterItemCount + rowIndex
            }
            seenChapters += rowSize
        }
    }
    return preChapterItemCount + (targetChapterIndex / 2).coerceAtLeast(0)
}

/** Aurora gradients for the grid cards; the design's default is pink → teal. */
private val mangaChapterGridGradients = listOf(
    listOf(Color(0xFFE0577A), Color(0xFF2E7E8C)),
    listOf(Color(0xFF5B7EC4), Color(0xFF2B3A67)),
    listOf(Color(0xFFC94F7C), Color(0xFF5B2A6E)),
    listOf(Color(0xFFD9A441), Color(0xFF6E7A2F)),
)

internal fun resolveMangaChapterGridGradient(chapterId: Long): List<Color> {
    val size = mangaChapterGridGradients.size
    val index = ((chapterId % size) + size).toInt() % size
    return mangaChapterGridGradients[index]
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaChapterGridCard(
    item: ChapterList.Item,
    selected: Boolean,
    isNew: Boolean,
    isAnyChapterSelected: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onChapterClicked: (Chapter) -> Unit,
    onLongClick: () -> Unit,
    onChapterSwipe: (LibraryPreferences.ChapterSwipeAction) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val chapter = item.chapter
    val gradientColors = remember(chapter.id) { resolveMangaChapterGridGradient(chapter.id) }
    val chapterProgressFraction = resolveMangaChapterProgressFraction(
        read = chapter.read,
        lastPageRead = chapter.lastPageRead,
    )
    val cardShape = RoundedCornerShape(16.dp)
    val startSwipeAction = auroraMangaSwipeAction(
        action = chapterSwipeStartAction,
        read = chapter.read,
        bookmark = chapter.bookmark,
        downloadState = item.downloadState,
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = { onChapterSwipe(chapterSwipeStartAction) },
    )
    val endSwipeAction = auroraMangaSwipeAction(
        action = chapterSwipeEndAction,
        read = chapter.read,
        bookmark = chapter.bookmark,
        downloadState = item.downloadState,
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = { onChapterSwipe(chapterSwipeEndAction) },
    )

    val chapterCard: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .clip(cardShape)
                .background(Brush.verticalGradient(gradientColors), cardShape)
                .then(
                    when {
                        selected -> Modifier.border(2.dp, colors.accent, cardShape)
                        isNew && !chapter.read -> Modifier.border(
                            1.5.dp,
                            colors.accent.copy(alpha = 0.7f),
                            cardShape,
                        )
                        else -> Modifier
                    },
                )
                .combinedClickable(
                    onClick = { onChapterClicked(chapter) },
                    onLongClick = onLongClick,
                ),
        ) {
            // Bottom scrim so the title stays readable on any gradient.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.35f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.55f),
                            ),
                        ),
                    ),
            )

            // Read / selection check in the top-end corner.
            if (isAnyChapterSelected || chapter.read) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                colors.accent
                            } else {
                                Color.White.copy(alpha = 0.28f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected || chapter.read) {
                        Icon(
                            imageVector = Icons.Filled.Done,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = chapter.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (chapter.bookmark) {
                            Icon(
                                imageVector = Icons.Outlined.Bookmark,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(12.dp),
                            )
                        }
                        Text(
                            text = relativeDateTimeText(chapter.dateUpload),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (isAnyChapterSelected) {
                    PassiveDownloadStatus(
                        downloaded = item.downloadState == MangaDownload.State.DOWNLOADED,
                        downloading = item.downloadState == MangaDownload.State.QUEUE ||
                            item.downloadState == MangaDownload.State.DOWNLOADING,
                    )
                } else if (onDownloadChapter != null) {
                    ChapterDownloadIndicator(
                        enabled = true,
                        downloadStateProvider = { item.downloadState },
                        downloadProgressProvider = { item.downloadProgress },
                        onClick = { onDownloadChapter(listOf(item), it) },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Progress bar for read/in-progress chapters.
            if (chapterProgressFraction != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.3f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(chapterProgressFraction)
                            .height(3.dp)
                            .background(Color.White),
                    )
                }
            }
        }
    }

    if (isAnyChapterSelected) {
        Box(modifier = modifier) { chapterCard() }
    } else {
        SwipeableActionsBox(
            modifier = modifier.clipToBounds(),
            startActions = listOfNotNull(startSwipeAction),
            endActions = listOfNotNull(endSwipeAction),
            swipeThreshold = auroraSwipeActionThreshold,
            backgroundUntilSwipeThreshold = Color.Transparent,
        ) {
            chapterCard()
        }
    }
}
