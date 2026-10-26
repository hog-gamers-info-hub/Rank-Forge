package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.data.local.MatchCalculatedEvidenceOrigin
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.NoOpMatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.ocr.MatchOcrCacheAvailability
import com.hoggamers.rankforge.data.ocr.MatchOcrCacheReadResult
import com.hoggamers.rankforge.data.ocr.MatchOcrCacheReader
import com.hoggamers.rankforge.data.ocr.NoOpMatchOcrCacheReader
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewProcessingResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRoleResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrPlayer
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrSlot
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrScreenshotResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewResult
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchFailure
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchInput
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchResult
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchWarning
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionRowInput
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.PreservedMatchOcrEvidence
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.MatchEligibleTeamSlotResolver
import com.hoggamers.rankforge.domain.tournament.MatchTeamIdentityContext
import com.hoggamers.rankforge.domain.tournament.MatchTeamIdentityContextReadResult
import com.hoggamers.rankforge.domain.tournament.ReadMatchTeamIdentityContextUseCase
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchDraftReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchDraftState
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchDraftMutationResult
import com.hoggamers.rankforge.domain.tournament.FinalizeGroupRotationMatchInput
import com.hoggamers.rankforge.domain.tournament.FinalizeGroupRotationMatchUseCase
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchFinalizationFailure
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchFinalizationIssueCode
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchFinalizationResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchFinalizationValidator
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationMatchParticipantResultsUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.data.ocr.matchlobby.GroupRotationMatchOcrIdentityAdapter
import com.hoggamers.rankforge.data.ocr.matchlobby.GroupRotationMatchOcrSuggestionResult
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.matching.TeamCandidateRosterInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult

private const val UNEXPECTED_OCR_CALCULATION_ERROR =
    "Unable to calculate results. Please try again or enter results manually."

@HiltViewModel
class MatchOcrReviewViewModel @Inject constructor(
    private val finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
    private val matchResultOcrPreviewRunner: MatchResultOcrPreviewRunner,
    private val matchLobbyPlayersOcrRunner: MatchLobbyPlayersOcrRunner,
    private val matchLobbySlotNumberOcrRunner: MatchLobbySlotNumberOcrRunner,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeRoster: ObserveRosterByTournamentUseCase,
    private val finalizedMatchCloudSync: FinalizedMatchCloudSyncAction =
        FinalizedMatchCloudSyncAction {
            QueueAwareActionResult(
                primaryResult = FinalizedMatchCloudSyncResult.ValidationFailure,
                queueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
            )
        },
    private val tournamentRepository: TournamentRepository = NO_OP_MATCHING_REPOSITORY,
    private val lobbyScreenshotAssetRepository: MatchLobbyScreenshotAssetRepository =
        NoOpMatchLobbyScreenshotAssetRepository(),
    private val matchOcrCacheReader: MatchOcrCacheReader = NoOpMatchOcrCacheReader,
    private val screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
    private val readMatchTeamIdentityContext: ReadMatchTeamIdentityContextUseCase =
        ReadMatchTeamIdentityContextUseCase(),
    private val observeGroupRotationMatchDraft: com.hoggamers.rankforge.domain.tournament.ObserveGroupRotationMatchDraftUseCase? = null,
    private val saveGroupRotationMatchParticipantResults: SaveGroupRotationMatchParticipantResultsUseCase? = null,
    private val finalizeGroupRotationMatch: FinalizeGroupRotationMatchUseCase? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MatchOcrReviewUiState>(MatchOcrReviewUiState.Loading)
    val uiState: StateFlow<MatchOcrReviewUiState> = _uiState.asStateFlow()
    private val _cacheAvailability = MutableStateFlow(MatchOcrCacheAvailability.UNKNOWN)
    val cacheAvailability: StateFlow<MatchOcrCacheAvailability> = _cacheAvailability.asStateFlow()

    private var loadedMatchKey: String? = null
    private var previewJob: Job? = null
    private var cacheLoadJob: Job? = null

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
    ) : this(
        finalizeOcrCorrectionMatch,
        NO_OP_MATCH_RESULT_OCR_PREVIEW_RUNNER,
        NO_OP_MATCH_LOBBY_PLAYERS_OCR_RUNNER,
        NO_OP_MATCH_LOBBY_SLOT_NUMBER_OCR_RUNNER,
        NO_OP_OBSERVE_TOURNAMENT_SLOTS,
        NO_OP_OBSERVE_ROSTER,
    )

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
        finalizedMatchCloudSync: FinalizedMatchCloudSyncAction,
        initialUiState: MatchOcrReviewUiState,
        screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
        observeGroupRotationMatchDraft: com.hoggamers.rankforge.domain.tournament.ObserveGroupRotationMatchDraftUseCase? = null,
        saveGroupRotationMatchParticipantResults: SaveGroupRotationMatchParticipantResultsUseCase? = null,
        finalizeGroupRotationMatch: FinalizeGroupRotationMatchUseCase? = null,
    ) : this(
        finalizeOcrCorrectionMatch,
        NO_OP_MATCH_RESULT_OCR_PREVIEW_RUNNER,
        NO_OP_MATCH_LOBBY_PLAYERS_OCR_RUNNER,
        NO_OP_MATCH_LOBBY_SLOT_NUMBER_OCR_RUNNER,
        NO_OP_OBSERVE_TOURNAMENT_SLOTS,
        NO_OP_OBSERVE_ROSTER,
        finalizedMatchCloudSync,
        screenshotOwnerProvider = screenshotOwnerProvider,
        observeGroupRotationMatchDraft = observeGroupRotationMatchDraft,
        saveGroupRotationMatchParticipantResults = saveGroupRotationMatchParticipantResults,
        finalizeGroupRotationMatch = finalizeGroupRotationMatch,
    ) {
        _uiState.value = initialUiState
    }

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
        matchResultOcrPreviewRunner: MatchResultOcrPreviewRunner,
        initialUiState: MatchOcrReviewUiState,
        screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
    ) : this(
        finalizeOcrCorrectionMatch,
        matchResultOcrPreviewRunner,
        NO_OP_MATCH_LOBBY_PLAYERS_OCR_RUNNER,
        NO_OP_MATCH_LOBBY_SLOT_NUMBER_OCR_RUNNER,
        NO_OP_OBSERVE_TOURNAMENT_SLOTS,
        NO_OP_OBSERVE_ROSTER,
        screenshotOwnerProvider = screenshotOwnerProvider,
    ) {
        _uiState.value = initialUiState
    }

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
        matchOcrCacheReader: MatchOcrCacheReader,
        initialUiState: MatchOcrReviewUiState = MatchOcrReviewUiState.Loading,
        screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
    ) : this(
        finalizeOcrCorrectionMatch = finalizeOcrCorrectionMatch,
        matchResultOcrPreviewRunner = NO_OP_MATCH_RESULT_OCR_PREVIEW_RUNNER,
        matchLobbyPlayersOcrRunner = NO_OP_MATCH_LOBBY_PLAYERS_OCR_RUNNER,
        matchLobbySlotNumberOcrRunner = NO_OP_MATCH_LOBBY_SLOT_NUMBER_OCR_RUNNER,
        observeTournamentSlots = NO_OP_OBSERVE_TOURNAMENT_SLOTS,
        observeRoster = NO_OP_OBSERVE_ROSTER,
        matchOcrCacheReader = matchOcrCacheReader,
        screenshotOwnerProvider = screenshotOwnerProvider,
    ) {
        _uiState.value = initialUiState
    }

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
        matchResultOcrPreviewRunner: MatchResultOcrPreviewRunner,
        observeTournamentSlots: ObserveTournamentSlotsUseCase,
        observeRoster: ObserveRosterByTournamentUseCase,
        initialUiState: MatchOcrReviewUiState,
        screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
    ) : this(
        finalizeOcrCorrectionMatch,
        matchResultOcrPreviewRunner,
        NO_OP_MATCH_LOBBY_PLAYERS_OCR_RUNNER,
        NO_OP_MATCH_LOBBY_SLOT_NUMBER_OCR_RUNNER,
        observeTournamentSlots,
        observeRoster,
        screenshotOwnerProvider = screenshotOwnerProvider,
    ) {
        _uiState.value = initialUiState
    }

    internal constructor(
        finalizeOcrCorrectionMatch: FinalizeOcrCorrectionMatchUseCase,
        matchResultOcrPreviewRunner: MatchResultOcrPreviewRunner,
        matchLobbyPlayersOcrRunner: MatchLobbyPlayersOcrRunner,
        matchLobbySlotNumberOcrRunner: MatchLobbySlotNumberOcrRunner = NO_OP_MATCH_LOBBY_SLOT_NUMBER_OCR_RUNNER,
        observeTournamentSlots: ObserveTournamentSlotsUseCase,
        observeRoster: ObserveRosterByTournamentUseCase,
        initialUiState: MatchOcrReviewUiState,
        tournamentRepository: TournamentRepository = NO_OP_MATCHING_REPOSITORY,
        screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
        readMatchTeamIdentityContext: ReadMatchTeamIdentityContextUseCase =
            ReadMatchTeamIdentityContextUseCase(),
    ) : this(
        finalizeOcrCorrectionMatch = finalizeOcrCorrectionMatch,
        matchResultOcrPreviewRunner = matchResultOcrPreviewRunner,
        matchLobbyPlayersOcrRunner = matchLobbyPlayersOcrRunner,
        matchLobbySlotNumberOcrRunner = matchLobbySlotNumberOcrRunner,
        observeTournamentSlots = observeTournamentSlots,
        observeRoster = observeRoster,
        tournamentRepository = tournamentRepository,
        screenshotOwnerProvider = screenshotOwnerProvider,
        readMatchTeamIdentityContext = readMatchTeamIdentityContext,
    ) {
        _uiState.value = initialUiState
    }

    fun load(
        tournamentId: String,
        matchId: String,
        useSlotNumberOnlyLobbyOcr: Boolean = false,
    ) {
        val matchKey = "$tournamentId:$matchId"
        if (loadedMatchKey == matchKey) return
        loadedMatchKey = matchKey
        startOcrProcessing(
            tournamentId = tournamentId,
            matchId = matchId,
            allowIncompleteEvidence = false,
            useSlotNumberOnlyLobbyOcr = useSlotNumberOnlyLobbyOcr,
        )
    }

    /** Explicit user-triggered refresh; unlike load(), this always reruns current screenshot evidence. */
    fun reprocess(
        tournamentId: String,
        matchId: String,
        allowIncompleteEvidence: Boolean,
        useSlotNumberOnlyLobbyOcr: Boolean = false,
    ) {
        loadedMatchKey = "$tournamentId:$matchId"
        startOcrProcessing(
            tournamentId = tournamentId,
            matchId = matchId,
            allowIncompleteEvidence = allowIncompleteEvidence,
            useSlotNumberOnlyLobbyOcr = useSlotNumberOnlyLobbyOcr,
        )
    }

    fun openManualReview(
        tournamentId: String,
        matchId: String,
    ) {
        cacheLoadJob?.cancel()
        previewJob?.cancel()

        val manualKey = "$tournamentId:$matchId:manual"
        loadedMatchKey = manualKey
        _cacheAvailability.value = MatchOcrCacheAvailability.NOT_AVAILABLE
        _uiState.value = MatchOcrReviewUiState.Loading
        cacheLoadJob = viewModelScope.launch {
            val teamContext = loadTeamContext(tournamentId, matchId)
            if (loadedMatchKey != manualKey) return@launch
            teamContext.identityFailure?.let { failure ->
                _uiState.value = MatchOcrReviewUiState.Error(
                    tournamentId = tournamentId,
                    matchId = matchId,
                    message = failure.toOcrReviewMessage(),
                )
                return@launch
            }
            val ready = completeManualFallbackReviewState(
                tournamentId = tournamentId,
                matchId = matchId,
                preview = MatchResultOcrPreviewUiState.NotRequested,
                teamNamesBySlot = teamContext.teamNamesBySlot,
                lobbyPlayers = emptyList(),
                phase1LobbySlotNumberOcr = null,
                calculationOrigin = MatchCalculatedEvidenceOrigin.MANUAL,
                eligibleTeamSlots = teamContext.eligibleTeamSlots,
                lobbySlotByTeamSlot = teamContext.lobbySlotByTeamSlot,
                usesPairRelativeIdentity = teamContext.usesPairRelativeIdentity,
            )
            val expectedPlacementByRowIndex = ready.rows.associate { row ->
                row.rowIndex to row.expectedPlacementLabel
            }
            val seededDraft = ready.correctionDraft?.let { draft ->
                MatchOcrReviewCorrectionDraftReducer.validate(
                    draft.copy(
                        rows = draft.rows.map { draftRow ->
                            val expectedPlacement =
                                expectedPlacementByRowIndex[draftRow.rowIndex].orEmpty()
                            draftRow.copy(
                                originalPlacementValue = expectedPlacement,
                                placementDraftValue = expectedPlacement,
                            )
                        },
                    ),
                )
            }
            _uiState.value = ready.copy(
                correctionDraft = seededDraft,
                isUuidBackedGroupRotation = teamContext.uuidSnapshot != null,
            )
        }
    }

    private fun startOcrProcessing(
        tournamentId: String,
        matchId: String,
        allowIncompleteEvidence: Boolean,
        useSlotNumberOnlyLobbyOcr: Boolean,
    ) {
        cacheLoadJob?.cancel()

        previewJob?.cancel()
        _uiState.value = MatchOcrReviewUiState.Calculating(
            tournamentId = tournamentId,
            matchId = matchId,
        )
        _cacheAvailability.value = MatchOcrCacheAvailability.UNKNOWN
        val newPreviewJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
            val teamContext = loadTeamContext(tournamentId, matchId)
            teamContext.identityFailure?.let { failure ->
                _uiState.value = MatchOcrReviewUiState.Error(
                    tournamentId = tournamentId,
                    matchId = matchId,
                    message = failure.toOcrReviewMessage(),
                )
                return@launch
            }
            val (roleResults, lobbyOutcome) = coroutineScope {
                val resultOcr = async {
                    MatchResultScreenshotRole.entries.map { role ->
                        val result = matchResultOcrPreviewRunner.process(
                            MatchResultScreenshotIdentity(
                                tournamentId = tournamentId,
                                matchId = matchId,
                                role = role,
                            ),
                        )
                        MatchResultOcrPreviewRoleResult(
                            role = (result as? MatchResultOcrPreviewProcessingResult.Processed)
                                ?.extraction
                                ?.role
                                ?: role,
                            result = result,
                        )
                    }
                }
                val lobbyOcr = async {
                    if (useSlotNumberOnlyLobbyOcr) {
                        SlotNumberOnlyLobbyOutcome(
                            processSlotNumberOnlyLobbyOcr(tournamentId, matchId),
                        )
                    } else {
                        PlayerLobbyOutcome(
                            try {
                                matchLobbyPlayersOcrRunner.process(tournamentId, matchId)
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (_: Throwable) {
                                MatchLobbyPlayersOcrResult.unavailable()
                            },
                        )
                    }
                }
                resultOcr.await() to lobbyOcr.await()
            }
            val preview = mapPreviewResults(roleResults)
            val slotNumberResult = (lobbyOutcome as? SlotNumberOnlyLobbyOutcome)?.result
            val playerLobbyResult = (lobbyOutcome as? PlayerLobbyOutcome)?.result
            val lobbyResultForMatching = playerLobbyResult
                ?: slotNumberResult?.toMatchLobbyPlayersOcrResult()
            val matchedRows = lobbyResultForMatching?.let { lobbyResult ->
                if (teamContext.uuidSnapshot != null && teamContext.tournament != null) {
                    mapUuidBackedSuggestions(
                        tournament = teamContext.tournament,
                        matchId = matchId,
                        snapshot = teamContext.uuidSnapshot,
                        preview = preview,
                        resultRows = roleResults.processedResultRows(),
                        lobbyOcrResult = lobbyResult,
                    )
                } else {
                    MatchResultOcrPreviewTeamSuggestionMapper.map(
                        preview = preview,
                        resultRows = roleResults.processedResultRows(),
                        lobbyOcrResult = lobbyResult,
                        eligibleTeamSlots = teamContext.eligibleTeamSlots,
                        matchIdentityContext = teamContext.identityContext,
                    )
                }
            }
            val lobbyPlayers = playerLobbyResult
                ?.takeIf { lobbyResult ->
                    lobbyResult.hasLobbyOcrEvidence(
                        matchId,
                        lobbyScreenshotAssetRepository,
                        screenshotOwnerProvider.currentOwnerUserId(),
                    )
                }
                ?.toUiState()
                .orEmpty()
            _uiState.update { state ->
                val reviewState = if (
                    allowIncompleteEvidence || roleResults.all {
                        it.result is MatchResultOcrPreviewProcessingResult.Processed
                    }
                ) {
                    completeReviewStateFromPreview(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        preview = preview,
                        reviewRows = matchedRows,
                        teamNamesBySlot = teamContext.teamNamesBySlot,
                        lobbyPlayers = lobbyPlayers,
                        phase1LobbySlotNumberOcr = slotNumberResult,
                        eligibleTeamSlots = teamContext.eligibleTeamSlots,
                        lobbySlotByTeamSlot = teamContext.lobbySlotByTeamSlot,
                        usesPairRelativeIdentity = teamContext.usesPairRelativeIdentity,
                    )
                } else {
                    null
                } ?: if (allowIncompleteEvidence) {
                    completeManualFallbackReviewState(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        preview = preview,
                        teamNamesBySlot = teamContext.teamNamesBySlot,
                        lobbyPlayers = lobbyPlayers,
                        phase1LobbySlotNumberOcr = slotNumberResult,
                        calculationOrigin = MatchCalculatedEvidenceOrigin.AUTOMATIC,
                        eligibleTeamSlots = teamContext.eligibleTeamSlots,
                        lobbySlotByTeamSlot = teamContext.lobbySlotByTeamSlot,
                        usesPairRelativeIdentity = teamContext.usesPairRelativeIdentity,
                    )
                } else {
                    null
                }
                when (state) {
                    is MatchOcrReviewUiState.Calculating -> reviewState?.copy(
                        isUuidBackedGroupRotation = teamContext.uuidSnapshot != null,
                    ) ?: MatchOcrReviewUiState.Empty(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        matchResultOcrPreview = preview,
                        teamNamesBySlot = teamContext.teamNamesBySlot,
                        eligibleTeamSlots = teamContext.eligibleTeamSlots,
                        lobbySlotByTeamSlot = teamContext.lobbySlotByTeamSlot,
                        usesPairRelativeIdentity = teamContext.usesPairRelativeIdentity,
                        lobbyPlayers = lobbyPlayers,
                        isUuidBackedGroupRotation = teamContext.uuidSnapshot != null,
                    )
                    is MatchOcrReviewUiState.Empty -> {
                        if (state.tournamentId == tournamentId && state.matchId == matchId) {
                            reviewState?.copy(
                                isUuidBackedGroupRotation = teamContext.uuidSnapshot != null,
                                matchResultOcrPreview = preview,
                                teamNamesBySlot = teamContext.teamNamesBySlot,
                                lobbySlotByTeamSlot = teamContext.lobbySlotByTeamSlot,
                                usesPairRelativeIdentity = teamContext.usesPairRelativeIdentity,
                            ) ?: state
                        } else {
                            state
                        }
                    }
                    is MatchOcrReviewUiState.Ready -> reviewState?.copy(
                        isUuidBackedGroupRotation = teamContext.uuidSnapshot != null,
                        matchResultOcrPreview = preview,
                        teamNamesBySlot = teamContext.teamNamesBySlot,
                        lobbySlotByTeamSlot = teamContext.lobbySlotByTeamSlot,
                        usesPairRelativeIdentity = teamContext.usesPairRelativeIdentity,
                    ) ?: state
                    is MatchOcrReviewUiState.Error -> state.copy(matchResultOcrPreview = preview)
                    MatchOcrReviewUiState.Loading -> state
                }
            }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                val calculationJob = currentCoroutineContext()[Job]
                val state = _uiState.value
                if (
                    calculationJob?.isActive == true &&
                    previewJob === calculationJob &&
                    loadedMatchKey == "$tournamentId:$matchId" &&
                    state is MatchOcrReviewUiState.Calculating &&
                    state.tournamentId == tournamentId &&
                    state.matchId == matchId
                ) {
                    _uiState.value = MatchOcrReviewUiState.Error(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        message = UNEXPECTED_OCR_CALCULATION_ERROR,
                    )
                }
            }
        }
        previewJob = newPreviewJob
        newPreviewJob.start()
    }

    private suspend fun processSlotNumberOnlyLobbyOcr(
        tournamentId: String,
        matchId: String,
    ): MatchLobbySlotNumberOcrResult = try {
        matchLobbySlotNumberOcrRunner.process(tournamentId, matchId)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        MatchLobbySlotNumberOcrResult(
            com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition.entries.map { position ->
                MatchLobbySlotNumberOcrScreenshotResult.Unavailable(
                    screenshotPosition = position,
                    reason = com.hoggamers.rankforge.data.ocr.matchlobby
                        .MatchLobbySlotNumberOcrUnavailableReason.EXTRACTION_FAILED,
                )
            },
        )
    }

    private fun mapUuidBackedSuggestions(
        tournament: Tournament,
        matchId: String,
        snapshot: GroupRotationMatchIdentitySnapshot,
        preview: MatchResultOcrPreviewUiState,
        resultRows: List<com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow>,
        lobbyOcrResult: com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrResult,
    ): List<MatchOcrReviewRowUiState>? {
        val rows = MatchResultOcrPreviewUiStateMapper.toReviewRows(preview) ?: return null
        val projection = when (
            val projected = GroupRotationMatchOcrIdentityAdapter.project(
                tournament = tournament,
                matchId = matchId,
                identitySnapshot = snapshot,
                lobbyOcrResult = lobbyOcrResult,
            )
        ) {
            is com.hoggamers.rankforge.data.ocr.matchlobby.GroupRotationMatchOcrIdentityProjectionResult.Invalid ->
                return null
            is com.hoggamers.rankforge.data.ocr.matchlobby.GroupRotationMatchOcrIdentityProjectionResult.Loaded ->
                projected.projection
        }
        val suggestions = when (val suggested = GroupRotationMatchOcrIdentityAdapter.suggest(projection, resultRows)) {
            is GroupRotationMatchOcrSuggestionResult.Invalid -> return null
            is GroupRotationMatchOcrSuggestionResult.Loaded -> suggested.suggestions.associateBy { it.resultPosition }
        }
        return rows.map { row ->
            val suggestion = suggestions[row.rowIndex + 1] ?: return@map row
            val proposedLobby = suggestion.proposedLobbySlotNumber
            val automaticLobby = suggestion.automaticLobbySlotNumber
            val blockers = row.blockerLabels
                .filterNot { it.startsWith("Team assignment:") }
                .toMutableList()
            if (automaticLobby == null) {
                blockers += "Team assignment: manual required"
            }
            row.copy(
                suggestedTeamSlotDisplayValue = proposedLobby?.toString() ?: "Unavailable",
                teamIdentityDisplayValue = (automaticLobby ?: proposedLobby)?.let { lobby ->
                    val name = snapshot.teams.firstOrNull { it.lobbySlotNumber == lobby }?.displayName
                    "Lobby ${lobby.toString().padStart(2, '0')} · ${name.orEmpty()}"
                },
                originalSuggestedTeamSlot = automaticLobby,
                confidenceScoreDisplayValue = suggestion.winningVotePercent?.toString() ?: "Unavailable",
                confidenceTierLabel = if (automaticLobby == null) "Manual required" else "Automatic candidate",
                assignmentSafetyStatusLabel = if (automaticLobby == null) "Manual required" else "Safe automatic assignment",
                blockerLabels = blockers.distinct(),
                severity = if (blockers.isEmpty()) {
                    MatchOcrReviewSeverity.INFORMATIONAL
                } else {
                    MatchOcrReviewSeverity.BLOCKING
                },
            )
        }
    }

    fun loadCached(tournamentId: String, matchId: String) {
        cacheLoadJob?.cancel()
        cacheLoadJob = viewModelScope.launch {
            val cached = try {
                matchOcrCacheReader.read(tournamentId, matchId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            val availability = cached?.availability ?: MatchOcrCacheAvailability.NOT_AVAILABLE
            _cacheAvailability.value = availability
            val teamContext = loadTeamContext(tournamentId, matchId)
            teamContext.identityFailure?.let { failure ->
                _uiState.value = MatchOcrReviewUiState.Error(
                    tournamentId = tournamentId,
                    matchId = matchId,
                    message = failure.toOcrReviewMessage(),
                )
                return@launch
            }
            val hasCompleteResultOcr = cached?.hasCompleteResultOcr() == true
            if (cached == null ||
                (availability != MatchOcrCacheAvailability.READY && !hasCompleteResultOcr)
            ) {
                _uiState.update { state ->
                    when (state) {
                        is MatchOcrReviewUiState.Ready -> state
                        is MatchOcrReviewUiState.Empty -> {
                            if (state.tournamentId == tournamentId && state.matchId == matchId) {
                                state
                            } else {
                                state
                            }
                        }
                        is MatchOcrReviewUiState.Error,
                        is MatchOcrReviewUiState.Calculating,
                        MatchOcrReviewUiState.Loading,
                        -> state
                    }
                }
                return@launch
            }

            val preview = mapPreviewResults(cached.resultRoleResults)
            if (preview !is MatchResultOcrPreviewUiState.Ready) {
                _cacheAvailability.value = MatchOcrCacheAvailability.STALE_OR_INCOMPLETE
                return@launch
            }
            val hasCompleteCombinedCache = availability == MatchOcrCacheAvailability.READY
            val matchedRows = if (hasCompleteCombinedCache) {
                if (teamContext.uuidSnapshot != null && teamContext.tournament != null) {
                    mapUuidBackedSuggestions(
                        tournament = teamContext.tournament,
                        matchId = matchId,
                        snapshot = teamContext.uuidSnapshot,
                        preview = preview,
                        resultRows = cached.resultRoleResults.processedResultRows(),
                        lobbyOcrResult = cached.lobbyResult,
                    )
                } else {
                    MatchResultOcrPreviewTeamSuggestionMapper.map(
                        preview = preview,
                        resultRows = cached.resultRoleResults.processedResultRows(),
                        lobbyOcrResult = cached.lobbyResult,
                        eligibleTeamSlots = teamContext.eligibleTeamSlots,
                        matchIdentityContext = teamContext.identityContext,
                    )
                }
            } else {
                null
            }
            val teamNamesBySlot = teamContext.teamNamesBySlot
            val lobbyPlayers = if (hasCompleteCombinedCache) {
                cached.lobbyResult.toUiState()
                    .takeIf {
                        cached.lobbyResult.hasLobbyOcrEvidence(
                            matchId,
                            lobbyScreenshotAssetRepository,
                            screenshotOwnerProvider.currentOwnerUserId(),
                        )
                    }
                    .orEmpty()
            } else {
                emptyList()
            }
            _uiState.update { state ->
                when (state) {
                    is MatchOcrReviewUiState.Ready -> state
                    is MatchOcrReviewUiState.Empty -> {
                        if (state.tournamentId == tournamentId && state.matchId == matchId) {
                            completeReviewStateFromPreview(
                                tournamentId = tournamentId,
                                matchId = matchId,
                                preview = preview,
                                reviewRows = matchedRows,
                                teamNamesBySlot = teamNamesBySlot,
                                lobbyPlayers = lobbyPlayers,
                                eligibleTeamSlots = teamContext.eligibleTeamSlots,
                                lobbySlotByTeamSlot = teamContext.lobbySlotByTeamSlot,
                                usesPairRelativeIdentity = teamContext.usesPairRelativeIdentity,
                            )?.copy(isUuidBackedGroupRotation = teamContext.uuidSnapshot != null) ?: state
                        } else {
                            state
                        }
                    }
                    is MatchOcrReviewUiState.Error,
                    is MatchOcrReviewUiState.Calculating,
                    -> state
                    MatchOcrReviewUiState.Loading -> completeReviewStateFromPreview(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        preview = preview,
                        reviewRows = matchedRows,
                        teamNamesBySlot = teamNamesBySlot,
                        lobbyPlayers = lobbyPlayers,
                        eligibleTeamSlots = teamContext.eligibleTeamSlots,
                        lobbySlotByTeamSlot = teamContext.lobbySlotByTeamSlot,
                        usesPairRelativeIdentity = teamContext.usesPairRelativeIdentity,
                    )?.copy(isUuidBackedGroupRotation = teamContext.uuidSnapshot != null) ?: state
                }
            }
        }
    }

    /** Restores saved calculated values only; this path never invokes an OCR runner. */
    fun restoreCalculatedEvidence(
        tournamentId: String,
        matchId: String,
        evidence: com.hoggamers.rankforge.data.local.MatchCalculatedEvidence,
    ) {
        val key = "$tournamentId:$matchId:calculated"
        if (loadedMatchKey == key && _uiState.value !is MatchOcrReviewUiState.Loading) return
        loadedMatchKey = key
        cacheLoadJob?.cancel()
        previewJob?.cancel()
        _cacheAvailability.value = MatchOcrCacheAvailability.NOT_AVAILABLE
        cacheLoadJob = viewModelScope.launch {
            val teamContext = loadTeamContext(tournamentId, matchId)
            teamContext.identityFailure?.let { failure ->
                _uiState.value = MatchOcrReviewUiState.Error(
                    tournamentId = tournamentId,
                    matchId = matchId,
                    message = failure.toOcrReviewMessage(),
                )
                return@launch
            }
            val restored = evidence.toRestoredOcrReviewUiState(
                tournamentId = tournamentId,
                matchId = matchId,
                teamNamesBySlot = teamContext.teamNamesBySlot,
                eligibleTeamSlots = teamContext.eligibleTeamSlots,
                lobbySlotByTeamSlot = teamContext.lobbySlotByTeamSlot,
                usesPairRelativeIdentity = teamContext.usesPairRelativeIdentity,
            )
            _uiState.value = when (restored) {
                is MatchOcrReviewUiState.Ready -> restored.copy(
                    isUuidBackedGroupRotation = teamContext.uuidSnapshot != null,
                )
                is MatchOcrReviewUiState.Empty -> restored.copy(
                    isUuidBackedGroupRotation = teamContext.uuidSnapshot != null,
                )
                else -> restored
            }
        }
    }

    /** Clears the calculated display before the normal cache-only fallback is loaded. */
    fun clearCalculatedEvidenceDisplay(tournamentId: String, matchId: String) {
        val current = _uiState.value
        if (current.tournamentId() != tournamentId || current.matchId() != matchId) return
        cacheLoadJob?.cancel()
        previewJob?.cancel()
        loadedMatchKey = "$tournamentId:$matchId"
        val ready = current as? MatchOcrReviewUiState.Ready
        _uiState.value = MatchOcrReviewUiState.Empty(
            tournamentId = tournamentId,
            matchId = matchId,
            teamNamesBySlot = ready?.teamNamesBySlot.orEmpty(),
            lobbySlotByTeamSlot = ready?.lobbySlotByTeamSlot.orEmpty(),
            usesPairRelativeIdentity = ready?.usesPairRelativeIdentity == true,
        )
    }

    fun loadHistoricalEvidence(tournamentId: String, matchId: String) {
        val matchKey = "$tournamentId:$matchId:historical"
        if (loadedMatchKey == matchKey && _uiState.value !is MatchOcrReviewUiState.Loading) return
        loadedMatchKey = matchKey
        cacheLoadJob?.cancel()
        _cacheAvailability.value = MatchOcrCacheAvailability.NOT_AVAILABLE
        previewJob?.cancel()
        _uiState.value = MatchOcrReviewUiState.Loading
        viewModelScope.launch {
            val teamContext = loadHistoricalTeamContext(tournamentId, matchId)
            teamContext.identityFailure?.let { failure ->
                _uiState.value = MatchOcrReviewUiState.Error(
                    tournamentId = tournamentId,
                    matchId = matchId,
                    message = failure.toOcrReviewMessage(),
                )
                return@launch
            }
            val evidence = try {
                tournamentRepository.readPreservedMatchOcrEvidenceByOwner(
                    tournamentId,
                    matchId,
                    screenshotOwnerProvider.currentOwnerUserId().orEmpty(),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (evidence == null) {
                _uiState.value = MatchOcrReviewUiState.Empty(
                    tournamentId = tournamentId,
                    matchId = matchId,
                )
                return@launch
            }
            _uiState.value = evidence.toHistoricalUiState(
                teamNamesBySlot = teamContext.teamNamesBySlot,
                eligibleTeamSlots = teamContext.eligibleTeamSlots,
                lobbySlotByTeamSlot = teamContext.lobbySlotByTeamSlot,
                usesPairRelativeIdentity = teamContext.usesPairRelativeIdentity,
            )
        }
    }

    /**
     * Loads already-computed OCR and team-matching evidence for this exact match.
     * This entry point remains display-only and does not replace an existing review draft.
     */
    fun loadDisplayInput(input: MatchOcrReviewDisplayInput) {
        val matchKey = "${input.tournamentId}:${input.matchId}"
        if (loadedMatchKey == matchKey && _uiState.value !is MatchOcrReviewUiState.Empty) return
        loadedMatchKey = matchKey
        _uiState.value = MatchOcrReviewUiStateMapper.map(input)
    }

    fun onPlacementChanged(rowIndex: Int, value: String) {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(draft, rowIndex, value)
        }
    }

    fun onKillsChanged(rowIndex: Int, value: String) {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onKillsChanged(draft, rowIndex, value)
        }
    }

    fun onPlayerKillsChanged(rowIndex: Int, playerSlot: Int, value: String) {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onPlayerKillsChanged(
                draft = draft,
                rowIndex = rowIndex,
                playerSlot = playerSlot,
                value = value,
            )
        }
    }

    fun onAssignedTeamSlotChanged(rowIndex: Int, value: String) {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(draft, rowIndex, value)
        }
    }

    fun onCompactAddTeam() {
        _uiState.update { state ->
            if (state !is MatchOcrReviewUiState.Ready || state.finalization.isFinalized) {
                state
            } else when (state.calculatedEvidenceOrigin) {
                MatchCalculatedEvidenceOrigin.MANUAL -> {
                    val position = state.nextAddTeamPositionOrNull() ?: return@update state
                    val currentDraft = state.correctionDraft
                        ?: return@update state
                    state.copy(
                        correctionDraft = MatchOcrReviewCorrectionDraftReducer.onRestoreExcludedRow(
                            draft = currentDraft,
                            rowIndex = position - 1,
                        ),
                    )
                }

                MatchCalculatedEvidenceOrigin.AUTOMATIC -> {
                    state.nextManualResultPositionOrNull()?.let { position ->
                        val currentDraft = state.correctionDraft
                            ?: MatchOcrReviewCorrectionDraftReducer.createInitialDraft(state.rows)
                        state.copy(
                            manuallyRevealedPositions = state.manuallyRevealedPositions + position,
                            correctionDraft = MatchOcrReviewCorrectionDraftReducer.onResetRowCorrection(
                                draft = currentDraft,
                                rowIndex = position - 1,
                            ),
                        )
                    } ?: state
                }
            }
        }
    }

    fun onExcludeRow(rowIndex: Int) {
        _uiState.update { state ->
            if (state is MatchOcrReviewUiState.Ready) {
                if (state.finalization.isFinalized) return@update state
                val position = rowIndex + 1
                val wasManuallyRevealed = position in state.manuallyRevealedPositions
                val currentDraft = state.correctionDraft
                    ?: MatchOcrReviewCorrectionDraftReducer.createInitialDraft(state.rows)
                val updatedDraft = when {
                    state.calculatedEvidenceOrigin == MatchCalculatedEvidenceOrigin.MANUAL -> {
                        MatchOcrReviewCorrectionDraftReducer.onRowExcluded(
                            draft = currentDraft,
                            rowIndex = rowIndex,
                        )
                    }
                    wasManuallyRevealed -> {
                        MatchOcrReviewCorrectionDraftReducer.onResetRowCorrection(
                            draft = currentDraft,
                            rowIndex = rowIndex,
                        )
                    }
                    else -> {
                        MatchOcrReviewCorrectionDraftReducer.onRowExcluded(
                            draft = currentDraft,
                            rowIndex = rowIndex,
                        )
                    }
                }
                state.copy(
                    manuallyRevealedPositions = state.manuallyRevealedPositions - position,
                    correctionDraft = updatedDraft,
                    finalization = state.finalization.copy(
                        showWarningConfirmation = false,
                        error = null,
                        missingTeamSlotNumbers = emptyList(),
                    ),
                )
            } else {
                state
            }
        }
    }

    fun onResetRowCorrection(rowIndex: Int) {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onResetRowCorrection(draft, rowIndex)
        }
    }

    fun onResetAllCorrections() {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onResetAllCorrections(draft)
        }
    }

    private fun updateCorrectionDraft(
        transform: (MatchOcrReviewCorrectionDraft) -> MatchOcrReviewCorrectionDraft,
    ) {
        _uiState.update { state ->
            if (state is MatchOcrReviewUiState.Ready) {
                if (state.finalization.isFinalized) return@update state
                val currentDraft = state.correctionDraft
                    ?: MatchOcrReviewCorrectionDraftReducer.createInitialDraft(state.rows)
                state.copy(
                    correctionDraft = transform(currentDraft),
                    finalization = state.finalization.copy(
                        showWarningConfirmation = false,
                        error = null,
                        missingTeamSlotNumbers = emptyList(),
                    ),
                )
            } else {
                state
            }
        }
    }

    fun onFinalizeOcrCorrection() {
        val current = _uiState.value as? MatchOcrReviewUiState.Ready ?: return
        if (current.finalization.isFinalizing || current.finalization.isFinalized) return
        val correctionDraft = current.correctionDraft
        if (correctionDraft == null) {
            updateFinalizationError(MatchOcrReviewFinalizationError.MISSING_CORRECTION_DRAFT)
            return
        }
        if (correctionDraft.blockerCount > 0) {
            updateFinalizationError(MatchOcrReviewFinalizationError.CORRECTION_DRAFT_BLOCKED)
            return
        }
        if (correctionDraft.warningCount > 0) {
            finalizeCurrentCorrectionDraft(warningConfirmationAccepted = true)
            return
        }

        finalizeCurrentCorrectionDraft(warningConfirmationAccepted = false)
    }

    fun onConfirmFinalizeWarnings() {
        val current = _uiState.value as? MatchOcrReviewUiState.Ready ?: return
        val correctionDraft = current.correctionDraft ?: return
        if (
            current.finalization.isFinalizing ||
            current.finalization.isFinalized ||
            !current.finalization.showWarningConfirmation ||
            correctionDraft.blockerCount > 0
        ) return

        finalizeCurrentCorrectionDraft(warningConfirmationAccepted = true)
    }

    fun onDismissFinalizeWarnings() {
        _uiState.updateReady {
            it.copy(
                finalization = it.finalization.copy(showWarningConfirmation = false),
            )
        }
    }

    private fun finalizeCurrentCorrectionDraft(warningConfirmationAccepted: Boolean) {
        val current = _uiState.value as? MatchOcrReviewUiState.Ready ?: return
        val correctionDraft = current.correctionDraft
        if (correctionDraft == null) {
            updateFinalizationError(MatchOcrReviewFinalizationError.MISSING_CORRECTION_DRAFT)
            return
        }
        if (correctionDraft.blockerCount > 0) {
            updateFinalizationError(MatchOcrReviewFinalizationError.CORRECTION_DRAFT_BLOCKED)
            return
        }

        if (current.isUuidBackedGroupRotation) {
            finalizeUuidBackedCorrection(current, correctionDraft)
            return
        }

        _uiState.updateReady {
            it.copy(
                finalization = it.finalization.copy(
                    isFinalizing = true,
                    showWarningConfirmation = false,
                    error = null,
                    missingTeamSlotNumbers = emptyList(),
                ),
            )
        }
        viewModelScope.launch {
            val result = finalizeOcrCorrectionMatch(
                FinalizeOcrCorrectionMatchInput(
                    tournamentId = current.tournamentId,
                    matchId = current.matchId,
                    correctionRows = correctionDraft.toFinalizeRows(current.rows),
                    warningConfirmationAccepted = warningConfirmationAccepted,
                    sourceScreenshotId = null,
                ),
            )
            _uiState.updateReady { ready ->
                when (result) {
                    is FinalizeOcrCorrectionMatchResult.Finalized -> ready.copy(
                        finalization = MatchOcrReviewFinalizationUiState(isFinalized = true),
                    )
                    is FinalizeOcrCorrectionMatchResult.ConfirmationRequired -> ready.copy(
                        finalization = ready.finalization.copy(
                            isFinalizing = false,
                            showWarningConfirmation = true,
                            error = null,
                        ),
                    )
                    is FinalizeOcrCorrectionMatchResult.Blocked -> ready.copy(
                        finalization = ready.finalization.copy(
                            isFinalizing = false,
                            showWarningConfirmation = false,
                            error = result.failures.toUiError(),
                            missingTeamSlotNumbers = result.missingTeamSlotNumbers.sorted(),
                        ),
                    )
                    FinalizeOcrCorrectionMatchResult.UnexpectedFailure -> ready.copy(
                        finalization = ready.finalization.copy(
                            isFinalizing = false,
                            showWarningConfirmation = false,
                            error = MatchOcrReviewFinalizationError.UNEXPECTED_FAILURE,
                        ),
                    )
                }
            }
            if (result is FinalizeOcrCorrectionMatchResult.Finalized) {
                launchFinalizedMatchCloudSync(current.tournamentId)
            }
        }
    }

    private fun finalizeUuidBackedCorrection(
        current: MatchOcrReviewUiState.Ready,
        correctionDraft: MatchOcrReviewCorrectionDraft,
    ) {
        val saveResults = saveGroupRotationMatchParticipantResults
        val finalize = finalizeGroupRotationMatch
        if (saveResults == null || finalize == null) {
            updateFinalizationError(MatchOcrReviewFinalizationError.FINALIZATION_FAILED)
            return
        }
        _uiState.updateReady {
            it.copy(
                finalization = it.finalization.copy(
                    isFinalizing = true,
                    showWarningConfirmation = false,
                    error = null,
                    missingTeamSlotNumbers = emptyList(),
                ),
            )
        }
        viewModelScope.launch {
            val draft = observeGroupRotationMatchDraft
                ?.invoke(current.tournamentId, current.matchId)
                ?.first() as? GroupRotationMatchDraftReadResult.Loaded
            val participantResults = draft?.state?.toUuidParticipantResults(correctionDraft)
            if (participantResults == null ||
                saveResults(current.tournamentId, current.matchId, participantResults) !=
                GroupRotationMatchDraftMutationResult.SAVED
            ) {
                updateFinalizationError(MatchOcrReviewFinalizationError.FINALIZATION_FAILED)
                return@launch
            }
            when (
                val result = finalize(
                    FinalizeGroupRotationMatchInput(
                        tournamentId = current.tournamentId,
                        matchId = current.matchId,
                        participantResults = participantResults,
                    ),
                )
            ) {
                is GroupRotationMatchFinalizationResult.Finalized -> {
                    _uiState.updateReady {
                        it.copy(finalization = MatchOcrReviewFinalizationUiState(isFinalized = true))
                    }
                    launchFinalizedMatchCloudSync(current.tournamentId)
                }
                is GroupRotationMatchFinalizationResult.Rejected -> {
                    val missingTeamSlotNumbers = if (
                        result.reason == GroupRotationMatchFinalizationFailure.INVALID_DATA
                    ) {
                        (observeGroupRotationMatchDraft
                            ?.invoke(current.tournamentId, current.matchId)
                            ?.first() as? GroupRotationMatchDraftReadResult.Loaded)
                            ?.state
                            ?.currentMissingAssignedTeamSlots(
                                correctionDraft = correctionDraft,
                                participantResults = participantResults,
                            )
                            .orEmpty()
                    } else {
                        emptyList()
                    }
                    if (missingTeamSlotNumbers.isNotEmpty()) {
                        updateMissingAssignedTeamNameError(missingTeamSlotNumbers)
                    } else {
                        updateFinalizationError(MatchOcrReviewFinalizationError.FINALIZATION_FAILED)
                    }
                }
            }
        }
    }

    private fun GroupRotationMatchDraftState.currentMissingAssignedTeamSlots(
        correctionDraft: MatchOcrReviewCorrectionDraft,
        participantResults: List<GroupRotationMatchParticipantResult>,
    ): List<Int> {
        val teamsByLobby = snapshot.teams.groupBy { it.lobbySlotNumber }
        if (
            snapshot.teams.any {
                it.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS ||
                    it.teamUUID.isBlank()
            } ||
            teamsByLobby.any { (_, teams) -> teams.size != 1 } ||
            snapshot.teams.groupBy { it.teamUUID }.any { (_, teams) -> teams.size != 1 }
        ) {
            return emptyList()
        }

        val missingTeamSlotNumbers = correctionDraft.includedRows
            .mapNotNull { row ->
                val lobbySlotNumber = row.assignedTeamSlotDraftValue.trim().toIntOrNull()
                    ?.takeIf { it in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS }
                    ?: return@mapNotNull null
                val team = teamsByLobby[lobbySlotNumber]?.singleOrNull()
                    ?: return@mapNotNull null
                lobbySlotNumber.takeIf {
                    team.nameCleared || team.displayName.trim().isBlank()
                }
            }
            .distinct()
            .sorted()
        if (missingTeamSlotNumbers.isEmpty()) return emptyList()

        val validationIssues = GroupRotationMatchFinalizationValidator().validate(
            snapshot = snapshot,
            participantResults = participantResults,
        )
        return missingTeamSlotNumbers.takeIf {
            validationIssues.isNotEmpty() && validationIssues.all { issue ->
                issue.code == GroupRotationMatchFinalizationIssueCode.INVALID_IDENTITY_SNAPSHOT
            }
        }.orEmpty()
    }

    private fun GroupRotationMatchDraftState.toUuidParticipantResults(
        correctionDraft: MatchOcrReviewCorrectionDraft,
    ): List<GroupRotationMatchParticipantResult>? {
        val teams = snapshot.teams.sortedBy { it.lobbySlotNumber }
        val teamsByLobby = teams.associateBy { it.lobbySlotNumber }
        val teamUuids = teams.map { it.teamUUID }
        if (
            teams.size != snapshot.teams.size ||
            teamsByLobby.size != teams.size ||
            teams.any {
                it.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS ||
                    it.teamUUID.isBlank()
            } ||
            teamUuids.toSet().size != teamUuids.size
        ) {
            return null
        }

        val existingByUuid = participantResults.groupBy { it.teamUUID }
        if (
            existingByUuid.values.any { it.size != 1 } ||
            existingByUuid.keys.any { it !in teamUuids }
        ) {
            return null
        }

        data class ReviewedRow(
            val lobbySlotNumber: Int,
            val row: MatchOcrReviewRowCorrectionDraft,
        )

        val reviewedRows = buildList {
            correctionDraft.rows.forEach { row ->
                // A deleted, unassigned structural row is not a registered participant.
                // Deleted rows with an assigned identity still represent explicit NO_SHOW.
                if (row.isImplicitlyAbsent || (row.isExcluded && row.assignedTeamSlotDraftValue.isBlank())) {
                    return@forEach
                }
                val lobbySlotNumber = row.assignedTeamSlotDraftValue.trim().toIntOrNull()
                    ?: return null
                if (
                    lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS ||
                    teamsByLobby[lobbySlotNumber] == null
                ) {
                    return null
                }
                add(ReviewedRow(lobbySlotNumber, row))
            }
        }

        val reviewedRowsByLobby = reviewedRows.groupBy { it.lobbySlotNumber }
        if (
            reviewedRowsByLobby.values.any { it.size != 1 } ||
            reviewedRowsByLobby.keys != teamsByLobby.keys
        ) {
            return null
        }

        val officialPlacementByRowIndex = correctionDraft.authoritativePlacementByRowIndex()

        val results = teams.map { team ->
            val reviewedRow = reviewedRowsByLobby.getValue(team.lobbySlotNumber).single().row
            val previous = existingByUuid[team.teamUUID]?.singleOrNull()
            val pointAdjustment = previous?.pointAdjustment
                ?: valuesByLobbySlot[team.lobbySlotNumber]?.pointAdjustment
                ?: 0
            if (reviewedRow.isEffectivelyExcluded) {
                GroupRotationMatchParticipantResult(
                    teamUUID = team.teamUUID,
                    participationStatus = MatchParticipationStatus.NO_SHOW,
                    placement = null,
                    kills = 0,
                    pointAdjustment = pointAdjustment,
                )
            } else {
                val placement = officialPlacementByRowIndex[reviewedRow.rowIndex]
                    ?: return null
                val kills = reviewedRow.killsDraftValue.trim().toIntOrNull()
                    ?.takeIf { it >= 0 }
                    ?: return null
                GroupRotationMatchParticipantResult(
                    teamUUID = team.teamUUID,
                    participationStatus = MatchParticipationStatus.PARTICIPATED,
                    placement = placement,
                    kills = kills,
                    pointAdjustment = pointAdjustment,
                )
            }
        }

        if (
            results.map { it.teamUUID }.toSet().size != results.size ||
            results.filter { it.participationStatus == MatchParticipationStatus.PARTICIPATED }
                .mapNotNull { it.placement }
                .let { placements -> placements.toSet().size != placements.size }
        ) {
            return null
        }
        return results
    }

    private fun launchFinalizedMatchCloudSync(tournamentId: String) {
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

    private fun updateFinalizationError(error: MatchOcrReviewFinalizationError) {
        _uiState.updateReady {
            it.copy(
                finalization = it.finalization.copy(
                    isFinalizing = false,
                    showWarningConfirmation = false,
                    error = error,
                    missingTeamSlotNumbers = emptyList(),
                ),
            )
        }
    }

    private fun updateMissingAssignedTeamNameError(teamSlotNumbers: List<Int>) {
        _uiState.updateReady {
            it.copy(
                finalization = it.finalization.copy(
                    isFinalizing = false,
                    showWarningConfirmation = false,
                    error = MatchOcrReviewFinalizationError.MISSING_ASSIGNED_TEAM_NAME,
                    missingTeamSlotNumbers = teamSlotNumbers.distinct().sorted(),
                ),
            )
        }
    }

    private fun MutableStateFlow<MatchOcrReviewUiState>.updateReady(
        transform: (MatchOcrReviewUiState.Ready) -> MatchOcrReviewUiState.Ready,
    ) {
        update { state ->
            if (state is MatchOcrReviewUiState.Ready) transform(state) else state
        }
    }

    private fun MatchOcrReviewCorrectionDraft.toFinalizeRows(
        reviewRows: List<MatchOcrReviewRowUiState>,
    ): List<FinalizeOcrCorrectionRowInput> {
        val reviewRowsByIndex = reviewRows.associateBy { it.rowIndex }
        val officialPlacementBySourceRowIndex = authoritativePlacementByRowIndex()
        return rows.map { row ->
            val reviewRow = reviewRowsByIndex[row.rowIndex]
            FinalizeOcrCorrectionRowInput(
                rowIndex = row.rowIndex,
                correctedPlacement = if (row.isEffectivelyExcluded) {
                    row.placementDraftValue
                } else {
                    officialPlacementBySourceRowIndex[row.rowIndex]?.toString().orEmpty()
                },
                correctedKills = row.killsDraftValue,
                correctedTeamSlotNumber = row.assignedTeamSlotDraftValue,
                warnings = row.validation.warnings.mapNotNull { it.toFinalizeWarning() }.toSet(),
                originalOcrText = reviewRow?.detectedPlayerNameEvidenceLabel,
                originalPlacement = reviewRow?.originalParsedPlacementValue,
                originalKills = reviewRow?.originalParsedKillValue,
                originalSuggestedTeamSlot = reviewRow?.originalSuggestedTeamSlot,
                confidenceSummary = reviewRow?.let {
                    "${it.confidenceTierLabel}|${it.confidenceScoreDisplayValue}"
                },
                safetySummary = reviewRow?.assignmentSafetyStatusLabel,
                manualReviewRequired = reviewRow?.let {
                    it.blockerLabels.isNotEmpty() || it.warningLabels.isNotEmpty()
                } ?: row.originallyRequiredManualReview,
                isExcluded = row.isEffectivelyExcluded,
            )
        }
    }

    private fun MatchOcrReviewCorrectionDraft.authoritativePlacementByRowIndex(): Map<Int, Int> {
        val activeRows = rows.filterNot { it.isEffectivelyExcluded }
        val correctedPlacements = activeRows.mapNotNull { row ->
            row.placementDraftValue.trim()
                .toIntOrNull()
                ?.takeIf { it in TeamSlot.SLOT_NUMBERS }
                ?.let { row.rowIndex to it }
        }
        if (
            correctedPlacements.size != activeRows.size ||
            correctedPlacements.map { it.second }.toSet().size != activeRows.size
        ) {
            return emptyMap()
        }
        if (rows.none { it.isEffectivelyExcluded }) {
            return correctedPlacements.toMap()
        }
        return correctedPlacements
            .sortedBy { it.second }
            .mapIndexed { officialIndex, (rowIndex, _) -> rowIndex to officialIndex + 1 }
            .toMap()
    }

    private fun MatchOcrReviewCorrectionReason.toFinalizeWarning(): FinalizeOcrCorrectionMatchWarning? =
        when (this) {
            MatchOcrReviewCorrectionReason.PLACEMENT_CHANGED_FROM_OCR ->
                FinalizeOcrCorrectionMatchWarning.PLACEMENT_CHANGED_FROM_OCR
            MatchOcrReviewCorrectionReason.KILLS_CHANGED_FROM_OCR ->
                FinalizeOcrCorrectionMatchWarning.KILLS_CHANGED_FROM_OCR
            MatchOcrReviewCorrectionReason.TEAM_SLOT_CHANGED_FROM_SUGGESTION ->
                FinalizeOcrCorrectionMatchWarning.TEAM_SLOT_CHANGED_FROM_SUGGESTION
            MatchOcrReviewCorrectionReason.ROW_ORIGINALLY_REQUIRED_MANUAL_REVIEW ->
                FinalizeOcrCorrectionMatchWarning.ROW_ORIGINALLY_REQUIRED_MANUAL_REVIEW
            MatchOcrReviewCorrectionReason.WEAK_CONFIDENCE_OR_SAFETY_EVIDENCE ->
                FinalizeOcrCorrectionMatchWarning.WEAK_CONFIDENCE_OR_SAFETY_EVIDENCE
            else -> null
        }

    private fun Set<FinalizeOcrCorrectionMatchFailure>.toUiError(): MatchOcrReviewFinalizationError =
        when {
            FinalizeOcrCorrectionMatchFailure.MISSING_CORRECTION_DRAFT in this ->
                MatchOcrReviewFinalizationError.MISSING_CORRECTION_DRAFT
            FinalizeOcrCorrectionMatchFailure.MISSING_TOURNAMENT in this ->
                MatchOcrReviewFinalizationError.MISSING_TOURNAMENT
            FinalizeOcrCorrectionMatchFailure.MISSING_MATCH in this ->
                MatchOcrReviewFinalizationError.MISSING_MATCH
            FinalizeOcrCorrectionMatchFailure.ALREADY_FINALIZED in this ->
                MatchOcrReviewFinalizationError.ALREADY_FINALIZED
            FinalizeOcrCorrectionMatchFailure.MISSING_ASSIGNED_TEAM_NAME in this ->
                MatchOcrReviewFinalizationError.MISSING_ASSIGNED_TEAM_NAME
            FinalizeOcrCorrectionMatchFailure.UNEXPECTED_FAILURE in this ->
                MatchOcrReviewFinalizationError.UNEXPECTED_FAILURE
            else -> MatchOcrReviewFinalizationError.FINALIZATION_FAILED
        }

    private fun mapPreviewResults(
        roleResults: List<MatchResultOcrPreviewRoleResult>,
    ): MatchResultOcrPreviewUiState {
        if (roleResults.any { it.result is MatchResultOcrPreviewProcessingResult.Processed }) {
            return MatchResultOcrPreviewUiStateMapper.map(roleResults)
        }
        return when {
            roleResults.any { it.result == MatchResultOcrPreviewProcessingResult.MissingConfirmedCrop } ->
                MatchResultOcrPreviewUiState.Error("Confirm the screenshot crop before running OCR preview.")
            roleResults.any { it.result == MatchResultOcrPreviewProcessingResult.MissingAsset } ->
                MatchResultOcrPreviewUiState.Error("The match-result screenshot asset is unavailable.")
            roleResults.any { it.result == MatchResultOcrPreviewProcessingResult.MissingLocalOriginal } ->
                MatchResultOcrPreviewUiState.Error("The local match-result screenshot is unavailable.")
            roleResults.any { it.result == MatchResultOcrPreviewProcessingResult.InvalidCrop } ->
                MatchResultOcrPreviewUiState.Error("The confirmed screenshot crop is invalid.")
            else -> MatchResultOcrPreviewUiState.Error("OCR preview could not be processed.")
        }
    }

    private fun completeReviewStateFromPreview(
        tournamentId: String,
        matchId: String,
        preview: MatchResultOcrPreviewUiState,
        reviewRows: List<MatchOcrReviewRowUiState>?,
        teamNamesBySlot: Map<Int, String>,
        lobbyPlayers: List<MatchOcrReviewLobbySlotUiState>,
        phase1LobbySlotNumberOcr: MatchLobbySlotNumberOcrResult? = null,
        eligibleTeamSlots: Set<Int>,
        lobbySlotByTeamSlot: Map<Int, Int> = emptyMap(),
        usesPairRelativeIdentity: Boolean = false,
    ): MatchOcrReviewUiState.Ready? {
        val rows = reviewRows ?: MatchResultOcrPreviewUiStateMapper.toReviewRows(preview) ?: return null
        val correctionDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(
            rows = rows,
            assignmentRequired = true,
            eligibleTeamSlots = eligibleTeamSlots,
        )
        val ready = MatchOcrReviewUiState.Ready(
            tournamentId = tournamentId,
            matchId = matchId,
            rowCount = rows.size,
            rows = rows,
            blockerCount = correctionDraft.blockerCount,
            warningCount = correctionDraft.warningCount,
            safeRowCount = rows.count { row ->
                if (row.resultLobbyVoteEvidencePresent) {
                    row.resultLobbyDecisionLabel == RESULT_LOBBY_AUTOMATIC_LABEL
                } else {
                    row.assignmentSafetyStatusLabel == SAFE_AUTOMATIC_ASSIGNMENT_LABEL
                }
            },
            manualRequiredRowCount = rows.count { row ->
                if (row.resultLobbyVoteEvidencePresent) {
                    row.resultLobbyDecisionLabel == RESULT_LOBBY_MANUAL_LABEL
                } else {
                    row.assignmentSafetyStatusLabel == MANUAL_REQUIRED_LABEL
                }
            },
            reviewRequiredRowCount = rows.count { row ->
                !row.resultLobbyVoteEvidencePresent &&
                    row.assignmentSafetyStatusLabel == REVIEW_REQUIRED_LABEL
            },
            manualReviewRequired = correctionDraft.blockerCount > 0 || correctionDraft.warningCount > 0,
            hasUnavailableEvidence = rows.any { row ->
                row.confidenceTierLabel == UNAVAILABLE_LABEL ||
                    row.assignmentSafetyStatusLabel == UNAVAILABLE_LABEL ||
                    row.topThreeSuggestionsSummary == listOf(NO_SUGGESTIONS_LABEL)
            },
            correctionDraft = correctionDraft,
            matchResultOcrPreview = preview,
            teamNamesBySlot = teamNamesBySlot,
            eligibleTeamSlots = eligibleTeamSlots,
            lobbyPlayers = lobbyPlayers,
            phase1LobbySlotNumberOcr = phase1LobbySlotNumberOcr,
            lobbySlotByTeamSlot = lobbySlotByTeamSlot,
            usesPairRelativeIdentity = usesPairRelativeIdentity,
        )
        return ready
    }

    private fun completeManualFallbackReviewState(
        tournamentId: String,
        matchId: String,
        preview: MatchResultOcrPreviewUiState,
        teamNamesBySlot: Map<Int, String>,
        lobbyPlayers: List<MatchOcrReviewLobbySlotUiState>,
        phase1LobbySlotNumberOcr: MatchLobbySlotNumberOcrResult? = null,
        calculationOrigin: MatchCalculatedEvidenceOrigin,
        eligibleTeamSlots: Set<Int>,
        lobbySlotByTeamSlot: Map<Int, Int> = emptyMap(),
        usesPairRelativeIdentity: Boolean = false,
    ): MatchOcrReviewUiState.Ready {
        val rows = MatchResultOcrPreviewUiStateMapper.manualFallbackRows()
        val correctionDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(
            rows = rows,
            assignmentRequired = true,
            eligibleTeamSlots = eligibleTeamSlots,
        )
        return MatchOcrReviewUiState.Ready(
            tournamentId = tournamentId,
            matchId = matchId,
            rowCount = rows.size,
            rows = rows,
            blockerCount = correctionDraft.blockerCount,
            warningCount = correctionDraft.warningCount,
            safeRowCount = 0,
            manualRequiredRowCount = rows.size,
            reviewRequiredRowCount = 0,
            manualReviewRequired = true,
            hasUnavailableEvidence = true,
            correctionDraft = correctionDraft,
            matchResultOcrPreview = preview,
            teamNamesBySlot = teamNamesBySlot,
            eligibleTeamSlots = eligibleTeamSlots,
            lobbyPlayers = lobbyPlayers,
            phase1LobbySlotNumberOcr = phase1LobbySlotNumberOcr,
            calculatedEvidenceOrigin = calculationOrigin,
            lobbySlotByTeamSlot = lobbySlotByTeamSlot,
            usesPairRelativeIdentity = usesPairRelativeIdentity,
        )
    }

    private suspend fun loadTeamContext(tournamentId: String, matchId: String): OcrReviewTeamContext = try {
        val persistedSlots = observeTournamentSlots(tournamentId).first()
        val match = tournamentRepository.observeMatchById(matchId).first()
        val tournament = tournamentRepository.observeById(tournamentId).first()
        val groupDraft = observeGroupRotationMatchDraft?.invoke(tournamentId, matchId)?.first()
        if (match?.groupPairing != null && groupDraft is GroupRotationMatchDraftReadResult.Loaded) {
            val snapshot = groupDraft.state.snapshot
            if (snapshot.tournamentId != tournamentId || snapshot.pairing != match.groupPairing) {
                return OcrReviewTeamContext(
                    teamNamesBySlot = emptyMap(),
                    eligibleTeamSlots = emptySet(),
                    tournament = tournament,
                    identityFailure = MatchTeamIdentityContextReadResult.InvalidMapping,
                )
            }
            return OcrReviewTeamContext(
                teamNamesBySlot = snapshot.teams.associate { it.lobbySlotNumber to it.displayName },
                eligibleTeamSlots = snapshot.teams.map { it.lobbySlotNumber }.toSet(),
                lobbySlotByTeamSlot = snapshot.teams.associate { it.lobbySlotNumber to it.lobbySlotNumber },
                usesPairRelativeIdentity = true,
                tournament = tournament,
                uuidSnapshot = snapshot,
            )
        }
        if (match?.groupPairing != null &&
            groupDraft != null &&
            groupDraft !is GroupRotationMatchDraftReadResult.NotUuidBacked
        ) {
            return OcrReviewTeamContext(
                teamNamesBySlot = emptyMap(),
                eligibleTeamSlots = emptySet(),
                tournament = tournament,
                identityFailure = MatchTeamIdentityContextReadResult.InvalidMapping,
            )
        }
        val identityResult = readMatchTeamIdentityContext.forMatch(matchId)
        val loadedIdentity = identityResult as? MatchTeamIdentityContextReadResult.Loaded
        val usesPairRelativeIdentity = tournament?.format == TournamentFormat.GROUP_ROTATION ||
            match?.groupPairing != null ||
            loadedIdentity?.context?.pairing != null

        val identityContext = if (usesPairRelativeIdentity) {
            when {
                tournament == null -> null
                match == null -> null
                match.groupPairing == null -> null
                match.groupPairing !in tournament.selectedGroupPairings -> null
                loadedIdentity == null -> null
                loadedIdentity.context.tournamentId != tournamentId -> null
                loadedIdentity.context.pairing != match.groupPairing -> null
                else -> loadedIdentity.context
            }
        } else {
            loadedIdentity?.context
        }
        val identityFailure = if (!usesPairRelativeIdentity) {
            null
        } else {
            when {
                tournament == null && identityResult is MatchTeamIdentityContextReadResult.TournamentNotFound ->
                    identityResult
                match == null && identityResult is MatchTeamIdentityContextReadResult.MatchNotFound ->
                    identityResult
                match?.groupPairing == null -> MatchTeamIdentityContextReadResult.PairingRequired
                tournament != null && match.groupPairing !in tournament.selectedGroupPairings ->
                    MatchTeamIdentityContextReadResult.PairingNotSelected
                loadedIdentity == null -> identityResult
                identityContext == null -> MatchTeamIdentityContextReadResult.InvalidMapping
                else -> null
            }
        }
        val resolvedStandardSlots = if (!usesPairRelativeIdentity && match != null && tournament != null) {
            runCatching {
                MatchEligibleTeamSlotResolver().resolve(tournament, persistedSlots, match)
            }.getOrNull()
        } else {
            null
        }
        val eligibleTeamSlots = identityContext?.eligibleTeamSlotNumbers
            ?: resolvedStandardSlots
            ?: if (usesPairRelativeIdentity) emptySet() else TeamSlot.SLOT_NUMBERS.toSet()
        val lobbySlotByTeamSlot = identityContext?.teams
            ?.associate { team -> team.teamSlotNumber to team.lobbySlotNumber }
            ?: eligibleTeamSlots.associateWith { slotNumber -> slotNumber }
        OcrReviewTeamContext(
            teamNamesBySlot = persistedSlots
                .filter { it.slotNumber in eligibleTeamSlots }
                .associate { it.slotNumber to it.teamName },
            eligibleTeamSlots = eligibleTeamSlots,
            identityContext = identityContext,
            lobbySlotByTeamSlot = lobbySlotByTeamSlot,
            usesPairRelativeIdentity = usesPairRelativeIdentity,
            tournament = tournament,
            identityFailure = identityFailure,
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        OcrReviewTeamContext(
            teamNamesBySlot = emptyMap(),
            eligibleTeamSlots = emptySet(),
            tournament = null,
            identityFailure = MatchTeamIdentityContextReadResult.InvalidMapping,
        )
    }

    private data class OcrReviewTeamContext(
        val teamNamesBySlot: Map<Int, String>,
        val eligibleTeamSlots: Set<Int>,
        val identityContext: MatchTeamIdentityContext? = null,
        val lobbySlotByTeamSlot: Map<Int, Int> = emptyMap(),
        val usesPairRelativeIdentity: Boolean = false,
        val tournament: Tournament? = null,
        val uuidSnapshot: GroupRotationMatchIdentitySnapshot? = null,
        val identityFailure: MatchTeamIdentityContextReadResult? = null,
    )

    /** Historical finalized evidence is read-only and may predate the persisted lobby mapping. */
    private suspend fun loadHistoricalTeamContext(
        tournamentId: String,
        matchId: String,
    ): OcrReviewTeamContext {
        val tournament = tournamentRepository.observeById(tournamentId).first()
        if (tournament?.format != TournamentFormat.GROUP_ROTATION) {
            return loadTeamContext(tournamentId, matchId)
        }
        val persistedSlots = observeTournamentSlots(tournamentId).first()
        val eligibleTeamSlots = persistedSlots.map { it.slotNumber }.toSet()
            .ifEmpty { TeamSlot.TOURNAMENT_SLOT_NUMBERS.toSet() }
        return OcrReviewTeamContext(
            teamNamesBySlot = persistedSlots.associate { it.slotNumber to it.teamName },
            eligibleTeamSlots = eligibleTeamSlots,
            usesPairRelativeIdentity = true,
        )
    }

    private fun MatchTeamIdentityContextReadResult.toOcrReviewMessage(): String = when (this) {
        MatchTeamIdentityContextReadResult.AuthenticationRequired ->
            "Authentication is required before match team identity can be loaded."
        MatchTeamIdentityContextReadResult.TournamentNotFound ->
            "Tournament team identity setup is unavailable."
        MatchTeamIdentityContextReadResult.MatchNotFound ->
            "Match team identity setup is unavailable."
        MatchTeamIdentityContextReadResult.PairingRequired ->
            "This Group Rotation match has no selected pairing."
        MatchTeamIdentityContextReadResult.PairingNotSelected ->
            "This Group Rotation match pairing is unavailable."
        MatchTeamIdentityContextReadResult.SetupRequired ->
            "Complete Group Rotation team setup before reviewing OCR."
        MatchTeamIdentityContextReadResult.InvalidMapping ->
            "The persisted match team identity mapping is invalid."
        is MatchTeamIdentityContextReadResult.Loaded ->
            "The persisted match team identity mapping is invalid."
    }

    private fun PreservedMatchOcrEvidence.toHistoricalUiState(
        teamNamesBySlot: Map<Int, String>,
        eligibleTeamSlots: Set<Int>,
        lobbySlotByTeamSlot: Map<Int, Int> = emptyMap(),
        usesPairRelativeIdentity: Boolean = false,
    ): MatchOcrReviewUiState {
        if (rows.isEmpty()) {
            return MatchOcrReviewUiState.Empty(
                tournamentId = tournamentId,
                matchId = matchId,
                teamNamesBySlot = teamNamesBySlot,
                eligibleTeamSlots = eligibleTeamSlots,
                lobbySlotByTeamSlot = lobbySlotByTeamSlot,
                usesPairRelativeIdentity = usesPairRelativeIdentity,
            )
        }

        val snapshotsByRow = correctionSnapshots.associateBy { it.rowIndex }
        val reviewRows = rows.sortedBy { it.rowIndex }.map { row ->
            val correction = snapshotsByRow[row.rowIndex]
            val placement = correction?.correctedPlacement ?: row.originalPlacement
            val kills = correction?.correctedKills ?: row.originalKills
            val teamSlot = correction?.correctedTeamSlot ?: row.originalSuggestedTeamSlot
            MatchOcrReviewRowUiState(
                rowIndex = row.rowIndex,
                expectedPlacementLabel = (row.rowIndex + 1).toString(),
                detectedPlacementDisplayValue = placement?.toString() ?: "Unavailable",
                placementStatusLabel = "Preserved OCR evidence",
                detectedKillDisplayValue = kills?.toString() ?: "Unavailable",
                killStatusLabel = "Preserved OCR evidence",
                detectedPlayerNameEvidenceLabel = row.originalOcrText ?: "Unavailable",
                playerNameStatusLabel = "Preserved OCR evidence",
                suggestedTeamSlotDisplayValue = teamSlot?.toString() ?: "Unavailable",
                teamIdentityDisplayValue = teamSlot?.let {
                    displayTeamIdentityLabel(
                        teamSlot = it,
                        teamNamesBySlot = teamNamesBySlot,
                        lobbySlotByTeamSlot = lobbySlotByTeamSlot,
                        usesPairRelativeIdentity = usesPairRelativeIdentity,
                    )
                },
                confidenceScoreDisplayValue = row.confidenceSummary ?: "Unavailable",
                confidenceTierLabel = "Preserved OCR evidence",
                assignmentSafetyStatusLabel = row.safetySummary ?: "Unavailable",
                topThreeSuggestionsSummary = listOf(
                    if (usesPairRelativeIdentity) {
                        "Original suggested identity: ${row.originalSuggestedTeamSlot?.let {
                            displayTeamIdentityLabel(
                                teamSlot = it,
                                teamNamesBySlot = teamNamesBySlot,
                                lobbySlotByTeamSlot = lobbySlotByTeamSlot,
                                usesPairRelativeIdentity = usesPairRelativeIdentity,
                            )
                        } ?: "Unavailable"}"
                    } else {
                        "Original suggested slot: ${row.originalSuggestedTeamSlot ?: "Unavailable"}"
                    },
                ),
                warningLabels = emptyList(),
                blockerLabels = if (row.manualReviewRequired) {
                    listOf("Manual review required")
                } else {
                    emptyList()
                },
                severity = if (row.manualReviewRequired) {
                    MatchOcrReviewSeverity.BLOCKING
                } else {
                    MatchOcrReviewSeverity.INFORMATIONAL
                },
                originalParsedPlacementValue = row.originalPlacement,
                originalParsedKillValue = row.originalKills,
                originalSuggestedTeamSlot = row.originalSuggestedTeamSlot,
            )
        }
        val initialDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(
            rows = reviewRows,
            assignmentRequired = true,
            eligibleTeamSlots = eligibleTeamSlots,
        )
        val finalizedDraft = MatchOcrReviewCorrectionDraftReducer.validate(
            initialDraft.copy(
                rows = initialDraft.rows.map { draftRow ->
                    val reviewRow = reviewRows.first { it.rowIndex == draftRow.rowIndex }
                    draftRow.copy(
                        placementDraftValue = reviewRow.detectedPlacementDisplayValue,
                        killsDraftValue = reviewRow.detectedKillDisplayValue,
                        assignedTeamSlotDraftValue = reviewRow.suggestedTeamSlotDisplayValue,
                    )
                },
            ),
        )
        val unavailableEvidence = reviewRows.any { row ->
            row.detectedPlacementDisplayValue == "Unavailable" ||
                row.detectedKillDisplayValue == "Unavailable" ||
                row.detectedPlayerNameEvidenceLabel == "Unavailable" ||
                row.confidenceScoreDisplayValue == "Unavailable" ||
                row.assignmentSafetyStatusLabel == "Unavailable"
        }
        val manualReviewCount = reviewRows.count { it.blockerLabels.isNotEmpty() }
        return MatchOcrReviewUiState.Ready(
            tournamentId = tournamentId,
            matchId = matchId,
            rowCount = reviewRows.size,
            rows = reviewRows,
            blockerCount = manualReviewCount,
            warningCount = 0,
            safeRowCount = reviewRows.size - manualReviewCount,
            manualRequiredRowCount = manualReviewCount,
            reviewRequiredRowCount = 0,
            manualReviewRequired = manualReviewCount > 0,
            hasUnavailableEvidence = unavailableEvidence,
            correctionDraft = finalizedDraft,
            finalization = MatchOcrReviewFinalizationUiState(isFinalized = true),
            matchResultOcrPreview = MatchResultOcrPreviewUiState.NotRequested,
            teamNamesBySlot = teamNamesBySlot,
            eligibleTeamSlots = eligibleTeamSlots,
            lobbySlotByTeamSlot = lobbySlotByTeamSlot,
            usesPairRelativeIdentity = usesPairRelativeIdentity,
        )
    }
}

private fun MatchOcrReviewUiState.tournamentId(): String? = when (this) {
    is MatchOcrReviewUiState.Calculating -> tournamentId
    is MatchOcrReviewUiState.Empty -> tournamentId
    is MatchOcrReviewUiState.Error -> tournamentId
    is MatchOcrReviewUiState.Ready -> tournamentId
    MatchOcrReviewUiState.Loading -> null
}

private fun MatchOcrReviewUiState.matchId(): String? = when (this) {
    is MatchOcrReviewUiState.Calculating -> matchId
    is MatchOcrReviewUiState.Empty -> matchId
    is MatchOcrReviewUiState.Error -> matchId
    is MatchOcrReviewUiState.Ready -> matchId
    MatchOcrReviewUiState.Loading -> null
}

private val NO_OP_MATCH_RESULT_OCR_PREVIEW_RUNNER = MatchResultOcrPreviewRunner {
    MatchResultOcrPreviewProcessingResult.MissingAsset
}

private val NO_OP_MATCH_LOBBY_PLAYERS_OCR_RUNNER = MatchLobbyPlayersOcrRunner { _, _ ->
    MatchLobbyPlayersOcrResult.unavailable()
}

private val NO_OP_MATCH_LOBBY_SLOT_NUMBER_OCR_RUNNER = MatchLobbySlotNumberOcrRunner { _, _ ->
    MatchLobbySlotNumberOcrResult(
        com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition.entries.map { position ->
            MatchLobbySlotNumberOcrScreenshotResult.Unavailable(
                screenshotPosition = position,
                reason = com.hoggamers.rankforge.data.ocr.matchlobby
                    .MatchLobbySlotNumberOcrUnavailableReason.EXTRACTION_FAILED,
            )
        },
    )
}

private sealed interface MatchLobbyOcrOutcome

private data class PlayerLobbyOutcome(
    val result: MatchLobbyPlayersOcrResult,
) : MatchLobbyOcrOutcome

private data class SlotNumberOnlyLobbyOutcome(
    val result: MatchLobbySlotNumberOcrResult,
) : MatchLobbyOcrOutcome

private fun MatchLobbySlotNumberOcrResult.toMatchLobbyPlayersOcrResult(): MatchLobbyPlayersOcrResult {
    val slots = MatchLobbyPlayersOcrResult.unavailable().slots.toMutableList()
    screenshots
        .filterIsInstance<MatchLobbySlotNumberOcrScreenshotResult.Processed>()
        .forEach { screenshot ->
            (screenshot.teamCropPreviews as? MatchLobbyTeamCropPreviewResult.Available)
                ?.previews
                .orEmpty()
                .forEach { teamCropPreview ->
                    val slotNumber = teamCropPreview.detectedSlotNumber
                        .takeIf { it in TeamSlot.SLOT_NUMBERS }
                        ?: return@forEach
                    val rowPreviewsByRow = teamCropPreview.playerRowPreviews.associateBy { it.row }
                    slots[slotNumber - 1] = MatchLobbyPlayersOcrSlot(
                        slotNumber = slotNumber,
                        players = LobbyPlayerRow.entries
                            .map { row ->
                                MatchLobbyPlayersOcrPlayer(
                                    playerNumber = row.ordinal + 1,
                                    playerName = rowPreviewsByRow[row]?.structuralEvidence,
                                )
                            },
                    )
                }
        }
    return MatchLobbyPlayersOcrResult(slots)
}

private fun MatchLobbyPlayersOcrResult.toUiState(): List<MatchOcrReviewLobbySlotUiState> =
    slots.sortedBy { it.slotNumber }.map { slot ->
        MatchOcrReviewLobbySlotUiState(
            slotNumber = slot.slotNumber,
            players = slot.players.sortedBy { it.playerNumber }.map { player ->
                MatchOcrReviewLobbyPlayerUiState(
                    playerNumber = player.playerNumber,
                    playerName = player.playerName,
                )
            },
        )
    }

private suspend fun MatchLobbyPlayersOcrResult.hasLobbyOcrEvidence(
    matchId: String,
    lobbyScreenshotAssetRepository: MatchLobbyScreenshotAssetRepository,
    ownerUserId: String?,
): Boolean {
    if (ownerUserId.isNullOrBlank()) return false
    val hasUsableScreenshot = try {
        lobbyScreenshotAssetRepository
            .observeByMatchIdAndOwner(matchId, ownerUserId)
            .first()
            .any { asset ->
                asset.cropProfileId == OcrCropValidationProfiles.Lobby.id &&
                    asset.cropLeft != null &&
                    asset.cropTop != null &&
                    asset.cropRight != null &&
                    asset.cropBottom != null
            }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }
    return hasUsableScreenshot || slots != MatchLobbyPlayersOcrResult.unavailable().slots
}

private val NO_OP_MATCHING_REPOSITORY = InMemoryTournamentRepository()
private val NO_OP_OBSERVE_TOURNAMENT_SLOTS = ObserveTournamentSlotsUseCase(NO_OP_MATCHING_REPOSITORY)
private val NO_OP_OBSERVE_ROSTER = ObserveRosterByTournamentUseCase(NO_OP_MATCHING_REPOSITORY)

private fun List<MatchResultOcrPreviewRoleResult>.processedResultRows(): List<MatchResultOcrRow> =
    flatMap { roleResult ->
        (roleResult.result as? MatchResultOcrPreviewProcessingResult.Processed)
            ?.extraction
            ?.rows
            .orEmpty()
    }

private fun MatchOcrCacheReadResult.hasCompleteResultOcr(): Boolean =
    resultRoleResults.size == MatchResultScreenshotRole.entries.size &&
        MatchResultScreenshotRole.entries.all { expectedRole ->
            resultRoleResults.any { roleResult ->
                roleResult.role == expectedRole &&
                    roleResult.result is MatchResultOcrPreviewProcessingResult.Processed
            }
        }

private const val SAFE_AUTOMATIC_ASSIGNMENT_LABEL = "Safe automatic assignment"
private const val REVIEW_REQUIRED_LABEL = "Review required"
private const val MANUAL_REQUIRED_LABEL = "Manual required"
private const val RESULT_LOBBY_AUTOMATIC_LABEL = "Automatic"
private const val RESULT_LOBBY_MANUAL_LABEL = "Manual required"
private const val UNAVAILABLE_LABEL = "Unavailable"
private const val NO_SUGGESTIONS_LABEL = "No suggestions"
