package com.hoggamers.rankforge.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class TeamListParserTest {
    @Test
    fun parsesPlainNewlineSeparatedNames() {
        assertNames(
            input = "Alpha\nBravo\nCharlie",
            expected = listOf("Alpha", "Bravo", "Charlie"),
        )
    }

    @Test
    fun ignoresBlankLines() {
        assertNames(
            input = "Alpha\n\n  \nBravo\n\nCharlie",
            expected = listOf("Alpha", "Bravo", "Charlie"),
        )
    }

    @Test
    fun removesNumberedPrefixesFromOneThroughTwelve() {
        assertNames(
            input = (1..12).joinToString("\n") { number -> "$number Team $number" },
            expected = (1..12).map { number -> "Team $number" },
        )
    }

    @Test
    fun removesZeroPaddedNumberedPrefixesFromOneThroughTwelve() {
        assertNames(
            input = (1..12).joinToString("\n") { number -> "%02d. Team $number".format(number) },
            expected = (1..12).map { number -> "Team $number" },
        )
    }

    @Test
    fun removesSupportedSeparatorsAndWhitespace() {
        assertNames(
            input = "01. Alpha\n02 - Bravo\n3) Charlie\n04: Delta\n5 Echo\n06    Foxtrot\n7.   Golf",
            expected = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf"),
        )
    }

    @Test
    fun removesRecognizedHeadersAtTheTop() {
        val headers = listOf("Team-list", "TeamList", "Teams", "Team Names", "Slot-list", "SlotList")

        headers.forEach { header ->
            assertNames(
                input = "$header\nAlpha\nBravo",
                expected = listOf("Alpha", "Bravo"),
            )
        }
    }

    @Test
    fun recognizesHeadersCaseInsensitivelyAndIgnoresBlankLinesBeforeThem() {
        assertNames(
            input = "\n  TEAM_NAMES  \nAlpha",
            expected = listOf("Alpha"),
        )
    }

    @Test
    fun removesHeaderOnlyFromFirstMeaningfulLine() {
        assertNames(
            input = "Alpha\nTeam Names\nBravo",
            expected = listOf("Alpha", "Team Names", "Bravo"),
        )
    }

    @Test
    fun preservesLegitimateNumericTeamNames() {
        assertNames(
            input = "7Sea Esports\n4AM\n12K Gaming\n1Tap Esports",
            expected = listOf("7Sea Esports", "4AM", "12K Gaming", "1Tap Esports"),
        )
    }

    @Test
    fun doesNotStripNumbersGreaterThanTwelve() {
        assertNames(
            input = "13. Alpha",
            expected = listOf("13. Alpha"),
        )
    }

    @Test
    fun trimsOuterWhitespaceAndPreservesInternalContent() {
        assertNames(
            input = "   OG x ELITE   \n  T2K-ESPORTS  \n RB.Speed ",
            expected = listOf("OG x ELITE", "T2K-ESPORTS", "RB.Speed"),
        )
    }

    @Test
    fun exactlyTwelveNamesDoNotOverflow() {
        val result = TeamListParser.parse((1..12).joinToString("\n") { "Team $it" })

        assertEquals((1..12).map { "Team $it" }, result.teamNames)
        assertEquals(false, result.hasOverflow)
    }

    @Test
    fun moreThanTwelveNamesReturnsFirstTwelveAndOverflow() {
        val result = TeamListParser.parse((1..13).joinToString("\n") { "Team $it" })

        assertEquals((1..12).map { "Team $it" }, result.teamNames)
        assertEquals(true, result.hasOverflow)
    }

    @Test
    fun emptyInputReturnsNoNamesWithoutOverflow() {
        val result = TeamListParser.parse("\n  \n")

        assertEquals(emptyList<String>(), result.teamNames)
        assertEquals(false, result.hasOverflow)
    }

    private fun assertNames(input: String, expected: List<String>) {
        val result = TeamListParser.parse(input)

        assertEquals(expected, result.teamNames)
        assertEquals(false, result.hasOverflow)
    }
}
