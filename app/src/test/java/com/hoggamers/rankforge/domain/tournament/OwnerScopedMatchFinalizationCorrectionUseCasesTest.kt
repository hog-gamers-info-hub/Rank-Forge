package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.LocalRevisionState
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerScopedMatchFinalizationCorrectionUseCasesTest {
    @Test fun finalizeUsesOnlyOwnedPathsAndRejectsUnauthenticatedForeignAndLegacy() = runTest {
        val owned = RecordingRepository(OWNER_A, MatchStatus.DRAFT)
        assertTrue(finalize(owned, auth(OWNER_A))(FinalizeMatchInput(MATCH_ID, rows())) is FinalizeMatchResult.Finalized)
        assertEquals(1, owned.finalizeCalls)
        listOf(auth(null), auth(" ")).forEach { identity ->
            val repository = RecordingRepository(OWNER_A, MatchStatus.DRAFT)
            assertEquals(FinalizeMatchGlobalError.AUTHENTICATION_REQUIRED, (finalize(repository, identity)(FinalizeMatchInput(MATCH_ID, rows())) as FinalizeMatchResult.Invalid).globalError)
            assertEquals(0, repository.finalizeCalls)
        }
        listOf(OWNER_B, null).forEach { owner ->
            val repository = RecordingRepository(owner, MatchStatus.DRAFT)
            assertEquals(FinalizeMatchGlobalError.MATCH_NOT_FOUND, (finalize(repository, auth(OWNER_A))(FinalizeMatchInput(MATCH_ID, rows())) as FinalizeMatchResult.Invalid).globalError)
            assertEquals(0, repository.finalizeCalls)
        }
    }

    @Test fun finalizePreservesDraftAndValidationAndUsesOwnerEvidenceApi() = runTest {
        val finalized = RecordingRepository(OWNER_A, MatchStatus.FINALIZED)
        assertEquals(FinalizeMatchGlobalError.MATCH_NOT_DRAFT, (finalize(finalized, auth(OWNER_A))(FinalizeMatchInput(MATCH_ID, rows())) as FinalizeMatchResult.Invalid).globalError)
        val invalid = RecordingRepository(OWNER_A, MatchStatus.DRAFT)
        assertTrue(finalize(invalid, auth(OWNER_A))(FinalizeMatchInput(MATCH_ID, rows().drop(1))) is FinalizeMatchResult.Invalid)
        assertEquals(0, invalid.finalizeCalls)
        val evidence = evidence()
        val ocr = RecordingRepository(OWNER_A, MatchStatus.DRAFT)
        finalize(ocr, auth(OWNER_A))(FinalizeMatchInput(MATCH_ID, rows(), evidence))
        assertEquals(1, ocr.ocrFinalizeCalls)
        assertEquals(evidence, ocr.evidence)
    }

    @Test fun ocrFinalizationUsesOwnedTournamentMatchSlotsAndWarningGate() = runTest {
        val repository = RecordingRepository(OWNER_A, MatchStatus.DRAFT)
        val useCase = FinalizeOcrCorrectionMatchUseCase(repository, finalize(repository, auth(OWNER_A)), auth(OWNER_A))
        val warning = ocrRows(warning = true)
        assertTrue(useCase(FinalizeOcrCorrectionMatchInput(TOURNAMENT_ID, MATCH_ID, warning)) is FinalizeOcrCorrectionMatchResult.ConfirmationRequired)
        assertTrue(useCase(FinalizeOcrCorrectionMatchInput(TOURNAMENT_ID, MATCH_ID, warning, true)) is FinalizeOcrCorrectionMatchResult.Finalized)
        listOf(auth(null), auth(" ")).forEach { identity ->
            val blocked = FinalizeOcrCorrectionMatchUseCase(repository, finalize(repository, auth(OWNER_A)), identity)(FinalizeOcrCorrectionMatchInput(TOURNAMENT_ID, MATCH_ID, ocrRows()))
            assertTrue(blocked is FinalizeOcrCorrectionMatchResult.Blocked)
        }
        listOf(OWNER_B, null).forEach { owner ->
            val blocked = FinalizeOcrCorrectionMatchUseCase(RecordingRepository(owner, MatchStatus.DRAFT), finalize(repository, auth(OWNER_A)), auth(OWNER_A))(FinalizeOcrCorrectionMatchInput(TOURNAMENT_ID, MATCH_ID, ocrRows())) as FinalizeOcrCorrectionMatchResult.Blocked
            assertTrue(FinalizeOcrCorrectionMatchFailure.MISSING_TOURNAMENT in blocked.failures)
        }
    }

    @Test fun startCorrectionUsesOwnedReadAndPreservesFinalizedCheck() = runTest {
        assertTrue(StartMatchCorrectionUseCase(RecordingRepository(OWNER_A, MatchStatus.FINALIZED), auth(OWNER_A))(MATCH_ID) is StartMatchCorrectionResult.Started)
        assertEquals(MatchCorrectionGlobalError.AUTHENTICATION_REQUIRED, (StartMatchCorrectionUseCase(RecordingRepository(OWNER_A, MatchStatus.FINALIZED), auth(null))(MATCH_ID) as StartMatchCorrectionResult.Rejected).error)
        assertEquals(MatchCorrectionGlobalError.MATCH_NOT_FOUND, (StartMatchCorrectionUseCase(RecordingRepository(OWNER_B, MatchStatus.FINALIZED), auth(OWNER_A))(MATCH_ID) as StartMatchCorrectionResult.Rejected).error)
        assertEquals(MatchCorrectionGlobalError.MATCH_NOT_FINALIZED, (StartMatchCorrectionUseCase(RecordingRepository(OWNER_A, MatchStatus.DRAFT), auth(OWNER_A))(MATCH_ID) as StartMatchCorrectionResult.Rejected).error)
    }

    @Test fun submitNeverCallsCloudBeforeOwnershipAndRecordsOwnedSuccess() = runTest {
        listOf(auth(null), auth(" "), auth(OWNER_A)).zip(listOf(OWNER_A, OWNER_A, OWNER_B)).forEach { (identity, owner) ->
            val repository = RecordingRepository(owner, MatchStatus.FINALIZED)
            val calls = intArrayOf(0)
            val result = submit(repository, identity) { calls[0]++; ProtectedMatchCorrectionResult.Success(3) }(SubmitMatchCorrectionInput(MATCH_ID, rows())) as SubmitMatchCorrectionResult.Invalid
            assertTrue(result.globalError in setOf(MatchCorrectionGlobalError.AUTHENTICATION_REQUIRED, MatchCorrectionGlobalError.MATCH_NOT_FOUND))
            assertEquals(0, calls[0]); assertEquals(0, repository.submitCalls); assertEquals(0, repository.confirmCalls)
        }
        val repository = RecordingRepository(OWNER_A, MatchStatus.FINALIZED)
        var protectedCalls = 0
        assertTrue(submit(repository, auth(OWNER_A)) { protectedCalls++; ProtectedMatchCorrectionResult.Success(3) }(SubmitMatchCorrectionInput(MATCH_ID, rows())) is SubmitMatchCorrectionResult.Submitted)
        assertEquals(1, protectedCalls); assertEquals(1, repository.submitCalls); assertEquals(1, repository.confirmCalls)
    }

    private fun finalize(repository: TournamentRepository, auth: AuthRepository) = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase(), auth)
    private fun submit(repository: TournamentRepository, auth: AuthRepository, protected: ProtectedMatchCorrectionAction) = SubmitMatchCorrectionUseCase(repository, ValidateMatchResultUseCase(), auth, protected)
    private fun auth(id: String?) = object : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(id?.let { AuthState.SignedIn(AuthUser(it, "x@test")) } ?: AuthState.SignedOut)
        override suspend fun restoreSession() = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String) = error("unused") as AuthOperationResult
        override suspend fun login(email: String, password: String) = error("unused") as AuthOperationResult
        override suspend fun logout() = AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
    }
    private fun rows() = (1..12).map { MatchResultRowInput(it, it.toString(), "0") }
    private fun ocrRows(warning: Boolean = false) = (0 until 12).map { index -> FinalizeOcrCorrectionRowInput(index, (index + 1).toString(), "0", (index + 1).toString(), if (warning && index == 0) setOf(FinalizeOcrCorrectionMatchWarning.PLACEMENT_CHANGED_FROM_OCR) else emptySet()) }
    private fun evidence() = PreservedMatchOcrEvidence(TOURNAMENT_ID, MATCH_ID, null, 1L, "test", emptyList(), emptyList())

    private class RecordingRepository(private val owner: String?, status: MatchStatus) : TournamentRepository {
        private val tournament = Tournament(TOURNAMENT_ID, "Cup", LocalDate.of(2026, 1, 1), "Org", "1", TournamentStatus.CONFIRMED, owner)
        private val match = Match(MATCH_ID, TOURNAMENT_ID, 1, tournament.date, "Map", status, participantResults = if (status == MatchStatus.FINALIZED) (1..12).map { MatchParticipantResult(it, MatchParticipationStatus.PARTICIPATED, it, 0) } else emptyList())
        var finalizeCalls = 0; var ocrFinalizeCalls = 0; var submitCalls = 0; var confirmCalls = 0; var evidence: PreservedMatchOcrEvidence? = null
        override suspend fun create(tournament: Tournament) = Unit
        override fun observeAll() = flowOf(listOf(tournament))
        override fun observeById(tournamentId: String): Flow<Tournament?> = error("unscoped tournament")
        override fun observeByIdAndOwner(tournamentId: String, ownerUserId: String) = flowOf(tournament.takeIf { tournamentId == TOURNAMENT_ID && ownerUserId == owner })
        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> = error("unscoped slots")
        override fun observeSlotsByTournamentIdAndOwner(tournamentId: String, ownerUserId: String) = flowOf(if (ownerUserId == owner) (1..12).map { TeamSlot(TOURNAMENT_ID, it, "T$it") } else emptyList())
        override suspend fun saveTeamNames(tournamentId: String, teamNamesBySlotNumber: Map<Int, String>) = Unit
        override fun observeRosterByTournamentAndSlot(tournamentId: String, slotNumber: Int) = flowOf(emptyList<RosterPlayer>())
        override suspend fun saveRoster(tournamentId: String, slotNumber: Int, players: List<RosterPlayer>) = Unit
        override suspend fun confirmTournament(tournamentId: String) = false
        override fun observeMatchById(matchId: String): Flow<Match?> = error("unscoped match")
        override fun observeMatchByIdAndOwner(matchId: String, ownerUserId: String) = flowOf(match.takeIf { matchId == MATCH_ID && ownerUserId == owner })
        override suspend fun finalizeDraftMatch(matchId: String, placements: List<MatchPlacement>, kills: List<MatchKill>, participantResults: List<MatchParticipantResult>?) = error("unscoped finalize") as FinalizeMatchRepositoryResult
        override suspend fun finalizeDraftMatchByOwner(matchId: String, ownerUserId: String, placements: List<MatchPlacement>, kills: List<MatchKill>, participantResults: List<MatchParticipantResult>?): FinalizeMatchRepositoryResult { if (ownerUserId != owner) return FinalizeMatchRepositoryResult.Rejected(FinalizeMatchFailure.MATCH_NOT_FOUND); finalizeCalls++; return FinalizeMatchRepositoryResult.Finalized(match.copy(status = MatchStatus.FINALIZED)) }
        override suspend fun finalizeDraftMatchWithOcrEvidence(matchId: String, placements: List<MatchPlacement>, kills: List<MatchKill>, participantResults: List<MatchParticipantResult>?, evidence: PreservedMatchOcrEvidence) = error("unscoped OCR finalize") as FinalizeMatchRepositoryResult
        override suspend fun finalizeDraftMatchWithOcrEvidenceByOwner(tournamentId: String, matchId: String, ownerUserId: String, placements: List<MatchPlacement>, kills: List<MatchKill>, participantResults: List<MatchParticipantResult>?, evidence: PreservedMatchOcrEvidence): FinalizeMatchRepositoryResult { ocrFinalizeCalls++; this.evidence = evidence; return FinalizeMatchRepositoryResult.Finalized(match.copy(status = MatchStatus.FINALIZED)) }
        override suspend fun readLocalRevisionState(tournamentId: String) = LocalRevisionState(2, CloudRevision(2))
        override suspend fun submitMatchCorrectionByOwner(matchId: String, ownerUserId: String, placements: List<MatchPlacement>, kills: List<MatchKill>, participantResults: List<MatchParticipantResult>?): SubmitMatchCorrectionRepositoryResult { submitCalls++; return SubmitMatchCorrectionRepositoryResult.Submitted(match) }
        override suspend fun confirmCloudRevisionByOwner(tournamentId: String, ownerUserId: String, cloudRevision: Int): OwnerScopedTournamentMutationResult { confirmCalls++; return OwnerScopedTournamentMutationResult.Saved }
    }
    private companion object { const val OWNER_A = "a"; const val OWNER_B = "b"; const val TOURNAMENT_ID = "t"; const val MATCH_ID = "m" }
}
