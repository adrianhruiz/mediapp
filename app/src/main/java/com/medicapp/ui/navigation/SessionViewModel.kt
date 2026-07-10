package com.medicapp.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data object LoggedIn : SessionState
}

class SessionViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = authRepository.authStateUserId
        .map { uid -> if (uid != null) SessionState.LoggedIn else SessionState.LoggedOut }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionState.Loading)

    fun signOut() {
        authRepository.signOut()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicApp
                SessionViewModel(app.container.authRepository)
            }
        }
    }
}
