package com.hoggamers.rankforge.domain.ocr.extraction

import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterPanelInput
import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxCroppedRosterPanelLayout
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotContentSlotNumberExtractor
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
    fun slotContentOnlyExtractionUsesEachVisibleContentRegionOnceAndPreservesRawEvidence() = runTest {
        val numbersByPosition = mapOf(
            RosterVisibleSlotPosition.TOP_LEFT to 5,
            RosterVisibleSlotPosition.TOP_RIGHT to 6,
            RosterVisibleSlotPosition.BOTTOM_LEFT to 7,
            RosterVisibleSlotPosition.BOTTOM_RIGHT to 8,
        )
        val engine = RecordingEngine { input ->
            val number = numbersByPosition.getValue(input.regionIdentity.visibleSlotPosition)
            RawOcrEngineOutput(
                fullText = "slot $number",
                blocks = listOf(
                    RawOcrBlock(
                        text = number.toString(),
                        geometry = RawOcrGeometry(
                            boundingBox = RawOcrBoundingBox(5, 10, 10, 20),
                            cornerPoints = listOf(RawOcrPoint(5, 10)),
                        ),
                        recognizedLanguage = "en",
                        confidence = RawOcrConfidence.Unavailable,
                        lines = listOf(
                            RawOcrLine(
                                text = number.toString(),
                                geometry = RawOcrGeometry(
                                    boundingBox = RawOcrBoundingBox(5, 10, 10, 20),
                                    cornerPoints = null,
                                ),
                                recognizedLanguage = "en",
                                confidence = RawOcrConfidence.Unavailable,
                                elements = listOf(
                                    RawOcrElement(
                                        text = number.toString(),
                                        geometry = RawOcrGeometry(
                                            boundingBox = RawOcrBoundingBox(5, 10, 10, 20),
                                            cornerPoints = null,
                                        ),
                                        recognizedLanguage = "en",
                                        confidence = RawOcrConfidence.Unavailable,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        val results = DefaultRosterRawOcrExtractor(engine).extract(
            validInput(regionSelection = RosterRawOcrRegionSelection.SLOT_CONTENT_ONLY),
        )

        assertEquals(4, engine.inputs.size)
        assertEquals(RosterVisibleSlotPosition.entries, engine.inputs.map { it.regionIdentity.visibleSlotPosition })
        assertTrue(engine.inputs.all { input ->
            input.regionIdentity.regionType == RosterRawOcrRegionType.SLOT_CONTENT &&
                input.regionIdentity.playerRowIndex == null
        })
        layout.slots.forEach { slot ->
            assertEquals(
                slot.contentRect.toPixelRect(800, 600),
                engine.inputs.single { it.regionIdentity.visibleSlotPosition == slot.visiblePosition }.pixelRect,
            )
        }

        val extracted = results.map { it as RosterRawOcrExtractionResult.Extracted }
        assertEquals(4, extracted.size)
        extracted.forEach { result ->
            val number = numbersByPosition.getValue(result.evidence.regionIdentity.visibleSlotPosition)
            assertEquals("slot $number", result.evidence.rawText)
            assertEquals(number.toString(), result.evidence.blocks.single().text)
            assertEquals(
                RawOcrBoundingBox(5, 10, 10, 20),
                result.evidence.blocks.single().lines.single().elements.single().geometry?.boundingBox,
            )
        }
        assertEquals(numbersByPosition, LobbySlotContentSlotNumberExtractor.derive(results).mapValues {
            it.value.detectedSlotNumber
        })
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
    fun slotContentOnlyEmptyRecognizerOutputBecomesTypedEmptyResults() = runTest {
        val results = DefaultRosterRawOcrExtractor(
            FakeEngine(output = RawOcrEngineOutput("", emptyList())),
        ).extract(validInput(regionSelection = RosterRawOcrRegionSelection.SLOT_CONTENT_ONLY))

        assertEquals(4, results.size)
        assertTrue(results.all {
            it is RosterRawOcrExtractionResult.Empty &&
                it.regionIdentity.regionType == RosterRawOcrRegionType.SLOT_CONTENT
        })
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
    fun slotContentOnlyRecognizerFailuresRetainSlotContentRegionIdentity() = runTest {
        val results = DefaultRosterRawOcrExtractor(
            FakeEngine(failure = IllegalStateException()),
        ).extract(validInput(regionSelection = RosterRawOcrRegionSelection.SLOT_CONTENT_ONLY))

        assertEquals(4, results.size)
        assertTrue(results.all {
            it is RosterRawOcrExtractionResult.Failed &&
                it.failure == RosterRawOcrFailure.RECOGNIZER_FAILED &&
                it.regionIdentity?.regionType == RosterRawOcrRegionType.SLOT_CONTENT &&
                it.regionIdentity.playerRowIndex == null
        })
    }

    @Test
    fun cancellationPropagatesWithoutCreatingEvidenceOrFailureResults() = runTest {
        val extractor = DefaultRosterRawOcrExtractor(FakeEngine(failure = CancellationException("cancelled")))

        try {
            extractor.extract(validInput(regionSelection = RosterRawOcrRegionSelection.SLOT_CONTENT_ONLY))
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
        regionSelection: RosterRawOcrRegionSelection = RosterRawOcrRegionSelection.FULL,
    ): RosterRawOcrExtractionInput = RosterRawOcrExtractionInput(
        croppedPanelImage = image,
        croppedPanelInput = panelInput,
        layout = layout,
        regionSelection = regionSelection,
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

    private class RecordingEngine(
        private val output: (RosterRawOcrRegionInput) -> RawOcrEngineOutput = {
            RawOcrEngineOutput("raw", emptyList())
        },
    ) : RosterRawOcrEngine {
        val inputs = mutableListOf<RosterRawOcrRegionInput>()

        override suspend fun recognize(input: RosterRawOcrRegionInput): RawOcrEngineOutput {
            inputs += input
            return output(input)
        }
    }
}
