package com.hoggamers.rankforge.domain.matching

data class TeamAssignmentSafetyResult(
    val rowCount: Int,
    val safeAssignmentCount: Int,
    val rowResults: List<RowTeamAssignmentSafetyResult>,
)
