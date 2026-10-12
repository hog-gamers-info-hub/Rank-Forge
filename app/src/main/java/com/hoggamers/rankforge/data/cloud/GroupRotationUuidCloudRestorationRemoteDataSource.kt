package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
            return GroupRotationUuidCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.VALIDATION,
            )
        }
        if (clientProvider.client.auth.currentSessionOrNull() == null) {
            return GroupRotationUuidCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.AUTHENTICATION,
            )
        }
        return try {
            readRevisionFencedSnapshot(tournamentId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            GroupRotationUuidCloudRestorationRemoteResult.Failure(throwable.toFailureCategory())
        }
    }

    private suspend fun readRevisionFencedSnapshot(
        tournamentId: String,
    ): GroupRotationUuidCloudRestorationRemoteResult {
        repeat(MAX_REVISION_READ_ATTEMPTS) { attempt ->
            val firstParent = tournamentReader.readTournament(tournamentId)
                ?: return GroupRotationUuidCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.NOT_FOUND,
                )
            if (firstParent.id != tournamentId || firstParent.revision <= 0) {
                return GroupRotationUuidCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.VALIDATION,
                )
            }

            val slots = tournamentReader.readTeamSlots(tournamentId)
            val players = slots
                .sortedBy { it.slotNumber }
                .flatMap { slot -> tournamentReader.readPlayers(slot.id) }
            val pairings = tournamentReader.readPairings(tournamentId)
            val pairingLobbySlots = tournamentReader.readPairingLobbySlots(tournamentId)
            val matches = matchReader.readMatches(tournamentId)
            val legacyResults = matches.flatMap { match -> matchReader.readResults(match.id) }
            val identities = clientProvider.client
                .from("tournament_team_identities")
                .select { filter { eq("tournament_id", tournamentId) } }
                .decodeList<GroupRotationUuidTeamIdentityPayload>()
            val assignments = clientProvider.client
                .from("tournament_group_pairing_team_assignments")
                .select { filter { eq("tournament_id", tournamentId) } }
                .decodeList<GroupRotationUuidPairingAssignmentPayload>()
            val uuidMatchSnapshots = clientProvider.client
                .from("group_rotation_match_uuid_snapshots")
                .select { filter { eq("tournament_id", tournamentId) } }
                .decodeList<GroupRotationUuidMatchSnapshotRestorePayload>()
            val uuidMatchAssignments = clientProvider.client
                .from("group_rotation_match_uuid_assignments")
                .select { filter { eq("tournament_id", tournamentId) } }
                .decodeList<GroupRotationUuidMatchAssignmentRestorePayload>()
            val uuidMatchResults = clientProvider.client
                .from("group_rotation_match_uuid_results")
                .select { filter { eq("tournament_id", tournamentId) } }
                .decodeList<GroupRotationUuidMatchResultRestorePayload>()

            val secondParent = tournamentReader.readTournament(tournamentId)
                ?: return GroupRotationUuidCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.NOT_FOUND,
                )
            if (secondParent.id != tournamentId || secondParent.revision <= 0) {
                return GroupRotationUuidCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.VALIDATION,
                )
            }
            if (firstParent.revision == secondParent.revision) {
                return GroupRotationUuidCloudRestorationRemoteResult.Success(
                    GroupRotationUuidCloudRestorationPayloads(
                        parent = TournamentCloudRestorationPayloads(
                            tournament = firstParent.toUploadPayload(),
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
                return GroupRotationUuidCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.NETWORK,
                )
            }
        }
        error("Revision-fenced Group Rotation UUID restoration read did not complete.")
    }
}

private const val MAX_REVISION_READ_ATTEMPTS = 2

private fun TeamSlotCloudRestorePayload.toUploadPayload() = TeamSlotUploadPayload(
    id = id,
    tournamentId = tournamentId,
    slotNumber = slotNumber,
    teamName = teamName,
    status = status,
    group = group,
)

private fun TournamentCloudRestorePayload.toUploadPayload() = TournamentUploadPayload(
    id = id,
    ownerId = ownerId,
    name = name,
    stageName = stageName,
    organizerContact = organizerContact,
    status = status,
    revision = revision,
    format = format,
    groupCount = groupCount,
    selectedGroupPairings = selectedGroupPairings,
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
