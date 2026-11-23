/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.more.settings.screen.player.custombutton.getButtons
import eu.kanade.presentation.theme.playerRippleConfiguration
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.ui.player.Dialogs
import eu.kanade.tachiyomi.ui.player.Panels
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.PlayerUpdates
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.Sheets
import eu.kanade.tachiyomi.ui.player.VideoAspect
import eu.kanade.tachiyomi.ui.player.controls.components.BrightnessOverlay
import eu.kanade.tachiyomi.ui.player.controls.components.BrightnessSlider
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import eu.kanade.tachiyomi.ui.player.controls.components.FilledControlsButton
import eu.kanade.tachiyomi.ui.player.controls.components.MiniSeekbar
import eu.kanade.tachiyomi.ui.player.controls.components.TextPlayerUpdate
import eu.kanade.tachiyomi.ui.player.controls.components.VolumeSlider
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.toFixed
import eu.kanade.tachiyomi.ui.player.execute
import eu.kanade.tachiyomi.ui.player.executeLongPress
import eu.kanade.tachiyomi.ui.player.resolveVisibleCustomButton
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Suppress("CompositionLocalAllowlist")
val LocalPlayerButtonsClickEvent = staticCompositionLocalOf { {} }

@Composable
fun PlayerControls(
    viewModel: PlayerViewModel,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.padding
    val playerPreferences = remember { Injekt.get<PlayerPreferences>() }
    val gesturePreferences = remember { Injekt.get<GesturePreferences>() }
    val audioPreferences = remember { Injekt.get<AudioPreferences>() }
    val subtitlePreferences = remember { Injekt.get<SubtitlePreferences>() }
    val interactionSource = remember { MutableInteractionSource() }

    val controlsShown by viewModel.controlsShown.collectAsStateWithLifecycle()
    val areControlsLocked by viewModel.areControlsLocked.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingEpisode by viewModel.isLoadingEpisode.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val position by viewModel.pos.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val gestureSeekAmount by viewModel.gestureSeekAmount.collectAsStateWithLifecycle()
    val doubleTapSeekAmount by viewModel.doubleTapSeekAmount.collectAsStateWithLifecycle()
    val seekText by viewModel.seekText.collectAsStateWithLifecycle()
    val currentChapter by viewModel.currentChapter.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val currentBrightness by viewModel.currentBrightness.collectAsStateWithLifecycle()
    val isDynamicSpeedActive by viewModel.isDynamicSpeedActive.collectAsStateWithLifecycle()
    val gesturePlaybackSpeed by viewModel.gesturePlaybackSpeed.collectAsStateWithLifecycle()

    val playerTimeToDisappear by playerPreferences.playerTimeToDisappear().collectAsStateWithLifecycle()
    val showCustomButtons by playerPreferences.showCustomButtons().collectAsStateWithLifecycle()
    var isMiniScrubbing by remember { mutableStateOf(false) }
    var resetControls by remember { mutableStateOf(true) }

    val customButtons by viewModel.customButtons.collectAsStateWithLifecycle()
    val customButton by viewModel.primaryButton.collectAsStateWithLifecycle()

    val mediaTitle by viewModel.mediaTitle.collectAsStateWithLifecycle()
    val animeTitle by viewModel.animeTitle.collectAsStateWithLifecycle()
    val currentEpisode by viewModel.currentEpisode.collectAsStateWithLifecycle()
    val anime by viewModel.currentAnime.collectAsStateWithLifecycle()
    val playlist by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val invertDuration by playerPreferences.invertDuration().collectAsStateWithLifecycle()
    val autoPlayEnabled by playerPreferences.autoplayEnabled().collectAsStateWithLifecycle()
    val aspectRatio by playerPreferences.aspectState().collectAsStateWithLifecycle()

    // Nuvio-style title block data, derived from the title's own metadata.
    val warnings = remember(anime) { contentWarningsFor(anime?.displayGenre) }
    val episodeLine = remember(anime, currentEpisode, mediaTitle) {
        val season = anime?.seasonNumber?.let { formatEpisodeNumber(it) }.orEmpty()
        val epNum = currentEpisode?.episode_number?.toDouble()?.let { formatEpisodeNumber(it) }.orEmpty()
        val name = mediaTitle.ifBlank { currentEpisode?.name.orEmpty() }
        when {
            season.isNotBlank() && epNum.isNotBlank() && name.isNotBlank() -> "T$season E$epNum · $name"
            name.isNotBlank() -> name
            else -> ""
        }
    }
    val yearLine = remember(anime, playlist) {
        val firstDate = playlist.minOfOrNull { it.date_upload }?.takeIf { it > 0L } ?: return@remember null
        val year = LocalDate.ofInstant(Instant.ofEpochMilli(firstDate), ZoneId.systemDefault()).year
        if (anime?.status != SAnime.COMPLETED.toLong()) "$year–" else "$year"
    }
    val timeText = remember(position, duration, invertDuration) {
        val durationValue = if (invertDuration) position - duration else duration
        "${Utils.prettyTime(position.toInt())} / ${Utils.prettyTime(durationValue.toInt(), invertDuration)}"
    }

    LaunchedEffect(
        controlsShown,
        paused,
        isMiniScrubbing,
        resetControls,
    ) {
        if (controlsShown && !paused && !isMiniScrubbing) {
            delay(playerTimeToDisappear.toLong())
            viewModel.hideControls()
        }
    }

    val transparentOverlay by animateFloatAsState(
        if (controlsShown && !areControlsLocked) .8f else 0f,
        animationSpec = playerControlsExitAnimationSpec(),
        label = "controls_transparent_overlay",
    )
    GestureHandler(
        viewModel = viewModel,
        interactionSource = interactionSource,
    )
    DoubleTapToSeekOvals(doubleTapSeekAmount, seekText, interactionSource)
    CompositionLocalProvider(
        LocalRippleConfiguration provides playerRippleConfiguration,
        LocalPlayerButtonsClickEvent provides { resetControls = !resetControls },
        LocalContentColor provides Color.White,
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Ltr,
        ) {
            ConstraintLayout(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            Pair(0f, Color.Black),
                            Pair(.2f, Color.Transparent),
                            Pair(.7f, Color.Transparent),
                            Pair(1f, Color.Black),
                        ),
                        alpha = transparentOverlay,
                    )
                    .padding(horizontal = MaterialTheme.padding.medium),
            ) {
                val (topLeftControls, topRightControls) = createRefs()
                val (volumeSlider, brightnessSlider) = createRefs()
                val unlockControlsButton = createRef()
                val centerControls = createRef()
                val titleBlock = createRef()
                val actionButtons = createRef()
                val bottomRow = createRef()
                val (playerUpdates) = createRefs()
                val speedPill = createRef()

                val isBrightnessSliderShown by viewModel.isBrightnessSliderShown.collectAsStateWithLifecycle()
                val isVolumeSliderShown by viewModel.isVolumeSliderShown.collectAsStateWithLifecycle()
                val brightness by viewModel.currentBrightness.collectAsStateWithLifecycle()
                val volume by viewModel.currentVolume.collectAsStateWithLifecycle()
                val mpvVolume by viewModel.currentMPVVolume.collectAsStateWithLifecycle()
                val swapVolumeAndBrightness by gesturePreferences.swapVolumeBrightness().collectAsStateWithLifecycle()
                val reduceMotion by playerPreferences.reduceMotion().collectAsStateWithLifecycle()

                LaunchedEffect(volume, mpvVolume, isVolumeSliderShown) {
                    delay(2000)
                    if (isVolumeSliderShown) viewModel.isVolumeSliderShown.update { false }
                }
                LaunchedEffect(brightness, isBrightnessSliderShown) {
                    delay(2000)
                    if (isBrightnessSliderShown) viewModel.isBrightnessSliderShown.update { false }
                }
                AnimatedVisibility(
                    isBrightnessSliderShown,
                    enter =
                    if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) {
                            if (swapVolumeAndBrightness) -it else it
                        } +
                            fadeIn(
                                playerControlsEnterAnimationSpec(),
                            )
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit =
                    if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) {
                            if (swapVolumeAndBrightness) -it else it
                        } +
                            fadeOut(
                                playerControlsExitAnimationSpec(),
                            )
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(brightnessSlider) {
                        if (swapVolumeAndBrightness) {
                            start.linkTo(parent.start, spacing.medium)
                        } else {
                            end.linkTo(parent.end, spacing.medium)
                        }
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    },
                ) {
                    BrightnessSlider(
                        brightness = brightness,
                        positiveRange = 0f..1f,
                        negativeRange = 0f..0.75f,
                    )
                }

                AnimatedVisibility(
                    isVolumeSliderShown,
                    enter =
                    if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) {
                            if (swapVolumeAndBrightness) it else -it
                        } +
                            fadeIn(
                                playerControlsEnterAnimationSpec(),
                            )
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit =
                    if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) {
                            if (swapVolumeAndBrightness) it else -it
                        } +
                            fadeOut(
                                playerControlsExitAnimationSpec(),
                            )
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(volumeSlider) {
                        if (swapVolumeAndBrightness) {
                            end.linkTo(parent.end, spacing.medium)
                        } else {
                            start.linkTo(parent.start, spacing.medium)
                        }
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    },
                ) {
                    val boostCap by audioPreferences.volumeBoostCap().collectAsStateWithLifecycle()
                    val displayVolumeAsPercentage by playerPreferences.displayVolPer().collectAsStateWithLifecycle()
                    VolumeSlider(
                        volume = volume,
                        mpvVolume = mpvVolume,
                        range = 0..viewModel.maxVolume,
                        boostRange = if (boostCap > 0) 0..audioPreferences.volumeBoostCap().get() else null,
                        displayAsPercentage = displayVolumeAsPercentage,
                    )
                }

                val currentPlayerUpdate by viewModel.playerUpdate.collectAsStateWithLifecycle()
                LaunchedEffect(currentPlayerUpdate, aspectRatio) {
                    if (currentPlayerUpdate is PlayerUpdates.DoubleSpeed || currentPlayerUpdate is PlayerUpdates.None) {
                        return@LaunchedEffect
                    }
                    delay(2000)
                    viewModel.playerUpdate.update { PlayerUpdates.None }
                }
                AnimatedVisibility(
                    currentPlayerUpdate !is PlayerUpdates.None,
                    enter = fadeIn(playerControlsEnterAnimationSpec()),
                    exit = fadeOut(playerControlsExitAnimationSpec()),
                    modifier = Modifier.constrainAs(playerUpdates) {
                        linkTo(parent.start, parent.end)
                        linkTo(parent.top, parent.bottom, bias = 0.2f)
                    },
                ) {
                    when (currentPlayerUpdate) {
                        // is PlayerUpdates.DoubleSpeed -> DoubleSpeedPlayerUpdate()
                        is PlayerUpdates.AspectRatio -> TextPlayerUpdate(stringResource(aspectRatio.titleRes))
                        is PlayerUpdates.ShowText -> TextPlayerUpdate(
                            (currentPlayerUpdate as PlayerUpdates.ShowText).value,
                        )
                        is PlayerUpdates.ShowTextResource -> TextPlayerUpdate(
                            stringResource((currentPlayerUpdate as PlayerUpdates.ShowTextResource).textResource),
                        )
                        else -> {}
                    }
                }

                AnimatedVisibility(
                    controlsShown && areControlsLocked,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.constrainAs(unlockControlsButton) {
                        top.linkTo(parent.top, spacing.medium)
                        start.linkTo(parent.start, spacing.medium)
                    },
                ) {
                    ControlsButton(
                        Icons.Filled.Lock,
                        onClick = { viewModel.unlockControls() },
                    )
                }
                AnimatedVisibility(
                    visible =
                    ((controlsShown && !areControlsLocked && !isDynamicSpeedActive) || gestureSeekAmount != null) ||
                        isLoading ||
                        isLoadingEpisode,
                    enter = fadeIn(playerControlsEnterAnimationSpec()),
                    exit = fadeOut(playerControlsExitAnimationSpec()),
                    modifier = Modifier.constrainAs(centerControls) {
                        end.linkTo(parent.absoluteRight)
                        start.linkTo(parent.absoluteLeft)
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    },
                ) {
                    val showLoadingCircle by playerPreferences.showLoadingCircle().collectAsStateWithLifecycle()
                    MiddlePlayerControls(
                        isLoading = isLoading,
                        isLoadingEpisode = isLoadingEpisode,
                        controlsShown = controlsShown,
                        areControlsLocked = areControlsLocked,
                        showLoadingCircle = showLoadingCircle,
                        paused = paused,
                        gestureSeekAmount = gestureSeekAmount,
                        onPlayPauseClick = viewModel::pauseUnpause,
                        onSeekBack = { viewModel.seekBy(-10) },
                        onSeekForward = { viewModel.seekBy(10) },
                        enter = fadeIn(playerControlsEnterAnimationSpec()),
                        exit = fadeOut(playerControlsExitAnimationSpec()),
                    )
                }
                // Content warnings, top-left.
                AnimatedVisibility(
                    controlsShown && !areControlsLocked && !isDynamicSpeedActive,
                    enter = if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) { -it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) { -it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(topLeftControls) {
                        top.linkTo(parent.top, spacing.medium)
                        start.linkTo(parent.start)
                        width = Dimension.fillToConstraints
                        end.linkTo(topRightControls.start)
                    },
                ) {
                    TopLeftPlayerControls(
                        warnings = warnings,
                    )
                }
                // Top right controls: aspect, watch party, lock, back.
                AnimatedVisibility(
                    controlsShown && !areControlsLocked && !isDynamicSpeedActive,
                    enter = if (!reduceMotion) {
                        slideInHorizontally(playerControlsEnterAnimationSpec()) { it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutHorizontally(playerControlsExitAnimationSpec()) { it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(topRightControls) {
                        top.linkTo(parent.top, spacing.medium)
                        end.linkTo(parent.end)
                    },
                ) {
                    TopRightPlayerControls(
                        onAspectClick = {
                            viewModel.changeVideoAspect(
                                when (aspectRatio) {
                                    VideoAspect.Fit -> VideoAspect.Stretch
                                    VideoAspect.Stretch -> VideoAspect.Crop
                                    VideoAspect.Crop -> VideoAspect.Fit
                                },
                            )
                        },
                        onWatchPartyClick = { viewModel.showWatchPartyUnavailable() },
                        onLockControls = viewModel::lockControls,
                        onBackClick = onBackPress,
                    )
                }
                // Title block above the bottom action row. It sits a
                // little in from the left edge (aligned with the bottom
                // row) and just above the row, not stuck to the corner.
                AnimatedVisibility(
                    controlsShown && !areControlsLocked && !isDynamicSpeedActive,
                    enter = if (!reduceMotion) {
                        slideInVertically(playerControlsEnterAnimationSpec()) { it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutVertically(playerControlsExitAnimationSpec()) { it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(titleBlock) {
                        bottom.linkTo(bottomRow.top, spacing.extraSmall)
                        start.linkTo(parent.start, spacing.large)
                        width = Dimension.fillToConstraints
                        end.linkTo(actionButtons.start, spacing.medium)
                    },
                ) {
                    BottomLeftPlayerControls(
                        animeTitle = animeTitle,
                        episodeLine = episodeLine,
                        yearLine = yearLine,
                    )
                }
                // Skip intro / custom button, above the bottom action
                // row on the right.
                val skipIntroButton by viewModel.skipIntroText.collectAsStateWithLifecycle()
                val customButtonTitle by viewModel.primaryButtonTitle.collectAsStateWithLifecycle()
                val visibleCustomButton = resolveVisibleCustomButton(showCustomButtons, customButton)
                val visibleSkipIntroButton = skipIntroButton
                AnimatedVisibility(
                    controlsShown && !areControlsLocked && !isDynamicSpeedActive &&
                        (visibleSkipIntroButton != null || visibleCustomButton != null),
                    enter = if (!reduceMotion) {
                        slideInVertically(playerControlsEnterAnimationSpec()) { it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutVertically(playerControlsExitAnimationSpec()) { it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(actionButtons) {
                        bottom.linkTo(bottomRow.top, spacing.extraSmall)
                        end.linkTo(parent.end)
                    },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (visibleSkipIntroButton != null) {
                            FilledControlsButton(
                                text = visibleSkipIntroButton,
                                onClick = viewModel::onSkipIntro,
                                onLongClick = {},
                            )
                        }
                        if (visibleCustomButton != null) {
                            FilledControlsButton(
                                text = customButtonTitle,
                                onClick = visibleCustomButton::execute,
                                onLongClick = visibleCustomButton::executeLongPress,
                            )
                        }
                    }
                }
                // Bottom action row: subtitles, episodes, download, PiP,
                // speed, quality, more — with the mini timeline centered
                // in the middle of the row and the clock at the far right.
                // Inset from the screen sides: the row never runs from
                // one edge of the screen to the other.
                AnimatedVisibility(
                    controlsShown && !areControlsLocked && !isDynamicSpeedActive,
                    enter = if (!reduceMotion) {
                        slideInVertically(playerControlsEnterAnimationSpec()) { it } +
                            fadeIn(playerControlsEnterAnimationSpec())
                    } else {
                        fadeIn(playerControlsEnterAnimationSpec())
                    },
                    exit = if (!reduceMotion) {
                        slideOutVertically(playerControlsExitAnimationSpec()) { it } +
                            fadeOut(playerControlsExitAnimationSpec())
                    } else {
                        fadeOut(playerControlsExitAnimationSpec())
                    },
                    modifier = Modifier.constrainAs(bottomRow) {
                        bottom.linkTo(parent.bottom, spacing.medium)
                        start.linkTo(parent.start, spacing.large)
                        end.linkTo(parent.end, spacing.large)
                        width = Dimension.fillToConstraints
                    },
                ) {
                    val activity = LocalContext.current as PlayerActivity
                    val readAhead by viewModel.readAhead.collectAsStateWithLifecycle()
                    val preciseSeeking by gesturePreferences.playerSmoothSeek().collectAsStateWithLifecycle()
                    BottomRightPlayerControls(
                        positionText = timeText,
                        // Mini timeline, inline on the same row as the
                        // buttons: the only timeline. Dragging it seeks
                        // directly — there is no long bar anymore.
                        miniSeekbarContent = {
                            MiniSeekbar(
                                position = position,
                                duration = duration,
                                readAheadValue = readAhead,
                                onValueChange = {
                                    isMiniScrubbing = true
                                    viewModel.updatePlayBackPos(it)
                                    viewModel.seekTo(it.toInt(), preciseSeeking)
                                },
                                onValueChangeFinished = { isMiniScrubbing = false },
                                chapters = chapters.map { it.toSegment() }.toImmutableList(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        onSubtitlesClick = { viewModel.showSheet(Sheets.SubtitleTracks) },
                        onSubtitlesLongClick = { viewModel.showPanel(Panels.SubtitleSettings) },
                        onEpisodesClick = { viewModel.showEpisodeListDialog() },
                        onDownloadClick = { viewModel.downloadCurrentEpisode() },
                        isPipAvailable = activity.isPipSupportedAndEnabled,
                        onPipClick = {
                            if (!viewModel.isLoadingEpisode.value) {
                                activity.enterPictureInPictureMode(activity.createPipParams())
                            }
                        },
                        onSpeedClick = {
                            val newSpeed = if (playbackSpeed >= 2) 0.25f else playbackSpeed + 0.25f
                            MPVLib.setPropertyDouble("speed", newSpeed.toDouble())
                            playerPreferences.playerSpeed().set(newSpeed)
                        },
                        onSpeedLongClick = { viewModel.showSheet(Sheets.PlaybackSpeed) },
                        onQualityClick = { viewModel.showSheet(Sheets.QualityTracks) },
                        onMoreClick = { viewModel.showSheet(Sheets.More) },
                        onTimeClick = { playerPreferences.invertDuration().set(!invertDuration) },
                    )
                }

                AnimatedVisibility(
                    visible = isDynamicSpeedActive,
                    enter =
                    fadeIn(playerControlsEnterAnimationSpec()) +
                        slideInVertically(playerControlsEnterAnimationSpec()) { -it },
                    exit =
                    fadeOut(playerControlsExitAnimationSpec()) +
                        slideOutVertically(playerControlsExitAnimationSpec()) { -it },
                    modifier = Modifier.constrainAs(speedPill) {
                        top.linkTo(parent.top, spacing.medium)
                        linkTo(parent.start, parent.end)
                    },
                ) {
                    PlaybackSpeedPill(speed = gesturePlaybackSpeed)
                }
            }
        }

        val sheetShown by viewModel.sheetShown.collectAsStateWithLifecycle()
        val dismissSheet by viewModel.dismissSheet.collectAsStateWithLifecycle()
        val subtitles by viewModel.subtitleTracks.collectAsStateWithLifecycle()
        val selectedSubtitles by viewModel.selectedSubtitles.collectAsStateWithLifecycle()
        val subtitleTranslationUiState by viewModel.subtitleTranslationUiState.collectAsStateWithLifecycle()
        val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
        val selectedAudio by viewModel.selectedAudio.collectAsStateWithLifecycle()
        val isLoadingHosters by viewModel.isLoadingHosters.collectAsStateWithLifecycle()
        val hosterState by viewModel.hosterState.collectAsStateWithLifecycle()
        val expandedState by viewModel.hosterExpandedList.collectAsStateWithLifecycle()
        val selectedHosterVideoIndex by viewModel.selectedHosterVideoIndex.collectAsStateWithLifecycle()
        val decoder by viewModel.currentDecoder.collectAsStateWithLifecycle()
        val speed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
        val sleepTimerTimeRemaining by viewModel.remainingTime.collectAsStateWithLifecycle()
        val showSubtitles by subtitlePreferences.screenshotSubtitles().collectAsStateWithLifecycle()
        val currentSource by viewModel.currentSource.collectAsStateWithLifecycle()
        val showFailedHosters by playerPreferences.showFailedHosters().collectAsStateWithLifecycle()
        val emptyHosters by playerPreferences.showEmptyHosters().collectAsStateWithLifecycle()

        PlayerSheets(
            sheetShown = sheetShown,
            subtitles = subtitles.toImmutableList(),
            selectedSubtitles = selectedSubtitles.toList().toImmutableList(),
            onAddSubtitle = viewModel::addSubtitle,
            onTranslateSubtitle = viewModel::openSubtitleTranslationSheet,
            subtitleTranslationUiState = subtitleTranslationUiState,
            onSelectSubtitleTranslationTrack = viewModel::selectSubtitleTranslationTrack,
            onSelectSubtitleTranslationProvider = viewModel::selectSubtitleTranslationProvider,
            onSubtitleTranslationSourceLanguageChange = viewModel::setSubtitleTranslationSourceLanguage,
            onSubtitleTranslationTargetLanguageChange = viewModel::setSubtitleTranslationTargetLanguage,
            onToggleSubtitleTranslationCache = viewModel::toggleSubtitleTranslationCache,
            onStartSubtitleTranslation = viewModel::startSubtitleTranslation,
            onCancelSubtitleTranslation = viewModel::cancelSubtitleTranslation,
            onSelectSubtitle = viewModel::selectSub,
            audioTracks = audioTracks.toImmutableList(),
            selectedAudio = selectedAudio,
            onAddAudio = viewModel::addAudio,
            onSelectAudio = viewModel::selectAudio,

            isLoadingHosters = isLoadingHosters,

            hosterState = hosterState,
            expandedState = expandedState,
            selectedVideoIndex = selectedHosterVideoIndex,
            onClickHoster = viewModel::onHosterClicked,
            onClickVideo = viewModel::onVideoClicked,
            displayHosters = Pair(showFailedHosters, emptyHosters),

            chapter = currentChapter?.toSegment(),
            chapters = chapters.map { it.toSegment() }.toImmutableList(),
            onSeekToChapter = {
                viewModel.selectChapter(it)
                viewModel.dismissSheet()
                viewModel.unpause()
            },
            decoder = decoder,
            onUpdateDecoder = viewModel::updateDecoder,
            speed = speed,
            onSpeedChange = { MPVLib.setPropertyDouble("speed", it.toFixed(2).toDouble()) },
            sleepTimerTimeRemaining = sleepTimerTimeRemaining,
            onStartSleepTimer = viewModel::startTimer,
            autoPlayEnabled = autoPlayEnabled,
            onToggleAutoPlay = viewModel::setAutoPlay,
            onOpenSheet = viewModel::showSheet,
            onCycleRotation = viewModel::cycleScreenRotations,
            hasChapters = currentChapter != null,
            buttons = if (showCustomButtons) {
                customButtons.getButtons().toImmutableList()
            } else {
                emptyList<tachiyomi.domain.custombuttons.model.CustomButton>().toImmutableList()
            },

            isLocalSource = currentSource?.id == LocalAnimeSource.ID,
            showSubtitles = showSubtitles,
            onToggleShowSubtitles = { subtitlePreferences.screenshotSubtitles().set(it) },
            cachePath = viewModel.cachePath,
            onSetAsArt = viewModel::setAsArt,
            onShare = { viewModel.shareImage(it, viewModel.pos.value.toInt()) },
            onSave = { viewModel.saveImage(it, viewModel.pos.value.toInt()) },
            takeScreenshot = viewModel::takeScreenshot,
            onDismissScreenshot = {
                viewModel.showSheet(Sheets.None)
                viewModel.unpause()
            },
            onOpenPanel = viewModel::showPanel,
            onDismissRequest = { viewModel.showSheet(Sheets.None) },
            dismissSheet = dismissSheet,
        )
        val panel by viewModel.panelShown.collectAsStateWithLifecycle()
        PlayerPanels(
            panelShown = panel,
            onDismissRequest = { viewModel.showPanel(Panels.None) },
        )

        val activity = LocalContext.current as PlayerActivity
        val dialog by viewModel.dialogShown.collectAsStateWithLifecycle()

        PlayerDialogs(
            dialogShown = dialog,
            episodeDisplayMode = anime?.displayMode,
            episodeList = playlist,
            currentEpisodeIndex = viewModel.getCurrentEpisodeIndex(),
            dateRelativeTime = viewModel.relativeTime,
            dateFormat = viewModel.dateFormat,
            anime = anime,
            onEpisodeClicked = {
                viewModel.showDialog(Dialogs.None)
                activity.changeEpisode(it)
            },
            onDismissRequest = { viewModel.showDialog(Dialogs.None) },
        )

        BrightnessOverlay(
            brightness = currentBrightness,
        )
    }
}

fun <T> playerControlsExitAnimationSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 300,
    easing = FastOutSlowInEasing,
)

fun <T> playerControlsEnterAnimationSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 100,
    easing = LinearOutSlowInEasing,
)

@Composable
fun PlaybackSpeedPill(
    speed: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (speed < 1.0f) {
                FlashingArrows(isForward = false)
            }
            Text(
                text = "${speed}x",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            if (speed > 1.0f) {
                FlashingArrows(isForward = true)
            }
        }
    }
}

@Composable
fun FlashingArrows(
    isForward: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arrows_flash")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arrows_alpha",
    )
    Text(
        text = if (isForward) ">>" else "<<",
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = alpha),
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}
