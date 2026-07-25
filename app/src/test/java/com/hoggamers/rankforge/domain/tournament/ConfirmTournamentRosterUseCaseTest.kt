package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository

class ConfirmTournamentRosterUseCaseTest {
    @Test
    fun invalidRosterCannotConfirm() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament())
        val useCase = ConfirmTournamentRosterUseCase(
            repository = repository,
            validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
        )

        val result = useCase("stable-id")

        assertTrue(result is ConfirmTournamentRosterResult.Invalid)
        assertEquals(TournamentStatus.DRAFT, repository.observeById("stable-id").first()?.status)
    }

    @Test
    fun validRosterConfirmsAndRepeatedConfirmationIsAlreadyConfirmed() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament())
        repository.saveTeamNames(
            "stable-id",
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        (1..12).forEach { slotNumber ->
            repository.saveRoster(
                tournamentId = "stable-id",
                slotNumber = slotNumber,
                players = (0..3).map { playerIndex ->
                    RosterPlayer.create("stable-id", slotNumber, "Player $playerIndex")
                },
            )
        }
        val useCase = ConfirmTournamentRosterUseCase(
            repository = repository,
            validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
        )

        assertTrue(useCase("stable-id") is ConfirmTournamentRosterResult.Confirmed)
        assertTrue(useCase("stable-id") is ConfirmTournamentRosterResult.AlreadyConfirmed)
        assertEquals(TournamentStatus.CONFIRMED, repository.observeById("stable-id").first()?.status)
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
