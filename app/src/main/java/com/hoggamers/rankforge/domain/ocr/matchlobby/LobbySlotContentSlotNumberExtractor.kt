package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionEvidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionType
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseFailure
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotNumberCandidate

object LobbySlotContentSlotNumberExtractor {
    fun derive(
        results: List<RosterRawOcrExtractionResult>,
    ): Map<RosterVisibleSlotPosition, RosterSlotNumberCandidate> = results
        .filter { result ->
            result.regionIdentityOrNull()?.regionType == RosterRawOcrRegionType.SLOT_CONTENT
        }
        .groupBy { result -> requireNotNull(result.regionIdentityOrNull()).visibleSlotPosition }
        .mapValues { (_, sources) -> deriveCandidate(sources) }

    private fun deriveCandidate(
        sources: List<RosterRawOcrExtractionResult>,
    ): RosterSlotNumberCandidate {
        val extractedSources = sources.filterIsInstance<RosterRawOcrExtractionResult.Extracted>()
        val selectedEvidence = extractedSources
            .map { it.evidence }
            .flatMap { evidence -> evidence.selectHighestPriorityEvidence() }
        val validNumbers = selectedEvidence.mapNotNull { evidence ->
            evidence.canonicalNumberOrNull()?.let { number -> number to evidence.confidence }
        }
        val distinctNumbers = validNumbers.map { it.first }.distinct()
        val confidence = validNumbers.map { it.second }.sharedConfidence()

        return when {
            distinctNumbers.size > 1 -> RosterSlotNumberCandidate(
                status = RosterCandidateParseStatus.AMBIGUOUS,
                detectedSlotNumber = null,
                failure = RosterCandidateParseFailure.MULTIPLE_FRAGMENTS,
                rawSourceResults = sources,
                confidence = confidence,
            )
            distinctNumbers.size == 1 -> RosterSlotNumberCandidate(
                status = RosterCandidateParseStatus.PARSED,
                detectedSlotNumber = distinctNumbers.single(),
                failure = null,
                rawSourceResults = sources,
                confidence = confidence,
            )
            else -> RosterSlotNumberCandidate(
                status = RosterCandidateParseStatus.MISSING,
                detectedSlotNumber = null,
                failure = RosterCandidateParseFailure.MISSING_EVIDENCE,
                rawSourceResults = sources,
                confidence = RawOcrConfidence.Unavailable,
            )
        }
    }

    private fun RosterRawOcrRegionEvidence.selectHighestPriorityEvidence(): List<NumericEvidence> {
        val elementEvidence = blocks.flatMap { block ->
            block.lines.flatMap { line ->
                line.elements.map { element -> element.numericEvidence(regionWidth) }
            }
        }
        if (elementEvidence.any { it != null }) return elementEvidence.filterNotNull()

        val lineEvidence = blocks.flatMap { block ->
            block.lines.map { line -> line.numericEvidence(regionWidth) }
        }
        if (lineEvidence.any { it != null }) return lineEvidence.filterNotNull()

        return blocks.map { block -> block.numericEvidence(regionWidth) }.filterNotNull()
    }

    private fun RawOcrElement.numericEvidence(regionWidth: Int): NumericEvidence? =
        NumericEvidence(text = text, geometry = geometry, confidence = confidence, regionWidth = regionWidth)
            .takeIf { it.isUsable() }

    private fun RawOcrLine.numericEvidence(regionWidth: Int): NumericEvidence? =
        NumericEvidence(text = text, geometry = geometry, confidence = confidence, regionWidth = regionWidth)
            .takeIf { it.isUsable() }

    private fun RawOcrBlock.numericEvidence(regionWidth: Int): NumericEvidence? =
        NumericEvidence(text = text, geometry = geometry, confidence = confidence, regionWidth = regionWidth)
            .takeIf { it.isUsable() }

    private fun NumericEvidence.isUsable(): Boolean = canonicalNumberOrNull() != null

    private fun NumericEvidence.canonicalNumberOrNull(): Int? {
        val boundingBox = geometry?.boundingBox ?: return null
        val width = regionWidth
        if (width <= 0) return null
        val centerX = (boundingBox.left + boundingBox.right) / 2.0
        val normalizedCenterX = centerX / width
        if (normalizedCenterX !in 0.0..MAX_SLOT_NUMBER_GUTTER_FRACTION) return null
        return CANONICAL_SLOT_NUMBERS.firstOrNull { number -> number.toString() == text.trim() }
    }

    private fun List<RawOcrConfidence>.sharedConfidence(): RawOcrConfidence =
        distinct().singleOrNull() ?: RawOcrConfidence.Unavailable

    private data class NumericEvidence(
        val text: String,
        val geometry: com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry?,
        val confidence: RawOcrConfidence,
        val regionWidth: Int,
    )

    private fun RosterRawOcrExtractionResult.regionIdentityOrNull() = when (this) {
        is RosterRawOcrExtractionResult.Extracted -> evidence.regionIdentity
        is RosterRawOcrExtractionResult.Empty -> regionIdentity
        is RosterRawOcrExtractionResult.Failed -> regionIdentity
    }

    private const val MAX_SLOT_NUMBER_GUTTER_FRACTION = 0.15
    private val CANONICAL_SLOT_NUMBERS = 1..12
}
