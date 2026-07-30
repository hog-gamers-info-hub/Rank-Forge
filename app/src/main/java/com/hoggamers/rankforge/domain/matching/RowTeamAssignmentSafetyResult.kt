package com.hoggamers.rankforge.domain.matching

data class RowTeamAssignmentSafetyResult(
    val rowIndex: Int,
    val confidenceAssessment: TeamMatchConfidenceAssessment,
    val safetyStatus: TeamAssignmentSafetyStatus,
    val proposedTeamSlot: Int?,
    val reasons: Set<TeamAssignmentSafetyReason>,
)
