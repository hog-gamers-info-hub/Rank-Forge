package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

interface MatchSnapshotRpcInvoker {
    suspend fun <M, R> invokeWrite(
        parameters: MatchSnapshotWriteParameters<M, R>,
    ): RevisionWriteResponse

    suspend fun <M, R> invokeWriteGroupRotation(
        parameters: MatchSnapshotWriteParameters<M, R>,
    ): RevisionWriteResponse

    suspend fun invokeFinalize(
        parameters: ProtectedMatchFinalizationParameters<
            FinalizedMatchUploadPayload,
            FinalizedMatchResultUploadPayload,
            >,
    ): RevisionWriteResponse

    suspend fun invokeFinalizeGroupRotation(
        parameters: ProtectedMatchFinalizationParameters<
            FinalizedMatchUploadPayload,
            FinalizedMatchResultUploadPayload,
            >,
    ): RevisionWriteResponse
}

@Singleton
class SupabaseMatchSnapshotRpcInvoker @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
) : MatchSnapshotRpcInvoker {
    override suspend fun <M, R> invokeWrite(
        parameters: MatchSnapshotWriteParameters<M, R>,
    ): RevisionWriteResponse = clientProvider.client.postgrest.rpc(
        "write_match_snapshot",
        parameters,
    ).decodeSingle()

    override suspend fun <M, R> invokeWriteGroupRotation(
        parameters: MatchSnapshotWriteParameters<M, R>,
    ): RevisionWriteResponse = clientProvider.client.postgrest.rpc(
        "write_match_snapshot_v2",
        parameters,
    ).decodeSingle()

    override suspend fun invokeFinalize(
        parameters: ProtectedMatchFinalizationParameters<
            FinalizedMatchUploadPayload,
            FinalizedMatchResultUploadPayload,
            >,
    ): RevisionWriteResponse = clientProvider.client.postgrest.rpc(
        "finalize_match_snapshot",
        parameters,
    ).decodeSingle()

    override suspend fun invokeFinalizeGroupRotation(
        parameters: ProtectedMatchFinalizationParameters<
            FinalizedMatchUploadPayload,
            FinalizedMatchResultUploadPayload,
            >,
    ): RevisionWriteResponse = clientProvider.client.postgrest.rpc(
        "finalize_match_snapshot_v2",
        parameters,
    ).decodeSingle()
}

internal fun isValidMatchSnapshotRouting(
    tournamentFormat: TournamentFormat,
    tournamentIds: List<String>,
    groupPairingKeys: List<String?>,
): Boolean {
    val tournamentId = tournamentIds.firstOrNull()?.takeIf { it.isNotBlank() } ?: return false
    if (tournamentIds.any { it != tournamentId }) return false
    return when (tournamentFormat) {
        TournamentFormat.STANDARD -> groupPairingKeys.all { it == null }
        TournamentFormat.GROUP_ROTATION -> groupPairingKeys.all { !it.isNullOrBlank() }
    }
}
