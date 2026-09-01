package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultNumericVerification
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionLogicalRowClassification
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionLogicalRowClassificationKind
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionLogicalRowDiagnostics
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionRowCrop
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionSemanticResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrPlayerSlot
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultPpOnlyPairReconciliationRunnerTest {
    @Test
    fun acceptedPpPairIsReturnedWithOnlyPpCalls() = runBlocking {
        val ppCalls = AtomicInteger()
        val runner = runner { identity ->
            ppCalls.incrementAndGet()
            processed(
                role = identity.role,
                playerSlots = listOf(blankPlayerSlot(identity.role)),
            )
        }

        val results = MatchResultScreenshotRole.entries.map { role ->
            async { runner.process(identity(role)) }
        }.awaitAll()

        assertTrue(results.all { it is MatchResultOcrPreviewProcessingResult.Processed })
        assertTrue(results.all { (it as MatchResultOcrPreviewProcessingResult.Processed).source == MatchResultOcrPreviewSource.NEW_PP_POSITION })
        assertEquals(2, ppCalls.get())
    }

    @Test
    fun swappedPhysicalRolesReturnCanonicalPpResults() = runBlocking {
        val runner = runner { storedIdentity ->
            val semanticRole = when (storedIdentity.role) {
                MatchResultScreenshotRole.MATCH_RESULT_UPPER -> MatchResultScreenshotRole.MATCH_RESULT_LOWER
                MatchResultScreenshotRole.MATCH_RESULT_LOWER -> MatchResultScreenshotRole.MATCH_RESULT_UPPER
            }
            processed(semanticRole)
        }

        val results = MatchResultScreenshotRole.entries.map { role ->
            async { runner.process(identity(role)) }
        }.awaitAll()

        assertEquals(
            MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            (results[0] as MatchResultOcrPreviewProcessingResult.Processed).extraction.role,
        )
        assertEquals(
            MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            (results[1] as MatchResultOcrPreviewProcessingResult.Processed).extraction.role,
        )
    }

    @Test
    fun semanticRoleConflictFailsSafely() = runBlocking {
        val runner = runner {
            processed(MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        }

        val results = MatchResultScreenshotRole.entries.map { role ->
            async { runner.process(identity(role)) }
        }.awaitAll()

        assertTrue(results.all { it == MatchResultOcrPreviewProcessingResult.SemanticRoleResolutionFailed })
    }

    @Test
    fun ppFailureRemainsPpFailureWithoutFallback() = runBlocking {
        val ppCalls = AtomicInteger()
        val runner = runner {
            ppCalls.incrementAndGet()
            MatchResultOcrPreviewProcessingResult.RecognitionFailed
        }

        val results = MatchResultScreenshotRole.entries.map { role ->
            async { runner.process(identity(role)) }
        }.awaitAll()

        assertTrue(results.all { it == MatchResultOcrPreviewProcessingResult.RecognitionFailed })
        assertEquals(2, ppCalls.get())
    }

    @Test
    fun unacceptableResolvedPpPairBecomesPpProcessingFailure() = runBlocking {
        val runner = runner { identity ->
            processed(identity.role, fields = emptyList())
        }

        val results = MatchResultScreenshotRole.entries.map { role ->
            async { runner.process(identity(role)) }
        }.awaitAll()

        assertTrue(results.all { it is MatchResultOcrPreviewProcessingResult.SemanticRoleProcessingFailed })
    }

    @Test
    fun validUpperAndSameRoleLowerFailurePreserveUpper() = runBlocking {
        val runner = runner { identity ->
            when (identity.role) {
                MatchResultScreenshotRole.MATCH_RESULT_UPPER -> processed(
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                )
                MatchResultScreenshotRole.MATCH_RESULT_LOWER ->
                    MatchResultOcrPreviewProcessingResult.SemanticRoleProcessingFailed(
                        MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                    )
            }
        }

        val results = MatchResultScreenshotRole.entries.map { role ->
            async { runner.process(identity(role)) }
        }.awaitAll()

        assertTrue(results[0] is MatchResultOcrPreviewProcessingResult.Processed)
        assertEquals(
            MatchResultOcrPreviewProcessingResult.SemanticRoleProcessingFailed(
                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            ),
            results[1],
        )
    }

    @Test
    fun validLowerAndSameRoleUpperFailurePreserveLower() = runBlocking {
        val runner = runner { identity ->
            when (identity.role) {
                MatchResultScreenshotRole.MATCH_RESULT_UPPER ->
                    MatchResultOcrPreviewProcessingResult.SemanticRoleProcessingFailed(
                        MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    )
                MatchResultScreenshotRole.MATCH_RESULT_LOWER -> processed(
                    MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                )
            }
        }

        val results = MatchResultScreenshotRole.entries.map { role ->
            async { runner.process(identity(role)) }
        }.awaitAll()

        assertEquals(
            MatchResultOcrPreviewProcessingResult.SemanticRoleProcessingFailed(
                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            ),
            results[0],
        )
        assertTrue(results[1] is MatchResultOcrPreviewProcessingResult.Processed)
    }

    @Test
    fun partialLowerExtractionRetainsEitherUsablePosition() {
        val position11 = semantic(MatchResultScreenshotRole.MATCH_RESULT_LOWER, 11)
        val position12 = semantic(MatchResultScreenshotRole.MATCH_RESULT_LOWER, 12)

        assertEquals(
            listOf(11),
            listOf(position11).toAcceptedExtraction(
                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                allowUpperFallback = false,
            )?.rows?.map { it.position },
        )
        assertEquals(
            listOf(12),
            listOf(position12).toAcceptedExtraction(
                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                allowUpperFallback = false,
            )?.rows?.map { it.position },
        )
    }

    @Test
    fun partialUpperExtractionRetainsOnlyExpectedUniquePositions() {
        val positions = listOf(1, 3, 10).map {
            semantic(MatchResultScreenshotRole.MATCH_RESULT_UPPER, it)
        }

        assertEquals(
            listOf(1, 3, 10),
            positions.toAcceptedExtraction(
                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                allowUpperFallback = false,
        )?.rows?.map { it.position },
        )
    }

    @Test
    fun upperFallbackAcceptsOptionalPositionElevenButNeverPositionTwelve() {
        val upperPositions = listOf(1, 11).map {
            semantic(MatchResultScreenshotRole.MATCH_RESULT_UPPER, it)
        }

        assertEquals(
            listOf(1, 11),
            upperPositions.toAcceptedExtraction(
                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                allowUpperFallback = true,
            )?.rows?.map { it.position },
        )
        assertEquals(
            null,
            listOf(semantic(MatchResultScreenshotRole.MATCH_RESULT_UPPER, 11)).toAcceptedExtraction(
                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                allowUpperFallback = false,
            ),
        )
        assertEquals(
            null,
            listOf(semantic(MatchResultScreenshotRole.MATCH_RESULT_UPPER, 12)).toAcceptedExtraction(
                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                allowUpperFallback = true,
            ),
        )
    }

    @Test
    fun missingLowerDoesNotDiscardUpperPositionEleven() = runBlocking {
        val runner = runner { identity ->
            when (identity.role) {
                MatchResultScreenshotRole.MATCH_RESULT_UPPER -> processed(
                    role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    positions = listOf(1, 11),
                )
                MatchResultScreenshotRole.MATCH_RESULT_LOWER ->
                    MatchResultOcrPreviewProcessingResult.MissingAsset
            }
        }

        val results = MatchResultScreenshotRole.entries.map { role ->
            async { runner.process(identity(role)) }
        }.awaitAll()

        assertEquals(
            listOf(1, 11),
            (results[0] as MatchResultOcrPreviewProcessingResult.Processed)
                .extraction.rows.map { it.position },
        )
        assertEquals(MatchResultOcrPreviewProcessingResult.MissingAsset, results[1])
    }

    @Test
    fun supplementalPositionElevenWithOnePlayerIsStructurallyUsableEvenWithBlankKill() {
        val player = field(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            type = MatchResultOcrFieldType.PLAYER,
            slot = 1,
            position = 11,
            resolvedText = "Player 11",
        )
        val blankKill = field(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            type = MatchResultOcrFieldType.KILL,
            slot = 1,
            position = 11,
            resolvedText = "",
        )
        val positionEleven = semantic(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            position = 11,
            playerSlots = listOf(MatchResultOcrPlayerSlot(1, player, blankKill)),
        )

        assertTrue(
            isPpPositionProductionStructurallyReady(
                localLines = 1,
                classification = availableClassification(),
                semantic = positionEleven,
            ),
        )
        assertEquals("Player 11", positionEleven.row!!.playerSlots.single().player.resolvedText)
        assertEquals("", positionEleven.row.playerSlots.single().kill.resolvedText)
    }

    @Test
    fun supplementalPositionElevenWithoutPlayersIsUnavailable() {
        assertTrue(
            !isPpPositionProductionStructurallyReady(
                localLines = 1,
                classification = availableClassification(),
                semantic = semantic(MatchResultScreenshotRole.MATCH_RESULT_UPPER, 11),
            ),
        )
    }

    @Test
    fun partialExtractionRejectsEmptyDuplicateAndCrossRolePositions() {
        val upperOne = semantic(MatchResultScreenshotRole.MATCH_RESULT_UPPER, 1)
        val lowerRoleAtUpperPosition = semantic(MatchResultScreenshotRole.MATCH_RESULT_LOWER, 11)
        assertEquals(
            null,
            emptyList<MatchResultPositionSemanticResult>().toAcceptedExtraction(
                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                allowUpperFallback = false,
            ),
        )
        assertEquals(
            null,
            listOf(upperOne, upperOne).toAcceptedExtraction(
                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                allowUpperFallback = false,
            ),
        )
        assertEquals(
            null,
            listOf(upperOne).toAcceptedExtraction(
                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                allowUpperFallback = false,
            ),
        )
        assertEquals(
            null,
            listOf(lowerRoleAtUpperPosition).toAcceptedExtraction(
                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                allowUpperFallback = false,
            ),
        )
    }

    @Test
    fun structurallyValidPositionWithBlankKillRemainsProductionUsable() {
        val player = field(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            type = MatchResultOcrFieldType.PLAYER,
            slot = 1,
            position = 10,
            resolvedText = "Player A",
        )
        val blankKill = field(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            type = MatchResultOcrFieldType.KILL,
            slot = 1,
            position = 10,
            resolvedText = "",
        )
        val semantic = MatchResultPositionSemanticResult(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            position = 10,
            fields = listOf(player, blankKill),
            row = MatchResultOcrRow(
                position = 10,
                source = MatchResultOcrRowSource.UPPER_TEMPLATE,
                placement = field(
                    role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    type = MatchResultOcrFieldType.PLACEMENT,
                    slot = null,
                    position = 10,
                    resolvedText = "10",
                ),
                playerSlots = listOf(MatchResultOcrPlayerSlot(1, player, blankKill)),
            ),
            placementVerification = MatchResultNumericVerification.Unresolved(emptyList()),
            killVerifications = emptyMap(),
            structuralIdentityValid = true,
            isAutoAcceptable = false,
        )

        assertTrue(
            isPpPositionProductionStructurallyReady(
                localLines = 1,
                classification = availableClassification(),
                semantic = semantic,
            ),
        )
        val row = requireNotNull(semantic.row)
        assertEquals("Player A", row.playerSlots.single().player.resolvedText)
        assertEquals("", row.playerSlots.single().kill.resolvedText)
    }

    @Test
    fun cancellationFromPpRouteIsNotConvertedToAnotherRoute() {
        try {
            runBlocking {
                runner { throw CancellationException("cancelled") }
                    .process(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER))
            }
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            // Cancellation is propagated from the PP route.
        }
    }

    private fun runner(
        ppRoute: MatchResultOcrPreviewRunner,
    ) = MatchResultPpOnlyPairReconciliationRunner(ppRoute = ppRoute)

    private fun identity(role: MatchResultScreenshotRole) = MatchResultScreenshotIdentity(
        tournamentId = "tournament",
        matchId = "match",
        role = role,
    )

    private fun processed(
        role: MatchResultScreenshotRole,
        fields: List<MatchResultOcrField>? = null,
        playerSlots: List<MatchResultOcrPlayerSlot> = emptyList(),
        positions: List<Int> = listOf(if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) 1 else 11),
    ) = MatchResultOcrPreviewProcessingResult.Processed(
        extraction = MatchResultOcrExtractionResult(
            role = role,
            fields = fields ?: positions.map { position ->
                field(role, position = position, resolvedText = position.toString())
            },
            rows = positions.map { position ->
                MatchResultOcrRow(
                    position = position,
                    source = MatchResultOcrRowSource.UPPER_TEMPLATE,
                    placement = field(role, position = position, resolvedText = position.toString()),
                    playerSlots = playerSlots,
                )
            },
        ),
        pixelCrop = OcrPixelCropRect(0, 0, 1, 1),
        cropWidth = 1,
        cropHeight = 1,
    )

    private fun field(
        role: MatchResultScreenshotRole,
        type: MatchResultOcrFieldType = MatchResultOcrFieldType.PLACEMENT,
        slot: Int? = null,
        position: Int = if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) 1 else 11,
        resolvedText: String = "1",
    ) = MatchResultOcrField(
        id = "${type.name}_${role.name}_${slot ?: 0}_$position",
        type = type,
        position = position,
        visualRow = null,
        slot = slot,
        canonicalRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
        mappedRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
        ocrText = resolvedText,
        resolvedText = resolvedText,
        status = if (resolvedText.isBlank()) MatchResultOcrFieldStatus.EMPTY else MatchResultOcrFieldStatus.DIRECT_TEXT,
    )

    private fun blankPlayerSlot(role: MatchResultScreenshotRole): MatchResultOcrPlayerSlot {
        val player = field(
            role = role,
            type = MatchResultOcrFieldType.PLAYER,
            slot = 1,
            resolvedText = "Player A",
        )
        val kill = field(
            role = role,
            type = MatchResultOcrFieldType.KILL,
            slot = 1,
            resolvedText = "",
        )
        return MatchResultOcrPlayerSlot(1, player, kill)
    }

    private fun semantic(
        role: MatchResultScreenshotRole,
        position: Int,
        playerSlots: List<MatchResultOcrPlayerSlot> = emptyList(),
    ): MatchResultPositionSemanticResult {
        val placement = field(
            role = role,
            type = MatchResultOcrFieldType.PLACEMENT,
            position = position,
            resolvedText = position.toString(),
        )
        return MatchResultPositionSemanticResult(
            role = role,
            position = position,
            fields = listOf(placement) + playerSlots.flatMap { listOf(it.player, it.kill) },
            row = MatchResultOcrRow(
                position = position,
                source = if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
                    MatchResultOcrRowSource.UPPER_TEMPLATE
                } else {
                    MatchResultOcrRowSource.LOWER_ROW_A
                },
                placement = placement,
                playerSlots = playerSlots,
            ),
            placementVerification = MatchResultNumericVerification.Verified(
                value = position,
                candidates = emptyList(),
            ),
            killVerifications = emptyMap(),
            structuralIdentityValid = true,
            isAutoAcceptable = true,
        )
    }

    private fun availableClassification() = MatchResultPositionLogicalRowClassification.Available(
        rowCrops = listOf(MatchResultPositionRowCrop(1, OcrPixelCropRect(0, 0, 10, 10))),
        blocks = emptyList<RawOcrBlock>(),
        diagnostics = MatchResultPositionLogicalRowDiagnostics(
            position = 10,
            positionHeight = 10,
            slotCenterYLocal = 5.0,
            medianTextHeight = 10.0,
            derivedTolerance = 5.0,
            totalMappedLines = 1,
            placementLinesRemoved = 0,
            spanningIgnored = 0,
            usableLines = 1,
            upperCount = 1,
            centerCount = 0,
            lowerCount = 0,
            classification = MatchResultPositionLogicalRowClassificationKind.ROW1_ONLY,
        ),
    )
}
