package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomDesignSetupScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsOnlyTheFiveLabelFieldsAndUploadAction() {
        var uploadCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                CustomDesignSetupScreen(onUploadCustomDesign = { uploadCount++ })
            }
        }

        listOf(
            CUSTOM_DESIGN_TEAM_NAME_FIELD_TEST_TAG,
            CUSTOM_DESIGN_WIN_FIELD_TEST_TAG,
            CUSTOM_DESIGN_TOTAL_KILLS_FIELD_TEST_TAG,
            CUSTOM_DESIGN_POSITION_POINTS_FIELD_TEST_TAG,
            CUSTOM_DESIGN_TOTAL_POINTS_FIELD_TEST_TAG,
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
        composeTestRule
            .onNodeWithTag(CUSTOM_DESIGN_UPLOAD_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle { assertEquals(1, uploadCount) }
    }
}
