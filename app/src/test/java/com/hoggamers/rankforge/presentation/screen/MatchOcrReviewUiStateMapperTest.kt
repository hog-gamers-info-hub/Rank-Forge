package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.matching.RowTeamAssignmentSafetyResult
import com.hoggamers.rankforge.domain.matching.TeamAssignmentSafetyReason
import com.hoggamers.rankforge.domain.matching.TeamAssignmentSafetyStatus
import com.hoggamers.rankforge.domain.matching.TeamCandidateScore
import com.hoggamers.rankforge.domain.matching.TeamMatchConfidenceAssessment
import com.hoggamers.rankforge.domain.matching.TeamMatchConfidenceReason
import com.hoggamers.rankforge.domain.matching.TeamMatchConfidenceTier
import com.hoggamers.rankforge.domain.matching.TopTeamCandidateSuggestion
import com.hoggamers.rankforge.domain.matching.TopTeamCandidateSuggestions
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewField
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewFieldType
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewReason
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewSeverity
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchOcrReviewUiStateMapperTest {
    @Test
    fun map_returnsReadyStateWithTwelveOrderedRows() {
        val state = readyState()

        assertEquals(12, state.rowCount)
        assertEquals((0..11).toList(), state.rows.map { it.rowIndex })
        assertEquals("1", state.rows.first().expectedPlacementLabel)
        assertEquals("Synthetic Unit 1", state.rows.first().detectedPlayerNameEvidenceLabel)
    }

    @Test
    fun map_countsBlockingRows() {
        val state = readyState()

        assertEquals(1, state.blockerCount)
        assertEquals(MatchOcrReviewSeverity.BLOCKING, state.rows[0].severity)
        assertTrue(state.rows[0].blockerLabels.contains("Placement: Missing"))
    }

    @Test
    fun map_countsWarningRowsWithoutCountingBlockingRowsAsWarnings() {
        val state = readyState()

        assertEquals(1, state.warningCount)
        assertEquals(MatchOcrReviewSeverity.WARNING, state.rows[1].severity)
        assertTrue(state.rows[1].warningLabels.contains("Safety: Review required"))
    }

    @Test
    fun map_setsManualReviewRequiredWhenAnyWarningOrBlockerExists() {
        val state = readyState()

        assertTrue(state.manualReviewRequired)
        assertEquals(10, state.safeRowCount)
        assertEquals(1, state.reviewRequiredRowCount)
        assertEquals(1, state.manualRequiredRowCount)
    }

    @Test
    fun map_preservesSuggestionConfidenceAndSafetyDisplayEvidence() {
        val state = readyState()
        val row = state.rows[2]

        assertEquals("3", row.suggestedTeamSlotDisplayValue)
        assertEquals("94", row.confidenceScoreDisplayValue)
        assertEquals("Automatic candidate", row.confidenceTierLabel)
        assertEquals("Safe automatic assignment", row.assignmentSafetyStatusLabel)
        assertEquals(
            listOf("Rank 1: Slot 3, confidence 94, matches 4, coverage 100"),
            row.topThreeSuggestionsSummary,
        )
    }

    @Test
    fun map_returnsEmptyStateForNoRows() {
        val state = MatchOcrReviewUiStateMapper.map(
            MatchOcrReviewDisplayInput(
                tournamentId = "synthetic-tournament",
                matchId = "synthetic-match",
                rows = emptyList(),
            ),
        )

        assertTrue(state is MatchOcrReviewUiState.Empty)
    }

    @Test
    fun map_returnsErrorForNonTwelveRowInput() {
        val state = MatchOcrReviewUiStateMapper.map(
            MatchOcrReviewDisplayInput(
                tournamentId = "synthetic-tournament",
                matchId = "synthetic-match",
                rows = listOf(rowInput(0)),
            ),
        )

        assertTrue(state is MatchOcrReviewUiState.Error)
    }

    @Test
    fun map_setsUnavailableEvidenceFlagWhenDownstreamEvidenceIsMissing() {
        val rows = (0..11).map { index ->
            rowInput(index, safetyStatus = null, confidenceTier = null, suggestions = null)
        }
        val state = MatchOcrReviewUiStateMapper.map(
            MatchOcrReviewDisplayInput(
                tournamentId = "synthetic-tournament",
                matchId = "synthetic-match",
                rows = rows,
            ),
        ) as MatchOcrReviewUiState.Ready

        assertTrue(state.hasUnavailableEvidence)
        assertFalse(state.rows.first().blockerLabels.isEmpty())
    }

    private fun readyState(): MatchOcrReviewUiState.Ready =
        MatchOcrReviewUiStateMapper.map(
            MatchOcrReviewDisplayInput(
                tournamentId = "synthetic-tournament",
                matchId = "synthetic-match",
                matchDisplayLabel = "Synthetic Match",
                rows = (0..11).map { index ->
                    when (index) {
                        0 -> rowInput(
                            index = index,
                            placementStatus = OcrReviewStatus.MISSING,
                            placementSeverity = OcrReviewSeverity.BLOCKING,
                            detectedPlacementValue = null,
                            confidenceTier = TeamMatchConfidenceTier.MANUAL_REQUIRED,
                            safetyStatus = TeamAssignmentSafetyStatus.MANUAL_REQUIRED,
                            safetyReasons = setOf(TeamAssignmentSafetyReason.NOT_AUTOMATIC_TIER),
                        )
                        1 -> rowInput(
                            index = index,
                            playerNameStatus = OcrReviewStatus.UNCERTAIN,
                            playerNameSeverity = OcrReviewSeverity.WARNING,
                            confidenceTier = TeamMatchConfidenceTier.CONFIRMATION_REQUIRED,
                            safetyStatus = TeamAssignmentSafetyStatus.REVIEW_REQUIRED,
                            safetyReasons = setOf(TeamAssignmentSafetyReason.INSUFFICIENT_CANDIDATE_LEAD),
                        )
                        else -> rowInput(index)
                    }
                },
            ),
        ) as MatchOcrReviewUiState.Ready

    private fun rowInput(
        index: Int,
        detectedPlacementValue: Int? = index + 1,
        detectedKillValue: Int? = index,
        detectedPlayerName: String? = "Synthetic Unit ${index + 1}",
        placementStatus: OcrReviewStatus = OcrReviewStatus.ACCEPTED,
        placementSeverity: OcrReviewSeverity = OcrReviewSeverity.INFORMATIONAL,
        playerNameStatus: OcrReviewStatus = OcrReviewStatus.ACCEPTED,
        playerNameSeverity: OcrReviewSeverity = OcrReviewSeverity.INFORMATIONAL,
        killStatus: OcrReviewStatus = OcrReviewStatus.ACCEPTED,
        killSeverity: OcrReviewSeverity = OcrReviewSeverity.INFORMATIONAL,
        confidenceTier: TeamMatchConfidenceTier? = TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE,
        safetyStatus: TeamAssignmentSafetyStatus? = TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT,
        safetyReasons: Set<TeamAssignmentSafetyReason> = emptySet(),
        suggestions: TopTeamCandidateSuggestions? = suggestions(index + 1),
    ): MatchOcrReviewRowEvidenceInput {
        val confidenceAssessment = confidenceTier?.let {
            confidenceAssessment(
                tier = it,
                suggestions = suggestions,
            )
        }
        return MatchOcrReviewRowEvidenceInput(
            rowIndex = index,
            expectedPlacementId = index + 1,
            detectedPlacementValue = detectedPlacementValue,
            detectedKillValue = detectedKillValue,
            detectedPlayerName = detectedPlayerName,
            ocrFields = listOf(
                field(OcrReviewFieldType.PLACEMENT, placementStatus, placementSeverity),
                field(OcrReviewFieldType.PLAYER_NAME, playerNameStatus, playerNameSeverity),
                field(OcrReviewFieldType.KILL, killStatus, killSeverity),
            ),
            suggestions = suggestions,
            confidenceAssessment = confidenceAssessment,
            safetyResult = if (safetyStatus != null && confidenceAssessment != null) {
                RowTeamAssignmentSafetyResult(
                    rowIndex = index,
                    confidenceAssessment = confidenceAssessment,
                    safetyStatus = safetyStatus,
                    proposedTeamSlot = suggestions?.suggestions?.firstOrNull()?.teamCandidateScore?.candidateTeamSlot,
                    reasons = safetyReasons,
                )
            } else {
                null
            },
        )
    }

    private fun field(
        type: OcrReviewFieldType,
        status: OcrReviewStatus,
        severity: OcrReviewSeverity,
    ): OcrReviewField = OcrReviewField(
        type = type,
        status = status,
        severity = severity,
        reason = when (status) {
            OcrReviewStatus.ACCEPTED -> OcrReviewReason.Accepted
            OcrReviewStatus.MISSING -> OcrReviewReason.Missing
            OcrReviewStatus.INVALID -> OcrReviewReason.PlacementInvalid
            OcrReviewStatus.AMBIGUOUS -> OcrReviewReason.Ambiguous
            OcrReviewStatus.DUPLICATE -> OcrReviewReason.Duplicate
            OcrReviewStatus.UNSUPPORTED -> OcrReviewReason.ParserOutputUnavailable
            OcrReviewStatus.UNCERTAIN -> OcrReviewReason.ParserOutputUnavailable
        },
        manualReviewRequired = severity != OcrReviewSeverity.INFORMATIONAL,
        evidence = emptyList(),
    )

    private fun confidenceAssessment(
        tier: TeamMatchConfidenceTier,
        suggestions: TopTeamCandidateSuggestions?,
    ): TeamMatchConfidenceAssessment = TeamMatchConfidenceAssessment(
        tier = tier,
        selectedSuggestion = suggestions?.suggestions?.firstOrNull(),
        suggestions = suggestions ?: TopTeamCandidateSuggestions(
            detectedPlayerCount = 0,
            evaluatedCandidateCount = 0,
            suggestions = emptyList(),
        ),
        reason = when (tier) {
            TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE -> TeamMatchConfidenceReason.MEETS_AUTOMATIC_THRESHOLD
            TeamMatchConfidenceTier.CONFIRMATION_REQUIRED -> TeamMatchConfidenceReason.MEETS_CONFIRMATION_THRESHOLD
            TeamMatchConfidenceTier.MANUAL_REQUIRED -> TeamMatchConfidenceReason.BELOW_CONFIRMATION_THRESHOLD
        },
    )

    private fun suggestions(teamSlot: Int): TopTeamCandidateSuggestions = TopTeamCandidateSuggestions(
        detectedPlayerCount = 4,
        evaluatedCandidateCount = 1,
        suggestions = listOf(
            TopTeamCandidateSuggestion(
                rank = 1,
                teamCandidateScore = TeamCandidateScore(
                    candidateTeamSlot = teamSlot,
                    confidenceScore = 94,
                    detectedPlayerCount = 4,
                    validDetectedPlayerCount = 4,
                    rosterPlayerCount = 4,
                    contributingMatchCount = 4,
                    averageMatchedPlayerScore = 100,
                    coverageScore = 100,
                    playerMatches = emptyList(),
                ),
            ),
        ),
    )
}
