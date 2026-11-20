package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.BottomNavAppearance
import eu.kanade.domain.ui.model.EInkProfile
import eu.kanade.domain.ui.model.EInkThemeMode
import eu.kanade.domain.ui.model.EpisodeListDensity
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.domain.ui.model.NavTransitionMode
import eu.kanade.domain.ui.model.RefreshRateMode
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.TitleScreenStyle
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.metadata.model.MetadataSource
import tachiyomi.presentation.core.util.HapticFeedbackMode
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class UiPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun themeMode() = preferenceStore.getEnum("pref_theme_mode_key", ThemeMode.SYSTEM)

    fun appTheme() = preferenceStore.getEnum(
        "pref_app_theme",
        AppTheme.AURORA,
    )

    fun themeDarkAmoled() = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    fun meltdownStage() = preferenceStore.getInt("pref_meltdown_stage", 0)

    fun relativeTime() = preferenceStore.getBoolean("relative_time_v2", true)

    fun dateFormat() = preferenceStore.getString("app_date_format", "")

    fun tabletUiMode() = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    fun startScreen() = preferenceStore.getEnum("start_screen", StartScreen.ANIME)

    fun showAnimeSection() = preferenceStore.getBoolean("aurora_show_anime_section", true)
    fun showMangaSection() = preferenceStore.getBoolean("aurora_show_manga_section", true)
    fun showNovelSection() = preferenceStore.getBoolean("aurora_show_novel_section", true)

    fun hideFeedTab() = preferenceStore.getBoolean("hide_feed_tab", false)
    fun feedTabInFront() = preferenceStore.getBoolean("feed_tab_position", false)

    fun showMangaScanlatorBranches() = preferenceStore.getBoolean("show_manga_scanlator_branches", true)

    fun auroraLibraryImmersiveMode() = preferenceStore.getBoolean(
        "aurora_library_immersive_mode",
        false,
    )

    fun auroraLibrarySwipeSwitchesCategories() = preferenceStore.getBoolean(
        "aurora_library_swipe_switches_categories",
        false,
    )

    fun libraryUpdatePacingTimeoutSeconds() = preferenceStore.getInt(
        "library_update_pacing_timeout_seconds",
        0,
    )

    fun libraryUpdatePacingSourceKeys() = preferenceStore.getStringSet(
        "library_update_pacing_source_keys",
        emptySet(),
    )

    fun entryAutoJumpToNextAnime() = preferenceStore.getBoolean("entry_auto_jump_to_next_anime", false)

    fun entryAutoJumpToNextManga() = preferenceStore.getBoolean("entry_auto_jump_to_next_manga", false)

    fun entryAutoJumpToNextNovel() = preferenceStore.getBoolean("entry_auto_jump_to_next_novel", false)

    fun bottomNavAppearance() = preferenceStore.getEnum("bottom_nav_appearance", BottomNavAppearance.Aurora)

    fun episodeListDensity() = preferenceStore.getEnum("episode_list_density", EpisodeListDensity.Comfortable)

    fun navStyle() = preferenceStore.getEnum("bottom_rail_nav_style", NavStyle.MOVE_HISTORY_TO_MORE)

    /** Order of the Aurora «More» screen entries; empty means the default order. */
    fun moreMenuOrder() = preferenceStore.getString("aurora_more_menu_order", "")

    /** Names of the [eu.kanade.tachiyomi.ui.more.MoreEntryId] entries hidden from the «More» screen. */
    fun moreMenuHidden() = preferenceStore.getStringSet("aurora_more_menu_hidden", emptySet())

    fun hapticFeedbackMode() = preferenceStore.getEnum(
        "haptic_feedback_mode",
        HapticFeedbackMode.PARTIAL,
    )

    fun navigationTransitionMode() = preferenceStore.getEnum(
        "navigation_transition_mode",
        NavTransitionMode.MODERN,
    )

    // V7.6: fluidez de movimientos (Adaptable / Estándar). Por defecto
    // Adaptable: la ventana pide la tasa máxima del panel. Estándar pide
    // 60 Hz. Se aplica en MainActivity al reanudar y al cambiar en Ajustes.
    fun refreshRateMode() = preferenceStore.getEnum(
        "refresh_rate_mode",
        RefreshRateMode.ADAPTIVE,
    )

    // V7.9: opacidad de la cápsula Aurora (0-100). 40 por defecto: cristal.
    // 0 = cristal translúcido como la barra; 100 = color liso (negro en
    // tema oscuro, blanco en tema claro). Se aplica al instante, sin
    // reinicio; se lee en HomeScreen (AuroraBarItemShell).
    fun auroraCapsuleOpacity() = preferenceStore.getInt(
        "aurora_capsule_opacity",
        40,
    )

    /**
     * Source for external metadata (posters, ratings, type, status).
     * Default is NONE (Off).
     */
    fun metadataSource() = preferenceStore.getEnum(
        "anime_metadata_source",
        MetadataSource.NONE,
    )

    @Deprecated("Use metadataSource() instead", ReplaceWith("metadataSource()"))
    fun animeMetadataSource() = metadataSource()

    /**
     * Whether the metadata authentication hint has been shown.
     * Used to show the hint only once.
     */
    fun metadataAuthHintShown() = preferenceStore.getBoolean("metadata_auth_hint_shown", false)

    @Deprecated("Use animeMetadataSource() instead", ReplaceWith("animeMetadataSource()"))
    fun useShikimoriRating() = preferenceStore.getBoolean("use_shikimori_rating", true)

    @Deprecated("Use animeMetadataSource() instead", ReplaceWith("animeMetadataSource()"))
    fun useShikimoriCovers() = preferenceStore.getBoolean("use_shikimori_covers", true)

    fun showOriginalTitle() = preferenceStore.getBoolean("show_original_title", true)

    fun auroraEntryTranslationEnabled() = preferenceStore.getBoolean(
        "aurora_entry_translation_enabled",
        false,
    )

    fun auroraEntryTranslationSourceLanguages() = preferenceStore.getStringSet(
        "aurora_entry_translation_source_languages",
        emptySet(),
    )


    fun showTabGlow() = preferenceStore.getBoolean("show_tab_glow", false)

    fun showCelestialNavbar() = preferenceStore.getBoolean("show_celestial_navbar", false)

    fun showCircuitNavbar() = preferenceStore.getBoolean("show_circuit_navbar", false)

    // V5: fondo ambiental animado apagado por defecto (el usuario puede
    // reactivarlo en Ajustes > Apariencia); el canvas especial además ya no
    // anima en bucle (ver AuroraComponents).
    fun animatedAuroraBackground() = preferenceStore.getBoolean("animated_aurora_background", false)

    // V6: modo ultra ligero: apaga de golpe animaciones, blur, fondos animados
    // y fuerza miniaturas en baja resolución en toda la app. Los cambios de
    // Compose se aplican al instante; los del pipeline de imágenes (crossfade,
    // RGB565), al reabrir la app.
    fun ultraLightMode() = preferenceStore.getBoolean("ultra_light_mode", false)

    fun auroraDarkRimLightEnabled() = preferenceStore.getBoolean("aurora_dark_rim_light_enabled", true)

    fun auroraFinishedStampEnabled() = preferenceStore.getBoolean("aurora_finished_stamp_enabled", true)

    fun disableHomeHeaderScrollHide() = preferenceStore.getBoolean(
        "disable_home_header_scroll_hide",
        false,
    )

    fun specialBackgroundStyle() = preferenceStore.getString("special_background_style", "none")

    fun eInkProfile() = preferenceStore.getEnum("e_ink_profile", EInkProfile.OFF)

    fun eInkThemeMode() = preferenceStore.getEnum("e_ink_theme_mode", EInkThemeMode.SYSTEM)

    fun eInkAutoOptimization() = preferenceStore.getBoolean("e_ink_auto_optimization", false)

    fun appUiFontId() = preferenceStore.getString("app_ui_font_id", DEFAULT_APP_UI_FONT_ID)

    fun coverTitleFontId() = preferenceStore.getString("cover_title_font_id", DEFAULT_COVER_TITLE_FONT_ID)

    fun enabledAuras() = preferenceStore.getStringSet("enabled_auras", emptySet())

    fun debugBypassTreasuryLocks() = preferenceStore.getBoolean("debug_bypass_treasury_locks", false)

    fun showSeasonTabs() = preferenceStore.getBoolean("show_season_tabs", true)

    fun alwaysShowFullEpisodeList() = preferenceStore.getBoolean("always_show_full_episode_list", false)

    fun alwaysShowFullChapterListManga() = preferenceStore.getBoolean("always_show_full_chapter_list_manga", false)

    fun alwaysShowFullChapterListNovel() = preferenceStore.getBoolean("always_show_full_chapter_list_novel", false)

    fun entrySuggestionsExpandInline() = preferenceStore.getBoolean("entry_suggestions_expand_inline", true)

    fun entrySuggestionsInOverflow() = preferenceStore.getBoolean("entry_suggestions_in_overflow", false)

    fun titleScreenStyle() = preferenceStore.getEnum("pref_title_screen_style", TitleScreenStyle.POSTER_IMMERSIVE)

    fun titleScreenAnimation() = preferenceStore.getBoolean("pref_title_screen_animation", true)

    // v7.9.2 — Actualizaciones: estrenos y fechas manuales.
    // Clave API de TMDB (gratuita, themoviedb.org). Vacía = los carruseles
    // de películas y series muestran el aviso y no llaman a la API.
    fun tmdbApiKey() = preferenceStore.getString("pref_tmdb_api_key", "")

    // Fechas de actualización puestas a mano por el usuario, en JSON:
    // {"al:<id anilist>": {"title": "...", "date": "2026-11-28"},
    //  "lib:<id anime local>": {...}}. Se muestran en el calendario con
    // un marcador distinto y tienen prioridad sobre la fecha de AniList.
    fun animeCustomDates() = preferenceStore.getString("pref_anime_custom_dates", "")

    // Lista local "Por ver" (películas y series de TMDB), en JSON:
    // {"movie:<id>": {"title": "...", "poster": "https://...",
    //  "date": "2026-12-05", "watched": false}}. Local, sin cuenta.
    fun tmdbWatchlist() = preferenceStore.getString("pref_tmdb_watchlist", "")

    companion object {
        const val DEFAULT_APP_UI_FONT_ID = ""
        const val DEFAULT_COVER_TITLE_FONT_ID = ""

        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }
    }
}
