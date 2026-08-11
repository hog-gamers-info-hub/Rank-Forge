package com.hoggamers.rankforge.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import com.hoggamers.rankforge.R

@Composable
fun RankForgeTheme(content: @Composable () -> Unit) {
    val rankForgeBackground = colorResource(R.color.rank_forge_background)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = RankForgePrimary,
            onPrimary = RankForgeOnPrimary,
            secondary = RankForgeSecondary,
            onSecondary = RankForgeOnSecondary,
            background = rankForgeBackground,
            onBackground = RankForgeOnBackground,
            surface = rankForgeBackground,
            onSurface = RankForgeOnSurface,
        ),
        typography = RankForgeTypography,
        content = content,
    )
}
