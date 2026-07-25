package com.hoggamers.rankforge.presentation.screen

import java.time.LocalDate
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

data class TournamentDetailsUiState(
    val isLoading: Boolean = true,
    val tournament: TournamentDetailsItemUiState? = null,
) {
    val isNotFound: Boolean
        get() = !isLoading && tournament == null
}

data class TournamentDetailsItemUiState(
    val id: String,
    val name: String,
    val date: LocalDate,
    val organizerName: String,
    val organizerContactNumber: String,
    val status: TournamentStatus,
)

fun Tournament.toDetailsItemUiState(): TournamentDetailsItemUiState = TournamentDetailsItemUiState(
    id = id,
    name = name,
    date = date,
    organizerName = organizerName,
    organizerContactNumber = organizerContactNumber,
    status = status,
)
