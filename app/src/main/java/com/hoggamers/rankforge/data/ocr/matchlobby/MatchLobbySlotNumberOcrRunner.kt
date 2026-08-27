package com.hoggamers.rankforge.data.ocr.matchlobby

import android.util.Log
import android.graphics.Bitmap
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.ocr.preprocessing.AndroidBitmapOcrImage
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractor
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionEvidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionSelection
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCrop
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropBounds
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropGeometryCalculator
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropGeometryResult
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropSlotGeometry
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropUnavailableReason
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotContentSlotNumberExtractor
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyGridReconstructionResult
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyObservedSlotAnchor
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotGridRole
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotGridReconstructor
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotNumberCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparer
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationResult
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import com.hoggamers.rankforge.presentation.screen.NoOpScreenshotOwnerProvider
import com.hoggamers.rankforge.presentation.screen.ScreenshotOwnerProvider
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

data class MatchLobbySlotNumberOcrSlot(
    val visibleSlotPosition: RosterVisibleSlotPosition,
    val candidate: RosterSlotNumberCandidate,
)

interface MatchLobbyTeamCropPreviewImage

data class AndroidMatchLobbyTeamCropPreviewImage(
    val bitmap: Bitmap,
) : MatchLobbyTeamCropPreviewImage

data class MatchLobbyTeamCropPreview(
    val visibleSlotPosition: RosterVisibleSlotPosition,
    val detectedSlotNumber: Int,
    val image: MatchLobbyTeamCropPreviewImage,
    val playerRowPreviews: List<LobbyPlayerRowCropPreview> = emptyList(),
)

enum class MatchLobbyTeamCropPreviewUnavailableReason {
    REQUIRED_SLOT_NUMBER_UNAVAILABLE,
    SLOT_NUMBER_GEOMETRY_UNAVAILABLE,
    INVALID_TEAM_GRID_GEOMETRY,
    INVALID_CROP_BOUNDS,
    BITMAP_CREATION_FAILED,
}

sealed interface MatchLobbyTeamCropPreviewResult {
    data class Available(
        val previews: List<MatchLobbyTeamCropPreview>,
    ) : MatchLobbyTeamCropPreviewResult {
        init {
            require(previews.map { it.visibleSlotPosition } == RosterVisibleSlotPosition.entries) {
                "Team crop previews must contain every visible slot position exactly once."
            }
        }
    }

    data class Unavailable(
        val reason: MatchLobbyTeamCropPreviewUnavailableReason,
    ) : MatchLobbyTeamCropPreviewResult
}

enum class MatchLobbySlotNumberOcrUnavailableReason {
    INVALID_MATCH_CONTEXT,
    OWNER_UNAVAILABLE,
    ASSET_UNAVAILABLE,
    INVALID_ASSET_CROP,
    PANEL_PREPARATION_FAILED,
    EXTRACTION_FAILED,
    PANEL_RELEASE_FAILED,
}

sealed interface MatchLobbySlotNumberOcrScreenshotResult {
    val screenshotPosition: RosterScreenshotPosition

    data class Processed(
        override val screenshotPosition: RosterScreenshotPosition,
        val slots: List<MatchLobbySlotNumberOcrSlot>,
        val teamCropPreviews: MatchLobbyTeamCropPreviewResult = MatchLobbyTeamCropPreviewResult.Unavailable(
            MatchLobbyTeamCropPreviewUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE,
        ),
    ) : MatchLobbySlotNumberOcrScreenshotResult {
        init {
            require(slots.map { it.visibleSlotPosition } == RosterVisibleSlotPosition.entries) {
                "Processed slot-number OCR must contain every visible slot position exactly once."
            }
        }
    }

    data class Unavailable(
        override val screenshotPosition: RosterScreenshotPosition,
        val reason: MatchLobbySlotNumberOcrUnavailableReason,
    ) : MatchLobbySlotNumberOcrScreenshotResult
}

data class MatchLobbySlotNumberOcrResult(
    val screenshots: List<MatchLobbySlotNumberOcrScreenshotResult>,
) {
    init {
        require(screenshots.map { it.screenshotPosition } == RosterScreenshotPosition.entries) {
            "Slot-number OCR must return one outcome for every lobby screenshot position."
        }
    }
}

fun interface MatchLobbySlotNumberOcrRunner {
    suspend fun process(tournamentId: String, matchId: String): MatchLobbySlotNumberOcrResult
}

@Singleton
class AndroidMatchLobbySlotNumberOcrRunner @Inject constructor(
    private val assetRepository: MatchLobbyScreenshotAssetRepository,
    private val panelPreparer: RosterOcrPanelPreparer,
    private val extractor: RosterRawOcrExtractor,
    private val screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
    private val playerRowCropPipeline: LobbyPlayerRowCropPipeline = NoOpLobbyPlayerRowCropPipeline,
) : MatchLobbySlotNumberOcrRunner {
    private val gridReconstructor = LobbySlotGridReconstructor()

    internal var teamCropPreviewFactory: MatchLobbyTeamCropPreviewFactory =
        AndroidMatchLobbyTeamCropPreviewFactory

    override suspend fun process(
        tournamentId: String,
        matchId: String,
    ): MatchLobbySlotNumberOcrResult {
        logPhase1("processStart")
        val result = if (tournamentId.isBlank() || matchId.isBlank()) {
            unavailableForAll(MatchLobbySlotNumberOcrUnavailableReason.INVALID_MATCH_CONTEXT)
        } else {
            val ownerUserId = screenshotOwnerProvider.currentOwnerUserId()?.takeIf { it.isNotBlank() }
            if (ownerUserId == null) {
                unavailableForAll(MatchLobbySlotNumberOcrUnavailableReason.OWNER_UNAVAILABLE)
            } else {
                MatchLobbySlotNumberOcrResult(
                    RosterScreenshotPosition.entries.map { position ->
                        processScreenshot(tournamentId, matchId, position, ownerUserId)
                    },
                )
            }
        }
        logPhase1("processComplete")
        return result
    }

    private suspend fun processScreenshot(
        tournamentId: String,
        matchId: String,
        position: RosterScreenshotPosition,
        ownerUserId: String,
    ): MatchLobbySlotNumberOcrScreenshotResult {
        logPhase1("screenshotStart screenshotIndex=${position.index}")
        val identity = MatchLobbyScreenshotIdentity(tournamentId, matchId, position.index)
        val asset = try {
            assetRepository.getByIdentityAndOwner(identity, ownerUserId)
        } catch (cancellation: CancellationException) {
            logPhase1("cancelled stage=assetLookup screenshotIndex=${position.index}")
            throw cancellation
        } catch (failure: Throwable) {
            logFailure("assetLookup", position, failure)
            return unavailable(position, MatchLobbySlotNumberOcrUnavailableReason.ASSET_UNAVAILABLE)
        } ?: return unavailable(position, MatchLobbySlotNumberOcrUnavailableReason.ASSET_UNAVAILABLE)

        val source = asset.toRosterOcrScreenshotSource(position, identity)
            ?: return unavailable(position, MatchLobbySlotNumberOcrUnavailableReason.INVALID_ASSET_CROP)
        val prepared = try {
            panelPreparer.prepare(source)
        } catch (cancellation: CancellationException) {
            logPhase1("cancelled stage=panelPreparation screenshotIndex=${position.index}")
            throw cancellation
        } catch (failure: Throwable) {
            logFailure("panelPreparation", position, failure)
            return unavailable(position, MatchLobbySlotNumberOcrUnavailableReason.PANEL_PREPARATION_FAILED)
        }
        val panel = when (prepared) {
            is RosterOcrPanelPreparationResult.Failed ->
                return unavailable(position, MatchLobbySlotNumberOcrUnavailableReason.PANEL_PREPARATION_FAILED)
            is RosterOcrPanelPreparationResult.Prepared -> prepared.panel
        }
        logPhase1("panelPrepared screenshotIndex=${position.index}")

        var extraction: List<RosterRawOcrExtractionResult>? = null
        var extractionFailure: Throwable? = null
        try {
            extraction = extractor.extract(
                RosterRawOcrExtractionInput(
                    croppedPanelImage = panel.croppedPanelImage,
                    croppedPanelInput = panel.croppedPanelInput,
                    regionSelection = RosterRawOcrRegionSelection.SLOT_CONTENT_ONLY,
                ),
            )
            logPhase1("extractionReturned screenshotIndex=${position.index} resultCount=${extraction.size}")
        } catch (throwable: Throwable) {
            extractionFailure = throwable
        }

        extractionFailure?.let { failure ->
            releasePanel(panel, position)?.let { releaseFailure ->
                if (releaseFailure is CancellationException) {
                    logPhase1("cancelled stage=panelRelease screenshotIndex=${position.index}")
                    throw releaseFailure
                }
                logFailure("panelRelease", position, releaseFailure)
            }
            if (failure is CancellationException) {
                logPhase1("cancelled stage=extraction screenshotIndex=${position.index}")
                throw failure
            }
            logFailure("extraction", position, failure)
            return unavailable(position, MatchLobbySlotNumberOcrUnavailableReason.EXTRACTION_FAILED)
        }
        val candidates = LobbySlotContentSlotNumberExtractor.derive(requireNotNull(extraction))
        val slots = RosterVisibleSlotPosition.entries.map { visiblePosition ->
            val candidate = candidates[visiblePosition] ?: RosterSlotNumberCandidate.unavailable()
            logPhase1(
                "screenshotIndex=${position.index} " +
                    "visiblePosition=${visiblePosition.name} " +
                    "status=${candidate.status.name} " +
                    "detectedNumber=${candidate.detectedSlotNumber}",
            )
            MatchLobbySlotNumberOcrSlot(
                visibleSlotPosition = visiblePosition,
                candidate = candidate,
            )
        }
        val teamCropPreviews = createTeamCropPreviews(
            panelImage = panel.croppedPanelImage,
            slots = slots,
            screenshotIndex = position.index,
        )
        releasePanel(panel, position)?.let { failure ->
            if (failure is CancellationException) {
                logPhase1("cancelled stage=panelRelease screenshotIndex=${position.index}")
                throw failure
            }
            logFailure("panelRelease", position, failure)
            return unavailable(position, MatchLobbySlotNumberOcrUnavailableReason.PANEL_RELEASE_FAILED)
        }
        logPhase1("screenshotComplete screenshotIndex=${position.index}")
        return MatchLobbySlotNumberOcrScreenshotResult.Processed(
            screenshotPosition = position,
            slots = slots,
            teamCropPreviews = teamCropPreviews,
        )
    }

    private fun releasePanel(
        panel: com.hoggamers.rankforge.domain.ocr.review.RosterOcrPreparedPanel,
        position: RosterScreenshotPosition,
    ): Throwable? = try {
        panel.release()
        null
    } catch (failure: Throwable) {
        failure
    }

    private suspend fun createTeamCropPreviews(
        panelImage: OcrPreprocessingImage,
        slots: List<MatchLobbySlotNumberOcrSlot>,
        screenshotIndex: Int,
    ): MatchLobbyTeamCropPreviewResult {
        val geometryBySlot = mutableMapOf<Int, LobbyTeamCropBounds>()
        val observedAnchors = slots.mapNotNull { slot ->
            val candidate = slot.candidate
            val detectedSlotNumber = candidate.detectedSlotNumber ?: return@mapNotNull null
            if (candidate.status != RosterCandidateParseStatus.PARSED ||
                detectedSlotNumber !in expectedSlotRange(screenshotIndex)
            ) {
                return@mapNotNull null
            }
            val evidence = candidate.rawSourceResults
                .filterIsInstance<RosterRawOcrExtractionResult.Extracted>()
                .map { it.evidence }
            val numberBounds = evidence.asSequence()
                .mapNotNull { it.slotNumberBoundsOrNull(detectedSlotNumber) }
                .firstOrNull()
                ?: return@mapNotNull null
            geometryBySlot[detectedSlotNumber] = numberBounds
            LobbyObservedSlotAnchor(
                slotNumber = detectedSlotNumber,
                centerX = numberBounds.centerX,
                centerY = numberBounds.centerY,
            )
        }
        if (observedAnchors.size < 2) {
            return unavailableTeamCrops(MatchLobbyTeamCropPreviewUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE)
        }
        val reconstructed = when (
            val result = gridReconstructor.reconstruct(screenshotIndex, observedAnchors)
        ) {
            is LobbyGridReconstructionResult.Reconstructed -> result.grid
            LobbyGridReconstructionResult.InsufficientAnchors,
            LobbyGridReconstructionResult.InvalidSlotGroup,
            LobbyGridReconstructionResult.DuplicateSlot,
            LobbyGridReconstructionResult.InvalidGeometry,
            -> return unavailableTeamCrops(MatchLobbyTeamCropPreviewUnavailableReason.INVALID_TEAM_GRID_GEOMETRY)
        }
        if (reconstructed.points.any { point ->
                point.centerX < 0.0 || point.centerX > panelImage.width.toDouble() ||
                    point.centerY < 0.0 || point.centerY > panelImage.height.toDouble()
            }
        ) {
            return unavailableTeamCrops(MatchLobbyTeamCropPreviewUnavailableReason.INVALID_TEAM_GRID_GEOMETRY)
        }
        val geometry = if (observedAnchors.size == RosterVisibleSlotPosition.entries.size) {
            val geometrySlots = slots.map { slot ->
                val candidate = slot.candidate
                val detectedSlotNumber = candidate.detectedSlotNumber
                    ?: return unavailableTeamCrops(MatchLobbyTeamCropPreviewUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE)
                val numberBounds = geometryBySlot[detectedSlotNumber]
                    ?: return unavailableTeamCrops(MatchLobbyTeamCropPreviewUnavailableReason.SLOT_NUMBER_GEOMETRY_UNAVAILABLE)
                LobbyTeamCropSlotGeometry(
                    visibleSlotPosition = slot.visibleSlotPosition,
                    detectedSlotNumber = detectedSlotNumber,
                    slotNumberBounds = numberBounds,
                )
            }
            LobbyTeamCropGeometryCalculator.calculate(
                panelWidth = panelImage.width,
                panelHeight = panelImage.height,
                slots = geometrySlots,
            )
        } else {
            val observedSlotLeftInsets = observedAnchors.mapNotNull { anchor ->
                val role = LobbySlotGridRole.fromSlotNumber(anchor.slotNumber)
                    ?: return@mapNotNull null
                val slotLeftInset = when (role) {
                    LobbySlotGridRole.TOP_LEFT,
                    LobbySlotGridRole.BOTTOM_LEFT,
                    -> anchor.centerX
                    LobbySlotGridRole.TOP_RIGHT,
                    LobbySlotGridRole.BOTTOM_RIGHT,
                    -> anchor.centerX - reconstructed.columnPitch
                }
                slotLeftInset
            }
            LobbyTeamCropGeometryCalculator.calculate(
                panelWidth = panelImage.width,
                panelHeight = panelImage.height,
                grid = reconstructed,
                observedSlotLeftInsets = observedSlotLeftInsets,
            )
        }
        val available = geometry as? LobbyTeamCropGeometryResult.Available
            ?: return unavailableTeamCrops(geometry.toPreviewUnavailableReason())
        return try {
            val previews = available.crops.map { crop ->
                val image = teamCropPreviewFactory.create(panelImage, crop)
                val rowPreviews = when (
                    val generated = playerRowCropPipeline.generate(
                        authoritativeTeamSlotNumber = crop.detectedSlotNumber,
                        teamCropImage = image,
                    )
                ) {
                    is LobbyPlayerRowCropGenerationResult.Generated -> generated.rows
                    LobbyPlayerRowCropGenerationResult.NotAvailable -> emptyList()
                }
                MatchLobbyTeamCropPreview(
                    visibleSlotPosition = crop.visibleSlotPosition,
                    detectedSlotNumber = crop.detectedSlotNumber,
                    image = image,
                    playerRowPreviews = rowPreviews,
                )
            }
            MatchLobbyTeamCropPreviewResult.Available(previews)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            unavailableTeamCrops(MatchLobbyTeamCropPreviewUnavailableReason.BITMAP_CREATION_FAILED)
        }
    }

    private fun expectedSlotRange(screenshotIndex: Int): IntRange =
        RosterScreenshotPosition.fromIndex(screenshotIndex)?.tournamentSlotRange ?: 1..0

    private fun RosterRawOcrRegionEvidence.slotNumberBoundsOrNull(
        number: Int,
    ): LobbyTeamCropBounds? {
        val elementBounds = blocks.flatMap { block ->
            block.lines.flatMap { line ->
                line.elements.mapNotNull { element ->
                    numericBoundsOrNull(element.text, element.geometry?.boundingBox, number)
                }
            }
        }
        if (elementBounds.isNotEmpty()) return elementBounds.first()
        val lineBounds = blocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                numericBoundsOrNull(line.text, line.geometry?.boundingBox, number)
            }
        }
        if (lineBounds.isNotEmpty()) return lineBounds.first()
        return blocks.mapNotNull { block ->
            numericBoundsOrNull(block.text, block.geometry?.boundingBox, number)
        }.firstOrNull()
    }

    private fun RosterRawOcrRegionEvidence.numericBoundsOrNull(
        text: String,
        boundingBox: RawOcrBoundingBox?,
        number: Int,
    ): LobbyTeamCropBounds? {
        if (text.trim() != number.toString() || regionWidth <= 0) return null
        val local = boundingBox ?: return null
        val localCenterX = (local.left + local.right) / 2.0
        if (localCenterX / regionWidth !in 0.0..0.15) return null
        return local.toPanelBoundsOrNull(panelPixelRect?.x, panelPixelRect?.y)
    }

    private fun RawOcrBoundingBox.toPanelBoundsOrNull(
        originX: Int?,
        originY: Int?,
    ): LobbyTeamCropBounds? {
        if (originX == null || originY == null || right <= left || bottom <= top) return null
        return LobbyTeamCropBounds(
            left = (left + originX).toDouble(),
            top = (top + originY).toDouble(),
            right = (right + originX).toDouble(),
            bottom = (bottom + originY).toDouble(),
        )
    }

    private fun LobbyTeamCropGeometryResult.toPreviewUnavailableReason(): MatchLobbyTeamCropPreviewUnavailableReason =
        when ((this as LobbyTeamCropGeometryResult.Unavailable).reason) {
            LobbyTeamCropUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE ->
                MatchLobbyTeamCropPreviewUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE
            LobbyTeamCropUnavailableReason.SLOT_NUMBER_GEOMETRY_UNAVAILABLE ->
                MatchLobbyTeamCropPreviewUnavailableReason.SLOT_NUMBER_GEOMETRY_UNAVAILABLE
            LobbyTeamCropUnavailableReason.INVALID_TEAM_GRID_GEOMETRY ->
                MatchLobbyTeamCropPreviewUnavailableReason.INVALID_TEAM_GRID_GEOMETRY
            LobbyTeamCropUnavailableReason.INVALID_CROP_BOUNDS ->
                MatchLobbyTeamCropPreviewUnavailableReason.INVALID_CROP_BOUNDS
        }

    private fun unavailableTeamCrops(
        reason: MatchLobbyTeamCropPreviewUnavailableReason,
    ): MatchLobbyTeamCropPreviewResult.Unavailable = MatchLobbyTeamCropPreviewResult.Unavailable(reason)

    private fun unavailableForAll(
        reason: MatchLobbySlotNumberOcrUnavailableReason,
    ): MatchLobbySlotNumberOcrResult = MatchLobbySlotNumberOcrResult(
        RosterScreenshotPosition.entries.map { position ->
            logPhase1("screenshotStart screenshotIndex=${position.index}")
            unavailable(position, reason)
        },
    )

    private fun unavailable(
        position: RosterScreenshotPosition,
        reason: MatchLobbySlotNumberOcrUnavailableReason,
    ): MatchLobbySlotNumberOcrScreenshotResult.Unavailable {
        logPhase1("screenshotIndex=${position.index} unavailableReason=${reason.name}")
        return MatchLobbySlotNumberOcrScreenshotResult.Unavailable(position, reason)
    }

    private fun logFailure(
        stage: String,
        position: RosterScreenshotPosition,
        failure: Throwable,
    ) {
        logPhase1(
            "failed stage=$stage screenshotIndex=${position.index} " +
                "exceptionType=${failure.javaClass.simpleName}",
        )
    }

    private fun logPhase1(message: String) {
        runCatching { Log.w(TEMP_LOBBY_SLOT_PHASE1_TAG, message) }
    }
}

internal fun interface MatchLobbyTeamCropPreviewFactory {
    fun create(
        panelImage: OcrPreprocessingImage,
        crop: LobbyTeamCrop,
    ): MatchLobbyTeamCropPreviewImage
}

private object AndroidMatchLobbyTeamCropPreviewFactory : MatchLobbyTeamCropPreviewFactory {
    override fun create(
        panelImage: OcrPreprocessingImage,
        crop: LobbyTeamCrop,
    ): MatchLobbyTeamCropPreviewImage {
        val source = (panelImage as? AndroidBitmapOcrImage)?.bitmap
            ?: throw IllegalStateException("Prepared panel bitmap is unavailable.")
        val left = kotlin.math.floor(crop.bounds.left).toInt()
        val top = kotlin.math.floor(crop.bounds.top).toInt()
        val right = kotlin.math.ceil(crop.bounds.right).toInt()
        val bottom = kotlin.math.ceil(crop.bounds.bottom).toInt()
        val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        val preview = cropped.copy(Bitmap.Config.ARGB_8888, false) ?: cropped
        if (preview !== cropped && !cropped.isRecycled) cropped.recycle()
        return AndroidMatchLobbyTeamCropPreviewImage(preview)
    }
}

private const val TEMP_LOBBY_SLOT_PHASE1_TAG = "TEMP_LOBBY_SLOT_PHASE1"
