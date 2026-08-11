package com.hoggamers.rankforge.presentation.navigation

import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.auth.AUTH_ACCOUNT_BACK_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.auth.AUTH_ACCOUNT_HOME_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.auth.AUTH_LOGOUT_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.auth.AUTH_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.auth.AuthUiState
import com.hoggamers.rankforge.data.export.GoogleSheetsStandingsExportExecutionResult
import com.hoggamers.rankforge.data.export.GoogleSheetsStandingsExportRemoteDataSource
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.tournament.CreateTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.CreateMatchUseCase
import com.hoggamers.rankforge.domain.tournament.CreateMatchInput
import com.hoggamers.rankforge.domain.tournament.CreateMatchResult
import com.hoggamers.rankforge.domain.tournament.ConfirmTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterPlayersUseCase
import com.hoggamers.rankforge.domain.tournament.SaveRosterUseCase
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.presentation.screen.TEAM_ENTRY_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TEAM_ENTRY_ROSTER_BUTTON_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.TeamEntryViewModel
import com.hoggamers.rankforge.presentation.screen.ROSTER_ENTRY_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.RosterEntryViewModel
import com.hoggamers.rankforge.presentation.screen.RosterReviewViewModel
import com.hoggamers.rankforge.presentation.screen.ROSTER_REVIEW_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.ROSTER_REVIEW_CONFIRM_BUTTON_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.ROSTER_SCREENSHOT_CROP_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.ImageCandidateMetadataReader
import com.hoggamers.rankforge.presentation.screen.ImageCandidateReadResult
import com.hoggamers.rankforge.presentation.screen.ImageCandidateValidator
import com.hoggamers.rankforge.presentation.screen.ImageSourceFingerprintGenerator
import com.hoggamers.rankforge.presentation.screen.ImageSourceStreamOpener
import com.hoggamers.rankforge.presentation.screen.RosterScreenshotIntakeViewModel
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_DETAILS_NOT_FOUND_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_DETAILS_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_LIST_EMPTY_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_DRAWER_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_LIST_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.ALL_TOURNAMENTS_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.ALL_TOURNAMENTS_HOME_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.ALL_TOURNAMENTS_BACK_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TournamentDetailsViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentListViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentCreationViewModel
import com.hoggamers.rankforge.presentation.screen.MatchCreationViewModel
import com.hoggamers.rankforge.presentation.screen.MatchPlacementViewModel
import com.hoggamers.rankforge.presentation.screen.MatchKillViewModel
import com.hoggamers.rankforge.presentation.screen.MatchReviewViewModel
import com.hoggamers.rankforge.presentation.screen.MatchOcrReviewTestTags
import com.hoggamers.rankforge.presentation.screen.MatchOcrReviewViewModel
import com.hoggamers.rankforge.presentation.screen.MatchCorrectionViewModel
import com.hoggamers.rankforge.presentation.screen.MATCH_PLACEMENT_ACTION_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_PLACEMENT_FIELD_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_PLACEMENT_SAVE_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_PLACEMENT_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_KILLS_ACTION_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_KILL_FIELD_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_KILL_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_KILL_SAVE_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_ACTION_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_KILLS_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_DETAILS_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_FINALIZE_CONFIRM_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_CORRECTION_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_CORRECTION_HISTORY_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_ROW_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_CORRECTION_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_CORRECTION_KILLS_FIELD_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_CORRECTION_SUBMIT_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_CORRECTION_SUBMIT_CONFIRM_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_CORRECTION_DISCARD_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_CORRECTION_DISCARD_CONFIRM_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_VALIDATION_ISSUES_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_CREATION_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_CREATION_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_CREATE_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_MAP_FIELD_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_NUMBER_FIELD_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.CREATE_MATCH_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.OPEN_STANDINGS_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_STANDINGS_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_STANDING_ROW_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.TournamentStandingsViewModel
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueUseCase
import com.hoggamers.rankforge.domain.tournament.ClearDraftMatchUseCase
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchUseCase
import com.hoggamers.rankforge.domain.tournament.SubmitMatchCorrectionUseCase
import com.hoggamers.rankforge.domain.tournament.ClearMatchCorrectionDraftUseCase
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStandingsEngine
import com.hoggamers.rankforge.domain.tournament.TieBreakRules

@RunWith(AndroidJUnit4::class)
class RankForgeNavigationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        const val OPEN_ROSTER_SCREENSHOT_CROP_TEST_TAG = "open_roster_screenshot_crop"
    }

    @Test
    fun openingAuthWhileAlreadySignedInKeepsAccountDestinationOpen() {
        val repository = InMemoryTournamentRepository()
        val listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))
        val authUiState = AuthUiState(
            isSignedIn = true,
            accountEmail = "user@example.com",
        )

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    authUiState = authUiState,
                    listViewModel = listViewModel,
                )
            }
        }

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
            .performClick()
        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(AUTH_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_LOGOUT_ACTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(context.getString(R.string.tournament_list_title)).assertCountEquals(0)
    }

    @Test
    fun accountHomeReturnsToHomepageWithDrawerClosed() {
        val repository = InMemoryTournamentRepository()
        val listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    authUiState = AuthUiState(
                        isSignedIn = true,
                        accountEmail = "user@example.com",
                    ),
                    listViewModel = listViewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(AUTH_ACCOUNT_HOME_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_DRAWER_TEST_TAG)
        .assertIsNotDisplayed()
    }

    @Test
    fun accountBackReturnsToHomepageWithDrawerOpen() {
        val repository = InMemoryTournamentRepository()
        val listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    authUiState = AuthUiState(
                        isSignedIn = true,
                        accountEmail = "user@example.com",
                    ),
                    listViewModel = listViewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(AUTH_ACCOUNT_BACK_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_DRAWER_TEST_TAG).assertIsDisplayed()
    }

    @Test
fun accountSystemBackReturnsToHomepageWithDrawerOpen() {
    val repository = InMemoryTournamentRepository()
    val listViewModel = TournamentListViewModel(
        ObserveTournamentsUseCase(repository),
    )

    composeTestRule.setContent {
        RankForgeTheme {
            RankForgeNavHost(
                authUiState = AuthUiState(
                    isSignedIn = true,
                    accountEmail = "user@example.com",
                ),
                listViewModel = listViewModel,
            )
        }
    }

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
        .performClick()

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG)
        .performClick()

    composeTestRule.waitForIdle()

    composeTestRule.runOnIdle {
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG)
        .assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_DRAWER_TEST_TAG)
        .assertIsDisplayed()
}

   @Test
fun logoutFromAccountStaysOnAuthAndShowsSignedOutLogin() {
    val repository = InMemoryTournamentRepository()
    val listViewModel = TournamentListViewModel(
        ObserveTournamentsUseCase(repository),
    )
    var authUiState by mutableStateOf(
        AuthUiState(
            isSignedIn = true,
            accountEmail = "user@example.com",
        ),
    )

    composeTestRule.setContent {
        RankForgeTheme {
            RankForgeNavHost(
                authUiState = authUiState,
                onAuthLogout = {
                    authUiState = authUiState.copy(
                        isSignedIn = false,
                        accountEmail = null,
                    )
                },
                listViewModel = listViewModel,
            )
        }
    }

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
        .performClick()

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG)
        .performClick()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(AUTH_LOGOUT_ACTION_TEST_TAG)
        .performClick()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(AUTH_SCREEN_TEST_TAG)
        .assertIsDisplayed()

    composeTestRule
        .onNodeWithText(context.getString(R.string.auth_login_heading))
        .assertIsDisplayed()

    composeTestRule
        .onAllNodesWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG)
        .assertCountEquals(0)

    composeTestRule
        .onAllNodesWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
        .assertCountEquals(0)
}

   @Test
    fun allTournamentsMenuItemOpensDedicatedDestination() {
    val repository = InMemoryTournamentRepository()
    val listViewModel = TournamentListViewModel(
        ObserveTournamentsUseCase(repository),
    )

    composeTestRule.setContent {
        RankForgeTheme {
            RankForgeNavHost(
                authUiState = AuthUiState(isSignedIn = true),
                listViewModel = listViewModel,
            )
        }
    }

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
        .performClick()

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG)
        .performClick()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(ALL_TOURNAMENTS_SCREEN_TEST_TAG)
        .assertIsDisplayed()

    composeTestRule
        .onNodeWithText("All Tournaments")
        .assertIsDisplayed()

        composeTestRule
            .onAllNodesWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun allTournamentsUsesPreloadedSharedTournamentListState() {
        val repository = InMemoryTournamentRepository()
        val tournaments = (1..4).map { index ->
            confirmedTournament().copy(
                id = "test$index-id",
                name = "test $index",
            )
        }
        runBlocking {
            tournaments.forEach { repository.create(it) }
        }
        val listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    authUiState = AuthUiState(isSignedIn = true),
                    listViewModel = listViewModel,
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            listViewModel.uiState.value.tournaments.size == tournaments.size
        }
        composeTestRule.waitForIdle()
        listOf("test2-id", "test3-id", "test4-id").forEach { tournamentId ->
            composeTestRule
                .onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournamentId)
                .performScrollTo()
                .assertIsDisplayed()
        }
        composeTestRule
            .onAllNodesWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "test1-id")
            .assertCountEquals(0)

        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG).performClick()
        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onAllNodesWithTag(TOURNAMENT_LIST_EMPTY_TEST_TAG)
            .assertCountEquals(0)
        tournaments.forEach { tournament ->
            composeTestRule
                .onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournament.id)
                .performScrollTo()
                .assertIsDisplayed()
        }

        composeTestRule.onNodeWithTag(ALL_TOURNAMENTS_HOME_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()
        listOf("test2-id", "test3-id", "test4-id").forEach { tournamentId ->
            composeTestRule
                .onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournamentId)
                .performScrollTo()
                .assertIsDisplayed()
        }
        composeTestRule
            .onAllNodesWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "test1-id")
            .assertCountEquals(0)
    }

    @Test
    fun allTournamentsHomeReturnsToHomepageWithDrawerClosed() {
    val repository = InMemoryTournamentRepository()
    val listViewModel = TournamentListViewModel(
        ObserveTournamentsUseCase(repository),
    )

    composeTestRule.setContent {
        RankForgeTheme {
            RankForgeNavHost(
                authUiState = AuthUiState(isSignedIn = true),
                listViewModel = listViewModel,
            )
        }
    }

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
        .performClick()

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG)
        .performClick()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(ALL_TOURNAMENTS_HOME_ACTION_TEST_TAG)
        .performClick()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG)
        .assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_DRAWER_TEST_TAG)
        .assertIsNotDisplayed()
}

    @Test
    fun allTournamentsBackReturnsToHomepageWithDrawerOpen() {
        val repository = InMemoryTournamentRepository()
        val listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    authUiState = AuthUiState(isSignedIn = true),
                    listViewModel = listViewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ALL_TOURNAMENTS_BACK_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_DRAWER_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun allTournamentsSystemBackReturnsToHomepageWithDrawerOpen() {
        val repository = InMemoryTournamentRepository()
        val listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    authUiState = AuthUiState(isSignedIn = true),
                    listViewModel = listViewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_DRAWER_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun navigationMovesForwardAndBackThroughVisibleDestinations() {
        val viewModels = createNavigationViewModels()
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                )
            }
        }

        val listTitle = context.getString(R.string.tournament_list_title)
        val openAction = context.getString(R.string.open_tournament_creation)

        composeTestRule.onNodeWithText(listTitle).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG).assertCountEquals(0)

        composeTestRule.onNodeWithText(openAction).performClick()
        composeTestRule.onNodeWithTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG).assertIsDisplayed()

        pressBackOnMainThread()
        composeTestRule.onNodeWithText(listTitle).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun dirtyBackShowsConfirmationBeforeReturningToList() {
        val viewModels = createNavigationViewModels()
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.open_tournament_creation)).performClick()
        composeTestRule.runOnIdle { viewModels.creationViewModel.onTournamentNameChanged("Draft") }
        composeTestRule.waitForIdle()
        pressBackOnMainThread()

        composeTestRule.onNodeWithText(context.getString(R.string.keep_editing_action)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.discard_changes_action)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.tournament_list_title)).assertIsDisplayed()
    }

    @Test
    fun rosterScreenshotCropDestinationOpensAndBackReturnsToRosterReview() {
        val viewModels = createNavigationViewModels()
        createTournamentFromViewModel(viewModels.creationViewModel)
        val tournamentId = viewModels.listViewModel.uiState.value.tournaments.single().id
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            RankForgeTheme {
                navController = rememberNavController()
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, onOpenCropEditor ->
                        Button(
                            onClick = { onOpenCropEditor(1) },
                            modifier = Modifier.testTag(OPEN_ROSTER_SCREENSHOT_CROP_TEST_TAG),
                        ) {
                            Text("Open crop")
                        }
                    },
                    rosterScreenshotCropViewModelFactory = { createCropNavigationViewModel() },
                )
            }
        }

        composeTestRule.runOnIdle {
            navController.navigate(RosterReviewDestination(tournamentId))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ROSTER_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule.onNodeWithTag(OPEN_ROSTER_SCREENSHOT_CROP_TEST_TAG).performClick()

        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_CROP_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(ROSTER_REVIEW_SCREEN_TEST_TAG).assertCountEquals(0)

        pressBackOnMainThread()

        composeTestRule.onNodeWithTag(ROSTER_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun successfulCreationEntersSetupAndBackReturnsToCreatedTournamentDetails() {
        val viewModels = createNavigationViewModels()
        var loadedTeamEntryTournamentId: String? = null
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = { tournamentId ->
                        loadedTeamEntryTournamentId = tournamentId
                        viewModels.teamEntryViewModel(tournamentId)
                    },
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.open_tournament_creation)).performClick()
        viewModels.creationViewModel.onTournamentNameChanged("Summer Cup")
        viewModels.creationViewModel.onTournamentDateChanged(LocalDate.of(2026, 7, 24))
        viewModels.creationViewModel.onOrganizerNameChanged("Alex")
        viewModels.creationViewModel.onOrganizerContactNumberChanged("123")
        viewModels.creationViewModel.submit()
        composeTestRule.waitForIdle()
        val createdTournamentId = viewModels.listViewModel.uiState.value.tournaments.single().id

        composeTestRule.onAllNodesWithTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithTag(TEAM_ENTRY_SCREEN_TEST_TAG).assertIsDisplayed()
        assertEquals(createdTournamentId, loadedTeamEntryTournamentId)

        pressBackOnMainThread()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Summer Cup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Contact: 123").assertIsDisplayed()
        composeTestRule.onNodeWithText("Status: DRAFT").assertIsDisplayed()
    }

    @Test
    fun tappingCreatedTournamentOpensDetailsAndBackReturnsToList() {
        val viewModels = createNavigationViewModels()
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                )
            }
        }

        createTournamentFromViewModel(viewModels.creationViewModel)
        composeTestRule.waitForIdle()
        val createdTournamentId = viewModels.listViewModel.uiState.value.tournaments.single().id

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + createdTournamentId).performClick()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Summer Cup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Contact: 123").assertIsDisplayed()

        pressBackOnMainThread()
        composeTestRule.onNodeWithText(context.getString(R.string.tournament_list_title)).assertIsDisplayed()
    }

    @Test
    fun detailsEntryActionOpensTeamEntryAndBackReturnsToDetails() {
        val viewModels = createNavigationViewModels()
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                )
            }
        }

        createTournamentFromViewModel(viewModels.creationViewModel)
        composeTestRule.waitForIdle()
        val createdTournamentId = viewModels.listViewModel.uiState.value.tournaments.single().id

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + createdTournamentId).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.enter_teams_action)).performClick()
        composeTestRule.onNodeWithTag(TEAM_ENTRY_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(TEAM_ENTRY_ROSTER_BUTTON_TEST_TAG_PREFIX + 1)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ROSTER_ENTRY_SCREEN_TEST_TAG).assertIsDisplayed()

        pressBackOnMainThread()
        composeTestRule.onNodeWithTag(TEAM_ENTRY_SCREEN_TEST_TAG).assertIsDisplayed()
        pressBackOnMainThread()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun rosterConfirmationReturnsToSameTournamentDetails() {
        val viewModels = createNavigationViewModels()
        val tournamentId = "setup-id"
        runBlocking {
            createValidRoster(viewModels.repository, tournamentId)
        }
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournamentId).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.enter_teams_action)).performClick()
        composeTestRule
            .onNodeWithText(context.getString(R.string.review_roster_action))
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(ROSTER_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(ROSTER_REVIEW_CONFIRM_BUTTON_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag(ROSTER_REVIEW_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Setup Cup").assertIsDisplayed()
        assertEquals(
            TournamentStatus.CONFIRMED,
            runBlocking { viewModels.repository.observeById(tournamentId).first()?.status },
        )
    }

    @Test
    fun unknownDetailsIdShowsNotFoundStateWithoutCrashing() {
        val viewModels = createNavigationViewModels()
        composeTestRule.setContent {
            val navController = rememberNavController()
            RankForgeTheme {
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                )
            }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.navigate(TournamentDetailsDestination("missing"))
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_NOT_FOUND_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.tournament_not_found_title)).assertIsDisplayed()
    }

    @Test
    fun directMatchOcrReviewRouteDisplaysEmptyStateAndBackReturnsToReviewFallback() {
        val viewModels = createNavigationViewModels()
        val matchId = runBlocking {
            viewModels.repository.create(confirmedTournament())
            (
                CreateMatchUseCase(viewModels.repository)(
                    CreateMatchInput(
                        tournamentId = "confirmed-id",
                        matchNumber = "1",
                        date = LocalDate.of(2026, 7, 24),
                        mapName = "Bermuda",
                    ),
                ) as CreateMatchResult.Created
            ).match.id
        }
        composeTestRule.setContent {
            val navController = rememberNavController()
            RankForgeTheme {
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchOcrReviewViewModelFactory = viewModels.matchOcrReviewViewModel,
                )
            }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.navigate(
                    MatchOcrReviewDestination(
                        tournamentId = "confirmed-id",
                        matchId = matchId,
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.EMPTY).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.match_ocr_review_empty_title)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.BACK_ACTION).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun confirmedTournamentDetailsNavigatesToMatchCreation() {
        val viewModels = createNavigationViewModels()
        runBlocking {
            viewModels.repository.create(confirmedTournament())
        }
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule
            .onNodeWithTag(CREATE_MATCH_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_CREATION_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.match_number_label)).assertIsDisplayed()
    }

    @Test
    fun manualMatchFlowMovesFromCreationToReviewAndSameTournamentDetails() {
        val viewModels = createNavigationViewModels()
        var activeMatchCreationViewModel: MatchCreationViewModel? = null
        var cachedMatchCreationViewModel: MatchCreationViewModel? = null
        runBlocking {
            viewModels.repository.create(confirmedTournament())
        }
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = { tournamentId ->
                        val viewModel = cachedMatchCreationViewModel
                            ?: viewModels.matchCreationViewModel(tournamentId).also {
                                cachedMatchCreationViewModel = it
                            }
                        activeMatchCreationViewModel = viewModel
                        viewModel
                    },
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule
            .onNodeWithTag(CREATE_MATCH_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_CREATION_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_NUMBER_FIELD_TEST_TAG).performTextInput("1")
        composeTestRule.runOnIdle {
            activeMatchCreationViewModel?.onMatchDateChanged(LocalDate.of(2026, 7, 24))
        }
        composeTestRule.onNodeWithTag(MATCH_MAP_FIELD_TEST_TAG).performTextInput("Bermuda")
        composeTestRule.onNodeWithTag(MATCH_CREATE_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_PLACEMENT_SCREEN_TEST_TAG).assertIsDisplayed()
        val matchId = runBlocking {
            viewModels.repository.observeMatchesByTournamentId("confirmed-id").first().single().id
        }
        (1..12).forEach { slotNumber ->
            composeTestRule
                .onNodeWithTag(MATCH_PLACEMENT_FIELD_TEST_TAG_PREFIX + slotNumber)
                .performScrollTo()
                .performTextInput(slotNumber.toString())
        }
        composeTestRule
            .onNodeWithTag(MATCH_PLACEMENT_SAVE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_KILL_SCREEN_TEST_TAG).assertIsDisplayed()
        (1..12).forEach { slotNumber ->
            composeTestRule
                .onNodeWithTag(MATCH_KILL_FIELD_TEST_TAG_PREFIX + slotNumber)
                .performScrollTo()
                .performTextInput((slotNumber - 1).toString())
        }
        composeTestRule
            .onNodeWithTag(MATCH_KILL_SAVE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        assertEquals(
            (1..12).map { MatchPlacement(it, it) },
            runBlocking { viewModels.repository.observeMatchById(matchId).first()?.placements },
        )
        assertEquals(
            (1..12).map { MatchKill(it, it - 1) },
            runBlocking { viewModels.repository.observeMatchById(matchId).first()?.kills },
        )
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_DETAILS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule
            .onNodeWithTag(CREATE_MATCH_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun manualMatchFlowFinalizesAndOpensUpdatedSameTournamentStandings() {
        val viewModels = createNavigationViewModels()
        var activeMatchCreationViewModel: MatchCreationViewModel? = null
        var cachedMatchCreationViewModel: MatchCreationViewModel? = null
        var activeMatchReviewViewModel: MatchReviewViewModel? = null
        var cachedMatchReviewViewModel: MatchReviewViewModel? = null
        runBlocking {
            viewModels.repository.create(confirmedTournament())
        }
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = { tournamentId ->
                        val viewModel = cachedMatchCreationViewModel
                            ?: viewModels.matchCreationViewModel(tournamentId).also {
                                cachedMatchCreationViewModel = it
                            }
                        activeMatchCreationViewModel = viewModel
                        viewModel
                    },
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = { tournamentId, matchId ->
                        val viewModel = cachedMatchReviewViewModel
                            ?: viewModels.matchReviewViewModel(tournamentId, matchId).also {
                                cachedMatchReviewViewModel = it
                            }
                        activeMatchReviewViewModel = viewModel
                        viewModel
                    },
                    standingsViewModelFactory = viewModels.standingsViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule
            .onNodeWithTag(CREATE_MATCH_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_CREATION_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_NUMBER_FIELD_TEST_TAG).performTextInput("1")
        composeTestRule.runOnIdle {
            activeMatchCreationViewModel?.onMatchDateChanged(LocalDate.of(2026, 7, 24))
        }
        composeTestRule.onNodeWithTag(MATCH_MAP_FIELD_TEST_TAG).performTextInput("Bermuda")
        composeTestRule.onNodeWithTag(MATCH_CREATE_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()

        (1..12).forEach { slotNumber ->
            composeTestRule
                .onNodeWithTag(MATCH_PLACEMENT_FIELD_TEST_TAG_PREFIX + slotNumber)
                .performScrollTo()
                .performTextInput(slotNumber.toString())
        }
        composeTestRule
            .onNodeWithTag(MATCH_PLACEMENT_SAVE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        (1..12).forEach { slotNumber ->
            composeTestRule
                .onNodeWithTag(MATCH_KILL_FIELD_TEST_TAG_PREFIX + slotNumber)
                .performScrollTo()
                .performTextInput(if (slotNumber == 2) "10" else (slotNumber - 1).toString())
        }
        composeTestRule
            .onNodeWithTag(MATCH_KILL_SAVE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZE_CONFIRM_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG).performScrollTo().assertIsDisplayed()

        val match = runBlocking {
            viewModels.repository.observeMatchesByTournamentId("confirmed-id").first().single()
        }
        assertEquals("confirmed-id", match.tournamentId)
        assertEquals(com.hoggamers.rankforge.domain.tournament.MatchStatus.FINALIZED, match.status)
        assertEquals("confirmed-id", activeMatchReviewViewModel?.uiState?.value?.tournamentId)
        assertEquals(match.id, activeMatchReviewViewModel?.uiState?.value?.matchId)

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_DETAILS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Status: FINALIZED").performScrollTo().assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(OPEN_STANDINGS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_STANDINGS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_STANDING_ROW_TEST_TAG_PREFIX + "2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total points: 19").assertIsDisplayed()
    }

    @Test
    fun draftMatchDetailsNavigatesToPlacementEntry() {
        val viewModels = createNavigationViewModels()
        val matchId = runBlocking {
            viewModels.repository.create(confirmedTournament())
            (
                CreateMatchUseCase(viewModels.repository)(
                    CreateMatchInput(
                        tournamentId = "confirmed-id",
                        matchNumber = "1",
                        date = LocalDate.of(2026, 7, 24),
                        mapName = "Bermuda",
                    ),
                ) as CreateMatchResult.Created
            ).match.id
        }
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule
            .onNodeWithTag(MATCH_PLACEMENT_ACTION_TEST_TAG_PREFIX + "1")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_PLACEMENT_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MATCH_PLACEMENT_FIELD_TEST_TAG_PREFIX + "1")
            .assertIsDisplayed()
        assert(matchId.isNotBlank())
    }

    @Test
    fun draftMatchDetailsNavigatesToKillEntry() {
        val viewModels = createNavigationViewModels()
        runBlocking {
            viewModels.repository.create(confirmedTournament())
            CreateMatchUseCase(viewModels.repository)(
                CreateMatchInput(
                    tournamentId = "confirmed-id",
                    matchNumber = "1",
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                ),
            )
        }
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchCorrectionViewModelFactory = viewModels.matchCorrectionViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule
            .onNodeWithTag(MATCH_KILLS_ACTION_TEST_TAG_PREFIX + "1")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_KILL_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MATCH_KILL_FIELD_TEST_TAG_PREFIX + "1")
            .assertIsDisplayed()
    }

    @Test
    fun draftMatchReviewNavigatesToPlacementKillsAndDetails() {
        val viewModels = createNavigationViewModels()
        runBlocking {
            viewModels.repository.create(confirmedTournament())
            CreateMatchUseCase(viewModels.repository)(
                CreateMatchInput(
                    tournamentId = "confirmed-id",
                    matchNumber = "1",
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                ),
            )
        }
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchCorrectionViewModelFactory = viewModels.matchCorrectionViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_ACTION_TEST_TAG_PREFIX + "1")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_PLACEMENT_SCREEN_TEST_TAG).assertIsDisplayed()
        pressBackOnMainThread()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_KILL_SCREEN_TEST_TAG).assertIsDisplayed()
        pressBackOnMainThread()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_DETAILS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun linkedDraftScreenshotOpensOcrReviewAndReturnsToSameReviewAndDetails() {
        val viewModels = createNavigationViewModels()
        var activeMatchReviewViewModel: MatchReviewViewModel? = null
        val cachedMatchReviewViewModels = mutableMapOf<String, MatchReviewViewModel>()
        runBlocking {
            viewModels.repository.create(confirmedTournament())
            CreateMatchUseCase(viewModels.repository)(
                CreateMatchInput(
                    tournamentId = "confirmed-id",
                    matchNumber = "1",
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                ),
            )
        }
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = { tournamentId, matchId ->
                        cachedMatchReviewViewModels.getOrPut("$tournamentId:$matchId") {
                            viewModels.matchReviewViewModel(tournamentId, matchId)
                        }.also {
                            activeMatchReviewViewModel = it
                        }
                    },
                    matchOcrReviewViewModelFactory = viewModels.matchOcrReviewViewModel,
                    matchCorrectionViewModelFactory = viewModels.matchCorrectionViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_ACTION_TEST_TAG_PREFIX + "1")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule.runOnIdle {
            activeMatchReviewViewModel?.onPhotoPickerResult("content://picker/ocr")
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            activeMatchReviewViewModel?.linkScreenshot()
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            activeMatchReviewViewModel?.uiState?.value?.canOpenOcrReview == true
        }
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.SCREEN).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.EMPTY).assertIsDisplayed()

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.BACK_ACTION).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_DETAILS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule
            .onNodeWithTag(CREATE_MATCH_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun finalizedMatchReviewDoesNotOfferPlacementOrKillEditing() {
        val viewModels = createNavigationViewModels()
        runBlocking {
            viewModels.repository.create(confirmedTournament())
            val match = (
                CreateMatchUseCase(viewModels.repository)(
                    CreateMatchInput(
                        tournamentId = "confirmed-id",
                        matchNumber = "1",
                        date = LocalDate.of(2026, 7, 24),
                        mapName = "Bermuda",
                    ),
                ) as CreateMatchResult.Created
            ).match
            viewModels.repository.finalizeDraftMatch(
                matchId = match.id,
                placements = (1..12).map { MatchPlacement(it, it) },
                kills = (1..12).map { MatchKill(it, it - 1) },
            )
        }
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchCorrectionViewModelFactory = viewModels.matchCorrectionViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_ACTION_TEST_TAG_PREFIX + "1")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_CORRECTION_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("This opens an editable correction copy. The finalized result stays unchanged until you submit it.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Start correction").performClick()
        composeTestRule.onNodeWithTag(MATCH_CORRECTION_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_CORRECTION_DISCARD_ACTION_TEST_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MATCH_CORRECTION_DISCARD_CONFIRM_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_DETAILS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("Status: FINALIZED").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_PLACEMENT_ACTION_TEST_TAG_PREFIX + "1").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_KILLS_ACTION_TEST_TAG_PREFIX + "1").assertCountEquals(0)
    }

    @Test
    fun finalizedMatchCorrectionReturnsToCorrectedReadOnlyReview() {
        val viewModels = createNavigationViewModels()
        runBlocking {
            viewModels.repository.create(confirmedTournament())
            val match = (
                CreateMatchUseCase(viewModels.repository)(
                    CreateMatchInput(
                        tournamentId = "confirmed-id",
                        matchNumber = "1",
                        date = LocalDate.of(2026, 7, 24),
                        mapName = "Bermuda",
                    ),
                ) as CreateMatchResult.Created
            ).match
            viewModels.repository.finalizeDraftMatch(
                matchId = match.id,
                placements = (1..12).map { MatchPlacement(it, it) },
                kills = (1..12).map { MatchKill(it, it - 1) },
            )
        }
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchCorrectionViewModelFactory = viewModels.matchCorrectionViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_ACTION_TEST_TAG_PREFIX + "1")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG).performScrollTo().assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_CORRECTION_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("Start correction").performClick()
        composeTestRule.onNodeWithTag(MATCH_CORRECTION_SCREEN_TEST_TAG).assertIsDisplayed()

        val correctionKillsField = composeTestRule
            .onNodeWithTag(MATCH_CORRECTION_KILLS_FIELD_TEST_TAG_PREFIX + "1")
            .performScrollTo()
        correctionKillsField.performTextClearance()
        correctionKillsField.performTextInput("5")
        composeTestRule
            .onNodeWithTag(MATCH_CORRECTION_SUBMIT_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("This replaces the current finalized result and preserves the previous result in correction history.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_CORRECTION_SUBMIT_CONFIRM_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_ROW_TEST_TAG_PREFIX + "1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNode(
            hasText("Kills: 5", substring = false) and
                hasAnyAncestor(hasTestTag(MATCH_REVIEW_ROW_TEST_TAG_PREFIX + "1")),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_CORRECTION_HISTORY_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Corrected result — Slot 1: placement 1, kills 5").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun draftMatchDetailsSurfacesMissingResultValidation() {
        val viewModels = createNavigationViewModels()
        runBlocking {
            viewModels.repository.create(confirmedTournament())
            CreateMatchUseCase(viewModels.repository)(
                CreateMatchInput(
                    tournamentId = "confirmed-id",
                    matchNumber = "1",
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                ),
            )
        }
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule
            .onNodeWithTag(MATCH_VALIDATION_ISSUES_TEST_TAG_PREFIX + "1")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                context.getString(
                    R.string.match_validation_issue,
                    1,
                    context.getString(R.string.match_validation_missing_placement),
                ),
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun draftMatchAllowsEnteringAndDisplayingAllKills() {
        val viewModels = createNavigationViewModels()
        runBlocking {
            viewModels.repository.create(confirmedTournament())
            CreateMatchUseCase(viewModels.repository)(
                CreateMatchInput(
                    tournamentId = "confirmed-id",
                    matchNumber = "1",
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                ),
            )
        }
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchCorrectionViewModelFactory = viewModels.matchCorrectionViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule
            .onNodeWithTag(MATCH_KILLS_ACTION_TEST_TAG_PREFIX + "1")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_KILL_SCREEN_TEST_TAG).assertIsDisplayed()

        (1..12).forEach { slotNumber ->
            composeTestRule
                .onNodeWithTag(MATCH_KILL_FIELD_TEST_TAG_PREFIX + slotNumber)
                .performScrollTo()
                .performTextInput((slotNumber - 1).toString())
        }
        composeTestRule
            .onNodeWithTag(MATCH_KILL_SAVE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    private class FakeGoogleSheetsStandingsExportRemoteDataSource :
        GoogleSheetsStandingsExportRemoteDataSource {
        override suspend fun export(
            tournamentId: String,
            rows: List<com.hoggamers.rankforge.domain.export.TournamentStandingsExportRow>,
        ): GoogleSheetsStandingsExportExecutionResult =
            GoogleSheetsStandingsExportExecutionResult.Success(
                exportedMatchCount = rows.firstOrNull()?.exportedMatchCount ?: 0,
                rowsWritten = rows.size,
            )
    }

    private fun pressBackOnMainThread() {
        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

    private fun createCropNavigationViewModel(): RosterScreenshotIntakeViewModel =
        RosterScreenshotIntakeViewModel(
            imageCandidateValidator = ImageCandidateValidator(
                ImageCandidateMetadataReader { ImageCandidateReadResult.Unreadable },
            ),
            fingerprintGenerator = ImageSourceFingerprintGenerator(
                streamOpener = ImageSourceStreamOpener { null },
            ),
        )

    private fun createNavigationViewModels(): NavigationViewModels {
        val repository = InMemoryTournamentRepository()
        return NavigationViewModels(
            repository = repository,
            creationViewModel = createCreationViewModel(repository),
            listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository)),
            detailsViewModel = { tournamentId ->
                TournamentDetailsViewModel(
                    getTournamentById = GetTournamentByIdUseCase(repository),
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    observeMatches = ObserveMatchesUseCase(repository),
                    observeRoster = ObserveRosterByTournamentUseCase(repository),
                    googleSheetsStandingsExport = FakeGoogleSheetsStandingsExportRemoteDataSource(),
                ).also {
                    it.load(tournamentId)
                }
            },
            standingsViewModel = { tournamentId ->
                TournamentStandingsViewModel(
                    observeMatches = ObserveMatchesUseCase(repository),
                    cumulativeStandings = CumulativeTournamentStandingsEngine(),
                    tieBreakRules = TieBreakRules(),
                ).also {
                    it.load(tournamentId)
                }
            },
            teamEntryViewModel = { tournamentId ->
                TeamEntryViewModel(
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    saveTeamSlotNames = SaveTeamSlotNamesUseCase(repository),
                    validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
                ).also {
                    it.load(tournamentId)
                }
            },
            rosterEntryViewModel = { tournamentId, slotNumber ->
                RosterEntryViewModel(
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    observeRosterPlayers = ObserveRosterPlayersUseCase(repository),
                    saveRoster = SaveRosterUseCase(repository),
                    rosterValidator = RosterValidator(),
                ).also {
                    it.load(tournamentId, slotNumber)
                }
            },
            rosterReviewViewModel = { tournamentId ->
                val validator = RosterValidator()
                RosterReviewViewModel(
                    getTournamentById = GetTournamentByIdUseCase(repository),
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    observeRosterPlayers = ObserveRosterPlayersUseCase(repository),
                    validateTournamentRoster = ValidateTournamentRosterUseCase(repository, validator),
                    confirmTournamentRoster = ConfirmTournamentRosterUseCase(
                        repository = repository,
                        validateTournamentRoster = ValidateTournamentRosterUseCase(repository, validator),
                    ),
                ).also {
                    it.load(tournamentId)
                }
            },
            matchCreationViewModel = { tournamentId ->
                MatchCreationViewModel(CreateMatchUseCase(repository)).also {
                    it.load(tournamentId)
                }
            },
            matchPlacementViewModel = { tournamentId, matchId ->
                MatchPlacementViewModel(
                    observeMatches = ObserveMatchesUseCase(repository),
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    observeRoster = ObserveRosterByTournamentUseCase(repository),
                    observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
                    saveMatchPlacements = SaveMatchPlacementsUseCase(repository),
                    saveDraftValue = SaveMatchDraftValueUseCase(repository),
                    clearDraftMatch = ClearDraftMatchUseCase(repository),
                ).also {
                    it.load(tournamentId, matchId)
                }
            },
            matchKillViewModel = { tournamentId, matchId ->
                MatchKillViewModel(
                    observeMatches = ObserveMatchesUseCase(repository),
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    observeRoster = ObserveRosterByTournamentUseCase(repository),
                    observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
                    saveMatchKills = SaveMatchKillsUseCase(repository),
                    saveDraftValue = SaveMatchDraftValueUseCase(repository),
                    clearDraftMatch = ClearDraftMatchUseCase(repository),
                ).also {
                    it.load(tournamentId, matchId)
                }
            },
            matchReviewViewModel = { tournamentId, matchId ->
                MatchReviewViewModel(
                    getTournamentById = GetTournamentByIdUseCase(repository),
                    observeMatches = ObserveMatchesUseCase(repository),
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    observeRoster = ObserveRosterByTournamentUseCase(repository),
                    observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
                    validateMatchResult = ValidateMatchResultUseCase(),
                    finalizeMatch = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase()),
                    imageCandidateValidator = com.hoggamers.rankforge.presentation.screen.ImageCandidateValidator(
                        com.hoggamers.rankforge.presentation.screen.ImageCandidateMetadataReader {
                            com.hoggamers.rankforge.presentation.screen.ImageCandidateReadResult.Metadata(
                                "image/png",
                                width = 1080,
                                height = 1920,
                            )
                        },
                    ),
                    screenshotDuplicateDetector = com.hoggamers.rankforge.presentation.screen.ScreenshotDuplicateDetector(
                        com.hoggamers.rankforge.presentation.screen.ImageSourceFingerprintGenerator(
                            com.hoggamers.rankforge.presentation.screen.ImageSourceStreamOpener { uri ->
                                uri.encodeToByteArray().inputStream()
                            },
                            kotlinx.coroutines.Dispatchers.Unconfined,
                        ),
                    ),
                    localImagePreserver = com.hoggamers.rankforge.presentation.screen.LocalImagePreserver(
                        appPrivateRoot = context.filesDir,
                        sourceStreamOpener = com.hoggamers.rankforge.presentation.screen.ImageSourceStreamOpener { uri ->
                            uri.encodeToByteArray().inputStream()
                        },
                        mimeTypeReader = com.hoggamers.rankforge.presentation.screen.ImageSourceMimeTypeReader {
                            "image/png"
                        },
                        ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
                    ),
                ).also {
                    it.load(tournamentId, matchId)
                }
            },
            matchOcrReviewViewModel = { tournamentId, matchId ->
                MatchOcrReviewViewModel(
                    FinalizeOcrCorrectionMatchUseCase(
                        repository,
                        FinalizeMatchUseCase(repository, ValidateMatchResultUseCase()),
                    ),
                ).also {
                    it.load(tournamentId, matchId)
                }
            },
            matchCorrectionViewModel = { tournamentId, matchId ->
                MatchCorrectionViewModel(
                    observeMatches = ObserveMatchesUseCase(repository),
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    observeRoster = ObserveRosterByTournamentUseCase(repository),
                    observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
                    validateMatchResult = ValidateMatchResultUseCase(),
                    submitCorrection = SubmitMatchCorrectionUseCase(
                        repository,
                        ValidateMatchResultUseCase(),
                        com.hoggamers.rankforge.domain.tournament.ProtectedMatchCorrectionAction {
                            com.hoggamers.rankforge.domain.tournament.ProtectedMatchCorrectionResult.Success(2)
                        },
                    ),
                    saveDraftValue = SaveMatchDraftValueUseCase(repository),
                    clearCorrectionDraft = ClearMatchCorrectionDraftUseCase(repository),
                ).also {
                    it.load(tournamentId, matchId)
                }
            },
        )
    }

    private suspend fun createValidRoster(
        repository: InMemoryTournamentRepository,
        tournamentId: String,
    ) {
        repository.create(
            Tournament(
                id = tournamentId,
                name = "Setup Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Alex",
                organizerContactNumber = "123",
                status = TournamentStatus.DRAFT,
            ),
        )
        SaveTeamSlotNamesUseCase(repository)(
            tournamentId,
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        val saveRoster = SaveRosterUseCase(repository)
        (1..12).forEach { slotNumber ->
            saveRoster(
                tournamentId = tournamentId,
                slotNumber = slotNumber,
                players = (0..3).map { playerIndex ->
                    RosterPlayer.create(tournamentId, slotNumber, "Player $playerIndex")
                },
            )
        }
    }

    private fun confirmedTournament() = Tournament(
        id = "confirmed-id",
        name = "Confirmed Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Alex",
        organizerContactNumber = "123",
        status = TournamentStatus.CONFIRMED,
    )

    private fun createCreationViewModel(repository: InMemoryTournamentRepository): TournamentCreationViewModel {
        val today = LocalDate.of(2026, 7, 24)
        return TournamentCreationViewModel(
            CreateTournamentUseCase(
                repository = repository,
                clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            ),
        )
    }

    private fun createTournamentFromViewModel(viewModel: TournamentCreationViewModel) {
        viewModel.onTournamentNameChanged("Summer Cup")
        viewModel.onTournamentDateChanged(LocalDate.of(2026, 7, 24))
        viewModel.onOrganizerNameChanged("Alex")
        viewModel.onOrganizerContactNumberChanged("123")
        viewModel.submit()
    }

    private data class NavigationViewModels(
        val repository: InMemoryTournamentRepository,
        val creationViewModel: TournamentCreationViewModel,
        val listViewModel: TournamentListViewModel,
        val detailsViewModel: (String) -> TournamentDetailsViewModel,
        val standingsViewModel: (String) -> TournamentStandingsViewModel,
        val teamEntryViewModel: (String) -> TeamEntryViewModel,
        val rosterEntryViewModel: (String, Int) -> RosterEntryViewModel,
        val rosterReviewViewModel: (String) -> RosterReviewViewModel,
        val matchCreationViewModel: (String) -> MatchCreationViewModel,
        val matchPlacementViewModel: (String, String) -> MatchPlacementViewModel,
        val matchKillViewModel: (String, String) -> MatchKillViewModel,
        val matchReviewViewModel: (String, String) -> MatchReviewViewModel,
        val matchOcrReviewViewModel: (String, String) -> MatchOcrReviewViewModel,
        val matchCorrectionViewModel: (String, String) -> MatchCorrectionViewModel,
    )
}
