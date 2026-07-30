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

class RosterSlotAssociatorTest {
    private val associator = FixedRosterSlotAssociator()

    @Test
    fun mapsScreenshotPositionsToTheirApprovedTournamentSlotRanges() {
        val result = associate(
            sourceCandidate(RosterScreenshotPosition.ONE, RosterVisibleSlotPosition.TOP_LEFT),
            sourceCandidate(RosterScreenshotPosition.TWO, RosterVisibleSlotPosition.TOP_LEFT),
            sourceCandidate(RosterScreenshotPosition.THREE, RosterVisibleSlotPosition.TOP_LEFT),
        )

        assertEquals(
            listOf(1, 5, 9),
            result.tournamentSlotCandidates.map { it.tournamentSlotNumber },
        )
    }

    @Test
    fun mapsVisiblePositionsToTheirApprovedOffsets() {
        val result = associate(
            RosterVisibleSlotPosition.entries.map { position ->
                sourceCandidate(RosterScreenshotPosition.ONE, position)
            },
        )

        assertEquals((1..4).toList(), result.tournamentSlotCandidates.map { it.tournamentSlotNumber })
    }

    @Test
    fun mapsAllSourcePositionsToSlotsOneThroughTwelveInDeterministicOrder() {
        val sourceCandidates = RosterScreenshotPosition.entries
            .flatMap { screenshotPosition ->
                RosterVisibleSlotPosition.entries.map { visibleSlotPosition ->
                    sourceCandidate(screenshotPosition, visibleSlotPosition)
                }
            }
            .reversed()

        val result = associate(sourceCandidates)

        assertEquals((1..12).toList(), result.tournamentSlotCandidates.map { it.tournamentSlotNumber })
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun incompleteInputReturnsPartialCandidatesAndTypedMissingPositionFailures() {
        val result = associate(
            sourceCandidate(RosterScreenshotPosition.ONE, RosterVisibleSlotPosition.TOP_LEFT),
        )

        assertEquals(listOf(1), result.tournamentSlotCandidates.map { it.tournamentSlotNumber })
        assertTrue(
            result.failures.any {
                it.type == RosterSlotAssociationFailureType.MISSING_SCREENSHOT_POSITION &&
                    it.screenshotPosition == RosterScreenshotPosition.TWO
            },
        )
        assertTrue(
            result.failures.any {
                it.type == RosterSlotAssociationFailureType.MISSING_VISIBLE_SLOT &&
                    it.screenshotPosition == RosterScreenshotPosition.ONE &&
                    it.visibleSlotPosition == RosterVisibleSlotPosition.TOP_RIGHT
            },
        )
    }

    @Test
    fun inconsistentScreenshotAndVisibleSlotMetadataFailSafely() {
        val invalidScreenshotMetadata = sourceCandidate(
            RosterScreenshotPosition.ONE,
            RosterVisibleSlotPosition.TOP_LEFT,
        ).copy(intendedTournamentSlotRange = 5..8)
        val invalidVisibleMetadata = sourceCandidate(
            RosterScreenshotPosition.TWO,
            RosterVisibleSlotPosition.TOP_LEFT,
        ).copy(intendedTournamentSlot = 8)

        val result = associate(invalidScreenshotMetadata, invalidVisibleMetadata)

        assertTrue(result.tournamentSlotCandidates.isEmpty())
        assertTrue(
            result.failures.any {
                it.type == RosterSlotAssociationFailureType.INVALID_SCREENSHOT_POSITION_METADATA
            },
        )
        assertTrue(
            result.failures.any {
                it.type == RosterSlotAssociationFailureType.INVALID_VISIBLE_SLOT_METADATA
            },
        )
    }

    @Test
    fun duplicateTournamentSlotCandidatesAreNotOverwritten() {
        val first = sourceCandidate(RosterScreenshotPosition.ONE, RosterVisibleSlotPosition.TOP_LEFT)
        val duplicate = first.copy()

        val result = associate(first, duplicate)

        assertTrue(result.tournamentSlotCandidates.isEmpty())
        val failure = result.failures.single {
            it.type == RosterSlotAssociationFailureType.DUPLICATE_TOURNAMENT_SLOT
        }
        assertEquals(1, failure.tournamentSlotNumber)
        assertEquals(listOf(first, duplicate), failure.sourceCandidates)
    }

    @Test
    fun preservesTeamPlayerParseStatusAndRawEvidenceReferences() {
        val source = sourceCandidate(
            RosterScreenshotPosition.THREE,
            RosterVisibleSlotPosition.BOTTOM_RIGHT,
        )

        val result = associate(source)
        val associated = result.tournamentSlotCandidates.single()

        assertEquals(12, associated.tournamentSlotNumber)
        assertSame(source.teamNameCandidate, associated.teamNameCandidate)
        assertSame(source.playerNameCandidates, associated.playerNameCandidates)
        assertEquals(RosterCandidateParseStatus.UNSUPPORTED, associated.teamNameCandidate.status)
        assertEquals((1..4).toList(), associated.playerNameCandidates.map { it.playerRowIndex })
        assertSame(
            source.playerNameCandidates.first().rawSourceResults,
            associated.playerNameCandidates.first().rawSourceResults,
        )
    }

    @Test
    fun associationDoesNotValidatePlayerCountsOrDuplicateNames() {
        val source = sourceCandidate(
            RosterScreenshotPosition.ONE,
            RosterVisibleSlotPosition.TOP_LEFT,
        ).copy(
            playerNameCandidates = sourcePlayers().take(2).map { player ->
                player.copy(candidateText = "Synthetic Duplicate")
            },
        )

        val result = associate(source)

        assertEquals(listOf(1), result.tournamentSlotCandidates.map { it.tournamentSlotNumber })
        assertEquals(2, result.tournamentSlotCandidates.single().playerNameCandidates.size)
    }

    @Test
    fun unsupportedFifthOrSixthPlayerRowsFailWithoutAssociation() {
        val source = sourceCandidate(
            RosterScreenshotPosition.ONE,
            RosterVisibleSlotPosition.TOP_LEFT,
        ).copy(
            playerNameCandidates = sourcePlayers().let { players ->
                players + players.last().copy(playerRowIndex = 5)
            },
        )

        val result = associate(source)

        assertTrue(result.tournamentSlotCandidates.isEmpty())
        assertTrue(
            result.failures.any { it.type == RosterSlotAssociationFailureType.UNSUPPORTED_PLAYER_ROW },
        )
    }

    @Test
    fun parserInputFailuresArePreservedAsAssociationFailures() {
        val result = associator.associate(
            RosterSlotAssociationInput(
                RosterCandidateParseResult(
                    slots = emptyList(),
                    inputFailures = listOf(RosterCandidateParseFailure.RAW_EXTRACTION_FAILURE),
                ),
            ),
        )

        assertTrue(
            result.failures.any {
                it.type == RosterSlotAssociationFailureType.PARSER_INPUT_FAILURE &&
                    it.parserFailure == RosterCandidateParseFailure.RAW_EXTRACTION_FAILURE
            },
        )
    }

    private fun associate(vararg candidates: RosterSlotCandidate): RosterSlotAssociationResult =
        associate(candidates.toList())

    private fun associate(candidates: List<RosterSlotCandidate>): RosterSlotAssociationResult =
        associator.associate(
            RosterSlotAssociationInput(
                RosterCandidateParseResult(candidates, emptyList()),
            ),
        )

    private fun sourceCandidate(
        screenshotPosition: RosterScreenshotPosition,
        visibleSlotPosition: RosterVisibleSlotPosition,
    ): RosterSlotCandidate = RosterSlotCandidate(
        screenshotPosition = screenshotPosition,
        visibleSlotPosition = visibleSlotPosition,
        intendedTournamentSlotRange = screenshotPosition.tournamentSlotRange,
        intendedTournamentSlot = screenshotPosition.tournamentSlotFor(visibleSlotPosition),
        teamNameCandidate = RosterTeamNameCandidate(
            status = RosterCandidateParseStatus.UNSUPPORTED,
            failure = RosterCandidateParseFailure.UNSUPPORTED_TEAM_NAME_REGION,
            rawSourceResults = emptyList(),
            confidence = RawOcrConfidence.Unavailable,
        ),
        playerNameCandidates = sourcePlayers(screenshotPosition, visibleSlotPosition),
    )

    private fun sourcePlayers(
        screenshotPosition: RosterScreenshotPosition = RosterScreenshotPosition.ONE,
        visibleSlotPosition: RosterVisibleSlotPosition = RosterVisibleSlotPosition.TOP_LEFT,
    ): List<RosterPlayerNameCandidate> = (1..4).map { rowIndex ->
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
            candidateText = "Synthetic Player $rowIndex",
            failure = null,
            rawSourceResults = listOf(RosterRawOcrExtractionResult.Empty(identity)),
            confidence = RawOcrConfidence.Unavailable,
        )
    }
}
