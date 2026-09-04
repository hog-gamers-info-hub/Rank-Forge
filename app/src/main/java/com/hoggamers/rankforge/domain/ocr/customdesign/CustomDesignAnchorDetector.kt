package com.hoggamers.rankforge.domain.ocr.customdesign

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max

class CustomDesignAnchorDetector @Inject constructor() {
    fun detect(
        sourceWidth: Int,
        sourceHeight: Int,
        labels: CustomDesignOcrLabels,
        blocks: List<RawOcrBlock>,
    ): CustomDesignOcrAnchors = detectDetailed(sourceWidth, sourceHeight, labels, blocks).anchors

    fun detectDetailed(
        sourceWidth: Int,
        sourceHeight: Int,
        labels: CustomDesignOcrLabels,
        blocks: List<RawOcrBlock>,
    ): CustomDesignAnchorDetectionResult {
        val width = sourceWidth.coerceAtLeast(0)
        val height = sourceHeight.coerceAtLeast(0)
        val observations = blocks.flatMap { block ->
            block.lines.flatMap { line -> line.headerObservations() }
        }
        val configuredLabels = linkedMapOf(
            CustomDesignAnchorField.TEAM_NAME to labels.teamName,
            CustomDesignAnchorField.WIN to labels.win,
            CustomDesignAnchorField.TOTAL_KILLS to labels.totalKills,
            CustomDesignAnchorField.POSITION_POINTS to labels.positionPoints,
            CustomDesignAnchorField.TOTAL_POINTS to labels.totalPoints,
        )
        val columnX = linkedMapOf<CustomDesignAnchorField, Float>()
        val headerY = linkedMapOf<CustomDesignAnchorField, Float>()
        val ambiguousFields = linkedSetOf<CustomDesignAnchorField>()
        val missingFields = linkedSetOf<CustomDesignAnchorField>()

        configuredLabels.forEach { (field, label) ->
            val matches = observations
                .filter { it.matches(label) }
                .distinctBy { it.geometry.signature() }
            when {
                matches.isEmpty() -> missingFields += field
                matches.size > 1 -> ambiguousFields += field
                else -> {
                    val center = matches.single().geometry.center(width, height)
                    columnX[field] = center.x
                    headerY[field] = center.y
                }
            }
        }

        val rankResult = detectRanks(
            sourceWidth = width,
            sourceHeight = height,
            blocks = blocks,
            headerX = columnX,
            headerY = headerY,
        )
        return CustomDesignAnchorDetectionResult(
            anchors = CustomDesignOcrAnchors(
                sourceWidth = width,
                sourceHeight = height,
                columnX = columnX,
                rowY = rankResult.rowY,
            ),
            headerCenterY = headerY,
            missingFields = missingFields,
            ambiguousFields = ambiguousFields,
            ambiguousRanks = rankResult.ambiguousRanks,
        )
    }

    private fun detectRanks(
        sourceWidth: Int,
        sourceHeight: Int,
        blocks: List<RawOcrBlock>,
        headerX: Map<CustomDesignAnchorField, Float>,
        headerY: Map<CustomDesignAnchorField, Float>,
    ): RankDetectionResult {
        if (headerX.isEmpty() || headerY.isEmpty()) return RankDetectionResult.EMPTY
        val headerRow = headerY.values.sorted().median()
        val leftOfHeader = headerX.values.minOrNull() ?: return RankDetectionResult.EMPTY
        val candidates = blocks
            .flatMap { block -> block.lines.flatMap { it.elements } }
            .mapNotNull { it.rankObservation(sourceWidth, sourceHeight) }
            .filter { it.centerY > headerRow && it.centerX < leftOfHeader }
        if (candidates.isEmpty()) return RankDetectionResult.EMPTY

        val xTolerance = candidates.map { it.width }.sorted().median().coerceAtLeast(1f) * 1.5f
        val clusters = mutableListOf<MutableList<RankObservation>>()
        candidates.sortedBy { it.centerX }.forEach { candidate ->
            val cluster = clusters.lastOrNull()
            if (cluster == null || candidate.centerX - cluster.last().centerX > xTolerance) {
                clusters += mutableListOf(candidate)
            } else {
                cluster += candidate
            }
        }
        val scoredClusters = clusters.map { cluster -> cluster to cluster.score() }
        val bestScore = scoredClusters.maxOfOrNull { it.second } ?: return RankDetectionResult.EMPTY
        val bestClusters = scoredClusters.filter { it.second == bestScore }
        if (bestClusters.size != 1) return RankDetectionResult.EMPTY

        val selected = bestClusters.single().first
        val rowY = linkedMapOf<Int, Float>()
        val ambiguousRanks = linkedSetOf<Int>()
        selected.groupBy { it.rank }.forEach { (rank, matches) ->
            if (matches.size == 1) {
                rowY[rank] = matches.single().centerY.coerceIn(0f, sourceHeight.toFloat())
            } else {
                ambiguousRanks += rank
            }
        }
        return RankDetectionResult(rowY, ambiguousRanks)
    }
}

private data class HeaderObservation(
    val text: String,
    val geometry: CandidateGeometry,
) {
    fun matches(expected: String): Boolean = normalize(text) == normalize(expected)
}

private data class RankObservation(
    val rank: Int,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
)

private data class RankDetectionResult(
    val rowY: Map<Int, Float>,
    val ambiguousRanks: Set<Int>,
) {
    companion object {
        val EMPTY = RankDetectionResult(emptyMap(), emptySet())
    }
}

private data class CandidateGeometry(
    val boundingBox: RawOcrBoundingBox,
    val cornerPoints: List<RawOcrPoint>?,
) {
    fun center(sourceWidth: Int, sourceHeight: Int): PointF = if (!cornerPoints.isNullOrEmpty()) {
        PointF(
            cornerPoints.map { it.x }.average().toFloat().coerceIn(0f, sourceWidth.toFloat()),
            cornerPoints.map { it.y }.average().toFloat().coerceIn(0f, sourceHeight.toFloat()),
        )
    } else {
        PointF(
            ((boundingBox.left + boundingBox.right) / 2f).coerceIn(0f, sourceWidth.toFloat()),
            ((boundingBox.top + boundingBox.bottom) / 2f).coerceIn(0f, sourceHeight.toFloat()),
        )
    }

    fun signature(): String = listOf(
        boundingBox.left,
        boundingBox.top,
        boundingBox.right,
        boundingBox.bottom,
        cornerPoints,
    ).toString()
}

private data class PointF(val x: Float, val y: Float)

private fun RawOcrLine.headerObservations(): List<HeaderObservation> {
    val elements = elements.mapNotNull { element ->
        element.geometry?.toCandidateGeometry()?.let { geometry ->
            HeaderElement(element.text, geometry)
        }
    }
    if (elements.isEmpty()) {
        return geometry?.toCandidateGeometry()?.let { listOf(HeaderObservation(text, it)) }.orEmpty()
    }

    val observations = mutableListOf<HeaderObservation>()
    for (start in elements.indices) {
        for (end in start until elements.size) {
            val span = elements.subList(start, end + 1)
            if (!span.isContiguous()) break
            observations += HeaderObservation(
                text = span.joinToString(" ") { it.text },
                geometry = span.map { it.geometry }.coveringGeometry(),
            )
        }
    }
    return observations
}

private data class HeaderElement(val text: String, val geometry: CandidateGeometry)

private fun List<HeaderElement>.isContiguous(): Boolean {
    if (size < 2) return true
    return zipWithNext().all { (left, right) ->
        val leftCenter = left.geometry.boundingBox.centerX()
        val rightCenter = right.geometry.boundingBox.centerX()
        val gap = right.geometry.boundingBox.left - left.geometry.boundingBox.right
        rightCenter >= leftCenter &&
            gap <= max(
                left.geometry.boundingBox.height(),
                right.geometry.boundingBox.height(),
            ) * 2.5f
    }
}

private fun List<CandidateGeometry>.coveringGeometry(): CandidateGeometry {
    val box = RawOcrBoundingBox(
        left = minOf { it.boundingBox.left },
        top = minOf { it.boundingBox.top },
        right = maxOf { it.boundingBox.right },
        bottom = maxOf { it.boundingBox.bottom },
    )
    val corners = takeIf { all { !it.cornerPoints.isNullOrEmpty() } }
        ?.flatMap { it.cornerPoints.orEmpty() }
    return CandidateGeometry(box, corners)
}

private fun RawOcrElement.rankObservation(sourceWidth: Int, sourceHeight: Int): RankObservation? {
    val rank = text.trim().takeIf { it.matches(Regex("0?([1-9]|1[0-2])")) }?.toIntOrNull() ?: return null
    val candidate = geometry?.toCandidateGeometry() ?: return null
    val center = candidate.center(sourceWidth, sourceHeight)
    return RankObservation(
        rank = rank,
        centerX = center.x,
        centerY = center.y,
        width = candidate.boundingBox.width().toFloat().coerceAtLeast(1f),
    )
}

private fun MutableList<RankObservation>.score(): Int {
    val byRank = groupBy { it.rank }.values.mapNotNull { it.singleOrNull() }.sortedBy { it.rank }
    val increasingPairs = byRank.zipWithNext().count { (left, right) -> right.centerY > left.centerY }
    return byRank.size * 100 + increasingPairs * 10
}

private fun RawOcrGeometry.toCandidateGeometry(): CandidateGeometry? {
    val corners = cornerPoints?.takeIf { it.isNotEmpty() }
    val cornerBox = corners?.let {
        RawOcrBoundingBox(
            left = it.minOf { point -> point.x },
            top = it.minOf { point -> point.y },
            right = it.maxOf { point -> point.x },
            bottom = it.maxOf { point -> point.y },
        )
    }
    val box = boundingBox?.takeIf { it.right > it.left && it.bottom > it.top }
        ?: cornerBox
        ?: return null
    if (box.right <= box.left || box.bottom <= box.top) return null
    return CandidateGeometry(box, corners)
}

private fun RawOcrBoundingBox.centerX(): Float = (left + right) / 2f

private fun RawOcrBoundingBox.width(): Int = right - left

private fun RawOcrBoundingBox.height(): Int = bottom - top

private fun normalize(value: String): String = value
    .trim()
    .lowercase(Locale.ROOT)
    .replace(Regex("\\s+"), " ")
    .trimEnd('.', '!', '?', ',', ';', ':')

private fun List<Float>.median(): Float = when {
    isEmpty() -> 0f
    size % 2 == 1 -> sorted()[size / 2]
    else -> {
        val sorted = sorted()
        (sorted[size / 2 - 1] + sorted[size / 2]) / 2f
    }
}

private inline fun <T> Iterable<T>.minOf(selector: (T) -> Int): Int = minOfOrNull(selector) ?: 0

private inline fun <T> Iterable<T>.maxOf(selector: (T) -> Int): Int = maxOfOrNull(selector) ?: 0
