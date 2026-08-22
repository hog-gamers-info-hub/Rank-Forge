package com.hoggamers.rankforge.presentation.auth

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
        composeTestRule.onAllNodesWithText("You can use local tournaments without signing in.")
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Continue without signing in").assertCountEquals(0)
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
        composeTestRule.onAllNodesWithText("You can still use local tournaments without signing in.")
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Continue without signing in").assertCountEquals(0)
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
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_ACCOUNT_EMAIL_TEST_TAG)
            .assertIsDisplayed()
            .assertTextEquals("user@example.com")
        composeTestRule.onNodeWithTag(AUTH_LOGOUT_ACTION_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun signedInAccountShowsOnlyReadOnlyAccountControls() {
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
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.auth_title)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_ACCOUNT_HOME_ACTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_ACCOUNT_BACK_ACTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.auth_email_label)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_ACCOUNT_EMAIL_TEST_TAG)
            .assertIsDisplayed()
            .assertTextEquals("user@example.com")
        composeTestRule.onNodeWithTag(AUTH_LOGOUT_ACTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(AUTH_EMAIL_FIELD_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(AUTH_PASSWORD_FIELD_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(AUTH_SUBMIT_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithText(context.getString(R.string.auth_login_mode))
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText(context.getString(R.string.auth_signup_mode))
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText(
            context.getString(R.string.auth_signed_in_as, "user@example.com"),
        ).assertCountEquals(0)
    }

    @Test
    fun signedInAccountHomeBackAndSystemBackUseSeparateCallbacks() {
        var homeCount by mutableIntStateOf(0)
        var backCount by mutableIntStateOf(0)

        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(isSignedIn = true, accountEmail = "user@example.com"),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onSignedInHome = { homeCount += 1 },
                    onSignedInBack = { backCount += 1 },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_ACCOUNT_HOME_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(AUTH_ACCOUNT_BACK_ACTION_TEST_TAG).performClick()
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(1, homeCount)
        assertEquals(2, backCount)
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
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun forgotPasswordActionIsShownOnlyInLoginMode() {
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_FORGOT_PASSWORD_ACTION_TEST_TAG).assertIsDisplayed()

        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(mode = AuthMode.SignUp),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                )
            }
        }

        composeTestRule.onAllNodesWithTag(AUTH_FORGOT_PASSWORD_ACTION_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun forgotPasswordOpensRequestScreenAndPreservesEmail() {
        var uiState by mutableStateOf(AuthUiState(email = "user@example.com"))
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = uiState,
                    onModeSelected = {},
                    onEmailChanged = { uiState = uiState.copy(email = it) },
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onBeginPasswordRecovery = {
                        uiState = uiState.copy(passwordRecoveryStage = PasswordRecoveryStage.REQUEST_EMAIL)
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_FORGOT_PASSWORD_ACTION_TEST_TAG).performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.auth_forgot_password_heading))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_EMAIL_FIELD_TEST_TAG)
            .assertTextEquals("user@example.com")
    }

    @Test
    fun passwordResetRequestDisablesBlankEmailAndShowsEmailSentContent() {
        var uiState by mutableStateOf(
            AuthUiState(passwordRecoveryStage = PasswordRecoveryStage.REQUEST_EMAIL),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = uiState,
                    onModeSelected = {},
                    onEmailChanged = { uiState = uiState.copy(email = it) },
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onRequestPasswordReset = {
                        uiState = uiState.copy(passwordRecoveryStage = PasswordRecoveryStage.EMAIL_SENT)
                    },
                    onCancelPasswordRecovery = {
                        uiState = uiState.copy(passwordRecoveryStage = PasswordRecoveryStage.NONE)
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_PASSWORD_RESET_SUBMIT_ACTION_TEST_TAG)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(AUTH_EMAIL_FIELD_TEST_TAG).performTextInput("user@example.com")
        composeTestRule.onNodeWithTag(AUTH_PASSWORD_RESET_SUBMIT_ACTION_TEST_TAG).performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.auth_check_email_heading))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(R.string.auth_password_reset_email_sent_message),
        ).assertIsDisplayed()
    }

    @Test
    fun passwordRecoveryBackReturnsToLogin() {
        var uiState by mutableStateOf(
            AuthUiState(passwordRecoveryStage = PasswordRecoveryStage.EMAIL_SENT),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = uiState,
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onCancelPasswordRecovery = {
                        uiState = uiState.copy(passwordRecoveryStage = PasswordRecoveryStage.NONE)
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(AUTH_PASSWORD_RECOVERY_BACK_ACTION_TEST_TAG).performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.auth_login_heading)).assertIsDisplayed()
    }

    @Test
    fun verifyingPasswordRecoveryLinkShowsVerificationState() {
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(
                        passwordRecoveryStage = PasswordRecoveryStage.VERIFYING_LINK,
                    ),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                )
            }
        }

        composeTestRule.onNodeWithText(
            context.getString(R.string.auth_verifying_password_reset_link),
        ).assertIsDisplayed()
    }

    @Test
    fun passwordRecoveryLinkErrorShowsExpiryMessageAndCleansUpToLogin() {
        var uiState by mutableStateOf(
            AuthUiState(passwordRecoveryStage = PasswordRecoveryStage.LINK_ERROR),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = uiState,
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                    onExitPasswordRecovery = {
                        uiState = uiState.copy(passwordRecoveryStage = PasswordRecoveryStage.NONE)
                    },
                )
            }
        }

        composeTestRule.onNodeWithText(
            context.getString(R.string.auth_password_reset_link_error),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_PASSWORD_RECOVERY_BACK_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.auth_login_heading)).assertIsDisplayed()
    }

    @Test
    fun setNewPasswordScreenShowsMaskedFieldsAndDisabledUpdateForInvalidInput() {
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(
                        passwordRecoveryStage = PasswordRecoveryStage.SET_NEW_PASSWORD,
                        newPassword = "short",
                        confirmNewPassword = "different",
                    ),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.auth_set_new_password_heading))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_NEW_PASSWORD_FIELD_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_CONFIRM_NEW_PASSWORD_FIELD_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.auth_password_too_short_error))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.auth_passwords_do_not_match_error))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_UPDATE_PASSWORD_ACTION_TEST_TAG).assertIsNotEnabled()

        composeTestRule.onNodeWithTag(AUTH_NEW_PASSWORD_VISIBILITY_TEST_TAG).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.auth_hide_password)).assertIsDisplayed()
    }

    @Test
    fun matchingMinimumPasswordEnablesUpdate() {
        composeTestRule.setContent {
            RankForgeTheme {
                AuthScreen(
                    uiState = AuthUiState(
                        passwordRecoveryStage = PasswordRecoveryStage.SET_NEW_PASSWORD,
                        newPassword = "123456",
                        confirmNewPassword = "123456",
                    ),
                    onModeSelected = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                    onLogout = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.auth_new_password_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.auth_confirm_new_password_label))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_UPDATE_PASSWORD_ACTION_TEST_TAG)
            .assertIsEnabled()
            .assertTextEquals(context.getString(R.string.auth_update_password_action))
    }
}
