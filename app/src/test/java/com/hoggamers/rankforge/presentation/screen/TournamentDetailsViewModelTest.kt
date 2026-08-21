package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.export.AndroidExportBlockedReason
import com.hoggamers.rankforge.data.export.AndroidExportResult
import com.hoggamers.rankforge.data.export.AndroidExportType
import com.hoggamers.rankforge.data.export.GoogleSheetsStandingsExportExecutionResult
import com.hoggamers.rankforge.data.export.GoogleSheetsStandingsExportRemoteDataSource
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.CreateNextMatchUseCase
import com.hoggamers.rankforge.domain.tournament.CreateMatchRepositoryResult
import com.hoggamers.rankforge.domain.tournament.CreateMatchResult
import com.hoggamers.rankforge.domain.tournament.MatchCreationFailure
import com.hoggamers.rankforge.domain.tournament.MAX_MATCHES_PER_TOURNAMENT
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncResult

@OptIn(ExperimentalCoroutinesApi::class)
class TournamentDetailsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: TestTournamentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = TestTournamentRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun foundTournamentRendersDetailsState() = runTest {
        val tournament = tournament(id = "stable-id")
        repository.create(tournament)
        val viewModel = detailsViewModel()

        viewModel.load("stable-id")
        advanceUntilIdle()

        assertEquals("Summer Cup", viewModel.uiState.value.tournament?.name)
        assertEquals("123", viewModel.uiState.value.tournament?.organizerContactNumber)
    }

    @Test
    fun calculatePointsWithEightTeamsShowsConfirmationWithoutRequestingMatch() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.saveTeamNames("stable-id", (1..8).associateWith { "Team $it" })
        val viewModel = detailsViewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onCalculatePointsRequested()
        advanceUntilIdle()

        assertEquals(
            TeamCountConfirmationUiState(enteredCount = 8, emptyCount = 4),
            viewModel.uiState.value.pendingTeamCountConfirmation,
        )
        assertNull(viewModel.uiState.value.matchReviewRequest)
    }

    @Test
    fun useEnteredTeamsRequestsMatchOnceAndLeavesTrailingBlanks() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.saveTeamNames("stable-id", (1..8).associateWith { "Team $it" })
        val viewModel = detailsViewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()
        viewModel.onCalculatePointsRequested()
        advanceUntilIdle()

        viewModel.useEnteredTeams()
        advanceUntilIdle()

        assertEquals("stable-id", viewModel.uiState.value.matchReviewRequest?.tournamentId)
        assertTrue(repository.observeSlotsByTournamentId("stable-id").first().drop(8).all { it.teamName.isBlank() })
        viewModel.onMatchReviewRequestHandled()
        assertNull(viewModel.uiState.value.matchReviewRequest)
    }

    @Test
    fun createdMatchAppliesTemplateSyncsDraftThenCheckpointsInheritedLobby() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.saveTeamNames("stable-id", (1..12).associateWith { "Team $it" })
        val events = mutableListOf<String>()
        val apply = ApplyLobbyTemplateAction { _, matchId ->
            events += "apply:$matchId"
            ApplyLobbyTemplateResult.Applied
        }
        val upload = MatchLobbyScreenshotUploadCheckpointAction { identity ->
            events += "upload:${identity.lobbyScreenshotIndex}"
            MatchLobbyScreenshotUploadCheckpointResult.Completed
        }
        val sync = RecordingDraftMatchCloudSyncAction(onInvoke = { events += "sync" })
        val viewModel = detailsViewModel(
            syncDraftMatches = sync,
            applyLobbyTemplate = apply,
            lobbyUploadCheckpoint = upload,
        )
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onCalculatePointsRequested()
        advanceUntilIdle()

        assertEquals("stable-id", viewModel.uiState.value.matchReviewRequest?.tournamentId)
        assertTrue(events.first().startsWith("apply:"))
        assertEquals(listOf("sync", "upload:1", "upload:2", "upload:3"), events.drop(1))
    }

    @Test
    fun unavailableTemplateAfterUnsaveCreatesNextMatchWithoutInheritedLobbyCheckpoints() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.saveTeamNames("stable-id", (1..12).associateWith { "Team $it" })
        var templateActive = true
        val events = mutableListOf<String>()
        val apply = ApplyLobbyTemplateAction { _, matchId ->
            events += "apply:$matchId"
            if (templateActive) ApplyLobbyTemplateResult.Applied else ApplyLobbyTemplateResult.Unavailable
        }
        val upload = MatchLobbyScreenshotUploadCheckpointAction { identity ->
            events += "upload:${identity.lobbyScreenshotIndex}"
            MatchLobbyScreenshotUploadCheckpointResult.Completed
        }
        val viewModel = detailsViewModel(
            syncDraftMatches = RecordingDraftMatchCloudSyncAction(onInvoke = { events += "sync" }),
            applyLobbyTemplate = apply,
            lobbyUploadCheckpoint = upload,
        )
        viewModel.load("stable-id")
        advanceUntilIdle()
        templateActive = false

        viewModel.onCalculatePointsRequested()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.matchReviewRequest)
        assertEquals(listOf("apply:${viewModel.uiState.value.matchReviewRequest?.matchId}", "sync"), events)
    }

    @Test
    fun useDefaultsPersistsRemainingNamesAndRequestsMatch() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.saveTeamNames("stable-id", (1..8).associateWith { "Team $it" })
        val viewModel = detailsViewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()
        viewModel.onCalculatePointsRequested()
        advanceUntilIdle()

        viewModel.useDefaults()
        advanceUntilIdle()

        assertEquals("stable-id", viewModel.uiState.value.matchReviewRequest?.tournamentId)
        assertEquals(
            (1..8).map { "Team $it" } + (9..12).map { "Team ${it.toString().padStart(2, '0')}" },
            repository.observeSlotsByTournamentId("stable-id").first().map { it.teamName },
        )
    }

    @Test
    fun sparseTeamSlotsRequestCountConfirmation() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.saveTeamNames("stable-id", mapOf(1 to "Alpha", 3 to "Charlie"))
        val syncAction = RecordingDraftMatchCloudSyncAction()
        val viewModel = detailsViewModel(syncAction)
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onCalculatePointsRequested()
        advanceUntilIdle()

        assertEquals(
            TeamCountConfirmationUiState(enteredCount = 2, emptyCount = 10),
            viewModel.uiState.value.pendingTeamCountConfirmation,
        )
        assertNull(viewModel.uiState.value.calculatePointsMessage)
        assertNull(viewModel.uiState.value.matchReviewRequest)
        assertTrue(syncAction.tournamentIds.isEmpty())

        viewModel.useEnteredTeams()
        advanceUntilIdle()

        assertEquals("stable-id", viewModel.uiState.value.matchReviewRequest?.tournamentId)
    }

    @Test
    fun matchLimitRejectionDoesNotSyncOrNavigate() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.saveTeamNames("stable-id", (1..12).associateWith { "Team $it" })
        repository.setMatches(
            "stable-id",
            (1..MAX_MATCHES_PER_TOURNAMENT).map { matchNumber ->
                Match(
                    id = "match-$matchNumber",
                    tournamentId = "stable-id",
                    matchNumber = matchNumber,
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                    status = MatchStatus.DRAFT,
                )
            },
        )
        val syncAction = RecordingDraftMatchCloudSyncAction()
        val viewModel = detailsViewModel(syncAction)
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onCalculatePointsRequested()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.calculatePointsMessage)
        assertNull(viewModel.uiState.value.matchReviewRequest)
        assertTrue(syncAction.tournamentIds.isEmpty())
    }

    @Test
    fun rejectedCreationDoesNotSyncOrNavigate() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.saveTeamNames("stable-id", (1..12).associateWith { "Team $it" })
        repository.rejectMatchCreation = true
        val syncAction = RecordingDraftMatchCloudSyncAction()
        val viewModel = detailsViewModel(syncAction)
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onCalculatePointsRequested()
        advanceUntilIdle()

        assertEquals(CalculatePointsMessage.MATCH_CREATION_FAILED, viewModel.uiState.value.calculatePointsMessage)
        assertNull(viewModel.uiState.value.matchReviewRequest)
        assertTrue(syncAction.tournamentIds.isEmpty())
    }

    @Test
    fun twelveTeamsRequestMatchWithoutConfirmation() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.saveTeamNames("stable-id", (1..12).associateWith { "Team $it" })
        val viewModel = detailsViewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onCalculatePointsRequested()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingTeamCountConfirmation)
        val request = viewModel.uiState.value.matchReviewRequest
        assertEquals("stable-id", request?.tournamentId)
        val firstMatch = repository.observeMatchesByTournamentId("stable-id").first().single()
        assertEquals(MatchReviewRequest("stable-id", firstMatch.id), request)
        assertEquals(1, firstMatch.matchNumber)

        viewModel.onMatchReviewRequestHandled()
        assertNull(viewModel.uiState.value.matchReviewRequest)
        viewModel.onCalculatePointsRequested()
        advanceUntilIdle()
        val matches = repository.observeMatchesByTournamentId("stable-id").first()
        assertEquals(2, matches.size)
        assertEquals(listOf(1, 2), matches.map { it.matchNumber })
        assertEquals(MatchReviewRequest("stable-id", matches[1].id), viewModel.uiState.value.matchReviewRequest)
    }

    @Test
    fun successfulCreationSyncsExactlyOnceBeforeReviewRequest() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.saveTeamNames("stable-id", (1..12).associateWith { "Team $it" })
        var localMatchExistsWhenSyncStarts = false
        val syncAction = RecordingDraftMatchCloudSyncAction(
            result = DraftMatchCloudSyncResult.Success,
            onInvoke = {
                localMatchExistsWhenSyncStarts = repository.observeMatchesByTournamentId("stable-id")
                    .first()
                    .isNotEmpty()
            },
        )
        val viewModel = detailsViewModel(syncAction)
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onCalculatePointsRequested()
        advanceUntilIdle()

        val match = repository.observeMatchesByTournamentId("stable-id").first().single()
        assertEquals(listOf("stable-id"), syncAction.tournamentIds)
        assertTrue(localMatchExistsWhenSyncStarts)
        assertEquals(MatchReviewRequest("stable-id", match.id), viewModel.uiState.value.matchReviewRequest)
    }

    @Test
    fun networkFailureAfterCreationKeepsLocalMatchAndReviewNavigation() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.saveTeamNames("stable-id", (1..12).associateWith { "Team $it" })
        val syncAction = RecordingDraftMatchCloudSyncAction(
            result = DraftMatchCloudSyncResult.NetworkFailure,
            queueRecordingResult = QueueRecordingResult.RECORDED,
        )
        val viewModel = detailsViewModel(syncAction)
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onCalculatePointsRequested()
        advanceUntilIdle()

        val match = repository.observeMatchesByTournamentId("stable-id").first().single()
        assertEquals(listOf("stable-id"), syncAction.tournamentIds)
        assertEquals(MatchReviewRequest("stable-id", match.id), viewModel.uiState.value.matchReviewRequest)
        assertNull(viewModel.uiState.value.calculatePointsMessage)
    }

    @Test
    fun authenticationRequiredAfterCreationKeepsLocalMatchAndReviewNavigation() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.saveTeamNames("stable-id", (1..12).associateWith { "Team $it" })
        val syncAction = RecordingDraftMatchCloudSyncAction(
            result = DraftMatchCloudSyncResult.AuthenticationRequired,
            queueRecordingResult = QueueRecordingResult.RECORDED,
        )
        val viewModel = detailsViewModel(syncAction)
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onCalculatePointsRequested()
        advanceUntilIdle()

        val match = repository.observeMatchesByTournamentId("stable-id").first().single()
        assertEquals(listOf("stable-id"), syncAction.tournamentIds)
        assertEquals(MatchReviewRequest("stable-id", match.id), viewModel.uiState.value.matchReviewRequest)
        assertNull(viewModel.uiState.value.calculatePointsMessage)
    }

    @Test
    fun repeatedCalculatePointsWhileMatchCreationIsPendingDoesNotDuplicate() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.saveTeamNames("stable-id", (1..12).associateWith { "Team $it" })
        repository.blockMatchCreation = true
        val syncAction = RecordingDraftMatchCloudSyncAction()
        val viewModel = detailsViewModel(syncAction)
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onCalculatePointsRequested()
        runCurrent()
        viewModel.onCalculatePointsRequested()
        runCurrent()

        assertTrue(viewModel.uiState.value.isCreatingMatch)
        assertEquals(0, repository.observeMatchesByTournamentId("stable-id").first().size)

        repository.releaseMatchCreation.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.observeMatchesByTournamentId("stable-id").first().size)
        assertNotNull(viewModel.uiState.value.matchReviewRequest)
        assertEquals(listOf("stable-id"), syncAction.tournamentIds)
    }

    @Test
    fun localDetailsRemainVisibleWhileExistingTournamentUploadIsQueued() = runTest {
        repository.create(tournament(id = "stable-id"))
        val detailsViewModel = detailsViewModel()
        detailsViewModel.load("stable-id")
        advanceUntilIdle()

        var requestedTournamentId: String? = null
        val uploadViewModel = TournamentCloudUploadViewModel(
            TournamentCloudUploadAction { tournamentId ->
                requestedTournamentId = tournamentId
                QueueAwareActionResult(
                    primaryResult = TournamentCloudUploadResult.NetworkFailure,
                    queueRecordingResult = QueueRecordingResult.RECORDED,
                )
            },
        )
        uploadViewModel.upload("stable-id")
        advanceUntilIdle()

        assertEquals("stable-id", detailsViewModel.uiState.value.tournament?.id)
        assertEquals("Summer Cup", detailsViewModel.uiState.value.tournament?.name)
        assertEquals("stable-id", requestedTournamentId)
        assertEquals(TournamentCloudUploadUiState.Queued, uploadViewModel.uiState.value)
    }

    @Test
    fun finalizedStandingsCsvExportPreservesTournamentIdentity() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.saveTeamNames(
            "stable-id",
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        repository.setMatches("stable-id", listOf(finalizedMatch()))
        val viewModel = detailsViewModel()

        viewModel.load("stable-id")
        advanceUntilIdle()
        viewModel.prepareStandingsCsvExport()
        advanceUntilIdle()

        val result = viewModel.uiState.value.csvExportResult
        assertTrue(result is AndroidExportResult.CsvReady)
        assertEquals(AndroidExportType.STANDINGS_CSV, result?.request?.type)
        assertEquals("stable-id", result?.request?.tournamentId)
        assertEquals(null, result?.request?.matchId)
        assertTrue((result as AndroidExportResult.CsvReady).content.contains("stable-id"))

        viewModel.prepareGoogleSheetsStandingsExport()
        advanceUntilIdle()
        assertEquals(
            AndroidExportType.STANDINGS_GOOGLE_SHEETS,
            viewModel.uiState.value.googleSheetsExportResult?.request?.type,
        )
        assertEquals("stable-id", viewModel.uiState.value.googleSheetsExportResult?.request?.tournamentId)
        assertTrue(
            viewModel.uiState.value.googleSheetsExportResult is AndroidExportResult.GoogleSheetsSuccess,
        )
    }

    @Test
    fun standingsCsvExportIsBlockedWhenNoFinalizedMatchesExist() = runTest {
        repository.create(tournament(id = "stable-id"))
        val viewModel = detailsViewModel()

        viewModel.load("stable-id")
        advanceUntilIdle()
        viewModel.prepareStandingsCsvExport()
        advanceUntilIdle()

        assertEquals(
            AndroidExportResult.Blocked(
                request = com.hoggamers.rankforge.data.export.AndroidExportRequest(
                    type = AndroidExportType.STANDINGS_CSV,
                    tournamentId = "stable-id",
                ),
                reason = AndroidExportBlockedReason.NO_FINALIZED_STANDINGS,
            ),
            viewModel.uiState.value.csvExportResult,
        )
        assertEquals("stable-id", viewModel.uiState.value.tournament?.id)
    }

    @Test
    fun foundTournamentWithNoTeamsExposesNoActiveSlots() = runTest {
        repository.create(tournament(id = "stable-id"))
        val viewModel = detailsViewModel()

        viewModel.load("stable-id")
        advanceUntilIdle()

        assertEquals(emptyList<Int>(), viewModel.uiState.value.tournament?.slots?.map { it.slotNumber })
    }

    @Test
    fun foundTournamentExposesSavedTeamNames() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.saveTeamNames("stable-id", mapOf(1 to "Alpha"))
        val viewModel = detailsViewModel()

        viewModel.load("stable-id")
        advanceUntilIdle()

        assertEquals("Alpha", viewModel.uiState.value.tournament?.slots?.first { it.slotNumber == 1 }?.teamName)
    }

    @Test
    fun foundDraftMatchExposesResultValidationIssues() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.setMatches(
            "stable-id",
            listOf(
                Match(
                    id = "match-id",
                    tournamentId = "stable-id",
                    matchNumber = 1,
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                    status = MatchStatus.DRAFT,
                ),
            ),
        )
        val viewModel = detailsViewModel()

        viewModel.load("stable-id")
        advanceUntilIdle()

        val issues = viewModel.uiState.value.tournament?.matches?.single()?.validationIssues.orEmpty()
        assertTrue(
            issues.any {
                it.teamSlotNumber == 1 &&
                    it.error == MatchResultValidationError.MISSING_PLACEMENT
            },
        )
        assertTrue(
            issues.any {
                it.teamSlotNumber == 1 &&
                    it.error == MatchResultValidationError.MISSING_KILLS
            },
        )
    }

    @Test
    fun finalizedMatchRefreshesSameTournamentDetailsWithoutDuplicateRows() = runTest {
        repository.create(tournament(id = "stable-id"))
        repository.setMatches(
            "stable-id",
            listOf(
                Match(
                    id = "match-id",
                    tournamentId = "stable-id",
                    matchNumber = 1,
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                    status = MatchStatus.DRAFT,
                ),
            ),
        )
        val viewModel = detailsViewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()

        repository.setMatches("stable-id", listOf(finalizedMatch()))
        advanceUntilIdle()

        assertEquals("stable-id", viewModel.uiState.value.tournament?.id)
        assertEquals(MatchStatus.FINALIZED, viewModel.uiState.value.tournament?.matches?.single()?.status)

        repository.setMatches("stable-id", listOf(finalizedMatch()))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.tournament?.matches?.size)
    }

    @Test
    fun unknownTournamentRendersNotFoundState() = runTest {
        val viewModel = detailsViewModel()

        viewModel.load("missing")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isNotFound)
        assertEquals(null, viewModel.uiState.value.csvExportResult)
        assertEquals(null, viewModel.uiState.value.googleSheetsExportResult)
    }

    private fun detailsViewModel(
        syncDraftMatches: DraftMatchCloudSyncAction = RecordingDraftMatchCloudSyncAction(),
        applyLobbyTemplate: ApplyLobbyTemplateAction = ApplyLobbyTemplateAction { _, _ -> ApplyLobbyTemplateResult.Unavailable },
        lobbyUploadCheckpoint: MatchLobbyScreenshotUploadCheckpointAction = MatchLobbyScreenshotUploadCheckpointAction { MatchLobbyScreenshotUploadCheckpointResult.Skipped },
    ) = TournamentDetailsViewModel(
        getTournamentById = GetTournamentByIdUseCase(repository),
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
        observeMatches = ObserveMatchesUseCase(repository),
        observeRoster = ObserveRosterByTournamentUseCase(repository),
        googleSheetsStandingsExport = FakeGoogleSheetsStandingsExportRemoteDataSource(),
        saveTeamSlotNames = SaveTeamSlotNamesUseCase(repository),
        validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
        createNextMatch = CreateNextMatchUseCase(repository),
        syncDraftMatches = syncDraftMatches,
        applyLobbyTemplate = applyLobbyTemplate,
        lobbyUploadCheckpoint = lobbyUploadCheckpoint,
    )

    private fun tournament(id: String) = Tournament(
        id = id,
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
    )

    private fun finalizedMatch() = Match(
        id = "match-id",
        tournamentId = "stable-id",
        matchNumber = 1,
        date = LocalDate.of(2026, 7, 24),
        mapName = "Bermuda",
        status = MatchStatus.FINALIZED,
        placements = (1..12).map { slotNumber -> MatchPlacement(slotNumber, slotNumber) },
        kills = (1..12).map { slotNumber -> MatchKill(slotNumber, slotNumber - 1) },
    )

    private class TestTournamentRepository : TournamentRepository {
        private val state = MutableStateFlow<List<Tournament>>(emptyList())
        private val matchesState = MutableStateFlow<Map<String, List<Match>>>(emptyMap())
        val releaseMatchCreation = CompletableDeferred<Unit>()
        var blockMatchCreation = false
        var rejectMatchCreation = false

        override suspend fun create(tournament: Tournament) {
            state.value = state.value + tournament
        }

        override fun observeAll(): Flow<List<Tournament>> = state

        override fun observeById(tournamentId: String): Flow<Tournament?> =
            state.map { tournaments -> tournaments.firstOrNull { it.id == tournamentId } }

        override fun observeMatchesByTournamentId(tournamentId: String): Flow<List<Match>> =
            matchesState.map { matches -> matches[tournamentId].orEmpty() }

        fun setMatches(tournamentId: String, matches: List<Match>) {
            matchesState.value = matchesState.value + (tournamentId to matches)
        }

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> =
            state.map { tournaments ->
                if (tournaments.any { it.id == tournamentId }) {
                    slotsByTournamentId[tournamentId] ?: TeamSlot.fixedSlotsForTournament(tournamentId)
                } else {
                    emptyList()
                }
            }

        private val slotsByTournamentId = mutableMapOf<String, List<TeamSlot>>()

        override suspend fun saveTeamNames(
            tournamentId: String,
            teamNamesBySlotNumber: Map<Int, String>,
        ) {
            slotsByTournamentId[tournamentId] = TeamSlot.fixedSlotsForTournament(tournamentId).map { slot ->
                if (teamNamesBySlotNumber.containsKey(slot.slotNumber)) {
                    slot.copy(teamName = teamNamesBySlotNumber.getValue(slot.slotNumber).trim())
                } else {
                    slot
                }
            }
        }

        override fun observeRosterByTournamentAndSlot(
            tournamentId: String,
            slotNumber: Int,
        ): Flow<List<com.hoggamers.rankforge.domain.tournament.RosterPlayer>> =
            kotlinx.coroutines.flow.flowOf(emptyList())

        override fun observeRosterByTournamentId(
            tournamentId: String,
        ): Flow<Map<Int, List<com.hoggamers.rankforge.domain.tournament.RosterPlayer>>> =
            kotlinx.coroutines.flow.flowOf(emptyMap())

        override suspend fun saveRoster(
            tournamentId: String,
            slotNumber: Int,
            players: List<com.hoggamers.rankforge.domain.tournament.RosterPlayer>,
        ) = Unit

        override suspend fun confirmTournament(tournamentId: String): Boolean = false

        override suspend fun createDraftMatch(match: Match): com.hoggamers.rankforge.domain.tournament.CreateMatchRepositoryResult {
            if (blockMatchCreation) releaseMatchCreation.await()
            if (rejectMatchCreation) {
                return CreateMatchRepositoryResult.Rejected(MatchCreationFailure.DUPLICATE_MATCH_NUMBER)
            }
            val current = matchesState.value[match.tournamentId].orEmpty()
            if (current.size >= MAX_MATCHES_PER_TOURNAMENT) {
                return CreateMatchRepositoryResult.Rejected(MatchCreationFailure.LIMIT_REACHED)
            }
            if (current.any { it.matchNumber == match.matchNumber }) {
                return CreateMatchRepositoryResult.Rejected(MatchCreationFailure.DUPLICATE_MATCH_NUMBER)
            }
            matchesState.value = matchesState.value + (match.tournamentId to (current + match))
            return CreateMatchRepositoryResult.Created
        }
    }

    private class FakeGoogleSheetsStandingsExportRemoteDataSource :
        GoogleSheetsStandingsExportRemoteDataSource {
        override suspend fun export(
            tournamentId: String,
            rows: List<com.hoggamers.rankforge.domain.export.TournamentStandingsExportRow>,
        ): GoogleSheetsStandingsExportExecutionResult =
            GoogleSheetsStandingsExportExecutionResult.Success(
                exportedMatchCount = rows.firstOrNull()?.exportedMatchCount ?: 0,
                rowsWritten = rows.size,
            )
    }

    private class RecordingDraftMatchCloudSyncAction(
        private val result: DraftMatchCloudSyncResult = DraftMatchCloudSyncResult.Success,
        private val queueRecordingResult: QueueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
        private val onInvoke: suspend () -> Unit = {},
    ) : DraftMatchCloudSyncAction {
        val tournamentIds = mutableListOf<String>()

        override suspend fun invoke(
            tournamentId: String,
        ): QueueAwareActionResult<DraftMatchCloudSyncResult> {
            tournamentIds += tournamentId
            onInvoke()
            return QueueAwareActionResult(result, queueRecordingResult)
        }
    }
}
