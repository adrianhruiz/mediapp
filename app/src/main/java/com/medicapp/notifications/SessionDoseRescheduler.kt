package com.medicapp.notifications

import android.util.Log
import com.medicapp.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Reprograma las alarmas de las tomas cada vez que hay una sesión: al arrancar la app con la
 * sesión ya iniciada y al iniciar sesión.
 *
 * Las alarmas viven en el móvil y las pautas en Firestore. En un móvil nuevo, tras reinstalar o
 * tras borrar los datos de la app, las pautas aparecen pero sus alarmas no existen, y antes solo
 * se recuperaban al reiniciar el móvil. Programar una alarma que ya existe la sustituye, así que
 * repetirlo en cada arranque no duplica avisos.
 */
object SessionDoseRescheduler {

    fun start(scope: CoroutineScope, authRepository: AuthRepository, reschedule: suspend () -> Unit): Job =
        scope.launch {
            authRepository.authStateUserId
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest {
                    try {
                        reschedule()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("SessionDoseRescheduler", "Failed to reschedule doses for the session", e)
                    }
                }
        }
}
