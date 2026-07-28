package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FinalizedMatchCloudSyncViewModelTest {
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
    fun mapsSyncStates() = runTest {
        val cases = listOf(
            FinalizedMatchCloudSyncResult.Success to FinalizedMatchCloudSyncUiState.Success,
            FinalizedMatchCloudSyncResult.AuthenticationRequired to FinalizedMatchCloudSyncUiState.AuthenticationRequired,
            FinalizedMatchCloudSyncResult.AuthorizationFailure to FinalizedMatchCloudSyncUiState.AuthorizationFailure,
            FinalizedMatchCloudSyncResult.ValidationFailure to FinalizedMatchCloudSyncUiState.ValidationFailure,
            FinalizedMatchCloudSyncResult.NetworkFailure to FinalizedMatchCloudSyncUiState.NetworkFailure,
        )

        cases.forEach { (result, state) ->
            val action = RecordingAction(result)
            val viewModel = FinalizedMatchCloudSyncViewModel(action)
            viewModel.sync(TOURNAMENT_ID)
            advanceUntilIdle()
            assertEquals(state, viewModel.uiState.value)
            assertEquals(TOURNAMENT_ID, action.tournamentId)
        }
    }

    @Test
    fun exposesPartialFailureForManualRetry() = runTest {
        val viewModel = FinalizedMatchCloudSyncViewModel(
            RecordingAction(FinalizedMatchCloudSyncResult.PartialFailure(FinalizedMatchCloudSyncStage.MATCHES)),
        )

        viewModel.sync(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(
            FinalizedMatchCloudSyncUiState.PartialFailure(FinalizedMatchCloudSyncStage.MATCHES),
            viewModel.uiState.value,
        )
        viewModel.reset()
        assertEquals(FinalizedMatchCloudSyncUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun queueOutcomesMapToDistinctStatesAndThrownExceptionMapsToNetworkFailure() = runTest {
        assertEquals(
            FinalizedMatchCloudSyncUiState.Queued,
            stateFor(FinalizedMatchCloudSyncResult.NetworkFailure, QueueRecordingResult.RECORDED),
        )
        assertEquals(
            FinalizedMatchCloudSyncUiState.QueuePersistenceFailure,
            stateFor(FinalizedMatchCloudSyncResult.NetworkFailure, QueueRecordingResult.PERSISTENCE_FAILED),
        )
        assertEquals(
            FinalizedMatchCloudSyncUiState.AuthenticationRequired,
            stateFor(FinalizedMatchCloudSyncResult.AuthenticationRequired, QueueRecordingResult.NOT_REQUIRED),
        )

        val viewModel = FinalizedMatchCloudSyncViewModel(
            RecordingAction(FinalizedMatchCloudSyncResult.NetworkFailure, throwOnInvoke = true),
        )
        viewModel.sync(TOURNAMENT_ID)
        advanceUntilIdle()
        assertEquals(FinalizedMatchCloudSyncUiState.NetworkFailure, viewModel.uiState.value)
    }

    private suspend fun TestScope.stateFor(
        result: FinalizedMatchCloudSyncResult,
        queueRecordingResult: QueueRecordingResult,
    ): FinalizedMatchCloudSyncUiState {
        val viewModel = FinalizedMatchCloudSyncViewModel(RecordingAction(result, queueRecordingResult))
        viewModel.sync(TOURNAMENT_ID)
        advanceUntilIdle()
        return viewModel.uiState.value
    }

    private class RecordingAction(
        private val result: FinalizedMatchCloudSyncResult,
        private val queueRecordingResult: QueueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
        private val throwOnInvoke: Boolean = false,
    ) : FinalizedMatchCloudSyncAction {
        var tournamentId: String? = null

        override suspend fun invoke(
            tournamentId: String,
        ): QueueAwareActionResult<FinalizedMatchCloudSyncResult> {
            this.tournamentId = tournamentId
            if (throwOnInvoke) throw IllegalStateException()
            return QueueAwareActionResult(result, queueRecordingResult)
        }
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
