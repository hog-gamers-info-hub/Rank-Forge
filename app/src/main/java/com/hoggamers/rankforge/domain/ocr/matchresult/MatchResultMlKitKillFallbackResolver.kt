package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox

/** Recovers only unresolved kill fields from already-retained ML Kit observations. */
class MatchResultMlKitKillFallbackResolver {
    fun resolve(
        positionCrop: MatchResultPositionCrop,
        rowCrops: List<MatchResultPositionRowCrop>,
        currentPpSemantic: MatchResultPositionSemanticResult,
        evidence: MatchResultAutoCropEvidence,
    ): Map<Int, MatchResultNumericVerification> {
        val killFields = currentPpSemantic.fields
            .asSequence()
            .filter { it.type == MatchResultOcrFieldType.KILL && it.slot != null }
            .associateBy { it.slot!! }
        return (1..4).mapNotNull { slot ->
            val kill = killFields[slot] ?: return@mapNotNull null
            if (kill.resolvedText.isNotBlank()) return@mapNotNull null

            val rowBounds = rowCrops.firstOrNull {
                it.rowIndex == MatchResultKillFieldLayout.rowIndexForSlot(slot)
            }?.bounds ?: return@mapNotNull null
            val killBounds = MatchResultKillFieldLayout.bounds(
                position = positionCrop.position,
                cropWidth = positionCrop.bounds.width,
                rowBounds = rowBounds,
                firstPlayerColumn = slot == 1 || slot == 2,
            )
            val observations = evidence.observations
                .mapNotNull { observation ->
                    val bounds = observation.boundingBox ?: return@mapNotNull null
                    val localBounds = bounds.toLocal(positionCrop.bounds)
                    if (localBounds.centerX() >= killBounds.left &&
                        localBounds.centerX() < killBounds.right &&
                        localBounds.centerY() >= killBounds.top &&
                        localBounds.centerY() < killBounds.bottom &&
                        observation.text.trim().isNotEmpty()
                    ) {
                        LocalObservation(observation.text.trim(), localBounds)
                    } else {
                        null
                    }
                }
                .sortedWith(compareBy<LocalObservation> { it.bounds.centerX() }.thenBy { it.bounds.centerY() })

            resolveCell(observations)?.let { verification -> slot to verification }
        }.toMap()
    }

    private fun resolveCell(observations: List<LocalObservation>): MatchResultNumericVerification? {
        if (observations.isEmpty()) return null

        val candidates = buildList {
            parseValue(observations.joinToString(" ") { it.text })?.let { add(it) }
            observations.forEach { observation ->
                parseStrongValue(observation.text)?.let { add(it) }
            }
        }.distinctBy { it.rawText to it.value }
        val values = candidates.mapNotNull { it.value }.distinct()
        return when (values.size) {
            0 -> null
            1 -> MatchResultNumericVerification.Verified(values.single(), candidates)
            else -> MatchResultNumericVerification.Unresolved(candidates)
        }
    }

    private fun parseStrongValue(text: String): MatchResultNumericCandidate? {
        val parsed = MatchResultPositionSemanticTextParser.parse(text)
        if (parsed.markerMatched &&
            parsed.kill != null &&
            parsed.prefixType != MatchResultEliminationPrefixType.EMPTY_PREFIX
        ) {
            return candidate(text, parsed.kill)
        }
        return NUMERIC_PREFIX_PATTERN.find(text)?.groupValues?.get(1)?.let { prefix ->
            val value = if (prefix.equals("O", ignoreCase = true)) 0 else prefix.toIntOrNull()
            value?.takeIf { it >= 0 }?.let { candidate(text, it) }
        }
    }

    private fun parseValue(text: String): MatchResultNumericCandidate? {
        val parsed = MatchResultPositionSemanticTextParser.parse(text)
        if (parsed.markerMatched && parsed.kill != null) {
            return candidate(text, parsed.kill)
        }
        return NUMERIC_PREFIX_PATTERN.find(text)?.groupValues?.get(1)?.let { prefix ->
            val value = if (prefix.equals("O", ignoreCase = true)) 0 else prefix.toIntOrNull()
            value?.takeIf { it >= 0 }?.let { candidate(text, it) }
        }
    }

    private fun candidate(rawText: String, value: Int) = MatchResultNumericCandidate(
        variant = MatchResultNumericCropVariant.ORIGINAL,
        rawText = rawText,
        value = value,
        confidence = null,
    )

    private data class LocalObservation(
        val text: String,
        val bounds: RawOcrBoundingBox,
    )

    private companion object {
        val NUMERIC_PREFIX_PATTERN = Regex("^\\s*(\\d+|[Oo])")
    }
}

private fun RawOcrBoundingBox.toLocal(positionBounds: com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect) =
    RawOcrBoundingBox(
        left = left - positionBounds.left,
        top = top - positionBounds.top,
        right = right - positionBounds.left,
        bottom = bottom - positionBounds.top,
    )

private fun RawOcrBoundingBox.centerX(): Double = (left + right) / 2.0

private fun RawOcrBoundingBox.centerY(): Double = (top + bottom) / 2.0
