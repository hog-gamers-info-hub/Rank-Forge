package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.presentation.auth.AuthUiState
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_DRAWER_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_NOTIFICATIONS_ITEM_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_SETTINGS_ITEM_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_SUBSCRIPTION_ITEM_TEST_TAG
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TournamentListAndDetailsScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun emptyListShowsCreateTournamentAction() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(),
                    authUiState = AuthUiState(isSignedIn = false),
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                    onOpenAuth = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.tournament_list_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.open_tournament_creation))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(TOURNAMENT_LIST_EMPTY_TEST_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun nonEmptyListShowsApprovedTournamentFieldsOnly() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(
                        tournaments = listOf(tournamentListItem()),
                    ),
                    authUiState = AuthUiState(isSignedIn = false),
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                    onOpenAuth = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Summer Cup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Date: 24 Jul 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Organizer: Alex").assertIsDisplayed()
        composeTestRule.onNodeWithText("Status: DRAFT").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Contact: 123").assertCountEquals(0)
    }

    @Test
    fun tappingListItemInvokesDetailsCallback() {
        var openedTournamentId: String? = null

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(
                        tournaments = listOf(tournamentListItem()),
                    ),
                    authUiState = AuthUiState(isSignedIn = false),
                    onCreateTournament = {},
                    onOpenTournamentDetails = { openedTournamentId = it },
                    onOpenAuth = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "stable-id")
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals("stable-id", openedTournamentId)
        }
    }

    @Test
    fun signedInHomepageShowsMenuAndSignedOutHomepageDoesNot() {
        val authUiState = mutableStateOf(
            AuthUiState(
                isSignedIn = true,
                accountEmail = "user@example.com",
            ),
        )

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(),
                    authUiState = authUiState.value,
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                    onOpenAuth = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
            .assertIsDisplayed()

        composeTestRule.runOnIdle {
            authUiState.value = AuthUiState(isSignedIn = false)
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onAllNodesWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun menuShowsEnabledAccountAndAllTournamentsAndDisabledFutureItems() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(),
                    authUiState = AuthUiState(isSignedIn = true),
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                    onOpenAuth = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
            .performClick()

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_DRAWER_TEST_TAG)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG)
            .assertIsEnabled()

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG)
            .assertIsEnabled()

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_SUBSCRIPTION_ITEM_TEST_TAG)
            .assertIsNotEnabled()

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_NOTIFICATIONS_ITEM_TEST_TAG)
            .assertIsNotEnabled()

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_SETTINGS_ITEM_TEST_TAG)
            .assertIsNotEnabled()
    }

    @Test
    fun accountMenuItemClosesDrawerAndInvokesAccountCallback() {
        var openAccountCount = 0

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(),
                    authUiState = AuthUiState(isSignedIn = true),
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                    onOpenAuth = { openAccountCount++ },
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
            assertEquals(1, openAccountCount)
        }

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_DRAWER_TEST_TAG)
            .assertIsNotDisplayed()
    }

    @Test
    fun allTournamentsMenuItemInvokesDedicatedPageCallback() {
        var openAllTournamentsCount = 0

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(),
                    authUiState = AuthUiState(isSignedIn = true),
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                    onOpenAuth = {},
                    onOpenAllTournaments = { openAllTournamentsCount += 1 },
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

        composeTestRule.runOnIdle {
            assertEquals(1, openAllTournamentsCount)
        }
    }

    @Test
    fun allTournamentsPageShowsApprovedContentWithoutHomepageControls() {
        composeTestRule.setContent {
            RankForgeTheme {
                AllTournamentsScreen(
                    uiState = TournamentListUiState(
                        tournaments = listOf(tournamentListItem()),
                    ),
                    restorationUiState = TournamentCloudRestorationUiState.Idle,
                    onHome = {},
                    onBack = {},
                    onOpenTournamentDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(ALL_TOURNAMENTS_HOME_ACTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ALL_TOURNAMENTS_BACK_ACTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ALL_TOURNAMENTS_LOCAL_HEADING_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Summer Cup").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ALL_TOURNAMENTS_CLOUD_HEADING_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(context.getString(R.string.tournament_list_title))
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText(context.getString(R.string.open_tournament_creation))
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText(context.getString(R.string.auth_account_section_title))
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun allTournamentsLocalTapPassesStableTournamentId() {
        var openedTournamentId: String? = null

        composeTestRule.setContent {
            RankForgeTheme {
                AllTournamentsScreen(
                    uiState = TournamentListUiState(
                        tournaments = listOf(tournamentListItem()),
                    ),
                    restorationUiState = null,
                    onHome = {},
                    onBack = {},
                    onOpenTournamentDetails = { openedTournamentId = it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "stable-id")
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals("stable-id", openedTournamentId)
        }
    }

    @Test
    fun allTournamentsPageKeepsCloudRestoreActionsAvailable() {
        val cloudTournamentId = "cloud-id"
        composeTestRule.setContent {
            RankForgeTheme {
                AllTournamentsScreen(
                    uiState = TournamentListUiState(),
                    restorationUiState = TournamentCloudRestorationUiState.Available(
                        listOf(
                            com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSummary(
                                id = cloudTournamentId,
                                name = "Cloud Cup",
                                date = "2026-07-24",
                                organizerName = "Organizer",
                                status = "draft",
                            ),
                        ),
                    ),
                    onHome = {},
                    onBack = {},
                    onOpenTournamentDetails = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_ITEM_TEST_TAG_PREFIX + cloudTournamentId)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun detailsScreenShowsAllApprovedFields() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = TournamentDetailsUiState(
                        isLoading = false,
                        tournament = tournamentDetailsItem(),
                    ),
                    onBackToList = {},
                    onEnterTeams = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Summer Cup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Date: 24 Jul 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Organizer: Alex").assertIsDisplayed()
        composeTestRule.onNodeWithText("Contact: 123").assertIsDisplayed()
        composeTestRule.onNodeWithText("Status: DRAFT").assertIsDisplayed()
    }

    @Test
    fun detailsScreenViewStandingsActionInvokesTournamentCallback() {
        var openedTournamentId: String? = null

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = TournamentDetailsUiState(
                        isLoading = false,
                        tournament = tournamentDetailsItem(),
                    ),
                    onBackToList = {},
                    onEnterTeams = {},
                    onOpenStandings = { openedTournamentId = it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(OPEN_STANDINGS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals("stable-id", openedTournamentId)
        }
    }

    @Test
    fun detailsScreenShowsAllTwelveEmptySlotsWithoutRosterControls() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = TournamentDetailsUiState(
                        isLoading = false,
                        tournament = tournamentDetailsItem(),
                    ),
                    onBackToList = {},
                    onEnterTeams = {},
                )
            }
        }

        (1..12).forEach { slotNumber ->
            composeTestRule
                .onNodeWithTag(TOURNAMENT_SLOT_ITEM_TEST_TAG_PREFIX + slotNumber)
                .performScrollTo()
                .assertIsDisplayed()

            composeTestRule
                .onNodeWithText("Slot $slotNumber")
                .performScrollTo()
                .assertIsDisplayed()
        }

        composeTestRule
            .onAllNodesWithText(context.getString(R.string.empty_team_slot_subtitle))
            .assertCountEquals(12)

        composeTestRule.onAllNodesWithText("Team name").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Player").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Edit").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Delete").assertCountEquals(0)
    }

    @Test
    fun detailsScreenShowsSavedTeamNameInSlotDisplayAndEntryAction() {
        var entryTournamentId: String? = null

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = TournamentDetailsUiState(
                        isLoading = false,
                        tournament = tournamentDetailsItem(
                            slots = listOf(
                                TeamSlotUiState(
                                    slotNumber = 1,
                                    teamName = "Alpha",
                                ),
                            ) + (2..12).map {
                                TeamSlotUiState(
                                    slotNumber = it,
                                    teamName = "",
                                )
                            },
                        ),
                    ),
                    onBackToList = {},
                    onEnterTeams = { entryTournamentId = it },
                )
            }
        }

        composeTestRule
            .onNodeWithText("Alpha")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(context.getString(R.string.enter_teams_action))
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals("stable-id", entryTournamentId)
        }
    }

    @Test
    fun detailsScreenShowsDraftMatchValidationIssues() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = TournamentDetailsUiState(
                        isLoading = false,
                        tournament = tournamentDetailsItem(
                            matches = listOf(
                                MatchUiState(
                                    id = "match-id",
                                    matchNumber = 1,
                                    date = LocalDate.of(2026, 7, 24),
                                    mapName = "Bermuda",
                                    status = com.hoggamers.rankforge.domain.tournament.MatchStatus.DRAFT,
                                    validationIssues = listOf(
                                        MatchResultValidationIssueUiState(
                                            teamSlotNumber = 1,
                                            error = com.hoggamers.rankforge.domain.tournament.MatchResultValidationError.MISSING_PLACEMENT,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    onBackToList = {},
                    onEnterTeams = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(MATCH_VALIDATION_ISSUES_TEST_TAG_PREFIX + "1")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(
                context.getString(
                    R.string.match_validation_issue,
                    1,
                    context.getString(
                        R.string.match_validation_missing_placement,
                    ),
                ),
            )
            .assertExists()

        composeTestRule
            .onNodeWithTag(
                MATCH_VALIDATION_ISSUE_TEST_TAG_PREFIX +
                    "1_" +
                    com.hoggamers.rankforge.domain.tournament
                        .MatchResultValidationError.MISSING_PLACEMENT.name,
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun detailsNotFoundStateRendersSafeMessageAndAction() {
        var backCount = 0

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = TournamentDetailsUiState(
                        isLoading = false,
                    ),
                    onBackToList = { backCount++ },
                    onEnterTeams = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(TOURNAMENT_DETAILS_NOT_FOUND_TEST_TAG)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(
                context.getString(R.string.tournament_not_found_title),
            )
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(
                context.getString(R.string.back_to_tournament_list_action),
            )
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, backCount)
        }
    }

    private fun tournamentListItem() = TournamentListItemUiState(
        id = "stable-id",
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Alex",
        status = TournamentStatus.DRAFT,
    )

    private fun tournamentDetailsItem(
        slots: List<TeamSlotUiState> = (1..12).map {
            TeamSlotUiState(
                slotNumber = it,
                teamName = "",
            )
        },
        matches: List<MatchUiState> = emptyList(),
    ) = TournamentDetailsItemUiState(
        id = "stable-id",
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Alex",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        slots = slots,
        matches = matches,
    )
}
