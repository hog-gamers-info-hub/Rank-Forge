package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.defaultGroupPairings
import com.hoggamers.rankforge.domain.tournament.formatDerivedSlots
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentCloudUploadMapperTest {
    @Test
    fun mapsTournamentAndFixedSlotsWithApprovedCloudFields() {
        val snapshot = snapshot()

        val result = TournamentCloudUploadMapper.map(snapshot, OWNER_ID)

        assertTrue(result is TournamentCloudUploadMappingResult.Success)
        val payloads = (result as TournamentCloudUploadMappingResult.Success).payloads
        assertEquals(TENANT_ID, payloads.tournament.id)
        assertEquals(OWNER_ID, payloads.tournament.ownerId)
        assertEquals("Organizer", payloads.tournament.stageName)
        assertEquals("draft", payloads.tournament.status)
        assertEquals(TeamSlot.SLOT_NUMBERS.toList(), payloads.teamSlots.map { it.slotNumber })
        assertEquals("Alpha", payloads.teamSlots.first { it.slotNumber == 1 }.teamName)
        assertTrue(payloads.teamSlots.all { it.status == "draft" })
    }

    @Test
    fun serializedTournamentPayloadDoesNotContainRemovedTournamentFields() {
        val result = TournamentCloudUploadMapper.map(snapshot(), OWNER_ID) as TournamentCloudUploadMappingResult.Success

        val json = Json.encodeToString(result.payloads.tournament)

        assertFalse(json.contains("tournament_date"))
        assertFalse(json.contains("organization_name"))
    }

    @Test
    fun mapsStableTeamSlotAndPlayerIdsAndNormalizesNames() {
        val snapshot = snapshot()

        val result = TournamentCloudUploadMapper.map(snapshot, OWNER_ID) as TournamentCloudUploadMappingResult.Success
        val payloads = result.payloads
        val slotId = UUID.nameUUIDFromBytes(
            "rank-forge:team-slot:$TENANT_ID:1".toByteArray(StandardCharsets.UTF_8),
        ).toString()
        val playerId = UUID.nameUUIDFromBytes(
            "rank-forge:player:$TENANT_ID:1:1".toByteArray(StandardCharsets.UTF_8),
        ).toString()

        assertEquals(slotId, payloads.teamSlots.first { it.slotNumber == 1 }.id)
        assertEquals(slotId, payloads.players.single().teamSlotId)
        assertEquals(playerId, payloads.players.single().id)
        assertEquals(" Alpha Player ", payloads.players.single().displayName)
        assertEquals("Alpha Player", payloads.players.single().normalizedName)
    }

    @Test
    fun repeatedMappingProducesIdenticalPayloads() {
        val snapshot = snapshot()

        val first = TournamentCloudUploadMapper.map(snapshot, OWNER_ID)
        val second = TournamentCloudUploadMapper.map(snapshot, OWNER_ID)

        assertEquals(first, second)
    }

    @Test
    fun mapsThreeGroupRotationConfigurationAndAllPermanentSlots() {
        val tournament = snapshot().tournament.copy(
            format = TournamentFormat.GROUP_ROTATION,
            groupCount = 3,
            selectedGroupPairings = defaultGroupPairings(3),
        )
        val result = TournamentCloudUploadMapper.map(
            TournamentCloudUploadSnapshot(
                tournament = tournament,
                slots = tournament.formatDerivedSlots().map { it.copy(teamName = "Team ${it.slotNumber}") },
                rosters = emptyMap(),
            ),
            OWNER_ID,
        ) as TournamentCloudUploadMappingResult.Success

        assertEquals("group_rotation", result.payloads.tournament.format)
        assertEquals(3, result.payloads.tournament.groupCount)
        assertEquals(3, result.payloads.tournament.selectedGroupPairings.size)
        assertEquals(18, result.payloads.teamSlots.size)
        assertEquals("C", result.payloads.teamSlots.single { it.slotNumber == 13 }.group)
        assertEquals(
            TournamentCloudIdentity.teamSlotId(UUID.fromString(TENANT_ID), 18),
            result.payloads.teamSlots.single { it.slotNumber == 18 }.id,
        )
        assertTrue(result.payloads.pairingLobbySlots.isEmpty())
    }

    @Test
    fun mapsPersistedGroupRotationLobbySlotsWithoutDerivingTheirIdentityFromPosition() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        val teamSlots = listOf(14, 3, 18, 7, 13, 1, 15, 4, 16, 8, 17, 6)
        val result = TournamentCloudUploadMapper.map(
            groupRotationSnapshot(
                pairings = listOf(pairing),
                mappings = mappingFor(pairing, teamSlots),
            ),
            OWNER_ID,
        ) as TournamentCloudUploadMappingResult.Success

        assertEquals(
            teamSlots,
            result.payloads.pairingLobbySlots.map { it.teamSlotNumber },
        )
        assertEquals((1..12).toList(), result.payloads.pairingLobbySlots.map { it.lobbySlotNumber })
        assertEquals(pairing.canonicalKey, result.payloads.pairingLobbySlots.first().pairingKey)
    }

    @Test
    fun allowsTheSameCanonicalTeamSlotInDifferentSelectedPairings() {
        val first = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        val second = GroupPairing(TournamentGroup.A, TournamentGroup.C)

        val result = TournamentCloudUploadMapper.map(
            groupRotationSnapshot(
                pairings = listOf(first, second),
                mappings = mappingFor(first, (1..12).toList()) +
                    mappingFor(second, (1..12).toList()),
            ),
            OWNER_ID,
        )

        assertTrue(result is TournamentCloudUploadMappingResult.Success)
    }

    @Test
    fun rejectsGroupRotationMappingsForAnUnselectedPairing() {
        val selected = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        val unselected = GroupPairing(TournamentGroup.A, TournamentGroup.C)

        val result = TournamentCloudUploadMapper.map(
            groupRotationSnapshot(
                pairings = listOf(selected),
                mappings = mappingFor(unselected, (1..12).toList()),
            ),
            OWNER_ID,
        )

        assertEquals(TournamentCloudUploadMappingResult.Invalid, result)
    }

    @Test
    fun rejectsIncompleteGroupRotationPairingMappings() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.B)

        val result = TournamentCloudUploadMapper.map(
            groupRotationSnapshot(
                pairings = listOf(pairing),
                mappings = mappingFor(pairing, (1..11).toList()),
            ),
            OWNER_ID,
        )

        assertEquals(TournamentCloudUploadMappingResult.Invalid, result)
    }

    @Test
    fun rejectsDuplicateLobbyPositionWithinOnePairing() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        val mappings = mappingFor(pairing, (1..12).toList()).toMutableList().apply {
            this[1] = this[1].copy(lobbySlotNumber = 1)
        }

        assertEquals(
            TournamentCloudUploadMappingResult.Invalid,
            TournamentCloudUploadMapper.map(
                groupRotationSnapshot(pairings = listOf(pairing), mappings = mappings),
                OWNER_ID,
            ),
        )
    }

    @Test
    fun rejectsDuplicateCanonicalTeamWithinOnePairing() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        val mappings = mappingFor(pairing, (1..12).toList()).toMutableList().apply {
            this[1] = this[1].copy(teamSlotNumber = 1)
        }

        assertEquals(
            TournamentCloudUploadMappingResult.Invalid,
            TournamentCloudUploadMapper.map(
                groupRotationSnapshot(pairings = listOf(pairing), mappings = mappings),
                OWNER_ID,
            ),
        )
    }

    @Test
    fun rejectsMissingSelectedPairingAndExtraPairing() {
        val first = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        val second = GroupPairing(TournamentGroup.A, TournamentGroup.C)

        assertEquals(
            TournamentCloudUploadMappingResult.Invalid,
            TournamentCloudUploadMapper.map(
                groupRotationSnapshot(
                    pairings = listOf(first, second),
                    mappings = mappingFor(first, (1..12).toList()),
                ),
                OWNER_ID,
            ),
        )
        assertEquals(
            TournamentCloudUploadMappingResult.Invalid,
            TournamentCloudUploadMapper.map(
                groupRotationSnapshot(
                    pairings = listOf(first),
                    mappings = mappingFor(first, (1..12).toList()) +
                        mappingFor(second, (1..12).toList()),
                ),
                OWNER_ID,
            ),
        )
    }

    @Test
    fun rejectsMappingWithWrongTournamentId() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        val mappings = mappingFor(pairing, (1..12).toList()).mapIndexed { index, mapping ->
            if (index == 0) mapping.copy(tournamentId = "another-tournament") else mapping
        }

        assertEquals(
            TournamentCloudUploadMappingResult.Invalid,
            TournamentCloudUploadMapper.map(
                groupRotationSnapshot(pairings = listOf(pairing), mappings = mappings),
                OWNER_ID,
            ),
        )
    }

    @Test
    fun acceptsCanonicalTwentyFourForFourGroupTournament() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        val result = TournamentCloudUploadMapper.map(
            groupRotationSnapshot(
                groupCount = 4,
                pairings = listOf(pairing),
                mappings = mappingFor(pairing, (13..24).toList()),
            ),
            OWNER_ID,
        )

        assertTrue(result is TournamentCloudUploadMappingResult.Success)
    }

    @Test
    fun rejectsCanonicalTeamSlotOutsideThreeGroupCapacity() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        val mappings = mappingFor(pairing, listOf(19) + (1..11).toList())

        val result = TournamentCloudUploadMapper.map(
            groupRotationSnapshot(pairings = listOf(pairing), mappings = mappings),
            OWNER_ID,
        )

        assertEquals(TournamentCloudUploadMappingResult.Invalid, result)
    }

    @Test
    fun rejectsMappedTeamSlotWithoutANonBlankPersistedTeamName() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        val snapshot = groupRotationSnapshot(
            pairings = listOf(pairing),
            mappings = mappingFor(pairing, (1..12).toList()),
        ).copy(
            slots = groupRotationSnapshot().slots.map { slot ->
                if (slot.slotNumber == 1) slot.copy(teamName = " ") else slot
            },
        )

        assertEquals(
            TournamentCloudUploadMappingResult.Invalid,
            TournamentCloudUploadMapper.map(snapshot, OWNER_ID),
        )
    }

    @Test
    fun rejectsMappedTeamSlotMissingFromThePersistedSnapshot() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        val snapshot = groupRotationSnapshot(
            pairings = listOf(pairing),
            mappings = mappingFor(pairing, (1..12).toList()),
        ).copy(
            slots = groupRotationSnapshot().slots.filterNot { it.slotNumber == 1 },
        )

        assertEquals(
            TournamentCloudUploadMappingResult.Invalid,
            TournamentCloudUploadMapper.map(snapshot, OWNER_ID),
        )
    }

    @Test
    fun rejectsPersistedMappingsForStandardTournament() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        val standard = snapshot().copy(
            pairingLobbySlots = listOf(
                GroupRotationPairingLobbySlot(TENANT_ID, pairing, 1, 1),
            ),
        )

        assertEquals(
            TournamentCloudUploadMappingResult.Invalid,
            TournamentCloudUploadMapper.map(standard, OWNER_ID),
        )
    }

    @Test
    fun rejectsInvalidLocalTournamentUuid() {
        val result = TournamentCloudUploadMapper.map(
            snapshot().copy(tournament = snapshot().tournament.copy(id = "not-a-uuid")),
            OWNER_ID,
        )

        assertEquals(TournamentCloudUploadMappingResult.Invalid, result)
    }

    @Test
    fun preservesConfirmedAsCloudDraft() {
        val result = TournamentCloudUploadMapper.map(
            snapshot().copy(tournament = snapshot().tournament.copy(status = TournamentStatus.CONFIRMED)),
            OWNER_ID,
        ) as TournamentCloudUploadMappingResult.Success

        assertEquals("draft", result.payloads.tournament.status)
    }

    private fun snapshot(): TournamentCloudUploadSnapshot = TournamentCloudUploadSnapshot(
        tournament = Tournament(
            id = TENANT_ID,
            name = "Summer Cup",
            stageName = "Organizer",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
        ),
        slots = listOf(
            TeamSlot.create(TENANT_ID, 1, "Alpha"),
        ),
        rosters = mapOf(
            1 to listOf(RosterPlayer.create(TENANT_ID, 1, " Alpha Player ")),
        ),
    )

    private fun groupRotationSnapshot(
        groupCount: Int = 3,
        pairings: List<GroupPairing> = defaultGroupPairings(groupCount),
        mappings: List<GroupRotationPairingLobbySlot> = emptyList(),
    ): TournamentCloudUploadSnapshot {
        val tournament = snapshot().tournament.copy(
            format = TournamentFormat.GROUP_ROTATION,
            groupCount = groupCount,
            selectedGroupPairings = pairings,
        )
        return TournamentCloudUploadSnapshot(
            tournament = tournament,
            slots = tournament.formatDerivedSlots().map { it.copy(teamName = "Team ${it.slotNumber}") },
            rosters = emptyMap(),
            pairingLobbySlots = mappings,
        )
    }

    private fun mappingFor(
        pairing: GroupPairing,
        teamSlots: List<Int>,
    ): List<GroupRotationPairingLobbySlot> = teamSlots.mapIndexed { index, teamSlotNumber ->
        GroupRotationPairingLobbySlot(
            tournamentId = TENANT_ID,
            pairing = pairing,
            lobbySlotNumber = index + 1,
            teamSlotNumber = teamSlotNumber,
        )
    }

    private companion object {
        const val TENANT_ID = "11111111-1111-1111-1111-111111111111"
        const val OWNER_ID = "22222222-2222-2222-2222-222222222222"
    }
}
