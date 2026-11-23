package eu.kanade.presentation.more.settings.screen.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.sync.DeviceSyncPreferences
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.canScroll
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.data.sync.direct.DirectSyncManager
import eu.kanade.tachiyomi.data.sync.direct.DirectSyncState
import eu.kanade.presentation.util.Screen
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.aniyomi.AYMR
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DateFormat
import java.util.Date

/**
 * v7.9.36 — "Sincronización" settings screen: direct device-to-device library sync.
 *
 * No account, no server: both devices on the same WiFi (or the phone sharing its
 * hotspot), one-time pairing with a 6-digit code. First sync sends the whole selected
 * library; later syncs send only what changed. Downloads are never synced.
 * v7.9.38 — pairing is ONE-SIDED: only one device types the other's address
 * and code; the other links itself automatically.
 */
class DeviceSyncScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { DeviceSyncScreenModel() }
        val uiStyle = rememberResolvedSettingsUiStyle()
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        val prefs = model.prefs
        val mode by prefs.mode().collectAsState()
        val pairCode by prefs.pairCode().collectAsState()
        val pairedName by prefs.pairedDeviceName().collectAsState()
        val pairedAddress by prefs.pairedDeviceAddress().collectAsState()
        val lastSyncAt by prefs.lastSyncAt().collectAsState()
        val excludedAnime by prefs.excludedAnimeCategories().collectAsState()
        val excludedManga by prefs.excludedMangaCategories().collectAsState()
        val excludedNovel by prefs.excludedNovelCategories().collectAsState()
        val syncState by model.manager.state.collectAsState()
        val categories by model.categories.collectAsState()
        val serverInfo by model.serverInfo.collectAsState()

        var peerAddress by rememberSaveable { mutableStateOf(pairedAddress) }
        var peerCode by rememberSaveable { mutableStateOf("") }
        var pairMessage by rememberSaveable { mutableStateOf<String?>(null) }
        var expandedAnime by rememberSaveable { mutableStateOf(false) }
        var expandedManga by rememberSaveable { mutableStateOf(false) }
        var expandedNovel by rememberSaveable { mutableStateOf(false) }

        SettingsScaffold(
            title = stringResource(MR.strings.device_sync_title),
            uiStyle = uiStyle,
            onBackPressed = navigator::pop,
            topBarCanScroll = { listState.canScroll() },
        ) { contentPadding ->
            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                item {
                    PreferenceGroupHeader(title = stringResource(MR.strings.device_sync_mode))
                }
                item {
                    ListPreferenceWidget(
                        value = mode,
                        title = stringResource(MR.strings.device_sync_mode),
                        subtitle = if (mode == DeviceSyncPreferences.MODE_AUTO) {
                            stringResource(MR.strings.device_sync_mode_auto)
                        } else {
                            stringResource(MR.strings.device_sync_mode_manual)
                        },
                        icon = Icons.Outlined.Sync,
                        entries = mapOf(
                            DeviceSyncPreferences.MODE_MANUAL to stringResource(MR.strings.device_sync_mode_manual),
                            DeviceSyncPreferences.MODE_AUTO to stringResource(MR.strings.device_sync_mode_auto),
                        ),
                        onValueChange = { prefs.mode().set(it) },
                    )
                }
                item {
                    Text(
                        text = stringResource(MR.strings.device_sync_mode_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                item {
                    PreferenceGroupHeader(title = stringResource(MR.strings.device_sync_what))
                }
                item {
                    Text(
                        text = stringResource(MR.strings.device_sync_what_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                item {
                    CategorySection(
                        title = stringResource(AYMR.strings.label_anime),
                        categories = categories.anime,
                        excluded = excludedAnime,
                        expanded = expandedAnime,
                        onToggleExpand = { expandedAnime = !expandedAnime },
                        onToggle = { model.toggleCategory("anime", it) },
                    )
                }
                item {
                    CategorySection(
                        title = stringResource(AYMR.strings.label_manga),
                        categories = categories.manga,
                        excluded = excludedManga,
                        expanded = expandedManga,
                        onToggleExpand = { expandedManga = !expandedManga },
                        onToggle = { model.toggleCategory("manga", it) },
                    )
                }
                item {
                    CategorySection(
                        title = stringResource(MR.strings.device_sync_novels),
                        categories = categories.novel,
                        excluded = excludedNovel,
                        expanded = expandedNovel,
                        onToggleExpand = { expandedNovel = !expandedNovel },
                        onToggle = { model.toggleCategory("novel", it) },
                    )
                }

                item {
                    PreferenceGroupHeader(title = stringResource(MR.strings.device_sync_this_device))
                }
                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.device_sync_pair_code),
                        subtitle = pairCode.ifBlank { stringResource(MR.strings.device_sync_code_not_generated) },
                        icon = Icons.Outlined.Devices,
                        onPreferenceClick = {
                            model.regenerateCode()
                        },
                    )
                }
                item {
                    Text(
                        text = stringResource(MR.strings.device_sync_pair_code_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                item {
                    TextPreferenceWidget(
                        title = stringResource(MR.strings.device_sync_address),
                        subtitle = serverInfo ?: stringResource(MR.strings.device_sync_server_off),
                        icon = Icons.Outlined.Devices,
                        onPreferenceClick = {
                            model.refreshServerInfo()
                        },
                    )
                }
                item {
                    Text(
                        text = stringResource(MR.strings.device_sync_address_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                item {
                    PreferenceGroupHeader(title = stringResource(MR.strings.device_sync_paired_device))
                }
                item {
                    Text(
                        text = if (pairedName.isNotBlank()) {
                            stringResource(MR.strings.device_sync_paired_with, pairedName, pairedAddress)
                        } else {
                            stringResource(MR.strings.device_sync_no_paired_device)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = peerAddress,
                        onValueChange = { peerAddress = it },
                        label = { Text(stringResource(MR.strings.device_sync_peer_address)) },
                        placeholder = { Text("192.168.1.5:27801") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = peerCode,
                        onValueChange = { peerCode = it },
                        label = { Text(stringResource(MR.strings.device_sync_peer_code)) },
                        placeholder = { Text("123456") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
                item {
                    Button(
                        onClick = {
                            scope.launch {
                                pairMessage = null
                                // v7.9.38: pairing is one-sided — only this
                                // device types anything; the other links
                                // itself automatically.
                                val result = model.pair(peerAddress, peerCode)
                                pairMessage = when (result) {
                                    "ok" -> context.stringResource(MR.strings.device_sync_pair_success)
                                    "invalid_address" -> context.stringResource(MR.strings.device_sync_error_invalid_address)
                                    else -> context.stringResource(MR.strings.device_sync_pair_failed)
                                }
                            }
                        },
                        enabled = peerAddress.isNotBlank() && peerCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text(stringResource(MR.strings.device_sync_pair))
                    }
                }
                if (pairMessage != null) {
                    item {
                        Text(
                            text = pairMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
                item {
                    Button(
                        onClick = {
                            scope.launch { model.syncNow() }
                        },
                        enabled = pairedAddress.isNotBlank() && syncState !is DirectSyncState.Running,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (syncState is DirectSyncState.Running) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(stringResource(MR.strings.device_sync_now))
                    }
                }
                item {
                    // v7.9.38/39: the error is a specific code now, not the raw
                    // exception text — the screen shows what actually went
                    // wrong (no connection, wrong code, bad address…) so the
                    // next failure is diagnosable from the screen itself.
                    // v7.9.39: the state carries "code" or "code|detail"
                    // (the server's error text, the exception name…).
                    val statusText = when (val s = syncState) {
                        is DirectSyncState.Success -> stringResource(MR.strings.device_sync_success)
                        is DirectSyncState.Error -> {
                            val code = s.message.substringBefore("|")
                            val detail = s.message.substringAfter("|", "")
                            when (code) {
                                "no_paired_device" -> stringResource(MR.strings.device_sync_no_paired_device)
                                "invalid_address" -> stringResource(MR.strings.device_sync_error_invalid_address)
                                "unreachable_host" -> stringResource(MR.strings.device_sync_error_unreachable_host)
                                "cannot_connect" -> stringResource(MR.strings.device_sync_error_cannot_connect)
                                "wrong_code" -> stringResource(MR.strings.device_sync_error_wrong_code)
                                "bad_response" -> stringResource(MR.strings.device_sync_error_bad_response)
                                "apply_failed" -> stringResource(MR.strings.device_sync_error_apply_failed)
                                "server_error" -> stringResource(
                                    MR.strings.device_sync_error_server_error,
                                    detail.ifBlank { s.message },
                                )
                                "timeout" -> stringResource(MR.strings.device_sync_error_timeout)
                                "build_failed" -> stringResource(MR.strings.device_sync_error_build_failed)
                                "bad_payload" -> stringResource(MR.strings.device_sync_error_bad_payload)
                                else -> stringResource(MR.strings.device_sync_failed, detail.ifBlank { s.message })
                            }
                        }
                        else -> null
                    }
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        if (statusText != null) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (lastSyncAt > 0L) {
                            Text(
                                text = stringResource(
                                    MR.strings.device_sync_last,
                                    DateFormat.getDateTimeInstance().format(Date(lastSyncAt)),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    @Composable
    private fun CategorySection(
        title: String,
        categories: List<SyncCategoryOption>,
        excluded: Set<String>,
        expanded: Boolean,
        onToggleExpand: () -> Unit,
        onToggle: (Long) -> Unit,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextPreferenceWidget(
                title = title,
                subtitle = if (categories.isEmpty()) {
                    stringResource(MR.strings.device_sync_all_categories)
                } else {
                    val selected = categories.count { it.id.toString() !in excluded }
                    stringResource(MR.strings.device_sync_selected_count, selected, categories.size)
                },
                icon = null,
                onPreferenceClick = onToggleExpand,
            )
            if (expanded) {
                if (categories.isEmpty()) {
                    Text(
                        text = stringResource(MR.strings.device_sync_no_categories),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
                    )
                } else {
                    categories.forEach { category ->
                        LabeledCheckbox(
                            label = category.name,
                            checked = category.id.toString() !in excluded,
                            onCheckedChange = { onToggle(category.id) },
                        )
                    }
                }
            }
        }
    }
}

private data class SyncCategoryOption(
    val id: Long,
    val name: String,
)

private data class SyncCategories(
    val anime: List<SyncCategoryOption> = emptyList(),
    val manga: List<SyncCategoryOption> = emptyList(),
    val novel: List<SyncCategoryOption> = emptyList(),
)

private class DeviceSyncScreenModel(
    val prefs: DeviceSyncPreferences = Injekt.get(),
    val manager: DirectSyncManager = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val getMangaCategories: GetMangaCategories = Injekt.get(),
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
) : StateScreenModel<SyncCategories>(SyncCategories()) {

    val categories: StateFlow<SyncCategories> = state

    private val _serverInfo = MutableStateFlow<String?>(null)
    val serverInfo: StateFlow<String?> = _serverInfo.asStateFlow()

    init {
        screenModelScope.launch {
            runCatching { manager.startServer() }
            refreshServerInfo()
            val anime = runCatching { getAnimeCategories.await() }.getOrNull().orEmpty()
                .filterNot { it.isSystemCategory }
                .map { SyncCategoryOption(it.id, it.name) }
            val manga = runCatching { getMangaCategories.await() }.getOrNull().orEmpty()
                .filterNot { it.isSystemCategory }
                .map { SyncCategoryOption(it.id, it.name) }
            val novel = runCatching { getNovelCategories.await() }.getOrNull().orEmpty()
                .filter { it.id > 0L }
                .map { SyncCategoryOption(it.id, it.name) }
            mutableState.update { SyncCategories(anime, manga, novel) }
        }
    }

    fun refreshServerInfo() {
        _serverInfo.value = manager.localAddressText()
    }

    fun regenerateCode() {
        manager.regeneratePairCode()
    }

    fun toggleCategory(kind: String, categoryId: Long) {
        val id = categoryId.toString()
        when (kind) {
            "anime" -> {
                val current = prefs.excludedAnimeCategories().get()
                prefs.excludedAnimeCategories().set(if (id in current) current - id else current + id)
            }
            "manga" -> {
                val current = prefs.excludedMangaCategories().get()
                prefs.excludedMangaCategories().set(if (id in current) current - id else current + id)
            }
            "novel" -> {
                val current = prefs.excludedNovelCategories().get()
                prefs.excludedNovelCategories().set(if (id in current) current - id else current + id)
            }
        }
    }

    /**
     * v7.9.38 — returns the pairing result: "ok", "invalid_address" (the
     * typed address cannot be used) or "failed". Pairing is one-sided:
     * only this device types anything; the peer links itself.
     */
    suspend fun pair(address: String, code: String): String {
        return try {
            val result = manager.pair(address, code)
            refreshServerInfo()
            result
        } catch (e: Exception) {
            "failed"
        }
    }

    suspend fun syncNow() {
        manager.syncNow()
        refreshServerInfo()
    }
}
