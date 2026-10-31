package com.medicapp.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.data.backup.BackupSerializer

private fun records(count: Int) = if (count == 1) "1 registro" else "$count registros"

/**
 * Menú de la cuenta: copias de los datos y cerrar sesión. Cerrar sesión va dentro del menú y no
 * como icono suelto: junto a los demás iconos de la cabecera era fácil salirse sin querer.
 *
 * Los resultados de las copias salen en [snackbarHostState], que pone la pantalla que lo aloja.
 */
@Composable
fun BackupMenu(
    snackbarHostState: SnackbarHostState,
    onLogout: () -> Unit,
    backupViewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
) {
    val context = LocalContext.current
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val pendingImport by backupViewModel.pendingImport.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }

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
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Copias y cuenta")
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Exportar mis datos") },
                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                enabled = !backupBusy,
                onClick = {
                    menuExpanded = false
                    exportLauncher.launch(BackupSerializer.suggestedFileName(System.currentTimeMillis()))
                },
            )
            DropdownMenuItem(
                text = { Text("Restaurar una copia") },
                leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                enabled = !backupBusy,
                onClick = {
                    menuExpanded = false
                    importLauncher.launch(arrayOf("application/json"))
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Cerrar sesión") },
                leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onLogout()
                },
            )
        }
    }
}
