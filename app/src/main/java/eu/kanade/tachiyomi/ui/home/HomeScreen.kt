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
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemColors
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
import androidx.compose.ui.graphics.lerp
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
import eu.kanade.presentation.util.AuroraMotionTokens
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
                                            // arriba) y Títulos son dos iconos separados,
                                            // Anime y Manga.
                                            val railLibraryState =
                                                rememberLibrarySectionBarState(isAuroraTheme)
                                            navStyle.tabs.filter {
                                                it::class != AnimeLibraryTab::class && it::class != MoreTab::class
                                            }.fastForEach {
                                                NavigationRailItem(it)
                                            }
                                            listOfNotNull(
                                                AnimeLibraryTab.Section.Anime.takeIf {
                                                    railLibraryState.showAnimeSection
                                                },
                                                AnimeLibraryTab.Section.Manga.takeIf {
                                                    railLibraryState.showMangaSection
                                                },
                                            ).forEach { section ->
                                                LibrarySectionRailItem(
                                                    section = section,
                                                    state = railLibraryState,
                                                )
                                            }
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
                                    // V7.3: barra más compacta — iconos más pegados y
                                    // cápsula visiblemente más corta: padding exterior
                                    // 12→24 dp (expandida) y 56→64 dp (colapsada).
                                    val animatedAuroraBarHPadding by animateDpAsState(
                                        targetValue = if (auroraBarCollapsed) 64.dp else 24.dp,
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
                                    // Fluidez v4: el padding del hueco del icono se anima UNA
                                    // sola vez a nivel de barra y se pasa como valor a los 5
                                    // huecos. Antes cada hueco animaba el suyo y los 5 relojes
                                    // podían desfasarse un frame entre sí: la cápsula
                                    // "ondulaba" al colapsar. Un solo valor animado para
                                    // todos = colapso uniforme, sin tirones.
                                    // V7.3: hueco más estrecho (12→8 dp expandida, 6→4 dp
                                    // colapsada): la píldora deslizante, que mide icono +
                                    // este padding, queda más corta. El icono sigue en 21 dp.
                                    val animatedAuroraBarIconHPadding by animateDpAsState(
                                        targetValue = if (auroraBarCollapsed) 4.dp else 8.dp,
                                        animationSpec = tween(
                                            durationMillis = 250,
                                            easing = AURORA_EASING,
                                        ),
                                        label = "auroraBarIconHPadding",
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
                                    val auroraSelectedIndex = navStyle.tabs.indexOfFirst {
                                        it::class ==
                                            tabNavigator.current::class
                                    }
                                    // En AMBAS barras (Aurora y Clásica), Títulos no es un
                                    // ítem normal: lo sustituyen DOS iconos separados,
                                    // Anime y Manga, al final (v4; la píldora unificada
                                    // se eliminó porque daba tirones y saltos). "Más"
                                    // tampoco está en ninguna barra: se unió al botón ⋮
                                    // de arriba de cada pantalla principal, que abre la
                                    // pantalla de Más. Cada barra queda con 5 huecos:
                                    // Inicio, Actualiz., Explorar, Anime y Manga. La
                                    // píldora deslizante sigue los índices de la barra
                                    // visible (los iconos de biblioteca van después de
                                    // los de pestaña).
                                    val bottomBarTabs = navStyle.tabs.filter {
                                        it::class != AnimeLibraryTab::class && it::class != MoreTab::class
                                    }
                                    val librarySectionState = rememberLibrarySectionBarState(isAuroraTheme)
                                    val visibleLibrarySections = listOfNotNull(
                                        AnimeLibraryTab.Section.Anime.takeIf {
                                            librarySectionState.showAnimeSection
                                        },
                                        AnimeLibraryTab.Section.Manga.takeIf {
                                            librarySectionState.showMangaSection
                                        },
                                    )
                                    val auroraBarSelectedIndex = if (librarySectionState.isLibraryTab) {
                                        val sectionIndex = visibleLibrarySections.indexOf(
                                            librarySectionState.currentSection,
                                        )
                                        if (sectionIndex >= 0) bottomBarTabs.size + sectionIndex else -1
                                    } else {
                                        bottomBarTabs.indexOfFirst {
                                            it::class == tabNavigator.current::class
                                        }
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
                                    // V7.3: spring más rígido y rápido para
                                    // la píldora (~200 ms; antes StiffnessMediumLow,
                                    // ~400-600 ms): la píldora llegaba tarde y
                                    // el contenido cambiaba primero, lo que se
                                    // sentía tosco aunque no se perdieran frames.
                                    // V7.7 (lo de Nuvio): tween fijo de 220 ms en
                                    // vez de spring — un spring integra según los
                                    // cuadros que caen y puede saltar si se pierde
                                    // uno; un tween dura siempre 220 ms y la
                                    // píldora llega a la vez que el contenido.
                                    // La cápsula se mantiene (el usuario la quiere);
                                    // solo cambia cómo viaja. El dibujo de la
                                    // píldora ya era un único rectángulo redondeado
                                    // por ítem (drawBehind), así que no se toca.
                                    val slidingPillIndex = remember { Animatable(slidingPillTarget.toFloat()) }
                                    LaunchedEffect(slidingPillTarget) {
                                        slidingPillIndex.animateTo(
                                            targetValue = slidingPillTarget.toFloat(),
                                            animationSpec = tween(
                                                durationMillis = AuroraMotionTokens.NORMAL_MS,
                                                easing = AuroraMotionTokens.standardEasing,
                                            ),
                                        )
                                    }
                                    val circuitNavbar = !isEInkMode && rememberLatticeCircuitNavbarUnlocked()
                                    val decoratedNavModifierBase = if (celestialNavbar && auroraColors != null) {
                                        // La onda celestial sigue los huecos VISIBLES de la
                                        // barra (con Anime y Manga separados), no los de
                                        // navStyle.tabs (donde la biblioteca es un solo ítem).
                                        navModifier.auroraCelestialBar(
                                            accent = auroraColors.accent,
                                            accentVariant = auroraColors.accentVariant,
                                            isDark = auroraColors.isDark,
                                            selectedIndex = auroraBarSelectedIndex,
                                            tabCount = bottomBarTabs.size + visibleLibrarySections.size,
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
                                                            iconBoxHPadding = animatedAuroraBarIconHPadding,
                                                        )
                                                    }
                                                    visibleLibrarySections.forEachIndexed { offset, section ->
                                                        AuroraLibrarySectionBarItem(
                                                            section = section,
                                                            state = librarySectionState,
                                                            index = bottomBarTabs.size + offset,
                                                            slidingPillIndex = slidingPillIndex,
                                                            celestialHalo = celestialUnlocked,
                                                            auroraColors = auroraColors!!,
                                                            showLabels = !auroraBarCollapsed,
                                                            iconBoxHPadding = animatedAuroraBarIconHPadding,
                                                        )
                                                    }
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
                                                    visibleLibrarySections.forEach { section ->
                                                        ClassicLibrarySectionBarItem(
                                                            section = section,
                                                            state = librarySectionState,
                                                        )
                                                    }
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
                                                            iconBoxHPadding = animatedAuroraBarIconHPadding,
                                                        )
                                                    }
                                                    visibleLibrarySections.forEachIndexed { offset, section ->
                                                        AuroraLibrarySectionBarItem(
                                                            section = section,
                                                            state = librarySectionState,
                                                            index = bottomBarTabs.size + offset,
                                                            slidingPillIndex = slidingPillIndex,
                                                            celestialHalo = celestialUnlocked,
                                                            auroraColors = auroraColors!!,
                                                            showLabels = !auroraBarCollapsed,
                                                            iconBoxHPadding = animatedAuroraBarIconHPadding,
                                                        )
                                                    }
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
                                                    visibleLibrarySections.forEach { section ->
                                                        ClassicLibrarySectionBarItem(
                                                            section = section,
                                                            state = librarySectionState,
                                                        )
                                                    }
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
        iconBoxHPadding: Dp = 12.dp,
    ) {
        if (useAuroraBottomNav) {
            AuroraNavigationBarItem(tab, index, slidingPillIndex, celestialHalo, auroraColors!!, showLabels, iconBoxHPadding)
            return
        }

        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        val appHaptics = LocalAppHaptics.current

        val colors = classicNavItemColors()

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
        iconBoxHPadding: Dp = 12.dp,
    ) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        AuroraBarItemShell(
            title = tab.options.title,
            selected = selected,
            index = index,
            slidingPillIndex = slidingPillIndex,
            celestialHalo = celestialHalo,
            auroraColors = auroraColors,
            showLabels = showLabels,
            iconBoxHPadding = iconBoxHPadding,
            coachTab = tab,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = {
                NavigationIconItem(
                    tab = tab,
                    selected = selected,
                    modifier = Modifier.size(21.dp),
                )
            },
        )
    }

    /**
     * Cáscara visual común de un hueco de la barra Aurora: icono de 21 dp
     * (fijo, no se achica al colapsar) sobre la píldora deslizante, etiqueta
     * que se funde al colapsar y escala al pulsar. La usan los ítems de
     * pestaña y los dos iconos separados Anime/Manga.
     *
     * Fluidez: el colapso solo anima el subárbol de la barra (las alturas y
     * paddings son animateDpAsState leídos solo aquí) y el estado de colapso
     * se lee vía derivedStateOf, así que un cambio de scroll solo recompone
     * la barra, nunca el contenido.
     */
    @Composable
    private fun RowScope.AuroraBarItemShell(
        title: String,
        selected: Boolean,
        index: Int,
        slidingPillIndex: Animatable<Float, AnimationVector1D>,
        celestialHalo: Boolean,
        auroraColors: AuroraColors,
        showLabels: Boolean = true,
        iconBoxHPadding: Dp,
        coachTab: eu.kanade.presentation.util.Tab? = null,
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
    ) {
        val appHaptics = LocalAppHaptics.current
        val interactionSource = remember { MutableInteractionSource() }
        // V7.9: opacidad de la cápsula Aurora (Ajustes → Navegación).
        // 0 = cristal translúcido, 1 = color liso (negro en tema oscuro,
        // blanco en tema claro). El icono seleccionado pierde contraste
        // sobre el color liso (el acento turquesa sobre blanco se lee
        // mal), así que a partir del 50% se funde hacia el texto
        // principal (claro en tema oscuro, oscuro en tema claro); en
        // cristal queda el acento actual. Se aplica al instante.
        val capsuleOpacity by uiPreferences.auroraCapsuleOpacity().collectAsState()
        val capsuleOpacityF = (capsuleOpacity / 100f).coerceIn(0f, 1f)
        val iconColor = if (selected) {
            val solidFactor = ((capsuleOpacityF - 0.5f) / 0.5f).coerceIn(0f, 1f)
            lerp(auroraColors.accent, auroraColors.textPrimary, solidFactor)
        } else {
            auroraColors.textSecondary.copy(alpha = if (auroraColors.isDark) 0.72f else 0.78f)
        }
        val labelColor = if (selected) {
            auroraColors.accent
        } else {
            auroraColors.textSecondary.copy(alpha = if (auroraColors.isDark) 0.82f else 0.88f)
        }
        val iconShape = RoundedCornerShape(999.dp)
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
        // El padding del hueco (12→6 dp al colapsar) llega ya animado desde
        // la barra: un solo reloj para los 5 huecos, sin desfases. El icono
        // sigue en 21 dp; solo se recorta el aire a su alrededor.

        Box(
            modifier = Modifier
                .weight(1f)
                .then(if (coachTab != null) Modifier.coachAnchorForTab(coachTab) else Modifier)
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
                        onClick()
                    },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = title
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
                            isDark = auroraColors.isDark,
                            capsuleOpacity = capsuleOpacityF,
                        )
                        .padding(horizontal = iconBoxHPadding, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides iconColor) {
                        icon()
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
                        text = title,
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
     * Anime y Manga como DOS iconos totalmente separados en la barra (v4).
     *
     * La píldora unificada se eliminó: compartía estado entre los dos lados
     * (preferencias + intención de ruta + pestaña) y eso daba los tirones y
     * saltos que el usuario reportaba. Ahora cada icono es independiente y
     * navega directo a su biblioteca:
     * - Anime: AnimeLibraryTab en la sección Anime (en Aurora y en Clásico;
     *   en Clásico AnimeLibraryTab solo muestra anime).
     * - Manga: en Aurora, AnimeLibraryTab en la sección Manga; en Clásico,
     *   MangaLibraryTab (la biblioteca de manga separada).
     * La sección se fija de forma síncrona (preferencias + requestSectionNow)
     * ANTES de cambiar de pestaña, igual que hacía la píldora: la biblioteca
     * arranca en la sección tocada, sin rebote.
     */
    private data class LibrarySectionBarState(
        val currentSection: AnimeLibraryTab.Section?,
        val isLibraryTab: Boolean,
        val showAnimeSection: Boolean,
        val showMangaSection: Boolean,
        val selectSection: (AnimeLibraryTab.Section) -> Unit,
    )

    @Composable
    private fun rememberLibrarySectionBarState(isAuroraTheme: Boolean): LibrarySectionBarState {
        val tabNavigator = LocalTabNavigator.current
        val appHaptics = LocalAppHaptics.current
        val userProfilePreferences = Injekt.get<UserProfilePreferences>()
        val librarySection by userProfilePreferences.libraryLastSection().collectAsState()
        val showAnimeSection by uiPreferences.showAnimeSection().collectAsState()
        val showMangaSection by uiPreferences.showMangaSection().collectAsState()

        val currentTabClass = tabNavigator.current::class
        val currentSection: AnimeLibraryTab.Section? = when {
            currentTabClass == MangaLibraryTab::class -> AnimeLibraryTab.Section.Manga
            !isAuroraTheme && currentTabClass == AnimeLibraryTab::class -> AnimeLibraryTab.Section.Anime
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
        val isLibraryTab = currentTabClass == AnimeLibraryTab::class ||
            currentTabClass == MangaLibraryTab::class

        // Lambda recordada: su identidad solo cambia si cambia la sección,
        // la pestaña o el tema, así que el layout no se recompone por otros
        // motivos. La preferencia y la intención de ruta quedan fijadas
        // ANTES de cambiar de pestaña (mismo turno del clic): antes iban
        // dentro de un launch y la biblioteca se componía leyendo la sección
        // VIEJA y luego saltaba a la nueva (el "jug" de la píldora).
        val selectSection: (AnimeLibraryTab.Section) -> Unit =
            remember(currentSection, isLibraryTab, isAuroraTheme) {
                { section ->
                    val targetTab = if (section == AnimeLibraryTab.Section.Manga) {
                        resolveLibraryTabForOpenTab(isAuroraTheme)
                    } else {
                        AnimeLibraryTab
                    }
                    val alreadyThere = tabNavigator.current::class == targetTab::class &&
                        currentSection == section
                    if (!alreadyThere) {
                        appHaptics.tap()
                        userProfilePreferences.libraryLastSection()
                            .set(section.name.lowercase(Locale.ROOT))
                        userProfilePreferences.browseLastSection().set(section.name)
                        AnimeLibraryTab.requestSectionNow(section)
                        if (tabNavigator.current::class != targetTab::class) {
                            tabNavigator.current = targetTab
                        }
                    }
                }
            }

        return LibrarySectionBarState(
            currentSection = currentSection,
            isLibraryTab = isLibraryTab,
            showAnimeSection = showAnimeSection,
            showMangaSection = showMangaSection,
            selectSection = selectSection,
        )
    }

    @Composable
    private fun RowScope.AuroraLibrarySectionBarItem(
        section: AnimeLibraryTab.Section,
        state: LibrarySectionBarState,
        index: Int,
        slidingPillIndex: Animatable<Float, AnimationVector1D>,
        celestialHalo: Boolean,
        auroraColors: AuroraColors,
        showLabels: Boolean = true,
        iconBoxHPadding: Dp = 12.dp,
    ) {
        val selected = state.isLibraryTab && state.currentSection == section
        AuroraBarItemShell(
            title = librarySectionTitle(section),
            selected = selected,
            index = index,
            slidingPillIndex = slidingPillIndex,
            celestialHalo = celestialHalo,
            auroraColors = auroraColors,
            showLabels = showLabels,
            iconBoxHPadding = iconBoxHPadding,
            coachTab = if (section == AnimeLibraryTab.Section.Anime) AnimeLibraryTab else null,
            onClick = { state.selectSection(section) },
            icon = {
                Icon(
                    imageVector = librarySectionIcon(section, selected),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                )
            },
        )
    }

    @Composable
    private fun RowScope.ClassicLibrarySectionBarItem(
        section: AnimeLibraryTab.Section,
        state: LibrarySectionBarState,
    ) {
        val appHaptics = LocalAppHaptics.current
        val selected = state.isLibraryTab && state.currentSection == section
        NavigationBarItem(
            selected = selected,
            onClick = {
                appHaptics.tap()
                state.selectSection(section)
            },
            icon = {
                Icon(
                    imageVector = librarySectionIcon(section, selected),
                    contentDescription = null,
                )
            },
            label = {
                Text(
                    text = librarySectionTitle(section),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = true,
            colors = classicNavItemColors(),
        )
    }

    /**
     * Icono de la sección en la barra: película para Anime, libro para Manga.
     * Relleno cuando es la sección activa, contorno cuando no.
     */
    @Composable
    private fun librarySectionIcon(
        section: AnimeLibraryTab.Section,
        selected: Boolean,
    ): ImageVector {
        return when (section) {
            AnimeLibraryTab.Section.Anime ->
                if (selected) Icons.Filled.Movie else Icons.Outlined.Movie
            AnimeLibraryTab.Section.Manga, AnimeLibraryTab.Section.Novel ->
                if (selected) Icons.Filled.MenuBook else Icons.Outlined.MenuBook
        }
    }

    @Composable
    private fun librarySectionTitle(section: AnimeLibraryTab.Section): String {
        return when (section) {
            AnimeLibraryTab.Section.Anime -> stringResource(AYMR.strings.label_anime)
            AnimeLibraryTab.Section.Manga -> stringResource(AYMR.strings.label_manga)
            AnimeLibraryTab.Section.Novel -> stringResource(AYMR.strings.label_novel)
        }
    }

    /**
     * Colores del ítem clásico de la barra: AMOLED sin píldora gris detrás
     * del icono seleccionado (el indicatorColor por defecto es
     * secondaryContainer, gris en Monocromo). La selección se distingue por
     * el icono relleno + el color de acento, no por un fondo gris.
     */
    @Composable
    private fun classicNavItemColors(): NavigationBarItemColors {
        val amoled by uiPreferences.themeDarkAmoled().collectAsState()
        return if (amoled && isSystemInDarkTheme()) {
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
    }

    @Composable
    private fun AuroraLibrarySectionRailItem(
        section: AnimeLibraryTab.Section,
        state: LibrarySectionBarState,
        celestialHalo: Boolean,
        auroraColors: AuroraColors,
    ) {
        val selected = state.isLibraryTab && state.currentSection == section
        AuroraRailItemShell(
            title = librarySectionTitle(section),
            selected = selected,
            celestialHalo = celestialHalo,
            auroraColors = auroraColors,
            coachTab = if (section == AnimeLibraryTab.Section.Anime) AnimeLibraryTab else null,
            onClick = { state.selectSection(section) },
            icon = {
                Icon(
                    imageVector = librarySectionIcon(section, selected),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                )
            },
        )
    }

    @Composable
    private fun LibrarySectionRailItem(
        section: AnimeLibraryTab.Section,
        state: LibrarySectionBarState,
    ) {
        val appHaptics = LocalAppHaptics.current
        val selected = state.isLibraryTab && state.currentSection == section
        NavigationRailItem(
            selected = selected,
            onClick = {
                appHaptics.tap()
                state.selectSection(section)
            },
            icon = {
                Icon(
                    imageVector = librarySectionIcon(section, selected),
                    contentDescription = null,
                )
            },
            label = {
                Text(
                    text = librarySectionTitle(section),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = true,
            colors = railItemColors(),
        )
    }

    /**
     * Colores del ítem clásico del rail: los mismos que el ítem de pestaña
     * del rail ([NavigationRailItem]).
     */
    @Composable
    private fun railItemColors(): NavigationRailItemColors {
        val theme by uiPreferences.appTheme().collectAsState()
        val isAurora = theme.isAuroraStyle
        return if (isAurora) {
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
    }

    /**
     * Píldora deslizante Aurora con tono cristal glass (V7.9): el relleno
     * es cristal translúcido como la barra a opacidad 0 y color liso a
     * opacidad 1 (negro en tema oscuro, blanco en tema claro). El sólido
     * se dibuja encima del cristal con alfa = opacidad, así que el paso
     * de cristal a liso es continuo, sin saltos. SIN hazeBlur en vivo:
     * la lección de v7.3 — el blur sobre la barra difuminaba todo el
     * contenido en cada frame al hacer scroll. El borde fino se mantiene
     * para que la píldora se distinga del cristal de la barra.
     * La píldora sigue viajando con el tween fijo de 220 ms de v7.7;
     * aquí solo cambia cómo se ve. Cada ítem dibuja la píldora solo
     * mientras su centro está en su zona [index - 0.5, index + 0.5),
     * así que entre vecinos no hay doble dibujo ni huecos.
     */
    private fun Modifier.auroraSlidingPill(
        slidingPillIndex: Animatable<Float, AnimationVector1D>,
        itemIndex: Int,
        itemWidthPx: State<Float>,
        isDark: Boolean,
        capsuleOpacity: Float = 0.4f,
    ): Modifier {
        // Base cristal: como la barra Aurora (#1E1E1E al 82% + velo
        // blanco del 8% en oscuro; blanco translúcido en claro).
        val glassBase = if (isDark) {
            Color(0xFF1E1E1E).copy(alpha = 0.82f)
        } else {
            Color.White.copy(alpha = 0.55f)
        }
        val glassVeil = if (isDark) {
            Color.White.copy(alpha = 0.08f)
        } else {
            Color.White.copy(alpha = 0.25f)
        }
        val solidColor = if (isDark) Color.Black else Color.White
        val borderColor = if (isDark) {
            Color.White.copy(alpha = 0.12f)
        } else {
            Color.Black.copy(alpha = 0.12f)
        }
        return this.drawBehind {
            val delta = slidingPillIndex.value - itemIndex
            if (delta < -0.5f || delta >= 0.5f) return@drawBehind
            val corner = CornerRadius(size.height / 2f, size.height / 2f)
            val topLeft = Offset(delta * itemWidthPx.value, 0f)
            drawRoundRect(
                color = glassBase,
                topLeft = topLeft,
                size = size,
                cornerRadius = corner,
            )
            drawRoundRect(
                color = glassVeil,
                topLeft = topLeft,
                size = size,
                cornerRadius = corner,
            )
            if (capsuleOpacity > 0f) {
                drawRoundRect(
                    color = solidColor.copy(alpha = capsuleOpacity),
                    topLeft = topLeft,
                    size = size,
                    cornerRadius = corner,
                )
            }
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
        // V5: blur más barato — radio 12 dp en vez de 24 dp y la mitad de
        // ruido; en AMOLED no había haze en absoluto (la base opaca negra ya
        // estaba debajo) ni sombra: el blur sobre negro puro no aporta nada.
        // V7.3: en AMOLED la barra vuelve a ser cristal (base gris muy
        // oscuro translúcida + hazeBlur) a petición del usuario: menos
        // oscura que el negro sólido, se nota el contenido detrás.
        // V7.4: el hazeBlur de AMOLED se quita OTRA VEZ — el blur volvía a
        // difuminar TODO el contenido en cada frame al hacer scroll (el
        // hazeSource envuelve todo el scaffold) y era la causa principal
        // del lag general de la v7.3. El cristal se queda (base #1E1E1E al
        // 82%) y el velo blanco del 8% va como capa translúcida encima,
        // sin difuminar nada: la barra sigue viéndose menos oscura que el
        // negro sólido, sin pagar el blur por frame.
        val hazeStyle = HazeBlurStyle {
            backgroundColor(colors.background)
            colorEffects(listOf(HazeColorEffect.tint(colors.surface.copy(alpha = 0.65f))))
            blurRadius(12.dp)
            noiseFactor(0.06f)
        }
        return if (colors.isDark) {
            // V5: una sola sombra (antes dos: 10 dp + 3 dp); en AMOLED ninguna.
            val shadowed = if (colors.isAmoled) {
                this
            } else {
                this.shadow(
                    elevation = 10.dp,
                    shape = shape,
                    ambientColor = Color.White.copy(alpha = 0.12f),
                    spotColor = Color.White.copy(alpha = 0.08f),
                )
            }
            shadowed
                .clip(shape)
                // Base bajo el cristal: en AMOLED gris muy oscuro translúcido
                // (#1E1E1E al 82%) en vez de negro opaco; en los primeros
                // frames (antes de que el haze capture contenido) la barra
                // no parpadea transparente. En no-AMOLED sigue el fondo opaco.
                .background(
                    if (colors.isAmoled) {
                        Color(0xFF1E1E1E).copy(alpha = 0.82f)
                    } else {
                        colors.background
                    },
                    shape,
                )
                .then(
                    if (colors.isAmoled) {
                        // V7.4: sin hazeBlur en AMOLED (ver comentario de
                        // arriba): el velo blanco del 8% es una capa
                        // translúcida sobre el cristal, no un blur.
                        Modifier.background(Color.White.copy(alpha = 0.08f), shape)
                    } else {
                        Modifier.hazeBlur(input = HazeInput.Sources(hazeState), style = hazeStyle)
                    },
                )
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
        // Títulos son dos iconos separados, Anime y Manga, al final. El
        // índice seleccionado se calcula sobre los huecos visibles.
        val railLibraryState = rememberLibrarySectionBarState(isAuroraTheme = true)
        val visibleLibrarySections = listOfNotNull(
            AnimeLibraryTab.Section.Anime.takeIf { railLibraryState.showAnimeSection },
            AnimeLibraryTab.Section.Manga.takeIf { railLibraryState.showMangaSection },
        )
        val railTabs = tabs.filter {
            it::class != AnimeLibraryTab::class && it::class != MoreTab::class
        }
        val selectedIndex = if (railLibraryState.isLibraryTab) {
            val sectionIndex = visibleLibrarySections.indexOf(railLibraryState.currentSection)
            if (sectionIndex >= 0) railTabs.size + sectionIndex else -1
        } else {
            railTabs.indexOfFirst { it::class == tabNavigator.current::class }
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
                tabCount = railTabs.size + visibleLibrarySections.size,
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
            visibleLibrarySections.forEach { section ->
                AuroraLibrarySectionRailItem(
                    section = section,
                    state = railLibraryState,
                    celestialHalo = celestialRail,
                    auroraColors = auroraColors,
                )
            }
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
        AuroraRailItemShell(
            title = tab.options.title,
            selected = selected,
            celestialHalo = celestialHalo,
            auroraColors = auroraColors,
            coachTab = tab,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = {
                NavigationIconItem(
                    tab = tab,
                    selected = selected,
                    modifier = Modifier.size(21.dp),
                )
            },
        )
    }

    /**
     * Cáscara visual común de un hueco del rail Aurora: mismo icono de 21 dp,
     * fondo y etiqueta que el ítem de pestaña. La usan los ítems de pestaña
     * y los dos iconos separados Anime/Manga.
     */
    @Composable
    private fun AuroraRailItemShell(
        title: String,
        selected: Boolean,
        celestialHalo: Boolean,
        auroraColors: AuroraColors,
        coachTab: eu.kanade.presentation.util.Tab? = null,
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
    ) {
        val appHaptics = LocalAppHaptics.current
        val interactionSource = remember { MutableInteractionSource() }
        // V7.9: igual que la barra inferior — la opacidad de la cápsula
        // (Ajustes → Navegación) y el contraste del icono seleccionado.
        val capsuleOpacity by uiPreferences.auroraCapsuleOpacity().collectAsState()
        val capsuleOpacityF = (capsuleOpacity / 100f).coerceIn(0f, 1f)
        val iconColor = if (selected) {
            val solidFactor = ((capsuleOpacityF - 0.5f) / 0.5f).coerceIn(0f, 1f)
            lerp(auroraColors.accent, auroraColors.textPrimary, solidFactor)
        } else {
            auroraColors.textSecondary.copy(alpha = if (auroraColors.isDark) 0.72f else 0.78f)
        }
        val labelColor = if (selected) {
            auroraColors.accent
        } else {
            auroraColors.textSecondary.copy(alpha = if (auroraColors.isDark) 0.82f else 0.88f)
        }
        // Fondo de la píldora del rail: mismo cristal→liso que la barra
        // (un solo color interpolado: cristal a 0, liso a 1).
        val iconBackgroundBrush = if (selected) {
            val glassColor = if (auroraColors.isDark) {
                Color(0xFF2A2A2A)
            } else {
                Color.White.copy(alpha = 0.6f)
            }
            val solidColor = if (auroraColors.isDark) Color.Black else Color.White
            SolidColor(lerp(glassColor, solidColor, capsuleOpacityF))
        } else {
            null
        }
        val iconShape = RoundedCornerShape(999.dp)

        Box(
            modifier = Modifier
                .then(if (coachTab != null) Modifier.coachAnchorForTab(coachTab) else Modifier)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        appHaptics.tap()
                        onClick()
                    },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = title
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
                        icon()
                    }
                }

                Text(
                    text = title,
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

        val colors = railItemColors()

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
