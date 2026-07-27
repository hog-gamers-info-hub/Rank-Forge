package com.hoggamers.rankforge.data.tournament

import com.hoggamers.rankforge.data.local.TournamentEntity
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
