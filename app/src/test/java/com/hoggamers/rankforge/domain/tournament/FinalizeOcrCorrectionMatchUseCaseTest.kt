package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalizeOcrCorrectionMatchUseCaseTest {
    @Test
    fun missingCorrectionDraftBlocksFinalization() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)

        val result = useCase(validInput(correctionRows = null))

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.MISSING_CORRECTION_DRAFT)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun fewerThanTwelveCorrectionRowsBlocksFinalization() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)

        val result = useCase(validInput(correctionRows = validCorrectionRows().dropLast(1)))

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.INVALID_CORRECTION_DRAFT)
        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.MISSING_CORRECTION_ROW)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun malformedDuplicateRowIndexBlocksFinalization() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)
        val rows = validCorrectionRows().mapIndexed { index, row ->
            if (index == 11) row.copy(rowIndex = 0) else row
        }

        val result = useCase(validInput(correctionRows = rows))

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.MALFORMED_ROW_DRAFT)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun missingPlacementBlocksFinalization() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)
        val rows = validCorrectionRows().replaceRow(0) { it.copy(correctedPlacement = "") }

        val result = useCase(validInput(correctionRows = rows))

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.MISSING_PLACEMENT)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun invalidPlacementBlocksFinalization() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)
        val rows = validCorrectionRows().replaceRow(0) { it.copy(correctedPlacement = "13") }

        val result = useCase(validInput(correctionRows = rows))

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.INVALID_PLACEMENT)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun duplicatePlacementBlocksFinalization() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)
        val rows = validCorrectionRows().replaceRow(1) { it.copy(correctedPlacement = "1") }

        val result = useCase(validInput(correctionRows = rows))

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.DUPLICATE_PLACEMENT)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun missingKillsBlocksFinalization() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)
        val rows = validCorrectionRows().replaceRow(0) { it.copy(correctedKills = " ") }

        val result = useCase(validInput(correctionRows = rows))

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.MISSING_KILLS)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun negativeKillsBlocksFinalization() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)
        val rows = validCorrectionRows().replaceRow(0) { it.copy(correctedKills = "-1") }

        val result = useCase(validInput(correctionRows = rows))

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.NEGATIVE_KILLS)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun nonNumericKillsBlocksFinalization() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)
        val rows = validCorrectionRows().replaceRow(0) { it.copy(correctedKills = "x") }

        val result = useCase(validInput(correctionRows = rows))

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.INVALID_KILLS)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun missingTeamSlotBlocksFinalization() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)
        val rows = validCorrectionRows().replaceRow(0) { it.copy(correctedTeamSlotNumber = null) }

        val result = useCase(validInput(correctionRows = rows))

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.MISSING_TEAM_SLOT)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun invalidTeamSlotBlocksFinalization() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)
        val rows = validCorrectionRows().replaceRow(0) { it.copy(correctedTeamSlotNumber = "13") }

        val result = useCase(validInput(correctionRows = rows))

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.INVALID_TEAM_SLOT)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun duplicateTeamSlotBlocksFinalization() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)
        val rows = validCorrectionRows().replaceRow(1) { it.copy(correctedTeamSlotNumber = "1") }

        val result = useCase(validInput(correctionRows = rows))

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.DUPLICATE_TEAM_SLOT)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun unavailableTeamSlotBlocksFinalization() = runTest {
        val repository = RejectingFinalizeRepository(
            availableTeamSlots = TeamSlot.SLOT_NUMBERS.toList().dropLast(1).toSet(),
        )
        val useCase = createUseCase(repository)

        val result = useCase(validInput())

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.TEAM_SLOT_UNAVAILABLE)
        assertEquals(0, repository.finalizeCallCount)
    }

    @Test
    fun warningDraftRequiresConfirmationBeforeFinalization() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)
        val rows = validCorrectionRows().replaceRow(0) {
            it.copy(warnings = setOf(FinalizeOcrCorrectionMatchWarning.KILLS_CHANGED_FROM_OCR))
        }

        val result = useCase(validInput(correctionRows = rows))

        val confirmation = result as FinalizeOcrCorrectionMatchResult.ConfirmationRequired
        assertEquals(1, confirmation.warningCount)
        assertEquals(setOf(0), confirmation.warningRowIndexes)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun confirmationAllowsValidWarningDraftToFinalize() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)
        val rows = validCorrectionRows().replaceRow(0) {
            it.copy(warnings = setOf(FinalizeOcrCorrectionMatchWarning.TEAM_SLOT_CHANGED_FROM_SUGGESTION))
        }

        val result = useCase(validInput(correctionRows = rows, warningConfirmationAccepted = true))

        val finalized = result as FinalizeOcrCorrectionMatchResult.Finalized
        assertEquals(MatchStatus.FINALIZED, finalized.match.status)
        assertEquals((1..12).toList(), finalized.match.placements.map { it.position })
        assertEquals((0..11).toList(), finalized.match.kills.map { it.kills })
    }

    @Test
    fun confirmationDoesNotOverrideBlockingDraftFailures() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)
        val rows = validCorrectionRows().replaceRow(1) {
            it.copy(
                correctedPlacement = "1",
                warnings = setOf(FinalizeOcrCorrectionMatchWarning.PLACEMENT_CHANGED_FROM_OCR),
            )
        }

        val result = useCase(validInput(correctionRows = rows, warningConfirmationAccepted = true))

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.DUPLICATE_PLACEMENT)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun validCorrectionRowsFinalizeThroughExistingMatchBoundary() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)

        val result = useCase(validInput())

        val finalized = result as FinalizeOcrCorrectionMatchResult.Finalized
        assertEquals(MatchStatus.FINALIZED, finalized.match.status)
        assertEquals((1..12).toList(), finalized.match.placements.map { it.teamSlotNumber })
        assertEquals((1..12).toList(), finalized.match.placements.map { it.position })
        assertEquals((1..12).toList(), finalized.match.kills.map { it.teamSlotNumber })
        assertEquals((0..11).toList(), finalized.match.kills.map { it.kills })
    }

    @Test
    fun missingTournamentBlocksFinalization() = runTest {
        val repository = RejectingFinalizeRepository(tournament = null)
        val useCase = createUseCase(repository)

        val result = useCase(validInput())

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.MISSING_TOURNAMENT)
        assertEquals(0, repository.finalizeCallCount)
    }

    @Test
    fun missingMatchBlocksFinalization() = runTest {
        val repository = RejectingFinalizeRepository(match = null)
        val useCase = createUseCase(repository)

        val result = useCase(validInput())

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.MISSING_MATCH)
        assertEquals(0, repository.finalizeCallCount)
    }

    @Test
    fun alreadyFinalizedMatchBlocksFinalization() = runTest {
        val repository = createRepository(matchStatus = MatchStatus.FINALIZED)
        val useCase = createUseCase(repository)

        val result = useCase(validInput())

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.ALREADY_FINALIZED)
        assertEquals(MatchStatus.FINALIZED, repository.observeMatchById(MATCH_ID).first()!!.status)
    }

    @Test
    fun repositoryFinalizationFailureIsReportedWithoutPartialMutation() = runTest {
        val repository = RejectingFinalizeRepository(finalizeFailure = FinalizeMatchFailure.INVALID_DATA)
        val useCase = createUseCase(repository)

        val result = useCase(validInput())

        val blocked = assertBlocked(result, FinalizeOcrCorrectionMatchFailure.FINALIZATION_FAILED)
        assertTrue(blocked.validation.errorsByTeamSlot.isNotEmpty())
        assertEquals(MatchStatus.DRAFT, repository.match!!.status)
        assertEquals(1, repository.finalizeCallCount)
    }

    @Test
    fun repeatedFinalizationAfterSuccessIsRejectedIdempotently() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)
        useCase(validInput())
        val preservedEvidence = repository.readPreservedOcrEvidence(MATCH_ID)

        val result = useCase(validInput())

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.ALREADY_FINALIZED)
        assertEquals(MatchStatus.FINALIZED, repository.observeMatchById(MATCH_ID).first()!!.status)
        assertEquals(preservedEvidence, repository.readPreservedOcrEvidence(MATCH_ID))
    }

    @Test
    fun validCorrectionRowsPreserveOriginalAndCorrectedOcrEvidence() = runTest {
        val repository = createRepository()
        val clock = Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC)
        val useCase = createUseCase(repository, clock)
        val rows = validCorrectionRowsWithEvidence()

        val result = useCase(
            validInput(
                correctionRows = rows,
                sourceScreenshotId = "screenshot-1",
            ),
        )

        val finalized = result as FinalizeOcrCorrectionMatchResult.Finalized
        val evidence = repository.readPreservedOcrEvidence(MATCH_ID)!!
        val firstRow = evidence.rows.first { it.rowIndex == 0 }
        val firstCorrection = evidence.correctionSnapshots.first { it.rowIndex == 0 }
        assertEquals(MatchStatus.FINALIZED, finalized.match.status)
        assertEquals(TOURNAMENT_ID, evidence.tournamentId)
        assertEquals(MATCH_ID, evidence.matchId)
        assertEquals("screenshot-1", evidence.sourceScreenshotId)
        assertEquals(clock.millis(), evidence.preservedAt)
        assertEquals("OCR_REVIEW_FINALIZATION", evidence.provenance)
        assertEquals(12, evidence.rows.size)
        assertEquals(12, evidence.correctionSnapshots.size)
        assertEquals("Raw OCR 0", firstRow.originalOcrText)
        assertEquals(12, firstRow.originalPlacement)
        assertEquals(10, firstRow.originalKills)
        assertEquals(12, firstRow.originalSuggestedTeamSlot)
        assertEquals("confidence-0", firstRow.confidenceSummary)
        assertEquals("safety-0", firstRow.safetySummary)
        assertTrue(firstRow.manualReviewRequired)
        assertEquals(1, firstCorrection.correctedPlacement)
        assertEquals(0, firstCorrection.correctedKills)
        assertEquals(1, firstCorrection.correctedTeamSlot)
        assertTrue(firstCorrection.placementChanged)
        assertTrue(firstCorrection.killsChanged)
        assertTrue(firstCorrection.teamSlotChanged)
    }

    @Test
    fun invalidCorrectionRowsDoNotPreserveOcrEvidence() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)
        val rows = validCorrectionRowsWithEvidence().replaceRow(1) { it.copy(correctedPlacement = "1") }

        val result = useCase(validInput(correctionRows = rows))

        assertBlocked(result, FinalizeOcrCorrectionMatchFailure.DUPLICATE_PLACEMENT)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)
        assertEquals(null, repository.readPreservedOcrEvidence(MATCH_ID))
    }

    @Test
    fun finalizationDoesNotCreateCorrectionHistory() = runTest {
        val repository = createRepository()
        val useCase = createUseCase(repository)

        val result = useCase(validInput())

        val finalized = result as FinalizeOcrCorrectionMatchResult.Finalized
        assertTrue(finalized.match.correctionHistory.isEmpty())
    }

    private fun createUseCase(
        repository: TournamentRepository,
        clock: Clock = Clock.systemUTC(),
    ): FinalizeOcrCorrectionMatchUseCase =
        FinalizeOcrCorrectionMatchUseCase(
            repository = repository,
            finalizeMatch = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase()),
            clock = clock,
        )

    private suspend fun createRepository(
        matchStatus: MatchStatus = MatchStatus.DRAFT,
    ): InMemoryTournamentRepository {
        val repository = InMemoryTournamentRepository()
        repository.create(testTournament())
        repository.createDraftMatch(testMatch(status = matchStatus))
        return repository
    }

    private fun validInput(
        correctionRows: List<FinalizeOcrCorrectionRowInput>? = validCorrectionRows(),
        warningConfirmationAccepted: Boolean = false,
        sourceScreenshotId: String? = null,
    ): FinalizeOcrCorrectionMatchInput =
        FinalizeOcrCorrectionMatchInput(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
            correctionRows = correctionRows,
            warningConfirmationAccepted = warningConfirmationAccepted,
            sourceScreenshotId = sourceScreenshotId,
        )

    private fun validCorrectionRows(): List<FinalizeOcrCorrectionRowInput> =
        (0 until TeamSlot.MAX_SLOT_NUMBER).map { index ->
            FinalizeOcrCorrectionRowInput(
                rowIndex = index,
                correctedPlacement = (index + 1).toString(),
                correctedKills = index.toString(),
                correctedTeamSlotNumber = (index + 1).toString(),
            )
        }

    private fun validCorrectionRowsWithEvidence(): List<FinalizeOcrCorrectionRowInput> =
        validCorrectionRows().map { row ->
            row.copy(
                originalOcrText = "Raw OCR ${row.rowIndex}",
                originalPlacement = TeamSlot.MAX_SLOT_NUMBER - row.rowIndex,
                originalKills = row.rowIndex + 10,
                originalSuggestedTeamSlot = TeamSlot.MAX_SLOT_NUMBER - row.rowIndex,
                confidenceSummary = "confidence-${row.rowIndex}",
                safetySummary = "safety-${row.rowIndex}",
                manualReviewRequired = row.rowIndex % 2 == 0,
            )
        }

    private fun List<FinalizeOcrCorrectionRowInput>.replaceRow(
        rowIndex: Int,
        transform: (FinalizeOcrCorrectionRowInput) -> FinalizeOcrCorrectionRowInput,
    ): List<FinalizeOcrCorrectionRowInput> =
        map { row -> if (row.rowIndex == rowIndex) transform(row) else row }

    private fun assertBlocked(
        result: FinalizeOcrCorrectionMatchResult,
        failure: FinalizeOcrCorrectionMatchFailure,
    ): FinalizeOcrCorrectionMatchResult.Blocked {
        val blocked = result as FinalizeOcrCorrectionMatchResult.Blocked
        assertTrue(failure in blocked.failures)
        return blocked
    }

    private class RejectingFinalizeRepository(
        private val tournament: Tournament? = testTournament(),
        var match: Match? = testMatch(),
        private val availableTeamSlots: Set<Int> = TeamSlot.SLOT_NUMBERS.toSet(),
        private val finalizeFailure: FinalizeMatchFailure = FinalizeMatchFailure.INVALID_DATA,
    ) : TournamentRepository {
        var finalizeCallCount: Int = 0
            private set

        override suspend fun create(tournament: Tournament) = Unit

        override fun observeAll(): Flow<List<Tournament>> = flowOf(listOfNotNull(tournament))

        override fun observeById(tournamentId: String): Flow<Tournament?> =
            flowOf(tournament?.takeIf { it.id == tournamentId })

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> =
            flowOf(
                availableTeamSlots.map { slotNumber ->
                    TeamSlot.create(
                        tournamentId = tournamentId,
                        slotNumber = slotNumber,
                    )
                },
            )

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

        override suspend fun confirmTournament(tournamentId: String): Boolean = false

        override fun observeMatchById(matchId: String): Flow<Match?> =
            flowOf(match?.takeIf { it.id == matchId })

        override suspend fun finalizeDraftMatch(
            matchId: String,
            placements: List<MatchPlacement>,
            kills: List<MatchKill>,
        ): FinalizeMatchRepositoryResult {
            finalizeCallCount += 1
            return FinalizeMatchRepositoryResult.Rejected(finalizeFailure)
        }

        override suspend fun finalizeDraftMatchWithOcrEvidence(
            matchId: String,
            placements: List<MatchPlacement>,
            kills: List<MatchKill>,
            evidence: PreservedMatchOcrEvidence,
        ): FinalizeMatchRepositoryResult =
            finalizeDraftMatch(matchId, placements, kills)
    }

    private companion object {
        const val TOURNAMENT_ID = "tournament-id"
        const val MATCH_ID = "match-id"

        fun testTournament(): Tournament =
            Tournament(
                id = TOURNAMENT_ID,
                name = "Summer Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            )

        fun testMatch(status: MatchStatus = MatchStatus.DRAFT): Match =
            Match(
                id = MATCH_ID,
                tournamentId = TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = status,
            )
    }
}
