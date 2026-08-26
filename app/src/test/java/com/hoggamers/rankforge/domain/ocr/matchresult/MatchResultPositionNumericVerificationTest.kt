package com.hoggamers.rankforge.domain.ocr.matchresult

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultPositionNumericVerificationTest {
    @Test
    fun threeMatchingVariantsAreVerified() {
        assertVerifiedValues(listOf(3, 3, 3), 3)
    }

    @Test
    fun missingOriginalWithTwoZeroVariantsVerifiesZero() {
        assertVerifiedValues(listOf(null, 0, 0), 0)
    }

    @Test
    fun letterONormalizesToZero() {
        val result = resolve("O", "0", "0")
        assertVerified(result, 0)
    }

    @Test
    fun majorityValueWinsOnlyWhenItHasStrictlyMoreSupport() {
        assertVerifiedValues(listOf(3, 8, 3), 3)
        assertTrue(resolve(3, 8, null) is MatchResultNumericVerification.Conflict)
    }

    @Test
    fun oneOrNoUsableCandidateIsUnresolved() {
        assertTrue(resolve(7, null, null) is MatchResultNumericVerification.Unresolved)
        assertTrue(resolve(null, null, null) is MatchResultNumericVerification.Unresolved)
        assertTrue(resolve("PLAYER7", null, null) is MatchResultNumericVerification.Unresolved)
    }

    @Test
    fun positionOwnershipIsAuthoritativeAndSequenceIsValidated() {
        val expected = MatchResultNumericVerification.Verified(
            value = 7,
            candidates = listOf(candidate(7)),
        )
        val input = MatchResultPositionOcrInput(
            role = com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            position = 7,
            cropWidth = 491,
            cropHeight = 82,
            blocks = emptyList(),
            rowCrops = emptyList(),
            placementVerification = expected,
            killVerifications = emptyMap(),
        )
        assertEquals(7, MatchResultPositionOcrFieldMapper().map(input).position)
        assertTrue(MatchResultPositionSequenceValidator.validate(
            com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            listOf(11, 12),
        ).isValid)
        assertTrue(MatchResultPositionSequenceValidator.validate(
            com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            listOf(1, 2, 12),
        ).unexpectedPositions.contains(12))
        val missing = MatchResultPositionSequenceValidator.validate(
            com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            (1..9).toList(),
        )
        assertTrue(!missing.isValid)
        assertTrue(missing.missingPositions.contains(10))
        val duplicate = MatchResultPositionSequenceValidator.validate(
            com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            listOf(1, 2, 2, 3),
        )
        assertTrue(!duplicate.isValid)
        assertTrue(duplicate.duplicatePositions.contains(2))
    }

    @Test
    fun focusedLayoutUsesRowBoundsAndRejectsInvalidRolePosition() {
        val row = MatchResultPositionRowCrop(2, com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect(0, 40, 491, 82))
        val bounds = MatchResultPositionFocusedNumericCropLayout.boundsOrNull(
            role = com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            position = 12,
            field = MatchResultFocusedNumericField.KILL_SLOT_4,
            imageWidth = 491,
            imageHeight = 82,
            row = row,
        )
        assertEquals(40, bounds?.top)
        assertEquals(82, bounds?.bottom)
        assertEquals(null, MatchResultPositionFocusedNumericCropLayout.boundsOrNull(
            role = com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            position = 12,
            field = MatchResultFocusedNumericField.PLACEMENT,
            imageWidth = 491,
            imageHeight = 82,
        ))
    }

    private fun assertVerifiedValues(values: List<Int?>, expected: Int) = assertVerified(
        values.mapIndexed { index, value ->
            MatchResultNumericCandidate(
                variant = MatchResultNumericCropVariant.entries[index],
                rawText = value?.toString().orEmpty(),
                value = value,
                confidence = 0.9f,
            )
        },
        expected,
    )

    private fun assertVerified(candidates: List<MatchResultNumericCandidate>, expected: Int) {
        val result = MatchResultNumericConsensus.resolve(candidates)
        assertVerified(result, expected)
    }

    private fun assertVerified(result: MatchResultNumericVerification, expected: Int) {
        assertTrue(result is MatchResultNumericVerification.Verified)
        assertEquals(expected, (result as MatchResultNumericVerification.Verified).value)
    }

    private fun resolve(first: Any?, second: Any?, third: Any?): MatchResultNumericVerification =
        MatchResultNumericConsensus.resolve(listOf(first, second, third).mapIndexed { index, value ->
            val raw = value?.toString().orEmpty()
            MatchResultNumericCandidate(
                variant = MatchResultNumericCropVariant.entries[index],
                rawText = raw,
                value = raw.toIntOrNull() ?: raw.takeIf { it.equals("O", true) }?.let { 0 },
                confidence = 0.9f,
            )
        })

    private fun candidate(value: Int) = MatchResultNumericCandidate(
        MatchResultNumericCropVariant.ORIGINAL,
        value.toString(),
        value,
        0.9f,
    )
}
