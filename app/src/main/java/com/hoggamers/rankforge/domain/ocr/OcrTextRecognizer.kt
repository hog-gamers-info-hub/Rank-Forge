package com.hoggamers.rankforge.domain.ocr

data class OcrImageInput(
    val sourceUri: String,
)

sealed interface OcrRecognitionResult {
    data class RecognizedText(
        val text: String,
    ) : OcrRecognitionResult

    data class Failed(
        val failure: OcrRecognitionFailure,
    ) : OcrRecognitionResult
}

enum class OcrRecognitionFailure {
    INPUT_UNAVAILABLE,
    RECOGNITION_FAILED,
}

interface OcrTextRecognizer {
    suspend fun recognize(input: OcrImageInput): OcrRecognitionResult
}
