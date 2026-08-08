package com.hoggamers.rankforge.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitScreenshot2LowerRoiDiagnosticTest {
    @Test
    fun lowerDiagnosticDefinesExactlyTheTwoAuthoritativeRowsAndEighteenFields() {
        val fields = MlKitScreenshot2LowerRoiDiagnostic.canonicalFieldSpecsForTest()

        assertEquals(18, fields.size)
        assertEquals(listOf("A", "B"), fields.map { it.visualRow }.distinct())
        assertEquals(2, fields.count { it.type == "PLACEMENT" })
        assertEquals(8, fields.count { it.type == "PLAYER" })
        assertEquals(8, fields.count { it.type == "KILL" })
        assertTrue(fields.none { it.id.contains("_7") || it.id.contains("_8") })
        assertEquals(
            LowerRoiFieldSpec("LOWER_ROW_A_PLACEMENT", "PLACEMENT", "A", null, 675, 297, 710, 363),
            fields.first(),
        )
        assertEquals(
            LowerRoiFieldSpec("LOWER_ROW_B_KILL_4", "KILL", "B", 4, 1074, 412, 1090, 441),
            fields.last(),
        )
    }

    @Test
    fun visualRowAPlacement11EmitsPosition11() {
        assertEquals(
            LowerVisualRowResolution("A", 11, "EMIT_POSITION_11", 11),
            resolveLowerVisualRow("A", "11"),
        )
    }

    @Test
    fun visualRowBPlacement12EmitsPosition12() {
        assertEquals(
            LowerVisualRowResolution("B", 12, "EMIT_POSITION_12", 12),
            resolveLowerVisualRow("B", "12"),
        )
    }

    @Test
    fun visualRowAPlacement10IsIgnoredByLowerScreenshot() {
        assertEquals(
            LowerVisualRowResolution("A", 10, "IGNORED_UPPER_OWNS_POSITION", null),
            resolveLowerVisualRow("A", "10"),
        )
    }

    @Test
    fun visualRowBPlacement11EmitsPosition11() {
        assertEquals(
            LowerVisualRowResolution("B", 11, "EMIT_POSITION_11", 11),
            resolveLowerVisualRow("B", "11"),
        )
    }

    @Test
    fun absentPlacement12DoesNotForcePosition12() {
        val emittedPositions = listOf(
            resolveLowerVisualRow("A", "11"),
            resolveLowerVisualRow("B", ""),
        ).mapNotNull { it.emittedPosition }

        assertEquals(listOf(11), emittedPositions)
    }

    @Test
    fun lowerScreenshotNeverEmitsPositionsOneThroughTen() {
        val resolutions = listOf("1", "7", "10").map { placement ->
            resolveLowerVisualRow("A", placement)
        }

        assertTrue(resolutions.all { it.emittedPosition == null })
        assertTrue(resolutions.all { it.decision == "IGNORED_UPPER_OWNS_POSITION" })
    }

    @Test
    fun blankKillWithPlayerPresentInfersZero() {
        assertEquals(
            LowerKillFallback(
                resolvedText = "0",
                status = "ZERO_INFERRED_FROM_PLAYER_PRESENT",
            ),
            lowerKillFallback(killResolvedText = "", playerResolvedText = "APX INFERNO"),
        )
    }

    @Test
    fun blankKillWithBlankPlayerRemainsAbsent() {
        assertEquals(
            LowerKillFallback(resolvedText = "", status = null),
            lowerKillFallback(killResolvedText = "", playerResolvedText = ""),
        )
    }

    @Test
    fun killLetterOStillNormalizesToZero() {
        assertEquals(
            LowerKillNormalization(
                resolvedText = "0",
                status = "O_NORMALIZED_TO_0",
            ),
            normalizeLowerKillOcr("O"),
        )
    }
}
