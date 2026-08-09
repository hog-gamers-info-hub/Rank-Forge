package com.hoggamers.rankforge.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.presentation.auth.AuthViewModel
import com.hoggamers.rankforge.presentation.navigation.RankForgeNavHost
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

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
            RankForgeNavHost(
                authUiState = authUiState,
                onAuthModeSelected = authViewModel::setMode,
                onAuthEmailChanged = authViewModel::onEmailChanged,
                onAuthPasswordChanged = authViewModel::onPasswordChanged,
                onAuthSubmit = authViewModel::submit,
                onAuthGoogleSignIn = authViewModel::signInWithGoogle,
                onAuthLogout = authViewModel::logout,
            )
        }
    }
}
