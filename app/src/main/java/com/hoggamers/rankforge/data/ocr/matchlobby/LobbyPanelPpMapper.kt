package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyObservedSlotAnchor
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerOcrFragment
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowBands
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropGeometryCalculator
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowEvidence
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowMapper
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorSource
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotGridReconstructor
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyGridReconstructionResult
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotGridRole
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCrop
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropBounds
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropGeometryCalculator
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropGeometryResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseFailure
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotNumberCandidate
import kotlin.math.abs

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
    val unavailableReason: MatchLobbyTeamCropPreviewUnavailableReason? = null,
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
    private const val MIN_FRAGMENT_CROP_OVERLAP = 0.50

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
                return@map LobbyPanelPpMappedTeam(
                    crop = crop,
                    rowPreviews = emptyList(),
                    unavailableReason = MatchLobbyTeamCropPreviewUnavailableReason.INVALID_CROP_BOUNDS,
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
                if (!fragment.boundingBox.hasMajorityOverlapWith(crop.bounds)) {
                    return@mapNotNull null
                }
                LocalPanelFragment(
                    fragment = fragment,
                    localBoundingBox = fragment.boundingBox.toLocal(cropLeft, cropTop),
                    isSlotNumberEvidence = fragment.readingOrderIndex in selectedIndices,
                )
            }
            val mapperFragments = localPanelFragments.map { local ->
                LobbyPlayerOcrFragment(
                    rawText = local.fragment.text,
                    boundingBox = local.localBoundingBox,
                    isSlotNumberEvidence = local.isSlotNumberEvidence,
                )
            }
            val initialRowMapping = LobbyPlayerRowMapper.map(
                rowBands = rowGeometry.bands,
                fragments = mapperFragments,
                selectedSlotBoundingBox = selectedSlotBox,
                slotGutterRight = rowGeometry.playerAreaLeft,
            )
            val isBottomLeft =
                LobbySlotGridRole.fromSlotNumber(crop.detectedSlotNumber) == LobbySlotGridRole.BOTTOM_LEFT
            val rowMapping = if (isBottomLeft) {
                LobbyPlayerRowMapper.map(
                    rowBands = rowGeometry.bands,
                    fragments = filterBottomLeftRow4Fragments(
                        localPanelFragments = localPanelFragments,
                        initialRowMapping = initialRowMapping,
                        rowBands = rowGeometry.bands,
                        selectedSlotBoundingBox = selectedSlotBox,
                        slotGutterRight = rowGeometry.playerAreaLeft,
                    ).map { local ->
                        LobbyPlayerOcrFragment(
                            rawText = local.fragment.text,
                            boundingBox = local.localBoundingBox,
                            isSlotNumberEvidence = local.isSlotNumberEvidence,
                        )
                    },
                    selectedSlotBoundingBox = selectedSlotBox,
                    slotGutterRight = rowGeometry.playerAreaLeft,
                )
            } else {
                initialRowMapping
            }
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

    private fun filterBottomLeftRow4Fragments(
        localPanelFragments: List<LocalPanelFragment>,
        initialRowMapping: com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowMapping,
        rowBands: LobbyPlayerRowBands,
        selectedSlotBoundingBox: RawOcrBoundingBox?,
        slotGutterRight: Int,
    ): List<LocalPanelFragment> {
        val rowCenters = LobbyPlayerRow.entries
            .take(3)
            .mapNotNull { row ->
                initialRowMapping.row(row).reliableCenterY()?.let { centerY ->
                    row.ordinal to centerY
                }
            }
        if (rowCenters.size < 2) return localPanelFragments

        val pitchCandidates = rowCenters.zipWithNext().mapNotNull { (first, second) ->
            val rowDistance = (second.first - first.first).toDouble()
            ((second.second - first.second) / rowDistance)
                .takeIf { it.isFinite() && it > 0.0 }
        }
        val rowPitch = pitchCandidates.medianOrNull() ?: return localPanelFragments
        val expectedRow4CenterY = rowCenters
            .map { (rowOrdinal, centerY) ->
                centerY + rowPitch * (LobbyPlayerRow.ROW_4.ordinal - rowOrdinal)
            }
            .medianOrNull()
            ?.takeIf { it.isFinite() }
            ?: return localPanelFragments
        val row4Candidates = localPanelFragments.filter { local ->
            val box = local.localBoundingBox
            !local.isSlotNumberEvidence &&
                box.isPositive() &&
                selectedSlotBoundingBox?.overlaps(box) != true &&
                box.right > slotGutterRight &&
                rowBands.bandFor(box.centerY)?.row == LobbyPlayerRow.ROW_4
        }
        if (row4Candidates.isEmpty()) return localPanelFragments

        val sameLineTolerance = rowPitch * 0.10
        val seed = row4Candidates.minBy { local ->
            abs(local.localBoundingBox.centerY - expectedRow4CenterY)
        }
        val keptReadingOrderIndices = row4Candidates
            .filter { local ->
                abs(local.localBoundingBox.centerY - seed.localBoundingBox.centerY) <= sameLineTolerance
            }
            .map { it.fragment.readingOrderIndex }
            .toSet()
        val row4CandidateIndices = row4Candidates
            .map { it.fragment.readingOrderIndex }
            .toSet()
        return localPanelFragments.filter { local ->
            local.fragment.readingOrderIndex !in row4CandidateIndices ||
                local.fragment.readingOrderIndex in keptReadingOrderIndices
        }
    }

    private fun LobbyPlayerRowEvidence.reliableCenterY(): Double? = fragments
        .mapNotNull { fragment ->
            fragment.boundingBox
                ?.takeIf { it.right > it.left && it.bottom > it.top }
                ?.let { boundingBox -> (boundingBox.top + boundingBox.bottom) / 2.0 }
        }
        .medianOrNull()

    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    private fun RawOcrBoundingBox.isPositive(): Boolean = right > left && bottom > top

    private fun RawOcrBoundingBox.overlaps(other: RawOcrBoundingBox): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

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

    private fun RawOcrBoundingBox.hasMajorityOverlapWith(crop: LobbyTeamCropBounds): Boolean {
        val fragmentLeft = left.toDouble()
        val fragmentTop = top.toDouble()
        val fragmentRight = right.toDouble()
        val fragmentBottom = bottom.toDouble()
        val fragmentWidth = fragmentRight - fragmentLeft
        val fragmentHeight = fragmentBottom - fragmentTop
        val cropWidth = crop.right - crop.left
        val cropHeight = crop.bottom - crop.top
        if (
            listOf(
                fragmentLeft,
                fragmentTop,
                fragmentRight,
                fragmentBottom,
                fragmentWidth,
                fragmentHeight,
                crop.left,
                crop.top,
                crop.right,
                crop.bottom,
                cropWidth,
                cropHeight,
            ).any { !it.isFinite() } ||
            fragmentWidth <= 0.0 ||
            fragmentHeight <= 0.0 ||
            cropWidth <= 0.0 ||
            cropHeight <= 0.0
        ) {
            return false
        }

        val intersectionWidth = (minOf(fragmentRight, crop.right) - maxOf(fragmentLeft, crop.left))
            .takeIf { it > 0.0 }
            ?: 0.0
        val intersectionHeight = (minOf(fragmentBottom, crop.bottom) - maxOf(fragmentTop, crop.top))
            .takeIf { it > 0.0 }
            ?: 0.0
        val intersectionArea = intersectionWidth * intersectionHeight
        val fragmentArea = fragmentWidth * fragmentHeight
        if (!intersectionArea.isFinite() || !fragmentArea.isFinite() || fragmentArea <= 0.0) {
            return false
        }
        return intersectionArea / fragmentArea >= MIN_FRAGMENT_CROP_OVERLAP
    }

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
