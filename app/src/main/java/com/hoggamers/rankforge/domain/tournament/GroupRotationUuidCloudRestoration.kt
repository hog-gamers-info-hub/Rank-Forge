package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.CloudRevision

/**
 * One immutable UUID-backed match snapshot read from the V4 cloud tables.
 * The numeric Match model is retained only for the existing match header;
 * identity and results remain in the UUID-native fields below.
 */
data class GroupRotationUuidMatchRestorationSnapshot(
    val match: Match,
    val identity: GroupRotationMatchIdentitySnapshot,
    val participantResults: List<GroupRotationMatchParticipantResult>,
)

/** Complete, revision-fenced Group Rotation restoration payload. */
data class GroupRotationUuidCloudRestorationSnapshot(
    val parent: TournamentCloudRestorationSnapshot,
    val identities: List<GroupRotationTeamIdentity>,
    val assignments: List<GroupRotationPairingAssignment>,
    val matches: List<Match>,
    val uuidMatches: List<GroupRotationUuidMatchRestorationSnapshot>,
    val cloudRevision: CloudRevision,
)

sealed interface GroupRotationUuidCloudRestorationResult {
    data class Restored(val tournamentName: String) : GroupRotationUuidCloudRestorationResult
    data object NotUuidBacked : GroupRotationUuidCloudRestorationResult
    data object AuthenticationRequired : GroupRotationUuidCloudRestorationResult
    data object AuthorizationFailure : GroupRotationUuidCloudRestorationResult
    data object NotFound : GroupRotationUuidCloudRestorationResult
    data object ValidationFailure : GroupRotationUuidCloudRestorationResult
    data object NetworkFailure : GroupRotationUuidCloudRestorationResult
    data object Conflict : GroupRotationUuidCloudRestorationResult
    data object DeletionBlocked : GroupRotationUuidCloudRestorationResult
    data object LocalTransactionFailure : GroupRotationUuidCloudRestorationResult
}

sealed interface GroupRotationUuidLocalRestoreResult {
    data object Restored : GroupRotationUuidLocalRestoreResult
    data object Conflict : GroupRotationUuidLocalRestoreResult
    data object DeletionBlocked : GroupRotationUuidLocalRestoreResult
    data object Invalid : GroupRotationUuidLocalRestoreResult
}

/** Isolated local boundary; normal restoration routing does not use it. */
interface GroupRotationUuidCloudRestorationLocalRepository {
    suspend fun restoreGroupRotationUuidSnapshot(
        snapshot: GroupRotationUuidCloudRestorationSnapshot,
        expectedOwnerUserId: String,
    ): GroupRotationUuidLocalRestoreResult
}
