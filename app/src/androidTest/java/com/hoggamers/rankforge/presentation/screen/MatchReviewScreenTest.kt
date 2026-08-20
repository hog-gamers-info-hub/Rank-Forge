package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.data.export.AndroidExportCoordinator
import com.hoggamers.rankforge.data.export.AndroidExportResult
import com.hoggamers.rankforge.data.export.AndroidGoogleSheetsExportFailureReason
import com.hoggamers.rankforge.data.ocr.MatchOcrCacheAvailability
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionRecord
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchReviewScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

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
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Select Screenshot 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Back to Tournament Details").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_ROW_TEST_TAG_PREFIX + "1").assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
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

        composeTestRule.onNodeWithText("Loading match review").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Review Match 1").assertCountEquals(0)
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

        composeTestRule.onNodeWithText("Loading match review").assertIsDisplayed()
        composeTestRule.runOnIdle { lobbyUiState = allLobbyReadyState() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Review Match 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lobby screenshot slots").assertIsDisplayed()
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

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .assertIsEnabled()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_PREFLIGHT_DIALOG_TEST_TAG)
            .assertIsDisplayed()
        listOf(
            "Lobby Screenshot 1 is not available.",
            "Lobby Screenshot 2 is not available.",
            "Lobby Screenshot 3 is not available.",
            "Result Screenshot 1 is not available.",
            "Result Screenshot 2 is not available.",
        ).forEach {
            composeTestRule.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun completeEvidenceBypassesPreflightAndStartsOcr() {
        var opened = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(resultScreenshots = allResultReadySlots()),
                    lobbyUiState = allLobbyReadyState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onOpenOcrReview = { opened++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG).performClick()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_OCR_PREFLIGHT_DIALOG_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.runOnIdle { assertEquals(1, opened) }
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertIsDisplayed()
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
                    uiState = availableState(),
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
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun calculatePointsAcceptsIncompleteEvidenceAndOpensInlineOcr() {
        var calculated = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onCalculatePoints = { calculated++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_PREFLIGHT_CALCULATE_ACTION_TEST_TAG)
            .performClick()
        composeTestRule.runOnIdle { assertEquals(1, calculated) }
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
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
                    uiState = availableState(),
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
        composeTestRule.onNodeWithText("Slot - 11 | Team name - Team 11").assertIsDisplayed()
        composeTestRule.onNodeWithText("1. Lobby One").assertIsDisplayed()
        composeTestRule.onNodeWithText("2. Not detected").assertIsDisplayed()
        composeTestRule.onNodeWithText("3. Lobby Three").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.ROW_LIST)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Position - 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Position").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kills").assertIsDisplayed()
        composeTestRule.onNodeWithText("Slot").assertIsDisplayed()
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
                    uiState = availableState(),
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
        composeTestRule.onNodeWithText("1. Lobby Two").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbySlot(11))
            .assertIsNotDisplayed()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_LOBBY_PLAYERS_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbySlot(11))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("1. Lobby Eleven").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbySlot(2))
            .assertIsNotDisplayed()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_LOBBY_PLAYERS_PAGER_TEST_TAG)
            .performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbySlot(2))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("1. Lobby Two").assertIsDisplayed()
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
    fun cachedOcrAvailabilityIsShownWithoutChangingInlineReviewStructure() {
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

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_READY_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun inlineOcrCorrectionControlsKeepCallbacksConnected() {
        val placements = mutableListOf<Pair<Int, String>>()
        val kills = mutableListOf<Pair<Int, String>>()
        val teamSlots = mutableListOf<Pair<Int, String>>()
        val resetRows = mutableListOf<Int>()
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
                    onOcrResetRowCorrection = { rowIndex -> resetRows += rowIndex },
                    onOcrResetAllCorrections = {},
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
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.resetRow(0))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onAllNodesWithText("Reset").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.RESET_ALL).assertCountEquals(0)
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(placements.any { it.first == 0 })
            assertTrue(kills.any { it.first == 0 })
            assertTrue(teamSlots.any { it.first == 0 })
            assertEquals(listOf(0), resetRows)
            assertEquals(1, finalizeCount)
        }
    }

    @Test
    fun simplifiedInlineFinalizeActionSitsBetweenResultRowsAndOcrReview() {
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
        val ocrReviewAction = composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)

        resultRows.performScrollTo()
        finalizeAction.performScrollTo().assertIsDisplayed()
        ocrReviewAction.performScrollTo().assertIsDisplayed()

        val resultRowsBounds = resultRows.fetchSemanticsNode().boundsInRoot
        val finalizeBounds = finalizeAction.fetchSemanticsNode().boundsInRoot
        val ocrReviewBounds = ocrReviewAction.fetchSemanticsNode().boundsInRoot
        assertTrue(finalizeBounds.top >= resultRowsBounds.bottom)
        assertTrue(finalizeBounds.bottom <= ocrReviewBounds.top)
    }

    @Test
    fun simplifiedReviewOcrActionInvokesCallbackWhenEligible() {
        var ocrReviewCount = 0
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
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeTestRule.runOnIdle { assertEquals(1, ocrReviewCount) }
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
    fun inlineOcrDetailsRemainVisibleAfterMatchFinalization() {
        var openedCount = 0
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
                    onOpenOcrReview = { openedCount++ },
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
        composeTestRule.runOnIdle { assertEquals(1, openedCount) }

        composeTestRule.runOnIdle {
            matchState = matchState.copy(status = MatchStatus.FINALIZED)
            ocrState = ocrState.copy(
                finalization = MatchOcrReviewFinalizationUiState(isFinalized = true),
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.placementInput(0))
            .assertIsNotEnabled()
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
        composeTestRule.onNodeWithText("Select Screenshot 1").assertIsDisplayed()
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
        composeTestRule.onNodeWithText("Select Screenshot 2").performClick()
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
        composeTestRule.onNodeWithText("Select Screenshot 1").performClick()
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

        composeTestRule.onNodeWithText("Select Screenshot 2").performClick()
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
        composeTestRule.onNodeWithText("Select Screenshot 2").assertIsDisplayed()
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

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_PREVIEW_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_PREVIEW_TEST_TAG)
            .assertCountEquals(0)
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
                                localPreviewUri = "file:///private/result-1.png",
                                originalWidth = 1920,
                                originalHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.0, 0.0, 1.0, 0.5),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                localPreviewUri = "file:///private/result-2.png",
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
        composeTestRule.waitForIdle()
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
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                localPreviewUri = "file:///private/result-2.png",
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

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_REPLACE_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG)
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
        composeTestRule.onNodeWithText("Select Screenshot 1").assertIsDisplayed()
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
        composeTestRule.onNodeWithText("Select Screenshot 2").assertIsDisplayed()
    }

    @Test
    fun lowerOnlySelectedResultMapsPagerPageToLowerRole() {
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

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Select Screenshot 1").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_PREVIEW_TEST_TAG)
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
                                confirmedCrop = OcrNormalizedCropRect(0.0, 0.0, 1.0, 0.5),
                                cropProfileId = "match-result",
                            ),
                            resultSlot(
                                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                                hasLinkedAsset = true,
                                localPreviewUri = "file:///private/result-2.png",
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

        val initialHeight = composeTestRule
            .onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_PREVIEW_TEST_TAG)
            .fetchSemanticsNode().size.height
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        val secondHeight = composeTestRule
            .onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_PREVIEW_TEST_TAG)
            .fetchSemanticsNode().size.height
        assertEquals(initialHeight, secondHeight)
    }

    @Test
    fun linkedResultScreenshotTwoShowsItsConfirmedCropPreview() {
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

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_PREVIEW_TEST_TAG)
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
    fun simplifiedFinalizedReviewShowsGoogleSheetsActionAndHidesLegacyControls() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(status = MatchStatus.FINALIZED),
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

        composeTestRule.onNodeWithTag(MATCH_REVIEW_GOOGLE_SHEETS_EXPORT_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_CORRECTION_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .assertIsNotEnabled()
    }

    @Test
    fun draftReviewDoesNotExposeGoogleSheetsAction() {
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

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_GOOGLE_SHEETS_EXPORT_ACTION_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun googleSheetsExportStatusesAreDeterministic() {
        val coordinator = AndroidExportCoordinator()
        val statuses = listOf(
            AndroidExportResult.GoogleSheetsExporting(
                coordinator.googleSheetsMatchExporting("tournament-id", "match-id").request,
            ),
            coordinator.googleSheetsMatchSuccess("tournament-id", "match-id", 1, 12),
            coordinator.googleSheetsMatchFailure(
                "tournament-id",
                "match-id",
                AndroidGoogleSheetsExportFailureReason.NETWORK_FAILURE,
            ),
            coordinator.blockGoogleSheetsMatch(
                "tournament-id",
                "match-id",
                com.hoggamers.rankforge.data.export.AndroidExportBlockedReason.INVALID_FINALIZED_MATCH,
            ),
        )
        val expectedText = listOf(
            "Exporting to Google Sheets",
            "Google Sheets export successful",
            "Google Sheets export failed",
            "Google Sheets export blocked",
        )

        var exportResult by mutableStateOf<AndroidExportResult>(statuses.first())
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        status = MatchStatus.FINALIZED,
                        googleSheetsExportResult = exportResult,
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
                exportResult = status
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag(MATCH_REVIEW_GOOGLE_SHEETS_EXPORT_STATUS_TEST_TAG)
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithText(expectedText[index]).assertIsDisplayed()
        }
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
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.resetRow(0))
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun availableState(
        validationErrors: Map<Int, Set<MatchResultValidationError>> = emptyMap(),
        resultScreenshots: List<MatchResultScreenshotSlotUiState> = defaultMatchResultScreenshotSlots(),
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
    )

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
