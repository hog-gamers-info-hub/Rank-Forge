package com.hoggamers.rankforge.domain.ocr.customdesign

object CustomDesignEditableGridInitializer {
    fun initialize(
        sourceWidth: Int,
        sourceHeight: Int,
        automatic: CustomDesignGridGeometry?,
    ): CustomDesignEditableGridGeometry? {
        if (sourceWidth <= 0 || sourceHeight <= 0) return null

        val usableAutomatic = automatic?.takeIf {
            it.sourceWidth == sourceWidth &&
                it.sourceHeight == sourceHeight
        }
        return CustomDesignEditableGridGeometry(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            columnX = initializeColumns(sourceWidth, usableAutomatic),
            rowY = initializeRows(sourceHeight, usableAutomatic),
        )
    }

    private fun initializeColumns(
        sourceWidth: Int,
        automatic: CustomDesignGridGeometry?,
    ): Map<CustomDesignAnchorField, CustomDesignEditableColumnCoordinate> {
        val automaticColumns = automatic?.columnX
            ?.filterValues { it.isFinite() && it in 0f..sourceWidth.toFloat() }
            .orEmpty()
        val fields = CustomDesignAnchorField.entries
        val canonicalOrderCompatible = fields
            .mapNotNull { field -> automaticColumns[field] }
            .zipWithNext()
            .all { (left, right) -> left < right }
        val result = linkedMapOf<CustomDesignAnchorField, CustomDesignEditableColumnCoordinate>()

        fields.forEach { field ->
            val automaticX = automaticColumns[field]
            if (automaticX != null) {
                result[field] = CustomDesignEditableColumnCoordinate(
                    x = automaticX,
                    source = CustomDesignEditableCoordinateSource.AUTOMATIC,
                )
                return@forEach
            }

            val fieldIndex = fields.indexOf(field)
            val previous = fields
                .subList(0, fieldIndex)
                .mapNotNull { previousField ->
                    automaticColumns[previousField]?.let { previousField to it }
                }
                .lastOrNull()
            val next = fields
                .subList(fieldIndex + 1, fields.size)
                .mapNotNull { nextField -> automaticColumns[nextField]?.let { nextField to it } }
                .firstOrNull()
            val estimatedX = if (canonicalOrderCompatible && previous != null && next != null) {
                val previousIndex = fields.indexOf(previous.first)
                val nextIndex = fields.indexOf(next.first)
                previous.second +
                    (next.second - previous.second) *
                    (fieldIndex - previousIndex).toFloat() /
                    (nextIndex - previousIndex).toFloat()
            } else {
                null
            }
            if (estimatedX != null && estimatedX.isFinite() && estimatedX in 0f..sourceWidth.toFloat()) {
                result[field] = CustomDesignEditableColumnCoordinate(
                    x = estimatedX,
                    source = CustomDesignEditableCoordinateSource.ESTIMATED,
                )
            }
        }

        val occupied = result.values.mapTo(mutableSetOf()) { it.x }
        fields.forEachIndexed { index, field ->
            if (field in result) return@forEachIndexed
            val fallbackX = fallbackColumnX(
                sourceWidth = sourceWidth,
                fieldIndex = index,
                occupied = occupied,
            )
            result[field] = CustomDesignEditableColumnCoordinate(
                x = fallbackX,
                source = CustomDesignEditableCoordinateSource.FALLBACK,
            )
            occupied += fallbackX
        }
        return result
    }

    private fun fallbackColumnX(
        sourceWidth: Int,
        fieldIndex: Int,
        occupied: Set<Float>,
    ): Float {
        val slotCount = CustomDesignAnchorField.entries.size * 4 + 2
        val preferredSlot = fieldIndex + 1
        val candidates = (1..slotCount).asSequence()
            .sortedBy { slot -> kotlin.math.abs(slot - preferredSlot) }
            .map { slot -> sourceWidth * slot.toFloat() / (slotCount + 1).toFloat() }
            .filter { it !in occupied }
        return candidates.firstOrNull() ?:
            (sourceWidth * preferredSlot.toFloat() / (CustomDesignAnchorField.entries.size + 1))
                .coerceIn(0f, sourceWidth.toFloat())
    }

    private fun initializeRows(
        sourceHeight: Int,
        automatic: CustomDesignGridGeometry?,
    ): Map<Int, CustomDesignEditableRowCoordinate> {
        val automaticRows = automatic?.rowY
            ?.filter { (rank, row) ->
                rank in RANK_RANGE &&
                    row.y.isFinite() &&
                    row.y in 0f..sourceHeight.toFloat()
            }
            ?.toSortedMap()
            .orEmpty()
        val orderedAutomaticRows = automaticRows.entries.toList()
        val structurallyValid = orderedAutomaticRows.zipWithNext().all { (left, right) ->
            right.value.y > left.value.y
        }
        if (!structurallyValid || orderedAutomaticRows.isEmpty()) {
            return fallbackRows(sourceHeight)
        }

        val result = linkedMapOf<Int, CustomDesignEditableRowCoordinate>()
        orderedAutomaticRows.forEach { (rank, row) ->
            result[rank] = CustomDesignEditableRowCoordinate(
                y = row.y,
                source = CustomDesignEditableCoordinateSource.AUTOMATIC,
            )
        }

        orderedAutomaticRows.zipWithNext().forEach { (left, right) ->
            val missingCount = right.key - left.key - 1
            if (missingCount <= 0) return@forEach
            repeat(missingCount) { offset ->
                val rank = left.key + offset + 1
                val fraction = (offset + 1).toFloat() / (missingCount + 1).toFloat()
                result[rank] = CustomDesignEditableRowCoordinate(
                    y = left.value.y + (right.value.y - left.value.y) * fraction,
                    source = CustomDesignEditableCoordinateSource.ESTIMATED,
                )
            }
        }

        val step = automatic?.estimatedRowStep?.takeIf { it.isFinite() && it > 0f }
        val firstAutomatic = orderedAutomaticRows.first()
        val leadingRanks = (1 until firstAutomatic.key).toList()
        val leadingEstimates = step?.let { spacing ->
            leadingRanks.map { rank ->
                rank to firstAutomatic.value.y - spacing * (firstAutomatic.key - rank).toFloat()
            }.takeIf { estimates -> estimates.all { (_, y) -> y.isFinite() && y in 0f..sourceHeight.toFloat() } }
        }
        if (leadingRanks.isNotEmpty()) {
            if (leadingEstimates != null) {
                leadingEstimates.forEach { (rank, y) ->
                    result[rank] = CustomDesignEditableRowCoordinate(
                        y = y,
                        source = CustomDesignEditableCoordinateSource.ESTIMATED,
                    )
                }
            } else {
                fillFallbackRows(
                    result = result,
                    ranks = leadingRanks,
                    lowerExclusive = 0f,
                    upperExclusive = firstAutomatic.value.y,
                )
            }
        }

        val lastAutomatic = orderedAutomaticRows.last()
        val trailingRanks = (lastAutomatic.key + 1..LAST_RANK).toList()
        val trailingEstimates = step?.let { spacing ->
            trailingRanks.map { rank ->
                rank to lastAutomatic.value.y + spacing * (rank - lastAutomatic.key).toFloat()
            }.takeIf { estimates -> estimates.all { (_, y) -> y.isFinite() && y in 0f..sourceHeight.toFloat() } }
        }
        if (trailingRanks.isNotEmpty()) {
            if (trailingEstimates != null) {
                trailingEstimates.forEach { (rank, y) ->
                    result[rank] = CustomDesignEditableRowCoordinate(
                        y = y,
                        source = CustomDesignEditableCoordinateSource.ESTIMATED,
                    )
                }
            } else {
                fillFallbackRows(
                    result = result,
                    ranks = trailingRanks,
                    lowerExclusive = lastAutomatic.value.y,
                    upperExclusive = sourceHeight.toFloat(),
                )
            }
        }

        return if (isStrictlyOrderedAndBounded(result, sourceHeight)) {
            result.toSortedMap()
        } else {
            fallbackRows(sourceHeight)
        }
    }

    private fun fillFallbackRows(
        result: MutableMap<Int, CustomDesignEditableRowCoordinate>,
        ranks: List<Int>,
        lowerExclusive: Float,
        upperExclusive: Float,
    ) {
        val span = upperExclusive - lowerExclusive
        ranks.forEachIndexed { index, rank ->
            val fraction = (index + 1).toFloat() / (ranks.size + 1).toFloat()
            result[rank] = CustomDesignEditableRowCoordinate(
                y = lowerExclusive + span * fraction,
                source = CustomDesignEditableCoordinateSource.FALLBACK,
            )
        }
    }

    private fun fallbackRows(sourceHeight: Int): Map<Int, CustomDesignEditableRowCoordinate> =
        (1..LAST_RANK).associateWith { rank ->
            CustomDesignEditableRowCoordinate(
                y = sourceHeight * rank.toFloat() / (LAST_RANK + 1).toFloat(),
                source = CustomDesignEditableCoordinateSource.FALLBACK,
            )
        }

    private fun isStrictlyOrderedAndBounded(
        rows: Map<Int, CustomDesignEditableRowCoordinate>,
        sourceHeight: Int,
    ): Boolean {
        val ordered = RANK_RANGE.mapNotNull { rank -> rows[rank]?.y }
        return ordered.size == RANK_RANGE.count() &&
            ordered.all { it.isFinite() && it in 0f..sourceHeight.toFloat() } &&
            ordered.zipWithNext().all { (left, right) -> right > left }
    }

    private const val LAST_RANK = 12
    private val RANK_RANGE = 1..LAST_RANK
}
