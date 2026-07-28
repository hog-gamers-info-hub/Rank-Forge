package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TournamentCloudUploadViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun successWithNoQueueRequirementMapsToSuccessAndPassesTournamentId() = runTest {
        val action = RecordingAction(
            QueueAwareActionResult(
                TournamentCloudUploadResult.Success,
                QueueRecordingResult.NOT_REQUIRED,
            ),
        )
        val viewModel = TournamentCloudUploadViewModel(action)

        viewModel.upload(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(TournamentCloudUploadUiState.Success, viewModel.uiState.value)
        assertEquals(TOURNAMENT_ID, action.tournamentId)
    }

    @Test
    fun recordedFailureMapsToQueued() = runTest {
        val viewModel = TournamentCloudUploadViewModel(
            RecordingAction(
                QueueAwareActionResult(
                    TournamentCloudUploadResult.NetworkFailure,
                    QueueRecordingResult.RECORDED,
                ),
            ),
        )

        viewModel.upload(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(
            TournamentCloudUploadUiState.Queued,
            viewModel.uiState.value,
        )
    }

    @Test
    fun queuePersistenceFailureMapsToLocalSaveFailure() = runTest {
        val viewModel = TournamentCloudUploadViewModel(
            RecordingAction(
                QueueAwareActionResult(
                    TournamentCloudUploadResult.NetworkFailure,
                    QueueRecordingResult.PERSISTENCE_FAILED,
                ),
            ),
        )

        viewModel.upload(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(TournamentCloudUploadUiState.QueuePersistenceFailure, viewModel.uiState.value)
    }

    @Test
    fun failedPrimaryResultWithoutQueueRequirementKeepsPrimaryFailureState() = runTest {
        val viewModel = TournamentCloudUploadViewModel(
            RecordingAction(
                QueueAwareActionResult(
                    TournamentCloudUploadResult.AuthenticationRequired,
                    QueueRecordingResult.NOT_REQUIRED,
                ),
            ),
        )

        viewModel.upload(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(TournamentCloudUploadUiState.AuthenticationRequired, viewModel.uiState.value)
    }

    @Test
    fun unexpectedExceptionMapsToNetworkFailureWithoutClaimingQueued() = runTest {
        val viewModel = TournamentCloudUploadViewModel(ThrowingAction())

        viewModel.upload(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(TournamentCloudUploadUiState.NetworkFailure, viewModel.uiState.value)
    }

    @Test
    fun resetReturnsToIdle() = runTest {
        val viewModel = TournamentCloudUploadViewModel(
            RecordingAction(
                QueueAwareActionResult(
                    TournamentCloudUploadResult.PartialFailure(TournamentCloudUploadStage.TOURNAMENT),
                    QueueRecordingResult.NOT_REQUIRED,
                ),
            ),
        )

        viewModel.upload(TOURNAMENT_ID)
        advanceUntilIdle()
        viewModel.reset()
        assertEquals(TournamentCloudUploadUiState.Idle, viewModel.uiState.value)
    }

    private class RecordingAction(
        private val result: QueueAwareActionResult<TournamentCloudUploadResult>,
    ) : TournamentCloudUploadAction {
        var tournamentId: String? = null

        override suspend fun invoke(tournamentId: String): QueueAwareActionResult<TournamentCloudUploadResult> {
            this.tournamentId = tournamentId
            return result
        }
    }

    private class ThrowingAction : TournamentCloudUploadAction {
        override suspend fun invoke(tournamentId: String): QueueAwareActionResult<TournamentCloudUploadResult> =
            throw IllegalStateException()
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
