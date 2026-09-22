package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultPositionKillFallbackMergerTest {
    private val merger = MatchResultPositionKillFallbackMerger()

    @Test
    fun noMissingPresentPlayerKillsDoesNotRequestPositionPp() = runTest {
        val base = semantic(
            position = 7,
            players = listOf("A", "", "", ""),
            kills = listOf("3", "", "", ""),
        )
        var calls = 0

        val result = merger.recover(listOf(base), setOf(7)) {
            calls += 1
            null
        }

        assertEquals(0, calls)
        assertSame(base, result.single())
    }

    @Test
    fun blankPlayersDoNotCreateFallbackTargets() = runTest {
        val base = semantic(
            position = 7,
            players = listOf("A", "", "", ""),
            kills = listOf("3", "", "", ""),
        )
        val targets = mutableListOf<MatchResultPositionKillFallbackTarget>()

        merger.recover(listOf(base), setOf(7)) {
            targets += it
            null
        }

        assertTrue(targets.isEmpty())
    }

    @Test
    fun multipleMissingKillsInOnePositionUseOneTarget() = runTest {
        val base = semantic(
            position = 7,
            players = listOf("A", "B", "", ""),
            kills = listOf("", "", "", ""),
        )
        val targets = mutableListOf<MatchResultPositionKillFallbackTarget>()

        merger.recover(listOf(base), setOf(7)) {
            targets += it
            null
        }

        assertEquals(1, targets.size)
        assertEquals(setOf(1, 2), targets.single().missingSlots)
    }

    @Test
    fun multiplePositionsAreRecoveredSequentiallyByPosition() = runTest {
        val positions = mutableListOf<Int>()
        val base = listOf(
            semantic(position = 7, players = listOf("A", "", "", ""), kills = listOf("", "", "", "")),
            semantic(position = 5, players = listOf("B", "", "", ""), kills = listOf("", "", "", "")),
        )

        merger.recover(base, setOf(5, 7)) {
            positions += it.position
            null
        }

        assertEquals(listOf(5, 7), positions)
    }

    @Test
    fun mlKitFilledKillIsNotTargeted() = runTest {
        val base = semantic(
            position = 7,
            players = listOf("A", "", "", ""),
            kills = listOf("6", "", "", ""),
        )
        var calls = 0

        merger.recover(listOf(base), setOf(7)) {
            calls += 1
            null
        }

        assertEquals(0, calls)
    }

    @Test
    fun strongExplicitKillRecoveryUpdatesOnlyMissingKillAndRow() {
        val base = semantic(
            position = 7,
            players = listOf("BASE", "", "", ""),
            kills = listOf("", "", "", ""),
        )
        val fallback = semantic(
            position = 7,
            players = listOf("DIFFERENT", "", "", ""),
            kills = listOf("3", "", "", ""),
            evidence = mapOf(1 to parsedKill(3)),
        )

        val result = merger.merge(base, fallback, setOf(1))
        val kill = result.fields.single { it.id == "KILL_7_1" }

        assertEquals("3", kill.resolvedText)
        assertEquals(MatchResultOcrFieldStatus.DIRECT_NUMERIC, kill.status)
        assertEquals("3", result.row!!.playerSlots.single { it.slot == 1 }.kill.resolvedText)
        assertEquals("BASE", result.fields.single { it.id == "PLAYER_7_1" }.resolvedText)
        assertTrue(result.isAutoAcceptable)
    }

    @Test
    fun normalizedORecoveryUsesZeroStatus() {
        val base = semantic(
            position = 7,
            players = listOf("A", "", "", ""),
            kills = listOf("", "", "", ""),
        )
        val fallback = semantic(
            position = 7,
            players = listOf("A", "", "", ""),
            kills = listOf("0", "", "", ""),
            evidence = mapOf(1 to parsedKill(0, MatchResultEliminationPrefixType.O_NORMALIZED)),
        )

        val result = merger.merge(base, fallback, setOf(1))

        assertEquals("0", result.fields.single { it.id == "KILL_7_1" }.resolvedText)
        assertEquals(
            MatchResultOcrFieldStatus.O_NORMALIZED_TO_0,
            result.fields.single { it.id == "KILL_7_1" }.status,
        )
    }

    @Test
    fun markerOnlyAndMalformedEvidenceRemainBlank() {
        val base = semantic(
            position = 7,
            players = listOf("A", "", "", ""),
            kills = listOf("", "", "", ""),
        )
        val evidence = listOf(
            ParsedEliminationText(
                kill = null,
                playerSuffix = null,
                markerMatched = true,
                prefixType = MatchResultEliminationPrefixType.EMPTY_PREFIX,
                rawText = "Eliminati",
            ),
            ParsedEliminationText(
                kill = null,
                playerSuffix = null,
                markerMatched = true,
                prefixType = MatchResultEliminationPrefixType.EXPLICIT_NUMERIC,
                rawText = "xEliminations",
            ),
        )

        evidence.forEach { parsed ->
            val fallback = semantic(
                position = 7,
                players = listOf("A", "", "", ""),
                kills = listOf("", "", "", ""),
                evidence = mapOf(1 to parsed),
            )

            assertSame(base, merger.merge(base, fallback, setOf(1)))
        }
    }

    @Test
    fun resolvedKillsNamesPlacementAndGeometryArePreserved() {
        val base = semantic(
            position = 7,
            players = listOf("BASE_ONE", "BASE_TWO", "", ""),
            kills = listOf("3", "", "", ""),
        )
        val basePlacement = base.fields.single { it.type == MatchResultOcrFieldType.PLACEMENT }
        val baseResolvedKill = base.fields.single { it.id == "KILL_7_1" }
        val fallback = semantic(
            position = 7,
            players = listOf("OTHER_ONE", "OTHER_TWO", "", ""),
            kills = listOf("8", "2", "", ""),
            evidence = mapOf(1 to parsedKill(8), 2 to parsedKill(2)),
        )

        val result = merger.merge(base, fallback, setOf(1, 2))

        assertEquals("3", result.fields.single { it.id == "KILL_7_1" }.resolvedText)
        assertEquals(baseResolvedKill.canonicalRect, result.fields.single { it.id == "KILL_7_1" }.canonicalRect)
        assertEquals("2", result.fields.single { it.id == "KILL_7_2" }.resolvedText)
        assertEquals("BASE_ONE", result.fields.single { it.id == "PLAYER_7_1" }.resolvedText)
        assertEquals("BASE_TWO", result.fields.single { it.id == "PLAYER_7_2" }.resolvedText)
        assertEquals(basePlacement, result.fields.single { it.type == MatchResultOcrFieldType.PLACEMENT })
    }

    @Test
    fun failedPositionDoesNotPreventLaterPositionRecovery() = runTest {
        val base = listOf(
            semantic(position = 5, players = listOf("A", "", "", ""), kills = listOf("", "", "", "")),
            semantic(position = 7, players = listOf("B", "", "", ""), kills = listOf("", "", "", "")),
        )
        val calls = mutableListOf<Int>()

        val result = merger.recover(base, setOf(5, 7)) { target ->
            calls += target.position
            if (target.position == 5) throw IllegalStateException("recognize failed")
            semantic(
                position = 7,
                players = listOf("B", "", "", ""),
                kills = listOf("4", "", "", ""),
                evidence = mapOf(1 to parsedKill(4)),
            )
        }

        assertEquals(listOf(5, 7), calls)
        assertEquals("", result.single { it.position == 5 }.fields.single { it.id == "KILL_5_1" }.resolvedText)
        assertEquals("4", result.single { it.position == 7 }.fields.single { it.id == "KILL_7_1" }.resolvedText)
    }

    @Test
    fun cancellationPropagates() = runTest {
        val base = semantic(
            position = 7,
            players = listOf("A", "", "", ""),
            kills = listOf("", "", "", ""),
        )

        var propagated = false
        try {
            merger.recover(listOf(base), setOf(7)) {
                throw CancellationException("cancelled")
            }
        } catch (_: CancellationException) {
            propagated = true
        }

        assertTrue(propagated)
    }

    @Test
    fun mismatchedPositionOrRoleIsNotMerged() {
        val base = semantic(
            position = 7,
            players = listOf("A", "", "", ""),
            kills = listOf("", "", "", ""),
        )
        val wrongPosition = semantic(
            position = 8,
            players = listOf("A", "", "", ""),
            kills = listOf("3", "", "", ""),
            evidence = mapOf(1 to parsedKill(3)),
        )
        val wrongRole = semantic(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            position = 7,
            players = listOf("A", "", "", ""),
            kills = listOf("3", "", "", ""),
            evidence = mapOf(1 to parsedKill(3)),
        )

        assertSame(base, merger.merge(base, wrongPosition, setOf(1)))
        assertSame(base, merger.merge(base, wrongRole, setOf(1)))
    }

    @Test
    fun lowerRolePositionFallbackKeepsLowerRowSource() {
        val base = semantic(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            position = 11,
            players = listOf("A", "", "", ""),
            kills = listOf("", "", "", ""),
        )
        val fallback = semantic(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            position = 11,
            players = listOf("DIFFERENT", "", "", ""),
            kills = listOf("2", "", "", ""),
            evidence = mapOf(1 to parsedKill(2)),
        )

        val result = merger.merge(base, fallback, setOf(1))

        assertEquals(MatchResultOcrRowSource.LOWER_ROW_A, result.row!!.source)
        assertEquals(MatchResultOcrVisualRow.A, result.row.placement.visualRow)
        assertEquals("2", result.row.playerSlots.single { it.slot == 1 }.kill.resolvedText)
    }

    private fun parsedKill(
        kill: Int,
        prefixType: MatchResultEliminationPrefixType = MatchResultEliminationPrefixType.EXPLICIT_NUMERIC,
    ) = ParsedEliminationText(
        kill = kill,
        playerSuffix = null,
        markerMatched = true,
        prefixType = prefixType,
        rawText = if (prefixType == MatchResultEliminationPrefixType.O_NORMALIZED) {
            "O Eliminations"
        } else {
            "$kill Eliminations"
        },
    )

    private fun semantic(
        role: MatchResultScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        position: Int,
        players: List<String>,
        kills: List<String>,
        evidence: Map<Int, ParsedEliminationText?> = emptyMap(),
    ): MatchResultPositionSemanticResult {
        val visualRow = if (role == MatchResultScreenshotRole.MATCH_RESULT_LOWER) {
            if (position == 11) MatchResultOcrVisualRow.A else MatchResultOcrVisualRow.B
        } else {
            null
        }
        val fields = buildList {
            add(
                field(
                    id = "PLACEMENT_$position",
                    type = MatchResultOcrFieldType.PLACEMENT,
                    position = position,
                    visualRow = visualRow,
                    slot = null,
                    resolvedText = position.toString(),
                    status = MatchResultOcrFieldStatus.TEMPLATE_ONLY,
                    rect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
                ),
            )
            (1..4).forEach { slot ->
                val playerText = players[slot - 1]
                val killText = kills[slot - 1]
                add(
                    field(
                        id = "PLAYER_${position}_$slot",
                        type = MatchResultOcrFieldType.PLAYER,
                        position = position,
                        visualRow = visualRow,
                        slot = slot,
                        resolvedText = playerText,
                        status = if (playerText.isBlank()) MatchResultOcrFieldStatus.EMPTY else MatchResultOcrFieldStatus.DIRECT_TEXT,
                        rect = MatchResultOcrRect(slot.toDouble(), 0.0, slot + 0.5, 1.0),
                    ),
                )
                add(
                    field(
                        id = "KILL_${position}_$slot",
                        type = MatchResultOcrFieldType.KILL,
                        position = position,
                        visualRow = visualRow,
                        slot = slot,
                        resolvedText = killText,
                        status = if (killText.isBlank()) MatchResultOcrFieldStatus.EMPTY else MatchResultOcrFieldStatus.DIRECT_NUMERIC,
                        rect = MatchResultOcrRect(slot.toDouble(), 1.0, slot + 0.5, 2.0),
                    ),
                )
            }
        }
        val source = when (role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> MatchResultOcrRowSource.UPPER_TEMPLATE
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> if (position == 11) {
                MatchResultOcrRowSource.LOWER_ROW_A
            } else {
                MatchResultOcrRowSource.LOWER_ROW_B
            }
        }
        return MatchResultPositionSemanticResult(
            role = role,
            position = position,
            fields = fields,
            row = MatchResultOcrRowAssembler.assemble(position, source, fields, visualRow),
            placementVerification = MatchResultNumericVerification.Unresolved(emptyList()),
            killVerifications = emptyMap(),
            structuralIdentityValid = true,
            isAutoAcceptable = players.withIndex().filter { it.value.isNotBlank() }
                .all { kills[it.index].isNotBlank() },
            basicKillEvidence = evidence,
        )
    }

    private fun field(
        id: String,
        type: MatchResultOcrFieldType,
        position: Int,
        visualRow: MatchResultOcrVisualRow?,
        slot: Int?,
        resolvedText: String,
        status: MatchResultOcrFieldStatus,
        rect: MatchResultOcrRect,
    ) = MatchResultOcrField(
        id = id,
        type = type,
        position = position,
        visualRow = visualRow,
        slot = slot,
        canonicalRect = rect,
        mappedRect = rect,
        ocrText = resolvedText,
        resolvedText = resolvedText,
        status = status,
    )
}
