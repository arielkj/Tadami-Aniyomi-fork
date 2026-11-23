package mihon.data.extension.model

/**
 * Normalizes an anime extensions-lib version to the numbering the anime
 * loader accepts (12.0..27.0).
 *
 * Some anime repos still publish their extensions with Mihon-style
 * versions ("1.4.x" / "1.5.x" / "1.6.x"). Those are the same generation as
 * the merged Aniyomi anime lib 16 — the "1.6" in the version string means
 * lib 16, not lib 1.6. Without this mapping a "1.6.3" entry is read as lib
 * 1.6, which falls outside the accepted range, so the extension is shown
 * as "Es necesario actualizar" and can neither load nor install, even
 * though the app already speaks the lib 16 API. The manga loader accepts
 * 1.4/1.5/1.6 as-is, which is exactly the asymmetry users see between
 * the two extension lists.
 */
fun normalizeAnimeLibVersion(rawLibVersion: Double): Double {
    val rounded = kotlin.math.round(rawLibVersion * 100.0) / 100.0
    return when (rounded) {
        1.4, 1.5, 1.6 -> 16.0
        else -> rounded
    }
}
