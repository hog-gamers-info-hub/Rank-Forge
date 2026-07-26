package com.hoggamers.rankforge.presentation.screen

import java.time.LocalDate
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MAX_MATCHES_PER_TOURNAMENT
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
    val matches: List<MatchUiState> = emptyList(),
)

data class TeamSlotUiState(
    val slotNumber: Int,
    val teamName: String,
)

data class MatchUiState(
    val id: String,
    val matchNumber: Int,
    val date: LocalDate,
    val mapName: String,
    val status: com.hoggamers.rankforge.domain.tournament.MatchStatus,
    val placements: List<MatchPlacementDisplayUiState> = emptyList(),
    val kills: List<MatchKillDisplayUiState> = emptyList(),
)

data class MatchPlacementDisplayUiState(
    val teamSlotNumber: Int,
    val position: Int,
)

data class MatchKillDisplayUiState(
    val teamSlotNumber: Int,
    val kills: Int,
)

fun Tournament.toDetailsItemUiState(
    slots: List<TeamSlot>,
    matches: List<Match> = emptyList(),
): TournamentDetailsItemUiState = TournamentDetailsItemUiState(
    id = id,
    name = name,
    date = date,
    organizerName = organizerName,
    organizerContactNumber = organizerContactNumber,
    status = status,
    slots = slots.map {
        TeamSlotUiState(
            slotNumber = it.slotNumber,
            teamName = it.teamName,
        )
    },
    matches = matches.sortedBy { it.matchNumber }.map { match ->
        MatchUiState(
            id = match.id,
            matchNumber = match.matchNumber,
            date = match.date,
            mapName = match.mapName,
            status = match.status,
            placements = match.placements.toUiState(),
            kills = match.kills.toKillUiState(),
        )
    },
)

private fun List<MatchPlacement>.toUiState(): List<MatchPlacementDisplayUiState> = map { placement ->
    MatchPlacementDisplayUiState(
        teamSlotNumber = placement.teamSlotNumber,
        position = placement.position,
    )
}

private fun List<MatchKill>.toKillUiState(): List<MatchKillDisplayUiState> = map { kill ->
    MatchKillDisplayUiState(
        teamSlotNumber = kill.teamSlotNumber,
        kills = kill.kills,
    )
}

fun TournamentDetailsItemUiState.canCreateMatch(): Boolean =
    status == TournamentStatus.CONFIRMED && matches.size < MAX_MATCHES_PER_TOURNAMENT
