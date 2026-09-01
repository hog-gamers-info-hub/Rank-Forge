package com.hoggamers.rankforge.data.ocr.matchlobby

import android.graphics.Bitmap
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.ocr.preprocessing.AndroidBitmapOcrImage
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCrop
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotNumberCandidate
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
    val authoritativeTeamSlotNumber: Int = 0,
)

sealed interface MatchLobbyTeamCropPreviewOutcome {
    val visibleSlotPosition: RosterVisibleSlotPosition

    data class Available(
        val preview: MatchLobbyTeamCropPreview,
    ) : MatchLobbyTeamCropPreviewOutcome {
        override val visibleSlotPosition: RosterVisibleSlotPosition
            get() = preview.visibleSlotPosition
    }

    data class Unavailable(
        override val visibleSlotPosition: RosterVisibleSlotPosition,
        val reason: MatchLobbyTeamCropPreviewUnavailableReason,
    ) : MatchLobbyTeamCropPreviewOutcome
}

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
        val unavailable: List<MatchLobbyTeamCropPreviewOutcome.Unavailable> = emptyList(),
    ) : MatchLobbyTeamCropPreviewResult {
        init {
            val positions = previews.map { it.visibleSlotPosition } + unavailable.map { it.visibleSlotPosition }
            require(positions.size == RosterVisibleSlotPosition.entries.size &&
                positions.toSet().size == positions.size &&
                positions.toSet() == RosterVisibleSlotPosition.entries.toSet()
            ) {
                "Team crop previews must contain one outcome for every visible slot position."
            }
        }

        val outcomes: List<MatchLobbyTeamCropPreviewOutcome>
            get() = RosterVisibleSlotPosition.entries.map { visibleSlotPosition ->
                previews.firstOrNull { it.visibleSlotPosition == visibleSlotPosition }
                    ?.let(MatchLobbyTeamCropPreviewOutcome::Available)
                    ?: unavailable.first { it.visibleSlotPosition == visibleSlotPosition }
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
    SEMANTIC_POSITION_UNRESOLVED,
    SEMANTIC_POSITION_CONFLICT,
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
    private val screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
    private val panelPpRuntime: LobbyPanelPpOcrRuntime = NoOpLobbyPanelPpOcrRuntime,
) : MatchLobbySlotNumberOcrRunner {
    internal var teamCropPreviewFactory: MatchLobbyTeamCropPreviewFactory =
        AndroidMatchLobbyTeamCropPreviewFactory

    override suspend fun process(
        tournamentId: String,
        matchId: String,
    ): MatchLobbySlotNumberOcrResult {
        return if (tournamentId.isBlank() || matchId.isBlank()) {
            unavailableForAll(MatchLobbySlotNumberOcrUnavailableReason.INVALID_MATCH_CONTEXT)
        } else {
            val ownerUserId = screenshotOwnerProvider.currentOwnerUserId()?.takeIf { it.isNotBlank() }
            if (ownerUserId == null) {
                unavailableForAll(MatchLobbySlotNumberOcrUnavailableReason.OWNER_UNAVAILABLE)
            } else {
                LobbySemanticPositionReconciler.reconcile(
                    RosterScreenshotPosition.entries.map { position ->
                        processScreenshot(tournamentId, matchId, position, ownerUserId)
                    },
                )
            }
        }
    }

    private suspend fun processScreenshot(
        tournamentId: String,
        matchId: String,
        position: RosterScreenshotPosition,
        ownerUserId: String,
    ): LobbyPhysicalProcessingOutcome {
        val identity = MatchLobbyScreenshotIdentity(tournamentId, matchId, position.index)

        val asset = try {
            assetRepository.getByIdentityAndOwner(identity, ownerUserId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return unavailableOutcome(position, MatchLobbySlotNumberOcrUnavailableReason.ASSET_UNAVAILABLE)
        } ?: return unavailableOutcome(position, MatchLobbySlotNumberOcrUnavailableReason.ASSET_UNAVAILABLE)

        val source = asset.toRosterOcrScreenshotSource(position, identity)
            ?: return unavailableOutcome(position, MatchLobbySlotNumberOcrUnavailableReason.INVALID_ASSET_CROP)

        val prepared = try {
            panelPreparer.prepare(source)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return unavailableOutcome(position, MatchLobbySlotNumberOcrUnavailableReason.PANEL_PREPARATION_FAILED)
        }
        val panel = when (prepared) {
            is RosterOcrPanelPreparationResult.Failed ->
                return unavailableOutcome(position, MatchLobbySlotNumberOcrUnavailableReason.PANEL_PREPARATION_FAILED)
            is RosterOcrPanelPreparationResult.Prepared -> prepared.panel
        }

        val panelBitmap = (panel.croppedPanelImage as? AndroidBitmapOcrImage)?.bitmap
        if (panelBitmap == null || panelBitmap.isRecycled || panelBitmap.width <= 0 || panelBitmap.height <= 0) {
            releasePanel(panel, position)?.let { releaseFailure ->
                if (releaseFailure is CancellationException) {
                    throw releaseFailure
                }
            }
            return unavailableOutcome(position, MatchLobbySlotNumberOcrUnavailableReason.INVALID_ASSET_CROP)
        }

        val ppRecognition = try {
            panelPpRuntime.recognize(panelBitmap, position.index)
        } catch (cancellation: CancellationException) {
            releasePanel(panel, position)?.let { releaseFailure ->
                if (releaseFailure is CancellationException) throw releaseFailure
            }
            throw cancellation
        } catch (_: Throwable) {
            releasePanel(panel, position)?.let { releaseFailure ->
                if (releaseFailure is CancellationException) throw releaseFailure
            }
            return unavailableOutcome(position, MatchLobbySlotNumberOcrUnavailableReason.EXTRACTION_FAILED)
        }

        val mapping = LobbyPanelPpMapper.map(
            panelWidth = panelBitmap.width,
            panelHeight = panelBitmap.height,
            fragments = ppRecognition.fragments,
        )
        val resolved = when (mapping) {
            is LobbyPanelSemanticMappingResult.Unavailable -> {
                val reason = when (mapping.failure) {
                    LobbyPanelSemanticMappingFailure.SEMANTIC_POSITION_UNRESOLVED ->
                        MatchLobbySlotNumberOcrUnavailableReason.SEMANTIC_POSITION_UNRESOLVED
                    LobbyPanelSemanticMappingFailure.SEMANTIC_POSITION_CONFLICT ->
                        MatchLobbySlotNumberOcrUnavailableReason.SEMANTIC_POSITION_CONFLICT
                }
                releasePanel(panel, position)?.let { failure ->
                    if (failure is CancellationException) throw failure
                    return unavailableOutcome(position, MatchLobbySlotNumberOcrUnavailableReason.PANEL_RELEASE_FAILED)
                }
                return unavailableOutcome(position, reason)
            }
            is LobbyPanelSemanticMappingResult.Available -> {
                val semanticPosition = mapping.screenshotPosition
                val panelMapping = mapping.mapping
                val outcomes = createMatchLobbyTeamCropPreviewOutcomes(
                    panelImage = panel.croppedPanelImage,
                    semanticPosition = semanticPosition,
                    teams = panelMapping.teams,
                    factory = teamCropPreviewFactory,
                )
                val previews = MatchLobbyTeamCropPreviewResult.Available(
                    previews = outcomes.mapNotNull { outcome ->
                        (outcome as? MatchLobbyTeamCropPreviewOutcome.Available)?.preview
                    },
                    unavailable = outcomes.mapNotNull { outcome ->
                        outcome as? MatchLobbyTeamCropPreviewOutcome.Unavailable
                    },
                )
                LobbyPhysicalProcessingOutcome.Resolved(
                    storedPosition = position,
                    semanticPosition = semanticPosition,
                    slots = panelMapping.slots,
                    teamCropPreviews = previews,
                )
            }
        }

        releasePanel(panel, position)?.let { failure ->
            if (failure is CancellationException) {
                throw failure
            }
            return unavailableOutcome(position, MatchLobbySlotNumberOcrUnavailableReason.PANEL_RELEASE_FAILED)
        }
        return resolved
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

    private fun unavailableForAll(
        reason: MatchLobbySlotNumberOcrUnavailableReason,
    ): MatchLobbySlotNumberOcrResult = MatchLobbySlotNumberOcrResult(
        RosterScreenshotPosition.entries.map { position ->
            unavailable(position, reason)
        },
    )

    private fun unavailable(
        position: RosterScreenshotPosition,
        reason: MatchLobbySlotNumberOcrUnavailableReason,
    ): MatchLobbySlotNumberOcrScreenshotResult.Unavailable {
        return MatchLobbySlotNumberOcrScreenshotResult.Unavailable(position, reason)
    }

    private fun unavailableOutcome(
        position: RosterScreenshotPosition,
        reason: MatchLobbySlotNumberOcrUnavailableReason,
    ): LobbyPhysicalProcessingOutcome.Unavailable =
        LobbyPhysicalProcessingOutcome.Unavailable(position, reason)
}

internal fun createMatchLobbyTeamCropPreviewOutcomes(
    panelImage: OcrPreprocessingImage,
    semanticPosition: RosterScreenshotPosition,
    teams: List<LobbyPanelPpMappedTeam>,
    factory: MatchLobbyTeamCropPreviewFactory,
): List<MatchLobbyTeamCropPreviewOutcome> = teams.map { mappedTeam ->
    val unavailableReason = mappedTeam.unavailableReason
    if (unavailableReason != null) {
        MatchLobbyTeamCropPreviewOutcome.Unavailable(
            visibleSlotPosition = mappedTeam.crop.visibleSlotPosition,
            reason = unavailableReason,
        )
    } else {
        try {
            MatchLobbyTeamCropPreviewOutcome.Available(
                MatchLobbyTeamCropPreview(
                    visibleSlotPosition = mappedTeam.crop.visibleSlotPosition,
                    detectedSlotNumber = mappedTeam.crop.detectedSlotNumber,
                    image = factory.create(panelImage, mappedTeam.crop),
                    playerRowPreviews = mappedTeam.rowPreviews,
                    authoritativeTeamSlotNumber = semanticPosition.tournamentSlotFor(
                        mappedTeam.crop.visibleSlotPosition,
                    ),
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            MatchLobbyTeamCropPreviewOutcome.Unavailable(
                visibleSlotPosition = mappedTeam.crop.visibleSlotPosition,
                reason = MatchLobbyTeamCropPreviewUnavailableReason.BITMAP_CREATION_FAILED,
            )
        }
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
