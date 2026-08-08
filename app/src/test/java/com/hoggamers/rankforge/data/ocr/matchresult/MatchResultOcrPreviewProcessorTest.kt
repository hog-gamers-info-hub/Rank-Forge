package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchResultScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultOcrPreviewProcessorTest {
    @Test
    fun returnsMissingAssetWhenNoAssetExists() = runBlocking {
        val processor = processor(asset = null)

        assertSame(
            MatchResultOcrPreviewProcessingResult.MissingAsset,
            processor.process(identity()),
        )
    }

    @Test
    fun returnsMissingConfirmedCropWhenCropMetadataIsAbsent() = runBlocking {
        val processor = processor(asset = asset(crop = null))

        assertSame(
            MatchResultOcrPreviewProcessingResult.MissingConfirmedCrop,
            processor.process(identity()),
        )
    }

    @Test
    fun passesUpperRoleAndCroppedDimensionsToExtractor() = runBlocking {
        var captured: Capture? = null
        val processor = processor(
            asset = asset(),
            recognition = MatchResultOcrPreviewRecognitionResult.Recognized(
                cropWidth = 321,
                cropHeight = 123,
                blocks = emptyList(),
            ),
            fieldExtractor = MatchResultOcrPreviewFieldExtractor { role, width, height, _ ->
                captured = Capture(role, width, height)
                emptyExtraction(role)
            },
        )

        assertTrue(processor.process(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER)) is MatchResultOcrPreviewProcessingResult.Processed)
        assertEquals(Capture(MatchResultScreenshotRole.MATCH_RESULT_UPPER, 321, 123), captured)
    }

    @Test
    fun passesLowerRoleAndCroppedDimensionsToExtractor() = runBlocking {
        var captured: Capture? = null
        val processor = processor(
            asset = asset(),
            recognition = MatchResultOcrPreviewRecognitionResult.Recognized(
                cropWidth = 476,
                cropHeight = 1174,
                blocks = emptyList(),
            ),
            fieldExtractor = MatchResultOcrPreviewFieldExtractor { role, width, height, _ ->
                captured = Capture(role, width, height)
                emptyExtraction(role)
            },
        )

        assertTrue(processor.process(identity(MatchResultScreenshotRole.MATCH_RESULT_LOWER)) is MatchResultOcrPreviewProcessingResult.Processed)
        assertEquals(Capture(MatchResultScreenshotRole.MATCH_RESULT_LOWER, 476, 1174), captured)
    }

    @Test
    fun processorDoesNotMutateAssetOrMatchState() = runBlocking {
        val original = asset()
        val repository = FakeAssetRepository(original)
        val processor = processor(repository = repository)

        assertTrue(processor.process(identity()) is MatchResultOcrPreviewProcessingResult.Processed)
        assertEquals(original, repository.asset)
        assertEquals(0, repository.writeCount)
    }

    @Test
    fun lowerPreviewDoesNotForcePosition12() = runBlocking {
        val extraction = emptyExtraction(MatchResultScreenshotRole.MATCH_RESULT_LOWER).copy(
            rows = listOf(fakeRow(11)),
        )
        val processor = processor(
            asset = asset(),
            fieldExtractor = MatchResultOcrPreviewFieldExtractor { _, _, _, _ -> extraction },
        )

        val processed = processor.process(identity(MatchResultScreenshotRole.MATCH_RESULT_LOWER))
            as MatchResultOcrPreviewProcessingResult.Processed

        assertEquals(listOf(11), processed.extraction.rows.map { it.position })
    }

    @Test
    fun recognizerFailureReturnsRecognitionFailed() = runBlocking {
        val processor = processor(
            asset = asset(),
            recognition = MatchResultOcrPreviewRecognitionResult.RecognitionFailed,
        )

        assertSame(
            MatchResultOcrPreviewProcessingResult.RecognitionFailed,
            processor.process(identity()),
        )
    }

    @Test
    fun invalidCropReturnsInvalidCrop() = runBlocking {
        val processor = processor(
            asset = asset(crop = OcrNormalizedCropRect(0.0, 0.0, 0.05, 1.0)),
        )

        assertSame(
            MatchResultOcrPreviewProcessingResult.InvalidCrop,
            processor.process(identity()),
        )
    }

    @Test
    fun missingLocalOriginalReturnsMissingLocalOriginal() = runBlocking {
        val processor = processor(
            asset = asset(),
            file = null,
        )

        assertSame(
            MatchResultOcrPreviewProcessingResult.MissingLocalOriginal,
            processor.process(identity()),
        )
    }

    private fun processor(
        asset: MatchResultScreenshotAssetEntity? = asset(),
        recognition: MatchResultOcrPreviewRecognitionResult = MatchResultOcrPreviewRecognitionResult.Recognized(
            cropWidth = 100,
            cropHeight = 100,
            blocks = emptyList(),
        ),
        fieldExtractor: MatchResultOcrPreviewFieldExtractor = MatchResultOcrPreviewFieldExtractor {
            role, _, _, _ -> emptyExtraction(role)
        },
        repository: FakeAssetRepository = FakeAssetRepository(asset),
        file: File? = temporaryFile(),
    ): MatchResultOcrPreviewProcessor = MatchResultOcrPreviewProcessor(
        assetRepository = repository,
        localFileResolver = MatchResultOcrPreviewLocalFileResolver { file },
        recognitionSource = MatchResultOcrPreviewRecognitionSource { _, _ -> recognition },
        fieldExtractor = fieldExtractor,
    )

    private fun identity(
        role: MatchResultScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
    ) = MatchResultScreenshotIdentity(
        tournamentId = "tournament-id",
        matchId = "match-id",
        role = role,
    )

    private fun asset(
        crop: OcrNormalizedCropRect? = OcrNormalizedCropRect(0.0, 0.0, 1.0, 1.0),
    ) = MatchResultScreenshotAssetEntity(
        tournamentId = "tournament-id",
        matchId = "match-id",
        screenshotKind = OcrScreenshotKind.MATCH_RESULT.name,
        screenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER.name,
        ownerUserId = "owner-id",
        localRelativePath = "screenshots/tournament/match/result/upper/original.png",
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 1000,
        originalHeight = 800,
        byteSize = 1L,
        sha256 = "a".repeat(64),
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
        uploadFailureCode = null,
        storageBucket = null,
        storageObjectPath = null,
        cropProfileId = crop?.let { "match-result" },
        cropLeft = crop?.left,
        cropTop = crop?.top,
        cropRight = crop?.right,
        cropBottom = crop?.bottom,
        createdAt = 1L,
        updatedAt = 1L,
        preservedAt = 1L,
        uploadedAt = null,
        revision = 1L,
    )

    private fun emptyExtraction(role: MatchResultScreenshotRole) = MatchResultOcrExtractionResult(
        role = role,
        fields = emptyList(),
        rows = emptyList(),
    )

    private fun fakeRow(position: Int) = MatchResultOcrRow(
        position = position,
        source = MatchResultOcrRowSource.LOWER_ROW_A,
        placement = MatchResultOcrField(
            id = "placement",
            type = MatchResultOcrFieldType.PLACEMENT,
            position = position,
            visualRow = null,
            slot = null,
            canonicalRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
            mappedRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
            ocrText = position.toString(),
            resolvedText = position.toString(),
            status = MatchResultOcrFieldStatus.DIRECT_NUMERIC,
        ),
        playerSlots = emptyList(),
    )

    private fun temporaryFile(): File = File.createTempFile("match-result-preview", ".png").also {
        it.writeText("preview")
        it.deleteOnExit()
    }

    private data class Capture(
        val role: MatchResultScreenshotRole,
        val width: Int,
        val height: Int,
    )

    private class FakeAssetRepository(
        var asset: MatchResultScreenshotAssetEntity?,
    ) : MatchResultScreenshotAssetRepository {
        var writeCount: Int = 0

        override fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>> = flowOf(emptyList())
        override fun observeByIdentity(identity: MatchResultScreenshotIdentity): Flow<MatchResultScreenshotAssetEntity?> = flowOf(asset)
        override suspend fun getByIdentity(identity: MatchResultScreenshotIdentity): MatchResultScreenshotAssetEntity? = asset
        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>> = flowOf(emptyList())
        override suspend fun findDuplicateFingerprint(identity: MatchResultScreenshotIdentity, sha256: String): MatchResultScreenshotAssetEntity? = null
        override suspend fun saveOrReplace(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetSaveResult {
            writeCount++
            this.asset = asset
            return MatchResultScreenshotAssetSaveResult.Saved
        }
        override suspend fun markLocalMissing(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markCleanupFailure(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun persistConfirmedCrop(identity: MatchResultScreenshotIdentity, crop: OcrNormalizedCropRect, updatedAt: Long): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.Saved
        override suspend fun clearConfirmedCrop(identity: MatchResultScreenshotIdentity, updatedAt: Long): MatchResultScreenshotCropSaveResult = MatchResultScreenshotCropSaveResult.Saved
        override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) = Unit
        override suspend fun deleteByMatchId(matchId: String) = Unit
    }
}
