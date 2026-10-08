package com.hoggamers.rankforge.domain.tournament

/** Pure structural validation for authoritative Group Rotation restoration data. */
object GroupRotationRestorationValidator {
    fun isValid(
        tournament: Tournament,
        slots: List<TeamSlot>,
        pairingLobbySlots: List<GroupRotationPairingLobbySlot>,
    ): Boolean {
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            return pairingLobbySlots.isEmpty()
        }

        val expectedSlots = runCatching { tournament.formatDerivedSlots() }.getOrNull()
            ?: return false
        val expectedSlotNumbers = expectedSlots.map { it.slotNumber }.toSet()
        if (slots.size != expectedSlots.size || slots.map { it.slotNumber }.toSet() != expectedSlotNumbers) {
            return false
        }
        if (slots.any { slot ->
                slot.tournamentId != tournament.id ||
                    expectedSlots.firstOrNull { it.slotNumber == slot.slotNumber }?.group != slot.group
            }
        ) {
            return false
        }

        if (pairingLobbySlots.isEmpty()) return true

        val expectedPairingKeys = tournament.selectedGroupPairings.map { it.canonicalKey }.toSet()
        val mappingsByPairing = pairingLobbySlots.groupBy { it.pairing.canonicalKey }
        if (mappingsByPairing.keys != expectedPairingKeys) return false

        val slotsByNumber = slots.associateBy { it.slotNumber }
        return mappingsByPairing.all { (pairingKey, mappings) ->
            mappings.size == GroupRotationPairingLobbySlot.MAX_LOBBY_SLOT_NUMBER &&
                mappings.all { mapping ->
                    mapping.tournamentId == tournament.id &&
                        mapping.pairing.canonicalKey == pairingKey &&
                        mapping.lobbySlotNumber in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS
                } &&
                mappings.map { it.lobbySlotNumber }.toSet() ==
                    GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS.toSet() &&
                mappings.map { it.teamSlotNumber }.distinct().size ==
                    GroupRotationPairingLobbySlot.MAX_LOBBY_SLOT_NUMBER &&
                mappings.all { mapping ->
                    mapping.teamSlotNumber in expectedSlotNumbers &&
                        slotsByNumber[mapping.teamSlotNumber]
                            ?.teamName
                            ?.trim()
                            ?.isNotEmpty() == true
                }
        }
    }
}
