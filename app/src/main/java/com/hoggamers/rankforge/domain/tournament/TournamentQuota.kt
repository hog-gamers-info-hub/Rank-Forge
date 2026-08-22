package com.hoggamers.rankforge.domain.tournament

const val MAX_TOURNAMENTS_PER_ACCOUNT = 5

sealed interface TournamentQuotaResult {
    data class Allowed(val currentCount: Int) : TournamentQuotaResult

    data class LimitReached(val currentCount: Int) : TournamentQuotaResult

    data object AuthenticationRequired : TournamentQuotaResult
    data object NetworkFailure : TournamentQuotaResult
    data object UnknownFailure : TournamentQuotaResult
}

interface TournamentQuotaRepository {
    suspend fun checkQuota(): TournamentQuotaResult
}
