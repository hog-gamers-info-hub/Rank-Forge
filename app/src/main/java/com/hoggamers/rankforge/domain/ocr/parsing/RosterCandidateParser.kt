package com.hoggamers.rankforge.domain.ocr.parsing

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionIdentity
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionType
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition

enum class RosterCandidateParseStatus {
    PARSED,
    EMPTY,
    MISSING,
    AMBIGUOUS,
    DUPLICATE,
    MALFORMED,
    UNCERTAIN,
    UNSUPPORTED,
    INPUT_FAILURE,
}

enum class RosterCandidateParseFailure {
    EMPTY_TEXT,
    MISSING_EVIDENCE,
    MULTIPLE_FRAGMENTS,
    DUPLICATE_TEXT,
    UNSUPPORTED_TEAM_NAME_REGION,
    UNSUPPORTED_PLAYER_ROW,
    RAW_EXTRACTION_FAILURE,
    MISSING_ROSTER_METADATA,
}

data class RosterCandidateParseInput(
    val extractions: List<RosterRawOcrExtractionResult>,
)

data class RosterCandidateParseResult(
    val slots: List<RosterSlotCandidate>,
    val inputFailures: List<RosterCandidateParseFailure>,
)

data class RosterSlotCandidate(
    val screenshotPosition: RosterScreenshotPosition,
    val visibleSlotPosition: RosterVisibleSlotPosition,
    val intendedTournamentSlotRange: IntRange,
    val intendedTournamentSlot: Int,
    val teamNameCandidate: RosterTeamNameCandidate,
    val playerNameCandidates: List<RosterPlayerNameCandidate>,
)

data class RosterTeamNameCandidate(
    val status: RosterCandidateParseStatus,
    val failure: RosterCandidateParseFailure,
    val rawSourceResults: List<RosterRawOcrExtractionResult>,
    val confidence: RawOcrConfidence,
)

data class RosterPlayerNameCandidate(
    val regionIdentity: RosterRawOcrRegionIdentity,
    val playerRowIndex: Int,
    val status: RosterCandidateParseStatus,
    val candidateText: String?,
    val failure: RosterCandidateParseFailure?,
    val rawSourceResults: List<RosterRawOcrExtractionResult>,
    val confidence: RawOcrConfidence,
)

interface RosterCandidateParser {
    fun parse(input: RosterCandidateParseInput): RosterCandidateParseResult
}

/**
 * Parses only the v0.8.10 roster player-row evidence. Team-name parsing is deliberately
 * unavailable because the approved layout has no dedicated team-name region.
 */
class FixedLayoutRosterCandidateParser : RosterCandidateParser {
    override fun parse(input: RosterCandidateParseInput): RosterCandidateParseResult {
        val slotMetadata = input.extractions
            .mapNotNull { it.regionIdentityOrNull() }
            .map { identity ->
                RosterSlotMetadata(identity.screenshotPosition, identity.visibleSlotPosition)
            }
            .distinct()
            .sortedWith(
                compareBy<RosterSlotMetadata> { it.screenshotPosition.index }
                    .thenBy { it.visibleSlotPosition.offset },
            )

        return RosterCandidateParseResult(
            slots = slotMetadata.map { metadata -> parseSlot(metadata, input.extractions) },
            inputFailures = input.extractions.inputFailures(),
        )
    }

    private fun parseSlot(
        metadata: RosterSlotMetadata,
        extractions: List<RosterRawOcrExtractionResult>,
    ): RosterSlotCandidate {
        val teamSources = extractions.forSlot(metadata)
            .filter { it.regionIdentityOrNull()?.regionType == RosterRawOcrRegionType.SLOT_CONTENT }
        val playerCandidates = SUPPORTED_PLAYER_ROWS.map { rowIndex ->
            val identity = RosterRawOcrRegionIdentity(
                screenshotPosition = metadata.screenshotPosition,
                visibleSlotPosition = metadata.visibleSlotPosition,
                regionType = RosterRawOcrRegionType.PLAYER_ROW,
                playerRowIndex = rowIndex,
            )
            parsePlayerRow(identity, extractions.filter { it.regionIdentityOrNull() == identity })
        }

        return RosterSlotCandidate(
            screenshotPosition = metadata.screenshotPosition,
            visibleSlotPosition = metadata.visibleSlotPosition,
            intendedTournamentSlotRange = metadata.screenshotPosition.tournamentSlotRange,
            intendedTournamentSlot = metadata.screenshotPosition.tournamentSlotFor(
                metadata.visibleSlotPosition,
            ),
            teamNameCandidate = RosterTeamNameCandidate(
                status = RosterCandidateParseStatus.UNSUPPORTED,
                failure = RosterCandidateParseFailure.UNSUPPORTED_TEAM_NAME_REGION,
                rawSourceResults = teamSources,
                confidence = RawOcrConfidence.Unavailable,
            ),
            playerNameCandidates = playerCandidates,
        )
    }

    private fun parsePlayerRow(
        identity: RosterRawOcrRegionIdentity,
        sources: List<RosterRawOcrExtractionResult>,
    ): RosterPlayerNameCandidate {
        val extractedSources = sources.filterIsInstance<RosterRawOcrExtractionResult.Extracted>()
        val failedSources = sources.filterIsInstance<RosterRawOcrExtractionResult.Failed>()
        val emptySources = sources.filterIsInstance<RosterRawOcrExtractionResult.Empty>()
        val texts = extractedSources.map { it.evidence.rawText.trim() }
        val distinctTexts = texts.distinct()
        val statusAndFailure = when {
            failedSources.isNotEmpty() ->
                RosterCandidateParseStatus.INPUT_FAILURE to
                    RosterCandidateParseFailure.RAW_EXTRACTION_FAILURE
            extractedSources.isEmpty() && emptySources.isNotEmpty() ->
                RosterCandidateParseStatus.EMPTY to RosterCandidateParseFailure.EMPTY_TEXT
            extractedSources.isEmpty() ->
                RosterCandidateParseStatus.MISSING to RosterCandidateParseFailure.MISSING_EVIDENCE
            distinctTexts.all { it.isEmpty() } ->
                RosterCandidateParseStatus.EMPTY to RosterCandidateParseFailure.EMPTY_TEXT
            distinctTexts.size > 1 ->
                RosterCandidateParseStatus.AMBIGUOUS to RosterCandidateParseFailure.MULTIPLE_FRAGMENTS
            extractedSources.size > 1 ->
                RosterCandidateParseStatus.DUPLICATE to RosterCandidateParseFailure.DUPLICATE_TEXT
            else -> RosterCandidateParseStatus.PARSED to null
        }

        return RosterPlayerNameCandidate(
            regionIdentity = identity,
            playerRowIndex = requireNotNull(identity.playerRowIndex),
            status = statusAndFailure.first,
            candidateText = texts.singleOrNull()?.takeIf { statusAndFailure.first == RosterCandidateParseStatus.PARSED },
            failure = statusAndFailure.second,
            rawSourceResults = sources,
            confidence = extractedSources.sharedConfidence(),
        )
    }

    private fun List<RosterRawOcrExtractionResult>.inputFailures(): List<RosterCandidateParseFailure> =
        buildList {
            if (isEmpty()) {
                add(RosterCandidateParseFailure.MISSING_ROSTER_METADATA)
            }
            if (any { it is RosterRawOcrExtractionResult.Failed && it.regionIdentity == null }) {
                add(RosterCandidateParseFailure.RAW_EXTRACTION_FAILURE)
            }
        }

    private fun List<RosterRawOcrExtractionResult>.forSlot(
        metadata: RosterSlotMetadata,
    ): List<RosterRawOcrExtractionResult> = filter { result ->
        result.regionIdentityOrNull()?.let { identity ->
            identity.screenshotPosition == metadata.screenshotPosition &&
                identity.visibleSlotPosition == metadata.visibleSlotPosition
        } == true
    }

    private fun RosterRawOcrExtractionResult.regionIdentityOrNull(): RosterRawOcrRegionIdentity? = when (this) {
        is RosterRawOcrExtractionResult.Extracted -> evidence.regionIdentity
        is RosterRawOcrExtractionResult.Empty -> regionIdentity
        is RosterRawOcrExtractionResult.Failed -> regionIdentity
    }

    private fun List<RosterRawOcrExtractionResult.Extracted>.sharedConfidence(): RawOcrConfidence {
        val confidences = flatMap { extracted ->
            extracted.evidence.rawEvidence.map { evidence -> evidence.confidence }
        }.distinct()
        return confidences.singleOrNull() ?: RawOcrConfidence.Unavailable
    }

    private data class RosterSlotMetadata(
        val screenshotPosition: RosterScreenshotPosition,
        val visibleSlotPosition: RosterVisibleSlotPosition,
    )

    private companion object {
        val SUPPORTED_PLAYER_ROWS = 1..4
    }
}
