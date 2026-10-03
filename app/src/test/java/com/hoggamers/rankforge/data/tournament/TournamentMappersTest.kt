package com.hoggamers.rankforge.data.tournament

import com.hoggamers.rankforge.data.local.MatchEntity
import com.hoggamers.rankforge.data.local.MatchKillEntity
import com.hoggamers.rankforge.data.local.MatchParticipantResultEntity
import com.hoggamers.rankforge.data.local.MatchPlacementEntity
import com.hoggamers.rankforge.data.local.MatchResultAggregate
import com.hoggamers.rankforge.data.local.TournamentEntity
import com.hoggamers.rankforge.data.local.TournamentSummaryProjection
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentMappersTest {
    @Test
    fun matchPairingRoundTripsThroughRoomEntityMapping() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        val match = Match(
            id = "match-1",
            tournamentId = "tournament-1",
            matchNumber = 1,
            date = LocalDate.of(2026, 10, 3),
            mapName = "Bermuda",
            status = MatchStatus.DRAFT,
            groupPairing = pairing,
        )

        val entity = match.toEntity()
        val restored = entity.toDomain()

        assertEquals(pairing.canonicalKey, entity.groupPairingKey)
        assertEquals(pairing, restored.groupPairing)
    }

    @Test
    fun knownAndUnknownOwnersSurviveTournamentRoomRoundTrips() {
        val tournament = tournament(ownerUserId = "user-a")
        val entity = tournament.toEntity(creationOrder = 1L)

        assertEquals("user-a", entity.ownerUserId)
        assertEquals("user-a", entity.toDomain().ownerUserId)
        assertNull(entity.copy(ownerUserId = null).toDomain().ownerUserId)
    }

    @Test
    fun groupRotationConfigurationAndCanonicalPairingsSurviveRoomMapping() {
        val tournament = Tournament(
            id = "tournament-1",
            name = "Summer Cup",
            stageName = "Organizer",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
            format = TournamentFormat.GROUP_ROTATION,
            groupCount = 3,
            selectedGroupPairings = listOf(
                GroupPairing.of(TournamentGroup.C, TournamentGroup.A),
            ),
        )
        val pairing = tournament.selectedGroupPairings.single()

        assertEquals(TournamentFormat.GROUP_ROTATION.name, tournament.toEntity(1L).format)
        assertEquals(3, tournament.toEntity(1L).groupCount)
        assertEquals(
            tournament,
            tournament.toEntity(1L).toDomain(listOf(pairing.toEntity(tournament.id).toDomain())),
        )
        assertEquals("A:C", pairing.toEntity(tournament.id).pairingKey)
    }

    @Test
    fun summaryMappingPreservesKnownOwner() {
        val summary = TournamentSummaryProjection(
            id = "tournament-1",
            name = "Summer Cup",
            stageName = "Organizer",
            organizerContactNumber = "123",
            status = "DRAFT",
            totalTeams = 2,
            totalMatches = 3,
            lastUpdatedEpochMillis = 1_800_000_000_000L,
            ownerUserId = "user-a",
        ).toDomain(emptyList())

        assertEquals("user-a", summary.tournament.ownerUserId)
        assertEquals(TournamentFormat.STANDARD, summary.tournament.format)
        assertNull(summary.tournament.groupCount)
        assertTrue(summary.tournament.selectedGroupPairings.isEmpty())
    }

    @Test
    fun groupRotationSummaryMappingUsesPersistedPairings() {
        val pairings = listOf(
            GroupPairing.of(TournamentGroup.A, TournamentGroup.B),
            GroupPairing.of(TournamentGroup.B, TournamentGroup.C),
            GroupPairing.of(TournamentGroup.A, TournamentGroup.C),
        )
        val summary = TournamentSummaryProjection(
            id = "tournament-1",
            name = "Summer Cup",
            stageName = "Organizer",
            organizerContactNumber = "123",
            status = "DRAFT",
            totalTeams = 18,
            totalMatches = 1,
            lastUpdatedEpochMillis = 1_800_000_000_000L,
            ownerUserId = "user-a",
            format = TournamentFormat.GROUP_ROTATION.name,
            groupCount = 3,
        ).toDomain(pairings)

        assertEquals(TournamentFormat.GROUP_ROTATION, summary.tournament.format)
        assertEquals(3, summary.tournament.groupCount)
        assertEquals(pairings, summary.tournament.selectedGroupPairings)
    }

    @Test
    fun completeFinalizedAggregateMapsWithoutFabricatingParticipantRows() {
        val result = completeAggregate().toDomainResult(Json)

        val mapped = result as MatchResultAggregateMapping.Complete
        assertEquals(
            listOf(1),
            mapped.match.participantResults.map { it.teamSlotNumber },
        )
        assertEquals(3, mapped.match.participantResults.single().kills)
    }

    @Test
    fun missingFinalizedKillIsIncompleteWithoutThrowingOrDefaulting() {
        val result = completeAggregate(kills = emptyList()).toDomainResult(Json)

        assertEquals(MatchResultAggregateMapping.Incomplete, result)
    }

    @Test
    fun missingFinalizedParticipantResultsAreIncompleteAndNotReconstructed() = runTest {
        var rereads = 0

        val result = completeAggregate(participantResults = emptyList()).toDomainResult(Json)
        val confirmed = completeAggregate(participantResults = emptyList())
            .toMatchWithConfirmation(Json) {
                rereads++
                completeAggregate(participantResults = emptyList())
            }

        assertEquals(MatchResultAggregateMapping.Incomplete, result)
        assertNull(confirmed)
        assertEquals(1, rereads)
    }

    @Test
    fun incompleteFirstReadUsesExactlyOneConfirmationReadWhenSecondIsComplete() = runTest {
        var rereads = 0

        val result = incompleteAggregate().toMatchWithConfirmation(Json) {
            rereads++
            completeAggregate()
        }

        assertEquals(1, rereads)
        assertTrue(result != null)
    }

    @Test
    fun incompleteSecondReadFailsSafelyAfterOneConfirmationRead() = runTest {
        var rereads = 0

        val result = incompleteAggregate().toMatchWithConfirmation(Json) {
            rereads++
            incompleteAggregate()
        }

        assertNull(result)
        assertEquals(1, rereads)
    }

    @Test
    fun invalidFirstReadDoesNotTriggerConfirmationRead() = runTest {
        var rereads = 0
        val invalid = completeAggregate().copy(
            match = completeAggregate().match.copy(status = "INVALID"),
        )

        val result = invalid.toMatchWithConfirmation(Json) {
            rereads++
            completeAggregate()
        }

        assertNull(result)
        assertEquals(0, rereads)
    }

    @Test
    fun failedMatchDoesNotProduceAShortenedTournamentList() {
        val valid = (completeAggregate().toDomainResult(Json) as MatchResultAggregateMapping.Complete).match

        assertNull(listOf(valid, null, valid).toCompleteMatchListOrNull())
        assertEquals(listOf(valid, valid), listOf(valid, valid).toCompleteMatchListOrNull())
    }

    private fun tournament(ownerUserId: String?): Tournament = Tournament(
        id = "tournament-1",
        name = "Summer Cup",
        stageName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        ownerUserId = ownerUserId,
    )

    private fun completeAggregate(
        kills: List<MatchKillEntity> = listOf(MatchKillEntity("match-1", 1, 3)),
        participantResults: List<MatchParticipantResultEntity> = listOf(
            MatchParticipantResultEntity(
                matchId = "match-1",
                teamSlotNumber = 1,
                participationStatus = MatchParticipationStatus.PARTICIPATED.name,
                placement = 1,
                kills = 3,
            ),
        ),
    ) = MatchResultAggregate(
        match = MatchEntity("match-1", "tournament-1", 1, "2026-08-23", "Bermuda", "FINALIZED"),
        placements = listOf(MatchPlacementEntity("match-1", 1, 1)),
        kills = kills,
        participantResults = participantResults,
        corrections = emptyList(),
    )

    private fun incompleteAggregate() = completeAggregate(kills = emptyList())
}
