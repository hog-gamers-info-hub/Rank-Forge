package com.hoggamers.rankforge.data.ocr.extraction

import com.hoggamers.rankforge.domain.ocr.extraction.DefaultRosterRawOcrExtractor
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrEngineOutput
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrEngine
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionType
import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterPanelInput
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test

class MlKitRosterRawOcrExtractorTest {
    @Test
    fun dedicatedSlotNumberRegionIsNotEmittedByTheRosterExtractionPipeline() = runTest {
        val regionTypes = mutableListOf<RosterRawOcrRegionType>()
        DefaultRosterRawOcrExtractor(
            object : RosterRawOcrEngine {
                override suspend fun recognize(input: RosterRawOcrRegionInput): RawOcrEngineOutput {
                    regionTypes += input.regionIdentity.regionType
                    return RawOcrEngineOutput("raw", emptyList())
                }
            },
        ).extract(
            RosterRawOcrExtractionInput(
                croppedPanelImage = FakeImage(),
                croppedPanelInput = CroppedRosterPanelInput(
                    screenshotPosition = RosterScreenshotPosition.ONE,
                    isPreparedRosterCrop = true,
                    imageWidth = 800,
                    imageHeight = 600,
                ),
            ),
        )

        assertFalse(regionTypes.contains(RosterRawOcrRegionType.SLOT_NUMBER))
    }

    private class FakeImage : OcrPreprocessingImage {
        override val width: Int = 800
        override val height: Int = 600
    }
}
