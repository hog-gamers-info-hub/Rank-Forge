package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.hoggamers.rankforge.domain.export.ResultExportRow
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomDesignBitmapComposerTest {
    private val composer = CustomDesignBitmapComposer()

    @Test
    fun validFullResolutionImageComposesWithoutMutatingSource() {
        withFixture { file, originalBytes ->
            val result = composer.compose(file.toURI().toString(), rows(1), geometry())

            val composed = (result as CustomDesignBitmapComposeResult.Success).bitmap
            try {
                assertEquals(320, composed.width)
                assertEquals(240, composed.height)
                assertArrayEquals(originalBytes, file.readBytes())
                assertEquals(Color.WHITE, composed.getPixel(0, 0))
                assertTrue(changedPixelCount(composed, 0, 0, 80, 40) > 0)
            } finally {
                composed.recycle()
            }
        }
    }

    @Test
    fun returnedBitmapIsDistinctAndPreservesUnrenderedSourcePixels() {
        withFixture { file, _ ->
            val original = BitmapFactory.decodeFile(file.absolutePath)
            val result = composer.compose(file.toURI().toString(), rows(1), geometry())
            val composed = (result as CustomDesignBitmapComposeResult.Success).bitmap
            try {
                assertNotSame(original, composed)
                assertEquals(Color.WHITE, original.getPixel(0, 0))
                assertEquals(Color.WHITE, composed.getPixel(0, 0))
                assertEquals(Color.WHITE, composed.getPixel(319, 239))
            } finally {
                original.recycle()
                composed.recycle()
            }
        }
    }

    @Test
    fun geometryDimensionMismatchFailsBeforeRendering() {
        withFixture { file, _ ->
            assertEquals(
                CustomDesignBitmapComposeResult.Failure(
                    CustomDesignBitmapComposeFailure.DIMENSION_MISMATCH,
                ),
                composer.compose(
                    file.toURI().toString(),
                    rows(1),
                    geometry(sourceWidth = 319),
                ),
            )
        }
    }

    @Test
    fun invalidOrNonFileReferenceFails() {
        assertEquals(
            CustomDesignBitmapComposeResult.Failure(
                CustomDesignBitmapComposeFailure.INVALID_IMAGE_REFERENCE,
            ),
            composer.compose("", rows(1), geometry()),
        )
        assertEquals(
            CustomDesignBitmapComposeResult.Failure(
                CustomDesignBitmapComposeFailure.INVALID_IMAGE_REFERENCE,
            ),
            composer.compose("content://not-supported", rows(1), geometry()),
        )
    }

    @Test
    fun missingFileFails() {
        val missing = File(
            ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            "custom-design-does-not-exist.png",
        )
        assertEquals(
            CustomDesignBitmapComposeResult.Failure(
                CustomDesignBitmapComposeFailure.IMAGE_NOT_FOUND,
            ),
            composer.compose(missing.toURI().toString(), rows(1), geometry()),
        )
    }

    @Test
    fun corruptOrNonImageFileFails() {
        val file = File.createTempFile(
            "custom-design-corrupt",
            ".png",
            ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
        )
        try {
            file.writeText("not a bitmap")
            assertEquals(
                CustomDesignBitmapComposeResult.Failure(
                    CustomDesignBitmapComposeFailure.IMAGE_DECODE_FAILED,
                ),
                composer.compose(file.toURI().toString(), rows(1), geometry()),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun rendererValidationFailureDoesNotReturnPartialBitmap() {
        withFixture { file, _ ->
            val invalidGeometry = geometry(
                columnX = mapOf(CustomDesignAnchorField.TEAM_NAME to 40f),
            )
            assertEquals(
                CustomDesignBitmapComposeResult.Failure(
                    CustomDesignBitmapComposeFailure.RENDER_FAILED,
                ),
                composer.compose(file.toURI().toString(), rows(1), invalidGeometry),
            )
        }
    }

    private fun withFixture(block: (File, ByteArray) -> Unit) {
        val file = File.createTempFile(
            "custom-design-source",
            ".png",
            ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
        )
        try {
            FileOutputStream(file).use { output ->
                Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.WHITE)
                    compress(Bitmap.CompressFormat.PNG, 100, output)
                    recycle()
                }
            }
            block(file, file.readBytes())
        } finally {
            file.delete()
        }
    }

    private fun geometry(
        sourceWidth: Int = 320,
        sourceHeight: Int = 240,
        columnX: Map<CustomDesignAnchorField, Float> = mapOf(
            CustomDesignAnchorField.TEAM_NAME to 40f,
            CustomDesignAnchorField.WIN to 100f,
            CustomDesignAnchorField.TOTAL_KILLS to 160f,
            CustomDesignAnchorField.POSITION_POINTS to 220f,
            CustomDesignAnchorField.TOTAL_POINTS to 280f,
        ),
    ) = CustomDesignEffectiveGridGeometry(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        columnX = columnX,
        rowY = (1..12).associateWith { 20f + (it - 1) * 18f },
    )

    private fun rows(count: Int) = (1..count).map { rank ->
        ResultExportRow(
            rank = rank,
            teamName = "TEAM",
            win = 1,
            totalKills = 2,
            positionPoints = 3,
            totalPoints = 4,
        )
    }

    private fun changedPixelCount(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        rightExclusive: Int,
        bottomExclusive: Int,
    ): Int {
        var changed = 0
        for (x in left until rightExclusive) {
            for (y in top until bottomExclusive) {
                if (bitmap.getPixel(x, y) != Color.WHITE) changed++
            }
        }
        return changed
    }
}
