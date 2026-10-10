package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchTeamIdentityContext
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.formatDerivedSlots

internal fun groupRotationTeamSlotsByNumberOrNull(
    tournament: Tournament,
    teamSlots: List<TeamSlot>,
): Map<Int, TeamSlot>? {
    if (tournament.format != TournamentFormat.GROUP_ROTATION) return null
    val expectedSlots = runCatching { tournament.formatDerivedSlots() }.getOrNull()
        ?: return null
    val expectedByNumber = expectedSlots.associateBy { it.slotNumber }
    if (
        teamSlots.size != expectedSlots.size ||
        teamSlots.map { it.slotNumber }.distinct().size != teamSlots.size ||
        teamSlots.map { it.slotNumber }.toSet() != expectedByNumber.keys ||
        teamSlots.any { slot ->
            slot.tournamentId != tournament.id ||
                !slot.group.isValidGroupRotationMetadata(tournament, slot.slotNumber)
        }
    ) {
        return null
    }
    return teamSlots.associateBy { it.slotNumber }
}

internal fun MatchTeamIdentityContext.eligibleGroupRotationSlotNumbersOrNull(
    tournament: Tournament,
    match: Match,
    teamSlotsByNumber: Map<Int, TeamSlot>,
): Set<Int>? {
    if (
        match.tournamentId != tournament.id ||
        match.groupPairing == null ||
        tournament.format != TournamentFormat.GROUP_ROTATION ||
        tournament.selectedGroupPairings.none { it == match.groupPairing } ||
        tournament.id != tournamentId ||
        pairing != match.groupPairing
    ) {
        return null
    }
    val eligibleSlotNumbers = eligibleTeamSlotNumbers
    if (
        eligibleSlotNumbers.size != TeamSlot.SLOT_NUMBERS.count() ||
        eligibleSlotNumbers.any { slotNumber ->
            val slot = teamSlotsByNumber[slotNumber]
            slot == null || slot.teamName.isBlank()
        }
    ) {
        return null
    }
    return eligibleSlotNumbers
}

private fun TournamentGroup?.isValidGroupRotationMetadata(
    tournament: Tournament,
    slotNumber: Int,
): Boolean = when (this) {
    null -> true
    TournamentGroup.A -> slotNumber in 1..6
    TournamentGroup.B -> slotNumber in 7..12
    TournamentGroup.C -> slotNumber in 13..18
    TournamentGroup.D -> tournament.groupCount == 4 && slotNumber in 19..24
}
