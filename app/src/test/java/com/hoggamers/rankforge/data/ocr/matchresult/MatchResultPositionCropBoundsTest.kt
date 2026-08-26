package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropEvidence
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropObservation
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculationResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculator
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchResultPositionCropBoundsTest {
    private val dimensions = OcrImageDimensions(width = 1_161, height = 470)

    @Test
    fun rightEdgeOvershootIsClampedToSourceWidth() {
        val normalized = box(left = 1_030, top = 20, right = 1_163, bottom = 40)
            .clampToPositionCropImageOrNull(dimensions)

        assertEquals(box(left = 1_030, top = 20, right = 1_161, bottom = 40), normalized)
    }

    @Test
    fun bottomEdgeOvershootIsClampedToSourceHeight() {
        val normalized = box(left = 20, top = 440, right = 80, bottom = 474)
            .clampToPositionCropImageOrNull(dimensions)

        assertEquals(box(left = 20, top = 440, right = 80, bottom = 470), normalized)
    }

    @Test
    fun fullyOutsideBoxIsRejectedAfterClamping() {
        val normalized = box(left = 1_162, top = 20, right = 1_163, bottom = 40)
            .clampToPositionCropImageOrNull(dimensions)

        assertNull(normalized)
    }

    @Test
    fun inBoundsBoxIsUnchanged() {
        val source = box(left = 20, top = 40, right = 80, bottom = 100)

        assertEquals(source, source.clampToPositionCropImageOrNull(dimensions))
    }

    @Test
    fun physicalUpperGeometryClampsRightBoundaryAndBuildsPositionsOneThroughTen() {
        val result = MatchResultPositionCropCalculator().calculate(
            evidence = MatchResultAutoCropEvidence(
                observations = listOf(
                    observation("Eliminations", 250, 40, 340, 70),
                    observation("Eliminations", 520, 40, 629, 70),
                    observation("Eliminations", 820, 40, 930, 70),
                    observation("Eliminations", 1_030, 40, 1_163, 70),
                    observation("4", 36, 319, 47, 339),
                    observation("5", 37, 413, 48, 433),
                    observation("6", 682, 25, 700, 45),
                    observation("7", 682, 107, 700, 127),
                ).map { observation ->
                    observation.copy(
                        boundingBox = observation.boundingBox?.clampToPositionCropImageOrNull(dimensions),
                    )
                },
                imageDimensions = dimensions,
            ),
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        ) as MatchResultPositionCropCalculationResult.Available

        assertEquals((1..10).toList(), result.crops.map { crop -> crop.position })
        assertEquals(24, result.crops.first { crop -> crop.position == 1 }.bounds.left)
        assertEquals(670, result.crops.first { crop -> crop.position == 6 }.bounds.left)
        assertEquals(1_161, result.crops.first { crop -> crop.position == 6 }.bounds.right)
        assertEquals(0, result.crops.first { crop -> crop.position == 1 }.bounds.top)
        assertEquals(94, result.crops.first { crop -> crop.position == 1 }.bounds.bottom)
        assertEquals(0, result.crops.first { crop -> crop.position == 6 }.bounds.top)
        assertEquals(76, result.crops.first { crop -> crop.position == 6 }.bounds.bottom)
    }

    private fun observation(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): MatchResultAutoCropObservation = MatchResultAutoCropObservation(
        text = text,
        boundingBox = box(left, top, right, bottom),
    )

    private fun box(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): RawOcrBoundingBox = RawOcrBoundingBox(left, top, right, bottom)
}
