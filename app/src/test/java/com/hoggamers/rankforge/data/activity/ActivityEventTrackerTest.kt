package com.hoggamers.rankforge.data.activity

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityEventTrackerTest {
    @Test
    fun eventTypesUseTheAllowListedWireNames() {
        assertEquals(
            listOf(
                "result_downloaded",
                "standings_share_initiated",
                "custom_design_saved",
            ),
            ActivityEventType.entries.map(ActivityEventType::wireName),
        )
    }

    @Test
    fun bestEffortTrackingSwallowsTrackerFailure() = runTest {
        val tracker = object : ActivityEventTracker {
            override suspend fun record(event: ActivityEventType) {
                error("tracking unavailable")
            }
        }

        tracker.recordBestEffort(ActivityEventType.RESULT_DOWNLOADED)
    }
}
