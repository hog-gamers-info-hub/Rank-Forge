package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry

object FreeDesignTemplateRegistry {
    const val DEFAULT_TEMPLATE_ID = "free_design_v1"
    const val DEFAULT_ASSET_PATH = "result_templates/free_design_v1.webp"

    private const val SOURCE_WIDTH = 1254
    private const val SOURCE_HEIGHT = 1254
    private const val HEADER_COLOR = "#F3E7C2"

    private val defaultTemplate = FreeDesignTemplate(
        id = DEFAULT_TEMPLATE_ID,
        assetPath = DEFAULT_ASSET_PATH,
        sourceWidth = SOURCE_WIDTH,
        sourceHeight = SOURCE_HEIGHT,
        tableGeometry = CustomDesignEffectiveGridGeometry(
            sourceWidth = SOURCE_WIDTH,
            sourceHeight = SOURCE_HEIGHT,
            columnX = mapOf(
                CustomDesignAnchorField.TEAM_NAME to 174f,
                CustomDesignAnchorField.WIN to 730f,
                CustomDesignAnchorField.POSITION_POINTS to 869f,
                CustomDesignAnchorField.TOTAL_KILLS to 998f,
                CustomDesignAnchorField.TOTAL_POINTS to 1135f,
            ),
            rowY = mapOf(
                1 to 392f,
                2 to 455f,
                3 to 518f,
                4 to 582f,
                5 to 645f,
                6 to 708f,
                7 to 772f,
                8 to 835f,
                9 to 898f,
                10 to 961f,
                11 to 1025f,
                12 to 1088f,
            ),
        ),
        resultColumnTextColors = CustomDesignColumnTextColors.fromMap(
            CustomDesignAnchorField.entries.associateWith { HEADER_COLOR },
        ) ?: error("Free Design v1 must define all result-column colors"),
        headerAnchors = mapOf(
            FreeDesignHeaderField.TOURNAMENT_NAME to headerAnchor(
                centerY = 120f,
                textSize = 72.8f,
                maxWidthPx = 1050f,
                minimumTextSizePx = 20f,
                typographyRole = FreeDesignTypographyRole.TITLE,
            ),
            FreeDesignHeaderField.ORGANIZER_NAME to headerAnchor(
                centerY = 185f,
                textSize = 50.4f,
                maxWidthPx = 1000f,
                minimumTextSizePx = 14f,
                typographyRole = FreeDesignTypographyRole.SECONDARY,
            ),
            FreeDesignHeaderField.RESULT_HEADING to headerAnchor(
                centerY = 250f,
                textSize = 22f,
                maxWidthPx = 1000f,
                minimumTextSizePx = 16f,
                typographyRole = FreeDesignTypographyRole.RESULT_HEADING,
            ),
            FreeDesignHeaderField.DATE to headerAnchor(
                centerY = 250f,
                textSize = 18f,
                maxWidthPx = 700f,
                minimumTextSizePx = 12f,
                typographyRole = FreeDesignTypographyRole.DATE,
            ),
        ),
    )

    private val templatesById: Map<String, FreeDesignTemplate> =
        mapOf(defaultTemplate.id to defaultTemplate)

    val all: List<FreeDesignTemplate> = templatesById.values.toList()

    fun default(): FreeDesignTemplate = defaultTemplate

    fun findById(id: String): FreeDesignTemplate? = templatesById[id]

    private fun headerAnchor(
        centerY: Float,
        textSize: Float,
        maxWidthPx: Float,
        minimumTextSizePx: Float,
        typographyRole: FreeDesignTypographyRole,
    ): FreeDesignHeaderAnchor = FreeDesignHeaderAnchor(
        centerX = SOURCE_WIDTH / 2f,
        centerY = centerY,
        style = FreeDesignHeaderTextStyle(
            textSize = textSize,
            color = HEADER_COLOR,
            alignment = FreeDesignTextAlignment.CENTER,
            typographyRole = typographyRole,
            maxWidthPx = maxWidthPx,
            minimumTextSizePx = minimumTextSizePx,
        ),
    )
}
