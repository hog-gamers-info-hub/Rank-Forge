package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReadMatchTeamIdentityContextUseCaseTest {
    @Test
    fun unauthenticatedReadsReturnAuthenticationRequiredWithoutCallingRepository() = runTest {
        val repository = RecordingRepository { MatchTeamIdentityContextReadResult.InvalidMapping }

        val result = ReadMatchTeamIdentityContextUseCase(
            repository = repository,
            authRepository = SetupMutationUnauthenticatedAuthRepository,
        ).forMatch("match")

        assertEquals(MatchTeamIdentityContextReadResult.AuthenticationRequired, result)
        assertNull(repository.matchId)
        assertNull(repository.ownerUserId)
    }

    @Test
    fun unauthenticatedPairingReadsReturnAuthenticationRequiredWithoutCallingRepository() = runTest {
        val repository = RecordingRepository { MatchTeamIdentityContextReadResult.InvalidMapping }

        val result = ReadMatchTeamIdentityContextUseCase(
            repository = repository,
            authRepository = SetupMutationUnauthenticatedAuthRepository,
        ).forPairing(
            tournamentId = "tournament",
            pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C),
        )

        assertEquals(MatchTeamIdentityContextReadResult.AuthenticationRequired, result)
        assertNull(repository.tournamentId)
        assertNull(repository.pairing)
        assertNull(repository.ownerUserId)
    }

    @Test
    fun authenticatedReadsPreserveEveryTypedRepositoryResult() = runTest {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        val loaded = MatchTeamIdentityContextReadResult.Loaded(
            MatchTeamIdentityContext(
                tournamentId = "tournament",
                pairing = pairing,
                teams = (1..12).map { lobbySlotNumber ->
                    MatchLobbyTeamIdentity(
                        lobbySlotNumber = lobbySlotNumber,
                        teamSlotNumber = lobbySlotNumber,
                    )
                },
            ),
        )
        val results = listOf(
            MatchTeamIdentityContextReadResult.TournamentNotFound,
            MatchTeamIdentityContextReadResult.MatchNotFound,
            MatchTeamIdentityContextReadResult.PairingRequired,
            MatchTeamIdentityContextReadResult.PairingNotSelected,
            MatchTeamIdentityContextReadResult.SetupRequired,
            MatchTeamIdentityContextReadResult.InvalidMapping,
            loaded,
        )

        results.forEach { expected ->
            val repository = RecordingRepository { expected }
            val actual = ReadMatchTeamIdentityContextUseCase(
                repository = repository,
                authRepository = SignedInTournamentTestAuthRepository(),
            ).forMatch("match")

            assertSame(expected, actual)
            assertEquals("match", repository.matchId)
            assertEquals(SignedInTournamentTestAuthRepository.OWNER_USER_ID, repository.ownerUserId)
        }
    }

    @Test
    fun authenticatedPairingReadForwardsArgumentsAndReturnsRepositoryResultUnchanged() = runTest {
        val expectedPairing = GroupPairing(TournamentGroup.B, TournamentGroup.D)
        val expected = MatchTeamIdentityContextReadResult.Loaded(
            MatchTeamIdentityContext(
                tournamentId = "tournament-id",
                pairing = expectedPairing,
                teams = (1..12).map { lobbySlotNumber ->
                    MatchLobbyTeamIdentity(
                        lobbySlotNumber = lobbySlotNumber,
                        teamSlotNumber = lobbySlotNumber,
                    )
                },
            ),
        )
        val repository = RecordingRepository { expected }

        val actual = ReadMatchTeamIdentityContextUseCase(
            repository = repository,
            authRepository = SignedInTournamentTestAuthRepository(),
        ).forPairing("tournament-id", expectedPairing)

        assertSame(expected, actual)
        assertNull(repository.matchId)
        assertEquals("tournament-id", repository.tournamentId)
        assertEquals(expectedPairing, repository.pairing)
        assertEquals(SignedInTournamentTestAuthRepository.OWNER_USER_ID, repository.ownerUserId)
    }

    private class RecordingRepository(
        private val result: () -> MatchTeamIdentityContextReadResult,
    ) : MatchTeamIdentityContextRepository {
        var matchId: String? = null
        var tournamentId: String? = null
        var pairing: GroupPairing? = null
        var ownerUserId: String? = null

        override suspend fun readForMatch(
            matchId: String,
            ownerUserId: String,
        ): MatchTeamIdentityContextReadResult {
            this.matchId = matchId
            this.ownerUserId = ownerUserId
            return result()
        }

        override suspend fun readForPairing(
            tournamentId: String,
            pairing: GroupPairing,
            ownerUserId: String,
        ): MatchTeamIdentityContextReadResult {
            this.tournamentId = tournamentId
            this.pairing = pairing
            this.ownerUserId = ownerUserId
            return result()
        }
    }
}
