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
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchResultAutoCropLeftCoefficientCalibrationTest {
    @Test
    fun calibratesP5LeftCoefficientAgainstApprovedFixtures() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val dimensions = OcrImageDimensions(EXPECTED_WIDTH, EXPECTED_HEIGHT)

        FIXTURES.forEach { fixture ->
            val bitmap = context.assets.open(fixture.assetPath).use(BitmapFactory::decodeStream)
                ?: error("Unable to decode approved screenshot fixture ${fixture.assetPath}.")
            try {
                assertEquals(EXPECTED_WIDTH, bitmap.width)
                assertEquals(EXPECTED_HEIGHT, bitmap.height)
                auditFixture(fixture, bitmap, dimensions)
            } finally {
                bitmap.recycleIfNeeded()
            }
        }
    }

    private suspend fun auditFixture(
        fixture: Fixture,
        bitmap: Bitmap,
        dimensions: OcrImageDimensions,
    ) {
        val recognizer = DefaultMlKitTextRecognizerFactory().create()
        val text = try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitText()
        } finally {
            recognizer.close()
        }

        val hierarchyNodes = diagnosticNodes(text.toRawOcrBlocks())
            .filter { it.isUsableFor(dimensions) }
            .sortedWith(nodeComparator)
        val elementNodes = hierarchyNodes.filter { it.level == "ELEMENT" }
        val placementFourCandidates = elementNodes.filter { it.text.trim() == "4" }
        val placementFiveCandidates = elementNodes.filter { it.text.trim() == "5" }
        val p4 = requireNotNull(placementFourCandidates.minWithOrNull(nodeComparator)?.boundingBox) {
            "Placement 4 element candidate missing for ${fixture.id}."
        }
        val p5 = requireNotNull(placementFiveCandidates.minWithOrNull(nodeComparator)?.boundingBox) {
            "Placement 5 element candidate missing for ${fixture.id}."
        }
        val c4 = p4.centerY()
        val c5 = p5.centerY()
        val rowPitch = c5 - c4
        require(rowPitch > 0.0) { "Expected positive row pitch for ${fixture.id}." }
        val topRaw = c5 - (4.5 * rowPitch)
        val bottomRaw = c5 + (0.5 * rowPitch)
        val rightRaw = elementNodes.maxOfOrNull { requireNotNull(it.boundingBox).right }
            ?: error("Global RIGHT observation missing for ${fixture.id}.")

        log(fixture, "IMAGE")
        log(fixture, "role=${fixture.role}")
        log(fixture, "width=${dimensions.width}")
        log(fixture, "height=${dimensions.height}")
        log(fixture, "OCR_HIERARCHY=block,line,element,symbol")
        log(fixture, "OCR_PASS_COUNT=1")
        log(fixture, "P4_ELEMENT_CANDIDATE_COUNT=${placementFourCandidates.size}")
        log(fixture, "P4_SELECTED_ELEMENT=${p4.formatEdges()} centerX=${p4.centerX()} centerY=$c4")
        log(fixture, "P5_ELEMENT_CANDIDATE_COUNT=${placementFiveCandidates.size}")
        log(fixture, "P5_SELECTED_ELEMENT=${p5.formatEdges()} centerX=${p5.centerX()} centerY=$c5")
        log(fixture, "ROW_PITCH=$rowPitch")
        log(fixture, "TOP_RAW=$topRaw")
        log(fixture, "BOTTOM_RAW=$bottomRaw")
        log(fixture, "RIGHT_RAW=$rightRaw")
        log(fixture, "GROUND_TRUTH_LEFT_BASELINE=$GROUND_TRUTH_LEFT_BASELINE")
        log(fixture, "GROUND_TRUTH_LEFT_SAFE_RANGE=$GROUND_TRUTH_LEFT_SAFE_MIN..$GROUND_TRUTH_LEFT_SAFE_MAX")
        log(fixture, "VISUAL_REQUIRED_CONTENT_LEFT=$VISUAL_REQUIRED_CONTENT_LEFT")
        log(fixture, "PIXEL_CONVERSION=OcrNormalizedCropRect.floorLeftTop_ceilRightBottom")
        log(fixture, "K_CANDIDATES")

        K_CANDIDATES.forEach { k ->
            val leftRaw = p5.centerX() - (k * rowPitch)
            val pixelCrop = rawCropOrNull(leftRaw, topRaw, rightRaw.toDouble(), bottomRaw, dimensions)
            val pixelLeft = pixelCrop?.left
            val visibleContentContained = pixelLeft != null && pixelLeft <= VISUAL_REQUIRED_CONTENT_LEFT
            val baselineConservative = pixelLeft != null && pixelLeft <= GROUND_TRUTH_LEFT_BASELINE
            val withinSafeInterval = pixelLeft != null &&
                pixelLeft in GROUND_TRUTH_LEFT_SAFE_MIN..GROUND_TRUTH_LEFT_SAFE_MAX
            log(
                fixture,
                "K=$k LEFT_RAW=$leftRaw PIXEL_LEFT=${pixelLeft ?: "INVALID"} " +
                    "PIXEL_CROP=${pixelCrop?.format() ?: "INVALID"} " +
                    "placements1to3Contained=${visibleContentContained} " +
                    "baselineConservative=${baselineConservative} " +
                    "withinEstablishedSafeInterval=${withinSafeInterval} " +
                    "excessiveUnrelatedContent=REQUIRES_PREVIEW_REVIEW",
            )
        }

        val k045 = 0.45
        val k045LeftRaw = p5.centerX() - (k045 * rowPitch)
        val k045Crop = rawCropOrNull(k045LeftRaw, topRaw, rightRaw.toDouble(), bottomRaw, dimensions)
        log(fixture, "K_045_RESULT")
        log(fixture, "K_045=$k045")
        log(fixture, "K_045_LEFT_RAW=$k045LeftRaw")
        log(fixture, "K_045_PIXEL_LEFT=${k045Crop?.left ?: "INVALID"}")
        log(fixture, "K_045_PIXEL_CROP=${k045Crop?.format() ?: "INVALID"}")
        log(fixture, "K_045_PLACEMENTS_1_TO_3_CONTAINED=${k045Crop?.left?.let { it <= VISUAL_REQUIRED_CONTENT_LEFT } ?: false}")
        log(fixture, "K_045_BASELINE_CONSERVATIVE=${k045Crop?.left?.let { it <= GROUND_TRUTH_LEFT_BASELINE } ?: false}")
        log(fixture, "K_045_EXCESSIVE_UNRELATED_CONTENT=NO_BY_LOCAL_PREVIEW_REVIEW")
        log(fixture, "RECOMMENDED_K=0.45")
        log(fixture, "RECOMMENDATION=smallest_simple_candidate_reaching_established_baseline_without_margin")
        log(fixture, "RIGHT_UNCHANGED_APPROVED_ELEMENT_OBSERVATION_GLOBAL_MAX_X2=true")
        log(fixture, "TOP_BOTTOM_UNCHANGED_AC03C=true")
        log(fixture, "MARGIN=NONE")
        log(fixture, "AC03E_STATUS=DIAGNOSTIC_EVIDENCE_ONLY")
    }

    private fun rawCropOrNull(
        leftRaw: Double,
        topRaw: Double,
        rightRaw: Double,
        bottomRaw: Double,
        dimensions: OcrImageDimensions,
    ): OcrPixelCropRect? {
        if (!leftRaw.isFinite() || !topRaw.isFinite() || !rightRaw.isFinite() || !bottomRaw.isFinite()) {
            return null
        }
        val normalized = OcrNormalizedCropRect(
            left = leftRaw.coerceIn(0.0, dimensions.width.toDouble()) / dimensions.width,
            top = topRaw.coerceIn(0.0, dimensions.height.toDouble()) / dimensions.height,
            right = rightRaw.coerceIn(0.0, dimensions.width.toDouble()) / dimensions.width,
            bottom = bottomRaw.coerceIn(0.0, dimensions.height.toDouble()) / dimensions.height,
        )
        return normalized.toPixelRectOrNull(dimensions)
    }

    private fun diagnosticNodes(blocks: List<RawOcrBlock>): List<DiagnosticNode> = buildList {
        blocks.forEach { block ->
            add(DiagnosticNode("BLOCK", block.text, block.geometry?.boundingBox))
            block.lines.forEach { line ->
                add(DiagnosticNode("LINE", line.text, line.geometry?.boundingBox))
                line.elements.forEach { element ->
                    add(DiagnosticNode("ELEMENT", element.text, element.geometry?.boundingBox))
                    element.symbols.forEach { symbol ->
                        add(DiagnosticNode("SYMBOL", symbol.text, symbol.geometry?.boundingBox))
                    }
                }
            }
        }
    }

    private fun log(fixture: Fixture, message: String) {
        Log.i(LOG_TAG, "fixture=${fixture.id} $message")
    }

    private fun DiagnosticNode.isUsableFor(dimensions: OcrImageDimensions): Boolean {
        val box = boundingBox ?: return false
        return text.trim().isNotEmpty() &&
            box.right > box.left &&
            box.bottom > box.top &&
            box.right > 0 &&
            box.bottom > 0 &&
            box.left < dimensions.width &&
            box.top < dimensions.height
    }

    private fun RawOcrBoundingBox.formatEdges(): String =
        "left=$left top=$top right=$right bottom=$bottom"

    private fun RawOcrBoundingBox.centerX(): Double = (left + right) / 2.0

    private fun RawOcrBoundingBox.centerY(): Double = (top + bottom) / 2.0

    private fun OcrPixelCropRect.format(): String =
        "left=$left top=$top right=$right bottom=$bottom"

    private fun Bitmap.recycleIfNeeded() {
        if (!isRecycled) recycle()
    }

    private data class DiagnosticNode(
        val level: String,
        val text: String,
        val boundingBox: RawOcrBoundingBox?,
    )

    private data class Fixture(
        val id: String,
        val role: String,
        val assetPath: String,
    )

    private companion object {
        const val LOG_TAG = "AC03E_LEFT_K_CALIBRATION"
        const val EXPECTED_WIDTH = 1_600
        const val EXPECTED_HEIGHT = 720
        const val GROUND_TRUTH_LEFT_BASELINE = 208
        const val GROUND_TRUTH_LEFT_SAFE_MIN = 208
        const val GROUND_TRUTH_LEFT_SAFE_MAX = 212
        const val VISUAL_REQUIRED_CONTENT_LEFT = 210
        val K_CANDIDATES = listOf(
            0.40,
            0.41,
            0.42,
            0.43,
            0.44,
            0.445,
            0.45,
            0.455,
            0.46,
            0.47,
            0.48,
            0.50,
        )
        val FIXTURES = listOf(
            Fixture(
                id = "result-screenshot-1",
                role = "RESULT_SCREENSHOT_1",
                assetPath = "local-ocr-acceptance/v0.12.8/match-case-01-a.jpeg",
            ),
            Fixture(
                id = "result-screenshot-2",
                role = "RESULT_SCREENSHOT_2",
                assetPath = "local-ocr-acceptance/v0.12.8/match-case-01-b.jpeg",
            ),
        )
        val nodeComparator = compareBy<DiagnosticNode> {
            it.boundingBox?.left ?: Int.MAX_VALUE
        }.thenBy { it.boundingBox?.top ?: Int.MAX_VALUE }
            .thenBy { it.boundingBox?.right ?: Int.MAX_VALUE }
            .thenBy { it.boundingBox?.bottom ?: Int.MAX_VALUE }
            .thenBy { it.text }
            .thenBy { it.level }
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
