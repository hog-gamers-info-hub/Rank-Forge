package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalizeMatchUseCaseTest {
    @Test
    fun validDraftFinalizesAndClearsOnlyRawDraftCache() = runTest {
        val repository = createRepository()
        repository.saveDraftMatchValue("tournament-id", "match-id", 1, "1", "4")
        val useCase = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase())

        val result = useCase(FinalizeMatchInput("match-id", validRows()))

        val finalized = result as FinalizeMatchResult.Finalized
        assertEquals(MatchStatus.FINALIZED, finalized.match.status)
        assertEquals((1..12).toList(), finalized.match.placements.map { it.position })
        assertEquals((0..11).toList(), finalized.match.kills.map { it.kills })
        assertTrue(repository.observeDraftMatchValues("tournament-id", "match-id").first().isEmpty())
        assertEquals(
            MatchStatus.FINALIZED,
            repository.observeMatchById("match-id").first()!!.status,
        )
    }

    @Test
    fun invalidDraftCannotFinalize() = runTest {
        val repository = createRepository()
        val useCase = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase())
        val rows = validRows().map { row ->
            if (row.teamSlotNumber == 1) row.copy(placement = "") else row
        }

        val result = useCase(FinalizeMatchInput("match-id", rows))

        val invalid = result as FinalizeMatchResult.Invalid
        assertTrue(MatchResultValidationError.MISSING_PLACEMENT in invalid.validation.errorsByTeamSlot.getValue(1))
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById("match-id").first()!!.status)
    }

    @Test
    fun finalizedMatchCannotBeFinalizedAgain() = runTest {
        val repository = createRepository()
        val useCase = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase())
        useCase(FinalizeMatchInput("match-id", validRows()))

        val result = useCase(FinalizeMatchInput("match-id", validRows()))

        assertEquals(
            FinalizeMatchGlobalError.MATCH_NOT_DRAFT,
            (result as FinalizeMatchResult.Invalid).globalError,
        )
    }

    private suspend fun createRepository(): InMemoryTournamentRepository {
        val repository = InMemoryTournamentRepository()
        repository.create(
            Tournament(
                id = "tournament-id",
                name = "Summer Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        repository.createDraftMatch(
            Match(
                id = "match-id",
                tournamentId = "tournament-id",
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        return repository
    }

    private fun validRows() = (1..12).map { slotNumber ->
        MatchResultRowInput(
            teamSlotNumber = slotNumber,
            placement = slotNumber.toString(),
            kills = (slotNumber - 1).toString(),
        )
    }
}
