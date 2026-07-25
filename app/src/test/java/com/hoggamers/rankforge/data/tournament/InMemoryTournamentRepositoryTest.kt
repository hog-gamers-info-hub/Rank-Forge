package com.hoggamers.rankforge.data.tournament

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

class InMemoryTournamentRepositoryTest {
    @Test
    fun createdTournamentIsStoredAndObservable() = runTest {
        val repository = InMemoryTournamentRepository()
        val tournament = Tournament(
            id = "stable-id",
            name = "Spring Cup",
            date = LocalDate.of(2026, 7, 24),
            organizerName = "Organizer",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
        )

        repository.create(tournament)

        assertEquals(listOf(tournament), repository.observeAll().first())
    }

    @Test
    fun sameStableIdIsNotStoredTwice() = runTest {
        val repository = InMemoryTournamentRepository()
        val tournament = Tournament(
            id = "stable-id",
            name = "Spring Cup",
            date = LocalDate.of(2026, 7, 24),
            organizerName = "Organizer",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
        )

        repository.create(tournament)
        repository.create(tournament)

        assertEquals(listOf(tournament), repository.observeAll().first())
    }

    @Test
    fun getByIdReturnsCreatedTournament() = runTest {
        val repository = InMemoryTournamentRepository()
        val tournament = Tournament(
            id = "stable-id",
            name = "Spring Cup",
            date = LocalDate.of(2026, 7, 24),
            organizerName = "Organizer",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
        )

        repository.create(tournament)

        assertEquals(tournament, repository.observeById("stable-id").first())
    }

    @Test
    fun getByIdReturnsNullForUnknownTournament() = runTest {
        val repository = InMemoryTournamentRepository()

        assertEquals(null, repository.observeById("missing").first())
    }
}
