package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
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
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_BACK_ITEM_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_DRAWER_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_NOTIFICATIONS_ITEM_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_SETTINGS_ITEM_TEST_TAG
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
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                    onOpenAuth = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.recent_tournaments_heading))
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
    fun homepageShowsAuthenticatedShell() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(),
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                    onOpenAuth = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun signedInHomeShowsRecentHeadingAndOmitsSignedOutContent() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(),
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                    onOpenAuth = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.open_tournament_creation))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.recent_tournaments_heading))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.tournament_list_title))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithTag(TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun signedInHomeShowsLastThreeTournamentsInCreationOrder() {
        val tournaments = listOf(
            tournamentListItem(id = "test1-id", name = "test 1"),
            tournamentListItem(id = "test2-id", name = "test 2"),
            tournamentListItem(id = "test3-id", name = "test 3"),
            tournamentListItem(id = "test4-id", name = "test 4"),
        )

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(tournaments = tournaments),
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                    onOpenAuth = {},
                )
            }
        }

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
    fun signedInRecentTournamentTapPassesStableTournamentId() {
        var openedTournamentId: String? = null

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(
                        tournaments = listOf(tournamentListItem()),
                    ),
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
    fun menuShowsEnabledAccountAndAllTournamentsAndDisabledFutureItems() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(),
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
            .onNodeWithTag(LOGGED_IN_HOME_BACK_ITEM_TEST_TAG)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG)
            .assertIsEnabled()

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG)
            .assertIsEnabled()

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_NOTIFICATIONS_ITEM_TEST_TAG)
            .assertIsNotEnabled()

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_SETTINGS_ITEM_TEST_TAG)
            .assertIsNotEnabled()
    }

    @Test
    fun accountMenuItemInvokesAccountCallbackSynchronously() {
        var openAccountCount = 0

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(),
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
    }

    @Test
    fun visibleBackItemClosesDrawerAndKeepsHomeVisible() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(),
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
            .onNodeWithTag(LOGGED_IN_HOME_BACK_ITEM_TEST_TAG)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(context.getString(R.string.recent_tournaments_heading))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_DRAWER_TEST_TAG)
            .assertIsNotDisplayed()
    }

    @Test
    fun systemBackClosesOpenDrawerAndKeepsHomeVisible() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(),
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                    onOpenAuth = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(context.getString(R.string.recent_tournaments_heading))
            .assertIsDisplayed()
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
        composeTestRule.onNodeWithText("Summer Cup").assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(context.getString(R.string.tournament_list_title))
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText(context.getString(R.string.open_tournament_creation))
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun allTournamentsPageShowsAllFourUniqueTournamentIds() {
        val tournaments = listOf(
            tournamentListItem(id = "test1-id", name = "test 1"),
            tournamentListItem(id = "test2-id", name = "test 2"),
            tournamentListItem(id = "test3-id", name = "test 3"),
            tournamentListItem(id = "test4-id", name = "test 4"),
        )

        composeTestRule.setContent {
            RankForgeTheme {
                AllTournamentsScreen(
                    uiState = TournamentListUiState(tournaments = tournaments),
                    restorationUiState = null,
                    onHome = {},
                    onBack = {},
                    onOpenTournamentDetails = {},
                )
            }
        }

        listOf("test1-id", "test2-id", "test3-id", "test4-id").forEach { tournamentId ->
            composeTestRule
                .onAllNodesWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournamentId)
                .assertCountEquals(1)
        }
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
    fun detailsScreenShowsSimplifiedHeaderAndHidesLegacyMetadata() {
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
        composeTestRule.onAllNodesWithText("Organizer: Alex").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Contact: 123").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Status: DRAFT").assertCountEquals(0)
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
    fun detailsScreenShowsNoTeamsWhenAllSlotsAreEmpty() {
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

        composeTestRule.onNodeWithText(context.getString(R.string.tournament_details_no_teams_saved)).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TOURNAMENT_SLOT_ITEM_TEST_TAG_PREFIX + "1").assertCountEquals(0)

        composeTestRule.onNodeWithText(context.getString(R.string.tournament_details_slot_list_title)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(context.getString(R.string.enter_teams_action)).assertCountEquals(1)
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
                                slots = (1..12).map { slotNumber ->
                                    TeamSlotUiState(
                                        slotNumber = slotNumber,
                                        teamName = when (slotNumber) {
                                            1 -> "Alpha"
                                            2 -> "HOG"
                                            else -> ""
                                        },
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
            .onNodeWithText("Slot 1 - Alpha")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Slot 2 - HOG").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Slot 10 - Team 10").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Slot 12 - Titan").assertCountEquals(0)

        composeTestRule
            .onNodeWithText(context.getString(R.string.enter_teams_action))
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals("stable-id", entryTournamentId)
        }
    }

    @Test
    fun detailsScreenHidesLegacyMatchDetailsAndShowsSimplifiedRows() {
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
            .onNodeWithText("Match 1 - In Progress")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_VALIDATION_ISSUES_TEST_TAG_PREFIX + "1").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Map: Bermuda").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Status: DRAFT").assertCountEquals(0)
        composeTestRule.onAllNodesWithText(context.getString(R.string.enter_match_placements_action)).assertCountEquals(0)
        composeTestRule.onAllNodesWithText(context.getString(R.string.enter_match_kills_action)).assertCountEquals(0)
        composeTestRule.onAllNodesWithText(context.getString(R.string.review_match_action)).assertCountEquals(0)
    }

    @Test
    fun existingMatchRowsShowChevronsAndOpenPersistedMatchIds() {
        val openedMatches = mutableListOf<Pair<String, String>>()
        val openedPlacements = mutableListOf<Pair<String, String>>()
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = TournamentDetailsUiState(
                        isLoading = false,
                        tournament = tournamentDetailsItem(
                            matches = listOf(
                                MatchUiState(
                                    id = "draft-match-id",
                                    matchNumber = 1,
                                    date = LocalDate.of(2026, 7, 24),
                                    mapName = "",
                                    status = com.hoggamers.rankforge.domain.tournament.MatchStatus.DRAFT,
                                ),
                                MatchUiState(
                                    id = "completed-match-id",
                                    matchNumber = 2,
                                    date = LocalDate.of(2026, 7, 24),
                                    mapName = "",
                                    status = com.hoggamers.rankforge.domain.tournament.MatchStatus.FINALIZED,
                                ),
                            ),
                        ),
                    ),
                    onBackToList = {},
                    onEnterTeams = {},
                    onEnterMatchPlacements = { tournamentId, matchId ->
                        openedPlacements += tournamentId to matchId
                    },
                    onReviewMatch = { tournamentId, matchId ->
                        openedMatches += tournamentId to matchId
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(MATCH_ITEM_CHEVRON_TEST_TAG_PREFIX + "1", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MATCH_ITEM_CHEVRON_TEST_TAG_PREFIX + "2", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_ITEM_TEST_TAG_PREFIX + "1").performClick()
        composeTestRule.onNodeWithTag(MATCH_ITEM_TEST_TAG_PREFIX + "2").performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    "stable-id" to "draft-match-id",
                    "stable-id" to "completed-match-id",
                ),
                openedMatches,
            )
            assertEquals(emptyList<Pair<String, String>>(), openedPlacements)
        }
    }

    @Test
    fun detailsScreenShowsCreateMatchForUnconfirmedTournament() {
        var createdTournamentId: String? = null

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = TournamentDetailsUiState(
                        isLoading = false,
                        tournament = tournamentDetailsItem(
                            slots = (1..8).map { slotNumber ->
                                TeamSlotUiState(slotNumber, "Team $slotNumber")
                            } + (9..12).map { slotNumber -> TeamSlotUiState(slotNumber, "") },
                            status = TournamentStatus.DRAFT,
                            matches = listOf(
                                MatchUiState(
                                    id = "finalized-id",
                                    matchNumber = 1,
                                    date = LocalDate.of(2026, 7, 24),
                                    mapName = "Bermuda",
                                    status = com.hoggamers.rankforge.domain.tournament.MatchStatus.FINALIZED,
                                ),
                            ),
                        ),
                    ),
                    onBackToList = {},
                    onEnterTeams = {},
                    onCreateMatch = { createdTournamentId = it },
                )
            }
        }

        composeTestRule.onNodeWithText("MATCHES").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Match 1 - Completed").performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.calculate_points_action))
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals("stable-id", createdTournamentId)
        }
        composeTestRule.onAllNodesWithText(context.getString(R.string.matches_require_confirmed_roster_message)).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("+ Calculate New Match").assertCountEquals(0)
    }

    @Test
    fun calculatePointsConfirmationShowsDynamicActionsOnDetails() {
        var cancelled = false
        var usedTeams = false
        var usedDefaults = false

        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = TournamentDetailsUiState(
                        isLoading = false,
                        tournament = tournamentDetailsItem(
                            slots = (1..8).map { TeamSlotUiState(it, "Team $it") },
                        ),
                        pendingTeamCountConfirmation = TeamCountConfirmationUiState(8, 4),
                    ),
                    onBackToList = {},
                    onEnterTeams = {},
                    onUseEnteredTeams = { usedTeams = true },
                    onUseDefaults = { usedDefaults = true },
                    onCancelTeamCountConfirmation = { cancelled = true },
                )
            }
        }

        composeTestRule.onNodeWithText("You have entered 8 team names.").assertIsDisplayed()
        composeTestRule.onNodeWithText("4 slots are empty.").assertIsDisplayed()
        composeTestRule.onNodeWithText("How would you like to continue?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use 8 Teams").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Use Defaults").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        composeTestRule.runOnIdle {
            assertEquals(true, usedTeams)
            assertEquals(true, usedDefaults)
            assertEquals(true, cancelled)
        }
    }

    @Test
    fun detailsScreenHidesLegacyManagementControls() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = TournamentDetailsUiState(
                        isLoading = false,
                        tournament = tournamentDetailsItem(
                            status = TournamentStatus.CONFIRMED,
                            matches = listOf(
                                MatchUiState(
                                    id = "finalized-id",
                                    matchNumber = 1,
                                    date = LocalDate.of(2026, 7, 24),
                                    mapName = "Bermuda",
                                    status = com.hoggamers.rankforge.domain.tournament.MatchStatus.FINALIZED,
                                ),
                            ),
                        ),
                    ),
                    onBackToList = {},
                    onEnterTeams = {},
                )
            }
        }

        composeTestRule.onAllNodesWithTag(TOURNAMENT_CLOUD_UPLOAD_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(DRAFT_MATCH_CLOUD_SYNC_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(FINALIZED_MATCH_CLOUD_SYNC_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_CLOUD_RESTORE_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(TOURNAMENT_STANDINGS_CSV_EXPORT_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(TOURNAMENT_STANDINGS_GOOGLE_SHEETS_EXPORT_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithText(context.getString(R.string.back_to_tournament_list_action)).assertCountEquals(0)
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

    private fun tournamentListItem(
        id: String = "stable-id",
        name: String = "Summer Cup",
    ) = TournamentListItemUiState(
        id = id,
        name = name,
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
        status: TournamentStatus = TournamentStatus.DRAFT,
    ) = TournamentDetailsItemUiState(
        id = "stable-id",
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Alex",
        organizerContactNumber = "123",
        status = status,
        slots = slots,
        matches = matches,
    )
}
