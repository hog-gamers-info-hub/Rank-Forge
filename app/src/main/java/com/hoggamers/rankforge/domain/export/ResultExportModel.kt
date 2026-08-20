package com.hoggamers.rankforge.domain.export

import java.time.LocalDate

data class ResultExportRow(
    val rank: Int,
    val teamName: String,
    val win: Int,
    val totalKills: Int,
    val positionPoints: Int,
    val totalPoints: Int,
)

data class MatchResultExportModel(
    val tournamentName: String,
    val matchNumber: Int,
    val matchDate: LocalDate,
    val mapName: String,
    val rows: List<ResultExportRow>,
)

data class TournamentResultExportModel(
    val tournamentName: String,
    val finalizedMatchCount: Int,
    val rows: List<ResultExportRow>,
)

sealed interface MatchResultExportModelBuildResult {
    data class Success(
        val model: MatchResultExportModel,
    ) : MatchResultExportModelBuildResult

    data class Failure(
        val failures: Set<MatchCsvExportFailure>,
    ) : MatchResultExportModelBuildResult
}

sealed interface TournamentResultExportModelBuildResult {
    data class Success(
        val model: TournamentResultExportModel,
    ) : TournamentResultExportModelBuildResult

    data class Failure(
        val failures: Set<TournamentCsvExportFailure>,
    ) : TournamentResultExportModelBuildResult
}
