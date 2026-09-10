package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import com.hoggamers.rankforge.domain.tournament.CreateNextMatchFailure
import com.hoggamers.rankforge.domain.tournament.CreateNextMatchResult
import com.hoggamers.rankforge.domain.tournament.CreateNextMatchUseCase
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesResult
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.analyzeTeamSlotParticipation
import com.hoggamers.rankforge.domain.tournament.defaultTeamNameForSlot
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Shared orchestration for creating the next draft match from either creation surface. */
class CreateNextMatchWorkflow @Inject constructor(
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val saveTeamSlotNames: SaveTeamSlotNamesUseCase,
    private val validateTournamentRoster: ValidateTournamentRosterUseCase,
    private val createNextMatch: CreateNextMatchUseCase,
    private val syncDraftMatches: DraftMatchCloudSyncAction,
    private val applyLobbyTemplate: ApplyLobbyTemplateAction,
    private val lobbyUploadCheckpoint: MatchLobbyScreenshotUploadCheckpointAction,
) {
    suspend fun teamCountConfirmationOrNull(
        tournamentId: String,
    ): TeamCountConfirmationUiState? {
        val participation = observeTournamentSlots(tournamentId)
            .first()
            .analyzeTeamSlotParticipation()
        return if (participation.activeCount < TeamSlot.MAX_SLOT_NUMBER) {
            TeamCountConfirmationUiState(
                enteredCount = participation.activeCount,
                emptyCount = TeamSlot.MAX_SLOT_NUMBER - participation.activeCount,
            )
        } else {
            null
        }
    }

    suspend fun applyDefaults(tournamentId: String): Boolean {
        val slots = observeTournamentSlots(tournamentId).first()
        val names = slots.associate { slot ->
            val trimmedName = slot.teamName.trim()
            slot.slotNumber to if (trimmedName.isBlank()) {
                defaultTeamNameForSlot(slot.slotNumber)
            } else {
                trimmedName
            }
        }
        val validation = validateTournamentRoster(
            tournamentId = tournamentId,
            teamNamesBySlotNumber = names,
            activeTeamSlotNumbers = TeamSlot.SLOT_NUMBERS.toSet(),
        )
        if (validation.hasBlockingIssues) return false

        return runCatching {
            saveTeamSlotNames(tournamentId, names)
        }.getOrNull() == SaveTeamSlotNamesResult.Saved
    }

    suspend fun create(tournamentId: String): CreateNextMatchResult {
        val result = createNextMatch(tournamentId)
        if (result !is CreateNextMatchResult.Created) return result

        val inheritedLobby = try {
            applyLobbyTemplate(result.match.tournamentId, result.match.id) == ApplyLobbyTemplateResult.Applied
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
        try {
            syncDraftMatches(result.match.tournamentId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Local match creation remains authoritative for navigation.
        }
        if (inheritedLobby) {
            (1..3).forEach { index ->
                try {
                    lobbyUploadCheckpoint.run(
                        MatchLobbyScreenshotIdentity(
                            tournamentId = result.match.tournamentId,
                            matchId = result.match.id,
                            lobbyScreenshotIndex = index,
                        ),
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    // Local inheritance and navigation remain authoritative.
                }
            }
        }
        return result
    }
}

fun CreateNextMatchFailure.toCalculatePointsMessage(): CalculatePointsMessage = when (this) {
    CreateNextMatchFailure.NO_PARTICIPATING_TEAMS -> CalculatePointsMessage.NO_TEAMS_SAVED
    CreateNextMatchFailure.INVALID_TEAM_SLOTS -> CalculatePointsMessage.INVALID_TEAM_SLOTS
    CreateNextMatchFailure.AUTHENTICATION_REQUIRED,
    CreateNextMatchFailure.TOURNAMENT_NOT_FOUND,
    CreateNextMatchFailure.LIMIT_REACHED,
    CreateNextMatchFailure.REPOSITORY_REJECTED,
    -> CalculatePointsMessage.MATCH_CREATION_FAILED
}
