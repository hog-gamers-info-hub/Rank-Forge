package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.tournament.CreateMatchInput
import com.hoggamers.rankforge.domain.tournament.CreateMatchResult
import com.hoggamers.rankforge.domain.tournament.CreateMatchUseCase
import com.hoggamers.rankforge.domain.tournament.MatchField
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MatchCreationViewModel @Inject constructor(
    private val createMatch: CreateMatchUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchCreationUiState())
    val uiState: StateFlow<MatchCreationUiState> = _uiState.asStateFlow()

    fun load(tournamentId: String) {
        if (_uiState.value.tournamentId == tournamentId) return
        _uiState.update { MatchCreationUiState(tournamentId = tournamentId) }
    }

    fun onMatchNumberChanged(value: String) {
        _uiState.update {
            it.copy(
                matchNumber = value,
                validationErrors = it.validationErrors - MatchField.MATCH_NUMBER,
                submissionError = null,
            )
        }
    }

    fun onMatchDateChanged(value: java.time.LocalDate) {
        _uiState.update {
            it.copy(
                matchDate = value,
                validationErrors = it.validationErrors - MatchField.DATE,
                submissionError = null,
            )
        }
    }

    fun onMapNameChanged(value: String) {
        _uiState.update {
            it.copy(
                mapName = value,
                validationErrors = it.validationErrors - MatchField.MAP,
                submissionError = null,
            )
        }
    }

    fun submit() {
        val currentState = _uiState.value
        val tournamentId = currentState.tournamentId ?: return
        if (currentState.isSubmitting || currentState.navigation != null) return

        _uiState.update { it.copy(isSubmitting = true, submissionError = null) }
        viewModelScope.launch {
            try {
                when (
                    val result = createMatch(
                        CreateMatchInput(
                            tournamentId = tournamentId,
                            matchNumber = currentState.matchNumber,
                            date = currentState.matchDate,
                            mapName = currentState.mapName,
                        ),
                    )
                ) {
                    is CreateMatchResult.Invalid -> _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            validationErrors = result.errors,
                        )
                    }

                    is CreateMatchResult.Created -> _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            navigation = MatchCreationNavigation.CREATED,
                        )
                    }
                }
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submissionError = MatchCreationSubmissionError.UNKNOWN,
                    )
                }
            }
        }
    }

    fun onBackPressed() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(navigation = MatchCreationNavigation.BACK) }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigation = null) }
    }
}

