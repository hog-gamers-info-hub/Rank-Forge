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
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MatchResultOcrCacheCodec @Inject constructor() {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(processed: MatchResultOcrPreviewProcessingResult.Processed): String =
        json.encodeToString(processed.toDto())

    fun decode(payload: String): MatchResultOcrPreviewProcessingResult.Processed? = runCatching {
        val dto = json.decodeFromString<MatchResultOcrCachedProcessedDto>(payload)
        if (dto.payloadVersion != MATCH_RESULT_OCR_CACHE_PAYLOAD_VERSION) return null
        if (dto.cropWidth <= 0 || dto.cropHeight <= 0) return null
        if (
            dto.pixelCrop.left < 0 ||
            dto.pixelCrop.top < 0 ||
            dto.pixelCrop.right <= dto.pixelCrop.left ||
            dto.pixelCrop.bottom <= dto.pixelCrop.top
        ) return null

        MatchResultOcrPreviewProcessingResult.Processed(
            extraction = dto.extraction.toDomain(),
            pixelCrop = OcrPixelCropRect(
                left = dto.pixelCrop.left,
                top = dto.pixelCrop.top,
                right = dto.pixelCrop.right,
                bottom = dto.pixelCrop.bottom,
            ),
            cropWidth = dto.cropWidth,
            cropHeight = dto.cropHeight,
        )
    }.getOrNull()
}

private const val MATCH_RESULT_OCR_CACHE_PAYLOAD_VERSION = 1

@Serializable
private data class MatchResultOcrCachedProcessedDto(
    val payloadVersion: Int,
    val extraction: MatchResultOcrExtractionDto,
    val pixelCrop: MatchResultOcrPixelCropDto,
    val cropWidth: Int,
    val cropHeight: Int,
)

@Serializable
private data class MatchResultOcrExtractionDto(
    val role: String,
    val fields: List<MatchResultOcrFieldDto>,
    val rows: List<MatchResultOcrRowDto>,
    val ignoredLowerRows: List<MatchResultOcrIgnoredLowerVisualRowDto>,
    val manualReviewRows: List<MatchResultOcrManualReviewRowDto>,
)

@Serializable
private data class MatchResultOcrFieldDto(
    val id: String,
    val type: String,
    val position: Int?,
    val visualRow: String?,
    val slot: Int?,
    val canonicalRect: MatchResultOcrRectDto,
    val mappedRect: MatchResultOcrRectDto,
    val ocrText: String,
    val resolvedText: String,
    val status: String,
)

@Serializable
private data class MatchResultOcrRectDto(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

@Serializable
private data class MatchResultOcrRowDto(
    val position: Int,
    val source: String,
    val placement: MatchResultOcrFieldDto,
    val playerSlots: List<MatchResultOcrPlayerSlotDto>,
)

@Serializable
private data class MatchResultOcrPlayerSlotDto(
    val slot: Int,
    val player: MatchResultOcrFieldDto,
    val kill: MatchResultOcrFieldDto,
)

@Serializable
private data class MatchResultOcrIgnoredLowerVisualRowDto(
    val visualRow: String,
    val detectedPlacement: Int?,
    val reason: String,
)

@Serializable
private data class MatchResultOcrManualReviewRowDto(
    val visualRow: String,
    val detectedPlacementText: String,
    val reason: String,
)

@Serializable
private data class MatchResultOcrPixelCropDto(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

private fun MatchResultOcrPreviewProcessingResult.Processed.toDto() = MatchResultOcrCachedProcessedDto(
    payloadVersion = MATCH_RESULT_OCR_CACHE_PAYLOAD_VERSION,
    extraction = extraction.toDto(),
    pixelCrop = MatchResultOcrPixelCropDto(
        left = pixelCrop.left,
        top = pixelCrop.top,
        right = pixelCrop.right,
        bottom = pixelCrop.bottom,
    ),
    cropWidth = cropWidth,
    cropHeight = cropHeight,
)

private fun MatchResultOcrExtractionResult.toDto() = MatchResultOcrExtractionDto(
    role = role.name,
    fields = fields.map { it.toDto() },
    rows = rows.map { row ->
        MatchResultOcrRowDto(
            position = row.position,
            source = row.source.name,
            placement = row.placement.toDto(),
            playerSlots = row.playerSlots.map { slot ->
                MatchResultOcrPlayerSlotDto(
                    slot = slot.slot,
                    player = slot.player.toDto(),
                    kill = slot.kill.toDto(),
                )
            },
        )
    },
    ignoredLowerRows = ignoredLowerRows.map { row ->
        MatchResultOcrIgnoredLowerVisualRowDto(
            visualRow = row.visualRow.name,
            detectedPlacement = row.detectedPlacement,
            reason = row.reason.name,
        )
    },
    manualReviewRows = manualReviewRows.map { row ->
        MatchResultOcrManualReviewRowDto(
            visualRow = row.visualRow.name,
            detectedPlacementText = row.detectedPlacementText,
            reason = row.reason.name,
        )
    },
)

private fun MatchResultOcrField.toDto() = MatchResultOcrFieldDto(
    id = id,
    type = type.name,
    position = position,
    visualRow = visualRow?.name,
    slot = slot,
    canonicalRect = canonicalRect.toDto(),
    mappedRect = mappedRect.toDto(),
    ocrText = ocrText,
    resolvedText = resolvedText,
    status = status.name,
)

private fun MatchResultOcrRect.toDto() = MatchResultOcrRectDto(
    left = left,
    top = top,
    right = right,
    bottom = bottom,
)

private fun MatchResultOcrExtractionDto.toDomain() = MatchResultOcrExtractionResult(
    role = MatchResultScreenshotRole.valueOf(role),
    fields = fields.map { it.toDomain() },
    rows = rows.map { row ->
        MatchResultOcrRow(
            position = row.position,
            source = MatchResultOcrRowSource.valueOf(row.source),
            placement = row.placement.toDomain(),
            playerSlots = row.playerSlots.map { slot ->
                MatchResultOcrPlayerSlot(
                    slot = slot.slot,
                    player = slot.player.toDomain(),
                    kill = slot.kill.toDomain(),
                )
            },
        )
    },
    ignoredLowerRows = ignoredLowerRows.map { row ->
        MatchResultOcrIgnoredLowerVisualRow(
            visualRow = MatchResultOcrVisualRow.valueOf(row.visualRow),
            detectedPlacement = row.detectedPlacement,
            reason = MatchResultOcrIgnoredLowerVisualRowReason.valueOf(row.reason),
        )
    },
    manualReviewRows = manualReviewRows.map { row ->
        MatchResultOcrManualReviewRow(
            visualRow = MatchResultOcrVisualRow.valueOf(row.visualRow),
            detectedPlacementText = row.detectedPlacementText,
            reason = MatchResultOcrManualReviewReason.valueOf(row.reason),
        )
    },
)

private fun MatchResultOcrFieldDto.toDomain() = MatchResultOcrField(
    id = id,
    type = MatchResultOcrFieldType.valueOf(type),
    position = position,
    visualRow = visualRow?.let(MatchResultOcrVisualRow::valueOf),
    slot = slot,
    canonicalRect = canonicalRect.toDomain(),
    mappedRect = mappedRect.toDomain(),
    ocrText = ocrText,
    resolvedText = resolvedText,
    status = MatchResultOcrFieldStatus.valueOf(status),
)

private fun MatchResultOcrRectDto.toDomain() = MatchResultOcrRect(
    left = left,
    top = top,
    right = right,
    bottom = bottom,
)
