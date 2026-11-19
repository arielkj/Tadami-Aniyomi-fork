package app.maparadio.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de errores (v1.0.1): si la app se cierra por un error no
 * controlado, guarda la traza en filesDir/crash.log para poder verla
 * y compartirla desde Ajustes -> Registro de errores. Sin internet,
 * sin dependencias, sin nada pesado: solo escribe un archivo.
 *
 * Se instala al abrir la app (primera linea de onCreate), ANTES de
 * inicializar el mapa, para que un cierre al cargar el mapa tambien
 * quede registrado.
 */
object CrashLog {
    private const val TAG = "MapaRadioCrash"
    private const val FILE_NAME = "crash.log"

    @Volatile
    private var installed = false

    /** Instala el manejador una sola vez; encadena con el anterior. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(appContext, thread, throwable)
            } catch (_: Exception) {
                // Nunca romper el cierre normal por culpa del registro.
            }
            // Dejar que el sistema cierre la app como siempre.
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val file = File(context.filesDir, FILE_NAME)
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val version = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.versionCode})"
        } catch (_: PackageManager.NameNotFoundException) {
            "?"
        }
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val text = buildString {
            appendLine("Mapa Radio — error no controlado")
            appendLine("Fecha: $date")
            appendLine("Version: $version")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Modelo: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Hilo: ${thread.name}")
            appendLine("---")
            append(sw.toString())
        }
        file.writeText(text)
        Log.e(TAG, "Crash guardado en ${file.absolutePath}")
    }

    /**
     * Añade una linea de evento con fecha al mismo registro (v1.0.3):
     * para fallos que NO cierran la app, como una ruta que no se pudo
     * calcular. Asi el proximo reporte trae el motivo exacto y no hay
     * que adivinar. El archivo se recorta si crece demasiado.
     */
    fun logEvent(context: Context, message: String) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists() && file.length() > 128 * 1024) {
                val lines = file.readLines()
                file.writeText(lines.takeLast(200).joinToString("\n") + "\n")
            }
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            file.appendText("Evento: $date — $message\n")
        } catch (_: Exception) {
            // El registro nunca debe romper la app.
        }
    }

    /** (fecha, primera linea de la excepcion) del ultimo cierre, o null. */
    fun lastCrashInfo(context: Context): Pair<String, String>? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) return null
        return try {
            val lines = file.readLines()
            val date = lines.firstOrNull { it.startsWith("Fecha: ") }
                ?.removePrefix("Fecha: ")?.trim().orEmpty()
            val exception = lines.firstOrNull {
                (it.contains("Exception") || it.contains("Error")) &&
                    !it.startsWith("Mapa Radio") && !it.startsWith("Fecha:")
            }?.trim().orEmpty()
            if (date.isEmpty() && exception.isEmpty()) null else date to exception
        } catch (_: Exception) {
            null
        }
    }

    /** Intent para compartir el registro completo, o null si no hay. */
    fun shareIntent(context: Context): Intent? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) return null
        val text = try {
            file.readText()
        } catch (_: Exception) {
            return null
        }
        return Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Mapa Radio — registro de errores")
            .putExtra(Intent.EXTRA_TEXT, text)
    }
}
