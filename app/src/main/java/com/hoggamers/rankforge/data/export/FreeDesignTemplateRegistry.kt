package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry

object FreeDesignTemplateRegistry {
    const val DEFAULT_TEMPLATE_ID = "free_design_v1"
    const val DEFAULT_ASSET_PATH = "result_templates/free_design_v1.webp"
    const val BLUE_TEMPLATE_ID = "free_design_v2_blue"
    const val BLUE_ASSET_PATH = "result_templates/free_design_v2_blue.webp"
    const val V3_TEMPLATE_ID = "free_design_v3"
    const val V3_ASSET_PATH = "result_templates/free_design_v3.webp"
    const val V4_TEMPLATE_ID = "free_design_v4"
    const val V4_ASSET_PATH = "result_templates/free_design_v4.webp"
    const val V5_TEMPLATE_ID = "free_design_v5"
    const val V5_ASSET_PATH = "result_templates/free_design_v5.webp"
    const val V6_TEMPLATE_ID = "free_design_v6"
    const val V6_ASSET_PATH = "result_templates/free_design_v6.webp"
    const val V7_TEMPLATE_ID = "free_design_v7"
    const val V7_ASSET_PATH = "result_templates/free_design_v7.webp"

    private const val SOURCE_WIDTH = 1254
    private const val SOURCE_HEIGHT = 1254
    private const val V3_SOURCE_WIDTH = 1072
    private const val V3_SOURCE_HEIGHT = 1467
    private const val HEADER_COLOR = "#F3E7C2"
    private const val BLUE_HEADER_COLOR = "#F4F7FF"
    private const val V3_RESULT_COLOR = "#F4F4F4"
    private const val V4_RESULT_COLOR = "#111111"
    private const val V5_RESULT_COLOR = "#F2F0FF"
    private const val V5_HEADER_COLOR = "#F2F0FF"
    private const val V6_SOURCE_WIDTH = 1374
    private const val V6_SOURCE_HEIGHT = 1145
    private const val V6_RESULT_COLOR = "#111111"
    private const val V6_HEADER_COLOR = "#F4F4F4"
    private const val V7_SOURCE_WIDTH = 1122
    private const val V7_SOURCE_HEIGHT = 1402
    private const val V7_RESULT_COLOR = "#E8EDF2"
    private const val V7_HEADER_COLOR = "#E8EDF2"

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

    private val blackGoldTemplate = FreeDesignTemplate(
        id = V3_TEMPLATE_ID,
        displayName = "Black Gold",
        assetPath = V3_ASSET_PATH,
        sourceWidth = V3_SOURCE_WIDTH,
        sourceHeight = V3_SOURCE_HEIGHT,
        tableGeometry = CustomDesignEffectiveGridGeometry(
            sourceWidth = V3_SOURCE_WIDTH,
            sourceHeight = V3_SOURCE_HEIGHT,
            columnX = mapOf(
                CustomDesignAnchorField.TEAM_NAME to 118f,
                CustomDesignAnchorField.WIN to 581f,
                CustomDesignAnchorField.POSITION_POINTS to 708f,
                CustomDesignAnchorField.TOTAL_KILLS to 836f,
                CustomDesignAnchorField.TOTAL_POINTS to 971f,
            ),
            rowY = mapOf(
                1 to 471.5f,
                2 to 536.5f,
                3 to 601f,
                4 to 665.5f,
                5 to 729.5f,
                6 to 794.5f,
                7 to 860.5f,
                8 to 926.5f,
                9 to 991.5f,
                10 to 1055.5f,
                11 to 1120.5f,
                12 to 1185f,
            ),
        ),
        resultColumnTextColors = CustomDesignColumnTextColors.fromMap(
            CustomDesignAnchorField.entries.associateWith { V3_RESULT_COLOR },
        ) ?: error("Free Design v3 must define all result-column colors"),
        resultTextStyle = FreeDesignResultTextStyle(
            textSizeMultiplier = 1.30f,
            teamNameStartPaddingPx = 16f,
        ),
        headerAnchors = mapOf(
            FreeDesignHeaderField.TOURNAMENT_NAME to headerAnchor(
                centerX = V3_SOURCE_WIDTH / 2f,
                centerY = 130f,
                textSize = 68f,
                maxWidthPx = 900f,
                minimumTextSizePx = 32f,
                typographyRole = FreeDesignTypographyRole.TITLE,
            ),
            FreeDesignHeaderField.ORGANIZER_NAME to headerAnchor(
                centerX = V3_SOURCE_WIDTH / 2f,
                centerY = 205f,
                textSize = 40f,
                maxWidthPx = 850f,
                minimumTextSizePx = 22f,
                typographyRole = FreeDesignTypographyRole.SECONDARY,
            ),
            FreeDesignHeaderField.RESULT_HEADING to headerAnchor(
                centerX = V3_SOURCE_WIDTH / 2f,
                centerY = 270f,
                textSize = 22f,
                maxWidthPx = 820f,
                minimumTextSizePx = 16f,
                typographyRole = FreeDesignTypographyRole.RESULT_HEADING,
            ),
            FreeDesignHeaderField.DATE to headerAnchor(
                centerX = V3_SOURCE_WIDTH / 2f,
                centerY = 270f,
                textSize = 18f,
                maxWidthPx = 700f,
                minimumTextSizePx = 12f,
                typographyRole = FreeDesignTypographyRole.DATE,
            ),
        ),
    )

    private val orangeBlazeTemplate = FreeDesignTemplate(
        id = V4_TEMPLATE_ID,
        displayName = "Orange Blaze",
        assetPath = V4_ASSET_PATH,
        sourceWidth = SOURCE_WIDTH,
        sourceHeight = SOURCE_HEIGHT,
        tableGeometry = CustomDesignEffectiveGridGeometry(
            sourceWidth = SOURCE_WIDTH,
            sourceHeight = SOURCE_HEIGHT,
            columnX = mapOf(
                CustomDesignAnchorField.TEAM_NAME to 164f,
                CustomDesignAnchorField.WIN to 699f,
                CustomDesignAnchorField.POSITION_POINTS to 844f,
                CustomDesignAnchorField.TOTAL_KILLS to 984f,
                CustomDesignAnchorField.TOTAL_POINTS to 1131f,
            ),
            rowY = mapOf(
                1 to 362f,
                2 to 424f,
                3 to 486f,
                4 to 547.5f,
                5 to 609f,
                6 to 671.5f,
                7 to 734f,
                8 to 796.5f,
                9 to 859f,
                10 to 922f,
                11 to 985f,
                12 to 1047.5f,
            ),
        ),
        resultColumnTextColors = CustomDesignColumnTextColors.fromMap(
            CustomDesignAnchorField.entries.associateWith { V4_RESULT_COLOR },
        ) ?: error("Free Design v4 must define all result-column colors"),
        resultTextStyle = FreeDesignResultTextStyle(
            textSizeMultiplier = 1.20f,
            teamNameStartPaddingPx = 24f,
        ),
        headerAnchors = mapOf(
            FreeDesignHeaderField.TOURNAMENT_NAME to headerAnchor(
                centerX = SOURCE_WIDTH / 2f,
                centerY = 108f,
                textSize = 72f,
                maxWidthPx = 1050f,
                minimumTextSizePx = 32f,
                typographyRole = FreeDesignTypographyRole.TITLE,
            ),
            FreeDesignHeaderField.ORGANIZER_NAME to headerAnchor(
                centerX = SOURCE_WIDTH / 2f,
                centerY = 165f,
                textSize = 42f,
                maxWidthPx = 980f,
                minimumTextSizePx = 22f,
                typographyRole = FreeDesignTypographyRole.SECONDARY,
            ),
            FreeDesignHeaderField.RESULT_HEADING to headerAnchor(
                centerX = SOURCE_WIDTH / 2f,
                centerY = 218f,
                textSize = 22f,
                maxWidthPx = 980f,
                minimumTextSizePx = 16f,
                typographyRole = FreeDesignTypographyRole.RESULT_HEADING,
            ),
            FreeDesignHeaderField.DATE to headerAnchor(
                centerX = SOURCE_WIDTH / 2f,
                centerY = 218f,
                textSize = 18f,
                maxWidthPx = 700f,
                minimumTextSizePx = 12f,
                typographyRole = FreeDesignTypographyRole.DATE,
            ),
        ),
    )

    private val purpleLuxeTemplate = FreeDesignTemplate(
        id = V5_TEMPLATE_ID,
        displayName = "Purple Luxe",
        assetPath = V5_ASSET_PATH,
        sourceWidth = SOURCE_WIDTH,
        sourceHeight = SOURCE_HEIGHT,
        tableGeometry = CustomDesignEffectiveGridGeometry(
            sourceWidth = SOURCE_WIDTH,
            sourceHeight = SOURCE_HEIGHT,
            columnX = mapOf(
                CustomDesignAnchorField.TEAM_NAME to 164f,
                CustomDesignAnchorField.WIN to 719f,
                CustomDesignAnchorField.POSITION_POINTS to 860f,
                CustomDesignAnchorField.TOTAL_KILLS to 1002f,
                CustomDesignAnchorField.TOTAL_POINTS to 1144f,
            ),
            rowY = mapOf(
                1 to 366f,
                2 to 430.5f,
                3 to 494.5f,
                4 to 558.5f,
                5 to 622.5f,
                6 to 686.5f,
                7 to 749.5f,
                8 to 812.5f,
                9 to 876.5f,
                10 to 939f,
                11 to 1002.5f,
                12 to 1067f,
            ),
        ),
        resultColumnTextColors = CustomDesignColumnTextColors.fromMap(
            CustomDesignAnchorField.entries.associateWith { V5_RESULT_COLOR },
        ) ?: error("Free Design v5 must define all result-column colors"),
        resultTextStyle = FreeDesignResultTextStyle(
            textSizeMultiplier = 1.20f,
            teamNameStartPaddingPx = 20f,
        ),
        headerAnchors = mapOf(
            FreeDesignHeaderField.TOURNAMENT_NAME to headerAnchor(
                centerX = SOURCE_WIDTH / 2f,
                centerY = 95f,
                textSize = 72f,
                maxWidthPx = 1050f,
                minimumTextSizePx = 32f,
                typographyRole = FreeDesignTypographyRole.TITLE,
                color = V5_HEADER_COLOR,
            ),
            FreeDesignHeaderField.ORGANIZER_NAME to headerAnchor(
                centerX = SOURCE_WIDTH / 2f,
                centerY = 160f,
                textSize = 42f,
                maxWidthPx = 980f,
                minimumTextSizePx = 22f,
                typographyRole = FreeDesignTypographyRole.SECONDARY,
                color = V5_HEADER_COLOR,
            ),
            FreeDesignHeaderField.RESULT_HEADING to headerAnchor(
                centerX = SOURCE_WIDTH / 2f,
                centerY = 218f,
                textSize = 22f,
                maxWidthPx = 980f,
                minimumTextSizePx = 16f,
                typographyRole = FreeDesignTypographyRole.RESULT_HEADING,
                color = V5_HEADER_COLOR,
            ),
            FreeDesignHeaderField.DATE to headerAnchor(
                centerX = SOURCE_WIDTH / 2f,
                centerY = 218f,
                textSize = 18f,
                maxWidthPx = 700f,
                minimumTextSizePx = 12f,
                typographyRole = FreeDesignTypographyRole.DATE,
                color = V5_HEADER_COLOR,
            ),
        ),
    )

    private val crimsonEdgeTemplate = FreeDesignTemplate(
        id = V6_TEMPLATE_ID,
        displayName = "Crimson Edge",
        assetPath = V6_ASSET_PATH,
        sourceWidth = V6_SOURCE_WIDTH,
        sourceHeight = V6_SOURCE_HEIGHT,
        tableGeometry = CustomDesignEffectiveGridGeometry(
            sourceWidth = V6_SOURCE_WIDTH,
            sourceHeight = V6_SOURCE_HEIGHT,
            columnX = mapOf(
                CustomDesignAnchorField.TEAM_NAME to 214f,
                CustomDesignAnchorField.WIN to 667f,
                CustomDesignAnchorField.POSITION_POINTS to 850.5f,
                CustomDesignAnchorField.TOTAL_KILLS to 1036.5f,
                CustomDesignAnchorField.TOTAL_POINTS to 1235.5f,
            ),
            rowY = mapOf(
                1 to 395f,
                2 to 453.5f,
                3 to 512f,
                4 to 570.5f,
                5 to 628f,
                6 to 685.5f,
                7 to 743.5f,
                8 to 802f,
                9 to 859f,
                10 to 916f,
                11 to 973f,
                12 to 1030.5f,
            ),
        ),
        resultColumnTextColors = CustomDesignColumnTextColors.fromMap(
            CustomDesignAnchorField.entries.associateWith { V6_RESULT_COLOR },
        ) ?: error("Free Design v6 must define all result-column colors"),
        resultTextStyle = FreeDesignResultTextStyle(
            textSizeMultiplier = 1.20f,
            teamNameStartPaddingPx = 20f,
        ),
        headerAnchors = mapOf(
            FreeDesignHeaderField.TOURNAMENT_NAME to headerAnchor(
                centerX = V6_SOURCE_WIDTH / 2f,
                centerY = 90f,
                textSize = 72f,
                maxWidthPx = 1150f,
                minimumTextSizePx = 32f,
                typographyRole = FreeDesignTypographyRole.TITLE,
                color = V6_HEADER_COLOR,
            ),
            FreeDesignHeaderField.ORGANIZER_NAME to headerAnchor(
                centerX = V6_SOURCE_WIDTH / 2f,
                centerY = 155f,
                textSize = 42f,
                maxWidthPx = 1100f,
                minimumTextSizePx = 22f,
                typographyRole = FreeDesignTypographyRole.SECONDARY,
                color = V6_HEADER_COLOR,
            ),
            FreeDesignHeaderField.RESULT_HEADING to headerAnchor(
                centerX = V6_SOURCE_WIDTH / 2f,
                centerY = 215f,
                textSize = 22f,
                maxWidthPx = 1050f,
                minimumTextSizePx = 16f,
                typographyRole = FreeDesignTypographyRole.RESULT_HEADING,
                color = V6_HEADER_COLOR,
            ),
            FreeDesignHeaderField.DATE to headerAnchor(
                centerX = V6_SOURCE_WIDTH / 2f,
                centerY = 215f,
                textSize = 18f,
                maxWidthPx = 700f,
                minimumTextSizePx = 12f,
                typographyRole = FreeDesignTypographyRole.DATE,
                color = V6_HEADER_COLOR,
            ),
        ),
    )

    private val steelPhantomTemplate = FreeDesignTemplate(
        id = V7_TEMPLATE_ID,
        displayName = "Steel Phantom",
        assetPath = V7_ASSET_PATH,
        sourceWidth = V7_SOURCE_WIDTH,
        sourceHeight = V7_SOURCE_HEIGHT,
        tableGeometry = CustomDesignEffectiveGridGeometry(
            sourceWidth = V7_SOURCE_WIDTH,
            sourceHeight = V7_SOURCE_HEIGHT,
            columnX = mapOf(
                CustomDesignAnchorField.TEAM_NAME to 136f,
                CustomDesignAnchorField.WIN to 641.5f,
                CustomDesignAnchorField.POSITION_POINTS to 763.5f,
                CustomDesignAnchorField.TOTAL_KILLS to 886.5f,
                CustomDesignAnchorField.TOTAL_POINTS to 1014f,
            ),
            rowY = mapOf(
                1 to 415.5f,
                2 to 486f,
                3 to 555.5f,
                4 to 625f,
                5 to 694.5f,
                6 to 765f,
                7 to 834f,
                8 to 903.5f,
                9 to 974f,
                10 to 1043.5f,
                11 to 1112.5f,
                12 to 1181.5f,
            ),
        ),
        resultColumnTextColors = CustomDesignColumnTextColors.fromMap(
            CustomDesignAnchorField.entries.associateWith { V7_RESULT_COLOR },
        ) ?: error("Free Design v7 must define all result-column colors"),
        resultTextStyle = FreeDesignResultTextStyle(
            textSizeMultiplier = 1.20f,
            teamNameStartPaddingPx = 16f,
        ),
        headerAnchors = mapOf(
            FreeDesignHeaderField.TOURNAMENT_NAME to headerAnchor(
                centerX = V7_SOURCE_WIDTH / 2f,
                centerY = 125f,
                textSize = 68f,
                maxWidthPx = 940f,
                minimumTextSizePx = 32f,
                typographyRole = FreeDesignTypographyRole.TITLE,
                color = V7_HEADER_COLOR,
            ),
            FreeDesignHeaderField.ORGANIZER_NAME to headerAnchor(
                centerX = V7_SOURCE_WIDTH / 2f,
                centerY = 195f,
                textSize = 40f,
                maxWidthPx = 900f,
                minimumTextSizePx = 22f,
                typographyRole = FreeDesignTypographyRole.SECONDARY,
                color = V7_HEADER_COLOR,
            ),
            FreeDesignHeaderField.RESULT_HEADING to headerAnchor(
                centerX = V7_SOURCE_WIDTH / 2f,
                centerY = 258f,
                textSize = 22f,
                maxWidthPx = 860f,
                minimumTextSizePx = 16f,
                typographyRole = FreeDesignTypographyRole.RESULT_HEADING,
                color = V7_HEADER_COLOR,
            ),
            FreeDesignHeaderField.DATE to headerAnchor(
                centerX = V7_SOURCE_WIDTH / 2f,
                centerY = 258f,
                textSize = 18f,
                maxWidthPx = 700f,
                minimumTextSizePx = 12f,
                typographyRole = FreeDesignTypographyRole.DATE,
                color = V7_HEADER_COLOR,
            ),
        ),
    )

    private val builtInTemplates: List<FreeDesignTemplate> =
        listOf(
            defaultTemplate,
            blueNeonTemplate,
            blackGoldTemplate,
            orangeBlazeTemplate,
            purpleLuxeTemplate,
            crimsonEdgeTemplate,
            steelPhantomTemplate,
        )

    private val templatesById: Map<String, FreeDesignTemplate> =
        builtInTemplates.associateBy { it.id }

    val all: List<FreeDesignTemplate> = builtInTemplates

    fun default(): FreeDesignTemplate = defaultTemplate

    fun findById(id: String): FreeDesignTemplate? = templatesById[id]

    private fun headerAnchor(
        centerX: Float = SOURCE_WIDTH / 2f,
        centerY: Float,
        textSize: Float,
        maxWidthPx: Float,
        minimumTextSizePx: Float,
        typographyRole: FreeDesignTypographyRole,
        color: String = HEADER_COLOR,
    ): FreeDesignHeaderAnchor = FreeDesignHeaderAnchor(
        centerX = centerX,
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
