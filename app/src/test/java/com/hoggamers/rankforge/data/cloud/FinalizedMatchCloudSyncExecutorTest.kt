package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalizedMatchCloudSyncExecutorTest {
    @Test
    fun bootstrapsMissingDataWithDraftAndEmptyResultsBeforeRetryingFinalize() = runBlocking {
        val calls = mutableListOf<String>()
        val originalMatch = payloads().matches.single()
        val finalizedResults = mutableListOf<List<FinalizedMatchResultUploadPayload>>()
        var draftPayload: FinalizedMatchUploadPayload? = null
        var draftResults: List<FinalizedMatchResultUploadPayload>? = null
        var finalizeCount = 0
        val executor = FinalizedMatchCloudSyncExecutor(
            finalizeMatch = { match, results, expectedRevision ->
                calls += "finalize:${match.id}:$expectedRevision"
                finalizedResults += results
                if (++finalizeCount == 1) {
                    RevisionWriteResponse("missing_data")
                } else {
                    assertEquals(3, expectedRevision)
                    RevisionWriteResponse("success", 4)
                }
            },
            writeDraftMatch = { match, results, expectedRevision ->
                calls += "draft:${match.id}:$expectedRevision"
                draftPayload = match
                draftResults = results
                RevisionWriteResponse("success", 3)
            },
        )

        val result = executor.execute(payloads(), expectedRevision = 2)

        assertEquals(FinalizedMatchCloudSyncExecutionResult.Success(4), result)
        assertEquals(
            listOf(
                "finalize:11111111-1111-1111-1111-111111111111:2",
                "draft:11111111-1111-1111-1111-111111111111:2",
                "finalize:11111111-1111-1111-1111-111111111111:3",
            ),
            calls,
        )
        assertEquals(listOf(payloads().matchResults.single()), finalizedResults[0])
        assertEquals(finalizedResults[0], finalizedResults[1])
        assertTrue(draftResults?.isEmpty() == true)
        assertEquals("draft", draftPayload?.status)
        assertEquals(originalMatch.id, draftPayload?.id)
        assertEquals(originalMatch.tournamentId, draftPayload?.tournamentId)
        assertEquals(originalMatch.matchNumber, draftPayload?.matchNumber)
        assertEquals(originalMatch.matchDate, draftPayload?.matchDate)
        assertEquals(originalMatch.mapName, draftPayload?.mapName)
    }

    @Test
    fun chainsThreeMissingMatchesFromCurrentRevision() = runBlocking {
        val finalizeRevisions = mutableListOf<Int>()
        val draftRevisions = mutableListOf<Int>()
        val executor = FinalizedMatchCloudSyncExecutor(
            finalizeMatch = { _, _, expectedRevision ->
                finalizeRevisions += expectedRevision
                if (expectedRevision % 2 == 0) {
                    RevisionWriteResponse("missing_data")
                } else {
                    RevisionWriteResponse("success", expectedRevision + 1)
                }
            },
            writeDraftMatch = { _, _, expectedRevision ->
                draftRevisions += expectedRevision
                RevisionWriteResponse("success", expectedRevision + 1)
            },
        )

        val result = executor.execute(payloads(matchCount = 3), expectedRevision = 2)

        assertEquals(FinalizedMatchCloudSyncExecutionResult.Success(8), result)
        assertEquals(listOf(2, 3, 4, 5, 6, 7), finalizeRevisions)
        assertEquals(listOf(2, 4, 6), draftRevisions)
    }

    @Test
    fun alreadyFinalizedUsesReturnedRevisionWithoutBootstrapping() = runBlocking {
        var bootstrapCalled = false
        val executor = FinalizedMatchCloudSyncExecutor(
            finalizeMatch = { _, _, _ -> RevisionWriteResponse("already_finalized", 7) },
            writeDraftMatch = { _, _, _ ->
                bootstrapCalled = true
                RevisionWriteResponse("success", 8)
            },
        )

        val result = executor.execute(payloads(), expectedRevision = 2)

        assertEquals(FinalizedMatchCloudSyncExecutionResult.Success(7), result)
        assertTrue(!bootstrapCalled)
    }

    @Test
    fun alreadyFinalizedWithoutRevisionReturnsValidationFailure() = runBlocking {
        val result = FinalizedMatchCloudSyncExecutor(
            finalizeMatch = { _, _, _ -> RevisionWriteResponse("already_finalized") },
            writeDraftMatch = { _, _, _ -> error("bootstrap must not run") },
        ).execute(payloads(), expectedRevision = 2)

        assertValidationFailureWithoutConfirmedRevision(result)
    }

    @Test
    fun alreadyFinalizedWithNonpositiveRevisionReturnsValidationFailure() = runBlocking {
        listOf(0, -1).forEach { invalidRevision ->
            val result = FinalizedMatchCloudSyncExecutor(
                finalizeMatch = { _, _, _ -> RevisionWriteResponse("already_finalized", invalidRevision) },
                writeDraftMatch = { _, _, _ -> error("bootstrap must not run") },
            ).execute(payloads(), expectedRevision = 2)

            assertValidationFailureWithoutConfirmedRevision(result)
        }
    }

    @Test
    fun conflictStopsBeforeLaterMatchesAndCarriesLastConfirmedRevision() = runBlocking {
        var finalized = 0
        val executor = FinalizedMatchCloudSyncExecutor(
            finalizeMatch = { _, _, _ ->
                finalized += 1
                if (finalized == 1) RevisionWriteResponse("success", 3)
                else RevisionWriteResponse("stale_write", 9)
            },
            writeDraftMatch = { _, _, _ -> error("bootstrap must not run") },
        )

        val result = executor.execute(payloads(matchCount = 3), expectedRevision = 2)

        assertEquals(
            FinalizedMatchCloudSyncExecutionResult.Failure(
                completedStage = FinalizedMatchCloudSyncCompletedStage.MATCHES,
                category = FinalizedMatchCloudSyncFailureCategory.CONFLICT,
                conflict = RevisionConflict.StaleWrite(CloudRevision(3), CloudRevision(9)),
                confirmedCloudRevision = 3,
            ),
            result,
        )
        assertEquals(2, finalized)
    }

    @Test
    fun failureAfterConfirmedMatchIsPartialAndKeepsRevision() = runBlocking {
        var finalized = 0
        val executor = FinalizedMatchCloudSyncExecutor(
            finalizeMatch = { _, _, expectedRevision ->
                finalized += 1
                if (finalized == 1) RevisionWriteResponse("success", expectedRevision + 1)
                else error("network unavailable")
            },
            writeDraftMatch = { _, _, _ -> error("bootstrap must not run") },
        )

        val result = executor.execute(payloads(matchCount = 2), expectedRevision = 2)

        assertEquals(FinalizedMatchCloudSyncFailureCategory.NETWORK, (result as FinalizedMatchCloudSyncExecutionResult.Failure).category)
        assertEquals(FinalizedMatchCloudSyncCompletedStage.MATCHES, result.completedStage)
        assertEquals(3, result.confirmedCloudRevision)
    }

    @Test
    fun successfulResponseWithoutPositiveRevisionIsValidationFailure() = runBlocking {
        val result = FinalizedMatchCloudSyncExecutor(
            finalizeMatch = { _, _, _ -> RevisionWriteResponse("success") },
            writeDraftMatch = { _, _, _ -> error("bootstrap must not run") },
        ).execute(payloads(), expectedRevision = 2)

        assertValidationFailureWithoutConfirmedRevision(result)
    }

    @Test
    fun emptyPayloadIsValidationFailureWithoutConfirmedProgress() = runBlocking {
        val result = FinalizedMatchCloudSyncExecutor(
            finalizeMatch = { _, _, _ -> error("finalize must not run") },
            writeDraftMatch = { _, _, _ -> error("bootstrap must not run") },
        ).execute(
            payloads = FinalizedMatchCloudSyncPayloads(emptyList(), emptyList()),
            expectedRevision = 2,
        )

        assertValidationFailureWithoutConfirmedRevision(result)
    }

    @Test
    fun executionSuccessRejectsNonpositiveRevision() {
        assertThrows(IllegalArgumentException::class.java) {
            FinalizedMatchCloudSyncExecutionResult.Success(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FinalizedMatchCloudSyncExecutionResult.Success(-1)
        }
    }

    @Test
    fun authenticationAndAuthorizationOutcomesRemainDistinct() = runBlocking {
        val authentication = FinalizedMatchCloudSyncExecutor(
            finalizeMatch = { _, _, _ -> RevisionWriteResponse("authentication_required") },
            writeDraftMatch = { _, _, _ -> error("bootstrap must not run") },
        ).execute(payloads(), expectedRevision = 2)
        val authorization = FinalizedMatchCloudSyncExecutor(
            finalizeMatch = { _, _, _ -> RevisionWriteResponse("unauthorized") },
            writeDraftMatch = { _, _, _ -> error("bootstrap must not run") },
        ).execute(payloads(), expectedRevision = 2)

        assertEquals(
            FinalizedMatchCloudSyncFailureCategory.AUTHENTICATION,
            (authentication as FinalizedMatchCloudSyncExecutionResult.Failure).category,
        )
        assertEquals(
            FinalizedMatchCloudSyncFailureCategory.AUTHORIZATION,
            (authorization as FinalizedMatchCloudSyncExecutionResult.Failure).category,
        )
    }

    private fun assertValidationFailureWithoutConfirmedRevision(
        result: FinalizedMatchCloudSyncExecutionResult,
    ) {
        val failure = result as FinalizedMatchCloudSyncExecutionResult.Failure
        assertEquals(FinalizedMatchCloudSyncFailureCategory.VALIDATION, failure.category)
        assertNull(failure.confirmedCloudRevision)
    }

    private fun payloads(matchCount: Int = 1): FinalizedMatchCloudSyncPayloads {
        val matches = (1..matchCount).map { number ->
            FinalizedMatchUploadPayload(
                id = "11111111-1111-1111-1111-11111111111$number",
                tournamentId = "22222222-2222-2222-2222-222222222222",
                matchNumber = number,
                matchDate = "2026-07-24",
                mapName = "Bermuda",
                status = "finalized",
            )
        }
        return FinalizedMatchCloudSyncPayloads(
            matches = matches,
            matchResults = listOf(
                FinalizedMatchResultUploadPayload(
                    id = "33333333-3333-3333-3333-333333333333",
                    matchId = matches.first().id,
                    teamSlotId = "44444444-4444-4444-4444-444444444444",
                    placement = 1,
                    kills = 0,
                    source = "manual",
                    reviewStatus = "confirmed",
                ),
            ),
        )
    }
}
