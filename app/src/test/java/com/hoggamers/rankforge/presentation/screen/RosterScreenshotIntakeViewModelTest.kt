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
