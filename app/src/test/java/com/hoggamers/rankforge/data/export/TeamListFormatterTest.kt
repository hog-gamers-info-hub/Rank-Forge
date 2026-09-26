package com.hoggamers.rankforge.data.export

import org.junit.Assert.assertEquals
import org.junit.Test

class TeamListFormatterTest {
    @Test
    fun formatUsesStageHeaderAndPreservesActualSlotNumbers() {
        val formatted = TeamListFormatter.format(
            tournamentName = "EC TOURNAMENT S9",
            stageName = "Quarterfinals Group 1",
            entries = listOf(
                TeamListEntry(1, " TEAM ELITE "),
                TeamListEntry(3, "GODLIKE"),
                TeamListEntry(12, "RUTHLESS ESPORTS"),
            ),
        )

        assertEquals(
            "EC TOURNAMENT S9\n" +
                "Stage: Quarterfinals Group 1\n\n" +
                "Team-List:\n" +
                "01. TEAM ELITE\n" +
                "03. GODLIKE\n" +
                "12. RUTHLESS ESPORTS",
            formatted,
        )
    }

    @Test
    fun formatSkipsBlankSlotsAndKeepsBlankStageLine() {
        val formatted = TeamListFormatter.format(
            tournamentName = "Cup",
            stageName = "   ",
            entries = listOf(
                TeamListEntry(1, "Alpha"),
                TeamListEntry(2, "   "),
                TeamListEntry(4, "Bravo"),
            ),
        )

        assertEquals(
            "Cup\nStage: \n\nTeam-List:\n01. Alpha\n04. Bravo",
            formatted,
        )
    }

    @Test
    fun fileNameRemovesOnlyUnsafeCharactersAndUsesFallbackWhenEmpty() {
        assertEquals(
            "EC TOURNAMENT S9.txt",
            TeamListFormatter.fileName("EC TOURNAMENT S9"),
        )
        assertEquals(
            "EC TOURNAMENTS9.txt",
            TeamListFormatter.fileName("EC: TOURNAMENT/S9"),
        )
        assertEquals(
            "Team_List.txt",
            TeamListFormatter.fileName(" :/* "),
        )
    }
}
