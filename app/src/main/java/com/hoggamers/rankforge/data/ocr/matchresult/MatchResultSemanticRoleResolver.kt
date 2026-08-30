package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultAutoCropEvidence
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculationResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCropCalculator
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole

sealed interface MatchResultSemanticRoleResolution {
    data class Resolved(
        val role: MatchResultScreenshotRole,
        val geometry: MatchResultPositionCropCalculationResult.Available,
    ) : MatchResultSemanticRoleResolution

    data object Unresolved : MatchResultSemanticRoleResolution
    data object Ambiguous : MatchResultSemanticRoleResolution
}

/** Resolves Result role from one already-collected ML Kit evidence set. */
class MatchResultSemanticRoleResolver(
    private val calculator: MatchResultPositionCropCalculator = MatchResultPositionCropCalculator(),
) {
    fun resolve(evidence: MatchResultAutoCropEvidence): MatchResultSemanticRoleResolution {
        val candidates = MatchResultScreenshotRole.entries.mapNotNull { role ->
            val geometry = calculateStrictGeometry(evidence, role) ?: return@mapNotNull null
            role to geometry
        }
        return when (candidates.size) {
            1 -> {
                val (role, geometry) = candidates.single()
                MatchResultSemanticRoleResolution.Resolved(role, geometry)
            }
            0 -> MatchResultSemanticRoleResolution.Unresolved
            else -> MatchResultSemanticRoleResolution.Ambiguous
        }
    }

    private fun calculateStrictGeometry(
        evidence: MatchResultAutoCropEvidence,
        role: MatchResultScreenshotRole,
    ): MatchResultPositionCropCalculationResult.Available? {
        if (!hasRoleEvidence(evidence, role)) return null
        val result = calculator.calculate(
            evidence = evidence,
            role = role,
            // Position 11 may never make a lower screenshot look upper.
            allowUpperPositionElevenFallback = false,
        ) as? MatchResultPositionCropCalculationResult.Available ?: return null
        val expected = when (role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> (1..10).toList()
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> (11..12).toList()
        }
        return result.takeIf { geometry -> geometry.crops.map { it.position } == expected }
    }

    private fun hasRoleEvidence(
        evidence: MatchResultAutoCropEvidence,
        role: MatchResultScreenshotRole,
    ): Boolean {
        val exactTexts = evidence.observations.map { it.text.trim() }.toSet()
        return when (role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER ->
                // Existing upper recovery allows either left anchor when the other is missed.
                exactTexts.contains("4") || exactTexts.contains("5")
            MatchResultScreenshotRole.MATCH_RESULT_LOWER ->
                exactTexts.containsAll(setOf("11", "12"))
        }
    }
}
