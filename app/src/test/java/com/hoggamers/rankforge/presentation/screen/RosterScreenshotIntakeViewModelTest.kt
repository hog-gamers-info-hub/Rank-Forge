package com.hoggamers.rankforge.presentation.screen

import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    )

    private fun validMetadata() = ImageCandidateReadResult.Metadata(
        mimeType = "image/png",
        width = 100,
        height = 100,
    )
}
