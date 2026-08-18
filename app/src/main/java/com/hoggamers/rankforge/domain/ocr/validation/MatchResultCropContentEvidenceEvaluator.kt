package com.hoggamers.rankforge.domain.ocr.validation

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrCanonicalField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrCanonicalLayout
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrCanonicalLayouts
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrVisualRow
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Measures role-aware structural OCR evidence without applying production acceptance thresholds.
 *
 * The evaluator consumes the same raw block/element geometry that already feeds match-result OCR,
 * but it does not call the downstream field extractor and it never treats parser output as proof
 * that a crop is correct.
 */
class MatchResultCropContentEvidenceEvaluator {
    fun evaluate(
        role: MatchResultScreenshotRole,
        cropWidth: Int,
        cropHeight: Int,
        blocks: List<RawOcrBlock>,
    ): MatchResultCropContentEvidence {
        require(cropWidth > 0) { "Crop width must be positive." }
        require(cropHeight > 0) { "Crop height must be positive." }

        val layout = when (role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> MatchResultOcrCanonicalLayouts.upper
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> MatchResultOcrCanonicalLayouts.lower
        }
        val observations = flattenObservations(blocks, cropWidth, cropHeight)
        val playerObservations = observations.filter(Observation::isPlayerLike)
        val killObservations = observations.filter(Observation::isKillLike)

        return MatchResultCropContentEvidence(
            role = role,
            cropWidth = cropWidth,
            cropHeight = cropHeight,
            nonBlankObservationCount = observations.size,
            playerLikeObservationCount = playerObservations.size,
            killLikeObservationCount = killObservations.size,
            placementEvidence = placementFields(role, layout).map { (expectedPlacement, field) ->
                val expected = field.rect.toNormalized(layout)
                val matching = observations.filter { it.parsedInteger == expectedPlacement }
                MatchResultCropPlacementEvidence(
                    expectedPlacement = expectedPlacement,
                    matchingCandidateCount = matching.size,
                    minimumNormalizedCenterDistance = matching.minOfOrNull {
                        expected.centerDistanceTo(it.rect)
                    },
                )
            },
            playerFieldEvidence = layout.fields
                .filter { it.type == MatchResultOcrFieldType.PLAYER }
                .map { field -> fieldEvidence(field, layout, playerObservations) },
            killFieldEvidence = layout.fields
                .filter { it.type == MatchResultOcrFieldType.KILL }
                .map { field -> fieldEvidence(field, layout, killObservations) },
            spatialDistribution = spatialDistribution(observations),
        )
    }

    private fun placementFields(
        role: MatchResultScreenshotRole,
        layout: MatchResultOcrCanonicalLayout,
    ): List<Pair<Int, MatchResultOcrCanonicalField>> = when (role) {
        MatchResultScreenshotRole.MATCH_RESULT_UPPER -> layout.fields
            .filter { it.type == MatchResultOcrFieldType.PLACEMENT }
            .map { field -> requireNotNull(field.position) to field }
            .sortedBy { it.first }

        MatchResultScreenshotRole.MATCH_RESULT_LOWER -> {
            val expectedByVisualRow = mapOf(
                MatchResultOcrVisualRow.A to 11,
                MatchResultOcrVisualRow.B to 12,
            )
            layout.fields
                .filter { it.type == MatchResultOcrFieldType.PLACEMENT }
                .map { field -> expectedByVisualRow.getValue(requireNotNull(field.visualRow)) to field }
                .sortedBy { it.first }
        }
    }

    private fun fieldEvidence(
        field: MatchResultOcrCanonicalField,
        layout: MatchResultOcrCanonicalLayout,
        candidates: List<Observation>,
    ): MatchResultCropFieldEvidence {
        val expected = field.rect.toNormalized(layout)
        return MatchResultCropFieldEvidence(
            fieldId = field.id,
            fieldType = field.type,
            maximumExpectedRegionCoverageRatio = candidates.maxOfOrNull { observation ->
                expected.intersectionArea(observation.rect) / expected.area
            } ?: 0.0,
            maximumObservationContainmentRatio = candidates.maxOfOrNull { observation ->
                expected.intersectionArea(observation.rect) / observation.rect.area
            } ?: 0.0,
            minimumNormalizedCenterDistance = candidates.minOfOrNull { observation ->
                expected.centerDistanceTo(observation.rect)
            },
        )
    }

    private fun flattenObservations(
        blocks: List<RawOcrBlock>,
        cropWidth: Int,
        cropHeight: Int,
    ): List<Observation> = buildList {
        blocks.forEach { block ->
            block.lines.forEach { line ->
                line.elements.forEach { element ->
                    val text = element.text.trim()
                    if (text.isBlank()) return@forEach

                    val pixelRect = element.geometry.toPixelRectOrNull()
                        ?: element.symbols
                            .mapNotNull { symbol -> symbol.geometry.toPixelRectOrNull() }
                            .boundingRectOrNull()
                        ?: return@forEach
                    val normalized = pixelRect.toNormalizedOrNull(cropWidth, cropHeight)
                        ?: return@forEach
                    add(Observation(text = text, rect = normalized))
                }
            }
        }
    }

    private fun spatialDistribution(observations: List<Observation>): MatchResultCropSpatialDistribution {
        val horizontal = MutableList(EVIDENCE_BAND_COUNT) { 0 }
        val vertical = MutableList(EVIDENCE_BAND_COUNT) { 0 }
        observations.forEach { observation ->
            horizontal[bandIndex(observation.rect.centerX)]++
            vertical[bandIndex(observation.rect.centerY)]++
        }
        return MatchResultCropSpatialDistribution(
            horizontalBandCounts = horizontal,
            verticalBandCounts = vertical,
        )
    }

    private fun bandIndex(value: Double): Int =
        floor(value * EVIDENCE_BAND_COUNT).toInt().coerceIn(0, EVIDENCE_BAND_COUNT - 1)

    private data class Observation(
        val text: String,
        val rect: NormalizedRect,
    ) {
        val parsedInteger: Int?
            get() = text.toIntOrNull()

        val isPlayerLike: Boolean
            get() = text.any(Char::isLetter)

        val isKillLike: Boolean
            get() = text.isNotEmpty() && text.all { it.isDigit() || it == 'O' || it == 'o' }
    }

    private data class PixelRect(
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double,
    )

    private data class NormalizedRect(
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double,
    ) {
        val area: Double
            get() = (right - left) * (bottom - top)
        val centerX: Double
            get() = (left + right) / 2.0
        val centerY: Double
            get() = (top + bottom) / 2.0

        fun intersectionArea(other: NormalizedRect): Double {
            val overlapWidth = (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0.0)
            val overlapHeight = (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0.0)
            return overlapWidth * overlapHeight
        }

        fun centerDistanceTo(other: NormalizedRect): Double {
            val dx = centerX - other.centerX
            val dy = centerY - other.centerY
            return sqrt(dx * dx + dy * dy)
        }
    }

    private fun com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect.toNormalized(
        layout: MatchResultOcrCanonicalLayout,
    ): NormalizedRect = NormalizedRect(
        left = left / layout.width,
        top = top / layout.height,
        right = right / layout.width,
        bottom = bottom / layout.height,
    )

    private fun RawOcrGeometry?.toPixelRectOrNull(): PixelRect? {
        val box = this?.boundingBox
        if (box != null) return box.toPixelRectOrNull()
        val points = this?.cornerPoints.orEmpty()
        if (points.isEmpty()) return null
        val rect = PixelRect(
            left = points.minOf { it.x }.toDouble(),
            top = points.minOf { it.y }.toDouble(),
            right = points.maxOf { it.x }.toDouble(),
            bottom = points.maxOf { it.y }.toDouble(),
        )
        return rect.takeIf { it.right > it.left && it.bottom > it.top }
    }

    private fun RawOcrBoundingBox.toPixelRectOrNull(): PixelRect? = PixelRect(
        left = left.toDouble(),
        top = top.toDouble(),
        right = right.toDouble(),
        bottom = bottom.toDouble(),
    ).takeIf { it.right > it.left && it.bottom > it.top }

    private fun List<PixelRect>.boundingRectOrNull(): PixelRect? =
        if (isEmpty()) {
            null
        } else {
            PixelRect(
                left = minOf { it.left },
                top = minOf { it.top },
                right = maxOf { it.right },
                bottom = maxOf { it.bottom },
            )
        }

    private fun PixelRect.toNormalizedOrNull(cropWidth: Int, cropHeight: Int): NormalizedRect? {
        val clippedLeft = left.coerceIn(0.0, cropWidth.toDouble())
        val clippedTop = top.coerceIn(0.0, cropHeight.toDouble())
        val clippedRight = right.coerceIn(0.0, cropWidth.toDouble())
        val clippedBottom = bottom.coerceIn(0.0, cropHeight.toDouble())
        if (clippedRight <= clippedLeft || clippedBottom <= clippedTop) return null
        return NormalizedRect(
            left = clippedLeft / cropWidth,
            top = clippedTop / cropHeight,
            right = clippedRight / cropWidth,
            bottom = clippedBottom / cropHeight,
        )
    }
}
