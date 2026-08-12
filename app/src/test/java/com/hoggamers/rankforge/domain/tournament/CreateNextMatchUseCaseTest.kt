package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CreateNextMatchUseCaseTest {
    private lateinit var repository: InMemoryTournamentRepository
    private lateinit var useCase: CreateNextMatchUseCase

    @Before
    fun setUp() {
        repository = InMemoryTournamentRepository()
        useCase = CreateNextMatchUseCase(repository)
    }

    @Test
    fun noMatchesCreatesMatchOneWithCompatibilityMetadata() = runTest {
        createReadyTournament("tournament")

        val created = useCase("tournament").createdMatch()

        assertEquals(1, created.matchNumber)
        assertEquals(LocalDate.of(2026, 7, 24), created.date)
        assertEquals("", created.mapName)
        assertEquals(MatchStatus.DRAFT, created.status)
    }

    @Test
    fun existingMatchCreatesNextNumber() = runTest {
        createReadyTournament("tournament")
        createExistingMatch("tournament", 1)

        assertEquals(2, useCase("tournament").createdMatch().matchNumber)
    }

    @Test
    fun multipleExistingMatchesCreateNextNumber() = runTest {
        createReadyTournament("tournament")
        (1..3).forEach { createExistingMatch("tournament", it) }

        assertEquals(4, useCase("tournament").createdMatch().matchNumber)
    }

    @Test
    fun nonContiguousExistingNumbersUseHighestPlusOne() = runTest {
        createReadyTournament("tournament")
        createExistingMatch("tournament", 1)
        createExistingMatch("tournament", 3)

        assertEquals(4, useCase("tournament").createdMatch().matchNumber)
    }

    @Test
    fun draftTournamentAndFewerThanTwelveTeamsCanCreateMatch() = runTest {
        repository.create(tournament("tournament"))
        repository.saveTeamNames("tournament", (1..8).associateWith { "Team $it" })

        assertTrue(useCase("tournament") is CreateNextMatchResult.Created)
    }

    @Test
    fun noTeamsAndGapAreControlledFailures() = runTest {
        repository.create(tournament("empty"))
        repository.create(tournament("gapped"))
        repository.saveTeamNames("gapped", mapOf(1 to "Team 1", 3 to "Team 3"))

        assertEquals(
            CreateNextMatchFailure.NO_PARTICIPATING_TEAMS,
            (useCase("empty") as CreateNextMatchResult.Rejected).failure,
        )
        assertEquals(
            CreateNextMatchFailure.INVALID_TEAM_SLOTS,
            (useCase("gapped") as CreateNextMatchResult.Rejected).failure,
        )
    }

    @Test
    fun maximumMatchesAreNotExceeded() = runTest {
        createReadyTournament("tournament")
        (1..MAX_MATCHES_PER_TOURNAMENT).forEach { createExistingMatch("tournament", it) }

        assertEquals(
            CreateNextMatchFailure.LIMIT_REACHED,
            (useCase("tournament") as CreateNextMatchResult.Rejected).failure,
        )
        assertEquals(MAX_MATCHES_PER_TOURNAMENT, repository.observeMatchesByTournamentId("tournament").first().size)
    }

    private suspend fun createReadyTournament(id: String) {
        repository.create(tournament(id))
        repository.saveTeamNames(id, mapOf(1 to "Team 1"))
    }

    private suspend fun createExistingMatch(tournamentId: String, matchNumber: Int) {
        CreateMatchUseCase(repository)(
            CreateMatchInput(
                tournamentId = tournamentId,
                matchNumber = matchNumber.toString(),
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
            ),
        )
    }

    private fun CreateNextMatchResult.createdMatch(): Match =
        (this as CreateNextMatchResult.Created).match

    private fun tournament(id: String) = Tournament(
        id = id,
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
    )
}
