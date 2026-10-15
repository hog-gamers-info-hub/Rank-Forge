package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.ProtectedMatchCorrectionRequest
import com.hoggamers.rankforge.domain.tournament.ProtectedMatchCorrectionResult
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

interface ProtectedMatchCorrectionRpcInvoker {
    suspend fun invokeStandard(parameters: ProtectedMatchCorrectionParameters): RevisionWriteResponse

    suspend fun invokeGroupRotation(parameters: ProtectedMatchCorrectionParameters): RevisionWriteResponse
}

@Singleton
class SupabaseProtectedMatchCorrectionRpcInvoker @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
) : ProtectedMatchCorrectionRpcInvoker {
    override suspend fun invokeStandard(
        parameters: ProtectedMatchCorrectionParameters,
    ): RevisionWriteResponse = clientProvider.client.postgrest.rpc(
        "correct_finalized_match_snapshot",
        parameters,
    ).decodeSingle()

    override suspend fun invokeGroupRotation(
        parameters: ProtectedMatchCorrectionParameters,
    ): RevisionWriteResponse = clientProvider.client.postgrest.rpc(
        "correct_finalized_match_snapshot_v2",
        parameters,
    ).decodeSingle()
}

internal suspend fun invokeProtectedMatchCorrectionRpc(
    request: ProtectedMatchCorrectionRequest,
    parameters: ProtectedMatchCorrectionParameters,
    rpcInvoker: ProtectedMatchCorrectionRpcInvoker,
): RevisionWriteResponse = when (request.tournament.format) {
    TournamentFormat.STANDARD -> rpcInvoker.invokeStandard(parameters)
    TournamentFormat.GROUP_ROTATION -> rpcInvoker.invokeGroupRotation(parameters)
}

internal fun RevisionWriteResponse.toProtectedMatchCorrectionResult(
    expectedRevision: Int,
): ProtectedMatchCorrectionResult = when (outcome) {
    "success" -> revision?.let(ProtectedMatchCorrectionResult::Success)
        ?: ProtectedMatchCorrectionResult.ValidationFailure
    "already_corrected" -> revision?.let(ProtectedMatchCorrectionResult::AlreadyCorrected)
        ?: ProtectedMatchCorrectionResult.ValidationFailure
    "stale_write" -> ProtectedMatchCorrectionResult.Conflict(
        toRevisionConflict(expectedRevision),
    )
    "missing_revision" -> ProtectedMatchCorrectionResult.Conflict(RevisionConflict.MissingRevision)
    "authentication_required" -> ProtectedMatchCorrectionResult.AuthenticationRequired
    "unauthorized" -> ProtectedMatchCorrectionResult.AuthorizationFailure
    "match_not_finalized" -> ProtectedMatchCorrectionResult.MatchNotFinalized
    else -> ProtectedMatchCorrectionResult.ValidationFailure
}
