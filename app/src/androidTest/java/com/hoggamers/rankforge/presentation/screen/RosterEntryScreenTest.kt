package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

@RunWith(AndroidJUnit4::class)
class RosterEntryScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rosterRendersAccessiblePlayerControlsAndIncompleteCount() {
        composeTestRule.setContent {
            RankForgeTheme {
                RosterEntryScreen(
                    uiState = stateWithPlayers(2),
                    onPlayerNameChanged = { _, _ -> },
                    onAddPlayer = {},
                    onRemovePlayer = {},
                    onSave = {},
                    onBackToTeamEntry = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(ROSTER_ENTRY_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.roster_player_count, 2)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.roster_incomplete_message)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.roster_player_name_label, 1)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.remove_player_action, 1)).assertIsDisplayed()
    }

    @Test
    fun addEditAndRemoveControlsUpdateDraftThroughCallbacks() {
        var state by mutableStateOf(stateWithPlayers(0))
        var saveCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                RosterEntryScreen(
                    uiState = state,
                    onPlayerNameChanged = { playerIndex, displayName ->
                        state = state.copy(
                            players = state.players.mapIndexed { index, player ->
                                if (index == playerIndex) player.copy(displayName = displayName) else player
                            },
                        )
                    },
                    onAddPlayer = {
                        state = state.copy(players = state.players + RosterPlayerUiState(""))
                    },
                    onRemovePlayer = { playerIndex ->
                        state = state.copy(players = state.players.filterIndexed { index, _ -> index != playerIndex })
                    },
                    onSave = { saveCount += 1 },
                    onBackToTeamEntry = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(ROSTER_ADD_PLAYER_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(ROSTER_PLAYER_INPUT_TEST_TAG_PREFIX + 0).performTextInput("Alpha")
        composeTestRule.onNodeWithTag(ROSTER_SAVE_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(ROSTER_REMOVE_PLAYER_BUTTON_TEST_TAG_PREFIX + 0).performClick()

        composeTestRule.runOnIdle {
            assertEquals(0, state.playerCount)
            assertEquals(1, saveCount)
        }
    }

    @Test
    fun addButtonIsDisabledAtSixPlayersAndMaximumStateIsShown() {
        composeTestRule.setContent {
            RankForgeTheme {
                RosterEntryScreen(
                    uiState = stateWithPlayers(6),
                    onPlayerNameChanged = { _, _ -> },
                    onAddPlayer = {},
                    onRemovePlayer = {},
                    onSave = {},
                    onBackToTeamEntry = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(ROSTER_ADD_PLAYER_BUTTON_TEST_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithText(context.getString(R.string.roster_maximum_message)).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("roster_player_count")
            .assertTextContains(context.getString(R.string.roster_player_count, 6))
    }

    private fun stateWithPlayers(count: Int) = RosterEntryUiState(
        isLoading = false,
        isAvailable = true,
        tournamentId = "stable-id",
        slotNumber = 2,
        players = List(count) { RosterPlayerUiState(displayName = "") },
    )
}
