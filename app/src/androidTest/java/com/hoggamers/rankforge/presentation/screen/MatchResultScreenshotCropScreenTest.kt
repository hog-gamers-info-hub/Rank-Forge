package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchResultScreenshotCropScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loadingStateShowsOnlyLoadingContent() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchResultScreenshotCropScreen(
                    uiState = MatchResultScreenshotCropUiState(
                        isLoading = true,
                        role = com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                    ),
                    onCropChanged = {},
                    onCancel = {},
                    onConfirm = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_RESULT_SCREENSHOT_CROP_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_RESULT_SCREENSHOT_CROP_LOADING_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_RESULT_SCREENSHOT_CROP_EDITOR_TEST_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(MATCH_RESULT_SCREENSHOT_CROP_CANCEL_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun loadedStateShowsCropEditorAndCancel() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchResultScreenshotCropScreen(
                    uiState = MatchResultScreenshotCropUiState(
                        isLoading = false,
                        role = com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                        imageUri = "file:///private/result-1.png",
                        originalWidth = 1920,
                        originalHeight = 1080,
                        draftCrop = OcrNormalizedCropRect(0.0, 0.0, 1.0, 1.0),
                    ),
                    onCropChanged = {},
                    onCancel = {},
                    onConfirm = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_RESULT_SCREENSHOT_CROP_EDITOR_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_RESULT_SCREENSHOT_CROP_LOADING_TEST_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(MATCH_RESULT_SCREENSHOT_CROP_CANCEL_TEST_TAG).assertIsDisplayed()
    }
}
