package com.hoggamers.rankforge.presentation.screen

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
    @Test fun mapsAllRestoreResults() = runTest {
        val cases = listOf(MatchCloudRestorationResult.Success to MatchCloudRestorationUiState.Success, MatchCloudRestorationResult.NoCloudMatches to MatchCloudRestorationUiState.NoCloudMatches, MatchCloudRestorationResult.AuthenticationRequired to MatchCloudRestorationUiState.AuthenticationRequired, MatchCloudRestorationResult.AuthorizationFailure to MatchCloudRestorationUiState.AuthorizationFailure, MatchCloudRestorationResult.ValidationFailure to MatchCloudRestorationUiState.ValidationFailure, MatchCloudRestorationResult.NetworkFailure to MatchCloudRestorationUiState.NetworkFailure, MatchCloudRestorationResult.LocalTransactionFailure to MatchCloudRestorationUiState.LocalTransactionFailure)
        cases.forEach { (result, expected) -> val vm = MatchCloudRestorationViewModel { result }; vm.restore("id"); advanceUntilIdle(); assertEquals(expected, vm.uiState.value) }
    }
}
