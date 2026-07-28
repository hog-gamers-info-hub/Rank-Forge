package com.hoggamers.rankforge.data.cloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalizedMatchCloudSyncExecutorTest {
    @Test
    fun uploadsFinalizedMatchesBeforeConfirmedResults() = runBlocking {
        val calls = mutableListOf<String>()
        val executor = FinalizedMatchCloudSyncExecutor(
            upsertMatches = { calls += "matches" },
            upsertMatchResults = { calls += "match_results" },
        )

        assertEquals(FinalizedMatchCloudSyncExecutionResult.Success, executor.execute(payloads()))
        assertEquals(listOf("matches", "match_results"), calls)
    }

    @Test
    fun reportsPartialFailureAfterMatchesWithoutRollback() = runBlocking {
        val calls = mutableListOf<String>()
        val executor = FinalizedMatchCloudSyncExecutor(
            upsertMatches = { calls += "matches" },
            upsertMatchResults = { calls += "match_results"; error("network unavailable") },
        )

        val result = executor.execute(payloads()) as FinalizedMatchCloudSyncExecutionResult.Failure

        assertEquals(FinalizedMatchCloudSyncCompletedStage.MATCHES, result.completedStage)
        assertEquals(FinalizedMatchCloudSyncFailureCategory.NETWORK, result.category)
        assertTrue("rollback" !in calls)
    }

    private fun payloads() = FinalizedMatchCloudSyncPayloads(
        matches = listOf(
            FinalizedMatchUploadPayload(
                id = "11111111-1111-1111-1111-111111111111",
                tournamentId = "22222222-2222-2222-2222-222222222222",
                matchNumber = 1,
                matchDate = "2026-07-24",
                mapName = "Bermuda",
                status = "finalized",
            ),
        ),
        matchResults = listOf(
            FinalizedMatchResultUploadPayload(
                id = "33333333-3333-3333-3333-333333333333",
                matchId = "11111111-1111-1111-1111-111111111111",
                teamSlotId = "44444444-4444-4444-4444-444444444444",
                placement = 1,
                kills = 0,
                source = "manual",
                reviewStatus = "confirmed",
            ),
        ),
    )
}
