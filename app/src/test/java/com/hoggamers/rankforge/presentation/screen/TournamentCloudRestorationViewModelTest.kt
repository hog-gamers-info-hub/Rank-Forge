package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSummary
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
class TournamentCloudRestorationViewModelTest {
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
    fun exposesAvailableTournamentsAndRestoreSuccess() = runTest {
        val action = RecordingAction(
            loadResult = TournamentCloudRestorationResult.Available(SUMMARIES),
            restoreResult = TournamentCloudRestorationResult.Success("Summer Cup"),
        )
        val viewModel = TournamentCloudRestorationViewModel(action)

        viewModel.loadAvailable()
        advanceUntilIdle()
        assertEquals(TournamentCloudRestorationUiState.Available(SUMMARIES), viewModel.uiState.value)

        viewModel.restore(TOURNAMENT_ID)
        advanceUntilIdle()
        assertEquals(TournamentCloudRestorationUiState.Success("Summer Cup"), viewModel.uiState.value)
        assertEquals(TOURNAMENT_ID, action.restoredTournamentId)
    }

    @Test
    fun mapsAuthenticationAndLocalTransactionFailures() = runTest {
        val authViewModel = TournamentCloudRestorationViewModel(
            RecordingAction(
                loadResult = TournamentCloudRestorationResult.AuthenticationRequired,
                restoreResult = TournamentCloudRestorationResult.AuthenticationRequired,
            ),
        )
        authViewModel.loadAvailable()
        advanceUntilIdle()
        assertEquals(
            TournamentCloudRestorationUiState.AuthenticationRequired,
            authViewModel.uiState.value,
        )

        val localViewModel = TournamentCloudRestorationViewModel(
            RecordingAction(
                loadResult = TournamentCloudRestorationResult.Available(SUMMARIES),
                restoreResult = TournamentCloudRestorationResult.LocalTransactionFailure,
            ),
        )
        localViewModel.restore(TOURNAMENT_ID)
        advanceUntilIdle()
        assertEquals(
            TournamentCloudRestorationUiState.LocalTransactionFailure,
            localViewModel.uiState.value,
        )
    }

    @Test
    fun restoreQueueOutcomesMapToDistinctStates() = runTest {
        val queuedViewModel = TournamentCloudRestorationViewModel(
            RecordingAction(
                TournamentCloudRestorationResult.Available(SUMMARIES),
                TournamentCloudRestorationResult.NetworkFailure,
                QueueRecordingResult.RECORDED,
            ),
        )
        queuedViewModel.restore(TOURNAMENT_ID)
        advanceUntilIdle()
        assertEquals(TournamentCloudRestorationUiState.Queued, queuedViewModel.uiState.value)

        val persistenceFailureViewModel = TournamentCloudRestorationViewModel(
            RecordingAction(
                TournamentCloudRestorationResult.Available(SUMMARIES),
                TournamentCloudRestorationResult.NetworkFailure,
                QueueRecordingResult.PERSISTENCE_FAILED,
            ),
        )
        persistenceFailureViewModel.restore(TOURNAMENT_ID)
        advanceUntilIdle()
        assertEquals(
            TournamentCloudRestorationUiState.QueuePersistenceFailure,
            persistenceFailureViewModel.uiState.value,
        )

        val primaryFailureViewModel = TournamentCloudRestorationViewModel(
            RecordingAction(
                TournamentCloudRestorationResult.Available(SUMMARIES),
                TournamentCloudRestorationResult.AuthenticationRequired,
            ),
        )
        primaryFailureViewModel.restore(TOURNAMENT_ID)
        advanceUntilIdle()
        assertEquals(
            TournamentCloudRestorationUiState.AuthenticationRequired,
            primaryFailureViewModel.uiState.value,
        )
    }

    @Test
    fun thrownRestoreExceptionMapsToNetworkFailureAndResetReturnsToIdle() = runTest {
        val viewModel = TournamentCloudRestorationViewModel(
            RecordingAction(
                TournamentCloudRestorationResult.Available(SUMMARIES),
                TournamentCloudRestorationResult.NetworkFailure,
                throwOnRestore = true,
            ),
        )

        viewModel.restore(TOURNAMENT_ID)
        advanceUntilIdle()
        assertEquals(TournamentCloudRestorationUiState.NetworkFailure, viewModel.uiState.value)

        viewModel.reset()
        assertEquals(TournamentCloudRestorationUiState.Idle, viewModel.uiState.value)
    }

    private class RecordingAction(
        private val loadResult: TournamentCloudRestorationResult,
        private val restoreResult: TournamentCloudRestorationResult,
        private val queueRecordingResult: QueueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
        private val throwOnRestore: Boolean = false,
    ) : TournamentCloudRestorationAction {
        var restoredTournamentId: String? = null

        override suspend fun loadAvailable(): TournamentCloudRestorationResult = loadResult

        override suspend fun restore(
            tournamentId: String,
        ): QueueAwareActionResult<TournamentCloudRestorationResult> {
            restoredTournamentId = tournamentId
            if (throwOnRestore) throw IllegalStateException()
            return QueueAwareActionResult(
                primaryResult = restoreResult,
                queueRecordingResult = queueRecordingResult,
            )
        }
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        val SUMMARIES = listOf(
            TournamentCloudRestorationSummary(
                id = TOURNAMENT_ID,
                name = "Summer Cup",
                date = "2026-07-24",
                organizerName = "Organizer",
                status = "draft",
            ),
        )
    }
}
