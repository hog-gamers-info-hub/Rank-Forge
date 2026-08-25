package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import kotlin.math.abs
import kotlin.math.max

data class LobbyPpPlayerTextRegion(
    val index: Int,
    val bounds: RawOcrBoundingBox?,
    val text: String,
    val confidence: Float?,
)

data class LobbyPpPlayerCandidateSelection(
    val candidateText: String?,
    val selectedRegionIndices: List<Int>,
    val status: LobbyPpPlayerCandidateSelectionStatus,
)

enum class LobbyPpPlayerCandidateSelectionStatus {
    EMPTY,
    SINGLE_REGION,
    MULTI_REGION_SELECTED,
    MULTI_REGION_MERGED,
}

object LobbyPpPlayerCandidateSelector {
    const val MULTI_REGION_CONFIDENCE_THRESHOLD = 0.5f

    fun select(regions: List<LobbyPpPlayerTextRegion>): LobbyPpPlayerCandidateSelection {
        val nonEmpty = regions.filter { it.text.isNotBlank() }
        if (nonEmpty.isEmpty()) {
            return LobbyPpPlayerCandidateSelection(
                candidateText = null,
                selectedRegionIndices = emptyList(),
                status = LobbyPpPlayerCandidateSelectionStatus.EMPTY,
            )
        }
        if (nonEmpty.size == 1) {
            return selection(nonEmpty, LobbyPpPlayerCandidateSelectionStatus.SINGLE_REGION)
        }

        val hasConfidentRegion = nonEmpty.any { region ->
            region.confidence == null || region.confidence >= MULTI_REGION_CONFIDENCE_THRESHOLD
        }
        val competingRegions = if (hasConfidentRegion) {
            nonEmpty.filter { region ->
                region.confidence == null || region.confidence >= MULTI_REGION_CONFIDENCE_THRESHOLD
            }
        } else {
            nonEmpty
        }

        val groups = adjacentGroups(competingRegions)
        val selectedGroup = groups.maxWithOrNull(
            compareBy<List<LobbyPpPlayerTextRegion>> { group -> group.maxOf { it.confidence ?: 0f } }
                .thenBy { group -> groupWidth(group) }
                .thenBy { group -> group.sumOf { it.text.trim().length } }
                .thenByDescending { group -> group.minOf { it.index } },
        ).orEmpty()
        return selection(
            selectedGroup,
            if (selectedGroup.size > 1) {
                LobbyPpPlayerCandidateSelectionStatus.MULTI_REGION_MERGED
            } else {
                LobbyPpPlayerCandidateSelectionStatus.MULTI_REGION_SELECTED
            },
        )
    }

    private fun selection(
        regions: List<LobbyPpPlayerTextRegion>,
        status: LobbyPpPlayerCandidateSelectionStatus,
    ): LobbyPpPlayerCandidateSelection {
        val ordered = regions.sortedWith(
            compareBy<LobbyPpPlayerTextRegion> { it.bounds?.left ?: Int.MAX_VALUE }
                .thenBy { it.index },
        )
        return LobbyPpPlayerCandidateSelection(
            candidateText = ordered.joinToString(" ") { it.text.trim() }.takeIf { it.isNotBlank() },
            selectedRegionIndices = ordered.map { it.index },
            status = status,
        )
    }

    private fun adjacentGroups(
        regions: List<LobbyPpPlayerTextRegion>,
    ): List<List<LobbyPpPlayerTextRegion>> {
        val ordered = regions.sortedWith(
            compareBy<LobbyPpPlayerTextRegion> { it.bounds?.left ?: Int.MAX_VALUE }
                .thenBy { it.index },
        )
        if (ordered.size <= 1) return listOf(ordered)

        val groups = mutableListOf<MutableList<LobbyPpPlayerTextRegion>>()
        ordered.forEach { region ->
            val previous = groups.lastOrNull()?.lastOrNull()
            if (previous != null && areAdjacentFragments(previous, region)) {
                groups.last().add(region)
            } else {
                groups += mutableListOf(region)
            }
        }
        return groups
    }

    private fun areAdjacentFragments(
        first: LobbyPpPlayerTextRegion,
        second: LobbyPpPlayerTextRegion,
    ): Boolean {
        val firstBounds = first.bounds ?: return false
        val secondBounds = second.bounds ?: return false
        val firstHeight = (firstBounds.bottom - firstBounds.top).coerceAtLeast(1)
        val secondHeight = (secondBounds.bottom - secondBounds.top).coerceAtLeast(1)
        val referenceHeight = minOf(firstHeight, secondHeight)
        val firstCenterY = (firstBounds.top + firstBounds.bottom) / 2.0
        val secondCenterY = (secondBounds.top + secondBounds.bottom) / 2.0
        val gap = secondBounds.left - firstBounds.right
        return abs(firstCenterY - secondCenterY) <= referenceHeight * 0.5 &&
            gap >= -referenceHeight * 0.25 &&
            gap <= max(4.0, referenceHeight * 0.75)
    }

    private fun groupWidth(group: List<LobbyPpPlayerTextRegion>): Int {
        val withBounds = group.mapNotNull { it.bounds }
        if (withBounds.isEmpty()) return 0
        return withBounds.maxOf { it.right } - withBounds.minOf { it.left }
    }
}
