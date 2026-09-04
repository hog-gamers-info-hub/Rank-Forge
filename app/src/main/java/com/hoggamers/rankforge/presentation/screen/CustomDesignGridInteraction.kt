package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.geometry.Offset
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import kotlin.math.abs

data class CustomDesignGridHitCandidates(
    val columnField: CustomDesignAnchorField? = null,
    val rowRank: Int? = null,
)

sealed interface CustomDesignGridSelection {
    data class Column(val field: CustomDesignAnchorField) : CustomDesignGridSelection
    data class Row(val rank: Int) : CustomDesignGridSelection
}

fun findCustomDesignGridHitCandidates(
    pointer: Offset,
    geometry: CustomDesignEffectiveGridGeometry,
    transform: SourceToPreviewTransform,
    hitTolerancePx: Float,
): CustomDesignGridHitCandidates {
    if (!transform.containsPreviewPoint(pointer.x, pointer.y) ||
        !hitTolerancePx.isFinite() ||
        hitTolerancePx < 0f
    ) {
        return CustomDesignGridHitCandidates()
    }

    val nearestColumn = geometry.columnX.entries
        .map { (field, sourceX) -> field to abs(transform.mapX(sourceX) - pointer.x) }
        .filter { (_, distance) -> distance <= hitTolerancePx }
        .minByOrNull { (_, distance) -> distance }
        ?.first
    val nearestRow = geometry.rowY.entries
        .map { (rank, sourceY) -> rank to abs(transform.mapY(sourceY) - pointer.y) }
        .filter { (_, distance) -> distance <= hitTolerancePx }
        .minByOrNull { (_, distance) -> distance }
        ?.first

    return CustomDesignGridHitCandidates(
        columnField = nearestColumn,
        rowRank = nearestRow,
    )
}

fun chooseCustomDesignGridSelection(
    candidates: CustomDesignGridHitCandidates,
    dragDelta: Offset,
): CustomDesignGridSelection? = when {
    candidates.columnField != null && candidates.rowRank != null -> {
        if (abs(dragDelta.x) >= abs(dragDelta.y)) {
            CustomDesignGridSelection.Column(candidates.columnField)
        } else {
            CustomDesignGridSelection.Row(candidates.rowRank)
        }
    }
    candidates.columnField != null -> CustomDesignGridSelection.Column(candidates.columnField)
    candidates.rowRank != null -> CustomDesignGridSelection.Row(candidates.rowRank)
    else -> null
}

fun customDesignColumnSourceX(
    previewX: Float,
    transform: SourceToPreviewTransform,
    sourceWidth: Int,
): Float? = previewX
    .takeIf { it.isFinite() && sourceWidth > 0 }
    ?.let { transform.unmapX(it).coerceIn(0f, sourceWidth.toFloat()) }

fun customDesignRowSourceY(
    previewY: Float,
    transform: SourceToPreviewTransform,
    sourceHeight: Int,
): Float? = previewY
    .takeIf { it.isFinite() && sourceHeight > 0 }
    ?.let { transform.unmapY(it).coerceIn(0f, sourceHeight.toFloat()) }

fun constrainCustomDesignRowSourceY(
    rank: Int,
    sourceY: Float,
    geometry: CustomDesignEffectiveGridGeometry,
    minimumSeparation: Float = CUSTOM_DESIGN_MIN_ROW_SEPARATION_SOURCE_PX,
): Float? {
    if (rank !in CUSTOM_DESIGN_RANK_RANGE ||
        !sourceY.isFinite() ||
        !minimumSeparation.isFinite() ||
        minimumSeparation < 0f
    ) {
        return null
    }

    val previousY = geometry.rowY
        .filterKeys { it < rank }
        .maxByOrNull { it.key }
        ?.value
    val nextY = geometry.rowY
        .filterKeys { it > rank }
        .minByOrNull { it.key }
        ?.value
    val minimum = maxOf(0f, (previousY ?: -minimumSeparation) + minimumSeparation)
    val maximum = minOf(
        geometry.sourceHeight.toFloat(),
        (nextY ?: (geometry.sourceHeight.toFloat() + minimumSeparation)) - minimumSeparation,
    )
    if (minimum > maximum) return null
    return sourceY.coerceIn(minimum, maximum)
}

const val CUSTOM_DESIGN_GRID_HIT_TOLERANCE_DP = 8
const val CUSTOM_DESIGN_MIN_ROW_SEPARATION_SOURCE_PX = 1f
val CUSTOM_DESIGN_RANK_RANGE = 1..12
