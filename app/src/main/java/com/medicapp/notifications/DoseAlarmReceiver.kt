package com.medicapp.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.medicapp.MainActivity

class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
            doseId.hashCode(),
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
            doseId.hashCode(),
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.DOSE_REMINDERS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Hora de tomar $nombreMedicamento")
            .setContentText("Toma $doseNumber de $totalDoses")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Marcar tomada", actionPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(doseId.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w("DoseAlarmReceiver", "Missing POST_NOTIFICATIONS permission", e)
        }
    }
}
