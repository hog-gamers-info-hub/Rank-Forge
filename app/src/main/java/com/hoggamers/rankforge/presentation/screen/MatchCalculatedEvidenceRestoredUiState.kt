package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchCalculatedEvidence
import com.hoggamers.rankforge.data.local.ResultPositionCalculatedEvidence
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus

private const val RESTORED_EVIDENCE_LABEL = "Restored calculated evidence"
private const val UNAVAILABLE_LABEL = "Unavailable"

internal fun MatchCalculatedEvidence.toRestoredOcrReviewUiState(
    tournamentId: String,
    matchId: String,
    teamNamesBySlot: Map<Int, String>,
): MatchOcrReviewUiState {
    val excludedSourcePositions = result.excludedSourcePositions
    val positions = result.positions.sortedBy { it.position }
    if (positions.isEmpty()) {
        return MatchOcrReviewUiState.Empty(
            tournamentId = tournamentId,
            matchId = matchId,
            teamNamesBySlot = teamNamesBySlot,
        )
    }
    val restoredTeamNames = teamNamesBySlot + positions.mapNotNull { position ->
        val slot = position.slotNumber ?: return@mapNotNull null
        val name = position.teamName?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        slot to name
    }
    val previewRows = positions
        .filter { it.hasRestorableGeometry() }
        .map { it.toRestoredPreviewRow() }
    val reviewRows = positions.map { it.toRestoredReviewRow() }
    val initialDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(reviewRows)
    val correctionDraft = MatchOcrReviewCorrectionDraftReducer.validate(
        initialDraft.copy(
            rows = initialDraft.rows.map { draft ->
                val row = reviewRows.first { it.rowIndex == draft.rowIndex }
                draft.copy(
                    placementDraftValue = row.detectedPlacementDisplayValue,
                    killsDraftValue = row.detectedKillDisplayValue,
                    assignedTeamSlotDraftValue = row.suggestedTeamSlotDisplayValue,
                    isExcluded = draft.rowIndex + 1 in excludedSourcePositions,
                )
            },
        ),
    )
    val preview = MatchResultOcrPreviewUiState.Ready(
        roles = positions.mapNotNull { it.sourceScreenshotRole }.distinct(),
        rows = previewRows,
        ignoredLowerRows = emptyList(),
        manualReviewRows = emptyList(),
    )
    return MatchOcrReviewUiState.Ready(
        tournamentId = tournamentId,
        matchId = matchId,
        rowCount = reviewRows.size,
        rows = reviewRows,
        blockerCount = correctionDraft.blockerCount,
        warningCount = correctionDraft.warningCount,
        safeRowCount = reviewRows.count { it.blockerLabels.isEmpty() },
        manualRequiredRowCount = reviewRows.count { it.blockerLabels.isNotEmpty() },
        reviewRequiredRowCount = 0,
        manualReviewRequired = correctionDraft.blockerCount > 0 || correctionDraft.warningCount > 0,
        hasUnavailableEvidence = reviewRows.any { it.blockerLabels.isNotEmpty() },
        correctionDraft = correctionDraft,
        matchResultOcrPreview = preview,
        teamNamesBySlot = restoredTeamNames,
        evidenceSource = MatchOcrReviewEvidenceSource.RESTORED_CALCULATED,
    )
}

private fun ResultPositionCalculatedEvidence.hasRestorableGeometry(): Boolean =
    sourceScreenshotRole != null &&
        cropLeft != null &&
        cropTop != null &&
        cropRight != null &&
        cropBottom != null

private fun ResultPositionCalculatedEvidence.displayPlacement(): String =
    placement?.toString().orEmpty()

private fun ResultPositionCalculatedEvidence.playerNameAt(slot: Int): String =
    playerNames.getOrNull(slot - 1)?.trim()?.takeIf { it.isNotBlank() }
        ?: MATCH_RESULT_NOT_DETECTED_PLAYER

private fun ResultPositionCalculatedEvidence.playerKillApplicability(): List<Boolean> =
    playerKillApplicable?.takeIf { it.size == 4 }
        ?: playerNames.map { name ->
            // Legacy calculated-evidence payloads did not persist OCR status. Only those payloads
            // use the displayed name as a compatibility fallback; new payloads use the flags.
            name?.trim()?.let { it.isNotBlank() && it != MATCH_RESULT_NOT_DETECTED_PLAYER } == true
        }

private fun ResultPositionCalculatedEvidence.toRestoredPreviewRow(): MatchResultOcrPreviewRowUiState {
    val displayPlacement = displayPlacement()
    val applicability = playerKillApplicability()
    return MatchResultOcrPreviewRowUiState(
        position = position,
        role = requireNotNull(sourceScreenshotRole),
        sourceLabel = RESTORED_EVIDENCE_LABEL,
        placementText = displayPlacement,
        slots = (1..4).map { slot ->
            val name = playerNameAt(slot)
            val kills = playerKills.getOrNull(slot - 1)?.toString().orEmpty()
            MatchResultOcrPreviewSlotUiState(
                slot = slot,
                playerText = name,
                playerOcrText = name,
                playerStatusLabel = if (applicability[slot - 1]) {
                    MatchResultOcrFieldStatus.DIRECT_TEXT.name
                } else {
                    MatchResultOcrFieldStatus.EMPTY.name
                },
                killText = kills,
                killOcrText = kills,
                killStatusLabel = RESTORED_EVIDENCE_LABEL,
            )
        },
    )
}

private fun ResultPositionCalculatedEvidence.toRestoredReviewRow(): MatchOcrReviewRowUiState {
    val displayPlacement = displayPlacement()
    val applicability = playerKillApplicability()
    val playerNamesLabel = (1..4).joinToString(", ") { slot ->
        "P$slot ${playerNameAt(slot)}"
    }
    val assignedSlot = slotNumber?.toString().orEmpty()
    val total = totalKills?.toString().orEmpty()
    val blockers = buildList {
        if (displayPlacement.isBlank()) add("Placement unavailable")
        if (total.isBlank()) add("Total kills unavailable")
        if (assignedSlot.isBlank()) add("Team assignment unavailable")
    }
    return MatchOcrReviewRowUiState(
        rowIndex = position - 1,
        expectedPlacementLabel = position.toString(),
        detectedPlacementDisplayValue = displayPlacement,
        placementStatusLabel = RESTORED_EVIDENCE_LABEL,
        detectedKillDisplayValue = total,
        killStatusLabel = RESTORED_EVIDENCE_LABEL,
        detectedPlayerNameEvidenceLabel = playerNamesLabel,
        playerNameStatusLabel = RESTORED_EVIDENCE_LABEL,
        suggestedTeamSlotDisplayValue = assignedSlot,
        confidenceScoreDisplayValue = RESTORED_EVIDENCE_LABEL,
        confidenceTierLabel = RESTORED_EVIDENCE_LABEL,
        assignmentSafetyStatusLabel = if (slotNumber == null) UNAVAILABLE_LABEL else RESTORED_EVIDENCE_LABEL,
        topThreeSuggestionsSummary = listOf(
            "Saved team slot: ${assignedSlot.ifBlank { UNAVAILABLE_LABEL }}",
        ),
        warningLabels = emptyList(),
        blockerLabels = blockers,
        severity = if (blockers.isEmpty()) MatchOcrReviewSeverity.INFORMATIONAL else MatchOcrReviewSeverity.BLOCKING,
        originalParsedPlacementValue = displayPlacement.toIntOrNull(),
        originalParsedKillValue = totalKills,
        originalSuggestedTeamSlot = slotNumber,
        allPlayersSemanticallyNotDetected = playerKillApplicability().all { applicable ->
            !applicable
        },
        playerKillEvidence = (1..4).filter { slot -> applicability[slot - 1] }.map { slot ->
            MatchOcrReviewPlayerKillEvidenceUiState(
                playerSlot = slot,
                originalKillsValue = playerKills.getOrNull(slot - 1)?.toString().orEmpty(),
            )
        },
    )
}
