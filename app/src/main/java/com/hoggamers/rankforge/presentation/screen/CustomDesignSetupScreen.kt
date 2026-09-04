package com.hoggamers.rankforge.presentation.screen

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignGridGeometry
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val CUSTOM_DESIGN_SETUP_SCREEN_TEST_TAG = "custom_design_setup_screen"
const val CUSTOM_DESIGN_TEAM_NAME_FIELD_TEST_TAG = "custom_design_team_name_field"
const val CUSTOM_DESIGN_WIN_FIELD_TEST_TAG = "custom_design_win_field"
const val CUSTOM_DESIGN_TOTAL_KILLS_FIELD_TEST_TAG = "custom_design_total_kills_field"
const val CUSTOM_DESIGN_POSITION_POINTS_FIELD_TEST_TAG = "custom_design_position_points_field"
const val CUSTOM_DESIGN_TOTAL_POINTS_FIELD_TEST_TAG = "custom_design_total_points_field"
const val CUSTOM_DESIGN_UPLOAD_ACTION_TEST_TAG = "custom_design_upload_action"
const val CUSTOM_DESIGN_IMAGE_PREVIEW_TEST_TAG = "custom_design_image_preview"
const val CUSTOM_DESIGN_GRID_OVERLAY_TEST_TAG = "custom_design_grid_overlay"
const val CUSTOM_DESIGN_IMAGE_ERROR_TEST_TAG = "custom_design_image_error"

@Composable
fun CustomDesignSetupRoute(
    onBack: () -> Unit,
    viewModel: CustomDesignSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { selectedUri ->
            viewModel.onPhotoPickerResult(selectedUri?.toString())
        },
    )

    LaunchedEffect(uiState.isPhotoPickerLaunchPending) {
        if (!uiState.isPhotoPickerLaunchPending) return@LaunchedEffect

        viewModel.onPhotoPickerLaunchHandled()
        try {
            imagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        } catch (_: Exception) {
            viewModel.onPhotoPickerLaunchFailed()
        }
    }

    BackHandler(onBack = onBack)

    CustomDesignSetupScreen(
        uiState = uiState,
        onTeamNameChanged = viewModel::onTeamNameChanged,
        onWinChanged = viewModel::onWinChanged,
        onTotalKillsChanged = viewModel::onTotalKillsChanged,
        onPositionPointsChanged = viewModel::onPositionPointsChanged,
        onTotalPointsChanged = viewModel::onTotalPointsChanged,
        onUploadCustomDesign = viewModel::requestPhotoPicker,
    )
}

@Composable
fun CustomDesignSetupScreen(
    uiState: CustomDesignSetupUiState = CustomDesignSetupUiState(),
    onTeamNameChanged: (String) -> Unit = {},
    onWinChanged: (String) -> Unit = {},
    onTotalKillsChanged: (String) -> Unit = {},
    onPositionPointsChanged: (String) -> Unit = {},
    onTotalPointsChanged: (String) -> Unit = {},
    onUploadCustomDesign: () -> Unit = {},
) {
    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(CUSTOM_DESIGN_SETUP_SCREEN_TEST_TAG)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.custom_design_setup_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        uiState.selectedImageReference?.let { imageReference ->
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            CustomDesignImagePreview(
                imageReference = imageReference,
                sourceWidth = uiState.sourceImageWidth,
                sourceHeight = uiState.sourceImageHeight,
                gridGeometry = uiState.gridGeometry,
                modifier = Modifier.testTag(CUSTOM_DESIGN_IMAGE_PREVIEW_TEST_TAG),
            )
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        CustomDesignLabelInput(
            value = uiState.teamNameLabel,
            onValueChange = onTeamNameChanged,
            label = stringResource(R.string.custom_design_team_name_label),
            testTag = CUSTOM_DESIGN_TEAM_NAME_FIELD_TEST_TAG,
            isError = CustomDesignLabelField.TEAM_NAME in uiState.validationErrors,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        CustomDesignLabelInput(
            value = uiState.winLabel,
            onValueChange = onWinChanged,
            label = stringResource(R.string.custom_design_win_label),
            testTag = CUSTOM_DESIGN_WIN_FIELD_TEST_TAG,
            isError = CustomDesignLabelField.WIN in uiState.validationErrors,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        CustomDesignLabelInput(
            value = uiState.totalKillsLabel,
            onValueChange = onTotalKillsChanged,
            label = stringResource(R.string.custom_design_total_kills_label),
            testTag = CUSTOM_DESIGN_TOTAL_KILLS_FIELD_TEST_TAG,
            isError = CustomDesignLabelField.TOTAL_KILLS in uiState.validationErrors,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        CustomDesignLabelInput(
            value = uiState.positionPointsLabel,
            onValueChange = onPositionPointsChanged,
            label = stringResource(R.string.custom_design_position_points_label),
            testTag = CUSTOM_DESIGN_POSITION_POINTS_FIELD_TEST_TAG,
            isError = CustomDesignLabelField.POSITION_POINTS in uiState.validationErrors,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        CustomDesignLabelInput(
            value = uiState.totalPointsLabel,
            onValueChange = onTotalPointsChanged,
            label = stringResource(R.string.custom_design_total_points_label),
            testTag = CUSTOM_DESIGN_TOTAL_POINTS_FIELD_TEST_TAG,
            isError = CustomDesignLabelField.TOTAL_POINTS in uiState.validationErrors,
        )
        uiState.validationErrors.takeIf { it.isNotEmpty() }?.let {
            Text(
                text = stringResource(R.string.required_field_error),
                color = MaterialTheme.colorScheme.error,
            )
        }
        uiState.imageValidationError?.let { error ->
            Text(
                text = stringResource(error.toCustomDesignMessageRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(CUSTOM_DESIGN_IMAGE_ERROR_TEST_TAG),
            )
        }
        uiState.photoPickerError?.let { error ->
            Text(
                text = stringResource(error.toCustomDesignMessageRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(CUSTOM_DESIGN_IMAGE_ERROR_TEST_TAG),
            )
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(
            onClick = onUploadCustomDesign,
            enabled = !uiState.isImageValidationInProgress &&
                !uiState.isPhotoPickerLaunchPending,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CUSTOM_DESIGN_UPLOAD_ACTION_TEST_TAG),
        ) {
            Text(stringResource(R.string.custom_design_upload_action))
        }
    }
}

@Composable
private fun CustomDesignLabelInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    isError: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        isError = isError,
        supportingText = {
            if (isError) Text(stringResource(R.string.required_field_error))
        },
    )
}

@Composable
private fun CustomDesignImagePreview(
    imageReference: String,
    sourceWidth: Int?,
    sourceHeight: Int?,
    gridGeometry: CustomDesignGridGeometry?,
    modifier: Modifier = Modifier,
) {
    val contentResolver = LocalContext.current.contentResolver
    val bitmap by produceState<Bitmap?>(initialValue = null, imageReference) {
        value = withContext(Dispatchers.IO) {
            decodeCustomDesignPreview(contentResolver, imageReference)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        bitmap?.let { previewBitmap ->
            val previewAspectRatio = if (
                sourceWidth != null &&
                sourceHeight != null &&
                sourceWidth > 0 &&
                sourceHeight > 0
            ) {
                sourceWidth.toFloat() / sourceHeight.toFloat()
            } else {
                previewBitmap.width.toFloat() / previewBitmap.height.toFloat()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(previewAspectRatio),
            ) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.custom_design_setup_title),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                if (
                    gridGeometry != null &&
                    sourceWidth != null &&
                    sourceHeight != null &&
                    gridGeometry.sourceWidth == sourceWidth &&
                    gridGeometry.sourceHeight == sourceHeight
                ) {
                    CustomDesignGridOverlay(
                        geometry = gridGeometry,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(CUSTOM_DESIGN_GRID_OVERLAY_TEST_TAG),
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomDesignGridOverlay(
    geometry: CustomDesignGridGeometry,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val transform = SourceToPreviewTransform.fit(
            sourceWidth = geometry.sourceWidth,
            sourceHeight = geometry.sourceHeight,
            containerWidth = size.width,
            containerHeight = size.height,
        ) ?: return@Canvas
        val lineColor = Color(0xFFD0D0D0)
        val strokeWidth = 1.dp.toPx()

        clipRect(
            left = transform.offsetX,
            top = transform.offsetY,
            right = transform.offsetX + transform.displayedWidth,
            bottom = transform.offsetY + transform.displayedHeight,
        ) {
            geometry.columnX.values.forEach { sourceX ->
                val previewX = transform.mapX(sourceX)
                drawLine(
                    color = lineColor,
                    start = Offset(previewX, transform.offsetY),
                    end = Offset(previewX, transform.offsetY + transform.displayedHeight),
                    strokeWidth = strokeWidth,
                )
            }
            geometry.rowY.values.forEach { row ->
                val previewY = transform.mapY(row.y)
                drawLine(
                    color = lineColor,
                    start = Offset(transform.offsetX, previewY),
                    end = Offset(transform.offsetX + transform.displayedWidth, previewY),
                    strokeWidth = strokeWidth,
                )
            }
        }
    }
}

private fun decodeCustomDesignPreview(
    contentResolver: ContentResolver,
    imageReference: String,
): Bitmap? = try {
    val uri = Uri.parse(imageReference)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            if (width <= 0 || height <= 0) {
                throw IllegalArgumentException("Invalid preview image dimensions.")
            }

            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            decoder.setTargetSampleSize(
                calculateLocalScreenshotPreviewSampleSize(width, height),
            )
        }
    } else {
        decodeCustomDesignPreviewLegacy(contentResolver, uri)
    }
} catch (_: IOException) {
    null
} catch (_: SecurityException) {
    null
} catch (_: IllegalArgumentException) {
    null
} catch (_: RuntimeException) {
    null
} catch (_: OutOfMemoryError) {
    null
}

private fun decodeCustomDesignPreviewLegacy(
    contentResolver: ContentResolver,
    uri: Uri,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    } ?: return null

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateLocalScreenshotPreviewSampleSize(
            bounds.outWidth,
            bounds.outHeight,
        )
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    return contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}

private fun ImageValidationError.toCustomDesignMessageRes(): Int = when (this) {
    ImageValidationError.EMPTY_URI -> R.string.match_review_image_validation_empty_uri_error
    ImageValidationError.NON_IMAGE_CONTENT -> R.string.match_review_image_validation_non_image_error
    ImageValidationError.UNSUPPORTED_FORMAT -> R.string.match_review_image_validation_unsupported_format_error
    ImageValidationError.UNREADABLE_URI -> R.string.match_review_image_validation_unreadable_error
    ImageValidationError.DECODE_FAILED -> R.string.match_review_image_validation_decode_failed_error
    ImageValidationError.INVALID_DIMENSIONS -> R.string.match_review_image_validation_invalid_dimensions_error
    ImageValidationError.IMAGE_TOO_LARGE -> R.string.match_review_image_validation_too_large_error
}

private fun PhotoPickerError.toCustomDesignMessageRes(): Int =
    R.string.match_review_photo_picker_launch_failed_error
