package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RosterScreenshotCropScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dedicatedCropScreenDisplaysEditor() {
        setCropScreenContent()

        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_CROP_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_CROP_EDITOR_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_PREVIEW_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_OVERLAY_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun cancelCallbackFires() {
        var cancelCount = 0
        setCropScreenContent(onCancel = { cancelCount++ })

        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_CROP_CANCEL_TEST_TAG).performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, cancelCount)
        }
    }

    @Test
    fun validConfirmCallbackFires() {
        var confirmCount = 0
        setCropScreenContent(onConfirm = { confirmCount++ })

        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_CONFIRM_ACTION_TEST_TAG).performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, confirmCount)
        }
    }

    @Test
    fun invalidCropDoesNotConfirm() {
        var confirmCount = 0
        setCropScreenContent(
            slot = selectedSlot().copy(
                cropDraft = RosterScreenshotCropDraft(
                    left = "0.20",
                    top = "0.20",
                    right = "0.25",
                    bottom = "0.25",
                ),
            ),
            onConfirm = { confirmCount++ },
        )

        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_CONFIRM_ACTION_TEST_TAG).assertIsNotEnabled()

        composeTestRule.runOnIdle {
            assertEquals(0, confirmCount)
        }
    }

    private fun setCropScreenContent(
        slot: RosterScreenshotSlotUiState = selectedSlot(),
        onCancel: () -> Unit = {},
        onConfirm: () -> Unit = {},
    ) {
        var currentSlot by mutableStateOf(slot)
        composeTestRule.setContent {
            RankForgeTheme {
                RosterScreenshotCropScreen(
                    slot = currentSlot,
                    onCropChanged = { crop ->
                        currentSlot = currentSlot.copy(cropDraft = crop.toRosterScreenshotCropDraft())
                    },
                    onCancel = onCancel,
                    onConfirm = onConfirm,
                )
            }
        }
    }

    private fun selectedSlot(): RosterScreenshotSlotUiState = RosterScreenshotSlotUiState(
        index = 1,
        selectedImageUri = "content://one",
        isSelectedImageValidated = true,
        selectedImageWidth = 1600,
        selectedImageHeight = 720,
        cropDraft = RosterScreenshotCropDefaults.FullImageCrop.toRosterScreenshotCropDraft(),
    )
}
