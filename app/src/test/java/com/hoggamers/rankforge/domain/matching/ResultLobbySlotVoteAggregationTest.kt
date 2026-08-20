package com.hoggamers.rankforge.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultLobbySlotVoteAggregationTest {
    @Test
    fun fourSamePlayersProduce100To0AutomaticWinner() {
        val result = match(players, candidate(5, players), candidate(9, unrelated))

        assertVote(result, slot = 5, count = 4, percent = 100)
        assertEquals(ResultLobbySlotDecisionStatus.AUTOMATIC, result.decisionStatus)
        assertEquals(ResultLobbySlotDecisionReason.UNIQUE_VOTE_WINNER, result.decisionReason)
        assertEquals(5, result.automaticAssignedTeamSlot)
    }

    @Test
    fun threeSamePlayersProduce75To0AutomaticWinner() {
        val result = match(
            listOf("Alpha", "Bravo", "Charlie", "NoMatch"),
            candidate(5, listOf("Alpha", "Bravo", "Charlie", "Other")),
            candidate(9, unrelated),
        )

        assertVote(result, slot = 5, count = 3, percent = 75)
        assertEquals(ResultLobbySlotDecisionStatus.AUTOMATIC, result.decisionStatus)
    }

    @Test
    fun threeSameAndOneOtherProduce75To25AutomaticWinner() {
        val result = match(
            players,
            candidate(5, listOf("Alpha", "Bravo", "Charlie", "Other")),
            candidate(9, listOf("Delta", "Remote", "Another", "Else")),
        )

        assertVote(result, slot = 5, count = 3, percent = 75)
        assertVote(result, slot = 9, count = 1, percent = 25)
        assertEquals(ResultLobbySlotDecisionStatus.AUTOMATIC, result.decisionStatus)
        assertEquals(5, result.proposedTeamSlot)
    }

    @Test
    fun twoSameAndTwoRandomProduce50To0AutomaticWinner() {
        val result = match(
            players,
            candidate(5, listOf("Alpha", "Bravo", "NoMatch1", "NoMatch2")),
            candidate(9, unrelated),
        )

        assertVote(result, slot = 5, count = 2, percent = 50)
        assertEquals(ResultLobbySlotDecisionStatus.AUTOMATIC, result.decisionStatus)
    }

    @Test
    fun twoSameOtherAndRandomProduce50To25AutomaticWinner() {
        val result = match(
            players,
            candidate(5, listOf("Alpha", "Bravo", "NoMatch1", "NoMatch2")),
            candidate(9, listOf("Charlie", "Remote", "Another", "Else")),
        )

        assertVote(result, slot = 5, count = 2, percent = 50)
        assertVote(result, slot = 9, count = 1, percent = 25)
        assertEquals(ResultLobbySlotDecisionStatus.AUTOMATIC, result.decisionStatus)
    }

    @Test
    fun twoVsTwoIsManualTie() {
        val result = match(
            players,
            candidate(5, listOf("Alpha", "Bravo", "NoMatch1", "NoMatch2")),
            candidate(9, listOf("Charlie", "Delta", "NoMatch3", "NoMatch4")),
        )

        assertVote(result, slot = 5, count = 2, percent = 50)
        assertVote(result, slot = 9, count = 2, percent = 50)
        assertNull(result.proposedTeamSlot)
        assertEquals(50, result.winningVotePercent)
        assertEquals(ResultLobbySlotDecisionStatus.MANUAL, result.decisionStatus)
        assertEquals(ResultLobbySlotDecisionReason.TOP_VOTE_TIE, result.decisionReason)
        assertNull(result.automaticAssignedTeamSlot)
    }

    @Test
    fun oneStrongVoteCanAutomaticallyAssign() {
        val result = match(
            listOf("Alpha", "R1", "R2", "R3"),
            candidate(5, listOf("Alpha", "Unrelated1", "Unrelated2", "Unrelated3")),
            candidate(9, unrelated),
        )

        assertVote(result, slot = 5, count = 1, percent = 25)
        assertEquals(ResultLobbySlotDecisionStatus.AUTOMATIC, result.decisionStatus)
        assertEquals(ResultLobbySlotDecisionReason.SINGLE_STRONG_VOTE, result.decisionReason)
        assertEquals(5, result.automaticAssignedTeamSlot)
    }

    @Test
    fun oneWeakPlausibleVoteRemainsManual() {
        val result = match(
            listOf("ABC", "R1", "R2", "R3"),
            candidate(5, listOf("ABD", "Unrelated1", "Unrelated2", "Unrelated3")),
            candidate(9, unrelated),
        )

        assertVote(result, slot = 5, count = 1, percent = 25)
        assertTrue(result.playerSlotVoteEvidence.single().bestSimilarityScore in 65..74)
        assertEquals(ResultLobbySlotDecisionStatus.MANUAL, result.decisionStatus)
        assertEquals(ResultLobbySlotDecisionReason.SINGLE_VOTE_BELOW_STRONG_THRESHOLD, result.decisionReason)
    }

    @Test
    fun noPlausibleMatchesRemainManualWithoutProposal() {
        val result = match(
            listOf("KLMNOP", "QRSTUV", "WXYZAB", "CDEFGH"),
            candidate(5, listOf("11111111", "22222222", "33333333", "44444444")),
            candidate(9, listOf("55555555", "66666666", "77777777", "88888888")),
        )

        assertTrue(result.playerSlotVoteEvidence.isEmpty())
        assertEquals(ResultLobbySlotDecisionStatus.MANUAL, result.decisionStatus)
        assertEquals(ResultLobbySlotDecisionReason.NO_PLAUSIBLE_MATCH, result.decisionReason)
        assertNull(result.proposedTeamSlot)
    }

    @Test
    fun oneResultPlayerCanVoteForTwoLobbySlotsButOnlyOncePerSlot() {
        val result = match(
            listOf("Alpha", "R1", "R2", "R3"),
            candidate(5, listOf("Alpha", "AlphaTwo", "AlphaThree", "No")),
            candidate(9, listOf("Alpha", "Remote", "Another", "Else")),
        )

        assertEquals(
            setOf(5, 9),
            result.playerSlotVoteEvidence.map { it.teamSlot }.toSet(),
        )
        assertEquals(1, result.slotVoteScores.single { it.teamSlot == 5 }.voteCount)
        assertEquals(1, result.slotVoteScores.single { it.teamSlot == 9 }.voteCount)
        assertEquals(ResultLobbySlotDecisionReason.TOP_VOTE_TIE, result.decisionReason)
    }

    @Test
    fun oneResultPlayerMatchingMultipleLobbyPlayersContributesOneVote() {
        val result = match(
            listOf("Alpha", "R1", "R2", "R3"),
            candidate(5, listOf("Alpha", "AlphaX", "AlphaY", "No")),
            candidate(9, unrelated),
        )

        assertEquals(1, result.playerSlotVoteEvidence.size)
        assertEquals(1, result.slotVoteScores.single { it.teamSlot == 5 }.voteCount)
    }

    @Test
    fun oneLobbyPlayerCanSupportMultipleResultPlayers() {
        val result = match(
            listOf("Alpha", "Alpha", "R2", "R3"),
            candidate(5, listOf("Alpha", "Remote", "Another", "Else")),
            candidate(9, unrelated),
        )

        assertEquals(listOf(1, 2), result.slotVoteScores.single { it.teamSlot == 5 }
            .supportingResultPlayerSlots)
        assertEquals(2, result.slotVoteScores.single { it.teamSlot == 5 }.voteCount)
    }

    @Test
    fun fourRowsCanOutvoteACompetingSlotWithTwoVotes() {
        val result = match(
            players,
            candidate(10, players),
            candidate(1, listOf("Alpha", "Bravo", "Remote", "Else")),
        )

        assertVote(result, slot = 10, count = 4, percent = 100)
        assertVote(result, slot = 1, count = 2, percent = 50)
        assertEquals(10, result.automaticAssignedTeamSlot)
    }

    @Test
    fun voteTieIsNotBrokenBySimilarityScore() {
        val result = match(
            listOf("Alpha", "ABC", "R2", "R3"),
            candidate(10, listOf("Alpha", "Remote", "Another", "Else")),
            candidate(1, listOf("ABD", "Remote", "Another", "Else")),
        )

        assertVote(result, slot = 10, count = 1, percent = 25)
        assertVote(result, slot = 1, count = 1, percent = 25)
        assertEquals(ResultLobbySlotDecisionStatus.MANUAL, result.decisionStatus)
        assertEquals(ResultLobbySlotDecisionReason.TOP_VOTE_TIE, result.decisionReason)
    }

    @Test
    fun blankResultAndLobbyPlayersDoNotVote() {
        val result = match(
            listOf(null, "", "  ", "R4"),
            candidate(5, listOf(null, "", "  ", "Other")),
            candidate(9, unrelated),
        )

        assertTrue(result.playerSlotVoteEvidence.isEmpty())
        assertEquals(ResultLobbySlotDecisionReason.NO_PLAUSIBLE_MATCH, result.decisionReason)
    }

    @Test
    fun fullNumericLobbyFixtureProducesDomainEvaluation() {
        val results = (1..12).map { position ->
            ResultLobbySlotMatcher.rank(
                ResultLobbySlotMatchInput(
                    resultPosition = position,
                    resultPlayerNames = numericTeamPlayers(position),
                    lobbyCandidates = (1..12).map { slot ->
                        candidate(slot, numericTeamPlayers(slot))
                    },
                ),
            )
        }

        val evaluation = ResultLobbySlotAssignmentEvaluator.evaluate(results)

        assertEquals(12, evaluation.rows.size)
    }

    @Test
    fun incompleteLobbyPlayersStillProduceManualDomainRows() {
        val result = ResultLobbySlotMatcher.rank(
            ResultLobbySlotMatchInput(
                resultPosition = 1,
                resultPlayerNames = listOf("Alpha", null, null, null),
                lobbyCandidates = (1..12).map { slot ->
                    candidate(slot, if (slot == 1) players else listOf(null, null, null, null))
                },
            ),
        )

        val evaluation = ResultLobbySlotAssignmentEvaluator.evaluate(listOf(result))

        assertEquals(1, evaluation.rows.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateLobbySlotNumbersAreRejected() {
        match(players, candidate(5, players), candidate(5, unrelated))
    }

    private fun match(
        resultPlayers: List<String?>,
        vararg candidates: LobbyTeamSlotMatchCandidate,
    ): ResultLobbySlotMatchResult = ResultLobbySlotMatcher.rank(
        ResultLobbySlotMatchInput(
            resultPosition = 1,
            resultPlayerNames = resultPlayers,
            lobbyCandidates = candidates.toList(),
        ),
    )

    private fun candidate(slot: Int, players: List<String?>) =
        LobbyTeamSlotMatchCandidate(teamSlotNumber = slot, playerNames = players)

    private fun assertVote(
        result: ResultLobbySlotMatchResult,
        slot: Int,
        count: Int,
        percent: Int,
    ) {
        val vote = result.slotVoteScores.single { it.teamSlot == slot }
        assertEquals(count, vote.voteCount)
        assertEquals(percent, vote.votePercent)
    }

    private fun numericTeamPlayers(teamSlot: Int): List<String?> = listOf(
        "Alpha$teamSlot",
        "Bravo$teamSlot",
        "Charlie$teamSlot",
        "Delta$teamSlot",
    )

    private companion object {
        val players = listOf<String?>("Alpha", "Bravo", "Charlie", "Delta")
        val unrelated = listOf<String?>("KLMNOP", "QRSTUV", "WXYZAB", "CDEFGH")
    }
}
