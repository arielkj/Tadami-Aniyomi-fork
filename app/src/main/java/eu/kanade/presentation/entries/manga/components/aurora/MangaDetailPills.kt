package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Library / tracking pills from the approved design, shown right below the hero
 * once the user scrolls. Both pills share the row half-and-half, centered:
 * "Biblioteca" (heart; white when the manga is in the library, grey otherwise;
 * long-press edits categories) and "Seguimiento" (white when at least one
 * tracker is linked, grey otherwise).
 *
 * This replaces [MangaActionCard] on the immersive detail screen; favorite,
 * tracking, webview and share keep working through these pills and the
 * overflow menu (see the screen's overflow actions).
 */
@Composable
fun MangaDetailLibraryPills(
    manga: Manga,
    trackingCount: Int,
    onAddToLibraryClicked: () -> Unit,
    onAddToLibraryLongClicked: (() -> Unit)? = null,
    onTrackingClicked: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // v4: las dos píldoras reparten la fila a partes iguales y centran su
    // contenido, como en el detalle de anime. La de biblioteca solo dice
    // "Biblioteca": el texto largo ("Añadir a la biblioteca") se cortaba.
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MangaDetailPill(
            active = manga.favorite,
            icon = if (manga.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            label = stringResource(MR.strings.label_library),
            onClick = onAddToLibraryClicked,
            onLongClick = onAddToLibraryLongClicked,
            modifier = Modifier.weight(1f),
        )
        if (onTrackingClicked != null) {
            MangaDetailPill(
                active = trackingCount > 0,
                icon = Icons.Outlined.Done,
                label = if (trackingCount > 0) {
                    pluralStringResource(MR.plurals.num_trackers, count = trackingCount, trackingCount)
                } else {
                    stringResource(MR.strings.manga_tracking_tab)
                },
                onClick = onTrackingClicked,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaDetailPill(
    active: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val containerColor = when {
        active -> Color.White
        colors.isDark -> Color.White.copy(alpha = 0.14f)
        else -> colors.surface.copy(alpha = 0.7f)
    }
    val contentColor = when {
        active -> Color.Black
        colors.isDark -> Color.White
        else -> colors.textPrimary
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
