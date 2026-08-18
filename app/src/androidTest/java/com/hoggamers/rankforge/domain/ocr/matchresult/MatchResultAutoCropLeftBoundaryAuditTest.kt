package com.hoggamers.rankforge.domain.ocr.matchresult

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchResultAutoCropLeftBoundaryAuditTest {
    @Test
    fun auditsLeftBoundaryAgainstIndependentVisualReference() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val dimensions = OcrImageDimensions(EXPECTED_WIDTH, EXPECTED_HEIGHT)
        val outputDirectory = File(context.cacheDir, "ac03d-left-boundary")

        FIXTURES.forEach { fixture ->
            val bitmap = context.assets.open(fixture.assetPath).use(BitmapFactory::decodeStream)
                ?: error("Unable to decode approved screenshot fixture ${fixture.assetPath}.")
            try {
                assertEquals(EXPECTED_WIDTH, bitmap.width)
                assertEquals(EXPECTED_HEIGHT, bitmap.height)
                auditFixture(fixture, bitmap, dimensions, outputDirectory)
            } finally {
                bitmap.recycleIfNeeded()
            }
        }
    }

    private suspend fun auditFixture(
        fixture: Fixture,
        bitmap: Bitmap,
        dimensions: OcrImageDimensions,
        outputDirectory: File,
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
        val selectedPlacementFour = selectVisualCandidate(
            nodes.filter {
                it.text.trim() == "4" &&
                    it.boundingBox?.intersects(VISUAL_PLACEMENT_FOUR_REGION) == true
            },
        )
        val selectedPlacementFive = selectVisualCandidate(
            nodes.filter {
                it.text.trim() == "5" &&
                    it.boundingBox?.intersects(VISUAL_PLACEMENT_FIVE_REGION) == true
            },
        )
        val p4 = requireNotNull(selectedPlacementFour?.boundingBox) {
            "Placement 4 was not found in the diagnostic visual region for ${fixture.id}."
        }
        val p5 = requireNotNull(selectedPlacementFive?.boundingBox) {
            "Placement 5 was not found in the diagnostic visual region for ${fixture.id}."
        }
        val c4 = p4.centerY()
        val c5 = p5.centerY()
        val rowPitch = c5 - c4
        require(rowPitch > 0.0) { "Expected positive row pitch for ${fixture.id}." }
        val topRaw = c5 - (4.5 * rowPitch)
        val bottomRaw = c5 + (0.5 * rowPitch)
        val globalRight = nodes
            .filter { it.level == "ELEMENT" }
            .maxOfOrNull { requireNotNull(it.boundingBox).right }
            ?: error("No usable element-level RIGHT boundary for ${fixture.id}.")
        val geometry = ReferenceGeometry(
            p4 = p4,
            p5 = p5,
            columnCenter = (p4.centerX() + p5.centerX()) / 2.0,
            rowPitch = rowPitch,
            topRaw = topRaw,
            bottomRaw = bottomRaw,
            right = globalRight,
        )
        val modelResults = MODELS.map { model ->
            model.evaluate(geometry, GROUND_TRUTH_LEFT.toDouble())
        }

        log(fixture, "IMAGE")
        log(fixture, "role=${fixture.role}")
        log(fixture, "width=${dimensions.width}")
        log(fixture, "height=${dimensions.height}")
        log(fixture, "OCR_HIERARCHY=block,line,element,symbol")
        log(fixture, "OCR_PASS_COUNT=1")
        log(fixture, "GROUND_TRUTH")
        log(fixture, "GROUND_TRUTH_LEFT=$GROUND_TRUTH_LEFT")
        log(fixture, "GROUND_TRUTH_LEFT_SAFE_RANGE=$GROUND_TRUTH_LEFT..$GROUND_TRUTH_LEFT_SAFE_MAX")
        log(fixture, "GROUND_TRUTH_SOURCE=existing_calibrated_layout_plus_direct_visual_measureMENT")
        log(fixture, "GROUND_TRUTH_CONFIDENCE=medium_no_fixture_specific_confirmed_crop_metadata")
        log(fixture, "VISUAL_REQUIRED_CONTENT_LEFT=$VISUAL_REQUIRED_CONTENT_LEFT")
        log(fixture, "REFERENCE_GEOMETRY")
        log(fixture, "P4=${p4.formatEdges()} centerX=${p4.centerX()} centerY=${p4.centerY()}")
        log(fixture, "P5=${p5.formatEdges()} centerX=${p5.centerX()} centerY=${p5.centerY()}")
        log(fixture, "COLUMN_CENTER=${geometry.columnCenter}")
        log(fixture, "ROW_PITCH=${geometry.rowPitch}")
        log(fixture, "TOP_RAW=${geometry.topRaw}")
        log(fixture, "BOTTOM_RAW=${geometry.bottomRaw}")
        log(fixture, "RIGHT_GLOBAL_X2=${geometry.right}")
        log(fixture, "OFFSET_RATIOS")
        logOffsetRatios(fixture, "P4_LEFT", p4.left.toDouble(), geometry)
        logOffsetRatios(fixture, "P5_LEFT", p5.left.toDouble(), geometry)
        logOffsetRatios(fixture, "P4_CENTER", p4.centerX(), geometry)
        logOffsetRatios(fixture, "P5_CENTER", p5.centerX(), geometry)
        logOffsetRatios(fixture, "COLUMN_CENTER", geometry.columnCenter, geometry)
        log(fixture, "CANDIDATE_MODELS")
        modelResults.forEach { result ->
            log(
                fixture,
                "${result.model.id}_REFERENCE=${result.model.referenceName} " +
                    "K=${result.k} LEFT=${result.left} safe=${result.safe}",
            )
            log(
                fixture,
                "${result.model.id}_FULL_RAW_CROP left=${result.left} top=${geometry.topRaw} " +
                    "right=${geometry.right} bottom=${geometry.bottomRaw}",
            )
            log(
                fixture,
                "${result.model.id}_VISUAL_RESULT placement1Contained=${result.placement1Contained} " +
                    "placement2Contained=${result.placement2Contained} " +
                    "placement3Contained=${result.placement3Contained} " +
                    "excessiveUnrelatedLeftContent=${result.excessiveUnrelatedLeftContent} " +
                    "passesCurrentResolution=${result.passesCurrentResolution}",
            )
        }
        log(fixture, "LEFT_MODEL_RATIO_COMPARISON=see_per_fixture_K_and_ratio_difference_logs")
        log(fixture, "RIGHT_BOUNDARY=UNCHANGED_GLOBAL_MAX_USABLE_OCR_X2")
        log(fixture, "TOP_BOTTOM=UNCHANGED_AC03C_ROW_PITCH_GEOMETRY")
        log(fixture, "MARGIN=NONE")

        val annotationPath = runCatching {
            check(outputDirectory.exists() || outputDirectory.mkdirs()) {
                "Unable to create the optional local AC-03D output directory."
            }
            writeAnnotation(
                bitmap = bitmap,
                fixture = fixture,
                geometry = geometry,
                modelResults = modelResults,
                outputDirectory = outputDirectory,
            )
        }.getOrNull()
        log(fixture, "ANNOTATED_OUTPUT_PATH=${annotationPath ?: "UNAVAILABLE_OPTIONAL_OUTPUT"}")
        log(fixture, "AC03D_STATUS=DIAGNOSTIC_EVIDENCE_ONLY")
    }

    private fun logOffsetRatios(
        fixture: Fixture,
        referenceName: String,
        reference: Double,
        geometry: ReferenceGeometry,
    ) {
        val offset = reference - GROUND_TRUTH_LEFT
        log(
            fixture,
            "OFFSET_FROM_$referenceName=$offset RATIO_FROM_$referenceName=${offset / geometry.rowPitch}",
        )
    }

    private fun writeAnnotation(
        bitmap: Bitmap,
        fixture: Fixture,
        geometry: ReferenceGeometry,
        modelResults: List<ModelResult>,
        outputDirectory: File,
    ): String {
        val annotated = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        boxPaint.color = Color.CYAN
        canvas.drawRect(geometry.p4.toRectF(), boxPaint)
        boxPaint.color = Color.MAGENTA
        canvas.drawRect(geometry.p5.toRectF(), boxPaint)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 2f
            textSize = 22f
        }
        drawVerticalLine(canvas, linePaint, GROUND_TRUTH_LEFT.toFloat(), Color.GREEN, "GROUND_TRUTH_LEFT")
        drawVerticalLine(canvas, linePaint, GROUND_TRUTH_LEFT_SAFE_MAX.toFloat(), Color.YELLOW, "GROUND_TRUTH_SAFE_MAX")
        drawVerticalLine(canvas, linePaint, geometry.columnCenter.toFloat(), Color.WHITE, "COLUMN_CENTER")
        modelResults.forEachIndexed { index, result ->
            drawVerticalLine(
                canvas,
                linePaint,
                result.left.toFloat(),
                MODEL_COLORS[index % MODEL_COLORS.size],
                result.model.id,
            )
        }
        drawHorizontalLine(canvas, linePaint, geometry.topRaw.toFloat(), Color.BLUE, "TOP_RAW")
        drawHorizontalLine(canvas, linePaint, geometry.bottomRaw.toFloat(), Color.BLUE, "BOTTOM_RAW")

        val output = File(outputDirectory, "${fixture.id}-left-boundary.png")
        FileOutputStream(output).use { stream ->
            check(annotated.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to write annotated AC-03D output for ${fixture.id}."
            }
        }
        annotated.recycleIfNeeded()
        return output.absolutePath
    }

    private fun drawVerticalLine(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        color: Int,
        label: String,
    ) {
        paint.color = color
        canvas.drawLine(x, 0f, x, EXPECTED_HEIGHT.toFloat(), paint)
        canvas.drawText(label, x + 4f, 28f, paint)
    }

    private fun drawHorizontalLine(
        canvas: Canvas,
        paint: Paint,
        y: Float,
        color: Int,
        label: String,
    ) {
        paint.color = color
        canvas.drawLine(0f, y, EXPECTED_WIDTH.toFloat(), y, paint)
        canvas.drawText(label, 4f, y - 4f, paint)
    }

    private fun selectVisualCandidate(candidates: List<DiagnosticNode>): DiagnosticNode? =
        candidates.minWithOrNull(
            compareBy<DiagnosticNode> { levelPriority[it.level] ?: Int.MAX_VALUE }
                .thenBy { it.boundingBox?.left ?: Int.MAX_VALUE }
                .thenBy { it.boundingBox?.top ?: Int.MAX_VALUE }
                .thenBy { it.boundingBox?.right ?: Int.MAX_VALUE }
                .thenBy { it.boundingBox?.bottom ?: Int.MAX_VALUE },
        )

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

    private fun RawOcrBoundingBox.intersects(other: RawOcrBoundingBox): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    private fun RawOcrBoundingBox.toRectF(): RectF =
        RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

    private fun RawOcrBoundingBox.formatEdges(): String =
        "left=$left top=$top right=$right bottom=$bottom"

    private fun RawOcrBoundingBox.centerX(): Double = (left + right) / 2.0

    private fun RawOcrBoundingBox.centerY(): Double = (top + bottom) / 2.0

    private fun Bitmap.recycleIfNeeded() {
        if (!isRecycled) recycle()
    }

    private data class DiagnosticNode(
        val level: String,
        val text: String,
        val boundingBox: RawOcrBoundingBox?,
    )

    private data class ReferenceGeometry(
        val p4: RawOcrBoundingBox,
        val p5: RawOcrBoundingBox,
        val columnCenter: Double,
        val rowPitch: Double,
        val topRaw: Double,
        val bottomRaw: Double,
        val right: Int,
    )

    private data class Model(
        val id: String,
        val referenceName: String,
        val reference: (ReferenceGeometry) -> Double,
    ) {
        fun evaluate(geometry: ReferenceGeometry, groundTruthLeft: Double): ModelResult {
            val referenceValue = reference(geometry)
            val k = (referenceValue - groundTruthLeft) / geometry.rowPitch
            val left = referenceValue - (k * geometry.rowPitch)
            val contains = left <= VISUAL_REQUIRED_CONTENT_LEFT
            val excessive = left < groundTruthLeft
            return ModelResult(
                model = this,
                k = k,
                left = left,
                safe = contains && !excessive,
                placement1Contained = contains,
                placement2Contained = contains,
                placement3Contained = contains,
                excessiveUnrelatedLeftContent = excessive,
                passesCurrentResolution = contains && !excessive,
            )
        }
    }

    private data class ModelResult(
        val model: Model,
        val k: Double,
        val left: Double,
        val safe: Boolean,
        val placement1Contained: Boolean,
        val placement2Contained: Boolean,
        val placement3Contained: Boolean,
        val excessiveUnrelatedLeftContent: Boolean,
        val passesCurrentResolution: Boolean,
    )

    private data class Fixture(
        val id: String,
        val role: String,
        val assetPath: String,
    )

    private companion object {
        const val LOG_TAG = "AC03D_LEFT_BOUNDARY_AUDIT"
        const val EXPECTED_WIDTH = 1_600
        const val EXPECTED_HEIGHT = 720
        const val GROUND_TRUTH_LEFT = 208
        const val GROUND_TRUTH_LEFT_SAFE_MAX = 212
        const val VISUAL_REQUIRED_CONTENT_LEFT = 210
        val VISUAL_PLACEMENT_FOUR_REGION = RawOcrBoundingBox(190, 440, 320, 535)
        val VISUAL_PLACEMENT_FIVE_REGION = RawOcrBoundingBox(190, 540, 320, 625)
        val levelPriority = mapOf("ELEMENT" to 0, "SYMBOL" to 1, "LINE" to 2, "BLOCK" to 3)
        val MODEL_COLORS = intArrayOf(Color.RED, Color.rgb(255, 128, 0), Color.rgb(128, 0, 255), Color.WHITE, Color.rgb(0, 255, 128))
        val MODELS = listOf(
            Model("MODEL_A", "P4.left") { it.p4.left.toDouble() },
            Model("MODEL_B", "P5.left") { it.p5.left.toDouble() },
            Model("MODEL_C", "P4.centerX") { geometry: ReferenceGeometry ->
                (geometry.p4.left + geometry.p4.right) / 2.0
            },
            Model("MODEL_D", "P5.centerX") { geometry: ReferenceGeometry ->
                (geometry.p5.left + geometry.p5.right) / 2.0
            },
            Model("MODEL_E", "COLUMN_CENTER") { geometry: ReferenceGeometry -> geometry.columnCenter },
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
