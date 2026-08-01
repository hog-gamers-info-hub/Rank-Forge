package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.cloud.ScreenshotStorageUploadResult
import com.hoggamers.rankforge.data.cloud.ScreenshotStorageUploader
import com.hoggamers.rankforge.data.cloud.ScreenshotMetadataCloudDataSource
import com.hoggamers.rankforge.data.cloud.ScreenshotMetadataCloudFailure
import com.hoggamers.rankforge.data.cloud.ScreenshotMetadataCloudPayload
import com.hoggamers.rankforge.data.cloud.ScreenshotMetadataCloudResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotMetadataEntity
import com.hoggamers.rankforge.data.local.ScreenshotMetadataFailureCode
import com.hoggamers.rankforge.data.local.ScreenshotMetadataRepository
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
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
import java.nio.file.Files
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
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
    fun validatedDraftScreenshotCanLinkReplaceAndUnlinkWithoutChangingMatchData() = runTest {
        val viewModel = reviewViewModel()
        viewModel.load("tournament-id", matchId)
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
    fun linkedDraftScreenshotExposesOcrReviewNavigationForSameMatch() = runTest {
        val viewModel = reviewViewModel()
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        viewModel.onPhotoPickerResult("content://picker/ocr")
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canOpenOcrReview)
        assertEquals("tournament-id", viewModel.uiState.value.tournamentId)
        assertEquals(matchId, viewModel.uiState.value.matchId)

        viewModel.openOcrReview()

        assertEquals(MatchReviewNavigation.OCR_REVIEW, viewModel.uiState.value.navigation)
        assertEquals("tournament-id", viewModel.uiState.value.tournamentId)
        assertEquals(matchId, viewModel.uiState.value.matchId)

        viewModel.onNavigationHandled()

        assertNull(viewModel.uiState.value.navigation)
    }

    @Test
    fun invalidScreenshotDoesNotExposeOcrReviewNavigation() = runTest {
        val viewModel = reviewViewModel(
            imageCandidateValidator = ImageCandidateValidator(
                ImageCandidateMetadataReader {
                    ImageCandidateReadResult.Metadata("text/plain", width = 1080, height = 1920)
                },
            ),
        )
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        viewModel.onPhotoPickerResult("content://picker/not-image")
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()
        viewModel.openOcrReview()

        assertFalse(viewModel.uiState.value.canOpenOcrReview)
        assertNull(viewModel.uiState.value.navigation)
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
        viewModel.load("tournament-id", matchId)
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
                "users/user-id/tournaments/tournament-id/matches/$matchId/original.png",
            ),
        )
        val viewModel = reviewViewModel(
            screenshotDuplicateDetector = duplicateDetector(mapOf(uri to bytes)),
            localImagePreserver = localImagePreserver(mapOf(uri to bytes)),
            screenshotStorageUploader = uploader,
        )
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        viewModel.onPhotoPickerResult(uri)
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()

        assertEquals(1, uploader.calls.size)
        assertEquals(bytes.toList(), uploader.calls.single().readBytes().toList())
        assertTrue(viewModel.uiState.value.isScreenshotUploaded)
        assertEquals(
            "users/user-id/tournaments/tournament-id/matches/$matchId/original.png",
            viewModel.uiState.value.screenshotUploadObjectPath,
        )
    }

    @Test
    fun metadataIsCreatedAfterSuccessfulPreservationAndUpdatedAfterStorageUpload() = runTest {
        val uri = "content://picker/metadata"
        val bytes = byteArrayOf(1, 2, 3, 4)
        val metadataRepository = FakeScreenshotMetadataRepository()
        val uploader = RecordingScreenshotStorageUploader(
            ScreenshotStorageUploadResult.Uploaded(
                "users/owner-id/tournaments/tournament-id/matches/$matchId/original.png",
            ),
        )
        val viewModel = reviewViewModel(
            screenshotDuplicateDetector = duplicateDetector(mapOf(uri to bytes)),
            localImagePreserver = localImagePreserver(mapOf(uri to bytes)),
            screenshotStorageUploader = uploader,
            screenshotMetadataRepository = metadataRepository,
            screenshotOwnerProvider = FixedScreenshotOwnerProvider("owner-id"),
        )
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        viewModel.onPhotoPickerResult(uri)
        advanceUntilIdle()

        viewModel.linkScreenshot()
        advanceUntilIdle()

        val metadata = metadataRepository.metadata.value!!
        assertEquals(matchId, metadata.matchId)
        assertEquals("tournament-id", metadata.tournamentId)
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
                    "users/owner-id/tournaments/tournament-id/matches/$matchId/original.png",
                ),
            ),
            screenshotMetadataRepository = metadataRepository,
            screenshotMetadataCloudDataSource = cloudDataSource,
            screenshotOwnerProvider = FixedScreenshotOwnerProvider("owner-id"),
        )
        viewModel.load("tournament-id", matchId)
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
        viewModel.load("tournament-id", matchId)
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
        viewModel.load("tournament-id", matchId)
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
        viewModel.load("tournament-id", matchId)
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
        viewModel.load("tournament-id", matchId)
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
        viewModel.load("tournament-id", matchId)
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
        viewModel.load("tournament-id", matchId)
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
                tournamentId = "tournament-id",
                ownerUserId = "owner-id",
                localRelativePath = "screenshots/missing/match/original.png",
                fileExtension = "png",
                mimeType = "image/png",
                width = 1080,
                height = 1920,
                byteSize = 4,
                sha256 = "a".repeat(64),
                storageBucket = "match-screenshots",
                storageObjectPath = "users/owner-id/tournaments/tournament-id/matches/$matchId/original.png",
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

        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPreservedScreenshotMissing)
        assertEquals(ScreenshotPreservationError.LOCAL_FILE_MISSING, viewModel.uiState.value.screenshotPreservationError)
        assertEquals(ScreenshotLocalStatus.MISSING.name, metadataRepository.metadata.value!!.localStatus)
    }

    @Test
    fun screenshotLinkDoesNotCarryToAnotherMatchContext() = runTest {
        val secondMatchId = (CreateMatchUseCase(repository)(
            CreateMatchInput(
                tournamentId = "tournament-id",
                matchNumber = "2",
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
            ),
        ) as CreateMatchResult.Created).match.id
        val viewModel = reviewViewModel()
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        viewModel.onPhotoPickerResult("content://picker/first")
        advanceUntilIdle()
        viewModel.linkScreenshot()
        advanceUntilIdle()
        assertEquals("content://picker/first", viewModel.uiState.value.linkedScreenshotUri)

        viewModel.load("tournament-id", secondMatchId)
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
        viewModel.load("tournament-id", matchId)
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
                tournamentId = "tournament-id",
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
        firstViewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        firstViewModel.onPhotoPickerResult(firstUri)
        advanceUntilIdle()
        firstViewModel.linkScreenshot()
        advanceUntilIdle()

        val secondViewModel = reviewViewModel(screenshotDuplicateDetector = detector)
        secondViewModel.load("tournament-id", secondMatchId)
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
        viewModel.load("tournament-id", matchId)
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
        viewModel.load("tournament-id", matchId)
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
                "tournament-id",
                matchId,
                slotNumber,
                placementInput = slotNumber.toString(),
                killsInput = "0",
            )
        }
        val viewModel = reviewViewModel()
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()
        viewModel.finalize()
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
        assertEquals("tournament-id", viewModel.uiState.value.tournamentId)
        assertEquals(matchId, viewModel.uiState.value.matchId)
        assertFalse(viewModel.uiState.value.isEditable)
        assertEquals(null, viewModel.uiState.value.finalizationError)
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
        assertEquals("tournament-id", viewModel.uiState.value.tournamentId)
        assertEquals(matchId, viewModel.uiState.value.matchId)
        assertTrue(viewModel.uiState.value.isEditable)
        assertFalse(viewModel.uiState.value.isFinalizing)
    }

    @Test
    fun duplicatePlacementCannotFinalizeAndKeepsReviewContext() = runTest {
        (1..12).forEach { slotNumber ->
            repository.saveDraftMatchValue(
                "tournament-id",
                matchId,
                slotNumber,
                placementInput = if (slotNumber == 2) "1" else slotNumber.toString(),
                killsInput = (slotNumber - 1).toString(),
            )
        }
        val viewModel = reviewViewModel()
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isValid)
        assertTrue(
            MatchResultValidationError.DUPLICATE_PLACEMENT in
                viewModel.uiState.value.validationErrors.getValue(1),
        )
        viewModel.finalize()
        advanceUntilIdle()

        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(matchId).first()!!.status)
        assertEquals("tournament-id", viewModel.uiState.value.tournamentId)
        assertEquals(matchId, viewModel.uiState.value.matchId)
    }

    @Test
    fun negativeKillsCannotFinalize() = runTest {
        (1..12).forEach { slotNumber ->
            repository.saveDraftMatchValue(
                "tournament-id",
                matchId,
                slotNumber,
                placementInput = slotNumber.toString(),
                killsInput = if (slotNumber == 1) "-1" else (slotNumber - 1).toString(),
            )
        }
        val viewModel = reviewViewModel()
        viewModel.load("tournament-id", matchId)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isValid)
        assertTrue(
            MatchResultValidationError.INVALID_KILLS in
                viewModel.uiState.value.validationErrors.getValue(1),
        )
        viewModel.finalize()
        advanceUntilIdle()

        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(matchId).first()!!.status)
        assertEquals("tournament-id", viewModel.uiState.value.tournamentId)
        assertEquals(matchId, viewModel.uiState.value.matchId)
    }

    private fun reviewViewModel(
        imageCandidateValidator: ImageCandidateValidator = ImageCandidateValidator(
        ImageCandidateMetadataReader {
            ImageCandidateReadResult.Metadata("image/png", width = 1080, height = 1920)
        },
        ),
        screenshotDuplicateDetector: ScreenshotDuplicateDetector = duplicateDetector(),
        localImagePreserver: LocalImagePreserver = localImagePreserver(),
        screenshotStorageUploader: ScreenshotStorageUploader =
            com.hoggamers.rankforge.data.cloud.NoOpScreenshotStorageUploader(),
        screenshotMetadataRepository: ScreenshotMetadataRepository = FakeScreenshotMetadataRepository(),
        screenshotMetadataCloudDataSource: ScreenshotMetadataCloudDataSource =
            com.hoggamers.rankforge.data.cloud.NoOpScreenshotMetadataCloudDataSource(),
        screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
    ) = MatchReviewViewModel(
        observeMatches = ObserveMatchesUseCase(repository),
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
        observeRoster = ObserveRosterByTournamentUseCase(repository),
        observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
        validateMatchResult = ValidateMatchResultUseCase(),
        finalizeMatch = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase()),
        imageCandidateValidator = imageCandidateValidator,
        screenshotDuplicateDetector = screenshotDuplicateDetector,
        localImagePreserver = localImagePreserver,
        screenshotStorageUploader = screenshotStorageUploader,
        screenshotMetadataRepository = screenshotMetadataRepository,
        screenshotMetadataCloudDataSource = screenshotMetadataCloudDataSource,
        screenshotOwnerProvider = screenshotOwnerProvider,
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
