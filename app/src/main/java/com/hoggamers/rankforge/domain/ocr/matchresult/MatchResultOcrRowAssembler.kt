package com.hoggamers.rankforge.domain.ocr.matchresult

object MatchResultOcrRowAssembler {
    fun assemble(
        position: Int,
        source: MatchResultOcrRowSource,
        fields: List<MatchResultOcrField>,
        visualRow: MatchResultOcrVisualRow? = null,
    ): MatchResultOcrRow {
        val rowFields = fields.filter {
            it.position == position && (visualRow == null || it.visualRow == visualRow)
        }
        val placement = requireNotNull(
            rowFields.firstOrNull { it.type == MatchResultOcrFieldType.PLACEMENT },
        ) { "Missing placement field for match-result position $position." }

        val slots = (1..4).mapNotNull { slot ->
            val player = rowFields.firstOrNull {
                it.type == MatchResultOcrFieldType.PLAYER && it.slot == slot
            } ?: return@mapNotNull null
            val kill = rowFields.firstOrNull {
                it.type == MatchResultOcrFieldType.KILL && it.slot == slot
            } ?: return@mapNotNull null
            if (player.resolvedText.isBlank() && kill.resolvedText.isBlank()) {
                null
            } else {
                MatchResultOcrPlayerSlot(slot, player, kill)
            }
        }

        return MatchResultOcrRow(
            position = position,
            source = source,
            placement = placement,
            playerSlots = slots,
        )
    }
}
