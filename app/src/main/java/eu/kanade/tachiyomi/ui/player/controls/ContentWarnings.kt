package eu.kanade.tachiyomi.ui.player.controls

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

/**
 * Nuvio-style content warnings shown at the top-left of the player.
 * Derived only from the title's own genres/tags: a warning is shown when a
 * genre matches a known keyword, otherwise the block is omitted entirely.
 * The severity is always mild — the app has no severity data, so nothing
 * stronger is ever claimed.
 */
data class ContentWarning(
    val label: StringResource,
    val severity: StringResource = AYMR.strings.player_content_mild,
)

fun contentWarningsFor(genres: List<String>?): List<ContentWarning> {
    if (genres.isNullOrEmpty()) return emptyList()
    val joined = genres.joinToString(separator = " ").lowercase()
    val warnings = mutableListOf<ContentWarning>()
    if (listOf("violence", "violencia", "gore").any { it in joined }) {
        warnings += ContentWarning(AYMR.strings.player_content_violence)
    }
    if (listOf("swearing", "profanity", "blasphemy", "blasfemia", "lenguaje soez").any { it in joined }) {
        warnings += ContentWarning(AYMR.strings.player_content_profanity)
    }
    if (
        listOf(
            "horror",
            "terror",
            "psychological",
            "psicológico",
            "psicologico",
            "thriller",
            "suspense",
            "disturbing",
        ).any { it in joined }
    ) {
        warnings += ContentWarning(AYMR.strings.player_content_disturbing)
    }
    return warnings
}
