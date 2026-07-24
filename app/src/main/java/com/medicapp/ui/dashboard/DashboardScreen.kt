package com.medicapp.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

    LaunchedEffect(backupState) {
        when (val state = backupState) {
            is BackupUiState.Exported -> {
                backupSnackbarHostState.showSnackbar("Copia guardada · ${state.itemCount} registros")
                backupViewModel.dismissMessage()
            }
            is BackupUiState.Error -> {
                backupSnackbarHostState.showSnackbar(state.message)
                backupViewModel.dismissMessage()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mediapp") },
                actions = {
                    IconButton(
                        onClick = {
                            exportLauncher.launch(BackupSerializer.suggestedFileName(System.currentTimeMillis()))
                        },
                        enabled = backupState !is BackupUiState.Exporting,
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Exportar mis datos")
                    }
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
