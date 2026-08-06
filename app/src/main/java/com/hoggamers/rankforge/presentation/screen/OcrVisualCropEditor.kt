package com.hoggamers.rankforge.presentation.screen

import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationError
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfile
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidator
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing
import kotlin.math.roundToInt

private val CropHandleTouchTargetSize = 56.dp
private val CropHandleTouchTargetOffset = -(CropHandleTouchTargetSize / 2)
private val CropHandleVisibleSize = 24.dp

const val OCR_VISUAL_CROP_PREVIEW_TEST_TAG = "ocr_visual_crop_preview"
const val OCR_VISUAL_CROP_OVERLAY_TEST_TAG = "ocr_visual_crop_overlay"
const val OCR_VISUAL_CROP_MOVE_HANDLE_TEST_TAG = "ocr_visual_crop_move_handle"
const val OCR_VISUAL_CROP_HANDLE_TOP_TEST_TAG = "ocr_visual_crop_handle_top"
const val OCR_VISUAL_CROP_HANDLE_BOTTOM_TEST_TAG = "ocr_visual_crop_handle_bottom"
const val OCR_VISUAL_CROP_HANDLE_LEFT_TEST_TAG = "ocr_visual_crop_handle_left"
const val OCR_VISUAL_CROP_HANDLE_RIGHT_TEST_TAG = "ocr_visual_crop_handle_right"
const val OCR_VISUAL_CROP_ERROR_TEST_TAG = "ocr_visual_crop_error"
const val OCR_VISUAL_CROP_RESET_ACTION_TEST_TAG = "ocr_visual_crop_reset"
const val OCR_VISUAL_CROP_CONFIRM_ACTION_TEST_TAG = "ocr_visual_crop_confirm"
const val OCR_VISUAL_CROP_WIDTH_VALUE_TEST_TAG = "ocr_visual_crop_width_value"
const val OCR_VISUAL_CROP_HEIGHT_VALUE_TEST_TAG = "ocr_visual_crop_height_value"

@Composable
fun OcrVisualCropEditor(
    imageUri: String?,
    crop: OcrNormalizedCropRect,
    defaultCrop: OcrNormalizedCropRect,
    profile: OcrCropValidationProfile,
    onCropChanged: (OcrNormalizedCropRect) -> Unit,
    onConfirmCrop: () -> Unit,
    modifier: Modifier = Modifier,
    sourceImageWidth: Int? = null,
    sourceImageHeight: Int? = null,
    confirmButtonText: String = stringResource(R.string.ocr_visual_crop_confirm_action),
    previewContentDescription: String = stringResource(R.string.ocr_visual_crop_preview_description),
) {
    val validation = remember(crop, profile) {
        OcrCropValidator.validate(crop, profile)
    }
    val previewCrop = remember(crop) {
        crop.toPreviewSafeCrop()
    }
    val pixelSize = remember(previewCrop, sourceImageWidth, sourceImageHeight) {
        calculateVisualCropPixelSize(
            crop = previewCrop,
            sourceWidth = sourceImageWidth,
            sourceHeight = sourceImageHeight,
        )
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        CropPreview(
            imageUri = imageUri,
            crop = crop,
            profile = profile,
            onCropChanged = onCropChanged,
            previewContentDescription = previewContentDescription,
            sourceImageWidth = sourceImageWidth,
            sourceImageHeight = sourceImageHeight,
        )
        CropDimensionRow(pixelSize = pixelSize)
        Text(
            text = stringResource(R.string.ocr_visual_crop_instruction),
            style = MaterialTheme.typography.bodySmall,
        )
        if (validation is OcrCropValidationResult.Invalid) {
            Text(
                text = stringResource(validation.error.toVisualCropStringRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(OCR_VISUAL_CROP_ERROR_TEST_TAG),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small)) {
            OutlinedButton(
                onClick = { onCropChanged(defaultCrop) },
                modifier = Modifier
                    .weight(1f)
                    .testTag(OCR_VISUAL_CROP_RESET_ACTION_TEST_TAG),
            ) {
                Text(text = stringResource(R.string.ocr_visual_crop_reset_action))
            }
            Button(
                onClick = onConfirmCrop,
                enabled = validation is OcrCropValidationResult.Valid,
                modifier = Modifier
                    .weight(1f)
                    .testTag(OCR_VISUAL_CROP_CONFIRM_ACTION_TEST_TAG),
            ) {
                Text(text = confirmButtonText)
            }
        }
    }
}

@Composable
private fun CropDimensionRow(
    pixelSize: OcrVisualCropPixelSize?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
    ) {
        CropDimensionBox(
            label = stringResource(R.string.ocr_visual_crop_width_label),
            value = pixelSize?.width?.let {
                stringResource(R.string.ocr_visual_crop_pixel_value, it)
            } ?: stringResource(R.string.ocr_visual_crop_dimension_unavailable),
            valueTestTag = OCR_VISUAL_CROP_WIDTH_VALUE_TEST_TAG,
            modifier = Modifier.weight(1f),
        )
        CropDimensionBox(
            label = stringResource(R.string.ocr_visual_crop_height_label),
            value = pixelSize?.height?.let {
                stringResource(R.string.ocr_visual_crop_pixel_value, it)
            } ?: stringResource(R.string.ocr_visual_crop_dimension_unavailable),
            valueTestTag = OCR_VISUAL_CROP_HEIGHT_VALUE_TEST_TAG,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CropDimensionBox(
    label: String,
    value: String,
    valueTestTag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.ExtraSmall),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = RankForgeSpacing.Small,
                        vertical = RankForgeSpacing.ExtraSmall,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(valueTestTag),
                )
            }
        }
    }
}

@Composable
private fun CropPreview(
    imageUri: String?,
    crop: OcrNormalizedCropRect,
    profile: OcrCropValidationProfile,
    onCropChanged: (OcrNormalizedCropRect) -> Unit,
    previewContentDescription: String,
    sourceImageWidth: Int?,
    sourceImageHeight: Int?,
) {
    val imageAspectRatio = sourceImageAspectRatio(sourceImageWidth, sourceImageHeight)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(imageAspectRatio)
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(OCR_VISUAL_CROP_PREVIEW_TEST_TAG),
    ) {
        val previewCrop = remember(crop) {
            crop.toPreviewSafeCrop()
        }
        val latestPreviewCrop = rememberUpdatedState(previewCrop)
        val latestOnCropChanged = rememberUpdatedState(onCropChanged)
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.takeIf { it > 0f } ?: 1f
        val heightPx = with(density) { maxHeight.toPx() }.takeIf { it > 0f } ?: 1f
        if (!imageUri.isNullOrBlank()) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_XY
                        contentDescription = previewContentDescription
                    }
                },
                update = { imageView ->
                    imageView.setImageURI(Uri.parse(imageUri))
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = stringResource(R.string.ocr_visual_crop_preview_unavailable),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        val leftPx = previewCrop.left * widthPx
        val topPx = previewCrop.top * heightPx
        val cropWidthPx = previewCrop.normalizedWidth * widthPx
        val cropHeightPx = previewCrop.normalizedHeight * heightPx
        Box(
            modifier = Modifier
                .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                .size(
                    width = with(density) { cropWidthPx.toFloat().toDp() },
                    height = with(density) { cropHeightPx.toFloat().toDp() },
                )
                .border(2.dp, MaterialTheme.colorScheme.primary)
                .background(Color.Transparent)
                .testTag(OCR_VISUAL_CROP_OVERLAY_TEST_TAG),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .cropDragGesture(
                        widthPx,
                        heightPx,
                        currentCrop = { latestPreviewCrop.value },
                    ) { gestureStartCrop, totalDrag ->
                        latestOnCropChanged.value(
                            OcrVisualCropGeometry.move(
                                crop = gestureStartCrop,
                                normalizedDeltaX = (totalDrag.x / widthPx).toDouble(),
                                normalizedDeltaY = (totalDrag.y / heightPx).toDouble(),
                            ),
                        )
                    }
                    .testTag(OCR_VISUAL_CROP_MOVE_HANDLE_TEST_TAG),
            )
            CropEdgeHandle(
                alignment = Alignment.TopCenter,
                offset = IntOffset(x = 0, y = with(density) { CropHandleTouchTargetOffset.roundToPx() }),
                testTag = OCR_VISUAL_CROP_HANDLE_TOP_TEST_TAG,
                widthPx = widthPx,
                heightPx = heightPx,
                profile = profile,
                handle = OcrVisualCropResizeHandle.TOP,
                currentCrop = { latestPreviewCrop.value },
                onCropChanged = { latestOnCropChanged.value(it) },
            )
            CropEdgeHandle(
                alignment = Alignment.BottomCenter,
                offset = IntOffset(x = 0, y = with(density) { -CropHandleTouchTargetOffset.roundToPx() }),
                testTag = OCR_VISUAL_CROP_HANDLE_BOTTOM_TEST_TAG,
                widthPx = widthPx,
                heightPx = heightPx,
                profile = profile,
                handle = OcrVisualCropResizeHandle.BOTTOM,
                currentCrop = { latestPreviewCrop.value },
                onCropChanged = { latestOnCropChanged.value(it) },
            )
            CropEdgeHandle(
                alignment = Alignment.CenterStart,
                offset = IntOffset(x = with(density) { CropHandleTouchTargetOffset.roundToPx() }, y = 0),
                testTag = OCR_VISUAL_CROP_HANDLE_LEFT_TEST_TAG,
                widthPx = widthPx,
                heightPx = heightPx,
                profile = profile,
                handle = OcrVisualCropResizeHandle.LEFT,
                currentCrop = { latestPreviewCrop.value },
                onCropChanged = { latestOnCropChanged.value(it) },
            )
            CropEdgeHandle(
                alignment = Alignment.CenterEnd,
                offset = IntOffset(x = with(density) { -CropHandleTouchTargetOffset.roundToPx() }, y = 0),
                testTag = OCR_VISUAL_CROP_HANDLE_RIGHT_TEST_TAG,
                widthPx = widthPx,
                heightPx = heightPx,
                profile = profile,
                handle = OcrVisualCropResizeHandle.RIGHT,
                currentCrop = { latestPreviewCrop.value },
                onCropChanged = { latestOnCropChanged.value(it) },
            )
        }
    }
}

@Composable
private fun BoxScope.CropEdgeHandle(
    alignment: Alignment,
    offset: IntOffset,
    testTag: String,
    widthPx: Float,
    heightPx: Float,
    profile: OcrCropValidationProfile,
    handle: OcrVisualCropResizeHandle,
    currentCrop: () -> OcrNormalizedCropRect,
    onCropChanged: (OcrNormalizedCropRect) -> Unit,
) {
    Box(
        modifier = Modifier
            .align(alignment)
            .offset { offset }
            .size(CropHandleTouchTargetSize)
            .cropDragGesture(
                widthPx,
                heightPx,
                profile,
                handle,
                currentCrop = currentCrop,
            ) { gestureStartCrop, totalDrag ->
                onCropChanged(
                    OcrVisualCropGeometry.resize(
                        crop = gestureStartCrop,
                        handle = handle,
                        normalizedDeltaX = (totalDrag.x / widthPx).toDouble(),
                        normalizedDeltaY = (totalDrag.y / heightPx).toDouble(),
                        profile = profile,
                    ),
                )
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(CropHandleVisibleSize)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        )
    }
}

private fun sourceImageAspectRatio(
    sourceImageWidth: Int?,
    sourceImageHeight: Int?,
): Float {
    val width = sourceImageWidth?.takeIf { it > 0 } ?: return 1f
    val height = sourceImageHeight?.takeIf { it > 0 } ?: return 1f
    return width.toFloat() / height.toFloat()
}

private fun Modifier.cropDragGesture(
    vararg keys: Any?,
    currentCrop: () -> OcrNormalizedCropRect,
    onDrag: (OcrNormalizedCropRect, Offset) -> Unit,
): Modifier = pointerInput(*keys) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        down.consume()
        val pointerId = down.id
        val gestureStartCrop = currentCrop()
        var totalDrag = Offset.Zero
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId }
                ?: event.changes.firstOrNull { it.pressed }
                ?: break
            if (!change.pressed) {
                change.consume()
                break
            }
            val dragAmount = change.positionChange()
            change.consume()
            if (dragAmount != Offset.Zero) {
                totalDrag += dragAmount
                onDrag(gestureStartCrop, totalDrag)
            }
        }
    }
}

private fun OcrNormalizedCropRect.toPreviewSafeCrop(): OcrNormalizedCropRect {
    if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) {
        return OcrVisualCropDefaults.FullImageCrop
    }
    val safeLeft = left.coerceIn(0.0, 1.0)
    val safeTop = top.coerceIn(0.0, 1.0)
    val safeRight = right.coerceIn(0.0, 1.0)
    val safeBottom = bottom.coerceIn(0.0, 1.0)
    return if (safeRight > safeLeft && safeBottom > safeTop) {
        OcrNormalizedCropRect(
            left = safeLeft,
            top = safeTop,
            right = safeRight,
            bottom = safeBottom,
        )
    } else {
        OcrVisualCropDefaults.FullImageCrop
    }
}

private fun OcrCropValidationError.toVisualCropStringRes(): Int = when (this) {
    OcrCropValidationError.NON_FINITE_VALUE -> R.string.ocr_visual_crop_non_finite
    OcrCropValidationError.OUT_OF_BOUNDS -> R.string.ocr_visual_crop_out_of_bounds
    OcrCropValidationError.INVALID_EDGES -> R.string.ocr_visual_crop_invalid_edges
    OcrCropValidationError.TOO_SMALL -> R.string.ocr_visual_crop_too_small
    OcrCropValidationError.INVALID_IMAGE_DIMENSIONS -> R.string.ocr_visual_crop_invalid_dimensions
    OcrCropValidationError.EMPTY_PIXEL_CROP -> R.string.ocr_visual_crop_empty_pixel_crop
}
