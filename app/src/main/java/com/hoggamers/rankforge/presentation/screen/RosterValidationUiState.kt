package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.RosterValidationIssue
import com.hoggamers.rankforge.domain.tournament.RosterValidationResult

sealed interface RosterValidationIssueUiState {
    val slotNumber: Int
    val isBlocking: Boolean

    data class MissingTeamName(
        override val slotNumber: Int,
    ) : RosterValidationIssueUiState {
        override val isBlocking: Boolean = false
    }

    data class DuplicateTeamName(
        override val slotNumber: Int,
        val firstSlotNumber: Int,
        val normalizedName: String,
    ) : RosterValidationIssueUiState {
        override val isBlocking: Boolean = true
    }

    data class InvalidPlayerCount(
        override val slotNumber: Int,
        val playerCount: Int,
    ) : RosterValidationIssueUiState {
        override val isBlocking: Boolean = false
    }

    data class DuplicatePlayerName(
        override val slotNumber: Int,
        val playerIndex: Int,
        val firstPlayerIndex: Int,
        val normalizedName: String,
    ) : RosterValidationIssueUiState {
        override val isBlocking: Boolean = true
    }
}

fun RosterValidationResult.toUiState(): List<RosterValidationIssueUiState> = issues.map { issue ->
    when (issue) {
        is RosterValidationIssue.MissingTeamName -> RosterValidationIssueUiState.MissingTeamName(
            slotNumber = issue.slotNumber,
        )
        is RosterValidationIssue.DuplicateTeamName -> RosterValidationIssueUiState.DuplicateTeamName(
            slotNumber = issue.slotNumber,
            firstSlotNumber = issue.firstSlotNumber,
            normalizedName = issue.normalizedName,
        )
        is RosterValidationIssue.InvalidPlayerCount -> RosterValidationIssueUiState.InvalidPlayerCount(
            slotNumber = issue.slotNumber,
            playerCount = issue.playerCount,
        )
        is RosterValidationIssue.DuplicatePlayerName -> RosterValidationIssueUiState.DuplicatePlayerName(
            slotNumber = issue.slotNumber,
            playerIndex = issue.playerIndex,
            firstPlayerIndex = issue.firstPlayerIndex,
            normalizedName = issue.normalizedName,
        )
    }
}
