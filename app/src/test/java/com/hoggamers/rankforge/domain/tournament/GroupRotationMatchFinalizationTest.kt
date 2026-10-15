package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRotationMatchFinalizationTest {
    private val validator = GroupRotationMatchFinalizationValidator()

    @Test
    fun validSparsePairingFinalizesWithoutPhantomTeams() {
        val snapshot = snapshot(
            team(2, ALPHA_UUID, "Alpha"),
            team(9, BETA_UUID, "Beta"),
        )

        val issues = validator.validate(
            snapshot,
            listOf(
                participated(ALPHA_UUID, 1, 4),
                participated(BETA_UUID, 2, 3),
            ),
        )

        assertTrue(issues.isEmpty())
    }

    @Test
    fun finalizedResultsRetainOriginalUuids() {
        val snapshot = snapshot(team(4, ALPHA_UUID, "Alpha"))

        val issues = validator.validate(snapshot, listOf(participated(ALPHA_UUID, 1, 2)))

        assertTrue(issues.isEmpty())
        assertEquals(ALPHA_UUID, snapshot.teams.single().teamUUID)
    }

    @Test
    fun sameUuidCanBeFinalizedInDifferentPairings() {
        val first = snapshot(team(2, ALPHA_UUID, "Alpha"), pairing = GroupPairing.fromCanonicalKey("A:B"))
        val second = snapshot(team(9, ALPHA_UUID, "Alpha"), pairing = GroupPairing.fromCanonicalKey("A:C"))

        assertTrue(validator.validate(first, listOf(participated(ALPHA_UUID, 1, 1))).isEmpty())
        assertTrue(validator.validate(second, listOf(participated(ALPHA_UUID, 1, 1))).isEmpty())
        assertEquals(ALPHA_UUID, first.teams.single().teamUUID)
        assertEquals(ALPHA_UUID, second.teams.single().teamUUID)
    }

    @Test
    fun registryRenameDoesNotChangeResultOwnership() {
        val snapshot = snapshot(team(3, ALPHA_UUID, "Alpha Renamed"))

        val issues = validator.validate(snapshot, listOf(participated(ALPHA_UUID, 1, 0)))

        assertTrue(issues.isEmpty())
        assertEquals(ALPHA_UUID, snapshot.teams.single().teamUUID)
        assertEquals("Alpha Renamed", snapshot.teams.single().displayName)
    }

    @Test
    fun persistedFinalizedSnapshotAllowsBlankCurrentDisplayName() {
        val snapshot = snapshot(team(3, ALPHA_UUID, ""))
        val results = listOf(participated(ALPHA_UUID, 1, 0))

        assertTrue(validator.validatePersistedSnapshot(snapshot, results).isEmpty())
        assertTrue(
            validator.validate(snapshot, results)
                .any { it.code == GroupRotationMatchFinalizationIssueCode.INVALID_IDENTITY_SNAPSHOT },
        )
    }

    @Test
    fun duplicateUuidResultsAreRejected() {
        val snapshot = snapshot(team(1, ALPHA_UUID, "Alpha"), team(2, BETA_UUID, "Beta"))

        val issues = validator.validate(
            snapshot,
            listOf(
                participated(ALPHA_UUID, 1, 1),
                participated(ALPHA_UUID, 2, 2),
            ),
        )

        assertTrue(issues.any { it.code == GroupRotationMatchFinalizationIssueCode.DUPLICATE_RESULT })
        assertTrue(issues.any { it.code == GroupRotationMatchFinalizationIssueCode.MISSING_RESULT })
    }

    @Test
    fun invalidPlacementAndKillsAreRejected() {
        val snapshot = snapshot(team(1, ALPHA_UUID, "Alpha"), team(2, BETA_UUID, "Beta"))

        val placementIssues = validator.validate(
            snapshot,
            listOf(
                participated(ALPHA_UUID, 3, 1),
                participated(BETA_UUID, 1, 2),
            ),
        )
        val killIssues = validator.validate(
            snapshot,
            listOf(
                GroupRotationMatchParticipantResult(
                    teamUUID = ALPHA_UUID,
                    participationStatus = MatchParticipationStatus.PARTICIPATED,
                    placement = 1,
                    kills = -1,
                ),
                participated(BETA_UUID, 2, 2),
            ),
        )

        assertTrue(placementIssues.any { it.code == GroupRotationMatchFinalizationIssueCode.INVALID_PLACEMENT })
        assertTrue(killIssues.any { it.code == GroupRotationMatchFinalizationIssueCode.INVALID_KILLS })
    }

    @Test
    fun allNoShowsAreRejectedAndNoShowValuesRemainStrict() {
        val snapshot = snapshot(team(1, ALPHA_UUID, "Alpha"))

        val issues = validator.validate(
            snapshot,
            listOf(
                GroupRotationMatchParticipantResult(
                    teamUUID = ALPHA_UUID,
                    participationStatus = MatchParticipationStatus.NO_SHOW,
                    placement = null,
                    kills = 0,
                ),
            ),
        )

        assertTrue(issues.any { it.code == GroupRotationMatchFinalizationIssueCode.NO_PARTICIPATED_TEAM })
        assertFalse(validator.validate(snapshot, listOf(
            GroupRotationMatchParticipantResult(
                ALPHA_UUID,
                MatchParticipationStatus.NO_SHOW,
                null,
                1,
            ),
        )).isEmpty())
    }

    @Test
    fun missingOrForeignResultUuidIsRejected() {
        val snapshot = snapshot(team(1, ALPHA_UUID, "Alpha"))

        val issues = validator.validate(snapshot, listOf(participated(FOREIGN_UUID, 1, 1)))

        assertTrue(issues.any { it.code == GroupRotationMatchFinalizationIssueCode.RESULT_TEAM_NOT_IN_MATCH })
        assertTrue(issues.any { it.code == GroupRotationMatchFinalizationIssueCode.MISSING_RESULT })
    }

    @Test
    fun missingIdentitySnapshotIsRejected() {
        val issues = validator.validate(
            snapshot(),
            emptyList(),
        )

        assertTrue(issues.any { it.code == GroupRotationMatchFinalizationIssueCode.EMPTY_IDENTITY_SNAPSHOT })
    }

    @Test
    fun participatingPlacementsMustBeGapFree() {
        val snapshot = snapshot(team(1, ALPHA_UUID, "Alpha"), team(2, BETA_UUID, "Beta"))

        val issues = validator.validate(
            snapshot,
            listOf(
                participated(ALPHA_UUID, 1, 0),
                participated(BETA_UUID, 3, 0),
            ),
        )

        assertTrue(issues.any { it.code == GroupRotationMatchFinalizationIssueCode.INVALID_PLACEMENT })
        assertTrue(issues.any { it.code == GroupRotationMatchFinalizationIssueCode.PLACEMENTS_NOT_GAP_FREE })
    }

    private fun snapshot(
        vararg teams: GroupRotationMatchTeamIdentity,
        pairing: GroupPairing = GroupPairing.fromCanonicalKey("A:B"),
    ) = GroupRotationMatchIdentitySnapshot(
        tournamentId = TOURNAMENT_ID,
        pairing = pairing,
        teams = teams.toList(),
    )

    private fun team(
        lobbySlot: Int,
        uuid: String,
        name: String,
    ) = GroupRotationMatchTeamIdentity(lobbySlot, uuid, name)

    private fun participated(uuid: String, placement: Int, kills: Int) =
        GroupRotationMatchParticipantResult(
            teamUUID = uuid,
            participationStatus = MatchParticipationStatus.PARTICIPATED,
            placement = placement,
            kills = kills,
        )

    private companion object {
        const val TOURNAMENT_ID = "tournament-1"
        const val ALPHA_UUID = "team-alpha-uuid"
        const val BETA_UUID = "team-beta-uuid"
        const val FOREIGN_UUID = "foreign-uuid"
    }
}
