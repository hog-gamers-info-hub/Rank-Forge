package com.hoggamers.rankforge.presentation.screen

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
    fun mapsAllUploadStates() = runTest {
        val action = RecordingAction(TournamentCloudUploadResult.Success)
        val viewModel = TournamentCloudUploadViewModel(action)

        viewModel.upload(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(TournamentCloudUploadUiState.Success, viewModel.uiState.value)
        assertEquals(TOURNAMENT_ID, action.tournamentId)
    }

    @Test
    fun exposesPartialFailureForManualRetry() = runTest {
        val viewModel = TournamentCloudUploadViewModel(
            RecordingAction(TournamentCloudUploadResult.PartialFailure(TournamentCloudUploadStage.TOURNAMENT)),
        )

        viewModel.upload(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(
            TournamentCloudUploadUiState.PartialFailure(TournamentCloudUploadStage.TOURNAMENT),
            viewModel.uiState.value,
        )
        viewModel.reset()
        assertEquals(TournamentCloudUploadUiState.Idle, viewModel.uiState.value)
    }

    private class RecordingAction(
        private val result: TournamentCloudUploadResult,
    ) : TournamentCloudUploadAction {
        var tournamentId: String? = null

        override suspend fun invoke(tournamentId: String): TournamentCloudUploadResult {
            this.tournamentId = tournamentId
            return result
        }
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
