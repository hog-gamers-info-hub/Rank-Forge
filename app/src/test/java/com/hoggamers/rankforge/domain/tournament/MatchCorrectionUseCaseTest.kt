package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchCorrectionUseCaseTest {
    @Test
    fun finalizedMatchCanStartCorrection() = runTest {
        val repository = createFinalizedRepository()

        val result = StartMatchCorrectionUseCase(repository)("match-id")

        assertTrue(result is StartMatchCorrectionResult.Started)
        assertEquals(MatchStatus.FINALIZED, (result as StartMatchCorrectionResult.Started).match.status)
    }

    @Test
    fun draftMatchCannotStartCorrection() = runTest {
        val repository = createDraftRepository()

        val result = StartMatchCorrectionUseCase(repository)("match-id")

        assertEquals(
            MatchCorrectionGlobalError.MATCH_NOT_FINALIZED,
            (result as StartMatchCorrectionResult.Rejected).error,
        )
    }

    @Test
    fun discardCorrectionLeavesFinalizedResultUnchanged() = runTest {
        val repository = createFinalizedRepository()
        repository.saveDraftMatchValue("tournament-id", "match-id", 1, "12", "99")
        val before = repository.observeMatchById("match-id").first()!!

        ClearMatchCorrectionDraftUseCase(repository)(
            ClearMatchCorrectionDraftInput("tournament-id", "match-id"),
        )

        assertEquals(before, repository.observeMatchById("match-id").first())
        assertTrue(repository.observeDraftMatchValues("tournament-id", "match-id").first().isEmpty())
    }

    @Test
    fun validCorrectionSubmitsAndPreservesPreviousResultInformation() = runTest {
        val repository = createFinalizedRepository()
        val result = SubmitMatchCorrectionUseCase(repository, ValidateMatchResultUseCase())(
            SubmitMatchCorrectionInput("match-id", correctedRows()),
        )

        val submitted = result as SubmitMatchCorrectionResult.Submitted
        assertEquals(MatchStatus.FINALIZED, submitted.match.status)
        assertEquals(2, submitted.match.placements.first { it.teamSlotNumber == 1 }.position)
        assertEquals(1, submitted.match.kills.first { it.teamSlotNumber == 1 }.kills)
        assertEquals(1, submitted.match.correctionHistory.size)
        assertEquals(1, submitted.match.correctionHistory.single().previousPlacements.first().position)
        assertEquals(2, submitted.match.correctionHistory.single().correctedPlacements.first().position)
        assertTrue(repository.observeDraftMatchValues("tournament-id", "match-id").first().isEmpty())
    }

    @Test
    fun invalidCorrectionIsBlockedAndFinalizedResultRemains() = runTest {
        val repository = createFinalizedRepository()
        val result = SubmitMatchCorrectionUseCase(repository, ValidateMatchResultUseCase())(
            SubmitMatchCorrectionInput(
                "match-id",
                correctedRows().map { row -> if (row.teamSlotNumber == 1) row.copy(placement = "") else row },
            ),
        )

        assertTrue(result is SubmitMatchCorrectionResult.Invalid)
        assertEquals(
            1,
            repository.observeMatchById("match-id").first()!!.placements.first { it.teamSlotNumber == 1 }.position,
        )
        assertTrue(repository.observeMatchById("match-id").first()!!.correctionHistory.isEmpty())
    }

    private suspend fun createDraftRepository(): InMemoryTournamentRepository {
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

    private suspend fun createFinalizedRepository(): InMemoryTournamentRepository {
        val repository = createDraftRepository()
        repository.finalizeDraftMatch(
            "match-id",
            (1..12).map { MatchPlacement(it, it) },
            (1..12).map { MatchKill(it, it - 1) },
        )
        return repository
    }

    private fun correctedRows() = (1..12).map { slotNumber ->
        MatchResultRowInput(
            teamSlotNumber = slotNumber,
            placement = when (slotNumber) {
                1 -> "2"
                2 -> "1"
                else -> slotNumber.toString()
            },
            kills = when (slotNumber) {
                1 -> "1"
                else -> (slotNumber - 1).toString()
            },
        )
    }
}
