package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrVisualCropEditorTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rendersImageBoundPreviewOverlayWithoutCoordinateSummary() {
        setEditorContent(
            crop = OcrNormalizedCropRect(
                left = 0.20,
                top = 0.25,
                right = 0.80,
                bottom = 0.75,
            ),
        )

        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_PREVIEW_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_OVERLAY_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_MOVE_HANDLE_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_HANDLE_TOP_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_HANDLE_BOTTOM_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_HANDLE_LEFT_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_HANDLE_RIGHT_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Crop:", substring = true).assertCountEquals(0)
    }

    @Test
    fun previewUsesSourceImageAspectRatio() {
        setEditorContent(
            crop = OcrNormalizedCropRect(
                left = 0.20,
                top = 0.25,
                right = 0.80,
                bottom = 0.75,
            ),
            sourceImageWidth = 1600,
            sourceImageHeight = 720,
        )

        val bounds = composeTestRule
            .onNodeWithTag(OCR_VISUAL_CROP_PREVIEW_TEST_TAG)
            .getUnclippedBoundsInRoot()
        val width = bounds.right.value - bounds.left.value
        val height = bounds.bottom.value - bounds.top.value

        assertEquals(1600f / 720f, width / height, 0.05f)
    }

    @Test
    fun displaysFullImagePixelDimensions() {
        val crop = OcrVisualCropDefaults.FullImageCrop
        setEditorContent(
            crop = crop,
            sourceImageWidth = 1600,
            sourceImageHeight = 720,
        )

        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_WIDTH_VALUE_TEST_TAG)
            .assertIsDisplayed()
            .assertTextContains(expectedWidthText(crop, sourceWidth = 1600, sourceHeight = 720))
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_HEIGHT_VALUE_TEST_TAG)
            .assertIsDisplayed()
            .assertTextContains(expectedHeightText(crop, sourceWidth = 1600, sourceHeight = 720))
    }

    @Test
    fun displaysPartialCropPixelDimensionsFromSharedContract() {
        val crop = OcrNormalizedCropRect(
            left = 0.10,
            top = 0.15,
            right = 0.90,
            bottom = 0.85,
        )
        setEditorContent(
            crop = crop,
            sourceImageWidth = 1600,
            sourceImageHeight = 720,
        )

        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_WIDTH_VALUE_TEST_TAG)
            .assertTextContains(expectedWidthText(crop, sourceWidth = 1600, sourceHeight = 720))
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_HEIGHT_VALUE_TEST_TAG)
            .assertTextContains(expectedHeightText(crop, sourceWidth = 1600, sourceHeight = 720))
    }

    @Test
    fun missingSourceDimensionsShowsUnavailableDimensionValues() {
        setEditorContent(
            crop = OcrVisualCropDefaults.FullImageCrop,
            sourceImageWidth = null,
            sourceImageHeight = null,
        )

        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_WIDTH_VALUE_TEST_TAG)
            .assertIsDisplayed()
            .assertTextContains(context.getString(R.string.ocr_visual_crop_dimension_unavailable))
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_HEIGHT_VALUE_TEST_TAG)
            .assertIsDisplayed()
            .assertTextContains(context.getString(R.string.ocr_visual_crop_dimension_unavailable))
    }

    @Test
    fun draggingCropBodyAppliesCumulativeMovementInOneGestureInsideScrollableParent() {
        val startCrop = OcrNormalizedCropRect(
            left = 0.15,
            top = 0.20,
            right = 0.55,
            bottom = 0.70,
        )
        val totalDragX = 160f
        val sourceWidth = 1600
        val sourceHeight = 720
        var crop by mutableStateOf(
            startCrop,
        )
        var latestCrop = crop
        var callbackCount = 0
        setEditorContentInScrollableParent(
            crop = { crop },
            onCropChanged = {
                callbackCount++
                latestCrop = it
                crop = it
            },
        )
        val previewWidthPx = previewWidthPx()
        val initialWidthText = expectedWidthText(startCrop, sourceWidth, sourceHeight)
        val initialHeightText = expectedHeightText(startCrop, sourceWidth, sourceHeight)

        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_MOVE_HANDLE_TEST_TAG)
            .performTouchInput {
                down(center)
                moveBy(Offset(x = 40f, y = 0f))
                moveBy(Offset(x = 40f, y = 0f))
                moveBy(Offset(x = 40f, y = 0f))
                moveBy(Offset(x = 40f, y = 0f))
                up()
            }

        val expectedDelta = totalDragX / previewWidthPx
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            latestCrop.left >= startCrop.left + expectedDelta - 0.04
        }
        composeTestRule.runOnIdle {
            assertEquals(startCrop.left + expectedDelta, latestCrop.left, 0.04)
            assertEquals(startCrop.right + expectedDelta, latestCrop.right, 0.04)
            assertEquals(startCrop.top, latestCrop.top, 0.000_001)
            assertEquals(startCrop.bottom, latestCrop.bottom, 0.000_001)
            assertTrue(callbackCount > 1)
        }
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_WIDTH_VALUE_TEST_TAG)
            .assertTextContains(initialWidthText)
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_HEIGHT_VALUE_TEST_TAG)
            .assertTextContains(initialHeightText)
    }

    @Test
    fun draggingRightEdgeHandleAppliesCumulativeMovementInOneGestureInsideScrollableParent() {
        val startCrop = OcrNormalizedCropRect(
            left = 0.15,
            top = 0.20,
            right = 0.55,
            bottom = 0.70,
        )
        val totalDragX = 160f
        val sourceWidth = 1600
        val sourceHeight = 720
        var crop by mutableStateOf(
            startCrop,
        )
        setEditorContentInScrollableParent(crop = { crop }, onCropChanged = { crop = it })
        val previewWidthPx = previewWidthPx()
        val initialWidthText = expectedWidthText(startCrop, sourceWidth, sourceHeight)
        val initialHeightText = expectedHeightText(startCrop, sourceWidth, sourceHeight)

        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_HANDLE_RIGHT_TEST_TAG)
            .performTouchInput {
                down(center)
                moveBy(Offset(x = 40f, y = 30f))
                moveBy(Offset(x = 40f, y = 30f))
                moveBy(Offset(x = 40f, y = 30f))
                moveBy(Offset(x = 40f, y = 30f))
                up()
            }

        val expectedDelta = totalDragX / previewWidthPx
        composeTestRule.runOnIdle {
            assertEquals(startCrop.left, crop.left, 0.000_001)
            assertEquals(startCrop.top, crop.top, 0.000_001)
            assertEquals(startCrop.right + expectedDelta, crop.right, 0.04)
            assertEquals(startCrop.bottom, crop.bottom, 0.000_001)
        }
        val finalWidthText = expectedWidthText(crop, sourceWidth, sourceHeight)
        assertTrue(finalWidthText != initialWidthText)
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_WIDTH_VALUE_TEST_TAG)
            .assertTextContains(finalWidthText)
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_HEIGHT_VALUE_TEST_TAG)
            .assertTextContains(initialHeightText)
    }

    @Test
    fun resetReturnsCropToDefault() {
        var crop by mutableStateOf(
            OcrNormalizedCropRect(
                left = 0.20,
                top = 0.25,
                right = 0.80,
                bottom = 0.75,
            ),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                OcrVisualCropEditor(
                    imageUri = null,
                    crop = crop,
                    defaultCrop = OcrVisualCropDefaults.FullImageCrop,
                    profile = OcrCropValidationProfiles.Roster,
                    onCropChanged = { crop = it },
                    onConfirmCrop = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_RESET_ACTION_TEST_TAG)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(OcrVisualCropDefaults.FullImageCrop, crop)
        }
    }

    @Test
    fun invalidCropShowsValidationAndBlocksConfirm() {
        setEditorContent(
            crop = OcrNormalizedCropRect(
                left = 0.20,
                top = 0.20,
                right = 0.25,
                bottom = 0.25,
            ),
        )

        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_ERROR_TEST_TAG)
            .assertIsDisplayed()
            .assertTextContains(context.getString(R.string.ocr_visual_crop_too_small))
        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_CONFIRM_ACTION_TEST_TAG)
            .assertIsNotEnabled()
    }

    @Test
    fun validCropAllowsConfirm() {
        var confirmCount = 0
        setEditorContent(
            crop = OcrNormalizedCropRect(
                left = 0.20,
                top = 0.20,
                right = 0.80,
                bottom = 0.80,
            ),
            onConfirmCrop = { confirmCount++ },
        )

        composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_CONFIRM_ACTION_TEST_TAG)
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, confirmCount)
        }
    }

    private fun setEditorContent(
        crop: OcrNormalizedCropRect,
        onConfirmCrop: () -> Unit = {},
        sourceImageWidth: Int? = 1600,
        sourceImageHeight: Int? = 720,
    ) {
        composeTestRule.setContent {
            RankForgeTheme {
                OcrVisualCropEditor(
                    imageUri = null,
                    crop = crop,
                    defaultCrop = OcrVisualCropDefaults.FullImageCrop,
                    profile = OcrCropValidationProfiles.Roster,
                    onCropChanged = {},
                    onConfirmCrop = onConfirmCrop,
                    sourceImageWidth = sourceImageWidth,
                    sourceImageHeight = sourceImageHeight,
                )
            }
        }
    }

    private fun setEditorContentInScrollableParent(
        crop: () -> OcrNormalizedCropRect,
        onCropChanged: (OcrNormalizedCropRect) -> Unit,
    ) {
        composeTestRule.setContent {
            RankForgeTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OcrVisualCropEditor(
                        imageUri = null,
                        crop = crop(),
                        defaultCrop = OcrVisualCropDefaults.FullImageCrop,
                        profile = OcrCropValidationProfiles.Roster,
                        onCropChanged = onCropChanged,
                        onConfirmCrop = {},
                        sourceImageWidth = 1600,
                        sourceImageHeight = 720,
                    )
                }
            }
        }
    }

    private fun previewWidthPx(): Float {
        val bounds = composeTestRule
            .onNodeWithTag(OCR_VISUAL_CROP_PREVIEW_TEST_TAG)
            .getUnclippedBoundsInRoot()
        return (bounds.right.value - bounds.left.value) * context.resources.displayMetrics.density
    }

    private fun expectedWidthText(
        crop: OcrNormalizedCropRect,
        sourceWidth: Int,
        sourceHeight: Int,
    ): String = context.getString(
        R.string.ocr_visual_crop_pixel_value,
        calculateVisualCropPixelSize(crop, sourceWidth, sourceHeight)!!.width,
    )

    private fun expectedHeightText(
        crop: OcrNormalizedCropRect,
        sourceWidth: Int,
        sourceHeight: Int,
    ): String = context.getString(
        R.string.ocr_visual_crop_pixel_value,
        calculateVisualCropPixelSize(crop, sourceWidth, sourceHeight)!!.height,
    )
}
