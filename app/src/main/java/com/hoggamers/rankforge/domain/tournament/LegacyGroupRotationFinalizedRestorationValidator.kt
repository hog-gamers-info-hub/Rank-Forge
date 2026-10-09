package com.hoggamers.rankforge.domain.tournament

/**
 * Validates historical finalized Group Rotation results when no V2 lobby
 * mappings were ever persisted. This path preserves canonical result identity
 * without reconstructing lobby assignments.
 */
object LegacyGroupRotationFinalizedRestorationValidator {
    fun isValid(
        tournament: Tournament,
        persistedTeamSlots: Collection<TeamSlot>,
        matches: Collection<Match>,
    ): Boolean {
        if (tournament.format != TournamentFormat.GROUP_ROTATION) return false

        val groupCount = tournament.groupCount ?: return false
        val expectedSlotCount = when (groupCount) {
            3 -> 18
            4 -> 24
            else -> return false
        }
        val expectedSlotNumbers = (1..expectedSlotCount).toSet()
        if (
            persistedTeamSlots.size != expectedSlotCount ||
            persistedTeamSlots.any { it.tournamentId != tournament.id } ||
            persistedTeamSlots.map { it.slotNumber }.toSet() != expectedSlotNumbers
        ) {
            return false
        }

        val selectedPairings = tournament.selectedGroupPairings.toSet()
        return matches.all { match ->
            if (
                match.tournamentId != tournament.id ||
                match.status != MatchStatus.FINALIZED
            ) {
                return@all false
            }
            val pairing = match.groupPairing ?: return@all false
            if (
                pairing !in selectedPairings ||
                pairing.firstGroup.ordinal >= groupCount ||
                pairing.secondGroup.ordinal >= groupCount
            ) {
                return@all false
            }

            val referencedTeamSlots = buildList {
                addAll(match.placements.map { it.teamSlotNumber })
                addAll(match.kills.map { it.teamSlotNumber })
                addAll(match.participantResults.map { it.teamSlotNumber })
            }
            referencedTeamSlots.distinct().size <= TeamSlot.SLOT_NUMBERS.count() &&
                match.placements.all { it.position in TeamSlot.SLOT_NUMBERS } &&
                match.participantResults.all {
                    it.placement?.let { placement ->
                        placement in TeamSlot.SLOT_NUMBERS
                    } ?: true
                } &&
                referencedTeamSlots.all { it in expectedSlotNumbers }
        }
    }
}
