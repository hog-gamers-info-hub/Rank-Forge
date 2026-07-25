package com.hoggamers.rankforge.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val ROSTER_VALIDATION_ISSUES_TEST_TAG = "roster_validation_issues"

@Composable
fun RosterValidationIssues(
    issues: List<RosterValidationIssueUiState>,
    modifier: Modifier = Modifier,
) {
    if (issues.isEmpty()) return

    Column(
        modifier = modifier.testTag(ROSTER_VALIDATION_ISSUES_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.roster_validation_issues_title),
            style = MaterialTheme.typography.titleMedium,
        )
        issues.forEach { issue ->
            Text(
                text = when (issue) {
                    is RosterValidationIssueUiState.MissingTeamName -> stringResource(
                        R.string.validation_missing_team_name,
                        issue.slotNumber,
                    )
                    is RosterValidationIssueUiState.DuplicateTeamName -> stringResource(
                        R.string.validation_duplicate_team_name,
                        issue.slotNumber,
                        issue.firstSlotNumber,
                        issue.normalizedName,
                    )
                    is RosterValidationIssueUiState.InvalidPlayerCount -> stringResource(
                        R.string.validation_invalid_player_count,
                        issue.slotNumber,
                        issue.playerCount,
                    )
                    is RosterValidationIssueUiState.DuplicatePlayerName -> stringResource(
                        R.string.validation_duplicate_player_name,
                        issue.slotNumber,
                        issue.playerIndex + 1,
                        issue.firstPlayerIndex + 1,
                        issue.normalizedName,
                    )
                },
                color = if (issue.isBlocking) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
