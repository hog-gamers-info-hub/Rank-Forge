package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadSnapshot
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
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
        assertEquals("2026-07-24", payloads.tournament.tournamentDate)
        assertEquals("draft", payloads.tournament.status)
        assertEquals(TeamSlot.SLOT_NUMBERS.toList(), payloads.teamSlots.map { it.slotNumber })
        assertEquals("Alpha", payloads.teamSlots.first { it.slotNumber == 1 }.teamName)
        assertTrue(payloads.teamSlots.all { it.status == "draft" })
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
            date = LocalDate.of(2026, 7, 24),
            organizerName = "Organizer",
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

    private companion object {
        const val TENANT_ID = "11111111-1111-1111-1111-111111111111"
        const val OWNER_ID = "22222222-2222-2222-2222-222222222222"
    }
}
