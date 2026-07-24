package com.hoggamers.rankforge.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val RankForgeLightColors = lightColorScheme(
    primary = RankForgePrimary,
    onPrimary = RankForgeOnPrimary,
    secondary = RankForgeSecondary,
    onSecondary = RankForgeOnSecondary,
    background = RankForgeBackground,
    onBackground = RankForgeOnBackground,
    surface = RankForgeSurface,
    onSurface = RankForgeOnSurface,
)

@Composable
fun RankForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RankForgeLightColors,
        typography = RankForgeTypography,
        content = content,
    )
}
