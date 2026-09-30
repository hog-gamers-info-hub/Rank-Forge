package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterSlotRegion
import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxCroppedRosterPanelLayout
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerOcrFragment
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowBands
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowBand
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropBounds
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowMapper
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorSource
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropBounds
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotNumberCandidate
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class LobbyFixedQuadrantFallbackTeam(
    val visibleSlotPosition: RosterVisibleSlotPosition,
    val bounds: LobbyTeamCropBounds,
    val detectedSlotNumber: Int?,
    val rowPreviews: List<LobbyPlayerRowCropPreview>,
)

internal sealed interface LobbyFixedQuadrantFallbackMappingResult {
    data class Available(
        val teams: List<LobbyFixedQuadrantFallbackTeam>,
        val slots: List<MatchLobbySlotNumberOcrSlot>,
        val semanticHint: RosterScreenshotPosition?,
        val fragmentCount: Int,
    ) : LobbyFixedQuadrantFallbackMappingResult {
        init {
            require(slots.map { it.visibleSlotPosition } == RosterVisibleSlotPosition.entries)
            require(teams.map { it.visibleSlotPosition } == RosterVisibleSlotPosition.entries)
        }
    }

    data class Unavailable(
        val reason: MatchLobbyTeamCropPreviewUnavailableReason,
    ) : LobbyFixedQuadrantFallbackMappingResult
}

/**
 * Builds the fixed four-quadrant fallback from the already-recognized panel
 * fragments. This class never invokes OCR and never performs cross-screenshot
 * semantic reconciliation.
 */
internal object LobbyFixedQuadrantFallbackMapper {
    private const val MIN_FRAGMENT_CROP_OVERLAP = 0.50

    fun map(
        panelWidth: Int,
        panelHeight: Int,
        fragments: List<LobbyPanelPpFragment>,
    ): LobbyFixedQuadrantFallbackMappingResult {
        if (panelWidth <= 0 || panelHeight <= 0) {
            return LobbyFixedQuadrantFallbackMappingResult.Unavailable(
                MatchLobbyTeamCropPreviewUnavailableReason.INVALID_TEAM_GRID_GEOMETRY,
            )
        }

        val regions = FreeFireMaxCroppedRosterPanelLayout.definition.slots
        if (regions.map { it.visiblePosition } != RosterVisibleSlotPosition.entries) {
            return LobbyFixedQuadrantFallbackMappingResult.Unavailable(
                MatchLobbyTeamCropPreviewUnavailableReason.INVALID_TEAM_GRID_GEOMETRY,
            )
        }

        val anchorsByQuadrant = regions.associate { region ->
            region.visiblePosition to selectValidAnchors(
                region = region,
                panelWidth = panelWidth,
                panelHeight = panelHeight,
                fragments = fragments,
            )
        }
        val validAnchors = anchorsByQuadrant.values.flatten()
        val semanticGroups = validAnchors.map { it.semanticPosition }.distinct()
        val semanticHint = semanticGroups.singleOrNull()
        val selectedAnchors = anchorsByQuadrant.mapValues { (_, anchors) ->
            val groups = anchors.map { it.semanticPosition }.distinct()
            if (groups.size == 1) {
                anchors.sortedWith(
                    compareByDescending<SlotEvidence> { it.fragment.confidence }
                        .thenBy { it.fragment.readingOrderIndex },
                ).firstOrNull()
            } else {
                null
            }
        }
        val evidenceIndices = validAnchors.map { it.fragment.readingOrderIndex }.toSet()

        val teams = regions.map { region ->
            val cropBounds = region.contentRect.toPanelBounds(panelWidth, panelHeight)
                ?: return LobbyFixedQuadrantFallbackMappingResult.Unavailable(
                    MatchLobbyTeamCropPreviewUnavailableReason.INVALID_CROP_BOUNDS,
                )
            val rowBounds = region.playerRowRegions.map { rowRegion ->
                rowRegion.rect.toPanelBounds(panelWidth, panelHeight)?.toLocalOrNull(cropBounds)
                    ?: return LobbyFixedQuadrantFallbackMappingResult.Unavailable(
                        MatchLobbyTeamCropPreviewUnavailableReason.INVALID_CROP_BOUNDS,
                    )
            }
            val rowBands = LobbyPlayerRowBands(
                teamCropHeight = cropBounds.bottom - cropBounds.top,
                slotAnchorY = (cropBounds.bottom - cropBounds.top) / 2.0,
                bands = rowBounds.mapIndexed { index, bounds ->
                    LobbyPlayerRowBand(
                        row = LobbyPlayerRow.entries[index],
                        top = bounds.top.toDouble(),
                        bottom = bounds.bottom.toDouble(),
                    )
                },
            )
            val selectedAnchor = selectedAnchors.getValue(region.visiblePosition)
            val selectedSlotBoundingBox = selectedAnchor?.fragment?.boundingBox
                ?.toLocal(cropBounds.left, cropBounds.top)
            val localFragments = fragments.mapNotNull { fragment ->
                if (!fragment.boundingBox.hasMajorityOverlapWith(cropBounds)) return@mapNotNull null
                LocalPanelFragment(
                    fragment = fragment,
                    localBoundingBox = fragment.boundingBox.toLocal(cropBounds.left, cropBounds.top),
                    isSlotNumberEvidence = fragment.readingOrderIndex in evidenceIndices,
                )
            }
            val mappedRows = LobbyPlayerRowMapper.map(
                rowBands = rowBands,
                fragments = localFragments.map { local ->
                    LobbyPlayerOcrFragment(
                        rawText = local.fragment.text,
                        boundingBox = local.localBoundingBox,
                        isSlotNumberEvidence = local.isSlotNumberEvidence,
                    )
                },
                selectedSlotBoundingBox = selectedSlotBoundingBox,
                slotGutterRight = floor(
                    (cropBounds.right - cropBounds.left) *
                        FreeFireMaxCroppedRosterPanelLayout.PLAYER_CONTENT_START_FRACTION,
                ).toInt(),
            )
            val anchorSource = if (selectedAnchor != null) {
                LobbySlotAnchorSource.PP_OCR_SLOT
            } else {
                LobbySlotAnchorSource.TEAM_CROP_CENTER_FALLBACK
            }
            LobbyFixedQuadrantFallbackTeam(
                visibleSlotPosition = region.visiblePosition,
                bounds = cropBounds,
                detectedSlotNumber = selectedAnchor?.slotNumber,
                rowPreviews = LobbyPlayerRow.entries.map { row ->
                    val evidence = mappedRows.row(row)
                    val text = evidence.structuralText
                    val confidence = evidence.fragments.mapNotNull { mapped ->
                        localFragments.firstOrNull { local ->
                            local.fragment.text == mapped.rawText &&
                                local.localBoundingBox == mapped.boundingBox &&
                                !local.isSlotNumberEvidence
                        }?.fragment?.confidence
                    }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
                    LobbyPlayerRowCropPreview(
                        row = row,
                        boundsInTeamCrop = rowBounds[row.ordinal],
                        slotAnchorSource = anchorSource,
                        slotAnchorY = rowBands.slotAnchorY,
                        structuralEvidence = text,
                        playerName = text,
                        playerNameConfidence = confidence,
                        playerNameSource = if (text == null) {
                            LobbyPlayerTextSource.EMPTY
                        } else {
                            LobbyPlayerTextSource.PP_PANEL
                        },
                    )
                },
            )
        }

        val slots = regions.map { region ->
            val selectedAnchor = selectedAnchors.getValue(region.visiblePosition)
            MatchLobbySlotNumberOcrSlot(
                visibleSlotPosition = region.visiblePosition,
                candidate = selectedAnchor?.toCandidate() ?: RosterSlotNumberCandidate.unavailable(),
            )
        }
        return LobbyFixedQuadrantFallbackMappingResult.Available(
            teams = teams,
            slots = slots,
            semanticHint = semanticHint,
            fragmentCount = fragments.size,
        )
    }

    private fun selectValidAnchors(
        region: CroppedRosterSlotRegion,
        panelWidth: Int,
        panelHeight: Int,
        fragments: List<LobbyPanelPpFragment>,
    ): List<SlotEvidence> {
        val bounds = region.contentRect.toPanelBounds(panelWidth, panelHeight) ?: return emptyList()
        val gutterRight = bounds.left + floor(
            (bounds.right - bounds.left) * FreeFireMaxCroppedRosterPanelLayout.PLAYER_CONTENT_START_FRACTION,
        )
        return fragments.mapNotNull { fragment ->
            val trimmedText = fragment.text.trim()
            val slotNumber = (1..12).singleOrNull { it.toString() == trimmedText }
                ?: return@mapNotNull null
            val box = fragment.boundingBox
            if (!box.isUsable() || !box.centerBelongsTo(bounds, panelWidth, panelHeight)) {
                return@mapNotNull null
            }
            if (box.centerX > gutterRight) return@mapNotNull null
            val semanticPosition = RosterScreenshotPosition.entries.singleOrNull { position ->
                position.tournamentSlotFor(region.visiblePosition) == slotNumber
            } ?: return@mapNotNull null
            SlotEvidence(slotNumber, semanticPosition, fragment)
        }
    }

    private fun SlotEvidence.toCandidate(): RosterSlotNumberCandidate = RosterSlotNumberCandidate(
        status = RosterCandidateParseStatus.PARSED,
        detectedSlotNumber = slotNumber,
        failure = null,
        rawSourceResults = emptyList(),
        confidence = RawOcrConfidence.Available(fragment.confidence.coerceIn(0f, 1f)),
    )

    private data class SlotEvidence(
        val slotNumber: Int,
        val semanticPosition: RosterScreenshotPosition,
        val fragment: LobbyPanelPpFragment,
    )

    private data class LocalPanelFragment(
        val fragment: LobbyPanelPpFragment,
        val localBoundingBox: RawOcrBoundingBox,
        val isSlotNumberEvidence: Boolean,
    )

    private fun com.hoggamers.rankforge.domain.ocr.layout.NormalizedOcrRect.toPanelBounds(
        panelWidth: Int,
        panelHeight: Int,
    ): LobbyTeamCropBounds? {
        val left = (x * panelWidth).roundToInt().coerceIn(0, panelWidth)
        val top = (y * panelHeight).roundToInt().coerceIn(0, panelHeight)
        val right = ((x + width) * panelWidth).roundToInt().coerceIn(0, panelWidth)
        val bottom = ((y + height) * panelHeight).roundToInt().coerceIn(0, panelHeight)
        if (right <= left || bottom <= top) return null
        return LobbyTeamCropBounds(
            left = left.toDouble(),
            top = top.toDouble(),
            right = right.toDouble(),
            bottom = bottom.toDouble(),
        )
    }

    private fun LobbyTeamCropBounds.toLocalOrNull(
        outer: LobbyTeamCropBounds,
    ): LobbyPlayerRowCropBounds? {
        val localLeft = floor(left - outer.left).toInt()
        val localTop = floor(top - outer.top).toInt()
        val localRight = floor(right - outer.left).toInt()
        val localBottom = floor(bottom - outer.top).toInt()
        if (localRight <= localLeft || localBottom <= localTop) return null
        return LobbyPlayerRowCropBounds(
            left = localLeft,
            top = localTop,
            right = localRight,
            bottom = localBottom,
        )
    }

    private fun RawOcrBoundingBox.centerBelongsTo(
        bounds: LobbyTeamCropBounds,
        panelWidth: Int,
        panelHeight: Int,
    ): Boolean {
        val centerX = centerX
        val centerY = centerY
        val inHorizontal = centerX >= bounds.left &&
            (centerX < bounds.right || centerX == panelWidth.toDouble())
        val inVertical = centerY >= bounds.top &&
            (centerY < bounds.bottom || centerY == panelHeight.toDouble())
        return inHorizontal && inVertical
    }

    private fun RawOcrBoundingBox.hasMajorityOverlapWith(crop: LobbyTeamCropBounds): Boolean {
        val fragmentWidth = right - left
        val fragmentHeight = bottom - top
        val cropWidth = crop.right - crop.left
        val cropHeight = crop.bottom - crop.top
        if (fragmentWidth <= 0 || fragmentHeight <= 0 || cropWidth <= 0.0 || cropHeight <= 0.0) {
            return false
        }
        val intersectionWidth = (minOf(right.toDouble(), crop.right) - maxOf(left.toDouble(), crop.left))
            .takeIf { it > 0.0 }
            ?: 0.0
        val intersectionHeight = (minOf(bottom.toDouble(), crop.bottom) - maxOf(top.toDouble(), crop.top))
            .takeIf { it > 0.0 }
            ?: 0.0
        return intersectionWidth * intersectionHeight / (fragmentWidth * fragmentHeight) >= MIN_FRAGMENT_CROP_OVERLAP
    }

    private fun RawOcrBoundingBox.toLocal(left: Double, top: Double): RawOcrBoundingBox =
        RawOcrBoundingBox(
            left = (this.left - left).toInt(),
            top = (this.top - top).toInt(),
            right = (this.right - left).toInt(),
            bottom = (this.bottom - top).toInt(),
        )

    private val RawOcrBoundingBox.centerX: Double
        get() = (left + right) / 2.0

    private val RawOcrBoundingBox.centerY: Double
        get() = (top + bottom) / 2.0

    private fun RawOcrBoundingBox.isUsable(): Boolean =
        left >= 0 && top >= 0 && right > left && bottom > top
}
