package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultEliminationPrefixType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionSemanticResult

data class MatchResultRowOcrSemanticSummary(
    val rowIndex: Int,
    val selected: MatchResultRowOcrCandidate,
    val first: MatchResultRowOcrSemanticKill,
    val second: MatchResultRowOcrSemanticKill,
)

data class MatchResultRowOcrSemanticKill(
    val value: String,
    val source: String,
    val fieldStatus: MatchResultOcrFieldStatus,
    val markerMatched: Boolean,
    val prefixType: String,
)

/** Temporary, pure projection of the mapper's actual production kill output. */
object MatchResultRowOcrSemanticDiagnostic {
    fun summarize(
        semantic: MatchResultPositionSemanticResult,
        rowIndex: Int,
        selected: MatchResultRowOcrCandidate,
    ): MatchResultRowOcrSemanticSummary {
        require(rowIndex in 1..2)
        val slots = if (rowIndex == 1) intArrayOf(1, 3) else intArrayOf(2, 4)
        return MatchResultRowOcrSemanticSummary(
            rowIndex = rowIndex,
            selected = selected,
            first = semantic.killForDiagnostic(slots[0]),
            second = semantic.killForDiagnostic(slots[1]),
        )
    }

    private fun MatchResultPositionSemanticResult.killForDiagnostic(slot: Int): MatchResultRowOcrSemanticKill {
        val field = fields.first { it.type == MatchResultOcrFieldType.KILL && it.slot == slot }
        val parsed = basicKillEvidence[slot]
        val source = when {
            parsed?.markerMatched != true -> "UNRESOLVED"
            parsed.prefixType == MatchResultEliminationPrefixType.EXPLICIT_NUMERIC -> "EXPLICIT_NUMERIC"
            parsed.prefixType == MatchResultEliminationPrefixType.O_NORMALIZED -> "O_NORMALIZED"
            else -> "EMPTY_PREFIX_ZERO"
        }
        return MatchResultRowOcrSemanticKill(
            value = field.resolvedText.ifBlank { "UNRESOLVED" },
            source = source,
            fieldStatus = field.status,
            markerMatched = parsed?.markerMatched == true,
            prefixType = parsed?.prefixType?.name ?: "NONE",
        )
    }
}
