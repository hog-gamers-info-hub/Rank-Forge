package com.hoggamers.rankforge.domain.ocr.extraction

import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterLayoutValidationError
import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterLayoutValidationResult
import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterLayoutValidator
import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterPanelInput
import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterPanelLayout
import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterPlayerRowRegion
import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterSlotRegion
import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxCroppedRosterPanelLayout
import com.hoggamers.rankforge.domain.ocr.layout.NormalizedOcrRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import java.util.concurrent.CancellationException

enum class RosterRawOcrRegionType {
    SLOT_CONTENT,
    SLOT_NUMBER,
    PLAYER_ROW,
}

data class RosterRawOcrRegionIdentity(
    val screenshotPosition: RosterScreenshotPosition,
    val visibleSlotPosition: RosterVisibleSlotPosition,
    val regionType: RosterRawOcrRegionType,
    val playerRowIndex: Int? = null,
) {
    init {
        require(
            (regionType == RosterRawOcrRegionType.PLAYER_ROW && playerRowIndex in 1..4) ||
                (regionType != RosterRawOcrRegionType.PLAYER_ROW && playerRowIndex == null),
        ) { "Only player-row regions may have a player row index from 1 through 4." }
    }

    val intendedTournamentSlotRange: IntRange
        get() = screenshotPosition.tournamentSlotRange

    val intendedTournamentSlot: Int
        get() = screenshotPosition.tournamentSlotFor(visibleSlotPosition)
}

typealias RosterRawOcrConfidence = RawOcrConfidence

data class RosterRawOcrEvidence(
    val text: String,
    val geometry: RawOcrGeometry?,
    val recognizedLanguage: String?,
    val confidence: RosterRawOcrConfidence,
)

data class RosterRawOcrRegionEvidence(
    val regionIdentity: RosterRawOcrRegionIdentity,
    val rawText: String,
    val blocks: List<RawOcrBlock>,
    val rawEvidence: List<RosterRawOcrEvidence>,
    val regionWidth: Int = 0,
    val regionHeight: Int = 0,
    val panelPixelRect: OcrPixelRect? = null,
)

data class RosterRawOcrExtractionInput(
    val croppedPanelImage: OcrPreprocessingImage?,
    val croppedPanelInput: CroppedRosterPanelInput,
    val layout: CroppedRosterPanelLayout = FreeFireMaxCroppedRosterPanelLayout.definition,
    val regionSelection: RosterRawOcrRegionSelection = RosterRawOcrRegionSelection.FULL,
)

enum class RosterRawOcrRegionSelection {
    FULL,
    SLOT_CONTENT_ONLY,
}

data class RosterRawOcrRegionInput(
    val croppedPanelImage: OcrPreprocessingImage,
    val regionIdentity: RosterRawOcrRegionIdentity,
    val pixelRect: OcrPixelRect,
)

enum class RosterRawOcrFailure {
    MISSING_CROPPED_INPUT,
    INVALID_CROPPED_PANEL_DIMENSIONS,
    UNPREPARED_CROPPED_INPUT,
    UNSUPPORTED_SCREENSHOT_POSITION,
    LAYOUT_INCOMPATIBLE,
    INVALID_REGION_BOUNDS,
    RECOGNIZER_INPUT_UNAVAILABLE,
    RECOGNIZER_FAILED,
}

sealed interface RosterRawOcrExtractionResult {
    data class Extracted(
        val evidence: RosterRawOcrRegionEvidence,
    ) : RosterRawOcrExtractionResult

    data class Empty(
        val regionIdentity: RosterRawOcrRegionIdentity,
    ) : RosterRawOcrExtractionResult

    data class Failed(
        val failure: RosterRawOcrFailure,
        val regionIdentity: RosterRawOcrRegionIdentity? = null,
    ) : RosterRawOcrExtractionResult
}

interface RosterRawOcrExtractor {
    suspend fun extract(input: RosterRawOcrExtractionInput): List<RosterRawOcrExtractionResult>
}

interface RosterRawOcrEngine {
    suspend fun recognize(input: RosterRawOcrRegionInput): RawOcrEngineOutput
}

class RosterRawOcrInputException : Exception()

class DefaultRosterRawOcrExtractor(
    private val engine: RosterRawOcrEngine,
    private val layoutValidator: CroppedRosterLayoutValidator = CroppedRosterLayoutValidator(),
) : RosterRawOcrExtractor {
    override suspend fun extract(
        input: RosterRawOcrExtractionInput,
    ): List<RosterRawOcrExtractionResult> {
        val image = input.croppedPanelImage ?: return missingInputFailure()
        val dimensions = image.dimensionsOrNull()
            ?: return listOf(
                RosterRawOcrExtractionResult.Failed(
                    RosterRawOcrFailure.INVALID_CROPPED_PANEL_DIMENSIONS,
                ),
            )
        val (imageWidth, imageHeight) = dimensions
        if (imageWidth != input.croppedPanelInput.imageWidth ||
            imageHeight != input.croppedPanelInput.imageHeight
        ) {
            return listOf(
                RosterRawOcrExtractionResult.Failed(
                    RosterRawOcrFailure.INVALID_CROPPED_PANEL_DIMENSIONS,
                ),
            )
        }

        when (val validation = layoutValidator.validate(input.layout, input.croppedPanelInput)) {
            CroppedRosterLayoutValidationResult.Compatible -> Unit
            is CroppedRosterLayoutValidationResult.Incompatible -> {
                return listOf(RosterRawOcrExtractionResult.Failed(validation.error.toFailure()))
            }
        }

        val screenshotPosition = input.croppedPanelInput.screenshotPosition
            ?: return listOf(
                RosterRawOcrExtractionResult.Failed(
                    RosterRawOcrFailure.UNSUPPORTED_SCREENSHOT_POSITION,
                ),
            )

        return input.layout.regionRequests(screenshotPosition, input.regionSelection).map { request ->
            val pixelRect = request.normalizedRect.toPixelRectOrNull(imageWidth, imageHeight)
            val result = if (pixelRect == null) {
                RosterRawOcrExtractionResult.Failed(
                    RosterRawOcrFailure.INVALID_REGION_BOUNDS,
                    request.regionIdentity,
                )
            } else {
                try {
                    val output = engine.recognize(
                        RosterRawOcrRegionInput(
                            croppedPanelImage = image,
                            regionIdentity = request.regionIdentity,
                            pixelRect = requireNotNull(pixelRect),
                        ),
                    )
                    if (output.fullText.isEmpty()) {
                        RosterRawOcrExtractionResult.Empty(request.regionIdentity)
                    } else {
                        RosterRawOcrExtractionResult.Extracted(
                            output.toRegionEvidence(
                                regionIdentity = request.regionIdentity,
                                pixelRect = requireNotNull(pixelRect),
                            ),
                        )
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: RosterRawOcrInputException) {
                    RosterRawOcrExtractionResult.Failed(
                        RosterRawOcrFailure.RECOGNIZER_INPUT_UNAVAILABLE,
                        request.regionIdentity,
                    )
                } catch (_: Throwable) {
                    RosterRawOcrExtractionResult.Failed(
                        RosterRawOcrFailure.RECOGNIZER_FAILED,
                        request.regionIdentity,
                    )
                }
            }
            result
        }
    }

    private fun missingInputFailure(): List<RosterRawOcrExtractionResult> = listOf(
        RosterRawOcrExtractionResult.Failed(RosterRawOcrFailure.MISSING_CROPPED_INPUT),
    )

    private fun OcrPreprocessingImage.dimensionsOrNull(): Pair<Int, Int>? = try {
        val width = this.width
        val height = this.height
        if (width > 0 && height > 0) width to height else null
    } catch (_: RuntimeException) {
        null
    }

    private fun CroppedRosterLayoutValidationError.toFailure(): RosterRawOcrFailure = when (this) {
        CroppedRosterLayoutValidationError.INVALID_CROPPED_PANEL_DIMENSIONS ->
            RosterRawOcrFailure.INVALID_CROPPED_PANEL_DIMENSIONS
        CroppedRosterLayoutValidationError.UNPREPARED_ROSTER_CROP ->
            RosterRawOcrFailure.UNPREPARED_CROPPED_INPUT
        CroppedRosterLayoutValidationError.UNSUPPORTED_SCREENSHOT_POSITION ->
            RosterRawOcrFailure.UNSUPPORTED_SCREENSHOT_POSITION
        else -> RosterRawOcrFailure.LAYOUT_INCOMPATIBLE
    }

    private fun CroppedRosterPanelLayout.regionRequests(
        screenshotPosition: RosterScreenshotPosition,
        regionSelection: RosterRawOcrRegionSelection,
    ): List<RosterRawOcrRegionRequest> = slots.flatMap { slot ->
        buildList {
            add(
                RosterRawOcrRegionRequest(
                    regionIdentity = slot.regionIdentity(
                        screenshotPosition,
                        RosterRawOcrRegionType.SLOT_CONTENT,
                    ),
                    normalizedRect = slot.contentRect,
                ),
            )
            if (regionSelection == RosterRawOcrRegionSelection.FULL) {
                slot.playerRowRegions.forEach { row ->
                    add(
                        RosterRawOcrRegionRequest(
                            regionIdentity = slot.regionIdentity(
                                screenshotPosition,
                                RosterRawOcrRegionType.PLAYER_ROW,
                                row,
                            ),
                            normalizedRect = row.rect,
                        ),
                    )
                }
            }
        }
    }

    private fun CroppedRosterSlotRegion.regionIdentity(
        screenshotPosition: RosterScreenshotPosition,
        regionType: RosterRawOcrRegionType,
        playerRow: CroppedRosterPlayerRowRegion? = null,
    ): RosterRawOcrRegionIdentity = RosterRawOcrRegionIdentity(
        screenshotPosition = screenshotPosition,
        visibleSlotPosition = visiblePosition,
        regionType = regionType,
        playerRowIndex = playerRow?.rowIndex,
    )

    private fun NormalizedOcrRect.toPixelRectOrNull(
        imageWidth: Int,
        imageHeight: Int,
    ): OcrPixelRect? = try {
        toPixelRect(imageWidth, imageHeight).takeIf { it.width > 0 && it.height > 0 }
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun RawOcrEngineOutput.toRegionEvidence(
        regionIdentity: RosterRawOcrRegionIdentity,
        pixelRect: OcrPixelRect,
    ): RosterRawOcrRegionEvidence = RosterRawOcrRegionEvidence(
        regionIdentity = regionIdentity,
        rawText = fullText,
        blocks = blocks,
        rawEvidence = blocks.flatMap { block ->
            buildList {
                add(block.toRosterEvidence())
                block.lines.forEach { line ->
                    add(line.toRosterEvidence())
                    line.elements.forEach { element ->
                        add(element.toRosterEvidence())
                    }
                }
            }
        },
        regionWidth = pixelRect.width,
        regionHeight = pixelRect.height,
        panelPixelRect = pixelRect,
    )

    private fun RawOcrBlock.toRosterEvidence(): RosterRawOcrEvidence = RosterRawOcrEvidence(
        text = text,
        geometry = geometry,
        recognizedLanguage = recognizedLanguage,
        confidence = confidence,
    )

    private fun RawOcrLine.toRosterEvidence(): RosterRawOcrEvidence = RosterRawOcrEvidence(
        text = text,
        geometry = geometry,
        recognizedLanguage = recognizedLanguage,
        confidence = confidence,
    )

    private fun RawOcrElement.toRosterEvidence(): RosterRawOcrEvidence = RosterRawOcrEvidence(
        text = text,
        geometry = geometry,
        recognizedLanguage = recognizedLanguage,
        confidence = confidence,
    )
}

private data class RosterRawOcrRegionRequest(
    val regionIdentity: RosterRawOcrRegionIdentity,
    val normalizedRect: NormalizedOcrRect,
)
