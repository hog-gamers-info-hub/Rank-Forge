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

        val expectedSlotCount = when (tournament.groupCount) {
            3 -> 18
            4 -> 24
            else -> return false
        }
        val expectedSlotNumbers = (1..expectedSlotCount).toSet()
        if (slots.size != expectedSlotCount || slots.map { it.slotNumber }.toSet() != expectedSlotNumbers) {
            return false
        }
        if (slots.any { it.tournamentId != tournament.id }) {
            return false
        }

        val normalizer = GroupRotationTeamIdentityNormalizer()
        val normalizedOccupiedNames = slots
            .map { normalizer.normalize(it.teamName) }
            .filter(String::isNotBlank)
        if (normalizedOccupiedNames.size != normalizedOccupiedNames.toSet().size) {
            return false
        }

        if (pairingLobbySlots.isEmpty()) return true

        val expectedPairingKeys = tournament.selectedGroupPairings.map { it.canonicalKey }.toSet()
        val mappingsByPairing = pairingLobbySlots.groupBy { it.pairing.canonicalKey }
        if (mappingsByPairing.keys.any { it !in expectedPairingKeys }) return false

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
