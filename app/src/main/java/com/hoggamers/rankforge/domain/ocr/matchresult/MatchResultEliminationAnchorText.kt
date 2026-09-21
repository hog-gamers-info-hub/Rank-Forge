package com.hoggamers.rankforge.domain.ocr.matchresult

/** A leading elimination marker and any trustworthy numeric prefix that precedes it. */
internal data class MatchResultEliminationAnchorMatch(
    val rawText: String,
    val kill: Int?,
    val prefixType: MatchResultEliminationPrefixType,
)

internal object MatchResultEliminationAnchorText {
    private val leadingAnchorPattern = Regex(
        "^\\s*(?:(\\d+|[Oo])\\s*)?Eliminat",
        RegexOption.IGNORE_CASE,
    )
    private val degradedPlayerBoundaryPattern = Regex(
        "^\\s*(?:(?:\\d+|[Oo])\\s*)?Eliminat\\S*\\s+(.+?)\\s*$",
        RegexOption.IGNORE_CASE,
    )

    fun find(text: String): MatchResultEliminationAnchorMatch? {
        val rawText = text.trim()
        val match = leadingAnchorPattern.find(text) ?: return null
        val prefix = match.groupValues[1]
        val prefixType = when {
            prefix.equals("O", ignoreCase = true) -> MatchResultEliminationPrefixType.O_NORMALIZED
            prefix.isNotBlank() -> MatchResultEliminationPrefixType.EXPLICIT_NUMERIC
            else -> MatchResultEliminationPrefixType.EMPTY_PREFIX
        }
        return MatchResultEliminationAnchorMatch(
            rawText = rawText,
            kill = when (prefixType) {
                MatchResultEliminationPrefixType.EXPLICIT_NUMERIC -> prefix.toIntOrNull()
                MatchResultEliminationPrefixType.O_NORMALIZED -> 0
                MatchResultEliminationPrefixType.EMPTY_PREFIX -> null
            },
            prefixType = prefixType,
        )
    }

    /**
     * Returns only the player text after a degraded leading elimination token when the
     * token is separated from the player text by whitespace.
     */
    fun playerSuffixAfterDegradedLeadingAnchorOrNull(text: String): String? {
        return degradedPlayerBoundaryPattern.matchEntire(text)?.groupValues?.get(1)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }
}
