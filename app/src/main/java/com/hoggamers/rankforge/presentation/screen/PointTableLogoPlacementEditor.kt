package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PointTableLogoEditorState(
    val tournamentId: String,
    val designKey: String,
    val previewPngBytes: ByteArray,
    val logoDisplayUri: String,
    val previousLogoPath: String?,
    val candidate: com.hoggamers.rankforge.data.local.PointTableLogoCandidate?,
    val initialPlacement: com.hoggamers.rankforge.data.local.PointTableLogoPlacement?,
)

data class PointTableLogoPlacementGeometry(
    val centerXRatio: Float,
    val centerYRatio: Float,
    val widthRatio: Float,
)

data class PointTableLogoBounds(
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float,
) {
    val widthPx: Float get() = rightPx - leftPx
    val heightPx: Float get() = bottomPx - topPx
}

data class PointTableLogoResizeHandle(
    val centerXPx: Float,
    val centerYPx: Float,
    val radiusPx: Float,
)

private const val MIN_LOGO_WIDTH_RATIO = 0.03f
private val LOGO_SELECTION_COLOR = Color(0xFFDDE2EB)
private val LOGO_RESIZE_ARROW_COLOR = Color(0xFF263238)
private const val RESIZE_HANDLE_DIAMETER_DP = 24f
private const val RESIZE_HANDLE_OUTWARD_OFFSET_DP = 2f
private const val RESIZE_ARROW_LENGTH_DP = 12f
private const val RESIZE_ARROW_STROKE_DP = 1.5f
private const val RESIZE_ARROW_HEAD_DP = 3f

fun defaultPointTableLogoPlacement(
    designAspectRatio: Float,
    logoAspectRatio: Float,
): PointTableLogoPlacementGeometry = clampPointTableLogoPlacement(
    placement = PointTableLogoPlacementGeometry(0.5f, 0.5f, 0.20f),
    designAspectRatio = designAspectRatio,
    logoAspectRatio = logoAspectRatio,
)

fun clampPointTableLogoPlacement(
    placement: PointTableLogoPlacementGeometry,
    designAspectRatio: Float,
    logoAspectRatio: Float,
): PointTableLogoPlacementGeometry {
    if (!designAspectRatio.isFinite() || designAspectRatio <= 0f ||
        !logoAspectRatio.isFinite() || logoAspectRatio <= 0f
    ) {
        return placement
    }
    val maxWidthRatio = (designAspectRatio * logoAspectRatio).coerceAtMost(1f)
    val minWidthRatio = MIN_LOGO_WIDTH_RATIO.coerceAtMost(maxWidthRatio)
    val widthRatio = placement.widthRatio
        .takeIf(Float::isFinite)
        ?.coerceIn(minWidthRatio, maxWidthRatio)
        ?: minWidthRatio
    val halfWidthRatio = widthRatio / 2f
    val halfHeightRatio = widthRatio / (designAspectRatio * logoAspectRatio * 2f)
    return PointTableLogoPlacementGeometry(
        centerXRatio = clampCenter(placement.centerXRatio, halfWidthRatio),
        centerYRatio = clampCenter(placement.centerYRatio, halfHeightRatio),
        widthRatio = widthRatio,
    )
}

private fun clampCenter(center: Float, halfSize: Float): Float {
    if (!center.isFinite()) return 0.5f
    if (halfSize >= 0.5f) return 0.5f
    return center.coerceIn(halfSize, 1f - halfSize)
}

fun pointTableLogoBounds(
    placement: PointTableLogoPlacementGeometry,
    containerWidthPx: Float,
    containerHeightPx: Float,
    logoAspectRatio: Float,
): PointTableLogoBounds {
    val logoWidthPx = placement.widthRatio * containerWidthPx
    val logoHeightPx = logoWidthPx / logoAspectRatio
    return PointTableLogoBounds(
        leftPx = placement.centerXRatio * containerWidthPx - logoWidthPx / 2f,
        topPx = placement.centerYRatio * containerHeightPx - logoHeightPx / 2f,
        rightPx = placement.centerXRatio * containerWidthPx + logoWidthPx / 2f,
        bottomPx = placement.centerYRatio * containerHeightPx + logoHeightPx / 2f,
    )
}

fun pointTableLogoResizeHandle(
    logoBounds: PointTableLogoBounds,
    handleDiameterPx: Float,
    outwardOffsetPx: Float = 0f,
): PointTableLogoResizeHandle {
    val radiusPx = handleDiameterPx / 2f
    return PointTableLogoResizeHandle(
        centerXPx = logoBounds.rightPx + radiusPx + outwardOffsetPx,
        centerYPx = logoBounds.bottomPx + radiusPx + outwardOffsetPx,
        radiusPx = radiusPx,
    )
}

fun isPointTableLogoResizeHandleHit(
    pointerX: Float,
    pointerY: Float,
    handle: PointTableLogoResizeHandle,
): Boolean {
    val deltaX = pointerX - handle.centerXPx
    val deltaY = pointerY - handle.centerYPx
    return deltaX * deltaX + deltaY * deltaY <= handle.radiusPx * handle.radiusPx
}

fun resizePointTableLogoFromHandleDrag(
    placement: PointTableLogoPlacementGeometry,
    dragX: Float,
    dragY: Float,
    containerWidthPx: Float,
    designAspectRatio: Float,
    logoAspectRatio: Float,
): PointTableLogoPlacementGeometry {
    if (!containerWidthPx.isFinite() || containerWidthPx <= 0f ||
        !logoAspectRatio.isFinite() || logoAspectRatio <= 0f
    ) {
        return placement
    }
    val heightPerWidth = 1f / logoAspectRatio
    val widthDeltaPx = 2f * (dragX + dragY * heightPerWidth) /
        (1f + heightPerWidth * heightPerWidth)
    return clampPointTableLogoPlacement(
        placement = placement.copy(
            widthRatio = placement.widthRatio + widthDeltaPx / containerWidthPx,
        ),
        designAspectRatio = designAspectRatio,
        logoAspectRatio = logoAspectRatio,
    )
}

fun transformPointTableLogoPlacement(
    placement: PointTableLogoPlacementGeometry,
    panX: Float,
    panY: Float,
    zoom: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
    designAspectRatio: Float,
    logoAspectRatio: Float,
): PointTableLogoPlacementGeometry {
    if (!containerWidthPx.isFinite() || containerWidthPx <= 0f ||
        !containerHeightPx.isFinite() || containerHeightPx <= 0f
    ) {
        return placement
    }
    return clampPointTableLogoPlacement(
        placement = placement.copy(
            centerXRatio = placement.centerXRatio + panX / containerWidthPx,
            centerYRatio = placement.centerYRatio + panY / containerHeightPx,
            widthRatio = placement.widthRatio * zoom,
        ),
        designAspectRatio = designAspectRatio,
        logoAspectRatio = logoAspectRatio,
    )
}

@Composable
fun PointTableLogoPlacementEditor(
    state: PointTableLogoEditorState,
    onCancel: () -> Unit,
    onSave: (PointTableLogoPlacementGeometry) -> Unit,
    isSaving: Boolean,
    errorMessage: String? = null,
) {
    val previewBitmap by produceState<Bitmap?>(initialValue = null, state.previewPngBytes) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(
                state.previewPngBytes,
                0,
                state.previewPngBytes.size,
            )
        }
    }
    val logoBitmap by produceState<Bitmap?>(initialValue = null, state.logoDisplayUri) {
        value = withContext(Dispatchers.IO) {
            Uri.parse(state.logoDisplayUri).path?.let(BitmapFactory::decodeFile)
        }
    }
    val currentPreviewBitmap = previewBitmap
    val currentLogoBitmap = logoBitmap
    // These bitmaps are UI-owned. Do not recycle them from composition disposal: Compose's
    // BitmapPainter may still hold a draw reference during disposal. They become eligible for
    // collection when this composition no longer retains them.

    BackHandler(onBack = onCancel)
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF071B3E),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onCancel,
                        enabled = !isSaving,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFF91AFE0),
                        ),
                    ) {
                        Text("Cancel")
                    }
                    Text(
                        text = "Organisation Logo",
                        color = Color(0xFFF6F8FF),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.width(72.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (currentPreviewBitmap == null || currentLogoBitmap == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = errorMessage ?: "Unable to load the selected logo.",
                            color = Color(0xFFF6F8FF),
                        )
                    }
                } else {
                    val design = currentPreviewBitmap
                    val logo = currentLogoBitmap
                    checkNotNull(design)
                    checkNotNull(logo)
                    val designAspectRatio = design.width.toFloat() / design.height.toFloat()
                    val logoAspectRatio = logo.width.toFloat() / logo.height.toFloat()
                    var placement by remember(
                        state.designKey,
                        state.logoDisplayUri,
                        design.width,
                        design.height,
                        logo.width,
                        logo.height,
                    ) {
                        mutableStateOf(
                            state.initialPlacement?.let {
                                PointTableLogoPlacementGeometry(
                                    centerXRatio = it.centerXRatio,
                                    centerYRatio = it.centerYRatio,
                                    widthRatio = it.widthRatio,
                                )
                            }?.let {
                                clampPointTableLogoPlacement(it, designAspectRatio, logoAspectRatio)
                            } ?: defaultPointTableLogoPlacement(designAspectRatio, logoAspectRatio),
                        )
                    }
                    val density = LocalDensity.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val maxWidthPx = with(density) { maxWidth.toPx() }
                            val maxHeightPx = with(density) { maxHeight.toPx() }
                            val widthPx = minOf(maxWidthPx, maxHeightPx * designAspectRatio)
                            val heightPx = widthPx / designAspectRatio
                            val widthDp = with(density) { widthPx.toDp() }
                            val heightDp = with(density) { heightPx.toDp() }
                            var containerSize by remember { mutableStateOf(IntSize.Zero) }
                            Box(
                                modifier = Modifier
                                    .size(widthDp, heightDp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .onSizeChanged { containerSize = it }
                                    .pointerInput(containerSize, logoAspectRatio, designAspectRatio) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            if (containerSize.width <= 0 || containerSize.height <= 0) return@detectTransformGestures
                                            placement = transformPointTableLogoPlacement(
                                                placement = placement.copy(
                                                    centerXRatio = placement.centerXRatio,
                                                    centerYRatio = placement.centerYRatio,
                                                    widthRatio = placement.widthRatio,
                                                ),
                                                panX = pan.x,
                                                panY = pan.y,
                                                zoom = zoom,
                                                containerWidthPx = containerSize.width.toFloat(),
                                                containerHeightPx = containerSize.height.toFloat(),
                                                designAspectRatio = designAspectRatio,
                                                logoAspectRatio = logoAspectRatio,
                                            )
                                        }
                                    },
                            ) {
                                Image(
                                    bitmap = design.asImageBitmap(),
                                    contentDescription = "Current result preview",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (containerSize.width > 0 && containerSize.height > 0) {
                                    val logoBounds = pointTableLogoBounds(
                                        placement = placement,
                                        containerWidthPx = containerSize.width.toFloat(),
                                        containerHeightPx = containerSize.height.toFloat(),
                                        logoAspectRatio = logoAspectRatio,
                                    )
                                    val logoWidthPx = logoBounds.widthPx
                                    val logoHeightPx = logoBounds.heightPx
                                    val logoWidthDp = with(density) { logoWidthPx.toDp() }
                                    val logoHeightDp = with(density) { logoHeightPx.toDp() }
                                    Image(
                                        bitmap = logo.asImageBitmap(),
                                        contentDescription = "Organisation Logo",
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier
                                            .size(logoWidthDp, logoHeightDp)
                                            .offset {
                                                IntOffset(
                                                    logoBounds.leftPx.roundToInt(),
                                                    logoBounds.topPx.roundToInt(),
                                                )
                                            },
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(logoWidthDp, logoHeightDp)
                                            .offset {
                                                IntOffset(
                                                    logoBounds.leftPx.roundToInt(),
                                                    logoBounds.topPx.roundToInt(),
                                                )
                                            }
                                            .border(1.dp, LOGO_SELECTION_COLOR),
                                    )
                                    val handleDiameterDp = RESIZE_HANDLE_DIAMETER_DP.dp
                                    val handleOutwardOffsetDp = RESIZE_HANDLE_OUTWARD_OFFSET_DP.dp
                                    val handle = pointTableLogoResizeHandle(
                                        logoBounds = logoBounds,
                                        handleDiameterPx = with(density) { handleDiameterDp.toPx() },
                                        outwardOffsetPx = with(density) { handleOutwardOffsetDp.toPx() },
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(handleDiameterDp)
                                            .offset {
                                                IntOffset(
                                                    (handle.centerXPx - handle.radiusPx).roundToInt(),
                                                    (handle.centerYPx - handle.radiusPx).roundToInt(),
                                                )
                                            }
                                            .pointerInput(containerSize, logoAspectRatio, designAspectRatio) {
                                                awaitEachGesture {
                                                    val down = awaitFirstDown(
                                                        requireUnconsumed = false,
                                                        pass = PointerEventPass.Initial,
                                                    )
                                                    val localHandle = PointTableLogoResizeHandle(
                                                        centerXPx = size.width / 2f,
                                                        centerYPx = size.height / 2f,
                                                        radiusPx = minOf(size.width, size.height) / 2f,
                                                    )
                                                    if (!isPointTableLogoResizeHandleHit(
                                                            pointerX = down.position.x,
                                                            pointerY = down.position.y,
                                                            handle = localHandle,
                                                        )
                                                    ) {
                                                        return@awaitEachGesture
                                                    }
                                                    down.consume()
                                                    val pointerId = down.id
                                                    while (true) {
                                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                                        val change = event.changes.firstOrNull { it.id == pointerId }
                                                            ?: break
                                                        if (!change.pressed) {
                                                            change.consume()
                                                            break
                                                        }
                                                        val dragAmount = change.positionChange()
                                                        change.consume()
                                                        if (dragAmount != Offset.Zero) {
                                                            placement = resizePointTableLogoFromHandleDrag(
                                                                placement = placement,
                                                                dragX = dragAmount.x,
                                                                dragY = dragAmount.y,
                                                                containerWidthPx = containerSize.width.toFloat(),
                                                                designAspectRatio = designAspectRatio,
                                                                logoAspectRatio = logoAspectRatio,
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.White, CircleShape),
                                        )
                                        Canvas(modifier = Modifier.size(24.dp)) {
                                            val arrowLengthPx = RESIZE_ARROW_LENGTH_DP.dp.toPx()
                                            val strokeWidthPx = RESIZE_ARROW_STROKE_DP.dp.toPx()
                                            val arrowHeadPx = RESIZE_ARROW_HEAD_DP.dp.toPx()
                                            val centerPx = size.width / 2f
                                            val start = Offset(
                                                centerPx - arrowLengthPx / 2f,
                                                centerPx - arrowLengthPx / 2f,
                                            )
                                            val end = Offset(
                                                centerPx + arrowLengthPx / 2f,
                                                centerPx + arrowLengthPx / 2f,
                                            )
                                            drawLine(
                                                color = LOGO_RESIZE_ARROW_COLOR,
                                                start = start,
                                                end = end,
                                                strokeWidth = strokeWidthPx,
                                                cap = StrokeCap.Round,
                                            )
                                            drawLine(
                                                color = LOGO_RESIZE_ARROW_COLOR,
                                                start = start,
                                                end = Offset(start.x + arrowHeadPx, start.y),
                                                strokeWidth = strokeWidthPx,
                                                cap = StrokeCap.Round,
                                            )
                                            drawLine(
                                                color = LOGO_RESIZE_ARROW_COLOR,
                                                start = start,
                                                end = Offset(start.x, start.y + arrowHeadPx),
                                                strokeWidth = strokeWidthPx,
                                                cap = StrokeCap.Round,
                                            )
                                            drawLine(
                                                color = LOGO_RESIZE_ARROW_COLOR,
                                                start = end,
                                                end = Offset(end.x - arrowHeadPx, end.y),
                                                strokeWidth = strokeWidthPx,
                                                cap = StrokeCap.Round,
                                            )
                                            drawLine(
                                                color = LOGO_RESIZE_ARROW_COLOR,
                                                start = end,
                                                end = Offset(end.x, end.y - arrowHeadPx),
                                                strokeWidth = strokeWidthPx,
                                                cap = StrokeCap.Round,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = Color(0xFFFFB4AB),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onSave(placement) },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF17C9F2),
                            contentColor = Color(0xFF071B3E),
                        ),
                    ) {
                        Text(if (isSaving) "Saving…" else "Save Logo")
                    }
                }
            }
        }
    }
}

@Composable
fun PointTableLogoThumbnail(displayUri: String) {
    val bitmap by produceState<Bitmap?>(initialValue = null, displayUri) {
        value = withContext(Dispatchers.IO) {
            Uri.parse(displayUri).path?.let(BitmapFactory::decodeFile)
        }
    }
    val currentBitmap = bitmap
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0B2B55)),
        contentAlignment = Alignment.Center,
    ) {
        currentBitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Organisation Logo thumbnail",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}
