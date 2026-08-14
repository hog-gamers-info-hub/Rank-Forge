package com.hoggamers.rankforge.data.local

import com.hoggamers.rankforge.data.ocr.matchresult.MATCH_RESULT_OCR_CACHE_PIPELINE_VERSION
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrCacheCodec
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewProcessingResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchResultOcrCacheRepositoryTest {
    private val codec = MatchResultOcrCacheCodec()
    private val dao = FakeDao()
    private val repository = RoomMatchResultOcrCacheRepository(
        dao = dao,
        codec = codec,
        clock = Clock.fixed(Instant.ofEpochMilli(1234L), ZoneOffset.UTC),
    )

    @Test
    fun readRequiresExactFingerprintAndMatchingPayloadRole() = runBlocking {
        val fingerprint = fingerprint()
        val expected = processed(fingerprint.role)
        dao.stored = entity(fingerprint, codec.encode(expected))

        assertEquals(expected, repository.read(fingerprint))
        assertNull(repository.read(fingerprint.copy(screenshotSha256 = "b".repeat(64))))
        assertNull(repository.read(fingerprint.copy(cropLeft = 0.1)))
        assertNull(repository.read(fingerprint.copy(ocrPipelineVersion = 0)))

        dao.stored = entity(
            fingerprint,
            codec.encode(processed(MatchResultScreenshotRole.MATCH_RESULT_LOWER)),
        )
        assertNull(repository.read(fingerprint))
    }

    @Test
    fun savePersistsFingerprintPayloadAndTimestamp() = runBlocking {
        val fingerprint = fingerprint()
        val expected = processed(fingerprint.role)

        repository.save(fingerprint, expected)

        val stored = dao.stored!!
        assertEquals(fingerprint.matchId, stored.matchId)
        assertEquals(fingerprint.role.name, stored.screenshotRole)
        assertEquals(fingerprint.screenshotSha256, stored.screenshotSha256)
        assertEquals(1234L, stored.cachedAt)
        assertEquals(expected, codec.decode(stored.processedPayloadJson))
    }

    private fun fingerprint(
        role: MatchResultScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
    ) = MatchResultOcrCacheFingerprint(
        tournamentId = "tournament-1",
        matchId = "match-1",
        role = role,
        screenshotSha256 = "a".repeat(64),
        originalWidth = 1000,
        originalHeight = 800,
        cropProfileId = "match-result",
        cropLeft = 0.0,
        cropTop = 0.0,
        cropRight = 1.0,
        cropBottom = 1.0,
        ocrPipelineVersion = MATCH_RESULT_OCR_CACHE_PIPELINE_VERSION,
    )

    private fun entity(
        fingerprint: MatchResultOcrCacheFingerprint,
        payload: String,
    ) = MatchResultOcrCacheEntity(
        tournamentId = fingerprint.tournamentId,
        matchId = fingerprint.matchId,
        screenshotRole = fingerprint.role.name,
        screenshotSha256 = fingerprint.screenshotSha256,
        originalWidth = fingerprint.originalWidth,
        originalHeight = fingerprint.originalHeight,
        cropProfileId = fingerprint.cropProfileId,
        cropLeft = fingerprint.cropLeft,
        cropTop = fingerprint.cropTop,
        cropRight = fingerprint.cropRight,
        cropBottom = fingerprint.cropBottom,
        ocrPipelineVersion = fingerprint.ocrPipelineVersion,
        processedPayloadJson = payload,
        cachedAt = 1L,
    )

    private fun processed(role: MatchResultScreenshotRole) =
        MatchResultOcrPreviewProcessingResult.Processed(
            extraction = MatchResultOcrExtractionResult(role, emptyList(), emptyList()),
            pixelCrop = OcrPixelCropRect(0, 0, 100, 100),
            cropWidth = 100,
            cropHeight = 100,
        )

    private class FakeDao : MatchResultOcrCacheDao {
        var stored: MatchResultOcrCacheEntity? = null

        override suspend fun readByMatchAndRole(
            matchId: String,
            screenshotRole: String,
        ): MatchResultOcrCacheEntity? = stored

        override suspend fun upsert(cache: MatchResultOcrCacheEntity) {
            stored = cache
        }

        override suspend fun deleteByMatchAndRole(matchId: String, screenshotRole: String) {
            stored = null
        }
    }
}
