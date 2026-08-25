package com.paddle.ocr.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.paddle.ocr.util.OpenCvNativeLoader
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.CvType
import org.opencv.core.Mat

@RunWith(AndroidJUnit4::class)
class OpenCvNativeLoaderTest {
    @Test
    fun nativeLoaderIsIdempotentAndMatConstructionSucceeds() {
        OpenCvNativeLoader.ensureLoaded()
        OpenCvNativeLoader.ensureLoaded()

        val mat = Mat(1, 1, CvType.CV_8UC1)
        try {
            assertFalse(mat.empty())
        } finally {
            mat.release()
        }
    }
}
