package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchResultPositionOcrGeometryMapperTest {
    @Test
    fun wholePositionCoordinatesReturnToPositionLocalSpaceAndClamp() {
        val mapped = MatchResultPositionOcrGeometryMapper.mapBlocks(
            blocks = listOf(
                RawOcrBlock(
                    text = "",
                    geometry = null,
                    recognizedLanguage = null,
                    confidence = RawOcrConfidence.Unavailable,
                    lines = listOf(
                        RawOcrLine(
                            text = "3EliminationPlayer",
                            geometry = RawOcrGeometry(RawOcrBoundingBox(-12, 30, 900, 300), null),
                            recognizedLanguage = null,
                            confidence = RawOcrConfidence.Unavailable,
                            elements = emptyList(),
                        ),
                    ),
                ),
            ),
            scale = MatchResultRowOcrCandidate.SCALE_3X,
            positionWidth = 200,
            positionHeight = 90,
        )

        assertEquals(
            RawOcrBoundingBox(0, 10, 200, 90),
            mapped.single().lines.single().geometry?.boundingBox,
        )
    }
}
