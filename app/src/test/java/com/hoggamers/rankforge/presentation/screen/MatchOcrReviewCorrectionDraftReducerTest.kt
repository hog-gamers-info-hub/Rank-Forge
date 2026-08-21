package com.hoggamers.rankforge.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchOcrReviewCorrectionDraftReducerTest {
    @Test
    fun initialCorrectionDraftIsCreatedFromTwelveReadyRows() {
        val draft = initialDraft()

        assertEquals(12, draft.rows.size)
        assertEquals(MatchOcrReviewCorrectionDraftStatus.VALID, draft.status)
        assertEquals("1", draft.rows.first().placementDraftValue)
        assertEquals("0", draft.rows.first().killsDraftValue)
        assertEquals("1", draft.rows.first().assignedTeamSlotDraftValue)
        assertFalse(draft.isDirty)
    }

    @Test
    fun initialCorrectionDraftIncludesAllRowsAsIncluded() {
        val draft = initialDraft()

        assertEquals(12, draft.rows.size)
        assertEquals(0, draft.excludedCount)
        assertEquals(12, draft.includedRows.size)
        assertTrue(draft.rows.all { !it.isExcluded })
    }

    @Test
    fun excludingOneRowPreservesStructuralRowsAndMarksDraftDirty() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onRowExcluded(initialDraft(), 10)

        assertEquals(12, draft.rows.size)
        assertEquals((0..11).toList(), draft.rows.map { it.rowIndex })
        assertTrue(draft.rows[10].isExcluded)
        assertTrue(draft.rows.filterIndexed { index, _ -> index != 10 }.all { !it.isExcluded })
        assertEquals(11, draft.includedRows.size)
        assertEquals(1, draft.excludedCount)
        assertTrue(draft.isDirty)
    }

    @Test
    fun excludedRowDoesNotContributeNormalBlockersOrWarnings() {
        val changed = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(initialDraft(), 0, "")
            .let { MatchOcrReviewCorrectionDraftReducer.onKillsChanged(it, 0, "-1") }
            .let { MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(it, 0, "13") }
        val draft = MatchOcrReviewCorrectionDraftReducer.onRowExcluded(changed, 0)

        assertTrue(draft.rows[0].validation.blockers.isEmpty())
        assertTrue(draft.rows[0].validation.warnings.isEmpty())
        assertEquals(0, draft.blockerCount)
    }

    @Test
    fun excludingOneSideOfDuplicatePlacementRemovesDuplicateBlockerFromIncludedRow() {
        val duplicate = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(initialDraft(), 1, "1")
        val excluded = MatchOcrReviewCorrectionDraftReducer.onRowExcluded(duplicate, 1)

        assertFalse(excluded.rows[0].validation.blockers.contains(MatchOcrReviewCorrectionReason.DUPLICATE_PLACEMENT))
        assertFalse(excluded.rows[1].validation.blockers.contains(MatchOcrReviewCorrectionReason.DUPLICATE_PLACEMENT))
    }

    @Test
    fun excludingOneSideOfDuplicateTeamSlotRemovesDuplicateBlockerFromIncludedRow() {
        val duplicate = MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(initialDraft(), 1, "1")
        val excluded = MatchOcrReviewCorrectionDraftReducer.onRowExcluded(duplicate, 1)

        assertFalse(excluded.rows[0].validation.blockers.contains(MatchOcrReviewCorrectionReason.DUPLICATE_TEAM_SLOT))
        assertFalse(excluded.rows[1].validation.blockers.contains(MatchOcrReviewCorrectionReason.DUPLICATE_TEAM_SLOT))
    }

    @Test
    fun placementCorrectionUpdatesOnlySelectedRow() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(initialDraft(), 0, "12")

        assertEquals("12", draft.rows[0].placementDraftValue)
        assertEquals("2", draft.rows[1].placementDraftValue)
        assertEquals("1", draft.rows[0].originalPlacementValue)
    }

    @Test
    fun killCorrectionUpdatesOnlySelectedRow() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(initialDraft(), 1, "9")

        assertEquals("0", draft.rows[0].killsDraftValue)
        assertEquals("9", draft.rows[1].killsDraftValue)
        assertEquals("1", draft.rows[1].originalKillsValue)
    }

    @Test
    fun teamSlotCorrectionUpdatesOnlySelectedRow() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(initialDraft(), 2, "11")

        assertEquals("1", draft.rows[0].assignedTeamSlotDraftValue)
        assertEquals("11", draft.rows[2].assignedTeamSlotDraftValue)
        assertEquals("3", draft.rows[2].originalAssignedTeamSlotValue)
    }

    @Test
    fun validPlacementRangeIncludesOneThroughTwelve() {
        val draft = initialDraft()

        assertFalse(draft.rows.first().validation.blockers.contains(MatchOcrReviewCorrectionReason.INVALID_PLACEMENT))
        assertFalse(draft.rows.last().validation.blockers.contains(MatchOcrReviewCorrectionReason.INVALID_PLACEMENT))
    }

    @Test
    fun missingPlacementBlocksDraft() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(initialDraft(), 0, "")

        assertRowBlocked(draft, 0, MatchOcrReviewCorrectionReason.MISSING_PLACEMENT)
    }

    @Test
    fun invalidPlacementBlocksDraft() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(initialDraft(), 0, "13")

        assertRowBlocked(draft, 0, MatchOcrReviewCorrectionReason.INVALID_PLACEMENT)
    }

    @Test
    fun duplicatePlacementBlocksDraft() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(initialDraft(), 1, "1")

        assertRowBlocked(draft, 0, MatchOcrReviewCorrectionReason.DUPLICATE_PLACEMENT)
        assertRowBlocked(draft, 1, MatchOcrReviewCorrectionReason.DUPLICATE_PLACEMENT)
    }

    @Test
    fun validKillsIncludeZero() {
        val draft = initialDraft()

        assertFalse(draft.rows.first().validation.blockers.contains(MatchOcrReviewCorrectionReason.INVALID_KILLS))
        assertEquals("0", draft.rows.first().killsDraftValue)
    }

    @Test
    fun blankKillsBlocksDraft() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(initialDraft(), 0, " ")

        assertRowBlocked(draft, 0, MatchOcrReviewCorrectionReason.MISSING_KILLS)
    }

    @Test
    fun nonNumericKillsBlocksDraft() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(initialDraft(), 0, "x")

        assertRowBlocked(draft, 0, MatchOcrReviewCorrectionReason.INVALID_KILLS)
    }

    @Test
    fun negativeKillsBlocksDraft() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(initialDraft(), 0, "-1")

        assertRowBlocked(draft, 0, MatchOcrReviewCorrectionReason.NEGATIVE_KILLS)
    }

    @Test
    fun validTeamSlotRangeIncludesOneThroughTwelve() {
        val draft = initialDraft()

        assertFalse(draft.rows.first().validation.blockers.contains(MatchOcrReviewCorrectionReason.INVALID_TEAM_SLOT))
        assertFalse(draft.rows.last().validation.blockers.contains(MatchOcrReviewCorrectionReason.INVALID_TEAM_SLOT))
    }

    @Test
    fun missingTeamSlotBlocksDraft() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(initialDraft(), 0, "")

        assertRowBlocked(draft, 0, MatchOcrReviewCorrectionReason.MISSING_TEAM_SLOT)
    }

    @Test
    fun invalidTeamSlotBlocksDraft() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(initialDraft(), 0, "13")

        assertRowBlocked(draft, 0, MatchOcrReviewCorrectionReason.INVALID_TEAM_SLOT)
    }

    @Test
    fun duplicateTeamSlotBlocksDraft() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(initialDraft(), 1, "1")

        assertRowBlocked(draft, 0, MatchOcrReviewCorrectionReason.DUPLICATE_TEAM_SLOT)
        assertRowBlocked(draft, 1, MatchOcrReviewCorrectionReason.DUPLICATE_TEAM_SLOT)
    }

    @Test
    fun rowDirtyTrackingIsBasedOnDraftValueDifferences() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(initialDraft(), 3, "10")

        assertFalse(draft.rows[0].isDirty)
        assertTrue(draft.rows[3].isDirty)
    }

    @Test
    fun screenDirtyTrackingIsTrueWhenAnyRowIsDirty() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(initialDraft(), 3, "10")

        assertTrue(draft.isDirty)
    }

    @Test
    fun resetRowRestoresOriginalParsedAndSuggestedValues() {
        val changed = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(initialDraft(), 3, "10")
        val reset = MatchOcrReviewCorrectionDraftReducer.onResetRowCorrection(changed, 3)

        assertEquals("3", reset.rows[3].killsDraftValue)
        assertFalse(reset.rows[3].isDirty)
        assertFalse(reset.isDirty)
    }

    @Test
    fun resetRowRestoresExcludedStateAndNormalValidation() {
        val changed = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(initialDraft(), 3, "")
        val excluded = MatchOcrReviewCorrectionDraftReducer.onRowExcluded(changed, 3)
        val reset = MatchOcrReviewCorrectionDraftReducer.onResetRowCorrection(excluded, 3)

        assertFalse(reset.rows[3].isExcluded)
        assertEquals("4", reset.rows[3].placementDraftValue)
        assertEquals("3", reset.rows[3].killsDraftValue)
        assertEquals("4", reset.rows[3].assignedTeamSlotDraftValue)
        assertTrue(reset.rows[3].validation.blockers.isEmpty())
        assertFalse(reset.isDirty)
    }

    @Test
    fun resetAllRestoresAllRows() {
        val changedPlacement = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(initialDraft(), 0, "12")
        val changedKills = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(changedPlacement, 3, "10")
        val changedTeamSlot = MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(changedKills, 4, "11")
        val excluded = MatchOcrReviewCorrectionDraftReducer.onRowExcluded(changedTeamSlot, 7)
        val reset = MatchOcrReviewCorrectionDraftReducer.onResetAllCorrections(excluded)

        assertFalse(reset.isDirty)
        assertEquals(0, reset.excludedCount)
        assertTrue(reset.rows.all { !it.isExcluded })
        assertEquals((1..12).map { it.toString() }, reset.rows.map { it.placementDraftValue })
        assertEquals((0..11).map { it.toString() }, reset.rows.map { it.killsDraftValue })
        assertEquals((1..12).map { it.toString() }, reset.rows.map { it.assignedTeamSlotDraftValue })
    }

    @Test
    fun blockerCountCountsRowsWithBlockingValidation() {
        val missingPlacement = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(initialDraft(), 0, "")
        val missingKills = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(missingPlacement, 1, "")

        assertEquals(2, missingKills.blockerCount)
        assertEquals(MatchOcrReviewCorrectionDraftStatus.BLOCKED, missingKills.status)
    }

    @Test
    fun warningCountCountsNonBlockingRowsWithWarnings() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(initialDraft(), 0, "20")

        assertEquals(0, draft.blockerCount)
        assertEquals(1, draft.warningCount)
        assertEquals(MatchOcrReviewCorrectionDraftStatus.WARNING, draft.status)
        assertTrue(
            draft.rows[0].validation.warnings.contains(MatchOcrReviewCorrectionReason.KILLS_CHANGED_FROM_OCR),
        )
    }

    @Test
    fun noSaveOrPersistenceActionIsExposedByViewModel() {
        val publicMethodNames = MatchOcrReviewViewModel::class.java.methods.map { it.name }.toSet()

        assertFalse(publicMethodNames.contains("save"))
        assertFalse(publicMethodNames.contains("persist"))
        assertFalse(publicMethodNames.contains("submit"))
        assertFalse(publicMethodNames.contains("sync"))
        assertTrue(publicMethodNames.contains("onFinalizeOcrCorrection"))
        assertTrue(publicMethodNames.contains("onConfirmFinalizeWarnings"))
        assertTrue(publicMethodNames.contains("onPlacementChanged"))
        assertTrue(publicMethodNames.contains("onKillsChanged"))
        assertTrue(publicMethodNames.contains("onAssignedTeamSlotChanged"))
    }

    @Test
    fun repeatedValidationIsDeterministic() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(initialDraft(), 0, "20")

        assertEquals(
            MatchOcrReviewCorrectionDraftReducer.validate(draft),
            MatchOcrReviewCorrectionDraftReducer.validate(MatchOcrReviewCorrectionDraftReducer.validate(draft)),
        )
    }

    @Test
    fun repeatedValidationRemainsDeterministicWithExcludedRows() {
        val changed = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(initialDraft(), 0, "")
        val draft = MatchOcrReviewCorrectionDraftReducer.onRowExcluded(changed, 0)

        assertEquals(
            MatchOcrReviewCorrectionDraftReducer.validate(draft),
            MatchOcrReviewCorrectionDraftReducer.validate(MatchOcrReviewCorrectionDraftReducer.validate(draft)),
        )
    }

    @Test
    fun originalEvidenceValuesRemainPreservedAfterDraftChanges() {
        val draft = MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(initialDraft(), 0, "12")
        val row = draft.rows[0]

        assertEquals("1", row.originalPlacementValue)
        assertEquals("0", row.originalKillsValue)
        assertEquals("1", row.originalAssignedTeamSlotValue)
        assertEquals("12", row.assignedTeamSlotDraftValue)
    }

    @Test
    fun originalEvidenceValuesRemainPreservedAfterExclusion() {
        val row = MatchOcrReviewCorrectionDraftReducer.onRowExcluded(initialDraft(), 0).rows[0]

        assertEquals("1", row.originalPlacementValue)
        assertEquals("0", row.originalKillsValue)
        assertEquals("1", row.originalAssignedTeamSlotValue)
    }

    @Test
    fun originalReviewAndWeakEvidenceWarningsArePreserved() {
        val draft = initialDraft(
            rows = readyRows().mapIndexed { index, row ->
                if (index == 0) {
                    row.copy(
                        warningLabels = listOf("Safety: Review required"),
                        confidenceTierLabel = "Confirmation required",
                        assignmentSafetyStatusLabel = "Review required",
                    )
                } else {
                    row
                }
            },
        )

        assertTrue(
            draft.rows[0].validation.warnings.contains(
                MatchOcrReviewCorrectionReason.ROW_ORIGINALLY_REQUIRED_MANUAL_REVIEW,
            ),
        )
        assertTrue(
            draft.rows[0].validation.warnings.contains(
                MatchOcrReviewCorrectionReason.WEAK_CONFIDENCE_OR_SAFETY_EVIDENCE,
            ),
        )
    }

    private fun initialDraft(
        rows: List<MatchOcrReviewRowUiState> = readyRows(),
    ): MatchOcrReviewCorrectionDraft =
        MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows)

    private fun readyRows(): List<MatchOcrReviewRowUiState> =
        (0..11).map { index ->
            MatchOcrReviewRowUiState(
                rowIndex = index,
                expectedPlacementLabel = (index + 1).toString(),
                detectedPlacementDisplayValue = (index + 1).toString(),
                placementStatusLabel = "Accepted",
                detectedKillDisplayValue = index.toString(),
                killStatusLabel = "Accepted",
                detectedPlayerNameEvidenceLabel = "Synthetic Unit ${index + 1}",
                playerNameStatusLabel = "Accepted",
                suggestedTeamSlotDisplayValue = (index + 1).toString(),
                confidenceScoreDisplayValue = "94",
                confidenceTierLabel = "Automatic candidate",
                assignmentSafetyStatusLabel = "Safe automatic assignment",
                topThreeSuggestionsSummary = listOf(
                    "Rank 1: Slot ${index + 1}, confidence 94, matches 4, coverage 100",
                ),
                warningLabels = emptyList(),
                blockerLabels = emptyList(),
                severity = MatchOcrReviewSeverity.INFORMATIONAL,
                originalParsedPlacementValue = index + 1,
                originalParsedKillValue = index,
                originalSuggestedTeamSlot = index + 1,
            )
        }

    private fun assertRowBlocked(
        draft: MatchOcrReviewCorrectionDraft,
        rowIndex: Int,
        reason: MatchOcrReviewCorrectionReason,
    ) {
        assertEquals(MatchOcrReviewCorrectionDraftStatus.BLOCKED, draft.status)
        assertTrue(draft.rows[rowIndex].validation.blockers.contains(reason))
    }
}
