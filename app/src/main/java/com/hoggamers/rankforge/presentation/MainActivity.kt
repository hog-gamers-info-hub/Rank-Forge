package com.hoggamers.rankforge.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var supabaseClientProvider: SupabaseClientProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleAuthCallback(intent)
        setContent {
            RankForgeApp()
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
        supabaseClientProvider.client.handleDeeplinks(intent)
    }
}
