package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRemoteResult
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
class SupabaseTournamentCloudRestorationRemoteDataSourceTest {
    private val providers = mutableListOf<SupabaseClientProvider>()

    @After
    fun closeClients() = runBlocking {
        providers.forEach { provider ->
            provider.client.auth.stopAutoRefreshForCurrentSession()
            provider.client.close()
        }
    }

    @Test
    fun equalRevisionParentsAreAcceptedInOneAttempt() = runTest {
        val reader = RecordingReader(listOf(parent(4), parent(4)))

        val result = remote(reader).readOwnedTournament(TOURNAMENT_ID)

        assertTrue(result isResultSuccessWithRevision 4)
        assertEquals(2, reader.parentReads)
        assertEquals(listOf(4), reader.childReadRevisions)
    }

    @Test
    fun revisionDriftDiscardsFirstChildrenAndRetriesTheEntireSequence() = runTest {
        val reader = RecordingReader(listOf(parent(1), parent(2), parent(3), parent(3)))

        val result = remote(reader).readOwnedTournament(TOURNAMENT_ID)

        assertTrue(result isResultSuccessWithRevision 3)
        assertEquals(4, reader.parentReads)
        assertEquals(listOf(1, 3), reader.childReadRevisions)
        val payloads = (result as TournamentCloudRestorationRemoteResult.Success).value
        assertEquals("Team generation 3", payloads.teamSlots.single().teamName)
    }

    @Test
    fun repeatedRevisionDriftReturnsRetryableNetworkFailure() = runTest {
        val reader = RecordingReader(listOf(parent(1), parent(2), parent(3), parent(4)))

        val result = remote(reader).readOwnedTournament(TOURNAMENT_ID)

        assertEquals(
            TournamentCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.NETWORK,
            ),
            result,
        )
        assertEquals(4, reader.parentReads)
        assertEquals(listOf(1, 3), reader.childReadRevisions)
    }

    @Test
    fun parentDisappearingAtFenceFailsClosed() = runTest {
        val reader = RecordingReader(listOf(parent(1), null))

        val result = remote(reader).readOwnedTournament(TOURNAMENT_ID)

        assertEquals(
            TournamentCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.NOT_FOUND,
            ),
            result,
        )
        assertEquals(2, reader.parentReads)
    }

    @Test
    fun mappingRowsAreSortedByPairingThenLobbyPosition() = runTest {
        val reader = RecordingReader(listOf(parent(7), parent(7)))
        reader.mappingRows = listOf(
            GroupPairingLobbySlotUploadPayload(TOURNAMENT_ID, "B:C", 2, 8),
            GroupPairingLobbySlotUploadPayload(TOURNAMENT_ID, "A:B", 12, 12),
            GroupPairingLobbySlotUploadPayload(TOURNAMENT_ID, "A:B", 1, 4),
        )

        val result = remote(reader).readOwnedTournament(TOURNAMENT_ID)

        assertTrue(result is TournamentCloudRestorationRemoteResult.Success)
        assertEquals(
            listOf("A:B" to 1, "A:B" to 12, "B:C" to 2),
            (result as TournamentCloudRestorationRemoteResult.Success).value.pairingLobbySlots
                .map { it.pairingKey to it.lobbySlotNumber },
        )
    }

    @Test
    fun invalidParentIdentityFailsClosedBeforeChildren() = runTest {
        val reader = RecordingReader(listOf(parent(1, id = OTHER_TOURNAMENT_ID)))

        val result = remote(reader).readOwnedTournament(TOURNAMENT_ID)

        assertEquals(
            TournamentCloudRestorationRemoteResult.Failure(
                TournamentCloudRestorationFailureCategory.VALIDATION,
            ),
            result,
        )
        assertEquals(0, reader.childReadRevisions.size)
    }

    private fun remote(reader: RecordingReader): SupabaseTournamentCloudRestorationRemoteDataSource {
        val provider = testClientProvider()
        providers += provider
        return SupabaseTournamentCloudRestorationRemoteDataSource(
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
        private val parentSequence: List<TournamentCloudRestorePayload?>,
    ) : TournamentCloudRestorationRemoteReader {
        var parentReads = 0
        val childReadRevisions = mutableListOf<Int>()
        var mappingRows: List<GroupPairingLobbySlotUploadPayload> = emptyList()
        private var currentRevision = 0

        override suspend fun readTournament(tournamentId: String): TournamentCloudRestorePayload? {
            val parent = parentSequence.getOrNull(parentReads++)
            currentRevision = parent?.revision ?: currentRevision
            return parent
        }

        override suspend fun readTeamSlots(tournamentId: String): List<TeamSlotCloudRestorePayload> {
            childReadRevisions += currentRevision
            return listOf(
                TeamSlotCloudRestorePayload(
                    id = "slot-$currentRevision",
                    tournamentId = tournamentId,
                    slotNumber = 1,
                    teamName = "Team generation $currentRevision",
                    status = "draft",
                ),
            )
        }

        override suspend fun readPlayers(teamSlotId: String): List<PlayerCloudRestorePayload> = emptyList()

        override suspend fun readPairings(tournamentId: String): List<GroupPairingUploadPayload> = emptyList()

        override suspend fun readPairingLobbySlots(
            tournamentId: String,
        ): List<GroupPairingLobbySlotUploadPayload> = mappingRows
    }

    private fun parent(
        revision: Int,
        id: String = TOURNAMENT_ID,
    ) = TournamentCloudRestorePayload(
        id = id,
        ownerId = OWNER_ID,
        name = "Tournament $revision",
        stageName = "Organizer",
        organizerContact = "123",
        status = "draft",
        revision = revision,
    )

    private infix fun TournamentCloudRestorationRemoteResult<TournamentCloudRestorationPayloads>.isResultSuccessWithRevision(
        revision: Int,
    ): Boolean = this is TournamentCloudRestorationRemoteResult.Success && value.tournament.revision == revision

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        const val OTHER_TOURNAMENT_ID = "22222222-2222-2222-2222-222222222222"
        const val OWNER_ID = "33333333-3333-3333-3333-333333333333"
    }
}
