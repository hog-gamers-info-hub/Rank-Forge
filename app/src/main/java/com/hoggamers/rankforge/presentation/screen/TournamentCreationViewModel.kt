package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.CreateTournamentInput
import com.hoggamers.rankforge.domain.tournament.CreateTournamentResult
import com.hoggamers.rankforge.domain.tournament.CreateTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.TournamentField
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TournamentCreationViewModel @Inject constructor(
    private val createTournament: CreateTournamentUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TournamentCreationUiState())
    val uiState: StateFlow<TournamentCreationUiState> = _uiState.asStateFlow()

    fun onTournamentNameChanged(value: String) {
        _uiState.update {
            it.copy(
                tournamentName = value,
                validationErrors = it.validationErrors - TournamentField.NAME,
                submissionError = null,
            )
        }
    }

    fun onTournamentDateChanged(value: java.time.LocalDate) {
        _uiState.update {
            it.copy(
                tournamentDate = value,
                validationErrors = it.validationErrors - TournamentField.DATE,
                submissionError = null,
            )
        }
    }

    fun onOrganizerNameChanged(value: String) {
        _uiState.update {
            it.copy(
                organizerName = value,
                validationErrors = it.validationErrors - TournamentField.ORGANIZER_NAME,
                submissionError = null,
            )
        }
    }

    fun onOrganizerContactNumberChanged(value: String) {
        _uiState.update {
            it.copy(
                organizerContactNumber = value,
                validationErrors = it.validationErrors - TournamentField.ORGANIZER_CONTACT_NUMBER,
                submissionError = null,
            )
        }
    }

    fun submit() {
        if (_uiState.value.isSubmitting || _uiState.value.navigation != null) return

        val currentState = _uiState.value
        _uiState.update { it.copy(isSubmitting = true, submissionError = null) }
        viewModelScope.launch {
            try {
                when (
                    val result = createTournament(
                        CreateTournamentInput(
                            name = currentState.tournamentName,
                            date = currentState.tournamentDate,
                            organizerName = currentState.organizerName,
                            organizerContactNumber = currentState.organizerContactNumber,
                        ),
                    )
                ) {
                    is CreateTournamentResult.Invalid -> _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            validationErrors = result.errors,
                        )
                    }

                    is CreateTournamentResult.Created -> _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            navigation = TournamentCreationNavigation.Created(result.tournament.id),
                        )
                    }
                }
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submissionError = TournamentCreationSubmissionError.UNKNOWN,
                    )
                }
            }
        }
    }

    fun onBackPressed() {
        if (_uiState.value.isDirty) {
            _uiState.update { it.copy(showDiscardDialog = true) }
        } else {
            _uiState.update { it.copy(navigation = TournamentCreationNavigation.Back) }
        }
    }

    fun keepEditing() {
        _uiState.update { it.copy(showDiscardDialog = false) }
    }

    fun discardChanges() {
        _uiState.update {
            it.copy(
                showDiscardDialog = false,
                navigation = TournamentCreationNavigation.Back,
            )
        }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigation = null) }
    }
}
