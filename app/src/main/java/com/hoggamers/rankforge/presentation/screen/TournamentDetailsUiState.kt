package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.export.AndroidExportResult
import java.time.LocalDate
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.analyzeTeamSlotParticipation
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import com.hoggamers.rankforge.domain.tournament.MAX_MATCHES_PER_TOURNAMENT
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

data class TeamCountConfirmationUiState(
    val enteredCount: Int,
    val emptyCount: Int,
)

enum class CalculatePointsMessage {
    NO_TEAMS_SAVED,
    INVALID_TEAM_SLOTS,
    VALIDATION_FAILED,
    MATCH_CREATION_FAILED,
}

data class MatchPlacementRequest(
    val tournamentId: String,
    val matchId: String,
)

data class TournamentDetailsUiState(
    val isLoading: Boolean = true,
    val tournament: TournamentDetailsItemUiState? = null,
    val csvExportResult: AndroidExportResult? = null,
    val googleSheetsExportResult: AndroidExportResult? = null,
    val pendingTeamCountConfirmation: TeamCountConfirmationUiState? = null,
    val calculatePointsMessage: CalculatePointsMessage? = null,
    val matchPlacementRequest: MatchPlacementRequest? = null,
    val isCreatingMatch: Boolean = false,
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
    val hasInvalidTeamSlotState: Boolean = false,
)

val TournamentDetailsItemUiState.canPrepareStandingsCsvExport: Boolean
    get() = matches.any { match ->
        match.status == com.hoggamers.rankforge.domain.tournament.MatchStatus.FINALIZED &&
            match.validationIssues.isEmpty()
    }

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
    val validationIssues: List<MatchResultValidationIssueUiState> = emptyList(),
)

data class MatchPlacementDisplayUiState(
    val teamSlotNumber: Int,
    val position: Int,
)

data class MatchKillDisplayUiState(
    val teamSlotNumber: Int,
    val kills: Int,
)

data class MatchResultValidationIssueUiState(
    val teamSlotNumber: Int,
    val error: MatchResultValidationError,
)

fun Tournament.toDetailsItemUiState(
    slots: List<TeamSlot>,
    matches: List<Match> = emptyList(),
): TournamentDetailsItemUiState {
    val participation = slots.analyzeTeamSlotParticipation()
    return TournamentDetailsItemUiState(
        id = id,
        name = name,
        date = date,
        organizerName = organizerName,
        organizerContactNumber = organizerContactNumber,
        status = status,
        slots = slots
            .filter { it.slotNumber in participation.activeSlotNumbers }
            .map {
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
            validationIssues = ValidateMatchResultUseCase()(match)
                .errorsByTeamSlot
                .toSortedMap()
                .flatMap { (teamSlotNumber, errors) ->
                    errors.sortedBy { it.ordinal }.map { error ->
                        MatchResultValidationIssueUiState(teamSlotNumber, error)
                    }
                },
        )
        },
        hasInvalidTeamSlotState = participation.hasGap,
    )
}

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
    matches.size < MAX_MATCHES_PER_TOURNAMENT

fun TournamentDetailsItemUiState.activeTeamSlotCount(): Int = slots.count { it.teamName.trim().isNotBlank() }
