package com.medicapp

import android.app.Application
import com.medicapp.di.AppContainer
import com.medicapp.notifications.NotificationChannels

class MedicApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
        NotificationChannels.create(this)
    }
}
