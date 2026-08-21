package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import com.hoggamers.rankforge.domain.sync.SyncQueueEntry
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import com.hoggamers.rankforge.domain.tournament.CloudDeletionFailureCategory
import com.hoggamers.rankforge.domain.tournament.CloudDeletionRepository
import com.hoggamers.rankforge.domain.tournament.CloudDeletionStageResult
import com.hoggamers.rankforge.domain.tournament.DeleteMatchUseCase
import com.hoggamers.rankforge.domain.tournament.DeletionIntent
import com.hoggamers.rankforge.domain.tournament.DeletionIntentPhase
import com.hoggamers.rankforge.domain.tournament.DeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.DeletionTargetType
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.LocalDeletionRepository
import com.hoggamers.rankforge.domain.tournament.LocalDeletionResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchReviewDeletionViewModelTest {
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
    fun successEmitsDetailsNavigationAndDuplicateDeleteIsIgnoredWhileBusy() = runTest {
        val cloud = RecordingCloud().apply { storageGate = CompletableDeferred() }
        val local = RecordingLocal()
        val viewModel = viewModel(cloud = cloud, local = local)
        viewModel.load("tournament-1", "match-1")
        advanceUntilIdle()

        viewModel.deleteMatch()
        assertTrue(viewModel.uiState.value.isDeleting)
        runCurrent()
        viewModel.deleteMatch()
        assertEquals(1, cloud.storageCalls)

        requireNotNull(cloud.storageGate).complete(Unit)
        advanceUntilIdle()

        assertEquals(MatchReviewNavigation.DETAILS, viewModel.uiState.value.navigation)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertNull(viewModel.uiState.value.deletionError)
        assertEquals(1, local.calls)
    }

    @Test
    fun storageFailureStaysOnMatchReview() = runTest {
        val cloud = RecordingCloud().apply {
            storageResult = CloudDeletionStageResult.Failed(CloudDeletionFailureCategory.STORAGE)
        }
        val viewModel = viewModel(cloud = cloud)
        viewModel.load("tournament-1", "match-1")
        advanceUntilIdle()

        viewModel.deleteMatch()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.navigation)
        assertEquals(MatchDeletionUiError.STORAGE_FAILURE, viewModel.uiState.value.deletionError)
        assertFalse(viewModel.uiState.value.isDeleting)
    }

    @Test
    fun remoteFailureStaysOnMatchReview() = runTest {
        val cloud = RecordingCloud().apply {
            remoteResult = CloudDeletionStageResult.Failed(CloudDeletionFailureCategory.REMOTE)
        }
        val viewModel = viewModel(cloud = cloud)
        viewModel.load("tournament-1", "match-1")
        advanceUntilIdle()

        viewModel.deleteMatch()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.navigation)
        assertEquals(MatchDeletionUiError.REMOTE_FAILURE, viewModel.uiState.value.deletionError)
    }

    @Test
    fun authorizationFailureStaysOnMatchReview() = runTest {
        val cloud = RecordingCloud().apply {
            remoteResult = CloudDeletionStageResult.Failed(CloudDeletionFailureCategory.AUTHORIZATION)
        }
        val viewModel = viewModel(cloud = cloud)
        viewModel.load("tournament-1", "match-1")
        advanceUntilIdle()

        viewModel.deleteMatch()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.navigation)
        assertEquals(MatchDeletionUiError.AUTHORIZATION_FAILURE, viewModel.uiState.value.deletionError)
    }

    @Test
    fun authenticationFailureStaysOnMatchReview() = runTest {
        val viewModel = viewModel(authState = AuthState.SignedOut)
        viewModel.load("tournament-1", "match-1")
        advanceUntilIdle()

        viewModel.deleteMatch()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.navigation)
        assertEquals(MatchDeletionUiError.AUTHENTICATION_REQUIRED, viewModel.uiState.value.deletionError)
    }

    @Test
    fun localCleanupFailureCanRetryToSuccessWithoutSecondUiNavigationBeforeSuccess() = runTest {
        val local = RecordingLocal().apply {
            results += LocalDeletionResult.FileCleanupFailed
            results += LocalDeletionResult.Deleted
        }
        val viewModel = viewModel(local = local)
        viewModel.load("tournament-1", "match-1")
        advanceUntilIdle()

        viewModel.deleteMatch()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.navigation)
        assertEquals(MatchDeletionUiError.LOCAL_CLEANUP_FAILURE, viewModel.uiState.value.deletionError)

        viewModel.deleteMatch()
        advanceUntilIdle()
        assertEquals(MatchReviewNavigation.DETAILS, viewModel.uiState.value.navigation)
        assertEquals(2, local.calls)
    }

    @Test
    fun successfulDeletionDoesNotRenumberSurvivingMatches() = runTest {
        val viewModel = viewModel()
        viewModel.load("tournament-1", "match-1")
        advanceUntilIdle()

        viewModel.deleteMatch()
        advanceUntilIdle()

        assertEquals(
            listOf(1, 3),
            repository.observeMatchesByTournamentId("tournament-1").first().map { it.matchNumber },
        )
    }

    private fun viewModel(
        cloud: RecordingCloud = RecordingCloud(),
        local: RecordingLocal = RecordingLocal(),
        authState: AuthState = AuthState.SignedIn(AuthUser("owner-1", "owner@example.com")),
    ) = MatchReviewViewModel(
        getTournamentById = GetTournamentByIdUseCase(repository),
        observeMatches = ObserveMatchesUseCase(repository),
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
        observeRoster = ObserveRosterByTournamentUseCase(repository),
        observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
        validateMatchResult = ValidateMatchResultUseCase(),
        finalizeMatch = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase()),
        imageCandidateValidator = ImageCandidateValidator(
            ImageCandidateMetadataReader { ImageCandidateReadResult.Unreadable },
        ),
        screenshotDuplicateDetector = ScreenshotDuplicateDetector(
            ImageSourceFingerprintGenerator(ImageSourceStreamOpener { null }),
        ),
        localImagePreserver = LocalImagePreserver(
            appPrivateRoot = Files.createTempDirectory("rank-forge-delete-ui").toFile(),
            sourceStreamOpener = ImageSourceStreamOpener { null },
            mimeTypeReader = ImageSourceMimeTypeReader { null },
            ioDispatcher = Dispatchers.Unconfined,
        ),
        deleteMatchUseCase = DeleteMatchUseCase(
            repository,
            TestAuthRepository(authState),
            TestQueueRepository(),
            cloud,
            local,
            TestDeletionIntentRepository(),
        ),
    )
}

private class TestTournamentRepository : TournamentRepository {
    private val tournament = Tournament(
        id = "tournament-1",
        name = "Test Cup",
        date = LocalDate.of(2026, 8, 21),
        organizerName = "Test Organizer",
        organizerContactNumber = "000",
        status = TournamentStatus.CONFIRMED,
    )
    private val match = Match(
        id = "match-1",
        tournamentId = "tournament-1",
        matchNumber = 1,
        date = LocalDate.of(2026, 8, 21),
        mapName = "Map A",
        status = MatchStatus.DRAFT,
    )
    private val survivingMatch = match.copy(id = "match-3", matchNumber = 3)

    override suspend fun create(tournament: Tournament) = Unit
    override fun observeAll(): Flow<List<Tournament>> = flowOf(listOf(tournament))
    override fun observeById(tournamentId: String): Flow<Tournament?> = flowOf(tournament.takeIf { it.id == tournamentId })
    override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> = flowOf(TeamSlot.fixedSlotsForTournament(tournamentId))
    override suspend fun saveTeamNames(tournamentId: String, teamNamesBySlotNumber: Map<Int, String>) = Unit
    override fun observeRosterByTournamentAndSlot(tournamentId: String, slotNumber: Int): Flow<List<RosterPlayer>> = flowOf(emptyList())
    override fun observeRosterByTournamentId(tournamentId: String): Flow<Map<Int, List<RosterPlayer>>> = flowOf(emptyMap())
    override suspend fun saveRoster(tournamentId: String, slotNumber: Int, players: List<RosterPlayer>) = Unit
    override suspend fun confirmTournament(tournamentId: String): Boolean = false
    override fun observeMatchesByTournamentId(tournamentId: String): Flow<List<Match>> =
        flowOf(listOf(match, survivingMatch).filter { it.tournamentId == tournamentId })
    override fun observeMatchById(matchId: String): Flow<Match?> = flowOf(match.takeIf { it.id == matchId })
}

private class TestAuthRepository(private val state: AuthState) : AuthRepository {
    override fun observeAuthState(): Flow<AuthState> = flowOf(state)
    override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
    override suspend fun signUp(email: String, password: String): AuthOperationResult = failure()
    override suspend fun login(email: String, password: String): AuthOperationResult = failure()
    override suspend fun logout(): AuthOperationResult = failure()
    private fun failure() = AuthOperationResult.Failure(AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure))
}

private class TestQueueRepository : PersistentSyncQueueRepository {
    override fun observeAll(): Flow<List<SyncQueueEntry>> = flowOf(emptyList())
    override suspend fun enqueue(operationType: SyncQueueOperationType, tournamentId: String?, status: SyncQueueStatus, failureCategory: String?) =
        SyncQueueEntry("queue-1", operationType, tournamentId, 0L, status, failureCategory, 0)
    override suspend fun completeOldestUnresolved(operationType: SyncQueueOperationType, tournamentId: String?) = Unit
    override suspend fun incrementAttemptCount(id: String) = Unit
    override suspend fun updateRetryFailure(id: String, status: SyncQueueStatus, failureCategory: String?) = Unit
    override suspend fun markCompleted(id: String) = Unit
    override suspend fun remove(id: String) = Unit
    override suspend fun purgeByTournamentId(tournamentId: String) = Unit
}

private class RecordingCloud : CloudDeletionRepository {
    var storageResult: CloudDeletionStageResult = CloudDeletionStageResult.Success
    var remoteResult: CloudDeletionStageResult = CloudDeletionStageResult.Success
    var storageGate: CompletableDeferred<Unit>? = null
    var storageCalls = 0

    override suspend fun deleteMatchStorage(tournamentId: String, matchId: String): CloudDeletionStageResult {
        storageCalls++
        storageGate?.await()
        return storageResult
    }
    override suspend fun deleteMatchRemote(tournamentId: String, matchId: String): CloudDeletionStageResult = remoteResult
    override suspend fun deleteTournamentStorage(tournamentId: String, matchIds: Set<String>): CloudDeletionStageResult = CloudDeletionStageResult.Success
    override suspend fun deleteTournamentRemote(tournamentId: String): CloudDeletionStageResult = CloudDeletionStageResult.Success
}

private class RecordingLocal : LocalDeletionRepository {
    val results = mutableListOf<LocalDeletionResult>()
    var calls = 0
    override suspend fun deleteMatchLocally(matchId: String): LocalDeletionResult {
        calls++
        return results.removeFirstOrNull() ?: LocalDeletionResult.Deleted
    }
    override suspend fun deleteTournamentLocally(tournamentId: String): LocalDeletionResult = LocalDeletionResult.Deleted
}

private class TestDeletionIntentRepository : DeletionIntentRepository {
    private val intents = mutableMapOf<Pair<DeletionTargetType, String>, DeletionIntent>()
    override suspend fun read(targetType: DeletionTargetType, targetId: String): DeletionIntent? = intents[targetType to targetId]
    override suspend fun start(intent: DeletionIntent): DeletionIntent {
        intents[intent.targetType to intent.targetId] = intent
        return intent
    }
    override suspend fun markRemoteDeleted(targetType: DeletionTargetType, targetId: String) {
        val key = targetType to targetId
        intents[key] = requireNotNull(intents[key]).copy(
            phase = DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING,
        )
    }
    override suspend fun clear(targetType: DeletionTargetType, targetId: String) {
        intents.remove(targetType to targetId)
    }
    override suspend fun isBlocking(tournamentId: String): Boolean = intents.values.any { it.tournamentId == tournamentId }
    override suspend fun readAll(): List<DeletionIntent> = intents.values.toList()
    override suspend fun readPendingLocalCleanup(): List<DeletionIntent> = intents.values.filter {
        it.phase == DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING
    }
}
