package com.medicapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.medicapp.MedicApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        val app = context.applicationContext as MedicApp
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (app.container.authRepository.currentUserId == null) return@launch
                val now = System.currentTimeMillis()
                app.container.scheduleRepository.getActiveSchedulesWithPendingDoses().forEach { scheduleWithDoses ->
                    scheduleWithDoses.doses
                        .filter { it.scheduledAtMillis > now }
                        .forEach { dose ->
                            DoseAlarmScheduler.scheduleDose(
                                appContext,
                                scheduleWithDoses.schedule.id,
                                dose,
                                scheduleWithDoses.schedule.nombreMedicamento,
                                scheduleWithDoses.schedule.totalDoses,
                            )
                        }
                }
            } catch (e: Exception) {
                Log.w("BootRescheduleReceiver", "Failed to reschedule doses after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
