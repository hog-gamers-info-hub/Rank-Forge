package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchResultSemanticRoleReconcilerTest {
    private val reconciler = MatchResultSemanticRoleReconciler()

    @Test
    fun upperAndLowerResolveToCanonicalRoles() {
        val result = reconciler.reconcile(physicalResults(upperStored = 0, lowerStored = 1))

        val resolved = result as MatchResultSemanticRoleReconciliation.Resolved
        assertEquals(setOf(*MatchResultScreenshotRole.entries.toTypedArray()), resolved.results.keys)
        assertEquals(0, resolved.results.getValue(MatchResultScreenshotRole.MATCH_RESULT_UPPER).pixelCrop.left)
        assertEquals(1, resolved.results.getValue(MatchResultScreenshotRole.MATCH_RESULT_LOWER).pixelCrop.left)
    }

    @Test
    fun swappedPhysicalRolesAreRekeyedBySemanticRole() {
        val result = reconciler.reconcile(physicalResults(upperStored = 1, lowerStored = 0))

        val resolved = result as MatchResultSemanticRoleReconciliation.Resolved
        assertEquals(0, resolved.results.getValue(MatchResultScreenshotRole.MATCH_RESULT_UPPER).pixelCrop.left)
        assertEquals(1, resolved.results.getValue(MatchResultScreenshotRole.MATCH_RESULT_LOWER).pixelCrop.left)
    }

    @Test
    fun duplicateUpperRoleIsConflict() {
        assertEquals(
            MatchResultSemanticRoleReconciliation.Conflict,
            reconciler.reconcile(
                mapOf(
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER to processed(MatchResultScreenshotRole.MATCH_RESULT_UPPER, 0),
                    MatchResultScreenshotRole.MATCH_RESULT_LOWER to processed(MatchResultScreenshotRole.MATCH_RESULT_UPPER, 1),
                ),
            ),
        )
    }

    @Test
    fun duplicateLowerRoleIsConflict() {
        assertEquals(
            MatchResultSemanticRoleReconciliation.Conflict,
            reconciler.reconcile(
                mapOf(
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER to processed(MatchResultScreenshotRole.MATCH_RESULT_LOWER, 0),
                    MatchResultScreenshotRole.MATCH_RESULT_LOWER to processed(MatchResultScreenshotRole.MATCH_RESULT_LOWER, 1),
                ),
            ),
        )
    }

    @Test
    fun unresolvedPhysicalResultIsIncomplete() {
        assertEquals(
            MatchResultSemanticRoleReconciliation.Incomplete,
            reconciler.reconcile(
                mapOf(
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER to MatchResultOcrPreviewProcessingResult.SemanticRoleResolutionFailed,
                    MatchResultScreenshotRole.MATCH_RESULT_LOWER to processed(MatchResultScreenshotRole.MATCH_RESULT_LOWER, 1),
                ),
            ),
        )
    }

    @Test
    fun ambiguousPhysicalResultIsIncomplete() {
        assertEquals(
            MatchResultSemanticRoleReconciliation.Incomplete,
            reconciler.reconcile(
                mapOf(
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER to MatchResultOcrPreviewProcessingResult.SemanticRoleResolutionFailed,
                    MatchResultScreenshotRole.MATCH_RESULT_LOWER to processed(MatchResultScreenshotRole.MATCH_RESULT_UPPER, 1),
                ),
            ),
        )
    }

    private fun physicalResults(upperStored: Int, lowerStored: Int) = mapOf(
        MatchResultScreenshotRole.MATCH_RESULT_UPPER to processed(
            if (upperStored == 0) MatchResultScreenshotRole.MATCH_RESULT_UPPER else MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            upperStored,
        ),
        MatchResultScreenshotRole.MATCH_RESULT_LOWER to processed(
            if (lowerStored == 0) MatchResultScreenshotRole.MATCH_RESULT_UPPER else MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            lowerStored,
        ),
    )

    private fun processed(role: MatchResultScreenshotRole, physicalMarker: Int): MatchResultOcrPreviewProcessingResult.Processed {
        val placement = MatchResultOcrField(
                        id = "PLACEMENT_${role.name}_$physicalMarker",
                        type = MatchResultOcrFieldType.PLACEMENT,
                        position = if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) 1 else 11,
                        visualRow = null,
                        slot = null,
                        canonicalRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
                        mappedRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
                        ocrText = "",
                        resolvedText = "1",
                        status = MatchResultOcrFieldStatus.TEMPLATE_ONLY,
                    )
        return MatchResultOcrPreviewProcessingResult.Processed(
            extraction = MatchResultOcrExtractionResult(
                role = role,
                fields = listOf(placement),
                rows = listOf(
                    MatchResultOcrRow(
                        position = if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) 1 else 11,
                        source = MatchResultOcrRowSource.UPPER_TEMPLATE,
                        placement = placement,
                        playerSlots = emptyList(),
                    ),
                ),
            ),
            pixelCrop = OcrPixelCropRect(physicalMarker, 0, physicalMarker + 1, 1),
            cropWidth = 1,
            cropHeight = 1,
            source = MatchResultOcrPreviewSource.NEW_PP_POSITION,
        )
    }
}
