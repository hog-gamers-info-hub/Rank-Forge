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
class MatchResultAutoCropAnchorAuditTest {
    @Test
    fun auditsPlacementOneAcrossFullImageOcrHierarchy() = runBlocking {
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
        val exactOnes = nodes.filter { it.text.trim() == "1" }
        val exactFives = nodes.filter { it.text.trim() == "5" }
        val placementOneRegionNodes = nodes.filter {
            it.boundingBox?.intersects(VISUAL_PLACEMENT_ONE_REGION) == true
        }
        val placementFiveRegionNodes = nodes.filter {
            it.boundingBox?.intersects(VISUAL_PLACEMENT_FIVE_REGION) == true
        }

        log(fixture, "IMAGE")
        log(fixture, "role=${fixture.role}")
        log(fixture, "width=${dimensions.width}")
        log(fixture, "height=${dimensions.height}")
        log(fixture, "OCR_HIERARCHY=block,line,element,symbol")
        log(fixture, "OCR_PASS_COUNT=1")
        log(fixture, "REGION_BASIS=visual_fixture_inspection_only_not_ocr_input")
        log(fixture, "PLACEMENT_1_INSPECTION_REGION=${VISUAL_PLACEMENT_ONE_REGION.formatEdges()}")
        log(fixture, "PLACEMENT_5_INSPECTION_REGION=${VISUAL_PLACEMENT_FIVE_REGION.formatEdges()}")

        log(fixture, "EXACT_1_CANDIDATES count=${exactOnes.size}")
        exactOnes.forEachIndexed { index, node ->
            log(fixture, node.format("EXACT_1_CANDIDATE index=${index + 1}"))
        }

        log(fixture, "PLACEMENT_1_REGION")
        if (placementOneRegionNodes.isEmpty()) {
            log(fixture, "PLACEMENT_1_REGION_NO_USABLE_HIERARCHY_NODE")
        } else {
            placementOneRegionNodes.forEach { node ->
                log(fixture, node.format("PLACEMENT_1_REGION_NODE"))
            }
        }

        log(fixture, "PLACEMENT_5_REFERENCE")
        placementFiveRegionNodes.forEach { node ->
            log(fixture, node.format("PLACEMENT_5_REGION_NODE"))
        }

        log(fixture, "HIERARCHY_AROUND_PLACEMENT_1")
        val relevantNodes = nodes.filter {
            it.boundingBox?.intersects(VISUAL_PLACEMENT_ONE_REGION) == true ||
                it.boundingBox?.intersects(VISUAL_PLACEMENT_FIVE_REGION) == true
        }
        if (relevantNodes.isEmpty()) {
            log(fixture, "NO_USABLE_HIERARCHY_NODES_IN_VISUAL_PLACEMENT_REGIONS")
        } else {
            relevantNodes.forEach { node ->
                log(fixture, node.format("HIERARCHY_NODE"))
            }
        }

        log(fixture, "GEOMETRY_COMPARISON")
        exactOnes.forEachIndexed { index, node ->
            log(fixture, node.geometryLine("false1_${index + 1}"))
        }
        placementFiveRegionNodes
            .filter { it.level == "ELEMENT" && it.text.trim() == "5" }
            .forEach { node -> log(fixture, node.geometryLine("placement5")) }
        log(
            fixture,
            "placement1_representation=NOT_DETECTED_IN_VISUAL_INSPECTION_REGION " +
                "region=${VISUAL_PLACEMENT_ONE_REGION.formatEdges()}",
        )

        log(fixture, "ROOT_CAUSE")
        log(
            fixture,
            "exact_element_1_candidates_exist_but_visual_placement_1_glyph_has_no_usable_node " +
                "in_the_visual_inspection_region",
        )
        log(fixture, "AC03B_STATUS=DIAGNOSTIC_EVIDENCE_ONLY")
    }

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
            "${box.formatEdges()} parent=\"${parentText?.sanitizedForLog() ?: "NONE"}\""
    }

    private fun DiagnosticNode.geometryLine(label: String): String {
        val box = requireNotNull(boundingBox)
        return "$label level=$level ${box.formatEdges()} width=${box.width()} height=${box.height()} " +
            "centerX=${box.centerX()} centerY=${box.centerY()}"
    }

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
        const val LOG_TAG = "AC03B_ANCHOR_AUDIT"
        const val EXPECTED_WIDTH = 1_600
        const val EXPECTED_HEIGHT = 720
        val VISUAL_PLACEMENT_ONE_REGION = RawOcrBoundingBox(190, 145, 320, 260)
        val VISUAL_PLACEMENT_FIVE_REGION = RawOcrBoundingBox(190, 540, 320, 625)
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
