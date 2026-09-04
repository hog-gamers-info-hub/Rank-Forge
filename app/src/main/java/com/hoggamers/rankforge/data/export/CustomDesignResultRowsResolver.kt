package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.domain.export.MatchResultExportModelBuildResult
import com.hoggamers.rankforge.domain.export.ResultExportModelBuilder
import com.hoggamers.rankforge.domain.export.ResultExportRow
import com.hoggamers.rankforge.domain.export.TournamentResultExportModelBuildResult

sealed interface CustomDesignResultRowsResult {
    data class Success(
        val rows: List<ResultExportRow>,
    ) : CustomDesignResultRowsResult

    data class MatchFailure(
        val failures: Set<com.hoggamers.rankforge.domain.export.MatchCsvExportFailure>,
    ) : CustomDesignResultRowsResult

    data class TournamentFailure(
        val failures: Set<com.hoggamers.rankforge.domain.export.TournamentCsvExportFailure>,
    ) : CustomDesignResultRowsResult
}

class CustomDesignResultRowsResolver(
    private val modelBuilder: ResultExportModelBuilder = ResultExportModelBuilder(),
) {
    fun resolve(request: ResultDownloadRequest): CustomDesignResultRowsResult = when (request) {
        is ResultDownloadRequest.CurrentMatch -> when (val result = modelBuilder.buildMatch(request.input)) {
            is MatchResultExportModelBuildResult.Success ->
                CustomDesignResultRowsResult.Success(result.model.rows)
            is MatchResultExportModelBuildResult.Failure ->
                CustomDesignResultRowsResult.MatchFailure(result.failures)
        }
        is ResultDownloadRequest.WholeTournament -> when (val result = modelBuilder.buildTournament(request.input)) {
            is TournamentResultExportModelBuildResult.Success ->
                CustomDesignResultRowsResult.Success(result.model.rows)
            is TournamentResultExportModelBuildResult.Failure ->
                CustomDesignResultRowsResult.TournamentFailure(result.failures)
        }
    }
}
