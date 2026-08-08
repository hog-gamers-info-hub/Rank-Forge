package com.hoggamers.rankforge.domain.ocr.extraction

import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCandidate

data class RawOcrPoint(val x: Int, val y: Int)

data class RawOcrBoundingBox(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class RawOcrGeometry(
    val boundingBox: RawOcrBoundingBox?,
    val cornerPoints: List<RawOcrPoint>?,
)

sealed interface RawOcrConfidence {
    data class Available(val value: Float) : RawOcrConfidence
    data object Unavailable : RawOcrConfidence
}

data class RawOcrSymbol(
    val text: String,
    val geometry: RawOcrGeometry?,
    val recognizedLanguage: String?,
    val confidence: RawOcrConfidence,
)

data class RawOcrElement(
    val text: String,
    val geometry: RawOcrGeometry?,
    val recognizedLanguage: String?,
    val confidence: RawOcrConfidence,
    val symbols: List<RawOcrSymbol> = emptyList(),
)

data class RawOcrLine(
    val text: String,
    val geometry: RawOcrGeometry?,
    val recognizedLanguage: String?,
    val confidence: RawOcrConfidence,
    val elements: List<RawOcrElement>,
)

data class RawOcrBlock(
    val text: String,
    val geometry: RawOcrGeometry?,
    val recognizedLanguage: String?,
    val confidence: RawOcrConfidence,
    val lines: List<RawOcrLine>,
)

data class RawOcrExtractionInput(val candidates: List<OcrPreprocessingCandidate>)

data class RawOcrEngineOutput(val fullText: String, val blocks: List<RawOcrBlock>)

enum class RawOcrExtractionFailure { INPUT_UNAVAILABLE, ENGINE_FAILED }

sealed interface RawOcrExtractionResult {
    val sourceCandidate: OcrPreprocessingCandidate

    data class Extracted(
        override val sourceCandidate: OcrPreprocessingCandidate,
        val fullText: String,
        val blocks: List<RawOcrBlock>,
    ) : RawOcrExtractionResult

    data class Empty(override val sourceCandidate: OcrPreprocessingCandidate) : RawOcrExtractionResult

    data class Failed(
        override val sourceCandidate: OcrPreprocessingCandidate,
        val failure: RawOcrExtractionFailure,
    ) : RawOcrExtractionResult
}

interface RawOcrTextExtractor { suspend fun extract(input: RawOcrExtractionInput): List<RawOcrExtractionResult> }
