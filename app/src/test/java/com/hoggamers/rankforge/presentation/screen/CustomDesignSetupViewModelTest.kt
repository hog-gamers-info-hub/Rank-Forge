package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrRunner
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrSource
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrStatus
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignRawOcrDocument
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorDetector
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignGridBuilder
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignRowCoordinateSource
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
class CustomDesignSetupViewModelTest {
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
    fun uploadRequestRejectsEveryBlankLabelBeforeOpeningPicker() {
        val viewModel = viewModel()

        viewModel.requestPhotoPicker()

        assertEquals(
            CustomDesignLabelField.entries.toSet(),
            viewModel.uiState.value.validationErrors,
        )
        assertFalse(viewModel.uiState.value.isPhotoPickerLaunchPending)
        assertFalse(viewModel.uiState.value.hasUsableDraft)
    }

    @Test
    fun exactTypedLabelsAreRetainedAndValidLabelsAllowPickerLaunch() {
        val viewModel = viewModel()
        val labels = listOf(" TEAM NAME ", "Win", "ELIM.", "POS.", "TOTAL")

        viewModel.onTeamNameChanged(labels[0])
        viewModel.onWinChanged(labels[1])
        viewModel.onTotalKillsChanged(labels[2])
        viewModel.onPositionPointsChanged(labels[3])
        viewModel.onTotalPointsChanged(labels[4])
        viewModel.requestPhotoPicker()

        val state = viewModel.uiState.value
        assertEquals(labels[0], state.teamNameLabel)
        assertEquals(labels[1], state.winLabel)
        assertEquals(labels[2], state.totalKillsLabel)
        assertEquals(labels[3], state.positionPointsLabel)
        assertEquals(labels[4], state.totalPointsLabel)
        assertTrue(state.validationErrors.isEmpty())
        assertTrue(state.isPhotoPickerLaunchPending)
    }

    @Test
    fun validImageSelectionCreatesDraftWithSourceDimensionsAndExactLabels() = runTest {
        val runner = FakeCustomDesignOcrRunner()
        val viewModel = viewModel(
            ImageCandidateMetadataReader {
                ImageCandidateReadResult.Metadata(
                    mimeType = "image/png",
                    width = 1080,
                    height = 1350,
                )
            },
            runner = runner,
        )
        viewModel.onTeamNameChanged("TEAM NAME")
        viewModel.onWinChanged("WIN")
        viewModel.onTotalKillsChanged("ELIM.")
        viewModel.onPositionPointsChanged("POS.")
        viewModel.onTotalPointsChanged("TOTAL")
        viewModel.requestPhotoPicker()
        viewModel.onPhotoPickerLaunchHandled()
        viewModel.onPhotoPickerResult("content://picker/custom-design")

        advanceUntilIdle()

        assertEquals("content://picker/custom-design", viewModel.uiState.value.selectedImageReference)
        assertEquals(1080, viewModel.uiState.value.sourceImageWidth)
        assertEquals(1350, viewModel.uiState.value.sourceImageHeight)
        assertEquals(
            CustomDesignDraft(
                imageReference = "content://picker/custom-design",
                imageWidth = 1080,
                imageHeight = 1350,
                teamNameLabel = "TEAM NAME",
                winLabel = "WIN",
                totalKillsLabel = "ELIM.",
                positionPointsLabel = "POS.",
                totalPointsLabel = "TOTAL",
            ),
            viewModel.uiState.value.draft,
        )
        assertEquals(1, runner.sources.size)
        assertEquals(CustomDesignOcrStatus.COMPLETED, viewModel.uiState.value.ocrStatus)
    }

    @Test
    fun ocrResultPopulatesPartialAnchorState() = runTest {
        val runner = FakeCustomDesignOcrRunner(documentWithHeader("WIN", 700))
        val viewModel = viewModel(runner = runner)

        selectImage(viewModel)
        advanceUntilIdle()

        assertEquals(CustomDesignOcrStatus.COMPLETED, viewModel.uiState.value.ocrStatus)
        assertEquals(1, viewModel.uiState.value.ocrAnchors?.columnX?.size)
    }

    @Test
    fun completedOcrAlsoPopulatesDerivedGridGeometry() = runTest {
        val viewModel = viewModel(runner = FakeCustomDesignOcrRunner(documentWithGridRows()))

        selectImage(viewModel)
        advanceUntilIdle()

        val grid = viewModel.uiState.value.gridGeometry
        assertEquals(CustomDesignOcrStatus.COMPLETED, viewModel.uiState.value.ocrStatus)
        assertEquals(1080, grid?.sourceWidth)
        assertEquals(1350, grid?.sourceHeight)
        assertEquals(CustomDesignRowCoordinateSource.OCR, grid?.rowY?.get(2)?.source)
        assertEquals(CustomDesignRowCoordinateSource.INTERPOLATED, grid?.rowY?.get(4)?.source)
    }

    @Test
    fun ocrFailurePreservesTheUsableDraft() = runTest {
        val runner = FakeCustomDesignOcrRunner(failure = IllegalStateException("engine failure"))
        val viewModel = viewModel(runner = runner)

        selectImage(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasUsableDraft)
        assertEquals(CustomDesignOcrStatus.FAILED, viewModel.uiState.value.ocrStatus)
        assertEquals("content://picker/custom-design", viewModel.uiState.value.draft?.imageReference)
        assertEquals(1080, viewModel.uiState.value.draft?.imageWidth)
        assertEquals(null, viewModel.uiState.value.gridGeometry)
    }

    @Test
    fun laterOcrFailureClearsExistingGridButPreservesDraft() = runTest {
        val runner = object : CustomDesignOcrRunner {
            var calls = 0

            override suspend fun recognize(source: CustomDesignOcrSource): CustomDesignRawOcrDocument {
                calls += 1
                if (calls == 1) return documentWithGridRows()
                throw IllegalStateException("engine failure")
            }
        }
        val viewModel = viewModel(runner = runner)

        selectImage(viewModel, "content://picker/first")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.gridGeometry != null)

        selectImage(viewModel, "content://picker/second")
        advanceUntilIdle()

        assertEquals(CustomDesignOcrStatus.FAILED, viewModel.uiState.value.ocrStatus)
        assertEquals(null, viewModel.uiState.value.gridGeometry)
        assertEquals("content://picker/second", viewModel.uiState.value.draft?.imageReference)
    }

    @Test
    fun changingLabelsRematchesCachedOcrWithoutRunningEngineAgain() = runTest {
        val runner = FakeCustomDesignOcrRunner(documentWithHeader("WIN", 700))
        val viewModel = viewModel(runner = runner)

        selectImage(viewModel)
        advanceUntilIdle()
        viewModel.onWinChanged("WINS")

        assertEquals(1, runner.sources.size)
        assertEquals(CustomDesignOcrStatus.COMPLETED, viewModel.uiState.value.ocrStatus)
        assertTrue(viewModel.uiState.value.ocrAnchors?.columnX?.isEmpty() == true)
        assertEquals("WINS", viewModel.uiState.value.draft?.winLabel)
    }

    @Test
    fun labelOnlyRematchRebuildsGridWithoutRunningEngineAgain() = runTest {
        val runner = FakeCustomDesignOcrRunner(documentWithGridRows())
        val viewModel = viewModel(runner = runner)

        selectImage(viewModel)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.gridGeometry?.rowY?.containsKey(4) == true)

        viewModel.onWinChanged("WINS")

        assertEquals(1, runner.sources.size)
        assertTrue(viewModel.uiState.value.gridGeometry?.rowY?.containsKey(4) == true)
        assertFalse(CustomDesignAnchorField.WIN in viewModel.uiState.value.gridGeometry!!.columnX)
    }

    @Test
    fun manualUpdatesAreValidatedAndDoNotRerunOcr() = runTest {
        val runner = FakeCustomDesignOcrRunner(documentWithGridRows())
        val viewModel = viewModel(runner = runner)

        selectImage(viewModel)
        advanceUntilIdle()
        val automaticWin = viewModel.uiState.value.gridGeometry
            ?.columnX
            ?.get(CustomDesignAnchorField.WIN)

        viewModel.setManualColumnX(CustomDesignAnchorField.WIN, 700f)
        viewModel.setManualRowY(2, 410f)
        viewModel.setManualColumnX(CustomDesignAnchorField.WIN, -1f)
        viewModel.setManualRowY(2, Float.NaN)
        viewModel.setManualRowY(0, 200f)

        assertEquals(700f, viewModel.uiState.value.manualGridOverrides.columnX[CustomDesignAnchorField.WIN])
        assertEquals(410f, viewModel.uiState.value.manualGridOverrides.rowY[2])
        assertEquals(automaticWin, viewModel.uiState.value.gridGeometry?.columnX?.get(CustomDesignAnchorField.WIN))
        assertEquals(1, runner.sources.size)
    }

    @Test
    fun clearingManualOverrideFallsBackToAutomaticCoordinate() = runTest {
        val viewModel = viewModel(runner = FakeCustomDesignOcrRunner(documentWithGridRows()))

        selectImage(viewModel)
        advanceUntilIdle()
        viewModel.setManualColumnX(CustomDesignAnchorField.WIN, 700f)
        viewModel.setManualRowY(2, 410f)

        viewModel.clearManualColumnX(CustomDesignAnchorField.WIN)
        viewModel.clearManualRowY(2)

        assertFalse(viewModel.uiState.value.manualGridOverrides.columnX.containsKey(CustomDesignAnchorField.WIN))
        assertFalse(viewModel.uiState.value.manualGridOverrides.rowY.containsKey(2))
    }

    @Test
    fun manualOverridesSurviveTemporaryBlankLabelAndLabelRematch() = runTest {
        val runner = FakeCustomDesignOcrRunner(documentWithGridRows())
        val viewModel = viewModel(runner = runner)

        selectImage(viewModel)
        advanceUntilIdle()
        viewModel.setManualColumnX(CustomDesignAnchorField.WIN, 700f)
        viewModel.setManualRowY(2, 410f)

        viewModel.onWinChanged("")
        assertEquals(700f, viewModel.uiState.value.manualGridOverrides.columnX[CustomDesignAnchorField.WIN])
        assertEquals(null, viewModel.uiState.value.gridGeometry)

        viewModel.onWinChanged("WINS")

        assertEquals(700f, viewModel.uiState.value.manualGridOverrides.columnX[CustomDesignAnchorField.WIN])
        assertEquals(410f, viewModel.uiState.value.manualGridOverrides.rowY[2])
        assertEquals(1, runner.sources.size)
    }

    @Test
    fun selectingDifferentImageClearsManualOverrides() = runTest {
        val viewModel = viewModel(runner = FakeCustomDesignOcrRunner(documentWithGridRows()))

        selectImage(viewModel, "content://picker/first")
        advanceUntilIdle()
        viewModel.setManualColumnX(CustomDesignAnchorField.WIN, 700f)
        viewModel.setManualRowY(2, 410f)

        selectImage(viewModel, "content://picker/second")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.manualGridOverrides.columnX.isEmpty())
        assertTrue(viewModel.uiState.value.manualGridOverrides.rowY.isEmpty())
    }

    @Test
    fun changingImageInvalidatesCachedOcrAndStartsNewRun() = runTest {
        val runner = FakeCustomDesignOcrRunner()
        val viewModel = viewModel(runner = runner)

        selectImage(viewModel, "content://picker/first")
        advanceUntilIdle()
        selectImage(viewModel, "content://picker/second")
        advanceUntilIdle()

        assertEquals(2, runner.sources.size)
        assertEquals("content://picker/second", viewModel.uiState.value.draft?.imageReference)
        assertEquals(CustomDesignOcrStatus.COMPLETED, viewModel.uiState.value.ocrStatus)
    }

    @Test
    fun changingImageInvalidatesOldGridBeforeNewOcrCompletes() = runTest {
        val second = CompletableDeferred<CustomDesignRawOcrDocument>()
        val runner = object : CustomDesignOcrRunner {
            var calls = 0

            override suspend fun recognize(source: CustomDesignOcrSource): CustomDesignRawOcrDocument {
                calls += 1
                return if (calls == 1) documentWithGridRows() else second.await()
            }
        }
        val viewModel = viewModel(runner = runner)

        selectImage(viewModel, "content://picker/first")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.gridGeometry != null)

        selectImage(viewModel, "content://picker/second")
        runCurrent()
        assertEquals(null, viewModel.uiState.value.gridGeometry)

        second.complete(documentWithGridRows(winLeft = 720))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.gridGeometry != null)
    }

    @Test
    fun staleOcrResultCannotOverwriteNewerImageState() = runTest {
        val first = CompletableDeferred<CustomDesignRawOcrDocument>()
        val second = CompletableDeferred<CustomDesignRawOcrDocument>()
        val runner = object : CustomDesignOcrRunner {
            val sources = mutableListOf<CustomDesignOcrSource>()

            override suspend fun recognize(source: CustomDesignOcrSource): CustomDesignRawOcrDocument {
                sources += source
                return if (sources.size == 1) {
                    withContext(NonCancellable) { first.await() }
                } else {
                    second.await()
                }
            }
        }
        val viewModel = viewModel(runner = runner)

        selectImage(viewModel, "content://picker/first")
        runCurrent()
        selectImage(viewModel, "content://picker/second")
        runCurrent()
        second.complete(documentWithGridRows(winLeft = 700))
        advanceUntilIdle()
        viewModel.setManualColumnX(CustomDesignAnchorField.WIN, 800f)
        first.complete(documentWithGridRows(winLeft = 100))
        advanceUntilIdle()

        assertEquals("content://picker/second", viewModel.uiState.value.draft?.imageReference)
        assertEquals(710f, viewModel.uiState.value.ocrAnchors?.columnX?.get(CustomDesignAnchorField.WIN))
        assertEquals(710f, viewModel.uiState.value.gridGeometry?.columnX?.get(CustomDesignAnchorField.WIN))
        assertEquals(800f, viewModel.uiState.value.manualGridOverrides.columnX[CustomDesignAnchorField.WIN])
    }

    private fun viewModel(
        readResult: ImageCandidateMetadataReader = ImageCandidateMetadataReader {
            ImageCandidateReadResult.Metadata("image/png", width = 1080, height = 1350)
        },
        runner: CustomDesignOcrRunner = FakeCustomDesignOcrRunner(),
    ) = CustomDesignSetupViewModel(
        imageCandidateValidator = ImageCandidateValidator(readResult),
        customDesignOcrRunner = runner,
        customDesignAnchorDetector = CustomDesignAnchorDetector(),
        customDesignGridBuilder = CustomDesignGridBuilder(),
    )

    private fun selectImage(
        viewModel: CustomDesignSetupViewModel,
        uri: String = "content://picker/custom-design",
    ) {
        viewModel.onTeamNameChanged("TEAM NAME")
        viewModel.onWinChanged("WIN")
        viewModel.onTotalKillsChanged("ELIM.")
        viewModel.onPositionPointsChanged("POS.")
        viewModel.onTotalPointsChanged("TOTAL")
        viewModel.requestPhotoPicker()
        viewModel.onPhotoPickerLaunchHandled()
        viewModel.onPhotoPickerResult(uri)
    }

    private class FakeCustomDesignOcrRunner(
        private val document: CustomDesignRawOcrDocument = CustomDesignRawOcrDocument(1080, 1350, emptyList()),
        private val failure: Throwable? = null,
    ) : CustomDesignOcrRunner {
        val sources = mutableListOf<CustomDesignOcrSource>()

        override suspend fun recognize(source: CustomDesignOcrSource): CustomDesignRawOcrDocument {
            sources += source
            failure?.let { throw it }
            return document
        }
    }

    private fun documentWithHeader(text: String, left: Int) = CustomDesignRawOcrDocument(
        sourceWidth = 1080,
        sourceHeight = 1350,
        blocks = listOf(
            RawOcrBlock(
                text = text,
                geometry = null,
                recognizedLanguage = null,
                confidence = RawOcrConfidence.Unavailable,
                lines = listOf(
                    RawOcrLine(
                        text = text,
                        geometry = null,
                        recognizedLanguage = null,
                        confidence = RawOcrConfidence.Unavailable,
                        elements = listOf(
                            RawOcrElement(
                                text = text,
                                geometry = RawOcrGeometry(
                                    boundingBox = RawOcrBoundingBox(left, 100, left + 20, 120),
                                    cornerPoints = null,
                                ),
                                recognizedLanguage = null,
                                confidence = RawOcrConfidence.Unavailable,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun documentWithGridRows(winLeft: Int = 450) = CustomDesignRawOcrDocument(
        sourceWidth = 1080,
        sourceHeight = 1350,
        blocks = listOf(
            block(
                line(element("TEAM NAME", 300, 100, 400, 120)),
                line(element("WIN", winLeft, 100, winLeft + 20, 120)),
                line(element("ELIM.", 520, 100, 570, 120)),
                line(element("POS.", 650, 100, 700, 120)),
                line(element("TOTAL", 780, 100, 830, 120)),
                line(element("2", 100, 300, 120, 330)),
                line(element("3", 100, 354, 120, 384)),
                line(element("5", 100, 461, 120, 491)),
                line(element("6", 100, 515, 120, 545)),
            ),
        ),
    )

    private fun block(vararg lines: RawOcrLine) = RawOcrBlock(
        text = lines.joinToString(" ") { it.text },
        geometry = null,
        recognizedLanguage = null,
        confidence = RawOcrConfidence.Unavailable,
        lines = lines.toList(),
    )

    private fun line(element: RawOcrElement) = RawOcrLine(
        text = element.text,
        geometry = null,
        recognizedLanguage = null,
        confidence = RawOcrConfidence.Unavailable,
        elements = listOf(element),
    )

    private fun element(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = RawOcrElement(
        text = text,
        geometry = RawOcrGeometry(
            boundingBox = RawOcrBoundingBox(left, top, right, bottom),
            cornerPoints = null,
        ),
        recognizedLanguage = null,
        confidence = RawOcrConfidence.Unavailable,
    )
}
