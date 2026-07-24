package com.medicapp.ui.backup

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.backup.BackupRepository
import com.medicapp.data.backup.BackupSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Exporting : BackupUiState
    data class Exported(val itemCount: Int) : BackupUiState
    data class Error(val message: String) : BackupUiState
}

class BackupViewModel(
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun export(contentResolver: ContentResolver, destination: Uri) {
        _uiState.value = BackupUiState.Exporting
        viewModelScope.launch {
            try {
                val backup = backupRepository.exportAll()
                val text = BackupSerializer.encode(backup)
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(destination)?.use { it.write(text.toByteArray()) }
                        ?: error("No se pudo abrir el fichero de destino")
                }
                _uiState.value = BackupUiState.Exported(backup.totalItems)
            } catch (e: Exception) {
                Log.w("BackupViewModel", "Export failed", e)
                _uiState.value = BackupUiState.Error("No se pudo guardar la copia de tus datos.")
            }
        }
    }

    fun dismissMessage() {
        _uiState.value = BackupUiState.Idle
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicApp
                BackupViewModel(app.container.backupRepository)
            }
        }
    }
}
