package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotNumberCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbySemanticPositionReconcilerTest {
    @Test
    fun allPhysicalPermutationsReconcileToCanonicalSemanticOrderAndRanges() {
        listOf(
            listOf(RosterScreenshotPosition.ONE, RosterScreenshotPosition.TWO, RosterScreenshotPosition.THREE),
            listOf(RosterScreenshotPosition.ONE, RosterScreenshotPosition.THREE, RosterScreenshotPosition.TWO),
            listOf(RosterScreenshotPosition.TWO, RosterScreenshotPosition.ONE, RosterScreenshotPosition.THREE),
            listOf(RosterScreenshotPosition.TWO, RosterScreenshotPosition.THREE, RosterScreenshotPosition.ONE),
            listOf(RosterScreenshotPosition.THREE, RosterScreenshotPosition.ONE, RosterScreenshotPosition.TWO),
            listOf(RosterScreenshotPosition.THREE, RosterScreenshotPosition.TWO, RosterScreenshotPosition.ONE),
        ).forEach { physicalSemanticOrder ->
            val result = LobbySemanticPositionReconciler.reconcile(
                physicalSemanticOrder.mapIndexed { storedIndex, semanticPosition ->
                    resolved(
                        storedPosition = RosterScreenshotPosition.entries[storedIndex],
                        semanticPosition = semanticPosition,
                    )
                },
            )

            assertEquals(
                RosterScreenshotPosition.entries,
                result.screenshots.map { it.screenshotPosition },
            )
            result.screenshots.forEachIndexed { index, screenshot ->
                val processed = screenshot as MatchLobbySlotNumberOcrScreenshotResult.Processed
                val expectedPosition = RosterScreenshotPosition.entries[index]
                assertEquals(
                    expectedPosition.tournamentSlotRange.toList(),
                    processed.teamCropPreviews.authoritativeSlots(),
                )
            }
        }
    }

    @Test
    fun duplicateSemanticPositionIsAnExplicitConflictWithNoSilentOverwrite() {
        val result = LobbySemanticPositionReconciler.reconcile(
            listOf(
                resolved(RosterScreenshotPosition.ONE, RosterScreenshotPosition.TWO),
                resolved(RosterScreenshotPosition.TWO, RosterScreenshotPosition.TWO),
                resolved(RosterScreenshotPosition.THREE, RosterScreenshotPosition.ONE),
            ),
        )

        val two = result.screenshots[1]
        assertTrue(two is MatchLobbySlotNumberOcrScreenshotResult.Unavailable)
        assertEquals(
            MatchLobbySlotNumberOcrUnavailableReason.SEMANTIC_POSITION_CONFLICT,
            (two as MatchLobbySlotNumberOcrScreenshotResult.Unavailable).reason,
        )
        assertTrue(result.screenshots[0] is MatchLobbySlotNumberOcrScreenshotResult.Processed)
        assertTrue(result.screenshots[2] is MatchLobbySlotNumberOcrScreenshotResult.Unavailable)
    }

    @Test
    fun missingSemanticPositionFailsSafelyAsUnavailable() {
        val result = LobbySemanticPositionReconciler.reconcile(
            listOf(
                resolved(RosterScreenshotPosition.ONE, RosterScreenshotPosition.ONE),
                resolved(RosterScreenshotPosition.TWO, RosterScreenshotPosition.TWO),
            ),
        )

        val three = result.screenshots[2]
        assertTrue(three is MatchLobbySlotNumberOcrScreenshotResult.Unavailable)
        assertEquals(
            MatchLobbySlotNumberOcrUnavailableReason.SEMANTIC_POSITION_UNRESOLVED,
            (three as MatchLobbySlotNumberOcrScreenshotResult.Unavailable).reason,
        )
    }

    private fun resolved(
        storedPosition: RosterScreenshotPosition,
        semanticPosition: RosterScreenshotPosition,
    ) = LobbyPhysicalProcessingOutcome.Resolved(
        storedPosition = storedPosition,
        semanticPosition = semanticPosition,
        slots = RosterVisibleSlotPosition.entries.map { visiblePosition ->
            MatchLobbySlotNumberOcrSlot(
                visibleSlotPosition = visiblePosition,
                candidate = RosterSlotNumberCandidate(
                    status = RosterCandidateParseStatus.PARSED,
                    detectedSlotNumber = semanticPosition.tournamentSlotFor(visiblePosition),
                    failure = null,
                    rawSourceResults = emptyList(),
                    confidence = com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence.Unavailable,
                ),
            )
        },
        teamCropPreviews = MatchLobbyTeamCropPreviewResult.Available(
            RosterVisibleSlotPosition.entries.map { visiblePosition ->
                MatchLobbyTeamCropPreview(
                    visibleSlotPosition = visiblePosition,
                    detectedSlotNumber = semanticPosition.tournamentSlotFor(visiblePosition),
                    image = TestPreviewImage,
                    authoritativeTeamSlotNumber = semanticPosition.tournamentSlotFor(visiblePosition),
                )
            },
        ),
    )

    private fun MatchLobbyTeamCropPreviewResult.authoritativeSlots(): List<Int> =
        (this as MatchLobbyTeamCropPreviewResult.Available)
            .previews
            .map { it.authoritativeTeamSlotNumber }

    private data object TestPreviewImage : MatchLobbyTeamCropPreviewImage
}
