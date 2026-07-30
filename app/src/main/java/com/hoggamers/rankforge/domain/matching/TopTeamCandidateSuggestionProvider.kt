package com.hoggamers.rankforge.domain.matching

object TopTeamCandidateSuggestionProvider {
    fun suggestTopThree(
        detectedPlayerNames: List<String?>,
        candidateTeams: List<TeamCandidateRosterInput>,
    ): TopTeamCandidateSuggestions {
        requireUniqueTeamSlots(candidateTeams)

        val orderedScores = candidateTeams
            .map { candidate ->
                TeamCandidateScorer.score(
                    detectedPlayerNames = detectedPlayerNames,
                    candidateTeamSlot = candidate.teamSlot,
                    rosterPlayerNames = candidate.rosterPlayerNames,
                )
            }
            .sortedWith(teamCandidateScoreOrdering)

        return TopTeamCandidateSuggestions(
            detectedPlayerCount = detectedPlayerNames.size,
            evaluatedCandidateCount = candidateTeams.size,
            suggestions = orderedScores
                .take(MAX_SUGGESTION_COUNT)
                .mapIndexed { index, teamCandidateScore ->
                    TopTeamCandidateSuggestion(
                        rank = index + 1,
                        teamCandidateScore = teamCandidateScore,
                    )
                },
        )
    }

    internal val teamCandidateScoreOrdering: Comparator<TeamCandidateScore> =
        compareByDescending<TeamCandidateScore> { it.confidenceScore }
            .thenByDescending { it.contributingMatchCount }
            .thenByDescending { it.averageMatchedPlayerScore }
            .thenByDescending { it.coverageScore }
            .thenBy { it.candidateTeamSlot }

    private fun requireUniqueTeamSlots(candidateTeams: List<TeamCandidateRosterInput>) {
        val duplicateTeamSlot = candidateTeams
            .groupingBy { it.teamSlot }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key

        require(duplicateTeamSlot == null) {
            "Duplicate candidate team slot: $duplicateTeamSlot."
        }
    }

    private const val MAX_SUGGESTION_COUNT = 3
}
