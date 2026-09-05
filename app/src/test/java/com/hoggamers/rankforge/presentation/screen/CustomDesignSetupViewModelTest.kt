package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.cloud.CustomDesignSaveAction
import com.hoggamers.rankforge.data.cloud.CustomDesignSaveFailure
import com.hoggamers.rankforge.data.cloud.CustomDesignSaveRequest
import com.hoggamers.rankforge.data.cloud.CustomDesignSaveResult
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreAction
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreResult
import com.hoggamers.rankforge.data.cloud.RestoredCustomDesign
import com.hoggamers.rankforge.data.cloud.CustomDesignDeleteAction
import com.hoggamers.rankforge.data.cloud.CustomDesignDeleteResult
import com.hoggamers.rankforge.data.cloud.CustomDesignSavedIdDiscoveryAction
import com.hoggamers.rankforge.data.cloud.CustomDesignSavedIdDiscoveryResult
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrRunner
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrSource
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrStatus
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignRawOcrDocument
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorDetector
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignGridBuilder
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrLabels
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignRowCoordinateSource
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import com.hoggamers.rankforge.domain.ocr.customdesign.resolveCustomDesignEffectiveGridGeometry
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
    fun initialDiscoveryNoneLeavesFreshStateUnchanged() = runTest {
        val viewModel = viewModel(
            discoveryAction = CustomDesignSavedIdDiscoveryAction {
                CustomDesignSavedIdDiscoveryResult.None
            },
        )

        advanceUntilIdle()

        assertEquals(CustomDesignSetupUiState(), viewModel.uiState.value)
    }

    @Test
    fun initialDiscoveryFoundRestoresExactlyOnceWithoutRunningOcr() = runTest {
        val design = restoredDesign()
        var restoreCalls = 0
        val runner = FakeCustomDesignOcrRunner()
        val viewModel = viewModel(
            runner = runner,
            discoveryAction = CustomDesignSavedIdDiscoveryAction {
                CustomDesignSavedIdDiscoveryResult.Found(design.customDesignId)
            },
            restoreAction = CustomDesignRestoreAction {
                restoreCalls += 1
                CustomDesignRestoreResult.Success(design)
            },
        )

        advanceUntilIdle()

        assertEquals(1, restoreCalls)
        assertEquals(design.customDesignId, viewModel.uiState.value.savedCustomDesignId)
        assertEquals(0, runner.sources.size)
    }

    @Test
    fun ambiguousInitialDiscoveryDoesNotRestore() = runTest {
        var restoreCalls = 0
        val viewModel = viewModel(
            discoveryAction = CustomDesignSavedIdDiscoveryAction {
                CustomDesignSavedIdDiscoveryResult.Ambiguous
            },
            restoreAction = CustomDesignRestoreAction {
                restoreCalls += 1
                CustomDesignRestoreResult.Failed(
                    com.hoggamers.rankforge.data.cloud.CustomDesignRestoreFailure.READ_FAILED,
                )
            },
        )

        advanceUntilIdle()

        assertEquals(0, restoreCalls)
        assertEquals(CustomDesignSetupUiState(), viewModel.uiState.value)
    }

    @Test
    fun discoveryResultAfterUserSelectedImageIsIgnored() = runTest {
        val result = CompletableDeferred<CustomDesignSavedIdDiscoveryResult>()
        var restoreCalls = 0
        val design = restoredDesign()
        val viewModel = viewModel(
            discoveryAction = CustomDesignSavedIdDiscoveryAction { result.await() },
            restoreAction = CustomDesignRestoreAction {
                restoreCalls += 1
                CustomDesignRestoreResult.Success(design)
            },
        )

        selectImage(viewModel)
        advanceUntilIdle()
        result.complete(CustomDesignSavedIdDiscoveryResult.Found(design.customDesignId))
        advanceUntilIdle()

        assertEquals(0, restoreCalls)
        assertEquals(null, viewModel.uiState.value.savedCustomDesignId)
        assertEquals("content://picker/custom-design", viewModel.uiState.value.selectedImageReference)
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
    fun uploadRequestWithoutImagePreservesExistingLabelValidation() {
        val viewModel = viewModel()

        viewModel.requestPhotoPicker()

        assertEquals(
            CustomDesignLabelField.entries.toSet(),
            viewModel.uiState.value.validationErrors,
        )
        assertFalse(viewModel.uiState.value.isPhotoPickerLaunchPending)
    }

    @Test
    fun saveActionWithoutImageDoesNotLaunchPhotoPicker() {
        var saveCalls = 0
        val viewModel = viewModel(
            saveAction = CustomDesignSaveAction {
                saveCalls += 1
                CustomDesignSaveResult.Success("unexpected", "unexpected")
            },
        )
        viewModel.onTeamNameChanged("TEAM NAME")
        viewModel.onWinChanged("WIN")
        viewModel.onTotalKillsChanged("ELIM.")
        viewModel.onPositionPointsChanged("POS.")
        viewModel.onTotalPointsChanged("TOTAL")

        viewModel.onSaveActionRequested()

        assertFalse(viewModel.uiState.value.isPhotoPickerLaunchPending)
        assertEquals(0, saveCalls)
    }

    @Test
    fun saveActionWithUnsavedImageInvokesCloudSaveOnce() = runTest {
        var saveCalls = 0
        val viewModel = viewModel(
            saveAction = CustomDesignSaveAction {
                saveCalls += 1
                CustomDesignSaveResult.Success("saved-id", "saved-path")
            },
        )

        selectImage(viewModel)
        advanceUntilIdle()
        viewModel.onSaveActionRequested()
        advanceUntilIdle()

        assertEquals(1, saveCalls)
        assertEquals(CustomDesignSaveStatus.SAVED, viewModel.uiState.value.saveStatus)
        assertFalse(viewModel.uiState.value.isPhotoPickerLaunchPending)
    }

    @Test
    fun saveSuccessAcknowledgementReturnsToIdleAndPreservesSavedDesign() = runTest {
        val viewModel = viewModel(
            saveAction = CustomDesignSaveAction {
                CustomDesignSaveResult.Success("saved-id", "saved-path")
            },
        )

        selectImage(viewModel)
        advanceUntilIdle()
        viewModel.saveNewCustomDesign()
        advanceUntilIdle()

        assertEquals(CustomDesignSaveStatus.SAVED, viewModel.uiState.value.saveStatus)
        viewModel.onSaveSuccessMessageHandled()

        assertEquals(CustomDesignSaveStatus.IDLE, viewModel.uiState.value.saveStatus)
        assertEquals("saved-id", viewModel.uiState.value.savedCustomDesignId)
        assertEquals("content://picker/custom-design", viewModel.uiState.value.selectedImageReference)
    }

    @Test
    fun saveSuccessAcknowledgementDoesNotAlterNonSavedState() {
        val viewModel = viewModel()

        viewModel.onSaveSuccessMessageHandled()
        assertEquals(CustomDesignSaveStatus.IDLE, viewModel.uiState.value.saveStatus)

        viewModel.saveNewCustomDesign()
        assertEquals(CustomDesignSaveStatus.FAILED, viewModel.uiState.value.saveStatus)
        viewModel.onSaveSuccessMessageHandled()
        assertEquals(CustomDesignSaveStatus.FAILED, viewModel.uiState.value.saveStatus)
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
        assertEquals(5, viewModel.uiState.value.editableGridGeometry?.columnX?.size)
        assertEquals(12, viewModel.uiState.value.editableGridGeometry?.rowY?.size)
    }

    @Test
    fun ocrSuccessWithMissingHeaderStillCreatesCompleteEditableColumns() = runTest {
        val viewModel = viewModel(runner = FakeCustomDesignOcrRunner(documentWithHeader("WIN", 700)))

        selectImage(viewModel)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.gridGeometry?.columnX?.size)
        assertEquals(5, viewModel.uiState.value.editableGridGeometry?.columnX?.size)
        assertEquals(12, viewModel.uiState.value.editableGridGeometry?.rowY?.size)
    }

    @Test
    fun manualCorrectionOnFallbackLineSurvivesOcrFailure() = runTest {
        val viewModel = viewModel(
            runner = FakeCustomDesignOcrRunner(
                failure = IllegalStateException("engine failure"),
            ),
        )

        selectImage(viewModel)
        advanceUntilIdle()
        viewModel.setManualColumnX(CustomDesignAnchorField.TOTAL_KILLS, 777f)
        viewModel.setManualRowY(7, 888f)

        assertEquals(777f, viewModel.uiState.value.manualGridOverrides.columnX[CustomDesignAnchorField.TOTAL_KILLS])
        assertEquals(888f, viewModel.uiState.value.manualGridOverrides.rowY[7])
        assertEquals(5, viewModel.uiState.value.editableGridGeometry?.columnX?.size)
        assertEquals(12, viewModel.uiState.value.editableGridGeometry?.rowY?.size)
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

    @Test
    fun saveUsesExactLabelsAndEffectiveManualGeometryWithoutRerunningOcr() = runTest {
        val runner = FakeCustomDesignOcrRunner(documentWithGridRows())
        var calls = 0
        var captured: CustomDesignSaveRequest? = null
        val viewModel = viewModel(
            runner = runner,
            saveAction = CustomDesignSaveAction { request ->
                calls += 1
                captured = request
                CustomDesignSaveResult.Success("saved-id", "saved-path")
            },
        )

        selectImage(viewModel)
        advanceUntilIdle()
        viewModel.setManualColumnX(CustomDesignAnchorField.WIN, 700f)
        viewModel.setManualRowY(2, 410f)
        viewModel.saveNewCustomDesign()
        advanceUntilIdle()

        assertEquals(CustomDesignSaveStatus.SAVED, viewModel.uiState.value.saveStatus)
        assertEquals("saved-id", viewModel.uiState.value.savedCustomDesignId)
        assertEquals(1, calls)
        assertEquals(1, runner.sources.size)
        assertEquals(
            CustomDesignOcrLabels("TEAM NAME", "WIN", "ELIM.", "POS.", "TOTAL"),
            captured?.labels,
        )
        assertEquals(700f, captured?.effectiveGridGeometry?.columnX?.get(CustomDesignAnchorField.WIN))
        assertEquals(410f, captured?.effectiveGridGeometry?.rowY?.get(2))
        assertEquals(CustomDesignColumnTextColors.allBlack(), captured?.textColors)

        viewModel.saveNewCustomDesign()
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test
    fun saveWithoutVerifiedDraftDoesNotInvokeCloudAction() {
        var calls = 0
        val viewModel = viewModel(
            saveAction = CustomDesignSaveAction {
                calls += 1
                CustomDesignSaveResult.Success("saved-id", "saved-path")
            },
        )

        viewModel.saveNewCustomDesign()

        assertEquals(CustomDesignSaveStatus.FAILED, viewModel.uiState.value.saveStatus)
        assertEquals(0, calls)
    }

    @Test
    fun successfulDeleteClearsSavedStateWithoutRerunningOcr() = runTest {
        val runner = FakeCustomDesignOcrRunner()
        val design = restoredDesign()
        var deleteCalls = 0
        val viewModel = viewModel(
            runner = runner,
            restoreAction = CustomDesignRestoreAction {
                CustomDesignRestoreResult.Success(design)
            },
            deleteAction = CustomDesignDeleteAction {
                deleteCalls += 1
                CustomDesignDeleteResult.Success
            },
        )

        viewModel.restoreSavedCustomDesign(design.customDesignId)
        advanceUntilIdle()
        viewModel.deleteSavedCustomDesign()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, deleteCalls)
        assertEquals(CustomDesignDeleteStatus.DELETED, state.deleteStatus)
        assertEquals(null, state.savedCustomDesignId)
        assertEquals(null, state.selectedImageReference)
        assertEquals(null, state.draft)
        assertEquals("", state.teamNameLabel)
        assertEquals(null, state.sourceImageWidth)
        assertEquals(null, state.sourceImageHeight)
        assertEquals(CustomDesignSaveStatus.IDLE, state.saveStatus)
        assertEquals(CustomDesignRestoreStatus.IDLE, state.restoreStatus)
        assertEquals(CustomDesignOcrStatus.IDLE, state.ocrStatus)
        assertEquals(null, state.ocrAnchors)
        assertEquals(null, state.gridGeometry)
        assertEquals(0, runner.sources.size)
    }

    @Test
    fun failedDeletePreservesStateAndCanBeRetried() = runTest {
        val design = restoredDesign()
        var deleteCalls = 0
        val viewModel = viewModel(
            restoreAction = CustomDesignRestoreAction {
                CustomDesignRestoreResult.Success(design)
            },
            deleteAction = CustomDesignDeleteAction {
                deleteCalls += 1
                if (deleteCalls == 1) {
                    CustomDesignDeleteResult.Failed(
                        com.hoggamers.rankforge.data.cloud.CustomDesignDeleteFailure.STORAGE_DELETE,
                    )
                } else {
                    CustomDesignDeleteResult.Success
                }
            },
        )

        viewModel.restoreSavedCustomDesign(design.customDesignId)
        advanceUntilIdle()
        val before = viewModel.uiState.value
        viewModel.deleteSavedCustomDesign()
        advanceUntilIdle()
        val afterFailure = viewModel.uiState.value
        assertEquals(CustomDesignDeleteStatus.FAILED, afterFailure.deleteStatus)
        assertEquals(before.savedCustomDesignId, afterFailure.savedCustomDesignId)
        assertEquals(before.draft, afterFailure.draft)
        assertEquals(before.manualGridOverrides, afterFailure.manualGridOverrides)

        viewModel.deleteSavedCustomDesign()
        advanceUntilIdle()
        assertEquals(2, deleteCalls)
        assertEquals(CustomDesignDeleteStatus.DELETED, viewModel.uiState.value.deleteStatus)
        assertEquals(null, viewModel.uiState.value.savedCustomDesignId)
    }

    @Test
    fun deleteIsIgnoredForUnsavedDesignAndDuplicateWhileDeleting() = runTest {
        var deleteCalls = 0
        val pending = CompletableDeferred<CustomDesignDeleteResult>()
        val viewModel = viewModel(
            deleteAction = CustomDesignDeleteAction {
                deleteCalls += 1
                pending.await()
            },
        )

        viewModel.deleteSavedCustomDesign()
        assertEquals(0, deleteCalls)

        val design = restoredDesign()
        val restoredViewModel = viewModel(
            restoreAction = CustomDesignRestoreAction { CustomDesignRestoreResult.Success(design) },
            deleteAction = CustomDesignDeleteAction {
                deleteCalls += 1
                pending.await()
            },
        )
        restoredViewModel.restoreSavedCustomDesign(design.customDesignId)
        advanceUntilIdle()
        restoredViewModel.deleteSavedCustomDesign()
        restoredViewModel.deleteSavedCustomDesign()
        runCurrent()
        assertEquals(1, deleteCalls)
        assertEquals(CustomDesignDeleteStatus.DELETING, restoredViewModel.uiState.value.deleteStatus)
        pending.complete(CustomDesignDeleteResult.Success)
        advanceUntilIdle()
    }

    @Test
    fun concurrentSaveAttemptsInvokeCloudActionOnlyOnce() = runTest {
        val result = CompletableDeferred<CustomDesignSaveResult>()
        var calls = 0
        val viewModel = viewModel(
            saveAction = CustomDesignSaveAction {
                calls += 1
                result.await()
            },
        )

        selectImage(viewModel)
        advanceUntilIdle()
        viewModel.saveNewCustomDesign()
        viewModel.saveNewCustomDesign()
        runCurrent()

        assertEquals(CustomDesignSaveStatus.SAVING, viewModel.uiState.value.saveStatus)
        assertEquals(1, calls)
        result.complete(CustomDesignSaveResult.Success("saved-id", "saved-path"))
        advanceUntilIdle()
        assertEquals(CustomDesignSaveStatus.SAVED, viewModel.uiState.value.saveStatus)
    }

    @Test
    fun failedCloudSaveDoesNotPublishSavedState() = runTest {
        val viewModel = viewModel(
            saveAction = CustomDesignSaveAction {
                CustomDesignSaveResult.Failed(CustomDesignSaveFailure.DATABASE_INSERT)
            },
        )

        selectImage(viewModel)
        advanceUntilIdle()
        viewModel.saveNewCustomDesign()
        advanceUntilIdle()

        assertEquals(CustomDesignSaveStatus.FAILED, viewModel.uiState.value.saveStatus)
        assertEquals(null, viewModel.uiState.value.savedCustomDesignId)
    }

    @Test
    fun restoreHydratesExactDesignWithoutRunningOcr() = runTest {
        val runner = FakeCustomDesignOcrRunner()
        val design = restoredDesign()
        var restoreCalls = 0
        val viewModel = viewModel(
            runner = runner,
            restoreAction = CustomDesignRestoreAction { id ->
                restoreCalls += 1
                assertEquals(design.customDesignId, id)
                CustomDesignRestoreResult.Success(design)
            },
        )

        viewModel.restoreSavedCustomDesign(design.customDesignId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, restoreCalls)
        assertEquals(0, runner.sources.size)
        assertEquals(CustomDesignRestoreStatus.RESTORED, state.restoreStatus)
        assertEquals(CustomDesignSaveStatus.SAVED, state.saveStatus)
        assertEquals(design.customDesignId, state.savedCustomDesignId)
        assertEquals(design.localImageReference, state.selectedImageReference)
        assertEquals(design.sourceWidth, state.sourceImageWidth)
        assertEquals(design.sourceHeight, state.sourceImageHeight)
        assertEquals(design.labels.teamName, state.teamNameLabel)
        assertEquals(design.labels.win, state.winLabel)
        assertEquals(design.labels.totalKills, state.totalKillsLabel)
        assertEquals(design.labels.positionPoints, state.positionPointsLabel)
        assertEquals(design.labels.totalPoints, state.totalPointsLabel)
        assertEquals(design.textColors, state.textColors)
        assertEquals(CustomDesignOcrStatus.IDLE, state.ocrStatus)
        assertEquals(null, state.ocrAnchors)
        assertEquals(null, state.gridGeometry)
        assertEquals(design.geometry.columnX, state.manualGridOverrides.columnX)
        assertEquals(design.geometry.rowY, state.manualGridOverrides.rowY)
        assertEquals(
            design.geometry,
            resolveCustomDesignEffectiveGridGeometry(state.editableGridGeometry, state.manualGridOverrides),
        )
    }

    @Test
    fun restoredDesignIsImmutableAndCannotStartSavePickerOrOcr() = runTest {
        val design = restoredDesign()
        var saves = 0
        val viewModel = viewModel(
            runner = FakeCustomDesignOcrRunner(),
            saveAction = CustomDesignSaveAction {
                saves += 1
                CustomDesignSaveResult.Success("unexpected", "unexpected")
            },
            restoreAction = CustomDesignRestoreAction {
                CustomDesignRestoreResult.Success(design)
            },
        )

        viewModel.restoreSavedCustomDesign(design.customDesignId)
        advanceUntilIdle()
        viewModel.onTeamNameChanged("changed")
        viewModel.setManualColumnX(CustomDesignAnchorField.WIN, 1f)
        viewModel.setManualRowY(1, 1f)
        viewModel.clearManualGridOverrides()
        viewModel.requestPhotoPicker()
        viewModel.onSaveActionRequested()
        viewModel.saveNewCustomDesign()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(design.labels.teamName, state.teamNameLabel)
        assertEquals(design.geometry.columnX, state.manualGridOverrides.columnX)
        assertEquals(design.geometry.rowY, state.manualGridOverrides.rowY)
        assertFalse(state.isPhotoPickerLaunchPending)
        assertEquals(0, saves)
        assertEquals(CustomDesignRestoreStatus.RESTORED, state.restoreStatus)
    }

    @Test
    fun saveActionIsIgnoredWhileSaving() = runTest {
        val result = CompletableDeferred<CustomDesignSaveResult>()
        var saveCalls = 0
        val viewModel = viewModel(
            saveAction = CustomDesignSaveAction {
                saveCalls += 1
                result.await()
            },
        )

        selectImage(viewModel)
        advanceUntilIdle()
        viewModel.onSaveActionRequested()
        viewModel.onSaveActionRequested()
        runCurrent()

        assertEquals(1, saveCalls)
        assertEquals(CustomDesignSaveStatus.SAVING, viewModel.uiState.value.saveStatus)
        result.complete(CustomDesignSaveResult.Success("saved-id", "saved-path"))
        advanceUntilIdle()
    }

    @Test
    fun saveActionIsIgnoredWhileRestoring() = runTest {
        val result = CompletableDeferred<CustomDesignRestoreResult>()
        var saveCalls = 0
        val viewModel = viewModel(
            saveAction = CustomDesignSaveAction {
                saveCalls += 1
                CustomDesignSaveResult.Success("unexpected", "unexpected")
            },
            restoreAction = CustomDesignRestoreAction { result.await() },
        )

        viewModel.restoreSavedCustomDesign("a2000000-0000-0000-0000-000000000001")
        runCurrent()
        viewModel.onSaveActionRequested()

        assertEquals(CustomDesignRestoreStatus.RESTORING, viewModel.uiState.value.restoreStatus)
        assertFalse(viewModel.uiState.value.isPhotoPickerLaunchPending)
        assertEquals(0, saveCalls)
        result.complete(
            CustomDesignRestoreResult.Failed(
                com.hoggamers.rankforge.data.cloud.CustomDesignRestoreFailure.READ_FAILED,
            ),
        )
        advanceUntilIdle()
    }

    @Test
    fun failedRestorePreservesExistingStateWithoutPartialHydration() = runTest {
        val runner = FakeCustomDesignOcrRunner()
        val viewModel = viewModel(
            runner = runner,
            restoreAction = CustomDesignRestoreAction {
                CustomDesignRestoreResult.Failed(
                    com.hoggamers.rankforge.data.cloud.CustomDesignRestoreFailure.READ_FAILED,
                )
            },
        )
        selectImage(viewModel)
        advanceUntilIdle()
        val before = viewModel.uiState.value

        viewModel.restoreSavedCustomDesign("a2000000-0000-0000-0000-000000000001")
        advanceUntilIdle()

        val after = viewModel.uiState.value
        assertEquals(CustomDesignRestoreStatus.FAILED, after.restoreStatus)
        assertEquals(before.draft, after.draft)
        assertEquals(before.selectedImageReference, after.selectedImageReference)
        assertEquals(before.teamNameLabel, after.teamNameLabel)
        assertEquals(before.manualGridOverrides, after.manualGridOverrides)
    }

    @Test
    fun staleOcrCannotOverwriteRestoredDesign() = runTest {
        val pending = CompletableDeferred<CustomDesignRawOcrDocument>()
        val runner = object : CustomDesignOcrRunner {
            var calls = 0

            override suspend fun recognize(source: CustomDesignOcrSource): CustomDesignRawOcrDocument {
                calls += 1
                return withContext(NonCancellable) { pending.await() }
            }
        }
        val design = restoredDesign()
        val viewModel = viewModel(
            runner = runner,
            restoreAction = CustomDesignRestoreAction {
                CustomDesignRestoreResult.Success(design)
            },
        )

        selectImage(viewModel)
        runCurrent()
        viewModel.restoreSavedCustomDesign(design.customDesignId)
        advanceUntilIdle()
        pending.complete(documentWithGridRows(winLeft = 100))
        advanceUntilIdle()

        assertEquals(1, runner.calls)
        assertEquals(design.customDesignId, viewModel.uiState.value.savedCustomDesignId)
        assertEquals(design.localImageReference, viewModel.uiState.value.selectedImageReference)
        assertEquals(CustomDesignOcrStatus.IDLE, viewModel.uiState.value.ocrStatus)
        assertEquals(null, viewModel.uiState.value.ocrAnchors)
        assertEquals(null, viewModel.uiState.value.gridGeometry)
    }

    private fun restoredDesign() = RestoredCustomDesign(
        customDesignId = "a2000000-0000-0000-0000-000000000001",
        ownerUserId = "a1000000-0000-0000-0000-000000000001",
        localImageReference = "D:/app/files/custom-designs/users/a1000000-0000-0000-0000-000000000001/a2000000-0000-0000-0000-000000000001/original.png",
        sourceWidth = 1080,
        sourceHeight = 1350,
        labels = CustomDesignOcrLabels(" TEAM NAME ", "WIN", "ELIM.", "POS.", "TOTAL"),
        geometry = CustomDesignEffectiveGridGeometry(
            sourceWidth = 1080,
            sourceHeight = 1350,
            columnX = linkedMapOf(
                CustomDesignAnchorField.TEAM_NAME to 900f,
                CustomDesignAnchorField.WIN to 100f,
                CustomDesignAnchorField.TOTAL_KILLS to 700f,
                CustomDesignAnchorField.POSITION_POINTS to 300f,
                CustomDesignAnchorField.TOTAL_POINTS to 500f,
            ),
            rowY = (1..12).associateWith { it * 100f },
        ),
        textColors = CustomDesignColumnTextColors.fromMap(
            mapOf(
                CustomDesignAnchorField.TEAM_NAME to "#112233",
                CustomDesignAnchorField.WIN to "#223344",
                CustomDesignAnchorField.TOTAL_KILLS to "#334455",
                CustomDesignAnchorField.POSITION_POINTS to "#445566",
                CustomDesignAnchorField.TOTAL_POINTS to "#556677",
            ),
        )!!,
    )

    private fun viewModel(
        readResult: ImageCandidateMetadataReader = ImageCandidateMetadataReader {
            ImageCandidateReadResult.Metadata("image/png", width = 1080, height = 1350)
        },
        runner: CustomDesignOcrRunner = FakeCustomDesignOcrRunner(),
        saveAction: CustomDesignSaveAction = CustomDesignSaveAction {
            CustomDesignSaveResult.Success("saved-id", "saved-path")
        },
        restoreAction: CustomDesignRestoreAction = CustomDesignRestoreAction {
            CustomDesignRestoreResult.Failed(
                com.hoggamers.rankforge.data.cloud.CustomDesignRestoreFailure.NOT_FOUND,
            )
        },
        deleteAction: CustomDesignDeleteAction = CustomDesignDeleteAction {
            CustomDesignDeleteResult.Failed(
                com.hoggamers.rankforge.data.cloud.CustomDesignDeleteFailure.DATABASE_DELETE,
            )
        },
        discoveryAction: CustomDesignSavedIdDiscoveryAction = CustomDesignSavedIdDiscoveryAction {
            CustomDesignSavedIdDiscoveryResult.None
        },
    ) = CustomDesignSetupViewModel(
        imageCandidateValidator = ImageCandidateValidator(readResult),
        customDesignOcrRunner = runner,
        customDesignAnchorDetector = CustomDesignAnchorDetector(),
        customDesignGridBuilder = CustomDesignGridBuilder(),
        customDesignSaveAction = saveAction,
        customDesignRestoreAction = restoreAction,
        customDesignDeleteAction = deleteAction,
        customDesignSavedIdDiscoveryAction = discoveryAction,
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
