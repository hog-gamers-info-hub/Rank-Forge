package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository

class SaveMatchPlacementsUseCaseTest {
    private lateinit var repository: InMemoryTournamentRepository
    private lateinit var createMatch: CreateMatchUseCase
    private lateinit var savePlacements: SaveMatchPlacementsUseCase

    @Before
    fun setUp() {
        repository = InMemoryTournamentRepository()
        createMatch = CreateMatchUseCase(repository, SignedInTournamentTestAuthRepository())
        savePlacements = SaveMatchPlacementsUseCase(repository, SignedInTournamentTestAuthRepository())
    }

    @Test
    fun validPlacementsFromOneThroughTwelveAreSavedToDraftMatch() = runTest {
        val matchId = createDraftMatch()

        val result = savePlacements(
            SaveMatchPlacementsInput(
                matchId = matchId,
                placementsByTeamSlot = (1..12).associateWith { it.toString() },
            ),
        )

        assertEquals(
            SaveMatchPlacementsResult.Saved((1..12).map { MatchPlacement(it, it) }),
            result,
        )
        assertEquals(
            (1..12).toList(),
            repository.observeMatchById(matchId).first()?.placements?.map { it.position },
        )
    }

    @Test
    fun blankRowsRemainUnassignedForEditableDrafts() = runTest {
        val matchId = createDraftMatch()

        val result = savePlacements(
            SaveMatchPlacementsInput(
                matchId = matchId,
                placementsByTeamSlot = mapOf(1 to "1", 2 to "", 3 to "  "),
            ),
        )

        assertEquals(SaveMatchPlacementsResult.Saved(listOf(MatchPlacement(1, 1))), result)
    }

    @Test
    fun outOfRangeAndNonNumericValuesAreRejected() = runTest {
        val matchId = createDraftMatch()

        listOf("0", "13", "-1", "abc").forEach { value ->
            val result = savePlacements(
                SaveMatchPlacementsInput(matchId, mapOf(1 to value)),
            )

            assertEquals(
                PlacementValidationError.INVALID,
                (result as SaveMatchPlacementsResult.Invalid).errorsByTeamSlot[1],
            )
        }
        assertTrue(repository.observeMatchById(matchId).first()?.placements.orEmpty().isEmpty())
    }

    @Test
    fun duplicatePositionsAreRejectedForBothTeams() = runTest {
        val matchId = createDraftMatch()

        val result = savePlacements(
            SaveMatchPlacementsInput(matchId, mapOf(1 to "1", 2 to "1")),
        )

        val invalid = result as SaveMatchPlacementsResult.Invalid
        assertEquals(PlacementValidationError.DUPLICATE, invalid.errorsByTeamSlot[1])
        assertEquals(PlacementValidationError.DUPLICATE, invalid.errorsByTeamSlot[2])
        assertTrue(repository.observeMatchById(matchId).first()?.placements.orEmpty().isEmpty())
    }

    @Test
    fun unknownMatchIsHandledWithoutSaving() = runTest {
        val result = savePlacements(
            SaveMatchPlacementsInput("missing-match", mapOf(1 to "1")),
        )

        assertEquals(
            PlacementGlobalError.MATCH_NOT_FOUND,
            (result as SaveMatchPlacementsResult.Invalid).globalError,
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
                ownerUserId = SignedInTournamentTestAuthRepository.OWNER_USER_ID,
            ),
        )
        repository.saveTeamNames("tournament-id", mapOf(1 to "Team One"))
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
