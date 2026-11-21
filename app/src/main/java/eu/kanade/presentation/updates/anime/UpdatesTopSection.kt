package eu.kanade.presentation.updates.anime

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.data.track.tmdb.TmdbUpcomingItem
import eu.kanade.tachiyomi.data.upcoming.AiringAnimeItem
import eu.kanade.tachiyomi.ui.updates.DayEntry
import eu.kanade.tachiyomi.ui.updates.UpdatesTopSectionScreenModel
import eu.kanade.tachiyomi.ui.updates.displayDate
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.launch
import mihon.feature.upcoming.components.calendar.Calendar
import mihon.feature.upcoming.components.calendar.CalendarDay
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

// v7.9.3: un solo tamaño intermedio para todas las portadas de
// Actualizaciones (carruseles 104dp, filas simples 60dp y tarjetas de
// grupo 48dp eran tres tamaños distintos): 72x108dp en los tres sitios.
private val PosterWidth = 72.dp
private val PosterHeight = 108.dp
private val CustomDateAmber = Color(0xFFFFA726)

/**
 * v7.9.2 — parte superior de Actualizaciones (pestaña Anime): calendario
 * primero y los 3 carruseles (animes, películas, series). La pestaña de
 * manga no lleva nada de esto, a petición del usuario.
 */
@Composable
fun UpdatesTopSection(
    model: UpdatesTopSectionScreenModel,
    onAnimeClick: (AiringAnimeItem) -> Unit,
    onViewAllAnime: () -> Unit,
    onViewAllMovies: () -> Unit,
    onViewAllSeries: () -> Unit,
    onOpenTmdbSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dayDialogDate by remember { mutableStateOf<LocalDate?>(null) }
    var datePickerTarget by remember { mutableStateOf<DatePickerTarget?>(null) }
    var tmdbDetail by remember { mutableStateOf<TmdbUpcomingItem?>(null) }
    // v7.9.3: el calendario nace plegado (solo la semana actual); la
    // flecha lo expande al mes completo y lo vuelve a plegar.
    var calendarExpanded by rememberSaveable { mutableStateOf(false) }
    val weekDays = currentWeekDays()
    // "+" de anime ya pulsados en esta sesión (la lista de AniList no se
    // vuelve a consultar; el toast confirma cada añadido).
    var addedAnimeIds by remember { mutableStateOf(setOf<Long>()) }
    val addedToPlanningMsg = stringResource(AYMR.strings.aurora_added_to_planning)
    val anilistNotLinkedMsg = stringResource(AYMR.strings.aurora_anilist_not_linked)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        // 1. Calendario primero, como tarjeta ancha. La expansión se anima
        // (animateContentSize) para que el cambio semana <-> mes sea fluido.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                CalendarHeaderRow(
                    expanded = calendarExpanded,
                    weekDays = weekDays,
                    selectedYearMonth = state.selectedYearMonth,
                    onPreviousMonth = { model.setSelectedYearMonth(state.selectedYearMonth.minusMonths(1)) },
                    onNextMonth = { model.setSelectedYearMonth(state.selectedYearMonth.plusMonths(1)) },
                    onToggleExpanded = { calendarExpanded = !calendarExpanded },
                )
                if (calendarExpanded) {
                    Calendar(
                        selectedYearMonth = state.selectedYearMonth,
                        events = state.calendarEvents,
                        customEvents = state.customCalendarEvents,
                        setSelectedYearMonth = model::setSelectedYearMonth,
                        showHeader = false,
                        onClickDay = { date ->
                            if (!state.dayEntries[date].isNullOrEmpty()) {
                                dayDialogDate = date
                            }
                        },
                    )
                } else {
                    WeekStrip(
                        weekDays = weekDays,
                        events = state.calendarEvents,
                        customEvents = state.customCalendarEvents,
                        onClickDay = { date ->
                            if (!state.dayEntries[date].isNullOrEmpty()) {
                                dayDialogDate = date
                            }
                        },
                    )
                }
                // Leyenda: emisión vs fecha puesta a mano.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LegendDot(
                        color = MaterialTheme.colorScheme.primary,
                        text = stringResource(AYMR.strings.aurora_airing),
                    )
                    LegendDot(
                        color = CustomDateAmber,
                        text = stringResource(AYMR.strings.aurora_custom_date),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Próximos animes (AniList, sin clave: estrenos nuevos del mes,
        // de todo AniList, sin filtrar por biblioteca).
        UpcomingCarouselHeader(
            title = stringResource(AYMR.strings.aurora_upcoming_anime),
            onViewAll = onViewAllAnime,
        )
        if (state.animeLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PosterHeight)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent)
            }
        } else if (state.animeItems.isEmpty()) {
            Text(
                text = stringResource(AYMR.strings.aurora_no_upcoming_anime),
                color = colors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = state.animeItems,
                    key = { it.anilistId },
                ) { item ->
                    val customDate = state.customDates["al:${item.anilistId}"]
                    val date = item.displayDate(state.customDates)
                    // v7.9.4 (fluidez): las tarjetas animan su posición al
                    // cargar o cambiar la lista, sin fundidos que den tirones.
                    Box(modifier = Modifier.animateItemFastScroll(this)) {
                        UpcomingPosterCard(
                            posterUrl = item.coverUrl,
                            title = item.title,
                            dateText = formatShortDate(date),
                            dateIsCustom = customDate != null,
                            extraLine = stringResource(AYMR.strings.aurora_episode_short, item.episode),
                            isAdded = item.anilistId in addedAnimeIds,
                            onClick = { onAnimeClick(item) },
                            onLongClick = {
                                datePickerTarget = DatePickerTarget(
                                    key = "al:${item.anilistId}",
                                    title = item.title,
                                    initialDate = date,
                                )
                            },
                            onAddClick = {
                                scope.launch {
                                    val added = model.addToAnilistPlanning(item)
                                    if (added) {
                                        addedAnimeIds = addedAnimeIds + item.anilistId
                                        context.toast(addedToPlanningMsg)
                                    } else {
                                        context.toast(anilistNotLinkedMsg)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3 y 4. Películas y series (TMDB, solo con clave).
        if (!state.tmdbKeySet) {
            TmdbKeyNotice(onOpenSettings = onOpenTmdbSettings)
        } else {
            UpcomingCarouselHeader(
                title = stringResource(AYMR.strings.aurora_upcoming_movies),
                onViewAll = onViewAllMovies,
            )
            TmdbCarousel(
                items = state.movies,
                loading = state.tmdbLoading,
                watchlist = state.watchlist,
                onItemClick = { tmdbDetail = it },
                onToggleWatchlist = { model.toggleWatchlist(it) },
            )

            Spacer(modifier = Modifier.height(20.dp))

            UpcomingCarouselHeader(
                title = stringResource(AYMR.strings.aurora_upcoming_series),
                onViewAll = onViewAllSeries,
            )
            TmdbCarousel(
                items = state.series,
                loading = state.tmdbLoading,
                watchlist = state.watchlist,
                onItemClick = { tmdbDetail = it },
                onToggleWatchlist = { model.toggleWatchlist(it) },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    // Diálogo: animes de un día del calendario (cambiar/quitar fecha).
    val dialogDate = dayDialogDate
    if (dialogDate != null) {
        val entries = state.dayEntries[dialogDate].orEmpty()
        DayEntriesDialog(
            date = dialogDate,
            entries = entries,
            onChangeDate = { entry ->
                dayDialogDate = null
                datePickerTarget = DatePickerTarget(
                    key = entry.key,
                    title = entry.title,
                    initialDate = entry.customDate ?: dialogDate,
                )
            },
            onClearDate = { entry -> model.clearCustomDate(entry.key) },
            onDismiss = { dayDialogDate = null },
        )
    }

    // Selector de fecha manual (poner o quitar la fecha de un anime).
    val pickerTarget = datePickerTarget
    if (pickerTarget != null) {
        val hasCustom = state.customDates.containsKey(pickerTarget.key)
        CustomDatePickerDialog(
            title = pickerTarget.title,
            initialDate = pickerTarget.initialDate,
            showRemove = hasCustom,
            onRemove = {
                model.clearCustomDate(pickerTarget.key)
                datePickerTarget = null
            },
            onConfirm = { date ->
                model.setCustomDate(pickerTarget.key, pickerTarget.title, date)
                datePickerTarget = null
            },
            onDismiss = { datePickerTarget = null },
        )
    }

    // Detalle simple de película/serie (TMDB).
    val detail = tmdbDetail
    if (detail != null) {
        val inWatchlist = state.watchlist[detail.watchlistKey]
        TmdbDetailDialog(
            item = detail,
            inWatchlist = inWatchlist != null,
            watched = inWatchlist?.watched == true,
            onToggleWatchlist = { model.toggleWatchlist(detail) },
            onToggleWatched = { watched ->
                state.watchlist[detail.watchlistKey]?.let {
                    model.setWatchlistWatched(it.key, watched)
                }
            },
            onDismiss = { tmdbDetail = null },
        )
    }
}

private data class DatePickerTarget(
    val key: String,
    val title: String,
    val initialDate: LocalDate,
)

@Composable
private fun LegendDot(color: Color, text: String) {
    val colors = AuroraTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, color = colors.textSecondary, fontSize = 11.sp)
    }
}

/** Los 7 días de la semana actual (empieza según el primer día local). */
private fun currentWeekDays(): List<LocalDate> {
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    return (0..6).map { weekStart.plusDays(it.toLong()) }
}

private fun formatWeekLabel(start: LocalDate, end: LocalDate): String {
    val short = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    return if (start.month == end.month) {
        "Semana del ${start.dayOfMonth} al ${short.format(end)}"
    } else {
        "Semana del ${short.format(start)} al ${short.format(end)}"
    }
}

/**
 * v7.9.3 — cabecera del calendario: plegado muestra la semana actual,
 * desplegado el mes con sus flechas para cambiar de mes. La flecha de la
 * derecha pliega y despliega.
 */
@Composable
private fun CalendarHeaderRow(
    expanded: Boolean,
    weekDays: List<LocalDate>,
    selectedYearMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val title = if (expanded) {
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
                .format(selectedYearMonth)
                .replaceFirstChar { it.uppercaseChar() }
        } else {
            formatWeekLabel(weekDays.first(), weekDays.last())
        }
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (expanded) {
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = colors.textPrimary,
                )
            }
            IconButton(onClick = onNextMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textPrimary,
                )
            }
        }
        IconButton(onClick = onToggleExpanded) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.accent,
            )
        }
    }
}

/** Tira de una semana: 7 días con sus puntos de emisión / fecha manual. */
@Composable
private fun WeekStrip(
    weekDays: List<LocalDate>,
    events: ImmutableMap<LocalDate, Int>,
    customEvents: ImmutableMap<LocalDate, Int>,
    onClickDay: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                Text(
                    text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                CalendarDay(
                    date = day,
                    events = events[day] ?: 0,
                    customEvents = customEvents[day] ?: 0,
                    onDayClick = { onClickDay(day) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun UpcomingCarouselHeader(
    title: String,
    onViewAll: () -> Unit,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(onClick = onViewAll) {
            Text(
                text = stringResource(AYMR.strings.aurora_view_all),
                color = colors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TmdbKeyNotice(onOpenSettings: () -> Unit) {
    val colors = AuroraTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(AYMR.strings.aurora_tmdb_key_needed),
                color = colors.textPrimary,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(text = stringResource(AYMR.strings.aurora_tmdb_api_key))
            }
        }
    }
}

@Composable
private fun TmdbCarousel(
    items: List<TmdbUpcomingItem>,
    loading: Boolean,
    watchlist: Map<String, eu.kanade.tachiyomi.ui.updates.WatchlistEntry>,
    onItemClick: (TmdbUpcomingItem) -> Unit,
    onToggleWatchlist: (TmdbUpcomingItem) -> Unit,
) {
    val colors = AuroraTheme.colors
    if (loading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PosterHeight)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = colors.accent)
        }
        return
    }
    if (items.isEmpty()) {
        Text(
            text = stringResource(AYMR.strings.aurora_no_upcoming_tmdb),
            color = colors.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        return
    }
    LazyRow(
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items = items, key = { it.watchlistKey }) { item ->
            val date = item.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            // v7.9.4 (fluidez): igual que el carrusel de animes.
            Box(modifier = Modifier.animateItemFastScroll(this)) {
                UpcomingPosterCard(
                    posterUrl = item.posterUrl,
                    title = item.title,
                    dateText = date?.let { formatShortDate(it) },
                    dateIsCustom = false,
                    extraLine = null,
                    isAdded = watchlist.containsKey(item.watchlistKey),
                    onClick = { onItemClick(item) },
                    onLongClick = null,
                    onAddClick = { onToggleWatchlist(item) },
                )
            }
        }
    }
}

@Composable
private fun UpcomingPosterCard(
    posterUrl: String?,
    title: String,
    dateText: String?,
    dateIsCustom: Boolean,
    extraLine: String?,
    isAdded: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onAddClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    Column(
        modifier = Modifier
            .width(PosterWidth)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                error = placeholderPainter,
                fallback = placeholderPainter,
                modifier = Modifier
                    .size(PosterWidth, PosterHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.cardBackground),
            )
            // v7.9.3: botón "+" pequeño (32dp). El IconButton de Material3
            // impone un mínimo táctil de 48dp y tapaba un cuarto del póster.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.accent, CircleShape)
                    .clickable { onAddClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isAdded) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = null,
                    tint = colors.textOnAccent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
        )
        if (dateText != null) {
            Text(
                text = dateText,
                color = if (dateIsCustom) CustomDateAmber else colors.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (extraLine != null) {
            Text(
                text = extraLine,
                color = colors.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DayEntriesDialog(
    date: LocalDate,
    entries: List<DayEntry>,
    onChangeDate: (DayEntry) -> Unit,
    onClearDate: (DayEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AuroraTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = formatLongDate(date), color = colors.textPrimary)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (entry.isCustom) {
                                Text(
                                    text = stringResource(AYMR.strings.aurora_custom_date),
                                    color = CustomDateAmber,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        IconButton(onClick = { onChangeDate(entry) }) {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = stringResource(AYMR.strings.aurora_set_custom_date),
                                tint = colors.accent,
                            )
                        }
                        if (entry.isCustom) {
                            IconButton(onClick = { onClearDate(entry) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(AYMR.strings.aurora_clear_custom_date),
                                    tint = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun CustomDatePickerDialog(
    title: String,
    initialDate: LocalDate,
    showRemove: Boolean,
    onRemove: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = remember(initialDate) {
        initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis ?: return@TextButton
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            Row {
                if (showRemove) {
                    TextButton(onClick = onRemove) {
                        Text(text = stringResource(AYMR.strings.aurora_clear_custom_date))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            }
        },
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            DatePicker(
                state = pickerState,
                title = null,
                headline = null,
                showModeToggle = false,
            )
        }
    }
}

@Composable
fun TmdbDetailDialog(
    item: TmdbUpcomingItem,
    inWatchlist: Boolean,
    watched: Boolean,
    onToggleWatchlist: () -> Unit,
    onToggleWatched: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val date = item.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = item.title, color = colors.textPrimary)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Row {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        error = placeholderPainter,
                        fallback = placeholderPainter,
                        modifier = Modifier
                            .size(96.dp, 144.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.cardBackground),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        if (date != null) {
                            Text(
                                text = formatLongDate(date),
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Button(
                            onClick = onToggleWatchlist,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = if (inWatchlist) {
                                    stringResource(AYMR.strings.aurora_remove_from_watchlist)
                                } else {
                                    stringResource(AYMR.strings.aurora_add_to_watchlist)
                                },
                            )
                        }
                        if (inWatchlist) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { onToggleWatched(!watched) }) {
                                Text(
                                    text = if (watched) {
                                        stringResource(AYMR.strings.aurora_mark_unwatched)
                                    } else {
                                        stringResource(AYMR.strings.aurora_mark_watched)
                                    },
                                    color = colors.accent,
                                )
                            }
                        }
                    }
                }
                if (item.overview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = item.overview,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
                if (inWatchlist && watched) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(AYMR.strings.aurora_watched),
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    )
}

private fun formatShortDate(date: LocalDate): String {
    val pattern = if (date.year == LocalDate.now().year) "d MMM" else "d MMM yyyy"
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(date)
}

private fun formatLongDate(date: LocalDate): String {
    return DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()).format(date)
}
