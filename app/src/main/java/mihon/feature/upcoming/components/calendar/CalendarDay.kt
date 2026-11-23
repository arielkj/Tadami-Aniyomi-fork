package mihon.feature.upcoming.components.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private const val MAX_EVENTS = 3

// v7.9.3: todos los números de día son 16sp; los días pasados solo van
// un poco más apagados (0.60). Con el apagado fuerte de antes (0.38) los
// días pasados se veían MÁS PEQUEÑOS que los futuros aunque el tamaño
// de letra era el mismo: tres tamaños distintos en una sola pantalla.
private const val PAST_DAY_ALPHA = 0.6f

// v7.9.37: tamaño fijo del círculo del día. El circleLayout() anterior
// forzaba la celda a un cuadrado de max(ancho, alto); en la tira de
// semana ancha de la tablet cada celda mide ~1/7 de una fila de ~1100dp
// y el círculo de "hoy" crecía a ~150dp. Ahora el círculo mide 44dp
// (igual que en el teléfono, donde la celda mide ~45-50dp) centrado en
// la celda, que sigue ocupando su hueco para que la fila quede repartida.
// De paso se quita el modificador de layout a medida: una pasada menos
// de medida por cada celda de día.
private val DayCircleSize = 44.dp

@Composable
fun CalendarDay(
    date: LocalDate,
    events: Int,
    onDayClick: () -> Unit,
    modifier: Modifier = Modifier,
    // v7.9.2: fechas puestas a mano por el usuario (puntos ámbar).
    customEvents: Int = 0,
) {
    val today = remember { LocalDate.now() }

    Box(
        modifier = modifier
            .clickable(onClick = onDayClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(DayCircleSize)
                .then(
                    if (today == date) {
                        Modifier.border(
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onBackground,
                            ),
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                )
                .clip(shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = if (date.isBefore(today)) {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = PAST_DAY_ALPHA)
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                fontWeight = FontWeight.SemiBold,
            )
            Row(Modifier.offset(y = 12.dp)) {
                val size = events.coerceAtMost(MAX_EVENTS)
                for (index in 0 until size) {
                    CalendarIndicator(
                        index = index,
                        size = 56.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // Fechas manuales: puntos ámbar, distintos de los de emisión.
                // (El índice reinicia: la fila ya ordena los puntos; el índice
                // solo controla la opacidad progresiva del indicador.)
                val customSize = customEvents.coerceAtMost(MAX_EVENTS - size)
                for (index in 0 until customSize) {
                    CalendarIndicator(
                        index = index,
                        size = 56.dp,
                        color = Color(0xFFFFA726),
                    )
                }
            }
        }
    }
}
