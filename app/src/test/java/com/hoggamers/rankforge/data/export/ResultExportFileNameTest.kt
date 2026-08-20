package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.domain.export.MatchResultExportModel
import com.hoggamers.rankforge.domain.export.ResultExportRow
import com.hoggamers.rankforge.domain.export.TournamentResultExportModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultExportFileNameTest {
    @Test
    fun currentMatchPdfUsesApprovedFilenameContract() {
        assertEquals(
            "RankForge_HOG_Championship_Match_1_Result.pdf",
            ResultExportFileName.forMatch(matchModel("HOG Championship", 1), ResultExportFileFormat.PDF),
        )
    }

    @Test
    fun currentMatchPngUsesApprovedFilenameContract() {
        assertEquals(
            "RankForge_HOG_Championship_Match_1_Result.png",
            ResultExportFileName.forMatch(matchModel("HOG Championship", 1), ResultExportFileFormat.PNG),
        )
    }

    @Test
    fun tournamentPdfUsesApprovedFilenameContract() {
        assertEquals(
            "RankForge_HOG_Championship_Tournament_Result.pdf",
            ResultExportFileName.forTournament(tournamentModel("HOG Championship"), ResultExportFileFormat.PDF),
        )
    }

    @Test
    fun tournamentPngUsesApprovedFilenameContract() {
        assertEquals(
            "RankForge_HOG_Championship_Tournament_Result.png",
            ResultExportFileName.forTournament(tournamentModel("HOG Championship"), ResultExportFileFormat.PNG),
        )
    }

    @Test
    fun repeatedSpacesBecomeOneSafeSeparator() {
        assertEquals(
            "HOG_Championship",
            ResultExportFileName.sanitizeTournamentComponent("  HOG   Championship  "),
        )
    }

    @Test
    fun forbiddenCharactersBecomeSafeSeparators() {
        assertEquals(
            "HOG_Championship",
            ResultExportFileName.sanitizeTournamentComponent("HOG<>:\"/\\|?*Championship"),
        )
    }

    @Test
    fun slashAndBackslashAreSanitized() {
        assertEquals(
            "Summer_Cup",
            ResultExportFileName.sanitizeTournamentComponent("Summer/Cup\\"),
        )
    }

    @Test
    fun trailingDotsAndSpacesAreRemoved() {
        assertEquals(
            "Summer_Cup",
            ResultExportFileName.sanitizeTournamentComponent("Summer Cup...   "),
        )
    }

    @Test
    fun controlCharactersBecomeSafeSeparators() {
        assertEquals(
            "HOG_Cup",
            ResultExportFileName.sanitizeTournamentComponent("HOG\u0000\u0001Cup"),
        )
    }

    @Test
    fun blankOrInvalidNameUsesStableFallback() {
        assertEquals(
            ResultExportFileName.FALLBACK_TOURNAMENT_COMPONENT,
            ResultExportFileName.sanitizeTournamentComponent("<>:/\\|?*..."),
        )
    }

    @Test
    fun veryLongTournamentNameIsBounded() {
        val sanitized = ResultExportFileName.sanitizeTournamentComponent("A".repeat(500))

        assertEquals(ResultExportFileName.MAX_TOURNAMENT_COMPONENT_LENGTH, sanitized.length)
    }

    @Test
    fun extensionRemainsCorrectAfterSanitization() {
        val model = matchModel("Tournament.pdf")

        assertTrue(ResultExportFileName.forMatch(model, ResultExportFileFormat.PDF).endsWith(".pdf"))
        assertTrue(ResultExportFileName.forMatch(model, ResultExportFileFormat.PNG).endsWith(".png"))
    }

    @Test
    fun unicodeTournamentTextIsPreservedDeterministically() {
        assertEquals(
            "Copa_São_Paulo_🔥",
            ResultExportFileName.sanitizeTournamentComponent("Copa São Paulo 🔥"),
        )
    }

    @Test
    fun sameInputProducesSameFilename() {
        val model = matchModel("HOG Championship", 7)

        assertEquals(
            ResultExportFileName.forMatch(model, ResultExportFileFormat.PDF),
            ResultExportFileName.forMatch(model, ResultExportFileFormat.PDF),
        )
    }

    @Test
    fun api26To28RequiresUserSelectedDestination() {
        assertEquals(
            ResultFileSaveRoute.USER_SELECTED_DESTINATION_REQUIRED,
            ResultFileSavePolicy.routeForSdk(26),
        )
        assertEquals(
            ResultFileSaveRoute.USER_SELECTED_DESTINATION_REQUIRED,
            ResultFileSavePolicy.routeForSdk(28),
        )
        assertEquals(
            ResultFileSaveRoute.MEDIA_STORE_DOWNLOADS,
            ResultFileSavePolicy.routeForSdk(29),
        )
    }

    private fun matchModel(
        tournamentName: String,
        matchNumber: Int = 1,
    ): MatchResultExportModel =
        MatchResultExportModel(
            tournamentName = tournamentName,
            matchNumber = matchNumber,
            matchDate = LocalDate.of(2026, 8, 20),
            mapName = "Bermuda",
            rows = rows(),
        )

    private fun tournamentModel(
        tournamentName: String,
    ): TournamentResultExportModel =
        TournamentResultExportModel(
            tournamentName = tournamentName,
            finalizedMatchCount = 2,
            rows = rows(),
        )

    private fun rows(): List<ResultExportRow> =
        (1..12).map { rank ->
            ResultExportRow(
                rank = rank,
                teamName = "Team $rank",
                win = if (rank == 1) 1 else 0,
                totalKills = rank,
                positionPoints = 13 - rank,
                totalPoints = rank * 2,
            )
        }
}
