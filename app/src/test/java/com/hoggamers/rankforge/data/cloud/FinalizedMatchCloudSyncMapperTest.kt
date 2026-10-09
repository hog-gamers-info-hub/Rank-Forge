package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncSnapshot
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchLobbyTeamIdentity
import com.hoggamers.rankforge.domain.tournament.MatchTeamIdentityContext
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.formatDerivedSlots
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalizedMatchCloudSyncMapperTest {
    @Test
    fun mapsOnlyFinalizedMatchesAndAllConfirmedResultRowsWithStableIds() {
        val result = FinalizedMatchCloudSyncMapper.map(snapshot()) as FinalizedMatchCloudSyncMappingResult.Success
        val payloads = result.payloads
        val expectedMatchId = UUID.nameUUIDFromBytes(
            "rank-forge:match:$TOURNAMENT_ID:finalized-match".toByteArray(StandardCharsets.UTF_8),
        ).toString()
        val expectedTeamSlotId = UUID.nameUUIDFromBytes(
            "rank-forge:team-slot:$TOURNAMENT_ID:1".toByteArray(StandardCharsets.UTF_8),
        ).toString()
        val expectedResultId = UUID.nameUUIDFromBytes(
            "rank-forge:match-result:$expectedMatchId:$expectedTeamSlotId".toByteArray(StandardCharsets.UTF_8),
        ).toString()

        assertEquals(1, payloads.matches.size)
        assertEquals(expectedMatchId, payloads.matches.single().id)
        assertEquals("finalized", payloads.matches.single().status)
        assertEquals(12, payloads.matchResults.size)
        assertEquals(expectedResultId, payloads.matchResults.first { it.teamSlotId == expectedTeamSlotId }.id)
        assertEquals(1, payloads.matchResults.first { it.teamSlotId == expectedTeamSlotId }.placement)
        assertEquals(0, payloads.matchResults.first { it.teamSlotId == expectedTeamSlotId }.kills)
        assertEquals("confirmed", payloads.matchResults.first().reviewStatus)
        assertEquals(TournamentFormat.STANDARD, payloads.tournamentFormat)
    }

    @Test
    fun repeatedMappingProducesIdenticalPayloadsWithoutDuplicateIntent() {
        assertEquals(
            FinalizedMatchCloudSyncMapper.map(snapshot()),
            FinalizedMatchCloudSyncMapper.map(snapshot()),
        )
    }

    @Test
    fun rejectsInvalidTournamentUuidAndIncompleteFinalizedRows() {
        val invalidId = snapshot().copy(tournament = snapshot().tournament.copy(id = "not-a-uuid"))
        val incomplete = snapshot().copy(
            matches = listOf(snapshot().matches.first().copy(placements = emptyList())),
        )

        assertEquals(FinalizedMatchCloudSyncMappingResult.Invalid, FinalizedMatchCloudSyncMapper.map(invalidId))
        assertEquals(FinalizedMatchCloudSyncMappingResult.Invalid, FinalizedMatchCloudSyncMapper.map(incomplete))
    }

    @Test
    fun mapsTenTeamParticipationWithoutInactiveRows() {
        val result = FinalizedMatchCloudSyncMapper.map(
            snapshot(
                teamSlots = namedTeamSlots(10),
                match = finalizedMatch((1..10).toList()),
            ),
        ) as FinalizedMatchCloudSyncMappingResult.Success

        assertEquals(10, result.payloads.matchResults.size)
        assertEquals((1..10).toList(), result.payloads.matchResults.map { it.placement })
        assertTrue(result.payloads.matchResults.all { it.teamSlotId == teamSlotId(requireNotNull(it.placement)) })
    }

    @Test
    fun mapsExplicitSparseNoShowSnapshotWithoutDerivingCurrentParticipation() {
        val snapshotMatch = finalizedMatch((1..4).toList()).copy(
            placements = listOf(MatchPlacement(1, 1), MatchPlacement(2, 2)),
            kills = listOf(MatchKill(1, 3), MatchKill(2, 1), MatchKill(6, 0)),
            participantResults = listOf(
                MatchParticipantResult(1, MatchParticipationStatus.PARTICIPATED, 1, 3),
                MatchParticipantResult(2, MatchParticipationStatus.PARTICIPATED, 2, 1),
                MatchParticipantResult(6, MatchParticipationStatus.NO_SHOW, null, 0),
            ),
        )
        val result = FinalizedMatchCloudSyncMapper.map(
            snapshot(teamSlots = namedTeamSlots(12), match = snapshotMatch),
        ) as FinalizedMatchCloudSyncMappingResult.Success

        assertEquals(listOf(1, 2, 6), result.payloads.matchResults.map {
            TeamSlot.SLOT_NUMBERS.first { slot -> it.teamSlotId == teamSlotId(slot) }
        })
        assertEquals(listOf("PARTICIPATED", "PARTICIPATED", "NO_SHOW"), result.payloads.matchResults.map { it.participationStatus })
        assertEquals(null, result.payloads.matchResults.last().placement)
    }

    @Test
    fun rejectsGroupRotationFinalizedUntilV2CloudMappingSupportExists() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        val groupTournament = snapshot().tournament.copy(
            format = TournamentFormat.GROUP_ROTATION,
            groupCount = 4,
            selectedGroupPairings = listOf(pairing),
        )
        val eligibleSlots = (1..6).toList() + (13..18).toList()
        val groupMatch = Match(
            id = "finalized-group-match",
            tournamentId = TOURNAMENT_ID,
            matchNumber = 1,
            date = LocalDate.of(2026, 7, 24),
            mapName = "Bermuda",
            status = MatchStatus.FINALIZED,
            groupPairing = pairing,
            placements = eligibleSlots.mapIndexed { index, slot -> MatchPlacement(slot, index + 1) },
            kills = eligibleSlots.map { slot -> MatchKill(slot, slot % 3) },
        )

        val result = FinalizedMatchCloudSyncMapper.map(
            FinalizedMatchCloudSyncSnapshot(
                tournament = groupTournament,
                teamSlots = groupTournament.formatDerivedSlots()
                    .map { slot -> slot.copy(teamName = "Team ${slot.slotNumber}") },
                matches = listOf(groupMatch),
            ),
        )

        assertEquals(FinalizedMatchCloudSyncMappingResult.Invalid, result)
    }

    @Test
    fun mapsGroupRotationFinalizedSnapshotUsingExplicitContextIncludingCanonicalSlotSeven() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        val tournament = groupRotationTournament(pairing)
        val match = groupFinalizedMatch(tournament, pairing)

        val result = FinalizedMatchCloudSyncMapper.map(
            FinalizedMatchCloudSyncSnapshot(
                tournament = tournament,
                teamSlots = namedGroupTeamSlots(tournament),
                matches = listOf(match),
                identityContextsByMatchId = mapOf(
                    match.id to identityContext(tournament, pairing),
                ),
            ),
        ) as FinalizedMatchCloudSyncMappingResult.Success

        assertEquals(12, result.payloads.matchResults.size)
        assertTrue(
            result.payloads.matchResults.any {
                it.teamSlotId == teamSlotId(7)
            },
        )
        assertEquals(TournamentFormat.GROUP_ROTATION, result.payloads.tournamentFormat)
    }

    @Test
    fun rejectsGroupRotationFinalizedSnapshotWhenContextIsMissingOrMismatched() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        val tournament = groupRotationTournament(pairing)
        val match = groupFinalizedMatch(tournament, pairing)
        val base = FinalizedMatchCloudSyncSnapshot(
            tournament = tournament,
            teamSlots = namedGroupTeamSlots(tournament),
            matches = listOf(match),
        )

        assertEquals(FinalizedMatchCloudSyncMappingResult.Invalid, FinalizedMatchCloudSyncMapper.map(base))
        assertEquals(
            FinalizedMatchCloudSyncMappingResult.Invalid,
            FinalizedMatchCloudSyncMapper.map(
                base.copy(
                    identityContextsByMatchId = mapOf(
                        match.id to identityContext(
                            tournament,
                            GroupPairing(TournamentGroup.A, TournamentGroup.B),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun rejectsGroupRotationFinalizedSnapshotWhenParticipantsAreOutsideContextIncompleteOrSlotsMalformed() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        val tournament = groupRotationTournament(pairing)
        val context = identityContext(tournament, pairing)

        fun snapshotFor(match: Match, teamSlots: List<TeamSlot> = namedGroupTeamSlots(tournament)) =
            FinalizedMatchCloudSyncSnapshot(
                tournament = tournament,
                teamSlots = teamSlots,
                matches = listOf(match),
                identityContextsByMatchId = mapOf(match.id to context),
            )

        assertEquals(
            FinalizedMatchCloudSyncMappingResult.Invalid,
            FinalizedMatchCloudSyncMapper.map(
                snapshotFor(groupFinalizedMatch(tournament, pairing, resultSlots = (1..11).toList() + 13)),
            ),
        )
        assertEquals(
            FinalizedMatchCloudSyncMappingResult.Invalid,
            FinalizedMatchCloudSyncMapper.map(
                snapshotFor(groupFinalizedMatch(tournament, pairing, resultSlots = (1..11).toList())),
            ),
        )
        assertEquals(
            FinalizedMatchCloudSyncMappingResult.Invalid,
            FinalizedMatchCloudSyncMapper.map(
                snapshotFor(
                    groupFinalizedMatch(tournament, pairing),
                    teamSlots = namedGroupTeamSlots(tournament).dropLast(1),
                ),
            ),
        )
    }

    @Test
    fun rejectsParticipantAwareResultShapeFailures() {
        val tenTeamSlots = namedTeamSlots(10)
        val cases = listOf(
            finalizedMatch((1..10).toList(), placements = (1..9).toList() + 11),
            finalizedMatch((1..10).toList(), placements = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 9)),
            finalizedMatch((1..10).toList(), kills = (0..8).toList() + -1),
        )

        cases.forEach { match ->
            assertEquals(
                FinalizedMatchCloudSyncMappingResult.Invalid,
                FinalizedMatchCloudSyncMapper.map(snapshot(teamSlots = tenTeamSlots, match = match)),
            )
        }
    }

    @Test
    fun rejectsGapZeroParticipantsAndMismatchedTeamSlotContext() {
        assertTrue(
            FinalizedMatchCloudSyncMapper.map(
                snapshot(
                    teamSlots = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
                        TeamSlot(TOURNAMENT_ID, slotNumber, if (slotNumber <= 5 || slotNumber == 7) "Team $slotNumber" else "")
                    },
                    match = finalizedMatch((1..5).toList()),
                ),
            ) is FinalizedMatchCloudSyncMappingResult.Success,
        )
        assertEquals(
            FinalizedMatchCloudSyncMappingResult.Invalid,
            FinalizedMatchCloudSyncMapper.map(
                snapshot(teamSlots = namedTeamSlots(0), match = finalizedMatch(emptyList())),
            ),
        )
        assertEquals(
            FinalizedMatchCloudSyncMappingResult.Invalid,
            FinalizedMatchCloudSyncMapper.map(
                snapshot(
                    teamSlots = namedTeamSlots(12).map { slot ->
                        if (slot.slotNumber == 1) slot.copy(tournamentId = OTHER_TOURNAMENT_ID) else slot
                    },
                ),
            ),
        )
    }

    private fun snapshot(
        teamSlots: List<TeamSlot> = namedTeamSlots(12),
        match: Match = finalizedMatch((1..12).toList()),
    ) = FinalizedMatchCloudSyncSnapshot(
        tournament = Tournament(
            id = TOURNAMENT_ID,
            name = "Summer Cup",
            stageName = "Organizer",
            organizerContactNumber = "123",
            status = TournamentStatus.CONFIRMED,
        ),
        teamSlots = teamSlots,
        matches = listOf(
            match,
            Match(
                id = "draft-match",
                tournamentId = TOURNAMENT_ID,
                matchNumber = 2,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Purgatory",
                status = MatchStatus.DRAFT,
            ),
        ),
    )

    private fun finalizedMatch(
        resultSlots: List<Int>,
        placements: List<Int> = resultSlots,
        kills: List<Int> = resultSlots.map { it - 1 },
    ) = Match(
        id = "finalized-match",
        tournamentId = TOURNAMENT_ID,
        matchNumber = 1,
        date = LocalDate.of(2026, 7, 24),
        mapName = "Bermuda",
        status = MatchStatus.FINALIZED,
        placements = resultSlots.mapIndexed { index, slot -> MatchPlacement(slot, placements[index]) },
        kills = resultSlots.mapIndexed { index, slot -> MatchKill(slot, kills[index]) },
    )

    private fun namedTeamSlots(activeCount: Int) = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
        TeamSlot(
            tournamentId = TOURNAMENT_ID,
            slotNumber = slotNumber,
            teamName = if (slotNumber <= activeCount) "Team $slotNumber" else "",
        )
    }

    private fun teamSlotId(slotNumber: Int): String =
        TournamentCloudIdentity.teamSlotId(UUID.fromString(TOURNAMENT_ID), slotNumber)

    private fun groupRotationTournament(pairing: GroupPairing) = snapshot().tournament.copy(
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = 4,
        selectedGroupPairings = listOf(pairing),
    )

    private fun groupFinalizedMatch(
        tournament: Tournament,
        pairing: GroupPairing,
        resultSlots: List<Int> = (1..12).toList(),
    ) = Match(
        id = "group-finalized-match",
        tournamentId = tournament.id,
        matchNumber = 1,
        date = LocalDate.of(2026, 7, 24),
        mapName = "Bermuda",
        status = MatchStatus.FINALIZED,
        placements = resultSlots.mapIndexed { index, slot ->
            MatchPlacement(slot, index + 1)
        },
        kills = resultSlots.mapIndexed { index, slot ->
            MatchKill(slot, index)
        },
        participantResults = resultSlots.mapIndexed { index, slot ->
            MatchParticipantResult(
                teamSlotNumber = slot,
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = index + 1,
                kills = index,
            )
        },
        groupPairing = pairing,
    )

    private fun namedGroupTeamSlots(tournament: Tournament) =
        tournament.formatDerivedSlots().map { it.copy(teamName = "Team " + it.slotNumber) }

    private fun identityContext(
        tournament: Tournament,
        pairing: GroupPairing,
        eligibleSlots: List<Int> = (1..12).toList(),
    ) = MatchTeamIdentityContext(
        tournamentId = tournament.id,
        pairing = pairing,
        teams = eligibleSlots.mapIndexed { index, slotNumber ->
            MatchLobbyTeamIdentity(index + 1, slotNumber)
        },
    )

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        const val OTHER_TOURNAMENT_ID = "22222222-2222-2222-2222-222222222222"
    }
}
