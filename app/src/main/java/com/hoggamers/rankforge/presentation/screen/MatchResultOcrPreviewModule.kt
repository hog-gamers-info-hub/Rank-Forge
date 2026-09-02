package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultOcrCacheRepository
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchResultLobbyOcrSlotRanker
import com.hoggamers.rankforge.data.ocr.matchresult.CachingMatchResultOcrPreviewRunner
import com.hoggamers.rankforge.data.ocr.matchresult.AndroidMatchResultPositionCropGenerator
import com.hoggamers.rankforge.data.ocr.matchresult.AndroidMatchResultPositionOcrPreviewRunner
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultPpOnlyPairReconciliationRunner
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewLocalFileResolver
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRunner
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotAssignmentEvaluator
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotDecisionReason
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotDecisionStatus
import com.hoggamers.rankforge.domain.matching.ResultLobbySlotVoteScore
import com.hoggamers.rankforge.domain.matching.TeamAssignmentSafetyStatus
import com.hoggamers.rankforge.domain.matching.TeamMatchConfidenceTier
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
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
        screenshotOwnerProvider: ScreenshotOwnerProvider,
        positionCropGenerator: AndroidMatchResultPositionCropGenerator,
        paddleEngineProvider: com.hoggamers.rankforge.data.ocr.matchresult.MatchResultPositionPaddleOcrEngineProvider,
        observeTournamentSlots: ObserveTournamentSlotsUseCase,
    ): MatchResultOcrPreviewRunner {
        val ppPositionRunner = AndroidMatchResultPositionOcrPreviewRunner(
            assetRepository = assetRepository,
            localFileResolver = MatchResultOcrPreviewLocalFileResolver(
                localImagePreserver::resolveRelativePath,
            ),
            screenshotOwnerProvider = screenshotOwnerProvider,
            positionCropGenerator = positionCropGenerator,
            paddleEngineProvider = paddleEngineProvider,
            observeTournamentSlots = observeTournamentSlots,
        )
        val ppPairRunner = MatchResultPpOnlyPairReconciliationRunner(
            ppRoute = ppPositionRunner,
        )
        return CachingMatchResultOcrPreviewRunner(
            assetRepository = assetRepository,
            cacheRepository = cacheRepository,
            screenshotOwnerProvider = screenshotOwnerProvider,
            delegate = ppPairRunner,
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
            when (result.decisionStatus) {
                ResultLobbySlotDecisionStatus.AUTOMATIC -> Unit
                ResultLobbySlotDecisionStatus.MANUAL ->
                    blockers += "Team assignment: manual required (${result.decisionReason.name})"
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
                resultLobbyVoteEvidencePresent = true,
                resultLobbyWinningVotePercentDisplayValue = result.winningVotePercent
                    ?.let { "$it%" }
                    ?: "Unavailable",
                resultLobbyDecisionLabel = result.decisionStatus.toVoteLabel(),
                resultLobbyDecisionReasonLabel = result.decisionReason.toVoteReasonLabel(),
                resultLobbyVoteSummary = result.matchResult.slotVoteScores.toVoteSummary(),
                resultLobbyTeamSlotCandidates = result.matchResult.slotVoteScores.map { score ->
                    MatchOcrReviewTeamSlotCandidateUiState(
                        teamSlot = score.teamSlot,
                        votePercent = score.votePercent,
                        bestSimilarityScore = result.matchResult.playerSlotVoteEvidence
                            .filter { evidence -> evidence.teamSlot == score.teamSlot }
                            .maxOfOrNull { evidence -> evidence.bestSimilarityScore },
                    )
                },
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

    private fun ResultLobbySlotDecisionStatus.toVoteLabel(): String = when (this) {
        ResultLobbySlotDecisionStatus.AUTOMATIC -> "Automatic"
        ResultLobbySlotDecisionStatus.MANUAL -> "Manual required"
    }

    private fun ResultLobbySlotDecisionReason.toVoteReasonLabel(): String = when (this) {
        ResultLobbySlotDecisionReason.UNIQUE_VOTE_WINNER -> "Unique vote winner"
        ResultLobbySlotDecisionReason.SINGLE_STRONG_VOTE -> "Single strong vote"
        ResultLobbySlotDecisionReason.NO_PLAUSIBLE_MATCH -> "No plausible match"
        ResultLobbySlotDecisionReason.TOP_VOTE_TIE -> "Top vote tie"
        ResultLobbySlotDecisionReason.SINGLE_VOTE_BELOW_STRONG_THRESHOLD ->
            "Single vote below strong threshold"
        ResultLobbySlotDecisionReason.DUPLICATE_SLOT_ACROSS_RESULT_ROWS ->
            "Duplicate slot across Result rows"
    }

    private fun List<ResultLobbySlotVoteScore>.toVoteSummary(): List<String> =
        filter { it.voteCount > 0 }
            .sortedWith(compareByDescending<ResultLobbySlotVoteScore> { it.voteCount }.thenBy { it.teamSlot })
            .map { score ->
                "Slot ${score.teamSlot}: ${score.votePercent}% (" +
                    score.supportingResultPlayerSlots.joinToString(", ") { "P$it" } + ")"
            }

    private const val TEAM_ASSIGNMENT_PREFIX = "Team assignment:"
}
