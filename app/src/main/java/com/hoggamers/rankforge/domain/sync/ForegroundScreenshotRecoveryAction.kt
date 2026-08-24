package com.hoggamers.rankforge.domain.sync

fun interface ForegroundScreenshotRecoveryAction {
    suspend fun recoverAfterParentQueue()

    suspend fun recoverAfterParentQueue(expectedOwnerUserId: String): Unit =
        throw SecurityException("Expected screenshot owner is required.")
}

object NoOpForegroundScreenshotRecoveryAction : ForegroundScreenshotRecoveryAction {
    override suspend fun recoverAfterParentQueue() {
        // Used by existing callers that do not provide screenshot recovery.
    }

    override suspend fun recoverAfterParentQueue(expectedOwnerUserId: String) {
        // Used by existing callers that do not provide screenshot recovery.
    }
}
