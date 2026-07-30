package com.hoggamers.rankforge.domain.ocr.parsing

import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition

enum class RosterSlotAssociationStatus {
    ASSOCIATED,
}

enum class RosterSlotAssociationFailureType {
    PARSER_INPUT_FAILURE,
    INVALID_SCREENSHOT_POSITION_METADATA,
    INVALID_VISIBLE_SLOT_METADATA,
    UNSUPPORTED_PLAYER_ROW,
    DUPLICATE_TOURNAMENT_SLOT,
    MISSING_SCREENSHOT_POSITION,
    MISSING_VISIBLE_SLOT,
}

data class RosterSlotAssociationFailure(
    val type: RosterSlotAssociationFailureType,
    val screenshotPosition: RosterScreenshotPosition? = null,
    val visibleSlotPosition: RosterVisibleSlotPosition? = null,
    val tournamentSlotNumber: Int? = null,
    val parserFailure: RosterCandidateParseFailure? = null,
    val sourceCandidates: List<RosterSlotCandidate> = emptyList(),
)

data class RosterSlotAssociationInput(
    val parsedCandidates: RosterCandidateParseResult,
)

data class RosterSlotAssociationResult(
    val tournamentSlotCandidates: List<RosterTournamentSlotCandidate>,
    val failures: List<RosterSlotAssociationFailure>,
)

data class RosterTournamentSlotCandidate(
    val tournamentSlotNumber: Int,
    val sourceScreenshotPosition: RosterScreenshotPosition,
    val sourceVisibleSlotPosition: RosterVisibleSlotPosition,
    val teamNameCandidate: RosterTeamNameCandidate,
    val playerNameCandidates: List<RosterPlayerNameCandidate>,
    val associationStatus: RosterSlotAssociationStatus,
)

interface RosterSlotAssociator {
    fun associate(input: RosterSlotAssociationInput): RosterSlotAssociationResult
}

/**
 * Associates v0.8.11 candidates solely from the approved screenshot and visible-slot metadata.
 * It does not validate candidate text, team/player identity, or roster completeness.
 */
class FixedRosterSlotAssociator : RosterSlotAssociator {
    override fun associate(input: RosterSlotAssociationInput): RosterSlotAssociationResult {
        val sourceCandidates = input.parsedCandidates.slots
        val failures = mutableListOf<RosterSlotAssociationFailure>().apply {
            input.parsedCandidates.inputFailures.forEach { parserFailure ->
                add(
                    RosterSlotAssociationFailure(
                        type = RosterSlotAssociationFailureType.PARSER_INPUT_FAILURE,
                        parserFailure = parserFailure,
                    ),
                )
            }
        }
        val candidatesByTournamentSlot = linkedMapOf<Int, MutableList<RosterSlotCandidate>>()

        sourceCandidates.forEach { candidate ->
            when (val validation = candidate.validateAssociationMetadata()) {
                is AssociationMetadataValidation.Valid -> {
                    candidatesByTournamentSlot.getOrPut(validation.tournamentSlotNumber) { mutableListOf() }
                        .add(candidate)
                }
                is AssociationMetadataValidation.Invalid -> failures += validation.failure
            }
        }

        val associatedCandidates = candidatesByTournamentSlot
            .toSortedMap()
            .mapNotNull { (tournamentSlotNumber, candidates) ->
                if (candidates.size == 1) {
                    candidates.single().toTournamentSlotCandidate(tournamentSlotNumber)
                } else {
                    failures += RosterSlotAssociationFailure(
                        type = RosterSlotAssociationFailureType.DUPLICATE_TOURNAMENT_SLOT,
                        screenshotPosition = candidates.first().screenshotPosition,
                        visibleSlotPosition = candidates.first().visibleSlotPosition,
                        tournamentSlotNumber = tournamentSlotNumber,
                        sourceCandidates = candidates.toList(),
                    )
                    null
                }
            }

        failures += sourceCandidates.missingPositionFailures()
        return RosterSlotAssociationResult(
            tournamentSlotCandidates = associatedCandidates,
            failures = failures.toList(),
        )
    }

    private fun RosterSlotCandidate.validateAssociationMetadata(): AssociationMetadataValidation {
        val expectedRange = screenshotPosition.tournamentSlotRange
        if (intendedTournamentSlotRange != expectedRange) {
            return AssociationMetadataValidation.Invalid(
                RosterSlotAssociationFailure(
                    type = RosterSlotAssociationFailureType.INVALID_SCREENSHOT_POSITION_METADATA,
                    screenshotPosition = screenshotPosition,
                    visibleSlotPosition = visibleSlotPosition,
                    sourceCandidates = listOf(this),
                ),
            )
        }

        val expectedSlot = screenshotPosition.tournamentSlotFor(visibleSlotPosition)
        if (intendedTournamentSlot != expectedSlot || expectedSlot !in TOURNAMENT_SLOT_NUMBERS) {
            return AssociationMetadataValidation.Invalid(
                RosterSlotAssociationFailure(
                    type = RosterSlotAssociationFailureType.INVALID_VISIBLE_SLOT_METADATA,
                    screenshotPosition = screenshotPosition,
                    visibleSlotPosition = visibleSlotPosition,
                    tournamentSlotNumber = intendedTournamentSlot,
                    sourceCandidates = listOf(this),
                ),
            )
        }

        if (playerNameCandidates.any { it.playerRowIndex !in SUPPORTED_PLAYER_ROWS }) {
            return AssociationMetadataValidation.Invalid(
                RosterSlotAssociationFailure(
                    type = RosterSlotAssociationFailureType.UNSUPPORTED_PLAYER_ROW,
                    screenshotPosition = screenshotPosition,
                    visibleSlotPosition = visibleSlotPosition,
                    tournamentSlotNumber = expectedSlot,
                    sourceCandidates = listOf(this),
                ),
            )
        }

        return AssociationMetadataValidation.Valid(expectedSlot)
    }

    private fun RosterSlotCandidate.toTournamentSlotCandidate(
        tournamentSlotNumber: Int,
    ): RosterTournamentSlotCandidate = RosterTournamentSlotCandidate(
        tournamentSlotNumber = tournamentSlotNumber,
        sourceScreenshotPosition = screenshotPosition,
        sourceVisibleSlotPosition = visibleSlotPosition,
        teamNameCandidate = teamNameCandidate,
        playerNameCandidates = playerNameCandidates,
        associationStatus = RosterSlotAssociationStatus.ASSOCIATED,
    )

    private fun List<RosterSlotCandidate>.missingPositionFailures(): List<RosterSlotAssociationFailure> {
        val sourceCandidates = this
        val availableScreenshots = sourceCandidates.map { it.screenshotPosition }.toSet()
        return buildList {
            RosterScreenshotPosition.entries
                .filterNot { it in availableScreenshots }
                .forEach { screenshotPosition ->
                    add(
                        RosterSlotAssociationFailure(
                            type = RosterSlotAssociationFailureType.MISSING_SCREENSHOT_POSITION,
                            screenshotPosition = screenshotPosition,
                        ),
                    )
                }

            RosterScreenshotPosition.entries
                .filter { it in availableScreenshots }
                .forEach { screenshotPosition ->
                    val availableVisibleSlots = sourceCandidates.asSequence()
                        .filter { it.screenshotPosition == screenshotPosition }
                        .map { it.visibleSlotPosition }
                        .toSet()
                    RosterVisibleSlotPosition.entries
                        .filterNot { it in availableVisibleSlots }
                        .forEach { visibleSlotPosition ->
                            add(
                                RosterSlotAssociationFailure(
                                    type = RosterSlotAssociationFailureType.MISSING_VISIBLE_SLOT,
                                    screenshotPosition = screenshotPosition,
                                    visibleSlotPosition = visibleSlotPosition,
                                    tournamentSlotNumber = screenshotPosition.tournamentSlotFor(
                                        visibleSlotPosition,
                                    ),
                                ),
                            )
                        }
                }
        }
    }

    private sealed interface AssociationMetadataValidation {
        data class Valid(val tournamentSlotNumber: Int) : AssociationMetadataValidation

        data class Invalid(
            val failure: RosterSlotAssociationFailure,
        ) : AssociationMetadataValidation
    }

    private companion object {
        val TOURNAMENT_SLOT_NUMBERS = 1..12
        val SUPPORTED_PLAYER_ROWS = 1..4
    }
}
