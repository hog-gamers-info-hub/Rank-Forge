package com.hoggamers.rankforge.domain.ocr.matchlobby

/**
 * Controls the expensive PP-OCRv6 rescue path for lobby auto-crop.
 *
 * ML Kit remains the primary engine. PP-OCRv6 is invoked only when the normal
 * ML Kit resolver leaves exactly one directly observed lobby-slot anchor across
 * all three screenshot groups. Zero anchors fall back to manual crop, while two
 * or more anchors continue through the existing reconstruction path without
 * paying PP-OCRv6 latency.
 */
object LobbyPpOcrFallbackPolicy {
    fun shouldRunPpOcr(
        mlKitGroups: List<LobbyResolvedOcrAnchorGroup>,
    ): Boolean = mlKitGroups.sumOf { it.directlyObservedAnchorCount } == ONE_ANCHOR

    /**
     * ML Kit remains authoritative for any slot it already resolved. PP-OCRv6
     * observations for those same slot numbers are removed before evidence is
     * merged, preventing slightly different engine boxes for the same physical
     * glyph from creating artificial ambiguity in the existing resolver.
     */
    fun ppObservationsForMerge(
        mlKitGroups: List<LobbyResolvedOcrAnchorGroup>,
        ppObservations: List<LobbyOcrAnchorObservation>,
    ): List<LobbyOcrAnchorObservation> {
        val resolvedMlKitSlots = mlKitGroups
            .flatMap { it.anchors }
            .map { it.anchor.slotNumber }
            .toSet()

        return ppObservations.filterNot { observation ->
            observation.text.trim().toIntOrNull() in resolvedMlKitSlots
        }
    }

    private const val ONE_ANCHOR = 1
}
