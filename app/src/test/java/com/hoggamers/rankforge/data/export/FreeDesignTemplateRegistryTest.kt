package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeDesignTemplateRegistryTest {
    private val template1 = FreeDesignTemplateRegistry.default()
    private val template2 = requireNotNull(
        FreeDesignTemplateRegistry.findById(FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID),
    )
    private val template3 = requireNotNull(
        FreeDesignTemplateRegistry.findById(FreeDesignTemplateRegistry.V3_TEMPLATE_ID),
    )

    @Test
    fun registryContainsExactlyThreeTemplatesInDeterministicOrder() {
        assertEquals(3, FreeDesignTemplateRegistry.all.size)
        assertEquals(
            listOf(
                FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID,
                FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID,
                FreeDesignTemplateRegistry.V3_TEMPLATE_ID,
            ),
            FreeDesignTemplateRegistry.all.map { it.id },
        )
        assertEquals(
            3,
            FreeDesignTemplateRegistry.all.map { it.id }.toSet().size,
        )
        assertEquals(
            3,
            FreeDesignTemplateRegistry.all.map { it.assetPath }.toSet().size,
        )
    }

    @Test
    fun defaultAndLookupHaveStableIdentity() {
        assertEquals(FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID, template1.id)
        assertEquals(FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID, FreeDesignTemplateRegistry.default().id)
        assertEquals(template1, FreeDesignTemplateRegistry.findById(template1.id))
        assertEquals(template2, FreeDesignTemplateRegistry.findById(template2.id))
        assertEquals(template3, FreeDesignTemplateRegistry.findById(template3.id))
        assertNull(FreeDesignTemplateRegistry.findById("unknown_template"))
    }

    @Test
    fun template1VisualContractRemainsUnchanged() {
        assertEquals("Gold", template1.displayName)
        assertEquals(
            "result_templates/free_design_v1.webp",
            template1.assetPath,
        )
        assertEquals(1254, template1.sourceWidth)
        assertEquals(1254, template1.sourceHeight)
        assertEquals(1.1f, template1.resultTextStyle.textSizeMultiplier, 0f)
        assertEquals(16f, template1.resultTextStyle.teamNameStartPaddingPx, 0f)
        assertEquals(72.8f, template1.headerAnchors.getValue(FreeDesignHeaderField.TOURNAMENT_NAME).style.textSize, 0f)
        assertEquals(46f, template1.headerAnchors.getValue(FreeDesignHeaderField.ORGANIZER_NAME).style.textSize, 0f)
        assertEquals(120f, template1.headerAnchors.getValue(FreeDesignHeaderField.TOURNAMENT_NAME).centerY, 0f)
        assertEquals(185f, template1.headerAnchors.getValue(FreeDesignHeaderField.ORGANIZER_NAME).centerY, 0f)
    }

    @Test
    fun template2ContractMatchesBlueNeonSpecification() {
        assertEquals("Blue Neon", template2.displayName)
        assertEquals("result_templates/free_design_v2_blue.webp", template2.assetPath)
        assertEquals(1254, template2.sourceWidth)
        assertEquals(1254, template2.sourceHeight)
        assertEquals(
            mapOf(
                CustomDesignAnchorField.TEAM_NAME to 199f,
                CustomDesignAnchorField.WIN to 676f,
                CustomDesignAnchorField.POSITION_POINTS to 820f,
                CustomDesignAnchorField.TOTAL_KILLS to 966f,
                CustomDesignAnchorField.TOTAL_POINTS to 1118f,
            ),
            template2.tableGeometry.columnX,
        )
        assertEquals(
            mapOf(
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
            template2.tableGeometry.rowY,
        )
        assertEquals(1.30f, template2.resultTextStyle.textSizeMultiplier, 0f)
        assertEquals(16f, template2.resultTextStyle.teamNameStartPaddingPx, 0f)
        assertEquals(
            CustomDesignAnchorField.entries.associateWith { "#F4F7FF" },
            template2.resultColumnTextColors.asMap(),
        )
        assertEquals(76f, template2.headerAnchors.getValue(FreeDesignHeaderField.TOURNAMENT_NAME).style.textSize, 0f)
        assertEquals(47f, template2.headerAnchors.getValue(FreeDesignHeaderField.ORGANIZER_NAME).style.textSize, 0f)
        assertEquals(24f, template2.headerAnchors.getValue(FreeDesignHeaderField.RESULT_HEADING).style.textSize, 0f)
    }

    @Test
    fun template3ContractMatchesBlackGoldSpecification() {
        assertEquals("Black Gold", template3.displayName)
        assertEquals(FreeDesignTemplateRegistry.V3_TEMPLATE_ID, template3.id)
        assertEquals(FreeDesignTemplateRegistry.V3_ASSET_PATH, template3.assetPath)
        assertEquals(1072, template3.sourceWidth)
        assertEquals(1467, template3.sourceHeight)
        assertEquals(
            mapOf(
                CustomDesignAnchorField.TEAM_NAME to 118f,
                CustomDesignAnchorField.WIN to 581f,
                CustomDesignAnchorField.POSITION_POINTS to 708f,
                CustomDesignAnchorField.TOTAL_KILLS to 836f,
                CustomDesignAnchorField.TOTAL_POINTS to 971f,
            ),
            template3.tableGeometry.columnX,
        )
        assertEquals(
            mapOf(
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
            template3.tableGeometry.rowY,
        )
        assertEquals(1.30f, template3.resultTextStyle.textSizeMultiplier, 0f)
        assertEquals(16f, template3.resultTextStyle.teamNameStartPaddingPx, 0f)
        assertEquals(
            CustomDesignAnchorField.entries.associateWith { "#F4F4F4" },
            template3.resultColumnTextColors.asMap(),
        )
        assertEquals(536f, template3.headerAnchors.getValue(FreeDesignHeaderField.TOURNAMENT_NAME).centerX, 0f)
        assertEquals(536f, template3.headerAnchors.getValue(FreeDesignHeaderField.ORGANIZER_NAME).centerX, 0f)
        assertEquals(536f, template3.headerAnchors.getValue(FreeDesignHeaderField.RESULT_HEADING).centerX, 0f)
        assertEquals(536f, template3.headerAnchors.getValue(FreeDesignHeaderField.DATE).centerX, 0f)
        assertEquals(130f, template3.headerAnchors.getValue(FreeDesignHeaderField.TOURNAMENT_NAME).centerY, 0f)
        assertEquals(68f, template3.headerAnchors.getValue(FreeDesignHeaderField.TOURNAMENT_NAME).style.textSize, 0f)
        assertEquals(205f, template3.headerAnchors.getValue(FreeDesignHeaderField.ORGANIZER_NAME).centerY, 0f)
        assertEquals(40f, template3.headerAnchors.getValue(FreeDesignHeaderField.ORGANIZER_NAME).style.textSize, 0f)
        assertEquals(270f, template3.headerAnchors.getValue(FreeDesignHeaderField.RESULT_HEADING).centerY, 0f)
        assertEquals(22f, template3.headerAnchors.getValue(FreeDesignHeaderField.RESULT_HEADING).style.textSize, 0f)
    }

    @Test
    fun allTemplatesContainAllColumnsAndRowsWithinSourceBounds() {
        FreeDesignTemplateRegistry.all.forEach { template ->
            assertEquals(CustomDesignAnchorField.entries.toSet(), template.tableGeometry.columnX.keys)
            assertEquals((1..12).toSet(), template.tableGeometry.rowY.keys)
            assertTrue(template.tableGeometry.columnX.values.all { it in 0f..template.sourceWidth.toFloat() })
            assertTrue(template.tableGeometry.rowY.values.all { it in 0f..template.sourceHeight.toFloat() })
        }
    }

    @Test
    fun allTemplatesDefineEverySemanticColumnColor() {
        FreeDesignTemplateRegistry.all.forEach { template ->
            assertEquals(
                CustomDesignAnchorField.entries.toSet(),
                template.resultColumnTextColors.asMap().keys,
            )
        }
    }

    @Test
    fun headerAnchorsContainAllFieldsAndRemainWithinSourceBounds() {
        FreeDesignTemplateRegistry.all.forEach { template ->
            assertEquals(FreeDesignHeaderField.entries.toSet(), template.headerAnchors.keys)
            template.headerAnchors.values.forEach { anchor ->
                assertTrue(anchor.centerX in 0f..template.sourceWidth.toFloat())
                assertTrue(anchor.centerY in 0f..template.sourceHeight.toFloat())
                assertNotNull(anchor.style.typographyRole)
            }
        }
    }
}
