package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tadami.aurora.R
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberCoverReloadTick
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
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
 * El banner muestra UN título en curso como recordatorio: la portada de
 * fondo, el título, la línea de datos (tipo • género • episodios • estado),
 * el botón blanco "Ver detalles" y los puntos de las páginas. La tanda del
 * banner se baraja con una semilla que cambia cada vez que se vuelve a
 * entrar a Títulos, así el destacado rota solo.
 *
 * El carrusel "Seguir viendo" ordena los títulos en curso por lo último
 * visto/leído: tarjeta ancha con portada, etiqueta T1 E13 (anime) o
 * Capítulo N (manga), barra fina de progreso e insignia azul "Nuevo
 * episodio"/"Nuevo capítulo" cuando hay algo sin ver/leer. Tocar la
 * tarjeta va DIRECTO a ese episodio/capítulo (no a la pantalla de detalle).
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
    val infoLine: String,
    val coverData: Any?,
    val backdropData: Any?,
    val progressLabel: String,
    val seasonEpisodeLabel: String?,
    val progress: Float,
    val hasNew: Boolean,
    val newBadgeText: String,
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
                    LibraryContinueCard(entry = entry)
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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(272.dp)
                .clip(RoundedCornerShape(20.dp)),
        ) { page ->
            LibraryFeaturedBannerPage(entry = entries[page])
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

@Composable
private fun LibraryFeaturedBannerPage(entry: LibraryFeaturedEntry) {
    val context = LocalContext.current
    val reloadTick = rememberCoverReloadTick()
    val request = remember(context, entry.backdropData, reloadTick) {
        buildAuroraCoverImageRequest(context, entry.backdropData)
    }
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(
        AuroraCoverPlaceholderVariant.Wide,
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { entry.onDetailsClick() },
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = rememberAuroraPosterColorFilter(),
            modifier = Modifier.fillMaxSize(),
            error = fallbackPainter,
            fallback = fallbackPainter,
        )
        // Degradado para que el título se lea sobre cualquier portada.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.45f to Color.Transparent,
                            0.75f to Color.Black.copy(alpha = 0.55f),
                            1.00f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                text = entry.title,
                color = Color.White,
                fontSize = 24.sp,
                fontFamily = FontFamily(Font(R.font.montserrat_bold)),
                lineHeight = 29.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.infoLine,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = entry.onDetailsClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = stringResource(AYMR.strings.aurora_library_featured_view_details),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun LibraryContinueCard(entry: LibraryFeaturedEntry) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val reloadTick = rememberCoverReloadTick()
    val request = remember(context, entry.coverData, reloadTick) {
        buildAuroraCoverImageRequest(context, entry.coverData)
    }
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(
        AuroraCoverPlaceholderVariant.Portrait,
    )
    Card(
        onClick = entry.onContinueClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        modifier = Modifier
            .width(264.dp)
            .height(196.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = rememberAuroraPosterColorFilter(),
                    modifier = Modifier.fillMaxSize(),
                    error = fallbackPainter,
                    fallback = fallbackPainter,
                )
                if (entry.hasNew) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E88E5))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = entry.newBadgeText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (entry.seasonEpisodeLabel != null) {
                    Text(
                        text = entry.seasonEpisodeLabel,
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                Text(
                    text = entry.title,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.progressLabel,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.weight(1f))
                LinearProgressIndicator(
                    progress = { entry.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = colors.accent,
                    trackColor = colors.textSecondary.copy(alpha = 0.25f),
                )
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
    // El siguiente episodio es el primero sin ver; el número que se muestra
    // (vistos + 1) coincide en el caso normal de ver en orden, y al tocar
    // se abre el episodio real sin ver (getNextUnseenEpisode), así que el
    // destino siempre es correcto aunque se haya saltado alguno.
    val nextNumber = seenCount + 1
    val season = anime.seasonNumber.takeIf { it > 0.0 }?.toInt() ?: 1
    val genre = anime.displayGenre?.firstOrNull()
    val infoParts = buildList {
        add(stringResource(AYMR.strings.label_anime))
        if (!genre.isNullOrBlank()) add(genre)
        if (totalEpisodes > 0) add("$totalEpisodes ep.")
        add(auroraLibraryEntryStatusText(anime.status))
    }
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
        infoLine = infoParts.joinToString(" • "),
        coverData = coverData,
        // El fondo del banner usa la imagen de fondo cuando la fuente la
        // trae; si no, la portada recortada.
        backdropData = anime.backgroundUrl?.takeIf { it.isNotBlank() } ?: coverData,
        progressLabel = stringResource(AYMR.strings.display_mode_episode, nextNumber.toString()),
        seasonEpisodeLabel = "T$season E$nextNumber",
        progress = if (totalEpisodes > 0) {
            (seenCount.toFloat() / totalEpisodes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        },
        hasNew = unseenCount > 0,
        newBadgeText = stringResource(AYMR.strings.aurora_library_featured_new_episode),
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
    val nextNumber = readCount + 1
    val genre = manga.displayGenre?.firstOrNull()
    val infoParts = buildList {
        add(stringResource(AYMR.strings.label_manga))
        if (!genre.isNullOrBlank()) add(genre)
        if (totalChapters > 0) add("$totalChapters cap.")
        add(auroraLibraryEntryStatusText(manga.status))
    }
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
        infoLine = infoParts.joinToString(" • "),
        coverData = coverData,
        backdropData = coverData,
        progressLabel = stringResource(MR.strings.display_mode_chapter, nextNumber.toString()),
        seasonEpisodeLabel = null,
        progress = if (totalChapters > 0) {
            (readCount.toFloat() / totalChapters.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        },
        hasNew = unreadCount > 0,
        newBadgeText = stringResource(AYMR.strings.aurora_library_featured_new_chapter),
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
