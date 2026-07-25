package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository

class SaveRosterUseCaseTest {
    @Test
    fun useCaseRejectsMoreThanSixPlayers() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament())
        val useCase = SaveRosterUseCase(repository)
        val players = (1..7).map { playerNumber ->
            RosterPlayer.create("stable-id", 1, "Player $playerNumber")
        }

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                useCase("stable-id", 1, players)
            }
        }
        assertEquals(emptyList<RosterPlayer>(), repository.observeRosterByTournamentAndSlot("stable-id", 1).first())
    }

    private fun tournament() = Tournament(
        id = "stable-id",
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
    )
}
