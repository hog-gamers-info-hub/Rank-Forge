package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultEliminationPrefixType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultNumericVerification
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionSemanticResult
import com.hoggamers.rankforge.domain.ocr.matchresult.ParsedEliminationText
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultRowOcrSemanticDiagnosticTest {
    @Test
    fun rowOneUsesSlotsOneAndThreeAndPreservesThreeXSelection() {
        val summary = MatchResultRowOcrSemanticDiagnostic.summarize(
            semantic(),
            rowIndex = 1,
            selected = MatchResultRowOcrCandidate.SCALE_3X,
        )

        assertEquals(MatchResultRowOcrCandidate.SCALE_3X, summary.selected)
        assertEquals("3", summary.first.value)
        assertEquals("EXPLICIT_NUMERIC", summary.first.source)
        assertTrue(summary.first.markerMatched)
        assertEquals("EXPLICIT_NUMERIC", summary.first.prefixType)
        assertEquals("0", summary.second.value)
        assertEquals("EMPTY_PREFIX_ZERO", summary.second.source)
        assertTrue(summary.second.markerMatched)
        assertEquals("EMPTY_PREFIX", summary.second.prefixType)
    }

    @Test
    fun rowTwoUsesSlotsTwoAndFourAndPreservesFourXSelection() {
        val summary = MatchResultRowOcrSemanticDiagnostic.summarize(
            semantic(),
            rowIndex = 2,
            selected = MatchResultRowOcrCandidate.SCALE_4X,
        )

        assertEquals(MatchResultRowOcrCandidate.SCALE_4X, summary.selected)
        assertEquals("0", summary.first.value)
        assertEquals("O_NORMALIZED", summary.first.source)
        assertEquals(MatchResultOcrFieldStatus.O_NORMALIZED_TO_0, summary.first.fieldStatus)
        assertEquals("O_NORMALIZED", summary.first.prefixType)
        assertEquals("UNRESOLVED", summary.second.value)
        assertEquals("UNRESOLVED", summary.second.source)
        assertFalse(summary.second.markerMatched)
        assertEquals("NONE", summary.second.prefixType)
    }

    @Test
    fun summaryDoesNotAlterMapperFieldsOrSlotOrder() {
        val semantic = semantic()
        val before = semantic.fields.map { it.slot to it.resolvedText }

        MatchResultRowOcrSemanticDiagnostic.summarize(semantic, 1, MatchResultRowOcrCandidate.SCALE_3X)
        MatchResultRowOcrSemanticDiagnostic.summarize(semantic, 2, MatchResultRowOcrCandidate.SCALE_4X)

        assertEquals(before, semantic.fields.map { it.slot to it.resolvedText })
        assertEquals(listOf(1, 2, 3, 4), semantic.fields.mapNotNull { it.slot })
    }

    private fun semantic() = MatchResultPositionSemanticResult(
        role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        position = 7,
        fields = listOf(
            kill(1, "3", MatchResultOcrFieldStatus.DIRECT_NUMERIC),
            kill(2, "0", MatchResultOcrFieldStatus.O_NORMALIZED_TO_0),
            kill(3, "0", MatchResultOcrFieldStatus.DIRECT_NUMERIC),
            kill(4, "", MatchResultOcrFieldStatus.EMPTY),
        ),
        row = null,
        placementVerification = MatchResultNumericVerification.Unresolved(emptyList()),
        killVerifications = emptyMap(),
        structuralIdentityValid = true,
        isAutoAcceptable = true,
        basicKillEvidence = mapOf(
            1 to parsed(MatchResultEliminationPrefixType.EXPLICIT_NUMERIC),
            2 to parsed(MatchResultEliminationPrefixType.O_NORMALIZED),
            3 to parsed(MatchResultEliminationPrefixType.EMPTY_PREFIX),
            4 to null,
        ),
    )

    private fun parsed(prefixType: MatchResultEliminationPrefixType) = ParsedEliminationText(
        kill = 0,
        playerSuffix = null,
        markerMatched = true,
        prefixType = prefixType,
    )

    private fun kill(slot: Int, resolvedText: String, status: MatchResultOcrFieldStatus) = MatchResultOcrField(
        id = "KILL_7_$slot",
        type = MatchResultOcrFieldType.KILL,
        position = 7,
        visualRow = null,
        slot = slot,
        canonicalRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
        mappedRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
        ocrText = "",
        resolvedText = resolvedText,
        status = status,
    )
}
