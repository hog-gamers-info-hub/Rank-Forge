package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Restores whole-position Paddle coordinates to the original position bitmap. */
object MatchResultPositionOcrGeometryMapper {
    fun mapBlocks(
        blocks: List<RawOcrBlock>,
        scale: MatchResultRowOcrCandidate,
        positionWidth: Int,
        positionHeight: Int,
    ): List<RawOcrBlock> = blocks.map { block ->
        block.copy(
            geometry = mapGeometry(block.geometry, scale.scale, positionWidth, positionHeight),
            lines = block.lines.map { line ->
                line.copy(
                    geometry = mapGeometry(line.geometry, scale.scale, positionWidth, positionHeight),
                    elements = line.elements.map { element ->
                        element.copy(
                            geometry = mapGeometry(element.geometry, scale.scale, positionWidth, positionHeight),
                            symbols = element.symbols.map { symbol ->
                                symbol.copy(geometry = mapGeometry(symbol.geometry, scale.scale, positionWidth, positionHeight))
                            },
                        )
                    },
                )
            },
        )
    }

    private fun mapGeometry(
        geometry: RawOcrGeometry?,
        scale: Int,
        width: Int,
        height: Int,
    ): RawOcrGeometry? {
        if (geometry == null) return null
        val box = geometry.boundingBox?.let { original ->
            val left = coordinate(original.left.toDouble() / scale, width, upper = false)
            val top = coordinate(original.top.toDouble() / scale, height, upper = false)
            val right = coordinate(original.right.toDouble() / scale, width, upper = true)
            val bottom = coordinate(original.bottom.toDouble() / scale, height, upper = true)
            RawOcrBoundingBox(left, top, right, bottom).takeIf { it.right > it.left && it.bottom > it.top }
        }
        val points = geometry.cornerPoints?.mapNotNull { point ->
            val x = (point.x.toDouble() / scale).takeIf(Double::isFinite) ?: return@mapNotNull null
            val y = (point.y.toDouble() / scale).takeIf(Double::isFinite) ?: return@mapNotNull null
            RawOcrPoint(
                x = x.roundToInt().coerceIn(0, width),
                y = y.roundToInt().coerceIn(0, height),
            )
        }?.takeIf { it.isNotEmpty() }
        return if (box == null && points == null) null else RawOcrGeometry(box, points)
    }

    private fun coordinate(value: Double, maximum: Int, upper: Boolean): Int {
        if (!value.isFinite()) return 0
        val rounded = if (upper) ceil(value).toInt() else floor(value).toInt()
        return rounded.coerceIn(0, maximum)
    }
}
