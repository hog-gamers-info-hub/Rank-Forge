package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionColumn
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchResultPositionPaddleOcrTest {
    @Test
    fun upperPositionElevenMetadataIsRepresentable() {
        val evidence = MatchResultPositionPaddleOcrEvidence(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            position = 11,
            column = MatchResultPositionColumn.RIGHT,
            cropWidth = 491,
            cropHeight = 82,
            blocks = emptyList<RawOcrBlock>(),
        )

        assertEquals(11, evidence.position)
        assertEquals(MatchResultScreenshotRole.MATCH_RESULT_UPPER, evidence.role)
        assertEquals(MatchResultPositionColumn.RIGHT, evidence.column)
    }
}
