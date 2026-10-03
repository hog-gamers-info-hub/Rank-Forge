package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import com.hoggamers.rankforge.domain.tournament.CreateTournamentInput
import com.hoggamers.rankforge.domain.tournament.CreateTournamentResult
import com.hoggamers.rankforge.domain.tournament.CreateTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.CheckTournamentQuotaUseCase
import com.hoggamers.rankforge.domain.tournament.LocalDeletionRepository
import com.hoggamers.rankforge.domain.tournament.LocalDeletionResult
import com.hoggamers.rankforge.domain.tournament.DeletionIntent
import com.hoggamers.rankforge.domain.tournament.DeletionIntentPhase
import com.hoggamers.rankforge.domain.tournament.DeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.DeletionTargetType
import com.hoggamers.rankforge.domain.tournament.NoOpDeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentField
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.defaultGroupPairings
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentQuotaResult
import com.hoggamers.rankforge.domain.tournament.validateCreateTournamentInput
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
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
    private val clock: Clock,
    private val checkTournamentQuota: CheckTournamentQuotaUseCase,
    private val uploadTournament: TournamentCloudUploadAction,
    private val localDeletionRepository: LocalDeletionRepository,
    private val deletionIntentRepository: DeletionIntentRepository = NoOpDeletionIntentRepository,
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

    fun onStageNameChanged(value: String) {
        _uiState.update {
            it.copy(
                stageName = value,
                validationErrors = it.validationErrors - TournamentField.STAGE_NAME,
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

    fun onGroupRotationChanged(enabled: Boolean) {
        _uiState.update { current ->
            if (enabled) {
                current.copy(
                    format = TournamentFormat.GROUP_ROTATION,
                    groupCount = 3,
                    selectedGroupPairings = defaultGroupPairings(3),
                    validationErrors = current.validationErrors - TournamentField.GROUP_CONFIGURATION,
                    submissionError = null,
                )
            } else {
                current.copy(
                    format = TournamentFormat.STANDARD,
                    groupCount = null,
                    selectedGroupPairings = emptyList(),
                    validationErrors = current.validationErrors - TournamentField.GROUP_CONFIGURATION,
                    submissionError = null,
                )
            }
        }
    }

    fun onGroupCountChanged(groupCount: Int) {
        if (groupCount !in setOf(3, 4)) return
        _uiState.update { current ->
            if (current.format != TournamentFormat.GROUP_ROTATION) return@update current
            current.copy(
                groupCount = groupCount,
                selectedGroupPairings = defaultGroupPairings(groupCount),
                validationErrors = current.validationErrors - TournamentField.GROUP_CONFIGURATION,
                submissionError = null,
            )
        }
    }

    fun onGroupPairingToggled(pairing: GroupPairing) {
        _uiState.update { current ->
            if (current.format != TournamentFormat.GROUP_ROTATION) return@update current
            val pairings = if (pairing in current.selectedGroupPairings) {
                current.selectedGroupPairings - pairing
            } else {
                current.selectedGroupPairings + pairing
            }
            current.copy(
                selectedGroupPairings = pairings,
                validationErrors = current.validationErrors - TournamentField.GROUP_CONFIGURATION,
                submissionError = null,
            )
        }
    }

    fun submit() {
        if (_uiState.value.isSubmitting || _uiState.value.navigation != null) return

        val currentState = _uiState.value
        val input = CreateTournamentInput(
            name = currentState.tournamentName,
            stageName = currentState.stageName,
            organizerContactNumber = currentState.organizerContactNumber,
            format = currentState.format,
            groupCount = currentState.groupCount,
            selectedGroupPairings = currentState.selectedGroupPairings,
        )
        val validationErrors = validateCreateTournamentInput(input)
        if (validationErrors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    validationErrors = validationErrors,
                    submissionError = null,
                )
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, submissionError = null) }
        viewModelScope.launch {
            try {
                when (checkTournamentQuota()) {
                    is TournamentQuotaResult.Allowed -> createAndUpload(input)
                    is TournamentQuotaResult.LimitReached -> _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submissionError = TournamentCreationSubmissionError.TOURNAMENT_LIMIT_REACHED,
                        )
                    }
                    TournamentQuotaResult.AuthenticationRequired -> _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submissionError = TournamentCreationSubmissionError.AUTHENTICATION_REQUIRED,
                        )
                    }
                    TournamentQuotaResult.NetworkFailure,
                    TournamentQuotaResult.UnknownFailure,
                    -> _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submissionError = TournamentCreationSubmissionError.QUOTA_CHECK_FAILED,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
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

    private suspend fun createAndUpload(input: CreateTournamentInput) {
        when (val result = createTournament(input)) {
            CreateTournamentResult.AuthenticationRequired -> _uiState.update {
                it.copy(
                    isSubmitting = false,
                    submissionError = TournamentCreationSubmissionError.AUTHENTICATION_REQUIRED,
                )
            }

            is CreateTournamentResult.Invalid -> _uiState.update {
                it.copy(
                    isSubmitting = false,
                    validationErrors = result.errors,
                )
            }

            is CreateTournamentResult.Created -> {
                val uploadResult = try {
                    uploadTournament(result.tournament.id)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submissionError = TournamentCreationSubmissionError.UNKNOWN,
                        )
                    }
                    return
                }
                if (uploadResult.primaryResult == TournamentCloudUploadResult.TournamentLimitReached) {
                    try {
                        result.tournament.ownerUserId
                            ?.takeIf { it.isNotBlank() }
                            ?.let { ownerUserId ->
                                val claimed = deletionIntentRepository.startIfAbsent(
                                    DeletionIntent(
                                        targetType = DeletionTargetType.TOURNAMENT,
                                        targetId = result.tournament.id,
                                        tournamentId = result.tournament.id,
                                        ownerUserId = ownerUserId,
                                        phase = DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING,
                                        updatedAtEpochMillis = clock.millis(),
                                    ),
                                )
                                if (!claimed) return@let
                                val localResult = localDeletionRepository.deleteTournamentLocallyByOwner(
                                    result.tournament.id,
                                    ownerUserId,
                                )
                                if (localResult == LocalDeletionResult.Deleted || localResult == LocalDeletionResult.NotFound) {
                                    deletionIntentRepository.clearByTargetAndOwner(
                                        DeletionTargetType.TOURNAMENT,
                                        result.tournament.id,
                                        ownerUserId,
                                    )
                                }
                            }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        // The limit result remains authoritative even if local cleanup reports a failure.
                    }
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submissionError = TournamentCreationSubmissionError.TOURNAMENT_LIMIT_REACHED,
                        )
                    }
                } else if (
                    uploadResult.primaryResult is TournamentCloudUploadResult.Success ||
                    (
                        uploadResult.primaryResult == TournamentCloudUploadResult.NetworkFailure &&
                            uploadResult.queueRecordingResult == QueueRecordingResult.RECORDED
                        )
                ) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            navigation = TournamentCreationNavigation.Created(result.tournament.id),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submissionError = uploadResult.primaryResult.toCreationSubmissionError(),
                        )
                    }
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

private fun TournamentCloudUploadResult.toCreationSubmissionError(): TournamentCreationSubmissionError =
    when (this) {
        TournamentCloudUploadResult.AuthenticationRequired ->
            TournamentCreationSubmissionError.AUTHENTICATION_REQUIRED
        TournamentCloudUploadResult.TournamentLimitReached ->
            TournamentCreationSubmissionError.TOURNAMENT_LIMIT_REACHED
        TournamentCloudUploadResult.ValidationFailure,
        TournamentCloudUploadResult.AuthorizationFailure,
        TournamentCloudUploadResult.NetworkFailure,
        is TournamentCloudUploadResult.Conflict,
        is TournamentCloudUploadResult.PartialFailure,
        is TournamentCloudUploadResult.Success,
        -> TournamentCreationSubmissionError.UNKNOWN
    }
