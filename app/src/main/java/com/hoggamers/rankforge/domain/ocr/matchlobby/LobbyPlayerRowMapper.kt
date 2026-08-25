package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox

data class LobbyPlayerOcrFragment(
    val rawText: String,
    val boundingBox: RawOcrBoundingBox?,
    val isSlotNumberEvidence: Boolean = false,
)

data class LobbyPlayerRowEvidence(
    val row: LobbyPlayerRow,
    val fragments: List<LobbyPlayerOcrFragment>,
    val unionBoundingBox: RawOcrBoundingBox?,
) {
    val structuralText: String?
        get() = fragments
            .joinToString(separator = "") { it.rawText }
            .trim()
            .takeIf { it.isNotEmpty() }
}

data class LobbyPlayerRowMapping(
    val rows: List<LobbyPlayerRowEvidence>,
) {
    init {
        require(rows.map { it.row } == LobbyPlayerRow.entries.toList()) {
            "Player row mapping must contain Row 1 through Row 4 in order."
        }
    }

    fun row(row: LobbyPlayerRow): LobbyPlayerRowEvidence = rows[row.ordinal]
}

object LobbyPlayerRowMapper {
    fun map(
        rowBands: LobbyPlayerRowBands,
        fragments: List<LobbyPlayerOcrFragment>,
        selectedSlotBoundingBox: RawOcrBoundingBox? = null,
        slotGutterRight: Int? = null,
    ): LobbyPlayerRowMapping {
        val grouped = LobbyPlayerRow.entries.associateWith { mutableListOf<LobbyPlayerOcrFragment>() }

        fragments.forEach { fragment ->
            val box = fragment.boundingBox ?: return@forEach
            if (shouldExclude(
                    fragment = fragment,
                    box = box,
                    selectedSlotBoundingBox = selectedSlotBoundingBox,
                    slotGutterRight = slotGutterRight,
                )
            ) {
                return@forEach
            }
            val centerY = (box.top + box.bottom) / 2.0
            rowBands.bandFor(centerY)?.let { band ->
                grouped.getValue(band.row) += fragment
            }
        }

        return LobbyPlayerRowMapping(
            rows = LobbyPlayerRow.entries.map { row ->
                val ordered = grouped.getValue(row).sortedWith(fragmentComparator)
                LobbyPlayerRowEvidence(
                    row = row,
                    fragments = ordered,
                    unionBoundingBox = ordered.mapNotNull { it.boundingBox }.unionBoundsOrNull(),
                )
            },
        )
    }

    private fun shouldExclude(
        fragment: LobbyPlayerOcrFragment,
        box: RawOcrBoundingBox,
        selectedSlotBoundingBox: RawOcrBoundingBox?,
        slotGutterRight: Int?,
    ): Boolean {
        if (fragment.isSlotNumberEvidence) return true
        if (!box.isPositive()) return true
        if (selectedSlotBoundingBox?.overlaps(box) == true) return true
        if (slotGutterRight != null && box.right <= slotGutterRight) return true
        return false
    }

    private fun List<RawOcrBoundingBox>.unionBoundsOrNull(): RawOcrBoundingBox? {
        if (isEmpty()) return null
        return RawOcrBoundingBox(
            left = minOf { it.left },
            top = minOf { it.top },
            right = maxOf { it.right },
            bottom = maxOf { it.bottom },
        )
    }

    private fun RawOcrBoundingBox.isPositive(): Boolean = right > left && bottom > top

    private fun RawOcrBoundingBox.overlaps(other: RawOcrBoundingBox): Boolean =
        left < other.right &&
            other.left < right &&
            top < other.bottom &&
            other.top < bottom

    private val fragmentComparator = compareBy<LobbyPlayerOcrFragment> {
        it.boundingBox?.left ?: Int.MAX_VALUE
    }.thenBy {
        it.boundingBox?.top ?: Int.MAX_VALUE
    }.thenBy {
        it.boundingBox?.right ?: Int.MAX_VALUE
    }.thenBy {
        it.boundingBox?.bottom ?: Int.MAX_VALUE
    }
}
