package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceIssue
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceIssueCode
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingAssignment
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityWriteResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.defaultGroupPairings
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupRotationV4OverallImageInputReaderTest {
    @Test
    fun validThreeGroupUuidHistoryIsAvailableWithSparseSnapshot() = runBlocking {
        val tournament = tournament(3)
        val match = finalizedMatch("uuid-3", GroupPairing(TournamentGroup.A, TournamentGroup.B))
        val result = reader(
            matchResult = loadedUuidSnapshot(tournament, match),
        ).read(tournament, listOf(match), slots(tournament, 18))

        assertEquals(GroupRotationV4OverallImageReadResult.Available::class, result::class)
    }

    @Test
    fun validFourGroupUuidHistoryIsAvailable() = runBlocking {
        val tournament = tournament(4)
        val match = finalizedMatch("uuid-4", GroupPairing(TournamentGroup.A, TournamentGroup.D))
        val result = reader(
            matchResult = loadedUuidSnapshot(tournament, match),
        ).read(tournament, listOf(match), slots(tournament, 24))

        assertEquals(GroupRotationV4OverallImageReadResult.Available::class, result::class)
    }

    @Test
    fun completeNumericHistoryIsThePositiveLegacyEvidence() = runBlocking {
        val tournament = tournament(3)
        val match = legacyMatch(tournament.id, GroupPairing(TournamentGroup.A, TournamentGroup.C))
        val result = reader().read(tournament, listOf(match), slots(tournament, 18))

        assertEquals(GroupRotationV4OverallImageReadResult.Legacy, result)
    }

    @Test
    fun missingUuidHeaderWithoutLegacyEvidenceFailsClosed() = runBlocking {
        val tournament = tournament(3)
        val match = finalizedMatch("missing-header", GroupPairing(TournamentGroup.A, TournamentGroup.B))
        val result = reader().read(tournament, listOf(match), slots(tournament, 18))

        assertEquals(GroupRotationV4OverallImageReadResult.Invalid, result)
    }

    @Test
    fun invalidUuidChildDoesNotFallBackToNumericHistory() = runBlocking {
        val tournament = tournament(3)
        val match = finalizedMatch("invalid-uuid", GroupPairing(TournamentGroup.A, TournamentGroup.B))
        val result = reader(
            matchResult = GroupRotationMatchPersistenceResult.Invalid(
                listOf(
                    GroupRotationMatchPersistenceIssue(
                        code = GroupRotationMatchPersistenceIssueCode.TEAM_UUID_NOT_FOUND,
                        matchId = match.id,
                    ),
                ),
            ),
        ).read(tournament, listOf(match), slots(tournament, 18))

        assertEquals(GroupRotationV4OverallImageReadResult.Invalid, result)
    }

    @Test
    fun finalizedLegacyAndUuidHistoryCannotBeMergedWithoutAuthoritativeBridge() = runBlocking {
        val tournament = tournament(3)
        val legacy = legacyMatch(tournament.id, GroupPairing(TournamentGroup.A, TournamentGroup.C))
        val uuid = finalizedMatch("uuid", GroupPairing(TournamentGroup.A, TournamentGroup.B))
        val result = reader(
            matchResults = mapOf(
                uuid.id to loadedUuidSnapshot(tournament, uuid),
            ),
        ).read(tournament, listOf(legacy, uuid), slots(tournament, 18))

        assertEquals(GroupRotationV4OverallImageReadResult.Invalid, result)
    }

    @Test
    fun legacyDraftWithNoResultsDoesNotInvalidateUuidHistory() = runBlocking {
        val tournament = tournament(3)
        val legacyDraft = finalizedMatch(
            id = "legacy-draft",
            pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C),
        ).copy(status = MatchStatus.DRAFT)
        val uuid = finalizedMatch("uuid", GroupPairing(TournamentGroup.A, TournamentGroup.B))
        val result = reader(
            matchResults = mapOf(uuid.id to loadedUuidSnapshot(tournament, uuid)),
        ).read(tournament, listOf(legacyDraft, uuid), slots(tournament, 18))

        assertEquals(GroupRotationV4OverallImageReadResult.Available::class, result::class)
    }

    private fun reader(
        matchResult: GroupRotationMatchPersistenceResult = missingHeader(),
        matchResults: Map<String, GroupRotationMatchPersistenceResult> = emptyMap(),
    ) = DefaultGroupRotationV4OverallImageInputReader(
        teamIdentityRepository = IdentityRepository(),
        matchIdentityRepository = MatchIdentityRepository(
            defaultResult = matchResult,
            results = matchResults,
        ),
    )

    private fun tournament(groupCount: Int) = Tournament(
        id = TOURNAMENT_ID,
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.CONFIRMED,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = groupCount,
        selectedGroupPairings = defaultGroupPairings(groupCount),
    )

    private fun slots(tournament: Tournament, count: Int) = (1..count).map { slotNumber ->
        TeamSlot.create(tournament.id, slotNumber)
    }

    private fun finalizedMatch(id: String, pairing: GroupPairing) = Match(
        id = id,
        tournamentId = TOURNAMENT_ID,
        matchNumber = 1,
        date = LocalDate.of(2026, 10, 15),
        mapName = "Bermuda",
        status = MatchStatus.FINALIZED,
        groupPairing = pairing,
    )

    private fun legacyMatch(tournamentId: String, pairing: GroupPairing) = Match(
        id = "legacy",
        tournamentId = tournamentId,
        matchNumber = 1,
        date = LocalDate.of(2026, 10, 15),
        mapName = "Bermuda",
        status = MatchStatus.FINALIZED,
        placements = (1..12).map { slotNumber ->
            MatchPlacement(slotNumber, slotNumber)
        },
        kills = (1..12).map { slotNumber ->
            MatchKill(slotNumber, slotNumber)
        },
        participantResults = (1..12).map { slotNumber ->
            MatchParticipantResult(
                teamSlotNumber = slotNumber,
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = slotNumber,
                kills = slotNumber,
            )
        },
        groupPairing = pairing,
    )

    private fun loadedUuidSnapshot(
        tournament: Tournament,
        match: Match,
    ): GroupRotationMatchPersistenceResult.Loaded = GroupRotationMatchPersistenceResult.Loaded(
        GroupRotationMatchPersistenceSnapshot(
            identity = GroupRotationMatchIdentitySnapshot(
                tournamentId = tournament.id,
                pairing = checkNotNull(match.groupPairing),
                teams = listOf(
                    GroupRotationMatchTeamIdentity(
                        lobbySlotNumber = 2,
                        teamUUID = TEAM_UUID,
                        displayName = "Alpha",
                    ),
                ),
            ),
            participantResults = listOf(
                GroupRotationMatchParticipantResult(
                    teamUUID = TEAM_UUID,
                    participationStatus = MatchParticipationStatus.PARTICIPATED,
                    placement = 1,
                    kills = 3,
                ),
            ),
        ),
    )

    private fun missingHeader() = GroupRotationMatchPersistenceResult.Invalid(
        listOf(
            GroupRotationMatchPersistenceIssue(
                code = GroupRotationMatchPersistenceIssueCode.MATCH_IDENTITY_NOT_SAVED,
            ),
        ),
    )

    private class IdentityRepository : GroupRotationTeamIdentityLocalRepository {
        override suspend fun read(tournament: Tournament) = GroupRotationTeamIdentityReadResult.Loaded(
            GroupRotationTeamIdentityLocalSnapshot(
                teams = listOf(
                    GroupRotationTeamIdentity(
                        tournamentId = tournament.id,
                        teamUUID = TEAM_UUID,
                        displayName = "Alpha",
                        normalizedName = "alpha",
                    ),
                ),
                assignments = listOf(
                    GroupRotationPairingAssignment(
                        tournamentId = tournament.id,
                        pairingKey = GroupPairing(TournamentGroup.A, TournamentGroup.B),
                        lobbySlotNumber = 2,
                        teamUUID = TEAM_UUID,
                    ),
                ),
            ),
        )

        override suspend fun savePairing(
            tournament: Tournament,
            pairingKey: GroupPairing,
            entries: List<com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamIdentityEntry>,
        ): GroupRotationTeamIdentityWriteResult = GroupRotationTeamIdentityWriteResult.Unsupported

        override suspend fun renameTeam(
            tournament: Tournament,
            teamUUID: String,
            displayName: String,
        ): GroupRotationTeamIdentityWriteResult = GroupRotationTeamIdentityWriteResult.Unsupported
    }

    private class MatchIdentityRepository(
        private val defaultResult: GroupRotationMatchPersistenceResult,
        private val results: Map<String, GroupRotationMatchPersistenceResult>,
    ) : com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentityPersistenceRepository {
        override suspend fun save(
            tournament: Tournament,
            matchId: String,
            snapshot: GroupRotationMatchIdentitySnapshot,
            participantResults: List<GroupRotationMatchParticipantResult>,
        ): GroupRotationMatchPersistenceResult = error("not used")

        override suspend fun read(
            tournament: Tournament,
            matchId: String,
        ): GroupRotationMatchPersistenceResult = results[matchId] ?: defaultResult
    }

    private companion object {
        const val TOURNAMENT_ID = "tournament-id"
        const val TEAM_UUID = "00000000-0000-0000-0000-000000000001"
    }
}
