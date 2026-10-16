package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientAuthSessionProbe
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncRepository
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentityPersistenceRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.NoOpGroupRotationMatchIdentityPersistenceRepository
import com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository
import com.hoggamers.rankforge.domain.tournament.SyncDraftMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.testQueueRecorder
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(SupabaseInternal::class)
class SupabaseGroupRotationCloudMatchProvisionerTest {
    private val providers = mutableListOf<SupabaseClientProvider>()

    @After
    fun closeClients() = runBlocking {
        providers.forEach { provider ->
            provider.client.auth.stopAutoRefreshForCurrentSession()
            provider.client.close()
        }
    }

    @Test
    fun standardMissingDraftParentUsesRevisionAwareDraftSyncAndRereadsParent() = runTest {
        val match = standardMatch()
        val repository = repositoryWith(standardTournament(), match)
        val cloudId = requireNotNull(MatchCloudIdentity.matchId(TOURNAMENT_ID, match.id))
        val server = MatchesMockEngine()
        val fixture = provisioner(repository, server) {
            server.put(standardRow(cloudId))
        }

        val result = fixture.provisioner.ensureParent(
            tournamentId = TOURNAMENT_ID,
            matchId = match.id,
            expectedOwnerUserId = OWNER_ID,
        )

        assertEquals(GroupRotationCloudMatchProvisionResult.Ensured, result)
        assertEquals(1, fixture.draftSyncRepository.syncCount)
        assertEquals(0, server.insertCount)
        assertEquals(listOf(cloudId, cloudId), server.selectedIds)
    }

    @Test
    fun standardDraftSyncSuccessWithoutExpectedParentFailsClosed() = runTest {
        val match = standardMatch()
        val repository = repositoryWith(standardTournament(), match)
        val cloudId = requireNotNull(MatchCloudIdentity.matchId(TOURNAMENT_ID, match.id))
        val server = MatchesMockEngine()
        val fixture = provisioner(repository, server)

        val result = fixture.provisioner.ensureParent(TOURNAMENT_ID, match.id, OWNER_ID)

        assertEquals(GroupRotationCloudMatchProvisionResult.ValidationFailure, result)
        assertEquals(1, fixture.draftSyncRepository.syncCount)
        assertEquals(0, server.insertCount)
        assertEquals(listOf(cloudId, cloudId), server.selectedIds)
    }

    @Test
    fun standardExistingDraftParentIsAcceptedWithoutWrite() = runTest {
        val match = standardMatch()
        val repository = repositoryWith(standardTournament(), match)
        val cloudId = requireNotNull(MatchCloudIdentity.matchId(TOURNAMENT_ID, match.id))
        val server = MatchesMockEngine(existingRows = mapOf(cloudId to standardRow(cloudId)))

        val fixture = provisioner(repository, server)
        val result = fixture.provisioner.ensureParent(
            TOURNAMENT_ID,
            match.id,
            OWNER_ID,
        )

        assertEquals(GroupRotationCloudMatchProvisionResult.Ensured, result)
        assertEquals(0, fixture.draftSyncRepository.syncCount)
        assertEquals(0, server.insertCount)
        assertEquals(listOf(cloudId), server.selectedIds)
    }

    @Test
    fun standardExistingFinalizedParentIsAcceptedWithoutDowngrade() = runTest {
        val match = standardMatch()
        val repository = repositoryWith(standardTournament(), match)
        val cloudId = requireNotNull(MatchCloudIdentity.matchId(TOURNAMENT_ID, match.id))
        val server = MatchesMockEngine(
            existingRows = mapOf(cloudId to standardRow(cloudId, status = "finalized")),
        )

        val fixture = provisioner(repository, server)
        val result = fixture.provisioner.ensureParent(
            TOURNAMENT_ID,
            match.id,
            OWNER_ID,
        )

        assertEquals(GroupRotationCloudMatchProvisionResult.Ensured, result)
        assertEquals(0, fixture.draftSyncRepository.syncCount)
        assertEquals(0, server.insertCount)
    }

    @Test
    fun standardExistingMismatchedParentFailsValidationWithoutWrite() = runTest {
        val match = standardMatch()
        val repository = repositoryWith(standardTournament(), match)
        val cloudId = requireNotNull(MatchCloudIdentity.matchId(TOURNAMENT_ID, match.id))
        val server = MatchesMockEngine(
            existingRows = mapOf(cloudId to standardRow(cloudId, mapName = "Wrong map")),
        )

        val fixture = provisioner(repository, server)
        val result = fixture.provisioner.ensureParent(
            TOURNAMENT_ID,
            match.id,
            OWNER_ID,
        )

        assertEquals(GroupRotationCloudMatchProvisionResult.ValidationFailure, result)
        assertEquals(0, fixture.draftSyncRepository.syncCount)
        assertEquals(0, server.insertCount)
    }

    @Test
    fun standardMissingDraftParentMapsDraftSyncFailuresWithoutCloudWrite() = runTest {
        val failures = listOf(
            DraftMatchCloudSyncResult.AuthenticationRequired to
                GroupRotationCloudMatchProvisionResult.AuthenticationRequired,
            DraftMatchCloudSyncResult.AuthorizationFailure to
                GroupRotationCloudMatchProvisionResult.AuthorizationFailure,
            DraftMatchCloudSyncResult.NetworkFailure to
                GroupRotationCloudMatchProvisionResult.NetworkFailure,
            DraftMatchCloudSyncResult.ValidationFailure to
                GroupRotationCloudMatchProvisionResult.ValidationFailure,
        )

        failures.forEach { (draftResult, expectedResult) ->
            val match = standardMatch()
            val repository = repositoryWith(standardTournament(), match)
            val cloudId = requireNotNull(MatchCloudIdentity.matchId(TOURNAMENT_ID, match.id))
            val server = MatchesMockEngine()
            val fixture = provisioner(repository, server, draftSyncResult = draftResult)

            val result = fixture.provisioner.ensureParent(TOURNAMENT_ID, match.id, OWNER_ID)

            assertEquals(expectedResult, result)
            assertEquals(1, fixture.draftSyncRepository.syncCount)
            assertEquals(0, server.insertCount)
            assertEquals(listOf(cloudId), server.selectedIds)
        }
    }

    @Test
    fun standardConflictFailsClosedWithoutRawInsert() = runTest {
        val repository = repositoryWith(standardTournament(), standardMatch())
        val server = MatchesMockEngine()
        val fixture = provisioner(
            repository,
            server,
            draftSyncResult = DraftMatchCloudSyncResult.Conflict(RevisionConflict.MissingRevision),
        )

        val result = fixture.provisioner.ensureParent(TOURNAMENT_ID, MATCH_ID, OWNER_ID)

        assertEquals(GroupRotationCloudMatchProvisionResult.ValidationFailure, result)
        assertEquals(1, fixture.draftSyncRepository.syncCount)
        assertEquals(0, server.insertCount)
    }

    @Test
    fun standardLocalFinalizedMatchWithMissingParentFailsWithoutDraftSync() = runTest {
        val match = standardMatch().copy(status = MatchStatus.FINALIZED)
        val repository = repositoryWith(standardTournament(), match)
        val cloudId = requireNotNull(MatchCloudIdentity.matchId(TOURNAMENT_ID, match.id))
        val server = MatchesMockEngine()
        val fixture = provisioner(repository, server)

        val result = fixture.provisioner.ensureParent(TOURNAMENT_ID, match.id, OWNER_ID)

        assertEquals(GroupRotationCloudMatchProvisionResult.ValidationFailure, result)
        assertEquals(0, fixture.draftSyncRepository.syncCount)
        assertEquals(0, server.insertCount)
        assertEquals(listOf(cloudId), server.selectedIds)
    }

    @Test
    fun standardMissingLocalMatchFailsClosedWithoutCloudWrite() = runTest {
        val repository = repositoryWith(standardTournament(), match = null)
        val server = MatchesMockEngine()

        val fixture = provisioner(repository, server)
        val result = fixture.provisioner.ensureParent(
            TOURNAMENT_ID,
            MATCH_ID,
            OWNER_ID,
        )

        assertEquals(GroupRotationCloudMatchProvisionResult.ValidationFailure, result)
        assertEquals(0, fixture.draftSyncRepository.syncCount)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun groupRotationStillUsesExistingUuidBackedParentProvisioning() = runTest {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        val match = groupRotationMatch(pairing)
        val tournament = groupRotationTournament(pairing)
        val repository = repositoryWith(tournament, match, includeGroupMapping = true)
        val server = MatchesMockEngine(
            existingRows = mapOf(match.id to groupRotationRow(match.id, pairing.canonicalKey)),
        )

        val fixture = provisioner(repository, server, UuidBackedIdentityRepository)
        val result = fixture.provisioner.ensureParent(
            tournament.id,
            match.id,
            OWNER_ID,
        )

        assertEquals(GroupRotationCloudMatchProvisionResult.Ensured, result)
        assertEquals(0, fixture.draftSyncRepository.syncCount)
        assertEquals(0, server.insertCount)
        assertEquals(listOf(match.id), server.selectedIds)
    }

    @Test
    fun standardResolutionUsesDeterministicCloudId() = runTest {
        val match = standardMatch()
        val repository = repositoryWith(standardTournament(), match)
        val server = MatchesMockEngine()
        val fixture = provisioner(repository, server)

        assertEquals(
            MatchCloudIdentity.matchId(TOURNAMENT_ID, MATCH_ID),
            fixture.provisioner.resolveCloudMatchId(TOURNAMENT_ID, MATCH_ID, OWNER_ID),
        )
        assertEquals(0, fixture.draftSyncRepository.syncCount)
        assertEquals(0, server.requestCount)
    }

    private fun provisioner(
        repository: InMemoryTournamentRepository,
        server: MatchesMockEngine,
        identityRepository: GroupRotationMatchIdentityPersistenceRepository =
            NoOpGroupRotationMatchIdentityPersistenceRepository,
        draftSyncResult: DraftMatchCloudSyncResult = DraftMatchCloudSyncResult.Success,
        onDraftSync: () -> Unit = {},
    ): ProvisionerFixture {
        val draftSyncRepository = RecordingDraftCloudSyncRepository(draftSyncResult, onDraftSync)
        val syncDraftMatchesUseCase = SyncDraftMatchesUseCase(
            tournamentRepository = repository,
            authRepository = SignedInTournamentTestAuthRepository(OWNER_ID),
            cloudSyncRepository = draftSyncRepository,
            queueRecorder = testQueueRecorder(),
        )
        val session = UserSession(
            accessToken = "test-access-token",
            refreshToken = "test-refresh-token",
            expiresIn = 3600,
            tokenType = "bearer",
            user = UserInfo(aud = "authenticated", id = OWNER_ID),
        )
        val provider = SupabaseClientProvider(
            config = SupabaseAuthConfig(SUPABASE_URL, SUPABASE_KEY),
            client = createSupabaseClient(
                supabaseUrl = SUPABASE_URL,
                supabaseKey = SUPABASE_KEY,
            ) {
                httpEngine = server.engine
                defaultLogLevel = LogLevel.NONE
                install(Auth) {
                    autoLoadFromStorage = false
                    autoSaveToStorage = false
                    autoSetupPlatform = false
                    codeVerifierCache = MemoryCodeVerifierCache()
                    sessionManager = MemorySessionManager(session)
                }
                install(Postgrest)
            },
        ).also { clientProvider ->
            clientProvider.client.auth.setSessionStatus(
                SessionStatus.Authenticated(session, SessionSource.Unknown),
            )
        }
        providers += provider
        return ProvisionerFixture(
            provisioner = SupabaseGroupRotationCloudMatchProvisioner(
                config = SupabaseAuthConfig(SUPABASE_URL, SUPABASE_KEY),
                sessionProbe = SupabaseClientAuthSessionProbe(provider),
                clientProvider = provider,
                tournamentRepository = repository,
                matchIdentityRepository = identityRepository,
                syncDraftMatchesUseCase = syncDraftMatchesUseCase,
            ),
            draftSyncRepository = draftSyncRepository,
        )
    }

    private data class ProvisionerFixture(
        val provisioner: SupabaseGroupRotationCloudMatchProvisioner,
        val draftSyncRepository: RecordingDraftCloudSyncRepository,
    )

    private class RecordingDraftCloudSyncRepository(
        private val result: DraftMatchCloudSyncResult,
        private val onSync: () -> Unit,
    ) : DraftMatchCloudSyncRepository {
        var syncCount: Int = 0
            private set

        override suspend fun sync(snapshot: DraftMatchCloudSyncSnapshot): DraftMatchCloudSyncResult {
            syncCount += 1
            onSync()
            return result
        }
    }

    private suspend fun repositoryWith(
        tournament: Tournament,
        match: Match?,
        includeGroupMapping: Boolean = false,
    ): InMemoryTournamentRepository {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament)
        repository.saveTeamNames(
            tournament.id,
            (1..if (tournament.format == TournamentFormat.STANDARD) 12 else 18)
                .associateWith { slotNumber -> "Team $slotNumber" },
        )
        if (includeGroupMapping) {
            val pairing = requireNotNull(match?.groupPairing)
            repository.replaceGroupRotationPairingLobbySlots(
                (1..12).map { lobbySlotNumber ->
                    GroupRotationPairingLobbySlot(
                        tournamentId = tournament.id,
                        pairing = pairing,
                        lobbySlotNumber = lobbySlotNumber,
                        teamSlotNumber = lobbySlotNumber,
                    )
                },
            )
        }
        match?.let { created ->
            assertTrue(
                repository.createDraftMatch(created) is
                    com.hoggamers.rankforge.domain.tournament.CreateMatchRepositoryResult.Created,
            )
        }
        return repository
    }

    private fun standardTournament() = Tournament(
        id = TOURNAMENT_ID,
        name = "Standard",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        ownerUserId = OWNER_ID,
    )

    private fun groupRotationTournament(pairing: GroupPairing) = Tournament(
        id = TOURNAMENT_ID,
        name = "Group Rotation",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        ownerUserId = OWNER_ID,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = 3,
        selectedGroupPairings = listOf(pairing),
    )

    private fun standardMatch() = Match(
        id = MATCH_ID,
        tournamentId = TOURNAMENT_ID,
        matchNumber = 1,
        date = MATCH_DATE,
        mapName = MAP_NAME,
        status = MatchStatus.DRAFT,
    )

    private fun groupRotationMatch(pairing: GroupPairing) = Match(
        id = UUID_MATCH_ID,
        tournamentId = TOURNAMENT_ID,
        matchNumber = 1,
        date = MATCH_DATE,
        mapName = MAP_NAME,
        status = MatchStatus.DRAFT,
        groupPairing = pairing,
    )

    private fun standardRow(
        cloudId: String,
        status: String = "draft",
        mapName: String = MAP_NAME,
    ) = row(
        id = cloudId,
        status = status,
        mapName = mapName,
        groupPairingKey = null,
    )

    private fun groupRotationRow(cloudId: String, pairingKey: String) = row(
        id = cloudId,
        status = "draft",
        mapName = MAP_NAME,
        groupPairingKey = pairingKey,
    )

    private fun row(
        id: String,
        status: String,
        mapName: String,
        groupPairingKey: String?,
    ): String = """
        {
          "id":"$id",
          "tournament_id":"$TOURNAMENT_ID",
          "match_number":1,
          "match_date":"$MATCH_DATE",
          "map_name":"$mapName",
          "status":"$status",
          "group_pairing_key":${groupPairingKey?.let { "\"$it\"" } ?: "null"}
        }
    """.trimIndent()

    private class MatchesMockEngine(
        existingRows: Map<String, String> = emptyMap(),
        private val insertedRow: String? = null,
    ) {
        private val rowsById = existingRows.toMutableMap()
        var requestCount: Int = 0
            private set
        var insertCount: Int = 0
            private set
        val selectedIds = mutableListOf<String?>()

        fun put(row: String) {
            rowsById[extractId(row)] = row
        }

        val engine = MockEngine { request ->
            requestCount += 1
            when (request.method) {
                HttpMethod.Get -> {
                    val requestedId = request.url.parameters["id"]?.removePrefix("eq.")
                    selectedIds += requestedId
                    response(rowsById[requestedId]?.let { "[$it]" } ?: "[]")
                }
                HttpMethod.Post -> {
                    insertCount += 1
                    insertedRow?.let { rowsById[extractId(it)] = it }
                    response("[]", HttpStatusCode.Created)
                }
                else -> response("[]", HttpStatusCode.MethodNotAllowed)
            }
        }

        private fun MockRequestHandleScope.response(
            body: String,
            status: HttpStatusCode = HttpStatusCode.OK,
        ) = respond(
            content = ByteReadChannel(body),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

        private fun extractId(row: String): String = row.substringAfter("\"id\":\"").substringBefore("\"")
    }

    private object UuidBackedIdentityRepository : GroupRotationMatchIdentityPersistenceRepository {
        override suspend fun save(
            tournament: Tournament,
            matchId: String,
            snapshot: GroupRotationMatchIdentitySnapshot,
            participantResults: List<GroupRotationMatchParticipantResult>,
        ): GroupRotationMatchPersistenceResult = error("unused")

        override suspend fun read(
            tournament: Tournament,
            matchId: String,
        ): GroupRotationMatchPersistenceResult = GroupRotationMatchPersistenceResult.Loaded(
            GroupRotationMatchPersistenceSnapshot(
                identity = GroupRotationMatchIdentitySnapshot(
                    tournamentId = tournament.id,
                    pairing = GroupPairing(TournamentGroup.A, TournamentGroup.B),
                    teams = emptyList<GroupRotationMatchTeamIdentity>(),
                ),
                participantResults = emptyList(),
            ),
        )
    }

    private companion object {
        const val SUPABASE_URL = "https://supabase.test"
        const val SUPABASE_KEY = "publishable-key"
        const val OWNER_ID = "owner-user-id"
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        const val MATCH_ID = "22222222-2222-2222-2222-222222222222"
        const val UUID_MATCH_ID = "33333333-3333-3333-3333-333333333333"
        const val MAP_NAME = "Bermuda"
        val MATCH_DATE: LocalDate = LocalDate.of(2026, 10, 17)
    }
}
