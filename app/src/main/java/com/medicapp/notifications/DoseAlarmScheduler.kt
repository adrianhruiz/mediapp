package com.medicapp.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.medicapp.model.MedicationDose

object DoseAlarmScheduler {

    const val EXTRA_SCHEDULE_ID = "scheduleId"
    const val EXTRA_DOSE_ID = "doseId"
    const val EXTRA_NOMBRE_MEDICAMENTO = "nombreMedicamento"
    const val EXTRA_DOSE_NUMBER = "doseNumber"
    const val EXTRA_TOTAL_DOSES = "totalDoses"

    /** Marca el aviso de "no la has tomado", que llega un rato después del aviso normal. */
    const val EXTRA_IS_REMINDER = "isReminder"

    /** Cuánto se espera tras la hora de la toma antes de avisar de que sigue sin marcar. */
    const val MISSED_REMINDER_DELAY_MILLIS = 3_600_000L

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    fun scheduleDose(
        context: Context,
        scheduleId: String,
        dose: MedicationDose,
        nombreMedicamento: String,
        totalDoses: Int,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = dosePendingIntent(context, scheduleId, dose.id) { intent ->
            intent.putExtra(EXTRA_NOMBRE_MEDICAMENTO, nombreMedicamento)
            intent.putExtra(EXTRA_DOSE_NUMBER, dose.doseNumber)
            intent.putExtra(EXTRA_TOTAL_DOSES, totalDoses)
        }
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dose.scheduledAtMillis, pendingIntent)
        } catch (e: SecurityException) {
            Log.w("DoseAlarmScheduler", "Missing exact alarm permission", e)
        }
    }

    /**
     * Segundo aviso para una toma que sigue sin marcar. Se programa al saltar el primero y el
     * receptor comprueba entonces si la toma sigue pendiente antes de avisar.
     */
    fun scheduleMissedReminder(
        context: Context,
        scheduleId: String,
        doseId: String,
        nombreMedicamento: String,
        doseNumber: Int,
        totalDoses: Int,
        atMillis: Long,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = dosePendingIntent(context, scheduleId, doseId, reminder = true) { intent ->
            intent.putExtra(EXTRA_NOMBRE_MEDICAMENTO, nombreMedicamento)
            intent.putExtra(EXTRA_DOSE_NUMBER, doseNumber)
            intent.putExtra(EXTRA_TOTAL_DOSES, totalDoses)
        }
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
        } catch (e: SecurityException) {
            Log.w("DoseAlarmScheduler", "Missing exact alarm permission", e)
        }
    }

    /** Quita las alarmas de la toma (el aviso y su recordatorio) y sus notificaciones ya mostradas. */
    fun cancelDose(context: Context, scheduleId: String, doseId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        listOf(false, true).forEach { reminder ->
            val pendingIntent = dosePendingIntent(context, scheduleId, doseId, reminder) {}
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        NotificationManagerCompat.from(context).apply {
            cancel(notificationId(doseId, reminder = false))
            cancel(notificationId(doseId, reminder = true))
        }
    }

    fun notificationId(doseId: String, reminder: Boolean): Int =
        if (reminder) (doseId + REMINDER_SUFFIX).hashCode() else doseId.hashCode()

    private fun dosePendingIntent(
        context: Context,
        scheduleId: String,
        doseId: String,
        reminder: Boolean = false,
        extras: (Intent) -> Unit,
    ): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java).apply {
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_DOSE_ID, doseId)
            putExtra(EXTRA_IS_REMINDER, reminder)
            extras(this)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(scheduleId, doseId, reminder),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(scheduleId: String, doseId: String, reminder: Boolean): Int =
        (scheduleId + doseId + if (reminder) REMINDER_SUFFIX else "").hashCode()

    private const val REMINDER_SUFFIX = "#recordatorio"
}
