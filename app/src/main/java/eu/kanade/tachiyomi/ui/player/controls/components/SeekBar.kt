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

package eu.kanade.tachiyomi.ui.player.controls.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.vivvvek.seeker.Seeker
import dev.vivvvek.seeker.SeekerDefaults
import dev.vivvvek.seeker.Segment
import eu.kanade.tachiyomi.animesource.model.ChapterType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class IndexedSegment(
    val name: String,
    val start: Float,
    val color: Color = Color.Unspecified,
    val index: Int = 0,
    val chapterType: ChapterType = ChapterType.Other,
) {
    companion object {
        val Unspecified = IndexedSegment(name = "", start = 0f)
    }

    fun toSegment(): Segment = Segment(name, start, color)
}

/**
 * Nuvio-style mini progress bar: the small centered bar in the bottom
 * action row. It is the only timeline — dragging it seeks directly.
 * Chapter markers are kept on the bar.
 */
@Composable
fun MiniSeekbar(
    position: Float,
    duration: Float,
    readAheadValue: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    chapters: ImmutableList<Segment>,
    modifier: Modifier = Modifier,
) {
    Seeker(
        value = position.coerceIn(0f, duration),
        range = 0f..duration,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        readAheadValue = readAheadValue,
        segments = chapters
            .filter { it.start in 0f..duration }
            .let {
                // add an extra segment at 0 if it doesn't exist.
                if (it.isNotEmpty() && it[0].start != 0f) {
                    persistentListOf(Segment("", 0f)) + it
                } else {
                    it
                } + it
            },
        modifier = modifier
            .height(20.dp),
        colors = SeekerDefaults.seekerColors(
            progressColor = Color.White,
            thumbColor = Color.White,
            trackColor = Color.White.copy(alpha = 0.35f),
            readAheadColor = Color.White.copy(alpha = 0.6f),
        ),
    )
}

@Preview
@Composable
private fun PreviewMiniSeekBar() {
    MiniSeekbar(
        5f,
        20f,
        4f,
        {},
        {},
        persistentListOf(),
        modifier = Modifier.fillMaxWidth(0.45f),
    )
}
