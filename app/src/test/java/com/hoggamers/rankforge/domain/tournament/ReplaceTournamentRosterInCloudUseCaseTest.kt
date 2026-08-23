package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.LocalRevisionState
import com.hoggamers.rankforge.domain.sync.RecordSyncQueueOutcome
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.sync.SyncQueueEntry
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaceTournamentRosterInCloudUseCaseTest {
    @Test
    fun missingBaselineBootstrapsAbsentCloudTournamentWithAuthoritativeRevision() = runTest {
        val repository = MissingBaselineRepository(localRepository())
        val upload = RecordingUploadRepository(TournamentCloudUploadResult.Success(9))
        val restoration = RecordingRestorationRepository(
            TournamentCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.NOT_FOUND,
            ),
        )
        val cloud = FakeCloud(TournamentRosterCloudReplacementResult.Success(8))

        val result = useCase(repository, cloud, upload = upload, restoration = restoration)
            .executeForRetry(TOURNAMENT_ID)

        assertEquals(TournamentRosterCloudReplacementResult.Success(9), result)
        assertEquals(0, upload.snapshot?.expectedCloudRevision)
        assertEquals(12, upload.snapshot?.slots?.size)
        assertTrue(upload.snapshot?.rosters?.get(1)?.single()?.displayName == "Player One")
        assertEquals(9, repository.readLocalRevisionState(TOURNAMENT_ID).expectedCloudRevision)
        assertTrue(cloud.snapshots.isEmpty())
    }

    @Test
    fun bootstrapRetryDoesNotRepeatCreateAfterAuthoritativeRevisionWasPersisted() = runTest {
        val repository = MissingBaselineRepository(localRepository())
        val upload = RecordingUploadRepository(TournamentCloudUploadResult.Success(9))
        val cloud = FakeCloud(TournamentRosterCloudReplacementResult.Success(10))
        val action = useCase(
            repository,
            cloud,
            upload = upload,
            restoration = RecordingRestorationRepository(
                TournamentCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.NOT_FOUND,
                ),
            ),
        )

        assertEquals(TournamentRosterCloudReplacementResult.Success(9), action.executeForRetry(TOURNAMENT_ID))
        assertEquals(TournamentRosterCloudReplacementResult.Success(10), action.executeForRetry(TOURNAMENT_ID))
        assertEquals(1, upload.calls)
        assertEquals(1, cloud.snapshots.size)
        assertEquals(9, cloud.snapshots.single().expectedCloudRevision)
    }

    @Test
    fun existingCloudTournamentEstablishesBaselineBeforePositiveRevisionReplacement() = runTest {
        val repository = MissingBaselineRepository(localRepository())
        val restoration = RecordingRestorationRepository(
            TournamentCloudRestorationRemoteResult.Success(
                TournamentCloudRestorationSnapshot(
                    tournament = repository.observeById(TOURNAMENT_ID).first()!!,
                    slots = TeamSlot.fixedSlotsForTournament(TOURNAMENT_ID),
                    players = emptyList(),
                    cloudRevision = CloudRevision(4),
                ),
            ),
        )
        val cloud = FakeCloud(TournamentRosterCloudReplacementResult.Success(5))

        val result = useCase(
            repository,
            cloud,
            restoration = restoration,
        ).executeForRetry(TOURNAMENT_ID)

        assertEquals(TournamentRosterCloudReplacementResult.Success(5), result)
        assertEquals(listOf(4), repository.establishedBaselines)
        assertEquals(4, cloud.snapshot?.expectedCloudRevision)
        assertEquals(5, repository.readLocalRevisionState(TOURNAMENT_ID).expectedCloudRevision)
    }

    @Test
    fun bootstrapNetworkFailurePreservesMissingBaselineAndReturnsRetryableNetworkFailure() = runTest {
        val repository = MissingBaselineRepository(localRepository())
        val queue = RecordingQueueRepository()
        val result = useCase(
            repository,
            FakeCloud(TournamentRosterCloudReplacementResult.Success(9)),
            queue,
            upload = RecordingUploadRepository(TournamentCloudUploadResult.NetworkFailure),
        ) .invoke(TOURNAMENT_ID)

        assertEquals(TournamentRosterCloudReplacementResult.NetworkFailure, result.primaryResult)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, queue.entries.single().status)
        assertEquals(null, repository.readLocalRevisionState(TOURNAMENT_ID).baseCloudRevision)
        assertEquals(5, repository.readLocalRevisionState(TOURNAMENT_ID).localRevision)
    }

    @Test
    fun authenticatedReplacementSendsCompleteLocalStateAndConfirmsReturnedRevision() = runTest {
        val repository = localRepository()
        val cloud = FakeCloud(TournamentRosterCloudReplacementResult.Success(7))
        val result = useCase(repository, cloud).executeForRetry(TOURNAMENT_ID)

        assertEquals(TournamentRosterCloudReplacementResult.Success(7), result)
        assertEquals(OWNER_ID, cloud.ownerId)
        assertEquals(12, cloud.snapshot?.slots?.size)
        assertEquals(1, cloud.snapshot?.expectedCloudRevision)
        assertEquals(7, repository.readLocalRevisionState(TOURNAMENT_ID).expectedCloudRevision)
    }

    @Test
    fun conflictDoesNotAdvanceLocalCloudRevision() = runTest {
        val repository = localRepository()
        val result = useCase(
            repository,
            FakeCloud(
                TournamentRosterCloudReplacementResult.Conflict(
                    RevisionConflict.StaleWrite(
                        com.hoggamers.rankforge.domain.sync.CloudRevision(1),
                        com.hoggamers.rankforge.domain.sync.CloudRevision(2),
                    ),
                ),
            ),
        ).executeForRetry(TOURNAMENT_ID)

        assertTrue(result is TournamentRosterCloudReplacementResult.Conflict)
        assertEquals(1, repository.readLocalRevisionState(TOURNAMENT_ID).expectedCloudRevision)
    }

    @Test
    fun retryRereadsCurrentLocalRosterAfterLocalMutation() = runTest {
        val repository = localRepository()
        repository.confirmTournament(TOURNAMENT_ID)
        val cloud = FakeCloud(TournamentRosterCloudReplacementResult.Success(7))
        val action = useCase(repository, cloud)

        assertEquals(
            TournamentRosterCloudReplacementResult.Success(7),
            action.executeForRetry(TOURNAMENT_ID),
        )
        assertEquals(
            listOf("Player One"),
            cloud.snapshots[0].rosters.getValue(1).map { it.displayName },
        )

        repository.saveRoster(
            TOURNAMENT_ID,
            1,
            listOf(RosterPlayer(TOURNAMENT_ID, 1, "Player Two")),
        )
        repository.confirmTournament(TOURNAMENT_ID)

        assertEquals(
            TournamentRosterCloudReplacementResult.Success(7),
            action.executeForRetry(TOURNAMENT_ID),
        )
        assertEquals(
            listOf("Player Two"),
            cloud.snapshots[1].rosters.getValue(1).map { it.displayName },
        )
    }

    @Test
    fun normalInvocationRecordsRosterReplacementFailureWithoutChangingPrimaryResult() = runTest {
        val queue = RecordingQueueRepository()
        val result = useCase(
            localRepository(),
            FakeCloud(TournamentRosterCloudReplacementResult.NetworkFailure),
            queue,
        )(TOURNAMENT_ID)

        assertEquals(TournamentRosterCloudReplacementResult.NetworkFailure, result.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, result.queueRecordingResult)
        assertEquals(SyncQueueOperationType.ROSTER_REPLACEMENT, queue.entries.single().operationType)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, queue.entries.single().status)
        assertEquals(OWNER_ID, queue.entries.single().ownerUserId)
    }

    private fun useCase(
        repository: TournamentRepository,
        cloud: FakeCloud,
        queue: RecordingQueueRepository = RecordingQueueRepository(),
        upload: TournamentCloudUploadRepository = FakeUploadRepository,
        restoration: TournamentCloudRestorationRepository = FakeRestorationRepository,
    ) = ReplaceTournamentRosterInCloudUseCase(
        repository,
        FakeAuthRepository,
        cloud,
        upload,
        restoration,
        RecordSyncQueueOutcome(queue),
    )

    private object FakeUploadRepository : TournamentCloudUploadRepository {
        override suspend fun upload(
            snapshot: TournamentCloudUploadSnapshot,
            ownerId: String,
        ): TournamentCloudUploadResult = TournamentCloudUploadResult.Success(7)
    }

    private object FakeRestorationRepository : TournamentCloudRestorationRepository {
        override suspend fun listOwnedTournaments() =
            TournamentCloudRestorationRemoteResult.Success(emptyList<TournamentCloudRestorationSummary>())

        override suspend fun readOwnedTournament(tournamentId: String) =
            TournamentCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.NOT_FOUND,
            )
    }

    private class RecordingUploadRepository(
        private val result: TournamentCloudUploadResult,
    ) : TournamentCloudUploadRepository {
        var calls = 0
        var snapshot: TournamentCloudUploadSnapshot? = null

        override suspend fun upload(
            snapshot: TournamentCloudUploadSnapshot,
            ownerId: String,
        ): TournamentCloudUploadResult {
            calls += 1
            this.snapshot = snapshot
            return result
        }
    }

    private class RecordingRestorationRepository(
        private val result: TournamentCloudRestorationRemoteResult<TournamentCloudRestorationSnapshot>,
    ) : TournamentCloudRestorationRepository {
        override suspend fun listOwnedTournaments() =
            TournamentCloudRestorationRemoteResult.Success(emptyList<TournamentCloudRestorationSummary>())

        override suspend fun readOwnedTournament(tournamentId: String) = result
    }

    private class MissingBaselineRepository(
        private val delegate: InMemoryTournamentRepository,
    ) : TournamentRepository by delegate {
        var establishedBaseline: Int? = null
        val establishedBaselines = mutableListOf<Int>()

        override suspend fun readLocalRevisionState(tournamentId: String): LocalRevisionState =
            LocalRevisionState(
                localRevision = 5,
                baseCloudRevision = establishedBaseline?.let(::CloudRevision),
            )

        override suspend fun establishCloudBaseline(tournamentId: String, cloudRevision: Int) {
            require(cloudRevision > 0)
            establishedBaseline = cloudRevision
            establishedBaselines += cloudRevision
        }

        override suspend fun confirmCloudRevision(tournamentId: String, cloudRevision: Int) {
            require(cloudRevision > 0)
            establishedBaseline = cloudRevision
        }

        override suspend fun confirmCloudRevisionByOwner(
            tournamentId: String,
            ownerUserId: String,
            cloudRevision: Int,
        ): OwnerScopedTournamentMutationResult {
            require(ownerUserId == OWNER_ID)
            confirmCloudRevision(tournamentId, cloudRevision)
            return OwnerScopedTournamentMutationResult.Saved
        }
    }

    private suspend fun localRepository(): InMemoryTournamentRepository = InMemoryTournamentRepository().also { repository ->
        repository.create(
            Tournament(
                TOURNAMENT_ID,
                "Roster Cup",
                java.time.LocalDate.of(2026, 8, 3),
                "Organizer",
                "123",
                TournamentStatus.DRAFT,
                ownerUserId = OWNER_ID,
            ),
        )
        repository.saveTeamNames(
            TOURNAMENT_ID,
            TeamSlot.SLOT_NUMBERS.associateWith { slotNumber -> "Team $slotNumber" },
        )
        repository.saveRoster(
            TOURNAMENT_ID,
            1,
            listOf(RosterPlayer(TOURNAMENT_ID, 1, "Player One")),
        )
    }

    private object FakeAuthRepository : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(
            AuthState.SignedIn(AuthUser(OWNER_ID, "owner@example.test")),
        )
        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated)
        override suspend fun login(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)
        override suspend fun logout(): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
    }

    private class FakeCloud(
        private val result: TournamentRosterCloudReplacementResult,
    ) : TournamentRosterCloudReplacementRepository {
        var snapshot: TournamentRosterCloudReplacement? = null
        var ownerId: String? = null
        val snapshots = mutableListOf<TournamentRosterCloudReplacement>()

        override suspend fun replace(
            snapshot: TournamentRosterCloudReplacement,
            ownerId: String,
        ): TournamentRosterCloudReplacementResult {
            this.snapshot = snapshot
            this.ownerId = ownerId
            snapshots += snapshot
            return result
        }
    }

    private class RecordingQueueRepository : PersistentSyncQueueRepository {
        private val state = MutableStateFlow<List<SyncQueueEntry>>(emptyList())
        val entries get() = state.value

        override fun observeAll(): Flow<List<SyncQueueEntry>> = state
        override suspend fun enqueue(
            ownerUserId: String,
            operationType: SyncQueueOperationType,
            tournamentId: String?,
            status: SyncQueueStatus,
            failureCategory: String?,
        ): SyncQueueEntry {
            val entry = SyncQueueEntry(
                id = "entry",
                operationType = operationType,
                tournamentId = tournamentId,
                createdAtEpochMillis = 0,
                status = status,
                failureCategory = failureCategory,
                attemptCount = 0,
                ownerUserId = ownerUserId,
            )
            state.value = listOf(entry)
            return entry
        }
        override suspend fun completeOldestUnresolvedByOwner(ownerUserId: String, operationType: SyncQueueOperationType, tournamentId: String?) = Unit
        override suspend fun incrementAttemptCountByOwner(id: String, ownerUserId: String) = Unit
        override suspend fun updateRetryFailureByOwner(id: String, ownerUserId: String, status: SyncQueueStatus, failureCategory: String?) = Unit
        override suspend fun markCompletedByOwner(id: String, ownerUserId: String) = Unit
        override suspend fun removeByOwner(id: String, ownerUserId: String) = Unit
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        const val OWNER_ID = "22222222-2222-2222-2222-222222222222"
    }
}
