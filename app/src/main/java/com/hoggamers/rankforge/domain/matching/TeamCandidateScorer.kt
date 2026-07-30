package com.hoggamers.rankforge.domain.matching

import com.hoggamers.rankforge.domain.tournament.TeamSlot
import kotlin.math.min

object TeamCandidateScorer {
    fun score(
        detectedPlayerNames: List<String?>,
        candidateTeamSlot: Int,
        rosterPlayerNames: List<String?>,
    ): TeamCandidateScore {
        require(candidateTeamSlot in TeamSlot.SLOT_NUMBERS) {
            "Team slot number must be between 1 and 12."
        }

        val selectedMatches = selectedContributingMatches(
            detectedPlayerNames = detectedPlayerNames,
            rosterPlayerNames = rosterPlayerNames,
        )
        val contributingMatchCount = selectedMatches.size
        val averageMatchedPlayerScore = averageMatchedPlayerScore(selectedMatches)
        val coverageScore = coverageScore(contributingMatchCount)

        return TeamCandidateScore(
            candidateTeamSlot = candidateTeamSlot,
            confidenceScore = confidenceScore(averageMatchedPlayerScore, coverageScore),
            detectedPlayerCount = detectedPlayerNames.size,
            validDetectedPlayerCount = detectedPlayerNames.count {
                PlayerNameComparisonNormalizer.normalize(it) != null
            },
            rosterPlayerCount = rosterPlayerNames.size,
            contributingMatchCount = contributingMatchCount,
            averageMatchedPlayerScore = averageMatchedPlayerScore,
            coverageScore = coverageScore,
            playerMatches = selectedMatches.sortedWith(
                compareBy<TeamCandidatePlayerMatch> { it.detectedPlayerIndex }
                    .thenBy { it.rosterPlayerIndex },
            ),
        )
    }

    private fun selectedContributingMatches(
        detectedPlayerNames: List<String?>,
        rosterPlayerNames: List<String?>,
    ): List<TeamCandidatePlayerMatch> {
        val candidatePairs = detectedPlayerNames.flatMapIndexed { detectedIndex, detectedName ->
            rosterPlayerNames.mapIndexed { rosterIndex, rosterName ->
                val assessment = PlayerNameSimilarityMatcher.compare(detectedName, rosterName)
                CandidatePair(
                    match = TeamCandidatePlayerMatch(
                        detectedPlayerIndex = detectedIndex,
                        rosterPlayerIndex = rosterIndex,
                        detectedOriginalName = detectedName,
                        rosterOriginalName = rosterName,
                        similarityAssessment = assessment,
                        contributesToScore = true,
                    ),
                    similarityScore = assessment.similarityScore,
                    distance = assessment.distance,
                    valid = assessment.comparisonType != PlayerNameComparisonType.INVALID_INPUT,
                )
            }
        }

        val usedDetectedIndexes = mutableSetOf<Int>()
        val usedRosterIndexes = mutableSetOf<Int>()
        val selectedMatches = mutableListOf<TeamCandidatePlayerMatch>()

        candidatePairs
            .asSequence()
            .filter { it.valid && it.similarityScore >= MINIMUM_CONTRIBUTING_PLAYER_SIMILARITY_SCORE }
            .sortedWith(
                compareByDescending<CandidatePair> { it.similarityScore }
                    .thenBy { it.distance ?: Int.MAX_VALUE }
                    .thenBy { it.match.detectedPlayerIndex }
                    .thenBy { it.match.rosterPlayerIndex },
            )
            .forEach { candidatePair ->
                val detectedIndex = candidatePair.match.detectedPlayerIndex
                val rosterIndex = candidatePair.match.rosterPlayerIndex

                if (detectedIndex !in usedDetectedIndexes && rosterIndex !in usedRosterIndexes) {
                    selectedMatches += candidatePair.match
                    usedDetectedIndexes += detectedIndex
                    usedRosterIndexes += rosterIndex
                }
            }

        return selectedMatches
    }

    private fun averageMatchedPlayerScore(matches: List<TeamCandidatePlayerMatch>): Int =
        if (matches.isEmpty()) {
            0
        } else {
            matches.sumOf { it.similarityAssessment.similarityScore } / matches.size
        }

    private fun coverageScore(contributingMatchCount: Int): Int {
        val coverageContributionCount = min(contributingMatchCount, REQUIRED_DETECTED_PLAYER_COUNT)
        return (coverageContributionCount * 100) / REQUIRED_DETECTED_PLAYER_COUNT
    }

    private fun confidenceScore(
        averageMatchedPlayerScore: Int,
        coverageScore: Int,
    ): Int =
        ((averageMatchedPlayerScore * AVERAGE_MATCH_WEIGHT + coverageScore * COVERAGE_WEIGHT) / 100)
            .coerceIn(0, 100)

    private data class CandidatePair(
        val match: TeamCandidatePlayerMatch,
        val similarityScore: Int,
        val distance: Int?,
        val valid: Boolean,
    )

    private const val MINIMUM_CONTRIBUTING_PLAYER_SIMILARITY_SCORE = 75
    private const val REQUIRED_DETECTED_PLAYER_COUNT = 4
    private const val AVERAGE_MATCH_WEIGHT = 70
    private const val COVERAGE_WEIGHT = 30
}
