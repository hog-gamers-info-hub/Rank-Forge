package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionProbe
import com.hoggamers.rankforge.data.auth.allowsAuthenticatedState
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentityMode
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentityPersistenceRepository
import com.hoggamers.rankforge.domain.tournament.Match
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
) : GroupRotationCloudMatchProvisioner {
    override suspend fun ensureParent(
        tournamentId: String,
        matchId: String,
        expectedOwnerUserId: String?,
    ): GroupRotationCloudMatchProvisionResult {
        val context = readUuidBackedMatch(tournamentId, matchId, expectedOwnerUserId)
            ?: return GroupRotationCloudMatchProvisionResult.ValidationFailure
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
        }
    }

    private suspend fun readUuidBackedMatch(
        tournamentId: String,
        matchId: String,
        expectedOwnerUserId: String?,
    ): MatchCloudContext? {
        val ownerUserId = expectedOwnerUserId ?: currentOwnerUserId(null) ?: return null
        val tournament = tournamentRepository.observeByIdAndOwner(tournamentId, ownerUserId).first()
            ?: return null
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
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

    private sealed interface MatchCloudContext {
        data object NotRequired : MatchCloudContext
        data class UuidBacked(val match: Match) : MatchCloudContext
    }

    private companion object {
        const val MATCHES_TABLE = "matches"
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
