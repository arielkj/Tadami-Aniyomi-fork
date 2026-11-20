package eu.kanade.presentation.updates.aurora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import eu.kanade.presentation.theme.resolveAuroraBorderColor
import eu.kanade.presentation.theme.resolveAuroraControlContainerColor

/**
 * v7.9.4: métrica de fila compartida entre la tarjeta de grupo y la fila
 * simple de Anime, para que las dos queden niveladas: mismo eje de póster
 * (x=20dp), mismo eje de texto (x=108dp) y misma acción a la derecha
 * (caja de 36dp centrada en el mismo eje vertical). Por defecto, la
 * geometría actual (Manga y Novela no cambian); Anime usa AnimeAligned.
 */
data class AuroraUpdateRowMetrics(
    /** Relleno interior de la fila (la tarjeta ya aporta 20dp laterales). */
    val innerPadding: PaddingValues = PaddingValues(12.dp),
    /** Separación póster -> texto. */
    val textGap: Dp = 12.dp,
    val titleFontSize: TextUnit = 16.sp,
    val titleLineHeight: TextUnit = 24.sp,
    val titleToSubtitleGap: Dp = 4.dp,
    val subtitleFontSize: TextUnit = 12.sp,
    /** Caja de la acción de la derecha (chevron); 22dp = solo el icono. */
    val trailingSlotSize: Dp = 22.dp,
    /** Relleno vertical exterior de la tarjeta; null = el del tema (6dp oscuro, 8dp claro). */
    val outerVerticalPadding: Dp? = null,
) {
    companion object {
        val Default = AuroraUpdateRowMetrics()

        /**
         * Misma métrica que la fila simple de Anime (AuroraUpdateCard):
         * póster 72x108 en x=20dp, texto 16sp/20sp con subtítulo 14sp,
         * acción de 36dp en el mismo eje que el botón de reproducir.
         */
        val AnimeAligned = AuroraUpdateRowMetrics(
            innerPadding = PaddingValues(vertical = 12.dp),
            textGap = 16.dp,
            titleFontSize = 16.sp,
            titleLineHeight = 20.sp,
            titleToSubtitleGap = 6.dp,
            subtitleFontSize = 14.sp,
            trailingSlotSize = 36.dp,
            outerVerticalPadding = 0.dp,
        )
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
    val tabContainerColor = if (!colors.isDark && !colors.isEInk) {
        Color.White
    } else {
        resolveAuroraControlContainerColor(colors)
    }
    val rimBrush = when {
        colors.isDark -> Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.24f),
                colors.accent.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.06f),
            ),
        )
        colors.isEInk -> Brush.verticalGradient(
            colors = listOf(
                resolveAuroraBorderColor(colors, emphasized = true),
                resolveAuroraBorderColor(colors, emphasized = false),
            ),
        )
        else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    val coverRequest = remember(coverData) {
        buildAuroraCoverImageRequest(context, coverData)
    }
    val isLightTheme = !colors.isDark && !colors.isEInk
    val cardShape = RoundedCornerShape(16.dp)

    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(metrics.innerPadding),
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
            // de reproducir de la fila simple (36dp en Anime), para que el
            // eje vertical de la derecha sea el mismo en las dos filas.
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

    if (isLightTheme) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = metrics.outerVerticalPadding ?: 8.dp)
                .drawBehind {
                    val radius = 16.dp.toPx()
                    val cornerRadius = CornerRadius(radius, radius)
                    val neutralOffsetY = 3.dp.toPx()
                    val warmOffsetY = 5.dp.toPx()
                    val neutralInset = 1.dp.toPx()
                    val warmInset = 3.dp.toPx()

                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.035f),
                        topLeft = Offset(x = neutralInset, y = neutralOffsetY),
                        size = Size(width = size.width - neutralInset * 2, height = size.height),
                        cornerRadius = cornerRadius,
                    )
                    drawRoundRect(
                        color = colors.accent.copy(alpha = 0.025f),
                        topLeft = Offset(x = warmInset, y = warmOffsetY),
                        size = Size(width = size.width - warmInset * 2, height = size.height),
                        cornerRadius = cornerRadius,
                    )
                }
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.78f),
                            Color.White.copy(alpha = 0.68f),
                            Color.White.copy(alpha = 0.60f),
                        ),
                    ),
                    shape = cardShape,
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.12f),
                        ),
                    ),
                    shape = cardShape,
                )
                .clip(cardShape)
                .clickable { onClick() },
        ) {
            cardContent()
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = metrics.outerVerticalPadding ?: 6.dp),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = tabContainerColor),
            border = if (colors.isDark || colors.isEInk) {
                BorderStroke(
                    width = 1.dp,
                    brush = rimBrush,
                )
            } else {
                null
            },
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp,
            ),
        ) {
            cardContent()
        }
    }
}
