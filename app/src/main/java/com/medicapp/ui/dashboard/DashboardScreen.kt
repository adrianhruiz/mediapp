package com.medicapp.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.data.backup.BackupSerializer
import com.medicapp.ui.backup.BackupUiState
import com.medicapp.ui.backup.BackupViewModel

private fun records(count: Int) = if (count == 1) "1 registro" else "$count registros"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateSearch: () -> Unit,
    onNavigateAllergies: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateReminders: () -> Unit,
    onLogout: () -> Unit,
    backupViewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
) {
    val context = LocalContext.current
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val backupSnackbarHostState = remember { SnackbarHostState() }
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
    val pendingImport by backupViewModel.pendingImport.collectAsStateWithLifecycle()

    LaunchedEffect(backupState) {
        when (val state = backupState) {
            is BackupUiState.Exported -> {
                backupSnackbarHostState.showSnackbar("Copia guardada · ${records(state.itemCount)}")
                backupViewModel.dismissMessage()
            }
            is BackupUiState.Imported -> {
                val restored = if (state.added == 0) {
                    "No faltaba nada por restaurar"
                } else {
                    "Restaurado${if (state.added == 1) "" else "s"} ${records(state.added)}"
                }
                val kept = if (state.skipped > 0) " · ${state.skipped} ya los tenías" else ""
                backupSnackbarHostState.showSnackbar(restored + kept)
                backupViewModel.dismissMessage()
            }
            is BackupUiState.Error -> {
                backupSnackbarHostState.showSnackbar(state.message)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mediapp") },
                actions = {
                    // Las copias van en un menú y cerrar sesión queda aparte: con los tres iconos
                    // seguidos, las zonas táctiles quedaban pegadas y era fácil salirse sin querer.
                    val backupBusy = backupState is BackupUiState.Exporting ||
                        backupState is BackupUiState.Importing
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }, enabled = !backupBusy) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Copias de mis datos")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Exportar mis datos") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                exportLauncher.launch(
                                    BackupSerializer.suggestedFileName(System.currentTimeMillis()),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Restaurar una copia") },
                            leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                importLauncher.launch(arrayOf("application/json"))
                            },
                        )
                    }
                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(backupSnackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DashboardCard(
                title = "Búsqueda",
                description = "Busca medicamentos y consulta si son recomendables para ti",
                icon = Icons.Default.Search,
                onClick = onNavigateSearch,
            )
            DashboardCard(
                title = "Alergias",
                description = "Gestiona tus alergias a componentes activos y familias",
                icon = Icons.Default.Warning,
                onClick = onNavigateAllergies,
            )
            DashboardCard(
                title = "Medicamentos",
                description = "Consulta el historial de medicamentos que has tomado",
                icon = Icons.Default.Folder,
                onClick = onNavigateHistory,
            )
            DashboardCard(
                title = "Recordatorios",
                description = "Programa avisos de toma y gestiona tus periodos activos",
                icon = Icons.Default.Notifications,
                onClick = onNavigateReminders,
            )
        }
    }
}

@Composable
private fun DashboardCard(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
