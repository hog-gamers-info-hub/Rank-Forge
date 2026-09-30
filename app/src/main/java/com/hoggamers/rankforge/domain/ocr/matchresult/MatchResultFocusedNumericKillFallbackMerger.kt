package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole

data class MatchResultFocusedNumericKillFallbackTarget(
    val role: MatchResultScreenshotRole,
    val position: Int,
    val slot: Int,
    val row: MatchResultPositionRowCrop,
)

data class MatchResultFocusedNumericKillFallbackKey(
    val role: MatchResultScreenshotRole,
    val position: Int,
    val slot: Int,
)

/** Selects and merges only unresolved kills eligible for the final focused numeric fallback. */
class MatchResultFocusedNumericKillFallbackMerger {
    fun eligibleTargets(
        baseSemantics: List<MatchResultPositionSemanticResult>,
        rowCropsByPosition: Map<Int, List<MatchResultPositionRowCrop>>,
        ppMarkerSeenSlotsByPosition: Map<Int, Set<Int>>,
        mlKitMarkerSeenSlotsByPosition: Map<Int, Set<Int>>,
    ): List<MatchResultFocusedNumericKillFallbackTarget> = baseSemantics.flatMap { semantic ->
        val row = semantic.row ?: return@flatMap emptyList()
        val ppMarkerSeen = ppMarkerSeenSlotsByPosition[semantic.position].orEmpty()
        val mlKitMarkerSeen = mlKitMarkerSeenSlotsByPosition[semantic.position].orEmpty()
        (1..4).mapNotNull { slot ->
            val playerSlot = row.playerSlots.firstOrNull { it.slot == slot } ?: return@mapNotNull null
            if (
                playerSlot.player.resolvedText.isBlank() ||
                playerSlot.kill.resolvedText.isNotBlank() ||
                semantic.killVerifications[slot] is MatchResultNumericVerification.Conflict ||
                slot in ppMarkerSeen ||
                slot in mlKitMarkerSeen
            ) {
                return@mapNotNull null
            }
            val rowCrop = rowCropsByPosition[semantic.position]
                .orEmpty()
                .firstOrNull { it.rowIndex == MatchResultKillFieldLayout.rowIndexForSlot(slot) }
                ?: return@mapNotNull null
            MatchResultFocusedNumericKillFallbackTarget(
                role = semantic.role,
                position = semantic.position,
                slot = slot,
                row = rowCrop,
            )
        }
    }

    fun merge(
        baseSemantics: List<MatchResultPositionSemanticResult>,
        verifiedKills: Map<MatchResultFocusedNumericKillFallbackKey, MatchResultNumericVerification>,
    ): List<MatchResultPositionSemanticResult> = baseSemantics.map { base ->
        val basePlayers = base.fields
            .filter { it.type == MatchResultOcrFieldType.PLAYER && it.slot != null }
            .associateBy { it.slot!! }
        val baseKills = base.fields
            .filter { it.type == MatchResultOcrFieldType.KILL && it.slot != null }
            .associateBy { it.slot!! }
        val verifiedBySlot = (1..4).mapNotNull { slot ->
            val player = basePlayers[slot] ?: return@mapNotNull null
            val kill = baseKills[slot] ?: return@mapNotNull null
            if (player.resolvedText.isBlank() || kill.resolvedText.isNotBlank()) {
                return@mapNotNull null
            }
            if (base.killVerifications[slot] is MatchResultNumericVerification.Conflict) {
                return@mapNotNull null
            }
            val verification = verifiedKills[
                MatchResultFocusedNumericKillFallbackKey(base.role, base.position, slot),
            ] as? MatchResultNumericVerification.Verified ?: return@mapNotNull null
            slot to verification
        }.toMap()
        if (verifiedBySlot.isEmpty()) return@map base

        val fields = base.fields.map { field ->
            val verification = verifiedBySlot[field.slot]
            if (field.type != MatchResultOcrFieldType.KILL || verification == null || field.resolvedText.isNotBlank()) {
                field
            } else {
                val rawText = verification.candidates
                    .firstOrNull { it.value == verification.value }
                    ?.rawText
                    .orEmpty()
                field.copy(
                    ocrText = rawText,
                    resolvedText = verification.value.toString(),
                    status = MatchResultOcrFieldStatus.FOCUSED_NUMERIC_FALLBACK,
                )
            }
        }
        val baseRow = base.row ?: return@map base
        val row = runCatching {
            MatchResultOcrRowAssembler.assemble(
                position = base.position,
                source = baseRow.source,
                fields = fields,
                visualRow = baseRow.placement.visualRow,
            )
        }.getOrNull() ?: return@map base
        val presentPlayersHaveKills = fields
            .filter { it.type == MatchResultOcrFieldType.PLAYER && it.resolvedText.isNotBlank() }
            .all { player ->
                fields.firstOrNull {
                    it.type == MatchResultOcrFieldType.KILL && it.slot == player.slot
                }?.resolvedText?.isNotBlank() == true
            }
        val updatedVerifications = base.killVerifications + verifiedKillsFor(verifiedBySlot)
        val noKillConflict = updatedVerifications
            .values
            .none { it is MatchResultNumericVerification.Conflict }
        base.copy(
            fields = fields,
            row = row,
            killVerifications = updatedVerifications,
            isAutoAcceptable = base.structuralIdentityValid &&
                base.placementVerification !is MatchResultNumericVerification.Conflict &&
                presentPlayersHaveKills &&
                noKillConflict,
        )
    }

    private fun verifiedKillsFor(
        verifiedBySlot: Map<Int, MatchResultNumericVerification.Verified>,
    ): Map<Int, MatchResultNumericVerification> = verifiedBySlot.mapKeys { (slot, _) ->
        slot
    }.mapValues { (_, verification) -> verification }
}
