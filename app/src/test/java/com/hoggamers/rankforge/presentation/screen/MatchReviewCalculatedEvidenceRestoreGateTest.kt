package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchCalculatedEvidence
import com.hoggamers.rankforge.data.local.ResultCalculatedEvidence
import com.hoggamers.rankforge.data.local.ResultPositionCalculatedEvidence
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchReviewCalculatedEvidenceRestoreGateTest {
    @Test
    fun draftNotRequestedHolds() {
        assertTrue(shouldHold(CalculatedEvidenceRestoreStatus.NOT_REQUESTED))
    }

    @Test
    fun draftCheckingHolds() {
        assertTrue(shouldHold(CalculatedEvidenceRestoreStatus.CHECKING))
    }

    @Test
    fun restoredSavedResultWaitsForDisplayableOcr() {
        assertTrue(
            shouldHold(
                status = CalculatedEvidenceRestoreStatus.RESTORED,
                evidence = savedEvidence(),
                ocrUiState = MatchOcrReviewUiState.Empty(TournamentId, MatchId),
            ),
        )
    }

    @Test
    fun restoredSavedResultReleasesForDisplayableOcrOfSameMatch() {
        assertFalse(
            shouldHold(
                status = CalculatedEvidenceRestoreStatus.RESTORED,
                evidence = savedEvidence(),
                ocrUiState = displayableOcrState(TournamentId, MatchId),
            ),
        )
    }

    @Test
    fun restoredSavedResultDoesNotReleaseForAnotherMatch() {
        assertTrue(
            shouldHold(
                status = CalculatedEvidenceRestoreStatus.RESTORED,
                evidence = savedEvidence(),
                ocrUiState = displayableOcrState(TournamentId, "other-match"),
            ),
        )
    }

    @Test
    fun restoredWithoutSavedResultReleases() {
        assertFalse(
            shouldHold(
                status = CalculatedEvidenceRestoreStatus.RESTORED,
                evidence = MatchCalculatedEvidence(),
            ),
        )
    }

    @Test
    fun notFoundReleases() {
        assertFalse(shouldHold(CalculatedEvidenceRestoreStatus.NOT_FOUND))
    }

    @Test
    fun failedReleases() {
        assertFalse(shouldHold(CalculatedEvidenceRestoreStatus.FAILED))
    }

    @Test
    fun clearedReleases() {
        assertFalse(shouldHold(CalculatedEvidenceRestoreStatus.CLEARED))
    }

    @Test
    fun finalizedReleasesRegardlessOfRestoreStatus() {
        assertFalse(
            shouldHold(
                status = CalculatedEvidenceRestoreStatus.NOT_REQUESTED,
                reviewState = draftReviewState().copy(status = MatchStatus.FINALIZED),
            ),
        )
    }

    @Test
    fun initialTransitionRemainsActiveForInitialRestore() {
        assertTrue(
            isInitialCalculatedRestoreTransitionActive(
                draftReviewState(CalculatedEvidenceRestoreStatus.NOT_REQUESTED),
            ),
        )
        assertTrue(
            isInitialCalculatedRestoreTransitionActive(
                draftReviewState(
                    status = CalculatedEvidenceRestoreStatus.RESTORED,
                    evidence = savedEvidence(),
                ),
            ),
        )
    }

    @Test
    fun initialTransitionIsInactiveWhenNoRestoreIsNeeded() {
        assertFalse(
            isInitialCalculatedRestoreTransitionActive(
                draftReviewState(
                    status = CalculatedEvidenceRestoreStatus.RESTORED,
                    evidence = MatchCalculatedEvidence(),
                ),
            ),
        )
        assertFalse(
            isInitialCalculatedRestoreTransitionActive(
                draftReviewState(CalculatedEvidenceRestoreStatus.NOT_FOUND),
            ),
        )
    }

    @Test
    fun activeRestoreGateRequestsTheSkeleton() {
        assertTrue(
            shouldShowMatchReviewRestoreSkeleton(
                holdForCalculatedEvidenceRestore = true,
                initialCalculatedRestoreTransitionActive = true,
            ),
        )
    }

    @Test
    fun releasedOrInactiveRestoreDoesNotRequestTheSkeleton() {
        assertFalse(
            shouldShowMatchReviewRestoreSkeleton(
                holdForCalculatedEvidenceRestore = false,
                initialCalculatedRestoreTransitionActive = true,
            ),
        )
        assertFalse(
            shouldShowMatchReviewRestoreSkeleton(
                holdForCalculatedEvidenceRestore = true,
                initialCalculatedRestoreTransitionActive = false,
            ),
        )
    }

    private fun shouldHold(
        status: CalculatedEvidenceRestoreStatus,
        evidence: MatchCalculatedEvidence? = null,
        ocrUiState: MatchOcrReviewUiState = MatchOcrReviewUiState.Loading,
        reviewState: MatchReviewUiState = draftReviewState(status, evidence),
    ): Boolean = shouldHoldMatchReviewForCalculatedEvidenceRestore(reviewState, ocrUiState)

    private fun draftReviewState(
        status: CalculatedEvidenceRestoreStatus = CalculatedEvidenceRestoreStatus.NOT_REQUESTED,
        evidence: MatchCalculatedEvidence? = null,
    ) = MatchReviewUiState(
        isLoading = false,
        isAvailable = true,
        tournamentId = TournamentId,
        matchId = MatchId,
        status = MatchStatus.DRAFT,
        calculatedEvidenceRestoreStatus = status,
        restoredCalculatedEvidence = evidence,
    )

    private fun savedEvidence() = MatchCalculatedEvidence(
        result = ResultCalculatedEvidence(
            positions = listOf(ResultPositionCalculatedEvidence(position = 1)),
        ),
    )

    private fun displayableOcrState(
        tournamentId: String,
        matchId: String,
    ) = MatchOcrReviewUiState.Empty(
        tournamentId = tournamentId,
        matchId = matchId,
        matchResultOcrPreview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            rows = listOf(
                MatchResultOcrPreviewRowUiState(
                    position = 1,
                    role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    sourceLabel = "UPPER",
                    placementText = "1",
                    slots = emptyList(),
                ),
            ),
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        ),
    )

    private companion object {
        const val TournamentId = "tournament-1"
        const val MatchId = "match-1"
    }
}
