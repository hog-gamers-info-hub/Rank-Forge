package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCrop

data class MatchResultPanelPpPositionEvidence(
    val crop: MatchResultPositionCrop,
    val blocks: List<RawOcrBlock>,
)

/** Maps one whole-panel PP-OCR result into the existing position-local evidence contract. */
object MatchResultPanelPpMapper {
    fun map(
        panelBlocks: List<RawOcrBlock>,
        crops: List<MatchResultPositionCrop>,
    ): List<MatchResultPanelPpPositionEvidence> {
        val orderedCrops = crops.sortedWith(compareBy<MatchResultPositionCrop> { it.position }.thenBy { it.column })
        val assignedLines = mutableSetOf<LineKey>()

        return orderedCrops.map { crop ->
            val localBlocks = panelBlocks.mapIndexedNotNull { blockIndex, block ->
                val localLines = block.lines.mapIndexedNotNull { lineIndex, line ->
                    val key = LineKey(blockIndex, lineIndex)
                    val panelBounds = line.geometry?.boundingBox ?: return@mapIndexedNotNull null
                    if (key in assignedLines || !panelBounds.isAssignedTo(crop)) {
                        return@mapIndexedNotNull null
                    }
                    val localLine = line.toLocal(crop)
                        ?: return@mapIndexedNotNull null
                    assignedLines += key
                    localLine
                }
                block.copy(
                    geometry = block.geometry?.toLocalGeometry(crop),
                    lines = localLines,
                ).takeIf { it.lines.isNotEmpty() }
            }
            MatchResultPanelPpPositionEvidence(crop, localBlocks)
        }
    }

    private data class LineKey(val blockIndex: Int, val lineIndex: Int)

    private fun RawOcrBoundingBox.isAssignedTo(crop: MatchResultPositionCrop): Boolean {
        val bounds = crop.bounds
        val lineLeft = left.toDouble()
        val lineTop = top.toDouble()
        val lineRight = right.toDouble()
        val lineBottom = bottom.toDouble()
        val cropLeft = bounds.left.toDouble()
        val cropTop = bounds.top.toDouble()
        val cropRight = bounds.right.toDouble()
        val cropBottom = bounds.bottom.toDouble()
        if (listOf(
                lineLeft, lineTop, lineRight, lineBottom,
                cropLeft, cropTop, cropRight, cropBottom,
            ).any { !it.isFinite() }
        ) return false
        if (lineRight <= lineLeft || lineBottom <= lineTop ||
            cropRight <= cropLeft || cropBottom <= cropTop
        ) return false

        val centerX = (lineLeft + lineRight) / 2.0
        val centerY = (lineTop + lineBottom) / 2.0
        if (centerX !in cropLeft..<cropRight || centerY !in cropTop..<cropBottom) {
            return false
        }

        val intersectionLeft = maxOf(lineLeft, cropLeft)
        val intersectionTop = maxOf(lineTop, cropTop)
        val intersectionRight = minOf(lineRight, cropRight)
        val intersectionBottom = minOf(lineBottom, cropBottom)
        if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
            return false
        }

        val lineArea = (lineRight - lineLeft) * (lineBottom - lineTop)
        val intersectionArea =
            (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val overlapRatio = intersectionArea / lineArea
        return lineArea.isFinite() && intersectionArea.isFinite() &&
            overlapRatio.isFinite() && overlapRatio >= MIN_MEANINGFUL_LINE_OVERLAP_RATIO
    }

    private fun RawOcrLine.toLocal(crop: MatchResultPositionCrop): RawOcrLine? = copy(
        geometry = geometry?.toLocalGeometry(crop),
        elements = elements.map { element ->
            element.copy(
                geometry = element.geometry?.toLocalGeometry(crop),
                symbols = element.symbols.map { symbol ->
                    symbol.copy(geometry = symbol.geometry?.toLocalGeometry(crop))
                },
            )
        },
    ).takeIf { it.geometry?.boundingBox?.isUsable() == true }

    private fun RawOcrGeometry.toLocalGeometry(crop: MatchResultPositionCrop): RawOcrGeometry? {
        val bounds = crop.bounds
        val localBounds = boundingBox?.let { box ->
            RawOcrBoundingBox(
                left = (box.left - bounds.left).coerceIn(0, bounds.width),
                top = (box.top - bounds.top).coerceIn(0, bounds.height),
                right = (box.right - bounds.left).coerceIn(0, bounds.width),
                bottom = (box.bottom - bounds.top).coerceIn(0, bounds.height),
            ).takeIf { it.isUsable() }
        }
        val localPoints = cornerPoints?.map { point ->
            RawOcrPoint(
                x = (point.x - bounds.left).coerceIn(0, bounds.width),
                y = (point.y - bounds.top).coerceIn(0, bounds.height),
            )
        }?.takeIf { it.isNotEmpty() }
        return if (localBounds == null && localPoints == null) null else RawOcrGeometry(localBounds, localPoints)
    }

    private fun RawOcrBoundingBox.isUsable(): Boolean = right > left && bottom > top

    private const val MIN_MEANINGFUL_LINE_OVERLAP_RATIO = 0.50
}
