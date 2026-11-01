package com.medicapp.ui.allergies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.model.Allergy
import com.medicapp.model.PatientProfile
import com.medicapp.ui.components.GelButton
import com.medicapp.ui.components.GroupHeader
import com.medicapp.ui.components.GroupedList
import com.medicapp.ui.components.GroupedRow
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.ScreenHeader
import com.medicapp.ui.components.SecondaryButton
import com.medicapp.ui.theme.DmMono
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Informe de alergias para enseñar el móvil a un médico en el extranjero: se cambia de idioma
 * con un toque. Los controles para rellenar los datos del paciente son para el dueño y van en
 * español; lo que lee el médico sigue el idioma elegido.
 */
@Composable
fun AllergyReportScreen(
    onBack: () -> Unit,
    viewModel: AllergyReportViewModel = viewModel(factory = AllergyReportViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val text = ReportText.of(uiState.language)
    var editingProfile by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = text.title, onBack = onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            FilterChip(
                selected = uiState.language == ReportLanguage.ES,
                onClick = { viewModel.onLanguageSelected(ReportLanguage.ES) },
                label = { Text("Español") },
            )
            FilterChip(
                selected = uiState.language == ReportLanguage.EN,
                onClick = { viewModel.onLanguageSelected(ReportLanguage.EN) },
                label = { Text("English") },
            )
        }

        if (!uiState.isLoaded) {
            Row(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (uiState.profileSaveFailed) {
            Text(
                "No se han podido guardar los datos del paciente. Inténtalo de nuevo.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        val profile = uiState.profile
        if (profile == null || editingProfile) {
            ProfileForm(
                initial = profile,
                onSave = { nombre, fecha ->
                    viewModel.saveProfile(nombre, fecha)
                    editingProfile = false
                },
                onCancel = if (profile != null) ({ editingProfile = false }) else null,
            )
        } else {
            PatientBlock(profile, text, onEdit = { editingProfile = true })
        }

        Text(
            text.statement,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 24.dp),
        )

        val groups = uiState.groups
        if (groups.families.isEmpty() && groups.ingredients.isEmpty()) {
            Text(text.noAllergies, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
        }
        var editing by remember { mutableStateOf<Allergy?>(null) }
        val english = uiState.language == ReportLanguage.EN
        ReportGroup(text.families, groups.families, english, onEdit = { editing = it })
        ReportGroup(text.ingredients, groups.ingredients, english, onEdit = { editing = it })
        editing?.let { allergy ->
            EnglishNameDialog(
                allergy = allergy,
                onSave = { name ->
                    viewModel.setManualEnglishName(allergy, name)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }

        val hasManual = english && (groups.families + groups.ingredients).any { it.nombreInglesManual }
        if (hasManual) {
            Text(
                text.manualNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Text(
            text.namesNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun PatientBlock(profile: PatientProfile, text: ReportText, onEdit: () -> Unit) {
    GroupHeader(text.patient, modifier = Modifier.padding(top = 24.dp))
    GroupedList {
        GroupedRow {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.nombre, style = MaterialTheme.typography.titleLarge)
                profile.fechaNacimiento?.let { fecha ->
                    Text(
                        "${text.birthDate}: ${text.formatDate(fecha)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
    TextButton(onClick = onEdit) { Text("Editar datos del paciente") }
}

/**
 * Código ATC delante y en negrita: es lo que el médico busca de un vistazo. En inglés, el
 * nombre en español queda debajo, para que una traducción nunca oculte el dato original.
 */
@Composable
private fun ReportGroup(title: String, allergies: List<Allergy>, english: Boolean, onEdit: (Allergy) -> Unit) {
    if (allergies.isEmpty()) return
    GroupHeader(title, modifier = Modifier.padding(top = 16.dp))
    GroupedList {
        allergies.forEachIndexed { index, allergy ->
            val spanish = MedicationNames.pretty(allergy.principioActivoNombre ?: allergy.atcNombre.orEmpty())
            val translated = allergy.nombreIngles?.takeIf { english }
            GroupedRow(divider = index > 0) {
                allergy.atcCodigo?.let { codigo ->
                    Text(
                        codigo,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = DmMono, fontWeight = FontWeight.Bold),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        // El nombre de la OMS va tal cual: su copyright no permite modificarlo.
                        translated?.let { it + if (allergy.nombreInglesManual) " *" else "" } ?: spanish,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (translated != null) {
                        Text(
                            spanish,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Solo lo que no tiene traducción oficial: la de la OMS no se toca.
                if (english && (allergy.nombreIngles == null || allergy.nombreInglesManual)) {
                    IconButton(onClick = { onEdit(allergy) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Escribir el nombre en inglés")
                    }
                }
            }
        }
    }
}

@Composable
private fun EnglishNameDialog(allergy: Allergy, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable(allergy.id) { mutableStateOf(allergy.nombreIngles.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nombre en inglés") },
        text = {
            Column {
                Text(
                    MedicationNames.pretty(allergy.principioActivoNombre ?: allergy.atcNombre.orEmpty()),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "No hay traducción oficial. Lo que escribas saldrá en el informe marcado con * " +
                        "como escrito por el paciente. Déjalo en blanco para quitarlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileForm(
    initial: PatientProfile?,
    onSave: (String, LocalDate?) -> Unit,
    onCancel: (() -> Unit)?,
) {
    var nombre by rememberSaveable { mutableStateOf(initial?.nombre.orEmpty()) }
    var fecha by remember { mutableStateOf(initial?.fechaNacimiento) }
    var showDatePicker by remember { mutableStateOf(false) }

    GroupHeader("Datos del paciente", modifier = Modifier.padding(top = 24.dp))
    OutlinedTextField(
        value = nombre,
        onValueChange = { nombre = it },
        label = { Text("Nombre y apellidos") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Text(
            fecha?.let { ReportText.of(ReportLanguage.ES).formatDate(it) } ?: "Sin fecha de nacimiento",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 12.dp),
        )
        SecondaryButton(onClick = { showDatePicker = true }) { Text("Elegir fecha") }
    }
    GelButton(
        onClick = { onSave(nombre, fecha) },
        enabled = nombre.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) { Text("Guardar datos") }
    if (onCancel != null) {
        SecondaryButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Cancelar")
        }
    }

    if (showDatePicker) {
        // El selector trabaja en milisegundos UTC a medianoche; se convierte en UTC para no
        // correr un día según la zona horaria del móvil.
        // Se abre escribiendo la fecha: llegar a un nacimiento pasando meses en el calendario
        // serían décadas de toques.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = fecha?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialDisplayMode = DisplayMode.Input,
            selectableDates = PastDates,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        fecha = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
    }
}

/** Una fecha de nacimiento no puede ser posterior a hoy. */
@OptIn(ExperimentalMaterial3Api::class)
private object PastDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcTimeMillis <= LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
}
