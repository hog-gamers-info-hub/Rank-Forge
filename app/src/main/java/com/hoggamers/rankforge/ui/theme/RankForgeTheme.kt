package com.hoggamers.rankforge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RankForgeLightColors = lightColorScheme(
    primary = Color(0xFF4F5D66),
    onPrimary = Color.White,
    secondary = Color(0xFF5E6062),
    onSecondary = Color.White,
    background = Color(0xFFF9F9F9),
    onBackground = Color(0xFF1A1C1D),
    surface = Color(0xFFF9F9F9),
    onSurface = Color(0xFF1A1C1D),
)

@Composable
fun RankForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RankForgeLightColors,
        content = content,
    )
}
