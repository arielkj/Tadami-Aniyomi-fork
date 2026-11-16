package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

internal abstract class BaseColorScheme {

    abstract val darkScheme: ColorScheme
    abstract val lightScheme: ColorScheme

    fun getColorScheme(isDark: Boolean, isAmoled: Boolean): ColorScheme {
        if (!isDark) return lightScheme

        return if (isAmoled) {
            // AMOLED verdadero: fondo y TODAS las superficies en negro puro.
            // La separación visual no viene de un relleno gris, sino de bordes
            // finos (outlineVariant) en las tarjetas, chips y barra inferior.
            // Esto también corrige temas como Monocromo, cuyo darkScheme ya es
            // negro puro y antes se veía peor con AMOLED por los grises de abajo.
            darkScheme.copy(
                background = Color.Black,
                onBackground = Color.White,
                surface = Color.Black,
                onSurface = Color.White,
                surfaceVariant = Color.Black,
                surfaceDim = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color.Black,
                surfaceContainer = Color.Black,
                surfaceContainerHigh = Color.Black,
                surfaceContainerHighest = Color.Black,
            )
        } else {
            darkScheme
        }
    }
}
