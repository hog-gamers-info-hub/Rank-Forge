package com.hoggamers.rankforge.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hoggamers.rankforge.presentation.screen.TournamentCreationRoute
import com.hoggamers.rankforge.presentation.screen.TournamentDetailsRoute
import com.hoggamers.rankforge.presentation.screen.TournamentDetailsViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentListRoute
import com.hoggamers.rankforge.presentation.screen.TournamentListPlaceholderScreen
import com.hoggamers.rankforge.presentation.screen.TournamentCreationViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentListViewModel

@Composable
fun RankForgeNavHost(
    navController: NavHostController = rememberNavController(),
    creationViewModel: TournamentCreationViewModel? = null,
    listViewModel: TournamentListViewModel? = null,
    detailsViewModelFactory: ((String) -> TournamentDetailsViewModel)? = null,
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
            val detailsViewModel = detailsViewModelFactory?.invoke(destination.tournamentId)
            if (detailsViewModel == null) {
                TournamentDetailsRoute(
                    tournamentId = destination.tournamentId,
                    onBackToList = onBackToList,
                )
            } else {
                TournamentDetailsRoute(
                    tournamentId = destination.tournamentId,
                    onBackToList = onBackToList,
                    viewModel = detailsViewModel,
                )
            }
        }
    }
}
