package eu.kanade.presentation.entries.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.coil.ai.CoverAiEnhancer
import eu.kanade.tachiyomi.data.coil.ai.RealEsrganModelDownloader
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * v7.9.20 Fase 2 — dialog for the manual «Mejorar portada» action.
 *
 * On open it starts the enhancement at once (load the original cover →
 * download the model once, WiFi only → upscale x2 on the Vulkan GPU →
 * apply the user's Fase 1 colour level), then shows the before/after
 * comparison so the user can keep or discard it. Nothing is saved until
 * the user taps «Guardar»; the detail, grid and «Seguir viendo» reload
 * from the saved file afterwards (coverLastModified bump).
 *
 * The enhanced bitmap is recycled when the dialog leaves composition.
 * The original bitmap is Coil-owned and is never recycled here.
 */
@Composable
fun AiCoverEnhanceDialog(
    kind: CoverAiEnhancer.Kind,
    entryId: Long,
    coverUrl: String?,
    fallbackCoverUrl: String?,
    onDismissRequest: () -> Unit,
    onSaved: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<Phase>(Phase.Running) }
    var downloadingModel by remember { mutableStateOf(false) }
    var showAfter by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf<CoverAiEnhancer.Result.Success?>(null) }

    DisposableEffect(success) {
        onDispose {
            success?.enhanced?.takeUnless { it.isRecycled }?.recycle()
        }
    }

    LaunchedEffect(Unit) {
        downloadingModel = !RealEsrganModelDownloader.isDownloaded(context)
        phase = when (val result = CoverAiEnhancer.enhance(context, kind, entryId, coverUrl, fallbackCoverUrl)) {
            is CoverAiEnhancer.Result.Success -> {
                success = result
                Phase.Compare
            }
            CoverAiEnhancer.Result.Disabled -> Phase.Error(MR.strings.ai_cover_unsupported)
            CoverAiEnhancer.Result.UnsupportedDevice -> Phase.Error(MR.strings.ai_cover_unsupported)
            CoverAiEnhancer.Result.ModelDownloadNeedsWifi -> Phase.Error(MR.strings.ai_cover_needs_wifi)
            CoverAiEnhancer.Result.ModelDownloadFailed -> Phase.Error(MR.strings.ai_cover_download_failed)
            CoverAiEnhancer.Result.CoverLoadFailed -> Phase.Error(MR.strings.ai_cover_load_failed)
            CoverAiEnhancer.Result.EnhanceFailed -> Phase.Error(MR.strings.ai_cover_enhance_failed)
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            when (phase) {
                Phase.Compare -> {
                    TextButton(
                        enabled = !saving,
                        onClick = {
                            val enhanced = success?.enhanced ?: return@TextButton
                            saving = true
                            scope.launch {
                                val ok = CoverAiEnhancer.saveEnhanced(context, kind, entryId, enhanced)
                                saving = false
                                if (ok) {
                                    onSaved()
                                    onDismissRequest()
                                } else {
                                    phase = Phase.Error(MR.strings.ai_cover_enhance_failed)
                                }
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.ai_cover_keep))
                    }
                }
                Phase.Error -> {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                }
                Phase.Running -> {}
            }
        },
        dismissButton = {
            when (phase) {
                Phase.Compare -> {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(MR.strings.ai_cover_discard))
                    }
                }
                Phase.Error, Phase.Running -> {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                }
            }
        },
        title = {
            Text(text = stringResource(MR.strings.ai_cover_improve))
        },
        text = {
            when (val currentPhase = phase) {
                Phase.Running -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Text(
                            text = stringResource(
                                if (downloadingModel) {
                                    MR.strings.ai_cover_downloading_model
                                } else {
                                    MR.strings.ai_cover_enhancing
                                },
                            ),
                        )
                    }
                }
                Phase.Compare -> {
                    val result = success
                    if (result != null) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (showAfter) {
                                    OutlinedButton(
                                        onClick = { showAfter = false },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(text = stringResource(MR.strings.ai_cover_before))
                                    }
                                    Button(
                                        onClick = {},
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(text = stringResource(MR.strings.ai_cover_after))
                                    }
                                } else {
                                    Button(
                                        onClick = {},
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(text = stringResource(MR.strings.ai_cover_before))
                                    }
                                    OutlinedButton(
                                        onClick = { showAfter = true },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(text = stringResource(MR.strings.ai_cover_after))
                                    }
                                }
                            }
                            val bitmap = if (showAfter) result.enhanced else result.original
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(360.dp),
                            )
                            Text(
                                text = stringResource(MR.strings.ai_cover_note),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                is Phase.Error -> {
                    Text(text = stringResource(currentPhase.message))
                }
            }
        },
    )
}

private sealed interface Phase {
    data object Running : Phase
    data object Compare : Phase
    data class Error(val message: StringResource) : Phase
}
