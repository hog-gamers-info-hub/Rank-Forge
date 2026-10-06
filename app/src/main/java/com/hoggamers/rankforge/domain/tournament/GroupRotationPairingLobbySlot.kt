package com.hoggamers.rankforge.domain.tournament

/**
 * Persists the reviewed identity of one local lobby position for one selected pairing.
 *
 * The lobby slot is deliberately separate from the permanent tournament team slot.
 */
data class GroupRotationPairingLobbySlot(
    val tournamentId: String,
    val pairing: GroupPairing,
    val lobbySlotNumber: Int,
    val teamSlotNumber: Int,
) {
    init {
        require(tournamentId.isNotBlank()) { "Tournament id is required." }
        require(lobbySlotNumber in LOBBY_SLOT_NUMBERS) {
            "Group Rotation lobby slot number must be between 1 and 12."
        }
        require(teamSlotNumber in TeamSlot.TOURNAMENT_SLOT_NUMBERS) {
            "Team slot number must be between 1 and 24."
        }
    }

    companion object {
        const val MIN_LOBBY_SLOT_NUMBER = 1
        const val MAX_LOBBY_SLOT_NUMBER = 12
        val LOBBY_SLOT_NUMBERS: IntRange = MIN_LOBBY_SLOT_NUMBER..MAX_LOBBY_SLOT_NUMBER
    }
}
