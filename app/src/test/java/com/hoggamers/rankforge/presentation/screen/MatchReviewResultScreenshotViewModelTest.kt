package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudResult
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotStorageUploadFailure
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
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
import com.hoggamers.rankforge.domain.tournament.CreateMatchInput
import com.hoggamers.rankforge.domain.tournament.CreateMatchResult
import com.hoggamers.rankforge.domain.tournament.CreateMatchUseCase
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

private const val RESULT_TOURNAMENT_ID = "22222222-2222-2222-2222-222222222222"

@OptIn(ExperimentalCoroutinesApi::class)
class MatchReviewResultScreenshotViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var tournamentRepository: InMemoryTournamentRepository
    private lateinit var matchId: String

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(dispatcher)
        tournamentRepository = InMemoryTournamentRepository()
        tournamentRepository.create(
            Tournament(
                id = RESULT_TOURNAMENT_ID,
                name = "Result Cup",
                date = LocalDate.of(2026, 8, 7),
                organizerName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        tournamentRepository.saveTeamNames(RESULT_TOURNAMENT_ID, mapOf(1 to "Team 1"))
        matchId = "result-match-id"
        tournamentRepository.createDraftMatch(
            Match(
                id = matchId,
                tournamentId = RESULT_TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 8, 7),
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
    fun upperPickerResultSavesUpperOnlyAndLeavesUploadPendingBeforeCrop() = runTest {
        val upperUri = "content://picker/upper"
        val bytesByUri = mapOf(upperUri to byteArrayOf(1, 2, 3))
        val assetRepository = FakeMatchResultScreenshotAssetRepository()
        val uploader = RecordingMatchResultScreenshotStorageUploader()
        val viewModel = viewModel(
            bytesByUri = bytesByUri,
            assetRepository = assetRepository,
            uploader = uploader,
        )

        viewModel.load(RESULT_TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_UPPER, upperUri)
        advanceUntilIdle()

        val upper = viewModel.uiState.value.resultScreenshots.slot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        val lower = viewModel.uiState.value.resultScreenshots.slot(MatchResultScreenshotRole.MATCH_RESULT_LOWER)
        assertTrue(upper.hasLinkedAsset)
        assertFalse(lower.hasLinkedAsset)
        assertNotNull(assetRepository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER)))
        assertNull(assetRepository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_LOWER)))
        assertTrue(uploader.calls.isEmpty())
        assertEquals(
            ScreenshotUploadStatus.PENDING.name,
            assetRepository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER))?.uploadStatus,
        )
        assertEquals(
            MatchReviewNavigation.RESULT_SCREENSHOT_1_CROP,
            viewModel.uiState.value.navigation,
        )
    }

    @Test
    fun lowerPickerResultDoesNotMutateExistingUpper() = runTest {
        val upperUri = "content://picker/upper"
        val lowerUri = "content://picker/lower"
        val bytesByUri = mapOf(
            upperUri to byteArrayOf(1),
            lowerUri to byteArrayOf(2),
        )
        val assetRepository = FakeMatchResultScreenshotAssetRepository()
        val viewModel = viewModel(bytesByUri = bytesByUri, assetRepository = assetRepository)

        viewModel.load(RESULT_TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_UPPER, upperUri)
        advanceUntilIdle()
        val upperFingerprint = viewModel.uiState.value.resultScreenshots
            .slot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
            .fingerprint

        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_LOWER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_LOWER, lowerUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(upperFingerprint, state.resultScreenshots.slot(MatchResultScreenshotRole.MATCH_RESULT_UPPER).fingerprint)
        assertTrue(state.resultScreenshots.slot(MatchResultScreenshotRole.MATCH_RESULT_LOWER).hasLinkedAsset)
        assertNotNull(assetRepository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER)))
        assertNotNull(assetRepository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_LOWER)))
        assertEquals(
            MatchReviewNavigation.RESULT_SCREENSHOT_2_CROP,
            viewModel.uiState.value.navigation,
        )
    }

    @Test
    fun replacingUpperClearsUpperCropAndKeepsLowerCrop() = runTest {
        val upperFirstUri = "content://picker/upper-first"
        val upperSecondUri = "content://picker/upper-second"
        val lowerUri = "content://picker/lower"
        val bytesByUri = mapOf(
            upperFirstUri to byteArrayOf(1),
            upperSecondUri to byteArrayOf(9),
            lowerUri to byteArrayOf(2),
        )
        val assetRepository = FakeMatchResultScreenshotAssetRepository()
        val viewModel = viewModel(bytesByUri = bytesByUri, assetRepository = assetRepository)

        viewModel.load(RESULT_TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_UPPER, upperFirstUri)
        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_LOWER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_LOWER, lowerUri)
        advanceUntilIdle()
        assetRepository.persistConfirmedCrop(
            identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
            updatedAt = 20L,
        )
        assetRepository.persistConfirmedCrop(
            identity(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
            OcrNormalizedCropRect(0.2, 0.2, 0.8, 0.8),
            updatedAt = 21L,
        )

        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_UPPER, upperSecondUri)
        advanceUntilIdle()

        val upper = assetRepository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER))!!
        val lower = assetRepository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_LOWER))!!
        assertNull(upper.cropProfileId)
        assertNull(upper.cropLeft)
        assertEquals(0.2, lower.cropLeft!!, 0.0)
        assertEquals(0.8, lower.cropRight!!, 0.0)
    }

    @Test
    fun replacingUpperWithSameBytesPreservesUpperCrop() = runTest {
        val firstUri = "content://picker/upper-same-first"
        val secondUri = "content://picker/upper-same-second"
        val bytes = byteArrayOf(1, 2, 3)
        val assetRepository = FakeMatchResultScreenshotAssetRepository()
        val viewModel = viewModel(
            bytesByUri = mapOf(firstUri to bytes, secondUri to bytes),
            assetRepository = assetRepository,
        )

        viewModel.load(RESULT_TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_UPPER, firstUri)
        advanceUntilIdle()
        val crop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9)
        assetRepository.persistConfirmedCrop(
            identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            crop,
            updatedAt = 20L,
        )

        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_UPPER, secondUri)
        advanceUntilIdle()

        val upper = assetRepository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER))!!
        assertEquals(crop.left, upper.cropLeft!!, 0.0)
        assertEquals(crop.right, upper.cropRight!!, 0.0)
    }


    @Test
    fun authorizationUploadIsDeferredUntilCropConfirmation() = runTest {
        val upperUri = "content://picker/upper-auth-denied"
        val assetRepository = FakeMatchResultScreenshotAssetRepository()
        val uploader = RecordingMatchResultScreenshotStorageUploader(
            result = MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.AUTHORIZATION,
            ),
        )
        val viewModel = viewModel(
            bytesByUri = mapOf(upperUri to byteArrayOf(7, 8, 9)),
            assetRepository = assetRepository,
            uploader = uploader,
        )

        viewModel.load(RESULT_TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_UPPER, upperUri)
        advanceUntilIdle()

        val upper = viewModel.uiState.value.resultScreenshots
            .slot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        assertTrue(upper.hasLinkedAsset)
        assertNull(upper.uploadError)
        assertTrue(uploader.calls.isEmpty())
        assertEquals(
            MatchReviewNavigation.RESULT_SCREENSHOT_1_CROP,
            viewModel.uiState.value.navigation,
        )
    }

    @Test
    fun handledAutoCropNavigationDoesNotReappearWithoutNewSelection() = runTest {
        val upperUri = "content://picker/upper-once"
        val viewModel = viewModel(
            bytesByUri = mapOf(upperUri to byteArrayOf(4, 5, 6)),
            assetRepository = FakeMatchResultScreenshotAssetRepository(),
        )

        viewModel.load(RESULT_TOURNAMENT_ID, matchId)
        advanceUntilIdle()
        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_UPPER, upperUri)
        advanceUntilIdle()

        assertEquals(
            MatchReviewNavigation.RESULT_SCREENSHOT_1_CROP,
            viewModel.uiState.value.navigation,
        )

        viewModel.onNavigationHandled()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.navigation)
    }

    @Test
    fun removingUpperKeepsLowerUntouched() = runTest {
        val upperUri = "content://picker/remove-upper"
        val lowerUri = "content://picker/keep-lower"
        val assetRepository = FakeMatchResultScreenshotAssetRepository()
        val viewModel = viewModel(
            bytesByUri = mapOf(
                upperUri to byteArrayOf(1, 2, 3),
                lowerUri to byteArrayOf(4, 5, 6),
            ),
            assetRepository = assetRepository,
        )

        viewModel.load(RESULT_TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_UPPER, upperUri)
        advanceUntilIdle()
        viewModel.onNavigationHandled()

        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_LOWER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_LOWER, lowerUri)
        advanceUntilIdle()
        viewModel.onNavigationHandled()

        viewModel.removeResultScreenshot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        advanceUntilIdle()

        val upper = viewModel.uiState.value.resultScreenshots
            .slot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        val lower = viewModel.uiState.value.resultScreenshots
            .slot(MatchResultScreenshotRole.MATCH_RESULT_LOWER)

        assertFalse(upper.hasLinkedAsset)
        assertNull(assetRepository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER)))
        assertTrue(lower.hasLinkedAsset)
        assertNotNull(assetRepository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_LOWER)))
    }

    @Test
    fun removingLowerKeepsUpperUntouched() = runTest {
        val upperUri = "content://picker/keep-upper"
        val lowerUri = "content://picker/remove-lower"
        val assetRepository = FakeMatchResultScreenshotAssetRepository()
        val viewModel = viewModel(
            bytesByUri = mapOf(
                upperUri to byteArrayOf(7, 8, 9),
                lowerUri to byteArrayOf(10, 11, 12),
            ),
            assetRepository = assetRepository,
        )

        viewModel.load(RESULT_TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_UPPER, upperUri)
        advanceUntilIdle()
        viewModel.onNavigationHandled()

        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_LOWER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_LOWER, lowerUri)
        advanceUntilIdle()
        viewModel.onNavigationHandled()

        viewModel.removeResultScreenshot(MatchResultScreenshotRole.MATCH_RESULT_LOWER)
        advanceUntilIdle()

        val upper = viewModel.uiState.value.resultScreenshots
            .slot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        val lower = viewModel.uiState.value.resultScreenshots
            .slot(MatchResultScreenshotRole.MATCH_RESULT_LOWER)

        assertTrue(upper.hasLinkedAsset)
        assertNotNull(assetRepository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER)))
        assertFalse(lower.hasLinkedAsset)
        assertNull(assetRepository.getByIdentity(identity(MatchResultScreenshotRole.MATCH_RESULT_LOWER)))
    }

    @Test
    fun finalizedMatchCannotRemoveResultScreenshot() = runTest {
        val upperUri = "content://picker/finalized-upper"
        val assetRepository = FakeMatchResultScreenshotAssetRepository()
        val viewModel = viewModel(
            bytesByUri = mapOf(upperUri to byteArrayOf(13, 14, 15)),
            assetRepository = assetRepository,
        )

        viewModel.load(RESULT_TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        viewModel.requestPhotoPicker(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        viewModel.onPhotoPickerResult(MatchResultScreenshotRole.MATCH_RESULT_UPPER, upperUri)
        advanceUntilIdle()
        viewModel.onNavigationHandled()

        tournamentRepository.finalizeDraftMatch(
            matchId = matchId,
            placements = (1..12).map { slot -> MatchPlacement(slot, slot) },
            kills = (1..12).map { slot -> MatchKill(slot, 0) },
        )
        advanceUntilIdle()

        viewModel.removeResultScreenshot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        advanceUntilIdle()

        assertTrue(
            viewModel.uiState.value.resultScreenshots
                .slot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
                .hasLinkedAsset,
        )
        assertNotNull(
            assetRepository.getByIdentity(
                identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER),
            ),
        )
    }
    @Test
    fun malformedPersistedRoleIsIgnoredAndDoesNotPopulateUpperSlot() = runTest {
        val malformedAsset = MatchResultScreenshotAssetEntity(
            tournamentId = RESULT_TOURNAMENT_ID,
            matchId = matchId,
            screenshotKind = OcrScreenshotKind.MATCH_RESULT.name,
            screenshotRole = "MALFORMED_ROLE",
            ownerUserId = "owner-id",
            localRelativePath = "screenshots/malformed/original.png",
            fileExtension = "png",
            mimeType = "image/png",
            originalWidth = 1600,
            originalHeight = 720,
            byteSize = 3L,
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
        val assetRepository = FakeMatchResultScreenshotAssetRepository(
            initialAssets = listOf(malformedAsset),
        )
        val viewModel = viewModel(
            bytesByUri = emptyMap(),
            assetRepository = assetRepository,
        )

        viewModel.load(RESULT_TOURNAMENT_ID, matchId)
        advanceUntilIdle()

        val upper = viewModel.uiState.value.resultScreenshots
            .slot(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        val lower = viewModel.uiState.value.resultScreenshots
            .slot(MatchResultScreenshotRole.MATCH_RESULT_LOWER)

        assertFalse(upper.hasLinkedAsset)
        assertNull(upper.fingerprint)
        assertFalse(lower.hasLinkedAsset)
        assertNull(lower.fingerprint)
    }

    private fun viewModel(
        bytesByUri: Map<String, ByteArray>,
        assetRepository: FakeMatchResultScreenshotAssetRepository,
        uploader: RecordingMatchResultScreenshotStorageUploader = RecordingMatchResultScreenshotStorageUploader(),
    ): MatchReviewViewModel {
        val fingerprintGenerator = ImageSourceFingerprintGenerator(
            ImageSourceStreamOpener { uri -> bytesByUri.getValue(uri).inputStream() },
            Dispatchers.Unconfined,
        )
        return MatchReviewViewModel(
            getTournamentById = GetTournamentByIdUseCase(tournamentRepository),
            observeMatches = ObserveMatchesUseCase(tournamentRepository),
            observeTournamentSlots = ObserveTournamentSlotsUseCase(tournamentRepository),
            observeRoster = ObserveRosterByTournamentUseCase(tournamentRepository),
            observeDraftValues = ObserveMatchDraftValuesUseCase(tournamentRepository),
            validateMatchResult = ValidateMatchResultUseCase(),
            finalizeMatch = FinalizeMatchUseCase(tournamentRepository, ValidateMatchResultUseCase()),
            imageCandidateValidator = ImageCandidateValidator(
                ImageCandidateMetadataReader {
                    ImageCandidateReadResult.Metadata("image/png", width = 1600, height = 720)
                },
            ),
            screenshotDuplicateDetector = ScreenshotDuplicateDetector(fingerprintGenerator),
            matchResultScreenshotDuplicateDetector = MatchResultScreenshotDuplicateDetector(
                fingerprintGenerator = fingerprintGenerator,
                assetRepository = assetRepository,
            ),
            localImagePreserver = LocalImagePreserver(
                appPrivateRoot = Files.createTempDirectory("rank-forge-result").toFile(),
                sourceStreamOpener = ImageSourceStreamOpener { uri -> bytesByUri.getValue(uri).inputStream() },
                mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
                ioDispatcher = Dispatchers.Unconfined,
            ),
            matchResultScreenshotStorageUploader = uploader,
            matchResultScreenshotAssetRepository = assetRepository,
            matchResultScreenshotAssetCloudDataSource = RecordingMatchResultScreenshotAssetCloudDataSource(),
            screenshotOwnerProvider = FixedScreenshotOwnerProvider("owner-id"),
        )
    }

    private fun identity(role: MatchResultScreenshotRole) = MatchResultScreenshotIdentity(
        tournamentId = RESULT_TOURNAMENT_ID,
        matchId = matchId,
        role = role,
    )

    private class FixedScreenshotOwnerProvider(
        private val ownerId: String?,
    ) : ScreenshotOwnerProvider {
        override suspend fun currentOwnerUserId(): String? = ownerId
    }

    private class RecordingMatchResultScreenshotStorageUploader(
        private val result: MatchResultScreenshotStorageUploadResult? = null,
    ) : MatchResultScreenshotStorageUploader {
        data class Call(
            val role: MatchResultScreenshotRole?,
            val file: File?,
        )

        val calls = mutableListOf<Call>()

        override suspend fun upload(
            tournamentId: String?,
            matchId: String?,
            role: MatchResultScreenshotRole?,
            localFile: File?,
        ): MatchResultScreenshotStorageUploadResult {
            calls += Call(role, localFile)
            return result ?: MatchResultScreenshotStorageUploadResult.Uploaded(
                "object/${role?.name}.png",
            )
        }
    }

    private class RecordingMatchResultScreenshotAssetCloudDataSource : MatchResultScreenshotAssetCloudDataSource {
        override suspend fun upsert(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetCloudResult =
            MatchResultScreenshotAssetCloudResult.Success

        override suspend fun deleteByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): MatchResultScreenshotAssetCloudResult = MatchResultScreenshotAssetCloudResult.Success
    }

    private class FakeMatchResultScreenshotAssetRepository(
        initialAssets: List<MatchResultScreenshotAssetEntity> = emptyList(),
    ) : MatchResultScreenshotAssetRepository {
        private val assets = MutableStateFlow(initialAssets)

        override fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
            assets.map { list -> list.filter { it.matchId == matchId } }

        override fun observeByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): Flow<MatchResultScreenshotAssetEntity?> =
            assets.map { list -> list.firstOrNull { it.matches(identity) } }

        override suspend fun getByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): MatchResultScreenshotAssetEntity? = assets.value.firstOrNull { it.matches(identity) }

        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
            assets.map { list -> list.filter { it.tournamentId == tournamentId } }

        override suspend fun findDuplicateFingerprint(
            identity: MatchResultScreenshotIdentity,
            sha256: String,
        ): MatchResultScreenshotAssetEntity? = assets.value.firstOrNull {
            it.tournamentId == identity.tournamentId &&
                it.sha256 == sha256 &&
                !it.matches(identity)
        }

        override suspend fun saveOrReplace(
            asset: MatchResultScreenshotAssetEntity,
        ): MatchResultScreenshotAssetSaveResult {
            val identity = MatchResultScreenshotIdentity(
                tournamentId = asset.tournamentId,
                matchId = asset.matchId,
                role = MatchResultScreenshotRole.valueOf(asset.screenshotRole),
            )
            val existing = getByIdentity(identity)
            val assetToSave = if (existing != null && existing.sha256 != asset.sha256) {
                asset.copy(
                    cropProfileId = null,
                    cropLeft = null,
                    cropTop = null,
                    cropRight = null,
                    cropBottom = null,
                )
            } else {
                asset
            }
            assets.value = assets.value.filterNot { it.matches(identity) } + assetToSave
            return MatchResultScreenshotAssetSaveResult.Saved
        }

        override suspend fun markLocalMissing(identity: MatchResultScreenshotIdentity, updatedAt: Long) {
            assets.value = assets.value.map {
                if (it.matches(identity)) {
                    it.copy(localStatus = ScreenshotLocalStatus.MISSING.name, updatedAt = updatedAt)
                } else {
                    it
                }
            }
        }

        override suspend fun markCleanupFailure(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit

        override suspend fun persistConfirmedCrop(
            identity: MatchResultScreenshotIdentity,
            crop: OcrNormalizedCropRect,
            updatedAt: Long,
        ): MatchResultScreenshotCropSaveResult {
            val existing = getByIdentity(identity) ?: return MatchResultScreenshotCropSaveResult.MissingAsset
            assets.value = assets.value.filterNot { it.matches(identity) } + existing.copy(
                cropProfileId = "match-result",
                cropLeft = crop.left,
                cropTop = crop.top,
                cropRight = crop.right,
                cropBottom = crop.bottom,
                updatedAt = updatedAt,
                revision = existing.revision + 1,
            )
            return MatchResultScreenshotCropSaveResult.Saved
        }

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
            tournamentId == identity.tournamentId &&
                matchId == identity.matchId &&
                screenshotRole == identity.role.name &&
                screenshotKind == OcrScreenshotKind.MATCH_RESULT.name
    }
}
