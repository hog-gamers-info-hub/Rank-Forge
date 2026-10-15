package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyGroupRotationFinalizedRestorationValidatorTest {
    @Test
    fun acceptsCanonicalSlotSevenInSelectedACFinalizedHistoryWithoutGroupInference() {
        val tournament = tournament()

        assertTrue(
            LegacyGroupRotationFinalizedRestorationValidator.isValid(
                tournament = tournament,
                persistedTeamSlots = slots(tournament.id, 18),
                matches = listOf(finalizedMatch(tournament.id, GroupPairing(TournamentGroup.A, TournamentGroup.C))),
            ),
        )
    }

    @Test
    fun acceptsTwelveDistinctCanonicalIdentities() {
        val tournament = tournament()

        assertTrue(
            LegacyGroupRotationFinalizedRestorationValidator.isValid(
                tournament = tournament,
                persistedTeamSlots = slots(tournament.id, 18),
                matches = listOf(
                    finalizedMatch(
                        tournament.id,
                        GroupPairing(TournamentGroup.A, TournamentGroup.C),
                        teamSlots = (1..12).toList(),
                    ),
                ),
            ),
        )
    }

    @Test
    fun rejectsThirteenDistinctCanonicalIdentities() {
        val tournament = tournament()
        val validMatch = finalizedMatch(
            tournament.id,
            GroupPairing(TournamentGroup.A, TournamentGroup.C),
            teamSlots = (1..12).toList(),
        )

        assertFalse(
            LegacyGroupRotationFinalizedRestorationValidator.isValid(
                tournament = tournament,
                persistedTeamSlots = slots(tournament.id, 18),
                matches = listOf(
                    validMatch.copy(
                        kills = validMatch.kills + MatchKill(teamSlotNumber = 13, kills = 1),
                    ),
                ),
            ),
        )
    }

    @Test
    fun rejectsPlacementOutsideTwelveEvenWhenCanonicalTeamSlotIsValid() {
        val tournament = tournament()
        val validMatch = finalizedMatch(
            tournament.id,
            GroupPairing(TournamentGroup.A, TournamentGroup.C),
            teamSlots = (1..12).toList(),
        )

        assertFalse(
            LegacyGroupRotationFinalizedRestorationValidator.isValid(
                tournament = tournament,
                persistedTeamSlots = slots(tournament.id, 18),
                matches = listOf(
                    validMatch.copy(
                        placements = validMatch.placements.mapIndexed { index, placement ->
                            if (index == 0) placement.copy(position = 13) else placement
                        },
                    ),
                ),
            ),
        )
    }

    @Test
    fun rejectsCanonicalSlotOutsideThreeGroupCapacity() {
        val tournament = tournament()

        assertFalse(
            LegacyGroupRotationFinalizedRestorationValidator.isValid(
                tournament = tournament,
                persistedTeamSlots = slots(tournament.id, 18),
                matches = listOf(
                    finalizedMatch(
                        tournament.id,
                        GroupPairing(TournamentGroup.A, TournamentGroup.C),
                        teamSlots = listOf(7, 19),
                    ),
                ),
            ),
        )
    }

    @Test
    fun rejectsUnselectedPairingAndDraftMatches() {
        val tournament = tournament()

        assertFalse(
            LegacyGroupRotationFinalizedRestorationValidator.isValid(
                tournament = tournament,
                persistedTeamSlots = slots(tournament.id, 18),
                matches = listOf(
                    finalizedMatch(
                        tournament.id,
                        GroupPairing(TournamentGroup.A, TournamentGroup.B),
                    ),
                ),
            ),
        )
        assertFalse(
            LegacyGroupRotationFinalizedRestorationValidator.isValid(
                tournament = tournament,
                persistedTeamSlots = slots(tournament.id, 18),
                matches = listOf(
                    finalizedMatch(
                        tournament.id,
                        GroupPairing(TournamentGroup.A, TournamentGroup.C),
                    ).copy(status = MatchStatus.DRAFT),
                ),
            ),
        )
    }

    private fun tournament() = Tournament(
        id = TOURNAMENT_ID,
        name = "Historical Group Rotation",
        stageName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = 3,
        selectedGroupPairings = listOf(GroupPairing(TournamentGroup.A, TournamentGroup.C)),
    )

    private fun slots(tournamentId: String, count: Int) = (1..count).map { slotNumber ->
        TeamSlot.create(
            tournamentId = tournamentId,
            slotNumber = slotNumber,
            group = TournamentGroup.entries[(slotNumber - 1) / 6],
        )
    }

    private fun finalizedMatch(
        tournamentId: String,
        pairing: GroupPairing,
        teamSlots: List<Int> = listOf(7, 13),
    ) = Match(
        id = "match-\${pairing.canonicalKey}",
        tournamentId = tournamentId,
        matchNumber = 1,
        date = LocalDate.of(2026, 8, 15),
        mapName = "Bermuda",
        status = MatchStatus.FINALIZED,
        placements = teamSlots.mapIndexed { index, slot ->
            MatchPlacement(slot, index + 1)
        },
        kills = teamSlots.mapIndexed { index, slot ->
            MatchKill(slot, index + 1)
        },
        participantResults = teamSlots.mapIndexed { index, slot ->
            MatchParticipantResult(
                teamSlotNumber = slot,
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = index + 1,
                kills = index + 1,
            )
        },
        groupPairing = pairing,
    )

    private companion object {
        const val TOURNAMENT_ID = "historical-group-rotation"
    }
}
