package com.medicapp.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionScopedFlowTest {

    private val session = MutableStateFlow<String?>(null)

    private val auth = object : AuthRepository {
        override val currentUserId: String? get() = session.value
        override val authStateUserId = session
        override suspend fun signUp(email: String, password: String) = Unit
        override suspend fun signIn(email: String, password: String) = Unit
        override fun signOut() {
            session.value = null
        }
    }

    /** Datos distintos por usuario, para poder ver a quién pertenece lo que sale. */
    private fun datosDe(userId: String) = flowOf(listOf("dato-de-$userId"))

    @Test
    fun `gives nothing while there is no session`() = runTest {
        val shared = auth.sharedPerSession(backgroundScope, emptyList<String>(), ::datosDe)

        assertEquals(emptyList<String>(), shared.first())
    }

    @Test
    fun `serves the data of whoever is logged in`() = runTest {
        val shared = auth.sharedPerSession(backgroundScope, emptyList<String>(), ::datosDe)
        session.value = "ana"

        assertEquals(listOf("dato-de-ana"), shared.first())
    }

    @Test
    fun `logging out clears the data of the screen that is looking`() = runTest {
        val shared = auth.sharedPerSession(backgroundScope, emptyList<String>(), ::datosDe)
        session.value = "ana"
        assertEquals(listOf("dato-de-ana"), shared.first())

        session.value = null
        // El cambio de sesión llega por el flujo, no de forma inmediata: hay que dejar correr al
        // planificador para que el listener se cancele y pase a emitir la lista vacía.
        runCurrent()

        assertEquals(emptyList<String>(), shared.first())
    }

    @Test
    fun `a second account never sees the data of the first one`() = runTest {
        val shared = auth.sharedPerSession(backgroundScope, emptyList<String>(), ::datosDe)

        // Ana entra, mira sus datos y sale de la pantalla.
        session.value = "ana"
        assertEquals(listOf("dato-de-ana"), shared.first())

        // Cierra sesión; pasa de sobra el tiempo en que se suelta el listener compartido.
        session.value = null
        advanceTimeBy(10_000)

        // Entra Bruno. Lo primero que reciba no puede ser lo que quedó de Ana.
        session.value = "bruno"

        assertEquals(listOf("dato-de-bruno"), shared.first())
    }
}
