package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveTournamentsUseCaseTest {
    @Test
    fun observingTournamentsReturnsNewestCreatedFirst() = runTest {
        val repository = TestTournamentRepository()
        val older = tournament(id = "older", name = "Older Cup")
        val newer = tournament(id = "newer", name = "Newer Cup")
        repository.create(older)
        repository.create(newer)

        val result = ObserveTournamentsUseCase(repository)().first()

        assertEquals(listOf(newer, older), result)
    }

    private fun tournament(
        id: String,
        name: String,
    ) = Tournament(
        id = id,
        name = name,
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
    )

    private class TestTournamentRepository : TournamentRepository {
        private val state = MutableStateFlow<List<Tournament>>(emptyList())

        override suspend fun create(tournament: Tournament) {
            state.value = state.value + tournament
        }

        override fun observeAll(): Flow<List<Tournament>> = state

        override fun observeById(tournamentId: String): Flow<Tournament?> =
            state.map { tournaments -> tournaments.firstOrNull { it.id == tournamentId } }

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> =
            state.map { tournaments ->
                if (tournaments.any { it.id == tournamentId }) {
                    TeamSlot.fixedSlotsForTournament(tournamentId)
                } else {
                    emptyList()
                }
            }
    }
}
