package com.hoggamers.rankforge.domain.tournament

enum class ScoringVerificationState {
    VALID,
    NO_FINALIZED_MATCHES,
    INVALID,
}

enum class ScoringVerificationIssueCode {
    INVALID_FINALIZED_MATCH,
    MATCH_TOTAL_MISMATCH,
    CUMULATIVE_TOTAL_MISMATCH,
}

data class ScoringVerificationIssue(
    val code: ScoringVerificationIssueCode,
    val matchId: String? = null,
    val validationErrors: Map<Int, Set<MatchResultValidationError>> = emptyMap(),
)

data class VerifiedMatchTeamScore(
    val teamSlotNumber: Int,
    val confirmedPlacement: Int,
    val confirmedKills: Int,
    val positionPoints: Int,
    val killPoints: Int,
    val matchTotal: Int,
)

data class MatchScoringVerification(
    val matchId: String,
    val validation: MatchResultValidation,
    val teamScores: List<VerifiedMatchTeamScore>,
    val matchTotalConsistent: Boolean,
) {
    val isValid: Boolean
        get() = validation.isValid && matchTotalConsistent
}

data class ScoringVerificationResult(
    val state: ScoringVerificationState,
    val finalizedMatchIds: List<String>,
    val excludedDraftMatchIds: List<String>,
    val duplicateFinalizedMatchIds: List<String>,
    val matchVerifications: List<MatchScoringVerification>,
    val standings: List<CumulativeTournamentStanding>,
    val tieBreakStandings: List<TieBreakStanding>,
    val cumulativeTotalsConsistent: Boolean,
    val tieBreakOrderingVerified: Boolean,
    val issues: List<ScoringVerificationIssue>,
) {
    val isValid: Boolean
        get() = state == ScoringVerificationState.VALID &&
            cumulativeTotalsConsistent &&
            tieBreakOrderingVerified
}

/**
 * Verifies finalized scoring by composing the existing scoring and standings engines.
 *
 * Match totals are derived values and are therefore verified from confirmed placement and kill
 * values rather than read from or stored independently. Duplicate finalized IDs are represented
 * separately because the standings engine intentionally counts each finalized match ID once.
 */
class ScoringVerificationEngine(
    private val positionPointsEngine: PositionPointsEngine = PositionPointsEngine(),
    private val killPointsEngine: KillPointsEngine = KillPointsEngine(),
    private val matchTotalEngine: MatchTotalEngine = MatchTotalEngine(),
    private val cumulativeStandingsEngine: CumulativeTournamentStandingsEngine =
        CumulativeTournamentStandingsEngine(),
    private val tieBreakRules: TieBreakRules = TieBreakRules(),
    private val validateMatchResult: ValidateMatchResultUseCase = ValidateMatchResultUseCase(),
) {
    operator fun invoke(matches: List<Match>): ScoringVerificationResult {
        val finalizedMatches = matches
            .asSequence()
            .filter { it.status == MatchStatus.FINALIZED }
            .sortedWith(compareBy<Match> { it.matchNumber }.thenBy { it.id })
            .toList()
        val uniqueFinalizedMatches = finalizedMatches.distinctBy { it.id }
        val excludedDraftMatchIds = matches
            .asSequence()
            .filter { it.status == MatchStatus.DRAFT }
            .map { it.id }
            .distinct()
            .sorted()
            .toList()
        val duplicateFinalizedMatchIds = finalizedMatches
            .groupingBy { it.id }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sorted()

        if (uniqueFinalizedMatches.isEmpty()) {
            return ScoringVerificationResult(
                state = ScoringVerificationState.NO_FINALIZED_MATCHES,
                finalizedMatchIds = emptyList(),
                excludedDraftMatchIds = excludedDraftMatchIds,
                duplicateFinalizedMatchIds = duplicateFinalizedMatchIds,
                matchVerifications = emptyList(),
                standings = emptyList(),
                tieBreakStandings = emptyList(),
                cumulativeTotalsConsistent = false,
                tieBreakOrderingVerified = true,
                issues = emptyList(),
            )
        }

        val matchVerifications = uniqueFinalizedMatches.map(::verifyMatch)
        val matchIssues = matchVerifications
            .filterNot { it.isValid }
            .map { verification ->
                ScoringVerificationIssue(
                    code = if (verification.validation.isValid) {
                        ScoringVerificationIssueCode.MATCH_TOTAL_MISMATCH
                    } else {
                        ScoringVerificationIssueCode.INVALID_FINALIZED_MATCH
                    },
                    matchId = verification.matchId,
                    validationErrors = verification.validation.errorsByTeamSlot,
                )
            }

        if (matchIssues.isNotEmpty()) {
            return ScoringVerificationResult(
                state = ScoringVerificationState.INVALID,
                finalizedMatchIds = uniqueFinalizedMatches.map { it.id },
                excludedDraftMatchIds = excludedDraftMatchIds,
                duplicateFinalizedMatchIds = duplicateFinalizedMatchIds,
                matchVerifications = matchVerifications,
                standings = emptyList(),
                tieBreakStandings = emptyList(),
                cumulativeTotalsConsistent = false,
                tieBreakOrderingVerified = false,
                issues = matchIssues,
            )
        }

        val standings = cumulativeStandingsEngine(uniqueFinalizedMatches)
        val expectedStandings = expectedStandings(uniqueFinalizedMatches, matchVerifications)
        val cumulativeTotalsConsistent = standings == expectedStandings
        val tieBreakStandings = tieBreakRules(standings)
        val tieBreakOrderingVerified = tieBreakStandings == tieBreakRules(standings)
        val issues = buildList {
            if (!cumulativeTotalsConsistent) {
                add(ScoringVerificationIssue(ScoringVerificationIssueCode.CUMULATIVE_TOTAL_MISMATCH))
            }
            if (matchVerifications.any { !it.matchTotalConsistent }) {
                add(ScoringVerificationIssue(ScoringVerificationIssueCode.MATCH_TOTAL_MISMATCH))
            }
        }

        return ScoringVerificationResult(
            state = if (issues.isEmpty() && tieBreakOrderingVerified) {
                ScoringVerificationState.VALID
            } else {
                ScoringVerificationState.INVALID
            },
            finalizedMatchIds = uniqueFinalizedMatches.map { it.id },
            excludedDraftMatchIds = excludedDraftMatchIds,
            duplicateFinalizedMatchIds = duplicateFinalizedMatchIds,
            matchVerifications = matchVerifications,
            standings = standings,
            tieBreakStandings = tieBreakStandings,
            cumulativeTotalsConsistent = cumulativeTotalsConsistent,
            tieBreakOrderingVerified = tieBreakOrderingVerified,
            issues = issues,
        )
    }

    private fun verifyMatch(match: Match): MatchScoringVerification {
        val participantResults = match.finalizedParticipantResultsOrNull()
        val validation = if (participantResults == null) {
            validateMatchResult(match)
        } else {
            validateMatchResult.validateParticipantResults(
                rows = participantResults.map { result ->
                    MatchResultRowInput(
                        teamSlotNumber = result.teamSlotNumber,
                        placement = result.placement?.toString(),
                        kills = result.kills.toString(),
                        participationStatus = result.participationStatus,
                    )
                },
                expectedTeamSlots = participantResults.map { it.teamSlotNumber },
            )
        }
        if (!validation.isValid) {
            return MatchScoringVerification(
                matchId = match.id,
                validation = validation,
                teamScores = emptyList(),
                matchTotalConsistent = false,
            )
        }

        val teamScores = (participantResults ?: match.placements.map { placement ->
            val confirmedKills = checkNotNull(match.kills.firstOrNull {
                it.teamSlotNumber == placement.teamSlotNumber
            }).kills
            MatchParticipantResult(
                teamSlotNumber = placement.teamSlotNumber,
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = placement.position,
                kills = confirmedKills,
            )
        })
            .filter { it.participationStatus == MatchParticipationStatus.PARTICIPATED }
            .sortedBy { it.teamSlotNumber }
            .map { result ->
                val confirmedPlacement = requireNotNull(result.placement)
                val positionPoints = positionPointsEngine(confirmedPlacement)
                val killPoints = killPointsEngine(result.kills)
                VerifiedMatchTeamScore(
                    teamSlotNumber = result.teamSlotNumber,
                    confirmedPlacement = confirmedPlacement,
                    confirmedKills = result.kills,
                    positionPoints = positionPoints,
                    killPoints = killPoints,
                    matchTotal = matchTotalEngine(confirmedPlacement, result.kills),
                )
            }
        val matchTotalConsistent = teamScores.all { score ->
            score.matchTotal == score.positionPoints + score.killPoints
        }

        return MatchScoringVerification(
            matchId = match.id,
            validation = validation,
            teamScores = teamScores,
            matchTotalConsistent = matchTotalConsistent,
        )
    }

    private fun expectedStandings(
        matches: List<Match>,
        matchVerifications: List<MatchScoringVerification>,
    ): List<CumulativeTournamentStanding> {
        val totalsByTeamSlot = mutableMapOf<Int, MutableStandingTotals>()
        matches.zip(matchVerifications).forEach { (match, verification) ->
            match.finalizedParticipantResultsOrNull()
                ?.filter { it.participationStatus == MatchParticipationStatus.NO_SHOW }
                ?.forEach { result ->
                    totalsByTeamSlot.getOrPut(result.teamSlotNumber) { MutableStandingTotals() }
                }
            verification.teamScores.forEach { score ->
                val totals = totalsByTeamSlot.getOrPut(score.teamSlotNumber) {
                    MutableStandingTotals()
                }
                totals.totalPositionPoints += score.positionPoints
                totals.totalKillPoints += score.killPoints
                totals.totalPoints += score.matchTotal
                if (score.confirmedPlacement == 1) totals.firstPlaceFinishes++
                totals.latestMatchPlacement = score.confirmedPlacement
                totals.matchesIncluded++
            }
        }
        return totalsByTeamSlot
            .toSortedMap()
            .map { (teamSlotNumber, totals) ->
                CumulativeTournamentStanding(
                    teamSlotNumber = teamSlotNumber,
                    totalPositionPoints = totals.totalPositionPoints,
                    totalKillPoints = totals.totalKillPoints,
                    totalPoints = totals.totalPoints,
                    firstPlaceFinishes = totals.firstPlaceFinishes,
                    latestMatchPlacement = totals.latestMatchPlacement,
                    matchesIncluded = totals.matchesIncluded,
                )
            }
    }

    private class MutableStandingTotals {
        var totalPositionPoints: Int = 0
        var totalKillPoints: Int = 0
        var totalPoints: Int = 0
        var firstPlaceFinishes: Int = 0
        var latestMatchPlacement: Int? = null
        var matchesIncluded: Int = 0
    }
}

