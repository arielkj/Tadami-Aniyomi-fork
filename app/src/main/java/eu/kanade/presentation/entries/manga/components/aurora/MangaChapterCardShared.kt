package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.Icon
import androidx.compose.material3.contentColorFor
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.ui.reader.decodePagedChapterProgress
import eu.kanade.tachiyomi.ui.reader.decodeWebtoonScrollProgress
import tachiyomi.domain.library.service.LibraryPreferences

/**
 * Helpers shared by the manga chapter cards ([MangaChapterCardCompact] and
 * [MangaChapterGridCard]): read-progress fraction and the swipe actions.
 * Moved here so both cards stay in sync instead of duplicating the logic.
 */

internal fun resolveMangaChapterProgressFraction(
    read: Boolean,
    lastPageRead: Long,
): Float? {
    if (read) return null
    decodePagedChapterProgress(lastPageRead)?.let { progress ->
        if (progress.totalPages <= 0) return null
        return ((progress.index + 1).toFloat() / progress.totalPages.toFloat())
            .coerceIn(0f, 1f)
            .takeIf { it > 0f }
    }

    val webtoonProgress = decodeWebtoonScrollProgress(lastPageRead)
    val totalPages = webtoonProgress?.totalPages
    if (webtoonProgress != null && totalPages != null && totalPages > 0) {
        val currentPageProgress = webtoonProgress.offsetRatioPpm
            ?.let { ratio -> ratio.toFloat() / 1_000_000f }
            ?: 1f
        return ((webtoonProgress.index.toFloat() + currentPageProgress) / totalPages.toFloat())
            .coerceIn(0f, 1f)
            .takeIf { it > 0f }
    }

    return null
}

internal fun auroraMangaSwipeAction(
    action: LibraryPreferences.ChapterSwipeAction,
    read: Boolean,
    bookmark: Boolean,
    downloadState: MangaDownload.State,
    background: Color,
    onSwipe: () -> Unit,
): me.saket.swipe.SwipeAction? {
    return when (action) {
        LibraryPreferences.ChapterSwipeAction.ToggleRead -> auroraSwipeAction(
            icon = if (!read) Icons.Outlined.Done else Icons.Outlined.RemoveDone,
            background = background,
            isUndo = read,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> auroraSwipeAction(
            icon = if (!bookmark) Icons.Outlined.BookmarkAdd else Icons.Outlined.BookmarkRemove,
            background = background,
            isUndo = bookmark,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.Download -> auroraSwipeAction(
            icon = when (downloadState) {
                MangaDownload.State.NOT_DOWNLOADED, MangaDownload.State.ERROR -> Icons.Outlined.Download
                MangaDownload.State.QUEUE, MangaDownload.State.DOWNLOADING -> Icons.Outlined.FileDownloadOff
                MangaDownload.State.DOWNLOADED -> Icons.Outlined.Delete
            },
            background = background,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.Disabled -> null
    }
}

private fun auroraSwipeAction(
    onSwipe: () -> Unit,
    icon: ImageVector,
    background: Color,
    isUndo: Boolean = false,
): me.saket.swipe.SwipeAction {
    return me.saket.swipe.SwipeAction(
        icon = {
            Icon(
                modifier = Modifier.padding(16.dp),
                imageVector = icon,
                tint = contentColorFor(background),
                contentDescription = null,
            )
        },
        background = background,
        onSwipe = onSwipe,
        isUndo = isUndo,
    )
}

internal val auroraSwipeActionThreshold = 56.dp
