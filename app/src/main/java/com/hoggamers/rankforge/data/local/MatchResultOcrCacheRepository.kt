package com.hoggamers.rankforge.data.local

import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrCacheCodec
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewProcessingResult
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

data class MatchResultOcrCacheFingerprint(
    val tournamentId: String,
    val matchId: String,
    val role: MatchResultScreenshotRole,
    val screenshotSha256: String,
    val originalWidth: Int,
    val originalHeight: Int,
    val cropProfileId: String,
    val cropLeft: Double,
    val cropTop: Double,
    val cropRight: Double,
    val cropBottom: Double,
    val ocrPipelineVersion: Int,
)

interface MatchResultOcrCacheRepository {
    suspend fun read(
        fingerprint: MatchResultOcrCacheFingerprint,
    ): MatchResultOcrPreviewProcessingResult.Processed?

    suspend fun save(
        fingerprint: MatchResultOcrCacheFingerprint,
        processed: MatchResultOcrPreviewProcessingResult.Processed,
    )
}

@Singleton
class RoomMatchResultOcrCacheRepository @Inject constructor(
    private val dao: MatchResultOcrCacheDao,
    private val codec: MatchResultOcrCacheCodec,
    private val clock: Clock,
) : MatchResultOcrCacheRepository {
    override suspend fun read(
        fingerprint: MatchResultOcrCacheFingerprint,
    ): MatchResultOcrPreviewProcessingResult.Processed? {
        val cached = dao.readByMatchAndRole(
            matchId = fingerprint.matchId,
            screenshotRole = fingerprint.role.name,
        ) ?: return null
        if (cached.toFingerprint() != fingerprint) return null

        return codec.decode(cached.processedPayloadJson)
            ?.takeIf { it.isValidFor(fingerprint) }
    }

    override suspend fun save(
        fingerprint: MatchResultOcrCacheFingerprint,
        processed: MatchResultOcrPreviewProcessingResult.Processed,
    ) {
        if (!processed.isValidFor(fingerprint)) return
        dao.upsert(
            MatchResultOcrCacheEntity(
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
                processedPayloadJson = codec.encode(processed),
                cachedAt = clock.millis(),
            ),
        )
    }
}

private fun MatchResultOcrCacheEntity.toFingerprint(): MatchResultOcrCacheFingerprint? = runCatching {
    MatchResultOcrCacheFingerprint(
        tournamentId = tournamentId,
        matchId = matchId,
        role = MatchResultScreenshotRole.valueOf(screenshotRole),
        screenshotSha256 = screenshotSha256,
        originalWidth = originalWidth,
        originalHeight = originalHeight,
        cropProfileId = cropProfileId,
        cropLeft = cropLeft,
        cropTop = cropTop,
        cropRight = cropRight,
        cropBottom = cropBottom,
        ocrPipelineVersion = ocrPipelineVersion,
    )
}.getOrNull()

private fun MatchResultOcrPreviewProcessingResult.Processed.isValidFor(
    fingerprint: MatchResultOcrCacheFingerprint,
): Boolean = extraction.role == fingerprint.role &&
    cropWidth > 0 &&
    cropHeight > 0 &&
    pixelCrop.left >= 0 &&
    pixelCrop.top >= 0 &&
    pixelCrop.right > pixelCrop.left &&
    pixelCrop.bottom > pixelCrop.top
