package com.hoggamers.rankforge.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreboardTeamIdentificationEvaluatorTest {
    @Test
    fun exactEvidenceProducesExpectedSlotAndSafeAutomaticAssignment() {
        val result = evaluate(
            rows = listOf(evidence(0, "Alpha", "Bravo", "Charlie", "Delta")),
            teams = listOf(team(1, "Alpha", "Bravo", "Charlie", "Delta"), team(2, "OtherA", "OtherB")),
        )
        val row = result.rows.single()

        assertEquals(1, row.suggestedTeamSlot)
        assertEquals(1, row.identifiedTeamSlot)
        assertEquals(TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE, row.confidenceAssessment.tier)
        assertEquals(TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT, row.assignmentSafety.safetyStatus)
        assertEquals(1, result.assignmentSafety.safeAssignmentCount)
    }

    @Test
    fun smallOcrErrorsStillUseTheExistingConfidenceClassification() {
        val result = evaluate(
            rows = listOf(evidence(0, "Alph", "Brav0", "Charlle", "Delt")),
            teams = listOf(team(1, "Alpha", "Bravo", "Charlie", "Delta"), team(2, "OtherA")),
        )
        val row = result.rows.single()

        assertEquals(1, row.suggestedTeamSlot)
        assertEquals(TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE, row.confidenceAssessment.tier)
        assertEquals(4, row.suggestions.suggestions.first().teamCandidateScore.contributingMatchCount)
    }

    @Test
    fun multipleImperfectNamesCollectivelyIdentifyOneTeam() {
        val result = evaluate(
            rows = listOf(evidence(0, "Alph", "Brav", "Charli", "Delt")),
            teams = listOf(team(1, "Alpha", "Bravo", "Charlie", "Delta"), team(2, "Zulu")),
        )

        assertEquals(1, result.rows.single().suggestedTeamSlot)
        assertEquals(4, result.rows.single().suggestions.suggestions.first().teamCandidateScore.contributingMatchCount)
    }

    @Test
    fun unrelatedEvidenceDoesNotBecomeSafeAutomaticAssignment() {
        val row = evaluate(
            rows = listOf(evidence(0, "UnrelatedA", "UnrelatedB", "UnrelatedC", "UnrelatedD")),
            teams = listOf(team(1, "Alpha", "Bravo", "Charlie", "Delta")),
        ).rows.single()

        assertEquals(TeamMatchConfidenceTier.MANUAL_REQUIRED, row.confidenceAssessment.tier)
        assertEquals(TeamAssignmentSafetyStatus.MANUAL_REQUIRED, row.assignmentSafety.safetyStatus)
        assertEquals(1, row.assignmentSafety.proposedTeamSlot)
        assertEquals(null, row.identifiedTeamSlot)
    }

    @Test
    fun preservesTopThreeOrderingAndConfidenceEvidence() {
        val result = evaluate(
            rows = listOf(evidence(0, "Alpha", "Bravo", "Charlie", "Delta")),
            teams = listOf(
                team(3, "Alpha", "Bravo", "Charlie", "Delta"),
                team(1, "Alpha", "Bravo"),
                team(2, "Alpha"),
                team(4, "Unrelated"),
            ),
        )
        val suggestions = result.rows.single().suggestions.suggestions

        assertEquals(listOf(3, 1, 2), suggestions.map { it.teamCandidateScore.candidateTeamSlot })
        assertEquals(listOf(1, 2, 3), suggestions.map { it.rank })
        assertNotNull(result.rows.single().confidenceAssessment.selectedSuggestion)
        assertEquals(listOf("Alpha", "Bravo", "Charlie", "Delta"), result.rows.single().detectedPlayerNames)
    }

    @Test
    fun duplicateTeamCandidatesRequireReviewAcrossRows() {
        val result = evaluate(
            rows = listOf(
                evidence(0, "Alpha", "Bravo", "Charlie", "Delta"),
                evidence(1, "Alpha", "Bravo", "Charlie", "Delta"),
            ),
            teams = listOf(team(1, "Alpha", "Bravo", "Charlie", "Delta"), team(2, "Other")),
        )

        assertEquals(2, result.assignmentSafety.rowResults.size)
        assertTrue(result.assignmentSafety.rowResults.all { row ->
            row.safetyStatus == TeamAssignmentSafetyStatus.REVIEW_REQUIRED &&
                TeamAssignmentSafetyReason.DUPLICATE_TEAM_CANDIDATE in row.reasons
        })
        assertEquals(0, result.assignmentSafety.safeAssignmentCount)
    }

    @Test
    fun insufficientEvidenceRemainsReviewRequiredAndStillExposesUsefulSlotWhenSuggested() {
        val result = evaluate(
            rows = listOf(evidence(0, "Alpha")),
            teams = listOf(team(1, "Alpha", "Bravo", "Charlie", "Delta")),
        )
        val row = result.rows.single()

        assertEquals(TeamMatchConfidenceTier.CONFIRMATION_REQUIRED, row.confidenceAssessment.tier)
        assertEquals(TeamAssignmentSafetyStatus.REVIEW_REQUIRED, row.assignmentSafety.safetyStatus)
        assertEquals(1, row.suggestedTeamSlot)
        assertEquals(1, row.identifiedTeamSlot)
    }

    @Test
    fun oneContributingFuzzyMatchExposesIdentificationWithoutAutomaticSafety() {
        val row = evaluate(
            rows = listOf(evidence(0, "Alph")),
            teams = listOf(team(1, "Alpha", "Bravo", "Charlie", "Delta")),
        ).rows.single()

        assertEquals(1, row.suggestedTeamSlot)
        assertEquals(1, row.identifiedTeamSlot)
        assertEquals(1, row.suggestions.suggestions.first().teamCandidateScore.contributingMatchCount)
        assertTrue(row.assignmentSafety.safetyStatus != TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT)
    }

    @Test
    fun emptyEvidenceHasNoPromotedPlayerNameAndRemainsManual() {
        val row = evaluate(
            rows = listOf(evidence(0)),
            teams = listOf(team(1, "Alpha", "Bravo")),
        ).rows.single()

        assertTrue(row.detectedPlayerNames.isEmpty())
        assertEquals(1, row.suggestedTeamSlot)
        assertEquals(null, row.identifiedTeamSlot)
        assertEquals(TeamMatchConfidenceReason.BELOW_CONFIRMATION_THRESHOLD, row.confidenceAssessment.reason)
    }

    private fun evaluate(
        rows: List<ScoreboardRowPlayerEvidence>,
        teams: List<TeamCandidateRosterInput>,
    ) = ScoreboardTeamIdentificationEvaluator.evaluate(rows, teams)

    private fun evidence(rowIndex: Int, vararg names: String) = ScoreboardRowPlayerEvidence(
        rowIndex = rowIndex,
        expectedPlacementId = rowIndex + 1,
        detectedPlayerNames = names.toList(),
    )

    private fun team(slot: Int, vararg names: String) = TeamCandidateRosterInput(
        teamSlot = slot,
        rosterPlayerNames = names.toList(),
    )
}
