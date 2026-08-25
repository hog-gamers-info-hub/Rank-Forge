package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrEngineOutput
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerOcrEngine
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerOcrTextFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LobbyPlayerOcrCandidateExtractorTest {
    @Test
    fun mlUsesCoherentLinesInGeometryOrderAndCollapsesOnlyWhitespace() {
        val result = LobbyPlayerOcrCandidateExtractor.fromMlOutput(
            RawOcrEngineOutput(
                fullText = "NE.\nZLUX",
                blocks = listOf(
                    block("ZLUX", RawOcrBoundingBox(60, 0, 100, 20)),
                    block("NE.   ", RawOcrBoundingBox(5, 0, 40, 20)),
                ),
            ),
        )

        assertEquals(LobbyPlayerOcrEngine.ML_KIT, result.engine)
        assertEquals("NE. ZLUX", result.candidateText)
        assertEquals("NE.\nZLUX", result.rawText)
        assertEquals(2, result.blocks.size)
    }

    @Test
    fun ppFragmentsAreOrderedLeftToRightAndEmptyRowsStayEmpty() {
        val candidate = LobbyPlayerOcrCandidateExtractor.fromFragments(
            listOf(
                LobbyPlayerOcrTextFragment("ZLUX", RawOcrBoundingBox(50, 0, 80, 20)),
                LobbyPlayerOcrTextFragment("NE.", RawOcrBoundingBox(5, 0, 35, 20)),
            ),
        )

        assertEquals("NE. ZLUX", candidate)
        assertNull(LobbyPlayerOcrCandidateExtractor.fromFragments(emptyList()))
    }

    private fun block(text: String, bounds: RawOcrBoundingBox): RawOcrBlock {
        val geometry = RawOcrGeometry(bounds, null)
        return RawOcrBlock(
            text = text,
            geometry = geometry,
            recognizedLanguage = null,
            confidence = RawOcrConfidence.Unavailable,
            lines = listOf(
                RawOcrLine(
                    text = text,
                    geometry = geometry,
                    recognizedLanguage = null,
                    confidence = RawOcrConfidence.Unavailable,
                    elements = emptyList(),
                ),
            ),
        )
    }
}
