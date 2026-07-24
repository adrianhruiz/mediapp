package com.medicapp.ui.search

import androidx.compose.runtime.Immutable
import com.medicapp.model.Medication

/**
 * Los distintivos ("ya en tu historial", "ya lo tienes guardado") viven fuera de este estado
 * a propósito: llegan de Firestore y cambian por su cuenta, así que incluirlos aquí obligaba
 * a reconstruir el resultado de búsqueda entero cada vez que se movía cualquiera de los dos.
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Empty : SearchUiState

    @Immutable
    data class Success(
        val results: List<Medication>,
        val totalFilas: Int,
        /** Los resultados salieron de la caché: pueden estar desfasados. */
        val isFromCache: Boolean = false,
    ) : SearchUiState

    data class Error(val message: String) : SearchUiState
}
