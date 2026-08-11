package com.hoggamers.rankforge.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.auth.AuthViewModel
import com.hoggamers.rankforge.presentation.auth.AuthMode
import com.hoggamers.rankforge.presentation.auth.AuthScreen
import com.hoggamers.rankforge.presentation.auth.AuthUiState
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.navigation.RankForgeNavHost
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val AUTH_SESSION_LOADING_SCREEN_TEST_TAG = "auth_session_loading_screen"

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
                authenticatedContent = {
                    RankForgeNavHost(
                        authUiState = authUiState,
                        onAuthModeSelected = authViewModel::setMode,
                        onAuthEmailChanged = authViewModel::onEmailChanged,
                        onAuthPasswordChanged = authViewModel::onPasswordChanged,
                        onAuthSubmit = authViewModel::submit,
                        onAuthGoogleSignIn = authViewModel::signInWithGoogle,
                        onAuthLogout = authViewModel::logout,
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
    authenticatedContent: @Composable () -> Unit,
) {
    when {
        authUiState.isSignedIn -> authenticatedContent()
        authUiState.isSessionLoading -> AuthSessionLoadingScreen()
        else -> AuthScreen(
            uiState = authUiState,
            onModeSelected = onAuthModeSelected,
            onEmailChanged = onAuthEmailChanged,
            onPasswordChanged = onAuthPasswordChanged,
            onSubmit = onAuthSubmit,
            onLogout = onAuthLogout,
            onGoogleSignIn = onAuthGoogleSignIn,
        )
    }
}

@Composable
private fun AuthSessionLoadingScreen() {
    RankForgeScreenContainer(
        modifier = Modifier.testTag(AUTH_SESSION_LOADING_SCREEN_TEST_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.auth_brand_name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Text(
            text = stringResource(R.string.auth_checking_session),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
