package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.LocalRevisionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TournamentRepository {
    /** Missing revision metadata intentionally blocks a cloud write until a safe restore establishes a base. */
    suspend fun readLocalRevisionState(tournamentId: String): LocalRevisionState = LocalRevisionState.Missing

    suspend fun confirmCloudRevision(tournamentId: String, cloudRevision: Int) = Unit

    /** Records an authoritative cloud baseline without discarding local unsynchronized changes. */
    suspend fun establishCloudBaseline(tournamentId: String, cloudRevision: Int) = Unit

    /** Updates the cloud base only after an explicit, draft-only conflict action. */
    suspend fun rebaseCloudRevisionForConflictResolution(
        tournamentId: String,
        cloudRevision: Int,
    ) = Unit

    suspend fun create(tournament: Tournament)

    fun observeAll(): Flow<List<Tournament>>

    fun observeAllByOwner(ownerUserId: String): Flow<List<Tournament>> =
        observeAll().map { tournaments -> tournaments.filter { it.ownerUserId == ownerUserId } }

    fun observeSummaries(): Flow<List<TournamentSummary>> = observeAll().map { tournaments ->
        tournaments.map { tournament ->
            TournamentSummary(
                tournament = tournament,
                totalTeams = 0,
                totalMatches = 0,
                lastUpdatedEpochMillis = null,
            )
        }
    }

    fun observeSummariesByOwner(ownerUserId: String): Flow<List<TournamentSummary>> =
        observeSummaries().map { summaries ->
            summaries.filter { it.tournament.ownerUserId == ownerUserId }
        }

    fun observeById(tournamentId: String): Flow<Tournament?>

    fun observeByIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Flow<Tournament?> = observeById(tournamentId).map { tournament ->
        tournament?.takeIf { it.ownerUserId == ownerUserId }
    }

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

    suspend fun replaceConfirmedTournamentRoster(
        candidate: ConfirmedRosterReplacementCandidate,
    ): ReplaceConfirmedTournamentRosterRepositoryResult =
        error("Confirmed roster replacement is not supported by this repository.")

    suspend fun confirmTournament(tournamentId: String): Boolean

    fun observeMatchesByTournamentId(tournamentId: String): Flow<List<Match>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    fun observeMatchById(matchId: String): Flow<Match?> =
        kotlinx.coroutines.flow.flowOf(null)

    /** Returns the authoritative OCR evidence already persisted for a match, if any. */
    suspend fun readPreservedMatchOcrEvidence(
        tournamentId: String,
        matchId: String,
    ): PreservedMatchOcrEvidence? = null

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
        participantResults: List<MatchParticipantResult>? = null,
    ): FinalizeMatchRepositoryResult =
        error("Match finalization is not supported by this repository.")

    suspend fun finalizeDraftMatchWithOcrEvidence(
        matchId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>? = null,
        evidence: PreservedMatchOcrEvidence,
    ): FinalizeMatchRepositoryResult =
        error("OCR evidence finalization is not supported by this repository.")

    suspend fun submitMatchCorrection(
        matchId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>? = null,
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
