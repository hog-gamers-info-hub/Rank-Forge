package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.TeamSlot

data class TeamEntryUiState(
    val isLoading: Boolean = true,
    val slots: List<TeamEntrySlotUiState> = emptyList(),
) {
    val isNotFound: Boolean
        get() = !isLoading && slots.isEmpty()
}

data class TeamEntrySlotUiState(
    val slotNumber: Int,
    val teamName: String,
)

fun List<TeamSlot>.toTeamEntrySlotUiState(): List<TeamEntrySlotUiState> =
    sortedBy { it.slotNumber }
        .map { slot ->
            TeamEntrySlotUiState(
                slotNumber = slot.slotNumber,
                teamName = slot.teamName,
            )
        }
