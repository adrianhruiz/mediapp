package com.medicapp.ui.allergies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import com.medicapp.model.AtcCode
import com.medicapp.model.PrincipioActivo
import com.medicapp.ui.components.GelBarButton
import com.medicapp.ui.components.GelButton
import com.medicapp.ui.components.GelTone
import com.medicapp.ui.components.GroupHeader
import com.medicapp.ui.components.GroupedList
import com.medicapp.ui.components.GroupedRow
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.ScreenHeader
import com.medicapp.ui.components.SearchHelpButton
import com.medicapp.ui.search.CapsuleSearchField
import com.medicapp.ui.theme.DmMono

private val addedTint = Color(0xFF9AA2AE)

@Composable
fun AllergiesScreen(
    contentPadding: PaddingValues,
    viewModel: AllergiesViewModel = viewModel(factory = AllergiesViewModel.Factory),
) {
    val allergies by viewModel.allergies.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    // Como el iOS clásico: el − rojo no borra, descubre el botón «Eliminar» de esa fila.
    var armedId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    // Sin alergias no hay nada que editar: el modo se apaga solo.
    val inEditMode = editing && allergies.isNotEmpty()

    if (adding) AddAllergySheet(viewModel = viewModel, allergies = allergies, onDismiss = { adding = false })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = "Alergias") {
            if (allergies.isNotEmpty()) {
                GelBarButton(onClick = {
                    editing = !inEditMode
                    armedId = null
                }) {
                    Text(if (inEditMode) "Listo" else "Editar")
                }
            }
        }

        if (allergies.isEmpty()) {
            Text(
                "Todavía no has marcado ninguna alergia",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val ingredients = allergies.filter { it.tipo == AllergyType.PRINCIPIO_ACTIVO }
            val families = allergies.filter { it.tipo == AllergyType.FAMILIA }
            AllergyGroup("Componentes activos · ${ingredients.size}", ingredients, inEditMode, armedId, { armedId = it }, viewModel::delete)
            AllergyGroup("Familias ATC · ${families.size}", families, inEditMode, armedId, { armedId = it }, viewModel::delete)
        }

        if (!inEditMode) {
            GelButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Añadir alergia")
            }
        }
    }
}

@Composable
private fun AllergyGroup(
    title: String,
    allergies: List<Allergy>,
    editing: Boolean,
    armedId: String?,
    onArm: (String?) -> Unit,
    onDelete: (Allergy) -> Unit,
) {
    Column {
        GroupHeader(title)
        if (allergies.isEmpty()) return@Column
        GroupedList {
            allergies.forEachIndexed { index, allergy ->
                GroupedRow(divider = index > 0, chevron = false) {
                    if (editing) {
                        IconButton(onClick = { onArm(if (armedId == allergy.id) null else allergy.id) }) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = "Quitar alergia",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            MedicationNames.pretty(allergy.principioActivoNombre ?: allergy.atcNombre.orEmpty()),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        allergy.atcCodigo?.let { codigo ->
                            Text(
                                codigo,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = DmMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (editing && armedId == allergy.id) {
                        GelButton(
                            onClick = {
                                onArm(null)
                                onDelete(allergy)
                            },
                            tone = GelTone.CORAL,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text("Eliminar")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hoja para añadir: un solo buscador. El tipo de alergia no se elige, lo dice el resultado
 * (componente activo o familia ATC).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAllergySheet(viewModel: AllergiesViewModel, allergies: List<Allergy>, onDismiss: () -> Unit) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val addableResultCount by viewModel.addableResultCount.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Añadir alergia", style = MaterialTheme.typography.titleLarge)
            CapsuleSearchField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                placeholder = "Ej: Ibuprofeno, penicilina, J01C",
                trailingIcon = {
                    SearchHelpButton(
                        title = "¿Qué puedo buscar aquí?",
                        description = "Un componente activo, la sustancia que hace efecto dentro del " +
                            "medicamento (Xazal contiene Levocetirizina), o una familia ATC, un grupo " +
                            "de medicamentos con efectos similares. Una alergia de familia avisa sobre " +
                            "todo el grupo. También puedes buscar por código ATC.",
                        examples = listOf("Ibuprofeno", "Amoxicilina", "penicilina", "M01A", "J01C"),
                    )
                },
            )

            if (addableResultCount > 0) {
                TextButton(onClick = viewModel::addAllSearchResults, modifier = Modifier.align(Alignment.End)) {
                    Text("Añadir todas ($addableResultCount)")
                }
            }

            when (val state = searchState) {
                is CatalogSearchUiState.Idle -> Unit
                is CatalogSearchUiState.Loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
                is CatalogSearchUiState.Empty -> CenteredText("No se han encontrado resultados")
                is CatalogSearchUiState.Error -> CenteredText(state.message)
                is CatalogSearchUiState.Results -> {
                    if (state.isFromCache) {
                        Text(
                            "Sin acceso al catálogo · mostrando resultados guardados",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.ingredients.isNotEmpty()) {
                        Column {
                            GroupHeader("Componentes activos")
                            GroupedList {
                                state.ingredients.forEachIndexed { index, item ->
                                    ResultRow(
                                        name = MedicationNames.pretty(item.nombre),
                                        code = null,
                                        divider = index > 0,
                                        alreadyAdded = allergies.isIngredientMarked(item),
                                        onAdd = { viewModel.addIngredientAllergy(item) },
                                    )
                                }
                            }
                        }
                    }
                    if (state.families.isNotEmpty()) {
                        Column {
                            GroupHeader("Familias ATC")
                            GroupedList {
                                state.families.forEachIndexed { index, item ->
                                    ResultRow(
                                        name = MedicationNames.pretty(item.nombre),
                                        code = item.codigo,
                                        divider = index > 0,
                                        alreadyAdded = allergies.isFamilyMarked(item),
                                        onAdd = { viewModel.addFamilyAllergy(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun List<Allergy>.isIngredientMarked(item: PrincipioActivo) =
    any { it.tipo == AllergyType.PRINCIPIO_ACTIVO && it.principioActivoId == item.id }

private fun List<Allergy>.isFamilyMarked(item: AtcCode) =
    any { it.tipo == AllergyType.FAMILIA && it.atcCodigo == item.codigo }

@Composable
private fun ResultRow(name: String, code: String?, divider: Boolean, alreadyAdded: Boolean, onAdd: () -> Unit) {
    GroupedRow(
        divider = divider,
        chevron = false,
        modifier = Modifier.clickable(enabled = !alreadyAdded, onClick = onAdd),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            code?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = DmMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (alreadyAdded) {
            Icon(Icons.Default.Check, contentDescription = "Ya añadida", tint = addedTint)
        } else {
            Icon(Icons.Default.Add, contentDescription = "Añadir alergia")
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Text(text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
}
