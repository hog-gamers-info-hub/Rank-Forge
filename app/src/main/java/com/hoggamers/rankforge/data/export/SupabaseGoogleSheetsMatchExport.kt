package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.domain.export.MatchExportRow
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

sealed interface GoogleSheetsMatchExportExecutionResult {
    data class Success(
        val rowsWritten: Int,
    ) : GoogleSheetsMatchExportExecutionResult

    data class Failure(
        val reason: AndroidGoogleSheetsExportFailureReason,
    ) : GoogleSheetsMatchExportExecutionResult
}

interface GoogleSheetsMatchExportRemoteDataSource {
    suspend fun export(
        tournamentId: String,
        matchId: String,
        rows: List<MatchExportRow>,
    ): GoogleSheetsMatchExportExecutionResult
}

class NoOpGoogleSheetsMatchExportRemoteDataSource : GoogleSheetsMatchExportRemoteDataSource {
    override suspend fun export(
        tournamentId: String,
        matchId: String,
        rows: List<MatchExportRow>,
    ): GoogleSheetsMatchExportExecutionResult =
        GoogleSheetsMatchExportExecutionResult.Failure(
            AndroidGoogleSheetsExportFailureReason.SERVER_CONFIGURATION,
        )
}

@Singleton
class SupabaseGoogleSheetsMatchExportRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val accessTokenProvider: SupabaseAccessTokenProvider,
    private val transport: GoogleSheetsExportHttpTransport,
) : GoogleSheetsMatchExportRemoteDataSource {
    override suspend fun export(
        tournamentId: String,
        matchId: String,
        rows: List<MatchExportRow>,
    ): GoogleSheetsMatchExportExecutionResult {
        if (!config.isConfigured) {
            return failure(AndroidGoogleSheetsExportFailureReason.SERVER_CONFIGURATION)
        }

        val accessToken = accessTokenProvider.currentAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?: return failure(AndroidGoogleSheetsExportFailureReason.AUTHENTICATION_REQUIRED)

        return try {
            val response = transport.post(
                GoogleSheetsHttpRequest(
                    url = "${config.supabaseUrl.trimEnd('/')}/functions/v1/google-sheets-export",
                    headers = mapOf(
                        "Authorization" to "Bearer $accessToken",
                        "apikey" to config.publishableKey,
                        "Content-Type" to "application/json",
                        "Accept" to "application/json",
                    ),
                    body = buildMatchRequestBody(tournamentId, matchId, rows),
                ),
            )
            response.toExecutionResult(tournamentId, matchId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SocketTimeoutException) {
            failure(AndroidGoogleSheetsExportFailureReason.TIMEOUT)
        } catch (_: IOException) {
            failure(AndroidGoogleSheetsExportFailureReason.NETWORK_FAILURE)
        } catch (_: Throwable) {
            failure(AndroidGoogleSheetsExportFailureReason.SERVER_FAILURE)
        }
    }

    private fun buildMatchRequestBody(
        tournamentId: String,
        matchId: String,
        rows: List<MatchExportRow>,
    ): String = buildJsonObject {
        put("operation", "export_match")
        put("tournament_id", tournamentId)
        put("match_id", matchId)
        put(
            "rows",
            buildJsonArray {
                rows.forEach { row -> add(row.toJsonObject()) }
            },
        )
    }.toString()

    private fun MatchExportRow.toJsonObject(): JsonObject = buildJsonObject {
        put("export_schema_version", exportSchemaVersion)
        put("export_type", exportType)
        put("tournament_id", tournamentId)
        put("tournament_name", tournamentName)
        put("match_id", matchId)
        put("match_label", matchLabel)
        put("match_finalized_at", matchFinalizedAt)
        put("row_number", rowNumber)
        put("placement", placement)
        put("team_slot", teamSlot)
        put("team_name", teamName)
        put("player_1_name", player1Name)
        put("player_2_name", player2Name)
        put("player_3_name", player3Name)
        put("player_4_name", player4Name)
        put("placement_points", placementPoints)
        put("kills", kills)
        put("kill_points", killPoints)
        put("total_points", totalPoints)
        put("correction_status", correctionStatus)
    }

    private fun GoogleSheetsHttpResponse.toExecutionResult(
        tournamentId: String,
        matchId: String,
    ): GoogleSheetsMatchExportExecutionResult {
        val response = runCatching {
            Json.parseToJsonElement(body).jsonObject
        }.getOrNull()

        if (statusCode in 200..299) {
            val rowsWritten = response?.get("rows_written")?.jsonPrimitive?.intOrNull
            return if (
                response?.get("ok")?.jsonPrimitive?.contentOrNull == "true" &&
                    response["operation"]?.jsonPrimitive?.contentOrNull == "export_match" &&
                    response["tournament_id"]?.jsonPrimitive?.contentOrNull == tournamentId &&
                    response["match_id"]?.jsonPrimitive?.contentOrNull == matchId &&
                    rowsWritten == REQUIRED_ROW_COUNT
            ) {
                GoogleSheetsMatchExportExecutionResult.Success(rowsWritten)
            } else {
                failure(AndroidGoogleSheetsExportFailureReason.INVALID_RESPONSE)
            }
        }

        return when (response?.get("error")?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull) {
            "UNAUTHORIZED", "SUPABASE_AUTH_FAILURE" ->
                failure(AndroidGoogleSheetsExportFailureReason.AUTHENTICATION_REQUIRED)
            "EXPORT_IN_PROGRESS" -> failure(AndroidGoogleSheetsExportFailureReason.IN_PROGRESS)
            "EXPORT_OUTCOME_UNCERTAIN" -> failure(AndroidGoogleSheetsExportFailureReason.OUTCOME_UNCERTAIN)
            "EXPORT_VERIFICATION_NOT_FOUND",
            "EXPORT_VERIFICATION_CONFLICT",
            "EXPORT_VERIFICATION_FAILURE",
            "EXPORT_VERIFICATION_RANGE_EXCEEDED",
            -> failure(AndroidGoogleSheetsExportFailureReason.VERIFICATION_FAILURE)
            "GOOGLE_CONFIG_MISSING",
            "GOOGLE_CREDENTIAL_INVALID",
            "GOOGLE_JWT_SIGNING_FAILURE",
            "GOOGLE_TOKEN_FAILURE",
            "GOOGLE_TOKEN_RESPONSE_INVALID",
            -> failure(AndroidGoogleSheetsExportFailureReason.SERVER_CONFIGURATION)
            else -> failure(AndroidGoogleSheetsExportFailureReason.SERVER_FAILURE)
        }
    }

    private fun failure(
        reason: AndroidGoogleSheetsExportFailureReason,
    ): GoogleSheetsMatchExportExecutionResult.Failure =
        GoogleSheetsMatchExportExecutionResult.Failure(reason)

    private companion object {
        const val REQUIRED_ROW_COUNT = 12
    }
}
