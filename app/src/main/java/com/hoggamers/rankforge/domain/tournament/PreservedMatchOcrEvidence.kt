package com.hoggamers.rankforge.domain.tournament

data class PreservedMatchOcrEvidence(
    val tournamentId: String,
    val matchId: String,
    val sourceScreenshotId: String?,
    val preservedAt: Long,
    val provenance: String,
    val rows: List<PreservedMatchOcrRowEvidence>,
    val correctionSnapshots: List<PreservedMatchOcrCorrectionSnapshot>,
)

data class PreservedMatchOcrRowEvidence(
    val rowIndex: Int,
    val originalOcrText: String?,
    val originalPlacement: Int?,
    val originalKills: Int?,
    val originalSuggestedTeamSlot: Int?,
    val confidenceSummary: String?,
    val safetySummary: String?,
    val manualReviewRequired: Boolean,
)

data class PreservedMatchOcrCorrectionSnapshot(
    val rowIndex: Int,
    val correctedPlacement: Int,
    val correctedKills: Int,
    val correctedTeamSlot: Int,
    val placementChanged: Boolean,
    val killsChanged: Boolean,
    val teamSlotChanged: Boolean,
)
