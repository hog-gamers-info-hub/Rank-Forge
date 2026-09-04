package com.hoggamers.rankforge.domain.ocr.customdesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDesignEffectiveGridGeometryTest {
    @Test
    fun noOverridesUsesAutomaticGeometry() {
        val automatic = automaticGeometry()

        val effective = resolveCustomDesignEffectiveGridGeometry(
            automatic = automatic,
            overrides = CustomDesignGridOverrides(),
        )

        assertEquals(automatic.columnX, effective?.columnX)
        assertEquals(mapOf(1 to 100f, 2 to 200f, 3 to 300f), effective?.rowY)
    }

    @Test
    fun manualColumnOverridesAutomaticWithoutChangingAutomatic() {
        val automatic = automaticGeometry()

        val effective = resolveCustomDesignEffectiveGridGeometry(
            automatic = automatic,
            overrides = CustomDesignGridOverrides(
                columnX = mapOf(CustomDesignAnchorField.WIN to 650f),
            ),
        )

        assertEquals(450f, automatic.columnX[CustomDesignAnchorField.WIN])
        assertEquals(650f, effective?.columnX?.get(CustomDesignAnchorField.WIN))
    }

    @Test
    fun manualRowOverridesAnyAutomaticRowSource() {
        val automatic = automaticGeometry()

        val effective = resolveCustomDesignEffectiveGridGeometry(
            automatic = automatic,
            overrides = CustomDesignGridOverrides(rowY = mapOf(2 to 240f, 3 to 350f)),
        )

        assertEquals(240f, effective?.rowY?.get(2))
        assertEquals(350f, effective?.rowY?.get(3))
        assertEquals(CustomDesignRowCoordinateSource.INTERPOLATED, automatic.rowY[2]?.source)
    }

    @Test
    fun clearingOverrideFallsBackToAutomatic() {
        val automatic = automaticGeometry()
        val overrides = CustomDesignGridOverrides(
            columnX = mapOf(CustomDesignAnchorField.WIN to 650f),
            rowY = mapOf(2 to 240f),
        )
        val cleared = overrides.copy(
            columnX = overrides.columnX - CustomDesignAnchorField.WIN,
            rowY = overrides.rowY - 2,
        )

        val effective = resolveCustomDesignEffectiveGridGeometry(automatic, cleared)

        assertEquals(450f, effective?.columnX?.get(CustomDesignAnchorField.WIN))
        assertEquals(200f, effective?.rowY?.get(2))
    }

    @Test
    fun overrideForUnresolvedCoordinateDoesNotInventEffectiveLine() {
        val effective = resolveCustomDesignEffectiveGridGeometry(
            automatic = automaticGeometry().copy(
                columnX = mapOf(CustomDesignAnchorField.TEAM_NAME to 300f),
                rowY = mapOf(1 to CustomDesignRowCoordinate(100f, CustomDesignRowCoordinateSource.OCR)),
            ),
            overrides = CustomDesignGridOverrides(
                columnX = mapOf(CustomDesignAnchorField.WIN to 650f),
                rowY = mapOf(2 to 240f),
            ),
        )

        assertTrue(effective?.columnX?.containsKey(CustomDesignAnchorField.WIN) == false)
        assertFalse(effective?.rowY?.containsKey(2) == true)
    }

    private fun automaticGeometry() = CustomDesignGridGeometry(
        sourceWidth = 1080,
        sourceHeight = 1350,
        columnX = mapOf(
            CustomDesignAnchorField.TEAM_NAME to 300f,
            CustomDesignAnchorField.WIN to 450f,
        ),
        rowY = mapOf(
            1 to CustomDesignRowCoordinate(100f, CustomDesignRowCoordinateSource.OCR),
            2 to CustomDesignRowCoordinate(200f, CustomDesignRowCoordinateSource.INTERPOLATED),
            3 to CustomDesignRowCoordinate(300f, CustomDesignRowCoordinateSource.EXTRAPOLATED),
        ),
        estimatedRowStep = 100f,
    )
}
