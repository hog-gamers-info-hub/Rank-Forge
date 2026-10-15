package com.hoggamers.rankforge.domain.tournament

data class GroupRotationTeamSetupRead(
    val tournament: Tournament,
    val candidate: GroupRotationTeamSetupCandidate,
)

sealed interface GroupRotationTeamSetupReadResult {
    data class Loaded(val setup: GroupRotationTeamSetupRead) : GroupRotationTeamSetupReadResult

    data class NoSavedSetup(val tournament: Tournament) : GroupRotationTeamSetupReadResult

    data object TournamentNotFound : GroupRotationTeamSetupReadResult

    data object InvalidStoredSetup : GroupRotationTeamSetupReadResult
}

interface GroupRotationTeamSetupReadRepository {
    suspend fun readGroupRotationTeamSetup(
        tournamentId: String,
        ownerUserId: String,
    ): GroupRotationTeamSetupReadResult
}
