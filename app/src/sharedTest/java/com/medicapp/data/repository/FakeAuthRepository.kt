package com.medicapp.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository(
    initialUserId: String? = "test-user",
) : AuthRepository {

    private val userIdFlow = MutableStateFlow(initialUserId)

    var shouldFailAuth: Boolean = false
    val signUpCalls: MutableList<Pair<String, String>> = mutableListOf()
    val signInCalls: MutableList<Pair<String, String>> = mutableListOf()

    override val currentUserId: String?
        get() = userIdFlow.value

    override val authStateUserId: Flow<String?> = userIdFlow

    override suspend fun signUp(email: String, password: String) {
        if (shouldFailAuth) error("Simulated sign up failure")
        signUpCalls.add(email to password)
        userIdFlow.value = "test-user"
    }

    override suspend fun signIn(email: String, password: String) {
        if (shouldFailAuth) error("Simulated sign in failure")
        signInCalls.add(email to password)
        userIdFlow.value = "test-user"
    }

    override fun signOut() {
        userIdFlow.value = null
    }
}
