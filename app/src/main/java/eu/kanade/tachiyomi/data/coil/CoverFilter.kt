package eu.kanade.tachiyomi.data.coil

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import eu.kanade.domain.ui.UiPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * v7.9.19 Fase 1 (Capa 2 de la guía de portadas): filtro suave para las
 * portadas GRANDES del detalle — contraste, saturación, brillo y nitidez,
 * con nivel de negro limpio para AMOLED. Colores VIVOS pero NATURALES,
 * nunca con aspecto de retoque falso.
 *
 * Solo se aplica a los pósters grandes del detalle (los que descarga
 * [AuroraPosterRequestFetcher]); las miniaturas de la cuadrícula no se
 * tocan, por fluidez. El filtro corre UNA vez al guardar en caché
 * (hilo de IO de Coil), nunca al hacer scroll ni en las páginas del
 * lector.
 *
 * El realce de color es *vibrance*, no saturación plana: los píxeles
 * apagados ganan más color que los ya saturados, y los tonos de piel
 * están protegidos, así que el resultado parece una mejor impresión de
 * la misma portada. Tres niveles (Ajustes → Apariencia → «Filtro de
 * portadas»), por defecto [CoverFilterLevel.SOFT]:
 *  - OFF:     sin filtro; la portada se muestra tal como viene.
 *  - SOFT:    contraste +11%, saturación +16% (vibrance), brillo +2%,
 *             unsharp mask moderado, negros casi negros a negro puro.
 *  - INTENSE: un paso más (contraste +16%, saturación +25%) para quien
 *             lo prefiera.
 *
 * Best-effort: cualquier fallo devuelve null y el llamador usa los
 * bytes originales sin filtrar.
 */
object CoverFilter {

    enum class CoverFilterLevel(val key: String) {
        OFF("off"),
        SOFT("soft"),
        INTENSE("intense"),
    }

    private data class LevelParams(
        val unsharpAmount: Float,
        val vibrance: Float,
        val saturation: Float,
        val contrast: Float,
        val brightness: Float,
        val blackLevel: Int,
    )

    private fun paramsFor(level: CoverFilterLevel): LevelParams? = when (level) {
        CoverFilterLevel.OFF -> null
        CoverFilterLevel.SOFT -> LevelParams(
            unsharpAmount = 0.50f,
            vibrance = 0.16f,
            saturation = 1.04f,
            contrast = 1.11f,
            brightness = 5f,
            blackLevel = 8,
        )
        CoverFilterLevel.INTENSE -> LevelParams(
            unsharpAmount = 0.60f,
            vibrance = 0.30f,
            saturation = 1.08f,
            contrast = 1.16f,
            brightness = 5f,
            blackLevel = 8,
        )
    }

    /** Ajustes → Apariencia → «Filtro de portadas» (por defecto Suave). */
    fun level(): CoverFilterLevel = runCatching {
        when (Injekt.get<UiPreferences>().auroraCoverFilter().get()) {
            "off" -> CoverFilterLevel.OFF
            "intense" -> CoverFilterLevel.INTENSE
            else -> CoverFilterLevel.SOFT
        }
    }.getOrDefault(CoverFilterLevel.SOFT)

    fun isEnabled(): Boolean = level() != CoverFilterLevel.OFF

    /**
     * Fragmento de clave de caché: cambiar el ajuste invalida tanto la
     * caché de disco del póster como la clave de memoria del fondo del
     * detalle, así que la portada se vuelve a bajar y a filtrar con el
     * nivel nuevo en vez de servir la versión vieja.
     */
    fun cacheKeyFragment(): String = when (level()) {
        CoverFilterLevel.OFF -> ";plain"
        CoverFilterLevel.SOFT -> ";filtro-suave"
        CoverFilterLevel.INTENSE -> ";filtro-intenso"
    }

    /**
     * Filtra los bytes crudos de una portada (JPEG/PNG/WebP) y devuelve
     * los bytes filtrados (JPEG, calidad 92), o null si el filtro está
     * apagado o algo falla (el llamador usa entonces los originales).
     */
    fun filter(bytes: ByteArray): ByteArray? {
        val params = paramsFor(level()) ?: return null
        return runCatching {
            val bitmap = decodeSampled(bytes) ?: return null
            try {
                val filtered = applyToBitmap(bitmap, params)
                try {
                    ByteArrayOutputStream().use { out ->
                        if (!filtered.compress(Bitmap.CompressFormat.JPEG, 92, out)) return null
                        out.toByteArray()
                    }
                } finally {
                    filtered.recycle()
                }
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
    }

    /**
     * v7.9.20 Fase 2: aplica SOLO la parte de color del filtro (vibrance/
     * saturación + contraste + brillo + negros limpios, según el nivel
     * actual) a un bitmap en memoria, SIN unsharp mask. La salida de la IA
     * ya es nítida; un segundo realce de bordes añadiría halos. Devuelve
     * un bitmap nuevo; la entrada queda intacta. Si el filtro está
     * apagado, devuelve la entrada tal cual.
     */
    fun applyColorOnly(source: Bitmap): Bitmap {
        val params = paramsFor(level()) ?: return source
        return runCatching {
            val w = source.width
            val h = source.height
            val pixels = IntArray(w * h)
            source.getPixels(pixels, 0, w, 0, 0, w, h)
            processPixels(pixels, w, h, params, sharpen = false)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out.setPixels(pixels, 0, w, 0, 0, w, h)
            out
        }.getOrNull() ?: source
    }

    /**
     * Decodifica con downsampling si la imagen es enorme, para no gastar
     * memoria de más en el hilo de IO: el póster del detalle se pide a
     * 1080 px de ancho como mucho, así que por encima de 1600 px en el
     * lado largo no se gana nada. Las portadas habituales (<=1000 px)
     * no se tocan.
     */
    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        var longest = max(bounds.outWidth, bounds.outHeight)
        while (longest > 1600) {
            sampleSize *= 2
            longest /= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    /**
     * Filtra un bitmap en memoria y devuelve uno nuevo; el de entrada
     * queda intacto.
     */
    private fun applyToBitmap(source: Bitmap, params: LevelParams): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        processPixels(pixels, w, h, params)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * En el sitio: unsharp mask (box blur 3x3, 2 pasadas) + vibrance/
     * saturación + contraste suave + brillo +2% + negros limpios.
     * Una pasada por los píxeles; ~300k píxeles en una portada de 460x650.
     * Con [sharpen] = false se salta el blur y el unsharp (solo color):
     * lo usa la Fase 2, cuya salida de IA ya es nítida.
     */
    private fun processPixels(pixels: IntArray, w: Int, h: Int, p: LevelParams, sharpen: Boolean = true) {
        // --- 3x3 box blur, separable (horizontal y luego vertical), 2 pasadas ---
        val blurred = if (sharpen) {
            val blurred = IntArray(w * h)
            boxBlur(pixels, blurred, w, h)
            boxBlur(blurred, blurred, w, h)
            blurred
        } else {
            null
        }

        val amount = p.unsharpAmount
        val vibrance = p.vibrance
        val saturation = p.saturation
        val contrast = p.contrast
        val brightness = p.brightness
        val blackLevel = p.blackLevel

        for (i in pixels.indices) {
            val orig = pixels[i]
            val a = orig ushr 24
            var r = (orig shr 16) and 0xFF
            var g = (orig shr 8) and 0xFF
            var b = orig and 0xFF

            // Unsharp mask: orig + amount * (orig - blur), por canal.
            // Se salta entero cuando sharpen = false (Fase 2).
            if (sharpen) {
                val blur = blurred!![i]
                val br = (blur shr 16) and 0xFF
                val bg = (blur shr 8) and 0xFF
                val bb = blur and 0xFF
                r = clamp(r + amount * (r - br))
                g = clamp(g + amount * (g - bg))
                b = clamp(b + amount * (b - bb))
            }

            // Vibrance + saturación. El peso de vibrance es mayor en los
            // píxeles apagados y ~0 en los ya saturados, así los rojos no
            // se queman y la piel no se vuelve naranja. El contraste es
            // una curva S suave alrededor de 128, con +2% de brillo para
            // compensarlo.
            val rf = r.toFloat()
            val gf = g.toFloat()
            val bf = b.toFloat()
            val maxC = max(rf, max(gf, bf))
            val minC = min(rf, min(gf, bf))
            val gray = 0.299f * rf + 0.587f * gf + 0.114f * bf
            // Lo saturado que ya está el píxel (0 = gris, 1 = puro).
            val sat01 = if (maxC <= 0f) 0f else (maxC - minC) / maxC
            // Vibrance: más realce en los apagados; se suaviza cerca de
            // los tonos de piel (naranja-rojo, R alta, G media, B baja)
            // para que las caras sigan naturales.
            val skinGuard = if (rf > gf && gf > bf && rf - bf > 40f) 0.45f else 1f
            val boost = saturation + vibrance * (1f - sat01) * skinGuard
            r = clamp(((gray + (rf - gray) * boost) - 128f) * contrast + 128f + brightness)
            g = clamp(((gray + (gf - gray) * boost) - 128f) * contrast + 128f + brightness)
            b = clamp(((gray + (bf - gray) * boost) - 128f) * contrast + 128f + brightness)

            // Negros limpios para AMOLED: los píxeles casi negros se
            // llevan a negro de verdad, sin tocar el resto. La portada
            // se integra con el fondo negro puro en vez de verse como un
            // rectángulo grisáceo.
            if (max(r, max(g, b)) < blackLevel) {
                r = 0
                g = 0
                b = 0
            }

            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun clamp(v: Float): Int = when {
        v <= 0f -> 0
        v >= 255f -> 255
        else -> v.toInt()
    }

    /** Separable 3x3 box blur (una pasada horizontal + una vertical). */
    private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int) {
        // Horizontal
        val tmp = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val x0 = if (x > 0) x - 1 else x
                val x1 = if (x < w - 1) x + 1 else x
                var r = 0
                var g = 0
                var b = 0
                for (xx in x0..x1) {
                    val px = src[row + xx]
                    r += (px shr 16) and 0xFF
                    g += (px shr 8) and 0xFF
                    b += px and 0xFF
                }
                val n = x1 - x0 + 1
                tmp[row + x] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        // Vertical
        for (y in 0 until h) {
            val y0 = if (y > 0) y - 1 else y
            val y1 = if (y < h - 1) y + 1 else y
            val n = y1 - y0 + 1
            for (x in 0 until w) {
                var r = 0
                var g = 0
                var b = 0
                for (yy in y0..y1) {
                    val px = tmp[yy * w + x]
                    r += (px shr 16) and 0xFF
                    g += (px shr 8) and 0xFF
                    b += px and 0xFF
                }
                dst[y * w + x] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
    }
}
