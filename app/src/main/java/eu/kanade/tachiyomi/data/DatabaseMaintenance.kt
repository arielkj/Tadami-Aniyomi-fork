package eu.kanade.tachiyomi.data

import android.content.Context
import android.os.StatFs
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.mi.data.AnimeDatabase
import tachiyomi.novel.data.NovelDatabase
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * V6: limpieza periódica ligera de las bases de datos.
 *
 * - ANALYZE cada semana: actualiza las estadísticas del planificador para que
 *   las consultas de la biblioteca (vistas con JOINs y agregados) elijan buen plan.
 * - VACUUM solo cuando hay muchas páginas libres (>1000, ~4 MB) y hay espacio
 *   libre en disco para reescribir la base; si no, PRAGMA incremental_vacuum.
 * - auto_vacuum = INCREMENTAL tras el primer VACUUM, para que las páginas que
 *   se liberen después se puedan reclamar sin reescribir toda la base.
 *
 * Corre en el hilo de fondo del warmup de AppModule, como mucho una vez por
 * semana, y todo va con try/catch: un fallo aquí nunca puede tumbar la app ni
 * bloquear el arranque.
 */
object DatabaseMaintenance {

    private const val PREFS_NAME = "db_maintenance"
    private const val KEY_LAST_RUN_MS = "last_run_ms"
    private const val INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    private const val FREELIST_PAGES_FOR_VACUUM = 1000L

    fun runIfDue(context: Context) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastRun = prefs.getLong(KEY_LAST_RUN_MS, 0L)
            val now = System.currentTimeMillis()
            if (now - lastRun < INTERVAL_MS) return
            prefs.edit().putLong(KEY_LAST_RUN_MS, now).apply()

            runForDatabase("anime") {
                val queries = Injekt.get<AnimeDatabase>().maintenanceQueries
                queries.analyzeLibrary()
                maintainFreelist(
                    context = context,
                    dbName = "tachiyomi.animedb",
                    freelistCount = { queries.libraryFreelistCount().executeAsOne() },
                    incrementalVacuum = { queries.incrementalVacuumLibrary() },
                    vacuum = { queries.vacuumLibrary() },
                )
                queries.setIncrementalAutoVacuum()
            }
            runForDatabase("manga") {
                val queries = Injekt.get<Database>().maintenanceQueries
                queries.analyzeLibrary()
                maintainFreelist(
                    context = context,
                    dbName = "tachiyomi.db",
                    freelistCount = { queries.libraryFreelistCount().executeAsOne() },
                    incrementalVacuum = { queries.incrementalVacuumLibrary() },
                    vacuum = { queries.vacuumLibrary() },
                )
                queries.setIncrementalAutoVacuum()
            }
            runForDatabase("novel") {
                val queries = Injekt.get<NovelDatabase>().maintenanceQueries
                queries.analyzeLibrary()
                maintainFreelist(
                    context = context,
                    dbName = "tachiyomi.noveldb",
                    freelistCount = { queries.libraryFreelistCount().executeAsOne() },
                    incrementalVacuum = { queries.incrementalVacuumLibrary() },
                    vacuum = { queries.vacuumLibrary() },
                )
                queries.setIncrementalAutoVacuum()
            }
        }.onFailure {
            logcat(LogPriority.WARN, it) { "DatabaseMaintenance failed" }
        }
    }

    private inline fun runForDatabase(name: String, block: () -> Unit) {
        runCatching { block() }
            .onFailure { logcat(LogPriority.WARN, it) { "DatabaseMaintenance ($name) failed" } }
    }

    /**
     * Reclama páginas libres: VACUUM completo si hay muchas y hay espacio de
     * sobra en disco; si no, un incremental_vacuum acotado. [freelistCount]
     * devuelve el PRAGMA freelist_count como texto.
     */
    private inline fun maintainFreelist(
        context: Context,
        dbName: String,
        freelistCount: () -> String,
        incrementalVacuum: () -> Unit,
        vacuum: () -> Unit,
    ) {
        val freePages = runCatching { freelistCount().toLongOrNull() ?: 0L }.getOrDefault(0L)
        if (freePages <= 0L) return
        if (freePages < FREELIST_PAGES_FOR_VACUUM) {
            // Pocas páginas libres: reclamar una parte sin reescribir la base.
            runCatching { incrementalVacuum() }
            return
        }
        val dbFile = context.getDatabasePath(dbName)
        val dbSize = runCatching { dbFile.length() }.getOrDefault(0L)
        val freeBytes = runCatching {
            val statFs = StatFs(context.filesDir.path)
            statFs.availableBytes
        }.getOrDefault(0L)
        // VACUUM reescribe la base entera: solo si sobra espacio (2x el tamaño).
        if (dbSize > 0L && freeBytes > dbSize * 2) {
            runCatching { vacuum() }
        }
    }
}
