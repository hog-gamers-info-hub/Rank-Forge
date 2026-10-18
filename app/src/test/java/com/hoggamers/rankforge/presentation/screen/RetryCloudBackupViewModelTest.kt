package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.RetryCloudBackupAction
import com.hoggamers.rankforge.domain.tournament.RetryCloudBackupResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryCloudBackupViewModelTest {
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
    fun duplicateRetryTapDoesNotStartAnotherOperationAndReportsSuccess() = runTest {
        val result = CompletableDeferred<RetryCloudBackupResult>()
        val action = RecordingAction(result)
        val viewModel = RetryCloudBackupViewModel(action)

        viewModel.retry(TOURNAMENT_ID)
        viewModel.retry(TOURNAMENT_ID)
        runCurrent()

        assertEquals(RetryCloudBackupUiState.Loading, viewModel.uiState.value)
        assertEquals(1, action.invocations)
        assertEquals(TOURNAMENT_ID, action.tournamentId)

        result.complete(RetryCloudBackupResult.Success)
        advanceUntilIdle()

        assertEquals(RetryCloudBackupUiState.Success, viewModel.uiState.value)
    }

    @Test
    fun mapsRecoveryFailureToAnAccurateState() = runTest {
        val viewModel = RetryCloudBackupViewModel(
            RecordingAction(CompletableDeferred(RetryCloudBackupResult.RevisionConflict)),
        )

        viewModel.retry(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(RetryCloudBackupUiState.RevisionConflict, viewModel.uiState.value)
    }

    @Test
    fun mapsNetworkFailureToVisibleFailureState() = runTest {
        val viewModel = RetryCloudBackupViewModel(
            RecordingAction(CompletableDeferred(RetryCloudBackupResult.NetworkFailure)),
        )

        viewModel.retry(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(RetryCloudBackupUiState.NetworkFailure, viewModel.uiState.value)
    }

    private class RecordingAction(
        private val result: CompletableDeferred<RetryCloudBackupResult>,
    ) : RetryCloudBackupAction {
        var invocations = 0
        var tournamentId: String? = null

        override suspend fun invoke(tournamentId: String): RetryCloudBackupResult {
            invocations++
            this.tournamentId = tournamentId
            return result.await()
        }
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
