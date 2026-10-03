package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.TournamentField
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentValidationError
import com.hoggamers.rankforge.domain.tournament.defaultGroupPairings
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

@RunWith(AndroidJUnit4::class)
class TournamentCreationScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun creationFormRendersLabelsAndAcceptsTournamentName() {
        var name by mutableStateOf("")

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCreationScreen(
                    uiState = TournamentCreationUiState(tournamentName = name),
                    onTournamentNameChanged = { name = it },
                    onStageNameChanged = {},
                    onOrganizerContactNumberChanged = {},
                    onSubmit = {},
                    onBackPressed = {},
                    onKeepEditing = {},
                    onDiscardChanges = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.tournament_name_label))
            .assertIsDisplayed()
            .performTextInput("Summer Cup")
        composeTestRule.onAllNodesWithText("Tournament Date").assertCountEquals(0)
        composeTestRule.onNodeWithText(context.getString(R.string.stage_name_label)).assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals("Summer Cup", name) }
    }

    @Test
    fun gameAndModeFieldsRenderTheirFixedValues() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCreationScreen(
                    uiState = TournamentCreationUiState(),
                    onTournamentNameChanged = {},
                    onStageNameChanged = {},
                    onOrganizerContactNumberChanged = {},
                    onSubmit = {},
                    onBackPressed = {},
                    onKeepEditing = {},
                    onDiscardChanges = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.tournament_game_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.tournament_game_free_fire_max))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_GAME_DROPDOWN_TEST_TAG)
            .assertIsDisplayed()
            .assertIsNotEnabled()

        composeTestRule.onNodeWithText(context.getString(R.string.tournament_mode_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.tournament_mode_squad)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_MODE_DROPDOWN_TEST_TAG)
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun groupRotationConfigurationRendersThreeGroupDefaults() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCreationScreen(
                    uiState = TournamentCreationUiState(
                        format = TournamentFormat.GROUP_ROTATION,
                        groupCount = 3,
                        selectedGroupPairings = defaultGroupPairings(3),
                    ),
                    onTournamentNameChanged = {},
                    onStageNameChanged = {},
                    onOrganizerContactNumberChanged = {},
                    onSubmit = {},
                    onBackPressed = {},
                    onKeepEditing = {},
                    onDiscardChanges = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_GROUP_ROTATION_CHECKBOX_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.tournament_group_count_three)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_GROUP_PAIRING_TEST_TAG_PREFIX + "A:B")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_GROUP_PAIRING_TEST_TAG_PREFIX + "A:C")
            .assertIsDisplayed()
    }

    @Test
    fun invalidStateDisplaysInlineValidation() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCreationScreen(
                    uiState = TournamentCreationUiState(
                        validationErrors = mapOf(
                            TournamentField.NAME to TournamentValidationError.REQUIRED,
                        ),
                    ),
                    onTournamentNameChanged = {},
                    onStageNameChanged = {},
                    onOrganizerContactNumberChanged = {},
                    onSubmit = {},
                    onBackPressed = {},
                    onKeepEditing = {},
                    onDiscardChanges = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.required_field_error)).assertIsDisplayed()
    }

    @Test
    fun submittingStateDisplaysLoadingMessage() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCreationScreen(
                    uiState = TournamentCreationUiState(isSubmitting = true),
                    onTournamentNameChanged = {},
                    onStageNameChanged = {},
                    onOrganizerContactNumberChanged = {},
                    onSubmit = {},
                    onBackPressed = {},
                    onKeepEditing = {},
                    onDiscardChanges = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.tournament_creation_submitting)).assertIsDisplayed()
    }

    @Test
    fun tournamentLimitStateDisplaysSpecificCreationMessage() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCreationScreen(
                    uiState = TournamentCreationUiState(
                        submissionError = TournamentCreationSubmissionError.TOURNAMENT_LIMIT_REACHED,
                    ),
                    onTournamentNameChanged = {},
                    onStageNameChanged = {},
                    onOrganizerContactNumberChanged = {},
                    onSubmit = {},
                    onBackPressed = {},
                    onKeepEditing = {},
                    onDiscardChanges = {},
                )
            }
        }

        composeTestRule.onNodeWithText(
            context.getString(R.string.tournament_creation_limit_reached_error),
        ).assertIsDisplayed()
    }

    @Test
    fun dirtyBackShowsDialogAndDiscardInvokesExitCallback() {
        var state by mutableStateOf(TournamentCreationUiState(tournamentName = "Draft"))
        var discardCount by mutableStateOf(0)

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCreationScreen(
                    uiState = state,
                    onTournamentNameChanged = { state = state.copy(tournamentName = it) },
                    onStageNameChanged = {},
                    onOrganizerContactNumberChanged = {},
                    onSubmit = {},
                    onBackPressed = { state = state.copy(showDiscardDialog = true) },
                    onKeepEditing = { state = state.copy(showDiscardDialog = false) },
                    onDiscardChanges = { discardCount++ },
                )
            }
        }

        pressBackOnMainThread()
        composeTestRule.onNodeWithText(context.getString(R.string.keep_editing_action)).assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.tournament_name_label)).assertIsDisplayed()

        pressBackOnMainThread()
        composeTestRule.onNodeWithText(context.getString(R.string.discard_changes_action)).performClick()
        composeTestRule.runOnIdle { assertEquals(1, discardCount) }
    }

    private fun pressBackOnMainThread() {
        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }
}
