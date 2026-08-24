package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.LocalRevisionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
interface TournamentRepository {
    /** Missing revision metadata intentionally blocks a cloud write until a safe restore establishes a base. */
    suspend fun readLocalRevisionState(tournamentId: String): LocalRevisionState = LocalRevisionState.Missing

    suspend fun confirmCloudRevision(tournamentId: String, cloudRevision: Int) = Unit

    suspend fun confirmCloudRevisionByOwner(
        tournamentId: String,
        ownerUserId: String,
        cloudRevision: Int,
    ): OwnerScopedTournamentMutationResult =
        error("Owner-scoped cloud revision confirmation is not supported by this repository.")

    suspend fun establishCloudBaselineByOwner(
        tournamentId: String,
        ownerUserId: String,
        cloudRevision: Int,
    ): OwnerScopedTournamentMutationResult =
        error("Owner-scoped cloud baseline establishment is not supported by this repository.")

    /** Records an authoritative cloud baseline without discarding local unsynchronized changes. */
    suspend fun establishCloudBaseline(tournamentId: String, cloudRevision: Int) = Unit

    /** Updates the cloud base only after an explicit, draft-only conflict action. */
    suspend fun rebaseCloudRevisionForConflictResolution(
        tournamentId: String,
        cloudRevision: Int,
    ) = Unit

    suspend fun rebaseCloudRevisionForConflictResolutionByOwner(
        tournamentId: String,
        ownerUserId: String,
        cloudRevision: Int,
    ): OwnerScopedTournamentMutationResult =
        error("Owner-scoped cloud revision rebase is not supported by this repository.")

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

    /** Trusted reconciliation-only API for pre-ownership legacy rows. */
    suspend fun readOwnerlessLegacyTournaments(): List<Tournament> = emptyList()

    suspend fun assignLegacyTournamentOwnerIfUnassigned(
        tournamentId: String,
        provenOwnerUserId: String,
    ): LegacyTournamentOwnerAssignmentResult = LegacyTournamentOwnerAssignmentResult.NotUnassigned

    fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>>

    fun observeSlotsByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Flow<List<TeamSlot>> = observeByIdAndOwner(tournamentId, ownerUserId).flatMapLatest {
        tournament ->
        if (tournament == null) flowOf(emptyList()) else observeSlotsByTournamentId(tournamentId)
    }

    suspend fun saveTeamNames(
        tournamentId: String,
        teamNamesBySlotNumber: Map<Int, String>,
    )

    suspend fun saveTeamNamesByOwner(
        tournamentId: String,
        ownerUserId: String,
        teamNamesBySlotNumber: Map<Int, String>,
    ): OwnerScopedTournamentMutationResult = if (
        observeByIdAndOwner(tournamentId, ownerUserId).first() == null
    ) {
        OwnerScopedTournamentMutationResult.TournamentNotFound
    } else {
        saveTeamNames(tournamentId, teamNamesBySlotNumber)
        OwnerScopedTournamentMutationResult.Saved
    }

    fun observeRosterByTournamentAndSlot(
        tournamentId: String,
        slotNumber: Int,
    ): Flow<List<RosterPlayer>>

    fun observeRosterByTournamentAndSlotAndOwner(
        tournamentId: String,
        slotNumber: Int,
        ownerUserId: String,
    ): Flow<List<RosterPlayer>> = observeByIdAndOwner(tournamentId, ownerUserId).flatMapLatest {
        tournament ->
        if (tournament == null) {
            flowOf(emptyList())
        } else {
            observeRosterByTournamentAndSlot(tournamentId, slotNumber)
        }
    }

    fun observeRosterByTournamentId(
        tournamentId: String,
    ): Flow<Map<Int, List<RosterPlayer>>> = kotlinx.coroutines.flow.flowOf(emptyMap())

    fun observeRosterByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Flow<Map<Int, List<RosterPlayer>>> = observeByIdAndOwner(tournamentId, ownerUserId).flatMapLatest {
        tournament ->
        if (tournament == null) flowOf(emptyMap()) else observeRosterByTournamentId(tournamentId)
    }

    suspend fun saveRoster(
        tournamentId: String,
        slotNumber: Int,
        players: List<RosterPlayer>,
    )

    suspend fun saveRosterByOwner(
        tournamentId: String,
        ownerUserId: String,
        slotNumber: Int,
        players: List<RosterPlayer>,
    ): OwnerScopedTournamentMutationResult = if (
        observeByIdAndOwner(tournamentId, ownerUserId).first() == null
    ) {
        OwnerScopedTournamentMutationResult.TournamentNotFound
    } else {
        saveRoster(tournamentId, slotNumber, players)
        OwnerScopedTournamentMutationResult.Saved
    }

    suspend fun replaceConfirmedTournamentRoster(
        candidate: ConfirmedRosterReplacementCandidate,
    ): ReplaceConfirmedTournamentRosterRepositoryResult =
        error("Confirmed roster replacement is not supported by this repository.")

    suspend fun replaceConfirmedTournamentRosterByOwner(
        candidate: ConfirmedRosterReplacementCandidate,
        ownerUserId: String,
    ): ReplaceConfirmedTournamentRosterRepositoryResult = if (
        observeByIdAndOwner(candidate.tournamentId, ownerUserId).first() == null
    ) {
        ReplaceConfirmedTournamentRosterRepositoryResult.TournamentNotFound
    } else {
        replaceConfirmedTournamentRoster(candidate)
    }

    suspend fun confirmTournament(tournamentId: String): Boolean

    suspend fun confirmTournamentByOwner(
        tournamentId: String,
        ownerUserId: String,
    ): OwnerScopedTournamentConfirmationResult = if (
        observeByIdAndOwner(tournamentId, ownerUserId).first() == null
    ) {
        OwnerScopedTournamentConfirmationResult.TournamentNotFound
    } else if (confirmTournament(tournamentId)) {
        OwnerScopedTournamentConfirmationResult.Confirmed
    } else {
        OwnerScopedTournamentConfirmationResult.AlreadyConfirmed
    }

    fun observeMatchesByTournamentId(tournamentId: String): Flow<List<Match>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    fun observeMatchesByTournamentIdAndOwner(
        tournamentId: String,
        ownerUserId: String,
    ): Flow<List<Match>> = observeByIdAndOwner(tournamentId, ownerUserId).flatMapLatest { tournament ->
        if (tournament == null) flowOf(emptyList()) else observeMatchesByTournamentId(tournamentId)
    }

    fun observeMatchById(matchId: String): Flow<Match?> =
        kotlinx.coroutines.flow.flowOf(null)

    fun observeMatchByIdAndOwner(
        matchId: String,
        ownerUserId: String,
    ): Flow<Match?> = observeMatchById(matchId).flatMapLatest { match ->
        if (match == null) {
            flowOf(null)
        } else {
            observeByIdAndOwner(match.tournamentId, ownerUserId).map { tournament ->
                match.takeIf { tournament != null }
            }
        }
    }

    /** Returns the authoritative OCR evidence already persisted for a match, if any. */
    suspend fun readPreservedMatchOcrEvidence(
        tournamentId: String,
        matchId: String,
    ): PreservedMatchOcrEvidence? = null

    suspend fun readPreservedMatchOcrEvidenceByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
    ): PreservedMatchOcrEvidence? = null

    suspend fun createDraftMatch(match: Match): CreateMatchRepositoryResult =
        error("Match creation is not supported by this repository.")

    suspend fun createDraftMatchByOwner(
        match: Match,
        ownerUserId: String,
    ): CreateMatchRepositoryResult =
        error("Owner-scoped match creation is not supported by this repository.")

    suspend fun saveDraftMatchPlacements(
        matchId: String,
        placements: List<MatchPlacement>,
    ): SaveMatchPlacementsRepositoryResult =
        error("Match placement updates are not supported by this repository.")

    suspend fun saveDraftMatchPlacementsByOwner(
        matchId: String,
        ownerUserId: String,
        placements: List<MatchPlacement>,
    ): SaveMatchPlacementsRepositoryResult =
        error("Owner-scoped match placement updates are not supported by this repository.")

    suspend fun saveDraftMatchKills(
        matchId: String,
        kills: List<MatchKill>,
    ): SaveMatchKillsRepositoryResult =
        error("Match kill updates are not supported by this repository.")

    suspend fun saveDraftMatchKillsByOwner(
        matchId: String,
        ownerUserId: String,
        kills: List<MatchKill>,
    ): SaveMatchKillsRepositoryResult =
        error("Owner-scoped match kill updates are not supported by this repository.")

    suspend fun finalizeDraftMatch(
        matchId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>? = null,
    ): FinalizeMatchRepositoryResult =
        error("Match finalization is not supported by this repository.")

    suspend fun finalizeDraftMatchByOwner(
        matchId: String,
        ownerUserId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>? = null,
    ): FinalizeMatchRepositoryResult =
        error("Owner-scoped match finalization is not supported by this repository.")

    suspend fun finalizeDraftMatchWithOcrEvidence(
        matchId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>? = null,
        evidence: PreservedMatchOcrEvidence,
    ): FinalizeMatchRepositoryResult =
        error("OCR evidence finalization is not supported by this repository.")

    suspend fun finalizeDraftMatchWithOcrEvidenceByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>? = null,
        evidence: PreservedMatchOcrEvidence,
    ): FinalizeMatchRepositoryResult =
        error("Owner-scoped OCR evidence finalization is not supported by this repository.")

    suspend fun submitMatchCorrection(
        matchId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>? = null,
    ): SubmitMatchCorrectionRepositoryResult =
        error("Match correction is not supported by this repository.")

    suspend fun submitMatchCorrectionByOwner(
        matchId: String,
        ownerUserId: String,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult>? = null,
    ): SubmitMatchCorrectionRepositoryResult =
        error("Owner-scoped match correction is not supported by this repository.")

    fun observeDraftMatchValues(
        tournamentId: String,
        matchId: String,
    ): Flow<Map<Int, MatchDraftFieldValues>> = kotlinx.coroutines.flow.flowOf(emptyMap())

    fun observeDraftMatchValuesByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
    ): Flow<Map<Int, MatchDraftFieldValues>> = observeByIdAndOwner(tournamentId, ownerUserId).flatMapLatest {
        tournament ->
        if (tournament == null) {
            flowOf(emptyMap())
        } else {
            observeMatchById(matchId).flatMapLatest { match ->
                if (match?.tournamentId == tournamentId) {
                    observeDraftMatchValues(tournamentId, matchId)
                } else {
                    flowOf(emptyMap())
                }
            }
        }
    }

    suspend fun saveDraftMatchValue(
        tournamentId: String,
        matchId: String,
        teamSlotNumber: Int,
        placementInput: String? = null,
        killsInput: String? = null,
    ) = Unit

    suspend fun saveDraftMatchValueByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
        teamSlotNumber: Int,
        placementInput: String? = null,
        killsInput: String? = null,
    ): OwnerScopedMatchMutationResult =
        error("Owner-scoped draft match value updates are not supported by this repository.")

    /** Clears editable result values for one draft match. Finalization can call this later. */
    suspend fun clearDraftMatch(
        tournamentId: String,
        matchId: String,
    ) = Unit

    suspend fun clearDraftMatchByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
    ): OwnerScopedMatchMutationResult =
        error("Owner-scoped draft match clearing is not supported by this repository.")

    suspend fun clearMatchCorrectionDraft(
        tournamentId: String,
        matchId: String,
    ) = Unit

    suspend fun clearMatchCorrectionDraftByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
    ): OwnerScopedMatchMutationResult =
        error("Owner-scoped correction draft clearing is not supported by this repository.")
}

sealed interface OwnerScopedMatchMutationResult {
    data object Saved : OwnerScopedMatchMutationResult

    data object MatchNotFound : OwnerScopedMatchMutationResult
}

sealed interface OwnerScopedTournamentMutationResult {
    data object Saved : OwnerScopedTournamentMutationResult

    data object TournamentNotFound : OwnerScopedTournamentMutationResult
}

sealed interface OwnerScopedTournamentConfirmationResult {
    data object Confirmed : OwnerScopedTournamentConfirmationResult

    data object AlreadyConfirmed : OwnerScopedTournamentConfirmationResult

    data object TournamentNotFound : OwnerScopedTournamentConfirmationResult
}

sealed interface LegacyTournamentOwnerAssignmentResult {
    data object Assigned : LegacyTournamentOwnerAssignmentResult

    /** The row is missing or has already been assigned by another safe writer. */
    data object NotUnassigned : LegacyTournamentOwnerAssignmentResult
}
