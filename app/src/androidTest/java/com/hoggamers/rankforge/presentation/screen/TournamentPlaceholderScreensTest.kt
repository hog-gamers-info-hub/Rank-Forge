package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.TournamentField
import com.hoggamers.rankforge.domain.tournament.TournamentValidationError
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

@RunWith(AndroidJUnit4::class)
class TournamentCreationScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

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
                    onTournamentDateChanged = {},
                    onOrganizerNameChanged = {},
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
        composeTestRule.onNodeWithText(context.getString(R.string.tournament_date_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.organizer_name_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.organizer_contact_number_label)).assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals("Summer Cup", name) }
    }

    @Test
    fun gameAndModeDropdownsRenderWithTheirAvailableOptions() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCreationScreen(
                    uiState = TournamentCreationUiState(),
                    onTournamentNameChanged = {},
                    onTournamentDateChanged = {},
                    onOrganizerNameChanged = {},
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
        composeTestRule.onNodeWithTag(TOURNAMENT_GAME_DROPDOWN_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(TOURNAMENT_GAME_OPTION_FREE_FIRE_MAX_TEST_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.tournament_mode_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.tournament_mode_squad)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_MODE_DROPDOWN_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(TOURNAMENT_MODE_OPTION_SOLO_TEST_TAG)
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(TOURNAMENT_MODE_OPTION_DUO_TEST_TAG)
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(TOURNAMENT_MODE_OPTION_SQUAD_TEST_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
    }

    @Test
    fun dateFieldOpensMaterialDatePicker() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCreationScreen(
                    uiState = TournamentCreationUiState(),
                    onTournamentNameChanged = {},
                    onTournamentDateChanged = {},
                    onOrganizerNameChanged = {},
                    onOrganizerContactNumberChanged = {},
                    onSubmit = {},
                    onBackPressed = {},
                    onKeepEditing = {},
                    onDiscardChanges = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_DATE_FIELD_TEST_TAG).performTouchInput {
            click(center)
        }
        composeTestRule.onNodeWithTag(TOURNAMENT_DATE_CONFIRM_ACTION_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun dateTrailingActionOpensMaterialDatePicker() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCreationScreen(
                    uiState = TournamentCreationUiState(),
                    onTournamentNameChanged = {},
                    onTournamentDateChanged = {},
                    onOrganizerNameChanged = {},
                    onOrganizerContactNumberChanged = {},
                    onSubmit = {},
                    onBackPressed = {},
                    onKeepEditing = {},
                    onDiscardChanges = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_DATE_TRAILING_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(TOURNAMENT_DATE_CONFIRM_ACTION_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun confirmingDatePickerSelectionPopulatesFormattedDate() {
        var selectedDate by mutableStateOf<LocalDate?>(null)

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCreationScreen(
                    uiState = TournamentCreationUiState(tournamentDate = selectedDate),
                    onTournamentNameChanged = {},
                    onTournamentDateChanged = { selectedDate = it },
                    onOrganizerNameChanged = {},
                    onOrganizerContactNumberChanged = {},
                    onSubmit = {},
                    onBackPressed = {},
                    onKeepEditing = {},
                    onDiscardChanges = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_DATE_FIELD_TEST_TAG).performTouchInput {
            click(center)
        }
        composeTestRule.onNodeWithTag(TOURNAMENT_DATE_CONFIRM_ACTION_TEST_TAG).performClick()

        val expectedDate = LocalDate.now()
        composeTestRule.onNodeWithText(expectedDate.format(dateFormatter)).assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(expectedDate, selectedDate) }
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
                    onTournamentDateChanged = {},
                    onOrganizerNameChanged = {},
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
    fun missingDateValidationDisplaysInlineError() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCreationScreen(
                    uiState = TournamentCreationUiState(
                        validationErrors = mapOf(
                            TournamentField.DATE to TournamentValidationError.REQUIRED,
                        ),
                    ),
                    onTournamentNameChanged = {},
                    onTournamentDateChanged = {},
                    onOrganizerNameChanged = {},
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
                    onTournamentDateChanged = {},
                    onOrganizerNameChanged = {},
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
                    onTournamentDateChanged = {},
                    onOrganizerNameChanged = {},
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
                    onTournamentDateChanged = { state = state.copy(tournamentDate = it) },
                    onOrganizerNameChanged = {},
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
