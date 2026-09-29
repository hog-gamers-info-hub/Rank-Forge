package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultMlKitKillFallbackResolverTest {
    private val resolver = MatchResultMlKitKillFallbackResolver()
    private val positionCrop = MatchResultPositionCrop(
        position = 7,
        column = MatchResultPositionColumn.RIGHT,
        bounds = OcrPixelCropRect(100, 50, 600, 150),
    )
    private val twoRows = listOf(
        row(1, 0, 50),
        row(2, 50, 100),
    )

    @Test
    fun exactEmptyKillCellAcceptsDirectAnchorPrefixes() {
        listOf(
            "4 Eliminations" to 4,
            "14 Eliminations" to 14,
            "O Eliminations" to 0,
            "3Eliminati" to 3,
        ).forEach { (text, expected) ->
            val result = resolve(
                evidence = evidence(observation(text, left = 530, top = 60, right = 570, bottom = 80)),
            )
            val verification = result[3]
            assertTrue("Expected a verified fallback for '$text'.", verification is MatchResultNumericVerification.Verified)
            assertEquals(expected, (verification as MatchResultNumericVerification.Verified).value)
        }
    }

    @Test
    fun splitNumberAndMarkerTokensAreJoinedOnlyInsideOneKillCell() {
        val result = resolve(
            evidence = evidence(
                observation("O", left = 515, top = 60, right = 535, bottom = 80),
                observation("Eliminati", left = 540, top = 60, right = 590, bottom = 80),
            ),
        )

        assertEquals(0, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun missingLAnchorSupportsExistingAdjacentStandaloneKillRecovery() {
        val result = resolve(
            evidence = evidence(
                observation("3", left = 515, top = 60, right = 535, bottom = 80),
                observation("Eiminati", left = 540, top = 60, right = 590, bottom = 80),
            ),
        )

        assertEquals(3, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun ppMarkerOnlyAnchorAuthorizesStandaloneMlRecovery() {
        val result = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("3", left = 515, top = 60, right = 535, bottom = 80),
            ),
        )

        assertEquals(3, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun ppMarkerOnlyAnchorAuthorizesStandaloneDAsZero() {
        val result = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("D", left = 515, top = 60, right = 535, bottom = 80),
            ),
        )

        assertEquals(0, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun positionSixPlayerOneUsesDirectAttachedMlZeroWithPpMarkerOnlyAnchor() {
        val result = resolve(
            position = 6,
            ppAnchorBounds = mapOf(1 to RawOcrBoundingBox(300, 10, 380, 30)),
            evidence = evidence(
                observation("0Eliminations", left = 400, top = 60, right = 480, bottom = 80),
            ),
        )

        assertEquals(0, (result.getValue(1) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun ppAnchorUsesDirectAttachedMlKillsBeforeStandaloneRecovery() {
        listOf(
            "OEliminations" to 0,
            "3Eliminations" to 3,
            "12Eiminations" to 12,
            "2Eiminati" to 2,
            "DEliminations" to 0,
            "D Eliminations" to 0,
            "DEliminati" to 0,
            "DEiminations" to 0,
            "D Eiminati" to 0,
            "DEiminatiokp" to 0,
        ).forEach { (text, expected) ->
            val result = resolve(
                ppAnchorBounds = mapOf(3 to anchorBounds()),
                evidence = evidence(
                    observation(text, left = 540, top = 60, right = 590, bottom = 80),
                ),
            )

            assertEquals(expected, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
        }
    }

    @Test
    fun markerOnlyMlAnchorDoesNotBecomeAResolvedKill() {
        val result = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("Eliminations", left = 540, top = 60, right = 590, bottom = 80),
            ),
        )

        assertFalse(result.containsKey(3))
    }

    @Test
    fun multipleAttachedMlKillsRemainUnresolved() {
        val result = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("2Eliminations", left = 515, top = 60, right = 535, bottom = 80),
                observation("3Eliminations", left = 530, top = 60, right = 590, bottom = 80),
            ),
        )

        assertTrue(result[3] is MatchResultNumericVerification.Unresolved)
    }

    @Test
    fun duplicateAttachedMlKillsResolveAsOneDistinctValue() {
        val result = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("2Eliminations", left = 515, top = 60, right = 535, bottom = 80),
                observation("2Eliminati", left = 530, top = 60, right = 590, bottom = 80),
            ),
        )

        assertEquals(2, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun ppResolvedKillRemainsHighestPriorityAgainstAttachedMlEvidence() {
        val result = resolve(
            currentPpKills = mapOf(3 to 4),
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("2Eliminations", left = 540, top = 60, right = 590, bottom = 80),
            ),
        )

        assertFalse(result.containsKey(3))
    }

    @Test
    fun realisticDamagedMlAnchorTextCanRepresentDAsZero() {
        val result = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("DEiminatiokp", left = 540, top = 60, right = 590, bottom = 80),
            ),
        )

        assertEquals(0, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun arbitraryDTextIsNotInterpretedAsAnAttachedKill() {
        listOf("DPChacha", "DFW", "GOD", "DPLAYER", "Demon", "playerD", "ABCD").forEach { text ->
            val result = resolve(
                ppAnchorBounds = mapOf(3 to anchorBounds()),
                evidence = evidence(
                    observation(text, left = 540, top = 60, right = 590, bottom = 80),
                ),
            )

            assertFalse("Unexpected kill recovery for '$text'.", result.containsKey(3))
        }
    }

    @Test
    fun standaloneDRequiresAnAnchorAndLocalNeighborhood() {
        val withoutAnchor = resolve(
            evidence = evidence(
                observation("D", left = 515, top = 60, right = 535, bottom = 80),
            ),
        )
        val outsideNeighborhood = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("D", left = 500, top = 60, right = 515, bottom = 80),
            ),
        )

        assertFalse(withoutAnchor.containsKey(3))
        assertFalse(outsideNeighborhood.containsKey(3))
    }

    @Test
    fun standaloneDInWrongSlotDoesNotResolveTargetKill() {
        val result = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("D", left = 315, top = 60, right = 335, bottom = 80),
            ),
        )

        assertFalse(result.containsKey(3))
    }

    @Test
    fun distinctStandaloneDAndNumericEvidenceRemainsUnresolved() {
        val result = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("D", left = 515, top = 60, right = 535, bottom = 80),
                observation("3", left = 530, top = 60, right = 540, bottom = 80),
            ),
        )

        assertTrue(result[3] is MatchResultNumericVerification.Unresolved)
    }

    @Test
    fun equivalentStandaloneDAndOEvidenceResolvesAsZero() {
        val result = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("D", left = 515, top = 60, right = 535, bottom = 80),
                observation("O", left = 530, top = 60, right = 540, bottom = 80),
            ),
        )

        assertEquals(0, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun ppMissingLAnchorAuthorizesStandaloneONormalizedMlRecovery() {
        val result = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("O", left = 515, top = 60, right = 535, bottom = 80),
            ),
        )

        assertEquals(0, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun attachedDKillEvidenceTakesPrecedenceOverStandaloneEvidence() {
        val result = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("DEiminatiokp", left = 540, top = 60, right = 590, bottom = 80),
                observation("2", left = 515, top = 60, right = 535, bottom = 80),
            ),
        )

        assertEquals(0, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun ppAnchorRejectsRightSideValuesAndTooFarLeftValues() {
        val rightSide = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("9743", left = 550, top = 60, right = 590, bottom = 80),
            ),
        )
        val tooFarLeft = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("3", left = 500, top = 60, right = 515, bottom = 80),
            ),
        )
        val plausibleRightSide = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("7", left = 580, top = 60, right = 600, bottom = 80),
            ),
        )

        assertFalse(rightSide.containsKey(3))
        assertFalse(tooFarLeft.containsKey(3))
        assertFalse(plausibleRightSide.containsKey(3))
    }

    @Test
    fun ppAnchorAllowsCandidateCenterInsideAnchorLeftRegion() {
        val result = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("3", left = 538, top = 60, right = 546, bottom = 80),
            ),
        )

        assertEquals(3, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun ppAnchorAllowsModestBboxShiftBeyondTheOldTinyGap() {
        val result = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("14", left = 500, top = 60, right = 530, bottom = 80),
            ),
        )

        assertEquals(14, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun multipleMlCandidatesAdjacentToOnePpAnchorRemainUnresolved() {
        val result = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("2", left = 515, top = 60, right = 535, bottom = 80),
                observation("3", left = 530, top = 60, right = 540, bottom = 80),
            ),
        )

        assertTrue(result[3] is MatchResultNumericVerification.Unresolved)
    }

    @Test
    fun ppAnchorRecoveryKeepsPositionAndSlotOwnership() {
        val earlyPosition = resolve(
            position = 5,
            ppAnchorBounds = mapOf(1 to RawOcrBoundingBox(240, 10, 280, 30)),
            evidence = evidence(
                observation("3", left = 315, top = 60, right = 335, bottom = 80),
            ),
        )
        val leftPlayer = resolve(
            ppAnchorBounds = mapOf(1 to RawOcrBoundingBox(240, 10, 280, 30)),
            evidence = evidence(
                observation("3", left = 315, top = 60, right = 335, bottom = 80),
            ),
        )
        val wrongSlot = resolve(
            ppAnchorBounds = mapOf(3 to anchorBounds()),
            evidence = evidence(
                observation("3", left = 315, top = 60, right = 335, bottom = 80),
            ),
        )

        assertEquals(3, (earlyPosition.getValue(1) as MatchResultNumericVerification.Verified).value)
        assertEquals(3, (leftPlayer.getValue(1) as MatchResultNumericVerification.Verified).value)
        assertTrue(wrongSlot.isEmpty())
    }

    @Test
    fun adjacentStandaloneNumericValuesResolveImmediatelyBeforeAnchor() {
        listOf("3" to 3, "14" to 14, "12" to 12).forEach { (text, expected) ->
            val result = resolve(
                evidence = evidence(
                    observation(text, left = 515, top = 60, right = 535, bottom = 80),
                    observation("Eliminations", left = 540, top = 60, right = 590, bottom = 80),
                ),
            )

            assertEquals(expected, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
        }
    }

    @Test
    fun overlappingStandaloneNumericBoxRemainsValidWhenCenterIsLeftOfAnchor() {
        val result = resolve(
            evidence = evidence(
                observation("3", left = 520, top = 60, right = 542, bottom = 80),
                observation("Eliminations", left = 540, top = 60, right = 590, bottom = 80),
            ),
        )

        assertEquals(3, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun sameEngineAnchorAllowsModestBboxShiftBeyondTheOldTinyGap() {
        val result = resolve(
            evidence = evidence(
                observation("3", left = 500, top = 60, right = 530, bottom = 80),
                observation("Eliminations", left = 540, top = 60, right = 590, bottom = 80),
            ),
        )

        assertEquals(3, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun standaloneNumberBeyondAdaptiveLeftGapRemainsUnresolved() {
        val result = resolve(
            evidence = evidence(
                observation("3", left = 490, top = 60, right = 520, bottom = 80),
                observation("Eliminations", left = 540, top = 60, right = 590, bottom = 80),
            ),
        )

        assertFalse(result.containsKey(3))
    }

    @Test
    fun multipleAdjacentStandaloneValuesRemainUnresolved() {
        val result = resolve(
            evidence = evidence(
                observation("2", left = 310, top = 60, right = 333, bottom = 80),
                observation("3", left = 325, top = 60, right = 336, bottom = 80),
                observation("Eliminations", left = 340, top = 60, right = 390, bottom = 80),
            ),
        )

        assertTrue(result[1] is MatchResultNumericVerification.Unresolved)
    }

    @Test
    fun sharedMiddleAnchorUsesOnlyPrecedingNumericEvidence() {
        val result = resolve(
            evidence = evidence(
                observation("O", left = 315, top = 60, right = 334, bottom = 80),
                observation("Eliminatohs", left = 340, top = 60, right = 390, bottom = 80),
                observation("SASUKE", left = 395, top = 60, right = 440, bottom = 80),
                observation("7?", left = 450, top = 60, right = 480, bottom = 80),
            ),
        )

        assertEquals(0, (result.getValue(1) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun sharedMiddleAnchorUsesExplicitPrefixAndIgnoresPlayerSuffixNumbers() {
        val result = resolve(
            evidence = evidence(
                observation("3 Eliminat6hs PLAYER99", left = 315, top = 60, right = 480, bottom = 80),
            ),
        )

        assertEquals(3, (result.getValue(1) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun numberToRightOfAnchorCannotBecomePreviousPlayersKill() {
        val result = resolve(
            evidence = evidence(
                observation("Eliminations", left = 340, top = 60, right = 390, bottom = 80),
                observation("9743", left = 395, top = 60, right = 450, bottom = 80),
            ),
        )

        assertFalse(result.containsKey(1))
    }

    @Test
    fun realisticSmallUsernameSuffixToRightOfAnchorCannotBecomeKill() {
        val result = resolve(
            evidence = evidence(
                observation("Eliminations", left = 340, top = 60, right = 390, bottom = 80),
                observation("player_4", left = 395, top = 60, right = 450, bottom = 80),
                observation("4", left = 455, top = 60, right = 470, bottom = 80),
            ),
        )

        assertFalse(result.containsKey(1))
    }

    @Test
    fun sharedMiddleAnchorWithoutPrefixDoesNotAcceptLaterNumber() {
        val result = resolve(
            evidence = evidence(
                observation("EliminatYs", left = 340, top = 60, right = 390, bottom = 80),
                observation("SASUKE", left = 395, top = 60, right = 440, bottom = 80),
                observation("7?", left = 450, top = 60, right = 480, bottom = 80),
            ),
        )

        assertFalse(result.containsKey(1))
    }

    @Test
    fun sharedMiddleMarkerOnlyDoesNotInferZero() {
        val result = resolve(
            evidence = evidence(
                observation("Eliminatohs", left = 340, top = 60, right = 390, bottom = 80),
            ),
        )

        assertFalse(result.containsKey(1))
    }

    @Test
    fun sharedMiddleCombinedNumericAndNormalizedPrefixesResolve() {
        val numeric = resolve(
            evidence = evidence(
                observation("0EliminatTYsSASUKE", left = 315, top = 60, right = 480, bottom = 80),
            ),
        )
        val normalized = resolve(
            evidence = evidence(
                observation("O Eliminatohs PLAYER", left = 315, top = 60, right = 480, bottom = 80),
            ),
        )

        assertEquals(0, (numeric.getValue(1) as MatchResultNumericVerification.Verified).value)
        assertEquals(0, (normalized.getValue(1) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun missingPlayerNameDoesNotPreventAdjacentKillRecovery() {
        val result = resolve(
            playerNamesResolved = false,
            evidence = evidence(
                observation("3", left = 515, top = 60, right = 535, bottom = 80),
                observation("Eliminations", left = 540, top = 60, right = 590, bottom = 80),
            ),
        )

        assertEquals(3, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun earlyPositionUsesTheSameAnchorRelativeRule() {
        val result = resolve(
            position = 5,
            evidence = evidence(
                observation("3", left = 300, top = 60, right = 320, bottom = 80),
                observation("Eliminations", left = 325, top = 60, right = 375, bottom = 80),
            ),
        )

        assertEquals(3, (result.getValue(1) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun rightPlayerColumnUsesTheSameAnchorRelativeRule() {
        val result = resolve(
            evidence = evidence(
                observation("3", left = 515, top = 60, right = 535, bottom = 80),
                observation("Eliminations", left = 540, top = 60, right = 590, bottom = 80),
            ),
        )

        assertEquals(3, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun noAnchorStandaloneNumericFallbackFailsClosedForEarlyPositions() {
        val result = resolve(
            position = 5,
            evidence = evidence(
                observation("2", left = 300, top = 60, right = 320, bottom = 80),
            ),
        )

        assertFalse(result.containsKey(1))
    }

    @Test
    fun wrongGeometryDoesNotRecoverTheTargetKill() {
        val result = resolve(
            evidence = evidence(
                observation("4Eminaions", left = 450, top = 60, right = 490, bottom = 80),
            ),
        )

        assertFalse(result.containsKey(3))
    }

    @Test
    fun wrongRowDoesNotRecoverWhenThatRowIsNotAvailableForTheTarget() {
        val result = resolve(
            rows = listOf(row(1, 0, 50)),
            evidence = evidence(
                observation("4Eminaions", left = 530, top = 110, right = 570, bottom = 130),
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun conflictingValuesRemainUnresolved() {
        val result = resolve(
            evidence = evidence(
                observation("2", left = 516, top = 60, right = 541, bottom = 80),
                observation("3", left = 530, top = 60, right = 546, bottom = 80),
                observation("Eliminations", left = 550, top = 60, right = 590, bottom = 80),
            ),
        )

        assertTrue(result[3] is MatchResultNumericVerification.Unresolved)
    }

    @Test
    fun ppResolvedKillIsNotTouchedByConflictingMlKitEvidence() {
        val result = resolve(
            currentPpKills = mapOf(3 to 3),
            evidence = evidence(
                observation("5Eminaions", left = 530, top = 60, right = 570, bottom = 80),
            ),
        )

        assertFalse(result.containsKey(3))
    }

    @Test
    fun multipleEmptyKillFieldsResolveIndependentlyByTheirExactCells() {
        val result = resolve(
            evidence = evidence(
                observation("1", left = 350, top = 60, right = 380, bottom = 80),
                observation("Eliminat", left = 386, top = 60, right = 430, bottom = 80),
                observation("0Eliminat", left = 530, top = 60, right = 570, bottom = 80),
                observation("2", left = 530, top = 110, right = 544, bottom = 130),
                observation("Eliminat", left = 550, top = 110, right = 590, bottom = 130),
            ),
        )

        assertEquals(1, (result.getValue(1) as MatchResultNumericVerification.Verified).value)
        assertEquals(0, (result.getValue(3) as MatchResultNumericVerification.Verified).value)
        assertEquals(2, (result.getValue(4) as MatchResultNumericVerification.Verified).value)
    }

    @Test
    fun missingEvidenceAndP11BottomRightRemainEmpty() {
        val noEvidence = resolve(evidence = evidence())
        val nonNumeric = resolve(
            position = 11,
            evidence = evidence(
                observation("PLAYER", left = 530, top = 110, right = 590, bottom = 130),
            ),
        )

        assertTrue(noEvidence.isEmpty())
        assertTrue(nonNumeric.isEmpty())
    }

    @Test
    fun allPpKillsResolvedMeansMlKitFallbackHasNoEffect() {
        val result = resolve(
            currentPpKills = mapOf(1 to 1, 2 to 0, 3 to 3, 4 to 4),
            evidence = evidence(
                observation("9Eminaions", left = 350, top = 60, right = 380, bottom = 80),
                observation("8Eminaions", left = 350, top = 110, right = 380, bottom = 130),
                observation("7Eminaions", left = 530, top = 60, right = 570, bottom = 80),
                observation("6Eminaions", left = 530, top = 110, right = 570, bottom = 130),
            ),
        )

        assertTrue(result.isEmpty())
    }

    private fun resolve(
        position: Int = 7,
        rows: List<MatchResultPositionRowCrop> = twoRows,
        currentPpKills: Map<Int, Int> = emptyMap(),
        ppAnchorBounds: Map<Int, RawOcrBoundingBox> = emptyMap(),
        playerNamesResolved: Boolean = true,
        evidence: MatchResultAutoCropEvidence,
    ): Map<Int, MatchResultNumericVerification> = resolver.resolve(
        positionCrop = positionCrop.copy(position = position),
        rowCrops = rows,
        currentPpSemantic = semantic(position, currentPpKills, ppAnchorBounds, playerNamesResolved),
        evidence = evidence,
    )

    private fun semantic(
        position: Int,
        currentPpKills: Map<Int, Int>,
        ppAnchorBounds: Map<Int, RawOcrBoundingBox>,
        playerNamesResolved: Boolean,
    ): MatchResultPositionSemanticResult {
        val placement = field(
            id = "PLACEMENT_$position",
            type = MatchResultOcrFieldType.PLACEMENT,
            position = position,
            slot = null,
            resolvedText = position.toString(),
            status = MatchResultOcrFieldStatus.TEMPLATE_ONLY,
        )
        val slots = (1..4).map { slot ->
            val player = field(
                id = "PLAYER_${position}_$slot",
                type = MatchResultOcrFieldType.PLAYER,
                position = position,
                slot = slot,
                resolvedText = if (playerNamesResolved) "Player$slot" else "",
                status = if (playerNamesResolved) {
                    MatchResultOcrFieldStatus.DIRECT_TEXT
                } else {
                    MatchResultOcrFieldStatus.EMPTY
                },
            )
            val ppKill = currentPpKills[slot]
            val kill = field(
                id = "KILL_${position}_$slot",
                type = MatchResultOcrFieldType.KILL,
                position = position,
                slot = slot,
                resolvedText = ppKill?.toString().orEmpty(),
                status = if (ppKill == null) MatchResultOcrFieldStatus.EMPTY else MatchResultOcrFieldStatus.DIRECT_NUMERIC,
            )
            MatchResultOcrPlayerSlot(slot, player, kill)
        }
        return MatchResultPositionSemanticResult(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            position = position,
            fields = listOf(placement) + slots.flatMap { listOf(it.player, it.kill) },
            row = MatchResultOcrRow(
                position = position,
                source = MatchResultOcrRowSource.UPPER_TEMPLATE,
                placement = placement,
                playerSlots = slots,
            ),
            placementVerification = MatchResultNumericVerification.Unresolved(emptyList()),
            killVerifications = emptyMap(),
            structuralIdentityValid = true,
            isAutoAcceptable = false,
            eliminationAnchorBounds = ppAnchorBounds,
        )
    }

    private fun anchorBounds() = RawOcrBoundingBox(440, 10, 490, 30)

    private fun field(
        id: String,
        type: MatchResultOcrFieldType,
        position: Int,
        slot: Int?,
        resolvedText: String,
        status: MatchResultOcrFieldStatus,
    ) = MatchResultOcrField(
        id = id,
        type = type,
        position = position,
        visualRow = null,
        slot = slot,
        canonicalRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
        mappedRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
        ocrText = resolvedText,
        resolvedText = resolvedText,
        status = status,
    )

    private fun row(index: Int, top: Int, bottom: Int) = MatchResultPositionRowCrop(
        rowIndex = index,
        bounds = OcrPixelCropRect(0, top, 500, bottom),
    )

    private fun evidence(vararg observations: MatchResultAutoCropObservation) = MatchResultAutoCropEvidence(
        observations = observations.toList(),
        imageDimensions = OcrImageDimensions(width = 1000, height = 300),
    )

    private fun observation(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        MatchResultAutoCropObservation(
            text = text,
            boundingBox = RawOcrBoundingBox(left, top, right, bottom),
        )
}
