package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.Tournament
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Cloud-shaped representation of a registered, tournament-scoped team identity. */
@Serializable
data class GroupRotationUuidTeamIdentityPayload(
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("team_uuid") val teamUUID: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("normalized_name") val normalizedName: String,
)

/** Cloud-shaped representation of one pairing-local lobby assignment. */
@Serializable
data class GroupRotationUuidPairingAssignmentPayload(
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("pairing_key") val pairingKey: String,
    @SerialName("lobby_slot_number") val lobbySlotNumber: Int,
    @SerialName("team_uuid") val teamUUID: String,
)

/** Immutable match-time lobby position to UUID snapshot sent to the V4 write RPC. */
@Serializable
data class GroupRotationUuidMatchLobbySnapshotPayload(
    @SerialName("lobby_slot_number") val lobbySlotNumber: Int,
    @SerialName("team_uuid") val teamUUID: String,
)

/** UUID-native draft or finalized participant result. */
@Serializable
data class GroupRotationUuidParticipantResultPayload(
    @SerialName("team_uuid") val teamUUID: String,
    @SerialName("participation_status") val participationStatus: String,
    val placement: Int?,
    val kills: Int,
    @SerialName("point_adjustment") val pointAdjustment: Int,
)

/** The exact p_match JSON object accepted by write_match_snapshot_v4. */
@Serializable
data class GroupRotationV4MatchPayload(
    val id: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("pairing_key") val pairingKey: String,
)

/** Exact parameters of write_match_snapshot_v4. */
@Serializable
data class GroupRotationV4DraftWriteParameters(
    @SerialName("p_tournament_id") val tournamentId: String,
    @SerialName("p_match") val match: GroupRotationV4MatchPayload,
    @SerialName("p_match_identity")
    val matchIdentity: List<GroupRotationUuidMatchLobbySnapshotPayload>,
    @SerialName("p_match_results")
    val matchResults: List<GroupRotationUuidParticipantResultPayload>,
    @SerialName("p_expected_revision") val expectedRevision: Int,
    @SerialName("p_request_id") val requestId: String,
)

/** Exact parameters of finalize_match_snapshot_v4. */
@Serializable
data class GroupRotationV4FinalizationParameters(
    @SerialName("p_tournament_id") val tournamentId: String,
    @SerialName("p_match_id") val matchId: String,
    @SerialName("p_match_results")
    val matchResults: List<GroupRotationUuidParticipantResultPayload>,
    @SerialName("p_expected_revision") val expectedRevision: Int,
    @SerialName("p_request_id") val requestId: String,
)

/** Exact row returned by both V4 RPCs. */
@Serializable
data class GroupRotationV4RpcResponse(
    val outcome: String,
    val revision: Int? = null,
)

/** Source data required to prove that a match snapshot uses saved UUID authority. */
data class GroupRotationMatchCloudV4RequestSource(
    val identitySnapshot: com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceSnapshot,
    val savedIdentitySnapshot: com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalSnapshot,
)

data class GroupRotationV4DraftWriteRequest(
    val tournament: Tournament,
    val tournamentId: String,
    val matchId: String,
    val source: GroupRotationMatchCloudV4RequestSource,
    val expectedRevision: Int,
    val requestId: String,
)

data class GroupRotationV4FinalizationRequest(
    val tournament: Tournament,
    val tournamentId: String,
    val matchId: String,
    val source: GroupRotationMatchCloudV4RequestSource,
    val expectedRevision: Int,
    val requestId: String,
)

enum class GroupRotationMatchCloudV4IssueCode {
    NOT_GROUP_ROTATION,
    INVALID_TOURNAMENT_ID,
    INVALID_MATCH_ID,
    INVALID_REQUEST_ID,
    INVALID_EXPECTED_REVISION,
    TOURNAMENT_ID_MISMATCH,
    MATCH_IDENTITY_TOURNAMENT_MISMATCH,
    UNSELECTED_PAIRING,
    INVALID_UUID_IDENTITY,
    DUPLICATE_TEAM_UUID,
    DUPLICATE_NORMALIZED_NAME,
    TEAM_UUID_NOT_FOUND,
    TEAM_UUID_TOURNAMENT_MISMATCH,
    INVALID_PAIRING_ASSIGNMENT,
    DUPLICATE_LOBBY_SLOT,
    DUPLICATE_ASSIGNED_TEAM_UUID,
    MATCH_PAIRING_MISMATCH,
    MATCH_IDENTITY_MISMATCH,
    EMPTY_MATCH_IDENTITY,
    INVALID_PARTICIPATION_STATUS,
    PARTICIPANT_TEAM_NOT_IN_MATCH,
    DUPLICATE_PARTICIPANT_RESULT,
    INVALID_PLACEMENT,
    INVALID_KILLS,
    INVALID_NO_SHOW_RESULT,
    DUPLICATE_PLACEMENT,
    MISSING_PARTICIPANT_RESULT,
    PLACEMENTS_NOT_GAP_FREE,
    NO_PARTICIPATED_TEAM,
}

data class GroupRotationMatchCloudV4Issue(
    val code: GroupRotationMatchCloudV4IssueCode,
    val teamUUID: String? = null,
    val lobbySlotNumber: Int? = null,
)

sealed interface GroupRotationV4MappingResult<out P> {
    data class Success<P>(val parameters: P) : GroupRotationV4MappingResult<P>
    data class Invalid(val issues: List<GroupRotationMatchCloudV4Issue>) : GroupRotationV4MappingResult<Nothing>
}

enum class GroupRotationMatchCloudV4FailureCategory {
    AUTHENTICATION,
    AUTHORIZATION,
    NETWORK,
    VALIDATION,
    CONFLICT,
    UNKNOWN,
}

sealed interface GroupRotationMatchCloudV4ExecutionResult {
    data class Success(
        val confirmedCloudRevision: Int,
        val outcome: String = "success",
    ) : GroupRotationMatchCloudV4ExecutionResult

    data class Failure(
        val category: GroupRotationMatchCloudV4FailureCategory,
        val outcome: String? = null,
        val conflict: RevisionConflict? = null,
        val cloudRevision: Int? = null,
    ) : GroupRotationMatchCloudV4ExecutionResult
}
