package com.hoggamers.rankforge.data.ocr.preprocessing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.presentation.screen.LocalImagePreserver
import java.io.File
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MatchResultPreparedCropImage {
    val width: Int
    val height: Int
}

class AndroidMatchResultPreparedCropImage(
    val bitmap: Bitmap,
) : MatchResultPreparedCropImage {
    override val width: Int
        get() = bitmap.width

    override val height: Int
        get() = bitmap.height
}

data class PreparedMatchResultConfirmedCrop(
    val identity: MatchResultScreenshotIdentity,
    val image: MatchResultPreparedCropImage,
    val originalDimensions: OcrImageDimensions,
    val confirmedCrop: OcrNormalizedCropRect,
    val pixelCropInOriginal: OcrPixelCropRect,
    val cropProfileId: String,
    val sourceSha256: String,
    val sourceRevision: Long,
)

enum class MatchResultConfirmedCropPreparationFailure {
    INVALID_CONTEXT,
    ASSET_NOT_FOUND,
    ASSET_IDENTITY_MISMATCH,
    CONFIRMED_CROP_MISSING,
    CONFIRMED_CROP_INVALID,
    LOCAL_FILE_MISSING,
    LOCAL_PATH_MISMATCH,
    ORIGINAL_DIMENSIONS_MISMATCH,
    IMAGE_UNREADABLE,
    CROP_FAILED,
}

sealed interface MatchResultConfirmedCropPreparationResult {
    data class Prepared(
        val crop: PreparedMatchResultConfirmedCrop,
    ) : MatchResultConfirmedCropPreparationResult

    data class Failed(
        val failure: MatchResultConfirmedCropPreparationFailure,
    ) : MatchResultConfirmedCropPreparationResult
}

interface MatchResultConfirmedCropImageOperations {
    fun readDimensions(file: File): OcrImageDimensions?

    fun decode(file: File): MatchResultPreparedCropImage?

    fun crop(
        source: MatchResultPreparedCropImage,
        cropRect: OcrPixelCropRect,
    ): MatchResultPreparedCropImage?

    fun release(image: MatchResultPreparedCropImage)
}

@Singleton
class MatchResultConfirmedCropPreparer(
    private val assetRepository: MatchResultScreenshotAssetRepository,
    private val localImagePreserver: LocalImagePreserver,
    private val imageOperations: MatchResultConfirmedCropImageOperations,
    private val ioDispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        assetRepository: MatchResultScreenshotAssetRepository,
        localImagePreserver: LocalImagePreserver,
    ) : this(
        assetRepository = assetRepository,
        localImagePreserver = localImagePreserver,
        imageOperations = AndroidMatchResultConfirmedCropImageOperations,
        ioDispatcher = Dispatchers.IO,
    )

    suspend fun prepare(
        identity: MatchResultScreenshotIdentity,
    ): MatchResultConfirmedCropPreparationResult = withContext(ioDispatcher) {
        if (identity.tournamentId.isBlank() || identity.matchId.isBlank()) {
            return@withContext failed(MatchResultConfirmedCropPreparationFailure.INVALID_CONTEXT)
        }

        val asset = try {
            assetRepository.getByIdentity(identity)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return@withContext failed(MatchResultConfirmedCropPreparationFailure.ASSET_NOT_FOUND)
        } ?: return@withContext failed(MatchResultConfirmedCropPreparationFailure.ASSET_NOT_FOUND)

        if (asset.identityOrNull() != identity) {
            return@withContext failed(MatchResultConfirmedCropPreparationFailure.ASSET_IDENTITY_MISMATCH)
        }

        if (asset.cropProfileId != OcrCropValidationProfiles.MatchResult.id) {
            return@withContext failed(MatchResultConfirmedCropPreparationFailure.CONFIRMED_CROP_MISSING)
        }

        val confirmedCrop = asset.confirmedCropOrNull()
            ?: return@withContext failed(MatchResultConfirmedCropPreparationFailure.CONFIRMED_CROP_MISSING)
        val originalDimensions = OcrImageDimensions.from(asset.originalWidth, asset.originalHeight)
            ?: return@withContext failed(MatchResultConfirmedCropPreparationFailure.CONFIRMED_CROP_INVALID)
        val validatedCrop = when (
            val validation = OcrCropValidator.validate(
                crop = confirmedCrop,
                dimensions = originalDimensions,
                profile = OcrCropValidationProfiles.MatchResult,
            )
        ) {
            is OcrCropValidationResult.Invalid -> {
                return@withContext failed(MatchResultConfirmedCropPreparationFailure.CONFIRMED_CROP_INVALID)
            }

            is OcrCropValidationResult.Valid -> validation
        }
        val pixelCrop = validatedCrop.pixelCrop
            ?: return@withContext failed(MatchResultConfirmedCropPreparationFailure.CONFIRMED_CROP_INVALID)

        val expectedRelativePath = localImagePreserver.matchResultRelativePath(
            tournamentId = identity.tournamentId,
            matchId = identity.matchId,
            role = identity.role,
            extension = asset.fileExtension,
        )
        if (asset.localRelativePath.replace('\\', '/') != expectedRelativePath) {
            return@withContext failed(MatchResultConfirmedCropPreparationFailure.LOCAL_PATH_MISMATCH)
        }

        val file = localImagePreserver.resolveRelativePath(asset.localRelativePath)
            ?: return@withContext failed(MatchResultConfirmedCropPreparationFailure.LOCAL_FILE_MISSING)
        if (!file.isFile) {
            return@withContext failed(MatchResultConfirmedCropPreparationFailure.LOCAL_FILE_MISSING)
        }

        val decodedDimensions = try {
            imageOperations.readDimensions(file)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            null
        } ?: return@withContext failed(MatchResultConfirmedCropPreparationFailure.IMAGE_UNREADABLE)
        if (decodedDimensions != originalDimensions) {
            return@withContext failed(MatchResultConfirmedCropPreparationFailure.ORIGINAL_DIMENSIONS_MISMATCH)
        }

        val originalImage = try {
            imageOperations.decode(file)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            null
        } ?: return@withContext failed(MatchResultConfirmedCropPreparationFailure.IMAGE_UNREADABLE)

        if (originalImage.width != originalDimensions.width || originalImage.height != originalDimensions.height) {
            imageOperations.release(originalImage)
            return@withContext failed(MatchResultConfirmedCropPreparationFailure.ORIGINAL_DIMENSIONS_MISMATCH)
        }

        val croppedImage = try {
            imageOperations.crop(originalImage, pixelCrop)
        } catch (cancellation: CancellationException) {
            imageOperations.release(originalImage)
            throw cancellation
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: RuntimeException) {
            null
        }
        imageOperations.release(originalImage)

        if (croppedImage == null ||
            croppedImage.width != pixelCrop.width ||
            croppedImage.height != pixelCrop.height
        ) {
            croppedImage?.let(imageOperations::release)
            return@withContext failed(MatchResultConfirmedCropPreparationFailure.CROP_FAILED)
        }

        MatchResultConfirmedCropPreparationResult.Prepared(
            PreparedMatchResultConfirmedCrop(
                identity = identity,
                image = croppedImage,
                originalDimensions = originalDimensions,
                confirmedCrop = confirmedCrop,
                pixelCropInOriginal = pixelCrop,
                cropProfileId = asset.cropProfileId,
                sourceSha256 = asset.sha256,
                sourceRevision = asset.revision,
            ),
        )
    }

    private fun failed(
        failure: MatchResultConfirmedCropPreparationFailure,
    ): MatchResultConfirmedCropPreparationResult.Failed =
        MatchResultConfirmedCropPreparationResult.Failed(failure)
}

private fun com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity.confirmedCropOrNull(): OcrNormalizedCropRect? {
    val left = cropLeft ?: return null
    val top = cropTop ?: return null
    val right = cropRight ?: return null
    val bottom = cropBottom ?: return null
    return OcrNormalizedCropRect(left = left, top = top, right = right, bottom = bottom)
}

private object AndroidMatchResultConfirmedCropImageOperations : MatchResultConfirmedCropImageOperations {
    override fun readDimensions(file: File): OcrImageDimensions? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return OcrImageDimensions.from(options.outWidth, options.outHeight)
    }

    override fun decode(file: File): MatchResultPreparedCropImage? =
        BitmapFactory.decodeFile(file.absolutePath)
            ?.takeIf { bitmap -> !bitmap.isRecycled }
            ?.let(::AndroidMatchResultPreparedCropImage)

    override fun crop(
        source: MatchResultPreparedCropImage,
        cropRect: OcrPixelCropRect,
    ): MatchResultPreparedCropImage? {
        val bitmap = (source as? AndroidMatchResultPreparedCropImage)
            ?.bitmap
            ?.takeIf { !it.isRecycled }
            ?: return null
        return AndroidMatchResultPreparedCropImage(
            Bitmap.createBitmap(
                bitmap,
                cropRect.left,
                cropRect.top,
                cropRect.width,
                cropRect.height,
            ),
        )
    }

    override fun release(image: MatchResultPreparedCropImage) {
        (image as? AndroidMatchResultPreparedCropImage)
            ?.bitmap
            ?.takeIf { !it.isRecycled }
            ?.recycle()
    }
}
