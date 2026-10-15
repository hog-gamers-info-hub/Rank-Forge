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
import com.hoggamers.rankforge.domain.sync.expectedRevisionForWrite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaceTournamentRosterInCloudUseCaseTest {
    @Test
    fun cloudResponseAfterOwnerSwitchDoesNotConfirmRevisionOrRecordQueue() = runTest {
        val auth = SwitchingAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null)))
        val cloud = SuspendingCloud()
        val repository = CountingRevisionRepository(localRepository())
        val queue = RecordingQueueRepository()
        val action = useCase(repository, cloud, queue, authRepository = auth)

        val job = launch {
            assertEquals(
                TournamentRosterCloudReplacementResult.AuthorizationFailure,
                action(TOURNAMENT_ID).primaryResult,
            )
        }
        cloud.started.await()
        auth.state.value = AuthState.SignedIn(AuthUser(OTHER_OWNER_ID, null))
        cloud.resume.complete(Unit)
        job.join()

        assertEquals(0, repository.revisionWrites)
        assertEquals(0, repository.baselineWrites)
        assertTrue(queue.entries.isEmpty())
    }

    @Test
    fun ownerSwitchAfterNormalSnapshotReadSkipsRosterReplacement() = runTest {
        val auth = SwitchingAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null)))
        val repository = localRepository()
        val cloud = FakeCloud(TournamentRosterCloudReplacementResult.Success(2))
        val snapshots = RecordingSnapshotRepository(
            listOf(standardSnapshot(expectedRevision = 1)),
            onRead = { index ->
                if (index == 0) auth.state.value = AuthState.SignedIn(AuthUser(OTHER_OWNER_ID, null))
            },
        )

        val result = useCase(
            repository = repository,
            cloud = cloud,
            authRepository = auth,
            localSnapshotRepository = snapshots,
        ).executeForRetry(TOURNAMENT_ID)

        assertEquals(TournamentRosterCloudReplacementResult.AuthorizationFailure, result)
        assertTrue(cloud.snapshots.isEmpty())
    }

    @Test
    fun missingBaselineBootstrapsAbsentCloudTournamentWithAuthoritativeRevision() = runTest {
        val repository = MissingBaselineRepository(localRepository())
        val upload = RecordingUploadRetryAction(TournamentCloudUploadResult.Success(9))
        val restoration = RecordingRestorationRepository(
            TournamentCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.NOT_FOUND,
            ),
        )
        val cloud = FakeCloud(TournamentRosterCloudReplacementResult.Success(8))

        val result = useCase(repository, cloud, upload = upload, restoration = restoration)
            .executeForRetry(TOURNAMENT_ID)

        assertEquals(TournamentRosterCloudReplacementResult.Success(9), result)
        assertEquals(1, upload.calls)
        assertEquals(9, repository.readLocalRevisionState(TOURNAMENT_ID).expectedCloudRevision)
        assertTrue(cloud.snapshots.isEmpty())
    }

    @Test
    fun bootstrapRetryDoesNotRepeatCreateAfterAuthoritativeRevisionWasPersisted() = runTest {
        val repository = MissingBaselineRepository(localRepository())
        val upload = RecordingUploadRetryAction(TournamentCloudUploadResult.Success(9))
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
    fun existingGroupRotationWithMappingsUploadsTournamentThenReplacesRosterAtNextRevision() = runTest {
        val repository = MissingBaselineRepository(localRepository())
        val snapshots = RecordingSnapshotRepository(
            listOf(
                groupRotationSnapshot(expectedRevision = 0, hasMappings = true),
                groupRotationSnapshot(expectedRevision = 4, hasMappings = true),
                groupRotationSnapshot(expectedRevision = 5, hasMappings = true),
            ),
        )
        val upload = RecordingUploadRetryAction(TournamentCloudUploadResult.Success(5))
        val cloud = FakeCloud(TournamentRosterCloudReplacementResult.Success(6))
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

        val result = useCase(
            repository = repository,
            cloud = cloud,
            upload = upload,
            restoration = restoration,
            localSnapshotRepository = snapshots,
        ).executeForRetry(TOURNAMENT_ID)

        assertEquals(TournamentRosterCloudReplacementResult.Success(6), result)
        assertEquals(listOf(4), repository.establishedBaselines)
        assertEquals(1, upload.calls)
        assertEquals(3, snapshots.calls)
        assertEquals(5, cloud.snapshot?.expectedCloudRevision)
    }

    @Test
    fun existingGroupRotationWithoutMappingsUsesV2RosterReplacementWithoutTournamentUpload() = runTest {
        val repository = MissingBaselineRepository(localRepository())
        val snapshots = RecordingSnapshotRepository(
            listOf(
                groupRotationSnapshot(expectedRevision = 0, hasMappings = false),
                groupRotationSnapshot(expectedRevision = 4, hasMappings = false),
            ),
        )
        val upload = RecordingUploadRetryAction(TournamentCloudUploadResult.Success(5))
        val cloud = FakeCloud(TournamentRosterCloudReplacementResult.Success(5))
        val result = useCase(
            repository = repository,
            cloud = cloud,
            upload = upload,
            restoration = RecordingRestorationRepository(
                TournamentCloudRestorationRemoteResult.Success(
                    TournamentCloudRestorationSnapshot(
                        tournament = repository.observeById(TOURNAMENT_ID).first()!!,
                        slots = TeamSlot.fixedSlotsForTournament(TOURNAMENT_ID),
                        players = emptyList(),
                        cloudRevision = CloudRevision(4),
                    ),
                ),
            ),
            localSnapshotRepository = snapshots,
        ).executeForRetry(TOURNAMENT_ID)

        assertEquals(TournamentRosterCloudReplacementResult.Success(5), result)
        assertEquals(0, upload.calls)
        assertEquals(2, snapshots.calls)
        assertEquals(4, cloud.snapshot?.expectedCloudRevision)
    }

    @Test
    fun ownerSwitchAfterStandardPostBaselineSnapshotSkipsRosterReplacement() = runTest {
        val auth = SwitchingAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null)))
        val repository = MissingBaselineRepository(localRepository())
        val cloud = FakeCloud(TournamentRosterCloudReplacementResult.Success(5))
        val snapshots = RecordingSnapshotRepository(
            listOf(
                standardSnapshot(expectedRevision = 0),
                standardSnapshot(expectedRevision = 4),
            ),
            onRead = { index ->
                if (index == 1) auth.state.value = AuthState.SignedIn(AuthUser(OTHER_OWNER_ID, null))
            },
        )
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

        val result = useCase(
            repository = repository,
            cloud = cloud,
            authRepository = auth,
            restoration = restoration,
            localSnapshotRepository = snapshots,
        ).executeForRetry(TOURNAMENT_ID)

        assertEquals(TournamentRosterCloudReplacementResult.AuthorizationFailure, result)
        assertTrue(cloud.snapshots.isEmpty())
        assertEquals(listOf(4), repository.establishedBaselines)
    }

    @Test
    fun ownerSwitchAfterGroupRotationUploadSkipsRosterReplacementAndKeepsIntermediateRevision() = runTest {
        val auth = SwitchingAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null)))
        val repository = MissingBaselineRepository(localRepository())
        val cloud = FakeCloud(TournamentRosterCloudReplacementResult.Success(6))
        val upload = RecordingUploadRetryAction(TournamentCloudUploadResult.Success(5))
        upload.onInvoke = {
            repository.confirmCloudRevisionByOwner(TOURNAMENT_ID, OWNER_ID, 5)
        }
        val snapshots = RecordingSnapshotRepository(
            listOf(
                groupRotationSnapshot(expectedRevision = 0, hasMappings = true),
                groupRotationSnapshot(expectedRevision = 4, hasMappings = true),
                groupRotationSnapshot(expectedRevision = 5, hasMappings = true),
            ),
            onRead = { index ->
                if (index == 2) auth.state.value = AuthState.SignedIn(AuthUser(OTHER_OWNER_ID, null))
            },
        )
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

        val result = useCase(
            repository = repository,
            cloud = cloud,
            upload = upload,
            restoration = restoration,
            authRepository = auth,
            localSnapshotRepository = snapshots,
        ).executeForRetry(TOURNAMENT_ID)

        assertEquals(TournamentRosterCloudReplacementResult.AuthorizationFailure, result)
        assertTrue(cloud.snapshots.isEmpty())
        assertEquals(5, repository.readLocalRevisionState(TOURNAMENT_ID).expectedCloudRevision)
    }

    @Test
    fun replacementUsesAtomicSnapshotInsteadOfAssemblingIndependentRosterReads() = runTest {
        val baseRepository = localRepository()
        val guardedRepository = GuardedRosterAssemblyRepository(baseRepository)
        val snapshot = TournamentCloudUploadSnapshot(
            tournament = baseRepository.observeById(TOURNAMENT_ID).first()!!,
            slots = TeamSlot.fixedSlotsForTournament(TOURNAMENT_ID),
            rosters = emptyMap(),
            expectedCloudRevision = 1,
        )

        val result = useCase(
            repository = guardedRepository,
            cloud = FakeCloud(TournamentRosterCloudReplacementResult.Success(2)),
            localSnapshotRepository = RecordingSnapshotRepository(listOf(snapshot)),
        ).executeForRetry(TOURNAMENT_ID)

        assertEquals(TournamentRosterCloudReplacementResult.Success(2), result)
    }

    @Test
    fun bootstrapNetworkFailurePreservesMissingBaselineAndReturnsRetryableNetworkFailure() = runTest {
        val repository = MissingBaselineRepository(localRepository())
        val queue = RecordingQueueRepository()
        val result = useCase(
            repository,
            FakeCloud(TournamentRosterCloudReplacementResult.Success(9)),
            queue,
            upload = RecordingUploadRetryAction(TournamentCloudUploadResult.NetworkFailure),
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
        cloud: TournamentRosterCloudReplacementRepository,
        queue: RecordingQueueRepository = RecordingQueueRepository(),
        upload: TournamentCloudUploadRetryAction = RecordingUploadRetryAction(
            TournamentCloudUploadResult.Success(7),
        ),
        restoration: TournamentCloudRestorationRepository = FakeRestorationRepository,
        authRepository: AuthRepository = FakeAuthRepository,
        localSnapshotRepository: TournamentCloudUploadLocalSnapshotRepository = snapshotRepository(repository),
    ) = ReplaceTournamentRosterInCloudUseCase(
        tournamentRepository = repository,
        localSnapshotRepository = localSnapshotRepository,
        authRepository = authRepository,
        cloudReplacementRepository = cloud,
        cloudRestorationRepository = restoration,
        tournamentUploadRetryAction = upload,
        queueRecorder = RecordSyncQueueOutcome(queue),
    )

    private class RecordingUploadRetryAction(
        private val result: TournamentCloudUploadResult,
    ) : TournamentCloudUploadRetryAction {
        var calls = 0
        var onInvoke: (suspend () -> Unit)? = null

        override suspend fun executeForRetry(tournamentId: String): TournamentCloudUploadResult {
            calls += 1
            onInvoke?.invoke()
            return result
        }

        override suspend fun executeForRetry(
            tournamentId: String,
            expectedOwnerUserId: String,
        ): TournamentCloudUploadResult {
            calls += 1
            onInvoke?.invoke()
            return result
        }
    }

    private class RecordingSnapshotRepository(
        private val snapshots: List<TournamentCloudUploadSnapshot>,
        private val onRead: (Int) -> Unit = {},
    ) : TournamentCloudUploadLocalSnapshotRepository {
        var calls = 0

        override suspend fun readCloudUploadSnapshotByOwner(
            tournamentId: String,
            ownerUserId: String,
        ): TournamentCloudUploadSnapshot? {
            val index = calls
            val snapshot = snapshots.getOrNull(index) ?: snapshots.lastOrNull()
            calls += 1
            onRead(index)
            return snapshot
        }
    }

    private class GuardedRosterAssemblyRepository(
        private val delegate: TournamentRepository,
    ) : TournamentRepository by delegate {
        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> =
            error("Roster replacement must use the atomic cloud-upload snapshot.")

        override fun observeRosterByTournamentId(
            tournamentId: String,
        ): Flow<Map<Int, List<RosterPlayer>>> =
            error("Roster replacement must use the atomic cloud-upload snapshot.")

        override suspend fun readLocalRevisionState(tournamentId: String): LocalRevisionState =
            error("Roster replacement must use the atomic cloud-upload snapshot.")
    }

    private fun groupRotationSnapshot(
        expectedRevision: Int,
        hasMappings: Boolean,
    ) = TournamentCloudUploadSnapshot(
        tournament = Tournament(
            id = TOURNAMENT_ID,
            name = "Rotation",
            stageName = "Stage",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
            ownerUserId = OWNER_ID,
            format = TournamentFormat.GROUP_ROTATION,
            groupCount = 3,
            selectedGroupPairings = defaultGroupPairings(3),
        ),
        slots = Tournament(
            id = TOURNAMENT_ID,
            name = "Rotation",
            stageName = "Stage",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
            ownerUserId = OWNER_ID,
            format = TournamentFormat.GROUP_ROTATION,
            groupCount = 3,
            selectedGroupPairings = defaultGroupPairings(3),
        ).formatDerivedSlots(),
        rosters = emptyMap(),
        pairingLobbySlots = if (hasMappings) {
            listOf(GroupRotationPairingLobbySlot(TOURNAMENT_ID, GroupPairing.fromCanonicalKey("A:B"), 1, 1))
        } else {
            emptyList()
        },
        expectedCloudRevision = expectedRevision,
    )

    private fun standardSnapshot(expectedRevision: Int) = TournamentCloudUploadSnapshot(
        tournament = Tournament(
            id = TOURNAMENT_ID,
            name = "Roster Cup",
            stageName = "Stage",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
            ownerUserId = OWNER_ID,
        ),
        slots = TeamSlot.fixedSlotsForTournament(TOURNAMENT_ID),
        rosters = emptyMap(),
        expectedCloudRevision = expectedRevision,
    )

    private fun snapshotRepository(
        repository: TournamentRepository,
    ): TournamentCloudUploadLocalSnapshotRepository = object : TournamentCloudUploadLocalSnapshotRepository {
        override suspend fun readCloudUploadSnapshotByOwner(
            tournamentId: String,
            ownerUserId: String,
        ): TournamentCloudUploadSnapshot? {
            val tournament = repository.observeByIdAndOwner(tournamentId, ownerUserId).first() ?: return null
            return TournamentCloudUploadSnapshot(
                tournament = tournament,
                slots = repository.observeSlotsByTournamentIdAndOwner(tournamentId, ownerUserId).first(),
                rosters = repository.observeRosterByTournamentIdAndOwner(tournamentId, ownerUserId).first(),
                expectedCloudRevision = repository.readLocalRevisionState(tournamentId).expectedRevisionForWrite(),
            )
        }
    }

    private object FakeRestorationRepository : TournamentCloudRestorationRepository {
        override suspend fun listOwnedTournaments() =
            TournamentCloudRestorationRemoteResult.Success(emptyList<TournamentCloudRestorationSummary>())

        override suspend fun readOwnedTournament(tournamentId: String) =
            TournamentCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.NOT_FOUND,
            )
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

        override suspend fun establishCloudBaselineByOwner(
            tournamentId: String,
            ownerUserId: String,
            cloudRevision: Int,
        ): OwnerScopedTournamentMutationResult {
            if (ownerUserId != OWNER_ID) return OwnerScopedTournamentMutationResult.TournamentNotFound
            establishCloudBaseline(tournamentId, cloudRevision)
            return OwnerScopedTournamentMutationResult.Saved
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

    private class CountingRevisionRepository(
        private val delegate: InMemoryTournamentRepository,
    ) : TournamentRepository by delegate {
        var revisionWrites = 0
        var baselineWrites = 0

        override suspend fun confirmCloudRevisionByOwner(
            tournamentId: String,
            ownerUserId: String,
            cloudRevision: Int,
        ): OwnerScopedTournamentMutationResult {
            revisionWrites += 1
            return delegate.confirmCloudRevisionByOwner(tournamentId, ownerUserId, cloudRevision)
        }

        override suspend fun establishCloudBaselineByOwner(
            tournamentId: String,
            ownerUserId: String,
            cloudRevision: Int,
        ): OwnerScopedTournamentMutationResult {
            baselineWrites += 1
            return delegate.establishCloudBaselineByOwner(tournamentId, ownerUserId, cloudRevision)
        }
    }

    private suspend fun localRepository(): InMemoryTournamentRepository = InMemoryTournamentRepository().also { repository ->
        repository.create(
            Tournament(
                TOURNAMENT_ID,
                "Roster Cup",
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

    private class SuspendingCloud : TournamentRosterCloudReplacementRepository {
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()

        override suspend fun replace(
            snapshot: TournamentRosterCloudReplacement,
            ownerId: String,
        ): TournamentRosterCloudReplacementResult {
            started.complete(Unit)
            resume.await()
            return TournamentRosterCloudReplacementResult.Success(8)
        }
    }

    private class SwitchingAuthRepository(initial: AuthState) : AuthRepository {
        val state = MutableStateFlow(initial)

        override fun observeAuthState(): Flow<AuthState> = state
        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated)
        override suspend fun login(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)
        override suspend fun logout(): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
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
        const val OTHER_OWNER_ID = "33333333-3333-3333-3333-333333333333"
    }
}
