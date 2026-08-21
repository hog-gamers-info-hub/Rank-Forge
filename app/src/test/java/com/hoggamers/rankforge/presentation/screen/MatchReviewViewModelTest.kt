package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.export.AndroidExportBlockedReason
import com.hoggamers.rankforge.data.export.AndroidExportResult
import com.hoggamers.rankforge.data.export.AndroidExportType
import com.hoggamers.rankforge.data.export.GoogleSheetsMatchExportExecutionResult
import com.hoggamers.rankforge.data.export.GoogleSheetsMatchExportRemoteDataSource
import com.hoggamers.rankforge.data.export.ResultDocumentWriteFailure
import com.hoggamers.rankforge.data.export.ResultDocumentWriteResult
import com.hoggamers.rankforge.data.export.ResultDocumentWriter
import com.hoggamers.rankforge.data.export.ResultDownloadCoordinator
import com.hoggamers.rankforge.data.export.ResultDownloadExecutionResult
import com.hoggamers.rankforge.data.export.ResultDownloadFailure
import com.hoggamers.rankforge.data.export.ResultDownloadRequest
import com.hoggamers.rankforge.data.export.ResultDownloadScope
import com.hoggamers.rankforge.data.export.ResultExportFileFormat
import com.hoggamers.rankforge.domain.export.MatchExportRow
import com.hoggamers.rankforge.data.cloud.MatchCloudIdentity
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.data.cloud.ScreenshotStorageUploadResult
import com.hoggamers.rankforge.data.cloud.ScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.ScreenshotMetadataCloudDataSource
import com.hoggamers.rankforge.data.cloud.ScreenshotMetadataCloudFailure
import com.hoggamers.rankforge.data.cloud.ScreenshotMetadataCloudPayload
import com.hoggamers.rankforge.data.cloud.ScreenshotMetadataCloudResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchResultScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotMetadataEntity
import com.hoggamers.rankforge.data.local.ScreenshotMetadataFailureCode
import com.hoggamers.rankforge.data.local.ScreenshotMetadataRepository
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.tournament.CreateMatchInput
import com.hoggamers.rankforge.domain.tournament.CreateMatchResult
import com.hoggamers.rankforge.domain.tournament.CreateMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
import java.time.LocalDate
import java.nio.file.Files
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
private const val HOSTED_ID_TEST_TOURNAMENT_ID = "f1e7a9b6-0543-4786-a328-fe927ca90814"
private const val HOSTED_ID_TEST_LOCAL_MATCH_ID = "2c7ed56f-e9e3-44b3-a830-0b9ef0866438"
private const val HOSTED_ID_TEST_HOSTED_MATCH_ID = "152837b7-65f3-3b03-a797-113848cbbf6d"

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
                id = TOURNAMENT_ID,
                name = "Summer Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        repository.saveTeamNames(
            TOURNAMENT_ID,
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        matchId = "review-match-id"
        repository.createDraftMatch(
            Match(
                id = matchId,
                tournamentId = TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun reviewShowsTwelveRowsAndRestoredDraftValues() = runTest {
        repository.saveRoster(
            TOURNAMENT_ID,
            1,
            listOf(RosterPlayer(TOURNAMENT_ID, 1, "Player One")),
        )
        (1..12).forEach { slotNumber ->
            repository.saveDraftMatchValue(
                TOURNAMENT_ID,
                matchId,
                slotNumber,
                placementInput = slotNumber.toString(),
                killsInput = (slotNumber - 1).toString(),
            )
        }

        val viewModel = reviewViewModel()
        viewModel.load(TOURNAMENT_ID, matchId)
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
        viewModel.load(TOURNAMENT_ID, matchId)
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
        viewModel.load(TOURNAMENT_ID, matchId)
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
        viewModel.load(TOURNAMENT_ID, matchId)
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
        viewModel.load(TOURNAMENT_ID, matchId)
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
        viewModel.load(TOURNAMENT_ID, matchId)
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
    fun validatedDraftScreenshotCanLinkReplaceAndUnlinkWithoutChangingMatchData() = runTest {
        val viewModel = reviewViewModel()
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        val beforeMatch = repository.observeMatchById(matchId).first()

        viewModel.onPhotoPickerResult("content://picker/first")
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()

        assertEquals("content://picker/first", viewModel.uiState.value.linkedScreenshotUri)
        assertEquals(beforeMatch, repository.observeMatchById(matchId).first())

        viewModel.onPhotoPickerResult("content://picker/second")
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()
        assertEquals("content://picker/second", viewModel.uiState.value.linkedScreenshotUri)

        viewModel.unlinkScreenshot()
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.linkedScreenshotUri)
        assertEquals(beforeMatch, repository.observeMatchById(matchId).first())
    }

    @Test
    fun linkedLegacyDraftScreenshotCanOpenOcrReviewWithoutCompleteResultInputs() = runTest {
        val viewModel = reviewViewModel()
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.onPhotoPickerResult("content://picker/ocr")
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canOpenOcrReview)
        assertEquals(TOURNAMENT_ID, viewModel.uiState.value.tournamentId)
        assertEquals(matchId, viewModel.uiState.value.matchId)

        viewModel.openOcrReview()

        assertEquals(MatchReviewNavigation.OCR_REVIEW, viewModel.uiState.value.navigation)
        assertEquals(TOURNAMENT_ID, viewModel.uiState.value.tournamentId)
        assertEquals(matchId, viewModel.uiState.value.matchId)
    }

    @Test
    fun upperReadyOnlyCanOpenOcrReviewForPreflight() = runTest {
        val viewModel = reviewViewModelWithReadyResultRoles(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.openOcrReview()

        assertFalse(viewModel.uiState.value.canOpenOcrReview)
        assertEquals(MatchReviewNavigation.OCR_REVIEW, viewModel.uiState.value.navigation)
    }

    @Test
    fun lowerReadyOnlyCanOpenOcrReviewForPreflight() = runTest {
        val viewModel = reviewViewModelWithReadyResultRoles(MatchResultScreenshotRole.MATCH_RESULT_LOWER)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.openOcrReview()

        assertFalse(viewModel.uiState.value.canOpenOcrReview)
        assertEquals(MatchReviewNavigation.OCR_REVIEW, viewModel.uiState.value.navigation)
    }

    @Test
    fun bothResultRolesReadyOpenOcrReview() = runTest {
        val viewModel = reviewViewModelWithReadyResultRoles(
            MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.openOcrReview()

        assertTrue(viewModel.uiState.value.canOpenOcrReview)
        assertEquals(MatchReviewNavigation.OCR_REVIEW, viewModel.uiState.value.navigation)
    }

    @Test
    fun handledOcrReviewNavigationIsCleared() = runTest {
        val viewModel = reviewViewModelWithReadyResultRoles(
            MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.openOcrReview()
        viewModel.onNavigationHandled()

        assertNull(viewModel.uiState.value.navigation)
    }

    @Test
    fun invalidScreenshotDoesNotBlockOcrReviewNavigationBeforePreflight() = runTest {
        val viewModel = reviewViewModel(
            imageCandidateValidator = ImageCandidateValidator(
                ImageCandidateMetadataReader {
                    ImageCandidateReadResult.Metadata("text/plain", width = 1080, height = 1920)
                },
            ),
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.onPhotoPickerResult("content://picker/not-image")
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()
        viewModel.openOcrReview()

        assertFalse(viewModel.uiState.value.canOpenOcrReview)
        assertEquals(MatchReviewNavigation.OCR_REVIEW, viewModel.uiState.value.navigation)
        assertEquals(ScreenshotLinkError.INVALID_IMAGE, viewModel.uiState.value.screenshotLinkError)
    }

    @Test
    fun validLinkedScreenshotIsPreservedByteForByteInMatchScopedStorage() = runTest {
        val uri = "content://picker/preserved"
        val bytes = byteArrayOf(4, 5, 6, 7)
        val viewModel = reviewViewModel(
            screenshotDuplicateDetector = duplicateDetector(mapOf(uri to bytes)),
            localImagePreserver = localImagePreserver(mapOf(uri to bytes), "image/jpeg"),
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.onPhotoPickerResult(uri)
        advanceUntilIdle()

        viewModel.linkScreenshot()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(uri, state.linkedScreenshotUri)
        assertTrue(state.isScreenshotLocallyPreserved)
        assertNotNull(state.preservedScreenshotRelativePath)
        assertTrue(state.preservedScreenshotRelativePath!!.endsWith("original.jpg"))
    }

    @Test
    fun validPreservedScreenshotUploadsFromTheLocalFileAndReportsSuccess() = runTest {
        val uri = "content://picker/upload"
        val bytes = byteArrayOf(10, 20, 30)
        val uploader = RecordingScreenshotStorageUploader(
            ScreenshotStorageUploadResult.Uploaded(
                screenshotObjectPath("user-id", matchId, "png"),
            ),
        )
        val viewModel = reviewViewModel(
            screenshotDuplicateDetector = duplicateDetector(mapOf(uri to bytes)),
            localImagePreserver = localImagePreserver(mapOf(uri to bytes)),
            screenshotStorageUploader = uploader,
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.onPhotoPickerResult(uri)
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()

        assertEquals(1, uploader.calls.size)
        assertEquals(bytes.toList(), uploader.calls.single().readBytes().toList())
        assertTrue(viewModel.uiState.value.isScreenshotUploaded)
        assertEquals(
            screenshotObjectPath("user-id", matchId, "png"),
            viewModel.uiState.value.screenshotUploadObjectPath,
        )
    }

    @Test
    fun restoredResultAssetExposesOnlyTheExistingLocalPreviewFile() = runTest {
        val preserver = localImagePreserver()
        val relativePath = "screenshots/$TOURNAMENT_ID/$matchId/result/upper/original.png"
        val file = preserver.resolveRelativePath(relativePath)!!.apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val assetRepository = FakeMatchResultScreenshotAssetRepository(
            listOf(resultScreenshotAsset(relativePath)),
        )
        val viewModel = reviewViewModel(
            localImagePreserver = preserver,
            matchResultScreenshotAssetRepository = assetRepository,
        )

        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        val slot = viewModel.uiState.value.resultScreenshots.slot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        assertEquals(file.toURI().toString(), slot.localPreviewUri)
        assertTrue(slot.hasLinkedAsset)
        assertFalse(slot.isLocalFileMissing)
    }

    @Test
    fun missingRestoredResultAssetHasNoPreviewAndKeepsMissingState() = runTest {
        val relativePath = "screenshots/$TOURNAMENT_ID/$matchId/result/upper/missing.png"
        val viewModel = reviewViewModel(
            matchResultScreenshotAssetRepository = FakeMatchResultScreenshotAssetRepository(
                listOf(resultScreenshotAsset(relativePath)),
            ),
        )

        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        val slot = viewModel.uiState.value.resultScreenshots.slot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        assertNull(slot.localPreviewUri)
        assertTrue(slot.isLocalFileMissing)
    }

    @Test
    fun freshlyPreservedResultPreviewUsesLocalFileAndSurvivesRestoredMerge() = runTest {
        val selectedUri = "content://picker/result-preview"
        val preserver = localImagePreserver(mapOf(selectedUri to byteArrayOf(4, 5, 6)))
        val assetRepository = FakeMatchResultScreenshotAssetRepository()
        val viewModel = reviewViewModel(
            screenshotDuplicateDetector = duplicateDetector(mapOf(selectedUri to byteArrayOf(4, 5, 6))),
            matchResultScreenshotDuplicateDetector = MatchResultScreenshotDuplicateDetector(
                ImageSourceFingerprintGenerator(
                    ImageSourceStreamOpener { byteArrayOf(4, 5, 6).inputStream() },
                    Dispatchers.Unconfined,
                ),
            ),
            localImagePreserver = preserver,
            matchResultScreenshotAssetRepository = assetRepository,
            screenshotOwnerProvider = FixedScreenshotOwnerProvider("owner-id"),
        )

        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_UPPER, selectedUri)
        advanceUntilIdle()

        val previewUri = viewModel.uiState.value.resultScreenshots
            .slot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
            .localPreviewUri
        assertNotNull(previewUri)
        assertTrue(previewUri!!.startsWith("file:"))
        assertFalse(previewUri == selectedUri)
        assertTrue(assetRepository.assets.value.isNotEmpty())
    }

    @Test
    fun metadataIsCreatedAfterSuccessfulPreservationAndUpdatedAfterStorageUpload() = runTest {
        val uri = "content://picker/metadata"
        val bytes = byteArrayOf(1, 2, 3, 4)
        val metadataRepository = FakeScreenshotMetadataRepository()
        val uploader = RecordingScreenshotStorageUploader(
            ScreenshotStorageUploadResult.Uploaded(
                screenshotObjectPath("owner-id", matchId, "png"),
            ),
        )
        val viewModel = reviewViewModel(
            screenshotDuplicateDetector = duplicateDetector(mapOf(uri to bytes)),
            localImagePreserver = localImagePreserver(mapOf(uri to bytes)),
            screenshotStorageUploader = uploader,
            screenshotMetadataRepository = metadataRepository,
            screenshotOwnerProvider = FixedScreenshotOwnerProvider("owner-id"),
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.onPhotoPickerResult(uri)
        advanceUntilIdle()

        viewModel.linkScreenshot()
        advanceUntilIdle()

        val metadata = metadataRepository.metadata.value!!
        assertEquals(matchId, metadata.matchId)
        assertEquals(TOURNAMENT_ID, metadata.tournamentId)
        assertEquals("owner-id", metadata.ownerUserId)
        assertEquals("png", metadata.fileExtension)
        assertEquals("image/png", metadata.mimeType)
        assertEquals(1080, metadata.width)
        assertEquals(1920, metadata.height)
        assertEquals(bytes.size.toLong(), metadata.byteSize)
        assertEquals(ScreenshotLocalStatus.PRESERVED.name, metadata.localStatus)
        assertEquals(ScreenshotUploadStatus.UPLOADED.name, metadata.uploadStatus)
        assertEquals(2, metadata.revision)
        assertTrue(viewModel.uiState.value.isScreenshotUploaded)
        assertEquals(ScreenshotMetadataUploadUiStatus.UPLOADED, viewModel.uiState.value.screenshotMetadata!!.uploadStatus)
    }

    @Test
    fun cloudMetadataFailureMarksLocalMetadataFailedWithControlledCode() = runTest {
        val metadataRepository = FakeScreenshotMetadataRepository()
        val cloudDataSource = FakeScreenshotMetadataCloudDataSource(
            upsertResult = ScreenshotMetadataCloudResult.Failed(ScreenshotMetadataCloudFailure.AUTHORIZATION),
        )
        val viewModel = reviewViewModel(
            screenshotStorageUploader = RecordingScreenshotStorageUploader(
                ScreenshotStorageUploadResult.Uploaded(
                    screenshotObjectPath("owner-id", matchId, "png"),
                ),
            ),
            screenshotMetadataRepository = metadataRepository,
            screenshotMetadataCloudDataSource = cloudDataSource,
            screenshotOwnerProvider = FixedScreenshotOwnerProvider("owner-id"),
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.onPhotoPickerResult("content://picker/cloud-metadata-failure")
        advanceUntilIdle()

        viewModel.linkScreenshot()
        advanceUntilIdle()

        assertEquals(ScreenshotUploadStatus.FAILED.name, metadataRepository.metadata.value!!.uploadStatus)
        assertEquals(ScreenshotMetadataFailureCode.RLS_DENIED.name, metadataRepository.metadata.value!!.uploadFailureCode)
        assertEquals(ScreenshotUploadError.RLS_DENIED, viewModel.uiState.value.screenshotUploadError)
    }

    @Test
    fun roomMetadataWriteFailureKeepsPreservedFileAndDoesNotUpload() = runTest {
        val metadataRepository = FakeScreenshotMetadataRepository().apply {
            failWrites = true
        }
        val uploader = RecordingScreenshotStorageUploader(
            ScreenshotStorageUploadResult.Uploaded("object/path"),
        )
        val viewModel = reviewViewModel(
            screenshotStorageUploader = uploader,
            screenshotMetadataRepository = metadataRepository,
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.onPhotoPickerResult("content://picker/room-failure")
        advanceUntilIdle()

        viewModel.linkScreenshot()
        advanceUntilIdle()

        assertEquals(ScreenshotPreservationError.ROOM_WRITE_FAILED, viewModel.uiState.value.screenshotPreservationError)
        assertEquals(null, viewModel.uiState.value.linkedScreenshotUri)
        assertEquals(0, uploader.calls.size)
    }

    @Test
    fun uploadFailureKeepsLocalPreservedFileAndShowsControlledError() = runTest {
        val uri = "content://picker/upload-failure"
        val uploader = RecordingScreenshotStorageUploader(
            ScreenshotStorageUploadResult.Failed(
                com.hoggamers.rankforge.data.cloud.ScreenshotStorageUploadFailure.NETWORK,
            ),
        )
        val viewModel = reviewViewModel(
            screenshotStorageUploader = uploader,
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.onPhotoPickerResult(uri)
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isScreenshotLocallyPreserved)
        assertEquals(ScreenshotUploadError.NETWORK, viewModel.uiState.value.screenshotUploadError)
    }

    @Test
    fun uploadFailureCanBeRetriedWithoutLosingTheLocalFile() = runTest {
        val uploader = RecordingScreenshotStorageUploader(
            ScreenshotStorageUploadResult.Failed(
                com.hoggamers.rankforge.data.cloud.ScreenshotStorageUploadFailure.NETWORK,
            ),
        )
        val viewModel = reviewViewModel(screenshotStorageUploader = uploader)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.onPhotoPickerResult("content://picker/retry")
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()

        uploader.result = ScreenshotStorageUploadResult.Uploaded("users/user/tournaments/t/matches/m/original.png")
        viewModel.retryScreenshotUpload()
        advanceUntilIdle()

        assertEquals(2, uploader.calls.size)
        assertTrue(viewModel.uiState.value.isScreenshotLocallyPreserved)
        assertTrue(viewModel.uiState.value.isScreenshotUploaded)
    }

    @Test
    fun preservationSourceFailureLeavesLinkUnsetAndShowsControlledError() = runTest {
        val uri = "content://picker/unreadable"
        val viewModel = reviewViewModel(
            screenshotDuplicateDetector = duplicateDetector(mapOf(uri to byteArrayOf(1))),
            localImagePreserver = LocalImagePreserver(
                appPrivateRoot = Files.createTempDirectory("rank-forge-source-error").toFile(),
                sourceStreamOpener = ImageSourceStreamOpener { null },
                mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
                ioDispatcher = Dispatchers.Unconfined,
            ),
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.onPhotoPickerResult(uri)
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.linkedScreenshotUri)
        assertEquals(
            ScreenshotPreservationError.SOURCE_READ_FAILED,
            viewModel.uiState.value.screenshotPreservationError,
        )
    }

    @Test
    fun preservationCopyFailureShowsControlledError() = runTest {
        val uri = "content://picker/copy-failure"
        val operations = object : LocalImageFileOperations {
            override fun ensureDirectory(directory: File): Boolean =
                directory.isDirectory || (directory.mkdirs() && directory.isDirectory)
            override fun createTempFile(directory: File): File =
                File.createTempFile("original-", ".tmp", directory)
            override fun openOutput(file: File): OutputStream = throw IOException("copy failed")
            override fun atomicMove(source: File, target: File): Boolean = false
            override fun listFiles(directory: File): List<File>? = directory.listFiles()?.toList() ?: emptyList()
            override fun delete(file: File): Boolean = !file.exists() || file.delete()
        }
        val viewModel = reviewViewModel(
            screenshotDuplicateDetector = duplicateDetector(mapOf(uri to byteArrayOf(1))),
            localImagePreserver = LocalImagePreserver(
                appPrivateRoot = Files.createTempDirectory("rank-forge-copy-error").toFile(),
                sourceStreamOpener = ImageSourceStreamOpener { byteArrayOf(1).inputStream() },
                mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
                fileOperations = operations,
                ioDispatcher = Dispatchers.Unconfined,
            ),
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.onPhotoPickerResult(uri)
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()

        assertEquals(
            ScreenshotPreservationError.COPY_FAILED,
            viewModel.uiState.value.screenshotPreservationError,
        )
        assertNull(viewModel.uiState.value.linkedScreenshotUri)
    }

    @Test
    fun unlinkCleanupFailureShowsControlledErrorWithoutCrashing() = runTest {
        val uri = "content://picker/cleanup"
        val operations = object : LocalImageFileOperations {
            override fun ensureDirectory(directory: File): Boolean =
                directory.isDirectory || (directory.mkdirs() && directory.isDirectory)
            override fun createTempFile(directory: File): File =
                File.createTempFile("original-", ".tmp", directory)
            override fun openOutput(file: File): OutputStream = FileOutputStream(file)
            override fun atomicMove(source: File, target: File): Boolean {
                if (target.exists()) target.delete()
                return source.renameTo(target)
            }
            override fun listFiles(directory: File): List<File>? = directory.listFiles()?.toList() ?: emptyList()
            override fun delete(file: File): Boolean = false
        }
        val viewModel = reviewViewModel(
            screenshotDuplicateDetector = duplicateDetector(mapOf(uri to byteArrayOf(8))),
            localImagePreserver = LocalImagePreserver(
                appPrivateRoot = Files.createTempDirectory("rank-forge-cleanup-error").toFile(),
                sourceStreamOpener = ImageSourceStreamOpener { byteArrayOf(8).inputStream() },
                mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
                fileOperations = operations,
                ioDispatcher = Dispatchers.Unconfined,
            ),
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.onPhotoPickerResult(uri)
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()
        viewModel.unlinkScreenshot()
        advanceUntilIdle()

        assertEquals(uri, viewModel.uiState.value.linkedScreenshotUri)
        assertEquals(
            ScreenshotPreservationError.CLEANUP_FAILED,
            viewModel.uiState.value.screenshotPreservationError,
        )
    }

    @Test
    fun restoredMetadataShowsLinkedStateAndMissingLocalFileMarksMetadataMissing() = runTest {
        val metadataRepository = FakeScreenshotMetadataRepository(
            metadata = ScreenshotMetadataEntity(
                matchId = matchId,
                tournamentId = TOURNAMENT_ID,
                ownerUserId = "owner-id",
                localRelativePath = "screenshots/missing/match/original.png",
                fileExtension = "png",
                mimeType = "image/png",
                width = 1080,
                height = 1920,
                byteSize = 4,
                sha256 = "a".repeat(64),
                storageBucket = "match-screenshots",
                storageObjectPath = screenshotObjectPath("owner-id", matchId, "png"),
                localStatus = ScreenshotLocalStatus.PRESERVED.name,
                uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
                uploadFailureCode = null,
                createdAt = 1,
                updatedAt = 1,
                preservedAt = 1,
                uploadedAt = 2,
                revision = 2,
            ),
        )
        val viewModel = reviewViewModel(screenshotMetadataRepository = metadataRepository)

        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPreservedScreenshotMissing)
        assertEquals(ScreenshotPreservationError.LOCAL_FILE_MISSING, viewModel.uiState.value.screenshotPreservationError)
        assertEquals(ScreenshotLocalStatus.MISSING.name, metadataRepository.metadata.value!!.localStatus)
    }

    @Test
    fun screenshotLinkDoesNotCarryToAnotherMatchContext() = runTest {
        val secondMatchId = (CreateMatchUseCase(repository)(
            CreateMatchInput(
                tournamentId = TOURNAMENT_ID,
                matchNumber = "2",
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
            ),
        ) as CreateMatchResult.Created).match.id
        val viewModel = reviewViewModel()
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.onPhotoPickerResult("content://picker/first")
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()
        assertEquals("content://picker/first", viewModel.uiState.value.linkedScreenshotUri)

        viewModel.load(TOURNAMENT_ID, secondMatchId)
        advanceUntilIdle()

        assertEquals(secondMatchId, viewModel.uiState.value.matchId)
        assertEquals(null, viewModel.uiState.value.linkedScreenshotUri)
    }

    @Test
    fun sameMatchDuplicateIsReportedAsNoOpAndKeepsTheExistingLink() = runTest {
        val firstUri = "content://picker/first"
        val duplicateUri = "content://picker/duplicate"
        val viewModel = reviewViewModel(
            screenshotDuplicateDetector = duplicateDetector(
                mapOf(firstUri to "same-image".encodeToByteArray(), duplicateUri to "same-image".encodeToByteArray()),
            ),
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.onPhotoPickerResult(firstUri)
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()

        viewModel.onPhotoPickerResult(duplicateUri)
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()

        assertEquals(firstUri, viewModel.uiState.value.linkedScreenshotUri)
        assertEquals(
            ScreenshotDuplicateInfo.ALREADY_LINKED_TO_THIS_MATCH,
            viewModel.uiState.value.screenshotDuplicateInfo,
        )
        assertEquals(null, viewModel.uiState.value.screenshotDuplicateError)
    }

    @Test
    fun duplicateLinkedToAnotherMatchIsRejectedWithinTheTournament() = runTest {
        val secondMatchId = (CreateMatchUseCase(repository)(
            CreateMatchInput(
                tournamentId = TOURNAMENT_ID,
                matchNumber = "2",
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
            ),
        ) as CreateMatchResult.Created).match.id
        val firstUri = "content://picker/first"
        val duplicateUri = "content://picker/duplicate"
        val detector = duplicateDetector(
            mapOf(firstUri to "same-image".encodeToByteArray(), duplicateUri to "same-image".encodeToByteArray()),
        )
        val firstViewModel = reviewViewModel(screenshotDuplicateDetector = detector)
        firstViewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        firstViewModel.onPhotoPickerResult(firstUri)
        advanceUntilIdle()
        firstViewModel.linkScreenshot()
        advanceUntilIdle()

        val secondViewModel = reviewViewModel(screenshotDuplicateDetector = detector)
        secondViewModel.load(TOURNAMENT_ID, secondMatchId)
        advanceUntilIdle()
        secondViewModel.onPhotoPickerResult(duplicateUri)
        advanceUntilIdle()
        secondViewModel.linkScreenshot()
        advanceUntilIdle()

        assertEquals(null, secondViewModel.uiState.value.linkedScreenshotUri)
        assertEquals(
            ScreenshotDuplicateError.LINKED_TO_OTHER_MATCH,
            secondViewModel.uiState.value.screenshotDuplicateError,
        )
    }

    @Test
    fun fingerprintFailureShowsControlledDuplicateError() = runTest {
        val viewModel = reviewViewModel(
            screenshotDuplicateDetector = ScreenshotDuplicateDetector(
                ImageSourceFingerprintGenerator(
                    ImageSourceStreamOpener { null },
                    Dispatchers.Unconfined,
                ),
            ),
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.onPhotoPickerResult("content://picker/unreadable")
        advanceUntilIdle()

        viewModel.linkScreenshot()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.linkedScreenshotUri)
        assertEquals(
            ScreenshotDuplicateError.FINGERPRINT_FAILED,
            viewModel.uiState.value.screenshotDuplicateError,
        )
    }

    @Test
    fun invalidScreenshotCannotBeLinked() = runTest {
        val viewModel = reviewViewModel(
            ImageCandidateValidator(
                ImageCandidateMetadataReader {
                    ImageCandidateReadResult.Metadata("image/gif", width = 1080, height = 1920)
                },
            ),
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.onPhotoPickerResult("content://picker/unsupported")
        advanceUntilIdle()
        viewModel.linkScreenshot()

        assertEquals(null, viewModel.uiState.value.linkedScreenshotUri)
        assertEquals(ScreenshotLinkError.INVALID_IMAGE, viewModel.uiState.value.screenshotLinkError)
    }

    @Test
    fun finalizedMatchCannotLinkOrReplaceScreenshot() = runTest {
        (1..12).forEach { slotNumber ->
            repository.saveDraftMatchValue(
                TOURNAMENT_ID,
                matchId,
                slotNumber,
                placementInput = slotNumber.toString(),
                killsInput = "0",
            )
        }
        val viewModel = reviewViewModel()
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.finalizeMatch()
        advanceUntilIdle()

        viewModel.onPhotoPickerResult("content://picker/validated")
        advanceUntilIdle()
        viewModel.linkScreenshot()

        assertEquals(MatchStatus.FINALIZED, viewModel.uiState.value.status)
        assertEquals(null, viewModel.uiState.value.linkedScreenshotUri)
        assertEquals(ScreenshotLinkError.FINALIZED_MATCH, viewModel.uiState.value.screenshotLinkError)
    }

    @Test
    fun missingTournamentContextBlocksLinkingWithoutCrashing() = runTest {
        val viewModel = reviewViewModel()

        viewModel.linkScreenshot()

        assertEquals(ScreenshotLinkError.MISSING_TOURNAMENT_ID, viewModel.uiState.value.screenshotLinkError)
    }

    @Test
    fun repeatedPhotoPickerRequestsDoNotCreateConcurrentLaunchState() = runTest {
        val viewModel = reviewViewModel()
        viewModel.load(TOURNAMENT_ID, matchId)
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
                TOURNAMENT_ID,
                matchId,
                slotNumber,
                placementInput = slotNumber.toString(),
                killsInput = (slotNumber - 1).toString(),
            )
        }
        val finalizedSync = RecordingFinalizedMatchCloudSync()
        val viewModel = reviewViewModel(finalizedMatchCloudSync = finalizedSync)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

       viewModel.finalizeMatch()
        advanceUntilIdle()

        assertEquals(MatchStatus.FINALIZED, viewModel.uiState.value.status)
        assertEquals(TOURNAMENT_ID, viewModel.uiState.value.tournamentId)
        assertEquals(matchId, viewModel.uiState.value.matchId)
        assertFalse(viewModel.uiState.value.isEditable)
        assertEquals(null, viewModel.uiState.value.finalizationError)
        assertTrue(repository.observeDraftMatchValues(TOURNAMENT_ID, matchId).first().isEmpty())
        assertEquals(
            MatchStatus.FINALIZED,
            repository.observeMatchById(matchId).first()!!.status,
        )
        assertEquals(listOf(TOURNAMENT_ID), finalizedSync.tournamentIds)
    }

    @Test
    fun localFinalizationDoesNotWaitForCloudSync() = runTest {
        (1..12).forEach { slotNumber ->
            repository.saveDraftMatchValue(
                TOURNAMENT_ID,
                matchId,
                slotNumber,
                placementInput = slotNumber.toString(),
                killsInput = (slotNumber - 1).toString(),
            )
        }
        val finalizedSync = RecordingFinalizedMatchCloudSync().also {
            it.gate = kotlinx.coroutines.CompletableDeferred()
        }
        val viewModel = reviewViewModel(finalizedMatchCloudSync = finalizedSync)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.finalizeMatch()
        advanceUntilIdle()

        assertEquals(MatchStatus.FINALIZED, repository.observeMatchById(matchId).first()!!.status)
        assertFalse(viewModel.uiState.value.isEditable)
        assertEquals(listOf(TOURNAMENT_ID), finalizedSync.tournamentIds)

        finalizedSync.gate!!.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun cloudFinalizationFailureDoesNotRevertLocalFinalization() = runTest {
        (1..12).forEach { slotNumber ->
            repository.saveDraftMatchValue(
                TOURNAMENT_ID,
                matchId,
                slotNumber,
                placementInput = slotNumber.toString(),
                killsInput = (slotNumber - 1).toString(),
            )
        }
        val finalizedSync = RecordingFinalizedMatchCloudSync(
            FinalizedMatchCloudSyncResult.NetworkFailure,
        )
        val viewModel = reviewViewModel(finalizedMatchCloudSync = finalizedSync)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.finalizeMatch()
        advanceUntilIdle()

        assertEquals(MatchStatus.FINALIZED, repository.observeMatchById(matchId).first()!!.status)
        assertFalse(viewModel.uiState.value.isEditable)
        assertEquals(listOf(TOURNAMENT_ID), finalizedSync.tournamentIds)
    }

    @Test
    fun queuedFinalizedSyncDoesNotReopenLocalFinalizedReview() = runTest {
        (1..12).forEach { slotNumber ->
            repository.saveDraftMatchValue(
                TOURNAMENT_ID,
                matchId,
                slotNumber,
                placementInput = slotNumber.toString(),
                killsInput = (slotNumber - 1).toString(),
            )
        }
        val viewModel = reviewViewModel()
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
       viewModel.finalizeMatch()
        advanceUntilIdle()

        var requestedTournamentId: String? = null
        val syncViewModel = FinalizedMatchCloudSyncViewModel(
            FinalizedMatchCloudSyncAction { tournamentId ->
                requestedTournamentId = tournamentId
                QueueAwareActionResult(
                    primaryResult = FinalizedMatchCloudSyncResult.NetworkFailure,
                    queueRecordingResult = QueueRecordingResult.RECORDED,
                )
            },
        )
        syncViewModel.sync(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(TOURNAMENT_ID, requestedTournamentId)
        assertEquals(FinalizedMatchCloudSyncUiState.Queued, syncViewModel.uiState.value)
        assertEquals(TOURNAMENT_ID, viewModel.uiState.value.tournamentId)
        assertEquals(matchId, viewModel.uiState.value.matchId)
        assertEquals(MatchStatus.FINALIZED, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isEditable)
    }

    @Test
    fun finalizedMatchCsvExportPreservesTournamentAndMatchIdentity() = runTest {
        repository.saveTeamNames(
            TOURNAMENT_ID,
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        (1..12).forEach { slotNumber ->
            repository.saveDraftMatchValue(
                TOURNAMENT_ID,
                matchId,
                slotNumber,
                placementInput = slotNumber.toString(),
                killsInput = (slotNumber - 1).toString(),
            )
        }
        val viewModel = reviewViewModel()
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
       viewModel.finalizeMatch()
        advanceUntilIdle()
        viewModel.prepareCsvExport()
        advanceUntilIdle()

        val result = viewModel.uiState.value.csvExportResult
        assertTrue(result is AndroidExportResult.CsvReady)
        assertEquals(AndroidExportType.MATCH_CSV, result?.request?.type)
        assertEquals(TOURNAMENT_ID, result?.request?.tournamentId)
        assertEquals(matchId, result?.request?.matchId)
        assertEquals("text/csv", (result as AndroidExportResult.CsvReady).mimeType)
        assertTrue(result.content.contains(matchId))
        assertEquals(TOURNAMENT_ID, viewModel.uiState.value.tournamentId)
        assertEquals(matchId, viewModel.uiState.value.matchId)
        assertFalse(viewModel.uiState.value.isEditable)

        viewModel.prepareGoogleSheetsExport()
        advanceUntilIdle()
        assertEquals(
            AndroidExportType.MATCH_GOOGLE_SHEETS,
            viewModel.uiState.value.googleSheetsExportResult?.request?.type,
        )
        assertTrue(viewModel.uiState.value.googleSheetsExportResult is AndroidExportResult.GoogleSheetsSuccess)
        assertEquals(
            1,
            (viewModel.uiState.value.googleSheetsExportResult as AndroidExportResult.GoogleSheetsSuccess)
                .exportedMatchCount,
        )
        assertEquals(
            12,
            (viewModel.uiState.value.googleSheetsExportResult as AndroidExportResult.GoogleSheetsSuccess)
                .rowsWritten,
        )
    }

    @Test
    fun draftMatchGoogleSheetsExportIsBlockedLocallyWithoutRemoteCall() = runTest {
        val remote = RecordingGoogleSheetsMatchExport()
        val viewModel = reviewViewModel(googleSheetsMatchExport = remote)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.prepareGoogleSheetsExport()
        advanceUntilIdle()

        val result = viewModel.uiState.value.googleSheetsExportResult as AndroidExportResult.Blocked
        assertEquals(AndroidExportType.MATCH_GOOGLE_SHEETS, result.request.type)
        assertEquals(AndroidExportBlockedReason.MATCH_NOT_FINALIZED, result.reason)
        assertTrue(remote.requests.isEmpty())
    }

    @Test
    fun invalidFinalizedMatchGoogleSheetsExportIsBlockedLocally() = runTest {
        saveValidFinalizedMatch()
        repository.saveTeamNames(TOURNAMENT_ID, mapOf(1 to ""))
        val remote = RecordingGoogleSheetsMatchExport()
        val viewModel = reviewViewModel(googleSheetsMatchExport = remote)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.prepareGoogleSheetsExport()
        advanceUntilIdle()

        val result = viewModel.uiState.value.googleSheetsExportResult as AndroidExportResult.Blocked
        assertEquals(AndroidExportBlockedReason.INVALID_FINALIZED_MATCH, result.reason)
        assertTrue(remote.requests.isEmpty())
    }

    @Test
    fun validFinalizedMatchGoogleSheetsExportUsesExactIdentityAndTwelveRows() = runTest {
        saveValidFinalizedMatch()
        val remote = RecordingGoogleSheetsMatchExport()
        val viewModel = reviewViewModel(googleSheetsMatchExport = remote)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.prepareGoogleSheetsExport()
        advanceUntilIdle()

        assertEquals(1, remote.requests.size)
        val request = remote.requests.single()
        assertEquals(TOURNAMENT_ID, request.tournamentId)
        assertEquals(checkNotNull(MatchCloudIdentity.matchId(TOURNAMENT_ID, matchId)), request.matchId)
        assertEquals(12, request.rows.size)
        assertEquals(setOf(request.matchId), request.rows.map { it.matchId }.toSet())
        assertFalse(request.rows.any { it.matchId == matchId })
        assertEquals(matchId, viewModel.uiState.value.matchId)
        assertEquals(matchId, viewModel.uiState.value.googleSheetsExportResult?.request?.matchId)
        assertTrue(viewModel.uiState.value.googleSheetsExportResult is AndroidExportResult.GoogleSheetsSuccess)
    }

    @Test
    fun googleSheetsExportMapsKnownLocalMatchIdToHostedMatchId() = runTest {
        repository.create(
            Tournament(
                id = HOSTED_ID_TEST_TOURNAMENT_ID,
                name = "Hosted ID Test Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        repository.saveTeamNames(
            HOSTED_ID_TEST_TOURNAMENT_ID,
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        repository.createDraftMatch(
            Match(
                id = HOSTED_ID_TEST_LOCAL_MATCH_ID,
                tournamentId = HOSTED_ID_TEST_TOURNAMENT_ID,
                matchNumber = 2,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        matchId = HOSTED_ID_TEST_LOCAL_MATCH_ID
        saveValidFinalizedMatch(HOSTED_ID_TEST_TOURNAMENT_ID)
        val remote = RecordingGoogleSheetsMatchExport()
        val viewModel = reviewViewModel(googleSheetsMatchExport = remote)
        viewModel.load(HOSTED_ID_TEST_TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.prepareGoogleSheetsExport()
        advanceUntilIdle()

        val request = remote.requests.single()
        assertEquals(HOSTED_ID_TEST_TOURNAMENT_ID, request.tournamentId)
        assertEquals(HOSTED_ID_TEST_HOSTED_MATCH_ID, request.matchId)
        assertEquals(12, request.rows.size)
        assertEquals(
            listOf(HOSTED_ID_TEST_HOSTED_MATCH_ID),
            request.rows.map { it.matchId }.distinct(),
        )
        assertFalse(request.rows.any { it.matchId == HOSTED_ID_TEST_LOCAL_MATCH_ID })
        assertEquals(HOSTED_ID_TEST_TOURNAMENT_ID, viewModel.uiState.value.tournamentId)
        assertEquals(HOSTED_ID_TEST_LOCAL_MATCH_ID, viewModel.uiState.value.matchId)
        assertEquals(
            HOSTED_ID_TEST_LOCAL_MATCH_ID,
            viewModel.uiState.value.googleSheetsExportResult?.request?.matchId,
        )
        assertTrue(viewModel.uiState.value.googleSheetsExportResult is AndroidExportResult.GoogleSheetsSuccess)
    }

    @Test
    fun secondGoogleSheetsRequestWhileFirstIsActiveDoesNotCreateAnotherCall() = runTest {
        saveValidFinalizedMatch()
        val remote = RecordingGoogleSheetsMatchExport().apply {
            gate = kotlinx.coroutines.CompletableDeferred()
        }
        val viewModel = reviewViewModel(googleSheetsMatchExport = remote)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.prepareGoogleSheetsExport()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.googleSheetsExportResult is AndroidExportResult.GoogleSheetsExporting)
        viewModel.prepareGoogleSheetsExport()
        advanceUntilIdle()
        assertEquals(1, remote.requests.size)

        checkNotNull(remote.gate).complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.googleSheetsExportResult is AndroidExportResult.GoogleSheetsSuccess)
    }

    @Test
    fun remoteGoogleSheetsFailureReasonIsPreserved() = runTest {
        saveValidFinalizedMatch()
        val remote = RecordingGoogleSheetsMatchExport(
            result = GoogleSheetsMatchExportExecutionResult.Failure(
                com.hoggamers.rankforge.data.export.AndroidGoogleSheetsExportFailureReason.OUTCOME_UNCERTAIN,
            ),
        )
        val viewModel = reviewViewModel(googleSheetsMatchExport = remote)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.prepareGoogleSheetsExport()
        advanceUntilIdle()

        val result = viewModel.uiState.value.googleSheetsExportResult as AndroidExportResult.GoogleSheetsFailure
        assertEquals(
            com.hoggamers.rankforge.data.export.AndroidGoogleSheetsExportFailureReason.OUTCOME_UNCERTAIN,
            result.reason,
        )
        assertEquals(matchId, result.request.matchId)
    }

    @Test
    fun draftMatchCsvExportIsBlockedWithoutFinalizing() = runTest {
        val viewModel = reviewViewModel()
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.prepareCsvExport()
        advanceUntilIdle()

        val result = viewModel.uiState.value.csvExportResult
        assertEquals(AndroidExportType.MATCH_CSV, result?.request?.type)
        assertEquals(TOURNAMENT_ID, result?.request?.tournamentId)
        assertEquals(matchId, result?.request?.matchId)
        assertEquals(
            AndroidExportBlockedReason.MATCH_NOT_FINALIZED,
            (result as AndroidExportResult.Blocked).reason,
        )
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(matchId).first()!!.status)
    }

    @Test
    fun missingMatchKeepsExactReviewContextInSafeNotFoundState() = runTest {
        val viewModel = reviewViewModel()

        viewModel.load(TOURNAMENT_ID, "missing-match")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isNotFound)
        assertEquals(TOURNAMENT_ID, viewModel.uiState.value.tournamentId)
        assertEquals("missing-match", viewModel.uiState.value.matchId)
        assertFalse(viewModel.uiState.value.isEditable)
        assertEquals(null, viewModel.uiState.value.csvExportResult)
    }

    @Test
    fun invalidReviewDoesNotFinalize() = runTest {
        val finalizedSync = RecordingFinalizedMatchCloudSync()
        val viewModel = reviewViewModel(finalizedMatchCloudSync = finalizedSync)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.finalizeMatch()
        advanceUntilIdle()

        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(matchId).first()!!.status)
        assertEquals(TOURNAMENT_ID, viewModel.uiState.value.tournamentId)
        assertEquals(matchId, viewModel.uiState.value.matchId)
        assertTrue(viewModel.uiState.value.isEditable)
        assertFalse(viewModel.uiState.value.isFinalizing)
        assertTrue(finalizedSync.tournamentIds.isEmpty())
    }

    @Test
    fun nonDraftMatchDoesNotInvokeFinalizedCloudSync() = runTest {
        saveValidFinalizedMatch()
        val finalizedSync = RecordingFinalizedMatchCloudSync()
        val viewModel = reviewViewModel(finalizedMatchCloudSync = finalizedSync)

        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.finalizeMatch()
        advanceUntilIdle()

        assertEquals(MatchStatus.FINALIZED, repository.observeMatchById(matchId).first()!!.status)
        assertTrue(finalizedSync.tournamentIds.isEmpty())
    }

    @Test
    fun duplicatePlacementCannotFinalizeAndKeepsReviewContext() = runTest {
        (1..12).forEach { slotNumber ->
            repository.saveDraftMatchValue(
                TOURNAMENT_ID,
                matchId,
                slotNumber,
                placementInput = if (slotNumber == 2) "1" else slotNumber.toString(),
                killsInput = (slotNumber - 1).toString(),
            )
        }
        val viewModel = reviewViewModel()
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isValid)
        assertTrue(
            MatchResultValidationError.DUPLICATE_PLACEMENT in
                viewModel.uiState.value.validationErrors.getValue(1),
        )
        viewModel.finalizeMatch()
        advanceUntilIdle()

        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(matchId).first()!!.status)
        assertEquals(TOURNAMENT_ID, viewModel.uiState.value.tournamentId)
        assertEquals(matchId, viewModel.uiState.value.matchId)
    }

    @Test
    fun negativeKillsCannotFinalize() = runTest {
        (1..12).forEach { slotNumber ->
            repository.saveDraftMatchValue(
                TOURNAMENT_ID,
                matchId,
                slotNumber,
                placementInput = slotNumber.toString(),
                killsInput = if (slotNumber == 1) "-1" else (slotNumber - 1).toString(),
            )
        }
        val viewModel = reviewViewModel()
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isValid)
        assertTrue(
            MatchResultValidationError.INVALID_KILLS in
                viewModel.uiState.value.validationErrors.getValue(1),
        )
        viewModel.finalizeMatch()
        advanceUntilIdle()

        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(matchId).first()!!.status)
        assertEquals(TOURNAMENT_ID, viewModel.uiState.value.tournamentId)
        assertEquals(matchId, viewModel.uiState.value.matchId)
    }

    @Test
    fun draftMatchCannotStartResultDownload() = runTest {
        val coordinator = RecordingResultDownloadCoordinator()
        val viewModel = reviewViewModel(resultDownloadCoordinator = coordinator)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PDF)
        advanceUntilIdle()

        assertTrue(coordinator.requests.isEmpty())
        assertEquals(ResultDownloadUiState.Idle, viewModel.uiState.value.resultDownloadUiState)
    }

    @Test
    fun invalidFinalizedMatchCannotStartResultDownload() = runTest {
        saveValidFinalizedMatch()
        repository.saveTeamNames(TOURNAMENT_ID, mapOf(1 to ""))
        val coordinator = RecordingResultDownloadCoordinator()
        val viewModel = reviewViewModel(resultDownloadCoordinator = coordinator)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PDF)
        advanceUntilIdle()

        assertTrue(coordinator.requests.isEmpty())
        assertEquals(ResultDownloadUiState.Idle, viewModel.uiState.value.resultDownloadUiState)
    }

    @Test
    fun participantAwareFinalizedMatchWithNoShowsRemainsDownloadable() = runTest {
        repository.saveTeamNames(
            TOURNAMENT_ID,
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        repository.finalizeDraftMatch(
            matchId = matchId,
            placements = (1..10).map { slotNumber ->
                com.hoggamers.rankforge.domain.tournament.MatchPlacement(
                    teamSlotNumber = slotNumber,
                    position = slotNumber,
                )
            },
            kills = (1..10).map { slotNumber ->
                com.hoggamers.rankforge.domain.tournament.MatchKill(
                    teamSlotNumber = slotNumber,
                    kills = 0,
                )
            },
        )
        val coordinator = RecordingResultDownloadCoordinator(
            result = ResultDownloadExecutionResult.Saved(ResultExportFileFormat.PDF, "result.pdf"),
        )
        val viewModel = reviewViewModel(resultDownloadCoordinator = coordinator)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isValid)
        assertTrue(viewModel.uiState.value.canDownloadResult)

        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PDF)
        advanceUntilIdle()

        assertEquals(1, coordinator.requests.size)
    }

    @Test
    fun currentMatchPdfUsesExactLocalContextAndSucceeds() = runTest {
        saveValidFinalizedMatch()
        val coordinator = RecordingResultDownloadCoordinator(
            result = ResultDownloadExecutionResult.Saved(ResultExportFileFormat.PDF, "result.pdf"),
        )
        val viewModel = reviewViewModel(resultDownloadCoordinator = coordinator)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PDF)
        advanceUntilIdle()

        val request = coordinator.requests.single()
        assertTrue(request.request is ResultDownloadRequest.CurrentMatch)
        val input = (request.request as ResultDownloadRequest.CurrentMatch).input
        assertEquals(TOURNAMENT_ID, input.tournament.id)
        assertEquals(matchId, input.match.id)
        assertEquals(ResultExportFileFormat.PDF, request.format)
        assertEquals(
            ResultDownloadUiState.Success(ResultExportFileFormat.PDF, false),
            viewModel.uiState.value.resultDownloadUiState,
        )
    }

    @Test
    fun currentMatchPngUsesPngPathAndSucceeds() = runTest {
        saveValidFinalizedMatch()
        val coordinator = RecordingResultDownloadCoordinator(
            result = ResultDownloadExecutionResult.Saved(ResultExportFileFormat.PNG, "result.png"),
        )
        val viewModel = reviewViewModel(resultDownloadCoordinator = coordinator)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PNG)
        advanceUntilIdle()

        assertEquals(ResultExportFileFormat.PNG, coordinator.requests.single().format)
        assertEquals(
            ResultDownloadUiState.Success(ResultExportFileFormat.PNG, false),
            viewModel.uiState.value.resultDownloadUiState,
        )
    }

    @Test
    fun wholeTournamentUsesAllLocalMatchesAndApprovedTournamentPath() = runTest {
        saveValidFinalizedMatch()
        val draftMatchId = "draft-second-match"
        repository.createDraftMatch(
            Match(
                id = draftMatchId,
                tournamentId = TOURNAMENT_ID,
                matchNumber = 2,
                date = LocalDate.of(2026, 7, 25),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        val coordinator = RecordingResultDownloadCoordinator(
            result = ResultDownloadExecutionResult.Saved(ResultExportFileFormat.PNG, "result.png"),
        )
        val viewModel = reviewViewModel(resultDownloadCoordinator = coordinator)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.requestResultDownload(ResultDownloadScope.WHOLE_TOURNAMENT, ResultExportFileFormat.PNG)
        advanceUntilIdle()

        val request = coordinator.requests.single().request as ResultDownloadRequest.WholeTournament
        assertEquals(setOf(matchId, draftMatchId), request.input.matches.map { it.id }.toSet())
        assertEquals(ResultExportFileFormat.PNG, coordinator.requests.single().format)
        assertEquals(
            ResultDownloadUiState.Success(ResultExportFileFormat.PNG, false),
            viewModel.uiState.value.resultDownloadUiState,
        )
    }

    @Test
    fun generationAndSaveFailuresAreDeterministic() = runTest {
        saveValidFinalizedMatch()
        val coordinator = RecordingResultDownloadCoordinator(
            result = ResultDownloadExecutionResult.Failure(ResultDownloadFailure.GENERATION_FAILED),
        )
        val viewModel = reviewViewModel(resultDownloadCoordinator = coordinator)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PDF)
        advanceUntilIdle()
        assertEquals(
            ResultDownloadUiState.Failure(ResultDownloadFailure.GENERATION_FAILED),
            viewModel.uiState.value.resultDownloadUiState,
        )

        coordinator.result = ResultDownloadExecutionResult.Failure(ResultDownloadFailure.SAVE_FAILED)
        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PDF)
        advanceUntilIdle()
        assertEquals(
            ResultDownloadUiState.Failure(ResultDownloadFailure.SAVE_FAILED),
            viewModel.uiState.value.resultDownloadUiState,
        )
    }

    @Test
    fun duplicateResultDownloadWhileActiveStartsOnlyOneOperation() = runTest {
        saveValidFinalizedMatch()
        val coordinator = RecordingResultDownloadCoordinator().apply {
            gate = CompletableDeferred()
        }
        val viewModel = reviewViewModel(resultDownloadCoordinator = coordinator)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PDF)
        advanceUntilIdle()
        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PDF)
        advanceUntilIdle()

        assertEquals(1, coordinator.requests.size)
        checkNotNull(coordinator.gate).complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun safDestinationRequestRetainsBytesPrivatelyAndCancellationReturnsIdle() = runTest {
        saveValidFinalizedMatch()
        val coordinator = RecordingResultDownloadCoordinator(
            result = ResultDownloadExecutionResult.UserDestinationRequired(
                format = ResultExportFileFormat.PDF,
                displayName = "RankForge_Summer_Cup_Match_1_Result.pdf",
                bytes = byteArrayOf(1, 2, 3),
            ),
        )
        val viewModel = reviewViewModel(resultDownloadCoordinator = coordinator)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PDF)
        advanceUntilIdle()
        assertEquals(
            ResultDownloadUiState.DestinationLaunchRequested(
                ResultExportFileFormat.PDF,
                "RankForge_Summer_Cup_Match_1_Result.pdf",
            ),
            viewModel.uiState.value.resultDownloadUiState,
        )

        viewModel.onDestinationLaunchHandled()
        assertTrue(viewModel.uiState.value.resultDownloadUiState is ResultDownloadUiState.WaitingForDestination)
        viewModel.onDestinationResult(null)
        assertEquals(ResultDownloadUiState.Idle, viewModel.uiState.value.resultDownloadUiState)
    }

    @Test
    fun safDestinationLaunchFailureClearsPendingAndAllowsRetry() = runTest {
        saveValidFinalizedMatch()
        val coordinator = RecordingResultDownloadCoordinator(
            result = ResultDownloadExecutionResult.UserDestinationRequired(
                format = ResultExportFileFormat.PDF,
                displayName = "result.pdf",
                bytes = byteArrayOf(7, 8, 9),
            ),
        )
        val writer = RecordingResultDocumentWriter()
        val viewModel = reviewViewModel(
            resultDownloadCoordinator = coordinator,
            resultDocumentWriter = writer,
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PDF)
        advanceUntilIdle()
        viewModel.onDestinationLaunchHandled()
        viewModel.onDestinationLaunchFailed()

        assertEquals(
            ResultDownloadUiState.Failure(ResultDownloadFailure.DESTINATION_LAUNCH_FAILED),
            viewModel.uiState.value.resultDownloadUiState,
        )
        viewModel.onDestinationResultForTesting()
        advanceUntilIdle()
        assertTrue(writer.bytesWritten.isEmpty())

        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PDF)
        advanceUntilIdle()
        assertEquals(2, coordinator.requests.size)
        assertTrue(viewModel.uiState.value.resultDownloadUiState is ResultDownloadUiState.DestinationLaunchRequested)
    }

    @Test
    fun safDestinationWriteFailureClearsPendingBytes() = runTest {
        saveValidFinalizedMatch()
        val writer = RecordingResultDocumentWriter(ResultDocumentWriteResult.Failure(
            ResultDocumentWriteFailure.WRITE_FAILED,
        ))
        val coordinator = RecordingResultDownloadCoordinator(
            result = ResultDownloadExecutionResult.UserDestinationRequired(
                format = ResultExportFileFormat.PDF,
                displayName = "result.pdf",
                bytes = byteArrayOf(10, 11),
            ),
        )
        val viewModel = reviewViewModel(
            resultDownloadCoordinator = coordinator,
            resultDocumentWriter = writer,
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PDF)
        advanceUntilIdle()
        viewModel.onDestinationLaunchHandled()
        viewModel.onDestinationResultForTesting()
        advanceUntilIdle()

        assertEquals(
            ResultDownloadUiState.Failure(ResultDownloadFailure.DESTINATION_WRITE_FAILED),
            viewModel.uiState.value.resultDownloadUiState,
        )
        assertEquals(1, writer.bytesWritten.size)
        viewModel.onDestinationResultForTesting()
        advanceUntilIdle()
        assertEquals(1, writer.bytesWritten.size)
    }

    @Test
    fun repositoryRefreshPreservesActiveResultDownloadStateWithoutDuplicateOperation() = runTest {
        saveValidFinalizedMatch()
        val coordinator = RecordingResultDownloadCoordinator().apply {
            gate = CompletableDeferred()
        }
        val viewModel = reviewViewModel(resultDownloadCoordinator = coordinator)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PDF)
        advanceUntilIdle()
        assertEquals(
            ResultDownloadUiState.Saving(ResultExportFileFormat.PDF),
            viewModel.uiState.value.resultDownloadUiState,
        )

        repository.saveTeamNames(
            TOURNAMENT_ID,
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        advanceUntilIdle()

        assertEquals(
            ResultDownloadUiState.Saving(ResultExportFileFormat.PDF),
            viewModel.uiState.value.resultDownloadUiState,
        )
        assertEquals(1, coordinator.requests.size)
        checkNotNull(coordinator.gate).complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun wholeTournamentPdfUsesAllLocalMatchesAndSucceeds() = runTest {
        saveValidFinalizedMatch()
        val draftMatchId = "draft-pdf-second-match"
        repository.createDraftMatch(
            Match(
                id = draftMatchId,
                tournamentId = TOURNAMENT_ID,
                matchNumber = 2,
                date = LocalDate.of(2026, 7, 25),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        val coordinator = RecordingResultDownloadCoordinator(
            result = ResultDownloadExecutionResult.Saved(ResultExportFileFormat.PDF, "result.pdf"),
        )
        val viewModel = reviewViewModel(resultDownloadCoordinator = coordinator)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.requestResultDownload(ResultDownloadScope.WHOLE_TOURNAMENT, ResultExportFileFormat.PDF)
        advanceUntilIdle()

        val request = coordinator.requests.single()
        assertTrue(request.request is ResultDownloadRequest.WholeTournament)
        assertEquals(ResultExportFileFormat.PDF, request.format)
        assertEquals(
            setOf(matchId, draftMatchId),
            (request.request as ResultDownloadRequest.WholeTournament).input.matches.map { it.id }.toSet(),
        )
        assertEquals(
            ResultDownloadUiState.Success(ResultExportFileFormat.PDF, false),
            viewModel.uiState.value.resultDownloadUiState,
        )
    }

    @Test
    fun safDestinationSuccessWritesBytesOnceAndClearsPendingDocument() = runTest {
        saveValidFinalizedMatch()
        val writer = RecordingResultDocumentWriter()
        val coordinator = RecordingResultDownloadCoordinator(
            result = ResultDownloadExecutionResult.UserDestinationRequired(
                format = ResultExportFileFormat.PNG,
                displayName = "result.png",
                bytes = byteArrayOf(4, 5, 6),
            ),
        )
        val viewModel = reviewViewModel(
            resultDownloadCoordinator = coordinator,
            resultDocumentWriter = writer,
        )
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PNG)
        advanceUntilIdle()
        viewModel.onDestinationLaunchHandled()
        viewModel.onDestinationResultForTesting()
        advanceUntilIdle()

        assertEquals(listOf(byteArrayOf(4, 5, 6).toList()), writer.bytesWritten)
        assertEquals(
            ResultDownloadUiState.Success(ResultExportFileFormat.PNG, true),
            viewModel.uiState.value.resultDownloadUiState,
        )
    }

    @Test
    fun loadingAnotherMatchClearsPendingSafDownload() = runTest {
        saveValidFinalizedMatch()
        val coordinator = RecordingResultDownloadCoordinator(
            result = ResultDownloadExecutionResult.UserDestinationRequired(
                ResultExportFileFormat.PDF,
                "result.pdf",
                byteArrayOf(1),
            ),
        )
        val viewModel = reviewViewModel(resultDownloadCoordinator = coordinator)
        viewModel.load(TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.requestResultDownload(ResultDownloadScope.CURRENT_MATCH, ResultExportFileFormat.PDF)
        advanceUntilIdle()

        viewModel.load(TOURNAMENT_ID, "another-match")
        advanceUntilIdle()

        assertEquals(ResultDownloadUiState.Idle, viewModel.uiState.value.resultDownloadUiState)
    }

    private fun screenshotObjectPath(
        userId: String,
        localMatchId: String,
        extension: String,
    ): String = "users/$userId/tournaments/$TOURNAMENT_ID/matches/${
        checkNotNull(MatchCloudIdentity.matchId(TOURNAMENT_ID, localMatchId))
    }/original.$extension"

    private suspend fun saveValidFinalizedMatch(tournamentId: String = TOURNAMENT_ID) {
        repository.saveTeamNames(
            tournamentId,
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        repository.finalizeDraftMatch(
            matchId = matchId,
            placements = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
                com.hoggamers.rankforge.domain.tournament.MatchPlacement(
                    teamSlotNumber = slotNumber,
                    position = slotNumber,
                )
            },
            kills = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
                com.hoggamers.rankforge.domain.tournament.MatchKill(
                    teamSlotNumber = slotNumber,
                    kills = slotNumber - 1,
                )
            },
        )
    }

    private fun reviewViewModel(
        imageCandidateValidator: ImageCandidateValidator = ImageCandidateValidator(
        ImageCandidateMetadataReader {
            ImageCandidateReadResult.Metadata("image/png", width = 1080, height = 1920)
        },
        ),
        screenshotDuplicateDetector: ScreenshotDuplicateDetector = duplicateDetector(),
        matchResultScreenshotDuplicateDetector: MatchResultScreenshotDuplicateDetector =
            MatchResultScreenshotDuplicateDetector(
                ImageSourceFingerprintGenerator(ImageSourceStreamOpener { null }),
            ),
        localImagePreserver: LocalImagePreserver = localImagePreserver(),
        screenshotStorageUploader: ScreenshotStorageUploader =
            com.hoggamers.rankforge.data.cloud.NoOpScreenshotStorageUploader(),
        screenshotMetadataRepository: ScreenshotMetadataRepository = FakeScreenshotMetadataRepository(),
        screenshotMetadataCloudDataSource: ScreenshotMetadataCloudDataSource =
            com.hoggamers.rankforge.data.cloud.NoOpScreenshotMetadataCloudDataSource(),
        googleSheetsMatchExport: GoogleSheetsMatchExportRemoteDataSource = RecordingGoogleSheetsMatchExport(),
        resultDownloadCoordinator: ResultDownloadCoordinator = RecordingResultDownloadCoordinator(),
        resultDocumentWriter: ResultDocumentWriter = RecordingResultDocumentWriter(),
        screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
        matchResultScreenshotAssetRepository: MatchResultScreenshotAssetRepository =
            com.hoggamers.rankforge.data.local.NoOpMatchResultScreenshotAssetRepository(),
        finalizedMatchCloudSync: FinalizedMatchCloudSyncAction = RecordingFinalizedMatchCloudSync(),
    ) = MatchReviewViewModel(
        getTournamentById = GetTournamentByIdUseCase(repository),
        observeMatches = ObserveMatchesUseCase(repository),
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
        observeRoster = ObserveRosterByTournamentUseCase(repository),
        observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
        validateMatchResult = ValidateMatchResultUseCase(),
        finalizeMatch = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase()),
        imageCandidateValidator = imageCandidateValidator,
        screenshotDuplicateDetector = screenshotDuplicateDetector,
        matchResultScreenshotDuplicateDetector = matchResultScreenshotDuplicateDetector,
        localImagePreserver = localImagePreserver,
        screenshotStorageUploader = screenshotStorageUploader,
        screenshotMetadataRepository = screenshotMetadataRepository,
        screenshotMetadataCloudDataSource = screenshotMetadataCloudDataSource,
        googleSheetsMatchExport = googleSheetsMatchExport,
        resultDownloadCoordinator = resultDownloadCoordinator,
        resultDocumentWriter = resultDocumentWriter,
        screenshotOwnerProvider = screenshotOwnerProvider,
        matchResultScreenshotAssetRepository = matchResultScreenshotAssetRepository,
        finalizedMatchCloudSync = finalizedMatchCloudSync,
        )

    private class RecordingFinalizedMatchCloudSync(
        private val result: FinalizedMatchCloudSyncResult = FinalizedMatchCloudSyncResult.Success(1),
    ) : FinalizedMatchCloudSyncAction {
        val tournamentIds = mutableListOf<String>()
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        override suspend fun invoke(
            tournamentId: String,
        ): QueueAwareActionResult<FinalizedMatchCloudSyncResult> {
            tournamentIds += tournamentId
            gate?.await()
            return QueueAwareActionResult(
                primaryResult = result,
                queueRecordingResult = QueueRecordingResult.RECORDED,
            )
        }
    }

    private class RecordingGoogleSheetsMatchExport(
        private val result: GoogleSheetsMatchExportExecutionResult =
            GoogleSheetsMatchExportExecutionResult.Success(rowsWritten = 12),
    ) : GoogleSheetsMatchExportRemoteDataSource {
        data class Request(
            val tournamentId: String,
            val matchId: String,
            val rows: List<MatchExportRow>,
        )

        val requests = mutableListOf<Request>()
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        override suspend fun export(
            tournamentId: String,
            matchId: String,
            rows: List<MatchExportRow>,
        ): GoogleSheetsMatchExportExecutionResult {
            requests += Request(tournamentId, matchId, rows)
            gate?.await()
            return result
        }
    }

    private class RecordingResultDownloadCoordinator(
        var result: ResultDownloadExecutionResult = ResultDownloadExecutionResult.Saved(
            format = ResultExportFileFormat.PDF,
            displayName = "result.pdf",
        ),
    ) : ResultDownloadCoordinator {
        data class Request(
            val request: ResultDownloadRequest,
            val format: ResultExportFileFormat,
        )

        val requests = mutableListOf<Request>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun execute(
            request: ResultDownloadRequest,
            format: ResultExportFileFormat,
            onSaving: suspend () -> Unit,
        ): ResultDownloadExecutionResult {
            requests += Request(request, format)
            onSaving()
            gate?.await()
            return result
        }
    }

    private class RecordingResultDocumentWriter(
        private val result: ResultDocumentWriteResult = ResultDocumentWriteResult.Success,
    ) : ResultDocumentWriter {
        val bytesWritten = mutableListOf<List<Byte>>()

        override suspend fun write(
            uri: android.net.Uri?,
            bytes: ByteArray,
        ): ResultDocumentWriteResult {
            bytesWritten += bytes.toList()
            return result
        }
    }

    private fun reviewViewModelWithReadyResultRoles(
        vararg roles: MatchResultScreenshotRole,
    ): MatchReviewViewModel {
        val preserver = localImagePreserver()
        val assets = roles.map { role ->
            val relativePath = "screenshots/$TOURNAMENT_ID/$matchId/result/${role.name}/original.png"
            preserver.resolveRelativePath(relativePath)!!.apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            resultScreenshotAsset(relativePath, role).copy(
                cropProfileId = "match-result",
                cropLeft = 0.1,
                cropTop = 0.1,
                cropRight = 0.9,
                cropBottom = 0.9,
            )
        }
        return reviewViewModel(
            localImagePreserver = preserver,
            matchResultScreenshotAssetRepository = FakeMatchResultScreenshotAssetRepository(assets),
        )
    }

    private fun resultScreenshotAsset(
        localRelativePath: String,
        role: MatchResultScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
    ) = MatchResultScreenshotAssetEntity(
        tournamentId = TOURNAMENT_ID,
        matchId = matchId,
        screenshotKind = OcrScreenshotKind.MATCH_RESULT.name,
        screenshotRole = role.name,
        ownerUserId = "owner-id",
        localRelativePath = localRelativePath,
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 1920,
        originalHeight = 1080,
        byteSize = 3,
        sha256 = "a".repeat(64),
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.PENDING.name,
        uploadFailureCode = null,
        storageBucket = null,
        storageObjectPath = null,
        cropProfileId = null,
        cropLeft = null,
        cropTop = null,
        cropRight = null,
        cropBottom = null,
        createdAt = 1L,
        updatedAt = 1L,
        preservedAt = 1L,
        uploadedAt = null,
        revision = 1L,
    )

    private class RecordingScreenshotStorageUploader(
        var result: ScreenshotStorageUploadResult,
    ) : ScreenshotStorageUploader {
        val calls = mutableListOf<File>()

        override suspend fun upload(
            tournamentId: String?,
            matchId: String?,
            localFile: File?,
        ): ScreenshotStorageUploadResult {
            localFile?.let(calls::add)
            return result
        }
    }

    private fun duplicateDetector(
        bytesByUri: Map<String, ByteArray> = emptyMap(),
    ) = ScreenshotDuplicateDetector(
        ImageSourceFingerprintGenerator(
            ImageSourceStreamOpener { uri ->
                (bytesByUri[uri] ?: uri.encodeToByteArray()).inputStream()
            },
            Dispatchers.Unconfined,
        ),
    )

    private fun localImagePreserver(
        bytesByUri: Map<String, ByteArray> = emptyMap(),
        mimeType: String? = "image/png",
    ) = LocalImagePreserver(
        appPrivateRoot = Files.createTempDirectory("rank-forge-preserve").toFile(),
        sourceStreamOpener = ImageSourceStreamOpener { uri ->
            (bytesByUri[uri] ?: uri.encodeToByteArray()).inputStream()
        },
        mimeTypeReader = ImageSourceMimeTypeReader { mimeType },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private class FixedScreenshotOwnerProvider(
        private val ownerId: String?,
    ) : ScreenshotOwnerProvider {
        override suspend fun currentOwnerUserId(): String? = ownerId
    }

    private class FakeMatchResultScreenshotAssetRepository(
        initialAssets: List<MatchResultScreenshotAssetEntity> = emptyList(),
    ) : MatchResultScreenshotAssetRepository {
        val assets = MutableStateFlow(initialAssets)

        override fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>> = assets

        override fun observeByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): Flow<MatchResultScreenshotAssetEntity?> =
            MutableStateFlow(assets.value.firstOrNull { it.matches(identity) })

        override suspend fun getByIdentity(identity: MatchResultScreenshotIdentity): MatchResultScreenshotAssetEntity? =
            assets.value.firstOrNull { it.matches(identity) }

        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
            MutableStateFlow(assets.value.filter { it.tournamentId == tournamentId })

        override suspend fun findDuplicateFingerprint(
            identity: MatchResultScreenshotIdentity,
            sha256: String,
        ): MatchResultScreenshotAssetEntity? = assets.value.firstOrNull {
            it.tournamentId == identity.tournamentId &&
                it.sha256 == sha256 &&
                !it.matches(identity)
        }

        override suspend fun saveOrReplace(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetSaveResult {
            assets.value = assets.value.filterNot { it.matches(asset.identity()) } + asset
            return MatchResultScreenshotAssetSaveResult.Saved
        }

        override suspend fun markLocalMissing(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit

        override suspend fun markCleanupFailure(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit

        override suspend fun persistConfirmedCrop(
            identity: MatchResultScreenshotIdentity,
            crop: OcrNormalizedCropRect,
            updatedAt: Long,
        ): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.Saved

        override suspend fun clearConfirmedCrop(
            identity: MatchResultScreenshotIdentity,
            updatedAt: Long,
        ): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.Saved

        override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) {
            assets.value = assets.value.filterNot { it.matches(identity) }
        }

        override suspend fun deleteByMatchId(matchId: String) {
            assets.value = assets.value.filterNot { it.matchId == matchId }
        }

        private fun MatchResultScreenshotAssetEntity.matches(identity: MatchResultScreenshotIdentity): Boolean =
            tournamentId == identity.tournamentId && matchId == identity.matchId && screenshotRole == identity.role.name

        private fun MatchResultScreenshotAssetEntity.identity(): MatchResultScreenshotIdentity =
            MatchResultScreenshotIdentity(
                tournamentId = tournamentId,
                matchId = matchId,
                role = MatchResultScreenshotRole.valueOf(screenshotRole),
            )
    }

    private class FakeScreenshotMetadataCloudDataSource(
        private val upsertResult: ScreenshotMetadataCloudResult = ScreenshotMetadataCloudResult.Success,
        private val deleteResult: ScreenshotMetadataCloudResult = ScreenshotMetadataCloudResult.Success,
    ) : ScreenshotMetadataCloudDataSource {
        val upserts = mutableListOf<ScreenshotMetadataCloudPayload>()

        override suspend fun upsert(payload: ScreenshotMetadataCloudPayload): ScreenshotMetadataCloudResult {
            upserts += payload
            return upsertResult
        }

        override suspend fun deleteByMatchId(matchId: String): ScreenshotMetadataCloudResult = deleteResult
    }

    private class FakeScreenshotMetadataRepository(
        metadata: ScreenshotMetadataEntity? = null,
    ) : ScreenshotMetadataRepository {
        val metadata = MutableStateFlow(metadata)
        var failWrites = false

        override fun observeByMatchId(matchId: String): Flow<ScreenshotMetadataEntity?> = metadata

        override suspend fun getByMatchId(matchId: String): ScreenshotMetadataEntity? = metadata.value

        override fun observeByTournamentId(tournamentId: String): Flow<List<ScreenshotMetadataEntity>> =
            MutableStateFlow(metadata.value?.let(::listOf).orEmpty())

        override suspend fun createOrReplace(metadata: ScreenshotMetadataEntity) {
            if (failWrites) error("room failed")
            this.metadata.value = metadata
        }

        override suspend fun updateUploadSuccess(
            matchId: String,
            storageBucket: String,
            storageObjectPath: String,
            uploadedAt: Long,
            updatedAt: Long,
        ) {
            metadata.value = metadata.value?.copy(
                storageBucket = storageBucket,
                storageObjectPath = storageObjectPath,
                uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
                uploadFailureCode = null,
                uploadedAt = uploadedAt,
                updatedAt = updatedAt,
                revision = metadata.value!!.revision + 1,
            )
        }

        override suspend fun updateUploadFailure(
            matchId: String,
            failureCode: String,
            updatedAt: Long,
        ) {
            metadata.value = metadata.value?.copy(
                uploadStatus = ScreenshotUploadStatus.FAILED.name,
                uploadFailureCode = failureCode,
                updatedAt = updatedAt,
                revision = metadata.value!!.revision + 1,
            )
        }

        override suspend fun markLocalMissing(matchId: String, updatedAt: Long) {
            metadata.value = metadata.value?.copy(
                localStatus = ScreenshotLocalStatus.MISSING.name,
                updatedAt = updatedAt,
                revision = metadata.value!!.revision + 1,
            )
        }

        override suspend fun markCleanupFailure(matchId: String, updatedAt: Long) {
            metadata.value = metadata.value?.copy(
                localStatus = ScreenshotLocalStatus.CLEANUP_FAILED.name,
                updatedAt = updatedAt,
                revision = metadata.value!!.revision + 1,
            )
        }

        override suspend fun deleteByMatchId(matchId: String) {
            metadata.value = null
        }

        override suspend fun deleteByTournamentId(tournamentId: String) {
            metadata.value = null
        }
    }
}
