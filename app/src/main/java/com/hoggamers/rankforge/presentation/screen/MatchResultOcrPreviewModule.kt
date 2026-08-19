package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultOcrCacheRepository
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchResultLobbyOcrSlotRanker
import com.hoggamers.rankforge.data.ocr.matchresult.CachingMatchResultOcrPreviewRunner
import com.hoggamers.rankforge.data.ocr.matchresult.AndroidMatchResultOcrPreviewProcessor
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewLocalFileResolver
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRunner
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotAssignmentEvaluator
import com.hoggamers.rankforge.domain.matching.TeamAssignmentSafetyReason
import com.hoggamers.rankforge.domain.matching.TeamAssignmentSafetyStatus
import com.hoggamers.rankforge.domain.matching.TeamMatchConfidenceTier
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
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
        cacheRepository: MatchResultOcrCacheRepository,
        localImagePreserver: LocalImagePreserver,
    ): MatchResultOcrPreviewRunner {
        val delegate = AndroidMatchResultOcrPreviewProcessor(
            assetRepository = assetRepository,
            localFileResolver = MatchResultOcrPreviewLocalFileResolver(
                localImagePreserver::resolveRelativePath,
            ),
        )
        return CachingMatchResultOcrPreviewRunner(
            assetRepository = assetRepository,
            cacheRepository = cacheRepository,
            delegate = delegate,
        )
    }
}

object MatchResultOcrPreviewTeamSuggestionMapper {
    fun map(
        preview: MatchResultOcrPreviewUiState,
        resultRows: List<MatchResultOcrRow>,
        lobbyOcrResult: MatchLobbyPlayersOcrResult,
    ): List<MatchOcrReviewRowUiState>? {
        val rows = MatchResultOcrPreviewUiStateMapper.toReviewRows(preview) ?: return null
        val matchResults = resultRows.mapNotNull { resultRow ->
            runCatching {
                MatchResultLobbyOcrSlotRanker.rank(resultRow, lobbyOcrResult)
            }.getOrNull()
        }
        val evaluation = runCatching {
            ResultLobbySlotAssignmentEvaluator.evaluate(matchResults)
        }.getOrNull()
        val evaluationByPosition = evaluation?.rows?.associateBy { it.resultPosition }.orEmpty()

        return rows.map { row ->
            val result = evaluationByPosition[row.rowIndex + 1] ?: return@map row
            val safety = result.assignmentSafety
            val automaticAssignedTeamSlot = result.automaticAssignedTeamSlot
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
                originalSuggestedTeamSlot = automaticAssignedTeamSlot,
                confidenceScoreDisplayValue = selected?.teamCandidateScore?.confidenceScore?.toString()
                    ?: "Unavailable",
                confidenceTierLabel = result.confidenceAssessment.tier.toLabel(),
                assignmentSafetyStatusLabel = safety.safetyStatus.toLabel(),
                topThreeSuggestionsSummary = result.matchResult.rankedCandidates.toSummary(),
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
