package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropBounds
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

    @Test
    fun uniqueFallbackHintsAndEliminationFillOnlyUnresolvedSemanticGroups() {
        val result = LobbySemanticPositionReconciler.reconcile(
            listOf(
                resolved(RosterScreenshotPosition.ONE, RosterScreenshotPosition.ONE),
                fallback(RosterScreenshotPosition.TWO, RosterScreenshotPosition.TWO),
                fallback(RosterScreenshotPosition.THREE, null),
            ),
        )

        assertEquals(
            LobbySemanticResolutionSource.CURRENT_ANCHOR_GRID,
            (result.screenshots[0] as MatchLobbySlotNumberOcrScreenshotResult.Processed).resolutionSource,
        )
        assertEquals(
            LobbySemanticResolutionSource.FIXED_QUADRANT_ANCHOR,
            (result.screenshots[1] as MatchLobbySlotNumberOcrScreenshotResult.Processed).resolutionSource,
        )
        assertEquals(
            LobbySemanticResolutionSource.ELIMINATION,
            (result.screenshots[2] as MatchLobbySlotNumberOcrScreenshotResult.Processed).resolutionSource,
        )
    }

    @Test
    fun duplicateFallbackHintIsDemotedAndCanOnlyBeResolvedByGlobalElimination() {
        val result = LobbySemanticPositionReconciler.reconcile(
            listOf(
                resolved(RosterScreenshotPosition.ONE, RosterScreenshotPosition.ONE),
                fallback(RosterScreenshotPosition.TWO, RosterScreenshotPosition.ONE),
                resolved(RosterScreenshotPosition.THREE, RosterScreenshotPosition.THREE),
            ),
        )

        assertTrue(result.screenshots[0] is MatchLobbySlotNumberOcrScreenshotResult.Processed)
        assertEquals(
            LobbySemanticResolutionSource.ELIMINATION,
            (result.screenshots[1] as MatchLobbySlotNumberOcrScreenshotResult.Processed).resolutionSource,
        )
        assertTrue(result.screenshots[2] is MatchLobbySlotNumberOcrScreenshotResult.Processed)
    }

    @Test
    fun threeUnresolvedFallbackPanelsUseStoredPreviewSequence() {
        val result = LobbySemanticPositionReconciler.reconcile(
            RosterScreenshotPosition.entries.map { storedPosition ->
                fallback(storedPosition, null)
            },
        )

        assertEquals(
            listOf(
                LobbySemanticResolutionSource.PREVIEW_SEQUENCE,
                LobbySemanticResolutionSource.PREVIEW_SEQUENCE,
                LobbySemanticResolutionSource.PREVIEW_SEQUENCE,
            ),
            result.screenshots.map {
                (it as MatchLobbySlotNumberOcrScreenshotResult.Processed).resolutionSource
            },
        )
        assertEquals(
            RosterScreenshotPosition.entries.flatMap { it.tournamentSlotRange.toList() },
            result.screenshots.flatMap { screenshot ->
                (screenshot as MatchLobbySlotNumberOcrScreenshotResult.Processed)
                    .teamCropPreviews
                    .authoritativeSlots()
            },
        )
    }

    @Test
    fun sequenceFallbackPairsUnresolvedStoredOrderWithRemainingSemanticOrder() {
        val result = LobbySemanticPositionReconciler.reconcile(
            listOf(
                fallback(RosterScreenshotPosition.ONE, null),
                resolved(RosterScreenshotPosition.TWO, RosterScreenshotPosition.THREE),
                fallback(RosterScreenshotPosition.THREE, null),
            ),
        )

        assertEquals(
            LobbySemanticResolutionSource.PREVIEW_SEQUENCE,
            (result.screenshots[0] as MatchLobbySlotNumberOcrScreenshotResult.Processed).resolutionSource,
        )
        assertEquals(
            LobbySemanticResolutionSource.PREVIEW_SEQUENCE,
            (result.screenshots[1] as MatchLobbySlotNumberOcrScreenshotResult.Processed).resolutionSource,
        )
        assertTrue(result.screenshots[2] is MatchLobbySlotNumberOcrScreenshotResult.Processed)
        assertEquals(
            RosterScreenshotPosition.THREE.tournamentSlotRange.toList(),
            (result.screenshots[2] as MatchLobbySlotNumberOcrScreenshotResult.Processed)
                .teamCropPreviews
                .authoritativeSlots(),
        )
    }

    @Test
    fun fewerThanThreePanelsNeverAssignsAnUnresolvedFallbackBySequence() {
        val result = LobbySemanticPositionReconciler.reconcile(
            listOf(
                resolved(RosterScreenshotPosition.ONE, RosterScreenshotPosition.ONE),
                fallback(RosterScreenshotPosition.TWO, null),
                LobbyPhysicalProcessingOutcome.Unavailable(
                    storedPosition = RosterScreenshotPosition.THREE,
                    reason = MatchLobbySlotNumberOcrUnavailableReason.ASSET_UNAVAILABLE,
                ),
            ),
        )

        assertTrue(result.screenshots[0] is MatchLobbySlotNumberOcrScreenshotResult.Processed)
        assertTrue(result.screenshots[1] is MatchLobbySlotNumberOcrScreenshotResult.Unavailable)
    }

    @Test
    fun twoIntrinsicallyIdentifiedFallbackPanelsCanContributeIndependently() {
        val result = LobbySemanticPositionReconciler.reconcile(
            listOf(
                fallback(RosterScreenshotPosition.ONE, RosterScreenshotPosition.ONE),
                fallback(RosterScreenshotPosition.TWO, RosterScreenshotPosition.THREE),
                LobbyPhysicalProcessingOutcome.Unavailable(
                    storedPosition = RosterScreenshotPosition.THREE,
                    reason = MatchLobbySlotNumberOcrUnavailableReason.ASSET_UNAVAILABLE,
                ),
            ),
        )

        assertTrue(result.screenshots[0] is MatchLobbySlotNumberOcrScreenshotResult.Processed)
        assertTrue(result.screenshots[1] is MatchLobbySlotNumberOcrScreenshotResult.Unavailable)
        assertTrue(result.screenshots[2] is MatchLobbySlotNumberOcrScreenshotResult.Processed)
    }

    @Test
    fun duplicateFallbackHintsRemainUnavailableWhenSequenceIsNotAllowed() {
        val result = LobbySemanticPositionReconciler.reconcile(
            listOf(
                fallback(RosterScreenshotPosition.ONE, RosterScreenshotPosition.ONE),
                fallback(RosterScreenshotPosition.TWO, RosterScreenshotPosition.ONE),
                LobbyPhysicalProcessingOutcome.Unavailable(
                    storedPosition = RosterScreenshotPosition.THREE,
                    reason = MatchLobbySlotNumberOcrUnavailableReason.ASSET_UNAVAILABLE,
                ),
            ),
        )

        assertTrue(result.screenshots.all { it is MatchLobbySlotNumberOcrScreenshotResult.Unavailable })
    }

    @Test
    fun fallbackPreviewCarriesOnlyActualRawSlotEvidence() {
        val result = LobbySemanticPositionReconciler.reconcile(
            listOf(fallback(RosterScreenshotPosition.ONE, RosterScreenshotPosition.TWO)),
        )
        val processed = result.screenshots[1] as MatchLobbySlotNumberOcrScreenshotResult.Processed

        assertEquals(listOf(null, 6, null, null), processed.slots.map { it.candidate.detectedSlotNumber })
        assertEquals(listOf(5, 6, 7, 8), processed.teamCropPreviews.authoritativeSlots())
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

    private fun fallback(
        storedPosition: RosterScreenshotPosition,
        semanticHint: RosterScreenshotPosition?,
    ) = LobbyPhysicalProcessingOutcome.FallbackCandidate(
        storedPosition = storedPosition,
        slots = RosterVisibleSlotPosition.entries.map { visiblePosition ->
            MatchLobbySlotNumberOcrSlot(
                visibleSlotPosition = visiblePosition,
                candidate = if (semanticHint == RosterScreenshotPosition.TWO &&
                    visiblePosition == RosterVisibleSlotPosition.TOP_RIGHT
                ) {
                    parsedCandidate(6)
                } else {
                    RosterSlotNumberCandidate.unavailable()
                },
            )
        },
        semanticHint = semanticHint,
        previews = RosterVisibleSlotPosition.entries.map { visiblePosition ->
            LobbyFixedQuadrantFallbackPreview(
                visibleSlotPosition = visiblePosition,
                detectedSlotNumber = if (semanticHint == RosterScreenshotPosition.TWO &&
                    visiblePosition == RosterVisibleSlotPosition.TOP_RIGHT
                ) {
                    6
                } else {
                    null
                },
                image = TestPreviewImage,
                playerRowPreviews = emptyList(),
                bounds = LobbyTeamCropBounds(0.0, 0.0, 1.0, 1.0),
            )
        },
        unavailable = emptyList(),
    )

    private fun parsedCandidate(slotNumber: Int) = RosterSlotNumberCandidate(
        status = RosterCandidateParseStatus.PARSED,
        detectedSlotNumber = slotNumber,
        failure = null,
        rawSourceResults = emptyList(),
        confidence = com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence.Unavailable,
    )

    private fun MatchLobbyTeamCropPreviewResult.authoritativeSlots(): List<Int> =
        (this as MatchLobbyTeamCropPreviewResult.Available)
            .previews
            .map { it.authoritativeTeamSlotNumber }

    private data object TestPreviewImage : MatchLobbyTeamCropPreviewImage
}
