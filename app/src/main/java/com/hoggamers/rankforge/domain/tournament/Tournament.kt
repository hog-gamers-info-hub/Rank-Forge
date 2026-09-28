package com.hoggamers.rankforge.domain.tournament

data class Tournament(
    val id: String,
    val name: String,
    val stageName: String,
    val organizerContactNumber: String,
    val status: TournamentStatus,
    val ownerUserId: String? = null,
    val organizationName: String? = null,
)
