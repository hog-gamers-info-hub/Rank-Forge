package com.hoggamers.rankforge.domain.ocr.customdesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDesignRowCapacityTest {
    @Test
    fun supportsOnlyTheStandardAndGroupRotationCapacities() {
        assertTrue(CustomDesignRowCapacity.isSupported(12))
        assertTrue(CustomDesignRowCapacity.isSupported(18))
        assertTrue(CustomDesignRowCapacity.isSupported(24))
        assertEquals(1..12, CustomDesignRowCapacity.rankRange(12))
        assertEquals(1..18, CustomDesignRowCapacity.rankRange(18))
        assertEquals(1..24, CustomDesignRowCapacity.rankRange(24))
    }

    @Test
    fun derivesCapacityOnlyFromAnExactCompleteRankKeySet() {
        assertEquals(
            18,
            CustomDesignRowCapacity.capacityForExactRankKeys((1..18).toSet()),
        )
        assertNull(CustomDesignRowCapacity.capacityForExactRankKeys((1..17).toSet()))
        assertNull(CustomDesignRowCapacity.capacityForExactRankKeys((1..18).toSet() + 19))
        assertNull(CustomDesignRowCapacity.capacityForExactRankKeys(setOf(1, 2, 4)))
    }
}
