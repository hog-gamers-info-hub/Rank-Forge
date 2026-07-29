package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.data.cloud.NoOpScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.ScreenshotStorageUploadFailure
import com.hoggamers.rankforge.data.cloud.ScreenshotStorageUploadResult
import com.hoggamers.rankforge.data.cloud.ScreenshotStorageUploader
import com.hoggamers.rankforge.domain.tournament.MatchResultRowInput
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchInput
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchResult
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MatchReviewViewModel @Inject constructor(
    private val observeMatches: ObserveMatchesUseCase,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeRoster: ObserveRosterByTournamentUseCase,
    private val observeDraftValues: ObserveMatchDraftValuesUseCase,
    private val validateMatchResult: ValidateMatchResultUseCase,
    private val finalizeMatch: FinalizeMatchUseCase,
    private val imageCandidateValidator: ImageCandidateValidator,
    private val screenshotDuplicateDetector: ScreenshotDuplicateDetector,
    private val localImagePreserver: LocalImagePreserver,
    private val screenshotStorageUploader: ScreenshotStorageUploader = NoOpScreenshotStorageUploader(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchReviewUiState())
    val uiState: StateFlow<MatchReviewUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var validationJob: Job? = null
    private var duplicateDetectionJob: Job? = null
    private var preservationJob: Job? = null
    private var uploadJob: Job? = null
    private var loadedMatchKey: String? = null

    fun load(tournamentId: String, matchId: String) {
        val matchKey = "$tournamentId:$matchId"
        if (loadedMatchKey == matchKey) return
        loadedMatchKey = matchKey
        loadJob?.cancel()
        validationJob?.cancel()
        duplicateDetectionJob?.cancel()
        preservationJob?.cancel()
        uploadJob?.cancel()
        _uiState.update {
            MatchReviewUiState(
                isLoading = true,
                tournamentId = tournamentId,
                matchId = matchId,
            )
        }
        loadJob = viewModelScope.launch {
            combine(
                observeMatches(tournamentId),
                observeTournamentSlots(tournamentId),
                observeRoster(tournamentId),
                observeDraftValues(tournamentId, matchId),
            ) { matches, slots, rosters, draftValues ->
                val match = matches.firstOrNull { it.id == matchId }
                if (match == null) {
                    MatchReviewUiState(
                        isLoading = false,
                        isAvailable = false,
                        tournamentId = tournamentId,
                        matchId = matchId,
                    )
                } else {
                    val fallbackSlots = TeamSlot.fixedSlotsForTournament(tournamentId)
                        .associateBy { it.slotNumber }
                    val slotsByNumber = slots.associateBy { it.slotNumber }
                    val placementsBySlot = match.placements.associateBy { it.teamSlotNumber }
                    val killsBySlot = match.kills.associateBy { it.teamSlotNumber }
                    val rows = TeamSlot.SLOT_NUMBERS.map { teamSlotNumber ->
                        val slot = slotsByNumber[teamSlotNumber] ?: fallbackSlots.getValue(teamSlotNumber)
                        val draft = draftValues[teamSlotNumber]
                            .takeIf { match.status == MatchStatus.DRAFT }
                        MatchReviewRowUiState(
                            teamSlotNumber = teamSlotNumber,
                            teamName = slot.teamName,
                            playerNames = rosters[teamSlotNumber].orEmpty().map { it.displayName },
                            placementInput = draft?.placementInput
                                ?: placementsBySlot[teamSlotNumber]?.position?.toString().orEmpty(),
                            killsInput = draft?.killsInput
                                ?: killsBySlot[teamSlotNumber]?.kills?.toString().orEmpty(),
                        )
                    }
                    val validation = if (match.status == MatchStatus.FINALIZED || draftValues.isEmpty()) {
                        validateMatchResult(match)
                    } else {
                        validateMatchResult(
                            rows.map { row ->
                                MatchResultRowInput(
                                    teamSlotNumber = row.teamSlotNumber,
                                    placement = row.placementInput,
                                    kills = row.killsInput,
                                )
                            },
                        )
                    }
                    MatchReviewUiState(
                        isLoading = false,
                        isAvailable = true,
                        tournamentId = tournamentId,
                        matchId = matchId,
                        matchNumber = match.matchNumber,
                        status = match.status,
                        correctionHistory = match.correctionHistory,
                        rows = rows.map { row ->
                            row.copy(validationErrors = validation.errorsByTeamSlot[row.teamSlotNumber].orEmpty())
                        },
                        validationErrors = validation.errorsByTeamSlot,
                    )
                }
            }.collect { state ->
                _uiState.update { current ->
                    state.copy(
                        navigation = current.navigation,
                        isFinalizing = current.isFinalizing,
                        finalizationError = current.finalizationError,
                        selectedScreenshotUri = current.selectedScreenshotUri,
                        isPhotoPickerLaunchPending = current.isPhotoPickerLaunchPending,
                        isPhotoPickerRequestActive = current.isPhotoPickerRequestActive,
                        photoPickerError = current.photoPickerError,
                        isScreenshotValidationInProgress = current.isScreenshotValidationInProgress,
                        isSelectedScreenshotValidated = current.isSelectedScreenshotValidated,
                        imageValidationError = current.imageValidationError,
                        linkedScreenshotUri = current.linkedScreenshotUri,
                        linkedScreenshotFingerprint = current.linkedScreenshotFingerprint,
                        screenshotLinkError = current.screenshotLinkError,
                        isScreenshotDuplicateDetectionInProgress = current.isScreenshotDuplicateDetectionInProgress,
                        screenshotDuplicateError = current.screenshotDuplicateError,
                        screenshotDuplicateInfo = current.screenshotDuplicateInfo,
                        isScreenshotPreservationInProgress = current.isScreenshotPreservationInProgress,
                        isScreenshotLocallyPreserved = current.isScreenshotLocallyPreserved,
                        preservedScreenshotPath = current.preservedScreenshotPath,
                        screenshotPreservationError = current.screenshotPreservationError,
                        isScreenshotUploadInProgress = current.isScreenshotUploadInProgress,
                        isScreenshotUploaded = current.isScreenshotUploaded,
                        screenshotUploadObjectPath = current.screenshotUploadObjectPath,
                        screenshotUploadError = current.screenshotUploadError,
                    )
                }
            }
        }
    }

    fun openPlacements() {
        if (_uiState.value.isEditable) {
            _uiState.update { it.copy(navigation = MatchReviewNavigation.PLACEMENTS) }
        }
    }

    fun openKills() {
        if (_uiState.value.isEditable) {
            _uiState.update { it.copy(navigation = MatchReviewNavigation.KILLS) }
        }
    }

    fun openCorrection() {
        if (_uiState.value.status == MatchStatus.FINALIZED) {
            _uiState.update { it.copy(navigation = MatchReviewNavigation.CORRECTION) }
        }
    }

    fun onBackToDetails() {
        if (_uiState.value.isAvailable) {
            _uiState.update { it.copy(navigation = MatchReviewNavigation.DETAILS) }
        }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigation = null) }
    }

    fun requestPhotoPicker() {
        val current = _uiState.value
        if (!current.isAvailable || current.isPhotoPickerRequestActive) return
        _uiState.update {
            it.copy(
                isPhotoPickerLaunchPending = true,
                isPhotoPickerRequestActive = true,
                photoPickerError = null,
            )
        }
    }

    fun onPhotoPickerLaunchHandled() {
        _uiState.update { it.copy(isPhotoPickerLaunchPending = false) }
    }

    fun onPhotoPickerResult(selectedUri: String?) {
        if (selectedUri == null) {
            _uiState.update {
                it.copy(
                    isPhotoPickerLaunchPending = false,
                    isPhotoPickerRequestActive = false,
                )
            }
            return
        }

        validationJob?.cancel()
        duplicateDetectionJob?.cancel()
        preservationJob?.cancel()
        uploadJob?.cancel()
        if (selectedUri.isBlank()) {
            _uiState.update {
                it.copy(
                    selectedScreenshotUri = null,
                    isPhotoPickerLaunchPending = false,
                    isPhotoPickerRequestActive = false,
                    photoPickerError = null,
                    isScreenshotValidationInProgress = false,
                    isSelectedScreenshotValidated = false,
                    imageValidationError = ImageValidationError.EMPTY_URI,
                    screenshotLinkError = null,
                    isScreenshotDuplicateDetectionInProgress = false,
                    screenshotDuplicateError = null,
                    screenshotDuplicateInfo = null,
                    isScreenshotPreservationInProgress = false,
                    screenshotPreservationError = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                selectedScreenshotUri = selectedUri,
                isPhotoPickerLaunchPending = false,
                isPhotoPickerRequestActive = false,
                photoPickerError = null,
                isScreenshotValidationInProgress = true,
                isSelectedScreenshotValidated = false,
                imageValidationError = null,
                screenshotLinkError = null,
                isScreenshotDuplicateDetectionInProgress = false,
                screenshotDuplicateError = null,
                screenshotDuplicateInfo = null,
                isScreenshotPreservationInProgress = false,
                screenshotPreservationError = null,
                isScreenshotUploadInProgress = false,
                isScreenshotUploaded = false,
                screenshotUploadObjectPath = null,
                screenshotUploadError = null,
            )
        }
        validationJob = viewModelScope.launch {
            val result = runCatching { imageCandidateValidator.validate(selectedUri) }
                .getOrElse {
                    ImageCandidateValidationResult.Invalid(ImageValidationError.DECODE_FAILED)
                }
            _uiState.update { current ->
                if (current.selectedScreenshotUri != selectedUri) {
                    current
                } else {
                    when (result) {
                        ImageCandidateValidationResult.Valid -> current.copy(
                            isScreenshotValidationInProgress = false,
                            isSelectedScreenshotValidated = true,
                            imageValidationError = null,
                        )
                        is ImageCandidateValidationResult.Invalid -> current.copy(
                            isScreenshotValidationInProgress = false,
                            isSelectedScreenshotValidated = false,
                            imageValidationError = result.error,
                        )
                    }
                }
            }
        }
    }

    fun onPhotoPickerLaunchFailed() {
        _uiState.update {
            it.copy(
                isPhotoPickerLaunchPending = false,
                isPhotoPickerRequestActive = false,
                photoPickerError = PhotoPickerError.LAUNCH_FAILED,
            )
        }
    }

    fun linkScreenshot() {
        val current = _uiState.value
        val selectedUri = current.selectedScreenshotUri?.takeIf { it.isNotBlank() }
        val error = when {
            current.tournamentId.isNullOrBlank() -> ScreenshotLinkError.MISSING_TOURNAMENT_ID
            current.matchId.isNullOrBlank() -> ScreenshotLinkError.MISSING_MATCH_ID
            current.status == MatchStatus.FINALIZED -> ScreenshotLinkError.FINALIZED_MATCH
            !current.isSelectedScreenshotValidated || selectedUri == null ->
                ScreenshotLinkError.INVALID_IMAGE
            !current.isAvailable -> ScreenshotLinkError.INVALID_IMAGE
            else -> null
        }
        if (error != null) {
            _uiState.update {
                it.copy(
                    screenshotLinkError = error,
                    screenshotDuplicateError = null,
                    screenshotDuplicateInfo = null,
                )
            }
            return
        }
        if (
            current.isScreenshotDuplicateDetectionInProgress ||
            current.isScreenshotPreservationInProgress ||
            current.isScreenshotUploadInProgress
        ) return
        _uiState.update {
            it.copy(
                screenshotLinkError = null,
                isScreenshotDuplicateDetectionInProgress = true,
                screenshotDuplicateError = null,
                screenshotDuplicateInfo = null,
            )
        }
        val tournamentId = current.tournamentId ?: return
        val matchId = current.matchId ?: return
        val candidateUri = selectedUri ?: return
        duplicateDetectionJob = viewModelScope.launch {
            val result = screenshotDuplicateDetector.link(
                tournamentId = tournamentId,
                matchId = matchId,
                selectedUri = candidateUri,
                currentFingerprint = current.linkedScreenshotFingerprint,
            )
            if (result is ScreenshotDuplicateLinkResult.Linked) {
                val previousFingerprint = current.linkedScreenshotFingerprint
                _uiState.update { latest ->
                    if (
                        latest.tournamentId == tournamentId &&
                        latest.matchId == matchId &&
                        latest.selectedScreenshotUri == selectedUri
                    ) {
                        latest.copy(
                            isScreenshotDuplicateDetectionInProgress = false,
                            isScreenshotPreservationInProgress = true,
                            screenshotDuplicateError = null,
                            screenshotDuplicateInfo = null,
                            screenshotPreservationError = null,
                        )
                    } else {
                        latest
                    }
                }
                val preservationResult = localImagePreserver.preserve(
                    tournamentId = tournamentId,
                    matchId = matchId,
                    selectedUri = candidateUri,
                )
                val preservationFailure = preservationResult as? LocalImagePreservationResult.Failed
                if (preservationFailure != null) {
                    screenshotDuplicateDetector.rollback(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        newFingerprint = result.fingerprint,
                        previousFingerprint = previousFingerprint,
                    )
                }
                val preservedFile = when (preservationResult) {
                    is LocalImagePreservationResult.Preserved -> preservationResult.file
                    is LocalImagePreservationResult.PreservedWithCleanupFailure -> preservationResult.file
                    is LocalImagePreservationResult.Failed -> null
                }
                val cleanupFailed = preservationResult is LocalImagePreservationResult.PreservedWithCleanupFailure
                _uiState.update { latest ->
                    if (
                        latest.tournamentId != tournamentId ||
                        latest.matchId != matchId ||
                        latest.selectedScreenshotUri != selectedUri
                    ) {
                        latest
                    } else if (preservationFailure != null) {
                        latest.copy(
                            isScreenshotDuplicateDetectionInProgress = false,
                            isScreenshotPreservationInProgress = false,
                            screenshotDuplicateError = null,
                            screenshotDuplicateInfo = null,
                            screenshotPreservationError = preservationFailure.error.toUiError(),
                        )
                    } else {
                        latest.copy(
                            linkedScreenshotUri = selectedUri,
                            linkedScreenshotFingerprint = result.fingerprint,
                            screenshotLinkError = null,
                            isScreenshotDuplicateDetectionInProgress = false,
                            isScreenshotPreservationInProgress = false,
                            isScreenshotLocallyPreserved = true,
                            preservedScreenshotPath = requireNotNull(preservedFile).absolutePath,
                            screenshotDuplicateError = null,
                            screenshotDuplicateInfo = null,
                            screenshotPreservationError = if (cleanupFailed) {
                                ScreenshotPreservationError.CLEANUP_FAILED
                            } else {
                                null
                            },
                            isScreenshotUploadInProgress = true,
                            isScreenshotUploaded = false,
                            screenshotUploadObjectPath = null,
                            screenshotUploadError = null,
                        )
                    }
                }
                val uploadIsStillCurrent = _uiState.value.let { latest ->
                    latest.tournamentId == tournamentId &&
                        latest.matchId == matchId &&
                        latest.selectedScreenshotUri == selectedUri &&
                        latest.linkedScreenshotUri == selectedUri
                }
                if (uploadIsStillCurrent) {
                    uploadPreservedScreenshot(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        selectedUri = selectedUri,
                        preservedFile = preservedFile,
                    )
                }
            } else {
                _uiState.update { latest ->
                    if (
                        latest.tournamentId != tournamentId ||
                        latest.matchId != matchId ||
                        latest.selectedScreenshotUri != selectedUri
                    ) {
                        latest
                    } else {
                        when (result) {
                            ScreenshotDuplicateLinkResult.SameMatch -> latest.copy(
                                screenshotLinkError = null,
                                isScreenshotDuplicateDetectionInProgress = false,
                                screenshotDuplicateError = null,
                                screenshotDuplicateInfo = ScreenshotDuplicateInfo.ALREADY_LINKED_TO_THIS_MATCH,
                            )
                            is ScreenshotDuplicateLinkResult.LinkedToOtherMatch -> latest.copy(
                                isScreenshotDuplicateDetectionInProgress = false,
                                screenshotDuplicateError = ScreenshotDuplicateError.LINKED_TO_OTHER_MATCH,
                                screenshotDuplicateInfo = null,
                            )
                            ScreenshotDuplicateLinkResult.FingerprintFailure -> latest.copy(
                                isScreenshotDuplicateDetectionInProgress = false,
                                screenshotDuplicateError = ScreenshotDuplicateError.FINGERPRINT_FAILED,
                                screenshotDuplicateInfo = null,
                            )
                            ScreenshotDuplicateLinkResult.StateConflict -> latest.copy(
                                isScreenshotDuplicateDetectionInProgress = false,
                                screenshotDuplicateError = ScreenshotDuplicateError.STATE_CONFLICT,
                                screenshotDuplicateInfo = null,
                            )
                            is ScreenshotDuplicateLinkResult.Linked -> latest
                        }
                    }
                }
            }
        }
    }

    private suspend fun uploadPreservedScreenshot(
        tournamentId: String,
        matchId: String,
        selectedUri: String,
        preservedFile: java.io.File?,
    ) {
        if (preservedFile == null) return
        val result = try {
            screenshotStorageUploader.upload(tournamentId, matchId, preservedFile)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            ScreenshotStorageUploadResult.Failed(ScreenshotStorageUploadFailure.UPLOAD_FAILED)
        }
        _uiState.update { latest ->
            if (
                latest.tournamentId != tournamentId ||
                latest.matchId != matchId ||
                latest.selectedScreenshotUri != selectedUri ||
                latest.linkedScreenshotUri != selectedUri
            ) {
                latest
            } else {
                when (result) {
                    is ScreenshotStorageUploadResult.Uploaded -> latest.copy(
                        isScreenshotUploadInProgress = false,
                        isScreenshotUploaded = true,
                        screenshotUploadObjectPath = result.objectPath,
                        screenshotUploadError = null,
                    )
                    is ScreenshotStorageUploadResult.Failed -> latest.copy(
                        isScreenshotUploadInProgress = false,
                        isScreenshotUploaded = false,
                        screenshotUploadObjectPath = null,
                        screenshotUploadError = result.error.toUiError(),
                    )
                }
            }
        }
    }

    fun retryScreenshotUpload() {
        val current = _uiState.value
        val tournamentId = current.tournamentId?.takeIf { it.isNotBlank() } ?: return
        val matchId = current.matchId?.takeIf { it.isNotBlank() } ?: return
        val selectedUri = current.linkedScreenshotUri ?: return
        val preservedPath = current.preservedScreenshotPath ?: return
        if (
            current.status == MatchStatus.FINALIZED ||
            current.isScreenshotUploadInProgress ||
            !current.isScreenshotLocallyPreserved
        ) return
        _uiState.update {
            it.copy(
                isScreenshotUploadInProgress = true,
                screenshotUploadError = null,
            )
        }
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            uploadPreservedScreenshot(
                tournamentId = tournamentId,
                matchId = matchId,
                selectedUri = selectedUri,
                preservedFile = java.io.File(preservedPath),
            )
        }
    }

    fun unlinkScreenshot() {
        val current = _uiState.value
        val error = when {
            current.tournamentId.isNullOrBlank() -> ScreenshotLinkError.MISSING_TOURNAMENT_ID
            current.matchId.isNullOrBlank() -> ScreenshotLinkError.MISSING_MATCH_ID
            current.status == MatchStatus.FINALIZED -> ScreenshotLinkError.FINALIZED_MATCH
            !current.isAvailable -> ScreenshotLinkError.INVALID_IMAGE
            else -> null
        }
        if (error != null) {
            _uiState.update {
                it.copy(
                    screenshotLinkError = error,
                    screenshotDuplicateError = null,
                    screenshotDuplicateInfo = null,
                )
            }
            return
        }
        if (
            current.isScreenshotDuplicateDetectionInProgress ||
            current.isScreenshotPreservationInProgress ||
            current.isScreenshotUploadInProgress
        ) return
        val result = screenshotDuplicateDetector.unlink(
            tournamentId = current.tournamentId.orEmpty(),
            matchId = current.matchId.orEmpty(),
            fingerprint = current.linkedScreenshotFingerprint,
        )
        if (result == ScreenshotDuplicateUnlinkResult.StateConflict) {
            _uiState.update {
                it.copy(
                    screenshotDuplicateError = ScreenshotDuplicateError.STATE_CONFLICT,
                    screenshotDuplicateInfo = null,
                )
            }
            return
        }

        val preservedPath = current.preservedScreenshotPath
        _uiState.update {
            it.copy(
                linkedScreenshotUri = null,
                linkedScreenshotFingerprint = null,
                screenshotLinkError = null,
                screenshotDuplicateError = null,
                screenshotDuplicateInfo = null,
                isScreenshotLocallyPreserved = false,
                isScreenshotPreservationInProgress = preservedPath != null,
                screenshotPreservationError = null,
                isScreenshotUploadInProgress = false,
                isScreenshotUploaded = false,
                screenshotUploadObjectPath = null,
                screenshotUploadError = null,
            )
        }
        if (preservedPath == null) return

        val tournamentId = current.tournamentId ?: return
        val matchId = current.matchId ?: return
        preservationJob = viewModelScope.launch {
            when (localImagePreserver.cleanup(tournamentId, matchId)) {
                LocalImageCleanupResult.Cleaned -> _uiState.update { latest ->
                    if (latest.tournamentId == tournamentId && latest.matchId == matchId) {
                        latest.copy(
                            isScreenshotPreservationInProgress = false,
                            preservedScreenshotPath = null,
                            screenshotPreservationError = null,
                        )
                    } else {
                        latest
                    }
                }
                LocalImageCleanupResult.Failed -> _uiState.update { latest ->
                    if (latest.tournamentId == tournamentId && latest.matchId == matchId) {
                        latest.copy(
                            isScreenshotPreservationInProgress = false,
                            screenshotPreservationError = ScreenshotPreservationError.CLEANUP_FAILED,
                        )
                    } else {
                        latest
                    }
                }
            }
        }
    }

    fun finalize() {
        val current = _uiState.value
        val matchId = current.matchId ?: return
        if (
            !current.isEditable ||
            !current.isValid ||
            current.isFinalizing ||
            current.isScreenshotPreservationInProgress ||
            current.isScreenshotUploadInProgress
        ) return
        _uiState.update { it.copy(isFinalizing = true, finalizationError = null) }
        viewModelScope.launch {
            when (
                val result = finalizeMatch(
                    FinalizeMatchInput(
                        matchId = matchId,
                        rows = current.rows.map { row ->
                            MatchResultRowInput(
                                teamSlotNumber = row.teamSlotNumber,
                                placement = row.placementInput,
                                kills = row.killsInput,
                            )
                        },
                    ),
                )
            ) {
                is FinalizeMatchResult.Finalized -> _uiState.update {
                    it.copy(isFinalizing = false, finalizationError = null)
                }
                is FinalizeMatchResult.Invalid -> _uiState.update { state ->
                    state.copy(
                        isFinalizing = false,
                        validationErrors = result.validation.errorsByTeamSlot,
                        rows = state.rows.map { row ->
                            row.copy(
                                validationErrors = result.validation.errorsByTeamSlot[row.teamSlotNumber].orEmpty(),
                            )
                        },
                        finalizationError = result.globalError,
                    )
                }
            }
        }
    }
}

private fun LocalImagePreservationFailure.toUiError(): ScreenshotPreservationError = when (this) {
    LocalImagePreservationFailure.SOURCE_READ_FAILED -> ScreenshotPreservationError.SOURCE_READ_FAILED
    LocalImagePreservationFailure.COPY_FAILED -> ScreenshotPreservationError.COPY_FAILED
    LocalImagePreservationFailure.ATOMIC_MOVE_FAILED -> ScreenshotPreservationError.ATOMIC_MOVE_FAILED
}

private fun ScreenshotStorageUploadFailure.toUiError(): ScreenshotUploadError = when (this) {
    ScreenshotStorageUploadFailure.MISSING_AUTH_SESSION -> ScreenshotUploadError.MISSING_AUTH_SESSION
    ScreenshotStorageUploadFailure.MISSING_LOCAL_FILE -> ScreenshotUploadError.MISSING_LOCAL_FILE
    ScreenshotStorageUploadFailure.MISSING_TOURNAMENT_ID -> ScreenshotUploadError.MISSING_TOURNAMENT_ID
    ScreenshotStorageUploadFailure.MISSING_MATCH_ID -> ScreenshotUploadError.MISSING_MATCH_ID
    ScreenshotStorageUploadFailure.UNSUPPORTED_FORMAT -> ScreenshotUploadError.UNSUPPORTED_FORMAT
    ScreenshotStorageUploadFailure.LOCAL_FILE_READ_FAILED -> ScreenshotUploadError.LOCAL_FILE_READ_FAILED
    ScreenshotStorageUploadFailure.NETWORK -> ScreenshotUploadError.NETWORK
    ScreenshotStorageUploadFailure.AUTHORIZATION -> ScreenshotUploadError.AUTHORIZATION
    ScreenshotStorageUploadFailure.UPLOAD_FAILED -> ScreenshotUploadError.UPLOAD_FAILED
}
