package com.hoggamers.rankforge.domain.ocr.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class FreeFireMaxScoreboardLayoutTest {
    private val layout = FreeFireMaxScoreboardLayout.definition
    private val validator = ScoreboardLayoutValidator()

    @Test
    fun normalizedRectangleRejectsCoordinatesOutsideImageBounds() {
        assertThrows(IllegalArgumentException::class.java) {
            NormalizedOcrRect(0.90, 0.20, 0.20, 0.10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NormalizedOcrRect(0.20, -0.01, 0.10, 0.10)
        }
    }

    @Test
    fun normalizedRectangleConvertsDeterministicallyForCalibrationDimensions() {
        val pixelRect = layout.overallContentRect.toPixelRect(
            FreeFireMaxScoreboardLayout.CALIBRATION_WIDTH,
            FreeFireMaxScoreboardLayout.CALIBRATION_HEIGHT,
        )

        assertEquals(OcrPixelRect(x = 208, y = 158, width = 1168, height = 468), pixelRect)
    }

    @Test
    fun calibrationDimensionsAreAccepted() {
        val result = validator.validate(
            layout,
            FreeFireMaxScoreboardLayout.CALIBRATION_WIDTH,
            FreeFireMaxScoreboardLayout.CALIBRATION_HEIGHT,
        )

        assertEquals(ScoreboardLayoutValidationResult.Compatible, result)
    }

    @Test
    fun nonPositiveDimensionsAreRejectedBeforeAspectValidation() {
        listOf(
            0 to 720,
            1_600 to 0,
            -1 to 720,
        ).forEach { (width, height) ->
            assertEquals(
                ScoreboardLayoutValidationResult.Incompatible(
                    ScoreboardLayoutValidationError.INVALID_DIMENSIONS,
                ),
                validator.validate(layout, width, height),
            )
        }
    }

    @Test
    fun exactSupportedAspectRatioBoundariesAreAccepted() {
        assertEquals(
            ScoreboardLayoutValidationResult.Compatible,
            validator.validate(layout, imageWidth = 211, imageHeight = 100),
        )
        assertEquals(
            ScoreboardLayoutValidationResult.Compatible,
            validator.validate(layout, imageWidth = 233, imageHeight = 100),
        )
    }

    @Test
    fun portraitAndUnsupportedAspectRatiosAreRejected() {
        assertEquals(
            ScoreboardLayoutValidationResult.Incompatible(
                ScoreboardLayoutValidationError.NOT_LANDSCAPE,
            ),
            validator.validate(layout, imageWidth = 720, imageHeight = 1_600),
        )
        assertEquals(
            ScoreboardLayoutValidationResult.Incompatible(
                ScoreboardLayoutValidationError.UNSUPPORTED_ASPECT_RATIO,
            ),
            validator.validate(layout, imageWidth = 1_600, imageHeight = 1_000),
        )
    }

    @Test
    fun contentPanelsAndExclusionZonesAreDefined() {
        assertEquals(NormalizedOcrRect(0.13, 0.22, 0.73, 0.65), layout.overallContentRect)
        assertEquals(
            NormalizedOcrRect(0.13, 0.22, 0.40, 0.65),
            layout.panels.single { it.id == ScoreboardPanelId.LEFT }.contentRect,
        )
        assertEquals(
            NormalizedOcrRect(0.54, 0.22, 0.32, 0.65),
            layout.panels.single { it.id == ScoreboardPanelId.RIGHT }.contentRect,
        )
        assertEquals(
            setOf(
                ScoreboardExclusionZoneType.TOP_LOGO,
                ScoreboardExclusionZoneType.BOTTOM_CONTROLS_BACK_BUTTON,
                ScoreboardExclusionZoneType.BOTTOM_LEFT_NUMERIC_OVERLAY,
                ScoreboardExclusionZoneType.RIGHT_SIDE_BACKGROUND,
            ),
            layout.exclusionZones.map { it.type }.toSet(),
        )
    }

    @Test
    fun panelRowsAndPlacementMappingsMatchTheApprovedLayout() {
        val leftPanel = layout.panels.single { it.id == ScoreboardPanelId.LEFT }
        val rightPanel = layout.panels.single { it.id == ScoreboardPanelId.RIGHT }

        assertEquals(5, leftPanel.rows.size)
        assertEquals(7, rightPanel.rows.size)
        assertEquals((1..5).toList(), leftPanel.rows.map { it.placementId })
        assertEquals((6..12).toList(), rightPanel.rows.map { it.placementId })
        assertEquals(
            PlacementToPanelRowMapping(
                placementId = 1,
                panelId = ScoreboardPanelId.LEFT,
                rowIndex = 0,
                visibility = ScoreboardRowVisibility.VISIBLE,
            ),
            layout.placementMappings.single { it.placementId == 1 },
        )
        assertEquals(
            PlacementToPanelRowMapping(
                placementId = 12,
                panelId = ScoreboardPanelId.RIGHT,
                rowIndex = 6,
                visibility = ScoreboardRowVisibility.CONSTRAINED_REFERENCE,
            ),
            layout.placementMappings.single { it.placementId == 12 },
        )
    }

    @Test
    fun everyExpectedRowDefinesFieldAndRepeatedLabelExclusionZones() {
        layout.panels.flatMap { it.rows }.forEach { row ->
            assertTrue(row.fieldZones.any { it.type == ScoreboardFieldZoneType.PLACEMENT_NUMBER })
            assertTrue(row.fieldZones.any { it.type == ScoreboardFieldZoneType.PLAYER_NAME })
            assertTrue(row.fieldZones.any { it.type == ScoreboardFieldZoneType.ELIMINATION_VALUE })
            assertNotNull(
                row.exclusionZones.singleOrNull {
                    it.type == ScoreboardExclusionZoneType.REPEATED_ELIMINATIONS_LABEL
                },
            )
        }
    }
}
