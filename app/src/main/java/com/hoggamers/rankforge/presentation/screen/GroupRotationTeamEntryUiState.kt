package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupIssue
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityNormalizer

data class GroupRotationTeamEntryUiState(
    val isLoading: Boolean = true,
    val tournamentName: String = "",
    val stageName: String = "",
    val pairingSections: List<GroupRotationPairingEntryUiState> = emptyList(),
    val selectedPairingKey: String? = null,
    val uniqueTeamCount: Int = 0,
    val maximumUniqueTeams: Int = 0,
    val isSaving: Boolean = false,
    val validationIssues: List<GroupRotationTeamSetupIssue> = emptyList(),
    val loadError: GroupRotationTeamEntryLoadError? = null,
    val saveError: GroupRotationTeamEntrySaveError? = null,
) {
    val selectedPairing: GroupRotationPairingEntryUiState?
        get() = pairingSections.firstOrNull { it.pairing.canonicalKey == selectedPairingKey }
}

data class GroupRotationPairingEntryUiState(
    val pairing: GroupPairing,
    val rows: List<GroupRotationLobbyTeamUiState>,
) {
    val filledCount: Int
        get() = rows.count { it.teamName.trim().isNotEmpty() }
}

data class GroupRotationLobbyTeamUiState(
    val lobbySlotNumber: Int,
    val teamName: String,
)

enum class GroupRotationTeamEntryLoadError {
    AuthenticationRequired,
    TournamentNotFound,
    InvalidStoredSetup,
    InvalidStoredDraft,
}

enum class GroupRotationTeamEntrySaveError {
    AuthenticationRequired,
    TournamentNotFound,
    ProtectedHistory,
    Unexpected,
}

fun GroupRotationTeamEntryUiState.uniqueNormalizedTeamCount(): Int {
    val normalizer = GroupRotationTeamIdentityNormalizer()
    return pairingSections
        .asSequence()
        .flatMap { section -> section.rows.asSequence() }
        .map { row -> normalizer.normalize(row.teamName) }
        .filter(String::isNotBlank)
        .toSet()
        .size
}
