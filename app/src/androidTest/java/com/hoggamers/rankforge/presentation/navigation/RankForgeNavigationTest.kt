package com.hoggamers.rankforge.presentation.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.tournament.CreateTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterPlayersUseCase
import com.hoggamers.rankforge.domain.tournament.SaveRosterUseCase
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.presentation.screen.TEAM_ENTRY_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TEAM_ENTRY_ROSTER_BUTTON_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.TeamEntryViewModel
import com.hoggamers.rankforge.presentation.screen.ROSTER_ENTRY_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.RosterEntryViewModel
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_DETAILS_NOT_FOUND_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_DETAILS_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.TournamentDetailsViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentListViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentCreationViewModel
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_CREATION_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

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

    private fun pressBackOnMainThread() {
        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

    private fun createNavigationViewModels(): NavigationViewModels {
        val repository = InMemoryTournamentRepository()
        return NavigationViewModels(
            creationViewModel = createCreationViewModel(repository),
            listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository)),
            detailsViewModel = { tournamentId ->
                TournamentDetailsViewModel(
                    getTournamentById = GetTournamentByIdUseCase(repository),
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                ).also {
                    it.load(tournamentId)
                }
            },
            teamEntryViewModel = { tournamentId ->
                TeamEntryViewModel(
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    saveTeamSlotNames = SaveTeamSlotNamesUseCase(repository),
                ).also {
                    it.load(tournamentId)
                }
            },
            rosterEntryViewModel = { tournamentId, slotNumber ->
                RosterEntryViewModel(
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    observeRosterPlayers = ObserveRosterPlayersUseCase(repository),
                    saveRoster = SaveRosterUseCase(repository),
                ).also {
                    it.load(tournamentId, slotNumber)
                }
            },
        )
    }

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
        val creationViewModel: TournamentCreationViewModel,
        val listViewModel: TournamentListViewModel,
        val detailsViewModel: (String) -> TournamentDetailsViewModel,
        val teamEntryViewModel: (String) -> TeamEntryViewModel,
        val rosterEntryViewModel: (String, Int) -> RosterEntryViewModel,
    )
}
