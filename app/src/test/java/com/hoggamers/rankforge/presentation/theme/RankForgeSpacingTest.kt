package com.hoggamers.rankforge.presentation.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class RankForgeSpacingTest {
    @Test
    fun spacingTokensMatchApprovedValues() {
        assertEquals(4.dp, RankForgeSpacing.ExtraSmall)
        assertEquals(8.dp, RankForgeSpacing.Small)
        assertEquals(16.dp, RankForgeSpacing.Medium)
        assertEquals(24.dp, RankForgeSpacing.Large)
        assertEquals(32.dp, RankForgeSpacing.ExtraLarge)
    }
}
