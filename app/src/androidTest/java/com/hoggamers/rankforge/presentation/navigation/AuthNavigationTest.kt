package com.hoggamers.rankforge.presentation.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.presentation.AUTH_SESSION_LOADING_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.RankForgeAppContent
import com.hoggamers.rankforge.presentation.auth.AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.auth.AUTH_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.auth.AuthMode
import com.hoggamers.rankforge.presentation.auth.AuthUiState
import com.hoggamers.rankforge.presentation.auth.PasswordRecoveryStage
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_LIST_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TournamentListViewModel
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentsUseCase
import com.hoggamers.rankforge.presentation.navigation.RankForgeNavHost
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthNavigationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sessionLoadingShowsOnlyOpaqueLoadingState() {
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = AuthUiState(isSessionLoading = true),
                    authenticatedContent = { Text("protected_home", Modifier.testTag("protected_home")) },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_SESSION_LOADING_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Rank-Forge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Checking account session.").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(AUTH_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("protected_home").assertCountEquals(0)
    }

    @Test
    fun signedOutAuthKeepsGoogleCallbackAvailable() {
        var googleClickCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = AuthUiState(),
                    onAuthGoogleSignIn = { googleClickCount += 1 },
                    authenticatedContent = { Text("protected_home", Modifier.testTag("protected_home")) },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG).performClick()
        composeTestRule.runOnIdle { org.junit.Assert.assertEquals(1, googleClickCount) }
    }

    @Test
    fun signedOutShowsAuthAndNoProtectedContent() {
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = AuthUiState(),
                    authenticatedContent = { Text("protected_home", Modifier.testTag("protected_home")) },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("protected_home").assertCountEquals(0)
    }

    @Test
    fun signedInShowsProtectedContentEvenWhileSessionLoading() {
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = AuthUiState(isSignedIn = true, isSessionLoading = true),
                    authenticatedContent = { Text("protected_home", Modifier.testTag("protected_home")) },
                )
            }
        }

        composeTestRule.onNodeWithTag("protected_home").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(AUTH_SESSION_LOADING_SCREEN_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun activePasswordRecoveryWinsOverSignedInProtectedRouting() {
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = AuthUiState(
                        isSignedIn = true,
                        accountEmail = "recovery@example.com",
                        passwordRecoveryStage = PasswordRecoveryStage.VERIFYING_LINK,
                    ),
                    authenticatedContent = {
                        Text("protected_home", Modifier.testTag("protected_home"))
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Verifying reset link…").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("protected_home").assertCountEquals(0)
    }

    @Test
    fun signedOutToSignedInTransitionRemovesAuthAndShowsProtectedContent() {
        var authUiState by mutableStateOf(AuthUiState())
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = authUiState,
                    authenticatedContent = { Text("protected_home", Modifier.testTag("protected_home")) },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.runOnIdle { authUiState = AuthUiState(isSignedIn = true) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("protected_home").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(AUTH_SCREEN_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun logoutOrSessionLossRemovesProtectedContentAndShowsAuth() {
        var authUiState by mutableStateOf(AuthUiState(isSignedIn = true))
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = authUiState,
                    authenticatedContent = { Text("protected_home", Modifier.testTag("protected_home")) },
                )
            }
        }

        composeTestRule.onNodeWithTag("protected_home").assertIsDisplayed()
        composeTestRule.runOnIdle { authUiState = AuthUiState() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(AUTH_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("protected_home").assertCountEquals(0)
    }

    @Test
    fun signupConfirmationRemainsOnAuthGate() {
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = AuthUiState(
                        mode = AuthMode.SignUp,
                        statusMessage = com.hoggamers.rankforge.presentation.auth.AuthUiMessage.SignUpConfirmationRequired,
                    ),
                    authenticatedContent = { Text("protected_home", Modifier.testTag("protected_home")) },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext
                .getString(R.string.auth_signup_confirmation_required_message),
        ).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("protected_home").assertCountEquals(0)
    }

    @Test
    fun startupRestoredSessionShowsRealTournamentListWithoutInteractiveTransition() {
        val listViewModel = createListViewModel()
        val restoredState = AuthUiState(
            isSignedIn = true,
            accountEmail = "restored@example.com",
        )

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = restoredState,
                    authenticatedContent = {
                        RankForgeNavHost(
                            authUiState = restoredState,
                            listViewModel = listViewModel,
                        )
                    },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(AUTH_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertCountEquals(1)
    }

    @Test
    fun interactiveEmailLoginSuccessShowsRealTournamentListExactlyOnce() {
        var authUiState by mutableStateOf(AuthUiState())
        val listViewModel = createListViewModel()

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = authUiState,
                    authenticatedContent = {
                        RankForgeNavHost(
                            authUiState = authUiState,
                            listViewModel = listViewModel,
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.runOnIdle {
            authUiState = AuthUiState(
                isSignedIn = true,
                accountEmail = "login@example.com",
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(AUTH_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertCountEquals(1)
    }

    @Test
    fun interactiveAuthenticatedSignUpSuccessShowsRealTournamentListExactlyOnce() {
        var authUiState by mutableStateOf(AuthUiState(mode = AuthMode.SignUp))
        val listViewModel = createListViewModel()

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = authUiState,
                    authenticatedContent = {
                        RankForgeNavHost(
                            authUiState = authUiState,
                            listViewModel = listViewModel,
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.runOnIdle {
            authUiState = AuthUiState(
                mode = AuthMode.SignUp,
                isSignedIn = true,
                accountEmail = "signup@example.com",
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(AUTH_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertCountEquals(1)
    }

    @Test
    fun confirmationRequiredSignUpKeepsAuthAndHidesRealTournamentList() {
        val listViewModel = createListViewModel()
        val authState = AuthUiState(
            mode = AuthMode.SignUp,
            statusMessage = com.hoggamers.rankforge.presentation.auth.AuthUiMessage.SignUpConfirmationRequired,
        )

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = authState,
                    authenticatedContent = {
                        RankForgeNavHost(
                            authUiState = authState,
                            listViewModel = listViewModel,
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun failedEmailAuthKeepsAuthAndHidesRealTournamentList() {
        val listViewModel = createListViewModel()
        val authState = AuthUiState(
            errorMessage = com.hoggamers.rankforge.presentation.auth.AuthUiMessage.AuthenticationFailure(
                com.hoggamers.rankforge.domain.auth.AuthFailureCategory.InvalidCredentials,
            ),
        )

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = authState,
                    authenticatedContent = {
                        RankForgeNavHost(
                            authUiState = authState,
                            listViewModel = listViewModel,
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun googleLaunchOnlyKeepsAuthAndHidesRealTournamentList() {
        val listViewModel = createListViewModel()
        val authState = AuthUiState(
            statusMessage = com.hoggamers.rankforge.presentation.auth.AuthUiMessage.ExternalAuthenticationLaunched,
        )

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = authState,
                    authenticatedContent = {
                        RankForgeNavHost(
                            authUiState = authState,
                            listViewModel = listViewModel,
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun googleCallbackSuccessShowsRealTournamentListExactlyOnce() {
        var authUiState by mutableStateOf(
            AuthUiState(
                statusMessage = com.hoggamers.rankforge.presentation.auth.AuthUiMessage.ExternalAuthenticationLaunched,
            ),
        )
        val listViewModel = createListViewModel()

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = authUiState,
                    authenticatedContent = {
                        RankForgeNavHost(
                            authUiState = authUiState,
                            listViewModel = listViewModel,
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.runOnIdle {
            authUiState = AuthUiState(
                isSignedIn = true,
                accountEmail = "google@example.com",
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(AUTH_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertCountEquals(1)
    }

    @Test
    fun googleCancellationWithoutCallbackKeepsAuthAndHidesRealTournamentList() {
        val listViewModel = createListViewModel()
        val authState = AuthUiState()

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = authState,
                    authenticatedContent = {
                        RankForgeNavHost(
                            authUiState = authState,
                            listViewModel = listViewModel,
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun repeatedSignedInStateDoesNotDuplicateRealTournamentList() {
        var authUiState by mutableStateOf(AuthUiState())
        val listViewModel = createListViewModel()

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeAppContent(
                    authUiState = authUiState,
                    authenticatedContent = {
                        RankForgeNavHost(
                            authUiState = authUiState,
                            listViewModel = listViewModel,
                        )
                    },
                )
            }
        }

        composeTestRule.runOnIdle {
            authUiState = AuthUiState(
                isSignedIn = true,
                accountEmail = "google@example.com",
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertCountEquals(1)

        composeTestRule.runOnIdle {
            authUiState = AuthUiState(
                isSignedIn = true,
                accountEmail = "google@example.com",
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(AUTH_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertCountEquals(1)
    }

    private fun createListViewModel(): TournamentListViewModel =
        TournamentListViewModel(
            ObserveTournamentsUseCase(InMemoryTournamentRepository()),
        )
}
