package com.hoggamers.rankforge.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hoggamers.rankforge.presentation.navigation.RankForgeNavHost
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

@Composable
fun RankForgeApp() {
    RankForgeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            RankForgeNavHost()
        }
    }
}
