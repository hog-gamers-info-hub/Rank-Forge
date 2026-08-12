package com.hoggamers.rankforge.presentation.screen

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult

@OptIn(ExperimentalCoroutinesApi::class)
class TeamEntryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemoryTournamentRepository
    private lateinit var uploadAction: FakeTournamentCloudUploadAction

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = InMemoryTournamentRepository()
        uploadAction = FakeTournamentCloudUploadAction()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadExposesAllTwelveSlotsInOrder() = runTest {
        repository.create(tournament())
        val viewModel = viewModel()

        viewModel.load("stable-id")
        advanceUntilIdle()

        assertEquals((1..12).toList(), viewModel.uiState.value.slots.map { it.slotNumber })
    }

    @Test
    fun editingOneSlotDoesNotChangeOtherSlots() = runTest {
        repository.create(tournament())
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onTeamNameChanged(slotNumber = 2, teamName = "Bravo")

        assertEquals("", viewModel.uiState.value.slots.first { it.slotNumber == 1 }.teamName)
        assertEquals("Bravo", viewModel.uiState.value.slots.first { it.slotNumber == 2 }.teamName)
    }

    @Test
    fun savePersistsEditedNamesAndTrimsWhitespace() = runTest {
        repository.create(tournament())
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onTeamNameChanged(slotNumber = 1, teamName = "  Alpha  ")
        (2..12).forEach { slotNumber ->
            viewModel.onTeamNameChanged(slotNumber = slotNumber, teamName = "Team $slotNumber")
        }
        viewModel.saveTeamNames()
        advanceUntilIdle()

        val savedSlots = repository.observeSlotsByTournamentId("stable-id").first()
        assertEquals("Alpha", savedSlots.first { it.slotNumber == 1 }.teamName)
        assertEquals("Team 2", savedSlots.first { it.slotNumber == 2 }.teamName)
        assertEquals("Alpha", viewModel.uiState.value.slots.first { it.slotNumber == 1 }.teamName)
        assertEquals("Team 2", viewModel.uiState.value.slots.first { it.slotNumber == 2 }.teamName)
        assertEquals(listOf("stable-id"), uploadAction.tournamentIds)
    }

    @Test
    fun saveUploadsAfterLocalNamesArePersisted() = runTest {
        repository.create(tournament())
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()
        viewModel.onTeamNameChanged(1, "Alpha")
        viewModel.onTeamNameChanged(2, "Bravo")
        uploadAction.onInvocation = {
            val slots = repository.observeSlotsByTournamentId("stable-id").first()
            assertEquals("Alpha", slots.first { it.slotNumber == 1 }.teamName)
            assertEquals("Bravo", slots.first { it.slotNumber == 2 }.teamName)
        }

        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(listOf("stable-id"), uploadAction.tournamentIds)
        assertFalse(viewModel.uiState.value.hasSaveError)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun networkFailurePreservesLocalNamesWithoutSaveError() = runTest {
        repository.create(tournament())
        uploadAction.result = QueueAwareActionResult(
            primaryResult = TournamentCloudUploadResult.NetworkFailure,
            queueRecordingResult = QueueRecordingResult.RECORDED,
        )
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()
        viewModel.onTeamNameChanged(1, "Alpha")
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals("Alpha", repository.observeSlotsByTournamentId("stable-id").first().first().teamName)
        assertFalse(viewModel.uiState.value.hasSaveError)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun queuePersistenceFailurePreservesLocalNamesWithoutSaveError() = runTest {
        repository.create(tournament())
        uploadAction.result = QueueAwareActionResult(
            primaryResult = TournamentCloudUploadResult.NetworkFailure,
            queueRecordingResult = QueueRecordingResult.PERSISTENCE_FAILED,
        )
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()
        viewModel.onTeamNameChanged(1, "Alpha")
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals("Alpha", repository.observeSlotsByTournamentId("stable-id").first().first().teamName)
        assertFalse(viewModel.uiState.value.hasSaveError)
    }

    @Test
    fun unexpectedCloudExceptionPreservesLocalNamesWithoutSaveError() = runTest {
        repository.create(tournament())
        uploadAction.throwable = IllegalStateException("cloud unavailable")
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()
        viewModel.onTeamNameChanged(1, "Alpha")
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals("Alpha", repository.observeSlotsByTournamentId("stable-id").first().first().teamName)
        assertFalse(viewModel.uiState.value.hasSaveError)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun repeatedSaveWhileCloudUploadIsBlockedDoesNotDuplicate() = runTest {
        repository.create(tournament())
        uploadAction.block = true
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()
        viewModel.onTeamNameChanged(1, "Alpha")
        viewModel.saveTeamNames()
        viewModel.saveTeamNames()
        runCurrent()

        assertEquals(1, uploadAction.tournamentIds.size)
        assertTrue(viewModel.uiState.value.isSaving)
        uploadAction.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, uploadAction.tournamentIds.size)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun savingAllBlankNamesKeepsTrailingSlotsBlank() = runTest {
        repository.create(tournament())
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(0, repository.observeSlotsByTournamentId("stable-id").first().count { it.teamName.isNotBlank() })
        assertTrue(repository.observeSlotsByTournamentId("stable-id").first().all { it.teamName.isBlank() })
        assertTrue(viewModel.uiState.value.validationIssues.none { it.isBlocking })
    }

    @Test
    fun incompleteNamesSaveImmediatelyAndPreserveTrailingBlanks() = runTest {
        repository.create(tournament())
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onTeamNameChanged(1, "  Alpha  ")
        viewModel.onTeamNameChanged(3, "Bravo")
        viewModel.onTeamNameChanged(7, "   ")
        viewModel.onTeamNameChanged(10, "  Titan  ")
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasTeamNameGap)

        viewModel.onTeamNameChanged(2, "Bravo")
        viewModel.onTeamNameChanged(3, "")
        viewModel.onTeamNameChanged(4, "")
        viewModel.onTeamNameChanged(7, "")
        viewModel.onTeamNameChanged(10, "")
        viewModel.saveTeamNames()
        advanceUntilIdle()
        val savedSlots = repository.observeSlotsByTournamentId("stable-id").first()
        assertEquals("Alpha", savedSlots[0].teamName)
        assertEquals("Bravo", savedSlots[1].teamName)
        assertEquals("", savedSlots[2].teamName)
        assertEquals("", savedSlots[9].teamName)
    }

    @Test
    fun reopeningTeamEntryShowsSavedNames() = runTest {
        repository.create(tournament())
        val firstViewModel = viewModel()
        firstViewModel.load("stable-id")
        advanceUntilIdle()
        firstViewModel.onTeamNameChanged(slotNumber = 3, teamName = "Charlie")
        (1..2).forEach { slotNumber -> firstViewModel.onTeamNameChanged(slotNumber, "Team $slotNumber") }
        (4..12).forEach { slotNumber -> firstViewModel.onTeamNameChanged(slotNumber, "Team $slotNumber") }
        firstViewModel.saveTeamNames()
        advanceUntilIdle()

        val reopenedViewModel = viewModel()
        reopenedViewModel.load("stable-id")
        advanceUntilIdle()

        assertEquals("Charlie", reopenedViewModel.uiState.value.slots.first { it.slotNumber == 3 }.teamName)
    }

    @Test
    fun unknownTournamentRendersNotFoundState() = runTest {
        val viewModel = viewModel()

        viewModel.load("missing")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isNotFound)
    }

    @Test
    fun duplicateTeamNamesBlockSavingButMissingNamesAndCountsDoNot() = runTest {
        repository.create(tournament())
        val viewModel = viewModel()
        viewModel.load("stable-id")
        advanceUntilIdle()

        viewModel.onTeamNameChanged(1, " Alpha ")
        viewModel.onTeamNameChanged(2, "Alpha")
        viewModel.saveTeamNames()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.validationIssues.none { it.isBlocking })
        assertEquals("", repository.observeSlotsByTournamentId("stable-id").first().first().teamName)
        assertEquals("", repository.observeSlotsByTournamentId("stable-id").first()[1].teamName)
        assertTrue(uploadAction.tournamentIds.isEmpty())
    }

    private fun viewModel() = TeamEntryViewModel(
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
        saveTeamSlotNames = SaveTeamSlotNamesUseCase(repository),
        validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
        uploadTournament = uploadAction,
    )

    private fun tournament() = Tournament(
        id = "stable-id",
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
    )

    private class FakeTournamentCloudUploadAction : TournamentCloudUploadAction {
        val tournamentIds = mutableListOf<String>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        var block = false
        var throwable: Throwable? = null
        var onInvocation: (suspend () -> Unit)? = null
        var result: QueueAwareActionResult<TournamentCloudUploadResult> = QueueAwareActionResult(
            primaryResult = TournamentCloudUploadResult.Success(1),
            queueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
        )

        override suspend fun invoke(tournamentId: String): QueueAwareActionResult<TournamentCloudUploadResult> {
            tournamentIds += tournamentId
            onInvocation?.invoke()
            if (block) release.await()
            throwable?.let { throw it }
            return result
        }
    }
}
