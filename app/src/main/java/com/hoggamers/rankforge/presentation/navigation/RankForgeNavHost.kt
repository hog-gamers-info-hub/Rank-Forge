package com.hoggamers.rankforge.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hoggamers.rankforge.presentation.screen.TournamentCreationRoute
import com.hoggamers.rankforge.presentation.screen.TournamentListPlaceholderScreen
import com.hoggamers.rankforge.presentation.screen.TournamentCreationViewModel

@Composable
fun RankForgeNavHost(
    navController: NavHostController = rememberNavController(),
    creationViewModel: TournamentCreationViewModel? = null,
) {
    NavHost(
        navController = navController,
        startDestination = TournamentListDestination,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable<TournamentListDestination> {
            TournamentListPlaceholderScreen(
                onCreateTournament = {
                    navController.navigate(TournamentCreationDestination)
                },
            )
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
    }
}
