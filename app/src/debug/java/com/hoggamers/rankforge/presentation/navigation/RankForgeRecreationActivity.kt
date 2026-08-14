package com.hoggamers.rankforge.presentation.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable

object RankForgeRecreationActivityConfiguration {
    @Volatile
    var content: (@Composable () -> Unit)? = null
}

class RankForgeRecreationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = requireNotNull(RankForgeRecreationActivityConfiguration.content) {
            "RankForgeRecreationActivityConfiguration.content must be installed by the test"
        }
        setContent { content() }
    }
}
