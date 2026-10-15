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
    eligibleTeamSlots: Set<Int> = com.hoggamers.rankforge.domain.tournament.TeamSlot.SLOT_NUMBERS.toSet(),
    lobbySlotByTeamSlot: Map<Int, Int> = emptyMap(),
    usesPairRelativeIdentity: Boolean = false,
): MatchOcrReviewUiState {
    val invalidCanonicalSlot = result.positions
        .asSequence()
        .mapNotNull { it.slotNumber }
        .firstOrNull { it !in eligibleTeamSlots }
    if (invalidCanonicalSlot != null) {
        return MatchOcrReviewUiState.Error(
            tournamentId = tournamentId,
            matchId = matchId,
            message = "Saved calculated evidence contains an invalid team identity.",
        )
    }
    val excludedSourcePositions = result.excludedSourcePositions
    val positions = result.positions.sortedBy { it.position }
    if (positions.isEmpty()) {
        return MatchOcrReviewUiState.Empty(
            tournamentId = tournamentId,
            matchId = matchId,
            teamNamesBySlot = teamNamesBySlot,
            eligibleTeamSlots = eligibleTeamSlots,
            lobbySlotByTeamSlot = lobbySlotByTeamSlot,
            usesPairRelativeIdentity = usesPairRelativeIdentity,
        )
    }
    val positionsByNumber = positions.associateBy { it.position }
    val restoredManualPositions = result.manuallyAddedPositions
        .asSequence()
        .filter { it in 1..12 }
        .distinct()
        .filter { it in positionsByNumber }
        .filterNot { it in excludedSourcePositions }
        .filter { position ->
            val savedPosition = positionsByNumber.getValue(position)
            savedPosition.sourceScreenshotRole == null &&
                savedPosition.cropLeft == null &&
                savedPosition.cropTop == null &&
                savedPosition.cropRight == null &&
                savedPosition.cropBottom == null
        }
        .toSet()
    val restoredTeamNames = teamNamesBySlot + positions.mapNotNull { position ->
        val slot = position.slotNumber ?: return@mapNotNull null
        val name = position.teamName?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        slot to name
    }
    val previewRows = positions
        .filter { it.hasRestorableGeometry() }
        .map { it.toRestoredPreviewRow() }
    val manualFallbackRowsByPosition = MatchResultOcrPreviewUiStateMapper.manualFallbackRows()
        .associateBy { it.rowIndex + 1 }
    val reviewRows = positions.map { position ->
        if (position.position in restoredManualPositions) {
            manualFallbackRowsByPosition.getValue(position.position)
        } else {
            position.toRestoredReviewRow(
                teamNamesBySlot = teamNamesBySlot,
                lobbySlotByTeamSlot = lobbySlotByTeamSlot,
                usesPairRelativeIdentity = usesPairRelativeIdentity,
            )
        }
    }
    val initialDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(
        reviewRows,
        eligibleTeamSlots = eligibleTeamSlots,
    )
    val correctionDraft = MatchOcrReviewCorrectionDraftReducer.validate(
        initialDraft.copy(
            rows = initialDraft.rows.map { draft ->
                val positionNumber = draft.rowIndex + 1
                val savedPosition = positionsByNumber.getValue(positionNumber)
                val row = reviewRows.first { it.rowIndex == draft.rowIndex }
                val isRestoredManual = positionNumber in restoredManualPositions
                draft.copy(
                    placementDraftValue = if (isRestoredManual) {
                        savedPosition.placement?.toString().orEmpty()
                    } else {
                        row.detectedPlacementDisplayValue
                    },
                    killsDraftValue = if (isRestoredManual) {
                        savedPosition.totalKills?.toString().orEmpty()
                    } else if (draft.playerKillDrafts.isNotEmpty()) {
                        draft.killsDraftValue
                    } else {
                        row.detectedKillDisplayValue
                    },
                    assignedTeamSlotDraftValue = if (isRestoredManual) {
                        savedPosition.slotNumber?.toString().orEmpty()
                    } else {
                        row.suggestedTeamSlotDisplayValue
                    },
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
        authoritativePositionCropsByRole = positions
            .mapNotNull { position ->
                position.sourceScreenshotRole
                    ?.let { role -> role to position.toAuthoritativePositionCropOrNull() }
            }
            .mapNotNull { (role, crop) -> crop?.let { role to it } }
            .groupBy({ it.first }, { it.second }),
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
        eligibleTeamSlots = eligibleTeamSlots,
        lobbySlotByTeamSlot = lobbySlotByTeamSlot,
        usesPairRelativeIdentity = usesPairRelativeIdentity,
        evidenceSource = MatchOcrReviewEvidenceSource.RESTORED_CALCULATED,
        calculatedEvidenceOrigin = result.calculationOrigin,
        manuallyRevealedPositions = restoredManualPositions,
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

private fun ResultPositionCalculatedEvidence.toRestoredReviewRow(
    teamNamesBySlot: Map<Int, String> = emptyMap(),
    lobbySlotByTeamSlot: Map<Int, Int> = emptyMap(),
    usesPairRelativeIdentity: Boolean = false,
): MatchOcrReviewRowUiState {
    val displayPlacement = displayPlacement()
    val applicability = playerKillApplicability()
    val playerNamesLabel = (1..4).joinToString(", ") { slot ->
        "P$slot ${playerNameAt(slot)}"
    }
    val assignedSlot = slotNumber?.toString().orEmpty()
    val teamIdentityDisplayValue = slotNumber?.let {
        displayTeamIdentityLabel(
            teamSlot = it,
            teamNamesBySlot = teamNamesBySlot,
            lobbySlotByTeamSlot = lobbySlotByTeamSlot,
            usesPairRelativeIdentity = usesPairRelativeIdentity,
        )
    }
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
        teamIdentityDisplayValue = teamIdentityDisplayValue,
        confidenceScoreDisplayValue = RESTORED_EVIDENCE_LABEL,
        confidenceTierLabel = RESTORED_EVIDENCE_LABEL,
        assignmentSafetyStatusLabel = if (slotNumber == null) UNAVAILABLE_LABEL else RESTORED_EVIDENCE_LABEL,
        topThreeSuggestionsSummary = listOf(
            if (usesPairRelativeIdentity) {
                "Saved team identity: ${teamIdentityDisplayValue ?: UNAVAILABLE_LABEL}"
            } else {
                "Saved team slot: ${assignedSlot.ifBlank { UNAVAILABLE_LABEL }}"
            },
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
        playerKillEvidence = if (hasRestorablePlayerKillEvidence()) {
            (1..4).map { slot ->
                MatchOcrReviewPlayerKillEvidenceUiState(
                    playerSlot = slot,
                    originalKillsValue = playerKills.getOrNull(slot - 1)?.toString().orEmpty(),
                    isPlayerDetected = applicability[slot - 1],
                )
            }
        } else {
            emptyList()
        },
    )
}

private fun ResultPositionCalculatedEvidence.hasRestorablePlayerKillEvidence(): Boolean =
    playerNames.any { name ->
        name?.trim()?.let { it.isNotBlank() && it != MATCH_RESULT_NOT_DETECTED_PLAYER } == true
    } ||
        playerKillApplicable?.any { it } == true ||
        playerKills.any { it != null }
