package com.medicapp.data.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    val currentUserId: String?

    val authStateUserId: Flow<String?>

    suspend fun signUp(email: String, password: String)

    suspend fun signIn(email: String, password: String)

    fun signOut()
}
