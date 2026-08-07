package com.hoggamers.rankforge.data.cloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentCloudUploadExecutorTest {
    @Test
    fun executesTournamentSlotsThenPlayersInOrder() = runBlocking {
        val calls = mutableListOf<String>()
        val executor = TournamentCloudUploadExecutor(
            upsertTournament = { calls += "tournament" },
            upsertTeamSlots = { calls += "team_slots" },
            upsertPlayers = { calls += "players" },
        )

        val result = executor.execute(payloads())

        assertTrue(result is CloudUploadExecutionResult.Success)
        assertEquals(listOf("tournament", "team_slots", "players"), calls)
    }

    @Test
    fun reportsPartialFailureAfterTournamentWithoutDeletingAcceptedRows() = runBlocking {
        val calls = mutableListOf<String>()
        val executor = TournamentCloudUploadExecutor(
            upsertTournament = { calls += "tournament" },
            upsertTeamSlots = { calls += "team_slots"; error("network unavailable") },
            upsertPlayers = { calls += "players" },
        )

        val result = executor.execute(payloads()) as CloudUploadExecutionResult.Failure

        assertEquals(CloudUploadCompletedStage.TOURNAMENT, result.completedStage)
        assertEquals(CloudUploadFailureCategory.NETWORK, result.category)
        assertEquals(listOf("tournament", "team_slots"), calls)
        assertTrue("players" !in calls)
    }

    private fun payloads() = TournamentCloudUploadPayloads(
        tournament = TournamentUploadPayload(
            id = "11111111-1111-1111-1111-111111111111",
            ownerId = "22222222-2222-2222-2222-222222222222",
            name = "Summer Cup",
            tournamentDate = "2026-07-24",
            organizerName = "Organizer",
            organizerContact = "123",
            status = "draft",
        ),
        teamSlots = emptyList(),
        players = emptyList(),
    )
}
