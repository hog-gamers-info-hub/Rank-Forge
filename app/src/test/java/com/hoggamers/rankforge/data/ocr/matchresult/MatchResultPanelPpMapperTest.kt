package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionColumn
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultPanelPpMapperTest {
    @Test
    fun lineCenteredInsideLeftCropIsAssignedToLeftPosition() {
        val result = map(crops = listOf(crop(1, 0, 0, 100, 100)))

        assertEquals(listOf("left"), result.single().blocks.single().lines.map { it.text })
    }

    @Test
    fun lineCenteredInsideRightCropIsNotAssignedToLeftPosition() {
        val result = MatchResultPanelPpMapper.map(
            panelBlocks = listOf(block(line("right", 120, 20, 160, 40))),
            crops = listOf(
                crop(1, 0, 0, 100, 100),
                crop(2, 100, 0, 200, 100, MatchResultPositionColumn.RIGHT),
            ),
        )

        assertTrue(result[0].blocks.isEmpty())
        assertEquals(listOf("right"), result[1].blocks.single().lines.map { it.text })
    }

    @Test
    fun centerInsideButWeakOverlapIsNotAssigned() {
        val result = MatchResultPanelPpMapper.map(
            panelBlocks = listOf(block(line("shot captured.", 0, 226, 166, 251))),
            crops = listOf(crop(12, 81, 161, 573, 242)),
        )

        assertTrue(result.single().blocks.isEmpty())
    }

    @Test
    fun weakOverlapIsRejectedWithoutTextSpecificFiltering() {
        val result = MatchResultPanelPpMapper.map(
            panelBlocks = listOf(block(line("arbitrary overlay", 0, 226, 166, 251))),
            crops = listOf(crop(12, 81, 161, 573, 242)),
        )

        assertTrue(result.single().blocks.isEmpty())
    }

    @Test
    fun fullyContainedPlayerAndEliminationEvidenceIsAssigned() {
        val result = MatchResultPanelPpMapper.map(
            panelBlocks = listOf(
                block(line("Player", 90, 170, 180, 190)),
                block(line("1Elimination", 200, 195, 320, 215)),
            ),
            crops = listOf(crop(12, 81, 161, 573, 242)),
        )

        assertEquals(
            listOf("Player", "1Elimination"),
            result.single().blocks.flatMap { it.lines }.map { it.text },
        )
    }

    @Test
    fun slightOverflowOnEachCropEdgeWithMajorityOverlapIsAssigned() {
        val cases = listOf(
            line("top", 110, 158, 140, 180),
            line("bottom", 110, 222, 140, 244),
            line("left", 78, 180, 110, 205),
            line("right", 544, 180, 576, 205),
        )

        cases.forEach { candidate ->
            val result = MatchResultPanelPpMapper.map(
                panelBlocks = listOf(block(candidate)),
                crops = listOf(crop(12, 81, 161, 573, 242)),
            )

            assertEquals(listOf(candidate.text), result.single().blocks.single().lines.map { it.text })
        }
    }

    @Test
    fun placementNearStructuralLeftEdgeIsAssigned() {
        val result = MatchResultPanelPpMapper.map(
            panelBlocks = listOf(block(line("12", 82, 170, 92, 190))),
            crops = listOf(crop(12, 81, 161, 573, 242)),
        )

        assertEquals(listOf("12"), result.single().blocks.single().lines.map { it.text })
    }

    @Test
    fun centerOutsideIsNotAssignedEvenWhenThereIsOverlap() {
        val result = MatchResultPanelPpMapper.map(
            panelBlocks = listOf(block(line("outside", 40, 180, 100, 200))),
            crops = listOf(crop(12, 81, 161, 573, 242)),
        )

        assertTrue(result.single().blocks.isEmpty())
    }

    @Test
    fun zeroAreaAndNoOverlapEvidenceAreHandledSafely() {
        val zeroWidth = line("zero-width", 100, 180, 100, 200)
        val zeroHeight = line("zero-height", 100, 180, 120, 180)
        val noOverlap = line("outside", 600, 180, 620, 200)

        listOf(zeroWidth, zeroHeight, noOverlap).forEach { candidate ->
            val result = MatchResultPanelPpMapper.map(
                panelBlocks = listOf(block(candidate)),
                crops = listOf(crop(12, 81, 161, 573, 242)),
            )

            assertTrue(result.single().blocks.isEmpty())
        }
    }

    @Test
    fun oneLineIsNotAssignedToMultipleDistantCrops() {
        val result = MatchResultPanelPpMapper.map(
            panelBlocks = listOf(block(line("single", 90, 180, 130, 200))),
            crops = listOf(
                crop(11, 81, 161, 200, 242),
                crop(12, 200, 161, 573, 242, MatchResultPositionColumn.RIGHT),
            ),
        )

        assertEquals(listOf("single"), result[0].blocks.single().lines.map { it.text })
        assertTrue(result[1].blocks.isEmpty())
    }

    @Test
    fun mergedPlayerEliminationEvidenceRemainsAssigned() {
        val result = MatchResultPanelPpMapper.map(
            panelBlocks = listOf(
                block(line("0Eliminations Slayersexy", 100, 180, 330, 205)),
                block(line("1EliminationYF", 340, 180, 470, 205)),
            ),
            crops = listOf(crop(12, 81, 161, 573, 242)),
        )

        assertEquals(
            listOf("0Eliminations Slayersexy", "1EliminationYF"),
            result.single().blocks.flatMap { it.lines }.map { it.text },
        )
    }

    @Test
    fun adjacentVerticalCropsDoNotDuplicateBoundaryNearLine() {
        val result = MatchResultPanelPpMapper.map(
            panelBlocks = listOf(block(line("boundary", 20, 90, 40, 110))),
            crops = listOf(crop(1, 0, 0, 100, 100), crop(2, 0, 100, 100, 200)),
        )

        assertTrue(result[0].blocks.isEmpty())
        assertEquals(listOf("boundary"), result[1].blocks.single().lines.map { it.text })
    }

    @Test
    fun panelGeometryIsTranslatedToCropLocalCoordinates() {
        val result = map(crops = listOf(crop(1, 50, 20, 150, 120)))
        val geometry = result.single().blocks.single().lines.single().geometry!!

        assertEquals(RawOcrBoundingBox(10, 15, 40, 35), geometry.boundingBox)
    }

    @Test
    fun translatedGeometryIsClampedToPositionDimensions() {
        val line = line("clamped", 95, 95, 155, 155).copy(
            geometry = RawOcrGeometry(
                boundingBox = RawOcrBoundingBox(95, 95, 155, 155),
                cornerPoints = listOf(RawOcrPoint(95, 95), RawOcrPoint(155, 155)),
            ),
        )
        val result = MatchResultPanelPpMapper.map(
            panelBlocks = listOf(block(line)),
            crops = listOf(crop(1, 100, 100, 150, 150)),
        )
        val geometry = result.single().blocks.single().lines.single().geometry!!

        assertEquals(RawOcrBoundingBox(0, 0, 50, 50), geometry.boundingBox)
        assertEquals(listOf(RawOcrPoint(0, 0), RawOcrPoint(50, 50)), geometry.cornerPoints)
    }

    @Test
    fun textAndConfidenceSurviveTranslation() {
        val result = map(crops = listOf(crop(1, 50, 20, 150, 120)))
        val mapped = result.single().blocks.single().lines.single()

        assertEquals("left", mapped.text)
        assertEquals(RawOcrConfidence.Available(0.87f), mapped.confidence)
        assertEquals("en", mapped.recognizedLanguage)
        assertEquals("left", mapped.elements.single().text)
        assertEquals(RawOcrConfidence.Available(0.87f), mapped.elements.single().confidence)
    }

    @Test
    fun emptyOrNonOverlappingEvidenceProducesEmptyLocalBlocks() {
        val result = MatchResultPanelPpMapper.map(
            panelBlocks = listOf(block(line("outside", 300, 300, 320, 320))),
            crops = listOf(crop(1, 0, 0, 100, 100)),
        )

        assertTrue(result.single().blocks.isEmpty())
        assertEquals(1, MatchResultPanelPpMapper.map(emptyList(), listOf(crop(1, 0, 0, 100, 100))).size)
    }

    @Test
    fun outputPreservesDeterministicPositionOrder() {
        val result = MatchResultPanelPpMapper.map(
            panelBlocks = emptyList(),
            crops = listOf(
                crop(2, 100, 0, 200, 100, MatchResultPositionColumn.RIGHT),
                crop(1, 0, 0, 100, 100),
            ),
        )

        assertEquals(listOf(1, 2), result.map { it.crop.position })
    }

    private fun map(crops: List<MatchResultPositionCrop>) = MatchResultPanelPpMapper.map(
        panelBlocks = listOf(block(line("left", 60, 35, 90, 55, "en", 0.87f))),
        crops = crops,
    )

    private fun crop(
        position: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        column: MatchResultPositionColumn = MatchResultPositionColumn.LEFT,
    ) = MatchResultPositionCrop(position, column, OcrPixelCropRect(left, top, right, bottom))

    private fun block(line: RawOcrLine) = RawOcrBlock(
        text = line.text,
        geometry = null,
        recognizedLanguage = "en",
        confidence = RawOcrConfidence.Unavailable,
        lines = listOf(line),
    )

    private fun line(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        language: String? = null,
        confidence: Float = 0.87f,
    ): RawOcrLine {
        val geometry = RawOcrGeometry(
            boundingBox = RawOcrBoundingBox(left, top, right, bottom),
            cornerPoints = listOf(
                RawOcrPoint(left, top), RawOcrPoint(right, top),
                RawOcrPoint(right, bottom), RawOcrPoint(left, bottom),
            ),
        )
        return RawOcrLine(
            text = text,
            geometry = geometry,
            recognizedLanguage = language,
            confidence = RawOcrConfidence.Available(confidence),
            elements = listOf(
                RawOcrElement(
                    text = text,
                    geometry = geometry,
                    recognizedLanguage = language,
                    confidence = RawOcrConfidence.Available(confidence),
                ),
            ),
        )
    }
}
