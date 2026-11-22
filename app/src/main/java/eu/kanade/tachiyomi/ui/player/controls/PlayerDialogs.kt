package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.data.database.models.anime.Episode
import eu.kanade.tachiyomi.ui.player.Dialogs
import eu.kanade.tachiyomi.ui.player.controls.components.dialogs.IntegerPickerDialog
import eu.kanade.tachiyomi.ui.player.controls.components.dialogs.NuvioEpisodePanel
import tachiyomi.domain.entries.anime.model.Anime
import java.time.format.DateTimeFormatter

@Composable
fun PlayerDialogs(
    dialogShown: Dialogs,

    // Episode list
    episodeDisplayMode: Long?,
    currentEpisodeIndex: Int,
    episodeList: List<Episode>,
    dateRelativeTime: Boolean,
    dateFormat: DateTimeFormatter,
    anime: Anime?,
    onEpisodeClicked: (Long?) -> Unit,

    onDismissRequest: () -> Unit,
) {
    when (dialogShown) {
        Dialogs.None -> {}
        Dialogs.EpisodeList -> {
            if (anime != null) {
                NuvioEpisodePanel(
                    displayMode = episodeDisplayMode,
                    currentEpisodeIndex = currentEpisodeIndex,
                    episodeList = episodeList,
                    dateRelativeTime = dateRelativeTime,
                    dateFormat = dateFormat,
                    anime = anime,
                    onEpisodeClicked = onEpisodeClicked,
                    onDismissRequest = onDismissRequest,
                )
            }
        }
        is Dialogs.IntegerPicker -> {
            IntegerPickerDialog(
                defaultValue = dialogShown.defaultValue,
                minValue = dialogShown.minValue,
                maxValue = dialogShown.maxValue,
                step = dialogShown.step,
                nameFormat = dialogShown.nameFormat,
                title = dialogShown.title,
                onChange = dialogShown.onChange,
                onDismissRequest = dialogShown.onDismissRequest,
            )
        }
    }
}
