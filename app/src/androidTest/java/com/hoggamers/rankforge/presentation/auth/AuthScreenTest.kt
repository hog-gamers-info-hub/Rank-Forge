package com.hoggamers.rankforge.presentation.auth

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun loginModeShowsApprovedLayoutWithoutSignedOutCard() {
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.auth_brand_name)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.auth_login_heading)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_EMAIL_FIELD_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_PASSWORD_FIELD_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_SUBMIT_ACTION_TEST_TAG)
            .assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.auth_log_in_action))
        composeTestRule.onNodeWithText(context.getString(R.string.auth_google_continue_action)).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(R.string.auth_login_local_tournaments_message),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(R.string.auth_continue_without_signing_in_action),
        ).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(context.getString(R.string.auth_signed_out)).assertCountEquals(0)
    }

    @Test
    fun loginPasswordStartsHiddenAndShowHideTogglesVisibility() {
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.auth_show_password)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(context.getString(R.string.auth_hide_password)).assertCountEquals(0)

        composeTestRule.onNodeWithTag(AUTH_PASSWORD_VISIBILITY_TEST_TAG).performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.auth_hide_password)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(context.getString(R.string.auth_show_password)).assertCountEquals(0)

        composeTestRule.onNodeWithTag(AUTH_PASSWORD_VISIBILITY_TEST_TAG).performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.auth_show_password)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(context.getString(R.string.auth_hide_password)).assertCountEquals(0)
    }

    @Test
    fun loginSubmitCallbackStillWorks() {
        var uiState by mutableStateOf(AuthUiState())
        var submitCount by mutableIntStateOf(0)

        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = uiState,
                    onModeSelected = { mode -> uiState = uiState.copy(mode = mode) },
                    onEmailChanged = { email -> uiState = uiState.copy(email = email) },
                    onPasswordChanged = { password -> uiState = uiState.copy(password = password) },
                    onSubmit = { submitCount += 1 },
                    onLogout = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_EMAIL_FIELD_TEST_TAG).performTextInput("user@example.com")
        composeTestRule.onNodeWithTag(AUTH_PASSWORD_FIELD_TEST_TAG).performTextInput("password")
        composeTestRule.onNodeWithTag(AUTH_SUBMIT_ACTION_TEST_TAG).performClick()

        assertEquals(AuthMode.Login, uiState.mode)
        assertEquals(1, submitCount)
    }

    @Test
    fun loginContinueWithoutSigningInInvokesOnBack() {
        var backCount by mutableIntStateOf(0)

        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onBack = { backCount += 1 },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_CONTINUE_WITHOUT_SIGNING_IN_ACTION_TEST_TAG).performClick()

        assertEquals(1, backCount)
    }

    @Test
    fun loginSubmittingStateDisablesRelevantActions() {
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(
                        email = "user@example.com",
                        password = "password",
                        isSubmitting = true,
                    ),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_SUBMIT_ACTION_TEST_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(AUTH_PASSWORD_VISIBILITY_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun signUpModeShowsApprovedLayout() {
        var uiState by mutableStateOf(AuthUiState())

        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = uiState,
                    onModeSelected = { mode -> uiState = uiState.copy(mode = mode) },
                    onEmailChanged = { email -> uiState = uiState.copy(email = email) },
                    onPasswordChanged = { password -> uiState = uiState.copy(password = password) },
                    onSubmit = {},
                    onLogout = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.auth_signup_mode)).performClick()

        composeTestRule.onAllNodesWithText("Back").assertCountEquals(0)
        composeTestRule.onNodeWithText(context.getString(R.string.auth_brand_name)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.auth_signup_heading)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_EMAIL_FIELD_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_PASSWORD_FIELD_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.auth_create_account_action)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.auth_google_continue_action)).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(R.string.auth_continue_without_signing_in_action),
        ).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(context.getString(R.string.auth_signed_out)).assertCountEquals(0)
    }

    @Test
    fun signUpModeAcceptsCredentialsWithoutRealSupabaseCredentials() {
        var uiState by mutableStateOf(AuthUiState())
        var submitCount by mutableIntStateOf(0)

        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = uiState,
                    onModeSelected = { mode -> uiState = uiState.copy(mode = mode) },
                    onEmailChanged = { email -> uiState = uiState.copy(email = email) },
                    onPasswordChanged = { password -> uiState = uiState.copy(password = password) },
                    onSubmit = { submitCount += 1 },
                    onLogout = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.auth_signup_mode)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.auth_create_account_action)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_EMAIL_FIELD_TEST_TAG).performTextInput("new@example.com")
        composeTestRule.onNodeWithTag(AUTH_PASSWORD_FIELD_TEST_TAG).performTextInput("password")
        composeTestRule.onNodeWithTag(AUTH_SUBMIT_ACTION_TEST_TAG).performClick()

        assertEquals(AuthMode.SignUp, uiState.mode)
        assertEquals(1, submitCount)
    }

    @Test
    fun continueWithoutSigningInInvokesOnBack() {
        var uiState by mutableStateOf(AuthUiState())
        var backCount by mutableIntStateOf(0)

        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = uiState,
                    onModeSelected = { mode -> uiState = uiState.copy(mode = mode) },
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onBack = { backCount += 1 },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.auth_signup_mode)).performClick()
        composeTestRule.onNodeWithTag(AUTH_CONTINUE_WITHOUT_SIGNING_IN_ACTION_TEST_TAG).performClick()

        assertEquals(1, backCount)
    }

    @Test
    fun passwordStartsHiddenAndShowHideTogglesVisibility() {
        var uiState by mutableStateOf(AuthUiState())

        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = uiState,
                    onModeSelected = { mode -> uiState = uiState.copy(mode = mode) },
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.auth_signup_mode)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.auth_show_password)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(context.getString(R.string.auth_hide_password)).assertCountEquals(0)

        composeTestRule.onNodeWithTag(AUTH_PASSWORD_VISIBILITY_TEST_TAG).performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.auth_hide_password)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(context.getString(R.string.auth_show_password)).assertCountEquals(0)
    }

    @Test
    fun signUpGoogleCallbackStillWorks() {
        var uiState by mutableStateOf(AuthUiState())
        var googleClickCount by mutableIntStateOf(0)

        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = uiState,
                    onModeSelected = { mode -> uiState = uiState.copy(mode = mode) },
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onBack = {},
                    onGoogleSignIn = { googleClickCount += 1 },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.auth_signup_mode)).performClick()
        composeTestRule.onNodeWithTag(AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG).performClick()

        assertEquals(1, googleClickCount)
    }

    @Test
    fun signUpSubmittingStateDisablesRelevantActions() {
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(
                        mode = AuthMode.SignUp,
                        email = "new@example.com",
                        password = "password",
                        isSubmitting = true,
                    ),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_SUBMIT_ACTION_TEST_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(AUTH_PASSWORD_VISIBILITY_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun signedInStateShowsLogoutAction() {
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(
                        isSignedIn = true,
                        accountEmail = "user@example.com",
                    ),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText(
            context.getString(R.string.auth_signed_in_as, "user@example.com"),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_LOGOUT_ACTION_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun restorationWarningIsDisplayedWithoutSignedInState() {
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(
                        warningMessage = AuthUiMessage.RestorationWarning(
                            AuthFailureCategory.NetworkUnavailable,
                        ),
                    ),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_WARNING_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(R.string.auth_restoration_network_warning),
        ).assertIsDisplayed()
    }

    @Test
    fun accountAlreadyRegisteredUsesErrorMessagePath() {
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(
                        errorMessage = AuthUiMessage.AuthenticationFailure(
                            AuthFailureCategory.AccountAlreadyRegistered,
                        ),
                    ),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_ERROR_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(R.string.auth_failure_account_already_registered),
        ).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(AUTH_STATUS_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun signUpOutcomesUseDistinctMessages() {
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(
                        statusMessage = AuthUiMessage.SignUpConfirmationRequired,
                    ),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText(
            context.getString(R.string.auth_signup_confirmation_required_message),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_STATUS_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(AUTH_ERROR_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun googleSignInActionIsIndependentOfEmailPasswordFields() {
        var googleClickCount by mutableIntStateOf(0)
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onBack = {},
                    onGoogleSignIn = { googleClickCount += 1 },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG).assertIsDisplayed().performClick()

        assertEquals(1, googleClickCount)
    }

    @Test
    fun googleSignInActionIsDisabledWhileSubmitting() {
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(isSubmitting = true),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG).assertIsNotEnabled()
    }
}
