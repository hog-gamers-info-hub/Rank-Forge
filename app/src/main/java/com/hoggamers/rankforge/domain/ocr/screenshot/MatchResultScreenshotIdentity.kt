package com.hoggamers.rankforge.domain.ocr.screenshot

enum class OcrScreenshotKind {
    MATCH_RESULT,
}

enum class MatchResultScreenshotRole {
    MATCH_RESULT_UPPER,
    MATCH_RESULT_LOWER,
}

data class MatchResultScreenshotIdentity(
    val tournamentId: String,
    val matchId: String,
    val kind: OcrScreenshotKind = OcrScreenshotKind.MATCH_RESULT,
    val role: MatchResultScreenshotRole,
) {
    init {
        require(tournamentId.isNotBlank()) { "Tournament ID must not be blank." }
        require(matchId.isNotBlank()) { "Match ID must not be blank." }
        require(kind == OcrScreenshotKind.MATCH_RESULT) {
            "Match result screenshot identity must use MATCH_RESULT kind."
        }
    }
}
