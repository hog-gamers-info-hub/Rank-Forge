package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoverPendingLocalDeletionCleanupUseCaseTest {
    @Test
    fun ownerPendingIntentUsesOwnerScopedDeletionAndClear() = runTest {
        val intents = RecordingIntents().apply { pending += intent("match-a", "owner-a") }
        val local = RecordingLocal()
        val useCase = RecoverPendingLocalDeletionCleanupUseCase(
            authRepository = FakeAuth("owner-a"),
            deletionIntentRepository = intents,
            localDeletionRepository = local,
        )

        useCase("owner-a")

        assertEquals(listOf("owner-a"), intents.pendingQueries)
        assertEquals(listOf("match-a:owner-a"), local.matchCalls)
        assertEquals(listOf("MATCH:match-a:owner-a"), intents.clears)
    }

    @Test
    fun notFoundAlsoClearsButRecoverableFailurePreservesIntent() = runTest {
        val intents = RecordingIntents().apply {
            pending += intent("match-a", "owner-a")
            pending += intent("tournament-a", "owner-a", DeletionTargetType.TOURNAMENT)
        }
        val local = RecordingLocal().apply {
            matchResult = LocalDeletionResult.NotFound
            tournamentResult = LocalDeletionResult.FileCleanupFailed
        }

        RecoverPendingLocalDeletionCleanupUseCase(FakeAuth("owner-a"), intents, local)("owner-a")

        assertEquals(listOf("MATCH:match-a:owner-a"), intents.clears)
        assertEquals(listOf("tournament-a:owner-a"), local.tournamentCalls)
    }

    @Test
    fun signedOutBlankAndDifferentOwnerDoNotQueryPendingCleanup() = runTest {
        listOf<AuthState>(
            AuthState.SignedOut,
            AuthState.SignedIn(AuthUser("", "blank@example.test")),
            AuthState.SignedIn(AuthUser("owner-b", "b@example.test")),
        ).forEach { state ->
            val intents = RecordingIntents()
            RecoverPendingLocalDeletionCleanupUseCase(FakeAuth(state), intents, RecordingLocal())("owner-a")
            assertTrue(intents.pendingQueries.isEmpty())
        }
    }

    @Test
    fun ownerSwitchDuringRecoveryStopsRemainingItemsAndDoesNotClearAfterTheSwitch() = runTest {
        val auth = FakeAuth("owner-a")
        val intents = RecordingIntents().apply {
            pending += intent("match-a", "owner-a")
            pending += intent("match-b", "owner-a")
        }
        val local = RecordingLocal().apply {
            afterMatch = { auth.authState.value = AuthState.SignedIn(AuthUser("owner-b", "b@example.test")) }
        }

        RecoverPendingLocalDeletionCleanupUseCase(auth, intents, local)("owner-a")

        assertEquals(listOf("match-a:owner-a"), local.matchCalls)
        assertTrue(intents.clears.isEmpty())
    }

    @Test(expected = CancellationException::class)
    fun cancellationFromLocalCleanupPropagates() = runTest {
        val intents = RecordingIntents().apply { pending += intent("match-a", "owner-a") }
        val local = RecordingLocal().apply { matchCancellation = true }

        RecoverPendingLocalDeletionCleanupUseCase(FakeAuth("owner-a"), intents, local)("owner-a")
    }

    private fun intent(
        targetId: String,
        owner: String,
        type: DeletionTargetType = DeletionTargetType.MATCH,
    ) = DeletionIntent(
        targetType = type,
        targetId = targetId,
        tournamentId = "tournament-a",
        ownerUserId = owner,
        phase = DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING,
        updatedAtEpochMillis = 1,
    )

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

    private class RecordingIntents : DeletionIntentRepository {
        val pending = mutableListOf<DeletionIntent>()
        val pendingQueries = mutableListOf<String>()
        val clears = mutableListOf<String>()
        override suspend fun findByTargetAndOwner(
            targetType: DeletionTargetType,
            targetId: String,
            ownerUserId: String,
        ): DeletionIntent? = pending.firstOrNull {
            it.targetType == targetType && it.targetId == targetId && it.ownerUserId == ownerUserId
        }

        override suspend fun startIfAbsent(intent: DeletionIntent): Boolean = false
        override suspend fun markRemoteDeletedByTargetAndOwner(
            targetType: DeletionTargetType,
            targetId: String,
            ownerUserId: String,
        ): Boolean = false

        override suspend fun clearByTargetAndOwner(
            targetType: DeletionTargetType,
            targetId: String,
            ownerUserId: String,
        ): Boolean {
            clears += "$targetType:$targetId:$ownerUserId"
            return pending.removeIf {
                it.targetType == targetType && it.targetId == targetId && it.ownerUserId == ownerUserId
            }
        }

        override suspend fun isBlockingByTournamentIdAndOwner(tournamentId: String, ownerUserId: String) = false
        override suspend fun readPendingLocalCleanupByOwner(ownerUserId: String): List<DeletionIntent> {
            pendingQueries += ownerUserId
            return pending.filter { it.ownerUserId == ownerUserId }.toList()
        }
    }

    private class RecordingLocal : LocalDeletionRepository {
        val matchCalls = mutableListOf<String>()
        val tournamentCalls = mutableListOf<String>()
        var matchResult: LocalDeletionResult = LocalDeletionResult.Deleted
        var tournamentResult: LocalDeletionResult = LocalDeletionResult.Deleted
        var matchCancellation = false
        var afterMatch: (() -> Unit)? = null
        override suspend fun deleteMatchLocallyByOwner(matchId: String, ownerUserId: String): LocalDeletionResult {
            if (matchCancellation) throw CancellationException("cancel")
            matchCalls += "$matchId:$ownerUserId"
            afterMatch?.invoke()
            return matchResult
        }

        override suspend fun deleteTournamentLocallyByOwner(
            tournamentId: String,
            ownerUserId: String,
        ): LocalDeletionResult {
            tournamentCalls += "$tournamentId:$ownerUserId"
            return tournamentResult
        }
    }
}
