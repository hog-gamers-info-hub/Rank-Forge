package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadResultScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun generatedFreeDesignPreviewReplacesComingSoonAndEnablesDownload() {
        var downloadCalls = 0
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = {},
                    initialDesign = DownloadResultDesignType.FREE_DESIGN,
                    previewState = DownloadResultPreviewState.ResultImage(testPngBytes()),
                    onDownload = { _, design ->
                        assertEquals(DownloadResultDesignType.FREE_DESIGN, design)
                        downloadCalls++
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Free Design").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Coming soon...").assertCountEquals(0)
        composeTestRule.onNodeWithText("Download").assertIsEnabled().performClick()
        composeTestRule.runOnIdle { assertEquals(1, downloadCalls) }
    }

    private fun testPngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
