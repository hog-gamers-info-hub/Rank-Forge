package com.hoggamers.rankforge.domain.tournament

/** The two identities that must remain separate for one match. */
data class MatchLobbyTeamIdentity(
    val lobbySlotNumber: Int,
    val teamSlotNumber: Int,
)

data class MatchTeamIdentityContext(
    val tournamentId: String,
    val pairing: GroupPairing?,
    val teams: List<MatchLobbyTeamIdentity>,
) {
    init {
        require(teams.size == TeamSlot.SLOT_NUMBERS.count()) {
            "A match identity context must contain exactly twelve lobby teams."
        }
        require(teams.map { it.lobbySlotNumber }.toSet() == TeamSlot.SLOT_NUMBERS.toSet()) {
            "A match identity context must contain each lobby slot from 1 through 12 exactly once."
        }
        require(teams.map { it.teamSlotNumber }.toSet().size == teams.size) {
            "A match identity context must contain unique canonical team slots."
        }
        require(teams.all { it.teamSlotNumber in TeamSlot.TOURNAMENT_SLOT_NUMBERS }) {
            "A match identity context contains an invalid canonical team slot."
        }
    }

    val orderedTeams: List<MatchLobbyTeamIdentity>
        get() = teams.sortedBy { it.lobbySlotNumber }

    val eligibleTeamSlotNumbers: Set<Int>
        get() = teams.map { it.teamSlotNumber }.toSet()

    fun canonicalTeamSlotForLobby(lobbySlotNumber: Int): Int? =
        teams.firstOrNull { it.lobbySlotNumber == lobbySlotNumber }?.teamSlotNumber

    fun lobbySlotForCanonicalTeamSlot(teamSlotNumber: Int): Int? =
        teams.firstOrNull { it.teamSlotNumber == teamSlotNumber }?.lobbySlotNumber
}

sealed interface MatchTeamIdentityContextReadResult {
    data class Loaded(val context: MatchTeamIdentityContext) : MatchTeamIdentityContextReadResult

    data object AuthenticationRequired : MatchTeamIdentityContextReadResult
    data object TournamentNotFound : MatchTeamIdentityContextReadResult
    data object MatchNotFound : MatchTeamIdentityContextReadResult
    data object PairingRequired : MatchTeamIdentityContextReadResult
    data object PairingNotSelected : MatchTeamIdentityContextReadResult
    data object SetupRequired : MatchTeamIdentityContextReadResult
    data object InvalidMapping : MatchTeamIdentityContextReadResult
}

/**
 * Returns only canonical team slots represented by persisted finalized match data.
 * Legacy finalized matches may not have a participant snapshot, so placements and
 * kills provide the narrowest historical fallback without inventing lobby positions.
 */
internal fun Match.finalizedHistoricalTeamSlotNumbers(): List<Int> =
    finalizedParticipantResultsOrNull()?.map { it.teamSlotNumber }
        ?: (placements.map { it.teamSlotNumber } + kills.map { it.teamSlotNumber })
            .filter { it in TeamSlot.TOURNAMENT_SLOT_NUMBERS }
            .distinct()
            .sorted()

interface MatchTeamIdentityContextRepository {
    suspend fun readForMatch(
        matchId: String,
        ownerUserId: String,
    ): MatchTeamIdentityContextReadResult

    suspend fun readForPairing(
        tournamentId: String,
        pairing: GroupPairing,
        ownerUserId: String,
    ): MatchTeamIdentityContextReadResult
}

suspend fun MatchTeamIdentityContextRepository.resolveEligibleTeamSlotNumbers(
    tournament: Tournament,
    persistedTeamSlots: Collection<TeamSlot>,
    match: Match,
    ownerUserId: String,
): Set<Int>? = if (tournament.format == TournamentFormat.GROUP_ROTATION) {
    (readForMatch(match.id, ownerUserId) as? MatchTeamIdentityContextReadResult.Loaded)
        ?.context
        ?.eligibleTeamSlotNumbers
} else {
    runCatching {
        MatchEligibleTeamSlotResolver().resolve(tournament, persistedTeamSlots, match)
    }.getOrNull()
}
