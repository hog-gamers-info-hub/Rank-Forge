package com.hoggamers.rankforge.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.hoggamers.rankforge.presentation.screen.RosterOcrReviewViewModel
import com.hoggamers.rankforge.presentation.screen.RosterScreenshotCropRoute
import com.hoggamers.rankforge.presentation.screen.RosterScreenshotIntakeRoute
import com.hoggamers.rankforge.presentation.screen.RosterScreenshotIntakeViewModel
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
import com.hoggamers.rankforge.presentation.screen.AllTournamentsRoute
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
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.presentation.screen.MatchResultScreenshotCropRoute
import com.hoggamers.rankforge.presentation.screen.MatchResultScreenshotCropViewModel
import com.hoggamers.rankforge.presentation.screen.MatchLobbyScreenshotCropRoute
import com.hoggamers.rankforge.presentation.screen.MatchLobbyScreenshotCropViewModel
import com.hoggamers.rankforge.presentation.screen.MatchLobbyScreenshotIntakeRoute
import com.hoggamers.rankforge.presentation.screen.MatchOcrReviewRoute
import com.hoggamers.rankforge.presentation.screen.MatchOcrReviewViewModel
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
    rosterOcrReviewViewModelFactory: ((String) -> RosterOcrReviewViewModel)? = null,
    rosterScreenshotIntakeContent: @Composable (String, (Int) -> Unit) -> Unit = { tournamentId, onOpenCropEditor ->
        RosterScreenshotIntakeRoute(
            tournamentId = tournamentId,
            onOpenCropEditor = onOpenCropEditor,
        )
    },
    rosterScreenshotCropViewModelFactory: (() -> RosterScreenshotIntakeViewModel)? = null,
    matchLobbyScreenshotIntakeContent: @Composable (String, String, (Int) -> Unit) -> Unit = { tournamentId, matchId, onOpenCropEditor ->
        MatchLobbyScreenshotIntakeRoute(
            tournamentId = tournamentId,
            matchId = matchId,
            onOpenCropEditor = onOpenCropEditor,
            showTitle = false,
        )
    },
    matchCreationViewModelFactory: ((String) -> MatchCreationViewModel)? = null,
    matchPlacementViewModelFactory: ((String, String) -> MatchPlacementViewModel)? = null,
    matchKillViewModelFactory: ((String, String) -> MatchKillViewModel)? = null,
    matchReviewViewModelFactory: ((String, String) -> MatchReviewViewModel)? = null,
    showLegacyManualReviewContent: Boolean = false,
    matchResultScreenshotCropViewModelFactory: (() -> MatchResultScreenshotCropViewModel)? = null,
    matchLobbyScreenshotCropViewModelFactory: ((String, String, Int) -> MatchLobbyScreenshotCropViewModel)? = null,
    matchOcrReviewViewModelFactory: ((String, String) -> MatchOcrReviewViewModel)? = null,
    matchCorrectionViewModelFactory: ((String, String) -> MatchCorrectionViewModel)? = null,
    onAuthGoogleSignIn: () -> Unit = {},
) {
    var openHomeMenuOnReturn by remember { mutableStateOf(false) }
    val sharedTournamentListViewModel =
        listViewModel ?: hiltViewModel<TournamentListViewModel>()

    NavHost(
        navController = navController,
        startDestination = TournamentListDestination,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
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
            val onOpenAllTournaments = {
                navController.navigate(AllTournamentsDestination)
            }
            TournamentListRoute(
                onCreateTournament = onCreateTournament,
                onOpenTournamentDetails = onOpenTournamentDetails,
                onOpenAuth = onOpenAuth,
                onOpenAllTournaments = onOpenAllTournaments,
                openDrawerOnEnter = openHomeMenuOnReturn,
                onDrawerOpenRequestConsumed = { openHomeMenuOnReturn = false },
                viewModel = sharedTournamentListViewModel,
            )
        }
        composable<AllTournamentsDestination> {
            val cloudRestorationViewModel = cloudRestorationViewModelFactory?.invoke()
                ?: if (listViewModel == null) {
                    hiltViewModel<TournamentCloudRestorationViewModel>()
                } else {
                    null
                }
            val onHome: () -> Unit = {
                openHomeMenuOnReturn = false
                navController.popBackStack(TournamentListDestination, inclusive = false)
            }
            val onBack: () -> Unit = {
                openHomeMenuOnReturn = true
                navController.popBackStack(TournamentListDestination, inclusive = false)
            }
            val onOpenTournamentDetails: (String) -> Unit = { tournamentId ->
                navController.navigate(TournamentDetailsDestination(tournamentId))
            }
            AllTournamentsRoute(
                onHome = onHome,
                onBack = onBack,
                onOpenTournamentDetails = onOpenTournamentDetails,
                viewModel = sharedTournamentListViewModel,
                restorationViewModel = cloudRestorationViewModel,
            )
        }
        composable<AuthDestination> {
            AuthScreen(
                uiState = authUiState,
                onModeSelected = onAuthModeSelected,
                onEmailChanged = onAuthEmailChanged,
                onPasswordChanged = onAuthPasswordChanged,
                onSubmit = onAuthSubmit,
                onGoogleSignIn = onAuthGoogleSignIn,
                onLogout = onAuthLogout,
                onSignedInHome = {
                    openHomeMenuOnReturn = false
                    navController.popBackStack()
                },
                onSignedInBack = {
                    openHomeMenuOnReturn = true
                    navController.popBackStack()
                },
            )
        }
        composable<TournamentCreationDestination> {
            val onBack: () -> Unit = { navController.popBackStack() }
            val onCreated: (String) -> Unit = { tournamentId ->
                navController.navigate(TournamentDetailsDestination(tournamentId)) {
                    popUpTo(TournamentCreationDestination) {
                        inclusive = true
                    }
                }
                navController.navigate(TeamEntryDestination(tournamentId))
            }
            if (creationViewModel == null) {
                TournamentCreationRoute(
                    onBack = onBack,
                    onCreated = onCreated,
                )
            } else {
                TournamentCreationRoute(
                    onBack = onBack,
                    onCreated = onCreated,
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
            val onOpenCropEditor: (Int) -> Unit = { screenshotIndex ->
                navController.navigate(
                    RosterScreenshotCropDestination(
                        tournamentId = destination.tournamentId,
                        screenshotIndex = screenshotIndex,
                    ),
                )
            }
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
            val onConfirmed: (String) -> Unit = { tournamentId ->
                val detailsDestination = TournamentDetailsDestination(tournamentId)
                if (!navController.popBackStack(detailsDestination, inclusive = false)) {
                    navController.navigate(detailsDestination) {
                        popUpTo(RosterReviewDestination(tournamentId)) {
                            inclusive = true
                        }
                    }
                }
            }
            val reviewViewModel = rosterReviewViewModelFactory?.invoke(destination.tournamentId)
            val ocrReviewViewModel = rosterOcrReviewViewModelFactory?.invoke(destination.tournamentId)
            if (reviewViewModel == null && ocrReviewViewModel == null) {
                RosterReviewRoute(
                    tournamentId = destination.tournamentId,
                    onEditTeam = onEditTeam,
                    onEditRoster = onEditRoster,
                    onBackToTeamEntry = onBackToTeamEntry,
                    onConfirmed = onConfirmed,
                    rosterScreenshotIntake = {
                        rosterScreenshotIntakeContent(destination.tournamentId, onOpenCropEditor)
                    },
                )
            } else if (reviewViewModel != null && ocrReviewViewModel == null) {
                RosterReviewRoute(
                    tournamentId = destination.tournamentId,
                    onEditTeam = onEditTeam,
                    onEditRoster = onEditRoster,
                    onBackToTeamEntry = onBackToTeamEntry,
                    onConfirmed = onConfirmed,
                    viewModel = reviewViewModel,
                    rosterScreenshotIntake = {
                        rosterScreenshotIntakeContent(destination.tournamentId, onOpenCropEditor)
                    },
                )
            } else if (reviewViewModel == null) {
                RosterReviewRoute(
                    tournamentId = destination.tournamentId,
                    onEditTeam = onEditTeam,
                    onEditRoster = onEditRoster,
                    onBackToTeamEntry = onBackToTeamEntry,
                    onConfirmed = onConfirmed,
                    rosterOcrViewModel = ocrReviewViewModel!!,
                    rosterScreenshotIntake = {
                        rosterScreenshotIntakeContent(destination.tournamentId, onOpenCropEditor)
                    },
                )
            } else {
                RosterReviewRoute(
                    tournamentId = destination.tournamentId,
                    onEditTeam = onEditTeam,
                    onEditRoster = onEditRoster,
                    onBackToTeamEntry = onBackToTeamEntry,
                    onConfirmed = onConfirmed,
                    viewModel = reviewViewModel,
                    rosterOcrViewModel = ocrReviewViewModel!!,
                    rosterScreenshotIntake = {
                        rosterScreenshotIntakeContent(destination.tournamentId, onOpenCropEditor)
                    },
                )
            }
        }
        composable<RosterScreenshotCropDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<RosterScreenshotCropDestination>()
            val cropViewModel = rosterScreenshotCropViewModelFactory?.invoke()
            if (cropViewModel == null) {
                RosterScreenshotCropRoute(
                    tournamentId = destination.tournamentId,
                    screenshotIndex = destination.screenshotIndex,
                    onCancel = { navController.popBackStack() },
                    onConfirmed = { navController.popBackStack() },
                )
            } else {
                RosterScreenshotCropRoute(
                    tournamentId = destination.tournamentId,
                    screenshotIndex = destination.screenshotIndex,
                    onCancel = { navController.popBackStack() },
                    onConfirmed = { navController.popBackStack() },
                    viewModel = cropViewModel,
                )
            }
        }
        composable<MatchCreationDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<MatchCreationDestination>()
            val onBackToDetails: () -> Unit = { navController.popBackStack() }
            val onCreated: (String, String) -> Unit = { tournamentId, matchId ->
                navController.navigate(MatchPlacementDestination(tournamentId, matchId)) {
                    popUpTo(MatchCreationDestination(tournamentId)) {
                        inclusive = true
                    }
                }
            }
            val matchCreationViewModel = matchCreationViewModelFactory?.invoke(destination.tournamentId)
            if (matchCreationViewModel == null) {
                MatchCreationRoute(
                    tournamentId = destination.tournamentId,
                    onBackToDetails = onBackToDetails,
                    onCreated = onCreated,
                )
            } else {
                MatchCreationRoute(
                    tournamentId = destination.tournamentId,
                    onBackToDetails = onBackToDetails,
                    onCreated = onCreated,
                    viewModel = matchCreationViewModel,
                )
            }
        }
        composable<MatchPlacementDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<MatchPlacementDestination>()
            val onBackToDetails: () -> Unit = { navController.popBackStack() }
            val onSavedToKills: (String, String) -> Unit = { tournamentId, matchId ->
                navController.navigate(MatchKillDestination(tournamentId, matchId)) {
                    popUpTo(MatchPlacementDestination(tournamentId, matchId)) {
                        inclusive = true
                    }
                }
            }
            val placementViewModel = matchPlacementViewModelFactory?.invoke(
                destination.tournamentId,
                destination.matchId,
            )
            if (placementViewModel == null) {
                MatchPlacementRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    onBackToDetails = onBackToDetails,
                    onSavedToKills = onSavedToKills,
                )
            } else {
                MatchPlacementRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    onBackToDetails = onBackToDetails,
                    onSavedToKills = onSavedToKills,
                    viewModel = placementViewModel,
                )
            }
        }
        composable<MatchKillDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<MatchKillDestination>()
            val onBackToDetails: () -> Unit = { navController.popBackStack() }
            val onSavedToReview: (String, String) -> Unit = { tournamentId, matchId ->
                navController.navigate(MatchReviewDestination(tournamentId, matchId)) {
                    popUpTo(MatchKillDestination(tournamentId, matchId)) {
                        inclusive = true
                    }
                }
            }
            val killViewModel = matchKillViewModelFactory?.invoke(
                destination.tournamentId,
                destination.matchId,
            )
            if (killViewModel == null) {
                MatchKillRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    onBackToDetails = onBackToDetails,
                    onSavedToReview = onSavedToReview,
                )
            } else {
                MatchKillRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    onBackToDetails = onBackToDetails,
                    onSavedToReview = onSavedToReview,
                    viewModel = killViewModel,
                )
            }
        }
        composable<MatchReviewDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<MatchReviewDestination>()
            val onBackToDetails: () -> Unit = {
                val detailsDestination = TournamentDetailsDestination(destination.tournamentId)
                if (!navController.popBackStack(detailsDestination, inclusive = false)) {
                    navController.navigate(detailsDestination) {
                        popUpTo(MatchReviewDestination(destination.tournamentId, destination.matchId)) {
                            inclusive = true
                        }
                    }
                }
            }
            val onEnterPlacements: (String, String) -> Unit = { tournamentId, matchId ->
                navController.navigate(MatchPlacementDestination(tournamentId, matchId))
            }
            val onEnterKills: (String, String) -> Unit = { tournamentId, matchId ->
                navController.navigate(MatchKillDestination(tournamentId, matchId))
            }
            val onStartCorrection: (String, String) -> Unit = { tournamentId, matchId ->
                navController.navigate(MatchCorrectionDestination(tournamentId, matchId))
            }
            val onOpenOcrReview: (String, String) -> Unit = if (showLegacyManualReviewContent) {
                { tournamentId, matchId ->
                    navController.navigate(MatchOcrReviewDestination(tournamentId, matchId))
                }
            } else {
                { _, _ -> }
            }
            val onOpenResultScreenshotCrop: (String, String, MatchResultScreenshotRole) -> Unit = {
                    tournamentId,
                    matchId,
                    role,
                ->
                navController.navigate(
                    MatchResultScreenshotCropDestination(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        screenshotRole = role.name,
                    ),
                )
            }
            val onOpenLobbyScreenshotCrop: (String, String, Int) -> Unit = {
                    tournamentId,
                    matchId,
                    lobbyScreenshotIndex,
                ->
                navController.navigate(
                    MatchLobbyScreenshotCropDestination(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        lobbyScreenshotIndex = lobbyScreenshotIndex,
                    ),
                )
            }
            val matchLobbyScreenshotIntake = @Composable {
                matchLobbyScreenshotIntakeContent(
                    destination.tournamentId,
                    destination.matchId,
                    { index ->
                        onOpenLobbyScreenshotCrop(
                            destination.tournamentId,
                            destination.matchId,
                            index,
                        )
                    },
                )
            }
            val reviewViewModel = matchReviewViewModelFactory?.invoke(
                destination.tournamentId,
                destination.matchId,
            )
            val ocrReviewViewModel = matchOcrReviewViewModelFactory?.invoke(
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
                    onOpenOcrReview = onOpenOcrReview,
                    onOpenResultScreenshotCrop = onOpenResultScreenshotCrop,
                    onStartCorrection = onStartCorrection,
                    matchLobbyScreenshotIntake = matchLobbyScreenshotIntake,
                    showLegacyManualReviewContent = showLegacyManualReviewContent,
                    ocrReviewViewModel = ocrReviewViewModel,
                )
            } else {
                MatchReviewRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    onBackToDetails = onBackToDetails,
                    onEnterPlacements = onEnterPlacements,
                    onEnterKills = onEnterKills,
                    onOpenOcrReview = onOpenOcrReview,
                    onOpenResultScreenshotCrop = onOpenResultScreenshotCrop,
                    onStartCorrection = onStartCorrection,
                    matchLobbyScreenshotIntake = matchLobbyScreenshotIntake,
                    showLegacyManualReviewContent = showLegacyManualReviewContent,
                    viewModel = reviewViewModel,
                    ocrReviewViewModel = ocrReviewViewModel,
                )
            }
        }
        composable<MatchLobbyScreenshotCropDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<MatchLobbyScreenshotCropDestination>()
            val onBackToReview: () -> Unit = {
                val reviewDestination = MatchReviewDestination(destination.tournamentId, destination.matchId)
                if (!navController.popBackStack(reviewDestination, inclusive = false)) {
                    navController.navigate(reviewDestination) {
                        popUpTo(
                            MatchLobbyScreenshotCropDestination(
                                destination.tournamentId,
                                destination.matchId,
                                destination.lobbyScreenshotIndex,
                            ),
                        ) {
                            inclusive = true
                        }
                    }
                }
            }
            val cropViewModel = matchLobbyScreenshotCropViewModelFactory?.invoke(
                destination.tournamentId,
                destination.matchId,
                destination.lobbyScreenshotIndex,
            )
            if (cropViewModel == null) {
                MatchLobbyScreenshotCropRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    lobbyScreenshotIndex = destination.lobbyScreenshotIndex,
                    onCancel = onBackToReview,
                    onConfirmed = onBackToReview,
                )
            } else {
                MatchLobbyScreenshotCropRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    lobbyScreenshotIndex = destination.lobbyScreenshotIndex,
                    onCancel = onBackToReview,
                    onConfirmed = onBackToReview,
                    viewModel = cropViewModel,
                )
            }
        }
        composable<MatchResultScreenshotCropDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<MatchResultScreenshotCropDestination>()
            val cropViewModel = matchResultScreenshotCropViewModelFactory?.invoke()
            val onBackToReview: () -> Unit = {
                val reviewDestination = MatchReviewDestination(destination.tournamentId, destination.matchId)
                if (!navController.popBackStack(reviewDestination, inclusive = false)) {
                    navController.navigate(reviewDestination) {
                        popUpTo(
                            MatchResultScreenshotCropDestination(
                                destination.tournamentId,
                                destination.matchId,
                                destination.screenshotRole,
                            ),
                        ) {
                            inclusive = true
                        }
                    }
                }
            }
            if (cropViewModel == null) {
                MatchResultScreenshotCropRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    screenshotRole = destination.screenshotRole,
                    onCancel = onBackToReview,
                    onConfirmed = onBackToReview,
                )
            } else {
                MatchResultScreenshotCropRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    screenshotRole = destination.screenshotRole,
                    onCancel = onBackToReview,
                    onConfirmed = onBackToReview,
                    viewModel = cropViewModel,
                )
            }
        }
        composable<MatchOcrReviewDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<MatchOcrReviewDestination>()
            val onBack: () -> Unit = {
                navController.popBackStack()
            }
            val ocrReviewViewModel = matchOcrReviewViewModelFactory?.invoke(
                destination.tournamentId,
                destination.matchId,
            )
            if (ocrReviewViewModel == null) {
                MatchOcrReviewRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    onBack = onBack,
                )
            } else {
                MatchOcrReviewRoute(
                    tournamentId = destination.tournamentId,
                    matchId = destination.matchId,
                    onBack = onBack,
                    viewModel = ocrReviewViewModel,
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
