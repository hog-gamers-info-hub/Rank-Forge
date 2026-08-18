package com.hoggamers.rankforge.domain.ocr.validation

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrSymbol
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrCanonicalLayout
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrCanonicalLayouts
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultCropContentEvidenceEvaluatorTest {
    private val evaluator = MatchResultCropContentEvidenceEvaluator()

    @Test
    fun upperRoleExposesExpectedPlacementAndFieldStructureWithoutClassifyingIt() {
        val evidence = evaluator.evaluate(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            cropWidth = 1156,
            cropHeight = 456,
            blocks = emptyList(),
        )

        assertEquals((1..10).toList(), evidence.placementEvidence.map { it.expectedPlacement })
        assertEquals(40, evidence.playerFieldEvidence.size)
        assertEquals(40, evidence.killFieldEvidence.size)
        assertEquals(0, evidence.nonBlankObservationCount)
        assertTrue(evidence.placementEvidence.all { it.matchingCandidateCount == 0 })
        assertTrue(evidence.placementEvidence.all { it.minimumNormalizedCenterDistance == null })
    }

    @Test
    fun lowerRoleExposesOnlyElevenAndTwelveAsExpectedPlacements() {
        val evidence = evaluator.evaluate(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            cropWidth = 1156,
            cropHeight = 452,
            blocks = emptyList(),
        )

        assertEquals(listOf(11, 12), evidence.placementEvidence.map { it.expectedPlacement })
        assertEquals(8, evidence.playerFieldEvidence.size)
        assertEquals(8, evidence.killFieldEvidence.size)
    }

    @Test
    fun upperRealGeometryExampleProducesSmallDistancesForObservedPlacementAnchors() {
        val cropWidth = 1169
        val cropHeight = 468
        val observedPlacements = listOf(4, 5, 7, 8, 9, 10)
        val elements = observedPlacements.map { placement ->
            val canonical = MatchResultOcrCanonicalLayouts.upper.placementRect(placement)
            element(
                text = placement.toString(),
                rect = canonical.transform(
                    xScale = 0.9967317363,
                    xOffset = 7.620779053,
                    yScale = 1.0157799243,
                    yOffset = 7.241712896,
                ),
            )
        }

        val evidence = evaluator.evaluate(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            cropWidth = cropWidth,
            cropHeight = cropHeight,
            blocks = block(elements),
        )

        observedPlacements.forEach { placement ->
            val placementEvidence = evidence.placementEvidence.single { it.expectedPlacement == placement }
            assertEquals(1, placementEvidence.matchingCandidateCount)
            assertNotNull(placementEvidence.minimumNormalizedCenterDistance)
            assertTrue(placementEvidence.minimumNormalizedCenterDistance!! < 0.02)
        }
        assertTrue(evidence.spatialDistribution.horizontalBandCounts[0] > 0)
        assertTrue(evidence.spatialDistribution.horizontalBandCounts[2] > 0)
    }

    @Test
    fun lowerRealGeometryExampleMeasuresElevenAndTwelveNearExpectedRows() {
        val evidence = evaluator.evaluate(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            cropWidth = 1174,
            cropHeight = 476,
            blocks = block(
                listOf(
                    centeredElement("11", centerX = 692.5, centerY = 347.0),
                    centeredElement("12", centerX = 693.5, centerY = 427.5),
                ),
            ),
        )

        val eleven = evidence.placementEvidence.single { it.expectedPlacement == 11 }
        val twelve = evidence.placementEvidence.single { it.expectedPlacement == 12 }
        assertEquals(1, eleven.matchingCandidateCount)
        assertEquals(1, twelve.matchingCandidateCount)
        assertTrue(eleven.minimumNormalizedCenterDistance!! < 0.02)
        assertTrue(twelve.minimumNormalizedCenterDistance!! < 0.02)
    }

    @Test
    fun exactPlayerAndKillRegionObservationsExposeFullOverlapMetrics() {
        val layout = MatchResultOcrCanonicalLayouts.upper
        val playerRect = layout.rect("PLAYER_4_1")
        val killRect = layout.rect("KILL_4_1")
        val evidence = evaluator.evaluate(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            cropWidth = layout.width.toInt(),
            cropHeight = layout.height.toInt(),
            blocks = block(
                listOf(
                    element("ALPHA", playerRect),
                    element("3", killRect),
                ),
            ),
        )

        val player = evidence.playerFieldEvidence.single { it.fieldId == "PLAYER_4_1" }
        val kill = evidence.killFieldEvidence.single { it.fieldId == "KILL_4_1" }
        assertEquals(1.0, player.maximumExpectedRegionCoverageRatio, 0.0)
        assertEquals(1.0, player.maximumObservationContainmentRatio, 0.0)
        assertEquals(0.0, player.minimumNormalizedCenterDistance!!, 0.0)
        assertEquals(1.0, kill.maximumExpectedRegionCoverageRatio, 0.0)
        assertEquals(1.0, kill.maximumObservationContainmentRatio, 0.0)
        assertEquals(0.0, kill.minimumNormalizedCenterDistance!!, 0.0)
    }

    @Test
    fun displacedTextRemainsVisibleAsWeakGeometryInsteadOfBeingDeclaredInvalid() {
        val layout = MatchResultOcrCanonicalLayouts.upper
        val evidence = evaluator.evaluate(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            cropWidth = layout.width.toInt(),
            cropHeight = layout.height.toInt(),
            blocks = block(
                listOf(
                    element("ALPHA", MatchResultOcrRect(900.0, 400.0, 1040.0, 430.0)),
                ),
            ),
        )

        val target = evidence.playerFieldEvidence.single { it.fieldId == "PLAYER_1_1" }
        assertEquals(0.0, target.maximumExpectedRegionCoverageRatio, 0.0)
        assertEquals(0.0, target.maximumObservationContainmentRatio, 0.0)
        assertNotNull(target.minimumNormalizedCenterDistance)
        assertTrue(target.minimumNormalizedCenterDistance!! > 0.5)
    }

    @Test
    fun upperPlacementsDoNotBecomeLowerPlacementEvidence() {
        val layout = MatchResultOcrCanonicalLayouts.upper
        val evidence = evaluator.evaluate(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            cropWidth = 1156,
            cropHeight = 452,
            blocks = block(
                listOf(
                    element("4", layout.placementRect(4)),
                    element("5", layout.placementRect(5)),
                ),
            ),
        )

        assertTrue(evidence.placementEvidence.all { it.matchingCandidateCount == 0 })
        assertTrue(evidence.placementEvidence.all { it.minimumNormalizedCenterDistance == null })
    }

    @Test
    fun elementGeometryFallsBackToSymbolGeometryWhenNeeded() {
        val layout = MatchResultOcrCanonicalLayouts.upper
        val playerRect = layout.rect("PLAYER_4_1")
        val evidence = evaluator.evaluate(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            cropWidth = layout.width.toInt(),
            cropHeight = layout.height.toInt(),
            blocks = block(
                listOf(
                    RawOcrElement(
                        text = "ALPHA",
                        geometry = null,
                        recognizedLanguage = null,
                        confidence = RawOcrConfidence.Unavailable,
                        symbols = listOf(symbol("A", playerRect)),
                    ),
                ),
            ),
        )

        val player = evidence.playerFieldEvidence.single { it.fieldId == "PLAYER_4_1" }
        assertEquals(1, evidence.nonBlankObservationCount)
        assertEquals(1.0, player.maximumExpectedRegionCoverageRatio, 0.0)
    }

    @Test
    fun cornerPointGeometryIsMeasuredAndOutOfCropGeometryIsIgnored() {
        val layout = MatchResultOcrCanonicalLayouts.upper
        val playerRect = layout.rect("PLAYER_4_1")
        val cornerElement = elementWithCorners("ALPHA", playerRect)
        val outsideElement = element("OUTSIDE", MatchResultOcrRect(-100.0, -100.0, -10.0, -10.0))

        val evidence = evaluator.evaluate(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            cropWidth = layout.width.toInt(),
            cropHeight = layout.height.toInt(),
            blocks = block(listOf(cornerElement, outsideElement)),
        )

        assertEquals(1, evidence.nonBlankObservationCount)
        val player = evidence.playerFieldEvidence.single { it.fieldId == "PLAYER_4_1" }
        assertEquals(1.0, player.maximumObservationContainmentRatio, 0.0)
    }

    @Test
    fun repeatedInputProducesDeterministicEvidence() {
        val layout = MatchResultOcrCanonicalLayouts.lower
        val input = block(
            listOf(
                element("11", layout.rect("LOWER_ROW_A_PLACEMENT")),
                element("ALPHA", layout.rect("LOWER_ROW_A_PLAYER_1")),
                element("2", layout.rect("LOWER_ROW_A_KILL_1")),
            ),
        )

        val first = evaluator.evaluate(
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            layout.width.toInt(),
            layout.height.toInt(),
            input,
        )
        val second = evaluator.evaluate(
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            layout.width.toInt(),
            layout.height.toInt(),
            input,
        )

        assertEquals(first, second)
        assertNull(first.placementEvidence.single { it.expectedPlacement == 12 }.minimumNormalizedCenterDistance)
    }

    private fun block(elements: List<RawOcrElement>): List<RawOcrBlock> = listOf(
        RawOcrBlock(
            text = elements.joinToString(" ") { it.text },
            geometry = null,
            recognizedLanguage = null,
            confidence = RawOcrConfidence.Unavailable,
            lines = listOf(
                RawOcrLine(
                    text = elements.joinToString(" ") { it.text },
                    geometry = null,
                    recognizedLanguage = null,
                    confidence = RawOcrConfidence.Unavailable,
                    elements = elements,
                ),
            ),
        ),
    )

    private fun element(text: String, rect: MatchResultOcrRect): RawOcrElement = RawOcrElement(
        text = text,
        geometry = rect.geometry(),
        recognizedLanguage = null,
        confidence = RawOcrConfidence.Unavailable,
    )

    private fun centeredElement(text: String, centerX: Double, centerY: Double): RawOcrElement = element(
        text = text,
        rect = MatchResultOcrRect(
            left = centerX - 4.0,
            top = centerY - 8.0,
            right = centerX + 4.0,
            bottom = centerY + 8.0,
        ),
    )

    private fun elementWithCorners(text: String, rect: MatchResultOcrRect): RawOcrElement = RawOcrElement(
        text = text,
        geometry = RawOcrGeometry(
            boundingBox = null,
            cornerPoints = listOf(
                RawOcrPoint(rect.left.toInt(), rect.top.toInt()),
                RawOcrPoint(rect.right.toInt(), rect.top.toInt()),
                RawOcrPoint(rect.right.toInt(), rect.bottom.toInt()),
                RawOcrPoint(rect.left.toInt(), rect.bottom.toInt()),
            ),
        ),
        recognizedLanguage = null,
        confidence = RawOcrConfidence.Unavailable,
    )

    private fun symbol(text: String, rect: MatchResultOcrRect): RawOcrSymbol = RawOcrSymbol(
        text = text,
        geometry = rect.geometry(),
        recognizedLanguage = null,
        confidence = RawOcrConfidence.Unavailable,
    )

    private fun MatchResultOcrRect.geometry(): RawOcrGeometry = RawOcrGeometry(
        boundingBox = RawOcrBoundingBox(
            left = left.toInt(),
            top = top.toInt(),
            right = right.toInt(),
            bottom = bottom.toInt(),
        ),
        cornerPoints = null,
    )

    private fun MatchResultOcrCanonicalLayout.rect(id: String): MatchResultOcrRect =
        fields.single { it.id == id }.rect

    private fun MatchResultOcrCanonicalLayout.placementRect(position: Int): MatchResultOcrRect =
        fields.single { it.position == position && it.id == "PLACEMENT_$position" }.rect

    private fun MatchResultOcrRect.transform(
        xScale: Double,
        xOffset: Double,
        yScale: Double,
        yOffset: Double,
    ): MatchResultOcrRect = MatchResultOcrRect(
        left = xScale * left + xOffset,
        top = yScale * top + yOffset,
        right = xScale * right + xOffset,
        bottom = yScale * bottom + yOffset,
    )
}
