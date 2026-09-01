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
import com.hoggamers.rankforge.domain.tournament.CreateMatchRepositoryResult
import com.hoggamers.rankforge.domain.tournament.DeleteTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.DeletionIntent
import com.hoggamers.rankforge.domain.tournament.DeletionIntentPhase
import com.hoggamers.rankforge.domain.tournament.DeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.DeletionTargetType
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.LocalDeletionRepository
import com.hoggamers.rankforge.domain.tournament.LocalDeletionResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchDraftFieldValues
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.CreateNextMatchUseCase
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncResult
import com.hoggamers.rankforge.data.export.GoogleSheetsStandingsExportRemoteDataSource
import com.hoggamers.rankforge.data.export.GoogleSheetsStandingsExportExecutionResult
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
class TournamentDetailsDeletionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: DeletionTournamentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = DeletionTournamentRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun successfulDeletionUsesDisplayedIdOnceAndEmitsOneListNavigation() = runTest {
        val cloud = TournamentDeletionRecordingCloud().apply { remoteGate = CompletableDeferred() }
        val viewModel = viewModel(cloud = cloud)
        viewModel.load("tournament-1")
        advanceUntilIdle()

        viewModel.deleteTournament()
        assertTrue(viewModel.uiState.value.isDeleting)
        runCurrent()
        viewModel.deleteTournament()
        assertEquals(1, cloud.remoteCalls)

        requireNotNull(cloud.remoteGate).complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("tournament-1"), cloud.remoteTournamentIds)
        assertEquals(TournamentDetailsNavigation.TOURNAMENT_LIST, viewModel.uiState.value.navigation)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertNull(viewModel.uiState.value.deletionError)
        assertEquals("Summer Cup", viewModel.uiState.value.tournament?.name)

        viewModel.onNavigationHandled()
        assertNull(viewModel.uiState.value.navigation)
    }

    @Test
    fun storageFailureStaysOnDetailsWithSafeError() = runTest {
        val viewModel = viewModel(
            cloud = TournamentDeletionRecordingCloud().apply {
                storageResult = CloudDeletionStageResult.Failed(CloudDeletionFailureCategory.STORAGE)
            },
        )
        viewModel.load("tournament-1")
        advanceUntilIdle()

        viewModel.deleteTournament()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.navigation)
        assertEquals(TournamentDeletionUiError.STORAGE_FAILURE, viewModel.uiState.value.deletionError)
        assertFalse(viewModel.uiState.value.isDeleting)
    }

    @Test
    fun remoteFailureStaysOnDetailsWithSafeError() = runTest {
        val viewModel = viewModel(
            cloud = TournamentDeletionRecordingCloud().apply {
                remoteResult = CloudDeletionStageResult.Failed(CloudDeletionFailureCategory.REMOTE)
            },
        )
        viewModel.load("tournament-1")
        advanceUntilIdle()

        viewModel.deleteTournament()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.navigation)
        assertEquals(TournamentDeletionUiError.REMOTE_FAILURE, viewModel.uiState.value.deletionError)
    }

    @Test
    fun authorizationFailureStaysOnDetailsWithSafeError() = runTest {
        val viewModel = viewModel(
            cloud = TournamentDeletionRecordingCloud().apply {
                remoteResult = CloudDeletionStageResult.Failed(CloudDeletionFailureCategory.AUTHORIZATION)
            },
        )
        viewModel.load("tournament-1")
        advanceUntilIdle()

        viewModel.deleteTournament()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.navigation)
        assertEquals(TournamentDeletionUiError.AUTHORIZATION_FAILURE, viewModel.uiState.value.deletionError)
    }

    @Test
    fun authenticationFailureStaysOnDetailsWithSafeError() = runTest {
        val viewModel = viewModel(authState = AuthState.SignedOut)
        viewModel.load("tournament-1")
        advanceUntilIdle()

        viewModel.deleteTournament()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.navigation)
        assertEquals(TournamentDeletionUiError.AUTHENTICATION_REQUIRED, viewModel.uiState.value.deletionError)
    }

    @Test
    fun remoteDeletedLocalCleanupFailureDoesNotNavigateAndRetryCanComplete() = runTest {
        val local = TournamentDeletionRecordingLocal().apply {
            results += LocalDeletionResult.FileCleanupFailed
            results += LocalDeletionResult.Deleted
        }
        val viewModel = viewModel(local = local)
        viewModel.load("tournament-1")
        advanceUntilIdle()

        viewModel.deleteTournament()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.navigation)
        assertEquals(TournamentDeletionUiError.LOCAL_CLEANUP_FAILURE, viewModel.uiState.value.deletionError)

        viewModel.deleteTournament()
        advanceUntilIdle()

        assertEquals(TournamentDetailsNavigation.TOURNAMENT_LIST, viewModel.uiState.value.navigation)
        assertEquals(2, local.calls)
    }

    @Test
    fun targetNotFoundStaysOnDetails() = runTest {
        val viewModel = viewModel()
        viewModel.load("tournament-1")
        advanceUntilIdle()
        repository.hasTournament = false

        viewModel.deleteTournament()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.navigation)
        assertEquals(TournamentDeletionUiError.TARGET_NOT_FOUND, viewModel.uiState.value.deletionError)
    }

    private fun viewModel(
        cloud: TournamentDeletionRecordingCloud = TournamentDeletionRecordingCloud(),
        local: TournamentDeletionRecordingLocal = TournamentDeletionRecordingLocal(),
        authState: AuthState = AuthState.SignedIn(AuthUser("owner-1", "owner@example.com")),
    ) = TournamentDetailsViewModel(
        getTournamentById = GetTournamentByIdUseCase(repository),
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
        observeMatches = ObserveMatchesUseCase(repository),
        observeRoster = ObserveRosterByTournamentUseCase(repository),
        googleSheetsStandingsExport = NoOpGoogleSheetsStandingsExportRemoteDataSource,
        saveTeamSlotNames = SaveTeamSlotNamesUseCase(repository),
        validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
        createNextMatch = CreateNextMatchUseCase(repository),
        syncDraftMatches = DraftMatchCloudSyncAction {
            QueueAwareActionResult(
                primaryResult = DraftMatchCloudSyncResult.Success,
                queueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
            )
        },
        deleteTournamentUseCase = DeleteTournamentUseCase(
            tournamentRepository = repository,
            authRepository = TournamentDeletionTestAuthRepository(authState),
            queueRepository = TournamentDeletionTestQueueRepository(),
            cloudDeletionRepository = cloud,
            localDeletionRepository = local,
            deletionIntentRepository = TournamentDeletionTestIntentRepository(),
        ),
    )
}

private object NoOpGoogleSheetsStandingsExportRemoteDataSource : GoogleSheetsStandingsExportRemoteDataSource {
    override suspend fun export(
        tournamentId: String,
        rows: List<com.hoggamers.rankforge.domain.export.TournamentStandingsExportRow>,
    ): GoogleSheetsStandingsExportExecutionResult =
        GoogleSheetsStandingsExportExecutionResult.Failure(
            com.hoggamers.rankforge.data.export.AndroidGoogleSheetsExportFailureReason.SERVER_FAILURE,
        )
}

private class DeletionTournamentRepository : TournamentRepository {
    var hasTournament = true
    private val tournament = Tournament(
        id = "tournament-1",
        name = "Summer Cup",
        date = LocalDate.of(2026, 8, 21),
        organizerName = "Organizer",
        organizerContactNumber = "000",
        status = TournamentStatus.CONFIRMED,
        ownerUserId = "owner-1",
    )

    override suspend fun create(tournament: Tournament) = Unit
    override fun observeAll(): Flow<List<Tournament>> = flowOf(if (hasTournament) listOf(tournament) else emptyList())
    override fun observeById(tournamentId: String): Flow<Tournament?> = flowOf(tournament.takeIf { hasTournament && it.id == tournamentId })
    override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> = flowOf(TeamSlot.fixedSlotsForTournament(tournamentId))
    override suspend fun saveTeamNames(tournamentId: String, teamNamesBySlotNumber: Map<Int, String>) = Unit
    override fun observeRosterByTournamentAndSlot(tournamentId: String, slotNumber: Int): Flow<List<RosterPlayer>> = flowOf(emptyList())
    override suspend fun saveRoster(tournamentId: String, slotNumber: Int, players: List<RosterPlayer>) = Unit
    override suspend fun confirmTournament(tournamentId: String): Boolean = false
}

private class TournamentDeletionTestAuthRepository(private val state: AuthState) : AuthRepository {
    override fun observeAuthState(): Flow<AuthState> = flowOf(state)
    override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
    override suspend fun signUp(email: String, password: String): AuthOperationResult = failure()
    override suspend fun login(email: String, password: String): AuthOperationResult = failure()
    override suspend fun logout(): AuthOperationResult = failure()
    private fun failure() = AuthOperationResult.Failure(AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure))
}

private class TournamentDeletionTestQueueRepository : PersistentSyncQueueRepository {
    override fun observeAll(): Flow<List<SyncQueueEntry>> = flowOf(emptyList())
    override suspend fun enqueue(operationType: SyncQueueOperationType, tournamentId: String?, status: SyncQueueStatus, failureCategory: String?) =
        SyncQueueEntry("queue-1", operationType, tournamentId, 0L, status, failureCategory, 0)
    override suspend fun completeOldestUnresolved(operationType: SyncQueueOperationType, tournamentId: String?) = Unit
    override suspend fun incrementAttemptCount(id: String) = Unit
    override suspend fun updateRetryFailure(id: String, status: SyncQueueStatus, failureCategory: String?) = Unit
    override suspend fun markCompleted(id: String) = Unit
    override suspend fun remove(id: String) = Unit
    override suspend fun purgeByTournamentId(tournamentId: String) = Unit
    override suspend fun purgeByTournamentIdAndOwner(tournamentId: String, ownerUserId: String) = Unit
}

private class TournamentDeletionRecordingCloud : CloudDeletionRepository {
    var storageResult: CloudDeletionStageResult = CloudDeletionStageResult.Success
    var remoteResult: CloudDeletionStageResult = CloudDeletionStageResult.Success
    var remoteGate: CompletableDeferred<Unit>? = null
    var remoteCalls = 0
    val remoteTournamentIds = mutableListOf<String>()

    override suspend fun deleteMatchStorage(tournamentId: String, matchId: String): CloudDeletionStageResult = CloudDeletionStageResult.Success
    override suspend fun deleteMatchRemote(tournamentId: String, matchId: String): CloudDeletionStageResult = CloudDeletionStageResult.Success
    override suspend fun deleteTournamentStorage(tournamentId: String, matchIds: Set<String>): CloudDeletionStageResult = storageResult
    override suspend fun deleteTournamentRemote(tournamentId: String): CloudDeletionStageResult {
        remoteCalls++
        remoteTournamentIds += tournamentId
        remoteGate?.await()
        return remoteResult
    }
}

private class TournamentDeletionRecordingLocal : LocalDeletionRepository {
    val results = mutableListOf<LocalDeletionResult>()
    var calls = 0
    override suspend fun deleteMatchLocally(matchId: String): LocalDeletionResult = LocalDeletionResult.Deleted
    override suspend fun deleteTournamentLocallyByOwner(
        tournamentId: String,
        ownerUserId: String,
    ): LocalDeletionResult {
        calls++
        return results.removeFirstOrNull() ?: LocalDeletionResult.Deleted
    }

    override suspend fun deleteTournamentLocally(tournamentId: String): LocalDeletionResult {
        calls++
        return results.removeFirstOrNull() ?: LocalDeletionResult.Deleted
    }
}

private class TournamentDeletionTestIntentRepository : DeletionIntentRepository {
    private val intents = mutableMapOf<Pair<DeletionTargetType, String>, DeletionIntent>()

    override suspend fun read(
        targetType: DeletionTargetType,
        targetId: String,
    ): DeletionIntent? = intents[targetType to targetId]

    override suspend fun findByTargetAndOwner(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): DeletionIntent? =
        intents[targetType to targetId]?.takeIf { it.ownerUserId == ownerUserId }

    override suspend fun start(intent: DeletionIntent): DeletionIntent {
        intents[intent.targetType to intent.targetId] = intent
        return intent
    }

    override suspend fun startIfAbsent(intent: DeletionIntent): Boolean {
        val key = intent.targetType to intent.targetId
        if (key in intents) return false
        intents[key] = intent
        return true
    }

    override suspend fun markRemoteDeleted(
        targetType: DeletionTargetType,
        targetId: String,
    ) {
        val key = targetType to targetId
        intents[key] = requireNotNull(intents[key]).copy(
            phase = DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING,
        )
    }

    override suspend fun markRemoteDeletedByTargetAndOwner(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): Boolean {
        val key = targetType to targetId
        val current = intents[key]?.takeIf { it.ownerUserId == ownerUserId } ?: return false
        intents[key] = current.copy(
            phase = DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING,
        )
        return true
    }

    override suspend fun clear(
        targetType: DeletionTargetType,
        targetId: String,
    ) {
        intents.remove(targetType to targetId)
    }

    override suspend fun clearByTargetAndOwner(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): Boolean {
        val key = targetType to targetId
        val current = intents[key]?.takeIf { it.ownerUserId == ownerUserId } ?: return false
        if (intents[key] != current) return false
        intents.remove(key)
        return true
    }

    override suspend fun isBlocking(
        tournamentId: String,
    ): Boolean = intents.values.any { it.tournamentId == tournamentId }

    override suspend fun isBlockingByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Boolean = intents.values.any {
        it.tournamentId == tournamentId && it.ownerUserId == ownerUserId
    }

    override suspend fun readAll(): List<DeletionIntent> = intents.values.toList()

    override suspend fun readPendingLocalCleanup(): List<DeletionIntent> =
        intents.values.filter {
            it.phase == DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING
        }

    override suspend fun readPendingLocalCleanupByOwner(
        ownerUserId: String,
    ): List<DeletionIntent> = intents.values.filter {
        it.ownerUserId == ownerUserId &&
            it.phase == DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING
    }

    override suspend fun hasLocalCleanupClaim(
        targetType: DeletionTargetType,
        targetId: String,
        ownerUserId: String,
    ): Boolean =
        findByTargetAndOwner(targetType, targetId, ownerUserId)?.phase ==
            DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING
}
