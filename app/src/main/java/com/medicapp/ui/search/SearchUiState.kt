package com.medicapp.ui.search

import com.medicapp.model.Medication

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Empty : SearchUiState
    data class Success(
        val results: List<Medication>,
        val totalFilas: Int,
        val savedNregistros: Set<String> = emptySet(),
    ) : SearchUiState
    data class Error(val message: String) : SearchUiState
}
