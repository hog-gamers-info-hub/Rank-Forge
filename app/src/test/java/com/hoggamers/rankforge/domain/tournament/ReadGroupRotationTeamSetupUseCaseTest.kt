package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadGroupRotationTeamSetupUseCaseTest {
    @Test
    fun signedInOwnerIdIsDelegatedAndLoadedIsMapped() = runBlocking {
        val repository = FakeRepository()
        repository.result = GroupRotationTeamSetupReadResult.Loaded(
            GroupRotationTeamSetupRead(tournament(), candidate()),
        )
        val useCase = ReadGroupRotationTeamSetupUseCase(
            repository,
            auth(AuthState.SignedIn(AuthUser("owner-1", "owner@test"))),
            UnsupportedIdentityRepository,
        )

        val result = useCase(TOURNAMENT_ID)

        assertEquals("owner-1", repository.ownerUserId)
        assertTrue(result is ReadGroupRotationTeamSetupResult.Loaded)
    }

    @Test
    fun unauthenticatedReturnsAuthenticationRequiredWithoutRepositoryCall() = runBlocking {
        val repository = FakeRepository()
        val useCase = ReadGroupRotationTeamSetupUseCase(repository, auth(AuthState.SignedOut), UnsupportedIdentityRepository)

        assertEquals(ReadGroupRotationTeamSetupResult.AuthenticationRequired, useCase(TOURNAMENT_ID))
        assertEquals(null, repository.ownerUserId)
    }

    @Test
    fun allRepositoryResultsMapDirectly() = runBlocking {
        val cases = listOf(
            GroupRotationTeamSetupReadResult.NoSavedSetup(tournament()) to
                ReadGroupRotationTeamSetupResult.NoSavedSetup(tournament()),
            GroupRotationTeamSetupReadResult.TournamentNotFound to
                ReadGroupRotationTeamSetupResult.TournamentNotFound,
            GroupRotationTeamSetupReadResult.InvalidStoredSetup to
                ReadGroupRotationTeamSetupResult.InvalidStoredSetup,
        )

        cases.forEach { (stored, expected) ->
            val repository = FakeRepository().also { it.result = stored }
            assertEquals(
                expected,
                ReadGroupRotationTeamSetupUseCase(
                    repository,
                    auth(AuthState.SignedIn(AuthUser("owner", null))),
                    UnsupportedIdentityRepository,
                )(TOURNAMENT_ID),
            )
        }
    }

    @Test
    fun loadedUuidRegistrySnapshotBecomesTheAuthoritativeCandidate() = runBlocking {
        val teamUuid = "00000000-0000-0000-0000-000000000003"
        val identityRepository = object : GroupRotationTeamIdentityLocalRepository {
            override suspend fun read(
                tournament: Tournament,
            ): GroupRotationTeamIdentityReadResult = GroupRotationTeamIdentityReadResult.Loaded(
                GroupRotationTeamIdentityLocalSnapshot(
                    teams = listOf(
                        GroupRotationTeamIdentity(tournament.id, teamUuid, "Phoenix", "phoenix"),
                    ),
                    assignments = listOf(
                        GroupRotationPairingAssignment(
                            tournament.id,
                            GroupPairing(TournamentGroup.A, TournamentGroup.B),
                            7,
                            teamUuid,
                        ),
                    ),
                ),
            )

            override suspend fun savePairing(
                tournament: Tournament,
                pairingKey: GroupPairing,
                entries: List<GroupRotationPairingTeamIdentityEntry>,
            ): GroupRotationTeamIdentityWriteResult = GroupRotationTeamIdentityWriteResult.Unsupported

            override suspend fun renameTeam(
                tournament: Tournament,
                teamUUID: String,
                displayName: String,
            ): GroupRotationTeamIdentityWriteResult = GroupRotationTeamIdentityWriteResult.Unsupported
        }
        val useCase = ReadGroupRotationTeamSetupUseCase(
            FakeRepository().also { it.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament()) },
            auth(AuthState.SignedIn(AuthUser("owner", null))),
            identityRepository,
        )

        val result = useCase(TOURNAMENT_ID)

        assertTrue(result is ReadGroupRotationTeamSetupResult.Loaded)
        val loaded = result as ReadGroupRotationTeamSetupResult.Loaded
        assertEquals(listOf("Phoenix"), loaded.candidate.entries.map { it.teamName })
        assertEquals(7, loaded.candidate.entries.single().lobbySlotNumber)
        assertTrue(loaded.identityAuthoritative)
    }

    private fun tournament(): Tournament = Tournament(
        id = TOURNAMENT_ID,
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = 3,
        selectedGroupPairings = listOf(GroupPairing(TournamentGroup.A, TournamentGroup.B)),
    )

    private fun candidate(): GroupRotationTeamSetupCandidate = GroupRotationTeamSetupCandidate(
        TOURNAMENT_ID,
        (1..12).map { GroupRotationPairingTeamEntry(GroupPairing(TournamentGroup.A, TournamentGroup.B), it, "Team $it") },
    )

    private fun auth(state: AuthState): AuthRepository = object : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(state)
        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String): AuthOperationResult = error("unused")
        override suspend fun login(email: String, password: String): AuthOperationResult = error("unused")
        override suspend fun logout(): AuthOperationResult = error("unused")
    }

    private class FakeRepository : GroupRotationTeamSetupReadRepository {
        var result: GroupRotationTeamSetupReadResult = GroupRotationTeamSetupReadResult.TournamentNotFound
        var ownerUserId: String? = null

        override suspend fun readGroupRotationTeamSetup(
            tournamentId: String,
            ownerUserId: String,
        ): GroupRotationTeamSetupReadResult {
            this.ownerUserId = ownerUserId
            return result
        }
    }

    private object UnsupportedIdentityRepository : GroupRotationTeamIdentityLocalRepository {
        override suspend fun read(
            tournament: Tournament,
        ): GroupRotationTeamIdentityReadResult = GroupRotationTeamIdentityReadResult.Unsupported

        override suspend fun savePairing(
            tournament: Tournament,
            pairingKey: GroupPairing,
            entries: List<GroupRotationPairingTeamIdentityEntry>,
        ): GroupRotationTeamIdentityWriteResult = GroupRotationTeamIdentityWriteResult.Unsupported

        override suspend fun renameTeam(
            tournament: Tournament,
            teamUUID: String,
            displayName: String,
        ): GroupRotationTeamIdentityWriteResult = GroupRotationTeamIdentityWriteResult.Unsupported
    }

    private companion object {
        const val TOURNAMENT_ID = "read-use-case-test"
    }
}
