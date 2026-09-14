package com.hoggamers.rankforge.presentation.screen

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.component.PointIqPageHeader

private val PointIqAllTournamentsBackground = Color(0xFF031225)
private val PointIqAllTournamentsAmbientBlue = Color(0xFF0B386F)
private val PointIqAllTournamentsHeader = Color(0xFFF6F8FF)
private val PointIqAllTournamentsSecondary = Color(0xFF91AFE0)

const val ALL_TOURNAMENTS_SCREEN_TEST_TAG = "all_tournaments_screen"
const val ALL_TOURNAMENTS_HOME_ACTION_TEST_TAG = "all_tournaments_home_action"
const val ALL_TOURNAMENTS_BACK_ACTION_TEST_TAG = "all_tournaments_back_action"

@Composable
fun AllTournamentsRoute(
    onHome: () -> Unit,
    onBack: () -> Unit,
    onOpenTournamentDetails: (String) -> Unit,
    viewModel: TournamentListViewModel? = null,
    restorationViewModel: TournamentCloudRestorationViewModel? = null,
) {
    val resolvedViewModel = viewModel ?: hiltViewModel<TournamentListViewModel>()
    val uiState by resolvedViewModel.uiState.collectAsStateWithLifecycle()

    val restorationUiState = if (restorationViewModel == null) {
        null
    } else {
        val state by restorationViewModel.uiState.collectAsStateWithLifecycle()
        state
    }

    AllTournamentsScreen(
        uiState = uiState,
        restorationUiState = restorationUiState,
        onHome = onHome,
        onBack = onBack,
        onOpenTournamentDetails = onOpenTournamentDetails,
        onLoadCloudTournaments = {
            restorationViewModel?.loadAvailable()
        },
        onRestoreCloudTournament = { tournamentId ->
            restorationViewModel?.restore(tournamentId)
        },
    )
}

@Composable
fun AllTournamentsScreen(
    uiState: TournamentListUiState,
    restorationUiState: TournamentCloudRestorationUiState?,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onOpenTournamentDetails: (String) -> Unit,
    onLoadCloudTournaments: () -> Unit = {},
    onRestoreCloudTournament: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)

    PointIqAllTournamentsScreenContainer(
        modifier = Modifier.testTag(ALL_TOURNAMENTS_SCREEN_TEST_TAG),
    ) {
        PointIqPageHeader(
            title = stringResource(R.string.all_tournaments_page_title),
            onBack = onBack,
            backTestTag = ALL_TOURNAMENTS_BACK_ACTION_TEST_TAG,
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.pointiq_all_tournaments_local_section),
                    color = PointIqAllTournamentsHeader,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (uiState.isEmpty) {
                item {
                    Text(
                        text = stringResource(R.string.tournament_list_empty_message),
                        color = PointIqAllTournamentsSecondary,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.testTag(TOURNAMENT_LIST_EMPTY_TEST_TAG),
                    )
                }
            } else {
                items(
                    items = uiState.tournaments,
                    key = { tournament -> tournament.id },
                ) { tournament ->
                    TournamentListItemCard(
                        tournament = tournament,
                        onClick = {
                            onOpenTournamentDetails(tournament.id)
                        },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.pointiq_all_tournaments_cloud_section),
                    color = PointIqAllTournamentsHeader,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (restorationUiState != null) {
                item {
                    TournamentCloudRestorationSection(
                        uiState = restorationUiState,
                        onLoadCloudTournaments = onLoadCloudTournaments,
                        onRestoreCloudTournament = onRestoreCloudTournament,
                    )
                }
            }
        }
    }
}

@Composable
private fun PointIqAllTournamentsScreenContainer(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window

    DisposableEffect(window, view) {
        if (window == null) {
            onDispose { }
        } else {
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            val previousStatusBarColor = window.statusBarColor
            val previousNavigationBarColor = window.navigationBarColor
            val previousLightStatusBars = windowInsetsController.isAppearanceLightStatusBars
            val previousLightNavigationBars = windowInsetsController.isAppearanceLightNavigationBars

            window.statusBarColor = PointIqAllTournamentsBackground.toArgb()
            window.navigationBarColor = PointIqAllTournamentsBackground.toArgb()
            windowInsetsController.isAppearanceLightStatusBars = false
            windowInsetsController.isAppearanceLightNavigationBars = false

            onDispose {
                window.statusBarColor = previousStatusBarColor
                window.navigationBarColor = previousNavigationBarColor
                windowInsetsController.isAppearanceLightStatusBars = previousLightStatusBars
                windowInsetsController.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PointIqAllTournamentsBackground)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PointIqAllTournamentsAmbientBlue.copy(alpha = 0.42f),
                            PointIqAllTournamentsAmbientBlue.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                        center = Offset(-size.width * 0.04f, size.height * 0.34f),
                        radius = size.width * 0.78f,
                    ),
                )
            }
            .padding(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}
