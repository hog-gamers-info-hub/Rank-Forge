package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.tournament.CreateMatchInput
import com.hoggamers.rankforge.domain.tournament.CreateMatchResult
import com.hoggamers.rankforge.domain.tournament.CreateMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemoryTournamentRepository
    private lateinit var matchId: String

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(dispatcher)
        repository = InMemoryTournamentRepository()
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
        matchId = (CreateMatchUseCase(repository)(
            CreateMatchInput(
                tournamentId = "tournament-id",
                matchNumber = "1",
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
            ),
        ) as CreateMatchResult.Created).match.id
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun reviewShowsTwelveRowsAndRestoredDraftValues() = runTest {
        repository.saveRoster(
            "tournament-id",
            1,
            listOf(RosterPlayer("tournament-id", 1, "Player One")),
        )
        (1..12).forEach { slotNumber ->
            repository.saveDraftMatchValue(
                "tournament-id",
                matchId,
                slotNumber,
                placementInput = slotNumber.toString(),
                killsInput = (slotNumber - 1).toString(),
            )
        }

        val viewModel = reviewViewModel()
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        assertEquals((1..12).toList(), viewModel.uiState.value.rows.map { it.teamSlotNumber })
        assertEquals(listOf("Player One"), viewModel.uiState.value.rows.first().playerNames)
        assertEquals("7", viewModel.uiState.value.rows[6].placementInput)
        assertEquals("6", viewModel.uiState.value.rows[6].killsInput)
        assertTrue(viewModel.uiState.value.isValid)
    }

    @Test
    fun reviewUsesExistingValidationForIncompleteDraft() = runTest {
        val viewModel = reviewViewModel()
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isValid)
        assertTrue(
            MatchResultValidationError.MISSING_PLACEMENT in
                viewModel.uiState.value.rows.first().validationErrors,
        )
        assertTrue(
            MatchResultValidationError.MISSING_KILLS in
                viewModel.uiState.value.validationErrors.getValue(12),
        )
    }

    @Test
    fun reviewActionsExposePlacementKillAndDetailsNavigation() = runTest {
        val viewModel = reviewViewModel()
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        viewModel.openPlacements()
        assertEquals(MatchReviewNavigation.PLACEMENTS, viewModel.uiState.value.navigation)
        viewModel.onNavigationHandled()
        viewModel.openKills()
        assertEquals(MatchReviewNavigation.KILLS, viewModel.uiState.value.navigation)
        viewModel.onNavigationHandled()
        viewModel.onBackToDetails()
        assertEquals(MatchReviewNavigation.DETAILS, viewModel.uiState.value.navigation)
    }

    @Test
    fun validatedPhotoPickerSelectionReplacesThePreviousTemporaryState() = runTest {
        val viewModel = reviewViewModel()
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        viewModel.requestPhotoPicker()
        viewModel.onPhotoPickerLaunchHandled()
        viewModel.onPhotoPickerResult("content://picker/first")
        advanceUntilIdle()
        assertEquals("content://picker/first", viewModel.uiState.value.selectedScreenshotUri)
        assertTrue(viewModel.uiState.value.isSelectedScreenshotValidated)

        viewModel.requestPhotoPicker()
        viewModel.onPhotoPickerLaunchHandled()
        viewModel.onPhotoPickerResult("content://picker/second")
        advanceUntilIdle()

        assertEquals("content://picker/second", viewModel.uiState.value.selectedScreenshotUri)
        assertTrue(viewModel.uiState.value.isSelectedScreenshotValidated)
        assertFalse(viewModel.uiState.value.isPhotoPickerRequestActive)
        assertEquals(null, viewModel.uiState.value.photoPickerError)
    }

    @Test
    fun photoPickerCancellationPreservesStateAndBlankResultIsRejected() = runTest {
        val viewModel = reviewViewModel()
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        viewModel.onPhotoPickerResult("content://picker/selected")
        advanceUntilIdle()

        viewModel.requestPhotoPicker()
        viewModel.onPhotoPickerLaunchHandled()
        viewModel.onPhotoPickerResult(null)

        assertEquals("content://picker/selected", viewModel.uiState.value.selectedScreenshotUri)
        assertTrue(viewModel.uiState.value.isSelectedScreenshotValidated)
        assertFalse(viewModel.uiState.value.isPhotoPickerRequestActive)

        viewModel.requestPhotoPicker()
        viewModel.onPhotoPickerLaunchHandled()
        viewModel.onPhotoPickerResult("")

        assertEquals(null, viewModel.uiState.value.selectedScreenshotUri)
        assertFalse(viewModel.uiState.value.isSelectedScreenshotValidated)
        assertEquals(ImageValidationError.EMPTY_URI, viewModel.uiState.value.imageValidationError)
        assertFalse(viewModel.uiState.value.isPhotoPickerRequestActive)
    }

    @Test
    fun invalidSelectionShowsValidationErrorAndReselectionCanBecomeValid() = runTest {
        val viewModel = reviewViewModel(
            ImageCandidateValidator(
                ImageCandidateMetadataReader { uri ->
                    if (uri.endsWith("unsupported")) {
                        ImageCandidateReadResult.Metadata("image/gif", width = 1080, height = 1920)
                    } else {
                        ImageCandidateReadResult.Metadata("image/png", width = 1080, height = 1920)
                    }
                },
            ),
        )
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        viewModel.onPhotoPickerResult("content://picker/unsupported")
        advanceUntilIdle()

        assertEquals("content://picker/unsupported", viewModel.uiState.value.selectedScreenshotUri)
        assertFalse(viewModel.uiState.value.isSelectedScreenshotValidated)
        assertEquals(ImageValidationError.UNSUPPORTED_FORMAT, viewModel.uiState.value.imageValidationError)

        viewModel.onPhotoPickerResult("content://picker/png")
        advanceUntilIdle()

        assertEquals("content://picker/png", viewModel.uiState.value.selectedScreenshotUri)
        assertTrue(viewModel.uiState.value.isSelectedScreenshotValidated)
        assertEquals(null, viewModel.uiState.value.imageValidationError)
    }

    @Test
    fun repeatedPhotoPickerRequestsDoNotCreateConcurrentLaunchState() = runTest {
        val viewModel = reviewViewModel()
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        viewModel.requestPhotoPicker()
        viewModel.requestPhotoPicker()

        assertTrue(viewModel.uiState.value.isPhotoPickerLaunchPending)
        assertTrue(viewModel.uiState.value.isPhotoPickerRequestActive)
        viewModel.onPhotoPickerLaunchHandled()
        assertFalse(viewModel.uiState.value.isPhotoPickerLaunchPending)
        assertTrue(viewModel.uiState.value.isPhotoPickerRequestActive)
    }

    @Test
    fun validReviewFinalizesAndBecomesReadOnly() = runTest {
        (1..12).forEach { slotNumber ->
            repository.saveDraftMatchValue(
                "tournament-id",
                matchId,
                slotNumber,
                placementInput = slotNumber.toString(),
                killsInput = (slotNumber - 1).toString(),
            )
        }
        val viewModel = reviewViewModel()
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        viewModel.finalize()
        advanceUntilIdle()

        assertEquals(MatchStatus.FINALIZED, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isEditable)
        assertTrue(repository.observeDraftMatchValues("tournament-id", matchId).first().isEmpty())
        assertEquals(
            MatchStatus.FINALIZED,
            repository.observeMatchById(matchId).first()!!.status,
        )
    }

    @Test
    fun invalidReviewDoesNotFinalize() = runTest {
        val viewModel = reviewViewModel()
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        viewModel.finalize()
        advanceUntilIdle()

        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(matchId).first()!!.status)
        assertTrue(viewModel.uiState.value.isEditable)
        assertFalse(viewModel.uiState.value.isFinalizing)
    }

    private fun reviewViewModel(
        imageCandidateValidator: ImageCandidateValidator = ImageCandidateValidator(
            ImageCandidateMetadataReader {
                ImageCandidateReadResult.Metadata("image/png", width = 1080, height = 1920)
            },
        ),
    ) = MatchReviewViewModel(
        observeMatches = ObserveMatchesUseCase(repository),
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
        observeRoster = ObserveRosterByTournamentUseCase(repository),
        observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
        validateMatchResult = ValidateMatchResultUseCase(),
        finalizeMatch = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase()),
        imageCandidateValidator = imageCandidateValidator,
    )
}
