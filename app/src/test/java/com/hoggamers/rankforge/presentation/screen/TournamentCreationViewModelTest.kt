package com.hoggamers.rankforge.presentation.screen

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.hoggamers.rankforge.domain.tournament.CreateTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentField
import com.hoggamers.rankforge.domain.tournament.TournamentRepository

@OptIn(ExperimentalCoroutinesApi::class)
class TournamentCreationViewModelTest {
    private val today = LocalDate.of(2026, 7, 24)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: TestTournamentRepository
    private lateinit var viewModel: TournamentCreationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = TestTournamentRepository()
        viewModel = TournamentCreationViewModel(createUseCase(repository))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsIdleAndClean() {
        assertFalse(viewModel.uiState.value.isDirty)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertTrue(viewModel.uiState.value.validationErrors.isEmpty())
    }

    @Test
    fun fieldUpdatesChangeStateAndMarkFormDirty() {
        viewModel.onTournamentNameChanged("Summer Cup")
        viewModel.onTournamentDateChanged(today)
        viewModel.onOrganizerNameChanged("Alex")
        viewModel.onOrganizerContactNumberChanged("123")

        assertTrue(viewModel.uiState.value.isDirty)
        assertEquals("Summer Cup", viewModel.uiState.value.tournamentName)
        assertEquals(today, viewModel.uiState.value.tournamentDate)
        assertEquals("Alex", viewModel.uiState.value.organizerName)
        assertEquals("123", viewModel.uiState.value.organizerContactNumber)
    }

    @Test
    fun invalidSubmitShowsValidationErrorsAndDoesNotCreate() = runTest {
        viewModel.submit()

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertTrue(viewModel.uiState.value.validationErrors.containsKey(TournamentField.NAME))
        assertTrue(viewModel.uiState.value.validationErrors.containsKey(TournamentField.DATE))
        assertTrue(viewModel.uiState.value.validationErrors.containsKey(TournamentField.ORGANIZER_NAME))
        assertTrue(viewModel.uiState.value.validationErrors.containsKey(TournamentField.ORGANIZER_CONTACT_NUMBER))
        assertTrue(repository.records.isEmpty())
        assertNull(viewModel.uiState.value.navigation)
    }

    @Test
    fun validSubmitEntersSubmittingAndThenSuccessWithCreatedTournamentId() = runTest {
        repository.blockCreation = true
        fillValidForm()

        viewModel.submit()
        runCurrent()

        assertTrue(viewModel.uiState.value.isSubmitting)
        repository.releaseCreation.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(1, repository.records.size)
        val navigation = viewModel.uiState.value.navigation
        assertTrue(navigation is TournamentCreationNavigation.Created)
        assertEquals(repository.records.single().id, (navigation as TournamentCreationNavigation.Created).tournamentId)
    }

    @Test
    fun repeatedSubmitWhileSubmittingDoesNotDuplicate() = runTest {
        repository.blockCreation = true
        fillValidForm()

        viewModel.submit()
        runCurrent()
        viewModel.submit()
        runCurrent()

        assertEquals(0, repository.records.size)
        repository.releaseCreation.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repository.records.size)
    }

    @Test
    fun repositoryFailureIsRepresentedInState() = runTest {
        repository.failCreation = true
        fillValidForm()

        viewModel.submit()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertNotNull(viewModel.uiState.value.submissionError)
        assertEquals(0, repository.records.size)
        assertNull(viewModel.uiState.value.navigation)
    }

    @Test
    fun dirtyBackShowsDialogAndKeepEditingPreservesForm() {
        viewModel.onTournamentNameChanged("Draft")

        viewModel.onBackPressed()

        assertTrue(viewModel.uiState.value.showDiscardDialog)
        viewModel.keepEditing()
        assertFalse(viewModel.uiState.value.showDiscardDialog)
        assertEquals("Draft", viewModel.uiState.value.tournamentName)
        assertTrue(viewModel.uiState.value.navigation == null)
    }

    @Test
    fun discardConfirmsExit() {
        viewModel.onTournamentNameChanged("Draft")
        viewModel.onBackPressed()

        viewModel.discardChanges()

        assertEquals(TournamentCreationNavigation.Back, viewModel.uiState.value.navigation)
    }

    private fun fillValidForm() {
        viewModel.onTournamentNameChanged("Summer Cup")
        viewModel.onTournamentDateChanged(today)
        viewModel.onOrganizerNameChanged("Alex")
        viewModel.onOrganizerContactNumberChanged("123")
    }

    private fun createUseCase(repository: TournamentRepository) = CreateTournamentUseCase(
        repository = repository,
        clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
    )

    private class TestTournamentRepository : TournamentRepository {
        val records = mutableListOf<Tournament>()
        val releaseCreation = CompletableDeferred<Unit>()
        var blockCreation = false
        var failCreation = false
        private val state = MutableStateFlow<List<Tournament>>(emptyList())

        override suspend fun create(tournament: Tournament) {
            if (blockCreation) releaseCreation.await()
            if (failCreation) error("creation failed")
            records += tournament
            state.value = records.toList()
        }

        override fun observeAll(): Flow<List<Tournament>> = state

        override fun observeById(tournamentId: String): Flow<Tournament?> =
            state.map { tournaments -> tournaments.firstOrNull { it.id == tournamentId } }

        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<com.hoggamers.rankforge.domain.tournament.TeamSlot>> =
            state.map { tournaments ->
                if (tournaments.any { it.id == tournamentId }) {
                    com.hoggamers.rankforge.domain.tournament.TeamSlot.fixedSlotsForTournament(tournamentId)
                } else {
                    emptyList()
                }
            }

        override suspend fun saveTeamNames(
            tournamentId: String,
            teamNamesBySlotNumber: Map<Int, String>,
        ) = Unit

        override fun observeRosterByTournamentAndSlot(
            tournamentId: String,
            slotNumber: Int,
        ): Flow<List<com.hoggamers.rankforge.domain.tournament.RosterPlayer>> =
            kotlinx.coroutines.flow.flowOf(emptyList())

        override suspend fun saveRoster(
            tournamentId: String,
            slotNumber: Int,
            players: List<com.hoggamers.rankforge.domain.tournament.RosterPlayer>,
        ) = Unit

        override suspend fun confirmTournament(tournamentId: String): Boolean = false
    }
}
