package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.presentation.screen.TournamentStandingRowUiState
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentStandingsShareRemoteDataSourceTest {
    @Test
    fun blankTournamentIdIsRejectedBeforeGatewayAccess() = runTest {
        val gateway = FakeGateway()

        assertFailure(
            dataSource(gateway).publish("   ", listOf(row())),
            TournamentStandingsShareFailureReason.INVALID_INPUT,
        )
        assertEquals(0, gateway.selectCalls)
    }

    @Test
    fun emptyRowsAreRejectedBeforeGatewayAccess() = runTest {
        val gateway = FakeGateway()

        assertFailure(
            dataSource(gateway).publish(TOURNAMENT_ID, emptyList()),
            TournamentStandingsShareFailureReason.INVALID_INPUT,
        )
        assertEquals(0, gateway.selectCalls)
    }

    @Test
    fun moreThanTwelveRowsAreRejectedBeforeGatewayAccess() = runTest {
        val gateway = FakeGateway()

        assertFailure(
            dataSource(gateway).publish(TOURNAMENT_ID, List(13) { row() }),
            TournamentStandingsShareFailureReason.INVALID_INPUT,
        )
        assertEquals(0, gateway.selectCalls)
    }

    @Test
    fun missingConfigurationIsRejectedBeforeSessionOrGatewayAccess() = runTest {
        val gateway = FakeGateway()

        assertFailure(
            dataSource(gateway, configured = false).publish(TOURNAMENT_ID, listOf(row())),
            TournamentStandingsShareFailureReason.SERVER_CONFIGURATION,
        )
        assertEquals(0, gateway.selectCalls)
    }

    @Test
    fun missingSessionIsRejectedBeforeGatewayAccess() = runTest {
        val gateway = FakeGateway(authenticated = false)

        assertFailure(
            dataSource(gateway).publish(TOURNAMENT_ID, listOf(row())),
            TournamentStandingsShareFailureReason.AUTHENTICATION_REQUIRED,
        )
        assertEquals(0, gateway.selectCalls)
    }

    @Test
    fun existingShareSelectsByTournamentUpdatesOnlyAllowedFieldsAndPreservesToken() = runTest {
        val token = "11111111-1111-4111-8111-111111111111"
        val gateway = FakeGateway(selectedTokens = listOf(token))

        val result = dataSource(gateway).publish(TOURNAMENT_ID, listOf(row()))

        assertSuccess(result, "https://hog-gamers-info-hub.github.io/Rank-Forge/standings/?token=$token")
        assertEquals(listOf(TOURNAMENT_ID), gateway.selectedTournamentIds)
        val update = gateway.updates.single()
        assertEquals(TOURNAMENT_ID, update.tournamentId)
        assertEquals(serializeRows(listOf(row())), update.standings)
        assertEquals(expectedJsonRow(), update.standings.single())
        assertTrue(runCatching { Instant.parse(update.updatedAt) }.isSuccess)
        assertEquals(setOf("standings", "updated_at"), buildUpdatePayload(update).keys)
    }

    @Test
    fun selectProjectionContainsOnlyShareToken() {
        assertEquals("share_token", shareTokenColumns().value)
    }

    @Test
    fun newShareOmitsTokenUsesDatabaseTokenAndSerializesAllNullableFields() = runTest {
        val token = "22222222-2222-4222-8222-222222222222"
        val standings = listOf(
            row(teamName = null, latestMatchPlacement = null),
        )
        val gateway = FakeGateway(insertResult = TournamentStandingsShareInsertResult.Created(token))

        val result = dataSource(gateway).publish(TOURNAMENT_ID, standings)

        assertSuccess(result, "https://hog-gamers-info-hub.github.io/Rank-Forge/standings/?token=$token")
        val insert = gateway.inserts.single()
        assertEquals(TOURNAMENT_ID, insert.first)
        assertEquals(serializeRows(standings), insert.second)
        assertEquals(setOf("tournament_id", "standings"), buildInsertPayload(insert.first, insert.second).keys)
        assertFalse(buildInsertPayload(insert.first, insert.second).containsKey("share_token"))
        val serializedRow = insert.second.single().jsonObject
        assertEquals(JsonNull, serializedRow["teamName"])
        assertEquals(JsonNull, serializedRow["latestMatchPlacement"])
    }

    @Test
    fun malformedReturnedTokenIsInvalidResponse() = runTest {
        val gateway = FakeGateway(
            insertResult = TournamentStandingsShareInsertResult.Created("not-a-uuid"),
        )

        assertFailure(
            dataSource(gateway).publish(TOURNAMENT_ID, listOf(row())),
            TournamentStandingsShareFailureReason.INVALID_RESPONSE,
        )
    }

    @Test
    fun concurrentCreateRecoversByRereadingAndUpdatingExistingToken() = runTest {
        val token = "33333333-3333-4333-8333-333333333333"
        val gateway = FakeGateway(
            insertResult = TournamentStandingsShareInsertResult.Conflict,
            recoveredTokens = listOf(token),
        )

        val result = dataSource(gateway).publish(TOURNAMENT_ID, listOf(row()))

        assertSuccess(result, "https://hog-gamers-info-hub.github.io/Rank-Forge/standings/?token=$token")
        assertEquals(listOf(TOURNAMENT_ID, TOURNAMENT_ID), gateway.selectedTournamentIds)
        assertEquals(1, gateway.inserts.size)
        assertEquals(1, gateway.updates.size)
    }

    @Test
    fun cancellationIsRethrown() = runTest {
        val gateway = FakeGateway(selectFailure = CancellationException("cancelled"))

        try {
            dataSource(gateway).publish(TOURNAMENT_ID, listOf(row()))
            throw AssertionError("Expected cancellation")
        } catch (cancellation: CancellationException) {
            assertEquals("cancelled", cancellation.message)
        }
    }

    @Test
    fun networkFailureMapsToNetworkFailure() = runTest {
        assertFailure(
            dataSource(FakeGateway(selectFailure = IOException("offline")))
                .publish(TOURNAMENT_ID, listOf(row())),
            TournamentStandingsShareFailureReason.NETWORK_FAILURE,
        )
    }

    @Test
    fun unexpectedFailureMapsToGenericServerFailure() = runTest {
        assertFailure(
            dataSource(FakeGateway(selectFailure = IllegalStateException("secret database detail")))
                .publish(TOURNAMENT_ID, listOf(row())),
            TournamentStandingsShareFailureReason.SERVER_FAILURE,
        )
    }

    private fun dataSource(
        gateway: FakeGateway,
        configured: Boolean = true,
    ) = SupabaseTournamentStandingsShareRemoteDataSource(
        config = if (configured) CONFIG else SupabaseAuthConfig("", ""),
        gateway = gateway,
    )

    private fun assertFailure(
        result: TournamentStandingsSharePublicationResult,
        reason: TournamentStandingsShareFailureReason,
    ) {
        assertEquals(TournamentStandingsSharePublicationResult.Failure(reason), result)
    }

    private fun assertSuccess(
        result: TournamentStandingsSharePublicationResult,
        publicUrl: String,
    ) {
        assertEquals(TournamentStandingsSharePublicationResult.Success(publicUrl), result)
    }

    private fun row(
        teamName: String? = "Alpha",
        latestMatchPlacement: Int? = 2,
    ) = TournamentStandingRowUiState(
        displayOrder = 1,
        teamSlotNumber = 7,
        teamName = teamName,
        totalPoints = 20,
        totalPositionPoints = 9,
        totalKillPoints = 11,
        firstPlaceFinishes = 0,
        latestMatchPlacement = latestMatchPlacement,
        matchesIncluded = 2,
        isCompleteTie = false,
    )

    private fun expectedJsonRow() = buildJsonObject {
        put("displayOrder", 1)
        put("teamSlotNumber", 7)
        put("teamName", "Alpha")
        put("totalPoints", 20)
        put("totalPositionPoints", 9)
        put("totalKillPoints", 11)
        put("firstPlaceFinishes", 0)
        put("latestMatchPlacement", 2)
        put("matchesIncluded", 2)
        put("isCompleteTie", false)
    }

    private class FakeGateway(
        private val authenticated: Boolean = true,
        private val selectedTokens: List<String?> = emptyList(),
        private val recoveredTokens: List<String?> = selectedTokens,
        private val insertResult: TournamentStandingsShareInsertResult =
            TournamentStandingsShareInsertResult.Created("44444444-4444-4444-8444-444444444444"),
        private val selectFailure: Throwable? = null,
    ) : TournamentStandingsShareGateway {
        var selectCalls: Int = 0
        val selectedTournamentIds = mutableListOf<String>()
        val updates = mutableListOf<TournamentStandingsShareUpdateRequest>()
        val inserts = mutableListOf<Pair<String, kotlinx.serialization.json.JsonArray>>()

        override fun hasAuthenticatedSession(): Boolean = authenticated

        override suspend fun selectShareTokens(tournamentId: String): List<String?> {
            selectCalls++
            selectedTournamentIds += tournamentId
            selectFailure?.let { throw it }
            return if (selectCalls == 1) selectedTokens else recoveredTokens
        }

        override suspend fun updateShare(request: TournamentStandingsShareUpdateRequest) {
            updates += request
        }

        override suspend fun insertShare(
            tournamentId: String,
            standings: kotlinx.serialization.json.JsonArray,
        ): TournamentStandingsShareInsertResult {
            inserts += tournamentId to standings
            return insertResult
        }
    }

    private companion object {
        const val TOURNAMENT_ID = "tournament-id"
        val CONFIG = SupabaseAuthConfig(
            supabaseUrl = "https://project.supabase.co",
            publishableKey = "publishable-key",
        )
    }
}
