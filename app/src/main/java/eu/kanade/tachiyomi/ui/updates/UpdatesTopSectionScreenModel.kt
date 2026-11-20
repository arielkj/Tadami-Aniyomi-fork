package eu.kanade.tachiyomi.ui.updates

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.tmdb.TmdbUpcomingItem
import eu.kanade.tachiyomi.data.upcoming.AiringAnimeItem
import eu.kanade.tachiyomi.data.upcoming.UpcomingReleasesRepository
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.upcoming.anime.interactor.GetUpcomingAnime
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Una fecha puesta a mano por el usuario para un anime.
 * key = "al:&lt;id AniList&gt;" (carrusel) o "lib:&lt;id anime local&gt;" (biblioteca).
 */
data class CustomDateEntry(
    val key: String,
    val title: String,
    val date: LocalDate,
)

/** Una película o serie guardada en la lista local "Por ver". */
data class WatchlistEntry(
    val key: String,
    val title: String,
    val posterUrl: String?,
    val date: LocalDate?,
    val mediaType: String,
    val watched: Boolean,
)

/** Un anime que sale un día concreto, para el diálogo al tocar un día del calendario. */
data class DayEntry(
    val key: String,
    val title: String,
    val isCustom: Boolean,
    val customDate: LocalDate?,
)

/**
 * v7.9.2 — estado de la parte superior de Actualizaciones (pestaña Anime):
 * calendario con las fechas de emisión de la biblioteca + fechas manuales,
 * y los 3 carruseles (animes de AniList, películas y series de TMDB).
 */
class UpdatesTopSectionScreenModel(
    private val getUpcomingAnime: GetUpcomingAnime = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val repository: UpcomingReleasesRepository = UpcomingReleasesRepository(),
) : ScreenModel {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val selectedYearMonthFlow = MutableStateFlow(YearMonth.now())

    init {
        // Calendario: fechas de la biblioteca + fechas manuales + lista Por ver.
        combine(
            getUpcomingAnime.subscribe(),
            uiPreferences.animeCustomDates().changes(),
            uiPreferences.tmdbWatchlist().changes(),
            uiPreferences.tmdbApiKey().changes(),
            selectedYearMonthFlow,
        ) { libraryAnime, customDatesJson, watchlistJson, tmdbKey, selectedYearMonth ->
            val customDates = parseCustomDates(customDatesJson)
            val watchlist = parseWatchlist(watchlistJson)
            buildCalendarState(
                libraryAnime = libraryAnime,
                customDates = customDates,
                watchlist = watchlist,
                tmdbKey = tmdbKey,
                selectedYearMonth = selectedYearMonth,
            )
        }
            .onEach { calendarState -> _state.value = calendarState }
            .launchIn(screenModelScope)

        // Estrenos de la red: AniList es público; TMDB solo con clave.
        // changes() emite el valor actual al suscribirse, así que este
        // collect ya carga TMDB al arrancar y al cambiar la clave.
        screenModelScope.launch { loadAiringAnime() }
        screenModelScope.launch {
            uiPreferences.tmdbApiKey().changes().collect { loadTmdb() }
        }
    }

    fun setSelectedYearMonth(yearMonth: YearMonth) {
        selectedYearMonthFlow.value = yearMonth
    }

    fun refresh() {
        screenModelScope.launch { loadAiringAnime() }
        screenModelScope.launch { loadTmdb() }
    }

    private suspend fun loadAiringAnime() {
        _state.update { it.copy(animeLoading = true) }
        val items = try {
            repository.fetchAiringAnime()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "UpdatesTopSection: AniList airing failed" }
            emptyList()
        }
        _state.update { it.copy(animeItems = items, animeLoading = false) }
    }

    private suspend fun loadTmdb() {
        val key = uiPreferences.tmdbApiKey().get().trim()
        _state.update { it.copy(tmdbKeySet = key.isNotEmpty(), tmdbLoading = key.isNotEmpty()) }
        if (key.isEmpty()) {
            _state.update { it.copy(movies = emptyList(), series = emptyList(), tmdbError = false) }
            return
        }
        val movies = try {
            repository.fetchUpcomingMovies(key)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "UpdatesTopSection: TMDB movies failed" }
            null
        }
        val series = try {
            repository.fetchUpcomingSeries(key)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "UpdatesTopSection: TMDB series failed" }
            null
        }
        _state.update {
            it.copy(
                movies = movies.orEmpty(),
                series = series.orEmpty(),
                tmdbLoading = false,
                tmdbError = movies == null || series == null,
            )
        }
    }

    /** Guarda o cambia la fecha manual de un anime (key "al:…" o "lib:…"). */
    fun setCustomDate(key: String, title: String, date: LocalDate) {
        val current = parseCustomDates(uiPreferences.animeCustomDates().get()).toMutableMap()
        current[key] = CustomDateEntry(key, title, date)
        uiPreferences.animeCustomDates().set(serializeCustomDates(current))
    }

    /** Quita la fecha manual de un anime (vuelve a mandar su fecha de AniList). */
    fun clearCustomDate(key: String) {
        val current = parseCustomDates(uiPreferences.animeCustomDates().get()).toMutableMap()
        if (current.remove(key) != null) {
            uiPreferences.animeCustomDates().set(serializeCustomDates(current))
        }
    }

    /**
     * "+" de un anime: lo añade a la lista "Por ver" (Planning) de AniList.
     * Devuelve true si se añadió, false si AniList no está conectado o falló.
     */
    suspend fun addToAnilistPlanning(item: AiringAnimeItem): Boolean {
        val anilist = trackerManager.aniList
        if (!anilist.isLoggedIn) return false
        return try {
            val track = AnimeTrack.create(TrackerManager.ANILIST).apply {
                remote_id = item.anilistId
                this.title = item.title
                status = Anilist.PLAN_TO_WATCH
                last_episode_seen = 0.0
                total_episodes = 0
                score = 0.0
                private = false
            }
            anilist.api.addLibAnime(track)
            true
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "UpdatesTopSection: AniList Planning add failed" }
            false
        }
    }

    /** "+" de una película/serie: entra o sale de la lista local "Por ver". */
    fun toggleWatchlist(item: TmdbUpcomingItem) {
        val current = parseWatchlist(uiPreferences.tmdbWatchlist().get()).toMutableMap()
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
        uiPreferences.tmdbWatchlist().set(serializeWatchlist(current))
    }

    fun setWatchlistWatched(key: String, watched: Boolean) {
        val current = parseWatchlist(uiPreferences.tmdbWatchlist().get()).toMutableMap()
        current[key]?.let { current[key] = it.copy(watched = watched) }
        uiPreferences.tmdbWatchlist().set(serializeWatchlist(current))
    }

    fun removeFromWatchlist(key: String) {
        val current = parseWatchlist(uiPreferences.tmdbWatchlist().get()).toMutableMap()
        if (current.remove(key) != null) {
            uiPreferences.tmdbWatchlist().set(serializeWatchlist(current))
        }
    }

    private fun buildCalendarState(
        libraryAnime: List<Anime>,
        customDates: Map<String, CustomDateEntry>,
        watchlist: Map<String, WatchlistEntry>,
        tmdbKey: String,
        selectedYearMonth: YearMonth,
    ): State {
        val events = mutableMapOf<LocalDate, Int>()
        val customEvents = mutableMapOf<LocalDate, Int>()
        val dayEntries = mutableMapOf<LocalDate, MutableList<DayEntry>>()

        // Biblioteca: la fecha de emisión de AniList, salvo que el usuario
        // le haya puesto una fecha manual (entonces manda la manual).
        for (anime in libraryAnime) {
            val key = "lib:${anime.id}"
            val custom = customDates[key]
            val date = custom?.date
                ?: anime.expectedNextUpdate?.toLocalDate()
                ?: continue
            if (custom != null) {
                customEvents[date] = (customEvents[date] ?: 0) + 1
            } else {
                events[date] = (events[date] ?: 0) + 1
            }
            dayEntries.getOrPut(date) { mutableListOf() }.add(
                DayEntry(key = key, title = anime.title, isCustom = custom != null, customDate = custom?.date),
            )
        }
        // Fechas manuales de animes del carrusel (no están en la biblioteca).
        for ((key, entry) in customDates) {
            if (key.startsWith("al:")) {
                customEvents[entry.date] = (customEvents[entry.date] ?: 0) + 1
                dayEntries.getOrPut(entry.date) { mutableListOf() }.add(
                    DayEntry(key = key, title = entry.title, isCustom = true, customDate = entry.date),
                )
            }
        }

        return _state.value.copy(
            selectedYearMonth = selectedYearMonth,
            calendarEvents = events.toImmutableMap(),
            customCalendarEvents = customEvents.toImmutableMap(),
            dayEntries = dayEntries,
            watchlist = watchlist,
            customDates = customDates,
            tmdbKeySet = tmdbKey.trim().isNotEmpty(),
        )
    }

    data class State(
        val selectedYearMonth: YearMonth = YearMonth.now(),
        val calendarEvents: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val customCalendarEvents: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val dayEntries: Map<LocalDate, List<DayEntry>> = emptyMap(),
        val animeItems: List<AiringAnimeItem> = emptyList(),
        val animeLoading: Boolean = true,
        val movies: List<TmdbUpcomingItem> = emptyList(),
        val series: List<TmdbUpcomingItem> = emptyList(),
        val tmdbLoading: Boolean = false,
        val tmdbKeySet: Boolean = false,
        val tmdbError: Boolean = false,
        val watchlist: Map<String, WatchlistEntry> = emptyMap(),
        val customDates: Map<String, CustomDateEntry> = emptyMap(),
    )

    companion object {
        fun parseCustomDates(json: String): Map<String, CustomDateEntry> {
            if (json.isBlank()) return emptyMap()
            return try {
                val obj = JSONObject(json)
                buildMap {
                    obj.keys().forEach { key ->
                        val entry = obj.optJSONObject(key) ?: return@forEach
                        val dateStr = entry.optString("date").takeIf { it.isNotBlank() } ?: return@forEach
                        put(
                            key,
                            CustomDateEntry(
                                key = key,
                                title = entry.optString("title"),
                                date = LocalDate.parse(dateStr),
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "UpdatesTopSection: bad custom dates json" }
                emptyMap()
            }
        }

        fun serializeCustomDates(map: Map<String, CustomDateEntry>): String {
            val obj = JSONObject()
            map.values.forEach { entry ->
                obj.put(
                    entry.key,
                    JSONObject().apply {
                        put("title", entry.title)
                        put("date", entry.date.toString())
                    },
                )
            }
            return obj.toString()
        }

        fun parseWatchlist(json: String): Map<String, WatchlistEntry> {
            if (json.isBlank()) return emptyMap()
            return try {
                val obj = JSONObject(json)
                buildMap {
                    obj.keys().forEach { key ->
                        val entry = obj.optJSONObject(key) ?: return@forEach
                        put(
                            key,
                            WatchlistEntry(
                                key = key,
                                title = entry.optString("title"),
                                posterUrl = entry.optString("poster").takeIf { it.isNotBlank() },
                                date = entry.optString("date").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
                                mediaType = entry.optString("mediaType").takeIf { it.isNotBlank() } ?: "movie",
                                watched = entry.optBoolean("watched", false),
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "UpdatesTopSection: bad watchlist json" }
                emptyMap()
            }
        }

        fun serializeWatchlist(map: Map<String, WatchlistEntry>): String {
            val obj = JSONObject()
            map.values.forEach { entry ->
                obj.put(
                    entry.key,
                    JSONObject().apply {
                        put("title", entry.title)
                        put("poster", entry.posterUrl ?: "")
                        put("date", entry.date?.toString() ?: "")
                        put("mediaType", entry.mediaType)
                        put("watched", entry.watched)
                    },
                )
            }
            return obj.toString()
        }
    }
}

/** Fecha que se muestra para un anime del carrusel: la manual si existe, si no la de AniList. */
fun AiringAnimeItem.displayDate(customDates: Map<String, CustomDateEntry>): LocalDate {
    val custom = customDates["al:$anilistId"]
    if (custom != null) return custom.date
    return Instant.ofEpochSecond(airingAtEpochSeconds)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}
