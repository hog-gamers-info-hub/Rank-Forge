package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry

enum class FreeDesignHeaderField {
    ORGANIZATION_NAME,
    TOURNAMENT_NAME,
    STAGE_NAME,
    RESULT_HEADING,
    DATE;

    companion object {
        val REQUIRED_FIELDS: Set<FreeDesignHeaderField> = setOf(
            TOURNAMENT_NAME,
            STAGE_NAME,
            RESULT_HEADING,
            DATE,
        )
    }
}

enum class FreeDesignTypographyRole {
    TITLE,
    SECONDARY,
    RESULT_HEADING,
    DATE,
}

enum class FreeDesignTextAlignment {
    CENTER,
    START,
    END,
}

data class FreeDesignHeaderTextStyle(
    val textSize: Float,
    val color: String,
    val alignment: FreeDesignTextAlignment,
    val typographyRole: FreeDesignTypographyRole,
    val maxWidthPx: Float,
    val minimumTextSizePx: Float,
    val bold: Boolean = false,
    val letterSpacing: Float = 0f,
)

data class FreeDesignHeaderAnchor(
    val centerX: Float,
    val centerY: Float,
    val style: FreeDesignHeaderTextStyle,
)

data class FreeDesignResultTextStyle(
    val textSizeMultiplier: Float,
    val teamNameStartPaddingPx: Float,
)

data class FreeDesignTemplate(
    val id: String,
    val displayName: String,
    val assetPath: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val tableGeometry: CustomDesignEffectiveGridGeometry,
    val resultColumnTextColors: CustomDesignColumnTextColors,
    val resultTextStyle: FreeDesignResultTextStyle,
    val headerAnchors: Map<FreeDesignHeaderField, FreeDesignHeaderAnchor>,
)
