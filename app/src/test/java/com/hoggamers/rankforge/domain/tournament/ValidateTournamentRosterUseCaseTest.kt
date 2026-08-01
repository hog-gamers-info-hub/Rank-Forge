package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ValidateTournamentRosterUseCaseTest {
    @Test
    fun aggregatesRostersInSlotOrderAndUsesOverrideTeamNames() = runTest {
        val repository = StubRepository(
            slots = listOf(
                TeamSlot("tournament-id", 2, "Stored Two"),
                TeamSlot("tournament-id", 1, "Stored One"),
            ),
            rostersBySlot = mapOf(
                1 to listOf(
                    RosterPlayer("tournament-id", 1, "One"),
                    RosterPlayer("tournament-id", 1, "Two"),
                    RosterPlayer("tournament-id", 1, "Three"),
                    RosterPlayer("tournament-id", 1, "Four"),
                ),
                2 to emptyList(),
            ),
        )
        val useCase = ValidateTournamentRosterUseCase(repository, RosterValidator())

        val result = useCase(
            tournamentId = "tournament-id",
            teamNamesBySlotNumber = mapOf(1 to ""),
        )

        assertEquals(
            listOf(
                RosterValidationIssue.MissingTeamName(1),
                RosterValidationIssue.InvalidPlayerCount(2, 0),
            ),
            result.issues,
        )
    }

    private class StubRepository(
        private val slots: List<TeamSlot>,
        private val rostersBySlot: Map<Int, List<RosterPlayer>>,
    ) : TournamentRepository {
        override suspend fun create(tournament: Tournament) = Unit

        override fun observeAll(): Flow<List<Tournament>> = flowOf(emptyList())

        override fun observeById(tournamentId: String): Flow<Tournament?> = flowOf(null)

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> = flowOf(slots)

        override suspend fun saveTeamNames(
            tournamentId: String,
            teamNamesBySlotNumber: Map<Int, String>,
        ) = Unit

        override fun observeRosterByTournamentAndSlot(
            tournamentId: String,
            slotNumber: Int,
        ): Flow<List<RosterPlayer>> = flowOf(rostersBySlot[slotNumber].orEmpty())

        override suspend fun saveRoster(
            tournamentId: String,
            slotNumber: Int,
            players: List<RosterPlayer>,
        ) = Unit

        override suspend fun confirmTournament(tournamentId: String): Boolean = false
    }
}
