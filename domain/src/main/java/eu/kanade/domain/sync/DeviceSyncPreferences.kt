package eu.kanade.domain.sync

import tachiyomi.core.common.preference.PreferenceStore

/**
 * v7.9.36 — Preferences for direct device-to-device library sync (no account, no server).
 *
 * The sync happens over the local network only: one device hosts a small HTTP server,
 * the other connects as a client. Pairing is protected by a 6-digit code shown on
 * one device and typed on the other.
 */
class DeviceSyncPreferences(
    private val preferenceStore: PreferenceStore,
) {

    companion object {
        const val MODE_MANUAL = "manual"
        const val MODE_AUTO = "auto"

        const val DEFAULT_PORT = 27801
    }

    /** Sync mode: [MODE_MANUAL] (only when the user taps "Sincronizar ahora") or [MODE_AUTO]. */
    fun mode() = preferenceStore.getString("device_sync_mode", MODE_MANUAL)

    /** Category ids (as strings) EXCLUDED from sync, per type. Empty set = share everything. */
    fun excludedAnimeCategories() = preferenceStore.getStringSet("device_sync_excluded_anime_categories", emptySet())

    fun excludedMangaCategories() = preferenceStore.getStringSet("device_sync_excluded_manga_categories", emptySet())

    fun excludedNovelCategories() = preferenceStore.getStringSet("device_sync_excluded_novel_categories", emptySet())

    /** Pairing code (6 digits) shown on this device. Generated once, kept until regenerated. */
    fun pairCode() = preferenceStore.getString("device_sync_pair_code", "")

    /** Paired device: empty means no device paired yet. */
    fun pairedDeviceId() = preferenceStore.getString("device_sync_paired_device_id", "")

    fun pairedDeviceName() = preferenceStore.getString("device_sync_paired_device_name", "")

    fun pairedDeviceAddress() = preferenceStore.getString("device_sync_paired_device_address", "")

    /** The paired device's own pairing code (typed by the user when pairing). Needed to sync to it. */
    fun pairedDeviceCode() = preferenceStore.getString("device_sync_paired_device_code", "")

    fun lastSyncAt() = preferenceStore.getLong("device_sync_last_sync_at", 0L)

    /**
     * Last-sent fingerprints, as a JSON object { entryKey: fingerprint }.
     * Used to send only the changes (deltas) after the first full sync.
     */
    fun fingerprintsJson() = preferenceStore.getString("device_sync_fingerprints", "{}")

    fun serverPort() = preferenceStore.getInt("device_sync_server_port", DEFAULT_PORT)

    /** Human readable name of this device (defaults to the model). */
    fun deviceName() = preferenceStore.getString("device_sync_device_name", android.os.Build.MODEL)
}
