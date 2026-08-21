package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.export.TournamentStandingsExportRow
import io.github.jan.supabase.auth.auth
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class AndroidGoogleSheetsExportFailureReason {
    AUTHENTICATION_REQUIRED,
    NETWORK_FAILURE,
    TIMEOUT,
    SERVER_CONFIGURATION,
    IN_PROGRESS,
    OUTCOME_UNCERTAIN,
    VERIFICATION_FAILURE,
    INVALID_RESPONSE,
    SERVER_FAILURE,
}

sealed interface GoogleSheetsStandingsExportExecutionResult {
    data class Success(
        val exportedMatchCount: Int,
        val rowsWritten: Int,
    ) : GoogleSheetsStandingsExportExecutionResult

    data class Failure(
        val reason: AndroidGoogleSheetsExportFailureReason,
    ) : GoogleSheetsStandingsExportExecutionResult
}

interface SupabaseAccessTokenProvider {
    fun currentAccessToken(): String?
}

@Singleton
class SupabaseSessionAccessTokenProvider @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
) : SupabaseAccessTokenProvider {
    override fun currentAccessToken(): String? =
        clientProvider.client.auth.currentSessionOrNull()?.accessToken
}

data class GoogleSheetsHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

data class GoogleSheetsHttpResponse(
    val statusCode: Int,
    val body: String,
)

fun interface GoogleSheetsExportHttpTransport {
    suspend fun post(request: GoogleSheetsHttpRequest): GoogleSheetsHttpResponse
}

@Singleton
class UrlConnectionGoogleSheetsExportHttpTransport @Inject constructor() :
    GoogleSheetsExportHttpTransport {
    override suspend fun post(request: GoogleSheetsHttpRequest): GoogleSheetsHttpResponse =
        withContext(Dispatchers.IO) {
            val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = REQUEST_TIMEOUT_MS
                readTimeout = REQUEST_TIMEOUT_MS
                doOutput = true
                request.headers.forEach { (name, value) ->
                    setRequestProperty(name, value)
                }
            }

            try {
                connection.outputStream.use { output ->
                    output.write(request.body.toByteArray(StandardCharsets.UTF_8))
                }
                val responseStream = if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                GoogleSheetsHttpResponse(
                    statusCode = connection.responseCode,
                    body = responseStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
                        .orEmpty(),
                )
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 10_000
    }
}

interface GoogleSheetsStandingsExportRemoteDataSource {
    suspend fun export(
        tournamentId: String,
        rows: List<TournamentStandingsExportRow>,
    ): GoogleSheetsStandingsExportExecutionResult
}

@Singleton
class SupabaseGoogleSheetsStandingsExportRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val accessTokenProvider: SupabaseAccessTokenProvider,
    private val transport: GoogleSheetsExportHttpTransport,
) : GoogleSheetsStandingsExportRemoteDataSource {
    override suspend fun export(
        tournamentId: String,
        rows: List<TournamentStandingsExportRow>,
    ): GoogleSheetsStandingsExportExecutionResult {
        val exportedMatchCount = rows.firstOrNull()?.exportedMatchCount
        if (
            rows.size !in 1..MAX_ROW_COUNT ||
            exportedMatchCount == null ||
            exportedMatchCount !in 1..MAX_MATCH_COUNT ||
            rows.any {
                it.exportedMatchCount != exportedMatchCount ||
                    it.matchesPlayed !in 0..exportedMatchCount
            }
        ) {
            return GoogleSheetsStandingsExportExecutionResult.Failure(
                AndroidGoogleSheetsExportFailureReason.INVALID_RESPONSE,
            )
        }

        if (!config.isConfigured) {
            return GoogleSheetsStandingsExportExecutionResult.Failure(
                AndroidGoogleSheetsExportFailureReason.SERVER_CONFIGURATION,
            )
        }

        val accessToken = accessTokenProvider.currentAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?: return GoogleSheetsStandingsExportExecutionResult.Failure(
                AndroidGoogleSheetsExportFailureReason.AUTHENTICATION_REQUIRED,
            )

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
                    body = buildStandingsRequestBody(tournamentId, rows),
                ),
            )
            response.toExecutionResult(
                expectedRows = rows.size,
                expectedExportedMatchCount = exportedMatchCount,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SocketTimeoutException) {
            GoogleSheetsStandingsExportExecutionResult.Failure(
                AndroidGoogleSheetsExportFailureReason.TIMEOUT,
            )
        } catch (_: IOException) {
            GoogleSheetsStandingsExportExecutionResult.Failure(
                AndroidGoogleSheetsExportFailureReason.NETWORK_FAILURE,
            )
        } catch (_: Throwable) {
            GoogleSheetsStandingsExportExecutionResult.Failure(
                AndroidGoogleSheetsExportFailureReason.SERVER_FAILURE,
            )
        }
    }

    private fun buildStandingsRequestBody(
        tournamentId: String,
        rows: List<TournamentStandingsExportRow>,
    ): String = buildJsonObject {
        put("operation", "export_standings")
        put("tournament_id", tournamentId)
        put(
            "rows",
            buildJsonArray {
                rows.forEach { row ->
                    add(row.toJsonObject())
                }
            },
        )
    }.toString()

    private fun TournamentStandingsExportRow.toJsonObject(): JsonObject = buildJsonObject {
        put("export_schema_version", exportSchemaVersion)
        put("export_type", exportType)
        put("tournament_id", tournamentId)
        put("tournament_name", tournamentName)
        put("exported_match_count", exportedMatchCount)
        put("standings_rank", standingsRank)
        put("team_slot", teamSlot)
        put("team_name", teamName)
        put("player_1_name", player1Name)
        put("player_2_name", player2Name)
        put("player_3_name", player3Name)
        put("player_4_name", player4Name)
        put("matches_played", matchesPlayed)
        put("total_position_points", totalPositionPoints)
        put("total_kills", totalKills)
        put("total_kill_points", totalKillPoints)
        put("total_points", totalPoints)
        if (bestPlacement == null) {
            put("best_placement", JsonNull)
        } else {
            put("best_placement", bestPlacement)
        }
        put("first_place_count", firstPlaceCount)
        put("tie_break_status", tieBreakStatus)
    }

    private fun GoogleSheetsHttpResponse.toExecutionResult(
        expectedRows: Int,
        expectedExportedMatchCount: Int,
    ): GoogleSheetsStandingsExportExecutionResult {
        val response = runCatching {
            Json.parseToJsonElement(body).jsonObject
        }.getOrNull()

        if (statusCode in 200..299) {
            val rowsWritten = response?.get("rows_written")?.jsonPrimitive?.intOrNull
            val exportedMatchCount = response?.get("exported_match_count")?.jsonPrimitive?.intOrNull
            return if (
                response?.get("ok")?.jsonPrimitive?.contentOrNull == "true" &&
                response["operation"]?.jsonPrimitive?.contentOrNull == "export_standings" &&
                rowsWritten == expectedRows &&
                exportedMatchCount == expectedExportedMatchCount
            ) {
                GoogleSheetsStandingsExportExecutionResult.Success(
                    exportedMatchCount = exportedMatchCount,
                    rowsWritten = rowsWritten,
                )
            } else {
                failure(AndroidGoogleSheetsExportFailureReason.INVALID_RESPONSE)
            }
        }

        return when (response?.get("error")?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull) {
            "UNAUTHORIZED", "SUPABASE_AUTH_FAILURE" -> failure(
                AndroidGoogleSheetsExportFailureReason.AUTHENTICATION_REQUIRED,
            )
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
    ): GoogleSheetsStandingsExportExecutionResult.Failure =
        GoogleSheetsStandingsExportExecutionResult.Failure(reason)

    private companion object {
        const val MAX_ROW_COUNT = 12
        const val MAX_MATCH_COUNT = 10
    }
}
