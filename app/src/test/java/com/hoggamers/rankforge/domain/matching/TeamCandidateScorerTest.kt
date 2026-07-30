package com.hoggamers.rankforge.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TeamCandidateScorerTest {
    @Test
    fun score_calculatesFourExactMatchesAgainstFourRosterPlayers() {
        assertScore(
            score = TeamCandidateScorer.score(
                detectedPlayerNames = listOf("Unit7", "Nova", "Rin", "Kai"),
                candidateTeamSlot = 1,
                rosterPlayerNames = listOf("Unit7", "Nova", "Rin", "Kai"),
            ),
            expectedConfidence = 100,
            expectedDetectedCount = 4,
            expectedValidDetectedCount = 4,
            expectedRosterCount = 4,
            expectedContributingCount = 4,
            expectedAverage = 100,
            expectedCoverage = 100,
            expectedMatches = listOf(0 to 0, 1 to 1, 2 to 2, 3 to 3),
        )
    }

    @Test
    fun score_reducesCoverageForMissingDetectedPlayers() {
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("Unit7", "Nova", "Rin", null),
                candidateTeamSlot = 2,
                rosterPlayerNames = listOf("Unit7", "Nova", "Rin", "Kai"),
            ),
            expectedConfidence = 92,
            expectedDetectedCount = 4,
            expectedValidDetectedCount = 3,
            expectedRosterCount = 4,
            expectedContributingCount = 3,
            expectedAverage = 100,
            expectedCoverage = 75,
            expectedMatches = listOf(0 to 0, 1 to 1, 2 to 2),
        )
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("Unit7", "Nova", null, ""),
                candidateTeamSlot = 3,
                rosterPlayerNames = listOf("Unit7", "Nova", "Rin", "Kai"),
            ),
            expectedConfidence = 85,
            expectedDetectedCount = 4,
            expectedValidDetectedCount = 2,
            expectedRosterCount = 4,
            expectedContributingCount = 2,
            expectedAverage = 100,
            expectedCoverage = 50,
            expectedMatches = listOf(0 to 0, 1 to 1),
        )
    }

    @Test
    fun score_handlesNoValidDetectedPlayersAndEmptyInputs() {
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf(null, "", "--", "***"),
                candidateTeamSlot = 4,
                rosterPlayerNames = listOf("Unit7", "Nova", "Rin", "Kai"),
            ),
            expectedConfidence = 0,
            expectedDetectedCount = 4,
            expectedValidDetectedCount = 0,
            expectedRosterCount = 4,
            expectedContributingCount = 0,
            expectedAverage = 0,
            expectedCoverage = 0,
            expectedMatches = emptyList(),
        )
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = emptyList(),
                candidateTeamSlot = 5,
                rosterPlayerNames = listOf("Unit7", "Nova", "Rin", "Kai"),
            ),
            expectedConfidence = 0,
            expectedDetectedCount = 0,
            expectedValidDetectedCount = 0,
            expectedRosterCount = 4,
            expectedContributingCount = 0,
            expectedAverage = 0,
            expectedCoverage = 0,
            expectedMatches = emptyList(),
        )
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("Unit7", "Nova", "Rin", "Kai"),
                candidateTeamSlot = 6,
                rosterPlayerNames = emptyList(),
            ),
            expectedConfidence = 0,
            expectedDetectedCount = 4,
            expectedValidDetectedCount = 4,
            expectedRosterCount = 0,
            expectedContributingCount = 0,
            expectedAverage = 0,
            expectedCoverage = 0,
            expectedMatches = emptyList(),
        )
    }

    @Test
    fun score_ignoresInvalidDetectedOrRosterNamesForContribution() {
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("Unit7", "--", "Rin", "Kai"),
                candidateTeamSlot = 7,
                rosterPlayerNames = listOf("Unit7", "Nova", "Rin", "Kai"),
            ),
            expectedConfidence = 92,
            expectedDetectedCount = 4,
            expectedValidDetectedCount = 3,
            expectedRosterCount = 4,
            expectedContributingCount = 3,
            expectedAverage = 100,
            expectedCoverage = 75,
            expectedMatches = listOf(0 to 0, 2 to 2, 3 to 3),
        )
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("Unit7", "Nova", "Rin", "Kai"),
                candidateTeamSlot = 8,
                rosterPlayerNames = listOf("Unit7", "--", "Rin", "Kai"),
            ),
            expectedConfidence = 92,
            expectedDetectedCount = 4,
            expectedValidDetectedCount = 4,
            expectedRosterCount = 4,
            expectedContributingCount = 3,
            expectedAverage = 100,
            expectedCoverage = 75,
            expectedMatches = listOf(0 to 0, 2 to 2, 3 to 3),
        )
    }

    @Test
    fun score_appliesContributionFloorWithoutCreatingConfidenceTier() {
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("abcd", "Nova", "Rin", "Kai"),
                candidateTeamSlot = 9,
                rosterPlayerNames = listOf("abxd", "Nova", "Rin", "Kai"),
            ),
            expectedConfidence = 95,
            expectedDetectedCount = 4,
            expectedValidDetectedCount = 4,
            expectedRosterCount = 4,
            expectedContributingCount = 4,
            expectedAverage = 93,
            expectedCoverage = 100,
            expectedMatches = listOf(0 to 0, 1 to 1, 2 to 2, 3 to 3),
        )
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("ab", "Nova", "Rin", "Kai"),
                candidateTeamSlot = 10,
                rosterPlayerNames = listOf("xy", "Nova", "Rin", "Kai"),
            ),
            expectedConfidence = 92,
            expectedDetectedCount = 4,
            expectedValidDetectedCount = 4,
            expectedRosterCount = 4,
            expectedContributingCount = 3,
            expectedAverage = 100,
            expectedCoverage = 75,
            expectedMatches = listOf(1 to 1, 2 to 2, 3 to 3),
        )
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("abcd"),
                candidateTeamSlot = 11,
                rosterPlayerNames = listOf("abxd"),
            ),
            expectedConfidence = 60,
            expectedDetectedCount = 1,
            expectedValidDetectedCount = 1,
            expectedRosterCount = 1,
            expectedContributingCount = 1,
            expectedAverage = 75,
            expectedCoverage = 25,
            expectedMatches = listOf(0 to 0),
        )
    }

    @Test
    fun score_preventsDuplicateDetectedAndRosterContributions() {
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("Unit7", "Unit7", "Rin", "Kai"),
                candidateTeamSlot = 12,
                rosterPlayerNames = listOf("Unit7", "Nova", "Rin", "Kai"),
            ),
            expectedConfidence = 92,
            expectedDetectedCount = 4,
            expectedValidDetectedCount = 4,
            expectedRosterCount = 4,
            expectedContributingCount = 3,
            expectedAverage = 100,
            expectedCoverage = 75,
            expectedMatches = listOf(0 to 0, 2 to 2, 3 to 3),
        )
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("Unit7", "Nova", "Rin", "Kai"),
                candidateTeamSlot = 1,
                rosterPlayerNames = listOf("Unit7", "Unit7", "Rin", "Kai"),
            ),
            expectedConfidence = 92,
            expectedDetectedCount = 4,
            expectedValidDetectedCount = 4,
            expectedRosterCount = 4,
            expectedContributingCount = 3,
            expectedAverage = 100,
            expectedCoverage = 75,
            expectedMatches = listOf(0 to 0, 2 to 2, 3 to 3),
        )
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("Unit7", "Unit7", "Rin", "Kai"),
                candidateTeamSlot = 2,
                rosterPlayerNames = listOf("Unit7"),
            ),
            expectedConfidence = 77,
            expectedDetectedCount = 4,
            expectedValidDetectedCount = 4,
            expectedRosterCount = 1,
            expectedContributingCount = 1,
            expectedAverage = 100,
            expectedCoverage = 25,
            expectedMatches = listOf(0 to 0),
        )
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("Unit7"),
                candidateTeamSlot = 3,
                rosterPlayerNames = listOf("Unit7", "Unit7", "Rin", "Kai"),
            ),
            expectedConfidence = 77,
            expectedDetectedCount = 1,
            expectedValidDetectedCount = 1,
            expectedRosterCount = 4,
            expectedContributingCount = 1,
            expectedAverage = 100,
            expectedCoverage = 25,
            expectedMatches = listOf(0 to 0),
        )
    }

    @Test
    fun score_breaksTiesDeterministically() {
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("abcdefghijkl"),
                candidateTeamSlot = 4,
                rosterPlayerNames = listOf("abcxyzghijkl", "abcdefghijklWXYZ"),
            ),
            expectedConfidence = 60,
            expectedDetectedCount = 1,
            expectedValidDetectedCount = 1,
            expectedRosterCount = 2,
            expectedContributingCount = 1,
            expectedAverage = 75,
            expectedCoverage = 25,
            expectedMatches = listOf(0 to 0),
        )
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("Unit7", "Unit7"),
                candidateTeamSlot = 5,
                rosterPlayerNames = listOf("Unit7"),
            ),
            expectedConfidence = 77,
            expectedDetectedCount = 2,
            expectedValidDetectedCount = 2,
            expectedRosterCount = 1,
            expectedContributingCount = 1,
            expectedAverage = 100,
            expectedCoverage = 25,
            expectedMatches = listOf(0 to 0),
        )
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("Unit7"),
                candidateTeamSlot = 6,
                rosterPlayerNames = listOf("Unit7", "Unit7"),
            ),
            expectedConfidence = 77,
            expectedDetectedCount = 1,
            expectedValidDetectedCount = 1,
            expectedRosterCount = 2,
            expectedContributingCount = 1,
            expectedAverage = 100,
            expectedCoverage = 25,
            expectedMatches = listOf(0 to 0),
        )
    }

    @Test
    fun score_handlesSixRosterPlayersAndMoreThanFourContributingMatches() {
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("Unit7", "Nova", "Rin", "Kai"),
                candidateTeamSlot = 7,
                rosterPlayerNames = listOf("BenchA", "Unit7", "BenchB", "Nova", "Rin", "Kai"),
            ),
            expectedConfidence = 100,
            expectedDetectedCount = 4,
            expectedValidDetectedCount = 4,
            expectedRosterCount = 6,
            expectedContributingCount = 4,
            expectedAverage = 100,
            expectedCoverage = 100,
            expectedMatches = listOf(0 to 1, 1 to 3, 2 to 4, 3 to 5),
        )
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("Unit7", "Nova", "Rin", "Kai", "Extra"),
                candidateTeamSlot = 8,
                rosterPlayerNames = listOf("Unit7", "Nova", "Rin", "Kai", "Extra"),
            ),
            expectedConfidence = 100,
            expectedDetectedCount = 5,
            expectedValidDetectedCount = 5,
            expectedRosterCount = 5,
            expectedContributingCount = 5,
            expectedAverage = 100,
            expectedCoverage = 100,
            expectedMatches = listOf(0 to 0, 1 to 1, 2 to 2, 3 to 3, 4 to 4),
        )
    }

    @Test
    fun score_isDeterministicAndPreservesInputs() {
        val detected = mutableListOf<String?>("Unit7", "Nova", "Rin", "Kai")
        val roster = mutableListOf<String?>("Unit7", "Nova", "Rin", "Kai")
        val firstScore = TeamCandidateScorer.score(detected, 9, roster)
        val secondScore = TeamCandidateScorer.score(detected, 9, roster)

        assertEquals(firstScore, secondScore)
        assertEquals(listOf("Unit7", "Nova", "Rin", "Kai"), detected)
        assertEquals(listOf("Unit7", "Nova", "Rin", "Kai"), roster)
    }

    @Test
    fun score_rejectsInvalidCandidateSlots() {
        assertIllegalArgumentException {
            TeamCandidateScorer.score(listOf("Unit7"), 0, listOf("Unit7"))
        }
        assertIllegalArgumentException {
            TeamCandidateScorer.score(listOf("Unit7"), 13, listOf("Unit7"))
        }
    }

    @Test
    fun score_doesNotAddSpeculativeConfusionMappings() {
        assertScore(
            TeamCandidateScorer.score(
                detectedPlayerNames = listOf("5S", "Nova", "Rin", "Kai"),
                candidateTeamSlot = 10,
                rosterPlayerNames = listOf("55", "Nova", "Rin", "Kai"),
            ),
            expectedConfidence = 92,
            expectedDetectedCount = 4,
            expectedValidDetectedCount = 4,
            expectedRosterCount = 4,
            expectedContributingCount = 3,
            expectedAverage = 100,
            expectedCoverage = 75,
            expectedMatches = listOf(1 to 1, 2 to 2, 3 to 3),
        )
    }

    private fun assertScore(
        score: TeamCandidateScore,
        expectedConfidence: Int,
        expectedDetectedCount: Int,
        expectedValidDetectedCount: Int,
        expectedRosterCount: Int,
        expectedContributingCount: Int,
        expectedAverage: Int,
        expectedCoverage: Int,
        expectedMatches: List<Pair<Int, Int>>,
    ) {
        assertEquals(expectedConfidence, score.confidenceScore)
        assertEquals(expectedDetectedCount, score.detectedPlayerCount)
        assertEquals(expectedValidDetectedCount, score.validDetectedPlayerCount)
        assertEquals(expectedRosterCount, score.rosterPlayerCount)
        assertEquals(expectedContributingCount, score.contributingMatchCount)
        assertEquals(expectedAverage, score.averageMatchedPlayerScore)
        assertEquals(expectedCoverage, score.coverageScore)
        assertEquals(expectedMatches, score.playerMatches.map { it.detectedPlayerIndex to it.rosterPlayerIndex })
        assertTrue(score.playerMatches.all { it.contributesToScore })
        assertEquals(
            score.playerMatches.size,
            score.playerMatches.map { it.detectedPlayerIndex }.toSet().size,
        )
        assertEquals(
            score.playerMatches.size,
            score.playerMatches.map { it.rosterPlayerIndex }.toSet().size,
        )
    }

    private fun assertIllegalArgumentException(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException.")
        } catch (_: IllegalArgumentException) {
        }
    }
}
