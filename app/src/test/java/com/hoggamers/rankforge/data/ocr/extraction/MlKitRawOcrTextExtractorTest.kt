package com.hoggamers.rankforge.data.ocr.extraction

import com.hoggamers.rankforge.domain.ocr.extraction.*
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
import com.hoggamers.rankforge.domain.ocr.preprocessing.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MlKitRawOcrTextExtractorTest {
    @Test fun preservesRawHierarchyGeometryLanguageConfidenceAndCandidateMetadata() = runTest {
        val candidate = candidate(3)
        val block = RawOcrBlock("raw block", RawOcrGeometry(RawOcrBoundingBox(1,2,3,4), listOf(RawOcrPoint(1,2))), "en", RawOcrConfidence.Available(.7f), listOf(RawOcrLine("raw line", null, null, RawOcrConfidence.Unavailable, listOf(RawOcrElement("raw element", null, "en", RawOcrConfidence.Unavailable)))))
        val result = MlKitRawOcrTextExtractor(FakeEngine(RawOcrEngineOutput("raw full text", listOf(block)))).extract(RawOcrExtractionInput(listOf(candidate)))
        assertEquals(listOf(RawOcrExtractionResult.Extracted(candidate, "raw full text", listOf(block))), result)
    }
    @Test fun preservesCandidateOrderAndHandlesEmptyOutput() = runTest {
        val first = candidate(0); val second = candidate(1)
        val result = MlKitRawOcrTextExtractor(object : MlKitRawOcrEngine { override suspend fun recognize(candidate: OcrPreprocessingCandidate) = if (candidate.order == 0) RawOcrEngineOutput("", emptyList()) else RawOcrEngineOutput("text", emptyList()) }).extract(RawOcrExtractionInput(listOf(first, second)))
        assertEquals(listOf(RawOcrExtractionResult.Empty(first), RawOcrExtractionResult.Extracted(second, "text", emptyList())), result)
    }
    @Test fun mapsInputAndEngineFailuresToTypedResults() = runTest {
        val candidate = candidate(0)
        val inputFailure = MlKitRawOcrTextExtractor(FakeEngine(failure = RawOcrInputException())).extract(RawOcrExtractionInput(listOf(candidate)))
        val engineFailure = MlKitRawOcrTextExtractor(FakeEngine(failure = IllegalStateException())).extract(RawOcrExtractionInput(listOf(candidate)))
        assertEquals(listOf(RawOcrExtractionResult.Failed(candidate, RawOcrExtractionFailure.INPUT_UNAVAILABLE)), inputFailure)
        assertEquals(listOf(RawOcrExtractionResult.Failed(candidate, RawOcrExtractionFailure.ENGINE_FAILED)), engineFailure)
    }
    private fun candidate(order: Int) = OcrPreprocessingCandidate(order, OcrPreprocessingCrop.OVERALL_SCOREBOARD, OcrPixelRect(1,2,3,4), FakeImage(), listOf(OcrPreprocessingStep.CROP), null)
    private class FakeImage : OcrPreprocessingImage { override val width = 3; override val height = 4 }
    private class FakeEngine(private val output: RawOcrEngineOutput? = null, private val failure: Throwable? = null) : MlKitRawOcrEngine { override suspend fun recognize(candidate: OcrPreprocessingCandidate): RawOcrEngineOutput { failure?.let { throw it }; return requireNotNull(output) } }
}
