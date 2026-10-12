package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.data.cloud.GroupPairingUploadPayload
import com.hoggamers.rankforge.data.cloud.GroupRotationUuidCloudRestorationMapper
import com.hoggamers.rankforge.data.cloud.GroupRotationUuidCloudRestorationPayloads
import com.hoggamers.rankforge.data.cloud.GroupRotationUuidCloudRestorationRemoteDataSource
import com.hoggamers.rankforge.data.cloud.GroupRotationUuidCloudRestorationRemoteResult
import com.hoggamers.rankforge.data.cloud.GroupRotationUuidTeamIdentityPayload
import com.hoggamers.rankforge.data.cloud.TeamSlotUploadPayload
import com.hoggamers.rankforge.data.cloud.TournamentCloudRestorationPayloads
import com.hoggamers.rankforge.data.cloud.TournamentUploadPayload
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreGroupRotationUuidCloudUseCaseTest {
    @Test
    fun unauthenticatedReadDoesNotCallRemoteOrLocal() = runTest {
        val remote = FakeRemote()
        val local = FakeLocal()
        val result = useCase(AuthState.SignedOut, remote, local)(T)

        assertEquals(GroupRotationUuidCloudRestorationResult.AuthenticationRequired, result)
        assertEquals(0, remote.readCount)
        assertEquals(0, local.restoreCount)
    }

    @Test
    fun remoteFailuresAreMappedAndNeverTreatedAsEmptySnapshots() = runTest {
        val categories = listOf(
            TournamentCloudRestorationFailureCategory.AUTHENTICATION to
                GroupRotationUuidCloudRestorationResult.AuthenticationRequired,
            TournamentCloudRestorationFailureCategory.AUTHORIZATION to
                GroupRotationUuidCloudRestorationResult.AuthorizationFailure,
            TournamentCloudRestorationFailureCategory.NOT_FOUND to
                GroupRotationUuidCloudRestorationResult.NotFound,
            TournamentCloudRestorationFailureCategory.NETWORK to
                GroupRotationUuidCloudRestorationResult.NetworkFailure,
            TournamentCloudRestorationFailureCategory.VALIDATION to
                GroupRotationUuidCloudRestorationResult.ValidationFailure,
        )

        categories.forEach { (category, expected) ->
            val result = useCase(
                AuthState.SignedIn(AuthUser(OWNER, "owner@example.test")),
                FakeRemote(GroupRotationUuidCloudRestorationRemoteResult.Failure(category)),
                FakeLocal(),
            )(T)
            assertEquals(expected, result)
        }
    }

    @Test
    fun localConflictDeletionAndInvalidResultsRemainExplicit() = runTest {
        val outcomes = listOf(
            GroupRotationUuidLocalRestoreResult.Conflict to GroupRotationUuidCloudRestorationResult.Conflict,
            GroupRotationUuidLocalRestoreResult.DeletionBlocked to GroupRotationUuidCloudRestorationResult.DeletionBlocked,
            GroupRotationUuidLocalRestoreResult.Invalid to GroupRotationUuidCloudRestorationResult.ValidationFailure,
        )

        outcomes.forEach { (localResult, expected) ->
            val result = useCase(
                AuthState.SignedIn(AuthUser(OWNER, "owner@example.test")),
                FakeRemote(GroupRotationUuidCloudRestorationRemoteResult.Success(payloads())),
                FakeLocal(localResult),
            )(T)
            assertEquals(expected, result)
        }
    }

    @Test
    fun localPersistenceExceptionFailsClosed() = runTest {
        val result = useCase(
            AuthState.SignedIn(AuthUser(OWNER, "owner@example.test")),
            FakeRemote(GroupRotationUuidCloudRestorationRemoteResult.Success(payloads())),
            FakeLocal(throwOnRestore = true),
        )(T)

        assertEquals(GroupRotationUuidCloudRestorationResult.LocalTransactionFailure, result)
    }

    @Test
    fun sameOwnerRestoreReturnsSuccessWithRevisionFencedSnapshot() = runTest {
        val local = FakeLocal(GroupRotationUuidLocalRestoreResult.Restored)
        val result = useCase(
            AuthState.SignedIn(AuthUser(OWNER, "owner@example.test")),
            FakeRemote(GroupRotationUuidCloudRestorationRemoteResult.Success(payloads())),
            local,
        )(T)

        assertTrue(result is GroupRotationUuidCloudRestorationResult.Restored)
        assertEquals("Rotation", (result as GroupRotationUuidCloudRestorationResult.Restored).tournamentName)
        assertEquals(1, local.restoreCount)
        assertEquals(7, local.snapshot?.cloudRevision?.value)
        assertEquals(OWNER, local.ownerUserId)
    }

    @Test
    fun malformedOrForeignMappedPayloadFailsBeforeLocalWrite() = runTest {
        val malformed = payloads().copy(
            identities = listOf(
                GroupRotationUuidTeamIdentityPayload(
                    tournamentId = OTHER_TOURNAMENT,
                    teamUUID = U1,
                    displayName = "Phoenix",
                    normalizedName = "phoenix",
                ),
            ),
        )
        val local = FakeLocal()
        val result = useCase(
            AuthState.SignedIn(AuthUser(OWNER, "owner@example.test")),
            FakeRemote(GroupRotationUuidCloudRestorationRemoteResult.Success(malformed)),
            local,
        )(T)

        assertEquals(GroupRotationUuidCloudRestorationResult.ValidationFailure, result)
        assertEquals(0, local.restoreCount)
    }

    private fun useCase(
        authState: AuthState,
        remote: FakeRemote,
        local: FakeLocal,
    ) = RestoreGroupRotationUuidCloudUseCase(remote, local, FakeAuth(authState))

    private class FakeRemote(
        private val result: GroupRotationUuidCloudRestorationRemoteResult =
            GroupRotationUuidCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.VALIDATION,
            ),
    ) : GroupRotationUuidCloudRestorationRemoteDataSource {
        var readCount = 0

        override suspend fun readOwnedGroupRotation(
            tournamentId: String,
        ): GroupRotationUuidCloudRestorationRemoteResult {
            readCount++
            return result
        }
    }

    private class FakeLocal(
        private val result: GroupRotationUuidLocalRestoreResult = GroupRotationUuidLocalRestoreResult.Restored,
        private val throwOnRestore: Boolean = false,
    ) : GroupRotationUuidCloudRestorationLocalRepository {
        var restoreCount = 0
        var snapshot: GroupRotationUuidCloudRestorationSnapshot? = null
        var ownerUserId: String? = null

        override suspend fun restoreGroupRotationUuidSnapshot(
            snapshot: GroupRotationUuidCloudRestorationSnapshot,
            expectedOwnerUserId: String,
        ): GroupRotationUuidLocalRestoreResult {
            restoreCount++
            if (throwOnRestore) error("transaction failed")
            this.snapshot = snapshot
            ownerUserId = expectedOwnerUserId
            return result
        }
    }

    private class FakeAuth(private val state: AuthState) : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(state)
        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated)
        override suspend fun login(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)
        override suspend fun logout(): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
    }

    private fun payloads() = GroupRotationUuidCloudRestorationPayloads(
        parent = TournamentCloudRestorationPayloads(
            tournament = TournamentUploadPayload(
                id = T,
                ownerId = OWNER,
                name = "Rotation",
                stageName = "Stage",
                organizerContact = "",
                status = "draft",
                revision = 7,
                format = TournamentFormat.GROUP_ROTATION.toCloudValue(),
                groupCount = 3,
                selectedGroupPairings = listOf(GroupPairingUploadPayload("A", "B", "A:B")),
            ),
            teamSlots = (1..18).map { slot ->
                TeamSlotUploadPayload(
                    id = "00000000-0000-0000-0000-${slot.toString().padStart(12, '0')}",
                    tournamentId = T,
                    slotNumber = slot,
                    teamName = "",
                    status = "active",
                )
            },
            players = emptyList(),
        ),
        matches = emptyList(),
        legacyResults = emptyList(),
        identities = listOf(
            GroupRotationUuidTeamIdentityPayload(T, U1, "Phoenix", "phoenix"),
        ),
        assignments = emptyList(),
        uuidMatchSnapshots = emptyList(),
        uuidMatchAssignments = emptyList(),
        uuidMatchResults = emptyList(),
    )

    companion object {
        private const val T = "11111111-1111-4111-8111-111111111111"
        private const val OTHER_TOURNAMENT = "22222222-2222-4222-8222-222222222222"
        private const val OWNER = "33333333-3333-4333-8333-333333333333"
        private const val U1 = "66666666-6666-4666-8666-666666666666"
    }
}
