package eu.kanade.presentation.more

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import eu.kanade.tachiyomi.ui.more.MoreEntryId
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Single source of truth for the label of a «More» screen entry, shared by the screen itself and by
 * the menu customization screen so a renamed entry cannot drift apart.
 *
 * [movedTabTitle] is resolved by the caller: it comes from `navStyle.moreTab.options`, which may only
 * be read inside the bottom-nav tab navigator.
 */
@Composable
internal fun MoreEntryTitle(
    id: MoreEntryId,
    movedTabTitle: String,
): String {
    return when (id) {
        MoreEntryId.MOVED_TAB -> movedTabTitle
        MoreEntryId.SETTINGS -> stringResource(AYMR.strings.aurora_settings)
        MoreEntryId.PLAYER_SETTINGS -> stringResource(AYMR.strings.aurora_player_settings)
        MoreEntryId.READER_MANGA -> stringResource(MR.strings.pref_category_reader)
        MoreEntryId.READER_NOVEL -> stringResource(AYMR.strings.pref_category_novel_reader)
        MoreEntryId.DATA_STORAGE -> stringResource(AYMR.strings.aurora_data_storage)
        MoreEntryId.UPDATE_ERRORS -> stringResource(AYMR.strings.option_label_library_update_errors)
        MoreEntryId.DOWNLOADS -> stringResource(AYMR.strings.aurora_downloads)
        MoreEntryId.CATEGORIES -> stringResource(AYMR.strings.aurora_categories)
        MoreEntryId.DOWNLOADED_ONLY -> stringResource(AYMR.strings.aurora_downloaded_only)
        MoreEntryId.INCOGNITO -> stringResource(AYMR.strings.aurora_incognito_mode)
        MoreEntryId.ABOUT -> stringResource(AYMR.strings.aurora_about)
        MoreEntryId.DEBUG_APP_UPDATE -> stringResource(AYMR.strings.debug_app_update_preview)
        MoreEntryId.DEBUG_CHANGELOG -> stringResource(AYMR.strings.debug_updated_changelog_preview)
        MoreEntryId.HELP -> stringResource(AYMR.strings.aurora_help)
    }
}

/** Icon of a «More» screen entry; [movedTabIcon] is `navStyle.moreIcon` resolved by the caller. */
internal fun moreEntryIcon(
    id: MoreEntryId,
    movedTabIcon: ImageVector,
): ImageVector {
    return when (id) {
        MoreEntryId.MOVED_TAB -> movedTabIcon
        MoreEntryId.SETTINGS -> Icons.Filled.Settings
        MoreEntryId.PLAYER_SETTINGS -> Icons.Outlined.VideoSettings
        MoreEntryId.READER_MANGA -> Icons.AutoMirrored.Outlined.ChromeReaderMode
        MoreEntryId.READER_NOVEL -> Icons.Outlined.Book
        MoreEntryId.DATA_STORAGE -> Icons.Outlined.Storage
        MoreEntryId.UPDATE_ERRORS -> Icons.Outlined.ReportProblem
        MoreEntryId.DOWNLOADS -> Icons.Filled.Download
        MoreEntryId.CATEGORIES -> Icons.AutoMirrored.Outlined.Label
        MoreEntryId.DOWNLOADED_ONLY -> Icons.Filled.CloudOff
        MoreEntryId.INCOGNITO -> Icons.Outlined.VisibilityOff
        MoreEntryId.ABOUT -> Icons.Filled.Info
        MoreEntryId.DEBUG_APP_UPDATE -> Icons.Outlined.NewReleases
        MoreEntryId.DEBUG_CHANGELOG -> Icons.Outlined.NewReleases
        MoreEntryId.HELP -> Icons.AutoMirrored.Filled.Help
    }
}
