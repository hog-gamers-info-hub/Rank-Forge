package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryCloudBackupUseCaseTest {
    @Test
    fun successfulRecoveryUploadsRosterBeforeFinalizedMatchesAndPreservesLocalData() = runTest {
        val repository = completeStandardRepository()
        val matchesBefore = repository.observeMatchesByTournamentId(TOURNAMENT_ID).firstValue()
        val slotsBefore = repository.observeSlotsByTournamentId(TOURNAMENT_ID).firstValue()
        val events = mutableListOf<String>()
        val upload = RecordingTournamentUploadAction(repository) {
            events += "tournament"
        }
        val finalized = RecordingFinalizedSyncAction(repository) {
            events += "finalized"
        }

        val result = RetryCloudBackupUseCase(
            repository,
            SignedInAuthRepository(OWNER_ID),
            upload,
            finalized,
        )(TOURNAMENT_ID)

        assertEquals(RetryCloudBackupResult.Success, result)
        assertEquals(listOf("tournament", "finalized"), events)
        assertEquals(2, upload.confirmedRevision)
        assertEquals(2, finalized.expectedRevision)
        assertEquals(matchesBefore, repository.observeMatchesByTournamentId(TOURNAMENT_ID).firstValue())
        assertEquals(slotsBefore, repository.observeSlotsByTournamentId(TOURNAMENT_ID).firstValue())
        assertEquals(3, repository.readLocalRevisionState(TOURNAMENT_ID).expectedCloudRevision)
    }

    @Test
    fun tournamentUploadFailureBlocksFinalizedSynchronization() = runTest {
        val repository = completeStandardRepository()
        val finalized = RecordingFinalizedSyncAction(repository)
        val result = RetryCloudBackupUseCase(
            repository,
            SignedInAuthRepository(OWNER_ID),
            RecordingTournamentUploadAction(
                repository,
                result = QueueAwareActionResult(
                    TournamentCloudUploadResult.NetworkFailure,
                    QueueRecordingResult.RECORDED,
                ),
            ),
            finalized,
        )(TOURNAMENT_ID)

        assertEquals(RetryCloudBackupResult.NetworkFailure, result)
        assertEquals(0, finalized.invocations)
    }

    @Test
    fun missingFinalizedAggregateStopsBeforeAnyCloudAction() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(standardTournament())
        repository.saveTeamNames(TOURNAMENT_ID, completeTeamNames())
        val upload = RecordingTournamentUploadAction(repository)
        val finalized = RecordingFinalizedSyncAction(repository)

        val result = RetryCloudBackupUseCase(
            repository,
            SignedInAuthRepository(OWNER_ID),
            upload,
            finalized,
        )(TOURNAMENT_ID)

        assertEquals(RetryCloudBackupResult.LocalDataIncomplete, result)
        assertEquals(0, upload.invocations)
        assertEquals(0, finalized.invocations)
    }

    @Test
    fun ownerMismatchStopsBeforeAnyCloudAction() = runTest {
        val repository = completeStandardRepository()
        val upload = RecordingTournamentUploadAction(repository)
        val finalized = RecordingFinalizedSyncAction(repository)

        val result = RetryCloudBackupUseCase(
            repository,
            SignedInAuthRepository("another-owner"),
            upload,
            finalized,
        )(TOURNAMENT_ID)

        assertEquals(RetryCloudBackupResult.AuthorizationFailure, result)
        assertEquals(0, upload.invocations)
        assertEquals(0, finalized.invocations)
    }

    @Test
    fun groupRotationStopsBeforeStandardRecoveryActions() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(
            standardTournament().copy(
                format = TournamentFormat.GROUP_ROTATION,
                groupCount = 3,
                selectedGroupPairings = defaultGroupPairings(3),
            ),
        )
        val upload = RecordingTournamentUploadAction(repository)
        val finalized = RecordingFinalizedSyncAction(repository)

        val result = RetryCloudBackupUseCase(
            repository,
            SignedInAuthRepository(OWNER_ID),
            upload,
            finalized,
        )(TOURNAMENT_ID)

        assertEquals(RetryCloudBackupResult.LocalDataIncomplete, result)
        assertEquals(0, upload.invocations)
        assertEquals(0, finalized.invocations)
    }

    @Test
    fun finalizedSynchronizationFailurePreservesConfirmedRosterRevisionAndLocalMatch() = runTest {
        val repository = completeStandardRepository()
        val matchesBefore = repository.observeMatchesByTournamentId(TOURNAMENT_ID).firstValue()
        val upload = RecordingTournamentUploadAction(repository)
        val finalized = RecordingFinalizedSyncAction(
            repository,
            result = QueueAwareActionResult(
                FinalizedMatchCloudSyncResult.NetworkFailure,
                QueueRecordingResult.RECORDED,
            ),
        )

        val result = RetryCloudBackupUseCase(
            repository,
            SignedInAuthRepository(OWNER_ID),
            upload,
            finalized,
        )(TOURNAMENT_ID)

        assertEquals(RetryCloudBackupResult.NetworkFailure, result)
        assertEquals(2, repository.readLocalRevisionState(TOURNAMENT_ID).expectedCloudRevision)
        assertEquals(matchesBefore, repository.observeMatchesByTournamentId(TOURNAMENT_ID).firstValue())
        assertTrue(finalized.invocations == 1)
    }

    private suspend fun completeStandardRepository(): InMemoryTournamentRepository =
        InMemoryTournamentRepository().also { repository ->
            repository.create(standardTournament())
            repository.saveTeamNames(TOURNAMENT_ID, completeTeamNames())
            repository.confirmTournament(TOURNAMENT_ID)
            repository.createDraftMatch(
                Match(
                    id = "finalized-match",
                    tournamentId = TOURNAMENT_ID,
                    matchNumber = 1,
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                    status = MatchStatus.DRAFT,
                ),
            )
            repository.finalizeDraftMatch(
                matchId = "finalized-match",
                placements = TeamSlot.SLOT_NUMBERS.map { MatchPlacement(it, it) },
                kills = TeamSlot.SLOT_NUMBERS.map { MatchKill(it, it - 1) },
            )
        }

    private fun standardTournament() = Tournament(
        id = TOURNAMENT_ID,
        name = "Summer Cup",
        stageName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        ownerUserId = OWNER_ID,
    )

    private fun completeTeamNames() = TeamSlot.SLOT_NUMBERS.associateWith { slot -> "Team $slot" }

    private class RecordingTournamentUploadAction(
        private val repository: InMemoryTournamentRepository,
        private val result: QueueAwareActionResult<TournamentCloudUploadResult> = QueueAwareActionResult(
            TournamentCloudUploadResult.Success(2),
            QueueRecordingResult.NOT_REQUIRED,
        ),
        private val onInvoke: () -> Unit = {},
    ) : TournamentCloudUploadAction {
        var invocations = 0
        var confirmedRevision: Int? = null

        override suspend fun invoke(tournamentId: String): QueueAwareActionResult<TournamentCloudUploadResult> {
            invocations++
            onInvoke()
            val successRevision = (result.primaryResult as? TournamentCloudUploadResult.Success)?.confirmedCloudRevision
            if (successRevision != null) {
                repository.confirmCloudRevision(tournamentId, successRevision)
                confirmedRevision = successRevision
            }
            return result
        }
    }

    private class RecordingFinalizedSyncAction(
        private val repository: InMemoryTournamentRepository,
        private val result: QueueAwareActionResult<FinalizedMatchCloudSyncResult> = QueueAwareActionResult(
            FinalizedMatchCloudSyncResult.Success(3),
            QueueRecordingResult.NOT_REQUIRED,
        ),
        private val onInvoke: () -> Unit = {},
    ) : FinalizedMatchCloudSyncAction {
        var invocations = 0
        var expectedRevision: Int? = null

        override suspend fun invoke(tournamentId: String): QueueAwareActionResult<FinalizedMatchCloudSyncResult> {
            invocations++
            onInvoke()
            expectedRevision = repository.readLocalRevisionState(tournamentId).expectedCloudRevision
            val successRevision = (result.primaryResult as? FinalizedMatchCloudSyncResult.Success)?.confirmedCloudRevision
            if (successRevision != null) repository.confirmCloudRevision(tournamentId, successRevision)
            return result
        }
    }

    private class SignedInAuthRepository(private val ownerId: String) : AuthRepository {
        override fun observeAuthState(): Flow<AuthState> = flowOf(AuthState.SignedIn(AuthUser(ownerId, null)))
        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated)
        override suspend fun login(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)
        override suspend fun logout(): AuthOperationResult =
            AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
    }

    private suspend fun <T> Flow<T>.firstValue(): T = first()

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        const val OWNER_ID = "owner-id"
    }
}
