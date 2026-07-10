package com.medicapp.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val DOSE_REMINDERS_CHANNEL_ID = "dose_reminders"

    fun create(context: Context) {
        val channel = NotificationChannel(
            DOSE_REMINDERS_CHANNEL_ID,
            "Recordatorios de toma",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avisos para tomar tus medicamentos programados"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
