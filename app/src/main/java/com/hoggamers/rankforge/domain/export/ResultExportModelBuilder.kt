package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus

class ResultExportModelBuilder(
    private val matchCsvExporter: MatchCsvExporter = MatchCsvExporter(),
    private val tournamentCsvExporter: TournamentCsvExporter = TournamentCsvExporter(),
) {
    fun buildMatch(
        input: MatchCsvExportInput,
    ): MatchResultExportModelBuildResult =
        when (val result = matchCsvExporter.buildMatchRows(input)) {
            is MatchExportRowsResult.Success -> MatchResultExportModelBuildResult.Success(
                model = MatchResultExportModel(
                    tournamentName = input.tournament.name,
                    matchNumber = input.match.matchNumber,
                    matchDate = input.match.date,
                    mapName = input.match.mapName,
                    rows = result.rows.map { row -> row.toResultExportRow() },
                ),
            )
            is MatchExportRowsResult.Failure -> MatchResultExportModelBuildResult.Failure(
                failures = result.failures,
            )
        }

    fun buildTournament(
        input: TournamentCsvExportInput,
    ): TournamentResultExportModelBuildResult =
        when (val result = tournamentCsvExporter.buildStandingsRows(input)) {
            is TournamentStandingsExportRowsResult.Success ->
                TournamentResultExportModelBuildResult.Success(
                    model = TournamentResultExportModel(
                        tournamentName = input.tournament.name,
                        finalizedMatchCount = result.rows.first().exportedMatchCount,
                        rows = result.rows.map { row -> row.toResultExportRow() },
                    ),
                )
            is TournamentStandingsExportRowsResult.Failure ->
                TournamentResultExportModelBuildResult.Failure(
                    failures = result.failures,
                )
        }

    private fun MatchExportRow.toResultExportRow(): ResultExportRow =
        ResultExportRow(
            rank = if (participationStatus == MatchParticipationStatus.NO_SHOW.name) null else rowNumber,
            teamName = teamName,
            win = if (placement == 1) 1 else 0,
            totalKills = kills,
            positionPoints = placementPoints,
            totalPoints = totalPoints,
        )

    private fun TournamentStandingsExportRow.toResultExportRow(): ResultExportRow =
        ResultExportRow(
            rank = standingsRank,
            teamName = teamName,
            win = firstPlaceCount,
            totalKills = totalKills,
            positionPoints = totalPositionPoints,
            totalPoints = totalPoints,
        )
}
