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
        val result = SubmitMatchCorrectionUseCase(
            repository,
            ValidateMatchResultUseCase(),
            ProtectedMatchCorrectionAction { ProtectedMatchCorrectionResult.Success(2) },
        )(
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

    @Test
    fun protectedCorrectionFailureLeavesFinalizedResultUnchanged() = runTest {
        val repository = createFinalizedRepository()

        val result = SubmitMatchCorrectionUseCase(
            repository,
            ValidateMatchResultUseCase(),
            ProtectedMatchCorrectionAction { ProtectedMatchCorrectionResult.NetworkFailure },
        )(SubmitMatchCorrectionInput("match-id", correctedRows()))

        assertEquals(
            MatchCorrectionGlobalError.NETWORK_FAILURE,
            (result as SubmitMatchCorrectionResult.Invalid).globalError,
        )
        assertEquals(1, repository.observeMatchById("match-id").first()!!.placements.first().position)
        assertTrue(repository.observeMatchById("match-id").first()!!.correctionHistory.isEmpty())
    }

    @Test
    fun tenTeamCorrectionSwapsPlacementsAndKeepsTenStoredResults() = runTest {
        val repository = createTenTeamFinalizedRepository()

        val result = SubmitMatchCorrectionUseCase(
            repository,
            ValidateMatchResultUseCase(),
            ProtectedMatchCorrectionAction { ProtectedMatchCorrectionResult.Success(2) },
        )(
            SubmitMatchCorrectionInput("match-id", correctedRows(10)),
        )

        val submitted = result as SubmitMatchCorrectionResult.Submitted
        assertEquals(10, submitted.match.placements.size)
        assertEquals(10, submitted.match.kills.size)
        assertEquals(setOf(1, 2), submitted.match.placements
            .filter { it.teamSlotNumber <= 2 }
            .map { it.position }
            .toSet())
        assertTrue(submitted.match.placements.none { it.teamSlotNumber > 10 })
    }

    @Test
    fun tenTeamCorrectionRejectsMissingOrExtraIncomingRows() = runTest {
        val repository = createTenTeamFinalizedRepository()

        listOf(
            correctedRows(10).dropLast(1),
            correctedRows(10) + MatchResultRowInput(11, "11", "10"),
        ).forEach { rows ->
            val result = SubmitMatchCorrectionUseCase(
                repository,
                ValidateMatchResultUseCase(),
                ProtectedMatchCorrectionAction { error("cloud correction must not be called") },
            )(
                SubmitMatchCorrectionInput("match-id", rows),
            )

            assertTrue(result is SubmitMatchCorrectionResult.Invalid)
            assertEquals(10, repository.observeMatchById("match-id").first()!!.placements.size)
        }
    }

    @Test
    fun localCorrectionPersistsParticipantStatusTransitionAndStatusAwareAudit() = runTest {
        val repository = createThreeTeamFinalizedRepository()
        val corrected = listOf(
            MatchParticipantResult(1, MatchParticipationStatus.PARTICIPATED, 1, 4),
            MatchParticipantResult(2, MatchParticipationStatus.PARTICIPATED, 2, 2),
            MatchParticipantResult(3, MatchParticipationStatus.PARTICIPATED, 3, 1),
        )

        val result = repository.submitMatchCorrection(
            matchId = "match-id",
            placements = corrected.map { MatchPlacement(it.teamSlotNumber, it.placement!!) },
            kills = corrected.map { MatchKill(it.teamSlotNumber, it.kills) },
            participantResults = corrected,
        )

        val submitted = result as SubmitMatchCorrectionRepositoryResult.Submitted
        assertEquals(corrected, submitted.match.participantResults)
        assertEquals(MatchParticipationStatus.PARTICIPATED, submitted.match.participantResults.last().participationStatus)
        assertEquals(MatchParticipationStatus.NO_SHOW, submitted.match.correctionHistory.single().previousParticipantResults.last().participationStatus)
        assertEquals(MatchParticipationStatus.PARTICIPATED, submitted.match.correctionHistory.single().correctedParticipantResults.last().participationStatus)
    }

    private suspend fun createDraftRepository(
        activeCount: Int? = null,
    ): InMemoryTournamentRepository {
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
        activeCount?.let { count ->
            repository.saveTeamNames(
                "tournament-id",
                (1..count).associateWith { slotNumber -> "Team $slotNumber" },
            )
        }
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

    private suspend fun createTenTeamFinalizedRepository(): InMemoryTournamentRepository {
        val repository = createDraftRepository(activeCount = 10)
        assertTrue(
            repository.finalizeDraftMatch(
                "match-id",
                (1..10).map { MatchPlacement(it, it) },
                (1..10).map { MatchKill(it, it - 1) },
            ) is FinalizeMatchRepositoryResult.Finalized,
        )
        return repository
    }

    private suspend fun createThreeTeamFinalizedRepository(): InMemoryTournamentRepository {
        val repository = createDraftRepository(activeCount = 3)
        val participantResults = listOf(
            MatchParticipantResult(1, MatchParticipationStatus.PARTICIPATED, 1, 3),
            MatchParticipantResult(2, MatchParticipationStatus.PARTICIPATED, 2, 2),
            MatchParticipantResult(3, MatchParticipationStatus.NO_SHOW, null, 0),
        )
        assertTrue(
            repository.finalizeDraftMatch(
                "match-id",
                participantResults.mapNotNull { it.placement?.let { position -> MatchPlacement(it.teamSlotNumber, position) } },
                participantResults.filter { it.placement != null }.map { MatchKill(it.teamSlotNumber, it.kills) },
                participantResults,
            ) is FinalizeMatchRepositoryResult.Finalized,
        )
        return repository
    }

    private fun correctedRows(count: Int = 12) = (1..count).map { slotNumber ->
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
