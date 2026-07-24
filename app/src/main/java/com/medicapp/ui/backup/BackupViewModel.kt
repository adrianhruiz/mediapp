package com.medicapp.ui.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.backup.BACKUP_FORMAT_VERSION
import com.medicapp.data.backup.BackupFile
import com.medicapp.data.backup.BackupRepository
import com.medicapp.data.backup.BackupSerializer
import com.medicapp.data.repository.ScheduleRepository
import com.medicapp.notifications.DoseRescheduler
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
    data object Importing : BackupUiState
    data class Imported(val added: Int, val skipped: Int) : BackupUiState
    data class Error(val message: String) : BackupUiState
}

/** Copia ya leída y validada, a la espera de que el usuario confirme la restauración. */
data class PendingImport(val backup: BackupFile)

class BackupViewModel(
    private val backupRepository: BackupRepository,
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

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

    /** Lee y valida el fichero, pero no escribe nada todavía: eso lo decide el usuario. */
    fun prepareImport(contentResolver: ContentResolver, source: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(source)?.use { it.readBytes().decodeToString() }
                        ?: error("No se pudo abrir el fichero")
                }
                val backup = BackupSerializer.decode(text)
                if (backup.version > BACKUP_FORMAT_VERSION) {
                    _uiState.value = BackupUiState.Error(
                        "Esa copia se hizo con una versión más nueva de la app y no se puede leer aquí.",
                    )
                    return@launch
                }
                _pendingImport.value = PendingImport(backup)
            } catch (e: Exception) {
                Log.w("BackupViewModel", "Could not read backup", e)
                _uiState.value = BackupUiState.Error("Ese fichero no parece una copia de Mediapp.")
            }
        }
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

    fun confirmImport(context: Context) {
        val pending = _pendingImport.value ?: return
        _pendingImport.value = null
        _uiState.value = BackupUiState.Importing
        viewModelScope.launch {
            try {
                val result = backupRepository.import(pending.backup)
                // Las pautas restauradas no traen alarmas: sin esto se verían pero no avisarían.
                DoseRescheduler.rescheduleFutureDoses(context, scheduleRepository)
                _uiState.value = BackupUiState.Imported(result.added, result.skipped)
            } catch (e: Exception) {
                Log.w("BackupViewModel", "Import failed", e)
                _uiState.value = BackupUiState.Error("No se pudieron restaurar los datos.")
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
                BackupViewModel(app.container.backupRepository, app.container.scheduleRepository)
            }
        }
    }
}
