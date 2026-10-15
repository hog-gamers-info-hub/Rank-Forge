package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupIssue
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupIssueCode
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRotationTeamEntryScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun screenShowsPairingAndExactlyTwelveLocalLobbyInputs() {
        composeTestRule.setContent {
            RankForgeTheme {
                GroupRotationTeamEntryScreen(
                    uiState = state(),
                    onBackToDetails = {},
                    onPairingSelected = {},
                    onTeamNameChanged = { _, _, _ -> },
                    onBulkTeamNamesApplied = {},
                    onSave = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GROUP_ROTATION_TEAM_ENTRY_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(GROUP_ROTATION_TEAM_ENTRY_PAIRING_TEST_TAG_PREFIX + "A:B")
            .assertIsDisplayed()
        (1..12).forEach { lobbySlotNumber ->
            composeTestRule
                .onNodeWithTag(
                    GROUP_ROTATION_TEAM_ENTRY_INPUT_TEST_TAG_PREFIX + "A:B_$lobbySlotNumber",
                )
                .performScrollTo()
                .assertIsDisplayed()
        }
        composeTestRule
            .onNodeWithTag(GROUP_ROTATION_TEAM_ENTRY_UNIQUE_COUNT_TEST_TAG)
            .assertTextContains("Unique teams: 1 / 18")
        composeTestRule.onNodeWithText("A × B  1/12").assertIsDisplayed()
        composeTestRule.onNodeWithText("A × B").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("A:B").assertCountEquals(0)
        composeTestRule.onNodeWithText("01").assertIsDisplayed()
        composeTestRule.onNodeWithText("12").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pairingSelectionAndInputCallbacksExposePairingAndLobbySlot() {
        var selectedPairing: String? = null
        var changedPairing: String? = null
        var changedLobbySlot: Int? = null
        composeTestRule.setContent {
            RankForgeTheme {
                GroupRotationTeamEntryScreen(
                    uiState = state(),
                    onBackToDetails = {},
                    onPairingSelected = { selectedPairing = it.canonicalKey },
                    onTeamNameChanged = { pairing, slot, _ ->
                        changedPairing = pairing.canonicalKey
                        changedLobbySlot = slot
                    },
                    onBulkTeamNamesApplied = {},
                    onSave = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(GROUP_ROTATION_TEAM_ENTRY_PAIRING_TEST_TAG_PREFIX + "A:C")
            .performClick()
        composeTestRule
            .onNodeWithTag(GROUP_ROTATION_TEAM_ENTRY_INPUT_TEST_TAG_PREFIX + "A:B_1")
            .performTextInput("Changed")
        composeTestRule.runOnIdle {
            assertEquals("A:C", selectedPairing)
            assertEquals("A:B", changedPairing)
            assertEquals(1, changedLobbySlot)
        }
    }

    @Test
    fun pasteDialogOpensWithDisplayPairingLabel() {
        composeTestRule.setContent {
            RankForgeTheme {
                GroupRotationTeamEntryScreen(
                    uiState = state(),
                    onBackToDetails = {},
                    onPairingSelected = {},
                    onTeamNameChanged = { _, _, _ -> },
                    onBulkTeamNamesApplied = {},
                    onSave = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GROUP_ROTATION_TEAM_ENTRY_PASTE_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("Paste up to 12 team names for A × B. Remaining lobby rows are cleared.")
            .assertIsDisplayed()
    }

    @Test
    fun validationAndSaveErrorsAreDisplayed() {
        composeTestRule.setContent {
            RankForgeTheme {
                GroupRotationTeamEntryScreen(
                    uiState = state().copy(
                        validationIssues = listOf(
                            GroupRotationTeamSetupIssue(
                                GroupRotationTeamSetupIssueCode.BLANK_TEAM_NAME,
                                pairingKey = "A:B",
                            ),
                        ),
                        saveError = GroupRotationTeamEntrySaveError.ProtectedHistory,
                        cloudSyncError = GroupRotationTeamEntryCloudSyncError.QueuePersistenceFailed,
                    ),
                    onBackToDetails = {},
                    onPairingSelected = {},
                    onTeamNameChanged = { _, _, _ -> },
                    onBulkTeamNamesApplied = {},
                    onSave = {},
                )
            }
        }

        composeTestRule.onNodeWithText("All 12 names for A × B must be filled.").assertIsDisplayed()
        composeTestRule.onNodeWithText("This tournament has protected history.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Team setup was saved locally, but the cloud retry could not be queued.")
            .assertIsDisplayed()
    }

    @Test
    fun loadingAndInvalidDraftStatesAreBackOnly() {
        composeTestRule.setContent {
            RankForgeTheme {
                GroupRotationTeamEntryScreen(
                    uiState = GroupRotationTeamEntryUiState(),
                    onBackToDetails = {},
                    onPairingSelected = {},
                    onTeamNameChanged = { _, _, _ -> },
                    onBulkTeamNamesApplied = {},
                    onSave = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Loading team slots").assertIsDisplayed()

        composeTestRule.setContent {
            RankForgeTheme {
                GroupRotationTeamEntryScreen(
                    uiState = GroupRotationTeamEntryUiState(
                        isLoading = false,
                        loadError = GroupRotationTeamEntryLoadError.InvalidStoredDraft,
                    ),
                    onBackToDetails = {},
                    onPairingSelected = {},
                    onTeamNameChanged = { _, _, _ -> },
                    onBulkTeamNamesApplied = {},
                    onSave = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(GROUP_ROTATION_TEAM_ENTRY_LOAD_ERROR_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(GROUP_ROTATION_TEAM_ENTRY_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(GROUP_ROTATION_TEAM_ENTRY_SAVE_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun saveCallbackWorks() {
        var saveCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                GroupRotationTeamEntryScreen(
                    uiState = state(),
                    onBackToDetails = {},
                    onPairingSelected = {},
                    onTeamNameChanged = { _, _, _ -> },
                    onBulkTeamNamesApplied = {},
                    onSave = { saveCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(GROUP_ROTATION_TEAM_ENTRY_SAVE_TEST_TAG).performScrollTo().performClick()
        composeTestRule.runOnIdle { assertEquals(1, saveCount) }
    }

    private fun state(): GroupRotationTeamEntryUiState {
        val first = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        val second = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        fun section(pairing: GroupPairing, firstName: String = "") =
            GroupRotationPairingEntryUiState(
                pairing = pairing,
                rows = (1..12).map { lobbySlot ->
                    GroupRotationLobbyTeamUiState(
                        lobbySlotNumber = lobbySlot,
                        teamName = if (lobbySlot == 1) firstName else "",
                    )
                },
            )
        return GroupRotationTeamEntryUiState(
            isLoading = false,
            pairingSections = listOf(section(first, "Alpha"), section(second)),
            selectedPairingKey = first.canonicalKey,
            uniqueTeamCount = 1,
            maximumUniqueTeams = 18,
        )
    }
}
