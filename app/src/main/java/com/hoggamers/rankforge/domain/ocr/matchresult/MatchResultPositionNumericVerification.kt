package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
/** A single focused numeric-crop reading retained for trust diagnostics. */
data class MatchResultNumericCandidate(
    val variant: MatchResultNumericCropVariant,
    val rawText: String,
    val value: Int?,
    val confidence: Float?,
)

enum class MatchResultNumericCropVariant {
    ORIGINAL,
    UPSCALE_2X,
    UPSCALE_3X,
}

/** Internal trust state; unresolved and conflicting values never become guesses. */
sealed interface MatchResultNumericVerification {
    val candidates: List<MatchResultNumericCandidate>

    data class Verified(
        val value: Int,
        override val candidates: List<MatchResultNumericCandidate>,
    ) : MatchResultNumericVerification

    data class Unresolved(
        override val candidates: List<MatchResultNumericCandidate>,
    ) : MatchResultNumericVerification

    data class Conflict(
        override val candidates: List<MatchResultNumericCandidate>,
    ) : MatchResultNumericVerification
}

object MatchResultNumericConsensus {
    fun resolve(candidates: List<MatchResultNumericCandidate>): MatchResultNumericVerification {
        val usable = candidates.filter { it.value != null }
        if (usable.isEmpty()) return MatchResultNumericVerification.Unresolved(candidates)

        val byValue = usable.groupBy { it.value!! }
        if (byValue.size == 1) {
            val only = byValue.values.single()
            return if (only.size >= 2) {
                MatchResultNumericVerification.Verified(
                    value = only.first().value!!,
                    candidates = candidates,
                )
            } else {
                MatchResultNumericVerification.Unresolved(candidates)
            }
        }

        val strongest = byValue.values
            .sortedWith(compareByDescending<List<MatchResultNumericCandidate>> { it.size }
                .thenByDescending { it.maxOfOrNull { candidate -> candidate.confidence ?: Float.NEGATIVE_INFINITY } ?: Float.NEGATIVE_INFINITY })
        val first = strongest.first()
        val second = strongest.getOrNull(1)
        return if (second != null && first.size > second.size) {
            MatchResultNumericVerification.Verified(first.first().value!!, candidates)
        } else {
            MatchResultNumericVerification.Conflict(candidates)
        }
    }
}

data class MatchResultPositionSequenceValidation(
    val expectedPositions: List<Int>,
    val actualPositions: List<Int>,
    val isValid: Boolean,
    val outOfSequence: Boolean = false,
    val duplicatePositions: List<Int> = emptyList(),
    val missingPositions: List<Int> = emptyList(),
    val unexpectedPositions: List<Int> = emptyList(),
)

object MatchResultPositionSequenceValidator {
    fun validate(
        role: com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole,
        positions: List<Int>,
        allowUpperPositionElevenFallback: Boolean = false,
    ): MatchResultPositionSequenceValidation {
        val expected = when (role) {
            com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole.MATCH_RESULT_UPPER ->
                if (allowUpperPositionElevenFallback) (1..11).toList() else (1..10).toList()
            com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole.MATCH_RESULT_LOWER ->
                (11..12).toList()
        }
        val duplicates = positions.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
        val missing = expected.filter { it !in positions }
        val unexpected = positions.filter { it !in expected }.distinct().sorted()
        val expectedPresentInOrder = expected.filter { it in positions }
        return MatchResultPositionSequenceValidation(
            expectedPositions = expected,
            actualPositions = positions,
            isValid = positions == expected,
            outOfSequence = positions != expectedPresentInOrder,
            duplicatePositions = duplicates,
            missingPositions = missing,
            unexpectedPositions = unexpected,
        )
    }
}

enum class MatchResultFocusedNumericField {
    PLACEMENT,
    KILL_SLOT_1,
    KILL_SLOT_2,
    KILL_SLOT_3,
    KILL_SLOT_4,
}

/** Measured, position-local micro-crop layout for the basic numeric verifier. */
object MatchResultPositionFocusedNumericCropLayout {
    fun boundsOrNull(
        role: MatchResultScreenshotRole,
        position: Int,
        field: MatchResultFocusedNumericField,
        imageWidth: Int,
        imageHeight: Int,
        row: MatchResultPositionRowCrop? = null,
    ): OcrPixelCropRect? {
        if (
            imageWidth <= 0 ||
            imageHeight <= 0 ||
            (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER && position !in 1..11) ||
            (role == MatchResultScreenshotRole.MATCH_RESULT_LOWER && position !in 11..12)
        ) return null
        val horizontal = when (field) {
            MatchResultFocusedNumericField.PLACEMENT -> 0.02..0.14
            MatchResultFocusedNumericField.KILL_SLOT_1,
            MatchResultFocusedNumericField.KILL_SLOT_2 -> if (position <= 5) 0.34..0.43 else 0.40..0.52
            MatchResultFocusedNumericField.KILL_SLOT_3,
            MatchResultFocusedNumericField.KILL_SLOT_4 -> if (position <= 5) 0.80..0.91 else 0.81..0.92
        }
        val vertical = if (field == MatchResultFocusedNumericField.PLACEMENT) {
            0..imageHeight
        } else {
            val bounds = row?.bounds ?: return null
            bounds.top.coerceIn(0, imageHeight)..bounds.bottom.coerceIn(0, imageHeight)
        }
        val left = (imageWidth * horizontal.start).toInt().coerceIn(0, imageWidth)
        val right = (imageWidth * horizontal.endInclusive).toInt().coerceIn(0, imageWidth)
        val top = vertical.first.coerceIn(0, imageHeight)
        val bottom = vertical.last.coerceIn(0, imageHeight)
        return if (right > left && bottom > top) OcrPixelCropRect(left, top, right, bottom) else null
    }
}
