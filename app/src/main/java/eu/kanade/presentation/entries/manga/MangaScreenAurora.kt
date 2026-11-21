package eu.kanade.presentation.entries.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AiCoverMode
import eu.kanade.domain.ui.model.TitleScreenStyle
import eu.kanade.presentation.components.AuroraBackground
import eu.kanade.presentation.components.EntryDownloadDropdownMenu
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.TitleFastScrollOverlayAccumulator
import eu.kanade.presentation.entries.components.AiCoverEnhanceDialog
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
import eu.kanade.presentation.entries.manga.components.ChapterDownloadAction
import eu.kanade.presentation.entries.manga.components.MangaChapterListItem
import eu.kanade.presentation.entries.manga.components.ScanlatorBranchSelector
import eu.kanade.presentation.entries.manga.components.aurora.ChaptersHeader
import eu.kanade.presentation.entries.manga.components.aurora.FullscreenPosterBackground
import eu.kanade.presentation.entries.manga.components.aurora.MangaActionCard
import eu.kanade.presentation.entries.manga.components.aurora.MangaDetailHero
import eu.kanade.presentation.entries.manga.components.aurora.MangaDetailLibraryPills
import eu.kanade.presentation.entries.manga.components.aurora.MangaDetailsSnapshot
import eu.kanade.presentation.entries.manga.components.aurora.MangaGlassHeroCard
import eu.kanade.presentation.entries.manga.components.aurora.MangaInfoCard
import eu.kanade.presentation.entries.manga.components.aurora.MangaStatsCard
import eu.kanade.presentation.entries.manga.components.aurora.MangaSynopsisCard
import eu.kanade.presentation.entries.manga.components.aurora.resolveMangaDetailsSnapshot
import eu.kanade.presentation.entries.reduceTitleFastScrollOverlayAccumulator
import eu.kanade.presentation.entries.resolveEntryAutoJumpTargetIndex
import eu.kanade.presentation.entries.resolveTitleListFastScrollSpec
import eu.kanade.presentation.entries.shouldShowTitleFastScrollFloatingActionButton
import eu.kanade.presentation.entries.shouldShowTitleFastScrollOverlayChrome
import eu.kanade.presentation.entries.translation.AuroraEntryTranslationState
import eu.kanade.presentation.entries.translation.rememberAuroraEntryTranslation
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.resolveAuroraAdaptiveSpec
import eu.kanade.presentation.theme.auroraHeaderIconSurface
import eu.kanade.tachiyomi.data.coil.ai.CoverAiEnhancer
import eu.kanade.tachiyomi.data.coil.ai.RealEsrganBridge
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.source.manga.getNameForMangaInfo
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreenModel
import eu.kanade.tachiyomi.util.debugTitleCoverFlow
import eu.kanade.tachiyomi.util.previewTitleCoverUrl
import eu.kanade.tachiyomi.util.system.copyToClipboardSilently
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.metadata.model.MetadataSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.entries.manga.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DateFormat
import java.time.Instant
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaScreenAuroraImpl(
    state: MangaScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    showScanlatorSelector: Boolean,
    scanlatorChapterCounts: Map<String, Int>,
    selectedScanlator: String?,
    onScanlatorSelected: (String?) -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
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
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onSettingsClicked: (() -> Unit)?,
    onToggleSort: (() -> Unit)? = null,
    onToggleUnreadFilter: (() -> Unit)? = null,
    isAutoJumpToNextEnabled: Boolean,
    autoJumpToNextLabel: String,
    onToggleAutoJumpToNext: () -> Unit,
    onClickEditInfo: (() -> Unit)? = null,
    onDeleteDownloadsClicked: () -> Unit = {},
    onDeleteReadChaptersClicked: () -> Unit = {},
    onRetrySuggestions: () -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
    onGenreClick: ((String) -> Unit)? = null,
    onGenreLongClick: ((String) -> Unit)? = null,
    onGenresSearch: ((List<String>) -> Unit)? = null,
    onSourceClick: (() -> Unit)? = null,
) {
    val manga = state.manga
    val selectedGenresState = remember { mutableStateOf(emptySet<String>()) }
    var selectedGenres by selectedGenresState
    val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val entrySuggestionsEnabled by sourcePreferences.entrySuggestionsEnabled().collectAsState()
    val entrySuggestionsExpandInline by uiPreferences.entrySuggestionsExpandInline().collectAsState()
    val entrySuggestionsInOverflow by uiPreferences.entrySuggestionsInOverflow().collectAsState()
    val alwaysShowFullChapterList by uiPreferences.alwaysShowFullChapterListManga().collectAsState()
    val titleScreenStyle by uiPreferences.titleScreenStyle().collectAsState()
    val titleScreenAnimation by uiPreferences.titleScreenAnimation().collectAsState()
    // V6: el modo ultra ligero apaga la animación de entrada al detalle.
    val ultraLightMode by uiPreferences.ultraLightMode().collectAsState()
    val titleStaggerState = rememberTitleScreenStaggerState(titleScreenAnimation && !ultraLightMode)
    val showOriginalTitle by uiPreferences.showOriginalTitle().collectAsState()
    val globalSearchQuery = remember(manga.displayTitle) { normalizeAuroraGlobalSearchQuery(manga.displayTitle) }
    val chapters = state.chapterListItems
    val realChapterCount = remember(chapters) {
        chapters.count { it is ChapterList.Item }
    }
    val missingChaptersCount = remember(chapters) {
        chapters.filterIsInstance<ChapterList.MissingCount>().sumOf { it.count }
    }
    val selectedChapters = remember(chapters) {
        chapters.filterIsInstance<ChapterList.Item>().filter { it.selected }
    }
    val isAnyChapterSelected = selectedChapters.isNotEmpty()
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
    val useTwoPaneLayout = shouldUseMangaAuroraTwoPane(auroraAdaptiveSpec.deviceClass)
    val metadataSource: MetadataSource = remember { uiPreferences.metadataSource().get() }
    val resolvedCover = remember(
        manga,
        state.isMetadataLoading,
        state.metadataError,
        state.mangaMetadata,
        metadataSource,
    ) {
        resolveExternalMetadataCover(
            baseCoverUrl = manga.thumbnailUrl.orEmpty(),
            metadata = state.mangaMetadata,
            isMetadataLoading = state.isMetadataLoading,
            metadataError = state.metadataError,
            useMetadataCovers = metadataSource != MetadataSource.NONE,
        )
    }
    val refererUrl = remember(state.source) {
        (state.source as? HttpSource)?.baseUrl
    }
    val sourceHeaders = remember(state.source) {
        (state.source as? HttpSource)?.headers?.toMap()
    }
    val sourceClient = remember(state.source) {
        (state.source as? HttpSource)?.client
    }
    LaunchedEffect(
        manga.id,
        state.isMetadataLoading,
        state.metadataError,
        resolvedCover.coverUrl,
        resolvedCover.coverUrlFallback,
    ) {
        val debugMessage = "id=${manga.id} loading=${state.isMetadataLoading} " +
            "error=${state.metadataError?.javaClass?.simpleName ?: "none"} " +
            "base=${previewTitleCoverUrl(manga.thumbnailUrl)} " +
            "resolved=${previewTitleCoverUrl(resolvedCover.coverUrl)} " +
            "fallback=${previewTitleCoverUrl(resolvedCover.coverUrlFallback)} " +
            "referer=${previewTitleCoverUrl(refererUrl)}"
        debugTitleCoverFlow(
            scope = "manga-screen",
            message = debugMessage,
        )
    }
    val chapterModels = remember(chapters) {
        chapters.mapNotNull { (it as? ChapterList.Item)?.chapter }
    }
    val context = LocalContext.current
    val detailsSnapshot = remember(
        manga,
        chapterModels,
        state.source,
        selectedScanlator,
        scanlatorChapterCounts,
        state.mangaMetadata,
        state.isMetadataLoading,
        state.metadataError,
    ) {
        resolveMangaDetailsSnapshot(
            sourceTitle = state.source.getNameForMangaInfo(),
            sourceName = state.source.name,
            sourceLanguage = state.source.lang,
            manga = manga,
            chapters = chapterModels,
            selectedScanlator = selectedScanlator,
            scanlatorChapterCounts = scanlatorChapterCounts,
            mangaMetadata = state.mangaMetadata,
            isMetadataLoading = state.isMetadataLoading,
            metadataError = state.metadataError,
            context = context,
        )
    }
    val nextChapterNum = remember(detailsSnapshot.progress) {
        detailsSnapshot.progress?.currentChapterIndex?.let { it + 1 }
    }
    // Keepsake «finished» stamp: live predicate (a completed story whose list is fully
    // read), keepsake date from the persisted first completion.
    val finishedStampEnabled by uiPreferences.auroraFinishedStampEnabled().collectAsState()
    val showFinishedStamp = finishedStampEnabled &&
        manga.displayStatus == SManga.COMPLETED.toLong() &&
        detailsSnapshot.progress?.isCompleted == true
    val finishedStampDate = remember(manga.completedAt) {
        manga.completedAt?.let { DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it)) }
    }
    val mangaActionResumeText = stringResource(MR.strings.action_resume)
    val mangaHeroTargetChText = nextChapterNum?.takeIf { it > 0 }?.let {
        stringResource(MR.strings.aurora_hero_cta_chapter, it)
    }
    val mangaHeroActionLabel =
        remember(detailsSnapshot.progress?.hasProgress, mangaHeroTargetChText, mangaActionResumeText) {
            if (detailsSnapshot.progress?.hasProgress == true && mangaHeroTargetChText != null) {
                "$mangaActionResumeText • $mangaHeroTargetChText"
            } else {
                null
            }
        }
    // White hero button label (approved design): "Continuar" with progress,
    // "Leer 1 cap." when starting from scratch.
    val mangaDetailContinueLabel = if (detailsSnapshot.progress?.hasProgress == true) {
        mangaActionResumeText
    } else {
        stringResource(AYMR.strings.aurora_read) + " " +
            stringResource(AYMR.strings.aurora_hero_cta_chapter, 1)
    }
    val auroraTranslationPreferences = remember { Injekt.get<UiPreferences>() }
    val auroraEntryTranslationEnabled by auroraTranslationPreferences
        .auroraEntryTranslationEnabled()
        .collectAsState()
    val auroraEntryTranslationSourceLanguages by auroraTranslationPreferences
        .auroraEntryTranslationSourceLanguages()
        .collectAsState()
    val auroraEntryTranslation = rememberAuroraEntryTranslation(
        title = manga.displayTitle,
        description = manga.displayDescription,
        sourceLanguage = state.source.lang,
        enabled = auroraEntryTranslationEnabled,
        allowedSourceFamilies = auroraEntryTranslationSourceLanguages,
    )

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

    val lazyListState = rememberLazyListState()
    // Scroll values are exposed as State and read only inside the smallest consumer scopes
    // (poster background, hero layer, top bar row) so per-pixel scroll updates do not
    // recompose the whole screen.
    val scrollOffsetState = remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
    val firstVisibleItemIndexState = remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
    val haptic = LocalHapticFeedback.current

    // State for chapters expansion
    val chaptersExpandedState = remember(alwaysShowFullChapterList) { mutableStateOf(alwaysShowFullChapterList) }
    var chaptersExpanded by chaptersExpandedState
    var isReverseScrollingOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(chaptersExpanded) {
        if (chaptersExpanded) {
            isReverseScrollingOverlay = false
        }
    }
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
                    isExpandedList = chaptersExpanded,
                    isScrolling = isScrolling,
                    index = index,
                    offset = offset,
                )
            }
            .map { it.revealed }
            .distinctUntilChanged()
            .collect { isReverseScrollingOverlay = it }
    }
    val chaptersToShow = remember(chapters, chaptersExpanded) {
        if (chaptersExpanded) {
            chapters
        } else {
            val result = mutableListOf<ChapterList>()
            var visibleChapters = 0
            for (item in chapters) {
                if (item is ChapterList.Item) {
                    if (visibleChapters >= 5) break
                    visibleChapters++
                }
                result += item
            }
            result
        }
    }

    // v5: chapters are a vertical list again, so the auto-jump target is the
    // LazyColumn index of the target chapter row: the items before the first
    // chapter row plus the target's flat position inside chaptersToShow
    // (targetChapterIndex counts ChapterList.Item entries only, while
    // MissingCount separators are LazyColumn items too).
    // LazyColumn items before the first chapter row: the mobile list renders
    // the fullscreen spacer, the pills/synopsis block and the chapters header;
    // the two-pane list starts at its header. Suggestions render after the
    // chapter list, so they never shift these indices.
    val mangaAuroraPreChapterItemCount = remember(
        useTwoPaneLayout,
        showScanlatorSelector,
        missingChaptersCount,
    ) {
        val headerAndGaps = 1 +
            (if (showScanlatorSelector) 1 else 0) +
            (if (missingChaptersCount > 0) 1 else 0)
        if (useTwoPaneLayout) {
            headerAndGaps
        } else {
            2 + headerAndGaps
        }
    }

    // Flat LazyColumn position of the auto-jump target chapter inside
    // chaptersToShow (MissingCount separators included: each one is a row).
    // Null while the collapsed list does not contain the target yet.
    val mangaAuroraTargetChapterListIndex = remember(chaptersToShow, state.targetChapterIndex) {
        state.targetChapterIndex?.let { target ->
            var seenItems = -1
            chaptersToShow.indexOfFirst { entry ->
                if (entry is ChapterList.Item) {
                    seenItems++
                    seenItems == target
                } else {
                    false
                }
            }.takeIf { it >= 0 }
        }
    }

    // Auto-scroll to target chapter on initial load
    var hasScrolledToTarget: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(state.targetChapterIndex, isAutoJumpToNextEnabled) {
        val targetIndex = resolveEntryAutoJumpTargetIndex(
            enabled = isAutoJumpToNextEnabled,
            targetIndex = state.targetChapterIndex,
            restoredScrollIndex = state.scrollIndex,
        )
        if (targetIndex != null) {
            chaptersExpanded = true
        }
    }

    LaunchedEffect(state.targetChapterIndex, chaptersExpanded) {
        if (!hasScrolledToTarget && chaptersExpanded) {
            hasScrolledToTarget = true
            val targetIndex = resolveEntryAutoJumpTargetIndex(
                enabled = isAutoJumpToNextEnabled,
                targetIndex = state.targetChapterIndex,
                restoredScrollIndex = state.scrollIndex,
            )
            if (targetIndex != null) {
                val targetListIndex = mangaAuroraTargetChapterListIndex
                if (targetListIndex != null) {
                    lazyListState.animateScrollToItem(mangaAuroraPreChapterItemCount + targetListIndex)
                }
            }
        }
    }

    BackHandler(onBack = {
        if (isAnyChapterSelected) {
            onAllChapterSelected(false)
        } else {
            navigateUp()
        }
    })

    LaunchedEffect(isAnyChapterSelected, chaptersExpanded, chapters.size) {
        if (isAnyChapterSelected &&
            shouldAutoExpandAuroraChaptersList(
                chaptersExpanded = chaptersExpanded,
                totalChapters = chapters.size,
            )
        ) {
            chaptersExpanded = true
        }
    }

    // State for description and genres expansion
    val descriptionExpandedState = remember { mutableStateOf(false) }
    var descriptionExpanded by descriptionExpandedState
    val genresExpandedState = remember { mutableStateOf(false) }
    var genresExpanded by genresExpandedState
    val isThumbFastScrollingState = remember { mutableStateOf(false) }
    var isThumbFastScrolling by isThumbFastScrollingState
    val showMangaOverlayChrome by remember {
        derivedStateOf {
            shouldShowTitleFastScrollOverlayChrome(
                isThumbDragged = isThumbFastScrolling,
                isExpandedList = chaptersExpanded,
                isReverseScrolling = isReverseScrollingOverlay,
            )
        }
    }

    AuroraEntryHoldToRefresh(
        refreshing = state.isRefreshingData,
        onRefresh = onRefresh,
        enabled = !isAnyChapterSelected,
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
            MangaAuroraBackgroundLayer(
                manga = manga,
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
                        MangaAuroraTwoPaneStartContent(
                            manga = manga,
                            state = state,
                            translation = auroraEntryTranslation,
                            detailsSnapshot = detailsSnapshot,
                            titleScreenStyle = titleScreenStyle,
                            showOriginalTitle = showOriginalTitle,
                            resolvedCover = resolvedCover,
                            refererUrl = refererUrl,
                            sourceHeaders = sourceHeaders,
                            sourceClient = sourceClient,
                            showFinishedStamp = showFinishedStamp,
                            finishedStampDate = finishedStampDate,
                            titleStaggerState = titleStaggerState,
                            hazeState = hazeState,
                            selectedGenresState = selectedGenresState,
                            descriptionExpandedState = descriptionExpandedState,
                            genresExpandedState = genresExpandedState,
                            entrySuggestionsEnabled = entrySuggestionsEnabled,
                            entrySuggestionsExpandInline = entrySuggestionsExpandInline,
                            entrySuggestionsInOverflow = entrySuggestionsInOverflow,
                            colors = colors,
                            mangaHeroActionLabel = mangaHeroActionLabel,
                            mangaDetailContinueLabel = mangaDetailContinueLabel,
                            realChapterCount = realChapterCount,
                            onEditNotesClicked = onEditNotesClicked,
                            onContinueReading = onContinueReading,
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
                        MangaAuroraTwoPaneEndContent(
                            lazyListState = lazyListState,
                            manga = manga,
                            state = state,
                            chapters = chapters,
                            chaptersToShow = chaptersToShow,
                            chaptersExpandedState = chaptersExpandedState,
                            isThumbFastScrollingState = isThumbFastScrollingState,
                            realChapterCount = realChapterCount,
                            missingChaptersCount = missingChaptersCount,
                            alwaysShowFullChapterList = alwaysShowFullChapterList,
                            showScanlatorSelector = showScanlatorSelector,
                            scanlatorChapterCounts = scanlatorChapterCounts,
                            selectedScanlator = selectedScanlator,
                            onScanlatorSelected = onScanlatorSelected,
                            titleStaggerState = titleStaggerState,
                            isAnyChapterSelected = isAnyChapterSelected,
                            chapterSwipeStartAction = chapterSwipeStartAction,
                            chapterSwipeEndAction = chapterSwipeEndAction,
                            onChapterClicked = onChapterClicked,
                            onChapterSelected = onChapterSelected,
                            onChapterSwipe = onChapterSwipe,
                            onDownloadChapter = onDownloadChapter,
                            onToggleSort = onToggleSort,
                            onToggleUnreadFilter = onToggleUnreadFilter,
                            onFilterButtonClicked = onFilterButtonClicked,
                            colors = colors,
                        )
                    },
                )
            } else {
                MangaAuroraMobileContent(
                    lazyListState = lazyListState,
                    manga = manga,
                    state = state,
                    titleScreenStyle = titleScreenStyle,
                    contentMaxWidthDp = contentMaxWidthDp,
                    screenHeight = screenHeight,
                    translation = auroraEntryTranslation,
                    detailsSnapshot = detailsSnapshot,
                    descriptionExpandedState = descriptionExpandedState,
                    genresExpandedState = genresExpandedState,
                    selectedGenresState = selectedGenresState,
                    showOriginalTitle = showOriginalTitle,
                    resolvedCover = resolvedCover,
                    refererUrl = refererUrl,
                    sourceHeaders = sourceHeaders,
                    sourceClient = sourceClient,
                    showFinishedStamp = showFinishedStamp,
                    finishedStampDate = finishedStampDate,
                    titleStaggerState = titleStaggerState,
                    realChapterCount = realChapterCount,
                    mangaHeroActionLabel = mangaHeroActionLabel,
                    colors = colors,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onEditCategoryClicked = onEditCategoryClicked,
                    onTrackingClicked = onTrackingClicked,
                    onEditNotesClicked = onEditNotesClicked,
                    onContinueReading = onContinueReading,
                    onCoverClicked = onCoverClicked,
                    onTitleCopy = onTitleCopy,
                    onTagSearch = onTagSearch,
                    onGenreClick = onGenreClick,
                    onGenresSearch = onGenresSearch,
                    onWebViewClicked = onWebViewClicked,
                    onShareClicked = onShareClicked,
                    onSourceClick = onSourceClick,
                    chapters = chapters,
                    chaptersToShow = chaptersToShow,
                    missingChaptersCount = missingChaptersCount,
                    alwaysShowFullChapterList = alwaysShowFullChapterList,
                    showScanlatorSelector = showScanlatorSelector,
                    scanlatorChapterCounts = scanlatorChapterCounts,
                    selectedScanlator = selectedScanlator,
                    onScanlatorSelected = onScanlatorSelected,
                    chaptersExpandedState = chaptersExpandedState,
                    isAnyChapterSelected = isAnyChapterSelected,
                    chapterSwipeStartAction = chapterSwipeStartAction,
                    chapterSwipeEndAction = chapterSwipeEndAction,
                    onChapterClicked = onChapterClicked,
                    onChapterSelected = onChapterSelected,
                    onChapterSwipe = onChapterSwipe,
                    onDownloadChapter = onDownloadChapter,
                    onToggleSort = onToggleSort,
                    onToggleUnreadFilter = onToggleUnreadFilter,
                    onFilterButtonClicked = onFilterButtonClicked,
                    entrySuggestionsEnabled = entrySuggestionsEnabled,
                    entrySuggestionsExpandInline = entrySuggestionsExpandInline,
                    entrySuggestionsInOverflow = entrySuggestionsInOverflow,
                    onSuggestionClick = onSuggestionClick,
                    onOpenSuggestions = onOpenSuggestions,
                    onRetrySuggestions = onRetrySuggestions,
                    isThumbFastScrollingState = isThumbFastScrollingState,
                )
            }

            // Hero content (fixed at bottom of first screen) - fades out on scroll
            // Show when we haven't scrolled much (index 0 with scroll less than 70% of screen height)
            val heroThreshold = (screenHeight.value * 0.7f).toInt()
            MangaAuroraHeroOverlay(
                heroThreshold = heroThreshold,
                useTwoPaneLayout = useTwoPaneLayout,
                isAnyChapterSelected = isAnyChapterSelected,
                titleScreenStyle = titleScreenStyle,
                scrollOffsetState = scrollOffsetState,
                firstVisibleItemIndexState = firstVisibleItemIndexState,
                manga = manga,
                translation = auroraEntryTranslation,
                detailsSnapshot = detailsSnapshot,
                selectedGenresState = selectedGenresState,
                titleStaggerState = titleStaggerState,
                hazeState = hazeState,
                navigationBarsBottom = navigationBarsBottom,
                mangaDetailContinueLabel = mangaDetailContinueLabel,
                onEditNotesClicked = onEditNotesClicked,
                onContinueReading = onContinueReading,
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
            val shouldShowFab = !useTwoPaneLayout && showFab && !isAnyChapterSelected
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
                        onClick = onContinueReading,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }

            MangaAuroraTopBar(
                heroThreshold = heroThreshold,
                state = state,
                resolvedCoverUrl = resolvedCover.coverUrl,
                chapters = chapters,
                titleScreenStyle = titleScreenStyle,
                scrollOffsetState = scrollOffsetState,
                firstVisibleItemIndexState = firstVisibleItemIndexState,
                showMangaOverlayChrome = showMangaOverlayChrome,
                isAnyChapterSelected = isAnyChapterSelected,
                hazeState = hazeState,
                titleStaggerState = titleStaggerState,
                colors = colors,
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
                onDeleteReadChaptersClicked = onDeleteReadChaptersClicked,
                onToggleAutoJumpToNext = onToggleAutoJumpToNext,
                onSearch = onSearch,
                onOpenSuggestions = onOpenSuggestions,
            )

            AuroraChapterSelectionBottomStack(
                selected = selectedChapters,
                onSelectAll = { onAllChapterSelected(true) },
                onInvertSelection = onInvertSelection,
                onCancel = { onAllChapterSelected(false) },
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                onDownloadChapter = onDownloadChapter,
                onMultiDeleteClicked = onMultiDeleteClicked,
                fillFraction = if (useTwoPaneLayout) 0.5f else 1f,
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

@Composable
private fun BoxScope.MangaAuroraBackgroundLayer(
    manga: Manga,
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
            manga = manga,
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
private fun MangaAuroraTwoPaneStartContent(
    manga: Manga,
    state: MangaScreenModel.State.Success,
    translation: AuroraEntryTranslationState,
    detailsSnapshot: MangaDetailsSnapshot,
    titleScreenStyle: TitleScreenStyle,
    showOriginalTitle: Boolean,
    resolvedCover: ResolvedCover,
    refererUrl: String?,
    sourceHeaders: Map<String, String>?,
    sourceClient: okhttp3.Call.Factory?,
    showFinishedStamp: Boolean,
    finishedStampDate: String?,
    titleStaggerState: AuroraTitleStaggerState,
    hazeState: HazeState,
    selectedGenresState: MutableState<Set<String>>,
    descriptionExpandedState: MutableState<Boolean>,
    genresExpandedState: MutableState<Boolean>,
    entrySuggestionsEnabled: Boolean,
    entrySuggestionsExpandInline: Boolean,
    entrySuggestionsInOverflow: Boolean,
    colors: AuroraColors,
    mangaHeroActionLabel: String?,
    mangaDetailContinueLabel: String,
    realChapterCount: Int,
    onEditNotesClicked: (() -> Unit)?,
    onContinueReading: () -> Unit,
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
                MangaDetailHero(
                    manga = manga,
                    translation = translation,
                    detailsSnapshot = detailsSnapshot,
                    note = manga.notes,
                    onEditNotesClicked = onEditNotesClicked,
                    continueLabel = mangaDetailContinueLabel,
                    onContinueReading = onContinueReading,
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
                    hazeState = hazeState,
                    modifier = Modifier,
                )
                Spacer(modifier = Modifier.height(if (colors.isDark) 8.dp else 16.dp))
                MangaStatsCard(
                    manga = manga,
                    detailsSnapshot = detailsSnapshot,
                    showAuthor = false,
                    onSourceClick = onSourceClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 2),
                )
                Spacer(modifier = Modifier.height(if (colors.isDark) 8.dp else 16.dp))
                MangaInfoCard(
                    manga = manga,
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
                Spacer(modifier = Modifier.height(if (colors.isDark) 12.dp else 16.dp))
                MangaDetailLibraryPills(
                    manga = manga,
                    trackingCount = state.trackingCount,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onAddToLibraryLongClicked = onEditCategoryClicked,
                    onTrackingClicked = onTrackingClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 4),
                )
            } else {
                MangaGlassHeroCard(
                    manga = manga,
                    translation = translation,
                    note = manga.notes,
                    onEditNotesClicked = onEditNotesClicked,
                    hasProgress = detailsSnapshot.progress?.hasProgress == true,
                    onContinueReading = onContinueReading,
                    onCoverClicked = onCoverClicked,
                    onCopyTitle = onTitleCopy,
                    actionLabel = mangaHeroActionLabel,
                    showOriginalTitle = showOriginalTitle,
                    chapterCount = realChapterCount,
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
                Spacer(modifier = Modifier.height(if (colors.isDark) 8.dp else 16.dp))
                MangaDetailLibraryPills(
                    manga = manga,
                    trackingCount = state.trackingCount,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onAddToLibraryLongClicked = onEditCategoryClicked,
                    onTrackingClicked = onTrackingClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 4),
                )
                Spacer(modifier = Modifier.height(if (colors.isDark) 8.dp else 16.dp))
                MangaInfoCard(
                    manga = manga,
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
                Spacer(modifier = Modifier.height(if (colors.isDark) 8.dp else 16.dp))
                MangaStatsCard(
                    manga = manga,
                    detailsSnapshot = detailsSnapshot,
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
private fun MangaAuroraTwoPaneEndContent(
    lazyListState: LazyListState,
    manga: Manga,
    state: MangaScreenModel.State.Success,
    chapters: List<ChapterList>,
    chaptersToShow: List<ChapterList>,
    chaptersExpandedState: MutableState<Boolean>,
    isThumbFastScrollingState: MutableState<Boolean>,
    realChapterCount: Int,
    missingChaptersCount: Int,
    alwaysShowFullChapterList: Boolean,
    showScanlatorSelector: Boolean,
    scanlatorChapterCounts: Map<String, Int>,
    selectedScanlator: String?,
    onScanlatorSelected: (String?) -> Unit,
    titleStaggerState: AuroraTitleStaggerState,
    isAnyChapterSelected: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onChapterClicked: (Chapter) -> Unit,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onToggleSort: (() -> Unit)?,
    onToggleUnreadFilter: (() -> Unit)?,
    onFilterButtonClicked: () -> Unit,
    colors: AuroraColors,
) {
    var chaptersExpanded by chaptersExpandedState
    var isThumbFastScrolling by isThumbFastScrollingState

    val topContentPadding = 96.dp
    val paneDensity = LocalDensity.current
    val paneTopPaddingPx = with(paneDensity) { topContentPadding.roundToPx() }
    val paneFastScrollBlockStartIndex = resolveMangaAuroraFastScrollBlockStartIndex(
        useTwoPaneLayout = true,
        showScanlatorSelector = showScanlatorSelector,
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
                    ?.plus(with(paneDensity) { MANGA_AURORA_FAST_SCROLL_ITEM_TOP_INSET.roundToPx() }),
            )
        }
    }
    VerticalFastScroller(
        listState = lazyListState,
        onThumbDragStarted = {
            val shouldExpand =
                shouldAutoExpandAuroraChaptersListForFastScroll(
                    chaptersExpanded,
                    chapters.size,
                )
            if (shouldExpand) {
                chaptersExpanded = true
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
            item {
                ChaptersHeader(
                    chapterCount = realChapterCount,
                    title = stringResource(MR.strings.chapters),
                    isDescending = manga.sortDescending(),
                    unreadFilter = manga.unreadFilter,
                    filterActive = state.filterActive,
                    onClickSort = onToggleSort ?: onFilterButtonClicked,
                    onLongClickSort = onFilterButtonClicked,
                    onClickFilter = onToggleUnreadFilter ?: onFilterButtonClicked,
                    onLongClickFilter = onFilterButtonClicked,
                    modifier = Modifier.titleScreenStagger(titleStaggerState, 5),
                )
            }

            if (missingChaptersCount > 0) {
                item(key = "missing-chapters-warning") {
                    MissingItemCountListItem(count = missingChaptersCount)
                }
            }

            if (showScanlatorSelector) {
                item {
                    ScanlatorBranchSelector(
                        scanlatorChapterCounts = scanlatorChapterCounts,
                        selectedScanlator = selectedScanlator,
                        onScanlatorSelected = onScanlatorSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            if (realChapterCount == 0) {
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

            // Chapters as a plain vertical list (v5): one row per chapter,
            // like the classic detail (see MangaAuroraChapterList).
            MangaAuroraChapterList(
                manga = manga,
                chapters = chapters,
                chaptersToShow = chaptersToShow,
                chaptersExpandedState = chaptersExpandedState,
                isAnyChapterSelected = isAnyChapterSelected,
                chapterSwipeStartAction = chapterSwipeStartAction,
                chapterSwipeEndAction = chapterSwipeEndAction,
                onChapterClicked = onChapterClicked,
                onChapterSelected = onChapterSelected,
                onChapterSwipe = onChapterSwipe,
                onDownloadChapter = onDownloadChapter,
                contentMaxWidthDp = null,
            )

            if (realChapterCount > 5 && !alwaysShowFullChapterList) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AuroraChapterListToggleButton(
                            text = if (chaptersExpanded) {
                                stringResource(AYMR.strings.action_show_less)
                            } else {
                                stringResource(
                                    AYMR.strings.action_show_all_chapters,
                                    realChapterCount,
                                )
                            },
                            onClick = { chaptersExpanded = !chaptersExpanded },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaAuroraMobileContent(
    lazyListState: LazyListState,
    manga: Manga,
    state: MangaScreenModel.State.Success,
    titleScreenStyle: TitleScreenStyle,
    contentMaxWidthDp: Int?,
    screenHeight: Dp,
    translation: AuroraEntryTranslationState,
    detailsSnapshot: MangaDetailsSnapshot,
    descriptionExpandedState: MutableState<Boolean>,
    genresExpandedState: MutableState<Boolean>,
    selectedGenresState: MutableState<Set<String>>,
    showOriginalTitle: Boolean,
    resolvedCover: ResolvedCover,
    refererUrl: String?,
    sourceHeaders: Map<String, String>?,
    sourceClient: okhttp3.Call.Factory?,
    showFinishedStamp: Boolean,
    finishedStampDate: String?,
    titleStaggerState: AuroraTitleStaggerState,
    realChapterCount: Int,
    mangaHeroActionLabel: String?,
    colors: AuroraColors,
    onAddToLibraryClicked: () -> Unit,
    onEditCategoryClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onEditNotesClicked: (() -> Unit)?,
    onContinueReading: () -> Unit,
    onCoverClicked: () -> Unit,
    onTitleCopy: () -> Unit,
    onTagSearch: (String) -> Unit,
    onGenreClick: ((String) -> Unit)?,
    onGenresSearch: ((List<String>) -> Unit)?,
    onWebViewClicked: (() -> Unit)?,
    onShareClicked: (() -> Unit)?,
    onSourceClick: (() -> Unit)?,
    chapters: List<ChapterList>,
    chaptersToShow: List<ChapterList>,
    missingChaptersCount: Int,
    alwaysShowFullChapterList: Boolean,
    showScanlatorSelector: Boolean,
    scanlatorChapterCounts: Map<String, Int>,
    selectedScanlator: String?,
    onScanlatorSelected: (String?) -> Unit,
    chaptersExpandedState: MutableState<Boolean>,
    isAnyChapterSelected: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onChapterClicked: (Chapter) -> Unit,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onToggleSort: (() -> Unit)?,
    onToggleUnreadFilter: (() -> Unit)?,
    onFilterButtonClicked: () -> Unit,
    entrySuggestionsEnabled: Boolean,
    entrySuggestionsExpandInline: Boolean,
    entrySuggestionsInOverflow: Boolean,
    onSuggestionClick: (eu.kanade.tachiyomi.data.suggestions.SuggestionItem) -> Unit,
    onOpenSuggestions: () -> Unit,
    onRetrySuggestions: () -> Unit,
    isThumbFastScrollingState: MutableState<Boolean>,
) {
    var chaptersExpanded by chaptersExpandedState
    var isThumbFastScrolling by isThumbFastScrollingState

    val density = LocalDensity.current
    val fastScrollBaseTopPadding =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp
    val fastScrollBaseTopPaddingPx = with(density) { fastScrollBaseTopPadding.roundToPx() }
    val fastScrollBlockStartIndex = resolveMangaAuroraFastScrollBlockStartIndex(
        useTwoPaneLayout = false,
        showScanlatorSelector = showScanlatorSelector,
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
                    ?.plus(with(density) { MANGA_AURORA_FAST_SCROLL_ITEM_TOP_INSET.roundToPx() }),
            )
        }
    }
    VerticalFastScroller(
        listState = lazyListState,
        onThumbDragStarted = {
            if (shouldAutoExpandAuroraChaptersListForFastScroll(chaptersExpanded, chapters.size)) {
                chaptersExpanded = true
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
            MangaAuroraMobileHeroItems(
                manga = manga,
                state = state,
                titleScreenStyle = titleScreenStyle,
                contentMaxWidthDp = contentMaxWidthDp,
                screenHeight = screenHeight,
                translation = translation,
                detailsSnapshot = detailsSnapshot,
                descriptionExpandedState = descriptionExpandedState,
                genresExpandedState = genresExpandedState,
                selectedGenresState = selectedGenresState,
                showOriginalTitle = showOriginalTitle,
                resolvedCover = resolvedCover,
                refererUrl = refererUrl,
                sourceHeaders = sourceHeaders,
                sourceClient = sourceClient,
                showFinishedStamp = showFinishedStamp,
                finishedStampDate = finishedStampDate,
                titleStaggerState = titleStaggerState,
                realChapterCount = realChapterCount,
                mangaHeroActionLabel = mangaHeroActionLabel,
                colors = colors,
                onAddToLibraryClicked = onAddToLibraryClicked,
                onEditCategoryClicked = onEditCategoryClicked,
                onTrackingClicked = onTrackingClicked,
                onEditNotesClicked = onEditNotesClicked,
                onContinueReading = onContinueReading,
                onCoverClicked = onCoverClicked,
                onTitleCopy = onTitleCopy,
                onTagSearch = onTagSearch,
                onGenreClick = onGenreClick,
                onGenresSearch = onGenresSearch,
                onWebViewClicked = onWebViewClicked,
                onShareClicked = onShareClicked,
                onSourceClick = onSourceClick,
            )

            MangaAuroraMobileChapterItems(
                manga = manga,
                state = state,
                chapters = chapters,
                chaptersToShow = chaptersToShow,
                realChapterCount = realChapterCount,
                missingChaptersCount = missingChaptersCount,
                alwaysShowFullChapterList = alwaysShowFullChapterList,
                showScanlatorSelector = showScanlatorSelector,
                scanlatorChapterCounts = scanlatorChapterCounts,
                selectedScanlator = selectedScanlator,
                onScanlatorSelected = onScanlatorSelected,
                chaptersExpandedState = chaptersExpandedState,
                isAnyChapterSelected = isAnyChapterSelected,
                chapterSwipeStartAction = chapterSwipeStartAction,
                chapterSwipeEndAction = chapterSwipeEndAction,
                onChapterClicked = onChapterClicked,
                onChapterSelected = onChapterSelected,
                onChapterSwipe = onChapterSwipe,
                onDownloadChapter = onDownloadChapter,
                onToggleSort = onToggleSort,
                onToggleUnreadFilter = onToggleUnreadFilter,
                onFilterButtonClicked = onFilterButtonClicked,
                titleStaggerState = titleStaggerState,
                contentMaxWidthDp = contentMaxWidthDp,
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

private fun LazyListScope.MangaAuroraMobileHeroItems(
    manga: Manga,
    state: MangaScreenModel.State.Success,
    titleScreenStyle: TitleScreenStyle,
    contentMaxWidthDp: Int?,
    screenHeight: Dp,
    translation: AuroraEntryTranslationState,
    detailsSnapshot: MangaDetailsSnapshot,
    descriptionExpandedState: MutableState<Boolean>,
    genresExpandedState: MutableState<Boolean>,
    selectedGenresState: MutableState<Set<String>>,
    showOriginalTitle: Boolean,
    resolvedCover: ResolvedCover,
    refererUrl: String?,
    sourceHeaders: Map<String, String>?,
    sourceClient: okhttp3.Call.Factory?,
    showFinishedStamp: Boolean,
    finishedStampDate: String?,
    titleStaggerState: AuroraTitleStaggerState,
    realChapterCount: Int,
    mangaHeroActionLabel: String?,
    colors: AuroraColors,
    onAddToLibraryClicked: () -> Unit,
    onEditCategoryClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onEditNotesClicked: (() -> Unit)?,
    onContinueReading: () -> Unit,
    onCoverClicked: () -> Unit,
    onTitleCopy: () -> Unit,
    onTagSearch: (String) -> Unit,
    onGenreClick: ((String) -> Unit)?,
    onGenresSearch: ((List<String>) -> Unit)?,
    onWebViewClicked: (() -> Unit)?,
    onShareClicked: (() -> Unit)?,
    onSourceClick: (() -> Unit)?,
) {
    var descriptionExpanded by descriptionExpandedState
    var genresExpanded by genresExpandedState
    var selectedGenres by selectedGenresState

    if (titleScreenStyle == TitleScreenStyle.POSTER_IMMERSIVE) {
        // Spacer for poster/hero area
        item {
            Spacer(modifier = Modifier.height(screenHeight))
        }

        // Pills + synopsis (approved design): shown once the
        // user scrolls past the fullscreen hero.
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
                MangaDetailLibraryPills(
                    manga = manga,
                    trackingCount = state.trackingCount,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onAddToLibraryLongClicked = onEditCategoryClicked,
                    onTrackingClicked = onTrackingClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .titleScreenStagger(titleStaggerState, 2),
                )
                Spacer(modifier = Modifier.height(12.dp))
                MangaSynopsisCard(
                    manga = manga,
                    translation = translation,
                    descriptionExpanded = descriptionExpanded,
                    onToggleDescription = {
                        descriptionExpanded = !descriptionExpanded
                    },
                    sourceTitle = detailsSnapshot.sourceTitle,
                    translationText = detailsSnapshot.translationText,
                    onSourceClick = onSourceClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .titleScreenStagger(titleStaggerState, 3),
                )
            }
        }
    } else {
        item {
            Spacer(
                modifier = Modifier.height(
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 76.dp,
                ),
            )
            MangaGlassHeroCard(
                manga = manga,
                translation = translation,
                note = manga.notes,
                onEditNotesClicked = onEditNotesClicked,
                hasProgress = detailsSnapshot.progress?.hasProgress == true,
                onContinueReading = onContinueReading,
                onCoverClicked = onCoverClicked,
                onCopyTitle = onTitleCopy,
                actionLabel = mangaHeroActionLabel,
                showOriginalTitle = showOriginalTitle,
                chapterCount = realChapterCount,
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
                Spacer(modifier = Modifier.height(if (colors.isDark) 8.dp else 16.dp))
                MangaActionCard(
                    manga = manga,
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
                Spacer(modifier = Modifier.height(if (colors.isDark) 8.dp else 16.dp))
                MangaInfoCard(
                    manga = manga,
                    translation = translation,
                    onTagSearch = onTagSearch,
                    descriptionExpanded = descriptionExpanded,
                    genresExpanded = genresExpanded,
                    onToggleDescription = { descriptionExpanded = !descriptionExpanded },
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
                Spacer(modifier = Modifier.height(if (colors.isDark) 8.dp else 16.dp))
                MangaStatsCard(
                    manga = manga,
                    detailsSnapshot = detailsSnapshot,
                    showAuthor = false,
                    onSourceClick = onSourceClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, 2),
                )
            }
        }
    }
}

private fun LazyListScope.MangaAuroraMobileChapterItems(
    manga: Manga,
    state: MangaScreenModel.State.Success,
    chapters: List<ChapterList>,
    chaptersToShow: List<ChapterList>,
    realChapterCount: Int,
    missingChaptersCount: Int,
    alwaysShowFullChapterList: Boolean,
    showScanlatorSelector: Boolean,
    scanlatorChapterCounts: Map<String, Int>,
    selectedScanlator: String?,
    onScanlatorSelected: (String?) -> Unit,
    chaptersExpandedState: MutableState<Boolean>,
    isAnyChapterSelected: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onChapterClicked: (Chapter) -> Unit,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onToggleSort: (() -> Unit)?,
    onToggleUnreadFilter: (() -> Unit)?,
    onFilterButtonClicked: () -> Unit,
    titleStaggerState: AuroraTitleStaggerState,
    contentMaxWidthDp: Int?,
    entrySuggestionsEnabled: Boolean,
    entrySuggestionsExpandInline: Boolean,
    entrySuggestionsInOverflow: Boolean,
    colors: AuroraColors,
    onSuggestionClick: (eu.kanade.tachiyomi.data.suggestions.SuggestionItem) -> Unit,
    onOpenSuggestions: () -> Unit,
    onRetrySuggestions: () -> Unit,
) {
    var chaptersExpanded by chaptersExpandedState

    // Chapters header
    item(
        key = MANGA_AURORA_CHAPTERS_HEADER_KEY,
        contentType = MANGA_AURORA_CHAPTERS_HEADER_KEY,
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        ChaptersHeader(
            chapterCount = realChapterCount,
            title = stringResource(MR.strings.chapters),
            isDescending = manga.sortDescending(),
            unreadFilter = manga.unreadFilter,
            filterActive = state.filterActive,
            onClickSort = onToggleSort ?: onFilterButtonClicked,
            onLongClickSort = onFilterButtonClicked,
            onClickFilter = onToggleUnreadFilter ?: onFilterButtonClicked,
            onLongClickFilter = onFilterButtonClicked,
            modifier = Modifier
                .auroraCenteredMaxWidth(contentMaxWidthDp)
                .titleScreenStagger(titleStaggerState, 5),
        )
    }

    if (showScanlatorSelector) {
        item {
            ScanlatorBranchSelector(
                scanlatorChapterCounts = scanlatorChapterCounts,
                selectedScanlator = selectedScanlator,
                onScanlatorSelected = onScanlatorSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    if (missingChaptersCount > 0) {
        item(key = "missing-chapters-warning") {
            MissingItemCountListItem(count = missingChaptersCount)
        }
    }

    // Empty state for chapters
    if (realChapterCount == 0) {
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

    // Chapters as a plain vertical list (v5): one row per chapter,
    // like the classic detail (see MangaAuroraChapterList).
    MangaAuroraChapterList(
        manga = manga,
        chapters = chapters,
        chaptersToShow = chaptersToShow,
        chaptersExpandedState = chaptersExpandedState,
        isAnyChapterSelected = isAnyChapterSelected,
        chapterSwipeStartAction = chapterSwipeStartAction,
        chapterSwipeEndAction = chapterSwipeEndAction,
        onChapterClicked = onChapterClicked,
        onChapterSelected = onChapterSelected,
        onChapterSwipe = onChapterSwipe,
        onDownloadChapter = onDownloadChapter,
        contentMaxWidthDp = contentMaxWidthDp,
    )

    // Show More button if there are more than 5 chapters
    if (realChapterCount > 5 && !alwaysShowFullChapterList) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                AuroraChapterListToggleButton(
                    text = if (chaptersExpanded) {
                        stringResource(AYMR.strings.action_show_less)
                    } else {
                        stringResource(AYMR.strings.action_show_all_chapters, realChapterCount)
                    },
                    onClick = { chaptersExpanded = !chaptersExpanded },
                )
            }
        }
    }

    // "Títulos similares" at the end (approved design).
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
private fun BoxScope.MangaAuroraHeroOverlay(
    heroThreshold: Int,
    useTwoPaneLayout: Boolean,
    isAnyChapterSelected: Boolean,
    titleScreenStyle: TitleScreenStyle,
    scrollOffsetState: State<Int>,
    firstVisibleItemIndexState: State<Int>,
    manga: Manga,
    translation: AuroraEntryTranslationState,
    detailsSnapshot: MangaDetailsSnapshot,
    selectedGenresState: MutableState<Set<String>>,
    titleStaggerState: AuroraTitleStaggerState,
    hazeState: HazeState,
    navigationBarsBottom: Dp,
    mangaDetailContinueLabel: String,
    onEditNotesClicked: (() -> Unit)?,
    onContinueReading: () -> Unit,
    onGenreClick: ((String) -> Unit)?,
    onGenresSearch: ((List<String>) -> Unit)?,
    onTitleCopy: () -> Unit,
) {
    var selectedGenres by selectedGenresState

    // Derived boolean/float so scroll only invalidates when thresholds are crossed;
    // alpha itself is read deferred inside graphicsLayer.
    val showHero by remember(useTwoPaneLayout, isAnyChapterSelected, heroThreshold) {
        derivedStateOf {
            shouldShowMangaAuroraHeroContent(
                useTwoPaneLayout = useTwoPaneLayout,
                firstVisibleItemIndex = firstVisibleItemIndexState.value,
                scrollOffset = scrollOffsetState.value,
                heroThreshold = heroThreshold,
                isSelectionMode = isAnyChapterSelected,
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
            // Calculate fade out alpha based on scroll (0-70% range);
            // deferred read keeps layer updates out of recomposition.
            val heroAlphaState = remember(heroThreshold) {
                derivedStateOf { (1f - (scrollOffsetState.value / heroThreshold.toFloat())).coerceIn(0f, 1f) }
            }

            Box(
                modifier = Modifier
                    .zIndex(AuroraZIndex.HERO)
                    .graphicsLayer { alpha = heroAlphaState.value },
            ) {
                MangaDetailHero(
                    manga = manga,
                    translation = translation,
                    detailsSnapshot = detailsSnapshot,
                    note = manga.notes,
                    onEditNotesClicked = onEditNotesClicked,
                    continueLabel = mangaDetailContinueLabel,
                    onContinueReading = onContinueReading,
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
                    hazeState = hazeState,
                    bottomPadding = resolveAuroraHeroBottomPadding(navigationBarsBottom),
                    modifier = Modifier,
                )
            }
        }
    }
}

@Composable
private fun MangaAuroraTopBar(
    heroThreshold: Int,
    state: MangaScreenModel.State.Success,
    resolvedCoverUrl: String? = null,
    chapters: List<ChapterList>,
    titleScreenStyle: TitleScreenStyle,
    scrollOffsetState: State<Int>,
    firstVisibleItemIndexState: State<Int>,
    showMangaOverlayChrome: Boolean,
    isAnyChapterSelected: Boolean,
    hazeState: HazeState,
    titleStaggerState: AuroraTitleStaggerState,
    colors: AuroraColors,
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
    onDeleteReadChaptersClicked: () -> Unit,
    onToggleAutoJumpToNext: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,
    onOpenSuggestions: () -> Unit,
) {
    val overlayChromeAlpha by animateFloatAsState(
        targetValue = if (!isAnyChapterSelected && showMangaOverlayChrome) 1f else 0f,
        label = "overlayChromeAlpha",
    )
    val overlayChromeOffsetY by animateFloatAsState(
        targetValue = if (!isAnyChapterSelected && showMangaOverlayChrome) 0f else -1f,
        label = "overlayChromeOffsetY",
    )
    // The lens densifies as the hero scrolls away, so list rows passing
    // under the top bar no longer blend into the buttons.
    val isPosterMode = titleScreenStyle == TitleScreenStyle.POSTER_IMMERSIVE
    // v7.9.20 Fase 2: el menú ⋮ vive en este top bar, no en
    // MangaScreenAuroraImpl, así que el contexto y el scope se sacan aquí.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
            iconTint = if (state.filterActive) colors.accent else colors.accent.copy(alpha = 0.7f),
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
                    isManga = true,
                    useAuroraStyle = true,
                )
            }
        }

        // More menu - Aurora glassmorphism style
        var showMenu by remember { mutableStateOf(false) }
        // v7.9.20 Fase 2: mejora de portada con IA (manual, Real-ESRGAN x2).
        // aiSupported se calcula una sola vez: sin Vulkan (o modo OFF) el
        // botón no existe, en vez de mostrar un error al tocarlo.
        var showAiDialog by remember { mutableStateOf(false) }
        var aiCoverVersion by remember { mutableStateOf(0) }
        val aiSupported = remember {
            CoverAiEnhancer.currentMode() != AiCoverMode.OFF && RealEsrganBridge.isSupported()
        }
        val hasAiCover = remember(state.manga.id, aiCoverVersion) {
            CoverAiEnhancer.hasEnhancedCover(context, CoverAiEnhancer.Kind.MANGA, state.manga.id)
        }
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
                // v7.9.20 Fase 2: «Mejorar portada» (IA, manual) y
                // «Volver a la original» cuando ya hay una mejorada.
                if (aiSupported) {
                    AuroraEntryDropdownMenuItem(
                        text = stringResource(AYMR.strings.ai_cover_improve),
                        onClick = {
                            showAiDialog = true
                            showMenu = false
                        },
                    )
                }
                if (hasAiCover) {
                    AuroraEntryDropdownMenuItem(
                        text = stringResource(AYMR.strings.ai_cover_revert),
                        onClick = {
                            showMenu = false
                            scope.launch {
                                CoverAiEnhancer.deleteEnhanced(
                                    context,
                                    CoverAiEnhancer.Kind.MANGA,
                                    state.manga.id,
                                )
                                aiCoverVersion++
                            }
                        },
                    )
                }
                val downloadedChapters = remember(chapters) {
                    chapters.filterIsInstance<ChapterList.Item>().count { it.isDownloaded }
                }
                val readDownloadedChapters = remember(chapters) {
                    chapters.filterIsInstance<ChapterList.Item>().count { it.isDownloaded && it.chapter.read }
                }
                resolveMangaAuroraOverflowActions(
                    hasGlobalSearch = globalSearchQuery != null,
                    hasWebView = onWebViewClicked != null,
                    hasShare = onShareClicked != null,
                    hasSettings = onSettingsClicked != null,
                    hasNotes = onEditNotesClicked != null,
                    hasEditInfo = onClickEditInfo != null,
                    hasMigrate = onMigrateClicked != null,
                    hasSuggestions = entrySuggestionsEnabled && entrySuggestionsInOverflow,
                    hasDeleteDownloads = downloadedChapters > 0,
                    hasDeleteRead = readDownloadedChapters > 0,
                ).forEach { action ->
                    AuroraEntryDropdownMenuItem(
                        text = when (action) {
                            AuroraMangaOverflowAction.Refresh ->
                                // I-LOW: was action_webview_refresh (webview page reload
                                // semantics); the entry overflow refreshes the manga data.
                                stringResource(MR.strings.action_entry_refresh)
                            AuroraMangaOverflowAction.AutoJump ->
                                autoJumpToNextLabel
                            AuroraMangaOverflowAction.DeleteDownloads ->
                                stringResource(AYMR.strings.novel_delete_downloaded_menu, downloadedChapters)
                            AuroraMangaOverflowAction.DeleteRead ->
                                stringResource(AYMR.strings.novel_delete_read_menu, readDownloadedChapters)
                            AuroraMangaOverflowAction.GlobalSearch ->
                                stringResource(MR.strings.action_global_search)
                            AuroraMangaOverflowAction.WebView ->
                                "Webview"
                            AuroraMangaOverflowAction.Share ->
                                stringResource(MR.strings.action_share)
                            AuroraMangaOverflowAction.Settings ->
                                stringResource(MR.strings.action_settings)
                            AuroraMangaOverflowAction.Notes ->
                                stringResource(MR.strings.action_notes)
                            AuroraMangaOverflowAction.EditInfo ->
                                stringResource(MR.strings.action_edit_info)
                            AuroraMangaOverflowAction.Migrate ->
                                stringResource(MR.strings.action_migrate)
                            AuroraMangaOverflowAction.Suggestions ->
                                stringResource(MR.strings.pref_entry_suggestions)
                        },
                        onClick = {
                            when (action) {
                                AuroraMangaOverflowAction.Refresh -> onRefresh()
                                AuroraMangaOverflowAction.AutoJump -> onToggleAutoJumpToNext()
                                AuroraMangaOverflowAction.DeleteDownloads -> onDeleteDownloadsClicked()
                                AuroraMangaOverflowAction.DeleteRead -> onDeleteReadChaptersClicked()
                                AuroraMangaOverflowAction.GlobalSearch -> onSearch(globalSearchQuery!!, true)
                                AuroraMangaOverflowAction.WebView -> onWebViewClicked!!()
                                AuroraMangaOverflowAction.Share -> onShareClicked!!()
                                AuroraMangaOverflowAction.Settings -> onSettingsClicked!!()
                                AuroraMangaOverflowAction.Notes -> onEditNotesClicked!!()
                                AuroraMangaOverflowAction.EditInfo -> onClickEditInfo!!()
                                AuroraMangaOverflowAction.Migrate -> onMigrateClicked!!()
                                AuroraMangaOverflowAction.Suggestions -> onOpenSuggestions()
                            }
                            showMenu = false
                        },
                    )
                }
            }
        }
        // v7.9.20 Fase 2: diálogo antes/después de la mejora con IA.
        if (showAiDialog) {
            AiCoverEnhanceDialog(
                kind = CoverAiEnhancer.Kind.MANGA,
                entryId = state.manga.id,
                coverUrl = resolvedCoverUrl,
                fallbackCoverUrl = state.manga.thumbnailUrl,
                onDismissRequest = { showAiDialog = false },
                onSaved = { aiCoverVersion++ },
            )
        }
    }
}

internal fun shouldUseMangaAuroraTwoPane(deviceClass: AuroraDeviceClass): Boolean {
    return deviceClass == AuroraDeviceClass.TabletExpanded
}

internal fun shouldUseMangaAuroraPaneScopedFastScroller(useTwoPaneLayout: Boolean): Boolean {
    return useTwoPaneLayout
}

internal fun shouldShowMangaAuroraHeroContent(
    useTwoPaneLayout: Boolean,
    firstVisibleItemIndex: Int,
    scrollOffset: Int,
    heroThreshold: Int,
    isSelectionMode: Boolean,
): Boolean {
    if (useTwoPaneLayout || isSelectionMode) return false
    return firstVisibleItemIndex == 0 && scrollOffset < heroThreshold
}

internal fun resolveMangaAuroraFastScrollBlockStartIndex(
    useTwoPaneLayout: Boolean,
    showScanlatorSelector: Boolean,
): Int {
    // Mobile: spacer (0), pills/synopsis (1), chapters header (2). Suggestions
    // render after the chapter list, so they never shift the header.
    val baseIndex = if (useTwoPaneLayout) 1 else 2
    return baseIndex + if (showScanlatorSelector) 1 else 0
}

private const val MANGA_AURORA_CHAPTERS_HEADER_KEY = "manga-aurora-chapters-header"
private val MANGA_AURORA_FAST_SCROLL_ITEM_TOP_INSET = 6.dp
internal enum class AuroraChapterClickAction {
    OpenChapter,
    SelectChapter,
    UnselectChapter,
}

internal fun resolveAuroraChapterClickAction(
    isChapterSelected: Boolean,
    isAnyChapterSelected: Boolean,
): AuroraChapterClickAction {
    return when {
        isChapterSelected -> AuroraChapterClickAction.UnselectChapter
        isAnyChapterSelected -> AuroraChapterClickAction.SelectChapter
        else -> AuroraChapterClickAction.OpenChapter
    }
}

internal fun shouldAutoExpandAuroraChaptersList(
    chaptersExpanded: Boolean,
    totalChapters: Int,
): Boolean {
    return !chaptersExpanded && totalChapters > 5
}

internal fun shouldAutoExpandAuroraChaptersListForFastScroll(
    chaptersExpanded: Boolean,
    totalChapters: Int,
): Boolean {
    return shouldAutoExpandAuroraChaptersList(
        chaptersExpanded = chaptersExpanded,
        totalChapters = totalChapters,
    )
}

internal enum class AuroraMangaOverflowAction {
    Refresh,
    AutoJump,
    DeleteDownloads,
    DeleteRead,
    GlobalSearch,
    WebView,
    Share,
    Settings,
    Notes,
    EditInfo,
    Migrate,
    Suggestions,
}

internal fun resolveMangaAuroraOverflowActions(
    hasGlobalSearch: Boolean,
    hasShare: Boolean,
    hasSettings: Boolean,
    hasNotes: Boolean,
    hasEditInfo: Boolean = false,
    hasWebView: Boolean = false,
    hasMigrate: Boolean,
    hasSuggestions: Boolean = false,
    hasDeleteDownloads: Boolean = false,
    hasDeleteRead: Boolean = false,
): List<AuroraMangaOverflowAction> {
    return buildList {
        add(AuroraMangaOverflowAction.Refresh)
        add(AuroraMangaOverflowAction.AutoJump)
        if (hasDeleteDownloads) add(AuroraMangaOverflowAction.DeleteDownloads)
        if (hasDeleteRead) add(AuroraMangaOverflowAction.DeleteRead)
        if (hasGlobalSearch) add(AuroraMangaOverflowAction.GlobalSearch)
        if (hasWebView) add(AuroraMangaOverflowAction.WebView)
        if (hasShare) add(AuroraMangaOverflowAction.Share)
        if (hasSettings) add(AuroraMangaOverflowAction.Settings)
        if (hasNotes) add(AuroraMangaOverflowAction.Notes)
        if (hasEditInfo) add(AuroraMangaOverflowAction.EditInfo)
        if (hasMigrate) add(AuroraMangaOverflowAction.Migrate)
        if (hasSuggestions) add(AuroraMangaOverflowAction.Suggestions)
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
private fun AuroraChapterSelectionBottomStack(
    selected: List<ChapterList.Item>,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancel: () -> Unit,
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    fillFraction: Float,
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
            isManga = true,
            modifier = Modifier.fillMaxWidth(),
            onBookmarkClicked = {
                onMultiBookmarkClicked(selected.map { it.chapter }, true)
            }.takeIf { selected.any { !it.chapter.bookmark } },
            onRemoveBookmarkClicked = {
                onMultiBookmarkClicked(selected.map { it.chapter }, false)
            }.takeIf { selected.all { it.chapter.bookmark } },
            onMarkAsViewedClicked = {
                onMultiMarkAsReadClicked(selected.map { it.chapter }, true)
            }.takeIf { selected.any { !it.chapter.read } },
            onMarkAsUnviewedClicked = {
                onMultiMarkAsReadClicked(selected.map { it.chapter }, false)
            }.takeIf { selected.any { it.chapter.read || it.chapter.lastPageRead > 0L } },
            onMarkPreviousAsViewedClicked = {
                onMarkPreviousAsReadClicked(selected.first().chapter)
            }.takeIf { selected.size == 1 },
            onDownloadClicked = {
                onDownloadChapter!!(selected, ChapterDownloadAction.START)
            }.takeIf {
                onDownloadChapter != null && selected.any { it.downloadState != MangaDownload.State.DOWNLOADED }
            },
            onDeleteClicked = {
                onMultiDeleteClicked(selected.map { it.chapter })
            }.takeIf {
                selected.any { it.downloadState == MangaDownload.State.DOWNLOADED }
            },
        )
    }
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
private fun AuroraChapterListToggleButton(
    text: String,
    onClick: () -> Unit,
) {
    // Wide grey button from the approved design ("Mostrar los N capítulos").
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                color = if (colors.isDark) {
                    Color.White.copy(alpha = 0.10f)
                } else {
                    colors.surface.copy(alpha = 0.55f)
                },
                shape = shape,
            )
            .auroraSpringClick(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Vertical chapter list (v5), shared by the mobile and two-pane lists:
 * one plain row per chapter, exactly like the classic detail (unread dot,
 * chapter name, date + read progress below, download icon on the right).
 * Stable keys + contentType per row, no per-item recomposition beyond the
 * row itself, and the outer LazyColumn scrolls vertically, so the list
 * stays fluid with any chapter count. Missing chapters between two
 * neighbours render as the full-width [MissingItemCountListItem], like the
 * classic list. The auto-jump target chapter is scrolled into view
 * vertically once, when the list is expanded (see the scroll effect in
 * [MangaScreenAuroraImpl]).
 */
private fun LazyListScope.MangaAuroraChapterList(
    manga: Manga,
    chapters: List<ChapterList>,
    chaptersToShow: List<ChapterList>,
    chaptersExpandedState: MutableState<Boolean>,
    isAnyChapterSelected: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onChapterClicked: (Chapter) -> Unit,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    contentMaxWidthDp: Int?,
) {
    var chaptersExpanded by chaptersExpandedState
    items(
        items = chaptersToShow,
        key = { entry ->
            when (entry) {
                is ChapterList.Item -> "chapter-${entry.chapter.id}"
                is ChapterList.MissingCount -> "missing-${entry.id}"
            }
        },
        contentType = { entry ->
            when (entry) {
                is ChapterList.Item -> "chapter"
                is ChapterList.MissingCount -> "missing-count"
            }
        },
    ) { entry ->
        when (entry) {
            is ChapterList.Item -> {
                MangaAuroraChapterRow(
                    manga = manga,
                    item = entry,
                    isAnyChapterSelected = isAnyChapterSelected,
                    chapterSwipeStartAction = chapterSwipeStartAction,
                    chapterSwipeEndAction = chapterSwipeEndAction,
                    onChapterClicked = onChapterClicked,
                    onChapterSelected = onChapterSelected,
                    onChapterSwipe = onChapterSwipe,
                    onDownloadChapter = onDownloadChapter,
                    onRequestExpandChapters = {
                        if (
                            shouldAutoExpandAuroraChaptersList(
                                chaptersExpanded,
                                chapters.size,
                            )
                        ) {
                            chaptersExpanded = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .auroraCenteredMaxWidth(contentMaxWidthDp),
                )
            }
            is ChapterList.MissingCount -> {
                MissingItemCountListItem(count = entry.count)
            }
        }
    }
}

/**
 * Vertical chapter row with the screen's standard click/selection wiring
 * (open, select/unselect, long-press, swipe, download), shared by the
 * mobile and two-pane lists. The row itself is the classic
 * [MangaChapterListItem]: unread dot, chapter name, date + read progress
 * ("Pág: N") below, download icon on the right — the exact rows of the
 * approved capture, no card background.
 */
@Composable
private fun MangaAuroraChapterRow(
    manga: Manga,
    item: ChapterList.Item,
    isAnyChapterSelected: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onChapterClicked: (Chapter) -> Unit,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onRequestExpandChapters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    MangaChapterListItem(
        title = if (manga.displayMode == Manga.CHAPTER_DISPLAY_NUMBER) {
            stringResource(
                MR.strings.display_mode_chapter,
                formatChapterNumber(item.chapter.chapterNumber),
            )
        } else {
            item.chapter.name
        },
        date = relativeDateTimeText(item.chapter.dateUpload),
        readProgress = item.chapter.lastPageRead
            .takeIf { !item.chapter.read && it > 0L }
            ?.let {
                stringResource(
                    MR.strings.chapter_progress,
                    it + 1,
                )
            },
        scanlator = item.chapter.scanlator.takeIf { !it.isNullOrBlank() },
        read = item.chapter.read,
        bookmark = item.chapter.bookmark,
        selected = item.selected,
        downloadIndicatorEnabled = !isAnyChapterSelected && !manga.isLocal(),
        downloadStateProvider = { item.downloadState },
        downloadProgressProvider = { item.downloadProgress },
        chapterSwipeStartAction = chapterSwipeStartAction,
        chapterSwipeEndAction = chapterSwipeEndAction,
        onLongClick = {
            onChapterSelected(item, !item.selected, true, true)
            if (!item.selected) {
                onRequestExpandChapters()
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onClick = {
            when (
                resolveAuroraChapterClickAction(
                    isChapterSelected = item.selected,
                    isAnyChapterSelected = isAnyChapterSelected,
                )
            ) {
                AuroraChapterClickAction.OpenChapter -> {
                    onChapterClicked(item.chapter)
                }
                AuroraChapterClickAction.SelectChapter -> {
                    onChapterSelected(item, true, true, false)
                    onRequestExpandChapters()
                }
                AuroraChapterClickAction.UnselectChapter -> {
                    onChapterSelected(item, false, true, false)
                }
            }
        },
        onDownloadClick = if (onDownloadChapter != null) {
            { onDownloadChapter(listOf(item), it) }
        } else {
            null
        },
        onChapterSwipe = { action -> onChapterSwipe(item, action) },
        modifier = modifier,
    )
}
