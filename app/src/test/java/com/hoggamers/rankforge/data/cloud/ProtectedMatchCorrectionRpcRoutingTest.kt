package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ProtectedMatchCorrectionRequest
import com.hoggamers.rankforge.domain.tournament.ProtectedMatchCorrectionResult
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedMatchCorrectionRpcRoutingTest {
    @Test
    fun standardCorrectionCallsOnlyLegacyRpc() = runBlocking {
        val invoker = RecordingInvoker()

        invokeProtectedMatchCorrectionRpc(
            request = request(TournamentFormat.STANDARD),
            parameters = parameters(),
            rpcInvoker = invoker,
        )

        assertEquals(listOf("standard"), invoker.calls)
    }

    @Test
    fun groupRotationCorrectionCallsOnlyV2RpcWithoutFallback() = runBlocking {
        val invoker = RecordingInvoker(
            groupRotationResponse = RevisionWriteResponse("validation_failure"),
        )

        val response = invokeProtectedMatchCorrectionRpc(
            request = request(TournamentFormat.GROUP_ROTATION),
            parameters = parameters(),
            rpcInvoker = invoker,
        )

        assertEquals(RevisionWriteResponse("validation_failure"), response)
        assertEquals(listOf("group_rotation"), invoker.calls)
        assertTrue("standard" !in invoker.calls)
    }

    @Test
    fun existingCorrectionOutcomeMappingRemainsUnchanged() {
        val outcomes = listOf(
            RevisionWriteResponse("success", 7) to ProtectedMatchCorrectionResult.Success(7),
            RevisionWriteResponse("already_corrected", 7) to ProtectedMatchCorrectionResult.AlreadyCorrected(7),
            RevisionWriteResponse("stale_write", 9) to ProtectedMatchCorrectionResult.Conflict(
                RevisionConflict.StaleWrite(CloudRevision(5), CloudRevision(9)),
            ),
            RevisionWriteResponse("missing_revision") to ProtectedMatchCorrectionResult.Conflict(
                RevisionConflict.MissingRevision,
            ),
            RevisionWriteResponse("authentication_required") to ProtectedMatchCorrectionResult.AuthenticationRequired,
            RevisionWriteResponse("unauthorized") to ProtectedMatchCorrectionResult.AuthorizationFailure,
            RevisionWriteResponse("match_not_finalized") to ProtectedMatchCorrectionResult.MatchNotFinalized,
            RevisionWriteResponse("unexpected") to ProtectedMatchCorrectionResult.ValidationFailure,
        )

        outcomes.forEach { (response, expected) ->
            assertEquals(expected, response.toProtectedMatchCorrectionResult(expectedRevision = 5))
        }
    }

    private fun request(format: TournamentFormat) = ProtectedMatchCorrectionRequest(
        tournament = Tournament(
            id = "tournament-id",
            name = "Cup",
            stageName = "Organizer",
            organizerContactNumber = "123",
            status = TournamentStatus.CONFIRMED,
            format = format,
            ownerUserId = "owner-id",
            groupCount = if (format == TournamentFormat.GROUP_ROTATION) 4 else null,
            selectedGroupPairings = if (format == TournamentFormat.GROUP_ROTATION) {
                listOf(GroupPairing(TournamentGroup.A, TournamentGroup.C))
            } else {
                emptyList()
            },
        ),
        match = Match(
            id = "match-id",
            tournamentId = "tournament-id",
            matchNumber = 1,
            date = LocalDate.of(2026, 7, 24),
            mapName = "Bermuda",
            status = MatchStatus.FINALIZED,
        ),
        placements = emptyList(),
        kills = emptyList(),
        expectedRevision = 5,
    )

    private fun parameters() = ProtectedMatchCorrectionParameters(
        tournamentId = "tournament-id",
        matchId = "match-id",
        matchResults = emptyList(),
        expectedRevision = 5,
    )

    private class RecordingInvoker(
        private val standardResponse: RevisionWriteResponse = RevisionWriteResponse("success", 7),
        private val groupRotationResponse: RevisionWriteResponse = RevisionWriteResponse("success", 7),
    ) : ProtectedMatchCorrectionRpcInvoker {
        val calls = mutableListOf<String>()

        override suspend fun invokeStandard(
            parameters: ProtectedMatchCorrectionParameters,
        ): RevisionWriteResponse {
            calls += "standard"
            return standardResponse
        }

        override suspend fun invokeGroupRotation(
            parameters: ProtectedMatchCorrectionParameters,
        ): RevisionWriteResponse {
            calls += "group_rotation"
            return groupRotationResponse
        }
    }
}
