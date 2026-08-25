package com.paddle.ocr

import android.graphics.PointF

/** Optional, temporary diagnostics for tracing the detector/recognizer pipeline. */
interface PaddleOcrDiagnosticsListener {
    fun onInvocationEntered(bitmapWidth: Int, bitmapHeight: Int) = Unit

    fun onDetectionComplete(
        inputWidth: Int,
        inputHeight: Int,
        boxes: List<PaddleOcrDetectionBox>,
    ) = Unit

    fun onDetectionCropPrepared(
        boxIndex: Int,
        cropWidth: Int,
        cropHeight: Int,
    ) = Unit

    fun onRecognitionInvocation(
        cropWidths: List<Int>,
        cropHeights: List<Int>,
        inputWidth: Int,
        inputHeight: Int,
    ) = Unit

    fun onDecodedText(boxIndex: Int, text: String, confidence: Float) = Unit

    fun onDirectRecognitionInvocation(bitmapWidth: Int, bitmapHeight: Int) = Unit

    fun onDirectRecognitionInput(inputWidth: Int, inputHeight: Int) = Unit

    fun onDirectDecodedText(text: String, confidence: Float) = Unit
}

data class PaddleOcrDetectionBox(
    val index: Int,
    val points: List<PointF>,
)
