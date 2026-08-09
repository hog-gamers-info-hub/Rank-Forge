package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.export.AndroidExportBlockedReason
import com.hoggamers.rankforge.data.export.AndroidExportResult
import com.hoggamers.rankforge.data.export.AndroidExportType
import com.hoggamers.rankforge.data.export.GoogleSheetsStandingsExportExecutionResult
import com.hoggamers.rankforge.data.export.GoogleSheetsStandingsExportRemoteDataSource
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

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
    fun foundTournamentExposesTwelveSlots() = runTest {
        repository.create(tournament(id = "stable-id"))
        val viewModel = detailsViewModel()

        viewModel.load("stable-id")
        advanceUntilIdle()

        assertEquals((1..12).toList(), viewModel.uiState.value.tournament?.slots?.map { it.slotNumber })
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

    private fun detailsViewModel() = TournamentDetailsViewModel(
        getTournamentById = GetTournamentByIdUseCase(repository),
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
        observeMatches = ObserveMatchesUseCase(repository),
        observeRoster = ObserveRosterByTournamentUseCase(repository),
        googleSheetsStandingsExport = FakeGoogleSheetsStandingsExportRemoteDataSource(),
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
}
