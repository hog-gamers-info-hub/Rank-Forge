package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultPositionOcrFieldMapperTest {
    private val mapper = MatchResultPositionOcrFieldMapper()

    @Test
    fun eliminationParserReturnsPrefixAndMergedSuffix() {
        val parsed = MatchResultPositionSemanticTextParser.parse("3EliminationABBA LIVE")
        assertEquals(3, parsed.kill)
        assertEquals("ABBA LIVE", parsed.playerSuffix)
        assertTrue(parsed.markerMatched)
        assertEquals(MatchResultEliminationPrefixType.EXPLICIT_NUMERIC, parsed.prefixType)
    }

    @Test
    fun eliminationParserNormalizesOAndMissingPrefixToZero() {
        assertEquals(0, MatchResultPositionSemanticTextParser.parse("O EliminatiokTS ASH!SH!!").kill)
        assertEquals("kTS ASH!SH!!", MatchResultPositionSemanticTextParser.parse("O EliminatiokTS ASH!SH!!").playerSuffix)
        assertEquals(0, MatchResultPositionSemanticTextParser.parse("EliminationUGZ×SN!PEY").kill)
        assertEquals(MatchResultEliminationPrefixType.EMPTY_PREFIX,
            MatchResultPositionSemanticTextParser.parse("EliminationUGZ×SN!PEY").prefixType)
    }

    @Test
    fun arbitraryTextWithoutMarkerIsUnresolved() {
        val parsed = MatchResultPositionSemanticTextParser.parse("PLAYER7")
        assertTrue(!parsed.markerMatched)
        assertEquals(null, parsed.kill)
        assertEquals(null, parsed.playerSuffix)
    }

    @Test
    fun decorativeEarlyPlacementUsesAuthoritativePhaseOnePosition() {
        (1..3).forEach { position ->
            val result = mapper.map(input(position = position, role = MatchResultScreenshotRole.MATCH_RESULT_UPPER))
            assertEquals(position.toString(), result.fields.first { it.type == MatchResultOcrFieldType.PLACEMENT }.resolvedText)
            assertEquals(MatchResultOcrFieldStatus.TEMPLATE_ONLY,
                result.fields.first { it.type == MatchResultOcrFieldType.PLACEMENT }.status)
        }
    }

    @Test
    fun missingPlacementOcrNeverRemovesAuthoritativePosition() {
        (1..4).forEach { position ->
            val result = mapper.map(input(position = position, role = MatchResultScreenshotRole.MATCH_RESULT_UPPER))
            val placement = result.fields.single { it.type == MatchResultOcrFieldType.PLACEMENT }
            assertEquals(position.toString(), placement.resolvedText)
            assertEquals(MatchResultOcrFieldStatus.TEMPLATE_ONLY, placement.status)
        }
    }

    @Test
    fun wrongPlacementOcrDoesNotOverwriteOrReorderPosition() {
        val result = mapper.map(
            input(position = 6, role = MatchResultScreenshotRole.MATCH_RESULT_UPPER).copy(
                placementVerification = verified(3),
            ),
        )
        assertEquals(6, result.position)
        assertEquals("6", result.fields.single { it.type == MatchResultOcrFieldType.PLACEMENT }.resolvedText)
        assertEquals(MatchResultOcrFieldStatus.TEMPLATE_ONLY,
            result.fields.single { it.type == MatchResultOcrFieldType.PLACEMENT }.status)
    }

    @Test
    fun lowerPlacementIdentityRemainsElevenAndTwelveWithoutPpNumber() {
        (11..12).forEach { position ->
            val result = mapper.map(input(position = position, role = MatchResultScreenshotRole.MATCH_RESULT_LOWER))
            assertEquals(position.toString(), result.fields.single { it.type == MatchResultOcrFieldType.PLACEMENT }.resolvedText)
        }
    }

    @Test
    fun lowerOwnsPositionsElevenAndTwelveAndUpperFallbackIsOptional() {
        val lower = mapper.mapBatch(
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            listOf(input(11, MatchResultScreenshotRole.MATCH_RESULT_LOWER), input(12, MatchResultScreenshotRole.MATCH_RESULT_LOWER)),
        )
        assertTrue(lower.sequenceValidation.isValid)
        val upper = mapper.mapBatch(
            MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            (1..11).map { input(it, MatchResultScreenshotRole.MATCH_RESULT_UPPER) },
            allowUpperPositionElevenFallback = true,
        )
        assertTrue(upper.sequenceValidation.isValid)
        val upperWithoutFallback = mapper.mapBatch(
            MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            (1..10).map { input(it, MatchResultScreenshotRole.MATCH_RESULT_UPPER) },
        )
        assertTrue(upperWithoutFallback.sequenceValidation.isValid)
    }

    @Test
    fun mergedMiddleAndRightTextMapKillsToSlotsByRow() {
        val result = mapper.map(
            MatchResultPositionOcrInput(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                position = 7,
                cropWidth = 491,
                cropHeight = 82,
                blocks = block(
                    line("A", 20, 10, 120, 30),
                    line("2EliminationB", 205, 10, 350, 30),
                    line("8 Eliminati", 410, 10, 480, 30),
                    line("C", 20, 45, 120, 70),
                    line("7EliminationD", 205, 45, 350, 70),
                    line("8 Eliminati", 410, 45, 480, 70),
                ),
                rowCrops = listOf(row(1, 0, 41), row(2, 41, 82)),
                placementVerification = unresolved(),
                killVerifications = emptyMap(),
            ),
        )
        assertEquals("2", result.fields.single { it.id == "KILL_7_1" }.resolvedText)
        assertEquals("7", result.fields.single { it.id == "KILL_7_2" }.resolvedText)
        assertEquals("8", result.fields.single { it.id == "KILL_7_3" }.resolvedText)
        assertEquals("8", result.fields.single { it.id == "KILL_7_4" }.resolvedText)
        assertEquals("B", result.fields.single { it.id == "PLAYER_7_3" }.resolvedText)
        assertEquals("D", result.fields.single { it.id == "PLAYER_7_4" }.resolvedText)
    }

    @Test
    fun rightLayoutMergedMiddleSuffixesAreParsedConservatively() {
        assertEquals("ABBA LIVE", MatchResultPositionSemanticTextParser.suffixAfterElimination("3EliminationABBA LIVE"))
        assertEquals("EB-ALPHA.18", MatchResultPositionSemanticTextParser.suffixAfterElimination("1EliminationEB-ALPHA.18"))
        assertEquals("30CRIMINAL", MatchResultPositionSemanticTextParser.suffixAfterElimination("3Eliminatio30CRIMINAL"))
        assertEquals("RB.BAZZIGAR", MatchResultPositionSemanticTextParser.suffixAfterElimination("EliminatioRB.BAZZIGAR"))
        assertEquals("UGZ×SN!PEY", MatchResultPositionSemanticTextParser.suffixAfterElimination("EliminationUGZ×SN!PEY"))
        assertEquals("rsenyeager", MatchResultPositionSemanticTextParser.suffixAfterElimination("4Eliminatiorsenyeager"))
    }

    @Test
    fun basicEliminationTextOverridesUnresolvedFocusedVerificationAndAssemblerMapsSlots() {
        val fields = mapper.map(
            MatchResultPositionOcrInput(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                position = 7,
                cropWidth = 491,
                cropHeight = 82,
                blocks = block(
                    line("A", 70, 10, 160, 30),
                    line("3EliminationC", 202, 10, 397, 30),
                    line("B", 70, 45, 160, 70),
                    line("EliminationD", 202, 45, 397, 70),
                ),
                rowCrops = listOf(
                    row(1, 0, 41),
                    row(2, 41, 82),
                ),
                placementVerification = verified(7),
                killVerifications = mapOf(
                    1 to unresolved(),
                    2 to verified(2),
                    3 to verified(3),
                    4 to verified(4),
                ),
            ),
        )

        assertEquals(listOf(1, 2, 3, 4), fields.row!!.playerSlots.map { it.slot })
        assertEquals("3", fields.fields.single { it.id == "KILL_7_1" }.resolvedText)
        assertEquals(MatchResultOcrFieldStatus.DIRECT_NUMERIC, fields.fields.single { it.id == "KILL_7_1" }.status)
        assertEquals("A", fields.row.playerSlots.first { it.slot == 1 }.player.resolvedText)
        assertEquals("D", fields.row.playerSlots.first { it.slot == 4 }.player.resolvedText)
        assertTrue(!fields.isAutoAcceptable)
    }

    @Test
    fun recognizedPlayerWithoutKillKeepsItsSlotAndBlankKill() {
        val result = mapper.map(
            rightInput(
                position = 10,
                middle = "PlayerB",
                right = "",
            ),
        )

        val slot = result.row!!.playerSlots.single { it.slot == 3 }
        assertEquals("PlayerB", slot.player.resolvedText)
        assertEquals("", slot.kill.resolvedText)
        assertEquals("", slot.kill.ocrText)
        assertTrue(!result.isAutoAcceptable)
    }

    @Test
    fun upperSinglePhysicalRowMapsOnlySlotsOneAndThree() {
        val result = mapper.map(singleRowInput(rowIndex = 1))

        assertEquals(listOf(1, 3), result.row!!.playerSlots.map { it.slot })
        assertEquals("PlayerA", result.fields.single { it.id == "PLAYER_11_1" }.resolvedText)
        assertEquals("PlayerC", result.fields.single { it.id == "PLAYER_11_3" }.resolvedText)
        assertEquals("", result.fields.single { it.id == "PLAYER_11_2" }.resolvedText)
        assertEquals("", result.fields.single { it.id == "PLAYER_11_4" }.resolvedText)
    }

    @Test
    fun lowerSinglePhysicalRowMapsOnlySlotsTwoAndFour() {
        val result = mapper.map(singleRowInput(rowIndex = 2))

        assertEquals(listOf(2, 4), result.row!!.playerSlots.map { it.slot })
        assertEquals("", result.fields.single { it.id == "PLAYER_11_1" }.resolvedText)
        assertEquals("PlayerA", result.fields.single { it.id == "PLAYER_11_2" }.resolvedText)
        assertEquals("", result.fields.single { it.id == "PLAYER_11_3" }.resolvedText)
        assertEquals("PlayerC", result.fields.single { it.id == "PLAYER_11_4" }.resolvedText)
    }

    @Test
    fun centeredPositionFiveClassifierOutputUsesExistingRowOneHorizontalSlots() {
        val classification = MatchResultPositionLogicalRowClassifier().classify(
            position = 5,
            cropWidth = 491,
            cropHeight = 95,
            slotCenterYLocal = 48.0,
            blocks = block(
                line("MG SHYAMLIVE", 40, 41, 150, 52),
                line("0 Eliminations", 170, 41, 270, 52),
                line("MG BAAZIGAR", 300, 44, 400, 54),
                line("1 Eliminations", 410, 44, 480, 55),
            ),
        ) as MatchResultPositionLogicalRowClassification.Available

        assertEquals(MatchResultPositionLogicalRowClassificationKind.CENTERED_SINGLE_ROW, classification.diagnostics.classification)
        assertEquals(listOf(1), classification.rowCrops.map { it.rowIndex })

        val result = mapper.map(
            MatchResultPositionOcrInput(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                position = 5,
                cropWidth = 491,
                cropHeight = 95,
                blocks = classification.blocks,
                rowCrops = classification.rowCrops,
                placementVerification = unresolved(),
                killVerifications = emptyMap(),
            ),
        )

        assertEquals(listOf(1, 3), result.row!!.playerSlots.map { it.slot })
        assertEquals("MG SHYAMLIVE", result.fields.single { it.id == "PLAYER_5_1" }.resolvedText)
        assertEquals("0", result.fields.single { it.id == "KILL_5_1" }.resolvedText)
        assertEquals("MG BAAZIGAR", result.fields.single { it.id == "PLAYER_5_3" }.resolvedText)
        assertEquals("1", result.fields.single { it.id == "KILL_5_3" }.resolvedText)
        listOf(2, 4).forEach { slot ->
            assertEquals("", result.fields.single { it.id == "PLAYER_5_$slot" }.resolvedText)
            assertEquals("", result.fields.single { it.id == "KILL_5_$slot" }.resolvedText)
        }
    }

    @Test
    fun centeredPositionEightOnePlayerUsesExistingLeftRowOneMapping() {
        val classification = MatchResultPositionLogicalRowClassifier().classify(
            position = 8,
            cropWidth = 491,
            cropHeight = 81,
            slotCenterYLocal = 40.5,
            blocks = block(
                line("SOUMO`BHAI", 40, 32, 150, 43),
                line("0 Eliminations", 210, 35, 330, 44),
            ),
        ) as MatchResultPositionLogicalRowClassification.Available

        assertEquals(MatchResultPositionLogicalRowClassificationKind.CENTERED_SINGLE_ROW, classification.diagnostics.classification)
        assertEquals(listOf(1), classification.rowCrops.map { it.rowIndex })

        val result = mapper.map(
            MatchResultPositionOcrInput(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                position = 8,
                cropWidth = 491,
                cropHeight = 81,
                blocks = classification.blocks,
                rowCrops = classification.rowCrops,
                placementVerification = unresolved(),
                killVerifications = emptyMap(),
            ),
        )

        assertEquals(listOf(1), result.row!!.playerSlots.map { it.slot })
        assertEquals("SOUMO`BHAI", result.fields.single { it.id == "PLAYER_8_1" }.resolvedText)
        assertEquals("0", result.fields.single { it.id == "KILL_8_1" }.resolvedText)
        (2..4).forEach { slot ->
            assertEquals("", result.fields.single { it.id == "PLAYER_8_$slot" }.resolvedText)
            assertEquals("", result.fields.single { it.id == "KILL_8_$slot" }.resolvedText)
        }
    }

    @Test
    fun singlePhysicalRowWithOnePlayerRetainsPlayerAndBlankKill() {
        val result = mapper.map(
            MatchResultPositionOcrInput(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                position = 11,
                cropWidth = 491,
                cropHeight = 82,
                blocks = block(line("PlayerA", 70, 10, 160, 30)),
                rowCrops = listOf(row(1, 0, 41)),
                placementVerification = unresolved(),
                killVerifications = emptyMap(),
            ),
        )

        assertEquals(listOf(1), result.row!!.playerSlots.map { it.slot })
        assertEquals("", result.row.playerSlots.single().kill.resolvedText)
    }

    @Test
    fun strongMiddleKillAnchorsCreateOnePlayerBoundaryAndPreserveTheRemainder() {
        val cases = listOf(
            StrongBoundaryCase("3EliminationsNxfhaccrr", "3", "Nxfhaccrr", MatchResultEliminationPrefixType.EXPLICIT_NUMERIC),
            StrongBoundaryCase("3EliminatiNxfhaccrr", "3", "Nxfhaccrr", MatchResultEliminationPrefixType.EXPLICIT_NUMERIC),
            StrongBoundaryCase("3EliminationsEliminationKing", "3", "EliminationKing", MatchResultEliminationPrefixType.EXPLICIT_NUMERIC),
            StrongBoundaryCase("O EliminatioPLAYER", "0", "PLAYER", MatchResultEliminationPrefixType.O_NORMALIZED),
            StrongBoundaryCase("0 EliminatiPLAYER", "0", "PLAYER", MatchResultEliminationPrefixType.EXPLICIT_NUMERIC),
            StrongBoundaryCase("3EliminationsEliminatiPro", "3", "EliminatiPro", MatchResultEliminationPrefixType.EXPLICIT_NUMERIC),
        )

        cases.forEach { case ->
            val result = mapper.map(rightInput(position = 7, middle = case.middle))
            assertEquals(case.kill, result.fields.single { it.id == "KILL_7_1" }.resolvedText)
            assertEquals(case.player, result.fields.single { it.id == "PLAYER_7_3" }.resolvedText)
            val boundary = requireNotNull(result.playerBoundaryEvidence[3])
            assertTrue(boundary.boundaryAccepted)
            assertEquals(MatchResultPlayerBoundaryReason.STRONG_KILL_ANCHOR, boundary.reason)
            assertEquals(case.prefixType, boundary.anchorPrefixType)
            assertEquals("MIDDLE", boundary.anchorRegion)
        }
    }

    @Test
    fun weakMiddleMarkerIsRemovedFromMergedPlayerTextWhileExistingKillFallbackIsPreserved() {
        val result = mapper.map(rightInput(position = 7, middle = "EliminationsPLAYER"))

        assertEquals("0", result.fields.single { it.id == "KILL_7_1" }.resolvedText)
        assertEquals("PLAYER", result.fields.single { it.id == "PLAYER_7_3" }.resolvedText)
        val boundary = requireNotNull(result.playerBoundaryEvidence[3])
        assertTrue(!boundary.boundaryAccepted)
        assertEquals(MatchResultPlayerBoundaryReason.WEAK_NO_PREFIX, boundary.reason)
        assertEquals(MatchResultEliminationPrefixType.EMPTY_PREFIX, boundary.anchorPrefixType)
    }

    @Test
    fun mergedRightPlayerCleanupRemovesApprovedMarkersOnlyAtTheFirstThreePositions() {
        val cases = listOf(
            "EliminationsPLAYER" to "PLAYER",
            "Eliminationxx" to "xx",
            "Eliminatioxxp" to "xxp",
            "Eliminatipu" to "pu",
            "3EliminationsPLAYER" to "PLAYER",
            "BEliminatio6R-SMOO7HX5" to "6R-SMOO7HX5",
            "12EliminationsPLAYER" to "PLAYER",
            "3 EliminationsATX.KARAN" to "ATX.KARAN",
            "3EliminationsEliminationKing" to "EliminationKing",
            "ABCEliminationsPLAYER" to "ABCEliminationsPLAYER",
            "Eliminator" to "Eliminator",
            "PLAYERONE" to "PLAYERONE",
        )

        cases.forEach { (middle, expected) ->
            val result = mapper.map(rightInput(position = 6, middle = middle))
            assertEquals(
                expected,
                result.fields.single { it.id == "PLAYER_6_3" }.resolvedText,
            )
        }
    }

    @Test
    fun mergedRightPlayerCleanupCoversPlayerThreeAndPlayerFourAcrossAffectedPositions() {
        listOf(6, 7, 8, 9, 10, 11, 12).forEach { position ->
            val result = mapper.map(rightInput(position = position, middle = "BEliminatioPLAYER"))
            assertEquals(
                "PLAYER",
                result.fields.single { it.id == "PLAYER_${position}_3" }.resolvedText,
            )
        }

        val playerFour = mapper.map(
            rightInputForSlot(position = 11, slot = 4, middle = "BEliminatioPLAYER"),
        )
        assertEquals("PLAYER", playerFour.fields.single { it.id == "PLAYER_11_4" }.resolvedText)
    }

    @Test
    fun mergedRightPlayerCleanupDoesNotChangePlayerTwoOrEarlyPositionBehavior() {
        val playerTwo = mapper.map(
            rightInputForSlot(
                position = 6,
                slot = 2,
                middle = "PlayerFour",
                left = "EliminationsPLAYER",
            ),
        )
        assertEquals("", playerTwo.fields.single { it.id == "PLAYER_6_2" }.resolvedText)

        val earlyPosition = mapper.map(
            MatchResultPositionOcrInput(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                position = 5,
                cropWidth = 491,
                cropHeight = 82,
                blocks = block(line("EliminationsPLAYER", 320, 10, 400, 30)),
                rowCrops = listOf(row(1, 0, 41), row(2, 41, 82)),
                placementVerification = unresolved(),
                killVerifications = emptyMap(),
            ),
        )
        assertEquals("", earlyPosition.fields.single { it.id == "PLAYER_5_3" }.resolvedText)
    }

    @Test
    fun mergedRightPlayerCleanupLeavesDeepMarkersAndOrdinaryPlayerTextUnchanged() {
        val playerThree = mapper.map(rightInput(position = 6, middle = "PLAYEREliminationsX"))
        assertEquals("PLAYEREliminationsX", playerThree.fields.single { it.id == "PLAYER_6_3" }.resolvedText)

        val playerFour = mapper.map(
            rightInputForSlot(position = 11, slot = 4, middle = "TERMINATOR"),
        )
        assertEquals("TERMINATOR", playerFour.fields.single { it.id == "PLAYER_11_4" }.resolvedText)
    }

    @Test
    fun leftPlayerAndRightKillMarkersCannotBecomeMiddlePlayerBoundaries() {
        val leftMarker = mapper.map(
            rightInput(position = 7, middle = "MiddlePlayer", left = "EliminationKing"),
        )
        assertEquals("MiddlePlayer", leftMarker.fields.single { it.id == "PLAYER_7_3" }.resolvedText)
        assertEquals(MatchResultPlayerBoundaryReason.NO_VALID_ANCHOR, leftMarker.playerBoundaryEvidence.getValue(3).reason)

        val rightKill = mapper.map(
            rightInput(position = 7, middle = "MiddlePlayer", right = "1Eliminations"),
        )
        assertEquals("MiddlePlayer", rightKill.fields.single { it.id == "PLAYER_7_3" }.resolvedText)
        assertEquals(MatchResultPlayerBoundaryReason.NO_VALID_ANCHOR, rightKill.playerBoundaryEvidence.getValue(3).reason)
        assertEquals("1", rightKill.fields.single { it.id == "KILL_7_3" }.resolvedText)
    }

    @Test
    fun rightLayoutsKeepCanonicalSlotsAndGeometryAuthoritativePlacementAcrossSixToTwelve() {
        listOf(6, 7, 8, 9, 10, 11, 12).forEach { position ->
            val role = if (position <= 10) MatchResultScreenshotRole.MATCH_RESULT_UPPER else MatchResultScreenshotRole.MATCH_RESULT_LOWER
            val result = mapper.map(rightInput(position = position, middle = "3EliminationsPlayerB", role = role))
            assertEquals(position.toString(), result.fields.single { it.type == MatchResultOcrFieldType.PLACEMENT }.resolvedText)
            assertEquals("PlayerB", result.fields.single { it.id == "PLAYER_${position}_3" }.resolvedText)
            assertEquals("3", result.fields.single { it.id == "KILL_${position}_1" }.resolvedText)
        }
    }

    @Test
    fun physicalKillMatrixRemainsStable() {
        val expected = listOf(
            intArrayOf(2, 7, 8, 8), intArrayOf(1, 1, 2, 2), intArrayOf(0, 4, 0, 4),
            intArrayOf(2, 7, 3, 0), intArrayOf(1, 0, 0, 2), intArrayOf(2, 2, 3, 0),
            intArrayOf(3, 0, 0, 1), intArrayOf(1, 3, 1, 1), intArrayOf(1, 1, 2, 3),
            intArrayOf(8, 1, 5, 3), intArrayOf(4, 0, 3, 3), intArrayOf(0, 1, 1, 0),
        )
        expected.forEachIndexed { index, kills ->
            val position = index + 1
            val result = mapper.map(productionShapeInput(position, kills))
            kills.forEachIndexed { slotIndex, value ->
                assertEquals(value.toString(), result.fields.single { it.id == "KILL_${position}_${slotIndex + 1}" }.resolvedText)
            }
        }
    }

    @Test
    fun verifiedZeroIsAcceptedOnlyFromNumericEvidence() {
        val result = mapper.map(
            MatchResultPositionOcrInput(
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                position = 1,
                cropWidth = 605,
                cropHeight = 94,
                blocks = block(line("PLAYER", 80, 10, 180, 30), line("0 Eliminati", 220, 10, 390, 30)),
                rowCrops = listOf(row(1, 0, 47), row(2, 47, 94)),
                placementVerification = unresolved(),
                killVerifications = mapOf(1 to verified(0)),
            ),
        )
        val kill = result.fields.single { it.id == "KILL_1_1" }
        assertEquals("0", kill.resolvedText)
        assertEquals(MatchResultOcrFieldStatus.DIRECT_NUMERIC, kill.status)
    }

    private fun verified(value: Int) = MatchResultNumericVerification.Verified(
        value,
        listOf(MatchResultNumericCandidate(MatchResultNumericCropVariant.ORIGINAL, value.toString(), value, 0.9f)),
    )

    private fun unresolved() = MatchResultNumericVerification.Unresolved(emptyList())

    private fun input(position: Int, role: MatchResultScreenshotRole) = MatchResultPositionOcrInput(
        role = role,
        position = position,
        cropWidth = 491,
        cropHeight = 82,
        blocks = emptyList(),
        rowCrops = listOf(row(1, 0, 41), row(2, 41, 82)),
        placementVerification = unresolved(),
        killVerifications = emptyMap(),
    )

    private fun rightInput(
        position: Int,
        middle: String,
        role: MatchResultScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        left: String = "PlayerA",
        right: String = "1Eliminations",
    ) = MatchResultPositionOcrInput(
        role = role,
        position = position,
        cropWidth = 491,
        cropHeight = 82,
        blocks = block(
            line(left, 70, 10, 160, 30),
            line(middle, 205, 10, 350, 30),
            line(right, 410, 10, 480, 30),
        ),
        rowCrops = listOf(row(1, 0, 41), row(2, 41, 82)),
        placementVerification = unresolved(),
        killVerifications = emptyMap(),
    )

    private fun singleRowInput(rowIndex: Int) = MatchResultPositionOcrInput(
        role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        position = 11,
        cropWidth = 491,
        cropHeight = 82,
        blocks = block(
            line("PlayerA", 70, if (rowIndex == 1) 10 else 50, 160, if (rowIndex == 1) 30 else 70),
            line("2EliminationsPlayerC", 205, if (rowIndex == 1) 10 else 50, 350, if (rowIndex == 1) 30 else 70),
            line("1Eliminations", 410, if (rowIndex == 1) 10 else 50, 480, if (rowIndex == 1) 30 else 70),
        ),
        rowCrops = listOf(if (rowIndex == 1) row(1, 0, 41) else row(2, 41, 82)),
        placementVerification = unresolved(),
        killVerifications = emptyMap(),
    )

    private fun rightInputForSlot(
        position: Int,
        slot: Int,
        middle: String,
        role: MatchResultScreenshotRole = if (position <= 10) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER
        } else {
            MatchResultScreenshotRole.MATCH_RESULT_LOWER
        },
        left: String = "PlayerA",
        right: String = "1Eliminations",
    ) = MatchResultPositionOcrInput(
        role = role,
        position = position,
        cropWidth = 491,
        cropHeight = 82,
        blocks = block(
            line(left, 70, if (slot == 2) 50 else 10, 160, if (slot == 2) 70 else 30),
            line(middle, 205, if (slot == 4) 50 else 10, 350, if (slot == 4) 70 else 30),
            line(right, 410, if (slot == 4) 50 else 10, 480, if (slot == 4) 70 else 30),
        ),
        rowCrops = listOf(row(1, 0, 41), row(2, 41, 82)),
        placementVerification = unresolved(),
        killVerifications = emptyMap(),
    )

    private fun productionShapeInput(position: Int, kills: IntArray): MatchResultPositionOcrInput {
        val role = if (position <= 10) MatchResultScreenshotRole.MATCH_RESULT_UPPER else MatchResultScreenshotRole.MATCH_RESULT_LOWER
        val lines = if (position <= 5) {
            listOf(
                line("P1", 80, 10, 150, 30), line("${kills[0]}Eliminations", 220, 10, 280, 30),
                line("P3", 320, 10, 380, 30), line("${kills[2]}Eliminations", 420, 10, 480, 30),
                line("P2", 80, 50, 150, 70), line("${kills[1]}Eliminations", 220, 50, 280, 70),
                line("P4", 320, 50, 380, 70), line("${kills[3]}Eliminations", 420, 50, 480, 70),
            )
        } else {
            listOf(
                line("P1", 70, 10, 160, 30), line("${kills[0]}EliminationsP3", 205, 10, 350, 30),
                line("${kills[2]}Eliminations", 410, 10, 480, 30),
                line("P2", 70, 50, 160, 70), line("${kills[1]}EliminationsP4", 205, 50, 350, 70),
                line("${kills[3]}Eliminations", 410, 50, 480, 70),
            )
        }
        return MatchResultPositionOcrInput(
            role = role,
            position = position,
            cropWidth = 491,
            cropHeight = 82,
            blocks = block(*lines.toTypedArray()),
            rowCrops = listOf(row(1, 0, 41), row(2, 41, 82)),
            placementVerification = unresolved(),
            killVerifications = emptyMap(),
        )
    }

    private fun row(index: Int, top: Int, bottom: Int) = MatchResultPositionRowCrop(
        index,
        OcrPixelCropRect(0, top, 491, bottom),
    )

    private fun block(vararg lines: RawOcrLine) = listOf(
        RawOcrBlock("", null, null, RawOcrConfidence.Unavailable, lines.toList()),
    )

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) = RawOcrLine(
        text = text,
        geometry = RawOcrGeometry(RawOcrBoundingBox(left, top, right, bottom), null),
        recognizedLanguage = null,
        confidence = RawOcrConfidence.Available(0.9f),
        elements = listOf(RawOcrElement(text, RawOcrGeometry(RawOcrBoundingBox(left, top, right, bottom), null), null, RawOcrConfidence.Available(0.9f))),
    )

    private data class StrongBoundaryCase(
        val middle: String,
        val kill: String,
        val player: String,
        val prefixType: MatchResultEliminationPrefixType,
    )
}
