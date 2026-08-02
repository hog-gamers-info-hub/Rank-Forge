package com.hoggamers.rankforge.domain.matching

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.layout.FreeFireMaxScoreboardLayout
import com.hoggamers.rankforge.domain.ocr.layout.NormalizedOcrRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardFieldZoneType
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardLayoutDefinition
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardPanelDefinition
import com.hoggamers.rankforge.domain.ocr.layout.ScoreboardRowDefinition
import kotlin.math.roundToInt

data class ScoreboardRowPlayerEvidence(
    val rowIndex: Int,
    val expectedPlacementId: Int,
    val detectedPlayerNames: List<String>,
)

/**
 * Maps one raw-OCR preprocessing candidate into fixed scoreboard rows.
 *
 * A collector instance has no cross-candidate state.  This is intentional: OCR
 * evidence from a scaled or contrast-adjusted candidate must not be silently
 * combined with evidence from another candidate.
 */
class ScoreboardRowPlayerEvidenceCollector(
    private val layout: ScoreboardLayoutDefinition = FreeFireMaxScoreboardLayout.definition,
) {
    fun collect(extraction: RawOcrExtractionResult.Extracted): List<ScoreboardRowPlayerEvidence> {
        val sourceCandidate = extraction.sourceCandidate
        val lines = extraction.blocks.flatMap { it.lines }
        val seenEvidence = mutableSetOf<RawLineKey>()

        return layout.panels
            .flatMap { panel ->
                panel.rows.map { row ->
                    val playerNameZone = requireNotNull(
                        row.fieldZones.singleOrNull { it.type == ScoreboardFieldZoneType.PLAYER_NAME },
                    ) { "Each scoreboard row must define one player-name zone." }
                    val zoneRect = playerNameZoneRect(panel, row, playerNameZone.relativeRect, sourceCandidate)
                    val rowCandidates = lines.mapNotNull { line ->
                        lineCandidate(line, zoneRect)?.takeIf { candidate ->
                            seenEvidence.add(candidate.key)
                        }
                    }
                    ScoreboardRowPlayerEvidence(
                        rowIndex = row.placementId - 1,
                        expectedPlacementId = row.placementId,
                        detectedPlayerNames = rowCandidates.map { it.text },
                    )
                }
            }
            .sortedBy { it.expectedPlacementId }
    }

    fun collect(extraction: RawOcrExtractionResult): List<ScoreboardRowPlayerEvidence> =
        (extraction as? RawOcrExtractionResult.Extracted)?.let(::collect).orEmpty()

    private fun playerNameZoneRect(
        panel: ScoreboardPanelDefinition,
        row: ScoreboardRowDefinition,
        relativeRect: NormalizedOcrRect,
        sourceCandidate: com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCandidate,
    ): OcrPixelRect {
        val rowHeight = panel.contentRect.height / panel.rows.size
        val rowY = panel.contentRect.y + row.rowIndex * rowHeight
        val layoutRect = NormalizedOcrRect(
            x = panel.contentRect.x + panel.contentRect.width * relativeRect.x,
            y = rowY + rowHeight * relativeRect.y,
            width = panel.contentRect.width * relativeRect.width,
            height = rowHeight * relativeRect.height,
        )
        val overall = layout.overallContentRect
        val scale = sourceCandidate.scaleFactor ?: 1.0
        return OcrPixelRect(
            x = (((layoutRect.x - overall.x) / overall.width) *
                sourceCandidate.cropRect.width * scale).roundToInt(),
            y = (((layoutRect.y - overall.y) / overall.height) *
                sourceCandidate.cropRect.height * scale).roundToInt(),
            width = ((layoutRect.width / overall.width) *
                sourceCandidate.cropRect.width * scale).roundToInt(),
            height = ((layoutRect.height / overall.height) *
                sourceCandidate.cropRect.height * scale).roundToInt(),
        )
    }

    private fun lineCandidate(
        line: RawOcrLine,
        zone: OcrPixelRect,
    ): LineCandidate? {
        if (line.elements.isEmpty()) {
            val text = line.text.trim()
            return text
                .takeIf { it.isNotEmpty() && !it.isNumeric() && line.geometry.intersects(zone) }
                ?.let { LineCandidate(it, RawLineKey(listOf(RawEntityKey(it, line.geometry)))) }
        }

        val selectedElements = line.elements
            .mapIndexed { index, element -> IndexedElement(index, element.text.trim(), element.geometry) }
            .filter { element -> element.geometry.intersects(zone) }
            .distinctBy { element -> RawEntityKey(element.text, element.geometry) }
            .sortedWith(
                compareBy<IndexedElement> { it.geometry?.boundingBox?.left ?: Int.MAX_VALUE }
                    .thenBy { it.geometry?.boundingBox?.top ?: Int.MAX_VALUE }
                    .thenBy { it.originalIndex },
            )
        val playerElements = selectedElements
            .filter { it.text.isNotEmpty() && !it.text.isNumeric() }

        if (playerElements.isEmpty()) return null

        val text = playerElements.joinToString(" ") { it.text }
        return LineCandidate(
            text = text,
            key = RawLineKey(
                playerElements.map { element -> RawEntityKey(element.text, element.geometry) },
            ),
        )
    }

    private data class LineCandidate(
        val text: String,
        val key: RawLineKey,
    )

    private data class IndexedElement(
        val originalIndex: Int,
        val text: String,
        val geometry: RawOcrGeometry?,
    )

    private data class RawLineKey(
        val entities: List<RawEntityKey>,
    )

    private data class RawEntityKey(
        val text: String,
        val geometry: RawOcrGeometry?,
    )

    private fun RawOcrGeometry?.intersects(zone: OcrPixelRect): Boolean =
        this?.boundingBox?.let { box ->
            box.left < zone.x + zone.width &&
                box.right > zone.x &&
                box.top < zone.y + zone.height &&
                box.bottom > zone.y
        } == true

    private fun String.isNumeric(): Boolean = matches(NUMERIC_TEXT)

    private companion object {
        val NUMERIC_TEXT = Regex("[+-]?\\d+")
    }
}
