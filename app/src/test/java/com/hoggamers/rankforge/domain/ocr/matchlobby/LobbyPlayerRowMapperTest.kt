package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.data.ocr.matchlobby.LobbyPlayerPpEvidenceMapper
import com.hoggamers.rankforge.data.ocr.matchlobby.LobbyPlayerPpOcrRecognition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerOcrTextFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LobbyPlayerRowMapperTest {
    private val bands = requireNotNull(LobbyPlayerRowBandCalculator.calculate(360.0, 180.0))

    @Test
    fun shuffledOcrEvidenceMapsToRowsByBoundingBoxY() {
        val mapping = LobbyPlayerRowMapper.map(
            rowBands = bands,
            fragments = listOf(
                fragment("row-4", 40, 280),
                fragment("row-2", 30, 100),
                fragment("row-1", 20, 20),
                fragment("row-3", 10, 200),
            ),
        )

        assertEquals(
            listOf("row-1", "row-2", "row-3", "row-4"),
            mapping.rows.map { it.structuralText },
        )
    }

    @Test
    fun missingRowDoesNotShiftLaterRows() {
        val mapping = LobbyPlayerRowMapper.map(
            rowBands = bands,
            fragments = listOf(
                fragment("row-1", 10, 20),
                fragment("row-3", 10, 200),
                fragment("row-4", 10, 280),
            ),
        )

        assertEquals("row-1", mapping.row(LobbyPlayerRow.ROW_1).structuralText)
        assertNull(mapping.row(LobbyPlayerRow.ROW_2).structuralText)
        assertEquals("row-3", mapping.row(LobbyPlayerRow.ROW_3).structuralText)
        assertEquals("row-4", mapping.row(LobbyPlayerRow.ROW_4).structuralText)
    }

    @Test
    fun multipleFragmentsSortLeftToRightAndPreserveUnionEvidence() {
        val mapping = LobbyPlayerRowMapper.map(
            rowBands = bands,
            fragments = listOf(
                fragment("ZLUX", 80, 20),
                fragment("NE.", 20, 20),
            ),
        )
        val row = mapping.row(LobbyPlayerRow.ROW_1)

        assertEquals(listOf("NE.", "ZLUX"), row.fragments.map { it.rawText })
        assertEquals("NE.ZLUX", row.structuralText)
        assertEquals(RawOcrBoundingBox(20, 20, 110, 40), row.unionBoundingBox)
    }

    @Test
    fun selectedSlotEvidenceAndSlotGutterFragmentsAreExcluded() {
        val mapping = LobbyPlayerRowMapper.map(
            rowBands = bands,
            selectedSlotBoundingBox = RawOcrBoundingBox(5, 20, 25, 30),
            slotGutterRight = 30,
            fragments = listOf(
                LobbyPlayerOcrFragment(
                    rawText = "5",
                    boundingBox = RawOcrBoundingBox(5, 20, 25, 30),
                ),
                LobbyPlayerOcrFragment(
                    rawText = "noise-slot",
                    boundingBox = RawOcrBoundingBox(2, 25, 20, 35),
                ),
                LobbyPlayerOcrFragment(
                    rawText = "flagged-slot",
                    boundingBox = RawOcrBoundingBox(50, 20, 70, 30),
                    isSlotNumberEvidence = true,
                ),
                fragment("player", 50, 20),
            ),
        )

        assertEquals(listOf("player"), mapping.row(LobbyPlayerRow.ROW_1).fragments.map { it.rawText })
    }

    @Test
    fun invalidOrMissingGeometryIsIgnored() {
        val mapping = LobbyPlayerRowMapper.map(
            rowBands = bands,
            fragments = listOf(
                LobbyPlayerOcrFragment("missing", null),
                LobbyPlayerOcrFragment("negative", RawOcrBoundingBox(10, 10, 5, 20)),
                fragment("outside", 10, 400),
            ),
        )

        assertEquals(emptyList<LobbyPlayerOcrFragment>(), mapping.row(LobbyPlayerRow.ROW_1).fragments)
        assertEquals(emptyList<LobbyPlayerOcrFragment>(), mapping.row(LobbyPlayerRow.ROW_4).fragments)
    }

    @Test
    fun exactBandBoundariesRemainDeterministic() {
        val mapping = LobbyPlayerRowMapper.map(
            rowBands = bands,
            fragments = listOf(
                fragment("upper-split", 10, 85),
                fragment("anchor", 10, 175),
                fragment("bottom", 10, 350),
            ),
        )

        assertEquals("upper-split", mapping.row(LobbyPlayerRow.ROW_2).structuralText)
        assertEquals("anchor", mapping.row(LobbyPlayerRow.ROW_3).structuralText)
        assertEquals("bottom", mapping.row(LobbyPlayerRow.ROW_4).structuralText)
    }

    @Test
    fun ppFragmentsAreThePlayerNameAuthorityAndPreserveMissingRows() {
        val pp = LobbyPlayerPpOcrRecognition(
            fragments = listOf(
                LobbyPlayerOcrTextFragment("PP_NAME_ONE", RawOcrBoundingBox(40, 20, 120, 40)),
                LobbyPlayerOcrTextFragment("PP_NAME_THREE", RawOcrBoundingBox(40, 200, 140, 220)),
                LobbyPlayerOcrTextFragment("PP_NAME_FOUR", RawOcrBoundingBox(40, 280, 140, 300)),
            ),
        )

        val mapping = LobbyPlayerRowMapper.map(bands, LobbyPlayerPpEvidenceMapper.playerFragments(pp))

        assertEquals("PP_NAME_ONE", mapping.row(LobbyPlayerRow.ROW_1).structuralText)
        assertNull(mapping.row(LobbyPlayerRow.ROW_2).structuralText)
        assertEquals("PP_NAME_THREE", mapping.row(LobbyPlayerRow.ROW_3).structuralText)
        assertEquals("PP_NAME_FOUR", mapping.row(LobbyPlayerRow.ROW_4).structuralText)
    }

    @Test
    fun ppSlotNumberIsExcludedAndItsOriginalCoordinatesArePreserved() {
        val pp = LobbyPlayerPpOcrRecognition(
            fragments = listOf(
                LobbyPlayerOcrTextFragment("4", RawOcrBoundingBox(5, 20, 25, 40)),
                LobbyPlayerOcrTextFragment("PP_NAME", RawOcrBoundingBox(40, 20, 120, 40)),
            ),
        )

        val fragments = LobbyPlayerPpEvidenceMapper.playerFragments(pp)
        val mapping = LobbyPlayerRowMapper.map(
            bands,
            fragments,
            selectedSlotBoundingBox = fragments.first().boundingBox,
            slotGutterRight = 30,
        )

        assertEquals(RawOcrBoundingBox(40, 20, 120, 40), fragments[1].boundingBox)
        assertEquals("PP_NAME", mapping.row(LobbyPlayerRow.ROW_1).structuralText)
    }

    private fun fragment(text: String, left: Int, top: Int) = LobbyPlayerOcrFragment(
        rawText = text,
        boundingBox = RawOcrBoundingBox(left, top, left + 30, top + 20),
    )
}
