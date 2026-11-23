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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton

/**
 * Nuvio-style bottom action row: subtitles, episodes, download, PiP,
 * playback speed, quality (servers/qualities) and more — with the mini
 * timeline centered in the middle of the row and the position / duration
 * clock at the far right (tap it to switch between total and remaining
 * time). The quality button uses the old player's HighQuality glyph on
 * purpose, so it never looks like the episodes button (square with
 * lines). The icons are kept small (22dp) so the mini bar fits on the
 * same row.
 */
@Composable
fun BottomRightPlayerControls(
    positionText: String,
    onSubtitlesClick: () -> Unit,
    onSubtitlesLongClick: () -> Unit,
    onEpisodesClick: () -> Unit,
    onDownloadClick: () -> Unit,
    isPipAvailable: Boolean,
    onPipClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onSpeedLongClick: () -> Unit,
    onQualityClick: () -> Unit,
    onMoreClick: () -> Unit,
    onTimeClick: () -> Unit,
    miniSeekbarContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ControlsButton(
            icon = Icons.Default.ClosedCaption,
            onClick = onSubtitlesClick,
            onLongClick = onSubtitlesLongClick,
            horizontalSpacing = 4.dp,
            iconSize = 22.dp,
        )
        ControlsButton(
            onClick = onEpisodesClick,
            horizontalSpacing = 4.dp,
            iconSize = 22.dp,
        ) {
            NuvioEpisodesIcon()
        }
        ControlsButton(
            icon = Icons.Default.CloudDownload,
            onClick = onDownloadClick,
            horizontalSpacing = 4.dp,
            iconSize = 22.dp,
        )
        if (isPipAvailable) {
            ControlsButton(
                icon = Icons.Default.PictureInPictureAlt,
                onClick = onPipClick,
                horizontalSpacing = 4.dp,
                iconSize = 22.dp,
            )
        }
        ControlsButton(
            icon = Icons.Default.Speed,
            onClick = onSpeedClick,
            onLongClick = onSpeedLongClick,
            horizontalSpacing = 4.dp,
            iconSize = 22.dp,
        )
        ControlsButton(
            icon = Icons.Default.HighQuality,
            onClick = onQualityClick,
            horizontalSpacing = 4.dp,
            iconSize = 22.dp,
        )
        ControlsButton(
            icon = Icons.Outlined.ChevronRight,
            onClick = onMoreClick,
            horizontalSpacing = 4.dp,
            iconSize = 22.dp,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            miniSeekbarContent()
        }
        val interactionSource = remember { MutableInteractionSource() }
        Text(
            text = positionText,
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onTimeClick,
                ),
        )
    }
}

/**
 * The episodes glyph from the Nuvio design: a small square with
 * three lines above it.
 */
@Composable
private fun NuvioEpisodesIcon(
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.size(22.dp),
    ) {
        val stroke = 1.8.dp.toPx()
        val w = size.width
        val h = size.height
        // Three lines above the square.
        listOf(0.10f, 0.24f, 0.38f).forEach { fraction ->
            val y = h * fraction
            drawLine(
                color = Color.White,
                start = Offset(w * 0.08f, y),
                end = Offset(w * 0.92f, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        // The square below them.
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.08f, h * 0.54f),
            size = Size(w * 0.84f, h * 0.38f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = stroke),
        )
    }
}
