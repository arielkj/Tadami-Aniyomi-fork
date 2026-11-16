package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.description.DescriptionEngine
import eu.kanade.presentation.entries.components.ExpandableDescriptionBlocks
import eu.kanade.presentation.entries.components.aurora.auroraSpringClick
import eu.kanade.presentation.entries.components.auroraDescriptionBlockStyle
import eu.kanade.presentation.entries.translation.AuroraEntryTranslationState
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Synopsis card from the approved design: a dark card with the description
 * clipped to 3 lines and a chevron that expands the full text. A muted source
 * row at the bottom keeps the source (and scanlator/language, when known)
 * reachable after [MangaStatsCard] was removed from the immersive layout.
 */
@Composable
fun MangaSynopsisCard(
    manga: Manga,
    translation: AuroraEntryTranslationState? = null,
    descriptionExpanded: Boolean,
    onToggleDescription: () -> Unit,
    sourceTitle: String? = null,
    translationText: String? = null,
    onSourceClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    var hasDescriptionOverflow by remember { mutableStateOf(false) }
    val displayDescription = translation?.description ?: manga.displayDescription
    val descriptionBlocks = remember(displayDescription) {
        displayDescription?.let { DescriptionEngine.beautify(it) } ?: emptyList()
    }
    val sourceLine = remember(sourceTitle, translationText) {
        when {
            sourceTitle.isNullOrBlank() -> null
            translationText.isNullOrBlank() -> sourceTitle
            else -> "$sourceTitle · $translationText"
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (colors.isDark) {
                    Color.White.copy(alpha = 0.07f)
                } else {
                    colors.surface.copy(alpha = 0.6f)
                },
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (descriptionBlocks.isEmpty()) {
            Text(
                text = stringResource(AYMR.strings.aurora_no_description),
                color = colors.textPrimary.copy(alpha = 0.9f),
                fontSize = 14.sp,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            ExpandableDescriptionBlocks(
                blocks = descriptionBlocks,
                expanded = descriptionExpanded,
                onToggle = onToggleDescription,
                style = auroraDescriptionBlockStyle(colors),
                collapsedLines = 3,
                onOverflowChanged = { hasDescriptionOverflow = it },
                modifier = Modifier.then(
                    if (hasDescriptionOverflow) {
                        Modifier.auroraSpringClick { onToggleDescription() }
                    } else {
                        Modifier
                    },
                ),
            )
            if (hasDescriptionOverflow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Icon(
                        imageVector = if (descriptionExpanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier
                            .size(24.dp)
                            .auroraSpringClick { onToggleDescription() },
                    )
                }
            }
        }

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
