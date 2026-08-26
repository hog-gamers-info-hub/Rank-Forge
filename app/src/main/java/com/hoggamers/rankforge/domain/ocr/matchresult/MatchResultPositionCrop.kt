package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Phase 1 position-crop geometry only. This class deliberately does not invoke or depend on
 * the existing result OCR parsing pipeline.
 */
enum class MatchResultPositionColumn {
    LEFT,
    RIGHT,
}

enum class MatchResultPositionPitchSource {
    LEFT_FOUR_TO_FIVE,
    RIGHT_CONSECUTIVE,
    RIGHT_NON_CONSECUTIVE,
    RECOVERED_FROM_RIGHT,
    RECOVERED_FROM_LEFT,
}

data class MatchResultPositionCrop(
    val position: Int,
    val column: MatchResultPositionColumn,
    val bounds: OcrPixelCropRect,
) {
    init {
        require(position in 1..12) { "Result position must be in 1..12." }
    }
}

enum class MatchResultPositionCropUnavailableReason {
    ELIMINATION_GEOMETRY_UNAVAILABLE,
    LEFT_POSITION_ANCHOR_UNAVAILABLE,
    LEFT_ROW_PITCH_UNAVAILABLE,
    RIGHT_POSITION_ANCHOR_UNAVAILABLE,
    RIGHT_ROW_PITCH_UNAVAILABLE,
    LEFT_COLUMN_BOUNDARY_UNAVAILABLE,
    RIGHT_COLUMN_BOUNDARY_UNAVAILABLE,
    POSITION_RECT_OUT_OF_BOUNDS,
}

sealed interface MatchResultPositionCropCalculationResult {
    data class Available(
        val crops: List<MatchResultPositionCrop>,
        val leftRowPitch: Double?,
        val rightRowPitch: Double,
        val leftPitchSource: MatchResultPositionPitchSource?,
        val rightPitchSource: MatchResultPositionPitchSource,
    ) : MatchResultPositionCropCalculationResult

    data class Unavailable(
        val reason: MatchResultPositionCropUnavailableReason,
    ) : MatchResultPositionCropCalculationResult
}

private data class PositionedBox(
    val position: Int,
    val box: RawOcrBoundingBox,
)

private data class PitchResolution(
    val pitch: Double,
    val source: MatchResultPositionPitchSource,
)

private data class RightPitchCandidate(
    val pitch: Double,
    val source: MatchResultPositionPitchSource,
    val horizontalDelta: Double,
    val positionGap: Int,
)

private data class EliminationColumnCluster(
    val boxes: List<RawOcrBoundingBox>,
) {
    val centerX: Double = boxes.map { it.centerX() }.average()
    val right: Int = boxes.maxOf { it.right }
}

class MatchResultPositionCropCalculator(
    private val anchorDetector: MatchResultAutoCropAnchorDetector = MatchResultAutoCropAnchorDetector(),
) {
    fun calculate(
        evidence: MatchResultAutoCropEvidence,
        role: MatchResultScreenshotRole,
        allowUpperPositionElevenFallback: Boolean = false,
    ): MatchResultPositionCropCalculationResult {
        val dimensions = evidence.imageDimensions

        val eliminationBoxes = evidence.eliminationBoxes()
        val eliminationClusters = clusterEliminationColumns(
            boxes = eliminationBoxes,
            imageWidth = dimensions.width,
        )
        if (eliminationClusters.size < 2) {
            return unavailable(MatchResultPositionCropUnavailableReason.ELIMINATION_GEOMETRY_UNAVAILABLE)
        }

        val provisionalSecondLeftBoundary = eliminationClusters[1].right
        val anchorFour = anchorDetector.findAnchorFour(evidence)
        val anchorFive = anchorDetector.findAnchorFive(evidence)
        val rightAnchors = resolveRightPlacementAnchors(
            evidence = evidence,
            secondLeftEliminationBoundary = provisionalSecondLeftBoundary,
        )
        if (rightAnchors.isEmpty()) {
            return unavailable(MatchResultPositionCropUnavailableReason.RIGHT_POSITION_ANCHOR_UNAVAILABLE)
        }

        val directLeftPitch = resolveDirectLeftPitch(
            anchorFour = anchorFour,
            anchorFive = anchorFive,
            imageWidth = dimensions.width,
            imageHeight = dimensions.height,
        )
        val detectedRightPitch = resolveRightPitchFromAnchors(
            anchors = rightAnchors,
            imageWidth = dimensions.width,
            imageHeight = dimensions.height,
        )

        val leftPitch = directLeftPitch ?: if (
            (anchorFour != null || anchorFive != null) && detectedRightPitch != null
        ) {
            PitchResolution(
                pitch = detectedRightPitch.pitch * LEFT_TO_RIGHT_ROW_PITCH_RATIO,
                source = MatchResultPositionPitchSource.RECOVERED_FROM_RIGHT,
            )
        } else {
            null
        }

        val rightPitch = detectedRightPitch ?: directLeftPitch?.let {
            PitchResolution(
                pitch = it.pitch / LEFT_TO_RIGHT_ROW_PITCH_RATIO,
                source = MatchResultPositionPitchSource.RECOVERED_FROM_LEFT,
            )
        }

        if (rightPitch == null || !isUsablePitch(rightPitch.pitch, dimensions.height)) {
            return unavailable(MatchResultPositionCropUnavailableReason.RIGHT_ROW_PITCH_UNAVAILABLE)
        }

        if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
            if (anchorFour == null && anchorFive == null) {
                return unavailable(MatchResultPositionCropUnavailableReason.LEFT_POSITION_ANCHOR_UNAVAILABLE)
            }
            if (leftPitch == null || !isUsablePitch(leftPitch.pitch, dimensions.height)) {
                return unavailable(MatchResultPositionCropUnavailableReason.LEFT_ROW_PITCH_UNAVAILABLE)
            }
        }

        val rightPlacementLeft = rightAnchors.minOf { it.box.left }
        val leftEliminationClusters = eliminationClusters
            .filter { it.centerX < rightPlacementLeft.toDouble() }
            .sortedBy { it.centerX }
        val leftBoundaryRight = leftEliminationClusters.getOrNull(1)?.right

        if (
            role == MatchResultScreenshotRole.MATCH_RESULT_UPPER &&
            leftBoundaryRight == null
        ) {
            return unavailable(MatchResultPositionCropUnavailableReason.LEFT_COLUMN_BOUNDARY_UNAVAILABLE)
        }

        val rightBoundaryRight = eliminationBoxes
            .asSequence()
            .filter { it.centerX() > rightPlacementLeft.toDouble() }
            .map { it.right }
            .maxOrNull()
            ?: return unavailable(MatchResultPositionCropUnavailableReason.RIGHT_COLUMN_BOUNDARY_UNAVAILABLE)

        if (rightBoundaryRight <= rightPlacementLeft) {
            return unavailable(MatchResultPositionCropUnavailableReason.RIGHT_COLUMN_BOUNDARY_UNAVAILABLE)
        }

        val crops = mutableListOf<MatchResultPositionCrop>()
        if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
            val resolvedLeftPitch = requireNotNull(leftPitch)
            val p5CenterY = when {
                anchorFive != null -> anchorFive.centerY()
                anchorFour != null -> anchorFour.centerY() + resolvedLeftPitch.pitch
                else -> return unavailable(MatchResultPositionCropUnavailableReason.LEFT_POSITION_ANCHOR_UNAVAILABLE)
            }
            val leftBoundaryLeft = listOfNotNull(anchorFour?.left, anchorFive?.left).minOrNull()
                ?: return unavailable(MatchResultPositionCropUnavailableReason.LEFT_POSITION_ANCHOR_UNAVAILABLE)
            val resolvedLeftBoundaryRight = requireNotNull(leftBoundaryRight)
            if (resolvedLeftBoundaryRight <= leftBoundaryLeft) {
                return unavailable(MatchResultPositionCropUnavailableReason.LEFT_COLUMN_BOUNDARY_UNAVAILABLE)
            }

            val leftCrops = buildColumnCrops(
                positions = 1..5,
                column = MatchResultPositionColumn.LEFT,
                left = withPlacementLeftPadding(leftBoundaryLeft, dimensions.width),
                right = resolvedLeftBoundaryRight,
                referencePosition = 5,
                referenceCenterY = p5CenterY,
                rowPitch = resolvedLeftPitch.pitch,
                imageWidth = dimensions.width,
                imageHeight = dimensions.height,
            ) ?: return unavailable(MatchResultPositionCropUnavailableReason.POSITION_RECT_OUT_OF_BOUNDS)
            crops += leftCrops
        }

        val rightReferenceCenterAtSix = median(
            rightAnchors.map { anchor ->
                anchor.box.centerY() - (anchor.position - 6) * rightPitch.pitch
            },
        ) ?: return unavailable(MatchResultPositionCropUnavailableReason.RIGHT_POSITION_ANCHOR_UNAVAILABLE)

        val hasUpperPositionElevenFallback =
            role == MatchResultScreenshotRole.MATCH_RESULT_UPPER &&
                allowUpperPositionElevenFallback &&
                rightAnchors.any { it.position == 11 }
        val rightPositions = when (role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> 6..10
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> 11..12
        }
        val rightCrops = buildColumnCrops(
            positions = rightPositions,
            column = MatchResultPositionColumn.RIGHT,
            left = withPlacementLeftPadding(rightPlacementLeft, dimensions.width),
            right = rightBoundaryRight,
            referencePosition = 6,
            referenceCenterY = rightReferenceCenterAtSix,
            rowPitch = rightPitch.pitch,
            imageWidth = dimensions.width,
            imageHeight = dimensions.height,
        ) ?: return unavailable(MatchResultPositionCropUnavailableReason.POSITION_RECT_OUT_OF_BOUNDS)
        crops += rightCrops

        if (hasUpperPositionElevenFallback) {
            buildColumnCrops(
                positions = 11..11,
                column = MatchResultPositionColumn.RIGHT,
                left = withPlacementLeftPadding(rightPlacementLeft, dimensions.width),
                right = rightBoundaryRight,
                referencePosition = 6,
                referenceCenterY = rightReferenceCenterAtSix,
                rowPitch = rightPitch.pitch,
                imageWidth = dimensions.width,
                imageHeight = dimensions.height,
            )?.let(crops::addAll)
        }

        val expectedPositions = when (role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER ->
                if (hasUpperPositionElevenFallback && crops.any { it.position == 11 }) {
                    (1..11).toList()
                } else {
                    (1..10).toList()
                }
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> (11..12).toList()
        }
        if (crops.map { it.position } != expectedPositions) {
            return unavailable(MatchResultPositionCropUnavailableReason.POSITION_RECT_OUT_OF_BOUNDS)
        }

        return MatchResultPositionCropCalculationResult.Available(
            crops = crops,
            leftRowPitch = leftPitch?.pitch,
            rightRowPitch = rightPitch.pitch,
            leftPitchSource = leftPitch?.source,
            rightPitchSource = rightPitch.source,
        )
    }

    private fun resolveDirectLeftPitch(
        anchorFour: RawOcrBoundingBox?,
        anchorFive: RawOcrBoundingBox?,
        imageWidth: Int,
        imageHeight: Int,
    ): PitchResolution? {
        if (anchorFour == null || anchorFive == null) return null
        val horizontalDelta = abs(anchorFive.centerX() - anchorFour.centerX())
        if (horizontalDelta > imageWidth * MAX_PLACEMENT_COLUMN_HORIZONTAL_DELTA_FRACTION) return null
        val pitch = anchorFive.centerY() - anchorFour.centerY()
        if (!isUsablePitch(pitch, imageHeight)) return null
        return PitchResolution(
            pitch = pitch,
            source = MatchResultPositionPitchSource.LEFT_FOUR_TO_FIVE,
        )
    }

    private fun resolveRightPlacementAnchors(
        evidence: MatchResultAutoCropEvidence,
        secondLeftEliminationBoundary: Int,
    ): List<PositionedBox> {
        val width = evidence.imageDimensions.width
        val boundaryTolerance = width * RIGHT_PLACEMENT_BOUNDARY_TOLERANCE_FRACTION
        val maxGap = width * MAX_RIGHT_PLACEMENT_GAP_FROM_LEFT_COLUMN_FRACTION
        val candidates = (6..12).flatMap { position ->
            evidence.exactBoxes(position.toString()).map { box -> PositionedBox(position, box) }
        }.filter { candidate ->
            val centerX = candidate.box.centerX()
            centerX >= secondLeftEliminationBoundary - boundaryTolerance &&
                centerX - secondLeftEliminationBoundary <= maxGap
        }
        if (candidates.isEmpty()) return emptyList()

        val clusters = clusterPositionCandidates(
            candidates = candidates,
            tolerance = width * RIGHT_PLACEMENT_COLUMN_CLUSTER_FRACTION,
        )
        val selectedCluster = clusters.minWithOrNull(
            compareByDescending<List<PositionedBox>> { cluster -> cluster.map { it.position }.distinct().size }
                .thenBy { cluster -> cluster.map { it.box.centerX() }.average() },
        ) ?: return emptyList()

        return selectedCluster
            .groupBy { it.position }
            .map { (_, samePosition) ->
                samePosition.maxWithOrNull(
                    compareBy<PositionedBox> { it.box.height() }
                        .thenBy { it.box.width() }
                        .thenBy { -it.box.top },
                )!!
            }
            .sortedBy { it.position }
    }

    private fun resolveRightPitchFromAnchors(
        anchors: List<PositionedBox>,
        imageWidth: Int,
        imageHeight: Int,
    ): PitchResolution? {
        val candidates = buildList {
            for (firstIndex in anchors.indices) {
                for (secondIndex in firstIndex + 1 until anchors.size) {
                    val first = anchors[firstIndex]
                    val second = anchors[secondIndex]
                    val positionGap = second.position - first.position
                    if (positionGap <= 0) continue
                    val horizontalDelta = abs(second.box.centerX() - first.box.centerX())
                    if (horizontalDelta > imageWidth * MAX_PLACEMENT_COLUMN_HORIZONTAL_DELTA_FRACTION) continue
                    val pitch = (second.box.centerY() - first.box.centerY()) / positionGap
                    if (!isUsablePitch(pitch, imageHeight)) continue
                    add(
                        RightPitchCandidate(
                            pitch = pitch,
                            source = if (positionGap == 1) {
                                MatchResultPositionPitchSource.RIGHT_CONSECUTIVE
                            } else {
                                MatchResultPositionPitchSource.RIGHT_NON_CONSECUTIVE
                            },
                            horizontalDelta = horizontalDelta,
                            positionGap = positionGap,
                        ),
                    )
                }
            }
        }
        val selected = candidates.minWithOrNull(
            compareBy<RightPitchCandidate> {
                if (it.source == MatchResultPositionPitchSource.RIGHT_CONSECUTIVE) 0 else 1
            }.thenBy { it.horizontalDelta }
                .thenBy { it.positionGap },
        ) ?: return null
        return PitchResolution(selected.pitch, selected.source)
    }

    private fun buildColumnCrops(
        positions: IntRange,
        column: MatchResultPositionColumn,
        left: Int,
        right: Int,
        referencePosition: Int,
        referenceCenterY: Double,
        rowPitch: Double,
        imageWidth: Int,
        imageHeight: Int,
    ): List<MatchResultPositionCrop>? {
        if (left < 0 || right > imageWidth || left >= right) return null
        val output = mutableListOf<MatchResultPositionCrop>()
        for (position in positions) {
            val centerY = referenceCenterY + (position - referencePosition) * rowPitch
            val rawTop = centerY - rowPitch / 2.0
            val rawBottom = centerY + rowPitch / 2.0
            val visibleTop = maxOf(0.0, rawTop)
            val visibleBottom = minOf(imageHeight.toDouble(), rawBottom)
            val visibleHeight = visibleBottom - visibleTop
            if (
                !visibleHeight.isFinite() || visibleHeight <= 0.0 ||
                visibleHeight / rowPitch < MIN_REQUIRED_VISIBLE_ROW_FRACTION
            ) {
                return null
            }
            val pixelTop = floor(visibleTop).toInt().coerceIn(0, imageHeight)
            val pixelBottom = ceil(visibleBottom).toInt().coerceIn(0, imageHeight)
            if (pixelBottom <= pixelTop) return null
            output += MatchResultPositionCrop(
                position = position,
                column = column,
                bounds = OcrPixelCropRect(
                    left = left,
                    top = pixelTop,
                    right = right,
                    bottom = pixelBottom,
                ),
            )
        }
        return output
    }

    private fun withPlacementLeftPadding(
        detectedLeft: Int,
        imageWidth: Int,
    ): Int = (detectedLeft - ceil(imageWidth * POSITION_LEFT_PADDING_WIDTH_FRACTION).toInt())
        .coerceAtLeast(0)

    private fun clusterEliminationColumns(
        boxes: List<RawOcrBoundingBox>,
        imageWidth: Int,
    ): List<EliminationColumnCluster> {
        if (boxes.isEmpty()) return emptyList()
        val tolerance = imageWidth * ELIMINATION_COLUMN_CLUSTER_FRACTION
        val groups = mutableListOf<MutableList<RawOcrBoundingBox>>()
        boxes.sortedBy { it.centerX() }.forEach { box ->
            val last = groups.lastOrNull()
            val lastCenter = last?.map { it.centerX() }?.average()
            if (last != null && lastCenter != null && abs(box.centerX() - lastCenter) <= tolerance) {
                last += box
            } else {
                groups += mutableListOf(box)
            }
        }
        return groups.map { EliminationColumnCluster(it) }.sortedBy { it.centerX }
    }

    private fun clusterPositionCandidates(
        candidates: List<PositionedBox>,
        tolerance: Double,
    ): List<List<PositionedBox>> {
        val groups = mutableListOf<MutableList<PositionedBox>>()
        candidates.sortedBy { it.box.centerX() }.forEach { candidate ->
            val last = groups.lastOrNull()
            val lastCenter = last?.map { it.box.centerX() }?.average()
            if (last != null && lastCenter != null && abs(candidate.box.centerX() - lastCenter) <= tolerance) {
                last += candidate
            } else {
                groups += mutableListOf(candidate)
            }
        }
        return groups
    }

    private fun MatchResultAutoCropEvidence.eliminationBoxes(): List<RawOcrBoundingBox> = observations
        .asSequence()
        .filter { it.text.looksLikeEliminationText() }
        .mapNotNull { observation ->
            observation.boundingBox?.takeIf { it.isUsableFor(imageDimensions.width, imageDimensions.height) }
        }
        .toList()

    private fun MatchResultAutoCropEvidence.exactBoxes(expectedText: String): List<RawOcrBoundingBox> = observations
        .asSequence()
        .filter { it.text.trim() == expectedText }
        .mapNotNull { observation ->
            observation.boundingBox?.takeIf { it.isUsableFor(imageDimensions.width, imageDimensions.height) }
        }
        .toList()

    private fun String.looksLikeEliminationText(): Boolean {
        val normalized = trim().lowercase()
        return normalized.contains("elimin") || normalized.contains("eimin")
    }

    private fun isUsablePitch(pitch: Double, imageHeight: Int): Boolean =
        pitch.isFinite() &&
            pitch >= imageHeight * MIN_ROW_PITCH_HEIGHT_FRACTION &&
            pitch <= imageHeight * MAX_ROW_PITCH_HEIGHT_FRACTION

    private fun median(values: List<Double>): Double? {
        val sorted = values.filter { it.isFinite() }.sorted()
        if (sorted.isEmpty()) return null
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    private fun unavailable(
        reason: MatchResultPositionCropUnavailableReason,
    ): MatchResultPositionCropCalculationResult.Unavailable =
        MatchResultPositionCropCalculationResult.Unavailable(reason)

    private companion object {
        // Same measured left/right row-pitch relationship used by Result auto-crop recovery.
        const val LEFT_TO_RIGHT_ROW_PITCH_RATIO = 1.172
        const val MAX_PLACEMENT_COLUMN_HORIZONTAL_DELTA_FRACTION = 0.02
        const val RIGHT_PLACEMENT_BOUNDARY_TOLERANCE_FRACTION = 0.02
        const val MAX_RIGHT_PLACEMENT_GAP_FROM_LEFT_COLUMN_FRACTION = 0.12
        const val RIGHT_PLACEMENT_COLUMN_CLUSTER_FRACTION = 0.025
        const val ELIMINATION_COLUMN_CLUSTER_FRACTION = 0.05
        const val POSITION_LEFT_PADDING_WIDTH_FRACTION = 0.01
        const val MIN_ROW_PITCH_HEIGHT_FRACTION = 0.03
        const val MAX_ROW_PITCH_HEIGHT_FRACTION = 0.30
        const val MIN_REQUIRED_VISIBLE_ROW_FRACTION = 0.60
    }
}

private fun RawOcrBoundingBox.centerX(): Double = (left + right) / 2.0
private fun RawOcrBoundingBox.centerY(): Double = (top + bottom) / 2.0
private fun RawOcrBoundingBox.width(): Int = right - left
private fun RawOcrBoundingBox.height(): Int = bottom - top
private fun RawOcrBoundingBox.isUsableFor(imageWidth: Int, imageHeight: Int): Boolean =
    right > left && bottom > top && right > 0 && bottom > 0 && left < imageWidth && top < imageHeight
