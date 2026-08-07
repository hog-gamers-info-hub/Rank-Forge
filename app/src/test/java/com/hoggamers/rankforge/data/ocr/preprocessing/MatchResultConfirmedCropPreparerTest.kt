package com.hoggamers.rankforge.data.ocr.preprocessing

import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchResultScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
import com.hoggamers.rankforge.presentation.screen.ImageSourceMimeTypeReader
import com.hoggamers.rankforge.presentation.screen.ImageSourceStreamOpener
import com.hoggamers.rankforge.presentation.screen.LocalImagePreserver
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultConfirmedCropPreparerTest {
    @Test
    fun upperUsesExactlyItsConfirmedManualCropOnce() = runTest {
        val fixture = fixture(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            crop = OcrNormalizedCropRect(
                left = 0.10,
                top = 0.20,
                right = 0.90,
                bottom = 0.80,
            ),
        )

        val result = fixture.preparer.prepare(fixture.identity)
            as MatchResultConfirmedCropPreparationResult.Prepared

        assertEquals(1, fixture.operations.cropCalls)
        assertEquals(
            OcrPixelCropRect(left = 160, top = 144, right = 1440, bottom = 576),
            fixture.operations.lastCropRect,
        )
        assertEquals(1280, result.crop.image.width)
        assertEquals(432, result.crop.image.height)
        assertEquals(fixture.identity, result.crop.identity)
        assertEquals(OcrCropValidationProfiles.MatchResult.id, result.crop.cropProfileId)
    }

    @Test
    fun upperAndLowerPrepareIndependentRoleSpecificCrops() = runTest {
        val upperCrop = OcrNormalizedCropRect(0.00, 0.00, 1.00, 0.70)
        val lowerCrop = OcrNormalizedCropRect(0.15, 0.35, 0.95, 1.00)
        val root = Files.createTempDirectory("rank-forge-confirmed-crops").toFile()
        val preserver = preserver(root)
        val upperIdentity = identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        val lowerIdentity = identity(MatchResultScreenshotRole.MATCH_RESULT_LOWER)
        val upperAsset = asset(preserver, upperIdentity, upperCrop, sha256 = "upper-sha")
        val lowerAsset = asset(preserver, lowerIdentity, lowerCrop, sha256 = "lower-sha")
        createPreservedFile(root, upperAsset.localRelativePath)
        createPreservedFile(root, lowerAsset.localRelativePath)
        val repository = FakeRepository(listOf(upperAsset, lowerAsset))
        val operations = FakeImageOperations()
        val preparer = MatchResultConfirmedCropPreparer(
            assetRepository = repository,
            localImagePreserver = preserver,
            imageOperations = operations,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val upper = preparer.prepare(upperIdentity)
            as MatchResultConfirmedCropPreparationResult.Prepared
        val upperRect = operations.lastCropRect
        val lower = preparer.prepare(lowerIdentity)
            as MatchResultConfirmedCropPreparationResult.Prepared
        val lowerRect = operations.lastCropRect

        assertEquals(OcrPixelCropRect(0, 0, 1600, 504), upperRect)
        assertEquals(OcrPixelCropRect(240, 252, 1520, 720), lowerRect)
        assertEquals("upper-sha", upper.crop.sourceSha256)
        assertEquals("lower-sha", lower.crop.sourceSha256)
        assertNotSame(upper.crop.image, lower.crop.image)
        assertEquals(2, operations.cropCalls)
    }

    @Test
    fun missingConfirmedCropIsBlockedBeforeImageDecode() = runTest {
        val fixture = fixture(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            crop = null,
        )

        val result = fixture.preparer.prepare(fixture.identity)
            as MatchResultConfirmedCropPreparationResult.Failed

        assertEquals(
            MatchResultConfirmedCropPreparationFailure.CONFIRMED_CROP_MISSING,
            result.failure,
        )
        assertEquals(0, fixture.operations.decodeCalls)
        assertEquals(0, fixture.operations.cropCalls)
    }

    @Test
    fun rolePathMismatchIsBlockedBeforeImageDecode() = runTest {
        val root = Files.createTempDirectory("rank-forge-confirmed-crop-path").toFile()
        val preserver = preserver(root)
        val upperIdentity = identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        val lowerPath = preserver.matchResultRelativePath(
            tournamentId = upperIdentity.tournamentId,
            matchId = upperIdentity.matchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            extension = "png",
        )
        val mismatchedAsset = baseAsset(
            identity = upperIdentity,
            localRelativePath = lowerPath,
            crop = OcrNormalizedCropRect(0.0, 0.0, 1.0, 1.0),
            sha256 = "upper-sha",
        )
        createPreservedFile(root, lowerPath)
        val operations = FakeImageOperations()
        val preparer = MatchResultConfirmedCropPreparer(
            assetRepository = FakeRepository(listOf(mismatchedAsset)),
            localImagePreserver = preserver,
            imageOperations = operations,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = preparer.prepare(upperIdentity)
            as MatchResultConfirmedCropPreparationResult.Failed

        assertEquals(MatchResultConfirmedCropPreparationFailure.LOCAL_PATH_MISMATCH, result.failure)
        assertEquals(0, operations.decodeCalls)
        assertEquals(0, operations.cropCalls)
    }

    private fun fixture(
        role: MatchResultScreenshotRole,
        crop: OcrNormalizedCropRect?,
    ): Fixture {
        val root = Files.createTempDirectory("rank-forge-confirmed-crop").toFile()
        val preserver = preserver(root)
        val identity = identity(role)
        val asset = asset(preserver, identity, crop, sha256 = "sha-${role.name}")
        createPreservedFile(root, asset.localRelativePath)
        val operations = FakeImageOperations()
        return Fixture(
            identity = identity,
            operations = operations,
            preparer = MatchResultConfirmedCropPreparer(
                assetRepository = FakeRepository(listOf(asset)),
                localImagePreserver = preserver,
                imageOperations = operations,
                ioDispatcher = Dispatchers.Unconfined,
            ),
        )
    }

    private fun preserver(root: File) = LocalImagePreserver(
        appPrivateRoot = root,
        sourceStreamOpener = ImageSourceStreamOpener { null },
        mimeTypeReader = ImageSourceMimeTypeReader { null },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun identity(
        role: MatchResultScreenshotRole,
    ) = MatchResultScreenshotIdentity(
        tournamentId = "tournament-1",
        matchId = "match-1",
        role = role,
    )

    private fun asset(
        preserver: LocalImagePreserver,
        identity: MatchResultScreenshotIdentity,
        crop: OcrNormalizedCropRect?,
        sha256: String,
    ): MatchResultScreenshotAssetEntity = baseAsset(
        identity = identity,
        localRelativePath = preserver.matchResultRelativePath(
            tournamentId = identity.tournamentId,
            matchId = identity.matchId,
            role = identity.role,
            extension = "png",
        ),
        crop = crop,
        sha256 = sha256,
    )

    private fun baseAsset(
        identity: MatchResultScreenshotIdentity,
        localRelativePath: String,
        crop: OcrNormalizedCropRect?,
        sha256: String,
    ) = MatchResultScreenshotAssetEntity(
        tournamentId = identity.tournamentId,
        matchId = identity.matchId,
        screenshotKind = OcrScreenshotKind.MATCH_RESULT.name,
        screenshotRole = identity.role.name,
        ownerUserId = "owner-1",
        localRelativePath = localRelativePath,
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 1600,
        originalHeight = 720,
        byteSize = 100,
        sha256 = sha256,
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
        uploadFailureCode = null,
        storageBucket = "ocr-screenshots",
        storageObjectPath = "object/path",
        cropProfileId = crop?.let { OcrCropValidationProfiles.MatchResult.id },
        cropLeft = crop?.left,
        cropTop = crop?.top,
        cropRight = crop?.right,
        cropBottom = crop?.bottom,
        createdAt = 1,
        updatedAt = 2,
        preservedAt = 1,
        uploadedAt = 2,
        revision = 3,
    )

    private fun createPreservedFile(
        root: File,
        relativePath: String,
    ) {
        val file = File(root, relativePath)
        assertTrue(file.parentFile?.mkdirs() != false)
        file.writeBytes(byteArrayOf(1))
    }

    private data class Fixture(
        val identity: MatchResultScreenshotIdentity,
        val operations: FakeImageOperations,
        val preparer: MatchResultConfirmedCropPreparer,
    )

    private data class FakeImage(
        override val width: Int,
        override val height: Int,
    ) : MatchResultPreparedCropImage

    private class FakeImageOperations : MatchResultConfirmedCropImageOperations {
        var decodeCalls = 0
        var cropCalls = 0
        var lastCropRect: OcrPixelCropRect? = null

        override fun readDimensions(file: File): OcrImageDimensions = OcrImageDimensions(1600, 720)

        override fun decode(file: File): MatchResultPreparedCropImage {
            decodeCalls += 1
            return FakeImage(1600, 720)
        }

        override fun crop(
            source: MatchResultPreparedCropImage,
            cropRect: OcrPixelCropRect,
        ): MatchResultPreparedCropImage {
            cropCalls += 1
            lastCropRect = cropRect
            return FakeImage(cropRect.width, cropRect.height)
        }

        override fun release(image: MatchResultPreparedCropImage) = Unit
    }

    private class FakeRepository(
        private val assets: List<MatchResultScreenshotAssetEntity>,
    ) : MatchResultScreenshotAssetRepository {
        override fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
            flowOf(assets.filter { it.matchId == matchId })

        override fun observeByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): Flow<MatchResultScreenshotAssetEntity?> = flowOf(find(identity))

        override suspend fun getByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): MatchResultScreenshotAssetEntity? = find(identity)

        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
            flowOf(assets.filter { it.tournamentId == tournamentId })

        override suspend fun findDuplicateFingerprint(
            identity: MatchResultScreenshotIdentity,
            sha256: String,
        ): MatchResultScreenshotAssetEntity? = null

        override suspend fun saveOrReplace(
            asset: MatchResultScreenshotAssetEntity,
        ): MatchResultScreenshotAssetSaveResult = MatchResultScreenshotAssetSaveResult.Saved

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

        override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) = Unit

        override suspend fun deleteByMatchId(matchId: String) = Unit

        private fun find(identity: MatchResultScreenshotIdentity): MatchResultScreenshotAssetEntity? =
            assets.firstOrNull { asset ->
                asset.tournamentId == identity.tournamentId &&
                    asset.matchId == identity.matchId &&
                    asset.screenshotRole == identity.role.name
            }
    }
}
