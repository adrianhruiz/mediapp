package com.medicapp.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.medicapp.model.MedicationDose

object DoseAlarmScheduler {

    const val EXTRA_SCHEDULE_ID = "scheduleId"
    const val EXTRA_DOSE_ID = "doseId"
    const val EXTRA_NOMBRE_MEDICAMENTO = "nombreMedicamento"
    const val EXTRA_DOSE_NUMBER = "doseNumber"
    const val EXTRA_TOTAL_DOSES = "totalDoses"

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

    fun cancelDose(context: Context, scheduleId: String, doseId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = dosePendingIntent(context, scheduleId, doseId) {}
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun dosePendingIntent(
        context: Context,
        scheduleId: String,
        doseId: String,
        extras: (Intent) -> Unit,
    ): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java).apply {
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_DOSE_ID, doseId)
            extras(this)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(scheduleId, doseId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(scheduleId: String, doseId: String): Int = (scheduleId + doseId).hashCode()
}
