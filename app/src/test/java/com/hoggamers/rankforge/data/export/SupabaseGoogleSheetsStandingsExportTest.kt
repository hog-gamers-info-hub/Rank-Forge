package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.domain.export.TournamentStandingsExportRow
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

class SupabaseGoogleSheetsStandingsExportTest {
    @Test
    fun requestUsesExactFunctionContractAndAuthenticatedHeaders() = runTest {
        val transport = RecordingTransport(
            GoogleSheetsHttpResponse(
                statusCode = 200,
                body = successBody(),
            ),
        )
        val result = dataSource(transport).export(TOURNAMENT_ID, rows())

        assertTrue(result is GoogleSheetsStandingsExportExecutionResult.Success)
        val request = transport.requests.single()
        assertEquals(
            "https://project.supabase.co/functions/v1/google-sheets-export",
            request.url,
        )
        assertEquals("Bearer access-token", request.headers["Authorization"])
        assertEquals("publishable-key", request.headers["apikey"])

        val body = Json.parseToJsonElement(request.body).jsonObject
        assertEquals(setOf("operation", "tournament_id", "rows"), body.keys)
        assertEquals("export_standings", body.getValue("operation").jsonPrimitive.content)
        assertEquals(TOURNAMENT_ID, body.getValue("tournament_id").jsonPrimitive.content)
        assertEquals(12, body.getValue("rows").jsonArray.size)
        body.getValue("rows").jsonArray.forEach { rowElement ->
            val row = rowElement.jsonObject
            assertEquals(EXACT_ROW_KEYS, row.keys)
            assertFalse(row.getValue("exported_match_count").jsonPrimitive.isString)
            assertFalse(row.getValue("standings_rank").jsonPrimitive.isString)
            assertFalse(row.getValue("total_points").jsonPrimitive.isString)
        }
    }

    @Test
    fun missingSessionDoesNotContactFunction() = runTest {
        val transport = RecordingTransport(successResponse())
        val result = dataSource(
            transport = transport,
            accessToken = null,
        ).export(TOURNAMENT_ID, rows())

        assertEquals(
            GoogleSheetsStandingsExportExecutionResult.Failure(
                AndroidGoogleSheetsExportFailureReason.AUTHENTICATION_REQUIRED,
            ),
            result,
        )
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun successResponseIsAcceptedOnlyForTwelveWrittenRows() = runTest {
        val success = dataSource(
            RecordingTransport(successResponse()),
        ).export(TOURNAMENT_ID, rows())

        assertEquals(
            GoogleSheetsStandingsExportExecutionResult.Success(
                exportedMatchCount = 3,
                rowsWritten = 12,
            ),
            success,
        )
    }

    @Test
    fun outcomeUncertainIsSurfacedWithoutClientRetry() = runTest {
        val transport = RecordingTransport(errorResponse("EXPORT_OUTCOME_UNCERTAIN"))
        val result = dataSource(transport).export(TOURNAMENT_ID, rows())

        assertEquals(
            GoogleSheetsStandingsExportExecutionResult.Failure(
                AndroidGoogleSheetsExportFailureReason.OUTCOME_UNCERTAIN,
            ),
            result,
        )
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun inProgressAndVerificationErrorsMapSafely() = runTest {
        assertEquals(
            AndroidGoogleSheetsExportFailureReason.IN_PROGRESS,
            failureReason("EXPORT_IN_PROGRESS"),
        )
        assertEquals(
            AndroidGoogleSheetsExportFailureReason.VERIFICATION_FAILURE,
            failureReason("EXPORT_VERIFICATION_CONFLICT"),
        )
        assertEquals(
            AndroidGoogleSheetsExportFailureReason.AUTHENTICATION_REQUIRED,
            failureReason("UNAUTHORIZED"),
        )
    }

    @Test
    fun transportFailuresMapToNetworkOrTimeout() = runTest {
        val network = dataSource(ThrowingTransport(IOException()))
            .export(TOURNAMENT_ID, rows())
        val timeout = dataSource(ThrowingTransport(SocketTimeoutException()))
            .export(TOURNAMENT_ID, rows())

        assertEquals(
            AndroidGoogleSheetsExportFailureReason.NETWORK_FAILURE,
            (network as GoogleSheetsStandingsExportExecutionResult.Failure).reason,
        )
        assertEquals(
            AndroidGoogleSheetsExportFailureReason.TIMEOUT,
            (timeout as GoogleSheetsStandingsExportExecutionResult.Failure).reason,
        )
    }

    private suspend fun failureReason(code: String): AndroidGoogleSheetsExportFailureReason {
        val result = dataSource(RecordingTransport(errorResponse(code)))
            .export(TOURNAMENT_ID, rows())
        return (result as GoogleSheetsStandingsExportExecutionResult.Failure).reason
    }

    private fun dataSource(
        transport: GoogleSheetsExportHttpTransport,
        accessToken: String? = "access-token",
    ) = SupabaseGoogleSheetsStandingsExportRemoteDataSource(
        config = com.hoggamers.rankforge.data.auth.SupabaseAuthConfig(
            supabaseUrl = "https://project.supabase.co",
            publishableKey = "publishable-key",
        ),
        accessTokenProvider = object : SupabaseAccessTokenProvider {
            override fun currentAccessToken(): String? = accessToken
        },
        transport = transport,
    )

    private fun rows(): List<TournamentStandingsExportRow> = (1..12).map { rank ->
        TournamentStandingsExportRow(
            exportSchemaVersion = "phase_10_v1",
            exportType = "tournament_standings",
            tournamentId = TOURNAMENT_ID,
            tournamentName = "Synthetic Cup",
            exportedMatchCount = 3,
            standingsRank = rank,
            teamSlot = rank,
            teamName = "Team $rank",
            player1Name = "Player $rank.1",
            player2Name = "Player $rank.2",
            player3Name = "Player $rank.3",
            player4Name = "Player $rank.4",
            matchesPlayed = 3,
            totalPositionPoints = 10,
            totalKills = 2,
            totalKillPoints = 2,
            totalPoints = 12,
            bestPlacement = rank,
            firstPlaceCount = if (rank == 1) 1 else 0,
            tieBreakStatus = "unique_order",
        )
    }

    private fun successResponse() = GoogleSheetsHttpResponse(200, successBody())

    private fun successBody() =
        """{"ok":true,"operation":"export_standings","tournament_id":"$TOURNAMENT_ID","exported_match_count":3,"rows_written":12}"""

    private fun errorResponse(code: String) = GoogleSheetsHttpResponse(
        statusCode = 409,
        body = """{"ok":false,"error":{"code":"$code","message":"safe"}}""",
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
        val EXACT_ROW_KEYS = setOf(
            "export_schema_version",
            "export_type",
            "tournament_id",
            "tournament_name",
            "exported_match_count",
            "standings_rank",
            "team_slot",
            "team_name",
            "player_1_name",
            "player_2_name",
            "player_3_name",
            "player_4_name",
            "matches_played",
            "total_position_points",
            "total_kills",
            "total_kill_points",
            "total_points",
            "best_placement",
            "first_place_count",
            "tie_break_status",
        )
    }
}
