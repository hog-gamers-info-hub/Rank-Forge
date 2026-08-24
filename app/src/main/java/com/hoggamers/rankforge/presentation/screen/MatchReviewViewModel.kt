package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.hoggamers.rankforge.data.cloud.MatchCloudIdentity
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudFailure
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudResult
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotStorageUploadFailure
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotStorageUploadResult
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.NoOpScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.NoOpMatchResultScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.NoOpMatchResultScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.OCR_SCREENSHOTS_BUCKET
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
import com.hoggamers.rankforge.data.export.AndroidExportBlockedReason
import com.hoggamers.rankforge.data.export.AndroidExportCoordinator
import com.hoggamers.rankforge.data.export.GoogleSheetsMatchExportExecutionResult
import com.hoggamers.rankforge.data.export.GoogleSheetsMatchExportRemoteDataSource
import com.hoggamers.rankforge.data.export.NoOpGoogleSheetsMatchExportRemoteDataSource
import com.hoggamers.rankforge.data.export.NoOpResultDocumentWriter
import com.hoggamers.rankforge.data.export.NoOpResultDownloadCoordinator
import com.hoggamers.rankforge.data.export.ResultDocumentWriteResult
import com.hoggamers.rankforge.data.export.ResultDocumentWriter
import com.hoggamers.rankforge.data.export.ResultDownloadCoordinator
import com.hoggamers.rankforge.data.export.ResultDownloadExecutionResult
import com.hoggamers.rankforge.data.export.ResultDownloadFailure
import com.hoggamers.rankforge.data.export.ResultDownloadRequest
import com.hoggamers.rankforge.data.export.ResultDownloadScope
import com.hoggamers.rankforge.data.export.ResultExportFileFormat
import com.hoggamers.rankforge.data.local.NoOpScreenshotMetadataRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.NoOpMatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotMetadataEntity
import com.hoggamers.rankforge.data.local.ScreenshotMetadataFailureCode
import com.hoggamers.rankforge.data.local.ScreenshotMetadataRepository
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.domain.tournament.MatchResultRowInput
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchDraftFieldValues
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.export.MatchCsvExportFailure
import com.hoggamers.rankforge.domain.export.MatchCsvExportInput
import com.hoggamers.rankforge.domain.export.MatchCsvExportResult
import com.hoggamers.rankforge.domain.export.MatchCsvExporter
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchInput
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchResult
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.CloudDeletionFailureCategory
import com.hoggamers.rankforge.domain.tournament.DeleteMatchResult
import com.hoggamers.rankforge.domain.tournament.DeleteMatchUseCase
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import com.hoggamers.rankforge.domain.tournament.finalizedParticipantResultsOrNull
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
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
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult

private fun DeleteMatchResult.toUiError(): MatchDeletionUiError = when (this) {
    DeleteMatchResult.TargetNotFound -> MatchDeletionUiError.TARGET_NOT_FOUND
    DeleteMatchResult.AuthenticationRequired -> MatchDeletionUiError.AUTHENTICATION_REQUIRED
    DeleteMatchResult.PendingSyncPreparationFailed -> MatchDeletionUiError.PREPARATION_FAILURE
    DeleteMatchResult.RemoteDeletedLocalCleanupFailed -> MatchDeletionUiError.LOCAL_CLEANUP_FAILURE
    is DeleteMatchResult.StorageDeletionFailed -> category.toUiError(MatchDeletionUiError.STORAGE_FAILURE)
    is DeleteMatchResult.RemoteDeletionFailed -> category.toUiError(MatchDeletionUiError.REMOTE_FAILURE)
    DeleteMatchResult.Success -> error("Successful deletion has no UI error")
}

private fun CloudDeletionFailureCategory.toUiError(default: MatchDeletionUiError): MatchDeletionUiError = when (this) {
    CloudDeletionFailureCategory.AUTHENTICATION -> MatchDeletionUiError.AUTHENTICATION_REQUIRED
    CloudDeletionFailureCategory.AUTHORIZATION -> MatchDeletionUiError.AUTHORIZATION_FAILURE
    CloudDeletionFailureCategory.VALIDATION -> MatchDeletionUiError.VALIDATION_FAILURE
    else -> default
}

@HiltViewModel
class MatchReviewViewModel @Inject constructor(
    private val getTournamentById: GetTournamentByIdUseCase,
    private val observeMatches: ObserveMatchesUseCase,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeRoster: ObserveRosterByTournamentUseCase,
    private val observeDraftValues: ObserveMatchDraftValuesUseCase,
    private val validateMatchResult: ValidateMatchResultUseCase,
    private val finalizeMatch: FinalizeMatchUseCase,
    private val imageCandidateValidator: ImageCandidateValidator,
    private val screenshotDuplicateDetector: ScreenshotDuplicateDetector,
    private val matchResultScreenshotDuplicateDetector: MatchResultScreenshotDuplicateDetector =
        MatchResultScreenshotDuplicateDetector(
            ImageSourceFingerprintGenerator(ImageSourceStreamOpener { null }),
        ),
    private val localImagePreserver: LocalImagePreserver,
    private val screenshotStorageUploader: ScreenshotStorageUploader = NoOpScreenshotStorageUploader(),
    private val matchResultScreenshotStorageUploader: MatchResultScreenshotStorageUploader =
        NoOpMatchResultScreenshotStorageUploader(),
    private val screenshotMetadataRepository: ScreenshotMetadataRepository = NoOpScreenshotMetadataRepository(),
    private val matchResultScreenshotAssetRepository: MatchResultScreenshotAssetRepository =
        NoOpMatchResultScreenshotAssetRepository(),
    private val screenshotMetadataCloudDataSource: ScreenshotMetadataCloudDataSource =
        NoOpScreenshotMetadataCloudDataSource(),
    private val matchResultScreenshotAssetCloudDataSource: MatchResultScreenshotAssetCloudDataSource =
        NoOpMatchResultScreenshotAssetCloudDataSource(),
    private val googleSheetsMatchExport: GoogleSheetsMatchExportRemoteDataSource =
        NoOpGoogleSheetsMatchExportRemoteDataSource(),
    private val resultDownloadCoordinator: ResultDownloadCoordinator =
        NoOpResultDownloadCoordinator,
    private val resultDocumentWriter: ResultDocumentWriter = NoOpResultDocumentWriter,
    private val screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
    private val clock: Clock = Clock.systemUTC(),
    private val finalizedMatchCloudSync: FinalizedMatchCloudSyncAction =
        FinalizedMatchCloudSyncAction {
            QueueAwareActionResult(
                primaryResult = FinalizedMatchCloudSyncResult.ValidationFailure,
                queueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
            )
        },
    private val deleteMatchUseCase: DeleteMatchUseCase? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchReviewUiState())
    val uiState: StateFlow<MatchReviewUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var validationJob: Job? = null
    private var duplicateDetectionJob: Job? = null
    private var preservationJob: Job? = null
    private var uploadJob: Job? = null
    private val resultScreenshotJobs = mutableMapOf<MatchResultScreenshotRole, Job>()
    private var exportJob: Job? = null
    private var resultDownloadJob: Job? = null
    private var pendingResultDocument: PendingResultDocument? = null
    private var restoredMissingMarkedForMatchId: String? = null
    private val restoredResultMissingMarked = mutableSetOf<String>()
    private var loadedMatchKey: String? = null
    private var resultScreenshotBatchJob: Job? = null
    private var screenshotIntakeGeneration = 0L
    private var activeResultBatchGeneration: Long? = null
    private var activeResultBatchRoles: Set<MatchResultScreenshotRole> = emptySet()
    private var activeResultBatchSelectedUris: Map<MatchResultScreenshotRole, String> = emptyMap()
    private var nextResultMultiPhotoPickerRequestId = 0L

    fun load(tournamentId: String, matchId: String) {
        val matchKey = "$tournamentId:$matchId"
        if (loadedMatchKey == matchKey) return
        loadedMatchKey = matchKey
        loadJob?.cancel()
        validationJob?.cancel()
        duplicateDetectionJob?.cancel()
        preservationJob?.cancel()
        uploadJob?.cancel()
        resultScreenshotJobs.values.forEach { it.cancel() }
        resultScreenshotJobs.clear()
        cancelActiveResultBatchAndClearTransientState()
        screenshotIntakeGeneration++
        exportJob?.cancel()
        resultDownloadJob?.cancel()
        pendingResultDocument = null
        _uiState.update {
            MatchReviewUiState(
                isLoading = true,
                tournamentId = tournamentId,
                matchId = matchId,
            )
        }
        loadJob = viewModelScope.launch {
            val ownerUserId = screenshotOwnerProvider.currentOwnerUserId()
            val baseInputs = combine(
                observeMatches(tournamentId),
                observeTournamentSlots(tournamentId),
                observeRoster(tournamentId),
                observeDraftValues(tournamentId, matchId),
            ) { matches, slots, rosters, draftValues ->
                MatchReviewLoadInputs(
                    matches = matches,
                    slots = slots,
                    rosters = rosters,
                    draftValues = draftValues,
                )
            }
            combine(
                baseInputs,
                if (ownerUserId.isNullOrBlank()) flowOf(null)
                else runCatching { screenshotMetadataRepository.observeByMatchIdAndOwner(matchId, ownerUserId) }
                    .getOrElse { flowOf(null) },
                if (ownerUserId.isNullOrBlank()) flowOf(emptyList())
                else runCatching { matchResultScreenshotAssetRepository.observeByMatchIdAndOwner(matchId, ownerUserId) }
                    .getOrElse { flowOf(emptyList()) },
            ) { inputs, screenshotMetadata, resultScreenshotAssets ->
                val matches = inputs.matches
                val slots = inputs.slots
                val rosters = inputs.rosters
                val draftValues = inputs.draftValues
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
                        validateMatchForReview(match)
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
                    val resultScreenshotSlots =
                        resultScreenshotAssets.toResultScreenshotSlots(localImagePreserver)
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
                        resultScreenshots = resultScreenshotSlots,
                    )
                }
            }.collect { state ->
                markRestoredMissingIfNeeded(state)
                markRestoredResultMissingIfNeeded(state)
                _uiState.update { current ->
                    state.copy(
                        navigation = current.navigation,
                        isDeleting = current.isDeleting,
                        deletionError = current.deletionError,
                        isFinalizing = current.isFinalizing,
                        finalizationError = current.finalizationError,
                        csvExportResult = current.csvExportResult,
                        googleSheetsExportResult = current.googleSheetsExportResult,
                        resultDownloadUiState = current.resultDownloadUiState,
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
                        resultScreenshots = mergeResultScreenshotSlots(
                            restored = state.resultScreenshots,
                            current = current.resultScreenshots,
                        ),
                        pendingResultScreenshotCropBatch = current.pendingResultScreenshotCropBatch,
                        resultScreenshotMultiPhotoPickerRequest = current.resultScreenshotMultiPhotoPickerRequest,
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
        val state = _uiState.value
        if (state.isEditable && !state.tournamentId.isNullOrBlank() && !state.matchId.isNullOrBlank()) {
            _uiState.update { it.copy(navigation = MatchReviewNavigation.OCR_REVIEW) }
        }
    }

    fun onBackToDetails() {
        if (_uiState.value.isAvailable && !_uiState.value.isDeleting) {
            _uiState.update { it.copy(navigation = MatchReviewNavigation.DETAILS) }
        }
    }

    fun deleteMatch() {
        val current = _uiState.value
        val matchId = current.matchId
        if (current.isDeleting || !current.isAvailable || matchId.isNullOrBlank()) return
        _uiState.update {
            it.copy(
                isDeleting = true,
                deletionError = null,
            )
        }
        viewModelScope.launch {
            val useCase = deleteMatchUseCase ?: run {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletionError = MatchDeletionUiError.UNKNOWN,
                    )
                }
                return@launch
            }
            val result = try {
                useCase(matchId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletionError = MatchDeletionUiError.UNKNOWN,
                    )
                }
                return@launch
            }
            when (result) {
                DeleteMatchResult.Success -> _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletionError = null,
                        navigation = MatchReviewNavigation.DETAILS,
                    )
                }
                else -> _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletionError = result.toUiError(),
                    )
                }
            }
        }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigation = null) }
    }

    fun onResultCropConfirmed(
        tournamentId: String,
        matchId: String,
        role: MatchResultScreenshotRole,
    ): MatchResultScreenshotRole? {
        val current = _uiState.value
        val batch = current.pendingResultScreenshotCropBatch
        if (
            current.tournamentId != tournamentId ||
            current.matchId != matchId ||
            batch?.currentRole != role
        ) {
            return null
        }
        val nextRole = batch.remainingRoles.firstOrNull()
        _uiState.update {
            it.copy(
                pendingResultScreenshotCropBatch = nextRole?.let {
                    MatchResultScreenshotCropBatch(
                        currentRole = it,
                        remainingRoles = batch.remainingRoles.drop(1),
                    )
                },
            )
        }
        return nextRole
    }

    fun cancelResultCropBatch(tournamentId: String, matchId: String) {
        val current = _uiState.value
        if (
            current.tournamentId != tournamentId ||
            current.matchId != matchId ||
            current.pendingResultScreenshotCropBatch == null
        ) {
            return
        }
        _uiState.update { it.copy(pendingResultScreenshotCropBatch = null) }
    }

    fun prepareCsvExport() {
        val current = _uiState.value
        val tournamentId = current.tournamentId ?: return
        val matchId = current.matchId ?: return
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            val tournament = getTournamentById(tournamentId).first()
            val match = observeMatches(tournamentId).first().firstOrNull { it.id == matchId }
            val result = when {
                tournament == null || match == null -> AndroidExportCoordinator().blockMatchCsv(
                    tournamentId = tournamentId,
                    matchId = matchId,
                    reason = AndroidExportBlockedReason.MISSING_CONTEXT,
                )
                match.status != MatchStatus.FINALIZED -> AndroidExportCoordinator().blockMatchCsv(
                    tournamentId = tournamentId,
                    matchId = matchId,
                    reason = AndroidExportBlockedReason.MATCH_NOT_FINALIZED,
                )
                else -> {
                    val exportResult = MatchCsvExporter().export(
                        MatchCsvExportInput(
                            tournament = tournament,
                            match = match,
                            teamSlots = observeTournamentSlots(tournamentId).first(),
                            rosterPlayers = observeRoster(tournamentId).first().values.flatten(),
                        ),
                    )
                    when (exportResult) {
                        is MatchCsvExportResult.Success -> AndroidExportCoordinator()
                            .prepareMatchCsv(tournamentId, matchId, exportResult.csv)
                        is MatchCsvExportResult.Failure -> AndroidExportCoordinator()
                            .blockMatchCsv(
                                tournamentId = tournamentId,
                                matchId = matchId,
                                reason = if (MatchCsvExportFailure.MATCH_NOT_FINALIZED in exportResult.failures) {
                                    AndroidExportBlockedReason.MATCH_NOT_FINALIZED
                                } else {
                                    AndroidExportBlockedReason.INVALID_FINALIZED_MATCH
                                },
                            )
                    }
                }
            }
            _uiState.update { state ->
                if (state.tournamentId == tournamentId && state.matchId == matchId) {
                    state.copy(csvExportResult = result)
                } else {
                    state
                }
            }
        }
    }

    fun prepareGoogleSheetsExport() {
        val current = _uiState.value
        val tournamentId = current.tournamentId ?: return
        val matchId = current.matchId ?: return
        if (exportJob?.isActive == true) return

        exportJob = viewModelScope.launch {
            val tournament = getTournamentById(tournamentId).first()
            val match = observeMatches(tournamentId).first().firstOrNull { it.id == matchId }
            val result = when {
                tournament == null || match == null -> AndroidExportCoordinator()
                    .blockGoogleSheetsMatch(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        reason = AndroidExportBlockedReason.MISSING_CONTEXT,
                    )
                match.status != MatchStatus.FINALIZED -> AndroidExportCoordinator()
                    .blockGoogleSheetsMatch(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        reason = AndroidExportBlockedReason.MATCH_NOT_FINALIZED,
                    )
                else -> {
                    val rowsResult = MatchCsvExporter().buildMatchRows(
                        MatchCsvExportInput(
                            tournament = tournament,
                            match = match,
                            teamSlots = observeTournamentSlots(tournamentId).first(),
                            rosterPlayers = observeRoster(tournamentId).first().values.flatten(),
                        ),
                    )
                    when (rowsResult) {
                        is com.hoggamers.rankforge.domain.export.MatchExportRowsResult.Failure ->
                            AndroidExportCoordinator().blockGoogleSheetsMatch(
                                tournamentId = tournamentId,
                                matchId = matchId,
                                reason = AndroidExportBlockedReason.INVALID_FINALIZED_MATCH,
                            )
                        is com.hoggamers.rankforge.domain.export.MatchExportRowsResult.Success -> {
                            val hostedMatchId = MatchCloudIdentity.matchId(
                                tournamentId = tournamentId,
                                localMatchId = match.id,
                            )
                            if (hostedMatchId == null) {
                                AndroidExportCoordinator().blockGoogleSheetsMatch(
                                    tournamentId = tournamentId,
                                    matchId = matchId,
                                    reason = AndroidExportBlockedReason.INVALID_FINALIZED_MATCH,
                                )
                            } else {
                                val hostedRows = rowsResult.rows.map { row ->
                                    row.copy(matchId = hostedMatchId)
                                }
                                _uiState.update { state ->
                                    if (state.tournamentId == tournamentId && state.matchId == matchId) {
                                        state.copy(
                                            googleSheetsExportResult = AndroidExportCoordinator()
                                                .googleSheetsMatchExporting(tournamentId, matchId),
                                        )
                                    } else {
                                        state
                                    }
                                }
                                when (
                                    val exportResult = googleSheetsMatchExport.export(
                                        tournamentId = tournamentId,
                                        matchId = hostedMatchId,
                                        rows = hostedRows,
                                    )
                                ) {
                                    is GoogleSheetsMatchExportExecutionResult.Success ->
                                        AndroidExportCoordinator().googleSheetsMatchSuccess(
                                            tournamentId = tournamentId,
                                            matchId = matchId,
                                            exportedMatchCount = 1,
                                            rowsWritten = exportResult.rowsWritten,
                                        )
                                    is GoogleSheetsMatchExportExecutionResult.Failure ->
                                        AndroidExportCoordinator().googleSheetsMatchFailure(
                                            tournamentId = tournamentId,
                                            matchId = matchId,
                                            reason = exportResult.reason,
                                        )
                                }
                            }
                        }
                    }
                }
            }
            _uiState.update { state ->
                if (state.tournamentId == tournamentId && state.matchId == matchId) {
                    state.copy(googleSheetsExportResult = result)
                } else {
                    state
                }
            }
        }
    }

    fun requestResultDownload(
        scope: ResultDownloadScope,
        format: ResultExportFileFormat,
    ) {
        val current = _uiState.value
        val tournamentId = current.tournamentId ?: return
        val matchId = current.matchId ?: return
        if (!current.canDownloadResult || resultDownloadJob?.isActive == true) return

        pendingResultDocument = null
        resultDownloadJob = viewModelScope.launch {
            _uiState.update { state ->
                if (state.tournamentId == tournamentId && state.matchId == matchId) {
                    state.copy(resultDownloadUiState = ResultDownloadUiState.Generating(scope, format))
                } else {
                    state
                }
            }
            val outcome = try {
                val tournament = getTournamentById(tournamentId).first()
                val matches = observeMatches(tournamentId).first()
                val currentMatch = matches.firstOrNull { it.id == matchId }
                when {
                    tournament == null || currentMatch == null ->
                        ResultDownloadExecutionResult.Failure(ResultDownloadFailure.INVALID_CONTEXT)
                    currentMatch.status != MatchStatus.FINALIZED ->
                        ResultDownloadExecutionResult.Failure(ResultDownloadFailure.INVALID_MATCH)
                    validateMatchForReview(currentMatch).errorsByTeamSlot.isNotEmpty() ->
                        ResultDownloadExecutionResult.Failure(ResultDownloadFailure.INVALID_MATCH)
                    else -> {
                        val inputSlots = observeTournamentSlots(tournamentId).first()
                        val rosterPlayers = observeRoster(tournamentId).first().values.flatten()
                        val request = when (scope) {
                            ResultDownloadScope.CURRENT_MATCH -> ResultDownloadRequest.CurrentMatch(
                                com.hoggamers.rankforge.domain.export.MatchCsvExportInput(
                                    tournament = tournament,
                                    match = currentMatch,
                                    teamSlots = inputSlots,
                                    rosterPlayers = rosterPlayers,
                                ),
                            )
                            ResultDownloadScope.WHOLE_TOURNAMENT -> ResultDownloadRequest.WholeTournament(
                                com.hoggamers.rankforge.domain.export.TournamentCsvExportInput(
                                    tournament = tournament,
                                    matches = matches,
                                    teamSlots = inputSlots,
                                    rosterPlayers = rosterPlayers,
                                ),
                            )
                        }
                        resultDownloadCoordinator.execute(
                            request = request,
                            format = format,
                            onSaving = {
                                _uiState.update { state ->
                                    if (state.tournamentId == tournamentId && state.matchId == matchId) {
                                        state.copy(resultDownloadUiState = ResultDownloadUiState.Saving(format))
                                    } else {
                                        state
                                    }
                                }
                            },
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ResultDownloadExecutionResult.Failure(ResultDownloadFailure.GENERATION_FAILED)
            }

            _uiState.update { state ->
                if (state.tournamentId != tournamentId || state.matchId != matchId) {
                    state
                } else {
                    when (outcome) {
                        is ResultDownloadExecutionResult.Saved -> state.copy(
                            resultDownloadUiState = ResultDownloadUiState.Success(
                                format = outcome.format,
                                userSelectedDestination = false,
                            ),
                        )
                        is ResultDownloadExecutionResult.UserDestinationRequired -> {
                            pendingResultDocument = PendingResultDocument(
                                format = outcome.format,
                                displayName = outcome.displayName,
                                bytes = outcome.bytes,
                            )
                            state.copy(
                                resultDownloadUiState = ResultDownloadUiState.DestinationLaunchRequested(
                                    format = outcome.format,
                                    suggestedDisplayName = outcome.displayName,
                                ),
                            )
                        }
                        is ResultDownloadExecutionResult.Failure -> state.copy(
                            resultDownloadUiState = ResultDownloadUiState.Failure(outcome.reason),
                        )
                    }
                }
            }
        }
    }

    private fun validateMatchForReview(match: Match) =
        match.finalizedParticipantResultsOrNull()?.let { participantResults ->
            validateMatchResult.validateParticipantResults(
                rows = participantResults.map { result ->
                    MatchResultRowInput(
                        teamSlotNumber = result.teamSlotNumber,
                        placement = result.placement?.toString(),
                        kills = result.kills.toString(),
                        participationStatus = result.participationStatus,
                    )
                },
                expectedTeamSlots = participantResults.map { result -> result.teamSlotNumber },
            )
        } ?: validateMatchResult(match)

    fun onDestinationLaunchHandled() {
        _uiState.update { state ->
            val requested = state.resultDownloadUiState as? ResultDownloadUiState.DestinationLaunchRequested
                ?: return@update state
            state.copy(
                resultDownloadUiState = ResultDownloadUiState.WaitingForDestination(
                    format = requested.format,
                    suggestedDisplayName = requested.suggestedDisplayName,
                ),
            )
        }
    }

    fun onDestinationLaunchFailed() {
        if (_uiState.value.resultDownloadUiState !is ResultDownloadUiState.WaitingForDestination) return
        pendingResultDocument = null
        _uiState.update {
            it.copy(
                resultDownloadUiState = ResultDownloadUiState.Failure(
                    ResultDownloadFailure.DESTINATION_LAUNCH_FAILED,
                ),
            )
        }
    }

    fun onDestinationResult(uri: Uri?) {
        val state = _uiState.value
        if (state.resultDownloadUiState !is ResultDownloadUiState.WaitingForDestination) return
        val pending = pendingResultDocument ?: return
        if (uri == null) {
            pendingResultDocument = null
            _uiState.update { it.copy(resultDownloadUiState = ResultDownloadUiState.Idle) }
            return
        }

        startDestinationWrite(uri, pending)
    }

    internal fun onDestinationResultForTesting() {
        val state = _uiState.value
        if (state.resultDownloadUiState !is ResultDownloadUiState.WaitingForDestination) return
        val pending = pendingResultDocument ?: return
        startDestinationWrite(null, pending)
    }

    private fun startDestinationWrite(
        uri: Uri?,
        pending: PendingResultDocument,
    ) {

        pendingResultDocument = null
        _uiState.update {
            it.copy(resultDownloadUiState = ResultDownloadUiState.Saving(pending.format))
        }
        resultDownloadJob = viewModelScope.launch {
            val writeResult = try {
                resultDocumentWriter.write(uri, pending.bytes)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ResultDocumentWriteResult.Failure(
                    com.hoggamers.rankforge.data.export.ResultDocumentWriteFailure.WRITE_FAILED,
                )
            }
            _uiState.update { current ->
                when (writeResult) {
                    ResultDocumentWriteResult.Success -> current.copy(
                        resultDownloadUiState = ResultDownloadUiState.Success(
                            format = pending.format,
                            userSelectedDestination = true,
                        ),
                    )
                    is ResultDocumentWriteResult.Failure -> current.copy(
                        resultDownloadUiState = ResultDownloadUiState.Failure(
                            ResultDownloadFailure.DESTINATION_WRITE_FAILED,
                        ),
                    )
                }
            }
        }
    }

    fun requestPhotoPicker(role: MatchResultScreenshotRole) {
        val current = _uiState.value
        val slot = current.resultScreenshots.slot(role)
        if (!current.isAvailable || current.resultScreenshotMultiPhotoPickerRequest != null ||
            current.resultScreenshots.any { it.isPhotoPickerRequestActive }
        ) return
        if (current.status == MatchStatus.FINALIZED) {
            _uiState.updateSlot(role) {
                it.copy(preservationError = ScreenshotPreservationError.FINALIZED_MATCH)
            }
            return
        }
        val isBusyOwnedByActiveBatch = activeResultBatchGeneration != null &&
            role in activeResultBatchRoles
        if (slot.isBusy && !isBusyOwnedByActiveBatch) return
        cancelActiveResultBatchAndClearTransientState()
        screenshotIntakeGeneration++
        _uiState.updateSlot(role) {
            it.copy(
                isPhotoPickerLaunchPending = true,
                isPhotoPickerRequestActive = true,
                photoPickerError = null,
                imageValidationError = null,
                duplicateError = null,
                duplicateInfo = null,
                preservationError = null,
            )
        }
        _uiState.update { it.copy(pendingResultScreenshotCropBatch = null) }
    }

    fun requestMultiPhotoPicker() {
        val current = _uiState.value
        if (!current.isAvailable || !current.isEditable) return
        if (current.resultScreenshotMultiPhotoPickerRequest != null ||
            current.resultScreenshots.any { it.isBusy }
        ) return
        val targetRoles = listOf(
            MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
        ).filter { role ->
            val slot = current.resultScreenshots.slot(role)
            !slot.hasLinkedAsset && slot.selectedScreenshotUri.isNullOrBlank()
        }.take(2)
        if (targetRoles.isEmpty()) return
        cancelActiveResultBatchAndClearTransientState()
        screenshotIntakeGeneration++
        val request = MatchResultScreenshotMultiPhotoPickerRequest(
            requestId = ++nextResultMultiPhotoPickerRequestId,
            targetRoles = targetRoles,
        )
        _uiState.update {
            it.copy(
                navigation = null,
                pendingResultScreenshotCropBatch = null,
                resultScreenshotMultiPhotoPickerRequest = request,
            )
        }
    }

    fun onPhotoPickerLaunchHandled(role: MatchResultScreenshotRole) {
        _uiState.updateSlot(role) {
            it.copy(isPhotoPickerLaunchPending = false)
        }
    }

    fun onPhotoPickerLaunchFailed(role: MatchResultScreenshotRole) {
        _uiState.updateSlot(role) {
            it.copy(
                isPhotoPickerLaunchPending = false,
                isPhotoPickerRequestActive = false,
                photoPickerError = PhotoPickerError.LAUNCH_FAILED,
            )
        }
    }

    fun onMultiPhotoPickerLaunchHandled(requestId: Long) {
        _uiState.update { state ->
            state.resultScreenshotMultiPhotoPickerRequest
                ?.takeIf { it.requestId == requestId }
                ?.let {
                    state.copy(resultScreenshotMultiPhotoPickerRequest = it.copy(isLaunchPending = false))
                }
                ?: state
        }
    }

    fun onMultiPhotoPickerLaunchFailed(requestId: Long) {
        _uiState.update { state ->
            val request = state.resultScreenshotMultiPhotoPickerRequest?.takeIf { it.requestId == requestId }
                ?: return@update state
            state.copy(
                resultScreenshotMultiPhotoPickerRequest = null,
                pendingResultScreenshotCropBatch = null,
                resultScreenshots = state.resultScreenshots.map { slot ->
                    if (slot.role in request.targetRoles) {
                        slot.copy(photoPickerError = PhotoPickerError.LAUNCH_FAILED)
                    } else slot
                },
            )
        }
    }

    fun onPhotoPickerResult(role: MatchResultScreenshotRole, selectedUri: String?) {
        if (selectedUri == null) {
            _uiState.updateSlot(role) {
                it.copy(
                    isPhotoPickerLaunchPending = false,
                    isPhotoPickerRequestActive = false,
                )
            }
            return
        }
        cancelActiveResultBatchAndClearTransientState()
        val generation = ++screenshotIntakeGeneration
        resultScreenshotJobs.remove(role)?.cancel()
        _uiState.update { it.copy(pendingResultScreenshotCropBatch = null) }
        if (selectedUri.isBlank()) {
            resultScreenshotJobs[role] = viewModelScope.launch {
                processResultScreenshotSelection(role, selectedUri, generation)
            }
            return
        }
        resultScreenshotJobs[role] = viewModelScope.launch {
            processResultScreenshotSelection(role, selectedUri, generation)
        }
    }

    fun onMultiPhotoPickerResult(selectedUris: List<String>) {
        val request = _uiState.value.resultScreenshotMultiPhotoPickerRequest ?: return
        val generation = ++screenshotIntakeGeneration
        cancelActiveResultBatchAndClearTransientState()
        _uiState.update { state ->
            if (state.resultScreenshotMultiPhotoPickerRequest?.requestId != request.requestId) {
                state
            } else {
                state.copy(
                    resultScreenshotMultiPhotoPickerRequest = null,
                    pendingResultScreenshotCropBatch = null,
                    resultScreenshots = state.resultScreenshots.map { slot ->
                        if (slot.role in request.targetRoles.take(selectedUris.size)) {
                            slot.copy(
                                isValidationInProgress = true,
                                imageValidationError = null,
                                duplicateError = null,
                                duplicateInfo = null,
                                preservationError = null,
                            )
                        } else slot
                    },
                )
            }
        }
        val assignments = request.targetRoles.zip(selectedUris.take(request.targetRoles.size))
        if (assignments.isEmpty()) return
        val targetRoles = assignments.map { it.first }.toSet()
        activeResultBatchGeneration = generation
        activeResultBatchRoles = targetRoles
        activeResultBatchSelectedUris = assignments.toMap()
        resultScreenshotBatchJob = viewModelScope.launch {
            try {
                val successfulRoles = buildList {
                    assignments.forEach { (role, uri) ->
                        if (processResultScreenshotSelection(
                                role = role,
                                selectedUri = uri,
                                generation = generation,
                                requestCropNavigation = false,
                            )
                        ) {
                            add(role)
                        }
                    }
                }
                if (generation == screenshotIntakeGeneration && successfulRoles.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            pendingResultScreenshotCropBatch = MatchResultScreenshotCropBatch(
                                currentRole = successfulRoles.first(),
                                remainingRoles = successfulRoles.drop(1),
                            ),
                            navigation = when (successfulRoles.first()) {
                                MatchResultScreenshotRole.MATCH_RESULT_UPPER ->
                                    MatchReviewNavigation.RESULT_SCREENSHOT_1_CROP
                                MatchResultScreenshotRole.MATCH_RESULT_LOWER ->
                                    MatchReviewNavigation.RESULT_SCREENSHOT_2_CROP
                            },
                        )
                    }
                }
            } finally {
                clearResultBatchTransientStateIfOwned(generation, targetRoles)
            }
        }
    }

    private suspend fun processResultScreenshotSelection(
        role: MatchResultScreenshotRole,
        selectedUri: String,
        generation: Long,
        requestCropNavigation: Boolean = true,
    ): Boolean {
        if (generation != screenshotIntakeGeneration) return false
        if (selectedUri.isBlank()) {
            _uiState.updateSlot(role) {
                it.copy(
                    selectedScreenshotUri = null,
                    selectedScreenshotMimeType = null,
                    selectedScreenshotWidth = null,
                    selectedScreenshotHeight = null,
                    isPhotoPickerLaunchPending = false,
                    isPhotoPickerRequestActive = false,
                    isValidationInProgress = false,
                    isSelectedScreenshotValidated = false,
                    imageValidationError = ImageValidationError.EMPTY_URI,
                    isDuplicateDetectionInProgress = false,
                    duplicateError = null,
                    duplicateInfo = null,
                    isPreservationInProgress = false,
                    preservationError = null,
                )
            }
            return false
        }
        _uiState.updateSlot(role) {
            it.copy(
                selectedScreenshotUri = selectedUri,
                selectedScreenshotMimeType = null,
                selectedScreenshotWidth = null,
                selectedScreenshotHeight = null,
                isPhotoPickerLaunchPending = false,
                isPhotoPickerRequestActive = false,
                photoPickerError = null,
                isValidationInProgress = true,
                isSelectedScreenshotValidated = false,
                imageValidationError = null,
                isDuplicateDetectionInProgress = false,
                duplicateError = null,
                duplicateInfo = null,
                isPreservationInProgress = false,
                preservationError = null,
            )
        }
        if (generation != screenshotIntakeGeneration) return false
        val validation = runCatching { imageCandidateValidator.validate(selectedUri) }
            .getOrElse { ImageCandidateValidationResult.Invalid(ImageValidationError.DECODE_FAILED) }
        val metadata = if (validation == ImageCandidateValidationResult.Valid) {
            runCatching { imageCandidateValidator.readValidMetadata(selectedUri) }.getOrNull()
        } else {
            null
        }
        if (generation != screenshotIntakeGeneration) return false
        if (validation is ImageCandidateValidationResult.Invalid || metadata == null) {
            _uiState.updateSlotIfCurrent(role, selectedUri) {
                it.copy(
                    isValidationInProgress = false,
                    isSelectedScreenshotValidated = false,
                    selectedScreenshotMimeType = null,
                    selectedScreenshotWidth = null,
                    selectedScreenshotHeight = null,
                    imageValidationError = (validation as? ImageCandidateValidationResult.Invalid)?.error
                        ?: ImageValidationError.DECODE_FAILED,
                )
            }
            return false
        }
        _uiState.updateSlotIfCurrent(role, selectedUri) {
            it.copy(
                isValidationInProgress = false,
                isSelectedScreenshotValidated = true,
                selectedScreenshotMimeType = metadata.mimeType,
                selectedScreenshotWidth = metadata.width,
                selectedScreenshotHeight = metadata.height,
                imageValidationError = null,
            )
        }
        return preserveValidatedResultScreenshot(
            role = role,
            selectedUri = selectedUri,
            metadata = metadata,
            generation = generation,
            requestCropNavigation = requestCropNavigation,
        )
    }

    private suspend fun preserveValidatedResultScreenshot(
        role: MatchResultScreenshotRole,
        selectedUri: String,
        metadata: ImageCandidateReadResult.Metadata,
        generation: Long,
        requestCropNavigation: Boolean = true,
    ): Boolean {
        if (generation != screenshotIntakeGeneration) return false
        val current = _uiState.value
        val tournamentId = current.tournamentId?.takeIf { it.isNotBlank() }
        val matchId = current.matchId?.takeIf { it.isNotBlank() }
        val identity = if (tournamentId != null && matchId != null) {
            MatchResultScreenshotIdentity(tournamentId = tournamentId, matchId = matchId, role = role)
        } else {
            null
        }
        val setupError = when {
            tournamentId == null -> ScreenshotPreservationError.MISSING_TOURNAMENT_ID
            matchId == null -> ScreenshotPreservationError.MISSING_MATCH_ID
            current.status == MatchStatus.FINALIZED -> ScreenshotPreservationError.FINALIZED_MATCH
            identity == null -> ScreenshotPreservationError.ROOM_WRITE_FAILED
            else -> null
        }
        if (setupError != null || identity == null) {
            _uiState.updateSlotIfCurrent(role, selectedUri) {
                it.copy(preservationError = setupError)
            }
            return false
        }
        val previousFingerprint = current.resultScreenshots.slot(role).fingerprint
        _uiState.updateSlotIfCurrent(role, selectedUri) {
            it.copy(
                isDuplicateDetectionInProgress = true,
                duplicateError = null,
                duplicateInfo = null,
                preservationError = null,
            )
        }
        val duplicateResult = matchResultScreenshotDuplicateDetector.link(
            identity = identity,
            selectedUri = selectedUri,
            currentFingerprint = previousFingerprint,
        )
        if (generation != screenshotIntakeGeneration) return false
        when (duplicateResult) {
            is MatchResultScreenshotDuplicateLinkResult.Linked -> {
                if (generation != screenshotIntakeGeneration) return false
                _uiState.updateSlotIfCurrent(role, selectedUri) {
                    it.copy(
                        isDuplicateDetectionInProgress = false,
                        isPreservationInProgress = true,
                    )
                }
                val preservation = localImagePreserver.preserveMatchResultScreenshot(
                    tournamentId = identity.tournamentId,
                    matchId = identity.matchId,
                    role = role,
                    selectedUri = selectedUri,
                )
                val preservedFile = when (preservation) {
                    is LocalImagePreservationResult.Preserved -> preservation.file
                    is LocalImagePreservationResult.PreservedWithCleanupFailure -> preservation.file
                    is LocalImagePreservationResult.Failed -> null
                }
                if (generation != screenshotIntakeGeneration) return false
                if (preservedFile == null) {
                    matchResultScreenshotDuplicateDetector.rollback(
                        identity = identity,
                        newFingerprint = duplicateResult.fingerprint,
                        previousFingerprint = previousFingerprint,
                    )
                    _uiState.updateSlotIfCurrent(role, selectedUri) {
                        it.copy(
                            isPreservationInProgress = false,
                            preservationError = (preservation as LocalImagePreservationResult.Failed).error.toUiError(),
                        )
                    }
                    return false
                }
                val assetResult = saveMatchResultScreenshotAsset(
                    identity = identity,
                    metadata = metadata,
                    preservedFile = preservedFile,
                    fingerprint = duplicateResult.fingerprint,
                    cleanupFailed = preservation is LocalImagePreservationResult.PreservedWithCleanupFailure,
                )
                if (generation != screenshotIntakeGeneration) return false
                if (assetResult !is MatchResultAssetWriteResult.Written) {
                    matchResultScreenshotDuplicateDetector.rollback(
                        identity = identity,
                        newFingerprint = duplicateResult.fingerprint,
                        previousFingerprint = previousFingerprint,
                    )
                    _uiState.updateSlotIfCurrent(role, selectedUri) {
                        it.copy(
                            isPreservationInProgress = false,
                            preservationError = ScreenshotPreservationError.ROOM_WRITE_FAILED,
                        )
                    }
                    return false
                }
                _uiState.updateSlotIfCurrent(role, selectedUri) {
                    (assetResult.asset.toSlotUiState(localImagePreserver) ?: it).copy(
                        selectedScreenshotUri = selectedUri,
                        linkedScreenshotUri = selectedUri,
                        selectedScreenshotMimeType = metadata.mimeType,
                        selectedScreenshotWidth = metadata.width,
                        selectedScreenshotHeight = metadata.height,
                        isSelectedScreenshotValidated = true,
                        isPreservationInProgress = false,
                        preservationError = if (
                            preservation is LocalImagePreservationResult.PreservedWithCleanupFailure
                        ) {
                            ScreenshotPreservationError.CLEANUP_FAILED
                        } else {
                            null
                        },
                        isUploadInProgress = false,
                        uploadError = null,
                    )
                }
                if (requestCropNavigation) {
                    requestResultScreenshotCropNavigationIfReady(
                        identity = identity,
                        selectedUri = selectedUri,
                    )
                }
                return true
            }

            MatchResultScreenshotDuplicateLinkResult.SameIdentity -> {
                _uiState.updateSlotIfCurrent(role, selectedUri) {
                    it.copy(
                        isDuplicateDetectionInProgress = false,
                        duplicateInfo = ScreenshotDuplicateInfo.ALREADY_LINKED_TO_THIS_MATCH,
                        duplicateError = null,
                    )
                }
                return false
            }

            is MatchResultScreenshotDuplicateLinkResult.LinkedToOtherIdentity -> {
                _uiState.updateSlotIfCurrent(role, selectedUri) {
                    it.copy(
                        isDuplicateDetectionInProgress = false,
                        duplicateError = ScreenshotDuplicateError.LINKED_TO_OTHER_MATCH,
                        duplicateInfo = null,
                    )
                }
                return false
            }

            MatchResultScreenshotDuplicateLinkResult.FingerprintFailure -> {
                _uiState.updateSlotIfCurrent(role, selectedUri) {
                    it.copy(
                        isDuplicateDetectionInProgress = false,
                        duplicateError = ScreenshotDuplicateError.FINGERPRINT_FAILED,
                        duplicateInfo = null,
                    )
                }
                return false
            }

            MatchResultScreenshotDuplicateLinkResult.StateConflict -> {
                _uiState.updateSlotIfCurrent(role, selectedUri) {
                    it.copy(
                        isDuplicateDetectionInProgress = false,
                        duplicateError = ScreenshotDuplicateError.STATE_CONFLICT,
                        duplicateInfo = null,
                    )
                }
                return false
            }
        }
    }

    private suspend fun saveMatchResultScreenshotAsset(
        identity: MatchResultScreenshotIdentity,
        metadata: ImageCandidateReadResult.Metadata,
        preservedFile: File,
        fingerprint: String,
        cleanupFailed: Boolean,
    ): MatchResultAssetWriteResult {
        val ownerUserId = screenshotOwnerProvider.currentOwnerUserId()
            ?: return MatchResultAssetWriteResult.Failed
        val relativePath = localImagePreserver.relativePathFor(preservedFile)
            ?: return MatchResultAssetWriteResult.Failed
        val extension = preservedFile.extension.lowercase()
        val mimeType = metadata.mimeType ?: mimeTypeForExtension(extension)
            ?: return MatchResultAssetWriteResult.Failed
        val byteSize = runCatching { preservedFile.length() }.getOrDefault(0L)
        if (byteSize <= 0L || fingerprint.length != 64 || fingerprint.any { it !in '0'..'9' && it !in 'a'..'f' }) {
            return MatchResultAssetWriteResult.Failed
        }
        val now = nowMillis()
        val previous = runCatching {
            matchResultScreenshotAssetRepository.getByIdentityAndOwner(
                identity,
                screenshotOwnerProvider.currentOwnerUserId().orEmpty(),
            )
        }.getOrNull()
        val sameBytes = previous?.sha256 == fingerprint
        val asset = MatchResultScreenshotAssetEntity(
            tournamentId = identity.tournamentId,
            matchId = identity.matchId,
            screenshotKind = OcrScreenshotKind.MATCH_RESULT.name,
            screenshotRole = identity.role.name,
            ownerUserId = ownerUserId,
            localRelativePath = relativePath,
            fileExtension = extension,
            mimeType = mimeType,
            originalWidth = metadata.width,
            originalHeight = metadata.height,
            byteSize = byteSize,
            sha256 = fingerprint,
            localStatus = if (cleanupFailed) {
                ScreenshotLocalStatus.CLEANUP_FAILED.name
            } else {
                ScreenshotLocalStatus.PRESERVED.name
            },
            uploadStatus = ScreenshotUploadStatus.PENDING.name,
            uploadFailureCode = null,
            storageBucket = null,
            storageObjectPath = null,
            cropProfileId = previous?.cropProfileId.takeIf { sameBytes },
            cropLeft = previous?.cropLeft.takeIf { sameBytes },
            cropTop = previous?.cropTop.takeIf { sameBytes },
            cropRight = previous?.cropRight.takeIf { sameBytes },
            cropBottom = previous?.cropBottom.takeIf { sameBytes },
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
            preservedAt = now,
            uploadedAt = null,
            revision = previous?.revision?.plus(1) ?: 1L,
        )
        return when (
            matchResultScreenshotAssetRepository.saveOrReplaceByOwner(
                asset,
                ownerUserId,
            )
        ) {
            MatchResultScreenshotAssetSaveResult.Saved -> MatchResultAssetWriteResult.Written(asset)
            else -> MatchResultAssetWriteResult.Failed
        }
    }

    private fun cancelActiveResultBatchAndClearTransientState() {
        val generation = activeResultBatchGeneration ?: return
        val targetRoles = activeResultBatchRoles
        val selectedUris = activeResultBatchSelectedUris
        resultScreenshotBatchJob?.cancel()
        clearResultBatchTransientStateIfOwned(generation, targetRoles, selectedUris)
    }

    private fun clearResultBatchTransientStateIfOwned(
        generation: Long,
        targetRoles: Set<MatchResultScreenshotRole>,
        selectedUris: Map<MatchResultScreenshotRole, String> = activeResultBatchSelectedUris,
    ) {
        if (activeResultBatchGeneration != generation) return
        activeResultBatchGeneration = null
        activeResultBatchRoles = emptySet()
        activeResultBatchSelectedUris = emptyMap()
        _uiState.update { state ->
            state.copy(
                resultScreenshots = state.resultScreenshots.map { slot ->
                    if (slot.role in targetRoles) {
                        val isOldUnlinkedCandidate = !slot.hasLinkedAsset &&
                            selectedUris[slot.role] == slot.selectedScreenshotUri
                        if (isOldUnlinkedCandidate) {
                            slot.copy(
                                selectedScreenshotUri = null,
                                selectedScreenshotMimeType = null,
                                selectedScreenshotWidth = null,
                                selectedScreenshotHeight = null,
                                isValidationInProgress = false,
                                isSelectedScreenshotValidated = false,
                                isDuplicateDetectionInProgress = false,
                                isPreservationInProgress = false,
                            )
                        } else {
                            slot.copy(
                                isValidationInProgress = false,
                                isDuplicateDetectionInProgress = false,
                                isPreservationInProgress = false,
                            )
                        }
                    } else {
                        slot
                    }
                },
            )
        }
    }

    private fun requestResultScreenshotCropNavigationIfReady(
        identity: MatchResultScreenshotIdentity,
        selectedUri: String,
    ) {
        _uiState.update { state ->
            val slot = state.resultScreenshots.slot(identity.role)
            val isCurrentSelection =
                slot.selectedScreenshotUri == selectedUri || slot.linkedScreenshotUri == selectedUri
            if (
                state.tournamentId != identity.tournamentId ||
                state.matchId != identity.matchId ||
                !state.isEditable ||
                !isCurrentSelection ||
                !slot.hasLinkedAsset ||
                slot.isBusy ||
                slot.isLocalFileMissing
            ) {
                state
            } else {
                state.copy(
                    navigation = when (identity.role) {
                        MatchResultScreenshotRole.MATCH_RESULT_UPPER ->
                            MatchReviewNavigation.RESULT_SCREENSHOT_1_CROP
                        MatchResultScreenshotRole.MATCH_RESULT_LOWER ->
                            MatchReviewNavigation.RESULT_SCREENSHOT_2_CROP
                    },
                )
            }
        }
    }

    private suspend fun uploadMatchResultScreenshot(
        identity: MatchResultScreenshotIdentity,
        selectedUri: String,
        preservedFile: File,
    ) {
        val result = try {
            matchResultScreenshotStorageUploader.upload(
                tournamentId = identity.tournamentId,
                matchId = identity.matchId,
                role = identity.role,
                localFile = preservedFile,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.UPLOAD_FAILED,
            )
        }
        when (result) {
            is MatchResultScreenshotStorageUploadResult.Uploaded -> {
                val uploadedAt = nowMillis()
                val updatedAsset = runCatching {
                    matchResultScreenshotAssetRepository.getByIdentityAndOwner(identity, screenshotOwnerProvider.currentOwnerUserId().orEmpty())?.copy(
                        storageBucket = OCR_SCREENSHOTS_BUCKET,
                        storageObjectPath = result.objectPath,
                        uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
                        uploadFailureCode = null,
                        uploadedAt = uploadedAt,
                        updatedAt = uploadedAt,
                    )?.let { asset ->
                        asset.copy(revision = asset.revision + 1)
                    }
                }.getOrNull()
                if (updatedAsset == null ||
                    matchResultScreenshotAssetRepository.saveOrReplaceByOwner(updatedAsset, screenshotOwnerProvider.currentOwnerUserId().orEmpty()) !is
                    MatchResultScreenshotAssetSaveResult.Saved
                ) {
                    setResultUploadError(identity, ScreenshotUploadError.UPLOAD_FAILED)
                    return
                }
                val cloudResult = runCatching {
                    matchResultScreenshotAssetCloudDataSource.upsert(updatedAsset)
                }.getOrElse {
                    MatchResultScreenshotAssetCloudResult.Failed(
                        MatchResultScreenshotAssetCloudFailure.WRITE_FAILED,
                    )
                }
                if (cloudResult is MatchResultScreenshotAssetCloudResult.Failed) {
                    val failureError = cloudResult.failure.toUiError()
                    updateResultUploadFailure(identity, failureError)
                    return
                }
                _uiState.updateSlotIfCurrentOrLinked(identity.role, selectedUri) {
                    (updatedAsset.toSlotUiState(localImagePreserver) ?: it).copy(
                        selectedScreenshotUri = it.selectedScreenshotUri,
                        selectedScreenshotMimeType = it.selectedScreenshotMimeType,
                        selectedScreenshotWidth = it.selectedScreenshotWidth,
                        selectedScreenshotHeight = it.selectedScreenshotHeight,
                        isSelectedScreenshotValidated = it.isSelectedScreenshotValidated,
                        linkedScreenshotUri = it.linkedScreenshotUri,
                        isUploadInProgress = false,
                        uploadError = null,
                    )
                }
            }

            is MatchResultScreenshotStorageUploadResult.Failed -> {
                updateResultUploadFailure(identity, result.failure.toUiError())
            }
        }
    }

    private suspend fun updateResultUploadFailure(
        identity: MatchResultScreenshotIdentity,
        error: ScreenshotUploadError,
    ) {
        val updatedAt = nowMillis()
        val updatedAsset = runCatching {
            matchResultScreenshotAssetRepository.getByIdentityAndOwner(identity, screenshotOwnerProvider.currentOwnerUserId().orEmpty())?.copy(
                uploadStatus = ScreenshotUploadStatus.FAILED.name,
                uploadFailureCode = error.name,
                updatedAt = updatedAt,
            )?.let { it.copy(revision = it.revision + 1) }
        }.getOrNull()
        if (updatedAsset != null) {
            runCatching {
                matchResultScreenshotAssetRepository.saveOrReplaceByOwner(
                    updatedAsset,
                    screenshotOwnerProvider.currentOwnerUserId().orEmpty(),
                )
            }
        }
        setResultUploadError(identity, error)
    }

    private fun setResultUploadError(
        identity: MatchResultScreenshotIdentity,
        error: ScreenshotUploadError,
    ) {
        _uiState.updateSlot(identity.role) {
            it.copy(
                isUploadInProgress = false,
                uploadStatus = ScreenshotMetadataUploadUiStatus.FAILED,
                uploadError = error,
            )
        }
    }

    fun removeResultScreenshot(role: MatchResultScreenshotRole) {
        val current = _uiState.value
        val tournamentId = current.tournamentId?.takeIf { it.isNotBlank() } ?: return
        val matchId = current.matchId?.takeIf { it.isNotBlank() } ?: return
        val slot = current.resultScreenshots.slot(role)

        if (!current.isEditable || !slot.hasLinkedAsset || slot.isBusy) return

        val identity = MatchResultScreenshotIdentity(
            tournamentId = tournamentId,
            matchId = matchId,
            role = role,
        )
        val fingerprint = slot.fingerprint
        val activeJob = resultScreenshotJobs.remove(role)

        _uiState.updateSlot(role) {
            it.copy(
                isPreservationInProgress = true,
                preservationError = null,
                isUploadInProgress = false,
                uploadError = null,
            )
        }

        resultScreenshotJobs[role] = viewModelScope.launch {
            activeJob?.cancel()
            activeJob?.join()

            when (
                localImagePreserver.cleanupMatchResultScreenshot(
                    tournamentId = tournamentId,
                    matchId = matchId,
                    role = role,
                )
            ) {
                LocalImageCleanupResult.Cleaned -> {
                    try {
                        matchResultScreenshotAssetRepository.deleteByIdentityAndOwner(
                            identity,
                            screenshotOwnerProvider.currentOwnerUserId().orEmpty(),
                        )
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        _uiState.updateSlot(role) {
                            it.copy(
                                isPreservationInProgress = false,
                                preservationError = ScreenshotPreservationError.ROOM_WRITE_FAILED,
                            )
                        }
                        return@launch
                    }

                    matchResultScreenshotDuplicateDetector.unlink(
                        identity = identity,
                        fingerprint = fingerprint,
                    )

                    restoredResultMissingMarked.remove(
                        "$tournamentId:$matchId:${role.name}",
                    )

                    _uiState.updateSlot(role) {
                        MatchResultScreenshotSlotUiState(role = role)
                    }

                    try {
                        matchResultScreenshotAssetCloudDataSource.deleteByIdentity(identity)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        // Local unlink remains authoritative. Cloud cleanup is best-effort.
                    }
                }

                LocalImageCleanupResult.Failed -> {
                    try {
                        matchResultScreenshotAssetRepository.markCleanupFailureByOwner(
                            identity = identity,
                            ownerUserId = screenshotOwnerProvider.currentOwnerUserId().orEmpty(),
                            updatedAt = nowMillis(),
                        )
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        Unit
                    }

                    _uiState.updateSlot(role) {
                        it.copy(
                            isPreservationInProgress = false,
                            localStatus = ScreenshotMetadataLocalUiStatus.CLEANUP_FAILED,
                            preservationError = ScreenshotPreservationError.CLEANUP_FAILED,
                        )
                    }
                }
            }
        }
    }
    fun retryResultScreenshotUpload(role: MatchResultScreenshotRole) {
        val current = _uiState.value
        val tournamentId = current.tournamentId?.takeIf { it.isNotBlank() } ?: return
        val matchId = current.matchId?.takeIf { it.isNotBlank() } ?: return
        val slot = current.resultScreenshots.slot(role)
        val relativePath = slot.localRelativePath ?: return
        val file = localImagePreserver.resolveRelativePath(relativePath) ?: return
        if (!current.isEditable || slot.isUploadInProgress || !slot.hasLinkedAsset) return
        resultScreenshotJobs.remove(role)?.cancel()
        _uiState.updateSlot(role) {
            it.copy(isUploadInProgress = true, uploadError = null)
        }
        val identity = MatchResultScreenshotIdentity(
            tournamentId = tournamentId,
            matchId = matchId,
            role = role,
        )
        resultScreenshotJobs[role] = viewModelScope.launch {
            uploadMatchResultScreenshot(
                identity = identity,
                selectedUri = slot.linkedScreenshotUri ?: slot.selectedScreenshotUri.orEmpty(),
                preservedFile = file,
            )
        }
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
            val ownerUserId = screenshotOwnerProvider.currentOwnerUserId().orEmpty()
            val result = screenshotDuplicateDetector.linkByOwner(
                tournamentId = tournamentId,
                matchId = matchId,
                selectedUri = candidateUri,
                currentFingerprint = current.linkedScreenshotFingerprint,
                ownerUserId = ownerUserId,
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
        val previous = runCatching {
            screenshotMetadataRepository.getByMatchIdAndOwner(matchId, screenshotOwnerProvider.currentOwnerUserId().orEmpty())
        }.getOrNull()
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
            val owner = screenshotOwnerProvider.currentOwnerUserId().orEmpty()
            if (owner.isBlank()) return MetadataWriteResult.Failed(ScreenshotPreservationError.ROOM_WRITE_FAILED)
            when (screenshotMetadataRepository.createOrReplaceByOwner(metadata, owner)) {
                com.hoggamers.rankforge.data.local.ScreenshotMetadataMutationResult.Saved -> Unit
                else -> return MetadataWriteResult.Failed(ScreenshotPreservationError.ROOM_WRITE_FAILED)
            }
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
            screenshotMetadataRepository.updateUploadSuccessByOwner(
                matchId = matchId,
                ownerUserId = screenshotOwnerProvider.currentOwnerUserId().orEmpty(),
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
        val updatedMetadata = runCatching {
            screenshotMetadataRepository.getByMatchIdAndOwner(matchId, screenshotOwnerProvider.currentOwnerUserId().orEmpty())
        }
            .getOrNull()
        val cloudPayload = updatedMetadata?.toCloudPayload()
        val cloudResult = if (cloudPayload != null) {
            runCatching {
                screenshotMetadataCloudDataSource.upsert(cloudPayload)
            }.getOrElse {
                ScreenshotMetadataCloudResult.Failed(ScreenshotMetadataCloudFailure.WRITE_FAILED)
            }
        } else {
            ScreenshotMetadataCloudResult.Failed(ScreenshotMetadataCloudFailure.WRITE_FAILED)
        }
        if (cloudResult is ScreenshotMetadataCloudResult.Failed) {
            runCatching {
                screenshotMetadataRepository.updateUploadFailureByOwner(
                    matchId = matchId,
                    ownerUserId = screenshotOwnerProvider.currentOwnerUserId().orEmpty(),
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
            screenshotMetadataRepository.updateUploadFailureByOwner(
                matchId = matchId,
                ownerUserId = screenshotOwnerProvider.currentOwnerUserId().orEmpty(),
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
                screenshotMetadataRepository.markLocalMissingByOwner(
                    matchId,
                    screenshotOwnerProvider.currentOwnerUserId().orEmpty(),
                    nowMillis(),
                )
            }
        }
    }

    private fun markRestoredResultMissingIfNeeded(state: MatchReviewUiState) {
        state.resultScreenshots
            .filter { it.isLocalFileMissing }
            .forEach { slot ->
                val tournamentId = state.tournamentId ?: return@forEach
                val matchId = state.matchId ?: return@forEach
                val key = "$tournamentId:$matchId:${slot.role.name}"
                if (!restoredResultMissingMarked.add(key)) return@forEach
                viewModelScope.launch {
                    runCatching {
                        matchResultScreenshotAssetRepository.markLocalMissingByOwner(
                            MatchResultScreenshotIdentity(
                                tournamentId = tournamentId,
                                matchId = matchId,
                                role = slot.role,
                            ),
                            screenshotOwnerProvider.currentOwnerUserId().orEmpty(),
                            nowMillis(),
                        )
                    }
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
                    runCatching {
                        screenshotMetadataRepository.deleteByMatchIdAndOwner(
                            matchId,
                            screenshotOwnerProvider.currentOwnerUserId().orEmpty(),
                        )
                    }
                    val cloudMatchId = MatchCloudIdentity.matchId(
                        tournamentId = tournamentId,
                        localMatchId = matchId,
                    )
                    val cloudDeleteResult = if (cloudMatchId != null) {
                        runCatching {
                            screenshotMetadataCloudDataSource.deleteByMatchId(cloudMatchId)
                        }.getOrDefault(ScreenshotMetadataCloudResult.Success)
                    } else {
                        ScreenshotMetadataCloudResult.Failed(
                            ScreenshotMetadataCloudFailure.WRITE_FAILED,
                        )
                    }
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
                    runCatching {
                        screenshotMetadataRepository.markCleanupFailureByOwner(
                            matchId,
                            screenshotOwnerProvider.currentOwnerUserId().orEmpty(),
                            nowMillis(),
                        )
                    }
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

    fun finalizeMatch() {
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
                is FinalizeMatchResult.Finalized -> {
                    _uiState.update {
                        it.copy(isFinalizing = false, finalizationError = null)
                    }
                    launchFinalizedMatchCloudSync(current.tournamentId)
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

    private fun launchFinalizedMatchCloudSync(tournamentId: String?) {
        tournamentId ?: return
        viewModelScope.launch {
            try {
                finalizedMatchCloudSync(tournamentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Local finalization remains authoritative when cloud sync fails.
            }
        }
    }
}

private data class MatchReviewLoadInputs(
    val matches: List<Match>,
    val slots: List<TeamSlot>,
    val rosters: Map<Int, List<RosterPlayer>>,
    val draftValues: Map<Int, MatchDraftFieldValues>,
)

private sealed interface MatchResultAssetWriteResult {
    data class Written(val asset: MatchResultScreenshotAssetEntity) : MatchResultAssetWriteResult
    data object Failed : MatchResultAssetWriteResult
}

private fun MutableStateFlow<MatchReviewUiState>.updateSlot(
    role: MatchResultScreenshotRole,
    transform: (MatchResultScreenshotSlotUiState) -> MatchResultScreenshotSlotUiState,
) {
    update { state ->
        state.copy(
            resultScreenshots = state.resultScreenshots.replaceSlot(role, transform),
        )
    }
}

private fun MutableStateFlow<MatchReviewUiState>.updateSlotIfCurrent(
    role: MatchResultScreenshotRole,
    selectedUri: String,
    transform: (MatchResultScreenshotSlotUiState) -> MatchResultScreenshotSlotUiState,
) {
    update { state ->
        if (state.resultScreenshots.slot(role).selectedScreenshotUri != selectedUri) {
            state
        } else {
            state.copy(
                resultScreenshots = state.resultScreenshots.replaceSlot(role, transform),
            )
        }
    }
}

private fun MutableStateFlow<MatchReviewUiState>.updateSlotIfCurrentOrLinked(
    role: MatchResultScreenshotRole,
    selectedUri: String,
    transform: (MatchResultScreenshotSlotUiState) -> MatchResultScreenshotSlotUiState,
) {
    update { state ->
        val slot = state.resultScreenshots.slot(role)
        if (
            selectedUri.isNotBlank() &&
            slot.selectedScreenshotUri != selectedUri &&
            slot.linkedScreenshotUri != selectedUri
        ) {
            state
        } else {
            state.copy(
                resultScreenshots = state.resultScreenshots.replaceSlot(role, transform),
            )
        }
    }
}

private fun List<MatchResultScreenshotAssetEntity>.toResultScreenshotSlots(
    localImagePreserver: LocalImagePreserver,
): List<MatchResultScreenshotSlotUiState> =
    defaultMatchResultScreenshotSlots().map { emptySlot ->
        firstOrNull { asset ->
            asset.screenshotRole == emptySlot.role.name &&
                asset.screenshotKind == OcrScreenshotKind.MATCH_RESULT.name
        }?.toSlotUiState(localImagePreserver) ?: emptySlot
    }

private fun MatchResultScreenshotAssetEntity.toSlotUiState(
    localImagePreserver: LocalImagePreserver,
): MatchResultScreenshotSlotUiState? {
    val role = runCatching { MatchResultScreenshotRole.valueOf(screenshotRole) }
        .getOrNull()
        ?: return null
    val local = runCatching { ScreenshotLocalStatus.valueOf(localStatus) }
        .getOrDefault(ScreenshotLocalStatus.MISSING)
    val upload = runCatching { ScreenshotUploadStatus.valueOf(uploadStatus) }
        .getOrDefault(ScreenshotUploadStatus.FAILED)
    val localFile = localImagePreserver.resolveRelativePath(localRelativePath)
    val fileIsPresent = localFile?.let { file ->
        runCatching { file.isFile && file.length() > 0L }.getOrDefault(false)
    } == true
    val effectiveLocal = if (fileIsPresent) local else ScreenshotLocalStatus.MISSING
    return MatchResultScreenshotSlotUiState(
        role = role,
        hasLinkedAsset = effectiveLocal != ScreenshotLocalStatus.MISSING,
        localRelativePath = localRelativePath,
        localPreviewUri = localFile?.takeIf { fileIsPresent }?.toURI()?.toString(),
        fingerprint = sha256,
        originalWidth = originalWidth,
        originalHeight = originalHeight,
        metadata = toMetadataUiState(),
        localStatus = when (effectiveLocal) {
            ScreenshotLocalStatus.PRESERVED -> ScreenshotMetadataLocalUiStatus.PRESERVED
            ScreenshotLocalStatus.CLEANUP_FAILED -> ScreenshotMetadataLocalUiStatus.CLEANUP_FAILED
            ScreenshotLocalStatus.MISSING -> ScreenshotMetadataLocalUiStatus.MISSING
        },
        uploadStatus = when (upload) {
            ScreenshotUploadStatus.PENDING -> ScreenshotMetadataUploadUiStatus.PENDING
            ScreenshotUploadStatus.UPLOADED -> ScreenshotMetadataUploadUiStatus.UPLOADED
            ScreenshotUploadStatus.FAILED -> ScreenshotMetadataUploadUiStatus.FAILED
        },
        uploadObjectPath = storageObjectPath,
        isLocalFileMissing = effectiveLocal == ScreenshotLocalStatus.MISSING,
        preservationError = if (effectiveLocal == ScreenshotLocalStatus.MISSING) {
            ScreenshotPreservationError.LOCAL_FILE_MISSING
        } else if (effectiveLocal == ScreenshotLocalStatus.CLEANUP_FAILED) {
            ScreenshotPreservationError.CLEANUP_FAILED
        } else {
            null
        },
        uploadError = if (upload == ScreenshotUploadStatus.FAILED) {
            uploadFailureCode?.toUploadUiError()
        } else {
            null
        },
        confirmedCrop = confirmedCropOrNull(),
        cropProfileId = cropProfileId,
    )
}

private fun MatchResultScreenshotAssetEntity.confirmedCropOrNull(): OcrNormalizedCropRect? {
    val left = cropLeft ?: return null
    val top = cropTop ?: return null
    val right = cropRight ?: return null
    val bottom = cropBottom ?: return null
    return runCatching {
        OcrNormalizedCropRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
        )
    }.getOrNull()
}

private fun MatchResultScreenshotAssetEntity.toMetadataUiState(): ScreenshotMetadataUiState =
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

private fun mergeResultScreenshotSlots(
    restored: List<MatchResultScreenshotSlotUiState>,
    current: List<MatchResultScreenshotSlotUiState>,
): List<MatchResultScreenshotSlotUiState> = defaultMatchResultScreenshotSlots().map { ordered ->
    val restoredSlot = restored.slot(ordered.role)
    val currentSlot = current.slot(ordered.role)
    val mergedSlot = if (currentSlot.isBusy || currentSlot.selectedScreenshotUri != null || currentSlot.imageValidationError != null) {
        restoredSlot.copy(
            selectedScreenshotUri = currentSlot.selectedScreenshotUri,
            selectedScreenshotMimeType = currentSlot.selectedScreenshotMimeType,
            selectedScreenshotWidth = currentSlot.selectedScreenshotWidth,
            selectedScreenshotHeight = currentSlot.selectedScreenshotHeight,
            isPhotoPickerLaunchPending = currentSlot.isPhotoPickerLaunchPending,
            isPhotoPickerRequestActive = currentSlot.isPhotoPickerRequestActive,
            photoPickerError = currentSlot.photoPickerError,
            isValidationInProgress = currentSlot.isValidationInProgress,
            isSelectedScreenshotValidated = currentSlot.isSelectedScreenshotValidated,
            imageValidationError = currentSlot.imageValidationError,
            isDuplicateDetectionInProgress = currentSlot.isDuplicateDetectionInProgress,
            duplicateError = currentSlot.duplicateError,
            duplicateInfo = currentSlot.duplicateInfo,
            isPreservationInProgress = currentSlot.isPreservationInProgress,
            preservationError = currentSlot.preservationError ?: restoredSlot.preservationError,
        )
    } else {
        restoredSlot
    }
    mergedSlot.copy(localPreviewUri = currentSlot.localPreviewUri ?: mergedSlot.localPreviewUri)
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

private fun MatchResultScreenshotStorageUploadFailure.toUiError(): ScreenshotUploadError = when (this) {
    MatchResultScreenshotStorageUploadFailure.MISSING_AUTH_SESSION -> ScreenshotUploadError.MISSING_AUTH_SESSION
    MatchResultScreenshotStorageUploadFailure.MISSING_LOCAL_FILE -> ScreenshotUploadError.MISSING_LOCAL_FILE
    MatchResultScreenshotStorageUploadFailure.MISSING_TOURNAMENT_ID -> ScreenshotUploadError.MISSING_TOURNAMENT_ID
    MatchResultScreenshotStorageUploadFailure.MISSING_MATCH_ID -> ScreenshotUploadError.MISSING_MATCH_ID
    MatchResultScreenshotStorageUploadFailure.INVALID_ROLE -> ScreenshotUploadError.UPLOAD_FAILED
    MatchResultScreenshotStorageUploadFailure.CLOUD_MATCH_ID_UNAVAILABLE -> ScreenshotUploadError.UPLOAD_FAILED
    MatchResultScreenshotStorageUploadFailure.UNSUPPORTED_FORMAT -> ScreenshotUploadError.UNSUPPORTED_FORMAT
    MatchResultScreenshotStorageUploadFailure.LOCAL_FILE_READ_FAILED -> ScreenshotUploadError.LOCAL_FILE_READ_FAILED
    MatchResultScreenshotStorageUploadFailure.NETWORK -> ScreenshotUploadError.NETWORK
    MatchResultScreenshotStorageUploadFailure.AUTHORIZATION -> ScreenshotUploadError.AUTHORIZATION
    MatchResultScreenshotStorageUploadFailure.UPLOAD_FAILED -> ScreenshotUploadError.UPLOAD_FAILED
}

private fun MatchResultScreenshotAssetCloudFailure.toUiError(): ScreenshotUploadError = when (this) {
    MatchResultScreenshotAssetCloudFailure.MISSING_AUTH_SESSION -> ScreenshotUploadError.MISSING_AUTH_SESSION
    MatchResultScreenshotAssetCloudFailure.AUTHORIZATION -> ScreenshotUploadError.RLS_DENIED
    else -> ScreenshotUploadError.CLOUD_METADATA_WRITE_FAILED
}

private data class PendingResultDocument(
    val format: ResultExportFileFormat,
    val displayName: String,
    val bytes: ByteArray,
)

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

private fun ScreenshotMetadataEntity.toCloudPayload(): ScreenshotMetadataCloudPayload? {
    val cloudMatchId = MatchCloudIdentity.matchId(
        tournamentId = tournamentId,
        localMatchId = matchId,
    ) ?: return null

    return ScreenshotMetadataCloudPayload(
        matchId = cloudMatchId,
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
}

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
