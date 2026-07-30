package com.hoggamers.rankforge.domain.ocr.parsing

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition

enum class RosterOcrValidationStatus {
    READY_FOR_REVIEW,
    NEEDS_MANUAL_REVIEW,
    BLOCKED,
}

enum class RosterOcrValidationSeverity {
    BLOCKING,
    WARNING,
    INFO,
}

enum class RosterOcrValidationIssueType {
    MISSING_TOURNAMENT_SLOT,
    DUPLICATE_TOURNAMENT_SLOT,
    INVALID_TOURNAMENT_SLOT,
    INVALID_SCREENSHOT_POSITION,
    INVALID_VISIBLE_SLOT_POSITION,
    METADATA_CONFLICT,
    MISSING_PLAYER_ROW,
    EMPTY_PLAYER_ROW,
    AMBIGUOUS_PLAYER_ROW,
    DUPLICATE_PLAYER_ROW_CANDIDATE,
    MALFORMED_PLAYER_ROW,
    UNCERTAIN_PLAYER_ROW,
    UNSUPPORTED_PLAYER_ROW,
    EXTRACTION_FAILURE,
    UNSUPPORTED_EXTRA_PLAYER_ROW,
    DUPLICATE_PLAYER_NAME_CANDIDATE,
    CONFIDENCE_UNAVAILABLE,
    TEAM_NAME_UNAVAILABLE,
    PARSER_INPUT_FAILURE,
    ASSOCIATION_INPUT_FAILURE,
}

data class RosterOcrValidationIssue(
    val severity: RosterOcrValidationSeverity,
    val type: RosterOcrValidationIssueType,
    val tournamentSlotNumber: Int? = null,
    val screenshotPosition: RosterScreenshotPosition? = null,
    val visibleSlotPosition: RosterVisibleSlotPosition? = null,
    val playerRowIndex: Int? = null,
    val sourceCandidate: RosterTournamentSlotCandidate? = null,
    val sourcePlayerCandidates: List<RosterPlayerNameCandidate> = emptyList(),
    val rawSourceResults: List<RosterRawOcrExtractionResult> = emptyList(),
    val associationFailure: RosterSlotAssociationFailure? = null,
)

data class RosterOcrValidationInput(
    val associationResult: RosterSlotAssociationResult,
)

data class RosterOcrSlotValidationResult(
    val tournamentSlotCandidate: RosterTournamentSlotCandidate,
    val issues: List<RosterOcrValidationIssue>,
)

data class RosterOcrValidationResult(
    val status: RosterOcrValidationStatus,
    val slotResults: List<RosterOcrSlotValidationResult>,
    val globalIssues: List<RosterOcrValidationIssue>,
) {
    val issues: List<RosterOcrValidationIssue>
        get() = globalIssues + slotResults.flatMap { it.issues }
}

interface RosterOcrValidator {
    fun validate(input: RosterOcrValidationInput): RosterOcrValidationResult
}

/**
 * Validates v0.8.12 candidate association output for later review without confirming roster data.
 */
class DefaultRosterOcrValidator : RosterOcrValidator {
    override fun validate(input: RosterOcrValidationInput): RosterOcrValidationResult {
        val orderedCandidates = input.associationResult.tournamentSlotCandidates
            .sortedBy { it.tournamentSlotNumber }
        val slotResults = orderedCandidates.map(::validateSlot)
        val globalIssues = buildList {
            addAll(input.associationResult.failures.mapNotNull(::associationIssue))
            addAll(missingTournamentSlotIssues(orderedCandidates))
            addAll(duplicatePlayerNameIssues(orderedCandidates))
        }
        val allIssues = globalIssues + slotResults.flatMap { it.issues }

        return RosterOcrValidationResult(
            status = allIssues.toValidationStatus(),
            slotResults = slotResults,
            globalIssues = globalIssues,
        )
    }

    private fun validateSlot(
        candidate: RosterTournamentSlotCandidate,
    ): RosterOcrSlotValidationResult {
        val issues = buildList {
            if (candidate.tournamentSlotNumber !in TOURNAMENT_SLOT_NUMBERS) {
                add(candidateIssue(candidate, RosterOcrValidationIssueType.INVALID_TOURNAMENT_SLOT))
            }
            if (candidate.tournamentSlotNumber != candidate.expectedTournamentSlot()) {
                add(candidateIssue(candidate, RosterOcrValidationIssueType.METADATA_CONFLICT))
            }
            if (candidate.teamNameCandidate.status == RosterCandidateParseStatus.UNSUPPORTED) {
                add(
                    RosterOcrValidationIssue(
                        severity = RosterOcrValidationSeverity.INFO,
                        type = RosterOcrValidationIssueType.TEAM_NAME_UNAVAILABLE,
                        tournamentSlotNumber = candidate.tournamentSlotNumber,
                        screenshotPosition = candidate.sourceScreenshotPosition,
                        visibleSlotPosition = candidate.sourceVisibleSlotPosition,
                        sourceCandidate = candidate,
                        rawSourceResults = candidate.teamNameCandidate.rawSourceResults,
                    ),
                )
            }

            SUPPORTED_PLAYER_ROWS.forEach { rowIndex ->
                val rowCandidates = candidate.playerNameCandidates.filter {
                    it.playerRowIndex == rowIndex
                }
                when {
                    rowCandidates.isEmpty() -> add(
                        playerIssue(
                            candidate = candidate,
                            type = RosterOcrValidationIssueType.MISSING_PLAYER_ROW,
                            rowIndex = rowIndex,
                            severity = RosterOcrValidationSeverity.BLOCKING,
                        ),
                    )
                    rowCandidates.size > 1 -> add(
                        playerIssue(
                            candidate = candidate,
                            type = RosterOcrValidationIssueType.DUPLICATE_PLAYER_ROW_CANDIDATE,
                            rowIndex = rowIndex,
                            severity = RosterOcrValidationSeverity.BLOCKING,
                            playerCandidates = rowCandidates,
                        ),
                    )
                }
                rowCandidates.forEach { playerCandidate ->
                    playerCandidate.validationIssue(candidate)?.let { issue -> add(issue) }
                    if (playerCandidate.confidence == RawOcrConfidence.Unavailable) {
                        add(
                            playerIssue(
                                candidate = candidate,
                                type = RosterOcrValidationIssueType.CONFIDENCE_UNAVAILABLE,
                                rowIndex = playerCandidate.playerRowIndex,
                                severity = RosterOcrValidationSeverity.INFO,
                                playerCandidates = listOf(playerCandidate),
                            ),
                        )
                    }
                }
            }

            candidate.playerNameCandidates
                .filter { it.playerRowIndex !in SUPPORTED_PLAYER_ROWS }
                .forEach { playerCandidate ->
                    add(
                        playerIssue(
                            candidate = candidate,
                            type = RosterOcrValidationIssueType.UNSUPPORTED_EXTRA_PLAYER_ROW,
                            rowIndex = playerCandidate.playerRowIndex,
                            severity = RosterOcrValidationSeverity.INFO,
                            playerCandidates = listOf(playerCandidate),
                        ),
                    )
                }
        }

        return RosterOcrSlotValidationResult(candidate, issues)
    }

    private fun RosterPlayerNameCandidate.validationIssue(
        candidate: RosterTournamentSlotCandidate,
    ): RosterOcrValidationIssue? = when (status) {
        RosterCandidateParseStatus.PARSED -> null
        RosterCandidateParseStatus.MISSING -> playerIssue(
            candidate,
            RosterOcrValidationIssueType.MISSING_PLAYER_ROW,
            playerRowIndex,
            RosterOcrValidationSeverity.BLOCKING,
            listOf(this),
        )
        RosterCandidateParseStatus.EMPTY -> playerIssue(
            candidate,
            RosterOcrValidationIssueType.EMPTY_PLAYER_ROW,
            playerRowIndex,
            RosterOcrValidationSeverity.BLOCKING,
            listOf(this),
        )
        RosterCandidateParseStatus.AMBIGUOUS -> playerIssue(
            candidate,
            RosterOcrValidationIssueType.AMBIGUOUS_PLAYER_ROW,
            playerRowIndex,
            RosterOcrValidationSeverity.BLOCKING,
            listOf(this),
        )
        RosterCandidateParseStatus.DUPLICATE -> playerIssue(
            candidate,
            RosterOcrValidationIssueType.DUPLICATE_PLAYER_ROW_CANDIDATE,
            playerRowIndex,
            RosterOcrValidationSeverity.BLOCKING,
            listOf(this),
        )
        RosterCandidateParseStatus.MALFORMED -> playerIssue(
            candidate,
            RosterOcrValidationIssueType.MALFORMED_PLAYER_ROW,
            playerRowIndex,
            RosterOcrValidationSeverity.BLOCKING,
            listOf(this),
        )
        RosterCandidateParseStatus.UNCERTAIN -> playerIssue(
            candidate,
            RosterOcrValidationIssueType.UNCERTAIN_PLAYER_ROW,
            playerRowIndex,
            RosterOcrValidationSeverity.BLOCKING,
            listOf(this),
        )
        RosterCandidateParseStatus.UNSUPPORTED -> playerIssue(
            candidate,
            RosterOcrValidationIssueType.UNSUPPORTED_PLAYER_ROW,
            playerRowIndex,
            RosterOcrValidationSeverity.BLOCKING,
            listOf(this),
        )
        RosterCandidateParseStatus.INPUT_FAILURE -> playerIssue(
            candidate,
            RosterOcrValidationIssueType.EXTRACTION_FAILURE,
            playerRowIndex,
            RosterOcrValidationSeverity.BLOCKING,
            listOf(this),
        )
    }

    private fun associationIssue(
        failure: RosterSlotAssociationFailure,
    ): RosterOcrValidationIssue? = when (failure.type) {
        RosterSlotAssociationFailureType.PARSER_INPUT_FAILURE -> associationIssue(
            failure,
            RosterOcrValidationIssueType.PARSER_INPUT_FAILURE,
        )
        RosterSlotAssociationFailureType.INVALID_SCREENSHOT_POSITION_METADATA -> associationIssue(
            failure,
            RosterOcrValidationIssueType.INVALID_SCREENSHOT_POSITION,
        )
        RosterSlotAssociationFailureType.INVALID_VISIBLE_SLOT_METADATA -> associationIssue(
            failure,
            RosterOcrValidationIssueType.INVALID_VISIBLE_SLOT_POSITION,
        )
        RosterSlotAssociationFailureType.UNSUPPORTED_PLAYER_ROW -> associationIssue(
            failure,
            RosterOcrValidationIssueType.UNSUPPORTED_EXTRA_PLAYER_ROW,
            RosterOcrValidationSeverity.INFO,
        )
        RosterSlotAssociationFailureType.DUPLICATE_TOURNAMENT_SLOT -> associationIssue(
            failure,
            RosterOcrValidationIssueType.DUPLICATE_TOURNAMENT_SLOT,
        )
        RosterSlotAssociationFailureType.MISSING_SCREENSHOT_POSITION,
        RosterSlotAssociationFailureType.MISSING_VISIBLE_SLOT
        -> associationIssue(failure, RosterOcrValidationIssueType.ASSOCIATION_INPUT_FAILURE)
    }

    private fun associationIssue(
        failure: RosterSlotAssociationFailure,
        type: RosterOcrValidationIssueType,
        severity: RosterOcrValidationSeverity = RosterOcrValidationSeverity.BLOCKING,
    ): RosterOcrValidationIssue = RosterOcrValidationIssue(
        severity = severity,
        type = type,
        tournamentSlotNumber = failure.tournamentSlotNumber,
        screenshotPosition = failure.screenshotPosition,
        visibleSlotPosition = failure.visibleSlotPosition,
        rawSourceResults = failure.sourceCandidates.flatMap { source ->
            source.teamNameCandidate.rawSourceResults +
                source.playerNameCandidates.flatMap { it.rawSourceResults }
        },
        associationFailure = failure,
    )

    private fun missingTournamentSlotIssues(
        candidates: List<RosterTournamentSlotCandidate>,
    ): List<RosterOcrValidationIssue> {
        val availableSlots = candidates.map { it.tournamentSlotNumber }.toSet()
        return TOURNAMENT_SLOT_NUMBERS
            .filterNot { it in availableSlots }
            .map { tournamentSlotNumber ->
                RosterOcrValidationIssue(
                    severity = RosterOcrValidationSeverity.BLOCKING,
                    type = RosterOcrValidationIssueType.MISSING_TOURNAMENT_SLOT,
                    tournamentSlotNumber = tournamentSlotNumber,
                )
            }
    }

    private fun duplicatePlayerNameIssues(
        candidates: List<RosterTournamentSlotCandidate>,
    ): List<RosterOcrValidationIssue> = candidates
        .flatMap { candidate ->
            candidate.playerNameCandidates.map { playerCandidate -> candidate to playerCandidate }
        }
        .filter { (_, playerCandidate) ->
            playerCandidate.status == RosterCandidateParseStatus.PARSED &&
                !playerCandidate.candidateText.isNullOrBlank()
        }
        .groupBy { (_, playerCandidate) -> playerCandidate.candidateText.orEmpty().trim() }
        .values
        .filter { it.size > 1 }
        .map { duplicates ->
            val firstCandidate = duplicates.first().first
            val players = duplicates.map { it.second }
            RosterOcrValidationIssue(
                severity = RosterOcrValidationSeverity.WARNING,
                type = RosterOcrValidationIssueType.DUPLICATE_PLAYER_NAME_CANDIDATE,
                tournamentSlotNumber = firstCandidate.tournamentSlotNumber,
                screenshotPosition = firstCandidate.sourceScreenshotPosition,
                visibleSlotPosition = firstCandidate.sourceVisibleSlotPosition,
                sourceCandidate = firstCandidate,
                sourcePlayerCandidates = players,
                rawSourceResults = players.flatMap { it.rawSourceResults },
            )
        }

    private fun RosterTournamentSlotCandidate.expectedTournamentSlot(): Int =
        sourceScreenshotPosition.tournamentSlotFor(sourceVisibleSlotPosition)

    private fun candidateIssue(
        candidate: RosterTournamentSlotCandidate,
        type: RosterOcrValidationIssueType,
    ): RosterOcrValidationIssue = RosterOcrValidationIssue(
        severity = RosterOcrValidationSeverity.BLOCKING,
        type = type,
        tournamentSlotNumber = candidate.tournamentSlotNumber,
        screenshotPosition = candidate.sourceScreenshotPosition,
        visibleSlotPosition = candidate.sourceVisibleSlotPosition,
        sourceCandidate = candidate,
    )

    private fun playerIssue(
        candidate: RosterTournamentSlotCandidate,
        type: RosterOcrValidationIssueType,
        rowIndex: Int,
        severity: RosterOcrValidationSeverity,
        playerCandidates: List<RosterPlayerNameCandidate> = emptyList(),
    ): RosterOcrValidationIssue = RosterOcrValidationIssue(
        severity = severity,
        type = type,
        tournamentSlotNumber = candidate.tournamentSlotNumber,
        screenshotPosition = candidate.sourceScreenshotPosition,
        visibleSlotPosition = candidate.sourceVisibleSlotPosition,
        playerRowIndex = rowIndex,
        sourceCandidate = candidate,
        sourcePlayerCandidates = playerCandidates,
        rawSourceResults = if (playerCandidates.size == 1) {
            playerCandidates.single().rawSourceResults
        } else {
            playerCandidates.flatMap { it.rawSourceResults }
        },
    )

    private fun List<RosterOcrValidationIssue>.toValidationStatus(): RosterOcrValidationStatus = when {
        any { it.severity == RosterOcrValidationSeverity.BLOCKING } ->
            RosterOcrValidationStatus.BLOCKED
        isNotEmpty() -> RosterOcrValidationStatus.NEEDS_MANUAL_REVIEW
        else -> RosterOcrValidationStatus.READY_FOR_REVIEW
    }

    private companion object {
        val TOURNAMENT_SLOT_NUMBERS = 1..12
        val SUPPORTED_PLAYER_ROWS = 1..4
    }
}
