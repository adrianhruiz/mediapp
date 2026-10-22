package com.medicapp.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** La hora actual, una vez por minuto: basta para "toca en 5 min" y para cambiar de día. */
fun minuteTicks(): Flow<Long> = flow {
    while (true) {
        val now = System.currentTimeMillis()
        emit(now)
        delay(60_000 - now % 60_000)
    }
}
