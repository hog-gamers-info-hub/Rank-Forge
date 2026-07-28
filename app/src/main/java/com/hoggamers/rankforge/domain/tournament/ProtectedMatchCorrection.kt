package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.RevisionConflict

data class ProtectedMatchCorrectionRequest(
    val tournament: Tournament,
    val match: Match,
    val placements: List<MatchPlacement>,
    val kills: List<MatchKill>,
    val expectedRevision: Int,
)

sealed interface ProtectedMatchCorrectionResult {
    data class Success(val revision: Int) : ProtectedMatchCorrectionResult
    data class AlreadyCorrected(val revision: Int) : ProtectedMatchCorrectionResult
    data object AuthenticationRequired : ProtectedMatchCorrectionResult
    data object AuthorizationFailure : ProtectedMatchCorrectionResult
    data object NetworkFailure : ProtectedMatchCorrectionResult
    data object ValidationFailure : ProtectedMatchCorrectionResult
    data object MatchNotFinalized : ProtectedMatchCorrectionResult
    data class Conflict(val conflict: RevisionConflict) : ProtectedMatchCorrectionResult
}

fun interface ProtectedMatchCorrectionAction {
    suspend operator fun invoke(request: ProtectedMatchCorrectionRequest): ProtectedMatchCorrectionResult
}
