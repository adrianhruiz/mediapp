package com.medicapp.ui.allergies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.repository.AllergyRepository
import com.medicapp.data.repository.ProfileRepository
import com.medicapp.domain.AllergyGroups
import com.medicapp.model.Allergy
import com.medicapp.model.PatientProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AllergyReportUiState(
    val language: ReportLanguage = ReportLanguage.ES,
    val profile: PatientProfile? = null,
    val groups: AllergyGroups = AllergyGroups(emptyList(), emptyList()),
    /** Hasta que llegan los datos no se pinta nada: un "sin alergias" provisional engañaría al médico. */
    val isLoaded: Boolean = false,
    val profileSaveFailed: Boolean = false,
    /** La lectura de alergias falló: el informe no se pinta, porque saldría como "sin alergias". */
    val allergiesLoadFailed: Boolean = false,
)

class AllergyReportViewModel(
    private val allergyRepository: AllergyRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val language = MutableStateFlow(ReportLanguage.ES)
    private val profileSaveFailed = MutableStateFlow(false)

    val uiState: StateFlow<AllergyReportUiState> =
        combine(
            language,
            profileRepository.observe(),
            allergyRepository.getAllOrThrow()
                .map { Result.success(it) }
                .catch { emit(Result.failure(it)) },
            profileSaveFailed,
        ) { language, profile, allergies, saveFailed ->
            AllergyReportUiState(
                language = language,
                profile = profile,
                groups = AllergyGroups.of(allergies.getOrDefault(emptyList())),
                isLoaded = true,
                profileSaveFailed = saveFailed,
                allergiesLoadFailed = allergies.isFailure,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AllergyReportUiState())

    fun onLanguageSelected(selected: ReportLanguage) {
        language.value = selected
    }

    fun saveProfile(nombre: String, fechaNacimiento: LocalDate?) {
        val trimmed = nombre.trim()
        if (trimmed.isEmpty()) return
        profileSaveFailed.value = false
        viewModelScope.launch {
            runCatching { profileRepository.save(PatientProfile(trimmed, fechaNacimiento)) }
                .onFailure { profileSaveFailed.value = true }
        }
    }

    /**
     * Nombre en inglés escrito a mano para una alergia que se quedó en español; en blanco lo
     * borra. Una traducción oficial no se toca: es la de la OMS para ese código exacto.
     */
    fun setManualEnglishName(allergy: Allergy, nombreIngles: String) {
        if (allergy.nombreIngles != null && !allergy.nombreInglesManual) return
        val trimmed = nombreIngles.trim().takeIf { it.isNotEmpty() }
        viewModelScope.launch { runCatching { allergyRepository.setEnglishName(allergy.id, trimmed, manual = true) } }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicApp
                AllergyReportViewModel(app.container.allergyRepository, app.container.profileRepository)
            }
        }
    }
}
