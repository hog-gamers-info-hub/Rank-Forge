package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotGridRole
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
    fun missingPlayerNameDoesNotMakeTeamUnavailable() {
        val result = mapped(
            fragmentsFor(RosterScreenshotPosition.ONE) + fragment("player-one", 110, 70),
        )
        val team = result.teams.single { it.crop.detectedSlotNumber == 1 }

        assertEquals(4, team.rowPreviews.size)
        assertEquals("player-one", team.rowPreviews.first().playerName)
        assertEquals(null, team.rowPreviews[1].playerName)
    }

    @Test
    fun fullyContainedPlayerFragmentRemainsMapped() {
        val result = mapped(completePanelFragments() + fragmentWithBounds(
            text = "fully-contained",
            left = 100,
            top = 100,
            right = 200,
            bottom = 120,
        ))

        assertTrue(
            result.teams.single { it.crop.detectedSlotNumber == 1 }
                .rowPreviews.any { it.playerName?.contains("fully-contained") == true },
        )
    }

    @Test
    fun mostlyContainedPlayerFragmentRemainsMapped() {
        val result = mapped(completePanelFragments() + fragmentWithBounds(
            text = "mostly-contained",
            left = 280,
            top = 250,
            right = 700,
            bottom = 290,
        ))

        assertTrue(
            result.teams.single { it.crop.detectedSlotNumber == 1 }
                .rowPreviews.any { it.playerName?.contains("mostly-contained") == true },
        )
        assertTrue(
            result.teams
                .filterNot { it.crop.detectedSlotNumber == 1 }
                .none { team -> team.rowPreviews.any { it.playerName?.contains("mostly-contained") == true } },
        )
    }

    @Test
    fun exactlyHalfContainedPlayerFragmentRemainsMapped() {
        val result = mapped(completePanelFragments() + fragmentWithBounds(
            text = "exactly-half-contained",
            left = 300,
            top = 250,
            right = 740,
            bottom = 290,
        ))

        assertTrue(
            result.teams.single { it.crop.detectedSlotNumber == 1 }
                .rowPreviews.any { it.playerName?.contains("exactly-half-contained") == true },
        )
    }

    @Test
    fun lessThanHalfContainedFragmentIsRejectedWithoutPlayerContamination() {
        val result = mapped(completePanelFragments() + fragmentWithBounds(
            text = "outside-contamination",
            left = 300,
            top = 0,
            right = 740,
            bottom = 440,
        ))

        assertTrue(
            result.teams.none { team ->
                team.rowPreviews.any { row ->
                    row.structuralEvidence?.contains("outside-contamination") == true ||
                        row.playerName?.contains("outside-contamination") == true
                }
            },
        )
    }

    @Test
    fun boundaryCrossingFragmentIsNotDuplicatedIntoNeighboringTeams() {
        val result = mapped(completePanelFragments() + fragmentWithBounds(
            text = "cross-team-contamination",
            left = 100,
            top = 0,
            right = 1300,
            bottom = 440,
        ))

        assertTrue(
            result.teams.none { team ->
                team.rowPreviews.any { row -> row.playerName?.contains("cross-team-contamination") == true }
            },
        )
    }

    @Test
    fun zeroOrInvalidAreaFragmentIsRejectedSafely() {
        val malformedFragments = listOf(
            fragmentWithBounds("zero-width", 100, 100, 100, 120),
            fragmentWithBounds("negative-width", 200, 100, 100, 120),
            fragmentWithBounds("zero-height", 100, 100, 200, 100),
        )
        val result = mapped(completePanelFragments() + malformedFragments)

        assertTrue(
            result.teams.none { team ->
                team.rowPreviews.any { row ->
                    malformedFragments.any { malformed -> row.playerName?.contains(malformed.text) == true }
                }
            },
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
    fun bottomLeftRow4SelectsClosestLineForSlotsThreeSevenAndEleven() {
        listOf(
            RosterScreenshotPosition.ONE to 3,
            RosterScreenshotPosition.TWO to 7,
            RosterScreenshotPosition.THREE to 11,
        ).forEach { (position, slotNumber) ->
            val result = mapped(
                bottomLeftFragmentsWithSeparateRow4Lines(
                    position = position,
                    footerText = "SRESTATOR UET(3/9)",
                ),
            )
            val team = result.teams.single { it.crop.detectedSlotNumber == slotNumber }

            assertEquals(LobbySlotGridRole.BOTTOM_LEFT, LobbySlotGridRole.fromSlotNumber(slotNumber))
            assertEquals(
                "player-4-leftplayer-4-right",
                team.rowPreviews.single { it.row == LobbyPlayerRow.ROW_4 }.playerName,
            )
        }
    }

    @Test
    fun bottomLeftPhysicalRow4LineSelectionRejectsFooterForSlotsThreeSevenAndEleven() {
        listOf(
            RosterScreenshotPosition.ONE to 3,
            RosterScreenshotPosition.TWO to 7,
            RosterScreenshotPosition.THREE to 11,
        ).forEach { (position, slotNumber) ->
            val result = mapped(
                physicalBottomLeftFragmentsFor(position),
                panelHeight = PHYSICAL_PANEL_HEIGHT,
            )
            val player4 = result.teams.single { it.crop.detectedSlotNumber == slotNumber }
                .rowPreviews.single { it.row == LobbyPlayerRow.ROW_4 }

            assertEquals("FE.PHANTOM", player4.playerName)
        }
    }

    @Test
    fun bottomLeftRow4FilterUsesOnlyGeometryForArbitraryFooterText() {
        listOf("XYZ", "28", "SPECT...").forEach { footerText ->
            val result = mapped(bottomLeftFragmentsFor(RosterScreenshotPosition.ONE, footerText))
            val row4 = result.teams.single { it.crop.detectedSlotNumber == 3 }
                .rowPreviews.single { it.row == LobbyPlayerRow.ROW_4 }

            assertEquals("player-4", row4.playerName)
        }
    }

    @Test
    fun bottomLeftRow4AcceptsGenuinePlayerWithSmallVerticalDeviation() {
        val result = mapped(
            bottomLeftFragmentsFor(
                position = RosterScreenshotPosition.ONE,
                footerText = "footer",
                player4CenterY = 790,
                footerCenterY = 815,
            ),
        )

        assertEquals(
            "player-4",
            result.teams.single { it.crop.detectedSlotNumber == 3 }
                .rowPreviews.single { it.row == LobbyPlayerRow.ROW_4 }
                .playerName,
        )
    }

    @Test
    fun bottomLeftRow4SingleClusterRemainsAccepted() {
        val result = mapped(
            fragmentsFor(RosterScreenshotPosition.ONE) + buildList {
                (1..3).forEach { player ->
                    add(fragment("player-$player", 110, 470 + (player - 1) * 100))
                }
                add(fragment("player-4", 110, 790))
            },
        )

        assertEquals(
            "player-4",
            result.teams.single { it.crop.detectedSlotNumber == 3 }
                .rowPreviews.single { it.row == LobbyPlayerRow.ROW_4 }
                .playerName,
        )
    }

    @Test
    fun nonBottomLeftRolesKeepTheirExistingRow4Mapping() {
        val result = mapped(
            completePanelFragments() + listOf(
                fragment("top-left-footer", 110, 410),
                fragment("top-right-footer", 610, 410),
                fragment("bottom-right-footer", 610, 810),
            ),
        )

        assertTrue(
            result.teams.single { it.crop.detectedSlotNumber == 1 }
                .rowPreviews.single { it.row == LobbyPlayerRow.ROW_4 }
                .playerName
                ?.contains("top-left-footer") == true,
        )
        assertTrue(
            result.teams.single { it.crop.detectedSlotNumber == 2 }
                .rowPreviews.single { it.row == LobbyPlayerRow.ROW_4 }
                .playerName
                ?.contains("top-right-footer") == true,
        )
        assertTrue(
            result.teams.single { it.crop.detectedSlotNumber == 4 }
                .rowPreviews.single { it.row == LobbyPlayerRow.ROW_4 }
                .playerName
                ?.contains("bottom-right-footer") == true,
        )
    }

    @Test
    fun bottomLeftRow4FilterSkipsWhenRowsOneThroughThreeEvidenceIsInsufficient() {
        val result = mapped(
            fragmentsFor(RosterScreenshotPosition.ONE) + listOf(
                fragment("only-player-1", 110, 470),
                fragment("footer", 110, 810),
            ),
        )

        assertEquals(
            "footer",
            result.teams.single { it.crop.detectedSlotNumber == 3 }
                .rowPreviews.single { it.row == LobbyPlayerRow.ROW_4 }
                .playerName,
        )
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

    private fun fragmentsFor(
        position: RosterScreenshotPosition,
        topAnchorCenterY: Int = 220,
        bottomAnchorCenterY: Int = 620,
    ): List<LobbyPanelPpFragment> = buildList {
        val numbers = position.tournamentSlotRange.toList()
        add(fragment(numbers[0].toString(), 60, topAnchorCenterY, confidence = 0.91f))
        add(fragment(numbers[1].toString(), 560, topAnchorCenterY, confidence = 0.82f))
        add(fragment(numbers[2].toString(), 60, bottomAnchorCenterY, confidence = 0.83f))
        add(fragment(numbers[3].toString(), 560, bottomAnchorCenterY, confidence = 0.84f))
    }

    private fun bottomLeftFragmentsFor(
        position: RosterScreenshotPosition,
        footerText: String,
        player4CenterY: Int = 770,
        footerCenterY: Int = 810,
    ): List<LobbyPanelPpFragment> = fragmentsFor(position) + buildList {
        (1..3).forEach { player ->
            add(fragment("player-$player", 110, 470 + (player - 1) * 100))
        }
        add(fragment("player-4", 110, player4CenterY))
        add(fragment(footerText, 110, footerCenterY))
    }

    private fun bottomLeftFragmentsWithSeparateRow4Lines(
        position: RosterScreenshotPosition,
        footerText: String,
        player4CenterY: Int = 770,
        footerCenterY: Int = 810,
    ): List<LobbyPanelPpFragment> = fragmentsFor(position) + buildList {
        (1..3).forEach { player ->
            add(fragment("player-$player", 110, 470 + (player - 1) * 100))
        }
        add(fragment("player-4-left", 110, player4CenterY))
        add(fragment("player-4-right", 210, player4CenterY))
        add(fragment("$footerText-left", 110, footerCenterY))
        add(fragment("$footerText-right", 210, footerCenterY))
    }

    private fun physicalBottomLeftFragmentsFor(
        position: RosterScreenshotPosition,
    ): List<LobbyPanelPpFragment> = fragmentsFor(
        position = position,
        topAnchorCenterY = PHYSICAL_TOP_ANCHOR_Y,
        bottomAnchorCenterY = PHYSICAL_BOTTOM_ANCHOR_Y,
    ) + buildList {
        add(physicalFragment("player-1", 110, 317, 337))
        add(physicalFragment("player-2", 110, 368, 387))
        add(physicalFragment("player-3", 110, 418, 438))
        add(physicalFragment("FE.PHANT", 110, 467, 487))
        add(physicalFragment("OM", 210, 468, 486))
        add(physicalFragment("SRESTATOR UET", 110, 479, 499))
        add(physicalFragment("(3/9)", 210, 479, 499))
    }

    private fun mapped(
        fragments: List<LobbyPanelPpFragment>,
        panelWidth: Int = PANEL_WIDTH,
        panelHeight: Int = PANEL_HEIGHT,
    ): LobbyPanelPpMappingResult.Available =
        (LobbyPanelPpMapper.map(panelWidth, panelHeight, fragments)
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

    private fun physicalFragment(
        text: String,
        centerX: Int,
        top: Int,
        bottom: Int,
    ) = LobbyPanelPpFragment(
        text = text,
        confidence = 0.75f,
        boundingBox = RawOcrBoundingBox(centerX - 20, top, centerX + 20, bottom),
        readingOrderIndex = nextIndex++,
    )

    private fun fragmentWithBounds(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = LobbyPanelPpFragment(
        text = text,
        confidence = 0.75f,
        boundingBox = RawOcrBoundingBox(left, top, right, bottom),
        readingOrderIndex = nextIndex++,
    )

    private companion object {
        const val PANEL_WIDTH = 1_000
        const val PANEL_HEIGHT = 900
        const val PHYSICAL_PANEL_HEIGHT = 505
        const val PHYSICAL_TOP_ANCHOR_Y = 202
        const val PHYSICAL_BOTTOM_ANCHOR_Y = 404
        var nextIndex = 0
    }
}
