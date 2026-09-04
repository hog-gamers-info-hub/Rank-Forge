package com.hoggamers.rankforge.data.local

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import kotlinx.serialization.Serializable

@Serializable
data class MatchCalculatedEvidence(
    val lobby: LobbyCalculatedEvidence = LobbyCalculatedEvidence(),
    val result: ResultCalculatedEvidence = ResultCalculatedEvidence(),
)

@Serializable
data class LobbyCalculatedEvidence(
    val teams: List<LobbyTeamCalculatedEvidence> = emptyList(),
) {
    init {
        require(teams.size <= 12) { "Lobby calculated evidence cannot contain more than 12 teams." }
    }
}

@Serializable
data class LobbyTeamCalculatedEvidence(
    val slotNumber: Int?,
    val teamName: String?,
    val sourceScreenshotIndex: Int,
    val cropLeft: Double,
    val cropTop: Double,
    val cropRight: Double,
    val cropBottom: Double,
    val playerNames: List<String?>,
) {
    init {
        require(playerNames.size == 4) { "Lobby calculated evidence must contain four player names." }
    }
}

@Serializable
data class ResultCalculatedEvidence(
    val positions: List<ResultPositionCalculatedEvidence> = emptyList(),
    /** Source positions explicitly excluded from finalization; absent in legacy payloads. */
    val excludedSourcePositions: List<Int> = emptyList(),
) {
    init {
        require(positions.size <= 12) { "Result calculated evidence cannot contain more than 12 positions." }
    }
}

@Serializable
data class ResultPositionCalculatedEvidence(
    val position: Int,
    val sourceScreenshotRole: MatchResultScreenshotRole? = null,
    val cropLeft: Int? = null,
    val cropTop: Int? = null,
    val cropRight: Int? = null,
    val cropBottom: Int? = null,
    val slotNumber: Int? = null,
    val teamName: String? = null,
    val playerNames: List<String?> = listOf(null, null, null, null),
    val playerKills: List<Int?> = listOf(null, null, null, null),
    val totalKills: Int? = null,
    /** The editable placement; [position] remains the immutable working-row identity. */
    val placement: Int? = position,
    /** Whether each logical player has a detected name and an applicable individual kill. */
    val playerKillApplicable: List<Boolean>? = null,
) {
    init {
        require(playerNames.size == 4) { "Result calculated evidence must contain four player names." }
        require(playerKills.size == 4) { "Result calculated evidence must contain four player kills." }
        require(playerKillApplicable == null || playerKillApplicable.size == 4) {
            "Result calculated evidence must contain four player applicability flags."
        }
    }
}
