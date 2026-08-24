package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.data.local.MatchResultOcrCacheFingerprint
import com.hoggamers.rankforge.data.local.MatchResultOcrCacheRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.presentation.screen.ScreenshotOwnerProvider
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import kotlinx.coroutines.CancellationException

/**
 * Increment this value whenever preprocessing, ML Kit recognition, canonical layout interpretation,
 * field extraction, OCR normalization, or other behavior changes in a way that could alter
 * MatchResultOcrExtractionResult.
 */
const val MATCH_RESULT_OCR_CACHE_PIPELINE_VERSION = 1

class CachingMatchResultOcrPreviewRunner(
    private val assetRepository: MatchResultScreenshotAssetRepository,
    private val cacheRepository: MatchResultOcrCacheRepository,
    private val screenshotOwnerProvider: ScreenshotOwnerProvider,
    private val delegate: MatchResultOcrPreviewRunner,
) : MatchResultOcrPreviewRunner {
    override suspend fun process(
        identity: MatchResultScreenshotIdentity,
    ): MatchResultOcrPreviewProcessingResult {
        val ownerUserId = screenshotOwnerProvider.currentOwnerUserId()?.takeIf { it.isNotBlank() }
            ?: return MatchResultOcrPreviewProcessingResult.MissingAsset
        val fingerprintBefore = readFingerprint(identity, ownerUserId)
        if (fingerprintBefore != null) {
            val cached = try {
                cacheRepository.readByOwner(fingerprintBefore, ownerUserId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (
                cached != null &&
                cached.isValidFor(fingerprintBefore) &&
                readFingerprint(identity, ownerUserId) == fingerprintBefore
            ) {
                return cached
            }
        }

        val fresh = delegate.process(identity)
        if (fresh is MatchResultOcrPreviewProcessingResult.Processed && fingerprintBefore != null) {
            if (readFingerprint(identity, ownerUserId) == fingerprintBefore) {
                try {
                    cacheRepository.saveByOwner(fingerprintBefore, fresh, ownerUserId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    // Cache persistence is an optimization and must not block OCR Review.
                }
            }
        }
        return fresh
    }

    private suspend fun readFingerprint(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
    ): MatchResultOcrCacheFingerprint? = try {
        assetRepository.getByIdentityAndOwner(identity, ownerUserId)
            ?.toMatchResultOcrCacheFingerprint(identity)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }
}

private fun MatchResultOcrPreviewProcessingResult.Processed.isValidFor(
    fingerprint: MatchResultOcrCacheFingerprint,
): Boolean = extraction.role == fingerprint.role &&
    cropWidth > 0 &&
    cropHeight > 0 &&
    pixelCrop.left >= 0 &&
    pixelCrop.top >= 0 &&
    pixelCrop.right > pixelCrop.left &&
    pixelCrop.bottom > pixelCrop.top

fun MatchResultScreenshotAssetEntity.toMatchResultOcrCacheFingerprint(
    identity: MatchResultScreenshotIdentity,
    pipelineVersion: Int = MATCH_RESULT_OCR_CACHE_PIPELINE_VERSION,
): MatchResultOcrCacheFingerprint? {
    if (identityOrNull() != identity) return null
    if (sha256.isBlank() || originalWidth <= 0 || originalHeight <= 0) return null
    val profileId = cropProfileId?.takeIf { it.isNotBlank() } ?: return null
    val left = cropLeft ?: return null
    val top = cropTop ?: return null
    val right = cropRight ?: return null
    val bottom = cropBottom ?: return null
    if (!listOf(left, top, right, bottom).all(Double::isFinite)) return null
    if (right <= left || bottom <= top) return null

    return MatchResultOcrCacheFingerprint(
        tournamentId = identity.tournamentId,
        matchId = identity.matchId,
        role = identity.role,
        screenshotSha256 = sha256,
        originalWidth = originalWidth,
        originalHeight = originalHeight,
        cropProfileId = profileId,
        cropLeft = left,
        cropTop = top,
        cropRight = right,
        cropBottom = bottom,
        ocrPipelineVersion = pipelineVersion,
    )
}
