package com.hoggamers.rankforge.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hoggamers.rankforge.presentation.screen.TeamEntryRoute
import com.hoggamers.rankforge.presentation.screen.TeamEntryViewModel
import com.hoggamers.rankforge.presentation.screen.RosterEntryRoute
import com.hoggamers.rankforge.presentation.screen.RosterEntryViewModel
import com.hoggamers.rankforge.presentation.screen.RosterReviewRoute
import com.hoggamers.rankforge.presentation.screen.RosterReviewViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentCreationRoute
import com.hoggamers.rankforge.presentation.screen.TournamentDetailsRoute
import com.hoggamers.rankforge.presentation.screen.TournamentDetailsViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentListRoute
import com.hoggamers.rankforge.presentation.screen.TournamentListPlaceholderScreen
import com.hoggamers.rankforge.presentation.screen.TournamentCreationViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentListViewModel
import com.hoggamers.rankforge.presentation.screen.MatchCreationRoute
import com.hoggamers.rankforge.presentation.screen.MatchCreationViewModel
import com.hoggamers.rankforge.presentation.screen.MatchPlacementRoute
import com.hoggamers.rankforge.presentation.screen.MatchPlacementViewModel
import com.hoggamers.rankforge.presentation.screen.MatchKillRoute
import com.hoggamers.rankforge.presentation.screen.MatchKillViewModel

@Composable
fun RankForgeNavHost(
    navController: NavHostController = rememberNavController(),
    creationViewModel: TournamentCreationViewModel? = null,
    listViewModel: TournamentListViewModel? = null,
    detailsViewModelFactory: ((String) -> TournamentDetailsViewModel)? = null,
    teamEntryViewModelFactory: ((String) -> TeamEntryViewModel)? = null,
    rosterEntryViewModelFactory: ((String, Int) -> RosterEntryViewModel)? = null,
    rosterReviewViewModelFactory: ((String) -> RosterReviewViewModel)? = null,
    matchCreationViewModelFactory: ((String) -> MatchCreationViewModel)? = null,
    matchPlacementViewModelFactory: ((String, String) -> MatchPlacementViewModel)? = null,
    matchKillViewModelFactory: ((String, String) -> MatchKillViewModel)? = null,
) {
    NavHost(
        navController = navController,
        startDestination = TournamentListDestination,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable<TournamentListDestination> {
            val onCreateTournament = {
                navController.navigate(TournamentCreationDestination)
            }
            val onOpenTournamentDetails: (String) -> Unit = { tournamentId ->
                navController.navigate(TournamentDetailsDestination(tournamentId))
            }
            if (listViewModel == null) {
                TournamentListRoute(
                    onCreateTournament = onCreateTournament,
                    onOpenTournamentDetails = onOpenTournamentDetails,
                )
            } else {
                TournamentListRoute(
                    onCreateTournament = onCreateTournament,
                    onOpenTournamentDetails = onOpenTournamentDetails,
                    viewModel = listViewModel,
                )
            }
        }
        composable<TournamentCreationDestination> {
            val onBack: () -> Unit = { navController.popBackStack() }
            if (creationViewModel == null) {
                TournamentCreationRoute(onBack = onBack)
            } else {
                TournamentCreationRoute(
                    onBack = onBack,
                    viewModel = creationViewModel,
                )
            }
        }
        composable<TournamentDetailsDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<TournamentDetailsDestination>()
            val onBackToList: () -> Unit = {
                navController.popBackStack(TournamentListDestination, inclusive = false)
            }
            val onEnterTeams: (String) -> Unit = { tournamentId ->
                navController.navigate(TeamEntryDestination(tournamentId))
            }
            val onCreateMatch: (String) -> Unit = { tournamentId ->
                navController.navigate(MatchCreationDestination(tournamentId))
            }
            val onEnterMatchPlacements: (String, String) -> Unit = { tournamentId, matchId ->
                navController.navigate(MatchPlacementDestination(tournamentId, matchId))
            }
            val onEnterMatchKills: (String, String) -> Unit = { tournamentId, matchId ->
                navController.navigate(MatchKillDestination(tournamentId, matchId))
            }
            val detailsViewModel = detailsViewModelFactory?.invoke(destination.tournamentId)
            if (detailsViewModel == null) {
                TournamentDetailsRoute(
                    tournamentId = destination.tournamentId,
                    onBackToList = onBackToList,
                    onEnterTeams = onEnterTeams,
                    onCreateMatch = onCreateMatch,
                    onEnterMatchPlacements = onEnterMatchPlacements,
                    onEnterMatchKills = onEnterMatchKills,
                )
            } else {
                TournamentDetailsRoute(
                    tournamentId = destination.tournamentId,
                    onBackToList = onBackToList,
                    onEnterTeams = onEnterTeams,
                    onCreateMatch = onCreateMatch,
                    onEnterMatchPlacements = onEnterMatchPlacements,
                    onEnterMatchKills = onEnterMatchKills,
                    viewModel = detailsViewModel,
                )
            }
        }
        composable<TeamEntryDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<TeamEntryDestination>()
            val onBackToDetails: () -> Unit = { navController.popBackStack() }
            val onEditRoster: (Int) -> Unit = { slotNumber ->
                navController.navigate(
                    RosterEntryDestination(
                        tournamentId = destination.tournamentId,
                        slotNumber = slotNumber,
                    ),
                )
            }
            val onReviewRoster: () -> Unit = {
                navController.navigate(RosterReviewDestination(destination.tournamentId))
            }
            val teamEntryViewModel = teamEntryViewModelFactory?.invoke(destination.tournamentId)
            if (teamEntryViewModel == null) {
                TeamEntryRoute(
                    tournamentId = destination.tournamentId,
                    onBackToDetails = onBackToDetails,
                    onEditRoster = onEditRoster,
                    onReviewRoster = onReviewRoster,
                    focusSlotNumber = destination.focusSlotNumber,
                )
            } else {
                TeamEntryRoute(
                    tournamentId = destination.tournamentId,
                    onBackToDetails = onBackToDetails,
                    onEditRoster = onEditRoster,
                    onReviewRoster = onReviewRoster,
                    focusSlotNumber = destination.focusSlotNumber,
                    viewModel = teamEntryViewModel,
                )
            }
        }
        composable<RosterEntryDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<RosterEntryDestination>()
            val onBackToTeamEntry: () -> Unit = { navController.popBackStack() }
            val rosterEntryViewModel = rosterEntryViewModelFactory?.invoke(
                destination.tournamentId,
                destination.slotNumber,
            )
            if (rosterEntryViewModel == null) {
                RosterEntryRoute(
                    tournamentId = destination.tournamentId,
                    slotNumber = destination.slotNumber,
                    onBackToTeamEntry = onBackToTeamEntry,
                )
            } else {
                RosterEntryRoute(
                    tournamentId = destination.tournamentId,
                    slotNumber = destination.slotNumber,
                    onBackToTeamEntry = onBackToTeamEntry,
                    viewModel = rosterEntryViewModel,
                )
            }
        }
        composable<RosterReviewDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<RosterReviewDestination>()
            val onEditTeam: (Int) -> Unit = { slotNumber ->
                navController.navigate(
                    TeamEntryDestination(
                        tournamentId = destination.tournamentId,
                        focusSlotNumber = slotNumber,
                    ),
                )
            }
            val onEditRoster: (Int) -> Unit = { slotNumber ->
                navController.navigate(
                    RosterEntryDestination(
                        tournamentId = destination.tournamentId,
                        slotNumber = slotNumber,
                    ),
                )
            }
            val onBackToTeamEntry: () -> Unit = { navController.popBackStack() }
            val reviewViewModel = rosterReviewViewModelFactory?.invoke(destination.tournamentId)
            if (reviewViewModel == null) {
                RosterReviewRoute(
                    tournamentId = destination.tournamentId,
                    onEditTeam = onEditTeam,
                    onEditRoster = onEditRoster,
                    onBackToTeamEntry = onBackToTeamEntry,
                )
            } else {
                RosterReviewRoute(
                    tournamentId = destination.tournamentId,
                    onEditTeam = onEditTeam,
                    onEditRoster = onEditRoster,
                    onBackToTeamEntry = onBackToTeamEntry,
                    viewModel = reviewViewModel,
                )
            }
        }
        composable<MatchCreationDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<MatchCreationDestination>()
            val onBackToDetails: () -> Unit = { navController.popBackStack() }
            val matchCreationViewModel = matchCreationViewModelFactory?.invoke(destination.tournamentId)
            if (matchCreationViewModel == null) {
                MatchCreationRoute(
                    tournamentId = destination.tournamentId,
                    onBackToDetails = onBackToDetails,
                )
            } else {
                MatchCreationRoute(
                    tournamentId = destination.tournamentId,
                    onBackToDetails = onBackToDetails,
                    viewModel = matchCreationViewModel,
                )
            }
        }
        composable<MatchPlacementDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<MatchPlacementDestination>()
            val onBackToDetails: () -> Unit = { navController.popBackStack() }
            val placementViewModel = matchPlacementViewModelFactory?.invoke(
                destination.tournamentId,
                destination.matchId,
            )
            if (placementViewModel == null) {
                MatchPlacementRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    onBackToDetails = onBackToDetails,
                )
            } else {
                MatchPlacementRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    onBackToDetails = onBackToDetails,
                    viewModel = placementViewModel,
                )
            }
        }
        composable<MatchKillDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<MatchKillDestination>()
            val onBackToDetails: () -> Unit = { navController.popBackStack() }
            val killViewModel = matchKillViewModelFactory?.invoke(
                destination.tournamentId,
                destination.matchId,
            )
            if (killViewModel == null) {
                MatchKillRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    onBackToDetails = onBackToDetails,
                )
            } else {
                MatchKillRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    onBackToDetails = onBackToDetails,
                    viewModel = killViewModel,
                )
            }
        }
    }
}
