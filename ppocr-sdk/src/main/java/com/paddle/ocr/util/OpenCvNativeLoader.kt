package com.paddle.ocr.util

import android.util.Log

/** Loads the OpenCV JNI library once before any OpenCV Java object is used. */
object OpenCvNativeLoader {
    const val LIBRARY_NAME = "opencv_java4"

    private const val TAG = "TEMP_PP_PLAYER_ROW"

    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return

        synchronized(this) {
            if (loaded) return

            Log.w(TAG, "openCvLoadStart openCvLibrary=$LIBRARY_NAME")
            try {
                System.loadLibrary(LIBRARY_NAME)
                loaded = true
                Log.w(TAG, "openCvLoadSuccess openCvLibrary=$LIBRARY_NAME")
            } catch (failure: UnsatisfiedLinkError) {
                Log.w(
                    TAG,
                    "openCvLoadFailure type=${failure.javaClass.simpleName} library=$LIBRARY_NAME message=${failure.message}",
                )
                throw failure
            }
        }
    }
}
