package com.medicapp.ui.navigation

import com.medicapp.data.repository.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `logged in user id maps to LoggedIn state`() = runTest(dispatcher) {
        val viewModel = SessionViewModel(FakeAuthRepository(initialUserId = "uid-1"))
        val collectorJob = launch { viewModel.sessionState.collect {} }
        runCurrent()

        assertEquals(SessionState.LoggedIn, viewModel.sessionState.value)
        collectorJob.cancel()
    }

    @Test
    fun `no user id maps to LoggedOut state`() = runTest(dispatcher) {
        val viewModel = SessionViewModel(FakeAuthRepository(initialUserId = null))
        val collectorJob = launch { viewModel.sessionState.collect {} }
        runCurrent()

        assertEquals(SessionState.LoggedOut, viewModel.sessionState.value)
        collectorJob.cancel()
    }

    @Test
    fun `signing out flips the session state back to LoggedOut`() = runTest(dispatcher) {
        val viewModel = SessionViewModel(FakeAuthRepository(initialUserId = "uid-1"))
        val collectorJob = launch { viewModel.sessionState.collect {} }
        runCurrent()
        assertEquals(SessionState.LoggedIn, viewModel.sessionState.value)

        viewModel.signOut()
        runCurrent()

        assertEquals(SessionState.LoggedOut, viewModel.sessionState.value)
        collectorJob.cancel()
    }
}
