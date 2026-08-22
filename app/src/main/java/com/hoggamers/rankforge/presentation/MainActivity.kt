package com.hoggamers.rankforge.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.presentation.auth.AuthCallbackClassifier
import com.hoggamers.rankforge.presentation.auth.AuthCallbackKind
import com.hoggamers.rankforge.presentation.auth.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    @Inject
    lateinit var supabaseClientProvider: SupabaseClientProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleAuthCallback(intent)
        setContent {
            RankForgeApp(authViewModel = authViewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    private fun handleAuthCallback(intent: Intent) {
        val data = intent.data ?: return
        if (intent.action != Intent.ACTION_VIEW ||
            data.scheme != SupabaseAuthConfig.AUTH_CALLBACK_SCHEME ||
            data.host != SupabaseAuthConfig.AUTH_CALLBACK_HOST
        ) {
            return
        }
        when (
            AuthCallbackClassifier.classify(
                scheme = data.scheme,
                host = data.host,
                path = data.path,
            )
        ) {
            AuthCallbackKind.NORMAL_AUTH_CALLBACK -> {
                supabaseClientProvider.client.handleDeeplinks(intent)
            }
            AuthCallbackKind.PASSWORD_RECOVERY_CALLBACK -> {
                authViewModel.onPasswordRecoveryLinkReceived()
                supabaseClientProvider.client.handleDeeplinks(
                    intent = intent,
                    onSessionSuccess = { authViewModel.onPasswordRecoveryLinkVerified() },
                    onError = { authViewModel.onPasswordRecoveryLinkFailed() },
                )
                if (
                    data.getQueryParameter("code").isNullOrBlank() ||
                        data.getQueryParameter("error") != null ||
                        data.getQueryParameter("error_code") != null ||
                        data.getQueryParameter("error_description") != null
                ) {
                    authViewModel.onPasswordRecoveryLinkFailed()
                }
            }
        }
    }
}
