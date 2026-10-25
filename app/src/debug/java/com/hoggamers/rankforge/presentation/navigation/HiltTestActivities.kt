package com.hoggamers.rankforge.presentation.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HiltComposeTestActivity : ComponentActivity()

@AndroidEntryPoint
class HiltRecreationTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = requireNotNull(RankForgeRecreationActivityConfiguration.content) {
            "RankForgeRecreationActivityConfiguration.content must be installed by the test"
        }
        setContent { content() }
    }
}
