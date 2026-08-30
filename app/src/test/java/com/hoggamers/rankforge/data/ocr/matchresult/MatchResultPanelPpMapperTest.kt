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
        val line = line("clamped", 80, 90, 180, 170).copy(
            geometry = RawOcrGeometry(
                boundingBox = RawOcrBoundingBox(80, 90, 180, 170),
                cornerPoints = listOf(RawOcrPoint(80, 90), RawOcrPoint(180, 170)),
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
