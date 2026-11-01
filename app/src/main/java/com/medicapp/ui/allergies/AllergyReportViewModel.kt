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
import com.medicapp.model.PatientProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AllergyReportUiState(
    val language: ReportLanguage = ReportLanguage.ES,
    val profile: PatientProfile? = null,
    val groups: AllergyGroups = AllergyGroups(emptyList(), emptyList()),
)

class AllergyReportViewModel(
    allergyRepository: AllergyRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val language = MutableStateFlow(ReportLanguage.ES)

    val uiState: StateFlow<AllergyReportUiState> =
        combine(language, profileRepository.observe(), allergyRepository.getAll()) { language, profile, allergies ->
            AllergyReportUiState(language, profile, AllergyGroups.of(allergies))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AllergyReportUiState())

    fun onLanguageSelected(selected: ReportLanguage) {
        language.value = selected
    }

    fun saveProfile(nombre: String, fechaNacimiento: LocalDate?) {
        val trimmed = nombre.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { profileRepository.save(PatientProfile(trimmed, fechaNacimiento)) }
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
