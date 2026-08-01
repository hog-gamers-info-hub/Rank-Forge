package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.domain.export.CsvUtf8FileFailure
import com.hoggamers.rankforge.domain.export.CsvUtf8FileValidator
import com.hoggamers.rankforge.domain.export.CsvUtf8PayloadResult

enum class AndroidExportType {
    MATCH_CSV,
    STANDINGS_CSV,
    MATCH_GOOGLE_SHEETS,
    STANDINGS_GOOGLE_SHEETS,
}

enum class AndroidExportBlockedReason {
    MISSING_CONTEXT,
    MATCH_NOT_FINALIZED,
    INVALID_FINALIZED_MATCH,
    NO_FINALIZED_STANDINGS,
    INVALID_FINALIZED_STANDINGS,
    INVALID_CSV_PAYLOAD,
}

enum class AndroidExportUnavailableReason {
    GOOGLE_SHEETS_CLIENT_NOT_CONFIGURED,
}

data class AndroidExportRequest(
    val type: AndroidExportType,
    val tournamentId: String,
    val matchId: String? = null,
)

sealed interface AndroidExportResult {
    val request: AndroidExportRequest

    data class CsvReady(
        override val request: AndroidExportRequest,
        val filename: String,
        val mimeType: String,
        val content: String,
        val byteCount: Int,
        val sha256: String,
    ) : AndroidExportResult

    data class Blocked(
        override val request: AndroidExportRequest,
        val reason: AndroidExportBlockedReason,
    ) : AndroidExportResult

    data class Unavailable(
        override val request: AndroidExportRequest,
        val reason: AndroidExportUnavailableReason,
    ) : AndroidExportResult
}

class AndroidExportCoordinator(
    private val csvUtf8FileValidator: CsvUtf8FileValidator = CsvUtf8FileValidator(),
) {
    fun prepareMatchCsv(
        tournamentId: String,
        matchId: String,
        csv: String,
    ): AndroidExportResult = prepareCsv(
        request = AndroidExportRequest(
            type = AndroidExportType.MATCH_CSV,
            tournamentId = tournamentId,
            matchId = matchId,
        ),
        filename = "rank-forge-match-$matchId.csv",
        csv = csv,
    )

    fun prepareStandingsCsv(
        tournamentId: String,
        csv: String,
    ): AndroidExportResult = prepareCsv(
        request = AndroidExportRequest(
            type = AndroidExportType.STANDINGS_CSV,
            tournamentId = tournamentId,
        ),
        filename = "rank-forge-standings-$tournamentId.csv",
        csv = csv,
    )

    fun blockMatchCsv(
        tournamentId: String,
        matchId: String,
        reason: AndroidExportBlockedReason,
    ): AndroidExportResult.Blocked = AndroidExportResult.Blocked(
        request = AndroidExportRequest(
            type = AndroidExportType.MATCH_CSV,
            tournamentId = tournamentId,
            matchId = matchId,
        ),
        reason = reason,
    )

    fun blockStandingsCsv(
        tournamentId: String,
        reason: AndroidExportBlockedReason,
    ): AndroidExportResult.Blocked = AndroidExportResult.Blocked(
        request = AndroidExportRequest(
            type = AndroidExportType.STANDINGS_CSV,
            tournamentId = tournamentId,
        ),
        reason = reason,
    )

    fun googleSheetsMatchUnavailable(
        tournamentId: String,
        matchId: String,
    ): AndroidExportResult.Unavailable = AndroidExportResult.Unavailable(
        request = AndroidExportRequest(
            type = AndroidExportType.MATCH_GOOGLE_SHEETS,
            tournamentId = tournamentId,
            matchId = matchId,
        ),
        reason = AndroidExportUnavailableReason.GOOGLE_SHEETS_CLIENT_NOT_CONFIGURED,
    )

    fun googleSheetsStandingsUnavailable(
        tournamentId: String,
    ): AndroidExportResult.Unavailable = AndroidExportResult.Unavailable(
        request = AndroidExportRequest(
            type = AndroidExportType.STANDINGS_GOOGLE_SHEETS,
            tournamentId = tournamentId,
        ),
        reason = AndroidExportUnavailableReason.GOOGLE_SHEETS_CLIENT_NOT_CONFIGURED,
    )

    private fun prepareCsv(
        request: AndroidExportRequest,
        filename: String,
        csv: String,
    ): AndroidExportResult {
        if (request.tournamentId.isBlank() ||
            (request.type == AndroidExportType.MATCH_CSV && request.matchId.isNullOrBlank())
        ) {
            return AndroidExportResult.Blocked(
                request = request,
                reason = AndroidExportBlockedReason.MISSING_CONTEXT,
            )
        }

        return when (val payloadResult = csvUtf8FileValidator.createPayload(csv)) {
            is CsvUtf8PayloadResult.Success -> AndroidExportResult.CsvReady(
                request = request,
                filename = filename,
                mimeType = CSV_MIME_TYPE,
                content = csv,
                byteCount = payloadResult.payload.byteCount,
                sha256 = payloadResult.payload.sha256,
            )
            is CsvUtf8PayloadResult.Failure -> AndroidExportResult.Blocked(
                request = request,
                reason = payloadResult.failures.toBlockedReason(),
            )
        }
    }

    private fun Set<CsvUtf8FileFailure>.toBlockedReason(): AndroidExportBlockedReason =
        AndroidExportBlockedReason.INVALID_CSV_PAYLOAD

    private companion object {
        const val CSV_MIME_TYPE = "text/csv"
    }
}
