package com.hoggamers.rankforge.presentation.screen

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCandidateValidatorTest {
    @Test
    fun acceptsPngJpegAndWebpCandidates() = runTest {
        listOf("image/png", "image/jpeg", "image/webp").forEach { mimeType ->
            val result = validator(
                ImageCandidateReadResult.Metadata(mimeType, width = 1080, height = 1920),
            ).validate("content://picker/image")

            assertEquals(ImageCandidateValidationResult.Valid, result)
        }
    }

    @Test
    fun rejectsNonImageAndUnsupportedFormats() = runTest {
        val nonImageResult = validator(
            ImageCandidateReadResult.Metadata("text/plain", width = 1080, height = 1920),
        ).validate("content://picker/text")
        val unsupportedResult = validator(
            ImageCandidateReadResult.Metadata("image/gif", width = 1080, height = 1920),
        ).validate("content://picker/gif")

        assertEquals(
            ImageCandidateValidationResult.Invalid(ImageValidationError.NON_IMAGE_CONTENT),
            nonImageResult,
        )
        assertEquals(
            ImageCandidateValidationResult.Invalid(ImageValidationError.UNSUPPORTED_FORMAT),
            unsupportedResult,
        )
    }

    @Test
    fun rejectsUnreadableAndDecodeFailingCandidates() = runTest {
        val unreadableResult = validator(ImageCandidateReadResult.Unreadable)
            .validate("content://picker/unreadable")
        val decodeFailureResult = validator(ImageCandidateReadResult.DecodeFailure)
            .validate("content://picker/malformed")

        assertEquals(
            ImageCandidateValidationResult.Invalid(ImageValidationError.UNREADABLE_URI),
            unreadableResult,
        )
        assertEquals(
            ImageCandidateValidationResult.Invalid(ImageValidationError.DECODE_FAILED),
            decodeFailureResult,
        )
    }

    @Test
    fun rejectsInvalidDimensionsAndOversizedCandidates() = runTest {
        val invalidDimensionsResult = validator(
            ImageCandidateReadResult.Metadata("image/png", width = 0, height = 1920),
        ).validate("content://picker/invalid-dimensions")
        val oversizedResult = validator(
            ImageCandidateReadResult.Metadata("image/png", width = 8192, height = 8192),
        ).validate("content://picker/oversized")

        assertEquals(
            ImageCandidateValidationResult.Invalid(ImageValidationError.INVALID_DIMENSIONS),
            invalidDimensionsResult,
        )
        assertEquals(
            ImageCandidateValidationResult.Invalid(ImageValidationError.IMAGE_TOO_LARGE),
            oversizedResult,
        )
    }

    @Test
    fun rejectsBlankUrisWithoutReadingMetadata() = runTest {
        val result = validator(ImageCandidateReadResult.Unreadable).validate(" ")

        assertEquals(
            ImageCandidateValidationResult.Invalid(ImageValidationError.EMPTY_URI),
            result,
        )
    }

    private fun validator(readResult: ImageCandidateReadResult): ImageCandidateValidator =
        ImageCandidateValidator(ImageCandidateMetadataReader { readResult })
}
