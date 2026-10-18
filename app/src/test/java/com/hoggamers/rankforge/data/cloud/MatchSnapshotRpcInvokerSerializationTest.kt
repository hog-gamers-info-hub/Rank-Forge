package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchSnapshotRpcInvokerSerializationTest {
    private val providers = mutableListOf<SupabaseClientProvider>()

    @After
    fun closeClients() = runBlocking {
        providers.forEach { it.client.close() }
    }

    @Test
    fun draftAndFinalizedWritesSerializeConcretePayloadsThroughSupabaseRpc() = runTest {
        val requests = mutableListOf<Pair<String, String>>()
        val engine = MockEngine { request ->
            requests += request.url.encodedPath to (request.body as TextContent).text
            respond(
                content = ByteReadChannel("[{\"outcome\":\"success\",\"revision\":2}]"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val provider = testClientProvider(engine)
        val invoker = SupabaseMatchSnapshotRpcInvoker(provider)

        val results = listOf(
            invoker.invokeWrite(draftParameters(groupPairingKey = null)),
            invoker.invokeWriteGroupRotation(draftParameters(groupPairingKey = "A:C")),
            invoker.invokeWriteFinalizedBootstrap(finalizedBootstrapParameters(groupPairingKey = null)),
            invoker.invokeWriteFinalizedBootstrapGroupRotation(
                finalizedBootstrapParameters(groupPairingKey = "A:C"),
            ),
            invoker.invokeFinalize(finalizationParameters(groupPairingKey = null)),
            invoker.invokeFinalizeGroupRotation(finalizationParameters(groupPairingKey = "A:C")),
        )

        assertEquals(
            listOf(
                RevisionWriteResponse("success", 2),
                RevisionWriteResponse("success", 2),
                RevisionWriteResponse("success", 2),
                RevisionWriteResponse("success", 2),
                RevisionWriteResponse("success", 2),
                RevisionWriteResponse("success", 2),
            ),
            results,
        )
        assertEquals(
            listOf(
                "/rest/v1/rpc/write_match_snapshot",
                "/rest/v1/rpc/write_match_snapshot_v2",
                "/rest/v1/rpc/write_match_snapshot",
                "/rest/v1/rpc/write_match_snapshot_v2",
                "/rest/v1/rpc/finalize_match_snapshot",
                "/rest/v1/rpc/finalize_match_snapshot_v2",
            ),
            requests.map { it.first },
        )
        assertEquals(
            listOf(
                draftJson(groupPairingKey = null),
                draftJson(groupPairingKey = "A:C"),
                finalizedBootstrapJson(groupPairingKey = null),
                finalizedBootstrapJson(groupPairingKey = "A:C"),
                finalizationJson(groupPairingKey = null),
                finalizationJson(groupPairingKey = "A:C"),
            ),
            requests.map { it.second },
        )
    }

    private fun draftParameters(groupPairingKey: String?) =
        MatchSnapshotWriteParameters<DraftMatchUploadPayload, DraftMatchResultUploadPayload>(
            tournamentId = TOURNAMENT_ID,
            matches = listOf(
                DraftMatchUploadPayload(
                    id = "match-id",
                    tournamentId = TOURNAMENT_ID,
                    matchNumber = 1,
                    matchDate = "2026-10-18",
                    mapName = "Bermuda",
                    status = "draft",
                    groupPairingKey = groupPairingKey,
                ),
            ),
            matchResults = listOf(
                DraftMatchResultUploadPayload(
                    id = "result-id",
                    matchId = "match-id",
                    teamSlotId = "slot-id",
                    placement = 1,
                    kills = 4,
                    source = "manual",
                    reviewStatus = "draft",
                ),
            ),
            expectedRevision = 1,
        )

    private fun finalizedBootstrapParameters(groupPairingKey: String?) =
        MatchSnapshotWriteParameters<FinalizedMatchUploadPayload, FinalizedMatchResultUploadPayload>(
            tournamentId = TOURNAMENT_ID,
            matches = listOf(
                FinalizedMatchUploadPayload(
                    id = "match-id",
                    tournamentId = TOURNAMENT_ID,
                    matchNumber = 1,
                    matchDate = "2026-10-18",
                    mapName = "Bermuda",
                    status = "draft",
                    groupPairingKey = groupPairingKey,
                ),
            ),
            matchResults = listOf(
                FinalizedMatchResultUploadPayload(
                    id = "result-id",
                    matchId = "match-id",
                    teamSlotId = "slot-id",
                    placement = 1,
                    kills = 4,
                    source = "manual",
                    reviewStatus = "confirmed",
                ),
            ),
            expectedRevision = 1,
        )

    private fun finalizationParameters(groupPairingKey: String?) =
        ProtectedMatchFinalizationParameters<FinalizedMatchUploadPayload, FinalizedMatchResultUploadPayload>(
            tournamentId = TOURNAMENT_ID,
            match = FinalizedMatchUploadPayload(
                id = "match-id",
                tournamentId = TOURNAMENT_ID,
                matchNumber = 1,
                matchDate = "2026-10-18",
                mapName = "Bermuda",
                status = "finalized",
                groupPairingKey = groupPairingKey,
            ),
            matchResults = listOf(
                FinalizedMatchResultUploadPayload(
                    id = "result-id",
                    matchId = "match-id",
                    teamSlotId = "slot-id",
                    placement = 1,
                    kills = 4,
                    source = "manual",
                    reviewStatus = "confirmed",
                ),
            ),
            expectedRevision = 1,
        )

    private fun draftJson(groupPairingKey: String?) =
        "{\"p_tournament_id\":\"$TOURNAMENT_ID\",\"p_matches\":[{\"id\":\"match-id\",\"tournament_id\":\"$TOURNAMENT_ID\",\"match_number\":1,\"match_date\":\"2026-10-18\",\"map_name\":\"Bermuda\",\"status\":\"draft\"${groupPairingJson(groupPairingKey)}}],\"p_match_results\":[{\"id\":\"result-id\",\"match_id\":\"match-id\",\"team_slot_id\":\"slot-id\",\"placement\":1,\"kills\":4,\"source\":\"manual\",\"review_status\":\"draft\"}],\"p_expected_revision\":1}"

    private fun finalizedBootstrapJson(groupPairingKey: String?) =
        "{\"p_tournament_id\":\"$TOURNAMENT_ID\",\"p_matches\":[{\"id\":\"match-id\",\"tournament_id\":\"$TOURNAMENT_ID\",\"match_number\":1,\"match_date\":\"2026-10-18\",\"map_name\":\"Bermuda\",\"status\":\"draft\"${groupPairingJson(groupPairingKey)}}],\"p_match_results\":[{\"id\":\"result-id\",\"match_id\":\"match-id\",\"team_slot_id\":\"slot-id\",\"placement\":1,\"kills\":4,\"source\":\"manual\",\"review_status\":\"confirmed\"}],\"p_expected_revision\":1}"

    private fun finalizationJson(groupPairingKey: String?) =
        "{\"p_tournament_id\":\"$TOURNAMENT_ID\",\"p_match\":{\"id\":\"match-id\",\"tournament_id\":\"$TOURNAMENT_ID\",\"match_number\":1,\"match_date\":\"2026-10-18\",\"map_name\":\"Bermuda\",\"status\":\"finalized\"${groupPairingJson(groupPairingKey)}},\"p_match_results\":[{\"id\":\"result-id\",\"match_id\":\"match-id\",\"team_slot_id\":\"slot-id\",\"placement\":1,\"kills\":4,\"source\":\"manual\",\"review_status\":\"confirmed\"}],\"p_expected_revision\":1}"

    private fun groupPairingJson(value: String?): String = value?.let { ",\"group_pairing_key\":\"$it\"" } ?: ""

    private fun testClientProvider(engine: MockEngine): SupabaseClientProvider =
        SupabaseClientProvider(
            config = SupabaseAuthConfig(SUPABASE_URL, SUPABASE_KEY),
            client = createSupabaseClient(
                supabaseUrl = SUPABASE_URL,
                supabaseKey = SUPABASE_KEY,
            ) {
                httpEngine = engine
                defaultLogLevel = LogLevel.NONE
                install(Postgrest)
            },
        ).also { providers += it }

    private companion object {
        const val SUPABASE_URL = "https://supabase.test"
        const val SUPABASE_KEY = "publishable-key"
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
