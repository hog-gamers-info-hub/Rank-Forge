package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.LocalRevisionState
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.sync.SyncQueueEntry
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ResolveDraftConflictUseCaseTest {
    @Test
    fun acceptCloudAtoBAbortsBeforeDraftReplacementOrQueueCompletion() = runTest {
        val auth = MutableAuth(AuthState.SignedIn(AuthUser(OWNER_A, null)))
        val cloud = SuspendingCloud(cloudSnapshot())
        val local = RecordingLocal()
        val queue = RecordingQueue()
        val action = useCase(auth, cloud, local, queue)

        val job = launch { assertEquals(DraftConflictResolutionResult.Failed, action.acceptCloudDraft(context())) }
        cloud.started.await()
        auth.state.value = AuthState.SignedIn(AuthUser(OWNER_B, null))
        cloud.resume.complete(Unit)
        job.join()

        assertEquals(0, local.ownerBoundReplacementCount)
        assertEquals(0, local.revisionWriteCount)
        assertEquals(0, queue.completedCount)
    }

    @Test
    fun acceptCloudSignedOutAbortsBeforeDraftReplacementOrQueueCompletion() = runTest {
        val auth = MutableAuth(AuthState.SignedIn(AuthUser(OWNER_A, null)))
        val cloud = SuspendingCloud(cloudSnapshot())
        val local = RecordingLocal()
        val queue = RecordingQueue()
        val action = useCase(auth, cloud, local, queue)

        val job = launch { assertEquals(DraftConflictResolutionResult.Failed, action.acceptCloudDraft(context())) }
        cloud.started.await()
        auth.state.value = AuthState.SignedOut
        cloud.resume.complete(Unit)
        job.join()

        assertEquals(0, local.ownerBoundReplacementCount)
        assertEquals(0, local.revisionWriteCount)
        assertEquals(0, queue.completedCount)
    }

    @Test
    fun acceptCloudRejectsWrongTargetBeforeAnyWrite() = runTest {
        val wrong = cloudSnapshot().copy(tournamentId = OTHER_TOURNAMENT_ID)
        val local = RecordingLocal()
        val queue = RecordingQueue()
        val result = useCase(
            MutableAuth(AuthState.SignedIn(AuthUser(OWNER_A, null))),
            ImmediateCloud(wrong),
            local,
            queue,
        ).acceptCloudDraft(context())

        assertEquals(DraftConflictResolutionResult.Unsupported, result)
        assertEquals(0, local.ownerBoundReplacementCount)
        assertEquals(0, local.revisionWriteCount)
        assertEquals(0, queue.completedCount)
    }

    @Test
    fun keepLocalAtoBAbortsBeforeOwnerBoundRebase() = runTest {
        val auth = MutableAuth(AuthState.SignedIn(AuthUser(OWNER_A, null)))
        val repository = SuspendingRebaseRepository(suspendRead = true)
        val queue = RecordingQueue()
        val action = useCase(auth, ImmediateCloud(cloudSnapshot()), RecordingLocal(), queue, repository)

        val job = launch { assertEquals(DraftConflictResolutionResult.Failed, action.keepLocal(context())) }
        repository.readStarted.await()
        auth.state.value = AuthState.SignedIn(AuthUser(OWNER_B, null))
        repository.readResume.complete(Unit)
        job.join()

        assertEquals(0, repository.rebaseWrites)
        assertEquals(0, queue.completedCount)
    }

    @Test
    fun keepLocalSameOwnerRefreshMayContinue() = runTest {
        val auth = MutableAuth(AuthState.SignedIn(AuthUser(OWNER_A, "old@example.test")))
        val repository = SuspendingRebaseRepository(suspendRead = true)
        val action = useCase(auth, ImmediateCloud(cloudSnapshot()), RecordingLocal(), RecordingQueue(), repository)

        val job = launch { assertEquals(DraftConflictResolutionResult.KeepLocalSucceeded, action.keepLocal(context())) }
        repository.readStarted.await()
        auth.state.value = AuthState.SignedIn(AuthUser(OWNER_A, "refreshed@example.test"))
        repository.readResume.complete(Unit)
        repository.started.await()
        repository.resume.complete(Unit)
        job.join()

        assertEquals(1, repository.rebaseWrites)
    }

    private fun context() = ConflictResolutionContext(
        tournamentId = TOURNAMENT_ID,
        matchId = MATCH_ID,
        operation = ConflictOperation.DRAFT_MATCH_SYNC,
        conflict = RevisionConflict.StaleWrite(CloudRevision(1), CloudRevision(2)),
        resolvability = ConflictResolvability.DRAFT_RESOLVABLE,
        localDraftMatches = listOf(localMatch()),
        currentCloudRevision = CloudRevision(2),
    )

    private fun cloudSnapshot(tournamentId: String = TOURNAMENT_ID) = MatchCloudRestorationSnapshot(
        tournamentId = tournamentId,
        matches = listOf(localMatch().copy(tournamentId = tournamentId)),
        cloudRevision = CloudRevision(3),
    )

    private fun localMatch() = Match(
        id = MATCH_ID,
        tournamentId = TOURNAMENT_ID,
        matchNumber = 1,
        date = LocalDate.of(2026, 8, 1),
        mapName = "Bermuda",
        status = MatchStatus.DRAFT,
    )

    private fun useCase(
        auth: MutableAuth,
        cloud: MatchCloudRestorationRepository,
        local: RecordingLocal,
        queue: RecordingQueue,
        repository: TournamentRepository = localTournamentRepository(),
    ) = ResolveDraftConflictUseCase(
        tournamentRepository = repository,
        authRepository = auth,
        cloudRepository = cloud,
        localRepository = local,
        syncDraftMatches = DraftAction(),
        queueRepository = queue,
    )

    private fun localTournamentRepository(): TournamentRepository = InMemoryTournamentRepository().also { repository ->
        kotlinx.coroutines.runBlocking {
            repository.create(
                Tournament(
                    TOURNAMENT_ID,
                    "Conflict Cup",
                    LocalDate.of(2026, 8, 1),
                    "Organizer",
                    "123",
                    TournamentStatus.DRAFT,
                    ownerUserId = OWNER_A,
                ),
            )
            repository.createDraftMatch(localMatch())
        }
    }

    private class RecordingLocal : MatchRestorationLocalRepository {
        var ownerBoundReplacementCount = 0
        var revisionWriteCount = 0
        override suspend fun replaceMatches(snapshot: MatchCloudRestorationSnapshot) = Unit
        override suspend fun replaceDraftMatches(snapshot: MatchCloudRestorationSnapshot) = Unit
        override suspend fun replaceMatchesByOwner(tournamentId: String, expectedOwnerUserId: String, snapshot: MatchCloudRestorationSnapshot) = Unit
        override suspend fun replaceDraftMatchesByOwner(tournamentId: String, expectedOwnerUserId: String, snapshot: MatchCloudRestorationSnapshot) {
            ownerBoundReplacementCount += 1
            revisionWriteCount += 1
        }
    }

    private class DraftAction : DraftMatchCloudSyncAction {
        override suspend fun invoke(tournamentId: String): QueueAwareActionResult<DraftMatchCloudSyncResult> =
            QueueAwareActionResult(DraftMatchCloudSyncResult.Success, QueueRecordingResult.NOT_REQUIRED)
        override suspend fun invoke(tournamentId: String, expectedOwnerUserId: String): QueueAwareActionResult<DraftMatchCloudSyncResult> =
            QueueAwareActionResult(DraftMatchCloudSyncResult.Success, QueueRecordingResult.NOT_REQUIRED)
    }

    private class ImmediateCloud(private val result: MatchCloudRestorationSnapshot) : MatchCloudRestorationRepository {
        override suspend fun readOwnedMatches(tournamentId: String) = MatchCloudRestorationRemoteResult.Success(result)
    }

    private class SuspendingCloud(private val result: MatchCloudRestorationSnapshot) : MatchCloudRestorationRepository {
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        override suspend fun readOwnedMatches(tournamentId: String) = run {
            started.complete(Unit)
            resume.await()
            MatchCloudRestorationRemoteResult.Success(result)
        }
    }

    private class SuspendingRebaseRepository(
        private val suspendRead: Boolean,
    ) : TournamentRepository by InMemoryTournamentRepository() {
        val readStarted = CompletableDeferred<Unit>()
        val readResume = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        var rebaseWrites = 0
        override fun observeMatchesByTournamentIdAndOwner(tournamentId: String, ownerUserId: String) =
            if (!suspendRead) flowOf(listOf(localMatchForRead())) else kotlinx.coroutines.flow.flow {
                readStarted.complete(Unit)
                readResume.await()
                emit(listOf(localMatchForRead()))
            }
        override suspend fun rebaseCloudRevisionForConflictResolutionByOwner(tournamentId: String, ownerUserId: String, cloudRevision: Int): OwnerScopedTournamentMutationResult {
            started.complete(Unit)
            resume.await()
            rebaseWrites += 1
            return OwnerScopedTournamentMutationResult.Saved
        }

        private fun localMatchForRead() = Match(
            id = MATCH_ID,
            tournamentId = TOURNAMENT_ID,
            matchNumber = 1,
            date = LocalDate.of(2026, 8, 1),
            mapName = "Bermuda",
            status = MatchStatus.DRAFT,
        )
    }

    private class RecordingQueue : PersistentSyncQueueRepository {
        var completedCount = 0
        override fun observeAll(): Flow<List<SyncQueueEntry>> = flowOf(emptyList())
        override suspend fun completeOldestUnresolvedByOwner(ownerUserId: String, operationType: SyncQueueOperationType, tournamentId: String?) { completedCount += 1 }
    }

    private class MutableAuth(initial: AuthState) : AuthRepository {
        val state = MutableStateFlow(initial)
        override fun observeAuthState(): Flow<AuthState> = state
        override suspend fun restoreSession() = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String) = AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated)
        override suspend fun login(email: String, password: String) = AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)
        override suspend fun logout() = AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        const val OTHER_TOURNAMENT_ID = "55555555-5555-5555-5555-555555555555"
        const val MATCH_ID = "33333333-3333-3333-3333-333333333333"
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
    }
}
