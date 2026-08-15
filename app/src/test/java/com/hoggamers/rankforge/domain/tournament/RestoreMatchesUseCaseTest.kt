package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import java.time.LocalDate
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreMatchesUseCaseTest {
    @Test
    fun noCloudMatchesPreservesResultWithoutQueueEntry() = runTest {
        val queue = RecordingTestQueueRepository()
        val result = RestoreMatchesUseCase(
            FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null))),
            FakeCloudRepository(
                MatchCloudRestorationRemoteResult.Success(
                    MatchCloudRestorationSnapshot(
                        TOURNAMENT_ID,
                        emptyList(),
                        com.hoggamers.rankforge.domain.sync.CloudRevision(1),
                    ),
                ),
            ),
            RecordingLocalRepository(),
            queue.recorder(),
        )(TOURNAMENT_ID)

        assertEquals(MatchCloudRestorationResult.NoCloudMatches, result.primaryResult)
        assertEquals(QueueRecordingResult.NOT_REQUIRED, result.queueRecordingResult)
        assertTrue(queue.entries.isEmpty())
    }

    @Test
    fun authenticationFailureIsRecordedWithQueueMetadata() = runTest {
        val queue = RecordingTestQueueRepository()
        val result = RestoreMatchesUseCase(
            FakeAuthRepository(AuthState.SignedOut),
            FakeCloudRepository(MatchCloudRestorationRemoteResult.Failure(MatchCloudRestorationFailureCategory.NETWORK)),
            RecordingLocalRepository(),
            queue.recorder(),
        )(TOURNAMENT_ID)

        assertEquals(MatchCloudRestorationResult.AuthenticationRequired, result.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, result.queueRecordingResult)
        assertEquals(SyncQueueOperationType.MATCH_RESTORATION, queue.entries.single().operationType)
        assertEquals(TOURNAMENT_ID, queue.entries.single().tournamentId)
        assertEquals(SyncQueueStatus.BLOCKED_AUTHENTICATION, queue.entries.single().status)
        assertEquals(0, queue.entries.single().attemptCount)
    }

    @Test
    fun networkFailureIsRecordedAndQueuePersistenceFailureIsExposed() = runTest {
        val auth = FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null)))
        val networkFailure = MatchCloudRestorationRemoteResult.Failure(
            MatchCloudRestorationFailureCategory.NETWORK,
        )
        val queue = RecordingTestQueueRepository()
        val networkResult = RestoreMatchesUseCase(
            auth,
            FakeCloudRepository(networkFailure),
            RecordingLocalRepository(),
            queue.recorder(),
        )(TOURNAMENT_ID)
        assertEquals(MatchCloudRestorationResult.NetworkFailure, networkResult.primaryResult)
        assertEquals(QueueRecordingResult.RECORDED, networkResult.queueRecordingResult)
        assertEquals(SyncQueueStatus.BLOCKED_NETWORK, queue.entries.single().status)

        val persistenceFailure = RestoreMatchesUseCase(
            auth,
            FakeCloudRepository(networkFailure),
            RecordingLocalRepository(),
            RecordingTestQueueRepository(IllegalStateException()).recorder(),
        )(TOURNAMENT_ID)
        assertEquals(MatchCloudRestorationResult.NetworkFailure, persistenceFailure.primaryResult)
        assertEquals(QueueRecordingResult.PERSISTENCE_FAILED, persistenceFailure.queueRecordingResult)
    }

    @Test
    fun screenshotFailureRunsAfterParentAndLeavesParentPersisted() = runTest {
        val events = mutableListOf<String>()
        val local = RecordingLocalRepository(events)
        val screenshotAction = RecordingScreenshotRestorationAction(
            events = events,
            result = MatchCloudRestorationResult.NetworkFailure,
        )
        val result = RestoreMatchesUseCase(
            FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null))),
            FakeCloudRepository(MatchCloudRestorationRemoteResult.Success(snapshotWithMatch())),
            local,
            RecordingTestQueueRepository().recorder(),
            screenshotAction,
        )(TOURNAMENT_ID)

        assertEquals(MatchCloudRestorationResult.NetworkFailure, result.primaryResult)
        assertEquals(listOf("parent", "screenshots"), events)
        assertTrue(local.restoreCalled)
        assertEquals(listOf(MATCH_ID), screenshotAction.matchIds)
    }

    @Test
    fun restorationPersistsCloudOcrEvidenceWithTheParentMatchBeforeChildRecovery() = runTest {
        val local = RecordingLocalRepository()
        val evidence = preservedEvidence()
        val result = RestoreMatchesUseCase(
            FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null))),
            FakeCloudRepository(
                MatchCloudRestorationRemoteResult.Success(
                    snapshotWithMatch().copy(ocrEvidence = listOf(evidence)),
                ),
            ),
            local,
            RecordingTestQueueRepository().recorder(),
        ).executeForRetry(TOURNAMENT_ID)

        assertEquals(MatchCloudRestorationResult.Success, result)
        assertEquals(listOf(evidence), local.restoredSnapshot?.ocrEvidence)
        assertEquals(listOf(MATCH_ID), local.restoredSnapshot?.matches?.map { it.id })
    }

    @Test
    fun restorationWithoutCloudOcrEvidenceSucceedsWithoutCreatingEvidence() = runTest {
        val local = RecordingLocalRepository()
        val result = RestoreMatchesUseCase(
            FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null))),
            FakeCloudRepository(MatchCloudRestorationRemoteResult.Success(snapshotWithMatch())),
            local,
            RecordingTestQueueRepository().recorder(),
        ).executeForRetry(TOURNAMENT_ID)

        assertEquals(MatchCloudRestorationResult.Success, result)
        assertTrue(local.restoredSnapshot?.ocrEvidence.orEmpty().isEmpty())
    }

    @Test
    fun screenshotCancellationPropagatesAfterParentPersistence() = runTest {
        val local = RecordingLocalRepository()
        val cancellation = CancellationException("cancelled")
        var propagated = false
        try {
            RestoreMatchesUseCase(
                FakeAuthRepository(AuthState.SignedIn(AuthUser(OWNER_ID, null))),
                FakeCloudRepository(MatchCloudRestorationRemoteResult.Success(snapshotWithMatch())),
                local,
                RecordingTestQueueRepository().recorder(),
                RecordingScreenshotRestorationAction(result = cancellation),
            ).executeForRetry(TOURNAMENT_ID)
        } catch (error: CancellationException) {
            propagated = error === cancellation
        }

        assertTrue(propagated)
        assertTrue(local.restoreCalled)
    }

    private fun snapshotWithMatch() = MatchCloudRestorationSnapshot(
        tournamentId = TOURNAMENT_ID,
        matches = listOf(
            Match(
                id = MATCH_ID,
                tournamentId = TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 8, 15),
                mapName = "",
                status = MatchStatus.DRAFT,
            ),
        ),
        cloudRevision = com.hoggamers.rankforge.domain.sync.CloudRevision(1),
    )

    private fun preservedEvidence() = PreservedMatchOcrEvidence(
        tournamentId = TOURNAMENT_ID,
        matchId = MATCH_ID,
        sourceScreenshotId = "MATCH_RESULT_UPPER",
        preservedAt = 1784896496000,
        provenance = "OCR_REVIEW_FINALIZATION",
        rows = listOf(
            PreservedMatchOcrRowEvidence(
                rowIndex = 1,
                originalOcrText = "Alpha",
                originalPlacement = 2,
                originalKills = 4,
                originalSuggestedTeamSlot = 3,
                confidenceSummary = "HIGH",
                safetySummary = "SAFE",
                manualReviewRequired = true,
            ),
        ),
        correctionSnapshots = listOf(
            PreservedMatchOcrCorrectionSnapshot(
                rowIndex = 1,
                correctedPlacement = 2,
                correctedKills = 4,
                correctedTeamSlot = 3,
                placementChanged = true,
                killsChanged = false,
                teamSlotChanged = true,
            ),
        ),
    )

    private class FakeCloudRepository(
        private val result: MatchCloudRestorationRemoteResult<MatchCloudRestorationSnapshot>,
    ) : MatchCloudRestorationRepository {
        override suspend fun readOwnedMatches(
            tournamentId: String,
        ): MatchCloudRestorationRemoteResult<MatchCloudRestorationSnapshot> = result
    }

    private class RecordingLocalRepository(
        private val events: MutableList<String> = mutableListOf(),
    ) : MatchRestorationLocalRepository {
        var restoreCalled = false
        var restoredSnapshot: MatchCloudRestorationSnapshot? = null
        override suspend fun replaceMatches(snapshot: MatchCloudRestorationSnapshot) {
            events += "parent"
            restoreCalled = true
            restoredSnapshot = snapshot
        }
    }

    private class RecordingScreenshotRestorationAction(
        private val events: MutableList<String> = mutableListOf(),
        private val result: Any = MatchCloudRestorationResult.Success,
    ) : MatchScreenshotRestorationAction {
        var matchIds: List<String> = emptyList()
        override suspend fun invoke(tournamentId: String, restoredMatchIds: Set<String>): MatchCloudRestorationResult {
            events += "screenshots"
            matchIds = restoredMatchIds.toList()
            if (result is Throwable) throw result
            return result as MatchCloudRestorationResult
        }
    }

    private class FakeAuthRepository(
        private val state: AuthState,
    ) : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(state)
        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated)
        override suspend fun login(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)
        override suspend fun logout(): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        const val OWNER_ID = "22222222-2222-2222-222222222222"
        const val MATCH_ID = "33333333-3333-3333-333333333333"
    }
}
