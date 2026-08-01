package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.data.cloud.NoOpScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.MATCH_SCREENSHOTS_BUCKET
import com.hoggamers.rankforge.data.cloud.NoOpScreenshotMetadataCloudDataSource
import com.hoggamers.rankforge.data.cloud.ScreenshotStorageUploadFailure
import com.hoggamers.rankforge.data.cloud.ScreenshotStorageUploadResult
import com.hoggamers.rankforge.data.cloud.ScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.ScreenshotMetadataCloudDataSource
import com.hoggamers.rankforge.data.cloud.ScreenshotMetadataCloudFailure
import com.hoggamers.rankforge.data.cloud.ScreenshotMetadataCloudPayload
import com.hoggamers.rankforge.data.cloud.ScreenshotMetadataCloudResult
import com.hoggamers.rankforge.data.cloud.toCloudTimestamp
import com.hoggamers.rankforge.data.local.NoOpScreenshotMetadataRepository
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotMetadataEntity
import com.hoggamers.rankforge.data.local.ScreenshotMetadataFailureCode
import com.hoggamers.rankforge.data.local.ScreenshotMetadataRepository
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
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
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
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
    private val screenshotMetadataRepository: ScreenshotMetadataRepository = NoOpScreenshotMetadataRepository(),
    private val screenshotMetadataCloudDataSource: ScreenshotMetadataCloudDataSource =
        NoOpScreenshotMetadataCloudDataSource(),
    private val screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchReviewUiState())
    val uiState: StateFlow<MatchReviewUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var validationJob: Job? = null
    private var duplicateDetectionJob: Job? = null
    private var preservationJob: Job? = null
    private var uploadJob: Job? = null
    private var restoredMissingMarkedForMatchId: String? = null
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
                runCatching { screenshotMetadataRepository.observeByMatchId(matchId) }
                    .getOrElse { flowOf(null) },
            ) { matches, slots, rosters, draftValues, screenshotMetadata ->
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
                    val restoredScreenshot = screenshotMetadata?.toRestoredUiState(localImagePreserver)
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
                        isScreenshotLinked = restoredScreenshot?.isLinked == true,
                        linkedScreenshotFingerprint = restoredScreenshot?.fingerprint,
                        isScreenshotLocallyPreserved = restoredScreenshot?.isLocallyPreserved == true,
                        preservedScreenshotRelativePath = screenshotMetadata?.localRelativePath,
                        screenshotMetadata = restoredScreenshot?.metadataUiState,
                        isPreservedScreenshotMissing = restoredScreenshot?.isMissing == true,
                        screenshotPreservationError = if (restoredScreenshot?.isMissing == true) {
                            ScreenshotPreservationError.LOCAL_FILE_MISSING
                        } else {
                            null
                        },
                        isScreenshotUploaded = restoredScreenshot?.isUploaded == true,
                        screenshotUploadObjectPath = screenshotMetadata?.storageObjectPath,
                        screenshotUploadError = restoredScreenshot?.uploadError,
                    )
                }
            }.collect { state ->
                markRestoredMissingIfNeeded(state)
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
                        selectedScreenshotMimeType = current.selectedScreenshotMimeType,
                        selectedScreenshotWidth = current.selectedScreenshotWidth,
                        selectedScreenshotHeight = current.selectedScreenshotHeight,
                        imageValidationError = current.imageValidationError,
                        isScreenshotLinked = current.isScreenshotLinked || state.isScreenshotLinked,
                        linkedScreenshotUri = current.linkedScreenshotUri,
                        linkedScreenshotFingerprint = current.linkedScreenshotFingerprint ?: state.linkedScreenshotFingerprint,
                        screenshotLinkError = current.screenshotLinkError,
                        isScreenshotDuplicateDetectionInProgress = current.isScreenshotDuplicateDetectionInProgress,
                        screenshotDuplicateError = current.screenshotDuplicateError,
                        screenshotDuplicateInfo = current.screenshotDuplicateInfo,
                        isScreenshotPreservationInProgress = current.isScreenshotPreservationInProgress,
                        isScreenshotLocallyPreserved = current.isScreenshotLocallyPreserved || state.isScreenshotLocallyPreserved,
                        preservedScreenshotRelativePath =
                            current.preservedScreenshotRelativePath ?: state.preservedScreenshotRelativePath,
                        screenshotMetadata = current.screenshotMetadata ?: state.screenshotMetadata,
                        isPreservedScreenshotMissing =
                            current.isPreservedScreenshotMissing || state.isPreservedScreenshotMissing,
                        screenshotPreservationError = current.screenshotPreservationError
                            ?: state.screenshotPreservationError,
                        isScreenshotUploadInProgress = current.isScreenshotUploadInProgress,
                        isScreenshotUploaded = current.isScreenshotUploaded || state.isScreenshotUploaded,
                        screenshotUploadObjectPath = current.screenshotUploadObjectPath ?: state.screenshotUploadObjectPath,
                        screenshotUploadError = current.screenshotUploadError ?: state.screenshotUploadError,
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

    fun openOcrReview() {
        if (_uiState.value.canOpenOcrReview) {
            _uiState.update { it.copy(navigation = MatchReviewNavigation.OCR_REVIEW) }
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
                    selectedScreenshotMimeType = null,
                    selectedScreenshotWidth = null,
                    selectedScreenshotHeight = null,
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
                selectedScreenshotMimeType = null,
                selectedScreenshotWidth = null,
                selectedScreenshotHeight = null,
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
            val metadata = if (result == ImageCandidateValidationResult.Valid) {
                runCatching { imageCandidateValidator.readValidMetadata(selectedUri) }.getOrNull()
            } else {
                null
            }
            _uiState.update { current ->
                if (current.selectedScreenshotUri != selectedUri) {
                    current
                } else {
                    when (result) {
                        ImageCandidateValidationResult.Valid -> current.copy(
                            isScreenshotValidationInProgress = false,
                            isSelectedScreenshotValidated = true,
                            selectedScreenshotMimeType = metadata?.mimeType,
                            selectedScreenshotWidth = metadata?.width,
                            selectedScreenshotHeight = metadata?.height,
                            imageValidationError = null,
                        )
                        is ImageCandidateValidationResult.Invalid -> current.copy(
                            isScreenshotValidationInProgress = false,
                            isSelectedScreenshotValidated = false,
                            selectedScreenshotMimeType = null,
                            selectedScreenshotWidth = null,
                            selectedScreenshotHeight = null,
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
                val metadataResult = if (preservedFile != null) {
                    createOrReplaceMetadata(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        selectedUri = candidateUri,
                        preservedFile = preservedFile,
                        sha256 = result.fingerprint,
                        cleanupFailed = cleanupFailed,
                    )
                } else {
                    MetadataWriteResult.NotAttempted
                }
                if (metadataResult is MetadataWriteResult.Failed) {
                    screenshotDuplicateDetector.rollback(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        newFingerprint = result.fingerprint,
                        previousFingerprint = previousFingerprint,
                    )
                }
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
                    } else if (metadataResult is MetadataWriteResult.Failed) {
                        latest.copy(
                            isScreenshotDuplicateDetectionInProgress = false,
                            isScreenshotPreservationInProgress = false,
                            screenshotDuplicateError = null,
                            screenshotDuplicateInfo = null,
                            screenshotPreservationError = metadataResult.error,
                        )
                    } else {
                        val metadata = (metadataResult as MetadataWriteResult.Written).metadata
                        latest.copy(
                            isScreenshotLinked = true,
                            linkedScreenshotUri = selectedUri,
                            linkedScreenshotFingerprint = result.fingerprint,
                            screenshotLinkError = null,
                            isScreenshotDuplicateDetectionInProgress = false,
                            isScreenshotPreservationInProgress = false,
                            isScreenshotLocallyPreserved = true,
                            preservedScreenshotRelativePath = metadata.localRelativePath,
                            screenshotMetadata = metadata.toUiState(),
                            isPreservedScreenshotMissing = false,
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
                        latest.isScreenshotLinked
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
        if (preservedFile == null) {
            handleStorageUploadFailure(
                tournamentId = tournamentId,
                matchId = matchId,
                selectedUri = selectedUri,
                failure = ScreenshotStorageUploadFailure.MISSING_LOCAL_FILE,
            )
            return
        }
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
                !latest.matchesUploadSelection(selectedUri) ||
                !latest.isScreenshotLinked
            ) {
                latest
            } else {
                when (result) {
                    is ScreenshotStorageUploadResult.Uploaded -> latest
                    is ScreenshotStorageUploadResult.Failed -> latest
                }
            }
        }
        when (result) {
            is ScreenshotStorageUploadResult.Uploaded -> handleStorageUploadSuccess(
                tournamentId = tournamentId,
                matchId = matchId,
                selectedUri = selectedUri,
                objectPath = result.objectPath,
            )
            is ScreenshotStorageUploadResult.Failed -> handleStorageUploadFailure(
                tournamentId = tournamentId,
                matchId = matchId,
                selectedUri = selectedUri,
                failure = result.error,
            )
        }
    }

    private suspend fun createOrReplaceMetadata(
        tournamentId: String,
        matchId: String,
        selectedUri: String,
        preservedFile: File,
        sha256: String,
        cleanupFailed: Boolean,
    ): MetadataWriteResult {
        val ownerUserId = screenshotOwnerProvider.currentOwnerUserId()
            ?: return MetadataWriteResult.Failed(ScreenshotPreservationError.ROOM_WRITE_FAILED)
        val relativePath = localImagePreserver.relativePathFor(preservedFile)
            ?: return MetadataWriteResult.Failed(ScreenshotPreservationError.INVALID_RELATIVE_PATH)
        val extension = preservedFile.extension.lowercase()
        val mimeType = _uiState.value.selectedScreenshotMimeType
            ?: mimeTypeForExtension(extension)
            ?: return MetadataWriteResult.Failed(ScreenshotPreservationError.ROOM_WRITE_FAILED)
        val dimensions = currentValidatedDimensions(selectedUri)
            ?: return MetadataWriteResult.Failed(ScreenshotPreservationError.ROOM_WRITE_FAILED)
        val byteSize = runCatching { preservedFile.length() }.getOrDefault(0L)
        if (byteSize <= 0L || sha256.length != 64 || sha256.any { it !in '0'..'9' && it !in 'a'..'f' }) {
            return MetadataWriteResult.Failed(ScreenshotPreservationError.ROOM_WRITE_FAILED)
        }

        val now = nowMillis()
        val previous = runCatching { screenshotMetadataRepository.getByMatchId(matchId) }.getOrNull()
        val metadata = ScreenshotMetadataEntity(
            matchId = matchId,
            tournamentId = tournamentId,
            ownerUserId = ownerUserId,
            localRelativePath = relativePath,
            fileExtension = extension,
            mimeType = mimeType,
            width = dimensions.width,
            height = dimensions.height,
            byteSize = byteSize,
            sha256 = sha256,
            storageBucket = null,
            storageObjectPath = null,
            localStatus = if (cleanupFailed) {
                ScreenshotLocalStatus.CLEANUP_FAILED.name
            } else {
                ScreenshotLocalStatus.PRESERVED.name
            },
            uploadStatus = ScreenshotUploadStatus.PENDING.name,
            uploadFailureCode = null,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
            preservedAt = now,
            uploadedAt = null,
            revision = previous?.revision?.plus(1) ?: 1L,
        )
        return try {
            screenshotMetadataRepository.createOrReplace(metadata)
            MetadataWriteResult.Written(metadata)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            MetadataWriteResult.Failed(ScreenshotPreservationError.ROOM_WRITE_FAILED)
        }
    }

    private suspend fun handleStorageUploadSuccess(
        tournamentId: String,
        matchId: String,
        selectedUri: String,
        objectPath: String,
    ) {
        val uploadedAt = nowMillis()
        val localUpdateSucceeded = runCatching {
            screenshotMetadataRepository.updateUploadSuccess(
                matchId = matchId,
                storageBucket = MATCH_SCREENSHOTS_BUCKET,
                storageObjectPath = objectPath,
                uploadedAt = uploadedAt,
                updatedAt = uploadedAt,
            )
        }.isSuccess
        if (!localUpdateSucceeded) {
            setUploadUiState(tournamentId, matchId, selectedUri, ScreenshotUploadError.UPLOAD_FAILED)
            return
        }
        val updatedMetadata = runCatching { screenshotMetadataRepository.getByMatchId(matchId) }
            .getOrNull()
        val cloudResult = if (updatedMetadata != null) {
            runCatching {
                screenshotMetadataCloudDataSource.upsert(updatedMetadata.toCloudPayload())
            }.getOrElse {
                ScreenshotMetadataCloudResult.Failed(ScreenshotMetadataCloudFailure.WRITE_FAILED)
            }
        } else {
            ScreenshotMetadataCloudResult.Failed(ScreenshotMetadataCloudFailure.WRITE_FAILED)
        }
        if (cloudResult is ScreenshotMetadataCloudResult.Failed) {
            runCatching {
                screenshotMetadataRepository.updateUploadFailure(
                    matchId = matchId,
                    failureCode = cloudResult.failure.toFailureCode(),
                    updatedAt = nowMillis(),
                )
            }
            setUploadUiState(tournamentId, matchId, selectedUri, cloudResult.failure.toUiError())
        } else {
            _uiState.update { latest ->
                if (latest.tournamentId == tournamentId && latest.matchId == matchId && latest.isScreenshotLinked) {
                    latest.copy(
                        isScreenshotUploadInProgress = false,
                        isScreenshotUploaded = true,
                        screenshotUploadObjectPath = objectPath,
                        screenshotUploadError = null,
                        screenshotMetadata = updatedMetadata?.toUiState(),
                    )
                } else {
                    latest
                }
            }
        }
    }

    private suspend fun handleStorageUploadFailure(
        tournamentId: String,
        matchId: String,
        selectedUri: String,
        failure: ScreenshotStorageUploadFailure,
    ) {
        runCatching {
            screenshotMetadataRepository.updateUploadFailure(
                matchId = matchId,
                failureCode = failure.name,
                updatedAt = nowMillis(),
            )
        }
        setUploadUiState(tournamentId, matchId, selectedUri, failure.toUiError())
    }

    private fun setUploadUiState(
        tournamentId: String,
        matchId: String,
        selectedUri: String,
        error: ScreenshotUploadError,
    ) {
        _uiState.update { latest ->
            if (
                latest.tournamentId != tournamentId ||
                latest.matchId != matchId ||
                !latest.matchesUploadSelection(selectedUri) ||
                !latest.isScreenshotLinked
            ) {
                latest
            } else {
                latest.copy(
                    isScreenshotUploadInProgress = false,
                    isScreenshotUploaded = false,
                    screenshotUploadObjectPath = null,
                    screenshotUploadError = error,
                    screenshotMetadata = latest.screenshotMetadata?.copy(
                        uploadStatus = ScreenshotMetadataUploadUiStatus.FAILED,
                        revision = latest.screenshotMetadata.revision + 1,
                    ),
                )
            }
        }
    }

    private suspend fun currentValidatedDimensions(selectedUri: String): ImageDimensions? {
        val current = _uiState.value
        val width = current.selectedScreenshotWidth
        val height = current.selectedScreenshotHeight
        if (width != null && height != null && width > 0 && height > 0) {
            return ImageDimensions(width, height)
        }
        return (imageCandidateValidator.readValidMetadata(selectedUri))?.let {
            ImageDimensions(it.width, it.height)
        }
    }

    private fun markRestoredMissingIfNeeded(state: MatchReviewUiState) {
        val matchId = state.matchId ?: return
        if (!state.isPreservedScreenshotMissing || restoredMissingMarkedForMatchId == matchId) return
        restoredMissingMarkedForMatchId = matchId
        viewModelScope.launch {
            runCatching {
                screenshotMetadataRepository.markLocalMissing(matchId, nowMillis())
            }
        }
    }

    private fun nowMillis(): Long = clock.millis()

    fun retryScreenshotUpload() {
        val current = _uiState.value
        val tournamentId = current.tournamentId?.takeIf { it.isNotBlank() } ?: return
        val matchId = current.matchId?.takeIf { it.isNotBlank() } ?: return
        val selectedUri = current.linkedScreenshotUri ?: current.selectedScreenshotUri.orEmpty()
        val preservedPath = current.preservedScreenshotRelativePath ?: return
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
                preservedFile = localImagePreserver.resolveRelativePath(preservedPath),
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

        val preservedPath = current.preservedScreenshotRelativePath
        _uiState.update {
            it.copy(
                screenshotLinkError = null,
                screenshotDuplicateError = null,
                screenshotDuplicateInfo = null,
                isScreenshotPreservationInProgress = preservedPath != null,
                screenshotPreservationError = null,
                isScreenshotUploadInProgress = false,
                screenshotUploadError = null,
            )
        }
        if (preservedPath == null) return

        val tournamentId = current.tournamentId ?: return
        val matchId = current.matchId ?: return
        preservationJob = viewModelScope.launch {
            when (localImagePreserver.cleanup(tournamentId, matchId)) {
                LocalImageCleanupResult.Cleaned -> {
                    runCatching { screenshotMetadataRepository.deleteByMatchId(matchId) }
                    val cloudDeleteResult = runCatching {
                        screenshotMetadataCloudDataSource.deleteByMatchId(matchId)
                    }.getOrDefault(ScreenshotMetadataCloudResult.Success)
                    _uiState.update { latest ->
                        if (latest.tournamentId == tournamentId && latest.matchId == matchId) {
                            latest.copy(
                                isScreenshotLinked = false,
                                linkedScreenshotUri = null,
                                linkedScreenshotFingerprint = null,
                                isScreenshotLocallyPreserved = false,
                                isScreenshotPreservationInProgress = false,
                                preservedScreenshotRelativePath = null,
                                screenshotMetadata = null,
                                isPreservedScreenshotMissing = false,
                                screenshotPreservationError = null,
                                isScreenshotUploaded = false,
                                screenshotUploadObjectPath = null,
                                screenshotUploadError = if (cloudDeleteResult is ScreenshotMetadataCloudResult.Failed) {
                                    cloudDeleteResult.failure.toUiError()
                                } else {
                                    null
                                },
                            )
                        } else {
                            latest
                        }
                    }
                }
                LocalImageCleanupResult.Failed -> {
                    runCatching { screenshotMetadataRepository.markCleanupFailure(matchId, nowMillis()) }
                    _uiState.update { latest ->
                        if (latest.tournamentId == tournamentId && latest.matchId == matchId) {
                            latest.copy(
                                isScreenshotPreservationInProgress = false,
                                screenshotPreservationError = ScreenshotPreservationError.CLEANUP_FAILED,
                                screenshotMetadata = latest.screenshotMetadata?.copy(
                                    localStatus = ScreenshotMetadataLocalUiStatus.CLEANUP_FAILED,
                                    revision = latest.screenshotMetadata.revision + 1,
                                ),
                            )
                        } else {
                            latest
                        }
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

private sealed interface MetadataWriteResult {
    data object NotAttempted : MetadataWriteResult
    data class Written(val metadata: ScreenshotMetadataEntity) : MetadataWriteResult
    data class Failed(val error: ScreenshotPreservationError) : MetadataWriteResult
}

private data class ImageDimensions(
    val width: Int,
    val height: Int,
)

private data class RestoredScreenshotState(
    val isLinked: Boolean,
    val fingerprint: String?,
    val isLocallyPreserved: Boolean,
    val isMissing: Boolean,
    val isUploaded: Boolean,
    val metadataUiState: ScreenshotMetadataUiState,
    val uploadError: ScreenshotUploadError?,
)

private fun ScreenshotMetadataEntity.toRestoredUiState(
    localImagePreserver: LocalImagePreserver,
): RestoredScreenshotState {
    val local = runCatching { ScreenshotLocalStatus.valueOf(localStatus) }
        .getOrDefault(ScreenshotLocalStatus.MISSING)
    val upload = runCatching { ScreenshotUploadStatus.valueOf(uploadStatus) }
        .getOrDefault(ScreenshotUploadStatus.FAILED)
    val fileIsPresent = localImagePreserver.resolveRelativePath(localRelativePath)?.let { file ->
        runCatching { file.isFile && file.length() > 0L }.getOrDefault(false)
    } == true
    val effectiveLocal = if (fileIsPresent) local else ScreenshotLocalStatus.MISSING
    return RestoredScreenshotState(
        isLinked = effectiveLocal != ScreenshotLocalStatus.MISSING,
        fingerprint = sha256,
        isLocallyPreserved = effectiveLocal == ScreenshotLocalStatus.PRESERVED,
        isMissing = effectiveLocal == ScreenshotLocalStatus.MISSING,
        isUploaded = upload == ScreenshotUploadStatus.UPLOADED,
        metadataUiState = toUiState(),
        uploadError = if (upload == ScreenshotUploadStatus.FAILED) {
            uploadFailureCode?.toUploadUiError()
        } else {
            null
        },
    )
}

private fun ScreenshotMetadataEntity.toUiState(): ScreenshotMetadataUiState =
    ScreenshotMetadataUiState(
        localStatus = when (runCatching { ScreenshotLocalStatus.valueOf(localStatus) }.getOrNull()) {
            ScreenshotLocalStatus.PRESERVED -> ScreenshotMetadataLocalUiStatus.PRESERVED
            ScreenshotLocalStatus.CLEANUP_FAILED -> ScreenshotMetadataLocalUiStatus.CLEANUP_FAILED
            else -> ScreenshotMetadataLocalUiStatus.MISSING
        },
        uploadStatus = when (runCatching { ScreenshotUploadStatus.valueOf(uploadStatus) }.getOrNull()) {
            ScreenshotUploadStatus.PENDING -> ScreenshotMetadataUploadUiStatus.PENDING
            ScreenshotUploadStatus.UPLOADED -> ScreenshotMetadataUploadUiStatus.UPLOADED
            else -> ScreenshotMetadataUploadUiStatus.FAILED
        },
        revision = revision,
    )

private fun ScreenshotMetadataEntity.toCloudPayload(): ScreenshotMetadataCloudPayload =
    ScreenshotMetadataCloudPayload(
        matchId = matchId,
        ownerId = ownerUserId,
        tournamentId = tournamentId,
        localFileExtension = fileExtension,
        mimeType = mimeType,
        width = width,
        height = height,
        byteSize = byteSize,
        sha256 = sha256,
        storageBucket = storageBucket,
        storageObjectPath = storageObjectPath,
        localStatus = localStatus,
        uploadStatus = uploadStatus,
        uploadFailureCode = uploadFailureCode,
        preservedAt = preservedAt.toCloudTimestamp(),
        uploadedAt = uploadedAt?.toCloudTimestamp(),
        revision = revision,
        createdAt = createdAt.toCloudTimestamp(),
        updatedAt = updatedAt.toCloudTimestamp(),
    )

private fun ScreenshotMetadataCloudFailure.toFailureCode(): String = when (this) {
    ScreenshotMetadataCloudFailure.AUTHORIZATION -> ScreenshotMetadataFailureCode.RLS_DENIED.name
    else -> ScreenshotMetadataFailureCode.CLOUD_METADATA_WRITE_FAILED.name
}

private fun ScreenshotMetadataCloudFailure.toUiError(): ScreenshotUploadError = when (this) {
    ScreenshotMetadataCloudFailure.AUTHORIZATION -> ScreenshotUploadError.RLS_DENIED
    else -> ScreenshotUploadError.CLOUD_METADATA_WRITE_FAILED
}

private fun String.toUploadUiError(): ScreenshotUploadError =
    runCatching { ScreenshotStorageUploadFailure.valueOf(this).toUiError() }
        .getOrElse {
            when (this) {
                ScreenshotMetadataFailureCode.RLS_DENIED.name -> ScreenshotUploadError.RLS_DENIED
                else -> ScreenshotUploadError.CLOUD_METADATA_WRITE_FAILED
            }
        }

private fun mimeTypeForExtension(extension: String): String? = when (extension.lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    else -> null
}

private fun MatchReviewUiState.matchesUploadSelection(selectedUri: String): Boolean =
    selectedUri.isBlank() || selectedScreenshotUri == selectedUri || linkedScreenshotUri == selectedUri
