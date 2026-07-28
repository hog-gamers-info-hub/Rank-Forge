package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.tournament.ConflictOperation
import com.hoggamers.rankforge.domain.tournament.ConflictResolutionContext
import com.hoggamers.rankforge.domain.tournament.ConflictResolvability
import com.hoggamers.rankforge.domain.tournament.DraftConflictResolutionResult
import com.hoggamers.rankforge.domain.tournament.DraftConflictResolver
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
class DraftConflictResolutionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun keepLocalDelegatesOnlyAfterAnExplicitAction() = runTest {
        val resolver = RecordingResolver(DraftConflictResolutionResult.KeepLocalSucceeded)
        val viewModel = DraftConflictResolutionViewModel(resolver)
        viewModel.load(TOURNAMENT_ID, 4)

        viewModel.keepLocal()
        advanceUntilIdle()

        assertEquals(1, resolver.keepLocalCalls)
        assertEquals(DraftConflictResolutionUiState.KeepLocalSucceeded, viewModel.uiState.value)
    }

    @Test
    fun acceptCloudRequiresConfirmationBeforeDelegating() = runTest {
        val resolver = RecordingResolver(DraftConflictResolutionResult.AcceptedCloudDraft)
        val viewModel = DraftConflictResolutionViewModel(resolver)
        viewModel.load(TOURNAMENT_ID, 4)

        viewModel.requestAcceptCloud()
        assertEquals(0, resolver.acceptCalls)
        viewModel.acceptCloud()
        advanceUntilIdle()

        assertEquals(1, resolver.acceptCalls)
        assertEquals(DraftConflictResolutionUiState.AcceptedCloudDraft, viewModel.uiState.value)
    }

    private class RecordingResolver(
        private val result: DraftConflictResolutionResult,
    ) : DraftConflictResolver {
        var keepLocalCalls = 0
        var acceptCalls = 0
        override suspend fun keepLocal(context: ConflictResolutionContext): DraftConflictResolutionResult {
            keepLocalCalls++
            return result
        }
        override suspend fun acceptCloudDraft(context: ConflictResolutionContext): DraftConflictResolutionResult {
            acceptCalls++
            return result
        }
    }

    private companion object { const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111" }
}
