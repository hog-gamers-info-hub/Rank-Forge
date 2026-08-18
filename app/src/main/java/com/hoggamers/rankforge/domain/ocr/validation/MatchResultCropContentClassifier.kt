package com.hoggamers.rankforge.domain.ocr.validation

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole

class MatchResultCropContentClassifier {
    fun classify(evidence: MatchResultCropContentEvidence): OcrCropContentValidationResult = when (evidence.role) {
        MatchResultScreenshotRole.MATCH_RESULT_UPPER -> classifyUpper(evidence)
        MatchResultScreenshotRole.MATCH_RESULT_LOWER -> classifyLower(evidence)
    }

    private fun classifyUpper(
        evidence: MatchResultCropContentEvidence,
    ): OcrCropContentValidationResult {
        if (!evidence.aspectRatioIn(UPPER_MIN_ASPECT_RATIO, UPPER_MAX_ASPECT_RATIO)) {
            return OcrCropContentValidationResult.Invalid(OcrCropContentInvalidReason.CROP_INCOMPLETE)
        }
        if (evidence.placementEvidence.count(::isAlignedPlacement) < UPPER_MIN_ALIGNED_PLACEMENTS) {
            return OcrCropContentValidationResult.Invalid(OcrCropContentInvalidReason.EXPECTED_STRUCTURE_MISSING)
        }
        if (
            evidence.playerFieldEvidence.size != UPPER_PLAYER_FIELD_COUNT ||
            evidence.playerFieldEvidence.count { it.maximumExpectedRegionCoverageRatio > 0.0 } <
            UPPER_MIN_OCCUPIED_PLAYER_FIELDS
        ) {
            return OcrCropContentValidationResult.Invalid(OcrCropContentInvalidReason.CROP_INCOMPLETE)
        }
        if (!evidence.hasAllSpatialBands()) {
            return OcrCropContentValidationResult.Invalid(OcrCropContentInvalidReason.EXPECTED_STRUCTURE_MISSING)
        }
        return OcrCropContentValidationResult.Valid
    }

    private fun classifyLower(
        evidence: MatchResultCropContentEvidence,
    ): OcrCropContentValidationResult {
        if (!evidence.aspectRatioIn(LOWER_MIN_ASPECT_RATIO, LOWER_MAX_ASPECT_RATIO)) {
            return OcrCropContentValidationResult.Invalid(OcrCropContentInvalidReason.CROP_INCOMPLETE)
        }
        if (
            evidence.placementEvidence.size != LOWER_REQUIRED_PLACEMENT_COUNT ||
            evidence.placementEvidence.map { it.expectedPlacement }.toSet() != LOWER_REQUIRED_PLACEMENTS
        ) {
            return OcrCropContentValidationResult.Invalid(OcrCropContentInvalidReason.REQUIRED_REGION_MISSING)
        }
        if (evidence.placementEvidence.any { !isAlignedPlacement(it) }) {
            return OcrCropContentValidationResult.Invalid(OcrCropContentInvalidReason.REQUIRED_REGION_MISSING)
        }
        if (
            evidence.playerFieldEvidence.size != LOWER_PLAYER_FIELD_COUNT ||
            evidence.playerFieldEvidence.count { it.maximumExpectedRegionCoverageRatio > 0.0 } <
            LOWER_MIN_OCCUPIED_PLAYER_FIELDS
        ) {
            return OcrCropContentValidationResult.Invalid(OcrCropContentInvalidReason.CROP_INCOMPLETE)
        }
        return OcrCropContentValidationResult.Valid
    }

    private fun isAlignedPlacement(evidence: MatchResultCropPlacementEvidence): Boolean =
        evidence.matchingCandidateCount >= MIN_MATCHING_PLACEMENT_CANDIDATES &&
            evidence.minimumNormalizedCenterDistance != null &&
            evidence.minimumNormalizedCenterDistance <= MAX_PLACEMENT_CENTER_DISTANCE

    private fun MatchResultCropContentEvidence.aspectRatioIn(minimum: Double, maximum: Double): Boolean {
        if (cropWidth <= 0 || cropHeight <= 0) return false
        return (cropWidth.toDouble() / cropHeight.toDouble()) in minimum..maximum
    }

    private fun MatchResultCropContentEvidence.hasAllSpatialBands(): Boolean =
        spatialDistribution.horizontalBandCounts.all { it > 0 } &&
            spatialDistribution.verticalBandCounts.all { it > 0 }

    private companion object {
        const val UPPER_MIN_ASPECT_RATIO = 2.30
        const val UPPER_MAX_ASPECT_RATIO = 2.60
        const val LOWER_MIN_ASPECT_RATIO = 2.30
        const val LOWER_MAX_ASPECT_RATIO = 2.85
        const val MAX_PLACEMENT_CENTER_DISTANCE = 0.05
        const val MIN_MATCHING_PLACEMENT_CANDIDATES = 1
        const val UPPER_MIN_ALIGNED_PLACEMENTS = 4
        const val UPPER_PLAYER_FIELD_COUNT = 40
        const val UPPER_MIN_OCCUPIED_PLAYER_FIELDS = 30
        const val LOWER_REQUIRED_PLACEMENT_COUNT = 2
        val LOWER_REQUIRED_PLACEMENTS = setOf(11, 12)
        const val LOWER_PLAYER_FIELD_COUNT = 8
        const val LOWER_MIN_OCCUPIED_PLAYER_FIELDS = 7
    }
}
