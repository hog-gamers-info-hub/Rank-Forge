package com.hoggamers.rankforge.domain.matching

data class TeamCandidateRosterInput(
    val teamSlot: Int,
    val rosterPlayerNames: List<String?>,
)
