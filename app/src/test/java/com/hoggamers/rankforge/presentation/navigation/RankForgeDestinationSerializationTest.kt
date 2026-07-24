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
}
