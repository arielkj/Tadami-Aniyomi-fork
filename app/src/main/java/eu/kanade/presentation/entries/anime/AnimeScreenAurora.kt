package eu.kanade.presentation.entries.anime

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import aniyomi.domain.anime.SeasonAnime
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.kanade.domain.entries.anime.model.episodesFiltered
import eu.kanade.domain.entries.anime.model.seasonsFiltered
import eu.kanade.domain.metadata.model.MetadataLoadError
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.EpisodeListDensity
import eu.kanade.domain.ui.model.TitleScreenStyle
import eu.kanade.presentation.components.AuroraBackground
import eu.kanade.presentation.components.EntryDownloadDropdownMenu
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.TitleFastScrollOverlayAccumulator
import eu.kanade.presentation.entries.anime.components.AnimeSeasonListItem
import eu.kanade.presentation.entries.anime.components.AnimeSeasonSwitcherAurora
import eu.kanade.presentation.entries.anime.components.AnimeSeasonSwitcherItem
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.entries.anime.components.NextEpisodeAiringListItem
import eu.kanade.presentation.entries.anime.components.aurora.AnimeActionCard
import eu.kanade.presentation.entries.anime.components.aurora.AnimeDetailHero
import eu.kanade.presentation.entries.anime.components.aurora.AnimeCarouselMissingCard
import eu.kanade.presentation.entries.anime.components.aurora.AnimeDetailLibraryPills
import eu.kanade.presentation.entries.anime.components.aurora.AnimeDetailScrolledTitle
import eu.kanade.presentation.entries.anime.components.aurora.AnimeDetailSynopsisCard
import eu.kanade.presentation.entries.anime.components.aurora.AnimeDetailsSnapshot
import eu.kanade.presentation.entries.anime.components.aurora.AnimeEpisodeCardCompact
import eu.kanade.presentation.entries.anime.components.aurora.AnimeEpisodeGridCard
import eu.kanade.presentation.entries.anime.components.aurora.AnimeGlassHeroCard
import eu.kanade.presentation.entries.anime.components.aurora.AnimeInfoCard
import eu.kanade.presentation.entries.anime.components.aurora.AnimeStatsCard
import eu.kanade.presentation.entries.anime.components.aurora.EpisodesHeader
import eu.kanade.presentation.entries.anime.components.aurora.FullscreenPosterBackground
import eu.kanade.presentation.entries.anime.components.aurora.filterAnimeDescription
import eu.kanade.presentation.entries.anime.components.aurora.resolveAnimeDetailsSnapshot
import eu.kanade.presentation.entries.anime.components.resolveAnimeSeasonSwitcherItems
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenu
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenuItem
import eu.kanade.presentation.entries.components.AuroraEntryHoldToRefresh
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.FinaleStamp
import eu.kanade.presentation.entries.components.MissingItemCountListItem
import eu.kanade.presentation.entries.components.ResolvedCover
import eu.kanade.presentation.entries.components.aurora.AuroraTitleHeroActionFab
import eu.kanade.presentation.entries.components.aurora.AuroraTitleStaggerState
import eu.kanade.presentation.entries.components.aurora.AuroraZIndex
import eu.kanade.presentation.entries.components.aurora.auroraPosterLongPress
import eu.kanade.presentation.entries.components.aurora.auroraSpringClick
import eu.kanade.presentation.entries.components.aurora.rememberTitleScreenStaggerState
import eu.kanade.presentation.entries.components.aurora.resolveAuroraFabBottomPadding
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroBottomPadding
import eu.kanade.presentation.entries.components.aurora.titleScreenPosterEntrance
import eu.kanade.presentation.entries.components.aurora.titleScreenStagger
import eu.kanade.presentation.entries.components.normalizeAuroraGlobalSearchQuery
import eu.kanade.presentation.entries.components.resolveExternalMetadataCover
import eu.kanade.presentation.entries.reduceTitleFastScrollOverlayAccumulator
import eu.kanade.presentation.entries.resolveEntryAutoJumpTargetIndex
import eu.kanade.presentation.entries.resolveTitleListFastScrollSpec
import eu.kanade.presentation.entries.shouldShowTitleFastScrollFloatingActionButton
import eu.kanade.presentation.entries.shouldShowTitleFastScrollOverlayChrome
import eu.kanade.presentation.entries.translation.AuroraEntryTranslationState
import eu.kanade.presentation.entries.translation.rememberAuroraEntryTranslation
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.resolveAuroraAdaptiveSpec
import eu.kanade.presentation.theme.auroraHeaderIconSurface
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.source.anime.getNameForAnimeInfo
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreenModel
import eu.kanade.tachiyomi.ui.entries.anime.AnimeSeasonItem
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import eu.kanade.tachiyomi.util.debugTitleCoverFlow
import eu.kanade.tachiyomi.util.previewTitleCoverUrl
import eu.kanade.tachiyomi.util.system.copyToClipboardSilently
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.metadata.model.MetadataSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DateFormat
import java.time.Instant
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimeScreenAuroraImpl(
    state: AnimeScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    navigateUp: () -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,
    onSuggestionClick: (eu.kanade.tachiyomi.data.suggestions.SuggestionItem) -> Unit,
    onCoverClicked: () -> Unit,
    onPosterLongClicked: (() -> Unit)? = onCoverClicked,
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onEditNotesClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    changeAnimeSkipIntro: (() -> Unit)?,
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onSeasonClicked: ((aniyomi.domain.anime.SeasonAnime) -> Unit)?,
    onContinueWatchingClicked: ((aniyomi.domain.anime.SeasonAnime) -> Unit)?,
    onDubbingClicked: (() -> Unit)?,
    selectedDubbing: String?,
    onDownloadLongClick: ((Episode) -> Unit)?,
    onRetryMetadata: () -> Unit,
    onSettingsClicked: (() -> Unit)?,
    onToggleSort: (() -> Unit)? = null,
    onToggleUnseenFilter: (() -> Unit)? = null,
    isAutoJumpToNextEnabled: Boolean,
    autoJumpToNextLabel: String,
    onToggleAutoJumpToNext: () -> Unit,
    onClickEditInfo: (() -> Unit)? = null,
    onRetrySuggestions: () -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
    onGenreClick: ((String) -> Unit)? = null,
    onGenreLongClick: ((String) -> Unit)? = null,
    onGenresSearch: ((List<String>) -> Unit)? = null,
    onSourceClick: (() -> Unit)? = null,
    onDeleteDownloadsClicked: () -> Unit = {},
    onDeleteReadEpisodesClicked: () -> Unit = {},
) {
    val anime = state.anime
    val selectedGenresState = remember { mutableStateOf(emptySet<String>()) }
    var selectedGenres by selectedGenresState
    val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val entrySuggestionsEnabled by sourcePreferences.entrySuggestionsEnabled().collectAsState()
    val entrySuggestionsExpandInline by uiPreferences.entrySuggestionsExpandInline().collectAsState()
    val entrySuggestionsInOverflow by uiPreferences.entrySuggestionsInOverflow().collectAsState()
    val showSeasonTabs by uiPreferences.showSeasonTabs().collectAsState()
    val alwaysShowFullEpisodeList by uiPreferences.alwaysShowFullEpisodeList().collectAsState()
    val episodeListDensity by uiPreferences.episodeListDensity().collectAsState()
    val titleScreenStyle by uiPreferences.titleScreenStyle().collectAsState()
    val titleScreenAnimation by uiPreferences.titleScreenAnimation().collectAsState()
    // V6: el modo ultra ligero apaga la animación de entrada al detalle.
    val ultraLightMode by uiPreferences.ultraLightMode().collectAsState()
    val titleStaggerState = rememberTitleScreenStaggerState(titleScreenAnimation && !ultraLightMode)
    val showOriginalTitle by uiPreferences.showOriginalTitle().collectAsState()
    val globalSearchQuery = remember(anime.displayTitle) { normalizeAuroraGlobalSearchQuery(anime.displayTitle) }
    val episodes = state.episodeListItems
    val selectedEpisodes = remember(episodes) {
        episodes.filterIsInstance<EpisodeList.Item>().filter { it.selected }
    }
    val isAnyEpisodeSelected = selectedEpisodes.isNotEmpty()
    val seasons = remember(state) { state.processedSeasons }
    val seasonSwitcherItems = remember(seasons) {
        resolveAnimeSeasonSwitcherItems(
            currentAnimeId = anime.id,
            seasons = seasons.map { it.seasonAnime },
        )
    }
    val hasFilters = remember(state) {
        when (state.anime.fetchType) {
            FetchType.Seasons -> state.anime.seasonsFiltered(state.downloadedOnly)
            FetchType.Episodes -> state.anime.episodesFiltered(state.downloadedOnly)
        }
    }

    val virtualSeasons = rememberAnimeAuroraVirtualSeasons(
        episodes = episodes,
        showSeasonTabs = showSeasonTabs,
    )
    val distinctVirtualSeasons = virtualSeasons.distinctVirtualSeasons
    val episodeIdToVirtualSeason = virtualSeasons.episodeIdToVirtualSeason
    val selectedVirtualSeasonState = virtualSeasons.selectedVirtualSeasonState
    var selectedVirtualSeason by selectedVirtualSeasonState
    val filteredEpisodes = virtualSeasons.filteredEpisodes
    val realEpisodeCount = virtualSeasons.realEpisodeCount
    val missingEpisodesCount = virtualSeasons.missingEpisodesCount

    val colors = AuroraTheme.colors
    val hazeState = remember { HazeState() }
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val auroraAdaptiveSpec = remember(isTabletUi, configuration.screenWidthDp) {
        resolveAuroraAdaptiveSpec(
            isTabletUi = isTabletUi,
            containerWidthDp = configuration.screenWidthDp,
        )
    }
    val contentMaxWidthDp = auroraAdaptiveSpec.entryMaxWidthDp
    val useTwoPaneLayout = shouldUseAnimeAuroraTwoPane(auroraAdaptiveSpec.deviceClass)

    val metadataSource: MetadataSource = remember {
        Injekt.get<eu.kanade.domain.ui.UiPreferences>().metadataSource().get()
    }

    val resolvedCover = remember(
        state.anime,
        state.isMetadataLoading,
        state.metadataError,
        state.animeMetadata,
        metadataSource,
    ) {
        resolveCoverUrl(state, metadataSource != MetadataSource.NONE)
    }
    val refererUrl = remember(state.source) {
        (state.source as? AnimeHttpSource)?.baseUrl
    }
    val sourceHeaders = remember(state.source) {
        (state.source as? AnimeHttpSource)?.headers?.toMap()
    }
    val sourceClient = remember(state.source) {
        (state.source as? AnimeHttpSource)?.client
    }
    LaunchedEffect(
        anime.id,
        state.isMetadataLoading,
        state.metadataError,
        resolvedCover.coverUrl,
        resolvedCover.coverUrlFallback,
    ) {
        val debugMessage = "id=${anime.id} loading=${state.isMetadataLoading} " +
            "error=${state.metadataError?.javaClass?.simpleName ?: "none"} " +
            "base=${previewTitleCoverUrl(anime.thumbnailUrl)} " +
            "resolved=${previewTitleCoverUrl(resolvedCover.coverUrl)} " +
            "fallback=${previewTitleCoverUrl(resolvedCover.coverUrlFallback)} " +
            "referer=${previewTitleCoverUrl(refererUrl)}"
        debugTitleCoverFlow(
            scope = "anime-screen",
            message = debugMessage,
        )
    }

    val lazyListState = rememberLazyListState()
    // Scroll values are exposed as State and read only inside the smallest consumer scopes
    // (poster background, hero layer, top bar row) so per-pixel scroll updates do not
    // recompose the whole screen.
    val scrollOffsetState = remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
    val firstVisibleItemIndexState = remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
    val context = LocalContext.current
    val animeDetailsSnapshot = remember(
        anime,
        episodes,
        state.source,
        selectedDubbing,
        nextUpdate,
        state.animeMetadata,
        state.sourceRating,
        state.isMetadataLoading,
        state.metadataError,
    ) {
        resolveAnimeDetailsSnapshot(
            anime = anime,
            watchedCount = state.episodes.count { it.episode.seen || it.episode.lastSecondSeen > 0L },
            totalEpisodes = state.episodes.size,
            sourceName = state.source.name,
            // v7.9.6: proveedor al final de la sinopsis, igual que en manga.
            sourceTitle = state.source.getNameForAnimeInfo(),
            sourceLanguage = state.source.lang,
            selectedDubbing = selectedDubbing,
            nextUpdate = nextUpdate,
            sourceRating = state.sourceRating,
            animeMetadata = state.animeMetadata,
            isMetadataLoading = state.isMetadataLoading,
            metadataError = state.metadataError,
            context = context,
        )
    }

    // Keepsake «finished» stamp: live predicate (completed status + fully watched) + pref;
    // keepsake date from the persisted first completion.
    val finishedStampEnabled by uiPreferences.auroraFinishedStampEnabled().collectAsState()
    val showFinishedStamp = finishedStampEnabled &&
        anime.displayStatus == SManga.COMPLETED.toLong() &&
        animeDetailsSnapshot.progress?.isCompleted == true
    val finishedStampDate = remember(anime.completedAt) {
        anime.completedAt?.let { DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it)) }
    }

    // State for episodes expansion
    val episodesExpandedState = remember(alwaysShowFullEpisodeList) { mutableStateOf(alwaysShowFullEpisodeList) }
    var episodesExpanded by episodesExpandedState
    var isReverseScrollingOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(episodesExpanded) {
        if (episodesExpanded) {
            isReverseScrollingOverlay = false
        }
    }
    val isThumbFastScrollingState = remember { mutableStateOf(false) }
    var isThumbFastScrolling by isThumbFastScrollingState
    LaunchedEffect(Unit) {
        snapshotFlow {
            Triple(
                lazyListState.isScrollInProgress,
                lazyListState.firstVisibleItemIndex,
                lazyListState.firstVisibleItemScrollOffset,
            )
        }
            .conflate()
            .runningFold(
                TitleFastScrollOverlayAccumulator(
                    prevIndex = lazyListState.firstVisibleItemIndex,
                    prevOffset = lazyListState.firstVisibleItemScrollOffset,
                    revealed = false,
                ),
            ) { current, (isScrolling, index, offset) ->
                reduceTitleFastScrollOverlayAccumulator(
                    current = current,
                    isExpandedList = episodesExpanded,
                    isScrolling = isScrolling,
                    index = index,
                    offset = offset,
                )
            }
            .map { it.revealed }
            .distinctUntilChanged()
            .collect { isReverseScrollingOverlay = it }
    }
    val episodesToShow = remember(filteredEpisodes, episodesExpanded) {
        if (episodesExpanded) {
            filteredEpisodes
        } else {
            val result = mutableListOf<EpisodeList>()
            var visibleEpisodes = 0
            for (item in filteredEpisodes) {
                if (item is EpisodeList.Item) {
                    if (visibleEpisodes >= 5) break
                    visibleEpisodes++
                }
                result += item
            }
            result
        }
    }
    // Definitive detail design (v4): on the mobile immersive screen the
    // episodes render as a horizontal carousel instead of compact list rows.
    val useEpisodeCarousel = !useTwoPaneLayout && titleScreenStyle == TitleScreenStyle.POSTER_IMMERSIVE
    val firstEpisodeNumberText = remember(episodes) {
        episodes.filterIsInstance<EpisodeList.Item>()
            .minOfOrNull { it.episode.episodeNumber }
            ?.takeIf { it > 0f }
            ?.let { formatEpisodeNumber(it) }
    }
    val haptic = LocalHapticFeedback.current
    val showAnimeOverlayChrome by remember {
        derivedStateOf {
            shouldShowTitleFastScrollOverlayChrome(
                isThumbDragged = isThumbFastScrolling,
                isExpandedList = episodesExpanded,
                isReverseScrolling = isReverseScrollingOverlay,
            )
        }
    }

    // Auto-scroll to target episode on initial load
    var hasScrolledToTarget: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(state.targetEpisodeIndex, isAutoJumpToNextEnabled) {
        val targetIndex = resolveEntryAutoJumpTargetIndex(
            enabled = isAutoJumpToNextEnabled,
            targetIndex = state.targetEpisodeIndex,
            restoredScrollIndex = state.scrollIndex,
        )
        if (targetIndex != null) {
            episodesExpanded = true
        }
    }

    LaunchedEffect(state.targetEpisodeIndex, episodesExpanded) {
        if (!hasScrolledToTarget && episodesExpanded) {
            hasScrolledToTarget = true
            val targetIndex = resolveEntryAutoJumpTargetIndex(
                enabled = isAutoJumpToNextEnabled,
                targetIndex = state.targetEpisodeIndex,
                restoredScrollIndex = state.scrollIndex,
            )
            if (targetIndex != null) {
                val fastScrollBlockStartIndex = resolveAnimeAuroraFastScrollBlockStartIndex(
                    useTwoPaneLayout = useTwoPaneLayout,
                )
                // The carousel is a single LazyColumn item, so the target
                // episode sits at the carousel's own index; list layouts
                // keep the 1:1 index. The row itself scrolls sideways to
                // the target card (see AnimeAuroraMobileEpisodeItems).
                val jumpIndex = if (useEpisodeCarousel) {
                    fastScrollBlockStartIndex
                } else {
                    targetIndex + fastScrollBlockStartIndex
                }
                lazyListState.animateScrollToItem(jumpIndex)
            }
        }
    }

    BackHandler(onBack = {
        if (isAnyEpisodeSelected) {
            onAllEpisodeSelected(false)
        } else {
            navigateUp()
        }
    })

    LaunchedEffect(isAnyEpisodeSelected, episodesExpanded, filteredEpisodes.size) {
        if (isAnyEpisodeSelected &&
            shouldAutoExpandAuroraEpisodesList(
                episodesExpanded = episodesExpanded,
                totalEpisodes = filteredEpisodes.size,
            )
        ) {
            episodesExpanded = true
        }
    }

    // State for description and genres expansion
    val descriptionExpandedState = remember { mutableStateOf(false) }
    var descriptionExpanded by descriptionExpandedState
    val genresExpandedState = remember { mutableStateOf(false) }
    var genresExpanded by genresExpandedState

    // Check if metadata auth hint was already shown (persistent)
    val metadataAuthHintShown = remember {
        Injekt.get<eu.kanade.domain.ui.UiPreferences>().metadataAuthHintShown()
    }
    var metadataHintDismissed by remember { mutableStateOf(false) }
    val authHintMessage = stringResource(AYMR.strings.metadata_auth_hint_message)
    val authHintAction = stringResource(AYMR.strings.metadata_auth_hint_action)

    // One-time Snackbar when metadata source is not authenticated
    LaunchedEffect(state.metadataError) {
        if (state.metadataError == MetadataLoadError.NotAuthenticated &&
            !metadataAuthHintShown.get() &&
            !metadataHintDismissed &&
            onTrackingClicked != null
        ) {
            val result = snackbarHostState.showSnackbar(
                message = authHintMessage,
                actionLabel = authHintAction,
                withDismissAction = true, // Add dismiss button
                duration = SnackbarDuration.Long,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> {
                    onTrackingClicked.invoke()
                    metadataAuthHintShown.set(true) // Don't show again
                }
                SnackbarResult.Dismissed -> {
                    metadataHintDismissed = true
                }
            }
        }
    }

    // Check if user already has any watching progress
    val hasWatchingProgress = remember(episodes) {
        episodes.any { episodeItem ->
            val episode = (episodeItem as? EpisodeList.Item)?.episode ?: return@any false
            episode.seen || episode.lastSecondSeen > 0L
        }
    }
    val nextEpisodeNum = remember(animeDetailsSnapshot.progress) {
        val watched = animeDetailsSnapshot.progress?.watchedCount ?: 0
        val total = animeDetailsSnapshot.progress?.totalEpisodes ?: 0
        if (watched in 1 until total) {
            watched + 1
        } else if (watched == total && total > 0) {
            total
        } else {
            null
        }
    }
    val animeActionResumeText = stringResource(MR.strings.action_resume)
    val animeHeroTargetEpText = nextEpisodeNum?.takeIf { it > 0 }?.let {
        stringResource(AYMR.strings.aurora_hero_cta_episode, it)
    }
    val animeHeroActionLabel = remember(hasWatchingProgress, animeHeroTargetEpText, animeActionResumeText) {
        if (hasWatchingProgress && animeHeroTargetEpText != null) {
            "$animeActionResumeText • $animeHeroTargetEpText"
        } else {
            null
        }
    }
    val auroraTranslationPreferences = remember { Injekt.get<UiPreferences>() }
    val auroraEntryTranslationEnabled by auroraTranslationPreferences
        .auroraEntryTranslationEnabled()
        .collectAsState()
    val auroraEntryTranslationSourceLanguages by auroraTranslationPreferences
        .auroraEntryTranslationSourceLanguages()
        .collectAsState()
    val auroraEntryTranslation = rememberAuroraEntryTranslation(
        title = anime.displayTitle,
        description = anime.displayDescription,
        sourceLanguage = state.source.lang,
        enabled = auroraEntryTranslationEnabled,
        allowedSourceFamilies = auroraEntryTranslationSourceLanguages,
    )
    // Synopsis for the definitive detail design: translated text when available,
    // otherwise the description with the metadata lines filtered out.
    val synopsisText = remember(anime.displayDescription, auroraEntryTranslation.description) {
        auroraEntryTranslation.description?.takeUnless { it.isBlank() }
            ?: filterAnimeDescription(anime.displayDescription)
    }

    val scope = rememberCoroutineScope()
    val copiedToClipboardMessage = stringResource(MR.strings.copied_to_clipboard_plain)
    val clipboardCopyErrorMessage = stringResource(MR.strings.clipboard_copy_error)
    val onTitleCopy: () -> Unit = {
        val ok = context.copyToClipboardSilently(
            "Entry title",
            auroraEntryTranslation.title,
        )
        scope.launch {
            snackbarHostState.showSnackbar(if (ok) copiedToClipboardMessage else clipboardCopyErrorMessage)
        }
    }

    AuroraEntryHoldToRefresh(
        refreshing = state.isRefreshingData,
        onRefresh = onRefresh,
        enabled = !isAnyEpisodeSelected,
        modifier = Modifier.fillMaxSize(),
        indicatorPadding = WindowInsets.statusBars.asPaddingValues(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .auroraPosterLongPress(onPosterLongClicked ?: onCoverClicked),
        ) {
            // Fixed background poster or Aurora background.
            // The haze source is scoped to the background layer only: the top
            // bar lenses must blur the poster/aurora backdrop, never the
            // scrolling list content passing underneath them.
            AnimeAuroraBackgroundLayer(
                anime = anime,
                titleScreenStyle = titleScreenStyle,
                scrollOffsetState = scrollOffsetState,
                firstVisibleItemIndexState = firstVisibleItemIndexState,
                resolvedCover = resolvedCover,
                refererUrl = refererUrl,
                sourceHeaders = sourceHeaders,
                sourceClient = sourceClient,
                hazeState = hazeState,
                titleStaggerState = titleStaggerState,
                showFinishedStamp = showFinishedStamp,
                finishedStampDate = finishedStampDate,
                colors = colors,
            )

            if (useTwoPaneLayout) {
                TwoPanelBox(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(AuroraZIndex.HERO),
                    startContent = {
                        AnimeAuroraTwoPaneStartContent(
                            anime = anime,
                            state = state,
                            translation = auroraEntryTranslation,
                            hasWatchingProgress = hasWatchingProgress,
                            detailsSnapshot = animeDetailsSnapshot,
                            titleScreenStyle = titleScreenStyle,
                            showOriginalTitle = showOriginalTitle,
                            resolvedCover = resolvedCover,
                            refererUrl = refererUrl,
                            sourceHeaders = sourceHeaders,
                            sourceClient = sourceClient,
                            showFinishedStamp = showFinishedStamp,
                            finishedStampDate = finishedStampDate,
                            titleStaggerState = titleStaggerState,
                            firstEpisodeNumberText = firstEpisodeNumberText,
                            selectedGenresState = selectedGenresState,
                            descriptionExpandedState = descriptionExpandedState,
                            genresExpandedState = genresExpandedState,
                            entrySuggestionsEnabled = entrySuggestionsEnabled,
                            entrySuggestionsExpandInline = entrySuggestionsExpandInline,
                            entrySuggestionsInOverflow = entrySuggestionsInOverflow,
                            colors = colors,
                            animeHeroActionLabel = animeHeroActionLabel,
                            onEditNotesClicked = onEditNotesClicked,
                            onContinueWatching = onContinueWatching,
                            onDubbingClicked = onDubbingClicked,
                            selectedDubbing = selectedDubbing,
                            onGenreClick = onGenreClick,
                            onGenresSearch = onGenresSearch,
                            onTitleCopy = onTitleCopy,
                            onAddToLibraryClicked = onAddToLibraryClicked,
                            onEditCategoryClicked = onEditCategoryClicked,
                            onWebViewClicked = onWebViewClicked,
                            onTrackingClicked = onTrackingClicked,
                            onShareClicked = onShareClicked,
                            onTagSearch = onTagSearch,
                            onSourceClick = onSourceClick,
                            onCoverClicked = onCoverClicked,
                            onSuggestionClick = onSuggestionClick,
                            onOpenSuggestions = onOpenSuggestions,
                            onRetrySuggestions = onRetrySuggestions,
                        )
                    },
                    endContent = {
                        AnimeAuroraTwoPaneEndContent(
                            lazyListState = lazyListState,
                            anime = anime,
                            state = state,
                            episodes = episodes,
                            seasons = seasons,
                            seasonSwitcherItems = seasonSwitcherItems,
                            distinctVirtualSeasons = distinctVirtualSeasons,
                            selectedVirtualSeasonState = selectedVirtualSeasonState,
                            filteredEpisodes = filteredEpisodes,
                            episodesToShow = episodesToShow,
                            episodesExpandedState = episodesExpandedState,
                            isThumbFastScrollingState = isThumbFastScrollingState,
                            episodeListDensity = episodeListDensity,
                            showSeasonTabs = showSeasonTabs,
                            hasFilters = hasFilters,
                            titleStaggerState = titleStaggerState,
                            screenHeight = screenHeight,
                            isAnyEpisodeSelected = isAnyEpisodeSelected,
                            realEpisodeCount = realEpisodeCount,
                            missingEpisodesCount = missingEpisodesCount,
                            alwaysShowFullEpisodeList = alwaysShowFullEpisodeList,
                            showNextEpisodeAirTime = showNextEpisodeAirTime,
                            haptic = haptic,
                            colors = colors,
                            onSeasonClicked = onSeasonClicked,
                            onContinueWatchingClicked = onContinueWatchingClicked,
                            onToggleSort = onToggleSort,
                            onToggleUnseenFilter = onToggleUnseenFilter,
                            onFilterButtonClicked = onFilterButtonClicked,
                            onEpisodeClicked = onEpisodeClicked,
                            onEpisodeSelected = onEpisodeSelected,
                            onEpisodeSwipe = onEpisodeSwipe,
                            episodeSwipeStartAction = episodeSwipeStartAction,
                            episodeSwipeEndAction = episodeSwipeEndAction,
                            onDownloadEpisode = onDownloadEpisode,
                        )
                    },
                )
            } else {
                AnimeAuroraMobileContent(
                    lazyListState = lazyListState,
                    anime = anime,
                    state = state,
                    titleScreenStyle = titleScreenStyle,
                    contentMaxWidthDp = contentMaxWidthDp,
                    screenHeight = screenHeight,
                    translation = auroraEntryTranslation,
                    detailsSnapshot = animeDetailsSnapshot,
                    synopsisText = synopsisText,
                    hasWatchingProgress = hasWatchingProgress,
                    showOriginalTitle = showOriginalTitle,
                    resolvedCover = resolvedCover,
                    refererUrl = refererUrl,
                    sourceHeaders = sourceHeaders,
                    sourceClient = sourceClient,
                    showFinishedStamp = showFinishedStamp,
                    finishedStampDate = finishedStampDate,
                    titleStaggerState = titleStaggerState,
                    realEpisodeCount = realEpisodeCount,
                    animeHeroActionLabel = animeHeroActionLabel,
                    selectedGenresState = selectedGenresState,
                    descriptionExpandedState = descriptionExpandedState,
                    genresExpandedState = genresExpandedState,
                    seasons = seasons,
                    seasonSwitcherItems = seasonSwitcherItems,
                    distinctVirtualSeasons = distinctVirtualSeasons,
                    selectedVirtualSeasonState = selectedVirtualSeasonState,
                    filteredEpisodes = filteredEpisodes,
                    showSeasonTabs = showSeasonTabs,
                    hasFilters = hasFilters,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onEditCategoryClicked = onEditCategoryClicked,
                    onTrackingClicked = onTrackingClicked,
                    onEditNotesClicked = onEditNotesClicked,
                    onContinueWatching = onContinueWatching,
                    onCoverClicked = onCoverClicked,
                    onTitleCopy = onTitleCopy,
                    onTagSearch = onTagSearch,
                    onGenreClick = onGenreClick,
                    onGenresSearch = onGenresSearch,
                    onWebViewClicked = onWebViewClicked,
                    onShareClicked = onShareClicked,
                    onSourceClick = onSourceClick,
                    onSeasonClicked = onSeasonClicked,
                    onContinueWatchingClicked = onContinueWatchingClicked,
                    onToggleSort = onToggleSort,
                    onToggleUnseenFilter = onToggleUnseenFilter,
                    onFilterButtonClicked = onFilterButtonClicked,
                    episodes = episodes,
                    episodesToShow = episodesToShow,
                    useEpisodeCarousel = useEpisodeCarousel,
                    targetEpisodeIndex = state.targetEpisodeIndex,
                    episodesExpandedState = episodesExpandedState,
                    episodeListDensity = episodeListDensity,
                    isAnyEpisodeSelected = isAnyEpisodeSelected,
                    missingEpisodesCount = missingEpisodesCount,
                    haptic = haptic,
                    episodeSwipeStartAction = episodeSwipeStartAction,
                    episodeSwipeEndAction = episodeSwipeEndAction,
                    onEpisodeClicked = onEpisodeClicked,
                    onEpisodeSelected = onEpisodeSelected,
                    onEpisodeSwipe = onEpisodeSwipe,
                    onDownloadEpisode = onDownloadEpisode,
                    alwaysShowFullEpisodeList = alwaysShowFullEpisodeList,
                    entrySuggestionsEnabled = entrySuggestionsEnabled,
                    entrySuggestionsExpandInline = entrySuggestionsExpandInline,
                    entrySuggestionsInOverflow = entrySuggestionsInOverflow,
                    colors = colors,
                    onSuggestionClick = onSuggestionClick,
                    onOpenSuggestions = onOpenSuggestions,
                    onRetrySuggestions = onRetrySuggestions,
                    isThumbFastScrollingState = isThumbFastScrollingState,
                )
            }

            // Hero content (fixed at bottom of first screen) - fades out on scroll
            val heroThreshold = (screenHeight.value * 0.7f).toInt()
            AnimeAuroraHeroOverlay(
                heroThreshold = heroThreshold,
                useTwoPaneLayout = useTwoPaneLayout,
                isAnyEpisodeSelected = isAnyEpisodeSelected,
                titleScreenStyle = titleScreenStyle,
                scrollOffsetState = scrollOffsetState,
                firstVisibleItemIndexState = firstVisibleItemIndexState,
                anime = anime,
                state = state,
                translation = auroraEntryTranslation,
                hasWatchingProgress = hasWatchingProgress,
                detailsSnapshot = animeDetailsSnapshot,
                selectedGenresState = selectedGenresState,
                titleStaggerState = titleStaggerState,
                firstEpisodeNumberText = firstEpisodeNumberText,
                navigationBarsBottom = navigationBarsBottom,
                onEditNotesClicked = onEditNotesClicked,
                onContinueWatching = onContinueWatching,
                onDubbingClicked = onDubbingClicked,
                selectedDubbing = selectedDubbing,
                onGenreClick = onGenreClick,
                onGenresSearch = onGenresSearch,
                onTitleCopy = onTitleCopy,
            )

            // Floating Play button (shows after Hero Content is hidden)
            val showFab by remember(heroThreshold) {
                derivedStateOf {
                    firstVisibleItemIndexState.value > 0 || scrollOffsetState.value > heroThreshold
                }
            }
            val shouldShowFab = !useTwoPaneLayout && showFab && !isAnyEpisodeSelected
            if (shouldShowTitleFastScrollFloatingActionButton(shouldShowFab, isThumbFastScrolling)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(AuroraZIndex.HERO)
                        .padding(
                            end = 20.dp,
                            bottom = resolveAuroraFabBottomPadding(navigationBarsBottom),
                        ),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    AuroraTitleHeroActionFab(
                        onClick = onContinueWatching,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }

            AnimeAuroraTopBar(
                heroThreshold = heroThreshold,
                state = state,
                titleScreenStyle = titleScreenStyle,
                scrollOffsetState = scrollOffsetState,
                firstVisibleItemIndexState = firstVisibleItemIndexState,
                showAnimeOverlayChrome = showAnimeOverlayChrome,
                isAnyEpisodeSelected = isAnyEpisodeSelected,
                hazeState = hazeState,
                titleStaggerState = titleStaggerState,
                autoJumpToNextLabel = autoJumpToNextLabel,
                globalSearchQuery = globalSearchQuery,
                entrySuggestionsEnabled = entrySuggestionsEnabled,
                entrySuggestionsInOverflow = entrySuggestionsInOverflow,
                navigateUp = navigateUp,
                onFilterButtonClicked = onFilterButtonClicked,
                onDownloadActionClicked = onDownloadActionClicked,
                onRefresh = onRefresh,
                onWebViewClicked = onWebViewClicked,
                onMigrateClicked = onMigrateClicked,
                onShareClicked = onShareClicked,
                onEditNotesClicked = onEditNotesClicked,
                onClickEditInfo = onClickEditInfo,
                onSettingsClicked = onSettingsClicked,
                onDeleteDownloadsClicked = onDeleteDownloadsClicked,
                onDeleteReadEpisodesClicked = onDeleteReadEpisodesClicked,
                onToggleAutoJumpToNext = onToggleAutoJumpToNext,
                onSearch = onSearch,
                onOpenSuggestions = onOpenSuggestions,
            )

            AuroraEpisodeSelectionBottomStack(
                selected = selectedEpisodes,
                onSelectAll = { onAllEpisodeSelected(true) },
                onInvertSelection = onInvertSelection,
                onCancel = { onAllEpisodeSelected(false) },
                onEpisodeClicked = onEpisodeClicked,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiFillermarkClicked = onMultiFillermarkClicked,
                onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
                onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
                onDownloadEpisode = onDownloadEpisode,
                onMultiDeleteClicked = onMultiDeleteClicked,
                fillFraction = if (
                    auroraSelectionControlsPlacement() == AuroraSelectionControlsPlacement.BottomStack &&
                    useTwoPaneLayout
                ) {
                    0.5f
                } else {
                    1f
                },
                alwaysUseExternalPlayer = alwaysUseExternalPlayer,
                modifier = Modifier
                    .align(if (useTwoPaneLayout) Alignment.BottomEnd else Alignment.BottomCenter)
                    .zIndex(AuroraZIndex.SELECTION)
                    .padding(WindowInsets.systemBars.asPaddingValues()),
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(AuroraZIndex.SNACKBAR)
                    .padding(WindowInsets.systemBars.asPaddingValues()),
            )
        }
    }
}

private class AnimeAuroraVirtualSeasons(
    val distinctVirtualSeasons: List<String>,
    val episodeIdToVirtualSeason: Map<Long, String>,
    val selectedVirtualSeasonState: MutableState<String>,
    val filteredEpisodes: List<EpisodeList>,
    val realEpisodeCount: Int,
    val missingEpisodesCount: Int,
)

@Composable
private fun rememberAnimeAuroraVirtualSeasons(
    episodes: List<EpisodeList>,
    showSeasonTabs: Boolean,
): AnimeAuroraVirtualSeasons {
    // Cheap content signature (ids + names + numbers): keeps the regex-heavy season
    // classification below from re-running on download/selection ticks that merely
    // swap the list instance without changing classified fields.
    val seasonsSignature = remember(episodes) {
        var hash = 7
        for (item in episodes) {
            val episode = (item as? EpisodeList.Item)?.episode ?: continue
            hash = hash * 31 + episode.id.hashCode()
            hash = hash * 31 + episode.name.hashCode()
            hash = hash * 31 + episode.episodeNumber.hashCode()
        }
        hash
    }

    val distinctVirtualSeasons = remember(seasonsSignature) {
        val items = episodes.filterIsInstance<EpisodeList.Item>()
        if (items.isEmpty()) {
            emptyList<String>()
        } else {
            val seasonsList = items.map { item ->
                val name = item.episode.name
                val num = item.episode.episodeNumber
                val match = seasonRegex.find(name)
                val explicitSeason = if (match != null) {
                    val sn = match.groupValues[1].toIntOrNull()
                    if (sn != null) "Season $sn" else null
                } else {
                    null
                }

                val isSpecial = num < 0 ||
                    specialKeywordsRegex.containsMatchIn(name) ||
                    !name.any { it.isDigit() } ||
                    (num > 0 && num < 1.0)

                if (explicitSeason != null) {
                    explicitSeason
                } else if (isSpecial) {
                    if (specialKeywordsRegex.containsMatchIn(name)) "Specials" else "Extras"
                } else {
                    "Season 1"
                }
            }.distinct()

            seasonsList.sortedWith(
                Comparator { s1, s2 ->
                    fun getPriority(s: String): Int {
                        return when {
                            s.startsWith("Season", ignoreCase = true) -> 0
                            s.contains("Extra", ignoreCase = true) -> 1
                            s.contains("Special", ignoreCase = true) -> 2
                            else -> 3
                        }
                    }
                    val p1 = getPriority(s1)
                    val p2 = getPriority(s2)
                    if (p1 != p2) {
                        p1.compareTo(p2)
                    } else {
                        val n1 = s1.filter { it.isDigit() }.toIntOrNull() ?: 0
                        val n2 = s2.filter { it.isDigit() }.toIntOrNull() ?: 0
                        if (n1 != n2) {
                            n1.compareTo(n2)
                        } else {
                            s1.compareTo(s2)
                        }
                    }
                },
            )
        }
    }

    val episodeIdToVirtualSeason = remember(seasonsSignature) {
        val items = episodes.filterIsInstance<EpisodeList.Item>()
        if (items.isEmpty()) {
            emptyMap<Long, String>()
        } else {
            items.associate { item ->
                val name = item.episode.name
                val num = item.episode.episodeNumber
                val match = seasonRegex.find(name)
                val explicitSeason = if (match != null) {
                    val sn = match.groupValues[1].toIntOrNull()
                    if (sn != null) "Season $sn" else null
                } else {
                    null
                }

                val isSpecial = num < 0 ||
                    specialKeywordsRegex.containsMatchIn(name) ||
                    !name.any { it.isDigit() } ||
                    (num > 0 && num < 1.0)

                val seasonName = if (explicitSeason != null) {
                    explicitSeason
                } else if (isSpecial) {
                    if (specialKeywordsRegex.containsMatchIn(name)) "Specials" else "Extras"
                } else {
                    "Season 1"
                }
                item.episode.id to seasonName
            }
        }
    }

    val selectedVirtualSeasonState = remember(distinctVirtualSeasons) {
        mutableStateOf(distinctVirtualSeasons.firstOrNull() ?: "")
    }
    var selectedVirtualSeason by selectedVirtualSeasonState

    val filteredEpisodes = remember(
        episodes,
        selectedVirtualSeason,
        episodeIdToVirtualSeason,
        distinctVirtualSeasons,
        showSeasonTabs,
    ) {
        if (!showSeasonTabs || distinctVirtualSeasons.size <= 1) {
            episodes
        } else {
            episodes.filter { item ->
                when (item) {
                    is EpisodeList.Item -> episodeIdToVirtualSeason[item.episode.id] == selectedVirtualSeason
                    is EpisodeList.MissingCount -> true
                }
            }
        }
    }

    val realEpisodeCount = remember(filteredEpisodes) {
        filteredEpisodes.count { it is EpisodeList.Item }
    }
    val missingEpisodesCount = remember(filteredEpisodes) {
        filteredEpisodes.filterIsInstance<EpisodeList.MissingCount>().sumOf { it.count }
    }
    return AnimeAuroraVirtualSeasons(
        distinctVirtualSeasons = distinctVirtualSeasons,
        episodeIdToVirtualSeason = episodeIdToVirtualSeason,
        selectedVirtualSeasonState = selectedVirtualSeasonState,
        filteredEpisodes = filteredEpisodes,
        realEpisodeCount = realEpisodeCount,
        missingEpisodesCount = missingEpisodesCount,
    )
}

@Composable
private fun BoxScope.AnimeAuroraBackgroundLayer(
    anime: Anime,
    titleScreenStyle: TitleScreenStyle,
    scrollOffsetState: State<Int>,
    firstVisibleItemIndexState: State<Int>,
    resolvedCover: ResolvedCover,
    refererUrl: String?,
    sourceHeaders: Map<String, String>?,
    sourceClient: okhttp3.Call.Factory?,
    hazeState: HazeState,
    titleStaggerState: AuroraTitleStaggerState,
    showFinishedStamp: Boolean,
    finishedStampDate: String?,
    colors: AuroraColors,
) {
    if (titleScreenStyle == TitleScreenStyle.POSTER_IMMERSIVE) {
        FullscreenPosterBackground(
            anime = anime,
            scrollOffsetState = scrollOffsetState,
            firstVisibleItemIndexState = firstVisibleItemIndexState,
            resolvedCoverUrl = resolvedCover.coverUrl,
            resolvedCoverUrlFallback = resolvedCover.coverUrlFallback,
            refererUrl = refererUrl,
            sourceHeaders = sourceHeaders,
            sourceClient = sourceClient,
            modifier = Modifier
                .titleScreenPosterEntrance(titleStaggerState)
                .hazeSource(state = hazeState),
        )

        // Keepsake «finished» stamp pinned to the poster corner, clear below the top
        // bar row, fading out together with the poster itself while scrolling.
        if (showFinishedStamp && LocalLayoutDirection.current != LayoutDirection.Rtl) {
            val stampDensity = LocalDensity.current
            val stampAlpha by remember(scrollOffsetState, firstVisibleItemIndexState) {
                derivedStateOf {
                    if (firstVisibleItemIndexState.value > 0) {
                        0f
                    } else {
                        (1f - scrollOffsetState.value / with(stampDensity) { 360.dp.toPx() })
                            .coerceIn(0f, 1f)
                    }
                }
            }
            FinaleStamp(
                label = stringResource(MR.strings.reader_finale_stamp_label),
                date = finishedStampDate,
                accent = colors.accent,
                size = 88.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(AuroraZIndex.SNACKBAR + 1f)
                    .statusBarsPadding()
                    .padding(top = 60.dp, end = 14.dp)
                    .graphicsLayer { alpha = stampAlpha },
            )
        }
    } else {
        AuroraBackground(
            modifier = Modifier
                .fillMaxSize()
                .titleScreenPosterEntrance(titleStaggerState)
                .hazeSource(state = hazeState),
        ) {}
    }
}

@Composable
private fun AnimeAuroraTwoPaneStartContent(
    anime: Anime,
    state: AnimeScreenModel.State.Success,
    translation: AuroraEntryTranslationState,
    hasWatchingProgress: Boolean,
    detailsSnapshot: AnimeDetailsSnapshot,
    titleScreenStyle: TitleScreenStyle,
    showOriginalTitle: Boolean,
    resolvedCover: ResolvedCover,
    refererUrl: String?,
    sourceHeaders: Map<String, String>?,
    sourceClient: okhttp3.Call.Factory?,
    showFinishedStamp: Boolean,
    finishedStampDate: String?,
    titleStaggerState: AuroraTitleStaggerState,
    firstEpisodeNumberText: String?,
    selectedGenresState: MutableState<Set<String>>,
    descriptionExpandedState: MutableState<Boolean>,
    genresExpandedState: MutableState<Boolean>,
    entrySuggestionsEnabled: Boolean,
    entrySuggestionsExpandInline: Boolean,
    entrySuggestionsInOverflow: Boolean,
    colors: AuroraColors,
    animeHeroActionLabel: String?,
    onEditNotesClicked: (() -> Unit)?,
    onContinueWatching: () -> Unit,
    onDubbingClicked: (() -> Unit)?,
    selectedDubbing: String?,
    onGenreClick: ((String) -> Unit)?,
    onGenresSearch: ((List<String>) -> Unit)?,
    onTitleCopy: () -> Unit,
    onAddToLibraryClicked: () -> Unit,
    onEditCategoryClicked: (() -> Unit)?,
    onWebViewClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onShareClicked: (() -> Unit)?,
    onTagSearch: (String) -> Unit,
    onSourceClick: (() -> Unit)?,
    onCoverClicked: () -> Unit,
    onSuggestionClick: (eu.kanade.tachiyomi.data.suggestions.SuggestionItem) -> Unit,
    onOpenSuggestions: () -> Unit,
    onRetrySuggestions: () -> Unit,
) {
    var selectedGenres by selectedGenresState
    var descriptionExpanded by descriptionExpandedState
    var genresExpanded by genresExpandedState
    val topContentPadding = 96.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 6.dp, top = topContentPadding, bottom = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .auroraCenteredMaxWidth(420)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                    alignment = Alignment.TopStart,
                ),
        ) {
            if (titleScreenStyle == TitleScreenStyle.POSTER_IMMERSIVE) {
                AnimeDetailHero(
                    anime = anime,
                    translation = translation,
                    hasWatchingProgress = hasWatchingProgress,
                    ratingText = detailsSnapshot.ratingText,
                    episodeCount = state.episodes.size,
                    statusText = detailsSnapshot.statusText,
                    note = anime.notes,
                    onEditNotesClicked = onEditNotesClicked,
                    onContinueWatching = onContinueWatching,
                    onDubbingClicked = onDubbingClicked,
                    selectedDubbing = selectedDubbing,
                    onGenreClick = onGenreClick,
                    onGenreLongClick = { genre ->
                        selectedGenres = if (genre in selectedGenres) {
                            selectedGenres - genre
                        } else {
                            selectedGenres + genre
                        }
                    },
                    selectedGenres = selectedGenres,
                    onSearchSelected = {
                        onGenresSearch?.invoke(selectedGenres.toList())
                        selectedGenres = emptySet()
                    },
                    onClearSelected = { selectedGenres = emptySet() },
                    onCopyTitle = onTitleCopy,
                    titleStaggerState = titleStaggerState,
                    firstEpisodeNumberText = firstEpisodeNumberText,
                    modifier = Modifier,
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimeDetailLibraryPills(
                    anime = anime,
                    trackingCount = state.trackingCount,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onAddToLibraryLongClicked = onEditCategoryClicked,
                    onTrackingClicked = onTrackingClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 2),
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimeStatsCard(
                    anime = anime,
                    snapshot = detailsSnapshot,
                    showAuthor = false,
                    onSourceClick = onSourceClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 2),
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimeInfoCard(
                    anime = anime,
                    translation = translation,
                    onTagSearch = onTagSearch,
                    descriptionExpanded = descriptionExpanded,
                    genresExpanded = genresExpanded,
                    onToggleDescription = {
                        descriptionExpanded = !descriptionExpanded
                    },
                    onToggleGenres = { genresExpanded = !genresExpanded },
                    selectedGenres = selectedGenres,
                    onGenreClick = onGenreClick,
                    onGenreLongClick = { genre ->
                        selectedGenres = if (genre in selectedGenres) {
                            selectedGenres - genre
                        } else {
                            selectedGenres + genre
                        }
                    },
                    onSearchSelected = {
                        onGenresSearch?.invoke(selectedGenres.toList())
                        selectedGenres = emptySet()
                    },
                    onClearSelected = { selectedGenres = emptySet() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 3),
                )
                Spacer(modifier = Modifier.height(12.dp))
                AnimeActionCard(
                    anime = anime,
                    trackingCount = state.trackingCount,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onAddToLibraryLongClicked = onEditCategoryClicked,
                    onWebViewClicked = onWebViewClicked,
                    onTrackingClicked = onTrackingClicked,
                    onShareClicked = onShareClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 4),
                )
            } else {
                AnimeGlassHeroCard(
                    anime = anime,
                    translation = translation,
                    hasWatchingProgress = hasWatchingProgress,
                    note = anime.notes,
                    onEditNotesClicked = onEditNotesClicked,
                    onContinueWatching = onContinueWatching,
                    onCoverClicked = onCoverClicked,
                    onCopyTitle = onTitleCopy,
                    actionLabel = animeHeroActionLabel,
                    showOriginalTitle = showOriginalTitle,
                    episodeCount = state.episodes.size,
                    resolvedCoverUrl = resolvedCover.coverUrl,
                    resolvedCoverUrlFallback = resolvedCover.coverUrlFallback,
                    refererUrl = refererUrl,
                    sourceHeaders = sourceHeaders,
                    sourceClient = sourceClient,
                    showFinishedStamp = showFinishedStamp,
                    finishedStampDate = finishedStampDate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 1),
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimeActionCard(
                    anime = anime,
                    trackingCount = state.trackingCount,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onAddToLibraryLongClicked = onEditCategoryClicked,
                    onWebViewClicked = onWebViewClicked,
                    onTrackingClicked = onTrackingClicked,
                    onShareClicked = onShareClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 4),
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimeInfoCard(
                    anime = anime,
                    translation = translation,
                    onTagSearch = onTagSearch,
                    descriptionExpanded = descriptionExpanded,
                    genresExpanded = genresExpanded,
                    onToggleDescription = {
                        descriptionExpanded = !descriptionExpanded
                    },
                    onToggleGenres = { genresExpanded = !genresExpanded },
                    selectedGenres = selectedGenres,
                    onGenreClick = onGenreClick,
                    onGenreLongClick = { genre ->
                        selectedGenres = if (genre in selectedGenres) {
                            selectedGenres - genre
                        } else {
                            selectedGenres + genre
                        }
                    },
                    onSearchSelected = {
                        onGenresSearch?.invoke(selectedGenres.toList())
                        selectedGenres = emptySet()
                    },
                    onClearSelected = { selectedGenres = emptySet() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 3),
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimeStatsCard(
                    anime = anime,
                    snapshot = detailsSnapshot,
                    showAuthor = false,
                    onSourceClick = onSourceClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 2),
                )
            }
            if (entrySuggestionsEnabled) {
                if (entrySuggestionsExpandInline) {
                    Spacer(modifier = Modifier.height(16.dp))
                    eu.kanade.presentation.entries.components.aurora.AuroraSuggestionsRow(
                        state = state.suggestions,
                        onSuggestionClick = onSuggestionClick,
                        onOpenSuggestions = onOpenSuggestions,
                        onRetryClick = onRetrySuggestions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (!entrySuggestionsInOverflow) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = if (colors.isDark) {
                                        listOf(
                                            Color.White.copy(alpha = 0.12f),
                                            Color.White.copy(alpha = 0.08f),
                                        )
                                    } else {
                                        listOf(
                                            colors.accent.copy(alpha = 0.15f),
                                            colors.accent.copy(alpha = 0.10f),
                                        )
                                    },
                                ),
                                shape = RoundedCornerShape(12.dp),
                            )
                            .auroraSpringClick(onClick = onOpenSuggestions)
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(MR.strings.suggestions_similar_titles),
                            color = if (colors.isDark) Color.White else colors.accent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AnimeAuroraTwoPaneEndContent(
    lazyListState: LazyListState,
    anime: Anime,
    state: AnimeScreenModel.State.Success,
    episodes: List<EpisodeList>,
    seasons: List<AnimeSeasonItem>,
    seasonSwitcherItems: List<AnimeSeasonSwitcherItem>,
    distinctVirtualSeasons: List<String>,
    selectedVirtualSeasonState: MutableState<String>,
    filteredEpisodes: List<EpisodeList>,
    episodesToShow: List<EpisodeList>,
    episodesExpandedState: MutableState<Boolean>,
    isThumbFastScrollingState: MutableState<Boolean>,
    episodeListDensity: EpisodeListDensity,
    showSeasonTabs: Boolean,
    hasFilters: Boolean,
    titleStaggerState: AuroraTitleStaggerState,
    screenHeight: Dp,
    isAnyEpisodeSelected: Boolean,
    realEpisodeCount: Int,
    missingEpisodesCount: Int,
    alwaysShowFullEpisodeList: Boolean,
    showNextEpisodeAirTime: Boolean,
    haptic: HapticFeedback,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    colors: AuroraColors,
    onSeasonClicked: ((SeasonAnime) -> Unit)?,
    onContinueWatchingClicked: ((SeasonAnime) -> Unit)?,
    onToggleSort: (() -> Unit)?,
    onToggleUnseenFilter: (() -> Unit)?,
    onFilterButtonClicked: () -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
) {
    var episodesExpanded by episodesExpandedState
    var isThumbFastScrolling by isThumbFastScrollingState
    var selectedVirtualSeason by selectedVirtualSeasonState

    val topContentPadding = 96.dp
    val paneDensity = LocalDensity.current
    val paneTopPaddingPx = with(paneDensity) { topContentPadding.roundToPx() }
    val paneFastScrollBlockStartIndex = resolveAnimeAuroraFastScrollBlockStartIndex(
        useTwoPaneLayout = true,
    )
    val paneFastScrollSpec by remember(paneTopPaddingPx, paneFastScrollBlockStartIndex) {
        derivedStateOf {
            resolveTitleListFastScrollSpec(
                baseTopPaddingPx = paneTopPaddingPx,
                firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
                blockStartIndex = paneFastScrollBlockStartIndex,
                blockStartOffsetPx = lazyListState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == paneFastScrollBlockStartIndex }
                    ?.offset
                    ?.plus(with(paneDensity) { ANIME_AURORA_FAST_SCROLL_ITEM_TOP_INSET.roundToPx() }),
            )
        }
    }
    VerticalFastScroller(
        listState = lazyListState,
        onThumbDragStarted = {
            val shouldExpand =
                shouldAutoExpandAuroraEpisodesListForFastScroll(
                    episodesExpanded,
                    episodes.size,
                )
            if (shouldExpand) {
                episodesExpanded = true
            }
        },
        onThumbDragStateChanged = { isThumbFastScrolling = it },
        thumbAllowed = { paneFastScrollSpec.thumbAllowed },
        topContentPadding = with(paneDensity) { paneFastScrollSpec.topPaddingPx.toDp() },
        endContentPadding = 12.dp,
        modifier = Modifier.zIndex(AuroraZIndex.BASE),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = topContentPadding, bottom = 100.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 12.dp),
        ) {
            item(key = "season-rail") {
                if (seasons.size > 1) {
                    AnimeSeasonRailAurora(
                        anime = anime,
                        seasons = seasons,
                        onSeasonClicked = onSeasonClicked,
                        onContinueWatchingClicked = onContinueWatchingClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            item(key = "episodes-header") {
                EpisodesHeader(
                    itemCount = if (state.anime.fetchType == FetchType.Seasons) {
                        seasons.size
                    } else {
                        filteredEpisodes.size
                    },
                    fetchType = state.anime.fetchType,
                    isDescending = anime.sortDescending(),
                    unseenFilter = anime.unseenFilter,
                    filterActive = hasFilters,
                    onClickSort = onToggleSort ?: onFilterButtonClicked,
                    onLongClickSort = onFilterButtonClicked,
                    onClickFilter = onToggleUnseenFilter ?: onFilterButtonClicked,
                    onLongClickFilter = onFilterButtonClicked,
                    modifier = Modifier.titleScreenStagger(titleStaggerState, 5),
                )
            }

            item(key = "season-switcher") {
                if (showSeasonTabs && seasonSwitcherItems.size > 1) {
                    AnimeSeasonSwitcherAurora(
                        items = seasonSwitcherItems,
                        onSeasonClicked = { onSeasonClicked?.invoke(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            item(key = "virtual-season-switcher") {
                if (showSeasonTabs && distinctVirtualSeasons.size > 1) {
                    VirtualSeasonSwitcherAurora(
                        seasons = distinctVirtualSeasons,
                        selectedSeason = selectedVirtualSeason,
                        onSeasonClicked = { selectedVirtualSeason = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            if (state.anime.fetchType == FetchType.Episodes && state.airingTime > 0L) {
                item(key = "airing-time") {
                    // Handles the second by second countdown; keyed on the
                    // upstream deadline so refreshed airing values restart cleanly.
                    var timer by remember { mutableLongStateOf(state.airingTime) }
                    LaunchedEffect(state.airingTime) {
                        timer = state.airingTime
                        while (isActive && timer > 0L) {
                            delay(1000L)
                            timer -= 1000L
                        }
                    }
                    if (timer > 0L &&
                        showNextEpisodeAirTime &&
                        state.anime.displayStatus.toInt() != SAnime.COMPLETED
                    ) {
                        NextEpisodeAiringListItem(
                            title = stringResource(
                                AYMR.strings.display_mode_episode,
                                formatEpisodeNumber(state.airingEpisodeNumber),
                            ),
                            date = formatTime(state.airingTime, useDayFormat = true),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                    }
                }
            }

            if (state.anime.fetchType == FetchType.Seasons) {
                items(
                    items = seasons,
                    key = { season -> season.seasonAnime.anime.id },
                    contentType = { "season" },
                ) { item ->
                    AnimeSeasonListItem(
                        anime = anime,
                        item = item,
                        containerHeight = screenHeight.value.toInt(),
                        onSeasonClicked = { onSeasonClicked?.invoke(it) },
                        onClickContinueWatching = onContinueWatchingClicked,
                        listItemModifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            } else {
                if (missingEpisodesCount > 0) {
                    item(key = "missing-episodes-warning") {
                        MissingItemCountListItem(count = missingEpisodesCount)
                    }
                }

                if (realEpisodeCount == 0) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(MR.strings.no_chapters_error),
                                color = colors.textPrimary.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                            )
                        }
                    }
                }

                items(
                    items = episodesToShow,
                    key = { (it as? EpisodeList.Item)?.episode?.id ?: it.hashCode() },
                    contentType = {
                        when (it) {
                            is EpisodeList.Item -> "episode"
                            is EpisodeList.MissingCount -> "missing-count"
                        }
                    },
                ) { item ->
                    when (item) {
                        is EpisodeList.Item -> {
                            AnimeEpisodeCardCompact(
                                anime = anime,
                                item = item,
                                selected = item.selected,
                                isNew = item.episode.id in state.newEpisodeIds,
                                isAnyEpisodeSelected = isAnyEpisodeSelected,
                                episodeSwipeStartAction = episodeSwipeStartAction,
                                episodeSwipeEndAction = episodeSwipeEndAction,
                                showPreviews = state.showPreviews,
                                showSummaries = state.showSummaries,
                                density = episodeListDensity,
                                onClick = {
                                    when (
                                        resolveAuroraEpisodeClickAction(
                                            isEpisodeSelected = item.selected,
                                            isAnyEpisodeSelected = isAnyEpisodeSelected,
                                        )
                                    ) {
                                        AuroraEpisodeClickAction.OpenEpisode -> {
                                            onEpisodeClicked(item.episode, false)
                                        }
                                        AuroraEpisodeClickAction.SelectEpisode -> {
                                            onEpisodeSelected(item, true, true, false)
                                            if (shouldAutoExpandAuroraEpisodesList(
                                                    episodesExpanded,
                                                    episodes.size,
                                                )
                                            ) {
                                                episodesExpanded = true
                                            }
                                        }
                                        AuroraEpisodeClickAction.UnselectEpisode -> {
                                            onEpisodeSelected(item, false, true, false)
                                        }
                                    }
                                },
                                onLongClick = {
                                    onEpisodeSelected(item, !item.selected, true, true)
                                    val shouldExpand = shouldAutoExpandAuroraEpisodesList(
                                        episodesExpanded,
                                        episodes.size,
                                    )
                                    if (!item.selected && shouldExpand) {
                                        episodesExpanded = true
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onEpisodeSwipe = { action -> onEpisodeSwipe(item, action) },
                                onDownloadEpisode = onDownloadEpisode,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        is EpisodeList.MissingCount -> {
                            MissingItemCountListItem(count = item.count)
                        }
                    }
                }
            }

            if (realEpisodeCount > 5 && !alwaysShowFullEpisodeList) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AuroraEpisodeListToggleButton(
                            text = if (episodesExpanded) {
                                stringResource(AYMR.strings.action_show_less)
                            } else {
                                stringResource(
                                    AYMR.strings.action_show_all_episodes,
                                    realEpisodeCount,
                                )
                            },
                            onClick = { episodesExpanded = !episodesExpanded },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeAuroraMobileContent(
    lazyListState: LazyListState,
    anime: Anime,
    state: AnimeScreenModel.State.Success,
    titleScreenStyle: TitleScreenStyle,
    contentMaxWidthDp: Int?,
    screenHeight: Dp,
    translation: AuroraEntryTranslationState,
    detailsSnapshot: AnimeDetailsSnapshot,
    synopsisText: String?,
    hasWatchingProgress: Boolean,
    showOriginalTitle: Boolean,
    resolvedCover: ResolvedCover,
    refererUrl: String?,
    sourceHeaders: Map<String, String>?,
    sourceClient: okhttp3.Call.Factory?,
    showFinishedStamp: Boolean,
    finishedStampDate: String?,
    titleStaggerState: AuroraTitleStaggerState,
    realEpisodeCount: Int,
    animeHeroActionLabel: String?,
    selectedGenresState: MutableState<Set<String>>,
    descriptionExpandedState: MutableState<Boolean>,
    genresExpandedState: MutableState<Boolean>,
    seasons: List<AnimeSeasonItem>,
    seasonSwitcherItems: List<AnimeSeasonSwitcherItem>,
    distinctVirtualSeasons: List<String>,
    selectedVirtualSeasonState: MutableState<String>,
    filteredEpisodes: List<EpisodeList>,
    showSeasonTabs: Boolean,
    hasFilters: Boolean,
    onAddToLibraryClicked: () -> Unit,
    onEditCategoryClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onEditNotesClicked: (() -> Unit)?,
    onContinueWatching: () -> Unit,
    onCoverClicked: () -> Unit,
    onTitleCopy: () -> Unit,
    onTagSearch: (String) -> Unit,
    onGenreClick: ((String) -> Unit)?,
    onGenresSearch: ((List<String>) -> Unit)?,
    onWebViewClicked: (() -> Unit)?,
    onShareClicked: (() -> Unit)?,
    onSourceClick: (() -> Unit)?,
    onSeasonClicked: ((SeasonAnime) -> Unit)?,
    onContinueWatchingClicked: ((SeasonAnime) -> Unit)?,
    onToggleSort: (() -> Unit)?,
    onToggleUnseenFilter: (() -> Unit)?,
    onFilterButtonClicked: () -> Unit,
    episodes: List<EpisodeList>,
    episodesToShow: List<EpisodeList>,
    useEpisodeCarousel: Boolean,
    targetEpisodeIndex: Int?,
    episodesExpandedState: MutableState<Boolean>,
    episodeListDensity: EpisodeListDensity,
    isAnyEpisodeSelected: Boolean,
    missingEpisodesCount: Int,
    haptic: HapticFeedback,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    alwaysShowFullEpisodeList: Boolean,
    entrySuggestionsEnabled: Boolean,
    entrySuggestionsExpandInline: Boolean,
    entrySuggestionsInOverflow: Boolean,
    colors: AuroraColors,
    onSuggestionClick: (eu.kanade.tachiyomi.data.suggestions.SuggestionItem) -> Unit,
    onOpenSuggestions: () -> Unit,
    onRetrySuggestions: () -> Unit,
    isThumbFastScrollingState: MutableState<Boolean>,
) {
    var episodesExpanded by episodesExpandedState
    var isThumbFastScrolling by isThumbFastScrollingState

    val density = LocalDensity.current
    val fastScrollBaseTopPadding =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp
    val fastScrollBaseTopPaddingPx = with(density) { fastScrollBaseTopPadding.roundToPx() }
    val fastScrollBlockStartIndex = resolveAnimeAuroraFastScrollBlockStartIndex(
        useTwoPaneLayout = false,
    )
    val fastScrollSpec by remember(fastScrollBaseTopPaddingPx, fastScrollBlockStartIndex) {
        derivedStateOf {
            resolveTitleListFastScrollSpec(
                baseTopPaddingPx = fastScrollBaseTopPaddingPx,
                firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
                blockStartIndex = fastScrollBlockStartIndex,
                blockStartOffsetPx = lazyListState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == fastScrollBlockStartIndex }
                    ?.offset
                    ?.plus(with(density) { ANIME_AURORA_FAST_SCROLL_ITEM_TOP_INSET.roundToPx() }),
            )
        }
    }
    VerticalFastScroller(
        listState = lazyListState,
        onThumbDragStarted = {
            if (shouldAutoExpandAuroraEpisodesListForFastScroll(episodesExpanded, episodes.size)) {
                episodesExpanded = true
            }
        },
        onThumbDragStateChanged = { isThumbFastScrolling = it },
        thumbAllowed = { fastScrollSpec.thumbAllowed },
        topContentPadding = with(density) { fastScrollSpec.topPaddingPx.toDp() },
        bottomContentPadding = 100.dp,
        modifier = Modifier.zIndex(AuroraZIndex.BASE),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 100.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            AnimeAuroraMobileHeroItems(
                anime = anime,
                state = state,
                titleScreenStyle = titleScreenStyle,
                contentMaxWidthDp = contentMaxWidthDp,
                screenHeight = screenHeight,
                translation = translation,
                detailsSnapshot = detailsSnapshot,
                synopsisText = synopsisText,
                hasWatchingProgress = hasWatchingProgress,
                showOriginalTitle = showOriginalTitle,
                resolvedCover = resolvedCover,
                refererUrl = refererUrl,
                sourceHeaders = sourceHeaders,
                sourceClient = sourceClient,
                showFinishedStamp = showFinishedStamp,
                finishedStampDate = finishedStampDate,
                titleStaggerState = titleStaggerState,
                realEpisodeCount = realEpisodeCount,
                animeHeroActionLabel = animeHeroActionLabel,
                selectedGenresState = selectedGenresState,
                descriptionExpandedState = descriptionExpandedState,
                genresExpandedState = genresExpandedState,
                seasons = seasons,
                seasonSwitcherItems = seasonSwitcherItems,
                distinctVirtualSeasons = distinctVirtualSeasons,
                selectedVirtualSeasonState = selectedVirtualSeasonState,
                filteredEpisodes = filteredEpisodes,
                showSeasonTabs = showSeasonTabs,
                hasFilters = hasFilters,
                onAddToLibraryClicked = onAddToLibraryClicked,
                onEditCategoryClicked = onEditCategoryClicked,
                onTrackingClicked = onTrackingClicked,
                onEditNotesClicked = onEditNotesClicked,
                onContinueWatching = onContinueWatching,
                onCoverClicked = onCoverClicked,
                onTitleCopy = onTitleCopy,
                onTagSearch = onTagSearch,
                onGenreClick = onGenreClick,
                onGenresSearch = onGenresSearch,
                onWebViewClicked = onWebViewClicked,
                onShareClicked = onShareClicked,
                onSourceClick = onSourceClick,
                onSeasonClicked = onSeasonClicked,
                onContinueWatchingClicked = onContinueWatchingClicked,
                onToggleSort = onToggleSort,
                onToggleUnseenFilter = onToggleUnseenFilter,
                onFilterButtonClicked = onFilterButtonClicked,
            )

            AnimeAuroraMobileEpisodeItems(
                anime = anime,
                state = state,
                episodes = episodes,
                episodesToShow = episodesToShow,
                seasons = seasons,
                useEpisodeCarousel = useEpisodeCarousel,
                targetEpisodeIndex = targetEpisodeIndex,
                episodesExpandedState = episodesExpandedState,
                episodeListDensity = episodeListDensity,
                isAnyEpisodeSelected = isAnyEpisodeSelected,
                realEpisodeCount = realEpisodeCount,
                missingEpisodesCount = missingEpisodesCount,
                screenHeight = screenHeight,
                contentMaxWidthDp = contentMaxWidthDp,
                haptic = haptic,
                episodeSwipeStartAction = episodeSwipeStartAction,
                episodeSwipeEndAction = episodeSwipeEndAction,
                onSeasonClicked = onSeasonClicked,
                onContinueWatchingClicked = onContinueWatchingClicked,
                onEpisodeClicked = onEpisodeClicked,
                onEpisodeSelected = onEpisodeSelected,
                onEpisodeSwipe = onEpisodeSwipe,
                onDownloadEpisode = onDownloadEpisode,
                alwaysShowFullEpisodeList = alwaysShowFullEpisodeList,
                entrySuggestionsEnabled = entrySuggestionsEnabled,
                entrySuggestionsExpandInline = entrySuggestionsExpandInline,
                entrySuggestionsInOverflow = entrySuggestionsInOverflow,
                colors = colors,
                onSuggestionClick = onSuggestionClick,
                onOpenSuggestions = onOpenSuggestions,
                onRetrySuggestions = onRetrySuggestions,
            )
        }
    }
}

private fun LazyListScope.AnimeAuroraMobileHeroItems(
    anime: Anime,
    state: AnimeScreenModel.State.Success,
    titleScreenStyle: TitleScreenStyle,
    contentMaxWidthDp: Int?,
    screenHeight: Dp,
    translation: AuroraEntryTranslationState,
    detailsSnapshot: AnimeDetailsSnapshot,
    synopsisText: String?,
    hasWatchingProgress: Boolean,
    showOriginalTitle: Boolean,
    resolvedCover: ResolvedCover,
    refererUrl: String?,
    sourceHeaders: Map<String, String>?,
    sourceClient: okhttp3.Call.Factory?,
    showFinishedStamp: Boolean,
    finishedStampDate: String?,
    titleStaggerState: AuroraTitleStaggerState,
    realEpisodeCount: Int,
    animeHeroActionLabel: String?,
    selectedGenresState: MutableState<Set<String>>,
    descriptionExpandedState: MutableState<Boolean>,
    genresExpandedState: MutableState<Boolean>,
    seasons: List<AnimeSeasonItem>,
    seasonSwitcherItems: List<AnimeSeasonSwitcherItem>,
    distinctVirtualSeasons: List<String>,
    selectedVirtualSeasonState: MutableState<String>,
    filteredEpisodes: List<EpisodeList>,
    showSeasonTabs: Boolean,
    hasFilters: Boolean,
    onAddToLibraryClicked: () -> Unit,
    onEditCategoryClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onEditNotesClicked: (() -> Unit)?,
    onContinueWatching: () -> Unit,
    onCoverClicked: () -> Unit,
    onTitleCopy: () -> Unit,
    onTagSearch: (String) -> Unit,
    onGenreClick: ((String) -> Unit)?,
    onGenresSearch: ((List<String>) -> Unit)?,
    onWebViewClicked: (() -> Unit)?,
    onShareClicked: (() -> Unit)?,
    onSourceClick: (() -> Unit)?,
    onSeasonClicked: ((SeasonAnime) -> Unit)?,
    onContinueWatchingClicked: ((SeasonAnime) -> Unit)?,
    onToggleSort: (() -> Unit)?,
    onToggleUnseenFilter: (() -> Unit)?,
    onFilterButtonClicked: () -> Unit,
) {
    var selectedGenres by selectedGenresState
    var descriptionExpanded by descriptionExpandedState
    var genresExpanded by genresExpandedState
    var selectedVirtualSeason by selectedVirtualSeasonState

    if (titleScreenStyle == TitleScreenStyle.POSTER_IMMERSIVE) {
        // Spacer for poster/hero area
        item {
            Spacer(modifier = Modifier.height(screenHeight))
        }

        // Definitive detail design: scrolled content starts with the
        // title + episode count, the library/tracking pills and the
        // 3-line expandable synopsis. The old stats/info/action glass
        // cards leave this branch: their actions now live on the pills
        // (library, tracking) and in the top ⋮ menu (share, webview),
        // and the hero already shows rating, episode count and status.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                        alignment = Alignment.TopStart,
                    ),
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                AnimeDetailScrolledTitle(
                    title = translation.title,
                    episodeCountText = pluralStringResource(
                        AYMR.plurals.anime_num_episodes,
                        count = realEpisodeCount,
                        realEpisodeCount,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 2),
                )
                Spacer(modifier = Modifier.height(16.dp))
                AnimeDetailLibraryPills(
                    anime = anime,
                    trackingCount = state.trackingCount,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onAddToLibraryLongClicked = onEditCategoryClicked,
                    onTrackingClicked = onTrackingClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .titleScreenStagger(titleStaggerState, 3),
                )
                Spacer(modifier = Modifier.height(12.dp))
                AnimeDetailSynopsisCard(
                    description = synopsisText,
                    expanded = descriptionExpanded,
                    onToggle = { descriptionExpanded = !descriptionExpanded },
                    // v7.9.6: proveedor al final de la sinopsis, igual que en manga.
                    sourceTitle = detailsSnapshot.sourceTitle,
                    translationText = detailsSnapshot.translationText,
                    onSourceClick = onSourceClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .titleScreenStagger(titleStaggerState, 4),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    } else {
        item {
            Spacer(
                modifier = Modifier.height(
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 76.dp,
                ),
            )
            AnimeGlassHeroCard(
                anime = anime,
                translation = translation,
                hasWatchingProgress = hasWatchingProgress,
                note = anime.notes,
                onEditNotesClicked = onEditNotesClicked,
                onContinueWatching = onContinueWatching,
                onCoverClicked = onCoverClicked,
                onCopyTitle = onTitleCopy,
                actionLabel = animeHeroActionLabel,
                showOriginalTitle = showOriginalTitle,
                episodeCount = state.episodes.size,
                resolvedCoverUrl = resolvedCover.coverUrl,
                resolvedCoverUrlFallback = resolvedCover.coverUrlFallback,
                refererUrl = refererUrl,
                sourceHeaders = sourceHeaders,
                sourceClient = sourceClient,
                showFinishedStamp = showFinishedStamp,
                finishedStampDate = finishedStampDate,
                modifier = Modifier
                    .fillMaxWidth()
                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                    .titleScreenStagger(titleStaggerState, 1),
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                        alignment = Alignment.TopStart,
                    ),
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                AnimeActionCard(
                    anime = anime,
                    trackingCount = state.trackingCount,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onAddToLibraryLongClicked = onEditCategoryClicked,
                    onWebViewClicked = onWebViewClicked,
                    onTrackingClicked = onTrackingClicked,
                    onShareClicked = onShareClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 4),
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimeInfoCard(
                    anime = anime,
                    translation = translation,
                    onTagSearch = onTagSearch,
                    descriptionExpanded = descriptionExpanded,
                    genresExpanded = genresExpanded,
                    onToggleDescription = {
                        descriptionExpanded = !descriptionExpanded
                    },
                    onToggleGenres = {
                        genresExpanded = !genresExpanded
                    },
                    selectedGenres = selectedGenres,
                    onGenreClick = onGenreClick,
                    onGenreLongClick = { genre ->
                        selectedGenres = if (genre in selectedGenres) {
                            selectedGenres - genre
                        } else {
                            selectedGenres + genre
                        }
                    },
                    onSearchSelected = {
                        onGenresSearch?.invoke(selectedGenres.toList())
                        selectedGenres = emptySet()
                    },
                    onClearSelected = { selectedGenres = emptySet() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 3),
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimeStatsCard(
                    anime = anime,
                    snapshot = detailsSnapshot,
                    showAuthor = false,
                    onSourceClick = onSourceClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 2),
                )
            }
        }
    }
    item(
        key = "anime-aurora-season-rail",
        contentType = "anime-aurora-season-rail",
    ) {
        if (seasons.size > 1) {
            AnimeSeasonRailAurora(
                anime = anime,
                seasons = seasons,
                onSeasonClicked = onSeasonClicked,
                onContinueWatchingClicked = onContinueWatchingClicked,
                modifier = Modifier
                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }

    item(
        key = ANIME_AURORA_EPISODES_HEADER_KEY,
        contentType = ANIME_AURORA_EPISODES_HEADER_KEY,
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        EpisodesHeader(
            itemCount = if (state.anime.fetchType == FetchType.Seasons) {
                seasons.size
            } else {
                filteredEpisodes.size
            },
            fetchType = state.anime.fetchType,
            isDescending = anime.sortDescending(),
            unseenFilter = anime.unseenFilter,
            filterActive = hasFilters,
            onClickSort = onToggleSort ?: onFilterButtonClicked,
            onLongClickSort = onFilterButtonClicked,
            onClickFilter = onToggleUnseenFilter ?: onFilterButtonClicked,
            onLongClickFilter = onFilterButtonClicked,
            modifier = Modifier
                .auroraCenteredMaxWidth(contentMaxWidthDp)
                .titleScreenStagger(titleStaggerState, 5),
        )
    }

    item(
        key = "anime-aurora-seasons-switcher",
        contentType = "anime-aurora-seasons-switcher",
    ) {
        if (showSeasonTabs && seasonSwitcherItems.size > 1) {
            AnimeSeasonSwitcherAurora(
                items = seasonSwitcherItems,
                onSeasonClicked = { onSeasonClicked?.invoke(it) },
                modifier = Modifier
                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }

    item(
        key = "anime-aurora-virtual-seasons-switcher",
        contentType = "anime-aurora-virtual-seasons-switcher",
    ) {
        if (showSeasonTabs && distinctVirtualSeasons.size > 1) {
            VirtualSeasonSwitcherAurora(
                seasons = distinctVirtualSeasons,
                selectedSeason = selectedVirtualSeason,
                onSeasonClicked = { selectedVirtualSeason = it },
                modifier = Modifier
                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

private fun LazyListScope.AnimeAuroraMobileEpisodeItems(
    anime: Anime,
    state: AnimeScreenModel.State.Success,
    episodes: List<EpisodeList>,
    episodesToShow: List<EpisodeList>,
    seasons: List<AnimeSeasonItem>,
    useEpisodeCarousel: Boolean,
    targetEpisodeIndex: Int?,
    episodesExpandedState: MutableState<Boolean>,
    episodeListDensity: EpisodeListDensity,
    isAnyEpisodeSelected: Boolean,
    realEpisodeCount: Int,
    missingEpisodesCount: Int,
    screenHeight: Dp,
    contentMaxWidthDp: Int?,
    haptic: HapticFeedback,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    onSeasonClicked: ((SeasonAnime) -> Unit)?,
    onContinueWatchingClicked: ((SeasonAnime) -> Unit)?,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    alwaysShowFullEpisodeList: Boolean,
    entrySuggestionsEnabled: Boolean,
    entrySuggestionsExpandInline: Boolean,
    entrySuggestionsInOverflow: Boolean,
    colors: AuroraColors,
    onSuggestionClick: (eu.kanade.tachiyomi.data.suggestions.SuggestionItem) -> Unit,
    onOpenSuggestions: () -> Unit,
    onRetrySuggestions: () -> Unit,
) {
    var episodesExpanded by episodesExpandedState

    if (state.anime.fetchType == FetchType.Seasons) {
        items(
            items = seasons,
            key = { season -> season.seasonAnime.anime.id },
            contentType = { "season" },
        ) { item ->
            AnimeSeasonListItem(
                anime = anime,
                item = item,
                containerHeight = screenHeight.value.toInt(),
                onSeasonClicked = { onSeasonClicked?.invoke(it) },
                onClickContinueWatching = onContinueWatchingClicked,
                listItemModifier = Modifier
                    .fillMaxWidth()
                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    } else {
        if (missingEpisodesCount > 0) {
            item(key = "missing-episodes-warning") {
                MissingItemCountListItem(count = missingEpisodesCount)
            }
        }

        // Empty state for episodes
        if (realEpisodeCount == 0) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .auroraCenteredMaxWidth(contentMaxWidthDp)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(MR.strings.no_chapters_error),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // Episodes: horizontal carousel on the mobile immersive screen
        // (v4 definitive design); everywhere else keeps the compact list
        // rows. One LazyColumn item holds the whole LazyRow: stable keys +
        // contentType per card, no per-item recomposition beyond the card
        // itself, and the row only scrolls sideways (swipe actions on the
        // tiles are off so they never fight the carousel scroll).
        if (useEpisodeCarousel) {
            item(key = "episodes-carousel") {
                val carouselState = rememberLazyListState()
                // Flat index of the auto-jump target episode inside the
                // carousel (targetEpisodeIndex counts EpisodeList.Item
                // entries only, MissingCount separators excluded).
                val targetCarouselIndex = remember(episodesToShow, targetEpisodeIndex) {
                    targetEpisodeIndex?.let { target ->
                        var seenItems = -1
                        episodesToShow.indexOfFirst { entry ->
                            if (entry is EpisodeList.Item) {
                                seenItems++
                                seenItems == target
                            } else {
                                false
                            }
                        }.takeIf { it >= 0 }
                    }
                }
                var carouselJumpDone by remember { mutableStateOf(false) }
                LaunchedEffect(targetCarouselIndex, episodesExpanded) {
                    if (!carouselJumpDone && targetCarouselIndex != null && episodesExpanded) {
                        carouselJumpDone = true
                        carouselState.scrollToItem(targetCarouselIndex)
                    }
                }
                LazyRow(
                    state = carouselState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .auroraCenteredMaxWidth(contentMaxWidthDp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(
                        items = episodesToShow,
                        key = { entry ->
                            when (entry) {
                                is EpisodeList.Item -> "episode-${entry.episode.id}"
                                is EpisodeList.MissingCount -> "missing-${entry.id}"
                            }
                        },
                        contentType = { entry ->
                            when (entry) {
                                is EpisodeList.Item -> "episode"
                                is EpisodeList.MissingCount -> "missing-count"
                            }
                        },
                    ) { entry ->
                        when (entry) {
                            is EpisodeList.Item -> {
                                AnimeEpisodeGridCard(
                                    item = entry,
                                    selected = entry.selected,
                                    isNew = entry.episode.id in state.newEpisodeIds,
                                    isAnyEpisodeSelected = isAnyEpisodeSelected,
                                    showPreview = state.showPreviews,
                                    episodeSwipeStartAction = episodeSwipeStartAction,
                                    episodeSwipeEndAction = episodeSwipeEndAction,
                                    onClick = {
                                        when (
                                            resolveAuroraEpisodeClickAction(
                                                isEpisodeSelected = entry.selected,
                                                isAnyEpisodeSelected = isAnyEpisodeSelected,
                                            )
                                        ) {
                                            AuroraEpisodeClickAction.OpenEpisode -> {
                                                onEpisodeClicked(entry.episode, false)
                                            }
                                            AuroraEpisodeClickAction.SelectEpisode -> {
                                                onEpisodeSelected(entry, true, true, false)
                                                if (shouldAutoExpandAuroraEpisodesList(
                                                        episodesExpanded,
                                                        episodes.size,
                                                    )
                                                ) {
                                                    episodesExpanded = true
                                                }
                                            }
                                            AuroraEpisodeClickAction.UnselectEpisode -> {
                                                onEpisodeSelected(entry, false, true, false)
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        onEpisodeSelected(entry, !entry.selected, true, true)
                                        if (!entry.selected &&
                                            shouldAutoExpandAuroraEpisodesList(
                                                episodesExpanded,
                                                episodes.size,
                                            )
                                        ) {
                                            episodesExpanded = true
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onEpisodeSwipe = { action ->
                                        onEpisodeSwipe(entry, action)
                                    },
                                    onDownloadEpisode = onDownloadEpisode,
                                    modifier = Modifier.width(280.dp),
                                    enableSwipe = false,
                                )
                            }
                            is EpisodeList.MissingCount -> {
                                AnimeCarouselMissingCard(count = entry.count)
                            }
                        }
                    }
                }
            }
        } else {
        // Episode list
        items(
            items = episodesToShow,
            key = { (it as? EpisodeList.Item)?.episode?.id ?: it.hashCode() },
            contentType = {
                when (it) {
                    is EpisodeList.Item -> "episode"
                    is EpisodeList.MissingCount -> "missing-count"
                }
            },
        ) { item ->
            when (item) {
                is EpisodeList.Item -> {
                    AnimeEpisodeCardCompact(
                        anime = anime,
                        item = item,
                        selected = item.selected,
                        isNew = item.episode.id in state.newEpisodeIds,
                        isAnyEpisodeSelected = isAnyEpisodeSelected,
                        episodeSwipeStartAction = episodeSwipeStartAction,
                        episodeSwipeEndAction = episodeSwipeEndAction,
                        showPreviews = state.showPreviews,
                        showSummaries = state.showSummaries,
                        density = episodeListDensity,
                        onClick = {
                            when (
                                resolveAuroraEpisodeClickAction(
                                    isEpisodeSelected = item.selected,
                                    isAnyEpisodeSelected = isAnyEpisodeSelected,
                                )
                            ) {
                                AuroraEpisodeClickAction.OpenEpisode -> {
                                    onEpisodeClicked(item.episode, false)
                                }
                                AuroraEpisodeClickAction.SelectEpisode -> {
                                    onEpisodeSelected(item, true, true, false)
                                    val shouldExpand = shouldAutoExpandAuroraEpisodesList(
                                        episodesExpanded,
                                        episodes.size,
                                    )
                                    if (shouldExpand) {
                                        episodesExpanded = true
                                    }
                                }
                                AuroraEpisodeClickAction.UnselectEpisode -> {
                                    onEpisodeSelected(item, false, true, false)
                                }
                            }
                        },
                        onLongClick = {
                            onEpisodeSelected(item, !item.selected, true, true)
                            if (!item.selected &&
                                shouldAutoExpandAuroraEpisodesList(
                                    episodesExpanded,
                                    episodes.size,
                                )
                            ) {
                                episodesExpanded = true
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onEpisodeSwipe = { action -> onEpisodeSwipe(item, action) },
                        onDownloadEpisode = onDownloadEpisode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .auroraCenteredMaxWidth(contentMaxWidthDp)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                is EpisodeList.MissingCount -> {
                    MissingItemCountListItem(count = item.count)
                }
            }
        }
        }
    }
    // Show More button if there are more than 5 episodes
    if (realEpisodeCount > 5 && !alwaysShowFullEpisodeList) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                AuroraEpisodeListToggleButton(
                    text = if (episodesExpanded) {
                        stringResource(AYMR.strings.action_show_less)
                    } else {
                        stringResource(
                            AYMR.strings.action_show_all_episodes,
                            realEpisodeCount,
                        )
                    },
                    onClick = { episodesExpanded = !episodesExpanded },
                )
            }
        }
    }

    // "Títulos similares" closes the screen (definitive design):
    // the suggestions carousel/button sits after the episode list
    // and the show-all button instead of before the episodes.
    if (entrySuggestionsEnabled) {
        if (entrySuggestionsExpandInline) {
            item(key = "suggestions_row") {
                eu.kanade.presentation.entries.components.aurora.AuroraSuggestionsRow(
                    state = state.suggestions,
                    onSuggestionClick = onSuggestionClick,
                    onOpenSuggestions = onOpenSuggestions,
                    onRetryClick = onRetrySuggestions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .auroraCenteredMaxWidth(contentMaxWidthDp),
                )
            }
        } else if (!entrySuggestionsInOverflow) {
            item(key = "suggestions_button") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .auroraCenteredMaxWidth(contentMaxWidthDp)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = if (colors.isDark) {
                                    listOf(
                                        Color.White.copy(alpha = 0.12f),
                                        Color.White.copy(alpha = 0.08f),
                                    )
                                } else {
                                    listOf(
                                        colors.accent.copy(alpha = 0.15f),
                                        colors.accent.copy(alpha = 0.10f),
                                    )
                                },
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .auroraSpringClick(onClick = onOpenSuggestions)
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(MR.strings.suggestions_similar_titles),
                        color = if (colors.isDark) Color.White else colors.accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.AnimeAuroraHeroOverlay(
    heroThreshold: Int,
    useTwoPaneLayout: Boolean,
    isAnyEpisodeSelected: Boolean,
    titleScreenStyle: TitleScreenStyle,
    scrollOffsetState: State<Int>,
    firstVisibleItemIndexState: State<Int>,
    anime: Anime,
    state: AnimeScreenModel.State.Success,
    translation: AuroraEntryTranslationState,
    hasWatchingProgress: Boolean,
    detailsSnapshot: AnimeDetailsSnapshot,
    selectedGenresState: MutableState<Set<String>>,
    titleStaggerState: AuroraTitleStaggerState,
    firstEpisodeNumberText: String?,
    navigationBarsBottom: Dp,
    onEditNotesClicked: (() -> Unit)?,
    onContinueWatching: () -> Unit,
    onDubbingClicked: (() -> Unit)?,
    selectedDubbing: String?,
    onGenreClick: ((String) -> Unit)?,
    onGenresSearch: ((List<String>) -> Unit)?,
    onTitleCopy: () -> Unit,
) {
    var selectedGenres by selectedGenresState

    // Derived booleans/floats so scroll only invalidates when thresholds are crossed;
    // alpha itself is read deferred inside graphicsLayer.
    val showHero by remember(useTwoPaneLayout, isAnyEpisodeSelected, heroThreshold) {
        derivedStateOf {
            shouldShowAnimeAuroraHeroContent(
                useTwoPaneLayout = useTwoPaneLayout,
                firstVisibleItemIndex = firstVisibleItemIndexState.value,
                scrollOffset = scrollOffsetState.value,
                heroThreshold = heroThreshold,
                isSelectionMode = isAnyEpisodeSelected,
            )
        }
    }
    if (
        titleScreenStyle == TitleScreenStyle.POSTER_IMMERSIVE &&
        showHero
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .zIndex(AuroraZIndex.HERO),
            contentAlignment = Alignment.BottomStart,
        ) {
            val heroAlphaState = remember(heroThreshold) {
                derivedStateOf { (1f - (scrollOffsetState.value / heroThreshold.toFloat())).coerceIn(0f, 1f) }
            }

            Box(
                modifier = Modifier
                    .zIndex(AuroraZIndex.HERO)
                    .graphicsLayer { alpha = heroAlphaState.value },
            ) {
                AnimeDetailHero(
                    anime = anime,
                    translation = translation,
                    hasWatchingProgress = hasWatchingProgress,
                    ratingText = detailsSnapshot.ratingText,
                    episodeCount = state.episodes.size,
                    statusText = detailsSnapshot.statusText,
                    note = anime.notes,
                    onEditNotesClicked = onEditNotesClicked,
                    onContinueWatching = onContinueWatching,
                    onDubbingClicked = onDubbingClicked,
                    selectedDubbing = selectedDubbing,
                    onGenreClick = onGenreClick,
                    onGenreLongClick = { genre ->
                        selectedGenres = if (genre in selectedGenres) {
                            selectedGenres - genre
                        } else {
                            selectedGenres + genre
                        }
                    },
                    selectedGenres = selectedGenres,
                    onSearchSelected = {
                        onGenresSearch?.invoke(selectedGenres.toList())
                        selectedGenres = emptySet()
                    },
                    onClearSelected = { selectedGenres = emptySet() },
                    onCopyTitle = onTitleCopy,
                    titleStaggerState = titleStaggerState,
                    firstEpisodeNumberText = firstEpisodeNumberText,
                    bottomPadding = resolveAuroraHeroBottomPadding(navigationBarsBottom),
                    modifier = Modifier,
                )
            }
        }
    }
}

@Composable
private fun AnimeAuroraTopBar(
    heroThreshold: Int,
    state: AnimeScreenModel.State.Success,
    titleScreenStyle: TitleScreenStyle,
    scrollOffsetState: State<Int>,
    firstVisibleItemIndexState: State<Int>,
    showAnimeOverlayChrome: Boolean,
    isAnyEpisodeSelected: Boolean,
    hazeState: HazeState,
    titleStaggerState: AuroraTitleStaggerState,
    autoJumpToNextLabel: String,
    globalSearchQuery: String?,
    entrySuggestionsEnabled: Boolean,
    entrySuggestionsInOverflow: Boolean,
    navigateUp: () -> Unit,
    onFilterButtonClicked: () -> Unit,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onRefresh: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onShareClicked: (() -> Unit)?,
    onEditNotesClicked: (() -> Unit)?,
    onClickEditInfo: (() -> Unit)?,
    onSettingsClicked: (() -> Unit)?,
    onDeleteDownloadsClicked: () -> Unit,
    onDeleteReadEpisodesClicked: () -> Unit,
    onToggleAutoJumpToNext: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,
    onOpenSuggestions: () -> Unit,
) {
    val overlayChromeAlpha by animateFloatAsState(
        targetValue = if (!isAnyEpisodeSelected && showAnimeOverlayChrome) 1f else 0f,
        label = "overlayChromeAlpha",
    )
    val overlayChromeOffsetY by animateFloatAsState(
        targetValue = if (!isAnyEpisodeSelected && showAnimeOverlayChrome) 0f else -1f,
        label = "overlayChromeOffsetY",
    )
    val isPosterMode = titleScreenStyle == TitleScreenStyle.POSTER_IMMERSIVE

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(AuroraZIndex.HERO)
            .graphicsLayer {
                alpha = overlayChromeAlpha
                translationY = overlayChromeOffsetY * size.height
            }
            .titleScreenStagger(titleStaggerState, 0)
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Scoped read: progress is recomputed only within this row on scroll.
        val topBarScrollProgress = if (firstVisibleItemIndexState.value > 0) {
            1f
        } else {
            (scrollOffsetState.value / heroThreshold.toFloat()).coerceIn(0f, 1f)
        }
        // Back button - Aurora glassmorphism style
        AuroraActionButton(
            onClick = navigateUp,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            hazeState = hazeState,
            scrollProgress = topBarScrollProgress,
            isPosterMode = isPosterMode,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Filter button - Aurora glassmorphism style
        AuroraActionButton(
            onClick = onFilterButtonClicked,
            icon = Icons.Default.FilterList,
            contentDescription = null,
            hazeState = hazeState,
            scrollProgress = topBarScrollProgress,
            isPosterMode = isPosterMode,
        )

        // Download menu - Aurora glassmorphism style
        if (onDownloadActionClicked != null) {
            var downloadExpanded by remember { mutableStateOf(false) }
            Box(contentAlignment = Alignment.TopEnd) {
                AuroraActionButton(
                    onClick = { downloadExpanded = !downloadExpanded },
                    icon = Icons.Filled.Download,
                    contentDescription = null,
                    hazeState = hazeState,
                    scrollProgress = topBarScrollProgress,
                    isPosterMode = isPosterMode,
                )
                EntryDownloadDropdownMenu(
                    expanded = downloadExpanded,
                    onDismissRequest = { downloadExpanded = false },
                    onDownloadClicked = { onDownloadActionClicked.invoke(it) },
                    isManga = false,
                    useAuroraStyle = true,
                )
            }
        }

        // More menu - Aurora glassmorphism style
        var showMenu by remember { mutableStateOf(false) }
        Box(contentAlignment = Alignment.TopEnd) {
            AuroraActionButton(
                onClick = { showMenu = !showMenu },
                icon = Icons.Default.MoreVert,
                contentDescription = null,
                hazeState = hazeState,
                scrollProgress = topBarScrollProgress,
                isPosterMode = isPosterMode,
            )
            AuroraEntryDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                AuroraEntryDropdownMenuItem(
                    text = stringResource(MR.strings.action_webview_refresh),
                    onClick = {
                        onRefresh()
                        showMenu = false
                    },
                )
                // WebView lived on the old action card; the definitive
                // design removes that card from the mobile immersive
                // screen, so it stays reachable from the ⋮ menu.
                if (onWebViewClicked != null) {
                    AuroraEntryDropdownMenuItem(
                        text = stringResource(MR.strings.action_open_in_web_view),
                        onClick = {
                            onWebViewClicked()
                            showMenu = false
                        },
                    )
                }
                val downloadedEpisodes = state.episodes.count { it.isDownloaded }
                val watchedDownloadedEpisodes = state.episodes.count { it.isDownloaded && it.episode.seen }
                if (downloadedEpisodes > 0) {
                    AuroraEntryDropdownMenuItem(
                        text = stringResource(AYMR.strings.anime_delete_downloaded_menu, downloadedEpisodes),
                        onClick = {
                            onDeleteDownloadsClicked()
                            showMenu = false
                        },
                    )
                }
                if (watchedDownloadedEpisodes > 0) {
                    AuroraEntryDropdownMenuItem(
                        text = stringResource(AYMR.strings.anime_delete_read_menu, watchedDownloadedEpisodes),
                        onClick = {
                            onDeleteReadEpisodesClicked()
                            showMenu = false
                        },
                    )
                }
                AuroraEntryDropdownMenuItem(
                    text = autoJumpToNextLabel,
                    onClick = {
                        onToggleAutoJumpToNext()
                        showMenu = false
                    },
                )
                if (globalSearchQuery != null) {
                    AuroraEntryDropdownMenuItem(
                        text = stringResource(MR.strings.action_global_search),
                        onClick = {
                            onSearch(globalSearchQuery, true)
                            showMenu = false
                        },
                    )
                }
                if (entrySuggestionsEnabled && entrySuggestionsInOverflow) {
                    AuroraEntryDropdownMenuItem(
                        text = stringResource(MR.strings.pref_entry_suggestions),
                        onClick = {
                            onOpenSuggestions()
                            showMenu = false
                        },
                    )
                }
                if (onMigrateClicked != null) {
                    AuroraEntryDropdownMenuItem(
                        text = stringResource(MR.strings.action_migrate),
                        onClick = {
                            onMigrateClicked()
                            showMenu = false
                        },
                    )
                }
                if (onShareClicked != null) {
                    AuroraEntryDropdownMenuItem(
                        text = stringResource(MR.strings.action_share),
                        onClick = {
                            onShareClicked()
                            showMenu = false
                        },
                    )
                }
                if (onEditNotesClicked != null) {
                    AuroraEntryDropdownMenuItem(
                        text = stringResource(MR.strings.action_notes),
                        onClick = {
                            onEditNotesClicked()
                            showMenu = false
                        },
                    )
                }
                if (onClickEditInfo != null) {
                    AuroraEntryDropdownMenuItem(
                        text = stringResource(MR.strings.action_edit_info),
                        onClick = {
                            onClickEditInfo()
                            showMenu = false
                        },
                    )
                }
                if (onSettingsClicked != null) {
                    AuroraEntryDropdownMenuItem(
                        text = stringResource(MR.strings.action_settings),
                        onClick = {
                            onSettingsClicked()
                            showMenu = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AuroraSelectionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        colors.accent.copy(alpha = 0.2f),
                        colors.accent.copy(alpha = 0.1f),
                    ),
                ),
            )
            .clickable(onClick = onClick)
            .padding(7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.textPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AuroraEpisodeSelectionBottomStack(
    selected: List<EpisodeList.Item>,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancel: () -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onMultiDeleteClicked: (List<Episode>) -> Unit,
    fillFraction: Float,
    alwaysUseExternalPlayer: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(fillFraction)) {
        if (selected.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AuroraTheme.colors.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AuroraTheme.colors.accent.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = selected.size.toString(),
                        color = AuroraTheme.colors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                AuroraSelectionIconButton(
                    icon = Icons.Outlined.SelectAll,
                    contentDescription = stringResource(MR.strings.action_select_all),
                    onClick = onSelectAll,
                )
                AuroraSelectionIconButton(
                    icon = Icons.Filled.SwapHoriz,
                    contentDescription = stringResource(MR.strings.action_select_inverse),
                    onClick = onInvertSelection,
                )
                AuroraSelectionIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = stringResource(MR.strings.action_cancel),
                    onClick = onCancel,
                )
            }
        }

        EntryBottomActionMenu(
            visible = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            onBookmarkClicked = {
                onMultiBookmarkClicked(selected.map { it.episode }, true)
            }.takeIf { selected.any { !it.episode.bookmark } },
            onRemoveBookmarkClicked = {
                onMultiBookmarkClicked(selected.map { it.episode }, false)
            }.takeIf { selected.all { it.episode.bookmark } },
            onFillermarkClicked = {
                onMultiFillermarkClicked(selected.map { it.episode }, true)
            }.takeIf { selected.any { !it.episode.fillermark } },
            onRemoveFillermarkClicked = {
                onMultiFillermarkClicked(selected.map { it.episode }, false)
            }.takeIf { selected.all { it.episode.fillermark } },
            onMarkAsViewedClicked = {
                onMultiMarkAsSeenClicked(selected.map { it.episode }, true)
            }.takeIf { selected.any { !it.episode.seen } },
            onMarkAsUnviewedClicked = {
                onMultiMarkAsSeenClicked(selected.map { it.episode }, false)
            }.takeIf { selected.any { it.episode.seen || it.episode.lastSecondSeen > 0L } },
            onMarkPreviousAsViewedClicked = {
                onMarkPreviousAsSeenClicked(selected.first().episode)
            }.takeIf { selected.size == 1 },
            onDownloadClicked = {
                onDownloadEpisode!!(selected, EpisodeDownloadAction.START)
            }.takeIf {
                onDownloadEpisode != null && selected.any { it.downloadState != AnimeDownload.State.DOWNLOADED }
            },
            onDeleteClicked = {
                onMultiDeleteClicked(selected.map { it.episode })
            }.takeIf {
                onDownloadEpisode != null && selected.any { it.downloadState == AnimeDownload.State.DOWNLOADED }
            },
            onExternalClicked = {
                onEpisodeClicked(selected.first().episode, true)
            }.takeIf { !alwaysUseExternalPlayer && selected.size == 1 },
            onInternalClicked = {
                onEpisodeClicked(selected.first().episode, true)
            }.takeIf { alwaysUseExternalPlayer && selected.size == 1 },
            isManga = false,
        )
    }
}

@Composable
private fun AnimeSeasonRailAurora(
    anime: Anime,
    seasons: List<AnimeSeasonItem>,
    onSeasonClicked: ((SeasonAnime) -> Unit)?,
    onContinueWatchingClicked: ((SeasonAnime) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (seasons.size <= 1) return

    val currentAnimeId = anime.id
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        seasons
            .sortedBy { it.seasonAnime.anime.seasonNumber }
            .forEach { item ->
                val onOpenSeason: () -> Unit = {
                    if (onSeasonClicked != null) {
                        onSeasonClicked(item.seasonAnime)
                    }
                }
                Box(
                    modifier = Modifier.width(132.dp),
                ) {
                    EntryComfortableGridItem(
                        isSelected = item.seasonAnime.anime.id == currentAnimeId,
                        title = item.seasonAnime.anime.title.ifBlank {
                            stringResource(
                                AYMR.strings.display_mode_season,
                                formatEpisodeNumber(item.seasonAnime.anime.seasonNumber),
                            )
                        },
                        onClick = onOpenSeason,
                        onLongClick = onOpenSeason,
                        coverData = AnimeCover(
                            animeId = item.seasonAnime.anime.id,
                            sourceId = item.seasonAnime.anime.source,
                            isAnimeFavorite = item.seasonAnime.anime.favorite,
                            url = item.seasonAnime.anime.thumbnailUrl,
                            lastModified = item.seasonAnime.anime.coverLastModified,
                        ),
                        onClickContinueViewing = if (onContinueWatchingClicked != null && item.showContinueOverlay) {
                            { onContinueWatchingClicked(item.seasonAnime) }
                        } else {
                            null
                        },
                    )
                }
            }
    }
}

private val seasonRegex = Regex("""(?i)(?:^|\b|\s|\[|_)(?:s|season\s*)(\d+)(?:\s|e|x|\||-|\.|\b|\]|_|$)""")

private val specialKeywordsRegex =
    Regex(
        """(?i)\b(ova|oav|ona|movie|pv|trailer|bonus|recap|summary|prologue|extra|special|omake|teaser|clip|interview|preview)s?\b""",
    )

internal fun shouldUseAnimeAuroraTwoPane(deviceClass: AuroraDeviceClass): Boolean {
    return deviceClass == AuroraDeviceClass.TabletExpanded
}

internal fun shouldUseAnimeAuroraPaneScopedFastScroller(useTwoPaneLayout: Boolean): Boolean {
    return useTwoPaneLayout
}

internal fun shouldShowAnimeAuroraHeroContent(
    useTwoPaneLayout: Boolean,
    firstVisibleItemIndex: Int,
    scrollOffset: Int,
    heroThreshold: Int,
    isSelectionMode: Boolean,
): Boolean {
    if (useTwoPaneLayout || isSelectionMode) return false
    return firstVisibleItemIndex == 0 && scrollOffset < heroThreshold
}

internal fun resolveAnimeAuroraFastScrollBlockStartIndex(
    useTwoPaneLayout: Boolean,
): Int {
    return if (useTwoPaneLayout) 1 else 3
}

private const val ANIME_AURORA_EPISODES_HEADER_KEY = "anime-aurora-episodes-header"
private val ANIME_AURORA_FAST_SCROLL_ITEM_TOP_INSET = 6.dp
internal enum class AuroraEpisodeClickAction {
    OpenEpisode,
    SelectEpisode,
    UnselectEpisode,
}

internal enum class AuroraSelectionControlsPlacement {
    BottomStack,
}

internal fun auroraSelectionControlsPlacement(): AuroraSelectionControlsPlacement {
    return AuroraSelectionControlsPlacement.BottomStack
}

internal fun shouldUseCompactAuroraSelectionActions(): Boolean {
    return true
}

internal fun resolveAuroraEpisodeClickAction(
    isEpisodeSelected: Boolean,
    isAnyEpisodeSelected: Boolean,
): AuroraEpisodeClickAction {
    return when {
        isEpisodeSelected -> AuroraEpisodeClickAction.UnselectEpisode
        isAnyEpisodeSelected -> AuroraEpisodeClickAction.SelectEpisode
        else -> AuroraEpisodeClickAction.OpenEpisode
    }
}

internal fun shouldAutoExpandAuroraEpisodesList(
    episodesExpanded: Boolean,
    totalEpisodes: Int,
): Boolean {
    return !episodesExpanded && totalEpisodes > 5
}

internal fun shouldAutoExpandAuroraEpisodesListForFastScroll(
    episodesExpanded: Boolean,
    totalEpisodes: Int,
): Boolean {
    return shouldAutoExpandAuroraEpisodesList(
        episodesExpanded = episodesExpanded,
        totalEpisodes = totalEpisodes,
    )
}

internal fun resolveCoverUrl(
    state: AnimeScreenModel.State.Success,
    useMetadataCovers: Boolean,
): ResolvedCover {
    return resolveExternalMetadataCover(
        baseCoverUrl = state.anime.thumbnailUrl.orEmpty(),
        metadata = state.animeMetadata,
        isMetadataLoading = state.isMetadataLoading,
        metadataError = state.metadataError,
        useMetadataCovers = useMetadataCovers,
    )
}

/**
 * Aurora-styled action button with glassmorphism effect
 */
@Composable
private fun AuroraActionButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
    hazeState: HazeState? = null,
    scrollProgress: Float = 0f,
    isPosterMode: Boolean = false,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val tint = iconTint ?: colors.accent.copy(alpha = 0.95f)

    Box(
        modifier = modifier
            .auroraHeaderIconSurface(
                colors = colors,
                hazeState = hazeState,
                scrollProgress = scrollProgress,
                isPosterMode = isPosterMode,
            )
            .size(44.dp)
            .clickable(onClick = {
                appHaptics.tap()
                onClick()
            }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun VirtualSeasonSwitcherAurora(
    seasons: List<String>,
    selectedSeason: String,
    onSeasonClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        seasons.forEach { season ->
            val isSelected = season == selectedSeason
            val activeBgColors = listOf(
                colors.accent.copy(alpha = 0.24f),
                colors.accent.copy(alpha = 0.12f),
            )
            val inactiveBgColors = listOf(
                colors.surface.copy(alpha = 0.4f),
                colors.surface.copy(alpha = 0.15f),
            )
            val currentBgColors = if (isSelected) activeBgColors else inactiveBgColors

            val activeBorderColor = colors.accent.copy(alpha = 0.6f)
            val inactiveBorderColor = colors.divider.copy(alpha = 0.25f)
            val currentBorderColor = if (isSelected) activeBorderColor else inactiveBorderColor

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(brush = Brush.linearGradient(colors = currentBgColors))
                    .border(
                        width = 1.dp,
                        color = currentBorderColor,
                        shape = RoundedCornerShape(100.dp),
                    )
                    .auroraSpringClick { onSeasonClicked(season) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = getLocalizedSeasonLabel(season),
                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun getLocalizedSeasonLabel(season: String): String {
    val isRussian = LocalContext.current.resources.configuration.locales[0].language == "ru"
    return when {
        season.startsWith("Season ", ignoreCase = true) -> {
            val num = season.substringAfter("Season ").trim()
            stringResource(AYMR.strings.display_mode_season, num)
        }
        season.equals("Specials", ignoreCase = true) -> {
            if (isRussian) "Спешлы" else "Specials"
        }
        season.equals("Extras", ignoreCase = true) -> {
            if (isRussian) "Экстры" else "Extras"
        }
        else -> season
    }
}

@Composable
private fun AuroraEpisodeListToggleButton(
    text: String,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(16.dp)

    // Wide full-width button from the definitive design ("Mostrar los N
    // episodios" / "Mostrar menos").
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = if (colors.isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.08f),
                        )
                    } else {
                        listOf(
                            colors.surface.copy(alpha = 0.55f),
                            colors.surface.copy(alpha = 0.30f),
                        )
                    },
                ),
                shape = shape,
            )
            .auroraSpringClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colors.accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
