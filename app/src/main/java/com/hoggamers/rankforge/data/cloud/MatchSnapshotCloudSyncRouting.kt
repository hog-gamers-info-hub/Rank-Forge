package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.TournamentFormat

internal class DraftMatchCloudSyncRpcRouter(
    private val rpcInvoker: MatchSnapshotRpcInvoker,
) {
    suspend fun invoke(
        payloads: DraftMatchCloudSyncPayloads,
        expectedRevision: Int,
    ): DraftMatchCloudSyncExecutionResult {
        if (!isValidMatchSnapshotRouting(
                tournamentFormat = payloads.tournamentFormat,
                tournamentIds = payloads.matches.map { it.tournamentId },
                groupPairingKeys = payloads.matches.map { it.groupPairingKey },
            )
        ) {
            return DraftMatchCloudSyncExecutionResult.Failure(
                completedStage = null,
                category = DraftMatchCloudSyncFailureCategory.VALIDATION,
            )
        }
        val tournamentId = payloads.matches.first().tournamentId
        val parameters = MatchSnapshotWriteParameters(
            tournamentId = tournamentId,
            matches = payloads.matches,
            matchResults = payloads.matchResults,
            expectedRevision = expectedRevision,
        )
        val response = when (payloads.tournamentFormat) {
            TournamentFormat.STANDARD -> rpcInvoker.invokeWrite(parameters)
            TournamentFormat.GROUP_ROTATION -> rpcInvoker.invokeWriteGroupRotation(parameters)
        }
        return response.toDraftExecutionResult(
            format = payloads.tournamentFormat,
            expectedRevision = expectedRevision,
        )
    }
}

internal fun finalizedMatchCloudSyncExecutor(
    payloads: FinalizedMatchCloudSyncPayloads,
    rpcInvoker: MatchSnapshotRpcInvoker,
): FinalizedMatchCloudSyncExecutor {
    val tournamentId = payloads.matches.first().tournamentId
    return when (payloads.tournamentFormat) {
        TournamentFormat.STANDARD -> FinalizedMatchCloudSyncExecutor(
            finalizeMatch = { match, matchResults, expectedRevision ->
                rpcInvoker.invokeFinalize(
                    ProtectedMatchFinalizationParameters(
                        tournamentId = tournamentId,
                        match = match,
                        matchResults = matchResults,
                        expectedRevision = expectedRevision,
                    ),
                )
            },
            writeDraftMatch = { match, matchResults, expectedRevision ->
                rpcInvoker.invokeWrite(
                    MatchSnapshotWriteParameters(
                        tournamentId = tournamentId,
                        matches = listOf(match),
                        matchResults = matchResults,
                        expectedRevision = expectedRevision,
                    ),
                )
            },
        )

        TournamentFormat.GROUP_ROTATION -> FinalizedMatchCloudSyncExecutor(
            finalizeMatch = { match, matchResults, expectedRevision ->
                rpcInvoker.invokeFinalizeGroupRotation(
                    ProtectedMatchFinalizationParameters(
                        tournamentId = tournamentId,
                        match = match,
                        matchResults = matchResults,
                        expectedRevision = expectedRevision,
                    ),
                )
            },
            writeDraftMatch = { match, matchResults, expectedRevision ->
                rpcInvoker.invokeWriteGroupRotation(
                    MatchSnapshotWriteParameters(
                        tournamentId = tournamentId,
                        matches = listOf(match),
                        matchResults = matchResults,
                        expectedRevision = expectedRevision,
                    ),
                )
            },
        )
    }
}

internal class FinalizedMatchCloudSyncRpcRouter(
    private val rpcInvoker: MatchSnapshotRpcInvoker,
) {
    suspend fun invoke(
        payloads: FinalizedMatchCloudSyncPayloads,
        expectedRevision: Int,
    ): FinalizedMatchCloudSyncExecutionResult {
        if (!isValidMatchSnapshotRouting(
                tournamentFormat = payloads.tournamentFormat,
                tournamentIds = payloads.matches.map { it.tournamentId },
                groupPairingKeys = payloads.matches.map { it.groupPairingKey },
            )
        ) {
            return FinalizedMatchCloudSyncExecutionResult.Failure(
                completedStage = null,
                category = FinalizedMatchCloudSyncFailureCategory.VALIDATION,
            )
        }
        return finalizedMatchCloudSyncExecutor(payloads, rpcInvoker)
            .execute(payloads, expectedRevision)
    }
}

private fun RevisionWriteResponse.toDraftExecutionResult(
    format: TournamentFormat,
    expectedRevision: Int,
): DraftMatchCloudSyncExecutionResult = when {
    outcome == "success" -> DraftMatchCloudSyncExecutionResult.Success
    format == TournamentFormat.STANDARD -> DraftMatchCloudSyncExecutionResult.Failure(
        completedStage = null,
        category = DraftMatchCloudSyncFailureCategory.CONFLICT,
        conflict = toRevisionConflict(expectedRevision),
    )

    else -> when (outcome) {
        "stale_write" -> DraftMatchCloudSyncExecutionResult.Failure(
            completedStage = null,
            category = DraftMatchCloudSyncFailureCategory.CONFLICT,
            conflict = toRevisionConflict(expectedRevision),
        )

        "missing_revision" -> DraftMatchCloudSyncExecutionResult.Failure(
            completedStage = null,
            category = DraftMatchCloudSyncFailureCategory.CONFLICT,
            conflict = RevisionConflict.MissingRevision,
        )

        "authentication_required" -> DraftMatchCloudSyncExecutionResult.Failure(
            completedStage = null,
            category = DraftMatchCloudSyncFailureCategory.AUTHENTICATION,
        )

        "unauthorized" -> DraftMatchCloudSyncExecutionResult.Failure(
            completedStage = null,
            category = DraftMatchCloudSyncFailureCategory.AUTHORIZATION,
        )

        "validation_failure" -> DraftMatchCloudSyncExecutionResult.Failure(
            completedStage = null,
            category = DraftMatchCloudSyncFailureCategory.VALIDATION,
        )

        else -> DraftMatchCloudSyncExecutionResult.Failure(
            completedStage = null,
            category = DraftMatchCloudSyncFailureCategory.UNKNOWN,
        )
    }
}
