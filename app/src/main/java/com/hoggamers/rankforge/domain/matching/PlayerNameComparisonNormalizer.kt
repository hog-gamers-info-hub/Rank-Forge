package com.hoggamers.rankforge.domain.matching

import java.text.Normalizer
import java.util.Locale

object PlayerNameComparisonNormalizer {
    fun normalize(value: String?): String? {
        value ?: return null

        val nfcValue = Normalizer.normalize(value, Normalizer.Form.NFC)
        val whitespaceCanonicalized = canonicalizeUnicodeWhitespace(nfcValue)
        val initiallySeparated = collapseAndTrimComparisonSeparators(whitespaceCanonicalized)
        val lowercase = initiallySeparated.lowercase(Locale.ROOT)
        val punctuationSeparated = convertPunctuationToComparisonSeparators(lowercase)
        val symbolsRemoved = removeDecorativeSymbolsAndControls(punctuationSeparated)
        val confusionMapped = applyApprovedOcrConfusionMappings(symbolsRemoved)
        val finalComparisonValue = collapseAndTrimComparisonSeparators(confusionMapped)

        return finalComparisonValue.takeIf { it.isNotEmpty() }
    }

    private fun canonicalizeUnicodeWhitespace(value: String): String {
        val result = StringBuilder()
        value.codePoints().forEach { codePoint ->
            if (codePoint.isUnicodeWhitespace()) {
                result.append(COMPARISON_SEPARATOR)
            } else {
                result.appendCodePoint(codePoint)
            }
        }
        return result.toString()
    }

    private fun convertPunctuationToComparisonSeparators(value: String): String {
        val result = StringBuilder()
        value.codePoints().forEach { codePoint ->
            if (codePoint.isUnicodePunctuation()) {
                result.append(COMPARISON_SEPARATOR)
            } else {
                result.appendCodePoint(codePoint)
            }
        }
        return result.toString()
    }

    private fun removeDecorativeSymbolsAndControls(value: String): String {
        val result = StringBuilder()
        value.codePoints().forEach { codePoint ->
            if (!codePoint.isDecorativeSymbolEmojiOrControl()) {
                result.appendCodePoint(codePoint)
            }
        }
        return result.toString()
    }

    private fun applyApprovedOcrConfusionMappings(value: String): String {
        val result = StringBuilder()
        value.codePoints().forEach { codePoint ->
            result.appendCodePoint(
                when (codePoint) {
                    '0'.code, 'o'.code -> '0'.code
                    '1'.code, 'i'.code, 'l'.code -> '1'.code
                    else -> codePoint
                }
            )
        }
        return result.toString()
    }

    private fun collapseAndTrimComparisonSeparators(value: String): String {
        val result = StringBuilder()
        var previousWasSeparator = true

        value.forEach { char ->
            if (char == COMPARISON_SEPARATOR) {
                if (!previousWasSeparator) {
                    result.append(COMPARISON_SEPARATOR)
                }
                previousWasSeparator = true
            } else {
                result.append(char)
                previousWasSeparator = false
            }
        }

        if (result.isNotEmpty() && result.last() == COMPARISON_SEPARATOR) {
            result.setLength(result.length - 1)
        }

        return result.toString()
    }

    private fun Int.isUnicodeWhitespace(): Boolean =
        Character.isWhitespace(this) || Character.isSpaceChar(this)

    private fun Int.isUnicodePunctuation(): Boolean {
        if (this == PIPE_CODE_POINT) {
            return true
        }

        return when (Character.getType(this)) {
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt()
            -> true
            else -> false
        }
    }

    private fun Int.isDecorativeSymbolEmojiOrControl(): Boolean =
        this in EMOJI_MODIFIER_RANGE ||
            this in VARIATION_SELECTOR_RANGE ||
            this in SUPPLEMENTARY_VARIATION_SELECTOR_RANGE ||
            Character.getType(this) in REMOVED_CHARACTER_TYPES

    private const val COMPARISON_SEPARATOR = ' '
    private const val PIPE_CODE_POINT = 0x7C

    private val EMOJI_MODIFIER_RANGE = 0x1F3FB..0x1F3FF
    private val VARIATION_SELECTOR_RANGE = 0xFE00..0xFE0F
    private val SUPPLEMENTARY_VARIATION_SELECTOR_RANGE = 0xE0100..0xE01EF

    private val REMOVED_CHARACTER_TYPES = setOf(
        Character.MATH_SYMBOL.toInt(),
        Character.CURRENCY_SYMBOL.toInt(),
        Character.MODIFIER_SYMBOL.toInt(),
        Character.OTHER_SYMBOL.toInt(),
        Character.CONTROL.toInt(),
        Character.FORMAT.toInt()
    )
}
