package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HybridMatchResultOcrPreviewRunnerTest {
    @Test
    fun acceptedNewPairIsReturnedWithoutLegacyCalls() = runBlocking {
        val newRouteCalls = AtomicInteger()
        val legacyCalls = AtomicInteger()
        val runner = HybridMatchResultOcrPreviewRunner(
            newRoute = MatchResultOcrPreviewRunner { identity ->
                newRouteCalls.incrementAndGet()
                processed(identity.role, MatchResultOcrPreviewSource.NEW_PP_POSITION)
            },
            legacyRoute = MatchResultOcrPreviewRunner { identity ->
                legacyCalls.incrementAndGet()
                processed(identity.role, MatchResultOcrPreviewSource.LEGACY_FULL_SCREENSHOT)
            },
        )
        val results = MatchResultScreenshotRole.entries.map { role ->
            async { runner.process(identity(role)) }
        }.awaitAll()
        assertTrue(results.all { (it as MatchResultOcrPreviewProcessingResult.Processed).source == MatchResultOcrPreviewSource.NEW_PP_POSITION })
        assertEquals(2, newRouteCalls.get())
        assertEquals(0, legacyCalls.get())
    }

    @Test
    fun sequentialRoleRequestsReuseSingleCompletedPair() = runBlocking {
        val newRouteCalls = AtomicInteger()
        val runner = HybridMatchResultOcrPreviewRunner(
            newRoute = MatchResultOcrPreviewRunner { identity ->
                newRouteCalls.incrementAndGet()
                processed(identity.role, MatchResultOcrPreviewSource.NEW_PP_POSITION)
            },
            legacyRoute = MatchResultOcrPreviewRunner { identity ->
                processed(identity.role, MatchResultOcrPreviewSource.LEGACY_FULL_SCREENSHOT)
            },
        )

        val upper = runner.process(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER))
        val lower = runner.process(identity(MatchResultScreenshotRole.MATCH_RESULT_LOWER))

        assertEquals(
            MatchResultOcrPreviewSource.NEW_PP_POSITION,
            (upper as MatchResultOcrPreviewProcessingResult.Processed).source,
        )
        assertEquals(
            MatchResultOcrPreviewSource.NEW_PP_POSITION,
            (lower as MatchResultOcrPreviewProcessingResult.Processed).source,
        )
        assertEquals(2, newRouteCalls.get())
    }

    @Test
    fun repeatedSameRoleAfterCompletedPairStartsFreshRun() = runBlocking {
        val newRouteCalls = AtomicInteger()
        val runner = HybridMatchResultOcrPreviewRunner(
            newRoute = MatchResultOcrPreviewRunner { identity ->
                newRouteCalls.incrementAndGet()
                processed(identity.role, MatchResultOcrPreviewSource.NEW_PP_POSITION)
            },
            legacyRoute = MatchResultOcrPreviewRunner { identity ->
                processed(identity.role, MatchResultOcrPreviewSource.LEGACY_FULL_SCREENSHOT)
            },
        )

        runner.process(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER))
        runner.process(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER))

        assertEquals(4, newRouteCalls.get())
    }

    @Test
    fun unacceptableNewPairFallsBackForBothRoles() = runBlocking {
        val legacyCalls = AtomicInteger()
        val runner = HybridMatchResultOcrPreviewRunner(
            newRoute = MatchResultOcrPreviewRunner { MatchResultOcrPreviewProcessingResult.RecognitionFailed },
            legacyRoute = MatchResultOcrPreviewRunner { identity ->
                legacyCalls.incrementAndGet()
                processed(identity.role, MatchResultOcrPreviewSource.LEGACY_FULL_SCREENSHOT)
            },
        )
        val results = MatchResultScreenshotRole.entries.map { role -> async { runner.process(identity(role)) } }.awaitAll()
        assertTrue(results.all { (it as MatchResultOcrPreviewProcessingResult.Processed).source == MatchResultOcrPreviewSource.LEGACY_FULL_SCREENSHOT })
        assertEquals(2, legacyCalls.get())
    }

    @Test
    fun cancellationFromNewRouteIsNotConvertedToLegacy() {
        val legacyCalls = AtomicInteger()
        try {
            runBlocking {
                val runner = HybridMatchResultOcrPreviewRunner(
                    newRoute = MatchResultOcrPreviewRunner { throw CancellationException("cancelled") },
                    legacyRoute = MatchResultOcrPreviewRunner {
                        legacyCalls.incrementAndGet()
                        processed(it.role, MatchResultOcrPreviewSource.LEGACY_FULL_SCREENSHOT)
                    },
                )
                runner.process(identity(MatchResultScreenshotRole.MATCH_RESULT_UPPER))
            }
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(0, legacyCalls.get())
        }
    }

    private fun identity(role: MatchResultScreenshotRole) = MatchResultScreenshotIdentity(
        tournamentId = "tournament",
        matchId = "match",
        role = role,
    )

    private fun processed(role: MatchResultScreenshotRole, source: MatchResultOcrPreviewSource) =
        MatchResultOcrPreviewProcessingResult.Processed(
            extraction = MatchResultOcrExtractionResult(
                role = role,
                fields = listOf(field(role)),
                rows = listOf(
                    MatchResultOcrRow(
                        position = if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) 1 else 11,
                        source = MatchResultOcrRowSource.UPPER_TEMPLATE,
                        placement = field(role),
                        playerSlots = emptyList(),
                    ),
                ),
            ),
            pixelCrop = OcrPixelCropRect(0, 0, 1, 1),
            cropWidth = 1,
            cropHeight = 1,
            source = source,
        )

    private fun field(role: MatchResultScreenshotRole) = MatchResultOcrField(
        id = "PLACEMENT_${role.name}",
        type = MatchResultOcrFieldType.PLACEMENT,
        position = if (role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) 1 else 11,
        visualRow = null,
        slot = null,
        canonicalRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
        mappedRect = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0),
        ocrText = "",
        resolvedText = "1",
        status = MatchResultOcrFieldStatus.TEMPLATE_ONLY,
    )
}
