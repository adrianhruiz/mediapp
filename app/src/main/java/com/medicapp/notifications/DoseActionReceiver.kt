package com.medicapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.medicapp.MedicApp
import com.medicapp.model.DoseStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_TAKEN) return
        val scheduleId = intent.getStringExtra(DoseAlarmScheduler.EXTRA_SCHEDULE_ID) ?: return
        val doseId = intent.getStringExtra(DoseAlarmScheduler.EXTRA_DOSE_ID) ?: return

        val pendingResult = goAsync()
        val app = context.applicationContext as MedicApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                markDoseTaken(app, scheduleId, doseId)
                NotificationManagerCompat.from(context).cancel(doseId.hashCode())
            } catch (e: Exception) {
                Log.w("DoseActionReceiver", "Failed to mark dose as taken", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun markDoseTaken(app: MedicApp, scheduleId: String, doseId: String) {
        if (app.container.authRepository.currentUserId == null) return
        val schedule = app.container.scheduleRepository.getSchedule(scheduleId) ?: return
        val dose = app.container.scheduleRepository.getDose(scheduleId, doseId) ?: return
        if (dose.status != DoseStatus.PENDING) return

        val historyEntryId = app.container.medicationHistoryRepository.add(
            nregistro = schedule.nregistro,
            nombreMedicamento = schedule.nombreMedicamento,
            reasonId = schedule.reasonId,
            reasonNombre = schedule.reasonNombre,
            dosisSnapshot = null,
            fotoUrl = schedule.fotoUrl,
        )
        app.container.scheduleRepository.markDoseTaken(scheduleId, doseId, historyEntryId)
    }

    companion object {
        const val ACTION_MARK_TAKEN = "com.medicapp.action.MARK_DOSE_TAKEN"
    }
}
