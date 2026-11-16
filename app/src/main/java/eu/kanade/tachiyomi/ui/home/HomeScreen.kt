package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.domain.ui.model.BottomNavAppearance
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.presentation.components.LocalHostScaffoldContentPadding
import eu.kanade.presentation.components.auroraCelestialBar
import eu.kanade.presentation.components.auroraCelestialHalo
import eu.kanade.presentation.components.auroraCelestialRail
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.components.latticeCircuitBar
import eu.kanade.presentation.components.latticeCircuitRail
import eu.kanade.presentation.components.rememberAuroraCelestialNavbarUnlocked
import eu.kanade.presentation.components.rememberLatticeCircuitNavbarUnlocked
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalIsEInkMode
import eu.kanade.presentation.tutorial.coachAnchorForTab
import eu.kanade.presentation.util.BottomNavVisibilityController
import eu.kanade.presentation.util.LocalBottomNavVisibilityController
import eu.kanade.presentation.util.ResolvedNavigationTransitionMode
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.presentation.util.resolveNavigationTransitionMode
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.download.DownloadsTab
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.system.powerManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.NavigationBar
import tachiyomi.presentation.core.components.material.NavigationRail
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Locale

/**
 * Shared [HazeState] for the home scaffold content source.
 * Same state powers the Aurora bottom nav glass — overlays (e.g. nickname editor)
 * should reuse it so blur samples the real tab surface.
 */
val LocalHomeHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Slot for overlays that must draw above the whole home scaffold but OUTSIDE the
 * [LocalHomeHazeState] haze source. Content hosted here (e.g. the nickname editor)
 * can use haze blur surfaces that sample and blur the real home content behind them
 * — blurring only what sits under the surface instead of the entire screen.
 */
val LocalHomeOverlayHost =
    staticCompositionLocalOf<MutableState<(@Composable () -> Unit)?>?> { null }

object HomeScreen : Screen() {
    // C1/C8: buffered - rendezvous sends suspended forever when HomeScreen was not composed
    // (or, for showBottomNav, when the bottom bar slot does not exist on navigation-rail
    // layouts), leaking suspended coroutines and dropping events.
    private val librarySearchEvent = Channel<LibrarySearchRequest>(Channel.BUFFERED)
    private val openTabEvent = Channel<Tab>(Channel.BUFFERED)
    private val showBottomNavEvent = Channel<Boolean>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private const val TAB_FADE_DURATION = 200
    private const val TAB_MODERN_ENTER_DURATION = 300
    private const val TAB_MODERN_EXIT_DURATION = 300
    private val AURORA_EASING = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    private const val TAB_NAVIGATOR_KEY = "HomeTabs"

    private val uiPreferences: UiPreferences by injectLazy()
    private val startScreen = uiPreferences.startScreen().get()
    private val defaultTab = startScreen.tab

    @Composable
    override fun Content() {
        val context = LocalContext.current

        val navStyle by uiPreferences.navStyle().collectAsState()
        val bottomNavAppearance by uiPreferences.bottomNavAppearance().collectAsState()
        val isEInkMode = LocalIsEInkMode.current

        val selectedTransitionMode by uiPreferences.navigationTransitionMode().collectAsState()
        val resolvedTransitionMode by remember(
            selectedTransitionMode,
            context.animatorDurationScale,
            context.powerManager.isPowerSaveMode,
            isEInkMode,
        ) {
            derivedStateOf {
                resolveNavigationTransitionMode(
                    selectedMode = selectedTransitionMode,
                    animatorDurationScale = context.animatorDurationScale,
                    isPowerSaveMode = context.powerManager.isPowerSaveMode,
                    isEInkMode = isEInkMode,
                )
            }
        }

        val currentMoreTab = navStyle.moreTab
        val theme by uiPreferences.appTheme().collectAsState()
        val isAuroraTheme = theme.isAuroraStyle
        val useNavigationRail = isTabletUi()
        val useAuroraBottomNav = bottomNavAppearance == BottomNavAppearance.Aurora

        val navigator = LocalNavigator.currentOrThrow
        val bottomNavVisibilityController = remember { BottomNavVisibilityController() }
        val hazeState = remember { HazeState() }
        val homeOverlayContent = remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

        // Animación de expansión de la barra Aurora (fotos de referencia):
        // al bajar, la barra se compacta y solo quedan los iconos; al subir,
        // se expande y vuelven los nombres. Mismo patrón que el chrome
        // inmersivo de la biblioteca (LibraryImmersiveChromeState): el
        // estado solo cambia cuando el scroll acumulado cruza el umbral
        // (56 dp), nunca por cada evento de scroll. El estado se lee SOLO
        // en la barra inferior, así que un cambio solo recompone la barra,
        // nunca el contenido ni el Scaffold.
        var auroraBarCollapseState by remember { mutableStateOf(AuroraBarCollapseState()) }
        val auroraBarHideThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
        val auroraBarScrollConnection = remember(auroraBarHideThresholdPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // Solo entrada directa del usuario; los scrolls
                    // programáticos (p. ej. saltar a una posición) no
                    // colapsan la barra. No se consume nada: el scroll
                    // sigue su curso normal.
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    val nextState = resolveAuroraBarCollapseState(
                        currentState = auroraBarCollapseState,
                        scrollDeltaPx = -available.y,
                        hideThresholdPx = auroraBarHideThresholdPx,
                    )
                    if (nextState != auroraBarCollapseState) {
                        auroraBarCollapseState = nextState
                    }
                    return Offset.Zero
                }
            }
        }
        // derivedStateOf: los valores derivados solo se recalculan cuando el
        // booleano cambia (al cruzar el umbral), no por cada evento de scroll.
        val auroraBarCollapsed by remember {
            derivedStateOf { !auroraBarCollapseState.isVisible }
        }
        val auroraBarHeight by remember {
            derivedStateOf { if (auroraBarCollapsed) 48.dp else 64.dp }
        }
        eu.kanade.presentation.tutorial.TutorialHost {
            TabNavigator(
                tab = defaultTab,
                key = TAB_NAVIGATOR_KEY,
            ) { tabNavigator ->
                val coachMarkState = eu.kanade.presentation.tutorial.LocalCoachMarkState.current
                LaunchedEffect(useNavigationRail) {
                    if (useNavigationRail) {
                        coachMarkState.isBottomBarVisible = false
                    }
                }
                // Al cambiar de pestaña, la barra Aurora vuelve expandida.
                LaunchedEffect(tabNavigator.current) {
                    auroraBarCollapseState = AuroraBarCollapseState()
                }
                LaunchedEffect(coachMarkState.activeTip) {
                    val activeTip = coachMarkState.activeTip ?: return@LaunchedEffect
                    val targetTabName = when (activeTip.anchor) {
                        eu.kanade.presentation.tutorial.TipAnchor.LIBRARY_TAB -> "Library"
                        eu.kanade.presentation.tutorial.TipAnchor.BROWSE_TAB -> "Browse"
                        eu.kanade.presentation.tutorial.TipAnchor.UPDATES_TAB -> "Updates"
                        eu.kanade.presentation.tutorial.TipAnchor.MORE_TAB -> "More"
                        eu.kanade.presentation.tutorial.TipAnchor.ADD_REPO_BUTTON -> "Browse"
                        else -> null
                    }
                    if (targetTabName != null) {
                        val matchingTab = navStyle.tabs.firstOrNull { tab ->
                            tab::class.simpleName.orEmpty().contains(targetTabName, ignoreCase = true)
                        } ?: if (targetTabName == "More") navStyle.moreTab else null

                        if (matchingTab != null && tabNavigator.current != matchingTab) {
                            tabNavigator.current = matchingTab
                        }
                        if (activeTip.anchor == eu.kanade.presentation.tutorial.TipAnchor.ADD_REPO_BUTTON) {
                            val uiPrefs = uy.kohesive.injekt.Injekt.get<eu.kanade.domain.ui.UiPreferences>()
                            if (uiPrefs.showAnimeSection().get()) {
                                eu.kanade.tachiyomi.ui.browse.BrowseTab.showAnimeExtension()
                            } else if (uiPrefs.showMangaSection().get()) {
                                eu.kanade.tachiyomi.ui.browse.BrowseTab.showExtension()
                            } else if (uiPrefs.showNovelSection().get()) {
                                eu.kanade.tachiyomi.ui.browse.BrowseTab.showNovelExtension()
                            }
                        }
                    }
                }

                // Provide usable navigator to content screen + shared haze for glass overlays
                CompositionLocalProvider(
                    LocalNavigator provides navigator,
                    LocalBottomNavVisibilityController provides bottomNavVisibilityController,
                    LocalHomeHazeState provides hazeState,
                    LocalHomeOverlayHost provides homeOverlayContent,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(
                            startBar = {
                                if (useNavigationRail) {
                                    val circuitRail = !isEInkMode && rememberLatticeCircuitNavbarUnlocked()
                                    if (isAuroraTheme && useAuroraBottomNav && !isEInkMode) {
                                        AuroraNavigationRail(
                                            tabs = navStyle.tabs,
                                            hazeState = hazeState,
                                            circuitDecoration = circuitRail,
                                        )
                                    } else {
                                        val railModifier = if (circuitRail) {
                                            Modifier.latticeCircuitRail()
                                        } else {
                                            Modifier
                                        }
                                        NavigationRail(modifier = railModifier) {
                                            // En el rail vale lo mismo que en la barra
                                            // inferior: sin "Más" (vive en los menús ⋮ de
                                            // arriba) y Títulos es la píldora Anime/Manga.
                                            navStyle.tabs.filter {
                                                it::class != AnimeLibraryTab::class && it::class != MoreTab::class
                                            }.fastForEach {
                                                NavigationRailItem(it)
                                            }
                                            RailMediaPill(auroraColors = null)
                                        }
                                    }
                                }
                            },
                            bottomBar = {
                                if (!useNavigationRail) {
                                    val bottomNavVisible by produceState(initialValue = true) {
                                        showBottomNavEvent.receiveAsFlow().collectLatest { value = it }
                                    }
                                    val showBottomNav = bottomNavVisible &&
                                        bottomNavVisibilityController.isVisible &&
                                        tabNavigator.current != currentMoreTab
                                    LaunchedEffect(showBottomNav) {
                                        coachMarkState.isBottomBarVisible = showBottomNav
                                    }
                                    val auroraColors = if (useAuroraBottomNav) {
                                        AuroraTheme.colorsForCurrentTheme()
                                    } else {
                                        null
                                    }
                                    val navBarShape = if (useAuroraBottomNav) {
                                        CircleShape
                                    } else {
                                        RoundedCornerShape(
                                            topStart = 20.dp,
                                            topEnd = 20.dp,
                                        )
                                    }
                                    val navContainerColor = if (useAuroraBottomNav) {
                                        Color.Transparent
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    }
                                    val navShadowElevation = 0.dp
                                    val navTonalElevation = 0.dp
                                    // La cápsula Aurora colapsada se achica en ANCHO y en ALTO:
                                    // más padding exterior (la barra se estrecha hacia el
                                    // centro), menos padding vertical e interior. Los iconos
                                    // NO cambian de tamaño (21 dp fijos); solo la cápsula
                                    // se achica. Misma duración/easing que la altura.
                                    val animatedAuroraBarHPadding by animateDpAsState(
                                        targetValue = if (auroraBarCollapsed) 56.dp else 12.dp,
                                        animationSpec = tween(
                                            durationMillis = 250,
                                            easing = AURORA_EASING,
                                        ),
                                        label = "auroraBarHPadding",
                                    )
                                    val animatedAuroraBarVPadding by animateDpAsState(
                                        targetValue = if (auroraBarCollapsed) 3.dp else 6.dp,
                                        animationSpec = tween(
                                            durationMillis = 250,
                                            easing = AURORA_EASING,
                                        ),
                                        label = "auroraBarVPadding",
                                    )
                                    val animatedAuroraBarContentPadding by animateDpAsState(
                                        targetValue = if (auroraBarCollapsed) 4.dp else 8.dp,
                                        animationSpec = tween(
                                            durationMillis = 250,
                                            easing = AURORA_EASING,
                                        ),
                                        label = "auroraBarContentPadding",
                                    )
                                    val navModifier = if (useAuroraBottomNav) {
                                        Modifier
                                            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                                            .padding(
                                                horizontal = animatedAuroraBarHPadding,
                                                vertical = animatedAuroraBarVPadding,
                                            )
                                            .auroraGlassNav(auroraColors!!, navBarShape, hazeState)
                                    } else {
                                        Modifier
                                    }
                                    val celestialUnlocked = rememberAuroraCelestialNavbarUnlocked()
                                    val celestialNavbar = useAuroraBottomNav && !isEInkMode && celestialUnlocked
                                    val celestialTabNavigator = if (celestialNavbar) LocalTabNavigator.current else null
                                    val celestialSelectedIndex = celestialTabNavigator?.let { tn ->
                                        navStyle.tabs.indexOfFirst { it::class == tn.current::class }
                                    } ?: -1
                                    val auroraSelectedIndex = navStyle.tabs.indexOfFirst {
                                        it::class ==
                                            tabNavigator.current::class
                                    }
                                    // En AMBAS barras (Aurora y Clásica), Títulos no es un
                                    // ítem normal: lo sustituye la píldora Anime/Manga al
                                    // final. "Más" tampoco está en ninguna barra: se unió al
                                    // botón ⋮ de arriba de cada pantalla principal, que abre
                                    // la pantalla de Más. Cada barra queda con 4 huecos:
                                    // Inicio, Actualiz., Explorar y la píldora. La píldora
                                    // deslizante sigue los índices de la barra visible.
                                    val bottomBarTabs = navStyle.tabs.filter {
                                        it::class != AnimeLibraryTab::class && it::class != MoreTab::class
                                    }
                                    val auroraBarSelectedIndex = bottomBarTabs.indexOfFirst {
                                        it::class == tabNavigator.current::class
                                    }
                                    // Altura animada de la barra Aurora: 64 dp expandida,
                                    // 48 dp compacta (solo iconos). Solo se anima el
                                    // subárbol de la barra; la clásica queda en 80 dp.
                                    val animatedAuroraBarHeight by animateDpAsState(
                                        targetValue = auroraBarHeight,
                                        animationSpec = tween(
                                            durationMillis = 250,
                                            easing = AURORA_EASING,
                                        ),
                                        label = "auroraBarHeight",
                                    )
                                    val bottomBarHeight = if (useAuroraBottomNav) {
                                        animatedAuroraBarHeight
                                    } else {
                                        80.dp
                                    }
                                    val slidingPillTarget = if (useAuroraBottomNav) {
                                        auroraBarSelectedIndex
                                    } else {
                                        auroraSelectedIndex
                                    }
                                    val slidingPillIndex = remember { Animatable(slidingPillTarget.toFloat()) }
                                    LaunchedEffect(slidingPillTarget) {
                                        slidingPillIndex.animateTo(
                                            targetValue = slidingPillTarget.toFloat(),
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow,
                                            ),
                                        )
                                    }
                                    val circuitNavbar = !isEInkMode && rememberLatticeCircuitNavbarUnlocked()
                                    val decoratedNavModifierBase = if (celestialNavbar && auroraColors != null) {
                                        navModifier.auroraCelestialBar(
                                            accent = auroraColors.accent,
                                            accentVariant = auroraColors.accentVariant,
                                            isDark = auroraColors.isDark,
                                            selectedIndex = celestialSelectedIndex,
                                            tabCount = navStyle.tabs.size,
                                        )
                                    } else {
                                        navModifier
                                    }
                                    val decoratedNavModifier = if (circuitNavbar) {
                                        decoratedNavModifierBase.latticeCircuitBar()
                                    } else {
                                        decoratedNavModifierBase
                                    }

                                    if (isEInkMode) {
                                        if (showBottomNav) {
                                            NavigationBar(
                                                containerColor = navContainerColor,
                                                contentColor = if (useAuroraBottomNav) {
                                                    auroraColors!!.textPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.contentColorFor(navContainerColor)
                                                },
                                                shadowElevation = navShadowElevation,
                                                tonalElevation = navTonalElevation,
                                                windowInsets = if (useAuroraBottomNav) {
                                                    WindowInsets(
                                                        0,
                                                    )
                                                } else {
                                                    NavigationBarDefaults.windowInsets
                                                },
                                                modifier = decoratedNavModifier,
                                                shape = navBarShape,
                                                contentPadding = if (useAuroraBottomNav) {
                                                    PaddingValues(horizontal = animatedAuroraBarContentPadding)
                                                } else {
                                                    PaddingValues(0.dp)
                                                },
                                                height = bottomBarHeight,
                                            ) {
                                                if (useAuroraBottomNav) {
                                                    bottomBarTabs.forEachIndexed { index, tab ->
                                                        NavigationBarItem(
                                                            tab,
                                                            useAuroraBottomNav,
                                                            index,
                                                            slidingPillIndex,
                                                            celestialUnlocked,
                                                            auroraColors,
                                                            showLabels = !auroraBarCollapsed,
                                                            compact = auroraBarCollapsed,
                                                        )
                                                    }
                                                    LibraryMediaPill(
                                                        auroraColors = auroraColors!!,
                                                        compact = auroraBarCollapsed,
                                                    )
                                                } else {
                                                    bottomBarTabs.forEachIndexed { index, tab ->
                                                        NavigationBarItem(
                                                            tab,
                                                            useAuroraBottomNav,
                                                            index,
                                                            slidingPillIndex,
                                                            celestialUnlocked,
                                                            auroraColors,
                                                        )
                                                    }
                                                    ClassicLibraryMediaPill()
                                                }
                                            }
                                        }
                                    } else {
                                        AnimatedVisibility(
                                            visible = showBottomNav,
                                            enter = expandVertically(expandFrom = Alignment.Bottom),
                                            exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
                                        ) {
                                            NavigationBar(
                                                containerColor = navContainerColor,
                                                contentColor = if (useAuroraBottomNav) {
                                                    auroraColors!!.textPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.contentColorFor(navContainerColor)
                                                },
                                                shadowElevation = navShadowElevation,
                                                tonalElevation = navTonalElevation,
                                                windowInsets = if (useAuroraBottomNav) {
                                                    WindowInsets(
                                                        0,
                                                    )
                                                } else {
                                                    NavigationBarDefaults.windowInsets
                                                },
                                                modifier = decoratedNavModifier,
                                                shape = navBarShape,
                                                contentPadding = if (useAuroraBottomNav) {
                                                    PaddingValues(horizontal = animatedAuroraBarContentPadding)
                                                } else {
                                                    PaddingValues(0.dp)
                                                },
                                                height = bottomBarHeight,
                                            ) {
                                                if (useAuroraBottomNav) {
                                                    bottomBarTabs.forEachIndexed { index, tab ->
                                                        NavigationBarItem(
                                                            tab,
                                                            useAuroraBottomNav,
                                                            index,
                                                            slidingPillIndex,
                                                            celestialUnlocked,
                                                            auroraColors,
                                                            showLabels = !auroraBarCollapsed,
                                                            compact = auroraBarCollapsed,
                                                        )
                                                    }
                                                    LibraryMediaPill(
                                                        auroraColors = auroraColors!!,
                                                        compact = auroraBarCollapsed,
                                                    )
                                                } else {
                                                    bottomBarTabs.forEachIndexed { index, tab ->
                                                        NavigationBarItem(
                                                            tab,
                                                            useAuroraBottomNav,
                                                            index,
                                                            slidingPillIndex,
                                                            celestialUnlocked,
                                                            auroraColors,
                                                        )
                                                    }
                                                    ClassicLibraryMediaPill()
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            contentWindowInsets = WindowInsets(0),
                        ) { contentPadding ->
                            val layoutDirection = LocalLayoutDirection.current
                            Box(
                                modifier = Modifier
                                    // La conexión de colapso de la barra Aurora
                                    // escucha el scroll del contenido (no consume
                                    // nada, no interfiere con el scroll).
                                    .nestedScroll(auroraBarScrollConnection)
                                    .padding(
                                        top = contentPadding.calculateTopPadding(),
                                        start = contentPadding.calculateStartPadding(layoutDirection),
                                        end = contentPadding.calculateEndPadding(layoutDirection),
                                    )
                                    .consumeWindowInsets(contentPadding)
                                    .hazeSource(hazeState),
                            ) {
                                CompositionLocalProvider(
                                    LocalHostScaffoldContentPadding provides PaddingValues(
                                        bottom = contentPadding.calculateBottomPadding(),
                                    ),
                                ) {
                                    if (resolvedTransitionMode == ResolvedNavigationTransitionMode.NONE) {
                                        val currentTab = tabNavigator.current
                                        tabNavigator.saveableState(key = "currentTab", currentTab) {
                                            currentTab.Content()
                                        }
                                    } else {
                                        AnimatedContent(
                                            targetState = tabNavigator.current,
                                            transitionSpec = {
                                                when (resolvedTransitionMode) {
                                                    ResolvedNavigationTransitionMode.NONE -> {
                                                        EnterTransition.None togetherWith ExitTransition.None
                                                    }
                                                    ResolvedNavigationTransitionMode.LEGACY -> {
                                                        materialFadeThroughIn(
                                                            initialScale = 1f,
                                                            durationMillis = TAB_FADE_DURATION,
                                                        ) togetherWith
                                                            materialFadeThroughOut(durationMillis = TAB_FADE_DURATION)
                                                    }
                                                    ResolvedNavigationTransitionMode.MODERN -> {
                                                        val direction = tabDirection(
                                                            initialTab = initialState,
                                                            targetTab = targetState,
                                                            currentMoreTab = currentMoreTab,
                                                            navStyle = navStyle,
                                                        )
                                                        val enter = slideInHorizontally(
                                                            animationSpec = tween(
                                                                durationMillis = TAB_MODERN_ENTER_DURATION,
                                                                easing = AURORA_EASING,
                                                            ),
                                                            initialOffsetX = { width -> direction * (width / 4) },
                                                        ) + fadeIn(
                                                            animationSpec = tween(
                                                                durationMillis = TAB_MODERN_ENTER_DURATION,
                                                                easing = AURORA_EASING,
                                                            ),
                                                        )
                                                        val exit = slideOutHorizontally(
                                                            animationSpec = tween(
                                                                durationMillis = TAB_MODERN_EXIT_DURATION,
                                                                easing = AURORA_EASING,
                                                            ),
                                                            targetOffsetX = { width -> -direction * (width / 5) },
                                                        ) + fadeOut(
                                                            animationSpec = tween(
                                                                durationMillis = TAB_MODERN_EXIT_DURATION,
                                                                easing = AURORA_EASING,
                                                            ),
                                                        )
                                                        (enter togetherWith exit).apply {
                                                            targetContentZIndex = 1f
                                                        }
                                                    }
                                                }
                                            },
                                            label = "tabContent",
                                        ) { currentTab ->
                                            tabNavigator.saveableState(key = "currentTab", currentTab) {
                                                currentTab.Content()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Overlay slot rendered OUTSIDE the hazeSource content above:
                        // overlays hosted here (e.g. the nickname editor) can use
                        // haze blur to blur the real home content behind their surface.
                        homeOverlayContent.value?.invoke()
                    }
                }

                val goToStartScreen = {
                    tabNavigator.current = resolveHomeStartTab(
                        defaultTab = defaultTab,
                        currentMoreTab = currentMoreTab,
                    )
                }
                BackHandler(
                    enabled = shouldHandleBackInHome(
                        currentTab = tabNavigator.current,
                        defaultTab = defaultTab,
                        currentMoreTab = currentMoreTab,
                    ),
                    onBack = goToStartScreen,
                )

                LaunchedEffect(Unit) {
                    if (startScreen == StartScreen.NOVEL) {
                        AnimeLibraryTab.showNovelSection()
                    }
                    launch {
                        librarySearchEvent.receiveAsFlow().collectLatest { request ->
                            // C1/C2/C3: land on the library tab that hosts the requested media in
                            // this theme, then hand the query to the matching section. The old
                            // routing sent to tab channels that might have no receiver (query
                            // silently lost) or always targeted the anime model.
                            val aurora = uiPreferences.appTheme().get().isAuroraStyle
                            val targetTab = when (request.media) {
                                LibrarySearchMedia.Manga -> resolveLibraryTabForOpenTab(aurora)
                                LibrarySearchMedia.Anime, LibrarySearchMedia.Novel -> AnimeLibraryTab
                            }
                            tabNavigator.current = targetTab
                            when {
                                targetTab == AnimeLibraryTab -> when (request.media) {
                                    LibrarySearchMedia.Anime -> AnimeLibraryTab.search(request.query)
                                    LibrarySearchMedia.Manga -> AnimeLibraryTab.searchManga(request.query)
                                    LibrarySearchMedia.Novel -> AnimeLibraryTab.searchNovel(request.query)
                                }
                                else -> MangaLibraryTab.search(request.query)
                            }
                        }
                    }
                    launch {
                        openTabEvent.receiveAsFlow().collectLatest {
                            tabNavigator.current = when (it) {
                                is Tab.AnimeLib -> AnimeLibraryTab
                                is Tab.Library -> resolveLibraryTabForOpenTab(isAuroraTheme)
                                is Tab.NovelLib -> AnimeLibraryTab
                                is Tab.Updates -> UpdatesTab
                                is Tab.History -> HistoriesTab
                                is Tab.Browse -> {
                                    if (it.toExtensions) {
                                        // BFEED-24: manga/anime-only routing - when the
                                        // requested section is disabled the consumer dropped
                                        // the event and the shortcut silently did nothing;
                                        // novels had no route at all. Fall back across the
                                        // visible sections.
                                        val showManga = uiPreferences.showMangaSection().get()
                                        val showAnime = uiPreferences.showAnimeSection().get()
                                        val showNovel = uiPreferences.showNovelSection().get()
                                        when {
                                            !it.anime && showManga -> BrowseTab.showExtension()
                                            it.anime && showAnime -> BrowseTab.showAnimeExtension()
                                            showManga -> BrowseTab.showExtension()
                                            showAnime -> BrowseTab.showAnimeExtension()
                                            showNovel -> BrowseTab.showNovelExtension()
                                        }
                                    }
                                    BrowseTab
                                }
                                is Tab.More -> MoreTab
                                is Tab.HomeHub -> HomeHubTab
                            }
                            if (it is Tab.NovelLib) {
                                AnimeLibraryTab.showNovelSection()
                            }
                            if (it is Tab.Library && isAuroraTheme) {
                                AnimeLibraryTab.showMangaSection()
                            }

                            if (it is Tab.AnimeLib && it.animeIdToOpen != null) {
                                navigator.push(AnimeScreen(it.animeIdToOpen))
                            }
                            if (it is Tab.Library && it.mangaIdToOpen != null) {
                                navigator.push(MangaScreen(it.mangaIdToOpen))
                            }
                            if (it is Tab.NovelLib && it.novelIdToOpen != null) {
                                navigator.push(eu.kanade.tachiyomi.ui.entries.novel.NovelScreen(it.novelIdToOpen))
                            }
                            if (it is Tab.More && it.toDownloads) {
                                navigator.push(DownloadsTab)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RowScope.NavigationBarItem(
        tab: eu.kanade.presentation.util.Tab,
        useAuroraBottomNav: Boolean,
        index: Int,
        slidingPillIndex: Animatable<Float, AnimationVector1D>,
        celestialHalo: Boolean,
        auroraColors: AuroraColors?,
        showLabels: Boolean = true,
        compact: Boolean = false,
    ) {
        if (useAuroraBottomNav) {
            AuroraNavigationBarItem(tab, index, slidingPillIndex, celestialHalo, auroraColors!!, showLabels, compact)
            return
        }

        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        val appHaptics = LocalAppHaptics.current

        // AMOLED: sin píldora gris detrás del icono seleccionado (el
        // indicatorColor por defecto es secondaryContainer, gris en
        // Monocromo). La selección se distingue por el icono relleno del
        // vector animado + el color de acento, no por un fondo gris.
        val amoled by uiPreferences.themeDarkAmoled().collectAsState()
        val colors = if (amoled && isSystemInDarkTheme()) {
            NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = Color.Transparent,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            )
        } else {
            NavigationBarItemDefaults.colors()
        }

        NavigationBarItem(
            modifier = Modifier.coachAnchorForTab(tab),
            selected = selected,
            onClick = {
                appHaptics.tap()
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab, selected) },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = true,
            colors = colors,
        )
    }

    @Composable
    private fun RowScope.AuroraNavigationBarItem(
        tab: eu.kanade.presentation.util.Tab,
        index: Int,
        slidingPillIndex: Animatable<Float, AnimationVector1D>,
        celestialHalo: Boolean,
        auroraColors: AuroraColors,
        showLabels: Boolean = true,
        compact: Boolean = false,
    ) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        val appHaptics = LocalAppHaptics.current
        val interactionSource = remember { MutableInteractionSource() }
        val iconColor = if (selected) {
            auroraColors.accent
        } else {
            auroraColors.textSecondary.copy(alpha = if (auroraColors.isDark) 0.72f else 0.78f)
        }
        val labelColor = if (selected) {
            auroraColors.accent
        } else {
            auroraColors.textSecondary.copy(alpha = if (auroraColors.isDark) 0.82f else 0.88f)
        }
        val iconShape = RoundedCornerShape(999.dp)
        val tabTitle = tab.options.title
        val isPressed by interactionSource.collectIsPressedAsState()
        val pressScale by animateFloatAsState(
            targetValue = if (isPressed) 0.96f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "auroraNavItemPressScale",
        )
        val itemWidthPx = remember { mutableStateOf(0f) }
        // Al colapsar, el hueco del icono se estrecha (12→6 dp) para que
        // quepan los 4 huecos en la cápsula más angosta; el icono sigue
        // en 21 dp, solo se recorta el aire a su alrededor.
        val iconBoxHPadding by animateDpAsState(
            targetValue = if (compact) 6.dp else 12.dp,
            animationSpec = tween(durationMillis = 250, easing = AURORA_EASING),
            label = "auroraNavItemIconPadding",
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .coachAnchorForTab(tab)
                .fillMaxHeight()
                .onSizeChanged { itemWidthPx.value = it.width.toFloat() }
                .scale(pressScale)
                .padding(horizontal = 1.dp)
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        appHaptics.tap()
                        if (!selected) {
                            tabNavigator.current = tab
                        } else {
                            scope.launch { tab.onReselect(navigator) }
                        }
                    },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = tabTitle
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (selected) {
                                Modifier.auroraCelestialHalo(
                                    accent = auroraColors.accent,
                                    accentVariant = auroraColors.accentVariant,
                                    isDark = auroraColors.isDark,
                                    shape = iconShape,
                                    enabled = celestialHalo,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .auroraSlidingPill(
                            slidingPillIndex = slidingPillIndex,
                            itemIndex = index,
                            itemWidthPx = itemWidthPx,
                            accent = auroraColors.accent,
                            accentVariant = auroraColors.accentVariant,
                            isDark = auroraColors.isDark,
                            isAmoled = auroraColors.isAmoled,
                        )
                        .padding(horizontal = iconBoxHPadding, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides iconColor) {
                        NavigationIconItem(
                            tab = tab,
                            selected = selected,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }

                // El nombre se oculta con fundido + encogido vertical cuando
                // la barra se compacta al bajar; el icono queda centrado
                // porque la columna está centrada en el hueco completo.
                AnimatedVisibility(
                    visible = showLabels,
                    enter = fadeIn(animationSpec = tween(durationMillis = 200)) +
                        expandVertically(
                            animationSpec = tween(durationMillis = 250, easing = AURORA_EASING),
                        ),
                    exit = fadeOut(animationSpec = tween(durationMillis = 150)) +
                        shrinkVertically(
                            animationSpec = tween(durationMillis = 250, easing = AURORA_EASING),
                        ),
                ) {
                    Text(
                        text = tab.options.title,
                        color = labelColor,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.82f,
                        ),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    /**
     * Píldora Anime/Manga de la barra inferior Aurora (fotos de referencia).
     *
     * Sustituye al ítem de Títulos en la barra Aurora: a la izquierda el
     * icono de película (Anime) y a la derecha el de libro (Manga). Es el
     * ÚNICO control para cambiar Anime/Manga — los selectores superiores de
     * Explorar y Títulos se eliminaron — y su estado se refleja en toda la
     * app porque escribe las preferencias compartidas browseLastSection y
     * libraryLastSection y encamina la sección a la biblioteca.
     *
     * Al tocar un lado cambia el modo y abre la biblioteca en esa sección;
     * si ya se está en la biblioteca, solo cambia la sección. El lado
     * seleccionado lleva el icono relleno sobre turquesa; el otro, el icono
     * en contorno. La etiqueta bajo la píldora muestra el modo actual.
     */
    private val mediaPillTurquoise = Color(0xFF2DD4BF)

    /**
     * Estado compartido de la píldora Anime/Manga: la misma lógica y el
     * mismo comportamiento en la barra Aurora, la barra Clásica y el rail
     * de tablets. Solo cambian los colores y si se compacta.
     */
    private data class MediaPillState(
        val currentSection: AnimeLibraryTab.Section?,
        val pillLabel: String,
        val isLibraryTab: Boolean,
        val showAnimeSection: Boolean,
        val showMangaSection: Boolean,
        val selectMedia: (AnimeLibraryTab.Section) -> Unit,
    )

    @Composable
    private fun rememberMediaPillState(): MediaPillState {
        val tabNavigator = LocalTabNavigator.current
        val appHaptics = LocalAppHaptics.current
        val userProfilePreferences = Injekt.get<UserProfilePreferences>()
        val librarySection by userProfilePreferences.libraryLastSection().collectAsState()
        val showAnimeSection by uiPreferences.showAnimeSection().collectAsState()
        val showMangaSection by uiPreferences.showMangaSection().collectAsState()

        val currentSection: AnimeLibraryTab.Section? = when {
            librarySection.equals("manga", ignoreCase = true) && showMangaSection ->
                AnimeLibraryTab.Section.Manga
            librarySection.equals("anime", ignoreCase = true) && showAnimeSection ->
                AnimeLibraryTab.Section.Anime
            librarySection.equals("novel", ignoreCase = true) ->
                AnimeLibraryTab.Section.Novel
            showAnimeSection -> AnimeLibraryTab.Section.Anime
            showMangaSection -> AnimeLibraryTab.Section.Manga
            else -> null
        }
        val pillLabel = when (currentSection) {
            AnimeLibraryTab.Section.Anime -> stringResource(AYMR.strings.label_anime)
            AnimeLibraryTab.Section.Manga -> stringResource(AYMR.strings.label_manga)
            AnimeLibraryTab.Section.Novel -> stringResource(AYMR.strings.label_novel)
            null -> stringResource(AYMR.strings.label_anime)
        }
        val isLibraryTab = tabNavigator.current::class == AnimeLibraryTab::class

        // Lambda recordada: su identidad solo cambia si cambia la sección
        // o la pestaña, así que el layout no se recompone por otros motivos.
        val selectMedia: (AnimeLibraryTab.Section) -> Unit = remember(currentSection, isLibraryTab) {
            { section ->
                if (!(isLibraryTab && currentSection == section)) {
                    appHaptics.tap()
                    // TODO SINCRÓNICO ANTES DE NAVEGAR (fix del "jug" al tocar
                    // Anime/Manga desde Inicio/Actualizaciones): antes las
                    // escrituras y el requestSection iban dentro de un
                    // scope.launch y la navegación ocurría en el mismo turno,
                    // así que la biblioteca se componía leyendo la sección
                    // VIEJA (el pager arrancaba en lo último seleccionado) y
                    // luego saltaba a la nueva. Ahora la preferencia y la
                    // intención de ruta quedan fijadas antes de cambiar de
                    // pestaña, y la biblioteca arranca directamente en la
                    // sección tocada, sin rebote.
                    userProfilePreferences.libraryLastSection()
                        .set(section.name.lowercase(Locale.ROOT))
                    userProfilePreferences.browseLastSection().set(section.name)
                    AnimeLibraryTab.requestSectionNow(section)
                    if (!isLibraryTab) {
                        tabNavigator.current = AnimeLibraryTab
                    }
                }
            }
        }

        return MediaPillState(
            currentSection = currentSection,
            pillLabel = pillLabel,
            isLibraryTab = isLibraryTab,
            showAnimeSection = showAnimeSection,
            showMangaSection = showMangaSection,
            selectMedia = selectMedia,
        )
    }

    private data class MediaPillColors(
        val background: Color,
        val border: Color,
        val unselectedIcon: Color,
        val selectedLabel: Color,
        val unselectedLabel: Color,
    )

    @Composable
    private fun rememberMediaPillColors(auroraColors: AuroraColors?): MediaPillColors {
        return if (auroraColors != null) {
            MediaPillColors(
                background = if (auroraColors.isAmoled) {
                    Color.Black
                } else {
                    auroraColors.accent.copy(alpha = 0.14f)
                },
                border = if (auroraColors.isDark) {
                    Color.White.copy(alpha = 0.12f)
                } else {
                    auroraColors.accent.copy(alpha = 0.16f)
                },
                // En Aurora el icono sin seleccionar es el color de texto
                // principal (igual que antes: LocalContentColor de la barra).
                unselectedIcon = auroraColors.textPrimary,
                selectedLabel = mediaPillTurquoise,
                unselectedLabel = auroraColors.textSecondary,
            )
        } else {
            val amoledPref by uiPreferences.themeDarkAmoled().collectAsState()
            val amoled = amoledPref && isSystemInDarkTheme()
            MediaPillColors(
                background = if (amoled) {
                    Color.Black
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = if (amoled) {
                    Color.White.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                },
                unselectedIcon = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedLabel = mediaPillTurquoise,
                unselectedLabel = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    /**
     * Dibujo común de la píldora: dos lados (película = Anime, libro =
     * Manga) y la etiqueta del modo debajo. `compact` es la versión
     * pequeña de la barra Aurora al bajar: menos alto (26 dp en vez de
     * 32 dp), menos padding y sin etiqueta. Los iconos NO se achican:
     * quedan en 21 dp siempre; solo la cápsula se achica.
     */
    @Composable
    private fun MediaPillLayout(
        modifier: Modifier,
        state: MediaPillState,
        colors: MediaPillColors,
        compact: Boolean,
    ) {
        // Alturas/padding animados: solo se re-mide el subárbol de la
        // píldora durante los 250 ms de la animación.
        val pillHeight by animateDpAsState(
            targetValue = if (compact) 26.dp else 32.dp,
            animationSpec = tween(durationMillis = 250, easing = AURORA_EASING),
            label = "mediaPillHeight",
        )
        val pillPadding by animateDpAsState(
            targetValue = if (compact) 2.dp else 3.dp,
            animationSpec = tween(durationMillis = 250, easing = AURORA_EASING),
            label = "mediaPillPadding",
        )
        // Tamaño fijo: al colapsar la cápsula los iconos mantienen su
        // tamaño; solo se achica la píldora que los contiene.
        val pillIconSize = 21.dp

        Box(
            modifier = Modifier
                .coachAnchorForTab(AnimeLibraryTab)
                .then(modifier),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .height(pillHeight)
                        .background(colors.background, RoundedCornerShape(999.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(999.dp))
                        .padding(pillPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(LocalContentColor provides colors.unselectedIcon) {
                        MediaPillSide(
                            selected = state.currentSection == AnimeLibraryTab.Section.Anime,
                            enabled = state.showAnimeSection,
                            iconSelected = Icons.Filled.Movie,
                            iconUnselected = Icons.Outlined.Movie,
                            contentDescription = stringResource(AYMR.strings.label_anime),
                            iconSize = pillIconSize,
                            onClick = { state.selectMedia(AnimeLibraryTab.Section.Anime) },
                        )
                        MediaPillSide(
                            selected = state.currentSection == AnimeLibraryTab.Section.Manga,
                            enabled = state.showMangaSection,
                            iconSelected = Icons.Filled.MenuBook,
                            iconUnselected = Icons.Outlined.MenuBook,
                            contentDescription = stringResource(AYMR.strings.label_manga),
                            iconSize = pillIconSize,
                            onClick = { state.selectMedia(AnimeLibraryTab.Section.Manga) },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = !compact,
                    enter = fadeIn(animationSpec = tween(durationMillis = 200)) +
                        expandVertically(
                            animationSpec = tween(durationMillis = 250, easing = AURORA_EASING),
                        ),
                    exit = fadeOut(animationSpec = tween(durationMillis = 150)) +
                        shrinkVertically(
                            animationSpec = tween(durationMillis = 250, easing = AURORA_EASING),
                        ),
                ) {
                    Text(
                        text = state.pillLabel,
                        color = if (state.currentSection == AnimeLibraryTab.Section.Novel ||
                            state.currentSection == null
                        ) {
                            colors.unselectedLabel
                        } else {
                            colors.selectedLabel
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.82f,
                        ),
                        fontWeight = if (state.isLibraryTab) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    @Composable
    private fun RowScope.LibraryMediaPill(auroraColors: AuroraColors, compact: Boolean) {
        MediaPillLayout(
            modifier = Modifier
                .weight(1.8f)
                .fillMaxHeight()
                .padding(horizontal = 1.dp),
            state = rememberMediaPillState(),
            colors = rememberMediaPillColors(auroraColors),
            compact = compact,
        )
    }

    @Composable
    private fun RowScope.ClassicLibraryMediaPill() {
        MediaPillLayout(
            modifier = Modifier
                .weight(1.8f)
                .fillMaxHeight()
                .padding(horizontal = 1.dp),
            state = rememberMediaPillState(),
            colors = rememberMediaPillColors(null),
            compact = false,
        )
    }

    @Composable
    private fun RailMediaPill(auroraColors: AuroraColors?) {
        MediaPillLayout(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            state = rememberMediaPillState(),
            colors = rememberMediaPillColors(auroraColors),
            compact = false,
        )
    }

    @Composable
    private fun RowScope.MediaPillSide(
        selected: Boolean,
        enabled: Boolean,
        iconSelected: ImageVector,
        iconUnselected: ImageVector,
        contentDescription: String,
        iconSize: Dp = 21.dp,
        onClick: () -> Unit,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) mediaPillTurquoise else Color.Transparent)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) iconSelected else iconUnselected,
                contentDescription = null,
                tint = if (selected) {
                    Color.Black
                } else {
                    LocalContentColor.current.copy(alpha = 0.8f)
                },
                modifier = Modifier.size(iconSize),
            )
        }
    }

    /**
     * Единая плашка активной вкладки: рисуется в координатах иконки каждого
     * айтема и плавно «переезжает» между ними по горизонтали. Каждый айтем
     * рисует плашку только пока её центр находится в его зоне
     * [index - 0.5, index + 0.5), поэтому между соседями нет ни двойной
     * отрисовки, ни разрыва.
     */
    @Composable
    private fun Modifier.auroraSlidingPill(
        slidingPillIndex: Animatable<Float, AnimationVector1D>,
        itemIndex: Int,
        itemWidthPx: State<Float>,
        accent: Color,
        accentVariant: Color,
        isDark: Boolean,
        isAmoled: Boolean = false,
    ): Modifier {
        val pillBrush = remember(accent, accentVariant, isDark, isAmoled) {
            if (isAmoled) {
                // AMOLED: la píldora deslizante es negra sólida con su borde
                // fino; el tinte del acento la volvía gris.
                SolidColor(Color.Black)
            } else {
                Brush.verticalGradient(
                    listOf(
                        if (isDark) accent.copy(alpha = 0.28f) else accent.copy(alpha = 0.18f),
                        if (isDark) accentVariant.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.78f),
                    ),
                )
            }
        }
        val borderColor = if (isDark) {
            Color.White.copy(alpha = 0.12f)
        } else {
            accent.copy(alpha = 0.16f)
        }
        return this.drawBehind {
            val delta = slidingPillIndex.value - itemIndex
            if (delta < -0.5f || delta >= 0.5f) return@drawBehind
            val corner = CornerRadius(size.height / 2f, size.height / 2f)
            val topLeft = Offset(delta * itemWidthPx.value, 0f)
            drawRoundRect(
                brush = pillBrush,
                topLeft = topLeft,
                size = size,
                cornerRadius = corner,
            )
            drawRoundRect(
                color = borderColor,
                topLeft = topLeft,
                size = size,
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }

    /**
     * Общее «стекло» Aurora-навигации: тень + clip + haze-blur + rim-light.
     * Одинаково для нижнего бара и бокового рейла, отличается только формой.
     */
    private fun Modifier.auroraGlassNav(
        colors: AuroraColors,
        shape: Shape,
        hazeState: HazeState,
    ): Modifier {
        val hazeStyle = HazeBlurStyle {
            backgroundColor(colors.background)
            colorEffects(listOf(HazeColorEffect.tint(colors.surface.copy(alpha = 0.65f))))
            blurRadius(24.dp)
            noiseFactor(0.12f)
        }
        return if (colors.isDark) {
            this
                .shadow(
                    elevation = 10.dp,
                    shape = shape,
                    // AMOLED: sin brillo blanco alrededor de la barra; el
                    // resplandor era otro gris visible sobre negro puro.
                    ambientColor = if (colors.isAmoled) Color.Transparent else Color.White.copy(alpha = 0.12f),
                    spotColor = if (colors.isAmoled) Color.Transparent else Color.White.copy(alpha = 0.08f),
                )
                .shadow(
                    elevation = 3.dp,
                    shape = shape,
                    ambientColor = if (colors.isAmoled) Color.Transparent else Color.White.copy(alpha = 0.18f),
                    spotColor = if (colors.isAmoled) Color.Transparent else Color.White.copy(alpha = 0.12f),
                )
                .clip(shape)
                // Opaque base under the frost: on first frames after navigation
                // (e.g. popping back to home) the haze layer has not captured
                // content yet, so without this the bar flashes transparent.
                .background(colors.background, shape)
                .hazeBlur(input = HazeInput.Sources(hazeState), style = hazeStyle)
                .border(
                    BorderStroke(width = 1.dp, brush = auroraMenuRimLightBrush(colors)),
                    shape = shape,
                )
        } else {
            this
                .shadow(elevation = 8.dp, shape = shape)
                .clip(shape)
                // Opaque base under the frost (see dark branch comment).
                .background(colors.background, shape)
                .hazeBlur(input = HazeInput.Sources(hazeState), style = hazeStyle)
                .border(
                    BorderStroke(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.80f),
                                Color.White.copy(alpha = 0.20f),
                            ),
                        ),
                    ),
                    shape = shape,
                )
        }
    }

    @Composable
    private fun AuroraNavigationRail(
        tabs: List<eu.kanade.presentation.util.Tab>,
        hazeState: HazeState,
        circuitDecoration: Boolean,
    ) {
        val auroraColors = AuroraTheme.colorsForCurrentTheme()
        val railShape = RoundedCornerShape(28.dp)
        val tabNavigator = LocalTabNavigator.current
        val celestialRail = rememberAuroraCelestialNavbarUnlocked()
        // En el rail vale lo mismo que en la barra inferior: sin "Más" y
        // Títulos es la píldora Anime/Manga. El índice seleccionado se
        // calcula sobre los huecos visibles (la píldora es el último).
        val railTabs = tabs.filter {
            it::class != AnimeLibraryTab::class && it::class != MoreTab::class
        }
        val selectedIndex = when {
            tabNavigator.current::class == AnimeLibraryTab::class -> railTabs.size
            else -> railTabs.indexOfFirst { it::class == tabNavigator.current::class }
        }
        val baseModifier = Modifier
            .windowInsetsPadding(NavigationRailDefaults.windowInsets)
            .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
        val glassModifier = baseModifier.auroraGlassNav(auroraColors, railShape, hazeState)
        val celestialModifier = if (celestialRail) {
            glassModifier.auroraCelestialRail(
                accent = auroraColors.accent,
                accentVariant = auroraColors.accentVariant,
                isDark = auroraColors.isDark,
                selectedIndex = selectedIndex,
                tabCount = railTabs.size + 1,
            )
        } else {
            glassModifier
        }
        val decoratedModifier = if (circuitDecoration) {
            celestialModifier.latticeCircuitRail()
        } else {
            celestialModifier
        }

        NavigationRail(
            modifier = decoratedModifier,
            containerColor = Color.Transparent,
            contentColor = auroraColors.textPrimary,
            windowInsets = WindowInsets(0),
        ) {
            railTabs.forEachIndexed { _, tab -> AuroraNavigationRailItem(tab, celestialRail, auroraColors) }
            RailMediaPill(auroraColors)
        }
    }

    @Composable
    private fun AuroraNavigationRailItem(
        tab: eu.kanade.presentation.util.Tab,
        celestialHalo: Boolean,
        auroraColors: AuroraColors,
    ) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        val appHaptics = LocalAppHaptics.current
        val interactionSource = remember { MutableInteractionSource() }
        val iconColor = if (selected) {
            auroraColors.accent
        } else {
            auroraColors.textSecondary.copy(alpha = if (auroraColors.isDark) 0.72f else 0.78f)
        }
        val labelColor = if (selected) {
            auroraColors.accent
        } else {
            auroraColors.textSecondary.copy(alpha = if (auroraColors.isDark) 0.82f else 0.88f)
        }
        val iconBackgroundBrush = if (selected) {
            if (auroraColors.isAmoled) {
                SolidColor(Color.Black)
            } else {
                Brush.verticalGradient(
                    listOf(
                        if (auroraColors.isDark) {
                            auroraColors.accent.copy(alpha = 0.28f)
                        } else {
                            auroraColors.accent.copy(alpha = 0.18f)
                        },
                    if (auroraColors.isDark) {
                        auroraColors.accentVariant.copy(alpha = 0.18f)
                    } else {
                        Color.White.copy(alpha = 0.78f)
                    },
                ),
            )
            }
        } else {
            null
        }
        val iconShape = RoundedCornerShape(999.dp)
        val tabTitle = tab.options.title

        Box(
            modifier = Modifier
                .coachAnchorForTab(tab)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        appHaptics.tap()
                        if (!selected) {
                            tabNavigator.current = tab
                        } else {
                            scope.launch { tab.onReselect(navigator) }
                        }
                    },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = tabTitle
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (selected) {
                                Modifier
                                    .auroraCelestialHalo(
                                        accent = auroraColors.accent,
                                        accentVariant = auroraColors.accentVariant,
                                        isDark = auroraColors.isDark,
                                        shape = iconShape,
                                        enabled = celestialHalo,
                                    )
                                    .background(iconBackgroundBrush!!, iconShape)
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (auroraColors.isDark) {
                                                Color.White.copy(alpha = 0.12f)
                                            } else {
                                                auroraColors.accent.copy(alpha = 0.16f)
                                            },
                                        ),
                                        iconShape,
                                    )
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides iconColor) {
                        NavigationIconItem(
                            tab = tab,
                            selected = selected,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }

                Text(
                    text = tab.options.title,
                    color = labelColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.82f,
                    ),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    @Composable
    fun NavigationRailItem(tab: eu.kanade.presentation.util.Tab) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        val appHaptics = LocalAppHaptics.current
        val theme by uiPreferences.appTheme().collectAsState()
        val isAurora = theme.isAuroraStyle

        val colors = if (isAurora) {
            val auroraColors = AuroraTheme.colors
            androidx.compose.material3.NavigationRailItemDefaults.colors(
                selectedIconColor = auroraColors.accent,
                selectedTextColor = auroraColors.accent,
                indicatorColor = auroraColors.accent.copy(alpha = 0.1f),
                unselectedIconColor = auroraColors.textSecondary,
                unselectedTextColor = auroraColors.textSecondary,
            )
        } else {
            androidx.compose.material3.NavigationRailItemDefaults.colors()
        }

        NavigationRailItem(
            selected = selected,
            onClick = {
                appHaptics.tap()
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab, selected) },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = true,
            colors = colors,
        )
    }

    @Composable
    private fun NavigationIconItem(
        tab: eu.kanade.presentation.util.Tab,
        selected: Boolean,
        modifier: Modifier = Modifier,
    ) {
        BadgedBox(
            badge = {
                when {
                    UpdatesTab::class.isInstance(tab) -> {
                        val count by produceState(initialValue = 0) {
                            val pref = Injekt.get<LibraryPreferences>()
                            combine(
                                pref.newAnimeUpdatesCount().changes(),
                                pref.newMangaUpdatesCount().changes(),
                                pref.newNovelUpdatesCount().changes(),
                            ) { countAnime, countManga, countNovel ->
                                countAnime + countManga + countNovel
                            }
                                .collectLatest { value = if (pref.newShowUpdatesCount().get()) it else 0 }
                        }
                        if (count > 0) {
                            Badge {
                                val desc = pluralStringResource(
                                    MR.plurals.notification_chapters_generic,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                    BrowseTab::class.isInstance(tab) -> {
                        val pref = Injekt.get<SourcePreferences>()
                        val seenCount by produceState(
                            initialValue = pref.browseExtensionUpdatesSeenCount().get(),
                        ) {
                            pref.browseExtensionUpdatesSeenCount().changes()
                                .collectLatest { value = it }
                        }
                        val count by produceState(initialValue = 0) {
                            combine(
                                pref.mangaExtensionUpdatesCount().changes(),
                                pref.animeExtensionUpdatesCount().changes(),
                                pref.novelExtensionUpdatesCount().changes(),
                            ) { mangaCount, animeCount, novelCount ->
                                ExtensionUpdateCounts.sum(mangaCount, animeCount, novelCount)
                            }
                                .collectLatest { value = it }
                        }
                        LaunchedEffect(selected, count) {
                            if (selected) {
                                pref.browseExtensionUpdatesSeenCount().set(count)
                            }
                        }
                        if (shouldShowBrowseExtensionBadge(selected, count, seenCount)) {
                            Badge {
                                val desc = pluralStringResource(
                                    MR.plurals.update_check_notification_ext_updates,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                }
            },
        ) {
            Icon(
                modifier = modifier,
                painter = tab.options.icon!!,
                contentDescription = tab.options.title,
                // TODO: https://issuetracker.google.com/u/0/issues/316327367
                tint = LocalContentColor.current,
            )
        }
    }

    enum class LibrarySearchMedia { Anime, Manga, Novel }

    data class LibrarySearchRequest(val media: LibrarySearchMedia, val query: String)

    /**
     * Routes a "search in library" request from the entry screens: lands on the library tab
     * hosting the requested media for the active theme and applies the query to the matching
     * section (C1/C2/C3).
     */
    suspend fun searchLibrary(media: LibrarySearchMedia, query: String) {
        librarySearchEvent.send(LibrarySearchRequest(media, query))
    }

    suspend fun openTab(tab: Tab) {
        openTabEvent.send(tab)
    }

    suspend fun showBottomNav(show: Boolean) {
        showBottomNavEvent.send(show)
    }

    sealed interface Tab {
        data class AnimeLib(val animeIdToOpen: Long? = null) : Tab
        data class Library(val mangaIdToOpen: Long? = null) : Tab
        data class NovelLib(val novelIdToOpen: Long? = null) : Tab
        data object Updates : Tab
        data object History : Tab
        data class Browse(val toExtensions: Boolean = false, val anime: Boolean = false) : Tab
        data class More(val toDownloads: Boolean) : Tab
        data object HomeHub : Tab
    }
}

internal fun shouldShowBrowseExtensionBadge(
    selected: Boolean,
    currentCount: Int,
    seenCount: Int,
): Boolean {
    return !selected && currentCount > 0 && currentCount != seenCount
}

internal fun resolveHomeStartTab(
    defaultTab: cafe.adriel.voyager.navigator.tab.Tab,
    currentMoreTab: cafe.adriel.voyager.navigator.tab.Tab,
): cafe.adriel.voyager.navigator.tab.Tab {
    return if (defaultTab != currentMoreTab) defaultTab else AnimeLibraryTab
}

internal fun resolveLibraryTabForOpenTab(isAuroraTheme: Boolean): eu.kanade.presentation.util.Tab {
    return if (isAuroraTheme) AnimeLibraryTab else MangaLibraryTab
}

internal fun shouldHandleBackInHome(
    currentTab: cafe.adriel.voyager.navigator.tab.Tab,
    defaultTab: cafe.adriel.voyager.navigator.tab.Tab,
    currentMoreTab: cafe.adriel.voyager.navigator.tab.Tab,
): Boolean {
    return (currentTab == currentMoreTab || currentTab != defaultTab) &&
        (currentTab != AnimeLibraryTab || defaultTab != currentMoreTab)
}

private fun tabDirection(
    initialTab: cafe.adriel.voyager.navigator.tab.Tab,
    targetTab: cafe.adriel.voyager.navigator.tab.Tab,
    currentMoreTab: cafe.adriel.voyager.navigator.tab.Tab,
    navStyle: eu.kanade.domain.ui.model.NavStyle,
): Int {
    val initialIndex = tabOrderIndex(initialTab, navStyle, currentMoreTab)
    val targetIndex = tabOrderIndex(targetTab, navStyle, currentMoreTab)
    return if (targetIndex >= initialIndex) 1 else -1
}

private fun tabOrderIndex(
    tab: cafe.adriel.voyager.navigator.tab.Tab,
    navStyle: eu.kanade.domain.ui.model.NavStyle,
    currentMoreTab: cafe.adriel.voyager.navigator.tab.Tab,
): Int {
    val visibleIndex = navStyle.tabs.indexOfFirst { it::class == tab::class }
    return when {
        visibleIndex >= 0 -> visibleIndex
        tab::class == currentMoreTab::class -> navStyle.tabs.size
        else -> navStyle.tabs.size + 1
    }
}

/**
 * Estado de colapso de la barra Aurora. Espejo del patrón de la
 * biblioteca ([eu.kanade.tachiyomi.ui.library.LibraryImmersiveChromeState]):
 * `isVisible = true` es la barra expandida (con nombres) y `false` la
 * compacta (solo iconos). El scroll se acumula hasta el umbral para que
 * un toque corto no haga parpadear la barra.
 */
private data class AuroraBarCollapseState(
    val isVisible: Boolean = true,
    val accumulatedScrollPx: Float = 0f,
)

private fun resolveAuroraBarCollapseState(
    currentState: AuroraBarCollapseState,
    scrollDeltaPx: Float,
    hideThresholdPx: Float,
): AuroraBarCollapseState {
    if (scrollDeltaPx == 0f) {
        return currentState
    }

    return if (currentState.isVisible) {
        if (scrollDeltaPx <= 0f) {
            currentState.copy(accumulatedScrollPx = 0f)
        } else {
            val accumulated = (currentState.accumulatedScrollPx + scrollDeltaPx)
                .coerceAtMost(hideThresholdPx)
            if (accumulated >= hideThresholdPx) {
                AuroraBarCollapseState(
                    isVisible = false,
                    accumulatedScrollPx = 0f,
                )
            } else {
                currentState.copy(accumulatedScrollPx = accumulated)
            }
        }
    } else {
        if (scrollDeltaPx >= 0f) {
            currentState.copy(accumulatedScrollPx = 0f)
        } else {
            val accumulated = (currentState.accumulatedScrollPx + scrollDeltaPx)
                .coerceAtLeast(-hideThresholdPx)
            if (accumulated <= -hideThresholdPx) {
                AuroraBarCollapseState(
                    isVisible = true,
                    accumulatedScrollPx = 0f,
                )
            } else {
                currentState.copy(accumulatedScrollPx = accumulated)
            }
        }
    }
}
