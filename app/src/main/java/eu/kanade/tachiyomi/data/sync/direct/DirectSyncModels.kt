package eu.kanade.tachiyomi.data.sync.direct

import eu.kanade.tachiyomi.data.sync.SyncData
import kotlinx.serialization.Serializable

/**
 * v7.9.36 — Direct device-to-device sync payload (local WiFi only, no account, no server).
 *
 * The [SyncData] (device id + [eu.kanade.tachiyomi.data.backup.models.Backup]) is the same
 * payload the Google Drive sync uses, so the merge engine in [eu.kanade.tachiyomi.data.sync.service.SyncService]
 * (newest version wins per entry, chapters/episodes merged, furthest progress kept) is reused as-is.
 * Custom covers are not part of the backup format, so they travel as base64 bytes here.
 */
@Serializable
data class DirectSyncPayload(
    val deviceId: String,
    val deviceName: String,
    val sentAt: Long,
    val syncData: SyncData,
    val covers: List<DirectSyncCover> = emptyList(),
)

@Serializable
data class DirectSyncCover(
    /** "manga", "anime" or "novel". */
    val kind: String,
    val source: Long,
    val url: String,
    val base64: String,
    val modifiedAt: Long,
)

@Serializable
data class DirectSyncPairRequest(
    val deviceId: String,
    val deviceName: String,
    val code: String,
)

@Serializable
data class DirectSyncPairResponse(
    val ok: Boolean,
    val deviceId: String = "",
    val deviceName: String = "",
    val error: String? = null,
)

@Serializable
data class DirectSyncInfo(
    val deviceId: String,
    val deviceName: String,
    val version: Int = 1,
)
