package com.hoggamers.rankforge.presentation.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.hoggamers.rankforge.R
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
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.presentation.screen.TEAM_ENTRY_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TEAM_ENTRY_ROSTER_BUTTON_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.TeamEntryViewModel
import com.hoggamers.rankforge.presentation.screen.ROSTER_ENTRY_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.RosterEntryViewModel
import com.hoggamers.rankforge.presentation.screen.RosterReviewViewModel
import com.hoggamers.rankforge.presentation.screen.ROSTER_REVIEW_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_DETAILS_NOT_FOUND_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_DETAILS_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.TournamentDetailsViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentListViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentCreationViewModel
import com.hoggamers.rankforge.presentation.screen.MatchCreationViewModel
import com.hoggamers.rankforge.presentation.screen.MatchPlacementViewModel
import com.hoggamers.rankforge.presentation.screen.MatchKillViewModel
import com.hoggamers.rankforge.presentation.screen.MATCH_PLACEMENT_ACTION_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_PLACEMENT_FIELD_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_PLACEMENT_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_KILLS_ACTION_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_KILL_FIELD_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_KILL_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_KILL_SAVE_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_CREATION_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_CREATION_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.CREATE_MATCH_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsUseCase

@RunWith(AndroidJUnit4::class)
class RankForgeNavigationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

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
    fun successfulCreationReturnsToListAndCreatedTournamentIsVisible() {
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

        composeTestRule.onNodeWithText(context.getString(R.string.open_tournament_creation)).performClick()
        viewModels.creationViewModel.onTournamentNameChanged("Summer Cup")
        viewModels.creationViewModel.onTournamentDateChanged(LocalDate.of(2026, 7, 24))
        viewModels.creationViewModel.onOrganizerNameChanged("Alex")
        viewModels.creationViewModel.onOrganizerContactNumberChanged("123")
        viewModels.creationViewModel.submit()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.tournament_list_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Summer Cup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Date: 24 Jul 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Organizer: Alex").assertIsDisplayed()
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
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
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
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
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

        composeTestRule
            .onNodeWithText(context.getString(R.string.match_kill_value, 1, 0))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.match_kill_value, 12, 11))
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun pressBackOnMainThread() {
        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

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
                    saveMatchPlacements = SaveMatchPlacementsUseCase(repository),
                ).also {
                    it.load(tournamentId, matchId)
                }
            },
            matchKillViewModel = { tournamentId, matchId ->
                MatchKillViewModel(
                    observeMatches = ObserveMatchesUseCase(repository),
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    saveMatchKills = SaveMatchKillsUseCase(repository),
                ).also {
                    it.load(tournamentId, matchId)
                }
            },
        )
    }

    private fun confirmedTournament() = com.hoggamers.rankforge.domain.tournament.Tournament(
        id = "confirmed-id",
        name = "Confirmed Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Alex",
        organizerContactNumber = "123",
        status = com.hoggamers.rankforge.domain.tournament.TournamentStatus.CONFIRMED,
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
        val teamEntryViewModel: (String) -> TeamEntryViewModel,
        val rosterEntryViewModel: (String, Int) -> RosterEntryViewModel,
        val rosterReviewViewModel: (String) -> RosterReviewViewModel,
        val matchCreationViewModel: (String) -> MatchCreationViewModel,
        val matchPlacementViewModel: (String, String) -> MatchPlacementViewModel,
        val matchKillViewModel: (String, String) -> MatchKillViewModel,
    )
}
