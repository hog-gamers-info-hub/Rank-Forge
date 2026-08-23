package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import java.time.LocalDate
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcileLegacyTournamentOwnershipUseCaseTest {
    @Test
    fun exactSameIdCloudOwnerAssignsOnlyThatLegacyTournament() = runTest {
        val repository = FakeTournamentRepository(ownerless = listOf(legacyTournament("tournament-a")))
        val cloud = FakeCloudRepository().apply { responses["tournament-a"] = success("tournament-a", OWNER_A) }

        useCase(FakeAuth(OWNER_A), repository, cloud)(OWNER_A)

        assertEquals(listOf("tournament-a:$OWNER_A"), repository.assignmentCalls)
    }

    @Test
    fun missingCloudTournamentDoesNotAssign() = runTest {
        assertNoAssignmentFor(TournamentCloudRestorationRemoteResult.Failure(TournamentCloudRestorationFailureCategory.NOT_FOUND))
    }

    @Test
    fun cloudNetworkFailureDoesNotAssign() = runTest {
        assertNoAssignmentFor(TournamentCloudRestorationRemoteResult.Failure(TournamentCloudRestorationFailureCategory.NETWORK))
    }

    @Test
    fun cloudAuthenticationFailureDoesNotAssign() = runTest {
        assertNoAssignmentFor(TournamentCloudRestorationRemoteResult.Failure(TournamentCloudRestorationFailureCategory.AUTHENTICATION))
    }

    @Test
    fun blankCloudOwnerDoesNotAssign() = runTest {
        val repository = FakeTournamentRepository(ownerless = listOf(legacyTournament("tournament-a")))
        val cloud = FakeCloudRepository().apply { responses["tournament-a"] = success("tournament-a", "") }

        useCase(FakeAuth(OWNER_A), repository, cloud)(OWNER_A)

        assertTrue(repository.assignmentCalls.isEmpty())
    }

    @Test
    fun cloudIdMismatchDoesNotAssign() = runTest {
        val repository = FakeTournamentRepository(ownerless = listOf(legacyTournament("tournament-a")))
        val cloud = FakeCloudRepository().apply { responses["tournament-a"] = success("other-id", OWNER_A) }

        useCase(FakeAuth(OWNER_A), repository, cloud)(OWNER_A)

        assertTrue(repository.assignmentCalls.isEmpty())
    }

    @Test
    fun cloudOwnerMismatchDoesNotAssign() = runTest {
        val repository = FakeTournamentRepository(ownerless = listOf(legacyTournament("tournament-a")))
        val cloud = FakeCloudRepository().apply { responses["tournament-a"] = success("tournament-a", OWNER_B) }

        useCase(FakeAuth(OWNER_A), repository, cloud)(OWNER_A)

        assertTrue(repository.assignmentCalls.isEmpty())
    }

    @Test
    fun onlyOwnerlessLocalTournamentsAreEnumerated() = runTest {
        val legacy = legacyTournament("legacy")
        val repository = FakeTournamentRepository(
            allTournaments = listOf(legacy, legacyTournament("owned-a", OWNER_A), legacyTournament("owned-b", OWNER_B)),
        )
        val cloud = FakeCloudRepository().apply { responses["legacy"] = success("legacy", OWNER_A) }

        useCase(FakeAuth(OWNER_A), repository, cloud)(OWNER_A)

        assertEquals(listOf("legacy"), cloud.readCalls)
        assertEquals(listOf("legacy:$OWNER_A"), repository.assignmentCalls)
    }

    @Test
    fun signedOutDoesNotEnumerateOrAssign() = runTest {
        val repository = FakeTournamentRepository(ownerless = listOf(legacyTournament("tournament-a")))
        val cloud = FakeCloudRepository().apply { responses["tournament-a"] = success("tournament-a", OWNER_A) }

        useCase(FakeAuth(AuthState.SignedOut), repository, cloud)(OWNER_A)

        assertTrue(cloud.readCalls.isEmpty())
        assertTrue(repository.assignmentCalls.isEmpty())
    }

    @Test
    fun blankSignedInIdDoesNotEnumerateOrAssign() = runTest {
        val repository = FakeTournamentRepository(ownerless = listOf(legacyTournament("tournament-a")))
        val cloud = FakeCloudRepository().apply { responses["tournament-a"] = success("tournament-a", OWNER_A) }

        useCase(FakeAuth(AuthState.SignedIn(AuthUser("", "blank@example.test"))), repository, cloud)(OWNER_A)

        assertTrue(cloud.readCalls.isEmpty())
        assertTrue(repository.assignmentCalls.isEmpty())
    }

    @Test
    fun authSwitchDuringCloudFetchDoesNotAssignUsingEitherOwner() = runTest {
        val auth = FakeAuth(OWNER_A)
        val repository = FakeTournamentRepository(ownerless = listOf(legacyTournament("tournament-a")))
        val cloud = FakeCloudRepository().apply {
            responses["tournament-a"] = success("tournament-a", OWNER_A)
            beforeReturn = { auth.authState.value = AuthState.SignedIn(AuthUser(OWNER_B, "b@example.test")) }
        }

        useCase(auth, repository, cloud)(OWNER_A)

        assertTrue(repository.assignmentCalls.isEmpty())
    }

    @Test(expected = CancellationException::class)
    fun cloudCancellationPropagates() = runTest {
        val repository = FakeTournamentRepository(ownerless = listOf(legacyTournament("tournament-a")))
        val cloud = FakeCloudRepository().apply { cancellation = true }

        useCase(FakeAuth(OWNER_A), repository, cloud)(OWNER_A)
    }

    @Test
    fun multipleLegacyRowsAssignOnlyExactPositiveProofs() = runTest {
        val repository = FakeTournamentRepository(ownerless = listOf(legacyTournament("one"), legacyTournament("two"), legacyTournament("three")))
        val cloud = FakeCloudRepository().apply {
            responses["one"] = success("one", OWNER_A)
            responses["two"] = success("two", OWNER_B)
            responses["three"] = TournamentCloudRestorationRemoteResult.Failure(TournamentCloudRestorationFailureCategory.NOT_FOUND)
        }

        useCase(FakeAuth(OWNER_A), repository, cloud)(OWNER_A)

        assertEquals(listOf("one:$OWNER_A"), repository.assignmentCalls)
    }

    @Test
    fun ordinaryCloudExceptionLeavesThatRowUnassignedAndContinuesToTheNext() = runTest {
        val repository = FakeTournamentRepository(ownerless = listOf(legacyTournament("one"), legacyTournament("two")))
        val cloud = FakeCloudRepository().apply {
            throwForIds += "one"
            responses["two"] = success("two", OWNER_A)
        }

        useCase(FakeAuth(OWNER_A), repository, cloud)(OWNER_A)

        assertEquals(listOf("two:$OWNER_A"), repository.assignmentCalls)
    }

    @Test
    fun repositoryConditionalRejectionDoesNotRetryOrOverwrite() = runTest {
        val repository = FakeTournamentRepository(ownerless = listOf(legacyTournament("tournament-a"))).apply {
            assignmentResult = LegacyTournamentOwnerAssignmentResult.NotUnassigned
        }
        val cloud = FakeCloudRepository().apply { responses["tournament-a"] = success("tournament-a", OWNER_A) }

        useCase(FakeAuth(OWNER_A), repository, cloud)(OWNER_A)

        assertEquals(listOf("tournament-a:$OWNER_A"), repository.assignmentCalls)
    }

    private suspend fun assertNoAssignmentFor(
        result: TournamentCloudRestorationRemoteResult<TournamentCloudRestorationSnapshot>,
    ) {
        val repository = FakeTournamentRepository(ownerless = listOf(legacyTournament("tournament-a")))
        val cloud = FakeCloudRepository().apply { responses["tournament-a"] = result }

        useCase(FakeAuth(OWNER_A), repository, cloud)(OWNER_A)

        assertTrue(repository.assignmentCalls.isEmpty())
    }

    private fun useCase(
        auth: AuthRepository,
        repository: TournamentRepository,
        cloud: TournamentCloudRestorationRepository,
    ) = ReconcileLegacyTournamentOwnershipUseCase(auth, repository, cloud)

    private fun success(id: String, owner: String) = TournamentCloudRestorationRemoteResult.Success(
        TournamentCloudRestorationSnapshot(
            tournament = legacyTournament(id, owner),
            slots = emptyList(),
            players = emptyList(),
        ),
    )

    private fun legacyTournament(id: String, owner: String? = null) = Tournament(
        id = id,
        name = "Tournament $id",
        date = LocalDate.of(2026, 1, 1),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        ownerUserId = owner,
    )

    private class FakeTournamentRepository(
        ownerless: List<Tournament> = emptyList(),
        allTournaments: List<Tournament> = ownerless,
    ) : TournamentRepository {
        private val tournaments = allTournaments.ifEmpty { ownerless }
        val assignmentCalls = mutableListOf<String>()
        var assignmentResult: LegacyTournamentOwnerAssignmentResult = LegacyTournamentOwnerAssignmentResult.Assigned

        override suspend fun create(tournament: Tournament) = Unit
        override fun observeAll(): Flow<List<Tournament>> = flowOf(tournaments)
        override fun observeById(tournamentId: String): Flow<Tournament?> = flowOf(tournaments.firstOrNull { it.id == tournamentId })
        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> = flowOf(emptyList())
        override suspend fun saveTeamNames(tournamentId: String, teamNamesBySlotNumber: Map<Int, String>) = Unit
        override fun observeRosterByTournamentAndSlot(tournamentId: String, slotNumber: Int): Flow<List<RosterPlayer>> = flowOf(emptyList())
        override suspend fun saveRoster(tournamentId: String, slotNumber: Int, players: List<RosterPlayer>) = Unit
        override suspend fun confirmTournament(tournamentId: String) = false
        override suspend fun readOwnerlessLegacyTournaments(): List<Tournament> = tournaments.filter { it.ownerUserId == null }
        override suspend fun assignLegacyTournamentOwnerIfUnassigned(
            tournamentId: String,
            provenOwnerUserId: String,
        ): LegacyTournamentOwnerAssignmentResult {
            assignmentCalls += "$tournamentId:$provenOwnerUserId"
            return assignmentResult
        }
    }

    private class FakeCloudRepository : TournamentCloudRestorationRepository {
        val responses = mutableMapOf<String, TournamentCloudRestorationRemoteResult<TournamentCloudRestorationSnapshot>>()
        val readCalls = mutableListOf<String>()
        var beforeReturn: (() -> Unit)? = null
        var cancellation = false
        val throwForIds = mutableSetOf<String>()
        override suspend fun listOwnedTournaments() = TournamentCloudRestorationRemoteResult.Success(emptyList<TournamentCloudRestorationSummary>())
        override suspend fun readOwnedTournament(tournamentId: String): TournamentCloudRestorationRemoteResult<TournamentCloudRestorationSnapshot> {
            readCalls += tournamentId
            if (cancellation) throw CancellationException("cancel")
            if (tournamentId in throwForIds) throw IllegalStateException("network unavailable")
            beforeReturn?.invoke()
            return checkNotNull(responses[tournamentId])
        }
    }

    private class FakeAuth(state: AuthState) : AuthRepository {
        constructor(ownerId: String) : this(AuthState.SignedIn(AuthUser(ownerId, "$ownerId@example.test")))
        val authState = MutableStateFlow(state)
        override fun observeAuthState(): Flow<AuthState> = authState
        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String): AuthOperationResult = failure()
        override suspend fun login(email: String, password: String): AuthOperationResult = failure()
        override suspend fun logout(): AuthOperationResult = failure()
        private fun failure() = AuthOperationResult.Failure(AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure))
    }

    private companion object {
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
    }
}
