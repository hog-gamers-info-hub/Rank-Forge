package com.hoggamers.rankforge.data.tournament

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.hoggamers.rankforge.domain.tournament.CreateMatchRepositoryResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchCreationFailure
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MAX_MATCHES_PER_TOURNAMENT
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus

class MatchRepositoryTest {
    @Test
    fun createdMatchesAreObservableAndIsolatedByTournament() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament("first"))
        repository.create(tournament("second"))

        repository.createDraftMatch(match("first", id = "match-first"))

        assertEquals(listOf("match-first"), repository.observeMatchesByTournamentId("first").first().map { it.id })
        assertEquals(emptyList<Match>(), repository.observeMatchesByTournamentId("second").first())
    }

    @Test
    fun duplicateIdAndNumberDoNotCorruptState() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament("first"))
        val first = match("first", id = "same-id")

        assertEquals(CreateMatchRepositoryResult.Created, repository.createDraftMatch(first))
        assertTrue(repository.createDraftMatch(first) is CreateMatchRepositoryResult.Rejected)
        assertTrue(repository.createDraftMatch(first.copy(id = "other-id")) is CreateMatchRepositoryResult.Rejected)
        assertEquals(listOf("same-id"), repository.observeMatchesByTournamentId("first").first().map { it.id })
    }

    @Test
    fun repositoryEnforcesTenMatchLimit() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament("first"))
        (1..MAX_MATCHES_PER_TOURNAMENT).forEach { number ->
            assertEquals(
                CreateMatchRepositoryResult.Created,
                repository.createDraftMatch(match("first", id = "match-$number", number = number)),
            )
        }

        val result = repository.createDraftMatch(match("first", id = "match-11", number = 11))

        assertEquals(
            CreateMatchRepositoryResult.Rejected(MatchCreationFailure.LIMIT_REACHED),
            result,
        )
    }

    private fun match(tournamentId: String, id: String, number: Int = 1) = Match(
        id = id,
        tournamentId = tournamentId,
        matchNumber = number,
        date = LocalDate.of(2026, 7, 24),
        mapName = "Bermuda",
        status = MatchStatus.DRAFT,
    )

    private fun tournament(id: String) = Tournament(
        id = id,
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.CONFIRMED,
    )
}

