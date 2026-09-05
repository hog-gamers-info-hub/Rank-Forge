package com.hoggamers.rankforge.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.presentation.auth.AUTH_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.auth.AuthViewModel
import com.hoggamers.rankforge.presentation.auth.AuthMode
import com.hoggamers.rankforge.presentation.auth.AuthScreen
import com.hoggamers.rankforge.presentation.auth.AuthUiState
import com.hoggamers.rankforge.presentation.auth.PointIqAuthMessages
import com.hoggamers.rankforge.presentation.auth.PointIqLoginScreen
import com.hoggamers.rankforge.presentation.auth.PointIqSignUpScreen
import com.hoggamers.rankforge.presentation.navigation.RankForgeNavHost
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

const val AUTH_SESSION_LOADING_SCREEN_TEST_TAG = "auth_session_loading_screen"
const val AUTH_EXTERNAL_AUTH_CALLBACK_PROCESSING_TEST_TAG = "auth_external_auth_callback_processing"

@Composable
fun RankForgeApp(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()

    RankForgeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            RankForgeAppContent(
                authUiState = authUiState,
                onAuthModeSelected = authViewModel::setMode,
                onAuthEmailChanged = authViewModel::onEmailChanged,
                onAuthPasswordChanged = authViewModel::onPasswordChanged,
                onAuthSubmit = authViewModel::submit,
                onAuthGoogleSignIn = authViewModel::signInWithGoogle,
                onAuthLogout = authViewModel::logout,
                onAuthBeginPasswordRecovery = authViewModel::beginPasswordRecovery,
                onAuthCancelPasswordRecovery = authViewModel::cancelPasswordRecovery,
                onAuthRequestPasswordReset = authViewModel::requestPasswordReset,
                onAuthNewPasswordChanged = authViewModel::onNewPasswordChanged,
                onAuthConfirmNewPasswordChanged = authViewModel::onConfirmNewPasswordChanged,
                onAuthUpdateRecoveredPassword = authViewModel::updateRecoveredPassword,
                onAuthExitPasswordRecovery = authViewModel::exitPasswordRecovery,
                onAuthDeleteAccountConfirmed = authViewModel::deleteAccount,
                authenticatedContent = {
                    RankForgeNavHost(
                        authUiState = authUiState,
                        onAuthModeSelected = authViewModel::setMode,
                        onAuthEmailChanged = authViewModel::onEmailChanged,
                        onAuthPasswordChanged = authViewModel::onPasswordChanged,
                        onAuthSubmit = authViewModel::submit,
                        onAuthGoogleSignIn = authViewModel::signInWithGoogle,
                        onAuthLogout = authViewModel::logout,
                        onAuthDeleteAccountConfirmed = authViewModel::deleteAccount,
                        matchLobbyScreenshotIntakeViewModelProvider = { _, _ -> hiltViewModel() },
                    )
                },
            )
        }
    }
}

@Composable
fun RankForgeAppContent(
    authUiState: AuthUiState,
    onAuthModeSelected: (AuthMode) -> Unit = {},
    onAuthEmailChanged: (String) -> Unit = {},
    onAuthPasswordChanged: (String) -> Unit = {},
    onAuthSubmit: () -> Unit = {},
    onAuthGoogleSignIn: () -> Unit = {},
    onAuthLogout: () -> Unit = {},
    onAuthBeginPasswordRecovery: () -> Unit = {},
    onAuthCancelPasswordRecovery: () -> Unit = {},
    onAuthRequestPasswordReset: () -> Unit = {},
    onAuthNewPasswordChanged: (String) -> Unit = {},
    onAuthConfirmNewPasswordChanged: (String) -> Unit = {},
    onAuthUpdateRecoveredPassword: () -> Unit = {},
    onAuthExitPasswordRecovery: () -> Unit = {},
    onAuthDeleteAccountConfirmed: () -> Unit = {},
    authenticatedContent: @Composable () -> Unit,
) {
    when {
        authUiState.accountDeletionState != com.hoggamers.rankforge.presentation.auth.AccountDeletionUiState.IDLE -> AuthScreen(
            uiState = authUiState,
            onModeSelected = onAuthModeSelected,
            onEmailChanged = onAuthEmailChanged,
            onPasswordChanged = onAuthPasswordChanged,
            onSubmit = onAuthSubmit,
            onLogout = onAuthLogout,
            onGoogleSignIn = onAuthGoogleSignIn,
            onBeginPasswordRecovery = onAuthBeginPasswordRecovery,
            onCancelPasswordRecovery = onAuthCancelPasswordRecovery,
            onRequestPasswordReset = onAuthRequestPasswordReset,
            onNewPasswordChanged = onAuthNewPasswordChanged,
            onConfirmNewPasswordChanged = onAuthConfirmNewPasswordChanged,
            onUpdateRecoveredPassword = onAuthUpdateRecoveredPassword,
            onExitPasswordRecovery = onAuthExitPasswordRecovery,
            onDeleteAccountConfirmed = onAuthDeleteAccountConfirmed,
        )
        authUiState.isPasswordRecoveryActive -> AuthScreen(
            uiState = authUiState,
            onModeSelected = onAuthModeSelected,
            onEmailChanged = onAuthEmailChanged,
            onPasswordChanged = onAuthPasswordChanged,
            onSubmit = onAuthSubmit,
            onLogout = onAuthLogout,
            onGoogleSignIn = onAuthGoogleSignIn,
            onBeginPasswordRecovery = onAuthBeginPasswordRecovery,
            onCancelPasswordRecovery = onAuthCancelPasswordRecovery,
            onRequestPasswordReset = onAuthRequestPasswordReset,
            onNewPasswordChanged = onAuthNewPasswordChanged,
            onConfirmNewPasswordChanged = onAuthConfirmNewPasswordChanged,
            onUpdateRecoveredPassword = onAuthUpdateRecoveredPassword,
            onExitPasswordRecovery = onAuthExitPasswordRecovery,
            onDeleteAccountConfirmed = onAuthDeleteAccountConfirmed,
        )
        authUiState.isSignedIn -> authenticatedContent()
        authUiState.isExternalAuthCallbackProcessing -> ExternalAuthCallbackProcessingScreen()
        authUiState.isSessionLoading -> AuthSessionLoadingScreen()
        authUiState.mode == AuthMode.Login -> PointIqLoginScreen(
            uiState = authUiState,
            onModeSelected = onAuthModeSelected,
            onEmailChanged = onAuthEmailChanged,
            onPasswordChanged = onAuthPasswordChanged,
            onSubmit = onAuthSubmit,
            onGoogleSignIn = onAuthGoogleSignIn,
            onBeginPasswordRecovery = onAuthBeginPasswordRecovery,
            modifier = Modifier.testTag(AUTH_SCREEN_TEST_TAG),
            messages = { PointIqAuthMessages(authUiState) },
        )
        authUiState.mode == AuthMode.SignUp -> PointIqSignUpScreen(
            uiState = authUiState,
            onModeSelected = onAuthModeSelected,
            onEmailChanged = onAuthEmailChanged,
            onPasswordChanged = onAuthPasswordChanged,
            onSubmit = onAuthSubmit,
            onGoogleSignIn = onAuthGoogleSignIn,
            modifier = Modifier.testTag(AUTH_SCREEN_TEST_TAG),
            messages = { PointIqAuthMessages(authUiState) },
        )
        else -> AuthScreen(
            uiState = authUiState,
            onModeSelected = onAuthModeSelected,
            onEmailChanged = onAuthEmailChanged,
            onPasswordChanged = onAuthPasswordChanged,
            onSubmit = onAuthSubmit,
            onLogout = onAuthLogout,
            onGoogleSignIn = onAuthGoogleSignIn,
            onBeginPasswordRecovery = onAuthBeginPasswordRecovery,
            onCancelPasswordRecovery = onAuthCancelPasswordRecovery,
            onRequestPasswordReset = onAuthRequestPasswordReset,
            onNewPasswordChanged = onAuthNewPasswordChanged,
            onConfirmNewPasswordChanged = onAuthConfirmNewPasswordChanged,
            onUpdateRecoveredPassword = onAuthUpdateRecoveredPassword,
            onExitPasswordRecovery = onAuthExitPasswordRecovery,
            onDeleteAccountConfirmed = onAuthDeleteAccountConfirmed,
        )
    }
}

@Composable
private fun ExternalAuthCallbackProcessingScreen() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(AUTH_EXTERNAL_AUTH_CALLBACK_PROCESSING_TEST_TAG),
        color = MaterialTheme.colorScheme.background,
    ) {}
}

@Composable
private fun AuthSessionLoadingScreen() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(AUTH_SESSION_LOADING_SCREEN_TEST_TAG),
        color = MaterialTheme.colorScheme.background,
    ) {}
}
