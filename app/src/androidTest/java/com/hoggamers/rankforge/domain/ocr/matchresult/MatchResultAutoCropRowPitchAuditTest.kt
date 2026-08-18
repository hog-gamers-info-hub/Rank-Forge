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
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchResultAutoCropRowPitchAuditTest {
    @Test
    fun auditsPlacementFourFiveRowPitchFromOneFullImageOcrPass() = runBlocking {
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

        val nodes = diagnosticNodes(text.toRawOcrBlocks())
            .filter { it.isUsableFor(dimensions) }
            .sortedWith(nodeComparator)
        val exactFours = nodes.filter { it.text.trim() == "4" }
        val exactFives = nodes.filter { it.text.trim() == "5" }
        val placementFourCandidates = exactFours.filter {
            it.boundingBox?.intersects(VISUAL_PLACEMENT_FOUR_REGION) == true
        }
        val placementFiveCandidates = exactFives.filter {
            it.boundingBox?.intersects(VISUAL_PLACEMENT_FIVE_REGION) == true
        }
        val selectedFour = selectVisualCandidate(placementFourCandidates)
        val selectedFive = selectVisualCandidate(placementFiveCandidates)
        val minimumXFourAcrossHierarchy = exactFours.minWithOrNull(nodeComparator)
        val minimumXFour = exactFours
            .filter { it.level == selectedFour?.level }
            .minWithOrNull(nodeComparator)
        val minimumXFive = exactFives
            .filter { it.level == selectedFive?.level }
            .minWithOrNull(nodeComparator)

        log(fixture, "IMAGE")
        log(fixture, "role=${fixture.role}")
        log(fixture, "width=${dimensions.width}")
        log(fixture, "height=${dimensions.height}")
        log(fixture, "OCR_HIERARCHY=block,line,element,symbol")
        log(fixture, "OCR_PASS_COUNT=1")
        log(fixture, "REGION_BASIS=visual_fixture_inspection_only_not_ocr_input")
        log(fixture, "PLACEMENT_4_INSPECTION_REGION=${VISUAL_PLACEMENT_FOUR_REGION.formatEdges()}")
        log(fixture, "PLACEMENT_5_INSPECTION_REGION=${VISUAL_PLACEMENT_FIVE_REGION.formatEdges()}")

        log(fixture, "EXACT_4_CANDIDATES count=${exactFours.size}")
        exactFours.forEachIndexed { index, node ->
            log(fixture, node.format("EXACT_4_CANDIDATE index=${index + 1}"))
        }
        log(fixture, "SELECTED_VISUAL_PLACEMENT_4")
        selectedFour?.let { log(fixture, it.format("PLACEMENT_4")) }
            ?: log(fixture, "PLACEMENT_4_NOT_FOUND_IN_VISUAL_REGION")
        log(fixture, "MIN_X1_CANDIDATE_4")
        minimumXFour?.let { log(fixture, it.format("MIN_X1_4")) }
            ?: log(fixture, "MIN_X1_4_NOT_FOUND")
        log(fixture, "MIN_X1_CORRECT_4=${minimumXFour != null && minimumXFour.sameGeometryAs(selectedFour)}")
        minimumXFourAcrossHierarchy?.let { log(fixture, it.format("MIN_X1_ALL_HIERARCHY_4")) }
        log(
            fixture,
            "MIN_X1_ALL_HIERARCHY_CORRECT_4=" +
                (minimumXFourAcrossHierarchy != null && minimumXFourAcrossHierarchy.sameGeometryAs(selectedFour)),
        )

        log(fixture, "EXACT_5_CANDIDATES count=${exactFives.size}")
        exactFives.forEachIndexed { index, node ->
            log(fixture, node.format("EXACT_5_CANDIDATE index=${index + 1}"))
        }
        log(fixture, "SELECTED_VISUAL_PLACEMENT_5")
        selectedFive?.let { log(fixture, it.format("PLACEMENT_5")) }
            ?: log(fixture, "PLACEMENT_5_NOT_FOUND_IN_VISUAL_REGION")
        log(fixture, "PLACEMENT_5_CORRECT=${selectedFive != null}")

        val c4 = selectedFour?.boundingBox?.centerY()
        val c5 = selectedFive?.boundingBox?.centerY()
        val rowPitch = if (c4 != null && c5 != null) c5 - c4 else null
        val topRaw = rowPitch?.let { c5!! - (4.5 * it) }
        val bottomRaw = rowPitch?.let { c5!! + (0.5 * it) }
        val topClamped = topRaw?.coerceIn(0.0, dimensions.height.toDouble())
        val bottomClamped = bottomRaw?.coerceIn(0.0, dimensions.height.toDouble())

        log(fixture, "ROW_PITCH")
        log(fixture, "C4=${c4 ?: "MISSING"}")
        log(fixture, "C5=${c5 ?: "MISSING"}")
        log(fixture, "ROW_PITCH=${rowPitch ?: "MISSING"}")
        log(fixture, "TOP_RAW=${topRaw ?: "MISSING"}")
        log(fixture, "BOTTOM_RAW=${bottomRaw ?: "MISSING"}")
        log(fixture, "TOP_CLAMPED=${topClamped ?: "MISSING"}")
        log(fixture, "BOTTOM_CLAMPED=${bottomClamped ?: "MISSING"}")

        val topContainsRowOne = topRaw?.let { it <= VISIBLE_ROW_ONE_TOP } ?: false
        val bottomContainsRowFive = bottomRaw?.let { it >= VISIBLE_ROW_FIVE_BOTTOM } ?: false
        log(fixture, "VISUAL_RESULT")
        log(fixture, "placement4Correct=${selectedFour != null}")
        log(fixture, "placement5Correct=${selectedFive != null}")
        log(fixture, "rowPitchPositive=${rowPitch?.let { it > 0.0 } ?: false}")
        log(fixture, "topContainsCompleteRow1=$topContainsRowOne")
        log(fixture, "topNotMateriallyInsideRow1=$topContainsRowOne")
        log(fixture, "bottomContainsCompleteRow5=$bottomContainsRowFive")
        log(
            fixture,
            "bottomBeyondPlacement5Glyph=${bottomRaw?.let { raw ->
                raw > (selectedFive?.boundingBox?.bottom?.toDouble() ?: Double.NEGATIVE_INFINITY)
            } ?: false}",
        )
        log(fixture, "completeFiveRowPanelCovered=${topContainsRowOne && bottomContainsRowFive}")
        log(fixture, "VISIBLE_ROW_ONE_TOP=$VISIBLE_ROW_ONE_TOP")
        log(fixture, "VISIBLE_ROW_FIVE_BOTTOM=$VISIBLE_ROW_FIVE_BOTTOM")
        log(fixture, "LEFT_BOUNDARY=UNRESOLVED")
        log(fixture, "RIGHT_BOUNDARY=UNCHANGED_GLOBAL_MAX_USABLE_OCR_X2")
        log(fixture, "MARGIN=NONE")
        log(fixture, "AC03C_STATUS=DIAGNOSTIC_EVIDENCE_ONLY")
    }

    private fun selectVisualCandidate(candidates: List<DiagnosticNode>): DiagnosticNode? =
        candidates.minWithOrNull(
            compareBy<DiagnosticNode> { levelPriority[it.level] ?: Int.MAX_VALUE }
                .thenBy { it.boundingBox?.left ?: Int.MAX_VALUE }
                .thenBy { it.boundingBox?.top ?: Int.MAX_VALUE }
                .thenBy { it.boundingBox?.right ?: Int.MAX_VALUE }
                .thenBy { it.boundingBox?.bottom ?: Int.MAX_VALUE }
                .thenBy { it.text }
                .thenBy { it.level },
        )

    private fun diagnosticNodes(blocks: List<RawOcrBlock>): List<DiagnosticNode> = buildList {
        blocks.forEach { block ->
            add(DiagnosticNode("BLOCK", block.text, block.geometry?.boundingBox, null))
            block.lines.forEach { line ->
                add(DiagnosticNode("LINE", line.text, line.geometry?.boundingBox, block.text))
                line.elements.forEach { element ->
                    add(DiagnosticNode("ELEMENT", element.text, element.geometry?.boundingBox, line.text))
                    element.symbols.forEach { symbol ->
                        add(DiagnosticNode("SYMBOL", symbol.text, symbol.geometry?.boundingBox, element.text))
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

    private fun DiagnosticNode.format(prefix: String): String {
        val box = requireNotNull(boundingBox)
        return "$prefix level=$level text=\"${text.sanitizedForLog()}\" " +
            "${box.formatEdges()} width=${box.width()} height=${box.height()} " +
            "centerX=${box.centerX()} centerY=${box.centerY()} " +
            "parent=\"${parentText?.sanitizedForLog() ?: "NONE"}\""
    }

    private fun DiagnosticNode.sameGeometryAs(other: DiagnosticNode?): Boolean =
        other != null && boundingBox == other.boundingBox

    private fun RawOcrBoundingBox.intersects(other: RawOcrBoundingBox): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    private fun RawOcrBoundingBox.formatEdges(): String =
        "left=$left top=$top right=$right bottom=$bottom"

    private fun RawOcrBoundingBox.width(): Int = right - left

    private fun RawOcrBoundingBox.height(): Int = bottom - top

    private fun RawOcrBoundingBox.centerX(): Double = (left + right) / 2.0

    private fun RawOcrBoundingBox.centerY(): Double = (top + bottom) / 2.0

    private fun String.sanitizedForLog(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")

    private fun Bitmap.recycleIfNeeded() {
        if (!isRecycled) recycle()
    }

    private data class DiagnosticNode(
        val level: String,
        val text: String,
        val boundingBox: RawOcrBoundingBox?,
        val parentText: String?,
    )

    private data class Fixture(
        val id: String,
        val role: String,
        val assetPath: String,
    )

    private companion object {
        const val LOG_TAG = "AC03C_ROW_PITCH_AUDIT"
        const val EXPECTED_WIDTH = 1_600
        const val EXPECTED_HEIGHT = 720
        // The first fully rendered pixel row of the fixture's top result panel.
        const val VISIBLE_ROW_ONE_TOP = 163
        const val VISIBLE_ROW_FIVE_BOTTOM = 626
        val VISUAL_PLACEMENT_FOUR_REGION = RawOcrBoundingBox(190, 440, 320, 535)
        val VISUAL_PLACEMENT_FIVE_REGION = RawOcrBoundingBox(190, 540, 320, 625)
        val levelPriority = mapOf("ELEMENT" to 0, "SYMBOL" to 1, "LINE" to 2, "BLOCK" to 3)
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
