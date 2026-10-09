package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseTournamentRosterSnapshotRpcInvoker @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
) : TournamentRosterSnapshotRpcInvoker {
    override suspend fun invoke(
        parameters: TournamentRosterCloudReplacementParameters,
    ): RevisionWriteResponse = clientProvider.client.postgrest.rpc(
        "replace_tournament_roster_snapshot",
        parameters,
    ).decodeSingle()

    override suspend fun invokeGroupRotation(
        parameters: TournamentRosterCloudReplacementV2Parameters,
    ): RevisionWriteResponse = clientProvider.client.postgrest.rpc(
        "replace_tournament_roster_snapshot_v2",
        parameters,
    ).decodeSingle()
}
