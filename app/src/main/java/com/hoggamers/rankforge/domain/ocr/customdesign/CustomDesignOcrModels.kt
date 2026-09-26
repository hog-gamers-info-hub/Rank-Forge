package com.hoggamers.rankforge.domain.ocr.customdesign

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox

enum class CustomDesignAnchorField {
    TEAM_NAME,
    WIN,
    MATCHES_PLAYED,
    TOTAL_KILLS,
    POSITION_POINTS,
    TOTAL_POINTS,

    ;

    companion object {
        val REQUIRED_FIELDS = listOf(
            TEAM_NAME,
            WIN,
            TOTAL_KILLS,
            POSITION_POINTS,
            TOTAL_POINTS,
        )
    }
}

data class CustomDesignOcrLabels(
    val teamName: String,
    val win: String,
    val totalKills: String,
    val positionPoints: String,
    val totalPoints: String,
    val matchesPlayed: String? = null,
)

fun CustomDesignOcrLabels.activeFields(): List<CustomDesignAnchorField> =
    activeCustomDesignFields(matchesPlayed)

fun activeCustomDesignFields(matchesPlayedLabel: String?): List<CustomDesignAnchorField> = buildList {
    add(CustomDesignAnchorField.TEAM_NAME)
    add(CustomDesignAnchorField.WIN)
    if (!matchesPlayedLabel.isNullOrBlank()) {
        add(CustomDesignAnchorField.MATCHES_PLAYED)
    }
    add(CustomDesignAnchorField.TOTAL_KILLS)
    add(CustomDesignAnchorField.POSITION_POINTS)
    add(CustomDesignAnchorField.TOTAL_POINTS)
}

data class CustomDesignOcrAnchors(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val columnX: Map<CustomDesignAnchorField, Float>,
    val rowY: Map<Int, Float>,
)

enum class CustomDesignOcrStatus {
    IDLE,
    PROCESSING,
    COMPLETED,
    FAILED,
}

data class CustomDesignOcrSource(
    val imageReference: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

data class CustomDesignRawOcrDocument(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val blocks: List<RawOcrBlock>,
)

fun interface CustomDesignOcrRunner {
    suspend fun recognize(source: CustomDesignOcrSource): CustomDesignRawOcrDocument
}

data class CustomDesignAnchorDetectionResult(
    val anchors: CustomDesignOcrAnchors,
    val headerCenterY: Map<CustomDesignAnchorField, Float>,
    val missingFields: Set<CustomDesignAnchorField>,
    val ambiguousFields: Set<CustomDesignAnchorField>,
    val ambiguousRanks: Set<Int>,
    val acceptedRankingBoundingBoxes: List<RawOcrBoundingBox> = emptyList(),
)
