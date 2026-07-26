package com.hoggamers.rankforge.domain.tournament

data class MatchResultRowInput(
    val teamSlotNumber: Int,
    val placement: String?,
    val kills: String?,
)

enum class MatchResultValidationError {
    MISSING_TEAM_RESULT_ROW,
    DUPLICATE_TEAM,
    MISSING_PLACEMENT,
    DUPLICATE_PLACEMENT,
    INVALID_PLACEMENT,
    MISSING_KILLS,
    INVALID_KILLS,
}

data class MatchResultValidation(
    val errorsByTeamSlot: Map<Int, Set<MatchResultValidationError>> = emptyMap(),
) {
    val isValid: Boolean
        get() = errorsByTeamSlot.isEmpty()
}

class ValidateMatchResultUseCase {
    operator fun invoke(rows: List<MatchResultRowInput>): MatchResultValidation {
        val errors = mutableMapOf<Int, MutableSet<MatchResultValidationError>>()
        val rowsByTeamSlot = rows.groupBy { it.teamSlotNumber }

        TeamSlot.SLOT_NUMBERS
            .filterNot { rowsByTeamSlot.containsKey(it) }
            .forEach { teamSlotNumber ->
                errors.add(teamSlotNumber, MatchResultValidationError.MISSING_TEAM_RESULT_ROW)
            }
        rowsByTeamSlot
            .filterValues { teamRows -> teamRows.size > 1 }
            .keys
            .filter { it in TeamSlot.SLOT_NUMBERS }
            .forEach { teamSlotNumber ->
                errors.add(teamSlotNumber, MatchResultValidationError.DUPLICATE_TEAM)
            }

        rows.forEach { row ->
            if (row.teamSlotNumber !in TeamSlot.SLOT_NUMBERS) return@forEach
            val placement = row.placement?.trim().orEmpty()
            if (placement.isBlank()) {
                errors.add(row.teamSlotNumber, MatchResultValidationError.MISSING_PLACEMENT)
            } else if (!placement.isWholeNumberIn(TeamSlot.SLOT_NUMBERS)) {
                errors.add(row.teamSlotNumber, MatchResultValidationError.INVALID_PLACEMENT)
            }

            val kills = row.kills?.trim().orEmpty()
            if (kills.isBlank()) {
                errors.add(row.teamSlotNumber, MatchResultValidationError.MISSING_KILLS)
            } else if (!kills.isWholeNumberAtLeastZero()) {
                errors.add(row.teamSlotNumber, MatchResultValidationError.INVALID_KILLS)
            }
        }

        rows.asSequence()
            .mapNotNull { row ->
                val placement = row.placement?.trim().orEmpty()
                placement.toIntOrNull()
                    ?.takeIf { row.teamSlotNumber in TeamSlot.SLOT_NUMBERS && it in TeamSlot.SLOT_NUMBERS }
                    ?.let { it to row.teamSlotNumber }
            }
            .groupBy({ (position, _) -> position }, { (_, teamSlotNumber) -> teamSlotNumber })
            .filterValues { teamSlots -> teamSlots.size > 1 }
            .values
            .flatten()
            .forEach { teamSlotNumber ->
                errors.add(teamSlotNumber, MatchResultValidationError.DUPLICATE_PLACEMENT)
            }

        return MatchResultValidation(errors.mapValues { (_, value) -> value.toSet() })
    }

    operator fun invoke(match: Match): MatchResultValidation {
        val errors = mutableMapOf<Int, MutableSet<MatchResultValidationError>>()
        val placementsByTeamSlot = match.placements.groupBy { it.teamSlotNumber }
        val killsByTeamSlot = match.kills.groupBy { it.teamSlotNumber }
        val resultTeamSlots = placementsByTeamSlot.keys + killsByTeamSlot.keys

        TeamSlot.SLOT_NUMBERS
            .filterNot { it in resultTeamSlots }
            .forEach { teamSlotNumber ->
                errors.add(teamSlotNumber, MatchResultValidationError.MISSING_TEAM_RESULT_ROW)
            }
        (placementsByTeamSlot.keys + killsByTeamSlot.keys)
            .filter { teamSlotNumber ->
                placementsByTeamSlot[teamSlotNumber].orEmpty().size > 1 ||
                    killsByTeamSlot[teamSlotNumber].orEmpty().size > 1
            }
            .filter { it in TeamSlot.SLOT_NUMBERS }
            .forEach { teamSlotNumber ->
                errors.add(teamSlotNumber, MatchResultValidationError.DUPLICATE_TEAM)
            }

        val validPositions = mutableListOf<Pair<Int, Int>>()
        TeamSlot.SLOT_NUMBERS.forEach { teamSlotNumber ->
            val placements = placementsByTeamSlot[teamSlotNumber].orEmpty()
            if (placements.isEmpty()) {
                errors.add(teamSlotNumber, MatchResultValidationError.MISSING_PLACEMENT)
            } else {
                placements.forEach { placement ->
                    if (placement.position !in TeamSlot.SLOT_NUMBERS) {
                        errors.add(teamSlotNumber, MatchResultValidationError.INVALID_PLACEMENT)
                    } else {
                        validPositions += placement.position to teamSlotNumber
                    }
                }
            }

            val kills = killsByTeamSlot[teamSlotNumber].orEmpty()
            if (kills.isEmpty()) {
                errors.add(teamSlotNumber, MatchResultValidationError.MISSING_KILLS)
            } else {
                kills.forEach { kill ->
                    if (kill.kills < 0) {
                        errors.add(teamSlotNumber, MatchResultValidationError.INVALID_KILLS)
                    }
                }
            }
        }

        validPositions
            .groupBy({ (position, _) -> position }, { (_, teamSlotNumber) -> teamSlotNumber })
            .filterValues { teamSlots -> teamSlots.size > 1 }
            .values
            .flatten()
            .forEach { teamSlotNumber ->
                errors.add(teamSlotNumber, MatchResultValidationError.DUPLICATE_PLACEMENT)
            }

        return MatchResultValidation(errors.mapValues { (_, value) -> value.toSet() })
    }

    private fun String.isWholeNumberIn(range: IntRange): Boolean =
        any { it !in '0'..'9' }.not() && toIntOrNull()?.let { it in range } == true

    private fun String.isWholeNumberAtLeastZero(): Boolean =
        any { it !in '0'..'9' }.not() && toIntOrNull()?.let { it >= 0 } == true

    private fun MutableMap<Int, MutableSet<MatchResultValidationError>>.add(
        teamSlotNumber: Int,
        error: MatchResultValidationError,
    ) {
        getOrPut(teamSlotNumber) { mutableSetOf() }.add(error)
    }
}
