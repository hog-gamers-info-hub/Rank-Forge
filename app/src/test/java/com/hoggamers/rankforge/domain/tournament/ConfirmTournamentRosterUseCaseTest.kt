package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthUser
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository

class ConfirmTournamentRosterUseCaseTest {
    @Test
    fun invalidRosterCannotConfirm() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament())
        val useCase = ConfirmTournamentRosterUseCase(
            repository = repository,
            validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
            authRepository = auth("owner-a"),
        )

        val result = useCase("stable-id")

        assertTrue(result is ConfirmTournamentRosterResult.Invalid)
        assertEquals(TournamentStatus.DRAFT, repository.observeById("stable-id").first()?.status)
    }

    @Test
    fun missingTournamentReturnsNotFound() = runTest {
        val repository = InMemoryTournamentRepository()
        val useCase = ConfirmTournamentRosterUseCase(
            repository = repository,
            validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
            authRepository = auth("owner-a"),
        )

        assertEquals(ConfirmTournamentRosterResult.NotFound, useCase("missing-id"))
    }

    @Test
    fun repositoryConfirmationReturningFalseReportsAlreadyConfirmed() = runTest {
        val delegate = InMemoryTournamentRepository()
        delegate.create(tournament())
        delegate.saveTeamNames(
            "stable-id",
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        (1..12).forEach { slotNumber ->
            delegate.saveRoster(
                tournamentId = "stable-id",
                slotNumber = slotNumber,
                players = (0..3).map { playerIndex ->
                    RosterPlayer.create("stable-id", slotNumber, "Player $playerIndex")
                },
            )
        }
        val repository = ConfirmationRejectingRepository(delegate)
        val useCase = ConfirmTournamentRosterUseCase(
            repository = repository,
            validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
            authRepository = auth("owner-a"),
        )

        assertTrue(useCase("stable-id") is ConfirmTournamentRosterResult.AlreadyConfirmed)
        assertEquals(TournamentStatus.DRAFT, delegate.observeById("stable-id").first()?.status)
    }

    @Test
    fun validRosterConfirmsAndRepeatedConfirmationIsAlreadyConfirmed() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament())
        repository.saveTeamNames(
            "stable-id",
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        (1..12).forEach { slotNumber ->
            repository.saveRoster(
                tournamentId = "stable-id",
                slotNumber = slotNumber,
                players = (0..3).map { playerIndex ->
                    RosterPlayer.create("stable-id", slotNumber, "Player $playerIndex")
                },
            )
        }
        val useCase = ConfirmTournamentRosterUseCase(
            repository = repository,
            validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
            authRepository = auth("owner-a"),
        )

        assertTrue(useCase("stable-id") is ConfirmTournamentRosterResult.Confirmed)
        assertTrue(useCase("stable-id") is ConfirmTournamentRosterResult.AlreadyConfirmed)
        assertEquals(TournamentStatus.CONFIRMED, repository.observeById("stable-id").first()?.status)
    }

    private fun tournament() = Tournament(
        id = "stable-id",
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        ownerUserId = "owner-a",
    )

    private fun auth(userId: String): AuthRepository = object : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(
            AuthState.SignedIn(AuthUser(userId, "$userId@example.test")),
        )

        override suspend fun restoreSession() = AuthRestorationResult.NoSavedSession

        override suspend fun signUp(email: String, password: String) = failure()

        override suspend fun login(email: String, password: String) = failure()

        override suspend fun logout() = failure()

        private fun failure() = AuthOperationResult.Failure(
            AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure),
        )
    }

    private class ConfirmationRejectingRepository(
        private val delegate: InMemoryTournamentRepository,
    ) : TournamentRepository {
        override suspend fun create(tournament: Tournament) = delegate.create(tournament)

        override fun observeAll(): Flow<List<Tournament>> = delegate.observeAll()

        override fun observeById(tournamentId: String): Flow<Tournament?> = delegate.observeById(tournamentId)

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> =
            delegate.observeSlotsByTournamentId(tournamentId)

        override suspend fun saveTeamNames(
            tournamentId: String,
            teamNamesBySlotNumber: Map<Int, String>,
        ) = delegate.saveTeamNames(tournamentId, teamNamesBySlotNumber)

        override fun observeRosterByTournamentAndSlot(
            tournamentId: String,
            slotNumber: Int,
        ): Flow<List<RosterPlayer>> = delegate.observeRosterByTournamentAndSlot(tournamentId, slotNumber)

        override suspend fun saveRoster(
            tournamentId: String,
            slotNumber: Int,
            players: List<RosterPlayer>,
        ) = delegate.saveRoster(tournamentId, slotNumber, players)

        override suspend fun confirmTournament(tournamentId: String): Boolean = false
    }
}
