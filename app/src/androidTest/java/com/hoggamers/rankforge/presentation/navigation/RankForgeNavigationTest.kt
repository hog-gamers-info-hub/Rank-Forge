package com.hoggamers.rankforge.presentation.navigation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RankForgeNavigationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun navigationMovesForwardAndBackThroughVisibleDestinations() {
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost()
            }
        }

        val listTitle = context.getString(R.string.foundation_title)
        val creationTitle = context.getString(R.string.tournament_creation_placeholder_title)
        val openAction = context.getString(R.string.open_tournament_creation)
        val backAction = context.getString(R.string.back_action)

        composeTestRule.onNodeWithText(listTitle).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(creationTitle).assertCountEquals(0)

        composeTestRule.onNodeWithText(openAction).performClick()
        composeTestRule.onNodeWithText(creationTitle).assertIsDisplayed()

        composeTestRule.onNodeWithText(backAction).performClick()
        composeTestRule.onNodeWithText(listTitle).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(creationTitle).assertCountEquals(0)
    }
}
