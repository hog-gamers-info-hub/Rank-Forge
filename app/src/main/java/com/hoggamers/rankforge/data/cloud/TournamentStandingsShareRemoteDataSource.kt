package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.presentation.screen.TournamentStandingRowUiState
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class TournamentStandingsShareFailureReason {
    AUTHENTICATION_REQUIRED,
    SERVER_CONFIGURATION,
    INVALID_INPUT,
    NETWORK_FAILURE,
    SERVER_FAILURE,
    INVALID_RESPONSE,
}

sealed interface TournamentStandingsSharePublicationResult {
    data class Success(val publicUrl: String) : TournamentStandingsSharePublicationResult

    data class Failure(
        val reason: TournamentStandingsShareFailureReason,
    ) : TournamentStandingsSharePublicationResult
}

interface TournamentStandingsShareRemoteDataSource {
    suspend fun publish(
        tournamentId: String,
        rows: List<TournamentStandingRowUiState>,
    ): TournamentStandingsSharePublicationResult
}

data class TournamentStandingsShareUpdateRequest(
    val tournamentId: String,
    val standings: JsonArray,
    val updatedAt: String,
)

sealed interface TournamentStandingsShareInsertResult {
    data class Created(val shareToken: String?) : TournamentStandingsShareInsertResult

    data object Conflict : TournamentStandingsShareInsertResult
}

interface TournamentStandingsShareGateway {
    fun hasAuthenticatedSession(): Boolean

    suspend fun selectShareTokens(tournamentId: String): List<String?>

    suspend fun updateShare(request: TournamentStandingsShareUpdateRequest)

    suspend fun insertShare(
        tournamentId: String,
        standings: JsonArray,
    ): TournamentStandingsShareInsertResult
}

@Serializable
private data class TournamentStandingsShareTokenPayload(
    @SerialName("share_token") val shareToken: String? = null,
)

@Singleton
class SupabaseTournamentStandingsShareGateway @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
) : TournamentStandingsShareGateway {
    override fun hasAuthenticatedSession(): Boolean =
        clientProvider.client.auth.currentSessionOrNull() != null

    override suspend fun selectShareTokens(tournamentId: String): List<String?> =
        clientProvider.client
            .from(TABLE_NAME)
            .select(shareTokenColumns()) {
                filter { eq("tournament_id", tournamentId) }
            }
            .decodeList<TournamentStandingsShareTokenPayload>()
            .map { it.shareToken }

    override suspend fun updateShare(request: TournamentStandingsShareUpdateRequest) {
        clientProvider.client
            .from(TABLE_NAME)
            .update(buildUpdatePayload(request)) {
                filter { eq("tournament_id", request.tournamentId) }
            }
    }

    override suspend fun insertShare(
        tournamentId: String,
        standings: JsonArray,
    ): TournamentStandingsShareInsertResult = try {
        val payload = clientProvider.client
            .from(TABLE_NAME)
            .insert(buildInsertPayload(tournamentId, standings)) {
                defaultToNull = false
                select(shareTokenColumns())
            }
            .decodeList<TournamentStandingsShareTokenPayload>()
        if (payload.size == 1) {
            TournamentStandingsShareInsertResult.Created(payload.single().shareToken)
        } else {
            TournamentStandingsShareInsertResult.Created(null)
        }
    } catch (exception: RestException) {
        if (exception.statusCode == 409) {
            TournamentStandingsShareInsertResult.Conflict
        } else {
            throw exception
        }
    }

    private companion object {
        const val TABLE_NAME = "tournament_standings_shares"
    }
}

@Singleton
class SupabaseTournamentStandingsShareRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val gateway: TournamentStandingsShareGateway,
) : TournamentStandingsShareRemoteDataSource {
    override suspend fun publish(
        tournamentId: String,
        rows: List<TournamentStandingRowUiState>,
    ): TournamentStandingsSharePublicationResult {
        if (tournamentId.isBlank() || rows.isEmpty() || rows.size > MAX_ROW_COUNT) {
            return failure(TournamentStandingsShareFailureReason.INVALID_INPUT)
        }
        if (!config.isConfigured) {
            return failure(TournamentStandingsShareFailureReason.SERVER_CONFIGURATION)
        }
        if (!gateway.hasAuthenticatedSession()) {
            return failure(TournamentStandingsShareFailureReason.AUTHENTICATION_REQUIRED)
        }

        val standings = serializeRows(rows)
        return try {
            val selectedTokens = gateway.selectShareTokens(tournamentId)
            when (selectedTokens.size) {
                0 -> publishNewOrRecover(
                    tournamentId = tournamentId,
                    standings = standings,
                )
                1 -> publishExisting(
                    tournamentId = tournamentId,
                    standings = standings,
                    shareToken = selectedTokens.single(),
                )
                else -> failure(TournamentStandingsShareFailureReason.INVALID_RESPONSE)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            failure(throwable.toFailureReason())
        }
    }

    private suspend fun publishNewOrRecover(
        tournamentId: String,
        standings: JsonArray,
    ): TournamentStandingsSharePublicationResult = when (
        val insertResult = gateway.insertShare(tournamentId, standings)
    ) {
        is TournamentStandingsShareInsertResult.Created ->
            insertResult.shareToken
                ?.validatedShareToken()
                ?.let { token -> success(token) }
                ?: failure(TournamentStandingsShareFailureReason.INVALID_RESPONSE)
        TournamentStandingsShareInsertResult.Conflict -> {
            val recoveredTokens = gateway.selectShareTokens(tournamentId)
            if (recoveredTokens.size != 1) {
                failure(TournamentStandingsShareFailureReason.INVALID_RESPONSE)
            } else {
                publishExisting(
                    tournamentId = tournamentId,
                    standings = standings,
                    shareToken = recoveredTokens.single(),
                )
            }
        }
    }

    private suspend fun publishExisting(
        tournamentId: String,
        standings: JsonArray,
        shareToken: String?,
    ): TournamentStandingsSharePublicationResult {
        val token = shareToken
            ?.validatedShareToken()
            ?: return failure(TournamentStandingsShareFailureReason.INVALID_RESPONSE)
        gateway.updateShare(
            TournamentStandingsShareUpdateRequest(
                tournamentId = tournamentId,
                standings = standings,
                updatedAt = Instant.now().toString(),
            ),
        )
        return success(token)
    }

    private fun success(token: String): TournamentStandingsSharePublicationResult.Success =
        TournamentStandingsSharePublicationResult.Success(
            publicUrl = "$PUBLIC_VIEWER_BASE_URL?token=$token",
        )

    private fun failure(
        reason: TournamentStandingsShareFailureReason,
    ): TournamentStandingsSharePublicationResult.Failure =
        TournamentStandingsSharePublicationResult.Failure(reason)

    private companion object {
        const val MAX_ROW_COUNT = 12
        const val PUBLIC_VIEWER_BASE_URL =
            "https://hog-gamers-info-hub.github.io/Rank-Forge/standings/"
    }
}

internal fun serializeRows(rows: List<TournamentStandingRowUiState>): JsonArray = buildJsonArray {
    rows.forEach { row ->
        add(
            buildJsonObject {
                put("displayOrder", row.displayOrder)
                put("teamSlotNumber", row.teamSlotNumber)
                if (row.teamName == null) {
                    put("teamName", JsonNull)
                } else {
                    put("teamName", row.teamName)
                }
                put("totalPoints", row.totalPoints)
                put("totalPositionPoints", row.totalPositionPoints)
                put("totalKillPoints", row.totalKillPoints)
                put("firstPlaceFinishes", row.firstPlaceFinishes)
                if (row.latestMatchPlacement == null) {
                    put("latestMatchPlacement", JsonNull)
                } else {
                    put("latestMatchPlacement", row.latestMatchPlacement)
                }
                put("matchesIncluded", row.matchesIncluded)
                put("isCompleteTie", row.isCompleteTie)
            },
        )
    }
}

internal fun shareTokenColumns() =
    io.github.jan.supabase.postgrest.query.Columns.list("share_token")

internal fun buildInsertPayload(
    tournamentId: String,
    standings: JsonArray,
): JsonObject = buildJsonObject {
    put("tournament_id", tournamentId)
    put("standings", standings)
}

internal fun buildUpdatePayload(
    request: TournamentStandingsShareUpdateRequest,
): JsonObject = buildJsonObject {
    put("standings", request.standings)
    put("updated_at", request.updatedAt)
}

private val SHARE_TOKEN_UUID_PATTERN =
    Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

private fun String.validatedShareToken(): String? =
    takeIf { SHARE_TOKEN_UUID_PATTERN.matches(it) }?.also { UUID.fromString(it) }

private fun Throwable.toFailureReason(): TournamentStandingsShareFailureReason = when {
    this is IOException -> TournamentStandingsShareFailureReason.NETWORK_FAILURE
    this is RestException && statusCode == 401 ->
        TournamentStandingsShareFailureReason.AUTHENTICATION_REQUIRED
    else -> TournamentStandingsShareFailureReason.SERVER_FAILURE
}
