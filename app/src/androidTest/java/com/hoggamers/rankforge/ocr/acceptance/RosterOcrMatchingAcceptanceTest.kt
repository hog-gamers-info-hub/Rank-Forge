package com.hoggamers.rankforge.ocr.acceptance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.ocr.DefaultMlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.extraction.MlKitRawOcrEngineImpl
import com.hoggamers.rankforge.data.ocr.extraction.MlKitRawOcrTextExtractor
import com.hoggamers.rankforge.data.ocr.extraction.MlKitRosterRawOcrEngineImpl
import com.hoggamers.rankforge.data.ocr.extraction.MlKitRosterRawOcrExtractor
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
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionIdentity
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionType
import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterPanelInput
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.parsing.DefaultRosterOcrValidator
import com.hoggamers.rankforge.domain.ocr.parsing.FixedLayoutRosterCandidateParser
import com.hoggamers.rankforge.domain.ocr.parsing.FixedRosterSlotAssociator
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociationInput
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
class RosterOcrMatchingAcceptanceTest {
    @Test
    fun approvedRosterEvidenceIdentifiesTeamsFromGenuineResults() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assetContext = instrumentation.context
        val reportContext = instrumentation.targetContext
        val rosterManifestText = readAssetTextOrNull(
            assetContext,
            ROSTER_ASSET_ROOT,
            ROSTER_DATASET_MANIFEST,
        )
        val resultManifestText = readAssetTextOrNull(
            assetContext,
            RESULT_ASSET_ROOT,
            RESULT_DATASET_MANIFEST,
        )
        assumeTrue(
            "The approved local roster/result acceptance datasets are not installed.",
            rosterManifestText != null && resultManifestText != null,
        )

        val rosterManifest = ManifestData.parse(requireNotNull(rosterManifestText))
        val resultManifest = ManifestData.parse(requireNotNull(resultManifestText))
        require(rosterManifest.required("dataset_status") == ROSTER_APPROVED_STATUS)
        require(rosterManifest.required("case_id") == ROSTER_CASE_ID)
        require(rosterManifest.requiredInt("screenshot_count") == ROSTER_SCREENSHOT_COUNT)
        require(rosterManifest.requiredDouble("slot_association_required_percent") == 100.0)
        require(rosterManifest.requiredInt("false_automatic_assignments_required") == 0)
        val resultCaseId = rosterManifest.required("result_acceptance_case_id")
        require(resultCaseId == RESULT_CASE_ID)

        val rosterScreenshotFiles = (1..ROSTER_SCREENSHOT_COUNT).map { index ->
            val assetName = rosterManifest.required("screenshot_$index")
            require(assetName == ROSTER_SCREENSHOTS[index - 1])
            checkAssetHash(
                context = assetContext,
                assetRoot = ROSTER_ASSET_ROOT,
                assetName = assetName,
                expectedHash = rosterManifest.required("screenshot_${index}_sha256"),
            )
            assetName
        }

        require(resultManifest.required("dataset_status") == RESULT_APPROVED_STATUS)
        val canonicalCandidateOrder = resultManifest.requiredInt("canonical_candidate_order")
        val thresholdPercent = resultManifest.requiredDouble("team_identification_threshold_percent")
        val falseAutomaticAssignmentsRequired =
            resultManifest.requiredInt("false_automatic_assignments_required")
        val resultScreenshotFiles = listOf(
            resultManifest.required("screenshot_a"),
            resultManifest.required("screenshot_b"),
        )
        require(resultScreenshotFiles == RESULT_SCREENSHOTS)
        listOf(
            resultScreenshotFiles[0] to resultManifest.required("screenshot_a_sha256"),
            resultScreenshotFiles[1] to resultManifest.required("screenshot_b_sha256"),
            RESULT_ROSTER_ASSET to resultManifest.required("roster_sha256"),
            RESULT_GROUND_TRUTH to resultManifest.required("ground_truth_sha256"),
            RESULT_ACCEPTANCE_POLICY to resultManifest.required("acceptance_policy_sha256"),
        ).forEach { (assetName, expectedHash) ->
            checkAssetHash(
                context = assetContext,
                assetRoot = RESULT_ASSET_ROOT,
                assetName = assetName,
                expectedHash = expectedHash,
            )
        }

        val groundTruth = GroundTruth.parse(
            readAssetText(assetContext, RESULT_ASSET_ROOT, RESULT_GROUND_TRUTH),
        )
        require(groundTruth.rows.size == CANONICAL_PLACEMENT_COUNT)
        require(groundTruth.rows.map { it.placementId }.toSet() == CANONICAL_PLACEMENTS)
        require(groundTruth.rows.map { it.caseId }.toSet() == setOf(resultCaseId))
        require(groundTruth.rows.count { it.status == EvaluationStatus.EVALUABLE } ==
            resultManifest.requiredInt("evaluable_row_count"))
        require(groundTruth.rows.all { it.screenshotFile in RESULT_SCREENSHOTS })

        val rosterResults = rosterScreenshotFiles.mapIndexed { index, assetName ->
            processRosterScreenshot(
                context = assetContext,
                assetName = assetName,
                screenshotPosition = RosterScreenshotPosition.entries[index],
            )
        }
        val allExtractions = rosterResults.flatMap { it.extractions }
        val parsedCandidates = FixedLayoutRosterCandidateParser().parse(
            RosterCandidateParseInput(allExtractions),
        )
        val associatedRoster = FixedRosterSlotAssociator().associate(
            RosterSlotAssociationInput(parsedCandidates),
        )
        val rosterValidation = DefaultRosterOcrValidator().validate(
            RosterOcrValidationInput(associatedRoster),
        )
        val associatedCandidates = associatedRoster.tournamentSlotCandidates
        val associatedSlotNumbers = associatedCandidates.map { it.tournamentSlotNumber }
        val associatedValidSlots = associatedSlotNumbers.filter { it in CANONICAL_PLACEMENTS }.toSet()
        val slotAssociationPercent = associatedValidSlots.size.toDouble() * 100.0 /
            CANONICAL_PLACEMENT_COUNT
        val slotAssociationIsExact = associatedCandidates.size == CANONICAL_PLACEMENT_COUNT &&
            associatedSlotNumbers.toSet() == CANONICAL_PLACEMENTS &&
            associatedRoster.failures.isEmpty()
        val candidateRoster = associatedCandidates
            .sortedBy { it.tournamentSlotNumber }
            .map { candidate ->
                TeamCandidateRosterInput(
                    teamSlot = candidate.tournamentSlotNumber,
                    rosterPlayerNames = (1..SUPPORTED_PLAYER_ROW_COUNT).map { rowIndex ->
                        candidate.playerNameCandidates
                            .singleOrNull { it.playerRowIndex == rowIndex }
                            ?.takeIf { it.status == RosterCandidateParseStatus.PARSED }
                            ?.candidateText
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                    },
                )
        }
        val allPlayerCandidates = associatedCandidates.flatMap { it.playerNameCandidates }
        val parserInputFailures = parsedCandidates.inputFailures
        val rawExtractionFailures = allExtractions
            .filterIsInstance<RosterRawOcrExtractionResult.Failed>()
        val playerRowStatusCounts = (1..SUPPORTED_PLAYER_ROW_COUNT).associateWith { rowIndex ->
            allPlayerCandidates
                .filter { it.playerRowIndex == rowIndex }
                .groupingBy { it.status.name }
                .eachCount()
        }
        val validationIssues = rosterValidation.issues

        val resultScreenshotResults = resultScreenshotFiles.map { assetName ->
            processResultScreenshot(
                context = assetContext,
                assetName = assetName,
                canonicalCandidateOrder = canonicalCandidateOrder,
            )
        }
        val canonicalEvidence = groundTruth.rows
            .sortedBy { it.placementId }
            .map { groundTruthRow ->
                resultScreenshotResults
                    .first { it.assetName == groundTruthRow.screenshotFile }
                    .rowsByPlacement[groundTruthRow.placementId]
                    ?: emptyEvidence(groundTruthRow.placementId)
            }
        require(canonicalEvidence.size == CANONICAL_PLACEMENT_COUNT)
        val evaluation = ScoreboardTeamIdentificationEvaluator.evaluate(
            rowEvidence = canonicalEvidence,
            candidateTeams = candidateRoster,
        )
        val metrics = AcceptanceMetrics.from(groundTruth.rows, evaluation)
        val teamIdentificationThresholdMet = metrics.accuracyPercent >= thresholdPercent
        val report = AcceptanceReport(
            passed = slotAssociationIsExact &&
                metrics.accuracyPercent >= thresholdPercent &&
                metrics.falseAutomaticAssignments <= falseAutomaticAssignmentsRequired,
            rosterCaseId = rosterManifest.required("case_id"),
            resultCaseId = resultCaseId,
            rosterScreenshotCount = rosterResults.size,
            associatedCandidateCount = associatedCandidates.size,
            associatedRosterSlotCount = associatedValidSlots.size,
            distinctValidAssociatedSlotCount = associatedValidSlots.size,
            associationFailureCount = associatedRoster.failures.size,
            associationFailureTypeCounts = associatedRoster.failures
                .groupingBy { it.type.name }
                .eachCount(),
            parserInputFailureCount = parserInputFailures.size,
            parserInputFailureTypeCounts = parserInputFailures
                .groupingBy { it.name }
                .eachCount(),
            rawExtractionFailureCount = rawExtractionFailures.size,
            rawExtractionFailureTypeCounts = rawExtractionFailures
                .groupingBy { it.failure.name }
                .eachCount(),
            rawExtractionFailureWithRegionIdentityCount = rawExtractionFailures.count {
                it.regionIdentity != null
            },
            rawExtractionFailureWithoutRegionIdentityCount = rawExtractionFailures.count {
                it.regionIdentity == null
            },
            playerRow1StatusCounts = playerRowStatusCounts.getValue(1),
            playerRow2StatusCounts = playerRowStatusCounts.getValue(2),
            playerRow3StatusCounts = playerRowStatusCounts.getValue(3),
            playerRow4StatusCounts = playerRowStatusCounts.getValue(4),
            slotAssociationPercent = slotAssociationPercent,
            playerRowRegionCount = allExtractions.count { result ->
                result.regionIdentityOrNull()?.regionType == RosterRawOcrRegionType.PLAYER_ROW
            },
            parsedPlayerCandidateCount = allPlayerCandidates.count {
                it.status == RosterCandidateParseStatus.PARSED &&
                    !it.candidateText.isNullOrBlank()
            },
            emptyOrMissingPlayerCandidateCount = allPlayerCandidates.count {
                it.status == RosterCandidateParseStatus.EMPTY ||
                    it.status == RosterCandidateParseStatus.MISSING
            },
            failedOrUnusablePlayerCandidateCount = allPlayerCandidates.count {
                it.status != RosterCandidateParseStatus.PARSED &&
                    it.status != RosterCandidateParseStatus.EMPTY &&
                    it.status != RosterCandidateParseStatus.MISSING
            },
            rosterValidationStatus = rosterValidation.status,
            validationIssueCount = validationIssues.size,
            blockingValidationIssueCount = validationIssues.count {
                it.severity == com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationSeverity.BLOCKING
            },
            warningValidationIssueCount = validationIssues.count {
                it.severity == com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationSeverity.WARNING
            },
            infoValidationIssueCount = validationIssues.count {
                it.severity == com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationSeverity.INFO
            },
            validationIssueTypeCounts = validationIssues
                .groupingBy { it.type.name }
                .eachCount(),
            evaluableResultRowCount = metrics.evaluableCount,
            correctTeamIdentifications = metrics.correctIdentifications,
            incorrectTeamIdentifications = metrics.incorrectIdentifications,
            unidentifiedResultRows = metrics.unidentifiedRows,
            teamIdentificationPercent = metrics.accuracyPercent,
            teamIdentificationThresholdPercent = thresholdPercent,
            teamIdentificationThresholdMet = teamIdentificationThresholdMet,
            falseAutomaticAssignments = metrics.falseAutomaticAssignments,
        )
        writeSanitizedReport(reportContext, report)

        check(slotAssociationIsExact)
        check(metrics.accuracyPercent >= thresholdPercent)
        check(metrics.falseAutomaticAssignments <= falseAutomaticAssignmentsRequired)
    }

    private suspend fun processRosterScreenshot(
        context: Context,
        assetName: String,
        screenshotPosition: RosterScreenshotPosition,
    ): RosterScreenshotResult {
        val bitmap = context.assets.open(assetPath(ROSTER_ASSET_ROOT, assetName)).use { input ->
            BitmapFactory.decodeStream(input)
        } ?: error("Unable to decode the approved roster acceptance asset.")
        return try {
            val image = AndroidBitmapOcrImage(bitmap)
            val input = RosterRawOcrExtractionInput(
                croppedPanelImage = image,
                croppedPanelInput = CroppedRosterPanelInput(
                    screenshotPosition = screenshotPosition,
                    isPreparedRosterCrop = true,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                ),
            )
            val extraction = MlKitRosterRawOcrExtractor(
                MlKitRosterRawOcrEngineImpl(DefaultMlKitTextRecognizerFactory()),
            ).extract(input)
            RosterScreenshotResult(
                extractions = extraction,
            )
        } finally {
            bitmap.recycleIfNeeded()
        }
    }

    private suspend fun processResultScreenshot(
        context: Context,
        assetName: String,
        canonicalCandidateOrder: Int,
    ): ResultScreenshotResult {
        val bitmap = context.assets.open(assetPath(RESULT_ASSET_ROOT, assetName)).use { input ->
            BitmapFactory.decodeStream(input)
        } ?: error("Unable to decode the approved result acceptance asset.")
        val sourceImage = AndroidBitmapOcrImage(bitmap)
        var candidates: List<OcrPreprocessingCandidate> = emptyList()

        return try {
            val preprocessingResult = AndroidBitmapOcrImagePreprocessor().preprocess(
                OcrPreprocessingInput(sourceImage),
            )
            candidates = when (preprocessingResult) {
                is OcrPreprocessingResult.Candidates -> preprocessingResult.candidates
                is OcrPreprocessingResult.Failed -> {
                    error("Approved result preprocessing did not produce candidates.")
                }
            }
            val canonicalCandidate = candidates.single { it.order == canonicalCandidateOrder }
            val extraction = MlKitRawOcrTextExtractor(
                MlKitRawOcrEngineImpl(DefaultMlKitTextRecognizerFactory()),
            ).extract(
                RawOcrExtractionInput(listOf(canonicalCandidate)),
            ).single()
            val rowsByPlacement = when (extraction) {
                is RawOcrExtractionResult.Extracted -> ScoreboardRowPlayerEvidenceCollector()
                    .collect(extraction)
                    .associateBy { it.expectedPlacementId }
                is RawOcrExtractionResult.Empty,
                is RawOcrExtractionResult.Failed,
                -> emptyMap()
            }
            ResultScreenshotResult(assetName = assetName, rowsByPlacement = rowsByPlacement)
        } finally {
            candidates.forEach { candidate ->
                (candidate.image as? AndroidBitmapOcrImage)?.bitmap?.recycleIfNeeded()
            }
            bitmap.recycleIfNeeded()
        }
    }

    private fun writeSanitizedReport(context: Context, report: AcceptanceReport) {
        val text = buildString {
            appendLine("status=${if (report.passed) "PASS" else "FAIL"}")
            appendLine("roster_case_id=${sanitizeCaseId(report.rosterCaseId)}")
            appendLine("result_case_id=${sanitizeCaseId(report.resultCaseId)}")
            appendLine("roster_screenshot_count=${report.rosterScreenshotCount}")
            appendLine("associated_candidate_count=${report.associatedCandidateCount}")
            appendLine("associated_roster_slot_count=${report.associatedRosterSlotCount}")
            appendLine(
                "distinct_valid_associated_slot_count=${report.distinctValidAssociatedSlotCount}",
            )
            appendLine("association_failure_count=${report.associationFailureCount}")
            appendLine(
                "association_failure_type_counts=${formatTypeCounts(report.associationFailureTypeCounts)}",
            )
            appendLine("parser_input_failure_count=${report.parserInputFailureCount}")
            appendLine(
                "parser_input_failure_type_counts=${formatTypeCounts(report.parserInputFailureTypeCounts)}",
            )
            appendLine("raw_extraction_failure_count=${report.rawExtractionFailureCount}")
            appendLine(
                "raw_extraction_failure_type_counts=${formatTypeCounts(report.rawExtractionFailureTypeCounts)}",
            )
            appendLine(
                "raw_extraction_failure_with_region_identity_count=" +
                    report.rawExtractionFailureWithRegionIdentityCount,
            )
            appendLine(
                "raw_extraction_failure_without_region_identity_count=" +
                    report.rawExtractionFailureWithoutRegionIdentityCount,
            )
            appendLine(
                "player_row_1_status_counts=${formatTypeCounts(report.playerRow1StatusCounts)}",
            )
            appendLine(
                "player_row_2_status_counts=${formatTypeCounts(report.playerRow2StatusCounts)}",
            )
            appendLine(
                "player_row_3_status_counts=${formatTypeCounts(report.playerRow3StatusCounts)}",
            )
            appendLine(
                "player_row_4_status_counts=${formatTypeCounts(report.playerRow4StatusCounts)}",
            )
            appendLine("slot_association_percent=${formatPercent(report.slotAssociationPercent)}")
            appendLine("player_row_region_count=${report.playerRowRegionCount}")
            appendLine("parsed_player_candidate_count=${report.parsedPlayerCandidateCount}")
            appendLine("empty_or_missing_player_candidate_count=${report.emptyOrMissingPlayerCandidateCount}")
            appendLine("failed_or_unusable_player_candidate_count=${report.failedOrUnusablePlayerCandidateCount}")
            appendLine("roster_validation_status=${report.rosterValidationStatus}")
            appendLine("validation_issue_count=${report.validationIssueCount}")
            appendLine("blocking_validation_issue_count=${report.blockingValidationIssueCount}")
            appendLine("warning_validation_issue_count=${report.warningValidationIssueCount}")
            appendLine("info_validation_issue_count=${report.infoValidationIssueCount}")
            appendLine(
                "validation_issue_type_counts=${formatTypeCounts(report.validationIssueTypeCounts)}",
            )
            appendLine("evaluable_result_row_count=${report.evaluableResultRowCount}")
            appendLine("correct_team_identifications=${report.correctTeamIdentifications}")
            appendLine("incorrect_team_identifications=${report.incorrectTeamIdentifications}")
            appendLine("unidentified_result_rows=${report.unidentifiedResultRows}")
            appendLine("team_identification_percent=${formatPercent(report.teamIdentificationPercent)}")
            appendLine(
                "team_identification_threshold_percent=${formatPercent(report.teamIdentificationThresholdPercent)}",
            )
            appendLine("team_identification_threshold_met=${report.teamIdentificationThresholdMet}")
            appendLine("false_automatic_assignments=${report.falseAutomaticAssignments}")
        }
        println("RANK_FORGE_ROSTER_OCR_MATCHING_ACCEPTANCE_REPORT_BEGIN")
        print(text)
        println("RANK_FORGE_ROSTER_OCR_MATCHING_ACCEPTANCE_REPORT_END")
        context.openFileOutput(REPORT_FILE, Context.MODE_PRIVATE).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    private fun checkAssetHash(
        context: Context,
        assetRoot: String,
        assetName: String,
        expectedHash: String,
    ) {
        val normalizedExpectedHash = expectedHash.lowercase(Locale.US)
        require(HASH_PATTERN.matches(normalizedExpectedHash))
        check(sha256(context, assetRoot, assetName) == normalizedExpectedHash)
    }

    private fun sha256(context: Context, assetRoot: String, assetName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(assetPath(assetRoot, assetName)).use { input ->
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

    private fun readAssetText(context: Context, assetRoot: String, assetName: String): String =
        context.assets.open(assetPath(assetRoot, assetName)).use { input ->
            InputStreamReader(input, Charsets.UTF_8).use { it.readText() }
        }

    private fun readAssetTextOrNull(
        context: Context,
        assetRoot: String,
        assetName: String,
    ): String? = try {
        readAssetText(context, assetRoot, assetName)
    } catch (_: FileNotFoundException) {
        null
    }

    private fun assetPath(assetRoot: String, assetName: String): String = "$assetRoot/$assetName"

    private fun emptyEvidence(placementId: Int) = ScoreboardRowPlayerEvidence(
        rowIndex = placementId - 1,
        expectedPlacementId = placementId,
        detectedPlayerNames = emptyList(),
    )

    private fun RosterRawOcrExtractionResult.regionIdentityOrNull(): RosterRawOcrRegionIdentity? =
        when (this) {
            is RosterRawOcrExtractionResult.Extracted -> evidence.regionIdentity
            is RosterRawOcrExtractionResult.Empty -> regionIdentity
            is RosterRawOcrExtractionResult.Failed -> regionIdentity
        }

    private fun Bitmap.recycleIfNeeded() {
        if (!isRecycled) recycle()
    }

    private fun sanitizeCaseId(caseId: String): String =
        caseId.filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
            .take(MAX_CASE_ID_LENGTH)
            .ifEmpty { "UNKNOWN" }

    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun formatTypeCounts(counts: Map<String, Int>): String = counts.toSortedMap()
        .entries
        .joinToString(",") { (type, count) -> "$type=$count" }

    private data class RosterScreenshotResult(
        val extractions: List<RosterRawOcrExtractionResult>,
    )

    private data class ResultScreenshotResult(
        val assetName: String,
        val rowsByPlacement: Map<Int, ScoreboardRowPlayerEvidence>,
    )

    private data class AcceptanceReport(
        val passed: Boolean,
        val rosterCaseId: String,
        val resultCaseId: String,
        val rosterScreenshotCount: Int,
        val associatedRosterSlotCount: Int,
        val associatedCandidateCount: Int,
        val distinctValidAssociatedSlotCount: Int,
        val associationFailureCount: Int,
        val associationFailureTypeCounts: Map<String, Int>,
        val parserInputFailureCount: Int,
        val parserInputFailureTypeCounts: Map<String, Int>,
        val rawExtractionFailureCount: Int,
        val rawExtractionFailureTypeCounts: Map<String, Int>,
        val rawExtractionFailureWithRegionIdentityCount: Int,
        val rawExtractionFailureWithoutRegionIdentityCount: Int,
        val playerRow1StatusCounts: Map<String, Int>,
        val playerRow2StatusCounts: Map<String, Int>,
        val playerRow3StatusCounts: Map<String, Int>,
        val playerRow4StatusCounts: Map<String, Int>,
        val slotAssociationPercent: Double,
        val playerRowRegionCount: Int,
        val parsedPlayerCandidateCount: Int,
        val emptyOrMissingPlayerCandidateCount: Int,
        val failedOrUnusablePlayerCandidateCount: Int,
        val rosterValidationStatus: RosterOcrValidationStatus,
        val validationIssueCount: Int,
        val blockingValidationIssueCount: Int,
        val warningValidationIssueCount: Int,
        val infoValidationIssueCount: Int,
        val validationIssueTypeCounts: Map<String, Int>,
        val evaluableResultRowCount: Int,
        val correctTeamIdentifications: Int,
        val incorrectTeamIdentifications: Int,
        val unidentifiedResultRows: Int,
        val teamIdentificationPercent: Double,
        val teamIdentificationThresholdPercent: Double,
        val teamIdentificationThresholdMet: Boolean,
        val falseAutomaticAssignments: Int,
    )

    private data class AcceptanceMetrics(
        val evaluableCount: Int,
        val correctIdentifications: Int,
        val incorrectIdentifications: Int,
        val unidentifiedRows: Int,
        val accuracyPercent: Double,
        val falseAutomaticAssignments: Int,
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
                    RowMetric(
                        expectedTeamSlot = requireNotNull(expected.expectedTeamSlot),
                        identifiedTeamSlot = observed.identifiedTeamSlot,
                        assignmentSafety = observed.assignmentSafety.safetyStatus,
                        proposedTeamSlot = observed.assignmentSafety.proposedTeamSlot,
                    )
                }
                val correct = rowMetrics.count { it.identifiedTeamSlot == it.expectedTeamSlot }
                val unidentified = rowMetrics.count { it.identifiedTeamSlot == null }
                return AcceptanceMetrics(
                    evaluableCount = rowMetrics.size,
                    correctIdentifications = correct,
                    incorrectIdentifications = rowMetrics.size - correct,
                    unidentifiedRows = unidentified,
                    accuracyPercent = if (rowMetrics.isEmpty()) 0.0 else {
                        correct.toDouble() * 100.0 / rowMetrics.size
                    },
                    falseAutomaticAssignments = rowMetrics.count { row ->
                        row.assignmentSafety == TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT &&
                            row.proposedTeamSlot != row.expectedTeamSlot
                    },
                )
            }
        }
    }

    private data class RowMetric(
        val expectedTeamSlot: Int,
        val identifiedTeamSlot: Int?,
        val assignmentSafety: TeamAssignmentSafetyStatus,
        val proposedTeamSlot: Int?,
    )

    private data class ManifestData(val properties: Map<String, String>) {
        fun required(name: String): String =
            properties.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
                ?.value
                ?.takeIf { it.isNotBlank() }
                ?: error("The approved acceptance manifest is missing a required value.")

        fun requiredInt(name: String): Int = required(name).toInt()

        fun requiredDouble(name: String): Double = required(name).toDouble()

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
                        trimmed.substring(0, separator).trim() to
                            trimmed.substring(separator + 1).trim().trim('"')
                    }
                }.toMap()
                return ManifestData(properties)
            }
        }
    }

    private data class GroundTruth(val rows: List<GroundTruthRow>) {
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

    private class CsvTable private constructor(
        private val headers: List<String>,
        val dataRows: List<Map<Int, String>>,
    ) {
        fun requiredColumn(name: String): Int = headers.indexOfFirst {
            it.equals(name, ignoreCase = true)
        }.takeIf { it >= 0 } ?: error("The approved acceptance CSV is missing a required column.")

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
                if (quoted) error("The approved acceptance CSV contains an unterminated field.")
                if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
                return rows
            }
        }
    }

    private companion object {
        const val ROSTER_ASSET_ROOT = "local-roster-ocr-acceptance/phase-12"
        const val RESULT_ASSET_ROOT = "local-ocr-acceptance/v0.12.8"
        const val ROSTER_DATASET_MANIFEST = "DATASET_MANIFEST.txt"
        const val RESULT_DATASET_MANIFEST = "DATASET_MANIFEST.txt"
        const val RESULT_GROUND_TRUTH = "ground-truth.csv"
        const val RESULT_ROSTER_ASSET = "roster.csv"
        const val RESULT_ACCEPTANCE_POLICY = "ACCEPTANCE_POLICY.txt"
        const val REPORT_FILE = "phase-12-roster-ocr-matching-acceptance-report.txt"
        const val ROSTER_APPROVED_STATUS = "APPROVED_FOR_PHASE_12_ROSTER_OCR_MATCHING"
        const val RESULT_APPROVED_STATUS = "APPROVED_FOR_V0_12_8"
        const val ROSTER_CASE_ID = "roster-case-01"
        const val RESULT_CASE_ID = "match-case-01"
        const val ROSTER_SCREENSHOT_COUNT = 3
        const val SUPPORTED_PLAYER_ROW_COUNT = 4
        const val CANONICAL_PLACEMENT_COUNT = 12
        const val MAX_CASE_ID_LENGTH = 64
        val ROSTER_SCREENSHOTS = listOf(
            "roster-case-01-a.jpeg",
            "roster-case-01-b.jpeg",
            "roster-case-01-c.jpeg",
        )
        val RESULT_SCREENSHOTS = listOf("match-case-01-a.jpeg", "match-case-01-b.jpeg")
        val CANONICAL_PLACEMENTS = (1..CANONICAL_PLACEMENT_COUNT).toSet()
        val HASH_PATTERN = Regex("[A-Fa-f0-9]{64}")
    }
}
