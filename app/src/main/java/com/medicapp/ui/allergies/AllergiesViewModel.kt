package com.medicapp.ui.allergies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.remote.CimaUnavailableException
import com.medicapp.data.repository.AllergyRepository
import com.medicapp.data.repository.MedicationCatalogRepository
import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import com.medicapp.model.AtcCode
import com.medicapp.model.PrincipioActivo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface CatalogSearchUiState {
    data object Idle : CatalogSearchUiState
    data object Loading : CatalogSearchUiState
    data object Empty : CatalogSearchUiState
    data class Error(val message: String) : CatalogSearchUiState

    /**
     * Una sola búsqueda en las dos maestras: el usuario no elige si busca un componente o una
     * familia, el tipo de alergia lo dice el propio resultado.
     */
    data class Results(
        val ingredients: List<PrincipioActivo>,
        val families: List<AtcCode>,
        val isFromCache: Boolean = false,
    ) : CatalogSearchUiState
}

class AllergiesViewModel(
    private val allergyRepository: AllergyRepository,
    private val catalogRepository: MedicationCatalogRepository,
) : ViewModel() {

    val allergies: StateFlow<List<Allergy>> = allergyRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val searchState: StateFlow<CatalogSearchUiState> =
        _query.debounce(350)
            .distinctUntilChanged()
            .flatMapLatest { text ->
                flow {
                    if (text.isBlank()) {
                        emit(CatalogSearchUiState.Idle)
                        return@flow
                    }
                    emit(CatalogSearchUiState.Loading)
                    try {
                        val (ingredients, families) = coroutineScope {
                            val ingredients = async { catalogRepository.searchPrincipiosActivos(text) }
                            val families = async { catalogRepository.searchAtcCodes(text) }
                            ingredients.await() to families.await()
                        }
                        emit(
                            if (ingredients.items.isEmpty() && families.items.isEmpty()) {
                                CatalogSearchUiState.Empty
                            } else {
                                CatalogSearchUiState.Results(
                                    ingredients = ingredients.items,
                                    families = families.items,
                                    isFromCache = ingredients.isFromCache || families.isFromCache,
                                )
                            },
                        )
                    } catch (e: CimaUnavailableException) {
                        emit(
                            CatalogSearchUiState.Error(
                                "El catálogo de medicamentos no está disponible ahora mismo. " +
                                    "Inténtalo de nuevo en unos minutos.",
                            ),
                        )
                    } catch (e: IOException) {
                        emit(CatalogSearchUiState.Error("No se pudo conectar. Comprueba tu conexión a internet."))
                    } catch (e: Exception) {
                        emit(CatalogSearchUiState.Error("Ha ocurrido un error inesperado."))
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CatalogSearchUiState.Idle)

    /** Cuántos resultados de la búsqueda actual todavía no están marcados como alergia. */
    val addableResultCount: StateFlow<Int> = combine(searchState, allergies) { state, _ ->
        if (state is CatalogSearchUiState.Results) {
            state.ingredients.count { !isIngredientAdded(it) } + state.families.count { !isFamilyAdded(it) }
        } else {
            0
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun addIngredientAllergy(principioActivo: PrincipioActivo) {
        if (isIngredientAdded(principioActivo)) return
        viewModelScope.launch { allergyRepository.addPrincipioActivo(principioActivo.id, principioActivo.nombre) }
    }

    fun addFamilyAllergy(atc: AtcCode) {
        if (isFamilyAdded(atc)) return
        viewModelScope.launch { allergyRepository.addFamilia(atc.codigo, atc.nombre) }
    }

    /**
     * Todas de una vez: una escritura por tipo para la lista entera, no una por resultado. Si un
     * tipo no tiene nada nuevo, no se escribe.
     */
    fun addAllSearchResults() {
        val state = searchState.value as? CatalogSearchUiState.Results ?: return
        val ingredients = state.ingredients.filterNot { isIngredientAdded(it) }
        val families = state.families.filterNot { isFamilyAdded(it) }
        viewModelScope.launch {
            if (ingredients.isNotEmpty()) allergyRepository.addPrincipiosActivos(ingredients)
            if (families.isNotEmpty()) allergyRepository.addFamilias(families)
        }
    }

    private fun isIngredientAdded(principioActivo: PrincipioActivo) = allergies.value.any {
        it.tipo == AllergyType.PRINCIPIO_ACTIVO && it.principioActivoId == principioActivo.id
    }

    private fun isFamilyAdded(atc: AtcCode) = allergies.value.any {
        it.tipo == AllergyType.FAMILIA && it.atcCodigo == atc.codigo
    }

    fun delete(allergy: Allergy) {
        viewModelScope.launch { allergyRepository.delete(allergy.id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicApp
                AllergiesViewModel(app.container.allergyRepository, app.container.medicationCatalogRepository)
            }
        }
    }
}
