package com.hoggamers.rankforge.presentation.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.tournament.CreateTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentsUseCase
import com.hoggamers.rankforge.presentation.auth.AUTH_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.auth.AuthUiState
import com.hoggamers.rankforge.presentation.auth.AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_CREATION_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_LIST_AUTH_ENTRY_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TournamentCreationViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentListViewModel
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthNavigationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun signedOutLocalTournamentCreationRemainsAccessible() {
        val repository = InMemoryTournamentRepository()
        val creationViewModel = createCreationViewModel(repository)
        val listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    authUiState = AuthUiState(isSignedIn = false),
                    creationViewModel = creationViewModel,
                    listViewModel = listViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.open_tournament_creation)).performClick()

        composeTestRule.onNodeWithTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun accountEntryOpensAuthRouteWithoutGatingList() {
        val repository = InMemoryTournamentRepository()
        val creationViewModel = createCreationViewModel(repository)
        val listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    authUiState = AuthUiState(isSignedIn = false),
                    creationViewModel = creationViewModel,
                    listViewModel = listViewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_AUTH_ENTRY_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(AUTH_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun googleSignInActionIsThreadedThroughNavigation() {
        val repository = InMemoryTournamentRepository()
        val creationViewModel = createCreationViewModel(repository)
        val listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))
        var googleClickCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    authUiState = AuthUiState(isSignedIn = false),
                    onAuthGoogleSignIn = { googleClickCount += 1 },
                    creationViewModel = creationViewModel,
                    listViewModel = listViewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_AUTH_ENTRY_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG).performClick()

        assertEquals(1, googleClickCount)
    }

    private fun createCreationViewModel(
        repository: InMemoryTournamentRepository,
    ): TournamentCreationViewModel {
        val today = LocalDate.of(2026, 7, 24)
        return TournamentCreationViewModel(
            CreateTournamentUseCase(
                repository = repository,
                clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            ),
        )
    }
}
