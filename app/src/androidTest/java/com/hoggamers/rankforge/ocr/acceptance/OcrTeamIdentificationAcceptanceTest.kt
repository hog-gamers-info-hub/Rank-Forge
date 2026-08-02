package com.hoggamers.rankforge.ocr.acceptance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.ocr.DefaultMlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.extraction.MlKitRawOcrEngineImpl
import com.hoggamers.rankforge.data.ocr.extraction.MlKitRawOcrTextExtractor
import com.hoggamers.rankforge.data.ocr.preprocessing.AndroidBitmapOcrImage
import com.hoggamers.rankforge.data.ocr.preprocessing.AndroidBitmapOcrImagePreprocessor
import com.hoggamers.rankforge.domain.matching.ScoreboardRowPlayerEvidence
import com.hoggamers.rankforge.domain.matching.ScoreboardRowPlayerEvidenceCollector
import com.hoggamers.rankforge.domain.matching.ScoreboardTeamIdentificationEvaluation
import com.hoggamers.rankforge.domain.matching.ScoreboardTeamIdentificationEvaluator
import com.hoggamers.rankforge.domain.matching.TeamAssignmentSafetyStatus
import com.hoggamers.rankforge.domain.matching.TeamCandidateRosterInput
import com.hoggamers.rankforge.domain.matching.TeamMatchConfidenceTier
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCandidate
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingInput
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingResult
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrTeamIdentificationAcceptanceTest {
    @Test
    fun approvedGenuineSetMeetsTeamIdentificationThreshold() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assetContext = instrumentation.context
        val reportContext = instrumentation.targetContext
        val manifestText = readAssetTextOrNull(assetContext, assetPath(DATASET_MANIFEST))
        assumeTrue("The approved local OCR dataset is not installed.", manifestText != null)

        val manifest = ManifestData.parse(requireNotNull(manifestText))
        require(manifest.required("dataset_status") == APPROVED_DATASET_STATUS)
        val canonicalCandidateOrder = manifest.requiredInt("canonical_candidate_order")
        val evaluableRowCount = manifest.requiredInt("evaluable_row_count")
        val thresholdPercent = manifest.requiredDouble("team_identification_threshold_percent")
        val falseAutomaticAssignmentsRequired =
            manifest.requiredInt("false_automatic_assignments_required")

        verifyAssetHashes(assetContext, manifest)

        val groundTruth = GroundTruth.parse(readAssetText(assetContext, GROUND_TRUTH))
        require(groundTruth.rows.size == CANONICAL_PLACEMENT_COUNT)
        require(groundTruth.rows.map { it.placementId }.toSet() == CANONICAL_PLACEMENTS)
        require(groundTruth.rows.map { it.caseId }.toSet().size == 1)
        require(groundTruth.rows.count { it.status == EvaluationStatus.EVALUABLE } == evaluableRowCount)
        require(groundTruth.rows.all { it.screenshotFile in CANONICAL_SCREENSHOTS })
        require(groundTruth.rows.map { it.screenshotFile }.toSet() == CANONICAL_SCREENSHOTS)

        val roster = Roster.parse(readAssetText(assetContext, ROSTER))
        require(roster.size == CANONICAL_PLACEMENT_COUNT)
        require(roster.map { it.teamSlot }.toSet() == CANONICAL_PLACEMENTS)

        val screenshotResults = linkedMapOf<String, ScreenshotResult>()
        for (screenshotFile in CANONICAL_SCREENSHOTS.sorted()) {
            screenshotResults[screenshotFile] = processScreenshot(
                context = assetContext,
                screenshotFile = screenshotFile,
                canonicalCandidateOrder = canonicalCandidateOrder,
            )
        }
        val canonicalEvidence = groundTruth.rows
            .sortedBy { it.placementId }
            .map { groundTruthRow ->
                screenshotResults.getValue(groundTruthRow.screenshotFile)
                    .rowsByPlacement[groundTruthRow.placementId]
                    ?: emptyEvidence(groundTruthRow.placementId)
            }
        require(canonicalEvidence.size == CANONICAL_PLACEMENT_COUNT)
        require(canonicalEvidence.map { it.expectedPlacementId }.toSet() == CANONICAL_PLACEMENTS)
        require(canonicalEvidence.map { it.rowIndex }.toSet() == CANONICAL_ROW_INDEXES)

        val evaluation = ScoreboardTeamIdentificationEvaluator.evaluate(canonicalEvidence, roster)
        require(evaluation.rows.size == CANONICAL_PLACEMENT_COUNT)
        require(evaluation.rows.map { it.expectedPlacementId }.toSet() == CANONICAL_PLACEMENTS)
        require(evaluation.rows.map { it.rowIndex }.toSet() == CANONICAL_ROW_INDEXES)

        val metrics = AcceptanceMetrics.from(groundTruth.rows, evaluation)
        writeSanitizedReport(
            context = reportContext,
            caseId = groundTruth.rows.first().caseId,
            screenshotResults = screenshotResults,
            metrics = metrics,
            thresholdPercent = thresholdPercent,
            falseAutomaticAssignmentsRequired = falseAutomaticAssignmentsRequired,
        )

        check(metrics.accuracyPercent >= thresholdPercent)
        check(metrics.falseAutomaticAssignments <= falseAutomaticAssignmentsRequired)
    }

    private suspend fun processScreenshot(
        context: Context,
        screenshotFile: String,
        canonicalCandidateOrder: Int,
    ): ScreenshotResult {
        val bitmap = context.assets.open(assetPath(screenshotFile)).use { input ->
            BitmapFactory.decodeStream(input)
        } ?: error("Unable to decode the approved OCR screenshot asset.")
        val sourceImage = AndroidBitmapOcrImage(bitmap)
        var candidates: List<OcrPreprocessingCandidate> = emptyList()

        return try {
            val preprocessingResult = AndroidBitmapOcrImagePreprocessor().preprocess(
                OcrPreprocessingInput(sourceImage),
            )
            candidates = when (preprocessingResult) {
                is OcrPreprocessingResult.Candidates -> preprocessingResult.candidates
                is OcrPreprocessingResult.Failed -> {
                    error("Approved OCR screenshot preprocessing did not produce candidates.")
                }
            }
            val canonicalCandidate = candidates.single { it.order == canonicalCandidateOrder }
            val extractor = MlKitRawOcrTextExtractor(
                MlKitRawOcrEngineImpl(DefaultMlKitTextRecognizerFactory()),
            )
            val extraction = extractor.extract(
                RawOcrExtractionInput(listOf(canonicalCandidate)),
            ).single()

            when (extraction) {
                is RawOcrExtractionResult.Extracted -> ScreenshotResult(
                    state = ExtractionState.EXTRACTED,
                    rowsByPlacement = ScoreboardRowPlayerEvidenceCollector()
                        .collect(extraction)
                        .associateBy { it.expectedPlacementId },
                )
                is RawOcrExtractionResult.Empty -> ScreenshotResult(
                    state = ExtractionState.EMPTY,
                    rowsByPlacement = emptyMap(),
                )
                is RawOcrExtractionResult.Failed -> ScreenshotResult(
                    state = ExtractionState.FAILED,
                    rowsByPlacement = emptyMap(),
                )
            }
        } finally {
            candidates.forEach { candidate ->
                (candidate.image as? AndroidBitmapOcrImage)?.bitmap?.recycleIfNeeded()
            }
            bitmap.recycleIfNeeded()
        }
    }

    private fun verifyAssetHashes(assetContext: Context, manifest: ManifestData) {
        listOf(SCREENSHOT_A, SCREENSHOT_B, ROSTER, GROUND_TRUTH, ACCEPTANCE_POLICY)
            .forEach { assetName ->
                val expectedHash = manifest.expectedHash(assetName)
                val actualHash = sha256(assetContext, assetName)
                check(actualHash == expectedHash)
            }
    }

    private fun writeSanitizedReport(
        context: Context,
        caseId: String,
        screenshotResults: Map<String, ScreenshotResult>,
        metrics: AcceptanceMetrics,
        thresholdPercent: Double,
        falseAutomaticAssignmentsRequired: Int,
    ) {
        val report = buildString {
            val passed = metrics.accuracyPercent >= thresholdPercent &&
                metrics.falseAutomaticAssignments <= falseAutomaticAssignmentsRequired
            appendLine("status=${if (passed) "PASS" else "FAIL"}")
            appendLine("case_id=${sanitizeCaseId(caseId)}")
            appendLine("screenshot_count=${screenshotResults.size}")
            screenshotResults.keys.sorted().forEachIndexed { index, screenshotFile ->
                appendLine("screenshot_${index + 1}_ocr_state=${screenshotResults.getValue(screenshotFile).state}")
            }
            appendLine("evaluable_row_count=${metrics.evaluableCount}")
            appendLine("correct_identifications=${metrics.correctIdentifications}")
            appendLine("incorrect_identifications=${metrics.incorrectIdentifications}")
            appendLine("unidentified_rows=${metrics.unidentifiedRows}")
            appendLine("accuracy_percent=${formatPercent(metrics.accuracyPercent)}")
            appendLine("automatic_candidate_count=${metrics.confidenceTierCounts[TeamMatchConfidenceTier.AUTOMATIC_CANDIDATE] ?: 0}")
            appendLine("confirmation_required_count=${metrics.confidenceTierCounts[TeamMatchConfidenceTier.CONFIRMATION_REQUIRED] ?: 0}")
            appendLine("manual_required_count=${metrics.confidenceTierCounts[TeamMatchConfidenceTier.MANUAL_REQUIRED] ?: 0}")
            appendLine("false_automatic_assignments=${metrics.falseAutomaticAssignments}")
            appendLine("expected_team_rank_1_count=${metrics.expectedTeamRankCounts[1] ?: 0}")
            appendLine("expected_team_rank_2_count=${metrics.expectedTeamRankCounts[2] ?: 0}")
            appendLine("expected_team_rank_3_count=${metrics.expectedTeamRankCounts[3] ?: 0}")
            appendLine("expected_team_absent_from_top_3_count=${metrics.expectedTeamAbsentFromTopThree}")
            metrics.rows.forEach { row ->
                appendLine(
                    "placement=${row.placementId},expected_team_slot=${row.expectedTeamSlot}," +
                        "identified_team_slot=${row.identifiedTeamSlot ?: "NONE"}," +
                        "suggested_team_slot=${row.suggestedTeamSlot ?: "NONE"}," +
                        "contributing_match_count=${row.contributingMatchCount}," +
                        "confidence_tier=${row.confidenceTier}," +
                        "assignment_safety=${row.assignmentSafety}," +
                        "top_three=${row.topThree.joinToString("|")}," +
                        "correct=${row.correct}",
                )
            }
        }
        println("RANK_FORGE_OCR_ACCEPTANCE_REPORT_BEGIN")
        print(report)
        println("RANK_FORGE_OCR_ACCEPTANCE_REPORT_END")

        context.openFileOutput(REPORT_FILE, Context.MODE_PRIVATE).use { output ->
            output.write(report.toByteArray(Charsets.UTF_8))
        }
    }

    private fun sha256(context: Context, assetName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(assetPath(assetName)).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(Locale.US, byte.toInt() and 0xff)
        }
    }

    private fun readAssetText(context: Context, assetName: String): String =
        context.assets.open(assetPath(assetName)).use { input ->
            InputStreamReader(input, Charsets.UTF_8).use { it.readText() }
        }

    private fun readAssetTextOrNull(context: Context, assetPath: String): String? = try {
        context.assets.open(assetPath).use { input ->
            InputStreamReader(input, Charsets.UTF_8).use { it.readText() }
        }
    } catch (_: FileNotFoundException) {
        null
    }

    private fun assetPath(assetName: String): String = "$ASSET_ROOT/$assetName"

    private fun emptyEvidence(placementId: Int) = ScoreboardRowPlayerEvidence(
        rowIndex = placementId - 1,
        expectedPlacementId = placementId,
        detectedPlayerNames = emptyList(),
    )

    private fun Bitmap.recycleIfNeeded() {
        if (!isRecycled) recycle()
    }

    private fun sanitizeCaseId(caseId: String): String =
        caseId.filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
            .take(MAX_CASE_ID_LENGTH)
            .ifEmpty { "UNKNOWN" }

    private fun formatPercent(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private data class ScreenshotResult(
        val state: ExtractionState,
        val rowsByPlacement: Map<Int, ScoreboardRowPlayerEvidence>,
    )

    private enum class ExtractionState {
        EXTRACTED,
        EMPTY,
        FAILED,
    }

    private data class ManifestData(
        val properties: Map<String, String>,
    ) {
        fun required(name: String): String =
            properties.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
                ?.value
                ?.takeIf { it.isNotBlank() }
                ?: error("The approved manifest is missing a required value.")

        fun requiredInt(name: String): Int = required(name).toInt()

        fun requiredDouble(name: String): Double = required(name).toDouble()

        fun expectedHash(assetName: String): String {
            val hashProperty = when (assetName) {
                SCREENSHOT_A, SCREENSHOT_B -> {
                    val screenshotProperty = listOf("screenshot_a", "screenshot_b")
                        .filter { property(it) == assetName }
                        .singleOrNull()
                        ?: error("The approved manifest is missing a screenshot mapping.")
                    "${screenshotProperty}_sha256"
                }
                ROSTER -> "roster_sha256"
                GROUND_TRUTH -> "ground_truth_sha256"
                ACCEPTANCE_POLICY -> "acceptance_policy_sha256"
                else -> error("The approved manifest references an unsupported asset.")
            }
            val hash = required(hashProperty).lowercase(Locale.US)
            require(HASH_PATTERN.matches(hash))
            return hash
        }

        private fun property(name: String): String? =
            properties.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        companion object {
            fun parse(text: String): ManifestData {
                val properties = text.lineSequence().mapNotNull { line ->
                    val trimmed = line.trim().removePrefix("\uFEFF")
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        null
                    } else {
                        val separator = listOf(trimmed.indexOf('='), trimmed.indexOf(':'))
                            .filter { it >= 0 }
                            .minOrNull()
                            ?: return@mapNotNull null
                        val key = trimmed.substring(0, separator).trim()
                        val value = trimmed.substring(separator + 1).trim().trim('"')
                        key to value
                    }
                }.toMap()
                return ManifestData(properties)
            }
        }
    }

    private data class GroundTruth(
        val rows: List<GroundTruthRow>,
    ) {
        companion object {
            fun parse(text: String): GroundTruth {
                val table = CsvTable.parse(text)
                val caseId = table.requiredColumn("case_id")
                val screenshotFile = table.requiredColumn("screenshot_file")
                val placementId = table.requiredColumn("placement_id")
                val expectedTeamSlot = table.requiredColumn("expected_team_slot")
                val evaluationStatus = table.requiredColumn("evaluation_status")
                val exclusionReason = table.requiredColumn("exclusion_reason")
                return GroundTruth(
                    rows = table.dataRows.map { row ->
                        val status = EvaluationStatus.valueOf(
                            row.getValue(evaluationStatus).trim().uppercase(Locale.US),
                        )
                        val expected = row.getValue(expectedTeamSlot).trim().toIntOrNull()
                        if (status == EvaluationStatus.EVALUABLE) require(expected != null)
                        GroundTruthRow(
                            caseId = row.getValue(caseId).trim(),
                            screenshotFile = row.getValue(screenshotFile).trim(),
                            placementId = row.getValue(placementId).trim().toInt(),
                            expectedTeamSlot = expected,
                            status = status,
                            exclusionReason = row.getValue(exclusionReason).trim(),
                        )
                    },
                )
            }
        }
    }

    private data class GroundTruthRow(
        val caseId: String,
        val screenshotFile: String,
        val placementId: Int,
        val expectedTeamSlot: Int?,
        val status: EvaluationStatus,
        val exclusionReason: String,
    )

    private enum class EvaluationStatus {
        EVALUABLE,
        UNEVALUABLE,
    }

    private object Roster {
        fun parse(text: String): List<TeamCandidateRosterInput> {
            val table = CsvTable.parse(text)
            val slot = table.requiredColumn("slot")
            val playerColumns = (1..4).map { table.requiredColumn("player_$it") }
            return table.dataRows.map { row ->
                TeamCandidateRosterInput(
                    teamSlot = row.getValue(slot).trim().toInt(),
                    rosterPlayerNames = playerColumns.map { column ->
                        row.getValue(column).trim().ifEmpty { null }
                    },
                )
            }
        }
    }

    private data class AcceptanceMetrics(
        val evaluableCount: Int,
        val correctIdentifications: Int,
        val incorrectIdentifications: Int,
        val unidentifiedRows: Int,
        val accuracyPercent: Double,
        val confidenceTierCounts: Map<TeamMatchConfidenceTier, Int>,
        val falseAutomaticAssignments: Int,
        val expectedTeamRankCounts: Map<Int, Int>,
        val expectedTeamAbsentFromTopThree: Int,
        val rows: List<RowMetric>,
    ) {
        companion object {
            fun from(
                groundTruthRows: List<GroundTruthRow>,
                evaluation: ScoreboardTeamIdentificationEvaluation,
            ): AcceptanceMetrics {
                val resultByPlacement = evaluation.rows.associateBy { it.expectedPlacementId }
                val evaluableRows = groundTruthRows.filter { it.status == EvaluationStatus.EVALUABLE }
                val rowMetrics = evaluableRows.map { expected ->
                    val observed = requireNotNull(resultByPlacement[expected.placementId])
                    val topThree = observed.suggestions.suggestions
                        .sortedBy { it.rank }
                        .map { it.teamCandidateScore.candidateTeamSlot }
                    RowMetric(
                        placementId = expected.placementId,
                        expectedTeamSlot = requireNotNull(expected.expectedTeamSlot),
                        identifiedTeamSlot = observed.identifiedTeamSlot,
                        suggestedTeamSlot = observed.suggestedTeamSlot,
                        proposedTeamSlot = observed.assignmentSafety.proposedTeamSlot,
                        contributingMatchCount = observed.confidenceAssessment.selectedSuggestion
                            ?.teamCandidateScore
                            ?.contributingMatchCount
                            ?: 0,
                        confidenceTier = observed.confidenceAssessment.tier,
                        assignmentSafety = observed.assignmentSafety.safetyStatus,
                        topThree = topThree,
                        correct = observed.identifiedTeamSlot == expected.expectedTeamSlot,
                    )
                }
                val correct = rowMetrics.count { it.correct }
                val unidentified = rowMetrics.count { it.identifiedTeamSlot == null }
                val expectedRankCounts = (1..3).associateWith { rank ->
                    rowMetrics.count { row -> row.topThree.getOrNull(rank - 1) == row.expectedTeamSlot }
                }
                return AcceptanceMetrics(
                    evaluableCount = rowMetrics.size,
                    correctIdentifications = correct,
                    incorrectIdentifications = rowMetrics.size - correct,
                    unidentifiedRows = unidentified,
                    accuracyPercent = if (rowMetrics.isEmpty()) 0.0 else {
                        correct.toDouble() * 100.0 / rowMetrics.size
                    },
                    confidenceTierCounts = rowMetrics.groupingBy { it.confidenceTier }.eachCount(),
                    falseAutomaticAssignments = rowMetrics.count { row ->
                        row.assignmentSafety == TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT &&
                            row.proposedTeamSlot != row.expectedTeamSlot
                    },
                    expectedTeamRankCounts = expectedRankCounts,
                    expectedTeamAbsentFromTopThree = rowMetrics.count { it.expectedTeamSlot !in it.topThree },
                    rows = rowMetrics,
                )
            }
        }
    }

    private data class RowMetric(
        val placementId: Int,
        val expectedTeamSlot: Int,
        val identifiedTeamSlot: Int?,
        val suggestedTeamSlot: Int?,
        val proposedTeamSlot: Int?,
        val contributingMatchCount: Int,
        val confidenceTier: TeamMatchConfidenceTier,
        val assignmentSafety: TeamAssignmentSafetyStatus,
        val topThree: List<Int>,
        val correct: Boolean,
    )

    private class CsvTable private constructor(
        private val headers: List<String>,
        val dataRows: List<Map<Int, String>>,
    ) {
        fun requiredColumn(name: String): Int = headers.indexOfFirst {
            it.equals(name, ignoreCase = true)
        }.takeIf { it >= 0 } ?: error("The approved CSV is missing a required column.")

        companion object {
            fun parse(text: String): CsvTable {
                val rows = parseRows(text)
                require(rows.isNotEmpty())
                val headers = rows.first().map { it.trim().removePrefix("\uFEFF") }
                require(headers.isNotEmpty() && headers.toSet().size == headers.size)
                val dataRows = rows.drop(1)
                    .filter { row -> row.any { it.isNotBlank() } }
                    .map { row ->
                        require(row.size == headers.size)
                        row.mapIndexed { index, value -> index to value }.toMap()
                    }
                return CsvTable(headers, dataRows)
            }

            private fun parseRows(text: String): List<List<String>> {
                val rows = mutableListOf<List<String>>()
                val row = mutableListOf<String>()
                val field = StringBuilder()
                var quoted = false
                var index = 0

                fun finishField() {
                    row += field.toString()
                    field.clear()
                }

                fun finishRow() {
                    finishField()
                    rows += row.toList()
                    row.clear()
                }

                while (index < text.length) {
                    val character = text[index]
                    if (quoted) {
                        when {
                            character == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                                field.append('"')
                                index++
                            }
                            character == '"' -> quoted = false
                            else -> field.append(character)
                        }
                    } else {
                        when (character) {
                            '"' -> quoted = true
                            ',' -> finishField()
                            '\n' -> finishRow()
                            '\r' -> Unit
                            else -> field.append(character)
                        }
                    }
                    index++
                }
                if (quoted) error("The approved CSV contains an unterminated quoted field.")
                if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
                return rows
            }
        }
    }

    private companion object {
        const val ASSET_ROOT = "local-ocr-acceptance/v0.12.8"
        const val SCREENSHOT_A = "match-case-01-a.jpeg"
        const val SCREENSHOT_B = "match-case-01-b.jpeg"
        const val ROSTER = "roster.csv"
        const val GROUND_TRUTH = "ground-truth.csv"
        const val ACCEPTANCE_POLICY = "ACCEPTANCE_POLICY.txt"
        const val DATASET_MANIFEST = "DATASET_MANIFEST.txt"
        const val REPORT_FILE = "v0.12.8-ocr-acceptance-report.txt"
        const val APPROVED_DATASET_STATUS = "APPROVED_FOR_V0_12_8"
        const val CANONICAL_PLACEMENT_COUNT = 12
        const val MAX_CASE_ID_LENGTH = 64
        val CANONICAL_PLACEMENTS = (1..CANONICAL_PLACEMENT_COUNT).toSet()
        val CANONICAL_ROW_INDEXES = (0 until CANONICAL_PLACEMENT_COUNT).toSet()
        val CANONICAL_SCREENSHOTS = setOf(SCREENSHOT_A, SCREENSHOT_B)
        val HASH_PATTERN = Regex("[A-Fa-f0-9]{64}")
    }
}
