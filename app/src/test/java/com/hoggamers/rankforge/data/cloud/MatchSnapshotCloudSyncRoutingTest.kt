package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchSnapshotCloudSyncRoutingTest {
    @Test
    fun standardDraftUsesOnlyLegacyWrite() = runBlocking {
        val invoker = RecordingInvoker()

        val result = DraftMatchCloudSyncRpcRouter(invoker)
            .invoke(draftPayloads(TournamentFormat.STANDARD), expectedRevision = 2)

        assertEquals(DraftMatchCloudSyncExecutionResult.Success, result)
        assertEquals(listOf("write"), invoker.calls)
    }

    @Test
    fun groupRotationDraftUsesOnlyV2Write() = runBlocking {
        val invoker = RecordingInvoker()

        val result = DraftMatchCloudSyncRpcRouter(invoker)
            .invoke(draftPayloads(TournamentFormat.GROUP_ROTATION), expectedRevision = 2)

        assertEquals(DraftMatchCloudSyncExecutionResult.Success, result)
        assertEquals(listOf("write_v2"), invoker.calls)
    }

    @Test
    fun groupRotationDraftMapsDocumentedOutcomesExplicitly() = runBlocking {
        val outcomes = listOf(
            RevisionWriteResponse("stale_write", 9) to
                DraftMatchCloudSyncFailureCategory.CONFLICT,
            RevisionWriteResponse("missing_revision") to
                DraftMatchCloudSyncFailureCategory.CONFLICT,
            RevisionWriteResponse("authentication_required") to
                DraftMatchCloudSyncFailureCategory.AUTHENTICATION,
            RevisionWriteResponse("unauthorized") to
                DraftMatchCloudSyncFailureCategory.AUTHORIZATION,
            RevisionWriteResponse("validation_failure") to
                DraftMatchCloudSyncFailureCategory.VALIDATION,
            RevisionWriteResponse("unexpected") to
                DraftMatchCloudSyncFailureCategory.UNKNOWN,
        )

        outcomes.forEach { (response, expectedCategory) ->
            val result = DraftMatchCloudSyncRpcRouter(
                RecordingInvoker(writeResponse = response),
            ).invoke(
                draftPayloads(TournamentFormat.GROUP_ROTATION),
                expectedRevision = 5,
            ) as DraftMatchCloudSyncExecutionResult.Failure

            assertEquals(expectedCategory, result.category)
            if (response.outcome == "missing_revision") {
                assertEquals(RevisionConflict.MissingRevision, result.conflict)
            }
        }

        val stale = DraftMatchCloudSyncRpcRouter(
            RecordingInvoker(writeResponse = RevisionWriteResponse("stale_write", 9)),
        ).invoke(
            draftPayloads(TournamentFormat.GROUP_ROTATION),
            expectedRevision = 5,
        ) as DraftMatchCloudSyncExecutionResult.Failure
        assertEquals(
            RevisionConflict.StaleWrite(CloudRevision(5), CloudRevision(9)),
            stale.conflict,
        )
    }

    @Test
    fun inconsistentDraftFormatPairingOrTournamentPayloadFailsBeforeRpc() = runBlocking {
        val cases = listOf(
            draftPayloads(TournamentFormat.STANDARD, groupPairingKey = "A:B"),
            draftPayloads(TournamentFormat.GROUP_ROTATION, groupPairingKey = null),
            draftPayloads(TournamentFormat.STANDARD).copy(
                matches = draftPayloads(TournamentFormat.STANDARD).matches + DraftMatchUploadPayload(
                    id = "draft-2",
                    tournamentId = "other-tournament",
                    matchNumber = 2,
                    matchDate = "2026-07-25",
                    mapName = "Bermuda",
                    status = "draft",
                ),
            ),
        )

        cases.forEach { payloads ->
            val invoker = RecordingInvoker()
            val result = DraftMatchCloudSyncRpcRouter(invoker)
                .invoke(payloads, expectedRevision = 2)

            assertEquals(
                DraftMatchCloudSyncFailureCategory.VALIDATION,
                (result as DraftMatchCloudSyncExecutionResult.Failure).category,
            )
            assertTrue(invoker.calls.isEmpty())
        }
    }

    @Test
    fun standardFinalizeUsesOnlyLegacyFinalize() = runBlocking {
        val invoker = RecordingInvoker(
            finalizeResponses = mutableListOf(RevisionWriteResponse("success", 3)),
        )

        val result = FinalizedMatchCloudSyncRpcRouter(invoker)
            .invoke(finalizedPayloads(TournamentFormat.STANDARD), expectedRevision = 2)

        assertEquals(FinalizedMatchCloudSyncExecutionResult.Success(3), result)
        assertEquals(listOf("finalize"), invoker.calls)
    }

    @Test
    fun groupRotationFinalizeUsesOnlyV2Finalize() = runBlocking {
        val invoker = RecordingInvoker(
            finalizeResponses = mutableListOf(RevisionWriteResponse("success", 3)),
        )

        val result = FinalizedMatchCloudSyncRpcRouter(invoker)
            .invoke(finalizedPayloads(TournamentFormat.GROUP_ROTATION), expectedRevision = 2)

        assertEquals(FinalizedMatchCloudSyncExecutionResult.Success(3), result)
        assertEquals(listOf("finalize_v2"), invoker.calls)
    }

    @Test
    fun standardMissingDataBootstrapsWithLegacyPairAndPreservesRevisionChain() = runBlocking {
        val invoker = RecordingInvoker(
            finalizeResponses = mutableListOf(
                RevisionWriteResponse("missing_data"),
                RevisionWriteResponse("success", 4),
            ),
            writeResponse = RevisionWriteResponse("success", 3),
        )

        val result = FinalizedMatchCloudSyncRpcRouter(invoker)
            .invoke(finalizedPayloads(TournamentFormat.STANDARD), expectedRevision = 2)

        assertEquals(FinalizedMatchCloudSyncExecutionResult.Success(4), result)
        assertEquals(listOf("finalize", "write", "finalize"), invoker.calls)
        assertEquals(listOf(2, 3), invoker.finalizeRevisions)
        assertEquals(listOf(2), invoker.writeRevisions)
    }

    @Test
    fun groupRotationMissingDataBootstrapsWithV2PairAndPreservesRevisionChain() = runBlocking {
        val invoker = RecordingInvoker(
            finalizeResponses = mutableListOf(
                RevisionWriteResponse("missing_data"),
                RevisionWriteResponse("success", 4),
            ),
            writeResponse = RevisionWriteResponse("success", 3),
        )

        val result = FinalizedMatchCloudSyncRpcRouter(invoker)
            .invoke(finalizedPayloads(TournamentFormat.GROUP_ROTATION), expectedRevision = 2)

        assertEquals(FinalizedMatchCloudSyncExecutionResult.Success(4), result)
        assertEquals(listOf("finalize_v2", "write_v2", "finalize_v2"), invoker.calls)
        assertEquals(listOf(2, 3), invoker.finalizeRevisions)
        assertEquals(listOf(2), invoker.writeRevisions)
        assertTrue(invoker.calls.none { it == "finalize" || it == "write" })
    }

    @Test
    fun inconsistentFinalizedFormatPairingOrTournamentPayloadFailsBeforeRpc() = runBlocking {
        val cases = listOf(
            finalizedPayloads(TournamentFormat.STANDARD, groupPairingKey = "A:B"),
            finalizedPayloads(TournamentFormat.GROUP_ROTATION, groupPairingKey = null),
            finalizedPayloads(TournamentFormat.STANDARD).copy(
                matches = finalizedPayloads(TournamentFormat.STANDARD).matches + FinalizedMatchUploadPayload(
                    id = "finalized-2",
                    tournamentId = "other-tournament",
                    matchNumber = 2,
                    matchDate = "2026-07-25",
                    mapName = "Bermuda",
                    status = "finalized",
                ),
            ),
        )

        cases.forEach { payloads ->
            val invoker = RecordingInvoker()
            val result = FinalizedMatchCloudSyncRpcRouter(invoker)
                .invoke(payloads, expectedRevision = 2)

            assertEquals(
                FinalizedMatchCloudSyncFailureCategory.VALIDATION,
                (result as FinalizedMatchCloudSyncExecutionResult.Failure).category,
            )
            assertTrue(invoker.calls.isEmpty())
        }
    }

    private fun draftPayloads(
        format: TournamentFormat,
        groupPairingKey: String? = if (format == TournamentFormat.GROUP_ROTATION) "A:C" else null,
    ) = DraftMatchCloudSyncPayloads(
        matches = listOf(
            DraftMatchUploadPayload(
                id = "draft-1",
                tournamentId = "tournament-1",
                matchNumber = 1,
                matchDate = "2026-07-24",
                mapName = "Bermuda",
                status = "draft",
                groupPairingKey = groupPairingKey,
            ),
        ),
        matchResults = emptyList(),
        tournamentFormat = format,
    )

    private fun finalizedPayloads(
        format: TournamentFormat,
        groupPairingKey: String? = if (format == TournamentFormat.GROUP_ROTATION) "A:C" else null,
    ) = FinalizedMatchCloudSyncPayloads(
        matches = listOf(
            FinalizedMatchUploadPayload(
                id = "finalized-1",
                tournamentId = "tournament-1",
                matchNumber = 1,
                matchDate = "2026-07-24",
                mapName = "Bermuda",
                status = "finalized",
                groupPairingKey = groupPairingKey,
            ),
        ),
        matchResults = emptyList(),
        tournamentFormat = format,
    )

    private class RecordingInvoker(
        private val writeResponse: RevisionWriteResponse = RevisionWriteResponse("success", 3),
        private val finalizeResponses: MutableList<RevisionWriteResponse> = mutableListOf(
            RevisionWriteResponse("success", 3),
        ),
    ) : MatchSnapshotRpcInvoker {
        val calls = mutableListOf<String>()
        val writeRevisions = mutableListOf<Int>()
        val finalizeRevisions = mutableListOf<Int>()

        override suspend fun <M, R> invokeWrite(
            parameters: MatchSnapshotWriteParameters<M, R>,
        ): RevisionWriteResponse {
            calls += "write"
            writeRevisions += parameters.expectedRevision
            return writeResponse
        }

        override suspend fun <M, R> invokeWriteGroupRotation(
            parameters: MatchSnapshotWriteParameters<M, R>,
        ): RevisionWriteResponse {
            calls += "write_v2"
            writeRevisions += parameters.expectedRevision
            return writeResponse
        }

        override suspend fun invokeFinalize(
            parameters: ProtectedMatchFinalizationParameters<
                FinalizedMatchUploadPayload,
                FinalizedMatchResultUploadPayload,
                >,
        ): RevisionWriteResponse {
            calls += "finalize"
            finalizeRevisions += parameters.expectedRevision
            return finalizeResponses.removeAt(0)
        }

        override suspend fun invokeFinalizeGroupRotation(
            parameters: ProtectedMatchFinalizationParameters<
                FinalizedMatchUploadPayload,
                FinalizedMatchResultUploadPayload,
                >,
        ): RevisionWriteResponse {
            calls += "finalize_v2"
            finalizeRevisions += parameters.expectedRevision
            return finalizeResponses.removeAt(0)
        }
    }
}
