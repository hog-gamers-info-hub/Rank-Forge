package com.hoggamers.rankforge.domain.ocr.validation

import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole

/**
 * Threshold-free measurements collected from one recognized match-result crop.
 *
 * CV-02 deliberately records evidence without deciding VALID/INVALID. Production acceptance
 * thresholds are locked only after the CR-005 calibration gate observes real good and bad crops.
 */
data class MatchResultCropContentEvidence(
    val role: MatchResultScreenshotRole,
    val cropWidth: Int,
    val cropHeight: Int,
    val nonBlankObservationCount: Int,
    val playerLikeObservationCount: Int,
    val killLikeObservationCount: Int,
    val placementEvidence: List<MatchResultCropPlacementEvidence>,
    val playerFieldEvidence: List<MatchResultCropFieldEvidence>,
    val killFieldEvidence: List<MatchResultCropFieldEvidence>,
    val spatialDistribution: MatchResultCropSpatialDistribution,
)

data class MatchResultCropPlacementEvidence(
    val expectedPlacement: Int,
    val matchingCandidateCount: Int,
    val minimumNormalizedCenterDistance: Double?,
)

data class MatchResultCropFieldEvidence(
    val fieldId: String,
    val fieldType: MatchResultOcrFieldType,
    val maximumExpectedRegionCoverageRatio: Double,
    val maximumObservationContainmentRatio: Double,
    val minimumNormalizedCenterDistance: Double?,
)

data class MatchResultCropSpatialDistribution(
    val horizontalBandCounts: List<Int>,
    val verticalBandCounts: List<Int>,
) {
    init {
        require(horizontalBandCounts.size == EVIDENCE_BAND_COUNT) {
            "Horizontal evidence must contain exactly $EVIDENCE_BAND_COUNT bands."
        }
        require(verticalBandCounts.size == EVIDENCE_BAND_COUNT) {
            "Vertical evidence must contain exactly $EVIDENCE_BAND_COUNT bands."
        }
        require(horizontalBandCounts.all { it >= 0 }) { "Horizontal evidence counts must not be negative." }
        require(verticalBandCounts.all { it >= 0 }) { "Vertical evidence counts must not be negative." }
    }
}

internal const val EVIDENCE_BAND_COUNT = 4
