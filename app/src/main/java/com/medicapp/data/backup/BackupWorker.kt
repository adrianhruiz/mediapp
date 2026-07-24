package com.medicapp.data.backup

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.medicapp.MedicApp
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "copia-automatica"

/**
 * Copia periódica en segundo plano, como red de seguridad frente a un borrado accidental.
 *
 * No sustituye a la exportación manual: esta copia se pierde al desinstalar la app. Para tener
 * los datos fuera del móvil hay que exportarlos a mano.
 */
class BackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MedicApp
        // Sin sesión no hay nada que copiar; se reintentará en el siguiente ciclo.
        if (app.container.authRepository.currentUserId == null) return Result.success()

        return try {
            val backup = app.container.backupRepository.exportAll()
            val store = BackupFileStore(applicationContext.getExternalFilesDir(null))
            store.write(
                fileName = BackupSerializer.suggestedFileName(backup.exportedAtMillis),
                contents = BackupSerializer.encode(backup),
            )
            Result.success()
        } catch (e: Exception) {
            Log.w("BackupWorker", "Automatic backup failed", e)
            Result.retry()
        }
    }

    companion object {
        /**
         * Programa la copia diaria. Con [ExistingPeriodicWorkPolicy.KEEP] se puede llamar en cada
         * arranque sin reiniciar el contador ni duplicar el trabajo.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
