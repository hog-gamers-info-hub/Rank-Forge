package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMatchResultRowOcrPreprocessorTest {
    private val preprocessor = AndroidMatchResultRowOcrPreprocessor()

    @Test
    fun enhancedCandidatesPreserveAspectRatioArgbFormatAndCallerOwnership() {
        val source = Bitmap.createBitmap(7, 3, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xff7f7f7f.toInt())
        }
        try {
            val threeX = preprocessor.create(source, MatchResultRowOcrCandidate.SCALE_3X)
            val fourX = preprocessor.create(source, MatchResultRowOcrCandidate.SCALE_4X)
            assertNotNull(threeX)
            assertNotNull(fourX)
            assertEquals(21, threeX!!.width)
            assertEquals(9, threeX.height)
            assertEquals(28, fourX!!.width)
            assertEquals(12, fourX.height)
            assertEquals(Bitmap.Config.ARGB_8888, threeX.config)
            assertFalse(source.isRecycled)
            threeX.recycle()
            fourX.recycle()
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    @Test
    fun preprocessingIsDeterministicAndRejectsInvalidSource() {
        val source = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888).apply {
            setPixels(
                intArrayOf(0xff000000.toInt(), 0xffffffff.toInt(), 0xff202020.toInt(), 0xffe0e0e0.toInt(),
                    0xff101010.toInt(), 0xfff0f0f0.toInt(), 0xff303030.toInt(), 0xffd0d0d0.toInt()),
                0,
                4,
                0,
                0,
                4,
                2,
            )
        }
        try {
            val first = preprocessor.create(source, MatchResultRowOcrCandidate.SCALE_3X)!!
            val second = preprocessor.create(source, MatchResultRowOcrCandidate.SCALE_3X)!!
            try {
                val firstPixels = IntArray(first.width * first.height)
                val secondPixels = IntArray(second.width * second.height)
                first.getPixels(firstPixels, 0, first.width, 0, 0, first.width, first.height)
                second.getPixels(secondPixels, 0, second.width, 0, 0, second.width, second.height)
                assertTrue(firstPixels.contentEquals(secondPixels))
            } finally {
                first.recycle()
                second.recycle()
            }
        } finally {
            source.recycle()
        }
        assertNull(preprocessor.create(source, MatchResultRowOcrCandidate.SCALE_3X))
    }
}
