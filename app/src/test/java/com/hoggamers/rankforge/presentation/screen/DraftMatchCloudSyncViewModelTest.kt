package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncStage
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
class DraftMatchCloudSyncViewModelTest {
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
    fun mapsAuthenticationAuthorizationAndNetworkStates() = runTest {
        val results = listOf(
            DraftMatchCloudSyncResult.Success to DraftMatchCloudSyncUiState.Success,
            DraftMatchCloudSyncResult.AuthenticationRequired to DraftMatchCloudSyncUiState.AuthenticationRequired,
            DraftMatchCloudSyncResult.AuthorizationFailure to DraftMatchCloudSyncUiState.AuthorizationFailure,
            DraftMatchCloudSyncResult.ValidationFailure to DraftMatchCloudSyncUiState.ValidationFailure,
            DraftMatchCloudSyncResult.NetworkFailure to DraftMatchCloudSyncUiState.NetworkFailure,
        )

        results.forEach { (result, expectedState) ->
            val viewModel = DraftMatchCloudSyncViewModel(RecordingAction(result))
            viewModel.sync(TOURNAMENT_ID)
            advanceUntilIdle()
            assertEquals(expectedState, viewModel.uiState.value)
        }
    }

    @Test
    fun exposesPartialFailureForManualRetry() = runTest {
        val viewModel = DraftMatchCloudSyncViewModel(
            RecordingAction(DraftMatchCloudSyncResult.PartialFailure(DraftMatchCloudSyncStage.MATCHES)),
        )

        viewModel.sync(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(
            DraftMatchCloudSyncUiState.PartialFailure(DraftMatchCloudSyncStage.MATCHES),
            viewModel.uiState.value,
        )
        viewModel.reset()
        assertEquals(DraftMatchCloudSyncUiState.Idle, viewModel.uiState.value)
    }

    private class RecordingAction(
        private val result: DraftMatchCloudSyncResult,
    ) : DraftMatchCloudSyncAction {
        override suspend fun invoke(tournamentId: String): DraftMatchCloudSyncResult = result
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
