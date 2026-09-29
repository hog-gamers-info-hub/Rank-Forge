package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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

private const val MIN_LOGO_WIDTH_RATIO = 0.03f

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
                                            placement = clampPointTableLogoPlacement(
                                                placement = placement.copy(
                                                    centerXRatio = placement.centerXRatio +
                                                        pan.x / containerSize.width.toFloat(),
                                                    centerYRatio = placement.centerYRatio +
                                                        pan.y / containerSize.height.toFloat(),
                                                    widthRatio = placement.widthRatio * zoom,
                                                ),
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
                                    val logoWidthPx = placement.widthRatio * containerSize.width
                                    val logoHeightPx = logoWidthPx / logoAspectRatio
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
                                                    (
                                                        placement.centerXRatio * containerSize.width -
                                                            logoWidthPx / 2f
                                                        ).roundToInt(),
                                                    (
                                                        placement.centerYRatio * containerSize.height -
                                                            logoHeightPx / 2f
                                                        ).roundToInt(),
                                                )
                                            },
                                    )
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
