package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.matching.ResultLobbySlotDecisionReason
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotDecisionStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrPlayerSlot
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchTeamIdentity
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRotationMatchOcrIdentityAdapterTest {
    @Test
    fun correctSavedUuidForLobbyPosition() {
        val projection = loadedProjection(
            snapshot = snapshot(team(lobbySlot = 7, teamUUID = "uuid-alpha", name = "Alpha")),
            lobbyOcr = lobbyResult(7 to listOf("A1", "A2", "A3", "A4")),
        )

        val projectedTeam = projection.teams.single()
        assertEquals(MATCH_ID, projectedTeam.matchId)
        assertEquals(7, projectedTeam.lobbySlotNumber)
        assertEquals("uuid-alpha", projectedTeam.teamUUID)
        assertEquals("Alpha", projectedTeam.displayName)
        assertEquals(listOf("A1", "A2", "A3", "A4"), projectedTeam.playerNames)
    }

    @Test
    fun sameUuidInDifferentPairingsAtDifferentLobbyPositionsResolvesIndependently() {
        val uuid = "uuid-shared"
        val ab = loadedProjection(
            snapshot = snapshot(team(lobbySlot = 2, teamUUID = uuid, name = "Shared"), pairing = AB),
            lobbyOcr = lobbyResult(2 to listOf("AB1", "AB2", "AB3", "AB4")),
        )
        val ac = loadedProjection(
            snapshot = snapshot(team(lobbySlot = 11, teamUUID = uuid, name = "Shared"), pairing = AC),
            lobbyOcr = lobbyResult(11 to listOf("AC1", "AC2", "AC3", "AC4")),
        )

        assertEquals(listOf(2), ab.teams.map { it.lobbySlotNumber })
        assertEquals(listOf(11), ac.teams.map { it.lobbySlotNumber })
        assertEquals(uuid, ab.teams.single().teamUUID)
        assertEquals(uuid, ac.teams.single().teamUUID)
    }

    @Test
    fun differentPlayersBetweenMatchesDoNotChangeSavedUuidOrLeakPreviousPlayers() {
        val first = loadedProjection(
            snapshot = snapshot(team(lobbySlot = 4, teamUUID = "uuid-alpha", name = "Alpha")),
            lobbyOcr = lobbyResult(4 to listOf("FIRST1", "FIRST2", "FIRST3", "FIRST4")),
        )
        val second = loadedProjection(
            snapshot = snapshot(team(lobbySlot = 4, teamUUID = "uuid-alpha", name = "Alpha")),
            lobbyOcr = lobbyResult(4 to listOf("SECOND1", "SECOND2", "SECOND3", "SECOND4")),
        )

        assertEquals("uuid-alpha", first.teams.single().teamUUID)
        assertEquals("uuid-alpha", second.teams.single().teamUUID)
        assertEquals(listOf("FIRST1", "FIRST2", "FIRST3", "FIRST4"), first.teams.single().playerNames)
        assertEquals(listOf("SECOND1", "SECOND2", "SECOND3", "SECOND4"), second.teams.single().playerNames)

        val suggestion = loadedSuggestions(
            second,
            resultRow(1, listOf("SECOND1", "SECOND2", "SECOND3", "SECOND4")),
        ).single()
        assertEquals("uuid-alpha", suggestion.automaticTeamUUID)
    }

    @Test
    fun sparseLobbyPositionsExposeOnlyRegisteredTeams() {
        val projection = loadedProjection(
            snapshot = snapshot(
                team(lobbySlot = 2, teamUUID = "uuid-two", name = "Two"),
                team(lobbySlot = 10, teamUUID = "uuid-ten", name = "Ten"),
            ),
            lobbyOcr = lobbyResult(
                2 to listOf("T2-1", "T2-2", "T2-3", "T2-4"),
                10 to listOf("T10-1", "T10-2", "T10-3", "T10-4"),
            ),
        )

        assertEquals(listOf(2, 10), projection.teams.map { it.lobbySlotNumber })
        assertTrue(projection.teams.none { it.lobbySlotNumber == 1 })
    }

    @Test
    fun missingPlayerOcrStillAllowsManualSelection() {
        val projection = loadedProjection(
            snapshot = snapshot(team(lobbySlot = 3, teamUUID = "uuid-three", name = "Three")),
            lobbyOcr = lobbyResult(3 to emptyList()),
        )

        assertEquals(listOf(null, null, null, null), projection.teams.single().playerNames)
        val suggestion = loadedSuggestions(projection, resultRow(1, emptyList())).single()
        assertNull(suggestion.automaticTeamUUID)
        assertEquals(com.hoggamers.rankforge.domain.matching.ResultLobbySlotDecisionStatus.MANUAL, suggestion.decisionStatus)

        val accepted = GroupRotationMatchOcrIdentityAdapter.accept(
            projection,
            acceptedLobbySlotsByResultPosition = mapOf(1 to 3),
        )
        val assignment = (accepted as GroupRotationMatchOcrAcceptedAssignmentResult.Loaded)
            .assignments
            .single()
        assertEquals("uuid-three", assignment.teamUUID)
    }

    @Test
    fun unassignedLobbyPositionCannotResolveToUuid() {
        val projection = loadedProjection(
            snapshot = snapshot(team(lobbySlot = 3, teamUUID = "uuid-three", name = "Three")),
            lobbyOcr = lobbyResult(
                3 to listOf("THREE1", "THREE2", "THREE3", "THREE4"),
                8 to listOf("UNASSIGNED1", "UNASSIGNED2", "UNASSIGNED3", "UNASSIGNED4"),
            ),
        )

        val suggestion = loadedSuggestions(
            projection,
            resultRow(1, listOf("UNASSIGNED1", "UNASSIGNED2", "UNASSIGNED3", "UNASSIGNED4")),
        ).single()
        assertNull(suggestion.automaticTeamUUID)
        assertNull(suggestion.proposedTeamUUID)
        assertTrue(
            suggestion.decisionStatus ==
                com.hoggamers.rankforge.domain.matching.ResultLobbySlotDecisionStatus.MANUAL,
        )

        val accepted = GroupRotationMatchOcrIdentityAdapter.accept(
            projection,
            acceptedLobbySlotsByResultPosition = mapOf(1 to 8),
        )
        assertTrue(accepted is GroupRotationMatchOcrAcceptedAssignmentResult.Invalid)
        assertTrue(
            (accepted as GroupRotationMatchOcrAcceptedAssignmentResult.Invalid).issues.any {
                it.code == GroupRotationMatchOcrIdentityIssueCode.ACCEPTED_LOBBY_SLOT_NOT_FOUND
            },
        )
    }

    @Test
    fun ambiguousMatchingRemainsUnresolved() {
        val projection = loadedProjection(
            snapshot = snapshot(
                team(lobbySlot = 2, teamUUID = "uuid-two", name = "Two"),
                team(lobbySlot = 9, teamUUID = "uuid-nine", name = "Nine"),
            ),
            lobbyOcr = lobbyResult(
                2 to listOf("SAME1", "SAME2", "SAME3", "SAME4"),
                9 to listOf("SAME1", "SAME2", "SAME3", "SAME4"),
            ),
        )

        val suggestion = loadedSuggestions(
            projection,
            resultRow(1, listOf("SAME1", "SAME2", "SAME3", "SAME4")),
        ).single()
        assertNull(suggestion.automaticTeamUUID)
        assertNull(suggestion.proposedTeamUUID)
        assertEquals(
            com.hoggamers.rankforge.domain.matching.ResultLobbySlotDecisionReason.TOP_VOTE_TIE,
            suggestion.decisionReason,
        )
    }

    @Test
    fun duplicateUuidAssignmentAcrossResultRowsIsRejected() {
        val projection = loadedProjection(
            snapshot = snapshot(
                team(lobbySlot = 2, teamUUID = "uuid-two", name = "Two"),
                team(lobbySlot = 9, teamUUID = "uuid-nine", name = "Nine"),
            ),
            lobbyOcr = lobbyResult(
                2 to listOf("TWO1", "TWO2", "TWO3", "TWO4"),
            ),
        )

        val accepted = GroupRotationMatchOcrIdentityAdapter.accept(
            projection,
            acceptedLobbySlotsByResultPosition = mapOf(1 to 2, 2 to 2),
        )
        assertTrue(accepted is GroupRotationMatchOcrAcceptedAssignmentResult.Invalid)
        assertTrue(
            (accepted as GroupRotationMatchOcrAcceptedAssignmentResult.Invalid).issues.any {
                it.code == GroupRotationMatchOcrIdentityIssueCode.DUPLICATE_ACCEPTED_TEAM_UUID
            },
        )

        val suggestions = loadedSuggestions(
            projection,
            resultRow(1, listOf("TWO1", "TWO2", "TWO3", "TWO4")),
            resultRow(2, listOf("TWO1", "TWO2", "TWO3", "TWO4")),
        )
        assertNull(suggestions.first().automaticTeamUUID)
        assertNull(suggestions[1].automaticTeamUUID)
        assertTrue(suggestions.all { suggestion ->
            suggestion.decisionStatus == ResultLobbySlotDecisionStatus.MANUAL &&
                suggestion.decisionReason == ResultLobbySlotDecisionReason.DUPLICATE_SLOT_ACROSS_RESULT_ROWS
        })
    }

    @Test
    fun strongerLaterPositionWinsDuplicateLobbyUuidAssignmentRegardlessOfInputOrder() {
        val projection = loadedProjection(
            snapshot = snapshot(team(lobbySlot = 5, teamUUID = "uuid-five", name = "Five")),
            lobbyOcr = lobbyResult(
                5 to listOf("Alpha5", "Bravo5", "Charlie5", "Delta5"),
            ),
        )
        val weakerRow = resultRow(9, listOf("Alpha5", null, null, null))
        val strongerRow = resultRow(11, listOf("Alpha5", "Bravo5", "Charlie5", null))

        val forward = loadedSuggestions(projection, weakerRow, strongerRow)
        val reversed = loadedSuggestions(projection, strongerRow, weakerRow)
        assertEquals(forward, reversed)

        val positionNine = forward.single { it.resultPosition == 9 }
        val positionEleven = forward.single { it.resultPosition == 11 }
        assertEquals(5, positionNine.proposedLobbySlotNumber)
        assertEquals("uuid-five", positionNine.proposedTeamUUID)
        assertEquals(25, positionNine.winningVotePercent)
        assertNull(positionNine.automaticTeamUUID)
        assertEquals(ResultLobbySlotDecisionStatus.MANUAL, positionNine.decisionStatus)
        assertEquals(ResultLobbySlotDecisionReason.DUPLICATE_SLOT_ACROSS_RESULT_ROWS, positionNine.decisionReason)
        assertEquals(5, positionEleven.proposedLobbySlotNumber)
        assertEquals("uuid-five", positionEleven.proposedTeamUUID)
        assertEquals(75, positionEleven.winningVotePercent)
        assertEquals("uuid-five", positionEleven.automaticTeamUUID)
        assertEquals(ResultLobbySlotDecisionStatus.AUTOMATIC, positionEleven.decisionStatus)
        assertEquals(ResultLobbySlotDecisionReason.UNIQUE_VOTE_WINNER, positionEleven.decisionReason)
    }

    @Test
    fun equalStrengthDuplicateLobbyUuidClaimsRemainManual() {
        val projection = loadedProjection(
            snapshot = snapshot(team(lobbySlot = 5, teamUUID = "uuid-five", name = "Five")),
            lobbyOcr = lobbyResult(
                5 to listOf("Alpha5", "Bravo5", "Charlie5", "Delta5"),
            ),
        )

        val suggestions = loadedSuggestions(
            projection,
            resultRow(9, listOf("Alpha5", "Bravo5", null, null)),
            resultRow(11, listOf("Alpha5", "Bravo5", null, null)),
        )

        assertTrue(suggestions.all { suggestion ->
            suggestion.proposedLobbySlotNumber == 5 &&
                suggestion.proposedTeamUUID == "uuid-five" &&
                suggestion.automaticTeamUUID == null &&
                suggestion.decisionStatus == ResultLobbySlotDecisionStatus.MANUAL &&
                suggestion.decisionReason == ResultLobbySlotDecisionReason.DUPLICATE_SLOT_ACROSS_RESULT_ROWS
        })
    }

    @Test
    fun threeWayDuplicateLobbyUuidClaimsKeepOnlyUniqueStrongestWinner() {
        val projection = loadedProjection(
            snapshot = snapshot(team(lobbySlot = 5, teamUUID = "uuid-five", name = "Five")),
            lobbyOcr = lobbyResult(
                5 to listOf("Alpha5", "Bravo5", "Charlie5", "Delta5"),
            ),
        )

        val suggestions = loadedSuggestions(
            projection,
            resultRow(9, listOf("Alpha5", null, null, null)),
            resultRow(10, listOf("Alpha5", "Bravo5", null, null)),
            resultRow(11, listOf("Alpha5", "Bravo5", "Charlie5", null)),
        )

        assertEquals("uuid-five", suggestions.single { it.resultPosition == 11 }.automaticTeamUUID)
        assertTrue(suggestions.filter { it.resultPosition != 11 }.all { suggestion ->
            suggestion.automaticTeamUUID == null &&
                suggestion.decisionStatus == ResultLobbySlotDecisionStatus.MANUAL &&
                suggestion.decisionReason == ResultLobbySlotDecisionReason.DUPLICATE_SLOT_ACROSS_RESULT_ROWS
        })
    }

    @Test
    fun differentLobbyUuidsAreAssignedIndependentlyWithoutDuplicateAutomaticAssignments() {
        val projection = loadedProjection(
            snapshot = snapshot(
                team(lobbySlot = 5, teamUUID = "uuid-five", name = "Five"),
                team(lobbySlot = 8, teamUUID = "uuid-eight", name = "Eight"),
            ),
            lobbyOcr = lobbyResult(
                5 to listOf("FIVE_ASTRAL", "FIVE_BREEZE", "FIVE_CINDER", "FIVE_DUSK"),
                8 to listOf("EIGHT_QUARTZ", "EIGHT_RIVER", "EIGHT_SABLE", "EIGHT_TUNDRA"),
            ),
        )

        val suggestions = loadedSuggestions(
            projection,
            resultRow(9, listOf("FIVE_ASTRAL", "FIVE_BREEZE", "FIVE_CINDER", "FIVE_DUSK")),
            resultRow(11, listOf("EIGHT_QUARTZ", "EIGHT_RIVER", "EIGHT_SABLE", "EIGHT_TUNDRA")),
        )
        val automaticUuids = suggestions.mapNotNull { it.automaticTeamUUID }

        assertEquals(setOf("uuid-five", "uuid-eight"), automaticUuids.toSet())
        assertEquals(automaticUuids.distinct(), automaticUuids)
    }

    @Test
    fun missingOrConflictingMatchUuidSnapshotIsRejected() {
        val missingUuid = GroupRotationMatchIdentitySnapshot(
            tournamentId = TOURNAMENT_ID,
            pairing = AB,
            teams = listOf(team(lobbySlot = 1, teamUUID = "", name = "Missing")),
        )
        val wrongTournament = snapshot(
            team(lobbySlot = 1, teamUUID = "uuid-one", name = "One"),
            tournamentId = "other-tournament",
        )
        val unselectedPairing = snapshot(
            team(lobbySlot = 1, teamUUID = "uuid-one", name = "One"),
            pairing = GroupPairing.fromCanonicalKey("B:C"),
        )

        assertInvalidWith(
            GroupRotationMatchOcrIdentityAdapter.project(
                tournament(), MATCH_ID, missingUuid, lobbyResult(),
            ),
            GroupRotationMatchOcrIdentityIssueCode.INVALID_TEAM_UUID,
        )
        assertInvalidWith(
            GroupRotationMatchOcrIdentityAdapter.project(
                tournament(), MATCH_ID, wrongTournament, lobbyResult(),
            ),
            GroupRotationMatchOcrIdentityIssueCode.TOURNAMENT_ID_MISMATCH,
        )
        assertInvalidWith(
            GroupRotationMatchOcrIdentityAdapter.project(
                tournament(), MATCH_ID, unselectedPairing, lobbyResult(),
            ),
            GroupRotationMatchOcrIdentityIssueCode.UNSELECTED_PAIRING,
        )
    }

    @Test
    fun renamedRegistryTeamUsesCurrentNameWithoutChangingUuid() {
        val projection = loadedProjection(
            snapshot = snapshot(
                team(lobbySlot = 5, teamUUID = "uuid-phoenix", name = "Phoenix Renamed"),
            ),
            lobbyOcr = lobbyResult(5 to listOf("P1", "P2", "P3", "P4")),
        )

        assertEquals("uuid-phoenix", projection.teams.single().teamUUID)
        assertEquals("Phoenix Renamed", projection.teams.single().displayName)
    }

    private fun loadedProjection(
        snapshot: GroupRotationMatchIdentitySnapshot,
        lobbyOcr: MatchLobbyPlayersOcrResult,
    ): GroupRotationMatchOcrIdentityProjection =
        (GroupRotationMatchOcrIdentityAdapter.project(
            tournament = tournament(),
            matchId = MATCH_ID,
            identitySnapshot = snapshot,
            lobbyOcrResult = lobbyOcr,
        ) as GroupRotationMatchOcrIdentityProjectionResult.Loaded).projection

    private fun loadedSuggestions(
        projection: GroupRotationMatchOcrIdentityProjection,
        vararg resultRows: MatchResultOcrRow,
    ): List<GroupRotationMatchOcrRowSuggestion> =
        (GroupRotationMatchOcrIdentityAdapter.suggest(projection, resultRows.toList())
            as GroupRotationMatchOcrSuggestionResult.Loaded).suggestions

    private fun assertInvalidWith(
        result: GroupRotationMatchOcrIdentityProjectionResult,
        code: GroupRotationMatchOcrIdentityIssueCode,
    ) {
        assertTrue(result is GroupRotationMatchOcrIdentityProjectionResult.Invalid)
        assertTrue(
            (result as GroupRotationMatchOcrIdentityProjectionResult.Invalid).issues.any {
                it.code == code
            },
        )
    }

    private fun snapshot(
        vararg teams: GroupRotationMatchTeamIdentity,
        pairing: GroupPairing = AB,
        tournamentId: String = TOURNAMENT_ID,
    ) = GroupRotationMatchIdentitySnapshot(
        tournamentId = tournamentId,
        pairing = pairing,
        teams = teams.toList(),
    )

    private fun team(
        lobbySlot: Int,
        teamUUID: String,
        name: String,
    ) = GroupRotationMatchTeamIdentity(
        lobbySlotNumber = lobbySlot,
        teamUUID = teamUUID,
        displayName = name,
    )

    private fun lobbyResult(
        vararg slots: Pair<Int, List<String?>>,
    ) = MatchLobbyPlayersOcrResult(
        slots = slots.map { (slotNumber, players) ->
            MatchLobbyPlayersOcrSlot(
                slotNumber = slotNumber,
                players = players.mapIndexed { index, playerName ->
                    MatchLobbyPlayersOcrPlayer(index + 1, playerName)
                },
            )
        },
    )

    private fun resultRow(
        position: Int,
        playerNames: List<String?>,
    ) = MatchResultOcrRow(
        position = position,
        source = MatchResultOcrRowSource.UPPER_TEMPLATE,
        placement = field(
            id = "placement-$position",
            type = MatchResultOcrFieldType.PLACEMENT,
            position = position,
            slot = null,
            text = position.toString(),
            status = MatchResultOcrFieldStatus.DIRECT_NUMERIC,
        ),
        playerSlots = (1..4).map { slot ->
            val playerName = playerNames.getOrNull(slot - 1)
            MatchResultOcrPlayerSlot(
                slot = slot,
                player = field(
                    id = "player-$position-$slot",
                    type = MatchResultOcrFieldType.PLAYER,
                    position = position,
                    slot = slot,
                    text = playerName.orEmpty(),
                    status = if (playerName.isNullOrBlank()) {
                        MatchResultOcrFieldStatus.EMPTY
                    } else {
                        MatchResultOcrFieldStatus.DIRECT_TEXT
                    },
                ),
                kill = field(
                    id = "kill-$position-$slot",
                    type = MatchResultOcrFieldType.KILL,
                    position = position,
                    slot = slot,
                    text = "0",
                    status = MatchResultOcrFieldStatus.DIRECT_NUMERIC,
                ),
            )
        },
    )

    private fun field(
        id: String,
        type: MatchResultOcrFieldType,
        position: Int,
        slot: Int?,
        text: String,
        status: MatchResultOcrFieldStatus,
    ) = MatchResultOcrField(
        id = id,
        type = type,
        position = position,
        visualRow = null,
        slot = slot,
        canonicalRect = EMPTY_RECT,
        mappedRect = EMPTY_RECT,
        ocrText = text,
        resolvedText = text,
        status = status,
    )

    private fun tournament() = Tournament(
        id = TOURNAMENT_ID,
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = 3,
        selectedGroupPairings = listOf(AB, AC),
    )

    private companion object {
        const val MATCH_ID = "match-1"
        const val TOURNAMENT_ID = "tournament-1"
        val AB = GroupPairing.fromCanonicalKey("A:B")
        val AC = GroupPairing.fromCanonicalKey("A:C")
        val EMPTY_RECT = MatchResultOcrRect(0.0, 0.0, 0.0, 0.0)
    }
}
