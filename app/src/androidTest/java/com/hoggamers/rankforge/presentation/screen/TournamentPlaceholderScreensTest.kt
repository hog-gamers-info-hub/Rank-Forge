package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TournamentPlaceholderScreensTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun tournamentListDisplaysFoundationContentAndCreateAction() {
        var createClicks = 0

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListPlaceholderScreen {
                    createClicks++
                }
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.foundation_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.foundation_description)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.foundation_version)).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.open_tournament_creation))
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, createClicks)
    }

    @Test
    fun tournamentCreationDisplaysPlaceholderContentAndBackAction() {
        var backClicks = 0

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCreationPlaceholderScreen {
                    backClicks++
                }
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.tournament_creation_placeholder_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.tournament_creation_placeholder_description))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.back_action))
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, backClicks)
    }
}
