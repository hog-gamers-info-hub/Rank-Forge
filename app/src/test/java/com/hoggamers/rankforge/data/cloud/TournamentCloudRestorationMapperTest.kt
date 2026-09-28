package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.TeamSlot
import java.util.UUID
import org.junit.Assert.assertEquals
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
        assertEquals("PointIQ", snapshot.tournament.organizationName)
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
    fun legacyNullOrganizationNameRestoresSafely() {
        val result = TournamentCloudRestorationMapper.mapSnapshot(
            payloads().copy(tournament = payloads().tournament.copy(organizationName = null)),
        ) as TournamentCloudRestorationMappingResult.Success

        assertEquals(null, result.value.tournament.organizationName)
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

    private fun payloads() = TournamentCloudRestorationPayloads(
        tournament = TournamentUploadPayload(
            id = TOURNAMENT_ID,
            ownerId = OWNER_ID,
            name = "Summer Cup",
            stageName = "Organizer",
            organizerContact = "123",
            status = "draft",
            revision = 1,
            organizationName = "PointIQ",
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
    }
}
