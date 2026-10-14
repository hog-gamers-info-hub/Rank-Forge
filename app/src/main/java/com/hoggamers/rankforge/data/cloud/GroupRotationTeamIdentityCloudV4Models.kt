package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.Tournament
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Exact team identity object accepted by write_group_rotation_uuid_identity_snapshot_v4. */
@Serializable
data class GroupRotationTeamIdentityV4Payload(
    @SerialName("team_uuid") val teamUUID: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("normalized_name") val normalizedName: String,
)

/** Exact nested assignment object accepted by the V4 identity RPC. */
@Serializable
data class GroupRotationPairingTeamAssignmentV4Payload(
    @SerialName("lobby_slot_number") val lobbySlotNumber: Int,
    @SerialName("team_uuid") val teamUUID: String,
    @SerialName("name_cleared") val nameCleared: Boolean,
)

/** One explicit pairing replacement scope. */
@Serializable
data class GroupRotationPairingAssignmentsV4Payload(
    @SerialName("pairing_key") val pairingKey: String,
    val assignments: List<GroupRotationPairingTeamAssignmentV4Payload>,
)

/** Exact parameters of write_group_rotation_uuid_identity_snapshot_v4. */
@Serializable
data class GroupRotationTeamIdentityV4WriteParameters(
    @SerialName("p_tournament_id") val tournamentId: String,
    @SerialName("p_team_identities") val teamIdentities: List<GroupRotationTeamIdentityV4Payload>,
    @SerialName("p_pairing_assignments")
    val pairingAssignments: List<GroupRotationPairingAssignmentsV4Payload>,
    @SerialName("p_expected_revision") val expectedRevision: Int,
    @SerialName("p_request_id") val requestId: String,
)

/**
 * Caller-owned scope for one identity snapshot write. A pairing listed here is
 * replaced, including when its saved assignment list is empty. An omitted
 * pairing is not sent and remains unchanged in the cloud.
 */
data class GroupRotationTeamIdentityV4WriteRequest(
    val tournament: Tournament,
    val pairingsToReplace: List<GroupPairing>,
    val expectedRevision: Int,
    val requestId: String,
)

@Serializable
data class GroupRotationTeamIdentityV4RpcResponse(
    val outcome: String,
    val revision: Int? = null,
)

enum class GroupRotationTeamIdentityV4IssueCode {
    NOT_GROUP_ROTATION,
    INVALID_TOURNAMENT_ID,
    INVALID_REQUEST_ID,
    INVALID_EXPECTED_REVISION,
    DUPLICATE_PAIRING_SCOPE,
    UNSELECTED_PAIRING,
    LOCAL_IDENTITY_READ_FAILED,
    LOCAL_IDENTITY_READ_UNSUPPORTED,
    TEAM_UUID_TOURNAMENT_MISMATCH,
    INVALID_TEAM_UUID,
    BLANK_TEAM_NAME,
    INVALID_NORMALIZED_NAME,
    DUPLICATE_TEAM_UUID,
    DUPLICATE_NORMALIZED_NAME,
    INVALID_ASSIGNMENT_TOURNAMENT,
    INVALID_LOBBY_SLOT,
    DUPLICATE_LOBBY_SLOT,
    DUPLICATE_TEAM_IN_PAIRING,
    TEAM_UUID_NOT_FOUND,
    ASSIGNMENT_UNSELECTED_PAIRING,
}

data class GroupRotationTeamIdentityV4Issue(
    val code: GroupRotationTeamIdentityV4IssueCode,
    val teamUUID: String? = null,
    val pairingKey: String? = null,
    val lobbySlotNumber: Int? = null,
)

sealed interface GroupRotationTeamIdentityV4MappingResult {
    data class Success(
        val parameters: GroupRotationTeamIdentityV4WriteParameters,
    ) : GroupRotationTeamIdentityV4MappingResult

    data class Invalid(
        val issues: List<GroupRotationTeamIdentityV4Issue>,
    ) : GroupRotationTeamIdentityV4MappingResult
}

enum class GroupRotationTeamIdentityV4FailureCategory {
    AUTHENTICATION,
    AUTHORIZATION,
    NETWORK,
    VALIDATION,
    CONFLICT,
    UNKNOWN,
}

sealed interface GroupRotationTeamIdentityV4ExecutionResult {
    data class Success(
        val confirmedCloudRevision: Int,
        val outcome: String = "success",
    ) : GroupRotationTeamIdentityV4ExecutionResult

    data class Failure(
        val category: GroupRotationTeamIdentityV4FailureCategory,
        val outcome: String? = null,
        val conflict: RevisionConflict? = null,
        val cloudRevision: Int? = null,
    ) : GroupRotationTeamIdentityV4ExecutionResult
}
