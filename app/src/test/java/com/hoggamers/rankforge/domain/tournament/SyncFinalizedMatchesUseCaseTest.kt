package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncFinalizedMatchesUseCaseTest {
    @Test
    fun unauthenticatedSyncIsRejectedBeforeCloudAccess() = runTest {
        val cloud = RecordingCloudRepository()
        val useCase = SyncFinalizedMatchesUseCase(
            tournamentRepository = localRepository(),
            authRepository = FakeAuthRepository(AuthState.SignedOut),
            cloudSyncRepository = cloud,
        )

        assertEquals(FinalizedMatchCloudSyncResult.AuthenticationRequired, useCase(TOURNAMENT_ID))
        assertNull(cloud.snapshot)
    }

    @Test
    fun authenticatedSyncSendsAndPreservesOnlyLocalFinalizedData() = runTest {
        val local = localRepository()
        val before = local.observeMatchesByTournamentId(TOURNAMENT_ID).first()
        val cloud = RecordingCloudRepository()
        val useCase = SyncFinalizedMatchesUseCase(
            tournamentRepository = local,
            authRepository = FakeAuthRepository(AuthState.SignedIn(AuthUser("owner-id", null))),
            cloudSyncRepository = cloud,
        )

        assertEquals(FinalizedMatchCloudSyncResult.Success, useCase(TOURNAMENT_ID))
        assertEquals(before, cloud.snapshot?.matches)
        assertEquals(before, local.observeMatchesByTournamentId(TOURNAMENT_ID).first())
    }

    @Test
    fun authorizationAndNetworkFailuresLeaveLocalFinalizedMatchUntouched() = runTest {
        val local = localRepository()
        val before = local.observeMatchesByTournamentId(TOURNAMENT_ID).first()
        val auth = FakeAuthRepository(AuthState.SignedIn(AuthUser("owner-id", null)))

        val authorization = SyncFinalizedMatchesUseCase(
            local,
            auth,
            RecordingCloudRepository(FinalizedMatchCloudSyncResult.AuthorizationFailure),
        )(TOURNAMENT_ID)
        val network = SyncFinalizedMatchesUseCase(
            local,
            auth,
            RecordingCloudRepository(FinalizedMatchCloudSyncResult.NetworkFailure),
        )(TOURNAMENT_ID)

        assertEquals(FinalizedMatchCloudSyncResult.AuthorizationFailure, authorization)
        assertEquals(FinalizedMatchCloudSyncResult.NetworkFailure, network)
        assertEquals(before, local.observeMatchesByTournamentId(TOURNAMENT_ID).first())
    }

    private suspend fun localRepository(): InMemoryTournamentRepository = InMemoryTournamentRepository().also { repository ->
        repository.create(
            Tournament(
                id = TOURNAMENT_ID,
                name = "Summer Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        repository.createDraftMatch(
            Match(
                id = "finalized-match",
                tournamentId = TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        repository.finalizeDraftMatch(
            matchId = "finalized-match",
            placements = TeamSlot.SLOT_NUMBERS.map { MatchPlacement(it, it) },
            kills = TeamSlot.SLOT_NUMBERS.map { MatchKill(it, it - 1) },
        )
    }

    private class RecordingCloudRepository(
        private val result: FinalizedMatchCloudSyncResult = FinalizedMatchCloudSyncResult.Success,
    ) : FinalizedMatchCloudSyncRepository {
        var snapshot: FinalizedMatchCloudSyncSnapshot? = null

        override suspend fun sync(snapshot: FinalizedMatchCloudSyncSnapshot): FinalizedMatchCloudSyncResult {
            this.snapshot = snapshot
            return result
        }
    }

    private class FakeAuthRepository(
        private val state: AuthState,
    ) : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(state)

        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession

        override suspend fun signUp(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated)

        override suspend fun login(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)

        override suspend fun logout(): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
