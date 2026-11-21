package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
 * v7.9.14 — "Seguir viendo" vuelve al formato DE LADO de la v7.9.12
 * (TitleCardSide), solo MÁS PEQUEÑO: la portada tiene el MISMO tamaño
 * que las de la biblioteca (la anchura de una celda de la rejilla, que
 * pasa el encabezado como `coverWidth`) y la tarjeta se achica alrededor
 * (`compact = true`: menos padding, textos un poco más chicos, insignia
 * compacta). La v7.9.13 lo cambió al formato de cuadrícula
 * (TitleCardContinueGrid, eliminado) y el usuario lo rechazó: solo había
 * que bajarle el tamaño, no cambiarle el formato.
 *
 * v7.9.14 (esquinas) — el usuario notó que el destacado, el Seguir
 * viendo y las tarjetas de la biblioteca tenían radios distintos. Las
 * tres quedan con el MISMO radio que las tarjetas de la biblioteca:
 * 4.dp — el de `ItemCover.Book` (`MaterialTheme.shapes.extraSmall`,
 * sin override en el tema), tanto la tarjeta como la portada dentro
 * de ella.
 *
 * Nota sobre `cover: Any?`: la especificación lo pedía como String, pero
 * en esta app la portada viaja como AnimeCover/MangaCover (o la URL del
 * fondo) para que la petición pase por la caché V7 (clave de portada +
 * lastModified + recarga). Un String pelado perdería esa clave y las
 * portadas parpadearían al remontar la tarjeta.
 */

/**
 * Radio de esquina de las tres tarjetas (v7.9.14): el MISMO que las
 * tarjetas de la cuadrícula de la biblioteca — 4.dp, el de
 * `ItemCover.Book` (MaterialTheme.shapes.extraSmall). Se aplica igual
 * a la tarjeta y a la portada dentro de ella, en el destacado y en el
 * "Seguir viendo" compacto.
 */
private val TITLE_CARD_CORNER = 4.dp

/**
 * Seguir viendo / destacado: tarjeta horizontal. Portada vertical a la
 * izquierda (144dp de ancho por defecto, 2:3); a la derecha, insignia de
 * contenido nuevo, título, dos líneas de datos, barra de progreso y
 * "Ep. X de Y" con el porcentaje.
 *
 * `compact = true` (v7.9.14, solo el carrusel de "Seguir viendo"): el
 * MISMO diseño, solo más pequeño — la portada pasa a `coverWidth` (el
 * tamaño de las de la biblioteca), menos padding y textos un poco más
 * chicos. El destacado usa los valores grandes de siempre.
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
    coverWidth: Dp = 144.dp,
    compact: Boolean = false,
) {
    val colors = AuroraTheme.colors
    val isManga = type == stringResource(AYMR.strings.label_manga)
    val quantity = titleCardQuantityText(isManga, total)
    val firstLine = listOfNotNull(type, genre).joinToString(" · ")
    val secondLine = listOfNotNull(quantity, status).joinToString(" · ")
    val padding = if (compact) 8.dp else 10.dp
    val spacing = if (compact) 10.dp else 14.dp
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(TITLE_CARD_CORNER),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(padding),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            TitleCardCoverImage(
                cover = cover,
                variant = AuroraCoverPlaceholderVariant.Portrait,
                modifier = Modifier
                    .width(coverWidth)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(TITLE_CARD_CORNER)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
            ) {
                if (hasNew) {
                    if (compact) {
                        TitleCardNewBadgeCompact(isManga = isManga)
                    } else {
                        TitleCardNewBadge(isManga = isManga)
                    }
                    Spacer(modifier = Modifier.height(if (compact) 6.dp else 8.dp))
                }
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = if (compact) 15.sp else 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = firstLine,
                    color = colors.textSecondary,
                    fontSize = if (compact) 12.sp else 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = secondLine,
                    color = colors.textSecondary,
                    fontSize = if (compact) 12.sp else 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(if (compact) 8.dp else 10.dp))
                TitleCardProgressBar(progress = titleCardProgress(current, total))
                Spacer(modifier = Modifier.height(6.dp))
                TitleCardProgressFooter(
                    isManga = isManga,
                    current = current,
                    total = total,
                    fontSize = if (compact) 11.sp else 12.sp,
                )
            }
        }
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
    fontSize: TextUnit = 12.sp,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = titleCardProgressLabel(isManga, current, total),
            color = colors.textSecondary,
            fontSize = fontSize,
            maxLines = 1,
        )
        Text(
            text = titleCardProgressPercent(current, total),
            color = colors.textSecondary,
            fontSize = fontSize,
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
private fun TitleCardSideCompactPreview() {
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
                    .width(272.dp),
                coverWidth = 112.dp,
                compact = true,
            )
        }
    }
}
