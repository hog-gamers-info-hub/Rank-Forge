package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchLobbyScreenshotAssetCloudResult
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.data.local.TournamentLobbyTemplateAssetEntity
import com.hoggamers.rankforge.data.local.TournamentLobbyTemplateAssetRepository
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
    private lateinit var templateRepository: FakeTemplateRepository
    private val tournamentId = "lobby-intake-tournament"
    private val matchId = "lobby-intake-match"

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(dispatcher)
        tournamentRepository = InMemoryTournamentRepository()
        templateRepository = FakeTemplateRepository()
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
        tournamentRepository.saveTeamNames(
            tournamentId,
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
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
    fun freshSelectionRemainsPendingAndDoesNotUploadBeforeCropConfirmation() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-intake-local-first").toFile())
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(2)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.pendingCropNavigationSlotIndex)
        assertEquals(
            ScreenshotUploadStatus.PENDING.name,
            lobbyRepository.readByMatchAndIndex(matchId, 2)?.uploadStatus,
        )
        assertTrue(lobbyRepository.readByMatchAndIndex(matchId, 2)?.localRelativePath?.isNotBlank() == true)
    }

    @Test
    fun freshSelectionSupportsEachLobbyIndexWithoutPreCropUpload() = runTest {
        val uris = listOf("picked-one", "picked-two", "picked-three")
        val viewModel = viewModel(
            preserver(Files.createTempDirectory("lobby-intake-all-indices").toFile()),
            bytesByUri = uris.mapIndexed { index, uri -> uri to byteArrayOf((index + 1).toByte()) }.toMap(),
        )
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        uris.forEachIndexed { offset, uri ->
            val index = offset + 1
            viewModel.requestPhotoPicker(index)
            viewModel.onPhotoPickerResult(uri)
            advanceUntilIdle()

            assertEquals(index, viewModel.uiState.value.pendingCropNavigationSlotIndex)
            assertEquals(ScreenshotUploadStatus.PENDING.name, lobbyRepository.readByMatchAndIndex(matchId, index)?.uploadStatus)
            viewModel.onCropNavigationHandled()
        }

        assertEquals(uris.indices.map { it + 1 }, lobbyRepository.snapshot().map { it.lobbyScreenshotIndex }.sorted())
    }

    @Test
    fun cancellingPhotoPickerDoesNotChangeExistingAsset() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-intake-picker-cancel").toFile())
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()
        val beforeCancel = lobbyRepository.readByMatchAndIndex(matchId, 1)

        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult(null)
        advanceUntilIdle()

        assertEquals(beforeCancel, lobbyRepository.readByMatchAndIndex(matchId, 1))
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
        val cloudStarted = CompletableDeferred<Unit>()
        val cloudResult = CompletableDeferred<MatchLobbyScreenshotAssetCloudResult>()
        val cloud = SuspendingCloudDataSource(cloudStarted, cloudResult)
        val viewModel = viewModel(preserver, cloudDataSource = cloud)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(1)
        viewModel.onPhotoPickerResult("picked")
        advanceUntilIdle()

        assertTrue(preserver.lobbyPreservedFile(tournamentId, matchId, 1, "png").isFile)
        assertTrue(cloudStarted.isCompleted)
        assertFalse(cloudResult.isCompleted)
        assertEquals(1, viewModel.uiState.value.pendingCropNavigationSlotIndex)
        cloudResult.complete(MatchLobbyScreenshotAssetCloudResult.Success)
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
        assertEquals(8L, restored?.revision)
        assertEquals(ScreenshotUploadStatus.PENDING.name, restored?.uploadStatus)
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

    @Test
    fun noTemplateRestoresOffStateAfterLoad() = runTest {
        val viewModel = viewModel(preserver(Files.createTempDirectory("lobby-toggle-off").toFile()))

        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLobbySavedForNextMatches)
    }

    @Test
    fun completeTemplateBeforeLoadRestoresOnState() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-toggle-restore").toFile())
        seedActiveTemplate(preserver)
        val viewModel = viewModel(preserver)

        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLobbySavedForNextMatches)
    }

    @Test
    fun saveTransitionsToOnOnlyAfterTemplateRepositoryEmits() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-toggle-save").toFile())
        seedReadyCurrentAssets(preserver)
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canSaveLobbyForNextMatches)
        viewModel.saveLobbyForNextMatches()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLobbySavedForNextMatches)
        assertEquals(MatchLobbyTemplateSaveStatus.SAVED, viewModel.uiState.value.lobbyTemplateSaveStatus)
    }

    @Test
    fun unsaveRemainsAllowedWhenCurrentLobbyIsIncomplete() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-toggle-unsave-incomplete").toFile())
        seedActiveTemplate(preserver)
        lobbyRepository.deleteByIdentity(MatchLobbyScreenshotIdentity(tournamentId, matchId, 2))
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLobbySavedForNextMatches)
        assertTrue(viewModel.uiState.value.canUnsaveLobbyForNextMatches)
        viewModel.unsaveLobbyForNextMatches()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLobbySavedForNextMatches)
    }

    @Test
    fun finalizedMatchWithTemplateOnCanUnsaveButFinalizedOffCannotSave() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-toggle-finalized").toFile())
        seedActiveTemplate(preserver)
        tournamentRepository.finalizeDraftMatch(
            matchId = matchId,
            placements = (1..12).map { com.hoggamers.rankforge.domain.tournament.MatchPlacement(it, it) },
            kills = (1..12).map { com.hoggamers.rankforge.domain.tournament.MatchKill(it, 0) },
        )
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canUnsaveLobbyForNextMatches)
        viewModel.unsaveLobbyForNextMatches()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLobbySavedForNextMatches)
        assertFalse(viewModel.uiState.value.canSaveLobbyForNextMatches)
    }

    @Test
    fun unsaveFailureKeepsObservedStateOnAndReportsFailure() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-toggle-failure").toFile())
        seedActiveTemplate(preserver)
        templateRepository.throwOnDelete = true
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.unsaveLobbyForNextMatches()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLobbySavedForNextMatches)
        assertEquals(MatchLobbyTemplateSaveStatus.FAILED, viewModel.uiState.value.lobbyTemplateSaveStatus)
    }

    @Test
    fun currentLobbyChangesDoNotChangeSavedTemplateState() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-toggle-snapshot").toFile())
        seedActiveTemplate(preserver)
        val savedBefore = templateRepository.getByTournamentId(tournamentId)
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.removeScreenshot(1)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLobbySavedForNextMatches)
        assertEquals(savedBefore, templateRepository.getByTournamentId(tournamentId))
    }

    @Test
    fun repeatedUnsaveIsIgnoredWhileMutationIsInProgress() = runTest {
        val preserver = preserver(Files.createTempDirectory("lobby-toggle-concurrency").toFile())
        seedActiveTemplate(preserver)
        val gate = CompletableDeferred<Unit>()
        templateRepository.deleteGate = gate
        val viewModel = viewModel(preserver)
        viewModel.load(tournamentId, matchId)
        advanceUntilIdle()

        viewModel.unsaveLobbyForNextMatches()
        advanceUntilIdle()
        viewModel.unsaveLobbyForNextMatches()
        assertEquals(1, templateRepository.deleteCalls)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    private suspend fun seedReadyCurrentAssets(preserver: LocalImagePreserver) {
        (1..3).forEach { index ->
            val file = preserver.lobbyPreservedFile(tournamentId, matchId, index, "png")
            file.parentFile?.mkdirs()
            file.writeBytes(byteArrayOf(index.toByte(), 1, 2))
            lobbyRepository.saveOrReplace(
                asset(index, matchId, preserver.relativePathFor(file)!!, "ready-$index").copy(
                    cropProfileId = "lobby",
                    cropLeft = 0.1,
                    cropTop = 0.1,
                    cropRight = 0.9,
                    cropBottom = 0.9,
                ),
            )
        }
    }

    private suspend fun seedActiveTemplate(preserver: LocalImagePreserver) {
        seedReadyCurrentAssets(preserver)
        assertEquals(
            SaveLobbyTemplateResult.Saved,
            SaveLobbyTemplateUseCase(
                assetRepository = lobbyRepository,
                templateRepository = templateRepository,
                localImagePreserver = preserver,
                clock = java.time.Clock.systemUTC(),
            )(tournamentId, matchId),
        )
    }

    private fun viewModel(
        preserver: LocalImagePreserver,
        validator: ImageCandidateValidator = ImageCandidateValidator(ImageCandidateMetadataReader { ImageCandidateReadResult.Metadata("image/png", 100, 100) }),
        cloudDataSource: MatchLobbyScreenshotAssetCloudDataSource = FakeCloudDataSource(),
        bytesByUri: Map<String, ByteArray> = mapOf("picked" to byteArrayOf(1, 2, 3)),
    ) = MatchLobbyScreenshotIntakeViewModel(
        observeMatches = ObserveMatchesUseCase(tournamentRepository),
        imageCandidateValidator = validator,
        duplicateDetector = MatchLobbyScreenshotDuplicateDetector(
            ImageSourceFingerprintGenerator(
                ImageSourceStreamOpener { uri -> bytesByUri.getValue(uri).inputStream() },
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
        saveLobbyTemplate = SaveLobbyTemplateUseCase(
            assetRepository = lobbyRepository,
            templateRepository = templateRepository,
            localImagePreserver = preserver,
            clock = java.time.Clock.systemUTC(),
        ),
        unsaveLobbyTemplate = UnsaveLobbyTemplateUseCase(
            templateRepository = templateRepository,
            localImagePreserver = preserver,
        ),
        templateRepository = templateRepository,
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
        fun snapshot() = state.value
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

    private class FakeTemplateRepository : TournamentLobbyTemplateAssetRepository {
        private val state = MutableStateFlow<List<TournamentLobbyTemplateAssetEntity>>(emptyList())
        var throwOnDelete = false
        var deleteGate: CompletableDeferred<Unit>? = null
        var deleteCalls = 0

        override fun observeByTournamentId(tournamentId: String): Flow<List<TournamentLobbyTemplateAssetEntity>> =
            state.asStateFlow().let { flow ->
                kotlinx.coroutines.flow.flow {
                    flow.collect { templates ->
                        emit(templates.filter { it.tournamentId == tournamentId })
                    }
                }
            }

        override suspend fun getByTournamentId(tournamentId: String): List<TournamentLobbyTemplateAssetEntity> =
            state.value.filter { it.tournamentId == tournamentId }

        override suspend fun replaceForTournament(
            tournamentId: String,
            assets: List<TournamentLobbyTemplateAssetEntity>,
        ) {
            state.value = state.value.filterNot { it.tournamentId == tournamentId } + assets
        }

        override suspend fun deleteByTournamentId(tournamentId: String) {
            deleteCalls += 1
            deleteGate?.await()
            if (throwOnDelete) error("delete failed")
            state.value = state.value.filterNot { it.tournamentId == tournamentId }
        }

        fun set(templates: List<TournamentLobbyTemplateAssetEntity>) {
            state.value = templates
        }
    }

    private class SuspendingCloudDataSource(
        private val started: CompletableDeferred<Unit>,
        private val result: CompletableDeferred<MatchLobbyScreenshotAssetCloudResult>,
    ) : MatchLobbyScreenshotAssetCloudDataSource {
        val upserts = mutableListOf<MatchLobbyScreenshotAssetEntity>()

        override suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetCloudResult {
            upserts += asset
            started.complete(Unit)
            return result.await()
        }

        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) =
            MatchLobbyScreenshotAssetCloudResult.Success
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
