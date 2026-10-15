package com.hoggamers.rankforge.data.tournament

import com.hoggamers.rankforge.data.local.GroupRotationPairingLobbySlotEntity
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot

internal fun GroupRotationPairingLobbySlot.toEntity(): GroupRotationPairingLobbySlotEntity =
    GroupRotationPairingLobbySlotEntity(
        tournamentId = tournamentId,
        pairingKey = pairing.canonicalKey,
        lobbySlotNumber = lobbySlotNumber,
        teamSlotNumber = teamSlotNumber,
    )

internal fun GroupRotationPairingLobbySlotEntity.toDomain(
    pairing: GroupPairing,
): GroupRotationPairingLobbySlot {
    require(pairing.canonicalKey == pairingKey) {
        "Pairing does not match the persisted pairing key."
    }
    return GroupRotationPairingLobbySlot(
        tournamentId = tournamentId,
        pairing = pairing,
        lobbySlotNumber = lobbySlotNumber,
        teamSlotNumber = teamSlotNumber,
    )
}
