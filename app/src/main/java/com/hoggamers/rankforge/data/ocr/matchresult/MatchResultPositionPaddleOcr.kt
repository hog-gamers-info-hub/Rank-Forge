package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionColumn
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole

/** Raw PP-OCR evidence tied to the authoritative Phase 1 position crop metadata. */
data class MatchResultPositionPaddleOcrEvidence(
    val role: MatchResultScreenshotRole,
    val position: Int,
    val column: MatchResultPositionColumn,
    val cropWidth: Int,
    val cropHeight: Int,
    val blocks: List<RawOcrBlock>,
) {
    init {
        require(position in 1..12) { "Result position must be in 1..12." }
        require(cropWidth > 0) { "Position crop width must be positive." }
        require(cropHeight > 0) { "Position crop height must be positive." }
    }
}

enum class MatchResultPositionPaddleOcrFailure {
    INVALID_SOURCE,
    OCR_INITIALIZATION_FAILED,
    OCR_RECOGNITION_FAILED,
}

sealed interface MatchResultPositionPaddleOcrResult {
    data class Success(
        val evidence: MatchResultPositionPaddleOcrEvidence,
    ) : MatchResultPositionPaddleOcrResult

    data class Failed(
        val reason: MatchResultPositionPaddleOcrFailure,
    ) : MatchResultPositionPaddleOcrResult
}

typealias MatchResultPositionPaddleOcrRecognitionResult = MatchResultPositionPaddleOcrResult
