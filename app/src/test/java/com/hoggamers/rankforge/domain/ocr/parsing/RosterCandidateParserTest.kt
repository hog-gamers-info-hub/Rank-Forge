package com.hoggamers.rankforge.domain.ocr.parsing

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrEvidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionEvidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionIdentity
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionType
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterCandidateParserTest {
    private val parser = FixedLayoutRosterCandidateParser()

    @Test
    fun parsesFourPlayerRowCandidatesInVisibleRowOrder() {
        val result = parse(
            playerRow(1, "Player One"),
            playerRow(2, "Player Two"),
            playerRow(3, "Player Three"),
            playerRow(4, "Player Four"),
        )

        val players = result.slots.single().playerNameCandidates
        assertEquals((1..4).toList(), players.map { it.playerRowIndex })
        assertEquals(
            listOf("Player One", "Player Two", "Player Three", "Player Four"),
            players.map { it.candidateText },
        )
        assertTrue(players.all { it.status == RosterCandidateParseStatus.PARSED })
    }

    @Test
    fun trimsSurroundingWhitespaceWhilePreservingCasePunctuationSymbolsAndTags() {
        val result = parse(playerRow(1, "  ^TAG-Player_One!  "))

        assertEquals("^TAG-Player_One!", result.slots.single().playerNameCandidates.first().candidateText)
    }

    @Test
    fun missingAndEmptyRowsRemainInTheirOriginalPositions() {
        val result = parse(
            playerRow(2, "  "),
            playerRow(3, "Player Three"),
        )

        val players = result.slots.single().playerNameCandidates
        assertEquals((1..4).toList(), players.map { it.playerRowIndex })
        assertEquals(RosterCandidateParseStatus.MISSING, players[0].status)
        assertEquals(RosterCandidateParseStatus.EMPTY, players[1].status)
        assertEquals(RosterCandidateParseStatus.PARSED, players[2].status)
        assertEquals(RosterCandidateParseStatus.MISSING, players[3].status)
    }

    @Test
    fun multipleDistinctFragmentsInOneRowRemainAmbiguousWithoutGuessing() {
        val result = parse(playerRow(1, "Player Alpha"), playerRow(1, "Player Beta"))

        val player = result.slots.single().playerNameCandidates.first()
        assertEquals(RosterCandidateParseStatus.AMBIGUOUS, player.status)
        assertEquals(RosterCandidateParseFailure.MULTIPLE_FRAGMENTS, player.failure)
        assertNull(player.candidateText)
    }

    @Test
    fun repeatedRawTextInOneRowRemainsTypedAsDuplicate() {
        val result = parse(playerRow(1, "Player Alpha"), playerRow(1, "Player Alpha"))

        val player = result.slots.single().playerNameCandidates.first()
        assertEquals(RosterCandidateParseStatus.DUPLICATE, player.status)
        assertEquals(RosterCandidateParseFailure.DUPLICATE_TEXT, player.failure)
        assertNull(player.candidateText)
    }

    @Test
    fun teamNameIsUnsupportedAndDoesNotUseSlotContentText() {
        val slotContent = extracted(
            identity(regionType = RosterRawOcrRegionType.SLOT_CONTENT),
            "^PREFIX PlayerName",
        )
        val result = parse(slotContent, playerRow(1, "^PREFIX PlayerName"))

        val team = result.slots.single().teamNameCandidate
        assertEquals(RosterCandidateParseStatus.UNSUPPORTED, team.status)
        assertEquals(RosterCandidateParseFailure.UNSUPPORTED_TEAM_NAME_REGION, team.failure)
        assertEquals(listOf(slotContent), team.rawSourceResults)
    }

    @Test
    fun onlyFourEvidencedPlayerRowsAreRepresented() {
        val result = parse((1..4).map { row -> playerRow(row, "Player $row") })

        assertEquals((1..4).toList(), result.slots.single().playerNameCandidates.map { it.playerRowIndex })
        assertTrue(result.slots.single().playerNameCandidates.none { it.playerRowIndex in 5..6 })
    }

    @Test
    fun preservesSlotMetadataSourceEvidenceAndUnavailableConfidence() {
        val source = playerRow(
            row = 1,
            text = "Player One",
            screenshotPosition = RosterScreenshotPosition.TWO,
            visibleSlotPosition = RosterVisibleSlotPosition.BOTTOM_RIGHT,
        )
        val result = parse(source)

        val slot = result.slots.single()
        val player = slot.playerNameCandidates.first()
        assertEquals(RosterScreenshotPosition.TWO, slot.screenshotPosition)
        assertEquals(RosterVisibleSlotPosition.BOTTOM_RIGHT, slot.visibleSlotPosition)
        assertEquals(5..8, slot.intendedTournamentSlotRange)
        assertEquals(8, slot.intendedTournamentSlot)
        assertEquals(1, player.regionIdentity.playerRowIndex)
        assertEquals(listOf(source), player.rawSourceResults)
        assertEquals(RawOcrConfidence.Unavailable, player.confidence)
    }

    @Test
    fun preservesAnAvailableRawConfidenceOnlyWhenAllRawEvidenceAgrees() {
        val source = playerRow(
            row = 1,
            text = "Player One",
            confidence = RawOcrConfidence.Available(0.75f),
        )

        val result = parse(source)

        assertEquals(
            RawOcrConfidence.Available(0.75f),
            result.slots.single().playerNameCandidates.first().confidence,
        )
    }

    @Test
    fun rawExtractionFailureForAPlayerRowBecomesTypedInputFailure() {
        val result = parser.parse(
            RosterCandidateParseInput(
                listOf(
                    RosterRawOcrExtractionResult.Failed(
                        failure = com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrFailure.RECOGNIZER_FAILED,
                        regionIdentity = identity(playerRowIndex = 1),
                    ),
                ),
            ),
        )

        val player = result.slots.single().playerNameCandidates.first()
        assertEquals(RosterCandidateParseStatus.INPUT_FAILURE, player.status)
        assertEquals(RosterCandidateParseFailure.RAW_EXTRACTION_FAILURE, player.failure)
    }

    @Test
    fun absentRawMetadataIsReportedAsAnInputFailureWithoutCreatingSlots() {
        val result = parser.parse(RosterCandidateParseInput(emptyList()))

        assertTrue(result.slots.isEmpty())
        assertEquals(listOf(RosterCandidateParseFailure.MISSING_ROSTER_METADATA), result.inputFailures)
    }

    private fun parse(vararg results: RosterRawOcrExtractionResult): RosterCandidateParseResult =
        parser.parse(RosterCandidateParseInput(results.toList()))

    private fun parse(results: List<RosterRawOcrExtractionResult>): RosterCandidateParseResult =
        parser.parse(RosterCandidateParseInput(results))

    private fun playerRow(
        row: Int,
        text: String,
        screenshotPosition: RosterScreenshotPosition = RosterScreenshotPosition.ONE,
        visibleSlotPosition: RosterVisibleSlotPosition = RosterVisibleSlotPosition.TOP_LEFT,
        confidence: RawOcrConfidence = RawOcrConfidence.Unavailable,
    ): RosterRawOcrExtractionResult.Extracted = extracted(
        identity(screenshotPosition, visibleSlotPosition, RosterRawOcrRegionType.PLAYER_ROW, row),
        text,
        confidence,
    )

    private fun identity(
        screenshotPosition: RosterScreenshotPosition = RosterScreenshotPosition.ONE,
        visibleSlotPosition: RosterVisibleSlotPosition = RosterVisibleSlotPosition.TOP_LEFT,
        regionType: RosterRawOcrRegionType = RosterRawOcrRegionType.PLAYER_ROW,
        playerRowIndex: Int? = null,
    ): RosterRawOcrRegionIdentity = RosterRawOcrRegionIdentity(
        screenshotPosition = screenshotPosition,
        visibleSlotPosition = visibleSlotPosition,
        regionType = regionType,
        playerRowIndex = if (regionType == RosterRawOcrRegionType.PLAYER_ROW) {
            playerRowIndex ?: 1
        } else {
            null
        },
    )

    private fun extracted(
        identity: RosterRawOcrRegionIdentity,
        text: String,
        confidence: RawOcrConfidence = RawOcrConfidence.Unavailable,
    ): RosterRawOcrExtractionResult.Extracted = RosterRawOcrExtractionResult.Extracted(
        RosterRawOcrRegionEvidence(
            regionIdentity = identity,
            rawText = text,
            blocks = emptyList(),
            rawEvidence = listOf(
                RosterRawOcrEvidence(
                    text = text,
                    geometry = null,
                    recognizedLanguage = null,
                    confidence = confidence,
                ),
            ),
        ),
    )
}
