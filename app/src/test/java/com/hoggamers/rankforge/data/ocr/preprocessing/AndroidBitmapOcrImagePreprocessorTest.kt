package com.hoggamers.rankforge.data.ocr.preprocessing

import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxScoreboardLayout
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingFailure
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingInput
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingResult
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidBitmapOcrImagePreprocessorTest {
    @Test
    fun invalidDimensionsReturnTypedFailure() = runTest {
        val result = preprocessor().preprocess(OcrPreprocessingInput(FakeImage(width = 0, height = 720)))

        assertEquals(
            OcrPreprocessingResult.Failed(OcrPreprocessingFailure.INVALID_DIMENSIONS),
            result,
        )
    }

    @Test
    fun portraitAndUnsupportedAspectRatioReturnTypedFailure() = runTest {
        assertEquals(
            OcrPreprocessingResult.Failed(OcrPreprocessingFailure.UNSUPPORTED_LAYOUT),
            preprocessor().preprocess(OcrPreprocessingInput(FakeImage(width = 720, height = 1_600))),
        )
        assertEquals(
            OcrPreprocessingResult.Failed(OcrPreprocessingFailure.UNSUPPORTED_LAYOUT),
            preprocessor().preprocess(OcrPreprocessingInput(FakeImage(width = 1_600, height = 1_000))),
        )
    }

    @Test
    fun unreadableInputReturnsTypedFailure() = runTest {
        val result = preprocessor(FakeOperations(readable = false)).preprocess(
            OcrPreprocessingInput(FakeImage(width = 1_600, height = 720)),
        )

        assertEquals(
            OcrPreprocessingResult.Failed(OcrPreprocessingFailure.UNREADABLE_INPUT),
            result,
        )
    }

    @Test
    fun baselineCropUsesTheFixedLayoutRectangleAndCandidatesHaveDeterministicOrder() = runTest {
        val operations = FakeOperations()
        val source = FakeImage(width = 1_600, height = 720)

        val result = preprocessor(operations).preprocess(OcrPreprocessingInput(source))

        val candidates = requireCandidates(result)
        assertEquals(OcrPixelRect(x = 208, y = 158, width = 1_168, height = 468), operations.cropRect)
        assertEquals(listOf(0, 1, 2, 3, 4), candidates.map { it.order })
        assertEquals(listOf(null, 1.5, 1.5, 2.0, 2.0), candidates.map { it.scaleFactor })
        assertEquals(
            listOf(
                listOf(OcrPreprocessingStep.CROP),
                listOf(OcrPreprocessingStep.CROP, OcrPreprocessingStep.SCALE),
                listOf(
                    OcrPreprocessingStep.CROP,
                    OcrPreprocessingStep.SCALE,
                    OcrPreprocessingStep.CONTRAST_ADJUSTMENT,
                ),
                listOf(OcrPreprocessingStep.CROP, OcrPreprocessingStep.SCALE),
                listOf(
                    OcrPreprocessingStep.CROP,
                    OcrPreprocessingStep.SCALE,
                    OcrPreprocessingStep.CONTRAST_ADJUSTMENT,
                ),
            ),
            candidates.map { it.appliedSteps },
        )
        assertFalse(source.wasMutated)
        assertTrue(candidates.none { it.image === source })
    }

    @Test
    fun invalidCropAndAllocationFailuresReturnTypedFailures() = runTest {
        val invalidCropResult = preprocessor(FakeOperations(cropResult = null)).preprocess(
            OcrPreprocessingInput(FakeImage(width = 1_600, height = 720)),
        )
        val allocationFailureResult = preprocessor(
            FakeOperations(scaleFailure = OutOfMemoryError()),
        ).preprocess(
            OcrPreprocessingInput(FakeImage(width = 1_600, height = 720)),
        )

        assertEquals(
            OcrPreprocessingResult.Failed(OcrPreprocessingFailure.INVALID_CROP_BOUNDS),
            invalidCropResult,
        )
        assertEquals(
            OcrPreprocessingResult.Failed(OcrPreprocessingFailure.RESOURCE_ALLOCATION_FAILED),
            allocationFailureResult,
        )
    }

    private fun preprocessor(
        operations: FakeOperations = FakeOperations(),
    ): AndroidBitmapOcrImagePreprocessor = AndroidBitmapOcrImagePreprocessor(
        layout = FreeFireMaxScoreboardLayout.definition,
        bitmapOperations = operations,
        dispatcher = Dispatchers.Unconfined,
    )

    private fun requireCandidates(result: OcrPreprocessingResult) =
        (result as? OcrPreprocessingResult.Candidates)?.candidates
            ?: throw AssertionError("Expected preprocessing candidates but was $result")

    private class FakeImage(
        override val width: Int,
        override val height: Int,
        var wasMutated: Boolean = false,
    ) : OcrPreprocessingImage

    private class FakeOperations(
        private val readable: Boolean = true,
        private val cropResult: OcrPreprocessingImage? = FakeImage(1_168, 468),
        private val scaleFailure: Throwable? = null,
    ) : AndroidBitmapOcrPreprocessingOperations {
        var cropRect: OcrPixelRect? = null

        override fun isReadable(image: OcrPreprocessingImage): Boolean = readable

        override fun crop(image: OcrPreprocessingImage, cropRect: OcrPixelRect): OcrPreprocessingImage? {
            this.cropRect = cropRect
            return cropResult
        }

        override fun scale(
            image: OcrPreprocessingImage,
            targetWidth: Int,
            targetHeight: Int,
        ): OcrPreprocessingImage? {
            scaleFailure?.let { throw it }
            return FakeImage(targetWidth, targetHeight)
        }

        override fun adjustContrast(image: OcrPreprocessingImage): OcrPreprocessingImage? =
            FakeImage(image.width, image.height)

        override fun discardGenerated(image: OcrPreprocessingImage) = Unit
    }
}
