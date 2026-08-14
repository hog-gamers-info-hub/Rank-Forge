package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewProcessingResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRoleResult
import com.hoggamers.rankforge.domain.matching.RowTeamAssignmentSafetyResult
import com.hoggamers.rankforge.domain.matching.TeamAssignmentSafetyReason
import com.hoggamers.rankforge.domain.matching.TeamAssignmentSafetyStatus
import com.hoggamers.rankforge.domain.matching.TeamMatchConfidenceAssessment
import com.hoggamers.rankforge.domain.matching.TeamMatchConfidenceTier
import com.hoggamers.rankforge.domain.matching.TopTeamCandidateSuggestions
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewField
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewFieldType
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewSeverity
import com.hoggamers.rankforge.domain.ocr.review.OcrReviewStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrPlayerSlot
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole

sealed interface MatchResultOcrPreviewUiState {
    data object NotRequested : MatchResultOcrPreviewUiState
    data object Processing : MatchResultOcrPreviewUiState
    data object Empty : MatchResultOcrPreviewUiState

    data class Error(val message: String) : MatchResultOcrPreviewUiState

    data class Ready(
        val roles: List<MatchResultScreenshotRole>,
        val rows: List<MatchResultOcrPreviewRowUiState>,
        val ignoredLowerRows: List<MatchResultOcrPreviewIgnoredRowUiState>,
        val manualReviewRows: List<MatchResultOcrPreviewManualRowUiState>,
    ) : MatchResultOcrPreviewUiState
}

data class MatchResultOcrPreviewRowUiState(
    val position: Int,
    val role: MatchResultScreenshotRole,
    val sourceLabel: String,
    val placementText: String,
    val slots: List<MatchResultOcrPreviewSlotUiState>,
)

data class MatchResultOcrPreviewSlotUiState(
    val slot: Int,
    val playerText: String,
    val playerOcrText: String,
    val playerStatusLabel: String,
    val killText: String,
    val killOcrText: String,
    val killStatusLabel: String,
)

data class MatchResultOcrPreviewIgnoredRowUiState(
    val role: MatchResultScreenshotRole,
    val visualRow: String,
    val detectedPlacement: Int?,
    val reason: String,
)

data class MatchResultOcrPreviewManualRowUiState(
    val role: MatchResultScreenshotRole,
    val visualRow: String,
    val detectedPlacementText: String,
    val reason: String,
)

sealed interface MatchOcrReviewUiState {
    data object Loading : MatchOcrReviewUiState

    data class Ready(
        val tournamentId: String,
        val matchId: String,
        val matchDisplayLabel: String? = null,
        val rowCount: Int,
        val rows: List<MatchOcrReviewRowUiState>,
        val blockerCount: Int,
        val warningCount: Int,
        val safeRowCount: Int,
        val manualRequiredRowCount: Int,
        val reviewRequiredRowCount: Int,
        val manualReviewRequired: Boolean,
        val hasUnavailableEvidence: Boolean,
        val correctionDraft: MatchOcrReviewCorrectionDraft? = null,
        val finalization: MatchOcrReviewFinalizationUiState = MatchOcrReviewFinalizationUiState(),
        val matchResultOcrPreview: MatchResultOcrPreviewUiState = MatchResultOcrPreviewUiState.NotRequested,
        val teamNamesBySlot: Map<Int, String> = emptyMap(),
    ) : MatchOcrReviewUiState

    data class Empty(
        val tournamentId: String? = null,
        val matchId: String? = null,
        val matchResultOcrPreview: MatchResultOcrPreviewUiState = MatchResultOcrPreviewUiState.NotRequested,
    ) : MatchOcrReviewUiState

    data class Error(
        val tournamentId: String? = null,
        val matchId: String? = null,
        val message: String,
        val matchResultOcrPreview: MatchResultOcrPreviewUiState = MatchResultOcrPreviewUiState.NotRequested,
    ) : MatchOcrReviewUiState
}

object MatchResultOcrPreviewUiStateMapper {
    fun map(
        processedResults: List<MatchResultOcrPreviewRoleResult>,
    ): MatchResultOcrPreviewUiState {
        val processed = processedResults.mapNotNull { result ->
            (result.result as? MatchResultOcrPreviewProcessingResult.Processed)?.let {
                result.role to it.extraction
            }
        }
        val rows = processed.flatMap { (role, extraction) ->
            extraction.rows.map { row -> row.toUiState(role) }
        }.sortedBy { it.position }
        val ignoredRows = processed.flatMap { (role, extraction) ->
            extraction.ignoredLowerRows.map { ignored ->
                MatchResultOcrPreviewIgnoredRowUiState(
                    role = role,
                    visualRow = ignored.visualRow.name,
                    detectedPlacement = ignored.detectedPlacement,
                    reason = ignored.reason.name,
                )
            }
        }
        val manualRows = processed.flatMap { (role, extraction) ->
            extraction.manualReviewRows.map { manual ->
                MatchResultOcrPreviewManualRowUiState(
                    role = role,
                    visualRow = manual.visualRow.name,
                    detectedPlacementText = manual.detectedPlacementText,
                    reason = manual.reason.name,
                )
            }
        }
        return if (rows.isEmpty() && ignoredRows.isEmpty() && manualRows.isEmpty()) {
            MatchResultOcrPreviewUiState.Empty
        } else {
            MatchResultOcrPreviewUiState.Ready(
                roles = processed.map { it.first }.distinct(),
                rows = rows,
                ignoredLowerRows = ignoredRows,
                manualReviewRows = manualRows,
            )
        }
    }

    fun toReviewRows(
        preview: MatchResultOcrPreviewUiState,
    ): List<MatchOcrReviewRowUiState>? {
        val ready = preview as? MatchResultOcrPreviewUiState.Ready ?: return null
        val positions = ready.rows.map { it.position }
        if (positions.size != 12 || positions.toSet() != (1..12).toSet()) return null

        return ready.rows
            .sortedBy { it.position }
            .map { row ->
                val placement = row.placementText.trim()
                val killValues = row.slots.mapNotNull { it.killText.trim().toIntOrNull() }
                val allKillsNumeric = row.slots.isNotEmpty() &&
                    row.slots.all { it.killText.trim().toIntOrNull() != null }
                val playerNames = row.slots
                    .sortedBy { it.slot }
                    .mapNotNull { slot ->
                        slot.playerText.trim().takeIf { it.isNotBlank() }?.let {
                            "P${slot.slot} $it"
                        }
                    }
                MatchOcrReviewRowUiState(
                    rowIndex = row.position - 1,
                    expectedPlacementLabel = row.position.toString(),
                    detectedPlacementDisplayValue = placement.ifBlank { row.position.toString() },
                    placementStatusLabel = "From match-result OCR preview",
                    detectedKillDisplayValue = if (killValues.isEmpty()) {
                        "Unavailable"
                    } else {
                        killValues.sum().toString()
                    },
                    killStatusLabel = row.slots.map { it.killStatusLabel }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(", ")
                        .ifBlank { "From match-result OCR preview" },
                    detectedPlayerNameEvidenceLabel = playerNames
                        .joinToString(", ")
                        .ifBlank { "Unavailable" },
                    playerNameStatusLabel = row.slots.map { it.playerStatusLabel }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(", ")
                        .ifBlank { "From match-result OCR preview" },
                    suggestedTeamSlotDisplayValue = "Unavailable",
                    confidenceScoreDisplayValue = "Unavailable",
                    confidenceTierLabel = "Unavailable",
                    assignmentSafetyStatusLabel = "Unavailable",
                    topThreeSuggestionsSummary = listOf("No suggestions"),
                    warningLabels = listOf("OCR preview requires manual confirmation"),
                    blockerLabels = listOf("Team assignment: manual team slot required"),
                    severity = MatchOcrReviewSeverity.BLOCKING,
                    originalParsedPlacementValue = placement.toIntOrNull() ?: row.position,
                    originalParsedKillValue = totalKillsOrNull(row, allKillsNumeric, killValues),
                    originalSuggestedTeamSlot = null,
                )
            }
    }

    private fun totalKillsOrNull(
        row: MatchResultOcrPreviewRowUiState,
        allKillsNumeric: Boolean,
        killValues: List<Int>,
    ): Int? = killValues.sum().takeIf { allKillsNumeric && row.slots.isNotEmpty() }

    private fun MatchResultOcrRow.toUiState(
        role: MatchResultScreenshotRole,
    ): MatchResultOcrPreviewRowUiState = MatchResultOcrPreviewRowUiState(
        position = position,
        role = role,
        sourceLabel = source.name,
        placementText = placement.resolvedText.ifBlank { placement.ocrText },
        slots = playerSlots.map { it.toUiState() },
    )

    private fun MatchResultOcrPlayerSlot.toUiState(): MatchResultOcrPreviewSlotUiState =
        MatchResultOcrPreviewSlotUiState(
            slot = slot,
            playerText = player.resolvedText.ifBlank { player.ocrText },
            playerOcrText = player.ocrText,
            playerStatusLabel = player.status.name,
            killText = kill.resolvedText.ifBlank { kill.ocrText },
            killOcrText = kill.ocrText,
            killStatusLabel = kill.status.name,
        )
}

data class MatchOcrReviewRowUiState(
    val rowIndex: Int,
    val expectedPlacementLabel: String,
    val detectedPlacementDisplayValue: String,
    val placementStatusLabel: String,
    val detectedKillDisplayValue: String,
    val killStatusLabel: String,
    val detectedPlayerNameEvidenceLabel: String,
    val playerNameStatusLabel: String,
    val suggestedTeamSlotDisplayValue: String,
    val confidenceScoreDisplayValue: String,
    val confidenceTierLabel: String,
    val assignmentSafetyStatusLabel: String,
    val topThreeSuggestionsSummary: List<String>,
    val warningLabels: List<String>,
    val blockerLabels: List<String>,
    val severity: MatchOcrReviewSeverity,
    val originalParsedPlacementValue: Int? = null,
    val originalParsedKillValue: Int? = null,
    val originalSuggestedTeamSlot: Int? = null,
)

enum class MatchOcrReviewSeverity {
    BLOCKING,
    WARNING,
    INFORMATIONAL,
}

data class MatchOcrReviewFinalizationUiState(
    val isFinalizing: Boolean = false,
    val showWarningConfirmation: Boolean = false,
    val isFinalized: Boolean = false,
    val error: MatchOcrReviewFinalizationError? = null,
)

enum class MatchOcrReviewFinalizationError {
    MISSING_CORRECTION_DRAFT,
    CORRECTION_DRAFT_BLOCKED,
    MISSING_TOURNAMENT,
    MISSING_MATCH,
    ALREADY_FINALIZED,
    FINALIZATION_FAILED,
    UNEXPECTED_FAILURE,
}

/**
 * Display-only input for v0.9.6 OCR review UI mapping.
 *
 * This model exists only to assemble already-computed OCR, suggestion, confidence, and safety
 * evidence for presentation. It is not domain storage and must not be persisted.
 */
data class MatchOcrReviewDisplayInput(
    val tournamentId: String,
    val matchId: String,
    val matchDisplayLabel: String? = null,
    val rows: List<MatchOcrReviewRowEvidenceInput>,
)

/**
 * Display-only row evidence for v0.9.6 OCR review UI mapping.
 *
 * Values passed here must already be computed by approved Phase 8 and v0.9.0-v0.9.5 boundaries.
 */
data class MatchOcrReviewRowEvidenceInput(
    val rowIndex: Int,
    val expectedPlacementId: Int,
    val detectedPlacementValue: Int?,
    val detectedKillValue: Int?,
    val detectedPlayerName: String?,
    val ocrFields: List<OcrReviewField> = emptyList(),
    val suggestions: TopTeamCandidateSuggestions? = null,
    val confidenceAssessment: TeamMatchConfidenceAssessment? = null,
    val safetyResult: RowTeamAssignmentSafetyResult? = null,
)

object MatchOcrReviewUiStateMapper {
    private const val EXPECTED_ROW_COUNT = 12
    private const val UNAVAILABLE = "Unavailable"
    private const val NO_SUGGESTIONS = "No suggestions"

    fun map(input: MatchOcrReviewDisplayInput): MatchOcrReviewUiState {
        if (input.rows.isEmpty()) {
            return MatchOcrReviewUiState.Empty(
                tournamentId = input.tournamentId,
                matchId = input.matchId,
            )
        }
        if (input.rows.size != EXPECTED_ROW_COUNT) {
            return MatchOcrReviewUiState.Error(
                tournamentId = input.tournamentId,
                matchId = input.matchId,
                message = "OCR review requires exactly 12 rows.",
            )
        }
        if (input.rows.map { it.rowIndex }.distinct().size != EXPECTED_ROW_COUNT) {
            return MatchOcrReviewUiState.Error(
                tournamentId = input.tournamentId,
                matchId = input.matchId,
                message = "OCR review rows must have unique row indexes.",
            )
        }
        if (input.rows.any { it.rowIndex !in 0 until EXPECTED_ROW_COUNT }) {
            return MatchOcrReviewUiState.Error(
                tournamentId = input.tournamentId,
                matchId = input.matchId,
                message = "OCR review row indexes must be in 0..11.",
            )
        }

        val rows = input.rows
            .sortedBy { it.rowIndex }
            .map(::mapRow)
        val blockerCount = rows.count { it.blockerLabels.isNotEmpty() }
        val warningCount = rows.count { it.blockerLabels.isEmpty() && it.warningLabels.isNotEmpty() }
        val safetyResults = input.rows.mapNotNull { it.safetyResult }

        return MatchOcrReviewUiState.Ready(
            tournamentId = input.tournamentId,
            matchId = input.matchId,
            matchDisplayLabel = input.matchDisplayLabel,
            rowCount = rows.size,
            rows = rows,
            blockerCount = blockerCount,
            warningCount = warningCount,
            safeRowCount = safetyResults.count {
                it.safetyStatus == TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT
            },
            manualRequiredRowCount = safetyResults.count {
                it.safetyStatus == TeamAssignmentSafetyStatus.MANUAL_REQUIRED
            },
            reviewRequiredRowCount = safetyResults.count {
                it.safetyStatus == TeamAssignmentSafetyStatus.REVIEW_REQUIRED
            },
            manualReviewRequired = blockerCount > 0 || warningCount > 0,
            hasUnavailableEvidence = rows.any { row ->
                row.confidenceTierLabel == UNAVAILABLE ||
                    row.assignmentSafetyStatusLabel == UNAVAILABLE ||
                    row.topThreeSuggestionsSummary == listOf(NO_SUGGESTIONS)
            },
        )
    }

    private fun mapRow(input: MatchOcrReviewRowEvidenceInput): MatchOcrReviewRowUiState {
        val confidenceAssessment = input.confidenceAssessment ?: input.safetyResult?.confidenceAssessment
        val suggestions = input.suggestions ?: confidenceAssessment?.suggestions
        val selectedSuggestion = confidenceAssessment?.selectedSuggestion
        val safetyResult = input.safetyResult
        val safetyStatus = input.safetyResult?.safetyStatus
        val suggestedTeamSlot = input.safetyResult?.proposedTeamSlot
            ?: selectedSuggestion?.teamCandidateScore?.candidateTeamSlot
        val blockers = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        input.ocrFields.forEach { field ->
            val label = "${field.type.label()}: ${field.status.label()}"
            when (field.severity) {
                OcrReviewSeverity.BLOCKING -> blockers += label
                OcrReviewSeverity.WARNING -> warnings += label
                OcrReviewSeverity.INFORMATIONAL -> Unit
            }
        }

        if (suggestions == null || suggestions.suggestions.isEmpty()) {
            blockers += "Suggestions: No usable team suggestion"
        }

        when (confidenceAssessment?.tier) {
            TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE -> Unit
            TeamMatchConfidenceTier.CONFIRMATION_REQUIRED -> warnings += "Confidence: Confirmation required"
            TeamMatchConfidenceTier.MANUAL_REQUIRED -> blockers += "Confidence: Manual required"
            null -> blockers += "Confidence: Unavailable"
        }

        when (safetyStatus) {
            TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT -> Unit
            TeamAssignmentSafetyStatus.REVIEW_REQUIRED -> {
                warnings += "Safety: Review required"
                warnings += safetyResult?.reasons.orEmpty().map { "Safety: ${it.label()}" }
            }
            TeamAssignmentSafetyStatus.MANUAL_REQUIRED -> {
                blockers += "Safety: Manual required"
                blockers += safetyResult?.reasons.orEmpty().map { "Safety: ${it.label()}" }
            }
            null -> blockers += "Safety: Unavailable"
        }

        return MatchOcrReviewRowUiState(
            rowIndex = input.rowIndex,
            expectedPlacementLabel = input.expectedPlacementId.toString(),
            detectedPlacementDisplayValue = input.detectedPlacementValue?.toString() ?: UNAVAILABLE,
            placementStatusLabel = input.fieldStatusLabel(OcrReviewFieldType.PLACEMENT),
            detectedKillDisplayValue = input.detectedKillValue?.toString() ?: UNAVAILABLE,
            killStatusLabel = input.fieldStatusLabel(OcrReviewFieldType.KILL),
            detectedPlayerNameEvidenceLabel = input.detectedPlayerName ?: UNAVAILABLE,
            playerNameStatusLabel = input.fieldStatusLabel(OcrReviewFieldType.PLAYER_NAME),
            suggestedTeamSlotDisplayValue = suggestedTeamSlot?.toString() ?: UNAVAILABLE,
            confidenceScoreDisplayValue = selectedSuggestion?.teamCandidateScore?.confidenceScore?.toString()
                ?: UNAVAILABLE,
            confidenceTierLabel = confidenceAssessment?.tier?.label() ?: UNAVAILABLE,
            assignmentSafetyStatusLabel = safetyStatus?.label() ?: UNAVAILABLE,
            topThreeSuggestionsSummary = suggestions.summary(),
            warningLabels = warnings.distinct(),
            blockerLabels = blockers.distinct(),
            severity = when {
                blockers.isNotEmpty() -> MatchOcrReviewSeverity.BLOCKING
                warnings.isNotEmpty() -> MatchOcrReviewSeverity.WARNING
                else -> MatchOcrReviewSeverity.INFORMATIONAL
            },
            originalParsedPlacementValue = input.detectedPlacementValue,
            originalParsedKillValue = input.detectedKillValue,
            originalSuggestedTeamSlot = suggestedTeamSlot,
        )
    }

    private fun MatchOcrReviewRowEvidenceInput.fieldStatusLabel(type: OcrReviewFieldType): String =
        ocrFields.firstOrNull { it.type == type }?.status?.label() ?: UNAVAILABLE

    private fun TopTeamCandidateSuggestions?.summary(): List<String> {
        val values = this?.suggestions.orEmpty()
        if (values.isEmpty()) {
            return listOf(NO_SUGGESTIONS)
        }
        return values.take(3).map { suggestion ->
            val score = suggestion.teamCandidateScore
            "Rank ${suggestion.rank}: Slot ${score.candidateTeamSlot}, confidence ${score.confidenceScore}, " +
                "matches ${score.contributingMatchCount}, coverage ${score.coverageScore}"
        }
    }

    private fun OcrReviewFieldType.label(): String = when (this) {
        OcrReviewFieldType.PLACEMENT -> "Placement"
        OcrReviewFieldType.PLAYER_NAME -> "Player name"
        OcrReviewFieldType.KILL -> "Kills"
    }

    private fun OcrReviewStatus.label(): String = when (this) {
        OcrReviewStatus.ACCEPTED -> "Accepted"
        OcrReviewStatus.MISSING -> "Missing"
        OcrReviewStatus.INVALID -> "Invalid"
        OcrReviewStatus.AMBIGUOUS -> "Ambiguous"
        OcrReviewStatus.DUPLICATE -> "Duplicate"
        OcrReviewStatus.UNSUPPORTED -> "Unsupported"
        OcrReviewStatus.UNCERTAIN -> "Uncertain"
    }

    private fun TeamMatchConfidenceTier.label(): String = when (this) {
        TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE -> "Automatic candidate"
        TeamMatchConfidenceTier.CONFIRMATION_REQUIRED -> "Confirmation required"
        TeamMatchConfidenceTier.MANUAL_REQUIRED -> "Manual required"
    }

    private fun TeamAssignmentSafetyStatus.label(): String = when (this) {
        TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT -> "Safe automatic assignment"
        TeamAssignmentSafetyStatus.REVIEW_REQUIRED -> "Review required"
        TeamAssignmentSafetyStatus.MANUAL_REQUIRED -> "Manual required"
    }

    private fun TeamAssignmentSafetyReason.label(): String = when (this) {
        TeamAssignmentSafetyReason.NO_SUGGESTION -> "No suggestion"
        TeamAssignmentSafetyReason.NOT_AUTOMATIC_TIER -> "Not automatic tier"
        TeamAssignmentSafetyReason.INSUFFICIENT_PLAYER_MATCH_COUNT -> "Insufficient player matches"
        TeamAssignmentSafetyReason.INSUFFICIENT_CANDIDATE_LEAD -> "Insufficient candidate lead"
        TeamAssignmentSafetyReason.DUPLICATE_TEAM_CANDIDATE -> "Duplicate team candidate"
        TeamAssignmentSafetyReason.MALFORMED_CONFIDENCE_ASSESSMENT -> "Malformed confidence assessment"
    }
}
