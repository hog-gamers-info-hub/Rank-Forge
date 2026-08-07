package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudDataSource
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudFailure
import com.hoggamers.rankforge.data.cloud.MatchResultScreenshotAssetCloudResult
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
import com.hoggamers.rankforge.domain.tournament.MatchKill
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
        matchId = (CreateMatchUseCase(tournamentRepository)(
            CreateMatchInput(
                tournamentId = CROP_TOURNAMENT_ID,
                matchNumber = "1",
                date = LocalDate.of(2026, 8, 7),
                mapName = "Bermuda",
            ),
        ) as CreateMatchResult.Created).match.id
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
        val viewModel = MatchResultScreenshotCropViewModel(
            observeMatches = ObserveMatchesUseCase(tournamentRepository),
            assetRepository = repository,
            cloudDataSource = FailingCropCloudDataSource(),
            localImagePreserver = preserver,
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
        val viewModel = MatchResultScreenshotCropViewModel(
            observeMatches = ObserveMatchesUseCase(tournamentRepository),
            assetRepository = repository,
            cloudDataSource = FailingCropCloudDataSource(),
            localImagePreserver = preserver,
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
        val viewModel = MatchResultScreenshotCropViewModel(
            observeMatches = ObserveMatchesUseCase(tournamentRepository),
            assetRepository = repository,
            cloudDataSource = FailingCropCloudDataSource(),
            localImagePreserver = preserver,
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
        val viewModel = MatchResultScreenshotCropViewModel(
            observeMatches = ObserveMatchesUseCase(tournamentRepository),
            assetRepository = repository,
            cloudDataSource = CancellingCropCloudDataSource(),
            localImagePreserver = preserver,
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
        assertFalse(confirmedCallbackCalled)
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

    private class FakeCropAssetRepository(
        asset: MatchResultScreenshotAssetEntity,
    ) : MatchResultScreenshotAssetRepository {
        private val assets = MutableStateFlow(listOf(asset))
        var persistConfirmedCropCalls: Int = 0
            private set

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
        ): MatchResultScreenshotAssetEntity? = null

        override suspend fun saveOrReplace(
            asset: MatchResultScreenshotAssetEntity,
        ): MatchResultScreenshotAssetSaveResult {
            assets.value = assets.value.filterNot {
                it.matchId == asset.matchId && it.screenshotRole == asset.screenshotRole
            } + asset
            return MatchResultScreenshotAssetSaveResult.Saved
        }

        override suspend fun markLocalMissing(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit

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
    }
}
