package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignGridGeometry
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
    fun showsOnlyTheFiveLabelFieldsAndUploadAction() {
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
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
        composeTestRule
            .onNodeWithTag(CUSTOM_DESIGN_UPLOAD_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle { assertEquals(1, uploadCount) }
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
    fun decodedImageWithGridGeometryShowsOverlayNode() {
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
}
