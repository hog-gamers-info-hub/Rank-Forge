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
        persistedMappings: Collection<GroupRotationPairingLobbySlot> = emptyList(),
    ): Set<Int> = resolveContext(
        tournament = tournament,
        persistedTeamSlots = persistedTeamSlots,
        match = match,
        persistedMappings = persistedMappings,
    ).eligibleTeamSlotNumbers

    fun resolveContext(
        tournament: Tournament,
        persistedTeamSlots: Collection<TeamSlot>,
        match: Match,
        persistedMappings: Collection<GroupRotationPairingLobbySlot> = emptyList(),
    ): MatchTeamIdentityContext {
        require(match.tournamentId == tournament.id) {
            "Match and tournament ids must match."
        }
        return when (tournament.format) {
            TournamentFormat.STANDARD -> {
                require(match.groupPairing == null) {
                    "Standard matches cannot specify a group pairing."
                }
                MatchTeamIdentityContext(
                    tournamentId = tournament.id,
                    pairing = null,
                    teams = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
                        MatchLobbyTeamIdentity(slotNumber, slotNumber)
                    },
                )
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
                require(persistedMappings.size == TeamSlot.SLOT_NUMBERS.count()) {
                    "Group Rotation match identity mapping must contain exactly twelve rows."
                }
                require(persistedMappings.all { mapping ->
                    mapping.tournamentId == tournament.id && mapping.pairing == pairing
                }) {
                    "Group Rotation match identity mapping belongs to another tournament or pairing."
                }
                require(persistedMappings.map { it.lobbySlotNumber }.toSet() == TeamSlot.SLOT_NUMBERS.toSet()) {
                    "Group Rotation match identity mapping must cover lobby slots 1 through 12 exactly once."
                }
                val mappedTeamSlotNumbers = persistedMappings.map { it.teamSlotNumber }
                require(mappedTeamSlotNumbers.toSet().size == mappedTeamSlotNumbers.size) {
                    "Group Rotation match identity mapping must contain unique canonical team slots."
                }
                val validTournamentSlots = tournament.formatDerivedSlots()
                    .map { it.slotNumber }
                    .toSet()
                require(mappedTeamSlotNumbers.all { it in validTournamentSlots }) {
                    "Group Rotation match identity mapping contains a team slot outside the tournament format."
                }
                val persistedBySlot = persistedTeamSlots
                    .filter { it.tournamentId == tournament.id }
                    .associateBy { it.slotNumber }
                require(mappedTeamSlotNumbers.all { teamSlotNumber ->
                    persistedBySlot[teamSlotNumber]?.teamName?.isNotBlank() == true
                }) {
                    "Group Rotation match identity mapping requires a nonblank canonical team name."
                }
                MatchTeamIdentityContext(
                    tournamentId = tournament.id,
                    pairing = pairing,
                    teams = persistedMappings.sortedBy { it.lobbySlotNumber }.map { mapping ->
                        MatchLobbyTeamIdentity(mapping.lobbySlotNumber, mapping.teamSlotNumber)
                    },
                )
            }
        }
    }
}

fun MatchEligibleTeamSlotResolver.resolveOrNull(
    tournament: Tournament,
    persistedTeamSlots: Collection<TeamSlot>,
    match: Match,
    persistedMappings: Collection<GroupRotationPairingLobbySlot> = emptyList(),
): Set<Int>? = runCatching {
    resolve(tournament, persistedTeamSlots, match, persistedMappings)
}.getOrNull()
