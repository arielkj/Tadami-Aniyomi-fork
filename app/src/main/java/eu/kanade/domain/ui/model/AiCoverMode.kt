package eu.kanade.domain.ui.model

/**
 * v7.9.20 — how the on-device AI cover enhancement (Real-ESRGAN) behaves.
 *  - OFF: no AI enhancement at all; the «Mejorar portada» button is hidden.
 *  - MANUAL: only when the user taps «Mejorar portada» on a detail screen.
 *  - AUTO_WIFI: like MANUAL, plus a background job enhances library covers
 *    while on WiFi (model is downloaded once, on first use).
 *
 * The user chose manual-only (21 Nov): AUTO_WIFI is never offered in the
 * UI; it exists so the mode can grow without a new preference.
 */
enum class AiCoverMode {
    OFF,
    MANUAL,
    AUTO_WIFI,
}
