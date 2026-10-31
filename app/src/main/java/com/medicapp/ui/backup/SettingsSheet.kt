package com.medicapp.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.data.backup.BackupSerializer
import com.medicapp.ui.components.GroupedList
import com.medicapp.ui.components.GroupedRow

private fun records(count: Int) = if (count == 1) "1 registro" else "$count registros"

/**
 * Botón de ajustes de la cabecera de Hoy y su hoja «Ajustes»: copias de los datos y cerrar
 * sesión. Cerrar sesión va dentro de la hoja y no como icono suelto: junto a los demás iconos de
 * la cabecera era fácil salirse sin querer.
 *
 * Los resultados de las copias salen en [snackbarHostState], que pone la pantalla que lo aloja.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsButton(
    snackbarHostState: SnackbarHostState,
    onLogout: () -> Unit,
    backupViewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
) {
    val context = LocalContext.current
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val pendingImport by backupViewModel.pendingImport.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { destination ->
        if (destination != null) backupViewModel.export(context.contentResolver, destination)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { source ->
        if (source != null) backupViewModel.prepareImport(context.contentResolver, source)
    }

    LaunchedEffect(backupState) {
        when (val state = backupState) {
            is BackupUiState.Exported -> {
                snackbarHostState.showSnackbar("Copia guardada · ${records(state.itemCount)}")
                backupViewModel.dismissMessage()
            }
            is BackupUiState.Imported -> {
                val restored = if (state.added == 0) {
                    "No faltaba nada por restaurar"
                } else {
                    "Restaurado${if (state.added == 1) "" else "s"} ${records(state.added)}"
                }
                val kept = if (state.skipped > 0) " · ${state.skipped} ya los tenías" else ""
                snackbarHostState.showSnackbar(restored + kept)
                backupViewModel.dismissMessage()
            }
            is BackupUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                backupViewModel.dismissMessage()
            }
            else -> Unit
        }
    }

    pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = backupViewModel::cancelImport,
            title = { Text("Restaurar copia") },
            text = {
                Text(
                    "La copia contiene ${pending.backup.totalItems} registros. Se añadirá lo que te " +
                        "falte; lo que ya tengas se queda como está, no se sobrescribe nada.",
                )
            },
            confirmButton = {
                TextButton(onClick = { backupViewModel.confirmImport(context) }) { Text("Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = backupViewModel::cancelImport) { Text("Cancelar") }
            },
        )
    }

    val backupBusy = backupState is BackupUiState.Exporting || backupState is BackupUiState.Importing
    IconButton(onClick = { sheetOpen = true }) {
        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
    }
    if (sheetOpen) {
        // La hoja se cierra antes de abrir el selector de ficheros o salir: si no, al volver
        // seguiría abierta tapando el resultado.
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Ajustes", style = MaterialTheme.typography.titleLarge)
                GroupedList {
                    SettingsRow(
                        text = "Exportar mis datos",
                        icon = Icons.Default.Download,
                        enabled = !backupBusy,
                        divider = false,
                        onClick = {
                            sheetOpen = false
                            exportLauncher.launch(BackupSerializer.suggestedFileName(System.currentTimeMillis()))
                        },
                    )
                    SettingsRow(
                        text = "Restaurar una copia",
                        icon = Icons.Default.Upload,
                        enabled = !backupBusy,
                        divider = true,
                        onClick = {
                            sheetOpen = false
                            importLauncher.launch(arrayOf("application/json"))
                        },
                    )
                }
                GroupedList {
                    SettingsRow(
                        text = "Cerrar sesión",
                        icon = Icons.Default.Logout,
                        enabled = true,
                        divider = false,
                        onClick = {
                            sheetOpen = false
                            onLogout()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(text: String, icon: ImageVector, enabled: Boolean, divider: Boolean, onClick: () -> Unit) {
    GroupedRow(
        divider = divider,
        chevron = false,
        modifier = Modifier.alpha(if (enabled) 1f else 0.45f).clickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(icon, contentDescription = null)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
