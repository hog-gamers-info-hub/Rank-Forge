package com.hoggamers.rankforge.data.tournament

import com.hoggamers.rankforge.data.local.MatchCorrectionEntity
import com.hoggamers.rankforge.data.local.MatchDraftValueEntity
import com.hoggamers.rankforge.data.local.MatchEntity
import com.hoggamers.rankforge.data.local.MatchKillEntity
import com.hoggamers.rankforge.data.local.MatchParticipantResultEntity
import com.hoggamers.rankforge.data.local.MatchPlacementEntity
import com.hoggamers.rankforge.data.local.MatchResultAggregate
import com.hoggamers.rankforge.data.local.RosterPlayerEntity
import com.hoggamers.rankforge.data.local.TeamSlotEntity
import com.hoggamers.rankforge.data.local.TournamentGroupPairingEntity
import com.hoggamers.rankforge.data.local.TournamentEntity
import com.hoggamers.rankforge.data.local.TournamentSummaryProjection
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
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.TournamentSummary
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal fun Tournament.toEntity(
    creationOrder: Long,
    lastUpdatedEpochMillis: Long? = null,
): TournamentEntity = TournamentEntity(
    id = id,
    name = name,
    stageName = stageName,
    organizerContactNumber = organizerContactNumber,
    status = status.name,
    creationOrder = creationOrder,
    lastUpdatedEpochMillis = lastUpdatedEpochMillis,
    ownerUserId = ownerUserId,
    format = format.name,
    groupCount = groupCount,
)

internal fun TournamentEntity.toDomain(
    selectedGroupPairings: List<GroupPairing> = emptyList(),
): Tournament = Tournament(
    id = id,
    name = name,
    stageName = stageName,
    organizerContactNumber = organizerContactNumber,
    status = TournamentStatus.valueOf(status),
    ownerUserId = ownerUserId,
    format = TournamentFormat.valueOf(format),
    groupCount = groupCount,
    selectedGroupPairings = selectedGroupPairings,
)

internal fun TournamentSummaryProjection.toDomain(): TournamentSummary = TournamentSummary(
    tournament = Tournament(
        id = id,
        name = name,
        stageName = stageName,
        organizerContactNumber = organizerContactNumber,
        status = TournamentStatus.valueOf(status),
        ownerUserId = ownerUserId,
        format = TournamentFormat.valueOf(format),
        groupCount = groupCount,
    ),
    totalTeams = totalTeams,
    totalMatches = totalMatches,
    lastUpdatedEpochMillis = lastUpdatedEpochMillis,
)

internal fun TeamSlot.toEntity(): TeamSlotEntity = TeamSlotEntity(
    tournamentId = tournamentId,
    slotNumber = slotNumber,
    teamName = teamName,
    group = group?.name,
)

internal fun TeamSlotEntity.toDomain(): TeamSlot = TeamSlot(
    tournamentId = tournamentId,
    slotNumber = slotNumber,
    teamName = teamName,
    group = group?.let(TournamentGroup::valueOf),
)

internal fun GroupPairing.toEntity(tournamentId: String): TournamentGroupPairingEntity =
    TournamentGroupPairingEntity(
        tournamentId = tournamentId,
        pairingKey = canonicalKey,
        firstGroup = firstGroup.name,
        secondGroup = secondGroup.name,
    )

internal fun TournamentGroupPairingEntity.toDomain(): GroupPairing =
    GroupPairing.fromCanonicalKey(pairingKey)

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
    groupPairingKey = groupPairing?.canonicalKey,
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
    participantResults = participantResults,
    groupPairing = groupPairingKey?.let(GroupPairing::fromCanonicalKey),
)

internal sealed interface MatchResultAggregateMapping {
    data class Complete(val match: Match) : MatchResultAggregateMapping

    data object Incomplete : MatchResultAggregateMapping

    data object Invalid : MatchResultAggregateMapping
}

internal fun MatchResultAggregate.toDomainResult(json: Json): MatchResultAggregateMapping {
    val status = runCatching { MatchStatus.valueOf(match.status) }
        .getOrElse { return MatchResultAggregateMapping.Invalid }
    val mapped = runCatching {
        val placements = placements.map { it.toDomain() }
        val kills = kills.map { it.toDomain() }
        val participantResults = participantResults.map { it.toDomain() }
        val correctionHistory = corrections.map { it.toDomain(json) }
        val finalizedParticipantResults = if (status == MatchStatus.FINALIZED) {
            when (validateFinalizedAggregate(placements, kills, participantResults)) {
                FinalizedAggregateValidation.Incomplete -> {
                    return MatchResultAggregateMapping.Incomplete
                }
                FinalizedAggregateValidation.Invalid -> {
                    return MatchResultAggregateMapping.Invalid
                }
                FinalizedAggregateValidation.Complete -> {
                    participantResults
                }
            }
        } else {
            participantResults
        }
        match.toDomain(
            placements = placements,
            kills = kills,
            correctionHistory = correctionHistory,
            participantResults = finalizedParticipantResults,
        )
    }.getOrElse { return MatchResultAggregateMapping.Invalid }
    return MatchResultAggregateMapping.Complete(mapped)
}

internal suspend fun MatchResultAggregate.toMatchWithConfirmation(
    json: Json,
    reread: suspend () -> MatchResultAggregate?,
): Match? = when (val first = toDomainResult(json)) {
    is MatchResultAggregateMapping.Complete -> first.match
    MatchResultAggregateMapping.Invalid -> null
    MatchResultAggregateMapping.Incomplete -> when (val second = reread()?.toDomainResult(json)) {
        is MatchResultAggregateMapping.Complete -> second.match
        MatchResultAggregateMapping.Incomplete,
        MatchResultAggregateMapping.Invalid,
        null,
        -> null
    }
}

private enum class FinalizedAggregateValidation {
    Complete,
    Incomplete,
    Invalid,
}

private fun validateFinalizedAggregate(
    placements: List<MatchPlacement>,
    kills: List<MatchKill>,
    participantResults: List<MatchParticipantResult>,
): FinalizedAggregateValidation {
    if (placements.isEmpty() || kills.isEmpty()) return FinalizedAggregateValidation.Incomplete

    val placementsBySlot = placements.associateBy { it.teamSlotNumber }
    val killsBySlot = kills.associateBy { it.teamSlotNumber }
    if (placementsBySlot.size != placements.size || killsBySlot.size != kills.size) {
        return FinalizedAggregateValidation.Invalid
    }

    if (participantResults.isEmpty()) return FinalizedAggregateValidation.Incomplete

    val participated = participantResults.filter {
        it.participationStatus == MatchParticipationStatus.PARTICIPATED
    }
    val participatedSlots = participated.map { it.teamSlotNumber }.toSet()
    if (participated.isEmpty()) return FinalizedAggregateValidation.Invalid

    if (participated.any { result ->
            val placement = placementsBySlot[result.teamSlotNumber]
            val kill = killsBySlot[result.teamSlotNumber]
            placement == null || kill == null
        }
    ) {
        return FinalizedAggregateValidation.Incomplete
    }
    if (placementsBySlot.keys != participatedSlots || killsBySlot.keys != participatedSlots) {
        return FinalizedAggregateValidation.Incomplete
    }
    if (participated.any { result ->
            placementsBySlot[result.teamSlotNumber]?.position != result.placement ||
                killsBySlot[result.teamSlotNumber]?.kills != result.kills
        }
    ) {
        return FinalizedAggregateValidation.Incomplete
    }
    if (participantResults.any {
            it.participationStatus == MatchParticipationStatus.NO_SHOW &&
                (it.placement != null || it.kills != 0)
        }
    ) {
        return FinalizedAggregateValidation.Invalid
    }
    return FinalizedAggregateValidation.Complete
}

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
        pointAdjustment = pointAdjustment,
    )

internal fun MatchParticipantResultEntity.toDomain(): MatchParticipantResult = MatchParticipantResult(
    teamSlotNumber = teamSlotNumber,
    participationStatus = MatchParticipationStatus.valueOf(participationStatus),
    placement = placement,
    kills = kills,
    pointAdjustment = pointAdjustment,
)

internal fun MatchDraftFieldValues.toEntity(
    matchId: String,
    teamSlotNumber: Int,
): MatchDraftValueEntity = MatchDraftValueEntity(
    matchId = matchId,
    teamSlotNumber = teamSlotNumber,
    placementInput = placementInput,
    killsInput = killsInput,
    pointAdjustment = pointAdjustment,
)

internal fun MatchDraftValueEntity.toDomain(): MatchDraftFieldValues = MatchDraftFieldValues(
    placementInput = placementInput,
    killsInput = killsInput,
    pointAdjustment = pointAdjustment,
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
    val pointAdjustment: Int = 0,
)

private fun MatchPlacement.toStored(): StoredPlacement = StoredPlacement(teamSlotNumber, position)

private fun MatchKill.toStored(): StoredKill = StoredKill(teamSlotNumber, kills)

private fun MatchParticipantResult.toStored(): StoredParticipantResult = StoredParticipantResult(
    teamSlotNumber = teamSlotNumber,
    participationStatus = participationStatus.name,
    placement = placement,
    kills = kills,
    pointAdjustment = pointAdjustment,
)

private fun StoredPlacement.toDomain(): MatchPlacement = MatchPlacement(teamSlotNumber, position)

private fun StoredKill.toDomain(): MatchKill = MatchKill(teamSlotNumber, kills)

private fun StoredParticipantResult.toDomain(): MatchParticipantResult = MatchParticipantResult(
    teamSlotNumber = teamSlotNumber,
    participationStatus = MatchParticipationStatus.valueOf(participationStatus),
    placement = placement,
    kills = kills,
    pointAdjustment = pointAdjustment,
)

private fun Json.decodeCorrectionSnapshotOrNull(raw: String): StoredCorrectionSnapshot? =
    runCatching { decodeFromString<StoredCorrectionSnapshot>(raw) }.getOrNull()
