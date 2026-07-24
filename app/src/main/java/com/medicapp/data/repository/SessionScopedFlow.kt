package com.medicapp.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Comparte un listener entre las pantallas que miran los mismos datos, en vez de abrir uno por
 * cada una, y lo ata a la sesión.
 *
 * Lo segundo no es un detalle: compartir con memoria de lo último emitido es justo lo que
 * permitiría que, al cerrar sesión y entrar con otra cuenta, la segunda viera de refilón los
 * datos de la primera. Dos cosas lo impiden:
 *
 * - Al cerrar sesión, [flatMapLatest] cancela el listener y pasa a emitir [emptyValue], así que
 *   lo último recordado deja de ser información de nadie.
 * - `replayExpirationMillis = 0` borra esa memoria en cuanto el último suscriptor se va, de modo
 *   que quien vuelva a suscribirse no recibe nada de la sesión anterior mientras llegan sus
 *   propios datos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> AuthRepository.sharedPerSession(
    scope: CoroutineScope,
    emptyValue: T,
    listen: (userId: String) -> Flow<T>,
): Flow<T> = authStateUserId
    .flatMapLatest { userId -> if (userId == null) flowOf(emptyValue) else listen(userId) }
    .shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = STOP_TIMEOUT_MILLIS,
            replayExpirationMillis = 0,
        ),
        replay = 1,
    )
