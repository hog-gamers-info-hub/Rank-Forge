package com.hoggamers.rankforge.domain.ocr.extraction

import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterPanelInput
import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxCroppedRosterPanelLayout
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RosterRawOcrExtractorTest {
    private val layout = FreeFireMaxCroppedRosterPanelLayout.definition

    @Test
    fun extractedEvidencePreservesRosterRegionAndRawMetadata() = runTest {
        val block = RawOcrBlock(
            text = "raw block",
            geometry = RawOcrGeometry(
                boundingBox = RawOcrBoundingBox(1, 2, 3, 4),
                cornerPoints = listOf(RawOcrPoint(1, 2)),
            ),
            recognizedLanguage = "en",
            confidence = RawOcrConfidence.Unavailable,
            lines = listOf(
                RawOcrLine(
                    text = "raw line",
                    geometry = null,
                    recognizedLanguage = "en",
                    confidence = RawOcrConfidence.Unavailable,
                    elements = listOf(
                        RawOcrElement(
                            text = "raw element",
                            geometry = null,
                            recognizedLanguage = null,
                            confidence = RawOcrConfidence.Unavailable,
                        ),
                    ),
                ),
            ),
        )
        val results = DefaultRosterRawOcrExtractor(
            FakeEngine(output = RawOcrEngineOutput("raw full text", listOf(block))),
        ).extract(validInput(screenshotPosition = RosterScreenshotPosition.TWO))

        assertEquals(20, results.size)
        val first = (results.first() as RosterRawOcrExtractionResult.Extracted).evidence
        assertEquals(RosterScreenshotPosition.TWO, first.regionIdentity.screenshotPosition)
        assertEquals(RosterVisibleSlotPosition.TOP_LEFT, first.regionIdentity.visibleSlotPosition)
        assertEquals(RosterRawOcrRegionType.SLOT_CONTENT, first.regionIdentity.regionType)
        assertEquals(5..8, first.regionIdentity.intendedTournamentSlotRange)
        assertEquals(5, first.regionIdentity.intendedTournamentSlot)
        assertEquals("raw full text", first.rawText)
        assertEquals(listOf(block), first.blocks)
        assertEquals(
            listOf(
                RosterRawOcrEvidence("raw block", block.geometry, "en", RawOcrConfidence.Unavailable),
                RosterRawOcrEvidence("raw line", null, "en", RawOcrConfidence.Unavailable),
                RosterRawOcrEvidence("raw element", null, null, RawOcrConfidence.Unavailable),
            ),
            first.rawEvidence,
        )
    }

    @Test
    fun regionIdentityDistinguishesSlotContentAndAllPlayerRows() = runTest {
        val results = DefaultRosterRawOcrExtractor(
            FakeEngine(output = RawOcrEngineOutput("raw", emptyList())),
        ).extract(validInput())

        val identities = results.map { result ->
            (result as RosterRawOcrExtractionResult.Extracted).evidence.regionIdentity
        }
        assertEquals(
            listOf(
                RosterRawOcrRegionType.SLOT_CONTENT,
                RosterRawOcrRegionType.PLAYER_ROW,
                RosterRawOcrRegionType.PLAYER_ROW,
                RosterRawOcrRegionType.PLAYER_ROW,
                RosterRawOcrRegionType.PLAYER_ROW,
            ),
            identities.take(5).map { it.regionType },
        )
        assertEquals((1..4).toList(), identities.take(5).drop(1).map { it.playerRowIndex })
        assertTrue(identities.filter { it.regionType != RosterRawOcrRegionType.PLAYER_ROW }
            .all { it.playerRowIndex == null })
    }

    @Test
    fun extractionUsesTheApprovedLayoutRegionsForEachRecognizerRequest() = runTest {
        val engine = RecordingEngine()
        DefaultRosterRawOcrExtractor(engine).extract(validInput())

        assertEquals(20, engine.inputs.size)
        assertEquals(
            listOf(
                RosterRawOcrRegionType.SLOT_CONTENT,
                RosterRawOcrRegionType.PLAYER_ROW,
                RosterRawOcrRegionType.PLAYER_ROW,
                RosterRawOcrRegionType.PLAYER_ROW,
                RosterRawOcrRegionType.PLAYER_ROW,
            ),
            engine.inputs.take(5).map { it.regionIdentity.regionType },
        )
        assertEquals(
            layout.slots.first().contentRect.toPixelRect(800, 600),
            engine.inputs.first().pixelRect,
        )
        assertEquals(
            layout.slots.first().playerRowRegions.first().rect.toPixelRect(800, 600),
            engine.inputs[1].pixelRect,
        )
    }

    @Test
    fun emptyRecognizerOutputBecomesTypedEmptyResultsForEveryRosterRegion() = runTest {
        val results = DefaultRosterRawOcrExtractor(
            FakeEngine(output = RawOcrEngineOutput("", emptyList())),
        ).extract(validInput())

        assertEquals(20, results.size)
        assertTrue(results.all { it is RosterRawOcrExtractionResult.Empty })
    }

    @Test
    fun missingInputInvalidDimensionsAndUnpreparedInputReturnTypedFailures() = runTest {
        val extractor = DefaultRosterRawOcrExtractor(FakeEngine(output = RawOcrEngineOutput("raw", emptyList())))

        assertEquals(
            listOf(RosterRawOcrExtractionResult.Failed(RosterRawOcrFailure.MISSING_CROPPED_INPUT)),
            extractor.extract(validInput(image = null)),
        )
        assertEquals(
            listOf(
                RosterRawOcrExtractionResult.Failed(
                    RosterRawOcrFailure.INVALID_CROPPED_PANEL_DIMENSIONS,
                ),
            ),
            extractor.extract(
                validInput(
                    panelInput = CroppedRosterPanelInput(
                        screenshotPosition = RosterScreenshotPosition.ONE,
                        isPreparedRosterCrop = true,
                        imageWidth = 0,
                        imageHeight = 600,
                    ),
                ),
            ),
        )
        assertEquals(
            listOf(
                RosterRawOcrExtractionResult.Failed(
                    RosterRawOcrFailure.UNPREPARED_CROPPED_INPUT,
                ),
            ),
            extractor.extract(
                validInput(
                    panelInput = CroppedRosterPanelInput(
                        screenshotPosition = RosterScreenshotPosition.ONE,
                        isPreparedRosterCrop = false,
                        imageWidth = 800,
                        imageHeight = 600,
                    ),
                ),
            ),
        )
        assertEquals(
            listOf(
                RosterRawOcrExtractionResult.Failed(
                    RosterRawOcrFailure.UNSUPPORTED_SCREENSHOT_POSITION,
                ),
            ),
            extractor.extract(
                validInput(
                    panelInput = CroppedRosterPanelInput(
                        screenshotPosition = null,
                        isPreparedRosterCrop = true,
                        imageWidth = 800,
                        imageHeight = 600,
                    ),
                ),
            ),
        )
    }

    @Test
    fun invalidLayoutAndRecognizerFailureReturnTypedFailures() = runTest {
        val invalidLayoutInput = validInput(
            layout = layout.copy(slots = layout.slots.drop(1)),
        )
        val invalidLayoutResult = DefaultRosterRawOcrExtractor(
            FakeEngine(output = RawOcrEngineOutput("raw", emptyList())),
        ).extract(invalidLayoutInput)
        assertEquals(
            listOf(RosterRawOcrExtractionResult.Failed(RosterRawOcrFailure.LAYOUT_INCOMPATIBLE)),
            invalidLayoutResult,
        )

        val failedResults = DefaultRosterRawOcrExtractor(
            FakeEngine(failure = IllegalStateException()),
        ).extract(validInput())
        assertEquals(20, failedResults.size)
        assertTrue(
            failedResults.all {
                it is RosterRawOcrExtractionResult.Failed &&
                    it.failure == RosterRawOcrFailure.RECOGNIZER_FAILED &&
                    it.regionIdentity != null
            },
        )
    }

    @Test
    fun cancellationPropagatesWithoutCreatingEvidenceOrFailureResults() = runTest {
        val extractor = DefaultRosterRawOcrExtractor(FakeEngine(failure = CancellationException("cancelled")))

        try {
            extractor.extract(validInput())
            fail("Expected cancellation to propagate.")
        } catch (cancellation: CancellationException) {
            assertEquals("cancelled", cancellation.message)
        }
    }

    private fun validInput(
        screenshotPosition: RosterScreenshotPosition = RosterScreenshotPosition.ONE,
        image: OcrPreprocessingImage? = FakeImage(),
        panelInput: CroppedRosterPanelInput = CroppedRosterPanelInput(
            screenshotPosition = screenshotPosition,
            isPreparedRosterCrop = true,
            imageWidth = 800,
            imageHeight = 600,
        ),
        layout: com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterPanelLayout = this.layout,
    ): RosterRawOcrExtractionInput = RosterRawOcrExtractionInput(
        croppedPanelImage = image,
        croppedPanelInput = panelInput,
        layout = layout,
    )

    private class FakeImage : OcrPreprocessingImage {
        override val width: Int = 800
        override val height: Int = 600
    }

    private class FakeEngine(
        private val output: RawOcrEngineOutput? = null,
        private val failure: Throwable? = null,
    ) : RosterRawOcrEngine {
        override suspend fun recognize(input: RosterRawOcrRegionInput): RawOcrEngineOutput {
            failure?.let { throw it }
            return requireNotNull(output)
        }
    }

    private class RecordingEngine : RosterRawOcrEngine {
        val inputs = mutableListOf<RosterRawOcrRegionInput>()

        override suspend fun recognize(input: RosterRawOcrRegionInput): RawOcrEngineOutput {
            inputs += input
            return RawOcrEngineOutput("raw", emptyList())
        }
    }
}
