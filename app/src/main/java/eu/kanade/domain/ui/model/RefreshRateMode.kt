package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

// V7.6: fluidez de movimientos, como Samsung (Adaptable / Estándar).
// ADAPTIVE pide la tasa máxima del panel; STANDARD pide 60 Hz. En ambos
// casos las animaciones son cortas y suaves (ver Navigator.kt y
// AuroraTitleStagger.kt), así que la app se siente fluida también a 60 Hz.
enum class RefreshRateMode(
    val titleRes: StringResource,
) {
    ADAPTIVE(
        titleRes = AYMR.strings.pref_refresh_rate_mode_adaptive,
    ),
    STANDARD(
        titleRes = AYMR.strings.pref_refresh_rate_mode_standard,
    ),
}
