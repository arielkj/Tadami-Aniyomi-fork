package eu.kanade.presentation.entries.components.aurora

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import eu.kanade.presentation.components.resolveAuroraCoverPlaceholderMemoryCacheKey
import eu.kanade.tachiyomi.data.coil.CoverFilter
import eu.kanade.tachiyomi.util.debugTitleCoverFlow
import eu.kanade.tachiyomi.util.previewTitleCoverValue

internal data class AuroraPosterBackgroundSpec(
    val memoryCacheKey: String,
)

internal fun auroraPosterBackgroundSpec(
    baseCacheKey: String,
    containerWidthPx: Int,
    containerHeightPx: Int,
): AuroraPosterBackgroundSpec {
    // v7.9.19 Fase 1 (Capa 2): el nivel del filtro forma parte de la
    // clave de memoria — al cambiar el ajuste, el detalle compone una
    // clave nueva y la portada se vuelve a pedir (y a filtrar) en vez
    // de servir el bitmap viejo de la caché de memoria.
    return AuroraPosterBackgroundSpec(
        memoryCacheKey = "$baseCacheKey;${containerWidthPx}x$containerHeightPx" +
            CoverFilter.cacheKeyFragment(),
    )
}

internal fun buildAuroraPosterBackgroundRequest(
    context: Context,
    data: Any?,
    spec: AuroraPosterBackgroundSpec,
    containerWidthPx: Int,
    containerHeightPx: Int,
    placeholderData: Any? = null,
    configure: ImageRequest.Builder.() -> Unit = {},
): ImageRequest {
    val placeholderKey = resolveAuroraPosterPlaceholderMemoryCacheKey(placeholderData)
    debugTitleCoverFlow(scope = "poster-request") {
        "build data=${previewTitleCoverValue(
            data,
        )} memoryKey=${spec.memoryCacheKey} placeholderKey=$placeholderKey size=${containerWidthPx}x$containerHeightPx"
    }
    // V7.3: el backdrop se pedía a resolución completa de pantalla
    // (~1440x3120 en el S938B, ~18 MB en ARGB8888) solo para ir detrás de
    // un desenfoque. Se topa a 1080 px de ancho (alto proporcional): el
    // recorte es el mismo y se decodifica casi 3x menos.
    val cappedWidthPx = minOf(containerWidthPx, 1080)
    val cappedHeightPx = if (containerWidthPx > 0) {
        (containerHeightPx * cappedWidthPx / containerWidthPx).coerceAtLeast(1)
    } else {
        containerHeightPx
    }
    return ImageRequest.Builder(context)
        .data(data)
        .memoryCacheKey(spec.memoryCacheKey)
        .placeholderMemoryCacheKey(placeholderKey)
        .size(cappedWidthPx, cappedHeightPx)
        .apply(configure)
        .build()
}

private fun resolveAuroraPosterPlaceholderMemoryCacheKey(placeholderData: Any?): String? {
    return when (placeholderData) {
        is AuroraPosterBackgroundSpec -> placeholderData.memoryCacheKey
        else -> resolveAuroraCoverPlaceholderMemoryCacheKey(placeholderData)
    }
}

@Composable
internal fun rememberAuroraPosterBackgroundPainter(
    request: ImageRequest,
    placeholderPainter: Painter,
): AsyncImagePainter {
    // Error/fallback are transparent on purpose: the full poster sits over the
    // always-visible preview thumbnail, so on failure the overlay must not draw
    // the "no poster" placeholder — it would flash between the thumbnail and the
    // final image (issue: заглушка during the title transition).
    val transparent = remember { ColorPainter(Color.Transparent) }
    return rememberAsyncImagePainter(
        model = request,
        error = transparent,
        fallback = transparent,
        contentScale = ContentScale.Crop,
    )
}

internal fun Modifier.auroraPosterBlur(blurRadius: Dp): Modifier {
    return if (blurRadius > 0.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        blur(blurRadius)
    } else {
        this
    }
}
internal fun shouldDrawAuroraPosterBlurOverlay(alpha: Float): Boolean {
    return alpha > 0.01f
}
