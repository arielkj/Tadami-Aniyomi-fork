package eu.kanade.domain.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.tadami.aurora.R
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

enum class NavStyle(
    val titleRes: StringResource,
    val moreTab: Tab,
) {
    MOVE_MANGA_TO_MORE(titleRes = AYMR.strings.pref_bottom_nav_no_manga, moreTab = AnimeLibraryTab),
    MOVE_UPDATES_TO_MORE(titleRes = AYMR.strings.pref_bottom_nav_no_updates, moreTab = UpdatesTab),
    MOVE_HISTORY_TO_MORE(titleRes = AYMR.strings.pref_bottom_nav_no_history, moreTab = HistoriesTab),
    MOVE_BROWSE_TO_MORE(titleRes = AYMR.strings.pref_bottom_nav_no_browse, moreTab = BrowseTab),
    ;

    val moreIcon: ImageVector
        @Composable
        get() = when (this) {
            MOVE_MANGA_TO_MORE -> Icons.Outlined.CollectionsBookmark
            MOVE_UPDATES_TO_MORE -> ImageVector.vectorResource(id = R.drawable.ic_updates_outline_24dp)
            MOVE_HISTORY_TO_MORE -> Icons.Outlined.History
            MOVE_BROWSE_TO_MORE -> Icons.Outlined.Explore
        }

    /**
     * Título de la pestaña movida a «Más», sin leer `moreTab.options`.
     *
     * v7.9.3 (crash 20 Nov, SM-G998U): `Tab.options` calcula `isSelected`
     * con `LocalTabNavigator.current`, que lanza IllegalStateException
     * cuando la pantalla se empuja como pantalla normal, sin TabNavigator
     * ancestro — que es justo como se abre Más desde la v7.9.2. Compose
     * prohíbe blindar esa lectura con runCatching o try/catch, así que el
     * título se resuelve aquí, con los mismos recursos que usa cada
     * pestaña en su `options`. Si una pestaña cambia su título, hay que
     * cambiarlo también aquí.
     */
    val movedTabTitle: String
        @Composable
        get() = when (moreTab) {
            is HistoriesTab -> stringResource(MR.strings.history)
            is BrowseTab -> stringResource(MR.strings.browse)
            is AnimeLibraryTab -> stringResource(AYMR.strings.label_titles)
            is UpdatesTab -> stringResource(MR.strings.label_recent_updates)
            else -> stringResource(MR.strings.label_more)
        }

    val tabs: List<Tab>
        get() {
            return mutableListOf(
                AnimeLibraryTab,
                UpdatesTab,
                HistoriesTab,
                BrowseTab,
                MoreTab,
            ).apply { remove(this@NavStyle.moreTab) }
        }
}
