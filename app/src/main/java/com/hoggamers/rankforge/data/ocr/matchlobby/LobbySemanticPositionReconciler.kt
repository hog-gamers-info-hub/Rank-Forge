package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition

/** Result of processing one physical asset before semantic reconciliation. */
internal sealed interface LobbyPhysicalProcessingOutcome {
    val storedPosition: RosterScreenshotPosition

    data class Resolved(
        override val storedPosition: RosterScreenshotPosition,
        val semanticPosition: RosterScreenshotPosition,
        val slots: List<MatchLobbySlotNumberOcrSlot>,
        val teamCropPreviews: MatchLobbyTeamCropPreviewResult,
    ) : LobbyPhysicalProcessingOutcome

    data class Unavailable(
        override val storedPosition: RosterScreenshotPosition,
        val reason: MatchLobbySlotNumberOcrUnavailableReason,
    ) : LobbyPhysicalProcessingOutcome
}

/**
 * Reconciles physical processing outcomes into the canonical semantic order.
 * A semantic position is emitted at most once; duplicates become an explicit
 * unavailable conflict instead of being overwritten.
 */
internal object LobbySemanticPositionReconciler {
    fun reconcile(
        outcomes: List<LobbyPhysicalProcessingOutcome>,
    ): MatchLobbySlotNumberOcrResult {
        val resolvedByPosition = outcomes
            .filterIsInstance<LobbyPhysicalProcessingOutcome.Resolved>()
            .groupBy { it.semanticPosition }
        val fallbackReason = fallbackUnavailableReason(outcomes)

        return MatchLobbySlotNumberOcrResult(
            RosterScreenshotPosition.entries.map { semanticPosition ->
                when (val resolved = resolvedByPosition[semanticPosition].orEmpty().size) {
                    0 -> MatchLobbySlotNumberOcrScreenshotResult.Unavailable(
                        screenshotPosition = semanticPosition,
                        reason = fallbackReason,
                    )
                    1 -> resolvedByPosition.getValue(semanticPosition).single().let { one ->
                        MatchLobbySlotNumberOcrScreenshotResult.Processed(
                            screenshotPosition = semanticPosition,
                            slots = one.slots,
                            teamCropPreviews = one.teamCropPreviews,
                        )
                    }
                    else -> MatchLobbySlotNumberOcrScreenshotResult.Unavailable(
                        screenshotPosition = semanticPosition,
                        reason = MatchLobbySlotNumberOcrUnavailableReason.SEMANTIC_POSITION_CONFLICT,
                    )
                }
            },
        )
    }

    private fun fallbackUnavailableReason(
        outcomes: List<LobbyPhysicalProcessingOutcome>,
    ): MatchLobbySlotNumberOcrUnavailableReason {
        if (outcomes.any { it is LobbyPhysicalProcessingOutcome.Resolved }) {
            return MatchLobbySlotNumberOcrUnavailableReason.SEMANTIC_POSITION_UNRESOLVED
        }
        val reasons = outcomes
            .filterIsInstance<LobbyPhysicalProcessingOutcome.Unavailable>()
            .map { it.reason }
            .distinct()
        return reasons.singleOrNull()
            ?: MatchLobbySlotNumberOcrUnavailableReason.SEMANTIC_POSITION_UNRESOLVED
    }
}
