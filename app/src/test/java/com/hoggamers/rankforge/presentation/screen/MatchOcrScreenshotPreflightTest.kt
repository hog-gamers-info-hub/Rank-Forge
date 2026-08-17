package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchOcrScreenshotPreflightTest {
    @Test
    fun classifiesAllFiveInputsInStableIdentityOrder() {
        val issues = classifyOcrScreenshotPreflight(
            lobbySlots = listOf(
                MatchLobbyScreenshotSlotUiState(index = 1),
                MatchLobbyScreenshotSlotUiState(index = 2, hasLinkedAsset = true),
                MatchLobbyScreenshotSlotUiState(index = 3, isValidationInProgress = true),
            ),
            resultSlots = listOf(
                MatchResultScreenshotSlotUiState(
                    role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    hasLinkedAsset = true,
                    isLocalFileMissing = true,
                ),
                MatchResultScreenshotSlotUiState(
                    role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                    hasLinkedAsset = true,
                    confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                    cropProfileId = "match-result",
                ),
            ),
        )

        assertEquals(
            listOf(
                OcrScreenshotPreflightIdentity.Lobby(1),
                OcrScreenshotPreflightIdentity.Lobby(2),
                OcrScreenshotPreflightIdentity.Lobby(3),
                OcrScreenshotPreflightIdentity.Result(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            ),
            issues.map { it.identity },
        )
        assertEquals(
            listOf(
                OcrScreenshotPreflightIssue.MISSING,
                OcrScreenshotPreflightIssue.CROP_REQUIRED,
                OcrScreenshotPreflightIssue.PROCESSING,
                OcrScreenshotPreflightIssue.LOCAL_FILE_MISSING,
            ),
            issues.map { it.issue },
        )
    }

    @Test
    fun readyInputsProduceNoIssuesAndBusyTakesPrecedence() {
        val readyLobby = (1..3).map { index ->
            MatchLobbyScreenshotSlotUiState(
                index = index,
                hasLinkedAsset = true,
                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                cropProfileId = "lobby",
            )
        }
        val readyResult = listOf(
            MatchResultScreenshotSlotUiState(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                hasLinkedAsset = true,
                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                cropProfileId = "match-result",
            ),
            MatchResultScreenshotSlotUiState(
                role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                hasLinkedAsset = true,
                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                cropProfileId = "match-result",
            ),
        )

        assertTrue(classifyOcrScreenshotPreflight(readyLobby, readyResult).isEmpty())
        val busy = readyLobby.map { slot ->
            if (slot.index == 2) slot.copy(isPreservationInProgress = true) else slot
        }
        val issue = classifyOcrScreenshotPreflight(busy, readyResult).single()
        assertEquals(OcrScreenshotPreflightIdentity.Lobby(2), issue.identity)
        assertEquals(OcrScreenshotPreflightIssue.PROCESSING, issue.issue)
    }
}
