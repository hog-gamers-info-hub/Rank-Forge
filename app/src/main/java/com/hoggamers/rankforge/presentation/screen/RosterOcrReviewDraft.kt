package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterPlayerNameCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationIssue
import com.hoggamers.rankforge.domain.ocr.review.ProcessRosterOcrEvidence
import com.hoggamers.rankforge.domain.tournament.ConfirmedRosterReplacementCandidate
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.RosterValidationIssue
import com.hoggamers.rankforge.domain.tournament.RosterValidationPlayer
import com.hoggamers.rankforge.domain.tournament.RosterValidationResult
import com.hoggamers.rankforge.domain.tournament.RosterValidationTeam
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.TeamSlot

enum class RosterOcrReviewDraftStatus {
    VALID,
    WARNING,
    BLOCKED,
}

enum class RosterOcrReviewDraftIssueType {
    MALFORMED_STRUCTURE,
    MISSING_TEAM_NAME,
    DUPLICATE_TEAM_NAME,
    INVALID_PLAYER_COUNT,
    DUPLICATE_PLAYER_NAME,
}

data class RosterOcrReviewDraftIssue(
    val type: RosterOcrReviewDraftIssueType,
    val slotNumber: Int? = null,
    val playerRowIndex: Int? = null,
    val detail: String? = null,
)

data class RosterOcrReviewDraftValidation(
    val issues: List<RosterOcrReviewDraftIssue> = emptyList(),
) {
    val blockers: List<RosterOcrReviewDraftIssue>
        get() = issues

    val hasBlockers: Boolean
        get() = issues.isNotEmpty()
}

data class RosterOcrReviewPlayerDraft(
    val playerRowIndex: Int,
    val originalOcrValue: String,
    val draftValue: String,
    val sourceCandidate: RosterPlayerNameCandidate? = null,
    val isManualOnly: Boolean = false,
    val validation: List<RosterOcrReviewDraftIssue> = emptyList(),
) {
    val isDirty: Boolean
        get() = draftValue != originalOcrValue

    val sourceStatus: RosterCandidateParseStatus?
        get() = sourceCandidate?.status
}

data class RosterOcrReviewSlotDraft(
    val slotNumber: Int,
    val currentTeamName: String,
    val players: List<RosterOcrReviewPlayerDraft>,
    val sourceIssues: List<RosterOcrValidationIssue> = emptyList(),
    val validation: List<RosterOcrReviewDraftIssue> = emptyList(),
)

data class RosterOcrReviewDraft(
    val tournamentId: String,
    val slots: List<RosterOcrReviewSlotDraft>,
    val originalEvidence: ProcessRosterOcrEvidence,
    val finalValidation: RosterOcrReviewDraftValidation = RosterOcrReviewDraftValidation(),
) {
    val isDirty: Boolean
        get() = slots.any { slot -> slot.players.any { player -> player.isDirty } }

    val blockerCount: Int
        get() = finalValidation.issues.size

    val warningCount: Int
        get() = originalEvidence.validation.issues.size

    val status: RosterOcrReviewDraftStatus
        get() = when {
            finalValidation.hasBlockers -> RosterOcrReviewDraftStatus.BLOCKED
            warningCount > 0 -> RosterOcrReviewDraftStatus.WARNING
            else -> RosterOcrReviewDraftStatus.VALID
        }

    val canConfirm: Boolean
        get() = finalValidation.issues.isEmpty()

    fun toConfirmedRosterReplacementCandidateOrNull(): ConfirmedRosterReplacementCandidate? {
        if (!canConfirm) return null
        return ConfirmedRosterReplacementCandidate(
            tournamentId = tournamentId,
            teamNamesBySlotNumber = slots.associate { it.slotNumber to it.currentTeamName },
            rosterPlayersBySlotNumber = slots.associate { slot ->
                slot.slotNumber to slot.players
                    .filter { it.draftValue.trim().isNotEmpty() }
                    .map { player ->
                        RosterPlayer.create(
                            tournamentId = tournamentId,
                            slotNumber = slot.slotNumber,
                            displayName = player.draftValue.trim(),
                        )
                    }
            },
        )
    }
}

sealed interface RosterOcrReviewDraftCreationResult {
    data class Created(val draft: RosterOcrReviewDraft) : RosterOcrReviewDraftCreationResult

    data class Rejected(val reason: RosterOcrReviewDraftCreationFailure) : RosterOcrReviewDraftCreationResult
}

enum class RosterOcrReviewDraftCreationFailure {
    INVALID_TOURNAMENT_CONTEXT,
    INCOMPLETE_TEAM_CONTEXT,
    DUPLICATE_TEAM_CONTEXT,
    INVALID_TEAM_SLOT,
    MISMATCHED_TOURNAMENT_CONTEXT,
}

object RosterOcrReviewDraftReducer {
    private val EXPECTED_SLOTS = TeamSlot.SLOT_NUMBERS.toSet()
    private val EXPECTED_PLAYER_ROWS = 1..6

    fun createInitialDraft(
        tournamentId: String,
        currentTeamSlots: List<TeamSlot>,
        evidence: ProcessRosterOcrEvidence,
    ): RosterOcrReviewDraftCreationResult {
        if (tournamentId.isBlank()) {
            return RosterOcrReviewDraftCreationResult.Rejected(
                RosterOcrReviewDraftCreationFailure.INVALID_TOURNAMENT_CONTEXT,
            )
        }
        if (currentTeamSlots.any { it.tournamentId != tournamentId }) {
            return RosterOcrReviewDraftCreationResult.Rejected(
                RosterOcrReviewDraftCreationFailure.MISMATCHED_TOURNAMENT_CONTEXT,
            )
        }
        if (currentTeamSlots.any { it.slotNumber !in TeamSlot.SLOT_NUMBERS }) {
            return RosterOcrReviewDraftCreationResult.Rejected(
                RosterOcrReviewDraftCreationFailure.INVALID_TEAM_SLOT,
            )
        }
        if (currentTeamSlots.map { it.slotNumber }.distinct().size != currentTeamSlots.size) {
            return RosterOcrReviewDraftCreationResult.Rejected(
                RosterOcrReviewDraftCreationFailure.DUPLICATE_TEAM_CONTEXT,
            )
        }
        if (currentTeamSlots.size != EXPECTED_SLOTS.size ||
            currentTeamSlots.map { it.slotNumber }.toSet() != EXPECTED_SLOTS
        ) {
            return RosterOcrReviewDraftCreationResult.Rejected(
                RosterOcrReviewDraftCreationFailure.INCOMPLETE_TEAM_CONTEXT,
            )
        }

        val candidatesBySlot = evidence.associatedCandidates.tournamentSlotCandidates
            .groupBy { it.tournamentSlotNumber }
        val slotsByNumber = currentTeamSlots.associateBy { it.slotNumber }
        val draft = RosterOcrReviewDraft(
            tournamentId = tournamentId,
            slots = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
                val sourceCandidates = candidatesBySlot[slotNumber].orEmpty()
                val source = sourceCandidates.singleOrNull()
                RosterOcrReviewSlotDraft(
                    slotNumber = slotNumber,
                    currentTeamName = slotsByNumber.getValue(slotNumber).teamName,
                    players = EXPECTED_PLAYER_ROWS.map { rowIndex ->
                        val sourceCandidate = if (rowIndex in 1..4) {
                            source
                                ?.playerNameCandidates
                                ?.filter { it.playerRowIndex == rowIndex }
                                ?.singleOrNull()
                        } else {
                            null
                        }
                        val originalValue = sourceCandidate
                            ?.takeIf {
                                it.playerRowIndex in 1..4 &&
                                    it.status == RosterCandidateParseStatus.PARSED &&
                                    !it.candidateText.isNullOrBlank()
                            }
                            ?.candidateText
                            ?.trim()
                            .orEmpty()
                        RosterOcrReviewPlayerDraft(
                            playerRowIndex = rowIndex,
                            originalOcrValue = originalValue,
                            draftValue = originalValue,
                            sourceCandidate = sourceCandidate,
                            isManualOnly = rowIndex in 5..6,
                        )
                    },
                    sourceIssues = evidence.validation.issues.filter {
                        it.tournamentSlotNumber == slotNumber
                    },
                )
            },
            originalEvidence = evidence,
        )
        return RosterOcrReviewDraftCreationResult.Created(validate(draft))
    }

    fun updatePlayerName(
        draft: RosterOcrReviewDraft,
        slotNumber: Int,
        playerRowIndex: Int,
        value: String,
    ): RosterOcrReviewDraft = updatePlayer(draft, slotNumber, playerRowIndex) { player ->
        player.copy(draftValue = value)
    }

    fun resetPlayerCorrection(
        draft: RosterOcrReviewDraft,
        slotNumber: Int,
        playerRowIndex: Int,
    ): RosterOcrReviewDraft = updatePlayer(draft, slotNumber, playerRowIndex) { player ->
        player.copy(draftValue = player.originalOcrValue)
    }

    fun resetSlotCorrections(
        draft: RosterOcrReviewDraft,
        slotNumber: Int,
    ): RosterOcrReviewDraft = validate(
        draft.copy(
            slots = draft.slots.map { slot ->
                if (slot.slotNumber != slotNumber) slot else slot.copy(
                    players = slot.players.map { player ->
                        player.copy(draftValue = player.originalOcrValue)
                    },
                )
            },
        ),
    )

    fun resetAllCorrections(draft: RosterOcrReviewDraft): RosterOcrReviewDraft = validate(
        draft.copy(
            slots = draft.slots.map { slot ->
                slot.copy(
                    players = slot.players.map { player ->
                        player.copy(draftValue = player.originalOcrValue)
                    },
                )
            },
        ),
    )

    fun validate(draft: RosterOcrReviewDraft): RosterOcrReviewDraft {
        val issues = mutableListOf<RosterOcrReviewDraftIssue>()
        val slotCounts = draft.slots.groupingBy { it.slotNumber }.eachCount()
        if (draft.tournamentId.isBlank() ||
            draft.slots.size != EXPECTED_SLOTS.size ||
            draft.slots.map { it.slotNumber }.toSet() != EXPECTED_SLOTS ||
            draft.slots.any { slotCounts.getValue(it.slotNumber) > 1 }
        ) {
            issues += RosterOcrReviewDraftIssue(RosterOcrReviewDraftIssueType.MALFORMED_STRUCTURE)
        }

        if (draft.slots.any { it.players.size != EXPECTED_PLAYER_ROWS.count() }) {
            issues += RosterOcrReviewDraftIssue(RosterOcrReviewDraftIssueType.MALFORMED_STRUCTURE)
        }
        draft.slots.forEach { slot ->
            if (slot.players.map { it.playerRowIndex }.toSet() != EXPECTED_PLAYER_ROWS.toSet() ||
                slot.players.any { it.playerRowIndex !in EXPECTED_PLAYER_ROWS }
            ) {
                issues += RosterOcrReviewDraftIssue(
                    type = RosterOcrReviewDraftIssueType.MALFORMED_STRUCTURE,
                    slotNumber = slot.slotNumber,
                )
            }
        }

        val validation = RosterValidator().validate(
            draft.slots.map { slot ->
                RosterValidationTeam(
                    slotNumber = slot.slotNumber,
                    teamName = slot.currentTeamName,
                    players = slot.players
                        .filter { it.draftValue.trim().isNotEmpty() }
                        .map { player ->
                            RosterValidationPlayer(
                                playerIndex = player.playerRowIndex,
                                displayName = player.draftValue.trim(),
                            )
                        },
                )
            },
        )
        issues += validation.toDraftIssues()

        val distinctIssues = issues.distinct()
        val bySlot = distinctIssues.groupBy { it.slotNumber }
        return draft.copy(
            finalValidation = RosterOcrReviewDraftValidation(distinctIssues),
            slots = draft.slots.map { slot ->
                val slotIssues = bySlot[slot.slotNumber].orEmpty()
                slot.copy(
                    validation = slotIssues,
                    players = slot.players.map { player ->
                        player.copy(
                            validation = slotIssues.filter { it.playerRowIndex == player.playerRowIndex },
                        )
                    },
                )
            },
        )
    }

    private fun updatePlayer(
        draft: RosterOcrReviewDraft,
        slotNumber: Int,
        playerRowIndex: Int,
        transform: (RosterOcrReviewPlayerDraft) -> RosterOcrReviewPlayerDraft,
    ): RosterOcrReviewDraft = validate(
        draft.copy(
            slots = draft.slots.map { slot ->
                if (slot.slotNumber != slotNumber) slot else slot.copy(
                    players = slot.players.map { player ->
                        if (player.playerRowIndex == playerRowIndex) transform(player) else player
                    },
                )
            },
        ),
    )

    private fun RosterValidationResult.toDraftIssues(): List<RosterOcrReviewDraftIssue> = issues.map { issue ->
        when (issue) {
            is RosterValidationIssue.MissingTeamName -> RosterOcrReviewDraftIssue(
                RosterOcrReviewDraftIssueType.MISSING_TEAM_NAME,
                slotNumber = issue.slotNumber,
            )
            is RosterValidationIssue.DuplicateTeamName -> RosterOcrReviewDraftIssue(
                RosterOcrReviewDraftIssueType.DUPLICATE_TEAM_NAME,
                slotNumber = issue.slotNumber,
                detail = issue.normalizedName,
            )
            is RosterValidationIssue.InvalidPlayerCount -> RosterOcrReviewDraftIssue(
                RosterOcrReviewDraftIssueType.INVALID_PLAYER_COUNT,
                slotNumber = issue.slotNumber,
                detail = issue.playerCount.toString(),
            )
            is RosterValidationIssue.DuplicatePlayerName -> RosterOcrReviewDraftIssue(
                RosterOcrReviewDraftIssueType.DUPLICATE_PLAYER_NAME,
                slotNumber = issue.slotNumber,
                playerRowIndex = issue.playerIndex,
                detail = issue.normalizedName,
            )
        }
    }
}
