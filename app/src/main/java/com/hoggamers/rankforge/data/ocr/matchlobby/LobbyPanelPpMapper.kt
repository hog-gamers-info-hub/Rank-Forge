package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyObservedSlotAnchor
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerOcrFragment
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropGeometryCalculator
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowMapper
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorSource
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotGridReconstructor
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyGridReconstructionResult
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotGridRole
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCrop
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropGeometryCalculator
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropGeometryResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseFailure
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotNumberCandidate

/** One result emitted by the single whole-panel PP-OCR request. */
data class LobbyPanelPpFragment(
    val text: String,
    val confidence: Float,
    val boundingBox: RawOcrBoundingBox,
    val readingOrderIndex: Int,
)

data class LobbyPanelPpMappedTeam(
    val crop: LobbyTeamCrop,
    val rowPreviews: List<LobbyPlayerRowCropPreview>,
)

sealed interface LobbyPanelPpMappingResult {
    data class Available(
        val slots: List<MatchLobbySlotNumberOcrSlot>,
        val teams: List<LobbyPanelPpMappedTeam>,
        val observedAnchorCount: Int,
        val fragmentCount: Int,
    ) : LobbyPanelPpMappingResult {
        init {
            require(slots.map { it.visibleSlotPosition } == RosterVisibleSlotPosition.entries)
            require(teams.map { it.crop.visibleSlotPosition } == RosterVisibleSlotPosition.entries)
        }
    }

    data class Unavailable(
        val reason: MatchLobbyTeamCropPreviewUnavailableReason,
        val observedAnchorCount: Int = 0,
        val fragmentCount: Int = 0,
    ) : LobbyPanelPpMappingResult
}

enum class LobbyPanelSemanticMappingFailure {
    SEMANTIC_POSITION_UNRESOLVED,
    SEMANTIC_POSITION_CONFLICT,
}

sealed interface LobbyPanelSemanticMappingResult {
    data class Available(
        val screenshotPosition: RosterScreenshotPosition,
        val mapping: LobbyPanelPpMappingResult.Available,
    ) : LobbyPanelSemanticMappingResult

    data class Unavailable(
        val reason: MatchLobbyTeamCropPreviewUnavailableReason,
        val failure: LobbyPanelSemanticMappingFailure,
        val fragmentCount: Int = 0,
    ) : LobbyPanelSemanticMappingResult
}

/**
 * Deterministically maps PP whole-panel evidence into the existing Lobby slot,
 * team-crop, and player-row contracts. It performs no OCR and never invents a
 * slot anchor from a player-name fragment.
 */
object LobbyPanelPpMapper {
    private const val SLOT_GUTTER_FRACTION = 0.15

    /**
     * Resolves semantic screenshot position from structural slot-number evidence.
     * The caller's storage identity is intentionally not an input.
     */
    fun map(
        panelWidth: Int,
        panelHeight: Int,
        fragments: List<LobbyPanelPpFragment>,
    ): LobbyPanelSemanticMappingResult {
        if (panelWidth <= 0 || panelHeight <= 0) {
            return LobbyPanelSemanticMappingResult.Unavailable(
                reason = MatchLobbyTeamCropPreviewUnavailableReason.INVALID_TEAM_GRID_GEOMETRY,
                failure = LobbyPanelSemanticMappingFailure.SEMANTIC_POSITION_UNRESOLVED,
                fragmentCount = fragments.size,
            )
        }

        val attempts = RosterScreenshotPosition.entries.map { position ->
            position to mapForPosition(panelWidth, panelHeight, position, fragments)
        }
        val validCandidates = attempts.mapNotNull { (position, result) ->
            (result as? LobbyPanelPpMappingResult.Available)?.let { mapping ->
                position to mapping
            }
        }

        return when (validCandidates.size) {
            1 -> {
                val (position, mapping) = validCandidates.single()
                LobbyPanelSemanticMappingResult.Available(position, mapping)
            }
            0 -> LobbyPanelSemanticMappingResult.Unavailable(
                reason = attempts.mapNotNull { (_, result) ->
                    (result as? LobbyPanelPpMappingResult.Unavailable)?.reason
                }.firstOrNull() ?: MatchLobbyTeamCropPreviewUnavailableReason.INVALID_TEAM_GRID_GEOMETRY,
                failure = LobbyPanelSemanticMappingFailure.SEMANTIC_POSITION_UNRESOLVED,
                fragmentCount = fragments.size,
            )
            else -> LobbyPanelSemanticMappingResult.Unavailable(
                reason = MatchLobbyTeamCropPreviewUnavailableReason.INVALID_TEAM_GRID_GEOMETRY,
                failure = LobbyPanelSemanticMappingFailure.SEMANTIC_POSITION_CONFLICT,
                fragmentCount = fragments.size,
            )
        }
    }

    private fun mapForPosition(
        panelWidth: Int,
        panelHeight: Int,
        position: RosterScreenshotPosition,
        fragments: List<LobbyPanelPpFragment>,
    ): LobbyPanelPpMappingResult {
        if (panelWidth <= 0 || panelHeight <= 0) {
            return unavailable(MatchLobbyTeamCropPreviewUnavailableReason.INVALID_TEAM_GRID_GEOMETRY, fragments)
        }
        val selectedSlotEvidence = selectSlotEvidence(
            panelWidth = panelWidth,
            position = position,
            fragments = fragments,
        )
        val observedAnchors = selectedSlotEvidence.values.map { evidence ->
            LobbyObservedSlotAnchor(
                slotNumber = evidence.slotNumber,
                centerX = evidence.fragment.boundingBox.centerX,
                centerY = evidence.fragment.boundingBox.centerY,
            )
        }
        if (observedAnchors.size < 2) {
            return unavailable(
                MatchLobbyTeamCropPreviewUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE,
                fragments,
                observedAnchors.size,
            )
        }

        val grid = when (val reconstruction = LobbySlotGridReconstructor().reconstruct(position.index, observedAnchors)) {
            is LobbyGridReconstructionResult.Reconstructed -> reconstruction.grid
            else -> return unavailable(
                MatchLobbyTeamCropPreviewUnavailableReason.INVALID_TEAM_GRID_GEOMETRY,
                fragments,
                observedAnchors.size,
            )
        }
        val observedSlotLeftInsets = observedAnchors.mapNotNull { anchor ->
            when (LobbySlotGridRole.fromSlotNumber(anchor.slotNumber)) {
                LobbySlotGridRole.TOP_LEFT,
                LobbySlotGridRole.BOTTOM_LEFT,
                -> anchor.centerX
                LobbySlotGridRole.TOP_RIGHT,
                LobbySlotGridRole.BOTTOM_RIGHT,
                -> anchor.centerX - grid.columnPitch
                null -> null
            }
        }
        val geometry = when (
            val calculated = LobbyTeamCropGeometryCalculator.calculate(
                panelWidth = panelWidth,
                panelHeight = panelHeight,
                grid = grid,
                observedSlotLeftInsets = observedSlotLeftInsets,
            )
        ) {
            is LobbyTeamCropGeometryResult.Available -> calculated.crops
            is LobbyTeamCropGeometryResult.Unavailable -> return unavailable(
                calculated.toPreviewUnavailableReason(),
                fragments,
                observedAnchors.size,
            )
        }

        val selectedIndices = selectedSlotEvidence.values.map { it.fragment.readingOrderIndex }.toSet()
        val teams = geometry.map { crop ->
            val cropLeft = crop.bounds.left
            val cropTop = crop.bounds.top
            val cropWidth = (crop.bounds.right - cropLeft).toInt()
            val cropHeight = (crop.bounds.bottom - cropTop).toInt()
            val selectedSlot = selectedSlotEvidence[crop.detectedSlotNumber]
            val selectedSlotBox = selectedSlot?.fragment?.boundingBox?.toLocal(cropLeft, cropTop)
            val slotAnchorY = selectedSlotBox?.centerY
                ?: (grid.pointFor(LobbySlotGridRole.fromSlotNumber(crop.detectedSlotNumber)!!).centerY - cropTop)
            val rowGeometry = LobbyPlayerRowCropGeometryCalculator.calculate(
                teamCropWidth = cropWidth,
                teamCropHeight = cropHeight,
                slotAnchorY = slotAnchorY,
            )
            if (rowGeometry == null) {
                return unavailable(
                    MatchLobbyTeamCropPreviewUnavailableReason.INVALID_CROP_BOUNDS,
                    fragments,
                    observedAnchors.size,
                )
            }
            val localPanelFragments = fragments.mapNotNull { fragment ->
                val centerX = fragment.boundingBox.centerX
                val centerY = fragment.boundingBox.centerY
                if (centerX !in crop.bounds.left..crop.bounds.right ||
                    centerY !in crop.bounds.top..crop.bounds.bottom
                ) {
                    return@mapNotNull null
                }
                LocalPanelFragment(
                    fragment = fragment,
                    localBoundingBox = fragment.boundingBox.toLocal(cropLeft, cropTop),
                    isSlotNumberEvidence = fragment.readingOrderIndex in selectedIndices,
                )
            }
            val rowMapping = LobbyPlayerRowMapper.map(
                rowBands = rowGeometry.bands,
                fragments = localPanelFragments.map { local ->
                    LobbyPlayerOcrFragment(
                        rawText = local.fragment.text,
                        boundingBox = local.localBoundingBox,
                        isSlotNumberEvidence = local.isSlotNumberEvidence,
                    )
                },
                selectedSlotBoundingBox = selectedSlotBox,
                slotGutterRight = rowGeometry.playerAreaLeft,
            )
            val anchorSource = if (selectedSlot != null) {
                LobbySlotAnchorSource.PP_OCR_SLOT
            } else {
                LobbySlotAnchorSource.TEAM_CROP_CENTER_FALLBACK
            }
            LobbyPanelPpMappedTeam(
                crop = crop,
                rowPreviews = LobbyPlayerRow.entries.map { row ->
                    val evidence = rowMapping.row(row)
                    val text = evidence.structuralText
                    val confidence = evidence.fragments.mapNotNull { mapped ->
                        localPanelFragments.firstOrNull { local ->
                            local.fragment.text == mapped.rawText &&
                                local.localBoundingBox == mapped.boundingBox &&
                                !local.isSlotNumberEvidence
                        }?.fragment?.confidence
                    }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
                    LobbyPlayerRowCropPreview(
                        row = row,
                        boundsInTeamCrop = rowGeometry.boundsFor(row),
                        slotAnchorSource = anchorSource,
                        slotAnchorY = rowGeometry.bands.slotAnchorY,
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

        val slots = RosterVisibleSlotPosition.entries.map { visiblePosition ->
            val slotNumber = position.tournamentSlotFor(visiblePosition)
            val evidence = selectedSlotEvidence[slotNumber]
            MatchLobbySlotNumberOcrSlot(
                visibleSlotPosition = visiblePosition,
                candidate = evidence?.toCandidate() ?: RosterSlotNumberCandidate.unavailable(),
            )
        }
        return LobbyPanelPpMappingResult.Available(
            slots = slots,
            teams = teams,
            observedAnchorCount = observedAnchors.size,
            fragmentCount = fragments.size,
        )
    }

    private fun selectSlotEvidence(
        panelWidth: Int,
        position: RosterScreenshotPosition,
        fragments: List<LobbyPanelPpFragment>,
    ): Map<Int, SlotEvidence> {
        val expectedRange = position.tournamentSlotRange
        return expectedRange.associateWithNotNull { slotNumber ->
            val role = LobbySlotGridRole.fromSlotNumber(slotNumber) ?: return@associateWithNotNull null
            fragments.asSequence()
                .mapNotNull { fragment ->
                    val box = fragment.boundingBox
                    if (fragment.text.trim() != slotNumber.toString() || !box.isUsable()) return@mapNotNull null
                    val centerX = box.centerX
                    val halfWidth = panelWidth / 2.0
                    val inExpectedColumn = when (role) {
                        LobbySlotGridRole.TOP_LEFT,
                        LobbySlotGridRole.BOTTOM_LEFT,
                        -> centerX < halfWidth
                        LobbySlotGridRole.TOP_RIGHT,
                        LobbySlotGridRole.BOTTOM_RIGHT,
                        -> centerX >= halfWidth
                    }
                    if (!inExpectedColumn) return@mapNotNull null
                    val localX = when (role) {
                        LobbySlotGridRole.TOP_LEFT,
                        LobbySlotGridRole.BOTTOM_LEFT,
                        -> centerX
                        LobbySlotGridRole.TOP_RIGHT,
                        LobbySlotGridRole.BOTTOM_RIGHT,
                        -> centerX - halfWidth
                    }
                    if (localX !in 0.0..(halfWidth * SLOT_GUTTER_FRACTION)) return@mapNotNull null
                    SlotEvidence(slotNumber, fragment)
                }
                .sortedWith(compareByDescending<SlotEvidence> { it.fragment.confidence }.thenBy { it.fragment.readingOrderIndex })
                .firstOrNull()
        }
    }

    private fun SlotEvidence.toCandidate(): RosterSlotNumberCandidate = RosterSlotNumberCandidate(
        status = RosterCandidateParseStatus.PARSED,
        detectedSlotNumber = slotNumber,
        failure = null,
        rawSourceResults = emptyList(),
        confidence = RawOcrConfidence.Available(fragment.confidence.coerceIn(0f, 1f)),
    )

    private fun unavailable(
        reason: MatchLobbyTeamCropPreviewUnavailableReason,
        fragments: List<LobbyPanelPpFragment>,
        observedAnchorCount: Int = 0,
    ) = LobbyPanelPpMappingResult.Unavailable(reason, observedAnchorCount, fragments.size)

    private data class SlotEvidence(
        val slotNumber: Int,
        val fragment: LobbyPanelPpFragment,
    )

    private data class LocalPanelFragment(
        val fragment: LobbyPanelPpFragment,
        val localBoundingBox: RawOcrBoundingBox,
        val isSlotNumberEvidence: Boolean,
    )

    private fun RawOcrBoundingBox.isUsable(): Boolean =
        left >= 0 && top >= 0 && right > left && bottom > top

    private val RawOcrBoundingBox.centerX: Double
        get() = (left + right) / 2.0

    private val RawOcrBoundingBox.centerY: Double
        get() = (top + bottom) / 2.0

    private fun RawOcrBoundingBox.toLocal(left: Double, top: Double): RawOcrBoundingBox =
        RawOcrBoundingBox(
            left = (this.left - left).toInt(),
            top = (this.top - top).toInt(),
            right = (this.right - left).toInt(),
            bottom = (this.bottom - top).toInt(),
        )

    private fun LobbyTeamCropGeometryResult.Unavailable.toPreviewUnavailableReason(): MatchLobbyTeamCropPreviewUnavailableReason =
        when (reason) {
            com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE ->
                MatchLobbyTeamCropPreviewUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE
            com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropUnavailableReason.SLOT_NUMBER_GEOMETRY_UNAVAILABLE ->
                MatchLobbyTeamCropPreviewUnavailableReason.SLOT_NUMBER_GEOMETRY_UNAVAILABLE
            com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropUnavailableReason.INVALID_TEAM_GRID_GEOMETRY ->
                MatchLobbyTeamCropPreviewUnavailableReason.INVALID_TEAM_GRID_GEOMETRY
            com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropUnavailableReason.INVALID_CROP_BOUNDS ->
                MatchLobbyTeamCropPreviewUnavailableReason.INVALID_CROP_BOUNDS
        }

    private inline fun <K, V> Iterable<K>.associateWithNotNull(
        valueSelector: (K) -> V?,
    ): Map<K, V> = buildMap {
        for (key in this@associateWithNotNull) {
            valueSelector(key)?.let { value -> put(key, value) }
        }
    }
}
