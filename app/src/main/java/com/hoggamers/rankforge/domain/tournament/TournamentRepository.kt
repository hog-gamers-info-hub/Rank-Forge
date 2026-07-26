package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.Flow

interface TournamentRepository {
    suspend fun create(tournament: Tournament)

    fun observeAll(): Flow<List<Tournament>>

    fun observeById(tournamentId: String): Flow<Tournament?>

    fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>>

    suspend fun saveTeamNames(
        tournamentId: String,
        teamNamesBySlotNumber: Map<Int, String>,
    )

    fun observeRosterByTournamentAndSlot(
        tournamentId: String,
        slotNumber: Int,
    ): Flow<List<RosterPlayer>>

    fun observeRosterByTournamentId(
        tournamentId: String,
    ): Flow<Map<Int, List<RosterPlayer>>> = kotlinx.coroutines.flow.flowOf(emptyMap())

    suspend fun saveRoster(
        tournamentId: String,
        slotNumber: Int,
        players: List<RosterPlayer>,
    )

    suspend fun confirmTournament(tournamentId: String): Boolean

    fun observeMatchesByTournamentId(tournamentId: String): Flow<List<Match>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    fun observeMatchById(matchId: String): Flow<Match?> =
        kotlinx.coroutines.flow.flowOf(null)

    suspend fun createDraftMatch(match: Match): CreateMatchRepositoryResult =
        error("Match creation is not supported by this repository.")

    suspend fun saveDraftMatchPlacements(
        matchId: String,
        placements: List<MatchPlacement>,
    ): SaveMatchPlacementsRepositoryResult =
        error("Match placement updates are not supported by this repository.")

    suspend fun saveDraftMatchKills(
        matchId: String,
        kills: List<MatchKill>,
    ): SaveMatchKillsRepositoryResult =
        error("Match kill updates are not supported by this repository.")

    suspend fun finalizeDraftMatch(
        matchId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
    ): FinalizeMatchRepositoryResult =
        error("Match finalization is not supported by this repository.")

    suspend fun submitMatchCorrection(
        matchId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
    ): SubmitMatchCorrectionRepositoryResult =
        error("Match correction is not supported by this repository.")

    fun observeDraftMatchValues(
        tournamentId: String,
        matchId: String,
    ): Flow<Map<Int, MatchDraftFieldValues>> = kotlinx.coroutines.flow.flowOf(emptyMap())

    suspend fun saveDraftMatchValue(
        tournamentId: String,
        matchId: String,
        teamSlotNumber: Int,
        placementInput: String? = null,
        killsInput: String? = null,
    ) = Unit

    /** Clears editable result values for one draft match. Finalization can call this later. */
    suspend fun clearDraftMatch(
        tournamentId: String,
        matchId: String,
    ) = Unit

    suspend fun clearMatchCorrectionDraft(
        tournamentId: String,
        matchId: String,
    ) = Unit
}
