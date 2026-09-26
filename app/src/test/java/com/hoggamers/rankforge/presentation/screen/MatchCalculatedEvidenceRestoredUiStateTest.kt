package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.data.local.MatchCalculatedEvidence
import com.hoggamers.rankforge.data.local.MatchCalculatedEvidenceOrigin
import com.hoggamers.rankforge.data.local.ResultCalculatedEvidence
import com.hoggamers.rankforge.data.local.ResultPositionCalculatedEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchCalculatedEvidenceRestoredUiStateTest {
    @Test
    fun aggregateCalculatedRowDoesNotCreatePlayerKillDrafts() {
        val state = restore(
            ResultPositionCalculatedEvidence(
                position = 3,
                slotNumber = 5,
                totalKills = 8,
                placement = 3,
            ),
            origin = MatchCalculatedEvidenceOrigin.MANUAL,
        )
        val row = state.rows.single()
        val correctionDraft = requireNotNull(state.correctionDraft)
        val draft = correctionDraft.rows.single()

        assertEquals(MatchCalculatedEvidenceOrigin.MANUAL, state.calculatedEvidenceOrigin)
        assertEquals("3", draft.placementDraftValue)
        assertEquals("8", draft.killsDraftValue)
        assertEquals("5", draft.assignedTeamSlotDraftValue)
        assertTrue(row.playerKillEvidence.isEmpty())
        assertTrue(draft.playerKillDrafts.isEmpty())
        assertFalse(draft.validation.blockers.contains(MatchOcrReviewCorrectionReason.MISSING_KILLS))

        val edited = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(
            draft = correctionDraft,
            rowIndex = 2,
            value = "9",
        )
        assertEquals("9", edited.rows.single().killsDraftValue)
    }

    @Test
    fun manualRestoredRowRequiresAggregateKillsWhenMissing() {
        val state = restore(
            ResultPositionCalculatedEvidence(
                position = 3,
                slotNumber = 5,
                totalKills = null,
                placement = 3,
            ),
            origin = MatchCalculatedEvidenceOrigin.MANUAL,
        )
        val draft = requireNotNull(state.correctionDraft).rows.single()

        assertTrue(draft.playerKillDrafts.isEmpty())
        assertTrue(draft.validation.blockers.contains(MatchOcrReviewCorrectionReason.MISSING_KILLS))
        assertEquals(
            R.string.match_review_finalize_manual_missing_kills,
            MatchOcrReviewCorrectionReason.MISSING_KILLS
                .toMatchReviewFinalizeBlockerMessageRes(MatchCalculatedEvidenceOrigin.MANUAL),
        )
    }

    @Test
    fun calculationOriginControlsCompactAggregateResultPresentation() {
        assertFalse(
            MatchCalculatedEvidenceOrigin.MANUAL.shouldShowCompactAggregateResultFields(),
        )
        assertTrue(
            MatchCalculatedEvidenceOrigin.AUTOMATIC.shouldShowCompactAggregateResultFields(),
        )
        assertEquals(
            R.string.match_review_finalize_missing_kills,
            MatchOcrReviewCorrectionReason.MISSING_KILLS.toMatchReviewFinalizeBlockerMessageRes(),
        )
    }

    @Test
    fun playerLevelCalculatedEvidenceStillCreatesPlayerKillDrafts() {
        val state = restore(
            ResultPositionCalculatedEvidence(
                position = 3,
                slotNumber = 5,
                totalKills = 8,
                placement = 3,
                playerNames = listOf("Player One", null, null, null),
                playerKills = listOf(2, null, null, null),
                playerKillApplicable = listOf(true, false, false, false),
            ),
        )

        assertEquals(MatchCalculatedEvidenceOrigin.AUTOMATIC, state.calculatedEvidenceOrigin)
        assertEquals(
            listOf(1, 2, 3, 4),
            state.correctionDraft!!.rows.single().playerKillDrafts.map { it.playerSlot },
        )
    }

    @Test
    fun legacyPlayerLevelEvidenceWithoutApplicabilityFlagsStillRestores() {
        val state = restore(
            ResultPositionCalculatedEvidence(
                position = 3,
                slotNumber = 5,
                totalKills = 8,
                placement = 3,
                playerNames = listOf("Legacy Player", null, null, null),
                playerKills = listOf(2, null, null, null),
            ),
        )

        assertEquals(
            listOf(1, 2, 3, 4),
            state.correctionDraft!!.rows.single().playerKillDrafts.map { it.playerSlot },
        )
    }

    private fun restore(
        position: ResultPositionCalculatedEvidence,
        origin: MatchCalculatedEvidenceOrigin = MatchCalculatedEvidenceOrigin.AUTOMATIC,
    ): MatchOcrReviewUiState.Ready =
        MatchCalculatedEvidence(
            result = ResultCalculatedEvidence(
                positions = listOf(position),
                calculationOrigin = origin,
            ),
        ).toRestoredOcrReviewUiState(
            tournamentId = "tournament",
            matchId = "match",
            teamNamesBySlot = emptyMap(),
        ) as MatchOcrReviewUiState.Ready
}
