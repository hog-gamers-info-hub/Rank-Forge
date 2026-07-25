package com.hoggamers.rankforge.presentation.screen

import java.time.LocalDate
import com.hoggamers.rankforge.domain.tournament.TeamSlot
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
    val slots: List<TeamSlotUiState>,
)

data class TeamSlotUiState(
    val slotNumber: Int,
)

fun Tournament.toDetailsItemUiState(slots: List<TeamSlot>): TournamentDetailsItemUiState = TournamentDetailsItemUiState(
    id = id,
    name = name,
    date = date,
    organizerName = organizerName,
    organizerContactNumber = organizerContactNumber,
    status = status,
    slots = slots.map { TeamSlotUiState(slotNumber = it.slotNumber) },
)
