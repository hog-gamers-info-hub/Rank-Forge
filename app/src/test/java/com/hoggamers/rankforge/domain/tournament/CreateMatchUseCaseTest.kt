package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository

class CreateMatchUseCaseTest {
    private lateinit var repository: InMemoryTournamentRepository
    private lateinit var useCase: CreateMatchUseCase

    @Before
    fun setUp() {
        repository = InMemoryTournamentRepository()
        useCase = CreateMatchUseCase(repository, SignedInTournamentTestAuthRepository())
    }

    @Test
    fun validInputCreatesDraftMatch() = runTest {
        createReadyTournament("first")

        val result = useCase(validInput("first"))

        val created = (result as CreateMatchResult.Created).match
        assertTrue(created.id.isNotBlank())
        assertEquals("first", created.tournamentId)
        assertEquals(1, created.matchNumber)
        assertEquals("Bermuda", created.mapName)
        assertEquals(MatchStatus.DRAFT, created.status)
    }

    @Test
    fun blankOrInvalidMatchNumberIsRejected() = runTest {
        createReadyTournament("first")

        listOf("", "  ", "abc", "1.5").forEach { value ->
            val result = useCase(validInput("first").copy(matchNumber = value))
            assertEquals(MatchValidationError.REQUIRED.takeIf { value.isBlank() } ?: MatchValidationError.INVALID,
                (result as CreateMatchResult.Invalid).errors[MatchField.MATCH_NUMBER])
        }
    }

    @Test
    fun zeroAndNegativeMatchNumbersAreRejected() = runTest {
        createReadyTournament("first")

        listOf("0", "-1").forEach { value ->
            val result = useCase(validInput("first").copy(matchNumber = value))
            assertEquals(MatchValidationError.INVALID, (result as CreateMatchResult.Invalid).errors[MatchField.MATCH_NUMBER])
        }
    }

    @Test
    fun duplicateMatchNumberIsRejectedWithinTournament() = runTest {
        createReadyTournament("first")
        assertTrue(useCase(validInput("first")) is CreateMatchResult.Created)

        val result = useCase(validInput("first"))

        assertEquals(MatchValidationError.DUPLICATE, (result as CreateMatchResult.Invalid).errors[MatchField.MATCH_NUMBER])
    }

    @Test
    fun sameMatchNumberIsAllowedAcrossTournaments() = runTest {
        createReadyTournament("first")
        createReadyTournament("second")

        assertTrue(useCase(validInput("first")) is CreateMatchResult.Created)
        assertTrue(useCase(validInput("second")) is CreateMatchResult.Created)
    }

    @Test
    fun missingDateAndBlankMapAreRejected() = runTest {
        createReadyTournament("first")

        val result = useCase(validInput("first").copy(date = null, mapName = "  "))

        val errors = (result as CreateMatchResult.Invalid).errors
        assertEquals(MatchValidationError.REQUIRED, errors[MatchField.DATE])
        assertEquals(MatchValidationError.REQUIRED, errors[MatchField.MAP])
    }

    @Test
    fun unknownTournamentIsRejected() = runTest {
        val result = useCase(validInput("missing"))

        assertEquals(
            MatchValidationError.TOURNAMENT_NOT_FOUND,
            (result as CreateMatchResult.Invalid).errors[MatchField.TOURNAMENT],
        )
    }

    @Test
    fun draftTournamentWithEightTeamsCanCreateMatch() = runTest {
        repository.create(tournament("first").copy(status = TournamentStatus.DRAFT))
        repository.saveTeamNames("first", (1..8).associateWith { "Team $it" })

        val result = useCase(validInput("first"))

        assertTrue(result is CreateMatchResult.Created)
    }

    @Test
    fun noParticipatingTeamsAreRejectedWithoutMutation() = runTest {
        repository.create(tournament("first").copy(status = TournamentStatus.DRAFT))

        val result = useCase(validInput("first"))

        assertEquals(MatchValidationError.NO_PARTICIPATING_TEAMS, (result as CreateMatchResult.Invalid).errors[MatchField.TOURNAMENT])
        assertTrue(repository.observeMatchesByTournamentId("first").first().isEmpty())
    }

    @Test
    fun sparseTeamSlotsCanCreateMatchWithoutMutation() = runTest {
        repository.create(tournament("first").copy(status = TournamentStatus.DRAFT))
        repository.saveTeamNames("first", mapOf(1 to "Alpha", 3 to "Charlie"))

        val result = useCase(validInput("first"))

        assertTrue(result is CreateMatchResult.Created)
        assertEquals(1, repository.observeMatchesByTournamentId("first").first().size)
    }

    @Test
    fun eleventhMatchIsRejected() = runTest {
        createReadyTournament("first")
        (1..MAX_MATCHES_PER_TOURNAMENT).forEach { number ->
            assertTrue(useCase(validInput("first").copy(matchNumber = number.toString())) is CreateMatchResult.Created)
        }

        val result = useCase(validInput("first").copy(matchNumber = "11"))

        assertEquals(MatchValidationError.LIMIT_REACHED, (result as CreateMatchResult.Invalid).errors[MatchField.TOURNAMENT])
        assertEquals(MAX_MATCHES_PER_TOURNAMENT, repository.observeMatchesByTournamentId("first").first().size)
    }

    private fun validInput(tournamentId: String) = CreateMatchInput(
        tournamentId = tournamentId,
        matchNumber = "1",
        date = LocalDate.of(2026, 7, 24),
        mapName = "  Bermuda  ",
    )

    private suspend fun createReadyTournament(id: String) {
        repository.create(tournament(id))
        repository.saveTeamNames(id, mapOf(1 to "Team 1"))
    }

    private fun tournament(id: String) = Tournament(
        id = id,
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.CONFIRMED,
        ownerUserId = SignedInTournamentTestAuthRepository.OWNER_USER_ID,
    )
}
