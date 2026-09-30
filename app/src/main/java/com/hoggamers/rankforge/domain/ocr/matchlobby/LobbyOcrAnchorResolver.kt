package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import kotlin.math.abs

enum class LobbyOcrAnchorLevel {
    ELEMENT,
    LINE,
    BLOCK,
}

data class LobbyOcrAnchorObservation(
    val text: String,
    val boundingBox: RawOcrBoundingBox,
    val level: LobbyOcrAnchorLevel,
    val blockIndex: Int = -1,
    val lineIndex: Int = -1,
    val elementIndex: Int = -1,
    val parentBoundingBox: RawOcrBoundingBox? = null,
)

data class LobbyResolvedOcrAnchor(
    val anchor: LobbyObservedSlotAnchor,
    val level: LobbyOcrAnchorLevel,
)

data class LobbyResolvedOcrAnchorGroup(
    val screenshotIndex: Int,
    val anchors: List<LobbyResolvedOcrAnchor>,
    val directlyObservedAnchorCount: Int,
    val alignmentError: Double,
    val potentialAnchorCount: Int = 0,
)

class LobbyOcrAnchorResolver {
    fun resolveAll(
        observations: List<LobbyOcrAnchorObservation>,
        imageDimensions: OcrImageDimensions,
    ): List<LobbyResolvedOcrAnchorGroup> = (1..3).map { screenshotIndex ->
        resolveGroup(screenshotIndex, observations, imageDimensions)
    }

    fun resolve(
        screenshotIndex: Int,
        observations: List<LobbyOcrAnchorObservation>,
        imageDimensions: OcrImageDimensions,
    ): List<LobbyResolvedOcrAnchor> =
        resolveGroup(screenshotIndex, observations, imageDimensions).anchors

    private fun resolveGroup(
        screenshotIndex: Int,
        observations: List<LobbyOcrAnchorObservation>,
        imageDimensions: OcrImageDimensions,
    ): LobbyResolvedOcrAnchorGroup {
        val expectedSlots = expectedSlotsFor(screenshotIndex)
            ?: return emptyResolvedGroup(screenshotIndex)

        val candidatesBySlot = observations
            .asSequence()
            .mapNotNull { it.toCandidateOrNull(expectedSlots, imageDimensions) }
            .groupBy { it.slot }
            .mapValues { (_, candidates) -> deduplicate(candidates) }
        val potentialAnchorCount = candidatesBySlot.count { (_, candidates) -> candidates.isNotEmpty() }

        val best = resolveBestAssignment(
            screenshotIndex = screenshotIndex,
            expectedSlots = expectedSlots,
            candidatesBySlot = candidatesBySlot,
            imageDimensions = imageDimensions,
        )

        return LobbyResolvedOcrAnchorGroup(
            screenshotIndex = screenshotIndex,
            anchors = best.anchors.filterNotNull().map { candidate ->
                LobbyResolvedOcrAnchor(
                    anchor = candidate.toObservedAnchor(),
                    level = candidate.level,
                )
            },
            directlyObservedAnchorCount = best.selectedCount,
            alignmentError = best.alignmentError,
            potentialAnchorCount = potentialAnchorCount,
        )
    }

    /**
     * Assignment selection has two evidence tiers.
     *
     * Strong evidence (3 or 4 anchors):
     * - preserve the existing ranking behavior;
     * - prefer more anchors, lower row/column alignment error, then hierarchy/tie-breakers.
     *
     * Two-anchor evidence:
     * - enumerate every ordering-valid physical OCR pair;
     * - require the pair to reconstruct a valid full grid;
     * - require every reconstructed center to remain inside the source image;
     * - accept only when exactly ONE physical pair survives.
     *
     * This prevents arbitrary coordinate/hierarchy tie-breakers from choosing between
     * multiple plausible two-anchor grids.
     */
    private fun resolveBestAssignment(
        screenshotIndex: Int,
        expectedSlots: List<Int>,
        candidatesBySlot: Map<Int, List<Candidate>>,
        imageDimensions: OcrImageDimensions,
    ): Assignment {
        val validAssignments = mutableListOf<Assignment>()
        val selected = mutableListOf<Candidate?>()

        fun visit(index: Int) {
            if (index == expectedSlots.size) {
                val assignment = Assignment(selected.toList())
                if (assignment.isOrderingValid()) {
                    validAssignments += assignment
                }
                return
            }

            val slot = expectedSlots[index]
            (listOf<Candidate?>(null) + candidatesBySlot[slot].orEmpty()).forEach { candidate ->
                selected += candidate
                visit(index + 1)
                selected.removeAt(selected.lastIndex)
            }
        }

        visit(0)

        val maximumObserved = validAssignments.maxOfOrNull { it.selectedCount } ?: 0
        val strongestAssignments = validAssignments.filter {
            it.selectedCount == maximumObserved
        }
        val validStrongAssignments = validAssignments.filter { assignment ->
            assignment.selectedCount >= STRONG_EVIDENCE_MINIMUM_ANCHORS &&
                assignment.hasValidStrongEvidenceGeometry() &&
                assignment.reconstructsInsideImage(
                    screenshotIndex = screenshotIndex,
                    imageDimensions = imageDimensions,
                )
        }

        return when {
            maximumObserved >= STRONG_EVIDENCE_MINIMUM_ANCHORS -> {
                val maximumValidStrong = validStrongAssignments.maxOfOrNull { it.selectedCount }
                if (maximumValidStrong == null) {
                    Assignment(List(expectedSlots.size) { null })
                } else {
                    validStrongAssignments
                        .filter { it.selectedCount == maximumValidStrong }
                        .bestByExistingRanking()
                }
            }

            maximumObserved == TWO_ANCHOR_COUNT ->
                resolveUniqueTwoAnchorAssignment(
                    screenshotIndex = screenshotIndex,
                    assignments = strongestAssignments,
                    imageDimensions = imageDimensions,
                    assignmentSize = expectedSlots.size,
                )

            else ->
                strongestAssignments.bestByExistingRanking()
        }
    }

    private fun Assignment.hasValidStrongEvidenceGeometry(): Boolean {
        val topLeft = anchors[ROLE_INDEX_TOP_LEFT]
        val topRight = anchors[ROLE_INDEX_TOP_RIGHT]
        val bottomLeft = anchors[ROLE_INDEX_BOTTOM_LEFT]
        val bottomRight = anchors[ROLE_INDEX_BOTTOM_RIGHT]

        return when (selectedCount) {
            FOUR_ANCHOR_COUNT -> {
                val topLeftAnchor = requireNotNull(topLeft)
                val topRightAnchor = requireNotNull(topRight)
                val bottomLeftAnchor = requireNotNull(bottomLeft)
                val bottomRightAnchor = requireNotNull(bottomRight)
                val topHorizontalPitch = topRightAnchor.centerX - topLeftAnchor.centerX
                val bottomHorizontalPitch = bottomRightAnchor.centerX - bottomLeftAnchor.centerX
                val leftVerticalPitch = bottomLeftAnchor.centerY - topLeftAnchor.centerY
                val rightVerticalPitch = bottomRightAnchor.centerY - topRightAnchor.centerY
                if (listOf(
                        topHorizontalPitch,
                        bottomHorizontalPitch,
                        leftVerticalPitch,
                        rightVerticalPitch,
                    ).any { pitch -> !pitch.isFinite() || pitch <= 0.0 }
                ) {
                    return false
                }
                strongEvidenceGeometry(
                    horizontalPitch = (topHorizontalPitch + bottomHorizontalPitch) / 2.0,
                    verticalPitch = (leftVerticalPitch + rightVerticalPitch) / 2.0,
                    rowErrors = listOf(
                        abs(topLeftAnchor.centerY - topRightAnchor.centerY),
                        abs(bottomLeftAnchor.centerY - bottomRightAnchor.centerY),
                    ),
                    columnErrors = listOf(
                        abs(topLeftAnchor.centerX - bottomLeftAnchor.centerX),
                        abs(topRightAnchor.centerX - bottomRightAnchor.centerX),
                    ),
                )
            }

            THREE_ANCHOR_COUNT -> when {
                topLeft == null -> strongEvidenceGeometry(
                    horizontalPitch = requireNotNull(bottomRight).centerX - requireNotNull(bottomLeft).centerX,
                    verticalPitch = requireNotNull(bottomRight).centerY - requireNotNull(topRight).centerY,
                    rowErrors = listOf(
                        abs(requireNotNull(bottomLeft).centerY - requireNotNull(bottomRight).centerY),
                    ),
                    columnErrors = listOf(
                        abs(requireNotNull(topRight).centerX - requireNotNull(bottomRight).centerX),
                    ),
                )

                topRight == null -> strongEvidenceGeometry(
                    horizontalPitch = requireNotNull(bottomRight).centerX - requireNotNull(bottomLeft).centerX,
                    verticalPitch = requireNotNull(bottomLeft).centerY - requireNotNull(topLeft).centerY,
                    rowErrors = listOf(
                        abs(requireNotNull(bottomLeft).centerY - requireNotNull(bottomRight).centerY),
                    ),
                    columnErrors = listOf(
                        abs(requireNotNull(topLeft).centerX - requireNotNull(bottomLeft).centerX),
                    ),
                )

                bottomLeft == null -> strongEvidenceGeometry(
                    horizontalPitch = requireNotNull(topRight).centerX - requireNotNull(topLeft).centerX,
                    verticalPitch = requireNotNull(bottomRight).centerY - requireNotNull(topRight).centerY,
                    rowErrors = listOf(
                        abs(requireNotNull(topLeft).centerY - requireNotNull(topRight).centerY),
                    ),
                    columnErrors = listOf(
                        abs(requireNotNull(topRight).centerX - requireNotNull(bottomRight).centerX),
                    ),
                )

                bottomRight == null -> strongEvidenceGeometry(
                    horizontalPitch = requireNotNull(topRight).centerX - requireNotNull(topLeft).centerX,
                    verticalPitch = requireNotNull(bottomLeft).centerY - requireNotNull(topLeft).centerY,
                    rowErrors = listOf(
                        abs(requireNotNull(topLeft).centerY - requireNotNull(topRight).centerY),
                    ),
                    columnErrors = listOf(
                        abs(requireNotNull(topLeft).centerX - requireNotNull(bottomLeft).centerX),
                    ),
                )

                else -> false
            }

            else -> false
        }
    }

    private fun strongEvidenceGeometry(
        horizontalPitch: Double,
        verticalPitch: Double,
        rowErrors: List<Double>,
        columnErrors: List<Double>,
    ): Boolean {
        if (!horizontalPitch.isFinite() || horizontalPitch <= 0.0) return false
        if (!verticalPitch.isFinite() || verticalPitch <= 0.0) return false
        return rowErrors.all { error ->
            error.isFinite() && error <= verticalPitch * STRONG_EVIDENCE_MAX_ALIGNMENT_RATIO
        } && columnErrors.all { error ->
            error.isFinite() && error <= horizontalPitch * STRONG_EVIDENCE_MAX_ALIGNMENT_RATIO
        }
    }

    private fun resolveUniqueTwoAnchorAssignment(
        screenshotIndex: Int,
        assignments: List<Assignment>,
        imageDimensions: OcrImageDimensions,
        assignmentSize: Int,
    ): Assignment {
        val usablePairs = assignments.filter { assignment ->
            assignment.selectedCount == TWO_ANCHOR_COUNT &&
                assignment.hasValidTwoAnchorGeometry(imageDimensions) &&
                assignment.reconstructsInsideImage(
                    screenshotIndex = screenshotIndex,
                    imageDimensions = imageDimensions,
                )
        }

        return usablePairs.singleOrNull()
            ?: Assignment(List(assignmentSize) { null })
    }

    private fun Assignment.hasValidTwoAnchorGeometry(
        imageDimensions: OcrImageDimensions,
    ): Boolean {
        if (selectedCount != TWO_ANCHOR_COUNT) return false

        val topLeft = anchors[ROLE_INDEX_TOP_LEFT]
        val topRight = anchors[ROLE_INDEX_TOP_RIGHT]
        val bottomLeft = anchors[ROLE_INDEX_BOTTOM_LEFT]
        val bottomRight = anchors[ROLE_INDEX_BOTTOM_RIGHT]

        return when {
            topLeft != null && topRight != null ->
                horizontalPairIsValid(topLeft, topRight, imageDimensions)

            bottomLeft != null && bottomRight != null ->
                horizontalPairIsValid(bottomLeft, bottomRight, imageDimensions)

            topLeft != null && bottomLeft != null ->
                verticalPairIsValid(topLeft, bottomLeft, imageDimensions)

            topRight != null && bottomRight != null ->
                verticalPairIsValid(topRight, bottomRight, imageDimensions)

            topLeft != null && bottomRight != null -> {
                if (topLeft.centerX >= bottomRight.centerX ||
                    topLeft.centerY >= bottomRight.centerY
                ) {
                    false
                } else {
                    diagonalPairIsValid(
                        horizontalSeparation = bottomRight.centerX - topLeft.centerX,
                        verticalSeparation = bottomRight.centerY - topLeft.centerY,
                        imageDimensions = imageDimensions,
                    )
                }
            }

            topRight != null && bottomLeft != null -> {
                if (bottomLeft.centerX >= topRight.centerX ||
                    topRight.centerY >= bottomLeft.centerY
                ) {
                    false
                } else {
                    diagonalPairIsValid(
                        horizontalSeparation = topRight.centerX - bottomLeft.centerX,
                        verticalSeparation = bottomLeft.centerY - topRight.centerY,
                        imageDimensions = imageDimensions,
                    )
                }
            }

            else -> false
        }
    }

    private fun horizontalPairIsValid(
        left: Candidate,
        right: Candidate,
        imageDimensions: OcrImageDimensions,
    ): Boolean {
        val horizontalSeparation = right.centerX - left.centerX
        val verticalDifference = abs(left.centerY - right.centerY)
        return horizontalSeparation.isFinite() &&
            verticalDifference.isFinite() &&
            horizontalSeparation >= imageDimensions.width * TWO_ANCHOR_MIN_HORIZONTAL_SEPARATION_RATIO &&
            verticalDifference <= TWO_ANCHOR_ROW_ALIGNMENT_TOLERANCE_PX
    }

    private fun verticalPairIsValid(
        top: Candidate,
        bottom: Candidate,
        imageDimensions: OcrImageDimensions,
    ): Boolean {
        val verticalSeparation = bottom.centerY - top.centerY
        val horizontalDifference = abs(top.centerX - bottom.centerX)
        return verticalSeparation.isFinite() &&
            horizontalDifference.isFinite() &&
            verticalSeparation >= imageDimensions.height * TWO_ANCHOR_MIN_VERTICAL_SEPARATION_RATIO &&
            horizontalDifference <= TWO_ANCHOR_COLUMN_ALIGNMENT_TOLERANCE_PX
    }

    private fun diagonalPairIsValid(
        horizontalSeparation: Double,
        verticalSeparation: Double,
        imageDimensions: OcrImageDimensions,
    ): Boolean {
        if (!horizontalSeparation.isFinite() || horizontalSeparation <= 0.0) return false
        if (!verticalSeparation.isFinite() || verticalSeparation <= 0.0) return false
        if (horizontalSeparation < imageDimensions.width * TWO_ANCHOR_MIN_HORIZONTAL_SEPARATION_RATIO) {
            return false
        }
        if (verticalSeparation < imageDimensions.height * TWO_ANCHOR_MIN_VERTICAL_SEPARATION_RATIO) {
            return false
        }

        val expectedRatio = LobbyGridGeometryCalibrationProfiles
            .InitialObservedPitchRatio
            .columnToRowPitchRatio
        val observedRatio = horizontalSeparation / verticalSeparation
        val relativeRatioError = abs(observedRatio - expectedRatio) / expectedRatio
        return relativeRatioError <= TWO_ANCHOR_DIAGONAL_RATIO_TOLERANCE
    }

    private fun Assignment.reconstructsInsideImage(
        screenshotIndex: Int,
        imageDimensions: OcrImageDimensions,
    ): Boolean {
        val reconstructed = LobbySlotGridReconstructor().reconstruct(
            screenshotIndex = screenshotIndex,
            observedAnchors = anchors.filterNotNull().map { it.toObservedAnchor() },
        ) as? LobbyGridReconstructionResult.Reconstructed ?: return false

        val width = imageDimensions.width.toDouble()
        val height = imageDimensions.height.toDouble()

        return reconstructed.grid.points.all { point ->
            point.centerX.isFinite() &&
                point.centerY.isFinite() &&
                point.centerX >= 0.0 &&
                point.centerY >= 0.0 &&
                point.centerX <= width &&
                point.centerY <= height
        }
    }

    private fun LobbyOcrAnchorObservation.toCandidateOrNull(
        expectedSlots: List<Int>,
        dimensions: OcrImageDimensions,
    ): Candidate? {
        val slot = text.trim().let { value ->
            expectedSlots.firstOrNull { it.toString() == value }
        } ?: return null
        if (!boundingBox.isUsableFor(dimensions)) return null

        return Candidate(
            slot = slot,
            level = level,
            boundingBox = boundingBox,
            parentBoundingBox = parentBoundingBox,
            blockIndex = blockIndex,
            lineIndex = lineIndex,
            elementIndex = elementIndex,
        )
    }

    private fun deduplicate(candidates: List<Candidate>): List<Candidate> =
        candidates
            .sortedWith(candidateComparator)
            .fold(mutableListOf()) { retained, candidate ->
                if (retained.none { it.isSamePhysicalGlyph(candidate) }) {
                    retained += candidate
                }
                retained
            }

    private fun Candidate.isSamePhysicalGlyph(other: Candidate): Boolean =
        boundingBox == other.boundingBox ||
            parentBoundingBox == other.boundingBox ||
            other.parentBoundingBox == boundingBox

    private data class Candidate(
        val slot: Int,
        val level: LobbyOcrAnchorLevel,
        val boundingBox: RawOcrBoundingBox,
        val parentBoundingBox: RawOcrBoundingBox?,
        val blockIndex: Int,
        val lineIndex: Int,
        val elementIndex: Int,
    ) {
        val centerX: Double
            get() = (boundingBox.left + boundingBox.right) / 2.0

        val centerY: Double
            get() = (boundingBox.top + boundingBox.bottom) / 2.0

        val hierarchyPriority: Int
            get() = levelPriority(level)

        fun toObservedAnchor() = LobbyObservedSlotAnchor(
            slotNumber = slot,
            centerX = centerX,
            centerY = centerY,
        )
    }

    private data class Assignment(
        val anchors: List<Candidate?>,
    ) {
        val selectedCount: Int
            get() = anchors.count { it != null }

        val alignmentError: Double
            get() = pairError(0, 1, horizontal = false) +
                pairError(2, 3, horizontal = false) +
                pairError(0, 2, horizontal = true) +
                pairError(1, 3, horizontal = true)

        val hierarchyScore: Int
            get() = anchors.filterNotNull().sumOf { it.hierarchyPriority }

        fun isOrderingValid(): Boolean {
            val rowAndColumnOrderingValid =
                ordered(0, 1, horizontal = true) &&
                    ordered(2, 3, horizontal = true) &&
                    ordered(0, 2, horizontal = false) &&
                    ordered(1, 3, horizontal = false)

            if (!rowAndColumnOrderingValid) return false
            if (selectedCount != TWO_ANCHOR_COUNT) return true

            return diagonalOrdered(
                first = 0,
                second = 3,
                firstMustBeLeft = true,
                firstMustBeAbove = true,
            ) && diagonalOrdered(
                first = 1,
                second = 2,
                firstMustBeLeft = false,
                firstMustBeAbove = true,
            )
        }

        fun isBetterThan(previous: Assignment?): Boolean {
            if (previous == null) return true
            if (selectedCount != previous.selectedCount) {
                return selectedCount > previous.selectedCount
            }
            if (alignmentError != previous.alignmentError) {
                return alignmentError < previous.alignmentError
            }
            if (hierarchyScore != previous.hierarchyScore) {
                return hierarchyScore < previous.hierarchyScore
            }
            return compareTieBreakers(previous) < 0
        }

        private fun ordered(
            first: Int,
            second: Int,
            horizontal: Boolean,
        ): Boolean {
            val firstAnchor = anchors[first] ?: return true
            val secondAnchor = anchors[second] ?: return true
            return if (horizontal) {
                firstAnchor.centerX < secondAnchor.centerX
            } else {
                firstAnchor.centerY < secondAnchor.centerY
            }
        }

        /**
         * Explicit diagonal ordering matters when exactly the opposite corners are
         * observed because the four row/column pair checks above otherwise have no
         * pair containing two non-null anchors.
         */
        private fun diagonalOrdered(
            first: Int,
            second: Int,
            firstMustBeLeft: Boolean,
            firstMustBeAbove: Boolean,
        ): Boolean {
            val firstAnchor = anchors[first] ?: return true
            val secondAnchor = anchors[second] ?: return true

            val horizontalValid = if (firstMustBeLeft) {
                firstAnchor.centerX < secondAnchor.centerX
            } else {
                firstAnchor.centerX > secondAnchor.centerX
            }
            val verticalValid = if (firstMustBeAbove) {
                firstAnchor.centerY < secondAnchor.centerY
            } else {
                firstAnchor.centerY > secondAnchor.centerY
            }

            return horizontalValid && verticalValid
        }

        private fun pairError(
            first: Int,
            second: Int,
            horizontal: Boolean,
        ): Double {
            val firstAnchor = anchors[first] ?: return 0.0
            val secondAnchor = anchors[second] ?: return 0.0
            return if (horizontal) {
                abs(firstAnchor.centerX - secondAnchor.centerX)
            } else {
                abs(firstAnchor.centerY - secondAnchor.centerY)
            }
        }

        private fun compareTieBreakers(other: Assignment): Int {
            anchors.zip(other.anchors).forEach { (first, second) ->
                val difference = compareOptionalCandidates(first, second)
                if (difference != 0) return difference
            }
            return 0
        }
    }

    private companion object {
        const val TWO_ANCHOR_COUNT = 2
        const val TWO_ANCHOR_MIN_HORIZONTAL_SEPARATION_RATIO = 0.20
        const val TWO_ANCHOR_MIN_VERTICAL_SEPARATION_RATIO = 0.20
        const val TWO_ANCHOR_ROW_ALIGNMENT_TOLERANCE_PX = 10.0
        const val TWO_ANCHOR_COLUMN_ALIGNMENT_TOLERANCE_PX = 10.0
        const val TWO_ANCHOR_DIAGONAL_RATIO_TOLERANCE = 0.15
        const val THREE_ANCHOR_COUNT = 3
        const val FOUR_ANCHOR_COUNT = 4
        const val STRONG_EVIDENCE_MINIMUM_ANCHORS = 3
        const val STRONG_EVIDENCE_MAX_ALIGNMENT_RATIO = 0.10

        const val ROLE_INDEX_TOP_LEFT = 0
        const val ROLE_INDEX_TOP_RIGHT = 1
        const val ROLE_INDEX_BOTTOM_LEFT = 2
        const val ROLE_INDEX_BOTTOM_RIGHT = 3

        val candidateComparator = compareBy<Candidate> { it.hierarchyPriority }
            .thenBy { it.boundingBox.left }
            .thenBy { it.boundingBox.top }
            .thenBy { it.boundingBox.right }
            .thenBy { it.boundingBox.bottom }
            .thenBy { it.blockIndex }
            .thenBy { it.lineIndex }
            .thenBy { it.elementIndex }

        fun expectedSlotsFor(screenshotIndex: Int): List<Int>? = when (screenshotIndex) {
            1 -> (1..4).toList()
            2 -> (5..8).toList()
            3 -> (9..12).toList()
            else -> null
        }

        fun emptyResolvedGroup(screenshotIndex: Int) = LobbyResolvedOcrAnchorGroup(
            screenshotIndex = screenshotIndex,
            anchors = emptyList(),
            directlyObservedAnchorCount = 0,
            alignmentError = Double.POSITIVE_INFINITY,
        )

        fun levelPriority(level: LobbyOcrAnchorLevel): Int = when (level) {
            LobbyOcrAnchorLevel.ELEMENT -> 0
            LobbyOcrAnchorLevel.LINE -> 1
            LobbyOcrAnchorLevel.BLOCK -> 2
        }

        fun RawOcrBoundingBox.isUsableFor(dimensions: OcrImageDimensions): Boolean =
            right > left &&
                bottom > top &&
                right > 0 &&
                bottom > 0 &&
                left < dimensions.width &&
                top < dimensions.height

        fun List<Assignment>.bestByExistingRanking(): Assignment {
            if (isEmpty()) return Assignment(List(4) { null })

            var best: Assignment? = null
            forEach { assignment ->
                if (assignment.isBetterThan(best)) {
                    best = assignment
                }
            }
            return requireNotNull(best)
        }

        fun compareOptionalCandidates(first: Candidate?, second: Candidate?): Int {
            if (first == null && second == null) return 0
            if (first == null) return 1
            if (second == null) return -1
            return compareValuesBy(
                first,
                second,
                { it.hierarchyPriority },
                { it.boundingBox.left },
                { it.boundingBox.top },
                { it.boundingBox.right },
                { it.boundingBox.bottom },
                { it.blockIndex },
                { it.lineIndex },
                { it.elementIndex },
            )
        }
    }
}
