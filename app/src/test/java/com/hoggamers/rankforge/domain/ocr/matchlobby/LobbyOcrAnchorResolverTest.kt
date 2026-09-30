package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyOcrAnchorResolverTest {
    private val resolver = LobbyOcrAnchorResolver()
    private val dimensions = OcrImageDimensions(1600, 720)

    @Test
    fun filtersObservationsToTheExpectedScreenshotGroup() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("1", 100, 100),
                observation("5", 300, 100),
                observation("9", 500, 100),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(1), anchors.map { it.anchor.slotNumber })
    }

    @Test
    fun acceptsOnlyStrictExactWholeNumberText() {
        val anchors = resolver.resolve(
            screenshotIndex = 2,
            observations = listOf(
                observation("6", 100, 100),
                observation("6.", 200, 100),
                observation("I", 300, 100),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(6), anchors.map { it.anchor.slotNumber })
    }

    @Test
    fun deduplicatesBlockLineElementRepresentationsAndPrefersElement() {
        val box = RawOcrBoundingBox(575, 226, 595, 246)
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                LobbyOcrAnchorObservation(
                    text = "1",
                    boundingBox = box,
                    level = LobbyOcrAnchorLevel.BLOCK,
                    blockIndex = 0,
                ),
                LobbyOcrAnchorObservation(
                    text = "1",
                    boundingBox = box,
                    level = LobbyOcrAnchorLevel.LINE,
                    blockIndex = 0,
                    lineIndex = 0,
                    parentBoundingBox = box,
                ),
                LobbyOcrAnchorObservation(
                    text = "1",
                    boundingBox = box,
                    level = LobbyOcrAnchorLevel.ELEMENT,
                    blockIndex = 0,
                    lineIndex = 0,
                    elementIndex = 0,
                    parentBoundingBox = box,
                ),
                observation("2", 1076, 236),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(1, 2), anchors.map { it.anchor.slotNumber })
        assertEquals(LobbyOcrAnchorLevel.ELEMENT, anchors.first().level)
    }

    @Test
    fun reportsDistinctPotentialSlotPositionsBeforeGeometryValidation() {
        val group = resolver.resolveAll(
            observations = listOf(
                observation("1", 300, 100),
                observation("4", 900, 300),
            ),
            imageDimensions = dimensions,
        ).first()

        assertEquals(2, group.potentialAnchorCount)
        assertTrue(group.anchors.isEmpty())
    }

    @Test
    fun duplicateHierarchyRepresentationsDoNotIncreasePotentialSlotCount() {
        val box = RawOcrBoundingBox(575, 226, 595, 246)
        val group = resolver.resolveAll(
            observations = listOf(
                LobbyOcrAnchorObservation(
                    text = "1",
                    boundingBox = box,
                    level = LobbyOcrAnchorLevel.BLOCK,
                    blockIndex = 0,
                ),
                LobbyOcrAnchorObservation(
                    text = "1",
                    boundingBox = box,
                    level = LobbyOcrAnchorLevel.LINE,
                    blockIndex = 0,
                    lineIndex = 0,
                    parentBoundingBox = box,
                ),
                LobbyOcrAnchorObservation(
                    text = "1",
                    boundingBox = box,
                    level = LobbyOcrAnchorLevel.ELEMENT,
                    blockIndex = 0,
                    lineIndex = 0,
                    elementIndex = 0,
                    parentBoundingBox = box,
                ),
            ),
            imageDimensions = dimensions,
        ).first()

        assertEquals(1, group.potentialAnchorCount)
    }

    @Test
    fun allSixUniqueTwoAnchorRelationshipsAreAccepted() {
        val cases = listOf(
            listOf(observation("1", 585, 236), observation("2", 1076, 236)),
            listOf(observation("3", 585, 441), observation("4", 1076, 441)),
            listOf(observation("1", 585, 236), observation("3", 585, 441)),
            listOf(observation("2", 1076, 236), observation("4", 1076, 441)),
            listOf(observation("1", 585, 236), observation("4", 1076, 441)),
            listOf(observation("2", 1076, 236), observation("3", 585, 441)),
        )
        val expectedSlots = listOf(
            listOf(1, 2),
            listOf(3, 4),
            listOf(1, 3),
            listOf(2, 4),
            listOf(1, 4),
            listOf(2, 3),
        )

        cases.zip(expectedSlots).forEach { (observations, expected) ->
            val resolved = resolver.resolve(1, observations, dimensions)
            assertEquals(expected, resolved.map { it.anchor.slotNumber })
        }
    }

    @Test
    fun horizontalTwoAnchorPairsRequireMinimumSeparationAndRowAlignment() {
        val cases = listOf(
            Triple("1", "2", listOf(1, 2)),
            Triple("3", "4", listOf(3, 4)),
        )

        cases.forEach { (leftSlot, rightSlot, expected) ->
            val accepted = resolver.resolve(
                screenshotIndex = 1,
                observations = listOf(
                    observation(leftSlot, 300, 200),
                    observation(rightSlot, 620, 210),
                ),
                imageDimensions = dimensions,
            )
            assertEquals(expected, accepted.map { it.anchor.slotNumber })

            val belowMinimum = resolver.resolve(
                screenshotIndex = 1,
                observations = listOf(
                    observation(leftSlot, 300, 200),
                    observation(rightSlot, 619, 200),
                ),
                imageDimensions = dimensions,
            )
            assertTrue(belowMinimum.isEmpty())

            val aboveAlignmentTolerance = resolver.resolve(
                screenshotIndex = 1,
                observations = listOf(
                    observation(leftSlot, 300, 200),
                    observation(rightSlot, 620, 211),
                ),
                imageDimensions = dimensions,
            )
            assertTrue(aboveAlignmentTolerance.isEmpty())

            val reversed = resolver.resolve(
                screenshotIndex = 1,
                observations = listOf(
                    observation(leftSlot, 620, 200),
                    observation(rightSlot, 300, 200),
                ),
                imageDimensions = dimensions,
            )
            assertEquals(1, reversed.size)
        }
    }

    @Test
    fun verticalTwoAnchorPairsRequireMinimumSeparationAndColumnAlignment() {
        val cases = listOf(
            Triple("1", "3", listOf(1, 3)),
            Triple("2", "4", listOf(2, 4)),
        )

        cases.forEach { (topSlot, bottomSlot, expected) ->
            val accepted = resolver.resolve(
                screenshotIndex = 1,
                observations = listOf(
                    observation(topSlot, 500, 200),
                    observation(bottomSlot, 510, 344),
                ),
                imageDimensions = dimensions,
            )
            assertEquals(expected, accepted.map { it.anchor.slotNumber })

            val belowMinimum = resolver.resolve(
                screenshotIndex = 1,
                observations = listOf(
                    observation(topSlot, 500, 200),
                    observation(bottomSlot, 500, 343),
                ),
                imageDimensions = dimensions,
            )
            assertTrue(belowMinimum.isEmpty())

            val aboveAlignmentTolerance = resolver.resolve(
                screenshotIndex = 1,
                observations = listOf(
                    observation(topSlot, 500, 200),
                    observation(bottomSlot, 511, 344),
                ),
                imageDimensions = dimensions,
            )
            assertTrue(aboveAlignmentTolerance.isEmpty())

            val reversed = resolver.resolve(
                screenshotIndex = 1,
                observations = listOf(
                    observation(topSlot, 500, 344),
                    observation(bottomSlot, 500, 200),
                ),
                imageDimensions = dimensions,
            )
            assertEquals(1, reversed.size)
        }
    }

    @Test
    fun diagonalTwoAnchorPairsRequireOrientationMinimumsAndRatioBounds() {
        val acceptedPlusBoundary = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("1", 300, 100),
                observation("4", 852, 300),
            ),
            imageDimensions = dimensions,
        )
        assertEquals(listOf(1, 4), acceptedPlusBoundary.map { it.anchor.slotNumber })

        val acceptedMinusBoundary = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("1", 300, 100),
                observation("4", 709, 300),
            ),
            imageDimensions = dimensions,
        )
        assertEquals(listOf(1, 4), acceptedMinusBoundary.map { it.anchor.slotNumber })

        val justOutsidePlusBoundary = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("1", 300, 100),
                observation("4", 853, 300),
            ),
            imageDimensions = dimensions,
        )
        assertTrue(justOutsidePlusBoundary.isEmpty())

        val justOutsideMinusBoundary = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("1", 300, 100),
                observation("4", 708, 300),
            ),
            imageDimensions = dimensions,
        )
        assertTrue(justOutsideMinusBoundary.isEmpty())

        val reversed = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("1", 852, 300),
                observation("4", 300, 100),
            ),
            imageDimensions = dimensions,
        )
        assertEquals(listOf(1), reversed.map { it.anchor.slotNumber })

        val belowHorizontalMinimum = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("1", 300, 100),
                observation("4", 690, 262),
            ),
            imageDimensions = OcrImageDimensions(2_000, 720),
        )
        assertTrue(belowHorizontalMinimum.isEmpty())

        val belowVerticalMinimum = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("1", 300, 100),
                observation("4", 778, 299),
            ),
            imageDimensions = OcrImageDimensions(1_600, 1_000),
        )
        assertTrue(belowVerticalMinimum.isEmpty())
    }

    @Test
    fun diagonalRatioValidationAppliesToTheOtherDiagonalRelationship() {
        val accepted = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("2", 852, 100),
                observation("3", 300, 300),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(2, 3), accepted.map { it.anchor.slotNumber })
    }

    @Test
    fun nearbyTwoAnchorPairIn1080By485ImageIsRejected() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("1", RawOcrBoundingBox(335, 9, 355, 28)),
                observation("4", RawOcrBoundingBox(409, 59, 430, 78)),
            ),
            imageDimensions = OcrImageDimensions(1080, 485),
        )

        assertTrue(anchors.isEmpty())
    }

    @Test
    fun sameTwoAnchorRulesApplyToAllThreeScreenshotGroups() {
        val observations = listOf(
            observation("5", 585, 236),
            observation("6", 1076, 236),
            observation("9", 585, 236),
            observation("12", 1076, 441),
        )

        assertEquals(
            listOf(5, 6),
            resolver.resolve(2, observations, dimensions).map { it.anchor.slotNumber },
        )
        assertEquals(
            listOf(9, 12),
            resolver.resolve(3, observations, dimensions).map { it.anchor.slotNumber },
        )
    }

    @Test
    fun ambiguousSameRowPhysicalPairsAreRejectedInsteadOfTieBroken() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("1", 585, 236),
                observation("1", 700, 236),
                observation("2", 1076, 236),
            ),
            imageDimensions = dimensions,
        )

        assertTrue(anchors.isEmpty())
    }

    @Test
    fun sameRowWithMultipleCandidatesAcceptsOnlyTheSingleGeometricallyValidPair() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("1", 585, 236),
                observation("1", 1200, 236),
                observation("2", 1076, 236),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(1, 2), anchors.map { it.anchor.slotNumber })
        assertEquals(585.0, anchors.first().anchor.centerX, 0.0)
    }

    @Test
    fun ambiguousSameColumnPhysicalPairsAreRejectedInsteadOfTieBroken() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("2", 1076, 236),
                observation("4", 1076, 441),
                observation("4", 1076, 500),
            ),
            imageDimensions = dimensions,
        )

        assertTrue(anchors.isEmpty())
    }

    @Test
    fun sameColumnWithMultipleCandidatesAcceptsOnlyTheSingleGeometricallyValidPair() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("2", 1076, 236),
                observation("4", 1076, 441),
                observation("4", 1076, 100),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(2, 4), anchors.map { it.anchor.slotNumber })
        assertEquals(441.0, anchors.last().anchor.centerY, 0.0)
    }

    @Test
    fun ambiguousDiagonalPhysicalPairsAreRejectedInsteadOfTieBroken() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("1", 585, 236),
                observation("1", 700, 285),
                observation("4", 1076, 441),
            ),
            imageDimensions = dimensions,
        )

        assertTrue(anchors.isEmpty())
    }

    @Test
    fun diagonalWithMultipleCandidatesAcceptsOnlyTheSingleGeometricallyValidPair() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("1", 585, 236),
                observation("1", 1200, 500),
                observation("4", 1076, 441),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(1, 4), anchors.map { it.anchor.slotNumber })
        assertEquals(585.0, anchors.first().anchor.centerX, 0.0)
        assertEquals(236.0, anchors.first().anchor.centerY, 0.0)
    }

    @Test
    fun twoAnchorPairIsRejectedWhenRatioAssistedGridWouldLeaveImageBounds() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("1", 100, 700),
                observation("2", 1500, 700),
            ),
            imageDimensions = dimensions,
        )

        assertTrue(anchors.isEmpty())
    }

    @Test
    fun structuralEvidenceStillRejectsFalseNumberPositionWhenThreeOrFourAnchorsExist() {
        val anchors = resolver.resolve(
            screenshotIndex = 2,
            observations = listOf(
                observation("5", 585, 245),
                observation("7", 585, 451),
                observation("8", 1076, 451),
                observation("6", 1400, 100),
                observation("6", 1076, 245),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(5, 6, 7, 8), anchors.map { it.anchor.slotNumber })
        assertEquals(1076.0, anchors[1].anchor.centerX, 0.0)
        assertEquals(245.0, anchors[1].anchor.centerY, 0.0)
    }

    @Test
    fun threeAndFourAnchorEvidenceRemainPreferredAndFullyResolved() {
        val shotTwo = resolver.resolve(
            screenshotIndex = 2,
            observations = listOf(
                observation("5", 585, 245),
                observation("7", 585, 451),
                observation("8", 1076, 451),
            ),
            imageDimensions = dimensions,
        )
        val shotThree = resolver.resolve(
            screenshotIndex = 3,
            observations = listOf(
                observation("9", 585, 250),
                observation("10", 1075, 250),
                observation("11", 584, 455),
                observation("12", 1074, 456),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(5, 7, 8), shotTwo.map { it.anchor.slotNumber })
        assertEquals(listOf(9, 10, 11, 12), shotThree.map { it.anchor.slotNumber })
    }

    @Test
    fun cleanFourAnchorGeometryPasses() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = fourAnchorObservations(),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(1, 2, 3, 4), anchors.map { it.anchor.slotNumber })
    }

    @Test
    fun moderateFourAnchorGeometryVariationPasses() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = fourAnchorObservations(
                topRight = 600 to 108,
                bottomLeft = 305 to 200,
                bottomRight = 595 to 203,
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(1, 2, 3, 4), anchors.map { it.anchor.slotNumber })
    }

    @Test
    fun excessiveTopRowMisalignmentRejectsAllStrongAssignments() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = fourAnchorObservations(
                topRight = 600 to 130,
                bottomRight = 600 to 211,
            ),
            imageDimensions = dimensions,
        )

        assertTrue(anchors.isEmpty())
    }

    @Test
    fun excessiveBottomRowMisalignmentRejectsAllStrongAssignments() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = fourAnchorObservations(
                topRight = 600 to 112,
                bottomRight = 600 to 230,
            ),
            imageDimensions = dimensions,
        )

        assertTrue(anchors.isEmpty())
    }

    @Test
    fun excessiveLeftColumnMisalignmentRejectsAllStrongAssignments() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = fourAnchorObservations(
                bottomLeft = 340 to 200,
                bottomRight = 650 to 200,
            ),
            imageDimensions = dimensions,
        )

        assertTrue(anchors.isEmpty())
    }

    @Test
    fun excessiveRightColumnMisalignmentRejectsAllStrongAssignments() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = fourAnchorObservations(
                bottomLeft = 350 to 200,
                bottomRight = 640 to 200,
            ),
            imageDimensions = dimensions,
        )

        assertTrue(anchors.isEmpty())
    }

    @Test
    fun exactlyTenPercentStrongEvidenceAlignmentPasses() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = fourAnchorObservations(
                topRight = 600 to 110,
                bottomRight = 600 to 210,
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(1, 2, 3, 4), anchors.map { it.anchor.slotNumber })
    }

    @Test
    fun justOverTenPercentStrongEvidenceAlignmentFails() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = fourAnchorObservations(
                topRight = 600 to 111,
                bottomRight = 600 to 211,
            ),
            imageDimensions = dimensions,
        )

        assertTrue(anchors.isEmpty())
    }

    @Test
    fun eachThreeAnchorMissingCornerCasePasses() {
        val cases = listOf(
            listOf(observation("2", 600, 100), observation("3", 300, 200), observation("4", 600, 200)),
            listOf(observation("1", 300, 100), observation("3", 300, 200), observation("4", 600, 200)),
            listOf(observation("1", 300, 100), observation("2", 600, 100), observation("4", 600, 200)),
            listOf(observation("1", 300, 100), observation("2", 600, 100), observation("3", 300, 200)),
        )
        val expectedSlots = listOf(
            listOf(2, 3, 4),
            listOf(1, 3, 4),
            listOf(1, 2, 4),
            listOf(1, 2, 3),
        )

        cases.zip(expectedSlots).forEach { (observations, expected) ->
            val anchors = resolver.resolve(1, observations, dimensions)
            assertEquals(expected, anchors.map { it.anchor.slotNumber })
        }
    }

    @Test
    fun moderateThreeAnchorGeometryVariationPasses() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("2", 600, 106),
                observation("3", 305, 200),
                observation("4", 600, 203),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(2, 3, 4), anchors.map { it.anchor.slotNumber })
    }

    @Test
    fun excessiveThreeAnchorRowErrorFails() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("2", 600, 100),
                observation("3", 300, 250),
                observation("4", 600, 200),
            ),
            imageDimensions = dimensions,
        )

        assertTrue(anchors.isEmpty())
    }

    @Test
    fun excessiveThreeAnchorColumnErrorFails() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("2", 600, 100),
                observation("3", 300, 200),
                observation("4", 800, 200),
            ),
            imageDimensions = dimensions,
        )

        assertTrue(anchors.isEmpty())
    }

    @Test
    fun inferredThreeAnchorPointOutsideSourceImageFails() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("2", 600, -5),
                observation("3", 300, 200),
                observation("4", 600, 200),
            ),
            imageDimensions = dimensions,
        )

        assertTrue(anchors.isEmpty())
    }

    @Test
    fun invalidFourAnchorAssignmentRecoversThroughValidThreeAnchorSubset() {
        val anchors = resolver.resolve(
            screenshotIndex = 2,
            observations = listOf(
                observation("5", 400, 100),
                observation("6", 1076, 245),
                observation("7", 585, 451),
                observation("8", 1076, 451),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(6, 7, 8), anchors.map { it.anchor.slotNumber })
    }

    @Test
    fun validFourAnchorAssignmentStillHasPriorityOverThreeAnchorSubset() {
        val anchors = resolver.resolve(
            screenshotIndex = 2,
            observations = listOf(
                observation("5", 585, 245),
                observation("6", 1076, 245),
                observation("7", 585, 451),
                observation("8", 1076, 451),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(5, 6, 7, 8), anchors.map { it.anchor.slotNumber })
    }

    @Test
    fun knownGoodScreenshotOneToFourThreeAnchorGeometryPasses() {
        val anchors = resolver.resolve(
            screenshotIndex = 1,
            observations = listOf(
                observation("2", RawOcrBoundingBox(716, 149, 736, 169)),
                observation("3", RawOcrBoundingBox(400, 288, 420, 307)),
                observation("4", RawOcrBoundingBox(715, 288, 736, 307)),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(2, 3, 4), anchors.map { it.anchor.slotNumber })
    }

    @Test
    fun knownGoodScreenshotNineToTwelveThreeAnchorGeometryPasses() {
        val anchors = resolver.resolve(
            screenshotIndex = 3,
            observations = listOf(
                observation("10", RawOcrBoundingBox(715, 158, 735, 179)),
                observation("11", RawOcrBoundingBox(399, 297, 419, 316)),
                observation("12", RawOcrBoundingBox(715, 297, 735, 316)),
            ),
            imageDimensions = dimensions,
        )

        assertEquals(listOf(10, 11, 12), anchors.map { it.anchor.slotNumber })
    }

    @Test
    fun inputOrderingDoesNotChangeStrongEvidenceResolution() {
        val observations = listOf(
            observation("5", 585, 245),
            observation("6", 1076, 245),
            observation("7", 585, 451),
            observation("8", 1076, 451),
        )

        val first = resolver.resolve(2, observations, dimensions)
        val second = resolver.resolve(2, observations.reversed(), dimensions)

        assertEquals(first, second)
    }

    @Test
    fun invalidScreenshotIndexProducesNoResolvedAnchors() {
        assertTrue(resolver.resolve(0, emptyList(), dimensions).isEmpty())
        assertTrue(resolver.resolve(4, emptyList(), dimensions).isEmpty())
    }

    private fun observation(
        text: String,
        centerX: Int,
        centerY: Int,
    ) = LobbyOcrAnchorObservation(
        text = text,
        boundingBox = RawOcrBoundingBox(
            centerX - 10,
            centerY - 10,
            centerX + 10,
            centerY + 10,
        ),
        level = LobbyOcrAnchorLevel.ELEMENT,
    )

    private fun observation(
        text: String,
        boundingBox: RawOcrBoundingBox,
    ) = LobbyOcrAnchorObservation(
        text = text,
        boundingBox = boundingBox,
        level = LobbyOcrAnchorLevel.ELEMENT,
    )

    private fun fourAnchorObservations(
        topLeft: Pair<Int, Int> = 300 to 100,
        topRight: Pair<Int, Int> = 600 to 100,
        bottomLeft: Pair<Int, Int> = 300 to 200,
        bottomRight: Pair<Int, Int> = 600 to 200,
    ) = listOf(
        observation("1", topLeft.first, topLeft.second),
        observation("2", topRight.first, topRight.second),
        observation("3", bottomLeft.first, bottomLeft.second),
        observation("4", bottomRight.first, bottomRight.second),
    )
}
