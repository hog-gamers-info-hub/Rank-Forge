package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

data class MatchResultAutoCropObservation(
    val text: String,
    val boundingBox: RawOcrBoundingBox?,
)

data class MatchResultAutoCropEvidence(
    val observations: List<MatchResultAutoCropObservation>,
    val imageDimensions: OcrImageDimensions,
)

sealed interface MatchResultAutoCropResult {
    data class Proposed(
        val crop: OcrNormalizedCropRect,
    ) : MatchResultAutoCropResult

    data object AnchorFourMissing : MatchResultAutoCropResult
    data object AnchorFiveMissing : MatchResultAutoCropResult
    data object RightBoundaryMissing : MatchResultAutoCropResult
    data object InvalidRowPitch : MatchResultAutoCropResult
    data object InvalidCalculatedCrop : MatchResultAutoCropResult
    data object OcrFailed : MatchResultAutoCropResult
}

private fun MatchResultAutoCropObservation.usableBoundingBoxOrNull(
    dimensions: OcrImageDimensions,
): RawOcrBoundingBox? =
    boundingBox?.takeIf { text.trim().isNotEmpty() && it.isUsableFor(dimensions) }

private fun RawOcrBoundingBox.isUsableFor(dimensions: OcrImageDimensions): Boolean =
    right > left &&
        bottom > top &&
        right > 0 &&
        bottom > 0 &&
        left < dimensions.width &&
        top < dimensions.height

private fun RawOcrBoundingBox.centerX(): Double =
    (left + right) / 2.0

private fun RawOcrBoundingBox.centerY(): Double =
    (top + bottom) / 2.0

private fun MatchResultAutoCropEvidence.findExactCandidates(
    expectedText: String,
): List<RawOcrBoundingBox> = observations
    .asSequence()
    .filter { it.text.trim() == expectedText }
    .mapNotNull { it.usableBoundingBoxOrNull(imageDimensions) }
    .toList()

private fun String.looksLikeEliminationText(): Boolean {
    val normalized = trim().lowercase()
    return normalized.contains("elimin") || normalized.contains("eimin")
}

private fun MatchResultAutoCropEvidence.leftmostEliminationBoundaryX(): Int? = observations
    .asSequence()
    .filter { it.text.looksLikeEliminationText() }
    .mapNotNull { it.usableBoundingBoxOrNull(imageDimensions) }
    .map { it.left }
    .minOrNull()

class MatchResultAutoCropAnchorDetector {
    fun findAnchorFour(evidence: MatchResultAutoCropEvidence): RawOcrBoundingBox? =
        findExactLeftPlacementAnchor(evidence, "4")

    fun findAnchorFive(evidence: MatchResultAutoCropEvidence): RawOcrBoundingBox? =
        findExactLeftPlacementAnchor(evidence, "5")

    private fun findExactLeftPlacementAnchor(
        evidence: MatchResultAutoCropEvidence,
        expectedText: String,
    ): RawOcrBoundingBox? {
        val eliminationBoundaryX = evidence.leftmostEliminationBoundaryX() ?: return null
        val minimumGap = evidence.imageDimensions.width * MIN_ANCHOR_TO_ELIMINATION_GAP_WIDTH_FRACTION

        return evidence.findExactCandidates(expectedText)
            .asSequence()
            .filter { candidate ->
                eliminationBoundaryX.toDouble() - candidate.right.toDouble() >= minimumGap
            }
            .minWithOrNull(
                compareBy<RawOcrBoundingBox> { it.left }
                    .thenBy { it.top }
                    .thenBy { it.right }
                    .thenBy { it.bottom },
            )
    }

    private companion object {
        /*
         * Real placement 4/5 boxes were about 13.1%-13.3% of image width away from
         * the first elimination column in the measured 1600x720, 1120x503, and
         * 2400x1080 result screenshots. 10% keeps a deliberate margin for OCR box
         * variation while still rejecting player-name digits and elimination digits.
         */
        const val MIN_ANCHOR_TO_ELIMINATION_GAP_WIDTH_FRACTION = 0.10
    }
}

private data class ResolvedCropGeometry(
    val p5CenterX: Double,
    val p5CenterY: Double,
    val rowPitch: Double,
)

private data class RightColumnRecoveryCandidate(
    val firstPosition: Int,
    val estimatedLeftRowPitch: Double,
    val normalizedKnownAnchorResidual: Double,
    val normalizedHorizontalDelta: Double,
)

class MatchResultAutoCropCalculator(
    private val anchorDetector: MatchResultAutoCropAnchorDetector = MatchResultAutoCropAnchorDetector(),
) {
    fun calculate(evidence: MatchResultAutoCropEvidence): MatchResultAutoCropResult {
        val anchorFour = anchorDetector.findAnchorFour(evidence)
        val anchorFive = anchorDetector.findAnchorFive(evidence)

        val geometry = resolveCropGeometry(
            evidence = evidence,
            anchorFour = anchorFour,
            anchorFive = anchorFive,
        ) ?: return when {
            anchorFour == null -> MatchResultAutoCropResult.AnchorFourMissing
            else -> MatchResultAutoCropResult.AnchorFiveMissing
        }

        val rightBoundary = evidence.observations
            .asSequence()
            .mapNotNull { it.usableBoundingBoxOrNull(evidence.imageDimensions) }
            .map { it.right }
            .maxOrNull()
            ?: return MatchResultAutoCropResult.RightBoundaryMissing

        val rowPitch = geometry.rowPitch
        if (!rowPitch.isFinite() || rowPitch <= 0.0) {
            return MatchResultAutoCropResult.InvalidRowPitch
        }

        // Existing result-crop geometry remains unchanged.
        val leftRaw = geometry.p5CenterX - LEFT_ROW_PITCH_FACTOR * rowPitch
        val topRaw = geometry.p5CenterY - TOP_ROW_PITCH_FACTOR * rowPitch
        val bottomRaw = geometry.p5CenterY + BOTTOM_ROW_PITCH_FACTOR * rowPitch
        if (!leftRaw.isFinite() || !topRaw.isFinite() || !bottomRaw.isFinite()) {
            return MatchResultAutoCropResult.InvalidCalculatedCrop
        }

        val dimensions = evidence.imageDimensions
        val pixelLeft = floor(leftRaw).toInt().coerceIn(0, dimensions.width)
        val pixelTop = floor(topRaw).toInt().coerceIn(0, dimensions.height)
        val pixelRight = ceil(rightBoundary.toDouble()).toInt().coerceIn(0, dimensions.width)
        val pixelBottom = ceil(bottomRaw).toInt().coerceIn(0, dimensions.height)

        if (pixelLeft >= pixelRight || pixelTop >= pixelBottom) {
            return MatchResultAutoCropResult.InvalidCalculatedCrop
        }

        val pixelCrop = runCatching {
            OcrPixelCropRect(
                left = pixelLeft,
                top = pixelTop,
                right = pixelRight,
                bottom = pixelBottom,
            )
        }.getOrElse { return MatchResultAutoCropResult.InvalidCalculatedCrop }

        return MatchResultAutoCropResult.Proposed(
            crop = OcrNormalizedCropRect.fromPixelRect(pixelCrop, dimensions),
        )
    }

    private fun resolveCropGeometry(
        evidence: MatchResultAutoCropEvidence,
        anchorFour: RawOcrBoundingBox?,
        anchorFive: RawOcrBoundingBox?,
    ): ResolvedCropGeometry? {
        // Primary path: identical to the old 4 -> 5 calculation.
        if (anchorFour != null && anchorFive != null) {
            val p4CenterY = anchorFour.centerY()
            val p5CenterX = anchorFive.centerX()
            val p5CenterY = anchorFive.centerY()
            return ResolvedCropGeometry(
                p5CenterX = p5CenterX,
                p5CenterY = p5CenterY,
                rowPitch = p5CenterY - p4CenterY,
            )
        }

        // Recovery requires one genuine left-column anchor. If both are missing,
        // keep the old missing-anchor failure contract and allow the next fallback.
        if (anchorFour == null && anchorFive == null) return null

        if (anchorFour == null && anchorFive != null) {
            val recoveredPitch = findRecoveredLeftRowPitch(
                evidence = evidence,
                knownLeftPosition = 5,
                knownLeftCenterY = anchorFive.centerY(),
            ) ?: return null

            return ResolvedCropGeometry(
                p5CenterX = anchorFive.centerX(),
                p5CenterY = anchorFive.centerY(),
                rowPitch = recoveredPitch,
            )
        }

        val realAnchorFour = anchorFour ?: return null
        val recoveredPitch = findRecoveredLeftRowPitch(
            evidence = evidence,
            knownLeftPosition = 4,
            knownLeftCenterY = realAnchorFour.centerY(),
        ) ?: return null

        return ResolvedCropGeometry(
            // 4 and 5 are in the same left-side placement-number column.
            p5CenterX = realAnchorFour.centerX(),
            p5CenterY = realAnchorFour.centerY() + recoveredPitch,
            rowPitch = recoveredPitch,
        )
    }

    private fun findRecoveredLeftRowPitch(
        evidence: MatchResultAutoCropEvidence,
        knownLeftPosition: Int,
        knownLeftCenterY: Double,
    ): Double? {
        val dimensions = evidence.imageDimensions
        val maxHorizontalDelta =
            dimensions.width * MAX_RIGHT_PAIR_HORIZONTAL_DELTA_FRACTION
        val candidates = mutableListOf<RightColumnRecoveryCandidate>()

        for (firstPosition in 6..10) {
            val secondPosition = firstPosition + 1
            val firstCandidates = evidence.findExactCandidates(firstPosition.toString())
            val secondCandidates = evidence.findExactCandidates(secondPosition.toString())

            for (firstBox in firstCandidates) {
                for (secondBox in secondCandidates) {
                    val horizontalDelta = abs(secondBox.centerX() - firstBox.centerX())
                    if (!horizontalDelta.isFinite() || horizontalDelta > maxHorizontalDelta) continue

                    val rightRowPitch = secondBox.centerY() - firstBox.centerY()
                    if (!rightRowPitch.isFinite() || rightRowPitch <= 0.0) continue

                    val estimatedLeftRowPitch =
                        rightRowPitch * LEFT_TO_RIGHT_ROW_PITCH_RATIO
                    if (!estimatedLeftRowPitch.isFinite() || estimatedLeftRowPitch <= 0.0) continue

                    val estimatedPositionSixCenterY =
                        firstBox.centerY() - (firstPosition - 6) * rightRowPitch
                    val expectedKnownLeftCenterY =
                        estimatedPositionSixCenterY +
                            (knownLeftPosition - 1) * estimatedLeftRowPitch
                    val knownAnchorResidual =
                        abs(expectedKnownLeftCenterY - knownLeftCenterY)
                    val maxKnownAnchorResidual =
                        estimatedLeftRowPitch * MAX_KNOWN_ANCHOR_RESIDUAL_TO_LEFT_PITCH_RATIO

                    if (
                        !knownAnchorResidual.isFinite() ||
                        knownAnchorResidual > maxKnownAnchorResidual
                    ) {
                        continue
                    }

                    candidates += RightColumnRecoveryCandidate(
                        firstPosition = firstPosition,
                        estimatedLeftRowPitch = estimatedLeftRowPitch,
                        normalizedKnownAnchorResidual =
                            knownAnchorResidual / estimatedLeftRowPitch,
                        normalizedHorizontalDelta = horizontalDelta / dimensions.width,
                    )
                }
            }
        }

        return candidates
            .minWithOrNull(
                compareBy<RightColumnRecoveryCandidate> { it.normalizedKnownAnchorResidual }
                    .thenBy { it.normalizedHorizontalDelta }
                    .thenBy { it.firstPosition },
            )
            ?.estimatedLeftRowPitch
    }

    private companion object {
        // Existing crop constants: unchanged.
        const val LEFT_ROW_PITCH_FACTOR = 0.45
        const val TOP_ROW_PITCH_FACTOR = 4.5
        const val BOTTOM_ROW_PITCH_FACTOR = 0.5

        // Measured dimensionless left-column/right-column pitch relationship.
        const val LEFT_TO_RIGHT_ROW_PITCH_RATIO = 1.172

        // Recovery validation is normalized so it scales with screenshot dimensions.
        const val MAX_RIGHT_PAIR_HORIZONTAL_DELTA_FRACTION = 0.02
        const val MAX_KNOWN_ANCHOR_RESIDUAL_TO_LEFT_PITCH_RATIO = 0.12
    }
}
