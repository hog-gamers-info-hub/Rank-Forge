package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole

enum class MatchResultOcrFieldType {
    PLACEMENT,
    PLAYER,
    KILL,
}

enum class MatchResultOcrFieldStatus {
    DIRECT_NUMERIC,
    DIRECT_TEXT,
    TEMPLATE_ONLY,
    OCR_MATCH,
    OCR_MISMATCH,
    EMPTY,
    O_NORMALIZED_TO_0,
    ZERO_INFERRED_FROM_PLAYER_PRESENT,
}

enum class MatchResultOcrRowSource {
    UPPER_TEMPLATE,
    LOWER_ROW_A,
    LOWER_ROW_B,
}

enum class MatchResultOcrIgnoredLowerVisualRowReason {
    UPPER_OWNS_POSITION,
}

enum class MatchResultOcrManualReviewReason {
    MISSING_PLACEMENT,
    INVALID_PLACEMENT,
    UNSUPPORTED_PLACEMENT,
}

data class MatchResultOcrRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

data class MatchResultOcrField(
    val id: String,
    val type: MatchResultOcrFieldType,
    val position: Int?,
    val visualRow: MatchResultOcrVisualRow?,
    val slot: Int?,
    val canonicalRect: MatchResultOcrRect,
    val mappedRect: MatchResultOcrRect,
    val ocrText: String,
    val resolvedText: String,
    val status: MatchResultOcrFieldStatus,
)

enum class MatchResultOcrVisualRow {
    A,
    B,
}

data class MatchResultOcrPlayerSlot(
    val slot: Int,
    val player: MatchResultOcrField,
    val kill: MatchResultOcrField,
)

data class MatchResultOcrRow(
    val position: Int,
    val source: MatchResultOcrRowSource,
    val placement: MatchResultOcrField,
    val playerSlots: List<MatchResultOcrPlayerSlot>,
)

data class MatchResultOcrIgnoredLowerVisualRow(
    val visualRow: MatchResultOcrVisualRow,
    val detectedPlacement: Int?,
    val reason: MatchResultOcrIgnoredLowerVisualRowReason,
)

data class MatchResultOcrManualReviewRow(
    val visualRow: MatchResultOcrVisualRow,
    val detectedPlacementText: String,
    val reason: MatchResultOcrManualReviewReason,
)

data class MatchResultOcrExtractionResult(
    val role: MatchResultScreenshotRole,
    val fields: List<MatchResultOcrField>,
    val rows: List<MatchResultOcrRow>,
    val ignoredLowerRows: List<MatchResultOcrIgnoredLowerVisualRow> = emptyList(),
    val manualReviewRows: List<MatchResultOcrManualReviewRow> = emptyList(),
)
