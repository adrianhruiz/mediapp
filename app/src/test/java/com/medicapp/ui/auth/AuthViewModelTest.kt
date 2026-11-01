package com.medicapp.ui.auth

import com.medicapp.data.repository.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

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
    fun `signIn requires both email and password`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)
        var succeeded = false

        viewModel.signIn { succeeded = true }

        assertFalse(succeeded)
        assertEquals("Introduce email y contraseña.", viewModel.uiState.value.errorMessage)
        assertTrue(repository.signInCalls.isEmpty())
    }

    @Test
    fun `signIn calls the repository and reports success`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)
        viewModel.onEmailChanged("user@test.com")
        viewModel.onPasswordChanged("secret1")
        var succeeded = false

        viewModel.signIn { succeeded = true }
        advanceUntilIdle()

        assertTrue(succeeded)
        assertEquals(listOf("user@test.com" to "secret1"), repository.signInCalls)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `signIn surfaces a friendly error when the repository throws`() = runTest(dispatcher) {
        val repository = FakeAuthRepository().apply { shouldFailAuth = true }
        val viewModel = AuthViewModel(repository)
        viewModel.onEmailChanged("user@test.com")
        viewModel.onPasswordChanged("secret1")
        var succeeded = false

        viewModel.signIn { succeeded = true }
        advanceUntilIdle()

        assertFalse(succeeded)
        assertEquals("No se pudo iniciar sesión. Comprueba tus datos.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `signUp requires a password with at least 6 characters`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)
        viewModel.onEmailChanged("user@test.com")
        viewModel.onPasswordChanged("123")

        viewModel.signUp {}

        assertEquals("La contraseña debe tener al menos 6 caracteres.", viewModel.uiState.value.errorMessage)
        assertTrue(repository.signUpCalls.isEmpty())
    }

    @Test
    fun `signUp calls the repository and reports success`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)
        viewModel.onEmailChanged("new@test.com")
        viewModel.onPasswordChanged("secret1")
        var succeeded = false

        viewModel.signUp { succeeded = true }
        advanceUntilIdle()

        assertTrue(succeeded)
        assertEquals(listOf("new@test.com" to "secret1"), repository.signUpCalls)
    }

    @Test
    fun `password reset asks for the email first`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)

        viewModel.sendPasswordReset()
        advanceUntilIdle()

        assertEquals("Escribe tu email para recuperar la contraseña.", viewModel.uiState.value.errorMessage)
        assertTrue(repository.passwordResetCalls.isEmpty())
    }

    @Test
    fun `password reset sends the email to the trimmed address and says so`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)
        viewModel.onEmailChanged("  user@test.com ")

        viewModel.sendPasswordReset()
        advanceUntilIdle()

        assertEquals(listOf("user@test.com"), repository.passwordResetCalls)
        assertEquals(
            "Si hay una cuenta con ese email, te llegará un enlace para elegir una contraseña nueva. Revisa también el correo no deseado.",
            viewModel.uiState.value.infoMessage,
        )
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `a failed password reset is reported`() = runTest(dispatcher) {
        val repository = FakeAuthRepository().apply { shouldFailAuth = true }
        val viewModel = AuthViewModel(repository)
        viewModel.onEmailChanged("user@test.com")

        viewModel.sendPasswordReset()
        advanceUntilIdle()

        assertEquals("No se pudo enviar el email. Comprueba el email y la conexión.", viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.infoMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `editing the email clears the password reset notice`() = runTest(dispatcher) {
        val viewModel = AuthViewModel(FakeAuthRepository())
        viewModel.onEmailChanged("user@test.com")
        viewModel.sendPasswordReset()
        advanceUntilIdle()

        viewModel.onEmailChanged("other@test.com")

        assertNull(viewModel.uiState.value.infoMessage)
    }
}
