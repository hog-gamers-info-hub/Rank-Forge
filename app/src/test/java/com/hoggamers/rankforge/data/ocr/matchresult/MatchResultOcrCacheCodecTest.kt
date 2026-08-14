package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrIgnoredLowerVisualRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrIgnoredLowerVisualRowReason
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrManualReviewReason
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrManualReviewRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrPlayerSlot
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrVisualRow
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchResultOcrCacheCodecTest {
    private val codec = MatchResultOcrCacheCodec()

    @Test
    fun roundTripPreservesCompleteProcessedResult() {
        val original = processed()

        assertEquals(original, codec.decode(codec.encode(original)))
    }

    @Test
    fun malformedJsonReturnsCacheMiss() {
        assertNull(codec.decode("not-json"))
    }

    @Test
    fun unsupportedPayloadVersionReturnsCacheMiss() {
        val encoded = codec.encode(processed())

        assertNull(codec.decode(encoded.replace("\"payloadVersion\":1", "\"payloadVersion\":999")))
    }

    @Test
    fun unknownEnumReturnsCacheMiss() {
        val encoded = codec.encode(processed())

        assertNull(codec.decode(encoded.replace("\"source\":\"UPPER_TEMPLATE\"", "\"source\":\"UNKNOWN\"")))
    }

    private fun processed() = MatchResultOcrPreviewProcessingResult.Processed(
        extraction = MatchResultOcrExtractionResult(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            fields = listOf(
                field(
                    id = "placement-1",
                    type = MatchResultOcrFieldType.PLACEMENT,
                    position = 1,
                    visualRow = null,
                    slot = null,
                    status = MatchResultOcrFieldStatus.DIRECT_NUMERIC,
                ),
                field(
                    id = "player-1",
                    type = MatchResultOcrFieldType.PLAYER,
                    position = 1,
                    visualRow = MatchResultOcrVisualRow.A,
                    slot = 1,
                    status = MatchResultOcrFieldStatus.OCR_MATCH,
                ),
                field(
                    id = "kill-1",
                    type = MatchResultOcrFieldType.KILL,
                    position = 1,
                    visualRow = MatchResultOcrVisualRow.A,
                    slot = 1,
                    status = MatchResultOcrFieldStatus.OCR_MISMATCH,
                ),
            ),
            rows = listOf(
                MatchResultOcrRow(
                    position = 1,
                    source = MatchResultOcrRowSource.UPPER_TEMPLATE,
                    placement = field(
                        id = "placement-1",
                        type = MatchResultOcrFieldType.PLACEMENT,
                        position = 1,
                        visualRow = null,
                        slot = null,
                        status = MatchResultOcrFieldStatus.DIRECT_NUMERIC,
                    ),
                    playerSlots = (1..4).map { slot ->
                        MatchResultOcrPlayerSlot(
                            slot = slot,
                            player = field(
                                id = "player-$slot",
                                type = MatchResultOcrFieldType.PLAYER,
                                position = 1,
                                visualRow = if (slot % 2 == 0) MatchResultOcrVisualRow.B else MatchResultOcrVisualRow.A,
                                slot = slot,
                                status = MatchResultOcrFieldStatus.DIRECT_TEXT,
                            ),
                            kill = field(
                                id = "kill-$slot",
                                type = MatchResultOcrFieldType.KILL,
                                position = 1,
                                visualRow = if (slot % 2 == 0) MatchResultOcrVisualRow.B else MatchResultOcrVisualRow.A,
                                slot = slot,
                                status = MatchResultOcrFieldStatus.EMPTY,
                            ),
                        )
                    },
                ),
            ),
            ignoredLowerRows = listOf(
                MatchResultOcrIgnoredLowerVisualRow(
                    visualRow = MatchResultOcrVisualRow.B,
                    detectedPlacement = 1,
                    reason = MatchResultOcrIgnoredLowerVisualRowReason.UPPER_OWNS_POSITION,
                ),
            ),
            manualReviewRows = listOf(
                MatchResultOcrManualReviewRow(
                    visualRow = MatchResultOcrVisualRow.A,
                    detectedPlacementText = "?",
                    reason = MatchResultOcrManualReviewReason.INVALID_PLACEMENT,
                ),
            ),
        ),
        pixelCrop = OcrPixelCropRect(left = 10, top = 20, right = 900, bottom = 700),
        cropWidth = 800,
        cropHeight = 600,
    )

    private fun field(
        id: String,
        type: MatchResultOcrFieldType,
        position: Int?,
        visualRow: MatchResultOcrVisualRow?,
        slot: Int?,
        status: MatchResultOcrFieldStatus,
    ) = MatchResultOcrField(
        id = id,
        type = type,
        position = position,
        visualRow = visualRow,
        slot = slot,
        canonicalRect = MatchResultOcrRect(0.1, 0.2, 0.3, 0.4),
        mappedRect = MatchResultOcrRect(10.0, 20.0, 30.0, 40.0),
        ocrText = "ocr-$id",
        resolvedText = "resolved-$id",
        status = status,
    )
}
