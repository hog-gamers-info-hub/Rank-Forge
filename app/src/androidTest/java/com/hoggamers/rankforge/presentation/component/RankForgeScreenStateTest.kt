package com.hoggamers.rankforge.presentation.component

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RankForgeScreenStateTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loadingStateDisplaysMessageAndProgressIndicator() {
        val message = "Loading baseline"

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeLoadingState(message)
            }
        }

        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        composeTestRule
            .onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertCountEquals(1)
    }

    @Test
    fun emptyStateDisplaysDescriptionAndInvokesActionOnce() {
        var actionClicks = 0

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeEmptyState(
                    title = "Empty baseline",
                    description = "No content is available.",
                    actionLabel = "Retry baseline",
                    onAction = { actionClicks++ },
                )
            }
        }

        composeTestRule.onNodeWithText("Empty baseline").assertIsDisplayed()
        composeTestRule.onNodeWithText("No content is available.").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Retry baseline")
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, actionClicks)
    }

    @Test
    fun emptyStateHidesActionWhenCallbackIsNull() {
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeEmptyState(
                    title = "Empty baseline",
                    actionLabel = "Unavailable action",
                )
            }
        }

        composeTestRule.onAllNodesWithText("Unavailable action").assertCountEquals(0)
    }

    @Test
    fun emptyStateHidesActionWhenLabelIsNull() {
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeEmptyState(
                    title = "Empty baseline",
                    onAction = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Empty baseline").assertIsDisplayed()
    }

    @Test
    fun successStateDisplaysTitle() {
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeSuccessState(title = "Success baseline")
            }
        }

        composeTestRule.onNodeWithText("Success baseline").assertIsDisplayed()
    }

    @Test
    fun warningStateDisplaysTitle() {
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeWarningState(title = "Warning baseline")
            }
        }

        composeTestRule.onNodeWithText("Warning baseline").assertIsDisplayed()
    }

    @Test
    fun errorStateDisplaysTitle() {
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeErrorState(title = "Error baseline")
            }
        }

        composeTestRule.onNodeWithText("Error baseline").assertIsDisplayed()
    }
}
