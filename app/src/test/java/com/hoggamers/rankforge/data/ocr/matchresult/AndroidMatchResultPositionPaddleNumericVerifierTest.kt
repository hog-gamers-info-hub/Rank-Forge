package com.hoggamers.rankforge.data.ocr.matchresult

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidMatchResultPositionPaddleNumericVerifierTest {
    @Test
    fun focusedKillParserAcceptsDigitsAndConservativeZeroAliases() {
        assertEquals(3, parseFocusedKillToken("3"))
        assertEquals(14, parseFocusedKillToken("14"))
        assertEquals(0, parseFocusedKillToken("O"))
        assertEquals(0, parseFocusedKillToken("o"))
        assertEquals(0, parseFocusedKillToken("D"))
    }

    @Test
    fun focusedKillParserRejectsNonExactOrAmbiguousTokens() {
        listOf("I", "l", "S", "B", "G", "D3", "3D", "O3", "3O", "Eliminations", "").forEach {
            assertNull("Unexpected focused kill value for '$it'.", parseFocusedKillToken(it))
        }
    }
}
