package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.first

data class SaveMatchPlacementsInput(
    val matchId: String,
    val placementsByTeamSlot: Map<Int, String>,
)

enum class PlacementValidationError {
    INVALID,
    DUPLICATE,
}

enum class PlacementGlobalError {
    AUTHENTICATION_REQUIRED,
    MATCH_NOT_FOUND,
    MATCH_NOT_DRAFT,
    INVALID_DATA,
}

sealed interface SaveMatchPlacementsResult {
    data class Saved(val placements: List<MatchPlacement>) : SaveMatchPlacementsResult

    data class Invalid(
        val errorsByTeamSlot: Map<Int, PlacementValidationError> = emptyMap(),
        val globalError: PlacementGlobalError? = null,
    ) : SaveMatchPlacementsResult
}

class SaveMatchPlacementsUseCase(
    private val repository: TournamentRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: TournamentRepository) : this(repository, SetupMutationUnauthenticatedAuthRepository)

    suspend operator fun invoke(input: SaveMatchPlacementsInput): SaveMatchPlacementsResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return SaveMatchPlacementsResult.Invalid(
                globalError = PlacementGlobalError.AUTHENTICATION_REQUIRED,
            )
        val match = repository.observeMatchByIdAndOwner(input.matchId, ownerUserId).first()
            ?: return SaveMatchPlacementsResult.Invalid(globalError = PlacementGlobalError.MATCH_NOT_FOUND)
        if (match.status != MatchStatus.DRAFT) {
            return SaveMatchPlacementsResult.Invalid(globalError = PlacementGlobalError.MATCH_NOT_DRAFT)
        }

        val errors = mutableMapOf<Int, PlacementValidationError>()
        val parsedPlacements = mutableMapOf<Int, Int>()
        input.placementsByTeamSlot.forEach { (teamSlotNumber, value) ->
            val trimmedValue = value.trim()
            if (trimmedValue.isBlank()) return@forEach
            val position = trimmedValue.toIntOrNull()
            if (
                teamSlotNumber !in TeamSlot.SLOT_NUMBERS ||
                trimmedValue.any { it !in '0'..'9' } ||
                position == null ||
                position !in TeamSlot.SLOT_NUMBERS
            ) {
                errors[teamSlotNumber] = PlacementValidationError.INVALID
            } else {
                parsedPlacements[teamSlotNumber] = position
            }
        }

        parsedPlacements.entries
            .groupBy { (_, position) -> position }
            .values
            .filter { entries -> entries.size > 1 }
            .flatMap { entries -> entries.map { it.key } }
            .forEach { teamSlotNumber ->
                errors[teamSlotNumber] = PlacementValidationError.DUPLICATE
            }
        if (errors.isNotEmpty()) {
            return SaveMatchPlacementsResult.Invalid(errorsByTeamSlot = errors)
        }

        val placements = parsedPlacements
            .toSortedMap()
            .map { (teamSlotNumber, position) ->
                MatchPlacement(teamSlotNumber = teamSlotNumber, position = position)
            }
        return when (
            val result = repository.saveDraftMatchPlacementsByOwner(input.matchId, ownerUserId, placements)
        ) {
            SaveMatchPlacementsRepositoryResult.Saved -> SaveMatchPlacementsResult.Saved(placements)
            is SaveMatchPlacementsRepositoryResult.Rejected -> when (result.reason) {
                SaveMatchPlacementsFailure.MATCH_NOT_FOUND -> SaveMatchPlacementsResult.Invalid(
                    globalError = PlacementGlobalError.MATCH_NOT_FOUND,
                )
                SaveMatchPlacementsFailure.MATCH_NOT_DRAFT -> SaveMatchPlacementsResult.Invalid(
                    globalError = PlacementGlobalError.MATCH_NOT_DRAFT,
                )
                SaveMatchPlacementsFailure.INVALID_TEAM_SLOT,
                SaveMatchPlacementsFailure.INVALID_POSITION,
                SaveMatchPlacementsFailure.DUPLICATE_TEAM_SLOT,
                SaveMatchPlacementsFailure.DUPLICATE_POSITION,
                -> SaveMatchPlacementsResult.Invalid(globalError = PlacementGlobalError.INVALID_DATA)
            }
        }
    }
}

enum class SaveMatchPlacementsFailure {
    MATCH_NOT_FOUND,
    MATCH_NOT_DRAFT,
    INVALID_TEAM_SLOT,
    INVALID_POSITION,
    DUPLICATE_TEAM_SLOT,
    DUPLICATE_POSITION,
}

sealed interface SaveMatchPlacementsRepositoryResult {
    data object Saved : SaveMatchPlacementsRepositoryResult

    data class Rejected(val reason: SaveMatchPlacementsFailure) : SaveMatchPlacementsRepositoryResult
}
