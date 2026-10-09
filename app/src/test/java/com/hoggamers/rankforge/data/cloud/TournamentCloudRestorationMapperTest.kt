package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.defaultGroupPairings
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentCloudRestorationMapperTest {
    @Test
    fun mapsTournamentSlotsAndPlayersToLocalSnapshot() {
        val result = TournamentCloudRestorationMapper.mapSnapshot(payloads())

        assertTrue(result is TournamentCloudRestorationMappingResult.Success)
        val snapshot = (result as TournamentCloudRestorationMappingResult.Success).value
        assertEquals(TOURNAMENT_ID, snapshot.tournament.id)
        assertEquals(OWNER_ID, snapshot.tournament.ownerUserId)
        assertEquals(TeamSlot.SLOT_NUMBERS.toList(), snapshot.slots.map { it.slotNumber })
        assertEquals("Alpha", snapshot.slots.first().teamName)
        assertEquals("Player One", snapshot.players.single().displayName)
        assertEquals(1, snapshot.players.single().rosterPosition)
    }

    @Test
    fun mapsAvailableCloudTournamentSummaries() {
        val result = TournamentCloudRestorationMapper.mapSummaries(
            listOf(payloads().tournament),
        )

        assertEquals(
            TournamentCloudRestorationMappingResult.Success(
                listOf(
                    com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSummary(
                        id = TOURNAMENT_ID,
                        name = "Summer Cup",
                        stageName = "Organizer",
                        status = "draft",
                    ),
                ),
            ),
            result,
        )
    }

    @Test
    fun rejectsPlayersAttachedToUnknownTeamSlot() {
        val result = TournamentCloudRestorationMapper.mapSnapshot(
            payloads().copy(
                players = listOf(
                    PlayerUploadPayload(
                        id = UUID.randomUUID().toString(),
                        teamSlotId = UUID.randomUUID().toString(),
                        displayName = "Unknown",
                        normalizedName = "Unknown",
                    ),
                ),
            ),
        )

        assertEquals(TournamentCloudRestorationMappingResult.Invalid, result)
    }

    @Test
    fun rejectsBlankTournamentOwner() {
        val base = payloads()

        val result = TournamentCloudRestorationMapper.mapSnapshot(
            base.copy(tournament = base.tournament.copy(ownerId = " ")),
        )

        assertEquals(TournamentCloudRestorationMappingResult.Invalid, result)
    }

    @Test
    fun restoresRosterPositionsFromDeterministicPlayerIds() {
        val base = payloads()
        val slotId = TournamentCloudIdentity.teamSlotId(UUID.fromString(TOURNAMENT_ID), 1)
        val positionTwo = PlayerUploadPayload(
            id = TournamentCloudIdentity.playerId(UUID.fromString(TOURNAMENT_ID), 1, 2),
            teamSlotId = slotId,
            displayName = "Player Two",
            normalizedName = "Player Two",
        )
        val result = TournamentCloudRestorationMapper.mapSnapshot(
            base.copy(players = listOf(positionTwo, base.players.single())),
        ) as TournamentCloudRestorationMappingResult.Success

        assertEquals(
            listOf("Player One", "Player Two"),
            result.value.players.sortedBy { it.rosterPosition }.map { it.displayName },
        )
        assertEquals(listOf(1, 2), result.value.players.map { it.rosterPosition })
    }

    @Test
    fun restoresThreeGroupConfigurationAndGroupMembership() {
        val result = TournamentCloudRestorationMapper.mapSnapshot(
            groupPayload(3),
        )
        assertTrue(result is TournamentCloudRestorationMappingResult.Success)
        val snapshot = (result as TournamentCloudRestorationMappingResult.Success).value

        assertEquals(TournamentFormat.GROUP_ROTATION, snapshot.tournament.format)
        assertEquals(3, snapshot.tournament.groupCount)
        assertEquals(3, snapshot.tournament.selectedGroupPairings.size)
        assertEquals((1..18).toList(), snapshot.slots.map { it.slotNumber })
        assertEquals("Team 13", snapshot.slots.single { it.slotNumber == 13 }.teamName)
        assertEquals(TournamentGroup.C, snapshot.slots.single { it.slotNumber == 13 }.group)
    }

    @Test
    fun restoresFourGroupExactSlotSet() {
        val result = TournamentCloudRestorationMapper.mapSnapshot(groupPayload(4))

        assertTrue(result is TournamentCloudRestorationMappingResult.Success)
        assertEquals(
            (1..24).toList(),
            (result as TournamentCloudRestorationMappingResult.Success).value.slots.map { it.slotNumber },
        )
    }

    @Test
    fun rejectsGroupRotationMissingSlot() {
        assertInvalid(groupPayload(3, slots = groupSlots(3).dropLast(1)))
    }

    @Test
    fun rejectsGroupRotationDuplicateSlot() {
        val slots = groupSlots(3).dropLast(1) + groupSlots(3).first()
        assertInvalid(groupPayload(3, slots = slots))
    }

    @Test
    fun rejectsGroupRotationWrongGroup() {
        val slots = groupSlots(3).map { slot ->
            if (slot.slotNumber == 13) slot.copy(group = "A") else slot
        }
        assertInvalid(groupPayload(3, slots = slots))
    }

    @Test
    fun acceptsGroupRotationWithZeroMappings() {
        val result = TournamentCloudRestorationMapper.mapSnapshot(groupPayload(3, mappings = emptyList()))

        assertTrue(result is TournamentCloudRestorationMappingResult.Success)
        assertTrue((result as TournamentCloudRestorationMappingResult.Success).value.pairingLobbySlots.isEmpty())
    }

    @Test
    fun acceptsCompleteMappings() {
        val result = TournamentCloudRestorationMapper.mapSnapshot(groupPayload(3, mappings = completeMappings()))

        assertTrue(result is TournamentCloudRestorationMappingResult.Success)
        assertEquals(36, (result as TournamentCloudRestorationMappingResult.Success).value.pairingLobbySlots.size)
    }

    @Test
    fun rejectsPartialMappings() {
        assertInvalid(groupPayload(3, mappings = completeMappings().dropLast(1)))
    }

    @Test
    fun rejectsMissingPairingMapping() {
        val mappings = completeMappings().filterNot { it.pairingKey == "B:C" }
        assertInvalid(groupPayload(3, mappings = mappings))
    }

    @Test
    fun rejectsExtraPairingMapping() {
        val extra = (1..12).map { lobby ->
            GroupPairingLobbySlotUploadPayload(TOURNAMENT_ID, "A:D", lobby, lobby)
        }
        assertInvalid(groupPayload(3, mappings = completeMappings() + extra))
    }

    @Test
    fun rejectsMissingLobbySlotMapping() {
        assertInvalid(groupPayload(3, mappings = completeMappings().filterNot { it.lobbySlotNumber == 12 }))
    }

    @Test
    fun rejectsDuplicateLobbySlotMapping() {
        val mappings = completeMappings().toMutableList()
        val first = mappings.first()
        mappings[1] = first.copy(lobbySlotNumber = 2, teamSlotNumber = 13)
        assertInvalid(groupPayload(3, mappings = mappings))
    }

    @Test
    fun rejectsDuplicateCanonicalMappingWithinPairing() {
        val mappings = completeMappings().toMutableList()
        val first = mappings.first()
        mappings[1] = mappings[1].copy(teamSlotNumber = first.teamSlotNumber)
        assertInvalid(groupPayload(3, mappings = mappings))
    }

    @Test
    fun rejectsOutOfCapacityCanonicalMapping() {
        val mappings = completeMappings().map { mapping ->
            if (mapping.pairingKey == "A:B" && mapping.lobbySlotNumber == 1) {
                mapping.copy(teamSlotNumber = 19)
            } else {
                mapping
            }
        }
        assertInvalid(groupPayload(3, mappings = mappings))
    }

    @Test
    fun rejectsMappingToMissingTeamSlot() {
        val slots = groupSlots(3).filterNot { it.slotNumber == 18 }
        assertInvalid(groupPayload(3, slots = slots, mappings = completeMappings()))
    }

    @Test
    fun rejectsMappingToBlankTeamSlot() {
        val slots = groupSlots(3).map { slot ->
            if (slot.slotNumber == 18) slot.copy(teamName = " ") else slot
        }
        val mappings = completeMappings().map { mapping ->
            if (mapping.pairingKey == "A:B" && mapping.lobbySlotNumber == 1) {
                mapping.copy(teamSlotNumber = 18)
            } else {
                mapping
            }
        }
        assertInvalid(groupPayload(3, slots = slots, mappings = mappings))
    }

    @Test
    fun acceptsSameCanonicalTeamAcrossDifferentPairings() {
        val mappings = completeMappings().map { mapping ->
            if (mapping.pairingKey == "B:C" && mapping.lobbySlotNumber == 1) {
                mapping.copy(teamSlotNumber = 18)
            } else {
                mapping
            }
        }
        val result = TournamentCloudRestorationMapper.mapSnapshot(groupPayload(3, mappings = mappings))

        assertTrue(result is TournamentCloudRestorationMappingResult.Success)
    }

    @Test
    fun rejectsMappingWithWrongTournamentId() {
        val mappings = completeMappings().mapIndexed { index, mapping ->
            if (index == 0) mapping.copy(tournamentId = OTHER_TOURNAMENT_ID) else mapping
        }
        assertInvalid(groupPayload(3, mappings = mappings))
    }

    @Test
    fun rejectsStandardMappingRows() {
        val standardMapping = GroupPairingLobbySlotUploadPayload(
            TOURNAMENT_ID,
            "A:B",
            1,
            1,
        )
        assertInvalid(payloads().copy(pairingLobbySlots = listOf(standardMapping)))
    }

    private fun assertInvalid(payloads: TournamentCloudRestorationPayloads) {
        assertFalse(TournamentCloudRestorationMapper.mapSnapshot(payloads) is TournamentCloudRestorationMappingResult.Success)
    }

    private fun groupPayload(
        groupCount: Int,
        slots: List<TeamSlotUploadPayload> = groupSlots(groupCount),
        mappings: List<GroupPairingLobbySlotUploadPayload> = completeMappings(groupCount),
    ) = payloads().copy(
        tournament = payloads().tournament.copy(
            format = "group_rotation",
            groupCount = groupCount,
            selectedGroupPairings = defaultGroupPairings(groupCount).map { pairing ->
                GroupPairingUploadPayload(
                    firstGroup = pairing.firstGroup.name,
                    secondGroup = pairing.secondGroup.name,
                    pairingKey = pairing.canonicalKey,
                )
            },
        ),
        teamSlots = slots,
        players = emptyList(),
        pairingLobbySlots = mappings,
    )

    private fun groupSlots(groupCount: Int) = (1..(groupCount * 6)).map { slotNumber ->
        val group = TournamentGroup.entries[(slotNumber - 1) / 6]
        TeamSlotUploadPayload(
            id = TournamentCloudIdentity.teamSlotId(UUID.fromString(TOURNAMENT_ID), slotNumber),
            tournamentId = TOURNAMENT_ID,
            slotNumber = slotNumber,
            teamName = "Team $slotNumber",
            status = "draft",
            group = group.name,
        )
    }

    private fun completeMappings(groupCount: Int = 3) = defaultGroupPairings(groupCount).flatMap { pairing ->
        val canonicalOrder = when {
            groupCount == 4 -> (1..12).toList()
            pairing.canonicalKey == "A:B" -> listOf(14, 3, 18, 7, 1, 2, 4, 5, 6, 13, 15, 16)
            pairing.canonicalKey == "B:C" -> (1..12).toList()
            else -> (7..18).toList()
        }
        canonicalOrder.mapIndexed { index, teamSlotNumber ->
            GroupPairingLobbySlotUploadPayload(
                tournamentId = TOURNAMENT_ID,
                pairingKey = pairing.canonicalKey,
                lobbySlotNumber = index + 1,
                teamSlotNumber = teamSlotNumber,
            )
        }
    }

    private fun payloads() = TournamentCloudRestorationPayloads(
        tournament = TournamentUploadPayload(
            id = TOURNAMENT_ID,
            ownerId = OWNER_ID,
            name = "Summer Cup",
            stageName = "Organizer",
            organizerContact = "123",
            status = "draft",
            revision = 1,
        ),
        teamSlots = listOf(
            TeamSlotUploadPayload(
                id = TournamentCloudIdentity.teamSlotId(UUID.fromString(TOURNAMENT_ID), 1),
                tournamentId = TOURNAMENT_ID,
                slotNumber = 1,
                teamName = "Alpha",
                status = "draft",
            ),
        ),
        players = listOf(
            PlayerUploadPayload(
                id = TournamentCloudIdentity.playerId(UUID.fromString(TOURNAMENT_ID), 1, 1),
                teamSlotId = TournamentCloudIdentity.teamSlotId(UUID.fromString(TOURNAMENT_ID), 1),
                displayName = "Player One",
                normalizedName = "Player One",
            ),
        ),
    )

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        const val OWNER_ID = "22222222-2222-2222-2222-222222222222"
        const val OTHER_TOURNAMENT_ID = "33333333-3333-3333-3333-333333333333"
    }
}
