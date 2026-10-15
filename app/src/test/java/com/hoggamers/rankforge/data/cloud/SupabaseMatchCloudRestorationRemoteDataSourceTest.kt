package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationFailureCategory
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationRemoteResult
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
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(SupabaseInternal::class)
class SupabaseMatchCloudRestorationRemoteDataSourceTest {
    private val providers = mutableListOf<SupabaseClientProvider>()

    @After
    fun closeClients() = runBlocking {
        providers.forEach { provider ->
            provider.client.auth.stopAutoRefreshForCurrentSession()
            provider.client.close()
        }
    }

    @Test
    fun equalRevisionParentsAreAccepted() = runTest {
        val reader = RecordingReader(listOf(parent(4), parent(4)))

        val result = remote(reader).readOwnedMatches(TOURNAMENT_ID)

        assertTrue(result is MatchCloudRestorationRemoteResult.Success)
        assertEquals(4, (result as MatchCloudRestorationRemoteResult.Success).value.cloudRevision)
        assertEquals(2, reader.parentReads)
        assertEquals(listOf(4), reader.childReadRevisions)
    }

    @Test
    fun firstDriftDiscardsChildrenAndRetriesWholeSequence() = runTest {
        val reader = RecordingReader(listOf(parent(1), parent(2), parent(3), parent(3)))

        val result = remote(reader).readOwnedMatches(TOURNAMENT_ID)

        assertTrue(result is MatchCloudRestorationRemoteResult.Success)
        val payloads = (result as MatchCloudRestorationRemoteResult.Success).value
        assertEquals(3, payloads.cloudRevision)
        assertEquals("match-3", payloads.matches.single().id)
        assertEquals(4, reader.parentReads)
        assertEquals(listOf(1, 3), reader.childReadRevisions)
    }

    @Test
    fun repeatedDriftReturnsNetworkFailureAfterOneRetry() = runTest {
        val reader = RecordingReader(listOf(parent(1), parent(2), parent(3), parent(4)))

        val result = remote(reader).readOwnedMatches(TOURNAMENT_ID)

        assertEquals(
            MatchCloudRestorationRemoteResult.Failure(MatchCloudRestorationFailureCategory.NETWORK),
            result,
        )
        assertEquals(4, reader.parentReads)
        assertEquals(listOf(1, 3), reader.childReadRevisions)
    }

    @Test
    fun parentDisappearanceAtFenceFailsClosed() = runTest {
        val reader = RecordingReader(listOf(parent(1), null))

        val result = remote(reader).readOwnedMatches(TOURNAMENT_ID)

        assertEquals(
            MatchCloudRestorationRemoteResult.Failure(MatchCloudRestorationFailureCategory.AUTHORIZATION),
            result,
        )
        assertEquals(listOf(1), reader.childReadRevisions)
    }

    private fun remote(reader: RecordingReader): SupabaseMatchCloudRestorationRemoteDataSource {
        val provider = testClientProvider()
        providers += provider
        return SupabaseMatchCloudRestorationRemoteDataSource(
            config = SupabaseAuthConfig("https://supabase.test", "publishable-key"),
            clientProvider = provider,
            reader = reader,
        )
    }

    private fun testClientProvider(): SupabaseClientProvider {
        val session = UserSession(
            accessToken = "test-access-token",
            refreshToken = "test-refresh-token",
            expiresIn = 3600,
            tokenType = "bearer",
        )
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
            provider.client.auth.setSessionStatus(
                SessionStatus.Authenticated(session, SessionSource.Unknown),
            )
        }
    }

    private class RecordingReader(
        private val parentSequence: List<TournamentRevisionRestorePayload?>,
    ) : MatchCloudRestorationRemoteReader {
        var parentReads = 0
        val childReadRevisions = mutableListOf<Int>()
        private var currentRevision = 0

        override suspend fun readTournament(tournamentId: String): TournamentRevisionRestorePayload? {
            val parent = parentSequence.getOrNull(parentReads++)
            currentRevision = parent?.revision ?: currentRevision
            return parent
        }

        override suspend fun readMatches(tournamentId: String): List<MatchCloudRestorePayload> {
            childReadRevisions += currentRevision
            return listOf(
                MatchCloudRestorePayload(
                    id = "match-$currentRevision",
                    tournamentId = tournamentId,
                    matchNumber = 1,
                    matchDate = "2026-08-15",
                    mapName = "Bermuda",
                    status = "draft",
                    revision = currentRevision,
                ),
            )
        }

        override suspend fun readResults(matchId: String): List<MatchResultCloudRestorePayload> = emptyList()
    }

    private fun parent(revision: Int, id: String = TOURNAMENT_ID) =
        TournamentRevisionRestorePayload(id = id, revision = revision)

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
