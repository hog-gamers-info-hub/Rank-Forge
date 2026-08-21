package com.hoggamers.rankforge.domain.tournament

data class TeamSlotParticipation(
    val activeSlotNumbers: List<Int>,
    val hasGap: Boolean,
) {
    val activeCount: Int
        get() = activeSlotNumbers.size

    val isReadyForMatchCreation: Boolean
        get() = activeCount > 0
}

fun Map<Int, String>.analyzeTeamSlotParticipation(): TeamSlotParticipation =
    TeamSlot.SLOT_NUMBERS.analyzeTeamSlotParticipation { slotNumber ->
        this[slotNumber].orEmpty()
    }

fun List<TeamSlot>.analyzeTeamSlotParticipation(): TeamSlotParticipation =
    TeamSlot.SLOT_NUMBERS.analyzeTeamSlotParticipation { slotNumber ->
        firstOrNull { it.slotNumber == slotNumber }?.teamName.orEmpty()
    }

fun defaultTeamNameForSlot(slotNumber: Int): String =
    "Team ${slotNumber.toString().padStart(2, '0')}"

private fun IntRange.analyzeTeamSlotParticipation(
    nameForSlot: (Int) -> String,
): TeamSlotParticipation {
    val activeSlotNumbers = mutableListOf<Int>()
    var foundBlank = false
    var hasGap = false
    for (slotNumber in this) {
        if (nameForSlot(slotNumber).trim().isBlank()) {
            foundBlank = true
        } else {
            activeSlotNumbers += slotNumber
            if (foundBlank) {
                hasGap = true
            }
        }
    }
    return TeamSlotParticipation(
        activeSlotNumbers = activeSlotNumbers,
        hasGap = hasGap,
    )
}
