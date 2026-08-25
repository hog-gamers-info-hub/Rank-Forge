package com.hoggamers.rankforge.domain.ocr.matchlobby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LobbyPlayerOcrConsensusResolverTest {
    @Test
    fun ppPrimaryResultLeavesMlEvidenceAbsentWhenFallbackWasNotNeeded() {
        val result = LobbyPlayerDualOcrResult(
            teamSlotNumber = 3,
            row = LobbyPlayerRow.ROW_1,
            rowBounds = LobbyPlayerRowCropBounds(0, 0, 10, 10),
            slotAnchorSource = LobbySlotAnchorSource.TEAM_CROP_CENTER_FALLBACK,
            slotAnchorY = 5.0,
            ppEvidence = LobbyPlayerOcrEngineEvidence(
                engine = LobbyPlayerOcrEngine.PP_OCRV6,
                rawText = "RB-Speed",
                candidateText = "RB-Speed",
            ),
            resolvedText = "RB-Speed",
            finalText = "RB-Speed",
            selectedSource = LobbyPlayerNameOcrSource.PP_PRIMARY,
        )

        assertEquals("RB-Speed", result.finalText)
        assertEquals(LobbyPlayerNameOcrSource.PP_PRIMARY, result.selectedSource)
        assertNull(result.mlEvidence)
    }

    @Test
    fun fallbackResultKeepsMlEvidenceAndSourceDistinctFromNotRun() {
        val result = LobbyPlayerDualOcrResult(
            teamSlotNumber = 3,
            row = LobbyPlayerRow.ROW_2,
            rowBounds = LobbyPlayerRowCropBounds(0, 0, 10, 10),
            slotAnchorSource = LobbySlotAnchorSource.TEAM_CROP_CENTER_FALLBACK,
            slotAnchorY = 5.0,
            ppEvidence = LobbyPlayerOcrEngineEvidence(
                engine = LobbyPlayerOcrEngine.PP_OCRV6,
                rawText = "",
                candidateText = null,
            ),
            mlEvidence = LobbyPlayerOcrEngineEvidence(
                engine = LobbyPlayerOcrEngine.ML_KIT,
                rawText = "Fallback Name",
                candidateText = "Fallback Name",
            ),
            resolvedText = "Fallback Name",
            finalText = "Fallback Name",
            selectedSource = LobbyPlayerNameOcrSource.ML_FALLBACK,
        )

        assertEquals("Fallback Name", result.finalText)
        assertEquals(LobbyPlayerNameOcrSource.ML_FALLBACK, result.selectedSource)
        assertEquals("Fallback Name", result.mlEvidence?.candidateText)
    }

    @Test
    fun bothEmptyProducesEmptyWithoutFabricatedText() {
        val result = LobbyPlayerOcrConsensusResolver.resolve("  ", null)

        assertNull(result.resolvedText)
        assertEquals(LobbyPlayerOcrConsensusStatus.BOTH_EMPTY, result.status)
        assertNull(result.similarityScore)
    }

    @Test
    fun exactAgreementPreservesTheAgreedCandidate() {
        val result = LobbyPlayerOcrConsensusResolver.resolve("NE.ZLUX", "NE.ZLUX")

        assertEquals("NE.ZLUX", result.resolvedText)
        assertEquals(LobbyPlayerOcrConsensusStatus.AGREED, result.status)
        assertEquals(100, result.similarityScore)
    }

    @Test
    fun singleEngineCandidatesAreSelectedWithoutDiscardingTheNonEmptyValue() {
        val ppOnly = LobbyPlayerOcrConsensusResolver.resolve(null, "DARKxKING")
        val mlOnly = LobbyPlayerOcrConsensusResolver.resolve("DARKxKING", "")

        assertEquals("DARKxKING", ppOnly.resolvedText)
        assertEquals(LobbyPlayerOcrConsensusStatus.PP_ONLY, ppOnly.status)
        assertEquals("DARKxKING", mlOnly.resolvedText)
        assertEquals(LobbyPlayerOcrConsensusStatus.ML_ONLY, mlOnly.status)
    }

    @Test
    fun similarCandidatesAlwaysSelectPpAndRetainSimilarityEvidence() {
        val result = LobbyPlayerOcrConsensusResolver.resolve("VELOCITyHxT", "VELOCITYHxT")

        assertEquals("VELOCITYHxT", result.resolvedText)
        assertEquals(LobbyPlayerOcrConsensusStatus.SIMILAR_PP_SELECTED, result.status)
        assertEquals(100, result.similarityScore)
    }

    @Test
    fun veryDifferentCandidatesAlsoSelectPpWithoutBlendingCharacters() {
        val result = LobbyPlayerOcrConsensusResolver.resolve("SOKiNGBOYS", "KiNGBDY$")

        assertEquals("KiNGBDY$", result.resolvedText)
        assertEquals(LobbyPlayerOcrConsensusStatus.DISAGREEMENT_PP_SELECTED, result.status)
        assertEquals("KiNGBDY$", result.resolvedText)
    }

    @Test
    fun comparisonNormalizationDoesNotApplyOcrCharacterSubstitutions() {
        assertEquals("0O 1Il S$ B8", LobbyPlayerOcrComparisonNormalizer.normalize(" 0O   1Il S$ B8 "))
        assertEquals("0o 1il s$ b8", LobbyPlayerOcrComparisonNormalizer.normalizeForSimilarity(" 0o   1il s$ b8 "))
    }
}
