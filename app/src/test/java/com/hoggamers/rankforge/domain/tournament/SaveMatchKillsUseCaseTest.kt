package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository

class SaveMatchKillsUseCaseTest {
    private lateinit var repository: InMemoryTournamentRepository
    private lateinit var createMatch: CreateMatchUseCase
    private lateinit var saveKills: SaveMatchKillsUseCase

    @Before
    fun setUp() {
        repository = InMemoryTournamentRepository()
        createMatch = CreateMatchUseCase(repository)
        saveKills = SaveMatchKillsUseCase(repository)
    }

    @Test
    fun zeroAndWholeNumberKillsAreSavedToDraftMatch() = runTest {
        val matchId = createDraftMatch()

        val result = saveKills(
            SaveMatchKillsInput(
                matchId = matchId,
                killsByTeamSlot = (1..12).associateWith { (it - 1).toString() },
            ),
        )

        assertEquals(
            SaveMatchKillsResult.Saved((1..12).map { MatchKill(it, it - 1) }),
            result,
        )
        assertEquals(
            (0..11).toList(),
            repository.observeMatchById(matchId).first()?.kills?.map { it.kills },
        )
    }

    @Test
    fun blankRowsRemainUnassignedForEditableDrafts() = runTest {
        val matchId = createDraftMatch()

        val result = saveKills(
            SaveMatchKillsInput(
                matchId = matchId,
                killsByTeamSlot = mapOf(1 to "0", 2 to "", 3 to "  "),
            ),
        )

        assertEquals(SaveMatchKillsResult.Saved(listOf(MatchKill(1, 0))), result)
    }

    @Test
    fun negativeAndNonNumericKillsAreRejected() = runTest {
        val matchId = createDraftMatch()

        listOf("-1", "1.5", "abc").forEach { value ->
            val result = saveKills(
                SaveMatchKillsInput(matchId, mapOf(1 to value)),
            )

            assertEquals(
                KillValidationError.INVALID,
                (result as SaveMatchKillsResult.Invalid).errorsByTeamSlot[1],
            )
        }
        assertTrue(repository.observeMatchById(matchId).first()?.kills.orEmpty().isEmpty())
    }

    @Test
    fun unknownMatchIsHandledWithoutSaving() = runTest {
        val result = saveKills(
            SaveMatchKillsInput("missing-match", mapOf(1 to "0")),
        )

        assertEquals(
            KillGlobalError.MATCH_NOT_FOUND,
            (result as SaveMatchKillsResult.Invalid).globalError,
        )
    }

    private suspend fun createDraftMatch(): String {
        repository.create(
            Tournament(
                id = "tournament-id",
                name = "Summer Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        return (createMatch(
            CreateMatchInput(
                tournamentId = "tournament-id",
                matchNumber = "1",
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
            ),
        ) as CreateMatchResult.Created).match.id
    }
}
