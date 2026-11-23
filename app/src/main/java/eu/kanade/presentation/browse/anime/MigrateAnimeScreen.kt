package eu.kanade.presentation.browse.anime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.entries.anime.components.BaseAnimeListItem
import eu.kanade.tachiyomi.ui.browse.anime.migration.anime.MigrateAnimeScreenModel
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun MigrateAnimeScreen(
    navigateUp: () -> Unit,
    title: String?,
    state: MigrateAnimeScreenModel.State,
    onClickItem: (Anime) -> Unit,
    onClickCover: (Anime) -> Unit,
    onClickAutoMigrate: (() -> Unit)? = null,
    // v7.9.35: progress of this source's active auto-migration run,
    // shown at the top of the list (outside the run's own screen).
    autoMigrationProgressBanner: @Composable (() -> Unit)? = null,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = title,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
                actions = {
                    if (onClickAutoMigrate != null) {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.auto_migration),
                                    icon = Icons.Outlined.Sync,
                                    onClick = onClickAutoMigrate,
                                ),
                            ),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        MigrateAnimeContent(
            contentPadding = contentPadding,
            state = state,
            onClickItem = onClickItem,
            onClickCover = onClickCover,
            autoMigrationProgressBanner = autoMigrationProgressBanner,
        )
    }
}

@Composable
private fun MigrateAnimeContent(
    contentPadding: PaddingValues,
    state: MigrateAnimeScreenModel.State,
    onClickItem: (Anime) -> Unit,
    onClickCover: (Anime) -> Unit,
    autoMigrationProgressBanner: @Composable (() -> Unit)? = null,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        if (autoMigrationProgressBanner != null) {
            item(key = "auto-migration-progress") {
                Box(
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
                ) {
                    autoMigrationProgressBanner()
                }
            }
        }
        items(state.titles) { anime ->
            MigrateAnimeItem(
                anime = anime,
                onClickItem = onClickItem,
                onClickCover = onClickCover,
            )
        }
    }
}

@Composable
private fun MigrateAnimeItem(
    anime: Anime,
    onClickItem: (Anime) -> Unit,
    onClickCover: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseAnimeListItem(
        modifier = modifier,
        anime = anime,
        onClickItem = { onClickItem(anime) },
        onClickCover = { onClickCover(anime) },
    )
}
