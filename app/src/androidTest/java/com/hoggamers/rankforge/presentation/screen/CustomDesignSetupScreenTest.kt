package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEditableGridInitializer
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignGridGeometry
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrAnchors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignOcrStatus
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignRowCoordinate
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignRowCoordinateSource
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomDesignSetupScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun noImageHidesFieldsAndColorControlsAndShowsUploadAction() {
        var uploadCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                CustomDesignSetupScreen(onUploadCustomDesign = { uploadCount++ })
            }
        }

        listOf(
            CUSTOM_DESIGN_TEAM_NAME_FIELD_TEST_TAG,
            CUSTOM_DESIGN_WIN_FIELD_TEST_TAG,
            CUSTOM_DESIGN_TOTAL_KILLS_FIELD_TEST_TAG,
            CUSTOM_DESIGN_POSITION_POINTS_FIELD_TEST_TAG,
            CUSTOM_DESIGN_TOTAL_POINTS_FIELD_TEST_TAG,
            CUSTOM_DESIGN_TEAM_NAME_COLOR_TEST_TAG,
            CUSTOM_DESIGN_WIN_COLOR_TEST_TAG,
            CUSTOM_DESIGN_TOTAL_KILLS_COLOR_TEST_TAG,
            CUSTOM_DESIGN_POSITION_POINTS_COLOR_TEST_TAG,
            CUSTOM_DESIGN_TOTAL_POINTS_COLOR_TEST_TAG,
        ).forEach { tag ->
            assertEquals(
                0,
                composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size,
            )
        }
        val uploadAction = composeTestRule
            .onNodeWithTag(CUSTOM_DESIGN_UPLOAD_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
        uploadAction.performClick()

        composeTestRule.runOnIdle { assertEquals(1, uploadCount) }
        assertEquals(
            0,
            composeTestRule
                .onAllNodesWithTag(CUSTOM_DESIGN_SAVE_ACTION_TEST_TAG)
                .fetchSemanticsNodes()
                .size,
        )
        assertEquals(
            0,
            composeTestRule
                .onAllNodesWithTag(CUSTOM_DESIGN_DELETE_ACTION_TEST_TAG)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun pendingCandidateHidesLegacyEmptySetupAndUploadAction() {
        composeTestRule.setContent {
            RankForgeTheme {
                CustomDesignSetupScreen(
                    isCandidatePending = true,
                    allowManualUpload = false,
                )
            }
        }

        assertEquals(
            0,
            composeTestRule
                .onAllNodesWithTag(CUSTOM_DESIGN_UPLOAD_ACTION_TEST_TAG)
                .fetchSemanticsNodes()
                .size,
        )
        assertEquals(
            0,
            composeTestRule
                .onAllNodesWithTag(CUSTOM_DESIGN_TEAM_NAME_FIELD_TEST_TAG)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun colorSelectorsUseIndependentPaletteSelectionsAndCancelPreservesColor() {
        var uiState by mutableStateOf(
            CustomDesignSetupUiState(selectedImageReference = "content://selected"),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                CustomDesignSetupScreen(
                    uiState = uiState,
                    onTextColorChanged = { field, color ->
                        uiState = uiState.copy(
                            textColors = CustomDesignColumnTextColors.fromMap(
                                uiState.textColors.asMap() + (field to color),
                            )!!,
                        )
                    },
                )
            }
        }

        val palette = listOf(
            CUSTOM_DESIGN_TEAM_NAME_COLOR_TEST_TAG to "FFFFFF",
            CUSTOM_DESIGN_WIN_COLOR_TEST_TAG to "FF0000",
            CUSTOM_DESIGN_TOTAL_KILLS_COLOR_TEST_TAG to "176AF7",
            CUSTOM_DESIGN_POSITION_POINTS_COLOR_TEST_TAG to "FFD600",
            CUSTOM_DESIGN_TOTAL_POINTS_COLOR_TEST_TAG to "FFFFFF",
        )
        val paletteColors = listOf("000000", "FFFFFF", "808080", "FF0000", "FF9800", "FFD600", "00A651", "176AF7", "7B1FA2")
        palette.forEach { (selectorTag, colorTag) ->
            composeTestRule.onNodeWithTag(selectorTag).performScrollTo().performClick()
            composeTestRule.onNodeWithTag(CUSTOM_DESIGN_TEXT_COLOR_DIALOG_TEST_TAG).assertIsDisplayed()
            paletteColors.forEach { paletteColor ->
                composeTestRule.onNodeWithTag(
                    CUSTOM_DESIGN_TEXT_COLOR_OPTION_TEST_TAG_PREFIX + paletteColor,
                ).assertIsDisplayed()
            }
            composeTestRule.onNodeWithTag(
                CUSTOM_DESIGN_TEXT_COLOR_OPTION_TEST_TAG_PREFIX + "000000",
            ).assertIsSelected()
            composeTestRule.onNodeWithTag(
                CUSTOM_DESIGN_TEXT_COLOR_OPTION_TEST_TAG_PREFIX + colorTag,
            ).performClick()
            assertEquals(
                0,
                composeTestRule.onAllNodesWithTag(CUSTOM_DESIGN_TEXT_COLOR_DIALOG_TEST_TAG)
                    .fetchSemanticsNodes().size,
            )
        }

        composeTestRule.runOnIdle {
            assertEquals("#FFFFFF", uiState.textColors.colorFor(CustomDesignAnchorField.TEAM_NAME))
            assertEquals("#FF0000", uiState.textColors.colorFor(CustomDesignAnchorField.WIN))
            assertEquals("#176AF7", uiState.textColors.colorFor(CustomDesignAnchorField.TOTAL_KILLS))
            assertEquals("#FFD600", uiState.textColors.colorFor(CustomDesignAnchorField.POSITION_POINTS))
            assertEquals("#FFFFFF", uiState.textColors.colorFor(CustomDesignAnchorField.TOTAL_POINTS))
        }

        composeTestRule.onNodeWithTag(CUSTOM_DESIGN_TEAM_NAME_COLOR_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(
            CUSTOM_DESIGN_TEXT_COLOR_OPTION_TEST_TAG_PREFIX + "FFFFFF",
        ).assertIsSelected()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.runOnIdle {
            assertEquals("#FFFFFF", uiState.textColors.colorFor(CustomDesignAnchorField.TEAM_NAME))
        }
    }

    @Test
    fun customHexApplyNormalizesAndUpdatesTheSelectedSemanticField() {
        var uiState by mutableStateOf(
            CustomDesignSetupUiState(selectedImageReference = "content://selected"),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                CustomDesignSetupScreen(
                    uiState = uiState,
                    onTextColorChanged = { field, color ->
                        uiState = uiState.copy(
                            textColors = CustomDesignColumnTextColors.fromMap(
                                uiState.textColors.asMap() + (field to color),
                            )!!,
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(CUSTOM_DESIGN_TEAM_NAME_COLOR_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(CUSTOM_DESIGN_CUSTOM_HEX_FIELD_TEST_TAG)
            .performTextReplacement("21d4fd")
        composeTestRule.onNodeWithTag(CUSTOM_DESIGN_CUSTOM_HEX_APPLY_TEST_TAG)
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals("#21D4FD", uiState.textColors.colorFor(CustomDesignAnchorField.TEAM_NAME))
        }
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(CUSTOM_DESIGN_TEXT_COLOR_DIALOG_TEST_TAG)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun invalidCustomHexDisablesApplyAndDoesNotUpdateColor() {
        var uiState by mutableStateOf(
            CustomDesignSetupUiState(selectedImageReference = "content://selected"),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                CustomDesignSetupScreen(
                    uiState = uiState,
                    onTextColorChanged = { field, color ->
                        uiState = uiState.copy(
                            textColors = CustomDesignColumnTextColors.fromMap(
                                uiState.textColors.asMap() + (field to color),
                            )!!,
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(CUSTOM_DESIGN_WIN_COLOR_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(CUSTOM_DESIGN_CUSTOM_HEX_FIELD_TEST_TAG)
            .performTextReplacement("#FFF")
        composeTestRule.onNodeWithTag(CUSTOM_DESIGN_CUSTOM_HEX_APPLY_TEST_TAG)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.runOnIdle {
            assertEquals("#000000", uiState.textColors.colorFor(CustomDesignAnchorField.WIN))
        }
    }

    @Test
    fun selectedUnsavedImageWithBlankLabelsShowsDisabledSave() {
        var saveCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                CustomDesignSetupScreen(
                    uiState = CustomDesignSetupUiState(selectedImageReference = "content://selected"),
                    allowManualUpload = false,
                    onSaveActionRequested = { saveCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(CUSTOM_DESIGN_SAVE_ACTION_TEST_TAG)
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeTestRule.runOnIdle { assertEquals(0, saveCount) }
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(CUSTOM_DESIGN_UPLOAD_ACTION_TEST_TAG)
                .fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(CUSTOM_DESIGN_DELETE_ACTION_TEST_TAG)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun selectedImageWithAllRequiredLabelsEnablesSave() {
        composeTestRule.setContent {
            RankForgeTheme {
                CustomDesignSetupScreen(
                    allowManualUpload = false,
                    uiState = CustomDesignSetupUiState(
                        teamNameLabel = "TEAM NAME",
                        winLabel = "WIN",
                        totalKillsLabel = "ELIM.",
                        positionPointsLabel = "POS.",
                        totalPointsLabel = "TOTAL",
                        selectedImageReference = "content://selected",
                        ocrStatus = CustomDesignOcrStatus.FAILED,
                        editableGridGeometry = CustomDesignEditableGridInitializer.initialize(
                            sourceWidth = 1080,
                            sourceHeight = 1350,
                            automatic = null,
                        ),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag(CUSTOM_DESIGN_SAVE_ACTION_TEST_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun saveActionStaysVisibleWithLongSetupContent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageFile = File.createTempFile("custom-design-long-content", ".png", context.cacheDir)
        FileOutputStream(imageFile).use { output ->
            Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        try {
            composeTestRule.setContent {
                RankForgeTheme {
                    CustomDesignSetupScreen(
                        uiState = CustomDesignSetupUiState(
                            selectedImageReference = imageFile.toURI().toString(),
                            sourceImageWidth = 1080,
                            sourceImageHeight = 1350,
                        ),
                    )
                }
            }

            composeTestRule
                .onNodeWithTag(CUSTOM_DESIGN_SAVE_ACTION_TEST_TAG)
                .assertIsDisplayed()
                .assertIsNotEnabled()
        } finally {
            imageFile.delete()
        }
    }

    @Test
    fun noImageWithManualUploadDisabledShowsNoAction() {
        composeTestRule.setContent {
            RankForgeTheme {
                CustomDesignSetupScreen(allowManualUpload = false)
            }
        }

        assertEquals(
            0,
            composeTestRule
                .onAllNodesWithTag(CUSTOM_DESIGN_UPLOAD_ACTION_TEST_TAG)
                .fetchSemanticsNodes()
                .size,
        )
        assertEquals(
            0,
            composeTestRule
                .onAllNodesWithTag(CUSTOM_DESIGN_SAVE_ACTION_TEST_TAG)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun savedDesignEnablesDelete() {
        var deleteCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                CustomDesignSetupScreen(
                    uiState = CustomDesignSetupUiState(
                        savedCustomDesignId = "a2000000-0000-0000-0000-000000000001",
                        saveStatus = CustomDesignSaveStatus.SAVED,
                        restoreStatus = CustomDesignRestoreStatus.RESTORED,
                    ),
                    onDeleteActionRequested = { deleteCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(CUSTOM_DESIGN_DELETE_ACTION_TEST_TAG)
            .assertIsEnabled()
            .performClick()
        composeTestRule.runOnIdle { assertEquals(1, deleteCount) }
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(CUSTOM_DESIGN_UPLOAD_ACTION_TEST_TAG)
                .fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(CUSTOM_DESIGN_SAVE_ACTION_TEST_TAG)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun newlySavedDesignShowsInformationalSuccessDialogAndAcknowledgesIt() {
        var acknowledged = 0
        composeTestRule.setContent {
            RankForgeTheme {
                CustomDesignSetupScreen(
                    uiState = CustomDesignSetupUiState(
                        savedCustomDesignId = "a2000000-0000-0000-0000-000000000001",
                        saveStatus = CustomDesignSaveStatus.SAVED,
                        restoreStatus = CustomDesignRestoreStatus.IDLE,
                    ),
                    onSaveSuccessAcknowledged = { acknowledged++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(CUSTOM_DESIGN_SAVE_SUCCESS_DIALOG_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Custom Design Saved").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "You can now download your result using “My Custom Design” from Result Format.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag(CUSTOM_DESIGN_SAVE_SUCCESS_OK_TEST_TAG)
            .performClick()

        composeTestRule.runOnIdle { assertEquals(1, acknowledged) }
    }

    @Test
    fun saveSuccessDialogIsAbsentForIdleSavingFailedAndRestoredStates() {
        var uiState by mutableStateOf(CustomDesignSetupUiState())
        composeTestRule.setContent {
            RankForgeTheme {
                CustomDesignSetupScreen(uiState = uiState)
            }
        }

        listOf(
            CustomDesignSetupUiState(),
            CustomDesignSetupUiState(saveStatus = CustomDesignSaveStatus.SAVING),
            CustomDesignSetupUiState(saveStatus = CustomDesignSaveStatus.FAILED),
            CustomDesignSetupUiState(
                savedCustomDesignId = "a2000000-0000-0000-0000-000000000001",
                saveStatus = CustomDesignSaveStatus.SAVED,
                restoreStatus = CustomDesignRestoreStatus.RESTORED,
            ),
        ).forEach { state ->
            composeTestRule.runOnIdle {
                uiState = state
            }
            composeTestRule.waitForIdle()

            assertEquals(
                0,
                composeTestRule.onAllNodesWithTag(CUSTOM_DESIGN_SAVE_SUCCESS_DIALOG_TEST_TAG)
                    .fetchSemanticsNodes()
                    .size,
            )
        }
    }

    @Test
    fun restoredDesignShowsOnlyImageAndDelete() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageFile = File.createTempFile("custom-design-restored", ".png", context.cacheDir)
        FileOutputStream(imageFile).use { output ->
            Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        try {
            composeTestRule.setContent {
                RankForgeTheme {
                    CustomDesignSetupScreen(
                        uiState = CustomDesignSetupUiState(
                            teamNameLabel = "TEAM NAME",
                            winLabel = "WIN",
                            totalKillsLabel = "ELIM.",
                            positionPointsLabel = "POS.",
                            totalPointsLabel = "TOTAL",
                            selectedImageReference = imageFile.toURI().toString(),
                            sourceImageWidth = 2,
                            sourceImageHeight = 3,
                            savedCustomDesignId = "a2000000-0000-0000-0000-000000000001",
                            saveStatus = CustomDesignSaveStatus.SAVED,
                            restoreStatus = CustomDesignRestoreStatus.RESTORED,
                            gridGeometry = CustomDesignGridGeometry(
                                sourceWidth = 2,
                                sourceHeight = 3,
                                columnX = mapOf(CustomDesignAnchorField.TEAM_NAME to 1f),
                                rowY = mapOf(
                                    1 to CustomDesignRowCoordinate(
                                        y = 1f,
                                        source = CustomDesignRowCoordinateSource.OCR,
                                    ),
                                ),
                                estimatedRowStep = null,
                            ),
                        ),
                    )
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule
                    .onAllNodesWithTag(CUSTOM_DESIGN_IMAGE_PREVIEW_TEST_TAG)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithTag(CUSTOM_DESIGN_IMAGE_PREVIEW_TEST_TAG)
                .assertIsDisplayed()
            composeTestRule.onNodeWithTag(CUSTOM_DESIGN_DELETE_ACTION_TEST_TAG)
                .performScrollTo()
                .assertIsDisplayed()

            listOf(
                CUSTOM_DESIGN_TEAM_NAME_FIELD_TEST_TAG,
                CUSTOM_DESIGN_WIN_FIELD_TEST_TAG,
                CUSTOM_DESIGN_TOTAL_KILLS_FIELD_TEST_TAG,
                CUSTOM_DESIGN_POSITION_POINTS_FIELD_TEST_TAG,
                CUSTOM_DESIGN_TOTAL_POINTS_FIELD_TEST_TAG,
                CUSTOM_DESIGN_UPLOAD_ACTION_TEST_TAG,
                CUSTOM_DESIGN_SAVE_ACTION_TEST_TAG,
                CUSTOM_DESIGN_GRID_OVERLAY_TEST_TAG,
                CUSTOM_DESIGN_TEAM_NAME_COLOR_TEST_TAG,
                CUSTOM_DESIGN_WIN_COLOR_TEST_TAG,
                CUSTOM_DESIGN_TOTAL_KILLS_COLOR_TEST_TAG,
                CUSTOM_DESIGN_POSITION_POINTS_COLOR_TEST_TAG,
                CUSTOM_DESIGN_TOTAL_POINTS_COLOR_TEST_TAG,
            ).forEach { tag ->
                assertEquals(
                    0,
                    composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size,
                )
            }
        } finally {
            imageFile.delete()
        }
    }

    @Test
    fun deletingDesignDisablesBothActions() {
        composeTestRule.setContent {
            RankForgeTheme {
                CustomDesignSetupScreen(
                    uiState = CustomDesignSetupUiState(
                        savedCustomDesignId = "a2000000-0000-0000-0000-000000000001",
                        saveStatus = CustomDesignSaveStatus.SAVED,
                        restoreStatus = CustomDesignRestoreStatus.RESTORED,
                        deleteStatus = CustomDesignDeleteStatus.DELETING,
                    ),
                )
            }
        }
        composeTestRule.onNodeWithTag(CUSTOM_DESIGN_DELETE_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsNotEnabled()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(CUSTOM_DESIGN_UPLOAD_ACTION_TEST_TAG)
                .fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(CUSTOM_DESIGN_SAVE_ACTION_TEST_TAG)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun selectedImageIsShownAsPreview() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageFile = File.createTempFile("custom-design-preview", ".png", context.cacheDir)
        FileOutputStream(imageFile).use { output ->
            Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        try {
            composeTestRule.setContent {
                RankForgeTheme {
                    CustomDesignSetupScreen(
                        uiState = CustomDesignSetupUiState(
                            selectedImageReference = imageFile.toURI().toString(),
                        ),
                    )
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule
                    .onAllNodesWithTag(CUSTOM_DESIGN_IMAGE_PREVIEW_TEST_TAG)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule
                .onNodeWithTag(CUSTOM_DESIGN_IMAGE_PREVIEW_TEST_TAG)
                .assertIsDisplayed()
            assertEquals(
                0,
                composeTestRule
                    .onAllNodesWithTag(CUSTOM_DESIGN_GRID_OVERLAY_TEST_TAG)
                    .fetchSemanticsNodes()
                    .size,
            )
        } finally {
            imageFile.delete()
        }
    }

    @Test
    fun detectedColumnIsShownBeforeAllLabelsAreFilled() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageFile = File.createTempFile("custom-design-partial-grid", ".png", context.cacheDir)
        FileOutputStream(imageFile).use { output ->
            Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        try {
            composeTestRule.setContent {
                RankForgeTheme {
                    CustomDesignSetupScreen(
                        uiState = CustomDesignSetupUiState(
                            teamNameLabel = "TEAM NAME",
                            selectedImageReference = imageFile.toURI().toString(),
                            sourceImageWidth = 2,
                            sourceImageHeight = 3,
                            ocrStatus = CustomDesignOcrStatus.COMPLETED,
                            ocrAnchors = CustomDesignOcrAnchors(
                                sourceWidth = 2,
                                sourceHeight = 3,
                                columnX = mapOf(CustomDesignAnchorField.TEAM_NAME to 1f),
                                rowY = mapOf(1 to 1f),
                            ),
                        ),
                    )
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule
                    .onAllNodesWithTag(CUSTOM_DESIGN_GRID_OVERLAY_TEST_TAG)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule
                .onNodeWithTag(CUSTOM_DESIGN_GRID_OVERLAY_TEST_TAG)
                .assertIsDisplayed()
        } finally {
            imageFile.delete()
        }
    }

    @Test
    fun decodedImageWithGridGeometryStillShowsCleanPreview() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageFile = File.createTempFile("custom-design-grid", ".png", context.cacheDir)
        FileOutputStream(imageFile).use { output ->
            Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        try {
            composeTestRule.setContent {
                RankForgeTheme {
                    CustomDesignSetupScreen(
                        uiState = CustomDesignSetupUiState(
                            selectedImageReference = imageFile.toURI().toString(),
                            sourceImageWidth = 2,
                            sourceImageHeight = 3,
                            gridGeometry = CustomDesignGridGeometry(
                                sourceWidth = 2,
                                sourceHeight = 3,
                                columnX = mapOf(CustomDesignAnchorField.TEAM_NAME to 1f),
                                rowY = mapOf(
                                    1 to CustomDesignRowCoordinate(
                                        y = 1f,
                                        source = CustomDesignRowCoordinateSource.OCR,
                                    ),
                                ),
                                estimatedRowStep = null,
                            ),
                        ),
                    )
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule
                    .onAllNodesWithTag(CUSTOM_DESIGN_IMAGE_PREVIEW_TEST_TAG)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule
                .onNodeWithTag(CUSTOM_DESIGN_IMAGE_PREVIEW_TEST_TAG)
                .assertIsDisplayed()
            assertEquals(
                0,
                composeTestRule
                    .onAllNodesWithTag(CUSTOM_DESIGN_GRID_OVERLAY_TEST_TAG)
                    .fetchSemanticsNodes()
                    .size,
            )
            listOf(
                CUSTOM_DESIGN_TEAM_NAME_FIELD_TEST_TAG,
                CUSTOM_DESIGN_WIN_FIELD_TEST_TAG,
                CUSTOM_DESIGN_TOTAL_KILLS_FIELD_TEST_TAG,
                CUSTOM_DESIGN_POSITION_POINTS_FIELD_TEST_TAG,
                CUSTOM_DESIGN_TOTAL_POINTS_FIELD_TEST_TAG,
            ).forEach { tag ->
                composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            }
            composeTestRule.onNodeWithTag(CUSTOM_DESIGN_SAVE_ACTION_TEST_TAG)
                .performScrollTo()
                .assertIsDisplayed()
        } finally {
            imageFile.delete()
        }
    }
}
