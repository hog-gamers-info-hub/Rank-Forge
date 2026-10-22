package com.hoggamers.rankforge.domain.ocr.matchresult

import java.text.Normalizer
import java.util.Locale

/** A leading elimination marker and any trustworthy numeric prefix that precedes it. */
internal data class MatchResultEliminationAnchorMatch(
    val rawText: String,
    val kill: Int?,
    val prefixType: MatchResultEliminationPrefixType,
    val markerText: String,
    val markerEndIndex: Int,
    val markerIsComplete: Boolean,
)

internal object MatchResultEliminationAnchorText {
    private const val MIN_MARKER_LENGTH = 5
    private const val MISSING_LEADING_E_MARKER_LENGTH = 4
    private const val MAX_MARKER_LENGTH = 14
    private const val MAX_MATCH_DISTANCE = 3
    private const val MIN_COMPLETE_MARKER_LENGTH = 9
    private const val COMPLETE_MARKER = "eliminations"

    private val markerStems = listOf(
        "elimin",
        "elimina",
        "eliminat",
        "eliminati",
        "eliminatio",
        "elimination",
        "eliminations",
    )
    private val plausibleMarkerStarts = setOf('e', 'l', 'f', 'b', 'c', 'i', '3')
    private val leadingEConfusions = setOf('f', 'b', 'c', 'i', 'l', '3')
    private val markerEditCharacters = setOf('e', 'l', 'i', 'm', 'n', 'a', 't', 'o', 's') +
        leadingEConfusions
    private val attachedSafeMarkerTexts = setOf(
        "eliminations",
        "elimination",
        "eliminatio",
        "eliminati",
        "eiminations",
        "eimination",
        "eiminatio",
        "eiminati",
        "ellmination",
        "elmination",
    )

    fun find(text: String): MatchResultEliminationAnchorMatch? {
        val rawText = text.trim()
        if (rawText.isEmpty()) return null

        val markerOnly = findMarkerAt(rawText, 0)
        if (
            markerOnly != null &&
            rawText.startsWith("3") &&
            isAmbiguousLeadingNumericPrefix(rawText, 0)
        ) {
            return anchorMatch(
                rawText = rawText,
                marker = markerOnly,
                prefix = "",
                prefixType = MatchResultEliminationPrefixType.EMPTY_PREFIX,
            )
        }

        val prefix = leadingPrefix(rawText) ?: return markerOnly?.let {
            anchorMatch(
                rawText = rawText,
                marker = it,
                prefix = "",
                prefixType = MatchResultEliminationPrefixType.EMPTY_PREFIX,
            )
        }
        val marker = findMarkerAt(rawText, prefix.markerStartIndex) ?: return null
        if (isAmbiguousLeadingNumericPrefix(rawText, prefix.markerStartIndex) &&
            prefix.value == "3"
        ) {
            return anchorMatch(
                rawText = rawText,
                marker = marker,
                prefix = "",
                prefixType = MatchResultEliminationPrefixType.EMPTY_PREFIX,
            )
        }

        val prefixType = when {
            prefix.value.equals("O", ignoreCase = true) -> MatchResultEliminationPrefixType.O_NORMALIZED
            else -> MatchResultEliminationPrefixType.EXPLICIT_NUMERIC
        }
        return anchorMatch(
            rawText = rawText,
            marker = marker,
            prefix = prefix.value,
            prefixType = prefixType,
        )
    }

    /** Finds a marker beginning at [startIndex] without interpreting a numeric prefix. */
    fun findMarkerEndIndex(text: String, startIndex: Int): Int? {
        val marker = findMarkerAt(text, startIndex) ?: return null
        val remainder = text.substring(marker.endIndex)
        return marker.endIndex.takeIf {
            remainder.isBlank() ||
                marker.isComplete ||
                remainder.firstOrNull()?.isWhitespace() == true
        }
    }

    /**
     * Returns only the player text after a degraded leading elimination token when the
     * token is separated from the player text by whitespace.
     */
    fun playerSuffixAfterDegradedLeadingAnchorOrNull(text: String): String? {
        val rawText = text.trim()
        val anchor = find(rawText) ?: return null
        val remainder = rawText.substring(anchor.markerEndIndex)
        return Regex("^\\s*\\S*\\s+(.+?)\\s*$")
            .matchEntire(remainder)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    private fun anchorMatch(
        rawText: String,
        marker: MarkerMatch,
        prefix: String,
        prefixType: MatchResultEliminationPrefixType,
    ): MatchResultEliminationAnchorMatch = MatchResultEliminationAnchorMatch(
        rawText = rawText,
        kill = when (prefixType) {
            MatchResultEliminationPrefixType.EXPLICIT_NUMERIC -> prefix.toIntOrNull()
            MatchResultEliminationPrefixType.O_NORMALIZED -> 0
            MatchResultEliminationPrefixType.EMPTY_PREFIX -> null
        },
        prefixType = prefixType,
        markerText = rawText.substring(marker.startIndex, marker.endIndex),
        markerEndIndex = marker.endIndex,
        markerIsComplete = marker.isComplete,
    )

    private fun leadingPrefix(text: String): LeadingPrefix? {
        var index = 0
        while (index < text.length && text[index].isWhitespace()) index++
        val startIndex = index
        if (index >= text.length) return null

        if (text[index].isDigit()) {
            while (index < text.length && text[index].isDigit()) index++
        } else if (text[index].equals('O', ignoreCase = true)) {
            index++
        } else {
            return null
        }

        val value = text.substring(startIndex, index)
        while (index < text.length && text[index].isWhitespace()) index++
        return LeadingPrefix(value = value, markerStartIndex = index)
    }

    private fun findMarkerAt(text: String, startIndex: Int): MarkerMatch? {
        if (startIndex !in text.indices) return null
        var tokenEndIndex = startIndex
        while (tokenEndIndex < text.length && !text[tokenEndIndex].isWhitespace()) tokenEndIndex++
        if (tokenEndIndex == startIndex) return null

        val token = text.substring(startIndex, tokenEndIndex)
        val normalizedToken = normalize(token)
        if (normalizedToken.firstOrNull() !in plausibleMarkerStarts) return null
        var leadingConfusionCount = 0
        while (leadingConfusionCount < normalizedToken.length &&
            normalizedToken[leadingConfusionCount] in leadingEConfusions
        ) {
            leadingConfusionCount++
        }
        if (leadingConfusionCount > 0 &&
            leadingConfusionCount < normalizedToken.length &&
            normalizedToken[leadingConfusionCount] == 'e'
        ) {
            return null
        }

        val maxLength = minOf(token.length, MAX_MARKER_LENGTH)
        val candidates = (MISSING_LEADING_E_MARKER_LENGTH..maxLength).mapNotNull { length ->
            val candidate = normalize(token.take(length))
            val minimumLength = if (candidate.startsWith("lmin")) {
                MISSING_LEADING_E_MARKER_LENGTH
            } else {
                MIN_MARKER_LENGTH
            }
            if (candidate.length < minimumLength) {
                return@mapNotNull null
            }
            val distance = markerStems.mapNotNull { stem ->
                boundedDistance(candidate, stem, MAX_MATCH_DISTANCE)
            }.minOrNull() ?: return@mapNotNull null
            MarkerMatch(
                startIndex = startIndex,
                endIndex = startIndex + length,
                distance = distance,
            )
        }

        val completeCandidates = candidates.mapNotNull { marker ->
            val candidate = normalize(token.take(marker.endIndex - startIndex))
            if (candidate.length < MIN_COMPLETE_MARKER_LENGTH ||
                isUnsafeWholeCandidate(candidate)
            ) {
                return@mapNotNull null
            }
            boundedDistance(candidate, COMPLETE_MARKER, MAX_MATCH_DISTANCE)?.let { distance ->
                marker to distance
            }
        }
        candidates
            .firstOrNull { it.endIndex == tokenEndIndex }
            ?.let { wholeCandidate ->
                val candidate = normalize(token)
                if (!isUnsafeWholeCandidate(candidate) &&
                    candidate.all { it in markerEditCharacters }
                ) {
                    return wholeCandidate.copy(
                        isComplete = wholeCandidate.isComplete || candidate in attachedSafeMarkerTexts,
                    )
                }
            }
        if (completeCandidates.isNotEmpty() && candidates.none { it.distance == 0 }) {
            return completeCandidates.minWithOrNull(
                compareBy<Pair<MarkerMatch, Int>> { it.second }
                    .thenByDescending { it.first.endIndex },
            )?.first?.copy(isComplete = true)
        }
        return candidates.minWithOrNull(
            compareBy<MarkerMatch> { it.distance }
                .thenByDescending { it.endIndex },
        )?.let { marker ->
            val candidate = normalize(token.take(marker.endIndex - startIndex))
            marker.copy(isComplete = marker.isComplete || candidate in attachedSafeMarkerTexts)
        }
    }

    private fun isUnsafeWholeCandidate(candidate: String): Boolean =
        candidate == "eliminato" ||
            candidate.startsWith("elimination") &&
            candidate != "elimination" &&
            candidate != COMPLETE_MARKER

    private fun normalize(text: String): String = Normalizer
        .normalize(text, Normalizer.Form.NFD)
        .lowercase(Locale.ROOT)
        .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        .map { character ->
            when (character) {
                '0' -> 'o'
                '1', '|', 'u', 'ı', 'ι', 'і' -> 'i'
                'ο', 'о' -> 'o'
                else -> character
            }
        }
        .joinToString("")

    private fun boundedDistance(candidate: String, stem: String, limit: Int): Int? {
        var previous = IntArray(stem.length + 1) { it }
        for (candidateIndex in candidate.indices) {
            val current = IntArray(stem.length + 1)
            current[0] = candidateIndex + 1
            for (stemIndex in stem.indices) {
                val substitution = substitutionCost(
                    candidate = candidate[candidateIndex],
                    stem = stem[stemIndex],
                    stemIndex = stemIndex,
                )
                current[stemIndex + 1] = minOf(
                    current[stemIndex] + 1,
                    if (candidate[candidateIndex] in markerEditCharacters) {
                        previous[stemIndex + 1] + 1
                    } else {
                        Int.MAX_VALUE
                    },
                    substitution?.let { previous[stemIndex] + it } ?: Int.MAX_VALUE,
                )
            }
            previous = current
        }
        return previous.last().takeIf { it <= limit }
    }

    private fun substitutionCost(candidate: Char, stem: Char, stemIndex: Int): Int? {
        if (candidate == stem) return 0
        if (stemIndex == 0 && stem == 'e' && candidate in leadingEConfusions) return 1
        if ((candidate == 'i' && stem == 'l') || (candidate == 'l' && stem == 'i')) return 1
        if ((candidate == 'm' && stem == 'n') || (candidate == 'n' && stem == 'm')) return 1
        if ((candidate == 'a' && stem == 'o') || (candidate == 'o' && stem == 'a')) return 1
        return null
    }

    private fun isAmbiguousLeadingNumericPrefix(text: String, markerStartIndex: Int): Boolean {
        val effectiveMarkerStart = if (markerStartIndex == 0 && text.startsWith("3")) {
            1
        } else {
            markerStartIndex
        }
        if (effectiveMarkerStart >= text.length) return false
        val markerStart = normalize(text[effectiveMarkerStart].toString()).firstOrNull()
        return markerStart in leadingEConfusions && markerStart != 'e'
    }

    private data class LeadingPrefix(
        val value: String,
        val markerStartIndex: Int,
    )

    private data class MarkerMatch(
        val startIndex: Int,
        val endIndex: Int,
        val distance: Int,
        val isComplete: Boolean = false,
    )
}
