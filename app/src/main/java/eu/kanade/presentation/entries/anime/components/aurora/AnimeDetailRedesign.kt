package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LabelOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.NewLabel
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadIndicator
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.entries.components.aurora.AURORA_ENTRY_READ_DIM_ALPHA
import eu.kanade.presentation.entries.components.aurora.AuroraHeroGenreChips
import eu.kanade.presentation.entries.components.aurora.AuroraNotePreviewCard
import eu.kanade.presentation.entries.components.aurora.AuroraTitleStaggerIndex
import eu.kanade.presentation.entries.components.aurora.AuroraTitleStaggerState
import eu.kanade.presentation.entries.components.aurora.CopyTitleIcon
import eu.kanade.presentation.entries.components.aurora.PassiveDownloadStatus
import eu.kanade.presentation.entries.components.aurora.auroraSpringClick
import eu.kanade.presentation.entries.components.aurora.copyTitleInlineContent
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroSecondaryMetaColor
import eu.kanade.presentation.entries.components.aurora.resolveAuroraHeroTitleColor
import eu.kanade.presentation.entries.components.aurora.titleScreenStagger
import eu.kanade.presentation.entries.translation.AuroraEntryTranslationState
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalCoverTitleFontFamily
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Definitive anime detail design (approved mockups, Nov 2026).
//
// Top on entry: full-bleed backdrop with the hero content laid directly over
// it (genre chips, big title, studio, rating line, big white "Continuar" /
// "Reproducir E1" button) — no glass card. Scrolling down reveals the title
// again with the episode count, the "Biblioteca" / "Seguimiento" pills,
// the synopsis card (3 lines + expand arrow), the "Episodios" header with
// the Descendente / All filter chips, a horizontal episode carousel, the wide
// "Mostrar los N episodios" button and the "Títulos similares" carousel.
//
// Fluency: the hero is a fixed overlay whose alpha is driven through a
// graphicsLayer read of a derived scroll state in the screen (same pattern
// as before); these components take plain values, so scrolling never
// recomposes them. Episode tiles reuse the episode preview URL the list
// already loads (Coil memory cache) and fall back to a cheap fixed aurora
// gradient derived from the episode id — no new per-item network work.
// ---------------------------------------------------------------------------

private fun parseOriginalTitle(description: String?): String? {
    if (description.isNullOrBlank()) return null

    val match = Regex(
        pattern = """(?:Original|Оригинал):\s*([^\n]+)""",
        options = setOf(RegexOption.IGNORE_CASE),
    ).find(description)

    return match?.groupValues?.get(1)?.trim()
}

/**
 * Hero laid directly over the full-bleed backdrop (no glass panel).
 * A bottom black gradient keeps the white text legible over any poster.
 * [onDubbingClicked]/[selectedDubbing] are kept for signature parity with
 * the previous hero; dubbing selection itself is not rendered here, exactly
 * as before.
 */
@Composable
fun AnimeDetailHero(
    anime: Anime,
    translation: AuroraEntryTranslationState? = null,
    hasWatchingProgress: Boolean,
    ratingText: String,
    episodeCount: Int,
    statusText: String,
    note: String,
    onEditNotesClicked: (() -> Unit)?,
    onContinueWatching: () -> Unit,
    onDubbingClicked: (() -> Unit)?,
    selectedDubbing: String?,
    onGenreClick: ((String) -> Unit)? = null,
    onGenreLongClick: ((String) -> Unit)? = null,
    selectedGenres: Set<String> = emptySet(),
    onSearchSelected: (() -> Unit)? = null,
    onClearSelected: (() -> Unit)? = null,
    onCopyTitle: (() -> Unit)? = null,
    titleStaggerState: AuroraTitleStaggerState? = null,
    firstEpisodeNumberText: String? = null,
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val showOriginalTitle by uiPreferences.showOriginalTitle().collectAsState()
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val coverTitleFontFamily = LocalCoverTitleFontFamily.current
    val titleColor = resolveAuroraHeroTitleColor(colors)
    val secondaryMetaColor = resolveAuroraHeroSecondaryMetaColor(colors)
    val originalTitle = remember(anime.displayDescription) {
        parseOriginalTitle(anime.displayDescription)
    }

    // White-on-poster text in both themes: the backdrop is a photo, so the
    // legibility comes from the black scrim below, not from theme text colors.
    val heroTextColor = Color.White
    val heroSecondaryColor = Color.White.copy(alpha = 0.85f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.55f to Color.Black.copy(alpha = 0.25f),
                        1f to Color.Black.copy(alpha = 0.92f),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 20.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AuroraHeroGenreChips(
                genres = anime.displayGenre,
                modifier = Modifier
                    .fillMaxWidth()
                    .titleScreenStagger(titleStaggerState, AuroraTitleStaggerIndex.HERO_GENRES),
                selectedGenres = selectedGenres,
                onGenreClick = onGenreClick,
                onGenreLongClick = onGenreLongClick,
                onSearchSelected = onSearchSelected,
                onClearSelected = onClearSelected,
            )

            val displayTitle = buildAnnotatedString {
                append(translation?.title ?: anime.displayTitle)

                if (showOriginalTitle && translation?.titleTranslated != true && originalTitle != null) {
                    withStyle(
                        SpanStyle(
                            color = heroSecondaryColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                    ) {
                        append(" ($originalTitle)")
                    }
                }
            }

            var titleOverflow by remember { mutableStateOf(false) }
            val showInlineCopyIcon = onCopyTitle != null && !titleOverflow
            val (copyTitleIconId, copyTitleInlineContent) = copyTitleInlineContent(
                onCopyTitle = if (showInlineCopyIcon) onCopyTitle else null,
                tint = secondaryMetaColor,
                contentDescription = stringResource(AYMR.strings.copy_title),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .titleScreenStagger(titleStaggerState, AuroraTitleStaggerIndex.HERO_TITLE),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = buildAnnotatedString {
                        append(displayTitle)
                        if (showInlineCopyIcon) {
                            append(' ')
                            appendInlineContent(copyTitleIconId)
                        }
                    },
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 38.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = if (colors.isDark) titleColor else heroTextColor,
                    style = TextStyle(
                        fontFamily = coverTitleFontFamily,
                        lineBreak = LineBreak.Heading,
                        hyphens = Hyphens.None,
                    ),
                    inlineContent = copyTitleInlineContent,
                    onTextLayout = { result ->
                        if (result.hasVisualOverflow && !titleOverflow) {
                            titleOverflow = true
                        }
                    },
                )

                if (titleOverflow && onCopyTitle != null) {
                    CopyTitleIcon(
                        onCopyTitle = onCopyTitle,
                        tint = secondaryMetaColor,
                        contentDescription = stringResource(AYMR.strings.copy_title),
                    )
                }
            }

            // Studio (author field holds the studio for anime).
            anime.displayAuthor?.takeIf { it.isNotBlank() }?.let { authorText ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, AuroraTitleStaggerIndex.HERO_AUTHOR),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonOutline,
                        contentDescription = null,
                        tint = heroSecondaryColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = authorText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = heroTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // ★ rating | N ep. | status — same values as the old hero stats row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .titleScreenStagger(titleStaggerState, AuroraTitleStaggerIndex.HERO_STATS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = colors.ratingStar,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = ratingText,
                        color = heroTextColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AnimeDetailHeroStatDivider()
                Text(
                    text = stringResource(AYMR.strings.aurora_episode_count, episodeCount),
                    color = heroTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AnimeDetailHeroStatDivider()
                Text(
                    text = statusText,
                    color = heroTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (note.isNotBlank()) {
                AuroraNotePreviewCard(
                    note = note,
                    onClick = onEditNotesClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleScreenStagger(titleStaggerState, AuroraTitleStaggerIndex.HERO_NOTE),
                )
            }

            AnimeDetailContinueButton(
                hasWatchingProgress = hasWatchingProgress,
                firstEpisodeNumberText = firstEpisodeNumberText,
                onClick = {
                    appHaptics.tap()
                    onContinueWatching()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .titleScreenStagger(titleStaggerState, AuroraTitleStaggerIndex.HERO_ACTION_BUTTON),
            )
        }
    }
}

@Composable
private fun AnimeDetailHeroStatDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(1.dp)
            .height(12.dp)
            .background(Color.White.copy(alpha = 0.35f)),
    )
}

/**
 * Big white continue button from the approved design: "Continuar" when there
 * is watching progress, otherwise "Reproducir E1" (first episode number).
 */
@Composable
fun AnimeDetailContinueButton(
    hasWatchingProgress: Boolean,
    firstEpisodeNumberText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (hasWatchingProgress) {
        stringResource(MR.strings.action_resume)
    } else if (firstEpisodeNumberText != null) {
        "${stringResource(AYMR.strings.aurora_play)} E$firstEpisodeNumberText"
    } else {
        stringResource(MR.strings.action_start)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .auroraSpringClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = label,
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * "Biblioteca" (corazón; blanca cuando está en la biblioteca) +
 * "Seguimiento" (gris) pills, del mismo tamaño y centradas.
 * Library long-press opens the category editor, same as the old action card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimeDetailLibraryPills(
    anime: Anime,
    trackingCount: Int,
    onAddToLibraryClicked: () -> Unit,
    onAddToLibraryLongClicked: (() -> Unit)? = null,
    onTrackingClicked: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val isDark = colors.isDark
    val inLibrary = anime.favorite
    val greyPillColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.65f)
    val greyPillTextColor = if (isDark) Color.White else Color(0xFF241A16)
    val inLibraryBackground = if (isDark) Color.White else colors.accent
    val inLibraryContent = if (isDark) Color.Black else colors.textOnAccent

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(CircleShape)
                .background(if (inLibrary) inLibraryBackground else greyPillColor)
                .combinedClickable(
                    onClick = onAddToLibraryClicked,
                    onLongClick = onAddToLibraryLongClicked,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (inLibrary) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (inLibrary) inLibraryContent else greyPillTextColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    // v4: solo "Biblioteca" con el corazón, igual que la
                    // píldora de "Seguimiento": el texto largo ("Añadir a
                    // la biblioteca") se cortaba y quedaba descentrado.
                    text = stringResource(MR.strings.label_library),
                    color = if (inLibrary) inLibraryContent else greyPillTextColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (onTrackingClicked != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(greyPillColor)
                    .auroraSpringClick(onClick = onTrackingClicked),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (trackingCount > 0) Icons.Outlined.Done else Icons.Outlined.Sync,
                        contentDescription = null,
                        tint = greyPillTextColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(MR.strings.manga_tracking_tab),
                        color = greyPillTextColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Title + episode count shown at the top of the scrolled content, as in the
 * second approved mockup.
 */
@Composable
fun AnimeDetailScrolledTitle(
    title: String,
    episodeCountText: String,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = episodeCountText,
            color = colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Synopsis card: 3 lines with an expand arrow that reveals the full text.
 * The arrow only shows when the text actually overflows (or is expanded).
 * v7.9.6: muted source row at the bottom (globe + "Fuente (ES) · Español"),
 * same as the manga synopsis card — the anime detail was missing it.
 */
@Composable
fun AnimeDetailSynopsisCard(
    description: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    sourceTitle: String? = null,
    translationText: String? = null,
    onSourceClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    var hasOverflow by remember(description) { mutableStateOf(false) }
    // Misma línea que en manga: "Fuente (ES) · Español", o solo la fuente.
    val sourceLine = remember(sourceTitle, translationText) {
        when {
            sourceTitle.isNullOrBlank() -> null
            translationText.isNullOrBlank() -> sourceTitle
            else -> "$sourceTitle · $translationText"
        }
    }
    val cardBackground = if (colors.isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.65f)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBackground)
            .auroraSpringClick(onClick = onToggle)
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = description?.takeIf { it.isNotBlank() }
                ?: stringResource(AYMR.strings.aurora_no_description),
            color = colors.textPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (result.hasVisualOverflow && !hasOverflow) {
                    hasOverflow = true
                }
            },
        )
        if (hasOverflow || expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // Fila del proveedor, idéntica a la de manga: globo + texto 12sp
        // en gris, toca para abrir la fuente. (El clic de la fila gana
        // sobre el clic de toda la tarjeta que expande la sinopsis.)
        if (sourceLine != null && onSourceClick != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .auroraSpringClick { onSourceClick() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Public,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = sourceLine,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Horizontal episode carousel (v4): the episodes slide sideways in one
// LazyRow instead of a 2-column vertical grid. The tile is unchanged.
// ---------------------------------------------------------------------------

/** Fixed aurora gradients for episode tiles (no per-item palette work). V5: pares de Color precalculados — antes se creaban dos Color por tarjeta en cada llamada. */
private val animeDetailEpisodeGradients = listOf(
    // Pink -> teal, as in the approved mockup.
    Color(0xFFB84A6B) to Color(0xFF2E7D8C),
    Color(0xFF3E6FB0) to Color(0xFF7B4BA8),
    Color(0xFFC96A3B) to Color(0xFF8C2F4D),
    Color(0xFF3F8F6B) to Color(0xFF2E6B8C),
    Color(0xFF8C5FB0) to Color(0xFF3B4A8C),
)

private fun animeDetailEpisodeGradientColors(episodeId: Long): List<Color> {
    val (top, bottom) = animeDetailEpisodeGradients[
        abs((episodeId % animeDetailEpisodeGradients.size).toInt()) % animeDetailEpisodeGradients.size
    ]
    return listOf(top, bottom)
}

/**
 * Episode tile for the horizontal carousel: gradient (or the episode preview
 * image the list already loads, from the Coil cache), episode name + date over
 * a bottom scrim, seen check at the top end, download indicator and watch
 * progress at the bottom. Click / long-press keep the exact callbacks of the
 * compact list card. Swipe actions are off inside the carousel
 * ([enableSwipe] = false): a horizontal swipe on a tile would fight the
 * carousel's own horizontal scroll; marking seen/read stays available from
 * the selection actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimeEpisodeGridCard(
    item: EpisodeList.Item,
    selected: Boolean,
    isNew: Boolean,
    isAnyEpisodeSelected: Boolean,
    showPreview: Boolean,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEpisodeSwipe: (LibraryPreferences.EpisodeSwipeAction) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    modifier: Modifier = Modifier,
    enableSwipe: Boolean = true,
) {
    val colors = AuroraTheme.colors
    val episode = item.episode
    val hasWatchProgress = episode.seen || episode.totalSeconds > 0L
    val watchProgress = when {
        episode.seen -> 1f
        episode.totalSeconds > 0L -> (
            episode.lastSecondSeen.toFloat() / maxOf(1L, episode.totalSeconds).toFloat()
            ).coerceIn(0f, 1f)
        else -> 0f
    }
    val gradientBrush = remember(episode.id) {
        Brush.verticalGradient(colors = animeDetailEpisodeGradientColors(episode.id))
    }
    val startSwipeAction = animeDetailSwipeAction(
        action = episodeSwipeStartAction,
        seen = episode.seen,
        bookmark = episode.bookmark,
        fillermark = episode.fillermark,
        downloadState = item.downloadState,
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = { onEpisodeSwipe(episodeSwipeStartAction) },
    )
    val endSwipeAction = animeDetailSwipeAction(
        action = episodeSwipeEndAction,
        seen = episode.seen,
        bookmark = episode.bookmark,
        fillermark = episode.fillermark,
        downloadState = item.downloadState,
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = { onEpisodeSwipe(episodeSwipeEndAction) },
    )

    val tileContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(gradientBrush)
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = colors.accent,
                            shape = RoundedCornerShape(16.dp),
                        )
                    } else {
                        Modifier
                    },
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            // Episode preview when the source provides one (same Coil request
            // the compact card already uses); otherwise the gradient is the
            // tile. Never a new per-item download beyond the cached preview.
            if (showPreview && !episode.previewUrl.isNullOrBlank()) {
                ItemCover.Thumb(
                    data = episode.previewUrl,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                )
            }

            // Bottom scrim for the title/date.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.35f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.8f),
                            ),
                        ),
                    ),
            )
            if (episode.seen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = AURORA_ENTRY_READ_DIM_ALPHA * 0.5f)),
                )
            }

            // New-episode dot.
            if (isNew && !episode.seen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }

            // Top-end status: selection check, seen check, or downloaded mark.
            if (isAnyEpisodeSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) colors.accent else Color.Black.copy(alpha = 0.35f),
                        )
                        .then(
                            if (selected) {
                                Modifier
                            } else {
                                Modifier.border(
                                    width = 1.5.dp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    shape = CircleShape,
                                )
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            } else if (episode.seen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.30f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            } else if (item.downloadState == AnimeDownload.State.DOWNLOADED) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.30f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.DownloadDone,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            // Title + date over the scrim, download control at the end.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = episode.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = relativeDateTimeText(episode.dateUpload),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isAnyEpisodeSelected) {
                        PassiveDownloadStatus(
                            downloaded = item.downloadState == AnimeDownload.State.DOWNLOADED,
                            downloading = item.downloadState == AnimeDownload.State.QUEUE ||
                                item.downloadState == AnimeDownload.State.DOWNLOADING,
                        )
                    } else if (onDownloadEpisode != null) {
                        EpisodeDownloadIndicator(
                            enabled = true,
                            downloadStateProvider = { item.downloadState },
                            downloadProgressProvider = { item.downloadProgress },
                            onClick = { onDownloadEpisode(listOf(item), it) },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Watch progress strip at the very bottom.
            if (hasWatchProgress && !episode.seen && watchProgress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.45f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(watchProgress)
                            .height(3.dp)
                            .background(colors.accent),
                    )
                }
            }
        }
    }

    Box(modifier = modifier.aspectRatio(16f / 9f)) {
        if (isAnyEpisodeSelected || !enableSwipe) {
            tileContent()
        } else {
            SwipeableActionsBox(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                startActions = listOfNotNull(startSwipeAction),
                endActions = listOfNotNull(endSwipeAction),
                swipeThreshold = animeDetailSwipeActionThreshold,
                backgroundUntilSwipeThreshold = Color.Transparent,
            ) {
                tileContent()
            }
        }
    }
}

/**
 * Gap placeholder inside the episode carousel: a narrow card marking the
 * missing episodes between two neighbours (same "N faltantes" text as the
 * full-width [MissingItemCountListItem], just sized for the row).
 */
@Composable
fun AnimeCarouselMissingCard(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(120.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = pluralStringResource(AYMR.plurals.missing_items, count = count, count),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

private val animeDetailSwipeActionThreshold = 56.dp

private fun animeDetailSwipeAction(
    action: LibraryPreferences.EpisodeSwipeAction,
    seen: Boolean,
    bookmark: Boolean,
    fillermark: Boolean,
    downloadState: AnimeDownload.State,
    background: Color,
    onSwipe: () -> Unit,
): me.saket.swipe.SwipeAction? {
    return when (action) {
        LibraryPreferences.EpisodeSwipeAction.ToggleSeen -> animeDetailSwipeActionItem(
            icon = if (!seen) Icons.Outlined.Done else Icons.Outlined.RemoveDone,
            background = background,
            isUndo = seen,
            onSwipe = onSwipe,
        )
        LibraryPreferences.EpisodeSwipeAction.ToggleBookmark -> animeDetailSwipeActionItem(
            icon = if (!bookmark) Icons.Outlined.BookmarkAdd else Icons.Outlined.BookmarkRemove,
            background = background,
            isUndo = bookmark,
            onSwipe = onSwipe,
        )
        LibraryPreferences.EpisodeSwipeAction.ToggleFillermark -> animeDetailSwipeActionItem(
            icon = if (!fillermark) Icons.Outlined.NewLabel else Icons.AutoMirrored.Outlined.LabelOff,
            background = background,
            isUndo = fillermark,
            onSwipe = onSwipe,
        )
        LibraryPreferences.EpisodeSwipeAction.Download -> animeDetailSwipeActionItem(
            icon = when (downloadState) {
                AnimeDownload.State.NOT_DOWNLOADED, AnimeDownload.State.ERROR -> Icons.Outlined.Download
                AnimeDownload.State.QUEUE, AnimeDownload.State.DOWNLOADING -> Icons.Outlined.FileDownloadOff
                AnimeDownload.State.DOWNLOADED -> Icons.Outlined.Delete
            },
            background = background,
            onSwipe = onSwipe,
        )
        LibraryPreferences.EpisodeSwipeAction.Disabled -> null
    }
}

private fun animeDetailSwipeActionItem(
    onSwipe: () -> Unit,
    icon: ImageVector,
    background: Color,
    isUndo: Boolean = false,
): me.saket.swipe.SwipeAction {
    return me.saket.swipe.SwipeAction(
        icon = {
            Icon(
                modifier = Modifier.padding(16.dp),
                imageVector = icon,
                tint = contentColorFor(background),
                contentDescription = null,
            )
        },
        background = background,
        onSwipe = onSwipe,
        isUndo = isUndo,
    )
}
