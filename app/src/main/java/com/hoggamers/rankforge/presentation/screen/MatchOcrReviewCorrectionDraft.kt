package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.TeamSlot

data class MatchOcrReviewCorrectionDraft(
    val rows: List<MatchOcrReviewRowCorrectionDraft>,
    val assignmentRequired: Boolean = true,
) {
    val isDirty: Boolean
        get() = rows.any { it.isDirty }

    val blockerCount: Int
        get() = rows.count { it.validation.blockers.isNotEmpty() }

    val warningCount: Int
        get() = rows.count { row ->
            row.validation.blockers.isEmpty() && row.validation.warnings.isNotEmpty()
        }

    val status: MatchOcrReviewCorrectionDraftStatus
        get() = when {
            blockerCount > 0 -> MatchOcrReviewCorrectionDraftStatus.BLOCKED
            warningCount > 0 -> MatchOcrReviewCorrectionDraftStatus.WARNING
            else -> MatchOcrReviewCorrectionDraftStatus.VALID
        }
}

data class MatchOcrReviewRowCorrectionDraft(
    val rowIndex: Int,
    val originalPlacementValue: String,
    val originalKillsValue: String,
    val originalAssignedTeamSlotValue: String,
    val placementDraftValue: String,
    val killsDraftValue: String,
    val assignedTeamSlotDraftValue: String,
    val originallyRequiredManualReview: Boolean,
    val weakConfidenceOrSafetyEvidence: Boolean,
    val validation: MatchOcrReviewRowCorrectionValidation,
) {
    val isDirty: Boolean
        get() = placementDraftValue != originalPlacementValue ||
            killsDraftValue != originalKillsValue ||
            assignedTeamSlotDraftValue != originalAssignedTeamSlotValue
}

data class MatchOcrReviewRowCorrectionValidation(
    val blockers: Set<MatchOcrReviewCorrectionReason> = emptySet(),
    val warnings: Set<MatchOcrReviewCorrectionReason> = emptySet(),
) {
    val status: MatchOcrReviewCorrectionDraftStatus
        get() = when {
            blockers.isNotEmpty() -> MatchOcrReviewCorrectionDraftStatus.BLOCKED
            warnings.isNotEmpty() -> MatchOcrReviewCorrectionDraftStatus.WARNING
            else -> MatchOcrReviewCorrectionDraftStatus.VALID
        }
}

enum class MatchOcrReviewCorrectionDraftStatus {
    VALID,
    WARNING,
    BLOCKED,
}

enum class MatchOcrReviewCorrectionReason {
    MISSING_PLACEMENT,
    INVALID_PLACEMENT,
    DUPLICATE_PLACEMENT,
    MISSING_KILLS,
    INVALID_KILLS,
    NEGATIVE_KILLS,
    MISSING_TEAM_SLOT,
    INVALID_TEAM_SLOT,
    DUPLICATE_TEAM_SLOT,
    MALFORMED_ROW_DRAFT,
    PLACEMENT_CHANGED_FROM_OCR,
    KILLS_CHANGED_FROM_OCR,
    TEAM_SLOT_CHANGED_FROM_SUGGESTION,
    ROW_ORIGINALLY_REQUIRED_MANUAL_REVIEW,
    WEAK_CONFIDENCE_OR_SAFETY_EVIDENCE,
}

object MatchOcrReviewCorrectionDraftReducer {
    private val EXPECTED_ROW_INDEXES = 0 until 12

    fun createInitialDraft(
        rows: List<MatchOcrReviewRowUiState>,
        assignmentRequired: Boolean = true,
    ): MatchOcrReviewCorrectionDraft {
        val drafts = rows.map { row ->
            val originalPlacementValue = row.originalParsedPlacementValue?.toString().orEmpty()
            val originalKillsValue = row.originalParsedKillValue?.toString().orEmpty()
            val originalAssignedTeamSlotValue = row.originalSuggestedTeamSlot?.toString().orEmpty()

            MatchOcrReviewRowCorrectionDraft(
                rowIndex = row.rowIndex,
                originalPlacementValue = originalPlacementValue,
                originalKillsValue = originalKillsValue,
                originalAssignedTeamSlotValue = originalAssignedTeamSlotValue,
                placementDraftValue = originalPlacementValue,
                killsDraftValue = originalKillsValue,
                assignedTeamSlotDraftValue = originalAssignedTeamSlotValue,
                originallyRequiredManualReview = row.blockerLabels.isNotEmpty() || row.warningLabels.isNotEmpty(),
                weakConfidenceOrSafetyEvidence = row.hasWeakConfidenceOrSafetyEvidence(),
                validation = MatchOcrReviewRowCorrectionValidation(),
            )
        }
        return validate(
            MatchOcrReviewCorrectionDraft(
                rows = drafts,
                assignmentRequired = assignmentRequired,
            ),
        )
    }

    fun onPlacementChanged(
        draft: MatchOcrReviewCorrectionDraft,
        rowIndex: Int,
        value: String,
    ): MatchOcrReviewCorrectionDraft = updateRow(draft, rowIndex) { row ->
        row.copy(placementDraftValue = value)
    }

    fun onKillsChanged(
        draft: MatchOcrReviewCorrectionDraft,
        rowIndex: Int,
        value: String,
    ): MatchOcrReviewCorrectionDraft = updateRow(draft, rowIndex) { row ->
        row.copy(killsDraftValue = value)
    }

    fun onAssignedTeamSlotChanged(
        draft: MatchOcrReviewCorrectionDraft,
        rowIndex: Int,
        value: String,
    ): MatchOcrReviewCorrectionDraft = updateRow(draft, rowIndex) { row ->
        row.copy(assignedTeamSlotDraftValue = value)
    }

    fun onResetRowCorrection(
        draft: MatchOcrReviewCorrectionDraft,
        rowIndex: Int,
    ): MatchOcrReviewCorrectionDraft = updateRow(draft, rowIndex) { row ->
        row.copy(
            placementDraftValue = row.originalPlacementValue,
            killsDraftValue = row.originalKillsValue,
            assignedTeamSlotDraftValue = row.originalAssignedTeamSlotValue,
        )
    }

    fun onResetAllCorrections(draft: MatchOcrReviewCorrectionDraft): MatchOcrReviewCorrectionDraft =
        validate(
            draft.copy(
                rows = draft.rows.map { row ->
                    row.copy(
                        placementDraftValue = row.originalPlacementValue,
                        killsDraftValue = row.originalKillsValue,
                        assignedTeamSlotDraftValue = row.originalAssignedTeamSlotValue,
                    )
                },
            ),
        )

    fun validate(draft: MatchOcrReviewCorrectionDraft): MatchOcrReviewCorrectionDraft {
        val rowIndexCounts = draft.rows.groupingBy { it.rowIndex }.eachCount()
        val malformedRowIndexes = draft.rows
            .filter { row ->
                row.rowIndex !in EXPECTED_ROW_INDEXES || rowIndexCounts.getValue(row.rowIndex) > 1
            }
            .map { it.rowIndex }
            .toSet()
        val placementDuplicates = duplicateRowIndexesForValues(
            draft.rows.mapNotNull { row ->
                row.placementDraftValue.trim().toStrictPositiveIntOrNull()
                    ?.takeIf { it in TeamSlot.SLOT_NUMBERS }
                    ?.let { row.rowIndex to it }
            },
        )
        val teamSlotDuplicates = duplicateRowIndexesForValues(
            draft.rows.mapNotNull { row ->
                row.assignedTeamSlotDraftValue.trim().toStrictPositiveIntOrNull()
                    ?.takeIf { it in TeamSlot.SLOT_NUMBERS }
                    ?.let { row.rowIndex to it }
            },
        )

        return draft.copy(
            rows = draft.rows.map { row ->
                val blockers = mutableSetOf<MatchOcrReviewCorrectionReason>()
                val warnings = mutableSetOf<MatchOcrReviewCorrectionReason>()
                val placement = row.placementDraftValue.trim()
                val kills = row.killsDraftValue.trim()
                val teamSlot = row.assignedTeamSlotDraftValue.trim()

                if (row.rowIndex in malformedRowIndexes) {
                    blockers += MatchOcrReviewCorrectionReason.MALFORMED_ROW_DRAFT
                }

                when {
                    placement.isBlank() -> blockers += MatchOcrReviewCorrectionReason.MISSING_PLACEMENT
                    placement.toStrictPositiveIntOrNull()?.let { it in TeamSlot.SLOT_NUMBERS } != true ->
                        blockers += MatchOcrReviewCorrectionReason.INVALID_PLACEMENT
                    row.rowIndex in placementDuplicates -> blockers += MatchOcrReviewCorrectionReason.DUPLICATE_PLACEMENT
                }

                when {
                    kills.isBlank() -> blockers += MatchOcrReviewCorrectionReason.MISSING_KILLS
                    kills.isStrictNegativeInteger() -> blockers += MatchOcrReviewCorrectionReason.NEGATIVE_KILLS
                    kills.toStrictNonNegativeIntOrNull() == null ->
                        blockers += MatchOcrReviewCorrectionReason.INVALID_KILLS
                }

                when {
                    teamSlot.isBlank() && draft.assignmentRequired ->
                        blockers += MatchOcrReviewCorrectionReason.MISSING_TEAM_SLOT
                    teamSlot.isNotBlank() &&
                        teamSlot.toStrictPositiveIntOrNull()?.let { it in TeamSlot.SLOT_NUMBERS } != true ->
                        blockers += MatchOcrReviewCorrectionReason.INVALID_TEAM_SLOT
                    teamSlot.isNotBlank() && row.rowIndex in teamSlotDuplicates ->
                        blockers += MatchOcrReviewCorrectionReason.DUPLICATE_TEAM_SLOT
                }

                if (row.placementDraftValue != row.originalPlacementValue) {
                    warnings += MatchOcrReviewCorrectionReason.PLACEMENT_CHANGED_FROM_OCR
                }
                if (row.killsDraftValue != row.originalKillsValue) {
                    warnings += MatchOcrReviewCorrectionReason.KILLS_CHANGED_FROM_OCR
                }
                if (row.assignedTeamSlotDraftValue != row.originalAssignedTeamSlotValue) {
                    warnings += MatchOcrReviewCorrectionReason.TEAM_SLOT_CHANGED_FROM_SUGGESTION
                }
                if (row.originallyRequiredManualReview) {
                    warnings += MatchOcrReviewCorrectionReason.ROW_ORIGINALLY_REQUIRED_MANUAL_REVIEW
                }
                if (row.weakConfidenceOrSafetyEvidence) {
                    warnings += MatchOcrReviewCorrectionReason.WEAK_CONFIDENCE_OR_SAFETY_EVIDENCE
                }

                row.copy(
                    validation = MatchOcrReviewRowCorrectionValidation(
                        blockers = blockers,
                        warnings = warnings,
                    ),
                )
            },
        )
    }

    private fun updateRow(
        draft: MatchOcrReviewCorrectionDraft,
        rowIndex: Int,
        transform: (MatchOcrReviewRowCorrectionDraft) -> MatchOcrReviewRowCorrectionDraft,
    ): MatchOcrReviewCorrectionDraft = validate(
        draft.copy(
            rows = draft.rows.map { row ->
                if (row.rowIndex == rowIndex) transform(row) else row
            },
        ),
    )

    private fun duplicateRowIndexesForValues(valuesByRowIndex: List<Pair<Int, Int>>): Set<Int> =
        valuesByRowIndex
            .groupBy({ (_, value) -> value }, { (rowIndex, _) -> rowIndex })
            .filterValues { rowIndexes -> rowIndexes.size > 1 }
            .values
            .flatten()
            .toSet()

    private fun MatchOcrReviewRowUiState.hasWeakConfidenceOrSafetyEvidence(): Boolean =
        confidenceTierLabel != "Automatic candidate" ||
            assignmentSafetyStatusLabel != "Safe automatic assignment"

    private fun String.toStrictPositiveIntOrNull(): Int? =
        takeIf { value -> value.isNotEmpty() && value.all { it in '0'..'9' } }?.toIntOrNull()

    private fun String.toStrictNonNegativeIntOrNull(): Int? =
        takeIf { value -> value.isNotEmpty() && value.all { it in '0'..'9' } }?.toIntOrNull()

    private fun String.isStrictNegativeInteger(): Boolean =
        startsWith("-") && drop(1).isNotEmpty() && drop(1).all { it in '0'..'9' }
}
