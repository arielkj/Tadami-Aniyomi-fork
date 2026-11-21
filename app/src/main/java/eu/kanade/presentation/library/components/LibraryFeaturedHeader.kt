package eu.kanade.presentation.library.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.ui.home.prefetchHomeHubCovers
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryItem
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.random.Random

/**
 * v7.9.6 — destacado + "Seguir viendo" de Títulos (diseño aprobado por el
 * usuario el 20 Nov: banner grande arriba, carrusel debajo, secciones
 * debajo del carrusel). La misma estructura para Anime y Manga.
 *
 * v7.9.10 — las tarjetas son las nuevas TitleCardTop (recomendaciones) y
 * TitleCardSide ("Seguir viendo"), con barra de progreso: el banner ya no
 * es una imagen a sangre con el título encima, sino una tarjeta con la
 * portada 4:3 arriba y los datos debajo; el carrusel ya no es una tarjeta
 * ancha con la portada arriba, sino una tarjeta de lado con la portada
 * vertical a la izquierda. El paginador del destacado (hasta 8, con
 * puntos) y el orden del carrusel (lo último visto/leído primero) no
 * cambian. Tocar la tarjeta del destacado abre los detalles; tocar una
 * del carrusel va DIRECTO a ese episodio/capítulo (no al detalle).
 *
 * v7.9.12 — el destacado usa el MISMO formato de lado que el carrusel
 * (TitleCardSide): el usuario pidió que las recomendaciones lleven ese
 * formato, no la tarjeta vertical. TitleCardTop se eliminó (quedó sin
 * uso) y el fondo ancho (backdropData) ya no se usa para el destacado.
 */

// Valores de UiPreferences.auroraLibraryFeaturedSource().
const val AURORA_LIBRARY_FEATURED_SOURCE_IN_PROGRESS = "in_progress"
const val AURORA_LIBRARY_FEATURED_SOURCE_ALL = "all"
const val AURORA_LIBRARY_FEATURED_SOURCE_PINNED = "pinned"

private const val MAX_FEATURED_BANNER_ITEMS = 8
private const val MAX_CONTINUE_ITEMS = 12

/**
 * Entrada ya resuelta para el destacado y el carrusel: la UI es la misma
 * para anime y manga, solo cambia de dónde sale cada dato.
 */
data class LibraryFeaturedEntry(
    val key: Long,
    val title: String,
    val type: String,
    val genre: String?,
    val total: Int,
    val current: Int,
    val status: String,
    val coverData: Any?,
    val hasNew: Boolean,
    val onDetailsClick: () -> Unit,
    val onContinueClick: () -> Unit,
)

@Composable
fun AnimeLibraryFeaturedHeader(
    items: List<AnimeLibraryItem>,
    featuredSource: String,
    seed: Int,
    onDetailsClick: (AnimeLibraryItem) -> Unit,
    onContinueClick: (AnimeLibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // En curso: empezado y con algo sin ver, lo último visto primero.
    val inProgress = remember(items) {
        items.filter { it.seenCount > 0 && it.unseenCount > 0 }
            .sortedByDescending { it.lastSeen }
    }
    // La tanda del banner depende del ajuste (En curso / Toda la
    // biblioteca / Favoritos); si la tanda elegida sale vacía se cae a
    // los en curso y luego a toda la categoría, para no dejar el
    // destacado vacío teniendo títulos.
    val featuredPool = remember(items, featuredSource, inProgress) {
        val selected = when (featuredSource) {
            AURORA_LIBRARY_FEATURED_SOURCE_ALL -> items
            AURORA_LIBRARY_FEATURED_SOURCE_PINNED -> items.filter { it.pinned }
            else -> inProgress
        }
        selected.ifEmpty { inProgress }.ifEmpty { items }
    }
    val featuredPoolShuffled = remember(featuredPool, seed) {
        if (featuredPool.isEmpty()) {
            emptyList()
        } else {
            featuredPool.shuffled(Random(seed)).take(MAX_FEATURED_BANNER_ITEMS)
        }
    }
    // El mapeo a entradas se hace en composición (toFeaturedEntry es
    // @Composable: lee strings y recuerda la portada con la clave V7);
    // solo el barajado va en remember para que no cambie a cada
    // recomposición.
    val featuredEntries = featuredPoolShuffled.map { it.toFeaturedEntry(onDetailsClick, onContinueClick) }
    val continueEntries = inProgress.take(MAX_CONTINUE_ITEMS)
        .map { it.toFeaturedEntry(onDetailsClick, onContinueClick) }
    // Fluidez: precalienta las portadas del destacado y del carrusel en la
    // caché de Coil (mismo patrón que el prefetch del inicio) para que al
    // entrar a Títulos ya estén y no parpadeen los placeholders al
    // desplazar el banner o el carrusel.
    LaunchedEffect(featuredPool, inProgress) {
        prefetchHomeHubCovers(
            (featuredPool.take(MAX_FEATURED_BANNER_ITEMS) + inProgress.take(MAX_CONTINUE_ITEMS))
                .map { it.featuredCoverData() },
        )
    }
    LibraryFeaturedHeaderContent(
        featuredEntries = featuredEntries,
        continueEntries = continueEntries,
        modifier = modifier,
    )
}

@Composable
fun MangaLibraryFeaturedHeader(
    items: List<MangaLibraryItem>,
    featuredSource: String,
    seed: Int,
    onDetailsClick: (MangaLibraryItem) -> Unit,
    onContinueClick: (MangaLibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inProgress = remember(items) {
        items.filter { it.readCount > 0 && it.unreadCount > 0 }
            .sortedByDescending { it.lastRead }
    }
    val featuredPool = remember(items, featuredSource, inProgress) {
        val selected = when (featuredSource) {
            AURORA_LIBRARY_FEATURED_SOURCE_ALL -> items
            AURORA_LIBRARY_FEATURED_SOURCE_PINNED -> items.filter { it.pinned }
            else -> inProgress
        }
        selected.ifEmpty { inProgress }.ifEmpty { items }
    }
    val featuredPoolShuffled = remember(featuredPool, seed) {
        if (featuredPool.isEmpty()) {
            emptyList()
        } else {
            featuredPool.shuffled(Random(seed)).take(MAX_FEATURED_BANNER_ITEMS)
        }
    }
    // El mapeo a entradas se hace en composición (ver el comentario del
    // destacado de anime).
    val featuredEntries = featuredPoolShuffled.map { it.toFeaturedEntry(onDetailsClick, onContinueClick) }
    val continueEntries = inProgress.take(MAX_CONTINUE_ITEMS)
        .map { it.toFeaturedEntry(onDetailsClick, onContinueClick) }
    LaunchedEffect(featuredPool, inProgress) {
        prefetchHomeHubCovers(
            (featuredPool.take(MAX_FEATURED_BANNER_ITEMS) + inProgress.take(MAX_CONTINUE_ITEMS))
                .map { it.featuredCoverData() },
        )
    }
    LibraryFeaturedHeaderContent(
        featuredEntries = featuredEntries,
        continueEntries = continueEntries,
        modifier = modifier,
    )
}

@Composable
private fun LibraryFeaturedHeaderContent(
    featuredEntries: List<LibraryFeaturedEntry>,
    continueEntries: List<LibraryFeaturedEntry>,
    modifier: Modifier = Modifier,
) {
    if (featuredEntries.isEmpty() && continueEntries.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        if (featuredEntries.isNotEmpty()) {
            LibraryFeaturedBanner(entries = featuredEntries)
        }
        if (continueEntries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(AYMR.strings.aurora_continue_watching),
                color = AuroraTheme.colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = continueEntries,
                    key = { it.key },
                    contentType = { "library_continue_card" },
                ) { entry ->
                    TitleCardSide(
                        cover = entry.coverData,
                        title = entry.title,
                        type = entry.type,
                        genre = entry.genre,
                        total = entry.total,
                        current = entry.current,
                        status = entry.status,
                        hasNew = entry.hasNew,
                        onClick = entry.onContinueClick,
                        modifier = Modifier.width(320.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryFeaturedBanner(entries: List<LibraryFeaturedEntry>) {
    val colors = AuroraTheme.colors
    val pagerState = rememberPagerState { entries.size }
    Column(modifier = Modifier.fillMaxWidth()) {
        // v7.9.12: cada página del destacado es una TitleCardSide (la
        // portada vertical a la izquierda y los datos con la barra a la
        // derecha), el mismo formato que el carrusel de "Seguir viendo":
        // así lo pidió el usuario. La altura del paginador la marca el
        // contenido y animateContentSize suaviza el cambio de altura al
        // pasar de página, en vez de dar el salto.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .animateContentSize(),
        ) { page ->
            val entry = entries[page]
            TitleCardSide(
                cover = entry.coverData,
                title = entry.title,
                type = entry.type,
                genre = entry.genre,
                total = entry.total,
                current = entry.current,
                status = entry.status,
                hasNew = entry.hasNew,
                onClick = entry.onDetailsClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (entries.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                entries.forEachIndexed { index, _ ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(
                                width = if (selected) 22.dp else 7.dp,
                                height = 7.dp,
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (selected) {
                                    colors.accent
                                } else {
                                    colors.textSecondary.copy(alpha = 0.4f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Resolución de entradas (anime / manga)
// ---------------------------------------------------------------------------

@Composable
private fun AnimeLibraryItem.toFeaturedEntry(
    onDetailsClick: (AnimeLibraryItem) -> Unit,
    onContinueClick: (AnimeLibraryItem) -> Unit,
): LibraryFeaturedEntry {
    val anime = coverAnime ?: libraryAnime.anime
    val genre = anime.displayGenre?.firstOrNull()
    val coverData = remember(
        anime.id,
        anime.source,
        anime.favorite,
        anime.thumbnailUrl,
        anime.coverLastModified,
    ) {
        AnimeCover(
            animeId = anime.id,
            sourceId = anime.source,
            isAnimeFavorite = anime.favorite,
            url = anime.thumbnailUrl,
            lastModified = anime.coverLastModified,
        )
    }
    return LibraryFeaturedEntry(
        key = id,
        title = title,
        type = stringResource(AYMR.strings.label_anime),
        genre = genre,
        total = totalEpisodes.toInt(),
        current = seenCount.toInt(),
        status = auroraLibraryEntryStatusText(anime.status),
        coverData = coverData,
        hasNew = unseenCount > 0,
        onDetailsClick = { onDetailsClick(this) },
        onContinueClick = { onContinueClick(this) },
    )
}

@Composable
private fun MangaLibraryItem.toFeaturedEntry(
    onDetailsClick: (MangaLibraryItem) -> Unit,
    onContinueClick: (MangaLibraryItem) -> Unit,
): LibraryFeaturedEntry {
    val manga = coverManga ?: libraryManga.manga
    val genre = manga.displayGenre?.firstOrNull()
    val coverData = remember(
        manga.id,
        manga.source,
        manga.favorite,
        manga.thumbnailUrl,
        manga.coverLastModified,
    ) {
        MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = manga.favorite,
            url = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
        )
    }
    return LibraryFeaturedEntry(
        key = id,
        title = title,
        type = stringResource(AYMR.strings.label_manga),
        genre = genre,
        total = totalChapters.toInt(),
        current = readCount.toInt(),
        status = auroraLibraryEntryStatusText(manga.status),
        coverData = coverData,
        hasNew = unreadCount > 0,
        onDetailsClick = { onDetailsClick(this) },
        onContinueClick = { onContinueClick(this) },
    )
}

private fun AnimeLibraryItem.featuredCoverData(): Any? {
    val anime = coverAnime ?: libraryAnime.anime
    return AnimeCover(
        animeId = anime.id,
        sourceId = anime.source,
        isAnimeFavorite = anime.favorite,
        url = anime.thumbnailUrl,
        lastModified = anime.coverLastModified,
    )
}

private fun MangaLibraryItem.featuredCoverData(): Any? {
    val manga = coverManga ?: libraryManga.manga
    return MangaCover(
        mangaId = manga.id,
        sourceId = manga.source,
        isMangaFavorite = manga.favorite,
        url = manga.thumbnailUrl,
        lastModified = manga.coverLastModified,
    )
}

@Composable
private fun auroraLibraryEntryStatusText(status: Long): String {
    // SAnime y SManga comparten los valores de estado.
    return when (status) {
        SAnime.ONGOING.toLong() -> stringResource(MR.strings.ongoing)
        SAnime.COMPLETED.toLong() -> stringResource(MR.strings.completed)
        SAnime.LICENSED.toLong() -> stringResource(MR.strings.licensed)
        SAnime.PUBLISHING_FINISHED.toLong() -> stringResource(MR.strings.publishing_finished)
        SAnime.CANCELLED.toLong() -> stringResource(MR.strings.cancelled)
        SAnime.ON_HIATUS.toLong() -> stringResource(MR.strings.on_hiatus)
        else -> stringResource(MR.strings.unknown)
    }
}
