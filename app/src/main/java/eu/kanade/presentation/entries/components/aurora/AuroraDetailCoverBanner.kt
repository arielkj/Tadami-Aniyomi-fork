package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.FinaleStamp
import eu.kanade.tachiyomi.data.coil.AuroraPosterRequest
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

/**
 * v7.9.40 — tablet/TV two-pane detail: the cover contained in a card instead
 * of a full-bleed poster backdrop. The wide detail screen is pure black
 * (#000000) everywhere, so the cover only lives inside this 16:9 card at the
 * top of the left pane; the bottom of the card fades to black so it melts
 * into the pane. Shared by the anime and manga two-pane detail screens.
 */
@Composable
fun AuroraDetailCoverBanner(
    entryId: Long,
    posterRequest: AuroraPosterRequest,
    previewCoverModel: Any?,
    placeholderPainter: Painter,
    reloadTick: Int,
    onCoverClicked: () -> Unit,
    showFinishedStamp: Boolean,
    finishedStampDate: String?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val appHaptics = LocalAppHaptics.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                appHaptics.tap()
                onCoverClicked()
            },
    ) {
        AuroraHeroCoverImage(
            entryId = entryId,
            posterRequest = posterRequest,
            previewCoverModel = previewCoverModel,
            placeholderPainter = placeholderPainter,
            reloadTick = reloadTick,
            modifier = Modifier.matchParentSize(),
        )

        // Bottom fade into the pure-black pane.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
                ),
        )

        // Keepsake «finished» stamp, same as the glass hero card.
        if (showFinishedStamp && LocalLayoutDirection.current != LayoutDirection.Rtl) {
            FinaleStamp(
                label = stringResource(MR.strings.reader_finale_stamp_label),
                date = finishedStampDate,
                accent = accent,
                size = 76.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp),
            )
        }
    }
}
