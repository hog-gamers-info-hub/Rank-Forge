package com.hoggamers.rankforge.domain.tournament

import javax.inject.Inject

class CheckTournamentQuotaUseCase @Inject constructor(
    private val repository: TournamentQuotaRepository,
) {
    suspend operator fun invoke(): TournamentQuotaResult = repository.checkQuota()
}
