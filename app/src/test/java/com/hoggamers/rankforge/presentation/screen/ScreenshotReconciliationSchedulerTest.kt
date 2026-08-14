package com.hoggamers.rankforge.presentation.screen

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScreenshotReconciliationSchedulerTest {
    @Test
    fun scheduledWorkOutlivesTheOwnerLifecycle() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val schedulerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val scheduler = ScreenshotReconciliationScheduler(schedulerScope, testOnly = true)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var completed = false
        val owner = SupervisorJob()

        val job = scheduler.schedule {
            started.complete(Unit)
            release.await()
            completed = true
        }
        advanceUntilIdle()
        started.await()
        owner.cancel()

        assertFalse(job.isCancelled)
        release.complete(Unit)
        advanceUntilIdle()

        assertTrue(completed)
        schedulerScope.cancel()
    }
}
