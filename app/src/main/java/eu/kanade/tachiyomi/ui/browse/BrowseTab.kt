package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.tadami.aurora.R
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.presentation.components.TabbedScreenAurora
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.extension.animeExtensionsTab
import eu.kanade.tachiyomi.ui.browse.anime.feed.animeFeedTab
import eu.kanade.tachiyomi.ui.browse.anime.migration.sources.migrateAnimeSourceTab
import eu.kanade.tachiyomi.ui.browse.anime.source.animeSourcesTab
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.extension.MangaExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.extension.mangaExtensionsTab
import eu.kanade.tachiyomi.ui.browse.manga.feed.mangaFeedTab
import eu.kanade.tachiyomi.ui.browse.manga.migration.sources.migrateMangaSourceTab
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.mangaSourcesTab
import eu.kanade.tachiyomi.ui.browse.novel.extension.NovelExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.novel.extension.novelExtensionsTab
import eu.kanade.tachiyomi.ui.browse.novel.feed.novelFeedTab
import eu.kanade.tachiyomi.ui.browse.novel.migration.sources.migrateNovelSourceTab
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.novelSourcesTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.injectLazy

data object BrowseTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current is BrowseTab
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        // Respect the section the user is actually working in: re-tapping Browse from the
        // manga section must open the manga global search, not the anime one.
        val screen = when (resolveInitialSection()) {
            BrowseSection.Manga -> GlobalMangaSearchScreen()
            BrowseSection.Novel -> GlobalNovelSearchScreen()
            BrowseSection.Anime -> GlobalAnimeSearchScreen()
        }
        // BGS-12: re-tapping Browse while the matching global search is already on top used to
        // push another copy (identical screens stacked, each running a full fan-out search on
        // reveal).
        if (navigator.lastItem::class == screen::class) return
        navigator.push(screen)
    }

    private val uiPreferences: UiPreferences by injectLazy()
    private val userProfilePreferences: UserProfilePreferences by injectLazy()

    /** Initial/last browse section from preferences, validated against the enabled sections. */
    private fun resolveInitialSection(): BrowseSection {
        val enabled = buildBrowseSections(
            showAnimeSection = uiPreferences.showAnimeSection().get(),
            showMangaSection = uiPreferences.showMangaSection().get(),
            showNovelSection = uiPreferences.showNovelSection().get(),
        )
        val stored = userProfilePreferences.browseLastSection().get()
        return enabled.firstOrNull { it.name.equals(stored, ignoreCase = true) }
            ?: enabled.firstOrNull()
            ?: BrowseSection.Anime
    }

    // BFEED-25 (residual, accepted-mitigated): buffered capacity 1 + DROP_OLDEST, consumed
    // once by the tab's LaunchedEffect collector; senders are HomeScreen's deep-link routing
    // (which since BFEED-23/24 only fires for VISIBLE sections) - an event sent while the tab
    // is not composed is consumed on its next composition (intended for the deep-link jump).
    private val switchToTabNumberChannel = Channel<Int>(1, BufferOverflow.DROP_OLDEST)

    internal enum class BrowseSection {
        Anime,
        Manga,
        Novel,
    }

    internal fun buildBrowseSections(
        showAnimeSection: Boolean,
        showMangaSection: Boolean,
        showNovelSection: Boolean,
    ): List<BrowseSection> {
        return buildList {
            if (showAnimeSection) add(BrowseSection.Anime)
            if (showMangaSection) add(BrowseSection.Manga)
            if (showNovelSection) add(BrowseSection.Novel)
        }
    }

    fun showExtension() {
        switchToTabNumberChannel.trySend(TAB_MANGA_EXTENSIONS)
    }

    fun showAnimeExtension() {
        switchToTabNumberChannel.trySend(TAB_ANIME_EXTENSIONS)
    }

    fun showNovelExtension() {
        switchToTabNumberChannel.trySend(TAB_NOVEL_EXTENSIONS)
    }

    private const val TAB_ANIME_EXTENSIONS = 1
    private const val TAB_MANGA_EXTENSIONS = 4
    private const val TAB_NOVEL_EXTENSIONS = 7

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val showAnimeSection by uiPreferences.showAnimeSection().collectAsStateWithLifecycle()
        val showMangaSection by uiPreferences.showMangaSection().collectAsStateWithLifecycle()
        val showNovelSection by uiPreferences.showNovelSection().collectAsStateWithLifecycle()
        val colors = AuroraTheme.colors

        val animeExtensionsScreenModel = rememberScreenModel { AnimeExtensionsScreenModel() }
        val animeExtensionsState by animeExtensionsScreenModel.state.collectAsStateWithLifecycle()

        val mangaExtensionsScreenModel = rememberScreenModel { MangaExtensionsScreenModel() }
        val mangaExtensionsState by mangaExtensionsScreenModel.state.collectAsStateWithLifecycle()

        val novelExtensionsScreenModel = rememberScreenModel { NovelExtensionsScreenModel() }
        val novelExtensionsState by novelExtensionsScreenModel.state.collectAsStateWithLifecycle()

        val hideFeedTab by uiPreferences.hideFeedTab().collectAsStateWithLifecycle()
        val feedTabInFront by uiPreferences.feedTabInFront().collectAsStateWithLifecycle()

        // v4 — Explorar junto: UN SOLO apartado con las pestañas de anime,
        // manga y novelas (las activas) en la misma fila de cápsulas, agrupadas
        // por función: primero las fuentes de cada medio, luego el contenido,
        // las extensiones y la migración. Cada cápsula lleva título propio:
        // las de Contenido y Migrar se distinguen por medio (cadenas nuevas
        // feed_anime/feed_manga/feed_novel y label_migration_novel), porque
        // antes las tres decían lo mismo ("Contenido" / "Migrar").
        // El orden respeta hideFeedTab y feedTabInFront como antes.
        val showFeed = !hideFeedTab
        val sourceEntries = listOfNotNull(
            if (showAnimeSection) Triple(BrowseSection.Anime, "sources", animeSourcesTab()) else null,
            if (showMangaSection) Triple(BrowseSection.Manga, "sources", mangaSourcesTab()) else null,
            if (showNovelSection) Triple(BrowseSection.Novel, "sources", novelSourcesTab()) else null,
        )
        val feedEntries = listOfNotNull(
            if (showAnimeSection && showFeed) {
                Triple(BrowseSection.Anime, "feed", animeFeedTab().copy(titleRes = AYMR.strings.feed_anime))
            } else {
                null
            },
            if (showMangaSection && showFeed) {
                Triple(BrowseSection.Manga, "feed", mangaFeedTab().copy(titleRes = AYMR.strings.feed_manga))
            } else {
                null
            },
            if (showNovelSection && showFeed) {
                Triple(BrowseSection.Novel, "feed", novelFeedTab().copy(titleRes = AYMR.strings.feed_novel))
            } else {
                null
            },
        )
        val extensionEntries = listOfNotNull(
            if (showAnimeSection) {
                Triple(BrowseSection.Anime, "extensions", animeExtensionsTab(animeExtensionsScreenModel))
            } else {
                null
            },
            if (showMangaSection) {
                Triple(BrowseSection.Manga, "extensions", mangaExtensionsTab(mangaExtensionsScreenModel))
            } else {
                null
            },
            if (showNovelSection) {
                Triple(BrowseSection.Novel, "extensions", novelExtensionsTab(novelExtensionsScreenModel))
            } else {
                null
            },
        )
        val migrateEntries = listOfNotNull(
            if (showAnimeSection) Triple(BrowseSection.Anime, "migrate", migrateAnimeSourceTab()) else null,
            if (showMangaSection) {
                Triple(
                    BrowseSection.Manga,
                    "migrate",
                    migrateMangaSourceTab().copy(titleRes = AYMR.strings.label_migration_manga),
                )
            } else {
                null
            },
            if (showNovelSection) {
                Triple(
                    BrowseSection.Novel,
                    "migrate",
                    migrateNovelSourceTab().copy(titleRes = AYMR.strings.label_migration_novel),
                )
            } else {
                null
            },
        )
        val combinedEntries = buildList {
            if (feedTabInFront) addAll(feedEntries)
            addAll(sourceEntries)
            if (!feedTabInFront) addAll(feedEntries)
            addAll(extensionEntries)
            addAll(migrateEntries)
        }
        val combinedTabs = combinedEntries.map { it.third }.toPersistentList()
        val tabMedia = combinedEntries.map { it.first }
        fun combinedIndex(media: BrowseSection, kind: String): Int {
            return combinedEntries.indexOfFirst { it.first == media && it.second == kind }
        }
        val animeExtensionsIndex = combinedIndex(BrowseSection.Anime, "extensions")
        val mangaExtensionsIndex = combinedIndex(BrowseSection.Manga, "extensions")
        val novelExtensionsIndex = combinedIndex(BrowseSection.Novel, "extensions")

        val sections = remember(showAnimeSection, showMangaSection, showNovelSection) {
            buildBrowseSections(
                showAnimeSection = showAnimeSection,
                showMangaSection = showMangaSection,
                showNovelSection = showNovelSection,
            )
        }
        // La sección guardada (browseLastSection, escrita por los iconos
        // Anime/Manga de la barra y por los deep links) solo decide en qué
        // cápsula de fuentes arranca Explorar; todas las cápsulas de ambos
        // medios están siempre en la misma lista.
        val storedBrowseSection by userProfilePreferences.browseLastSection()
            .collectAsStateWithLifecycle()
        val initialSection = sections.firstOrNull {
            it.name.equals(storedBrowseSection, ignoreCase = true)
        } ?: sections.firstOrNull() ?: BrowseSection.Anime
        val initialPage = combinedIndex(initialSection, "sources").takeIf { it >= 0 } ?: 0
        val state = rememberPagerState(initialPage = initialPage) { combinedTabs.size }
        val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
        val useWideBrowseTabs = auroraAdaptiveSpec.deviceClass != AuroraDeviceClass.Phone

        Column(modifier = Modifier.fillMaxSize()) {
            TabbedScreenAurora(
                titleRes = MR.strings.browse,
                tabs = combinedTabs,
                state = state,
                mangaSearchQuery = mangaExtensionsState.searchQuery,
                onChangeMangaSearchQuery = mangaExtensionsScreenModel::search,
                animeSearchQuery = animeExtensionsState.searchQuery,
                onChangeAnimeSearchQuery = animeExtensionsScreenModel::search,
                novelSearchQuery = novelExtensionsState.searchQuery,
                onChangeNovelSearchQuery = novelExtensionsScreenModel::search,
                isMangaTab = { page -> tabMedia.getOrNull(page) == BrowseSection.Manga },
                isNovelTab = { page -> tabMedia.getOrNull(page) == BrowseSection.Novel },
                // Keep browse sub-tabs content-sized to avoid the full-width stretched segmented bar.
                scrollable = true,
                showTabRowBorder = false,
                applyStatusBarsPadding = true,
                highlightSearchAction = false,
                highlightedActionTitle = null,
                extraActionGapAfterTitle = null,
            )
        }

        LaunchedEffect(combinedTabs.size) {
            val lastIndex = combinedTabs.lastIndex
            if (lastIndex >= 0 && state.currentPage > lastIndex) {
                state.scrollToPage(lastIndex)
            }
        }

        LaunchedEffect(Unit) {
            switchToTabNumberChannel.receiveAsFlow()
                .collectLatest { targetIndex ->
                    // v4: Explorar ya no cambia de sección; el deep link solo
                    // salta a la cápsula de extensiones de su medio.
                    // BFEED-23: cada rama comprueba que su medio esté activo;
                    // con el medio desactivado su cápsula no existe (-1).
                    val page = when (targetIndex) {
                        TAB_MANGA_EXTENSIONS ->
                            mangaExtensionsIndex.takeIf { it >= 0 && showMangaSection }
                        TAB_ANIME_EXTENSIONS ->
                            animeExtensionsIndex.takeIf { it >= 0 && showAnimeSection }
                        TAB_NOVEL_EXTENSIONS ->
                            novelExtensionsIndex.takeIf { it >= 0 && showNovelSection }
                        else -> null
                    }
                    if (page != null) {
                        state.scrollToPage(page)
                    }
                }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
