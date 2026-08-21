package com.hoggamers.rankforge.domain.export

data class TournamentStandingsExportRow(
    val exportSchemaVersion: String,
    val exportType: String,
    val tournamentId: String,
    val tournamentName: String,
    val exportedMatchCount: Int,
    val standingsRank: Int,
    val teamSlot: Int,
    val teamName: String,
    val player1Name: String,
    val player2Name: String,
    val player3Name: String,
    val player4Name: String,
    val matchesPlayed: Int,
    val totalPositionPoints: Int,
    val totalKills: Int,
    val totalKillPoints: Int,
    val totalPoints: Int,
    val bestPlacement: Int?,
    val firstPlaceCount: Int,
    val tieBreakStatus: String,
) {
    fun orderedFields(): List<String> = listOf(
        exportSchemaVersion,
        exportType,
        tournamentId,
        tournamentName,
        exportedMatchCount.toString(),
        standingsRank.toString(),
        teamSlot.toString(),
        teamName,
        player1Name,
        player2Name,
        player3Name,
        player4Name,
        matchesPlayed.toString(),
        totalPositionPoints.toString(),
        totalKills.toString(),
        totalKillPoints.toString(),
        totalPoints.toString(),
        bestPlacement?.toString().orEmpty(),
        firstPlaceCount.toString(),
        tieBreakStatus,
    )
}

sealed interface TournamentStandingsExportRowsResult {
    data class Success(
        val rows: List<TournamentStandingsExportRow>,
    ) : TournamentStandingsExportRowsResult

    data class Failure(
        val failures: Set<TournamentCsvExportFailure>,
    ) : TournamentStandingsExportRowsResult
}
