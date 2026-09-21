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

            val verification = if (positionCrop.position in 6..12 && (slot == 1 || slot == 2)) {
                resolveSharedMiddleCell(observations)
            } else {
                resolveCell(observations)
            }
            verification?.let { slot to it }
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

    private fun resolveSharedMiddleCell(
        observations: List<LocalObservation>,
    ): MatchResultNumericVerification? {
        val anchor = observations.asSequence()
            .mapNotNull { observation ->
                MatchResultEliminationAnchorText.find(observation.text)?.let { observation to it }
            }
            .firstOrNull()
            ?: return resolveCell(observations)

        val (anchorObservation, anchorMatch) = anchor
        if (anchorMatch.prefixType != MatchResultEliminationPrefixType.EMPTY_PREFIX) {
            return anchorMatch.kill?.let { value ->
                MatchResultNumericVerification.Verified(
                    value = value,
                    candidates = listOf(candidate(anchorMatch.rawText, value)),
                )
            }
        }

        val precedingCandidates = observations
            .asSequence()
            .filter { it.bounds.centerX() < anchorObservation.bounds.centerX() }
            .mapNotNull { observation -> parseStandaloneNumeric(observation.text) }
            .toList()
        if (precedingCandidates.isEmpty()) return null

        val values = precedingCandidates.mapNotNull { it.value }.distinct()
        return when (values.size) {
            1 -> MatchResultNumericVerification.Verified(values.single(), precedingCandidates)
            else -> MatchResultNumericVerification.Unresolved(precedingCandidates)
        }
    }

    private fun parseStandaloneNumeric(text: String): MatchResultNumericCandidate? {
        val prefix = STANDALONE_NUMERIC_PATTERN.matchEntire(text)?.groupValues?.get(1)
            ?: return null
        val value = if (prefix.equals("O", ignoreCase = true)) 0 else prefix.toIntOrNull()
        return value?.takeIf { it >= 0 }?.let { candidate(text.trim(), it) }
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
        val STANDALONE_NUMERIC_PATTERN = Regex("^\\s*(\\d+|[Oo])\\s*$")
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
