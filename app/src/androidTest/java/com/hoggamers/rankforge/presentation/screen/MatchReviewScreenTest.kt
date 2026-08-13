package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        composeTestRule.onNodeWithText("Screenshot 1 of 2").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_INDICATOR_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Back to Tournament Details").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_ROW_TEST_TAG_PREFIX + "1").assertCountEquals(0)
        val lobbyY = composeTestRule.onNodeWithTag(MATCH_REVIEW_LOBBY_SCREENSHOTS_SECTION_TEST_TAG)
            .fetchSemanticsNode().positionInRoot.y
        val resultY = composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_SECTION_TEST_TAG)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(lobbyY < resultY)
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
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_PREVIEW_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_PREVIEW_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_PREVIEW_TEST_TAG)
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
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_PREVIEW_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun resultPagerUsesStaticRoleBoundActionsAndReturnsToFirstPage() {
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
                    onSelectResultScreenshot = { selectedRoles += it },
                    onOpenResultScreenshotCrop = { cropRoles += it },
                    onRemoveResultScreenshot = { removedRoles += it },
                )
            }
        }

        val pager = composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
        val initialActionY = composeTestRule
            .onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG)
            .fetchSemanticsNode().positionInRoot.y
        composeTestRule.onNodeWithText("Screenshot 1 of 2").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_INDICATOR_TEST_TAG_PREFIX + 1)
            .assertIsSelected()
        composeTestRule.onNodeWithText("Replace").assertIsDisplayed()
        composeTestRule.onNodeWithText("Crop").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_CROP_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REMOVE_TEST_TAG).performClick()

        pager.performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Screenshot 2 of 2").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_INDICATOR_TEST_TAG_PREFIX + 1)
            .assertIsNotSelected()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_INDICATOR_TEST_TAG_PREFIX + 2)
            .assertIsSelected()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_REPLACE_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_CROP_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_REMOVE_TEST_TAG).performClick()
        assertEquals(
            initialActionY,
            composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_2_REPLACE_TEST_TAG)
                .fetchSemanticsNode().positionInRoot.y,
        )

        pager.performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Screenshot 1 of 2").assertIsDisplayed()
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
    fun resultPagerActionStateFollowsBusyMissingActiveSlot() {
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
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_REPLACE_TEST_TAG)
            .assertIsDisplayed()
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
                )
            }
        }

        val pager = composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
        val initialHeight = pager.fetchSemanticsNode().size.height
        pager.performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        assertEquals(initialHeight, pager.fetchSemanticsNode().size.height)
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
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
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
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
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
                    onSelectResultScreenshot = {
                        if (it == MatchResultScreenshotRole.MATCH_RESULT_UPPER) photoPickerActionCount++
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_SELECT_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_SECTION_TEST_TAG).assertIsDisplayed()
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
                )
            }
        }

        composeTestRule.onNodeWithText("Preserving screenshot locally.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Screenshot ready.")
            .performScrollTo()
            .assertIsDisplayed()
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
                    onOpenResultScreenshotCrop = {
                        if (it == MatchResultScreenshotRole.MATCH_RESULT_UPPER) cropCount++
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOT_1_CROP_READY_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
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
                )
            }
        }

        composeTestRule.onNodeWithText("Screenshot ready.")
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
                )
            }
        }

        composeTestRule.onNodeWithText("Screenshot ready.")
            .performScrollTo()
            .assertIsDisplayed()
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
    fun correctionActionRequiresConfirmation() {
        var correctionCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(status = MatchStatus.FINALIZED),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
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
