package com.hoggamers.rankforge.domain.ocr.customdesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomDesignColumnTextColorsTest {
    @Test
    fun defaultsAreBlackForAllFiveSemanticFields() {
        assertEquals(
            CustomDesignAnchorField.entries.associateWith { "#000000" },
            CustomDesignColumnTextColors.allBlack().asMap(),
        )
    }

    @Test
    fun validColorsAreExactlyFiveAndNormalizedToUppercase() {
        val colors = CustomDesignColumnTextColors.fromMap(
            CustomDesignAnchorField.entries.associateWith { "#a1b2c3" },
        )

        assertEquals(
            CustomDesignAnchorField.entries.associateWith { "#A1B2C3" },
            colors?.asMap(),
        )
    }

    @Test
    fun customHexValuesAllowOptionalHashAndNormalizeToUppercase() {
        assertEquals("#F2F0FF", CustomDesignColumnTextColors.normalizeHexColor("#F2F0FF"))
        assertEquals("#F2F0FF", CustomDesignColumnTextColors.normalizeHexColor("F2F0FF"))
        assertEquals("#21D4FD", CustomDesignColumnTextColors.normalizeHexColor("21d4fd"))
        assertEquals("#111111", CustomDesignColumnTextColors.normalizeHexColor("#111111"))
    }

    @Test
    fun customHexValuesRejectInvalidLengthsAndCharacters() {
        listOf("", "#FFF", "#FFFF", "#FFFFFFFF", "#GG0000", "12345", "1234567")
            .forEach { value ->
                assertNull(CustomDesignColumnTextColors.normalizeHexColor(value))
            }
    }

    @Test
    fun missingExtraAndMalformedColorsAreRejected() {
        val base = CustomDesignAnchorField.entries.associateWith { "#000000" }
        assertNull(CustomDesignColumnTextColors.fromMap(base - CustomDesignAnchorField.WIN))
        assertNull(
            CustomDesignColumnTextColors.fromMap(
                base + (CustomDesignAnchorField.WIN to "#000000") +
                    (CustomDesignAnchorField.TEAM_NAME to "#00000"),
            ),
        )
    }
}
