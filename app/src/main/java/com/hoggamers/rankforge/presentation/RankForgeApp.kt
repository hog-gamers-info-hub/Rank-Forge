package com.hoggamers.rankforge.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.presentation.auth.AUTH_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.auth.AuthViewModel
import com.hoggamers.rankforge.presentation.auth.AuthMode
import com.hoggamers.rankforge.presentation.auth.AuthScreen
import com.hoggamers.rankforge.presentation.auth.AuthUiMessage
import com.hoggamers.rankforge.presentation.auth.AuthUiState
import com.hoggamers.rankforge.presentation.auth.PointIqAuthMessages
import com.hoggamers.rankforge.presentation.auth.PointIqLoginScreen
import com.hoggamers.rankforge.presentation.auth.PointIqSignUpScreen
import com.hoggamers.rankforge.presentation.navigation.RankForgeNavHost
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi

const val AUTH_SESSION_LOADING_SCREEN_TEST_TAG = "auth_session_loading_screen"
const val AUTH_EXTERNAL_AUTH_CALLBACK_PROCESSING_TEST_TAG = "auth_external_auth_callback_processing"
const val AUTH_RESTORATION_ERROR_SCREEN_TEST_TAG = "auth_restoration_error_screen"
const val AUTH_RESTORATION_RETRY_TEST_TAG = "auth_restoration_retry"

@Composable
@OptIn(ExperimentalCoroutinesApi::class)
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
                onAuthRetrySessionRestoration = authViewModel::retrySessionRestoration,
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
    onAuthRetrySessionRestoration: () -> Unit = {},
    authenticatedContent: @Composable () -> Unit,
) {
    val restorationWarning = authUiState.warningMessage as? AuthUiMessage.RestorationWarning
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
        authUiState.isRestorationRetryPending && restorationWarning != null ->
            AuthRestorationErrorScreen(
                category = restorationWarning.category,
                isRetrying = authUiState.isSessionLoading,
                onRetry = onAuthRetrySessionRestoration,
            )
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
private fun AuthRestorationErrorScreen(
    category: AuthFailureCategory,
    isRetrying: Boolean,
    onRetry: () -> Unit,
) {
    val titleResource = when (category) {
        AuthFailureCategory.NetworkUnavailable -> R.string.auth_restoration_network_title
        AuthFailureCategory.Timeout -> R.string.auth_restoration_timeout_title
        else -> R.string.auth_restoration_unknown_title
    }
    val descriptionResource = when (category) {
        AuthFailureCategory.NetworkUnavailable -> R.string.auth_restoration_network_description
        AuthFailureCategory.Timeout -> R.string.auth_restoration_timeout_description
        else -> R.string.auth_restoration_unknown_description
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(AUTH_RESTORATION_ERROR_SCREEN_TEST_TAG)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(titleResource),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(descriptionResource),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            enabled = !isRetrying,
            modifier = Modifier.testTag(AUTH_RESTORATION_RETRY_TEST_TAG),
        ) {
            Text(text = stringResource(R.string.auth_restoration_retry_action))
        }
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
