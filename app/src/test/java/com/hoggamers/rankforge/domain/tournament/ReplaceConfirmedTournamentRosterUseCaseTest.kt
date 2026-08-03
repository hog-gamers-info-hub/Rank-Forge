package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaceConfirmedTournamentRosterUseCaseTest {
    @Test
    fun validCompleteCandidateReachesRepositoryAndSucceeds() = runTest {
        val repository = RecordingRepository(ReplaceConfirmedTournamentRosterRepositoryResult.Replaced)
        val result = useCase(repository)(candidate())

        assertEquals(ReplaceConfirmedTournamentRosterResult.Replaced, result)
        assertTrue(repository.receivedCandidate != null)
    }

    @Test
    fun incompleteCandidateIsRejectedBeforeRepositoryMutation() = runTest {
        val repository = RecordingRepository(ReplaceConfirmedTournamentRosterRepositoryResult.Replaced)
        val incomplete = candidate().copy(
            rosterPlayersBySlotNumber = candidate().rosterPlayersBySlotNumber - 12,
        )

        assertEquals(
            ReplaceConfirmedTournamentRosterResult.InvalidCandidate,
            useCase(repository)(incomplete),
        )
        assertFalse(repository.receivedCandidate != null)
    }

    @Test
    fun existingRosterValidatorIsApplied() = runTest {
        val repository = RecordingRepository(ReplaceConfirmedTournamentRosterRepositoryResult.Replaced)
        val invalid = candidate().copy(
            teamNamesBySlotNumber = candidate().teamNamesBySlotNumber + (2 to "Team 1"),
        )

        assertEquals(
            ReplaceConfirmedTournamentRosterResult.InvalidCandidate,
            useCase(repository)(invalid),
        )
        assertFalse(repository.receivedCandidate != null)
    }

    @Test
    fun playerTournamentAndSlotMismatchIsRejectedBeforeRepositoryMutation() = runTest {
        val repository = RecordingRepository(ReplaceConfirmedTournamentRosterRepositoryResult.Replaced)
        val invalid = candidate().copy(
            rosterPlayersBySlotNumber = candidate().rosterPlayersBySlotNumber +
    (1 to listOf(RosterPlayer("other-tournament", 2, "Wrong Team Player"))),
        )

        assertEquals(
            ReplaceConfirmedTournamentRosterResult.InvalidCandidate,
            useCase(repository)(invalid),
        )
        assertFalse(repository.receivedCandidate != null)
    }

    @Test
    fun repositoryNotFoundMapsCorrectly() = runTest {
        val repository = RecordingRepository(ReplaceConfirmedTournamentRosterRepositoryResult.TournamentNotFound)

        assertEquals(
            ReplaceConfirmedTournamentRosterResult.TournamentNotFound,
            useCase(repository)(candidate()),
        )
    }

    @Test
    fun repositoryBlockedByMatchesMapsCorrectly() = runTest {
        val repository = RecordingRepository(ReplaceConfirmedTournamentRosterRepositoryResult.BlockedByExistingMatches)

        assertEquals(
            ReplaceConfirmedTournamentRosterResult.BlockedByExistingMatches,
            useCase(repository)(candidate()),
        )
    }

    private fun useCase(repository: TournamentRepository) =
        ReplaceConfirmedTournamentRosterUseCase(repository, RosterValidator())

    private fun candidate(tournamentId: String = "tournament-1") = ConfirmedRosterReplacementCandidate(
        tournamentId = tournamentId,
        teamNamesBySlotNumber = TeamSlot.SLOT_NUMBERS.associateWith { slotNumber -> "Team $slotNumber" },
        rosterPlayersBySlotNumber = TeamSlot.SLOT_NUMBERS.associateWith { slotNumber ->
            (1..4).map { playerNumber ->
                RosterPlayer(tournamentId, slotNumber, "Player $slotNumber-$playerNumber")
            }
        },
    )

    private class RecordingRepository(
        private val replacementResult: ReplaceConfirmedTournamentRosterRepositoryResult,
    ) : TournamentRepository {
        var receivedCandidate: ConfirmedRosterReplacementCandidate? = null

        override suspend fun create(tournament: Tournament) = Unit

        override fun observeAll(): Flow<List<Tournament>> = flowOf(emptyList())

        override fun observeById(tournamentId: String): Flow<Tournament?> = flowOf(null)

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> =
            flowOf(TeamSlot.fixedSlotsForTournament(tournamentId))

        override suspend fun saveTeamNames(
            tournamentId: String,
            teamNamesBySlotNumber: Map<Int, String>,
        ) = Unit

        override fun observeRosterByTournamentAndSlot(
            tournamentId: String,
            slotNumber: Int,
        ): Flow<List<RosterPlayer>> = flowOf(emptyList())

        override suspend fun saveRoster(
            tournamentId: String,
            slotNumber: Int,
            players: List<RosterPlayer>,
        ) = Unit

        override suspend fun replaceConfirmedTournamentRoster(
            candidate: ConfirmedRosterReplacementCandidate,
        ): ReplaceConfirmedTournamentRosterRepositoryResult {
            receivedCandidate = candidate
            return replacementResult
        }

        override suspend fun confirmTournament(tournamentId: String): Boolean = false
    }
}
