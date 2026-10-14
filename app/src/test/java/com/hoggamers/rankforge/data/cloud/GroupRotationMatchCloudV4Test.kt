package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionProbe
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionReadiness
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionStatus
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingAssignment
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalSnapshot
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRotationMatchCloudV4Test {
    @Test
    fun draftPayloadUsesExactRpcKeysAndPreservesSparseUuidIdentity() {
        val request = draftRequest(
            persistence = persistence(
                lobbySlots = listOf(1, 5, 12),
                results = listOf(
                    participant(U1, MatchParticipationStatus.PARTICIPATED, placement = null, kills = 4),
                    participant(U2, MatchParticipationStatus.NO_SHOW, placement = null, kills = 0),
                ),
            ),
        )

        val parameters = (GroupRotationMatchCloudV4Mapper.mapDraft(tournament(), request)
            as GroupRotationV4MappingResult.Success).parameters
        val json = Json.encodeToString(parameters)
        val decoded = Json.parseToJsonElement(json).jsonObject

        assertEquals(
            setOf(
                "p_tournament_id",
                "p_match",
                "p_match_identity",
                "p_match_results",
                "p_expected_revision",
                "p_request_id",
            ),
            decoded.keys,
        )
        assertEquals(T, decoded.getValue("p_tournament_id").toString().trim('"'))
        assertEquals(3, parameters.matchIdentity.size)
        assertEquals(listOf(1, 5, 12), parameters.matchIdentity.map { it.lobbySlotNumber })
        assertEquals(listOf(U1, U2, U3), parameters.matchIdentity.map { it.teamUUID })
        assertEquals(null, parameters.matchResults.first().placement)
        assertEquals(4, parameters.matchResults.first().kills)
        assertTrue(json.contains("\"placement\":null"))
        assertFalse(json.contains("team_slot_id"))
    }

    @Test
    fun finalizationPayloadPreservesUuidResultsAndNoShowSemantics() {
        val request = finalRequest(
            persistence(
                lobbySlots = listOf(1, 5, 12),
                results = listOf(
                    participant(U1, MatchParticipationStatus.PARTICIPATED, placement = 1, kills = 4),
                    participant(U2, MatchParticipationStatus.PARTICIPATED, placement = 2, kills = 2),
                    participant(U3, MatchParticipationStatus.NO_SHOW, placement = null, kills = 0),
                ),
            ),
        )

        val parameters = (GroupRotationMatchCloudV4Mapper.mapFinalization(tournament(), request)
            as GroupRotationV4MappingResult.Success).parameters

        assertEquals(MATCH, parameters.matchId)
        assertEquals(listOf(U1, U2, U3), parameters.matchResults.map { it.teamUUID })
        assertEquals(listOf("PARTICIPATED", "PARTICIPATED", "NO_SHOW"), parameters.matchResults.map { it.participationStatus })
        assertEquals(listOf(1, 2, null), parameters.matchResults.map { it.placement })
        assertEquals(listOf(4, 2, 0), parameters.matchResults.map { it.kills })
        assertEquals(REQUEST, parameters.requestId)
    }

    @Test
    fun sameUuidMayAppearInDifferentPairingsWithoutChangingSelectedMatchIdentity() {
        val otherPairing = GroupPairing(TournamentGroup.B, TournamentGroup.C)
        val saved = savedIdentitySnapshot(
            assignments = listOf(
                GroupRotationPairingAssignment(T, PAIRING, 3, U1),
                GroupRotationPairingAssignment(T, PAIRING, 9, U2),
                GroupRotationPairingAssignment(T, otherPairing, 1, U1),
            ),
        )
        val persistence = persistence(lobbySlots = listOf(3, 9)).copy(
            identity = GroupRotationMatchIdentitySnapshot(
                tournamentId = T,
                pairing = PAIRING,
                teams = listOf(
                    matchTeam(3, U1),
                    matchTeam(9, U2),
                ),
            ),
        )
        val result = GroupRotationMatchCloudV4Mapper.mapDraft(
            tournament(),
            draftRequest(persistence, savedIdentitySnapshot = saved),
        )

        assertTrue(result is GroupRotationV4MappingResult.Success)
        assertEquals(listOf(U1, U2), (result as GroupRotationV4MappingResult.Success).parameters.matchIdentity.map { it.teamUUID })
    }

    @Test
    fun changedCurrentAssignmentDoesNotInvalidateImmutableMatchSnapshot() {
        val saved = savedIdentitySnapshot(
            assignments = listOf(
                GroupRotationPairingAssignment(T, PAIRING, 1, U1),
                GroupRotationPairingAssignment(T, PAIRING, 5, U2),
                GroupRotationPairingAssignment(T, PAIRING, 12, U3),
            ),
        )
        val changed = persistence(lobbySlots = listOf(1, 5, 12)).copy(
            identity = GroupRotationMatchIdentitySnapshot(
                tournamentId = T,
                pairing = PAIRING,
                teams = listOf(matchTeam(1, U1), matchTeam(5, U3), matchTeam(12, U2)),
            ),
        )

        val result = GroupRotationMatchCloudV4Mapper.mapDraft(
            tournament(),
            draftRequest(changed, savedIdentitySnapshot = saved),
        )

        assertTrue(result is GroupRotationV4MappingResult.Success)
        assertEquals(
            listOf(U1, U3, U2),
            (result as GroupRotationV4MappingResult.Success).parameters.matchIdentity.map { it.teamUUID },
        )
    }

    @Test
    fun unknownAndDuplicateUuidsAreRejected() {
        val unknown = "44444444-4444-4444-8444-444444444444"
        val unknownResult = GroupRotationMatchCloudV4Mapper.mapDraft(
            tournament(),
            draftRequest(
                persistence(lobbySlots = listOf(1, 5, 12)).copy(
                    identity = GroupRotationMatchIdentitySnapshot(
                        T,
                        PAIRING,
                        listOf(matchTeam(1, unknown), matchTeam(5, U2), matchTeam(12, U3)),
                    ),
                ),
                savedIdentitySnapshot = savedIdentitySnapshot(),
            ),
        )
        assertTrue(unknownResult is GroupRotationV4MappingResult.Invalid)
        assertTrue((unknownResult as GroupRotationV4MappingResult.Invalid).issues.any {
            it.code == GroupRotationMatchCloudV4IssueCode.TEAM_UUID_NOT_FOUND
        })

        val duplicateResult = GroupRotationMatchCloudV4Mapper.mapDraft(
            tournament(),
            draftRequest(
                persistence(
                    lobbySlots = listOf(1, 5, 12),
                    results = listOf(
                        participant(U1, MatchParticipationStatus.PARTICIPATED, 1, 1),
                        participant(U1, MatchParticipationStatus.PARTICIPATED, 2, 1),
                    ),
                ),
            ),
        )
        assertTrue(duplicateResult is GroupRotationV4MappingResult.Invalid)
        assertTrue((duplicateResult as GroupRotationV4MappingResult.Invalid).issues.any {
            it.code == GroupRotationMatchCloudV4IssueCode.DUPLICATE_PARTICIPANT_RESULT
        })
    }

    @Test
    fun currentRenamedRegistryDisplayNameDoesNotReplaceSavedUuid() {
        val renamed = savedIdentitySnapshot(
            teams = listOf(
                team(U1, "Phoenix Updated"),
                team(U2, "Bravo"),
                team(U3, "Charlie"),
            ),
        )
        val snapshot = persistence(lobbySlots = listOf(1, 5, 12)).copy(
            identity = GroupRotationMatchIdentitySnapshot(
                T,
                PAIRING,
                listOf(
                    matchTeam(1, U1, "Phoenix Updated"),
                    matchTeam(5, U2),
                    matchTeam(12, U3),
                ),
            ),
        )

        val parameters = (GroupRotationMatchCloudV4Mapper.mapDraft(
            tournament(),
            draftRequest(snapshot, savedIdentitySnapshot = renamed),
        ) as GroupRotationV4MappingResult.Success).parameters

        assertEquals(U1, parameters.matchIdentity.first().teamUUID)
        assertEquals("Phoenix Updated", renamed.teams.first().displayName)
        assertFalse(parameters.matchIdentity.toString().contains("Phoenix"))
    }

    @Test
    fun standardAndLegacyRouteCannotUseV4Mapper() {
        val standard = Tournament(
            id = T,
            name = "Standard",
            stageName = "",
            organizerContactNumber = "",
            status = TournamentStatus.DRAFT,
        )
        val result = GroupRotationMatchCloudV4Mapper.mapDraft(
            standard,
            draftRequest(persistence(lobbySlots = listOf(1, 5, 12))),
        )

        assertTrue(result is GroupRotationV4MappingResult.Invalid)
        assertTrue((result as GroupRotationV4MappingResult.Invalid).issues.any {
            it.code == GroupRotationMatchCloudV4IssueCode.NOT_GROUP_ROTATION
        })
    }

    @Test
    fun v4InvokerUsesOnlyTheVerifiedV4RpcNames() {
        assertEquals("write_match_snapshot_v4", WRITE_MATCH_SNAPSHOT_V4_RPC)
        assertEquals("finalize_match_snapshot_v4", FINALIZE_MATCH_SNAPSHOT_V4_RPC)
    }

    @Test
    fun remoteSelectsOnlyV4RpcsAndRetainsRequestIdAcrossRetry() = runTest {
        val invoker = RecordingInvoker()
        val remote = remote(invoker)
        val request = draftRequest(persistence(lobbySlots = listOf(1, 5, 12)))

        assertTrue(remote.writeDraft(request) is GroupRotationMatchCloudV4ExecutionResult.Success)
        assertTrue(remote.writeDraft(request) is GroupRotationMatchCloudV4ExecutionResult.Success)
        assertEquals(listOf("write", "write"), invoker.calls)
        assertEquals(listOf(REQUEST, REQUEST), invoker.writeParameters.map { it.requestId })

        val finalRequest = finalRequest(
            persistence(
                lobbySlots = listOf(1, 5, 12),
                results = listOf(
                    participant(U1, MatchParticipationStatus.PARTICIPATED, 1, 1),
                    participant(U2, MatchParticipationStatus.PARTICIPATED, 2, 1),
                    participant(U3, MatchParticipationStatus.NO_SHOW, null, 0),
                ),
            ),
        )
        assertTrue(remote.finalize(finalRequest) is GroupRotationMatchCloudV4ExecutionResult.Success)
        assertEquals(listOf("write", "write", "finalize"), invoker.calls)
        assertEquals(REQUEST, invoker.finalizeParameters.single().requestId)
    }

    @Test
    fun remoteClassifiesRevisionAuthAuthorizationValidationAndNetworkFailures() = runTest {
        val outcomes = listOf(
            GroupRotationV4RpcResponse("stale_write", 7) to GroupRotationMatchCloudV4FailureCategory.CONFLICT,
            GroupRotationV4RpcResponse("missing_revision") to GroupRotationMatchCloudV4FailureCategory.CONFLICT,
            GroupRotationV4RpcResponse("authentication_required") to GroupRotationMatchCloudV4FailureCategory.AUTHENTICATION,
            GroupRotationV4RpcResponse("unauthorized", 4) to GroupRotationMatchCloudV4FailureCategory.AUTHORIZATION,
            GroupRotationV4RpcResponse("validation_failure", 4) to GroupRotationMatchCloudV4FailureCategory.VALIDATION,
            GroupRotationV4RpcResponse("already_finalized", 4) to GroupRotationMatchCloudV4FailureCategory.CONFLICT,
            GroupRotationV4RpcResponse("unexpected", 4) to GroupRotationMatchCloudV4FailureCategory.UNKNOWN,
        )
        outcomes.forEach { (response, category) ->
            val result = remote(RecordingInvoker(response = response)).writeDraft(draftRequest(
                persistence(lobbySlots = listOf(1, 5, 12)),
                expectedRevision = 3,
            )) as GroupRotationMatchCloudV4ExecutionResult.Failure
            assertEquals(category, result.category)
            assertEquals(response.outcome, result.outcome)
        }

        val stale = remote(RecordingInvoker(GroupRotationV4RpcResponse("stale_write", 7)))
            .writeDraft(draftRequest(persistence(lobbySlots = listOf(1, 5, 12)), expectedRevision = 3))
                as GroupRotationMatchCloudV4ExecutionResult.Failure
        assertEquals(
            RevisionConflict.StaleWrite(CloudRevision(3), CloudRevision(7)),
            stale.conflict,
        )

        val network = remote(RecordingInvoker(failure = IOException("network timeout")))
            .writeDraft(draftRequest(persistence(lobbySlots = listOf(1, 5, 12))))
                as GroupRotationMatchCloudV4ExecutionResult.Failure
        assertEquals(GroupRotationMatchCloudV4FailureCategory.NETWORK, network.category)
    }

    @Test
    fun successWithoutPositiveRevisionIsNotReportedAsCloudSuccess() = runTest {
        val result = remote(RecordingInvoker(response = GroupRotationV4RpcResponse("success", null)))
            .writeDraft(draftRequest(persistence(lobbySlots = listOf(1, 5, 12))))

        assertEquals(
            GroupRotationMatchCloudV4ExecutionResult.Failure(
                category = GroupRotationMatchCloudV4FailureCategory.VALIDATION,
                outcome = "success",
                cloudRevision = null,
            ),
            result,
        )
    }

    @Test
    fun mapperRejectsUnauthenticatedTransportBeforeRpc() = runTest {
        val invoker = RecordingInvoker()
        val remote = SupabaseGroupRotationMatchCloudV4RemoteDataSource(
            config = SupabaseAuthConfig("https://supabase.test", "publishable-key"),
            sessionProbe = RecordingSessionProbe(
                SupabaseAuthSessionReadiness(
                    status = SupabaseAuthSessionStatus.NOT_AUTHENTICATED,
                    hasUsableSession = false,
                ),
            ),
            rpcInvoker = invoker,
        )

        val result = remote.writeDraft(draftRequest(persistence(lobbySlots = listOf(1, 5, 12))))

        assertEquals(
            GroupRotationMatchCloudV4FailureCategory.AUTHENTICATION,
            (result as GroupRotationMatchCloudV4ExecutionResult.Failure).category,
        )
        assertTrue(invoker.calls.isEmpty())
    }

    private fun tournament() = Tournament(
        id = T,
        name = "Rotation",
        stageName = "",
        organizerContactNumber = "",
        status = TournamentStatus.CONFIRMED,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = 3,
        selectedGroupPairings = listOf(PAIRING, GroupPairing(TournamentGroup.B, TournamentGroup.C)),
    )

    private fun persistence(
        lobbySlots: List<Int>,
        results: List<GroupRotationMatchParticipantResult> = emptyList(),
    ): GroupRotationMatchPersistenceSnapshot {
        val uuids = listOf(U1, U2, U3)
        val teams = lobbySlots.mapIndexed { index, lobby -> matchTeam(lobby, uuids[index]) }
        val assignments = lobbySlots.mapIndexed { index, lobby ->
            GroupRotationPairingAssignment(T, PAIRING, lobby, uuids[index])
        }
        return GroupRotationMatchPersistenceSnapshot(
            identity = GroupRotationMatchIdentitySnapshot(T, PAIRING, teams),
            participantResults = results,
        ).also {
            check(assignments.size == teams.size)
        }
    }

    private fun draftRequest(
        persistence: GroupRotationMatchPersistenceSnapshot,
        savedIdentitySnapshot: GroupRotationTeamIdentityLocalSnapshot = savedIdentitySnapshotFor(persistence),
        expectedRevision: Int = 4,
    ) = GroupRotationV4DraftWriteRequest(
        tournament = tournament(),
        tournamentId = T,
        matchId = MATCH,
        source = GroupRotationMatchCloudV4RequestSource(persistence, savedIdentitySnapshot),
        expectedRevision = expectedRevision,
        requestId = REQUEST,
    )

    private fun finalRequest(
        persistence: GroupRotationMatchPersistenceSnapshot,
        savedIdentitySnapshot: GroupRotationTeamIdentityLocalSnapshot = savedIdentitySnapshotFor(persistence),
        expectedRevision: Int = 4,
    ) = GroupRotationV4FinalizationRequest(
        tournament = tournament(),
        tournamentId = T,
        matchId = MATCH,
        source = GroupRotationMatchCloudV4RequestSource(persistence, savedIdentitySnapshot),
        expectedRevision = expectedRevision,
        requestId = REQUEST,
    )

    private fun savedIdentitySnapshot(
        teams: List<GroupRotationTeamIdentity> = listOf(
            team(U1, "Alpha"),
            team(U2, "Bravo"),
            team(U3, "Charlie"),
        ),
        assignments: List<GroupRotationPairingAssignment> = listOf(
            GroupRotationPairingAssignment(T, PAIRING, 1, U1),
            GroupRotationPairingAssignment(T, PAIRING, 5, U2),
            GroupRotationPairingAssignment(T, PAIRING, 12, U3),
        ),
    ) = GroupRotationTeamIdentityLocalSnapshot(teams, assignments)

    private fun savedIdentitySnapshotFor(
        persistence: GroupRotationMatchPersistenceSnapshot,
    ) = GroupRotationTeamIdentityLocalSnapshot(
        teams = persistence.identity.teams.map { team(it.teamUUID, it.displayName) },
        assignments = persistence.identity.teams.map { team ->
            GroupRotationPairingAssignment(
                tournamentId = T,
                pairingKey = persistence.identity.pairing,
                lobbySlotNumber = team.lobbySlotNumber,
                teamUUID = team.teamUUID,
            )
        },
    )

    private fun team(uuid: String, name: String) = GroupRotationTeamIdentity(
        tournamentId = T,
        teamUUID = uuid,
        displayName = name,
        normalizedName = name.lowercase(),
    )

    private fun matchTeam(lobby: Int, uuid: String, name: String = "Team $lobby") =
        GroupRotationMatchTeamIdentity(lobby, uuid, name)

    private fun participant(
        uuid: String,
        status: MatchParticipationStatus,
        placement: Int?,
        kills: Int,
    ) = GroupRotationMatchParticipantResult(uuid, status, placement, kills)

    private fun remote(
        invoker: RecordingInvoker,
        readiness: SupabaseAuthSessionReadiness = SupabaseAuthSessionReadiness(
            status = SupabaseAuthSessionStatus.AUTHENTICATED,
            hasUsableSession = true,
        ),
    ) = SupabaseGroupRotationMatchCloudV4RemoteDataSource(
        config = SupabaseAuthConfig("https://supabase.test", "publishable-key"),
        sessionProbe = RecordingSessionProbe(readiness),
        rpcInvoker = invoker,
    )

    private class RecordingSessionProbe(
        private val readiness: SupabaseAuthSessionReadiness,
    ) : SupabaseAuthSessionProbe {
        var initializationCalls = 0

        override suspend fun awaitInitialization() {
            initializationCalls++
        }

        override fun currentReadiness(): SupabaseAuthSessionReadiness = readiness
    }

    private class RecordingInvoker(
        private val response: GroupRotationV4RpcResponse = GroupRotationV4RpcResponse("success", 5),
        private val failure: Throwable? = null,
    ) : GroupRotationMatchCloudV4RpcInvoker {
        val calls = mutableListOf<String>()
        val writeParameters = mutableListOf<GroupRotationV4DraftWriteParameters>()
        val finalizeParameters = mutableListOf<GroupRotationV4FinalizationParameters>()

        override suspend fun write(parameters: GroupRotationV4DraftWriteParameters): GroupRotationV4RpcResponse {
            calls += "write"
            writeParameters += parameters
            failure?.let { throw it }
            return response
        }

        override suspend fun finalize(parameters: GroupRotationV4FinalizationParameters): GroupRotationV4RpcResponse {
            calls += "finalize"
            finalizeParameters += parameters
            failure?.let { throw it }
            return response
        }
    }

    private companion object {
        const val T = "11111111-1111-1111-8111-111111111111"
        const val MATCH = "22222222-2222-2222-8222-222222222222"
        const val REQUEST = "33333333-3333-3333-8333-333333333333"
        const val U1 = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val U2 = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        const val U3 = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
        val PAIRING = GroupPairing(TournamentGroup.A, TournamentGroup.C)
    }
}
