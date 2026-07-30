package com.hoggamers.rankforge.domain.ocr.parsing

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionIdentity
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionType
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterOcrValidatorTest {
    private val validator = DefaultRosterOcrValidator()

    @Test
    fun completeTwelveSlotCandidateSetPreservesOrderAndHasNoBlockingIssues() {
        val result = validate((1..12).map(::candidate))

        assertEquals((1..12).toList(), result.slotResults.map { it.tournamentSlotCandidate.tournamentSlotNumber })
        assertTrue(result.issues.none { it.severity == RosterOcrValidationSeverity.BLOCKING })
        assertEquals(RosterOcrValidationStatus.NEEDS_MANUAL_REVIEW, result.status)
    }

    @Test
    fun missingTournamentSlotsAreBlockingIssues() {
        val result = validate(listOf(candidate(1)))

        assertEquals(11, result.globalIssues.count {
            it.type == RosterOcrValidationIssueType.MISSING_TOURNAMENT_SLOT
        })
        assertEquals(RosterOcrValidationStatus.BLOCKED, result.status)
    }

    @Test
    fun associationConflictsAndInvalidMetadataAreBlockingIssues() {
        val duplicate = RosterSlotAssociationFailure(
            type = RosterSlotAssociationFailureType.DUPLICATE_TOURNAMENT_SLOT,
            tournamentSlotNumber = 1,
        )
        val invalidScreenshot = RosterSlotAssociationFailure(
            type = RosterSlotAssociationFailureType.INVALID_SCREENSHOT_POSITION_METADATA,
            screenshotPosition = RosterScreenshotPosition.ONE,
        )
        val invalidVisible = RosterSlotAssociationFailure(
            type = RosterSlotAssociationFailureType.INVALID_VISIBLE_SLOT_METADATA,
            visibleSlotPosition = RosterVisibleSlotPosition.TOP_LEFT,
        )

        val result = validate(emptyList(), listOf(duplicate, invalidScreenshot, invalidVisible))

        assertTrue(result.globalIssues.any { it.type == RosterOcrValidationIssueType.DUPLICATE_TOURNAMENT_SLOT })
        assertTrue(result.globalIssues.any { it.type == RosterOcrValidationIssueType.INVALID_SCREENSHOT_POSITION })
        assertTrue(result.globalIssues.any { it.type == RosterOcrValidationIssueType.INVALID_VISIBLE_SLOT_POSITION })
        assertEquals(RosterOcrValidationStatus.BLOCKED, result.status)
    }

    @Test
    fun missingEmptyAmbiguousDuplicateAndExtractionFailureRowsAreBlocking() {
        val players = playersFor(1).toMutableList().apply {
            this[0] = this[0].copy(status = RosterCandidateParseStatus.EMPTY, candidateText = null)
            this[1] = this[1].copy(status = RosterCandidateParseStatus.AMBIGUOUS, candidateText = null)
            this[2] = this[2].copy(status = RosterCandidateParseStatus.DUPLICATE, candidateText = null)
            this[3] = this[3].copy(status = RosterCandidateParseStatus.INPUT_FAILURE, candidateText = null)
        }
        val result = validate(listOf(candidate(1, playerNameCandidates = players)))

        val types = result.slotResults.single().issues.map { it.type }
        assertTrue(types.contains(RosterOcrValidationIssueType.EMPTY_PLAYER_ROW))
        assertTrue(types.contains(RosterOcrValidationIssueType.AMBIGUOUS_PLAYER_ROW))
        assertTrue(types.contains(RosterOcrValidationIssueType.DUPLICATE_PLAYER_ROW_CANDIDATE))
        assertTrue(types.contains(RosterOcrValidationIssueType.EXTRACTION_FAILURE))
        assertEquals(RosterOcrValidationStatus.BLOCKED, result.status)
    }

    @Test
    fun absentRequiredPlayerRowIsBlockingWhileFifthRowRemainsUnsupportedInfo() {
        val players = playersFor(1).drop(1) + playersFor(1).last().copy(playerRowIndex = 5)
        val result = validate(listOf(candidate(1, playerNameCandidates = players)))

        val issues = result.slotResults.single().issues
        assertTrue(issues.any { it.type == RosterOcrValidationIssueType.MISSING_PLAYER_ROW && it.playerRowIndex == 1 })
        assertTrue(issues.any {
            it.type == RosterOcrValidationIssueType.UNSUPPORTED_EXTRA_PLAYER_ROW &&
                it.severity == RosterOcrValidationSeverity.INFO
        })
    }

    @Test
    fun teamNameUnavailableAndConfidenceUnavailableAreInformationalWithoutInference() {
        val result = validate(listOf(candidate(1)))
        val issues = result.slotResults.single().issues

        assertTrue(issues.any {
            it.type == RosterOcrValidationIssueType.TEAM_NAME_UNAVAILABLE &&
                it.severity == RosterOcrValidationSeverity.INFO
        })
        assertTrue(issues.any {
            it.type == RosterOcrValidationIssueType.CONFIDENCE_UNAVAILABLE &&
                it.severity == RosterOcrValidationSeverity.INFO
        })
        assertEquals(null, result.slotResults.single().tournamentSlotCandidate.teamNameCandidate.rawSourceResults.singleOrNull())
    }

    @Test
    fun duplicateTrimmedPlayerNamesAreWarningsAndOriginalEvidenceIsPreserved() {
        val first = candidate(1, playerNameCandidates = playersFor(1, firstName = "  Synthetic^Name  "))
        val second = candidate(2, playerNameCandidates = playersFor(2, firstName = "Synthetic^Name"))

        val result = validate(listOf(first, second))
        val issue = result.globalIssues.single {
            it.type == RosterOcrValidationIssueType.DUPLICATE_PLAYER_NAME_CANDIDATE
        }

        assertEquals(RosterOcrValidationSeverity.WARNING, issue.severity)
        assertEquals("  Synthetic^Name  ", first.playerNameCandidates.first().candidateText)
        assertEquals(2, issue.sourcePlayerCandidates.size)
    }

    @Test
    fun parserAndAssociationInputFailuresAreGlobalBlockingIssues() {
        val parserFailure = RosterSlotAssociationFailure(
            type = RosterSlotAssociationFailureType.PARSER_INPUT_FAILURE,
            parserFailure = RosterCandidateParseFailure.RAW_EXTRACTION_FAILURE,
        )
        val associationFailure = RosterSlotAssociationFailure(
            type = RosterSlotAssociationFailureType.MISSING_SCREENSHOT_POSITION,
            screenshotPosition = RosterScreenshotPosition.THREE,
        )
        val result = validate(emptyList(), listOf(parserFailure, associationFailure))

        assertTrue(result.globalIssues.any { it.type == RosterOcrValidationIssueType.PARSER_INPUT_FAILURE })
        assertTrue(result.globalIssues.any { it.type == RosterOcrValidationIssueType.ASSOCIATION_INPUT_FAILURE })
        assertEquals(RosterOcrValidationStatus.BLOCKED, result.status)
    }

    @Test
    fun preservesCandidateAndRawEvidenceReferencesForPhaseNineReview() {
        val source = candidate(12)
        val result = validate(listOf(source))
        val slotResult = result.slotResults.single()

        assertSame(source, slotResult.tournamentSlotCandidate)
        val confidenceIssue = slotResult.issues.first {
            it.type == RosterOcrValidationIssueType.CONFIDENCE_UNAVAILABLE
        }
        assertSame(source.playerNameCandidates.first().rawSourceResults, confidenceIssue.rawSourceResults)
    }

    private fun validate(
        candidates: List<RosterTournamentSlotCandidate>,
        failures: List<RosterSlotAssociationFailure> = emptyList(),
    ): RosterOcrValidationResult = validator.validate(
        RosterOcrValidationInput(RosterSlotAssociationResult(candidates, failures)),
    )

    private fun candidate(
        tournamentSlotNumber: Int,
        playerNameCandidates: List<RosterPlayerNameCandidate> = playersFor(tournamentSlotNumber),
    ): RosterTournamentSlotCandidate {
        val screenshotPosition = screenshotPositionFor(tournamentSlotNumber)
        val visibleSlotPosition = visibleSlotPositionFor(tournamentSlotNumber)
        return RosterTournamentSlotCandidate(
            tournamentSlotNumber = tournamentSlotNumber,
            sourceScreenshotPosition = screenshotPosition,
            sourceVisibleSlotPosition = visibleSlotPosition,
            teamNameCandidate = RosterTeamNameCandidate(
                status = RosterCandidateParseStatus.UNSUPPORTED,
                failure = RosterCandidateParseFailure.UNSUPPORTED_TEAM_NAME_REGION,
                rawSourceResults = emptyList(),
                confidence = RawOcrConfidence.Unavailable,
            ),
            playerNameCandidates = playerNameCandidates,
            associationStatus = RosterSlotAssociationStatus.ASSOCIATED,
        )
    }

    private fun playersFor(
        tournamentSlotNumber: Int,
        firstName: String = "Synthetic Player 1",
    ): List<RosterPlayerNameCandidate> {
        val screenshotPosition = screenshotPositionFor(tournamentSlotNumber)
        val visibleSlotPosition = visibleSlotPositionFor(tournamentSlotNumber)
        return (1..4).map { rowIndex ->
            val identity = RosterRawOcrRegionIdentity(
                screenshotPosition = screenshotPosition,
                visibleSlotPosition = visibleSlotPosition,
                regionType = RosterRawOcrRegionType.PLAYER_ROW,
                playerRowIndex = rowIndex,
            )
            RosterPlayerNameCandidate(
                regionIdentity = identity,
                playerRowIndex = rowIndex,
                status = RosterCandidateParseStatus.PARSED,
                candidateText = if (rowIndex == 1) firstName else "Synthetic Player $tournamentSlotNumber-$rowIndex",
                failure = null,
                rawSourceResults = listOf(RosterRawOcrExtractionResult.Empty(identity)),
                confidence = RawOcrConfidence.Unavailable,
            )
        }
    }

    private fun screenshotPositionFor(tournamentSlotNumber: Int): RosterScreenshotPosition = when {
        tournamentSlotNumber in 1..4 -> RosterScreenshotPosition.ONE
        tournamentSlotNumber in 5..8 -> RosterScreenshotPosition.TWO
        else -> RosterScreenshotPosition.THREE
    }

    private fun visibleSlotPositionFor(tournamentSlotNumber: Int): RosterVisibleSlotPosition =
        RosterVisibleSlotPosition.entries[(tournamentSlotNumber - 1) % 4]
}
