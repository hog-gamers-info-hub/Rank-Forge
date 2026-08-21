package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Clock

data class FinalizeOcrCorrectionMatchInput(
    val tournamentId: String,
    val matchId: String,
    val correctionRows: List<FinalizeOcrCorrectionRowInput>?,
    val warningConfirmationAccepted: Boolean = false,
    val sourceScreenshotId: String? = null,
)

data class FinalizeOcrCorrectionRowInput(
    val rowIndex: Int,
    val correctedPlacement: String?,
    val correctedKills: String?,
    val correctedTeamSlotNumber: String?,
    val warnings: Set<FinalizeOcrCorrectionMatchWarning> = emptySet(),
    val originalOcrText: String? = null,
    val originalPlacement: Int? = null,
    val originalKills: Int? = null,
    val originalSuggestedTeamSlot: Int? = null,
    val confidenceSummary: String? = null,
    val safetySummary: String? = null,
    val manualReviewRequired: Boolean = false,
    val isExcluded: Boolean = false,
)

enum class FinalizeOcrCorrectionMatchWarning {
    PLACEMENT_CHANGED_FROM_OCR,
    KILLS_CHANGED_FROM_OCR,
    TEAM_SLOT_CHANGED_FROM_SUGGESTION,
    ROW_ORIGINALLY_REQUIRED_MANUAL_REVIEW,
    WEAK_CONFIDENCE_OR_SAFETY_EVIDENCE,
}

enum class FinalizeOcrCorrectionMatchFailure {
    MISSING_CORRECTION_DRAFT,
    INVALID_CORRECTION_DRAFT,
    MISSING_CORRECTION_ROW,
    MALFORMED_ROW_DRAFT,
    MISSING_PLACEMENT,
    INVALID_PLACEMENT,
    DUPLICATE_PLACEMENT,
    MISSING_KILLS,
    INVALID_KILLS,
    NEGATIVE_KILLS,
    MISSING_TEAM_SLOT,
    INVALID_TEAM_SLOT,
    DUPLICATE_TEAM_SLOT,
    TEAM_SLOT_UNAVAILABLE,
    MISSING_TOURNAMENT,
    MISSING_MATCH,
    ALREADY_FINALIZED,
    FINALIZATION_FAILED,
    UNEXPECTED_FAILURE,
}

sealed interface FinalizeOcrCorrectionMatchResult {
    data class Finalized(val match: Match) : FinalizeOcrCorrectionMatchResult

    data class ConfirmationRequired(
        val warningCount: Int,
        val warningRowIndexes: Set<Int>,
    ) : FinalizeOcrCorrectionMatchResult

    data class Blocked(
        val failures: Set<FinalizeOcrCorrectionMatchFailure>,
        val failuresByRowIndex: Map<Int, Set<FinalizeOcrCorrectionMatchFailure>> = emptyMap(),
        val validation: MatchResultValidation = MatchResultValidation(),
    ) : FinalizeOcrCorrectionMatchResult

    data object UnexpectedFailure : FinalizeOcrCorrectionMatchResult
}

class FinalizeOcrCorrectionMatchUseCase(
    private val repository: TournamentRepository,
    private val finalizeMatch: FinalizeMatchUseCase,
    private val clock: Clock = Clock.systemUTC(),
    private val validateMatchResult: ValidateMatchResultUseCase = ValidateMatchResultUseCase(),
) {
    suspend operator fun invoke(input: FinalizeOcrCorrectionMatchInput): FinalizeOcrCorrectionMatchResult {
        return try {
            finalize(input)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            FinalizeOcrCorrectionMatchResult.UnexpectedFailure
        }
    }

    private suspend fun finalize(
        input: FinalizeOcrCorrectionMatchInput,
    ): FinalizeOcrCorrectionMatchResult {
        val correctionRows = input.correctionRows
            ?: return FinalizeOcrCorrectionMatchResult.Blocked(
                failures = setOf(FinalizeOcrCorrectionMatchFailure.MISSING_CORRECTION_DRAFT),
            )

        val rowValidation = validateCorrectionRows(correctionRows)
        if (rowValidation.failures.isNotEmpty()) {
            return FinalizeOcrCorrectionMatchResult.Blocked(
                failures = rowValidation.failures,
                failuresByRowIndex = rowValidation.failuresByRowIndex,
            )
        }
        val tournament = repository.observeById(input.tournamentId).first()
            ?: return FinalizeOcrCorrectionMatchResult.Blocked(
                failures = setOf(FinalizeOcrCorrectionMatchFailure.MISSING_TOURNAMENT),
            )
        val match = repository.observeMatchById(input.matchId).first()
            ?: return FinalizeOcrCorrectionMatchResult.Blocked(
                failures = setOf(FinalizeOcrCorrectionMatchFailure.MISSING_MATCH),
            )
        if (match.tournamentId != tournament.id) {
            return FinalizeOcrCorrectionMatchResult.Blocked(
                failures = setOf(FinalizeOcrCorrectionMatchFailure.MISSING_MATCH),
            )
        }
        if (match.status != MatchStatus.DRAFT) {
            return FinalizeOcrCorrectionMatchResult.Blocked(
                failures = setOf(FinalizeOcrCorrectionMatchFailure.ALREADY_FINALIZED),
            )
        }

        val slots = repository.observeSlotsByTournamentId(input.tournamentId).first()
        val participation = slots.analyzeTeamSlotParticipation()
        if (!participation.isReadyForMatchCreation) {
            // Invalid participation is reported through the existing draft-integrity contract.
            return FinalizeOcrCorrectionMatchResult.Blocked(
                failures = setOf(FinalizeOcrCorrectionMatchFailure.INVALID_CORRECTION_DRAFT),
            )
        }

        val participantValidation = validateMatchResult.validateForInitialFinalization(
            rows = rowValidation.finalizeRows,
            registeredTeamSlots = participation.activeSlotNumbers,
        )
        val positionedTeamSlots = rowValidation.finalizeRows
            .map { it.teamSlotNumber }
            .toSet()
        if (rowValidation.finalizeRows.isEmpty() || !participantValidation.isValid) {
            return FinalizeOcrCorrectionMatchResult.Blocked(
                failures = setOf(FinalizeOcrCorrectionMatchFailure.INVALID_CORRECTION_DRAFT),
                validation = participantValidation,
            )
        }

        val availableTeamSlots = slots
            .map { it.slotNumber }
            .toSet()
        val unavailableTeamSlotRows = rowValidation.finalizeRows
            .filterNot { it.teamSlotNumber in availableTeamSlots }
            .map { it.teamSlotNumber }
            .toSet()
        if (unavailableTeamSlotRows.isNotEmpty()) {
            return FinalizeOcrCorrectionMatchResult.Blocked(
                failures = setOf(FinalizeOcrCorrectionMatchFailure.TEAM_SLOT_UNAVAILABLE),
                failuresByRowIndex = correctionRows
                    .filterNot { it.isExcluded }
                    .mapNotNull { row ->
                        val teamSlotNumber = row.correctedTeamSlotNumber?.trim()?.toIntOrNull()
                        if (teamSlotNumber != null && teamSlotNumber in unavailableTeamSlotRows) {
                            row.rowIndex to setOf(FinalizeOcrCorrectionMatchFailure.TEAM_SLOT_UNAVAILABLE)
                        } else {
                            null
                        }
                    }
                    .toMap(),
            )
        }

        val warningRows = correctionRows
            .filter { !it.isExcluded && it.warnings.isNotEmpty() }
            .map { it.rowIndex }
            .toSet()
        if (warningRows.isNotEmpty() && !input.warningConfirmationAccepted) {
            return FinalizeOcrCorrectionMatchResult.ConfirmationRequired(
                warningCount = warningRows.size,
                warningRowIndexes = warningRows,
            )
        }

        val finalizeMatchResult = finalizeMatch(
            FinalizeMatchInput(
                matchId = input.matchId,
                rows = rowValidation.finalizeRows,
                ocrEvidence = input.toPreservedEvidence(correctionRows, rowValidation),
            ),
        )
        return when (val result = finalizeMatchResult) {
            is FinalizeMatchResult.Finalized -> FinalizeOcrCorrectionMatchResult.Finalized(result.match)
            is FinalizeMatchResult.Invalid -> FinalizeOcrCorrectionMatchResult.Blocked(
                failures = setOf(result.globalError.toOcrCorrectionFailure()),
                validation = result.validation,
            )
        }
    }

    private fun validateCorrectionRows(
        rows: List<FinalizeOcrCorrectionRowInput>,
    ): RowValidation {
        val failures = mutableSetOf<FinalizeOcrCorrectionMatchFailure>()
        val failuresByRowIndex = mutableMapOf<Int, MutableSet<FinalizeOcrCorrectionMatchFailure>>()
        val expectedRowIndexes = (0 until TeamSlot.MAX_SLOT_NUMBER).toSet()
        val rowIndexCounts = rows.groupingBy { it.rowIndex }.eachCount()

        if (rows.size != TeamSlot.MAX_SLOT_NUMBER) {
            failures += FinalizeOcrCorrectionMatchFailure.INVALID_CORRECTION_DRAFT
        }
        if (!rows.map { it.rowIndex }.toSet().containsAll(expectedRowIndexes)) {
            failures += FinalizeOcrCorrectionMatchFailure.MISSING_CORRECTION_ROW
        }
        rows
            .filter { row -> row.rowIndex !in expectedRowIndexes || rowIndexCounts.getValue(row.rowIndex) > 1 }
            .forEach { row ->
                failures.addForRow(row.rowIndex, failuresByRowIndex, FinalizeOcrCorrectionMatchFailure.MALFORMED_ROW_DRAFT)
            }

        val parsedRows = rows.map { row ->
            val placement = if (row.isExcluded) {
                ParsedInt(null, null)
            } else {
                row.correctedPlacement.parsePlacement()
            }
            val kills = if (row.isExcluded) {
                ParsedInt(null, null)
            } else {
                row.correctedKills.parseKills()
            }
            val teamSlot = if (row.isExcluded) {
                ParsedInt(null, null)
            } else {
                row.correctedTeamSlotNumber.parseTeamSlot()
            }

            if (placement.failure != null) {
                failures.addForRow(row.rowIndex, failuresByRowIndex, placement.failure)
            }
            if (kills.failure != null) {
                failures.addForRow(row.rowIndex, failuresByRowIndex, kills.failure)
            }
            if (teamSlot.failure != null) {
                failures.addForRow(row.rowIndex, failuresByRowIndex, teamSlot.failure)
            }

            ParsedRow(
                rowIndex = row.rowIndex,
                placement = placement.value,
                kills = kills.value,
                teamSlotNumber = teamSlot.value,
                isExcluded = row.isExcluded,
            )
        }

        parsedRows
            .filterNot { it.isExcluded }
            .duplicateIndexesByValue { it.placement }
            .forEach { rowIndex ->
                failures.addForRow(rowIndex, failuresByRowIndex, FinalizeOcrCorrectionMatchFailure.DUPLICATE_PLACEMENT)
            }
        parsedRows
            .filterNot { it.isExcluded }
            .duplicateIndexesByValue { it.teamSlotNumber }
            .forEach { rowIndex ->
                failures.addForRow(rowIndex, failuresByRowIndex, FinalizeOcrCorrectionMatchFailure.DUPLICATE_TEAM_SLOT)
            }

        val finalizeRows = parsedRows.mapNotNull { row ->
            if (row.isExcluded) return@mapNotNull null
            val teamSlotNumber = row.teamSlotNumber ?: return@mapNotNull null
            val placement = row.placement ?: return@mapNotNull null
            val kills = row.kills ?: return@mapNotNull null
            MatchResultRowInput(
                teamSlotNumber = teamSlotNumber,
                placement = placement.toString(),
                kills = kills.toString(),
            )
        }

        return RowValidation(
            failures = failures.toSet(),
            failuresByRowIndex = failuresByRowIndex.mapValues { (_, rowFailures) -> rowFailures.toSet() },
            finalizeRows = finalizeRows,
            parsedRows = parsedRows,
        )
    }

    private fun FinalizeOcrCorrectionMatchInput.toPreservedEvidence(
        correctionRows: List<FinalizeOcrCorrectionRowInput>,
        rowValidation: RowValidation,
    ): PreservedMatchOcrEvidence {
        val parsedRowsByIndex = rowValidation.parsedRows.associateBy { it.rowIndex }
        val preservedAt = clock.millis()
        return PreservedMatchOcrEvidence(
            tournamentId = tournamentId,
            matchId = matchId,
            sourceScreenshotId = sourceScreenshotId,
            preservedAt = preservedAt,
            provenance = OCR_REVIEW_FINALIZATION_PROVENANCE,
            rows = correctionRows.map { row ->
                PreservedMatchOcrRowEvidence(
                    rowIndex = row.rowIndex,
                    originalOcrText = row.originalOcrText,
                    originalPlacement = row.originalPlacement,
                    originalKills = row.originalKills,
                    originalSuggestedTeamSlot = row.originalSuggestedTeamSlot,
                    confidenceSummary = row.confidenceSummary,
                    safetySummary = row.safetySummary,
                    manualReviewRequired = row.manualReviewRequired,
                )
            },
            correctionSnapshots = correctionRows.filterNot { it.isExcluded }.map { row ->
                val parsed = parsedRowsByIndex.getValue(row.rowIndex)
                PreservedMatchOcrCorrectionSnapshot(
                    rowIndex = row.rowIndex,
                    correctedPlacement = parsed.placement!!,
                    correctedKills = parsed.kills!!,
                    correctedTeamSlot = parsed.teamSlotNumber!!,
                    placementChanged = row.originalPlacement != parsed.placement,
                    killsChanged = row.originalKills != parsed.kills,
                    teamSlotChanged = row.originalSuggestedTeamSlot != parsed.teamSlotNumber,
                )
            },
        )
    }

    private fun String?.parsePlacement(): ParsedInt =
        parseStrictPositiveInt(
            missingFailure = FinalizeOcrCorrectionMatchFailure.MISSING_PLACEMENT,
            invalidFailure = FinalizeOcrCorrectionMatchFailure.INVALID_PLACEMENT,
        ).requireInSlotRange(FinalizeOcrCorrectionMatchFailure.INVALID_PLACEMENT)

    private fun String?.parseTeamSlot(): ParsedInt =
        parseStrictPositiveInt(
            missingFailure = FinalizeOcrCorrectionMatchFailure.MISSING_TEAM_SLOT,
            invalidFailure = FinalizeOcrCorrectionMatchFailure.INVALID_TEAM_SLOT,
        ).requireInSlotRange(FinalizeOcrCorrectionMatchFailure.INVALID_TEAM_SLOT)

    private fun String?.parseKills(): ParsedInt {
        val trimmed = this?.trim().orEmpty()
        return when {
            trimmed.isBlank() -> ParsedInt(null, FinalizeOcrCorrectionMatchFailure.MISSING_KILLS)
            trimmed.isStrictNegativeInteger() -> ParsedInt(null, FinalizeOcrCorrectionMatchFailure.NEGATIVE_KILLS)
            trimmed.any { it !in '0'..'9' } -> ParsedInt(null, FinalizeOcrCorrectionMatchFailure.INVALID_KILLS)
            else -> trimmed.toIntOrNull()
                ?.let { ParsedInt(it, null) }
                ?: ParsedInt(null, FinalizeOcrCorrectionMatchFailure.INVALID_KILLS)
        }
    }

    private fun String?.parseStrictPositiveInt(
        missingFailure: FinalizeOcrCorrectionMatchFailure,
        invalidFailure: FinalizeOcrCorrectionMatchFailure,
    ): ParsedInt {
        val trimmed = this?.trim().orEmpty()
        return when {
            trimmed.isBlank() -> ParsedInt(null, missingFailure)
            trimmed.any { it !in '0'..'9' } -> ParsedInt(null, invalidFailure)
            else -> trimmed.toIntOrNull()
                ?.let { ParsedInt(it, null) }
                ?: ParsedInt(null, invalidFailure)
        }
    }

    private fun ParsedInt.requireInSlotRange(
        invalidFailure: FinalizeOcrCorrectionMatchFailure,
    ): ParsedInt =
        if (failure == null && value?.let { it !in TeamSlot.SLOT_NUMBERS } == true) {
            ParsedInt(null, invalidFailure)
        } else {
            this
        }

    private fun List<ParsedRow>.duplicateIndexesByValue(
        valueSelector: (ParsedRow) -> Int?,
    ): Set<Int> =
        mapNotNull { row -> valueSelector(row)?.let { row.rowIndex to it } }
            .groupBy({ (_, value) -> value }, { (rowIndex, _) -> rowIndex })
            .filterValues { rowIndexes -> rowIndexes.size > 1 }
            .values
            .flatten()
            .toSet()

    private fun MutableSet<FinalizeOcrCorrectionMatchFailure>.addForRow(
        rowIndex: Int,
        failuresByRowIndex: MutableMap<Int, MutableSet<FinalizeOcrCorrectionMatchFailure>>,
        failure: FinalizeOcrCorrectionMatchFailure,
    ) {
        this += failure
        failuresByRowIndex.getOrPut(rowIndex) { mutableSetOf() }.add(failure)
    }

    private fun FinalizeMatchGlobalError?.toOcrCorrectionFailure(): FinalizeOcrCorrectionMatchFailure =
        when (this) {
            FinalizeMatchGlobalError.MATCH_NOT_FOUND -> FinalizeOcrCorrectionMatchFailure.MISSING_MATCH
            FinalizeMatchGlobalError.MATCH_NOT_DRAFT -> FinalizeOcrCorrectionMatchFailure.ALREADY_FINALIZED
            FinalizeMatchGlobalError.INVALID_DATA, null -> FinalizeOcrCorrectionMatchFailure.FINALIZATION_FAILED
        }

    private fun String.isStrictNegativeInteger(): Boolean =
        startsWith("-") && drop(1).isNotEmpty() && drop(1).all { it in '0'..'9' }

    private data class ParsedInt(
        val value: Int?,
        val failure: FinalizeOcrCorrectionMatchFailure?,
    )

    private data class ParsedRow(
        val rowIndex: Int,
        val placement: Int?,
        val kills: Int?,
        val teamSlotNumber: Int?,
        val isExcluded: Boolean,
    )

    private data class RowValidation(
        val failures: Set<FinalizeOcrCorrectionMatchFailure>,
        val failuresByRowIndex: Map<Int, Set<FinalizeOcrCorrectionMatchFailure>>,
        val finalizeRows: List<MatchResultRowInput>,
        val parsedRows: List<ParsedRow>,
    )

    private companion object {
        const val OCR_REVIEW_FINALIZATION_PROVENANCE = "OCR_REVIEW_FINALIZATION"
    }
}
