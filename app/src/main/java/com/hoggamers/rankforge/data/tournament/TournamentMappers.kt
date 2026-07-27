package com.hoggamers.rankforge.data.tournament

import com.hoggamers.rankforge.data.local.TournamentEntity
import com.hoggamers.rankforge.data.local.RosterPlayerEntity
import com.hoggamers.rankforge.data.local.TeamSlotEntity
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate

internal fun Tournament.toEntity(): TournamentEntity = TournamentEntity(
    id = id,
    name = name,
    date = date.toString(),
    organizerName = organizerName,
    organizerContactNumber = organizerContactNumber,
    status = status.name,
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
