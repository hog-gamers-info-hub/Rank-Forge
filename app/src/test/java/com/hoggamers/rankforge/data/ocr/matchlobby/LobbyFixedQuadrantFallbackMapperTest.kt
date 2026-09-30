package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxCroppedRosterPanelLayout
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyFixedQuadrantFallbackMapperTest {
    @Test
    fun oddPanelDimensionsUseCompleteNonOverlappingQuadrantsAndRows() {
        val result = mapFallback(emptyList())

        assertEquals(
            listOf(
                TeamBounds(RosterVisibleSlotPosition.TOP_LEFT, 0, 0, 501, 451),
                TeamBounds(RosterVisibleSlotPosition.TOP_RIGHT, 501, 0, 1001, 451),
                TeamBounds(RosterVisibleSlotPosition.BOTTOM_LEFT, 0, 451, 501, 901),
                TeamBounds(RosterVisibleSlotPosition.BOTTOM_RIGHT, 501, 451, 1001, 901),
            ),
            result.teams.map { team ->
                TeamBounds(
                    team.visibleSlotPosition,
                    team.bounds.left.toInt(),
                    team.bounds.top.toInt(),
                    team.bounds.right.toInt(),
                    team.bounds.bottom.toInt(),
                )
            },
        )
        result.teams.forEach { team ->
            val rows = team.rowPreviews.map { it.boundsInTeamCrop }
            assertEquals(0, rows.first().top)
            assertTrue(rows.first().left >= 0)
            assertEquals((team.bounds.right - team.bounds.left).toInt(), rows.first().right)
            rows.zipWithNext().forEach { (first, second) ->
                assertEquals(first.bottom, second.top)
            }
            assertEquals((team.bounds.bottom - team.bounds.top).toInt(), rows.last().bottom)
        }
    }

    @Test
    fun oneValidAnchorInEachQuadrantResolvesItsSemanticGroupWithoutFabricatingEvidence() {
        RosterScreenshotPosition.entries.forEach { semanticPosition ->
            RosterVisibleSlotPosition.entries.forEach { visiblePosition ->
                val result = mapFallback(listOf(anchor(semanticPosition, visiblePosition)))
                assertEquals(semanticPosition, result.semanticHint)
                result.slots.forEach { slot ->
                    if (slot.visibleSlotPosition == visiblePosition) {
                        assertEquals(
                            semanticPosition.tournamentSlotFor(visiblePosition),
                            slot.candidate.detectedSlotNumber,
                        )
                    } else {
                        assertNull(slot.candidate.detectedSlotNumber)
                    }
                }
                result.teams.forEach { team ->
                    if (team.visibleSlotPosition == visiblePosition) {
                        assertEquals(
                            semanticPosition.tournamentSlotFor(visiblePosition),
                            team.detectedSlotNumber,
                        )
                    } else {
                        assertNull(team.detectedSlotNumber)
                    }
                }
            }
        }
    }

    @Test
    fun wrongQuadrantNumbersAreIgnoredAsSlotEvidence() {
        val fragments = listOf(
            fragmentFor(RosterVisibleSlotPosition.TOP_LEFT, "6"),
            fragmentFor(RosterVisibleSlotPosition.TOP_RIGHT, "1"),
            fragmentFor(RosterVisibleSlotPosition.BOTTOM_LEFT, "12"),
            fragmentFor(RosterVisibleSlotPosition.BOTTOM_RIGHT, "9"),
        )

        val result = mapFallback(fragments)

        assertNull(result.semanticHint)
        assertTrue(result.slots.all { it.candidate.detectedSlotNumber == null })
        assertTrue(result.teams.all { it.detectedSlotNumber == null })
    }

    @Test
    fun sameGroupAnchorsAreAcceptedButConflictingGroupsRemainUnresolved() {
        assertEquals(
            RosterScreenshotPosition.TWO,
            mapFallback(
                listOf(
                    anchor(RosterScreenshotPosition.TWO, RosterVisibleSlotPosition.TOP_LEFT),
                    anchor(RosterScreenshotPosition.TWO, RosterVisibleSlotPosition.TOP_RIGHT),
                ),
            ).semanticHint,
        )
        assertNull(
            mapFallback(
                listOf(
                    anchor(RosterScreenshotPosition.ONE, RosterVisibleSlotPosition.TOP_LEFT),
                    anchor(RosterScreenshotPosition.TWO, RosterVisibleSlotPosition.TOP_RIGHT),
                ),
            ).semanticHint,
        )
    }

    @Test
    fun playerFragmentsStayInsideTheirQuadrantsAndSlotTextIsExcluded() {
        val fragments = RosterVisibleSlotPosition.entries.flatMap { visiblePosition ->
            val anchor = anchor(RosterScreenshotPosition.TWO, visiblePosition)
            val row = FreeFireMaxCroppedRosterPanelLayout.definition.slots
                .single { it.visiblePosition == visiblePosition }
                .playerRowRegions
                .first()
                .rect
                .toPixelRect(PANEL_WIDTH, PANEL_HEIGHT)
            listOf(
                anchor,
                LobbyPanelPpFragment(
                    text = visiblePosition.name,
                    confidence = 0.8f,
                    boundingBox = RawOcrBoundingBox(
                        left = row.x + 8,
                        top = row.y + 2,
                        right = row.x + 70,
                        bottom = row.y + 18,
                    ),
                    readingOrderIndex = 100 + visiblePosition.offset,
                ),
            )
        }

        val result = mapFallback(fragments)

        assertEquals(RosterScreenshotPosition.TWO, result.semanticHint)
        result.teams.forEach { team ->
            val rowOne = team.rowPreviews.first()
            assertEquals(team.visibleSlotPosition.name, rowOne.playerName)
            assertEquals(team.visibleSlotPosition.name, rowOne.structuralEvidence)
        }
        assertEquals(6, result.teams.single {
            it.visibleSlotPosition == RosterVisibleSlotPosition.TOP_RIGHT
        }.detectedSlotNumber)
    }

    private fun mapFallback(
        fragments: List<LobbyPanelPpFragment>,
    ): LobbyFixedQuadrantFallbackMappingResult.Available =
        LobbyFixedQuadrantFallbackMapper.map(
            panelWidth = PANEL_WIDTH,
            panelHeight = PANEL_HEIGHT,
            fragments = fragments,
        ) as LobbyFixedQuadrantFallbackMappingResult.Available

    private fun anchor(
        semanticPosition: RosterScreenshotPosition,
        visiblePosition: RosterVisibleSlotPosition,
    ): LobbyPanelPpFragment = fragmentFor(
        visiblePosition = visiblePosition,
        text = semanticPosition.tournamentSlotFor(visiblePosition).toString(),
    )

    private fun fragmentFor(
        visiblePosition: RosterVisibleSlotPosition,
        text: String,
    ): LobbyPanelPpFragment {
        val region = FreeFireMaxCroppedRosterPanelLayout.definition.slots
            .single { it.visiblePosition == visiblePosition }
            .contentRect
            .toPixelRect(PANEL_WIDTH, PANEL_HEIGHT)
        val centerX = region.x + (region.width * 0.05).toInt().coerceAtLeast(3)
        val centerY = region.y + (region.height * 0.10).toInt().coerceAtLeast(3)
        return LobbyPanelPpFragment(
            text = text,
            confidence = 0.9f,
            boundingBox = RawOcrBoundingBox(
                left = centerX - 3,
                top = centerY - 3,
                right = centerX + 3,
                bottom = centerY + 3,
            ),
            readingOrderIndex = centerY * PANEL_WIDTH + centerX,
        )
    }

    private data class TeamBounds(
        val position: RosterVisibleSlotPosition,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    private companion object {
        const val PANEL_WIDTH = 1_001
        const val PANEL_HEIGHT = 901
    }
}
