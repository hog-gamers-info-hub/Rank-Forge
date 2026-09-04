package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.cloud.CustomDesignSavedIdDiscoveryAction
import com.hoggamers.rankforge.data.cloud.CustomDesignSavedIdDiscoveryResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomDesignFormatAvailabilityViewModelTest {
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
    fun noneMapsToNoneWithoutAnId() = runTest {
        val viewModel = createViewModel {
            CustomDesignSavedIdDiscoveryResult.None
        }

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(
            CustomDesignFormatAvailabilityUiState(
                status = CustomDesignFormatAvailabilityStatus.NONE,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun foundMapsToFoundWithTheExactId() = runTest {
        val designId = "a2000000-0000-0000-0000-000000000001"
        val viewModel = createViewModel {
            CustomDesignSavedIdDiscoveryResult.Found(designId)
        }

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(
            CustomDesignFormatAvailabilityUiState(
                status = CustomDesignFormatAvailabilityStatus.FOUND,
                customDesignId = designId,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun ambiguousMapsToUnavailableWithoutAnId() = runTest {
        val viewModel = createViewModel {
            CustomDesignSavedIdDiscoveryResult.Ambiguous
        }

        viewModel.refresh()
        advanceUntilIdle()

        assertUnavailable(viewModel)
    }

    @Test
    fun failedMapsToUnavailableWithoutAnId() = runTest {
        val viewModel = createViewModel {
            CustomDesignSavedIdDiscoveryResult.Failed(
                com.hoggamers.rankforge.data.cloud.CustomDesignTemplateCloudFailure.READ_FAILED,
            )
        }

        viewModel.refresh()
        advanceUntilIdle()

        assertUnavailable(viewModel)
    }

    @Test
    fun thrownExceptionMapsToUnavailable() = runTest {
        val viewModel = createViewModel {
            error("discovery failed")
        }

        viewModel.refresh()
        advanceUntilIdle()

        assertUnavailable(viewModel)
    }

    @Test
    fun staleOlderRefreshCannotOverwriteNewerResult() = runTest {
        val oldResult = CompletableDeferred<CustomDesignSavedIdDiscoveryResult>()
        val newResult = CompletableDeferred<CustomDesignSavedIdDiscoveryResult>()
        var calls = 0
        val viewModel = createViewModel {
            if (calls++ == 0) {
                withContext(NonCancellable) { oldResult.await() }
            } else {
                newResult.await()
            }
        }

        viewModel.refresh()
        runCurrent()
        viewModel.refresh()
        newResult.complete(
            CustomDesignSavedIdDiscoveryResult.Found("a2000000-0000-0000-0000-000000000002"),
        )
        advanceUntilIdle()

        assertEquals(CustomDesignFormatAvailabilityStatus.FOUND, viewModel.uiState.value.status)
        assertEquals("a2000000-0000-0000-0000-000000000002", viewModel.uiState.value.customDesignId)

        oldResult.complete(
            CustomDesignSavedIdDiscoveryResult.Found("a2000000-0000-0000-0000-000000000001"),
        )
        advanceUntilIdle()

        assertEquals("a2000000-0000-0000-0000-000000000002", viewModel.uiState.value.customDesignId)
    }

    @Test
    fun laterRefreshCanChangeNoneToFound() = runTest {
        val results = ArrayDeque<CustomDesignSavedIdDiscoveryResult>(
            listOf(
                CustomDesignSavedIdDiscoveryResult.None,
                CustomDesignSavedIdDiscoveryResult.Found("a2000000-0000-0000-0000-000000000001"),
            ),
        )
        val viewModel = createViewModel { results.removeFirst() }

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(CustomDesignFormatAvailabilityStatus.FOUND, viewModel.uiState.value.status)
        assertEquals("a2000000-0000-0000-0000-000000000001", viewModel.uiState.value.customDesignId)
    }

    @Test
    fun laterRefreshCanChangeFoundToNone() = runTest {
        val results = ArrayDeque<CustomDesignSavedIdDiscoveryResult>(
            listOf(
                CustomDesignSavedIdDiscoveryResult.Found("a2000000-0000-0000-0000-000000000001"),
                CustomDesignSavedIdDiscoveryResult.None,
            ),
        )
        val viewModel = createViewModel { results.removeFirst() }

        viewModel.refresh()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(CustomDesignFormatAvailabilityStatus.NONE, viewModel.uiState.value.status)
        assertEquals(null, viewModel.uiState.value.customDesignId)
    }

    private fun createViewModel(
        find: suspend () -> CustomDesignSavedIdDiscoveryResult,
    ) = CustomDesignFormatAvailabilityViewModel(
        CustomDesignSavedIdDiscoveryAction(find),
    )

    private fun assertUnavailable(
        viewModel: CustomDesignFormatAvailabilityViewModel,
    ) {
        assertEquals(CustomDesignFormatAvailabilityStatus.UNAVAILABLE, viewModel.uiState.value.status)
        assertEquals(null, viewModel.uiState.value.customDesignId)
    }
}
