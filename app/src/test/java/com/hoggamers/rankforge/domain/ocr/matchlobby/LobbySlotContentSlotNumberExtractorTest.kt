package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrEvidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionEvidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionIdentity
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionType
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LobbySlotContentSlotNumberExtractorTest {
    @Test
    fun elementNineInsideLeftGutterBecomesParsedCandidate() {
        val candidate = derive(element("9", 10, 20)).singleCandidate()

        assertEquals(RosterCandidateParseStatus.PARSED, candidate.status)
        assertEquals(9, candidate.detectedSlotNumber)
    }

    @Test
    fun elementTenInsideLeftGutterBecomesParsedCandidate() {
        val candidate = derive(element("10", 5, 10)).singleCandidate()

        assertEquals(RosterCandidateParseStatus.PARSED, candidate.status)
        assertEquals(10, candidate.detectedSlotNumber)
    }

    @Test
    fun playerNameDigitsOutsideGutterAreNotSlotCandidates() {
        val candidate = derive(element("Achint09!", 80, 95)).singleCandidate()

        assertEquals(RosterCandidateParseStatus.MISSING, candidate.status)
        assertNull(candidate.detectedSlotNumber)
    }

    @Test
    fun canonicalDigitsOutsideGutterAreNotAccepted() {
        val candidate = derive(element("10", 80, 95)).singleCandidate()

        assertEquals(RosterCandidateParseStatus.MISSING, candidate.status)
        assertNull(candidate.detectedSlotNumber)
    }

    @Test
    fun gutterElevenWinsWithoutUsingDigitsOutsideTheGutter() {
        val candidate = derive(
            element("11", 5, 10),
            element("10", 80, 95),
            element("J_76", 80, 95),
        ).singleCandidate()

        assertEquals(RosterCandidateParseStatus.PARSED, candidate.status)
        assertEquals(11, candidate.detectedSlotNumber)
    }

    @Test
    fun differentCanonicalNumbersInsideGutterRemainAmbiguous() {
        val candidate = derive(
            element("9", 10, 20),
            element("10", 5, 10),
        ).singleCandidate()

        assertEquals(RosterCandidateParseStatus.AMBIGUOUS, candidate.status)
        assertNull(candidate.detectedSlotNumber)
    }

    @Test
    fun missingGeometryIsNotAcceptedAsAuthoritativeEvidence() {
        val candidate = derive(element("9", null, null)).singleCandidate()

        assertEquals(RosterCandidateParseStatus.MISSING, candidate.status)
        assertNull(candidate.detectedSlotNumber)
    }

    @Test
    fun elementPriorityPrecedesLineAndBlockFallback() {
        val candidate = deriveResults(
            extracted(
                blocks = listOf(
                    block(
                        text = "8",
                        line = line(
                            text = "8",
                            elements = listOf(element("9", 10, 20)),
                        ),
                        geometry = geometry(10, 20),
                    ),
                ),
            ),
        ).singleCandidate()

        assertEquals(RosterCandidateParseStatus.PARSED, candidate.status)
        assertEquals(9, candidate.detectedSlotNumber)
    }

    private fun derive(vararg elements: RawOcrElement): Map<RosterVisibleSlotPosition, com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotNumberCandidate> =
        deriveResults(
            extracted(
                blocks = listOf(
                    block(
                        text = elements.joinToString(" ") { it.text },
                        line = line(
                            elements.joinToString(" ") { it.text },
                            elements.toList(),
                            geometry = elements.firstOrNull()?.geometry,
                        ),
                        geometry = elements.firstOrNull()?.geometry,
                    ),
                ),
            ),
        )

    private fun deriveResults(
        vararg results: RosterRawOcrExtractionResult,
    ) = LobbySlotContentSlotNumberExtractor.derive(results.toList())

    private fun Map<RosterVisibleSlotPosition, com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotNumberCandidate>.singleCandidate() =
        getValue(RosterVisibleSlotPosition.TOP_LEFT)

    private fun extracted(
        blocks: List<RawOcrBlock>,
    ) = RosterRawOcrExtractionResult.Extracted(
        RosterRawOcrRegionEvidence(
            regionIdentity = RosterRawOcrRegionIdentity(
                screenshotPosition = RosterScreenshotPosition.THREE,
                visibleSlotPosition = RosterVisibleSlotPosition.TOP_LEFT,
                regionType = RosterRawOcrRegionType.SLOT_CONTENT,
            ),
            rawText = blocks.joinToString("\n") { it.text },
            blocks = blocks,
            rawEvidence = emptyList<RosterRawOcrEvidence>(),
            regionWidth = 100,
            regionHeight = 100,
        ),
    )

    private fun element(text: String, left: Int?, right: Int?): RawOcrElement = RawOcrElement(
        text = text,
        geometry = if (left == null || right == null) null else geometry(left, right),
        recognizedLanguage = null,
        confidence = RawOcrConfidence.Unavailable,
    )

    private fun block(
        text: String,
        line: RawOcrLine,
        geometry: RawOcrGeometry? = line.geometry,
    ) = RawOcrBlock(
        text = text,
        geometry = geometry,
        recognizedLanguage = null,
        confidence = RawOcrConfidence.Unavailable,
        lines = listOf(line),
    )

    private fun line(
        text: String,
        elements: List<RawOcrElement>,
        geometry: RawOcrGeometry? = null,
    ) = RawOcrLine(
        text = text,
        geometry = geometry,
        recognizedLanguage = null,
        confidence = RawOcrConfidence.Unavailable,
        elements = elements,
    )

    private fun geometry(left: Int, right: Int) = RawOcrGeometry(
        boundingBox = RawOcrBoundingBox(left, 10, right, 20),
        cornerPoints = null,
    )
}
