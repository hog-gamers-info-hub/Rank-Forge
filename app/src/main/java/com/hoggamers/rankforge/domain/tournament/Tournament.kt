package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate

data class Tournament(
    val id: String,
    val name: String,
    val date: LocalDate,
    val organizerName: String,
    val organizerContactNumber: String,
    val status: TournamentStatus,
)
