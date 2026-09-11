package com.paddle.ocr.util


/** Loads the OpenCV JNI library once before any OpenCV Java object is used. */
object OpenCvNativeLoader {
    const val LIBRARY_NAME = "opencv_java4"


    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return

        synchronized(this) {
            if (loaded) return

            try {
                System.loadLibrary(LIBRARY_NAME)
                loaded = true
            } catch (failure: UnsatisfiedLinkError) {
                throw failure
            }
        }
    }
}
