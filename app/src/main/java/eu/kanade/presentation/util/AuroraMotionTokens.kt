package eu.kanade.presentation.util

import androidx.compose.animation.core.CubicBezierEasing

/**
 * V7.7 (lo de Nuvio): duraciones y curva ÚNICAS para las animaciones
 * de la app, como los tokens de Nuvio (150/220/300/400 ms + una sola
 * curva). Antes cada animación tenía su duración y su spring sueltos;
 * un spring hace saltar la animación si se pierde un cuadro, un tween
 * fijo no: la animación dura lo mismo se dibujen 60 o 120 cuadros.
 * Nuvio demuestra que a 60 Hz ya se puede sentir super bien si cada
 * cuadro cuesta poco y la duración es fija.
 */
object AuroraMotionTokens {
    const val FAST_MS = 150
    const val NORMAL_MS = 220
    const val SHEET_MS = 300
    const val SLOW_MS = 400

    /** Curva estándar única (la de Nuvio: salida rápida, llegada suave). */
    val standardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
