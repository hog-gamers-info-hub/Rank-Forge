package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrPlayerSlot
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotDecisionReason
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotDecisionStatus
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.MatchLobbyTeamIdentity
import com.hoggamers.rankforge.domain.tournament.MatchTeamIdentityContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultLobbyOcrSlotRankerTest {
    @Test
    fun rank_usesCurrentLobbyOcrSemanticSlotTwelveAsCandidateIdentity() {
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(
                position = 4,
                playerNames = exactPlayers,
            ),
            lobbyOcrResult = lobbyResult(
                overrides = mapOf(12 to exactPlayers),
            ),
        )

        val topScore = result.rankedCandidates.suggestions.first().teamCandidateScore
        assertEquals(4, result.resultPosition)
        assertEquals(12, result.rankedCandidates.evaluatedCandidateCount)
        assertEquals(12, topScore.candidateTeamSlot)
        assertEquals(4, topScore.contributingMatchCount)
        assertEquals(100, topScore.confidenceScore)
    }

    @Test
    fun rank_readsResultPlayerNamesFromExistingResultOcrFieldsOnly() {
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(
                position = 7,
                playerNames = listOf("PLAYER0NE", "NOVA", "RIN", "KAI"),
            ),
            lobbyOcrResult = lobbyResult(
                overrides = mapOf(
                    5 to listOf("PLAYERONE", "NOVA", "RIN", "KAI"),
                ),
            ),
        )

        val topScore = result.rankedCandidates.suggestions.first().teamCandidateScore
        assertEquals(listOf("PLAYER0NE", "NOVA", "RIN", "KAI"), result.resultPlayerNames)
        assertEquals(5, topScore.candidateTeamSlot)
        assertEquals(4, topScore.contributingMatchCount)
        assertEquals(100, topScore.confidenceScore)
    }

    @Test
    fun rank_preservesFourLogicalSlotsWhenResultPlayerFourIsMissing() {
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(
                position = 7,
                playerNamesBySlot = mapOf(1 to "P1", 2 to "P2", 3 to "P3"),
            ),
            lobbyOcrResult = lobbyResult(overrides = emptyMap()),
        )

        assertEquals(listOf("P1", "P2", "P3", null), result.resultPlayerNames)
    }

    @Test
    fun rank_preservesMissingMiddleResultPlayerSlotWithoutShiftingLaterPlayers() {
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(
                position = 7,
                playerNamesBySlot = mapOf(1 to "P1", 3 to "P3", 4 to "P4"),
            ),
            lobbyOcrResult = lobbyResult(overrides = emptyMap()),
        )

        assertEquals(listOf("P1", null, "P3", "P4"), result.resultPlayerNames)
    }

    @Test
    fun rank_keepsTwoDetectedPlayersEligibleForUniqueAutomaticLobbyVote() {
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(
                position = 7,
                playerNamesBySlot = mapOf(
                    1 to "EB-ALPHA.18",
                    2 to "ydvAbhi",
                    3 to "KAPPA",
                ),
            ),
            lobbyOcrResult = lobbyResult(
                overrides = mapOf(
                    12 to listOf("FB-ALPHA.18", "ydvAbhi☆", "OMEGA", null),
                ),
            ),
        )

        val slotTwelve = result.slotVoteScores.single { it.teamSlot == 12 }
        assertEquals(listOf("EB-ALPHA.18", "ydvAbhi", "KAPPA", null), result.resultPlayerNames)
        assertEquals(2, slotTwelve.voteCount)
        assertEquals(listOf(1, 2), slotTwelve.supportingResultPlayerSlots)
        assertEquals(ResultLobbySlotDecisionStatus.AUTOMATIC, result.decisionStatus)
        assertEquals(ResultLobbySlotDecisionReason.UNIQUE_VOTE_WINNER, result.decisionReason)
        assertEquals(12, result.automaticAssignedTeamSlot)
    }

    @Test
    fun rank_allMissingResultPlayersProducesManualNoPlausibleMatch() {
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(position = 7, playerNamesBySlot = emptyMap()),
            lobbyOcrResult = lobbyResult(overrides = emptyMap()),
        )

        assertEquals(listOf(null, null, null, null), result.resultPlayerNames)
        assertEquals(ResultLobbySlotDecisionStatus.MANUAL, result.decisionStatus)
        assertEquals(ResultLobbySlotDecisionReason.NO_PLAUSIBLE_MATCH, result.decisionReason)
        assertTrue(result.playerSlotVoteEvidence.isEmpty())
    }

    @Test
    fun rank_unavailableLobbyPlayersContributeNoFabricatedMatches() {
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(
                position = 10,
                playerNames = exactPlayers,
            ),
            lobbyOcrResult = MatchLobbyPlayersOcrResult.unavailable(),
        )

        assertEquals(12, result.rankedCandidates.evaluatedCandidateCount)
        assertEquals(3, result.rankedCandidates.suggestions.size)
        assertTrue(result.rankedCandidates.suggestions.all { suggestion ->
            suggestion.teamCandidateScore.contributingMatchCount == 0 &&
                suggestion.teamCandidateScore.confidenceScore == 0
        })
    }

    @Test
    fun rankGroupRotationCandidateSetUsesPermanentSlotsFromSelectedPairing() {
        val eligibleSlots = (1..6).toList() + (13..18).toList()
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(position = 4, playerNames = rosterPlayers(13)),
            lobbyOcrResult = lobbyResult(
                overrides = groupRotationLobbyOverrides(),
            ),
            eligibleTeamSlots = eligibleSlots,
            permanentTeamCandidates = rosterCandidates(eligibleSlots),
            allRosterTeamCandidates = rosterCandidates(1..24),
            matchIdentityContext = acGroupRotationContext(),
        )

        assertEquals(eligibleSlots, result.slotVoteScores.map { it.teamSlot })
        assertEquals(eligibleSlots.size, result.rankedCandidates.evaluatedCandidateCount)
    }

    @Test
    fun rankUsesPersistedArbitraryLobbyOrderForCanonicalTeam18() {
        val context = exactScenarioIdentityContext()
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(position = 1, playerNames = rosterPlayers(18)),
            lobbyOcrResult = lobbyResult(overrides = mapOf(3 to rosterPlayers(18))),
            eligibleTeamSlots = context.eligibleTeamSlotNumbers,
            allRosterTeamCandidates = rosterCandidates(1..24),
            matchIdentityContext = context,
        )

        assertEquals(18, result.automaticAssignedTeamSlot)
        assertEquals(18, result.rankedCandidates.suggestions.first().teamCandidateScore.candidateTeamSlot)
        assertTrue(result.slotVoteScores.none { it.teamSlot == 3 && it.voteCount > 0 })
    }

    @Test
    fun rankGroupRotationLocalLobbySlotOneDoesNotMeanPermanentSlotOne() {
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(position = 4, playerNames = rosterPlayers(14)),
            lobbyOcrResult = lobbyResult(overrides = mapOf(1 to rosterPlayers(14))),
            eligibleTeamSlots = (1..6).toList() + (13..18).toList(),
            permanentTeamCandidates = rosterCandidates((1..6).toList() + (13..18).toList()),
            allRosterTeamCandidates = rosterCandidates(1..24),
            matchIdentityContext = acGroupRotationContext(),
        )

        assertEquals(14, result.rankedCandidates.suggestions.first().teamCandidateScore.candidateTeamSlot)
        assertEquals(14, result.automaticAssignedTeamSlot)
        assertTrue(result.slotVoteScores.any { it.teamSlot == 14 })
        assertTrue(result.slotVoteScores.none { it.teamSlot == 1 && it.voteCount > 0 })
    }

    @Test
    fun rankGroupRotationLocalLobbySlotSevenDoesNotMeanPermanentSlotThirteen() {
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(position = 4, playerNames = rosterPlayers(6)),
            lobbyOcrResult = lobbyResult(overrides = mapOf(7 to rosterPlayers(6))),
            eligibleTeamSlots = (1..6).toList() + (13..18).toList(),
            permanentTeamCandidates = rosterCandidates((1..6).toList() + (13..18).toList()),
            allRosterTeamCandidates = rosterCandidates(1..24),
            matchIdentityContext = acGroupRotationContext(),
        )

        assertEquals(6, result.rankedCandidates.suggestions.first().teamCandidateScore.candidateTeamSlot)
        assertEquals(6, result.automaticAssignedTeamSlot)
        assertTrue(result.slotVoteScores.none { it.teamSlot == 13 && it.voteCount > 0 })
    }

    @Test
    fun rankGroupRotationUsesRosterEvidenceForCGroupAtArbitraryLobbyPosition() {
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(position = 4, playerNames = rosterPlayers(16)),
            lobbyOcrResult = lobbyResult(overrides = mapOf(5 to rosterPlayers(16))),
            eligibleTeamSlots = (1..6).toList() + (13..18).toList(),
            permanentTeamCandidates = rosterCandidates((1..6).toList() + (13..18).toList()),
            allRosterTeamCandidates = rosterCandidates(1..24),
            matchIdentityContext = acGroupRotationContext(),
        )

        assertEquals(16, result.automaticAssignedTeamSlot)
        assertEquals(16, result.rankedCandidates.suggestions.first().teamCandidateScore.candidateTeamSlot)
    }

    @Test
    fun rankGroupRotationDoesNotForceOutOfGroupEvidenceToEligibleIdentity() {
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(position = 4, playerNames = rosterPlayers(7)),
            lobbyOcrResult = lobbyResult(overrides = mapOf(1 to rosterPlayers(7))),
            eligibleTeamSlots = (1..6).toList() + (13..18).toList(),
            permanentTeamCandidates = rosterCandidates((1..6).toList() + (13..18).toList()),
            allRosterTeamCandidates = rosterCandidates(1..24),
            matchIdentityContext = acGroupRotationContext(),
        )

        assertNull(result.automaticAssignedTeamSlot)
        assertNull(result.proposedTeamSlot)
        assertTrue(result.rankedCandidates.suggestions.none {
            it.teamCandidateScore.contributingMatchCount > 0
        })
        assertEquals(ResultLobbySlotDecisionStatus.MANUAL, result.decisionStatus)
    }

    @Test
    fun rankGroupRotationKeepsAmbiguousLobbyRosterEvidenceManual() {
        val result = MatchResultLobbyOcrSlotRanker.rank(
            resultRow = resultRow(position = 4, playerNames = rosterPlayers(13)),
            lobbyOcrResult = lobbyResult(
                overrides = mapOf(
                    1 to listOf(
                        rosterPlayers(1)[0],
                        rosterPlayers(1)[1],
                        rosterPlayers(13)[0],
                        rosterPlayers(13)[1],
                    ),
                ),
            ),
            eligibleTeamSlots = (1..6).toList() + (13..18).toList(),
            permanentTeamCandidates = rosterCandidates((1..6).toList() + (13..18).toList()),
            allRosterTeamCandidates = rosterCandidates(1..24),
            matchIdentityContext = acGroupRotationContext(),
        )

        assertNull(result.automaticAssignedTeamSlot)
        assertNull(result.proposedTeamSlot)
        assertEquals(ResultLobbySlotDecisionStatus.MANUAL, result.decisionStatus)
    }

    private fun lobbyResult(
        overrides: Map<Int, List<String?>>,
    ): MatchLobbyPlayersOcrResult = MatchLobbyPlayersOcrResult(
        slots = (1..12).map { teamSlot ->
            val playerNames = overrides[teamSlot] ?: unrelatedPlayers(teamSlot)
            MatchLobbyPlayersOcrSlot(
                slotNumber = teamSlot,
                players = playerNames.mapIndexed { index, playerName ->
                    MatchLobbyPlayersOcrPlayer(
                        playerNumber = index + 1,
                        playerName = playerName,
                    )
                },
            )
        },
    )

    private fun resultRow(
        position: Int,
        playerNames: List<String?> = exactPlayers,
        playerNamesBySlot: Map<Int, String?> = playerNames.mapIndexed { index, playerName ->
            index + 1 to playerName
        }.toMap(),
    ): MatchResultOcrRow = MatchResultOcrRow(
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
        playerSlots = playerNamesBySlot.map { (playerSlot, playerName) ->
            MatchResultOcrPlayerSlot(
                slot = playerSlot,
                player = field(
                    id = "player-$position-$playerSlot",
                    type = MatchResultOcrFieldType.PLAYER,
                    position = position,
                    slot = playerSlot,
                    text = playerName.orEmpty(),
                    status = if (playerName.isNullOrBlank()) {
                        MatchResultOcrFieldStatus.EMPTY
                    } else {
                        MatchResultOcrFieldStatus.DIRECT_TEXT
                    },
                ),
                kill = field(
                    id = "kill-$position-$playerSlot",
                    type = MatchResultOcrFieldType.KILL,
                    position = position,
                    slot = playerSlot,
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
    ): MatchResultOcrField = MatchResultOcrField(
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

    private fun unrelatedPlayers(teamSlot: Int): List<String?> = listOf(
        "ZZ${teamSlot}Quartz",
        "YY${teamSlot}Vex",
        "XX${teamSlot}Mirth",
        "WW${teamSlot}Pond",
    )

    private fun rosterCandidates(slots: Iterable<Int>): List<com.hoggamers.rankforge.domain.matching.TeamCandidateRosterInput> =
        slots.map { slot ->
            com.hoggamers.rankforge.domain.matching.TeamCandidateRosterInput(
                teamSlot = slot,
                rosterPlayerNames = rosterPlayers(slot),
            )
        }

    private fun rosterPlayers(slot: Int): List<String?> = listOf(
        "${slotToken(slot)}Alpha",
        "${slotToken(slot)}Bravo",
        "${slotToken(slot)}Charlie",
        "${slotToken(slot)}Delta",
    )

    private fun slotToken(slot: Int): String =
        ('A'.code + slot - 1).toChar().toString().repeat(8)

    private fun acGroupRotationContext(): MatchTeamIdentityContext = MatchTeamIdentityContext(
        tournamentId = "tournament",
        pairing = GroupPairing.fromCanonicalKey("A:C"),
        teams = listOf(14, 3, 18, 13, 16, 5, 6, 15, 2, 17, 4, 1)
            .mapIndexed { index, teamSlot ->
                MatchLobbyTeamIdentity(index + 1, teamSlot)
            },
    )

    private fun exactScenarioIdentityContext(): MatchTeamIdentityContext = MatchTeamIdentityContext(
        tournamentId = "tournament",
        pairing = GroupPairing.fromCanonicalKey("A:C"),
        teams = listOf(14, 3, 18, 7, 16, 5, 6, 15, 2, 17, 4, 13)
            .mapIndexed { index, teamSlot ->
                MatchLobbyTeamIdentity(index + 1, teamSlot)
            },
    )

    private fun groupRotationLobbyOverrides(): Map<Int, List<String?>> =
        acGroupRotationContext().orderedTeams.associate { identity ->
            identity.lobbySlotNumber to rosterPlayers(identity.teamSlotNumber)
        }

    private companion object {
        val exactPlayers = listOf<String?>(
            "AlphaWolf",
            "BravoFox",
            "CharlieRay",
            "DeltaKing",
        )
        val EMPTY_RECT = MatchResultOcrRect(
            left = 0.0,
            top = 0.0,
            right = 0.0,
            bottom = 0.0,
        )
    }
}
