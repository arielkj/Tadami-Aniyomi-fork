package eu.kanade.tachiyomi.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * V7.4 (B4): el mantenimiento semanal de la BD (ANALYZE/VACUUM) ya no
 * corre en el arranque de la app. VACUUM toma un bloqueo exclusivo de la
 * base y las consultas paginadas de la biblioteca se quedaban esperando a
 * que terminara: justo el arranque en que tocaba mantenimiento, la
 * ruedita duraba mucho más. Ahora se encola como trabajo en segundo
 * plano y WorkManager lo ejecuta cuando pueda; runIfDue sigue
 * limitándolo a 1 vez por semana y un fallo aquí nunca tumba la app.
 */
class DatabaseMaintenanceWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching { DatabaseMaintenance.runIfDue(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "DatabaseMaintenance"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<DatabaseMaintenanceWorker>()
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
