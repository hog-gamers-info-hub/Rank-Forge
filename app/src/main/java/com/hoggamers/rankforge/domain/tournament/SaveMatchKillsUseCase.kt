package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.first

data class SaveMatchKillsInput(
    val matchId: String,
    val killsByTeamSlot: Map<Int, String>,
)

enum class KillValidationError {
    INVALID,
}

enum class KillGlobalError {
    MATCH_NOT_FOUND,
    MATCH_NOT_DRAFT,
    INVALID_DATA,
}

sealed interface SaveMatchKillsResult {
    data class Saved(val kills: List<MatchKill>) : SaveMatchKillsResult

    data class Invalid(
        val errorsByTeamSlot: Map<Int, KillValidationError> = emptyMap(),
        val globalError: KillGlobalError? = null,
    ) : SaveMatchKillsResult
}

class SaveMatchKillsUseCase(
    private val repository: TournamentRepository,
) {
    suspend operator fun invoke(input: SaveMatchKillsInput): SaveMatchKillsResult {
        val match = repository.observeMatchById(input.matchId).first()
            ?: return SaveMatchKillsResult.Invalid(globalError = KillGlobalError.MATCH_NOT_FOUND)
        if (match.status != MatchStatus.DRAFT) {
            return SaveMatchKillsResult.Invalid(globalError = KillGlobalError.MATCH_NOT_DRAFT)
        }

        val errors = mutableMapOf<Int, KillValidationError>()
        val parsedKills = mutableMapOf<Int, Int>()
        input.killsByTeamSlot.forEach { (teamSlotNumber, value) ->
            val trimmedValue = value.trim()
            if (trimmedValue.isBlank()) return@forEach
            val kills = trimmedValue.toIntOrNull()
            if (
                teamSlotNumber !in TeamSlot.SLOT_NUMBERS ||
                trimmedValue.any { it !in '0'..'9' } ||
                kills == null ||
                kills < 0
            ) {
                errors[teamSlotNumber] = KillValidationError.INVALID
            } else {
                parsedKills[teamSlotNumber] = kills
            }
        }
        if (errors.isNotEmpty()) {
            return SaveMatchKillsResult.Invalid(errorsByTeamSlot = errors)
        }

        val kills = parsedKills
            .toSortedMap()
            .map { (teamSlotNumber, teamKills) ->
                MatchKill(teamSlotNumber = teamSlotNumber, kills = teamKills)
            }
        return when (val result = repository.saveDraftMatchKills(input.matchId, kills)) {
            SaveMatchKillsRepositoryResult.Saved -> SaveMatchKillsResult.Saved(kills)
            is SaveMatchKillsRepositoryResult.Rejected -> when (result.reason) {
                SaveMatchKillsFailure.MATCH_NOT_FOUND -> SaveMatchKillsResult.Invalid(
                    globalError = KillGlobalError.MATCH_NOT_FOUND,
                )
                SaveMatchKillsFailure.MATCH_NOT_DRAFT -> SaveMatchKillsResult.Invalid(
                    globalError = KillGlobalError.MATCH_NOT_DRAFT,
                )
                SaveMatchKillsFailure.INVALID_TEAM_SLOT,
                SaveMatchKillsFailure.INVALID_KILLS,
                SaveMatchKillsFailure.DUPLICATE_TEAM_SLOT,
                -> SaveMatchKillsResult.Invalid(globalError = KillGlobalError.INVALID_DATA)
            }
        }
    }
}

enum class SaveMatchKillsFailure {
    MATCH_NOT_FOUND,
    MATCH_NOT_DRAFT,
    INVALID_TEAM_SLOT,
    INVALID_KILLS,
    DUPLICATE_TEAM_SLOT,
}

sealed interface SaveMatchKillsRepositoryResult {
    data object Saved : SaveMatchKillsRepositoryResult

    data class Rejected(val reason: SaveMatchKillsFailure) : SaveMatchKillsRepositoryResult
}
