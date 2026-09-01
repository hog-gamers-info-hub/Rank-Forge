package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.data.local.MatchResultOcrCacheFingerprint
import com.hoggamers.rankforge.data.local.MatchResultOcrCacheRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchResultScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
import com.hoggamers.rankforge.presentation.screen.ScreenshotOwnerProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CachingMatchResultOcrPreviewRunnerTest {
    @Test
    fun validCacheHitSkipsDelegate() = runTest {
        val identity = identity()
        val assetRepository = FakeAssetRepository(asset(identity))
        val cache = FakeCacheRepository()
        val expected = processed(identity.role)
        cache.entries[assetRepository.fingerprint(identity)] = expected
        val delegateCalls = AtomicInteger()
        val runner = runner(assetRepository, cache) {
            delegateCalls.incrementAndGet()
            error("delegate must not run on a valid cache hit")
        }

        assertEquals(expected, runner.process(identity))
        assertEquals(0, delegateCalls.get())
    }

    @Test
    fun cacheMissRunsDelegateAndSavesProcessedResult() = runTest {
        val identity = identity()
        val assetRepository = FakeAssetRepository(asset(identity))
        val cache = FakeCacheRepository()
        val expected = processed(identity.role)
        var delegateCalls = 0
        val runner = runner(assetRepository, cache) {
            delegateCalls++
            expected
        }

        assertEquals(expected, runner.process(identity))
        assertEquals(1, delegateCalls)
        assertEquals(expected, cache.entries[assetRepository.fingerprint(identity)])
        assertEquals(1, cache.saveCount)
    }

    @Test
    fun secondCallWithSameFingerprintUsesCache() = runTest {
        val identity = identity()
        val assetRepository = FakeAssetRepository(asset(identity))
        val cache = FakeCacheRepository()
        var delegateCalls = 0
        val runner = runner(assetRepository, cache) {
            delegateCalls++
            processed(identity.role)
        }

        runner.process(identity)
        runner.process(identity)

        assertEquals(1, delegateCalls)
        assertEquals(1, cache.saveCount)
    }

    @Test
    fun changedShaInvalidatesCache() = runTest {
        val identity = identity()
        val assetRepository = FakeAssetRepository(asset(identity, sha256 = "a".repeat(64)))
        val cache = FakeCacheRepository()
        val oldFingerprint = assetRepository.fingerprint(identity)
        cache.entries[oldFingerprint] = processed(identity.role)
        assetRepository.asset = asset(identity, sha256 = "b".repeat(64))
        val runner = runner(assetRepository, cache) { processed(identity.role) }

        runner.process(identity)

        assertTrue(cache.entries.containsKey(assetRepository.fingerprint(identity)))
        assertEquals(1, cache.saveCount)
    }

    @Test
    fun changedCropInvalidatesCache() = runTest {
        val identity = identity()
        val assetRepository = FakeAssetRepository(asset(identity, crop = OcrNormalizedCropRect(0.0, 0.0, 0.5, 1.0)))
        val cache = FakeCacheRepository()
        cache.entries[assetRepository.fingerprint(identity)] = processed(identity.role)
        assetRepository.asset = asset(identity, crop = OcrNormalizedCropRect(0.5, 0.0, 1.0, 1.0))
        val runner = runner(assetRepository, cache) { processed(identity.role) }

        runner.process(identity)

        assertEquals(1, cache.saveCount)
        assertEquals(assetRepository.fingerprint(identity), cache.savedFingerprint)
    }

    @Test
    fun changedPipelineVersionInvalidatesCache() = runTest {
        assertEquals(11, MATCH_RESULT_OCR_CACHE_PIPELINE_VERSION)
        val identity = identity()
        val assetRepository = FakeAssetRepository(asset(identity))
        val cache = FakeCacheRepository()
        val oldFingerprint = assetRepository.fingerprint(identity).copy(ocrPipelineVersion = 0)
        cache.entries[oldFingerprint] = processed(identity.role)
        val runner = runner(assetRepository, cache) { processed(identity.role) }

        runner.process(identity)

        assertEquals(1, cache.saveCount)
        assertEquals(MATCH_RESULT_OCR_CACHE_PIPELINE_VERSION, cache.savedFingerprint?.ocrPipelineVersion)
    }

    @Test
    fun cacheReadFailureFallsBackToFreshOcr() = runTest {
        val identity = identity()
        val cache = FakeCacheRepository().apply { readFailure = IllegalStateException("read") }
        var delegateCalls = 0
        val runner = runner(FakeAssetRepository(asset(identity)), cache) {
            delegateCalls++
            processed(identity.role)
        }

        val result = runner.process(identity)

        assertTrue(result is MatchResultOcrPreviewProcessingResult.Processed)
        assertEquals(1, delegateCalls)
    }

    @Test
    fun cacheWriteFailureDoesNotHideFreshResult() = runTest {
        val identity = identity()
        val cache = FakeCacheRepository().apply { saveFailure = IllegalStateException("write") }
        val expected = processed(identity.role)
        val runner = runner(FakeAssetRepository(asset(identity)), cache) { expected }

        assertEquals(expected, runner.process(identity))
    }

    @Test
    fun nonProcessedResultsAreNotCached() = runTest {
        val identity = identity()
        val cache = FakeCacheRepository()
        val runner = runner(FakeAssetRepository(asset(identity)), cache) {
            MatchResultOcrPreviewProcessingResult.RecognitionFailed
        }

        runner.process(identity)

        assertEquals(0, cache.saveCount)
    }

    @Test
    fun missingAssetDoesNotProduceCacheHit() = runTest {
        val identity = identity()
        val cache = FakeCacheRepository()
        val runner = runner(FakeAssetRepository(), cache) {
            MatchResultOcrPreviewProcessingResult.MissingAsset
        }

        assertSame(MatchResultOcrPreviewProcessingResult.MissingAsset, runner.process(identity))
        assertEquals(0, cache.saveCount)
    }

    @Test
    fun cacheReadCancellationPropagates() = runTest {
        val identity = identity()
        val cache = FakeCacheRepository().apply { readFailure = CancellationException("cancel read") }
        val runner = runner(FakeAssetRepository(asset(identity)), cache) { processed(identity.role) }

        assertCancellation { runner.process(identity) }
    }

    @Test
    fun cacheSaveCancellationPropagates() = runTest {
        val identity = identity()
        val cache = FakeCacheRepository().apply { saveFailure = CancellationException("cancel save") }
        val runner = runner(FakeAssetRepository(asset(identity)), cache) { processed(identity.role) }

        assertCancellation { runner.process(identity) }
    }

    @Test
    fun upperAndLowerUseIndependentCacheEntries() = runTest {
        val upper = identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        val lower = identity(MatchResultScreenshotRole.MATCH_RESULT_LOWER)
        val assets = FakeAssetRepository(asset(upper), asset(lower))
        val cache = FakeCacheRepository()
        cache.entries[assets.fingerprint(upper)] = processed(upper.role)
        var lowerCalls = 0
        val runner = CachingMatchResultOcrPreviewRunner(assets, cache, screenshotOwnerProvider = ownerProvider) { identityToProcess ->
            lowerCalls++
            processed(identityToProcess.role)
        }

        runner.process(upper)
        runner.process(lower)

        assertEquals(1, lowerCalls)
        assertTrue(cache.entries.containsKey(assets.fingerprint(lower)))
    }

    @Test
    fun rolePayloadMismatchIsRejectedByCacheRunner() = runTest {
        val identity = identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        val assetRepository = FakeAssetRepository(asset(identity))
        val cache = FakeCacheRepository().apply {
            entries[assetRepository.fingerprint(identity)] = processed(MatchResultScreenshotRole.MATCH_RESULT_LOWER)
        }
        var delegateCalls = 0
        val runner = runner(assetRepository, cache) {
            delegateCalls++
            processed(identity.role)
        }

        runner.process(identity)

        assertEquals(1, delegateCalls)
    }

    @Test
    fun cacheHitRaceFallsBackWithoutReturningStaleResultOrWritingFreshResult() = runTest {
        val identity = identity()
        val first = asset(identity, sha256 = "a".repeat(64))
        val second = asset(identity, sha256 = "b".repeat(64))
        val assetRepository = FakeAssetRepository(first).apply {
            onGet = { if (getCount == 2) asset = second }
        }
        val cache = FakeCacheRepository().apply {
            entries[assetRepository.fingerprint(identity)] = processed(identity.role)
        }
        var delegateCalls = 0
        val runner = runner(assetRepository, cache) {
            delegateCalls++
            processed(identity.role)
        }

        runner.process(identity)

        assertEquals(1, delegateCalls)
        assertEquals(0, cache.saveCount)
    }

    @Test
    fun changedFingerprintAfterProcessingDoesNotWriteStaleResult() = runTest {
        val identity = identity()
        val first = asset(identity, sha256 = "a".repeat(64))
        val second = asset(identity, sha256 = "b".repeat(64))
        val assetRepository = FakeAssetRepository(first).apply {
            onGet = { if (getCount == 2) asset = second }
        }
        val cache = FakeCacheRepository()
        val runner = runner(assetRepository, cache) { processed(identity.role) }

        runner.process(identity)

        assertEquals(0, cache.saveCount)
    }

    private fun runner(
        assets: FakeAssetRepository,
        cache: FakeCacheRepository,
        delegate: suspend (MatchResultScreenshotIdentity) -> MatchResultOcrPreviewProcessingResult,
    ) = CachingMatchResultOcrPreviewRunner(
        assetRepository = assets,
        cacheRepository = cache,
        screenshotOwnerProvider = ownerProvider,
        delegate = MatchResultOcrPreviewRunner(delegate),
    )

    private val ownerProvider = object : ScreenshotOwnerProvider {
        override suspend fun currentOwnerUserId(): String = "owner-1"
    }

    private fun identity(
        role: MatchResultScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
    ) = MatchResultScreenshotIdentity("tournament-1", "match-1", role = role)

    private fun asset(
        identity: MatchResultScreenshotIdentity,
        sha256: String = "a".repeat(64),
        crop: OcrNormalizedCropRect = OcrNormalizedCropRect(0.0, 0.0, 1.0, 1.0),
    ) = MatchResultScreenshotAssetEntity(
        tournamentId = identity.tournamentId,
        matchId = identity.matchId,
        screenshotKind = OcrScreenshotKind.MATCH_RESULT.name,
        screenshotRole = identity.role.name,
        ownerUserId = "owner-1",
        localRelativePath = "result.png",
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 1000,
        originalHeight = 800,
        byteSize = 100L,
        sha256 = sha256,
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.PENDING.name,
        uploadFailureCode = null,
        storageBucket = null,
        storageObjectPath = null,
        cropProfileId = "match-result",
        cropLeft = crop.left,
        cropTop = crop.top,
        cropRight = crop.right,
        cropBottom = crop.bottom,
        createdAt = 1L,
        updatedAt = 1L,
        preservedAt = 1L,
        uploadedAt = null,
        revision = 1L,
    )

    private fun processed(role: MatchResultScreenshotRole) =
        MatchResultOcrPreviewProcessingResult.Processed(
            extraction = MatchResultOcrExtractionResult(role, emptyList(), emptyList()),
            pixelCrop = OcrPixelCropRect(0, 0, 100, 100),
            cropWidth = 100,
            cropHeight = 100,
        )

    private suspend fun assertCancellation(block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            // Expected.
        }
    }

    private class FakeAssetRepository(
        vararg initialAssets: MatchResultScreenshotAssetEntity,
    ) : MatchResultScreenshotAssetRepository {
        private val assets = initialAssets.associateBy { it.screenshotRole }.toMutableMap()
        var asset: MatchResultScreenshotAssetEntity?
            get() = assets.values.firstOrNull()
            set(value) {
                if (value != null) assets[value.screenshotRole] = value
            }
        var getCount = 0
        var onGet: (() -> Unit)? = null

        override fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>> = flowOf(emptyList())
        override fun observeByIdentity(identity: MatchResultScreenshotIdentity): Flow<MatchResultScreenshotAssetEntity?> = flowOf(assets[identity.role.name])
        override suspend fun getByIdentity(identity: MatchResultScreenshotIdentity): MatchResultScreenshotAssetEntity? {
            getCount++
            onGet?.invoke()
            return assets[identity.role.name]
        }
        override suspend fun getByIdentityAndOwner(identity: MatchResultScreenshotIdentity, ownerUserId: String): MatchResultScreenshotAssetEntity? =
            getByIdentity(identity)?.takeIf { it.ownerUserId == ownerUserId }
        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>> = flowOf(emptyList())
        override suspend fun findDuplicateFingerprint(identity: MatchResultScreenshotIdentity, sha256: String): MatchResultScreenshotAssetEntity? = null
        override suspend fun saveOrReplace(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetSaveResult = MatchResultScreenshotAssetSaveResult.Saved
        override suspend fun markLocalMissing(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markCleanupFailure(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun persistConfirmedCrop(identity: MatchResultScreenshotIdentity, crop: OcrNormalizedCropRect, updatedAt: Long): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.Saved
        override suspend fun clearConfirmedCrop(identity: MatchResultScreenshotIdentity, updatedAt: Long): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.Saved
        override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) = Unit
        override suspend fun deleteByMatchId(matchId: String) = Unit

        fun fingerprint(identity: MatchResultScreenshotIdentity): MatchResultOcrCacheFingerprint =
            assets.getValue(identity.role.name).toMatchResultOcrCacheFingerprint(identity)!!
    }

    private class FakeCacheRepository : MatchResultOcrCacheRepository {
        val entries = mutableMapOf<MatchResultOcrCacheFingerprint, MatchResultOcrPreviewProcessingResult.Processed>()
        var readFailure: Throwable? = null
        var saveFailure: Throwable? = null
        var saveCount = 0
        var savedFingerprint: MatchResultOcrCacheFingerprint? = null

        override suspend fun read(fingerprint: MatchResultOcrCacheFingerprint): MatchResultOcrPreviewProcessingResult.Processed? {
            readFailure?.let { throw it }
            return entries[fingerprint]
        }

        override suspend fun save(
            fingerprint: MatchResultOcrCacheFingerprint,
            processed: MatchResultOcrPreviewProcessingResult.Processed,
        ) {
            saveFailure?.let { throw it }
            saveCount++
            savedFingerprint = fingerprint
            entries[fingerprint] = processed
        }

        override suspend fun readByOwner(
            fingerprint: MatchResultOcrCacheFingerprint,
            ownerUserId: String,
        ): MatchResultOcrPreviewProcessingResult.Processed? =
            if (ownerUserId == "owner-1") read(fingerprint) else null

        override suspend fun saveByOwner(
            fingerprint: MatchResultOcrCacheFingerprint,
            processed: MatchResultOcrPreviewProcessingResult.Processed,
            ownerUserId: String,
        ): Boolean {
            if (ownerUserId != "owner-1") return false
            save(fingerprint, processed)
            return true
        }
    }
}
