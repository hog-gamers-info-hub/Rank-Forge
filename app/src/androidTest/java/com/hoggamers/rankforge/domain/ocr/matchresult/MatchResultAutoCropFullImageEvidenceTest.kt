package com.hoggamers.rankforge.domain.ocr.matchresult

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.hoggamers.rankforge.data.ocr.DefaultMlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.toRawOcrBlocks
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.layout.OcrImageDimensions
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchResultAutoCropFullImageEvidenceTest {
    @Test
    fun emitsFullImageMlKitEvidenceForApprovedResultScreenshots() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val dimensions = OcrImageDimensions(width = EXPECTED_WIDTH, height = EXPECTED_HEIGHT)

        FIXTURES.forEach { fixture ->
            val bitmap = context.assets.open(fixture.assetPath).use(BitmapFactory::decodeStream)
                ?: error("Unable to decode approved screenshot fixture ${fixture.assetPath}.")
            try {
                assertEquals(EXPECTED_WIDTH, bitmap.width)
                assertEquals(EXPECTED_HEIGHT, bitmap.height)
                emitEvidence(fixture, bitmap, dimensions, targetContext)
            } finally {
                bitmap.recycleIfNeeded()
            }
        }
    }

    private suspend fun emitEvidence(
        fixture: Fixture,
        bitmap: Bitmap,
        dimensions: OcrImageDimensions,
        targetContext: android.content.Context,
    ) {
        val recognizer = DefaultMlKitTextRecognizerFactory().create()
        val recognizedText = try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitText()
        } finally {
            recognizer.close()
        }
        val blocks = recognizedText.toRawOcrBlocks()
        val observations = blocks
            .asSequence()
            .flatMap { it.elementObservations().asSequence() }
            .filter { it.isUsableFor(dimensions) }
            .sortedWith(observationComparator)
            .toList()
        val evidence = MatchResultAutoCropEvidence(
            observations = observations,
            imageDimensions = dimensions,
        )
        val detector = MatchResultAutoCropAnchorDetector()
        val anchorFourCandidates = observations.filter { it.text.trim() == "4" }
        val anchorFiveCandidates = observations.filter { it.text.trim() == "5" }
        val selectedAnchorFour = detector.findAnchorFour(evidence)
        val selectedAnchorFive = detector.findAnchorFive(evidence)
        val rightmostObservation = observations.maxWithOrNull(rightmostComparator)
        val result = MatchResultAutoCropCalculator().calculate(evidence)
        val reversedResult = MatchResultAutoCropCalculator().calculate(
            evidence.copy(observations = observations.asReversed()),
        )

        assertEquals(fixture.expectedP4, selectedAnchorFour)
        assertEquals(fixture.expectedP5, selectedAnchorFive)
        assertEquals(fixture.expectedRight, rightmostObservation?.boundingBox?.right)
        assertTrue(
            "AC-02R must propose a crop for ${fixture.id}; actual=$result",
            result is MatchResultAutoCropResult.Proposed,
        )
        assertEquals("Observation order must not change ${fixture.id} crop", result, reversedResult)
        val proposedCrop = (result as MatchResultAutoCropResult.Proposed).crop
        val actualPixelCrop = requireNotNull(proposedCrop.toPixelRectOrNull(dimensions))
        assertEquals(fixture.expectedPixelCrop, actualPixelCrop)
        saveActualCropPreview(targetContext, fixture, bitmap, actualPixelCrop)

        log(fixture, "IMAGE")
        log(fixture, "role=${fixture.role}")
        log(fixture, "width=${dimensions.width}")
        log(fixture, "height=${dimensions.height}")
        log(fixture, "OCR_HIERARCHY=element")
        log(fixture, "OCR_PASS_COUNT=1")
        log(fixture, "OBSERVATION_ADAPTER=ELEMENT_ONLY")

        log(fixture, "ALL_USABLE_OBSERVATIONS")
        observations.forEach { observation ->
            log(fixture, observation.format("OBS"))
        }

        log(fixture, "ANCHOR_4_CANDIDATES")
        anchorFourCandidates.forEach { log(fixture, it.format("ANCHOR_4_CANDIDATE")) }
        selectedAnchorFour?.let { log(fixture, it.format("SELECTED_ANCHOR_4")) }
            ?: log(fixture, "ANCHOR_4_MISSING")

        log(fixture, "ANCHOR_5_CANDIDATES")
        anchorFiveCandidates.forEach { log(fixture, it.format("ANCHOR_5_CANDIDATE")) }
        selectedAnchorFive?.let { log(fixture, it.format("SELECTED_ANCHOR_5")) }
            ?: log(fixture, "ANCHOR_5_MISSING")

        log(fixture, "GLOBAL_RIGHT")
        rightmostObservation?.let { log(fixture, it.format("RIGHTMOST_OBSERVATION")) }
            ?: log(fixture, "RIGHTMOST_OBSERVATION_MISSING")
        log(fixture, "GLOBAL_RIGHT_X2=${rightmostObservation?.boundingBox?.right ?: "MISSING"}")

        log(fixture, "AC_02_RESULT=${result.resultName()}")
        proposedCrop.toPixelRectOrNull(dimensions)?.let { log(fixture, it.format("PIXEL_CROP")) }
        log(
            fixture,
            "NORMALIZED_CROP left=${proposedCrop.left} top=${proposedCrop.top} " +
                "right=${proposedCrop.right} bottom=${proposedCrop.bottom}",
        )
        log(fixture, "REVERSED_OBSERVATION_RESULT=${reversedResult.resultName()}")
        log(fixture, "EXPECTED_PIXEL_CROP=${fixture.expectedPixelCrop.format("EXPECTED")}")
    }

    private fun log(fixture: Fixture, message: String) {
        Log.i(LOG_TAG, "fixture=${fixture.id} $message")
    }

    private fun saveActualCropPreview(
        targetContext: android.content.Context,
        fixture: Fixture,
        bitmap: Bitmap,
        pixelCrop: OcrPixelCropRect,
    ) {
        val previewFile = File(targetContext.cacheDir, "ac03f-${fixture.id}-actual-contract.png")
        val previewBitmap = Bitmap.createBitmap(
            bitmap,
            pixelCrop.left,
            pixelCrop.top,
            pixelCrop.width,
            pixelCrop.height,
        )
        try {
            FileOutputStream(previewFile).use { output ->
                check(previewBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Unable to write AC-03F preview for ${fixture.id}."
                }
            }
        } finally {
            previewBitmap.recycleIfNeeded()
        }
        log(fixture, "ACTUAL_PREVIEW_DEVICE_PATH=${previewFile.absolutePath}")
    }

    private fun RawOcrBlock.elementObservations(): List<MatchResultAutoCropObservation> =
        lines.flatMap { line ->
            line.elements.map { element ->
                MatchResultAutoCropObservation(
                    text = element.text,
                    boundingBox = element.geometry?.boundingBox,
                )
            }
        }

    private fun MatchResultAutoCropObservation.isUsableFor(dimensions: OcrImageDimensions): Boolean {
        val box = boundingBox ?: return false
        return text.trim().isNotEmpty() &&
            box.right > box.left &&
            box.bottom > box.top &&
            box.right > 0 &&
            box.bottom > 0 &&
            box.left < dimensions.width &&
            box.top < dimensions.height
    }

    private fun MatchResultAutoCropObservation.format(prefix: String): String {
        val box = requireNotNull(boundingBox)
        return "$prefix text=\"${text.replace("\\", "\\\\").replace("\"", "\\\"")}\" " +
            box.formatEdges()
    }

    private fun com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox.format(prefix: String): String =
        "$prefix ${formatEdges()}"

    private fun com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox.formatEdges(): String =
        "left=$left top=$top right=$right bottom=$bottom"

    private fun OcrPixelCropRect.format(prefix: String): String =
        "$prefix left=$left top=$top right=$right bottom=$bottom"

    private fun MatchResultAutoCropResult.resultName(): String = when (this) {
        is MatchResultAutoCropResult.Proposed -> "Proposed"
        MatchResultAutoCropResult.AnchorFourMissing -> "AnchorFourMissing"
        MatchResultAutoCropResult.AnchorFiveMissing -> "AnchorFiveMissing"
        MatchResultAutoCropResult.RightBoundaryMissing -> "RightBoundaryMissing"
        MatchResultAutoCropResult.InvalidRowPitch -> "InvalidRowPitch"
        MatchResultAutoCropResult.InvalidCalculatedCrop -> "InvalidCalculatedCrop"
        MatchResultAutoCropResult.OcrFailed -> "OcrFailed"
    }

    private fun Bitmap.recycleIfNeeded() {
        if (!isRecycled) recycle()
    }

    private data class Fixture(
        val id: String,
        val role: String,
        val assetPath: String,
        val expectedP4: RawOcrBoundingBox,
        val expectedP5: RawOcrBoundingBox,
        val expectedRight: Int,
        val expectedPixelCrop: OcrPixelCropRect,
    )

    private companion object {
        const val LOG_TAG = "AC03_FULL_IMAGE_OCR"
        const val EXPECTED_WIDTH = 1_600
        const val EXPECTED_HEIGHT = 720
        val FIXTURES = listOf(
            Fixture(
                id = "result-screenshot-1",
                role = "RESULT_SCREENSHOT_1",
                assetPath = "local-ocr-acceptance/v0.12.8/match-case-01-a.jpeg",
                expectedP4 = RawOcrBoundingBox(244, 480, 256, 499),
                expectedP5 = RawOcrBoundingBox(245, 573, 256, 593),
                expectedRight = 1369,
                expectedPixelCrop = OcrPixelCropRect(208, 162, 1369, 630),
            ),
            Fixture(
                id = "result-screenshot-2",
                role = "RESULT_SCREENSHOT_2",
                assetPath = "local-ocr-acceptance/v0.12.8/match-case-01-b.jpeg",
                expectedP4 = RawOcrBoundingBox(244, 479, 256, 499),
                expectedP5 = RawOcrBoundingBox(245, 573, 256, 593),
                expectedRight = 1371,
                expectedPixelCrop = OcrPixelCropRect(208, 160, 1371, 630),
            ),
        )
        val observationComparator = compareBy<MatchResultAutoCropObservation> {
            it.boundingBox?.top ?: Int.MAX_VALUE
        }.thenBy { it.boundingBox?.left ?: Int.MAX_VALUE }
            .thenBy { it.boundingBox?.right ?: Int.MAX_VALUE }
            .thenBy { it.boundingBox?.bottom ?: Int.MAX_VALUE }
            .thenBy { it.text }
        val rightmostComparator = compareBy<MatchResultAutoCropObservation> {
            it.boundingBox?.right ?: Int.MIN_VALUE
        }.thenBy { it.boundingBox?.left ?: Int.MIN_VALUE }
            .thenBy { it.boundingBox?.top ?: Int.MIN_VALUE }
            .thenBy { it.boundingBox?.bottom ?: Int.MIN_VALUE }
            .thenBy { it.text }
    }
}

private suspend fun Task<Text>.awaitText(): Text = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { text ->
        if (continuation.isActive) continuation.resume(text)
    }
    addOnFailureListener { throwable ->
        if (continuation.isActive) continuation.resumeWithException(throwable)
    }
    addOnCanceledListener {
        continuation.cancel(CancellationException("ML Kit OCR task was cancelled."))
    }
}
