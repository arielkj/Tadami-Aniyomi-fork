package eu.kanade.presentation.updates.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.theme.AuroraTheme

/**
 * v7.9.4: métrica de fila compartida entre la fila de grupo y la fila
 * simple, para que las dos queden niveladas: mismo eje de póster
 * (x=20dp), mismo eje de texto (x=108dp) y misma acción a la derecha
 * (caja de 36dp centrada en el mismo eje vertical).
 *
 * v7.9.5: la fila de grupo ya NO es una cápsula con borde/fondo — es una
 * fila plana sin borde, igual que la fila simple, para que ninguna fila
 * de Actualizaciones destaque sobre las demás. Solo se quitó la cápsula
 * (Card con borde en oscuro, Box con fondo+borde+sombras en claro); la
 * geometría es la misma que la fila simple en Anime, Manga y Novela.
 * De paso, sin Card ni sombras dibujadas por fila hay menos overdraw al
 * desplazar la lista.
 */
data class AuroraUpdateRowMetrics(
    /** Separación póster -> texto. */
    val textGap: Dp = 16.dp,
    val titleFontSize: TextUnit = 16.sp,
    val titleLineHeight: TextUnit = 20.sp,
    val titleToSubtitleGap: Dp = 6.dp,
    val subtitleFontSize: TextUnit = 14.sp,
    /** Caja de la acción de la derecha (chevron); 36dp = como el botón de reproducir. */
    val trailingSlotSize: Dp = 36.dp,
) {
    companion object {
        val Default = AuroraUpdateRowMetrics()

        /**
         * Misma métrica que la fila simple (AuroraUpdateCard y sus
         * equivalentes de Manga/Novela): póster 72x108 en x=20dp, texto
         * 16sp/20sp con subtítulo 14sp, acción de 36dp en el mismo eje
         * que el botón de reproducir.
         */
        val AnimeAligned = AuroraUpdateRowMetrics()
    }
}

@Composable
fun AuroraUpdatesGroupCard(
    title: String,
    countText: String,
    coverData: Any?,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metrics: AuroraUpdateRowMetrics = AuroraUpdateRowMetrics.Default,
) {
    val colors = AuroraTheme.colors
    val context = LocalContext.current
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val coverRequest = remember(coverData) {
        buildAuroraCoverImageRequest(context, coverData)
    }

    // v7.9.5: fila plana sin cápsula — sin Card, sin borde, sin fondo:
    // misma geometría que la fila simple para que toda la lista sea uniforme.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = coverRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = rememberAuroraPosterColorFilter(),
            modifier = Modifier
                // v7.9.3: 72x108dp, tamaño intermedio único con las filas
                // simples y los pósters de los carruseles.
                .width(72.dp)
                .height(108.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.cardBackground),
            error = placeholderPainter,
            fallback = placeholderPainter,
        )

        Spacer(modifier = Modifier.width(metrics.textGap))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.titleFontSize,
                lineHeight = metrics.titleLineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(metrics.titleToSubtitleGap))
            Text(
                text = countText,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                fontSize = metrics.subtitleFontSize,
            )
        }

        // v7.9.4: la acción va en una caja del mismo tamaño que el botón
        // de reproducir de la fila simple (36dp), para que el eje vertical
        // de la derecha sea el mismo en las dos filas.
        Box(
            modifier = Modifier.size(metrics.trailingSlotSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
