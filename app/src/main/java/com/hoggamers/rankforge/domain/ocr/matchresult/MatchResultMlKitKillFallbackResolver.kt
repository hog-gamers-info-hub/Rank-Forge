package com.hoggamers.rankforge.domain.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox

/** Recovers only unresolved kill fields from already-retained ML Kit observations. */
data class MatchResultMlKitKillFallbackResolution(
    val verifications: Map<Int, MatchResultNumericVerification>,
    val recognizedEliminationMarkerSlots: Set<Int>,
)

class MatchResultMlKitKillFallbackResolver {
    fun resolve(
        positionCrop: MatchResultPositionCrop,
        rowCrops: List<MatchResultPositionRowCrop>,
        currentPpSemantic: MatchResultPositionSemanticResult,
        evidence: MatchResultAutoCropEvidence,
    ): Map<Int, MatchResultNumericVerification> = resolveWithEvidence(
        positionCrop = positionCrop,
        rowCrops = rowCrops,
        currentPpSemantic = currentPpSemantic,
        evidence = evidence,
    ).verifications

    fun resolveWithEvidence(
        positionCrop: MatchResultPositionCrop,
        rowCrops: List<MatchResultPositionRowCrop>,
        currentPpSemantic: MatchResultPositionSemanticResult,
        evidence: MatchResultAutoCropEvidence,
    ): MatchResultMlKitKillFallbackResolution {
        val killFields = currentPpSemantic.fields
            .asSequence()
            .filter { it.type == MatchResultOcrFieldType.KILL && it.slot != null }
            .associateBy { it.slot!! }
        val verifications = mutableMapOf<Int, MatchResultNumericVerification>()
        val recognizedMarkerSlots = mutableSetOf<Int>()
        (1..4).forEach { slot ->
            val kill = killFields[slot] ?: return@forEach
            if (kill.resolvedText.isNotBlank()) return@forEach

            val rowBounds = rowCrops.firstOrNull {
                it.rowIndex == MatchResultKillFieldLayout.rowIndexForSlot(slot)
            }?.bounds ?: return@forEach
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

            if (observations.any { MatchResultEliminationAnchorText.find(it.text) != null }) {
                recognizedMarkerSlots += slot
            }

            val verification = resolveCell(
                observations = observations,
                ppAnchorBounds = currentPpSemantic.eliminationAnchorBounds[slot],
            )
            verification?.let { verifications[slot] = it }
        }
        return MatchResultMlKitKillFallbackResolution(
            verifications = verifications,
            recognizedEliminationMarkerSlots = recognizedMarkerSlots,
        )
    }

    private fun resolveCell(
        observations: List<LocalObservation>,
        ppAnchorBounds: RawOcrBoundingBox?,
    ): MatchResultNumericVerification? {
        if (observations.isEmpty()) return null

        val mlAnchors = observations.mapNotNull { observation ->
            MatchResultEliminationAnchorText.find(observation.text)?.let { observation to it }
        }
        val directCandidates = observations.mapNotNull { observation ->
            parseDirectAttachedKill(observation.text)
        }
        if (directCandidates.isNotEmpty()) return resolveCandidates(directCandidates)

        val anchorBounds = ppAnchorBounds ?: mlAnchors.firstOrNull()?.first?.bounds ?: return null
        val precedingCandidates = observations
            .asSequence()
            .filter { observation ->
                MatchResultKillFieldLayout.isLocallyNearStandaloneKillAnchor(
                    candidateBounds = observation.bounds,
                    anchorBounds = anchorBounds,
                )
            }
            .mapNotNull { observation -> parseStandaloneNumeric(observation.text) }
            .toList()
        return resolveCandidates(precedingCandidates)
    }

    private fun parseDirectAttachedKill(text: String): MatchResultNumericCandidate? {
        val rawText = text.trim()
        MatchResultEliminationAnchorText.find(rawText)?.kill?.let { value ->
            return candidate(rawText, value)
        }

        if (!rawText.startsWith("D")) return null
        val anchorText = rawText.removePrefix("D").trimStart()
        val anchor = MatchResultEliminationAnchorText.find(anchorText) ?: return null
        if (anchor.kill != null) return null
        return candidate(rawText, 0)
    }

    private fun resolveCandidates(
        candidates: List<MatchResultNumericCandidate>,
    ): MatchResultNumericVerification? {
        if (candidates.isEmpty()) return null
        val values = candidates.mapNotNull { it.value }.distinct()
        return when (values.size) {
            1 -> MatchResultNumericVerification.Verified(values.single(), candidates)
            else -> MatchResultNumericVerification.Unresolved(candidates)
        }
    }

    private fun parseStandaloneNumeric(text: String): MatchResultNumericCandidate? {
        val prefix = STANDALONE_NUMERIC_PATTERN.matchEntire(text)?.groupValues?.get(1)
            ?: return null
        val value = when {
            prefix.equals("O", ignoreCase = true) || prefix == "D" -> 0
            else -> prefix.toIntOrNull()
        }
        return value?.takeIf { it >= 0 }?.let { candidate(text.trim(), it) }
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
        val STANDALONE_NUMERIC_PATTERN = Regex("^\\s*(\\d+|[Oo]|D)\\s*$")
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
