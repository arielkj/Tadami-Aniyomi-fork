package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberCoverReloadTick
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

/**
 * v7.9.10 — tarjetas de Títulos con barra de progreso (diseño del usuario,
 * 20 Nov): TitleCardSide para el destacado de arriba y para "Seguir
 * viendo". La misma estructura para anime y manga; solo cambia
 * "Episodio"/"Capítulo" y la insignia de contenido nuevo, que se derivan
 * del tipo ("Anime"/"Manga").
 *
 * v7.9.12 — el destacado usa TitleCardSide (el usuario pidió el formato
 * de lado, no la tarjeta vertical TitleCardTop, que se eliminó).
 *
 * Nota sobre `cover: Any?`: la especificación lo pedía como String, pero
 * en esta app la portada viaja como AnimeCover/MangaCover (o la URL del
 * fondo) para que la petición pase por la caché V7 (clave de portada +
 * lastModified + recarga). Un String pelado perdería esa clave y las
 * portadas parpadearían al remontar la tarjeta.
 */

/**
 * Seguir viendo: tarjeta horizontal. Portada vertical a la izquierda
 * (144dp de ancho, 2:3, esquinas de 24dp); a la derecha, insignia de
 * contenido nuevo, título, dos líneas de datos, barra de progreso y
 * "Ep. X de Y" con el porcentaje.
 */
@Composable
fun TitleCardSide(
    cover: Any?,
    title: String,
    type: String,
    genre: String?,
    total: Int,
    current: Int,
    status: String,
    hasNew: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val isManga = type == stringResource(AYMR.strings.label_manga)
    val quantity = titleCardQuantityText(isManga, total)
    val firstLine = listOfNotNull(type, genre).joinToString(" · ")
    val secondLine = listOfNotNull(quantity, status).joinToString(" · ")
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TitleCardCoverImage(
                cover = cover,
                variant = AuroraCoverPlaceholderVariant.Portrait,
                modifier = Modifier
                    .width(144.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
            ) {
                if (hasNew) {
                    TitleCardNewBadge(isManga = isManga)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = firstLine,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = secondLine,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(10.dp))
                TitleCardProgressBar(progress = titleCardProgress(current, total))
                Spacer(modifier = Modifier.height(6.dp))
                TitleCardProgressFooter(isManga = isManga, current = current, total = total)
            }
        }
    }
}

/**
 * v7.9.13 — "Seguir viendo" del MISMO TAMAÑO que las tarjetas de la
 * cuadrícula de cada categoría ("para no tener varios tamaños"): la
 * anchura es la de una celda de la rejilla (la calcula el encabezado
 * con las columnas) y la portada es 2:3, como las de abajo. Portada
 * arriba con la insignia de contenido nuevo y la barra de progreso
 * fina en su borde inferior; debajo, el título y "Ep./Cap. X de Y · %".
 * El tap va directo al episodio/capítulo, como antes. El destacado
 * sigue siendo TitleCardSide (grande); solo cambia el carrusel.
 */
@Composable
fun TitleCardContinueGrid(
    cover: Any?,
    title: String,
    type: String,
    genre: String?,
    total: Int,
    current: Int,
    status: String,
    hasNew: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val isManga = type == stringResource(AYMR.strings.label_manga)
    // genre y status no caben a este tamaño: la tarjeta es la misma que
    // una celda de la rejilla (portada + título + progreso); el género
    // y el estado ya salen en la tarjeta del destacado.
    Column(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            TitleCardCoverImage(
                cover = cover,
                variant = AuroraCoverPlaceholderVariant.Portrait,
                modifier = Modifier.fillMaxSize(),
            )
            if (hasNew) {
                TitleCardNewBadgeCompact(
                    isManga = isManga,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }
            TitleCardProgressBar(
                progress = titleCardProgress(current, total),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
        )
        Text(
            text = "${titleCardProgressLabel(isManga, current, total)} · " +
                titleCardProgressPercent(current, total),
            color = colors.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TitleCardCoverImage(
    cover: Any?,
    variant: AuroraCoverPlaceholderVariant,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reloadTick = rememberCoverReloadTick()
    val request = remember(context, cover, reloadTick) {
        buildAuroraCoverImageRequest(context, cover)
    }
    val fallbackPainter = rememberThemeAwareCoverErrorPainter(variant)
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = rememberAuroraPosterColorFilter(),
        modifier = modifier,
        error = fallbackPainter,
        fallback = fallbackPainter,
    )
}

@Composable
private fun TitleCardNewBadge(
    isManga: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E88E5))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = stringResource(
                if (isManga) {
                    AYMR.strings.aurora_library_featured_new_chapter
                } else {
                    AYMR.strings.aurora_library_featured_new_episode
                },
            ),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TitleCardNewBadgeCompact(
    isManga: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E88E5))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(
                if (isManga) {
                    AYMR.strings.aurora_library_featured_new_chapter
                } else {
                    AYMR.strings.aurora_library_featured_new_episode
                },
            ),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun TitleCardProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = colors.accent,
        trackColor = colors.textSecondary.copy(alpha = 0.25f),
    )
}

@Composable
private fun TitleCardProgressFooter(
    isManga: Boolean,
    current: Int,
    total: Int,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = titleCardProgressLabel(isManga, current, total),
            color = colors.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
        )
        Text(
            text = titleCardProgressPercent(current, total),
            color = colors.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

private fun titleCardProgress(current: Int, total: Int): Float {
    if (total <= 0) return 0f
    return (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

private fun titleCardProgressLabel(isManga: Boolean, current: Int, total: Int): String {
    val unit = if (isManga) "Cap." else "Ep."
    return if (total > 0) "$unit $current de $total" else "$unit $current"
}

private fun titleCardProgressPercent(current: Int, total: Int): String {
    if (total <= 0) return ""
    return "${((current.toFloat() / total.toFloat()) * 100f).roundToInt()}%"
}

private fun titleCardQuantityText(isManga: Boolean, total: Int): String? {
    if (total <= 0) return null
    return if (isManga) "$total cap." else "$total ep."
}

@PreviewLightDark
@Composable
private fun TitleCardSidePreview() {
    TachiyomiPreviewTheme {
        Surface {
            TitleCardSide(
                cover = "https://example.com/cover.jpg",
                title = "Odisea en la Mazmorra",
                type = "Manga",
                genre = null,
                total = 75,
                current = 6,
                status = "En progreso",
                hasNew = true,
                onClick = {},
                modifier = Modifier
                    .padding(16.dp)
                    .width(320.dp),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TitleCardContinueGridPreview() {
    TachiyomiPreviewTheme {
        Surface {
            TitleCardContinueGrid(
                cover = "https://example.com/cover.jpg",
                title = "Odisea en la Mazmorra",
                type = "Manga",
                genre = null,
                total = 75,
                current = 6,
                status = "En progreso",
                hasNew = true,
                onClick = {},
                modifier = Modifier
                    .padding(16.dp)
                    .width(112.dp),
            )
        }
    }
}
