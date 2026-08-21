package com.hoggamers.rankforge.domain.export

data class MatchExportRow(
    val exportSchemaVersion: String,
    val exportType: String,
    val tournamentId: String,
    val tournamentName: String,
    val matchId: String,
    val matchLabel: String,
    val matchFinalizedAt: String,
    val rowNumber: Int,
    val placement: Int?,
    val participationStatus: String = "PARTICIPATED",
    val teamSlot: Int,
    val teamName: String,
    val player1Name: String,
    val player2Name: String,
    val player3Name: String,
    val player4Name: String,
    val placementPoints: Int,
    val kills: Int,
    val killPoints: Int,
    val totalPoints: Int,
    val correctionStatus: String,
) {
    fun orderedFields(): List<String> = listOf(
        exportSchemaVersion,
        exportType,
        tournamentId,
        tournamentName,
        matchId,
        matchLabel,
        matchFinalizedAt,
        rowNumber.toString(),
        placement?.toString().orEmpty(),
        participationStatus,
        teamSlot.toString(),
        teamName,
        player1Name,
        player2Name,
        player3Name,
        player4Name,
        placementPoints.toString(),
        kills.toString(),
        killPoints.toString(),
        totalPoints.toString(),
        correctionStatus,
    )
}

sealed interface MatchExportRowsResult {
    data class Success(
        val rows: List<MatchExportRow>,
    ) : MatchExportRowsResult

    data class Failure(
        val failures: Set<MatchCsvExportFailure>,
    ) : MatchExportRowsResult
}
