package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository

class SaveTeamSlotNamesUseCaseTest {
    @Test
    fun trimsWhitespaceOnSaveOnly() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament().copy(ownerUserId = SignedInTournamentTestAuthRepository.OWNER_USER_ID))

        SaveTeamSlotNamesUseCase(repository, SignedInTournamentTestAuthRepository())(
            tournamentId = "stable-id",
            teamNamesBySlotNumber = mapOf(1 to "  Alpha Team  "),
        )

        val slot = repository.observeSlotsByTournamentId("stable-id").first().first { it.slotNumber == 1 }
        assertEquals("Alpha Team", slot.teamName)
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
