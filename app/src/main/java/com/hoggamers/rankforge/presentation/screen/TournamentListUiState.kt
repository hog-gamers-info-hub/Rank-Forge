package com.hoggamers.rankforge.presentation.screen

import java.time.LocalDate
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.TournamentSummary

data class TournamentListUiState(
    val tournaments: List<TournamentListItemUiState> = emptyList(),
) {
    val isEmpty: Boolean
        get() = tournaments.isEmpty()
}

data class TournamentListItemUiState(
    val id: String,
    val name: String,
    val date: LocalDate,
    val organizerName: String,
    val status: TournamentStatus,
    val totalTeams: Int = 0,
    val totalMatches: Int = 0,
    val lastUpdatedEpochMillis: Long? = null,
)

fun Tournament.toListItemUiState(): TournamentListItemUiState = TournamentListItemUiState(
    id = id,
    name = name,
    date = date,
    organizerName = organizerName,
    status = status,
    totalTeams = 0,
    totalMatches = 0,
    lastUpdatedEpochMillis = null,
)

fun TournamentSummary.toListItemUiState(): TournamentListItemUiState = TournamentListItemUiState(
    id = tournament.id,
    name = tournament.name,
    date = tournament.date,
    organizerName = tournament.organizerName,
    status = tournament.status,
    totalTeams = totalTeams,
    totalMatches = totalMatches,
    lastUpdatedEpochMillis = lastUpdatedEpochMillis,
)
