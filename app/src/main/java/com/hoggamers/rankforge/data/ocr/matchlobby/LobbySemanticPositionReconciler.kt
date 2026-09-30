package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropBounds

enum class LobbySemanticResolutionSource {
    CURRENT_ANCHOR_GRID,
    FIXED_QUADRANT_ANCHOR,
    ELIMINATION,
    PREVIEW_SEQUENCE,
}

internal data class LobbyFixedQuadrantFallbackPreview(
    val visibleSlotPosition: RosterVisibleSlotPosition,
    val detectedSlotNumber: Int?,
    val image: MatchLobbyTeamCropPreviewImage,
    val playerRowPreviews: List<LobbyPlayerRowCropPreview>,
    val bounds: LobbyTeamCropBounds,
)

/** Result of processing one physical asset before semantic reconciliation. */
internal sealed interface LobbyPhysicalProcessingOutcome {
    val storedPosition: RosterScreenshotPosition

    data class Resolved(
        override val storedPosition: RosterScreenshotPosition,
        val semanticPosition: RosterScreenshotPosition,
        val slots: List<MatchLobbySlotNumberOcrSlot>,
        val teamCropPreviews: MatchLobbyTeamCropPreviewResult,
        val resolutionSource: LobbySemanticResolutionSource =
            LobbySemanticResolutionSource.CURRENT_ANCHOR_GRID,
    ) : LobbyPhysicalProcessingOutcome

    data class FallbackCandidate(
        override val storedPosition: RosterScreenshotPosition,
        val slots: List<MatchLobbySlotNumberOcrSlot>,
        val semanticHint: RosterScreenshotPosition?,
        val previews: List<LobbyFixedQuadrantFallbackPreview>,
        val unavailable: List<MatchLobbyTeamCropPreviewOutcome.Unavailable>,
    ) : LobbyPhysicalProcessingOutcome {
        init {
            val positions = previews.map { it.visibleSlotPosition } + unavailable.map { it.visibleSlotPosition }
            require(
                positions.size == RosterVisibleSlotPosition.entries.size &&
                    positions.toSet().size == positions.size &&
                    positions.toSet() == RosterVisibleSlotPosition.entries.toSet(),
            ) {
                "Fixed-quadrant fallback must contain one outcome for every visible slot position."
            }
            require(slots.map { it.visibleSlotPosition } == RosterVisibleSlotPosition.entries)
        }
    }

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
        val strongConflicts = resolvedByPosition
            .filterValues { it.size > 1 }
            .keys
        val assigned = resolvedByPosition
            .filterValues { it.size == 1 }
            .mapValues { (_, values) ->
                AssignedOutcome(
                    outcome = values.single(),
                    source = LobbySemanticResolutionSource.CURRENT_ANCHOR_GRID,
                )
            }
            .toMutableMap()
        val fallbackCandidates = outcomes.filterIsInstance<LobbyPhysicalProcessingOutcome.FallbackCandidate>()
        val fallbackHintCounts = fallbackCandidates
            .mapNotNull { candidate -> candidate.semanticHint }
            .groupingBy { it }
            .eachCount()
        val unresolvedFallback = fallbackCandidates.filter { candidate ->
            val hint = candidate.semanticHint
            val acceptsHint = hint != null &&
                hint !in assigned &&
                hint !in strongConflicts &&
                fallbackHintCounts[hint] == 1
            if (acceptsHint) {
                assigned[requireNotNull(hint)] = AssignedOutcome(
                    outcome = candidate,
                    source = LobbySemanticResolutionSource.FIXED_QUADRANT_ANCHOR,
                )
            }
            !acceptsHint
        }.toMutableList()

        val usableCount = outcomes.count {
            it is LobbyPhysicalProcessingOutcome.Resolved ||
                it is LobbyPhysicalProcessingOutcome.FallbackCandidate
        }
        val remainingSemanticPositions = RosterScreenshotPosition.entries.filter { position ->
            position !in assigned && position !in strongConflicts
        }
        if (usableCount == RosterScreenshotPosition.entries.size && unresolvedFallback.isNotEmpty()) {
            if (unresolvedFallback.size == 1 && remainingSemanticPositions.size == 1) {
                assigned[remainingSemanticPositions.single()] = AssignedOutcome(
                    outcome = unresolvedFallback.removeAt(0),
                    source = LobbySemanticResolutionSource.ELIMINATION,
                )
            } else {
                unresolvedFallback
                    .sortedBy { it.storedPosition.index }
                    .zip(remainingSemanticPositions)
                    .forEach { (candidate, semanticPosition) ->
                        assigned[semanticPosition] = AssignedOutcome(
                            outcome = candidate,
                            source = LobbySemanticResolutionSource.PREVIEW_SEQUENCE,
                        )
                    }
            }
        }
        val fallbackReason = fallbackUnavailableReason(outcomes)

        return MatchLobbySlotNumberOcrResult(
            RosterScreenshotPosition.entries.map { semanticPosition ->
                when {
                    semanticPosition in strongConflicts -> MatchLobbySlotNumberOcrScreenshotResult.Unavailable(
                        screenshotPosition = semanticPosition,
                        reason = MatchLobbySlotNumberOcrUnavailableReason.SEMANTIC_POSITION_CONFLICT,
                    )
                    assigned[semanticPosition] != null -> assigned.getValue(semanticPosition).toScreenshotResult(
                        semanticPosition = semanticPosition,
                    )
                    else -> MatchLobbySlotNumberOcrScreenshotResult.Unavailable(
                        screenshotPosition = semanticPosition,
                        reason = fallbackReason,
                    )
                }
            },
        )
    }

    private fun AssignedOutcome.toScreenshotResult(
        semanticPosition: RosterScreenshotPosition,
    ): MatchLobbySlotNumberOcrScreenshotResult.Processed = when (val outcome = outcome) {
        is LobbyPhysicalProcessingOutcome.Resolved -> MatchLobbySlotNumberOcrScreenshotResult.Processed(
            screenshotPosition = semanticPosition,
            slots = outcome.slots,
            teamCropPreviews = outcome.teamCropPreviews,
            resolutionSource = outcome.resolutionSource,
        )
        is LobbyPhysicalProcessingOutcome.FallbackCandidate -> outcome.toProcessed(
            semanticPosition = semanticPosition,
            source = source,
        )
        is LobbyPhysicalProcessingOutcome.Unavailable -> error("Unavailable outcomes cannot be assigned.")
    }

    private fun LobbyPhysicalProcessingOutcome.FallbackCandidate.toProcessed(
        semanticPosition: RosterScreenshotPosition,
        source: LobbySemanticResolutionSource,
    ): MatchLobbySlotNumberOcrScreenshotResult.Processed {
        val previews = previews.map { preview ->
            MatchLobbyTeamCropPreview(
                visibleSlotPosition = preview.visibleSlotPosition,
                // The raw OCR candidate remains unavailable for inferred quadrants.
                // This field is the existing UI crop identity; raw evidence is kept in slots.
                detectedSlotNumber = semanticPosition.tournamentSlotFor(preview.visibleSlotPosition),
                image = preview.image,
                playerRowPreviews = preview.playerRowPreviews,
                authoritativeTeamSlotNumber = semanticPosition.tournamentSlotFor(
                    preview.visibleSlotPosition,
                ),
                bounds = preview.bounds,
            )
        }
        return MatchLobbySlotNumberOcrScreenshotResult.Processed(
            screenshotPosition = semanticPosition,
            slots = slots,
            teamCropPreviews = MatchLobbyTeamCropPreviewResult.Available(
                previews = previews,
                unavailable = unavailable,
            ),
            resolutionSource = source,
        )
    }

    private fun fallbackUnavailableReason(
        outcomes: List<LobbyPhysicalProcessingOutcome>,
    ): MatchLobbySlotNumberOcrUnavailableReason {
        if (outcomes.any {
                it is LobbyPhysicalProcessingOutcome.Resolved ||
                    it is LobbyPhysicalProcessingOutcome.FallbackCandidate
            }
        ) {
            return MatchLobbySlotNumberOcrUnavailableReason.SEMANTIC_POSITION_UNRESOLVED
        }
        val reasons = outcomes
            .filterIsInstance<LobbyPhysicalProcessingOutcome.Unavailable>()
            .map { it.reason }
            .distinct()
        return reasons.singleOrNull()
            ?: MatchLobbySlotNumberOcrUnavailableReason.SEMANTIC_POSITION_UNRESOLVED
    }

    private data class AssignedOutcome(
        val outcome: LobbyPhysicalProcessingOutcome,
        val source: LobbySemanticResolutionSource,
    )
}
