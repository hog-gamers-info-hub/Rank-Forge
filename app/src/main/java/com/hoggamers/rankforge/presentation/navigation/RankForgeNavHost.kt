package com.hoggamers.rankforge.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hoggamers.rankforge.presentation.auth.AuthMode
import com.hoggamers.rankforge.presentation.auth.AuthScreen
import com.hoggamers.rankforge.presentation.auth.AuthUiState
import com.hoggamers.rankforge.presentation.screen.TeamEntryRoute
import com.hoggamers.rankforge.presentation.screen.TeamEntryViewModel
import com.hoggamers.rankforge.presentation.screen.RosterEntryRoute
import com.hoggamers.rankforge.presentation.screen.RosterEntryViewModel
import com.hoggamers.rankforge.presentation.screen.RosterReviewRoute
import com.hoggamers.rankforge.presentation.screen.RosterReviewViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentCreationRoute
import com.hoggamers.rankforge.presentation.screen.TournamentDetailsRoute
import com.hoggamers.rankforge.presentation.screen.TournamentDetailsViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentCloudUploadViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentCloudRestorationViewModel
import com.hoggamers.rankforge.presentation.screen.DraftMatchCloudSyncViewModel
import com.hoggamers.rankforge.presentation.screen.FinalizedMatchCloudSyncViewModel
import com.hoggamers.rankforge.presentation.screen.MatchCloudRestorationViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentStandingsRoute
import com.hoggamers.rankforge.presentation.screen.TournamentStandingsViewModel
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
import com.hoggamers.rankforge.presentation.screen.MatchReviewRoute
import com.hoggamers.rankforge.presentation.screen.MatchReviewViewModel
import com.hoggamers.rankforge.presentation.screen.MatchCorrectionRoute
import com.hoggamers.rankforge.presentation.screen.MatchCorrectionViewModel
import com.hoggamers.rankforge.presentation.screen.DraftConflictResolutionRoute

@Composable
fun RankForgeNavHost(
    navController: NavHostController = rememberNavController(),
    authUiState: AuthUiState = AuthUiState(),
    onAuthModeSelected: (AuthMode) -> Unit = {},
    onAuthEmailChanged: (String) -> Unit = {},
    onAuthPasswordChanged: (String) -> Unit = {},
    onAuthSubmit: () -> Unit = {},
    onAuthLogout: () -> Unit = {},
    creationViewModel: TournamentCreationViewModel? = null,
    listViewModel: TournamentListViewModel? = null,
    cloudRestorationViewModelFactory: (() -> TournamentCloudRestorationViewModel)? = null,
    detailsViewModelFactory: ((String) -> TournamentDetailsViewModel)? = null,
    cloudUploadViewModelFactory: ((String) -> TournamentCloudUploadViewModel)? = null,
    draftMatchSyncViewModelFactory: ((String) -> DraftMatchCloudSyncViewModel)? = null,
    finalizedMatchSyncViewModelFactory: ((String) -> FinalizedMatchCloudSyncViewModel)? = null,
    matchCloudRestorationViewModelFactory: ((String) -> MatchCloudRestorationViewModel)? = null,
    standingsViewModelFactory: ((String) -> TournamentStandingsViewModel)? = null,
    teamEntryViewModelFactory: ((String) -> TeamEntryViewModel)? = null,
    rosterEntryViewModelFactory: ((String, Int) -> RosterEntryViewModel)? = null,
    rosterReviewViewModelFactory: ((String) -> RosterReviewViewModel)? = null,
    matchCreationViewModelFactory: ((String) -> MatchCreationViewModel)? = null,
    matchPlacementViewModelFactory: ((String, String) -> MatchPlacementViewModel)? = null,
    matchKillViewModelFactory: ((String, String) -> MatchKillViewModel)? = null,
    matchReviewViewModelFactory: ((String, String) -> MatchReviewViewModel)? = null,
    matchCorrectionViewModelFactory: ((String, String) -> MatchCorrectionViewModel)? = null,
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
            val onOpenAuth = {
                navController.navigate(AuthDestination)
            }
            val onOpenTournamentDetails: (String) -> Unit = { tournamentId ->
                navController.navigate(TournamentDetailsDestination(tournamentId))
            }
            val cloudRestorationViewModel = cloudRestorationViewModelFactory?.invoke()
                ?: if (listViewModel == null) {
                    hiltViewModel<TournamentCloudRestorationViewModel>()
                } else {
                    null
                }
            if (listViewModel == null) {
                TournamentListRoute(
                    onCreateTournament = onCreateTournament,
                    onOpenTournamentDetails = onOpenTournamentDetails,
                    authUiState = authUiState,
                    onOpenAuth = onOpenAuth,
                    restorationViewModel = cloudRestorationViewModel,
                )
            } else {
                TournamentListRoute(
                    onCreateTournament = onCreateTournament,
                    onOpenTournamentDetails = onOpenTournamentDetails,
                    authUiState = authUiState,
                    onOpenAuth = onOpenAuth,
                    viewModel = listViewModel,
                    restorationViewModel = cloudRestorationViewModel,
                )
            }
        }
        composable<AuthDestination> {
            AuthScreen(
                uiState = authUiState,
                onModeSelected = onAuthModeSelected,
                onEmailChanged = onAuthEmailChanged,
                onPasswordChanged = onAuthPasswordChanged,
                onSubmit = onAuthSubmit,
                onLogout = onAuthLogout,
                onBack = { navController.popBackStack() },
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
            val onReviewMatch: (String, String) -> Unit = { tournamentId, matchId ->
                navController.navigate(MatchReviewDestination(tournamentId, matchId))
            }
            val onOpenStandings: (String) -> Unit = { tournamentId ->
                navController.navigate(TournamentStandingsDestination(tournamentId))
            }
            val onResolveDraftConflict: (com.hoggamers.rankforge.domain.tournament.ConflictResolutionContext) -> Unit = { conflict ->
                conflict.currentCloudRevision?.let { revision ->
                    navController.navigate(
                        DraftConflictResolutionDestination(
                            tournamentId = conflict.tournamentId,
                            currentCloudRevision = revision.value,
                        ),
                    )
                }
            }
            val cloudUploadViewModel = cloudUploadViewModelFactory?.invoke(destination.tournamentId)
                ?: if (detailsViewModelFactory == null) {
                    hiltViewModel<TournamentCloudUploadViewModel>()
                } else {
                    null
                }
            val draftMatchSyncViewModel = draftMatchSyncViewModelFactory?.invoke(destination.tournamentId)
                ?: if (detailsViewModelFactory == null) {
                    hiltViewModel<DraftMatchCloudSyncViewModel>()
                } else {
                    null
                }
            val finalizedMatchSyncViewModel = finalizedMatchSyncViewModelFactory?.invoke(destination.tournamentId)
                ?: if (detailsViewModelFactory == null) {
                    hiltViewModel<FinalizedMatchCloudSyncViewModel>()
                } else {
                    null
                }
            val matchCloudRestorationViewModel = matchCloudRestorationViewModelFactory?.invoke(destination.tournamentId)
                ?: if (detailsViewModelFactory == null) { hiltViewModel<MatchCloudRestorationViewModel>() } else null
            val detailsViewModel = detailsViewModelFactory?.invoke(destination.tournamentId)
            if (detailsViewModel == null) {
                TournamentDetailsRoute(
                    tournamentId = destination.tournamentId,
                    onBackToList = onBackToList,
                    onEnterTeams = onEnterTeams,
                    onCreateMatch = onCreateMatch,
                    onEnterMatchPlacements = onEnterMatchPlacements,
                    onEnterMatchKills = onEnterMatchKills,
                    onReviewMatch = onReviewMatch,
                    onOpenStandings = onOpenStandings,
                    onResolveDraftConflict = onResolveDraftConflict,
                    uploadViewModel = cloudUploadViewModel,
                    draftMatchSyncViewModel = draftMatchSyncViewModel,
                    finalizedMatchSyncViewModel = finalizedMatchSyncViewModel,
                    matchCloudRestorationViewModel = matchCloudRestorationViewModel,
                )
            } else {
                TournamentDetailsRoute(
                    tournamentId = destination.tournamentId,
                    onBackToList = onBackToList,
                    onEnterTeams = onEnterTeams,
                    onCreateMatch = onCreateMatch,
                    onEnterMatchPlacements = onEnterMatchPlacements,
                    onEnterMatchKills = onEnterMatchKills,
                    onReviewMatch = onReviewMatch,
                    onOpenStandings = onOpenStandings,
                    onResolveDraftConflict = onResolveDraftConflict,
                    viewModel = detailsViewModel,
                    uploadViewModel = cloudUploadViewModel,
                    draftMatchSyncViewModel = draftMatchSyncViewModel,
                    finalizedMatchSyncViewModel = finalizedMatchSyncViewModel,
                    matchCloudRestorationViewModel = matchCloudRestorationViewModel,
                )
            }
        }
        composable<DraftConflictResolutionDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<DraftConflictResolutionDestination>()
            DraftConflictResolutionRoute(
                tournamentId = destination.tournamentId,
                currentCloudRevision = destination.currentCloudRevision,
                onBack = { navController.popBackStack() },
            )
        }
        composable<TournamentStandingsDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<TournamentStandingsDestination>()
            val onBackToDetails: () -> Unit = { navController.popBackStack() }
            val standingsViewModel = standingsViewModelFactory?.invoke(destination.tournamentId)
            if (standingsViewModel == null) {
                TournamentStandingsRoute(
                    tournamentId = destination.tournamentId,
                    onBackToTournamentDetails = onBackToDetails,
                )
            } else {
                TournamentStandingsRoute(
                    tournamentId = destination.tournamentId,
                    onBackToTournamentDetails = onBackToDetails,
                    viewModel = standingsViewModel,
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
        composable<MatchReviewDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<MatchReviewDestination>()
            val onBackToDetails: () -> Unit = { navController.popBackStack() }
            val onEnterPlacements: (String, String) -> Unit = { tournamentId, matchId ->
                navController.navigate(MatchPlacementDestination(tournamentId, matchId))
            }
            val onEnterKills: (String, String) -> Unit = { tournamentId, matchId ->
                navController.navigate(MatchKillDestination(tournamentId, matchId))
            }
            val onStartCorrection: (String, String) -> Unit = { tournamentId, matchId ->
                navController.navigate(MatchCorrectionDestination(tournamentId, matchId))
            }
            val reviewViewModel = matchReviewViewModelFactory?.invoke(
                destination.tournamentId,
                destination.matchId,
            )
            if (reviewViewModel == null) {
                MatchReviewRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    onBackToDetails = onBackToDetails,
                    onEnterPlacements = onEnterPlacements,
                    onEnterKills = onEnterKills,
                    onStartCorrection = onStartCorrection,
                )
            } else {
                MatchReviewRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    onBackToDetails = onBackToDetails,
                    onEnterPlacements = onEnterPlacements,
                    onEnterKills = onEnterKills,
                    onStartCorrection = onStartCorrection,
                    viewModel = reviewViewModel,
                )
            }
        }
        composable<MatchCorrectionDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<MatchCorrectionDestination>()
            val onBackToReview: () -> Unit = { navController.popBackStack() }
            val correctionViewModel = matchCorrectionViewModelFactory?.invoke(
                destination.tournamentId,
                destination.matchId,
            )
            if (correctionViewModel == null) {
                MatchCorrectionRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    onBackToReview = onBackToReview,
                )
            } else {
                MatchCorrectionRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    onBackToReview = onBackToReview,
                    viewModel = correctionViewModel,
                )
            }
        }
    }
}
