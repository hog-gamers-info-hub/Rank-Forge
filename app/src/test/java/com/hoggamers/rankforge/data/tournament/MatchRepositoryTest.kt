package com.hoggamers.rankforge.data.tournament

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.hoggamers.rankforge.domain.tournament.CreateMatchRepositoryResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchCreationFailure
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MAX_MATCHES_PER_TOURNAMENT
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsFailure
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsRepositoryResult
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsFailure
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsRepositoryResult
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

    @Test
    fun repositoryUpdatesDraftPlacementsAndRejectsDuplicatePositions() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament("first"))
        repository.createDraftMatch(match("first", id = "match-first"))

        assertEquals(
            SaveMatchPlacementsRepositoryResult.Saved,
            repository.saveDraftMatchPlacements(
                matchId = "match-first",
                placements = listOf(MatchPlacement(teamSlotNumber = 1, position = 1)),
            ),
        )
        assertEquals(
            listOf(MatchPlacement(1, 1)),
            repository.observeMatchById("match-first").first()?.placements,
        )
        assertEquals(
            SaveMatchPlacementsRepositoryResult.Rejected(SaveMatchPlacementsFailure.DUPLICATE_POSITION),
            repository.saveDraftMatchPlacements(
                matchId = "match-first",
                placements = listOf(MatchPlacement(1, 1), MatchPlacement(2, 1)),
            ),
        )
        assertEquals(listOf(MatchPlacement(1, 1)), repository.observeMatchById("match-first").first()?.placements)
    }

    @Test
    fun repositoryUpdatesDraftKillsAndRejectsNegativeValues() = runTest {
        val repository = InMemoryTournamentRepository()
        repository.create(tournament("first"))
        repository.createDraftMatch(match("first", id = "match-first"))

        assertEquals(
            SaveMatchKillsRepositoryResult.Saved,
            repository.saveDraftMatchKills(
                matchId = "match-first",
                kills = listOf(MatchKill(teamSlotNumber = 1, kills = 0)),
            ),
        )
        assertEquals(
            listOf(MatchKill(1, 0)),
            repository.observeMatchById("match-first").first()?.kills,
        )
        assertEquals(
            SaveMatchKillsRepositoryResult.Rejected(SaveMatchKillsFailure.INVALID_KILLS),
            repository.saveDraftMatchKills(
                matchId = "match-first",
                kills = listOf(MatchKill(teamSlotNumber = 2, kills = -1)),
            ),
        )
        assertEquals(listOf(MatchKill(1, 0)), repository.observeMatchById("match-first").first()?.kills)
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

