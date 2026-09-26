package com.hoggamers.rankforge.domain.ocr.customdesign

import java.util.Locale

class CustomDesignColumnTextColors private constructor(
    private val colorsByField: Map<CustomDesignAnchorField, String>,
) {
    fun colorFor(field: CustomDesignAnchorField): String = colorsByField[field] ?: DEFAULT_COLOR

    fun asMap(): Map<CustomDesignAnchorField, String> = colorsByField

    override fun equals(other: Any?): Boolean =
        other is CustomDesignColumnTextColors && colorsByField == other.colorsByField

    override fun hashCode(): Int = colorsByField.hashCode()

    companion object {
        const val DEFAULT_COLOR = "#000000"
        private val colorPattern = Regex("[0-9A-Fa-f]{6}")

        fun normalizeHexColor(value: String): String? {
            val digits = value.removePrefix("#")
            if (!colorPattern.matches(digits)) return null
            return "#${digits.uppercase(Locale.ROOT)}"
        }

        fun allBlack(): CustomDesignColumnTextColors =
            CustomDesignColumnTextColors(
                CustomDesignAnchorField.REQUIRED_FIELDS.associateWith { DEFAULT_COLOR },
            )

        fun fromMap(values: Map<CustomDesignAnchorField, String>): CustomDesignColumnTextColors? {
            val requiredKeys = CustomDesignAnchorField.REQUIRED_FIELDS.toSet()
            val allKeys = CustomDesignAnchorField.entries.toSet()
            if (values.keys != requiredKeys && values.keys != allKeys) return null
            val activeFields = if (values.keys == requiredKeys) {
                CustomDesignAnchorField.REQUIRED_FIELDS
            } else {
                CustomDesignAnchorField.entries
            }
            val normalized = linkedMapOf<CustomDesignAnchorField, String>()
            activeFields.forEach { field ->
                val value = values[field] ?: return null
                normalized[field] = normalizeHexColor(value) ?: return null
            }
            return CustomDesignColumnTextColors(normalized)
        }
    }
}
