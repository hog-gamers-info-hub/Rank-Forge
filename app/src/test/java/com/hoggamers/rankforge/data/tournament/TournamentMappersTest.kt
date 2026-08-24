package com.hoggamers.rankforge.data.tournament

import com.hoggamers.rankforge.data.local.TournamentEntity
import com.hoggamers.rankforge.data.local.TournamentSummaryProjection
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TournamentMappersTest {
    @Test
    fun knownAndUnknownOwnersSurviveTournamentRoomRoundTrips() {
        val tournament = tournament(ownerUserId = "user-a")
        val entity = tournament.toEntity(creationOrder = 1L)

        assertEquals("user-a", entity.ownerUserId)
        assertEquals("user-a", entity.toDomain().ownerUserId)
        assertNull(entity.copy(ownerUserId = null).toDomain().ownerUserId)
    }

    @Test
    fun summaryMappingPreservesKnownOwner() {
        val summary = TournamentSummaryProjection(
            id = "tournament-1",
            name = "Summer Cup",
            date = "2026-08-23",
            organizerName = "Organizer",
            organizerContactNumber = "123",
            status = "DRAFT",
            totalTeams = 2,
            totalMatches = 3,
            lastUpdatedEpochMillis = 1_800_000_000_000L,
            ownerUserId = "user-a",
        ).toDomain()

        assertEquals("user-a", summary.tournament.ownerUserId)
    }

    private fun tournament(ownerUserId: String?): Tournament = Tournament(
        id = "tournament-1",
        name = "Summer Cup",
        date = LocalDate.of(2026, 8, 23),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        ownerUserId = ownerUserId,
    )
}
