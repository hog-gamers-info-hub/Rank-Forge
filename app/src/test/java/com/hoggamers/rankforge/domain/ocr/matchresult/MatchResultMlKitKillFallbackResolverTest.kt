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
    fun exactEmptyKillCellAcceptsExistingAndDegradedNumericPrefixes() {
        listOf(
            "4Eliminations" to 4,
            "3Eliminat" to 3,
            "3Eminaions" to 3,
            "0Eliminaiens" to 0,
            "2Elninafens" to 2,
            "4Eians" to 4,
            "1Elminations" to 1,
            "2" to 2,
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
                observation("2", left = 530, top = 60, right = 545, bottom = 80),
                observation("3Eminaions", left = 550, top = 60, right = 590, bottom = 80),
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
                observation("0Eliminaiens", left = 530, top = 60, right = 570, bottom = 80),
                observation("2", left = 530, top = 110, right = 570, bottom = 130),
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
        evidence: MatchResultAutoCropEvidence,
    ): Map<Int, MatchResultNumericVerification> = resolver.resolve(
        positionCrop = positionCrop.copy(position = position),
        rowCrops = rows,
        currentPpSemantic = semantic(position, currentPpKills),
        evidence = evidence,
    )

    private fun semantic(
        position: Int,
        currentPpKills: Map<Int, Int>,
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
                resolvedText = "Player$slot",
                status = MatchResultOcrFieldStatus.DIRECT_TEXT,
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
        )
    }

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
