package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LOCAL_SCREENSHOT_PREVIEW_MAX_LONG_EDGE_PX = 1024

internal data class LocalScreenshotPreviewPixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top
}

internal fun normalizedCropToLocalScreenshotPreviewPixelRect(
    crop: OcrNormalizedCropRect,
    sourceWidth: Int,
    sourceHeight: Int,
): LocalScreenshotPreviewPixelRect? =
    OcrImageDimensions.from(sourceWidth, sourceHeight)
        ?.let(crop::toPixelRectOrNull)
        ?.toPreviewPixelRect()

private fun OcrPixelCropRect.toPreviewPixelRect() = LocalScreenshotPreviewPixelRect(
    left = left,
    top = top,
    right = right,
    bottom = bottom,
)

internal fun calculateLocalScreenshotPreviewSampleSize(
    width: Int,
    height: Int,
    maxLongEdgePx: Int = LOCAL_SCREENSHOT_PREVIEW_MAX_LONG_EDGE_PX,
): Int {
    if (width <= 0 || height <= 0 || maxLongEdgePx <= 0) return 0
    val longEdge = max(width, height)
    var sampleSize = 1
    while (longEdge.toLong() > maxLongEdgePx.toLong() * sampleSize) {
        sampleSize *= 2
    }
    return sampleSize
}

internal fun calculateLocalScreenshotPreviewAspectRatio(
    crop: LocalScreenshotPreviewPixelRect,
): Double? = crop.takeIf { it.width > 0 && it.height > 0 }?.let {
    it.width.toDouble() / it.height.toDouble()
}

private data class LocalScreenshotPreviewDecodeResult(
    val bitmap: Bitmap?,
    val aspectRatio: Float?,
)

@Composable
fun LocalScreenshotPreview(
    imageUri: String,
    crop: OcrNormalizedCropRect?,
    contentDescription: String,
    sourceImageWidth: Int? = null,
    sourceImageHeight: Int? = null,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val decoded by produceState<LocalScreenshotPreviewDecodeResult?>(
        initialValue = null,
        imageUri,
        crop?.left,
        crop?.top,
        crop?.right,
        crop?.bottom,
        sourceImageWidth,
        sourceImageHeight,
    ) {
        value = withContext(Dispatchers.IO) {
            decodeLocalScreenshotPreview(
                imageUri = imageUri,
                crop = crop,
                sourceImageWidth = sourceImageWidth,
                sourceImageHeight = sourceImageHeight,
            )
        }
    }
    val previewModifier = modifier
        .fillMaxWidth()
        .heightIn(max = 320.dp)
        .let { current ->
            decoded?.aspectRatio?.let(current::aspectRatio) ?: current
        }
    val taggedModifier = if (testTag == null) previewModifier else previewModifier.testTag(testTag)
    val bitmap = decoded?.bitmap
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = taggedModifier,
        )
    } else {
        Text(
            text = stringResource(R.string.local_screenshot_preview_unavailable),
            color = MaterialTheme.colorScheme.error,
            modifier = taggedModifier,
        )
    }
}

private fun decodeLocalScreenshotPreview(
    imageUri: String,
    crop: OcrNormalizedCropRect?,
    sourceImageWidth: Int?,
    sourceImageHeight: Int?,
): LocalScreenshotPreviewDecodeResult? = try {
    if (crop == null) return null
    val uri = Uri.parse(imageUri)
    if (uri.scheme != "file") return null
    val path = uri.path ?: return null
    val file = File(path)
    if (!file.isFile || !file.canRead() || file.length() <= 0L) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val actualWidth = bounds.outWidth
    val actualHeight = bounds.outHeight
    val dimensions = OcrImageDimensions.from(actualWidth, actualHeight) ?: return null
    val pixelCrop = normalizedCropToLocalScreenshotPreviewPixelRect(
        crop = crop,
        sourceWidth = dimensions.width,
        sourceHeight = dimensions.height,
    ) ?: return null
    val sampleSize = calculateLocalScreenshotPreviewSampleSize(pixelCrop.width, pixelCrop.height)
    if (sampleSize == 0) return null

    val decoder = BitmapRegionDecoder.newInstance(file.path, false)
    try {
        val bitmap = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }.let { options -> decoder.decodeRegion(pixelCrop.toAndroidRect(), options) }
            ?: return null
        val aspectRatio = calculateLocalScreenshotPreviewAspectRatio(pixelCrop)?.toFloat()
        LocalScreenshotPreviewDecodeResult(bitmap, aspectRatio)
    } finally {
        decoder.recycle()
    }
} catch (_: IOException) {
    null
} catch (_: IllegalArgumentException) {
    null
} catch (_: RuntimeException) {
    null
} catch (_: OutOfMemoryError) {
    null
}

private fun LocalScreenshotPreviewPixelRect.toAndroidRect() = Rect(left, top, right, bottom)
