package com.hoggamers.rankforge.domain.tournament

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CreateTournamentUseCaseTest {
    private val today = LocalDate.of(2026, 7, 24)
    private lateinit var repository: RecordingTournamentRepository
    private lateinit var useCase: CreateTournamentUseCase

    @Before
    fun setUp() {
        repository = RecordingTournamentRepository()
        useCase = CreateTournamentUseCase(
            repository = repository,
            clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
        )
    }

    @Test
    fun validInputCreatesDraftWithStableId() = runTest {
        val result = useCase(validInput())

        val created = result.createdTournament()
        assertEquals(TournamentStatus.DRAFT, created.status)
        assertTrue(created.id.isNotBlank())
        assertEquals(1, repository.records.size)
        assertEquals(created.id, repository.records.single().id)
    }

    @Test
    fun blankTournamentNameFailsWithoutCreating() = runTest {
        val result = useCase(validInput().copy(name = "   "))

        assertInvalid(result, TournamentField.NAME)
    }

    @Test
    fun missingDateFailsWithoutCreating() = runTest {
        val result = useCase(validInput().copy(date = null))

        assertInvalid(result, TournamentField.DATE)
    }

    @Test
    fun pastDateFailsWithoutCreating() = runTest {
        val result = useCase(validInput().copy(date = today.minusDays(1)))

        assertInvalid(result, TournamentField.DATE)
        assertEquals(TournamentValidationError.PAST_DATE, (result as CreateTournamentResult.Invalid).errors[TournamentField.DATE])
    }

    @Test
    fun todayDateSucceeds() = runTest {
        assertTrue(useCase(validInput().copy(date = today)) is CreateTournamentResult.Created)
    }

    @Test
    fun futureDateSucceeds() = runTest {
        assertTrue(useCase(validInput().copy(date = today.plusDays(1))) is CreateTournamentResult.Created)
    }

    @Test
    fun blankOrganizerNameFailsWithoutCreating() = runTest {
        val result = useCase(validInput().copy(organizerName = ""))

        assertInvalid(result, TournamentField.ORGANIZER_NAME)
    }

    @Test
    fun blankOrganizerContactNumberFailsWithoutCreating() = runTest {
        val result = useCase(validInput().copy(organizerContactNumber = "\t"))

        assertInvalid(result, TournamentField.ORGANIZER_CONTACT_NUMBER)
    }

    @Test
    fun oneCreateCallProducesOneRepositoryRecord() = runTest {
        useCase(validInput())

        assertEquals(1, repository.records.size)
    }

    @Test
    fun validInputCreatesExactlyTwelveSlots() = runTest {
        val result = useCase(validInput())
        val created = result.createdTournament()

        assertEquals((1..12).toList(), repository.slotsByTournamentId.getValue(created.id).map { it.slotNumber })
    }

    private fun validInput() = CreateTournamentInput(
        name = "Summer Cup",
        date = today,
        organizerName = "Alex",
        organizerContactNumber = "1234567890",
    )

    private fun assertInvalid(
        result: CreateTournamentResult,
        field: TournamentField,
    ) {
        assertTrue(result is CreateTournamentResult.Invalid)
        assertTrue((result as CreateTournamentResult.Invalid).errors.containsKey(field))
        assertTrue(repository.records.isEmpty())
    }

    private fun CreateTournamentResult.createdTournament(): Tournament =
        (this as CreateTournamentResult.Created).tournament

    private class RecordingTournamentRepository : TournamentRepository {
        val records = mutableListOf<Tournament>()
        val slotsByTournamentId = mutableMapOf<String, List<TeamSlot>>()
        private val state = MutableStateFlow<List<Tournament>>(emptyList())

        override suspend fun create(tournament: Tournament) {
            records += tournament
            slotsByTournamentId[tournament.id] = TeamSlot.fixedSlotsForTournament(tournament.id)
            state.value = records.toList()
        }

        override fun observeAll(): Flow<List<Tournament>> = state

        override fun observeById(tournamentId: String): Flow<Tournament?> =
            state.map { tournaments -> tournaments.firstOrNull { it.id == tournamentId } }

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> =
            state.map { tournaments ->
                if (tournaments.any { it.id == tournamentId }) {
                    slotsByTournamentId[tournamentId] ?: TeamSlot.fixedSlotsForTournament(tournamentId)
                } else {
                    emptyList()
                }
            }
    }
}
