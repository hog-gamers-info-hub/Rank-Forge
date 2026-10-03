package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.defaultGroupPairings
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentResultImageModelBuilderTest {
    private val builder = TournamentResultImageModelBuilder()

    @Test
    fun standardOverallImageModelContainsAllTwelvePermanentSlots() {
        val result = builder.build(
            input(
                tournament = standardTournament(),
                teamSlots = permanentSlots(12),
                matches = listOf(standardMatch()),
            ),
        )

        val model = success(result)
        assertEquals(12, model.rows.size)
        assertEquals((1..12).toList(), model.rows.map { row -> row.rank })
    }

    @Test
    fun threeGroupOverallImageModelContainsAllEighteenSlotsIncludingRestingTeams() {
        val result = builder.build(
            input(
                tournament = groupTournament(3),
                teamSlots = permanentSlots(18),
                matches = listOf(
                    groupMatch(
                        pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C),
                        participantResults = listOf(
                            participated(13, placement = 1, kills = 3),
                            participated(2, placement = 2),
                        ),
                    ),
                ),
            ),
        )

        val model = success(result)
        assertEquals(18, model.rows.size)
        assertEquals((1..18).toSet(), model.rows.map { row -> row.rank }.toSet())
        assertEquals(15, model.rows.first { row -> row.teamName == "Team 13" }.totalPoints)
        assertEquals(0, model.rows.first { row -> row.teamName == "Team 18" }.totalPoints)
        assertEquals(0, model.rows.first { row -> row.teamName == "Team 18" }.matchesPlayed)
    }

    @Test
    fun fourGroupOverallImageModelContainsAllTwentyFourPermanentSlots() {
        val result = builder.build(
            input(
                tournament = groupTournament(4),
                teamSlots = permanentSlots(24),
                matches = listOf(
                    groupMatch(
                        pairing = GroupPairing(TournamentGroup.A, TournamentGroup.D),
                        participantResults = listOf(
                            participated(19, placement = 1, kills = 1),
                        ),
                    ),
                ),
            ),
        )

        val model = success(result)
        assertEquals(24, model.rows.size)
        assertEquals((1..24).toSet(), model.rows.map { row -> row.rank }.toSet())
        assertEquals("Team 19", model.rows.first().teamName)
    }

    @Test
    fun noShowAndNeverPlayedTeamsRemainZeroWithoutFakeParticipantRows() {
        val result = builder.build(
            input(
                tournament = groupTournament(3),
                teamSlots = permanentSlots(18),
                matches = listOf(
                    groupMatch(
                        pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C),
                        participantResults = listOf(
                            participated(1, placement = 1),
                            MatchParticipantResult(13, MatchParticipationStatus.NO_SHOW, null, 0),
                        ),
                    ),
                ),
            ),
        )

        val model = success(result)
        val noShow = model.rows.first { row -> row.teamName == "Team 13" }
        val neverPlayed = model.rows.first { row -> row.teamName == "Team 18" }
        assertEquals(0, noShow.totalPoints)
        assertEquals(0, noShow.matchesPlayed)
        assertEquals(0, neverPlayed.totalPoints)
        assertEquals(0, neverPlayed.matchesPlayed)
    }

    @Test
    fun selectedPairingRejectsOutOfGroupParticipantInsteadOfReplacingItsIdentity() {
        val result = builder.build(
            input(
                tournament = groupTournament(3),
                teamSlots = permanentSlots(18),
                matches = listOf(
                    groupMatch(
                        pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C),
                        participantResults = listOf(
                            participated(7, placement = 1),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(
            (result as TournamentResultImageModelBuildResult.Failure).failures.contains(
                TournamentResultImageModelFailure.INVALID_FINALIZED_MATCH,
            ),
        )
    }

    @Test
    fun missingPermanentTeamNameFailsClosed() {
        val result = builder.build(
            input(
                tournament = groupTournament(3),
                teamSlots = permanentSlots(18).map { slot ->
                    if (slot.slotNumber == 18) slot.copy(teamName = " ") else slot
                },
                matches = listOf(groupMatch(GroupPairing(TournamentGroup.A, TournamentGroup.C))),
            ),
        )

        assertTrue(
            (result as TournamentResultImageModelBuildResult.Failure).failures.contains(
                TournamentResultImageModelFailure.MISSING_TEAM_IDENTITY,
            ),
        )
    }

    private fun success(result: TournamentResultImageModelBuildResult): TournamentResultExportModel =
        (result as TournamentResultImageModelBuildResult.Success).model

    private fun input(
        tournament: Tournament,
        teamSlots: List<TeamSlot>,
        matches: List<Match>,
    ) = TournamentCsvExportInput(
        tournament = tournament,
        matches = matches,
        teamSlots = teamSlots,
        rosterPlayers = emptyList(),
    )

    private fun standardTournament() = Tournament(
        id = TOURNAMENT_ID,
        name = "Standard Cup",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.CONFIRMED,
    )

    private fun groupTournament(groupCount: Int) = Tournament(
        id = TOURNAMENT_ID,
        name = "Rotation Cup",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.CONFIRMED,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = groupCount,
        selectedGroupPairings = defaultGroupPairings(groupCount),
    )

    private fun permanentSlots(count: Int): List<TeamSlot> = (1..count).map { slotNumber ->
        TeamSlot(
            tournamentId = TOURNAMENT_ID,
            slotNumber = slotNumber,
            teamName = "Team $slotNumber",
            group = if (count == 12) {
                null
            } else when (slotNumber) {
                in 1..6 -> TournamentGroup.A
                in 7..12 -> TournamentGroup.B
                in 13..18 -> TournamentGroup.C
                else -> TournamentGroup.D
            },
        )
    }

    private fun standardMatch() = Match(
        id = "standard-match",
        tournamentId = TOURNAMENT_ID,
        matchNumber = 1,
        date = LocalDate.of(2026, 10, 3),
        mapName = "Bermuda",
        status = MatchStatus.FINALIZED,
        participantResults = (1..12).map { slotNumber ->
            participated(slotNumber, placement = slotNumber)
        },
    )

    private fun groupMatch(
        pairing: GroupPairing,
        participantResults: List<MatchParticipantResult> = listOf(participated(1, 1)),
    ) = Match(
        id = "group-match-${pairing.canonicalKey}",
        tournamentId = TOURNAMENT_ID,
        matchNumber = 1,
        date = LocalDate.of(2026, 10, 3),
        mapName = "Bermuda",
        status = MatchStatus.FINALIZED,
        participantResults = participantResults,
        groupPairing = pairing,
    )

    private fun participated(
        slotNumber: Int,
        placement: Int,
        kills: Int = 0,
    ) = MatchParticipantResult(
        teamSlotNumber = slotNumber,
        participationStatus = MatchParticipationStatus.PARTICIPATED,
        placement = placement,
        kills = kills,
    )

    private companion object {
        const val TOURNAMENT_ID = "tournament-id"
    }
}
