package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.tournament.CreateTournamentInput
import com.hoggamers.rankforge.domain.tournament.CreateTournamentResult
import com.hoggamers.rankforge.domain.tournament.CreateTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentField
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TournamentCreationViewModel @Inject constructor(
    private val createTournament: CreateTournamentUseCase,
    private val uploadTournament: TournamentCloudUploadAction,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TournamentCreationUiState())
    val uiState: StateFlow<TournamentCreationUiState> = _uiState.asStateFlow()

    private var pendingCloudTournamentId: String? = null

    fun onTournamentNameChanged(value: String) {
        if (_uiState.value.cloudConfirmationPending) return
        _uiState.update {
            it.copy(
                tournamentName = value,
                validationErrors = it.validationErrors - TournamentField.NAME,
                submissionError = null,
            )
        }
    }

    fun onTournamentDateChanged(value: java.time.LocalDate) {
        if (_uiState.value.cloudConfirmationPending) return
        _uiState.update {
            it.copy(
                tournamentDate = value,
                validationErrors = it.validationErrors - TournamentField.DATE,
                submissionError = null,
            )
        }
    }

    fun onOrganizerNameChanged(value: String) {
        if (_uiState.value.cloudConfirmationPending) return
        _uiState.update {
            it.copy(
                organizerName = value,
                validationErrors = it.validationErrors - TournamentField.ORGANIZER_NAME,
                submissionError = null,
            )
        }
    }

    fun onOrganizerContactNumberChanged(value: String) {
        if (_uiState.value.cloudConfirmationPending) return
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

        val existingTournamentId = pendingCloudTournamentId
        val currentState = _uiState.value
        _uiState.update { it.copy(isSubmitting = true, submissionError = null) }
        viewModelScope.launch {
            try {
                if (existingTournamentId != null) {
                    confirmCloudTournament(existingTournamentId)
                    return@launch
                }

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

                    is CreateTournamentResult.Created -> {
                        pendingCloudTournamentId = result.tournament.id
                        confirmCloudTournament(result.tournament.id)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        cloudConfirmationPending = pendingCloudTournamentId != null,
                        submissionError = TournamentCreationSubmissionError.UNKNOWN,
                    )
                }
            }
        }
    }

    private suspend fun confirmCloudTournament(tournamentId: String) {
        val uploadResult = try {
            uploadTournament(tournamentId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    cloudConfirmationPending = true,
                    submissionError = TournamentCreationSubmissionError.UNKNOWN,
                )
            }
            return
        }

        if (uploadResult.primaryResult is TournamentCloudUploadResult.Success) {
            pendingCloudTournamentId = null
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    cloudConfirmationPending = false,
                    submissionError = null,
                    navigation = TournamentCreationNavigation.Created(tournamentId),
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isSubmitting = false,
                cloudConfirmationPending = true,
                submissionError = if (uploadResult.queueRecordingResult == QueueRecordingResult.RECORDED) {
                    TournamentCreationSubmissionError.CLOUD_SYNC_PENDING
                } else {
                    TournamentCreationSubmissionError.UNKNOWN
                },
            )
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
