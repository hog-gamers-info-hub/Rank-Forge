package com.hoggamers.rankforge.presentation.screen

import java.time.LocalDate
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

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
)

fun Tournament.toListItemUiState(): TournamentListItemUiState = TournamentListItemUiState(
    id = id,
    name = name,
    date = date,
    organizerName = organizerName,
    status = status,
)
