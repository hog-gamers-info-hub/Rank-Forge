package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRemoteResult
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TournamentCloudRestorePayload(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    @SerialName("tournament_date") val tournamentDate: String,
    @SerialName("organizer_name") val organizerName: String,
    @SerialName("organizer_contact") val organizerContact: String,
    val status: String,
    val revision: Int,
)

@Serializable
data class TeamSlotCloudRestorePayload(
    val id: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("slot_number") val slotNumber: Int,
    @SerialName("team_name") val teamName: String,
    val status: String,
)

@Serializable
data class PlayerCloudRestorePayload(
    val id: String,
    @SerialName("team_slot_id") val teamSlotId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("normalized_name") val normalizedName: String,
)

interface TournamentCloudRestorationRemoteDataSource {
    suspend fun listOwnedTournaments(): TournamentCloudRestorationRemoteResult<
        List<TournamentCloudRestorePayload>
        >

    suspend fun readOwnedTournament(
        tournamentId: String,
    ): TournamentCloudRestorationRemoteResult<TournamentCloudRestorationPayloads>
}

@Singleton
class SupabaseTournamentCloudRestorationRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
) : TournamentCloudRestorationRemoteDataSource {
    override suspend fun listOwnedTournaments(): TournamentCloudRestorationRemoteResult<
        List<TournamentCloudRestorePayload>
        > {
        val accessFailure = accessFailure() ?: return try {
            val payloads = clientProvider.client
                .from("tournaments")
                .select()
                .decodeList<TournamentCloudRestorePayload>()
            TournamentCloudRestorationRemoteResult.Success(payloads)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            TournamentCloudRestorationRemoteResult.Failure(throwable.toFailureCategory())
        }
        return accessFailure
    }

    override suspend fun readOwnedTournament(
        tournamentId: String,
    ): TournamentCloudRestorationRemoteResult<TournamentCloudRestorationPayloads> {
        val accessFailure = accessFailure() ?: return try {
            val client = clientProvider.client
            val tournaments = client
                .from("tournaments")
                .select {
                    filter { eq("id", tournamentId) }
                }
                .decodeList<TournamentCloudRestorePayload>()
            val tournament = tournaments.singleOrNull()
                ?: return TournamentCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.AUTHORIZATION,
                )
            val slots = client
                .from("tournament_team_slots")
                .select {
                    filter { eq("tournament_id", tournamentId) }
                }
                .decodeList<TeamSlotCloudRestorePayload>()
            val players = slots.flatMap { slot ->
                client
                    .from("players")
                    .select {
                        filter { eq("team_slot_id", slot.id) }
                    }
                    .decodeList<PlayerCloudRestorePayload>()
            }
            TournamentCloudRestorationRemoteResult.Success(
                TournamentCloudRestorationPayloads(
                    tournament = tournament.toUploadPayload(),
                    teamSlots = slots.map { it.toUploadPayload() },
                    players = players.map { it.toUploadPayload() },
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            TournamentCloudRestorationRemoteResult.Failure(throwable.toFailureCategory())
        }
        return accessFailure
    }

    private fun accessFailure(): TournamentCloudRestorationRemoteResult.Failure? {
        if (!config.isConfigured) {
            return TournamentCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.VALIDATION,
            )
        }
        if (clientProvider.client.auth.currentSessionOrNull() == null) {
            return TournamentCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.AUTHENTICATION,
            )
        }
        return null
    }
}

private fun TournamentCloudRestorePayload.toUploadPayload() = TournamentUploadPayload(
    id = id,
    ownerId = ownerId,
    name = name,
    tournamentDate = tournamentDate,
    organizerName = organizerName,
    organizerContact = organizerContact,
    status = status,
    revision = revision,
)

private fun TeamSlotCloudRestorePayload.toUploadPayload() = TeamSlotUploadPayload(
    id = id,
    tournamentId = tournamentId,
    slotNumber = slotNumber,
    teamName = teamName,
    status = status,
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
