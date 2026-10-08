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
    @SerialName("organizer_name") val stageName: String,
    @SerialName("organizer_contact") val organizerContact: String,
    val status: String,
    val revision: Int,
    val format: String = "standard",
    @SerialName("group_count") val groupCount: Int? = null,
    @SerialName("selected_group_pairings") val selectedGroupPairings: List<GroupPairingUploadPayload> = emptyList(),
)

@Serializable
data class TeamSlotCloudRestorePayload(
    val id: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("slot_number") val slotNumber: Int,
    @SerialName("team_name") val teamName: String,
    val status: String,
    @SerialName("group") val group: String? = null,
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

interface TournamentCloudRestorationRemoteReader {
    suspend fun readTournament(tournamentId: String): TournamentCloudRestorePayload?

    suspend fun readTeamSlots(tournamentId: String): List<TeamSlotCloudRestorePayload>

    suspend fun readPlayers(teamSlotId: String): List<PlayerCloudRestorePayload>

    suspend fun readPairings(tournamentId: String): List<GroupPairingUploadPayload>

    suspend fun readPairingLobbySlots(tournamentId: String): List<GroupPairingLobbySlotUploadPayload>
}

@Singleton
class SupabaseTournamentCloudRestorationRemoteReader @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
) : TournamentCloudRestorationRemoteReader {
    override suspend fun readTournament(tournamentId: String): TournamentCloudRestorePayload? =
        clientProvider.client
            .from("tournaments")
            .select {
                filter { eq("id", tournamentId) }
            }
            .decodeList<TournamentCloudRestorePayload>()
            .singleOrNull()

    override suspend fun readTeamSlots(tournamentId: String): List<TeamSlotCloudRestorePayload> =
        clientProvider.client
            .from("tournament_team_slots")
            .select {
                filter { eq("tournament_id", tournamentId) }
            }
            .decodeList()

    override suspend fun readPlayers(teamSlotId: String): List<PlayerCloudRestorePayload> =
        clientProvider.client
            .from("players")
            .select {
                filter { eq("team_slot_id", teamSlotId) }
            }
            .decodeList()

    override suspend fun readPairings(tournamentId: String): List<GroupPairingUploadPayload> =
        clientProvider.client
            .from("tournament_group_pairings")
            .select {
                filter { eq("tournament_id", tournamentId) }
            }
            .decodeList()

    override suspend fun readPairingLobbySlots(
        tournamentId: String,
    ): List<GroupPairingLobbySlotUploadPayload> =
        clientProvider.client
            .from("tournament_group_pairing_lobby_slots")
            .select {
                filter { eq("tournament_id", tournamentId) }
            }
            .decodeList<GroupPairingLobbySlotUploadPayload>()
            .sortedWith(compareBy({ it.pairingKey }, { it.lobbySlotNumber }))
}

@Singleton
class SupabaseTournamentCloudRestorationRemoteDataSource @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
    private val reader: TournamentCloudRestorationRemoteReader,
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
            readRevisionFencedSnapshot(tournamentId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            TournamentCloudRestorationRemoteResult.Failure(throwable.toFailureCategory())
        }
        return accessFailure
    }

    private suspend fun readRevisionFencedSnapshot(
        tournamentId: String,
    ): TournamentCloudRestorationRemoteResult<TournamentCloudRestorationPayloads> {
        for (attempt in 0 until MAX_REVISION_READ_ATTEMPTS) {
            val firstParent = reader.readTournament(tournamentId)
                ?: return TournamentCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.NOT_FOUND,
                )
            val firstFence = firstParent.toRevisionFence(tournamentId)
                ?: return TournamentCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.VALIDATION,
                )

            val slots = reader.readTeamSlots(tournamentId)
            val players = slots
                .sortedBy { it.slotNumber }
                .flatMap { slot -> reader.readPlayers(slot.id) }
            val pairings = reader.readPairings(tournamentId)
            val pairingLobbySlots = reader.readPairingLobbySlots(tournamentId)

            val secondParent = reader.readTournament(tournamentId)
                ?: return TournamentCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.NOT_FOUND,
                )
            val secondFence = secondParent.toRevisionFence(tournamentId)
                ?: return TournamentCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.VALIDATION,
                )

            if (firstFence == secondFence) {
                return TournamentCloudRestorationRemoteResult.Success(
                    TournamentCloudRestorationPayloads(
                        tournament = firstParent.toUploadPayload(pairings),
                        teamSlots = slots.map { it.toUploadPayload() },
                        players = players.map { it.toUploadPayload() },
                        pairingLobbySlots = pairingLobbySlots
                            .sortedWith(compareBy({ it.pairingKey }, { it.lobbySlotNumber })),
                    ),
                )
            }

            if (attempt == MAX_REVISION_READ_ATTEMPTS - 1) {
                return TournamentCloudRestorationRemoteResult.Failure(
                    TournamentCloudRestorationFailureCategory.NETWORK,
                )
            }
        }
        error("Revision-fenced restoration read did not complete.")
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

private data class TournamentRevisionFence(
    val tournamentId: String,
    val ownerId: String,
    val revision: Int,
)

private fun TournamentCloudRestorePayload.toRevisionFence(
    requestedTournamentId: String,
): TournamentRevisionFence? =
    takeIf {
        id == requestedTournamentId &&
            ownerId.isNotBlank() &&
            revision > 0
    }?.let {
        TournamentRevisionFence(
            tournamentId = id,
            ownerId = ownerId,
            revision = revision,
        )
    }

private const val MAX_REVISION_READ_ATTEMPTS = 2

private fun TournamentCloudRestorePayload.toUploadPayload(
    pairings: List<GroupPairingUploadPayload> = selectedGroupPairings,
) = TournamentUploadPayload(
    id = id,
    ownerId = ownerId,
    name = name,
    stageName = stageName,
    organizerContact = organizerContact,
    status = status,
    revision = revision,
    format = format,
    groupCount = groupCount,
    selectedGroupPairings = pairings,
)

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
