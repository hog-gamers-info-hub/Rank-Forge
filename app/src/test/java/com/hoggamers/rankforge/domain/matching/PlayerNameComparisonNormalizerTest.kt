package com.hoggamers.rankforge.domain.matching

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerNameComparisonNormalizerTest {
    @Test
    fun normalize_returnsNullForNullEmptyAndBlankInput() {
        assertNull(PlayerNameComparisonNormalizer.normalize(null))
        assertNull(PlayerNameComparisonNormalizer.normalize(""))
        assertNull(PlayerNameComparisonNormalizer.normalize(" \t\n\r\u00A0\u2003 "))
    }

    @Test
    fun normalize_normalizesOrdinaryMixedCaseAndWhitespace() {
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha Beta"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("  Alpha   Beta  "))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha\tBeta"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha\nBeta"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha\u00A0\u2003Beta"))
    }

    @Test
    fun normalize_convertsPunctuationToSeparators() {
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha_Beta"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha-Beta"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha.Beta"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha'Beta"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("\"Alpha\"Beta"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("[Alpha](Beta)"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha/Beta"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha|Beta"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha:Beta"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha;Beta"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha,Beta"))
    }

    @Test
    fun normalize_cleansRepeatedAndEdgeSeparatorsAfterPunctuationConversion() {
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha--Beta"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha__Beta"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha...Beta"))
        assertEquals("a1pha beta", PlayerNameComparisonNormalizer.normalize("Alpha - Beta"))
        assertEquals("a1pha", PlayerNameComparisonNormalizer.normalize("--Alpha--"))
    }

    @Test
    fun normalize_returnsNullWhenInputBecomesBlankAfterCleanup() {
        assertNull(PlayerNameComparisonNormalizer.normalize("--__..."))
        assertNull(PlayerNameComparisonNormalizer.normalize("***"))
        assertNull(PlayerNameComparisonNormalizer.normalize("\u2605\u2606"))
        assertNull(PlayerNameComparisonNormalizer.normalize("\uD83D\uDE42\uD83D\uDD25"))
        assertNull(PlayerNameComparisonNormalizer.normalize(" -- \u2605\u2606 __ "))
    }

    @Test
    fun normalize_removesDecorativeSymbolsEmojiAndControls() {
        assertEquals("a1pha", PlayerNameComparisonNormalizer.normalize("\u2605Alpha\u2606"))
        assertEquals("a1pha", PlayerNameComparisonNormalizer.normalize("\uD83D\uDE42Alpha\uD83D\uDD25"))
        assertEquals("a1pha", PlayerNameComparisonNormalizer.normalize("Al\u0000pha"))
        assertEquals("a1pha", PlayerNameComparisonNormalizer.normalize("Al\u200Dpha"))
    }

    @Test
    fun normalize_appliesNfcUnicodeNormalization() {
        val composed = "Caf\u00E9"
        val decomposed = "Cafe\u0301"

        assertEquals("café", PlayerNameComparisonNormalizer.normalize(composed))
        assertEquals(
            PlayerNameComparisonNormalizer.normalize(composed),
            PlayerNameComparisonNormalizer.normalize(decomposed)
        )
    }

    @Test
    fun normalize_usesLocaleRootLowercase() {
        val originalLocale = Locale.getDefault()

        try {
            Locale.setDefault(Locale("tr", "TR"))

            assertEquals("10ta", PlayerNameComparisonNormalizer.normalize("IOTA"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun normalize_appliesOnlyApprovedOcrConfusionMappings() {
        assertEquals("000", PlayerNameComparisonNormalizer.normalize("O0o"))
        assertEquals("111", PlayerNameComparisonNormalizer.normalize("1Il"))
        assertEquals("5s 8b 2z 6g", PlayerNameComparisonNormalizer.normalize("5S 8B 2Z 6G"))
    }

    @Test
    fun normalize_isDeterministicAndIdempotentAfterSeparatorCleanup() {
        val input = " --Alpha...Beta__ "
        val firstResult = PlayerNameComparisonNormalizer.normalize(input)

        assertEquals("a1pha beta", firstResult)
        assertEquals(firstResult, PlayerNameComparisonNormalizer.normalize(input))
        assertEquals(firstResult, PlayerNameComparisonNormalizer.normalize(firstResult))
    }

    @Test
    fun normalize_doesNotModifyOriginalInputString() {
        val original = " Alpha--Beta "

        PlayerNameComparisonNormalizer.normalize(original)

        assertEquals(" Alpha--Beta ", original)
    }
}
