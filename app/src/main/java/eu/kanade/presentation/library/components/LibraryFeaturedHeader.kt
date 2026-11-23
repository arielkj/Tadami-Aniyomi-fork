package eu.kanade.presentation.library.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import tachiyomi.domain.library.model.LibraryDisplayMode
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
 *
 * v7.9.13 — "Seguir viendo" del MISMO TAMAÑO que las tarjetas de la
 * cuadrícula de cada categoría ("para no tener varios tamaños"): el
 * carrusel ya no usa la tarjeta grande de lado (320dp), sino
 * TitleCardContinueGrid, con la anchura de una celda de la rejilla
 * (la calcula el encabezado con las columnas: mismo ancho y misma
 * proporción 2:3 que las de abajo). El destacado sigue siendo
 * TitleCardSide (grande).
 *
 * v7.9.14 — el usuario rechazó el cambio de formato de la v7.9.13:
 * "Seguir viendo" vuelve al formato DE LADO (TitleCardSide, letras al
 * lado de la portada, todo el contenido de antes), solo MÁS PEQUEÑO
 * (`compact = true`). TitleCardContinueGrid se eliminó. Además las
 * esquinas de las tres tarjetas (destacado, Seguir viendo, biblioteca)
 * quedan iguales: 4.dp, el radio de las tarjetas de la cuadrícula.
 *
 * v7.9.18 — la v7.9.16 calculaba la CELDA y la usaba como portada,
 * pero la portada de la cuadrícula es 8dp más estrecha que su celda:
 * GridItemSelectable mete 4dp de padding a cada lado dentro de cada
 * ítem compacto (y la tarjeta GlowContour tiene el mismo 4dp). La
 * portada del carrusel es ahora la celda menos esos 8dp (~120dp en el
 * teléfono del usuario, medida 296px frente a 276px en sus capturas).
 * El diseño de lado no se toca.
 *
 * v7.9.16 — "Seguir viendo" con la portada del MISMO TAMAÑO que las
 * de la cuadrícula de la biblioteca (el usuario: "quiero que esta
 * portada tenga el mismo tamaño que las otras", con las letras al lado).
 * La anchura disponible aquí (maxWidth del BoxWithConstraints) ES la
 * misma que la de la rejilla: el encabezado y la rejilla viven en la
 * misma columna y el padding del encabezado (contentPadding + 8dp por
 * lado, ver AuroraCollapsingLibraryHeader) es el mismo que el de la
 * rejilla (contentPadding + 8dp extra de LazyLibraryGrid), así que la
 * fórmula de la celda es la de la rejilla: columnas fijas con 4dp
 * entre celdas (GridHorizontalSpacer), o la celda adaptable si no hay
 * columnas fijas. La tarjeta es portada + columna de texto (156dp,
 * como en la v7.9.14); quepan las que quepan. La v7.9.14 calculaba la
 * celda igual, pero el usuario la percibió distinta ("No como la de
 * antes"): por eso la fórmula se escribe aquí desde la rejilla, con
 * las columnas del mismo ajuste que usa la rejilla, no copiada.
 *
 * v7.9.15 — (1) "Seguir viendo" MÁS PEQUEÑO, QUE QUEPAN DOS EN LA
 * FILA, NO TRES: la tarjeta pasa a ~la mitad del ancho del carrusel
 * (dos a la vez, una al lado de la otra), con el MISMO diseño de lado
 * y las MISMAS letras (15/12/11sp); la portada y la columna de texto
 * se ajustan proporcionalmente para caber (portada ~38% del interior
 * de la tarjeta, para que quepa la insignia "Nuevo capítulo" con su
 * padding). El usuario lo rechazó: la portada quedó muy pequeña.
 * (2) El destacado se desliza SOLO cada ~4 s, en bucle, como un
 * catálogo; pausa mientras el usuario toca/arrastra (y reanuda 4 s
 * después del último cambio de página) y no avanza si el encabezado
 * está colapsado/oculto. El diseño no se toca. (3) Filtro de
 * privacidad: las fuentes desmarcadas en Ajustes → "Seguir viendo y
 * sugerencias" no aparecen ni en "Seguir viendo" ni en el destacado
 * (`hiddenSourceKeys`, claves "a:<id>" / "m:<id>"). (4) La semilla del
 * barajado ya no es aleatoria a cada entrada: la guarda UiPreferences
 * y se renueva cada 6 h (ver AnimeLibraryTab), así las sugerencias
 * cambian solas cada 6 horas.
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
    // v7.9.16: columnas de la rejilla de la biblioteca (el mismo ajuste
    // que usa la rejilla) y celda mínima adaptable: el carrusel de
    // "Seguir viendo" calcula la portada con la fórmula de la rejilla.
    columns: Int,
    adaptiveMinCellDp: Int,
    // v7.9.15: fuentes ocultas por el usuario en Ajustes ("a:<id>");
    // no aparecen ni en "Seguir viendo" ni en el destacado.
    hiddenSourceKeys: Set<String> = emptySet(),
    // v7.9.15: el avance automático del destacado no corre si el
    // encabezado está colapsado/oculto.
    headerVisible: () -> Boolean = { true },
    // v7.9.37: tamaño exacto de la portada de la cuadrícula para
    // "Seguir viendo" en el diseño ancho (tablet/TV).
    // v7.9.38: el modo de vista de la biblioteca (CompactGrid /
    // ComfortableGrid / CoverOnlyGrid) — cada uno dibuja la portada de
    // la cuadrícula con una geometría distinta y "Seguir viendo" tiene
    // que copiar la del modo activo, no una fórmula única.
    isWideLayout: Boolean = false,
    useGlowContourCards: Boolean = true,
    displayMode: LibraryDisplayMode = LibraryDisplayMode.ComfortableGrid,
) {
    // v7.9.15: primero se quitan las fuentes ocultas — TODO lo de
    // abajo (en curso, destacado y sus respaldos) sale de los visibles,
    // para que nada privado se cuele por el respaldo de "toda la
    // categoría" cuando la tanda elegida sale vacía.
    val visibleItems = remember(items, hiddenSourceKeys) {
        if (hiddenSourceKeys.isEmpty()) {
            items
        } else {
            items.filterNot { it.isFromHiddenSource(hiddenSourceKeys) }
        }
    }
    // En curso: empezado y con algo sin ver, lo último visto primero.
    val inProgress = remember(visibleItems) {
        visibleItems.filter { it.seenCount > 0 && it.unseenCount > 0 }
            .sortedByDescending { it.lastSeen }
    }
    // La tanda del banner depende del ajuste (En curso / Toda la
    // biblioteca / Favoritos); si la tanda elegida sale vacía se cae a
    // los en curso y luego a toda la categoría, para no dejar el
    // destacado vacío teniendo títulos.
    val featuredPool = remember(visibleItems, featuredSource, inProgress) {
        val selected = when (featuredSource) {
            AURORA_LIBRARY_FEATURED_SOURCE_ALL -> visibleItems
            AURORA_LIBRARY_FEATURED_SOURCE_PINNED -> visibleItems.filter { it.pinned }
            else -> inProgress
        }
        selected.ifEmpty { inProgress }.ifEmpty { visibleItems }
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
        columns = columns,
        adaptiveMinCellDp = adaptiveMinCellDp,
        modifier = modifier,
        headerVisible = headerVisible,
        isWideLayout = isWideLayout,
        useGlowContourCards = useGlowContourCards,
        displayMode = displayMode,
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
    // v7.9.16: columnas de la rejilla de la biblioteca (el mismo ajuste
    // que usa la rejilla) y celda mínima adaptable: el carrusel de
    // "Seguir viendo" calcula la portada con la fórmula de la rejilla.
    columns: Int,
    adaptiveMinCellDp: Int,
    // v7.9.15: fuentes ocultas por el usuario en Ajustes ("m:<id>");
    // no aparecen ni en "Seguir viendo" ni en el destacado.
    hiddenSourceKeys: Set<String> = emptySet(),
    // v7.9.15: el avance automático del destacado no corre si el
    // encabezado está colapsado/oculto.
    headerVisible: () -> Boolean = { true },
    // v7.9.37: tamaño exacto de la portada de la cuadrícula para
    // "Seguir viendo" en el diseño ancho (tablet/TV).
    // v7.9.38: el modo de vista de la biblioteca (ver el comentario en
    // el encabezado de anime).
    isWideLayout: Boolean = false,
    useGlowContourCards: Boolean = true,
    displayMode: LibraryDisplayMode = LibraryDisplayMode.ComfortableGrid,
) {
    // v7.9.15: primero se quitan las fuentes ocultas (ver el comentario
    // en el destacado de anime).
    val visibleItems = remember(items, hiddenSourceKeys) {
        if (hiddenSourceKeys.isEmpty()) {
            items
        } else {
            items.filterNot { it.isFromHiddenSource(hiddenSourceKeys) }
        }
    }
    val inProgress = remember(visibleItems) {
        visibleItems.filter { it.readCount > 0 && it.unreadCount > 0 }
            .sortedByDescending { it.lastRead }
    }
    val featuredPool = remember(visibleItems, featuredSource, inProgress) {
        val selected = when (featuredSource) {
            AURORA_LIBRARY_FEATURED_SOURCE_ALL -> visibleItems
            AURORA_LIBRARY_FEATURED_SOURCE_PINNED -> visibleItems.filter { it.pinned }
            else -> inProgress
        }
        selected.ifEmpty { inProgress }.ifEmpty { visibleItems }
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
        columns = columns,
        adaptiveMinCellDp = adaptiveMinCellDp,
        modifier = modifier,
        headerVisible = headerVisible,
        isWideLayout = isWideLayout,
        useGlowContourCards = useGlowContourCards,
        displayMode = displayMode,
    )
}

@Composable
private fun LibraryFeaturedHeaderContent(
    featuredEntries: List<LibraryFeaturedEntry>,
    continueEntries: List<LibraryFeaturedEntry>,
    columns: Int,
    adaptiveMinCellDp: Int,
    modifier: Modifier = Modifier,
    // v7.9.15: el avance automático del destacado no corre si el
    // encabezado está colapsado/oculto.
    headerVisible: () -> Boolean = { true },
    // v7.9.37: en el diseño ancho (tablet/TV) la portada de "Seguir
    // viendo" mide EXACTAMENTE lo mismo que la portada de la
    // cuadrícula de la biblioteca (ancho y alto). En el teléfono se
    // queda la regla de la v7.9.18 (ancho = celda − 8dp, 2:3).
    // v7.9.38: el modo de vista de la biblioteca (ver el comentario en
    // el encabezado de anime).
    isWideLayout: Boolean = false,
    useGlowContourCards: Boolean = true,
    displayMode: LibraryDisplayMode = LibraryDisplayMode.ComfortableGrid,
) {
    if (featuredEntries.isEmpty() && continueEntries.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        if (featuredEntries.isNotEmpty()) {
            LibraryFeaturedBanner(entries = featuredEntries, headerVisible = headerVisible)
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
            // v7.9.18: "Seguir viendo" con la portada del MISMO TAMAÑO
            // que las de la cuadrícula (medido en las capturas del
            // usuario: la portada del carrusel 296px y la de la
            // cuadrícula 276px, ~7% más grande). La v7.9.16 calculaba
            // la CELDA (129dp) y la usaba como portada, pero la portada
            // de la cuadrícula es 8dp más estrecha que su celda:
            // GridItemSelectable (CommonEntryItem.kt) mete 4dp de
            // padding a cada lado dentro de cada ítem compacto — y la
            // tarjeta GlowContour tiene el mismo 4dp en su superficie.
            // La anchura disponible sí es la misma que la de la
            // rejilla (mismo contentPadding + 8dp por lado), así que la
            // celda se calcula igual que antes y la portada es la celda
            // menos esos 8dp. El alto sigue la proporción 2:3. El
            // diseño de lado no se toca (misma columna de texto 156dp,
            // mismas letras 15/12/11sp, `compact = true`).
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val gridCellWidth = if (columns > 0) {
                    (maxWidth - 4.dp * (columns - 1)) / columns
                } else {
                    val count = ((maxWidth + 4.dp) / (adaptiveMinCellDp.dp + 4.dp))
                        .toInt().coerceAtLeast(1)
                    (maxWidth - 4.dp * (count - 1)) / count
                }
                // La portada de la cuadrícula no llena su celda: cada
                // ítem lleva 4dp de padding por lado (GridItemSelectable).
                // v7.9.37: en el diseño ancho (tablet/TV) la portada de
                // "Seguir viendo" mide EXACTAMENTE lo mismo que la de la
                // cuadrícula, no solo el mismo ancho.
                // v7.9.38: la v7.9.37 usó UNA fórmula para las tarjetas
                // Estándar (celda × celda/0.66×0.65) y en la rejilla
                // compacta del usuario salió al revés: más ancha y más
                // baja que la de la biblioteca. Cada modo dibuja la
                // portada de la cuadrícula con su propia geometría
                // (leída de los ítems de la rejilla, no adivinada):
                // - GlowContour (todos los modos): la tarjeta llena la
                //   celda con proporción 0.76 y su superficie lleva 4dp
                //   de padding por lado; la portada llena la superficie.
                // - Estándar Cómoda: AuroraCard de proporción 0.66 y la
                //   portada ocupa el 68% del alto de la tarjeta
                //   (coverHeightFraction 0.68), sin padding.
                // - Estándar solo portadas: AuroraCard de proporción 0.6
                //   y la portada llena toda la tarjeta (fracción 1.0).
                // - Estándar compacta: EntryCompactGridItem, con
                //   GridItemSelectable (4dp de padding por lado) y la
                //   portada en proporción 2:3 (ItemCover.Book).
                val (coverWidth, coverHeight) = if (isWideLayout) {
                    when {
                        useGlowContourCards -> {
                            val w = (gridCellWidth - 8.dp).coerceAtLeast(0.dp)
                            val h = (gridCellWidth / 0.76f - 8.dp).coerceAtLeast(0.dp)
                            w to h
                        }
                        displayMode == LibraryDisplayMode.CompactGrid -> {
                            val w = (gridCellWidth - 8.dp).coerceAtLeast(0.dp)
                            w to (w / (2f / 3f)).coerceAtLeast(0.dp)
                        }
                        displayMode == LibraryDisplayMode.CoverOnlyGrid -> {
                            gridCellWidth to (gridCellWidth / 0.6f).coerceAtLeast(0.dp)
                        }
                        else -> {
                            gridCellWidth to (gridCellWidth / 0.66f * 0.68f).coerceAtLeast(0.dp)
                        }
                    }
                } else {
                    val w = (gridCellWidth - 8.dp).coerceAtLeast(0.dp)
                    w to null
                }
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
                            modifier = Modifier.width(coverWidth + 156.dp),
                            coverWidth = coverWidth,
                            compact = true,
                            coverHeight = coverHeight,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryFeaturedBanner(
    entries: List<LibraryFeaturedEntry>,
    // v7.9.15: el avance automático no corre si el encabezado está
    // colapsado/oculto (la Títulos baja la cuadrícula y el encabezado
    // se va con ella).
    headerVisible: () -> Boolean = { true },
) {
    val colors = AuroraTheme.colors
    val pagerState = rememberPagerState { entries.size }
    // v7.9.15: las sugerencias se deslizan SOLAS cada ~4 s, en bucle,
    // como un catálogo que se va mostrando. El diseño no se toca.
    // - El temporizador se reinicia en cada cambio de página (manual o
    //   automático): se avanza solo si pasaron 4 s desde el último
    //   cambio, así tras un deslizamiento del usuario hay 4 s de pausa
    //   antes de que el catálogo siga solo.
    // - Mientras el usuario toca/arrastra (isScrollInProgress) no se
    //   avanza; al soltar, el siguiente tick lo retoma.
    // - Si el encabezado está colapsado/oculto no se avanza.
    // El bucle NO depende de currentPage como clave: si lo hiciera, el
    // cambio de página del propio avance cancelaría la animación a
    // mitad de camino (currentPage cambia al pasar la mitad del
    // deslizamiento). Por eso el bucle es while(true) con un tick de
    // 1 s y la guarda de "último cambio".
    var lastPageChangeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect {
            lastPageChangeMs = System.currentTimeMillis()
        }
    }
    LaunchedEffect(pagerState, entries.size) {
        if (entries.size <= 1) return@LaunchedEffect
        while (true) {
            delay(1000)
            if (entries.size <= 1) continue
            if (!headerVisible()) continue
            if (pagerState.isScrollInProgress) continue
            if (System.currentTimeMillis() - lastPageChangeMs < 4000) continue
            val next = (pagerState.currentPage + 1) % entries.size
            pagerState.animateScrollToPage(next)
        }
    }
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

// v7.9.15: la fuente de un título está oculta por el usuario en Ajustes
// ("Seguir viendo y sugerencias") cuando su clave ("a:<id>" en anime,
// "m:<id>" en manga) está en el conjunto guardado. La fuente es la del
// anime/manga que da la portada y el título (la misma que usa
// toFeaturedEntry), así el filtro y lo que se muestra coinciden.
private fun AnimeLibraryItem.isFromHiddenSource(hiddenSourceKeys: Set<String>): Boolean {
    if (hiddenSourceKeys.isEmpty()) return false
    val source = (coverAnime ?: libraryAnime.anime).source
    return "a:$source" in hiddenSourceKeys
}

private fun MangaLibraryItem.isFromHiddenSource(hiddenSourceKeys: Set<String>): Boolean {
    if (hiddenSourceKeys.isEmpty()) return false
    val source = (coverManga ?: libraryManga.manga).source
    return "m:$source" in hiddenSourceKeys
}

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
