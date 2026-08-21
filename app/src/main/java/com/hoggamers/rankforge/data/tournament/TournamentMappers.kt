package com.hoggamers.rankforge.data.tournament

import com.hoggamers.rankforge.data.local.MatchCorrectionEntity
import com.hoggamers.rankforge.data.local.MatchDraftValueEntity
import com.hoggamers.rankforge.data.local.MatchEntity
import com.hoggamers.rankforge.data.local.MatchKillEntity
import com.hoggamers.rankforge.data.local.MatchParticipantResultEntity
import com.hoggamers.rankforge.data.local.MatchPlacementEntity
import com.hoggamers.rankforge.data.local.RosterPlayerEntity
import com.hoggamers.rankforge.data.local.TeamSlotEntity
import com.hoggamers.rankforge.data.local.TournamentEntity
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionRecord
import com.hoggamers.rankforge.domain.tournament.MatchDraftFieldValues
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.RestoredRosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal fun Tournament.toEntity(creationOrder: Long): TournamentEntity = TournamentEntity(
    id = id,
    name = name,
    date = date.toString(),
    organizerName = organizerName,
    organizerContactNumber = organizerContactNumber,
    status = status.name,
    creationOrder = creationOrder,
)

internal fun TournamentEntity.toDomain(): Tournament = Tournament(
    id = id,
    name = name,
    date = LocalDate.parse(date),
    organizerName = organizerName,
    organizerContactNumber = organizerContactNumber,
    status = TournamentStatus.valueOf(status),
)

internal fun TeamSlot.toEntity(): TeamSlotEntity = TeamSlotEntity(
    tournamentId = tournamentId,
    slotNumber = slotNumber,
    teamName = teamName,
)

internal fun TeamSlotEntity.toDomain(): TeamSlot = TeamSlot(
    tournamentId = tournamentId,
    slotNumber = slotNumber,
    teamName = teamName,
)

internal fun RosterPlayer.toEntity(rosterPosition: Int): RosterPlayerEntity = RosterPlayerEntity(
    tournamentId = tournamentId,
    slotNumber = slotNumber,
    rosterPosition = rosterPosition,
    displayName = displayName,
)

internal fun RosterPlayerEntity.toDomain(): RosterPlayer = RosterPlayer(
    tournamentId = tournamentId,
    slotNumber = slotNumber,
    displayName = displayName,
)

internal fun List<RosterPlayer>.toEntities(): List<RosterPlayerEntity> = mapIndexed { index, player ->
    player.toEntity(rosterPosition = index + 1)
}

internal fun RestoredRosterPlayer.toEntity(): RosterPlayerEntity = RosterPlayerEntity(
    tournamentId = tournamentId,
    slotNumber = slotNumber,
    rosterPosition = rosterPosition,
    displayName = displayName,
)

internal fun Match.toEntity(): MatchEntity = MatchEntity(
    id = id,
    tournamentId = tournamentId,
    matchNumber = matchNumber,
    date = date.toString(),
    mapName = mapName,
    status = status.name,
)

internal fun MatchEntity.toDomain(
    placements: List<MatchPlacement> = emptyList(),
    kills: List<MatchKill> = emptyList(),
    correctionHistory: List<MatchCorrectionRecord> = emptyList(),
    participantResults: List<MatchParticipantResult> = emptyList(),
): Match = Match(
    id = id,
    tournamentId = tournamentId,
    matchNumber = matchNumber,
    date = LocalDate.parse(date),
    mapName = mapName,
    status = MatchStatus.valueOf(status),
    placements = placements,
    kills = kills,
    correctionHistory = correctionHistory,
    participantResults = participantResults.ifEmpty {
        if (MatchStatus.valueOf(status) == MatchStatus.FINALIZED) {
            val killsBySlot = kills.associateBy { it.teamSlotNumber }
            placements.map { placement ->
                MatchParticipantResult(
                    teamSlotNumber = placement.teamSlotNumber,
                    participationStatus = MatchParticipationStatus.PARTICIPATED,
                    placement = placement.position,
                    kills = killsBySlot.getValue(placement.teamSlotNumber).kills,
                )
            }
        } else {
            emptyList()
        }
    },
)

internal fun MatchPlacement.toEntity(matchId: String): MatchPlacementEntity = MatchPlacementEntity(
    matchId = matchId,
    teamSlotNumber = teamSlotNumber,
    position = position,
)

internal fun MatchPlacementEntity.toDomain(): MatchPlacement = MatchPlacement(
    teamSlotNumber = teamSlotNumber,
    position = position,
)

internal fun MatchKill.toEntity(matchId: String): MatchKillEntity = MatchKillEntity(
    matchId = matchId,
    teamSlotNumber = teamSlotNumber,
    kills = kills,
)

internal fun MatchKillEntity.toDomain(): MatchKill = MatchKill(
    teamSlotNumber = teamSlotNumber,
    kills = kills,
)

internal fun MatchParticipantResult.toEntity(matchId: String): MatchParticipantResultEntity =
    MatchParticipantResultEntity(
        matchId = matchId,
        teamSlotNumber = teamSlotNumber,
        participationStatus = participationStatus.name,
        placement = placement,
        kills = kills,
    )

internal fun MatchParticipantResultEntity.toDomain(): MatchParticipantResult = MatchParticipantResult(
    teamSlotNumber = teamSlotNumber,
    participationStatus = MatchParticipationStatus.valueOf(participationStatus),
    placement = placement,
    kills = kills,
)

internal fun MatchDraftFieldValues.toEntity(
    matchId: String,
    teamSlotNumber: Int,
): MatchDraftValueEntity = MatchDraftValueEntity(
    matchId = matchId,
    teamSlotNumber = teamSlotNumber,
    placementInput = placementInput,
    killsInput = killsInput,
)

internal fun MatchDraftValueEntity.toDomain(): MatchDraftFieldValues = MatchDraftFieldValues(
    placementInput = placementInput,
    killsInput = killsInput,
)

internal fun MatchCorrectionRecord.toEntity(
    matchId: String,
    correctionIndex: Int,
    json: Json,
): MatchCorrectionEntity = MatchCorrectionEntity(
    matchId = matchId,
    correctionIndex = correctionIndex,
    previousPlacements = if (previousParticipantResults.isEmpty()) {
        json.encodeToString(previousPlacements.map { it.toStored() })
    } else {
        json.encodeToString(
            StoredCorrectionSnapshot(
                placements = previousPlacements.map { it.toStored() },
                participantResults = previousParticipantResults.map { it.toStored() },
            )
        )
    },
    previousKills = json.encodeToString(previousKills.map { it.toStored() }),
    correctedPlacements = if (correctedParticipantResults.isEmpty()) {
        json.encodeToString(correctedPlacements.map { it.toStored() })
    } else {
        json.encodeToString(
            StoredCorrectionSnapshot(
                placements = correctedPlacements.map { it.toStored() },
                participantResults = correctedParticipantResults.map { it.toStored() },
            )
        )
    },
    correctedKills = json.encodeToString(correctedKills.map { it.toStored() }),
)

internal fun MatchCorrectionEntity.toDomain(json: Json): MatchCorrectionRecord {
    val previousSnapshot = json.decodeCorrectionSnapshotOrNull(previousPlacements)
    val correctedSnapshot = json.decodeCorrectionSnapshotOrNull(correctedPlacements)
    return MatchCorrectionRecord(
    previousPlacements = previousSnapshot?.placements?.map { it.toDomain() }
        ?: json.decodeFromString<List<StoredPlacement>>(previousPlacements).map { it.toDomain() },
    previousKills = json.decodeFromString<List<StoredKill>>(previousKills).map { it.toDomain() },
    correctedPlacements = correctedSnapshot?.placements?.map { it.toDomain() }
        ?: json.decodeFromString<List<StoredPlacement>>(correctedPlacements).map { it.toDomain() },
    correctedKills = json.decodeFromString<List<StoredKill>>(correctedKills).map { it.toDomain() },
    previousParticipantResults = previousSnapshot?.participantResults?.map { it.toDomain() }.orEmpty(),
    correctedParticipantResults = correctedSnapshot?.participantResults?.map { it.toDomain() }.orEmpty(),
    )
}

@Serializable
private data class StoredPlacement(val teamSlotNumber: Int, val position: Int)

@Serializable
private data class StoredKill(val teamSlotNumber: Int, val kills: Int)

@Serializable
private data class StoredCorrectionSnapshot(
    val placements: List<StoredPlacement>,
    val participantResults: List<StoredParticipantResult>,
)

@Serializable
private data class StoredParticipantResult(
    val teamSlotNumber: Int,
    val participationStatus: String,
    val placement: Int?,
    val kills: Int,
)

private fun MatchPlacement.toStored(): StoredPlacement = StoredPlacement(teamSlotNumber, position)

private fun MatchKill.toStored(): StoredKill = StoredKill(teamSlotNumber, kills)

private fun MatchParticipantResult.toStored(): StoredParticipantResult = StoredParticipantResult(
    teamSlotNumber = teamSlotNumber,
    participationStatus = participationStatus.name,
    placement = placement,
    kills = kills,
)

private fun StoredPlacement.toDomain(): MatchPlacement = MatchPlacement(teamSlotNumber, position)

private fun StoredKill.toDomain(): MatchKill = MatchKill(teamSlotNumber, kills)

private fun StoredParticipantResult.toDomain(): MatchParticipantResult = MatchParticipantResult(
    teamSlotNumber = teamSlotNumber,
    participationStatus = MatchParticipationStatus.valueOf(participationStatus),
    placement = placement,
    kills = kills,
)

private fun Json.decodeCorrectionSnapshotOrNull(raw: String): StoredCorrectionSnapshot? =
    runCatching { decodeFromString<StoredCorrectionSnapshot>(raw) }.getOrNull()
