package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudFailure
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudResult
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotStorageUploadResult
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotStorageUploader
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchResultScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropProposer
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropResult
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
import com.hoggamers.rankforge.domain.tournament.CreateMatchInput
import com.hoggamers.rankforge.domain.tournament.CreateMatchResult
import com.hoggamers.rankforge.domain.tournament.CreateMatchUseCase
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val CROP_TOURNAMENT_ID = "33333333-3333-3333-3333-333333333333"

@OptIn(ExperimentalCoroutinesApi::class)
class MatchResultScreenshotCropViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var tournamentRepository: InMemoryTournamentRepository
    private lateinit var matchId: String

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(dispatcher)
        tournamentRepository = InMemoryTournamentRepository()
        tournamentRepository.create(
            Tournament(
                id = CROP_TOURNAMENT_ID,
                name = "Crop Cup",
                date = LocalDate.of(2026, 8, 7),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        tournamentRepository.saveTeamNames(
            CROP_TOURNAMENT_ID,
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        matchId = "crop-match-id"
        tournamentRepository.createDraftMatch(
            Match(
                id = matchId,
                tournamentId = CROP_TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 8, 7),
                mapName = "Bermuda",
                status = com.hoggamers.rankforge.domain.tournament.MatchStatus.DRAFT,
            ),
        )
        Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun confirmCropPersistsLocalCropAndDoesNotRollBackWhenCloudMetadataFails() = runTest {
        val root = Files.createTempDirectory("rank-forge-crop").toFile()
        val preserver = LocalImagePreserver(
            appPrivateRoot = root,
            sourceStreamOpener = ImageSourceStreamOpener { null },
            mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val file = preserver.matchResultPreservedFile(
            tournamentId = CROP_TOURNAMENT_ID,
            matchId = matchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            extension = "png",
        )
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        val repository = FakeCropAssetRepository(
            asset(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                relativePath = preserver.relativePathFor(file)!!,
            ),
        )
        val viewModel = viewModel(
            repository = repository,
            preserver = preserver,
            cloud = FailingCropCloudDataSource(),
            clock = Clock.fixed(Instant.ofEpochMilli(50L), ZoneOffset.UTC),
        )

        viewModel.load(CROP_TOURNAMENT_ID, matchId, MatchResultScreenshotRole.MATCH_RESULT_UPPER.name)
        advanceUntilIdle()
        val crop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9)
        viewModel.onCropChanged(crop)
        viewModel.confirmCrop {}
        advanceUntilIdle()

        val saved = repository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER))!!
        assertEquals(crop.left, saved.cropLeft!!, 0.0)
        assertEquals(crop.right, saved.cropRight!!, 0.0)
        assertEquals(ScreenshotUploadStatus.FAILED.name, saved.uploadStatus)
        assertEquals(MatchResultScreenshotAssetCloudFailure.WRITE_FAILED.name, saved.uploadFailureCode)
        assertTrue(viewModel.uiState.value.confirmedCrop == crop)
    }

    @Test
    fun confirmCropPersistsBeforeUploadingOriginalAndUpsertsCombinedMetadata() = runTest {
        val root = Files.createTempDirectory("rank-forge-crop-upload").toFile()
        val preserver = LocalImagePreserver(
            appPrivateRoot = root,
            sourceStreamOpener = ImageSourceStreamOpener { null },
            mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val file = preserver.matchResultPreservedFile(
            tournamentId = CROP_TOURNAMENT_ID,
            matchId = matchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            extension = "png",
        )
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(4, 5, 6))
        val repository = FakeCropAssetRepository(
            asset(
                role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                relativePath = preserver.relativePathFor(file)!!,
            ).copy(
                uploadStatus = ScreenshotUploadStatus.PENDING.name,
                storageBucket = null,
                storageObjectPath = null,
                uploadedAt = null,
            ),
        )
        val uploader = RecordingResultStorageUploader(repository)
        val cloud = RecordingCropCloudDataSource()
        val viewModel = viewModel(
            repository = repository,
            preserver = preserver,
            cloud = cloud,
            storageUploader = uploader,
        )

        viewModel.load(CROP_TOURNAMENT_ID, matchId, MatchResultScreenshotRole.MATCH_RESULT_LOWER.name)
        advanceUntilIdle()
        val crop = OcrNormalizedCropRect(0.2, 0.1, 0.8, 0.9)
        var confirmations = 0
        viewModel.onCropChanged(crop)
        viewModel.confirmCrop { confirmations++ }
        advanceUntilIdle()

        assertEquals(1, uploader.calls.size)
        assertEquals(file, uploader.calls.single().localFile)
        assertEquals(crop.left, uploader.cropAtUpload!!.cropLeft!!, 0.0)
        assertEquals(1, cloud.upserts.size)
        assertEquals(crop.left, cloud.upserts.single().cropLeft!!, 0.0)
        assertEquals("cloud/result/lower/original.png", cloud.upserts.single().storageObjectPath)
        assertEquals(ScreenshotUploadStatus.UPLOADED.name, repository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_LOWER))?.uploadStatus)
        assertEquals(1, confirmations)
    }


    @Test
    fun missingMatchCannotPersistCrop() = runTest {
        val missingMatchId = "missing-match"
        val root = Files.createTempDirectory("rank-forge-crop-missing").toFile()
        val preserver = LocalImagePreserver(
            appPrivateRoot = root,
            sourceStreamOpener = ImageSourceStreamOpener { null },
            mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val file = preserver.matchResultPreservedFile(
            tournamentId = CROP_TOURNAMENT_ID,
            matchId = missingMatchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            extension = "png",
        )
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        val repository = FakeCropAssetRepository(
            asset(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                relativePath = preserver.relativePathFor(file)!!,
            ).copy(matchId = missingMatchId),
        )
        val viewModel = viewModel(
            repository = repository,
            preserver = preserver,
            cloud = FailingCropCloudDataSource(),
        )

        viewModel.load(
            CROP_TOURNAMENT_ID,
            missingMatchId,
            MatchResultScreenshotRole.MATCH_RESULT_UPPER.name,
        )
        advanceUntilIdle()
        viewModel.onCropChanged(OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9))
        viewModel.confirmCrop {}
        advanceUntilIdle()

        assertEquals(0, repository.persistConfirmedCropCalls)
        assertNull(repository.getByIdentity(
            MatchResultScreenshotIdentity(
                tournamentId = CROP_TOURNAMENT_ID,
                matchId = missingMatchId,
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            ),
        )?.cropLeft)
        assertEquals(
            MatchResultScreenshotCropError.SAVE_FAILED,
            viewModel.uiState.value.error,
        )
    }

    @Test
    fun finalizedMatchCannotPersistCrop() = runTest {
        tournamentRepository.finalizeDraftMatch(
            matchId = matchId,
            placements = (1..12).map { slot -> MatchPlacement(slot, slot) },
            kills = (1..12).map { slot -> MatchKill(slot, 0) },
        )

        val root = Files.createTempDirectory("rank-forge-crop-finalized").toFile()
        val preserver = LocalImagePreserver(
            appPrivateRoot = root,
            sourceStreamOpener = ImageSourceStreamOpener { null },
            mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val file = preserver.matchResultPreservedFile(
            tournamentId = CROP_TOURNAMENT_ID,
            matchId = matchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            extension = "png",
        )
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        val repository = FakeCropAssetRepository(
            asset(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                relativePath = preserver.relativePathFor(file)!!,
            ),
        )
        val viewModel = viewModel(
            repository = repository,
            preserver = preserver,
            cloud = FailingCropCloudDataSource(),
        )

        viewModel.load(
            CROP_TOURNAMENT_ID,
            matchId,
            MatchResultScreenshotRole.MATCH_RESULT_UPPER.name,
        )
        advanceUntilIdle()
        viewModel.onCropChanged(OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9))
        viewModel.confirmCrop {}
        advanceUntilIdle()

        assertEquals(0, repository.persistConfirmedCropCalls)
        assertNull(repository.getByIdentity(
            identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
        )?.cropLeft)
        assertEquals(
            MatchResultScreenshotCropError.FINALIZED_MATCH,
            viewModel.uiState.value.error,
        )
    }

    @Test
    fun cloudMetadataCancellationIsNotConvertedToUploadFailure() = runTest {
        val root = Files.createTempDirectory("rank-forge-crop-cancel").toFile()
        val preserver = LocalImagePreserver(
            appPrivateRoot = root,
            sourceStreamOpener = ImageSourceStreamOpener { null },
            mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val file = preserver.matchResultPreservedFile(
            tournamentId = CROP_TOURNAMENT_ID,
            matchId = matchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            extension = "png",
        )
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        val repository = FakeCropAssetRepository(
            asset(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                relativePath = preserver.relativePathFor(file)!!,
            ),
        )
        val viewModel = viewModel(
            repository = repository,
            preserver = preserver,
            cloud = CancellingCropCloudDataSource(),
        )
        var confirmedCallbackCalled = false

        viewModel.load(
            CROP_TOURNAMENT_ID,
            matchId,
            MatchResultScreenshotRole.MATCH_RESULT_UPPER.name,
        )
        advanceUntilIdle()
        val crop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9)
        viewModel.onCropChanged(crop)
        viewModel.confirmCrop { confirmedCallbackCalled = true }
        advanceUntilIdle()

        val saved = repository.getByIdentity(
            identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
        )!!
        assertEquals(crop.left, saved.cropLeft!!, 0.0)
        assertEquals(ScreenshotUploadStatus.UPLOADED.name, saved.uploadStatus)
        assertNull(saved.uploadFailureCode)
        assertTrue(confirmedCallbackCalled)
    }

    @Test
    fun confirmCropCallbackDoesNotWaitForSuspendedCloudMetadata() = runTest {
        val root = Files.createTempDirectory("rank-forge-crop-nonblocking").toFile()
        val preserver = LocalImagePreserver(
            appPrivateRoot = root,
            sourceStreamOpener = ImageSourceStreamOpener { null },
            mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val file = preserver.matchResultPreservedFile(
            tournamentId = CROP_TOURNAMENT_ID,
            matchId = matchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            extension = "png",
        )
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        val repository = FakeCropAssetRepository(
            asset(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                relativePath = preserver.relativePathFor(file)!!,
            ),
        )
        val cloudStarted = CompletableDeferred<Unit>()
        val cloudResult = CompletableDeferred<MatchResultScreenshotAssetCloudResult>()
        val viewModel = viewModel(
            repository = repository,
            preserver = preserver,
            cloud = SuspendingResultCropCloudDataSource(cloudStarted, cloudResult),
        )
        var confirmations = 0

        viewModel.load(CROP_TOURNAMENT_ID, matchId, MatchResultScreenshotRole.MATCH_RESULT_UPPER.name)
        advanceUntilIdle()
        viewModel.onCropChanged(OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9))
        viewModel.confirmCrop { confirmations++ }
        advanceUntilIdle()

        assertTrue(cloudStarted.isCompleted)
        assertEquals(1, confirmations)
        assertEquals(
            ScreenshotUploadStatus.UPLOADED.name,
            repository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER))?.uploadStatus,
        )

        cloudResult.complete(MatchResultScreenshotAssetCloudResult.Success)
        advanceUntilIdle()
    }

    @Test
    fun proposedCropInitializesDraftAndSurvivesRepeatedAssetEmissionWithOneAttempt() = runTest {
        val root = Files.createTempDirectory("rank-forge-auto-crop-proposed").toFile()
        val preserver = testPreserver(root)
        val file = preserver.matchResultPreservedFile(
            tournamentId = CROP_TOURNAMENT_ID,
            matchId = matchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            extension = "png",
        )
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        val repository = FakeCropAssetRepository(
            asset(MatchResultScreenshotRole.MATCH_RESULT_UPPER, preserver.relativePathFor(file)!!),
        )
        val proposed = OcrNormalizedCropRect(0.12, 0.18, 0.88, 0.82)
        val proposer = RecordingAutoCropProposer(MatchResultAutoCropResult.Proposed(proposed))
        val viewModel = viewModel(repository, preserver, autoCropProposer = proposer)

        viewModel.load(CROP_TOURNAMENT_ID, matchId, MatchResultScreenshotRole.MATCH_RESULT_UPPER.name)
        advanceUntilIdle()

        assertEquals(proposed, viewModel.uiState.value.draftCrop)
        assertEquals(1, proposer.calls)
        repository.updateAsset(repository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER))!!.copy(updatedAt = 2L))
        advanceUntilIdle()
        assertEquals(proposed, viewModel.uiState.value.draftCrop)
        assertEquals(1, proposer.calls)
    }

    @Test
    fun confirmedCropSkipsAutoCropProposer() = runTest {
        val root = Files.createTempDirectory("rank-forge-auto-crop-confirmed").toFile()
        val preserver = testPreserver(root)
        val file = preserver.matchResultPreservedFile(
            tournamentId = CROP_TOURNAMENT_ID,
            matchId = matchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            extension = "png",
        )
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(4, 5, 6))
        val confirmed = OcrNormalizedCropRect(0.2, 0.2, 0.8, 0.8)
        val repository = FakeCropAssetRepository(
            asset(MatchResultScreenshotRole.MATCH_RESULT_LOWER, preserver.relativePathFor(file)!!).copy(
                cropProfileId = "match-result",
                cropLeft = confirmed.left,
                cropTop = confirmed.top,
                cropRight = confirmed.right,
                cropBottom = confirmed.bottom,
            ),
        )
        val proposer = RecordingAutoCropProposer(MatchResultAutoCropResult.Proposed(OcrVisualCropDefaults.FullImageCrop))
        val viewModel = viewModel(repository, preserver, autoCropProposer = proposer)

        viewModel.load(CROP_TOURNAMENT_ID, matchId, MatchResultScreenshotRole.MATCH_RESULT_LOWER.name)
        advanceUntilIdle()

        assertEquals(confirmed, viewModel.uiState.value.draftCrop)
        assertEquals(0, proposer.calls)
    }

    @Test
    fun proposerFailureFallsBackToFullImageCrop() = runTest {
        val root = Files.createTempDirectory("rank-forge-auto-crop-failure").toFile()
        val preserver = testPreserver(root)
        val file = preserver.matchResultPreservedFile(
            tournamentId = CROP_TOURNAMENT_ID,
            matchId = matchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            extension = "png",
        )
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        val repository = FakeCropAssetRepository(asset(MatchResultScreenshotRole.MATCH_RESULT_UPPER, preserver.relativePathFor(file)!!))
        val proposer = RecordingAutoCropProposer(MatchResultAutoCropResult.OcrFailed)
        val viewModel = viewModel(repository, preserver, autoCropProposer = proposer)

        viewModel.load(CROP_TOURNAMENT_ID, matchId, MatchResultScreenshotRole.MATCH_RESULT_UPPER.name)
        advanceUntilIdle()

        assertEquals(OcrVisualCropDefaults.FullImageCrop, viewModel.uiState.value.draftCrop)
        assertEquals(1, proposer.calls)
    }

    @Test
    fun anchorFourMissingFallsBackWithoutRetryOrPersistence() = runTest {
        val root = Files.createTempDirectory("rank-forge-auto-crop-anchor-four-missing").toFile()
        val preserver = testPreserver(root)
        val file = preserver.matchResultPreservedFile(
            tournamentId = CROP_TOURNAMENT_ID,
            matchId = matchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            extension = "png",
        )
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        val repository = FakeCropAssetRepository(asset(MatchResultScreenshotRole.MATCH_RESULT_UPPER, preserver.relativePathFor(file)!!))
        val proposer = RecordingAutoCropProposer(MatchResultAutoCropResult.AnchorFourMissing)
        val viewModel = viewModel(repository, preserver, autoCropProposer = proposer)

        viewModel.load(CROP_TOURNAMENT_ID, matchId, MatchResultScreenshotRole.MATCH_RESULT_UPPER.name)
        advanceUntilIdle()
        repository.updateAsset(repository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER))!!.copy(updatedAt = 2L))
        advanceUntilIdle()

        assertEquals(OcrVisualCropDefaults.FullImageCrop, viewModel.uiState.value.draftCrop)
        assertEquals(1, proposer.calls)
        assertEquals(0, repository.persistConfirmedCropCalls)
    }

    @Test
    fun draftPriorityIsUserEditThenConfirmedThenAutoThenFullImage() = runTest {
        val root = Files.createTempDirectory("rank-forge-auto-crop-priority").toFile()
        val preserver = testPreserver(root)
        val file = preserver.matchResultPreservedFile(
            tournamentId = CROP_TOURNAMENT_ID,
            matchId = matchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            extension = "png",
        )
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        val automatic = OcrNormalizedCropRect(0.15, 0.15, 0.85, 0.85)
        val confirmed = OcrNormalizedCropRect(0.25, 0.25, 0.75, 0.75)
        val manual = OcrNormalizedCropRect(0.05, 0.05, 0.95, 0.95)
        val repository = FakeCropAssetRepository(asset(MatchResultScreenshotRole.MATCH_RESULT_UPPER, preserver.relativePathFor(file)!!))
        val proposer = RecordingAutoCropProposer(MatchResultAutoCropResult.Proposed(automatic))
        val viewModel = viewModel(repository, preserver, autoCropProposer = proposer)

        viewModel.load(CROP_TOURNAMENT_ID, matchId, MatchResultScreenshotRole.MATCH_RESULT_UPPER.name)
        advanceUntilIdle()
        assertEquals(automatic, viewModel.uiState.value.draftCrop)

        repository.updateAsset(repository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER))!!.copy(
            cropProfileId = "match-result",
            cropLeft = confirmed.left,
            cropTop = confirmed.top,
            cropRight = confirmed.right,
            cropBottom = confirmed.bottom,
            updatedAt = 2L,
        ))
        advanceUntilIdle()
        assertEquals(confirmed, viewModel.uiState.value.draftCrop)

        viewModel.onCropChanged(manual)
        repository.updateAsset(repository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER))!!.copy(updatedAt = 3L))
        advanceUntilIdle()
        assertEquals(manual, viewModel.uiState.value.draftCrop)
    }

    @Test
    fun lowerResultScreenshotUsesSameProposalPathAndDoesNotShareUpperState() = runTest {
        val root = Files.createTempDirectory("rank-forge-auto-crop-roles").toFile()
        val preserver = testPreserver(root)
        val upperFile = preserver.matchResultPreservedFile(
            tournamentId = CROP_TOURNAMENT_ID,
            matchId = matchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            extension = "png",
        )
        val lowerFile = preserver.matchResultPreservedFile(
            tournamentId = CROP_TOURNAMENT_ID,
            matchId = matchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            extension = "png",
        )
        upperFile.parentFile?.mkdirs()
        lowerFile.parentFile?.mkdirs()
        upperFile.writeBytes(byteArrayOf(1, 2, 3))
        lowerFile.writeBytes(byteArrayOf(4, 5, 6))
        val upperCrop = OcrNormalizedCropRect(0.1, 0.1, 0.7, 0.7)
        val lowerCrop = OcrNormalizedCropRect(0.2, 0.2, 0.8, 0.8)
        val repository = FakeCropAssetRepository(
            asset(MatchResultScreenshotRole.MATCH_RESULT_UPPER, preserver.relativePathFor(upperFile)!!),
            additionalAssets = listOf(
                asset(MatchResultScreenshotRole.MATCH_RESULT_LOWER, preserver.relativePathFor(lowerFile)!!).copy(
                    sha256 = "b".repeat(64),
                ),
            ),
        )
        val proposer = SequencedImmediateAutoCropProposer(
            MatchResultAutoCropResult.Proposed(upperCrop),
            MatchResultAutoCropResult.Proposed(lowerCrop),
        )
        val viewModel = viewModel(repository, preserver, autoCropProposer = proposer)

        viewModel.load(CROP_TOURNAMENT_ID, matchId, MatchResultScreenshotRole.MATCH_RESULT_UPPER.name)
        advanceUntilIdle()
        assertEquals(upperCrop, viewModel.uiState.value.draftCrop)
        viewModel.load(CROP_TOURNAMENT_ID, matchId, MatchResultScreenshotRole.MATCH_RESULT_LOWER.name)
        advanceUntilIdle()
        assertEquals(lowerCrop, viewModel.uiState.value.draftCrop)
        viewModel.load(CROP_TOURNAMENT_ID, matchId, MatchResultScreenshotRole.MATCH_RESULT_UPPER.name)
        advanceUntilIdle()

        assertEquals(upperCrop, viewModel.uiState.value.draftCrop)
        assertEquals(2, proposer.calls)
    }

    @Test
    fun userEditWinsDelayedAutoCropProposal() = runTest {
        val root = Files.createTempDirectory("rank-forge-auto-crop-user-edit").toFile()
        val preserver = testPreserver(root)
        val file = preserver.matchResultPreservedFile(
            tournamentId = CROP_TOURNAMENT_ID,
            matchId = matchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            extension = "png",
        )
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        val repository = FakeCropAssetRepository(asset(MatchResultScreenshotRole.MATCH_RESULT_UPPER, preserver.relativePathFor(file)!!))
        val response = CompletableDeferred<MatchResultAutoCropResult>()
        val proposer = SuspendingAutoCropProposer(response)
        val viewModel = viewModel(repository, preserver, autoCropProposer = proposer)

        viewModel.load(CROP_TOURNAMENT_ID, matchId, MatchResultScreenshotRole.MATCH_RESULT_UPPER.name)
        runCurrent()
        val manual = OcrNormalizedCropRect(0.05, 0.1, 0.95, 0.9)
        viewModel.onCropChanged(manual)
        response.complete(MatchResultAutoCropResult.Proposed(OcrNormalizedCropRect(0.2, 0.2, 0.8, 0.8)))
        advanceUntilIdle()

        assertEquals(1, proposer.calls)
        assertEquals(manual, viewModel.uiState.value.draftCrop)
    }

    @Test
    fun staleProposalIsIgnoredAndReplacementAssetGetsItsOwnAttempt() = runTest {
        val root = Files.createTempDirectory("rank-forge-auto-crop-stale").toFile()
        val preserver = testPreserver(root)
        val file = preserver.matchResultPreservedFile(
            tournamentId = CROP_TOURNAMENT_ID,
            matchId = matchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            extension = "png",
        )
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        val first = asset(MatchResultScreenshotRole.MATCH_RESULT_UPPER, preserver.relativePathFor(file)!!)
        val repository = FakeCropAssetRepository(first)
        val firstResponse = CompletableDeferred<MatchResultAutoCropResult>()
        val secondResponse = CompletableDeferred<MatchResultAutoCropResult>()
        val proposer = SequencedAutoCropProposer(firstResponse, secondResponse)
        val viewModel = viewModel(repository, preserver, autoCropProposer = proposer)

        viewModel.load(CROP_TOURNAMENT_ID, matchId, MatchResultScreenshotRole.MATCH_RESULT_UPPER.name)
        runCurrent()
        repository.updateAsset(first.copy(sha256 = "b".repeat(64), updatedAt = 2L))
        runCurrent()
        assertEquals(2, proposer.calls)

        firstResponse.complete(MatchResultAutoCropResult.Proposed(OcrNormalizedCropRect(0.1, 0.1, 0.7, 0.7)))
        runCurrent()
        assertEquals(OcrVisualCropDefaults.FullImageCrop, viewModel.uiState.value.draftCrop)
        val replacementCrop = OcrNormalizedCropRect(0.2, 0.2, 0.8, 0.8)
        secondResponse.complete(MatchResultAutoCropResult.Proposed(replacementCrop))
        advanceUntilIdle()
        assertEquals(replacementCrop, viewModel.uiState.value.draftCrop)
    }

    private fun testPreserver(root: java.io.File) = LocalImagePreserver(
        appPrivateRoot = root,
        sourceStreamOpener = ImageSourceStreamOpener { null },
        mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun viewModel(
        repository: FakeCropAssetRepository,
        preserver: LocalImagePreserver,
        cloud: MatchResultScreenshotAssetCloudDataSource = FailingCropCloudDataSource(),
        storageUploader: MatchResultScreenshotStorageUploader? = null,
        clock: Clock = Clock.systemUTC(),
        autoCropProposer: MatchResultAutoCropProposer = MatchResultAutoCropProposer {
            MatchResultAutoCropResult.OcrFailed
        },
    ): MatchResultScreenshotCropViewModel {
        val checkpoint = MatchResultScreenshotUploadCheckpoint(
            assetRepository = repository,
            localImagePreserver = preserver,
            clock = clock,
            storageUploader = storageUploader ?: RecordingResultStorageUploader(repository),
            cloudDataSource = cloud,
        )
        return MatchResultScreenshotCropViewModel(
            observeMatches = ObserveMatchesUseCase(tournamentRepository),
            assetRepository = repository,
            localImagePreserver = preserver,
            clock = clock,
            uploadCheckpoint = checkpoint,
            reconciliationScheduler = ScreenshotReconciliationScheduler(
                scope = CoroutineScope(SupervisorJob() + dispatcher),
                testOnly = true,
            ),
            autoCropProposer = autoCropProposer,
            screenshotOwnerProvider = object : ScreenshotOwnerProvider {
                override suspend fun currentOwnerUserId(): String = "owner-1"
            },
        )
    }

    private fun identity(role: MatchResultScreenshotRole) = MatchResultScreenshotIdentity(
        tournamentId = CROP_TOURNAMENT_ID,
        matchId = matchId,
        role = role,
    )

    private fun asset(
        role: MatchResultScreenshotRole,
        relativePath: String,
    ) = MatchResultScreenshotAssetEntity(
        tournamentId = CROP_TOURNAMENT_ID,
        matchId = matchId,
        screenshotKind = OcrScreenshotKind.MATCH_RESULT.name,
        screenshotRole = role.name,
        ownerUserId = "owner-id",
        localRelativePath = relativePath,
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 1600,
        originalHeight = 720,
        byteSize = 3L,
        sha256 = "a".repeat(64),
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
        uploadFailureCode = null,
        storageBucket = "ocr-screenshots",
        storageObjectPath = "object/path.png",
        cropProfileId = null,
        cropLeft = null,
        cropTop = null,
        cropRight = null,
        cropBottom = null,
        createdAt = 1L,
        updatedAt = 1L,
        preservedAt = 1L,
        uploadedAt = 1L,
        revision = 1L,
    )

    private class RecordingAutoCropProposer(
        private val response: MatchResultAutoCropResult,
    ) : MatchResultAutoCropProposer {
        var calls = 0

        override suspend fun propose(localFile: java.io.File): MatchResultAutoCropResult {
            calls++
            return response
        }
    }

    private class SuspendingAutoCropProposer(
        private val response: CompletableDeferred<MatchResultAutoCropResult>,
    ) : MatchResultAutoCropProposer {
        var calls = 0

        override suspend fun propose(localFile: java.io.File): MatchResultAutoCropResult {
            calls++
            return response.await()
        }
    }

    private class SequencedAutoCropProposer(
        private vararg val responses: CompletableDeferred<MatchResultAutoCropResult>,
    ) : MatchResultAutoCropProposer {
        var calls = 0

        override suspend fun propose(localFile: java.io.File): MatchResultAutoCropResult {
            val response = responses[calls++]
            return response.await()
        }
    }

    private class SequencedImmediateAutoCropProposer(
        private vararg val responses: MatchResultAutoCropResult,
    ) : MatchResultAutoCropProposer {
        var calls = 0

        override suspend fun propose(localFile: java.io.File): MatchResultAutoCropResult = responses[calls++]
    }

    private class FailingCropCloudDataSource : MatchResultScreenshotAssetCloudDataSource {
        override suspend fun upsert(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetCloudResult =
            MatchResultScreenshotAssetCloudResult.Failed(MatchResultScreenshotAssetCloudFailure.WRITE_FAILED)

        override suspend fun deleteByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): MatchResultScreenshotAssetCloudResult = MatchResultScreenshotAssetCloudResult.Success
    }

    private class CancellingCropCloudDataSource : MatchResultScreenshotAssetCloudDataSource {
        override suspend fun upsert(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetCloudResult {
            throw CancellationException("test cancellation")
        }

        override suspend fun deleteByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): MatchResultScreenshotAssetCloudResult = MatchResultScreenshotAssetCloudResult.Success
    }

    private class RecordingCropCloudDataSource : MatchResultScreenshotAssetCloudDataSource {
        val upserts = mutableListOf<MatchResultScreenshotAssetEntity>()

        override suspend fun upsert(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetCloudResult {
            upserts += asset
            return MatchResultScreenshotAssetCloudResult.Success
        }

        override suspend fun deleteByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): MatchResultScreenshotAssetCloudResult = MatchResultScreenshotAssetCloudResult.Success
    }

    private class SuspendingResultCropCloudDataSource(
        private val started: CompletableDeferred<Unit>,
        private val result: CompletableDeferred<MatchResultScreenshotAssetCloudResult>,
    ) : MatchResultScreenshotAssetCloudDataSource {
        override suspend fun upsert(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetCloudResult {
            started.complete(Unit)
            return result.await()
        }

        override suspend fun deleteByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): MatchResultScreenshotAssetCloudResult = MatchResultScreenshotAssetCloudResult.Success
    }

    private class RecordingResultStorageUploader(
        private val repository: FakeCropAssetRepository,
    ) : MatchResultScreenshotStorageUploader {
        data class Call(val localFile: java.io.File)

        val calls = mutableListOf<Call>()
        var cropAtUpload: MatchResultScreenshotAssetEntity? = null

        override suspend fun upload(
            tournamentId: String?,
            matchId: String?,
            role: MatchResultScreenshotRole?,
            localFile: java.io.File?,
        ): MatchResultScreenshotStorageUploadResult {
            cropAtUpload = repository.getByIdentity(
                MatchResultScreenshotIdentity(
                    tournamentId = tournamentId!!,
                    matchId = matchId!!,
                    role = role!!,
                ),
            )
            calls += Call(localFile!!)
            return MatchResultScreenshotStorageUploadResult.Uploaded("cloud/result/lower/original.png")
        }
    }

    private class FakeCropAssetRepository(
        asset: MatchResultScreenshotAssetEntity,
        additionalAssets: List<MatchResultScreenshotAssetEntity> = emptyList(),
    ) : MatchResultScreenshotAssetRepository {
        private val assets = MutableStateFlow(listOf(asset) + additionalAssets)
        var persistConfirmedCropCalls: Int = 0
            private set

        fun updateAsset(value: MatchResultScreenshotAssetEntity) {
            assets.value = assets.value.filterNot { it.matches(value.identity()) } + value
        }

        override fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
            assets.map { list -> list.filter { it.matchId == matchId } }

        override fun observeByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): Flow<MatchResultScreenshotAssetEntity?> =
            assets.map { list -> list.firstOrNull { it.matches(identity) } }
        override fun observeByIdentityAndOwner(identity: MatchResultScreenshotIdentity, ownerUserId: String): Flow<MatchResultScreenshotAssetEntity?> =
            if (ownerUserId.isBlank()) emptyFlow() else observeByIdentity(identity)

        override suspend fun getByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): MatchResultScreenshotAssetEntity? = assets.value.firstOrNull { it.matches(identity) }
        override suspend fun getByIdentityAndOwner(identity: MatchResultScreenshotIdentity, ownerUserId: String) =
            if (ownerUserId.isBlank()) null else getByIdentity(identity)

        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
            assets.map { list -> list.filter { it.tournamentId == tournamentId } }

        override suspend fun findDuplicateFingerprint(
            identity: MatchResultScreenshotIdentity,
            sha256: String,
        ): MatchResultScreenshotAssetEntity? = null

        override suspend fun saveOrReplace(
            asset: MatchResultScreenshotAssetEntity,
        ): MatchResultScreenshotAssetSaveResult {
            assets.value = assets.value.filterNot {
                it.matchId == asset.matchId && it.screenshotRole == asset.screenshotRole
            } + asset
            return MatchResultScreenshotAssetSaveResult.Saved
        }
        override suspend fun saveOrReplaceByOwner(asset: MatchResultScreenshotAssetEntity, ownerUserId: String) =
            if (ownerUserId.isBlank()) MatchResultScreenshotAssetSaveResult.AuthenticationRequired else saveOrReplace(asset.copy(ownerUserId = ownerUserId))

        override suspend fun updateUploadSuccessIfFingerprintMatches(
            identity: MatchResultScreenshotIdentity,
            sha256: String,
            storageBucket: String,
            storageObjectPath: String,
            uploadedAt: Long,
            updatedAt: Long,
        ): Boolean {
            val current = getByIdentity(identity) ?: return false
            if (current.sha256 != sha256) return false
            return saveOrReplace(
                current.copy(
                    storageBucket = storageBucket,
                    storageObjectPath = storageObjectPath,
                    uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
                    uploadFailureCode = null,
                    uploadedAt = uploadedAt,
                    updatedAt = updatedAt,
                    revision = current.revision + 1L,
                ),
            ) is MatchResultScreenshotAssetSaveResult.Saved
        }

        override suspend fun updateUploadFailureIfFingerprintMatches(
            identity: MatchResultScreenshotIdentity,
            sha256: String,
            failureCode: String,
            updatedAt: Long,
        ): Boolean {
            val current = getByIdentity(identity) ?: return false
            if (current.sha256 != sha256) return false
            return saveOrReplace(
                current.copy(
                    uploadStatus = ScreenshotUploadStatus.FAILED.name,
                    uploadFailureCode = failureCode,
                    updatedAt = updatedAt,
                    revision = current.revision + 1L,
                ),
            ) is MatchResultScreenshotAssetSaveResult.Saved
        }

        override suspend fun updateUploadSuccessIfGenerationMatches(
            identity: MatchResultScreenshotIdentity,
            sha256: String,
            expectedRevision: Long,
            storageBucket: String,
            storageObjectPath: String,
            uploadedAt: Long,
            updatedAt: Long,
        ): Boolean {
            val current = getByIdentity(identity) ?: return false
            if (current.sha256 != sha256 || current.revision != expectedRevision) return false
            return saveOrReplace(
                current.copy(
                    storageBucket = storageBucket,
                    storageObjectPath = storageObjectPath,
                    uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
                    uploadFailureCode = null,
                    uploadedAt = uploadedAt,
                    updatedAt = updatedAt,
                    revision = current.revision + 1L,
                ),
            ) is MatchResultScreenshotAssetSaveResult.Saved
        }

        override suspend fun updateUploadFailureIfGenerationMatches(
            identity: MatchResultScreenshotIdentity,
            sha256: String,
            expectedRevision: Long,
            failureCode: String,
            updatedAt: Long,
        ): Boolean {
            val current = getByIdentity(identity) ?: return false
            if (current.sha256 != sha256 || current.revision != expectedRevision) return false
            return saveOrReplace(
                current.copy(
                    uploadStatus = ScreenshotUploadStatus.FAILED.name,
                    uploadFailureCode = failureCode,
                    updatedAt = updatedAt,
                    revision = current.revision + 1L,
                ),
            ) is MatchResultScreenshotAssetSaveResult.Saved
        }

        override suspend fun markLocalMissing(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markLocalMissingByOwner(identity: MatchResultScreenshotIdentity, ownerUserId: String, updatedAt: Long) = ownerUserId.isNotBlank()

        override suspend fun markCleanupFailure(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit

        override suspend fun persistConfirmedCrop(
            identity: MatchResultScreenshotIdentity,
            crop: OcrNormalizedCropRect,
            updatedAt: Long,
        ): MatchResultScreenshotCropSaveResult {
            persistConfirmedCropCalls += 1
            val asset = getByIdentity(identity) ?: return MatchResultScreenshotCropSaveResult.MissingAsset
            assets.value = assets.value.filterNot { it.matches(identity) } + asset.copy(
                cropProfileId = "match-result",
                cropLeft = crop.left,
                cropTop = crop.top,
                cropRight = crop.right,
                cropBottom = crop.bottom,
                updatedAt = updatedAt,
                revision = asset.revision + 1,
            )
            return MatchResultScreenshotCropSaveResult.Saved
        }
        override suspend fun persistConfirmedCropByOwner(identity: MatchResultScreenshotIdentity, ownerUserId: String, crop: OcrNormalizedCropRect, updatedAt: Long) =
            if (ownerUserId.isBlank()) MatchResultScreenshotCropSaveResult.AuthenticationRequired else persistConfirmedCrop(identity, crop, updatedAt)

        override suspend fun clearConfirmedCrop(
            identity: MatchResultScreenshotIdentity,
            updatedAt: Long,
        ): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.Saved

        override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) = Unit

        override suspend fun deleteByMatchId(matchId: String) = Unit

        private fun MatchResultScreenshotAssetEntity.matches(identity: MatchResultScreenshotIdentity): Boolean =
            tournamentId == identity.tournamentId &&
                matchId == identity.matchId &&
                screenshotRole == identity.role.name

        private fun MatchResultScreenshotAssetEntity.identity() = MatchResultScreenshotIdentity(
            tournamentId = tournamentId,
            matchId = matchId,
            role = MatchResultScreenshotRole.valueOf(screenshotRole),
        )
    }
}
