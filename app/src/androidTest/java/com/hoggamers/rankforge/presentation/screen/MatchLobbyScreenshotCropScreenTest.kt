package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchLobbyScreenshotCropScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun missingAssetRendersControlledStateAndCancelCallback() {
        var cancelled = false
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotCropScreen(
                    uiState = MatchLobbyScreenshotCropUiState(
                        isLoading = false,
                        lobbyScreenshotIndex = 1,
                        error = MatchLobbyScreenshotCropError.MISSING_ASSET,
                    ),
                    onCropChanged = {},
                    onCancel = { cancelled = true },
                    onConfirm = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_CROP_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_CROP_CANCEL_TEST_TAG)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle { assertTrue(cancelled) }
    }
}
