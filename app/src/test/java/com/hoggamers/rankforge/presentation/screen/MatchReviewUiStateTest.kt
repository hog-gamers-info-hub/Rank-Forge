package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.data.export.ResultDownloadScope
import com.hoggamers.rankforge.data.export.ResultExportFileFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchReviewUiStateTest {
    @Test
    fun onlyValidFinalizedMatchCanDownloadResult() {
        assertFalse(state().canDownloadResult)
        assertTrue(
            state().copy(
                status = com.hoggamers.rankforge.domain.tournament.MatchStatus.FINALIZED,
                validationErrors = emptyMap(),
                finalizedParticipantSlotNumbers = (1..12).toSet(),
            ).canDownloadResult,
        )
        assertFalse(
            state().copy(
                status = com.hoggamers.rankforge.domain.tournament.MatchStatus.FINALIZED,
                validationErrors = mapOf(1 to setOf(MatchResultValidationError.MISSING_PLACEMENT)),
            ).canDownloadResult,
        )
    }

    @Test
    fun activeDownloadDisablesNewDownload() {
        val active = state().copy(
            status = com.hoggamers.rankforge.domain.tournament.MatchStatus.FINALIZED,
            resultDownloadUiState = ResultDownloadUiState.Generating(
                ResultDownloadScope.CURRENT_MATCH,
                ResultExportFileFormat.PDF,
            ),
        )

        assertTrue(active.resultDownloadUiState.isBusy)
        assertFalse(active.canDownloadResult)
    }

    @Test
    fun neitherResultRoleReadyDisablesOcrReview() {
        assertFalse(state().canOpenOcrReview)
    }

    @Test
    fun upperOnlyReadyDisablesOcrReview() {
        assertFalse(state(upper = readySlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)).canOpenOcrReview)
    }

    @Test
    fun lowerOnlyReadyDisablesOcrReview() {
        assertFalse(state(lower = readySlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER)).canOpenOcrReview)
    }

    @Test
    fun bothRolesReadyEnableOcrReview() {
        assertTrue(state(upper = readySlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER), lower = readySlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER)).canOpenOcrReview)
    }

    @Test
    fun upperMissingLocalFileDisablesOcrReview() {
        assertFalse(state(upper = readySlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER).copy(isLocalFileMissing = true), lower = readySlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER)).canOpenOcrReview)
    }

    @Test
    fun lowerMissingLocalFileDisablesOcrReview() {
        assertFalse(state(upper = readySlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER), lower = readySlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER).copy(isLocalFileMissing = true)).canOpenOcrReview)
    }

    @Test
    fun upperBusyDisablesOcrReview() {
        assertFalse(state(upper = readySlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER).copy(isUploadInProgress = true), lower = readySlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER)).canOpenOcrReview)
    }

    @Test
    fun lowerBusyDisablesOcrReview() {
        assertFalse(state(upper = readySlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER), lower = readySlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER).copy(isUploadInProgress = true)).canOpenOcrReview)
    }

    @Test
    fun upperWrongCropProfileDisablesOcrReview() {
        assertFalse(state(upper = readySlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER).copy(cropProfileId = "wrong"), lower = readySlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER)).canOpenOcrReview)
    }

    @Test
    fun lowerWrongCropProfileDisablesOcrReview() {
        assertFalse(state(upper = readySlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER), lower = readySlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER).copy(cropProfileId = "wrong")).canOpenOcrReview)
    }

    @Test
    fun upperWithoutConfirmedCropDisablesOcrReview() {
        assertFalse(state(upper = readySlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER).copy(confirmedCrop = null), lower = readySlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER)).canOpenOcrReview)
    }

    @Test
    fun lowerWithoutConfirmedCropDisablesOcrReview() {
        assertFalse(state(upper = readySlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER), lower = readySlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER).copy(confirmedCrop = null)).canOpenOcrReview)
    }

    @Test
    fun bothLocallyReadyRemainEnabledWhenUploadIsPendingOrFailed() {
        assertTrue(
            state(
                upper = readySlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER).copy(uploadStatus = ScreenshotMetadataUploadUiStatus.PENDING),
                lower = readySlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER).copy(uploadStatus = ScreenshotMetadataUploadUiStatus.FAILED),
            ).canOpenOcrReview,
        )
    }

    @Test
    fun finalizedMatchDisablesOcrReviewEvenWhenBothRolesAreReady() {
        assertFalse(
            state(
                upper = readySlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
                lower = readySlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
            ).copy(status = com.hoggamers.rankforge.domain.tournament.MatchStatus.FINALIZED).canOpenOcrReview,
        )
    }

    @Test
    fun blankTournamentIdDisablesOcrReview() {
        assertFalse(
            state(
                upper = readySlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
                lower = readySlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
            ).copy(tournamentId = "").canOpenOcrReview,
        )
    }

    @Test
    fun blankMatchIdDisablesOcrReview() {
        assertFalse(
            state(
                upper = readySlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
                lower = readySlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
            ).copy(matchId = "").canOpenOcrReview,
        )
    }

    private fun state(
        upper: MatchResultScreenshotSlotUiState = MatchResultScreenshotSlotUiState(
            MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        ),
        lower: MatchResultScreenshotSlotUiState = MatchResultScreenshotSlotUiState(
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
        ),
    ) = MatchReviewUiState(
        isLoading = false,
        isAvailable = true,
        tournamentId = "tournament-id",
        matchId = "match-id",
        rows = (1..12).map { slot -> MatchReviewRowUiState(slot, "Team $slot") },
        resultScreenshots = listOf(upper, lower),
    )

    private fun readySlot(role: MatchResultScreenshotRole) = MatchResultScreenshotSlotUiState(
        role = role,
        hasLinkedAsset = true,
        confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
        cropProfileId = OcrCropValidationProfiles.MatchResult.id,
    )
}
