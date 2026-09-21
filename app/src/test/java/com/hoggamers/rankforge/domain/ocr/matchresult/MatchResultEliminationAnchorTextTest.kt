package com.hoggamers.rankforge.domain.ocr.matchresult

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchResultEliminationAnchorTextTest {
    @Test
    fun degradedPlayerSuffixRequiresLeadingAnchorAndWhitespaceSeparator() {
        assertEquals(
            "SASUKE",
            MatchResultEliminationAnchorText
                .playerSuffixAfterDegradedLeadingAnchorOrNull("EliminatYs SASUKE"),
        )
        assertEquals(
            "SASUKE 7?",
            MatchResultEliminationAnchorText
                .playerSuffixAfterDegradedLeadingAnchorOrNull("0 EliminatTYs SASUKE 7?"),
        )
        assertEquals(
            "PLAYER99",
            MatchResultEliminationAnchorText
                .playerSuffixAfterDegradedLeadingAnchorOrNull("O Eliminat6hs PLAYER99"),
        )
    }

    @Test
    fun degradedPlayerSuffixRejectsUnsafeConcatenatedOrNonLeadingText() {
        listOf(
            "EliminatYsSASUKE",
            "0 EliminatTYsSASUKE",
            "PLAYER EliminatYs SASUKE",
            "EliminatYs",
        ).forEach { text ->
            assertNull(
                MatchResultEliminationAnchorText
                    .playerSuffixAfterDegradedLeadingAnchorOrNull(text),
            )
        }
    }
}
