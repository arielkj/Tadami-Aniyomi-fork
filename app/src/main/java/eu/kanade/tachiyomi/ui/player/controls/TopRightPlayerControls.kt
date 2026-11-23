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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import tachiyomi.presentation.core.components.material.padding

/**
 * Nuvio-style top-right row: aspect ratio, watch party, lock, back.
 * The watch party has no backend in Tadami: tapping it shows a notice.
 */
@Composable
fun TopRightPlayerControls(
    onAspectClick: () -> Unit,
    onWatchPartyClick: () -> Unit,
    onLockControls: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        ControlsButton(
            icon = Icons.Default.AspectRatio,
            onClick = onAspectClick,
            horizontalSpacing = 8.dp,
            iconSize = 22.dp,
        )
        ControlsButton(
            icon = Icons.Default.Group,
            onClick = onWatchPartyClick,
            horizontalSpacing = 8.dp,
            iconSize = 22.dp,
        )
        ControlsButton(
            icon = Icons.Default.Lock,
            onClick = onLockControls,
            horizontalSpacing = 8.dp,
            iconSize = 22.dp,
        )
        ControlsButton(
            icon = Icons.AutoMirrored.Default.ArrowBack,
            onClick = onBackClick,
            horizontalSpacing = 8.dp,
            iconSize = 22.dp,
        )
    }
}
