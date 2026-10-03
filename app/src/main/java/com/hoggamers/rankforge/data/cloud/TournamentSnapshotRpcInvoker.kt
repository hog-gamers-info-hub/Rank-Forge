package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

interface TournamentSnapshotRpcInvoker {
    suspend fun invoke(parameters: TournamentSnapshotWriteParameters): RevisionWriteResponse
}

@Singleton
class SupabaseTournamentSnapshotRpcInvoker @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
) : TournamentSnapshotRpcInvoker {
    override suspend fun invoke(parameters: TournamentSnapshotWriteParameters): RevisionWriteResponse =
        clientProvider.client.postgrest.rpc(
            "write_tournament_snapshot",
            parameters,
        ).decodeSingle()
}
