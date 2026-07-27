package com.hoggamers.rankforge.presentation.auth

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.R
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
        composeTestRule.onNodeWithTag(AUTH_EMAIL_FIELD_TEST_TAG).performTextInput("new@example.com")
        composeTestRule.onNodeWithTag(AUTH_PASSWORD_FIELD_TEST_TAG).performTextInput("password")
        composeTestRule.onNodeWithTag(AUTH_SUBMIT_ACTION_TEST_TAG).performClick()

        assertEquals(AuthMode.SignUp, uiState.mode)
        assertEquals(1, submitCount)
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
}
