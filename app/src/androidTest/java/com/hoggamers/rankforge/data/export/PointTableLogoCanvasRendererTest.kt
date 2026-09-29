package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.data.local.PointTableLogoPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PointTableLogoCanvasRendererTest {
    private val renderer = PointTableLogoCanvasRenderer()

    @Test
    fun normalizedPlacementPreservesBitmapAspectRatio() {
        val target = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val logo = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        try {
            val drawn = renderer.draw(
                canvas = Canvas(target),
                targetWidth = target.width,
                targetHeight = target.height,
                renderData = PointTableLogoRenderData(
                    bitmap = logo,
                    placement = PointTableLogoPlacement(
                        tournamentId = "tournament-id",
                        designKey = "IMAGE",
                        centerXRatio = 0.5f,
                        centerYRatio = 0.5f,
                        widthRatio = 0.5f,
                    ),
                ),
            )

            assertTrue(drawn)
            assertEquals(Color.RED, target.getPixel(100, 50))
            assertEquals(0, Color.alpha(target.getPixel(10, 10)))
            assertTrue(nonTransparentPixelCount(target) in 4_000..6_000)
        } finally {
            logo.recycle()
            target.recycle()
        }
    }

    @Test
    fun invalidPlacementDoesNotChangeTarget() {
        val target = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val logo = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }
        try {
            val drawn = renderer.draw(
                canvas = Canvas(target),
                targetWidth = target.width,
                targetHeight = target.height,
                renderData = PointTableLogoRenderData(
                    bitmap = logo,
                    placement = PointTableLogoPlacement(
                        tournamentId = "tournament-id",
                        designKey = "IMAGE",
                        centerXRatio = 0.5f,
                        centerYRatio = 0.5f,
                        widthRatio = 0f,
                    ),
                ),
            )

            assertTrue(!drawn)
            assertEquals(0, nonTransparentPixelCount(target))
        } finally {
            logo.recycle()
            target.recycle()
        }
    }

    private fun nonTransparentPixelCount(bitmap: Bitmap): Int {
        var count = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (Color.alpha(bitmap.getPixel(x, y)) != 0) count++
            }
        }
        return count
    }
}
