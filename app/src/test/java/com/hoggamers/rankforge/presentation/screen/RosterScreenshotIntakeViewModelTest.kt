package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.RosterScreenshotAssociationSaveResult
import com.hoggamers.rankforge.data.local.RosterScreenshotMetadataEntity
import com.hoggamers.rankforge.data.local.RosterScreenshotMetadataRepository
import com.hoggamers.rankforge.data.local.RosterScreenshotValidationStatus
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RosterScreenshotIntakeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun intendedSetHasExactlyThreeTournamentScopedSlotsAndStartsIncomplete() = runTest {
        val viewModel = viewModel()

        viewModel.load("tournament-1")

        assertEquals("tournament-1", viewModel.uiState.value.tournamentId)
        assertEquals((1..3).toList(), viewModel.uiState.value.slots.map { it.index })
        assertTrue(viewModel.uiState.value.isIncompleteDraftSet)
        assertFalse(viewModel.uiState.value.isCompleteSet)
        assertEquals(0, viewModel.uiState.value.selectedImageCount)
    }

    @Test
    fun selectReplaceRemoveAndCancelAreDeterministic() = runTest {
        val viewModel = viewModel(
            metadata = mapOf(
                "content://one" to validMetadata(),
                "content://replacement" to validMetadata(),
            ),
            imageBytes = mapOf(
                "content://one" to byteArrayOf(1),
                "content://replacement" to byteArrayOf(2),
            ),
        )
        viewModel.load("tournament-1")

        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerLaunchHandled()
        viewModel.onPhotoPickerResult("content://one")
        advanceUntilIdle()
        assertEquals("content://one", viewModel.uiState.value.slots[0].selectedImageUri)

        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerLaunchHandled()
        viewModel.onPhotoPickerResult(null)
        assertEquals("content://one", viewModel.uiState.value.slots[0].selectedImageUri)

        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerLaunchHandled()
        viewModel.onPhotoPickerResult("content://replacement")
        advanceUntilIdle()
        assertEquals("content://replacement", viewModel.uiState.value.slots[0].selectedImageUri)

        viewModel.removeSelectedImage(1)
        assertNull(viewModel.uiState.value.slots[0].selectedImageUri)
        assertTrue(viewModel.uiState.value.isIncompleteDraftSet)
    }

    @Test
    fun invalidImageIsRepresentedWithoutReplacingThePreviousValidSelection() = runTest {
        val viewModel = viewModel(
            metadata = mapOf(
                "content://valid" to validMetadata(),
                "content://invalid" to ImageCandidateReadResult.Metadata(
                    mimeType = "text/plain",
                    width = 10,
                    height = 10,
                ),
            ),
            imageBytes = mapOf("content://valid" to byteArrayOf(1)),
        )
        viewModel.load("tournament-1")
        select(viewModel, 1, "content://valid")

        select(viewModel, 1, "content://invalid")

        val slot = viewModel.uiState.value.slots[0]
        assertEquals("content://valid", slot.selectedImageUri)
        assertTrue(slot.hasValidatedImage)
        assertEquals(ImageValidationError.NON_IMAGE_CONTENT, slot.lastValidationError)
        assertFalse(viewModel.uiState.value.isCompleteSet)
    }

    @Test
    fun duplicateImageIsRejectedForAnotherRosterSlotWithoutMatchAssociation() = runTest {
        val viewModel = viewModel(
            metadata = mapOf(
                "content://one" to validMetadata(),
                "content://duplicate" to validMetadata(),
            ),
            imageBytes = mapOf(
                "content://one" to byteArrayOf(1, 2),
                "content://duplicate" to byteArrayOf(1, 2),
            ),
        )
        viewModel.load("tournament-1")
        select(viewModel, 1, "content://one")
        select(viewModel, 2, "content://duplicate")

        assertEquals("content://one", viewModel.uiState.value.slots[0].selectedImageUri)
        assertNull(viewModel.uiState.value.slots[1].selectedImageUri)
        assertEquals(
            RosterScreenshotDuplicateSelectionState.SELECTED_FOR_ANOTHER_ROSTER_SCREENSHOT,
            viewModel.uiState.value.slots[1].duplicateSelectionState,
        )
        assertEquals("tournament-1", viewModel.uiState.value.tournamentId)
    }

    @Test
    fun missingTournamentContextBlocksPickerRequest() = runTest {
        val viewModel = viewModel()

        viewModel.load("")
        viewModel.requestPhotoPicker(1)

        assertEquals(
            RosterScreenshotIntakeError.MISSING_TOURNAMENT_ID,
            viewModel.uiState.value.intakeError,
        )
        assertFalse(viewModel.uiState.value.isPhotoPickerLaunchPending)
    }

    @Test
    fun signedOutMetadataIsEmptyAndSelectionDoesNotPreserveOrWrite() = runTest {
        val repository = FakeRosterScreenshotMetadataRepository(listOf(association(1)))
        val localStore = FakeRosterScreenshotLocalImageStore(emptyMap())
        val viewModel = viewModel(
            repository = repository,
            localImageStore = localStore,
            authRepository = FakeAuthRepository(AuthState.SignedOut),
        )

        viewModel.load("tournament-1")
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)

        assertTrue(viewModel.uiState.value.slots.none { it.hasValidatedImage })
        assertEquals(RosterScreenshotIntakeError.AUTHENTICATION_REQUIRED, viewModel.uiState.value.intakeError)
        assertEquals(0, localStore.preserveCalls)
        assertEquals(0, repository.ownerSaveCalls)
    }

    @Test
    fun blankSignedInIdFailsClosedBeforePreservation() = runTest {
        val repository = FakeRosterScreenshotMetadataRepository()
        val localStore = FakeRosterScreenshotLocalImageStore(emptyMap())
        val viewModel = viewModel(
            metadata = mapOf("content://one" to validMetadata()),
            imageBytes = mapOf("content://one" to byteArrayOf(1)),
            repository = repository,
            localImageStore = localStore,
            authRepository = FakeAuthRepository(AuthState.SignedIn(AuthUser("   ", "blank@example.test"))),
        )

        viewModel.load("tournament-1")
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)

        assertEquals(RosterScreenshotIntakeError.AUTHENTICATION_REQUIRED, viewModel.uiState.value.intakeError)
        assertEquals(0, localStore.preserveCalls)
        assertEquals(0, repository.ownerSaveCalls)
    }

    @Test
    fun foreignTournamentFailsBeforePreservationOrMutation() = runTest {
        val repository = FakeRosterScreenshotMetadataRepository(authorized = false)
        val localStore = FakeRosterScreenshotLocalImageStore(emptyMap())
        val viewModel = viewModel(
            metadata = mapOf("content://one" to validMetadata()),
            imageBytes = mapOf("content://one" to byteArrayOf(1)),
            repository = repository,
            localImageStore = localStore,
        )

        viewModel.load("tournament-foreign")
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerLaunchHandled()
        viewModel.onPhotoPickerResult("content://one")
        advanceUntilIdle()

        assertEquals(RosterScreenshotIntakeError.TOURNAMENT_NOT_FOUND, viewModel.uiState.value.intakeError)
        assertEquals(0, localStore.preserveCalls)
        assertEquals(0, repository.ownerSaveCalls)
    }

    @Test
    fun nullOwnerTournamentFailsBeforePreservationOrMutation() = runTest {
        val repository = FakeRosterScreenshotMetadataRepository(authorized = false)
        val localStore = FakeRosterScreenshotLocalImageStore(emptyMap())
        val viewModel = viewModel(
            metadata = mapOf("content://one" to validMetadata()),
            imageBytes = mapOf("content://one" to byteArrayOf(1)),
            repository = repository,
            localImageStore = localStore,
        )

        viewModel.load("tournament-legacy")
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerLaunchHandled()
        viewModel.onPhotoPickerResult("content://one")
        advanceUntilIdle()

        assertEquals(RosterScreenshotIntakeError.TOURNAMENT_NOT_FOUND, viewModel.uiState.value.intakeError)
        assertEquals(0, localStore.preserveCalls)
        assertEquals(0, repository.ownerSaveCalls)
    }

    @Test
    fun foreignTournamentDeleteDoesNotMutateOrCleanLocalFile() = runTest {
        val repository = FakeRosterScreenshotMetadataRepository(
            initial = listOf(association(1)),
            authorized = false,
        )
        val localStore = FakeRosterScreenshotLocalImageStore(emptyMap())
        val viewModel = viewModel(
            repository = repository,
            localImageStore = localStore,
        )

        viewModel.load("tournament-foreign")
        advanceUntilIdle()
        viewModel.removeSelectedImage(1)
        advanceUntilIdle()

        assertEquals(RosterScreenshotIntakeError.TOURNAMENT_NOT_FOUND, viewModel.uiState.value.intakeError)
        assertEquals(0, repository.ownerDeleteCalls)
        assertTrue(localStore.cleanedIndexes.isEmpty())
    }

    @Test
    fun authSwitchReplacesOwnerMetadataFlow() = runTest {
        val auth = FakeAuthRepository(AuthState.SignedIn(AuthUser("owner-a", "a@example.test")))
        val repository = FakeRosterScreenshotMetadataRepository(
            ownerRows = mapOf("owner-a" to listOf(association(1)), "owner-b" to listOf(association(2))),
        )
        val viewModel = viewModel(
            repository = repository,
            localImageStore = FakeRosterScreenshotLocalImageStore(
                mapOf(
                    "screenshots/tournament/roster/1/original.png" to "file:///one.png",
                    "screenshots/tournament/roster/2/original.png" to "file:///two.png",
                ),
            ),
            authRepository = auth,
        )

        viewModel.load("tournament-1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.slots[0].hasValidatedImage)
        assertFalse(viewModel.uiState.value.slots[1].hasValidatedImage)

        auth.state.value = AuthState.SignedIn(AuthUser("owner-b", "b@example.test"))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.slots[0].hasValidatedImage)
        assertTrue(viewModel.uiState.value.slots[1].hasValidatedImage)
    }

    @Test
    fun cropCanBeSetReplacedAndClearedForSelectedRosterScreenshot() = runTest {
        val viewModel = viewModel(
            metadata = mapOf("content://one" to validMetadata()),
            imageBytes = mapOf("content://one" to byteArrayOf(1)),
        )
        viewModel.load("tournament-1")
        select(viewModel, 1, "content://one")

        setCrop(viewModel, 1, "0.10", "0.10", "0.60", "0.70")
        assertTrue(viewModel.uiState.value.slots[0].isCropReady)
        assertEquals(
            NormalizedCropRect(0.10, 0.10, 0.60, 0.70),
            (viewModel.uiState.value.slots[0].cropState as RosterScreenshotCropState.Set).crop,
        )

        setCrop(viewModel, 1, "0.20", "0.20", "0.80", "0.80")
        assertEquals(
            NormalizedCropRect(0.20, 0.20, 0.80, 0.80),
            (viewModel.uiState.value.slots[0].cropState as RosterScreenshotCropState.Set).crop,
        )

        viewModel.clearCrop(1)
        assertFalse(viewModel.uiState.value.slots[0].isCropReady)
        assertEquals(RosterScreenshotCropState.NotSet, viewModel.uiState.value.slots[0].cropState)
    }

    @Test
    fun validatedSelectionInitializesDefaultCropDraftAndEmitsCropNavigation() = runTest {
        val viewModel = viewModel(
            metadata = mapOf("content://one" to validMetadata()),
            imageBytes = mapOf("content://one" to byteArrayOf(1)),
        )
        viewModel.load("tournament-1")
        select(viewModel, 1, "content://one")

        val selectedSlot = viewModel.uiState.value.slots[0]
        assertTrue(selectedSlot.hasValidatedImage)
        assertEquals(1, viewModel.uiState.value.pendingCropNavigationSlotIndex)
        assertEquals(
            RosterScreenshotCropDefaults.FullImageCrop.toRosterScreenshotCropDraft(),
            selectedSlot.cropDraft,
        )
    }

    @Test
    fun cropNavigationRequestCanBeConsumedAndDoesNotRepeat() = runTest {
        val viewModel = viewModel(
            metadata = mapOf("content://one" to validMetadata()),
            imageBytes = mapOf("content://one" to byteArrayOf(1)),
        )
        viewModel.load("tournament-1")
        select(viewModel, 1, "content://one")

        viewModel.onCropNavigationHandled()

        assertNull(viewModel.uiState.value.pendingCropNavigationSlotIndex)
    }

    @Test
    fun confirmCropReturnsTrueForValidCropAndPersistsCropReadyState() = runTest {
        val viewModel = viewModel(
            metadata = mapOf("content://one" to validMetadata()),
            imageBytes = mapOf("content://one" to byteArrayOf(1)),
        )
        viewModel.load("tournament-1")
        select(viewModel, 1, "content://one")

        val confirmed = viewModel.confirmCrop(1)
        advanceUntilIdle()

        assertTrue(confirmed)
        assertTrue(viewModel.uiState.value.slots[0].isCropReady)
        assertEquals(
            RosterScreenshotCropState.Set(RosterScreenshotCropDefaults.FullImageCrop),
            viewModel.uiState.value.slots[0].cropState,
        )
    }

    @Test
    fun requestCropEditorEmitsNavigationWithoutMutatingConfirmedCrop() = runTest {
        val viewModel = viewModel(
            metadata = mapOf("content://one" to validMetadata()),
            imageBytes = mapOf("content://one" to byteArrayOf(1)),
        )
        viewModel.load("tournament-1")
        select(viewModel, 1, "content://one")
        viewModel.onCropNavigationHandled()

        viewModel.requestCropEditor(1)

        assertEquals(1, viewModel.uiState.value.pendingCropNavigationSlotIndex)
        assertFalse(viewModel.uiState.value.slots[0].isCropReady)
        assertEquals(RosterScreenshotCropState.NotSet, viewModel.uiState.value.slots[0].cropState)
    }

    @Test
    fun visualCropChangeReplacesDraftWithoutConfirmingOrPersistingCrop() = runTest {
        val repository = FakeRosterScreenshotMetadataRepository()
        val localStore = FakeRosterScreenshotLocalImageStore(
            mapOf("screenshots/tournament/roster/1/original.png" to "file:///one.png"),
        )
        val viewModel = viewModel(
            metadata = mapOf("content://one" to validMetadata()),
            imageBytes = mapOf("content://one" to byteArrayOf(1)),
            repository = repository,
            localImageStore = localStore,
        )
        viewModel.load("tournament-1")
        select(viewModel, 1, "content://one")
        val visualCrop = NormalizedCropRect(0.15, 0.20, 0.55, 0.70)

        viewModel.onVisualCropChanged(1, visualCrop)

        val slot = viewModel.uiState.value.slots[0]
        assertEquals(visualCrop.toRosterScreenshotCropDraft(), slot.cropDraft)
        assertEquals(RosterScreenshotCropState.NotSet, slot.cropState)
        assertFalse(slot.isCropReady)
        assertNull(repository.current("tournament-1", 1)?.cropLeft)
        assertNull(repository.current("tournament-1", 1)?.cropTop)
        assertNull(repository.current("tournament-1", 1)?.cropRight)
        assertNull(repository.current("tournament-1", 1)?.cropBottom)
    }

    @Test
    fun confirmCropReturnsFalseAndKeepsInvalidStateForTinyCrop() = runTest {
        val viewModel = viewModel(
            metadata = mapOf("content://one" to validMetadata()),
            imageBytes = mapOf("content://one" to byteArrayOf(1)),
        )
        viewModel.load("tournament-1")
        select(viewModel, 1, "content://one")

        viewModel.onCropCoordinateChanged(1, RosterScreenshotCropCoordinate.LEFT, "0.20")
        viewModel.onCropCoordinateChanged(1, RosterScreenshotCropCoordinate.TOP, "0.20")
        viewModel.onCropCoordinateChanged(1, RosterScreenshotCropCoordinate.RIGHT, "0.25")
        viewModel.onCropCoordinateChanged(1, RosterScreenshotCropCoordinate.BOTTOM, "0.25")
        val confirmed = viewModel.confirmCrop(1)

        assertFalse(confirmed)
        assertEquals(RosterScreenshotCropError.TOO_SMALL, viewModel.uiState.value.slots[0].cropError)
        assertFalse(viewModel.uiState.value.slots[0].isCropReady)
    }

    @Test
    fun cropRejectsMissingImagesInvalidBoundsAndTooSmallRectangles() = runTest {
        val viewModel = viewModel(
            metadata = mapOf("content://one" to validMetadata()),
            imageBytes = mapOf("content://one" to byteArrayOf(1)),
        )
        viewModel.load("tournament-1")

        viewModel.setCrop(1)
        assertEquals(
            RosterScreenshotCropError.MISSING_SELECTED_IMAGE,
            viewModel.uiState.value.slots[0].cropError,
        )

        select(viewModel, 1, "content://one")
        setCrop(viewModel, 1, "-0.01", "0.10", "0.80", "0.80")
        assertEquals(RosterScreenshotCropError.OUT_OF_BOUNDS, viewModel.uiState.value.slots[0].cropError)
        assertFalse(viewModel.uiState.value.slots[0].isCropReady)

        setCrop(viewModel, 1, "0.10", "0.10", "0.19", "0.20")
        assertEquals(RosterScreenshotCropError.TOO_SMALL, viewModel.uiState.value.slots[0].cropError)
        assertFalse(viewModel.uiState.value.slots[0].isCropReady)
    }

    @Test
    fun cropReadyStateIsLimitedToThreeSelectedRosterScreenshotPositions() = runTest {
        val imageUris = (1..3).associate { index -> "content://$index" to validMetadata() }
        val imageBytes = (1..3).associate { index -> "content://$index" to byteArrayOf(index.toByte()) }
        val viewModel = viewModel(metadata = imageUris, imageBytes = imageBytes)
        viewModel.load("tournament-1")

        (1..3).forEach { index ->
            select(viewModel, index, "content://$index")
            setCrop(viewModel, index, "0.10", "0.10", "0.60", "0.60")
        }
        viewModel.setCrop(4)

        assertEquals((1..3).toList(), viewModel.uiState.value.slots.map { it.index })
        assertEquals(3, viewModel.uiState.value.cropReadyImageCount)
        assertTrue(viewModel.uiState.value.isCompleteCropReadySet)
    }

    @Test
    fun replacingOrRemovingSelectedImageClearsItsCropState() = runTest {
        val viewModel = viewModel(
            metadata = mapOf(
                "content://one" to validMetadata(),
                "content://replacement" to validMetadata(),
            ),
            imageBytes = mapOf(
                "content://one" to byteArrayOf(1),
                "content://replacement" to byteArrayOf(2),
            ),
        )
        viewModel.load("tournament-1")
        select(viewModel, 1, "content://one")
        setCrop(viewModel, 1, "0.10", "0.10", "0.60", "0.60")

        select(viewModel, 1, "content://replacement")
        assertEquals(RosterScreenshotCropState.NotSet, viewModel.uiState.value.slots[0].cropState)
        assertFalse(viewModel.uiState.value.slots[0].isCropReady)

        viewModel.removeSelectedImage(1)
        assertEquals(RosterScreenshotCropState.NotSet, viewModel.uiState.value.slots[0].cropState)
        assertNull(viewModel.uiState.value.slots[0].selectedImageUri)
    }

    @Test
    fun restoresDurableRosterAssociationAndItsCropMetadataInPositionOrder() = runTest {
        val repository = FakeRosterScreenshotMetadataRepository(
            listOf(
                association(index = 3, crop = NormalizedCropRect(0.10, 0.20, 0.90, 0.80)),
                association(index = 1),
            ),
        )
        val localStore = FakeRosterScreenshotLocalImageStore(
            mapOf(
                "screenshots/tournament/roster/1/original.png" to "file:///one.png",
                "screenshots/tournament/roster/3/original.png" to "file:///three.png",
            ),
        )
        val viewModel = viewModel(
            repository = repository,
            localImageStore = localStore,
        )

        viewModel.load("tournament-1")
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3), viewModel.uiState.value.slots.map { it.index })
        assertEquals("file:///one.png", viewModel.uiState.value.slots[0].selectedImageUri)
        assertNull(viewModel.uiState.value.slots[1].selectedImageUri)
        assertEquals("file:///three.png", viewModel.uiState.value.slots[2].selectedImageUri)
        assertEquals(
            RosterScreenshotCropState.Set(NormalizedCropRect(0.10, 0.20, 0.90, 0.80)),
            viewModel.uiState.value.slots[2].cropState,
        )
    }

    @Test
    fun selectionCropAndRemovalPersistOnlyTournamentScopedRosterAssociation() = runTest {
        val repository = FakeRosterScreenshotMetadataRepository()
        val localStore = FakeRosterScreenshotLocalImageStore(
            mapOf("screenshots/tournament/roster/1/original.png" to "file:///one.png"),
        )
        val viewModel = viewModel(
            metadata = mapOf("content://one" to validMetadata()),
            imageBytes = mapOf("content://one" to byteArrayOf(1)),
            repository = repository,
            localImageStore = localStore,
        )
        viewModel.load("tournament-1")

        select(viewModel, 1, "content://one")
        setCrop(viewModel, 1, "0.10", "0.20", "0.90", "0.80")
        advanceUntilIdle()

        val saved = repository.current("tournament-1", 1)!!
        assertEquals("tournament-1", saved.tournamentId)
        assertEquals(1, saved.rosterScreenshotIndex)
        assertEquals("screenshots/tournament/roster/1/original.png", saved.localRelativePath)
        assertEquals(0.10, saved.cropLeft)
        assertEquals(0.80, saved.cropBottom)

        viewModel.removeSelectedImage(1)
        advanceUntilIdle()

        assertNull(repository.current("tournament-1", 1))
        assertEquals(listOf(1), localStore.cleanedIndexes)
    }

    private suspend fun TestScope.select(
        viewModel: RosterScreenshotIntakeViewModel,
        slotIndex: Int,
        uri: String,
    ) {
        viewModel.requestPhotoPicker(slotIndex)
        viewModel.onPhotoPickerLaunchHandled()
        viewModel.onPhotoPickerResult(uri)
        advanceUntilIdle()
    }

    private fun setCrop(
        viewModel: RosterScreenshotIntakeViewModel,
        slotIndex: Int,
        left: String,
        top: String,
        right: String,
        bottom: String,
    ) {
        viewModel.onCropCoordinateChanged(slotIndex, RosterScreenshotCropCoordinate.LEFT, left)
        viewModel.onCropCoordinateChanged(slotIndex, RosterScreenshotCropCoordinate.TOP, top)
        viewModel.onCropCoordinateChanged(slotIndex, RosterScreenshotCropCoordinate.RIGHT, right)
        viewModel.onCropCoordinateChanged(slotIndex, RosterScreenshotCropCoordinate.BOTTOM, bottom)
        viewModel.setCrop(slotIndex)
    }

    private fun viewModel(
        metadata: Map<String, ImageCandidateReadResult> = emptyMap(),
        imageBytes: Map<String, ByteArray> = emptyMap(),
        repository: RosterScreenshotMetadataRepository = FakeRosterScreenshotMetadataRepository(),
        localImageStore: RosterScreenshotLocalImageStore = NoOpRosterScreenshotLocalImageStore(),
        authRepository: AuthRepository = FakeAuthRepository(AuthState.SignedIn(AuthUser("owner-a", "owner-a@example.test"))),
    ) = RosterScreenshotIntakeViewModel(
        imageCandidateValidator = ImageCandidateValidator(
            ImageCandidateMetadataReader { uri ->
                metadata[uri] ?: ImageCandidateReadResult.Unreadable
            },
        ),
        fingerprintGenerator = ImageSourceFingerprintGenerator(
            streamOpener = ImageSourceStreamOpener { uri ->
                imageBytes[uri]?.let(::ByteArrayInputStream)
            },
            coroutineDispatcher = dispatcher,
        ),
        authRepository = authRepository,
        rosterScreenshotMetadataRepository = repository,
        rosterScreenshotLocalImageStore = localImageStore,
    )

    private fun validMetadata() = ImageCandidateReadResult.Metadata(
        mimeType = "image/png",
        width = 100,
        height = 100,
    )

    private fun association(
        index: Int,
        crop: NormalizedCropRect? = null,
    ) = RosterScreenshotMetadataEntity(
        tournamentId = "tournament-1",
        rosterScreenshotIndex = index,
        localRelativePath = "screenshots/tournament/roster/$index/original.png",
        mimeType = "image/png",
        width = 100,
        height = 100,
        sha256 = ("$index").repeat(64),
        validationStatus = RosterScreenshotValidationStatus.VALID.name,
        cropLeft = crop?.left,
        cropTop = crop?.top,
        cropRight = crop?.right,
        cropBottom = crop?.bottom,
        createdAt = 1,
        updatedAt = 2,
    )

    private class FakeRosterScreenshotMetadataRepository(
        initial: List<RosterScreenshotMetadataEntity> = emptyList(),
        private val authorized: Boolean = true,
        private val ownerRows: Map<String, List<RosterScreenshotMetadataEntity>> = emptyMap(),
    ) : RosterScreenshotMetadataRepository {
        private val state = MutableStateFlow(initial.sortedBy { it.rosterScreenshotIndex })
        var ownerSaveCalls = 0
        var ownerDeleteCalls = 0

        override fun observeByTournamentId(tournamentId: String): Flow<List<RosterScreenshotMetadataEntity>> = state

        override fun observeByTournamentIdAndOwner(
            tournamentId: String,
            ownerUserId: String,
        ): Flow<List<RosterScreenshotMetadataEntity>> = flowOf(ownerRows[ownerUserId] ?: state.value)

        override suspend fun existsByTournamentIdAndOwner(tournamentId: String, ownerUserId: String): Boolean = authorized

        override suspend fun readByTournamentAndIndexAndOwner(
            tournamentId: String,
            index: Int,
            ownerUserId: String,
        ): RosterScreenshotMetadataEntity? = current(tournamentId, index)

        override suspend fun findDuplicateFingerprintAndOwner(
            tournamentId: String,
            sha256: String,
            index: Int,
            ownerUserId: String,
        ): RosterScreenshotMetadataEntity? = state.value.firstOrNull {
            it.tournamentId == tournamentId && it.rosterScreenshotIndex != index && it.sha256 == sha256
        }

        override suspend fun saveOrReplace(
            metadata: RosterScreenshotMetadataEntity,
        ): RosterScreenshotAssociationSaveResult {
            if (metadata.rosterScreenshotIndex !in 1..3) {
                return RosterScreenshotAssociationSaveResult.InvalidIndex
            }
            if (state.value.any {
                    it.tournamentId == metadata.tournamentId &&
                        it.rosterScreenshotIndex != metadata.rosterScreenshotIndex &&
                        it.sha256 == metadata.sha256
                }
            ) {
                return RosterScreenshotAssociationSaveResult.DuplicateFingerprint
            }
            state.value = state.value
                .filterNot {
                    it.tournamentId == metadata.tournamentId &&
                        it.rosterScreenshotIndex == metadata.rosterScreenshotIndex
                }
                .plus(metadata)
                .sortedBy { it.rosterScreenshotIndex }
            return RosterScreenshotAssociationSaveResult.Saved
        }

        override suspend fun saveOrReplaceByOwner(
            metadata: RosterScreenshotMetadataEntity,
            ownerUserId: String,
        ): RosterScreenshotAssociationSaveResult {
            ownerSaveCalls++
            return if (authorized) saveOrReplace(metadata)
            else RosterScreenshotAssociationSaveResult.TournamentNotFound
        }

        override suspend fun deleteByTournamentAndIndex(tournamentId: String, index: Int) {
            state.value = state.value.filterNot {
                it.tournamentId == tournamentId && it.rosterScreenshotIndex == index
            }
        }

        override suspend fun deleteByTournamentAndIndexAndOwner(
            tournamentId: String,
            index: Int,
            ownerUserId: String,
        ): com.hoggamers.rankforge.data.local.RosterScreenshotAssociationDeleteResult {
            if (!authorized) {
                return com.hoggamers.rankforge.data.local.RosterScreenshotAssociationDeleteResult.TournamentNotFound
            }
            ownerDeleteCalls++
            deleteByTournamentAndIndex(tournamentId, index)
            return com.hoggamers.rankforge.data.local.RosterScreenshotAssociationDeleteResult.Deleted
        }

        fun current(tournamentId: String, index: Int): RosterScreenshotMetadataEntity? =
            state.value.firstOrNull {
                it.tournamentId == tournamentId && it.rosterScreenshotIndex == index
            }
    }

    private class FakeAuthRepository(
        initialState: AuthState,
    ) : AuthRepository {
        val state = MutableStateFlow(initialState)

        override fun observeAuthState() = state
        override suspend fun restoreSession() = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String) =
            AuthOperationResult.Success(com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome.SignedIn)
        override suspend fun login(email: String, password: String) =
            AuthOperationResult.Success(com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome.SignedIn)
        override suspend fun logout() =
            AuthOperationResult.Success(com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome.SignedOutLocally)
    }

    private class FakeRosterScreenshotLocalImageStore(
        private val displayUris: Map<String, String>,
    ) : RosterScreenshotLocalImageStore {
        val cleanedIndexes = mutableListOf<Int>()
        var preserveCalls = 0

        override suspend fun preserve(
            tournamentId: String,
            rosterScreenshotIndex: Int,
            selectedUri: String,
        ): RosterScreenshotLocalImageStoreResult = RosterScreenshotLocalImageStoreResult.Preserved(
            localRelativePath = "screenshots/tournament/roster/$rosterScreenshotIndex/original.png",
            displayUri = displayUris.getOrDefault(
                "screenshots/tournament/roster/$rosterScreenshotIndex/original.png",
                "file:///synthetic.png",
            ),
        ).also { preserveCalls++ }

        override suspend fun cleanup(tournamentId: String, rosterScreenshotIndex: Int) {
            cleanedIndexes += rosterScreenshotIndex
        }

        override fun displayUriOrNull(localRelativePath: String): String? = displayUris[localRelativePath]
    }
}
