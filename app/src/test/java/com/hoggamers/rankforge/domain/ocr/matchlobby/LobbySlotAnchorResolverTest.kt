package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LobbySlotAnchorResolverTest {
    private val resolver = LobbySlotAnchorResolver()

    @Test
    fun validMlSlotAnchorHasPriority() {
        val result = resolver.resolve(
            authoritativeTeamSlotNumber = 5,
            teamCropWidth = 400,
            teamCropHeight = 360,
            mlKitEvidence = evidence("7", 7, RawOcrBoundingBox(10, 170, 30, 190)),
            ppOcrEvidence = evidence("8", 8, RawOcrBoundingBox(10, 160, 30, 180)),
        )

        val resolved = requireNotNull(result)
        assertEquals(LobbySlotAnchorSource.ML_KIT_SLOT, resolved.source)
        assertEquals(5, resolved.authoritativeTeamSlotNumber)
        assertEquals(7, resolved.detectedSlotNumber)
        assertEquals(180.0, resolved.anchorY, 0.0)
    }

    @Test
    fun ppAnchorIsSelectedWhenMlEvidenceIsUnavailable() {
        val result = resolver.resolve(
            authoritativeTeamSlotNumber = 5,
            teamCropWidth = 400,
            teamCropHeight = 360,
            mlKitEvidence = evidence("5", 5, null),
            ppOcrEvidence = evidence("10", 10, RawOcrBoundingBox(10, 171, 30, 191)),
        )

        val resolved = requireNotNull(result)
        assertEquals(LobbySlotAnchorSource.PP_OCR_SLOT, resolved.source)
        assertEquals(181.0, resolved.anchorY, 0.0)
        assertEquals(5, resolved.authoritativeTeamSlotNumber)
    }

    @Test
    fun centerFallbackIsUsedWhenMlAndPpEvidenceIsUnavailable() {
        val result = resolver.resolve(
            authoritativeTeamSlotNumber = 12,
            teamCropWidth = 400,
            teamCropHeight = 361,
            mlKitEvidence = null,
            ppOcrEvidence = null,
        )

        assertEquals(LobbySlotAnchorSource.TEAM_CROP_CENTER_FALLBACK, result?.source)
        assertEquals(180.5, requireNotNull(result).anchorY, 0.0)
        assertNull(result.detectedSlotNumber)
        assertNull(result.selectedEvidence)
    }

    @Test
    fun invalidEvidenceFallsBackToExactCropCenter() {
        val result = resolver.resolve(
            authoritativeTeamSlotNumber = 1,
            teamCropWidth = 400,
            teamCropHeight = 360,
            mlKitEvidence = evidence("13", 13, RawOcrBoundingBox(10, 10, 20, 20)),
            ppOcrEvidence = evidence("1", 1, RawOcrBoundingBox(-1, 170, 20, 190)),
        )

        assertEquals(LobbySlotAnchorSource.TEAM_CROP_CENTER_FALLBACK, result?.source)
        assertEquals(180.0, requireNotNull(result).anchorY, 0.0)
    }

    @Test
    fun slotAreaMismatchIsIgnoredWithoutChangingAuthoritativeIdentity() {
        val result = resolver.resolve(
            authoritativeTeamSlotNumber = 6,
            teamCropWidth = 400,
            teamCropHeight = 360,
            mlKitEvidence = evidence(
                rawText = "6",
                detectedSlotNumber = 6,
                boundingBox = RawOcrBoundingBox(10, 170, 30, 190),
                belongsToSlotArea = false,
            ),
            ppOcrEvidence = null,
        )

        assertEquals(LobbySlotAnchorSource.TEAM_CROP_CENTER_FALLBACK, result?.source)
        assertEquals(6, result?.authoritativeTeamSlotNumber)
    }

    @Test
    fun parsedSlotEvidenceCanPreserveRawTextConflict() {
        val result = resolver.resolve(
            authoritativeTeamSlotNumber = 6,
            teamCropWidth = 400,
            teamCropHeight = 360,
            mlKitEvidence = evidence("7", 6, RawOcrBoundingBox(10, 170, 30, 190)),
            ppOcrEvidence = null,
        )

        assertEquals(LobbySlotAnchorSource.ML_KIT_SLOT, result?.source)
        assertEquals(6, result?.detectedSlotNumber)
    }

    @Test
    fun invalidAuthoritativeSlotOrCropDimensionsReturnNoResolution() {
        assertNull(
            resolver.resolve(
                authoritativeTeamSlotNumber = 13,
                teamCropWidth = 400,
                teamCropHeight = 360,
                mlKitEvidence = null,
                ppOcrEvidence = null,
            ),
        )
        assertNull(
            resolver.resolve(
                authoritativeTeamSlotNumber = 1,
                teamCropWidth = 0,
                teamCropHeight = 360,
                mlKitEvidence = null,
                ppOcrEvidence = null,
            ),
        )
    }

    private fun evidence(
        rawText: String,
        detectedSlotNumber: Int?,
        boundingBox: RawOcrBoundingBox?,
        belongsToSlotArea: Boolean = true,
    ) = LobbySlotAnchorEvidence(
        rawText = rawText,
        detectedSlotNumber = detectedSlotNumber,
        boundingBox = boundingBox,
        belongsToSlotArea = belongsToSlotArea,
    )
}
