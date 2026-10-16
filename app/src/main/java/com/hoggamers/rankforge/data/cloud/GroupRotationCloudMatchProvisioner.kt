package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionProbe
import com.hoggamers.rankforge.data.auth.allowsAuthenticatedState
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentityMode
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentityPersistenceRepository
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.SyncDraftMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.readIdentityMode
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface GroupRotationCloudMatchProvisionResult {
    data object Ensured : GroupRotationCloudMatchProvisionResult
    data object NotRequired : GroupRotationCloudMatchProvisionResult
    data object AuthenticationRequired : GroupRotationCloudMatchProvisionResult
    data object AuthorizationFailure : GroupRotationCloudMatchProvisionResult
    data object ValidationFailure : GroupRotationCloudMatchProvisionResult
    data object NetworkFailure : GroupRotationCloudMatchProvisionResult
}

sealed interface GroupRotationCloudMatchRemoteStateResult {
    data object NotRequired : GroupRotationCloudMatchRemoteStateResult

    data class Missing(
        val currentTournamentRevision: Int,
    ) : GroupRotationCloudMatchRemoteStateResult

    data class Loaded(
        val snapshot: GroupRotationUuidMatchSnapshotRestorePayload,
        val assignments: List<GroupRotationUuidMatchAssignmentRestorePayload>,
        val results: List<GroupRotationUuidMatchResultRestorePayload>,
        val currentTournamentRevision: Int,
    ) : GroupRotationCloudMatchRemoteStateResult

    data object AuthenticationRequired : GroupRotationCloudMatchRemoteStateResult
    data object AuthorizationFailure : GroupRotationCloudMatchRemoteStateResult
    data object ValidationFailure : GroupRotationCloudMatchRemoteStateResult
    data object NetworkFailure : GroupRotationCloudMatchRemoteStateResult
}

interface GroupRotationCloudMatchProvisioner {
    suspend fun ensureParent(
        tournamentId: String,
        matchId: String,
        expectedOwnerUserId: String? = null,
    ): GroupRotationCloudMatchProvisionResult

    suspend fun resolveCloudMatchId(
        tournamentId: String,
        matchId: String,
        expectedOwnerUserId: String? = null,
    ): String?

    suspend fun readRemoteState(
        tournamentId: String,
        matchId: String,
        expectedOwnerUserId: String? = null,
    ): GroupRotationCloudMatchRemoteStateResult =
        GroupRotationCloudMatchRemoteStateResult.NotRequired
}

object NoOpGroupRotationCloudMatchProvisioner : GroupRotationCloudMatchProvisioner {
    override suspend fun ensureParent(
        tournamentId: String,
        matchId: String,
        expectedOwnerUserId: String?,
    ): GroupRotationCloudMatchProvisionResult = GroupRotationCloudMatchProvisionResult.NotRequired

    override suspend fun resolveCloudMatchId(
        tournamentId: String,
        matchId: String,
        expectedOwnerUserId: String?,
    ): String? = MatchCloudIdentity.matchId(tournamentId, matchId)
}

@Singleton
class SupabaseGroupRotationCloudMatchProvisioner @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val sessionProbe: SupabaseAuthSessionProbe,
    private val clientProvider: SupabaseClientProvider,
    private val tournamentRepository: TournamentRepository,
    private val matchIdentityRepository: GroupRotationMatchIdentityPersistenceRepository,
    private val syncDraftMatchesUseCase: SyncDraftMatchesUseCase,
) : GroupRotationCloudMatchProvisioner {
    override suspend fun ensureParent(
        tournamentId: String,
        matchId: String,
        expectedOwnerUserId: String?,
    ): GroupRotationCloudMatchProvisionResult {
        val context = readUuidBackedMatch(
            tournamentId = tournamentId,
            matchId = matchId,
            expectedOwnerUserId = expectedOwnerUserId,
            includeStandard = true,
        )
            ?: return GroupRotationCloudMatchProvisionResult.ValidationFailure
        if (context is MatchCloudContext.Standard) {
            val ownerUserId = currentOwnerUserId(expectedOwnerUserId)
                ?: return GroupRotationCloudMatchProvisionResult.AuthenticationRequired
            return ensureStandardParent(context.match, ownerUserId)
        }
        if (context is MatchCloudContext.NotRequired) {
            return GroupRotationCloudMatchProvisionResult.NotRequired
        }
        val uuidContext = context as? MatchCloudContext.UuidBacked
            ?: return GroupRotationCloudMatchProvisionResult.ValidationFailure
        val ownerUserId = currentOwnerUserId(expectedOwnerUserId)
            ?: return GroupRotationCloudMatchProvisionResult.AuthenticationRequired
        val payload = GroupRotationMatchParentCloudPayload(
            id = uuidContext.match.id,
            tournamentId = uuidContext.match.tournamentId,
            matchNumber = uuidContext.match.matchNumber,
            matchDate = uuidContext.match.date.toString(),
            mapName = uuidContext.match.mapName,
            status = "draft",
            groupPairingKey = uuidContext.match.groupPairing?.canonicalKey
                ?: return GroupRotationCloudMatchProvisionResult.ValidationFailure,
        )
        return try {
            val existing = clientProvider.client
                .from(MATCHES_TABLE)
                .select { filter { eq("id", payload.id) } }
                .decodeList<GroupRotationMatchParentCloudRow>()
                .singleOrNull()
            if (existing != null) {
                return validateExisting(existing, payload, ownerUserId)
            }

            clientProvider.client.from(MATCHES_TABLE).insert(payload)
            val inserted = clientProvider.client
                .from(MATCHES_TABLE)
                .select { filter { eq("id", payload.id) } }
                .decodeList<GroupRotationMatchParentCloudRow>()
                .singleOrNull()
            if (inserted == null) {
                GroupRotationCloudMatchProvisionResult.NetworkFailure
            } else {
                validateExisting(inserted, payload, ownerUserId)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val raced = runCatching {
                clientProvider.client
                    .from(MATCHES_TABLE)
                    .select { filter { eq("id", payload.id) } }
                    .decodeList<GroupRotationMatchParentCloudRow>()
                    .singleOrNull()
            }.getOrNull()
            if (raced != null) {
                validateExisting(raced, payload, ownerUserId)
            } else {
                throwable.toProvisionResult()
            }
        }
    }

    override suspend fun resolveCloudMatchId(
        tournamentId: String,
        matchId: String,
        expectedOwnerUserId: String?,
    ): String? {
        return when (val context = readUuidBackedMatch(tournamentId, matchId, expectedOwnerUserId)) {
            is MatchCloudContext.UuidBacked -> context.match.id
            MatchCloudContext.NotRequired -> MatchCloudIdentity.matchId(tournamentId, matchId)
            null -> null
            else -> MatchCloudIdentity.matchId(tournamentId, matchId)
        }
    }

    override suspend fun readRemoteState(
        tournamentId: String,
        matchId: String,
        expectedOwnerUserId: String?,
    ): GroupRotationCloudMatchRemoteStateResult {
        val ownerUserId = currentOwnerUserId(expectedOwnerUserId)
            ?: return GroupRotationCloudMatchRemoteStateResult.AuthenticationRequired
        val context = readUuidBackedMatch(tournamentId, matchId, ownerUserId)
            ?: return GroupRotationCloudMatchRemoteStateResult.AuthorizationFailure
        if (context is MatchCloudContext.NotRequired) {
            return GroupRotationCloudMatchRemoteStateResult.NotRequired
        }
        if (context !is MatchCloudContext.UuidBacked) {
            return GroupRotationCloudMatchRemoteStateResult.ValidationFailure
        }

        return try {
            val tournamentRevision = clientProvider.client
                .from(TOURNAMENTS_TABLE)
                .select { filter { eq("id", tournamentId) } }
                .decodeList<GroupRotationTournamentRevisionCloudRow>()
                .singleOrNull()
                ?.takeIf { it.id == tournamentId && it.revision > 0 }
                ?.revision
                ?: return GroupRotationCloudMatchRemoteStateResult.AuthorizationFailure

            val snapshots = clientProvider.client
                .from(UUID_MATCH_SNAPSHOTS_TABLE)
                .select {
                    filter {
                        eq("match_id", matchId)
                        eq("tournament_id", tournamentId)
                    }
                }
                .decodeList<GroupRotationUuidMatchSnapshotRestorePayload>()
            if (snapshots.size > 1) {
                return GroupRotationCloudMatchRemoteStateResult.ValidationFailure
            }
            val snapshot = snapshots.singleOrNull()
                ?: return GroupRotationCloudMatchRemoteStateResult.Missing(tournamentRevision)
            if (snapshot.matchId != matchId ||
                snapshot.tournamentId != tournamentId ||
                snapshot.status !in setOf("draft", "finalized") ||
                snapshot.revision <= 0 ||
                snapshot.pairingKey.isBlank()
            ) {
                return GroupRotationCloudMatchRemoteStateResult.ValidationFailure
            }
            val parent = clientProvider.client
                .from(MATCHES_TABLE)
                .select {
                    filter {
                        eq("id", matchId)
                        eq("tournament_id", tournamentId)
                    }
                }
                .decodeList<GroupRotationMatchParentCloudRow>()
                .singleOrNull()
                ?: return GroupRotationCloudMatchRemoteStateResult.ValidationFailure
            if (parent.status != snapshot.status || parent.groupPairingKey != snapshot.pairingKey) {
                return GroupRotationCloudMatchRemoteStateResult.ValidationFailure
            }

            val assignments = clientProvider.client
                .from(UUID_MATCH_ASSIGNMENTS_TABLE)
                .select {
                    filter {
                        eq("match_id", matchId)
                        eq("tournament_id", tournamentId)
                    }
                }
                .decodeList<GroupRotationUuidMatchAssignmentRestorePayload>()
            val results = clientProvider.client
                .from(UUID_MATCH_RESULTS_TABLE)
                .select {
                    filter {
                        eq("match_id", matchId)
                        eq("tournament_id", tournamentId)
                    }
                }
                .decodeList<GroupRotationUuidMatchResultRestorePayload>()
            if (assignments.any { it.matchId != matchId || it.tournamentId != tournamentId } ||
                results.any { it.matchId != matchId || it.tournamentId != tournamentId } ||
                assignments.groupBy { it.lobbySlotNumber }.any { it.value.size > 1 } ||
                assignments.groupBy { it.teamUUID }.any { it.value.size > 1 } ||
                results.groupBy { it.teamUUID }.any { it.value.size > 1 }
            ) {
                return GroupRotationCloudMatchRemoteStateResult.ValidationFailure
            }
            GroupRotationCloudMatchRemoteStateResult.Loaded(
                snapshot = snapshot,
                assignments = assignments,
                results = results,
                currentTournamentRevision = tournamentRevision,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            throwable.toRemoteStateResult()
        }
    }

    private suspend fun readUuidBackedMatch(
        tournamentId: String,
        matchId: String,
        expectedOwnerUserId: String?,
        includeStandard: Boolean = false,
    ): MatchCloudContext? {
        val ownerUserId = expectedOwnerUserId ?: currentOwnerUserId(null) ?: return null
        val tournament = tournamentRepository.observeByIdAndOwner(tournamentId, ownerUserId).first()
            ?: return null
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            if (includeStandard && tournament.format == TournamentFormat.STANDARD) {
                val match = tournamentRepository.observeMatchesByTournamentIdAndOwner(tournamentId, ownerUserId)
                    .first()
                    .firstOrNull { it.id == matchId }
                    ?: return null
                return MatchCloudContext.Standard(match)
            }
            return MatchCloudContext.NotRequired
        }
        val match = tournamentRepository.observeMatchesByTournamentIdAndOwner(tournamentId, ownerUserId)
            .first()
            .firstOrNull { it.id == matchId }
            ?: return null
        return when (matchIdentityRepository.readIdentityMode(tournament, match)) {
            GroupRotationMatchIdentityMode.Legacy -> MatchCloudContext.NotRequired
            GroupRotationMatchIdentityMode.UuidBacked -> MatchCloudContext.UuidBacked(match)
            GroupRotationMatchIdentityMode.Invalid -> null
        }
    }

    private suspend fun currentOwnerUserId(expectedOwnerUserId: String?): String? {
        if (!config.isConfigured) return null
        sessionProbe.awaitInitialization()
        if (!sessionProbe.currentReadiness().allowsAuthenticatedState()) return null
        val ownerUserId = clientProvider.client.auth.currentSessionOrNull()?.user?.id
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return if (expectedOwnerUserId == null || expectedOwnerUserId == ownerUserId) ownerUserId else null
    }

    private suspend fun ensureStandardParent(
        match: Match,
        ownerUserId: String,
    ): GroupRotationCloudMatchProvisionResult {
        val expected = StandardMatchParentCloudExpectation(
            id = MatchCloudIdentity.matchId(match.tournamentId, match.id)
                ?: return GroupRotationCloudMatchProvisionResult.ValidationFailure,
            tournamentId = match.tournamentId,
            matchNumber = match.matchNumber,
            matchDate = match.date.toString(),
            mapName = match.mapName,
        )
        return try {
            val existing = clientProvider.client
                .from(MATCHES_TABLE)
                .select { filter { eq("id", expected.id) } }
                .decodeList<GroupRotationMatchParentCloudRow>()
                .singleOrNull()
            if (existing != null) {
                return validateStandardExisting(existing, expected, ownerUserId)
            }

            if (match.status != MatchStatus.DRAFT) {
                return GroupRotationCloudMatchProvisionResult.ValidationFailure
            }
            when (val syncResult = syncDraftMatchesUseCase.executeForRetry(
                match.tournamentId,
                ownerUserId,
            )) {
                DraftMatchCloudSyncResult.Success -> {
                    val synced = clientProvider.client
                        .from(MATCHES_TABLE)
                        .select { filter { eq("id", expected.id) } }
                        .decodeList<GroupRotationMatchParentCloudRow>()
                        .singleOrNull()
                        ?: return GroupRotationCloudMatchProvisionResult.ValidationFailure
                    validateStandardExisting(synced, expected, ownerUserId)
                }
                DraftMatchCloudSyncResult.AuthenticationRequired ->
                    GroupRotationCloudMatchProvisionResult.AuthenticationRequired
                DraftMatchCloudSyncResult.AuthorizationFailure ->
                    GroupRotationCloudMatchProvisionResult.AuthorizationFailure
                DraftMatchCloudSyncResult.NetworkFailure ->
                    GroupRotationCloudMatchProvisionResult.NetworkFailure
                DraftMatchCloudSyncResult.ValidationFailure ->
                    GroupRotationCloudMatchProvisionResult.ValidationFailure
                is DraftMatchCloudSyncResult.Conflict,
                is DraftMatchCloudSyncResult.PartialFailure,
                -> GroupRotationCloudMatchProvisionResult.ValidationFailure
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            throwable.toProvisionResult()
        }
    }

    private fun validateExisting(
        existing: GroupRotationMatchParentCloudRow,
        expected: GroupRotationMatchParentCloudPayload,
        ownerUserId: String,
    ): GroupRotationCloudMatchProvisionResult {
        if (existing.id != expected.id ||
            existing.tournamentId != expected.tournamentId ||
            existing.groupPairingKey != expected.groupPairingKey
        ) {
            return GroupRotationCloudMatchProvisionResult.ValidationFailure
        }
        return if (ownerUserId.isNotBlank()) {
            GroupRotationCloudMatchProvisionResult.Ensured
        } else {
            GroupRotationCloudMatchProvisionResult.AuthorizationFailure
        }
    }

    private fun validateStandardExisting(
        existing: GroupRotationMatchParentCloudRow,
        expected: StandardMatchParentCloudExpectation,
        ownerUserId: String,
    ): GroupRotationCloudMatchProvisionResult {
        if (existing.id != expected.id ||
            existing.tournamentId != expected.tournamentId ||
            existing.matchNumber != expected.matchNumber ||
            existing.matchDate != expected.matchDate ||
            existing.mapName != expected.mapName ||
            existing.groupPairingKey != null ||
            existing.status !in setOf("draft", "finalized")
        ) {
            return GroupRotationCloudMatchProvisionResult.ValidationFailure
        }
        return if (ownerUserId.isNotBlank()) {
            GroupRotationCloudMatchProvisionResult.Ensured
        } else {
            GroupRotationCloudMatchProvisionResult.AuthorizationFailure
        }
    }

    private sealed interface MatchCloudContext {
        data object NotRequired : MatchCloudContext
        data class Standard(val match: Match) : MatchCloudContext
        data class UuidBacked(val match: Match) : MatchCloudContext
    }

    private companion object {
        const val MATCHES_TABLE = "matches"
        const val TOURNAMENTS_TABLE = "tournaments"
        const val UUID_MATCH_SNAPSHOTS_TABLE = "group_rotation_match_uuid_snapshots"
        const val UUID_MATCH_ASSIGNMENTS_TABLE = "group_rotation_match_uuid_assignments"
        const val UUID_MATCH_RESULTS_TABLE = "group_rotation_match_uuid_results"
    }
}

@Serializable
private data class GroupRotationMatchParentCloudPayload(
    val id: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("match_number") val matchNumber: Int,
    @SerialName("match_date") val matchDate: String,
    @SerialName("map_name") val mapName: String,
    val status: String,
    @SerialName("group_pairing_key") val groupPairingKey: String,
)

private data class StandardMatchParentCloudExpectation(
    val id: String,
    val tournamentId: String,
    val matchNumber: Int,
    val matchDate: String,
    val mapName: String,
)

@Serializable
private data class GroupRotationMatchParentCloudRow(
    val id: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("match_number") val matchNumber: Int,
    @SerialName("match_date") val matchDate: String? = null,
    @SerialName("map_name") val mapName: String? = null,
    val status: String,
    @SerialName("group_pairing_key") val groupPairingKey: String? = null,
    val revision: Int? = null,
    @SerialName("finalized_at") val finalizedAt: String? = null,
    @SerialName("finalized_by") val finalizedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
private data class GroupRotationTournamentRevisionCloudRow(
    val id: String,
    val revision: Int,
)

private fun Throwable.toProvisionResult(): GroupRotationCloudMatchProvisionResult {
    val message = message.orEmpty().lowercase()
    return when {
        message.contains("401") ||
            message.contains("session") ||
            message.contains("jwt") ||
            message.contains("unauthorized") -> GroupRotationCloudMatchProvisionResult.AuthenticationRequired
        message.contains("403") ||
            message.contains("42501") ||
            message.contains("forbidden") ||
            message.contains("row-level security") -> GroupRotationCloudMatchProvisionResult.AuthorizationFailure
        this is IOException ||
            message.contains("network") ||
            message.contains("timeout") ||
            message.contains("connection") -> GroupRotationCloudMatchProvisionResult.NetworkFailure
        else -> GroupRotationCloudMatchProvisionResult.ValidationFailure
    }
}

private fun Throwable.toRemoteStateResult(): GroupRotationCloudMatchRemoteStateResult {
    val message = message.orEmpty().lowercase()
    return when {
        message.contains("401") ||
            message.contains("session") ||
            message.contains("jwt") ||
            message.contains("unauthorized") -> GroupRotationCloudMatchRemoteStateResult.AuthenticationRequired
        message.contains("403") ||
            message.contains("42501") ||
            message.contains("forbidden") ||
            message.contains("row-level security") -> GroupRotationCloudMatchRemoteStateResult.AuthorizationFailure
        this is IOException ||
            message.contains("network") ||
            message.contains("timeout") ||
            message.contains("connection") -> GroupRotationCloudMatchRemoteStateResult.NetworkFailure
        else -> GroupRotationCloudMatchRemoteStateResult.ValidationFailure
    }
}
