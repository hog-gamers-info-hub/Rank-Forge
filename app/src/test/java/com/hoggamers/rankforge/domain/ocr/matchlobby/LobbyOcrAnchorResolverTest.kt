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
                observation("4", 1000, 441),
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
                observation("1", 700, 300),
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
}
