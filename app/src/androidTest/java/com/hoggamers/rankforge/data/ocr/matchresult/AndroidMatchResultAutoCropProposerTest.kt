package com.hoggamers.rankforge.data.ocr.matchresult

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.ocr.DefaultMlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.MlKitTextRecognizerFactory
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropResult
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMatchResultAutoCropProposerTest {
    @Test
    fun approvedFixturesProduceExpectedProposedPixelCrops() = runBlocking {
        val proposer = AndroidMatchResultAutoCropProposer(DefaultMlKitTextRecognizerFactory())
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val dimensions = OcrImageDimensions(EXPECTED_WIDTH, EXPECTED_HEIGHT)

        FIXTURES.forEach { fixture ->
            val localFile = copyFixture(instrumentationContext, targetContext, fixture)
            try {
                val result = proposer.propose(localFile)

                assertTrue(
                    "Expected Proposed for ${fixture.id}, actual=$result",
                    result is MatchResultAutoCropResult.Proposed,
                )
                val crop = (result as MatchResultAutoCropResult.Proposed).crop
                assertEquals(fixture.expectedPixelCrop, crop.toPixelRectOrNull(dimensions))
            } finally {
                localFile.delete()
            }
        }
    }

    @Test
    fun undecodableLocalFileReturnsOcrFailed() = runBlocking {
        val missingFile = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "ac04-missing-${System.nanoTime()}.jpeg",
        )

        assertEquals(
            MatchResultAutoCropResult.OcrFailed,
            AndroidMatchResultAutoCropProposer(DefaultMlKitTextRecognizerFactory()).propose(missingFile),
        )
    }

    @Test
    fun recognizerExceptionReturnsOcrFailed() = runBlocking {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val localFile = copyFixture(instrumentationContext, targetContext, FIXTURES.first())
        val failingFactory = MlKitTextRecognizerFactory {
            throw IllegalStateException("synthetic recognizer creation failure")
        }

        try {
            assertEquals(
                MatchResultAutoCropResult.OcrFailed,
                AndroidMatchResultAutoCropProposer(failingFactory).propose(localFile),
            )
        } finally {
            localFile.delete()
        }
    }

    @Test
    fun cancellationIsPropagatedInsteadOfMappedToOcrFailed() = runBlocking {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val localFile = copyFixture(instrumentationContext, targetContext, FIXTURES.first())
        val cancellingFactory = MlKitTextRecognizerFactory {
            throw CancellationException("synthetic cancellation")
        }

        try {
            AndroidMatchResultAutoCropProposer(cancellingFactory).propose(localFile)
            fail("CancellationException should be propagated")
        } catch (_: CancellationException) {
            // Expected: cancellation is not an OCR failure.
        } finally {
            localFile.delete()
        }
    }

    private fun copyFixture(
        assetContext: android.content.Context,
        fileContext: android.content.Context,
        fixture: Fixture,
    ): File {
        val localFile = File(fileContext.cacheDir, "ac04-${fixture.id}.jpeg")
        assetContext.assets.open(fixture.assetPath).use { input ->
            localFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return localFile
    }

    private data class Fixture(
        val id: String,
        val assetPath: String,
        val expectedPixelCrop: OcrPixelCropRect,
    )

    private companion object {
        const val EXPECTED_WIDTH = 1_600
        const val EXPECTED_HEIGHT = 720
        val FIXTURES = listOf(
            Fixture(
                id = "result-screenshot-1",
                assetPath = "local-ocr-acceptance/v0.12.8/match-case-01-a.jpeg",
                expectedPixelCrop = OcrPixelCropRect(208, 162, 1369, 630),
            ),
            Fixture(
                id = "result-screenshot-2",
                assetPath = "local-ocr-acceptance/v0.12.8/match-case-01-b.jpeg",
                expectedPixelCrop = OcrPixelCropRect(208, 160, 1371, 630),
            ),
        )
    }
}
