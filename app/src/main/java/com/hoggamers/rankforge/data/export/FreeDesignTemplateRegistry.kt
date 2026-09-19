package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry

object FreeDesignTemplateRegistry {
    const val DEFAULT_TEMPLATE_ID = "free_design_v1"
    const val DEFAULT_ASSET_PATH = "result_templates/free_design_v1.webp"
    const val BLUE_TEMPLATE_ID = "free_design_v2_blue"
    const val BLUE_ASSET_PATH = "result_templates/free_design_v2_blue.webp"

    private const val SOURCE_WIDTH = 1254
    private const val SOURCE_HEIGHT = 1254
    private const val HEADER_COLOR = "#F3E7C2"
    private const val BLUE_HEADER_COLOR = "#F4F7FF"

    private val defaultTemplate = FreeDesignTemplate(
        id = DEFAULT_TEMPLATE_ID,
        displayName = "Gold",
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
        resultTextStyle = FreeDesignResultTextStyle(
            textSizeMultiplier = 1.1f,
            teamNameStartPaddingPx = 16f,
        ),
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
                textSize = 46f,
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

    private val blueNeonTemplate = FreeDesignTemplate(
        id = BLUE_TEMPLATE_ID,
        displayName = "Blue Neon",
        assetPath = BLUE_ASSET_PATH,
        sourceWidth = SOURCE_WIDTH,
        sourceHeight = SOURCE_HEIGHT,
        tableGeometry = CustomDesignEffectiveGridGeometry(
            sourceWidth = SOURCE_WIDTH,
            sourceHeight = SOURCE_HEIGHT,
            columnX = mapOf(
                CustomDesignAnchorField.TEAM_NAME to 199f,
                CustomDesignAnchorField.WIN to 676f,
                CustomDesignAnchorField.POSITION_POINTS to 820f,
                CustomDesignAnchorField.TOTAL_KILLS to 966f,
                CustomDesignAnchorField.TOTAL_POINTS to 1118f,
            ),
            rowY = mapOf(
                1 to 375f,
                2 to 437f,
                3 to 499f,
                4 to 560f,
                5 to 622f,
                6 to 684f,
                7 to 746f,
                8 to 808f,
                9 to 870f,
                10 to 933f,
                11 to 995f,
                12 to 1058f,
            ),
        ),
        resultColumnTextColors = CustomDesignColumnTextColors.fromMap(
            CustomDesignAnchorField.entries.associateWith { BLUE_HEADER_COLOR },
        ) ?: error("Free Design v2 must define all result-column colors"),
        resultTextStyle = FreeDesignResultTextStyle(
            textSizeMultiplier = 1.30f,
            teamNameStartPaddingPx = 16f,
        ),
        headerAnchors = mapOf(
            FreeDesignHeaderField.TOURNAMENT_NAME to headerAnchor(
                centerY = 100f,
                textSize = 76f,
                maxWidthPx = 1050f,
                minimumTextSizePx = 36f,
                typographyRole = FreeDesignTypographyRole.TITLE,
                color = BLUE_HEADER_COLOR,
            ),
            FreeDesignHeaderField.ORGANIZER_NAME to headerAnchor(
                centerY = 165f,
                textSize = 47f,
                maxWidthPx = 1000f,
                minimumTextSizePx = 26f,
                typographyRole = FreeDesignTypographyRole.SECONDARY,
                color = BLUE_HEADER_COLOR,
            ),
            FreeDesignHeaderField.RESULT_HEADING to headerAnchor(
                centerY = 225f,
                textSize = 24f,
                maxWidthPx = 1000f,
                minimumTextSizePx = 16f,
                typographyRole = FreeDesignTypographyRole.RESULT_HEADING,
                color = BLUE_HEADER_COLOR,
            ),
            FreeDesignHeaderField.DATE to headerAnchor(
                centerY = 225f,
                textSize = 20f,
                maxWidthPx = 800f,
                minimumTextSizePx = 14f,
                typographyRole = FreeDesignTypographyRole.DATE,
                color = BLUE_HEADER_COLOR,
            ),
        ),
    )

    private val builtInTemplates: List<FreeDesignTemplate> =
        listOf(defaultTemplate, blueNeonTemplate)

    private val templatesById: Map<String, FreeDesignTemplate> =
        builtInTemplates.associateBy { it.id }

    val all: List<FreeDesignTemplate> = builtInTemplates

    fun default(): FreeDesignTemplate = defaultTemplate

    fun findById(id: String): FreeDesignTemplate? = templatesById[id]

    private fun headerAnchor(
        centerY: Float,
        textSize: Float,
        maxWidthPx: Float,
        minimumTextSizePx: Float,
        typographyRole: FreeDesignTypographyRole,
        color: String = HEADER_COLOR,
    ): FreeDesignHeaderAnchor = FreeDesignHeaderAnchor(
        centerX = SOURCE_WIDTH / 2f,
        centerY = centerY,
        style = FreeDesignHeaderTextStyle(
            textSize = textSize,
            color = color,
            alignment = FreeDesignTextAlignment.CENTER,
            typographyRole = typographyRole,
            maxWidthPx = maxWidthPx,
            minimumTextSizePx = minimumTextSizePx,
        ),
    )
}
