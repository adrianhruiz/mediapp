package com.medicapp

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.medicapp.data.backup.BackupWorker
import com.medicapp.di.AppContainer
import com.medicapp.notifications.NotificationChannels

class MedicApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
        NotificationChannels.create(this)
        BackupWorker.schedule(this)
    }

    /**
     * Coil crearía su propio cliente HTTP, con otro pool de conexiones y otra caché de disco.
     * Reutilizando el que ya tenemos, las fotos de CIMA viajan por las mismas conexiones que
     * el resto de peticiones a la API.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.okHttpClient }))
            }
            .crossfade(true)
            .build()
}
