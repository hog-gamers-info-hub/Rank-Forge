package com.hoggamers.rankforge.presentation.navigation

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RankForgeDestinationSerializationTest {
    private val json = Json

    @Test
    fun tournamentListDestinationSurvivesSerializationRoundTrip() {
        val encoded = json.encodeToString(TournamentListDestination)
        val decoded = json.decodeFromString<TournamentListDestination>(encoded)

        assertEquals(TournamentListDestination, decoded)
    }

    @Test
    fun tournamentCreationDestinationSurvivesSerializationRoundTrip() {
        val encoded = json.encodeToString(TournamentCreationDestination)
        val decoded = json.decodeFromString<TournamentCreationDestination>(encoded)

        assertEquals(TournamentCreationDestination, decoded)
    }

    @Test
    fun tournamentDetailsDestinationSurvivesSerializationRoundTrip() {
        val destination = TournamentDetailsDestination(tournamentId = "stable-id")

        val encoded = json.encodeToString(destination)
        val decoded = json.decodeFromString<TournamentDetailsDestination>(encoded)

        assertEquals(destination, decoded)
    }

    @Test
    fun rosterReviewDestinationSurvivesSerializationRoundTrip() {
        val destination = RosterReviewDestination(tournamentId = "stable-id")

        val encoded = json.encodeToString(destination)
        val decoded = json.decodeFromString<RosterReviewDestination>(encoded)

        assertEquals(destination, decoded)
    }

    @Test
    fun matchPlacementDestinationSurvivesSerializationRoundTrip() {
        val destination = MatchPlacementDestination(
            tournamentId = "tournament-id",
            matchId = "match-id",
        )

        val encoded = json.encodeToString(destination)
        val decoded = json.decodeFromString<MatchPlacementDestination>(encoded)

        assertEquals(destination, decoded)
    }

    @Test
    fun focusedTeamEntryDestinationSurvivesSerializationRoundTrip() {
        val destination = TeamEntryDestination(
            tournamentId = "stable-id",
            focusSlotNumber = 7,
        )

        val encoded = json.encodeToString(destination)
        val decoded = json.decodeFromString<TeamEntryDestination>(encoded)

        assertEquals(destination, decoded)
    }
}
