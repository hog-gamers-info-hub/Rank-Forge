package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalizeMatchUseCaseTest {
    @Test
    fun validDraftFinalizesAndClearsOnlyRawDraftCache() = runTest {
        val repository = createRepository()
        repository.saveDraftMatchValue("tournament-id", "match-id", 1, "1", "4")
        val useCase = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase())

        val result = useCase(FinalizeMatchInput("match-id", validRows()))

        val finalized = result as FinalizeMatchResult.Finalized
        assertEquals(MatchStatus.FINALIZED, finalized.match.status)
        assertEquals((1..12).toList(), finalized.match.placements.map { it.position })
        assertEquals((0..11).toList(), finalized.match.kills.map { it.kills })
        assertTrue(repository.observeDraftMatchValues("tournament-id", "match-id").first().isEmpty())
        assertEquals(
            MatchStatus.FINALIZED,
            repository.observeMatchById("match-id").first()!!.status,
        )
    }

    @Test
    fun invalidDraftCannotFinalize() = runTest {
        val repository = createRepository()
        val useCase = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase())
        val rows = validRows().map { row ->
            if (row.teamSlotNumber == 1) row.copy(placement = "") else row
        }

        val result = useCase(FinalizeMatchInput("match-id", rows))

        val invalid = result as FinalizeMatchResult.Invalid
        assertTrue(MatchResultValidationError.MISSING_PLACEMENT in invalid.validation.errorsByTeamSlot.getValue(1))
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById("match-id").first()!!.status)
    }

    @Test
    fun finalizedMatchCannotBeFinalizedAgain() = runTest {
        val repository = createRepository()
        val useCase = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase())
        useCase(FinalizeMatchInput("match-id", validRows()))

        val result = useCase(FinalizeMatchInput("match-id", validRows()))

        assertEquals(
            FinalizeMatchGlobalError.MATCH_NOT_DRAFT,
            (result as FinalizeMatchResult.Invalid).globalError,
        )
    }

    @Test
    fun missingMatchCannotBeFinalized() = runTest {
        val repository = InMemoryTournamentRepository()
        val useCase = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase())

        val result = useCase(FinalizeMatchInput("missing-id", validRows()))

        val invalid = result as FinalizeMatchResult.Invalid
        assertEquals(FinalizeMatchGlobalError.MATCH_NOT_FOUND, invalid.globalError)
        assertTrue(invalid.validation.isValid)
    }

    @Test
    fun repositoryFinalizationRejectionIsReportedWithoutChangingTheDraft() = runTest {
        val delegate = createRepository()
        val repository = FinalizationRejectingRepository(delegate)
        val useCase = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase())

        val result = useCase(FinalizeMatchInput("match-id", validRows()))

        assertEquals(
            FinalizeMatchGlobalError.INVALID_DATA,
            (result as FinalizeMatchResult.Invalid).globalError,
        )
        assertEquals(MatchStatus.DRAFT, delegate.observeMatchById("match-id").first()!!.status)
    }

    private suspend fun createRepository(): InMemoryTournamentRepository {
        val repository = InMemoryTournamentRepository()
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
        repository.createDraftMatch(
            Match(
                id = "match-id",
                tournamentId = "tournament-id",
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        return repository
    }

    private fun validRows() = (1..12).map { slotNumber ->
        MatchResultRowInput(
            teamSlotNumber = slotNumber,
            placement = slotNumber.toString(),
            kills = (slotNumber - 1).toString(),
        )
    }

    private class FinalizationRejectingRepository(
        private val delegate: InMemoryTournamentRepository,
    ) : TournamentRepository {
        override suspend fun create(tournament: Tournament) = delegate.create(tournament)

        override fun observeAll(): Flow<List<Tournament>> = delegate.observeAll()

        override fun observeById(tournamentId: String): Flow<Tournament?> = delegate.observeById(tournamentId)

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> =
            delegate.observeSlotsByTournamentId(tournamentId)

        override suspend fun saveTeamNames(
            tournamentId: String,
            teamNamesBySlotNumber: Map<Int, String>,
        ) = delegate.saveTeamNames(tournamentId, teamNamesBySlotNumber)

        override fun observeRosterByTournamentAndSlot(
            tournamentId: String,
            slotNumber: Int,
        ): Flow<List<RosterPlayer>> = delegate.observeRosterByTournamentAndSlot(tournamentId, slotNumber)

        override suspend fun saveRoster(
            tournamentId: String,
            slotNumber: Int,
            players: List<RosterPlayer>,
        ) = delegate.saveRoster(tournamentId, slotNumber, players)

        override suspend fun confirmTournament(tournamentId: String): Boolean = delegate.confirmTournament(tournamentId)

        override fun observeMatchById(matchId: String): Flow<Match?> = delegate.observeMatchById(matchId)

        override suspend fun finalizeDraftMatch(
            matchId: String,
            placements: List<MatchPlacement>,
            kills: List<MatchKill>,
        ): FinalizeMatchRepositoryResult = FinalizeMatchRepositoryResult.Rejected(FinalizeMatchFailure.INVALID_DATA)
    }
}
