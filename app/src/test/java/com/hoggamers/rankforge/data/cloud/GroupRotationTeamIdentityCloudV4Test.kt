package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionProbe
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionReadiness
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionStatus
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingAssignment
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityWriteResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamIdentityEntry
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityNormalizer
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRotationTeamIdentityCloudV4Test {
    @Test
    fun payloadUsesExactRpcKeysAndPreservesUuidIdentityFields() {
        val result = map(
            loaded(
                assignments = listOf(
                    assignment(PAIRING_AB, 5, U2),
                    assignment(PAIRING_AB, 1, U1),
                ),
            ),
        ) as GroupRotationTeamIdentityV4MappingResult.Success

        val json = Json.encodeToString(result.parameters)
        assertEquals(
            setOf(
                "p_tournament_id",
                "p_team_identities",
                "p_pairing_assignments",
                "p_expected_revision",
                "p_request_id",
            ),
            Json.parseToJsonElement(json).jsonObject.keys,
        )
        assertEquals(T, result.parameters.tournamentId)
        assertEquals(listOf(U1, U2), result.parameters.teamIdentities.map { it.teamUUID })
        assertEquals(listOf(1, 5), result.parameters.pairingAssignments.single().assignments.map { it.lobbySlotNumber })
        val identityJson = Json.parseToJsonElement(json).jsonObject
            .getValue("p_team_identities").jsonArray.first().jsonObject
        assertFalse("tournament_id" in identityJson.keys)
    }

    @Test
    fun renamedRegistryDisplayNamePreservesExactUuidAndNormalizedName() {
        val renamed = loaded(
            teams = listOf(team(U1, "Phoenix Updated"), team(U2, "Bravo")),
            assignments = listOf(assignment(PAIRING_AB, 4, U1)),
        )

        val parameters = (map(renamed) as GroupRotationTeamIdentityV4MappingResult.Success).parameters
        assertEquals(U1, parameters.teamIdentities.first { it.displayName == "Phoenix Updated" }.teamUUID)
        assertEquals("phoenix updated", parameters.teamIdentities.first { it.teamUUID == U1 }.normalizedName)
    }

    @Test
    fun assignmentPayloadCarriesClearedStateAndExplicitFalse() {
        val parameters = (map(
            loaded(
                assignments = listOf(
                    assignment(PAIRING_AB, 1, U1, nameCleared = true),
                    assignment(PAIRING_AB, 2, U2),
                ),
            ),
        ) as GroupRotationTeamIdentityV4MappingResult.Success).parameters

        val assignments = parameters.pairingAssignments.single().assignments
        assertTrue(assignments.first { it.teamUUID == U1 }.nameCleared)
        assertFalse(assignments.first { it.teamUUID == U2 }.nameCleared)
        val json = Json.encodeToString(parameters)
        val assignmentJson = Json.parseToJsonElement(json).jsonObject
            .getValue("p_pairing_assignments").jsonArray.first().jsonObject
            .getValue("assignments").jsonArray
        assertTrue("name_cleared" in assignmentJson[0].jsonObject.keys)
        assertTrue("name_cleared" in assignmentJson[1].jsonObject.keys)
    }

    @Test
    fun sparseLobbyPositionsArePreservedNumerically() {
        val parameters = (map(
            loaded(assignments = listOf(assignment(PAIRING_AB, 2, U1), assignment(PAIRING_AB, 11, U2))),
        ) as GroupRotationTeamIdentityV4MappingResult.Success).parameters

        assertEquals(listOf(2, 11), parameters.pairingAssignments.single().assignments.map { it.lobbySlotNumber })
    }

    @Test
    fun sameUuidMayAppearAtDifferentPositionsInDifferentPairings() {
        val parameters = (map(
            loaded(
                assignments = listOf(
                    assignment(PAIRING_AB, 2, U1),
                    assignment(PAIRING_AC, 9, U1),
                ),
            ),
            pairingsToReplace = listOf(PAIRING_AB, PAIRING_AC),
        ) as GroupRotationTeamIdentityV4MappingResult.Success).parameters

        assertEquals(
            listOf(U1, U1),
            parameters.pairingAssignments.flatMap { it.assignments }.map { it.teamUUID },
        )
        assertEquals(listOf(2, 9), parameters.pairingAssignments.flatMap { it.assignments }.map { it.lobbySlotNumber })
    }

    @Test
    fun omittedPairingIsNotEmittedAsAReplacementScope() {
        val parameters = (map(
            loaded(
                assignments = listOf(
                    assignment(PAIRING_AB, 1, U1),
                    assignment(PAIRING_AC, 2, U2),
                ),
            ),
            pairingsToReplace = listOf(PAIRING_AB),
        ) as GroupRotationTeamIdentityV4MappingResult.Success).parameters

        assertEquals(listOf("A:B"), parameters.pairingAssignments.map { it.pairingKey })
    }

    @Test
    fun explicitEmptyPairingIsEmittedAndCanClearOnlyThatPairing() {
        val parameters = (map(
            loaded(assignments = listOf(assignment(PAIRING_AC, 2, U2))),
            pairingsToReplace = listOf(PAIRING_AB),
        ) as GroupRotationTeamIdentityV4MappingResult.Success).parameters

        assertEquals(listOf("A:B"), parameters.pairingAssignments.map { it.pairingKey })
        assertTrue(parameters.pairingAssignments.single().assignments.isEmpty())
    }

    @Test
    fun duplicateLobbyWithinPairingIsRejected() {
        val result = map(
            loaded(assignments = listOf(assignment(PAIRING_AB, 1, U1), assignment(PAIRING_AB, 1, U2))),
        )

        assertInvalidWith(result, GroupRotationTeamIdentityV4IssueCode.DUPLICATE_LOBBY_SLOT)
    }

    @Test
    fun duplicateTeamWithinPairingIsRejected() {
        val result = map(
            loaded(assignments = listOf(assignment(PAIRING_AB, 1, U1), assignment(PAIRING_AB, 2, U1))),
        )

        assertInvalidWith(result, GroupRotationTeamIdentityV4IssueCode.DUPLICATE_TEAM_IN_PAIRING)
    }

    @Test
    fun unregisteredUuidIsRejected() {
        val result = map(
            loaded(assignments = listOf(assignment(PAIRING_AB, 1, U3))),
        )

        assertInvalidWith(result, GroupRotationTeamIdentityV4IssueCode.TEAM_UUID_NOT_FOUND)
    }

    @Test
    fun foreignTournamentIdentityIsRejected() {
        val result = map(
            loaded(teams = listOf(team(U1, "Alpha", tournamentId = OTHER_TOURNAMENT))),
        )

        assertInvalidWith(result, GroupRotationTeamIdentityV4IssueCode.TEAM_UUID_TOURNAMENT_MISMATCH)
    }

    @Test
    fun unselectedPairingAssignmentIsRejected() {
        val result = map(
            loaded(assignments = listOf(assignment(PAIRING_BC, 1, U1))),
        )

        assertInvalidWith(result, GroupRotationTeamIdentityV4IssueCode.ASSIGNMENT_UNSELECTED_PAIRING)
    }

    @Test
    fun duplicatePairingReplacementScopeIsRejected() {
        val result = map(
            loaded(),
            pairingsToReplace = listOf(PAIRING_AB, PAIRING_AB),
        )

        assertInvalidWith(result, GroupRotationTeamIdentityV4IssueCode.DUPLICATE_PAIRING_SCOPE)
    }

    @Test
    fun invalidLobbySlotIsRejected() {
        val result = map(
            loaded(assignments = listOf(assignment(PAIRING_AB, 13, U1))),
        )

        assertInvalidWith(result, GroupRotationTeamIdentityV4IssueCode.INVALID_LOBBY_SLOT)
    }

    @Test
    fun normalizedNameMustMatchTheSavedDisplayName() {
        val result = map(
            loaded(teams = listOf(team(U1, "Alpha", normalizedName = "beta"))),
        )

        assertInvalidWith(result, GroupRotationTeamIdentityV4IssueCode.INVALID_NORMALIZED_NAME)
    }

    @Test
    fun failedRoomReadCannotBecomeAnEmptyDestructiveSnapshot() = runTest {
        val invoker = RecordingInvoker()
        val remote = remote(
            repository = RecordingRepository(
                GroupRotationTeamIdentityReadResult.Invalid(emptyList()),
            ),
            invoker = invoker,
        )

        val result = remote.write(request()) as GroupRotationTeamIdentityV4ExecutionResult.Failure

        assertEquals(GroupRotationTeamIdentityV4FailureCategory.VALIDATION, result.category)
        assertEquals(0, invoker.calls)
    }

    @Test
    fun unsupportedRoomReadCannotBecomeAnEmptyDestructiveSnapshot() = runTest {
        val invoker = RecordingInvoker()
        val result = remote(
            repository = RecordingRepository(GroupRotationTeamIdentityReadResult.Unsupported),
            invoker = invoker,
        ).write(request()) as GroupRotationTeamIdentityV4ExecutionResult.Failure

        assertEquals(GroupRotationTeamIdentityV4FailureCategory.VALIDATION, result.category)
        assertEquals(0, invoker.calls)
    }

    @Test
    fun successRequiresPositiveConfirmedRevision() = runTest {
        val result = remote(
            invoker = RecordingInvoker(GroupRotationTeamIdentityV4RpcResponse("success", 0)),
        ).write(request()) as GroupRotationTeamIdentityV4ExecutionResult.Failure

        assertEquals(GroupRotationTeamIdentityV4FailureCategory.VALIDATION, result.category)
        assertEquals("success", result.outcome)
    }

    @Test
    fun staleWritePreservesRevisionConflictDetails() = runTest {
        val result = remote(
            invoker = RecordingInvoker(GroupRotationTeamIdentityV4RpcResponse("stale_write", 8)),
        ).write(request(expectedRevision = 7)) as GroupRotationTeamIdentityV4ExecutionResult.Failure

        assertEquals(GroupRotationTeamIdentityV4FailureCategory.CONFLICT, result.category)
        assertEquals(
            RevisionConflict.StaleWrite(CloudRevision(7), CloudRevision(8)),
            result.conflict,
        )
    }

    @Test
    fun authenticationFailureStopsBeforeRpc() = runTest {
        val invoker = RecordingInvoker()
        val result = remote(
            invoker = invoker,
            readiness = SupabaseAuthSessionReadiness(
                status = SupabaseAuthSessionStatus.NOT_AUTHENTICATED,
                hasUsableSession = false,
            ),
        ).write(request()) as GroupRotationTeamIdentityV4ExecutionResult.Failure

        assertEquals(GroupRotationTeamIdentityV4FailureCategory.AUTHENTICATION, result.category)
        assertEquals(0, invoker.calls)
    }

    @Test
    fun responseOutcomesMapToAuthAuthorizationValidationAndUnknown() = runTest {
        val expected = listOf(
            GroupRotationTeamIdentityV4RpcResponse("authentication_required") to
                GroupRotationTeamIdentityV4FailureCategory.AUTHENTICATION,
            GroupRotationTeamIdentityV4RpcResponse("unauthorized") to
                GroupRotationTeamIdentityV4FailureCategory.AUTHORIZATION,
            GroupRotationTeamIdentityV4RpcResponse("validation_failure") to
                GroupRotationTeamIdentityV4FailureCategory.VALIDATION,
            GroupRotationTeamIdentityV4RpcResponse("unexpected") to
                GroupRotationTeamIdentityV4FailureCategory.UNKNOWN,
        )

        expected.forEach { (response, category) ->
            val result = remote(invoker = RecordingInvoker(response)).write(request())
                as GroupRotationTeamIdentityV4ExecutionResult.Failure
            assertEquals(category, result.category)
            assertEquals(response.outcome, result.outcome)
        }
    }

    @Test
    fun networkFailureIsClassifiedWithoutQueueOrRoomMutation() = runTest {
        val result = remote(
            invoker = RecordingInvoker(failure = IOException("network timeout")),
        ).write(request()) as GroupRotationTeamIdentityV4ExecutionResult.Failure

        assertEquals(GroupRotationTeamIdentityV4FailureCategory.NETWORK, result.category)
    }

    @Test
    fun callerSuppliedRequestIdRemainsStableAcrossRetries() = runTest {
        val invoker = RecordingInvoker()
        val remote = remote(invoker = invoker)

        assertTrue(remote.write(request()) is GroupRotationTeamIdentityV4ExecutionResult.Success)
        assertTrue(remote.write(request()) is GroupRotationTeamIdentityV4ExecutionResult.Success)
        assertEquals(listOf(REQUEST, REQUEST), invoker.requestIds)
    }

    @Test
    fun standardTournamentCannotUseGroupRotationIdentityTransport() = runTest {
        val standard = Tournament(
            id = T,
            name = "Standard",
            stageName = "",
            organizerContactNumber = "",
            status = TournamentStatus.CONFIRMED,
        )
        val result = GroupRotationTeamIdentityCloudV4Mapper.map(
            GroupRotationTeamIdentityV4WriteRequest(
                tournament = standard,
                pairingsToReplace = emptyList(),
                expectedRevision = 1,
                requestId = REQUEST,
            ),
            loaded(),
        )

        assertInvalidWith(result, GroupRotationTeamIdentityV4IssueCode.NOT_GROUP_ROTATION)
    }

    @Test
    fun invalidExpectedRevisionAndRequestIdAreRejectedBeforeRpc() {
        val result = map(
            loaded(),
            expectedRevision = 0,
            requestId = "not-a-uuid",
        )

        assertTrue(result is GroupRotationTeamIdentityV4MappingResult.Invalid)
        val codes = (result as GroupRotationTeamIdentityV4MappingResult.Invalid).issues.map { it.code }
        assertTrue(GroupRotationTeamIdentityV4IssueCode.INVALID_EXPECTED_REVISION in codes)
        assertTrue(GroupRotationTeamIdentityV4IssueCode.INVALID_REQUEST_ID in codes)
    }

    private fun map(
        readResult: GroupRotationTeamIdentityReadResult,
        pairingsToReplace: List<GroupPairing> = listOf(PAIRING_AB),
        expectedRevision: Int = 4,
        requestId: String = REQUEST,
    ) = GroupRotationTeamIdentityCloudV4Mapper.map(
        request(
            pairingsToReplace = pairingsToReplace,
            expectedRevision = expectedRevision,
            requestId = requestId,
        ),
        readResult,
    )

    private fun request(
        pairingsToReplace: List<GroupPairing> = listOf(PAIRING_AB),
        expectedRevision: Int = 4,
        requestId: String = REQUEST,
        tournament: Tournament = tournament(),
    ) = GroupRotationTeamIdentityV4WriteRequest(
        tournament = tournament,
        pairingsToReplace = pairingsToReplace,
        expectedRevision = expectedRevision,
        requestId = requestId,
    )

    private fun loaded(
        teams: List<GroupRotationTeamIdentity> = listOf(team(U1, "Alpha"), team(U2, "Bravo")),
        assignments: List<GroupRotationPairingAssignment> = listOf(assignment(PAIRING_AB, 1, U1)),
    ) = GroupRotationTeamIdentityReadResult.Loaded(
        GroupRotationTeamIdentityLocalSnapshot(teams = teams, assignments = assignments),
    )

    private fun assignment(
        pairing: GroupPairing,
        lobbySlot: Int,
        teamUUID: String,
        nameCleared: Boolean = false,
    ) = GroupRotationPairingAssignment(T, pairing, lobbySlot, teamUUID, nameCleared)

    private fun team(
        teamUUID: String,
        displayName: String,
        tournamentId: String = T,
        normalizedName: String = GroupRotationTeamIdentityNormalizer().normalize(displayName),
    ) = GroupRotationTeamIdentity(tournamentId, teamUUID, displayName, normalizedName)

    private fun tournament() = Tournament(
        id = T,
        name = "Rotation",
        stageName = "",
        organizerContactNumber = "",
        status = TournamentStatus.CONFIRMED,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = 3,
        selectedGroupPairings = listOf(PAIRING_AB, PAIRING_AC),
    )

    private fun remote(
        repository: GroupRotationTeamIdentityLocalRepository = RecordingRepository(loaded()),
        invoker: RecordingInvoker = RecordingInvoker(),
        readiness: SupabaseAuthSessionReadiness = SupabaseAuthSessionReadiness(
            status = SupabaseAuthSessionStatus.AUTHENTICATED,
            hasUsableSession = true,
        ),
    ) = SupabaseGroupRotationTeamIdentityCloudV4RemoteDataSource(
        config = SupabaseAuthConfig("https://supabase.test", "publishable-key"),
        sessionProbe = RecordingSessionProbe(readiness),
        identityRepository = repository,
        rpcInvoker = invoker,
    )

    private fun assertInvalidWith(
        result: GroupRotationTeamIdentityV4MappingResult,
        expected: GroupRotationTeamIdentityV4IssueCode,
    ) {
        assertTrue(result is GroupRotationTeamIdentityV4MappingResult.Invalid)
        assertTrue(
            (result as GroupRotationTeamIdentityV4MappingResult.Invalid).issues.any { it.code == expected },
        )
    }

    private class RecordingRepository(
        private val result: GroupRotationTeamIdentityReadResult,
    ) : GroupRotationTeamIdentityLocalRepository {
        var reads = 0

        override suspend fun read(tournament: Tournament): GroupRotationTeamIdentityReadResult {
            reads++
            return result
        }

        override suspend fun savePairing(
            tournament: Tournament,
            pairingKey: GroupPairing,
            entries: List<GroupRotationPairingTeamIdentityEntry>,
        ): GroupRotationTeamIdentityWriteResult = GroupRotationTeamIdentityWriteResult.Unsupported

        override suspend fun renameTeam(
            tournament: Tournament,
            teamUUID: String,
            displayName: String,
        ): GroupRotationTeamIdentityWriteResult = GroupRotationTeamIdentityWriteResult.Unsupported
    }

    private class RecordingSessionProbe(
        private val readiness: SupabaseAuthSessionReadiness,
    ) : SupabaseAuthSessionProbe {
        override suspend fun awaitInitialization() = Unit

        override fun currentReadiness(): SupabaseAuthSessionReadiness = readiness
    }

    private class RecordingInvoker(
        private val response: GroupRotationTeamIdentityV4RpcResponse =
            GroupRotationTeamIdentityV4RpcResponse("success", 5),
        private val failure: Throwable? = null,
    ) : GroupRotationTeamIdentityCloudV4RpcInvoker {
        var calls = 0
        val requestIds = mutableListOf<String>()

        override suspend fun write(
            parameters: GroupRotationTeamIdentityV4WriteParameters,
        ): GroupRotationTeamIdentityV4RpcResponse {
            calls++
            requestIds += parameters.requestId
            failure?.let { throw it }
            return response
        }
    }

    private companion object {
        const val T = "11111111-1111-1111-8111-111111111111"
        const val OTHER_TOURNAMENT = "99999999-9999-4999-8999-999999999999"
        const val REQUEST = "33333333-3333-3333-8333-333333333333"
        const val U1 = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val U2 = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        const val U3 = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
        val PAIRING_AB = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        val PAIRING_AC = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        val PAIRING_BC = GroupPairing(TournamentGroup.B, TournamentGroup.C)
    }
}
