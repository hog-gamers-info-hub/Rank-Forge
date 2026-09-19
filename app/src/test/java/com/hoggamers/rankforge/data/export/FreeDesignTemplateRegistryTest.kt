package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignAnchorField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeDesignTemplateRegistryTest {
    private val template = FreeDesignTemplateRegistry.default()

    @Test
    fun registryContainsExactlyOneTemplate() {
        assertEquals(1, FreeDesignTemplateRegistry.all.size)
        assertEquals(
            listOf(FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID),
            FreeDesignTemplateRegistry.all.map { it.id },
        )
    }

    @Test
    fun defaultTemplateHasStableIdentityAndSourceAsset() {
        assertEquals(FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID, template.id)
        assertEquals(
            "result_templates/free_design_v1.webp",
            template.assetPath,
        )
        assertEquals(1254, template.sourceWidth)
        assertEquals(1254, template.sourceHeight)
    }

    @Test
    fun tableContainsAllColumnsAndRowsWithinSourceBounds() {
        assertEquals(CustomDesignAnchorField.entries.toSet(), template.tableGeometry.columnX.keys)
        assertEquals((1..12).toSet(), template.tableGeometry.rowY.keys)
        assertTrue(
            template.tableGeometry.columnX.values.all { it in 0f..template.sourceWidth.toFloat() },
        )
        assertTrue(
            template.tableGeometry.rowY.values.all { it in 0f..template.sourceHeight.toFloat() },
        )
    }

    @Test
    fun resultTextColorsContainEverySemanticColumn() {
        assertEquals(
            CustomDesignAnchorField.entries.associateWith { "#F3E7C2" },
            template.resultColumnTextColors.asMap(),
        )
    }

    @Test
    fun headerAnchorsContainAllFieldsAndRemainWithinSourceBounds() {
        assertEquals(
            FreeDesignHeaderField.entries.toSet(),
            template.headerAnchors.keys,
        )
        template.headerAnchors.values.forEach { anchor ->
            assertTrue(anchor.centerX in 0f..template.sourceWidth.toFloat())
            assertTrue(anchor.centerY in 0f..template.sourceHeight.toFloat())
            assertNotNull(anchor.style.typographyRole)
        }
    }
}
