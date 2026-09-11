package com.hoggamers.rankforge.data.ocr.matchlobby

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.media.Image
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.Feature
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.odml.image.MlImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.interfaces.Detector
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import com.hoggamers.rankforge.data.ocr.MlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.preprocessing.OcrEnhancementProfile
import com.hoggamers.rankforge.data.ocr.preprocessing.OcrImageEnhancer
import com.hoggamers.rankforge.domain.ocr.matchlobby.MatchLobbyAutoCropResult
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMatchLobbyAutoCropProposerTest {
    private lateinit var sourceFile: File

    @Before
    fun setUp() {
        sourceFile = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lobby-auto-crop-${System.nanoTime()}.png",
        )
        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            sourceFile.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }

    @After
    fun tearDown() {
        sourceFile.delete()
    }

    @Test
    fun validOriginalProposalUsesOneMlKitPassAndSkipsEnhancement() = runBlocking {
        val factory = SequencedRecognizerFactory(listOf(validText()))
        val enhancer = RecordingEnhancer()

        val result = proposer(factory, enhancer).propose(sourceFile)

        assertTrue(result is MatchLobbyAutoCropResult.Proposed)
        assertEquals(1, factory.processCount)
        assertEquals(0, enhancer.invocationCount)
    }

    @Test
    fun validTwoAnchorOriginalProposalAlsoSkipsEnhancement() = runBlocking {
        val factory = SequencedRecognizerFactory(
            listOf(
                text(
                    Anchor("1", 585, 236),
                    Anchor("4", 1076, 441),
                ),
            ),
        )
        val enhancer = RecordingEnhancer()

        val result = proposer(factory, enhancer).propose(sourceFile)

        assertTrue(result is MatchLobbyAutoCropResult.Proposed)
        assertEquals(1, factory.processCount)
        assertEquals(0, enhancer.invocationCount)
    }

    @Test
    fun insufficientOriginalEvidenceUsesOneEnhancementAndTwoMlKitPasses() = runBlocking {
        val factory = SequencedRecognizerFactory(listOf(emptyText(), validText()))
        val enhancer = RecordingEnhancer()

        val result = proposer(factory, enhancer).propose(sourceFile)

        assertTrue(result is MatchLobbyAutoCropResult.Proposed)
        assertEquals(2, factory.processCount)
        assertEquals(1, enhancer.invocationCount)
        assertTrue(requireNotNull(enhancer.lastBitmap).isRecycled)
        assertTrue(factory.bitmapInputs[0] !== factory.bitmapInputs[1])
        assertSame(enhancer.lastBitmap, factory.bitmapInputs[1])
    }

    @Test
    fun ambiguousTwoAnchorOriginalEvidenceUsesFallback() = runBlocking {
        val factory = SequencedRecognizerFactory(
            listOf(
                text(
                    Anchor("1", 585, 236),
                    Anchor("1", 700, 236),
                    Anchor("2", 1076, 236),
                ),
                validText(),
            ),
        )
        val enhancer = RecordingEnhancer()

        val result = proposer(factory, enhancer).propose(sourceFile)

        assertTrue(result is MatchLobbyAutoCropResult.Proposed)
        assertEquals(2, factory.processCount)
        assertEquals(1, enhancer.invocationCount)
    }

    @Test
    fun failedFallbackReturnsNoProposalWithoutThirdMlKitPass() = runBlocking {
        val factory = SequencedRecognizerFactory(listOf(emptyText(), emptyText()))
        val enhancer = RecordingEnhancer()

        val result = proposer(factory, enhancer).propose(sourceFile)

        assertEquals(MatchLobbyAutoCropResult.NoProposal, result)
        assertEquals(2, factory.processCount)
        assertEquals(1, enhancer.invocationCount)
        assertTrue(requireNotNull(enhancer.lastBitmap).isRecycled)
    }

    @Test
    fun mlKitFailureDoesNotTriggerEnhancementFallback() = runBlocking {
        val factory = FailingRecognizerFactory()
        val enhancer = RecordingEnhancer()

        val result = proposer(factory, enhancer).propose(sourceFile)

        assertEquals(MatchLobbyAutoCropResult.NoProposal, result)
        assertEquals(1, factory.createCount)
        assertEquals(0, enhancer.invocationCount)
    }

    private fun proposer(
        factory: MlKitTextRecognizerFactory,
        enhancer: RecordingEnhancer,
    ) = AndroidMatchLobbyAutoCropProposer(factory, enhancer)

    private fun validText() = text(
        Anchor("1", 585, 236),
        Anchor("2", 1076, 236),
        Anchor("3", 585, 441),
        Anchor("4", 1076, 441),
    )

    private fun emptyText() = Text("", emptyList<Text.TextBlock>())

    private fun text(vararg anchors: Anchor): Text {
        val blocks = anchors.map { anchor ->
            val rectangle = anchor.rectangle()
            val element = Text.Element(
                anchor.value,
                rectangle,
                emptyList<android.graphics.Point>(),
                "",
                null,
                0f,
                1f,
                emptyList<Text.Symbol>(),
            )
            val line = Text.Line(
                anchor.value,
                rectangle,
                emptyList<android.graphics.Point>(),
                "",
                null,
                listOf(element),
                0f,
                1f,
            )
            Text.TextBlock(
                anchor.value,
                rectangle,
                emptyList<android.graphics.Point>(),
                "",
                null,
                listOf(line),
            )
        }
        return Text(anchors.joinToString(" ") { it.value }, blocks)
    }

    private data class Anchor(
        val value: String,
        val centerX: Int,
        val centerY: Int,
    ) {
        fun rectangle() = Rect(
            centerX - 10,
            centerY - 10,
            centerX + 10,
            centerY + 10,
        )
    }

    private class RecordingEnhancer : OcrImageEnhancer {
        var invocationCount = 0
        var lastBitmap: Bitmap? = null

        override fun enhance(source: Bitmap, profile: OcrEnhancementProfile): Bitmap? {
            invocationCount += 1
            return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also {
                lastBitmap = it
            }
        }
    }

    private class SequencedRecognizerFactory(
        private val texts: List<Text>,
    ) : MlKitTextRecognizerFactory {
        var processCount = 0
        val bitmapInputs = mutableListOf<Bitmap>()

        override fun create(): TextRecognizer = RecordingTextRecognizer(texts[processCount]) { inputImage ->
            processCount += 1
            bitmapInputs += requireNotNull(inputImage.getBitmapInternal())
        }
    }

    private class FailingRecognizerFactory : MlKitTextRecognizerFactory {
        var createCount = 0

        override fun create(): TextRecognizer {
            createCount += 1
            throw IllegalStateException("synthetic recognizer creation failure")
        }
    }

    private class RecordingTextRecognizer(
        private val text: Text,
        private val onProcess: (InputImage) -> Unit,
    ) : TextRecognizer {
        override fun process(image: InputImage): Task<Text> {
            onProcess(image)
            return Tasks.forResult(text)
        }

        override fun process(image: MlImage): Task<Text> = Tasks.forResult(text)

        override fun process(image: Bitmap, rotationDegrees: Int): Task<Text> = Tasks.forResult(text)

        override fun process(image: Image, rotationDegrees: Int): Task<Text> = Tasks.forResult(text)

        override fun process(
            image: Image,
            rotationDegrees: Int,
            transformationMatrix: Matrix,
        ): Task<Text> = Tasks.forResult(text)

        override fun process(
            image: ByteBuffer,
            width: Int,
            height: Int,
            rotationDegrees: Int,
            imageFormat: Int,
        ): Task<Text> = Tasks.forResult(text)

        override fun getDetectorType(): Int = Detector.TYPE_TEXT_RECOGNITION

        override fun getOptionalFeatures(): Array<Feature> = emptyArray()

        override fun close() = Unit
    }

    private companion object {
        const val IMAGE_WIDTH = 1600
        const val IMAGE_HEIGHT = 720
    }
}
