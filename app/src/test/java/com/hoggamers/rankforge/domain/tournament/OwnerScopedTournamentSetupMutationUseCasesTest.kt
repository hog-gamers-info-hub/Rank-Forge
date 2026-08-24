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
import org.junit.Assert.fail
import org.junit.Test

class OwnerScopedTournamentSetupMutationUseCasesTest {
    @Test
    fun saveTeamNamesUsesAuthenticatedOwnerAndTrimsNames() = runTest {
        val repository = RecordingRepository(ownerUserId = "user-a")

        val result = SaveTeamSlotNamesUseCase(repository, auth("user-a"))(
            tournamentId = TOURNAMENT_ID,
            teamNamesBySlotNumber = mapOf(1 to "  Alpha  "),
        )

        assertEquals(SaveTeamSlotNamesResult.Saved, result)
        assertEquals("user-a", repository.teamNamesOwnerUserId)
        assertEquals(mapOf(1 to "Alpha"), repository.teamNames)
    }

    @Test
    fun saveTeamNamesRejectsSignedOutAndForeignOwnersWithoutMutation() = runTest {
        val signedOutRepository = RecordingRepository(ownerUserId = "user-a")
        assertEquals(
            SaveTeamSlotNamesResult.AuthenticationRequired,
            SaveTeamSlotNamesUseCase(signedOutRepository, auth(null))(TOURNAMENT_ID, emptyMap()),
        )
        assertNull(signedOutRepository.teamNamesOwnerUserId)

        val foreignRepository = RecordingRepository(ownerUserId = "user-b")
        assertEquals(
            SaveTeamSlotNamesResult.TournamentNotFound,
            SaveTeamSlotNamesUseCase(foreignRepository, auth("user-a"))(TOURNAMENT_ID, emptyMap()),
        )
        assertNull(foreignRepository.teamNamesOwnerUserId)
    }

    @Test
    fun saveTeamNamesRejectsBlankAndNullOwnersWithoutMutation() = runTest {
        val blankAuthRepository = RecordingRepository(ownerUserId = "user-a")
        assertEquals(
            SaveTeamSlotNamesResult.AuthenticationRequired,
            SaveTeamSlotNamesUseCase(blankAuthRepository, auth("   "))(TOURNAMENT_ID, emptyMap()),
        )
        assertNull(blankAuthRepository.teamNamesOwnerUserId)

        val legacyRepository = RecordingRepository(ownerUserId = null)
        assertEquals(
            SaveTeamSlotNamesResult.TournamentNotFound,
            SaveTeamSlotNamesUseCase(legacyRepository, auth("user-a"))(TOURNAMENT_ID, emptyMap()),
        )
        assertNull(legacyRepository.teamNamesOwnerUserId)
    }

    @Test
    fun saveRosterUsesOwnerScopedMutationAndRejectsBlankAuthId() = runTest {
        val repository = RecordingRepository(ownerUserId = "user-a")
        val players = listOf(RosterPlayer.create(TOURNAMENT_ID, 1, "Player"))

        assertEquals(
            SaveRosterResult.Saved,
            SaveRosterUseCase(repository, auth("user-a"))(TOURNAMENT_ID, 1, players),
        )
        assertEquals("user-a", repository.rosterOwnerUserId)
        assertEquals(players, repository.rosterPlayers)

        val blankAuthRepository = RecordingRepository(ownerUserId = "user-a")
        assertEquals(
            SaveRosterResult.AuthenticationRequired,
            SaveRosterUseCase(blankAuthRepository, auth("   "))(TOURNAMENT_ID, 1, players),
        )
        assertNull(blankAuthRepository.rosterOwnerUserId)
    }

    @Test
    fun saveRosterRejectsSignedOutForeignAndNullOwnersWithoutMutation() = runTest {
        val players = listOf(RosterPlayer.create(TOURNAMENT_ID, 1, "Player"))
        val signedOutRepository = RecordingRepository(ownerUserId = "user-a")
        assertEquals(
            SaveRosterResult.AuthenticationRequired,
            SaveRosterUseCase(signedOutRepository, auth(null))(TOURNAMENT_ID, 1, players),
        )
        assertNull(signedOutRepository.rosterOwnerUserId)

        val foreignRepository = RecordingRepository(ownerUserId = "user-b")
        assertEquals(
            SaveRosterResult.TournamentNotFound,
            SaveRosterUseCase(foreignRepository, auth("user-a"))(TOURNAMENT_ID, 1, players),
        )
        assertNull(foreignRepository.rosterOwnerUserId)

        val legacyRepository = RecordingRepository(ownerUserId = null)
        assertEquals(
            SaveRosterResult.TournamentNotFound,
            SaveRosterUseCase(legacyRepository, auth("user-a"))(TOURNAMENT_ID, 1, players),
        )
        assertNull(legacyRepository.rosterOwnerUserId)
    }

    @Test
    fun saveRosterRetainsStructuralValidationBeforeMutation() = runTest {
        val repository = RecordingRepository(ownerUserId = "user-a")
        try {
            SaveRosterUseCase(repository, auth("user-a"))(
                TOURNAMENT_ID,
                1,
                List(RosterPlayer.MAX_PLAYERS + 1) { RosterPlayer.create(TOURNAMENT_ID, 1, "Player $it") },
            )
            fail("Expected structural validation to reject more than six players")
        } catch (_: IllegalArgumentException) {
            assertNull(repository.rosterOwnerUserId)
        }
    }

    @Test
    fun confirmRejectsForeignTournamentBeforeValidationOrMutation() = runTest {
        val repository = RecordingRepository(ownerUserId = "user-b")
        val validation = ValidateTournamentRosterUseCase(
            ObserveTournamentSlotsUseCase(repository, auth("user-a")),
            ObserveRosterPlayersUseCase(repository, auth("user-a")),
            RosterValidator(),
        )

        assertEquals(
            ConfirmTournamentRosterResult.NotFound,
            ConfirmTournamentRosterUseCase(repository, validation, auth("user-a"))(TOURNAMENT_ID),
        )
        assertNull(repository.confirmOwnerUserId)
    }

    @Test
    fun confirmRejectsMissingAuthenticationAndNullOwnerWithoutMutation() = runTest {
        val signedOutRepository = RecordingRepository(ownerUserId = "user-a", roster = RosterFixture.Valid)
        val signedOutValidation = validation(signedOutRepository, auth(null))
        assertEquals(
            ConfirmTournamentRosterResult.AuthenticationRequired,
            ConfirmTournamentRosterUseCase(signedOutRepository, signedOutValidation, auth(null))(TOURNAMENT_ID),
        )
        assertNull(signedOutRepository.confirmOwnerUserId)

        val blankRepository = RecordingRepository(ownerUserId = "user-a", roster = RosterFixture.Valid)
        val blankValidation = validation(blankRepository, auth(" "))
        assertEquals(
            ConfirmTournamentRosterResult.AuthenticationRequired,
            ConfirmTournamentRosterUseCase(blankRepository, blankValidation, auth(" "))(TOURNAMENT_ID),
        )
        assertNull(blankRepository.confirmOwnerUserId)

        val legacyRepository = RecordingRepository(ownerUserId = null, roster = RosterFixture.Valid)
        val legacyValidation = validation(legacyRepository, auth("user-a"))
        assertEquals(
            ConfirmTournamentRosterResult.NotFound,
            ConfirmTournamentRosterUseCase(legacyRepository, legacyValidation, auth("user-a"))(TOURNAMENT_ID),
        )
        assertNull(legacyRepository.confirmOwnerUserId)
    }

    @Test
    fun confirmRetainsConfirmedAndInvalidSemanticsThroughOwnerScopedPaths() = runTest {
        val validRepository = RecordingRepository(ownerUserId = "user-a", roster = RosterFixture.Valid)
        val validAuth = auth("user-a")
        assertEquals(
            ConfirmTournamentRosterResult.Confirmed::class,
            ConfirmTournamentRosterUseCase(validRepository, validation(validRepository, validAuth), validAuth)(TOURNAMENT_ID)::class,
        )
        assertEquals("user-a", validRepository.confirmOwnerUserId)

        val confirmedRepository = RecordingRepository(
            ownerUserId = "user-a",
            status = TournamentStatus.CONFIRMED,
            roster = RosterFixture.Valid,
        )
        assertEquals(
            ConfirmTournamentRosterResult.AlreadyConfirmed::class,
            ConfirmTournamentRosterUseCase(
                confirmedRepository,
                validation(confirmedRepository, validAuth),
                validAuth,
            )(TOURNAMENT_ID)::class,
        )
        assertNull(confirmedRepository.confirmOwnerUserId)

        val invalidRepository = RecordingRepository(ownerUserId = "user-a", roster = RosterFixture.Invalid)
        assertEquals(
            ConfirmTournamentRosterResult.Invalid::class,
            ConfirmTournamentRosterUseCase(
                invalidRepository,
                validation(invalidRepository, validAuth),
                validAuth,
            )(TOURNAMENT_ID)::class,
        )
        assertNull(invalidRepository.confirmOwnerUserId)
    }

    @Test
    fun productionValidationUsesOwnerScopedObserversRatherThanRawRepositoryReads() = runTest {
        val repository = RecordingRepository(ownerUserId = "user-a")
        val result = ValidateTournamentRosterUseCase(
            ObserveTournamentSlotsUseCase(repository, auth("user-a")),
            ObserveRosterPlayersUseCase(repository, auth("user-a")),
            RosterValidator(),
        )(TOURNAMENT_ID)

        assertEquals(emptyList<RosterValidationIssue>(), result.issues)
    }

    @Test
    fun replacementUsesOwnerScopedRepositoryAndRejectsSignedOut() = runTest {
        val candidate = replacementCandidate()
        val repository = RecordingRepository(ownerUserId = "user-a")
        assertEquals(
            ReplaceConfirmedTournamentRosterResult.Replaced,
            ReplaceConfirmedTournamentRosterUseCase(repository, RosterValidator(), auth("user-a"))(candidate),
        )
        assertEquals("user-a", repository.replacementOwnerUserId)

        val signedOutRepository = RecordingRepository(ownerUserId = "user-a")
        assertEquals(
            ReplaceConfirmedTournamentRosterResult.AuthenticationRequired,
            ReplaceConfirmedTournamentRosterUseCase(signedOutRepository, RosterValidator(), auth(null))(candidate),
        )
        assertNull(signedOutRepository.replacementOwnerUserId)
    }

    @Test
    fun replacementRejectsBlankForeignAndNullOwnersAndPreservesRepositoryResults() = runTest {
        val candidate = replacementCandidate()
        val blankRepository = RecordingRepository(ownerUserId = "user-a")
        assertEquals(
            ReplaceConfirmedTournamentRosterResult.AuthenticationRequired,
            ReplaceConfirmedTournamentRosterUseCase(blankRepository, RosterValidator(), auth(" "))(candidate),
        )
        assertNull(blankRepository.replacementOwnerUserId)

        val foreignRepository = RecordingRepository(ownerUserId = "user-b")
        assertEquals(
            ReplaceConfirmedTournamentRosterResult.TournamentNotFound,
            ReplaceConfirmedTournamentRosterUseCase(foreignRepository, RosterValidator(), auth("user-a"))(candidate),
        )
        assertNull(foreignRepository.replacementOwnerUserId)

        val legacyRepository = RecordingRepository(ownerUserId = null)
        assertEquals(
            ReplaceConfirmedTournamentRosterResult.TournamentNotFound,
            ReplaceConfirmedTournamentRosterUseCase(legacyRepository, RosterValidator(), auth("user-a"))(candidate),
        )
        assertNull(legacyRepository.replacementOwnerUserId)

        val blockedRepository = RecordingRepository(
            ownerUserId = "user-a",
            replacementResult = ReplaceConfirmedTournamentRosterRepositoryResult.BlockedByExistingMatches,
        )
        assertEquals(
            ReplaceConfirmedTournamentRosterResult.BlockedByExistingMatches,
            ReplaceConfirmedTournamentRosterUseCase(blockedRepository, RosterValidator(), auth("user-a"))(candidate),
        )
        assertEquals("user-a", blockedRepository.replacementOwnerUserId)

        val invalidRepository = RecordingRepository(ownerUserId = "user-a")
        assertEquals(
            ReplaceConfirmedTournamentRosterResult.InvalidCandidate,
            ReplaceConfirmedTournamentRosterUseCase(invalidRepository, RosterValidator(), auth("user-a"))(
                candidate.copy(rosterPlayersBySlotNumber = candidate.rosterPlayersBySlotNumber - 12),
            ),
        )
        assertNull(invalidRepository.replacementOwnerUserId)
    }

    private fun auth(userId: String?): AuthRepository = object : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(
            userId?.let { AuthState.SignedIn(AuthUser(it, "$it@example.test")) } ?: AuthState.SignedOut,
        )

        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession

        override suspend fun signUp(email: String, password: String): AuthOperationResult = failure()

        override suspend fun login(email: String, password: String): AuthOperationResult = failure()

        override suspend fun logout(): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
    }

    private fun failure() = AuthOperationResult.Failure(
        AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
    )

    private fun replacementCandidate() = ConfirmedRosterReplacementCandidate(
        tournamentId = TOURNAMENT_ID,
        teamNamesBySlotNumber = TeamSlot.SLOT_NUMBERS.associateWith { "Team $it" },
        rosterPlayersBySlotNumber = TeamSlot.SLOT_NUMBERS.associateWith { slotNumber ->
            (1..4).map { playerNumber ->
                RosterPlayer.create(TOURNAMENT_ID, slotNumber, "Player $slotNumber-$playerNumber")
            }
        },
    )

    private fun validation(repository: RecordingRepository, authRepository: AuthRepository) =
        ValidateTournamentRosterUseCase(
            ObserveTournamentSlotsUseCase(repository, authRepository),
            ObserveRosterPlayersUseCase(repository, authRepository),
            RosterValidator(),
        )

    private enum class RosterFixture { Empty, Valid, Invalid }

    private class RecordingRepository(
        private val ownerUserId: String?,
        private val status: TournamentStatus = TournamentStatus.DRAFT,
        private val roster: RosterFixture = RosterFixture.Empty,
        private val replacementResult: ReplaceConfirmedTournamentRosterRepositoryResult =
            ReplaceConfirmedTournamentRosterRepositoryResult.Replaced,
    ) : TournamentRepository {
        var teamNamesOwnerUserId: String? = null
        var teamNames: Map<Int, String>? = null
        var rosterOwnerUserId: String? = null
        var rosterPlayers: List<RosterPlayer>? = null
        var confirmOwnerUserId: String? = null
        var replacementOwnerUserId: String? = null

        override suspend fun create(tournament: Tournament) = Unit

        override fun observeAll(): Flow<List<Tournament>> = flowOf(emptyList())

        override fun observeById(tournamentId: String): Flow<Tournament?> =
            flowOf(tournamentId.takeIf { it == TOURNAMENT_ID }?.let { tournament(ownerUserId, status) })

        override fun observeByIdAndOwner(tournamentId: String, ownerUserId: String): Flow<Tournament?> =
            flowOf(tournamentId.takeIf { it == TOURNAMENT_ID && this.ownerUserId == ownerUserId }
                ?.let { tournament(ownerUserId, status) })

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> =
            error("Unscoped child reads must not be used by production validation")

        override fun observeSlotsByTournamentIdAndOwner(
            tournamentId: String,
            ownerUserId: String,
        ): Flow<List<TeamSlot>> = flowOf(
            if (ownerUserId == this.ownerUserId && roster != RosterFixture.Empty) {
                TeamSlot.SLOT_NUMBERS.map { slotNumber -> TeamSlot(TOURNAMENT_ID, slotNumber, "Team $slotNumber") }
            } else {
                emptyList()
            },
        )

        override suspend fun saveTeamNames(tournamentId: String, teamNamesBySlotNumber: Map<Int, String>) =
            error("Unscoped mutation must not be used")

        override suspend fun saveTeamNamesByOwner(
            tournamentId: String,
            ownerUserId: String,
            teamNamesBySlotNumber: Map<Int, String>,
        ): OwnerScopedTournamentMutationResult {
            if (tournamentId != TOURNAMENT_ID || ownerUserId != this.ownerUserId) {
                return OwnerScopedTournamentMutationResult.TournamentNotFound
            }
            teamNamesOwnerUserId = ownerUserId
            teamNames = teamNamesBySlotNumber
            return OwnerScopedTournamentMutationResult.Saved
        }

        override fun observeRosterByTournamentAndSlot(
            tournamentId: String,
            slotNumber: Int,
        ): Flow<List<RosterPlayer>> = error("Unscoped child reads must not be used by production validation")

        override fun observeRosterByTournamentAndSlotAndOwner(
            tournamentId: String,
            slotNumber: Int,
            ownerUserId: String,
        ): Flow<List<RosterPlayer>> = flowOf(
            if (ownerUserId != this.ownerUserId) {
                emptyList()
            } else when (roster) {
                RosterFixture.Valid -> (1..4).map { playerNumber ->
                    RosterPlayer.create(TOURNAMENT_ID, slotNumber, "Player $slotNumber-$playerNumber")
                }
                RosterFixture.Invalid -> if (slotNumber == 1) emptyList() else (1..4).map { playerNumber ->
                    RosterPlayer.create(TOURNAMENT_ID, slotNumber, "Player $slotNumber-$playerNumber")
                }
                RosterFixture.Empty -> emptyList()
            },
        )

        override suspend fun saveRoster(
            tournamentId: String,
            slotNumber: Int,
            players: List<RosterPlayer>,
        ) = error("Unscoped mutation must not be used")

        override suspend fun saveRosterByOwner(
            tournamentId: String,
            ownerUserId: String,
            slotNumber: Int,
            players: List<RosterPlayer>,
        ): OwnerScopedTournamentMutationResult {
            if (tournamentId != TOURNAMENT_ID || ownerUserId != this.ownerUserId) {
                return OwnerScopedTournamentMutationResult.TournamentNotFound
            }
            rosterOwnerUserId = ownerUserId
            rosterPlayers = players
            return OwnerScopedTournamentMutationResult.Saved
        }

        override suspend fun replaceConfirmedTournamentRoster(
            candidate: ConfirmedRosterReplacementCandidate,
        ): ReplaceConfirmedTournamentRosterRepositoryResult = error("Unscoped mutation must not be used")

        override suspend fun replaceConfirmedTournamentRosterByOwner(
            candidate: ConfirmedRosterReplacementCandidate,
            ownerUserId: String,
        ): ReplaceConfirmedTournamentRosterRepositoryResult {
            if (candidate.tournamentId != TOURNAMENT_ID || ownerUserId != this.ownerUserId) {
                return ReplaceConfirmedTournamentRosterRepositoryResult.TournamentNotFound
            }
            replacementOwnerUserId = ownerUserId
            return replacementResult
        }

        override suspend fun confirmTournament(tournamentId: String): Boolean =
            error("Unscoped mutation must not be used")

        override suspend fun confirmTournamentByOwner(
            tournamentId: String,
            ownerUserId: String,
        ): OwnerScopedTournamentConfirmationResult {
            if (tournamentId != TOURNAMENT_ID || ownerUserId != this.ownerUserId) {
                return OwnerScopedTournamentConfirmationResult.TournamentNotFound
            }
            confirmOwnerUserId = ownerUserId
            return OwnerScopedTournamentConfirmationResult.Confirmed
        }
    }

    private companion object {
        const val TOURNAMENT_ID = "tournament-a"

        fun tournament(ownerUserId: String?, status: TournamentStatus = TournamentStatus.DRAFT) = Tournament(
            id = TOURNAMENT_ID,
            name = "Tournament",
            date = LocalDate.of(2026, 8, 23),
            organizerName = "Organizer",
            organizerContactNumber = "123",
            status = status,
            ownerUserId = ownerUserId,
        )
    }
}
