package com.hoggamers.rankforge.domain.ocr.customdesign

import java.util.Locale

class CustomDesignColumnTextColors private constructor(
    private val colorsByField: Map<CustomDesignAnchorField, String>,
) {
    fun colorFor(field: CustomDesignAnchorField): String = colorsByField.getValue(field)

    fun asMap(): Map<CustomDesignAnchorField, String> = colorsByField

    override fun equals(other: Any?): Boolean =
        other is CustomDesignColumnTextColors && colorsByField == other.colorsByField

    override fun hashCode(): Int = colorsByField.hashCode()

    companion object {
        const val DEFAULT_COLOR = "#000000"
        private val colorPattern = Regex("#[0-9A-Fa-f]{6}")

        fun allBlack(): CustomDesignColumnTextColors =
            CustomDesignColumnTextColors(
                CustomDesignAnchorField.entries.associateWith { DEFAULT_COLOR },
            )

        fun fromMap(values: Map<CustomDesignAnchorField, String>): CustomDesignColumnTextColors? {
            if (values.keys != CustomDesignAnchorField.entries.toSet()) return null
            val normalized = linkedMapOf<CustomDesignAnchorField, String>()
            for (field in CustomDesignAnchorField.entries) {
                val value = values[field] ?: return null
                if (!colorPattern.matches(value)) return null
                normalized[field] = value.uppercase(Locale.ROOT)
            }
            return CustomDesignColumnTextColors(normalized)
        }
    }
}
