package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSummary
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

    @Test
    fun loadingAndRestoringBlockDuplicatesAndKeepListForAnotherRestore() = runTest {
        val loadGate = CompletableDeferred<Unit>()
        val restoreGate = CompletableDeferred<Unit>()
        val summaries = SUMMARIES + TournamentCloudRestorationSummary(
            id = SECOND_TOURNAMENT_ID,
            name = "Winter Cup",
            stageName = "Organizer",
            status = "draft",
        )
        val action = RecordingAction(
            loadResult = TournamentCloudRestorationResult.Available(summaries),
            restoreResult = TournamentCloudRestorationResult.Success("Summer Cup"),
            loadGate = loadGate,
            restoreGate = restoreGate,
        )
        val viewModel = TournamentCloudRestorationViewModel(action)

        viewModel.loadAvailable()
        runCurrent()
        assertEquals(TournamentCloudRestorationUiState.Loading, viewModel.uiState.value)
        viewModel.loadAvailable()
        assertEquals(1, action.loadCalls)

        loadGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(summaries, viewModel.availableTournaments.value)

        viewModel.restore(TOURNAMENT_ID)
        runCurrent()
        assertEquals(
            TournamentCloudRestorationUiState.Restoring(TOURNAMENT_ID, "Summer Cup"),
            viewModel.uiState.value,
        )
        assertEquals(summaries, viewModel.availableTournaments.value)
        viewModel.restore(SECOND_TOURNAMENT_ID)
        assertEquals(1, action.restoreCalls)

        restoreGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(TournamentCloudRestorationUiState.Success("Summer Cup"), viewModel.uiState.value)

        viewModel.restore(SECOND_TOURNAMENT_ID)
        advanceUntilIdle()
        assertEquals(2, action.restoreCalls)
        assertEquals(1, action.loadCalls)
    }

    private class RecordingAction(
        private val loadResult: TournamentCloudRestorationResult,
        private val restoreResult: TournamentCloudRestorationResult,
        private val queueRecordingResult: QueueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
        private val throwOnRestore: Boolean = false,
        private val loadGate: CompletableDeferred<Unit>? = null,
        private val restoreGate: CompletableDeferred<Unit>? = null,
    ) : TournamentCloudRestorationAction {
        var loadCalls: Int = 0
        var restoreCalls: Int = 0
        var restoredTournamentId: String? = null

        override suspend fun loadAvailable(): TournamentCloudRestorationResult {
            loadCalls += 1
            loadGate?.await()
            return loadResult
        }

        override suspend fun restore(
            tournamentId: String,
        ): QueueAwareActionResult<TournamentCloudRestorationResult> {
            restoreCalls += 1
            restoredTournamentId = tournamentId
            restoreGate?.await()
            if (throwOnRestore) throw IllegalStateException()
            return QueueAwareActionResult(
                primaryResult = restoreResult,
                queueRecordingResult = queueRecordingResult,
            )
        }
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        const val SECOND_TOURNAMENT_ID = "22222222-2222-2222-2222-222222222222"
        val SUMMARIES = listOf(
            TournamentCloudRestorationSummary(
                id = TOURNAMENT_ID,
                name = "Summer Cup",
                stageName = "Organizer",
                status = "draft",
            ),
        )
    }
}
