package com.medicapp.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.medicapp.MainActivity
import com.medicapp.MedicApp
import com.medicapp.model.DoseStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Salta a la hora de cada toma y avisa. Un rato después ([DoseAlarmScheduler.MISSED_REMINDER_DELAY_MILLIS])
 * vuelve a saltar como recordatorio, y solo avisa si la toma sigue sin marcar.
 */
class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminder = intent.getBooleanExtra(DoseAlarmScheduler.EXTRA_IS_REMINDER, false)
        if (!reminder) {
            showNotification(context, intent, reminder = false)
            scheduleMissedReminder(context, intent)
            return
        }
        val scheduleId = intent.getStringExtra(DoseAlarmScheduler.EXTRA_SCHEDULE_ID) ?: return
        val doseId = intent.getStringExtra(DoseAlarmScheduler.EXTRA_DOSE_ID) ?: return
        val pendingResult = goAsync()
        val app = context.applicationContext as MedicApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Un solo documento: si ya se marcó (o la pauta se cerró) no hay nada que recordar.
                val stillPending = app.container.authRepository.currentUserId != null &&
                    app.container.scheduleRepository.getDose(scheduleId, doseId)?.status == DoseStatus.PENDING
                if (stillPending) showNotification(context, intent, reminder = true)
            } catch (e: Exception) {
                Log.w("DoseAlarmReceiver", "Failed to check missed dose", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun scheduleMissedReminder(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra(DoseAlarmScheduler.EXTRA_SCHEDULE_ID) ?: return
        val doseId = intent.getStringExtra(DoseAlarmScheduler.EXTRA_DOSE_ID) ?: return
        DoseAlarmScheduler.scheduleMissedReminder(
            context,
            scheduleId,
            doseId,
            intent.getStringExtra(DoseAlarmScheduler.EXTRA_NOMBRE_MEDICAMENTO).orEmpty(),
            intent.getIntExtra(DoseAlarmScheduler.EXTRA_DOSE_NUMBER, 1),
            intent.getIntExtra(DoseAlarmScheduler.EXTRA_TOTAL_DOSES, 1),
            System.currentTimeMillis() + DoseAlarmScheduler.MISSED_REMINDER_DELAY_MILLIS,
        )
    }

    private fun showNotification(context: Context, intent: Intent, reminder: Boolean) {
        val scheduleId = intent.getStringExtra(DoseAlarmScheduler.EXTRA_SCHEDULE_ID) ?: return
        val doseId = intent.getStringExtra(DoseAlarmScheduler.EXTRA_DOSE_ID) ?: return
        val nombreMedicamento = intent.getStringExtra(DoseAlarmScheduler.EXTRA_NOMBRE_MEDICAMENTO).orEmpty()
        val doseNumber = intent.getIntExtra(DoseAlarmScheduler.EXTRA_DOSE_NUMBER, 1)
        val totalDoses = intent.getIntExtra(DoseAlarmScheduler.EXTRA_TOTAL_DOSES, 1)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_SCHEDULE_ID, scheduleId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            DoseAlarmScheduler.notificationId(doseId, reminder),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val actionIntent = Intent(context, DoseActionReceiver::class.java).apply {
            action = DoseActionReceiver.ACTION_MARK_TAKEN
            putExtra(DoseAlarmScheduler.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(DoseAlarmScheduler.EXTRA_DOSE_ID, doseId)
        }
        val actionPendingIntent = PendingIntent.getBroadcast(
            context,
            DoseAlarmScheduler.notificationId(doseId, reminder),
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.DOSE_REMINDERS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (reminder) "Sin tomar: $nombreMedicamento" else "Hora de tomar $nombreMedicamento")
            .setContentText(DoseNotificationText.content(doseNumber, totalDoses, reminder))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Marcar tomada", actionPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(DoseAlarmScheduler.notificationId(doseId, reminder), notification)
        } catch (e: SecurityException) {
            Log.w("DoseAlarmReceiver", "Missing POST_NOTIFICATIONS permission", e)
        }
    }
}
