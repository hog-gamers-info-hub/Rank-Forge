package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionProbe
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionReadiness
import com.hoggamers.rankforge.data.auth.SupabaseAuthSessionStatus
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(SupabaseInternal::class)
class SupabaseTournamentRosterCloudReplacementRemoteDataSourceTest {
    private val clientProviders = mutableListOf<SupabaseClientProvider>()

    @After
    fun closeClientProviders() = runBlocking {
        clientProviders.forEach {
            it.client.auth.stopAutoRefreshForCurrentSession()
            it.client.close()
        }
    }

    @Test
    fun standardUsesLegacyRosterRpc() = runTest {
        val rpc = RecordingRpcInvoker(RevisionWriteResponse("success", 4))

        val result = remote(rpc).replace(payloads(TournamentFormat.STANDARD), expectedRevision = 2)

        assertEquals(TournamentRosterCloudReplacementRemoteResult.Success(4), result)
        assertEquals(1, rpc.legacyParameters.size)
        assertEquals(0, rpc.groupRotationParameters.size)
        assertEquals(2, rpc.legacyParameters.single().expectedRevision)
    }

    @Test
    fun standardWithoutUsableSessionFailsBeforeLegacyRpc() = runTest {
        val rpc = RecordingRpcInvoker(RevisionWriteResponse("success", 4))

        val result = remote(
            rpc = rpc,
            clientProvider = testClientProvider(hasSession = false),
            sessionProbe = RecordingSessionProbe(
                status = SupabaseAuthSessionStatus.AUTHENTICATED,
                hasUsableSession = true,
            ),
        ).replace(payloads(TournamentFormat.STANDARD), expectedRevision = 2)

        assertEquals(
            TournamentRosterCloudReplacementRemoteResult.Failure(CloudUploadFailureCategory.AUTHENTICATION),
            result,
        )
        assertEquals(0, rpc.legacyParameters.size)
    }

    @Test
    fun standardDoesNotRequireV2ReadinessStatusWhenSessionIsUsable() = runTest {
        val rpc = RecordingRpcInvoker(RevisionWriteResponse("success", 4))
        val probe = RecordingSessionProbe(
            status = SupabaseAuthSessionStatus.REFRESH_FAILURE,
            hasUsableSession = false,
        )

        val result = remote(
            rpc = rpc,
            clientProvider = testClientProvider(hasSession = true),
            sessionProbe = probe,
        ).replace(payloads(TournamentFormat.STANDARD), expectedRevision = 2)

        assertEquals(TournamentRosterCloudReplacementRemoteResult.Success(4), result)
        assertEquals(1, rpc.legacyParameters.size)
        assertEquals(0, probe.awaitInitializationCalls)
        assertEquals(0, probe.readinessCalls)
    }

    @Test
    fun groupRotationUsesV2RosterRpcWithoutMappingArgument() = runTest {
        val rpc = RecordingRpcInvoker(RevisionWriteResponse("success", 5))
        val probe = RecordingSessionProbe(
            status = SupabaseAuthSessionStatus.AUTHENTICATED,
            hasUsableSession = true,
        )

        val result = remote(
            rpc = rpc,
            sessionProbe = probe,
        ).replace(payloads(TournamentFormat.GROUP_ROTATION), expectedRevision = 4)

        assertEquals(TournamentRosterCloudReplacementRemoteResult.Success(5), result)
        assertEquals(0, rpc.legacyParameters.size)
        assertEquals(1, rpc.groupRotationParameters.size)
        assertEquals(4, rpc.groupRotationParameters.single().expectedRevision)
        assertEquals(1, probe.awaitInitializationCalls)
        assertEquals(1, probe.readinessCalls)
    }

    @Test
    fun groupRotationRequiresAuthenticatedUsableSessionAfterInitialization() = runTest {
        val rpc = RecordingRpcInvoker(RevisionWriteResponse("success", 5))
        val probe = RecordingSessionProbe(
            status = SupabaseAuthSessionStatus.REFRESH_FAILURE,
            hasUsableSession = true,
        )

        val result = remote(
            rpc = rpc,
            sessionProbe = probe,
        ).replace(payloads(TournamentFormat.GROUP_ROTATION), expectedRevision = 4)

        assertEquals(
            TournamentRosterCloudReplacementRemoteResult.Failure(CloudUploadFailureCategory.AUTHENTICATION),
            result,
        )
        assertEquals(0, rpc.groupRotationParameters.size)
        assertEquals(1, probe.awaitInitializationCalls)
        assertEquals(1, probe.readinessCalls)
    }

    @Test
    fun groupRotationOutcomesRemainDistinctBeforeDomainMapping() = runTest {
        val cases = listOf(
            RevisionWriteResponse("stale_write", 7) to
                TournamentRosterCloudReplacementRemoteResult.Conflict(
                    RevisionConflict.StaleWrite(
                        com.hoggamers.rankforge.domain.sync.CloudRevision(4),
                        com.hoggamers.rankforge.domain.sync.CloudRevision(7),
                    ),
                ),
            RevisionWriteResponse("missing_revision") to
                TournamentRosterCloudReplacementRemoteResult.Conflict(RevisionConflict.MissingRevision),
            RevisionWriteResponse("matches_exist") to
                TournamentRosterCloudReplacementRemoteResult.BlockedByExistingMatches,
            RevisionWriteResponse("authentication_required") to
                TournamentRosterCloudReplacementRemoteResult.Failure(CloudUploadFailureCategory.AUTHENTICATION),
            RevisionWriteResponse("unauthorized") to
                TournamentRosterCloudReplacementRemoteResult.Failure(CloudUploadFailureCategory.AUTHORIZATION),
            RevisionWriteResponse("validation_failure") to
                TournamentRosterCloudReplacementRemoteResult.Failure(CloudUploadFailureCategory.VALIDATION),
            RevisionWriteResponse("unexpected") to
                TournamentRosterCloudReplacementRemoteResult.Failure(CloudUploadFailureCategory.UNKNOWN),
        )

        cases.forEach { (response, expected) ->
            assertEquals(
                "outcome=${response.outcome}",
                expected,
                remote(RecordingRpcInvoker(response)).replace(
                    payloads(TournamentFormat.GROUP_ROTATION),
                    expectedRevision = 4,
                ),
            )
        }
    }

    private fun remote(
        rpc: RecordingRpcInvoker,
        sessionProbe: SupabaseAuthSessionProbe = ReadySessionProbe,
        clientProvider: SupabaseClientProvider = testClientProvider(hasSession = true),
    ) = SupabaseTournamentRosterCloudReplacementRemoteDataSource(
        config = SupabaseAuthConfig("https://supabase.test", "publishable-key"),
        clientProvider = clientProvider,
        sessionProbe = sessionProbe,
        rpcInvoker = rpc,
    )

    private fun testClientProvider(hasSession: Boolean): SupabaseClientProvider {
        val session = if (hasSession) {
            UserSession(
                accessToken = "test-access-token",
                refreshToken = "test-refresh-token",
                expiresIn = 3600,
                tokenType = "bearer",
            )
        } else {
            null
        }

        return SupabaseClientProvider(
            config = SupabaseAuthConfig("https://supabase.test", "publishable-key"),
            client = createSupabaseClient(
                supabaseUrl = "https://supabase.test",
                supabaseKey = "publishable-key",
            ) {
                install(Auth) {
                    autoLoadFromStorage = false
                    autoSaveToStorage = false
                    autoSetupPlatform = false
                    codeVerifierCache = MemoryCodeVerifierCache()
                    sessionManager = MemorySessionManager(session)
                }
            },
        ).also { provider ->
            clientProviders += provider
            provider.client.auth.setSessionStatus(
                if (hasSession) {
                    SessionStatus.Authenticated(
                        session = session!!,
                        source = SessionSource.Unknown,
                    )
                } else {
                    SessionStatus.NotAuthenticated()
                },
            )
        }
    }

    private fun payloads(format: TournamentFormat) = TournamentRosterCloudReplacementPayloads(
        tournamentId = "11111111-1111-1111-1111-111111111111",
        teamSlots = emptyList(),
        players = emptyList(),
        format = format,
    )

    private object ReadySessionProbe : SupabaseAuthSessionProbe {
        override suspend fun awaitInitialization() = Unit

        override fun currentReadiness() = SupabaseAuthSessionReadiness(
            status = SupabaseAuthSessionStatus.AUTHENTICATED,
            hasUsableSession = true,
        )
    }

    private class RecordingSessionProbe(
        private val status: SupabaseAuthSessionStatus,
        private val hasUsableSession: Boolean,
    ) : SupabaseAuthSessionProbe {
        var awaitInitializationCalls = 0
        var readinessCalls = 0

        override suspend fun awaitInitialization() {
            awaitInitializationCalls += 1
        }

        override fun currentReadiness(): SupabaseAuthSessionReadiness {
            readinessCalls += 1
            return SupabaseAuthSessionReadiness(
                status = status,
                hasUsableSession = hasUsableSession,
            )
        }
    }

    private class RecordingRpcInvoker(
        private val response: RevisionWriteResponse,
    ) : TournamentRosterSnapshotRpcInvoker {
        val legacyParameters = mutableListOf<TournamentRosterCloudReplacementParameters>()
        val groupRotationParameters = mutableListOf<TournamentRosterCloudReplacementV2Parameters>()

        override suspend fun invoke(
            parameters: TournamentRosterCloudReplacementParameters,
        ): RevisionWriteResponse {
            legacyParameters += parameters
            return response
        }

        override suspend fun invokeGroupRotation(
            parameters: TournamentRosterCloudReplacementV2Parameters,
        ): RevisionWriteResponse {
            groupRotationParameters += parameters
            return response
        }
    }
}
