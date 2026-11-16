package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

const val LATTICE_CIRCUIT_NAVBAR_UNLOCKABLE = "special_navbar_lattice_circuit"

private val LatticeCyan = Color(0xFF0095AE)
private val LatticeAmber = Color(0xFFFFA726)

/**
 * Circuit Edge active when user equipped it. V5: sin гейт por unlockable —
 * el sistema de desbloqueo quedó huérfano tras la fase 1 y su observador
 * permanente no aportaba nada; manda solo el interruptor de preferencias.
 */
@Composable
fun rememberLatticeCircuitNavbarUnlocked(): Boolean {
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    return uiPreferences.showCircuitNavbar().collectAsState().value
}

/**
 * Circuit Edge — **bottom rim-light only**.
 *
 * Draws a soft glow and hairline along the underside of the nav capsule
 * (bottom edge + lower corner arcs). V5: color estático — antes respiraba
 * cyan ↔ amber con dos bucles infinitos (7,4 s y 4,6 s); ahora se dibuja
 * una sola vez con los valores fijos que ya usaba la rama de movimiento
 * reducido. El parámetro [animate] se conserva por compatibilidad con los
 * call sites y ya no tiene efecto.
 */
@Composable
fun Modifier.latticeCircuitBar(animate: Boolean = true): Modifier {
    // V5: valores fijos (antes eran la rama de movimiento reducido).
    val phase = 0.18f
    val breath = 0.35f

    val mix = (0.5f + 0.5f * sin(phase * 2f * PI.toFloat())).coerceIn(0f, 1f)
    val alphaPulse = (0.28f + 0.36f * (0.5f + 0.5f * sin(breath * 2f * PI.toFloat() + 0.4f)))
        .coerceIn(0.22f, 0.7f)
    val color = lerp(LatticeCyan, LatticeAmber, mix)
    val mixInner = (0.5f + 0.5f * sin(phase * 2f * PI.toFloat() + PI.toFloat())).coerceIn(0f, 1f)
    val colorInner = lerp(LatticeCyan, LatticeAmber, mixInner)

    return this.drawBehind {
        val inset = 1.5.dp.toPx()
        val w = (size.width - inset * 2f).coerceAtLeast(0f)
        val h = (size.height - inset * 2f).coerceAtLeast(0f)
        if (w <= 0f || h <= 0f) return@drawBehind

        // Capsule geometry (matches nav pill)
        val left = inset
        val top = inset
        val right = inset + w
        val bottom = inset + h
        val radius = h / 2f

        // Underside of the capsule only: left lower quarter → bottom edge → right lower quarter.
        // Canvas angles (y-down): 0 = east, π/2 = south, π = west.
        fun bottomRimPath(): Path = Path().apply {
            val steps = 14
            val lcx = left + radius
            val lcy = top + radius
            val rcx = right - radius
            val rcy = top + radius
            val pi = PI.toFloat()
            val halfPi = pi / 2f

            moveTo(lcx + radius * cos(pi), lcy + radius * sin(pi))
            for (i in 1..steps) {
                val a = pi - halfPi * (i / steps.toFloat())
                lineTo(lcx + radius * cos(a), lcy + radius * sin(a))
            }
            lineTo(rcx + radius * cos(halfPi), rcy + radius * sin(halfPi))
            for (i in 1..steps) {
                val a = halfPi - halfPi * (i / steps.toFloat())
                lineTo(rcx + radius * cos(a), rcy + radius * sin(a))
            }
        }

        val rim = bottomRimPath()

        // Soft bloom under the bar (true rim-light falloff)
        val glowH = 10.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    color.copy(alpha = alphaPulse * 0.10f),
                    color.copy(alpha = alphaPulse * 0.28f),
                    color.copy(alpha = alphaPulse * 0.08f),
                    Color.Transparent,
                ),
                startY = bottom - glowH * 0.55f,
                endY = bottom + glowH * 0.85f,
            ),
            topLeft = Offset(left + radius * 0.15f, bottom - glowH * 0.55f),
            size = androidx.compose.ui.geometry.Size(
                (w - radius * 0.3f).coerceAtLeast(0f),
                glowH * 1.4f,
            ),
        )

        // Outer soft stroke along bottom rim
        drawPath(
            path = rim,
            color = color.copy(alpha = alphaPulse * 0.28f),
            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
        )
        // Main hairline
        drawPath(
            path = rim,
            color = color.copy(alpha = (0.50f + alphaPulse * 0.45f).coerceIn(0.4f, 0.98f)),
            style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round),
        )
        // Thin inner rim, slightly inset upward
        val insetY = 1.4.dp.toPx()
        val inner = Path().apply {
            val steps = 14
            val r = (radius - insetY).coerceAtLeast(1f)
            val lcx = left + radius
            val lcy = top + radius
            val rcx = right - radius
            val rcy = top + radius
            val by = bottom - insetY
            moveTo(lcx + r * cos(PI.toFloat()), lcy + r * sin(PI.toFloat()))
            for (i in 1..steps) {
                val a = PI.toFloat() - (PI.toFloat() / 2f) * (i / steps.toFloat())
                lineTo(lcx + r * cos(a), (lcy + r * sin(a)).coerceAtMost(by))
            }
            lineTo(rcx, by)
            for (i in 1..steps) {
                val a = PI.toFloat() / 2f - (PI.toFloat() / 2f) * (i / steps.toFloat())
                lineTo(rcx + r * cos(a), (rcy + r * sin(a)).coerceAtMost(by))
            }
        }
        drawPath(
            path = inner,
            color = colorInner.copy(alpha = 0.10f + alphaPulse * 0.14f),
            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/** Vertical circuit line along the trailing edge of a navigation rail (tablet / landscape). V5: pulso estático — antes recorría el rail con un bucle infinito de 5,2 s. */
@Composable
fun Modifier.latticeCircuitRail(animate: Boolean = true): Modifier {
    val accent = Color(0xFF0095AE)
    // V5: posición fija del brillo (antes era la rama de movimiento reducido).
    val progress = 0.35f
    return this.drawBehind {
        val x = size.width - 1.dp.toPx()
        val stroke = 1.5f * density
        drawLine(accent.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, size.height), stroke)
        val ticks = 12
        for (i in 1 until ticks) {
            val y = size.height * i / ticks
            drawLine(accent.copy(alpha = 0.3f), Offset(x, y), Offset(x - 4.dp.toPx(), y), density)
        }
        val py = size.height * progress
        val half = size.height * 0.08f
        drawLine(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.5f to accent,
                1f to Color.Transparent,
                startY = py - half,
                endY = py + half,
            ),
            start = Offset(x, py - half),
            end = Offset(x, py + half),
            strokeWidth = stroke * 1.6f,
        )
    }
}
