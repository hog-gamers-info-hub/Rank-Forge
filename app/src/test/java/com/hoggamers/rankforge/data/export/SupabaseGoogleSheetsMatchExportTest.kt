package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.domain.export.MatchExportRow
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseGoogleSheetsMatchExportTest {
    @Test
    fun requestUsesExactFunctionContractAndAuthenticatedHeaders() = runTest {
        val transport = RecordingTransport(successResponse())
        val result = dataSource(transport).export(TOURNAMENT_ID, MATCH_ID, rows())

        assertTrue(result is GoogleSheetsMatchExportExecutionResult.Success)
        val request = transport.requests.single()
        assertEquals(
            "https://project.supabase.co/functions/v1/google-sheets-export",
            request.url,
        )
        assertEquals("Bearer access-token", request.headers["Authorization"])
        assertEquals("publishable-key", request.headers["apikey"])
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals("application/json", request.headers["Accept"])

        val body = Json.parseToJsonElement(request.body).jsonObject
        assertEquals(setOf("operation", "tournament_id", "match_id", "rows"), body.keys)
        assertEquals("export_match", body.getValue("operation").jsonPrimitive.content)
        assertEquals(TOURNAMENT_ID, body.getValue("tournament_id").jsonPrimitive.content)
        assertEquals(MATCH_ID, body.getValue("match_id").jsonPrimitive.content)
        assertEquals(12, body.getValue("rows").jsonArray.size)
        body.getValue("rows").jsonArray.forEach { rowElement ->
            val row = rowElement.jsonObject
            assertEquals(EXACT_ROW_KEYS, row.keys)
            assertFalse(row.getValue("row_number").jsonPrimitive.isString)
            assertFalse(row.getValue("placement_points").jsonPrimitive.isString)
            assertTrue(row.getValue("team_name").jsonPrimitive.isString)
        }
    }

    @Test
    fun successRequiresAllAuthoritativeResponseFields() = runTest {
        assertEquals(
            GoogleSheetsMatchExportExecutionResult.Success(12),
            dataSource(RecordingTransport(successResponse()))
                .export(TOURNAMENT_ID, MATCH_ID, rows()),
        )
        listOf(
            "{}",
            "not-json",
            """{"ok":true,"operation":"export_standings","tournament_id":"$TOURNAMENT_ID","match_id":"$MATCH_ID","rows_written":12}""",
            """{"ok":true,"operation":"export_match","tournament_id":"wrong","match_id":"$MATCH_ID","rows_written":12}""",
            """{"ok":true,"operation":"export_match","tournament_id":"$TOURNAMENT_ID","match_id":"wrong","rows_written":12}""",
            """{"ok":true,"operation":"export_match","tournament_id":"$TOURNAMENT_ID","match_id":"$MATCH_ID","rows_written":11}""",
        ).forEach { body ->
            val result = dataSource(RecordingTransport(GoogleSheetsHttpResponse(200, body)))
                .export(TOURNAMENT_ID, MATCH_ID, rows())
            assertEquals(
                GoogleSheetsMatchExportExecutionResult.Failure(
                    AndroidGoogleSheetsExportFailureReason.INVALID_RESPONSE,
                ),
                result,
            )
        }
    }

    @Test
    fun missingSessionAndConfigurationDoNotContactFunction() = runTest {
        val missingSessionTransport = RecordingTransport(successResponse())
        assertEquals(
            GoogleSheetsMatchExportExecutionResult.Failure(
                AndroidGoogleSheetsExportFailureReason.AUTHENTICATION_REQUIRED,
            ),
            dataSource(missingSessionTransport, accessToken = null)
                .export(TOURNAMENT_ID, MATCH_ID, rows()),
        )
        assertTrue(missingSessionTransport.requests.isEmpty())

        val unconfiguredTransport = RecordingTransport(successResponse())
        assertEquals(
            GoogleSheetsMatchExportExecutionResult.Failure(
                AndroidGoogleSheetsExportFailureReason.SERVER_CONFIGURATION,
            ),
            dataSource(unconfiguredTransport, configured = false)
                .export(TOURNAMENT_ID, MATCH_ID, rows()),
        )
        assertTrue(unconfiguredTransport.requests.isEmpty())
    }

    @Test
    fun transportFailuresMapToNetworkOrTimeout() = runTest {
        val network = dataSource(ThrowingTransport(IOException()))
            .export(TOURNAMENT_ID, MATCH_ID, rows())
        val timeout = dataSource(ThrowingTransport(SocketTimeoutException()))
            .export(TOURNAMENT_ID, MATCH_ID, rows())

        assertEquals(
            AndroidGoogleSheetsExportFailureReason.NETWORK_FAILURE,
            (network as GoogleSheetsMatchExportExecutionResult.Failure).reason,
        )
        assertEquals(
            AndroidGoogleSheetsExportFailureReason.TIMEOUT,
            (timeout as GoogleSheetsMatchExportExecutionResult.Failure).reason,
        )
    }

    @Test
    fun knownBackendFailuresMapWithoutClientRetry() = runTest {
        assertEquals(
            AndroidGoogleSheetsExportFailureReason.IN_PROGRESS,
            failureReason("EXPORT_IN_PROGRESS"),
        )
        assertEquals(
            AndroidGoogleSheetsExportFailureReason.OUTCOME_UNCERTAIN,
            failureReason("EXPORT_OUTCOME_UNCERTAIN"),
        )
        assertEquals(
            AndroidGoogleSheetsExportFailureReason.VERIFICATION_FAILURE,
            failureReason("EXPORT_VERIFICATION_CONFLICT"),
        )
        assertEquals(
            AndroidGoogleSheetsExportFailureReason.AUTHENTICATION_REQUIRED,
            failureReason("UNAUTHORIZED"),
        )
        assertEquals(
            AndroidGoogleSheetsExportFailureReason.SERVER_CONFIGURATION,
            failureReason("GOOGLE_TOKEN_FAILURE"),
        )
        assertEquals(
            AndroidGoogleSheetsExportFailureReason.SERVER_FAILURE,
            failureReason("MATCH_EXPORT_DATA_MISMATCH"),
        )
    }

    private suspend fun failureReason(code: String): AndroidGoogleSheetsExportFailureReason {
        val result = dataSource(
            RecordingTransport(
                GoogleSheetsHttpResponse(
                    statusCode = 409,
                    body = """{"ok":false,"error":{"code":"$code","message":"safe"}}""",
                ),
            ),
        ).export(TOURNAMENT_ID, MATCH_ID, rows())
        return (result as GoogleSheetsMatchExportExecutionResult.Failure).reason
    }

    private fun dataSource(
        transport: GoogleSheetsExportHttpTransport,
        accessToken: String? = "access-token",
        configured: Boolean = true,
    ) = SupabaseGoogleSheetsMatchExportRemoteDataSource(
        config = if (configured) {
            SupabaseAuthConfig(
                supabaseUrl = "https://project.supabase.co",
                publishableKey = "publishable-key",
            )
        } else {
            SupabaseAuthConfig(supabaseUrl = "", publishableKey = "")
        },
        accessTokenProvider = object : SupabaseAccessTokenProvider {
            override fun currentAccessToken(): String? = accessToken
        },
        transport = transport,
    )

    private fun rows(): List<MatchExportRow> = (1..12).map { position ->
        MatchExportRow(
            exportSchemaVersion = "phase_10_v1",
            exportType = "match_result",
            tournamentId = TOURNAMENT_ID,
            tournamentName = "Synthetic Cup",
            matchId = MATCH_ID,
            matchLabel = "Match 3",
            matchFinalizedAt = "",
            rowNumber = position,
            placement = position,
            teamSlot = position,
            teamName = "Team $position",
            player1Name = "Player $position.1",
            player2Name = "Player $position.2",
            player3Name = "Player $position.3",
            player4Name = "Player $position.4",
            placementPoints = if (position == 1) 12 else 0,
            kills = position - 1,
            killPoints = position - 1,
            totalPoints = (if (position == 1) 12 else 0) + position - 1,
            correctionStatus = "original_finalized",
        )
    }

    private fun successResponse() = GoogleSheetsHttpResponse(
        statusCode = 200,
        body = """{"ok":true,"operation":"export_match","tournament_id":"$TOURNAMENT_ID","match_id":"$MATCH_ID","rows_written":12}""",
    )

    private class RecordingTransport(
        private val response: GoogleSheetsHttpResponse,
    ) : GoogleSheetsExportHttpTransport {
        val requests = mutableListOf<GoogleSheetsHttpRequest>()

        override suspend fun post(request: GoogleSheetsHttpRequest): GoogleSheetsHttpResponse {
            requests += request
            return response
        }
    }

    private class ThrowingTransport(
        private val failure: IOException,
    ) : GoogleSheetsExportHttpTransport {
        override suspend fun post(request: GoogleSheetsHttpRequest): GoogleSheetsHttpResponse =
            throw failure
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-4111-8111-111111111111"
        const val MATCH_ID = "22222222-2222-4222-8222-222222222222"
        val EXACT_ROW_KEYS = setOf(
            "export_schema_version",
            "export_type",
            "tournament_id",
            "tournament_name",
            "match_id",
            "match_label",
            "match_finalized_at",
            "row_number",
            "placement",
            "team_slot",
            "team_name",
            "player_1_name",
            "player_2_name",
            "player_3_name",
            "player_4_name",
            "placement_points",
            "kills",
            "kill_points",
            "total_points",
            "correction_status",
        )
    }
}
