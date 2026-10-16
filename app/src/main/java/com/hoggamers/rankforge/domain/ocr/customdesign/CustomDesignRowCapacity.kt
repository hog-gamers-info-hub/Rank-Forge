package com.hoggamers.rankforge.domain.ocr.customdesign

/** Exact row counts supported by a saved Custom Design template. */
object CustomDesignRowCapacity {
    const val DEFAULT_ROW_COUNT = 12

    private val supportedRowCounts = setOf(12, 18, 24)

    fun isSupported(rowCount: Int): Boolean = rowCount in supportedRowCounts

    fun requireSupported(rowCount: Int): Int {
        require(isSupported(rowCount)) { "Unsupported Custom Design row count: $rowCount" }
        return rowCount
    }

    fun rankRange(rowCount: Int): IntRange = 1..requireSupported(rowCount)

    fun capacityForExactRankKeys(keys: Set<Int>): Int? = supportedRowCounts
        .singleOrNull { rowCount -> keys == rankRange(rowCount).toSet() }
}
