package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionProbe
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionReadiness
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseTournamentCloudUploadRemoteDataSourceTest {
    @Test
    fun unconfiguredUploadStopsBeforeSessionReadinessAndRpc() = runTest {
        val probe = RecordingSessionProbe()
        val rpc = RecordingRpcInvoker()

        val result = remote(
            config = SupabaseAuthConfig("", ""),
            probe = probe,
            rpc = rpc,
        ).upload(payloads(), expectedRevision = 0)

        assertEquals(
            CloudUploadExecutionResult.Failure(null, CloudUploadFailureCategory.VALIDATION),
            result,
        )
        assertEquals(0, probe.awaitInitializationCalls)
        assertEquals(0, rpc.parameters.size)
    }

    @Test
    fun everyNonUsableSessionStatusStopsBeforeRpc() = runTest {
        listOf(
            SupabaseAuthSessionStatus.INITIALIZING,
            SupabaseAuthSessionStatus.REFRESH_FAILURE,
            SupabaseAuthSessionStatus.NOT_AUTHENTICATED,
        ).forEach { status ->
            val probe = RecordingSessionProbe(
                SupabaseAuthSessionReadiness(status = status, hasUsableSession = false),
            )
            val rpc = RecordingRpcInvoker()

            val result = remote(probe = probe, rpc = rpc).upload(payloads(), expectedRevision = 0)

            assertEquals(
                "status=$status",
                CloudUploadExecutionResult.Failure(null, CloudUploadFailureCategory.AUTHENTICATION),
                result,
            )
            assertEquals("status=$status", 1, probe.awaitInitializationCalls)
            assertEquals("status=$status", 0, rpc.parameters.size)
        }
    }

    @Test
    fun authenticatedUsableSessionAwaitsInitializationAndInvokesSnapshotRpc() = runTest {
        val probe = RecordingSessionProbe(
            SupabaseAuthSessionReadiness(
                status = SupabaseAuthSessionStatus.AUTHENTICATED,
                hasUsableSession = true,
            ),
        )
        val rpc = RecordingRpcInvoker(RevisionWriteResponse("success", revision = 7))

        val result = remote(probe = probe, rpc = rpc).upload(payloads(), expectedRevision = 0)

        assertEquals(CloudUploadExecutionResult.Success(7), result)
        assertEquals(1, probe.awaitInitializationCalls)
        assertEquals(1, rpc.parameters.size)
        assertEquals(0, rpc.parameters.single().expectedRevision)
        assertEquals("group_rotation", rpc.parameters.single().tournament.format)
    }

    @Test
    fun standardPayloadUsesTheSameUsableSessionGate() = runTest {
        val rpc = RecordingRpcInvoker(RevisionWriteResponse("success", revision = 3))

        val result = remote(rpc = rpc).upload(
            payloads().copy(
                tournament = payloads().tournament.copy(
                    format = "standard",
                    groupCount = null,
                    selectedGroupPairings = emptyList(),
                ),
            ),
            expectedRevision = 0,
        )

        assertEquals(CloudUploadExecutionResult.Success(3), result)
        assertEquals("standard", rpc.parameters.single().tournament.format)
    }

    @Test
    fun snapshotWriteParametersSerializeBeforeRpcDispatch() {
        val parameters = TournamentSnapshotWriteParameters(
            tournament = payloads().tournament,
            teamSlots = payloads().teamSlots,
            players = payloads().players,
            expectedRevision = 0,
        )

        val json = Json.encodeToString(parameters)

        assertTrue(json.contains("\"p_tournament\""))
        assertTrue(json.contains("\"p_team_slots\""))
        assertTrue(json.contains("\"p_players\""))
        assertTrue(json.contains("\"p_expected_revision\":0"))
    }

    @Test
    fun rpcRequestFailureIsReportedAfterPreRpcChecks() = runTest {
        val rpc = RecordingRpcInvoker(failure = IllegalArgumentException("invalid request"))

        val result = remote(rpc = rpc).upload(payloads(), expectedRevision = 0)

        assertEquals(
            CloudUploadExecutionResult.Failure(null, CloudUploadFailureCategory.VALIDATION),
            result,
        )
        assertEquals(1, rpc.parameters.size)
    }

    private fun remote(
        config: SupabaseAuthConfig = SupabaseAuthConfig("https://supabase.test", "publishable-key"),
        probe: RecordingSessionProbe = RecordingSessionProbe(),
        rpc: RecordingRpcInvoker = RecordingRpcInvoker(),
    ) = SupabaseTournamentCloudUploadRemoteDataSource(config, probe, rpc)

    private fun payloads() = TournamentCloudUploadPayloads(
        tournament = TournamentUploadPayload(
            id = "11111111-1111-1111-1111-111111111111",
            ownerId = "22222222-2222-2222-2222-222222222222",
            name = "Rotation",
            stageName = "Organizer",
            organizerContact = "",
            status = "draft",
            format = "group_rotation",
            groupCount = 3,
            selectedGroupPairings = listOf(
                GroupPairingUploadPayload("A", "B", "A×B"),
                GroupPairingUploadPayload("B", "C", "B×C"),
                GroupPairingUploadPayload("C", "A", "A×C"),
            ),
        ),
        teamSlots = listOf(
            TeamSlotUploadPayload(
                id = "slot-1",
                tournamentId = "11111111-1111-1111-1111-111111111111",
                slotNumber = 1,
                teamName = "Team 1",
                status = "draft",
                group = "A",
            ),
        ),
        players = listOf(
            PlayerUploadPayload(
                id = "player-1",
                teamSlotId = "slot-1",
                displayName = "Player 1",
                normalizedName = "Player 1",
            ),
        ),
    )

    private class RecordingSessionProbe(
        private val readiness: SupabaseAuthSessionReadiness =
            SupabaseAuthSessionReadiness(
                status = SupabaseAuthSessionStatus.AUTHENTICATED,
                hasUsableSession = true,
            ),
    ) : SupabaseAuthSessionProbe {
        var awaitInitializationCalls: Int = 0

        override suspend fun awaitInitialization() {
            awaitInitializationCalls += 1
        }

        override fun currentReadiness(): SupabaseAuthSessionReadiness = readiness
    }

    private class RecordingRpcInvoker(
        private val response: RevisionWriteResponse = RevisionWriteResponse("success", 1),
        private val failure: Throwable? = null,
    ) : TournamentSnapshotRpcInvoker {
        val parameters = mutableListOf<TournamentSnapshotWriteParameters>()

        override suspend fun invoke(parameters: TournamentSnapshotWriteParameters): RevisionWriteResponse {
            this.parameters += parameters
            failure?.let { throw it }
            return response
        }
    }
}
