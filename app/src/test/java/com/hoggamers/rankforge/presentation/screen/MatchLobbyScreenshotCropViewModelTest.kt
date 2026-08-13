package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudFailure
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudResult
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotStorageUploadResult
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotStorageUploader
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MatchLobbyScreenshotCropViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var tournamentRepository: InMemoryTournamentRepository
    private lateinit var assetRepository: FakeLobbyRepository
    private val tournamentId = "lobby-crop-tournament"
    private val matchId = "lobby-crop-match"

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(dispatcher)
        tournamentRepository = InMemoryTournamentRepository()
        tournamentRepository.create(
            Tournament(
                id = tournamentId,
                name = "Crop Cup",
                date = LocalDate.of(2026, 8, 13),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        tournamentRepository.saveTeamNames(tournamentId, mapOf(1 to "Team 1"))
        tournamentRepository.createDraftMatch(
            Match(matchId, tournamentId, 1, LocalDate.of(2026, 8, 13), "Bermuda", MatchStatus.DRAFT),
        )
        assetRepository = FakeLobbyRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun validCropPersistsLobbyProfileAndCallsConfirmedOnce() = runTest {
        val root = Files.createTempDirectory("lobby-crop-valid").toFile()
        val preserver = preserver(root)
        val file = preserver.lobbyPreservedFile(tournamentId, matchId, 1, "png")
        file.parentFile.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        assetRepository.saveOrReplace(asset(preserver.relativePathFor(file)!!))
        val uploader = RecordingLobbyStorageUploader(
            MatchLobbyScreenshotStorageUploadResult.Uploaded("cloud/lobby/1/original.png"),
        )
        val viewModel = viewModel(preserver, storageUploader = uploader)
        viewModel.load(tournamentId, matchId, 1)
        advanceUntilIdle()
        val crop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9)
        viewModel.onCropChanged(crop)
        var confirmations = 0
        viewModel.confirmCrop { confirmations++ }
        advanceUntilIdle()

        assertEquals(1, confirmations)
        assertEquals(crop, assetRepository.getByIdentity(MatchLobbyScreenshotIdentity(tournamentId, matchId, 1))?.let {
            OcrNormalizedCropRect(it.cropLeft!!, it.cropTop!!, it.cropRight!!, it.cropBottom!!)
        })
        assertEquals("lobby", assetRepository.getByIdentity(MatchLobbyScreenshotIdentity(tournamentId, matchId, 1))?.cropProfileId)
        assertEquals(1, uploader.calls.size)
        assertEquals("cloud/lobby/1/original.png", assetRepository.getByIdentity(MatchLobbyScreenshotIdentity(tournamentId, matchId, 1))?.storageObjectPath)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun successfulCropUpsertsLatestCloudMetadataAndPreservesStorageFields() = runTest {
        val root = Files.createTempDirectory("lobby-crop-cloud").toFile()
        val preserver = preserver(root)
        val file = preserver.lobbyPreservedFile(tournamentId, matchId, 1, "png")
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        assetRepository.saveOrReplace(
            asset(preserver.relativePathFor(file)!!).copy(
                storageBucket = "ocr-screenshots",
                storageObjectPath = "cloud/path.png",
                uploadStatus = com.hoggamers.rankforge.data.local.ScreenshotUploadStatus.UPLOADED.name,
            ),
        )
        val cloud = FakeCloudDataSource()
        val viewModel = viewModel(preserver, cloud, RecordingLobbyStorageUploader())
        viewModel.load(tournamentId, matchId, 1)
        advanceUntilIdle()
        viewModel.onCropChanged(OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9))
        var confirmations = 0
        viewModel.confirmCrop { confirmations++ }
        advanceUntilIdle()

        assertEquals(1, confirmations)
        assertEquals(1, cloud.upserts.size)
        assertEquals("cloud/path.png", cloud.upserts.single().storageObjectPath)
        assertEquals("lobby", cloud.upserts.single().cropProfileId)
    }

    @Test
    fun cloudFailureDoesNotUndoLocalCropOrConfirmedCallback() = runTest {
        val root = Files.createTempDirectory("lobby-crop-cloud-failure").toFile()
        val preserver = preserver(root)
        val file = preserver.lobbyPreservedFile(tournamentId, matchId, 1, "png")
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        assetRepository.saveOrReplace(asset(preserver.relativePathFor(file)!!))
        val cloud = FakeCloudDataSource(MatchLobbyScreenshotAssetCloudResult.Failed(MatchLobbyScreenshotAssetCloudFailure.NETWORK))
        val uploader = RecordingLobbyStorageUploader(
            MatchLobbyScreenshotStorageUploadResult.Uploaded("cloud/lobby/1/original.png"),
        )
        val viewModel = viewModel(preserver, cloud, uploader)
        viewModel.load(tournamentId, matchId, 1)
        advanceUntilIdle()
        val crop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9)
        viewModel.onCropChanged(crop)
        var confirmations = 0
        viewModel.confirmCrop { confirmations++ }
        advanceUntilIdle()

        assertEquals(1, confirmations)
        val saved = assetRepository.getByIdentity(MatchLobbyScreenshotIdentity(tournamentId, matchId, 1))
        assertEquals(crop.left, saved?.cropLeft)
        assertEquals(com.hoggamers.rankforge.data.local.ScreenshotUploadStatus.FAILED.name, saved?.uploadStatus)
        assertEquals(MatchLobbyScreenshotAssetCloudFailure.NETWORK.name, saved?.uploadFailureCode)
        assertEquals("cloud/lobby/1/original.png", saved?.storageObjectPath)
        assertEquals(1, uploader.calls.size)
    }

    @Test
    fun cloudFailureMarksNewestAssetWithoutLosingConcurrentStorageState() = runTest {
        val root = Files.createTempDirectory("lobby-crop-cloud-race").toFile()
        val preserver = preserver(root)
        val file = preserver.lobbyPreservedFile(tournamentId, matchId, 1, "png")
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        assetRepository.saveOrReplace(asset(preserver.relativePathFor(file)!!))
        val cloudStarted = CompletableDeferred<Unit>()
        val cloudResult = CompletableDeferred<MatchLobbyScreenshotAssetCloudResult>()
        val cloud = SuspendingCloudDataSource(cloudStarted, cloudResult)
        val uploader = RecordingLobbyStorageUploader(
            MatchLobbyScreenshotStorageUploadResult.Uploaded("cloud/lobby/1/original.png"),
        )
        val viewModel = viewModel(preserver, cloud, uploader)
        viewModel.load(tournamentId, matchId, 1)
        advanceUntilIdle()
        viewModel.onCropChanged(OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9))
        var confirmations = 0
        viewModel.confirmCrop { confirmations++ }
        advanceUntilIdle()

        assertTrue(cloudStarted.isCompleted)
        assertEquals(0, confirmations)
        val afterCrop = assetRepository.getByIdentity(MatchLobbyScreenshotIdentity(tournamentId, matchId, 1))!!
        val uploaded = afterCrop.copy(
            uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
            storageBucket = "ocr-screenshots",
            storageObjectPath = "cloud/path.png",
            uploadedAt = 99,
            revision = afterCrop.revision + 4L,
        )
        assetRepository.saveOrReplace(uploaded)
        cloudResult.complete(
            MatchLobbyScreenshotAssetCloudResult.Failed(MatchLobbyScreenshotAssetCloudFailure.NETWORK),
        )
        advanceUntilIdle()

        val final = assetRepository.getByIdentity(MatchLobbyScreenshotIdentity(tournamentId, matchId, 1))!!
        assertEquals(1, confirmations)
        assertEquals(ScreenshotUploadStatus.FAILED.name, final.uploadStatus)
        assertEquals(MatchLobbyScreenshotAssetCloudFailure.NETWORK.name, final.uploadFailureCode)
        assertEquals("ocr-screenshots", final.storageBucket)
        assertEquals("cloud/path.png", final.storageObjectPath)
        assertEquals(99L, final.uploadedAt)
        assertEquals(uploaded.revision + 1L, final.revision)
        assertEquals(0.1, final.cropLeft)
        assertEquals(0.9, final.cropRight)
    }

    @Test
    fun noAssetAndFinalizedMatchAreControlled() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-crop-missing").toFile())
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId, 2)
        advanceUntilIdle()
        assertEquals(MatchLobbyScreenshotCropError.MISSING_ASSET, viewModel.uiState.value.error)

        tournamentRepository.createDraftMatch(
            Match("finalized-match", tournamentId, 2, LocalDate.of(2026, 8, 13), "Bermuda", MatchStatus.FINALIZED),
        )
        val finalizedFile = preserver.lobbyPreservedFile(tournamentId, "finalized-match", 1, "png")
        finalizedFile.parentFile.mkdirs()
        finalizedFile.writeBytes(byteArrayOf(1))
        assetRepository.saveOrReplace(asset(preserver.relativePathFor(finalizedFile)!!, "finalized-match"))
        val finalizedViewModel = viewModel(preserver)
        finalizedViewModel.load(tournamentId, "finalized-match", 1)
        advanceUntilIdle()
        finalizedViewModel.confirmCrop {}
        advanceUntilIdle()
        assertEquals(MatchLobbyScreenshotCropError.FINALIZED_MATCH, finalizedViewModel.uiState.value.error)
    }

    private fun viewModel(
        preserver: LocalImagePreserver,
        cloud: MatchLobbyScreenshotAssetCloudDataSource = FakeCloudDataSource(),
        storageUploader: MatchLobbyScreenshotStorageUploader = RecordingLobbyStorageUploader(),
    ) = MatchLobbyScreenshotCropViewModel(
        observeMatches = ObserveMatchesUseCase(tournamentRepository),
        assetRepository = assetRepository,
        localImagePreserver = preserver,
        clock = java.time.Clock.systemUTC(),
        uploadCheckpoint = MatchLobbyScreenshotUploadCheckpoint(
            assetRepository = assetRepository,
            localImagePreserver = preserver,
            clock = java.time.Clock.systemUTC(),
            storageUploader = storageUploader,
            cloudDataSource = cloud,
        ),
    )

    private fun preserver(root: java.io.File) = LocalImagePreserver(
        appPrivateRoot = root,
        sourceStreamOpener = ImageSourceStreamOpener { byteArrayOf(1, 2, 3).inputStream() },
        mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun asset(path: String, assetMatchId: String = matchId) = MatchLobbyScreenshotAssetEntity(
        tournamentId = tournamentId,
        matchId = assetMatchId,
        lobbyScreenshotIndex = 1,
        ownerUserId = "owner-1",
        localRelativePath = path,
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 100,
        originalHeight = 100,
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
        createdAt = 1,
        updatedAt = 1,
        preservedAt = 1,
        uploadedAt = null,
        revision = 1,
    )

    private class FakeLobbyRepository : MatchLobbyScreenshotAssetRepository {
        private val state = MutableStateFlow<List<MatchLobbyScreenshotAssetEntity>>(emptyList())
        override fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> =
            state.asStateFlow().let { flow -> kotlinx.coroutines.flow.flow { flow.collect { emit(it.filter { asset -> asset.matchId == matchId }) } } }
        override fun observeByIdentity(identity: MatchLobbyScreenshotIdentity): Flow<MatchLobbyScreenshotAssetEntity?> =
            state.asStateFlow().let { flow -> kotlinx.coroutines.flow.flow { flow.collect { emit(it.firstOrNull { asset -> asset.matchId == identity.matchId && asset.lobbyScreenshotIndex == identity.lobbyScreenshotIndex }) } } }
        override suspend fun getByIdentity(identity: MatchLobbyScreenshotIdentity) = state.value.firstOrNull { it.matchId == identity.matchId && it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex }
        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = state.asStateFlow()
        override suspend fun findDuplicateFingerprint(identity: MatchLobbyScreenshotIdentity, sha256: String) = null
        override suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetSaveResult { state.value = state.value.filterNot { it.matchId == asset.matchId && it.lobbyScreenshotIndex == asset.lobbyScreenshotIndex } + asset; return MatchLobbyScreenshotAssetSaveResult.Saved }
        override suspend fun markLocalMissing(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markCleanupFailure(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) = Unit
        override suspend fun deleteByMatchId(matchId: String) = Unit
        override suspend fun persistConfirmedCrop(identity: MatchLobbyScreenshotIdentity, crop: OcrNormalizedCropRect, updatedAt: Long): MatchLobbyScreenshotCropSaveResult {
            val current = getByIdentity(identity) ?: return MatchLobbyScreenshotCropSaveResult.MissingAsset
            state.value = state.value.map { if (it == current) it.copy(cropProfileId = "lobby", cropLeft = crop.left, cropTop = crop.top, cropRight = crop.right, cropBottom = crop.bottom) else it }
            return MatchLobbyScreenshotCropSaveResult.Saved
        }
        override suspend fun clearConfirmedCrop(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = MatchLobbyScreenshotCropSaveResult.Saved
    }

    private class FakeCloudDataSource(
        private val result: MatchLobbyScreenshotAssetCloudResult = MatchLobbyScreenshotAssetCloudResult.Success,
    ) : MatchLobbyScreenshotAssetCloudDataSource {
        val upserts = mutableListOf<MatchLobbyScreenshotAssetEntity>()

        override suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetCloudResult {
            upserts += asset
            return result
        }

        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) =
            MatchLobbyScreenshotAssetCloudResult.Success
    }

    private class SuspendingCloudDataSource(
        private val started: CompletableDeferred<Unit>,
        private val result: CompletableDeferred<MatchLobbyScreenshotAssetCloudResult>,
    ) : MatchLobbyScreenshotAssetCloudDataSource {
        override suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetCloudResult {
            started.complete(Unit)
            return result.await()
        }

        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) =
            MatchLobbyScreenshotAssetCloudResult.Success
    }

    private class RecordingLobbyStorageUploader(
        private val result: MatchLobbyScreenshotStorageUploadResult =
            MatchLobbyScreenshotStorageUploadResult.Uploaded("cloud/default.png"),
    ) : MatchLobbyScreenshotStorageUploader {
        val calls = mutableListOf<Triple<String?, String?, Int?>>()

        override suspend fun upload(
            tournamentId: String?,
            matchId: String?,
            lobbyScreenshotIndex: Int?,
            localFile: java.io.File?,
        ): MatchLobbyScreenshotStorageUploadResult {
            calls += Triple(tournamentId, matchId, lobbyScreenshotIndex)
            assertTrue(localFile?.isFile == true)
            return result
        }
    }
}
