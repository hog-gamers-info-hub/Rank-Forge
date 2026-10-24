package com.hoggamers.rankforge.data.activity

import kotlinx.coroutines.CancellationException

enum class ActivityEventType(val wireName: String) {
    RESULT_DOWNLOADED("result_downloaded"),
    STANDINGS_SHARE_INITIATED("standings_share_initiated"),
    CUSTOM_DESIGN_SAVED("custom_design_saved"),
}

interface ActivityEventTracker {
    suspend fun record(event: ActivityEventType)
}

object NoOpActivityEventTracker : ActivityEventTracker {
    override suspend fun record(event: ActivityEventType) = Unit
}

suspend fun ActivityEventTracker.recordBestEffort(event: ActivityEventType) {
    try {
        record(event)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        // Activity tracking must never affect the primary user operation.
    }
}
