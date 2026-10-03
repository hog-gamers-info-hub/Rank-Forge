package com.hoggamers.rankforge.domain.tournament

/**
 * Resolves the permanent team-slot identities that may participate in one match.
 *
 * Lobby/OCR positions are local to a match, but persisted results must retain the
 * tournament slot identity. Keeping this decision here prevents the UI, OCR, and
 * persistence layers from independently treating resting groups as participants.
 */
class MatchEligibleTeamSlotResolver {
    fun resolve(
        tournament: Tournament,
        persistedTeamSlots: Collection<TeamSlot>,
        match: Match,
    ): Set<Int> {
        require(match.tournamentId == tournament.id) {
            "Match and tournament ids must match."
        }
        return when (tournament.format) {
            TournamentFormat.STANDARD -> {
                require(match.groupPairing == null) {
                    "Standard matches cannot specify a group pairing."
                }
                TeamSlot.SLOT_NUMBERS.toSet()
            }

            TournamentFormat.GROUP_ROTATION -> {
                val pairing = requireNotNull(match.groupPairing) {
                    "Group Rotation matches require a group pairing."
                }
                require(pairing in tournament.selectedGroupPairings) {
                    "Match pairing is not selected for this tournament."
                }
                val groupCount = requireNotNull(tournament.groupCount)
                require(pairing.firstGroup.ordinal < groupCount && pairing.secondGroup.ordinal < groupCount) {
                    "Match pairing uses a group outside the tournament configuration."
                }
                val selectedGroups = setOf(pairing.firstGroup, pairing.secondGroup)
                val eligibleSlots = persistedTeamSlots
                    .filter { it.tournamentId == tournament.id && it.group in selectedGroups }
                    .onEach { slot ->
                        require(slot.slotNumber in TeamSlot.TOURNAMENT_SLOT_NUMBERS)
                    }
                val slotNumbers = eligibleSlots.map { it.slotNumber }
                require(slotNumbers.size == slotNumbers.toSet().size) {
                    "A match cannot contain duplicate permanent team-slot identities."
                }
                require(selectedGroups.all { group ->
                    eligibleSlots.count { it.group == group } == MAX_TEAMS_PER_GROUP
                }) {
                    "Each group pairing must resolve to six team slots per group."
                }
                val eligible = slotNumbers.toSet()
                require(eligible.size == MAX_TEAMS_PER_GROUP * 2) {
                    "Each group pairing must resolve to exactly twelve team slots."
                }
                eligible
            }
        }
    }
}

fun MatchEligibleTeamSlotResolver.resolveOrNull(
    tournament: Tournament,
    persistedTeamSlots: Collection<TeamSlot>,
    match: Match,
): Set<Int>? = runCatching { resolve(tournament, persistedTeamSlots, match) }.getOrNull()
