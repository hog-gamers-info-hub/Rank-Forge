package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.export.ResultDownloadFailure
import com.hoggamers.rankforge.data.export.ResultDownloadScope
import com.hoggamers.rankforge.data.export.ResultExportFileFormat
import com.hoggamers.rankforge.data.ocr.matchlobby.AndroidMatchLobbyTeamCropPreviewImage
import com.hoggamers.rankforge.data.ocr.matchlobby.LobbyPlayerRowCropPreview
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrScreenshotResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrSlot
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreview
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewResult
import com.hoggamers.rankforge.data.ocr.MatchOcrCacheAvailability
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropBounds
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorSource
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotNumberCandidate
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionColumn
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCrop
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionRecord
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val LOBBY_SCREENSHOT_DESCRIPTION =
    "Select all the screenshots of the lobby after all players have joined. Crop tightly to the lobby area and exclude any extra text, overlays, or unrelated content."
private const val RESULT_SCREENSHOT_DESCRIPTION =
    "Select all the screenshots showing the final match results. Crop tightly to the results area and exclude any extra text, overlays, or unrelated content."

@RunWith(AndroidJUnit4::class)
class MatchReviewScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyLobbyAndResultShowBothScreenshotDescriptions() {
        setScreenshotDescriptionContent()

        assertScreenshotDescriptions(lobbyVisible = true, resultVisible = true)
    }

    @Test
    fun selectedLobbyHidesLobbyDescriptionButKeepsResultDescription() {
        setScreenshotDescriptionContent(lobbyUiState = allLobbyReadyState())

        composeTestRule.onAllNodesWithText(LOBBY_SCREENSHOT_DESCRIPTION)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText(RESULT_SCREENSHOT_DESCRIPTION)
            .assertCountEquals(1)
    }

    @Test
    fun selectedResultHidesOnlyResultScreenshotDescription() {
        setScreenshotDescriptionContent(resultScreenshots = allResultReadySlots())

        assertScreenshotDescriptions(lobbyVisible = true, resultVisible = false)
    }

    @Test
    fun selectedLobbyAndResultHideBothScreenshotDescriptions() {
        setScreenshotDescriptionContent(
            lobbyUiState = allLobbyReadyState(),
            resultScreenshots = allResultReadySlots(),
        )

        assertScreenshotDescriptions(lobbyVisible = false, resultVisible = false)
    }

    @Test
    fun deleteMatchActionOpensConfirmationAndCancelDoesNotInvokeDeletion() {
        var deleteRequests = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onDeleteMatch = { deleteRequests++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OVERFLOW_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_DELETE_ACTION_TEST_TAG)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_DELETE_DIALOG_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete Match?").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Are you sure you want to delete Match 1?",
            substring = true,
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_DELETE_CANCEL_ACTION_TEST_TAG).performClick()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_DELETE_DIALOG_TEST_TAG).assertCountEquals(0)
        composeTestRule.runOnIdle { assertEquals(0, deleteRequests) }
    }

    @Test
    fun deleteConfirmationStartsLockedProgressState() {
        var uiState by mutableStateOf(availableState())
        var deleteRequests = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = uiState,
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onDeleteMatch = {
                        deleteRequests++
                        uiState = uiState.copy(isDeleting = true)
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OVERFLOW_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_DELETE_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_DELETE_CONFIRM_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle { assertEquals(1, deleteRequests) }
        composeTestRule.onNodeWithTag(MATCH_REVIEW_DELETE_PROGRESS_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_DELETE_DIALOG_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun deleteActionRemainsAvailableForFinalizedMatches() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(status = MatchStatus.FINALIZED),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_DELETE_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun draftReviewAdjustsSelectedTeamWithTemporarySignedIndicator() {
        composeTestRule.setContent {
            RankForgeTheme {
                var uiState by remember {
                    mutableStateOf(availableState(resultScreenshots = allResultReadySlots()))
                }
                MatchReviewScreen(
                    uiState = uiState,
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrStateWithRows(),
                    onSaveTeamPointAdjustment = { teamSlotNumber, pointAdjustment ->
                        uiState = uiState.copy(
                            rows = uiState.rows.map { row ->
                                if (row.teamSlotNumber == teamSlotNumber) {
                                    row.copy(pointAdjustment = pointAdjustment)
                                } else {
                                    row
                                }
                            },
                        )
                    },
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactMenu(2))[0]
            .performClick()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.adjustTeamPoints(1))
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_DIALOG_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Team 2").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_INPUT_TEST_TAG)
            .performTextReplacement("-3")
        composeTestRule.onNodeWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_APPLY_TEST_TAG)
            .assertIsEnabled()
            .performClick()

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_ADJUSTMENT_INDICATOR_TEST_TAG_PREFIX + "2")
            .assertIsDisplayed()
            .assertTextContains("Points adjustment: -3")
        composeTestRule
            .onNodeWithText("1. Player Two - [9]", substring = true)
            .assertIsDisplayed()

        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactMenu(2))[0]
            .performClick()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.adjustTeamPoints(1))
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_INPUT_TEST_TAG)
            .performTextReplacement("3")
        composeTestRule.onNodeWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_APPLY_TEST_TAG)
            .assertIsEnabled()
            .performClick()
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_ADJUSTMENT_INDICATOR_TEST_TAG_PREFIX + "2")
            .assertTextContains("Points adjustment: +3")

        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactMenu(2))[0]
            .performClick()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.adjustTeamPoints(1))
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_INPUT_TEST_TAG)
            .performTextReplacement("0")
        composeTestRule.onNodeWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_APPLY_TEST_TAG)
            .assertIsEnabled()
            .performClick()
        composeTestRule
            .onAllNodesWithTag(MATCH_REVIEW_ADJUSTMENT_INDICATOR_TEST_TAG_PREFIX + "2")
            .assertCountEquals(0)
    }

    @Test
    fun cancelingTeamPointAdjustmentLeavesNoTemporaryIndicator() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(resultScreenshots = allResultReadySlots()),
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrState(),
                )
            }
        }

        openAdjustTeamPointsDialog()
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_INPUT_TEST_TAG)
            .performTextInput("-3")
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_CANCEL_TEST_TAG)
            .performClick()

        composeTestRule
            .onAllNodesWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_DIALOG_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithTag(MATCH_REVIEW_ADJUSTMENT_INDICATOR_TEST_TAG_PREFIX + "1")
            .assertCountEquals(0)
    }

    @Test
    fun finalizedReviewHidesAdjustTeamPointsAndKeepsDeleteAction() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(status = MatchStatus.FINALIZED),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_OVERFLOW_ACTION_TEST_TAG).performClick()
        composeTestRule
            .onAllNodesWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_ACTION_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_DELETE_ACTION_TEST_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun finalizedReviewShowsNumberedCreateNextMatchAction() {
        var createRequests = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        status = MatchStatus.FINALIZED,
                        finalizedParticipantSlotNumbers = (1..12).toSet(),
                        nextMatchNumber = 6,
                        existingMatchCount = 5,
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onRequestNextMatchCreation = { createRequests++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OVERFLOW_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_CREATE_NEXT_MATCH_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
        composeTestRule.onNodeWithText("Create Match 6").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_CREATE_NEXT_MATCH_ACTION_TEST_TAG).performClick()
        composeTestRule.runOnIdle { assertEquals(1, createRequests) }
    }

    @Test
    fun draftReviewShowsCreateNextMatchAction() {
        var createRequests = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(nextMatchNumber = 2, existingMatchCount = 1),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onRequestNextMatchCreation = { createRequests++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OVERFLOW_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_CREATE_NEXT_MATCH_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
        composeTestRule.onNodeWithText("Create Match 2").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_CREATE_NEXT_MATCH_ACTION_TEST_TAG).performClick()
        composeTestRule.runOnIdle { assertEquals(1, createRequests) }
    }

    @Test
    fun deletionErrorLeavesMatchReviewVisible() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        deletionError = MatchDeletionUiError.REMOTE_FAILURE,
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_DELETE_ERROR_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("The match could not be deleted from the cloud. Try again.")
            .assertIsDisplayed()
    }

    @Test
    fun simplifiedReviewShowsLobbyBeforeResultAndHidesLegacyManualContent() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    matchLobbyScreenshotIntake = {
                        androidx.compose.material3.Text("Lobby screenshot slots")
                    },
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Review Match 1").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_LOBBY_SCREENSHOTS_SECTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_SECTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_DETAILS_HEADER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_DETAILS_STEP_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_ROW_TEST_TAG_PREFIX + "1").assertCountEquals(0)
        composeTestRule.onNodeWithText("Manual Calculate")
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertCountEquals(0)
        val lobbyY = composeTestRule.onNodeWithTag(MATCH_REVIEW_LOBBY_SCREENSHOTS_SECTION_TEST_TAG)
            .fetchSemanticsNode().positionInRoot.y
        val resultY = composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_SECTION_TEST_TAG)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(lobbyY < resultY)
    }

    @Test
    fun simplifiedReviewKeepsFullScreenLoadingWhileLobbyLoads() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    lobbyUiState = MatchLobbyScreenshotIntakeUiState(isLoading = true),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    matchLobbyScreenshotIntake = {
                        androidx.compose.material3.Text("Lobby screenshot slots")
                    },
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESTORE_SKELETON_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Review Match 1").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Lobby screenshot slots").assertCountEquals(0)
    }

    @Test
    fun simplifiedReviewRendersAfterLobbyTransitionsFromLoadingToReady() {
        var lobbyUiState by mutableStateOf(MatchLobbyScreenshotIntakeUiState(isLoading = true))
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    lobbyUiState = lobbyUiState,
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    matchLobbyScreenshotIntake = {
                        androidx.compose.material3.Text("Lobby screenshot slots")
                    },
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESTORE_SKELETON_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.runOnIdle { lobbyUiState = allLobbyReadyState() }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESTORE_SKELETON_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_LOBBY_SCREENSHOTS_SECTION_TEST_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun ocrPreflightIsShownWithZeroScreenshotsAndKeepsOcrReviewEnabled() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Manual Calculate")
            .assertIsDisplayed()
            .assertIsEnabled()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun foundCustomDesignShowsMyCustomDesignAndDownloadsWithoutSetupNavigation() {
        var download: Pair<DownloadResultSelection, DownloadResultDesignType>? = null
        var importCalls = 0
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = {},
                    initialResult = DownloadResultSelection.Overall,
                    initialDesign = DownloadResultDesignType.MY_DESIGN,
                    previewState = DownloadResultPreviewState.ResultImage(ByteArray(0)),
                    hasSavedCustomDesign = true,
                    onImportYourDesign = { importCalls++ },
                    onDownload = { selection, design -> download = selection to design },
                )
            }
        }

        composeTestRule.onNodeWithTag(DOWNLOAD_RESULT_DESIGN_MY_OPTION_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Import Your Design").assertCountEquals(0)
        composeTestRule.onNodeWithText("Download").performClick()

        composeTestRule.runOnIdle {
            assertEquals(DownloadResultSelection.Overall to DownloadResultDesignType.MY_DESIGN, download)
            assertEquals(0, importCalls)
        }
    }

    @Test
    fun completeNonLegacyEvidenceBypassesPreflightAndCalculatesPoints() {
        var opened = 0
        var calculated = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(resultScreenshots = allResultReadySlots()),
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onOpenOcrReview = { opened++ },
                    onCalculatePoints = { calculated++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_OCR_PREFLIGHT_DIALOG_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.runOnIdle {
            assertEquals(0, opened)
            assertEquals(1, calculated)
        }
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun clearResultActionIsVisibleAndInvokesOnlyClearCallback() {
        var clearCount = 0
        var calculateCount = 0
        val originalState = availableState(resultScreenshots = allResultReadySlots())

        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = originalState,
                    lobbyUiState = allLobbyReadyState(),
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onCalculatePoints = { calculateCount++ },
                    onClearResult = { clearCount++ },
                )
            }
        }

        val finalizeY = composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .performScrollTo()
            .fetchSemanticsNode()
            .positionInRoot
            .y
        val clearY = composeTestRule.onNodeWithTag(MATCH_REVIEW_CLEAR_RESULT_ACTION_TEST_TAG)
            .performScrollTo()
            .fetchSemanticsNode()
            .positionInRoot
            .y
        assertTrue(finalizeY < clearY)
        composeTestRule.onNodeWithText("Finalize Result").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Finalize Match").assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_CLEAR_RESULT_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, clearCount)
            assertEquals(0, calculateCount)
            assertEquals("7", originalState.rows.first().placementInput)
            assertEquals("3", originalState.rows.first().killsInput)
        }
    }

    @Test
    fun simplifiedReviewDoesNotShowClearResultForScreenshotSelectionWithoutDisplayedOcr() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(resultScreenshots = allResultReadySlots()),
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showClearResult = true,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_CLEAR_RESULT_ACTION_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun calculatedOcrDetailsRemainVisibleAfterCalculatingStateRecomposition() {
        var ocrUiState by mutableStateOf<MatchOcrReviewUiState>(MatchOcrReviewUiState.Loading)
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(resultScreenshots = allResultReadySlots()),
                    lobbyUiState = allLobbyReadyState(),
                    ocrUiState = ocrUiState,
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onCalculatePoints = {
                        ocrUiState = MatchOcrReviewUiState.Calculating(
                            tournamentId = "tournament-id",
                            matchId = "match-id",
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("Calculating Results").assertIsDisplayed()

        composeTestRule.runOnIdle {
            ocrUiState = inlineOcrState()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("1. Player One - [8]", substring = true)
            .assertExists()
    }

    @Test
    fun completeLegacyEvidenceOpensOcrReviewWithoutCalculatingPoints() {
        var opened = 0
        var calculated = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(resultScreenshots = allResultReadySlots()),
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onOpenOcrReview = { opened++ },
                    onCalculatePoints = { calculated++ },
                    showLegacyManualReviewContent = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, opened)
            assertEquals(0, calculated)
        }
    }

    @Test
    fun lobbyIssueOffersOnlyItsExactSelectAction() {
        var selectedIndex: Int? = null
        val lobbyState = allLobbyReadyState().copy(
            slots = allLobbyReadyState().slots.map { slot ->
                if (slot.index == 2) slot.copy(hasLinkedAsset = false, confirmedCrop = null, cropProfileId = null)
                else slot
            },
        )
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(resultScreenshots = allResultReadySlots()),
                    lobbyUiState = lobbyState,
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onSelectLobbyScreenshot = { selectedIndex = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("Lobby Screenshot 2 is not available.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select Lobby Screenshot 2").performClick()
        composeTestRule.runOnIdle { assertEquals(2, selectedIndex) }
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_OCR_PREFLIGHT_DIALOG_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun lowerResultIssueUsesLowerRoleSelectAction() {
        var selectedRole: MatchResultScreenshotRole? = null
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            allResultReadySlots().first(),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onSelectResultScreenshot = { selectedRole = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("Select Result Screenshot 2").performClick()
        composeTestRule.runOnIdle {
            assertEquals(MatchResultScreenshotRole.MATCH_RESULT_LOWER, selectedRole)
        }
    }

    @Test
    fun cropAndLocalMissingIssuesOfferCropAndReplace() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            allResultReadySlots().first(),
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                isLocalFileMissing = true,
                            ),
                        ),
                    ),
                    lobbyUiState = allLobbyReadyState().copy(
                        slots = allLobbyReadyState().slots.map { slot ->
                            if (slot.index == 3) slot.copy(confirmedCrop = null, cropProfileId = null)
                            else slot
                        },
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("Lobby Screenshot 3 needs a confirmed crop.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Crop Lobby Screenshot 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Result Screenshot 2 local image is unavailable.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Replace Result Screenshot 2").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Select Result Screenshot 2").assertCountEquals(0)
    }

    @Test
    fun processingIssueDisablesCalculateWithoutAnActionButton() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(resultScreenshots = allResultReadySlots()),
                    lobbyUiState = allLobbyReadyState().copy(
                        slots = allLobbyReadyState().slots.map { slot ->
                            if (slot.index == 2) slot.copy(isValidationInProgress = true) else slot
                        },
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("Lobby Screenshot 2 is still processing.")
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Select Lobby Screenshot 2").assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_PREFLIGHT_CALCULATE_ACTION_TEST_TAG)
            .assertIsNotEnabled()
    }

    @Test
    fun cancelDoesNotOpenInlineOcrOrStartCalculation() {
        var calculated = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(resultScreenshots = selectedResultScreenshotSlots()),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onCalculatePoints = { calculated++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_PREFLIGHT_CANCEL_ACTION_TEST_TAG)
            .performClick()
        composeTestRule.runOnIdle { assertEquals(0, calculated) }
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_OCR_PREFLIGHT_DIALOG_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun calculatePointsAcceptsIncompleteEvidenceAndOpensInlineOcr() {
        var opened = 0
        var calculated = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(resultScreenshots = selectedResultScreenshotSlots()),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onOpenOcrReview = { opened++ },
                    onCalculatePoints = { calculated++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_PREFLIGHT_CALCULATE_ACTION_TEST_TAG)
            .performClick()
        composeTestRule.runOnIdle {
            assertEquals(0, opened)
            assertEquals(1, calculated)
        }
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun noLobbyOcrEvidenceHidesLobbyPlayerDetails() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    ocrUiState = MatchOcrReviewUiState.Empty(
                        lobbyPlayers = listOf(
                            MatchOcrReviewLobbySlotUiState(
                                slotNumber = 1,
                                players = emptyList(),
                            ),
                        ),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_LOBBY_SCREENSHOTS_SECTION_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_LOBBY_PLAYER_DETAILS_SECTION_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText("1. Not detected")
            .assertCountEquals(0)
    }

    @Test
    fun inlineOcrContentFollowsItsScreenshotSectionsAndKeepsExistingStructure() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(resultScreenshots = selectedResultScreenshotSlots()),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    matchLobbyScreenshotIntake = {
                        androidx.compose.material3.Text("Lobby screenshot slots")
                    },
                    showLegacyManualReviewContent = false,
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrState(),
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_LOBBY_PLAYER_DETAILS_SECTION_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.LOBBY_PLAYERS)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbySlot(11))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbyPlayer(11, 1))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbyPlayer(11, 2))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbyPlayer(11, 3))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.ROW_LIST)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactRow(1))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.placementInput(0))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.killsInput(0))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotInput(0))
            .assertIsDisplayed()
        val placementFieldY = composeTestRule
            .onNodeWithTag(MatchOcrReviewTestTags.placementInput(0))
            .fetchSemanticsNode().positionInRoot.y
        val killsFieldY = composeTestRule
            .onNodeWithTag(MatchOcrReviewTestTags.killsInput(0))
            .fetchSemanticsNode().positionInRoot.y
        val teamSlotFieldY = composeTestRule
            .onNodeWithTag(MatchOcrReviewTestTags.teamSlotInput(0))
            .fetchSemanticsNode().positionInRoot.y
        assertEquals(placementFieldY, killsFieldY)
        assertEquals(placementFieldY, teamSlotFieldY)

        val lobbyScreenshotsY = composeTestRule
            .onNodeWithTag(MATCH_REVIEW_LOBBY_SCREENSHOTS_SECTION_TEST_TAG)
            .fetchSemanticsNode().positionInRoot.y
        val lobbyDetailsY = composeTestRule
            .onNodeWithTag(MATCH_REVIEW_LOBBY_PLAYER_DETAILS_SECTION_TEST_TAG)
            .fetchSemanticsNode().positionInRoot.y
        val resultScreenshotsY = composeTestRule
            .onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_SECTION_TEST_TAG)
            .fetchSemanticsNode().positionInRoot.y
        val resultDetailsY = composeTestRule
            .onNodeWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(lobbyScreenshotsY < lobbyDetailsY)
        assertTrue(lobbyDetailsY < resultScreenshotsY)
        assertTrue(resultScreenshotsY < resultDetailsY)
    }

    @Test
    fun lobbySourceIntakeCollapsesTogetherWhenLobbyPlayerEvidenceAppears() {
        var ocrUiState by mutableStateOf<MatchOcrReviewUiState>(
            MatchOcrReviewUiState.Empty(lobbyPlayers = emptyList()),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = ocrUiState,
                    matchLobbyScreenshotIntake = {
                        Text(
                            text = "Lobby Details",
                            modifier = Modifier.testTag("lobby_details_title"),
                        )
                        Text(
                            text = "Lobby details description",
                            modifier = Modifier.testTag("lobby_details_description"),
                        )
                        Text(
                            text = "Save Lobby",
                            modifier = Modifier.testTag("save_lobby"),
                        )
                        Text(
                            text = "Save Lobby switch",
                            modifier = Modifier.testTag("save_lobby_switch"),
                        )
                        if (LocalMatchLobbySourceSectionVisible.current) {
                            Column {
                                (1..3).forEach { index ->
                                    Text(
                                        text = "Source screenshot $index",
                                        modifier = Modifier.testTag("source_screenshot_$index"),
                                    )
                                    Text(
                                        text = "Replace $index",
                                        modifier = Modifier.testTag("source_replace_$index"),
                                    )
                                    Text(
                                        text = "Edit $index",
                                        modifier = Modifier.testTag("source_edit_$index"),
                                    )
                                    Text(
                                        text = "Remove $index",
                                        modifier = Modifier.testTag("source_remove_$index"),
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Canonical team crop",
                            modifier = Modifier.testTag("canonical_team_crop"),
                        )
                    },
                )
            }
        }

        (1..3).forEach { index ->
            composeTestRule.onNodeWithTag("source_screenshot_$index").assertIsDisplayed()
            composeTestRule.onNodeWithTag("source_replace_$index").assertIsDisplayed()
            composeTestRule.onNodeWithTag("source_edit_$index").assertIsDisplayed()
            composeTestRule.onNodeWithTag("source_remove_$index").assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag("canonical_team_crop").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lobby_details_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lobby_details_description").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_lobby").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_lobby_switch").assertIsDisplayed()

        composeTestRule.runOnIdle {
            ocrUiState = inlineOcrState().copy(
                phase1LobbySlotNumberOcr = processedLobbySlotNumberOcrWithPlayerRows(),
            )
        }
        composeTestRule.waitForIdle()

        (1..3).forEach { index ->
            composeTestRule.onAllNodesWithTag("source_screenshot_$index").assertCountEquals(0)
            composeTestRule.onAllNodesWithTag("source_replace_$index").assertCountEquals(0)
            composeTestRule.onAllNodesWithTag("source_edit_$index").assertCountEquals(0)
            composeTestRule.onAllNodesWithTag("source_remove_$index").assertCountEquals(0)
        }
        composeTestRule.onNodeWithTag("canonical_team_crop").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lobby_details_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lobby_details_description").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_lobby").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_lobby_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbySlot(11))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbyPlayer(11, 1))
            .assertIsDisplayed()
    }

    @Test
    fun inlineLobbyPagerUsesSlotOrderAndReturnsToThePreviousSlot() {
        val firstSlot = MatchOcrReviewLobbySlotUiState(
            slotNumber = 11,
            players = listOf(MatchOcrReviewLobbyPlayerUiState(1, "Lobby Eleven")),
        )
        val secondSlot = MatchOcrReviewLobbySlotUiState(
            slotNumber = 2,
            players = listOf(MatchOcrReviewLobbyPlayerUiState(1, "Lobby Two")),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(resultScreenshots = selectedResultScreenshotSlots()),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrState().copy(
                        teamNamesBySlot = mapOf(2 to "Team 2", 11 to "Team 11"),
                        lobbyPlayers = listOf(firstSlot, secondSlot),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_LOBBY_PLAYERS_PAGER_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbySlot(2))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbyPlayer(2, 1))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbySlot(11))
            .assertIsNotDisplayed()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_LOBBY_PLAYERS_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbySlot(11))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbyPlayer(11, 1))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbySlot(2))
            .assertIsNotDisplayed()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_LOBBY_PLAYERS_PAGER_TEST_TAG)
            .performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbySlot(2))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbyPlayer(2, 1))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbySlot(11))
            .assertIsNotDisplayed()
    }

    @Test
    fun inlineResultRowsPagerUsesSuppliedRowOrderAndKeepsRowsIndependentFromScreenshotPager() {
        val state = inlineOcrStateWithRows()
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                        ),
                    ),
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = state,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_ROWS_PAGER_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.row(1))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.row(0))
            .assertIsNotDisplayed()
        composeTestRule.onNodeWithText("Position - 2").assertIsDisplayed()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_ROWS_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.row(0))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Position - 1").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.row(1))
            .assertIsNotDisplayed()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .assertIsDisplayed()
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_SECTION_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.row(0))
            .assertIsDisplayed()
    }

    @Test
    fun inlineResultPreviewPagerUsesPreviewRowsWithoutAddingAnotherSort() {
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            rows = listOf(
                previewRow(2, MatchResultScreenshotRole.MATCH_RESULT_UPPER),
                previewRow(1, MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            ),
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = MatchOcrReviewUiState.Empty(
                        tournamentId = "tournament-id",
                        matchId = "match-id",
                        matchResultOcrPreview = preview,
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_PREVIEW_PAGER_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactRow(2))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactRow(1))
            .assertIsNotDisplayed()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_PREVIEW_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactRow(1))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactRow(2))
            .assertIsNotDisplayed()
    }

    @Test
    fun resultSourceVisibilityFollowsDraftAndFinalizedOcrLifecycle() {
        val positionCropImage = AndroidMatchResultPositionCropPreviewImage(
            Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
        )
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            rows = listOf(previewRow(1, MatchResultScreenshotRole.MATCH_RESULT_UPPER)),
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )
        var reviewUiState by mutableStateOf(
            availableState(
                resultScreenshots = listOf(
                    resultSlot(
                        MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                        hasLinkedAsset = true,
                        localPreviewUri = "file:///private/result-1.png",
                        originalWidth = 1920,
                        originalHeight = 1080,
                        confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                        cropProfileId = "match-result",
                    ),
                    resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                ),
                resultPositionCropPreviews = mapOf(
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER to
                        MatchResultPositionCropPreviewState.Available(
                            listOf(MatchResultPositionCropPreview(1, positionCropImage)),
                        ),
                ),
            ),
        )
        var showInlineOcrDetails by mutableStateOf(true)
        var ocrUiState by mutableStateOf<MatchOcrReviewUiState>(MatchOcrReviewUiState.Empty())
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = reviewUiState,
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    showInlineOcrDetails = showInlineOcrDetails,
                    ocrUiState = ocrUiState,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_VIEWPORT_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_POSITION_CROPS_UPPER_PAGER_TEST_TAG)
            .assertIsDisplayed()

        composeTestRule.runOnIdle { ocrUiState = MatchOcrReviewUiState.Loading }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_VIEWPORT_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()

        composeTestRule.runOnIdle {
            ocrUiState = MatchOcrReviewUiState.Error(message = "Result OCR unavailable")
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_VIEWPORT_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()

        composeTestRule.runOnIdle {
            ocrUiState = MatchOcrReviewUiState.Empty(
                tournamentId = "tournament-id",
                matchId = "match-id",
                matchResultOcrPreview = preview,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_VIEWPORT_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_POSITION_CROPS_UPPER_PAGER_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_OCR_PREVIEW_PAGER_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactRow(1))[0]
            .assertIsDisplayed()

        composeTestRule.runOnIdle { ocrUiState = inlineOcrState() }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_VIEWPORT_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.row(0))[0]
            .assertIsDisplayed()

        composeTestRule.runOnIdle {
            reviewUiState = reviewUiState.copy(
                status = MatchStatus.FINALIZED,
                finalizedParticipantSlotNumbers = setOf(1),
            )
            showInlineOcrDetails = false
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_VIEWPORT_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_ROWS_PAGER_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.row(6))
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_POSITION_CROPS_UPPER_PAGER_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun resultPositionPagerPairsUpperRectanglesWithTheirExactPreviewRows() {
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            rows = listOf(
                previewRow(1, MatchResultScreenshotRole.MATCH_RESULT_UPPER),
                previewRow(2, MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            ),
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                        resultPositionCropPreviews = mapOf(
                            MatchResultScreenshotRole.MATCH_RESULT_UPPER to
                                MatchResultPositionCropPreviewState.Available(
                                    listOf(
                                        MatchResultPositionCropPreview(
                                            1,
                                            AndroidMatchResultPositionCropPreviewImage(
                                                Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
                                            ),
                                        ),
                                        MatchResultPositionCropPreview(
                                            2,
                                            AndroidMatchResultPositionCropPreviewImage(
                                                Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
                                            ),
                                        ),
                                    ),
                                ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = MatchOcrReviewUiState.Empty(
                        tournamentId = "tournament-id",
                        matchId = "match-id",
                        matchResultOcrPreview = preview,
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_POSITION_CROPS_UPPER_PAGER_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(
            MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "1",
        )[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactRow(1))[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_OCR_PREVIEW_PAGER_TEST_TAG)
            .assertCountEquals(0)

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_POSITION_CROPS_UPPER_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag(
            MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "2",
        )[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactRow(2))[0]
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactRow(1))
            .assertIsNotDisplayed()
    }

    @Test
    fun resultPositionPagerKeepsWholePageHeightStableAcrossDifferentCropRatios() {
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            rows = listOf(
                previewRow(1, MatchResultScreenshotRole.MATCH_RESULT_UPPER),
                previewRow(2, MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            ),
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                        resultPositionCropPreviews = mapOf(
                            MatchResultScreenshotRole.MATCH_RESULT_UPPER to
                                MatchResultPositionCropPreviewState.Available(
                                    listOf(
                                        MatchResultPositionCropPreview(
                                            1,
                                            AndroidMatchResultPositionCropPreviewImage(
                                                Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
                                            ),
                                        ),
                                        MatchResultPositionCropPreview(
                                            2,
                                            AndroidMatchResultPositionCropPreviewImage(
                                                Bitmap.createBitmap(12, 30, Bitmap.Config.ARGB_8888),
                                            ),
                                        ),
                                    ),
                                ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = MatchOcrReviewUiState.Empty(
                        tournamentId = "tournament-id",
                        matchId = "match-id",
                        matchResultOcrPreview = preview,
                    ),
                )
            }
        }

        val pager = composeTestRule.onNodeWithTag(
            MATCH_REVIEW_RESULT_POSITION_CROPS_UPPER_PAGER_TEST_TAG,
        )
        val initialHeight = pager.fetchSemanticsNode().boundsInRoot.height

        pager.performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        val nextHeight = pager.fetchSemanticsNode().boundsInRoot.height
        assertEquals(initialHeight, nextHeight, 0.5f)
        composeTestRule.onAllNodesWithTag(
            MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "2",
        )[0]
            .assertIsDisplayed()
    }

    @Test
    fun resultPositionPagerPairsLowerPositionsElevenAndTwelveWithTheirExactPreviewRows() {
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
            rows = listOf(
                previewRow(11, MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                previewRow(12, MatchResultScreenshotRole.MATCH_RESULT_LOWER),
            ),
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                        ),
                        resultPositionCropPreviews = mapOf(
                            MatchResultScreenshotRole.MATCH_RESULT_LOWER to
                                MatchResultPositionCropPreviewState.Available(
                                    listOf(
                                        MatchResultPositionCropPreview(
                                            11,
                                            AndroidMatchResultPositionCropPreviewImage(
                                                Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
                                            ),
                                        ),
                                        MatchResultPositionCropPreview(
                                            12,
                                            AndroidMatchResultPositionCropPreviewImage(
                                                Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
                                            ),
                                        ),
                                    ),
                                ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = MatchOcrReviewUiState.Empty(
                        tournamentId = "tournament-id",
                        matchId = "match-id",
                        matchResultOcrPreview = preview,
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_POSITION_CROPS_LOWER_PAGER_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(
            MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "11",
        )[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactRow(11))[0]
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_POSITION_CROPS_LOWER_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag(
            MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "12",
        )[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactRow(12))[0]
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactRow(11))
            .assertIsNotDisplayed()
    }

    @Test
    fun resultPositionPagerDoesNotSubstituteAnotherPreviewRowWhenPositionIsMissing() {
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            rows = listOf(previewRow(2, MatchResultScreenshotRole.MATCH_RESULT_UPPER)),
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                        resultPositionCropPreviews = mapOf(
                            MatchResultScreenshotRole.MATCH_RESULT_UPPER to
                                MatchResultPositionCropPreviewState.Available(
                                    listOf(
                                        MatchResultPositionCropPreview(
                                            1,
                                            AndroidMatchResultPositionCropPreviewImage(
                                                Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
                                            ),
                                        ),
                                        MatchResultPositionCropPreview(
                                            2,
                                            AndroidMatchResultPositionCropPreviewImage(
                                                Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
                                            ),
                                        ),
                                    ),
                                ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = MatchOcrReviewUiState.Empty(
                        tournamentId = "tournament-id",
                        matchId = "match-id",
                        matchResultOcrPreview = preview,
                    ),
                )
            }
        }

        composeTestRule.onAllNodesWithTag(
            MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "1",
        )[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactRow(2))
            .assertCountEquals(0)

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_POSITION_CROPS_UPPER_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag(
            MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "2",
        )[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactRow(2))[0]
            .assertIsDisplayed()
    }

    @Test
    fun resultPositionPagerNavigatesContinuouslyAcrossUpperLowerBoundaryInBothDirections() {
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(
                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            ),
            rows = (1..12).map { position ->
                previewRow(
                    position = position,
                    role = if (position <= 10) {
                        MatchResultScreenshotRole.MATCH_RESULT_UPPER
                    } else {
                        MatchResultScreenshotRole.MATCH_RESULT_LOWER
                    },
                )
            },
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )
        val upperImage = AndroidMatchResultPositionCropPreviewImage(
            Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
        )
        val lowerImage = AndroidMatchResultPositionCropPreviewImage(
            Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER, hasLinkedAsset = true),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER, hasLinkedAsset = true),
                        ),
                        resultPositionCropPreviews = mapOf(
                            MatchResultScreenshotRole.MATCH_RESULT_UPPER to
                                MatchResultPositionCropPreviewState.Available(
                                    listOf(
                                        *(1..11).map { position ->
                                            MatchResultPositionCropPreview(position, upperImage)
                                        }.toTypedArray(),
                                    ),
                                ),
                            MatchResultScreenshotRole.MATCH_RESULT_LOWER to
                                MatchResultPositionCropPreviewState.Available(
                                    listOf(
                                        MatchResultPositionCropPreview(11, lowerImage),
                                        MatchResultPositionCropPreview(12, lowerImage),
                                    ),
                                ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = MatchOcrReviewUiState.Empty(
                        tournamentId = "tournament-id",
                        matchId = "match-id",
                        matchResultOcrPreview = preview,
                    ),
                )
            }
        }

        val pager = composeTestRule.onNodeWithTag(
            MATCH_REVIEW_RESULT_POSITION_CROPS_UPPER_PAGER_TEST_TAG,
        )
        repeat(8) {
            pager.performTouchInput { swipeLeft() }
            composeTestRule.waitForIdle()
        }
        composeTestRule.onAllNodesWithTag(
            MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "9",
        )[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactRow(9))[0]
            .assertIsDisplayed()

        pager.performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(
            MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "10",
        )[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactRow(10))[0]
            .assertIsDisplayed()

        pager.performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(
            MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "11",
        )[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactRow(11))[0]
            .assertIsDisplayed()

        pager.performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(
            MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "10",
        )[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactRow(10))[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "1")
            .assertCountEquals(0)
    }

  @Test
fun legacyReviewKeepsResultSourceWhenDisplayableOcrDataExists() {
    val upperPreviewUri = createReadableResultPreviewUri("legacy-upper")
    val preview = MatchResultOcrPreviewUiState.Ready(
        roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
        rows = listOf(previewRow(1, MatchResultScreenshotRole.MATCH_RESULT_UPPER)),
        ignoredLowerRows = emptyList(),
        manualReviewRows = emptyList(),
    )

    composeTestRule.setContent {
        RankForgeTheme {
            MatchReviewScreen(
                uiState = availableState(
                    resultScreenshots = listOf(
                        resultSlot(
                            MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                            hasLinkedAsset = true,
                            localPreviewUri = upperPreviewUri,
                            originalWidth = 1920,
                            originalHeight = 1080,
                            confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                            cropProfileId = "match-result",
                        ),
                        resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                    ),
                ),
                onEnterPlacements = {},
                onEnterKills = {},
                onBackToDetails = {},
                showLegacyManualReviewContent = true,
                showInlineOcrDetails = true,
                ocrUiState = MatchOcrReviewUiState.Empty(
                    tournamentId = "tournament-id",
                    matchId = "match-id",
                    matchResultOcrPreview = preview,
                ),
            )
        }
    }

    awaitResultScreenshotPreview(1)
}

    @Test
    fun readyCachedOcrAvailabilityKeepsReviewStructureWithoutShowingInformationalText() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrCacheAvailability = MatchOcrCacheAvailability.READY,
                    ocrUiState = inlineOcrState(),
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_OCR_READY_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText("OCR data ready")
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun staleCachedOcrAvailabilityRemainsVisible() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrCacheAvailability = MatchOcrCacheAvailability.STALE_OR_INCOMPLETE,
                    ocrUiState = inlineOcrState(),
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_STALE_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("OCR data needs refresh")
            .assertIsDisplayed()
    }

    @Test
    fun staleCacheWithDisplayableResultOcrAllowsInlineResultDetails() {
        assertTrue(
            shouldShowInlineOcrDetailsForCache(
                cacheAvailability = MatchOcrCacheAvailability.STALE_OR_INCOMPLETE,
                ocrUiState = inlineOcrState(),
            ),
        )
    }

    @Test
    fun inlineOcrCorrectionControlsKeepCallbacksConnected() {
        val placements = mutableListOf<Pair<Int, String>>()
        val kills = mutableListOf<Pair<Int, String>>()
        val teamSlots = mutableListOf<Pair<Int, String>>()
        var finalizeCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrState(),
                    onOcrPlacementChanged = { row, value -> placements += row to value },
                    onOcrKillsChanged = { row, value -> kills += row to value },
                    onOcrAssignedTeamSlotChanged = { row, value -> teamSlots += row to value },
                    onOcrFinalize = { finalizeCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.placementInput(0))
            .performScrollTo()
            .performTextInput("9")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.killsInput(0))
            .performScrollTo()
            .performTextInput("8")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotInput(0))
            .performScrollTo()
            .performTextInput("7")
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .assertIsEnabled()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(placements.any { it.first == 0 })
            assertTrue(kills.any { it.first == 0 })
            assertTrue(teamSlots.any { it.first == 0 })
            assertEquals(1, finalizeCount)
        }
    }

    @Test
    fun resultPositionPagerKeepsTeamSlotControlsMappedToTheirCorrectionRows() {
        val ocrState = embeddedManualOcrState()

        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                        resultPositionCropPreviews = mapOf(
                            MatchResultScreenshotRole.MATCH_RESULT_UPPER to
                                MatchResultPositionCropPreviewState.Available(
                                    listOf(
                                        MatchResultPositionCropPreview(
                                            1,
                                            AndroidMatchResultPositionCropPreviewImage(
                                                Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
                                            ),
                                        ),
                                        MatchResultPositionCropPreview(
                                            2,
                                            AndroidMatchResultPositionCropPreviewImage(
                                                Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
                                            ),
                                        ),
                                    ),
                                ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    showInlineOcrDetails = true,
                    ocrUiState = ocrState,
                )
            }
        }

        composeTestRule.onAllNodesWithText("Remaining Team Slots").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Slots: 11, 12").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.teamSlotOption(0, 11))[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.teamSlotOption(0, 12))[0]
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_POSITION_CROPS_UPPER_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.teamSlotOption(1, 11))[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.teamSlotOption(1, 12))[0]
            .assertIsDisplayed()
    }

    @Test
    fun embeddedOcrPagerReusesTeamSlotAssistantForOptionsCallbacksAndRecomputation() {
        var ocrState by mutableStateOf(embeddedManualOcrState())
        val teamSlots = mutableListOf<Pair<Int, String>>()

        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    showInlineOcrDetails = true,
                    ocrUiState = ocrState,
                    onOcrAssignedTeamSlotChanged = { rowIndex, value ->
                        teamSlots += rowIndex to value
                        val draft = ocrState.correctionDraft ?: error("Expected correction draft")
                        val updatedDraft = MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(
                            draft = draft,
                            rowIndex = rowIndex,
                            value = value,
                        )
                        ocrState = ocrState.copy(
                            correctionDraft = updatedDraft,
                            blockerCount = updatedDraft.blockerCount,
                            warningCount = updatedDraft.warningCount,
                        )
                    },
                )
            }
        }

        composeTestRule.onAllNodesWithText("Remaining Team Slots").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Slots: 11, 12").assertCountEquals(0)
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotInput(0))
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetText) {
                it(AnnotatedString("11"))
            }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertEquals(listOf(0 to "11"), teamSlots)
            assertEquals("11", ocrState.correctionDraft?.rows?.first()?.assignedTeamSlotDraftValue)
        }
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.teamSlotOption(0, 11))
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotOption(0, 12))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotInput(0))
            .performSemanticsAction(SemanticsActions.SetText) {
                it(AnnotatedString("12"))
            }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertEquals("12", ocrState.correctionDraft?.rows?.first()?.assignedTeamSlotDraftValue)
        }
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotOption(0, 11))
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.teamSlotOption(0, 12))
            .assertCountEquals(0)

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotInput(0))
            .performSemanticsAction(SemanticsActions.SetText) {
                it(AnnotatedString("13"))
            }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotOption(0, 11))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotOption(0, 12))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotInput(0))
            .performSemanticsAction(SemanticsActions.SetText) {
                it(AnnotatedString(""))
            }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertEquals("", ocrState.correctionDraft?.rows?.first()?.assignedTeamSlotDraftValue)
        }
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotOption(0, 11))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotOption(0, 12))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotOption(0, 11))
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertEquals(
                listOf(0 to "11", 0 to "12", 0 to "13", 0 to "", 0 to "11"),
                teamSlots,
            )
            assertEquals("11", ocrState.correctionDraft?.rows?.first()?.assignedTeamSlotDraftValue)
        }
        composeTestRule.onAllNodesWithText("Slots: 12").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.teamSlotOption(0, 11))
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotOption(0, 12))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_ROWS_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.teamSlotOption(1, 11))
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotOption(1, 12))
            .assertIsDisplayed()

        composeTestRule.runOnIdle {
            ocrState = ocrState.copy(
                finalization = ocrState.finalization.copy(isFinalized = true),
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.REMAINING_TEAM_SLOTS)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.teamSlotOption(1, 12))
            .assertCountEquals(0)
    }

    @Test
    fun simplifiedInlineFinalizeActionSitsAfterResultRows() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrState(),
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .assertCountEquals(1)
        val resultRows = composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_ROWS_PAGER_TEST_TAG)
        val finalizeAction = composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)

        resultRows.performScrollTo()
        finalizeAction.performScrollTo().assertIsDisplayed()

        val resultRowsBounds = resultRows.fetchSemanticsNode().boundsInRoot
        val finalizeBounds = finalizeAction.fetchSemanticsNode().boundsInRoot
        assertTrue(finalizeBounds.top >= resultRowsBounds.bottom)
    }

    @Test
    fun simplifiedInlineFinalizeShowsOneWarningDialogWithCombinedPositionPreviews() {
        var finalizeCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = selectedResultScreenshotSlots(),
                        resultPositionCropPreviews = combinedPositionCropPreviewStates(),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    showInlineOcrDetails = true,
                    ocrUiState = warningOcrState(),
                    onOcrFinalize = { finalizeCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle { assertEquals(1, finalizeCount) }
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.FINALIZE_WARNING_DIALOG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.CONFIRM_FINALIZE_WARNINGS)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.DISMISS_FINALIZE_WARNINGS)
            .assertCountEquals(0)
    }

    @Test
    fun simplifiedInlineFinalizeWarningConfirmInvokesCallbackExactlyOnce() {
        var finalizeCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = selectedResultScreenshotSlots(),
                        resultPositionCropPreviews = combinedPositionCropPreviewStates(),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    showInlineOcrDetails = true,
                    ocrUiState = warningOcrState().copy(
                        finalization = MatchOcrReviewFinalizationUiState(
                            showWarningConfirmation = true,
                        ),
                    ),
                    onOcrFinalize = { finalizeCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .performScrollTo()
            .performClick()
        composeTestRule.runOnIdle { assertEquals(1, finalizeCount) }
    }

    @Test
    fun simplifiedInlineFinalizeWarningDismissInvokesCallbackExactlyOnce() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = selectedResultScreenshotSlots(),
                        resultPositionCropPreviews = combinedPositionCropPreviewStates(),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    showInlineOcrDetails = true,
                    ocrUiState = warningOcrState().copy(
                        finalization = MatchOcrReviewFinalizationUiState(
                            showWarningConfirmation = true,
                        ),
                    ),
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.FINALIZE_WARNING_DIALOG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.CONFIRM_FINALIZE_WARNINGS)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.DISMISS_FINALIZE_WARNINGS)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun simplifiedInlineFinalizeWithoutWarningsRemainsEnabledWithCombinedPositionPreviews() {
        var finalizeCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = selectedResultScreenshotSlots(),
                        resultPositionCropPreviews = combinedPositionCropPreviewStates(),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrState(),
                    onOcrFinalize = { finalizeCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule.runOnIdle { assertEquals(1, finalizeCount) }
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.FINALIZE_WARNING_DIALOG)
            .assertCountEquals(0)
    }

    @Test
    fun simplifiedInlineFinalizeErrorRemainsVisibleWithCombinedPositionPreviews() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = selectedResultScreenshotSlots(),
                        resultPositionCropPreviews = combinedPositionCropPreviewStates(),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrState().copy(
                        finalization = MatchOcrReviewFinalizationUiState(
                            error = MatchOcrReviewFinalizationError.FINALIZATION_FAILED,
                        ),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZATION_ERROR)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun simplifiedReviewCalculateActionInvokesCalculateCallbackWhenEligible() {
        var ocrReviewCount = 0
        var calculateCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                        ),
                    ),
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onOpenOcrReview = { ocrReviewCount++ },
                    onCalculatePoints = { calculateCount++ },
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performScrollTo()
            .performClick()
        composeTestRule.runOnIdle {
            assertEquals(0, ocrReviewCount)
            assertEquals(1, calculateCount)
        }
    }

    @Test
    fun simplifiedReviewHidesCalculateActionWhenReadyOcrRowsExist() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrState(),
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun simplifiedReviewHidesCalculateActionWhenOnlyReadyResultPreviewRowsExist() {
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            rows = listOf(previewRow(1, MatchResultScreenshotRole.MATCH_RESULT_UPPER)),
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = MatchOcrReviewUiState.Empty(
                        tournamentId = "tournament-id",
                        matchId = "match-id",
                        matchResultOcrPreview = preview,
                    ),
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun simplifiedReviewHidesCalculateActionForRestoredDisplayableOcrState() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrState().copy(
                        evidenceSource = MatchOcrReviewEvidenceSource.RESTORED_CALCULATED,
                    ),
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun legacyReviewKeepsCalculateActionWhenDisplayableOcrDataExists() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrState(),
                    showLegacyManualReviewContent = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun finalizedReviewHidesCalculateAction() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(status = MatchStatus.FINALIZED),
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun inlineReadyPreviewUsesExistingCompactOcrReviewPresentation() {
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(
                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            ),
            rows = listOf(
                MatchResultOcrPreviewRowUiState(
                    position = 1,
                    role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    sourceLabel = "UPPER_TEMPLATE",
                    placementText = "1",
                    slots = (1..4).map { slot ->
                        MatchResultOcrPreviewSlotUiState(
                            slot = slot,
                            playerText = "Player $slot",
                            playerOcrText = "Player $slot",
                            playerStatusLabel = "DIRECT_NUMERIC",
                            killText = slot.toString(),
                            killOcrText = slot.toString(),
                            killStatusLabel = "DIRECT_NUMERIC",
                        )
                    },
                ),
            ),
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )

        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = MatchOcrReviewUiState.Empty(
                        tournamentId = "tournament-id",
                        matchId = "match-id",
                        matchResultOcrPreview = preview,
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.COMPACT_LIST)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactRow(1))
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Roles: MATCH_RESULT_UPPER")
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText("DIRECT_NUMERIC")
            .assertCountEquals(0)
    }

    @Test
    fun addTeamRendersNextManualPositionWithoutAddingACrop() {
        composeTestRule.setContent {
            var currentOcrState by remember { mutableStateOf(automaticOcrState(1..6)) }
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = positionCropTestUiState((1..6).toList()),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = currentOcrState,
                    onCompactAddTeam = {
                        val ready = currentOcrState as MatchOcrReviewUiState.Ready
                        currentOcrState = ready.nextManualResultPositionOrNull()
                            ?.let { position ->
                                ready.copy(
                                    manuallyRevealedPositions =
                                        ready.manuallyRevealedPositions + position,
                                )
                            }
                            ?: ready
                    },
                )
            }
        }

        composeTestRule
            .onAllNodesWithTag(MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "1")[0]
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithTag(MatchOcrReviewTestTags.compactMenu(1))[0]
            .performClick()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactAddTeam(1)).performClick()

        composeTestRule
            .onAllNodesWithTag(MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "7")
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactPlacement(7))[0]
            .assertIsDisplayed()
            .assertTextContains("Position - 7")
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactTeam(7))[0]
            .assertTextContains("Team name - Not matched")
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.placementInput(6))[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.killsInput(6))[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.teamSlotInput(6))[0]
            .assertIsDisplayed()
    }

    @Test
    fun inlinePosition11WithFourNotDetectedPlayersShowsDelete() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrStateForPosition11(),
                    onExcludeOcrRow = {},
                )
            }
        }

        (1..4).forEach { slot ->
            composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayer(11, slot))
                .assertTextContains("$slot. Not detected", substring = true)
        }
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactMenu(11))[0]
            .performClick()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.deleteRow(10))
            .assertIsDisplayed()
    }

    @Test
    fun inlineDeleteUsesStructuralRowIndex() {
        var capturedRowIndex: Int? = null
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrStateForPosition11(),
                    onExcludeOcrRow = { capturedRowIndex = it },
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactMenu(11))[0]
            .performClick()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.deleteRow(10))
            .performClick()
        composeTestRule.runOnIdle {
            assertEquals(10, capturedRowIndex)
        }
    }

    @Test
    fun inlineOneDetectedPlayerHidesDelete() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrStateForPosition11(
                        playersBySlot = mapOf(3 to "DetectedPlayer"),
                    ),
                    onExcludeOcrRow = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayer(11, 3))
            .performScrollTo()
            .assertTextContains("3. DetectedPlayer", substring = true)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.deleteRow(10))
            .assertCountEquals(0)
    }

    @Test
    fun inlineAllNotDetectedRowKeepsDeleteAndResetAvailable() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrStateForPosition11(),
                    onExcludeOcrRow = {},
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.compactMenu(11))[0]
            .performClick()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.deleteRow(10))
            .assertIsDisplayed()
    }

    @Test
    fun inlineExcludedRowIsNotRendered() {
        val state = inlineOcrStateForPosition11()
        val correctionDraft = state.correctionDraft ?: error("Expected correction draft")
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = state.copy(
                        correctionDraft = correctionDraft.copy(
                            rows = listOf(correctionDraft.rows.single().copy(isExcluded = true)),
                        ),
                    ),
                )
            }
        }

        composeTestRule.onAllNodesWithText("Position - 11").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.row(10))
            .assertCountEquals(0)
    }

    @Test
    fun inlineExcludedRowKeepsNeighborStructuralPositionAndPreviewAlignment() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showInlineOcrDetails = true,
                    ocrUiState = inlineOcrStateWithExcludedPosition11AndPosition12(),
                )
            }
        }

        composeTestRule.onNodeWithText("Position - 11").assertDoesNotExist()
        composeTestRule.onNodeWithText("Position - 12").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.row(11))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayer(12, 1))
            .assertTextContains("1. Position 12 Player", substring = true)
    }

    @Test
    fun explicitlyExcludedResultRowHidesOnlyItsMatchingPositionCrop() {
        setPositionCropVisibilityContent(
            ocrUiState = ocrStateWithCorrectionRows(excludedRowIndexes = setOf(8)),
            positions = listOf(8, 9, 10),
        )

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "8")
            .assertCountEquals(2)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "9")
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_POSITION_CROPS_UPPER_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "10")
            .assertCountEquals(2)
    }

    @Test
    fun resettingExplicitlyExcludedResultRowRestoresItsExistingPositionCrop() {
        var ocrUiState by mutableStateOf(
            ocrStateWithCorrectionRows(excludedRowIndexes = setOf(8)),
        )
        val positionCropUiState = positionCropTestUiState(listOf(9))
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = positionCropUiState,
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    ocrUiState = ocrUiState,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "9")
            .assertCountEquals(0)

        composeTestRule.runOnIdle {
            ocrUiState = ocrStateWithCorrectionRows()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "9")
            .assertCountEquals(2)
    }

    @Test
    fun implicitAbsentResultRowDoesNotHideItsPositionCrop() {
        setPositionCropVisibilityContent(
            ocrUiState = ocrStateWithCorrectionRows(implicitlyAbsentRowIndexes = setOf(8)),
            positions = listOf(9),
        )

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "9")
            .assertCountEquals(2)
    }

    @Test
    fun multipleExplicitlyExcludedResultRowsHideOnlyTheirMatchingPositionCrops() {
        setPositionCropVisibilityContent(
            ocrUiState = ocrStateWithCorrectionRows(excludedRowIndexes = setOf(8, 10)),
            positions = listOf(8, 9, 10, 11, 12),
        )

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "8")
            .assertCountEquals(2)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "9")
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "11")
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_POSITION_CROPS_UPPER_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "10")
            .assertCountEquals(2)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_POSITION_CROPS_UPPER_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "12")
            .assertCountEquals(2)
    }

    @Test
    fun inlineOcrDetailsRemainVisibleAfterMatchFinalization() {
        var calculatedCount = 0
        var matchState by mutableStateOf(
            availableState(
                resultScreenshots = listOf(
                    resultSlot(
                        role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                        hasLinkedAsset = true,
                        confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                        cropProfileId = "match-result",
                    ),
                    resultSlot(
                        role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                        hasLinkedAsset = true,
                        confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                        cropProfileId = "match-result",
                    ),
                ),
            ).copy(
                finalizedParticipantSlotNumbers = setOf(1),
            ),
        )
        var ocrState by mutableStateOf(inlineOcrState())

        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = matchState,
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onCalculatePoints = { calculatedCount++ },
                    ocrUiState = ocrState,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(1, calculatedCount) }

        composeTestRule.runOnIdle {
            matchState = matchState.copy(status = MatchStatus.FINALIZED)
            ocrState = ocrState.copy(
                finalization = MatchOcrReviewFinalizationUiState(isFinalized = true),
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_ROWS_PAGER_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.row(6))
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .assertCountEquals(0)
    }

    @Test
    fun simplifiedReviewOcrActionIsDisabledWhenOnlyUpperIsReady() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .assertIsEnabled()
    }

    @Test
    fun simplifiedReviewOcrActionIsDisabledWhenOnlyLowerIsReady() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .assertIsEnabled()
    }

    @Test
    fun simplifiedReviewOcrActionIsEnabledWhenBothRolesAreReady() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .assertIsEnabled()
    }

    @Test
    fun emptyResultUsesSequentialSelectorForUpperRoleCallback() {
        val selectedRoles = mutableListOf<MatchResultScreenshotRole>()
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    onSelectResultScreenshot = { selectedRoles += it },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER), selectedRoles)
        }
    }

    @Test
    fun sequentialResultSelectorTargetsLowerAfterUpperIsSelected() {
        val selectedRoles = mutableListOf<MatchResultScreenshotRole>()
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER, hasLinkedAsset = true),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onSelectResultScreenshot = { selectedRoles += it },
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(MatchResultScreenshotRole.MATCH_RESULT_LOWER), selectedRoles)
        }
    }

    @Test
    fun bothSelectedResultScreenshotsHideSequentialSelectorAndKeepPager() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER, hasLinkedAsset = true),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER, hasLinkedAsset = true),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun lowerOnlyResultSelectionReturnsSequentialTargetToUpper() {
        val selectedRoles = mutableListOf<MatchResultScreenshotRole>()
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER, hasLinkedAsset = true),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onSelectResultScreenshot = { selectedRoles += it },
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(MatchResultScreenshotRole.MATCH_RESULT_UPPER), selectedRoles)
        }
    }

    @Test
    fun missingLocalUpperResultRemainsAllocatedForSequentialSelection() {
        val selectedRoles = mutableListOf<MatchResultScreenshotRole>()
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                isLocalFileMissing = true,
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onSelectResultScreenshot = { selectedRoles += it },
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(MatchResultScreenshotRole.MATCH_RESULT_LOWER), selectedRoles)
        }
    }

    @Test
    fun busyNextResultSlotRemainsDisplayedAndDisabled() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER, hasLinkedAsset = true),
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                isDuplicateDetectionInProgress = true,
                            ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun finalizedResultSelectionIsDisplayedButDisabled() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(status = MatchStatus.FINALIZED),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun legacyReviewContainsOnlyOneOcrAction() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = true,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .assertCountEquals(1)
    }

    @Test
    fun linkedResultSlotsShowLocalPreviewsAndUnselectedSlotsDoNot() {
        val upperPreviewUri = createReadableResultPreviewUri("linked-upper")
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                localPreviewUri = upperPreviewUri,
                                originalWidth = 1920,
                                originalHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        awaitResultScreenshotPreview(1)
        composeTestRule.onNodeWithTag(
            MATCH_REVIEW_RESULT_SCREENSHOT_1_PREVIEW_TEST_TAG,
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_PREVIEW_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun resultPositionRectanglesRenderWithoutCropOrRowLabels() {
        val upperPreviewUri = createReadableResultPreviewUri("position-upper")
        val lowerPreviewUri = createReadableResultPreviewUri("position-lower")
        val upperImage = AndroidMatchResultPositionCropPreviewImage(
            Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
        )
        val lowerImage = AndroidMatchResultPositionCropPreviewImage(
            Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                localPreviewUri = upperPreviewUri,
                                originalWidth = 1920,
                                originalHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                localPreviewUri = lowerPreviewUri,
                                originalWidth = 1920,
                                originalHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                        ),
                        resultPositionCropPreviews = mapOf(
                            MatchResultScreenshotRole.MATCH_RESULT_UPPER to
                                MatchResultPositionCropPreviewState.Available(
                                    (1..10).map { position ->
                                        MatchResultPositionCropPreview(position, upperImage)
                                    },
                                ),
                            MatchResultScreenshotRole.MATCH_RESULT_LOWER to
                                MatchResultPositionCropPreviewState.Available(
                                    (11..12).map { position ->
                                        MatchResultPositionCropPreview(position, lowerImage)
                                    },
                                ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        expandResultScreenshotActions(1)
        composeTestRule.onNodeWithTag(
            MATCH_REVIEW_RESULT_SCREENSHOT_1_PREVIEW_TEST_TAG,
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_CROP_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REMOVE_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_POSITION_CROPS_UPPER_PAGER_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(
            MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "1",
        )[0]
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Position crops").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Position 1").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Row 1").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Row 2").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("match_review_result_position_row_crops_1")
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("match_review_result_position_row_crop_1_1")
            .assertCountEquals(0)
        repeat(5) {
            composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_POSITION_CROPS_UPPER_PAGER_TEST_TAG)
                .performTouchInput { swipeLeft() }
            composeTestRule.waitForIdle()
        }
        composeTestRule.onAllNodesWithTag(
            MATCH_REVIEW_RESULT_POSITION_CROP_TEST_TAG_PREFIX + "6",
        )[0]
            .assertIsDisplayed()
    }

    @Test
    fun linkedResultSlotWithoutConfirmedCropDoesNotShowPreview() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                localPreviewUri = "file:///private/result-1.png",
                                originalWidth = 1920,
                                originalHeight = 1080,
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_PREVIEW_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun resultSelectorUsesStaticRoleBoundActionsForActiveSlots() {
        val upperPreviewUri = createReadableResultPreviewUri("actions-upper")
        val lowerPreviewUri = createReadableResultPreviewUri("actions-lower")
        val selectedRoles = mutableListOf<MatchResultScreenshotRole>()
        val cropRoles = mutableListOf<MatchResultScreenshotRole>()
        val removedRoles = mutableListOf<MatchResultScreenshotRole>()
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                localPreviewUri = upperPreviewUri,
                                originalWidth = 1920,
                                originalHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.0, 0.0, 1.0, 0.5),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                localPreviewUri = lowerPreviewUri,
                                originalWidth = 1920,
                                originalHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.0, 0.0, 0.5, 1.0),
                                cropProfileId = "match-result",
                            ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    onSelectResultScreenshot = { selectedRoles += it },
                    onOpenResultScreenshotCrop = { cropRoles += it },
                    onRemoveResultScreenshot = { removedRoles += it },
                )
            }
        }

        expandResultScreenshotActions(1)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_CROP_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REMOVE_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_CROP_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REMOVE_TEST_TAG).performClick()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        expandResultScreenshotActions(2)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_REPLACE_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_CROP_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_REMOVE_TEST_TAG).performClick()
        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                ),
                selectedRoles,
            )
            assertEquals(
                listOf(
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                ),
                cropRoles,
            )
            assertEquals(
                listOf(
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                ),
                removedRoles,
            )
        }
    }

    @Test
    fun selectedResultRolesSwipeThroughPagerInCanonicalOrder() {
        val upperPreviewUri = createReadableResultPreviewUri("swipe-upper")
        val lowerPreviewUri = createReadableResultPreviewUri("swipe-lower")
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                localPreviewUri = upperPreviewUri,
                                originalWidth = 1920,
                                originalHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                localPreviewUri = lowerPreviewUri,
                                originalWidth = 1920,
                                originalHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        awaitResultScreenshotPreview(1)
        composeTestRule
            .onNodeWithTag(
                MATCH_REVIEW_RESULT_SCREENSHOT_1_PREVIEW_TEST_TAG,
                useUnmergedTree = true,
            )
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        awaitResultScreenshotPreview(2)
        composeTestRule
            .onNodeWithTag(
                MATCH_REVIEW_RESULT_SCREENSHOT_2_PREVIEW_TEST_TAG,
                useUnmergedTree = true,
            )
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_REPLACE_TEST_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun removingLastSelectedResultClearsPagerAndActions() {
        composeTestRule.setContent {
            var state by remember {
                mutableStateOf(
                    availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                )
            }
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = state,
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    onRemoveResultScreenshot = { role ->
                        state = state.copy(
                            resultScreenshots = state.resultScreenshots.map { slot ->
                                if (slot.role == role) {
                                    slot.copy(hasLinkedAsset = false, selectedScreenshotUri = null)
                                } else {
                                    slot
                                }
                            },
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REMOVE_TEST_TAG)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REMOVE_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun removingLowerResultRevealsLowerSequentialTarget() {
        composeTestRule.setContent {
            var state by remember {
                mutableStateOf(
                    availableState(
                        resultScreenshots = listOf(
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER, hasLinkedAsset = true),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER, hasLinkedAsset = true),
                        ),
                    ),
                )
            }
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = state,
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    onRemoveResultScreenshot = { role ->
                        state = state.copy(
                            resultScreenshots = state.resultScreenshots.map { slot ->
                                if (slot.role == role) {
                                    slot.copy(hasLinkedAsset = false, selectedScreenshotUri = null)
                                } else {
                                    slot
                                }
                            },
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_REMOVE_TEST_TAG)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun lowerOnlySelectedResultMapsPagerPageToLowerRole() {
        val lowerPreviewUri = createReadableResultPreviewUri("lower-only")
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                localPreviewUri = lowerPreviewUri,
                                originalWidth = 1920,
                                originalHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        awaitResultScreenshotPreview(2)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(
            MATCH_REVIEW_RESULT_SCREENSHOT_2_PREVIEW_TEST_TAG,
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
    }

    @Test
    fun resultSelectorActionStateFollowsBusyMissingActiveSlot() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                            ),
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                isLocalFileMissing = true,
                                isUploadInProgress = true,
                            ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_REPLACE_TEST_TAG)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_CROP_TEST_TAG)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_REMOVE_TEST_TAG)
            .assertIsNotEnabled()
    }

    @Test
    fun resultPreviewViewportRemainsStableAcrossCropAspectRatios() {
        val upperPreviewUri = createReadableResultPreviewUri("viewport-upper")
        val lowerPreviewUri = createReadableResultPreviewUri("viewport-lower")
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                localPreviewUri = upperPreviewUri,
                                originalWidth = 1920,
                                originalHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.0, 0.0, 1.0, 0.5),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                localPreviewUri = lowerPreviewUri,
                                originalWidth = 1920,
                                originalHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.0, 0.0, 0.5, 1.0),
                                cropProfileId = "match-result",
                            ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        awaitResultScreenshotPreview(1)
        val initialHeight = composeTestRule
            .onNodeWithTag(
                MATCH_REVIEW_RESULT_SCREENSHOT_1_VIEWPORT_TEST_TAG,
                useUnmergedTree = true,
            )
            .fetchSemanticsNode().size.height
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        awaitResultScreenshotPreview(2)
        val secondHeight = composeTestRule
            .onNodeWithTag(
                MATCH_REVIEW_RESULT_SCREENSHOT_2_VIEWPORT_TEST_TAG,
                useUnmergedTree = true,
            )
            .fetchSemanticsNode().size.height
        assertEquals(initialHeight, secondHeight)
    }

    @Test
    fun linkedResultScreenshotTwoShowsItsConfirmedCropPreview() {
        val lowerPreviewUri = createReadableResultPreviewUri("linked-lower")
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                localPreviewUri = lowerPreviewUri,
                                originalWidth = 1920,
                                originalHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        awaitResultScreenshotPreview(2)
        composeTestRule.onNodeWithTag(
            MATCH_REVIEW_RESULT_SCREENSHOT_2_PREVIEW_TEST_TAG,
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
    }

    @Test
    fun missingLocalResultFileDoesNotShowPreview() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        resultScreenshots = listOf(
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                localPreviewUri = "file:///private/result-2.png",
                                isLocalFileMissing = true,
                                originalWidth = 1920,
                                originalHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_PREVIEW_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun reviewScreenShowsAllRowsRestoredValuesAndValidStatus() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_ROW_TEST_TAG_PREFIX + "1").assertCountEquals(1)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_VALID_STATUS_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Players: Player One").assertIsDisplayed()
        composeTestRule.onNodeWithText("Placement: 7").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kills: 3").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_ROW_TEST_TAG_PREFIX + "12")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun reviewScreenShowsOverallAndRowValidationIssues() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        validationErrors = mapOf(
                            1 to setOf(MatchResultValidationError.MISSING_PLACEMENT),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_ISSUES_STATUS_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Issue: Placement is missing.").assertIsDisplayed()
    }

    @Test
    fun reviewActionsInvokePlacementKillAndDetailsCallbacks() {
        var placementCount = 0
        var killCount = 0
        var detailsCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = { placementCount++ },
                    onEnterKills = { killCount++ },
                    onBackToDetails = { detailsCount++ },
                    showLegacyManualReviewContent = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_DETAILS_ACTION_TEST_TAG).performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, placementCount)
            assertEquals(1, killCount)
            assertEquals(1, detailsCount)
        }
    }

    @Test
    fun photoPickerActionIsAvailableAndSelectedStateIsVisible() {
        var photoPickerActionCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        resultScreenshots = listOf(
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                selectedScreenshotUri = "content://picker/selected",
                                isSelectedScreenshotValidated = true,
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    onSelectResultScreenshot = {
                        if (it == MatchResultScreenshotRole.MATCH_RESULT_UPPER) photoPickerActionCount++
                    },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_SELECT_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_SECTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Screenshot selected and validated.")
            .assertCountEquals(0)
        composeTestRule.runOnIdle { assertEquals(1, photoPickerActionCount) }
    }

    @Test
    fun photoPickerActionIsDisabledWhileARequestIsActive() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        resultScreenshots = listOf(
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                selectedScreenshotUri = "content://picker/selected",
                                isPhotoPickerRequestActive = true,
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_SELECT_TEST_TAG)
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun invalidImageSelectionShowsValidationErrorWithoutValidatedConfirmation() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        resultScreenshots = listOf(
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                selectedScreenshotUri = "content://picker/unsupported",
                                imageValidationError = ImageValidationError.UNSUPPORTED_FORMAT,
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithText("Select a PNG, JPEG, or WebP image.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_SELECT_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun duplicateScreenshotStatesAreVisibleAndBlockConcurrentLinkActions() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        resultScreenshots = listOf(
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                selectedScreenshotUri = "content://picker/selected",
                                isSelectedScreenshotValidated = true,
                                isDuplicateDetectionInProgress = true,
                                duplicateInfo = ScreenshotDuplicateInfo.ALREADY_LINKED_TO_THIS_MATCH,
                                duplicateError = ScreenshotDuplicateError.LINKED_TO_OTHER_MATCH,
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithText("Checking the selected screenshot for duplicates.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("This screenshot is already linked to this match.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("This screenshot is already linked to another match in this tournament.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_SELECT_TEST_TAG)
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun localPreservationStatesAreVisibleAndBlockLinkActions() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        resultScreenshots = listOf(
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                selectedScreenshotUri = "content://picker/selected",
                                isSelectedScreenshotValidated = true,
                                hasLinkedAsset = true,
                                isPreservationInProgress = true,
                                preservationError = ScreenshotPreservationError.CLEANUP_FAILED,
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithText("Preserving screenshot locally.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Screenshot ready.").assertCountEquals(0)
        composeTestRule.onNodeWithText("The screenshot was preserved, but old local screenshot cleanup failed.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG)
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun linkedScreenshotShowsCropActionAndCropReadyState() {
        var cropCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        resultScreenshots = listOf(
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    onOpenResultScreenshotCrop = {
                        if (it == MatchResultScreenshotRole.MATCH_RESULT_UPPER) cropCount++
                    },
                )
            }
        }

        composeTestRule.onAllNodesWithText("Crop ready.").assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_CROP_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.runOnIdle { assertEquals(1, cropCount) }
    }

    @Test
    fun linkedResultScreenshotsExposeRoleSpecificRemoveActions() {
        var upperRemoveCount = 0
        var lowerRemoveCount = 0

        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        resultScreenshots = listOf(
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                            ),
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                            ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onRemoveResultScreenshot = { role ->
                        when (role) {
                            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> upperRemoveCount++
                            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> lowerRemoveCount++
                        }
                    },
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REMOVE_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_REMOVE_TEST_TAG)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, upperRemoveCount)
            assertEquals(1, lowerRemoveCount)
        }
    }
    @Test
    fun uploadFailureKeepsErrorVisibleWithoutRetryAction() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        resultScreenshots = listOf(
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                uploadError = ScreenshotUploadError.NETWORK,
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onAllNodesWithText("Screenshot ready.").assertCountEquals(0)
        composeTestRule.onNodeWithText("Crop required.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("The screenshot upload could not reach cloud storage. Try again.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_SCREENSHOT_UPLOAD_RETRY_ACTION_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onNodeWithText("Retry screenshot upload")
            .assertDoesNotExist()
    }

    @Test
    fun restoredUploadedAndMissingMetadataStatesAreVisible() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        resultScreenshots = listOf(
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                                isLocalFileMissing = true,
                                uploadStatus = ScreenshotMetadataUploadUiStatus.UPLOADED,
                                uploadError = ScreenshotUploadError.CLOUD_METADATA_WRITE_FAILED,
                                preservationError = ScreenshotPreservationError.LOCAL_FILE_MISSING,
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onAllNodesWithText("Screenshot ready.").assertCountEquals(0)
        composeTestRule.onNodeWithText("The preserved local screenshot file is missing.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("The screenshot uploaded, but cloud metadata could not be saved.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun finalizedMatchDoesNotExposeScreenshotLinkActions() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        status = MatchStatus.FINALIZED,
                        resultScreenshots = listOf(
                            resultSlot(
                                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                                hasLinkedAsset = true,
                            ),
                            resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_SELECT_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_CROP_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REMOVE_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_REMOVE_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun finalizeActionRequiresConfirmation() {
        var finalizeCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = true,
                    onFinalize = { finalizeCount++ },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("This will make the match read-only. You can still review its results.")
            .assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(0, finalizeCount) }
        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZE_CONFIRM_ACTION_TEST_TAG).performClick()
        composeTestRule.runOnIdle { assertEquals(1, finalizeCount) }
    }

    @Test
    fun finalizedReviewIsReadOnlyAndShowsFinalizedState() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(status = MatchStatus.FINALIZED),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Status: FINALIZED").assertIsDisplayed()
        composeTestRule.onNodeWithText("Finalized matches are read-only.").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_CORRECTION_ACTION_TEST_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun simplifiedFinalizedReviewShowsDownloadResultActionAndHidesLegacyControls() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        status = MatchStatus.FINALIZED,
                        finalizedParticipantSlotNumbers = (1..12).toSet(),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    ocrUiState = inlineOcrState().copy(
                        finalization = MatchOcrReviewFinalizationUiState(isFinalized = true),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OVERFLOW_ACTION_TEST_TAG).performClick()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_DOWNLOAD_RESULT_ACTION_TEST_TAG)[0]
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_CORRECTION_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .assertCountEquals(0)
    }

    @Test
    fun draftReviewDoesNotExposeDownloadResultAction() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_DOWNLOAD_RESULT_ACTION_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun downloadStatusesAreDeterministic() {
        val statuses = listOf(
            ResultDownloadUiState.Generating(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PNG),
            ResultDownloadUiState.Saving(ResultExportFileFormat.PNG),
            ResultDownloadUiState.Success(ResultExportFileFormat.PNG, false),
            ResultDownloadUiState.Failure(ResultDownloadFailure.SAVE_FAILED),
        )
        val expectedText = listOf(
            "Generating result.",
            "Saving result.",
            "PNG saved to Downloads/PointIQ.",
            "Unable to save result.",
        )

        var downloadState by mutableStateOf<ResultDownloadUiState>(statuses.first())
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        status = MatchStatus.FINALIZED,
                        finalizedParticipantSlotNumbers = (1..12).toSet(),
                        resultDownloadUiState = downloadState,
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                )
            }
        }

        statuses.forEachIndexed { index, status ->
            composeTestRule.runOnIdle {
                downloadState = status
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag(MATCH_REVIEW_DOWNLOAD_STATUS_TEST_TAG)
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithText(expectedText[index]).assertIsDisplayed()
        }
    }

    @Test
    fun downloadResultDialogsSelectScopeAndFormat() {
        var download: Pair<DownloadResultSelection, DownloadResultDesignType>? = null
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = {},
                    initialResult = DownloadResultSelection.Overall,
                    initialDesign = DownloadResultDesignType.IMAGE,
                    previewState = DownloadResultPreviewState.ResultImage(ByteArray(0)),
                    onDownload = { selection, design -> download = selection to design },
                )
            }
        }

        composeTestRule.onNodeWithTag(DOWNLOAD_RESULT_MATCH_OPTION_TEST_TAG_PREFIX + "1").performClick()
        composeTestRule.onNodeWithTag(DOWNLOAD_RESULT_DESIGN_IMAGE_OPTION_TEST_TAG)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText("Download").performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                DownloadResultSelection.Match("match-1") to DownloadResultDesignType.IMAGE,
                download,
            )
        }
    }

    @Test
    fun groupRotationWholeTournamentFormatDialogExposesOnlyPng() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        status = MatchStatus.FINALIZED,
                        finalizedParticipantSlotNumbers = (1..12).toSet(),
                        tournamentFormat = TournamentFormat.GROUP_ROTATION,
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OVERFLOW_ACTION_TEST_TAG).performClick()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_DOWNLOAD_RESULT_ACTION_TEST_TAG)[0]
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_DOWNLOAD_SCOPE_TOURNAMENT_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_DOWNLOAD_SCOPE_CONTINUE_TEST_TAG).performClick()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_DOWNLOAD_FORMAT_PNG_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_DOWNLOAD_FORMAT_PDF_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_DOWNLOAD_FORMAT_CUSTOM_DESIGN_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_DOWNLOAD_FORMAT_MY_CUSTOM_DESIGN_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun wholeTournamentAndPngSelectionCallsExpectedDownload() {
        var download: Pair<DownloadResultSelection, DownloadResultDesignType>? = null
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = {},
                    initialResult = DownloadResultSelection.Overall,
                    initialDesign = DownloadResultDesignType.IMAGE,
                    previewState = DownloadResultPreviewState.ResultImage(ByteArray(0)),
                    onDownload = { selection, design -> download = selection to design },
                )
            }
        }

        composeTestRule.onNodeWithTag(DOWNLOAD_RESULT_OVERALL_OPTION_TEST_TAG)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithTag(DOWNLOAD_RESULT_DESIGN_IMAGE_OPTION_TEST_TAG)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText("Download").performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                DownloadResultSelection.Overall to DownloadResultDesignType.IMAGE,
                download,
            )
        }
    }

    @Test
    fun customDesignSelectionOpensSetupForSelectedScope() {
        var importedSelection: DownloadResultSelection? = null
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = {},
                    initialResult = DownloadResultSelection.Overall,
                    initialDesign = DownloadResultDesignType.MY_DESIGN,
                    previewState = DownloadResultPreviewState.ImportYourDesign,
                    onImportYourDesign = { importedSelection = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(DOWNLOAD_RESULT_DESIGN_MY_OPTION_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Import Your Design").assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(DownloadResultSelection.Overall, importedSelection)
        }
    }

    @Test
    fun loadingCustomDesignShowsImportButNotMyCustomDesign() {
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = {},
                    initialResult = DownloadResultSelection.Overall,
                    initialDesign = DownloadResultDesignType.MY_DESIGN,
                    previewState = DownloadResultPreviewState.ImportYourDesign,
                    hasSavedCustomDesign = false,
                )
            }
        }

        composeTestRule.onNodeWithText("Import Your Design").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("My Custom Design").assertCountEquals(0)
    }

    @Test
    fun unavailableCustomDesignShowsImportButNotMyCustomDesign() {
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = {},
                    initialResult = DownloadResultSelection.Overall,
                    initialDesign = DownloadResultDesignType.MY_DESIGN,
                    previewState = DownloadResultPreviewState.ImportYourDesign,
                    hasSavedCustomDesign = false,
                )
            }
        }

        composeTestRule.onNodeWithText("Import Your Design").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("My Custom Design").assertCountEquals(0)
    }

    @Test
    fun customDesignAvailabilityRefreshToNoneHidesMyDesignAndKeepsImport() {
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = {},
                    initialResult = DownloadResultSelection.Overall,
                    initialDesign = DownloadResultDesignType.MY_DESIGN,
                    previewState = DownloadResultPreviewState.ImportYourDesign,
                    hasSavedCustomDesign = false,
                )
            }
        }

        composeTestRule.onNodeWithTag(DOWNLOAD_RESULT_DESIGN_MY_OPTION_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Import Your Design").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("My Custom Design").assertCountEquals(0)
    }

    @Test
    fun downloadScopeCancelDoesNotStartWork() {
        var backCount = 0
        var downloadCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = { backCount++ },
                    onDownload = { _, _ -> downloadCount++ },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DOWNLOAD_RESULT_SCREEN_TEST_TAG + "_back")
            .performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, backCount)
            assertEquals(0, downloadCount)
        }
    }

    @Test
    fun formatBackPreservesScopeAndRequiresFreshFormatSelection() {
        var lastDesignSelection: Pair<DownloadResultSelection, DownloadResultDesignType>? = null
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = {},
                    initialResult = DownloadResultSelection.Match("match-1"),
                    initialDesign = DownloadResultDesignType.IMAGE,
                    previewState = DownloadResultPreviewState.ResultImage(ByteArray(0)),
                    onDesignSelected = { selection, design ->
                        lastDesignSelection = selection to design
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(DOWNLOAD_RESULT_DESIGN_FREE_OPTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(DOWNLOAD_RESULT_DESIGN_IMAGE_OPTION_TEST_TAG).performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                DownloadResultSelection.Match("match-1") to DownloadResultDesignType.IMAGE,
                lastDesignSelection,
            )
        }
    }

    @Test
    fun busyDownloadActionIsDisabled() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        status = MatchStatus.FINALIZED,
                        resultDownloadUiState = ResultDownloadUiState.Saving(ResultExportFileFormat.PDF),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_DOWNLOAD_RESULT_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun correctionActionRequiresConfirmation() {
        var correctionCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(status = MatchStatus.FINALIZED),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = true,
                    onStartCorrection = { correctionCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_CORRECTION_ACTION_TEST_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithText("This opens an editable correction copy. The finalized result stays unchanged until you submit it.")
            .assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(0, correctionCount) }
        composeTestRule.onNodeWithTag(MATCH_REVIEW_CORRECTION_CONFIRM_ACTION_TEST_TAG).performClick()
        composeTestRule.runOnIdle { assertEquals(1, correctionCount) }
    }

    @Test
    fun correctionHistoryShowsPreviousAndCorrectedValues() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        status = MatchStatus.FINALIZED,
                        correctionHistory = listOf(
                            MatchCorrectionRecord(
                                previousPlacements = listOf(MatchPlacement(1, 7)),
                                previousKills = listOf(MatchKill(1, 3)),
                                correctedPlacements = listOf(MatchPlacement(1, 2)),
                                correctedKills = listOf(MatchKill(1, 8)),
                            ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_CORRECTION_HISTORY_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Previous finalized result — Slot 1: placement 7, kills 3")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Corrected result — Slot 1: placement 2, kills 8")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun simplifiedInlineOcrHidesPerRowWarningDetailsAndVerboseSummary() {
        val baseState = inlineOcrState()
        val baseDraft = baseState.correctionDraft ?: error("Expected correction draft")
        val warningDraft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(baseDraft, 0, "9")
        val warningState = baseState.copy(
            warningCount = warningDraft.warningCount,
            correctionDraft = warningDraft,
        )
        assertEquals(1, warningDraft.warningCount)

        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    showInlineOcrDetails = true,
                    ocrUiState = warningState,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.rowWarning(0)).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Warnings:").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Warning: Kills changed from OCR value.").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Correction draft:", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Correction draft has unsaved in-memory changes.")
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Corrected rows are ready for finalization review.")
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Finalization blocked:", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Warnings requiring confirmation:", substring = true)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.FINALIZE_BLOCKED_LABEL)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.FINALIZE_WARNING_COUNT)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.RESET_ALL)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun simplifiedInlineOcrHidesPerRowBlockerDetailsButKeepsBlockingState() {
        val baseState = inlineOcrState()
        val baseDraft = baseState.correctionDraft ?: error("Expected correction draft")
        val blockedDraft = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(baseDraft, 0, "")
        val blockedState = baseState.copy(
            correctionDraft = blockedDraft,
        )
        assertTrue(blockedDraft.blockerCount > 0)

        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    showLegacyManualReviewContent = false,
                    showInlineOcrDetails = true,
                    ocrUiState = blockedState,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.rowBlocker(0)).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Correction blockers").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Blocker:", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.rowWarning(0)).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Warnings:").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Warning:", substring = true).assertCountEquals(0)
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.placementInput(0))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .performScrollTo()
            .assertIsNotEnabled()
    }

    private fun setScreenshotDescriptionContent(
        lobbyUiState: MatchLobbyScreenshotIntakeUiState = emptyLobbyReadyState(),
        resultScreenshots: List<MatchResultScreenshotSlotUiState> = defaultMatchResultScreenshotSlots(),
    ) {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(resultScreenshots = resultScreenshots),
                    lobbyUiState = lobbyUiState,
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    matchLobbyScreenshotIntake = {
                        MatchLobbyScreenshotIntakeScreen(
                            uiState = lobbyUiState,
                            onSelect = {},
                            onCrop = {},
                            onRemove = {},
                            showTitle = false,
                            compactSelectors = true,
                            compactActions = true,
                        )
                    },
                    showLegacyManualReviewContent = false,
                )
            }
        }
    }

    private fun setResultFormatAvailabilityContent(
        status: CustomDesignFormatAvailabilityStatus,
    ) {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        status = MatchStatus.FINALIZED,
                        finalizedParticipantSlotNumbers = (1..12).toSet(),
                    ),
                    customDesignFormatAvailabilityUiState = CustomDesignFormatAvailabilityUiState(
                        status = status,
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }
        openResultFormatDialog()
    }

    private fun openResultFormatDialog() {
        composeTestRule.onNodeWithTag(MATCH_REVIEW_OVERFLOW_ACTION_TEST_TAG).performClick()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_DOWNLOAD_RESULT_ACTION_TEST_TAG)[0]
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_DOWNLOAD_SCOPE_CURRENT_MATCH_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_DOWNLOAD_SCOPE_CONTINUE_TEST_TAG).performClick()
    }

    private fun assertScreenshotDescriptions(lobbyVisible: Boolean, resultVisible: Boolean) {
        composeTestRule.onAllNodesWithText(LOBBY_SCREENSHOT_DESCRIPTION)
            .assertCountEquals(if (lobbyVisible) 1 else 0)
        composeTestRule.onAllNodesWithText(RESULT_SCREENSHOT_DESCRIPTION)
            .assertCountEquals(if (resultVisible) 1 else 0)
    }

    private fun createReadableResultPreviewUri(name: String): String {
        val cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = File.createTempFile("match-review-$name-", ".png", cacheDir)
        val bitmap = Bitmap.createBitmap(192, 108, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        return Uri.fromFile(file).toString()
    }

    private fun awaitResultScreenshotPreview(screenshotNumber: Int) {
        val previewTag = if (screenshotNumber == 1) {
            MATCH_REVIEW_RESULT_SCREENSHOT_1_PREVIEW_TEST_TAG
        } else {
            MATCH_REVIEW_RESULT_SCREENSHOT_2_PREVIEW_TEST_TAG
        }
        val previewDescription = "Result Screenshot $screenshotNumber preview"
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithTag(previewTag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule
            .onNodeWithTag(previewTag, useUnmergedTree = true)
            .assertExists()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithContentDescription(
                    previewDescription,
                    useUnmergedTree = true,
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun expandResultScreenshotActions(screenshotNumber: Int) {
        awaitResultScreenshotPreview(screenshotNumber)
        val replaceTag = if (screenshotNumber == 1) {
            MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG
        } else {
            MATCH_REVIEW_RESULT_SCREENSHOT_2_REPLACE_TEST_TAG
        }
        val previewDescription = "Result Screenshot $screenshotNumber preview"
        composeTestRule
            .onNodeWithContentDescription(previewDescription)
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithTag(replaceTag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule
            .onNodeWithTag(replaceTag, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun emptyLobbyReadyState() = MatchLobbyScreenshotIntakeUiState(
        isLoading = false,
        isAvailable = true,
        tournamentId = "tournament-id",
        matchId = "match-id",
        status = MatchStatus.DRAFT,
        slots = (1..3).map { index -> MatchLobbyScreenshotSlotUiState(index = index) },
    )

    private fun openAdjustTeamPointsDialog() {
        composeTestRule.onNodeWithTag(MATCH_REVIEW_OVERFLOW_ACTION_TEST_TAG).performClick()
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_ACTION_TEST_TAG)
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_DIALOG_TEST_TAG)
            .assertIsDisplayed()
    }

    private fun applyTeamPointAdjustment(teamSlotNumber: Int, input: String) {
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_TEAM_SELECTOR_TEST_TAG)
            .performClick()
        composeTestRule
            .onNodeWithTag(
                MATCH_REVIEW_ADJUST_TEAM_POINTS_TEAM_OPTION_TEST_TAG_PREFIX + teamSlotNumber,
            )
            .performClick()
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_INPUT_TEST_TAG)
            .performTextReplacement(input)
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_ADJUST_TEAM_POINTS_APPLY_TEST_TAG)
            .assertIsEnabled()
            .performClick()
    }

    private fun availableState(
        validationErrors: Map<Int, Set<MatchResultValidationError>> = emptyMap(),
        resultScreenshots: List<MatchResultScreenshotSlotUiState> = defaultMatchResultScreenshotSlots(),
        resultPositionCropPreviews: Map<
            MatchResultScreenshotRole,
            MatchResultPositionCropPreviewState,
        > = defaultMatchResultPositionCropPreviewStates(),
    ) = MatchReviewUiState(
        isLoading = false,
        isAvailable = true,
        tournamentId = "tournament-id",
        matchId = "match-id",
        matchNumber = 1,
        rows = (1..12).map { slotNumber ->
            MatchReviewRowUiState(
                teamSlotNumber = slotNumber,
                teamName = "Team $slotNumber",
                playerNames = if (slotNumber == 1) listOf("Player One") else emptyList(),
                placementInput = when {
                    slotNumber == 1 -> "7"
                    slotNumber <= 7 -> (slotNumber - 1).toString()
                    else -> slotNumber.toString()
                },
                killsInput = if (slotNumber == 1) "3" else "0",
                validationErrors = validationErrors[slotNumber].orEmpty(),
            )
        },
        validationErrors = validationErrors,
        resultScreenshots = resultScreenshots,
        resultPositionCropPreviews = resultPositionCropPreviews,
    )

    private fun setPositionCropVisibilityContent(
        ocrUiState: MatchOcrReviewUiState.Ready,
        positions: List<Int>,
    ) {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = positionCropTestUiState(positions),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    ocrUiState = ocrUiState,
                )
            }
        }
    }

    private fun positionCropTestUiState(
        positions: List<Int>,
    ) = availableState(
        resultScreenshots = selectedResultScreenshotSlots(),
        resultPositionCropPreviews = mapOf(
            MatchResultScreenshotRole.MATCH_RESULT_UPPER to
                MatchResultPositionCropPreviewState.Available(
                    positions.map { position ->
                        MatchResultPositionCropPreview(
                            position = position,
                            image = AndroidMatchResultPositionCropPreviewImage(
                                Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
                            ),
                        )
                    },
                ),
        ),
    )

    private fun ocrStateWithCorrectionRows(
        excludedRowIndexes: Set<Int> = emptySet(),
        implicitlyAbsentRowIndexes: Set<Int> = emptySet(),
    ): MatchOcrReviewUiState.Ready {
        val base = inlineOcrState()
        val baseDraft = base.correctionDraft ?: error("Expected correction draft")
        val rowIndexes = (excludedRowIndexes + implicitlyAbsentRowIndexes).sorted()
        val correctionRows = rowIndexes.map { rowIndex ->
            val isImplicitlyAbsent = rowIndex in implicitlyAbsentRowIndexes
            baseDraft.rows.single().copy(
                rowIndex = rowIndex,
                originalPlacementValue = if (isImplicitlyAbsent) "" else "1",
                originalKillsValue = if (isImplicitlyAbsent) "" else "8",
                originalAssignedTeamSlotValue = if (isImplicitlyAbsent) "" else "1",
                placementDraftValue = if (isImplicitlyAbsent) "" else "1",
                killsDraftValue = if (isImplicitlyAbsent) "" else "8",
                assignedTeamSlotDraftValue = if (isImplicitlyAbsent) "" else "1",
                isExcluded = rowIndex in excludedRowIndexes,
                allPlayersSemanticallyNotDetected = isImplicitlyAbsent,
            )
        }
        return base.copy(
            rows = emptyList(),
            rowCount = 0,
            blockerCount = 0,
            warningCount = 0,
            safeRowCount = 0,
            manualRequiredRowCount = 0,
            reviewRequiredRowCount = 0,
            manualReviewRequired = false,
            correctionDraft = baseDraft.copy(rows = correctionRows),
        )
    }

    private fun allLobbyReadyState() = MatchLobbyScreenshotIntakeUiState(
        isLoading = false,
        isAvailable = true,
        tournamentId = "tournament-id",
        matchId = "match-id",
        status = MatchStatus.DRAFT,
        slots = (1..3).map { index ->
            MatchLobbyScreenshotSlotUiState(
                index = index,
                hasLinkedAsset = true,
                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                cropProfileId = "lobby",
            )
        },
    )

    private fun allResultReadySlots() = listOf(
        resultSlot(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            hasLinkedAsset = true,
            confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
            cropProfileId = "match-result",
        ),
        resultSlot(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            hasLinkedAsset = true,
            confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
            cropProfileId = "match-result",
        ),
    )

    private fun processedLobbySlotNumberOcrWithPlayerRows(): MatchLobbySlotNumberOcrResult {
        val image = AndroidMatchLobbyTeamCropPreviewImage(
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888),
        )
        val rowPreview = LobbyPlayerRowCropPreview(
            row = LobbyPlayerRow.ROW_1,
            boundsInTeamCrop = LobbyPlayerRowCropBounds(0, 0, 4, 2),
            slotAnchorSource = LobbySlotAnchorSource.TEAM_CROP_CENTER_FALLBACK,
            slotAnchorY = 4.0,
            structuralEvidence = null,
        )
        val teamCrops = MatchLobbyTeamCropPreviewResult.Available(
            RosterVisibleSlotPosition.entries.mapIndexed { index, position ->
                MatchLobbyTeamCropPreview(
                    visibleSlotPosition = position,
                    detectedSlotNumber = index + 1,
                    image = image,
                    playerRowPreviews = if (index == 0) listOf(rowPreview) else emptyList(),
                )
            },
        )
        return MatchLobbySlotNumberOcrResult(
            RosterScreenshotPosition.entries.map { screenshotPosition ->
                MatchLobbySlotNumberOcrScreenshotResult.Processed(
                    screenshotPosition = screenshotPosition,
                    slots = RosterVisibleSlotPosition.entries.map { visiblePosition ->
                        MatchLobbySlotNumberOcrSlot(
                            visibleSlotPosition = visiblePosition,
                            candidate = RosterSlotNumberCandidate.unavailable(),
                        )
                    },
                    teamCropPreviews = teamCrops,
                )
            },
        )
    }

    private fun inlineOcrState(): MatchOcrReviewUiState.Ready {
        val row = MatchOcrReviewRowUiState(
            rowIndex = 0,
            expectedPlacementLabel = "1",
            detectedPlacementDisplayValue = "1",
            placementStatusLabel = "Accepted",
            detectedKillDisplayValue = "8",
            killStatusLabel = "Accepted",
            detectedPlayerNameEvidenceLabel = "Player One",
            playerNameStatusLabel = "Accepted",
            suggestedTeamSlotDisplayValue = "1",
            confidenceScoreDisplayValue = "96",
            confidenceTierLabel = "Automatic candidate",
            assignmentSafetyStatusLabel = "Safe automatic assignment",
            topThreeSuggestionsSummary = listOf("Rank 1: Slot 1"),
            warningLabels = emptyList(),
            blockerLabels = emptyList(),
            severity = MatchOcrReviewSeverity.INFORMATIONAL,
            originalParsedPlacementValue = 1,
            originalParsedKillValue = 8,
            originalSuggestedTeamSlot = 1,
        )
        return MatchOcrReviewUiState.Ready(
            tournamentId = "tournament-id",
            matchId = "match-id",
            rowCount = 1,
            rows = listOf(row),
            blockerCount = 0,
            warningCount = 0,
            safeRowCount = 1,
            manualRequiredRowCount = 0,
            reviewRequiredRowCount = 0,
            manualReviewRequired = false,
            hasUnavailableEvidence = false,
            correctionDraft = MatchOcrReviewCorrectionDraft(
                rows = listOf(
                    MatchOcrReviewRowCorrectionDraft(
                        rowIndex = 0,
                        originalPlacementValue = "1",
                        originalKillsValue = "8",
                        originalAssignedTeamSlotValue = "1",
                        placementDraftValue = "1",
                        killsDraftValue = "8",
                        assignedTeamSlotDraftValue = "1",
                        originallyRequiredManualReview = false,
                        weakConfidenceOrSafetyEvidence = false,
                        validation = MatchOcrReviewRowCorrectionValidation(),
                    ),
                ),
            ),
            teamNamesBySlot = mapOf(1 to "Team 1", 11 to "Team 11"),
            lobbyPlayers = listOf(
                MatchOcrReviewLobbySlotUiState(
                    slotNumber = 11,
                    players = listOf(
                        MatchOcrReviewLobbyPlayerUiState(1, "Lobby One"),
                        MatchOcrReviewLobbyPlayerUiState(2, null),
                        MatchOcrReviewLobbyPlayerUiState(3, "Lobby Three"),
                        MatchOcrReviewLobbyPlayerUiState(4, null),
                    ),
                ),
            ),
        )
    }

    private fun automaticOcrState(
        visiblePositions: IntRange,
    ): MatchOcrReviewUiState.Ready {
        val role = MatchResultScreenshotRole.MATCH_RESULT_UPPER
        val preview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(role),
            rows = visiblePositions.map { position -> previewRow(position, role) },
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
            authoritativePositionCropsByRole = mapOf(
                role to visiblePositions.map { position ->
                    MatchResultPositionCrop(
                        position = position,
                        column = MatchResultPositionColumn.LEFT,
                        bounds = OcrPixelCropRect(0, 0, 1, 1),
                        hasDetectedPositionAnchor = true,
                    )
                },
            ),
        )
        val rows = MatchResultOcrPreviewUiStateMapper.toReviewRows(preview)!!
        val draft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows)
        return MatchOcrReviewUiState.Ready(
            tournamentId = "tournament-id",
            matchId = "match-id",
            rowCount = rows.size,
            rows = rows,
            blockerCount = draft.blockerCount,
            warningCount = draft.warningCount,
            safeRowCount = 0,
            manualRequiredRowCount = rows.size,
            reviewRequiredRowCount = 0,
            manualReviewRequired = true,
            hasUnavailableEvidence = true,
            correctionDraft = draft,
            matchResultOcrPreview = preview,
        )
    }

    private fun warningOcrState(): MatchOcrReviewUiState.Ready {
        val base = inlineOcrState()
        val baseDraft = base.correctionDraft ?: error("Expected correction draft")
        val warningDraft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(baseDraft, 0, "9")
        return base.copy(
            warningCount = warningDraft.warningCount,
            correctionDraft = warningDraft,
        )
    }

    private fun selectedResultScreenshotSlots() = listOf(
        resultSlot(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            hasLinkedAsset = true,
            confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
            cropProfileId = "match-result",
        ),
        resultSlot(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
    )

    private fun combinedPositionCropPreviewStates() = mapOf(
        MatchResultScreenshotRole.MATCH_RESULT_UPPER to
            MatchResultPositionCropPreviewState.Available(
                listOf(
                    MatchResultPositionCropPreview(
                        position = 1,
                        image = AndroidMatchResultPositionCropPreviewImage(
                            Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888),
                        ),
                    ),
                ),
            ),
    )

    private fun inlineOcrStateWithRows(): MatchOcrReviewUiState.Ready {
        val first = inlineOcrState()
        val firstRow = first.rows.single()
        val secondRow = firstRow.copy(
            rowIndex = 1,
            expectedPlacementLabel = "2",
            detectedPlacementDisplayValue = "2",
            detectedKillDisplayValue = "9",
            detectedPlayerNameEvidenceLabel = "Player Two",
            suggestedTeamSlotDisplayValue = "2",
            originalParsedPlacementValue = 2,
            originalParsedKillValue = 9,
            originalSuggestedTeamSlot = 2,
        )
        val correctionDraft = first.correctionDraft ?: error("Expected correction draft")
        val firstCorrection = correctionDraft.rows.single()
        val secondCorrection = firstCorrection.copy(
            rowIndex = 1,
            originalPlacementValue = "2",
            originalKillsValue = "9",
            originalAssignedTeamSlotValue = "2",
            placementDraftValue = "2",
            killsDraftValue = "9",
            assignedTeamSlotDraftValue = "2",
        )
        return first.copy(
            rowCount = 2,
            rows = listOf(secondRow, firstRow),
            safeRowCount = 2,
            correctionDraft = correctionDraft.copy(
                rows = listOf(secondCorrection, firstCorrection),
            ),
            lobbyPlayers = emptyList(),
        )
    }

    private fun inlineOcrStateForPosition11(
        playersBySlot: Map<Int, String> = emptyMap(),
    ): MatchOcrReviewUiState.Ready {
        val base = inlineOcrState()
        val row = base.rows.single().copy(
            rowIndex = 10,
            expectedPlacementLabel = "11",
            detectedPlacementDisplayValue = "11",
            detectedPlayerNameEvidenceLabel = "Unavailable",
            suggestedTeamSlotDisplayValue = "Unavailable",
            originalParsedPlacementValue = 11,
            originalSuggestedTeamSlot = null,
            blockerLabels = listOf("Team assignment: manual team slot required"),
            severity = MatchOcrReviewSeverity.BLOCKING,
        )
        val correction = base.correctionDraft?.rows?.single()?.copy(
            rowIndex = 10,
            originalPlacementValue = "11",
            placementDraftValue = "11",
            originalAssignedTeamSlotValue = "",
            assignedTeamSlotDraftValue = "",
        ) ?: error("Expected correction draft")
        val preview = previewRow(11, MatchResultScreenshotRole.MATCH_RESULT_LOWER).copy(
            slots = previewRow(11, MatchResultScreenshotRole.MATCH_RESULT_LOWER).slots.map { slot ->
                slot.copy(
                    playerText = playersBySlot[slot.slot].orEmpty(),
                    playerOcrText = playersBySlot[slot.slot].orEmpty(),
                )
            },
        )
        return base.copy(
            rowCount = 1,
            rows = listOf(row),
            blockerCount = 1,
            safeRowCount = 0,
            manualRequiredRowCount = 1,
            manualReviewRequired = true,
            correctionDraft = base.correctionDraft.copy(rows = listOf(correction)),
            matchResultOcrPreview = MatchResultOcrPreviewUiState.Ready(
                roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                rows = listOf(preview),
                ignoredLowerRows = emptyList(),
                manualReviewRows = emptyList(),
            ),
            lobbyPlayers = emptyList(),
        )
    }

    private fun inlineOcrStateWithExcludedPosition11AndPosition12(): MatchOcrReviewUiState.Ready {
        val base = inlineOcrStateForPosition11()
        val row11 = base.rows.single()
        val row12 = row11.copy(
            rowIndex = 11,
            expectedPlacementLabel = "12",
            detectedPlacementDisplayValue = "12",
            detectedKillDisplayValue = "12",
            detectedPlayerNameEvidenceLabel = "Position 12 Player",
            originalParsedPlacementValue = 12,
            originalParsedKillValue = 12,
            suggestedTeamSlotDisplayValue = "1",
            originalSuggestedTeamSlot = 1,
            blockerLabels = emptyList(),
            severity = MatchOcrReviewSeverity.INFORMATIONAL,
        )
        val baseDraft = base.correctionDraft ?: error("Expected correction draft")
        val correction11 = baseDraft.rows.single().copy(isExcluded = true)
        val correction12 = correction11.copy(
            rowIndex = 11,
            originalPlacementValue = "12",
            originalKillsValue = "12",
            originalAssignedTeamSlotValue = "1",
            placementDraftValue = "12",
            killsDraftValue = "12",
            assignedTeamSlotDraftValue = "1",
            isExcluded = false,
        )
        val preview11 = previewRow(11, MatchResultScreenshotRole.MATCH_RESULT_LOWER).copy(
            slots = previewRow(11, MatchResultScreenshotRole.MATCH_RESULT_LOWER).slots.map {
                it.copy(playerText = "Position 11 Player", playerOcrText = "Position 11 Player")
            },
        )
        val preview12 = previewRow(12, MatchResultScreenshotRole.MATCH_RESULT_LOWER).copy(
            slots = previewRow(12, MatchResultScreenshotRole.MATCH_RESULT_LOWER).slots.map {
                it.copy(playerText = "Position 12 Player", playerOcrText = "Position 12 Player")
            },
        )
        return base.copy(
            rowCount = 2,
            rows = listOf(row11, row12),
            blockerCount = 0,
            safeRowCount = 1,
            manualRequiredRowCount = 0,
            manualReviewRequired = false,
            correctionDraft = baseDraft.copy(rows = listOf(correction11, correction12)),
            matchResultOcrPreview = MatchResultOcrPreviewUiState.Ready(
                roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
                rows = listOf(preview11, preview12),
                ignoredLowerRows = emptyList(),
                manualReviewRows = emptyList(),
            ),
        )
    }

    private fun embeddedManualOcrState(): MatchOcrReviewUiState.Ready {
        val base = inlineOcrState()
        val rows = buildList {
            add(
                base.rows.single().copy(
                    rowIndex = 0,
                    expectedPlacementLabel = "1",
                    originalParsedPlacementValue = 1,
                    originalSuggestedTeamSlot = null,
                    suggestedTeamSlotDisplayValue = "Unavailable",
                    blockerLabels = listOf("Team assignment: manual required"),
                    severity = MatchOcrReviewSeverity.BLOCKING,
                ),
            )
            add(
                base.rows.single().copy(
                    rowIndex = 1,
                    expectedPlacementLabel = "2",
                    originalParsedPlacementValue = 2,
                    originalSuggestedTeamSlot = null,
                    suggestedTeamSlotDisplayValue = "Unavailable",
                    blockerLabels = listOf("Team assignment: manual required"),
                    severity = MatchOcrReviewSeverity.BLOCKING,
                ),
            )
            (1..10).forEach { slot ->
                add(
                    base.rows.single().copy(
                        rowIndex = slot + 1,
                        expectedPlacementLabel = (slot + 2).toString(),
                        originalParsedPlacementValue = slot + 2,
                        originalSuggestedTeamSlot = slot,
                        suggestedTeamSlotDisplayValue = slot.toString(),
                        blockerLabels = emptyList(),
                        severity = MatchOcrReviewSeverity.INFORMATIONAL,
                    ),
                )
            }
        }
        val draft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows)
        return base.copy(
            rowCount = rows.size,
            rows = rows,
            blockerCount = draft.blockerCount,
            warningCount = draft.warningCount,
            safeRowCount = 10,
            manualRequiredRowCount = 2,
            manualReviewRequired = true,
            correctionDraft = draft,
        )
    }

    private fun previewRow(
        position: Int,
        role: MatchResultScreenshotRole,
    ) = MatchResultOcrPreviewRowUiState(
        position = position,
        role = role,
        sourceLabel = "SOURCE_$position",
        placementText = position.toString(),
        slots = (1..4).map { slot ->
            MatchResultOcrPreviewSlotUiState(
                slot = slot,
                playerText = "Player $position-$slot",
                playerOcrText = "Player $position-$slot",
                playerStatusLabel = "DIRECT_NUMERIC",
                killText = slot.toString(),
                killOcrText = slot.toString(),
                killStatusLabel = "DIRECT_NUMERIC",
            )
        },
    )

    private fun resultSlot(
        role: MatchResultScreenshotRole,
        selectedScreenshotUri: String? = null,
        localPreviewUri: String? = null,
        originalWidth: Int? = null,
        originalHeight: Int? = null,
        isPhotoPickerRequestActive: Boolean = false,
        isSelectedScreenshotValidated: Boolean = false,
        imageValidationError: ImageValidationError? = null,
        hasLinkedAsset: Boolean = false,
        isDuplicateDetectionInProgress: Boolean = false,
        duplicateInfo: ScreenshotDuplicateInfo? = null,
        duplicateError: ScreenshotDuplicateError? = null,
        isPreservationInProgress: Boolean = false,
        preservationError: ScreenshotPreservationError? = null,
        isLocalFileMissing: Boolean = false,
        isUploadInProgress: Boolean = false,
        uploadStatus: ScreenshotMetadataUploadUiStatus? = null,
        uploadError: ScreenshotUploadError? = null,
        confirmedCrop: OcrNormalizedCropRect? = null,
        cropProfileId: String? = null,
    ) = MatchResultScreenshotSlotUiState(
        role = role,
        selectedScreenshotUri = selectedScreenshotUri,
        localPreviewUri = localPreviewUri,
        originalWidth = originalWidth,
        originalHeight = originalHeight,
        isPhotoPickerRequestActive = isPhotoPickerRequestActive,
        isSelectedScreenshotValidated = isSelectedScreenshotValidated,
        imageValidationError = imageValidationError,
        hasLinkedAsset = hasLinkedAsset,
        isDuplicateDetectionInProgress = isDuplicateDetectionInProgress,
        duplicateInfo = duplicateInfo,
        duplicateError = duplicateError,
        isPreservationInProgress = isPreservationInProgress,
        preservationError = preservationError,
        isLocalFileMissing = isLocalFileMissing,
        isUploadInProgress = isUploadInProgress,
        uploadStatus = uploadStatus,
        uploadError = uploadError,
        confirmedCrop = confirmedCrop,
        cropProfileId = cropProfileId,
    )
}
