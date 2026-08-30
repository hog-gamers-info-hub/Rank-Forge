package com.hoggamers.rankforge.data.ocr.matchresult

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole

sealed interface MatchResultSemanticRoleReconciliation {
    data class Resolved(
        val results: Map<MatchResultScreenshotRole, MatchResultOcrPreviewProcessingResult.Processed>,
    ) : MatchResultSemanticRoleReconciliation

    data object Incomplete : MatchResultSemanticRoleReconciliation
    data object Conflict : MatchResultSemanticRoleReconciliation
}

/** Re-keys a complete physical pair by the role proven by each extraction. */
class MatchResultSemanticRoleReconciler {
    fun reconcile(
        physicalResults: Map<MatchResultScreenshotRole, MatchResultOcrPreviewProcessingResult>,
    ): MatchResultSemanticRoleReconciliation {
        if (physicalResults.keys != MatchResultScreenshotRole.entries.toSet()) {
            return MatchResultSemanticRoleReconciliation.Incomplete
        }
        val processedByRole = physicalResults.values
            .filterIsInstance<MatchResultOcrPreviewProcessingResult.Processed>()
            .groupBy { it.extraction.role }
        if (processedByRole.keys != MatchResultScreenshotRole.entries.toSet()) {
            return if (physicalResults.values.all {
                it is MatchResultOcrPreviewProcessingResult.Processed
            }) {
                MatchResultSemanticRoleReconciliation.Conflict
            } else {
                MatchResultSemanticRoleReconciliation.Incomplete
            }
        }
        if (processedByRole.values.any { it.size != 1 }) {
            return MatchResultSemanticRoleReconciliation.Conflict
        }
        return MatchResultSemanticRoleReconciliation.Resolved(
            results = MatchResultScreenshotRole.entries.associateWith { role ->
                processedByRole.getValue(role).single()
            },
        )
    }
}
