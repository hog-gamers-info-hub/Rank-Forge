package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationAction
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationResult
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
class MatchCloudRestorationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    @Test fun mapsAllRestoreResultsAndPassesSelectedTournamentId() = runTest {
        val cases = listOf(MatchCloudRestorationResult.Success to MatchCloudRestorationUiState.Success, MatchCloudRestorationResult.NoCloudMatches to MatchCloudRestorationUiState.NoCloudMatches, MatchCloudRestorationResult.AuthenticationRequired to MatchCloudRestorationUiState.AuthenticationRequired, MatchCloudRestorationResult.AuthorizationFailure to MatchCloudRestorationUiState.AuthorizationFailure, MatchCloudRestorationResult.ValidationFailure to MatchCloudRestorationUiState.ValidationFailure, MatchCloudRestorationResult.NetworkFailure to MatchCloudRestorationUiState.NetworkFailure, MatchCloudRestorationResult.LocalTransactionFailure to MatchCloudRestorationUiState.LocalTransactionFailure)
        cases.forEach { (result, expected) ->
            val action = RecordingAction(result)
            val vm = MatchCloudRestorationViewModel(action)
            vm.restore(TOURNAMENT_ID)
            advanceUntilIdle()
            assertEquals(expected, vm.uiState.value)
            assertEquals(TOURNAMENT_ID, action.tournamentId)
        }
    }

    @Test fun queueOutcomesMapToDistinctStatesAndThrownExceptionMapsToNetworkFailure() = runTest {
        val queued = MatchCloudRestorationViewModel(
            RecordingAction(MatchCloudRestorationResult.NetworkFailure, QueueRecordingResult.RECORDED),
        )
        queued.restore(TOURNAMENT_ID)
        advanceUntilIdle()
        assertEquals(MatchCloudRestorationUiState.Queued, queued.uiState.value)

        val persistenceFailure = MatchCloudRestorationViewModel(
            RecordingAction(MatchCloudRestorationResult.NetworkFailure, QueueRecordingResult.PERSISTENCE_FAILED),
        )
        persistenceFailure.restore(TOURNAMENT_ID)
        advanceUntilIdle()
        assertEquals(MatchCloudRestorationUiState.QueuePersistenceFailure, persistenceFailure.uiState.value)

        val primaryFailure = MatchCloudRestorationViewModel(
            RecordingAction(MatchCloudRestorationResult.AuthenticationRequired),
        )
        primaryFailure.restore(TOURNAMENT_ID)
        advanceUntilIdle()
        assertEquals(MatchCloudRestorationUiState.AuthenticationRequired, primaryFailure.uiState.value)

        val throwing = MatchCloudRestorationViewModel(
            RecordingAction(MatchCloudRestorationResult.NetworkFailure, throwOnInvoke = true),
        )
        throwing.restore(TOURNAMENT_ID)
        advanceUntilIdle()
        assertEquals(MatchCloudRestorationUiState.NetworkFailure, throwing.uiState.value)
    }

    private class RecordingAction(
        private val result: MatchCloudRestorationResult,
        private val queueRecordingResult: QueueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
        private val throwOnInvoke: Boolean = false,
    ) : MatchCloudRestorationAction {
        var tournamentId: String? = null

        override suspend fun invoke(tournamentId: String): QueueAwareActionResult<MatchCloudRestorationResult> {
            this.tournamentId = tournamentId
            if (throwOnInvoke) throw IllegalStateException()
            return QueueAwareActionResult(result, queueRecordingResult)
        }
    }

    private companion object { const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111" }
}
