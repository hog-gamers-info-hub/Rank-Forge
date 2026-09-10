package com.hoggamers.rankforge.presentation.screen

import java.util.Locale

data class TeamListParseResult(
    val teamNames: List<String>,
    val hasOverflow: Boolean,
)

object TeamListParser {
    private const val MAX_TEAM_NAMES = 12
    private val numberedPrefix = Regex(
        """^0?(?:[1-9]|1[0-2])(?:\s*[.)\-:]\s*|\s+)""",
    )
    private val recognizedHeaders = setOf(
        "teamlist",
        "slotlist",
        "teams",
        "teamnames",
    )

    fun parse(input: String): TeamListParseResult {
        val meaningfulLines = input.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
            .let { lines ->
                if (lines.firstOrNull()?.isRecognizedHeader() == true) {
                    lines.drop(1)
                } else {
                    lines
                }
            }

        val teamNames = meaningfulLines.mapNotNull { line ->
            line.replaceFirst(numberedPrefix, "").trim().takeIf(String::isNotEmpty)
        }

        return TeamListParseResult(
            teamNames = teamNames.take(MAX_TEAM_NAMES),
            hasOverflow = teamNames.size > MAX_TEAM_NAMES,
        )
    }

    private fun String.isRecognizedHeader(): Boolean =
        lowercase(Locale.ROOT)
            .filterNot { it.isWhitespace() || it == '-' || it == '_' } in recognizedHeaders
}
