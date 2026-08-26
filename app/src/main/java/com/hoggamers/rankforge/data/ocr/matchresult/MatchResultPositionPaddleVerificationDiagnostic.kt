package com.hoggamers.rankforge.data.ocr.matchresult

import android.graphics.Bitmap
import android.util.Log
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultFocusedNumericField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultNumericCandidate
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultNumericVerification
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionFocusedNumericCropLayout
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionOcrFieldMapper
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionOcrInput
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionRowCrop
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionSemanticResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionSequenceValidator
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.presentation.screen.AndroidMatchResultPositionCropPreviewImage
import com.hoggamers.rankforge.presentation.screen.MatchResultPositionCropPreview
import java.util.Locale
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** TEMPORARY Phase 2 semantic verification diagnostic. REMOVE BEFORE COMMIT. */
@Singleton
class MatchResultPositionPaddleVerificationDiagnostic @Inject constructor(
    private val numericVerifier: AndroidMatchResultPositionPaddleNumericVerifier,
    private val batchTracker: MatchResultPositionPaddleVerificationBatchTracker,
) {
    private val mapper = MatchResultPositionOcrFieldMapper()

    /** Diagnostics only; the returned Phase 1 row generation result is never changed. */
    suspend fun verify(
        positionPreview: MatchResultPositionCropPreview,
        rowResult: MatchResultPositionRowCropGenerationResult,
    ) {
        val role = positionPreview.role ?: return
        val bitmap = (positionPreview.image as? AndroidMatchResultPositionCropPreviewImage)?.bitmap
            ?: return
        val position = positionPreview.position
        val dimensions = bitmap.safeDiagnosticDimensions() ?: run {
            log("role=$role position=$position status=DIAGNOSTIC_FAILED")
            return
        }
        log("role=$role position=$position crop=${dimensions.first}x${dimensions.second} status=START")

        val rows = (rowResult as? MatchResultPositionRowCropGenerationResult.Generated)
            ?.crops
            ?.sortedBy { it.geometry.rowIndex }
            .orEmpty()
        rows.forEach { row ->
            val bounds = row.geometry.bounds
            log("role=$role position=$position row=${row.geometry.rowIndex} " +
                "rowBounds=[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]")
        }

        val placement = verifyField(
            bitmap = bitmap,
            role = role,
            position = position,
            field = MatchResultFocusedNumericField.PLACEMENT,
            row = null,
            slot = null,
            dimensions = dimensions,
        )
        val kills = linkedMapOf<Int, MatchResultNumericVerification>()
        (1..4).forEach { slot ->
            val row = rows.firstOrNull { it.geometry.rowIndex == slotRow(slot) }
            kills[slot] = verifyField(
                bitmap = bitmap,
                role = role,
                position = position,
                field = slotField(slot),
                row = row?.geometry,
                slot = slot,
                dimensions = dimensions,
            )
        }

        val semantic = mapper.map(
            MatchResultPositionOcrInput(
                role = role,
                position = position,
                cropWidth = dimensions.first,
                cropHeight = dimensions.second,
                blocks = positionPreview.temporaryPpEvidence?.blocks.orEmpty(),
                rowCrops = rows.map { it.geometry },
                placementVerification = placement,
                killVerifications = kills,
            ),
        )
        logPlayerPresence(role, position, semantic)
        logBasicSemantic(role, position, semantic)
        logPositionSummary(role, position, semantic)
        batchTracker.record(role, positionPreview.allowUpperPositionElevenFallback, semantic)
    }

    private suspend fun verifyField(
        bitmap: Bitmap,
        role: MatchResultScreenshotRole,
        position: Int,
        field: MatchResultFocusedNumericField,
        row: MatchResultPositionRowCrop?,
        slot: Int?,
        dimensions: Pair<Int, Int>,
    ): MatchResultNumericVerification {
        val bounds = MatchResultPositionFocusedNumericCropLayout.boundsOrNull(
            role = role,
            position = position,
            field = field,
            imageWidth = dimensions.first,
            imageHeight = dimensions.second,
            row = row,
        )
        val prefix = buildString {
            append("role=$role position=$position")
            if (slot != null) append(" slot=$slot field=KILL row=${slotRow(slot)}")
            else append(" field=$field expected=$position")
        }
        log("$prefix focusedBounds=${bounds?.let { "[${it.left},${it.top},${it.right},${it.bottom}]" } ?: "null"}")
        val result = if (bounds == null) {
            MatchResultNumericVerification.Unresolved(emptyList())
        } else {
            try {
                numericVerifier.verify(bitmap, bounds)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                MatchResultNumericVerification.Unresolved(emptyList())
            }
        }
        result.candidates.forEach { candidate ->
            logCandidate(role, position, slot, field, candidate)
        }
        val finalValue = (result as? MatchResultNumericVerification.Verified)?.value
        log(
            "$prefix verification=${result.verificationName()} value=${finalValue ?: "null"}",
        )
        return result
    }

    private fun logCandidate(
        role: MatchResultScreenshotRole,
        position: Int,
        slot: Int?,
        field: MatchResultFocusedNumericField,
        candidate: MatchResultNumericCandidate,
    ) {
        val raw = candidate.rawText
            .takeIf { candidate.value != null }
            ?.escapeDiagnosticText()
            ?: "<rejected>"
        val normalized = candidate.value?.toString() ?: "null"
        val confidence = candidate.confidence?.let { String.format(Locale.US, "%.3f", it) } ?: "null"
        val fieldName = if (slot == null) "PLACEMENT" else "KILL"
        log(
            buildString {
                append("role=$role position=$position")
                if (slot != null) append(" slot=$slot")
                append(" field=$fieldName variant=${candidate.variant} raw=\"$raw\" " +
                    "normalized=$normalized confidence=$confidence")
            },
        )
    }

    private fun logPlayerPresence(
        role: MatchResultScreenshotRole,
        position: Int,
        semantic: MatchResultPositionSemanticResult,
    ) {
        semantic.fields
            .filter { it.type == MatchResultOcrFieldType.PLAYER }
            .sortedBy { it.slot }
            .forEach { player ->
                val slot = requireNotNull(player.slot)
                val verification = semantic.killVerifications[slot]
                    ?: MatchResultNumericVerification.Unresolved(emptyList())
                val value = (verification as? MatchResultNumericVerification.Verified)?.value
                log(
                    "role=$role position=$position slot=$slot playerPresent=${player.resolvedText.isNotBlank()} " +
                        "killVerification=${verification.verificationName()} killValue=${value ?: "null"}",
                )
            }
    }

    private fun logPositionSummary(
        role: MatchResultScreenshotRole,
        position: Int,
        semantic: MatchResultPositionSemanticResult,
    ) {
        val placement = semantic.placementVerification.summaryValue()
        val slots = (1..4).joinToString(" ") { slot ->
            val player = semantic.fields.first { it.type == MatchResultOcrFieldType.PLAYER && it.slot == slot }
            val verification = semantic.killVerifications[slot]
                ?: MatchResultNumericVerification.Unresolved(emptyList())
            "slot$slot=" + if (player.resolvedText.isBlank()) "NO_PLAYER" else verification.summaryValue()
        }
        log(
            "role=$role position=$position placement=$placement $slots " +
                "isAutoAcceptable=${semantic.isAutoAcceptable} status=COMPLETE",
        )
    }

    private fun logBasicSemantic(
        role: MatchResultScreenshotRole,
        position: Int,
        semantic: MatchResultPositionSemanticResult,
    ) {
        (1..4).forEach { slot ->
            val parsed = semantic.basicKillEvidence[slot]
            val field = semantic.fields.first { it.type == MatchResultOcrFieldType.KILL && it.slot == slot }
            val source = when {
                parsed?.markerMatched != true -> "UNRESOLVED"
                parsed.prefixType == com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultEliminationPrefixType.EXPLICIT_NUMERIC -> "EXPLICIT_NUMERIC"
                parsed.prefixType == com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultEliminationPrefixType.O_NORMALIZED -> "O_NORMALIZED"
                else -> "EMPTY_PREFIX_ZERO"
            }
            log(
                "role=$role position=$position slot=$slot basicKill=${field.resolvedText.ifBlank { "null" }} " +
                    "source=$source markerMatched=${parsed?.markerMatched == true} prefixType=${parsed?.prefixType ?: "NONE"}",
            )
        }
        val slotValues = (1..4).joinToString(" ") { slot ->
            val field = semantic.fields.first { it.type == MatchResultOcrFieldType.KILL && it.slot == slot }
            "slot${slot}Kill=${field.resolvedText.ifBlank { "null" }}"
        }
        log(
            "role=$role position=$position basicPlacement=$position $slotValues status=BASIC_PARSED",
        )
    }

    private fun log(message: String) = Log.i(RESULT_POSITION_PP_VERIFY_LOG_TAG, message)

    private companion object {
        const val RESULT_POSITION_PP_VERIFY_LOG_TAG = "RESULT_POSITION_PP_VERIFY"
    }
}

/** TEMPORARY Phase 2 semantic verification diagnostic. REMOVE BEFORE COMMIT. */
@Singleton
class MatchResultPositionPaddleVerificationBatchTracker @Inject constructor() {
    private val lock = Any()
    private val batches = mutableMapOf<BatchKey, MutableMap<Int, MatchResultPositionSemanticResult>>()
    private val emitted = mutableSetOf<BatchKey>()

    fun record(role: MatchResultScreenshotRole, allowUpperFallback: Boolean, result: MatchResultPositionSemanticResult) {
        val key = BatchKey(role, allowUpperFallback)
        val expected = expectedPositions(role, allowUpperFallback)
        val complete: Map<Int, MatchResultPositionSemanticResult>?
        synchronized(lock) {
            if (result.position == expected.first()) {
                batches.remove(key)
                emitted.remove(key)
            }
            val batch = batches.getOrPut(key) { linkedMapOf() }
            batch[result.position] = result
            complete = if (expected.all { it in batch.keys } && key !in emitted) {
                emitted += key
                batch.toMap()
            } else null
        }
        if (complete != null) logBatch(role, allowUpperFallback, complete, expected)
    }

    private fun logBatch(
        role: MatchResultScreenshotRole,
        allowUpperFallback: Boolean,
        results: Map<Int, MatchResultPositionSemanticResult>,
        expected: List<Int>,
    ) {
        val sequence = MatchResultPositionSequenceValidator.validate(role, results.keys.toList(), allowUpperFallback)
        val unresolved = results.values.count { result ->
            result.placementVerification is MatchResultNumericVerification.Unresolved ||
                result.killVerifications.values.any { it is MatchResultNumericVerification.Unresolved }
        }
        val conflicts = results.values.count { result ->
            result.placementVerification is MatchResultNumericVerification.Conflict ||
                result.killVerifications.values.any { it is MatchResultNumericVerification.Conflict }
        }
        val positions = results.keys.sorted().joinToString(",")
        val sequenceText = if (sequence.isValid) {
            "VALID"
        } else {
            "INVALID reason=${sequenceReason(sequence)}"
        }
        Log.i(
            RESULT_POSITION_PP_VERIFY_LOG_TAG,
            "role=$role expectedPositions=${expected.first()}-${expected.last()} " +
                "positionsProcessed=${results.size} autoAcceptablePositions=${results.values.count { it.isAutoAcceptable }} " +
                "unresolvedPositions=$unresolved conflictPositions=$conflicts sequence=$sequenceText " +
                "status=COMPLETE positions=$positions",
        )
    }

    private data class BatchKey(val role: MatchResultScreenshotRole, val allowUpperFallback: Boolean)

    private companion object {
        const val RESULT_POSITION_PP_VERIFY_LOG_TAG = "RESULT_POSITION_PP_VERIFY"

        fun expectedPositions(role: MatchResultScreenshotRole, allowUpperFallback: Boolean): List<Int> = when (role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> if (allowUpperFallback) (1..11).toList() else (1..10).toList()
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> (11..12).toList()
        }
    }
}

private fun MatchResultNumericVerification.verificationName(): String = when (this) {
    is MatchResultNumericVerification.Verified -> "VERIFIED"
    is MatchResultNumericVerification.Unresolved -> "UNRESOLVED"
    is MatchResultNumericVerification.Conflict -> "CONFLICT"
}

private fun MatchResultNumericVerification.summaryValue(): String = when (this) {
    is MatchResultNumericVerification.Verified -> "VERIFIED:$value"
    is MatchResultNumericVerification.Unresolved -> "UNRESOLVED"
    is MatchResultNumericVerification.Conflict -> "CONFLICT"
}

private fun String.escapeDiagnosticText(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

private fun Bitmap.safeDiagnosticDimensions(): Pair<Int, Int>? = try {
    if (isRecycled || width <= 0 || height <= 0) null else width to height
} catch (_: Throwable) {
    null
}

private fun slotRow(slot: Int): Int = if (slot == 1 || slot == 3) 1 else 2

private fun slotField(slot: Int): MatchResultFocusedNumericField = when (slot) {
    1 -> MatchResultFocusedNumericField.KILL_SLOT_1
    2 -> MatchResultFocusedNumericField.KILL_SLOT_2
    3 -> MatchResultFocusedNumericField.KILL_SLOT_3
    else -> MatchResultFocusedNumericField.KILL_SLOT_4
}

private fun sequenceReason(validation: com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionSequenceValidation): String =
    listOfNotNull(
        validation.missingPositions.takeIf { it.isNotEmpty() }?.let { "missing=${it.joinToString(",")}" },
        validation.duplicatePositions.takeIf { it.isNotEmpty() }?.let { "duplicate=${it.joinToString(",")}" },
        validation.unexpectedPositions.takeIf { it.isNotEmpty() }?.let { "unexpected=${it.joinToString(",")}" },
        validation.outOfSequence.takeIf { it }?.let { "out_of_sequence" },
    ).joinToString(";").ifBlank { "invalid" }
