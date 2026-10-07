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
import com.hoggamers.rankforge.domain.tournament.nextAvailableMatchNumber
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.GroupPairing

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

enum class TournamentDetailsNavigation {
    TOURNAMENT_LIST,
}

enum class TournamentDeletionUiError {
    TARGET_NOT_FOUND,
    AUTHENTICATION_REQUIRED,
    AUTHORIZATION_FAILURE,
    VALIDATION_FAILURE,
    STORAGE_FAILURE,
    REMOTE_FAILURE,
    LOCAL_CLEANUP_FAILURE,
    PREPARATION_FAILURE,
    UNKNOWN,
}

data class MatchReviewRequest(
    val tournamentId: String,
    val matchId: String,
)

data class TournamentDetailsUiState(
    val isLoading: Boolean = true,
    val tournament: TournamentDetailsItemUiState? = null,
    val csvExportResult: AndroidExportResult? = null,
    val pendingTeamCountConfirmation: TeamCountConfirmationUiState? = null,
    val calculatePointsMessage: CalculatePointsMessage? = null,
    val matchReviewRequest: MatchReviewRequest? = null,
    val isCreatingMatch: Boolean = false,
    val navigation: TournamentDetailsNavigation? = null,
    val isDeleting: Boolean = false,
    val deletionError: TournamentDeletionUiError? = null,
    val pendingGroupPairing: GroupPairing? = null,
) {
    val isNotFound: Boolean
        get() = !isLoading && tournament == null
}

data class TournamentDetailsItemUiState(
    val id: String,
    val name: String,
    val stageName: String,
    val organizerContactNumber: String,
    val status: TournamentStatus,
    val slots: List<TeamSlotUiState>,
    val matches: List<MatchUiState> = emptyList(),
    val hasInvalidTeamSlotState: Boolean = false,
    val format: TournamentFormat = TournamentFormat.STANDARD,
    val selectedGroupPairings: List<GroupPairing> = emptyList(),
)

data class TournamentMatchSectionUiState(
    val pairing: GroupPairing,
    val matches: List<MatchUiState>,
)

val TournamentDetailsItemUiState.groupMatchSections: List<TournamentMatchSectionUiState>
    get() = selectedGroupPairings.map { pairing ->
        TournamentMatchSectionUiState(
            pairing = pairing,
            matches = matches.filter { it.groupPairing == pairing }
                .sortedWith(compareBy<MatchUiState> { it.matchNumber }.thenBy { it.id }),
        )
    }

val TournamentDetailsItemUiState.nextMatchNumber: Int?
    get() = nextAvailableMatchNumber(matches.map { it.matchNumber })

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
    val groupPairing: GroupPairing? = null,
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
    val visibleSlots = if (format == TournamentFormat.GROUP_ROTATION) {
        slots.filter { it.teamName.trim().isNotBlank() }
    } else {
        slots.filter { it.slotNumber in participation.activeSlotNumbers }
    }
    return TournamentDetailsItemUiState(
        id = id,
        name = name,
        stageName = stageName,
        organizerContactNumber = organizerContactNumber,
        status = status,
        format = format,
        selectedGroupPairings = selectedGroupPairings,
        slots = visibleSlots
            .map {
        TeamSlotUiState(
            slotNumber = it.slotNumber,
            teamName = it.teamName,
        )
            },
        matches = matches.sortedWith(compareBy<Match> { it.matchNumber }.thenBy { it.id }).map { match ->
        MatchUiState(
            id = match.id,
            matchNumber = match.matchNumber,
            date = match.date,
            mapName = match.mapName,
            status = match.status,
            groupPairing = match.groupPairing,
            placements = match.placements.toUiState(),
            kills = match.kills.toKillUiState(),
            validationIssues = ValidateMatchResultUseCase()(match, runCatching {
                com.hoggamers.rankforge.domain.tournament.MatchEligibleTeamSlotResolver().resolve(
                    this@toDetailsItemUiState,
                    slots,
                    match,
                )
                }.getOrElse {
                    if (format == com.hoggamers.rankforge.domain.tournament.TournamentFormat.GROUP_ROTATION) {
                        emptySet()
                    } else {
                        TeamSlot.SLOT_NUMBERS.toSet()
                    }
                })
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
    matches.size < MAX_MATCHES_PER_TOURNAMENT && nextMatchNumber != null

fun TournamentDetailsItemUiState.activeTeamSlotCount(): Int = slots.count { it.teamName.trim().isNotBlank() }
