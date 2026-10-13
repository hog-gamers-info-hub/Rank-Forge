package com.hoggamers.rankforge.data.cloud

import android.util.Log
import com.hoggamers.rankforge.BuildConfig
import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
data class GroupRotationUuidMatchSnapshotRestorePayload(
    @SerialName("match_id") val matchId: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("pairing_key") val pairingKey: String,
    val status: String,
    val revision: Int,
    @SerialName("finalized_at") val finalizedAt: String? = null,
    @SerialName("finalized_by") val finalizedBy: String? = null,
)

@Serializable
data class GroupRotationUuidMatchAssignmentRestorePayload(
    @SerialName("match_id") val matchId: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("lobby_slot_number") val lobbySlotNumber: Int,
    @SerialName("team_uuid") val teamUUID: String,
)

@Serializable
data class GroupRotationUuidMatchResultRestorePayload(
    @SerialName("match_id") val matchId: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("team_uuid") val teamUUID: String,
    @SerialName("participation_status") val participationStatus: String,
    val placement: Int? = null,
    val kills: Int,
    @SerialName("point_adjustment") val pointAdjustment: Int = 0,
)

data class GroupRotationUuidCloudRestorationPayloads(
    val parent: TournamentCloudRestorationPayloads,
    val matches: List<MatchCloudRestorePayload>,
    val legacyResults: List<MatchResultCloudRestorePayload>,
    val identities: List<GroupRotationUuidTeamIdentityPayload>,
    val assignments: List<GroupRotationUuidPairingAssignmentPayload>,
    val uuidMatchSnapshots: List<GroupRotationUuidMatchSnapshotRestorePayload>,
    val uuidMatchAssignments: List<GroupRotationUuidMatchAssignmentRestorePayload>,
    val uuidMatchResults: List<GroupRotationUuidMatchResultRestorePayload>,
)

sealed interface GroupRotationUuidCloudRestorationRemoteResult {
    data class Success(val payloads: GroupRotationUuidCloudRestorationPayloads) :
        GroupRotationUuidCloudRestorationRemoteResult

    data class Failure(val category: TournamentCloudRestorationFailureCategory) :
        GroupRotationUuidCloudRestorationRemoteResult
}

interface GroupRotationUuidCloudRestorationRemoteDataSource {
    suspend fun readOwnedGroupRotation(
        tournamentId: String,
    ): GroupRotationUuidCloudRestorationRemoteResult
}

/**
 * Reads the additive V4 tables directly through owner-scoped PostgREST SELECTs.
 * The parent revision is fenced around the complete read; no partial response
 * is converted to an empty snapshot.
 */
@Singleton
class SupabaseGroupRotationUuidCloudRestorationRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
    private val tournamentReader: TournamentCloudRestorationRemoteReader,
    private val matchReader: MatchCloudRestorationRemoteReader,
) : GroupRotationUuidCloudRestorationRemoteDataSource {
    override suspend fun readOwnedGroupRotation(
        tournamentId: String,
    ): GroupRotationUuidCloudRestorationRemoteResult {
        if (!config.isConfigured) {
            debugRemote("REMOTE_CONFIG_UNAVAILABLE")
            return GroupRotationUuidCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.VALIDATION,
            )
        }
        if (clientProvider.client.auth.currentSessionOrNull() == null) {
            debugRemote("REMOTE_AUTH_SESSION_MISSING")
            return GroupRotationUuidCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.AUTHENTICATION,
            )
        }
        return try {
            readRevisionFencedSnapshot(tournamentId).also { result ->
                debugRemote("REMOTE_RESULT_${result.diagnosticCode()}")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            debugRemote("REMOTE_RESULT_FAILURE_${throwable.diagnosticCode()}")
            GroupRotationUuidCloudRestorationRemoteResult.Failure(throwable.toFailureCategory())
        }
    }

    private suspend fun readRevisionFencedSnapshot(
        tournamentId: String,
    ): GroupRotationUuidCloudRestorationRemoteResult {
        repeat(MAX_REVISION_READ_ATTEMPTS) { attempt ->
            val firstParent = readStage("PARENT_FIRST") {
                tournamentReader.readTournament(tournamentId)
            }
                ?: return GroupRotationUuidCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.NOT_FOUND
                ).also { debugRemote("REMOTE_PARENT_FIRST_NOT_FOUND") }
            if (firstParent.id != tournamentId || firstParent.revision <= 0) {
                return GroupRotationUuidCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.VALIDATION,
                ).also { debugRemote("REMOTE_PARENT_FIRST_INVALID") }
            }

            val slots = readStage("TEAM_SLOTS") { tournamentReader.readTeamSlots(tournamentId) }
            val players = readStage("PLAYERS") {
                slots.sortedBy { it.slotNumber }
                    .flatMap { slot -> tournamentReader.readPlayers(slot.id) }
            }
            val pairings = readStage("PAIRINGS") { tournamentReader.readPairings(tournamentId) }
            val pairingLobbySlots = readStage("PAIRING_LOBBY_SLOTS") {
                tournamentReader.readPairingLobbySlots(tournamentId)
            }
            val matches = readStage("MATCHES") { matchReader.readMatches(tournamentId) }
            val legacyResults = readStage("LEGACY_RESULTS") {
                matches.flatMap { match -> matchReader.readResults(match.id) }
            }
            val identities = readStage("IDENTITIES") {
                clientProvider.client
                    .from("tournament_team_identities")
                    .select { filter { eq("tournament_id", tournamentId) } }
                    .decodeList<GroupRotationUuidTeamIdentityPayload>()
            }
            val assignments = readStage("PAIRING_ASSIGNMENTS") {
                clientProvider.client
                    .from("tournament_group_pairing_team_assignments")
                    .select { filter { eq("tournament_id", tournamentId) } }
                    .decodeList<GroupRotationUuidPairingAssignmentPayload>()
            }
            val uuidMatchSnapshots = readStage("UUID_MATCH_HEADERS") {
                clientProvider.client
                    .from("group_rotation_match_uuid_snapshots")
                    .select { filter { eq("tournament_id", tournamentId) } }
                    .decodeList<GroupRotationUuidMatchSnapshotRestorePayload>()
            }
            val uuidMatchAssignments = readStage("UUID_MATCH_ASSIGNMENTS") {
                clientProvider.client
                    .from("group_rotation_match_uuid_assignments")
                    .select { filter { eq("tournament_id", tournamentId) } }
                    .decodeList<GroupRotationUuidMatchAssignmentRestorePayload>()
            }
            val uuidMatchResults = readStage("UUID_MATCH_RESULTS") {
                clientProvider.client
                    .from("group_rotation_match_uuid_results")
                    .select { filter { eq("tournament_id", tournamentId) } }
                    .decodeList<GroupRotationUuidMatchResultRestorePayload>()
            }

            val secondParent = readStage("PARENT_SECOND") {
                tournamentReader.readTournament(tournamentId)
            }
                ?: return GroupRotationUuidCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.NOT_FOUND
                ).also { debugRemote("REMOTE_PARENT_SECOND_NOT_FOUND") }
            if (secondParent.id != tournamentId || secondParent.revision <= 0) {
                return GroupRotationUuidCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.VALIDATION,
                ).also { debugRemote("REMOTE_PARENT_SECOND_INVALID") }
            }
            if (firstParent.revision == secondParent.revision) {
                debugRemote("REMOTE_REVISION_FENCE_STABLE")
                return GroupRotationUuidCloudRestorationRemoteResult.Success(
                    GroupRotationUuidCloudRestorationPayloads(
                        parent = TournamentCloudRestorationPayloads(
                            tournament = firstParent.toUploadPayload(pairings),
                            teamSlots = slots.map { it.toUploadPayload() },
                            players = players.map { it.toUploadPayload() },
                            pairingLobbySlots = pairingLobbySlots
                                .sortedWith(compareBy({ it.pairingKey }, { it.lobbySlotNumber })),
                        ),
                        matches = matches,
                        legacyResults = legacyResults,
                        identities = identities,
                        assignments = assignments,
                        uuidMatchSnapshots = uuidMatchSnapshots,
                        uuidMatchAssignments = uuidMatchAssignments,
                        uuidMatchResults = uuidMatchResults,
                    ),
                )
            }
            if (attempt == MAX_REVISION_READ_ATTEMPTS - 1) {
                debugRemote("REMOTE_REVISION_FENCE_EXHAUSTED")
                return GroupRotationUuidCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.NETWORK,
                )
            }
            debugRemote("REMOTE_REVISION_CHANGED_RETRY")
        }
        error("Revision-fenced Group Rotation UUID restoration read did not complete.")
    }

    private suspend fun <T> readStage(
        stage: String,
        block: suspend () -> T,
    ): T = try {
        block().also { debugRemote("REMOTE_STAGE_${stage}_SUCCESS") }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        debugRemote("REMOTE_STAGE_${stage}_FAILURE_${throwable.diagnosticCode()}")
        throw throwable
    }
}

private const val MAX_REVISION_READ_ATTEMPTS = 2
private const val RESTORE_LOG_TAG = "POINTIQ_V4_RESTORE"

private fun TeamSlotCloudRestorePayload.toUploadPayload() = TeamSlotUploadPayload(
    id = id,
    tournamentId = tournamentId,
    slotNumber = slotNumber,
    teamName = teamName,
    status = status,
    group = group,
)

private fun PlayerCloudRestorePayload.toUploadPayload() = PlayerUploadPayload(
    id = id,
    teamSlotId = teamSlotId,
    displayName = displayName,
    normalizedName = normalizedName,
)

private fun Throwable.toFailureCategory(): TournamentCloudRestorationFailureCategory {
    val message = message.orEmpty().lowercase()
    return when {
        message.contains("42501") ||
            message.contains("row-level security") ||
            message.contains("permission") ||
            message.contains("forbidden") ||
            message.contains("403") -> TournamentCloudRestorationFailureCategory.AUTHORIZATION
        message.contains("401") ||
            message.contains("unauthorized") ||
            message.contains("session") ||
            message.contains("jwt") -> TournamentCloudRestorationFailureCategory.AUTHENTICATION
        this is IOException ||
            message.contains("network") ||
            message.contains("timeout") ||
            message.contains("connection") -> TournamentCloudRestorationFailureCategory.NETWORK
        else -> TournamentCloudRestorationFailureCategory.VALIDATION
    }
}

private fun GroupRotationUuidCloudRestorationRemoteResult.diagnosticCode() = when (this) {
    is GroupRotationUuidCloudRestorationRemoteResult.Success -> "SUCCESS"
    is GroupRotationUuidCloudRestorationRemoteResult.Failure -> "FAILURE_${category.name}"
}

private fun Throwable.diagnosticCode(): String = when (this) {
    is RestException ->
        "POSTGREST_STATUS_${statusCode}_ERROR_${error.toDiagnosticCode()}"
    is SerializationException -> "JSON_DECODE"
    is IOException -> "NETWORK_TYPE_${simpleTypeName()}"
    else -> "OTHER_TYPE_${simpleTypeName()}"
}

private fun Throwable.simpleTypeName(): String =
    this::class.java.simpleName
        .filter { it.isLetterOrDigit() || it == '_' }
        .take(32)
        .ifBlank { "UNKNOWN" }

private fun String.toDiagnosticCode(): String =
    uppercase().let { code ->
        when {
            code.length == 5 && code.all { character -> character.isDigit() } -> code
            code.startsWith("PGRST") && code.length <= 32 && code.drop(5).all { character -> character.isDigit() } -> code
            else -> "UNKNOWN"
        }
    }

private fun debugRemote(code: String) {
    if (BuildConfig.DEBUG) Log.d(RESTORE_LOG_TAG, code)
}
