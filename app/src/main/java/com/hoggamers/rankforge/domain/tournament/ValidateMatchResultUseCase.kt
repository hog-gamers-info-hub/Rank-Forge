package com.hoggamers.rankforge.domain.tournament

data class MatchResultRowInput(
    val teamSlotNumber: Int,
    val placement: String?,
    val kills: String?,
    val participationStatus: MatchParticipationStatus = MatchParticipationStatus.PARTICIPATED,
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
    operator fun invoke(rows: List<MatchResultRowInput>): MatchResultValidation =
        invoke(rows, TeamSlot.SLOT_NUMBERS.toList())

    operator fun invoke(
        rows: List<MatchResultRowInput>,
        expectedTeamSlots: Collection<Int>,
    ): MatchResultValidation {
        val errors = mutableMapOf<Int, MutableSet<MatchResultValidationError>>()
        val expectedSlots = expectedTeamSlots.toSet()
        val expectedPlacements = 1..expectedSlots.size
        val rowsByTeamSlot = rows.groupBy { it.teamSlotNumber }

        expectedSlots
            .filterNot { rowsByTeamSlot.containsKey(it) }
            .forEach { teamSlotNumber ->
                errors.add(teamSlotNumber, MatchResultValidationError.MISSING_TEAM_RESULT_ROW)
            }
        rowsByTeamSlot
            .filterValues { teamRows -> teamRows.size > 1 }
            .keys
            .forEach { teamSlotNumber ->
                errors.add(teamSlotNumber, MatchResultValidationError.DUPLICATE_TEAM)
            }
        rows
            .filter { it.teamSlotNumber !in expectedSlots }
            .forEach { row ->
                errors.add(row.teamSlotNumber, MatchResultValidationError.DUPLICATE_TEAM)
            }

        rows.forEach { row ->
            if (row.teamSlotNumber !in expectedSlots) return@forEach
            val placement = row.placement?.trim().orEmpty()
            if (placement.isBlank()) {
                errors.add(row.teamSlotNumber, MatchResultValidationError.MISSING_PLACEMENT)
            } else if (!placement.isWholeNumberIn(expectedPlacements)) {
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
                    ?.takeIf { row.teamSlotNumber in expectedSlots && it in expectedPlacements }
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

    fun validateParticipantResults(
        rows: List<MatchResultRowInput>,
        expectedTeamSlots: Collection<Int>,
    ): MatchResultValidation {
        val errors = mutableMapOf<Int, MutableSet<MatchResultValidationError>>()
        val expectedSlots = expectedTeamSlots.toSet()
        val rowsByTeamSlot = rows.groupBy { it.teamSlotNumber }
        expectedSlots.filterNot { it in rowsByTeamSlot }
            .forEach { errors.add(it, MatchResultValidationError.MISSING_TEAM_RESULT_ROW) }
        rowsByTeamSlot.filterValues { it.size > 1 }.keys
            .forEach { errors.add(it, MatchResultValidationError.DUPLICATE_TEAM) }
        rows.filter { it.teamSlotNumber !in expectedSlots }
            .forEach { errors.add(it.teamSlotNumber, MatchResultValidationError.DUPLICATE_TEAM) }

        rows.filter { it.teamSlotNumber in expectedSlots }.forEach { row ->
            when (row.participationStatus) {
                MatchParticipationStatus.NO_SHOW -> {
                    val placement = row.placement?.trim().orEmpty()
                    if (placement.isNotBlank()) {
                        errors.add(row.teamSlotNumber, MatchResultValidationError.INVALID_PLACEMENT)
                    }
                    val kills = row.kills?.trim().orEmpty()
                    if (kills.isNotBlank() && kills != "0") {
                        errors.add(row.teamSlotNumber, MatchResultValidationError.INVALID_KILLS)
                    }
                }
                MatchParticipationStatus.PARTICIPATED -> {
                    val placement = row.placement?.trim().orEmpty()
                    if (placement.isBlank()) {
                        errors.add(row.teamSlotNumber, MatchResultValidationError.MISSING_PLACEMENT)
                    } else if (!placement.isWholeNumberAtLeastOne()) {
                        errors.add(row.teamSlotNumber, MatchResultValidationError.INVALID_PLACEMENT)
                    }
                    val kills = row.kills?.trim().orEmpty()
                    if (kills.isBlank()) {
                        errors.add(row.teamSlotNumber, MatchResultValidationError.MISSING_KILLS)
                    } else if (!kills.isWholeNumberAtLeastZero()) {
                        errors.add(row.teamSlotNumber, MatchResultValidationError.INVALID_KILLS)
                    }
                }
            }
        }
        val participated = rows.filter { it.teamSlotNumber in expectedSlots && it.participationStatus == MatchParticipationStatus.PARTICIPATED }
        val expectedPlacements = 1..participated.size
        participated.mapNotNull { row ->
            row.placement?.trim()?.toIntOrNull()?.takeIf { it in expectedPlacements }
                ?.let { it to row.teamSlotNumber }
        }.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }
            .values.flatten().forEach { errors.add(it, MatchResultValidationError.DUPLICATE_PLACEMENT) }
        if (participated.isNotEmpty()) {
            val validPlacements = participated.mapNotNull { it.placement?.trim()?.toIntOrNull() }.toSet()
            if (validPlacements != expectedPlacements.toSet()) {
                participated.forEach { row ->
                    if (row.placement?.trim()?.toIntOrNull() !in expectedPlacements) {
                        errors.add(row.teamSlotNumber, MatchResultValidationError.INVALID_PLACEMENT)
                    }
                }
            }
        }
        return MatchResultValidation(errors.mapValues { it.value.toSet() })
    }

    /**
     * Validates the positioned rows of a new finalized match.
     *
     * Missing registered TeamSlots are allowed here because the caller derives
     * their NO_SHOW snapshot rows from the registered-minus-positioned set.
     */
    fun validateForInitialFinalization(
        rows: List<MatchResultRowInput>,
        registeredTeamSlots: Collection<Int>,
    ): MatchResultValidation {
        val errors = mutableMapOf<Int, MutableSet<MatchResultValidationError>>()
        val registeredSlots = registeredTeamSlots.toSet()
        val expectedPlacements = 1..rows.size
        val rowsByTeamSlot = rows.groupBy { it.teamSlotNumber }

        rowsByTeamSlot
            .filterValues { teamRows -> teamRows.size > 1 }
            .keys
            .forEach { teamSlotNumber ->
                errors.add(teamSlotNumber, MatchResultValidationError.DUPLICATE_TEAM)
            }
        rows
            .filter { it.teamSlotNumber !in registeredSlots }
            .forEach { row ->
                errors.add(row.teamSlotNumber, MatchResultValidationError.DUPLICATE_TEAM)
            }

        rows.forEach { row ->
            if (row.teamSlotNumber !in registeredSlots) return@forEach
            val placement = row.placement?.trim().orEmpty()
            if (placement.isBlank()) {
                errors.add(row.teamSlotNumber, MatchResultValidationError.MISSING_PLACEMENT)
            } else if (!placement.isWholeNumberIn(expectedPlacements)) {
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
                    ?.takeIf { row.teamSlotNumber in registeredSlots && it in expectedPlacements }
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

    operator fun invoke(match: Match): MatchResultValidation =
        invoke(match, TeamSlot.SLOT_NUMBERS.toList())

    operator fun invoke(
        match: Match,
        expectedTeamSlots: Collection<Int>,
    ): MatchResultValidation {
        val errors = mutableMapOf<Int, MutableSet<MatchResultValidationError>>()
        val expectedSlots = expectedTeamSlots.toSet()
        val expectedPlacements = 1..expectedSlots.size
        val placementsByTeamSlot = match.placements.groupBy { it.teamSlotNumber }
        val killsByTeamSlot = match.kills.groupBy { it.teamSlotNumber }
        val resultTeamSlots = placementsByTeamSlot.keys + killsByTeamSlot.keys

        expectedSlots
            .filterNot { it in resultTeamSlots }
            .forEach { teamSlotNumber ->
                errors.add(teamSlotNumber, MatchResultValidationError.MISSING_TEAM_RESULT_ROW)
            }
        (placementsByTeamSlot.keys + killsByTeamSlot.keys)
            .filter { teamSlotNumber ->
                placementsByTeamSlot[teamSlotNumber].orEmpty().size > 1 ||
                    killsByTeamSlot[teamSlotNumber].orEmpty().size > 1
            }
            .forEach { teamSlotNumber ->
                errors.add(teamSlotNumber, MatchResultValidationError.DUPLICATE_TEAM)
            }
        resultTeamSlots
            .filter { it !in expectedSlots }
            .forEach { teamSlotNumber ->
                errors.add(teamSlotNumber, MatchResultValidationError.DUPLICATE_TEAM)
            }

        val validPositions = mutableListOf<Pair<Int, Int>>()
        expectedSlots.forEach { teamSlotNumber ->
            val placements = placementsByTeamSlot[teamSlotNumber].orEmpty()
            if (placements.isEmpty()) {
                errors.add(teamSlotNumber, MatchResultValidationError.MISSING_PLACEMENT)
            } else {
                placements.forEach { placement ->
                    if (placement.position !in expectedPlacements) {
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

    private fun String.isWholeNumberAtLeastOne(): Boolean =
        any { it !in '0'..'9' }.not() && toIntOrNull()?.let { it >= 1 } == true

    private fun MutableMap<Int, MutableSet<MatchResultValidationError>>.add(
        teamSlotNumber: Int,
        error: MatchResultValidationError,
    ) {
        getOrPut(teamSlotNumber) { mutableSetOf() }.add(error)
    }
}
