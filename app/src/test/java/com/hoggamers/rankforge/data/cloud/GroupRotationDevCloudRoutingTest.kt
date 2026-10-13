package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.sync.RecordSyncQueueOutcome
import com.hoggamers.rankforge.domain.tournament.GroupRotationUuidCloudRestorationLocalRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationUuidCloudRestorationResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationUuidLocalRestoreResult
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationAction
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationResult
import com.hoggamers.rankforge.domain.tournament.RestoreGroupRotationUuidCloudUseCase
import com.hoggamers.rankforge.domain.tournament.RestoreTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRemoteResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationResult
import com.hoggamers.rankforge.domain.tournament.TournamentRestorationLocalRepository
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.toCloudValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class GroupRotationDevCloudRoutingTest {
    @Test
    fun standardRestorationUsesLegacyRouteThroughDevAction() = runTest {
        val fixture = fixture(
            uuidResult = GroupRotationUuidCloudRestorationRemoteResult.Success(standardUuidPayloads()),
        )

        val result = fixture.route.restoreAction(STANDARD_TOURNAMENT_ID).primaryResult

        assertEquals(TournamentCloudRestorationResult.Success("Standard"), result)
        assertEquals(1, fixture.legacyCloud.readCount)
        assertEquals(0, fixture.uuidCloud.readCount)
        assertEquals(0, fixture.uuidLocal.restoreCount)
    }

    @Test
    fun standardRestorationRetryUsesLegacyRouteAndNeverRestoresUuidData() = runTest {
        val fixture = fixture(
            uuidResult = GroupRotationUuidCloudRestorationRemoteResult.Success(standardUuidPayloads()),
        )

        val result = fixture.route.restorationRetryForOwner(STANDARD_TOURNAMENT_ID, OWNER_ID)

        assertEquals(TournamentCloudRestorationResult.Success("Standard"), result)
        assertEquals(1, fixture.legacyCloud.readCount)
        assertEquals(0, fixture.uuidLocal.restoreCount)
    }

    @Test
    fun legacyGroupRotationRestorationAndRetryUseLegacyRoute() = runTest {
        val fixture = fixture(
            uuidResult = GroupRotationUuidCloudRestorationRemoteResult.Success(legacyUuidPayloads()),
            legacySnapshot = legacySnapshot(),
        )

        assertEquals(
            TournamentCloudRestorationResult.Success("Legacy Rotation"),
            fixture.route.restoreAction(STANDARD_TOURNAMENT_ID).primaryResult,
        )
        assertEquals(
            TournamentCloudRestorationResult.Success("Legacy Rotation"),
            fixture.route.restorationRetryForOwner(STANDARD_TOURNAMENT_ID, OWNER_ID),
        )
        assertEquals(2, fixture.legacyCloud.readCount)
        assertEquals(0, fixture.uuidLocal.restoreCount)
    }

    @Test
    fun uuidNativeGroupRotationRestorationAndRetryUseV4Route() = runTest {
        val fixture = fixture(
            uuidResult = GroupRotationUuidCloudRestorationRemoteResult.Success(uuidBackedPayloads()),
            legacySnapshot = legacySnapshot(),
        )

        assertEquals(
            TournamentCloudRestorationResult.Success("Legacy Rotation"),
            fixture.route.restoreAction(STANDARD_TOURNAMENT_ID).primaryResult,
        )
        assertEquals(
            TournamentCloudRestorationResult.Success("Legacy Rotation"),
            fixture.route.restorationRetryForOwner(STANDARD_TOURNAMENT_ID, OWNER_ID),
        )
        assertEquals(0, fixture.legacyCloud.readCount)
        assertEquals(2, fixture.uuidLocal.restoreCount)
    }

    @Test
    fun uuidRestorationWorksWhenTournamentIsAbsentLocally() = runTest {
        val fixture = fixture(
            uuidResult = GroupRotationUuidCloudRestorationRemoteResult.Success(uuidBackedPayloads()),
            legacySnapshot = legacySnapshot(),
        )

        val result = fixture.route.restoreAction(STANDARD_TOURNAMENT_ID).primaryResult

        assertEquals(TournamentCloudRestorationResult.Success("Legacy Rotation"), result)
        assertEquals(1, fixture.uuidLocal.restoreCount)
        assertEquals(0, fixture.legacyCloud.readCount)
    }

    @Test
    fun cloudClassificationFailuresNeverFallBackToLegacyRestoration() = runTest {
        listOf(
            TournamentCloudRestorationFailureCategory.AUTHORIZATION to
                TournamentCloudRestorationResult.AuthorizationFailure,
            TournamentCloudRestorationFailureCategory.NOT_FOUND to
                TournamentCloudRestorationResult.AuthorizationFailure,
            TournamentCloudRestorationFailureCategory.NETWORK to
                TournamentCloudRestorationResult.NetworkFailure,
            TournamentCloudRestorationFailureCategory.VALIDATION to
                TournamentCloudRestorationResult.ValidationFailure,
        ).forEach { (category, expected) ->
            val fixture = fixture(
                uuidResult = GroupRotationUuidCloudRestorationRemoteResult.Failure(category),
                legacySnapshot = legacySnapshot(),
            )

            assertEquals(expected, fixture.route.restoreAction(STANDARD_TOURNAMENT_ID).primaryResult)
            assertEquals(0, fixture.legacyCloud.readCount)
            assertEquals(0, fixture.uuidLocal.restoreCount)
        }
    }

    @Test
    fun disabledDevV4RoutingPreservesLegacyRestoration() = runTest {
        val fixture = fixture(
            uuidResult = GroupRotationUuidCloudRestorationRemoteResult.Success(uuidBackedPayloads()),
            devV4Enabled = false,
        )

        assertEquals(
            TournamentCloudRestorationResult.Success("Standard"),
            fixture.route.restoreAction(STANDARD_TOURNAMENT_ID).primaryResult,
        )
        assertEquals(1, fixture.legacyCloud.readCount)
        assertEquals(0, fixture.uuidCloud.readCount)
        assertEquals(0, fixture.uuidLocal.restoreCount)
    }

    @Test
    fun retryWithDifferentOwnerFailsBeforeCloudClassification() = runTest {
        val fixture = fixture(
            uuidResult = GroupRotationUuidCloudRestorationRemoteResult.Success(uuidBackedPayloads()),
        )

        assertEquals(
            TournamentCloudRestorationResult.AuthorizationFailure,
            fixture.route.restorationRetryForOwner(STANDARD_TOURNAMENT_ID, OTHER_OWNER_ID),
        )
        assertEquals(0, fixture.uuidCloud.readCount)
        assertEquals(0, fixture.legacyCloud.readCount)
    }

    private fun fixture(
        uuidResult: GroupRotationUuidCloudRestorationRemoteResult,
        legacySnapshot: TournamentCloudRestorationSnapshot = standardSnapshot(),
        devV4Enabled: Boolean = true,
    ): Fixture {
        val auth = SignedInAuth()
        val queueRecorder = RecordSyncQueueOutcome(NoOpQueueRepository)
        val uuidCloud = FakeUuidCloud(uuidResult)
        val uuidLocal = FakeUuidLocal()
        val classificationCloud = LegacyCloud(legacySnapshot)
        val legacyCloud = LegacyCloud(legacySnapshot)
        val tournamentRepository = noOp<TournamentRepository>(TournamentRepository::class.java)
        val identityRepository = noOp<com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalRepository>(
            com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalRepository::class.java,
        )
        val uuidRestoration = RestoreGroupRotationUuidCloudUseCase(
            remoteDataSource = uuidCloud,
            localRepository = uuidLocal,
            authRepository = auth,
        )
        val restoration = RestoreTournamentUseCase(
            authRepository = auth,
            cloudRepository = legacyCloud,
            localRepository = object : TournamentRestorationLocalRepository {
                override suspend fun restore(snapshot: TournamentCloudRestorationSnapshot) = Unit

                override suspend fun restoreByOwner(
                    snapshot: TournamentCloudRestorationSnapshot,
                    expectedOwnerUserId: String,
                ) = Unit
            },
            queueRecorder = queueRecorder,
            matchCloudRestorationAction = object : MatchCloudRestorationAction {
                override suspend fun invoke(
                    tournamentId: String,
                ): QueueAwareActionResult<MatchCloudRestorationResult> =
                    QueueAwareActionResult(MatchCloudRestorationResult.Success, QueueRecordingResult.NOT_REQUIRED)

                override suspend fun invoke(
                    tournamentId: String,
                    expectedOwnerUserId: String,
                    expectedParentCloudRevision: com.hoggamers.rankforge.domain.sync.CloudRevision,
                ): QueueAwareActionResult<MatchCloudRestorationResult> =
                    QueueAwareActionResult(MatchCloudRestorationResult.Success, QueueRecordingResult.NOT_REQUIRED)
            },
        )
        val uploadDelegate = com.hoggamers.rankforge.domain.tournament.UploadTournamentUseCase(
            tournamentRepository = tournamentRepository,
            localSnapshotRepository = noOp(com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadLocalSnapshotRepository::class.java),
            authRepository = auth,
            cloudUploadRepository = noOp(com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadRepository::class.java),
            queueRecorder = queueRecorder,
        )
        val uploadCoordinator = GroupRotationV4CloudUploadCoordinator(
            authRepository = auth,
            tournamentRepository = tournamentRepository,
            cloudUploadSnapshotRepository = noOp(com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadLocalSnapshotRepository::class.java),
            tournamentCloudUploadRepository = noOp(com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadRepository::class.java),
            teamIdentityRepository = identityRepository,
            matchIdentityRepository = noOp(com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentityPersistenceRepository::class.java),
            journalRepository = noOp(com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalRepository::class.java),
            teamIdentityRemote = noOp(GroupRotationTeamIdentityCloudV4PreparedRemoteDataSource::class.java),
            matchRemote = noOp(GroupRotationMatchCloudV4PreparedRemoteDataSource::class.java),
            requestIdGenerator = { "request" },
        )
        val draftDelegate = com.hoggamers.rankforge.domain.tournament.SyncDraftMatchesUseCase(
            tournamentRepository = tournamentRepository,
            authRepository = auth,
            cloudSyncRepository = noOp(com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncRepository::class.java),
            queueRecorder = queueRecorder,
        )
        val finalizedDelegate = com.hoggamers.rankforge.domain.tournament.SyncFinalizedMatchesUseCase(
            tournamentRepository = tournamentRepository,
            authRepository = auth,
            cloudSyncRepository = noOp(com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncRepository::class.java),
            queueRecorder = queueRecorder,
        )
        val route = GroupRotationDevCloudRoute(
            authRepository = auth,
            tournamentRepository = tournamentRepository,
            teamIdentityRepository = identityRepository,
            queueRecorder = queueRecorder,
            uploadDelegate = uploadDelegate,
            uploadCoordinator = uploadCoordinator,
            restorationDelegate = restoration,
            restorationCloudRepository = classificationCloud,
            uuidRestoration = uuidRestoration,
            draftDelegate = draftDelegate,
            finalizedDelegate = finalizedDelegate,
            devV4Enabled = devV4Enabled,
        )
        return Fixture(route, uuidCloud, uuidLocal, legacyCloud, classificationCloud)
    }

    private data class Fixture(
        val route: GroupRotationDevCloudRoute,
        val uuidCloud: FakeUuidCloud,
        val uuidLocal: FakeUuidLocal,
        val legacyCloud: LegacyCloud,
        val classificationCloud: LegacyCloud,
    )

    private class SignedInAuth : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> =
            flowOf(AuthState.SignedIn(AuthUser(OWNER_ID, "owner@example.test")))

        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated)
        override suspend fun login(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)
        override suspend fun logout(): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
    }

    private class FakeUuidCloud(
        private val result: GroupRotationUuidCloudRestorationRemoteResult,
    ) : GroupRotationUuidCloudRestorationRemoteDataSource {
        var readCount = 0

        override suspend fun readOwnedGroupRotation(
            tournamentId: String,
        ): GroupRotationUuidCloudRestorationRemoteResult {
            readCount++
            return result
        }
    }

    private class FakeUuidLocal : GroupRotationUuidCloudRestorationLocalRepository {
        var restoreCount = 0

        override suspend fun restoreGroupRotationUuidSnapshot(
            snapshot: com.hoggamers.rankforge.domain.tournament.GroupRotationUuidCloudRestorationSnapshot,
            expectedOwnerUserId: String,
        ): GroupRotationUuidLocalRestoreResult {
            restoreCount++
            return GroupRotationUuidLocalRestoreResult.Restored
        }
    }

    private class LegacyCloud(
        private val snapshot: TournamentCloudRestorationSnapshot,
    ) : TournamentCloudRestorationRepository {
        var readCount = 0

        override suspend fun listOwnedTournaments(): TournamentCloudRestorationRemoteResult<
            List<com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSummary>
            > = TournamentCloudRestorationRemoteResult.Success(emptyList())

        override suspend fun readOwnedTournament(
            tournamentId: String,
        ): TournamentCloudRestorationRemoteResult<TournamentCloudRestorationSnapshot> {
            readCount++
            return TournamentCloudRestorationRemoteResult.Success(snapshot)
        }
    }

    private fun standardSnapshot(): TournamentCloudRestorationSnapshot =
        TournamentCloudRestorationSnapshot(
            tournament = com.hoggamers.rankforge.domain.tournament.Tournament(
                id = STANDARD_TOURNAMENT_ID,
                name = "Standard",
                stageName = "Stage",
                organizerContactNumber = "",
                status = TournamentStatus.DRAFT,
                ownerUserId = OWNER_ID,
                format = TournamentFormat.STANDARD,
            ),
            slots = emptyList(),
            players = emptyList(),
            cloudRevision = CloudRevision(1),
        )

    private fun legacySnapshot() = standardSnapshot().copy(
        tournament = standardSnapshot().tournament.copy(
            name = "Legacy Rotation",
            format = TournamentFormat.GROUP_ROTATION,
            groupCount = 3,
            selectedGroupPairings = listOf(GroupPairing(TournamentGroup.A, TournamentGroup.B)),
        ),
    )

    private fun standardUuidPayloads() = GroupRotationUuidCloudRestorationPayloads(
        parent = TournamentCloudRestorationPayloads(
            tournament = TournamentUploadPayload(
                id = STANDARD_TOURNAMENT_ID,
                ownerId = OWNER_ID,
                name = "Standard",
                stageName = "Stage",
                organizerContact = "",
                status = "draft",
                revision = 1,
                format = TournamentFormat.STANDARD.toCloudValue(),
            ),
            teamSlots = (1..12).map { slot ->
                TeamSlotUploadPayload(
                    id = TournamentCloudIdentity.teamSlotId(java.util.UUID.fromString(STANDARD_TOURNAMENT_ID), slot),
                    tournamentId = STANDARD_TOURNAMENT_ID,
                    slotNumber = slot,
                    teamName = "",
                    status = "active",
                )
            },
            players = emptyList(),
        ),
        matches = emptyList(),
        legacyResults = emptyList(),
        identities = emptyList(),
        assignments = emptyList(),
        uuidMatchSnapshots = emptyList(),
        uuidMatchAssignments = emptyList(),
        uuidMatchResults = emptyList(),
    )

    private fun legacyUuidPayloads() = groupRotationUuidPayloads()

    private fun uuidBackedPayloads() = groupRotationUuidPayloads(
        identities = listOf(
            GroupRotationUuidTeamIdentityPayload(
                tournamentId = STANDARD_TOURNAMENT_ID,
                teamUUID = TEAM_UUID,
                displayName = "Phoenix",
                normalizedName = "phoenix",
            ),
        ),
    )

    private fun groupRotationUuidPayloads(
        identities: List<GroupRotationUuidTeamIdentityPayload> = emptyList(),
    ) = GroupRotationUuidCloudRestorationPayloads(
        parent = TournamentCloudRestorationPayloads(
            tournament = TournamentUploadPayload(
                id = STANDARD_TOURNAMENT_ID,
                ownerId = OWNER_ID,
                name = "Legacy Rotation",
                stageName = "Stage",
                organizerContact = "",
                status = "draft",
                revision = 1,
                format = TournamentFormat.GROUP_ROTATION.toCloudValue(),
                groupCount = 3,
                selectedGroupPairings = listOf(GroupPairingUploadPayload("A", "B", "A:B")),
            ),
            teamSlots = (1..18).map { slot ->
                TeamSlotUploadPayload(
                    id = TournamentCloudIdentity.teamSlotId(java.util.UUID.fromString(STANDARD_TOURNAMENT_ID), slot),
                    tournamentId = STANDARD_TOURNAMENT_ID,
                    slotNumber = slot,
                    teamName = "",
                    status = "active",
                    group = null,
                )
            },
            players = emptyList(),
        ),
        matches = emptyList(),
        legacyResults = emptyList(),
        identities = identities,
        assignments = emptyList(),
        uuidMatchSnapshots = emptyList(),
        uuidMatchAssignments = emptyList(),
        uuidMatchResults = emptyList(),
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T> noOp(type: Class<T>): T = Proxy.newProxyInstance(
        type.classLoader,
        arrayOf(type),
    ) { _, method, _ ->
        when (method.returnType) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            else -> null
        }
    } as T

    private object NoOpQueueRepository : PersistentSyncQueueRepository {
        override fun observeAll(): Flow<List<com.hoggamers.rankforge.domain.sync.SyncQueueEntry>> = flowOf(emptyList())
    }

    companion object {
        private const val OWNER_ID = "33333333-3333-4333-8333-333333333333"
        private const val OTHER_OWNER_ID = "44444444-4444-4444-8444-444444444444"
        private const val STANDARD_TOURNAMENT_ID = "11111111-1111-4111-8111-111111111111"
        private const val TEAM_UUID = "66666666-6666-4666-8666-666666666666"
    }
}
