package com.hoggamers.rankforge.domain.ocr.screenshot

data class MatchLobbyScreenshotIdentity(
    val tournamentId: String,
    val matchId: String,
    val lobbyScreenshotIndex: Int,
) {
    init {
        require(tournamentId.isNotBlank()) { "Tournament ID must not be blank." }
        require(matchId.isNotBlank()) { "Match ID must not be blank." }
        require(lobbyScreenshotIndex in 1..3) { "Lobby screenshot index must be between 1 and 3." }
    }
}
