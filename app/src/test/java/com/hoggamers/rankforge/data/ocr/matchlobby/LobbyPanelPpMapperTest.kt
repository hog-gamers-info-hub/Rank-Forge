package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyPanelPpMapperTest {
    @Test
    fun contentDrivenMappingResolvesEachSemanticSlotRange() {
        RosterScreenshotPosition.entries.forEach { expectedPosition ->
            val result = LobbyPanelPpMapper.map(
                panelWidth = PANEL_WIDTH,
                panelHeight = PANEL_HEIGHT,
                fragments = fragmentsFor(expectedPosition),
            ) as LobbyPanelSemanticMappingResult.Available

            assertEquals(expectedPosition, result.screenshotPosition)
            assertEquals(
                expectedPosition.tournamentSlotRange.toList(),
                result.mapping.slots.map { it.candidate.detectedSlotNumber },
            )
        }
    }

    @Test
    fun contentDrivenMappingUsesTwoAnchorsWithExistingGridReconstruction() {
        val result = LobbyPanelPpMapper.map(
            panelWidth = PANEL_WIDTH,
            panelHeight = PANEL_HEIGHT,
            fragments = fragmentsFor(RosterScreenshotPosition.THREE)
                .filterNot { it.text == "11" || it.text == "12" },
        ) as LobbyPanelSemanticMappingResult.Available

        assertEquals(RosterScreenshotPosition.THREE, result.screenshotPosition)
        assertEquals(2, result.mapping.observedAnchorCount)
        assertEquals(listOf(9, 10, 11, 12), result.mapping.teams.map { it.crop.detectedSlotNumber })
    }

    @Test
    fun contentDrivenMappingRejectsFewerThanTwoValidAnchors() {
        val result = LobbyPanelPpMapper.map(
            panelWidth = PANEL_WIDTH,
            panelHeight = PANEL_HEIGHT,
            fragments = listOf(fragment("1", 60, 220)),
        ) as LobbyPanelSemanticMappingResult.Unavailable

        assertEquals(
            LobbyPanelSemanticMappingFailure.SEMANTIC_POSITION_UNRESOLVED,
            result.failure,
        )
    }

    @Test
    fun numericTextInPlayerNameRegionDoesNotBecomeSlotIdentityEvidence() {
        val result = LobbyPanelPpMapper.map(
            panelWidth = PANEL_WIDTH,
            panelHeight = PANEL_HEIGHT,
            fragments = listOf(
                fragment("1", 110, 220),
                fragment("2", 610, 220),
                fragment("3", 110, 620),
                fragment("4", 610, 620),
            ),
        ) as LobbyPanelSemanticMappingResult.Unavailable

        assertEquals(
            LobbyPanelSemanticMappingFailure.SEMANTIC_POSITION_UNRESOLVED,
            result.failure,
        )
    }

    @Test
    fun contentDrivenMappingFailsAmbiguousEvidenceWithoutGuessing() {
        val result = LobbyPanelPpMapper.map(
            panelWidth = PANEL_WIDTH,
            panelHeight = PANEL_HEIGHT,
            fragments = fragmentsFor(RosterScreenshotPosition.ONE) +
                fragmentsFor(RosterScreenshotPosition.TWO),
        ) as LobbyPanelSemanticMappingResult.Unavailable

        assertEquals(
            LobbyPanelSemanticMappingFailure.SEMANTIC_POSITION_CONFLICT,
            result.failure,
        )
    }

    @Test
    fun contentDrivenMappingFailsWhenNoSemanticRangeMatches() {
        val result = LobbyPanelPpMapper.map(
            panelWidth = PANEL_WIDTH,
            panelHeight = PANEL_HEIGHT,
            fragments = completePanelFragments().filter { it.text.toIntOrNull() == null },
        ) as LobbyPanelSemanticMappingResult.Unavailable

        assertEquals(
            LobbyPanelSemanticMappingFailure.SEMANTIC_POSITION_UNRESOLVED,
            result.failure,
        )
    }

    @Test
    fun mapsOneWholePanelIntoFourTeamsAndFourRows() {
        val result = mapped(completePanelFragments())

        assertEquals(listOf(1, 2, 3, 4), result.slots.map { it.candidate.detectedSlotNumber })
        assertEquals(RosterVisibleSlotPosition.entries, result.teams.map { it.crop.visibleSlotPosition })
        assertTrue(result.teams.all { it.rowPreviews.map { row -> row.row } == LobbyPlayerRow.entries })
        assertEquals("alpha-one", result.teams[0].rowPreviews[0].playerName)
        assertEquals(0.75f, result.teams[0].rowPreviews[0].playerNameConfidence)
        assertEquals("delta-four", result.teams[3].rowPreviews[3].playerName)
        assertEquals(
            RawOcrConfidence.Available(0.91f),
            result.slots.first().candidate.confidence,
        )
    }

    @Test
    fun twoObservedAnchorsReconstructTheRemainingTeams() {
        val result = mapped(
            completePanelFragments().filterNot { it.text == "3" || it.text == "4" },
        )

        assertEquals(listOf(1, 2, 3, 4), result.teams.map { it.crop.detectedSlotNumber })
        assertEquals(2, result.observedAnchorCount)
        assertEquals(null, result.slots[2].candidate.detectedSlotNumber)
        assertEquals(null, result.slots[3].candidate.detectedSlotNumber)
    }

    @Test
    fun fewerThanTwoSlotAnchorsLeavesPanelUnavailable() {
        val result = unmapped(
            completePanelFragments().filter { it.text == "1" || it.text == "alpha-one" },
        )

        assertEquals(MatchLobbyTeamCropPreviewUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE, result.reason)
    }

    @Test
    fun slotNumberAndLeftGutterFragmentsDoNotBecomePlayerNames() {
        val fragments = completePanelFragments() +
            fragment("not-a-player", 12, 170, confidence = 0.99f) +
            fragment("1", 60, 220, confidence = 0.92f)

        val result = mapped(fragments)

        assertEquals("alpha-one", result.teams[0].rowPreviews[0].playerName)
        assertTrue(result.teams[0].rowPreviews.none { it.playerName == "not-a-player" })
    }

    @Test
    fun duplicateSlotEvidenceUsesHighestConfidenceDeterministically() {
        val result = mapped(
            completePanelFragments() + fragment("1", 60, 220, confidence = 0.99f),
        )

        assertEquals(RawOcrConfidence.Available(0.99f), result.slots.first().candidate.confidence)
    }

    private fun completePanelFragments(): List<LobbyPanelPpFragment> = buildList {
        add(fragment("1", 60, 220, confidence = 0.91f))
        add(fragment("2", 560, 220, confidence = 0.82f))
        add(fragment("3", 60, 620, confidence = 0.83f))
        add(fragment("4", 560, 620, confidence = 0.84f))
        add(fragment("alpha-one", 110, 70))
        add(fragment("alpha-two", 110, 170))
        add(fragment("alpha-three", 110, 270))
        add(fragment("alpha-four", 110, 370))
        add(fragment("bravo-one", 610, 70))
        add(fragment("bravo-two", 610, 170))
        add(fragment("bravo-three", 610, 270))
        add(fragment("bravo-four", 610, 370))
        add(fragment("charlie-one", 110, 470))
        add(fragment("charlie-two", 110, 570))
        add(fragment("charlie-three", 110, 670))
        add(fragment("charlie-four", 110, 770))
        add(fragment("delta-one", 610, 470))
        add(fragment("delta-two", 610, 570))
        add(fragment("delta-three", 610, 670))
        add(fragment("delta-four", 610, 770))
    }

    private fun fragmentsFor(position: RosterScreenshotPosition): List<LobbyPanelPpFragment> = buildList {
        val numbers = position.tournamentSlotRange.toList()
        add(fragment(numbers[0].toString(), 60, 220, confidence = 0.91f))
        add(fragment(numbers[1].toString(), 560, 220, confidence = 0.82f))
        add(fragment(numbers[2].toString(), 60, 620, confidence = 0.83f))
        add(fragment(numbers[3].toString(), 560, 620, confidence = 0.84f))
    }

    private fun mapped(fragments: List<LobbyPanelPpFragment>): LobbyPanelPpMappingResult.Available =
        (LobbyPanelPpMapper.map(PANEL_WIDTH, PANEL_HEIGHT, fragments)
            as LobbyPanelSemanticMappingResult.Available).mapping

    private fun unmapped(fragments: List<LobbyPanelPpFragment>): LobbyPanelPpMappingResult.Unavailable =
        (LobbyPanelPpMapper.map(PANEL_WIDTH, PANEL_HEIGHT, fragments)
            as LobbyPanelSemanticMappingResult.Unavailable)
            .let { LobbyPanelPpMappingResult.Unavailable(it.reason, fragmentCount = it.fragmentCount) }

    private fun fragment(
        text: String,
        centerX: Int,
        centerY: Int,
        confidence: Float = 0.75f,
    ) = LobbyPanelPpFragment(
        text = text,
        confidence = confidence,
        boundingBox = RawOcrBoundingBox(centerX - 20, centerY - 10, centerX + 20, centerY + 10),
        readingOrderIndex = nextIndex++,
    )

    private companion object {
        const val PANEL_WIDTH = 1_000
        const val PANEL_HEIGHT = 900
        var nextIndex = 0
    }
}
