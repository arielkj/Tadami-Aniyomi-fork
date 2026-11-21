package eu.kanade.presentation.library.manga

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.item
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.EntryCompactGridItem
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.LazyLibraryGrid
import eu.kanade.presentation.library.components.PinnedBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.presentation.library.components.globalSearchItem
import eu.kanade.presentation.library.components.idsToHashSet
import eu.kanade.presentation.library.components.shouldShowContinueViewingAction
import eu.kanade.presentation.library.manga.components.SeriesStackedCoverCard
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.series.model.SeriesCoverMode

@Composable
internal fun MangaLibraryCompactGrid(
    items: List<MangaLibraryItem>,
    showTitle: Boolean,
    columns: Int,
    contentPadding: PaddingValues,
    selection: List<MangaLibraryItem>,
    selectedIds: Set<Long> = selection.idsToHashSet { it.id },
    state: LazyGridState = rememberLazyGridState(),
    onClick: (MangaLibraryItem) -> Unit,
    onSeriesClicked: (Long) -> Unit,
    onLongClick: (MangaLibraryItem) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    onTogglePinned: (MangaLibraryItem) -> Unit,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    // v7.9.8: el destacado + carrusel y las píldoras también salen en la
    // rejilla compacta con tarjetas Estándar — antes esta rejilla ni
    // siquiera los recibía y era la causa de que en Títulos no saliera nada.
    headerContent: (@Composable () -> Unit)? = null,
    categoryTabsContent: (@Composable () -> Unit)? = null,
) {
    LazyLibraryGrid(
        state = state,
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        // v7.9.8: destacado + carrusel arriba y las píldoras debajo, TODO
        // como ítems normales que se desplazan con el contenido (igual que
        // en MangaLibraryAuroraCardGrid): al bajar desaparecen y al subir
        // vuelven; nada queda fijo.
        if (headerContent != null) {
            item(
                span = { GridItemSpan(maxLineSpan) },
                contentType = "library_featured_header",
            ) {
                headerContent()
            }
        }

        if (categoryTabsContent != null) {
            item(
                span = { GridItemSpan(maxLineSpan) },
                contentType = "library_category_tabs",
            ) {
                categoryTabsContent()
            }
        }

        items(
            items = items,
            key = { it.id },
            contentType = { "manga_library_compact_grid_item" },
        ) { libraryItem ->
            val manga = libraryItem.coverManga ?: libraryItem.libraryManga.manga
            val isSeries = libraryItem is MangaLibraryItem.Series
            val notSelectionMode = selection.isEmpty()
            val title = if (isSeries) libraryItem.title else manga.displayTitle
            val isSelected = selectedIds.contains(libraryItem.id)
            val targetManga = if (isSeries) {
                libraryItem.librarySeries.entries.firstOrNull {
                    it.manga.id == libraryItem.librarySeries.activeManga?.id
                } ?: libraryItem.libraryManga
            } else {
                libraryItem.libraryManga
            }
            // V7: clave solo con los campos de la portada, no el manga entero.
            val coverData = remember(manga.id, manga.source, manga.favorite, manga.thumbnailUrl, manga.coverLastModified) {
                MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                )
            }
            EntryCompactGridItem(
                isSelected = isSelected,
                title = title.takeIf { showTitle },
                coverData = coverData,
                customCover = if (isSeries && libraryItem.librarySeries.series.coverMode == SeriesCoverMode.AUTO) {
                    {
                        SeriesStackedCoverCard(
                            covers = libraryItem.covers,
                            isSelected = isSelected,
                        )
                    }
                } else {
                    null
                },
                coverBadgeStart = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnviewedBadge(count = libraryItem.unreadCount)
                },
                coverBadgeEnd = {
                    LanguageBadge(
                        isLocal = libraryItem.isLocal,
                        sourceLanguage = libraryItem.sourceLanguage,
                    )
                },
                topEndBadge = if (libraryItem.pinned) {
                    { PinnedBadge() }
                } else {
                    null
                },
                menuContent = null,
                onLongClick = { onLongClick(libraryItem) },
                onClick = {
                    if (notSelectionMode && isSeries) {
                        onSeriesClicked(libraryItem.librarySeries.id)
                    } else {
                        onClick(libraryItem)
                    }
                },
                onClickContinueViewing = if (
                    shouldShowContinueViewingAction(
                        hasContinueAction = onClickContinueReading != null,
                        remainingCount = libraryItem.unreadCount,
                    )
                ) {
                    { onClickContinueReading?.invoke(targetManga) }
                } else {
                    null
                },
            )
        }
    }
}
