package com.hoggamers.rankforge.domain.ocr.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OcrCropContentValidationContractTest {
    @Test
    fun validOutcomeIsAStableSingleton() {
        assertSame(
            OcrCropContentValidationResult.Valid,
            OcrCropContentValidationResult.Valid,
        )
    }

    @Test
    fun invalidOutcomePreservesSemanticFailureReason() {
        val result = OcrCropContentValidationResult.Invalid(
            reason = OcrCropContentInvalidReason.CROP_INCOMPLETE,
        )

        assertEquals(OcrCropContentInvalidReason.CROP_INCOMPLETE, result.reason)
    }

    @Test
    fun indeterminateOutcomePreservesTechnicalFailureReason() {
        val result = OcrCropContentValidationResult.Indeterminate(
            reason = OcrCropContentIndeterminateReason.OCR_RECOGNITION_FAILED,
        )

        assertEquals(OcrCropContentIndeterminateReason.OCR_RECOGNITION_FAILED, result.reason)
    }

    @Test
    fun invalidReasonSetMatchesDecisionContract() {
        assertEquals(
            setOf(
                OcrCropContentInvalidReason.EXPECTED_STRUCTURE_MISSING,
                OcrCropContentInvalidReason.REQUIRED_REGION_MISSING,
                OcrCropContentInvalidReason.CROP_INCOMPLETE,
                OcrCropContentInvalidReason.WRONG_CONTENT,
            ),
            OcrCropContentInvalidReason.values().toSet(),
        )
    }

    @Test
    fun indeterminateReasonSetMatchesDecisionContract() {
        assertEquals(
            setOf(
                OcrCropContentIndeterminateReason.IMAGE_DECODE_FAILED,
                OcrCropContentIndeterminateReason.INVALID_PREPARED_BITMAP,
                OcrCropContentIndeterminateReason.OCR_RECOGNITION_FAILED,
                OcrCropContentIndeterminateReason.VALIDATION_EXECUTION_FAILED,
            ),
            OcrCropContentIndeterminateReason.values().toSet(),
        )
    }

    @Test
    fun topLevelOutcomesRemainDistinct() {
        assertEquals("VALID", statusOf(OcrCropContentValidationResult.Valid))
        assertEquals(
            "INVALID",
            statusOf(
                OcrCropContentValidationResult.Invalid(
                    OcrCropContentInvalidReason.EXPECTED_STRUCTURE_MISSING,
                ),
            ),
        )
        assertEquals(
            "INDETERMINATE",
            statusOf(
                OcrCropContentValidationResult.Indeterminate(
                    OcrCropContentIndeterminateReason.VALIDATION_EXECUTION_FAILED,
                ),
            ),
        )
    }

    private fun statusOf(result: OcrCropContentValidationResult): String = when (result) {
        OcrCropContentValidationResult.Valid -> "VALID"
        is OcrCropContentValidationResult.Invalid -> "INVALID"
        is OcrCropContentValidationResult.Indeterminate -> "INDETERMINATE"
    }
}
