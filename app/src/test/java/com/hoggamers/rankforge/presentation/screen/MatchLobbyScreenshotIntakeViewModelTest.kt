package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudResult
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotStorageUploadFailure
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
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MatchLobbyScreenshotIntakeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var tournamentRepository: InMemoryTournamentRepository
    private lateinit var lobbyRepository: FakeLobbyRepository
    private val tournamentId = "lobby-intake-tournament"
    private val matchId = "lobby-intake-match"

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(dispatcher)
        tournamentRepository = InMemoryTournamentRepository()
        tournamentRepository.create(
            Tournament(
                id = tournamentId,
                name = "Lobby Cup",
                date = LocalDate.of(2026, 8, 13),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        tournamentRepository.saveTeamNames(tournamentId, mapOf(1 to "Team 1"))
        tournamentRepository.createDraftMatch(
            Match(
                id = matchId,
                tournamentId = tournamentId,
                matchNumber = 1,
                date = LocalDate.of(2026, 8, 13),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        lobbyRepository = FakeLobbyRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadRestoresOnlyTheExactMatchAcrossThreeLobbySlots() = runTest {
        val root = Files.createTempDirectory("lobby-intake-restore").toFile()
        val preserver = preserver(root)
        val firstFile = preserver.lobbyPreservedFile(tournamentId, matchId, 1, "png")
        firstFile.parentFile.mkdirs()
        firstFile.writeBytes(byteArrayOf(1, 2, 3))
        lobbyRepository.saveOrReplace(asset(1, matchId, preserver.relativePathFor(firstFile)!!, "one"))
        lobbyRepository.saveOrReplace(asset(2, matchId, "missing/path.png", "two"))
        lobbyRepository.saveOrReplace(asset(3, "other-match", "other/path.png", "three"))

        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isAvailable)
        assertEquals(3, state.slots.size)
        assertTrue(state.slot(1)?.selectedScreenshotUri != null)
        assertTrue(state.slot(2)?.isLocalFileMissing == true)
        assertFalse(state.slot(3)?.fingerprint == "three")
    }

    @Test
    fun validSelectionPersistsAndRequestsExactCropIndex() = runTest {
        val root = Files.createTempDirectory("lobby-intake-select").toFile()
        val preserver = preserver(root)
        val validator = ImageCandidateValidator(
            ImageCandidateMetadataReader {
                ImageCandidateReadResult.Metadata("image/png", 100, 100)
            },
        )
        val viewModel = viewModel(preserver, validator)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(2)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()

        assertTrue(lobbyRepository.readByMatchAndIndex(matchId, 2) != null)
        assertEquals(2, viewModel.uiState.value.pendingCropNavigationSlotIndex)
        assertTrue(viewModel.uiState.value.slot(2)?.hasLinkedAsset == true)
    }

    @Test
    fun localSaveRequestsCropNavigationWithoutWaitingForCloud() = runTest {
        val gate = CompletableDeferred<Unit>()
        val uploader = FakeStorageUploader { _, _, _, _ ->
            gate.await()
            MatchLobbyScreenshotStorageUploadResult.Uploaded("cloud/lobby/2/original.png")
        }
        val preserver = preserver(Files.createTempDirectory("lobby-intake-local-first").toFile())
        val viewModel = viewModel(preserver, storageUploader = uploader)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(2)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.pendingCropNavigationSlotIndex)
        assertTrue(uploader.calls.isNotEmpty())
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun cancellingPhotoPickerDoesNotCancelExistingUploadOrChangeAsset() = runTest {
        val gate = CompletableDeferred<Unit>()
        var uploadCancelled = false
        val uploader = FakeStorageUploader { _, _, _, _ ->
            try {
                gate.await()
            } catch (cancellation: CancellationException) {
                uploadCancelled = true
                throw cancellation
            }
            MatchLobbyScreenshotStorageUploadResult.Uploaded("cloud/lobby/1/original.png")
        }
        val preserver = preserver(Files.createTempDirectory("lobby-intake-picker-cancel").toFile())
        val viewModel = viewModel(preserver, storageUploader = uploader)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()
        val beforeCancel = lobbyRepository.readByMatchAndIndex(matchId, 1)

        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult(null)
        advanceUntilIdle()

        assertFalse(uploadCancelled)
        assertEquals(beforeCancel, lobbyRepository.readByMatchAndIndex(matchId, 1))
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun storageSuccessUpdatesLatestLocalAssetAndCloudMetadata() = runTest {
        val uploader = FakeStorageUploader { _, _, _, _ ->
            MatchLobbyScreenshotStorageUploadResult.Uploaded("users/owner/lobby/1/original.png")
        }
        val cloud = FakeCloudDataSource()
        val preserver = preserver(Files.createTempDirectory("lobby-intake-uploaded").toFile())
        val viewModel = viewModel(preserver, storageUploader = uploader, cloudDataSource = cloud)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()

        val saved = lobbyRepository.readByMatchAndIndex(matchId, 1)
        assertEquals(ScreenshotUploadStatus.UPLOADED.name, saved?.uploadStatus)
        assertEquals("users/owner/lobby/1/original.png", saved?.storageObjectPath)
        assertEquals(1, cloud.upserts.size)
        assertEquals(saved, cloud.upserts.single())
    }

    @Test
    fun storageFailureLeavesLocalAssetLinkedAndMarksFailure() = runTest {
        val uploader = FakeStorageUploader { _, _, _, _ ->
            MatchLobbyScreenshotStorageUploadResult.Failed(MatchLobbyScreenshotStorageUploadFailure.NETWORK)
        }
        val preserver = preserver(Files.createTempDirectory("lobby-intake-failed").toFile())
        val viewModel = viewModel(preserver, storageUploader = uploader)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()

        val saved = lobbyRepository.readByMatchAndIndex(matchId, 1)
        assertTrue(viewModel.uiState.value.slot(1)?.hasLinkedAsset == true)
        assertEquals(ScreenshotUploadStatus.FAILED.name, saved?.uploadStatus)
        assertEquals(MatchLobbyScreenshotStorageUploadFailure.NETWORK.name, saved?.uploadFailureCode)
    }

    @Test
    fun staleUploadCannotMutateReplacementAsset() = runTest {
        val gate = CompletableDeferred<Unit>()
        val uploader = FakeStorageUploader { _, _, _, _ ->
            gate.await()
            MatchLobbyScreenshotStorageUploadResult.Uploaded("stale/path.png")
        }
        val preserver = preserver(Files.createTempDirectory("lobby-intake-stale").toFile())
        val viewModel = viewModel(preserver, storageUploader = uploader)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()
        lobbyRepository.saveOrReplace(asset(1, matchId, "replacement/path.png", "replacement"))
        gate.complete(Unit)
        advanceUntilIdle()

        val replacement = lobbyRepository.readByMatchAndIndex(matchId, 1)
        assertEquals("replacement", replacement?.sha256)
        assertEquals(ScreenshotUploadStatus.PENDING.name, replacement?.uploadStatus)
        assertEquals(null, replacement?.storageObjectPath)
    }

    @Test
    fun sameIdentityRecoveryRetainsSuccessfulCloudState() = runTest {
        val root = Files.createTempDirectory("lobby-intake-cloud-recovery").toFile()
        val preserver = preserver(root)
        val fingerprint = byteArrayOf(1, 2, 3).sha256()
        lobbyRepository.saveOrReplace(
            asset(1, matchId, "missing/path.png", fingerprint).copy(
                storageBucket = "ocr-screenshots",
                storageObjectPath = "cloud/path.png",
                uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
                uploadedAt = 4,
                cropProfileId = "lobby",
                cropLeft = 0.1,
                cropTop = 0.1,
                cropRight = 0.9,
                cropBottom = 0.9,
            ),
        )
        val uploader = FakeStorageUploader { _, _, _, _ -> error("upload must not run") }
        val cloud = FakeCloudDataSource()
        val viewModel = viewModel(preserver, storageUploader = uploader, cloudDataSource = cloud)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()

        val restored = lobbyRepository.readByMatchAndIndex(matchId, 1)
        assertEquals(ScreenshotUploadStatus.UPLOADED.name, restored?.uploadStatus)
        assertEquals("cloud/path.png", restored?.storageObjectPath)
        assertEquals("lobby", restored?.cropProfileId)
        assertEquals(2L, restored?.revision)
        assertEquals(ScreenshotLocalStatus.PRESERVED.name, restored?.localStatus)
        assertEquals(1, cloud.upserts.size)
        assertEquals(restored, cloud.upserts.single())
        assertEquals("ocr-screenshots", cloud.upserts.single().storageBucket)
        assertEquals("cloud/path.png", cloud.upserts.single().storageObjectPath)
        assertEquals(4L, cloud.upserts.single().uploadedAt)
        assertEquals(0.1, cloud.upserts.single().cropLeft)
        assertTrue(viewModel.uiState.value.pendingCropNavigationSlotIndex == 1)
        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun sameIdentityWithMissingLocalFileRepreservesBeforeCropNavigation() = runTest {
        val root = Files.createTempDirectory("lobby-intake-recover").toFile()
        val preserver = preserver(root)
        val fingerprint = byteArrayOf(1, 2, 3).sha256()
        val existingAsset = asset(1, matchId, "missing/lobby.png", fingerprint).copy(
            cropProfileId = "lobby",
            cropLeft = 0.1,
            cropTop = 0.1,
            cropRight = 0.9,
            cropBottom = 0.9,
            createdAt = 41,
            updatedAt = 42,
            preservedAt = 42,
            revision = 7,
        )
        lobbyRepository.saveOrReplace(existingAsset)

        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.slot(1)?.isLocalFileMissing == true)

        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()

        assertTrue(preserver.lobbyPreservedFile(tournamentId, matchId, 1, "png").isFile)
        val restored = lobbyRepository.readByMatchAndIndex(matchId, 1)
        assertTrue(restored != null)
        assertEquals(fingerprint, restored?.sha256)
        assertEquals(41L, restored?.createdAt)
        assertEquals(9L, restored?.revision)
        assertEquals("lobby", restored?.cropProfileId)
        assertTrue(viewModel.uiState.value.slot(1)?.hasLinkedAsset == true)
        assertFalse(viewModel.uiState.value.slot(1)?.isLocalFileMissing == true)
        assertEquals(1, viewModel.uiState.value.pendingCropNavigationSlotIndex)
    }

    @Test
    fun finalizedMatchBlocksPickerAndRemoval() = runTest {
        tournamentRepository.finalizeDraftMatch(
            matchId = matchId,
            placements = (1..12).map { com.hoggamers.rankforge.domain.tournament.MatchPlacement(it, it) },
            kills = (1..12).map { com.hoggamers.rankforge.domain.tournament.MatchKill(it, 0) },
        )
        val viewModel = viewModel(preserver(Files.createTempDirectory("lobby-intake-finalized").toFile()))
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)
        viewModel.removeScreenshot(1)
        assertEquals(MatchLobbyScreenshotIntakeError.FINALIZED_MATCH, viewModel.uiState.value.intakeError)
        assertFalse(viewModel.uiState.value.slot(1)?.isPhotoPickerRequestActive == true)
    }

    private fun viewModel(
        preserver: LocalImagePreserver,
        validator: ImageCandidateValidator = ImageCandidateValidator(ImageCandidateMetadataReader { ImageCandidateReadResult.Metadata("image/png", 100, 100) }),
        storageUploader: MatchLobbyScreenshotStorageUploader = FakeStorageUploader { _, _, _, _ ->
            MatchLobbyScreenshotStorageUploadResult.Uploaded("cloud/default.png")
        },
        cloudDataSource: MatchLobbyScreenshotAssetCloudDataSource = FakeCloudDataSource(),
    ) = MatchLobbyScreenshotIntakeViewModel(
        observeMatches = ObserveMatchesUseCase(tournamentRepository),
        imageCandidateValidator = validator,
        duplicateDetector = MatchLobbyScreenshotDuplicateDetector(
            ImageSourceFingerprintGenerator(
                ImageSourceStreamOpener { "picked".let { byteArrayOf(1, 2, 3).inputStream() } },
                Dispatchers.Unconfined,
            ),
            lobbyRepository,
        ),
        localImagePreserver = preserver,
        assetRepository = lobbyRepository,
        screenshotOwnerProvider = object : ScreenshotOwnerProvider {
            override suspend fun currentOwnerUserId(): String = "owner-1"
        },
        clock = java.time.Clock.systemUTC(),
        storageUploader = storageUploader,
        cloudDataSource = cloudDataSource,
    )

    private fun preserver(root: java.io.File) = LocalImagePreserver(
        appPrivateRoot = root,
        sourceStreamOpener = ImageSourceStreamOpener { byteArrayOf(1, 2, 3).inputStream() },
        mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun asset(index: Int, assetMatchId: String, path: String, sha: String) = MatchLobbyScreenshotAssetEntity(
        tournamentId = tournamentId,
        matchId = assetMatchId,
        lobbyScreenshotIndex = index,
        ownerUserId = "owner-1",
        localRelativePath = path,
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 100,
        originalHeight = 100,
        byteSize = 3,
        sha256 = sha,
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
        override suspend fun findDuplicateFingerprint(identity: MatchLobbyScreenshotIdentity, sha256: String) = state.value.firstOrNull { it.tournamentId == identity.tournamentId && it.sha256 == sha256 && !(it.matchId == identity.matchId && it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex) }
        override suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetSaveResult { state.value = state.value.filterNot { it.matchId == asset.matchId && it.lobbyScreenshotIndex == asset.lobbyScreenshotIndex } + asset; return MatchLobbyScreenshotAssetSaveResult.Saved }
        override suspend fun markLocalMissing(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markCleanupFailure(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) { state.value = state.value.filterNot { it.matchId == identity.matchId && it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex } }
        override suspend fun deleteByMatchId(matchId: String) { state.value = state.value.filterNot { it.matchId == matchId } }
        override suspend fun persistConfirmedCrop(identity: MatchLobbyScreenshotIdentity, crop: OcrNormalizedCropRect, updatedAt: Long) = MatchLobbyScreenshotCropSaveResult.Saved
        override suspend fun clearConfirmedCrop(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = MatchLobbyScreenshotCropSaveResult.Saved
        fun readByMatchAndIndex(matchId: String, index: Int) = state.value.firstOrNull { it.matchId == matchId && it.lobbyScreenshotIndex == index }
    }

    private class FakeStorageUploader(
        private val handler: suspend (String, String, Int, java.io.File?) -> MatchLobbyScreenshotStorageUploadResult,
    ) : MatchLobbyScreenshotStorageUploader {
        val calls = mutableListOf<Int>()

        override suspend fun upload(
            tournamentId: String?,
            matchId: String?,
            lobbyScreenshotIndex: Int?,
            localFile: java.io.File?,
        ): MatchLobbyScreenshotStorageUploadResult {
            calls += lobbyScreenshotIndex ?: -1
            return handler(tournamentId.orEmpty(), matchId.orEmpty(), lobbyScreenshotIndex ?: -1, localFile)
        }
    }

    private class FakeCloudDataSource : MatchLobbyScreenshotAssetCloudDataSource {
        val upserts = mutableListOf<MatchLobbyScreenshotAssetEntity>()

        override suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetCloudResult {
            upserts += asset
            return MatchLobbyScreenshotAssetCloudResult.Success
        }

        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) =
            MatchLobbyScreenshotAssetCloudResult.Success
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
