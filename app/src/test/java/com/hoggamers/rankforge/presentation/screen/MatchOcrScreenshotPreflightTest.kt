package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchOcrScreenshotPreflightTest {
    private fun readyLobbySlot(
        index: Int = 1,
        hasConfirmedCrop: Boolean = true,
        isPreviewPreparationInProgress: Boolean = false,
        isValidationInProgress: Boolean = false,
        isDuplicateDetectionInProgress: Boolean = false,
        isPreservationInProgress: Boolean = false,
    ) = MatchLobbyScreenshotSlotUiState(
        index = index,
        hasLinkedAsset = true,
        isPreviewPreparationInProgress = isPreviewPreparationInProgress,
        isValidationInProgress = isValidationInProgress,
        isDuplicateDetectionInProgress = isDuplicateDetectionInProgress,
        isPreservationInProgress = isPreservationInProgress,
        confirmedCrop = if (hasConfirmedCrop) OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9) else null,
        cropProfileId = if (hasConfirmedCrop) "lobby" else null,
    )

    private fun readyResultSlot(
        role: MatchResultScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        hasConfirmedCrop: Boolean = true,
        isPreviewPreparationInProgress: Boolean = false,
        isUploadInProgress: Boolean = false,
        isValidationInProgress: Boolean = false,
        isDuplicateDetectionInProgress: Boolean = false,
        isPreservationInProgress: Boolean = false,
    ) = MatchResultScreenshotSlotUiState(
        role = role,
        hasLinkedAsset = true,
        isPreviewPreparationInProgress = isPreviewPreparationInProgress,
        isUploadInProgress = isUploadInProgress,
        isValidationInProgress = isValidationInProgress,
        isDuplicateDetectionInProgress = isDuplicateDetectionInProgress,
        isPreservationInProgress = isPreservationInProgress,
        confirmedCrop = if (hasConfirmedCrop) OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9) else null,
        cropProfileId = if (hasConfirmedCrop) "match-result" else null,
    )

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

    @Test
    fun previewPreparationDoesNotBlockReadyLobbyScreenshot() {
        val issues = classifyOcrScreenshotPreflight(
            lobbySlots = listOf(readyLobbySlot(isPreviewPreparationInProgress = true)),
            resultSlots = emptyList(),
        )

        assertTrue(issues.none { it.identity == OcrScreenshotPreflightIdentity.Lobby(1) })
    }

    @Test
    fun previewPreparationDoesNotBlockReadyResultScreenshot() {
        val issues = classifyOcrScreenshotPreflight(
            lobbySlots = emptyList(),
            resultSlots = listOf(readyResultSlot(isPreviewPreparationInProgress = true)),
        )

        assertTrue(issues.none { it.identity == OcrScreenshotPreflightIdentity.Result(MatchResultScreenshotRole.MATCH_RESULT_UPPER) })
    }

    @Test
    fun linkedResultScreenshotWithoutCropIsReadyForOcr() {
        val issues = classifyOcrScreenshotPreflight(
            lobbySlots = emptyList(),
            resultSlots = listOf(readyResultSlot(hasConfirmedCrop = false)),
        )

        assertTrue(issues.none {
            it.identity == OcrScreenshotPreflightIdentity.Result(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        })
    }

    @Test
    fun resultWithoutCropDuringPreviewPreparationIsReadyForOcr() {
        val issues = classifyOcrScreenshotPreflight(
            lobbySlots = emptyList(),
            resultSlots = listOf(
                readyResultSlot(
                    hasConfirmedCrop = false,
                    isPreviewPreparationInProgress = true,
                ),
            ),
        )

        assertTrue(issues.none {
            it.identity == OcrScreenshotPreflightIdentity.Result(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        })
    }

    @Test
    fun resultWithoutCropDuringUploadIsReadyForOcr() {
        val issues = classifyOcrScreenshotPreflight(
            lobbySlots = emptyList(),
            resultSlots = listOf(
                readyResultSlot(
                    hasConfirmedCrop = false,
                    isUploadInProgress = true,
                ),
            ),
        )

        assertTrue(issues.none {
            it.identity == OcrScreenshotPreflightIdentity.Result(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        })
    }

    @Test
    fun multipleResultsWithoutCropsDoNotBlockOcr() {
        val issues = classifyOcrScreenshotPreflight(
            lobbySlots = (1..3).map { index -> readyLobbySlot(index = index) },
            resultSlots = MatchResultScreenshotRole.entries.map { role ->
                readyResultSlot(role = role, hasConfirmedCrop = false)
            },
        )

        assertTrue(issues.isEmpty())
    }

    @Test
    fun lobbyWithoutCropBlocksWhenResultsAreReady() {
        val issues = classifyOcrScreenshotPreflight(
            lobbySlots = listOf(readyLobbySlot(hasConfirmedCrop = false)),
            resultSlots = MatchResultScreenshotRole.entries.map { role -> readyResultSlot(role = role) },
        )

        assertEquals(
            OcrScreenshotPreflightIssue.CROP_REQUIRED,
            issues.first { it.identity == OcrScreenshotPreflightIdentity.Lobby(1) }.issue,
        )
    }

    @Test
    fun resultUploadDoesNotBlockReadyResultScreenshot() {
        val issues = classifyOcrScreenshotPreflight(
            lobbySlots = emptyList(),
            resultSlots = listOf(readyResultSlot(isUploadInProgress = true)),
        )

        assertTrue(issues.none { it.identity == OcrScreenshotPreflightIdentity.Result(MatchResultScreenshotRole.MATCH_RESULT_UPPER) })
    }

    @Test
    fun previewPreparationAndUploadTogetherDoNotBlockReadyResultScreenshot() {
        val issues = classifyOcrScreenshotPreflight(
            lobbySlots = (1..3).map { index -> readyLobbySlot(index = index) },
            resultSlots = listOf(
                readyResultSlot(
                    isPreviewPreparationInProgress = true,
                    isUploadInProgress = true,
                ),
                readyResultSlot(role = MatchResultScreenshotRole.MATCH_RESULT_LOWER),
            ),
        )

        assertTrue(issues.isEmpty())
    }

    @Test
    fun validationDuplicateAndPreservationRemainProcessing() {
        val issues = classifyOcrScreenshotPreflight(
            lobbySlots = listOf(
                readyLobbySlot(isValidationInProgress = true),
                readyLobbySlot(index = 2, isDuplicateDetectionInProgress = true),
                readyLobbySlot(index = 3, isPreservationInProgress = true),
            ),
            resultSlots = listOf(
                readyResultSlot(isValidationInProgress = true),
                readyResultSlot(
                    role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                    isDuplicateDetectionInProgress = true,
                ),
            ),
        )

        assertEquals(5, issues.size)
        assertTrue(issues.all { it.issue == OcrScreenshotPreflightIssue.PROCESSING })
    }

    @Test
    fun replacementWithOldConfirmedAssetRemainsProcessing() {
        val issues = classifyOcrScreenshotPreflight(
            lobbySlots = listOf(readyLobbySlot(isValidationInProgress = true)),
            resultSlots = listOf(
                readyResultSlot(isPreservationInProgress = true),
                readyResultSlot(role = MatchResultScreenshotRole.MATCH_RESULT_LOWER),
            ),
        )

        assertEquals(
            OcrScreenshotPreflightIssue.PROCESSING,
            issues.first { it.identity == OcrScreenshotPreflightIdentity.Lobby(1) }.issue,
        )
        assertEquals(
            OcrScreenshotPreflightIssue.PROCESSING,
            issues.first {
                it.identity == OcrScreenshotPreflightIdentity.Result(
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                )
            }.issue,
        )
    }

    @Test
    fun linkedScreenshotWithoutCropRequiresCropWhenIdle() {
        val issues = classifyOcrScreenshotPreflight(
            lobbySlots = (1..3).map { index ->
                MatchLobbyScreenshotSlotUiState(index = index, hasLinkedAsset = true)
            },
            resultSlots = MatchResultScreenshotRole.entries.map { role ->
                MatchResultScreenshotSlotUiState(role = role, hasLinkedAsset = true)
            },
        )

        assertEquals(3, issues.size)
        assertTrue(issues.all {
            it.identity is OcrScreenshotPreflightIdentity.Lobby &&
                it.issue == OcrScreenshotPreflightIssue.CROP_REQUIRED
        })
    }
}
