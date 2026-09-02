package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropEvidence
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculationResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculator
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import kotlin.math.abs

sealed interface MatchResultSemanticRoleResolution {
    data class Resolved(
        val role: MatchResultScreenshotRole,
        val geometry: MatchResultPositionCropCalculationResult.Available,
    ) : MatchResultSemanticRoleResolution

    data object Unresolved : MatchResultSemanticRoleResolution
    data object Ambiguous : MatchResultSemanticRoleResolution
}

/** Resolves Result role from one already-collected ML Kit evidence set. */
class MatchResultSemanticRoleResolver(
    private val calculator: MatchResultPositionCropCalculator = MatchResultPositionCropCalculator(),
) {
    fun resolve(evidence: MatchResultAutoCropEvidence): MatchResultSemanticRoleResolution {
        val candidates = MatchResultScreenshotRole.entries.mapNotNull { role ->
            val geometry = calculateStrictGeometry(evidence, role) ?: return@mapNotNull null
            role to geometry
        }
        return when (candidates.size) {
            1 -> {
                val (role, geometry) = candidates.single()
                MatchResultSemanticRoleResolution.Resolved(role, geometry)
            }
            0 -> MatchResultSemanticRoleResolution.Unresolved
            else -> MatchResultSemanticRoleResolution.Ambiguous
        }
    }

    private fun calculateStrictGeometry(
        evidence: MatchResultAutoCropEvidence,
        role: MatchResultScreenshotRole,
    ): MatchResultPositionCropCalculationResult.Available? {
        if (!hasRoleEvidence(evidence, role)) return null
        val result = calculator.calculate(
            evidence = evidence,
            role = role,
            // Position 11 may never make a lower screenshot look upper.
            allowUpperPositionElevenFallback = false,
        ) as? MatchResultPositionCropCalculationResult.Available ?: return null
        val expected = when (role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> (1..10).toList()
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> (11..12).toList()
        }
        return result.takeIf { geometry -> geometry.crops.map { it.position } == expected }
    }

    private fun hasRoleEvidence(
        evidence: MatchResultAutoCropEvidence,
        role: MatchResultScreenshotRole,
    ): Boolean {
        val exactTexts = evidence.observations.map { it.text.trim() }.toSet()
        return when (role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER ->
                // Existing upper recovery allows either left anchor when the other is missed.
                exactTexts.contains("4") || exactTexts.contains("5")
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> hasLowerRoleEvidence(evidence, exactTexts)
        }
    }

    private fun hasLowerRoleEvidence(
        evidence: MatchResultAutoCropEvidence,
        exactTexts: Set<String>,
    ): Boolean {
        // Preserve the original strict path before considering any tolerant fallback.
        if (exactTexts.containsAll(setOf("11", "12"))) return true

        val anchors = resolveRightPlacementAnchors(evidence)
        if (anchors.size < MIN_PLACEMENT_ANCHOR_COUNT) return false

        val exactTwelve = evidence.observations
            .asSequence()
            .filter { it.text.trim() == "12" }
            .mapNotNull { observation ->
                observation.boundingBox
                    ?.takeIf { it.isUsableFor(evidence) }
            }
            .filter { it.isPlacementColumnAligned(anchors, evidence) }
            .filter { it.isPlacementSizedLike(anchors) }
            .toList()
        if (exactTwelve.size == 1) return true
        if (exactTwelve.size > 1) return false

        return hasMisreadPositionTwelveEvidence(evidence, anchors)
    }

    private fun hasMisreadPositionTwelveEvidence(
        evidence: MatchResultAutoCropEvidence,
        anchors: List<PlacementAnchor>,
    ): Boolean {
        val sortedAnchors = anchors.sortedBy { it.position }
        if (sortedAnchors.zipWithNext().any { (first, second) ->
                second.box.centerY() <= first.box.centerY()
            }) {
            return false
        }

        val rowPitch = median(
            sortedAnchors.flatMapIndexed { firstIndex, first ->
                sortedAnchors.drop(firstIndex + 1).mapNotNull { second ->
                    val positionGap = second.position - first.position
                    if (positionGap <= 0) return@mapNotNull null
                    val pitch = (second.box.centerY() - first.box.centerY()) / positionGap
                    pitch.takeIf { it.isFinite() && it > 0.0 }
                }
            },
        ) ?: return false
        if (!isUsableRowPitch(rowPitch, evidence)) return false

        val referencePosition = sortedAnchors.first().position
        val referenceCenterY = median(
            sortedAnchors.map { anchor ->
                anchor.box.centerY() - (anchor.position - referencePosition) * rowPitch
            },
        ) ?: return false
        val maxModelResidual = rowPitch * MAX_ROW_MODEL_RESIDUAL_PITCH_FRACTION
        if (sortedAnchors.any { anchor ->
                abs(
                    anchor.box.centerY() -
                        (referenceCenterY + (anchor.position - referencePosition) * rowPitch),
                ) > maxModelResidual
            }) {
            return false
        }

        val expectedCenterY = referenceCenterY + (12 - referencePosition) * rowPitch
        if (!expectedCenterY.isFinite() || expectedCenterY !in 0.0..evidence.imageDimensions.height.toDouble()) {
            return false
        }

        val candidates = evidence.observations
            .asSequence()
            .filter { observation ->
                val text = observation.text.trim()
                text.codePointCount(0, text.length) in MIN_MISREAD_TEXT_CODE_POINTS..MAX_MISREAD_TEXT_CODE_POINTS &&
                    text != "12" &&
                    text.toIntOrNull()?.let { it in 6..12 } != true
            }
            .mapNotNull { observation ->
                observation.boundingBox
                    ?.takeIf { it.isUsableFor(evidence) }
            }
            .filter { it.isPlacementColumnAligned(anchors, evidence) }
            .filter { it.isPlacementSizedLike(anchors) }
            .filter {
                abs(it.centerY() - expectedCenterY) <= maxModelResidual
            }
            .toList()

        return candidates.size == 1
    }

    private fun resolveRightPlacementAnchors(
        evidence: MatchResultAutoCropEvidence,
    ): List<PlacementAnchor> {
        val dimensions = evidence.imageDimensions
        val eliminationBoxes = evidence.observations
            .asSequence()
            .filter { observation ->
                val normalized = observation.text.trim().lowercase()
                normalized.contains("elimin") || normalized.contains("eimin")
            }
            .mapNotNull { it.boundingBox?.takeIf { box -> box.isUsableFor(evidence) } }
            .toList()
        val eliminationClusters = clusterByCenterX(
            boxes = eliminationBoxes,
            tolerance = dimensions.width * ELIMINATION_COLUMN_CLUSTER_FRACTION,
            centerX = { it.centerX() },
        )
        val secondLeftBoundary = eliminationClusters.getOrNull(1)?.maxOfOrNull { it.right }
            ?: return emptyList()
        val boundaryTolerance = dimensions.width * RIGHT_PLACEMENT_BOUNDARY_TOLERANCE_FRACTION
        val maxGap = dimensions.width * MAX_RIGHT_PLACEMENT_GAP_FROM_LEFT_COLUMN_FRACTION
        val candidates = (6..11).flatMap { position ->
            evidence.observations
                .asSequence()
                .filter { it.text.trim() == position.toString() }
                .mapNotNull { it.boundingBox?.takeIf { box -> box.isUsableFor(evidence) } }
                .map { PlacementAnchor(position, it) }
                .toList()
        }.filter { candidate ->
            val centerX = candidate.box.centerX()
            centerX >= secondLeftBoundary.toDouble() - boundaryTolerance &&
                centerX - secondLeftBoundary.toDouble() <= maxGap
        }
        if (candidates.isEmpty()) return emptyList()

        val selectedCluster = clusterByCenterX(
            boxes = candidates,
            tolerance = dimensions.width * RIGHT_PLACEMENT_COLUMN_CLUSTER_FRACTION,
            centerX = { it.box.centerX() },
        ).minWithOrNull(
            compareByDescending<List<PlacementAnchor>> { cluster -> cluster.map { it.position }.distinct().size }
                .thenBy { cluster -> cluster.map { it.box.centerX() }.average() },
        ) ?: return emptyList()

        return selectedCluster
            .groupBy { it.position }
            .mapNotNull { (_, samePosition) ->
                samePosition.maxWithOrNull(
                    compareBy<PlacementAnchor> { it.box.height() }
                        .thenBy { it.box.width() }
                        .thenBy { -it.box.top },
                )
            }
    }

    private fun RawOcrBoundingBox.isPlacementColumnAligned(
        anchors: List<PlacementAnchor>,
        evidence: MatchResultAutoCropEvidence,
    ): Boolean {
        val anchorCenterX = median(anchors.map { it.box.centerX() }) ?: return false
        return abs(centerX() - anchorCenterX) <=
            evidence.imageDimensions.width * RIGHT_PLACEMENT_COLUMN_CLUSTER_FRACTION
    }

    private fun RawOcrBoundingBox.isPlacementSizedLike(
        anchors: List<PlacementAnchor>,
    ): Boolean {
        val medianWidth = median(anchors.map { it.box.width().toDouble() }) ?: return false
        val medianHeight = median(anchors.map { it.box.height().toDouble() }) ?: return false
        return width().toDouble() in medianWidth * MIN_PLACEMENT_SIZE_RATIO..medianWidth * MAX_PLACEMENT_SIZE_RATIO &&
            height().toDouble() in medianHeight * MIN_PLACEMENT_SIZE_RATIO..medianHeight * MAX_PLACEMENT_SIZE_RATIO
    }

    private fun RawOcrBoundingBox.isUsableFor(
        evidence: MatchResultAutoCropEvidence,
    ): Boolean = right > left &&
        bottom > top &&
        right > 0 &&
        bottom > 0 &&
        left < evidence.imageDimensions.width &&
        top < evidence.imageDimensions.height

    private fun <T> clusterByCenterX(
        boxes: List<T>,
        tolerance: Double,
        centerX: (T) -> Double,
    ): List<List<T>> {
        val groups = mutableListOf<MutableList<T>>()
        boxes.sortedBy(centerX).forEach { candidate ->
            val last = groups.lastOrNull()
            val lastCenter = last?.map(centerX)?.average()
            if (last != null && lastCenter != null && abs(centerX(candidate) - lastCenter) <= tolerance) {
                last += candidate
            } else {
                groups += mutableListOf(candidate)
            }
        }
        return groups
    }

    private fun median(values: List<Double>): Double? {
        val sorted = values.filter { it.isFinite() }.sorted()
        if (sorted.isEmpty()) return null
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    private fun isUsableRowPitch(
        pitch: Double,
        evidence: MatchResultAutoCropEvidence,
    ): Boolean = pitch >= evidence.imageDimensions.height * MIN_ROW_PITCH_HEIGHT_FRACTION &&
        pitch <= evidence.imageDimensions.height * MAX_ROW_PITCH_HEIGHT_FRACTION

    private data class PlacementAnchor(
        val position: Int,
        val box: RawOcrBoundingBox,
    )

    private fun RawOcrBoundingBox.centerX(): Double = (left + right) / 2.0
    private fun RawOcrBoundingBox.centerY(): Double = (top + bottom) / 2.0
    private fun RawOcrBoundingBox.width(): Int = right - left
    private fun RawOcrBoundingBox.height(): Int = bottom - top

    private companion object {
        const val MIN_PLACEMENT_ANCHOR_COUNT = 2
        const val RIGHT_PLACEMENT_BOUNDARY_TOLERANCE_FRACTION = 0.02
        const val MAX_RIGHT_PLACEMENT_GAP_FROM_LEFT_COLUMN_FRACTION = 0.12
        const val RIGHT_PLACEMENT_COLUMN_CLUSTER_FRACTION = 0.025
        const val ELIMINATION_COLUMN_CLUSTER_FRACTION = 0.05
        const val MIN_ROW_PITCH_HEIGHT_FRACTION = 0.03
        const val MAX_ROW_PITCH_HEIGHT_FRACTION = 0.30
        const val MAX_ROW_MODEL_RESIDUAL_PITCH_FRACTION = 0.35
        const val MIN_MISREAD_TEXT_CODE_POINTS = 1
        const val MAX_MISREAD_TEXT_CODE_POINTS = 3
        const val MIN_PLACEMENT_SIZE_RATIO = 0.50
        const val MAX_PLACEMENT_SIZE_RATIO = 2.0
    }
}
