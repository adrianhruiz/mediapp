package com.medicapp.notifications

import com.medicapp.data.repository.FakeAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
/** Con runCurrent: advanceUntilIdle no ejecuta el trabajo lanzado en backgroundScope. */
class SessionDoseReschedulerTest {

    @Test
    fun `doses are rescheduled when the app starts with a session`() = runTest {
        var calls = 0
        SessionDoseRescheduler.start(backgroundScope, FakeAuthRepository("ana")) { calls++ }
        runCurrent()

        assertEquals(1, calls)
    }

    @Test
    fun `nothing is rescheduled without a session, and signing in reschedules`() = runTest {
        val auth = FakeAuthRepository(initialUserId = null)
        var calls = 0
        SessionDoseRescheduler.start(backgroundScope, auth) { calls++ }
        runCurrent()
        assertEquals(0, calls)

        auth.signIn("ana@example.com", "secret")
        runCurrent()

        assertEquals(1, calls)
    }

    @Test
    fun `signing out does not reschedule`() = runTest {
        val auth = FakeAuthRepository("ana")
        var calls = 0
        SessionDoseRescheduler.start(backgroundScope, auth) { calls++ }
        runCurrent()

        auth.signOut()
        runCurrent()

        assertEquals(1, calls)
    }
}
