package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.ocr.matchresult.AndroidMatchResultOcrPreviewProcessor
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewLocalFileResolver
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRunner
import com.hoggamers.rankforge.domain.matching.ScoreboardRowPlayerEvidence
import com.hoggamers.rankforge.domain.matching.ScoreboardTeamIdentificationEvaluator
import com.hoggamers.rankforge.domain.matching.TeamAssignmentSafetyReason
import com.hoggamers.rankforge.domain.matching.TeamAssignmentSafetyStatus
import com.hoggamers.rankforge.domain.matching.TeamCandidateRosterInput
import com.hoggamers.rankforge.domain.matching.TeamMatchConfidenceTier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MatchResultOcrPreviewModule {
    @Provides
    @Singleton
    fun provideMatchResultOcrPreviewRunner(
        assetRepository: MatchResultScreenshotAssetRepository,
        localImagePreserver: LocalImagePreserver,
    ): MatchResultOcrPreviewRunner = AndroidMatchResultOcrPreviewProcessor(
        assetRepository = assetRepository,
        localFileResolver = MatchResultOcrPreviewLocalFileResolver(
            localImagePreserver::resolveRelativePath,
        ),
    )
}

object MatchResultOcrPreviewTeamSuggestionMapper {
    fun map(
        preview: MatchResultOcrPreviewUiState,
        candidateTeams: List<TeamCandidateRosterInput>,
    ): List<MatchOcrReviewRowUiState>? {
        val ready = preview as? MatchResultOcrPreviewUiState.Ready ?: return null
        val rows = MatchResultOcrPreviewUiStateMapper.toReviewRows(preview) ?: return null
        if (candidateTeams.isEmpty()) return rows

        val evidence = ready.rows.map { row ->
            ScoreboardRowPlayerEvidence(
                rowIndex = row.position - 1,
                expectedPlacementId = row.position,
                detectedPlayerNames = row.slots
                    .sortedBy(MatchResultOcrPreviewSlotUiState::slot)
                    .mapNotNull { it.playerText.trim().takeIf(String::isNotBlank) },
            )
        }
        val evaluation = runCatching {
            ScoreboardTeamIdentificationEvaluator.evaluate(evidence, candidateTeams)
        }.getOrNull() ?: return rows
        val evaluationByRow = evaluation.rows.associateBy { it.rowIndex }

        return rows.map { row ->
            val result = evaluationByRow[row.rowIndex] ?: return@map row
            val safety = result.assignmentSafety
            val safePrefill = safety.safetyStatus == TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT &&
                safety.proposedTeamSlot != null
            val blockers = row.blockerLabels
                .filterNot { it.startsWith(TEAM_ASSIGNMENT_PREFIX) }
                .toMutableList()
            val warnings = row.warningLabels.toMutableList()
            when (safety.safetyStatus) {
                TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT -> Unit
                TeamAssignmentSafetyStatus.REVIEW_REQUIRED ->
                    blockers += "Team assignment: review required (${safety.reasons.toLabels()})"
                TeamAssignmentSafetyStatus.MANUAL_REQUIRED ->
                    blockers += "Team assignment: manual assignment required (${safety.reasons.toLabels()})"
            }
            val selected = result.confidenceAssessment.selectedSuggestion
            row.copy(
                suggestedTeamSlotDisplayValue = safety.proposedTeamSlot?.toString() ?: "Unavailable",
                originalSuggestedTeamSlot = safety.proposedTeamSlot.takeIf { safePrefill },
                confidenceScoreDisplayValue = selected?.teamCandidateScore?.confidenceScore?.toString()
                    ?: "Unavailable",
                confidenceTierLabel = result.confidenceAssessment.tier.toLabel(),
                assignmentSafetyStatusLabel = safety.safetyStatus.toLabel(),
                topThreeSuggestionsSummary = result.suggestions.toSummary(),
                warningLabels = warnings.distinct(),
                blockerLabels = blockers.distinct(),
                severity = when {
                    blockers.isNotEmpty() -> MatchOcrReviewSeverity.BLOCKING
                    warnings.isNotEmpty() -> MatchOcrReviewSeverity.WARNING
                    else -> MatchOcrReviewSeverity.INFORMATIONAL
                },
            )
        }
    }

    private fun TeamAssignmentSafetyStatus.toLabel(): String = when (this) {
        TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT -> "Safe automatic assignment"
        TeamAssignmentSafetyStatus.REVIEW_REQUIRED -> "Review required"
        TeamAssignmentSafetyStatus.MANUAL_REQUIRED -> "Manual required"
    }

    private fun TeamMatchConfidenceTier.toLabel(): String = when (this) {
        TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE -> "Automatic candidate"
        TeamMatchConfidenceTier.CONFIRMATION_REQUIRED -> "Confirmation required"
        TeamMatchConfidenceTier.MANUAL_REQUIRED -> "Manual required"
    }

    private fun com.hoggamers.rankforge.domain.matching.TopTeamCandidateSuggestions.toSummary(): List<String> {
        if (suggestions.isEmpty()) return listOf("No suggestions")
        return suggestions.take(3).map { suggestion ->
            val score = suggestion.teamCandidateScore
            "Rank ${suggestion.rank}: Slot ${score.candidateTeamSlot}, confidence ${score.confidenceScore}, " +
                "matches ${score.contributingMatchCount}, coverage ${score.coverageScore}"
        }
    }

    private fun Set<TeamAssignmentSafetyReason>.toLabels(): String =
        if (isEmpty()) "safety review" else joinToString(", ") { it.name }

    private const val TEAM_ASSIGNMENT_PREFIX = "Team assignment:"
}
