package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerScopedMatchDraftMutationUseCasesTest {
    @Test
    fun createMatchUsesOnlyOwnerScopedReadsAndMutation() = runTest {
        val repository = RecordingRepository(ownerUserId = OWNER_A)

        val result = CreateMatchUseCase(repository, auth(OWNER_A))(
            CreateMatchInput(TOURNAMENT_A, " 3 ", DATE, "  Bermuda  "),
        )

        val created = result as CreateMatchResult.Created
        assertEquals(3, created.match.matchNumber)
        assertEquals("Bermuda", created.match.mapName)
        assertEquals(OWNER_A, repository.createOwnerUserId)
    }

    @Test
    fun createMatchRejectsSignedOutBlankForeignAndNullOwnersWithoutMutation() = runTest {
        listOf(auth(null), auth("  ")).forEach { unauthenticated ->
            val repository = RecordingRepository(ownerUserId = OWNER_A)
            assertEquals(
                CreateMatchResult.AuthenticationRequired,
                CreateMatchUseCase(repository, unauthenticated)(validCreateInput()),
            )
            assertNull(repository.createOwnerUserId)
        }
        listOf(OWNER_B, null).forEach { targetOwner ->
            val repository = RecordingRepository(ownerUserId = targetOwner)
            val result = CreateMatchUseCase(repository, auth(OWNER_A))(validCreateInput())
            assertEquals(
                MatchValidationError.TOURNAMENT_NOT_FOUND,
                (result as CreateMatchResult.Invalid).errors[MatchField.TOURNAMENT],
            )
            assertNull(repository.createOwnerUserId)
        }
    }

    @Test
    fun createNextMatchUsesOwnerScopedTournamentChildrenAndPreservesNumber() = runTest {
        val repository = RecordingRepository(ownerUserId = OWNER_A, matches = listOf(match(number = 2)))

        val result = CreateNextMatchUseCase(repository, auth(OWNER_A))(TOURNAMENT_A)

        assertEquals(3, (result as CreateNextMatchResult.Created).match.matchNumber)
        assertEquals(OWNER_A, repository.createOwnerUserId)
    }

    @Test
    fun createNextMatchRejectsUnauthenticatedForeignAndNullOwners() = runTest {
        listOf(auth(null), auth(" ")).forEach { unauthenticated ->
            assertEquals(
                CreateNextMatchResult.Rejected(CreateNextMatchFailure.AUTHENTICATION_REQUIRED),
                CreateNextMatchUseCase(RecordingRepository(OWNER_A), unauthenticated)(TOURNAMENT_A),
            )
        }
        listOf(OWNER_B, null).forEach { targetOwner ->
            assertEquals(
                CreateNextMatchResult.Rejected(CreateNextMatchFailure.TOURNAMENT_NOT_FOUND),
                CreateNextMatchUseCase(RecordingRepository(targetOwner), auth(OWNER_A))(TOURNAMENT_A),
            )
        }
    }

    @Test
    fun placementsAndKillsRequireOwnedDraftMatchAndPreserveValidation() = runTest {
        val placementsRepository = RecordingRepository(ownerUserId = OWNER_A)
        val placementResult = SaveMatchPlacementsUseCase(placementsRepository, auth(OWNER_A))(
            SaveMatchPlacementsInput(MATCH_A, mapOf(1 to "1")),
        )
        assertTrue(placementResult is SaveMatchPlacementsResult.Saved)
        assertEquals(OWNER_A, placementsRepository.placementsOwnerUserId)

        val killsRepository = RecordingRepository(ownerUserId = OWNER_A)
        val killResult = SaveMatchKillsUseCase(killsRepository, auth(OWNER_A))(
            SaveMatchKillsInput(MATCH_A, mapOf(1 to "2")),
        )
        assertTrue(killResult is SaveMatchKillsResult.Saved)
        assertEquals(OWNER_A, killsRepository.killsOwnerUserId)

        val invalid = SaveMatchPlacementsUseCase(RecordingRepository(OWNER_A), auth(OWNER_A))(
            SaveMatchPlacementsInput(MATCH_A, mapOf(1 to "x")),
        ) as SaveMatchPlacementsResult.Invalid
        assertEquals(PlacementValidationError.INVALID, invalid.errorsByTeamSlot[1])
    }

    @Test
    fun placementsAndKillsRejectUnauthenticatedForeignAndNullOwnersWithoutMutation() = runTest {
        listOf(auth(null), auth(" ")).forEach { unauthenticated ->
            assertEquals(
                PlacementGlobalError.AUTHENTICATION_REQUIRED,
                (SaveMatchPlacementsUseCase(RecordingRepository(OWNER_A), unauthenticated)(
                    SaveMatchPlacementsInput(MATCH_A, emptyMap()),
                ) as SaveMatchPlacementsResult.Invalid).globalError,
            )
            assertEquals(
                KillGlobalError.AUTHENTICATION_REQUIRED,
                (SaveMatchKillsUseCase(RecordingRepository(OWNER_A), unauthenticated)(
                    SaveMatchKillsInput(MATCH_A, emptyMap()),
                ) as SaveMatchKillsResult.Invalid).globalError,
            )
        }
        listOf(OWNER_B, null).forEach { targetOwner ->
            val repository = RecordingRepository(targetOwner)
            assertEquals(
                PlacementGlobalError.MATCH_NOT_FOUND,
                (SaveMatchPlacementsUseCase(repository, auth(OWNER_A))(
                    SaveMatchPlacementsInput(MATCH_A, emptyMap()),
                ) as SaveMatchPlacementsResult.Invalid).globalError,
            )
            assertNull(repository.placementsOwnerUserId)
            assertEquals(
                KillGlobalError.MATCH_NOT_FOUND,
                (SaveMatchKillsUseCase(repository, auth(OWNER_A))(
                    SaveMatchKillsInput(MATCH_A, emptyMap()),
                ) as SaveMatchKillsResult.Invalid).globalError,
            )
            assertNull(repository.killsOwnerUserId)
        }
    }

    @Test
    fun draftValueAndClearsRequireMatchingOwnedTournamentAndMatch() = runTest {
        val repository = RecordingRepository(OWNER_A)
        assertEquals(
            SaveMatchDraftValueResult.Saved,
            SaveMatchDraftValueUseCase(repository, auth(OWNER_A))(
                SaveMatchDraftValueInput(TOURNAMENT_A, MATCH_A, 1, placementInput = "1"),
            ),
        )
        assertEquals(OWNER_A, repository.draftValueOwnerUserId)
        assertEquals(
            ClearDraftMatchResult.Cleared,
            ClearDraftMatchUseCase(repository, auth(OWNER_A))(ClearDraftMatchInput(TOURNAMENT_A, MATCH_A)),
        )
        assertEquals(OWNER_A, repository.clearDraftOwnerUserId)
        assertEquals(
            ClearMatchCorrectionDraftResult.Cleared,
            ClearMatchCorrectionDraftUseCase(repository, auth(OWNER_A))(
                ClearMatchCorrectionDraftInput(TOURNAMENT_A, MATCH_A),
            ),
        )
        assertEquals(OWNER_A, repository.clearCorrectionOwnerUserId)
    }

    @Test
    fun dualIdDraftMutationsRejectUnauthenticatedForeignNullAndMismatchedTargets() = runTest {
        val commands: suspend (RecordingRepository, AuthRepository, String, String) -> Any = { repository, auth, tournamentId, matchId ->
            SaveMatchDraftValueUseCase(repository, auth)(
                SaveMatchDraftValueInput(tournamentId, matchId, 1, placementInput = "1"),
            )
        }
        listOf(auth(null), auth(" ")).forEach { unauthenticated ->
            assertEquals(
                SaveMatchDraftValueResult.AuthenticationRequired,
                commands(RecordingRepository(OWNER_A), unauthenticated, TOURNAMENT_A, MATCH_A),
            )
        }
        listOf(RecordingRepository(OWNER_B), RecordingRepository(null)).forEach { repository ->
            assertEquals(
                SaveMatchDraftValueResult.MatchNotFound,
                commands(repository, auth(OWNER_A), TOURNAMENT_A, MATCH_A),
            )
            assertNull(repository.draftValueOwnerUserId)
        }
        val mismatched = RecordingRepository(OWNER_A)
        assertEquals(
            SaveMatchDraftValueResult.MatchNotFound,
            commands(mismatched, auth(OWNER_A), TOURNAMENT_B, MATCH_A),
        )
        assertNull(mismatched.draftValueOwnerUserId)

        val clearCommands = listOf<suspend (RecordingRepository, AuthRepository, String, String) -> Any>(
            { repository, auth, tournamentId, matchId ->
                ClearDraftMatchUseCase(repository, auth)(ClearDraftMatchInput(tournamentId, matchId))
            },
            { repository, auth, tournamentId, matchId ->
                ClearMatchCorrectionDraftUseCase(repository, auth)(
                    ClearMatchCorrectionDraftInput(tournamentId, matchId),
                )
            },
        )
        clearCommands.forEach { command ->
            listOf(auth(null), auth(" ")).forEach { unauthenticated ->
                assertTrue(command(RecordingRepository(OWNER_A), unauthenticated, TOURNAMENT_A, MATCH_A) in setOf(
                    ClearDraftMatchResult.AuthenticationRequired,
                    ClearMatchCorrectionDraftResult.AuthenticationRequired,
                ))
            }
            listOf(OWNER_B, null).forEach { targetOwner ->
                assertTrue(command(RecordingRepository(targetOwner), auth(OWNER_A), TOURNAMENT_A, MATCH_A) in setOf(
                    ClearDraftMatchResult.MatchNotFound,
                    ClearMatchCorrectionDraftResult.MatchNotFound,
                ))
            }
            assertTrue(command(RecordingRepository(OWNER_A), auth(OWNER_A), TOURNAMENT_B, MATCH_A) in setOf(
                ClearDraftMatchResult.MatchNotFound,
                ClearMatchCorrectionDraftResult.MatchNotFound,
            ))
        }
    }

    private fun validCreateInput() = CreateMatchInput(TOURNAMENT_A, "1", DATE, "Map")

    private fun auth(userId: String?): AuthRepository = object : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(
            userId?.let { AuthState.SignedIn(AuthUser(it, "$it@example.test")) } ?: AuthState.SignedOut,
        )
        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String): AuthOperationResult = failure()
        override suspend fun login(email: String, password: String): AuthOperationResult = failure()
        override suspend fun logout(): AuthOperationResult = AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
    }

    private fun failure() = AuthOperationResult.Failure(AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure))

    private class RecordingRepository(
        private val ownerUserId: String?,
        private val matches: List<Match> = listOf(match()),
    ) : TournamentRepository {
        var createOwnerUserId: String? = null
        var placementsOwnerUserId: String? = null
        var killsOwnerUserId: String? = null
        var draftValueOwnerUserId: String? = null
        var clearDraftOwnerUserId: String? = null
        var clearCorrectionOwnerUserId: String? = null

        override suspend fun create(tournament: Tournament) = Unit
        override fun observeAll(): Flow<List<Tournament>> = flowOf(emptyList())
        override fun observeById(tournamentId: String): Flow<Tournament?> = error("Unscoped tournament read must not be used")
        override fun observeByIdAndOwner(tournamentId: String, ownerUserId: String): Flow<Tournament?> =
            flowOf(tournamentId.takeIf { it == TOURNAMENT_A && ownerUserId == this.ownerUserId }?.let(::tournament))
        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> =
            error("Unscoped slot read must not be used")
        override fun observeSlotsByTournamentIdAndOwner(tournamentId: String, ownerUserId: String): Flow<List<TeamSlot>> =
            flowOf(if (tournamentId == TOURNAMENT_A && ownerUserId == this.ownerUserId) listOf(TeamSlot(TOURNAMENT_A, 1, "Alpha")) else emptyList())
        override suspend fun saveTeamNames(tournamentId: String, teamNamesBySlotNumber: Map<Int, String>) = Unit
        override fun observeRosterByTournamentAndSlot(tournamentId: String, slotNumber: Int): Flow<List<RosterPlayer>> = flowOf(emptyList())
        override suspend fun saveRoster(tournamentId: String, slotNumber: Int, players: List<RosterPlayer>) = Unit
        override suspend fun confirmTournament(tournamentId: String): Boolean = false
        override fun observeMatchesByTournamentId(tournamentId: String): Flow<List<Match>> =
            error("Unscoped match list read must not be used")
        override fun observeMatchesByTournamentIdAndOwner(tournamentId: String, ownerUserId: String): Flow<List<Match>> =
            flowOf(if (tournamentId == TOURNAMENT_A && ownerUserId == this.ownerUserId) matches else emptyList())
        override fun observeMatchById(matchId: String): Flow<Match?> = error("Unscoped match read must not be used")
        override fun observeMatchByIdAndOwner(matchId: String, ownerUserId: String): Flow<Match?> =
            flowOf(matches.singleOrNull { it.id == matchId }?.takeIf { ownerUserId == this.ownerUserId })
        override suspend fun createDraftMatch(match: Match): CreateMatchRepositoryResult = error("Unscoped create must not be used")
        override suspend fun createDraftMatchByOwner(match: Match, ownerUserId: String): CreateMatchRepositoryResult =
            if (match.tournamentId != TOURNAMENT_A || ownerUserId != this.ownerUserId) {
                CreateMatchRepositoryResult.Rejected(MatchCreationFailure.TOURNAMENT_NOT_FOUND)
            } else {
                createOwnerUserId = ownerUserId
                CreateMatchRepositoryResult.Created
            }
        override suspend fun saveDraftMatchPlacements(matchId: String, placements: List<MatchPlacement>): SaveMatchPlacementsRepositoryResult = error("Unscoped placement mutation must not be used")
        override suspend fun saveDraftMatchPlacementsByOwner(matchId: String, ownerUserId: String, placements: List<MatchPlacement>): SaveMatchPlacementsRepositoryResult =
            if (matches.none { it.id == matchId } || ownerUserId != this.ownerUserId) SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.MATCH_NOT_FOUND) else {
                placementsOwnerUserId = ownerUserId; SaveMatchPlacementsRepositoryResult.Saved
            }
        override suspend fun saveDraftMatchKills(matchId: String, kills: List<MatchKill>): SaveMatchKillsRepositoryResult = error("Unscoped kill mutation must not be used")
        override suspend fun saveDraftMatchKillsByOwner(matchId: String, ownerUserId: String, kills: List<MatchKill>): SaveMatchKillsRepositoryResult =
            if (matches.none { it.id == matchId } || ownerUserId != this.ownerUserId) SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.MATCH_NOT_FOUND) else {
                killsOwnerUserId = ownerUserId; SaveMatchKillsRepositoryResult.Saved
            }
        override suspend fun saveDraftMatchValueByOwner(tournamentId: String, matchId: String, ownerUserId: String, teamSlotNumber: Int, placementInput: String?, killsInput: String?): OwnerScopedMatchMutationResult =
            dualResult(tournamentId, matchId, ownerUserId) { draftValueOwnerUserId = it }
        override suspend fun clearDraftMatchByOwner(tournamentId: String, matchId: String, ownerUserId: String): OwnerScopedMatchMutationResult =
            dualResult(tournamentId, matchId, ownerUserId) { clearDraftOwnerUserId = it }
        override suspend fun clearMatchCorrectionDraftByOwner(tournamentId: String, matchId: String, ownerUserId: String): OwnerScopedMatchMutationResult =
            dualResult(tournamentId, matchId, ownerUserId) { clearCorrectionOwnerUserId = it }
        private fun dualResult(tournamentId: String, matchId: String, ownerUserId: String, record: (String) -> Unit): OwnerScopedMatchMutationResult =
            if (tournamentId != TOURNAMENT_A || matches.none { it.id == matchId } || ownerUserId != this.ownerUserId) OwnerScopedMatchMutationResult.MatchNotFound else {
                record(ownerUserId); OwnerScopedMatchMutationResult.Saved
            }
    }

    private companion object {
        const val OWNER_A = "user-a"
        const val OWNER_B = "user-b"
        const val TOURNAMENT_A = "tournament-a"
        const val TOURNAMENT_B = "tournament-b"
        const val MATCH_A = "match-a"
        val DATE: LocalDate = LocalDate.of(2026, 8, 23)
        fun tournament(id: String = TOURNAMENT_A) = Tournament(
            id,
            "Tournament",
            DATE,
            "Organizer",
            "123",
            TournamentStatus.DRAFT,
            OWNER_A,
        )
        fun match(number: Int = 1) = Match(MATCH_A, TOURNAMENT_A, number, DATE, "Map", MatchStatus.DRAFT)
    }
}
