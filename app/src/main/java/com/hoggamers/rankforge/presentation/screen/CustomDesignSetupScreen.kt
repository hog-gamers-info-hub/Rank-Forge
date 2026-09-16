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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEditableGridInitializer
import com.hoggamers.rankforge.domain.ocr.customdesign.resolveCustomDesignEffectiveGridGeometry
import com.hoggamers.rankforge.presentation.component.PointIqConfirmationDialog
import com.hoggamers.rankforge.presentation.component.PointIqHomeSystemBars
import com.hoggamers.rankforge.presentation.component.PointIqPageHeader
import com.hoggamers.rankforge.presentation.component.pointIqHomeBackground
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
const val CUSTOM_DESIGN_SAVE_ACTION_TEST_TAG = "custom_design_save_action"
const val CUSTOM_DESIGN_DELETE_ACTION_TEST_TAG = "custom_design_delete_action"
const val CUSTOM_DESIGN_IMAGE_PREVIEW_TEST_TAG = "custom_design_image_preview"
const val CUSTOM_DESIGN_GRID_OVERLAY_TEST_TAG = "custom_design_grid_overlay"
const val CUSTOM_DESIGN_IMAGE_ERROR_TEST_TAG = "custom_design_image_error"
const val CUSTOM_DESIGN_SAVE_SUCCESS_DIALOG_TEST_TAG = "custom_design_save_success_dialog"
const val CUSTOM_DESIGN_SAVE_SUCCESS_OK_TEST_TAG = "custom_design_save_success_ok"
const val CUSTOM_DESIGN_TEAM_NAME_COLOR_TEST_TAG = "custom_design_team_name_color"
const val CUSTOM_DESIGN_WIN_COLOR_TEST_TAG = "custom_design_win_color"
const val CUSTOM_DESIGN_TOTAL_KILLS_COLOR_TEST_TAG = "custom_design_total_kills_color"
const val CUSTOM_DESIGN_POSITION_POINTS_COLOR_TEST_TAG = "custom_design_position_points_color"
const val CUSTOM_DESIGN_TOTAL_POINTS_COLOR_TEST_TAG = "custom_design_total_points_color"
const val CUSTOM_DESIGN_TEXT_COLOR_DIALOG_TEST_TAG = "custom_design_text_color_dialog"
const val CUSTOM_DESIGN_TEXT_COLOR_OPTION_TEST_TAG_PREFIX = "custom_design_text_color_option_"

private val CustomDesignPointIqDanger = Color(0xFFD92D3A)
private val CustomDesignPointIqDangerContainer = Color(0xFFFFF5F5)
private val CustomDesignPointIqTitle = Color(0xFFF6F8FF)
private val CustomDesignPointIqBody = Color(0xFF91AFE0)
private val CustomDesignPointIqCtaTopBlue = Color(0xFF159CF8)
private val CustomDesignPointIqCtaMiddleBlue = Color(0xFF1688F7)
private val CustomDesignPointIqCtaDeepBlue = Color(0xFF1675F0)
private val CustomDesignPointIqCtaBorder = Color(0xFF4AAFF7)
private val CustomDesignPointIqFieldInactive = Color(0xFF7D9DCE)
private val CustomDesignPointIqCyan = Color(0xFF17C9F2)
private const val WATCH_DEMO_URL =
    "https://youtube.com/shorts/uM3RpjI8fdA?feature=share"
private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

private data class CustomDesignSelectableTextColor(
    val hex: String,
)

private val CustomDesignSelectableTextColors = listOf(
    CustomDesignSelectableTextColor("#000000"),
    CustomDesignSelectableTextColor("#FFFFFF"),
    CustomDesignSelectableTextColor("#808080"),
    CustomDesignSelectableTextColor("#FF0000"),
    CustomDesignSelectableTextColor("#FF9800"),
    CustomDesignSelectableTextColor("#FFD600"),
    CustomDesignSelectableTextColor("#00A651"),
    CustomDesignSelectableTextColor("#176AF7"),
    CustomDesignSelectableTextColor("#7B1FA2"),
)

@Composable
fun CustomDesignSetupRoute(
    onBack: () -> Unit,
    onSaveSuccessConfirmed: () -> Unit = {},
    viewModel: CustomDesignSetupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
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
    PointIqHomeSystemBars()

    CustomDesignSetupScreen(
        uiState = uiState,
        onBack = onBack,
        onTeamNameChanged = viewModel::onTeamNameChanged,
        onWinChanged = viewModel::onWinChanged,
        onTotalKillsChanged = viewModel::onTotalKillsChanged,
        onPositionPointsChanged = viewModel::onPositionPointsChanged,
        onTotalPointsChanged = viewModel::onTotalPointsChanged,
        onTextColorChanged = viewModel::setTextColor,
        onUploadCustomDesign = viewModel::requestPhotoPicker,
        onSaveActionRequested = viewModel::saveNewCustomDesign,
        onSaveSuccessAcknowledged = viewModel::onSaveSuccessMessageHandled,
        onSaveSuccessConfirmed = {
            viewModel.onSaveSuccessMessageHandled()
            onSaveSuccessConfirmed()
        },
        onDeleteActionRequested = viewModel::deleteSavedCustomDesign,
        onManualColumnXChanged = viewModel::setManualColumnX,
        onManualRowYChanged = viewModel::setManualRowY,
        onWatchDemo = {
            launchWithFallback(
                primary = buildSocialAppIntent(WATCH_DEMO_URL, YOUTUBE_PACKAGE),
                fallback = buildBrowserIntent(WATCH_DEMO_URL),
            ) { intent ->
                context.startActivity(intent)
            }
        },
    )
}

@Composable
fun CustomDesignSetupScreen(
    uiState: CustomDesignSetupUiState = CustomDesignSetupUiState(),
    onBack: () -> Unit = {},
    onTeamNameChanged: (String) -> Unit = {},
    onWinChanged: (String) -> Unit = {},
    onTotalKillsChanged: (String) -> Unit = {},
    onPositionPointsChanged: (String) -> Unit = {},
    onTotalPointsChanged: (String) -> Unit = {},
    onTextColorChanged: (CustomDesignAnchorField, String) -> Unit = { _, _ -> },
    onUploadCustomDesign: () -> Unit = {},
    onSaveActionRequested: () -> Unit = {},
    onSaveSuccessAcknowledged: () -> Unit = {},
    onSaveSuccessConfirmed: () -> Unit = {},
    onDeleteActionRequested: () -> Unit = {},
    onManualColumnXChanged: (CustomDesignAnchorField, Float) -> Unit = { _, _ -> },
    onManualRowYChanged: (Int, Float) -> Unit = { _, _ -> },
    onWatchDemo: () -> Unit = {},
) {
    val isRestored = uiState.restoreStatus == CustomDesignRestoreStatus.RESTORED
    var activeTextColorField by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<CustomDesignAnchorField?>(null)
    }
    val editableGridGeometry = uiState.editableGridGeometry
        ?: CustomDesignEditableGridInitializer.initialize(
            sourceWidth = uiState.sourceImageWidth ?: 0,
            sourceHeight = uiState.sourceImageHeight ?: 0,
            automatic = uiState.gridGeometry,
        )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointIqHomeBackground()
            .testTag(CUSTOM_DESIGN_SETUP_SCREEN_TEST_TAG)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        PointIqPageHeader(
            title = "Import Your Design",
            onBack = onBack,
            backTestTag = CUSTOM_DESIGN_SETUP_SCREEN_TEST_TAG + "_back",
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (!isRestored) {
            val introShape = RoundedCornerShape(12.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B386F).copy(alpha = 0.42f), introShape)
                    .border(1.dp, Color(0xFF176AF7).copy(alpha = 0.5f), introShape)
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Set up columns",
                        color = CustomDesignPointIqTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF071B3E))
                            .border(
                                1.dp,
                                CustomDesignPointIqCyan.copy(alpha = 0.55f),
                                RoundedCornerShape(10.dp),
                            )
                            .clickable(onClick = onWatchDemo)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Watch demo",
                            color = CustomDesignPointIqCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Watch demo",
                            tint = CustomDesignPointIqCyan,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enter the column headings exactly as they appear in your result design. " +
                        "Choose the text color PointIQ should use when filling each column.",
                    color = CustomDesignPointIqBody,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
        uiState.selectedImageReference?.let { imageReference ->
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            CustomDesignImagePreview(
                imageReference = imageReference,
                sourceWidth = uiState.sourceImageWidth,
                sourceHeight = uiState.sourceImageHeight,
                gridGeometry = if (isRestored) {
                    null
                } else {
                    resolveCustomDesignEffectiveGridGeometry(
                        editable = editableGridGeometry,
                        overrides = uiState.manualGridOverrides,
                    )
                },
                onManualColumnXChanged = onManualColumnXChanged,
                onManualRowYChanged = onManualRowYChanged,
                modifier = Modifier.testTag(CUSTOM_DESIGN_IMAGE_PREVIEW_TEST_TAG),
            )
        }
        if (!isRestored) {
            Spacer(
                modifier = Modifier.height(
                    if (uiState.selectedImageReference == null) 24.dp else RankForgeSpacing.Medium,
                ),
            )
            CustomDesignLabelWithColor(
                value = uiState.teamNameLabel,
                onValueChange = onTeamNameChanged,
                label = "Team Name",
                testTag = CUSTOM_DESIGN_TEAM_NAME_FIELD_TEST_TAG,
                isError = CustomDesignLabelField.TEAM_NAME in uiState.validationErrors,
                color = uiState.textColors.colorFor(CustomDesignAnchorField.TEAM_NAME),
                colorTestTag = CUSTOM_DESIGN_TEAM_NAME_COLOR_TEST_TAG,
                onColorClick = { activeTextColorField = CustomDesignAnchorField.TEAM_NAME },
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            CustomDesignLabelWithColor(
                value = uiState.winLabel,
                onValueChange = onWinChanged,
                label = "Win",
                testTag = CUSTOM_DESIGN_WIN_FIELD_TEST_TAG,
                isError = CustomDesignLabelField.WIN in uiState.validationErrors,
                color = uiState.textColors.colorFor(CustomDesignAnchorField.WIN),
                colorTestTag = CUSTOM_DESIGN_WIN_COLOR_TEST_TAG,
                onColorClick = { activeTextColorField = CustomDesignAnchorField.WIN },
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            CustomDesignLabelWithColor(
                value = uiState.totalKillsLabel,
                onValueChange = onTotalKillsChanged,
                label = "Total Kills",
                testTag = CUSTOM_DESIGN_TOTAL_KILLS_FIELD_TEST_TAG,
                isError = CustomDesignLabelField.TOTAL_KILLS in uiState.validationErrors,
                color = uiState.textColors.colorFor(CustomDesignAnchorField.TOTAL_KILLS),
                colorTestTag = CUSTOM_DESIGN_TOTAL_KILLS_COLOR_TEST_TAG,
                onColorClick = { activeTextColorField = CustomDesignAnchorField.TOTAL_KILLS },
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            CustomDesignLabelWithColor(
                value = uiState.positionPointsLabel,
                onValueChange = onPositionPointsChanged,
                label = "Position Points",
                testTag = CUSTOM_DESIGN_POSITION_POINTS_FIELD_TEST_TAG,
                isError = CustomDesignLabelField.POSITION_POINTS in uiState.validationErrors,
                color = uiState.textColors.colorFor(CustomDesignAnchorField.POSITION_POINTS),
                colorTestTag = CUSTOM_DESIGN_POSITION_POINTS_COLOR_TEST_TAG,
                onColorClick = { activeTextColorField = CustomDesignAnchorField.POSITION_POINTS },
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            CustomDesignLabelWithColor(
                value = uiState.totalPointsLabel,
                onValueChange = onTotalPointsChanged,
                label = "Total Points",
                testTag = CUSTOM_DESIGN_TOTAL_POINTS_FIELD_TEST_TAG,
                isError = CustomDesignLabelField.TOTAL_POINTS in uiState.validationErrors,
                color = uiState.textColors.colorFor(CustomDesignAnchorField.TOTAL_POINTS),
                colorTestTag = CUSTOM_DESIGN_TOTAL_POINTS_COLOR_TEST_TAG,
                onColorClick = { activeTextColorField = CustomDesignAnchorField.TOTAL_POINTS },
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
        }
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        val actionBusy = uiState.saveStatus == CustomDesignSaveStatus.SAVING ||
            uiState.restoreStatus == CustomDesignRestoreStatus.RESTORING ||
            uiState.deleteStatus == CustomDesignDeleteStatus.DELETING ||
            uiState.isImageValidationInProgress ||
            uiState.isPhotoPickerLaunchPending
        val saveEnabled = uiState.selectedImageReference != null &&
            uiState.savedCustomDesignId == null &&
            !actionBusy
        val deleteEnabled = uiState.savedCustomDesignId != null && !actionBusy
        when {
            uiState.savedCustomDesignId != null -> OutlinedButton(
                onClick = onDeleteActionRequested,
                enabled = deleteEnabled,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CustomDesignPointIqDangerContainer,
                    contentColor = CustomDesignPointIqDanger,
                    disabledContainerColor = CustomDesignPointIqDangerContainer.copy(alpha = 0.55f),
                    disabledContentColor = CustomDesignPointIqDanger.copy(alpha = 0.45f),
                ),
                border = BorderStroke(1.dp, CustomDesignPointIqDanger.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag(CUSTOM_DESIGN_DELETE_ACTION_TEST_TAG),
            ) {
                Text(
                    text = stringResource(R.string.custom_design_delete_action),
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
            }
            else -> CustomDesignPrimaryAction(
                onClick = if (uiState.selectedImageReference != null) {
                    onSaveActionRequested
                } else {
                    onUploadCustomDesign
                },
                enabled = if (uiState.selectedImageReference != null) saveEnabled else !actionBusy,
                testTag = if (uiState.selectedImageReference != null) {
                    CUSTOM_DESIGN_SAVE_ACTION_TEST_TAG
                } else {
                    CUSTOM_DESIGN_UPLOAD_ACTION_TEST_TAG
                },
                label = if (uiState.selectedImageReference != null) {
                    stringResource(R.string.custom_design_save_action)
                } else {
                    "Upload Your Design"
                },
            )
        }
    }

    if (uiState.saveStatus == CustomDesignSaveStatus.SAVED &&
        uiState.restoreStatus != CustomDesignRestoreStatus.RESTORED
    ) {
        PointIqConfirmationDialog(
            modifier = Modifier.testTag(CUSTOM_DESIGN_SAVE_SUCCESS_DIALOG_TEST_TAG),
            onDismissRequest = onSaveSuccessAcknowledged,
            title = stringResource(R.string.custom_design_save_success_title),
            message = stringResource(R.string.custom_design_save_success_message),
            confirmLabel = stringResource(R.string.custom_design_save_success_ok),
            onConfirm = onSaveSuccessConfirmed,
            confirmModifier = Modifier.testTag(CUSTOM_DESIGN_SAVE_SUCCESS_OK_TEST_TAG),
        )
    }

    activeTextColorField?.let { field ->
        CustomDesignTextColorDialog(
            selectedColor = uiState.textColors.colorFor(field),
            onColorSelected = { color ->
                onTextColorChanged(field, color)
                activeTextColorField = null
            },
            onDismissRequest = { activeTextColorField = null },
        )
    }
}

@Composable
private fun CustomDesignLabelWithColor(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    isError: Boolean,
    color: String,
    colorTestTag: String,
    onColorClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            CustomDesignFloatingUnderlineTextField(
                value = value,
                onValueChange = onValueChange,
                label = label,
                modifier = Modifier
                    .testTag(testTag),
            )
            CustomDesignColorSwatch(
                color = color,
                testTag = colorTestTag,
                onClick = onColorClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp),
            )
        }
        if (isError) {
            Text(
                text = stringResource(R.string.required_field_error),
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun CustomDesignFloatingUnderlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var isFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val visualFocused = isFocused
    val floating = visualFocused || value.isNotEmpty()
    val fieldHeight = 48.dp
    val lineColor = if (visualFocused) CustomDesignPointIqCyan else CustomDesignPointIqFieldInactive
    val labelColor = if (visualFocused) CustomDesignPointIqCyan else CustomDesignPointIqFieldInactive

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = CustomDesignPointIqTitle,
            fontSize = 18.sp,
            lineHeight = 22.sp,
        ),
        cursorBrush = SolidColor(CustomDesignPointIqCyan),
        keyboardOptions = KeyboardOptions.Default,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
            .semantics(mergeDescendants = true) {
                contentDescription = label
            },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fieldHeight)
                    .drawBehind {
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, size.height - 9.dp.toPx()),
                            end = Offset(size.width, size.height - 9.dp.toPx()),
                            strokeWidth = if (visualFocused) 1.5.dp.toPx() else 1.dp.toPx(),
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp, end = 40.dp),
                ) {
                    if (!floating) {
                        Text(
                            text = label,
                            color = CustomDesignPointIqFieldInactive,
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                        )
                    }
                    innerTextField()
                }
                if (floating) {
                    Text(
                        text = label,
                        color = labelColor,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun CustomDesignColorSwatch(
    color: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(shape)
            .background(Color(android.graphics.Color.parseColor(color)))
            .border(
                border = BorderStroke(1.dp, CustomDesignPointIqBody),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
    )
}

@Composable
private fun CustomDesignPrimaryAction(
    onClick: () -> Unit,
    enabled: Boolean,
    testTag: String,
    label: String,
) {
    val shape = RoundedCornerShape(8.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to CustomDesignPointIqCtaTopBlue,
                        0.52f to CustomDesignPointIqCtaMiddleBlue,
                        1f to CustomDesignPointIqCtaDeepBlue,
                    ),
                ),
                shape = shape,
            )
            .border(1.dp, CustomDesignPointIqCtaBorder, shape)
            .testTag(testTag),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = CustomDesignPointIqTitle,
            disabledContentColor = CustomDesignPointIqTitle.copy(alpha = 0.6f),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CustomDesignTextColorDialog(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(CUSTOM_DESIGN_TEXT_COLOR_DIALOG_TEST_TAG),
        onDismissRequest = onDismissRequest,
        title = { Text("Text Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small)) {
                CustomDesignSelectableTextColors.chunked(3).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        rowColors.forEach { option ->
                            val isSelected = option.hex == selectedColor
                            val shape = RoundedCornerShape(8.dp)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(shape)
                                    .background(Color(android.graphics.Color.parseColor(option.hex)))
                                    .border(
                                        border = BorderStroke(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                        ),
                                        shape = shape,
                                    )
                                    .selectable(
                                        selected = isSelected,
                                        role = Role.RadioButton,
                                        onClick = { onColorSelected(option.hex) },
                                    )
                                    .testTag(
                                        CUSTOM_DESIGN_TEXT_COLOR_OPTION_TEST_TAG_PREFIX +
                                            option.hex.removePrefix("#"),
                                    ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel_action))
            }
        },
    )
}

@Composable
private fun CustomDesignImagePreview(
    imageReference: String,
    sourceWidth: Int?,
    sourceHeight: Int?,
    gridGeometry: CustomDesignEffectiveGridGeometry?,
    onManualColumnXChanged: (CustomDesignAnchorField, Float) -> Unit,
    onManualRowYChanged: (Int, Float) -> Unit,
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
                        onManualColumnXChanged = onManualColumnXChanged,
                        onManualRowYChanged = onManualRowYChanged,
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
    geometry: CustomDesignEffectiveGridGeometry,
    onManualColumnXChanged: (CustomDesignAnchorField, Float) -> Unit,
    onManualRowYChanged: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestGeometry by rememberUpdatedState(geometry)
    val latestOnManualColumnXChanged by rememberUpdatedState(onManualColumnXChanged)
    val latestOnManualRowYChanged by rememberUpdatedState(onManualRowYChanged)
    val textMeasurer = rememberTextMeasurer()
    val hitTolerancePx = with(LocalDensity.current) {
        CUSTOM_DESIGN_GRID_HIT_TOLERANCE_DP.dp.toPx()
    }

    Canvas(
        modifier = modifier.pointerInput(hitTolerancePx) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                val currentGeometry = latestGeometry
                val transform = SourceToPreviewTransform.fit(
                    sourceWidth = currentGeometry.sourceWidth,
                    sourceHeight = currentGeometry.sourceHeight,
                    containerWidth = size.width.toFloat(),
                    containerHeight = size.height.toFloat(),
                ) ?: return@awaitEachGesture
                val candidates = findCustomDesignGridHitCandidates(
                    pointer = down.position,
                    geometry = currentGeometry,
                    transform = transform,
                    hitTolerancePx = hitTolerancePx,
                )
                var selection: CustomDesignGridSelection? = null
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: return@awaitEachGesture
                    if (!change.pressed) return@awaitEachGesture

                    val dragDelta = change.position - down.position
                    if (selection == null) {
                        val touchSlop = viewConfiguration.touchSlop
                        val dragDistanceSquared =
                            dragDelta.x * dragDelta.x + dragDelta.y * dragDelta.y
                        if (dragDistanceSquared < touchSlop * touchSlop) continue
                        selection = chooseCustomDesignGridSelection(candidates, dragDelta)
                            ?: return@awaitEachGesture
                    }

                    change.consume()
                    val selected = checkNotNull(selection)
                    when (selected) {
                        is CustomDesignGridSelection.Column -> {
                            val sourceX = customDesignColumnSourceX(
                                previewX = change.position.x,
                                transform = transform,
                                sourceWidth = currentGeometry.sourceWidth,
                            ) ?: continue
                            latestOnManualColumnXChanged(selected.field, sourceX)
                        }
                        is CustomDesignGridSelection.Row -> {
                            val sourceY = customDesignRowSourceY(
                                previewY = change.position.y,
                                transform = transform,
                                sourceHeight = currentGeometry.sourceHeight,
                            ) ?: continue
                            constrainCustomDesignRowSourceY(
                                rank = selected.rank,
                                sourceY = sourceY,
                                geometry = currentGeometry,
                            )?.let { constrainedY ->
                                latestOnManualRowYChanged(selected.rank, constrainedY)
                            }
                        }
                    }
                }
            }
        },
    ) {
        val transform = SourceToPreviewTransform.fit(
            sourceWidth = geometry.sourceWidth,
            sourceHeight = geometry.sourceHeight,
            containerWidth = size.width,
            containerHeight = size.height,
        ) ?: return@Canvas
        val lineColor = Color(0xFFD0D0D0)
        val strokeWidth = 1.dp.toPx()
        val labelStyle = TextStyle(
            color = Color.White,
            fontSize = 8.sp,
        )
        val labelHorizontalPadding = 3.dp.toPx()
        val labelVerticalPadding = 2.dp.toPx()
        val labelCornerRadius = 2.dp.toPx()
        val upperLevelOffset = 4.dp.toPx()
        val lowerLevelOffset = 28.dp.toPx()

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
            geometry.rowY.values.forEach { sourceY ->
                val previewY = transform.mapY(sourceY)
                drawLine(
                    color = lineColor,
                    start = Offset(transform.offsetX, previewY),
                    end = Offset(transform.offsetX + transform.displayedWidth, previewY),
                    strokeWidth = strokeWidth,
                )
            }
            customDesignSemanticColumnLabels(geometry).forEach { label ->
                val previewX = transform.mapX(label.sourceX)
                val textLayout = textMeasurer.measure(
                    text = label.text,
                    style = labelStyle,
                    maxLines = 1,
                )
                val boxWidth = textLayout.size.width + labelHorizontalPadding * 2f
                val boxHeight = textLayout.size.height + labelVerticalPadding * 2f
                val maxBoxX = (
                    transform.offsetX + transform.displayedWidth - boxWidth
                    ).coerceAtLeast(transform.offsetX)
                val boxX = (previewX - boxWidth / 2f)
                    .coerceIn(transform.offsetX, maxBoxX)
                val requestedBoxY = when (label.level) {
                    CustomDesignSemanticColumnLabelLevel.UPPER -> transform.offsetY + upperLevelOffset
                    CustomDesignSemanticColumnLabelLevel.LOWER -> transform.offsetY + lowerLevelOffset
                }
                val maxBoxY = (
                    transform.offsetY + transform.displayedHeight - boxHeight
                    ).coerceAtLeast(transform.offsetY)
                val boxY = requestedBoxY.coerceIn(transform.offsetY, maxBoxY)
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(boxX, boxY),
                    size = Size(boxWidth, boxHeight),
                    cornerRadius = CornerRadius(labelCornerRadius, labelCornerRadius),
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(
                        boxX + labelHorizontalPadding,
                        boxY + labelVerticalPadding,
                    ),
                )
            }
        }
    }
}

internal enum class CustomDesignSemanticColumnLabelLevel {
    UPPER,
    LOWER,
}

internal data class CustomDesignSemanticColumnLabel(
    val field: CustomDesignAnchorField,
    val sourceX: Float,
    val level: CustomDesignSemanticColumnLabelLevel,
) {
    val text: String
        get() = this.field.name
}

internal fun customDesignSemanticColumnLabels(
    geometry: CustomDesignEffectiveGridGeometry,
): List<CustomDesignSemanticColumnLabel> {
    val levelByField = geometry.columnX.entries
        .sortedWith(
            compareBy<Map.Entry<CustomDesignAnchorField, Float>> { it.value }
                .thenBy { it.key.ordinal },
        )
        .mapIndexed { index, entry ->
            entry.key to if (index % 2 == 0) {
                CustomDesignSemanticColumnLabelLevel.UPPER
            } else {
                CustomDesignSemanticColumnLabelLevel.LOWER
            }
        }
        .toMap()

    return geometry.columnX.map { (field, sourceX) ->
        CustomDesignSemanticColumnLabel(
            field = field,
            sourceX = sourceX,
            level = checkNotNull(levelByField[field]),
        )
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
