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
    FALLBACK_THREE_ELIMINATION_GEOMETRY,
}

data class MatchResultPositionCrop(
    val position: Int,
    val column: MatchResultPositionColumn,
    val bounds: OcrPixelCropRect,
    /** Structural row center in the original source image, before clipping the crop bounds. */
    val structuralCenterYInSource: Double? = null,
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

private data class FallbackThreeRightEliminationColumns(
    val second: EliminationColumnCluster,
    val third: EliminationColumnCluster,
    val fourth: EliminationColumnCluster,
)

private enum class FallbackThreeGroupKind {
    NORMAL_PAIR,
    CENTERED_TWO_PLAYER,
    SINGLETON,
}

private data class FallbackThreeVerticalGroup(
    val kind: FallbackThreeGroupKind,
    val boxes: List<RawOcrBoundingBox>,
    val centerY: Double,
    val top: Int?,
    val bottom: Int?,
) {
    val height: Double?
        get() = if (top != null && bottom != null) (bottom - top).toDouble() else null
}

private data class FallbackThreeSideGeometry(
    val groups: List<FallbackThreeVerticalGroup>,
    val averageCropHeight: Double,
    val positionCenterPitch: Double?,
)

private data class FallbackThreeResolvedGroup(
    val position: Int,
    val group: FallbackThreeVerticalGroup,
)

private data class FallbackThreeColumnGroup(
    val columnIndex: Int,
    val group: FallbackThreeVerticalGroup,
)

private data class FallbackThreeWithinPositionGapFamily(
    val representativeGap: Double,
    val maximumGap: Double,
)

private data class ExistingUpperSideGeometry(
    val crops: List<MatchResultPositionCrop>,
    val pitch: PitchResolution,
)

private data class ExistingUpperSideResolution(
    val left: ExistingUpperSideGeometry?,
    val right: ExistingUpperSideGeometry?,
    val rightAnchors: List<PositionedBox>,
)

class MatchResultPositionCropCalculator(
    private val anchorDetector: MatchResultAutoCropAnchorDetector = MatchResultAutoCropAnchorDetector(),
) {
    fun calculate(
        evidence: MatchResultAutoCropEvidence,
        role: MatchResultScreenshotRole,
        allowUpperPositionElevenFallback: Boolean = false,
        expectedLowerPositions: List<Int> = (11..12).toList(),
        minimumRightPlacementAnchorCount: Int = 0,
    ): MatchResultPositionCropCalculationResult {
        val existing = calculateExisting(
            evidence = evidence,
            role = role,
            allowUpperPositionElevenFallback = allowUpperPositionElevenFallback,
            expectedLowerPositions = expectedLowerPositions,
            minimumRightPlacementAnchorCount = minimumRightPlacementAnchorCount,
        )
        if (
            role != MatchResultScreenshotRole.MATCH_RESULT_UPPER ||
            existing is MatchResultPositionCropCalculationResult.Available
        ) {
            return existing
        }
        val existingUpperSides = resolveExistingUpperSides(
            evidence = evidence,
            allowUpperPositionElevenFallback = allowUpperPositionElevenFallback,
            minimumRightPlacementAnchorCount = minimumRightPlacementAnchorCount,
        )
        return tryCalculateFallbackThree(
            evidence = evidence,
            existingUpperSides = existingUpperSides,
            allowUpperPositionElevenFallback = allowUpperPositionElevenFallback,
        ) ?: existing
    }

    private fun calculateExisting(
        evidence: MatchResultAutoCropEvidence,
        role: MatchResultScreenshotRole,
        allowUpperPositionElevenFallback: Boolean,
        expectedLowerPositions: List<Int>,
        minimumRightPlacementAnchorCount: Int,
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
        if (
            role == MatchResultScreenshotRole.MATCH_RESULT_LOWER &&
            rightAnchors.count { it.position in 6..10 } < minimumRightPlacementAnchorCount
        ) {
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
            requireConsistentAnchors = minimumRightPlacementAnchorCount > 0,
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

        val rightPositions = when (role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER ->
                if (allowUpperPositionElevenFallback) 6..12 else 6..10
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> expectedLowerPositions.toIntRangeOrNull()
                ?: return unavailable(MatchResultPositionCropUnavailableReason.POSITION_RECT_OUT_OF_BOUNDS)
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

        val paddedCrops = MatchResultPositionColumn.entries.flatMap { column ->
            applyVerticalPositionPadding(
                crops = crops.filter { it.column == column },
                imageHeight = dimensions.height,
            )
        }

        return MatchResultPositionCropCalculationResult.Available(
            crops = paddedCrops,
            leftRowPitch = leftPitch?.pitch,
            rightRowPitch = rightPitch.pitch,
            leftPitchSource = leftPitch?.source,
            rightPitchSource = rightPitch.source,
        )
    }

    private fun resolveExistingUpperSides(
        evidence: MatchResultAutoCropEvidence,
        allowUpperPositionElevenFallback: Boolean,
        minimumRightPlacementAnchorCount: Int,
    ): ExistingUpperSideResolution {
        val dimensions = evidence.imageDimensions
        val eliminationBoxes = evidence.eliminationBoxes()
        val eliminationClusters = clusterEliminationColumns(
            boxes = eliminationBoxes,
            imageWidth = dimensions.width,
        )
        if (eliminationClusters.size < 2) {
            return ExistingUpperSideResolution(
                left = null,
                right = null,
                rightAnchors = emptyList(),
            )
        }

        val anchorFour = anchorDetector.findAnchorFour(evidence)
        val anchorFive = anchorDetector.findAnchorFive(evidence)
        val rightAnchors = resolveRightPlacementAnchors(
            evidence = evidence,
            secondLeftEliminationBoundary = eliminationClusters[1].right,
        )
        if (rightAnchors.isEmpty()) {
            return ExistingUpperSideResolution(
                left = null,
                right = null,
                rightAnchors = emptyList(),
            )
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
            requireConsistentAnchors = minimumRightPlacementAnchorCount > 0,
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

        val rightPlacementLeft = rightAnchors.minOf { it.box.left }
        val leftEliminationClusters = eliminationClusters
            .filter { it.centerX < rightPlacementLeft.toDouble() }
            .sortedBy { it.centerX }
        val leftBoundaryRight = leftEliminationClusters.getOrNull(1)?.right
        val rightBoundaryRight = eliminationBoxes
            .asSequence()
            .filter { it.centerX() > rightPlacementLeft.toDouble() }
            .map { it.right }
            .maxOrNull()

        val left = if (
            anchorFour != null || anchorFive != null
        ) {
            val resolvedLeftPitch = leftPitch?.takeIf {
                isUsablePitch(it.pitch, dimensions.height)
            }
            val p5CenterY = when {
                resolvedLeftPitch == null -> null
                anchorFive != null -> anchorFive.centerY()
                anchorFour != null -> anchorFour.centerY() + resolvedLeftPitch.pitch
                else -> null
            }
            val leftBoundaryLeft = listOfNotNull(anchorFour?.left, anchorFive?.left).minOrNull()
            val resolvedLeftBoundaryRight = leftBoundaryRight
            if (
                resolvedLeftPitch != null &&
                p5CenterY != null &&
                leftBoundaryLeft != null &&
                resolvedLeftBoundaryRight != null &&
                resolvedLeftBoundaryRight > leftBoundaryLeft
            ) {
                buildColumnCrops(
                    positions = 1..5,
                    column = MatchResultPositionColumn.LEFT,
                    left = withPlacementLeftPadding(leftBoundaryLeft, dimensions.width),
                    right = resolvedLeftBoundaryRight,
                    referencePosition = 5,
                    referenceCenterY = p5CenterY,
                    rowPitch = resolvedLeftPitch.pitch,
                    imageWidth = dimensions.width,
                    imageHeight = dimensions.height,
                )?.let { crops ->
                    ExistingUpperSideGeometry(crops = crops, pitch = resolvedLeftPitch)
                }
            } else {
                null
            }
        } else {
            null
        }

        val right = rightPitch
            ?.takeIf { isUsablePitch(it.pitch, dimensions.height) }
            ?.let { resolvedRightPitch ->
                val resolvedRightBoundaryRight = rightBoundaryRight
                if (
                    resolvedRightBoundaryRight == null ||
                    resolvedRightBoundaryRight <= rightPlacementLeft
                ) {
                    return@let null
                }
                val rightReferenceCenterAtSix = median(
                    rightAnchors.map { anchor ->
                        anchor.box.centerY() - (anchor.position - 6) * resolvedRightPitch.pitch
                    },
                ) ?: return@let null
                val rightCrops = buildColumnCrops(
                    positions = 6..10,
                    column = MatchResultPositionColumn.RIGHT,
                    left = withPlacementLeftPadding(rightPlacementLeft, dimensions.width),
                    right = resolvedRightBoundaryRight,
                    referencePosition = 6,
                    referenceCenterY = rightReferenceCenterAtSix,
                    rowPitch = resolvedRightPitch.pitch,
                    imageWidth = dimensions.width,
                    imageHeight = dimensions.height,
                ) ?: return@let null
                val crops = rightCrops.toMutableList()
                val hasUpperPositionElevenFallback =
                    allowUpperPositionElevenFallback &&
                        rightAnchors.any { it.position == 11 }
                if (hasUpperPositionElevenFallback) {
                    buildColumnCrops(
                        positions = 11..11,
                        column = MatchResultPositionColumn.RIGHT,
                        left = withPlacementLeftPadding(rightPlacementLeft, dimensions.width),
                        right = resolvedRightBoundaryRight,
                        referencePosition = 6,
                        referenceCenterY = rightReferenceCenterAtSix,
                        rowPitch = resolvedRightPitch.pitch,
                        imageWidth = dimensions.width,
                        imageHeight = dimensions.height,
                    )?.let(crops::addAll)
                }
                ExistingUpperSideGeometry(crops = crops, pitch = resolvedRightPitch)
            }

        return ExistingUpperSideResolution(
            left = left,
            right = right,
            rightAnchors = rightAnchors,
        )
    }

    private fun tryCalculateFallbackThree(
        evidence: MatchResultAutoCropEvidence,
        existingUpperSides: ExistingUpperSideResolution,
        allowUpperPositionElevenFallback: Boolean,
    ): MatchResultPositionCropCalculationResult.Available? {
        val dimensions = evidence.imageDimensions
        val eliminationClusters = clusterEliminationColumns(
            boxes = evidence.eliminationBoxes(),
            imageWidth = dimensions.width,
        )
        var left = existingUpperSides.left
        if (left == null) {
            val leftColumns = selectFallbackThreeLeftEliminationColumns(eliminationClusters) ?: return null
            left = buildFallbackThreeSide(
                columns = listOf(leftColumns.first()),
                positions = 1..5,
                anchors = listOfNotNull(
                    anchorDetector.findAnchorFour(evidence)?.let { PositionedBox(4, it) },
                    anchorDetector.findAnchorFive(evidence)?.let { PositionedBox(5, it) },
                ),
                requireAnchorSeed = false,
                column = MatchResultPositionColumn.LEFT,
                horizontalBounds = buildFallbackThreeLeftHorizontalBounds(
                    firstColumn = leftColumns[0],
                    secondColumn = leftColumns[1],
                    imageWidth = dimensions.width,
                ),
                imageWidth = dimensions.width,
                imageHeight = dimensions.height,
            ) ?: return null
        }

        var right = existingUpperSides.right
        if (right == null) {
            val rightPositions = if (allowUpperPositionElevenFallback) 6..11 else 6..10
            val rightColumns = selectFallbackThreeRightEliminationColumns(
                clusters = eliminationClusters,
                allowTwoColumnSelection = existingUpperSides.left != null,
            ) ?: return null
            right = buildFallbackThreeSide(
                columns = listOf(rightColumns.third),
                positions = rightPositions,
                anchors = existingUpperSides.rightAnchors,
                requireAnchorSeed = false,
                topToBottomIdentity = true,
                column = MatchResultPositionColumn.RIGHT,
                horizontalBounds = buildFallbackThreeRightHorizontalBounds(
                    secondColumn = rightColumns.second,
                    thirdColumn = rightColumns.third,
                    fourthColumn = rightColumns.fourth,
                    imageWidth = dimensions.width,
                ),
                imageWidth = dimensions.width,
                imageHeight = dimensions.height,
            ) ?: return null
        }

        val crops = left.crops + right.crops
        val paddedCrops = MatchResultPositionColumn.entries.flatMap { column ->
            applyVerticalPositionPadding(
                crops = crops.filter { it.column == column },
                imageHeight = dimensions.height,
            )
        }
        return MatchResultPositionCropCalculationResult.Available(
            crops = paddedCrops,
            leftRowPitch = left.pitch.pitch,
            rightRowPitch = right.pitch.pitch,
            leftPitchSource = left.pitch.source,
            rightPitchSource = right.pitch.source,
        )
    }

    private fun selectFallbackThreeLeftEliminationColumns(
        clusters: List<EliminationColumnCluster>,
    ): List<EliminationColumnCluster>? = clusters
        .take(2)
        .takeIf { it.size == 2 && it.all { column -> column.boxes.isNotEmpty() } }

    private fun selectFallbackThreeRightEliminationColumns(
        clusters: List<EliminationColumnCluster>,
        allowTwoColumnSelection: Boolean,
    ): FallbackThreeRightEliminationColumns? = when {
        clusters.size >= 4 -> FallbackThreeRightEliminationColumns(
            second = clusters[1],
            third = clusters[2],
            fourth = clusters[3],
        )
        allowTwoColumnSelection && clusters.size >= 3 -> FallbackThreeRightEliminationColumns(
            second = clusters[0],
            third = clusters[1],
            fourth = clusters[2],
        )
        else -> null
    }

    private fun buildFallbackThreeSide(
        columns: List<EliminationColumnCluster>,
        positions: IntRange,
        anchors: List<PositionedBox>,
        requireAnchorSeed: Boolean,
        topToBottomIdentity: Boolean = false,
        column: MatchResultPositionColumn,
        horizontalBounds: Pair<Int, Int>?,
        imageWidth: Int,
        imageHeight: Int,
    ): ExistingUpperSideGeometry? {
        val rightSide = column == MatchResultPositionColumn.RIGHT
        val geometry = buildFallbackThreeSideGeometry(
            columns = columns,
            imageHeight = imageHeight,
            rightSide = rightSide,
        ) ?: return null
        val groups = if (topToBottomIdentity) {
            resolveFallbackThreeRightGroups(
                groups = geometry.groups,
                positions = positions,
                anchors = anchors,
                positionCenterPitch = geometry.positionCenterPitch,
                averageCropHeight = geometry.averageCropHeight,
            )
        } else {
            resolveFallbackThreeGroups(
                groups = geometry.groups,
                positions = positions,
                anchors = anchors,
                positionCenterPitch = geometry.positionCenterPitch,
                averageCropHeight = geometry.averageCropHeight,
                requireAnchorSeed = requireAnchorSeed,
            )
        } ?: return null
        val (left, right) = horizontalBounds ?: return null
        val crops = groups.mapNotNull { resolved ->
            buildFallbackThreeCrop(
                resolved = resolved,
                column = column,
                left = left,
                right = right,
                averageCropHeight = geometry.averageCropHeight,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
            )
        }
        if (crops.isEmpty()) return null
        val pitch = geometry.positionCenterPitch ?: if (rightSide && groups.size == 1) {
            geometry.averageCropHeight
        } else return null
        return ExistingUpperSideGeometry(
            crops = crops,
            pitch = PitchResolution(
                pitch = pitch,
                source = MatchResultPositionPitchSource.FALLBACK_THREE_ELIMINATION_GEOMETRY,
            ),
        )
    }

    private fun resolveFallbackThreeRightGroups(
        groups: List<FallbackThreeVerticalGroup>,
        positions: IntRange,
        anchors: List<PositionedBox>,
        positionCenterPitch: Double?,
        averageCropHeight: Double,
    ): List<FallbackThreeResolvedGroup>? {
        val orderedGroups = groups.sortedBy { it.centerY }
        if (orderedGroups.isEmpty()) return null
        if (orderedGroups.size > positions.count()) return null
        val orderedPositions = positions.toList()
        val resolved = orderedGroups.mapIndexed { index, group ->
            FallbackThreeResolvedGroup(
                position = orderedPositions[index],
                group = group,
            )
        }
        val tolerance = fallbackThreeAnchorAssignmentTolerance(positionCenterPitch, averageCropHeight)
        val survivingAnchors = anchors.filter { it.position in positions }
        val anchorConflicts = survivingAnchors.mapNotNull { anchor ->
                val resolvedGroup = resolved.firstOrNull { it.position == anchor.position }
                if (
                    resolvedGroup == null ||
                    abs(anchor.box.centerY() - resolvedGroup.group.centerY) > tolerance
                ) {
                    anchor to resolvedGroup
                } else {
                    null
                }
            }
        if (anchorConflicts.isNotEmpty()) {
            return null
        }
        return resolved
    }

    private fun buildFallbackThreeSideGeometry(
        columns: List<EliminationColumnCluster>,
        imageHeight: Int,
        rightSide: Boolean = false,
    ): FallbackThreeSideGeometry? {
        if (rightSide && columns.size == 1 && columns.single().boxes.size <= 2) {
            return buildSparseFallbackThreeRightSideGeometry(
                boxes = columns.single().boxes,
                imageHeight = imageHeight,
            )
        }
        val columnGroups = columns.mapIndexed { index, column ->
            val representativeHeight = median(column.boxes.map { it.height().toDouble() }) ?: return null
            val groupsForColumn = if (rightSide && index == 0) {
                pairFallbackThreeRightColumnBoxes(
                    boxes = column.boxes,
                    representativeTextHeight = representativeHeight,
                )
            } else {
                pairFallbackThreeColumnBoxes(
                    boxes = column.boxes,
                    representativeTextHeight = representativeHeight,
                )
            }
            index to groupsForColumn
        }
        val groups = if (columnGroups.size == 1) {
            columnGroups.single().second
        } else {
            mergeFallbackThreeLeftColumnGroups(
                columnGroups.map { (index, groupsForColumn) ->
                    groupsForColumn.map { group -> FallbackThreeColumnGroup(index, group) }
                }.flatten(),
            ) ?: return null
        }
        val completeGroups = groups.filter { it.kind == FallbackThreeGroupKind.NORMAL_PAIR }
        val averageCropHeight = median(completeGroups.mapNotNull { it.height }) ?: return null
        if (!averageCropHeight.isFinite() || averageCropHeight <= 0.0) {
            return null
        }
        val positionCenterPitch = representativePositionCenterPitch(
            centers = completeGroups.map { it.centerY },
            imageHeight = imageHeight,
        )
        return FallbackThreeSideGeometry(
            groups = groups.sortedBy { it.centerY },
            averageCropHeight = averageCropHeight,
            positionCenterPitch = positionCenterPitch,
        )
    }

    private fun buildSparseFallbackThreeRightSideGeometry(
        boxes: List<RawOcrBoundingBox>,
        imageHeight: Int,
    ): FallbackThreeSideGeometry? {
        val sorted = boxes.sortedWith(
            compareBy<RawOcrBoundingBox> { it.centerY() }
                .thenBy { it.top }
                .thenBy { it.left }
                .thenBy { it.right }
                .thenBy { it.bottom },
        )
        if (sorted.isEmpty()) return null
        val representativeTextHeight = median(sorted.map { it.height().toDouble() })
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: return null
        if (sorted.size == 1) {
            val group = FallbackThreeVerticalGroup(
                kind = FallbackThreeGroupKind.SINGLETON,
                boxes = listOf(sorted.single()),
                centerY = sorted.single().centerY(),
                top = null,
                bottom = null,
            )
            return FallbackThreeSideGeometry(
                groups = listOf(group),
                averageCropHeight = representativeTextHeight,
                positionCenterPitch = null,
            )
        }

        val upper = sorted[0]
        val lower = sorted[1]
        val edgeGap = (lower.top - upper.bottom).toDouble()
        val normalizedGap = edgeGap / representativeTextHeight
        if (!edgeGap.isFinite() || !normalizedGap.isFinite()) return null
        if (normalizedGap <= SPARSE_C3_SAME_POSITION_MAX_NORMALIZED_GAP) {
            val top = upper.top
            val bottom = lower.bottom
            if (bottom <= top) return null
            val group = FallbackThreeVerticalGroup(
                kind = FallbackThreeGroupKind.NORMAL_PAIR,
                boxes = listOf(upper, lower),
                centerY = (top + bottom) / 2.0,
                top = top,
                bottom = bottom,
            )
            return FallbackThreeSideGeometry(
                groups = listOf(group),
                averageCropHeight = representativeTextHeight,
                positionCenterPitch = null,
            )
        }

        val positionCenterPitch = representativePositionCenterPitch(
            centers = sorted.map { it.centerY() },
            imageHeight = imageHeight,
        )
        return FallbackThreeSideGeometry(
            groups = sorted.map { box ->
                FallbackThreeVerticalGroup(
                    kind = FallbackThreeGroupKind.SINGLETON,
                    boxes = listOf(box),
                    centerY = box.centerY(),
                    top = null,
                    bottom = null,
                )
            },
            averageCropHeight = representativeTextHeight,
            positionCenterPitch = positionCenterPitch,
        )
    }

    private fun pairFallbackThreeColumnBoxes(
        boxes: List<RawOcrBoundingBox>,
        representativeTextHeight: Double,
    ): List<FallbackThreeVerticalGroup> = pairFallbackThreeColumnBoxesInternal(
        boxes = boxes,
        representativeTextHeight = representativeTextHeight,
    )

    private fun pairFallbackThreeRightColumnBoxes(
        boxes: List<RawOcrBoundingBox>,
        representativeTextHeight: Double,
    ): List<FallbackThreeVerticalGroup> = pairFallbackThreeColumnBoxesInternal(
        boxes = boxes,
        representativeTextHeight = representativeTextHeight,
    )

    private fun pairFallbackThreeColumnBoxesInternal(
        boxes: List<RawOcrBoundingBox>,
        representativeTextHeight: Double,
    ): List<FallbackThreeVerticalGroup> {
        val sorted = boxes.sortedWith(
            compareBy<RawOcrBoundingBox> { it.centerY() }
                .thenBy { it.top }
                .thenBy { it.left }
                .thenBy { it.right }
                .thenBy { it.bottom },
        )
        if (sorted.isEmpty()) return emptyList()
        val adjacentGaps = sorted.zipWithNext()
            .mapIndexedNotNull { gapIndex, (first, second) ->
                val gap = second.centerY() - first.centerY()
                gap.takeIf { it.isFinite() && it > 0.0 }?.let { gapIndex to it }
            }
        val gapFamily = resolveWithinPositionGapFamily(
            gaps = adjacentGaps.map { it.second },
            representativeTextHeight = representativeTextHeight,
        )
        val pairStarts = mutableSetOf<Int>()
        val consumedObservationIndices = mutableSetOf<Int>()
        gapFamily?.let { family ->
            adjacentGaps
                .mapNotNull { (gapIndex, gap) ->
                    if (gap <= family.maximumGap) {
                        gapIndex to abs(gap - family.representativeGap)
                    } else {
                        null
                    }
                }
                .sortedWith(compareBy<Pair<Int, Double>> { it.second }.thenBy { it.first })
                .forEach { (gapIndex, _) ->
                    if (
                        gapIndex !in consumedObservationIndices &&
                        gapIndex + 1 !in consumedObservationIndices
                    ) {
                        pairStarts += gapIndex
                        consumedObservationIndices += gapIndex
                        consumedObservationIndices += gapIndex + 1
                    }
                }
        }
        val output = mutableListOf<FallbackThreeVerticalGroup>()
        var index = 0
        while (index < sorted.size) {
            val first = sorted[index]
            val second = sorted.getOrNull(index + 1)
            if (
                second != null &&
                index in pairStarts
            ) {
                output += FallbackThreeVerticalGroup(
                    kind = FallbackThreeGroupKind.NORMAL_PAIR,
                    boxes = listOf(first, second),
                    centerY = (first.top + second.bottom) / 2.0,
                    top = first.top,
                    bottom = second.bottom,
                )
                index += 2
            } else {
                output += FallbackThreeVerticalGroup(
                    kind = FallbackThreeGroupKind.SINGLETON,
                    boxes = listOf(first),
                    centerY = first.centerY(),
                    top = null,
                    bottom = null,
                )
                index++
            }
        }
        return output
    }

    private fun resolveWithinPositionGapFamily(
        gaps: List<Double>,
        representativeTextHeight: Double,
    ): FallbackThreeWithinPositionGapFamily? {
        val sortedGaps = gaps.filter { it.isFinite() && it > 0.0 }.sorted()
        if (sortedGaps.size < FALLBACK_THREE_MIN_GAP_FAMILY_GAP_COUNT) return null
        if (!representativeTextHeight.isFinite() || representativeTextHeight <= 0.0) return null

        var best: FallbackThreeWithinPositionGapFamily? = null
        var bestSeparationFraction = Double.NEGATIVE_INFINITY
        for (splitIndex in 1 until sortedGaps.size) {
            val lower = sortedGaps.subList(0, splitIndex)
            val upper = sortedGaps.subList(splitIndex, sortedGaps.size)
            val lowerMaximum = lower.last()
            val upperMinimum = upper.first()
            if (upperMinimum <= lowerMaximum) {
                continue
            }

            val lowerRepresentative = median(lower) ?: run {
                continue
            }
            val upperRepresentative = median(upper) ?: run {
                continue
            }
            if (
                !lowerRepresentative.isFinite() ||
                lowerRepresentative <= 0.0 ||
                !upperRepresentative.isFinite() ||
                upperRepresentative <= lowerRepresentative
            ) {
                continue
            }

            val lowerVariationFraction =
                (lowerMaximum - lower.first()) / lowerRepresentative
            val separationFraction =
                (upperMinimum - lowerMaximum) / lowerRepresentative
            val minimumMaterialSeparation = maxOf(
                FALLBACK_THREE_GAP_FAMILY_MIN_VARIATION_FRACTION,
                lowerVariationFraction,
            ) * FALLBACK_THREE_GAP_FAMILY_SEPARATION_VARIATION_MULTIPLIER
            val representativeRatio = upperRepresentative / lowerRepresentative
            val textHeightSanityPassed =
                lowerRepresentative <= representativeTextHeight * FALLBACK_THREE_MAX_SAME_ROW_GAP_HEIGHT_FACTOR
            val rejectionReason = when {
                separationFraction < minimumMaterialSeparation -> "INSUFFICIENT_SEPARATION"
                representativeRatio < FALLBACK_THREE_GAP_FAMILY_MIN_REPRESENTATIVE_RATIO -> "REPRESENTATIVE_RATIO_TOO_LOW"
                !textHeightSanityPassed -> "TEXT_HEIGHT_SANITY_FAILED"
                else -> null
            }
            if (rejectionReason != null) {
                continue
            }

            if (separationFraction > bestSeparationFraction) {
                best = FallbackThreeWithinPositionGapFamily(
                    representativeGap = lowerRepresentative,
                    maximumGap = lowerMaximum,
                )
                bestSeparationFraction = separationFraction
            }
        }
        return best
    }

    private fun mergeFallbackThreeLeftColumnGroups(
        candidates: List<FallbackThreeColumnGroup>,
    ): List<FallbackThreeVerticalGroup>? {
        if (candidates.isEmpty()) return emptyList()
        val tolerance = candidates
            .flatMap { it.group.boxes }
            .map { it.height().toDouble() }
            .let { heights -> median(heights)?.times(FALLBACK_THREE_COLUMN_CENTER_ALIGNMENT_HEIGHT_FACTOR) }
            ?: return null
        if (!tolerance.isFinite() || tolerance <= 0.0) return null
        val clusters = mutableListOf<MutableList<FallbackThreeColumnGroup>>()
        candidates.sortedWith(
            compareBy<FallbackThreeColumnGroup> { it.group.centerY }
                .thenBy { it.columnIndex },
        ).forEach { candidate ->
            val last = clusters.lastOrNull()
            val lastCenter = last?.map { it.group.centerY }?.average()
            if (last != null && lastCenter != null && abs(candidate.group.centerY - lastCenter) <= tolerance) {
                last += candidate
            } else {
                clusters += mutableListOf(candidate)
            }
        }
        if (clusters.any { cluster -> cluster.groupBy { it.columnIndex }.values.any { it.size > 1 } }) {
            return null
        }
        return clusters.mapNotNull { cluster ->
            val normalGroups = cluster.filter { it.group.kind == FallbackThreeGroupKind.NORMAL_PAIR }
            when {
                normalGroups.isNotEmpty() -> {
                    val boxes = normalGroups.flatMap { it.group.boxes }
                    val top = boxes.minOf { it.top }
                    val bottom = boxes.maxOf { it.bottom }
                    FallbackThreeVerticalGroup(
                        kind = FallbackThreeGroupKind.NORMAL_PAIR,
                        boxes = boxes,
                        centerY = (top + bottom) / 2.0,
                        top = top,
                        bottom = bottom,
                    )
                }

                cluster.map { it.columnIndex }.distinct().size >= 2 -> {
                    val boxes = cluster.flatMap { it.group.boxes }
                    FallbackThreeVerticalGroup(
                        kind = FallbackThreeGroupKind.CENTERED_TWO_PLAYER,
                        boxes = boxes,
                        centerY = boxes.map { it.centerY() }.average(),
                        top = null,
                        bottom = null,
                    )
                }

                cluster.size == 1 -> cluster.single().group
                else -> null
            }
        }
    }

    private fun representativePositionCenterPitch(
        centers: List<Double>,
        imageHeight: Int,
    ): Double? {
        val gaps = centers.sorted().zipWithNext()
            .map { (first, second) -> second - first }
            .filter { it.isFinite() && it > 0.0 }
        return median(gaps)?.takeIf { isUsablePitch(it, imageHeight) }
    }

    private fun resolveFallbackThreeGroups(
        groups: List<FallbackThreeVerticalGroup>,
        positions: IntRange,
        anchors: List<PositionedBox>,
        positionCenterPitch: Double?,
        averageCropHeight: Double,
        requireAnchorSeed: Boolean,
    ): List<FallbackThreeResolvedGroup>? {
        val orderedGroups = groups.sortedBy { it.centerY }
        if (orderedGroups.isEmpty() || orderedGroups.size > positions.count()) return null
        val orderedPositions = positions.toList()
        if (orderedGroups.size == orderedPositions.size) {
            if (requireAnchorSeed && anchors.isEmpty()) return null
            val tolerance = fallbackThreeAnchorAssignmentTolerance(positionCenterPitch, averageCropHeight)
            if (!anchors.all { anchor ->
                    val index = orderedPositions.indexOf(anchor.position)
                    index >= 0 && abs(anchor.box.centerY() - orderedGroups[index].centerY) <= tolerance
                }
            ) {
                return null
            }
            return orderedGroups.mapIndexed { index, group ->
                FallbackThreeResolvedGroup(orderedPositions[index], group)
            }
        }
        if (anchors.isEmpty() || positionCenterPitch == null) return null
        val tolerance = fallbackThreeAnchorAssignmentTolerance(positionCenterPitch, averageCropHeight)
        val resolved = orderedGroups.map { group ->
            val candidates = orderedPositions.mapNotNull { position ->
                val errors = anchors.map { anchor ->
                    abs(
                        group.centerY -
                            (anchor.box.centerY() + (position - anchor.position) * positionCenterPitch),
                    )
                }
                val error = errors.maxOrNull() ?: return@mapNotNull null
                if (error <= tolerance) position to error else null
            }
            if (candidates.isEmpty()) return null
            val bestError = candidates.minOf { it.second }
            val best = candidates.filter { abs(it.second - bestError) <= FALLBACK_THREE_ASSIGNMENT_TIE_EPSILON }
            if (best.size != 1) return null
            FallbackThreeResolvedGroup(best.single().first, group)
        }
        if (resolved.map { it.position }.distinct().size != resolved.size) return null
        if (resolved.zipWithNext().any { (first, second) -> second.position <= first.position }) return null
        return resolved
    }

    private fun fallbackThreeAnchorAssignmentTolerance(
        positionCenterPitch: Double?,
        averageCropHeight: Double,
    ): Double {
        val pitchBound = positionCenterPitch?.times(FALLBACK_THREE_ANCHOR_ASSIGNMENT_PITCH_FRACTION)
        val heightBound = averageCropHeight * FALLBACK_THREE_ANCHOR_ASSIGNMENT_HEIGHT_FACTOR
        return listOfNotNull(pitchBound, heightBound).minOrNull() ?: heightBound
    }

    private fun buildFallbackThreeLeftHorizontalBounds(
        firstColumn: EliminationColumnCluster,
        secondColumn: EliminationColumnCluster,
        imageWidth: Int,
    ): Pair<Int, Int>? {
        val firstLeft = representativeColumnEdge(firstColumn, useLeftEdge = true) ?: return null
        val firstWidth = representativeColumnWidth(firstColumn) ?: return null
        val secondRight = representativeColumnEdge(secondColumn, useLeftEdge = false) ?: return null
        val left = floor(firstLeft - FALLBACK_THREE_LEFT_OFFSET_MULTIPLIER * firstWidth)
            .toInt()
            .coerceIn(0, imageWidth)
        val right = ceil(secondRight).toInt().coerceIn(0, imageWidth)
        return (left to right).takeIf { (resolvedLeft, resolvedRight) -> resolvedRight > resolvedLeft }
    }

    private fun buildFallbackThreeRightHorizontalBounds(
        secondColumn: EliminationColumnCluster,
        thirdColumn: EliminationColumnCluster,
        fourthColumn: EliminationColumnCluster,
        imageWidth: Int,
    ): Pair<Int, Int>? {
        val secondRight = representativeColumnEdge(secondColumn, useLeftEdge = false) ?: return null
        val thirdWidth = representativeColumnWidth(thirdColumn) ?: return null
        val rightPadding = FALLBACK_THREE_RIGHT_START_PADDING_WIDTH_FACTOR * thirdWidth
        val left = ceil(secondRight + rightPadding)
            .toInt()
            .coerceIn(0, imageWidth)
        val c4ObservationRights = fourthColumn.boxes.map { it.right }
        val c4MaxRight = c4ObservationRights.maxOrNull() ?: return null
        val right = minOf(imageWidth, c4MaxRight)
        return (left to right).takeIf { (resolvedLeft, resolvedRight) ->
            resolvedLeft < resolvedRight && resolvedRight <= imageWidth
        }
    }

    private fun representativeColumnWidth(column: EliminationColumnCluster): Double? =
        median(column.boxes.map { it.width().toDouble() })?.takeIf { it.isFinite() && it > 0.0 }

    private fun representativeColumnEdge(
        column: EliminationColumnCluster,
        useLeftEdge: Boolean,
    ): Double? = median(
        column.boxes.map { box ->
            if (useLeftEdge) box.left.toDouble() else box.right.toDouble()
        },
    )

    private fun buildFallbackThreeCrop(
        resolved: FallbackThreeResolvedGroup,
        column: MatchResultPositionColumn,
        left: Int,
        right: Int,
        averageCropHeight: Double,
        imageWidth: Int,
        imageHeight: Int,
    ): MatchResultPositionCrop? {
        if (left < 0 || right > imageWidth || left >= right) return null
        val group = resolved.group
        val rawTop: Double
        val rawBottom: Double
        when (group.kind) {
            FallbackThreeGroupKind.NORMAL_PAIR -> {
                rawTop = group.top?.toDouble() ?: return null
                rawBottom = group.bottom?.toDouble() ?: return null
            }

            FallbackThreeGroupKind.CENTERED_TWO_PLAYER,
            FallbackThreeGroupKind.SINGLETON,
            -> {
                rawTop = group.centerY - averageCropHeight / 2.0
                rawBottom = group.centerY + averageCropHeight / 2.0
            }
        }
        if (!rawTop.isFinite() || !rawBottom.isFinite() || rawBottom <= rawTop) return null
        val visibleTop = maxOf(0.0, rawTop)
        val visibleBottom = minOf(imageHeight.toDouble(), rawBottom)
        val visibleHeight = visibleBottom - visibleTop
        val expectedHeight = rawBottom - rawTop
        if (
            !visibleHeight.isFinite() ||
            visibleHeight <= 0.0 ||
            expectedHeight <= 0.0 ||
            visibleHeight / expectedHeight < MIN_REQUIRED_VISIBLE_ROW_FRACTION
        ) {
            return null
        }
        val top = floor(visibleTop).toInt().coerceIn(0, imageHeight)
        val bottom = ceil(visibleBottom).toInt().coerceIn(0, imageHeight)
        if (bottom <= top) return null
        return MatchResultPositionCrop(
            position = resolved.position,
            column = column,
            bounds = OcrPixelCropRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
            ),
            structuralCenterYInSource = group.centerY,
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
        requireConsistentAnchors: Boolean = false,
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
        if (requireConsistentAnchors && !rightAnchorsFitPitch(anchors, selected.pitch)) return null
        return PitchResolution(selected.pitch, selected.source)
    }

    private fun rightAnchorsFitPitch(
        anchors: List<PositionedBox>,
        pitch: Double,
    ): Boolean {
        val reference = median(
            anchors.map { anchor ->
                anchor.box.centerY() - (anchor.position - anchors.minOf { it.position }) * pitch
            },
        ) ?: return false
        return anchors.all { anchor ->
            abs(
                anchor.box.centerY() -
                    (reference + (anchor.position - anchors.minOf { it.position }) * pitch),
            ) <= pitch * MAX_RIGHT_ANCHOR_RESIDUAL_PITCH_FRACTION
        }
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
            if (!centerY.isFinite() || centerY < 0.0 || centerY >= imageHeight.toDouble()) continue
            val rawTop = centerY - rowPitch / 2.0
            val rawBottom = centerY + rowPitch / 2.0
            val visibleTop = maxOf(0.0, rawTop)
            val visibleBottom = minOf(imageHeight.toDouble(), rawBottom)
            val visibleHeight = visibleBottom - visibleTop
            if (
                !visibleHeight.isFinite() || visibleHeight <= 0.0 ||
                visibleHeight / rowPitch < MIN_REQUIRED_VISIBLE_ROW_FRACTION
            ) {
                continue
            }
            val pixelTop = floor(visibleTop).toInt().coerceIn(0, imageHeight)
            val pixelBottom = ceil(visibleBottom).toInt().coerceIn(0, imageHeight)
            if (pixelBottom <= pixelTop) continue
            output += MatchResultPositionCrop(
                position = position,
                column = column,
                bounds = OcrPixelCropRect(
                    left = left,
                    top = pixelTop,
                    right = right,
                    bottom = pixelBottom,
                ),
                structuralCenterYInSource = centerY,
            )
        }
        return output.takeIf { it.isNotEmpty() }
    }

    private fun applyVerticalPositionPadding(
        crops: List<MatchResultPositionCrop>,
        imageHeight: Int,
    ): List<MatchResultPositionCrop> {
        if (crops.isEmpty()) return crops
        val padded = crops.map { crop ->
            val originalBounds = crop.bounds
            val originalHeight = (originalBounds.bottom - originalBounds.top).toDouble()
            val verticalPadding = originalHeight * POSITION_RECT_VERTICAL_PADDING_FRACTION
            crop.copy(
                bounds = originalBounds.copy(
                    top = floor(originalBounds.top - verticalPadding)
                        .toInt()
                        .coerceIn(0, imageHeight),
                    bottom = ceil(originalBounds.bottom + verticalPadding)
                        .toInt()
                        .coerceIn(0, imageHeight),
                ),
            )
        }.toMutableList()

        val orderedIndices = crops.indices.sortedWith(
            compareBy<Int> { crops[it].structuralCenterYInSource }
                .thenBy { crops[it].position },
        )
        orderedIndices.zipWithNext().forEach { (upperIndex, lowerIndex) ->
            val upperCenter = crops[upperIndex].structuralCenterYInSource
            val lowerCenter = crops[lowerIndex].structuralCenterYInSource
            if (upperCenter == null || lowerCenter == null || lowerCenter <= upperCenter) return@forEach
            val midpoint = (upperCenter + lowerCenter) / 2.0
            if (!midpoint.isFinite()) return@forEach

            val originalUpperBottom = crops[upperIndex].bounds.bottom
            val originalLowerTop = crops[lowerIndex].bounds.top
            val upperBounds = padded[upperIndex].bounds
            val lowerBounds = padded[lowerIndex].bounds
            if (upperBounds.bottom > midpoint && originalUpperBottom <= midpoint) {
                padded[upperIndex] = padded[upperIndex].copy(
                    bounds = upperBounds.copy(bottom = minOf(upperBounds.bottom, floor(midpoint).toInt())),
                )
            }
            if (lowerBounds.top < midpoint && originalLowerTop >= midpoint) {
                padded[lowerIndex] = padded[lowerIndex].copy(
                    bounds = lowerBounds.copy(top = maxOf(lowerBounds.top, ceil(midpoint).toInt())),
                )
            }
        }
        return padded
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
        val normalized = lowercase().filter { it in 'a'..'z' }
        if (normalized.startsWith(ELIMINATION_TEXT_STEM)) {
            return normalized.getOrNull(ELIMINATION_TEXT_STEM.length) != 'e'
        }
        val expected = ELIMINATION_TEXT_PREFIX
        val minimumPrefixLength = expected.length - 1
        val maximumPrefixLength = minOf(normalized.length, expected.length)
        if (maximumPrefixLength < minimumPrefixLength) return false
        return (minimumPrefixLength..maximumPrefixLength).any { prefixLength ->
            normalized.take(prefixLength).hasAtMostOneEditFrom(expected)
        }
    }

    private fun String.hasAtMostOneEditFrom(expected: String): Boolean {
        if (kotlin.math.abs(length - expected.length) > 1) return false
        var previous = IntArray(expected.length + 1) { it }
        for (leftIndex in indices) {
            val current = IntArray(expected.length + 1)
            current[0] = leftIndex + 1
            var rowMinimum = current[0]
            for (expectedIndex in expected.indices) {
                current[expectedIndex + 1] = if (this[leftIndex] == expected[expectedIndex]) {
                    previous[expectedIndex]
                } else {
                    1 + minOf(
                        previous[expectedIndex],
                        current[expectedIndex],
                        previous[expectedIndex + 1],
                    )
                }
                rowMinimum = minOf(rowMinimum, current[expectedIndex + 1])
            }
            if (rowMinimum > 1) return false
            previous = current
        }
        return previous[expected.length] <= 1
    }

    private fun isUsablePitch(pitch: Double, imageHeight: Int): Boolean =
        pitch.isFinite() &&
            pitch >= imageHeight * MIN_ROW_PITCH_HEIGHT_FRACTION &&
            pitch <= imageHeight * MAX_ROW_PITCH_HEIGHT_FRACTION

    private fun List<Int>.toIntRangeOrNull(): IntRange? = when (this) {
        listOf(11) -> 11..11
        listOf(11, 12) -> 11..12
        else -> null
    }

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
        const val MAX_RIGHT_ANCHOR_RESIDUAL_PITCH_FRACTION = 0.35
        const val FALLBACK_THREE_LEFT_OFFSET_MULTIPLIER = 3.0
        const val FALLBACK_THREE_RIGHT_START_PADDING_WIDTH_FACTOR = 0.13
        const val POSITION_RECT_VERTICAL_PADDING_FRACTION = 0.15
        const val FALLBACK_THREE_MAX_SAME_ROW_GAP_HEIGHT_FACTOR = 3.0
        const val FALLBACK_THREE_MIN_GAP_FAMILY_GAP_COUNT = 3
        const val FALLBACK_THREE_GAP_FAMILY_MIN_VARIATION_FRACTION = 0.05
        const val FALLBACK_THREE_GAP_FAMILY_SEPARATION_VARIATION_MULTIPLIER = 2.0
        const val FALLBACK_THREE_GAP_FAMILY_MIN_REPRESENTATIVE_RATIO = 1.25
        const val FALLBACK_THREE_COLUMN_CENTER_ALIGNMENT_HEIGHT_FACTOR = 1.0
        const val FALLBACK_THREE_ANCHOR_ASSIGNMENT_PITCH_FRACTION = 0.35
        const val FALLBACK_THREE_ANCHOR_ASSIGNMENT_HEIGHT_FACTOR = 1.5
        const val FALLBACK_THREE_ASSIGNMENT_TIE_EPSILON = 0.000001
        const val SPARSE_C3_SAME_POSITION_MAX_NORMALIZED_GAP = 0.90
        const val ELIMINATION_TEXT_STEM = "eliminat"
        const val ELIMINATION_TEXT_PREFIX = "eliminations"
    }
}

private fun RawOcrBoundingBox.centerX(): Double = (left + right) / 2.0
private fun RawOcrBoundingBox.centerY(): Double = (top + bottom) / 2.0
private fun RawOcrBoundingBox.width(): Int = right - left
private fun RawOcrBoundingBox.height(): Int = bottom - top
private fun RawOcrBoundingBox.isUsableFor(imageWidth: Int, imageHeight: Int): Boolean =
    right > left && bottom > top && right > 0 && bottom > 0 && left < imageWidth && top < imageHeight
